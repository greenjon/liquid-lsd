package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter

import llm.slop.liquidlsd.parameters.ParameterOwner

/**
 * Interface for renderable visual objects that consume modulatable parameters.
 * Designed to allow swap-in of different source types (Mandala, VideoFeed, etc.).
 */
interface VisualSource : ParameterOwner {
    /**
     * Human-readable display name of this visual source.
     */
    val displayName: String
        get() = "Visual Source"

    /**
     * Categories for this visual source (e.g., "Generator", "Geometric", "3D").
     */
    val categories: List<String>
        get() = emptyList()

    /**
     * Map of parameter names to their modulatable counterparts.
     */
    val parameters: Map<String, ModulatableParameter>

    /**
     * Indicates whether this is a native 3D visual source (e.g. raymarched volume or 3D/4D mesh)
     * which handles its own 3D rotation and projection.
     */
    val is3D: Boolean
        get() = false

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

    /**
     * Clean up any native or graphics resources.
     */
    fun dispose() {}

    /**
     * Clear any accumulated feedback/history buffers.
     */
    fun clear() {}
}
