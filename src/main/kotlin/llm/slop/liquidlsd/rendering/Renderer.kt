package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.utils.TimeSource
import org.lwjgl.opengl.GL33.*
import kotlin.math.roundToInt

/**
 * Main OpenGL renderer class.
 * Handles loading of shaders (feedback, mixer, blit, tri-planar),
 * and orchestrates Deck rendering and Mixer compositing.
 */
class Renderer {

    private val feedbackShader: Shader
    private val mixerShader: Shader
    val blitShader: Shader
    private val triPlanarShader: Shader
    private val view2DShader: Shader

    private var isDisposed = false

    init {
        // Load the shaders
        feedbackShader = Shader.fromResources("shaders/blit.vert", "shaders/feedback.frag")
        mixerShader = Shader.fromResources("shaders/blit.vert", "shaders/mixer.frag")
        blitShader = Shader.fromResources("shaders/blit.vert", "shaders/blit.frag")
        triPlanarShader = Shader.fromResources("shaders/tri_planar.vert", "shaders/tri_planar.frag")
        view2DShader = Shader.fromResources("shaders/blit.vert", "shaders/view2d.frag")
    }

    fun render(source: VisualSource, targetFBO: FBO) {
        if (source !is DynamicVisualSource) return

        val hasFb = source.hasFeedback
        val renderTarget = if (hasFb) {
            if (source.fb1 == null ||
                source.fb1!!.width  != targetFBO.width ||
                source.fb1!!.height != targetFBO.height
            ) {
                source.fb1?.dispose()
                source.fb2?.dispose()
                source.fb1 = FBO(targetFBO.width, targetFBO.height)
                source.fb2 = FBO(targetFBO.width, targetFBO.height)
                source.fb1!!.clear(0f, 0f, 0f, 0f)
                source.fb2!!.clear(0f, 0f, 0f, 0f)
                source.fbIndex = 0
            }
            source.getNextHistoryFBO()!!
        } else {
            targetFBO
        }

        renderTarget.bind()
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        if (hasFb) {
            glDisable(GL_BLEND)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, source.getCurrentHistoryFBO()!!.texture)
        } else {
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        }

        source.shader.bind()
        source.setupUniforms(source.shader)

        // Common uniforms for all sources
        source.shader.setUniform("uAlpha",       source.globalAlpha.value)
        source.shader.setUniform("uResolution",  targetFBO.width.toFloat(), targetFBO.height.toFloat())
        source.shader.setUniform("uTime",        TimeSource.getTimeSec().toFloat())
        source.shader.setUniform("uAspectRatio", targetFBO.width.toFloat() / targetFBO.height.toFloat())
        if (hasFb) {
            source.shader.setUniform("src", 0)
        }

        source.drawTopology()

        source.shader.unbind()
        renderTarget.unbind()

        if (hasFb) {
            source.swapFeedbackBuffers()
            // blit new history → targetFBO
            targetFBO.bind()
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            blitShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, source.getCurrentHistoryFBO()!!.texture)
            blitShader.setUniform("uTexture", 0)
            Geometry.drawFullscreenQuad()
            blitShader.unbind()
            targetFBO.unbind()
            glActiveTexture(GL_TEXTURE0)
        }
    }



    /**
     * Renders a Deck's visual source and updates its ping-pong feedback loop.
     */
    fun renderDeck(deck: Deck) {
        if (deck.isEmpty) {
            return
        }
        // 1. Render clean source image
        val is3D = deck.view3DMode.value >= 0.5f
        if (!is3D) {
            // Render 2D source into rawSource2DFBO (widescreen native resolution)
            render(deck.source, deck.rawSource2DFBO)

            // Render 2D transformed view (Zoom, Rotate Z) onto cleanFBO
            deck.cleanFBO.bind()
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)

            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            view2DShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, deck.rawSource2DFBO.texture)
            view2DShader.setUniform("uTexture", 0)

            view2DShader.setUniform("uZoom", deck.viewZoom.value)
            view2DShader.setUniform("uRotateZ", deck.viewRotateZ.value)
            val aspect = deck.cleanFBO.width.toFloat() / deck.cleanFBO.height.toFloat()
            view2DShader.setUniform("uAspectRatio", aspect)

            Geometry.drawFullscreenQuad()

            view2DShader.unbind()
            deck.cleanFBO.unbind()
            glActiveTexture(GL_TEXTURE0)
        } else {
            // Render 2D source into rawSourceFBO
            render(deck.source, deck.rawSourceFBO)

            // Render 3D Tri-Planar projection onto cleanFBO
            deck.cleanFBO.bind()
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)

            val isAdditive = deck.viewBlendMode.value >= 0.5f
            glEnable(GL_BLEND)
            if (isAdditive) {
                glBlendFunc(GL_ONE, GL_ONE)
            } else {
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            }

            triPlanarShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, deck.rawSourceFBO.texture)
            triPlanarShader.setUniform("uTexture", 0)

            triPlanarShader.setUniform("uPitch", deck.viewRotateX.value)
            triPlanarShader.setUniform("uYaw", deck.viewRotateY.value)
            triPlanarShader.setUniform("uRoll", deck.viewRotateZ.value)
            triPlanarShader.setUniform("uZoom", deck.viewZoom.value)
            triPlanarShader.setUniform("uPersp", deck.viewPersp.value)
            triPlanarShader.setUniform("uSeparation", deck.viewSeparation.value)
            triPlanarShader.setUniform("uDepthDim", deck.viewDepthDim.value)
            triPlanarShader.setUniform("uAlpha", deck.source.globalAlpha.value)
            triPlanarShader.setUniform("uBlendAdditive", if (isAdditive) 1.0f else 0.0f)
            val aspect = deck.cleanFBO.width.toFloat() / deck.cleanFBO.height.toFloat()
            triPlanarShader.setUniform("uAspectRatio", aspect)

            val modeVal = deck.view3DMode.value.roundToInt()
            val numInstances = if (modeVal >= 2) 6 else 3

            glBindVertexArray(Geometry.getFullscreenQuad())
            glDrawArraysInstanced(GL_TRIANGLES, 0, 6, numInstances)
            glBindVertexArray(0)

            triPlanarShader.unbind()
            deck.cleanFBO.unbind()
            glActiveTexture(GL_TEXTURE0)
        }

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
        feedbackShader.setUniform("uKaleido", deck.fbKaleido.value)

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

        // Bind Deck BG output texture to Unit 2
        glActiveTexture(GL_TEXTURE2)
        glBindTexture(GL_TEXTURE_2D, mixer.deckBG.getOutputTexture())
        mixerShader.setUniform("uTexBG", 2)

        // Set mix uniforms
        mixerShader.setUniform("uMode", mixer.mode.value.toInt())
        mixerShader.setUniform("uBalance", (mixer.crossfade.value + 1.0f) / 2.0f)
        mixerShader.setUniform("uAlpha", mixer.masterAlpha.value)
        mixerShader.setUniform("uBgAlpha", 1.0f)
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
            feedbackShader.dispose()
            mixerShader.dispose()
            blitShader.dispose()
            triPlanarShader.dispose()
            view2DShader.dispose()
            isDisposed = true
        }
    }
}
