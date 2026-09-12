package llm.slop.liquidlsd.broadcast

import mu.KotlinLogging
import java.io.File
import java.util.Properties

/**
 * Manages configuration and persistence for the WebSocket broadcast relay.
 */
object BroadcastPreferences {
    private val logger = KotlinLogging.logger {}
    private val preferencesFile = File("lsd-preferences.properties")
    private val legacySettingsFile = File("lsd-settings.properties")

    @Volatile
    var serverUrl: String = ""

    @Volatile
    var token: String = ""

    @Volatile
    var autoConnect: Boolean = false

    @Volatile
    var targetFps: Int = 25

    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && token.isNotBlank()

    init {
        loadPreferences()
    }

    fun loadPreferences() {
        val fileToRead = when {
            preferencesFile.exists() -> preferencesFile
            legacySettingsFile.exists() -> legacySettingsFile
            else -> return
        }
        try {
            val props = Properties()
            fileToRead.inputStream().use { props.load(it) }
            val url = props.getProperty("broadcast.serverUrl") ?: props.getProperty("broadcastServerUrl")
            url?.let { serverUrl = it.trim() }
            val tok = props.getProperty("broadcast.token") ?: props.getProperty("broadcastToken")
            tok?.let { token = it.trim() }
            val auto = props.getProperty("broadcast.autoConnect") ?: props.getProperty("broadcastAutoConnect")
            auto?.let { autoConnect = it.toBooleanStrictOrNull() ?: it.toBoolean() }
            val fps = props.getProperty("broadcast.targetFps") ?: props.getProperty("broadcastTargetFps")
            fps?.toIntOrNull()?.let { targetFps = it.coerceIn(5, 60) }
            logger.info { "Loaded broadcast preferences from ${fileToRead.name}: url=$serverUrl, autoConnect=$autoConnect, targetFps=$targetFps" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load broadcast preferences from ${fileToRead.name}" }
        }
    }

    fun savePreferences() {
        try {
            val props = Properties()
            val fileToRead = if (preferencesFile.exists()) preferencesFile else if (legacySettingsFile.exists()) legacySettingsFile else null
            fileToRead?.inputStream()?.use { props.load(it) }

            props.setProperty("broadcast.serverUrl", serverUrl)
            props.setProperty("broadcast.token", token)
            props.setProperty("broadcast.autoConnect", autoConnect.toString())
            props.setProperty("broadcast.targetFps", targetFps.toString())
            preferencesFile.outputStream().use { props.store(it, "Liquid LSD User Preferences") }
            logger.info { "Saved broadcast preferences to ${preferencesFile.name}" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to save broadcast preferences to ${preferencesFile.name}" }
        }
    }

    fun resetDefaults() {
        serverUrl = ""
        token = ""
        autoConnect = false
        targetFps = 25
    }

    // Backward compatibility delegates
    fun loadSettings() = loadPreferences()
    fun saveSettings() = savePreferences()
}

typealias BroadcastSettings = BroadcastPreferences
