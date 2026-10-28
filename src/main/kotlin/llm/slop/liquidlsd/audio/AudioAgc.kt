package llm.slop.liquidlsd.audio

import kotlin.math.exp
import kotlin.math.max

/**
 * Per-band automatic gain for the audio CV signals (`audio_amp/bass/mid/high` and their flux). Always on:
 * Audio Response is the only reactivity control the user sees.
 *
 * Only the normalisation stage sees it: beat detection, downbeat detection and the silence gate keep
 * working on the raw band RMS. Each band tracks a peak envelope of its RMS (instant attack, [RELEASE_SEC]
 * release) and normalises against `max(envelope * HEADROOM, FLOOR)`, so the recent peaks of the music
 * land just over 1.0 whatever the input level. [FLOOR] stops silence and room noise from being boosted into
 * constant motion (at most [FIXED_SCALE] / [FLOOR] of boost).
 *
 * No allocation; audio thread only.
 */
class AudioAgc {
    private val envelope = FloatArray(BANDS)

    /** Feeds this block's RMS for [band] ([AMP]..[HIGH]) and returns the divisor to normalise it with. */
    fun update(band: Int, rms: Float, dtSec: Double): Float {
        val decay = exp(-dtSec / RELEASE_SEC).toFloat()
        envelope[band] = max(rms, envelope[band] * decay)
        return divisor(band)
    }

    /** Current divisor for [band]. */
    fun divisor(band: Int): Float = max(envelope[band] * HEADROOM, FLOOR)

    /** How much louder than the fixed scale [band] is being made (1 = unchanged). For the meter. */
    fun boost(band: Int): Float = FIXED_SCALE / divisor(band)

    fun reset() = envelope.fill(0f)

    companion object {
        const val AMP = 0
        const val BASS = 1
        const val MID = 2
        const val HIGH = 3
        const val BANDS = 4

        /** The pre-AGC normalisation scale: band RMS of this reads 1.0. Flux scales are relative to it. */
        const val FIXED_SCALE = 0.25f
        const val FLOOR = 0.02f
        const val RELEASE_SEC = 8.0

        /**
         * Recent peaks normalise to 1 / HEADROOM, so the top of the music clips slightly. Fixed: it was a
         * user setting, but it only scaled every signal the way Audio Response does. Recipes are tuned
         * against this value at 100% Response.
         */
        const val HEADROOM = 0.85f
    }
}
