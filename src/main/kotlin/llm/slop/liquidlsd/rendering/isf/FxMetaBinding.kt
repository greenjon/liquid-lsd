package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.Serializable

/**
 * Response curve applied to the 0..1 Metaknob position before it's mapped onto
 * [FxMetaBinding.minVal]..[FxMetaBinding.maxVal]. Chosen automatically by
 * [ISFAutoBindEngine] for time/frequency-like semantic names (rate, speed,
 * frequency, decay, feedback), or explicitly by a curated/user override.
 */
enum class MetaCurve {
    LINEAR,
    EXPONENTIAL
}

/**
 * Resolved binding between an [ISFFilter]'s 0..1 Metaknob and one of its underlying
 * uniform parameters (or, when [targetParamName] is null, the filter's own [ISFFilter.dryWet]
 * as a last-resort "safety net" macro).
 */
data class FxMetaBinding(
    val targetParamName: String?,
    val minVal: Float,
    val maxVal: Float,
    val curve: MetaCurve = MetaCurve.LINEAR,
    val invert: Boolean = false
) {
    /** Maps a 0..1 Metaknob position onto this binding's target range, applying curve and direction. */
    fun mapKnobToTarget(knob01: Float): Float {
        val k = knob01.coerceIn(0f, 1f)
        val shaped = when (curve) {
            MetaCurve.LINEAR -> k
            MetaCurve.EXPONENTIAL -> k * k
        }
        val (lo, hi) = if (invert) maxVal to minVal else minVal to maxVal
        return lo + (hi - lo) * shaped
    }

    /**
     * Inverse of [mapKnobToTarget]: the Metaknob position that reproduces [targetValue].
     * Used once at filter-load time so a freshly-bound Metaknob starts at the position matching
     * the target uniform's own authored default, instead of silently overwriting it with knob=0.
     */
    fun knobForTarget(targetValue: Float): Float {
        val (lo, hi) = if (invert) maxVal to minVal else minVal to maxVal
        if (hi == lo) return 0f
        val t = ((targetValue - lo) / (hi - lo)).coerceIn(0f, 1f)
        return when (curve) {
            MetaCurve.LINEAR -> t
            MetaCurve.EXPONENTIAL -> kotlin.math.sqrt(t)
        }
    }

    companion object {
        /** The "safety net" binding: no float parameter was a viable macro target, drive dry/wet directly instead. */
        val DRY_WET_SAFETY_NET = FxMetaBinding(targetParamName = null, minVal = 0f, maxVal = 1f)
    }
}

/** Serializable form of [FxMetaBinding] for user-override caching (keyed by shader content hash) and DTO persistence. */
@Serializable
data class FxMetaBindingDto(
    val targetParamName: String? = null,
    val minVal: Float = 0f,
    val maxVal: Float = 1f,
    val curve: String = MetaCurve.LINEAR.name,
    val invert: Boolean = false
)

fun FxMetaBinding.toDto(): FxMetaBindingDto = FxMetaBindingDto(
    targetParamName = targetParamName,
    minVal = minVal,
    maxVal = maxVal,
    curve = curve.name,
    invert = invert
)

fun FxMetaBindingDto.toBinding(): FxMetaBinding = FxMetaBinding(
    targetParamName = targetParamName,
    minVal = minVal,
    maxVal = maxVal,
    curve = runCatching { MetaCurve.valueOf(curve) }.getOrDefault(MetaCurve.LINEAR),
    invert = invert
)
