package llm.slop.liquidlsd.audio

import kotlin.math.exp
import kotlin.math.max

/**
 * Per-band automatic gain for the audio CV signals (`audio_amp/bass/mid/high` and their flux).
 *
 * Only the normalisation stage sees it: beat detection, downbeat detection and the silence gate keep
 * working on the raw band RMS. Each band tracks a peak envelope of its RMS (instant attack, [RELEASE_SEC]
 * release) and normalises against `max(envelope * headroom, FLOOR)`, so the recent peaks of the music
 * land near 1.0 whatever the input level. [FLOOR] stops silence and room noise from being boosted into
 * constant motion (at most [FIXED_SCALE] / [FLOOR] of boost).
 *
 * With [enabled] false every divisor is [FIXED_SCALE], the pre-AGC behaviour. No allocation; audio thread only.
 */
class AudioAgc {
    @Volatile var enabled: Boolean = true
    @Volatile var headroom: Float = DEFAULT_HEADROOM

    private val envelope = FloatArray(BANDS)

    /** Feeds this block's RMS for [band] ([AMP]..[HIGH]) and returns the divisor to normalise it with. */
    fun update(band: Int, rms: Float, dtSec: Double): Float {
        val decay = exp(-dtSec / RELEASE_SEC).toFloat()
        envelope[band] = max(rms, envelope[band] * decay)
        return divisor(band)
    }

    /** Current divisor for [band]. */
    fun divisor(band: Int): Float {
        if (!enabled) return FIXED_SCALE
        return max(envelope[band] * headroom.coerceIn(MIN_HEADROOM, 1f), FLOOR)
    }

    /** How much louder than the fixed scale [band] is being made (1 = unchanged). For the meter. */
    fun boost(band: Int): Float = FIXED_SCALE / divisor(band)

    fun reset() = envelope.fill(0f)

    companion object {
        const val AMP = 0
        const val BASS = 1
        const val MID = 2
        const val HIGH = 3
        const val BANDS = 4

        /** The pre-AGC normalisation scale: band RMS of this reads 1.0. */
        const val FIXED_SCALE = 0.25f
        const val FLOOR = 0.02f
        const val RELEASE_SEC = 8.0
        const val DEFAULT_HEADROOM = 0.85f
        const val MIN_HEADROOM = 0.5f
    }
}
