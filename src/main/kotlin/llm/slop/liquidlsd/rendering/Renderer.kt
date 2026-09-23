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

    private val mixerShader: Shader
    val blitShader: Shader
    private val view2DShader: Shader
    val audioTexture: AudioTexture = AudioTexture()
    private var renderFrameIndex: Int = 0
    private var lastRenderTime: Float = 0f

    private var isDisposed = false

    init {
        // Load the shaders
        mixerShader = Shader.fromResources("shaders/blit.vert", "shaders/mixer.frag")
        mixerShader.bind()
        mixerShader.setUniform("uZoom", 1.0f)
        mixerShader.setUniform("uRotateZ", 0.0f)
        mixerShader.setUniform("uAspectRatio", 1.0f)
        mixerShader.unbind()

        blitShader = Shader.fromResources("shaders/blit.vert", "shaders/blit.frag")
        blitShader.bind()
        blitShader.setUniform("uZoom", 1.0f)
        blitShader.setUniform("uRotateZ", 0.0f)
        blitShader.setUniform("uAspectRatio", 1.0f)
        blitShader.unbind()

        view2DShader = Shader.fromResources("shaders/blit.vert", "shaders/view2d.frag")
    }

    fun render(source: VisualSource, targetFBO: FBO, zoom: Float = 1.0f, rotZ: Float = 0.0f) {
        if (source is ExternalVideoSource) {
            renderExternalVideoSource(source, targetFBO, zoom, rotZ)
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

        // Common universal uniforms for all sources (ISF, Shadertoy, GLSL Sandbox, Audio)
        val aspect = targetFBO.width.toFloat() / targetFBO.height.toFloat()
        val widthF = targetFBO.width.toFloat()
        val heightF = targetFBO.height.toFloat()
        val currentTime = TimeSource.getTimeSec().toFloat()
        val deltaTime = if (lastRenderTime > 0f) currentTime - lastRenderTime else 0.01666f
        lastRenderTime = currentTime
        val frameIdx = renderFrameIndex++

        // Dimensions & Aspect
        source.shader.setUniform("uAlpha",       source.globalAlpha.value)
        source.shader.setUniform("uResolution",  widthF, heightF)
        source.shader.setUniform("RENDERSIZE",   widthF, heightF)
        source.shader.setUniform("u_resolution", widthF, heightF)
        source.shader.setUniform("resolution",   widthF, heightF)
        source.shader.setUniform("iResolution",  widthF, heightF, 1.0f)
        source.shader.setUniform("uAspectRatio", aspect)
        source.shader.setUniform("uZoom",        zoom)
        source.shader.setUniform("uRotateZ",     rotZ)

        // Time & Clocks
        source.shader.setUniform("uTime",        currentTime)
        source.shader.setUniform("TIME",         currentTime)
        source.shader.setUniform("iTime",        currentTime)
        source.shader.setUniform("u_time",       currentTime)
        source.shader.setUniform("time",         currentTime)
        source.shader.setUniform("TIMEDELTA",    deltaTime)
        source.shader.setUniform("iTimeDelta",   deltaTime)
        source.shader.setUniform("u_delta",      deltaTime)

        // Frame Indices
        source.shader.setUniform("FRAMEINDEX",   frameIdx)
        source.shader.setUniform("iFrame",       frameIdx)
        source.shader.setUniform("u_frame",      frameIdx)
        val fps = try { imgui.ImGui.getIO().framerate } catch (_: Exception) { 60.0f }
        source.shader.setUniform("iFrameRate",   if (fps > 0f) fps else 60.0f)

        // Date derivation (zero allocation)
        val epochMs = System.currentTimeMillis()
        val epochSec = epochMs / 1000L
        val secondsSinceMidnight = (epochSec % 86400L).toFloat() + ((epochMs % 1000L) / 1000f)
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
        val dayNum = rem1 - monthStarts[monthNum] + 1
        source.shader.setUniform("DATE",  yearNum.toFloat(), (monthNum + 1).toFloat(), dayNum.toFloat(), secondsSinceMidnight)
        source.shader.setUniform("iDate", yearNum.toFloat(), (monthNum + 1).toFloat(), dayNum.toFloat(), secondsSinceMidnight)

        // Mouse & Interaction
        var mouseX = 0f
        var mouseY = 0f
        var isMouseDown = false
        try {
            val io = imgui.ImGui.getIO()
            mouseX = io.mousePos.x
            mouseY = io.mousePos.y
            isMouseDown = imgui.ImGui.isMouseDown(0)
        } catch (_: Exception) {}
        val glMouseY = (heightF - mouseY).coerceAtLeast(0f)
        val normMouseX = (mouseX / widthF).coerceIn(0f, 1f)
        val normMouseY = (glMouseY / heightF).coerceIn(0f, 1f)
        val clickX = if (isMouseDown) mouseX else 0f
        val clickY = if (isMouseDown) glMouseY else 0f
        source.shader.setUniform("u_mouse", normMouseX, normMouseY)
        source.shader.setUniform("mouse",   normMouseX, normMouseY)
        source.shader.setUniform("iMouse",  mouseX, glMouseY, clickX, clickY)

        // Real-Time Audio (VJ Standard)
        val audioVol = llm.slop.liquidlsd.cv.CVRegistry.get("amp")
        val audioBass = llm.slop.liquidlsd.cv.CVRegistry.get("bass")
        val audioMid = llm.slop.liquidlsd.cv.CVRegistry.get("mid")
        val audioTreble = llm.slop.liquidlsd.cv.CVRegistry.get("high")
        source.shader.setUniform("audioVolume", audioVol)
        source.shader.setUniform("audioBass",   audioBass)
        source.shader.setUniform("audioMid",    audioMid)
        source.shader.setUniform("audioTreble", audioTreble)

        // Update Audio FFT Texture and bind to unit 5
        audioTexture.updateFromAudioEngine(llm.slop.liquidlsd.audio.AudioEngine)
        audioTexture.bind(5)
        source.shader.setUniform("audioFFT", 5)
        source.shader.setUniform("iChannel0", 5)

        if (hasFb) {
            source.shader.setUniform("src", 0)
        }

        source.renderTopology(renderTarget)

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
        glActiveTexture(GL_TEXTURE0)
    }

    private fun renderExternalVideoSource(source: ExternalVideoSource, targetFBO: FBO, zoom: Float = 1.0f, rotZ: Float = 0.0f) {
        targetFBO.bind()
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)

        val tex = source.currentTextureId
        if (tex != 0) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            view2DShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, tex)
            view2DShader.setUniform("uTexture", 0)
            view2DShader.setUniform("uZoom", zoom)
            view2DShader.setUniform("uRotateZ", rotZ)
            val aspect = targetFBO.width.toFloat() / targetFBO.height.toFloat()
            view2DShader.setUniform("uAspectRatio", aspect)

            Geometry.drawFullscreenQuad()

            view2DShader.unbind()
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
        val zoom = if (deck.source.is3D) 1.0f else deck.viewZoom.value
        val rotZ = if (deck.source.is3D) 0.0f else deck.viewRotateZ.value
        render(deck.source, deck.cleanFBO, zoom, rotZ)

        // 2. Render Deck's dedicated FX chain (3 slots) if enabled and wet > 0.
        val chain = deck.fxChain
        val deckWetAmount = chain.dryWet.value
        val hasActiveSlots = chain.slots.any { it != null && it.enabled && it.dryWet.value > 0.0f }

        val finalTex = if (chain.enabled && deckWetAmount > 0.0f && hasActiveSlots) {
            renderFxChainPass(
                cleanTex = deck.cleanFBO.texture,
                chain = chain,
                width = deck.width,
                height = deck.height,
                pingFBO = deck.fxPingFBO,
                pongFBO = deck.fxPongFBO,
                outFBO = deck.fxBankOutFBO
            )
        } else {
            deck.cleanFBO.texture
        }
        deck.activeOutputTexture = finalTex
    }

    private var fallbackTransition: llm.slop.liquidlsd.rendering.isf.ISFFilter? = null

    /**
     * Composites Deck A and Deck B outputs into the Mixer's master output FBO.
     */
    fun renderMixer(mixer: Mixer) {
        val activeTransition = mixer.transitionFilter
        val progress = (mixer.crossfade.value + 1.0f) / 2.0f

        // Pass 1: Render ISF Transition Shader into intermediate blendFBO
        mixer.blendFBO.bind()
        glViewport(0, 0, mixer.width, mixer.height)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_BLEND)

        if (activeTransition != null && activeTransition.enabled) {
            activeTransition.renderTransition(
                startTexture = mixer.deckA.getOutputTexture(),
                endTexture = mixer.deckB.getOutputTexture(),
                progressValue = progress,
                width = mixer.width,
                height = mixer.height
            )
        } else {
            val fallback = fallbackTransition ?: llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.createTransition("linear_crossfade")?.also {
                fallbackTransition = it
            }
            fallback?.renderTransition(
                startTexture = mixer.deckA.getOutputTexture(),
                endTexture = mixer.deckB.getOutputTexture(),
                progressValue = progress,
                width = mixer.width,
                height = mixer.height
            )
        }

        mixer.blendFBO.unbind()

        // Pass 2: Composite blended result with Deck BG, bloom & master alpha into masterCompositeFBO
        mixer.masterCompositeFBO.bind()
        glViewport(0, 0, mixer.width, mixer.height)
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_BLEND)

        mixerShader.bind()
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, mixer.blendFBO.texture)
        mixerShader.setUniform("uTex1", 0)

        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, mixer.deckBG.getOutputTexture())
        mixerShader.setUniform("uTexBG", 1)

        mixerShader.setUniform("uProgress", progress)
        mixerShader.setUniform("uLevelA", mixer.levelA.value)
        mixerShader.setUniform("uLevelB", mixer.levelB.value)
        mixerShader.setUniform("uLevelBG", mixer.levelBG.value)
        mixerShader.setUniform("uMasterLevel", mixer.masterLevel.value)
        mixerShader.setUniform("uBgAlpha", 1.0f)

        Geometry.drawFullscreenQuad()

        mixerShader.unbind()
        mixer.masterCompositeFBO.unbind()
        glActiveTexture(GL_TEXTURE0)

        // Pass 3: Master FX active chain processing (3 slots)
        val masterFxWet = mixer.masterFxBank.masterWetDry.value
        val masterFinalTex = if (mixer.masterFxBank.enabled && masterFxWet > 0.0f) {
            renderFxBankPass(
                cleanTex = mixer.masterCompositeFBO.texture,
                bank = mixer.masterFxBank,
                masterWetAmount = masterFxWet,
                width = mixer.width,
                height = mixer.height,
                pingFBO = mixer.masterFxPingFBO,
                pongFBO = mixer.masterFxPongFBO,
                bankOutFBO = mixer.masterFxBankOutFBO
            )
        } else {
            mixer.masterCompositeFBO.texture
        }

        // Final Target Blit into masterFBO
        mixer.masterFBO.bind()
        glViewport(0, 0, mixer.width, mixer.height)
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_BLEND)

        blitShader.bind()
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, masterFinalTex)
        blitShader.setUniform("uTexture", 0)
        Geometry.drawFullscreenQuad()

        blitShader.unbind()
        mixer.masterFBO.unbind()
        glActiveTexture(GL_TEXTURE0)
    }

    /**
     * Renders a dedicated [FxChain] (3 serial slots).
     * [pingFBO]/[pongFBO] ping-pong slot-to-slot passes within the chain; [outFBO] holds the
     * final dry/wet blend against [cleanTex].
     */
    fun renderFxChainPass(
        cleanTex: Int,
        chain: FxChain,
        width: Int,
        height: Int,
        pingFBO: FBO,
        pongFBO: FBO,
        outFBO: FBO
    ): Int {
        if (!chain.enabled || chain.dryWet.value <= 0.0f ||
            chain.slots.all { it == null || !it.enabled || it.dryWet.value <= 0.0f }) {
            return cleanTex
        }

        var slotInputTex = cleanTex
        var writeFBO = pingFBO
        var readFBO = pongFBO

        for (slot in chain.slots) {
            if (slot == null || !slot.enabled || slot.dryWet.value <= 0.0f) continue

            writeFBO.bind()
            glViewport(0, 0, width, height)
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_BLEND)

            slot.render(slotInputTex, width, height)

            val dryWet = slot.dryWet.value
            if (dryWet < 1.0f) {
                glEnable(GL_BLEND)
                glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA)
                glBlendColor(0f, 0f, 0f, 1.0f - dryWet)

                blitShader.bind()
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, slotInputTex)
                blitShader.setUniform("uTexture", 0)
                Geometry.drawFullscreenQuad()
                blitShader.unbind()
                glDisable(GL_BLEND)
            }

            writeFBO.unbind()
            slotInputTex = writeFBO.texture

            // Swap scratch buffers for the next slot
            val temp = writeFBO
            writeFBO = readFBO
            readFBO = temp
        }

        // Chain wet output is in slotInputTex. Blend with cleanTex using chain.dryWet into outFBO.
        val chainWet = chain.dryWet.value
        outFBO.bind()
        glViewport(0, 0, width, height)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_BLEND)

        blitShader.bind()
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, slotInputTex)
        blitShader.setUniform("uTexture", 0)
        Geometry.drawFullscreenQuad()

        if (chainWet < 1.0f) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA)
            glBlendColor(0f, 0f, 0f, 1.0f - chainWet)

            glBindTexture(GL_TEXTURE_2D, cleanTex)
            Geometry.drawFullscreenQuad()
            glDisable(GL_BLEND)
        }
        blitShader.unbind()
        outFBO.unbind()

        return outFBO.texture
    }

    /**
     * Renders the single active [FxChain] of an [FxBank] (see [FxBank.activeChainIndex]).
     * [pingFBO]/[pongFBO] ping-pong slot-to-slot passes within the chain; [bankOutFBO] holds the
     * chain-level dry/wet blend. The final overall dry/wet blend against [cleanTex] reuses
     * [pingFBO] as scratch (free once the slot loop finishes) rather than a 4th dedicated buffer.
     */
    private fun renderFxBankPass(
        cleanTex: Int,
        bank: FxBank,
        masterWetAmount: Float,
        width: Int,
        height: Int,
        pingFBO: FBO,
        pongFBO: FBO,
        bankOutFBO: FBO
    ): Int {
        if (!bank.enabled || masterWetAmount <= 0.0f) {
            return cleanTex
        }

        val chain = bank.activeChain
        if (!chain.enabled || chain.dryWet.value <= 0.0f ||
            chain.slots.all { it == null || !it.enabled || it.dryWet.value <= 0.0f }) {
            return cleanTex
        }

        var slotInputTex = cleanTex
        var writeFBO = pingFBO
        var readFBO = pongFBO

        for (slot in chain.slots) {
            if (slot == null || !slot.enabled || slot.dryWet.value <= 0.0f) continue

            writeFBO.bind()
            glViewport(0, 0, width, height)
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_BLEND)

            slot.render(slotInputTex, width, height)

            val dryWet = slot.dryWet.value
            if (dryWet < 1.0f) {
                glEnable(GL_BLEND)
                glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA)
                glBlendColor(0f, 0f, 0f, 1.0f - dryWet)

                blitShader.bind()
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, slotInputTex)
                blitShader.setUniform("uTexture", 0)
                Geometry.drawFullscreenQuad()
                blitShader.unbind()
                glDisable(GL_BLEND)
            }

            writeFBO.unbind()
            slotInputTex = writeFBO.texture

            // Swap scratch buffers for the next slot
            val temp = writeFBO
            writeFBO = readFBO
            readFBO = temp
        }

        // Chain wet output is in slotInputTex. Blend with cleanTex using chain.dryWet into bankOutFBO.
        val chainWet = chain.dryWet.value
        bankOutFBO.bind()
        glViewport(0, 0, width, height)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_BLEND)

        blitShader.bind()
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, slotInputTex)
        blitShader.setUniform("uTexture", 0)
        Geometry.drawFullscreenQuad()

        if (chainWet < 1.0f) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA)
            glBlendColor(0f, 0f, 0f, 1.0f - chainWet)

            glBindTexture(GL_TEXTURE_2D, cleanTex)
            Geometry.drawFullscreenQuad()
            glDisable(GL_BLEND)
        }
        blitShader.unbind()
        bankOutFBO.unbind()

        // Apply overall master dry/wet blend against cleanTex, writing into pingFBO (free scratch).
        return if (masterWetAmount < 1.0f) {
            pingFBO.bind()
            glViewport(0, 0, width, height)
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_BLEND)

            blitShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, bankOutFBO.texture)
            blitShader.setUniform("uTexture", 0)
            Geometry.drawFullscreenQuad()

            glEnable(GL_BLEND)
            glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA)
            glBlendColor(0f, 0f, 0f, 1.0f - masterWetAmount)

            glBindTexture(GL_TEXTURE_2D, cleanTex)
            Geometry.drawFullscreenQuad()
            blitShader.unbind()
            glDisable(GL_BLEND)

            pingFBO.unbind()
            pingFBO.texture
        } else {
            bankOutFBO.texture
        }
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
            fallbackTransition?.dispose()
            fallbackTransition = null
            mixerShader.dispose()
            blitShader.dispose()
            view2DShader.dispose()
            audioTexture.dispose()
            isDisposed = true
        }
    }

    companion object {
        private val MONTH_STARTS_NORMAL = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334,365)
        private val MONTH_STARTS_LEAP   = intArrayOf(0,31,60,91,121,152,182,213,244,274,305,335,366)
    }
}

