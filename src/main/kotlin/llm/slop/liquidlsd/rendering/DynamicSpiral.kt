package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.roundToInt

/**
 * DynamicSpiral is a fragment-shader point-cloud source that renders a mathematical spiral
 * consisting of up to [uMaxPoints] animated points, each with additive glow and palette-driven colour.
 *
 * The "Max Points" parameter is intentionally non-modulatable: it is a GPU performance knob
 * whose integer nature makes CV modulation semantically meaningless. The [modulatorFilter]
 * on that parameter is set to `{ false }` so every modulator is silently ignored and
 * the parameter always tracks its [baseValue].
 *
 * All other parameters are fully modulatable via the standard CV / LFO grid.
 */
class DynamicSpiral(
    id: String,
    displayName: String,
    shader: Shader,
    parameters: LinkedHashMap<String, ModulatableParameter>,
    hasFeedback: Boolean = false,
    ownsShader: Boolean = false
) : DynamicVisualSource(id, displayName, shader, parameters, hasFeedback = hasFeedback, ownsShader = ownsShader) {

    init {
        // Max Points is a quality/performance dial, not a CV target.
        // Silently ignore any modulators wired to it.
        parameters["Max Points"]?.modulatorFilter = { false }
    }

    /**
     * Passes all parameters to the shader, with Max Points snapped to the nearest integer
     * so the GLSL `int(uMaxPoints)` cast is exact and there is no off-by-one drift.
     */
    override fun setupUniforms(shader: Shader) {
        parameters.forEach { (name, param) ->
            val uniformName = "u" + name.replace(" ", "")
            if (name == "Max Points") {
                shader.setUniform(uniformName, param.value.roundToInt().toFloat())
            } else {
                shader.setUniform(uniformName, param.value)
            }
        }
    }

    override fun clone(): DynamicSpiral {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        this.parameters.forEach { (name, param) ->
            clonedParams[name] = param.clone()
        }
        return DynamicSpiral(
            id = this.id,
            displayName = this.displayName,
            shader = this.shader,
            parameters = clonedParams,
            hasFeedback = this.hasFeedback,
            ownsShader = false // Cloned instances do not own the shared shader
        )
    }
}
