package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.ClockSource
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Central state machine and orchestrator for Ableton Link sync mode management.
 * Controls mode transitions between [SyncMode.DISABLED], [SyncMode.LINK_FOLLOWER], and [SyncMode.AUDIO_BROADCAST].
 */
object LinkSyncManager {
    private val logger = KotlinLogging.logger {}
    private val transitionLock = ReentrantLock()

    @Volatile
    var currentMode: SyncMode = SyncMode.DISABLED
        private set

    private val eventSinks = CopyOnWriteArrayList<AudioTempoEventSink>()

    /**
     * Damping filter for conditioning raw audio beat detector output before network broadcast.
     */
    val signalDamping = BeatTrackToLinkDamping(
        downstreamSink = object : AudioTempoEventSink {
            override fun onTempoCommitted(bpm: Double) {
                if (currentMode == SyncMode.AUDIO_BROADCAST) {
                    AbletonLinkEngine.setTempo(bpm)
                }
                dispatchToSinksTempo(bpm)
            }

            override fun onBeatAligned(beatTime: Double, microsecondTimestamp: Long, quantum: Double) {
                if (currentMode == SyncMode.AUDIO_BROADCAST) {
                    AbletonLinkEngine.requestBeatAtTime(beatTime, microsecondTimestamp, quantum)
                }
                dispatchToSinksBeat(beatTime, microsecondTimestamp, quantum)
            }
        }
    )

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeSyncJob: Job? = null

    /**
     * Returns true if Link sync is active ([LINK_FOLLOWER] or [AUDIO_BROADCAST]) and backend is enabled.
     */
    val isLinked: Boolean
        get() = currentMode != SyncMode.DISABLED && AbletonLinkEngine.isEnabled

    /**
     * Returns the number of connected Ableton Link peers on the network.
     */
    val peersCount: Int
        get() = if (isLinked) AbletonLinkEngine.getNumPeers() else 0

    /**
     * Returns the currently active BPM based on the current sync mode.
     */
    val activeBpm: Double
        get() = when (currentMode) {
            SyncMode.LINK_FOLLOWER -> AbletonLinkEngine.getTempo()
            SyncMode.AUDIO_BROADCAST, SyncMode.DISABLED -> AudioEngine.getEstimatedBpm().toDouble()
        }

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
     * Returns true if audio beat tracking is actively transmitting tempo/phase updates to the Link network session.
     */
    val isTransmitting: Boolean
        get() = currentMode == SyncMode.AUDIO_BROADCAST && AbletonLinkEngine.isEnabled && AbletonLinkEngine.isConnected()

    /**
     * Registers an output event sink to receive audio beat tracker tempo and beat events during [SyncMode.AUDIO_BROADCAST].
     */
    fun registerEventSink(sink: AudioTempoEventSink) {
        if (!eventSinks.contains(sink)) {
            eventSinks.add(sink)
        }
    }

    /**
     * Unregisters an output event sink.
     */
    fun unregisterEventSink(sink: AudioTempoEventSink) {
        eventSinks.remove(sink)
    }

    /**
     * Clears all registered event sinks.
     */
    fun clearEventSinks() {
        eventSinks.clear()
    }

    /**
     * Transitions the synchronization state machine to the target [newMode].
     * Thread-safe, non-reentrant, and cancels active coroutine jobs to eliminate feedback echoes.
     */
    fun setSyncMode(newMode: SyncMode) {
        transitionLock.withLock {
            if (currentMode == newMode) return

            logger.info { "LinkSyncManager: Transitioning mode from $currentMode to $newMode" }

            // 1. Immediately cancel active background sync jobs to break any echo loops
            activeSyncJob?.cancel()
            activeSyncJob = null
            signalDamping.reset(AudioEngine.getEstimatedBpm().toDouble())

            val oldMode = currentMode
            currentMode = newMode

            try {
                when (newMode) {
                    SyncMode.DISABLED -> {
                        AbletonLinkEngine.setEnabled(false)
                        if (AudioEngine.clockSource == ClockSource.ABLETON_LINK) {
                            AudioEngine.clockSource = ClockSource.AUDIO_TRACKER
                        }
                    }

                    SyncMode.LINK_FOLLOWER -> {
                        if (!AbletonLinkEngine.isEnabled) {
                            AbletonLinkEngine.setEnabled(true)
                        }
                        AudioEngine.clockSource = ClockSource.ABLETON_LINK
                    }

                    SyncMode.AUDIO_BROADCAST -> {
                        if (!AbletonLinkEngine.isEnabled) {
                            AbletonLinkEngine.setEnabled(true)
                        }
                        if (AudioEngine.clockSource == ClockSource.ABLETON_LINK) {
                            AudioEngine.clockSource = ClockSource.AUDIO_TRACKER
                        }

                        // Prepare background broadcast coroutine worker for active monitoring
                        activeSyncJob = syncScope.launch {
                            logger.debug { "LinkSyncManager: Audio broadcast job activated." }
                            try {
                                awaitCancellation()
                            } finally {
                                logger.debug { "LinkSyncManager: Audio broadcast job deactivated." }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error during LinkSyncManager transition to $newMode" }
                currentMode = oldMode
            }
        }
    }

    /**
     * Toggles through sync modes in sequence: DISABLED -> LINK_FOLLOWER -> AUDIO_BROADCAST -> DISABLED.
     */
    fun toggleSyncMode(): SyncMode {
        val nextMode = when (currentMode) {
            SyncMode.DISABLED -> SyncMode.LINK_FOLLOWER
            SyncMode.LINK_FOLLOWER -> SyncMode.AUDIO_BROADCAST
            SyncMode.AUDIO_BROADCAST -> SyncMode.DISABLED
        }
        setSyncMode(nextMode)
        return nextMode
    }

    /**
     * Dispatches a raw tempo change event from audio beat tracker through the damping filter.
     * Only active when operating in [SyncMode.AUDIO_BROADCAST].
     */
    fun publishTempoCommitted(bpm: Double) {
        if (currentMode != SyncMode.AUDIO_BROADCAST) return
        signalDamping.processRawBpm(bpm)
    }

    /**
     * Dispatches a raw beat/phase alignment event from audio beat tracker through the damping filter.
     * Only active when operating in [SyncMode.AUDIO_BROADCAST].
     */
    fun publishBeatAligned(beatTime: Double, microsecondTimestamp: Long = 0L, quantum: Double = 4.0) {
        if (currentMode != SyncMode.AUDIO_BROADCAST) return
        signalDamping.processBeatOnset(beatTime, microsecondTimestamp, quantum)
    }

    private fun dispatchToSinksTempo(bpm: Double) {
        val count = eventSinks.size
        for (i in 0 until count) {
            try {
                eventSinks[i].onTempoCommitted(bpm)
            } catch (e: Exception) {
                logger.warn(e) { "Error in AudioTempoEventSink.onTempoCommitted" }
            }
        }
    }

    private fun dispatchToSinksBeat(beatTime: Double, microsecondTimestamp: Long, quantum: Double) {
        val count = eventSinks.size
        for (i in 0 until count) {
            try {
                eventSinks[i].onBeatAligned(beatTime, microsecondTimestamp, quantum)
            } catch (e: Exception) {
                logger.warn(e) { "Error in AudioTempoEventSink.onBeatAligned" }
            }
        }
    }

    /**
     * Resets the manager state (used for testing or application shutdown).
     */
    fun reset() {
        transitionLock.withLock {
            activeSyncJob?.cancel()
            activeSyncJob = null
            eventSinks.clear()
            signalDamping.reset()
            currentMode = SyncMode.DISABLED
        }
    }
}
