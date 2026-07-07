package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.config.RuntimeConfig

/**
 * Represents a single visual rendering chain (Deck).
 * Manages its own offscreen Framebuffer Objects (FBOs) for ping-pong feedback effects,
 * as well as parameters that control the feedback loop.
 */
class Deck(
    var source: VisualSource,
    val width: Int = RuntimeConfig.Rendering.DEFAULT_WIDTH,
    val height: Int = RuntimeConfig.Rendering.DEFAULT_HEIGHT
) {
    var isEmpty: Boolean = false
    var lastSourceSelectBase: Float = 0.0f

    // FBO for rendering the clean visual source output
    val cleanFBO = FBO(width, height)

    // Ping-pong feedback FBOs
    val fb1 = FBO(width, height)
    val fb2 = FBO(width, height)
    private var fbIndex = 0

    // Source selection parameter
    val sourceSelect = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)

    // Keep instances of all visual sources
    val availableSources = mutableListOf<VisualSource>()

    // Feedback parameters with custom clamp ranges
    val fbDecay = ModulatableParameter(0.73f, minClamp = 0f, maxClamp = 1f)
    val fbGain = ModulatableParameter(1.0f, minClamp = 0f, maxClamp = 2f)
    val fbZoom = ModulatableParameter(0.0f, minClamp = -1f, maxClamp = 1f) // negative is zoom out, positive is zoom in
    val fbRotate = ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = llm.slop.spirals.parameters.MeterType.ENDLESS) // in radians
    val fbHueShift = ModulatableParameter(0.0f, minClamp = -1f, maxClamp = 1f, meterType = llm.slop.spirals.parameters.MeterType.ENDLESS) // range 0..1
    val fbBlur = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f) // range 0..1
    val fbChroma = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val fbMode = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f) // 0 = Max, 1 = Difference
    val fbKaleido = ModulatableParameter(1.0f, minClamp = 1f, maxClamp = 12f)

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
        
        updateSourceSelection()
        lastSourceSelectBase = sourceSelect.baseValue
    }

    /**
     * Resets all parameters to their defaults.
     */
    private fun updateSourceSelection() {
        val size = availableSources.size
        if (size == 0) return
        val index = (sourceSelect.value * size).toInt().coerceIn(0, size - 1)
        source = availableSources[index]
    }

    fun reset() {
        isEmpty = true
        sourceSelect.reset()
        lastSourceSelectBase = sourceSelect.baseValue
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
        updateSourceSelection()

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
     * Retrieves the final output texture of the Deck (reconstructed clean FBO if background is active,
     * otherwise the current history FBO texture).
     */
    fun getOutputTexture(): Int {
        val bgStyle = source.parameters["Bg Style"]?.value ?: 0f
        return if (bgStyle > 0.5f) {
            cleanFBO.texture
        } else {
            getCurrentHistoryFBO().texture
        }
    }

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
        val oldSelectValue = sourceSelect.value
        sourceSelect.evaluate()
        updateSourceSelection()
        val newSelectValue = sourceSelect.value

        if (isEmpty && sourceSelect.baseValue != lastSourceSelectBase) {
            isEmpty = false
        }

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
}
