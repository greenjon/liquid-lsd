package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter

/**
 * Represents a single visual rendering chain (Deck).
 * Manages its own offscreen Framebuffer Objects (FBOs) for ping-pong feedback effects,
 * as well as parameters that control the feedback loop.
 */
class Deck(
    var source: VisualSource,
    val width: Int = 1920,
    val height: Int = 1080
) {
    // FBO for rendering the clean visual source output
    val cleanFBO = FBO(width, height)

    // Ping-pong feedback FBOs
    val fb1 = FBO(width, height)
    val fb2 = FBO(width, height)
    private var fbIndex = 0

    // Source selection parameter
    val sourceSelect = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)

    // Keep instances of both visual sources
    val mandala: Mandala = if (source is Mandala) source as Mandala else Mandala(MandalaLibrary.MandalaRatios.first())
    val mandelbulb: Mandelbulb = if (source is Mandelbulb) source as Mandelbulb else Mandelbulb()

    // Feedback parameters with custom clamp ranges
    val fbDecay = ModulatableParameter(0.73f, minClamp = 0f, maxClamp = 1f)
    val fbGain = ModulatableParameter(1.0f, minClamp = 0f, maxClamp = 2f)
    val fbZoom = ModulatableParameter(0.0f, minClamp = -1f, maxClamp = 1f) // negative is zoom out, positive is zoom in
    val fbRotate = ModulatableParameter(0.0f, minClamp = -3.14f, maxClamp = 3.14f, meterType = llm.slop.spirals.parameters.MeterType.ENDLESS) // in radians
    val fbHueShift = ModulatableParameter(0.0f, minClamp = -1f, maxClamp = 1f, meterType = llm.slop.spirals.parameters.MeterType.ENDLESS) // range 0..1
    val fbBlur = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f) // range 0..1
    val fbChroma = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
    val fbMode = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f) // 0 = Max, 1 = Difference

    init {
        // Clear all FBOs at startup to prevent reading uninitialized GPU memory
        fb1.clear(0f, 0f, 0f, 0f)
        fb2.clear(0f, 0f, 0f, 0f)
        cleanFBO.clear(0f, 0f, 0f, 0f)
        
        // Ensure starting source matches the initialized source
        source = if (sourceSelect.value >= 0.5f) mandelbulb else mandala
    }

    /**
     * Resets all parameters to their defaults.
     */
    fun reset() {
        sourceSelect.reset()
        mandala.parameters.values.forEach { it.reset() }
        mandala.globalAlpha.reset()
        mandelbulb.parameters.values.forEach { it.reset() }
        mandelbulb.globalAlpha.reset()
        fbDecay.reset()
        fbGain.reset()
        fbZoom.reset()
        fbRotate.reset()
        fbHueShift.reset()
        fbBlur.reset()
        fbChroma.reset()
        fbMode.reset()
        source = if (sourceSelect.value >= 0.5f) mandelbulb else mandala
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
        sourceSelect.evaluate()
        source = if (sourceSelect.value >= 0.5f) mandelbulb else mandala
        source.update()
        fbDecay.evaluate()
        fbGain.evaluate()
        fbZoom.evaluate()
        fbRotate.evaluate()
        fbHueShift.evaluate()
        fbBlur.evaluate()
        fbChroma.evaluate()
        fbMode.evaluate()
    }

    /**
     * Disposes all FBOs associated with this Deck.
     */
    fun dispose() {
        cleanFBO.dispose()
        fb1.dispose()
        fb2.dispose()
    }
}
