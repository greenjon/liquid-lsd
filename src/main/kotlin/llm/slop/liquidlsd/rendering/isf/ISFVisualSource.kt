package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.utils.TimeSource
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

class ISFVisualSource(
    id: String,
    displayName: String,
    shader: Shader,
    val header: ISFHeader,
    parameters: LinkedHashMap<String, ModulatableParameter>,
    hasFeedback: Boolean = false,
    ownsShader: Boolean = false,
    is3D: Boolean = header.is3D || (parameters.containsKey("Rotate X") && parameters.containsKey("Rotate Y")),
    override val categories: List<String> = header.CATEGORIES ?: emptyList()
) : DynamicVisualSource(id, displayName, shader, parameters, hasFeedback = hasFeedback, ownsShader = ownsShader, is3D = is3D, categories = categories) {

    private var frameIndex = 0
    private var lastTime = TimeSource.getTimeSec().toFloat()

    override fun setupUniforms(shader: Shader) {
        // 1. ISF standard uniforms
        val currentTime = TimeSource.getTimeSec().toFloat()
        val deltaTime = currentTime - lastTime
        lastTime = currentTime
        
        shader.setUniform("TIME", currentTime)
        shader.setUniform("TIMEDELTA", deltaTime)
        shader.setUniform("FRAMEINDEX", frameIndex++)
        
        val now = LocalDateTime.now()
        val secondsSinceMidnight = LocalTime.now().toSecondOfDay().toFloat() + (now.nano / 1_000_000_000f)
        shader.setUniform("DATE", now.year.toFloat(), now.monthValue.toFloat(), now.dayOfMonth.toFloat(), secondsSinceMidnight)
        
        // 2. ISF Input uniforms
        header.INPUTS.forEach { input ->
            when (input.TYPE) {
                "float" -> {
                    shader.setUniform(input.NAME, parameters[input.NAME]?.value ?: 0f)
                }
                "bool" -> {
                    shader.setUniform(input.NAME, if ((parameters[input.NAME]?.value ?: 0f) > 0.5f) 1.0f else 0.0f)
                }
                "long" -> {
                    shader.setUniform(input.NAME, parameters[input.NAME]?.value ?: 0f)
                }
                "color" -> {
                    val r = parameters["${input.NAME} R"]?.value ?: 0f
                    val g = parameters["${input.NAME} G"]?.value ?: 0f
                    val b = parameters["${input.NAME} B"]?.value ?: 0f
                    val a = parameters["${input.NAME} A"]?.value ?: 1f
                    shader.setUniform(input.NAME, r, g, b, a)
                }
                "point2D" -> {
                    val x = parameters["${input.NAME} X"]?.value ?: 0f
                    val y = parameters["${input.NAME} Y"]?.value ?: 0f
                    shader.setUniform(input.NAME, x, y)
                }
            }
        }
    }

    companion object {
        fun createParameters(header: ISFHeader): LinkedHashMap<String, ModulatableParameter> {
            val params = LinkedHashMap<String, ModulatableParameter>()
            header.INPUTS.forEach { input ->
                when (input.TYPE) {
                    "float", "long" -> {
                        val default = (input.DEFAULT as? JsonPrimitive)?.floatOrNull ?: 0.5f
                        val min = (input.MIN as? JsonPrimitive)?.floatOrNull ?: 0.0f
                        val max = (input.MAX as? JsonPrimitive)?.floatOrNull ?: 1.0f
                        params[input.NAME] = ModulatableParameter(
                            baseValue = default,
                            minClamp = min,
                            maxClamp = max
                        )
                    }
                    "bool" -> {
                        val default = (input.DEFAULT as? JsonPrimitive)?.booleanOrNull ?: false
                        params[input.NAME] = ModulatableParameter(
                            baseValue = if (default) 1.0f else 0.0f,
                            minClamp = 0.0f,
                            maxClamp = 1.0f
                        )
                    }
                    "color" -> {
                        val defArray = input.DEFAULT as? JsonArray
                        val r = (defArray?.getOrNull(0) as? JsonPrimitive)?.floatOrNull ?: 1f
                        val g = (defArray?.getOrNull(1) as? JsonPrimitive)?.floatOrNull ?: 1f
                        val b = (defArray?.getOrNull(2) as? JsonPrimitive)?.floatOrNull ?: 1f
                        val a = (defArray?.getOrNull(3) as? JsonPrimitive)?.floatOrNull ?: 1f
                        
                        params["${input.NAME} R"] = ModulatableParameter(r, minClamp = 0f, maxClamp = 1f)
                        params["${input.NAME} G"] = ModulatableParameter(g, minClamp = 0f, maxClamp = 1f)
                        params["${input.NAME} B"] = ModulatableParameter(b, minClamp = 0f, maxClamp = 1f)
                        params["${input.NAME} A"] = ModulatableParameter(a, minClamp = 0f, maxClamp = 1f)
                    }
                    "point2D" -> {
                        val defArray = input.DEFAULT as? JsonArray
                        val x = (defArray?.getOrNull(0) as? JsonPrimitive)?.floatOrNull ?: 0.5f
                        val y = (defArray?.getOrNull(1) as? JsonPrimitive)?.floatOrNull ?: 0.5f

                        val minArray = input.MIN as? JsonArray
                        val minX = (minArray?.getOrNull(0) as? JsonPrimitive)?.floatOrNull ?: 0f
                        val minY = (minArray?.getOrNull(1) as? JsonPrimitive)?.floatOrNull ?: 0f

                        val maxArray = input.MAX as? JsonArray
                        val maxX = (maxArray?.getOrNull(0) as? JsonPrimitive)?.floatOrNull ?: 1f
                        val maxY = (maxArray?.getOrNull(1) as? JsonPrimitive)?.floatOrNull ?: 1f
                        
                        params["${input.NAME} X"] = ModulatableParameter(x, minClamp = minX, maxClamp = maxX)
                        params["${input.NAME} Y"] = ModulatableParameter(y, minClamp = minY, maxClamp = maxY)
                    }
                }
            }
            return params
        }
    }

    override fun clone(): ISFVisualSource {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        this.parameters.forEach { (name, param) ->
            clonedParams[name] = param.clone()
        }
        return ISFVisualSource(
            id = this.id,
            displayName = this.displayName,
            shader = this.shader,
            header = this.header,
            parameters = clonedParams,
            hasFeedback = this.hasFeedback,
            ownsShader = false,
            is3D = this.is3D
        )
    }
}
