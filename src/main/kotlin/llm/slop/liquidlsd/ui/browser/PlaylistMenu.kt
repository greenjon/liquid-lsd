package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import llm.slop.liquidlsd.ui.ToastOverlay
import java.io.File

/**
 * The row menu's playlist items, the same on the Sources, FX and Transitions tabs: a one-click `Add to '<name>'` for the playlist
 * selected in the tree, then an `Add to Playlist` submenu listing every playlist of the tab, with each playlist folder as its own
 * submenu and a `New Playlist...` item at every level that creates the playlist there and adds the rows to it.
 */
object PlaylistMenu {
    /** Folder levels read while the menu is open, by "ext|path"; dropped when the menu wasn't drawn last frame. */
    private val levels = HashMap<String, PlaylistTargets.Level>()
    private val selectedFiles = HashMap<BrowseKind, File?>()
    private var lastFrame = -2

    /**
     * Draws the items for [paths] (the saved files of the rows the menu covers; for a stock transition its bundled `.fs`). [count] is
     * how many rows that is, shown in the labels when more than one ("Add 3 to Playlist").
     */
    fun draw(kind: BrowseKind, paths: List<String>, count: Int = paths.size) {
        if (paths.isEmpty()) return
        // The folders are read once per opening of the menu, not every frame it stays open.
        val frame = ImGui.getFrameCount()
        if (frame > lastFrame + 1) { levels.clear(); selectedFiles.clear() }
        lastFrame = frame
        val n = if (count > 1) "$count " else ""
        val selected = selectedFiles.getOrPut(kind) { PlaylistTargets.selected(kind) }
        if (selected != null && ImGui.menuItem("Add ${n}to '${selected.nameWithoutExtension}'")) add(kind, selected, paths)
        val root = LibraryFolders.rootOf(kind, BrowseSection.PLAYLISTS) ?: return
        val ext = LibraryFolders.extensionOf(kind, BrowseSection.PLAYLISTS) ?: return
        if (ImGui.beginMenu("Add ${n}to Playlist")) {
            drawLevel(kind, root, root, ext, paths, selected)
            ImGui.endMenu()
        }
    }

    /** One folder's playlists, then its subfolders as submenus, then `New Playlist...` for that folder. Reads the folder only while it is open. */
    private fun drawLevel(kind: BrowseKind, root: File, dir: File, ext: String, paths: List<String>, selected: File?) {
        val level = levels.getOrPut("$ext|${dir.path}") { PlaylistTargets.level(dir, ext) }
        for (file in level.playlists) {
            if (ImGui.menuItem("${file.nameWithoutExtension}##${file.path}", null, file.absoluteFile == selected?.absoluteFile)) add(kind, file, paths)
        }
        for (folder in level.folders) {
            if (ImGui.beginMenu("${folder.name}##${folder.path}")) {
                drawLevel(kind, root, folder, ext, paths, selected)
                ImGui.endMenu()
            }
        }
        if (level.isEmpty) ImGui.textDisabled("(no playlists)")
        ImGui.separator()
        if (ImGui.menuItem(if (dir == root) "New Playlist...##newPl" else "New Playlist Here...##newPl")) {
            BrowserPopupHandler.openNewPlaylist(kind, dir, paths)
        }
    }

    /** Adds [paths] to [file] (written off the render thread) and says so in a toast when done, or says why not. */
    fun add(kind: BrowseKind, file: File, paths: List<String>) {
        val name = file.nameWithoutExtension
        PlaylistTargets.append(kind, file, paths) { result ->
            result.onSuccess { ToastOverlay.show(if (it == 1) "Added to '$name'" else "Added $it to '$name'") }
                .onFailure { ToastOverlay.show("Couldn't add to '$name': ${it.message ?: "see the log"}") }
        }
    }
}
