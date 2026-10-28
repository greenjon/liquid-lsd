package llm.slop.liquidlsd.ui.browser

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.presets.FxShortlist
import mu.KotlinLogging
import java.io.File

/**
 * Favorites for the unified browser, one list per [BrowseKind]. A key is a [BrowseEntry.key]: a shader id for stock rows,
 * a file path for saved ones.
 *
 * Stock FX favorites are the existing [FxShortlist] (which the slot cells' step buttons also use, and which walks shader ids), so a
 * saved single or chain is starred in a separate FX store keyed by path; the FX Favorites folder lists both. SRC and TRANS get their
 * own JSON lists next to it in `library/`, so favorites travel with the library.
 */
object BrowseFavorites {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true }
    private val listSerializer = ListSerializer(String.serializer())

    private class Store(var file: File) {
        val keys = linkedSetOf<String>()
        var loaded = false
        @Volatile var version = 0
    }

    private val stores = mapOf(
        BrowseKind.SRC to Store(File("library/src_favorites.json")),
        BrowseKind.FX to Store(File("library/fx_saved_favorites.json")),
        BrowseKind.TRANS to Store(File("library/transition_favorites.json"))
    )

    /** Bumped on every change, so a catalog built from the favorites knows when to rebuild. */
    fun version(kind: BrowseKind): Int = stores.getValue(kind).version + if (kind == BrowseKind.FX) FxShortlist.version else 0

    fun keys(kind: BrowseKind): Set<String> =
        if (kind == BrowseKind.FX) FxShortlist.favorites().toSet() + loaded(kind).keys else loaded(kind).keys

    /** Whether an FX key names a saved single or chain (a path) rather than a stock filter id. */
    private fun isSavedFx(key: String): Boolean = LibraryRefs.kindOf(File(key)) == BrowseKind.FX

    fun isFavorite(kind: BrowseKind, key: String): Boolean =
        key in loaded(kind).keys || (kind == BrowseKind.FX && FxShortlist.isFavorite(key))

    /** Stars or un-stars [key] and saves immediately. */
    fun toggle(kind: BrowseKind, key: String) {
        if (kind == BrowseKind.FX && !isSavedFx(key)) {
            FxShortlist.toggle(key)
            return
        }
        val store = loaded(kind)
        if (!store.keys.remove(key)) store.keys.add(key)
        store.version++
        save(store)
    }

    /** Follows renamed or moved files ([moves]: old key to new). Stock FX favorites are shader ids, so they never move. */
    fun relocate(kind: BrowseKind, moves: Map<String, String>) {
        val store = loaded(kind)
        if (store.keys.none { it in moves }) return
        val updated = store.keys.map { moves[it] ?: it }
        store.keys.clear()
        store.keys.addAll(updated)
        store.version++
        save(store)
    }

    /** Drops deleted files ([keys]). */
    fun forget(kind: BrowseKind, keys: Set<String>) {
        val store = loaded(kind)
        if (store.keys.removeAll(keys)) {
            store.version++
            save(store)
        }
    }

    /** Test hook: point a kind at [file] and drop in-memory state (for FX, the saved-FX store only). */
    internal fun resetForTest(kind: BrowseKind, file: File) {
        val store = stores.getValue(kind)
        store.file = file
        store.keys.clear()
        store.loaded = false
        store.version++
    }

    private fun loaded(kind: BrowseKind): Store {
        val store = stores.getValue(kind)
        if (store.loaded) return store
        store.loaded = true
        if (store.file.exists()) {
            try {
                store.keys.addAll(json.decodeFromString(listSerializer, store.file.readText()))
            } catch (e: Exception) {
                logger.warn(e) { "Could not read favorites ${store.file.path}; starting empty" }
            }
        }
        return store
    }

    private fun save(store: Store) {
        try {
            store.file.parentFile?.mkdirs()
            store.file.writeText(json.encodeToString(listSerializer, store.keys.toList()))
        } catch (e: Exception) {
            logger.error(e) { "Could not save favorites ${store.file.path}" }
        }
    }
}
