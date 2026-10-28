package llm.slop.liquidlsd.audio

import kotlin.math.max
import kotlin.math.min

/**
 * Per-band automatic gain for the audio CV signals (`audio_amp/bass/mid/high` and their flux). Always on:
 * Audio Response is the only reactivity control the user sees.
 *
 * Each band is divided by its own long-term level (a [LONG_TAU] mean, a running mean while it fills) times
 * [SCALE], so steady music reads the same whatever the input level, the way MilkDrop compares a band to
 * its average. The long-term levels freeze while [watch] holds (a breakdown, or one being confirmed), so a breakdown reads quiet and a
 * bass dropout is not boosted into kicks, and they jump to the current level when the watch adopts a new
 * normal (a quieter or louder song). After [SILENCE_RESET] seconds under [SILENT_RMS] everything waits and
 * starts afresh, so a new feed levels in at once. [FLOOR] stops silence and room noise from being boosted into constant
 * motion (at most [FIXED_SCALE] / [FLOOR] of boost).
 *
 * Only the normalisation stage sees it: beat detection, downbeat detection and the silence gate keep the
 * raw band RMS. Ported to `web/dsp.js` (AudioAgc); `WebAudioAgcVectorsTest` keeps the two identical.
 * No allocation; audio thread only.
 */
class AudioAgc {
    /** The section detector the long-term levels follow. */
    val watch = SectionWatch()

    private val ref = FloatArray(BANDS)
    private val short = FloatArray(BANDS)
    private var time = 0f
    private var quiet = 0f

    /** Feeds this block's raw RMS per band over [dtSec] seconds; read the divisors with [divisor]. */
    fun update(amp: Float, bass: Float, mid: Float, high: Float, dtSec: Double) {
        val dt = dtSec.toFloat()
        quiet = if (amp < SILENT_RMS) quiet + dt else 0f
        if (quiet > SILENCE_RESET) { // silence: forget the old level and wait, so the next feed levels in at once
            if (time > 0f) restart()
            return
        }
        watch.update(min(1f, bass / FIXED_SCALE), min(1f, amp / FIXED_SCALE), dt)
        time += dt
        val frozen = watch.holding
        follow(AMP, amp, dt, frozen)
        follow(BASS, bass, dt, frozen)
        follow(MID, mid, dt, frozen)
        follow(HIGH, high, dt, frozen)
    }

    private fun follow(band: Int, rms: Float, dt: Float, frozen: Boolean) {
        short[band] += (rms - short[band]) * min(1f, dt / SHORT_TAU)
        if (watch.rebased) ref[band] = short[band]
        else if (!frozen) ref[band] += (rms - ref[band]) * min(1f, dt / min(LONG_TAU, time))
    }

    /** Current divisor for [band] ([AMP]..[HIGH]). */
    fun divisor(band: Int): Float = max(ref[band] * SCALE[band], FLOOR)

    /** How much louder than the fixed scale [band] is being made (1 = unchanged). For the meter. */
    fun boost(band: Int): Float = FIXED_SCALE / divisor(band)

    fun reset() {
        restart()
        quiet = 0f
    }

    private fun restart() {
        watch.reset()
        ref.fill(0f)
        short.fill(0f)
        time = 0f
    }

    companion object {
        const val AMP = 0
        const val BASS = 1
        const val MID = 2
        const val HIGH = 3
        const val BANDS = 4

        /** The pre-AGC normalisation scale: band RMS of this reads 1.0. Flux scales are relative to it. */
        const val FIXED_SCALE = 0.25f
        const val FLOOR = 0.02f
        const val LONG_TAU = 20f        // s, the same reference time as the section watch
        const val SHORT_TAU = 1.5f      // s, the "now" level a new normal jumps to
        const val SILENT_RMS = 0.003f   // about -50 dBFS broadband...
        const val SILENCE_RESET = 2f    // ...for this long (s) starts afresh

        /**
         * Long-term mean to divisor, per band (amp, bass, mid, high). Calibrated on four 10-minute DJ mixes
         * (2026-10-28) so steady music lands where the earlier peak-following gain (8 s release, 0.85
         * headroom) put it, which the built-in responses are tuned for. Spikier bands need more.
         */
        val SCALE = floatArrayOf(1.5f, 2.0f, 2.0f, 2.7f)
    }
}
