package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.type.ImString
import llm.slop.liquidlsd.ui.AssetBrowserPanel
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.LibraryView
import llm.slop.liquidlsd.ui.PlaylistManager
import llm.slop.liquidlsd.patches.PlayQueueManager
import java.io.File

object BrowserPopupHandler {
    var renameTarget: AssetItem? = null
    var deleteTarget: AssetItem? = null
    var pendingOpenRenamePopup = false
    var pendingOpenDeletePopup = false
    
    val renameBuffer = ImString(256)
    val newPlaylistNameBuffer = ImString(256)
    val renamePlaylistBuffer = ImString(256)
    val exportQueueNameBuffer = ImString(256)

    fun drawRenameAssetPopup() {
        if (ImGui.beginPopupModal("RenameAssetPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            val target = renameTarget
            if (target == null) {
                ImGui.closeCurrentPopup()
                ImGui.endPopup()
                return
            }
            
            val typeStr = when (target.type) {
                AssetType.PATCH -> "Patch"
                AssetType.PLAYLIST -> "Playlist"
                AssetType.FOLDER -> "Folder"
            }
            
            ImGui.text("Rename $typeStr to:")
            ImGui.inputText("##renameAssetInput", renameBuffer)
            
            if (ImGui.button("Rename", 120f, 0f)) {
                val newName = renameBuffer.get().trim()
                if (newName.isNotBlank()) {
                    FileSystemManager.renameFile(target.path, newName).onSuccess { newPath ->
                        if (target.type == AssetType.PATCH) {
                            PlaylistManager.updatePatchPathInAllPlaylists(target.path, newPath)
                            AssetBrowserPanel.activePlaylistData = null
                            AssetBrowserPanel.refreshAssets()
                        } else if (target.type == AssetType.PLAYLIST) {
                            val currentPlaylistPath = (SidebarPanel.currentView as? LibraryView.SpecificPlaylist)?.playlistFile?.absolutePath
                            if (target.path == currentPlaylistPath) {
                                SidebarPanel.currentView = LibraryView.SpecificPlaylist(File(newPath))
                                AssetBrowserPanel.activePlaylistData = null
                            }
                        }
                    }
                }
                renameBuffer.set("")
                renameTarget = null
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 120f, 0f)) {
                renameBuffer.set("")
                renameTarget = null
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawDeleteAssetConfirmationPopup() {
        if (ImGui.beginPopupModal("ConfirmDeleteAssetPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            val target = deleteTarget
            if (target == null) {
                ImGui.closeCurrentPopup()
                ImGui.endPopup()
                return
            }
            
            val typeStr = when (target.type) {
                AssetType.PATCH -> "Patch"
                AssetType.PLAYLIST -> "Playlist"
                AssetType.FOLDER -> "Folder"
            }
            
            ImGui.text("Delete $typeStr ${target.name}?")
            ImGui.text("This action cannot be undone.")
            ImGui.separator()
            if (ImGui.button("Delete", 120f, 0f)) {
                FileSystemManager.deleteFile(target.path).onSuccess {
                    if (target.type == AssetType.PATCH) {
                        AssetBrowserPanel.refreshAssets()
                    } else if (target.type == AssetType.PLAYLIST) {
                        val currentPlaylistPath = (SidebarPanel.currentView as? LibraryView.SpecificPlaylist)?.playlistFile?.absolutePath
                        if (target.path == currentPlaylistPath) {
                            SidebarPanel.currentView = LibraryView.PlaylistsRoot
                            AssetBrowserPanel.activePlaylistData = null
                        }
                    }
                }
                deleteTarget = null
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 120f, 0f)) {
                deleteTarget = null
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawRenamePlaylistPopup(playlist: PlaylistManager.Playlist) {
        if (ImGui.beginPopupModal("RenamePlaylistPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Rename Playlist to:")
            if (renamePlaylistBuffer.get().isBlank()) {
                renamePlaylistBuffer.set(playlist.name)
            }
            ImGui.inputText("##renamePlaylistInput", renamePlaylistBuffer)
            if (ImGui.button("Rename", 120f, 0f)) {
                val newName = renamePlaylistBuffer.get().trim()
                if (newName.isNotBlank()) {
                    FileSystemManager.renameFile(playlist.filePath, newName).onSuccess { newPath ->
                        SidebarPanel.currentView = LibraryView.SpecificPlaylist(File(newPath))
                        AssetBrowserPanel.activePlaylistData = null
                    }
                }
                renamePlaylistBuffer.set("")
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 120f, 0f)) {
                renamePlaylistBuffer.set("")
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawDeletePlaylistConfirmationPopup(playlistFile: File) {
        if (ImGui.beginPopupModal("ConfirmDeletePlaylistPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Delete Playlist ${playlistFile.nameWithoutExtension}?")
            ImGui.text("This action cannot be undone.")
            ImGui.separator()
            if (ImGui.button("Delete", 120f, 0f)) {
                FileSystemManager.deleteFile(playlistFile.absolutePath).onSuccess {
                    SidebarPanel.currentView = LibraryView.PlaylistsRoot
                    AssetBrowserPanel.activePlaylistData = null
                }
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 120f, 0f)) {
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawNewPlaylistPopup() {
        if (ImGui.beginPopupModal("NewPlaylistPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Create New Playlist")
            ImGui.separator()
            ImGui.inputText("Name", newPlaylistNameBuffer)
            if (ImGui.button("Create", 120f, 0f)) {
                val name = newPlaylistNameBuffer.get()
                if (name.isNotBlank()) {
                    PlaylistManager.createPlaylist(name, FileSystemManager.getPlaylistsRoot()).onSuccess { newPlaylist ->
                        SidebarPanel.currentView = LibraryView.SpecificPlaylist(File(newPlaylist.filePath))
                        newPlaylistNameBuffer.set("")
                    }
                }
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 120f, 0f)) {
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawExportQueuePopup(session: llm.slop.liquidlsd.SessionContext) {
        if (ImGui.beginPopupModal("ExportQueuePopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Export Queue as Playlist")
            ImGui.separator()
            ImGui.inputText("Playlist Name", exportQueueNameBuffer)
            if (ImGui.button("Export", 120f, 0f)) {
                val name = exportQueueNameBuffer.get().trim()
                if (name.isNotBlank()) {
                    PlaylistManager.createPlaylist(name, FileSystemManager.getPlaylistsRoot()).onSuccess { playlist ->
                        session.playQueueManager.queue.forEach { queueFile ->
                            PlaylistManager.insertPatch(playlist, queueFile.absolutePath, playlist.patches.size)
                        }
                        PlaylistManager.savePlaylist(playlist)
                    }
                }
                exportQueueNameBuffer.set("")
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 120f, 0f)) {
                exportQueueNameBuffer.set("")
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }
}
