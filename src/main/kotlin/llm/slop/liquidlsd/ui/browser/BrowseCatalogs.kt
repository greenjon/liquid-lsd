package llm.slop.liquidlsd.ui.browser

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXPresetDto
import llm.slop.liquidlsd.models.TransitionPresetDto
import llm.slop.liquidlsd.presets.PlaylistParser
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import java.io.File

/**
 * Builds the [BrowseCatalog] for each kind from the live registries, the cached file-system scans and the playlist files.
 * Cheap enough to call every frame: a catalog is rebuilt only when an upstream list, the favorites version or a playlist file
 * changes, mirroring the row caches the old per-tab browser panels kept.
 */
object BrowseCatalogs {
    const val STOCK_SOURCE_PREFIX = "stock-source://"
    const val STOCK_FX_PREFIX = "stock-fx://"
    const val STOCK_TRANS_PREFIX = "stock-trans://"

    private val json = Json { ignoreUnknownKeys = true }

    private class Cache(val inputs: List<Any?>, val catalog: BrowseCatalog)
    private val caches = HashMap<BrowseKind, Cache>()

    fun get(kind: BrowseKind): BrowseCatalog {
        val playlistAssets = playlistAssets(kind)
        val inputs = when (kind) {
            BrowseKind.SRC -> listOf(VisualSourceRegistry.availableSources.toList(), FileSystemManager.scanAllPresets(), playlistAssets)
            BrowseKind.FX -> listOf(ISFFilterRegistry.availableFilters, FileSystemManager.scanAllFxPresets(), FileSystemManager.scanAllFxChains(), playlistAssets)
            BrowseKind.TRANS -> listOf(ISFTransitionRegistry.availableTransitions, FileSystemManager.scanAllTransitionPresets(), playlistAssets)
        } + BrowseFavorites.version(kind) + playlistAssets.map { File(it.path).lastModified() }
        val cached = caches[kind]
        if (cached != null && cached.inputs == inputs) return cached.catalog
        return build(kind, playlistAssets).also { caches[kind] = Cache(inputs, it) }
    }

    private fun playlistAssets(kind: BrowseKind): List<AssetItem> = when (kind) {
        BrowseKind.SRC -> FileSystemManager.scanAllPlaylists()
        BrowseKind.FX -> FileSystemManager.scanAllFxPlaylists()
        BrowseKind.TRANS -> FileSystemManager.scanAllTransitionPlaylists()
    }

    private fun build(kind: BrowseKind, playlistAssets: List<AssetItem>): BrowseCatalog {
        val entries = when (kind) {
            BrowseKind.SRC -> srcEntries()
            BrowseKind.FX -> fxEntries()
            BrowseKind.TRANS -> transEntries()
        }
        val playlists = playlistAssets.map { a ->
            val items = try { PlaylistParser.parseFile(File(a.path)) } catch (e: Exception) { emptyList() }
            BrowsePlaylist(a.name, a.path, items)
        }
        return BrowseCatalog(kind, entries, playlists, BrowseFavorites.keys(kind))
    }

    private fun srcEntries(): List<BrowseEntry> {
        val root = FileSystemManager.getPresetsRoot()
        val stock = VisualSourceRegistry.availableSources.sortedBy { it.displayName.lowercase() }.map {
            BrowseEntry(
                AssetItem(STOCK_SOURCE_PREFIX + it.id, it.displayName, AssetType.SOURCE_STOCK, tags = it.categories),
                BrowseSection.STOCK, it.id, it.folderPath, it.categories, it.categories.joinToString(", ")
            )
        }
        val saved = FileSystemManager.scanAllPresets().map {
            BrowseEntry(it, BrowseSection.SAVED, it.path, BrowseCatalog.folderOf(File(it.path), root), it.tags, it.tags.joinToString(", "))
        }
        return stock + saved
    }

    private fun fxEntries(): List<BrowseEntry> {
        val stock = ISFFilterRegistry.availableFilters.sortedBy { it.displayName.lowercase() }.map {
            BrowseEntry(
                AssetItem(STOCK_FX_PREFIX + it.id, it.displayName, AssetType.FX_STOCK),
                BrowseSection.STOCK, it.id, it.folderPath, it.categories, it.categories.joinToString(", ")
            )
        }
        val singleRoot = FileSystemManager.getFxPresetsRoot()
        val singles = FileSystemManager.scanAllFxPresets().map {
            BrowseEntry(it, BrowseSection.SINGLE, it.path, BrowseCatalog.folderOf(File(it.path), singleRoot), it.tags, fxSingleInfo(File(it.path)))
        }
        val chainRoot = FileSystemManager.getFxChainsRoot()
        val chains = FileSystemManager.scanAllFxChains().map {
            BrowseEntry(it, BrowseSection.CHAIN, it.path, BrowseCatalog.folderOf(File(it.path), chainRoot), it.tags, fxChainInfo(File(it.path)))
        }
        return stock + singles + chains
    }

    private fun transEntries(): List<BrowseEntry> {
        val stock = ISFTransitionRegistry.availableTransitions.sortedBy { it.displayName.lowercase() }.map {
            BrowseEntry(
                AssetItem(STOCK_TRANS_PREFIX + it.id, it.displayName, AssetType.TRANSITION_STOCK, tags = it.categories),
                BrowseSection.STOCK, it.id, it.folderPath, it.categories, it.categories.joinToString(", ")
            )
        }
        val root = FileSystemManager.getTransitionsRoot()
        val saved = FileSystemManager.scanAllTransitionPresets().map {
            BrowseEntry(it, BrowseSection.SAVED, it.path, BrowseCatalog.folderOf(File(it.path), root), it.tags, transSavedInfo(File(it.path)))
        }
        return stock + saved
    }

    // --- info columns (cached per file + mtime; the files are tiny but the pane draws every frame) ---

    private data class InfoKey(val path: String, val mtime: Long)
    private val infoCache = HashMap<InfoKey, String>()

    private fun cachedInfo(file: File, compute: () -> String): String =
        infoCache.getOrPut(InfoKey(file.path, file.lastModified())) { try { compute() } catch (e: Exception) { "" } }

    private fun filterName(id: String): String =
        ISFFilterRegistry.availableFilters.firstOrNull { it.id == id }?.displayName ?: id

    private fun transName(id: String): String =
        ISFTransitionRegistry.availableTransitions.firstOrNull { it.id == id }?.displayName ?: id

    /** "Blur > Glow > (empty)": the three slots in order, so a chain can be told apart without loading it. */
    internal fun chainInfo(filterIds: List<String?>): String =
        (0 until 3).joinToString(" > ") { i -> filterIds.getOrNull(i)?.let(::filterName) ?: "(empty)" }

    private fun fxChainInfo(file: File): String = cachedInfo(file) {
        chainInfo(json.decodeFromString<FXChainDto>(file.readText()).slots.map { it?.filterId })
    }

    private fun fxSingleInfo(file: File): String = cachedInfo(file) {
        filterName(json.decodeFromString<FXPresetDto>(file.readText()).slot.filterId)
    }

    private fun transSavedInfo(file: File): String = cachedInfo(file) {
        transName(json.decodeFromString<TransitionPresetDto>(file.readText()).slot.filterId)
    }
}
