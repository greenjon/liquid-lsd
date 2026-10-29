package llm.slop.liquidlsd.midi

import java.util.Collections
import javax.sound.midi.MidiMessage
import javax.sound.midi.Receiver
import javax.sound.midi.SysexMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MidiOutputPortsTest {
    /** A receiver that takes [sendMs] per message, like a slow device. */
    private class SlowReceiver(private val sendMs: Long) : Receiver {
        val log: MutableList<String> = Collections.synchronizedList(ArrayList<String>())
        override fun send(message: MidiMessage, timeStamp: Long) {
            Thread.sleep(sendMs)
            log += if (message is SysexMessage) "sysex${message.message[1]}" else "cc"
        }
        override fun close() { log += "close" }
    }

    private fun sysex(id: Int) = byteArrayOf(0xF0.toByte(), id.toByte(), 0xF7.toByte())

    @Test
    fun closeReturnsAtOnceAndTheWriterFlushesSysexBeforeClosingThePort() {
        val receiver = SlowReceiver(sendMs = 10)
        var deviceClosed = false
        val sink = MidiOutputPorts.DeviceSink("test", receiver, 2, closeDevice = { deviceClosed = true })
        repeat(5) { sink.sendSysex(sysex(it)) }
        val start = System.nanoTime()
        sink.close()
        val tookMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(tookMs < 20, "close must not block the render thread (took $tookMs ms)")
        assertFalse(sink.isHealthy)
        sink.sendSysex(sysex(9))   // dropped: nothing may follow the leave
        sink.sendCc(0, 1, 2)

        assertTrue(sink.awaitClosed(2000))
        assertEquals(listOf("sysex0", "sysex1", "sysex2", "sysex3", "sysex4", "close"), receiver.log.toList())
        assertTrue(deviceClosed)
    }

    @Test
    fun aSinkWhoseWriterAlreadyStoppedStillReleasesThePortOnClose() {
        val receiver = object : Receiver {
            @Volatile var closed = false
            override fun send(message: MidiMessage, timeStamp: Long) = throw IllegalStateException("unplugged")
            override fun close() { closed = true }
        }
        val sink = MidiOutputPorts.DeviceSink("test", receiver, 0)
        sink.sendCc(0, 1, 2)
        val deadline = System.currentTimeMillis() + 2000
        while (sink.isHealthy && System.currentTimeMillis() < deadline) Thread.sleep(2)
        assertFalse(sink.isHealthy)
        Thread.sleep(20)   // let the writer exit
        sink.close()
        assertTrue(sink.awaitClosed(1000))
        assertTrue(receiver.closed)
    }
}
