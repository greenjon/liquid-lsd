package llm.slop.liquidlsd.midi

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ModulationOperator
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.MidiLearnTarget
import llm.slop.liquidlsd.ui.ParametersState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val safeProfileCharacter = Regex("[^A-Za-z0-9._-]")
private val repeatedUnderscores = Regex("_+")

private val fxMacroSyncBankIds = setOf(
    llm.slop.liquidlsd.macro.MacroEngine.FX_BANK_1,
    llm.slop.liquidlsd.macro.MacroEngine.FX_BANK_2,
    llm.slop.liquidlsd.macro.MacroEngine.MASTER_FX
)
private val macroKnobPathPattern = Regex("""^Macro/([^/]+)/knob_([1-4])$""")

/** True for "Macro/<bankId>/knob_N" paths on FX1/FX2/MFX's 4-knob row -- see [MidiLearnTarget.MacroTarget]. */
internal fun isFxMacroSyncOwnedKnobPath(macroPath: String): Boolean {
    val match = macroKnobPathPattern.matchEntire(macroPath) ?: return false
    return match.groupValues[1] in fxMacroSyncBankIds
}

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
    private val hasTakenOver = ConcurrentHashMap<String, Boolean>()
    private val lastPhysicalScaled = ConcurrentHashMap<String, Float>()
    private val buttonLatched = ConcurrentHashMap<String, Boolean>()
    private val lastButtonHigh = ConcurrentHashMap<String, Boolean>()

    private var lastUpdateTimeNanos = System.nanoTime()

    // Edge-detection state for the fixed set of global (non-parameter) MIDI actions,
    // consumed each frame by processGlobalMidiEvents().
    private var lastNextMidiCcHigh = false
    private var lastPrevMidiCcHigh = false
    private var lastBgNextMidiCcHigh = false
    private var lastBgPrevMidiCcHigh = false
    private var lastTransNextMidiCcHigh = false
    private var lastTransPrevMidiCcHigh = false
    private var lastTapMidiCcHigh = false
    private var lastAutoFadeMidiCcHigh = false
    private var lastSnapAMidiCcHigh = false
    private var lastSnapBMidiCcHigh = false

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
        val mapping = activeProfile.mappings[parameterPath]
        if (mapping?.takeoverMode == TakeoverMode.SOFT_TAKEOVER) {
            return hasTakenOver[parameterPath] ?: false
        }
        return true
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

    /**
     * Formats a user-friendly label for a parameter path, handling both base paths
     * and nested modulator variables (e.g. "Deck A/geometry/zoom [LFO 1 Speed]").
     */
    fun formatDisplayPath(parameterPath: String): String {
        if (parameterPath.contains(":mod/")) {
            val base = parameterPath.substringBefore(":mod/")
            val remainder = parameterPath.substringAfter(":mod/")
            val modIdx = remainder.substringBefore("/").toIntOrNull() ?: 0
            val prop = remainder.substringAfter("/")
            val label = llm.slop.liquidlsd.parameters.ModulatorPropertyAccessor.formatPropertyLabel(modIdx, prop)
            return "$base [$label]"
        }
        return parameterPath
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
        val isCrossfade: Boolean,
        val modIndex: Int? = null,
        val propertyName: String? = null
    ) {
        // Per-binding slew state, mutated directly on the hot per-frame update() path.
        // Lives as unboxed fields on the binding itself (indexed via resolvedBindings)
        // rather than in a String-keyed map, avoiding per-frame boxing/hashing/GC churn.
        // Reset to "unset" automatically whenever bindings are rebuilt (mapping changes),
        // since rebuildResolvedBindings() allocates fresh binding instances.
        var hasTarget: Boolean = false
        var targetValue: Float = 0f
        var hasSmoothed: Boolean = false
        var smoothedValue: Float = 0f

        fun getCurrentValue(): Float {
            return if (modIndex != null && propertyName != null) {
                param.modulators.getOrNull(modIndex)?.let {
                    llm.slop.liquidlsd.parameters.ModulatorPropertyAccessor.get(it, propertyName)
                } ?: param.baseValue
            } else {
                param.baseValue
            }
        }

        fun applyValue(value: Float) {
            if (modIndex != null && propertyName != null) {
                param.modulators.getOrNull(modIndex)?.let {
                    llm.slop.liquidlsd.parameters.ModulatorPropertyAccessor.set(it, propertyName, value)
                }
            } else {
                param.baseValue = value
            }
        }
    }

    @Volatile
    private var resolvedBindings: Array<ResolvedMidiBinding> = emptyArray()
    @Volatile
    private var bindingsDirty = true

    fun invalidateBindings() {
        bindingsDirty = true
        hasTakenOver.clear()
        lastRawMidi.clear()
        lastPhysicalScaled.clear()
        buttonLatched.clear()
        lastButtonHigh.clear()
    }

    private fun rebuildResolvedBindings(mixer: Mixer) {
        val list = ArrayList<ResolvedMidiBinding>()
        for ((path, mapping) in activeProfile.mappings) {
            if (path.startsWith("Global/") || path.startsWith("Macro/")) continue

            val (baseParamPath, modIndex, propertyName) = if (path.contains(":mod/")) {
                val base = path.substringBefore(":mod/")
                val rem = path.substringAfter(":mod/")
                val idx = rem.substringBefore("/").toIntOrNull()
                val prop = rem.substringAfter("/")
                Triple(base, idx, prop)
            } else {
                Triple(path, null, null)
            }
            if (path.contains(":mod/") && modIndex == null) continue

            val param = ParameterResolver.findParameterByPath(mixer, baseParamPath) ?: continue
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
                    isCrossfade = (baseParamPath == "Mixer/crossfade" && modIndex == null),
                    modIndex = modIndex,
                    propertyName = propertyName
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
        if (bindingsDirty) {
            rebuildResolvedBindings(mixer)
        }

        // Dispatch to Macro mappings
        for ((path, mapping) in activeProfile.mappings) {
            if (!path.startsWith("Macro/")) continue
            if (mapping.channel != event.channel || mapping.cc != event.index) continue
            if (mapping.messageType != event.type && !(mapping.messageType == MidiMessageType.CC && event.type == MidiMessageType.PITCH_BEND)) continue

            // "Macro/<bankId>/knob_N"
            val rest = path.removePrefix("Macro/")
            val slashIdx = rest.indexOf('/')
            if (slashIdx < 0) continue
            val bankId = rest.substring(0, slashIdx)
            val slot = rest.substring(slashIdx + 1)
            val bank = llm.slop.liquidlsd.macro.MacroEngine.getBank(bankId) ?: continue
            if (slot.startsWith("knob_")) {
                val idx = slot.removePrefix("knob_").toIntOrNull()?.minus(1) ?: continue
                val knob = bank.knobs.getOrNull(idx) ?: continue
                when (mapping.inputType) {
                    MidiInputType.ROTARY_BINARY_OFFSET,
                    MidiInputType.ROTARY_SIGNED_BIT,
                    MidiInputType.ROTARY_TWOS_COMP -> {
                        val deltaSteps = decodeRotaryDelta(mapping.inputType, event.rawValue)
                        if (deltaSteps != 0) {
                            val sign = if (mapping.inverted) -1f else 1f
                            val deltaVal = deltaSteps * mapping.stepSize * sign
                            knob.value = (knob.value + deltaVal).coerceIn(0f, 1f)
                        }
                    }
                    else -> {
                        val rawNorm = if (mapping.inverted) 1f - event.normalizedValue else event.normalizedValue
                        knob.value = rawNorm.coerceIn(0f, 1f)
                    }
                }
            }
        }

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
                        val newVal = (b.getCurrentValue() + deltaVal).coerceIn(b.minVal, b.maxVal)
                        b.applyValue(newVal)
                        b.targetValue = newVal
                        b.hasTarget = true
                        b.smoothedValue = newVal
                        b.hasSmoothed = true
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
                            b.applyValue(newVal)
                            b.targetValue = newVal
                            b.hasTarget = true
                            b.smoothedValue = newVal
                            b.hasSmoothed = true
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
                                b.applyValue(newVal)
                                b.targetValue = newVal
                                b.hasTarget = true
                                b.smoothedValue = newVal
                                b.hasSmoothed = true
                                if (b.isCrossfade) mixer.onCrossfadeManualTakeover()
                            }
                        }
                        TriggerMode.STEP_INCREMENT -> {
                            if (isHigh && !prevHigh) {
                                val step = b.stepSize * (b.maxVal - b.minVal) * (if (b.inverted) -1f else 1f)
                                val newVal = (b.getCurrentValue() + step).coerceIn(b.minVal, b.maxVal)
                                b.applyValue(newVal)
                                b.targetValue = newVal
                                b.hasTarget = true
                                b.smoothedValue = newVal
                                b.hasSmoothed = true
                                if (b.isCrossfade) mixer.onCrossfadeManualTakeover()
                            }
                        }
                        TriggerMode.STEP_DECREMENT -> {
                            if (isHigh && !prevHigh) {
                                val step = b.stepSize * (b.maxVal - b.minVal) * (if (b.inverted) 1f else -1f)
                                val newVal = (b.getCurrentValue() + step).coerceIn(b.minVal, b.maxVal)
                                b.applyValue(newVal)
                                b.targetValue = newVal
                                b.hasTarget = true
                                b.smoothedValue = newVal
                                b.hasSmoothed = true
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
                            val currentVal = b.getCurrentValue()
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
                        b.targetValue = scaledTarget
                        b.hasTarget = true
                        if (b.slewMs <= 0f) {
                            b.applyValue(scaledTarget)
                            b.smoothedValue = scaledTarget
                            b.hasSmoothed = true
                        }
                        if (b.isCrossfade) mixer.onCrossfadeManualTakeover()
                    }
                }
            }
        }
    }

    /** Queue-navigation deltas accumulated from global MIDI CC actions this frame. */
    data class GlobalMidiDeltas(val queueDelta: Int, val bgQueueDelta: Int, val transQueueDelta: Int = 0)

    /**
     * Drains all MIDI events queued by the MIDI receiver thread since the last frame,
     * dispatching each one to either MIDI-learn (if a learn target is active), the fixed
     * set of global actions (queue next/prev, bg-queue next/prev, tap tempo), or the
     * regular parameter bindings via [onMidiEvent].
     *
     * Returns the net queue-navigation deltas produced by global-action CC edges this frame;
     * the caller combines these with CV/keyboard deltas to decide whether to advance the queue.
     */
    fun processGlobalMidiEvents(
        midiEnabled: Boolean,
        parametersState: ParametersState,
        mixer: Mixer,
        onTapTempo: () -> Unit
    ): GlobalMidiDeltas {
        var midiCcDelta = 0
        var bgMidiCcDelta = 0
        var transMidiCcDelta = 0

        if (!midiEnabled) {
            MidiEngine.receivedEvents.clear()
            MidiEngine.receivedCcEvents.clear()
            return GlobalMidiDeltas(0, 0, 0)
        }

        // Check for MIDI learn auto-timeout (15 seconds)
        if (parametersState.midiLearnTarget != null && System.currentTimeMillis() - parametersState.midiLearnStartTimeMs > 15000L) {
            parametersState.midiLearnTarget = null
        }

        while (true) {
            val event = MidiEngine.receivedEvents.poll() ?: break
            val target = parametersState.midiLearnTarget
            if (target != null) {
                val inputType = when (event.type) {
                    MidiMessageType.NOTE -> MidiInputType.BUTTON_NOTE
                    MidiMessageType.PITCH_BEND -> MidiInputType.PITCH_BEND
                    MidiMessageType.CC -> when {
                        event.rawValue == 63 || event.rawValue == 65 -> MidiInputType.ROTARY_BINARY_OFFSET
                        else -> MidiInputType.CONTINUOUS_CC
                    }
                }
                val triggerMode = if (event.type == MidiMessageType.NOTE) TriggerMode.MOMENTARY else TriggerMode.TOGGLE

                when (target) {
                    is MidiLearnTarget.BaseValueSlider -> {
                        addMapping(
                            parameterPath = target.paramKey,
                            cc = event.index,
                            channel = event.channel,
                            minVal = target.min,
                            maxVal = target.max,
                            messageType = event.type,
                            inputType = inputType,
                            triggerMode = triggerMode
                        )
                        saveActiveProfile()
                    }
                    is MidiLearnTarget.GridCell -> {
                        val midiId = if (event.type == MidiMessageType.NOTE) {
                            "midi_note_${event.channel}_${event.index}"
                        } else {
                            "midi_cc_${event.channel}_${event.index}"
                        }
                        val existingMods = target.param.modulators.filter { it.sourceId.startsWith("midi_cc_") || it.sourceId.startsWith("midi_note_") }
                        target.param.modulators.removeAll(existingMods)
                        val exists = target.param.modulators.any { it.sourceId == midiId }
                        if (!exists) {
                            target.param.modulators.add(
                                CvModulator(
                                    sourceId = midiId,
                                    depth = 1.0f,
                                    operator = ModulationOperator.ADD
                                )
                            )
                        }
                    }
                    is MidiLearnTarget.GlobalAction -> {
                        addMapping(
                            parameterPath = target.actionKey,
                            cc = event.index,
                            channel = event.channel,
                            minVal = 0f,
                            maxVal = 1f,
                            messageType = event.type,
                            inputType = inputType,
                            triggerMode = TriggerMode.TOGGLE
                        )
                        saveActiveProfile()
                    }
                    is MidiLearnTarget.ModulatorProperty -> {
                        addMapping(
                            parameterPath = target.fullPath,
                            cc = event.index,
                            channel = event.channel,
                            minVal = target.min,
                            maxVal = target.max,
                            messageType = event.type,
                            inputType = inputType,
                            triggerMode = triggerMode
                        )
                        saveActiveProfile()
                    }
                    is MidiLearnTarget.MacroTarget -> {
                        // Performance Console's FX row knobs get re-synced to a new target every
                        // time the focused bank/chain changes (see FxMacroSync); an absolute CC
                        // learned onto one of them defaults to soft-takeover so re-focusing never
                        // yanks the value on the next physical touch. Relative encoders have no
                        // jump problem by construction, so they're left at IMMEDIATE regardless.
                        val takeoverMode = if (inputType == MidiInputType.CONTINUOUS_CC && isFxMacroSyncOwnedKnobPath(target.macroPath)) {
                            TakeoverMode.SOFT_TAKEOVER
                        } else {
                            TakeoverMode.IMMEDIATE
                        }
                        addMapping(
                            parameterPath = target.macroPath,
                            cc = event.index,
                            channel = event.channel,
                            minVal = 0f,
                            maxVal = 1f,
                            messageType = event.type,
                            inputType = inputType,
                            triggerMode = triggerMode,
                            takeoverMode = takeoverMode
                        )
                        saveActiveProfile()
                    }
                }
                parametersState.midiLearnTarget = null
            } else {
                // Global Actions
                val nextCc = getCcForSpecial("Global/queueNext")
                val nextCh = getChannelForSpecial("Global/queueNext")
                if (nextCc != -1 && event.index == nextCc && event.channel == nextCh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastNextMidiCcHigh) {
                        midiCcDelta += 1
                    }
                    lastNextMidiCcHigh = isHigh
                }
                val prevCc = getCcForSpecial("Global/queuePrev")
                val prevCh = getChannelForSpecial("Global/queuePrev")
                if (prevCc != -1 && event.index == prevCc && event.channel == prevCh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastPrevMidiCcHigh) {
                        midiCcDelta -= 1
                    }
                    lastPrevMidiCcHigh = isHigh
                }
                val bgNextCc = getCcForSpecial("Global/bgQueueNext")
                val bgNextCh = getChannelForSpecial("Global/bgQueueNext")
                if (bgNextCc != -1 && event.index == bgNextCc && event.channel == bgNextCh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastBgNextMidiCcHigh) {
                        bgMidiCcDelta += 1
                    }
                    lastBgNextMidiCcHigh = isHigh
                }
                val bgPrevCc = getCcForSpecial("Global/bgQueuePrev")
                val bgPrevCh = getChannelForSpecial("Global/bgQueuePrev")
                if (bgPrevCc != -1 && event.index == bgPrevCc && event.channel == bgPrevCh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastBgPrevMidiCcHigh) {
                        bgMidiCcDelta -= 1
                    }
                    lastBgPrevMidiCcHigh = isHigh
                }
                val transNextCc = getCcForSpecial("Global/transQueueNext")
                val transNextCh = getChannelForSpecial("Global/transQueueNext")
                if (transNextCc != -1 && event.index == transNextCc && event.channel == transNextCh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastTransNextMidiCcHigh) {
                        transMidiCcDelta += 1
                    }
                    lastTransNextMidiCcHigh = isHigh
                }
                val transPrevCc = getCcForSpecial("Global/transQueuePrev")
                val transPrevCh = getChannelForSpecial("Global/transQueuePrev")
                if (transPrevCc != -1 && event.index == transPrevCc && event.channel == transPrevCh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastTransPrevMidiCcHigh) {
                        transMidiCcDelta -= 1
                    }
                    lastTransPrevMidiCcHigh = isHigh
                }
                val tapCc = getCcForSpecial("Global/tapTempo")
                val tapCh = getChannelForSpecial("Global/tapTempo")
                if (tapCc != -1 && event.index == tapCc && event.channel == tapCh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastTapMidiCcHigh) {
                        onTapTempo()
                    }
                    lastTapMidiCcHigh = isHigh
                }

                val autoFadeCc = getCcForSpecial("Global/autoFade")
                val autoFadeCh = getChannelForSpecial("Global/autoFade")
                if (autoFadeCc != -1 && event.index == autoFadeCc && event.channel == autoFadeCh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastAutoFadeMidiCcHigh) {
                        if (mixer.isAutoFading) {
                            mixer.onCrossfadeManualTakeover()
                        } else {
                            val targetIsA = mixer.crossfade.baseValue > 0.0f
                            mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
                            mixer.isAutoFading = true
                            mixer.muteCrossfadeNonMidiCv()
                        }
                    }
                    lastAutoFadeMidiCcHigh = isHigh
                }

                val snapACc = getCcForSpecial("Global/snapDeckA")
                val snapACh = getChannelForSpecial("Global/snapDeckA")
                if (snapACc != -1 && event.index == snapACc && event.channel == snapACh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastSnapAMidiCcHigh) {
                        mixer.onCrossfadeManualTakeover()
                        mixer.crossfade.set(-1.0f)
                    }
                    lastSnapAMidiCcHigh = isHigh
                }

                val snapBCc = getCcForSpecial("Global/snapDeckB")
                val snapBCh = getChannelForSpecial("Global/snapDeckB")
                if (snapBCc != -1 && event.index == snapBCc && event.channel == snapBCh) {
                    val isHigh = event.normalizedValue > 0.5f
                    if (isHigh && !lastSnapBMidiCcHigh) {
                        mixer.onCrossfadeManualTakeover()
                        mixer.crossfade.set(1.0f)
                    }
                    lastSnapBMidiCcHigh = isHigh
                }

                // Forward to parameter bindings (rotary deltas, buttons, continuous takeover)
                onMidiEvent(event, mixer)
            }
        }
        MidiEngine.receivedCcEvents.clear()

        return GlobalMidiDeltas(midiCcDelta, bgMidiCcDelta, transMidiCcDelta)
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
            if (!b.hasTarget) continue
            val target = b.targetValue

            if (b.slewMs > 0f) {
                val current = if (b.hasSmoothed) b.smoothedValue else b.getCurrentValue()
                val tau = (b.slewMs / 1000f).coerceAtLeast(0.001f)
                val alpha = (1.0f - kotlin.math.exp(-dtSeconds / tau)).coerceIn(0.01f, 1.0f)
                val nextVal = current + (target - current) * alpha
                b.smoothedValue = nextVal
                b.hasSmoothed = true
                b.applyValue(nextVal)
            }
        }
    }
}

