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
    private val backgroundShader: Shader
    private val blitShader: Shader
    private val mandelbulbShader: Shader
    private val kifsShader: Shader
    private val gyroidShader: Shader
    private val chladniShader: Shader

    private var mandalaVAO: Int = 0
    private var mandalaVBO: Int = 0
    private var isDisposed = false

    init {
        // Load the shaders
        mandalaShader = Shader.fromResources("shaders/mandala.vert", "shaders/mandala.frag")
        feedbackShader = Shader.fromResources("shaders/blit.vert", "shaders/feedback.frag")
        mixerShader = Shader.fromResources("shaders/blit.vert", "shaders/mixer.frag")
        backgroundShader = Shader.fromResources("shaders/blit.vert", "shaders/background.frag")
        blitShader = Shader.fromResources("shaders/blit.vert", "shaders/blit.frag")
        mandelbulbShader = Shader.fromResources("shaders/mandelbulb.vert", "shaders/mandelbulb.frag")
        kifsShader = Shader.fromResources("shaders/mandelbulb.vert", "shaders/kifs.frag")
        gyroidShader = Shader.fromResources("shaders/mandelbulb.vert", "shaders/gyroid.frag")
        chladniShader = Shader.fromResources("shaders/mandelbulb.vert", "shaders/chladni.frag")

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

    fun render(source: VisualSource, targetFBO: FBO) {
        if (source is Mandala) {
            renderMandala(source, targetFBO)
        } else if (source is Mandelbulb) {
            renderMandelbulb(source, targetFBO)
        } else if (source is Kifs) {
            renderKifs(source, targetFBO)
        } else if (source is Gyroid) {
            renderGyroid(source, targetFBO)
        } else if (source is Chladni) {
            renderChladni(source, targetFBO)
        }
    }

    private fun renderChladni(chladni: Chladni, targetFBO: FBO) {
        targetFBO.bind()

        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        chladniShader.bind()

        val p = chladni.parameters
        chladniShader.setUniform("uMode", p["Mode"]?.value ?: 0.0f)
        chladniShader.setUniform("uFrequencyN", p["Frequency N"]?.value ?: 3.0f)
        chladniShader.setUniform("uFrequencyM", p["Frequency M"]?.value ?: 5.0f)
        chladniShader.setUniform("uFrequencyL", p["Frequency L"]?.value ?: 2.0f)
        chladniShader.setUniform("uThickness", p["Thickness"]?.value ?: 0.0f)
        chladniShader.setUniform("uWallWidth", p["Wall Width"]?.value ?: 0.1f)
        chladniShader.setUniform("uScale", p["Scale"]?.value ?: 3.0f)
        chladniShader.setUniform("uSpeed", p["Speed"]?.value ?: 1.0f)
        chladniShader.setUniform("uZoom", p["Zoom"]?.value ?: 1.0f)
        chladniShader.setUniform("uColorShift", p["Color Shift"]?.value ?: 0.0f)
        chladniShader.setUniform("uYaw", p["Yaw"]?.value ?: 0.0f)
        chladniShader.setUniform("uPitch", p["Pitch"]?.value ?: 0.0f)
        chladniShader.setUniform("uAlpha", chladni.globalAlpha.value)
        chladniShader.setUniform("uResolution", targetFBO.width.toFloat(), targetFBO.height.toFloat())
        chladniShader.setUniform("uGlow", p["Glow"]?.value ?: 0.5f)
        chladniShader.setUniform("uTime", org.lwjgl.glfw.GLFW.glfwGetTime().toFloat())

        Geometry.drawFullscreenQuad()

        chladniShader.unbind()
        targetFBO.unbind()
    }

    private fun renderKifs(kifs: Kifs, targetFBO: FBO) {
        targetFBO.bind()

        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        kifsShader.bind()

        val p = kifs.parameters
        kifsShader.setUniform("uIterations", p["Iterations"]?.value ?: 5.0f)
        kifsShader.setUniform("uScale", p["Scale"]?.value ?: 2.0f)
        kifsShader.setUniform("uFoldX", p["Fold X"]?.value ?: 0.5f)
        kifsShader.setUniform("uFoldY", p["Fold Y"]?.value ?: 0.5f)
        kifsShader.setUniform("uFoldZ", p["Fold Z"]?.value ?: 0.5f)
        kifsShader.setUniform("uFoldAngleX", p["Fold Angle X"]?.value ?: 0.0f)
        kifsShader.setUniform("uFoldAngleY", p["Fold Angle Y"]?.value ?: 0.0f)
        kifsShader.setUniform("uFoldAngleZ", p["Fold Angle Z"]?.value ?: 0.0f)
        kifsShader.setUniform("uShapeMorph", p["Shape Morph"]?.value ?: 0.0f)
        kifsShader.setUniform("uZoom", p["Zoom"]?.value ?: 1.0f)
        kifsShader.setUniform("uColorShift", p["Color Shift"]?.value ?: 0.0f)
        kifsShader.setUniform("uYaw", p["Yaw"]?.value ?: 0.0f)
        kifsShader.setUniform("uPitch", p["Pitch"]?.value ?: 0.0f)
        kifsShader.setUniform("uAlpha", kifs.globalAlpha.value)
        kifsShader.setUniform("uResolution", targetFBO.width.toFloat(), targetFBO.height.toFloat())
        kifsShader.setUniform("uGlow", p["Glow"]?.value ?: 0.5f)

        Geometry.drawFullscreenQuad()

        kifsShader.unbind()
        targetFBO.unbind()
    }

    private fun renderGyroid(gyroid: Gyroid, targetFBO: FBO) {
        targetFBO.bind()

        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        gyroidShader.bind()

        val p = gyroid.parameters
        gyroidShader.setUniform("uScaleX", p["Scale X"]?.value ?: 3.0f)
        gyroidShader.setUniform("uScaleY", p["Scale Y"]?.value ?: 3.0f)
        gyroidShader.setUniform("uScaleZ", p["Scale Z"]?.value ?: 3.0f)
        gyroidShader.setUniform("uThickness", p["Thickness"]?.value ?: 0.0f)
        gyroidShader.setUniform("uWallWidth", p["Wall Width"]?.value ?: 0.1f)
        gyroidShader.setUniform("uSpeed", p["Speed"]?.value ?: 1.0f)
        gyroidShader.setUniform("uZoom", p["Zoom"]?.value ?: 1.0f)
        gyroidShader.setUniform("uColorShift", p["Color Shift"]?.value ?: 0.0f)
        gyroidShader.setUniform("uYaw", p["Yaw"]?.value ?: 0.0f)
        gyroidShader.setUniform("uPitch", p["Pitch"]?.value ?: 0.0f)
        gyroidShader.setUniform("uAlpha", gyroid.globalAlpha.value)
        gyroidShader.setUniform("uResolution", targetFBO.width.toFloat(), targetFBO.height.toFloat())
        gyroidShader.setUniform("uGlow", p["Glow"]?.value ?: 0.5f)
        gyroidShader.setUniform("uTime", org.lwjgl.glfw.GLFW.glfwGetTime().toFloat())

        Geometry.drawFullscreenQuad()

        gyroidShader.unbind()
        targetFBO.unbind()
    }


    private fun renderBackground(mandala: Mandala, alpha: Float) {
        backgroundShader.bind()
        val p = mandala.parameters
        val style = p["Bg Style"]?.value?.toInt() ?: 0
        backgroundShader.setUniform("uBgStyle", style)
        backgroundShader.setUniform("uBgHue", p["Bg Hue"]?.value ?: 0f)
        backgroundShader.setUniform("uBgSat", p["Bg Sat"]?.value ?: 0.8f)
        backgroundShader.setUniform("uBgVal", p["Bg Val"]?.value ?: 0.5f)
        backgroundShader.setUniform("uBgSweep", p["Bg Sweep"]?.value ?: 0.2f)
        backgroundShader.setUniform("uBgSpeed", p["Bg Speed"]?.value ?: 0.2f)
        backgroundShader.setUniform("uBgZoom", p["Bg Zoom"]?.value ?: 1.0f)
        backgroundShader.setUniform("uTime", org.lwjgl.glfw.GLFW.glfwGetTime().toFloat())
        backgroundShader.setUniform("uAlpha", alpha)

        Geometry.drawFullscreenQuad()
        backgroundShader.unbind()
    }

    /**
     * Renders a Mandala to the specified FBO.
     */
    private fun renderMandala(mandala: Mandala, targetFBO: FBO) {
        targetFBO.bind()

        // Clear the framebuffer's color buffer (fully transparent black background)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        val p = mandala.parameters
        val bgStyle = p["Bg Style"]?.value ?: 0f
        if (bgStyle > 0.5f) {
            val bgFeedback = p["Bg Feedback"]?.value ?: 0f
            val alpha = bgFeedback * mandala.globalAlpha.value
            if (alpha > 0.001f) {
                renderBackground(mandala, alpha)
            }
        }

        mandalaShader.bind()

        // Set arm length parameters (L1 to L4)
        mandalaShader.setUniform("uL1", p["L1"]?.value ?: 0f)
        mandalaShader.setUniform("uL2", p["L2"]?.value ?: 0f)
        mandalaShader.setUniform("uL3", p["L3"]?.value ?: 0f)
        mandalaShader.setUniform("uL4", p["L4"]?.value ?: 0f)

        // Set arm frequency parameters (a to d)
        mandalaShader.setUniform("uA", mandala.recipe.a.toFloat())
        mandalaShader.setUniform("uB", mandala.recipe.b.toFloat())
        mandalaShader.setUniform("uC", mandala.recipe.c.toFloat())
        mandalaShader.setUniform("uD", mandala.recipe.d.toFloat())

        // Set 3D Mode & Symmetrical Projection parameters
        val modeVal = p["3D Mode"]?.value ?: 0f
        val mode = modeVal.roundToInt().coerceIn(0, 3)
        mandalaShader.setUniform("u3DMode", mode.toFloat())
        mandalaShader.setUniform("uSphereWrapX", p["Sphere Wrap X"]?.value ?: 1f)
        mandalaShader.setUniform("uSphereWrapY", p["Sphere Wrap Y"]?.value ?: 1f)
        mandalaShader.setUniform("uMirrorGroup", p["Mirror Group"]?.value ?: 0f)
        mandalaShader.setUniform("uPermuteXY", p["Permute XY"]?.value ?: 1f)
        mandalaShader.setUniform("uPermuteYZ", p["Permute YZ"]?.value ?: 1f)
        mandalaShader.setUniform("uPermuteZX", p["Permute ZX"]?.value ?: 1f)

        // Set 3D rotations & perspective projection
        mandalaShader.setUniform("uYaw", p["3D Yaw"]?.value ?: 0f)
        mandalaShader.setUniform("uPitch", p["3D Pitch"]?.value ?: 0f)
        mandalaShader.setUniform("uPersp", p["3D Persp"]?.value ?: 0.5f)

        // Calculate and set global transformation uniforms
        val scale = (p["Scale"]?.value ?: 0.125f) * 8.0f
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
        val numInstances = when (mode) {
            0 -> 1 // 2D
            1 -> 1 // Spherical
            2 -> {
                // Polyhedral/Mirror
                val mirrorGroup = (p["Mirror Group"]?.value ?: 0f).roundToInt().coerceIn(0, 1)
                if (mirrorGroup == 0) 8 else 4 // 8 for Cubic, 4 for Tetrahedral
            }
            3 -> 3 // Coordinate Permutation
            else -> 1
        }
        if (numInstances > 1) {
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, (Mandala.POINTS + 1) * 2, numInstances)
        } else {
            glDrawArrays(GL_TRIANGLE_STRIP, 0, (Mandala.POINTS + 1) * 2)
        }

        glBindVertexArray(0)
        mandalaShader.unbind()
        targetFBO.unbind()
    }

    private fun renderMandelbulb(mandelbulb: Mandelbulb, targetFBO: FBO) {
        targetFBO.bind()

        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        mandelbulbShader.bind()

        val p = mandelbulb.parameters
        mandelbulbShader.setUniform("uPower", p["Power"]?.value ?: 8.0f)
        mandelbulbShader.setUniform("uIterations", p["Iterations"]?.value ?: 6.0f)
        mandelbulbShader.setUniform("uGlow", p["Glow"]?.value ?: 0.5f)
        mandelbulbShader.setUniform("uZoom", p["Zoom"]?.value ?: 1.0f)
        mandelbulbShader.setUniform("uColorShift", p["Color Shift"]?.value ?: 0.0f)
        mandelbulbShader.setUniform("uBailout", p["Bailout"]?.value ?: 2.0f)
        mandelbulbShader.setUniform("uYaw", p["Yaw"]?.value ?: 0.0f)
        mandelbulbShader.setUniform("uPitch", p["Pitch"]?.value ?: 0.0f)
        mandelbulbShader.setUniform("uAlpha", mandelbulb.globalAlpha.value)
        mandelbulbShader.setUniform("uResolution", targetFBO.width.toFloat(), targetFBO.height.toFloat())

        Geometry.drawFullscreenQuad()

        mandelbulbShader.unbind()
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
        feedbackShader.setUniform("uChroma", deck.fbChroma.value)
        feedbackShader.setUniform("uFeedbackMode", deck.fbMode.value)

        // Composite feedback onto fullscreen quad
        Geometry.drawFullscreenQuad()

        feedbackShader.unbind()
        nextHistoryFBO.unbind()

        val mandala = deck.source as? Mandala
        val bgStyle = mandala?.parameters?.get("Bg Style")?.value ?: 0f
        if (bgStyle > 0.5f && mandala != null) {
            deck.cleanFBO.bind()
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)

            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            val bgFeedback = mandala.parameters["Bg Feedback"]?.value ?: 0f
            val staticAlpha = (1.0f - bgFeedback) * mandala.globalAlpha.value
            if (staticAlpha > 0.001f) {
                renderBackground(mandala, staticAlpha)
            }

            glEnable(GL_BLEND)
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

            blitShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, nextHistoryFBO.texture)
            blitShader.setUniform("uTexture", 0)

            Geometry.drawFullscreenQuad()

            blitShader.unbind()
            deck.cleanFBO.unbind()
        }

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

        glDisable(GL_BLEND)

        mixerShader.bind()

        // Bind Deck A output texture to Unit 0
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, mixer.deckA.getOutputTexture())
        mixerShader.setUniform("uTex1", 0)

        // Bind Deck B output texture to Unit 1
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, mixer.deckB.getOutputTexture())
        mixerShader.setUniform("uTex2", 1)

        // Set mix uniforms
        mixerShader.setUniform("uMode", mixer.mode.value.toInt())
        mixerShader.setUniform("uBalance", mixer.crossfade.value)
        mixerShader.setUniform("uAlpha", mixer.masterAlpha.value)
        mixerShader.setUniform("uBloom", mixer.bloom.value)

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
            backgroundShader.dispose()
            blitShader.dispose()
            mandelbulbShader.dispose()
            kifsShader.dispose()
            gyroidShader.dispose()
            chladniShader.dispose()
            isDisposed = true
        }
    }
}
