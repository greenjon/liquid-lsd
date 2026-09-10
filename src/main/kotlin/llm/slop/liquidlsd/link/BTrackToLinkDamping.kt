package llm.slop.liquidlsd.link

import mu.KotlinLogging
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs

/**
 * Signal conditioning filter between BTrack raw audio beat detector and Ableton Link / Carabiner.
 *
 * Prevents network tempo spam and micro-warping in connected Link peers by applying:
 * 1. **Sanity Range Filtering**: Discards raw BPM detections outside `[minBpm, maxBpm]` (60–200 BPM).
 * 2. **Rolling Median + EMA Smoothing**: Uses a 7-sample rolling median window to remove percussive outliers,
 *    followed by an alpha Exponential Moving Average (EMA) for continuous trajectory tracking.
 * 3. **Quantization & Hysteresis**: Requires a divergence of >= 0.5 BPM sustained over 4 consecutive beats
 *    before committing an outbound tempo change.
 * 4. **Major Phase Error Realignment**: Measures phase error relative to the network Link timeline clock and
 *    only publishes beat realignments when phase error >= 0.5 beats (half-beat), ignoring acoustic onset jitter.
 */
class BTrackToLinkDamping(
    var minBpm: Double = 60.0,
    var maxBpm: Double = 200.0,
    var medianWindowSize: Int = 7,
    var emaAlpha: Double = 0.20,
    var hysteresisThresholdBpm: Double = 0.5,
    var sustainedBeatsThreshold: Int = 4,
    var phaseErrorThresholdBeats: Double = 0.5,
    var quantum: Double = 4.0,
    var downstreamSink: AudioTempoEventSink? = null
) : AudioTempoEventSink {

    private val logger = KotlinLogging.logger {}
    private val lock = ReentrantLock()

    @Volatile
    var lastPublishedBpm: Double = 120.0
        private set

    @Volatile
    var currentStabilizedBpm: Double = 120.0
        private set

    private val bpmHistory = DoubleArray(16)
    private var historyCount = 0
    private var historyIndex = 0

    private var emaBpm: Double = 120.0
    private var isEmaInitialized = false
    private var pendingDivergenceCount = 0

    /**
     * Processes a raw BPM sample from BTrack.
     * Applies sanity bounds, rolling median, EMA smoothing, and hysteresis checks.
     */
    fun processRawBpm(rawBpm: Double) {
        // 1. Sanity bounds check
        if (rawBpm < minBpm || rawBpm > maxBpm || rawBpm.isNaN()) {
            return
        }

        lock.withLock {
            // 2. Add to rolling median history ring buffer
            val capacity = medianWindowSize.coerceIn(1, bpmHistory.size)
            bpmHistory[historyIndex] = rawBpm
            historyIndex = (historyIndex + 1) % capacity
            if (historyCount < capacity) historyCount++

            // 3. Compute rolling median
            val median = calculateMedian()

            // 4. Compute EMA smoothing
            if (!isEmaInitialized) {
                emaBpm = median
                isEmaInitialized = true
            } else {
                emaBpm = (emaBpm * (1.0 - emaAlpha)) + (median * emaAlpha)
            }
            currentStabilizedBpm = emaBpm

            // 5. Hysteresis & sustained beat counter
            val delta = abs(rawBpm - lastPublishedBpm)
            if (delta >= hysteresisThresholdBpm) {
                pendingDivergenceCount++
                if (pendingDivergenceCount >= sustainedBeatsThreshold) {
                    lastPublishedBpm = emaBpm
                    pendingDivergenceCount = 0
                    logger.info { "BTrackToLinkDamping: Committed outbound tempo update -> %.2f BPM (delta=%.2f)".format(lastPublishedBpm, delta) }
                    downstreamSink?.onTempoCommitted(lastPublishedBpm)
                }
            } else {
                pendingDivergenceCount = 0
            }
        }
    }

    /**
     * Processes a beat onset event from BTrack.
     * Measures phase error relative to Ableton Link's timeline clock and only triggers
     * realignment if phase error exceeds [phaseErrorThresholdBeats] (>= 0.5 beats).
     */
    fun processBeatOnset(beatTime: Double, microsecondTimestamp: Long = 0L, targetQuantum: Double = quantum) {
        val timestampUs = if (microsecondTimestamp != 0L) microsecondTimestamp else (System.nanoTime() / 1000)

        lock.withLock {
            val q = targetQuantum.coerceAtLeast(1.0)
            val expectedBeat = AbletonLinkEngine.getBeatAtTime(timestampUs, q)

            val rawDiff = (beatTime - expectedBeat) % q
            var wrappedDiff = rawDiff
            if (wrappedDiff > q / 2.0) wrappedDiff -= q
            if (wrappedDiff < -q / 2.0) wrappedDiff += q

            val phaseError = abs(wrappedDiff)

            if (phaseError >= phaseErrorThresholdBeats) {
                logger.info { "BTrackToLinkDamping: Major phase error detected (%.2f beats >= %.2f threshold). Aligning phase.".format(phaseError, phaseErrorThresholdBeats) }
                downstreamSink?.onBeatAligned(beatTime, timestampUs, q)
            }
        }
    }

    override fun onTempoCommitted(bpm: Double) {
        processRawBpm(bpm)
    }

    override fun onBeatAligned(beatTime: Double, microsecondTimestamp: Long, quantum: Double) {
        processBeatOnset(beatTime, microsecondTimestamp, quantum)
    }

    private fun calculateMedian(): Double {
        if (historyCount == 0) return 120.0
        val temp = DoubleArray(historyCount)
        System.arraycopy(bpmHistory, 0, temp, 0, historyCount)
        temp.sort()
        val mid = historyCount / 2
        return if (historyCount % 2 == 1) {
            temp[mid]
        } else {
            (temp[mid - 1] + temp[mid]) / 2.0
        }
    }

    /**
     * Resets filter internal state.
     */
    fun reset(initialBpm: Double = 120.0) {
        lock.withLock {
            lastPublishedBpm = initialBpm
            currentStabilizedBpm = initialBpm
            emaBpm = initialBpm
            isEmaInitialized = false
            pendingDivergenceCount = 0
            historyCount = 0
            historyIndex = 0
            bpmHistory.fill(0.0)
        }
    }
}
