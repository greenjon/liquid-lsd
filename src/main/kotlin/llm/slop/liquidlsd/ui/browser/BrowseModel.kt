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

/**
 * Where a row comes from, which is also the top level of the folder tree. [PLAYLISTS] holds no rows; it names the playlist files'
 * own folder, so playlists get folders like the saved sections.
 */
enum class BrowseSection { STOCK, SAVED, SINGLE, CHAIN, LIVE, PLAYLISTS }

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

/**
 * A saved playlist. [items] are the raw tokens stored in the file (stock ids or saved names/paths), in playlist order. [folder] is
 * its '/'-separated folder below the kind's playlists folder ("" for the top level).
 */
data class BrowsePlaylist(val name: String, val path: String, val items: List<String>, val folder: String = "")

/** What the middle list shows. */
sealed interface BrowseScope {
    data object All : BrowseScope
    data object Favorites : BrowseScope

    /** The items loaded or applied most recently, newest first. */
    data object Recent : BrowseScope

    /** A section, optionally narrowed to a subfolder ([folder] "" means the whole section including subfolders). */
    data class Folder(val section: BrowseSection, val folder: String = "") : BrowseScope

    /** The contents of one playlist, in playlist order (the only scope the list can reorder). */
    data class Playlist(val path: String) : BrowseScope

    /** Non-selectable "Playlists" header in the tree. */
    data object PlaylistsHeader : BrowseScope

    /** A non-selectable folder of playlists under the Playlists header ([folder] '/'-separated below the playlists folder). */
    data class PlaylistFolder(val folder: String) : BrowseScope

    /** Every saved entry carrying tag [name] (tags are lowercase): a crate, any number of which an entry can be in. */
    data class Tag(val name: String) : BrowseScope

    /** Non-selectable "Tags" header in the tree. */
    data object TagsHeader : BrowseScope
}

/** One row of the flattened folder tree. [count] is how many entries the scope holds. */
data class BrowseNode(val scope: BrowseScope, val label: String, val depth: Int, val count: Int) {
    val selectable: Boolean get() = scope != BrowseScope.PlaylistsHeader && scope != BrowseScope.TagsHeader && scope !is BrowseScope.PlaylistFolder
}

/** The selectable tree rows that are on screen: rows below a collapsed ancestor are hidden, headers are skipped. */
fun visibleSelectableScopes(tree: List<BrowseNode>, collapsed: Set<BrowseScope>): List<BrowseScope> {
    val visible = mutableListOf<BrowseScope>()
    var hideBelow = Int.MAX_VALUE
    for ((i, node) in tree.withIndex()) {
        if (node.depth > hideBelow) continue
        hideBelow = Int.MAX_VALUE
        val expandable = tree.getOrNull(i + 1)?.let { it.depth > node.depth } == true
        if (expandable && node.scope in collapsed) hideBelow = node.depth
        if (node.selectable) visible += node.scope
    }
    return visible
}

/**
 * The tree cursor after moving [delta] rows from [current] over the [visible] scopes, clamped at both ends. A [current]
 * that is null or no longer visible (collapsed away, playlist deleted) starts from the first row going down, the last going up.
 */
fun stepTreeCursor(visible: List<BrowseScope>, current: BrowseScope?, delta: Int): BrowseScope? {
    if (visible.isEmpty()) return null
    val at = visible.indexOf(current)
    if (at < 0) return if (delta > 0) visible.first() else visible.last()
    return visible[(at + delta).coerceIn(0, visible.lastIndex)]
}

/**
 * The data behind one unified browser pane: every stock and saved entry of a [kind], its playlists, and (when the kind has
 * a favorites store) the favorite keys. Pure and UI-free so the tree, filtering and playlist rules can be unit tested;
 * the panel builds a new catalog when its upstream lists change.
 *
 * [favorites] is null when the kind has no favorites store, in which case the tree has no Favorites folder. [recent] (entry keys,
 * newest first) is null when the kind keeps no history, in which case the tree has no Recent folder. [folders] lists each saved
 * section's folders on disk, so an empty folder still has a tree row.
 */
class BrowseCatalog(
    val kind: BrowseKind,
    private val entries: List<BrowseEntry>,
    private val playlists: List<BrowsePlaylist> = emptyList(),
    private val favorites: Set<String>? = null,
    private val recent: List<String>? = null,
    private val folders: Map<BrowseSection, List<String>> = emptyMap(),
    private val playlistFolders: List<String> = emptyList()
) {
    private val byPath: Map<String, BrowsePlaylist> = playlists.associateBy { it.path }

    /**
     * The folder tree, flattened depth-first. Sections and the Playlists header are always present so the tree does not jump around.
     * With [accepts] (a hosting target's filter) the counts only include rows the list would show.
     */
    fun tree(accepts: ((BrowseEntry) -> Boolean)? = null): List<BrowseNode> {
        val shown = if (accepts == null) entries else entries.filter(accepts)
        val nodes = mutableListOf<BrowseNode>()
        nodes += BrowseNode(BrowseScope.All, "All", 0, shown.size)
        if (favorites != null) {
            nodes += BrowseNode(BrowseScope.Favorites, "Favorites", 0, shown.count { it.key in favorites })
        }
        if (recent != null) {
            nodes += BrowseNode(BrowseScope.Recent, "Recent", 0, recentEntries().count { accepts == null || accepts(it) })
        }
        for (section in sectionsFor(kind)) {
            val inSection = shown.filter { it.section == section }
            nodes += BrowseNode(BrowseScope.Folder(section), sectionLabel(kind, section), 0, inSection.size)
            nodes += folderNodes(section, inSection)
        }
        // Tags: the saved entries' tags, A-Z, each counting the entries that carry it; the header counts the tagged entries.
        val tagged = shown.filter { isSaved(it) && it.asset.tags.isNotEmpty() }
        val tagNodes = tagged.flatMap { tagsOf(it) }.distinct().sorted().map { tag ->
            BrowseNode(BrowseScope.Tag(tag), tag, 1, tagged.count { tag in tagsOf(it) })
        }
        nodes += BrowseNode(BrowseScope.TagsHeader, "Tags", 0, tagged.size)
        nodes += tagNodes
        // Every count means items: each playlist counts its items and the header their total, like a section counts its folders' rows.
        fun countOf(pl: BrowsePlaylist) = if (accepts == null) pl.items.size else pl.items.count { item -> resolve(item)?.let(accepts) == true }
        val playlistNodes = playlistNodes(::countOf)
        nodes += BrowseNode(BrowseScope.PlaylistsHeader, "Playlists", 0, playlists.sumOf(::countOf))
        nodes += playlistNodes
        return nodes
    }

    /** The rows for [scope], filtered by [query] (the shared [SearchMatcher] rule). Playlist scopes keep playlist order. */
    fun rows(scope: BrowseScope, query: String = ""): List<BrowseEntry> {
        val tokens = SearchMatcher.tokens(query)
        val base = when (scope) {
            BrowseScope.All -> entries
            BrowseScope.Favorites -> entries.filter { favorites != null && it.key in favorites }
            BrowseScope.Recent -> recentEntries()
            is BrowseScope.Folder -> entries.filter { it.section == scope.section && inFolder(it.folder, scope.folder) }
            is BrowseScope.Playlist -> byPath[scope.path]?.items?.mapNotNull { resolve(it) } ?: emptyList()
            is BrowseScope.Tag -> entries.filter { isSaved(it) && scope.name in tagsOf(it) }
            BrowseScope.PlaylistsHeader, BrowseScope.TagsHeader, is BrowseScope.PlaylistFolder -> emptyList()
        }
        return base.filter { matches(tokens, it) }
    }

    /** Every tag on a saved entry of this kind, A-Z. */
    fun tags(): List<String> = entries.filter(::isSaved).flatMap { tagsOf(it) }.distinct().sorted()

    private fun isSaved(e: BrowseEntry): Boolean = e.section != BrowseSection.STOCK && e.section != BrowseSection.LIVE

    private fun tagsOf(e: BrowseEntry): List<String> = e.asset.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** The recent keys that still resolve to an entry, newest first. */
    private fun recentEntries(): List<BrowseEntry> {
        val byKey = entries.associateBy { it.key }
        return recent.orEmpty().mapNotNull { byKey[it] }
    }

    /** The entries of a playlist scope index-aligned with the playlist file: null where an item resolves to nothing. Empty for other scopes. */
    fun playlistSlots(scope: BrowseScope): List<BrowseEntry?> =
        if (scope is BrowseScope.Playlist) byPath[scope.path]?.items?.map { resolve(it) } ?: emptyList() else emptyList()

    /** How many playlist items of [scope] match no entry (deleted or renamed files); 0 for non-playlist scopes. */
    fun missing(scope: BrowseScope): Int {
        if (scope !is BrowseScope.Playlist) return 0
        return byPath[scope.path]?.items?.count { resolve(it) == null } ?: 0
    }

    /** The items of a playlist [scope] that match no entry, with their positions in the playlist file. */
    fun missingItems(scope: BrowseScope): List<Pair<Int, String>> =
        if (scope !is BrowseScope.Playlist) emptyList()
        else byPath[scope.path]?.items?.withIndex()?.filter { resolve(it.value) == null }?.map { it.index to it.value } ?: emptyList()

    /**
     * Saved entries that a missing playlist item could be: the same file name (with or without its extension) anywhere in the
     * kind's saved sections, so a file moved or renamed outside the app can be found again.
     */
    fun candidatesFor(item: String): List<BrowseEntry> {
        val name = item.replace('\\', '/').substringAfterLast('/')
        if (name.isEmpty()) return emptyList()
        return entries.filter { e ->
            isSaved(e) && e.key.replace('\\', '/').substringAfterLast('/').let { it.equals(name, true) || it.substringBeforeLast('.').equals(name, true) }
        }
    }

    /** Whether [entry] may be added to a playlist or queue-as-playlist (stock rows only for kinds that store stock ids). */
    fun canAddToPlaylist(entry: BrowseEntry): Boolean =
        entry.section != BrowseSection.LIVE && (entry.section != BrowseSection.STOCK || kind.stockInPlaylists)

    /** The playlist token to store for [entry] (stock id or saved path). */
    fun playlistToken(entry: BrowseEntry): String = entry.key

    /** The only scope in which rows may be reordered or removed from the list. */
    fun isReorderable(scope: BrowseScope): Boolean = scope is BrowseScope.Playlist

    private fun resolve(item: String): BrowseEntry? {
        entries.firstOrNull { it.key == item }?.let { return it }
        // Saved names are stored bare ("my fx"), relative ("sub/my fx.lsdfx") or absolute; match by trailing path or file name.
        val norm = item.replace('\\', '/')
        return entries.firstOrNull { e ->
            e.section != BrowseSection.STOCK && e.section != BrowseSection.LIVE && run {
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

    /** The playlists under the header: each folder (A-Z) with its own folders and playlists below it, then the loose playlists. */
    private fun playlistNodes(countOf: (BrowsePlaylist) -> Int): List<BrowseNode> {
        val all = (playlists.map { it.folder } + playlistFolders).filter { it.isNotEmpty() }
            .flatMap { path -> path.split('/').runningReduce { acc, seg -> "$acc/$seg" } }
            .distinct()
        val nodes = mutableListOf<BrowseNode>()
        fun emit(parent: String, depth: Int) {
            for (f in all.filter { it.substringBeforeLast('/', "") == parent }.sortedWith(String.CASE_INSENSITIVE_ORDER)) {
                nodes += BrowseNode(BrowseScope.PlaylistFolder(f), f.substringAfterLast('/'), depth, playlists.filter { inFolder(it.folder, f) }.sumOf(countOf))
                emit(f, depth + 1)
            }
            for (pl in playlists.filter { it.folder == parent }) nodes += BrowseNode(BrowseScope.Playlist(pl.path), pl.name, depth, countOf(pl))
        }
        emit("", 1)
        return nodes
    }

    private fun folderNodes(section: BrowseSection, inSection: List<BrowseEntry>): List<BrowseNode> {
        val folders = (inSection.map { it.folder } + folders[section].orEmpty()).filter { it.isNotEmpty() }
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
            BrowseKind.SRC -> listOf(BrowseSection.STOCK, BrowseSection.SAVED, BrowseSection.LIVE)
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
            BrowseSection.LIVE -> "External video"
            BrowseSection.PLAYLISTS -> "Playlists"
        }

        /**
         * The name a row shows: a saved file name written in snake_case (`hyperspace_crash_zoom`) reads as words
         * (`Hyperspace crash zoom`). Names that already contain a space, or no underscore, are left alone; the file keeps its name.
         */
        fun prettyName(name: String): String {
            if ('_' !in name || ' ' in name) return name
            val words = name.split('_').filter { it.isNotEmpty() }.joinToString(" ")
            return words.replaceFirstChar { it.uppercaseChar() }.ifEmpty { name }
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
