package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Resolved metadata for a registered directory source after path expansion and status evaluation.
 */
data class ResolvedDirectorySource(
    val config: DirectorySourceConfig,
    val expandedPath: String,
    val status: DirectoryStatus
)

/**
 * Manages ISF directory sources, platform-specific default path resolution, variable expansion,
 * status evaluation (Active, Missing, Unreadable), and persistent serialization.
 */
object ISFDirectoryManager {
    private var configFile: File = File("library/isf_directories.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var currentSources: MutableList<DirectorySourceConfig> = mutableListOf()

    /**
     * Reconfigures the storage file (primarily for unit testing isolation).
     */
    fun setConfigFile(file: File) {
        configFile = file
    }

    /**
     * Expands path variables such as `~`, `%ENV_VAR%`, and `$ENV_VAR` into an absolute normalized path.
     */
    fun expandPath(
        path: String,
        userHome: String = System.getProperty("user.home") ?: "",
        envLookup: (String) -> String? = { System.getenv(it) }
    ): String {
        if (path.isBlank()) return ""

        var result = path

        // Handle ~ home expansion
        if (result == "~" || result.startsWith("~/") || result.startsWith("~\\")) {
            result = userHome + result.substring(1)
        }

        // Handle Windows %ENV_VAR% syntax
        val winEnvRegex = Regex("""%([A-Za-z0-9_]+)%""")
        result = winEnvRegex.replace(result) { match ->
            val envKey = match.groupValues[1]
            envLookup(envKey) ?: match.value
        }

        // Handle Unix ${ENV_VAR} or $ENV_VAR syntax
        val unixEnvRegex = Regex("""\$\{([A-Za-z0-9_]+)\}|\$([A-Za-z0-9_]+)""")
        result = unixEnvRegex.replace(result) { match ->
            val envKey = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (envKey == "XDG_DATA_HOME") {
                envLookup("XDG_DATA_HOME") ?: (userHome + "/.local/share")
            } else {
                envLookup(envKey) ?: match.value
            }
        }

        return try {
            File(result).canonicalPath
        } catch (e: Exception) {
            File(result).absolutePath
        }
    }

    /**
     * Evaluates the availability status of a directory source on the local filesystem.
     */
    fun evaluateStatus(
        config: DirectorySourceConfig,
        userHome: String = System.getProperty("user.home") ?: "",
        envLookup: (String) -> String? = { System.getenv(it) }
    ): DirectoryStatus {
        val expanded = expandPath(config.path, userHome, envLookup)
        if (expanded.isBlank()) return DirectoryStatus.MISSING

        val file = File(expanded)
        return when {
            !file.exists() -> DirectoryStatus.MISSING
            file.isDirectory && file.canRead() -> DirectoryStatus.ACTIVE
            else -> DirectoryStatus.UNREADABLE
        }
    }

    /**
     * Returns platform-standard default directory sources for the specified OS environment.
     */
    fun getPlatformDefaultSources(
        osName: String = System.getProperty("os.name") ?: "",
        userHome: String = System.getProperty("user.home") ?: "",
        envLookup: (String) -> String? = { System.getenv(it) }
    ): List<DirectorySourceConfig> {
        val defaults = mutableListOf<DirectorySourceConfig>()

        // Built-In sources (always preserved across platforms)
        defaults.add(DirectorySourceConfig("library/sources", DirectorySourceType.BUILT_IN, isEnabled = true))
        defaults.add(DirectorySourceConfig("library/filters", DirectorySourceType.BUILT_IN, isEnabled = true))
        defaults.add(DirectorySourceConfig("library/transitions", DirectorySourceType.BUILT_IN, isEnabled = true))

        val lowerOs = osName.lowercase()
        when {
            lowerOs.contains("mac") || lowerOs.contains("darwin") -> {
                defaults.add(DirectorySourceConfig("/Library/Graphics/ISF/", DirectorySourceType.SYSTEM_STANDARD, isEnabled = true))
                defaults.add(DirectorySourceConfig("~/Library/Graphics/ISF/", DirectorySourceType.USER_STANDARD, isEnabled = true))
            }
            lowerOs.contains("win") -> {
                defaults.add(DirectorySourceConfig("%ProgramData%\\ISF\\", DirectorySourceType.SYSTEM_STANDARD, isEnabled = true))
                defaults.add(DirectorySourceConfig("%LOCALAPPDATA%\\ISF\\", DirectorySourceType.USER_STANDARD, isEnabled = true))
            }
            else -> {
                // Linux / Unix
                defaults.add(DirectorySourceConfig("/usr/share/isf/", DirectorySourceType.SYSTEM_STANDARD, isEnabled = true))
                defaults.add(DirectorySourceConfig("/usr/local/share/isf/", DirectorySourceType.SYSTEM_STANDARD, isEnabled = true))

                val xdgDataHome = envLookup("XDG_DATA_HOME")
                val userPath = if (!xdgDataHome.isNullAndBlank()) {
                    "$xdgDataHome/isf/"
                } else {
                    "~/.local/share/isf/"
                }
                defaults.add(DirectorySourceConfig(userPath, DirectorySourceType.USER_STANDARD, isEnabled = true))
            }
        }

        return defaults
    }

    /**
     * Loads directory configurations from persistent JSON storage, auto-populating missing defaults.
     */
    @Synchronized
    fun loadSettings(
        osName: String = System.getProperty("os.name") ?: "",
        userHome: String = System.getProperty("user.home") ?: "",
        envLookup: (String) -> String? = { System.getenv(it) }
    ): List<DirectorySourceConfig> {
        val platformDefaults = getPlatformDefaultSources(osName, userHome, envLookup)

        if (!configFile.exists()) {
            currentSources = platformDefaults.toMutableList()
            saveSettings()
            return currentSources.toList()
        }

        try {
            val content = configFile.readText()
            val loadedSettings = json.decodeFromString<ISFDirectorySettings>(content)
            val mergedList = mutableListOf<DirectorySourceConfig>()
            mergedList.addAll(loadedSettings.sources)

            // Ensure missing default paths for current OS are merged into existing settings
            for (defaultConfig in platformDefaults) {
                val expandedDefault = expandPath(defaultConfig.path, userHome, envLookup)
                val existsInLoaded = mergedList.any { loaded ->
                    expandPath(loaded.path, userHome, envLookup) == expandedDefault || loaded.path == defaultConfig.path
                }
                if (!existsInLoaded) {
                    mergedList.add(defaultConfig)
                }
            }

            currentSources = mergedList
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse ISF directory settings from ${configFile.path}; restoring platform defaults." }
            currentSources = platformDefaults.toMutableList()
            saveSettings()
        }

        return currentSources.toList()
    }

    /**
     * Persists the current directory configurations to disk.
     */
    @Synchronized
    fun saveSettings() {
        try {
            val parentDir = configFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }
            val settingsContainer = ISFDirectorySettings(sources = currentSources)
            val serialized = json.encodeToString(ISFDirectorySettings.serializer(), settingsContainer)
            configFile.writeText(serialized)
        } catch (e: Exception) {
            logger.error(e) { "Failed to save ISF directory settings to ${configFile.path}" }
        }
    }

    /**
     * Returns the currently configured raw directory sources.
     */
    @Synchronized
    fun getRegisteredDirectories(): List<DirectorySourceConfig> {
        if (currentSources.isEmpty()) {
            loadSettings()
        }
        return currentSources.toList()
    }

    /**
     * Returns all registered directory sources paired with their expanded path and evaluated status.
     */
    @Synchronized
    fun getResolvedDirectories(
        userHome: String = System.getProperty("user.home") ?: "",
        envLookup: (String) -> String? = { System.getenv(it) }
    ): List<ResolvedDirectorySource> {
        return getRegisteredDirectories().map { config ->
            val expanded = expandPath(config.path, userHome, envLookup)
            val status = evaluateStatus(config, userHome, envLookup)
            ResolvedDirectorySource(config, expanded, status)
        }
    }

    /**
     * Registers a new custom local directory source.
     */
    @Synchronized
    fun addCustomDirectory(
        path: String,
        userHome: String = System.getProperty("user.home") ?: "",
        envLookup: (String) -> String? = { System.getenv(it) }
    ): Boolean {
        if (path.isBlank()) return false
        val normalizedInput = expandPath(path, userHome, envLookup)

        if (currentSources.isEmpty()) {
            loadSettings(userHome = userHome, envLookup = envLookup)
        }

        val alreadyExists = currentSources.any { existing ->
            expandPath(existing.path, userHome, envLookup) == normalizedInput || existing.path == path
        }

        if (alreadyExists) {
            return false
        }

        val newConfig = DirectorySourceConfig(
            path = path.trim(),
            type = DirectorySourceType.CUSTOM,
            isEnabled = true
        )
        currentSources.add(newConfig)
        saveSettings()
        return true
    }

    /**
     * Removes a directory source. Built-in sources cannot be removed.
     */
    @Synchronized
    fun removeDirectory(
        path: String,
        userHome: String = System.getProperty("user.home") ?: "",
        envLookup: (String) -> String? = { System.getenv(it) }
    ): Boolean {
        if (currentSources.isEmpty()) {
            loadSettings(userHome = userHome, envLookup = envLookup)
        }

        val targetExpanded = expandPath(path, userHome, envLookup)
        val target = currentSources.find { existing ->
            existing.path == path || expandPath(existing.path, userHome, envLookup) == targetExpanded
        } ?: return false

        if (target.type == DirectorySourceType.BUILT_IN) {
            logger.warn { "Attempted to remove protected built-in directory: ${target.path}" }
            return false
        }

        val removed = currentSources.remove(target)
        if (removed) {
            saveSettings()
        }
        return removed
    }

    /**
     * Toggles the enabled state of a directory source.
     */
    @Synchronized
    fun toggleDirectoryEnabled(
        path: String,
        isEnabled: Boolean,
        userHome: String = System.getProperty("user.home") ?: "",
        envLookup: (String) -> String? = { System.getenv(it) }
    ): Boolean {
        if (currentSources.isEmpty()) {
            loadSettings(userHome = userHome, envLookup = envLookup)
        }

        val targetExpanded = expandPath(path, userHome, envLookup)
        val index = currentSources.indexOfFirst { existing ->
            existing.path == path || expandPath(existing.path, userHome, envLookup) == targetExpanded
        }

        if (index == -1) return false

        val updatedConfig = currentSources[index].copy(isEnabled = isEnabled)
        currentSources[index] = updatedConfig
        saveSettings()
        return true
    }

    /**
     * Resets directory sources to the platform defaults for the current OS.
     */
    @Synchronized
    fun resetToDefaults(
        osName: String = System.getProperty("os.name") ?: "",
        userHome: String = System.getProperty("user.home") ?: "",
        envLookup: (String) -> String? = { System.getenv(it) }
    ): List<DirectorySourceConfig> {
        val defaults = getPlatformDefaultSources(osName, userHome, envLookup)
        currentSources = defaults.toMutableList()
        saveSettings()
        return currentSources.toList()
    }
}

private fun String?.isNullAndBlank(): Boolean = this == null || this.isBlank()
