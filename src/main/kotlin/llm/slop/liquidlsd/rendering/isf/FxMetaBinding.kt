package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.Serializable
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroCurve
import llm.slop.liquidlsd.macro.MacroCurveType
import llm.slop.liquidlsd.macro.MacroLinkMode
import llm.slop.liquidlsd.macro.MacroTargetType

/**
 * Response curve applied to the 0..1 Metaknob position before it's mapped onto
 * [FxMetaBinding.minVal]..[FxMetaBinding.maxVal]. Chosen automatically by
 * [ISFAutoBindEngine] for time/frequency-like semantic names (rate, speed,
 * frequency, decay, feedback), or explicitly by a curated/user override.
 */
enum class MetaCurve {
    LINEAR,
    EXPONENTIAL,
    LOGARITHMIC,
    S_CURVE,
    STEP
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

private fun MetaCurve.toMacro(): MacroCurveType = when (this) {
    MetaCurve.LINEAR -> MacroCurveType.LINEAR
    MetaCurve.EXPONENTIAL -> MacroCurveType.EXPONENTIAL
    MetaCurve.LOGARITHMIC -> MacroCurveType.LOGARITHMIC
    MetaCurve.S_CURVE -> MacroCurveType.S_CURVE
    MetaCurve.STEP -> MacroCurveType.STEP
}

private fun MetaLinkMode.toMacro(): MacroLinkMode = when (this) {
    MetaLinkMode.FULL -> MacroLinkMode.FULL
    MetaLinkMode.FIRST_HALF -> MacroLinkMode.FIRST_HALF
    MetaLinkMode.SECOND_HALF -> MacroLinkMode.SECOND_HALF
    MetaLinkMode.TRIANGLE -> MacroLinkMode.TRIANGLE
    MetaLinkMode.BIPOLAR -> MacroLinkMode.BIPOLAR
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
    val enabled: Boolean = true,
    /** Number of positions for [MetaCurve.STEP]; ignored by the other curves. */
    val stepCount: Int = 8
) {
    /**
     * Maps a 0..1 Metaknob position onto this binding's target range, applying link windowing, curve, and direction.
     * Shares [MacroCurve]'s math with macro bindings so a source row and an FX slot respond identically.
     */
    fun mapKnobToTarget(knob01: Float): Float {
        val shaped = MacroCurve.shape(MacroCurve.window(knob01, linkMode.toMacro()), curve.toMacro(), stepCount)
        val t = if (invert) 1f - shaped else shaped
        return minVal + t * (maxVal - minVal)
    }

    /**
     * Inverse of [mapKnobToTarget]: the Metaknob position that reproduces [targetValue].
     * Used at filter-load and reset time (not per frame) so a freshly-bound Metaknob starts at the position matching
     * the target uniform's own authored default, instead of silently overwriting it with knob=0.
     * Exact for LINEAR, EXPONENTIAL and LOGARITHMIC with FULL; best effort for the piecewise link modes, STEP and S_CURVE.
     */
    fun knobForTarget(targetValue: Float): Float = MacroCurve.inverse(
        targetValue,
        MacroBinding(
            parameterId = "",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = minVal,
            maxVal = maxVal,
            curve = curve.toMacro(),
            inverted = invert,
            linkMode = linkMode.toMacro(),
            stepCount = stepCount
        )
    )

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
    val enabled: Boolean = true,
    val stepCount: Int = 8
)

fun FxMetaBinding.toDto(): FxMetaBindingDto = FxMetaBindingDto(
    targetParamName = targetParamName,
    minVal = minVal,
    maxVal = maxVal,
    curve = curve.name,
    invert = invert,
    linkMode = linkMode.name,
    enabled = enabled,
    stepCount = stepCount
)

fun FxMetaBindingDto.toBinding(): FxMetaBinding = FxMetaBinding(
    targetParamName = targetParamName,
    minVal = minVal,
    maxVal = maxVal,
    curve = runCatching { MetaCurve.valueOf(curve) }.getOrDefault(MetaCurve.LINEAR),
    invert = invert,
    linkMode = runCatching { MetaLinkMode.valueOf(linkMode) }.getOrDefault(MetaLinkMode.FULL),
    enabled = enabled,
    stepCount = stepCount
)
