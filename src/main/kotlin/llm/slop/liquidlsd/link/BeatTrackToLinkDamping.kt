package llm.slop.liquidlsd.link

import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Signal conditioning filter between raw audio beat detector and Ableton Link / Carabiner.
 *
 * Prevents network tempo spam and micro-warping in connected Link peers by applying:
 * 1. **Sanity Range Filtering**: Discards raw BPM detections outside `[minBpm, maxBpm]` (60–200 BPM).
 * 2. **Rolling Median + EMA Smoothing**: Uses a 7-sample rolling median window to remove percussive
 *    outliers, followed by an alpha EMA for continuous trajectory tracking.
 * 3. **Quantization & Hysteresis**: Requires >= 0.5 BPM divergence sustained over 4 consecutive beats
 *    before committing an outbound tempo change.
 * 4. **Major Phase Error Realignment**: Measures phase error against the Link timeline clock and only
 *    publishes beat realignments when phase error >= 0.5 beats (half-beat).
 *
 * ## Real-time safety
 * `processRawBpm` and `processBeatOnset` are called from the JACK audio processing thread.
 * They contain **zero locks, zero allocations, and zero logging** (logger calls happen on a
 * background thread via the `drainPendingLog` mechanism).
 */
class BeatTrackToLinkDamping(
    var minBpm: Double = 60.0,
    var maxBpm: Double = 200.0,
    var medianWindowSize: Int = 7,
    var emaAlpha: Double = 0.20,
    var hysteresisThresholdBpm: Double = 0.5,
    var sustainedBeatsThreshold: Int = 4,
    var phaseErrorThresholdBeats: Double = 0.5,
    var quantum: Double = 4.0,
    var onTempoCommitted: ((Double) -> Unit)? = null,
    var onBeatAligned: ((Double, Long, Double) -> Unit)? = null
) {

    private val logger = KotlinLogging.logger {}

    // ── Public read-only state (written by audio thread, read by UI thread) ──────────────────
    @Volatile var lastPublishedBpm: Double = 120.0
        private set

    @Volatile var currentStabilizedBpm: Double = 120.0
        private set

    // ── Audio-thread-only state (single writer — no synchronization needed) ──────────────────
    // Ring buffer for rolling median (pre-allocated, never resized)
    private val bpmHistory  = DoubleArray(16)
    // Scratch buffer for sort — pre-allocated to avoid any allocation in calculateMedian()
    private val sortScratch = DoubleArray(16)
    private var historyCount = 0
    private var historyIndex = 0

    private var emaBpm: Double = 120.0
    private var isEmaInitialized = false
    private var pendingDivergenceCount = 0

    // ── Deferred logging (audio thread deposits, non-RT thread drains) ────────────────────────
    // AtomicReference.set() is a single CAS — safe to call from an RT thread.
    // Call drainPendingLog() from a non-RT thread (render loop or logger dispatcher).
    private val pendingLogMessage = AtomicReference<String?>(null)

    /**
     * Drains and logs any message deposited by the audio thread.
     * Call from any non-RT thread — do NOT call from the JACK callback.
     */
    fun drainPendingLog() {
        val msg = pendingLogMessage.getAndSet(null) ?: return
        logger.info { msg }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // Audio-thread entry points — ZERO allocations, ZERO locks, ZERO blocking calls
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Processes a raw BPM sample from the audio beat tracker.
     * RT-safe: no allocations, no locks, no blocking.
     */
    fun processRawBpm(rawBpm: Double) {
        // 1. Sanity bounds
        if (rawBpm < minBpm || rawBpm > maxBpm || rawBpm.isNaN()) return

        // 2. Rolling median ring buffer (single-writer, no lock needed)
        val capacity = medianWindowSize.coerceIn(1, bpmHistory.size)
        bpmHistory[historyIndex] = rawBpm
        historyIndex = (historyIndex + 1) % capacity
        if (historyCount < capacity) historyCount++

        // 3. Rolling median via pre-allocated scratch (zero allocation)
        val median = calculateMedian()

        // 4. EMA smoothing
        if (!isEmaInitialized) {
            emaBpm = median
            isEmaInitialized = true
        } else {
            emaBpm = (emaBpm * (1.0 - emaAlpha)) + (median * emaAlpha)
        }
        currentStabilizedBpm = emaBpm  // @Volatile write — visible to UI thread

        // 5. Hysteresis & sustained beat counter
        val delta = abs(rawBpm - lastPublishedBpm)
        if (delta >= hysteresisThresholdBpm) {
            pendingDivergenceCount++
            if (pendingDivergenceCount >= sustainedBeatsThreshold) {
                lastPublishedBpm = emaBpm  // @Volatile write
                pendingDivergenceCount = 0
                // Deposit log message for non-RT drain — no String.format on the audio thread
                pendingLogMessage.set(
                    "BeatTrackToLinkDamping: Committed outbound tempo -> %.2f BPM (delta=%.2f)".format(lastPublishedBpm, delta)
                )
                downstreamSinkOnTempoCommitted(lastPublishedBpm)
            }
        } else {
            pendingDivergenceCount = 0
        }
    }

    private fun downstreamSinkOnTempoCommitted(bpm: Double) {
        onTempoCommitted?.invoke(bpm)
    }

    /**
     * Processes a beat onset event from the audio beat tracker.
     * RT-safe: no allocations, no locks, no blocking.
     */
    fun processBeatOnset(beatTime: Double, microsecondTimestamp: Long = 0L, targetQuantum: Double = quantum) {
        val timestampUs = if (microsecondTimestamp != 0L) microsecondTimestamp else (System.nanoTime() / 1000)

        val q = targetQuantum.coerceAtLeast(1.0)
        val expectedBeat = AbletonLinkEngine.getBeatAtTime(timestampUs, q)

        val rawDiff = (beatTime - expectedBeat) % q
        var wrappedDiff = rawDiff
        if (wrappedDiff >  q / 2.0) wrappedDiff -= q
        if (wrappedDiff < -q / 2.0) wrappedDiff += q

        val phaseError = abs(wrappedDiff)

        if (phaseError >= phaseErrorThresholdBeats) {
            pendingLogMessage.set(
                "BeatTrackToLinkDamping: Phase error %.2f beats >= %.2f threshold. Aligning.".format(phaseError, phaseErrorThresholdBeats)
            )
            onBeatAligned?.invoke(beatTime, timestampUs, q)
        }
    }

    /**
     * Calculates the rolling median of the [historyCount] samples in [bpmHistory].
     * Uses [sortScratch] to avoid any heap allocation. RT-safe.
     */
    private fun calculateMedian(): Double {
        if (historyCount == 0) return 120.0
        System.arraycopy(bpmHistory, 0, sortScratch, 0, historyCount)
        sortScratch.sort(0, historyCount)   // in-place sub-range sort, allocation-free
        val mid = historyCount / 2
        return if (historyCount % 2 == 1) sortScratch[mid]
        else (sortScratch[mid - 1] + sortScratch[mid]) / 2.0
    }

    /**
     * Resets filter state. Safe to call from any thread.
     * Not called from the audio thread during normal operation.
     */
    fun reset(initialBpm: Double = 120.0) {
        lastPublishedBpm = initialBpm
        currentStabilizedBpm = initialBpm
        emaBpm = initialBpm
        isEmaInitialized = false
        pendingDivergenceCount = 0
        historyCount = 0
        historyIndex = 0
        bpmHistory.fill(0.0)
        pendingLogMessage.set(null)
    }
}
