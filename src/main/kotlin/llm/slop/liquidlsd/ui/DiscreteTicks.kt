package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.parameters.ModulatableParameter

/** Pure helpers for stepped ([ModulatableParameter.steps]) knobs: where the tick marks go and what the readout says. */
object DiscreteTicks {
    /** Above this many steps no ticks are drawn (they would merge into a smear); snapping still applies. */
    const val MAX_TICKS = 16

    /** Angle of each of [steps] ticks, evenly from [startAngle] to [endAngle] inclusive; empty when none should be drawn. */
    fun angles(steps: Int?, startAngle: Float, endAngle: Float): FloatArray {
        if (steps == null || steps < 2 || steps > MAX_TICKS) return FloatArray(0)
        return FloatArray(steps) { startAngle + (endAngle - startAngle) * it / (steps - 1) }
    }

    /** Index (0-based) of the step nearest [v], or null for a continuous parameter. */
    fun stepIndex(param: ModulatableParameter, v: Float): Int? {
        val n = param.steps?.takeIf { it >= 2 } ?: return null
        val stepSize = (param.maxClamp - param.minClamp) / (n - 1)
        if (stepSize <= 0f) return null
        return Math.round((v - param.minClamp) / stepSize).coerceIn(0, n - 1)
    }

    /** Readout for [v] on a stepped parameter: its label, else the snapped value as a whole number; null when continuous. */
    fun readout(param: ModulatableParameter, v: Float): String? {
        val index = stepIndex(param, v) ?: return null
        param.labels?.getOrNull(index)?.let { return it }
        return ValueFormat.trimmed(param.snap(v))
    }
}
