package llm.slop.liquidlsd.audio

import llm.slop.liquidlsd.utils.TimeSource

/**
 * Controller for tracking VJ tap tempo cadence, averaging tap intervals, and dispatching tempo updates.
 *
 * All operations are allocation-free during runtime after initial instantiation.
 */
class TapTempoController(
    private val audioEngine: AudioEngine,
    val maxTaps: Int = 8,
    val timeoutNs: Long = 2_000_000_000L // 2.0s = 30 BPM cutoff
) {
    private val tapHistory = LongArray(maxTaps)
    private var tapCount = 0
    private var head = 0
    @Volatile var lastTapTimeNs: Long = 0L
        private set
    @Volatile var lastCalculatedBpm: Float? = null
        private set

    /**
     * Registers a tap. Returns newly calculated BPM if 2 or more taps in sequence, or null on first tap.
     */
    @Synchronized
    fun tap(timestampNs: Long = TimeSource.getTimeNanos()): Float? {
        if (lastTapTimeNs > 0L && (timestampNs - lastTapTimeNs) > timeoutNs) {
            tapCount = 0
            head = 0
        }

        tapHistory[head] = timestampNs
        head = (head + 1) % maxTaps
        tapCount++
        lastTapTimeNs = timestampNs

        if (tapCount >= 2) {
            val available = minOf(tapCount, maxTaps)
            // Calculate intervals between consecutive taps
            var totalIntervalSec = 0.0
            val intervals = available - 1

            // Reconstruct chronological order from circular buffer
            val startIdx = if (tapCount < maxTaps) 0 else head
            for (i in 0 until intervals) {
                val idx1 = (startIdx + i) % maxTaps
                val idx2 = (startIdx + i + 1) % maxTaps
                val dtSec = (tapHistory[idx2] - tapHistory[idx1]) / 1_000_000_000.0
                if (dtSec <= 0.0 || dtSec > (timeoutNs / 1_000_000_000.0)) {
                    // Stale or invalid interval
                    tapCount = 1
                    audioEngine.registerTap(null, timestampNs)
                    lastCalculatedBpm = null
                    return null
                }
                totalIntervalSec += dtSec
            }

            val avgIntervalSec = totalIntervalSec / intervals
            val rawBpm = (60.0 / avgIntervalSec).toFloat()
            val bpmFloor = audioEngine.beatDetector.engine.bpmFloor
            val bpmCeiling = audioEngine.beatDetector.engine.bpmCeiling
            val clampedBpm = rawBpm.coerceIn(bpmFloor, bpmCeiling)
            val roundedBpm = kotlin.math.round(clampedBpm * 10.0f) / 10.0f

            lastCalculatedBpm = roundedBpm
            audioEngine.registerTap(roundedBpm, timestampNs)
            return roundedBpm
        } else {
            audioEngine.registerTap(null, timestampNs)
            return null
        }
    }

    /**
     * Returns current tap count in active cadence, or 0 if timed out.
     */
    fun getActiveTapCount(nowNs: Long = TimeSource.getTimeNanos()): Int {
        val last = lastTapTimeNs
        if (last == 0L || (nowNs - last) > timeoutNs) {
            return 0
        }
        return tapCount
    }

    /**
     * Returns visual flash intensity [0..1] for UI feedback, decaying over 300ms.
     */
    fun getFlashIntensity(nowNs: Long = TimeSource.getTimeNanos()): Float {
        val last = lastTapTimeNs
        if (last == 0L) return 0f
        val elapsedNs = nowNs - last
        val flashDurationNs = 300_000_000L // 300ms
        if (elapsedNs >= flashDurationNs) return 0f
        return (1.0f - (elapsedNs.toFloat() / flashDurationNs.toFloat())).coerceIn(0f, 1f)
    }

    @Synchronized
    fun reset() {
        tapCount = 0
        head = 0
        lastTapTimeNs = 0L
        lastCalculatedBpm = null
    }
}
