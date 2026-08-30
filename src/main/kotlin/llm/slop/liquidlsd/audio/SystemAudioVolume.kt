package llm.slop.liquidlsd.audio

import mu.KotlinLogging
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Helper to query and set the system input level (default audio source) via native utilities.
 * Supports Linux (wpctl) and macOS (osascript). Gracefully disables on Windows or other OSes.
 */
object SystemAudioVolume {
    private val logger = KotlinLogging.logger {}
    private const val PROCESS_TIMEOUT_SECONDS = 2L
    
    val isSupported: Boolean
    private val os: OS

    private enum class OS { LINUX, MAC, WINDOWS, UNKNOWN }

    private val volumeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SystemAudioVolume-Worker").apply { isDaemon = true }
    }

    init {
        val osName = System.getProperty("os.name").lowercase(Locale.ENGLISH)
        os = when {
            osName.contains("mac") -> OS.MAC
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> OS.LINUX
            osName.contains("win") -> OS.WINDOWS
            else -> OS.UNKNOWN
        }
        isSupported = (os == OS.LINUX || os == OS.MAC)
    }

    @Volatile
    var systemInputVolume: Float = 1.0f
        private set

    @Volatile
    var isMuted: Boolean = false
        private set

    private var lastQueryTime = 0L
    private var queryIntervalMs = 2000L
    private var isQuerying = false
    private var consecutiveErrors = 0

    private fun waitForProcess(process: Process): Boolean {
        if (process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return true
        }

        process.destroy()
        if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
        }
        return false
    }

    fun updateSystemVolume(volume: Float) {
        if (!isSupported) return
        systemInputVolume = volume.coerceIn(0f, 1f)
        volumeExecutor.execute {
            var process: Process? = null
            try {
                val pb = when (os) {
                    OS.LINUX -> ProcessBuilder("wpctl", "set-volume", "@DEFAULT_AUDIO_SOURCE@", "%.2f".format(volume))
                    OS.MAC -> {
                        val pct = (volume * 100).toInt()
                        ProcessBuilder("osascript", "-e", "set volume input volume $pct")
                    }
                    else -> return@execute
                }
                pb.redirectErrorStream(true)
                process = pb.start()
                if (!waitForProcess(process)) {
                    logger.warn { "Timed out setting system volume" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to set system volume" }
            } finally {
                try { process?.inputStream?.close() } catch (_: Exception) {}
                try { process?.outputStream?.close() } catch (_: Exception) {}
                try { process?.errorStream?.close() } catch (_: Exception) {}
                try { process?.destroy() } catch (_: Exception) {}
            }
        }
    }

    fun queryAsync() {
        if (!isSupported) return
        val now = System.currentTimeMillis()
        if (now - lastQueryTime < queryIntervalMs || isQuerying) return
        isQuerying = true
        volumeExecutor.execute {
            var process: Process? = null
            try {
                val pb = when (os) {
                    OS.LINUX -> ProcessBuilder("wpctl", "get-volume", "@DEFAULT_AUDIO_SOURCE@")
                    OS.MAC -> ProcessBuilder("osascript", "-e", "input volume of (get volume settings)")
                    else -> return@execute
                }
                pb.redirectErrorStream(true)
                process = pb.start()
                if (!waitForProcess(process)) {
                    logger.warn { "Timed out querying system volume" }
                    consecutiveErrors++
                    return@execute
                }
                if (process.exitValue() != 0) {
                    consecutiveErrors++
                    return@execute
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                when (os) {
                    OS.LINUX -> {
                        if (output.startsWith("Volume:")) {
                            val parts = output.removePrefix("Volume:").trim().split(" ")
                            val vol = parts.getOrNull(0)?.toFloatOrNull()
                            if (vol != null) {
                                systemInputVolume = vol
                            }
                            isMuted = output.contains("[MUTED]")
                            consecutiveErrors = 0
                        } else {
                            consecutiveErrors++
                        }
                    }
                    OS.MAC -> {
                        val vol = output.toFloatOrNull()
                        if (vol != null) {
                            systemInputVolume = vol / 100f
                            isMuted = (vol == 0f)
                            consecutiveErrors = 0
                        } else {
                            consecutiveErrors++
                        }
                    }
                    OS.WINDOWS, OS.UNKNOWN -> {}
                }
            } catch (e: Exception) {
                consecutiveErrors++
                logger.debug { "Failed to query system volume: ${e.message}" }
            } finally {
                try { process?.inputStream?.close() } catch (_: Exception) {}
                try { process?.outputStream?.close() } catch (_: Exception) {}
                try { process?.errorStream?.close() } catch (_: Exception) {}
                try { process?.destroy() } catch (_: Exception) {}
                queryIntervalMs = if (consecutiveErrors >= 3) 30000L else 2000L
                lastQueryTime = System.currentTimeMillis()
                isQuerying = false
            }
        }
    }
}
