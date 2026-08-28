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

    private var lastTimeNanos: Long = System.nanoTime()
    var integratedTime: Float = 0f
        private set
    var integratedShear: Float = 0f
        private set

    init {
        // Max Points is a quality/performance dial, not a CV target.
        // Silently ignore any modulators wired to it.
        parameters["Max Points"]?.modulatorFilter = { false }
    }

    override fun update() {
        super.update()
        val dt = if (llm.slop.liquidlsd.utils.TimeSource.isSimulated) {
            llm.slop.liquidlsd.utils.TimeSource.getDeltaTimeSec().toFloat()
        } else {
            val now = llm.slop.liquidlsd.utils.TimeSource.getTimeNanos()
            val computed = ((now - lastTimeNanos) / 1_000_000_000.0).toFloat().coerceIn(0.0f, 0.1f)
            lastTimeNanos = now
            computed
        }

        val speed = parameters["Speed"]?.value ?: 0.5f
        val shear = parameters["Shear"]?.value ?: 0.1f

        integratedTime += dt * speed
        integratedShear += dt * speed * shear
    }

    /**
     * Passes all parameters to the shader, with Max Points snapped to the nearest integer
     * and integrated time/shear phases passed for seamless speed & shear transitions.
     */
    override fun setupUniforms(shader: Shader) {
        parameters.forEach { (name, param) ->
            if (name == "Speed" || name == "Shear") return@forEach
            val uniformName = "u" + name.replace(" ", "")
            if (name == "Max Points") {
                shader.setUniform(uniformName, param.value.roundToInt().toFloat())
            } else {
                shader.setUniform(uniformName, param.value)
            }
        }
        shader.setUniform("uIntegratedTime", integratedTime)
        shader.setUniform("uIntegratedShear", integratedShear)
    }

    override fun clear() {
        super.clear()
        integratedTime = 0f
        integratedShear = 0f
        lastTimeNanos = System.nanoTime()
    }

    override fun clone(): DynamicSpiral {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        this.parameters.forEach { (name, param) ->
            clonedParams[name] = param.clone()
        }
        val copy = DynamicSpiral(
            id = this.id,
            displayName = this.displayName,
            shader = this.shader,
            parameters = clonedParams,
            hasFeedback = this.hasFeedback,
            ownsShader = false // Cloned instances do not own the shared shader
        )
        copy.integratedTime = this.integratedTime
        copy.integratedShear = this.integratedShear
        copy.lastTimeNanos = System.nanoTime()
        return copy
    }
}
