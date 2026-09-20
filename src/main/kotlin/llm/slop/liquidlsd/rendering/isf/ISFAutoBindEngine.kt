package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Resolves a sensible default Metaknob binding for any ISF filter so that loading one of the
 * thousands of third-party shaders never leaves the user with a dead/unbound macro knob.
 *
 * Resolution priority, most-specific wins (note this is the reverse of the tier numbering below,
 * which reflects the original design doc's authoring order, not the order these are checked in):
 *   1. User override cache, keyed by a content hash of the shader source (Tier 3)
 *   2. Hand-curated bindings for bundled/core filters (Tier 1)
 *   3. Automated heuristic over the shader's declared INPUTS (Tier 2)
 */
object ISFAutoBindEngine {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val overridesDir = File("library/isf_overrides")
    private val overrideCache = ConcurrentHashMap<String, FxMetaBinding>()

    // Semantic name candidates in priority order — first match on a float input wins.
    private val SEMANTIC_CANDIDATES = listOf(
        "amount", "intensity", "level", "mix", "strength", "drywet",
        "depth", "feedback", "rate", "speed", "frequency", "decay"
    )

    // Time/frequency-like names default to an exponential curve instead of linear.
    private val EXPONENTIAL_NAMES = setOf("rate", "speed", "frequency", "decay", "feedback")

    // Hand-curated bindings for ISFFilterRegistry.bundledFilters. First pass at sensible
    // defaults based on each filter's actual declared inputs — expected to be refined by ear
    // over time, not the final word.
    private val CURATED: Map<String, FxMetaBinding> = mapOf(
        "invert" to FxMetaBinding("invertIntensity", 0f, 1f),
        "hue_shift" to FxMetaBinding("hueShift", 0f, 1f),
        // Knob 0 = many levels (subtle quantization), knob 1 = few levels (strong posterization)
        "posterize" to FxMetaBinding("levels", 2f, 32f, invert = true),
        "luma_key" to FxMetaBinding("threshold", 0f, 1f),
        "edge_detect" to FxMetaBinding("edgeStrength", 0f, 5f),
        "bloom" to FxMetaBinding("bloomIntensity", 0f, 3f),
        "feedback_trails" to FxMetaBinding("trailDecay", 0f, 0.99f, curve = MetaCurve.EXPONENTIAL),
        "feedback" to FxMetaBinding("fbDecay", 0f, 1f, curve = MetaCurve.EXPONENTIAL),
        "3d_elevation" to FxMetaBinding("separation", 0f, 2f),
        "glitch" to FxMetaBinding("glitchAmount", 0f, 1f),
        "mirror" to FxMetaBinding("symmetryMode", 0f, 2f)
    )

    /** Resolves the Metaknob binding for [filter]: user override, then curated, then heuristic. */
    fun resolveBinding(filter: ISFFilter): FxMetaBinding {
        filter.contentHash?.let { hash -> loadOverride(hash)?.let { return it } }
        CURATED[filter.id]?.let { return it }
        return resolveHeuristic(filter)
    }

    private fun resolveHeuristic(filter: ISFFilter): FxMetaBinding {
        val floatInputs = filter.header.INPUTS.filter { it.TYPE.equals("float", ignoreCase = true) }
        if (floatInputs.isEmpty()) return FxMetaBinding.DRY_WET_SAFETY_NET

        // Step 1: IDENTITY check
        for (input in floatInputs) {
            val identity = input.IDENTITY?.toString()?.toFloatOrNull() ?: continue
            val param = filter.parameters[input.NAME] ?: continue
            val min = param.minClamp
            val max = param.maxClamp
            val binding = when {
                approxEquals(identity, min) -> FxMetaBinding(input.NAME, min, max)
                approxEquals(identity, max) -> FxMetaBinding(input.NAME, min, max, invert = true)
                (max - identity) >= (identity - min) -> FxMetaBinding(input.NAME, identity, max)
                else -> FxMetaBinding(input.NAME, identity, min)
            }
            return withExponentialIfNamed(input.NAME, binding)
        }

        // Step 2: semantic name matching, sweeping the input's own MIN..MAX
        for (candidate in SEMANTIC_CANDIDATES) {
            val input = floatInputs.firstOrNull { it.NAME.equals(candidate, ignoreCase = true) } ?: continue
            val param = filter.parameters[input.NAME] ?: continue
            return withExponentialIfNamed(candidate, FxMetaBinding(input.NAME, param.minClamp, param.maxClamp))
        }

        // Step 3: single normalized float fallback
        if (floatInputs.size == 1) {
            val input = floatInputs[0]
            val param = filter.parameters[input.NAME] ?: return FxMetaBinding.DRY_WET_SAFETY_NET
            return FxMetaBinding(input.NAME, param.minClamp, param.maxClamp)
        }
        val normalized = floatInputs.firstOrNull { input ->
            val param = filter.parameters[input.NAME]
            param != null && approxEquals(param.minClamp, 0f) && approxEquals(param.maxClamp, 1f)
        }
        if (normalized != null) {
            val param = filter.parameters[normalized.NAME]!!
            return FxMetaBinding(normalized.NAME, param.minClamp, param.maxClamp)
        }
        // Step 3b: multiple floats, none normalized to [0,1] — fall back to the first-declared one
        val first = floatInputs[0]
        val param = filter.parameters[first.NAME] ?: return FxMetaBinding.DRY_WET_SAFETY_NET
        return FxMetaBinding(first.NAME, param.minClamp, param.maxClamp)
    }

    private fun withExponentialIfNamed(name: String, binding: FxMetaBinding): FxMetaBinding =
        if (EXPONENTIAL_NAMES.any { it.equals(name, ignoreCase = true) }) binding.copy(curve = MetaCurve.EXPONENTIAL) else binding

    private fun approxEquals(a: Float, b: Float, tolerance: Float = 0.0001f): Boolean = kotlin.math.abs(a - b) <= tolerance

    /** Stable content hash of a shader's raw source — used to key overrides across path/pack moves. */
    fun contentHash(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun loadOverride(hash: String): FxMetaBinding? {
        overrideCache[hash]?.let { return it }
        val file = File(overridesDir, "$hash.json")
        if (!file.exists()) return null
        return try {
            val binding = json.decodeFromString(FxMetaBindingDto.serializer(), file.readText()).toBinding()
            overrideCache[hash] = binding
            binding
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read ISF Metaknob override: ${file.path}" }
            null
        }
    }

    /** Persists a user-customized binding keyed by shader content hash, so it applies wherever that shader loads next. */
    fun saveOverride(contentHash: String, binding: FxMetaBinding) {
        try {
            if (!overridesDir.exists()) overridesDir.mkdirs()
            File(overridesDir, "$contentHash.json").writeText(json.encodeToString(FxMetaBindingDto.serializer(), binding.toDto()))
            overrideCache[contentHash] = binding
        } catch (e: Exception) {
            logger.warn(e) { "Failed to save ISF Metaknob override for hash $contentHash" }
        }
    }

    /** Removes a user override, reverting that shader's Metaknob to curated/heuristic resolution. */
    fun deleteOverride(contentHash: String) {
        overrideCache.remove(contentHash)
        val file = File(overridesDir, "$contentHash.json")
        if (file.exists()) file.delete()
    }
}
