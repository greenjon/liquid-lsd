package llm.slop.liquidlsd.macro

/**
 * Pure curve-shaping math for the Macro Controls system. See proposal §3.2 ("Evaluation
 * Pipeline") for the formula this implements:
 *
 *   mappedVal = Curve(macroVal, curve) * (maxVal - minVal) + minVal   (inverted if inverted == true)
 */
object MacroCurve {
    /**
     * Windows a normalized knob value [0,1] according to [linkMode], carving out which zone of
     * the knob's travel this binding responds to before curve shaping. Always returns a value
     * in [0,1]. See [MacroLinkMode] for the transfer function each mode implements.
     */
    fun window(macroVal: Float, linkMode: MacroLinkMode): Float {
        val v = macroVal.coerceIn(0f, 1f)
        return when (linkMode) {
            MacroLinkMode.FULL -> v
            MacroLinkMode.FIRST_HALF -> if (v <= 0.5f) v * 2f else 1f
            MacroLinkMode.SECOND_HALF -> if (v < 0.5f) 0f else (v - 0.5f) * 2f
            MacroLinkMode.TRIANGLE -> if (v <= 0.5f) v * 2f else (1f - v) * 2f
            MacroLinkMode.BIPOLAR -> kotlin.math.abs(v - 0.5f) * 2f
        }
    }

    /**
     * Shapes a normalized macro value [0,1] according to [curve]. [stepCount] is only used by
     * [MacroCurveType.STEP]. Always returns a value in [0,1].
     */
    fun shape(macroVal: Float, curve: MacroCurveType, stepCount: Int): Float {
        val v = macroVal.coerceIn(0f, 1f)
        return when (curve) {
            MacroCurveType.LINEAR -> v
            MacroCurveType.EXPONENTIAL -> v * v
            MacroCurveType.LOGARITHMIC -> 1f - (1f - v) * (1f - v)
            MacroCurveType.S_CURVE -> v * v * (3f - 2f * v)
            MacroCurveType.STEP -> {
                val n = stepCount.coerceAtLeast(1)
                if (n == 1) {
                    0f
                } else {
                    (kotlin.math.floor(v * n).coerceAtMost((n - 1).toFloat())) / (n - 1)
                }
            }
        }
    }

    /**
     * Windows [macroVal] via [MacroBinding.linkMode], shapes the result via
     * [MacroBinding.curve]/[MacroBinding.stepCount], applies [MacroBinding.inverted], then maps
     * into [MacroBinding.minVal]..[MacroBinding.maxVal]. `minVal > maxVal` is a valid, intentional
     * "inverted range" and is handled naturally by the linear interpolation below — it is a
     * distinct concept from the [MacroBinding.inverted] flag, which flips the *shaped curve
     * position* rather than the endpoints.
     */
    fun mapToRange(macroVal: Float, binding: MacroBinding): Float {
        val windowed = window(macroVal, binding.linkMode)
        val shaped = shape(windowed, binding.curve, binding.stepCount)
        val t = if (binding.inverted) 1f - shaped else shaped
        return binding.minVal + t * (binding.maxVal - binding.minVal)
    }
}
