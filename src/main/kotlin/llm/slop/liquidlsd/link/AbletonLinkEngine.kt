package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.cv.CVRegistry
import mu.KotlinLogging

/**
 * Central orchestrator for Ableton Link synchronization.
 * Automatically tries Native JNI backend (`NativeJniLinkBackend`), then Carabiner TCP backend (`CarabinerTcpLinkBackend`),
 * and falls back to `NoOpLinkBackend` if unavailable.
 */
object AbletonLinkEngine {
    private val logger = KotlinLogging.logger {}

    @Volatile var isEnabled: Boolean = false
        private set

    @Volatile var quantum: Double = 4.0 // Default 4 beats = 1 bar

    @Volatile var carabinerHost: String = "127.0.0.1"
    @Volatile var carabinerPort: Int = 17000

    private var activeBackend: LinkBackend = NoOpLinkBackend()
    @Volatile private var isInitialized = false

    /**
     * Initializes the Link engine with the target initial BPM.
     */
    @Synchronized
    fun init(initialBpm: Double = 120.0): Boolean {
        if (isInitialized) return true

        // 1. Try Native JNI Backend
        val jniBackend = NativeJniLinkBackend()
        if (jniBackend.init(initialBpm)) {
            activeBackend = jniBackend
            isInitialized = true
            logger.info { "AbletonLinkEngine: Initialized with native JNI driver." }
            return true
        }

        // 2. Try Carabiner TCP Backend
        val carabinerBackend = CarabinerTcpLinkBackend(carabinerHost, carabinerPort)
        if (carabinerBackend.init(initialBpm)) {
            activeBackend = carabinerBackend
            isInitialized = true
            logger.info { "AbletonLinkEngine: Initialized with Carabiner TCP driver ($carabinerHost:$carabinerPort)." }
            return true
        }

        // 3. Fallback to NoOp
        activeBackend = NoOpLinkBackend().apply { init(initialBpm) }
        isInitialized = true
        logger.info { "AbletonLinkEngine: Operating in fallback No-Op mode." }
        return true
    }

    /**
     * Re-attempts initializing Link backends (e.g. after user changes Carabiner config or native lib path).
     */
    @Synchronized
    fun reinitialize(initialBpm: Double = 120.0) {
        shutdown()
        init(initialBpm)
        if (isEnabled) {
            activeBackend.setEnabled(true)
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!isInitialized) {
            init()
        }
        activeBackend.setEnabled(enabled)
        logger.info { "AbletonLinkEngine: Enabled state set to $enabled" }
    }

    fun getActiveBackendName(): String = activeBackend.name

    fun isConnected(): Boolean = isEnabled && activeBackend.isConnected()

    fun getNumPeers(): Int = if (isEnabled) activeBackend.getNumPeers() else 0

    fun getTempo(): Double = activeBackend.getTempo()

    fun setTempo(bpm: Double) {
        activeBackend.setTempo(bpm)
    }

    fun getBeatAtTime(timeUs: Long): Double = activeBackend.getBeatAtTime(timeUs, quantum)

    fun getBeatAtTime(timeUs: Long, quantum: Double): Double = activeBackend.getBeatAtTime(timeUs, quantum)

    fun getPhaseAtTime(timeUs: Long): Double = activeBackend.getPhaseAtTime(timeUs, quantum)

    /**
     * Aligns beat phase across Link network session.
     */
    fun requestBeatAtTime(beat: Double, timeUs: Long = 0L, targetQuantum: Double = quantum) {
        activeBackend.requestBeatAtTime(beat, timeUs, targetQuantum)
    }

    fun isStartStopSyncEnabled(): Boolean = activeBackend.isStartStopSyncEnabled()

    fun setStartStopSyncEnabled(enabled: Boolean) {
        activeBackend.setStartStopSyncEnabled(enabled)
    }

    fun isPlaying(): Boolean = activeBackend.isPlaying()

    fun setIsPlaying(isPlaying: Boolean) {
        activeBackend.setIsPlaying(isPlaying)
    }

    /**
     * Frame/block tick called when ClockSource == ABLETON_LINK.
     * Synchronizes beat time and tempo directly with CVRegistry.
     *
     * Uses a **single** [timeNs] snapshot for both [getTempo] and [getBeatAtTime] queries so that
     * tempo and beat are always coherent with each other (Link capture-and-commit pattern).
     * Also drains any log messages deferred from the RT audio path.
     */
    fun updateClockAnchor(timeNs: Long = System.nanoTime()) {
        if (!isEnabled) return
        // Drain log messages deposited by the audio-thread damping filter
        // (this runs on the render/GL thread — safe to log here)
        llm.slop.liquidlsd.link.LinkSyncManager.signalDamping.drainPendingLog()

        val timeUs = timeNs / 1000
        // Single atomic snapshot: both values derived from the same timeUs to stay phase-coherent
        val bpm  = activeBackend.getTempo().toFloat()
        val beat = activeBackend.getBeatAtTime(timeUs, quantum)

        CVRegistry.updateBeatAnchor(beat, bpm, timeNs)
    }

    /**
     * Shuts down Link session and releases backend drivers.
     *
     * Sets [isEnabled] to `false` **before** closing the backend so that any concurrent
     * [updateClockAnchor] call on the render/audio thread sees the flag and returns immediately
     * without touching the (already-destroyed) native handle. This closes the use-after-free
     * race window described in AUDIT #3.
     */
    @Synchronized
    fun shutdown() {
        if (isInitialized) {
            isEnabled = false          // ← must precede backend destruction
            activeBackend.close()
            activeBackend = NoOpLinkBackend()
            isInitialized = false
            logger.info { "AbletonLinkEngine: Shutdown complete." }
        }
    }
}
