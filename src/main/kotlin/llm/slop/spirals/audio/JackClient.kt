package llm.slop.spirals.audio

import org.jaudiolibs.jnajack.*
import java.nio.FloatBuffer
import java.util.EnumSet
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Handles initialization, callback registration, and port connections
 * for the JACK Audio Connection Kit.
 */
class JackClient(
    val clientName: String = "spirals-desktop",
    val onProcess: (FloatBuffer, Int, Float) -> Unit // (buffer, nframes, sampleRate)
) {
    private var client: org.jaudiolibs.jnajack.JackClient? = null
    private var inputPort: JackPort? = null

    val isConnected: Boolean
        get() = client != null

    /**
     * Initializes and activates the JACK client.
     */
    fun start() {
        try {
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
                } catch (e: Exception) {
                    logger.error(e) { "Error in JACK process callback" }
                }
                true
            }

            // Activate audio thread
            client!!.activate()
            logger.info { "JACK Client activated." }

            // Auto-connect to physical system inputs
            autoConnectInput()
        } catch (e: Throwable) {
            logger.warn { "Could not connect to JACK server: ${e.message}. Running in silent / fallback mode." }
            client = null
            inputPort = null
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
