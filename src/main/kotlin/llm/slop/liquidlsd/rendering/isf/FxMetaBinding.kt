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
 * Mixxx-style knob-travel windowing applied to an ISF filter's 0..1 Metaknob position,
 * before [MetaCurve] shaping, allowing an effect slot's Metaknob to choreograph multiple
 * parameters across distinct zones of knob rotation.
 */
enum class MetaLinkMode {
    /** Parameter sweeps 0..1 across the full 0.0..1.0 knob travel. */
    FULL,
    /** Parameter sweeps 0..1 across knob 0.0..0.5, then holds at 1.0 across 0.5..1.0. */
    FIRST_HALF,
    /** Parameter holds at 0.0 across knob 0.0..0.5, then sweeps 0..1 across 0.5..1.0. */
    SECOND_HALF,
    /** Parameter sweeps 0..1 across knob 0.0..0.5, then reverses 1..0 across 0.5..1.0. */
    TRIANGLE,
    /** Parameter is 0.0 at knob center (0.5) and sweeps outward to 1.0 at either end. */
    BIPOLAR
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
    val invert: Boolean = false,
    val linkMode: MetaLinkMode = MetaLinkMode.FULL,
    val enabled: Boolean = true
) {
    /** Maps a 0..1 Metaknob position onto this binding's target range, applying link windowing, curve, and direction. */
    fun mapKnobToTarget(knob01: Float): Float {
        val k = if (knob01.isNaN()) 0f else knob01.coerceIn(0f, 1f)
        val w = when (linkMode) {
            MetaLinkMode.FULL -> k
            MetaLinkMode.FIRST_HALF -> if (k <= 0.5f) k * 2f else 1f
            MetaLinkMode.SECOND_HALF -> if (k < 0.5f) 0f else (k - 0.5f) * 2f
            MetaLinkMode.TRIANGLE -> if (k <= 0.5f) k * 2f else (1f - k) * 2f
            MetaLinkMode.BIPOLAR -> kotlin.math.abs(k - 0.5f) * 2f
        }
        val shaped = when (curve) {
            MetaCurve.LINEAR -> w
            MetaCurve.EXPONENTIAL -> w * w
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
        val shaped = when (curve) {
            MetaCurve.LINEAR -> t
            MetaCurve.EXPONENTIAL -> kotlin.math.sqrt(t)
        }
        return when (linkMode) {
            MetaLinkMode.FULL -> shaped
            MetaLinkMode.FIRST_HALF -> shaped * 0.5f
            MetaLinkMode.SECOND_HALF -> 0.5f + shaped * 0.5f
            MetaLinkMode.TRIANGLE -> shaped * 0.5f
            MetaLinkMode.BIPOLAR -> 0.5f + shaped * 0.5f
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
    val invert: Boolean = false,
    val linkMode: String = MetaLinkMode.FULL.name,
    val enabled: Boolean = true
)

fun FxMetaBinding.toDto(): FxMetaBindingDto = FxMetaBindingDto(
    targetParamName = targetParamName,
    minVal = minVal,
    maxVal = maxVal,
    curve = curve.name,
    invert = invert,
    linkMode = linkMode.name,
    enabled = enabled
)

fun FxMetaBindingDto.toBinding(): FxMetaBinding = FxMetaBinding(
    targetParamName = targetParamName,
    minVal = minVal,
    maxVal = maxVal,
    curve = runCatching { MetaCurve.valueOf(curve) }.getOrDefault(MetaCurve.LINEAR),
    invert = invert,
    linkMode = runCatching { MetaLinkMode.valueOf(linkMode) }.getOrDefault(MetaLinkMode.FULL),
    enabled = enabled
)
