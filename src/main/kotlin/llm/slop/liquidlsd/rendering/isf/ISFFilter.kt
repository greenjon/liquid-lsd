package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.FBO
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
    val ownsShader: Boolean = true,
    override val categories: List<String> = header.CATEGORIES.takeIf { it != null && it.isNotEmpty() } ?: listOf("Color Adjustment")
) : VisualEffect {

    override val parameters = mutableMapOf<String, ModulatableParameter>()
    override val dryWet = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f)
    override var enabled = true

    private val inputImageName: String?
    private var frameIndex = 0
    private var lastTime = TimeSource.getTimeSec().toFloat()
    
    // Multipass infrastructure
    private val passFBOs = mutableMapOf<String, FBO>()
    private val passHistoryFBOs = mutableMapOf<String, Pair<FBO, FBO>>()
    private var deckWidth = 0
    private var deckHeight = 0

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
     * Resolves pass width/height expressions like "$WIDTH/2.0".
     */
    private fun resolveDimension(expr: String?, baseDim: Int): Int {
        if (expr == null) return baseDim
        return try {
            val s = expr.replace("\$WIDTH", deckWidth.toString())
                        .replace("\$HEIGHT", deckHeight.toString())
            // Very simple division parsing for now, enough for bloom
            if (s.contains("/")) {
                val parts = s.split("/")
                (parts[0].trim().toFloat() / parts[1].trim().toFloat()).toInt()
            } else {
                s.toFloat().toInt()
            }
        } catch (e: Exception) {
            baseDim
        }
    }

    /**
     * Renders the filter reading from [inputTexture] into the currently bound FBO.
     */
    fun render(inputTexture: Int, width: Int, height: Int) {
        // Handle resizing passes if needed
        if (deckWidth != width || deckHeight != height) {
            deckWidth = width
            deckHeight = height
            resizePassFBOs()
        }

        shader.bind()

        // Set standard ISF uniforms
        val currentTime = TimeSource.getTimeSec().toFloat()
        val deltaTime = currentTime - lastTime
        lastTime = currentTime

        shader.setUniform("TIME", currentTime)
        shader.setUniform("TIMEDELTA", deltaTime)
        shader.setUniform("FRAMEINDEX", frameIndex++)
        
        val now = LocalDateTime.now()
        shader.setUniform("DATE", now.year.toFloat(), now.monthValue.toFloat(), now.dayOfMonth.toFloat(), 
            now.hour * 3600f + now.minute * 60f + now.second + now.nano / 1_000_000_000f)

        // Set user parameters
        for (input in header.INPUTS) {
            val param = parameters[input.NAME] ?: continue
            val type = input.TYPE.lowercase()
            
            when (type) {
                "float", "long", "bool" -> shader.setUniform(input.NAME, param.value)
                "point2d" -> shader.setUniform(input.NAME, param.value, 0f) 
                "color" -> shader.setUniform(input.NAME, param.value, param.value, param.value, 1.0f) 
            }
        }

        if (header.PASSES.isEmpty()) {
            shader.setUniform("PASSINDEX", 0)
            shader.setUniform("RENDERSIZE", width.toFloat(), height.toFloat())
            if (inputImageName != null) {
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, inputTexture)
                shader.setUniform(inputImageName, 0)
            }
            Geometry.drawFullscreenQuad()
        } else {
            // Restore FBO ID later since we bind our own pass targets
            val currentFbo = glGetInteger(GL_FRAMEBUFFER_BINDING)

            for ((passIdx, pass) in header.PASSES.withIndex()) {
                val isFinalPass = (passIdx == header.PASSES.size - 1)
                
                // Determine target FBO
                val targetName = pass.TARGET
                val fboToBind = if (isFinalPass) {
                    currentFbo
                } else if (pass.PERSISTENT && targetName != null) {
                    // write to the active persistent FBO
                    passHistoryFBOs[targetName]?.first?.framebufferId ?: currentFbo
                } else {
                    passFBOs[targetName]?.framebufferId ?: currentFbo
                }

                glBindFramebuffer(GL_FRAMEBUFFER, fboToBind)
                
                val renderWidth = resolveDimension(pass.WIDTH, width)
                val renderHeight = resolveDimension(pass.HEIGHT, height)
                glViewport(0, 0, renderWidth, renderHeight)
                
                shader.setUniform("PASSINDEX", passIdx)
                shader.setUniform("RENDERSIZE", renderWidth.toFloat(), renderHeight.toFloat())

                var texUnit = 0
                
                // Bind input image
                if (inputImageName != null) {
                    glActiveTexture(GL_TEXTURE0 + texUnit)
                    glBindTexture(GL_TEXTURE_2D, inputTexture)
                    shader.setUniform(inputImageName, texUnit)
                    texUnit++
                }

                // Bind all pass targets available
                for (target in header.PASSES.mapNotNull { it.TARGET }) {
                    val p = header.PASSES.find { it.TARGET == target }
                    val texToBind = if (p?.PERSISTENT == true) {
                        // read from the history persistent FBO
                        passHistoryFBOs[target]?.second?.texture ?: 0
                    } else {
                        passFBOs[target]?.texture ?: 0
                    }
                    if (texToBind != 0) {
                        glActiveTexture(GL_TEXTURE0 + texUnit)
                        glBindTexture(GL_TEXTURE_2D, texToBind)
                        shader.setUniform(target, texUnit)
                        texUnit++
                    }
                }

                Geometry.drawFullscreenQuad()

                // Swap ping pong buffers if persistent
                if (pass.PERSISTENT && targetName != null) {
                    val pair = passHistoryFBOs[targetName]
                    if (pair != null) {
                        passHistoryFBOs[targetName] = Pair(pair.second, pair.first)
                    }
                }
            }
            
            // Restore original FBO and viewport
            glBindFramebuffer(GL_FRAMEBUFFER, currentFbo)
            glViewport(0, 0, width, height)
        }

        shader.unbind()
        glActiveTexture(GL_TEXTURE0)
    }

    /**
     * Renders a 2-image transition reading from [startTexture] (Deck A) and [endTexture] (Deck B)
     * into the currently bound FBO using [progressValue] (0.0 to 1.0).
     */
    fun renderTransition(startTexture: Int, endTexture: Int, progressValue: Float, width: Int, height: Int) {
        if (deckWidth != width || deckHeight != height) {
            deckWidth = width
            deckHeight = height
            resizePassFBOs()
        }

        shader.bind()

        val currentTime = TimeSource.getTimeSec().toFloat()
        val deltaTime = currentTime - lastTime
        lastTime = currentTime

        shader.setUniform("TIME", currentTime)
        shader.setUniform("TIMEDELTA", deltaTime)
        shader.setUniform("FRAMEINDEX", frameIndex++)
        
        val now = LocalDateTime.now()
        shader.setUniform("DATE", now.year.toFloat(), now.monthValue.toFloat(), now.dayOfMonth.toFloat(), 
            now.hour * 3600f + now.minute * 60f + now.second + now.nano / 1_000_000_000f)

        // Set user parameters
        for (input in header.INPUTS) {
            val type = input.TYPE.lowercase()
            if (type == "image") continue

            if (input.NAME.equals("progress", ignoreCase = true)) {
                shader.setUniform(input.NAME, progressValue)
                continue
            }

            val param = parameters[input.NAME] ?: continue
            when (type) {
                "float", "long", "bool" -> shader.setUniform(input.NAME, param.value)
                "point2d" -> shader.setUniform(input.NAME, param.value, 0f) 
                "color" -> shader.setUniform(input.NAME, param.value, param.value, param.value, 1.0f) 
            }
        }

        shader.setUniform("PASSINDEX", 0)
        shader.setUniform("RENDERSIZE", width.toFloat(), height.toFloat())

        // Map image inputs (startImage -> unit 0, endImage -> unit 1)
        val imageInputs = header.INPUTS.filter { it.TYPE.lowercase() == "image" }
        val startName = imageInputs.find { it.NAME.equals("startImage", true) || it.NAME.equals("inputImage", true) || it.NAME.equals("uTex1", true) }?.NAME
            ?: imageInputs.firstOrNull()?.NAME ?: "startImage"
        val endName = imageInputs.find { it.NAME.equals("endImage", true) || it.NAME.equals("toImage", true) || it.NAME.equals("uTex2", true) }?.NAME
            ?: imageInputs.getOrNull(1)?.NAME ?: "endImage"

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, startTexture)
        shader.setUniform(startName, 0)

        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, endTexture)
        shader.setUniform(endName, 1)

        Geometry.drawFullscreenQuad()

        shader.unbind()
        glActiveTexture(GL_TEXTURE0)
    }

    private fun resizePassFBOs() {
        // clear existing
        passFBOs.values.forEach { it.dispose() }
        passFBOs.clear()
        
        passHistoryFBOs.values.forEach { 
            it.first.dispose()
            it.second.dispose()
        }
        passHistoryFBOs.clear()

        // create new
        for (pass in header.PASSES) {
            val target = pass.TARGET ?: continue
            val passW = resolveDimension(pass.WIDTH, deckWidth)
            val passH = resolveDimension(pass.HEIGHT, deckHeight)
            val format = if (pass.FLOAT) GL_RGBA32F else GL_RGBA8

            if (pass.PERSISTENT) {
                val fbo1 = FBO(passW, passH, format)
                val fbo2 = FBO(passW, passH, format)
                // Initialize persistent buffers to zero/transparent
                fbo1.clear(0f,0f,0f,0f)
                fbo2.clear(0f,0f,0f,0f)
                passHistoryFBOs[target] = Pair(fbo1, fbo2)
            } else {
                passFBOs[target] = FBO(passW, passH, format)
            }
        }
    }

    override fun clone(): ISFFilter {
        val copy = ISFFilter(id, displayName, header, shader, ownsShader = false, categories = this.categories)
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
        passFBOs.values.forEach { it.dispose() }
        passFBOs.clear()
        passHistoryFBOs.values.forEach { 
            it.first.dispose()
            it.second.dispose()
        }
        passHistoryFBOs.clear()
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
