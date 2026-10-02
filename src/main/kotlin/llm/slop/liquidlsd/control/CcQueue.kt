package llm.slop.liquidlsd.control

/** One pending control change. */
data class Cc(val channel: Int, val cc: Int, val value: Int)

/**
 * Control changes waiting to be written to a device, keyed by (channel, cc): a newer value for a
 * key replaces the older one still waiting (keeping its place in line), so a slow device can never
 * build up a backlog of stale ring positions. Thread-safe; [take] blocks the writer thread.
 */
class CcQueue {
    private val pending = LinkedHashMap<Int, Int>()

    @Synchronized
    fun offer(channel: Int, cc: Int, value: Int) {
        pending[(channel shl 8) or cc] = value
        (this as Object).notifyAll()
    }

    /** The oldest pending change, or null if none. */
    @Synchronized
    fun poll(): Cc? {
        val iterator = pending.entries.iterator()
        if (!iterator.hasNext()) return null
        val entry = iterator.next()
        iterator.remove()
        return Cc(entry.key shr 8, entry.key and 0xFF, entry.value)
    }

    /** Waits up to [timeoutMs] for a change; null if none arrived. */
    @Synchronized
    fun take(timeoutMs: Long): Cc? {
        if (pending.isEmpty()) (this as Object).wait(timeoutMs)
        return poll()
    }

    @get:Synchronized
    val size: Int get() = pending.size
}
