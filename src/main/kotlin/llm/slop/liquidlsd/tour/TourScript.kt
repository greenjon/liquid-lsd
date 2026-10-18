package llm.slop.liquidlsd.tour

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Tour script step. Timing: absolute [t] seconds, or [dt] seconds after the previous step (default 0, i.e. simultaneous);
 * [TourScript.parse] resolves every step to an absolute [t].
 * Actions: move (ease cursor to target/x,y over durationSec), click (press at the cursor, release after durationSec),
 * set_page (UITheme.performancePageId = target), hold (caption only), drag (press, travel dx/dy over durationSec, release),
 * chapter (target = id, title; starts a chapter here), tooltips (value 0|1: ImGui hover tooltips, off by default),
 * library_mode (FULL|HALF), open_params / open_params_pair / focus_pair (target = module id / pair tag), close_focus,
 * preferences (target = category name, blank = last) / preferences_close, theme (light|dark),
 * load_source (deck, target = source id), load_preset (deck, target = .lsd path),
 * set_param (deck, param, value), bind_modulator (deck, param, target = CV source id e.g. lfo / audio_bass, value = depth, unit).
 * FX (deck = A/B/BG/PV/MASTER): fx_filter (slot, target = ISF filter id, blank clears), fx_enable / fx_link (slot, value 0|1),
 * fx_swap (slot, slot2), fx_chain (target = .lsdfxchain), fx_clear, fx_dry_wet (value), fx_param (slot, param, value),
 * super_knob (value), meta_knob (slot, value).
 * Mixer: transition (target = stock id), transition_preset (target = .lsdtrans), crossfade (value -1..1).
 * Captions: shown until the next step with a caption ("" clears). `{ch:<id>}` becomes "Ch. N at m:ss". With captionStyle
 * "cursor" the box sits below the cursor (flips when it does not fit); a step's captionPos (above/left/right) keeps it clear of
 * whatever that step is showing.
 */
@Serializable
data class TourStep(
    val t: Double = Double.NaN,
    val dt: Double? = null,
    val action: String,
    val target: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val durationSec: Double? = null,
    val caption: String? = null,
    /** Deck for load_source / load_preset / bind_modulator / set_param: A, B, BG or PV (FX actions also MASTER). */
    val deck: String? = null,
    /** Parameter name (bind_modulator, set_param). */
    val param: String? = null,
    /** Base value (set_param) or modulation depth (bind_modulator). */
    val value: Float? = null,
    /** bind_modulator: BEAT or TIME for LFO sources. */
    val unit: String? = null,
    /** FX slot index 0..2 (fx_filter, fx_enable, fx_link, fx_param, meta_knob, fx_swap). */
    val slot: Int? = null,
    /** fx_swap: the other slot. */
    val slot2: Int? = null,
    /** drag: pixel offset the pressed cursor travels (knobs: up or right = increase). */
    val dx: Float? = null,
    val dy: Float? = null,
    /** chapter: display title. */
    val title: String? = null,
    /** chapter: silent loop length for the website. */
    val clipSec: Double? = null,
    /** Caption box side relative to the cursor from this caption on: below (default), above, left, right. */
    val captionPos: String? = null
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
    /** Tour length; when absent, the last step's end plus [tailSec]. */
    val durationSec: Double? = null,
    val tailSec: Double = 3.0,
    /** Seeds DeterministicRandom (randomizers, shuffle, modulator ids). */
    val seed: Long = 1,
    /** Optional audio file fed to the analyzer by frame clock and muxed as the soundtrack (relative to the script). */
    val audio: String? = null,
    /** Explicit clock: manual BPM for the beat flywheel. */
    val bpm: Double = 120.0,
    /** Draw each step's caption into the video (a .vtt is always written). */
    val burnCaptions: Boolean = false,
    /** Burned caption placement: "cursor" (a box beside the cursor, like a tooltip) or "bottom" (centred bar). */
    val captionStyle: String = "cursor",
    /** ImGui hover tooltips at start (they would sit where the cursor caption box goes). */
    val tooltips: Boolean = false,
    val chapters: List<TourChapter> = emptyList(),
    val steps: List<TourStep>
) {
    /** Resolved length (see [durationSec]). */
    val length: Double get() = durationSec ?: ((steps.maxOfOrNull { it.t + (it.durationSec ?: 0.0) } ?: 0.0) + tailSec)

    /** [chapters] plus the `chapter` steps, by start time. */
    val allChapters: List<TourChapter> by lazy {
        (chapters + steps.filter { it.action == "chapter" && it.target != null }
            .map { TourChapter(it.t, it.target!!, it.title ?: it.target, clipSec = it.clipSec) }).sortedBy { it.t }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): TourScript = json.decodeFromString(serializer(), text).resolved()

        fun mmss(sec: Double): String {
            val s = sec.toInt()
            return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
        }
    }

    /** Absolute times for `dt` steps (in list order), then `{ch:id}` references in captions. */
    private fun resolved(): TourScript {
        var prev = 0.0
        val timed = steps.map { s ->
            val t = Math.round((if (!s.t.isNaN()) s.t else prev + (s.dt ?: 0.0)) * 1e6) / 1e6
            prev = t
            if (s.t == t) s else s.copy(t = t)
        }
        val withTimes = copy(steps = timed)
        val chs = withTimes.allChapters
        val ref = Regex("""\{ch:([A-Za-z0-9_\-]+)\}""")
        return withTimes.copy(steps = timed.map { s ->
            val c = s.caption ?: return@map s
            if (!c.contains("{ch:")) return@map s
            s.copy(caption = ref.replace(c) { m ->
                val i = chs.indexOfFirst { it.id == m.groupValues[1] }
                if (i < 0) m.value else "Ch. ${i + 1} at ${mmss(chs[i].t)}"
            })
        })
    }
}
