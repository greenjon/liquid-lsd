package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.MeterType
import llm.slop.spirals.parameters.ModulatableParameter
import kotlinx.serialization.Serializable

@Serializable
data class ParamMeta(
    val name: String,
    val default: Float,
    val min: Float,
    val max: Float,
    val type: String = "MONOPOLAR"
)

@Serializable
data class SourceMeta(
    val id: String,
    val name: String,
    val parameters: List<ParamMeta>
)

class DynamicVisualSource(
    val id: String,
    val displayName: String,
    val shader: Shader,
    override val parameters: LinkedHashMap<String, ModulatableParameter>,
    override val globalAlpha: ModulatableParameter = ModulatableParameter(1.0f)
) : VisualSource
