package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.utils.TimeSource
import kotlinx.serialization.json.*
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

    private sealed class ISFInputBinding {
        abstract fun apply(shader: Shader)

        class FloatInput(val name: String, val param: ModulatableParameter?) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, param?.value ?: 0f)
            }
        }

        class BoolInput(val name: String, val param: ModulatableParameter?) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, if ((param?.value ?: 0f) > 0.5f) 1.0f else 0.0f)
            }
        }

        class LongInput(val name: String, val param: ModulatableParameter?) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, param?.value ?: 0f)
            }
        }

        class ColorInput(
            val name: String,
            val r: ModulatableParameter?,
            val g: ModulatableParameter?,
            val b: ModulatableParameter?,
            val a: ModulatableParameter?
        ) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, r?.value ?: 0f, g?.value ?: 0f, b?.value ?: 0f, a?.value ?: 1f)
            }
        }

        class Point2DInput(
            val name: String,
            val x: ModulatableParameter?,
            val y: ModulatableParameter?
        ) : ISFInputBinding() {
            override fun apply(shader: Shader) {
                shader.setUniform(name, x?.value ?: 0f, y?.value ?: 0f)
            }
        }
    }

    private val inputBindings: Array<ISFInputBinding> = header.INPUTS.mapNotNull { input ->
        when (input.TYPE) {
            "float" -> ISFInputBinding.FloatInput(input.NAME, parameters[input.NAME])
            "bool" -> ISFInputBinding.BoolInput(input.NAME, parameters[input.NAME])
            "long" -> ISFInputBinding.LongInput(input.NAME, parameters[input.NAME])
            "color" -> ISFInputBinding.ColorInput(
                input.NAME,
                parameters["${input.NAME} R"],
                parameters["${input.NAME} G"],
                parameters["${input.NAME} B"],
                parameters["${input.NAME} A"]
            )
            "point2D" -> ISFInputBinding.Point2DInput(
                input.NAME,
                parameters["${input.NAME} X"],
                parameters["${input.NAME} Y"]
            )
            else -> null
        }
    }.toTypedArray()

    override fun setupUniforms(shader: Shader) {
        // 1. ISF standard uniforms
        val currentTime = TimeSource.getTimeSec().toFloat()
        val deltaTime = currentTime - lastTime
        lastTime = currentTime
        
        shader.setUniform("TIME", currentTime)
        shader.setUniform("TIMEDELTA", deltaTime)
        shader.setUniform("FRAMEINDEX", frameIndex++)
        
        // Derive DATE components via epoch arithmetic — zero allocation (no LocalDateTime/LocalTime objects)
        val epochMs = System.currentTimeMillis()
        val epochSec = epochMs / 1000L
        val secondsSinceMidnight = (epochSec % 86400L).toFloat() + ((epochMs % 1000L) / 1000f)
        // Gregorian calendar from epoch seconds (proleptic, valid well past 2100)
        val daysSinceEpoch = (epochSec / 86400L).toInt()
        val year400 = daysSinceEpoch / 146097; val rem400 = daysSinceEpoch % 146097
        val year100 = minOf(rem400 / 36524, 3); val rem100 = rem400 - year100 * 36524
        val year4   = rem100 / 1461;             val rem4   = rem100 % 1461
        val year1   = minOf(rem4 / 365, 3);      val rem1   = rem4 - year1 * 365
        val yearNum = 1970 + year400 * 400 + year100 * 100 + year4 * 4 + year1
        val isLeap  = (yearNum % 4 == 0 && yearNum % 100 != 0) || yearNum % 400 == 0
        val monthStarts = if (isLeap) MONTH_STARTS_LEAP else MONTH_STARTS_NORMAL
        var monthNum = 11
        for (m in 0..10) { if (rem1 < monthStarts[m + 1]) { monthNum = m; break } }
        shader.setUniform("DATE", yearNum.toFloat(), (monthNum + 1).toFloat(), (rem1 - monthStarts[monthNum] + 1).toFloat(), secondsSinceMidnight)
        
        // 2. ISF Input uniforms — pre-bound direct references, zero allocations per frame
        for (i in 0 until inputBindings.size) {
            inputBindings[i].apply(shader)
        }
    }

    companion object {
        // Day-of-year offset for the 1st of each month (index 0=Jan … 11=Dec), plus sentinel at [12]
        // Used in setupUniforms() so the DATE computation is allocation-free on the render thread.
        private val MONTH_STARTS_NORMAL = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334,365)
        private val MONTH_STARTS_LEAP   = intArrayOf(0,31,60,91,121,152,182,213,244,274,305,335,366)

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
