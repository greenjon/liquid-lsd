package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Shader

private val logger = mu.KotlinLogging.logger {}

/**
 * The [ISFHeader.LSD_PHASES] uniforms of one source or filter instance. Each frame every phase advances by the product of its
 * rate inputs times the time since the previous frame, so changing a rate changes how fast the phase moves, never where it is.
 * `TIME * Speed` in the shader instead moves the phase by TIME x the change, which grows without bound over a set.
 */
class PhaseIntegrators(phases: Map<String, List<String>>, parameters: Map<String, ModulatableParameter>) {
    private val names: Array<String> = phases.keys.toTypedArray()
    private val rates: Array<Array<ModulatableParameter>> = phases.map { (name, inputs) ->
        inputs.mapNotNull { input ->
            parameters[input] ?: null.also { logger.warn { "LSD_PHASES '$name': no float input '$input'; left out of the rate" } }
        }.toTypedArray()
    }.toTypedArray()
    private val values = DoubleArray(names.size)
    private var lastTimeSec = Double.NaN

    /**
     * Advances every phase to [nowSec]. The first call starts each phase where `TIME * rate` would have put it, so a freshly
     * loaded source looks as it always did; gaps longer than [MAX_STEP_SEC] count as that.
     */
    fun advance(nowSec: Double) {
        val first = lastTimeSec.isNaN()
        val dt = if (first) 0.0 else (nowSec - lastTimeSec).coerceIn(0.0, MAX_STEP_SEC)
        lastTimeSec = nowSec
        for (i in names.indices) {
            var rate = 1.0
            for (p in rates[i]) rate *= p.value
            values[i] = if (first) rate * nowSec else values[i] + rate * dt
        }
    }

    fun apply(shader: Shader) {
        for (i in names.indices) shader.setUniform(names[i], values[i].toFloat())
    }

    fun value(name: String): Double = values[names.indexOf(name)]

    companion object {
        /** A deck that was not rendered for a while resumes where it stopped instead of leaping ahead. */
        const val MAX_STEP_SEC = 0.25
    }
}
