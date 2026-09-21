package llm.slop.liquidlsd.osc

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OscEngineTest {

    @AfterTest
    fun tearDown() {
        OscEngine.stop()
    }

    private fun waitUntil(timeoutMs: Long = 2000L, message: String = "Condition not met", condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition(), "$message within ${timeoutMs}ms")
    }

    @Test
    fun testReceivesAndQueuesInboundMessageOnLoopback() {
        // Bind to an ephemeral port (0) to avoid colliding with a real running instance.
        OscEngine.start(inPort = 0, outPort = 9000)
        waitUntil(message = "Inbound port bound") { OscEngine.boundInboundPort() > 0 }
        val port = OscEngine.boundInboundPort()

        val payload = OscCodec.encode(OscMessage("/1/fader1", listOf(0.42f)))
        DatagramSocket().use { socket ->
            val packet = DatagramPacket(payload, payload.size, InetAddress.getLoopbackAddress(), port)
            socket.send(packet)
            waitUntil(message = "Inbound message received and queued") {
                if (OscEngine.inboundQueue.isNotEmpty()) {
                    true
                } else {
                    socket.send(packet)
                    false
                }
            }
        }

        val received = OscEngine.inboundQueue.poll()
        assertEquals("/1/fader1", received?.address)
        assertEquals(0.42f, received?.args?.get(0) as Float, absoluteTolerance = 1e-4f)
    }

    @Test
    fun testAutoLearnsRemoteClientAddressFromInboundSender() {
        OscEngine.start(inPort = 0, outPort = 9000)
        waitUntil(message = "Inbound port bound") { OscEngine.boundInboundPort() > 0 }
        val port = OscEngine.boundInboundPort()

        assertEquals("(none)", OscEngine.remoteAddressLabel())

        val payload = OscCodec.encode(OscMessage("/ping", listOf(1)))
        DatagramSocket().use { socket ->
            val packet = DatagramPacket(payload, payload.size, InetAddress.getLoopbackAddress(), port)
            socket.send(packet)
            waitUntil(message = "Remote client address learned") {
                if (OscEngine.remoteAddressLabel() != "(none)") {
                    true
                } else {
                    socket.send(packet)
                    false
                }
            }
        }

        assertTrue(OscEngine.remoteAddressLabel().contains("127.0.0.1") || OscEngine.remoteAddressLabel().contains("0:0:0:0:0:0:0:1"))
    }

    @Test
    fun testPacketSnifferRecordsInboundTraffic() {
        OscEngine.start(inPort = 0, outPort = 9000)
        waitUntil(message = "Inbound port bound") { OscEngine.boundInboundPort() > 0 }
        val port = OscEngine.boundInboundPort()

        val payload = OscCodec.encode(OscMessage("/sniff/test", listOf(1.0f)))
        DatagramSocket().use { socket ->
            val packet = DatagramPacket(payload, payload.size, InetAddress.getLoopbackAddress(), port)
            socket.send(packet)
            waitUntil(message = "Inbound sniffed packet recorded") {
                if (OscEngine.getRecentPackets().any { it.address == "/sniff/test" }) {
                    true
                } else {
                    socket.send(packet)
                    false
                }
            }
        }

        val packet = OscEngine.getRecentPackets().first { it.address == "/sniff/test" }
        assertEquals(OscPacketDirection.IN, packet.direction)
    }

    @Test
    fun testSendIsNoOpBeforeRemoteClientIsLearned() {
        OscEngine.start(inPort = 0, outPort = 9000)
        waitUntil(message = "Inbound port bound") { OscEngine.boundInboundPort() > 0 }
        // No inbound packet has been received yet, so no remote is learned; send() should be a safe no-op.
        OscEngine.sendFloat("/macro/knob/1", 0.5f)
        assertTrue(OscEngine.getRecentPackets().none { it.direction == OscPacketDirection.OUT })
    }

    @Test
    fun testStopClearsQueueAndSnifferState() {
        OscEngine.start(inPort = 0, outPort = 9000)
        waitUntil(message = "Inbound port bound") { OscEngine.boundInboundPort() > 0 }
        val port = OscEngine.boundInboundPort()

        val payload = OscCodec.encode(OscMessage("/x", listOf(1.0f)))
        DatagramSocket().use { socket ->
            val packet = DatagramPacket(payload, payload.size, InetAddress.getLoopbackAddress(), port)
            socket.send(packet)
            // Wait for both the queue and the sniffer to observe the packet: dispatchInbound()
            // writes them sequentially on the receiver thread, so checking only the queue can
            // race ahead of the sniffer recording by a few instructions.
            waitUntil(message = "Packet received and sniffed before stop") {
                if (OscEngine.inboundQueue.isNotEmpty() && OscEngine.getRecentPackets().isNotEmpty()) {
                    true
                } else {
                    socket.send(packet)
                    false
                }
            }
        }

        OscEngine.stop()
        assertTrue(OscEngine.inboundQueue.isEmpty())
        assertTrue(OscEngine.getRecentPackets().isEmpty())
        assertEquals("(none)", OscEngine.remoteAddressLabel())
        assertTrue(!OscEngine.isRunning)
    }
}
