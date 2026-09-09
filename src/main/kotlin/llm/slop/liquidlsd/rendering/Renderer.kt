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
    private val tetraKaleidoShader: Shader
    private val view2DShader: Shader

    private var isDisposed = false

    init {
        // Load the shaders
        feedbackShader = Shader.fromResources("shaders/blit.vert", "shaders/feedback.frag")
        mixerShader = Shader.fromResources("shaders/blit.vert", "shaders/mixer.frag")
        blitShader = Shader.fromResources("shaders/blit.vert", "shaders/blit.frag")
        triPlanarShader = Shader.fromResources("shaders/tri_planar.vert", "shaders/tri_planar.frag")
        tetraKaleidoShader = Shader.fromResources("shaders/tetra_kaleido.vert", "shaders/tetra_kaleido.frag")
        view2DShader = Shader.fromResources("shaders/blit.vert", "shaders/view2d.frag")
    }

    fun render(source: VisualSource, targetFBO: FBO) {
        if (source is ExternalVideoSource) {
            renderExternalVideoSource(source, targetFBO)
            return
        }
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
        source.shader.setUniform("RENDERSIZE",   targetFBO.width.toFloat(), targetFBO.height.toFloat())
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

    private fun renderExternalVideoSource(source: ExternalVideoSource, targetFBO: FBO) {
        targetFBO.bind()
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        val tex = source.currentTextureId
        if (tex != 0) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            blitShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, tex)
            blitShader.setUniform("uTexture", 0)

            Geometry.drawFullscreenQuad()

            blitShader.unbind()
            glActiveTexture(GL_TEXTURE0)
        }
        targetFBO.unbind()
    }

    /**
     * Renders a Deck's visual source and updates its ping-pong feedback loop.
     */
    fun renderDeck(deck: Deck) {
        if (deck.isEmpty) {
            return
        }
        // 1. Render clean source image
        val is3D = !deck.source.is3D && deck.view3DMode.value >= 0.5f
        if (!is3D) {
            // Render 2D source or native 3D source into rawSource2DFBO (widescreen native resolution)
            render(deck.source, deck.rawSource2DFBO)

            // Render transformed view onto cleanFBO.
            // Native 3D sources handle their own camera Zoom and 3D rotation internally;
            // for 3D sources we pass 1.0 Zoom and 0.0 RotateZ to blit 1:1 without compounding 2D canvas transforms.
            deck.cleanFBO.bind()
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)

            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            view2DShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, deck.rawSource2DFBO.texture)
            view2DShader.setUniform("uTexture", 0)

            val zoom = if (deck.source.is3D) 1.0f else deck.viewZoom.value
            val rotZ = if (deck.source.is3D) 0.0f else deck.viewRotateZ.value
            view2DShader.setUniform("uZoom", zoom)
            view2DShader.setUniform("uRotateZ", rotZ)
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

            val modeVal = deck.view3DMode.value.roundToInt()
            val aspect = deck.cleanFBO.width.toFloat() / deck.cleanFBO.height.toFloat()

            if (modeVal == 4) {
                // Mode 4: Tetrahedral Kaleidoscope (24-Chamber Space Folding)
                tetraKaleidoShader.bind()
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, deck.rawSourceFBO.texture)
                tetraKaleidoShader.setUniform("uTexture", 0)

                tetraKaleidoShader.setUniform("uPitch", deck.viewRotateX.value)
                tetraKaleidoShader.setUniform("uYaw", deck.viewRotateY.value)
                tetraKaleidoShader.setUniform("uRoll", deck.viewRotateZ.value)
                tetraKaleidoShader.setUniform("uZoom", deck.viewZoom.value)
                tetraKaleidoShader.setUniform("uPersp", deck.viewPersp.value)
                tetraKaleidoShader.setUniform("uSeparation", deck.viewSeparation.value)
                tetraKaleidoShader.setUniform("uDepthDim", deck.viewDepthDim.value)
                tetraKaleidoShader.setUniform("uAlpha", deck.source.globalAlpha.value)
                tetraKaleidoShader.setUniform("uBlendAdditive", if (isAdditive) 1.0f else 0.0f)
                tetraKaleidoShader.setUniform("uAspectRatio", aspect)
                tetraKaleidoShader.setUniform("uRoundness", deck.viewRoundness.value)

                Geometry.drawFullscreenQuad()

                tetraKaleidoShader.unbind()
                deck.cleanFBO.unbind()
                glActiveTexture(GL_TEXTURE0)
            } else {
                // Modes 1..3: Tri-Planar / Cube Cage / Hex-Planar instanced planes
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
                triPlanarShader.setUniform("uAspectRatio", aspect)
                triPlanarShader.setUniform("uRoundness", deck.viewRoundness.value)
                triPlanarShader.setUniform("u3DMode", modeVal)

                val numInstances = if (modeVal == 3 || modeVal == 2) 6 else 3

                glBindVertexArray(Geometry.getFullscreenQuad())
                glDrawArraysInstanced(GL_TRIANGLES, 0, 6, numInstances)
                glBindVertexArray(0)

                triPlanarShader.unbind()
                deck.cleanFBO.unbind()
                glActiveTexture(GL_TEXTURE0)
            }
        }

        // --- Dual FX Filter Stages ---
        // Stage 1: FX Slot 1 (Color / Degradation)
        val fx1 = deck.fxSlot1
        val texAfterFx1 = if (fx1 != null && fx1.enabled && fx1.dryWet.value > 0.0f) {
            deck.fxFBO1.bind()
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_BLEND)

            fx1.render(deck.cleanFBO.texture, deck.fxFBO1.width, deck.fxFBO1.height)

            val dryWet = fx1.dryWet.value
            if (dryWet < 1.0f) {
                // Blend dry (cleanFBO) with wet (fxFBO1)
                glEnable(GL_BLEND)
                glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA)
                glBlendColor(0f, 0f, 0f, 1.0f - dryWet) // dry amount

                blitShader.bind()
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, deck.cleanFBO.texture)
                blitShader.setUniform("uTexture", 0)
                Geometry.drawFullscreenQuad()
                blitShader.unbind()
                glDisable(GL_BLEND)
            }

            deck.fxFBO1.unbind()
            deck.fxFBO1.texture
        } else {
            deck.cleanFBO.texture
        }

        // Stage 2: FX Slot 2 (Spatial / Distortion)
        val fx2 = deck.fxSlot2
        val liveTexture = if (fx2 != null && fx2.enabled && fx2.dryWet.value > 0.0f) {
            deck.fxFBO2.bind()
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_BLEND)

            fx2.render(texAfterFx1, deck.fxFBO2.width, deck.fxFBO2.height)

            val dryWet = fx2.dryWet.value
            if (dryWet < 1.0f) {
                // Blend dry (texAfterFx1) with wet (fxFBO2)
                glEnable(GL_BLEND)
                glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA)
                glBlendColor(0f, 0f, 0f, 1.0f - dryWet) // dry amount

                blitShader.bind()
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, texAfterFx1)
                blitShader.setUniform("uTexture", 0)
                Geometry.drawFullscreenQuad()
                blitShader.unbind()
                glDisable(GL_BLEND)
            }

            deck.fxFBO2.unbind()
            deck.fxFBO2.texture
        } else {
            texAfterFx1
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
        glBindTexture(GL_TEXTURE_2D, liveTexture)
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

        val activeTransition = mixer.transitionFilter?.takeIf { it.enabled }
        val progress = (mixer.crossfade.value + 1.0f) / 2.0f

        if (activeTransition != null) {
            // Pass 1: Render ISF Transition Shader into intermediate blendFBO
            mixer.blendFBO.bind()
            glViewport(0, 0, mixer.width, mixer.height)
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)

            activeTransition.renderTransition(
                startTexture = mixer.deckA.getOutputTexture(),
                endTexture = mixer.deckB.getOutputTexture(),
                progressValue = progress,
                width = mixer.width,
                height = mixer.height
            )

            // Pass 2: Composite blended result with Deck BG, level multipliers, bloom & master alpha
            mixer.masterFBO.bind()
            glViewport(0, 0, mixer.width, mixer.height)
            glClearColor(0f, 0f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT)

            mixerShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, mixer.blendFBO.texture)
            mixerShader.setUniform("uTex1", 0)

            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mixer.deckB.getOutputTexture())
            mixerShader.setUniform("uTex2", 1)

            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, mixer.deckBG.getOutputTexture())
            mixerShader.setUniform("uTexBG", 2)

            mixerShader.setUniform("uMode", 4) // XFADE
            mixerShader.setUniform("uBalance", 0.0f) // 100% blendFBO
            mixerShader.setUniform("uAlpha", mixer.masterAlpha.value)
            mixerShader.setUniform("uBgAlpha", 1.0f)
            mixerShader.setUniform("uBloom", mixer.bloom.value)
            mixerShader.setUniform("uLevelA", mixer.levelA)
            mixerShader.setUniform("uLevelB", mixer.levelB)
            mixerShader.setUniform("uLevelBG", mixer.levelBG)
            mixerShader.setUniform("uMasterLevel", mixer.masterLevel)

            Geometry.drawFullscreenQuad()
            mixerShader.unbind()
        } else {
            // Fallback non-ISF mixer: Built-in blend modes pass
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
            mixerShader.setUniform("uBalance", progress)
            mixerShader.setUniform("uAlpha", mixer.masterAlpha.value)
            mixerShader.setUniform("uBgAlpha", 1.0f)
            mixerShader.setUniform("uBloom", mixer.bloom.value)
            mixerShader.setUniform("uLevelA", mixer.levelA)
            mixerShader.setUniform("uLevelB", mixer.levelB)
            mixerShader.setUniform("uLevelBG", mixer.levelBG)
            mixerShader.setUniform("uMasterLevel", mixer.masterLevel)

            // Blit mixed output
            Geometry.drawFullscreenQuad()

            mixerShader.unbind()
        }

        mixer.masterFBO.unbind()
    }

    /**
     * Rescales a source texture into a destination FBO using a specified scaling mode.
     */
    fun rescale(srcTex: Int, srcW: Int, srcH: Int, destFbo: FBO, mode: llm.slop.liquidlsd.ui.UITheme.OutputScaleMode) {
        destFbo.bind()
        val vp = ViewportHelper.computeViewport(destFbo.width, destFbo.height, srcW, srcH, mode)
        glViewport(vp.x, vp.y, vp.width, vp.height)
        
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        
        glDisable(GL_BLEND)
        blitShader.bind()
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, srcTex)
        blitShader.setUniform("uTexture", 0)
        Geometry.drawFullscreenQuad()
        blitShader.unbind()
        
        destFbo.unbind()
        glActiveTexture(GL_TEXTURE0)
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
            tetraKaleidoShader.dispose()
            view2DShader.dispose()
            isDisposed = true
        }
    }
}
