package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    val IDENTITY: JsonElement? = null
)

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

