package llm.slop.liquidlsd.rendering.isf

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ISFInput(
    val NAME: String,
    val TYPE: String,
    val DEFAULT: JsonElement? = null,
    val MIN: JsonElement? = null,
    val MAX: JsonElement? = null,
    val LABEL: String? = null,
    val VALUES: List<JsonElement>? = null,
    val LABELS: List<String>? = null
)

@Serializable
data class ISFPass(
    val TARGET: String? = null,
    val WIDTH: String? = null,
    val HEIGHT: String? = null,
    val PERSISTENT: Boolean = false,
    val FLOAT: Boolean = false
)

@Serializable
data class ISFHeader(
    val DESCRIPTION: String? = null,
    val CREDIT: String? = null,
    val CATEGORIES: List<String>? = null,
    val INPUTS: List<ISFInput> = emptyList(),
    val PASSES: List<ISFPass> = emptyList(),
    val is3D: Boolean = false,
    val feedback: Boolean = false
)
