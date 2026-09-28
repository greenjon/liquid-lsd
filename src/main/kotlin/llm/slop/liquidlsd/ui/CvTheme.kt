package llm.slop.liquidlsd.ui

import imgui.ImGui

/**
 * Modulation-source signal palette (CV graphs, parameter-grid cells, badges -- see
 * ParametersRenderer, AudioEnginePanel, PerformanceDeepEditBay, ValueParamSection).
 *
 * Deliberately NOT drawn from [TangoPalette]: these colors render inside the very cells/sliders
 * that TangoPalette.SYNC/ALERT already color for macro-bound/hover/learn state (CustomRangeSlider
 * draws a SYNC-cyan track whenever a slider is macro-bound, in the same grid CvTheme colors per
 * signal type), and PerformanceDeepEditBay renders its Deck PV side-tab (TangoPalette.PLUM) in
 * the same frame as this palette's "midi" cells. Reusing Tango hues here would recreate the
 * "which meaning does this color have" ambiguity the Tango migration eliminated for decks/status.
 * Keep hues bright/"electric" and disjoint from Tango's muted family so a signal-type color is
 * never mistaken for deck identity or bound/hover/active status.
 */
object CvTheme {

    class Signal(val r: Float, val g: Float, val b: Float) {
        fun toFloatArray(): FloatArray = floatArrayOf(r, g, b)
    }

    // -- Base value / static output -----------------------------------------------------------
    val VALUE           = Signal(0.00f, 0.95f, 0.72f) // Crisp Mint Cyan
    val BASE             = Signal(0.85f, 0.65f, 0.35f) // Warm Bronze Sand

    // -- MIDI ------------------------------------------------------------------------------------
    val MIDI             = Signal(0.72f, 0.45f, 1.00f) // Bright Orchid Purple

    // -- LFO / synthetic generators ---------------------------------------------------------------
    val LFO               = Signal(0.15f, 0.75f, 1.00f) // Electric Sky Blue
    val SAMPLE_AND_HOLD   = Signal(0.30f, 0.65f, 1.00f) // Periwinkle Blue
    val BEAT_PHASE        = Signal(0.40f, 0.60f, 1.00f) // Deep Sky Blue

    // -- Step sequencer ----------------------------------------------------------------------------
    val SEQ                = Signal(0.20f, 0.95f, 0.30f) // Electric Lime Green

    // -- Audio spectrum followers ---------------------------------------------------------------
    val AUDIO             = Signal(1.00f, 0.68f, 0.12f) // Warm Amber Gold
    val AUDIO_AMP         = Signal(1.00f, 0.75f, 0.20f) // Bright Amber
    val AUDIO_BASS        = Signal(1.00f, 0.42f, 0.15f) // Deep Orange
    val AUDIO_MID         = Signal(1.00f, 0.68f, 0.12f) // Golden Amber
    val AUDIO_HIGH        = Signal(0.95f, 0.88f, 0.25f) // Bright Gold

    // -- Audio transient flux triggers -----------------------------------------------------------
    val AUDIO_FLUX_AMP    = Signal(1.00f, 0.35f, 0.55f) // Coral Pink
    val AUDIO_FLUX_BASS   = Signal(1.00f, 0.20f, 0.35f) // Crimson Kick
    val AUDIO_FLUX_MID    = Signal(1.00f, 0.45f, 0.25f) // Flame Snare
    val AUDIO_FLUX_HIGH   = Signal(1.00f, 0.70f, 0.30f) // Neon Gold Hat

    val UNKNOWN           = Signal(0.60f, 0.60f, 0.60f)

    private val byId: Map<String, Signal> = mapOf(
        "value" to VALUE, "final" to VALUE,
        "base" to BASE,
        "midi" to MIDI,
        "lfo" to LFO,
        "sampleAndHold" to SAMPLE_AND_HOLD,
        "beatPhase" to BEAT_PHASE, "beatSine" to BEAT_PHASE,
        "seq" to SEQ,
        "audio" to AUDIO,
        "amp" to AUDIO_AMP, "audio_amp" to AUDIO_AMP,
        "bass" to AUDIO_BASS, "audio_bass" to AUDIO_BASS,
        "mid" to AUDIO_MID, "audio_mid" to AUDIO_MID,
        "high" to AUDIO_HIGH, "audio_high" to AUDIO_HIGH,
        "audio_flux_amp" to AUDIO_FLUX_AMP,
        "audio_flux_bass" to AUDIO_FLUX_BASS,
        "audio_flux_mid" to AUDIO_FLUX_MID,
        "audio_flux_high" to AUDIO_FLUX_HIGH
    )

    fun getThemeColor(cvId: String, alpha: Float = 1f): Int {
        val signal = byId[cvId] ?: UNKNOWN
        return ImGui.colorConvertFloat4ToU32(signal.r, signal.g, signal.b, alpha)
    }

    fun getThemeColorRGB(cvId: String): FloatArray = (byId[cvId] ?: UNKNOWN).toFloatArray()
}
