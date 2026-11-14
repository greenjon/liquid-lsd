package llm.slop.liquidlsd.ui.browser

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.FXPlaylistDto
import llm.slop.liquidlsd.models.TransitionPlaylistDto
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.PlaylistManager
import java.io.File

/**
 * The playlists a row menu's "Add to Playlist" offers, and adding rows to one: one path for all three kinds (`.lsdplay`,
 * `.lsdfxplay`, `.lsdtransplay`), so a menu add stores the same entries a drag does. Pure apart from the files; unit tested.
 */
object PlaylistTargets {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** One folder level of a playlists root: its playlists and its subfolders, each sorted case-insensitively. */
    class Level(val playlists: List<File>, val folders: List<File>) {
        val isEmpty get() = playlists.isEmpty() && folders.isEmpty()
    }

    /** The playlists (files ending in [ext]) and visible subfolders directly in [dir]; hidden folders (".trash") are left out. */
    fun level(dir: File, ext: String): Level {
        val all = dir.listFiles()?.toList() ?: emptyList()
        val byName = compareBy<File, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        return Level(
            all.filter { it.isFile && it.extension.equals(ext, ignoreCase = true) }.sortedWith(byName),
            all.filter { it.isDirectory && !it.name.startsWith(".") }.sortedWith(byName)
        )
    }

    /** The playlist entry for a saved file at [path]: a stock transition's bundled `.fs` becomes its shader id, a saved file its path under [roots]. */
    fun token(kind: BrowseKind, path: String, roots: List<File>): String =
        if (kind == BrowseKind.TRANS && path.endsWith(".fs", ignoreCase = true)) File(path).nameWithoutExtension
        else PlaylistItems.shortToken(path, roots)

    /**
     * Appends [paths] to the end of the FX or transition playlist [file], as tokens ([token]). Returns how many were added, or a
     * failure (unreadable, not a playlist, read-only) with the file left as it was.
     */
    fun appendTokens(kind: BrowseKind, file: File, paths: List<String>, roots: List<File> = PlaylistItems.savedRoots(kind)): Result<Int> = runCatching {
        require(kind != BrowseKind.SRC) { "Sources playlists are not token lists" }
        val tokens = paths.map { token(kind, it, roots) }
        val text = when (kind) {
            BrowseKind.FX -> json.decodeFromString<FXPlaylistDto>(file.readText()).let { json.encodeToString(FXPlaylistDto.serializer(), it.copy(items = it.items + tokens)) }
            else -> json.decodeFromString<TransitionPlaylistDto>(file.readText()).let { json.encodeToString(TransitionPlaylistDto.serializer(), it.copy(items = it.items + tokens)) }
        }
        llm.slop.liquidlsd.utils.AtomicFile.write(file, text)
        tokens.size
    }

    /** Appends saved preset [paths] to the end of [playlist] and saves it once; on a failed save the playlist is left unchanged in memory too. */
    fun appendPresets(playlist: PlaylistManager.Playlist, paths: List<String>): Result<Int> {
        val before = playlist.presets.toList()
        for (p in paths) PlaylistManager.insertPreset(playlist, p, playlist.presets.size, autoSave = false)
        return PlaylistManager.savePlaylist(playlist).map { paths.size }.onFailure {
            playlist.presets.clear()
            playlist.presets.addAll(before)
        }
    }

    /**
     * Appends [paths] to the playlist [file] of [kind]. A Sources playlist open in the Library is edited in place so its list updates;
     * every listing is re-read afterwards.
     */
    fun append(kind: BrowseKind, file: File, paths: List<String>): Result<Int> {
        val result = when (kind) {
            BrowseKind.SRC -> {
                val open = LibraryPanel.activePlaylistData?.takeIf { File(it.filePath).absoluteFile == file.absoluteFile }
                (open?.let { Result.success(it) } ?: PlaylistManager.loadPlaylist(file)).mapCatching { appendPresets(it, paths).getOrThrow() }
            }
            else -> appendTokens(kind, file, paths)
        }
        if (result.isSuccess) LibraryPanel.refreshAssets()
        return result
    }

    /** The playlist of [kind] selected in the Library tree, which the menu offers as a one-click item. */
    fun selected(kind: BrowseKind): File? = when (kind) {
        BrowseKind.SRC -> LibraryPanel.selectedPlaylistFile
        BrowseKind.FX -> LibraryPanel.selectedFxPlaylistFile
        BrowseKind.TRANS -> LibraryPanel.selectedTransitionPlaylistFile
    }?.takeIf { it.isFile }
}
