package llm.slop.liquidlsd.cv

import kotlin.math.round

/**
 * `audio_drop`: 1 on a drop, falling to 0 over [BARS] bars. The section watch calls a drop a second or two
 * after the low end came back, so the hit is dated back by its lag ([report]) and snapped to the nearest
 * beat: it lands on the beat the drop was on, already a little decayed, rather than late at full height.
 * The audio thread reports; the envelope runs on the render thread from the beat clock. A port of
 * `web/energy.js` (DropEnvelope).
 */
class SectionDrop(override val id: String = "audio_drop") : CVSource {
    private var _value = 0f
    override val value: Float get() = _value

    @Volatile private var reportedDrops = 0
    @Volatile private var reportedLagSec = 0f
    private var seenDrops = 0
    private var anchor = Double.NaN

    /** Audio thread: the section watch's drop count and the lag of the latest drop. */
    fun report(drops: Int, lagSec: Float) {
        reportedLagSec = lagSec
        reportedDrops = drops
    }

    override fun update(totalBeats: Double, elapsedSeconds: Double) {
        val drops = reportedDrops
        if (drops > seenDrops) {
            val bpm = CVRegistry.get("bpm").coerceAtLeast(1f)
            anchor = dropBeat(totalBeats, reportedLagSec, bpm)
        }
        seenDrops = drops // a new engine counts from 0 again: follow it down without a hit
        _value = if (anchor.isNaN()) 0f else envelope(totalBeats - anchor, (BARS * CVRegistry.beatsPerBar).toDouble())
    }

    companion object {
        const val BARS = 4

        /** The beat a drop called at [beats], [lagSec] after it happened, is dated to (never in the future). */
        fun dropBeat(beats: Double, lagSec: Float, bpm: Float): Double =
            minOf(round(beats - lagSec * bpm / 60.0), beats)

        /** 1 at the drop, (1 - t)^2 down to 0 after [lengthBeats]. */
        fun envelope(beatsSince: Double, lengthBeats: Double): Float {
            if (beatsSince < 0.0 || beatsSince >= lengthBeats) return 0f
            val left = 1.0 - beatsSince / lengthBeats
            return (left * left).toFloat()
        }
    }
}
