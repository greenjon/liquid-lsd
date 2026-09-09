package llm.slop.liquidlsd.link

/**
 * Common interface for Ableton Link implementations (Native JNI, Carabiner TCP, No-Op).
 */
interface LinkBackend {
    val name: String

    /**
     * Initializes the Link backend with an initial BPM.
     * @return true if initialization succeeded.
     */
    fun init(initialBpm: Double): Boolean

    /**
     * Releases backend resources and disconnects from sessions/sockets.
     */
    fun close()

    /**
     * Enables or disables Link network session participation.
     */
    fun setEnabled(enabled: Boolean)

    /**
     * Returns true if Link session is active.
     */
    fun isEnabled(): Boolean

    /**
     * Returns the number of connected Ableton Link peers on the local network.
     */
    fun getNumPeers(): Int

    /**
     * Returns the current session tempo in BPM.
     */
    fun getTempo(): Double

    /**
     * Sets the session tempo in BPM, propagating to all connected peers.
     */
    fun setTempo(bpm: Double)

    /**
     * Returns the beat timeline value at the specified microsecond host timestamp.
     */
    fun getBeatAtTime(timeUs: Long, quantum: Double): Double

    /**
     * Returns the phase [0.0, quantum) at the specified microsecond host timestamp.
     */
    fun getPhaseAtTime(timeUs: Long, quantum: Double): Double

    /**
     * Requests aligning beat phase to targetBeat at timeUs for the given quantum.
     */
    fun requestBeatAtTime(beat: Double, quantum: Double)

    /**
     * Enables or disables Start/Stop Transport Synchronization across Link peers.
     */
    fun setStartStopSyncEnabled(enabled: Boolean)

    /**
     * Returns true if Start/Stop sync is enabled.
     */
    fun isStartStopSyncEnabled(): Boolean

    /**
     * Returns true if Link transport state is currently playing.
     */
    fun isPlaying(): Boolean

    /**
     * Sets transport playing state (start/stop) across Link peers.
     */
    fun setIsPlaying(isPlaying: Boolean)
}
