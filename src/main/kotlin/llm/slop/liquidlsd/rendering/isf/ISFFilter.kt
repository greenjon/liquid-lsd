package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Geometry
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.VisualEffect
import llm.slop.liquidlsd.utils.TimeSource
import org.lwjgl.opengl.GL33.*
import java.time.LocalDateTime

class ISFFilter(
    override val id: String,
    override val displayName: String,
    val header: ISFHeader,
    val shader: Shader,
    val ownsShader: Boolean = true
) : VisualEffect {

    override val parameters = mutableMapOf<String, ModulatableParameter>()
    override val dryWet = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    override var enabled = true

    private val inputImageName: String?
    private var frameIndex = 0
    private var lastTime = TimeSource.getTimeSec().toFloat()

    init {
        // Find the primary image input
        inputImageName = header.INPUTS.find { it.TYPE.lowercase() == "image" }?.NAME

        // Create modulatable parameters for non-image inputs
        for (input in header.INPUTS) {
            if (input.TYPE.lowercase() == "image") continue

            val defaultVal = input.DEFAULT?.toString()?.toFloatOrNull() ?: 0.0f
            val minVal = input.MIN?.toString()?.toFloatOrNull() ?: 0.0f
            val maxVal = input.MAX?.toString()?.toFloatOrNull() ?: 1.0f
            
            parameters[input.NAME] = ModulatableParameter(
                baseValue = defaultVal,
                minClamp = minVal,
                maxClamp = maxVal
            )
        }
    }

    override fun update() {
        dryWet.evaluate()
        parameters.values.forEach { it.evaluate() }
    }

    /**
     * Renders the filter reading from [inputTexture] into the currently bound FBO.
     */
    fun render(inputTexture: Int, width: Int, height: Int) {
        shader.bind()

        // Set standard ISF uniforms
        val currentTime = TimeSource.getTimeSec().toFloat()
        val deltaTime = currentTime - lastTime
        lastTime = currentTime

        shader.setUniform("TIME", currentTime)
        shader.setUniform("TIMEDELTA", deltaTime)
        shader.setUniform("FRAMEINDEX", frameIndex++)
        shader.setUniform("RENDERSIZE", width.toFloat(), height.toFloat())
        
        val now = LocalDateTime.now()
        shader.setUniform("DATE", now.year.toFloat(), now.monthValue.toFloat(), now.dayOfMonth.toFloat(), 
            now.hour * 3600f + now.minute * 60f + now.second + now.nano / 1_000_000_000f)

        // Bind input image if present
        if (inputImageName != null) {
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, inputTexture)
            shader.setUniform(inputImageName, 0)
        }

        // Set user parameters
        for (input in header.INPUTS) {
            val param = parameters[input.NAME] ?: continue
            val type = input.TYPE.lowercase()
            
            when (type) {
                "float", "long", "bool" -> shader.setUniform(input.NAME, param.value)
                "point2d" -> shader.setUniform(input.NAME, param.value, 0f) // Simplified, assuming single float param for point2D if not complex
                "color" -> shader.setUniform(input.NAME, param.value, param.value, param.value, 1.0f) // Simplified grayscale for now
            }
        }

        Geometry.drawFullscreenQuad()

        shader.unbind()
        glActiveTexture(GL_TEXTURE0)
    }

    override fun clone(): ISFFilter {
        val copy = ISFFilter(id, displayName, header, shader, ownsShader = false)
        copy.enabled = this.enabled
        copy.dryWet.baseValue = this.dryWet.baseValue
        // Note: modulators are not cloned here as they are managed by the Preset/Deck logic
        this.parameters.forEach { (name, param) ->
            copy.parameters[name]?.baseValue = param.baseValue
        }
        return copy
    }

    override fun reset() {
        enabled = true
        dryWet.reset()
        parameters.values.forEach { it.reset() }
        frameIndex = 0
    }

    override fun dispose() {
        if (ownsShader) {
            shader.dispose()
        }
    }

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        list.add("$prefix/DryWet" to dryWet)
        parameters.forEach { (name, param) ->
            list.add("$prefix/$name" to param)
        }
        return list
    }
}
