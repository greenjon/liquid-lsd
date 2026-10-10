package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter

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
) {
    private var engine: FlameEngine? = null
    private val genome = FloatArray(FlameGenome.PACKED_FLOATS)

    /** Only the tone-map pass reads uniforms here; the ~170 genome parameters go to the engine as a packed array. */
    override fun setupUniforms(shader: Shader) {
        for (name in TONE_UNIFORMS) shader.setUniform("u$name", parameters[name]?.value ?: 0f)
    }

    override fun renderTopology(targetFBO: FBO) {
        val e = engine ?: FlameEngine().also { engine = it }
        val xfCount = FlameGenome.pack(parameters, genome)
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
