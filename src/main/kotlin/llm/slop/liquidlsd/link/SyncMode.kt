package llm.slop.liquidlsd.link

/**
 * Synchronization mode governing Ableton Link interaction and master clock ownership.
 */
enum class SyncMode(val displayName: String) {
    /**
     * Ableton Link synchronization is disabled. Engine runs on internal clock (Audio Beat Tracker or Manual Tap).
     */
    DISABLED("Disabled"),

    /**
     * Link Follower mode: Listens to incoming Ableton Link / Carabiner BPM and beat timeline
     * events to drive the visual engine's internal clock. Outbound audio tempo broadcasts are disabled.
     */
    LINK_FOLLOWER("Link Follower"),

    /**
     * Link Broadcast/Master mode: Master clock is driven locally by audio beat detection or manual tap.
     * Incoming Carabiner/Link BPM updates do NOT mutate local master clock. Detected audio tempo & onsets
     * are emitted to a dedicated output event sink for network broadcast.
     */
    AUDIO_BROADCAST("Audio Broadcast")
}

/**
 * Event sink interface for receiving audio tempo updates and beat alignment events during [SyncMode.AUDIO_BROADCAST].
 */
interface AudioTempoEventSink {
    /**
     * Called when a new tempo (BPM) is committed or detected by audio beat tracker / manual tap.
     *
     * @param bpm The target tempo in Beats Per Minute.
     */
    fun onTempoCommitted(bpm: Double)

    /**
     * Called when a downbeat / phase alignment event occurs.
     *
     * @param beatTime The target beat index on the timeline.
     * @param microsecondTimestamp The host microsecond timestamp corresponding to the beat event (0L if current reference time should be used).
     * @param quantum The musical bar length / quantum (default: 4.0 beats).
     */
    fun onBeatAligned(beatTime: Double, microsecondTimestamp: Long = 0L, quantum: Double = 4.0)
}
