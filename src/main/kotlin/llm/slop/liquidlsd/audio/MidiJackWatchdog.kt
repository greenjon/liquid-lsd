package llm.slop.liquidlsd.audio

import llm.slop.liquidlsd.midi.MidiEngine
import llm.slop.liquidlsd.ui.UITheme
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

    private var consecutiveReconnectAttempts = 0
    private const val MAX_AUTOMATIC_RECONNECT_ATTEMPTS = 3

    @Synchronized
    fun start() {
        if (running) return
        running = true
        consecutiveReconnectAttempts = 0
        thread = Thread {
            logger.info { "Starting MidiJackWatchdog background daemon..." }
            while (running) {
                try {
                    // 1. Scan/Manage MIDI hardware
                    if (isMidiScanActive && UITheme.midiEnabled) {
                        MidiEngine.scanForNewDevices()
                    }

                    // 2. Re-establish connection to JACK server
                    if (isJackReconnectActive && UITheme.audioEngineEnabled) {
                        if (AudioEngine.isActive()) {
                            consecutiveReconnectAttempts = 0
                        } else if (!AudioEngine.presetIOInFlight.get()) {
                            if (consecutiveReconnectAttempts < MAX_AUTOMATIC_RECONNECT_ATTEMPTS) {
                                consecutiveReconnectAttempts++
                                logger.info { "Watchdog attempting audio reconnection (attempt $consecutiveReconnectAttempts of $MAX_AUTOMATIC_RECONNECT_ATTEMPTS)..." }
                                AudioEngine.tryReconnect()
                            } else if (consecutiveReconnectAttempts == MAX_AUTOMATIC_RECONNECT_ATTEMPTS) {
                                consecutiveReconnectAttempts++
                                logger.warn { "Watchdog paused automatic audio reconnect after $MAX_AUTOMATIC_RECONNECT_ATTEMPTS attempts to prevent link thrashing. Use manual retry in settings." }
                            }
                        } else {
                            logger.warn { "Watchdog skipping JACK reconnect because Preset I/O is in flight." }
                        }
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
