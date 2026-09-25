package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.ui.FileSystemManager
import mu.KotlinLogging
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Serializable DTO representing default Metaknob bindings and parameter values for an ISF filter.
 */
@Serializable
data class FxDefaultDto(
    val version: Int = 2,
    val metaBindings: List<FxMetaBindingDto>,
    val parameters: Map<String, Float> = emptyMap()
)

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
    var overridesDir: File = FileSystemManager.getIsfOverridesRoot()
    private val overrideCache = ConcurrentHashMap<String, FxDefaultDto>()

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
        "luma_key" to FxMetaBinding("threshold", 0f, 1f),
        "bloom" to FxMetaBinding("bloomIntensity", 0f, 3f),
        "feedback" to FxMetaBinding("fbDecay", 0f, 1f, curve = MetaCurve.EXPONENTIAL),
        "3d_elevation" to FxMetaBinding("separation", 0f, 2f),
        "kaleidoscope" to FxMetaBinding("rotation", -3.14159f, 3.14159f),
        "radial_blur" to FxMetaBinding("blurAmount", 0f, 1f),
        "rgb_split" to FxMetaBinding("amount", 0f, 0.1f),
        "polar_tunnel" to FxMetaBinding("depth", 0.1f, 5f),
        "color_levels" to FxMetaBinding("contrast", 0f, 3f),
        "gradient_map" to FxMetaBinding("mixAmount", 0f, 1f),
        "directional_blur" to FxMetaBinding("blurAmount", 0f, 1f),
        "wave_displace" to FxMetaBinding("amplitude", 0f, 0.2f),
        "pixelate" to FxMetaBinding("pixelSize", 1f, 64f),
        "retro_crt" to FxMetaBinding("scanlineIntensity", 0f, 1f),
        "pinch_bulge" to FxMetaBinding("amount", -1f, 1f),
        "neon_edge" to FxMetaBinding("edgeStrength", 0f, 5f),
        "luma_displace" to FxMetaBinding("refractAmount", 0f, 0.25f),
        "video_strobe" to FxMetaBinding("rate", 0f, 20f),
        "fluid_smear" to FxMetaBinding("smearAmount", 0f, 1f),
        "thermal_scanner" to FxMetaBinding("intensity", 0f, 1f),
        "mirror_sphere" to FxMetaBinding("sphereRadius", 0f, 0.8f),
        "halftone" to FxMetaBinding("dotScale", 5f, 150f),
        "anamorphic_streak" to FxMetaBinding("streakIntensity", 0f, 3f),
        "vhs_glitch" to FxMetaBinding("trackingJitter", 0f, 1f),
        "vortex_swirl" to FxMetaBinding("twist", -3f, 3f),
        "faceted_glass" to FxMetaBinding("refraction", 0f, 1f)
    )

    /** Resolves the default bindings and parameters for [filter]: user override, then curated, then heuristic. */
    fun resolveDefault(filter: ISFFilter): FxDefaultDto {
        filter.contentHash?.let { hash -> loadOverride(hash)?.let { return it } }
        CURATED[filter.id]?.let {
            return FxDefaultDto(version = 2, metaBindings = listOf(it.toDto()), parameters = emptyMap())
        }
        val heuristic = resolveHeuristic(filter)
        return FxDefaultDto(version = 2, metaBindings = listOf(heuristic.toDto()), parameters = emptyMap())
    }

    /** Resolves all Metaknob bindings for [filter]. */
    fun resolveBindings(filter: ISFFilter): List<FxMetaBinding> =
        resolveDefault(filter).metaBindings.map { it.toBinding() }

    /** Resolves the primary Metaknob binding for [filter]: user override, then curated, then heuristic. */
    fun resolveBinding(filter: ISFFilter): FxMetaBinding =
        resolveBindings(filter).firstOrNull() ?: FxMetaBinding.DRY_WET_SAFETY_NET

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

    private fun loadOverride(hash: String): FxDefaultDto? {
        overrideCache[hash]?.let { return it }
        val file = File(overridesDir, "$hash.json")
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            val dto = try {
                json.decodeFromString(FxDefaultDto.serializer(), text)
            } catch (_: Exception) {
                // Backwards compatibility: gracefully read legacy single FxMetaBindingDto files
                val legacy = json.decodeFromString(FxMetaBindingDto.serializer(), text)
                FxDefaultDto(version = 1, metaBindings = listOf(legacy), parameters = emptyMap())
            }
            overrideCache[hash] = dto
            dto
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read ISF Metaknob override: ${file.path}" }
            null
        }
    }

    /** Persists a filter's current bindings and parameter baselines as its user default. */
    fun saveFilterDefault(filter: ISFFilter) {
        val hash = filter.contentHash ?: return
        val dto = FxDefaultDto(
            version = 2,
            metaBindings = filter.metaBindings.map { it.toDto() },
            parameters = filter.parameters.mapValues { it.value.baseValue }
        )
        try {
            if (!overridesDir.exists()) overridesDir.mkdirs()
            File(overridesDir, "$hash.json").writeText(json.encodeToString(FxDefaultDto.serializer(), dto))
            overrideCache[hash] = dto
            logger.info { "Saved ISF filter default for ${filter.displayName} (hash: $hash)" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save ISF filter default for hash $hash" }
        }
    }

    /** Removes a filter's user default, reverting it to curated/heuristic defaults. */
    fun deleteFilterDefault(filter: ISFFilter) {
        val hash = filter.contentHash ?: return
        deleteOverride(hash)
    }

    /** Returns whether a user default exists for [filter]. */
    fun hasFilterDefault(filter: ISFFilter): Boolean {
        val hash = filter.contentHash ?: return false
        return overrideCache.containsKey(hash) || File(overridesDir, "$hash.json").exists()
    }

    /** Persists a user-customized binding keyed by shader content hash, so it applies wherever that shader loads next. */
    fun saveOverride(contentHash: String, binding: FxMetaBinding) {
        val dto = FxDefaultDto(version = 2, metaBindings = listOf(binding.toDto()), parameters = emptyMap())
        try {
            if (!overridesDir.exists()) overridesDir.mkdirs()
            File(overridesDir, "$contentHash.json").writeText(json.encodeToString(FxDefaultDto.serializer(), dto))
            overrideCache[contentHash] = dto
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
