package llm.slop.spirals.rendering

import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Main OpenGL renderer class.
 * Handles loading of shaders (mandala, feedback, mixer), VAO/VBO initialization,
 * and orchestrates Deck rendering and Mixer compositing.
 */
class Renderer {
    private val mandalaShader: Shader
    private val feedbackShader: Shader
    private val mixerShader: Shader

    private var mandalaVAO: Int = 0
    private var mandalaVBO: Int = 0
    private var isDisposed = false

    init {
        // Load the shaders
        mandalaShader = Shader.fromResources("shaders/mandala.vert", "shaders/mandala.frag")
        feedbackShader = Shader.fromResources("shaders/blit.vert", "shaders/feedback.frag")
        mixerShader = Shader.fromResources("shaders/blit.vert", "shaders/mixer.frag")

        // Initialize VAO and VBO for Mandala geometry (ribbon coordinates)
        val expansionBuffer = Mandala.expansionBuffer
        val buffer: FloatBuffer = MemoryUtil.memAllocFloat(expansionBuffer.size)
        buffer.put(expansionBuffer).flip()

        try {
            mandalaVAO = glGenVertexArrays()
            mandalaVBO = glGenBuffers()

            glBindVertexArray(mandalaVAO)
            glBindBuffer(GL_ARRAY_BUFFER, mandalaVBO)
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)

            // Location 0: vec2 [phase, side]
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
            glEnableVertexAttribArray(0)

            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindVertexArray(0)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    /**
     * Renders a general VisualSource to the specified FBO.
     */
    fun render(source: VisualSource, targetFBO: FBO) {
        if (source is Mandala) {
            renderMandala(source, targetFBO)
        }
    }

    /**
     * Renders a Mandala to the specified FBO.
     */
    private fun renderMandala(mandala: Mandala, targetFBO: FBO) {
        targetFBO.bind()

        // Clear the framebuffer's color buffer (fully transparent black background)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        mandalaShader.bind()

        // Set arm length parameters (L1 to L4)
        val p = mandala.parameters
        mandalaShader.setUniform("uL1", p["L1"]?.value ?: 0f)
        mandalaShader.setUniform("uL2", p["L2"]?.value ?: 0f)
        mandalaShader.setUniform("uL3", p["L3"]?.value ?: 0f)
        mandalaShader.setUniform("uL4", p["L4"]?.value ?: 0f)

        // Set arm frequency parameters (a to d)
        mandalaShader.setUniform("uA", mandala.recipe.a.toFloat())
        mandalaShader.setUniform("uB", mandala.recipe.b.toFloat())
        mandalaShader.setUniform("uC", mandala.recipe.c.toFloat())
        mandalaShader.setUniform("uD", mandala.recipe.d.toFloat())

        // Calculate and set global transformation uniforms
        val scale = (p["Scale"]?.value ?: 0.125f) * mandala.globalScale.value * 8.0f
        val rotation = (p["Rotation"]?.value ?: 0f) * 2.0f * Math.PI.toFloat()
        val thickness = (p["Thickness"]?.value ?: 0.5f) * 0.035f
        val aspect = targetFBO.width.toFloat() / targetFBO.height.toFloat()

        mandalaShader.setUniform("uGlobalScale", scale)
        mandalaShader.setUniform("uGlobalRotation", rotation)
        mandalaShader.setUniform("uThickness", thickness)
        mandalaShader.setUniform("uAspectRatio", aspect)

        // Set color-related uniforms
        val hueOffset = p["Hue Offset"]?.value ?: 0f
        val petals = mandala.recipe.petals
        val options = mandala.getSymmetricHueCycles(petals)
        val rawSweep = p["Hue Sweep"]?.value ?: 0f
        val index = if (options.size > 1) {
            (rawSweep * (options.size - 1)).roundToInt().coerceIn(0, options.size - 1)
        } else {
            0
        }
        val hueSweep = options[index].toFloat()
        val depth = p["Depth"]?.value ?: 0.35f

        mandalaShader.setUniform("uHueOffset", hueOffset)
        mandalaShader.setUniform("uHueSweep", hueSweep)
        mandalaShader.setUniform("uDepth", depth)
        mandalaShader.setUniform("uMaxR", mandala.maxR)
        mandalaShader.setUniform("uAlpha", mandala.globalAlpha.value)

        // Render the mandala ribbon
        glBindVertexArray(mandalaVAO)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, (Mandala.POINTS + 1) * 2)

        glBindVertexArray(0)
        mandalaShader.unbind()
        targetFBO.unbind()
    }

    /**
     * Renders a Deck's visual source and updates its ping-pong feedback loop.
     */
    fun renderDeck(deck: Deck) {
        // 1. Render clean source image
        render(deck.source, deck.cleanFBO)

        // 2. Blend clean image and current history into next history FBO
        val nextHistoryFBO = deck.getNextHistoryFBO()
        nextHistoryFBO.bind()

        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        // Disable GL blending so the feedback shader can perform its custom max blending
        // without the GPU applying compounding alpha multiplication on top.
        glDisable(GL_BLEND)

        feedbackShader.bind()

        // Bind clean source texture to Unit 0
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, deck.cleanFBO.texture)
        feedbackShader.setUniform("uTextureLive", 0)

        // Bind current history texture to Unit 1
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, deck.getCurrentHistoryFBO().texture)
        feedbackShader.setUniform("uTextureHistory", 1)

        // Set feedback parameters (map feedback strength S to decay using a cubic curve)
        val s = deck.fbDecay.value
        val decayVal = Math.pow((1.0f - s).toDouble(), 3.0).toFloat()
        feedbackShader.setUniform("uDecay", decayVal)
        feedbackShader.setUniform("uGain", deck.fbGain.value)
        feedbackShader.setUniform("uZoom", deck.fbZoom.value)
        feedbackShader.setUniform("uRotate", deck.fbRotate.value)
        feedbackShader.setUniform("uHueShift", deck.fbHueShift.value)
        feedbackShader.setUniform("uBlur", deck.fbBlur.value)

        // Composite feedback onto fullscreen quad
        Geometry.drawFullscreenQuad()

        feedbackShader.unbind()
        nextHistoryFBO.unbind()

        // Re-enable blending for subsequent rendering passes
        glEnable(GL_BLEND)

        // Reset active texture unit to Unit 0 to avoid side effects
        glActiveTexture(GL_TEXTURE0)

        // Swap ping-pong indices so currentHistory points to the frame we just rendered
        deck.swapFeedbackBuffers()
    }

    /**
     * Composites Deck A and Deck B outputs into the Mixer's master output FBO.
     */
    fun renderMixer(mixer: Mixer) {
        mixer.masterFBO.bind()

        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)

        mixerShader.bind()

        // Bind Deck A output texture to Unit 0
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, mixer.deckA.getCurrentHistoryFBO().texture)
        mixerShader.setUniform("uTex1", 0)

        // Bind Deck B output texture to Unit 1
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, mixer.deckB.getCurrentHistoryFBO().texture)
        mixerShader.setUniform("uTex2", 1)

        // Set mix uniforms
        mixerShader.setUniform("uMode", mixer.mode.value.toInt())
        mixerShader.setUniform("uBalance", mixer.crossfade.value)
        mixerShader.setUniform("uAlpha", mixer.masterAlpha.value)

        // Blit mixed output
        Geometry.drawFullscreenQuad()

        mixerShader.unbind()
        mixer.masterFBO.unbind()
    }

    /**
     * Clean up OpenGL resources.
     */
    fun dispose() {
        if (!isDisposed) {
            glDeleteBuffers(mandalaVBO)
            glDeleteVertexArrays(mandalaVAO)
            mandalaShader.dispose()
            feedbackShader.dispose()
            mixerShader.dispose()
            isDisposed = true
        }
    }
}
