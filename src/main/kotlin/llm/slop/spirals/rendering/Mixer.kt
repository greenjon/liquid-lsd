package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter

/**
 * Manages the blending of two Decks (Deck A and Deck B) into a master output FBO.
 * Provides controls for crossfade, master alpha, and blending mode.
 */
class Mixer(
    val deckA: Deck,
    val deckB: Deck,
    val width: Int = 1920,
    val height: Int = 1080
) {
    // The master FBO where the blended result is rendered
    val masterFBO = FBO(width, height)

    // Blend parameters
    val crossfade = ModulatableParameter(0.0f) // 0.0 = Deck A, 1.0 = Deck B
    val mode = ModulatableParameter(4.0f) // 0 = ADD, 1 = SCREEN, 2 = MULT, 3 = MAX, 4 = XFADE
    val masterAlpha = ModulatableParameter(1.0f) // Master output gain
    val bloom = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)

    /**
     * Evaluates mixer parameters.
     */
    fun update() {
        crossfade.evaluate()
        mode.evaluate()
        masterAlpha.evaluate()
        bloom.evaluate()
    }

    /**
     * Disposes the master FBO.
     */
    fun dispose() {
        masterFBO.dispose()
    }
}
