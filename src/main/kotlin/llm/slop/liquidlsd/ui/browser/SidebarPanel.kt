package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetBrowserPanel
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.LibraryView
import llm.slop.liquidlsd.patches.PlayQueueManager
import llm.slop.liquidlsd.ui.PlaylistManager
import java.io.File
import mu.KotlinLogging

object SidebarPanel {
    private val logger = KotlinLogging.logger {}
    
    var currentView: LibraryView = LibraryView.Patches(FileSystemManager.getPatchesRoot())

    fun draw(mixer: Mixer) {
        // Node 2: Playlists
        val isPlaylistsActive = currentView is LibraryView.PlaylistsRoot || currentView is LibraryView.SpecificPlaylist
        val playlistsFlags = ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.OpenOnDoubleClick or ImGuiTreeNodeFlags.SpanAvailWidth or
            (if (isPlaylistsActive) ImGuiTreeNodeFlags.Selected else 0)
        val playlistsOpened = ImGui.treeNodeEx("Playlists", playlistsFlags)
        if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) {
            currentView = LibraryView.PlaylistsRoot
        }
        if (playlistsOpened) {
            drawPlaylistsSidebarTree(FileSystemManager.getPlaylistsRoot(), mixer)
            ImGui.treePop()
        }
        
        // Node 3: Patches
        val patchesRoot = FileSystemManager.getPatchesRoot()
        val isPatchesActive = currentView is LibraryView.Patches
        val patchesFlags = ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.OpenOnDoubleClick or ImGuiTreeNodeFlags.SpanAvailWidth or
            (if (isPatchesActive) ImGuiTreeNodeFlags.Selected else 0)
        
        val patchesOpened = ImGui.treeNodeEx("Patches", patchesFlags)
        if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) {
            currentView = LibraryView.Patches(patchesRoot)
            AssetBrowserPanel.refreshAssets()
        }
        if (patchesOpened) {
            drawPatchesFolderTree(patchesRoot)
            ImGui.treePop()
        }
    }

    private fun drawPlaylistsSidebarTree(root: File, mixer: Mixer) {
        val items = FileSystemManager.scanDirectory(root)
        items.forEach { asset ->
            if (asset.type == AssetType.FOLDER) {
                val flags = ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.OpenOnDoubleClick or ImGuiTreeNodeFlags.SpanAvailWidth
                val opened = ImGui.treeNodeEx("[D] ${asset.name}##sidebar_${asset.path}", flags)
                if (opened) {
                    drawPlaylistsSidebarTree(File(asset.path), mixer)
                    ImGui.treePop()
                }
            } else if (asset.type == AssetType.PLAYLIST) {
                val isPlaylistSelected = (currentView as? LibraryView.SpecificPlaylist)?.playlistFile?.absolutePath == asset.path
                val itemFlags = ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.SpanAvailWidth or
                    (if (isPlaylistSelected) ImGuiTreeNodeFlags.Selected else 0)
                
                val itemOpened = ImGui.treeNodeEx("${asset.displayName}##sidebar_${asset.path}", itemFlags)
                if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) {
                    currentView = LibraryView.SpecificPlaylist(File(asset.path))
                }
                
                if (ImGui.beginDragDropTarget()) {
                    val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                    if (payload != null) {
                        handlePatchDropOnPlaylist(payload, File(asset.path))
                    }
                    ImGui.endDragDropTarget()
                }

                if (ImGui.beginPopupContextItem("sidebar_playlist_context_menu_${asset.path}")) {
                    if (ImGui.menuItem("Play now (and replace queue)")) {
                        PlayQueueManager.playPlaylistNow(File(asset.path), mixer)
                    }
                    if (ImGui.menuItem("Insert into the queue after current")) {
                        PlayQueueManager.insertPlaylistAfterCurrent(File(asset.path))
                    }
                    if (ImGui.menuItem("Add to the bottom of the queue")) {
                        PlayQueueManager.appendPlaylistToQueue(File(asset.path))
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Rename")) {
                        BrowserPopupHandler.renameTarget = asset
                        BrowserPopupHandler.renameBuffer.set(asset.name)
                        BrowserPopupHandler.pendingOpenRenamePopup = true
                    }
                    if (ImGui.menuItem("Clone")) {
                        FileSystemManager.cloneFile(asset.path).onSuccess { newPath ->
                            currentView = LibraryView.SpecificPlaylist(File(newPath))
                            AssetBrowserPanel.activePlaylistData = null
                        }
                    }
                    if (ImGui.menuItem("Delete")) {
                        BrowserPopupHandler.deleteTarget = asset
                        BrowserPopupHandler.pendingOpenDeletePopup = true
                    }
                    ImGui.endPopup()
                }
                
                if (itemOpened) {
                    ImGui.treePop()
                }
            }
        }
    }

    private fun drawPatchesFolderTree(root: File) {
        val subdirs = root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        subdirs.forEach { subDir ->
            val isSelected = (currentView as? LibraryView.Patches)?.currentDir?.absolutePath == subDir.absolutePath
            val flags = ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.OpenOnDoubleClick or ImGuiTreeNodeFlags.SpanAvailWidth
            val hasChildren = subDir.listFiles()?.any { it.isDirectory } == true
            val nodeFlags = if (isSelected) flags or ImGuiTreeNodeFlags.Selected else flags
            val finalFlags = if (hasChildren) nodeFlags else nodeFlags or ImGuiTreeNodeFlags.Leaf
            
            val opened = ImGui.treeNodeEx("[D] ${subDir.name}##folder_${subDir.absolutePath}", finalFlags)
            if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) {
                currentView = LibraryView.Patches(subDir)
                AssetBrowserPanel.refreshAssets()
            }
            if (opened) {
                drawPatchesFolderTree(subDir)
                ImGui.treePop()
            }
        }
    }

    private fun handlePatchDropOnPlaylist(patchPath: String, playlistFile: File) {
        val droppedFile = File(patchPath)
        if (droppedFile.extension.lowercase() in listOf("patch", "lsd", "json")) {
            val playlistToModify = if (AssetBrowserPanel.activePlaylistData?.filePath == playlistFile.absolutePath) {
                AssetBrowserPanel.activePlaylistData
            } else {
                PlaylistManager.loadPlaylist(playlistFile).getOrNull()
            }
            
            playlistToModify?.let { playlist ->
                PlaylistManager.insertPatch(playlist, patchPath, playlist.patches.size).onSuccess {
                    PlaylistManager.savePlaylist(playlist).onSuccess {
                        logger.info { "Added patch ${droppedFile.name} to playlist ${playlist.name} via sidebar drag-drop" }
                    }
                }
            }
        }
    }
}
