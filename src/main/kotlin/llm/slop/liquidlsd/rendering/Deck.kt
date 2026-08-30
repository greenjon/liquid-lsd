package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter

import llm.slop.liquidlsd.parameters.ParameterOwner

/**
 * Represents a single visual rendering chain (Deck).
 * Manages its own offscreen Framebuffer Objects (FBOs) for ping-pong feedback effects,
 * as well as parameters that control the feedback loop.
 */
class Deck(
    var source: VisualSource,
    var width: Int = 1920,
    var height: Int = 1080
) : ParameterOwner {
    var isEmpty: Boolean = false

    // FBO for rendering the clean visual source output
    var cleanFBO = FBO(width, height)

    // Ping-pong feedback FBOs
    var fb1 = FBO(width, height)
    var fb2 = FBO(width, height)
    private var fbIndex = 0

    fun resize(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight) return
        width = newWidth
        height = newHeight
        cleanFBO.dispose()
        fb1.dispose()
        fb2.dispose()
        cleanFBO = FBO(width, height)
        fb1 = FBO(width, height)
        fb2 = FBO(width, height)
        fb1.clear(0f, 0f, 0f, 0f)
        fb2.clear(0f, 0f, 0f, 0f)
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fbIndex = 0
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
        fb1.clear(0f, 0f, 0f, 0f)
        fb2.clear(0f, 0f, 0f, 0f)
        cleanFBO.clear(0f, 0f, 0f, 0f)
        
        val initialId = (source as? DynamicVisualSource)?.id
        val registrySources = VisualSourceRegistry.availableSources
            .filter { it.id != initialId }
            .map { it.clone() }
        
        availableSources.add(source.clone())
        availableSources.addAll(registrySources)
        source = availableSources.first()
    }

    fun reset() {
        isEmpty = true
        availableSources.forEach { src ->
            src.parameters.values.forEach { it.reset() }
            src.globalAlpha.reset()
            src.clear()
        }
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

        // Clear FBOs to prevent rendering stale feedback
        fb1.clear(0f, 0f, 0f, 0f)
        fb2.clear(0f, 0f, 0f, 0f)
        cleanFBO.clear(0f, 0f, 0f, 0f)
    }

    /**
     * Retrieves the current history FBO (from the last frame).
     */
    fun getCurrentHistoryFBO(): FBO = if (fbIndex == 0) fb1 else fb2

    /**
     * Retrieves the final output texture of the Deck (the current history FBO texture).
     */
    fun getOutputTexture(): Int = getCurrentHistoryFBO().texture

    /**
     * Retrieves the target FBO for the new feedback combination.
     */
    fun getNextHistoryFBO(): FBO = if (fbIndex == 0) fb2 else fb1

    /**
     * Swaps the feedback FBO ping-pong index.
     */
    fun swapFeedbackBuffers() {
        fbIndex = 1 - fbIndex
    }

    /**
     * Updates the underlying visual source and evaluates feedback parameters.
     */
    fun update() {
        source.update()
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
        val allParams = mutableListOf<ModulatableParameter>()
        allParams.addAll(this.source.parameters.values)
        allParams.add(this.source.globalAlpha)
        allParams.add(this.fbDecay)
        allParams.add(this.fbGain)
        allParams.add(this.fbZoom)
        allParams.add(this.fbRotate)
        allParams.add(this.fbHueShift)
        allParams.add(this.fbBlur)
        allParams.add(this.fbChroma)
        allParams.add(this.fbMode)

        for (param in allParams) {
            val randomized = param.modulators.map { it.randomizeActiveValues() }
            param.modulators.clear()
            param.modulators.addAll(randomized)
            param.randomizeBaseValue()
        }
    }

    /**
     * Disposes all FBOs associated with this Deck.
     */
    fun dispose() {
        cleanFBO.dispose()
        fb1.dispose()
        fb2.dispose()
        // Note: `source` is always one of the entries in `availableSources`, so the
        // forEach below already disposes it. Do NOT call source.dispose() here — that
        // would double-free the active source's GPU objects.
        availableSources.forEach { it.dispose() }
    }

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        // Add all source parameters first (Mandala or DynamicVisualSource)
        list.addAll(source.getParameterPaths(prefix))

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
