package llm.slop.liquidlsd.tour

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tour script. Each step starts at [TourStep.t] seconds on the simulated clock.
 * Actions: move (ease cursor to target/x,y over durationSec), click (press at the cursor, release after durationSec),
 * set_page (UITheme.performancePageId = target), hold (caption only),
 * load_source (deck, target = source id), load_preset (deck, target = .lsd path),
 * set_param (deck, param, value), bind_modulator (deck, param, target = CV source id e.g. lfo / audio_bass, value = depth, unit).
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
    val unit: String? = null
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
    val steps: List<TourStep>
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): TourScript = json.decodeFromString(serializer(), text)
    }
}
