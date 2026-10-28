package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.presets.GardenThumbnails
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.PlaylistManager
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The one place that follows a saved library file to its new path, or forgets it, everywhere the app refers to it: playlists,
 * live queues, favorites, recents, the decks' save-back files, FX chains' and slots' source files, and a preset's thumbnail.
 * Rename, move and delete all go through here, so nothing is left pointing at a path that is gone. Call it after the file itself
 * was renamed, moved or deleted on disk.
 */
object LibraryRefs {
    private val logger = KotlinLogging.logger {}

    /** The browser kind a saved file belongs to by its extension, or null for anything that isn't a saved preset. */
    fun kindOf(file: File): BrowseKind? = when (file.extension.lowercase()) {
        "lsd" -> BrowseKind.SRC
        "lsdfx", "lsdfxchain" -> BrowseKind.FX
        "lsdtrans" -> BrowseKind.TRANS
        else -> null
    }

    /** Follows every file in [moves] (old to new, both already on disk as new) wherever it is referenced. [fxChains] are the mixer's. */
    fun relocate(moves: Map<File, File>, fxChains: List<FxChain>) {
        for ((kind, entries) in moves.entries.groupBy { kindOf(it.key) }) {
            if (kind == null) continue
            val paths = entries.associate { it.key.absolutePath to it.value.absolutePath }
            when (kind) {
                BrowseKind.SRC -> {
                    PlaylistManager.updatePresetPathsInAllPlaylists(paths)
                    relocateQueue(PlayQueueManager.queue, paths)
                    relocateQueue(BgQueueManager.queue, paths)
                    for ((old, new) in entries) {
                        PresetManager.relocateActive(old, new)
                        moveThumbnail(old, new)
                    }
                }
                BrowseKind.FX -> {
                    tokenPlaylists(kind).forEach { it.relocateFiles(paths) }
                    relocateQueue(FXQueueManager.queue, paths)
                    relocateQueue(FXBgQueueManager.queue, paths)
                    FxOps.relocateSlotSources(paths)
                    for (chain in fxChains) chain.sourceFile?.let { paths[it.absolutePath] }?.let { chain.sourceFile = File(it) }
                }
                BrowseKind.TRANS -> {
                    tokenPlaylists(kind).forEach { it.relocateFiles(paths) }
                    relocateQueue(TransitionQueueManager.queue, paths)
                }
            }
            BrowseFavorites.relocate(kind, paths)
            BrowseRecents.relocate(kind, paths)
        }
        LibraryPanel.activePlaylistData = null
        LibraryPanel.refreshAssets()
    }

    /** Drops every reference to [files], which were just deleted from disk, and deletes their thumbnails. [fxChains] are the mixer's. */
    fun forget(files: List<File>, fxChains: List<FxChain>) {
        for ((kind, group) in files.groupBy { kindOf(it) }) {
            if (kind == null) continue
            val paths = group.mapTo(HashSet()) { it.absolutePath }
            when (kind) {
                BrowseKind.SRC -> for (file in group) {
                    PlaylistManager.removePresetFromAllPlaylists(file.absolutePath)
                    PlayQueueManager.removeFileFromQueue(file)
                    BgQueueManager.removeFileFromQueue(file)
                    PresetManager.forgetActive(file)
                    GardenThumbnails.thumbnailFor(file).takeIf { it.isFile }?.let { FileSystemManager.deleteFile(it.path) }
                }
                BrowseKind.FX -> {
                    val playlists = tokenPlaylists(kind)
                    for (file in group) {
                        FXQueueManager.removeFileFromQueue(file)
                        FXBgQueueManager.removeFileFromQueue(file)
                        playlists.forEach { it.removeFile(file.absolutePath) }
                    }
                    FxOps.forgetSlotSources(paths)
                    for (chain in fxChains) if (chain.sourceFile?.absolutePath in paths) chain.sourceFile = null
                }
                BrowseKind.TRANS -> {
                    val playlists = tokenPlaylists(kind)
                    for (file in group) {
                        TransitionQueueManager.removeFileFromQueue(file)
                        playlists.forEach { it.removeFile(file.absolutePath) }
                    }
                }
            }
            BrowseFavorites.forget(kind, paths)
            BrowseRecents.forget(kind, paths)
        }
        LibraryPanel.activePlaylistData = null
        LibraryPanel.refreshAssets()
    }

    /** Swaps moved files in place, so the queue's order, active index and shuffle order are untouched. */
    internal fun relocateQueue(queue: CopyOnWriteArrayList<File>, moves: Map<String, String>) {
        queue.replaceAll { f -> moves[f.absolutePath]?.let(::File) ?: f }
    }

    private fun tokenPlaylists(kind: BrowseKind): List<TokenPlaylistEdit> {
        val playlists = if (kind == BrowseKind.FX) FileSystemManager.scanAllFxPlaylists() else FileSystemManager.scanAllTransitionPlaylists()
        return playlists.mapNotNull { TokenPlaylistEdit.open(kind, File(it.path)) }
    }

    /** A preset's thumbnail sidecar moves with it, unless the destination already has one. */
    private fun moveThumbnail(old: File, new: File) {
        val from = GardenThumbnails.thumbnailFor(old)
        val to = GardenThumbnails.thumbnailFor(new)
        if (!from.isFile || to.exists()) return
        if (!from.renameTo(to)) logger.warn { "Could not move thumbnail ${from.path} to ${to.path}" }
    }
}
