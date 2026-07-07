package llm.slop.spirals.midi

import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.ParameterResolver
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mandala
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import llm.slop.spirals.config.ProjectConfig
import mu.KotlinLogging
import java.io.File

private val safeProfileCharacter = Regex("[^A-Za-z0-9._-]")
private val repeatedUnderscores = Regex("_+")

internal fun sanitizeMidiProfileName(profileName: String): String {
    val sanitized = profileName.trim()
        .replace('\\', '_')
        .replace('/', '_')
        .replace(safeProfileCharacter, "_")
        .replace(repeatedUnderscores, "_")
        .trim('.', '_', '-')
        .take(80)

    return sanitized.ifBlank { "default" }
}

internal fun midiProfileFile(midiDir: File, profileName: String): File {
    val safeName = sanitizeMidiProfileName(profileName)
    val file = File(midiDir, "$safeName.json")
    val rootPath = midiDir.canonicalFile.toPath()
    val filePath = file.canonicalFile.toPath()

    require(filePath.startsWith(rootPath)) {
        "MIDI profile path escapes ${ProjectConfig.Paths.MIDI_DIR}: $profileName"
    }

    return file
}

@Serializable
data class MidiControlMapping(
    val cc: Int,
    val channel: Int = 0,
    val minVal: Float = 0f,
    val maxVal: Float = 1f
)

@Serializable
data class MidiMappingProfile(
    val profileName: String,
    val mappings: Map<String, MidiControlMapping> = emptyMap()
)

object MidiMappingManager {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val midiDir = File(ProjectConfig.Paths.MIDI_DIR)

    var activeProfileName = ProjectConfig.Files.DEFAULT_MIDI_PROFILE
        private set

    private var activeProfile = MidiMappingProfile("Default Profile")

    init {
        if (!midiDir.exists()) midiDir.mkdirs()
        loadProfile(activeProfileName)
    }

    fun loadProfile(profileName: String) {
        val safeProfileName = sanitizeMidiProfileName(profileName)
        val file = midiProfileFile(midiDir, safeProfileName)
        if (file.exists()) {
            try {
                val content = file.readText()
                activeProfile = json.decodeFromString<MidiMappingProfile>(content)
                activeProfileName = safeProfileName
                logger.info { "Loaded MIDI mapping profile: $safeProfileName" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load MIDI profile: $safeProfileName" }
            }
        } else {
            // Create default empty profile
            activeProfile = MidiMappingProfile(safeProfileName)
            activeProfileName = safeProfileName
            saveActiveProfile()
        }
    }

    fun saveActiveProfile() {
        val file = midiProfileFile(midiDir, activeProfileName)
        try {
            val content = json.encodeToString(activeProfile)
            file.writeText(content)
            logger.info { "Saved MIDI mapping profile: $activeProfileName" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save MIDI profile: $activeProfileName" }
        }
    }

    fun getMappingForParameter(parameterPath: String): MidiControlMapping? {
        return activeProfile.mappings[parameterPath]
    }

    fun hasMapping(parameterPath: String): Boolean {
        return activeProfile.mappings.containsKey(parameterPath)
    }

    fun addMapping(parameterPath: String, cc: Int, channel: Int = 0, minVal: Float = 0f, maxVal: Float = 1f) {
        val newMappings = activeProfile.mappings.toMutableMap()
        newMappings[parameterPath] = MidiControlMapping(cc, channel, minVal, maxVal)
        activeProfile = activeProfile.copy(mappings = newMappings)
    }

    fun removeMapping(parameterPath: String) {
        val newMappings = activeProfile.mappings.toMutableMap()
        newMappings.remove(parameterPath)
        activeProfile = activeProfile.copy(mappings = newMappings)
    }

    fun getCcForSpecial(specialPath: String): Int {
        return activeProfile.mappings[specialPath]?.cc ?: -1
    }

    fun getChannelForSpecial(specialPath: String): Int {
        return activeProfile.mappings[specialPath]?.channel ?: 0
    }

    fun update(mixer: Mixer) {
        for ((path, mapping) in activeProfile.mappings) {
            if (path.startsWith("Global/")) continue // special trigger mappings
            val param = ParameterResolver.findParameterByPath(mixer, path) ?: continue
            val rawMidi = MidiEngine.getCcValue(mapping.channel, mapping.cc)
            val scaled = mapping.minVal + rawMidi * (mapping.maxVal - mapping.minVal)
            param.baseValue = scaled
        }
    }

}
