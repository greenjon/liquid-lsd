package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import mu.KotlinLogging
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/** Where a genome came from: the kept offspring it was bred or drawn from (empty for random ones) and their tags. */
class FlameLineage(val parents: List<String>, val parentTags: List<List<String>>) {
    companion object { val NONE = FlameLineage(emptyList(), emptyList()) }
}

/**
 * Fractal-flame generator (see .planning/flame-source-plan.md). The genome is the parameter map built by
 * [FlameGenome]; [FlameEngine] iterates and splats points on the GPU, and the folder's shader.frag is the tone-map
 * pass that [Renderer] binds.
 */
class FlameSource(
    id: String,
    displayName: String,
    shader: Shader,
    parameters: LinkedHashMap<String, ModulatableParameter>,
    ownsShader: Boolean,
    categories: List<String> = emptyList()
) : DynamicVisualSource(
    id = id,
    displayName = displayName,
    shader = shader,
    parameters = parameters,
    ownsShader = ownsShader,
    categories = categories
), GenomeMorph, StructuredEditorSource {
    private var engine: FlameEngine? = null
    private val genome = FloatArray(FlameGenome.PACKED_FLOATS)

    /** Only the tone-map pass reads uniforms here; the ~170 genome parameters go to the engine as a packed array. */
    override fun setupUniforms(shader: Shader) {
        for (name in TONE_UNIFORMS) shader.setUniform("u$name", parameters[name]?.value ?: 0f)
    }

    override fun editorTabs(keep: String?) = FlameGenome.editorTabs(parameters, keep)

    override fun addTabLabel(): String? =
        FlameGenome.firstHiddenXform(parameters).takeIf { it >= 0 }?.let { "+ X${it + 1}" }

    override fun addEditorTab(): String? =
        FlameGenome.firstHiddenXform(parameters).takeIf { it >= 0 }?.let { FlameGenome.reveal(parameters, it) }

    // ---- deck morph (GenomeMorph) -------------------------------------------------------------------------------

    private var morphA: DeckMorphSnapshot? = null
    private var morphB: DeckMorphSnapshot? = null
    private var morphT = 0f
    private var morphSerial = 0
    private var seenSerial = 0
    private var morphLive = false

    private val lookup = object : FlameMorphLookup {
        override val t get() = morphT
        override fun a(p: ModulatableParameter) = morphA?.parameters?.get(p)?.baseValue ?: p.baseValue
        override fun b(p: ModulatableParameter) = morphB?.parameters?.get(p)?.baseValue ?: p.baseValue
    }

    /** Lazily created on the render thread; null where there is no GL context (then candidates go ungated). */
    private var probe: FlameProbe? = null
    private var probeFailed = false

    private fun gate(): FlameProbe? {
        if (probe == null && !probeFailed) {
            probe = try { FlameProbe() } catch (e: Throwable) {
                probeFailed = true
                logger.warn(e) { "Flame quality gate unavailable; candidates are used ungated" }
                null
            }
        }
        return probe
    }

    private var liveOrigin = FlameLineage.NONE

    /** Kept offspring of this source; replaceable so tests need no files. */
    internal var poolProvider: () -> List<PoolMember> = { llm.slop.liquidlsd.presets.Garden.pool(id) }
    internal var favoritesProvider: () -> Set<String> = { llm.slop.liquidlsd.presets.Garden.favoritePaths() }

    /**
     * Proposes a genome the way [strategy] says and keeps only a candidate that the quality gate accepts (blank,
     * exploded, collapsed or fog-like ones are retried; when every try fails a fresh request falls back to the
     * starter and a drift stays put). Pool picks are kept offspring and skip the gate; an empty pool (two for Breed
     * means one parent bred with itself) falls back to Random when [fresh] and Mutate otherwise. When [fresh] the
     * camera is set too, because it is not part of the morph: framed on the attractor, or the offspring's own.
     */
    override fun sampleMorphTarget(from: DeckMorphSnapshot, target: DeckMorphSnapshot, random: Random, strategy: SamplerStrategy, fresh: Boolean) {
        fun fromValue(name: String) = parameters[name]?.let { from.parameters[it]?.baseValue ?: it.baseValue } ?: 0f
        val mutation = parameters["Mutation"]?.value ?: 0.25f
        val pool = if (strategy == SamplerStrategy.POOL || strategy == SamplerStrategy.BREED) poolProvider() else emptyList()
        val favorites = if (pool.size > 1) favoritesProvider() else emptySet()
        val effective = when {
            pool.isEmpty() && (strategy == SamplerStrategy.POOL || strategy == SamplerStrategy.BREED) ->
                if (fresh) SamplerStrategy.RANDOM else SamplerStrategy.MUTATE
            else -> strategy
        }
        fun member(p: PoolMember) = { name: String -> p.values[name] ?: parameters[name]?.defaultValue ?: 0f }

        // a kept offspring is used as it is: its own camera, no gate
        if (effective == SamplerStrategy.POOL) {
            val m = GenomePool.pick(pool, favorites, random)
            applyToTarget(target, genomeValues(member(m)))
            target.origin = FlameLineage(listOf(m.name), listOf(m.tags)).also { if (fresh) liveOrigin = it }
            if (fresh) for (name in FlameGenome.MORPH_CAMERA) parameters[name]?.set(member(m)(name))
            return
        }

        val gate = gate()
        var chosen: Map<String, Float>? = null
        var verdict: FlameQuality.Result? = null
        // a drift descends from whatever the state being left descended from; Random has no parents
        var lineage = if (effective == SamplerStrategy.RANDOM || fresh) FlameLineage.NONE else (from.origin as? FlameLineage ?: FlameLineage.NONE)
        for (attempt in 0 until MAX_TRIES) {
            val candidate = when (effective) {
                SamplerStrategy.RANDOM -> FlameSampler.random(parameters, random)
                SamplerStrategy.BREED -> {
                    val a = GenomePool.pick(pool, favorites, random); val b = GenomePool.pick(pool, favorites, random)
                    lineage = FlameLineage(listOf(a.name, b.name).distinct(), listOf(a.tags, b.tags))
                    FlameSampler.breed(parameters, member(a), member(b), mutation, random)
                }
                else -> FlameSampler.mutate(parameters, ::fromValue, mutation, random)
            }
            if (gate == null) { chosen = candidate; break }
            val result = gate.evaluate { name -> candidate[name] ?: fromValue(name) }
            if (result.ok) { chosen = candidate; verdict = result; break }
            logger.debug { "Flame candidate rejected (try ${attempt + 1}): $result" }
        }
        if (chosen == null) {
            logger.info { "Flame: no candidate passed the quality gate in $MAX_TRIES tries ($effective)" }
            chosen = if (fresh) starterValues() else FlameSampler.mutate(parameters, ::fromValue, 0f, random)
        }
        // the target brings its own framing, so the morph drifts the camera along with the genome: framed on the
        // candidate's attractor, else the starter's camera (fresh) or the state being left (a drift that stayed put)
        val camera = FlameGenome.MORPH_CAMERA.associateWith { name ->
            when {
                verdict != null -> when (name) { "Zoom" -> verdict.zoom; "CenterX" -> verdict.centerX; else -> verdict.centerY }
                fresh -> parameters[name]?.defaultValue ?: 0f
                else -> fromValue(name)
            }
        }
        applyToTarget(target, chosen + camera)
        target.origin = lineage
        if (fresh) {
            liveOrigin = lineage
            // a deck whose camera params predate the morph flag (old saves) still gets the framing on randomize
            for ((name, v) in camera) parameters[name]?.set(v)
        }
    }

    /** Lineage of the genome showing now: that of the nearer morph end while a morph runs, else of the last randomize. */
    fun currentLineage(): FlameLineage {
        if (!morphLive) return liveOrigin
        val end = if (morphT >= 0.5f) morphB else morphA
        return end?.origin as? FlameLineage ?: FlameLineage.NONE
    }

    /**
     * The base values of the nearer morph end, for the morphed (genome) parameters, or null when no morph is running.
     * Keeping the blend itself would save a genome that never existed as such (variation ids sit at the nearer end
     * while their weights cross-fade); the nearer end is a genome the sampler produced, and its lineage is the one
     * [currentLineage] reports.
     */
    fun nearerEndValues(): Map<ModulatableParameter, Float>? {
        if (!morphLive) return null
        val end = (if (morphT >= 0.5f) morphB else morphA) ?: return null
        return parameters.values.filter { it.randomizeBase }.mapNotNull { p -> end.parameters[p]?.let { p to it.baseValue } }.toMap()
    }

    private fun applyToTarget(target: DeckMorphSnapshot, values: Map<String, Float>) {
        for ((name, v) in values) {
            val p = parameters[name] ?: continue
            target.parameters[p]?.baseValue = v
        }
    }

    /** The morph-enabled genome parameters (not the final xform) read through [value]. */
    private fun genomeValues(value: (String) -> Float): Map<String, Float> =
        parameters.filter { (name, p) -> p.randomizeBase && !name.startsWith("XF") }.mapValues { value(it.key) }

    /** Default values of the genome's morph-enabled parameters (the shipped starter flame). */
    private fun starterValues(): Map<String, Float> =
        parameters.filter { (name, p) -> p.randomizeBase && !name.startsWith("XF") }.mapValues { it.value.defaultValue }

    override fun isDiscreteMorph(param: ModulatableParameter) = param.steps != null

    override fun onMorphUpdate(a: DeckMorphSnapshot, b: DeckMorphSnapshot, t: Float) {
        morphA = a; morphB = b; morphT = t; morphSerial++
    }

    /** The morph is live while the controller keeps updating; when it stops (Rand off) the snapshots go stale, so ignore them. */
    override fun update() {
        super.update()
        morphLive = morphSerial != seenSerial
        seenSerial = morphSerial
    }

    override fun renderTopology(targetFBO: FBO) {
        val e = engine ?: FlameEngine().also { engine = it; paletteDirty = true }
        if (paletteDirty) {
            e.setPalette(customPalette)
            paletteDirty = false
        }
        val xfCount = FlameGenome.pack(parameters, genome, if (morphLive) lookup else null)
        e.render(this, targetFBO, genome, xfCount)
    }

    /** Imported 768-byte RGB palette; null = the procedural default. Saved with the preset. */
    var customPalette: ByteArray? = null
        set(value) {
            field = value?.takeIf { it.size >= 768 }?.copyOf(768)
            paletteDirty = true
        }
    private var paletteDirty = true

    override fun clear() {
        super.clear()
        engine?.resetPoints()
    }

    override fun dispose() {
        engine?.dispose()
        engine = null
        probe?.dispose()
        probe = null
        super.dispose()
    }

    override fun clone(): FlameSource {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        parameters.forEach { (name, param) -> clonedParams[name] = param.clone() }
        return FlameSource(id, displayName, shader, clonedParams, ownsShader = false, categories = categories).also {
            it.globalAlpha.set(globalAlpha.baseValue)
        }
    }

    companion object {
        const val SOURCE_ID = "fractal_flame"
        /** Candidates tried per request before falling back; each costs well under a millisecond on the GPU. */
        private const val MAX_TRIES = 8
        private val TONE_UNIFORMS = listOf("Exposure", "Brightness", "Gamma", "Vibrancy", "Toe", "Decay", "Smooth")
    }
}
