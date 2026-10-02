package llm.slop.liquidlsd.control

/** Where a controller's feedback messages go. [sendCc] must not block the caller. */
interface MidiSink {
    /** Sends a control change on [channel] (0..15). */
    fun sendCc(channel: Int, cc: Int, value: Int)

    /** False once the device has gone away; the owner should drop the sink and reopen later. */
    val isHealthy: Boolean get() = true

    fun close() {}
}
