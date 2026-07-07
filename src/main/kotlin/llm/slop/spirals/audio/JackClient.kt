package llm.slop.spirals.audio

import llm.slop.spirals.config.ProjectConfig
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
    val clientName: String = ProjectConfig.App.JACK_CLIENT_NAME,
    val onProcess: (FloatBuffer, Int, Float) -> Unit // (buffer, nframes, sampleRate)
) {
    @Volatile
    private var client: org.jaudiolibs.jnajack.JackClient? = null
    @Volatile
    private var inputPort: JackPort? = null
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

            // Register mono input port
            inputPort = client!!.registerPort(
                "input",
                JackPortType.AUDIO,
                EnumSet.of(JackPortFlags.JackPortIsInput)
            )

            // Register the process callback
            client!!.setProcessCallback { _, nframes ->
                try {
                    val buffer = inputPort?.floatBuffer
                    if (buffer != null) {
                        onProcess(buffer, nframes, sampleRate)
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
            inputPort = null
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
     * Auto-connects our input port to the first physical system capture port.
     */
    private fun autoConnectInput() {
        val c = client ?: return
        val port = inputPort ?: return
        try {
            val jack = Jack.getInstance()
            val systemPorts = jack.getPorts(
                c,
                null,
                JackPortType.AUDIO,
                EnumSet.of(JackPortFlags.JackPortIsPhysical, JackPortFlags.JackPortIsOutput)
            )
            if (systemPorts != null && systemPorts.isNotEmpty()) {
                jack.connect(c, systemPorts[0], port.name)
                logger.info { "Auto-connected JACK input to system port: ${systemPorts[0]}" }
            } else {
                logger.warn { "No physical capture ports found to connect." }
            }
        } catch (e: Exception) {
            logger.warn { "Failed to auto-connect physical ports: ${e.message}" }
        }
    }

    /**
     * Stops the JACK client session.
     */
    fun stop() {
        try {
            errorLoggerExecutor.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            logger.info { "Stopping JACK client..." }
            client?.deactivate()
            client?.close()
        } catch (e: Exception) {
            // Ignore
        }
        client = null
        inputPort = null
    }
}
