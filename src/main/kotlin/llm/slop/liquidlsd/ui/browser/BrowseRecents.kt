package llm.slop.liquidlsd.ui.browser

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import mu.KotlinLogging
import java.io.File

/**
 * The browser's Recent folder: the last [LIMIT] items of each [BrowseKind] loaded or applied from the Library or a bound dock, newest first.
 * Keys are [BrowseEntry.key]s (stock id, external-stream key or file path). Kept in memory and saved next to the favorites in `library/`,
 * so the list survives a restart; a key whose file is gone simply stops resolving.
 */
object BrowseRecents {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true }
    private val listSerializer = ListSerializer(String.serializer())

    const val LIMIT = 20

    private class Store(var file: File) {
        val keys = ArrayList<String>()
        var loaded = false
        @Volatile var version = 0
    }

    private val stores = mapOf(
        BrowseKind.SRC to Store(File("library/src_recent.json")),
        BrowseKind.FX to Store(File("library/fx_recent.json")),
        BrowseKind.TRANS to Store(File("library/transition_recent.json"))
    )

    /** Bumped on every change, so a catalog built from the list knows when to rebuild. */
    fun version(kind: BrowseKind): Int = stores.getValue(kind).version

    /** Newest first. */
    fun keys(kind: BrowseKind): List<String> = loaded(kind).keys

    /** The [BrowseEntry.key] of a row's [asset]: the id for stock rows, the path for everything else. */
    fun keyOf(asset: AssetItem): String = when (asset.type) {
        AssetType.SOURCE_STOCK -> asset.path.removePrefix(BrowseCatalogs.STOCK_SOURCE_PREFIX)
        AssetType.FX_STOCK -> asset.path.removePrefix(BrowseCatalogs.STOCK_FX_PREFIX)
        AssetType.TRANSITION_STOCK -> asset.path.removePrefix(BrowseCatalogs.STOCK_TRANS_PREFIX)
        else -> asset.path
    }

    fun kindOf(asset: AssetItem): BrowseKind? = when (asset.type) {
        AssetType.PRESET, AssetType.SOURCE_STOCK, AssetType.SOURCE_EXTERNAL -> BrowseKind.SRC
        AssetType.FX_STOCK, AssetType.FX_PRESET, AssetType.FX_CHAIN -> BrowseKind.FX
        AssetType.TRANSITION_STOCK, AssetType.TRANSITION_PRESET -> BrowseKind.TRANS
        else -> null
    }

    /** Records that [asset] was just loaded or applied: it moves to the top, the oldest falls off past [LIMIT]. */
    fun note(asset: AssetItem) {
        val kind = kindOf(asset) ?: return
        note(kind, keyOf(asset))
    }

    fun note(kind: BrowseKind, key: String) {
        val store = loaded(kind)
        if (store.keys.firstOrNull() == key) return
        store.keys.remove(key)
        store.keys.add(0, key)
        while (store.keys.size > LIMIT) store.keys.removeAt(store.keys.lastIndex)
        store.version++
        save(store)
    }

    /** Test hook: point a kind at [file] and drop in-memory state. */
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
                store.keys.addAll(json.decodeFromString(listSerializer, store.file.readText()).distinct().take(LIMIT))
            } catch (e: Exception) {
                logger.warn(e) { "Could not read recent items ${store.file.path}; starting empty" }
            }
        }
        return store
    }

    private fun save(store: Store) {
        try {
            store.file.parentFile?.mkdirs()
            store.file.writeText(json.encodeToString(listSerializer, store.keys))
        } catch (e: Exception) {
            logger.error(e) { "Could not save recent items ${store.file.path}" }
        }
    }
}
