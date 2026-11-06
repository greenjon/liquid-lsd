package llm.slop.liquidlsd.link

/**
 * What the app needs from an Ableton Link session: [NativeJniLinkBackend], or [NoOpLinkBackend] where the
 * native library cannot load.
 *
 * Every `timeUs` is on [clockMicros], not System.nanoTime: on Linux Link uses CLOCK_MONOTONIC_RAW, which
 * drifts away from the JVM's clock with uptime and would put us out of phase with our peers.
 */
interface LinkBackend {
    val name: String

    /** @return true if initialization succeeded. */
    fun init(initialBpm: Double): Boolean

    /** Leaves the session and releases native resources. */
    fun close()

    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean

    /** Other apps in the session, not counting this one. */
    fun getNumPeers(): Int

    /** Link's clock, in microseconds. */
    fun clockMicros(): Long

    fun getTempo(): Double

    /** Sets the session tempo, for every peer. */
    fun setTempo(bpm: Double)

    /** The beat at [timeUs]. Its phase within [quantum] is shared with the session; its magnitude is ours. */
    fun getBeatAtTime(timeUs: Long, quantum: Double): Double

    /**
     * Puts [beat] at [timeUs]. Alone in a session this moves the timeline at once; with peers it only
     * picks our beat numbering, at the next time the session's phase matches. It cannot move the session.
     */
    fun requestBeatAtTime(beat: Double, timeUs: Long, quantum: Double)

    /**
     * Puts [beat] at [timeUs] for the whole session, whatever the peers are doing. Peers hear this as a
     * jump, so [LinkLeader] keeps it for large errors that hold.
     */
    fun forceBeatAtTime(beat: Double, timeUs: Long, quantum: Double)
}
