package llm.slop.liquidlsd.midi

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mandala
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import mu.KotlinLogging
import java.io.File

private val safeProfileCharacter = Regex("[^A-Za-z0-9._-]")
private val repeatedUnderscores = Regex("_+")

internal fun sanitiseProfileName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..") || trimmed.contains("\u0000")) {
        throw IllegalArgumentException("Invalid characters in MIDI profile name: $name")
    }
    if (!trimmed.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
        throw IllegalArgumentException("MIDI profile name must contain only alphanumeric characters, underscores, and hyphens: $name")
    }
    return trimmed
}

internal fun midiProfileFile(midiDir: File, profileName: String): File {
    val safeName = sanitiseProfileName(profileName)
    val file = File(midiDir, "$safeName.json")
    val rootPath = midiDir.canonicalFile.toPath()
    val filePath = file.canonicalFile.toPath()

    require(filePath.startsWith(rootPath)) {
        "MIDI profile path escapes library/midi: $profileName"
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
    private val midiDir = File("library/midi")

    var activeProfileName = "default"
        private set

    private var activeProfile = MidiMappingProfile("Default Profile")

    init {
        if (!midiDir.exists()) midiDir.mkdirs()
        loadProfile(activeProfileName)
    }

    fun loadProfile(profileName: String) {
        val safeProfileName = try { sanitiseProfileName(profileName) } catch (e: Exception) { "default" }
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
        bindingsDirty = true
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
        bindingsDirty = true
    }

    fun removeMapping(parameterPath: String) {
        val newMappings = activeProfile.mappings.toMutableMap()
        newMappings.remove(parameterPath)
        activeProfile = activeProfile.copy(mappings = newMappings)
        bindingsDirty = true
    }

    fun getCcForSpecial(specialPath: String): Int {
        return activeProfile.mappings[specialPath]?.cc ?: -1
    }

    private val lastMidiValues = java.util.concurrent.ConcurrentHashMap<String, Float>()

    private class ResolvedMidiBinding(
        val path: String,
        val param: ModulatableParameter,
        val channel: Int,
        val cc: Int,
        val minVal: Float,
        val maxVal: Float,
        val isCrossfade: Boolean
    )

    @Volatile
    private var resolvedBindings: Array<ResolvedMidiBinding> = emptyArray()
    @Volatile
    private var bindingsDirty = true

    fun invalidateBindings() {
        bindingsDirty = true
    }

    private fun rebuildResolvedBindings(mixer: Mixer) {
        val list = ArrayList<ResolvedMidiBinding>()
        for ((path, mapping) in activeProfile.mappings) {
            if (path.startsWith("Global/")) continue
            val param = ParameterResolver.findParameterByPath(mixer, path) ?: continue
            list.add(
                ResolvedMidiBinding(
                    path = path,
                    param = param,
                    channel = mapping.channel,
                    cc = mapping.cc,
                    minVal = mapping.minVal,
                    maxVal = mapping.maxVal,
                    isCrossfade = (path == "Mixer/crossfade")
                )
            )
        }
        resolvedBindings = list.toTypedArray()
        bindingsDirty = false
    }

    fun getChannelForSpecial(specialPath: String): Int {
        return activeProfile.mappings[specialPath]?.channel ?: 0
    }

    fun update(mixer: Mixer) {
        if (bindingsDirty) {
            rebuildResolvedBindings(mixer)
        }
        val bindings = resolvedBindings
        for (i in 0 until bindings.size) {
            val b = bindings[i]
            val rawMidi = MidiEngine.getCcValue(b.channel, b.cc)
            val prevMidi = lastMidiValues[b.path]
            if (prevMidi != null && prevMidi != rawMidi) {
                if (b.isCrossfade) {
                    mixer.onCrossfadeManualTakeover()
                }
            }
            lastMidiValues[b.path] = rawMidi
            val scaled = b.minVal + rawMidi * (b.maxVal - b.minVal)
            b.param.baseValue = scaled
        }
    }

}
