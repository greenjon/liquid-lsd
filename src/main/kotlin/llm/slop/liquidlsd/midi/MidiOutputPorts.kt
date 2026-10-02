package llm.slop.liquidlsd.midi

import llm.slop.liquidlsd.control.CcQueue
import llm.slop.liquidlsd.control.MidiSink
import mu.KotlinLogging
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage

/**
 * Opens a MIDI output port by device name, for controller feedback. [MidiEngine] only ever opens
 * input ports; javax.sound.midi lists a controller's output as a separate device with the same name.
 * Each sink writes from its own daemon thread so a slow or stalled port never blocks the render thread.
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

    private class DeviceSink(
        private val name: String,
        private val device: MidiDevice,
        private val receiver: Receiver,
        minIntervalMs: Int
    ) : MidiSink {
        private val queue = CcQueue()
        private val minIntervalNs = minIntervalMs.coerceAtLeast(0) * 1_000_000L
        @Volatile private var failed = false
        @Volatile private var closed = false

        private val writer = Thread({
            var lastSendNs = 0L
            var windowStartMs = System.currentTimeMillis()
            var sent = 0
            var totalSendNs = 0L
            var maxSendNs = 0L
            var maxBacklog = 0
            while (!closed) {
                val change = try { queue.take(250) } catch (e: InterruptedException) { break }
                if (change != null) {
                    // Pace: never closer than the minimum interval to the previous message.
                    val wait = minIntervalNs - (System.nanoTime() - lastSendNs)
                    if (wait > 0) {
                        try { Thread.sleep(wait / 1_000_000L, (wait % 1_000_000L).toInt()) } catch (e: InterruptedException) { break }
                    }
                    val backlog = queue.size
                    val start = System.nanoTime()
                    try {
                        receiver.send(ShortMessage(ShortMessage.CONTROL_CHANGE, change.channel, change.cc, change.value), -1)
                    } catch (e: Throwable) {
                        logger.warn { "MIDI output failed, dropping feedback port: ${e.message}" }
                        failed = true
                        break
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
        }, "midi-feedback-writer").apply { isDaemon = true; start() }

        override fun sendCc(channel: Int, cc: Int, value: Int) {
            if (failed || closed) return
            queue.offer(channel.coerceIn(0, 15), cc.coerceIn(0, 127), value.coerceIn(0, 127))
        }

        override val isHealthy: Boolean get() = !failed && !closed && device.isOpen

        override fun close() {
            closed = true
            writer.interrupt()
            try { receiver.close() } catch (_: Throwable) {}
            try { device.close() } catch (_: Throwable) {}
        }
    }

    private const val STATS_WINDOW_MS = 5000L
}
