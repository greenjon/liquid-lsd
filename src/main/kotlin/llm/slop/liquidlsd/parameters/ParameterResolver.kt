package llm.slop.liquidlsd.parameters

import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.DynamicVisualSource

object ParameterResolver {
    fun getAllParameterPaths(mixer: Mixer): List<Pair<String, ModulatableParameter>> {
        return mixer.getParameterPaths("Mixer")
    }

    fun findParameterByPath(mixer: Mixer, path: String): ModulatableParameter? {
        // Simple O(N) search against the list. We could optimize this by caching if performance is an issue,
        // but it is usually only called when restoring UI state or handling midi mappings occasionally.
        return getAllParameterPaths(mixer).find { it.first == path }?.second
    }
}
