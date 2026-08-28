package llm.slop.liquidlsd.broadcast

import mu.KotlinLogging
import java.io.File
import java.util.Properties

/**
 * Manages configuration and persistence for the WebSocket broadcast relay.
 */
object BroadcastSettings {
    private val logger = KotlinLogging.logger {}
    private val settingsFile = File("lsd-settings.properties")

    @Volatile
    var serverUrl: String = "http://spaz.org/lsd-relay"

    @Volatile
    var token: String = "lsd25"

    @Volatile
    var autoConnect: Boolean = false

    @Volatile
    var targetFps: Int = 25

    init {
        loadSettings()
    }

    fun loadSettings() {
        if (!settingsFile.exists()) return
        try {
            val props = Properties()
            settingsFile.inputStream().use { props.load(it) }
            props.getProperty("broadcast.serverUrl")?.let { if (it.isNotBlank()) serverUrl = it.trim() }
            props.getProperty("broadcast.token")?.let { token = it.trim() }
            props.getProperty("broadcast.autoConnect")?.toBooleanStrictOrNull()?.let { autoConnect = it }
            props.getProperty("broadcast.targetFps")?.toIntOrNull()?.let { targetFps = it.coerceIn(5, 60) }
            logger.info { "Loaded broadcast settings: url=$serverUrl, autoConnect=$autoConnect, targetFps=$targetFps" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load broadcast settings from ${settingsFile.name}" }
        }
    }

    fun saveSettings() {
        try {
            val props = Properties()
            if (settingsFile.exists()) {
                settingsFile.inputStream().use { props.load(it) }
            }
            props.setProperty("broadcast.serverUrl", serverUrl)
            props.setProperty("broadcast.token", token)
            props.setProperty("broadcast.autoConnect", autoConnect.toString())
            props.setProperty("broadcast.targetFps", targetFps.toString())
            settingsFile.outputStream().use { props.store(it, "Liquid LSD User Settings") }
            logger.info { "Saved broadcast settings to ${settingsFile.name}" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to save broadcast settings to ${settingsFile.name}" }
        }
    }
}
