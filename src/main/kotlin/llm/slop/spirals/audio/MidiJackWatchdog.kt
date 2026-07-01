package llm.slop.spirals.audio

import llm.slop.spirals.midi.MidiEngine
import llm.slop.spirals.ui.UITheme
import mu.KotlinLogging

/**
 * A centralized, lightweight background watchdog daemon thread that periodically (every 4 seconds):
 * 1. Checks and cleans up disconnected MIDI devices, and auto-detects new MIDI controllers.
 * 2. Attempts to reconnect to JACK/PipeWire if the Audio Engine was enabled but connection failed/lost.
 */
object MidiJackWatchdog {
    private val logger = KotlinLogging.logger {}
    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    var isMidiScanActive = true

    @Volatile
    var isJackReconnectActive = true

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread {
            logger.info { "Starting MidiJackWatchdog background daemon..." }
            while (running) {
                try {
                    // 1. Scan/Manage MIDI hardware
                    if (isMidiScanActive) {
                        MidiEngine.scanForNewDevices()
                    }

                    // 2. Re-establish connection to JACK server
                    if (isJackReconnectActive && UITheme.audioEngineEnabled && !AudioEngine.isActive()) {
                        AudioEngine.tryReconnect()
                    }

                    Thread.sleep(4000)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    logger.error(e) { "Error in MidiJackWatchdog loop cycle" }
                }
            }
            logger.info { "MidiJackWatchdog background daemon stopped." }
        }.apply {
            isDaemon = true
            name = "MidiJackWatchdog-Daemon"
            start()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }
}
