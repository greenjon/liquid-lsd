package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.AssetItem
import java.io.File

/** The three browsable asset kinds (the Library's SRC / FX / Trans tabs). */
enum class BrowseKind(
    /** Whether a stock row may be added to a playlist (transition playlists store the stock shader id; the others only hold saved files). */
    val stockInPlaylists: Boolean
) {
    SRC(false), FX(false), TRANS(true)
}

/** Where a row comes from, which is also the top level of the folder tree. */
enum class BrowseSection { STOCK, SAVED, SINGLE, CHAIN }

/**
 * One browsable row. [key] identifies it for favorites and playlist membership: the shader id for stock rows, the file path for saved ones.
 * [folder] is a '/'-separated path below the section root ("" for top level), used to build subfolders in the tree.
 */
data class BrowseEntry(
    val asset: AssetItem,
    val section: BrowseSection,
    val key: String,
    val folder: String = "",
    val categories: List<String> = emptyList(),
    val info: String = ""
)

/** A saved playlist. [items] are the raw tokens stored in the file (stock ids or saved names/paths), in playlist order. */
data class BrowsePlaylist(val name: String, val path: String, val items: List<String>)

/** What the middle list shows. */
sealed interface BrowseScope {
    data object All : BrowseScope
    data object Favorites : BrowseScope

    /** A section, optionally narrowed to a subfolder ([folder] "" means the whole section including subfolders). */
    data class Folder(val section: BrowseSection, val folder: String = "") : BrowseScope

    /** The contents of one playlist, in playlist order (the only scope the list can reorder). */
    data class Playlist(val path: String) : BrowseScope

    /** Non-selectable "Playlists" header in the tree. */
    data object PlaylistsHeader : BrowseScope
}

/** One row of the flattened folder tree. [count] is how many entries the scope holds. */
data class BrowseNode(val scope: BrowseScope, val label: String, val depth: Int, val count: Int) {
    val selectable: Boolean get() = scope != BrowseScope.PlaylistsHeader
}

/**
 * The data behind one unified browser pane: every stock and saved entry of a [kind], its playlists, and (when the kind has
 * a favorites store) the favorite keys. Pure and UI-free so the tree, filtering and playlist rules can be unit tested;
 * the panel builds a new catalog when its upstream lists change.
 *
 * [favorites] is null when the kind has no favorites store, in which case the tree has no Favorites folder.
 */
class BrowseCatalog(
    val kind: BrowseKind,
    private val entries: List<BrowseEntry>,
    private val playlists: List<BrowsePlaylist> = emptyList(),
    private val favorites: Set<String>? = null
) {
    private val byPath: Map<String, BrowsePlaylist> = playlists.associateBy { it.path }

    /** The folder tree, flattened depth-first. Sections and the Playlists header are always present so the tree does not jump around. */
    fun tree(): List<BrowseNode> {
        val nodes = mutableListOf<BrowseNode>()
        nodes += BrowseNode(BrowseScope.All, "All", 0, entries.size)
        if (favorites != null) {
            nodes += BrowseNode(BrowseScope.Favorites, "Favorites", 0, entries.count { it.key in favorites })
        }
        for (section in sectionsFor(kind)) {
            val inSection = entries.filter { it.section == section }
            nodes += BrowseNode(BrowseScope.Folder(section), sectionLabel(kind, section), 0, inSection.size)
            nodes += folderNodes(section, inSection)
        }
        nodes += BrowseNode(BrowseScope.PlaylistsHeader, "Playlists", 0, playlists.size)
        playlists.forEach { nodes += BrowseNode(BrowseScope.Playlist(it.path), it.name, 1, it.items.size) }
        return nodes
    }

    /** The rows for [scope], filtered by [query] (the shared [SearchMatcher] rule). Playlist scopes keep playlist order. */
    fun rows(scope: BrowseScope, query: String = ""): List<BrowseEntry> {
        val tokens = SearchMatcher.tokens(query)
        val base = when (scope) {
            BrowseScope.All -> entries
            BrowseScope.Favorites -> entries.filter { favorites != null && it.key in favorites }
            is BrowseScope.Folder -> entries.filter { it.section == scope.section && inFolder(it.folder, scope.folder) }
            is BrowseScope.Playlist -> byPath[scope.path]?.items?.mapNotNull { resolve(it) } ?: emptyList()
            BrowseScope.PlaylistsHeader -> emptyList()
        }
        return base.filter { matches(tokens, it) }
    }

    /** How many playlist items of [scope] match no entry (deleted or renamed files); 0 for non-playlist scopes. */
    fun missing(scope: BrowseScope): Int {
        if (scope !is BrowseScope.Playlist) return 0
        return byPath[scope.path]?.items?.count { resolve(it) == null } ?: 0
    }

    /** Whether [entry] may be added to a playlist or queue-as-playlist (stock rows only for kinds that store stock ids). */
    fun canAddToPlaylist(entry: BrowseEntry): Boolean = entry.section != BrowseSection.STOCK || kind.stockInPlaylists

    /** The playlist token to store for [entry] (stock id or saved path). */
    fun playlistToken(entry: BrowseEntry): String = entry.key

    /** The only scope in which rows may be reordered or removed from the list. */
    fun isReorderable(scope: BrowseScope): Boolean = scope is BrowseScope.Playlist

    private fun resolve(item: String): BrowseEntry? {
        entries.firstOrNull { it.key == item }?.let { return it }
        // Saved names are stored bare ("my fx"), relative ("sub/my fx.lsdfx") or absolute; match by trailing path or file name.
        val norm = item.replace('\\', '/')
        return entries.firstOrNull { e ->
            e.section != BrowseSection.STOCK && run {
                val p = e.key.replace('\\', '/')
                val file = p.substringAfterLast('/')
                p == norm || p.endsWith("/$norm") || file == norm || file.substringBeforeLast('.') == norm
            }
        }
    }

    private fun matches(tokens: List<String>, e: BrowseEntry): Boolean {
        if (tokens.isEmpty()) return true
        val fields = listOf(e.asset.name, if (e.section == BrowseSection.STOCK) e.key else "", e.folder)
        return SearchMatcher.matches(tokens, fields, e.categories + e.asset.tags)
    }

    private fun folderNodes(section: BrowseSection, inSection: List<BrowseEntry>): List<BrowseNode> {
        val folders = inSection.map { it.folder }.filter { it.isNotEmpty() }
            .flatMap { path -> path.split('/').runningReduce { acc, seg -> "$acc/$seg" } }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        return folders.map { f ->
            BrowseNode(
                BrowseScope.Folder(section, f),
                f.substringAfterLast('/'),
                f.count { it == '/' } + 1,
                inSection.count { inFolder(it.folder, f) }
            )
        }
    }

    companion object {
        fun sectionsFor(kind: BrowseKind): List<BrowseSection> = when (kind) {
            BrowseKind.FX -> listOf(BrowseSection.STOCK, BrowseSection.SINGLE, BrowseSection.CHAIN)
            else -> listOf(BrowseSection.STOCK, BrowseSection.SAVED)
        }

        fun sectionLabel(kind: BrowseKind, section: BrowseSection): String = when (section) {
            BrowseSection.STOCK -> when (kind) {
                BrowseKind.SRC -> "Stock sources"
                BrowseKind.FX -> "Stock filters"
                BrowseKind.TRANS -> "Stock transitions"
            }
            BrowseSection.SAVED -> when (kind) {
                BrowseKind.SRC -> "Saved presets"
                else -> "Saved transitions"
            }
            BrowseSection.SINGLE -> "Saved single FX"
            BrowseSection.CHAIN -> "Saved chains"
        }

        /** True when [folder] is [scopeFolder] or lies below it; an empty [scopeFolder] matches everything. */
        fun inFolder(folder: String, scopeFolder: String): Boolean =
            scopeFolder.isEmpty() || folder == scopeFolder || folder.startsWith("$scopeFolder/")

        /** The '/'-separated folder of [file] below [root], or "" if it sits directly in (or outside) [root]. */
        fun folderOf(file: File, root: File): String {
            val rel = try {
                file.absoluteFile.parentFile?.relativeTo(root.absoluteFile)?.path ?: return ""
            } catch (e: IllegalArgumentException) {
                return ""
            }
            if (rel.isEmpty() || rel.startsWith("..")) return ""
            return rel.replace('\\', '/')
        }
    }
}
