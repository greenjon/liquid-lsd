package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import mu.KotlinLogging
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

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

    /**
     * Proposes a new genome ([fresh]) or a drift away from [from], and keeps only a candidate that the quality gate
     * accepts (blank, exploded, collapsed or fog-like ones are retried; when every try fails a fresh request falls
     * back to the starter and a drift stays where it is). A fresh genome also frames the camera on its attractor,
     * because the camera parameters are not part of the morph.
     */
    override fun sampleMorphTarget(from: DeckMorphSnapshot, target: DeckMorphSnapshot, random: Random, fresh: Boolean) {
        fun fromValue(name: String) = parameters[name]?.let { from.parameters[it]?.baseValue ?: it.baseValue } ?: 0f
        val strength = parameters["Mutation"]?.value ?: 0.25f
        val gate = gate()
        var chosen: Map<String, Float>? = null
        var verdict: FlameQuality.Result? = null
        for (attempt in 0 until MAX_TRIES) {
            val candidate = if (fresh) FlameSampler.random(parameters, random) else FlameSampler.mutate(parameters, ::fromValue, strength, random)
            if (gate == null) { chosen = candidate; break }
            val result = gate.evaluate { name -> candidate[name] ?: fromValue(name) }
            if (result.ok) { chosen = candidate; verdict = result; break }
            logger.debug { "Flame candidate rejected (try ${attempt + 1}): $result" }
        }
        if (chosen == null) {
            logger.info { "Flame: no candidate passed the quality gate in $MAX_TRIES tries (${if (fresh) "fresh" else "drift"})" }
            chosen = if (fresh) starterValues() else FlameSampler.mutate(parameters, ::fromValue, 0f, random)
        }
        for ((name, v) in chosen) {
            val p = parameters[name] ?: continue
            target.parameters[p]?.baseValue = v
        }
        if (fresh) {   // framed on the attractor, or the starter's camera when the starter was the fallback
            for ((name, v) in listOf("Zoom" to verdict?.zoom, "CenterX" to verdict?.centerX, "CenterY" to verdict?.centerY)) {
                parameters[name]?.let { it.set(v ?: it.defaultValue) }
            }
        }
    }

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
        val e = engine ?: FlameEngine().also { engine = it }
        val xfCount = FlameGenome.pack(parameters, genome, if (morphLive) lookup else null)
        e.render(this, targetFBO, genome, xfCount)
    }

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
        private val TONE_UNIFORMS = listOf("Exposure", "Brightness", "Gamma", "Vibrancy", "Toe", "Decay")
    }
}
