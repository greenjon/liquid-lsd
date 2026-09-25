package llm.slop.liquidlsd.presets

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File

/**
 * The FX shortlist that a slot cell's ◀ / ▶ (and mouse wheel) steps through, Mixxx
 * "visible effects"-style: stepping through thousands of ISF shaders live is useless, so the
 * user stars the handful they actually perform with (★ in the shader picker).
 *
 * Stepping rule ([next]): while the current effect is a favorite (or the slot is empty and
 * favorites exist), step through the favorites in the order they were starred. Otherwise step
 * through the current effect's own category, alphabetically -- so ◀ / ▶ is useful before the
 * user has starred anything.
 *
 * Persisted as a JSON id list in `library/fx_shortlist.json` so it travels with the library.
 */
object FxShortlist {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true }
    private val listSerializer = ListSerializer(String.serializer())

    /** Minimal view of a stock filter, so [next] stays a pure function (see FxShortlistTest). */
    data class Candidate(val id: String, val displayName: String, val categories: List<String>)

    var file: File = File("library/fx_shortlist.json")

    private val favorites = mutableListOf<String>()
    private var loaded = false

    fun favorites(): List<String> {
        ensureLoaded()
        return favorites
    }

    fun isFavorite(id: String): Boolean {
        ensureLoaded()
        return id in favorites
    }

    /** Stars or un-stars [id] and saves immediately. */
    fun toggle(id: String) {
        ensureLoaded()
        if (!favorites.remove(id)) favorites.add(id)
        save()
    }

    /** Test hook: replace the in-memory list without touching disk. */
    internal fun setForTest(ids: List<String>) {
        favorites.clear()
        favorites.addAll(ids)
        loaded = true
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        try {
            favorites.addAll(json.decodeFromString(listSerializer, file.readText()))
        } catch (e: Exception) {
            logger.warn(e) { "Could not read FX shortlist ${file.path}; starting empty" }
        }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(listSerializer, favorites))
        } catch (e: Exception) {
            logger.error(e) { "Could not save FX shortlist ${file.path}" }
        }
    }

    /**
     * The filter id to step to from [currentId] in direction [dir] (+1 / -1), wrapping around, or
     * null if there is nothing to step to. [available] is every loaded stock filter.
     */
    fun next(currentId: String?, dir: Int, available: List<Candidate>, favoriteIds: List<String> = favorites()): String? {
        if (available.isEmpty()) return null
        val byId = available.associateBy { it.id }
        val favs = favoriteIds.filter { it in byId }
        val list: List<String> = when {
            favs.isNotEmpty() && (currentId == null || currentId in favs) -> favs
            currentId != null && byId[currentId] != null -> {
                val category = byId.getValue(currentId).categories.firstOrNull()
                available.filter { category == null || category in it.categories }
                    .sortedBy { it.displayName.lowercase() }
                    .map { it.id }
            }
            else -> available.sortedBy { it.displayName.lowercase() }.map { it.id }
        }
        if (list.isEmpty()) return null
        val idx = list.indexOf(currentId)
        val nextIdx = when {
            idx < 0 -> if (dir >= 0) 0 else list.lastIndex
            else -> Math.floorMod(idx + dir, list.size)
        }
        return list[nextIdx]
    }
}
