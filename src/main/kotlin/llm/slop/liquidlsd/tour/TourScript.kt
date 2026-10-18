package llm.slop.liquidlsd.tour

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tour script. Each step starts at [TourStep.t] seconds on the simulated clock.
 * Actions: move (ease cursor to target/x,y over durationSec), click (press at the cursor, release after durationSec),
 * set_page (UITheme.performancePageId = target), hold (caption only), drag (press, travel dx/dy over durationSec, release),
 * library_mode (FULL|HALF), open_params / open_params_pair / focus_pair (target = module id / pair tag), close_focus,
 * preferences (target = category name, blank = last) / preferences_close, theme (light|dark),
 * load_source (deck, target = source id), load_preset (deck, target = .lsd path),
 * set_param (deck, param, value), bind_modulator (deck, param, target = CV source id e.g. lfo / audio_bass, value = depth, unit).
 * FX (deck = A/B/BG/PV/MASTER): fx_filter (slot, target = ISF filter id, blank clears), fx_enable / fx_link (slot, value 0|1),
 * fx_chain (target = .lsdfxchain), fx_clear, fx_dry_wet (value), fx_param (slot, param, value), super_knob (value), meta_knob (slot, value).
 * Mixer: transition (target = stock id), transition_preset (target = .lsdtrans), crossfade (value -1..1).
 */
@Serializable
data class TourStep(
    val t: Double,
    val action: String,
    val target: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val durationSec: Double? = null,
    val caption: String? = null,
    /** Deck for load_source / load_preset / bind_modulator / set_param: A, B, BG or PV. */
    val deck: String? = null,
    /** Parameter name (bind_modulator, set_param). */
    val param: String? = null,
    /** Base value (set_param) or modulation depth (bind_modulator). */
    val value: Float? = null,
    /** bind_modulator: BEAT or TIME for LFO sources. */
    val unit: String? = null,
    /** FX slot index 0..2 (fx_filter, fx_enable, fx_link, fx_param, meta_knob). */
    val slot: Int? = null,
    /** drag: pixel offset the pressed cursor travels (up on a knob = increase). */
    val dx: Float? = null,
    val dy: Float? = null
)

/** A named range of the tour. Ends at the next chapter's start (or the tour end) unless [endSec] is set; [clipSec] caps its silent loop. */
@Serializable
data class TourChapter(
    val t: Double,
    val id: String,
    val title: String,
    val endSec: Double? = null,
    /** Silent loop length for the website; defaults to the whole chapter, capped at 20 s by render_tour.sh. */
    val clipSec: Double? = null
)

@Serializable
data class TourScript(
    val durationSec: Double,
    /** Seeds DeterministicRandom (randomizers, shuffle, modulator ids). */
    val seed: Long = 1,
    /** Optional audio file fed to the analyzer by frame clock and muxed as the soundtrack (relative to the script). */
    val audio: String? = null,
    /** Explicit clock: manual BPM for the beat flywheel. */
    val bpm: Double = 120.0,
    /** Draw each step's caption into the video (a .vtt is always written). */
    val burnCaptions: Boolean = false,
    val chapters: List<TourChapter> = emptyList(),
    val steps: List<TourStep>
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): TourScript = json.decodeFromString(serializer(), text)
    }
}
