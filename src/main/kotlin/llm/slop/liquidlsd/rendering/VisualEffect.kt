package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterOwner

/**
 * Interface for post-processing visual effects that can be applied to a Deck.
 */
interface VisualEffect : ParameterOwner {
    val id: String
    val displayName: String
    val categories: List<String>
        get() = emptyList()
    val parameters: Map<String, ModulatableParameter>
    val dryWet: ModulatableParameter
    var enabled: Boolean

    fun update()
    fun clone(): VisualEffect
    fun dispose()
    fun reset()
}
