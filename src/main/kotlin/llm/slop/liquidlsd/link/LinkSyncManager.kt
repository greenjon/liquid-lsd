package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.audio.AudioEngine
import mu.KotlinLogging

/**
 * Session status accessor and audio tempo damping orchestrator for Ableton Link synchronization.
 */
object LinkSyncManager {
    private val logger = KotlinLogging.logger {}

    /**
     * Signal damping filter for conditioning raw audio beat detector output before committing to Ableton Link.
     */
    val signalDamping = BeatTrackToLinkDamping(
        onTempoCommitted = { bpm ->
            if (AbletonLinkEngine.isEnabled) {
                AbletonLinkEngine.setTempo(bpm)
            }
        },
        onBeatAligned = { beatTime, microsecondTimestamp, quantum ->
            if (AbletonLinkEngine.isEnabled) {
                AbletonLinkEngine.requestBeatAtTime(beatTime, microsecondTimestamp, quantum)
            }
        }
    )

    /**
     * Returns true if Ableton Link session is enabled.
     */
    val isLinked: Boolean
        get() = AbletonLinkEngine.isEnabled

    /**
     * Returns the number of connected Ableton Link peers on the network.
     */
    val peersCount: Int
        get() = if (isLinked) AbletonLinkEngine.getNumPeers() else 0

    /**
     * Returns the currently active BPM based on Ableton Link state or local audio engine estimate.
     */
    val activeBpm: Double
        get() = if (isLinked) AbletonLinkEngine.getTempo() else AudioEngine.getEstimatedBpm().toDouble()

    /**
     * Active tempo in BPM formatted for UI display (1 decimal place).
     */
    val formattedActiveBpm: String
        get() = String.format(java.util.Locale.US, "%.1f", activeBpm)

    /**
     * Audio beat tracker tracking confidence/stability metric [0.0f, 1.0f].
     */
    val confidence: Float
        get() = AudioEngine.confidence

    /**
     * Tracking confidence percentage [0, 100].
     */
    val confidencePercent: Int
        get() = (confidence * 100f).coerceIn(0f, 100f).toInt()

    /**
     * Dispatches a raw tempo change event from audio beat tracker through the damping filter.
     */
    fun publishTempoCommitted(bpm: Double) {
        if (!AbletonLinkEngine.isEnabled) return
        signalDamping.processRawBpm(bpm)
    }

    /**
     * Dispatches a raw beat/phase alignment event from audio beat tracker through the damping filter.
     */
    fun publishBeatAligned(beatTime: Double, microsecondTimestamp: Long = 0L, quantum: Double = 4.0) {
        if (!AbletonLinkEngine.isEnabled) return
        signalDamping.processBeatOnset(beatTime, microsecondTimestamp, quantum)
    }

    /**
     * Resets manager and signal damping state.
     */
    fun reset() {
        signalDamping.reset()
    }
}
