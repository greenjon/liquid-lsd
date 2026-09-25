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
        val v = if (macroVal.isNaN()) 0f else macroVal.coerceIn(0f, 1f)
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
        val v = if (macroVal.isNaN()) 0f else macroVal.coerceIn(0f, 1f)
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

    /**
     * Inverts [mapToRange] to calculate the normalized knob value [0,1] needed to reproduce [target].
     * Exactly inverts LINEAR, EXPONENTIAL, and LOGARITHMIC curves with FULL link mode, and provides
     * best-effort clamped fallbacks for piecewise link modes and stepped/S-curves.
     */
    fun inverse(target: Float, binding: MacroBinding): Float {
        val range = binding.maxVal - binding.minVal
        if (kotlin.math.abs(range) < 1e-6f) return 0f

        val tRaw = (target - binding.minVal) / range
        val t = if (tRaw.isNaN()) 0f else tRaw.coerceIn(0f, 1f)
        val shaped = if (binding.inverted) 1f - t else t

        val windowed = when (binding.curve) {
            MacroCurveType.LINEAR -> shaped
            MacroCurveType.EXPONENTIAL -> kotlin.math.sqrt(shaped)
            MacroCurveType.LOGARITHMIC -> 1f - kotlin.math.sqrt((1f - shaped).coerceAtLeast(0f))
            MacroCurveType.S_CURVE -> {
                var low = 0f
                var high = 1f
                var mid = shaped
                for (i in 0 until 12) {
                    mid = (low + high) * 0.5f
                    val sm = mid * mid * (3f - 2f * mid)
                    if (sm < shaped) low = mid else high = mid
                }
                mid
            }
            MacroCurveType.STEP -> shaped
        }

        val macroVal = when (binding.linkMode) {
            MacroLinkMode.FULL -> windowed
            MacroLinkMode.FIRST_HALF -> windowed * 0.5f
            MacroLinkMode.SECOND_HALF -> 0.5f + windowed * 0.5f
            MacroLinkMode.TRIANGLE -> windowed * 0.5f
            MacroLinkMode.BIPOLAR -> 0.5f + windowed * 0.5f
        }

        return if (macroVal.isNaN()) 0f else macroVal.coerceIn(0f, 1f)
    }
}

