package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.parameters.ModulatableParameter
import java.util.Locale
import kotlin.math.abs

/**
 * User-facing number formatting. Stored values never change: a parameter that spans exactly 0..1 or -1..1
 * is *shown* as 0..100 / -100..100 (one decimal only when it is non-zero), everything else keeps its native units.
 */
object ValueFormat {
    /**
     * Sentinel for "use the default formatter for this range". Slider callers pass it as `formatValue` and the slider
     * resolves it with [resolve] once it knows the scale. Compared by identity; the body is the unscaled fallback.
     */
    val AUTO: (Float) -> String = { String.format(Locale.ROOT, "%.3f", it) }

    private const val PERCENT = 100f

    /** Radians to degrees: a -pi..pi range is shown as -180..180. */
    const val DEGREES = (180.0 / Math.PI).toFloat()

    /** 100 for unit-fraction ranges (0..1, -1..1, and trimmed variants like 0.001..0.999), 180/pi for -pi..pi angles, else 1. */
    fun scaleFor(min: Float, max: Float): Float = when {
        min >= -1.001f && max <= 1.001f && max - min >= 0.99f -> PERCENT
        ModulatableParameter.isPiRange(min, max) -> DEGREES
        else -> 1f
    }

    fun scaleFor(min: Float, max: Float, isAngle: Boolean): Float = if (isAngle) 1f else scaleFor(min, max)

    /** [v] is a stored value; [scale] comes from [scaleFor]. */
    fun format(v: Float, scale: Float): String {
        if (scale == 1f) return String.format(Locale.ROOT, "%.3f", v)
        return trimmed(v * scale)
    }

    /** Whole numbers bare, otherwise one decimal. `-0` never appears. */
    fun trimmed(display: Float): String {
        val r = Math.round(display * 10f) / 10f
        if (r == 0f) return "0"
        return if (r == r.toInt().toFloat()) r.toInt().toString() else String.format(Locale.ROOT, "%.1f", r)
    }

    /** Knob-face text: integers as-is, otherwise a short fixed-point. Normalized controls read as 0..100. */
    fun knob(v: Float, min: Float = 0f, max: Float = 1f): String {
        val s = scaleFor(min, max)
        if (s != 1f) return Math.round(v * s).let { if (it == 0) "0" else it.toString() }
        return if (v == v.toInt().toFloat() && abs(v) < 1000f) v.toInt().toString() else String.format(Locale.ROOT, "%.2f", v)
    }

    fun resolve(formatValue: (Float) -> String, scale: Float): (Float) -> String =
        if (formatValue === AUTO) { v -> format(v, scale) } else formatValue
}
