package llm.slop.liquidlsd.midi

import llm.slop.liquidlsd.control.CcQueue
import llm.slop.liquidlsd.control.MidiSink
import mu.KotlinLogging
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage
import javax.sound.midi.SysexMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opens a MIDI output port by device name, for controller feedback. [MidiEngine] only ever opens
 * input ports; javax.sound.midi lists a controller's output as a separate device with the same name.
 * Each sink writes, and finally closes its port, from its own daemon thread so a slow or stalled port
 * never blocks the render thread.
 */
object MidiOutputPorts {
    private val logger = KotlinLogging.logger {}

    /**
     * A sink for the output port named like [deviceName], or null if there isn't one or it can't be
     * opened. Messages are sent at least [minIntervalMs] apart: the Twister drops messages that
     * arrive in a burst.
     */
    fun openFor(deviceName: String, minIntervalMs: Int = 2): MidiSink? {
        val infos = try { MidiSystem.getMidiDeviceInfo() } catch (e: Throwable) { return null }
        for (info in infos) {
            if (info.name != deviceName) continue
            try {
                val device = MidiSystem.getMidiDevice(info)
                if (device.maxReceivers == 0) continue
                if (!device.isOpen) device.open()
                logger.info { "Opened MIDI output port for feedback: ${info.name} - ${info.description} (min interval $minIntervalMs ms)" }
                return DeviceSink(info.name, device, device.receiver, minIntervalMs)
            } catch (e: Throwable) {
                logger.debug { "Could not open MIDI output ${info.name}: ${e.message}" }
            }
        }
        return null
    }

    /**
     * Writes queued messages to [receiver] from its own `midi-feedback-writer` daemon thread. [close] returns
     * at once: the writer sends the SysEx still queued (up to [SYSEX_FLUSH_MS]), then closes the port itself,
     * so unplugging a device never stalls the render thread. [isDeviceOpen]/[closeDevice] stand in for the
     * [MidiDevice] so tests can drive it with a fake receiver.
     */
    internal class DeviceSink(
        private val name: String,
        private val receiver: Receiver,
        minIntervalMs: Int,
        private val isDeviceOpen: () -> Boolean = { true },
        private val closeDevice: () -> Unit = {}
    ) : MidiSink {
        constructor(name: String, device: MidiDevice, receiver: Receiver, minIntervalMs: Int) :
            this(name, receiver, minIntervalMs, { device.isOpen }, { device.close() })

        private val queue = CcQueue()
        private val minIntervalNs = minIntervalMs.coerceAtLeast(0) * 1_000_000L
        @Volatile private var failed = false
        /** Set by [close]: no new messages; the writer flushes SysEx and releases the port. */
        @Volatile private var closing = false
        @Volatile private var closeDeadlineMs = 0L
        /** Set when the writer loop has ended, so [close] knows whether anyone is left to release the port. */
        @Volatile private var writerExited = false
        private val released = AtomicBoolean(false)
        private val releasedLatch = CountDownLatch(1)

        private val writer = Thread({
            try { writeLoop() } finally {
                writerExited = true
                if (closing) release(flushed = !failed)
            }
        }, "midi-feedback-writer").apply { isDaemon = true; start() }

        private fun pace(lastSendNs: Long) {
            val wait = minIntervalNs - (System.nanoTime() - lastSendNs)
            if (wait > 0) Thread.sleep(wait / 1_000_000L, (wait % 1_000_000L).toInt())
        }

        private fun writeLoop() {
            var lastSendNs = 0L
            var windowStartMs = System.currentTimeMillis()
            var sent = 0
            var totalSendNs = 0L
            var maxSendNs = 0L
            var maxBacklog = 0
            while (true) {
                if (closing && (queue.sysexPending == 0 || System.currentTimeMillis() >= closeDeadlineMs)) return
                try { queue.awaitWork(if (closing) 5 else 250) } catch (e: InterruptedException) { return }
                while (true) {
                    if (closing && System.currentTimeMillis() >= closeDeadlineMs) break
                    val message = queue.pollSysex() ?: break
                    try { pace(lastSendNs) } catch (e: InterruptedException) { return }
                    try {
                        receiver.send(SysexMessage(message, message.size), -1)
                    } catch (e: Throwable) {
                        logger.warn { "MIDI output failed, dropping feedback port: ${e.message}" }
                        failed = true
                        return
                    }
                    lastSendNs = System.nanoTime()
                }
                if (closing) continue   // only the SysEx (e.g. leaving native mode) is worth sending now
                val change = queue.poll()
                if (change != null) {
                    // Pace: never closer than the minimum interval to the previous message.
                    try { pace(lastSendNs) } catch (e: InterruptedException) { return }
                    val backlog = queue.size
                    val start = System.nanoTime()
                    try {
                        receiver.send(ShortMessage(ShortMessage.CONTROL_CHANGE, change.channel, change.cc, change.value), -1)
                    } catch (e: Throwable) {
                        logger.warn { "MIDI output failed, dropping feedback port: ${e.message}" }
                        failed = true
                        return
                    }
                    lastSendNs = System.nanoTime()
                    val took = lastSendNs - start
                    sent++; totalSendNs += took
                    if (took > maxSendNs) maxSendNs = took
                    if (backlog > maxBacklog) maxBacklog = backlog
                }
                val now = System.currentTimeMillis()
                if (now - windowStartMs >= STATS_WINDOW_MS) {
                    if (sent > 0) {
                        val avgMs = totalSendNs / sent / 1e6
                        val maxMs = maxSendNs / 1e6
                        val line = "MIDI feedback to $name: sent=$sent avgSend=${"%.2f".format(avgMs)}ms maxSend=${"%.2f".format(maxMs)}ms maxBacklog=$maxBacklog"
                        if (maxMs > 5.0 || maxBacklog > 40) logger.info { line } else logger.debug { line }
                    }
                    windowStartMs = now; sent = 0; totalSendNs = 0; maxSendNs = 0; maxBacklog = 0
                }
            }
        }

        /** Closes the port once; [flushed] gives the last SysEx a moment to land before the port goes away. */
        private fun release(flushed: Boolean) {
            if (!released.compareAndSet(false, true)) return
            if (flushed) try { Thread.sleep(20) } catch (_: InterruptedException) {}
            try { receiver.close() } catch (_: Throwable) {}
            try { closeDevice() } catch (_: Throwable) {}
            releasedLatch.countDown()
        }

        override fun sendCc(channel: Int, cc: Int, value: Int) {
            if (failed || closing) return
            queue.offer(channel.coerceIn(0, 15), cc.coerceIn(0, 127), value.coerceIn(0, 127))
        }

        override fun sendSysex(bytes: ByteArray) {
            if (failed || closing) return
            queue.offerSysex(bytes.copyOf())
        }

        override val isHealthy: Boolean get() = !failed && !closing && isDeviceOpen()

        override fun close() {
            if (closing) return
            closeDeadlineMs = System.currentTimeMillis() + SYSEX_FLUSH_MS
            closing = true
            // Either the writer sees [closing] on its way out, or we see it already gone (e.g. after a failed send).
            if (writerExited) release(flushed = false) else queue.wake()
        }

        override fun awaitClosed(timeoutMs: Long): Boolean {
            val done = try { releasedLatch.await(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { false }
            if (!done) {   // a stalled send: close the port from here so exit isn't held up
                writer.interrupt()
                release(flushed = false)
            }
            return done
        }
    }

    private const val STATS_WINDOW_MS = 5000L
    private const val SYSEX_FLUSH_MS = 300L
}
