package llm.slop.liquidlsd.link

import mu.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ableton Link backend communicating over TCP socket with Carabiner Link daemon (`127.0.0.1:17000`).
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
        try {
            val sock = Socket(host, port)
            sock.soTimeout = 3000
            socket = sock
            writer = PrintWriter(sock.getOutputStream(), true)
            reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            running.set(true)

            executor.submit { listenLoop() }

            // Initial setup commands
            sendCommand("status")
            sendCommand("bpm $initialBpm")
            isLinkEnabled = true
            logger.info { "CarabinerTcpLinkBackend: Connected to $host:$port" }
            return true
        } catch (e: Exception) {
            logger.debug { "CarabinerTcpLinkBackend: Connection to $host:$port failed: ${e.message}" }
            close()
            return false
        }
    }

    private fun sendCommand(cmd: String) {
        try {
            writer?.println(cmd)
            writer?.flush()
        } catch (e: Exception) {
            logger.warn(e) { "Error sending Carabiner command: $cmd" }
        }
    }

    private fun listenLoop() {
        while (running.get()) {
            try {
                val line = reader?.readLine() ?: break
                parseLine(line)
            } catch (e: Exception) {
                if (running.get()) {
                    logger.debug { "Carabiner socket read finished or disconnected: ${e.message}" }
                }
                break
            }
        }
        close()
    }

    private fun parseLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return

        // Carabiner protocol line examples:
        // "status { bpm: 120.0, beat: 12.5, peers: 1, start-stop: 1, playing: 1 }"
        // "bpm 128.0"
        // "peers 2"
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

    override fun close() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        writer = null
        reader = null
        isLinkEnabled = false
        currentPeers = 0
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
        sendCommand("bpm %.2f".format(currentBpm))
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

    override fun requestBeatAtTime(beat: Double, quantum: Double) {
        anchorBeat = beat
        anchorTimeUs = System.nanoTime() / 1000
        sendCommand("beat %.2f".format(beat))
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
