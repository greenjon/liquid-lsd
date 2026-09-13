package llm.slop.liquidlsd.audio

import org.jaudiolibs.jnajack.*
import java.nio.FloatBuffer
import java.util.EnumSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

enum class JackStartFailure {
    NATIVE_LIBRARY_MISSING,
    CONNECTION_FAILED
}

/**
 * Handles initialization, callback registration, and port connections
 * for the JACK Audio Connection Kit.
 */
class JackClient(
    val clientName: String = "lsd",
    val onProcess: (FloatBuffer, FloatBuffer?, Int, Float) -> Unit // (leftBuffer, rightBuffer, nframes, sampleRate)
) {
    @Volatile
    private var client: org.jaudiolibs.jnajack.JackClient? = null
    @Volatile
    private var inputPortL: JackPort? = null
    @Volatile
    private var inputPortR: JackPort? = null
    @Volatile
    var lastStartFailure: JackStartFailure? = null
        private set
    @Volatile
    var lastStartFailureMessage: String? = null
        private set

    private val callbackErrorCount = AtomicInteger(0)
    private val lastCallbackError = AtomicReference<Throwable?>(null)
    private val errorLoggerExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "JackClient-ErrorLogger").apply { isDaemon = true }
    }
    private var lastLoggedErrorCount = 0

    init {
        errorLoggerExecutor.scheduleWithFixedDelay({
            try {
                val count = callbackErrorCount.get()
                if (count > lastLoggedErrorCount) {
                    val error = lastCallbackError.getAndSet(null)
                    if (error != null) {
                        logger.error(error) { "Error in JACK process callback (Total occurrences: $count)" }
                    } else {
                        logger.error { "Error in JACK process callback occurred. Total occurrences: $count" }
                    }
                    lastLoggedErrorCount = count
                }
            } catch (e: Exception) {
                // Ignore background logging exception
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    val isConnected: Boolean
        get() = client != null

    /**
     * Initializes and activates the JACK client.
     */
    fun start(): Boolean {
        try {
            lastStartFailure = null
            lastStartFailureMessage = null
            logger.info { "Starting JACK Audio client..." }
            val jack = Jack.getInstance()
            
            // Open JACK client. If no server running, do not auto-start (noStartServer flag)
            client = jack.openClient(
                clientName,
                EnumSet.of(JackOptions.JackNoStartServer),
                EnumSet.noneOf(JackStatus::class.java)
            )

            val sampleRate = client!!.sampleRate.toFloat()
            logger.info { "JACK Client opened. Sample Rate: $sampleRate" }

            // Register stereo input ports
            inputPortL = client!!.registerPort(
                "input_1",
                JackPortType.AUDIO,
                EnumSet.of(JackPortFlags.JackPortIsInput)
            )
            inputPortR = client!!.registerPort(
                "input_2",
                JackPortType.AUDIO,
                EnumSet.of(JackPortFlags.JackPortIsInput)
            )

            // Register the process callback
            client!!.setProcessCallback { _, nframes ->
                try {
                    val bufL = inputPortL?.floatBuffer
                    val bufR = inputPortR?.floatBuffer
                    if (bufL != null) {
                        onProcess(bufL, bufR, nframes, sampleRate)
                    }
                } catch (e: Throwable) {
                    lastCallbackError.set(e)
                    callbackErrorCount.incrementAndGet()
                }
                true
            }

            // Activate audio thread
            client!!.activate()
            logger.info { "JACK Client activated." }

            // Auto-connect to physical system inputs
            autoConnectInput()
            return true
        } catch (e: Throwable) {
            lastStartFailure = classifyStartFailure(e)
            lastStartFailureMessage = e.message
            val summary = if (lastStartFailure == JackStartFailure.NATIVE_LIBRARY_MISSING) {
                "JACK native library is not available"
            } else {
                "Could not connect to JACK server"
            }
            logger.warn { "$summary: ${e.message}. Running in silent / fallback mode." }
            client = null
            inputPortL = null
            inputPortR = null
            return false
        }
    }

    private fun classifyStartFailure(error: Throwable): JackStartFailure {
        val message = error.message.orEmpty()
        return if (error is UnsatisfiedLinkError || message.contains("native library", ignoreCase = true)) {
            JackStartFailure.NATIVE_LIBRARY_MISSING
        } else {
            JackStartFailure.CONNECTION_FAILED
        }
    }

    /**
     * Auto-connects our input ports to physical system capture ports.
     * Prioritizes ports matching "capture" to prevent connecting to playback monitor ports.
     */
    private fun autoConnectInput() {
        val c = client ?: return
        val portL = inputPortL ?: return
        val portR = inputPortR ?: return
        try {
            val jack = Jack.getInstance()
            val rawSystemPorts = jack.getPorts(
                c,
                null,
                JackPortType.AUDIO,
                EnumSet.of(JackPortFlags.JackPortIsPhysical, JackPortFlags.JackPortIsOutput)
            )
            if (rawSystemPorts != null && rawSystemPorts.isNotEmpty()) {
                val capturePorts = rawSystemPorts.filter { it.contains("capture", ignoreCase = true) }
                val targetPorts: List<String> = if (capturePorts.isNotEmpty()) capturePorts else rawSystemPorts.toList()

                try {
                    jack.connect(c, targetPorts[0], portL.name)
                    logger.info { "Auto-connected JACK left input to system port: ${targetPorts[0]}" }
                } catch (e: Exception) {
                    logger.debug { "Left port already connected or connection skipped: ${e.message}" }
                }

                try {
                    if (targetPorts.size > 1) {
                        jack.connect(c, targetPorts[1], portR.name)
                        logger.info { "Auto-connected JACK right input to system port: ${targetPorts[1]}" }
                    } else {
                        jack.connect(c, targetPorts[0], portR.name)
                        logger.info { "Auto-connected single JACK capture port to both channels: ${targetPorts[0]}" }
                    }
                } catch (e: Exception) {
                    logger.debug { "Right port already connected or connection skipped: ${e.message}" }
                }
            } else {
                logger.warn { "No physical capture ports found to connect." }
            }
        } catch (e: Exception) {
            logger.warn { "Failed to auto-connect physical ports: ${e.message}" }
        }
    }

    /**
     * Stops the JACK client session with clean port disconnection and deactivation.
     */
    fun stop() {
        try {
            errorLoggerExecutor.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
        val c = client
        val pL = inputPortL
        val pR = inputPortR
        if (c != null) {
            try {
                logger.info { "Stopping JACK client..." }
                val jack = Jack.getInstance()
                // Cleanly disconnect input ports to let WirePlumber / JACK release link graph entries
                try {
                    pL?.connections?.forEach { conn ->
                        jack.disconnect(c, conn, pL.name)
                    }
                    pR?.connections?.forEach { conn ->
                        jack.disconnect(c, conn, pR.name)
                    }
                } catch (e: Exception) {
                    logger.debug { "Ignored error during port disconnection: ${e.message}" }
                }

                c.deactivate()
                c.close()
            } catch (e: Exception) {
                logger.warn(e) { "Error closing JACK client" }
            }
        }
        client = null
        inputPortL = null
        inputPortR = null
    }
}
