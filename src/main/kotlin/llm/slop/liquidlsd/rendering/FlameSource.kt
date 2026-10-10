package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.random.Random

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
), GenomeMorph {
    private var engine: FlameEngine? = null
    private val genome = FloatArray(FlameGenome.PACKED_FLOATS)

    /** Only the tone-map pass reads uniforms here; the ~170 genome parameters go to the engine as a packed array. */
    override fun setupUniforms(shader: Shader) {
        for (name in TONE_UNIFORMS) shader.setUniform("u$name", parameters[name]?.value ?: 0f)
    }

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

    override fun sampleMorphTarget(from: DeckMorphSnapshot, target: DeckMorphSnapshot, random: Random) {
        val mutated = FlameSampler.mutate(
            parameters,
            from = { name -> parameters[name]?.let { from.parameters[it]?.baseValue ?: it.baseValue } ?: 0f },
            strength = parameters["Mutation"]?.value ?: 0.25f,
            random = random
        )
        for ((name, v) in mutated) {
            val p = parameters[name] ?: continue
            target.parameters[p]?.baseValue = v
        }
    }

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
        private val TONE_UNIFORMS = listOf("Exposure", "Brightness", "Gamma", "Vibrancy", "Toe", "Decay")
    }
}
