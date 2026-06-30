package llm.slop.spirals.rendering

import llm.slop.spirals.parameters.ModulatableParameter

/**
 * Interface for renderable visual objects that consume modulatable parameters.
 * Designed to allow swap-in of different source types (Mandala, VideoFeed, etc.).
 */
interface VisualSource {
    /**
     * Map of parameter names to their modulatable counterparts.
     */
    val parameters: Map<String, ModulatableParameter>

    /**
     * Top-level parameters for mixing and composition.
     */
    val globalAlpha: ModulatableParameter

    /**
     * Trigger evaluation of all parameters.
     * Expected to be called once per frame.
     */
    fun update() {
        parameters.values.forEach { it.evaluate() }
        globalAlpha.evaluate()
    }

    /**
     * Creates an independent copy of this visual source.
     */
    fun clone(): VisualSource
}
