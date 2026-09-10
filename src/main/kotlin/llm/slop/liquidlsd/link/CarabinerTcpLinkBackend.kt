package llm.slop.liquidlsd.link

import mu.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ableton Link backend communicating over TCP socket with Carabiner Link daemon (`127.0.0.1:17000`).
 * Uses an asynchronous non-blocking command pipeline and automatic background reconnection logic.
 */
class CarabinerTcpLinkBackend(
    val host: String = "127.0.0.1",
    val port: Int = 17000
) : LinkBackend {
    private val logger = KotlinLogging.logger {}
    override val name: String = "Carabiner TCP ($host:$port)"

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private val running = AtomicBoolean(false)
    @Volatile private var connected = false
    private val commandQueue = ConcurrentLinkedQueue<String>()
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "CarabinerTcpClient").apply { isDaemon = true } }

    @Volatile private var isLinkEnabled = false
    @Volatile private var currentPeers = 0
    @Volatile private var currentBpm = 120.0
    @Volatile private var startStopSync = false
    @Volatile private var isTransportPlaying = false

    @Volatile private var anchorBeat = 0.0
    @Volatile private var anchorTimeUs = System.nanoTime() / 1000

    override fun init(initialBpm: Double): Boolean {
        currentBpm = initialBpm
        anchorTimeUs = System.nanoTime() / 1000
        isLinkEnabled = true
        running.set(true)

        executor.submit { connectionWorkerLoop() }
        return true
    }

    override fun isConnected(): Boolean = connected && running.get()

    /**
     * Enqueues an outbound Carabiner TCP command on a lock-free queue.
     * Guaranteed non-blocking for caller threads (audio callback / UI rendering thread).
     */
    private fun sendCommand(cmd: String) {
        if (!running.get()) return
        commandQueue.offer(cmd)
    }

    private fun connectionWorkerLoop() {
        while (running.get()) {
            if (!connected) {
                try {
                    val sock = Socket()
                    sock.connect(InetSocketAddress(host, port), 2000)
                    sock.tcpNoDelay = true
                    sock.soTimeout = 100
                    writer = PrintWriter(OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8), true)
                    reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))
                    socket = sock
                    connected = true
                    logger.info { "CarabinerTcpLinkBackend: Connected to $host:$port" }

                    // Initial setup commands
                    sendCommand("status")
                    sendCommand(String.format(Locale.US, "bpm %.2f", currentBpm))
                    if (isLinkEnabled) {
                        sendCommand("enable")
                    }
                } catch (e: Exception) {
                    closeSocket()
                    if (!running.get()) break
                    try { Thread.sleep(3000) } catch (_: InterruptedException) {}
                    continue
                }
            }

            try {
                // 1. Flush pending outbound commands
                while (running.get() && connected) {
                    val cmd = commandQueue.poll() ?: break
                    val w = writer ?: break
                    w.println(cmd)
                    if (w.checkError()) {
                        throw java.io.IOException("PrintWriter error during send")
                    }
                }

                // 2. Read incoming network line (blocks up to soTimeout = 100ms)
                val r = reader ?: break
                val line = r.readLine() ?: throw java.io.IOException("Socket EOF")
                parseLine(line)
            } catch (_: java.net.SocketTimeoutException) {
                // Expected timeout when no incoming messages; loop back to flush outbound queue
            } catch (e: Exception) {
                if (running.get()) {
                    logger.debug { "Carabiner socket disconnected: ${e.message}" }
                }
                closeSocket()
                try { Thread.sleep(2000) } catch (_: InterruptedException) {}
            }
        }
        closeSocket()
    }

    private fun parseLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return

        try {
            when {
                trimmed.startsWith("status") -> {
                    val bpmMatch = Regex("bpm:\\s*([0-9.]+)").find(trimmed)
                    bpmMatch?.groupValues?.get(1)?.toDoubleOrNull()?.let { currentBpm = it }

                    val beatMatch = Regex("beat:\\s*([0-9.]+)").find(trimmed)
                    beatMatch?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                        anchorBeat = it
                        anchorTimeUs = System.nanoTime() / 1000
                    }

                    val peerMatch = Regex("peers:\\s*([0-9]+)").find(trimmed)
                    peerMatch?.groupValues?.get(1)?.toIntOrNull()?.let { currentPeers = it }

                    val ssMatch = Regex("start-stop:\\s*([0-1])").find(trimmed)
                    ssMatch?.groupValues?.get(1)?.let { startStopSync = (it == "1") }

                    val playMatch = Regex("playing:\\s*([0-1])").find(trimmed)
                    playMatch?.groupValues?.get(1)?.let { isTransportPlaying = (it == "1") }
                }
                trimmed.startsWith("bpm ") -> {
                    trimmed.substring(4).trim().toDoubleOrNull()?.let { currentBpm = it }
                }
                trimmed.startsWith("peers ") -> {
                    trimmed.substring(6).trim().toIntOrNull()?.let { currentPeers = it }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error parsing Carabiner output line: $line" }
        }
    }

    private fun closeSocket() {
        connected = false
        currentPeers = 0
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        writer = null
        reader = null
    }

    override fun close() {
        running.set(false)
        closeSocket()
        commandQueue.clear()
        isLinkEnabled = false
    }

    override fun setEnabled(enabled: Boolean) {
        isLinkEnabled = enabled
        if (enabled) {
            sendCommand("enable")
        } else {
            sendCommand("disable")
        }
    }

    override fun isEnabled(): Boolean = isLinkEnabled && running.get()

    override fun getNumPeers(): Int = currentPeers

    override fun getTempo(): Double = currentBpm

    override fun setTempo(bpm: Double) {
        currentBpm = bpm.coerceIn(20.0, 300.0)
        sendCommand(String.format(Locale.US, "bpm %.2f", currentBpm))
    }

    override fun getBeatAtTime(timeUs: Long, quantum: Double): Double {
        val elapsedSec = (timeUs - anchorTimeUs) / 1_000_000.0
        val beatDelta = elapsedSec * (currentBpm / 60.0)
        return (anchorBeat + beatDelta).coerceAtLeast(0.0)
    }

    override fun getPhaseAtTime(timeUs: Long, quantum: Double): Double {
        val beat = getBeatAtTime(timeUs, quantum)
        val q = quantum.coerceAtLeast(1.0)
        return beat % q
    }

    override fun requestBeatAtTime(beat: Double, timeUs: Long, quantum: Double) {
        anchorBeat = beat
        anchorTimeUs = if (timeUs > 0) timeUs else (System.nanoTime() / 1000)
        if (timeUs > 0) {
            sendCommand(String.format(Locale.US, "beat %.2f %d %.2f", beat, timeUs, quantum))
        } else {
            sendCommand(String.format(Locale.US, "beat %.2f", beat))
        }
    }

    override fun setStartStopSyncEnabled(enabled: Boolean) {
        startStopSync = enabled
        sendCommand("start-stop " + if (enabled) "1" else "0")
    }

    override fun isStartStopSyncEnabled(): Boolean = startStopSync

    override fun isPlaying(): Boolean = isTransportPlaying

    override fun setIsPlaying(isPlaying: Boolean) {
        isTransportPlaying = isPlaying
        sendCommand("play " + if (isPlaying) "1" else "0")
    }
}
