package llm.slop.liquidlsd.midi

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
enum class MidiInputType {
    CONTINUOUS_CC,        // Standard absolute pot/fader (0..127)
    BUTTON_NOTE,          // Pad/Key Note-On/Off
    BUTTON_CC,            // Button sending CC (0 vs 127)
    ROTARY_BINARY_OFFSET, // Relative: 65=+1, 63=-1
    ROTARY_SIGNED_BIT,    // Relative: 1=+1, 65=-1
    ROTARY_TWOS_COMP,     // Relative: 1=+1, 127=-1
    PITCH_BEND            // 14-bit bipolar (-1.0..1.0)
}

@Serializable
enum class TriggerMode {
    TOGGLE,          // Latched toggle on/off
    MOMENTARY,       // Active while held, resets on release
    STEP_INCREMENT,  // Press steps value up by stepSize
    STEP_DECREMENT   // Press steps value down by stepSize
}

@Serializable
enum class TakeoverMode {
    IMMEDIATE,       // Immediately jumps to hardware value
    SOFT_TAKEOVER    // Wait until hardware value crosses current software value
}

@Serializable
data class MidiControlMapping(
    val cc: Int = 0, // Note# or CC# (0..127)
    val channel: Int = 0, // 0..15
    val minVal: Float = 0f,
    val maxVal: Float = 1f,
    val messageType: MidiMessageType = MidiMessageType.CC,
    val inputType: MidiInputType = MidiInputType.CONTINUOUS_CC,
    val triggerMode: TriggerMode = TriggerMode.TOGGLE,
    val takeoverMode: TakeoverMode = TakeoverMode.IMMEDIATE,
    val inverted: Boolean = false,
    val slewMs: Float = 0f,
    val stepSize: Float = 0.05f
)

@Serializable
data class MidiMappingProfile(
    val profileName: String,
    val mappings: Map<String, MidiControlMapping> = emptyMap()
)

object MidiMappingManager {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val midiDir = File("library/midi")

    var activeProfileName = "default"
        private set

    private var activeProfile = MidiMappingProfile("Default Profile")

    // Runtime state tracking
    private val lastRawMidi = ConcurrentHashMap<String, Float>()
    private val targetValues = ConcurrentHashMap<String, Float>()
    private val smoothedValues = ConcurrentHashMap<String, Float>()
    private val hasTakenOver = ConcurrentHashMap<String, Boolean>()
    private val lastPhysicalScaled = ConcurrentHashMap<String, Float>()
    private val buttonLatched = ConcurrentHashMap<String, Boolean>()
    private val lastButtonHigh = ConcurrentHashMap<String, Boolean>()

    private var lastUpdateTimeNanos = System.nanoTime()

    init {
        if (!midiDir.exists()) midiDir.mkdirs()
        loadProfile(activeProfileName)
    }

    fun listProfiles(): List<String> {
        val files = midiDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        val list = files.map { it.nameWithoutExtension }.sorted().toMutableList()
        if (list.isEmpty()) list.add("default")
        return list
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
        invalidateBindings()
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

    fun deleteProfile(profileName: String): Boolean {
        if (profileName == "default") return false
        val safeProfileName = try { sanitiseProfileName(profileName) } catch (e: Exception) { return false }
        val file = midiProfileFile(midiDir, safeProfileName)
        return try {
            val deleted = file.delete()
            if (deleted && activeProfileName == safeProfileName) {
                loadProfile("default")
            }
            deleted
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete MIDI profile: $safeProfileName" }
            false
        }
    }

    fun getMappingForParameter(parameterPath: String): MidiControlMapping? {
        return activeProfile.mappings[parameterPath]
    }

    fun hasMapping(parameterPath: String): Boolean {
        return activeProfile.mappings.containsKey(parameterPath)
    }

    fun getMappings(): Map<String, MidiControlMapping> {
        return activeProfile.mappings
    }

    fun addMapping(
        parameterPath: String,
        cc: Int,
        channel: Int = 0,
        minVal: Float = 0f,
        maxVal: Float = 1f,
        messageType: MidiMessageType = MidiMessageType.CC,
        inputType: MidiInputType = MidiInputType.CONTINUOUS_CC,
        triggerMode: TriggerMode = TriggerMode.TOGGLE,
        takeoverMode: TakeoverMode = TakeoverMode.IMMEDIATE,
        inverted: Boolean = false,
        slewMs: Float = 0f,
        stepSize: Float = 0.05f
    ) {
        val mapping = MidiControlMapping(
            cc = cc,
            channel = channel,
            minVal = minVal,
            maxVal = maxVal,
            messageType = messageType,
            inputType = inputType,
            triggerMode = triggerMode,
            takeoverMode = takeoverMode,
            inverted = inverted,
            slewMs = slewMs,
            stepSize = stepSize
        )
        val newMappings = activeProfile.mappings.toMutableMap()
        newMappings[parameterPath] = mapping
        activeProfile = activeProfile.copy(mappings = newMappings)
        invalidateBindings()
    }

    fun updateMapping(parameterPath: String, mapping: MidiControlMapping) {
        val newMappings = activeProfile.mappings.toMutableMap()
        newMappings[parameterPath] = mapping
        activeProfile = activeProfile.copy(mappings = newMappings)
        invalidateBindings()
    }

    fun removeMapping(parameterPath: String) {
        val newMappings = activeProfile.mappings.toMutableMap()
        newMappings.remove(parameterPath)
        activeProfile = activeProfile.copy(mappings = newMappings)
        invalidateBindings()
    }

    fun clearAllMappings() {
        activeProfile = activeProfile.copy(mappings = emptyMap())
        invalidateBindings()
    }

    fun getCcForSpecial(specialPath: String): Int {
        return activeProfile.mappings[specialPath]?.cc ?: -1
    }

    fun getChannelForSpecial(specialPath: String): Int {
        return activeProfile.mappings[specialPath]?.channel ?: 0
    }

    fun isSoftTakeoverActive(parameterPath: String): Boolean {
        return hasTakenOver[parameterPath] ?: true
    }

    fun getPhysicalPosition(parameterPath: String): Float? {
        return lastPhysicalScaled[parameterPath]
    }

    fun decodeRotaryDelta(inputType: MidiInputType, rawValue: Int): Int {
        return when (inputType) {
            MidiInputType.ROTARY_BINARY_OFFSET -> rawValue - 64
            MidiInputType.ROTARY_SIGNED_BIT -> if (rawValue > 64) -(rawValue - 64) else rawValue
            MidiInputType.ROTARY_TWOS_COMP -> if (rawValue >= 64) rawValue - 128 else rawValue
            else -> 0
        }
    }

    private class ResolvedMidiBinding(
        val path: String,
        val param: ModulatableParameter,
        val channel: Int,
        val index: Int,
        val minVal: Float,
        val maxVal: Float,
        val messageType: MidiMessageType,
        val inputType: MidiInputType,
        val triggerMode: TriggerMode,
        val takeoverMode: TakeoverMode,
        val inverted: Boolean,
        val slewMs: Float,
        val stepSize: Float,
        val isCrossfade: Boolean
    )

    @Volatile
    private var resolvedBindings: Array<ResolvedMidiBinding> = emptyArray()
    @Volatile
    private var bindingsDirty = true

    fun invalidateBindings() {
        bindingsDirty = true
        hasTakenOver.clear()
        targetValues.clear()
        smoothedValues.clear()
        lastRawMidi.clear()
        lastPhysicalScaled.clear()
        buttonLatched.clear()
        lastButtonHigh.clear()
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
                    index = mapping.cc,
                    minVal = mapping.minVal,
                    maxVal = mapping.maxVal,
                    messageType = mapping.messageType,
                    inputType = mapping.inputType,
                    triggerMode = mapping.triggerMode,
                    takeoverMode = mapping.takeoverMode,
                    inverted = mapping.inverted,
                    slewMs = mapping.slewMs,
                    stepSize = mapping.stepSize,
                    isCrossfade = (path == "Mixer/crossfade")
                )
            )
        }
        resolvedBindings = list.toTypedArray()
        bindingsDirty = false
    }

    /**
     * Called when a new MIDI event is received. Dispatches to all matching bindings.
     */
    fun onMidiEvent(event: MidiEvent, mixer: Mixer) {
        val bindings = resolvedBindings
        for (i in 0 until bindings.size) {
            val b = bindings[i]
            if (b.channel != event.channel || b.index != event.index) continue
            if (b.messageType != event.type && !(b.messageType == MidiMessageType.CC && event.type == MidiMessageType.PITCH_BEND)) continue

            when (b.inputType) {
                MidiInputType.ROTARY_BINARY_OFFSET,
                MidiInputType.ROTARY_SIGNED_BIT,
                MidiInputType.ROTARY_TWOS_COMP -> {
                    val deltaSteps = decodeRotaryDelta(b.inputType, event.rawValue)
                    if (deltaSteps != 0) {
                        val sign = if (b.inverted) -1f else 1f
                        val deltaVal = deltaSteps * b.stepSize * (b.maxVal - b.minVal) * sign
                        val newVal = (b.param.baseValue + deltaVal).coerceIn(b.minVal, b.maxVal)
                        b.param.baseValue = newVal
                        targetValues[b.path] = newVal
                        smoothedValues[b.path] = newVal
                        if (b.isCrossfade) mixer.onCrossfadeManualTakeover()
                    }
                }
                MidiInputType.BUTTON_NOTE,
                MidiInputType.BUTTON_CC -> {
                    val isHigh = event.normalizedValue > 0.05f
                    val prevHigh = lastButtonHigh[b.path] ?: false
                    lastButtonHigh[b.path] = isHigh

                    when (b.triggerMode) {
                        TriggerMode.MOMENTARY -> {
                            val activeVal = if (b.inverted) b.minVal else b.maxVal
                            val inactiveVal = if (b.inverted) b.maxVal else b.minVal
                            val newVal = if (isHigh) activeVal else inactiveVal
                            b.param.baseValue = newVal
                            targetValues[b.path] = newVal
                            smoothedValues[b.path] = newVal
                            if (b.isCrossfade) mixer.onCrossfadeManualTakeover()
                        }
                        TriggerMode.TOGGLE -> {
                            if (isHigh && !prevHigh) {
                                // Rising edge toggle
                                val currentLatched = buttonLatched[b.path] ?: false
                                val nextLatched = !currentLatched
                                buttonLatched[b.path] = nextLatched

                                val activeVal = if (b.inverted) b.minVal else b.maxVal
                                val inactiveVal = if (b.inverted) b.maxVal else b.minVal
                                val newVal = if (nextLatched) activeVal else inactiveVal
                                b.param.baseValue = newVal
                                targetValues[b.path] = newVal
                                smoothedValues[b.path] = newVal
                                if (b.isCrossfade) mixer.onCrossfadeManualTakeover()
                            }
                        }
                        TriggerMode.STEP_INCREMENT -> {
                            if (isHigh && !prevHigh) {
                                val step = b.stepSize * (b.maxVal - b.minVal) * (if (b.inverted) -1f else 1f)
                                val newVal = (b.param.baseValue + step).coerceIn(b.minVal, b.maxVal)
                                b.param.baseValue = newVal
                                targetValues[b.path] = newVal
                                smoothedValues[b.path] = newVal
                                if (b.isCrossfade) mixer.onCrossfadeManualTakeover()
                            }
                        }
                        TriggerMode.STEP_DECREMENT -> {
                            if (isHigh && !prevHigh) {
                                val step = b.stepSize * (b.maxVal - b.minVal) * (if (b.inverted) 1f else -1f)
                                val newVal = (b.param.baseValue + step).coerceIn(b.minVal, b.maxVal)
                                b.param.baseValue = newVal
                                targetValues[b.path] = newVal
                                smoothedValues[b.path] = newVal
                                if (b.isCrossfade) mixer.onCrossfadeManualTakeover()
                            }
                        }
                    }
                }
                MidiInputType.CONTINUOUS_CC,
                MidiInputType.PITCH_BEND -> {
                    var norm = event.normalizedValue
                    if (b.messageType == MidiMessageType.PITCH_BEND) {
                        // Pitch bend is -1.0..1.0, map to 0.0..1.0
                        norm = (norm + 1.0f) * 0.5f
                    }
                    if (b.inverted) norm = 1.0f - norm

                    val scaledTarget = b.minVal + norm * (b.maxVal - b.minVal)
                    val prevPhys = lastPhysicalScaled[b.path]
                    lastPhysicalScaled[b.path] = scaledTarget

                    if (b.takeoverMode == TakeoverMode.SOFT_TAKEOVER) {
                        val alreadyTakenOver = hasTakenOver[b.path] ?: false
                        if (!alreadyTakenOver) {
                            val currentVal = b.param.baseValue
                            val tolerance = (b.maxVal - b.minVal) * 0.04f
                            // Crossed or within tolerance
                            val crossed = prevPhys != null && ((prevPhys - currentVal) * (scaledTarget - currentVal) <= 0f)
                            val closeEnough = kotlin.math.abs(scaledTarget - currentVal) <= tolerance
                            if (crossed || closeEnough) {
                                hasTakenOver[b.path] = true
                            }
                        }
                    } else {
                        hasTakenOver[b.path] = true
                    }

                    if (hasTakenOver[b.path] == true) {
                        targetValues[b.path] = scaledTarget
                        if (b.slewMs <= 0f) {
                            b.param.baseValue = scaledTarget
                            smoothedValues[b.path] = scaledTarget
                        }
                        if (b.isCrossfade) mixer.onCrossfadeManualTakeover()
                    }
                }
            }
        }
    }

    /**
     * Frame-by-frame update: applies slew smoothing and parameter convergence.
     */
    fun update(mixer: Mixer) {
        if (bindingsDirty) {
            rebuildResolvedBindings(mixer)
        }

        val nowNanos = System.nanoTime()
        val dtSeconds = ((nowNanos - lastUpdateTimeNanos) / 1_000_000_000.0).toFloat().coerceIn(0.0001f, 0.1f)
        lastUpdateTimeNanos = nowNanos

        val bindings = resolvedBindings
        for (i in 0 until bindings.size) {
            val b = bindings[i]
            val target = targetValues[b.path] ?: continue

            if (b.slewMs > 0f) {
                val current = smoothedValues[b.path] ?: b.param.baseValue
                val tau = (b.slewMs / 1000f).coerceAtLeast(0.001f)
                val alpha = (1.0f - kotlin.math.exp(-dtSeconds / tau)).coerceIn(0.01f, 1.0f)
                val nextVal = current + (target - current) * alpha
                smoothedValues[b.path] = nextVal
                b.param.baseValue = nextVal
            }
        }
    }
}

