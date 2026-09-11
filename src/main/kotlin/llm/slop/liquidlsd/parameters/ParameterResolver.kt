package llm.slop.liquidlsd.parameters

import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.DynamicVisualSource

import java.util.concurrent.ConcurrentHashMap

object ParameterResolver {
    private val pathCache = ConcurrentHashMap<String, ModulatableParameter>()
    @Volatile private var cachedMixer: Mixer? = null

    fun clearCache() {
        pathCache.clear()
        cachedMixer = null
    }

    fun getAllParameterPaths(mixer: Mixer): List<Pair<String, ModulatableParameter>> {
        return mixer.getParameterPaths("Mixer")
    }

    fun findParameterByPath(mixer: Mixer, path: String): ModulatableParameter? {
        if (cachedMixer !== mixer) {
            pathCache.clear()
            cachedMixer = mixer
        }
        val cached = pathCache[path]
        if (cached != null) return cached

        val found = getAllParameterPaths(mixer).find { it.first == path }?.second
        if (found != null) {
            pathCache[path] = found
        }
        return found
    }
}
