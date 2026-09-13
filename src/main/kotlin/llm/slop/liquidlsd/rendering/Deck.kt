package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter

import llm.slop.liquidlsd.parameters.ParameterOwner

/**
 * Represents a single visual rendering chain (Deck).
 * Manages its own offscreen Framebuffer Objects (FBOs) for ping-pong feedback effects,
 * as well as parameters that control the feedback loop.
 */
class Deck(
    initialSource: VisualSource,
    var width: Int = 1920,
    var height: Int = 1080,
    var isEmpty: Boolean = true
) : ParameterOwner {

    var source: VisualSource = initialSource
        set(value) {
            field = value
            if (value.is3D) {
                view3DMode.reset()
            }
            llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
            llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
        }

    // FBO for rendering the clean visual source output
    var cleanFBO = FBO(width, height)

    // ISF Filter Slot 1 & Slot 2
    var fxSlot1: llm.slop.liquidlsd.rendering.isf.ISFFilter? = null
    var fxFBO1 = FBO(width, height)

    var fxSlot2: llm.slop.liquidlsd.rendering.isf.ISFFilter? = null
    var fxFBO2 = FBO(width, height)

    fun resize(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight) return
        width = newWidth
        height = newHeight
        cleanFBO.dispose()
        fxFBO1.dispose()
        fxFBO2.dispose()
        cleanFBO = FBO(width, height)
        fxFBO1 = FBO(width, height)
        fxFBO2 = FBO(width, height)
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxFBO1.clear(0f, 0f, 0f, 0f)
        fxFBO2.clear(0f, 0f, 0f, 0f)
        availableSources.forEach { src ->
            if (src is DynamicVisualSource) {
                src.fb1?.dispose()
                src.fb2?.dispose()
                src.fb1 = null
                src.fb2 = null
                src.fbIndex = 0
            }
        }
    }

    // Keep instances of all visual sources
    val availableSources = mutableListOf<VisualSource>()

    // 3D View parameters (universal for 2D visual sources)
    val view3DMode = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 4f) // 0 = 2D Flat, 1 = Tri-Axial (90°), 2 = Cube Cage, 3 = Hex-Planar (60°), 4 = Tetra Kaleido
    val viewZoom = ModulatableParameter(1.0f, minClamp = 0.1f, maxClamp = 5.0f)
    val viewRotateX = ModulatableParameter(0.0f, minClamp = -3.14159f, maxClamp = 3.14159f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS, explicitIsAngle = true)
    val viewRotateY = ModulatableParameter(0.0f, minClamp = -3.14159f, maxClamp = 3.14159f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS, explicitIsAngle = true)
    val viewRotateZ = ModulatableParameter(0.0f, minClamp = -3.14159f, maxClamp = 3.14159f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS, explicitIsAngle = true)
    val viewPersp = ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 1.0f)
    val viewDepthDim = ModulatableParameter(0.5f, minClamp = 0.0f, maxClamp = 1.0f)
    val viewSeparation = ModulatableParameter(0.0f, minClamp = 0.0f, maxClamp = 1.0f)
    val viewBlendMode = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f) // 1.0 = Additive, 0.0 = Alpha
    val viewRoundness = ModulatableParameter(1.0f, minClamp = 0.0f, maxClamp = 1.0f) // 1.0 = Circular Disc, 0.0 = Square Quad

    // Feedback parameters with custom clamp ranges
    val fbDecay = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val fbGain = ModulatableParameter(1.0f, minClamp = 0f, maxClamp = 2f)
    val fbZoom = ModulatableParameter(0.0f, minClamp = -1f, maxClamp = 1f) // negative is zoom out, positive is zoom in
    val fbRotate = ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS, explicitIsAngle = true) // in radians
    val fbHueShift = ModulatableParameter(0.0f, minClamp = -1f, maxClamp = 1f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS) // range 0..1
    val fbBlur = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f) // range 0..1
    val fbChroma = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val fbMode = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f) // 0 = Max, 1 = Difference
    val fbKaleido = ModulatableParameter(1.0f, minClamp = 1f, maxClamp = 12f)

    companion object {
        // Registration moved to getParameterPaths
    }

    init {
        // Clear all FBOs at startup to prevent reading uninitialized GPU memory
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxFBO1.clear(0f, 0f, 0f, 0f)
        fxFBO2.clear(0f, 0f, 0f, 0f)
        
        val initialId = (initialSource as? DynamicVisualSource)?.id
        val registrySources = VisualSourceRegistry.availableSources
            .filter { it.id != initialId }
            .map { it.clone() }
        
        availableSources.add(initialSource.clone())
        availableSources.addAll(registrySources)
        this.source = availableSources.first()
    }

    fun reset() {
        isEmpty = true
        fxSlot1?.reset()
        fxSlot2?.reset()
        availableSources.forEach { src ->
            src.parameters.values.forEach { it.reset() }
            src.globalAlpha.reset()
            src.clear()
        }
        view3DMode.reset()
        viewZoom.reset()
        viewRotateX.reset()
        viewRotateY.reset()
        viewRotateZ.reset()
        viewPersp.reset()
        viewDepthDim.reset()
        viewSeparation.reset()
        viewBlendMode.reset()
        viewRoundness.reset()

        fbDecay.reset()
        fbGain.reset()
        fbZoom.reset()
        fbRotate.reset()
        fbHueShift.reset()
        fbBlur.reset()
        fbChroma.reset()
        fbMode.reset()
        fbKaleido.reset()
        source.clear()

        // Clear active FBOs
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxFBO1.clear(0f, 0f, 0f, 0f)
        fxFBO2.clear(0f, 0f, 0f, 0f)
        morphController.initFromCurrentState()
    }

    /**
     * Retrieves all randomizable parameters across the visual source, 3D view, and feedback system.
     */
    fun getAllRandomizableParameters(): List<ModulatableParameter> {
        val allParams = mutableListOf<ModulatableParameter>()
        allParams.addAll(this.source.parameters.values)
        allParams.add(this.source.globalAlpha)
        
        fxSlot1?.let { fx ->
            if (fx.enabled) {
                allParams.add(fx.dryWet)
                allParams.addAll(fx.parameters.values)
            }
        }
        
        fxSlot2?.let { fx ->
            if (fx.enabled) {
                allParams.add(fx.dryWet)
                allParams.addAll(fx.parameters.values)
            }
        }
        
        allParams.add(this.view3DMode)
        allParams.add(this.viewZoom)
        allParams.add(this.viewRotateX)
        allParams.add(this.viewRotateY)
        allParams.add(this.viewRotateZ)
        allParams.add(this.viewPersp)
        allParams.add(this.viewDepthDim)
        allParams.add(this.viewSeparation)
        allParams.add(this.viewBlendMode)
        allParams.add(this.viewRoundness)
        allParams.add(this.fbDecay)
        allParams.add(this.fbGain)
        allParams.add(this.fbZoom)
        allParams.add(this.fbRotate)
        allParams.add(this.fbHueShift)
        allParams.add(this.fbBlur)
        allParams.add(this.fbChroma)
        allParams.add(this.fbMode)
        allParams.add(this.fbKaleido)
        return allParams
    }

    val morphController = DeckMorphController(::getAllRandomizableParameters)

    /**
     * Retrieves the final output texture of the Deck (the active stage texture).
     */
    fun getOutputTexture(): Int {
        val fx2 = fxSlot2
        if (fx2 != null && fx2.enabled && fx2.dryWet.value > 0.0f) {
            return fxFBO2.texture
        }
        val fx1 = fxSlot1
        if (fx1 != null && fx1.enabled && fx1.dryWet.value > 0.0f) {
            return fxFBO1.texture
        }
        return cleanFBO.texture
    }

    /**
     * Updates the underlying visual source and evaluates view and feedback parameters.
     */
    fun update() {
        source.update()
        fxSlot1?.update()
        fxSlot2?.update()
        view3DMode.evaluate()
        viewZoom.evaluate()
        viewRotateX.evaluate()
        viewRotateY.evaluate()
        viewRotateZ.evaluate()
        viewPersp.evaluate()
        viewDepthDim.evaluate()
        viewSeparation.evaluate()
        viewBlendMode.evaluate()
        viewRoundness.evaluate()

        fbDecay.evaluate()
        fbGain.evaluate()
        fbZoom.evaluate()
        fbRotate.evaluate()
        fbHueShift.evaluate()
        fbBlur.evaluate()
        fbChroma.evaluate()
        fbMode.evaluate()
        fbKaleido.evaluate()
    }

    /**
     * Re-randomizes modulators and base values for all randomizable parameters in this Deck.
     */
    fun randomizeModulators() {
        morphController.forceRandomize()
    }

    /**
     * Disposes all FBOs associated with this Deck.
     */
    fun dispose() {
        cleanFBO.dispose()
        fxFBO1.dispose()
        fxFBO2.dispose()
        fxSlot1?.dispose()
        fxSlot2?.dispose()
        // Note: `source` is always one of the entries in `availableSources`, so the
        // forEach below already disposes it. Do NOT call source.dispose() here — that
        // would double-free the active source's GPU objects.
        availableSources.forEach { it.dispose() }
    }

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        // Add all source parameters first (Mandala or DynamicVisualSource)
        list.addAll(source.getParameterPaths(prefix))

        // Add FX parameters
        fxSlot1?.getParameterPaths("$prefix/FX1")?.let { list.addAll(it) }
        fxSlot2?.getParameterPaths("$prefix/FX2")?.let { list.addAll(it) }

        // Add Deck's View parameters
        list.add("$prefix/View/3DMode" to view3DMode)
        list.add("$prefix/View/Zoom" to viewZoom)
        list.add("$prefix/View/RotateX" to viewRotateX)
        list.add("$prefix/View/RotateY" to viewRotateY)
        list.add("$prefix/View/RotateZ" to viewRotateZ)
        list.add("$prefix/View/Persp" to viewPersp)
        list.add("$prefix/View/DepthDim" to viewDepthDim)
        list.add("$prefix/View/Separation" to viewSeparation)
        list.add("$prefix/View/BlendMode" to viewBlendMode)
        list.add("$prefix/View/Roundness" to viewRoundness)

        // Add Deck's own feedback parameters
        list.add("$prefix/FB/Decay" to fbDecay)
        list.add("$prefix/FB/Gain" to fbGain)
        list.add("$prefix/FB/Zoom" to fbZoom)
        list.add("$prefix/FB/Rotate" to fbRotate)
        list.add("$prefix/FB/HueShift" to fbHueShift)
        list.add("$prefix/FB/Blur" to fbBlur)
        list.add("$prefix/FB/Chroma" to fbChroma)
        list.add("$prefix/FB/Mode" to fbMode)
        list.add("$prefix/FB/Kaleido" to fbKaleido)
        
        return list
    }
}
