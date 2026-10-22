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

    private val LEGACY_GAIN = Regex("^(Deck (?:A|B|BG|PV))/[^/]+/Gain$")

    /**
     * Mappings saved before Gain moved to "<deck>/Gain" name it under the source ("Deck A/Mandala/Gain"). Tried only
     * after an exact match, so a source input that is itself called Gain still wins.
     */
    fun legacyGainPath(path: String): String? = LEGACY_GAIN.matchEntire(path)?.let { "${it.groupValues[1]}/Gain" }

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

        val all = getAllParameterPaths(mixer)
        val found = all.find { it.first == path }?.second
            ?: legacyGainPath(path)?.let { gain -> all.find { it.first == gain }?.second }
        if (found != null) {
            pathCache[path] = found
        }
        return found
    }
}
