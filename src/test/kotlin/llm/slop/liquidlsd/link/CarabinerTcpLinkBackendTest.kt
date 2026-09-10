package llm.slop.liquidlsd.link

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

class CarabinerTcpLinkBackendTest {

    @Test
    fun testOfflineNonBlockingOperations() {
        // Unreachable port -> backend should not block or throw unhandled exceptions
        val backend = CarabinerTcpLinkBackend(host = "127.0.0.1", port = 65530)
        assertTrue(backend.init(120.0))
        assertFalse(backend.isConnected())

        // Ensure commands do not block caller
        backend.setTempo(128.0)
        backend.requestBeatAtTime(16.0, 1000000L, 4.0)
        backend.setEnabled(true)
        backend.setStartStopSyncEnabled(true)
        backend.setIsPlaying(true)

        assertEquals(128.0, backend.getTempo())
        assertEquals(0, backend.getNumPeers())

        backend.close()
        assertFalse(backend.isEnabled())
    }

    @Test
    fun testMockServerCommunicationAndReconnection() {
        val receivedCommands = ConcurrentLinkedQueue<String>()
        val connectionLatch = CountDownLatch(1)

        // 1. Start local mock Carabiner server
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        var clientSocket: Socket? = null
        val serverThread = thread(name = "MockCarabinerServer") {
            try {
                val sock = serverSocket.accept()
                clientSocket = sock
                connectionLatch.countDown()
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))
                val serverWriter = PrintWriter(sock.getOutputStream(), true)

                // Send mock Carabiner status response
                serverWriter.println("status { bpm: 128.5, beat: 16.0, peers: 2, start-stop: 1, playing: 1 }")

                while (!serverSocket.isClosed && !sock.isClosed) {
                    val line = reader.readLine() ?: break
                    receivedCommands.add(line.trim())
                }
            } catch (_: Exception) {}
        }

        // 2. Initialize CarabinerTcpLinkBackend
        val backend = CarabinerTcpLinkBackend(host = "127.0.0.1", port = port)
        assertTrue(backend.init(120.0))

        // Wait for connection to mock server
        assertTrue(connectionLatch.await(3, TimeUnit.SECONDS), "Client failed to connect to mock Carabiner server")

        // Wait brief moment for setup commands and status line parsing
        var attempts = 0
        while ((!backend.isConnected() || backend.getNumPeers() == 0) && attempts++ < 30) {
            Thread.sleep(50)
        }
        assertTrue(backend.isConnected())

        // Verify status line parsing
        assertEquals(128.5, backend.getTempo(), 0.01)
        assertEquals(2, backend.getNumPeers())
        assertTrue(backend.isStartStopSyncEnabled())
        assertTrue(backend.isPlaying())

        // Send commands from backend and verify mock server receives formatted strings
        backend.setTempo(132.0)
        backend.requestBeatAtTime(32.0, 5000000L, 4.0)

        // Wait for server to receive additional commands (total 5: status, bpm 120, enable, bpm 132, beat 32...)
        attempts = 0
        while (receivedCommands.size < 5 && attempts++ < 30) {
            Thread.sleep(50)
        }

        val commandsList = receivedCommands.toList()
        assertTrue(commandsList.any { it.startsWith("bpm 132.00") }, "Expected 'bpm 132.00' in $commandsList")
        assertTrue(commandsList.any { it.startsWith("beat 32.00 5000000 4.00") }, "Expected 'beat 32.00 5000000 4.00' in $commandsList")

        // 3. Test Disconnection
        clientSocket?.close()
        serverSocket.close()
        serverThread.join(1000)

        attempts = 0
        while (backend.isConnected() && attempts++ < 20) {
            Thread.sleep(50)
        }
        assertFalse(backend.isConnected())
        assertEquals(0, backend.getNumPeers())

        backend.close()
    }
}
