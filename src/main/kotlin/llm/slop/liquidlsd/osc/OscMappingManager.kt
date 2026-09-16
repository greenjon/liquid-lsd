package llm.slop.liquidlsd.osc

import llm.slop.liquidlsd.macro.MacroOscBridge
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal fun sanitiseOscProfileName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..") || trimmed.any { it.code == 0 }) {
        throw IllegalArgumentException("Invalid characters in OSC profile name: $name")
    }
    if (!trimmed.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
        throw IllegalArgumentException("OSC profile name must contain only alphanumeric characters, underscores, and hyphens: $name")
    }
    return trimmed
}

internal fun oscProfileFile(oscDir: File, profileName: String): File {
    val safeName = sanitiseOscProfileName(profileName)
    val file = File(oscDir, "$safeName.json")
    val rootPath = oscDir.canonicalFile.toPath()
    val filePath = file.canonicalFile.toPath()
    require(filePath.startsWith(rootPath)) { "OSC profile path escapes library/osc: $profileName" }
    return file
}

@Serializable
enum class OscTakeoverMode {
    IMMEDIATE,       // Immediately jumps to the incoming OSC value
    SOFT_TAKEOVER    // Waits until the incoming value crosses the current parameter value
}

@Serializable
data class OscControlMapping(
    val parameterPath: String = "",
    val minVal: Float = 0f,
    val maxVal: Float = 1f,
    val inverted: Boolean = false,
    val slewMs: Float = 0f,
    val takeoverMode: OscTakeoverMode = OscTakeoverMode.IMMEDIATE
)

@Serializable
data class OscMappingProfile(
    val profileName: String,
    // Keyed by OSC address. Multi-argument vector messages (e.g. TouchOSC "/2/xy") are
    // unpacked and keyed as "<address>/<argIndex>" (e.g. "/2/xy/0", "/2/xy/1").
    val mappings: Map<String, OscControlMapping> = emptyMap()
)

/**
 * Maps inbound OSC addresses to application parameters (min/max clamp, invert, slew,
 * soft takeover), forwards `/macro/knob/N` and `/macro/switch/N` to [MacroOscBridge],
 * unpacks multi-argument vector messages (TouchOSC XY pads) into individually
 * addressable components, and persists mapping profiles as JSON under `library/osc/`.
 */
object OscMappingManager {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val oscDir = File("library/osc")

    var activeProfileName = "default"
        private set

    private var activeProfile = OscMappingProfile("Default Profile")

    private val hasTakenOver = ConcurrentHashMap<String, Boolean>()
    private val lastPhysicalScaled = ConcurrentHashMap<String, Float>()

    private class SlewState {
        var hasTarget = false
        var targetValue = 0f
        var hasSmoothed = false
        var smoothedValue = 0f
    }
    private val slewStates = ConcurrentHashMap<String, SlewState>()

    private var lastUpdateTimeNanos = System.nanoTime()

    private val feedbackListener = MacroOscBridge.MacroFeedbackListener { address, value ->
        OscEngine.sendFloat(address, value)
    }

    init {
        if (!oscDir.exists()) oscDir.mkdirs()
        loadProfile(activeProfileName)
        MacroOscBridge.addListener(feedbackListener)
    }

    fun listProfiles(): List<String> {
        val files = oscDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        val list = files.map { it.nameWithoutExtension }.sorted().toMutableList()
        if (list.isEmpty()) list.add("default")
        return list
    }

    fun loadProfile(profileName: String) {
        val safeProfileName = try { sanitiseOscProfileName(profileName) } catch (e: Exception) { "default" }
        val file = oscProfileFile(oscDir, safeProfileName)
        if (file.exists()) {
            try {
                val content = file.readText()
                activeProfile = json.decodeFromString<OscMappingProfile>(content)
                activeProfileName = safeProfileName
                logger.info { "Loaded OSC mapping profile: $safeProfileName" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load OSC profile: $safeProfileName" }
            }
        } else {
            activeProfile = OscMappingProfile(safeProfileName)
            activeProfileName = safeProfileName
            saveActiveProfile()
        }
        resetRuntimeState()
    }

    fun saveActiveProfile() {
        val file = oscProfileFile(oscDir, activeProfileName)
        try {
            file.writeText(json.encodeToString(activeProfile))
            logger.info { "Saved OSC mapping profile: $activeProfileName" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save OSC profile: $activeProfileName" }
        }
    }

    fun deleteProfile(profileName: String): Boolean {
        if (profileName == "default") return false
        val safeProfileName = try { sanitiseOscProfileName(profileName) } catch (e: Exception) { return false }
        val file = oscProfileFile(oscDir, safeProfileName)
        return try {
            val deleted = file.delete()
            if (deleted && activeProfileName == safeProfileName) loadProfile("default")
            deleted
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete OSC profile: $safeProfileName" }
            false
        }
    }

    fun getMappings(): Map<String, OscControlMapping> = activeProfile.mappings
    fun getMappingForAddress(address: String): OscControlMapping? = activeProfile.mappings[address]
    fun hasMapping(address: String): Boolean = activeProfile.mappings.containsKey(address)

    fun addMapping(address: String, mapping: OscControlMapping) {
        val newMappings = activeProfile.mappings.toMutableMap()
        newMappings[address] = mapping
        activeProfile = activeProfile.copy(mappings = newMappings)
        resetRuntimeState()
    }

    fun updateMapping(address: String, mapping: OscControlMapping) = addMapping(address, mapping)

    fun removeMapping(address: String) {
        val newMappings = activeProfile.mappings.toMutableMap()
        newMappings.remove(address)
        activeProfile = activeProfile.copy(mappings = newMappings)
        resetRuntimeState()
    }

    fun clearAllMappings() {
        activeProfile = activeProfile.copy(mappings = emptyMap())
        resetRuntimeState()
    }

    private fun resetRuntimeState() {
        hasTakenOver.clear()
        lastPhysicalScaled.clear()
        slewStates.clear()
    }

    fun isSoftTakeoverActive(address: String): Boolean = hasTakenOver[address] ?: true
    fun getPhysicalPosition(address: String): Float? = lastPhysicalScaled[address]

    private fun asFloat(arg: Any): Float? = when (arg) {
        is Float -> arg
        is Int -> arg.toFloat()
        else -> null
    }

    /**
     * Dispatches an inbound OSC message. If OSC Learn is armed, the message is consumed
     * to establish a new binding instead of being routed normally. Otherwise, `/macro/knob/N`
     * and `/macro/switch/N` are forwarded straight to [MacroOscBridge]; multi-argument vector
     * messages (e.g. TouchOSC "/2/xy") are unpacked per-component; everything else is
     * resolved against the active mapping profile.
     */
    fun onOscMessage(message: OscMessage, mixer: Mixer) {
        if (OscLearnState.isLearning()) {
            OscLearnState.captureLearnedAddress(message)
            return
        }

        if (message.address.startsWith("/macro/knob/") || message.address.startsWith("/macro/switch/")) {
            val value = message.args.firstNotNullOfOrNull { asFloat(it) } ?: return
            MacroOscBridge.handleOscMessage(message.address, value)
            return
        }

        if (message.args.size > 1) {
            message.args.forEachIndexed { index, arg ->
                val value = asFloat(arg) ?: return@forEachIndexed
                dispatchToParameter("${message.address}/$index", value, mixer)
            }
        } else {
            val value = message.args.firstNotNullOfOrNull { asFloat(it) } ?: return
            dispatchToParameter(message.address, value, mixer)
        }
    }

    private fun dispatchToParameter(key: String, rawValue: Float, mixer: Mixer) {
        val mapping = activeProfile.mappings[key] ?: return
        val param = ParameterResolver.findParameterByPath(mixer, mapping.parameterPath) ?: return

        var norm = rawValue.coerceIn(0f, 1f)
        if (mapping.inverted) norm = 1f - norm
        val scaledTarget = mapping.minVal + norm * (mapping.maxVal - mapping.minVal)

        val prevPhys = lastPhysicalScaled[key]
        lastPhysicalScaled[key] = scaledTarget

        if (mapping.takeoverMode == OscTakeoverMode.SOFT_TAKEOVER) {
            val alreadyTakenOver = hasTakenOver[key] ?: false
            if (!alreadyTakenOver) {
                val currentVal = param.baseValue
                val tolerance = (mapping.maxVal - mapping.minVal) * 0.04f
                val crossed = prevPhys != null && ((prevPhys - currentVal) * (scaledTarget - currentVal) <= 0f)
                val closeEnough = kotlin.math.abs(scaledTarget - currentVal) <= tolerance
                if (crossed || closeEnough) hasTakenOver[key] = true
            }
        } else {
            hasTakenOver[key] = true
        }

        if (hasTakenOver[key] != true) return

        val state = slewStates.getOrPut(key) { SlewState() }
        state.targetValue = scaledTarget
        state.hasTarget = true
        if (mapping.slewMs <= 0f) {
            param.baseValue = scaledTarget
            state.smoothedValue = scaledTarget
            state.hasSmoothed = true
        }
    }

    /** Per-frame slew smoothing convergence for OSC-mapped parameters with `slewMs > 0`. */
    fun update(mixer: Mixer) {
        val nowNanos = System.nanoTime()
        val dtSeconds = ((nowNanos - lastUpdateTimeNanos) / 1_000_000_000.0).toFloat().coerceIn(0.0001f, 0.1f)
        lastUpdateTimeNanos = nowNanos

        for ((key, mapping) in activeProfile.mappings) {
            if (mapping.slewMs <= 0f) continue
            val state = slewStates[key] ?: continue
            if (!state.hasTarget) continue
            val param = ParameterResolver.findParameterByPath(mixer, mapping.parameterPath) ?: continue

            val current = if (state.hasSmoothed) state.smoothedValue else param.baseValue
            val tau = (mapping.slewMs / 1000f).coerceAtLeast(0.001f)
            val alpha = (1.0f - kotlin.math.exp(-dtSeconds / tau)).coerceIn(0.01f, 1.0f)
            val nextVal = current + (state.targetValue - current) * alpha
            state.smoothedValue = nextVal
            state.hasSmoothed = true
            param.baseValue = nextVal
        }
    }
}
