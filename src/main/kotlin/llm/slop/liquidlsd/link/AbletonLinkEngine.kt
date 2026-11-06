package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.utils.TimeSource
import mu.KotlinLogging

/**
 * The app's Ableton Link session. Uses the bundled native library, and reports itself unavailable
 * ([isAvailable]) if that cannot load on this machine.
 *
 * Only the render thread and the UI touch the backend, because Link's app-side calls take a lock. The audio
 * thread reads [latestTempo] and [latestBeat], which [LinkSyncManager.tick] refreshes every frame.
 */
object AbletonLinkEngine {
    private val logger = KotlinLogging.logger {}

    @Volatile var isEnabled: Boolean = false
        private set

    @Volatile var quantum: Double = 4.0 // Default 4 beats = 1 bar

    /** The session tempo at the last [refresh]; safe to read from the audio thread. */
    @Volatile var latestTempo: Double = 120.0
        private set

    /** The session beat at the last [refresh]; safe to read from the audio thread. */
    @Volatile var latestBeat: Double = 0.0
        private set

    private var activeBackend: LinkBackend = NoOpLinkBackend()
    @Volatile private var isInitialized = false
    @Volatile private var available = false

    /** False if the native library cannot load here, in which case Link cannot be enabled. */
    val isAvailable: Boolean
        get() {
            if (!isInitialized) init()
            return available
        }

    @Synchronized
    fun init(initialBpm: Double = 120.0): Boolean {
        if (isInitialized) return true
        val native = NativeJniLinkBackend()
        if (native.init(initialBpm)) {
            activeBackend = native
            available = true
            logger.info { "Ableton Link ready." }
        } else {
            activeBackend = NoOpLinkBackend().apply { init(initialBpm) }
            available = false
        }
        isInitialized = true
        return true
    }

    /** Runs the engine on [backend] from now on (tests). */
    @Synchronized
    internal fun installBackend(backend: LinkBackend) {
        shutdown()
        activeBackend = backend
        available = true
        isInitialized = true
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (!isInitialized) init()
        if (enabled && !available) {
            logger.warn { "Ableton Link is unavailable on this machine." }
            return
        }
        isEnabled = enabled
        activeBackend.setEnabled(enabled)
        logger.info { "Ableton Link ${if (enabled) "on" else "off"}" }
    }

    fun getActiveBackendName(): String = activeBackend.name

    fun getNumPeers(): Int = if (isEnabled) activeBackend.getNumPeers() else 0

    fun clockMicros(): Long = activeBackend.clockMicros()

    fun getTempo(): Double = activeBackend.getTempo()

    fun setTempo(bpm: Double) {
        activeBackend.setTempo(bpm)
    }

    fun getBeatAtTime(timeUs: Long, targetQuantum: Double = quantum): Double = activeBackend.getBeatAtTime(timeUs, targetQuantum)

    /** Asks for [beat] now. Moves the timeline only when alone in the session. */
    fun requestBeat(beat: Double) {
        activeBackend.requestBeatAtTime(beat, activeBackend.clockMicros(), quantum)
    }

    /** The backend, for [LinkLeader]. */
    internal val backend: LinkBackend get() = activeBackend

    /** Samples the session on the render thread for the audio thread to read. */
    fun refresh() {
        if (!isEnabled) return
        latestTempo = activeBackend.getTempo()
        latestBeat = activeBackend.getBeatAtTime(activeBackend.clockMicros(), quantum)
    }

    /** Runs the app's clock from the session: the [llm.slop.liquidlsd.audio.ClockSource.LINK] clock source. */
    fun updateClockAnchor() {
        if (!isEnabled) return
        // Read together: the clock, the tempo and the beat should all describe the same instant.
        val timeNs = TimeSource.getTimeNanos()
        val timeUs = activeBackend.clockMicros()
        val bpm = activeBackend.getTempo()
        val beat = activeBackend.getBeatAtTime(timeUs, quantum)
        latestTempo = bpm
        latestBeat = beat
        CVRegistry.updateBeatAnchor(beat, bpm.toFloat(), timeNs)
    }

    /**
     * Leaves the session and frees the backend. [isEnabled] goes false first so a frame that is
     * mid-update does not reach the freed native handle.
     */
    @Synchronized
    fun shutdown() {
        if (isInitialized) {
            isEnabled = false
            activeBackend.close()
            activeBackend = NoOpLinkBackend()
            available = false
            isInitialized = false
        }
    }
}
