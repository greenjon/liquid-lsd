package llm.slop.liquidlsd.osc

import mu.KotlinLogging
import java.io.File
import java.util.Properties

/**
 * Manages configuration and persistence for the OSC/TouchOSC UDP transport
 * (enable flag, inbound/outbound ports, active mapping profile).
 */
object OscPreferences {
    private val logger = KotlinLogging.logger {}
    private val preferencesFile = File("lsd-preferences.properties")

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var inboundPort: Int = OscEngine.DEFAULT_INBOUND_PORT

    @Volatile
    var outboundPort: Int = OscEngine.DEFAULT_OUTBOUND_PORT

    @Volatile
    var activeProfile: String = "default"

    init {
        loadPreferences()
    }

    fun loadPreferences() {
        if (!preferencesFile.exists()) return
        try {
            val props = Properties()
            preferencesFile.inputStream().use { props.load(it) }
            props.getProperty("osc.enabled")?.let { enabled = it.toBooleanStrictOrNull() ?: it.toBoolean() }
            props.getProperty("osc.inboundPort")?.toIntOrNull()?.let { inboundPort = it.coerceIn(1, 65535) }
            props.getProperty("osc.outboundPort")?.toIntOrNull()?.let { outboundPort = it.coerceIn(1, 65535) }
            props.getProperty("osc.activeProfile")?.let { activeProfile = it }
            logger.info { "Loaded OSC preferences: enabled=$enabled, inboundPort=$inboundPort, outboundPort=$outboundPort" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load OSC preferences" }
        }
    }

    fun savePreferences() {
        try {
            val props = Properties()
            if (preferencesFile.exists()) preferencesFile.inputStream().use { props.load(it) }
            props.setProperty("osc.enabled", enabled.toString())
            props.setProperty("osc.inboundPort", inboundPort.toString())
            props.setProperty("osc.outboundPort", outboundPort.toString())
            props.setProperty("osc.activeProfile", activeProfile)
            preferencesFile.outputStream().use { props.store(it, "Liquid LSD User Preferences") }
            logger.info { "Saved OSC preferences" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to save OSC preferences" }
        }
    }
}
