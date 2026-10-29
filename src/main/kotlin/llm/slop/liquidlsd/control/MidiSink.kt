package llm.slop.liquidlsd.control

/** Where a controller's feedback messages go. [sendCc] must not block the caller. */
interface MidiSink {
    /** Sends a control change on [channel] (0..15). */
    fun sendCc(channel: Int, cc: Int, value: Int)

    /** Sends one complete SysEx message ([bytes] from F0 to F7), in order and never merged. Default: dropped. */
    fun sendSysex(bytes: ByteArray) {}

    /** False once the device has gone away; the owner should drop the sink and reopen later. */
    val isHealthy: Boolean get() = true

    /**
     * Lets go of the port without blocking: anything already queued (e.g. leaving native mode) is still
     * sent, then the port closes in the background. Sends after this are dropped.
     */
    fun close() {}

    /** Waits up to [timeoutMs] for a [close]d sink to finish sending and release its port; false on timeout. For app exit. */
    fun awaitClosed(timeoutMs: Long): Boolean = true
}
