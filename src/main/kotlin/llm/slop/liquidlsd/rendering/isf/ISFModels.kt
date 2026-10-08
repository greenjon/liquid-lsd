package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val logger = mu.KotlinLogging.logger {}

enum class ShaderFormat {
    ISF,
    SHADERTOY,
    GLSL_SANDBOX
}

@Serializable
data class ISFInput(
    val NAME: String,
    val TYPE: String,
    val DEFAULT: JsonElement? = null,
    val MIN: JsonElement? = null,
    val MAX: JsonElement? = null,
    val LABEL: String? = null,
    val VALUES: List<JsonElement>? = null,
    val LABELS: List<String>? = null,
    /** Optional ISF-spec "neutral state" value (e.g. blur radius 0.0, opacity 1.0) used by [llm.slop.liquidlsd.rendering.isf.ISFAutoBindEngine]'s auto-bind heuristic. */
    val IDENTITY: JsonElement? = null,
    /**
     * Liquid LSD extension (not in the ISF spec; other ISF hosts ignore it): marks a `float` input as discrete, with values
     * MIN, MIN+STEP, ... MAX. The shader keeps receiving a float uniform.
     */
    val STEP: JsonElement? = null
) {
    /**
     * Number of discrete values for a `long`/`int` input (VALUES count when present, else the whole-number MIN..MAX span),
     * 2 for `bool`, or `(MAX-MIN)/STEP + 1` for a `float` with a [STEP]; null for continuous inputs or spans that are not whole.
     */
    fun discreteSteps(min: Float, max: Float): Int? = when (TYPE.lowercase()) {
        "bool" -> 2
        "long", "int" -> {
            val byValues = VALUES?.size?.takeIf { it >= 2 }
            val span = max - min
            val bySpan = if (span >= 1f && span == Math.round(span).toFloat()) Math.round(span) + 1 else null
            byValues ?: bySpan
        }
        "float" -> {
            val step = (STEP as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
            if (step == null) null
            else {
                val n = (max - min) / step
                if (step > 0f && n >= 1f && kotlin.math.abs(n - Math.round(n)) < 1e-3f) Math.round(n) + 1
                else {
                    logger.warn { "ISF input '$NAME': STEP $step does not divide MIN..MAX ($min..$max) into whole steps; ignored" }
                    null
                }
            }
        }
        else -> null
    }

    /** LABELS only when there is one per step. */
    fun labelsFor(steps: Int?): List<String>? = LABELS?.takeIf { steps != null && it.size == steps }
}

@Serializable
data class ISFPass(
    val TARGET: String? = null,
    val WIDTH: String? = null,
    val HEIGHT: String? = null,
    val PERSISTENT: Boolean = false,
    val FLOAT: Boolean = false
)

data class ISFImportedAsset(
    val name: String,
    val path: String
)

@Serializable
data class ISFHeader(
    val DESCRIPTION: String? = null,
    val CREDIT: String? = null,
    val CATEGORIES: List<String>? = null,
    val INPUTS: List<ISFInput> = emptyList(),
    val PASSES: List<ISFPass> = emptyList(),
    val IMPORTED: JsonElement? = null,
    val is3D: Boolean = false,
    val feedback: Boolean = false
) {
    /**
     * Parses declared static imported asset images (LUTs, noise maps, audio textures)
     * from IMPORTED, supporting both JSON Object and JSON Array specifications.
     */
    fun getImportedAssets(): List<ISFImportedAsset> {
        val element = IMPORTED ?: return emptyList()
        val list = mutableListOf<ISFImportedAsset>()
        try {
            if (element is JsonObject) {
                for ((key, value) in element) {
                    val path = when (value) {
                        is JsonObject -> (value["PATH"] as? JsonPrimitive)?.contentOrNull
                        is JsonPrimitive -> value.contentOrNull
                        else -> null
                    }
                    if (!path.isNullOrBlank()) {
                        list.add(ISFImportedAsset(name = key, path = path))
                    }
                }
            } else if (element is JsonArray) {
                for (item in element) {
                    if (item is JsonObject) {
                        val name = (item["NAME"] as? JsonPrimitive)?.contentOrNull
                        val path = (item["PATH"] as? JsonPrimitive)?.contentOrNull
                        if (!name.isNullOrBlank() && !path.isNullOrBlank()) {
                            list.add(ISFImportedAsset(name = name, path = path))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore malformed imported blocks
        }
        return list
    }
}

