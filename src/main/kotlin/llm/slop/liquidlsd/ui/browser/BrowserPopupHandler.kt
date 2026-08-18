package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.type.ImString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.ui.AssetBrowserPanel
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.LibraryView
import llm.slop.liquidlsd.ui.PlaylistManager
import llm.slop.liquidlsd.ui.SavePresetModal
import mu.KotlinLogging
import java.io.File

object BrowserPopupHandler {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    var renameTarget: AssetItem? = null
    var deleteTarget: AssetItem? = null
    var pendingOpenRenamePopup = false
    var pendingOpenDeletePopup = false
    
    val renameBuffer = ImString(256)
    val newPlaylistNameBuffer = ImString(256)
    val exportQueueNameBuffer = ImString(256)

    fun openRenamePresetModal(asset: AssetItem) {
        val file = File(asset.path)
        val currentTags = loadPresetTags(file)
        SavePresetModal.request(
            title = "Rename / Edit Preset Tags",
            confirmLabel = "Save",
            defaultName = asset.name,
            defaultTags = currentTags,
            originalPath = asset.path
        ) { newName, newTags ->
            val oldPath = asset.path
            val cleanName = newName.removeSuffix(".lsd").trim()
            if (cleanName.isBlank()) return@request

            val targetPath = if (cleanName != asset.name) {
                val result = FileSystemManager.renameFile(oldPath, cleanName)
                if (result.isFailure) {
                    logger.error { "Failed to rename preset: ${result.exceptionOrNull()?.message}" }
                    return@request
                }
                val newPath = result.getOrThrow()
                PlaylistManager.updatePresetPathInAllPlaylists(oldPath, newPath)
                AssetBrowserPanel.activePlaylistData = null
                newPath
            } else {
                oldPath
            }

            updatePresetFileMeta(File(targetPath), cleanName, newTags)
            AssetBrowserPanel.refreshAssets()
        }
    }

    fun openDuplicatePresetModal(asset: AssetItem, onDuplicateSuccess: ((String) -> Unit)? = null) {
        val file = File(asset.path)
        val currentTags = loadPresetTags(file)
        val defaultCloneName = "${asset.name}_copy"
        SavePresetModal.request(
            title = "Duplicate Preset",
            confirmLabel = "Duplicate",
            defaultName = defaultCloneName,
            defaultTags = currentTags
        ) { newName, newTags ->
            val cleanName = newName.removeSuffix(".lsd").trim()
            if (cleanName.isBlank()) return@request

            val parentDir = file.parentFile ?: FileSystemManager.getPresetsRoot()
            val targetFile = File(parentDir, "$cleanName.lsd")
            if (targetFile.exists()) return@request

            try {
                val content = file.readText()
                val dto = json.decodeFromString<DeckPresetDto>(content)
                val updated = dto.copy(name = cleanName, tags = newTags)
                targetFile.writeText(json.encodeToString(DeckPresetDto.serializer(), updated))
                FileSystemManager.clearScanCache()
                AssetBrowserPanel.refreshAssets()
                onDuplicateSuccess?.invoke(targetFile.absolutePath)
            } catch (e: Exception) {
                logger.error(e) { "Failed to duplicate preset DTO; falling back to file copy" }
                FileSystemManager.cloneFile(asset.path).onSuccess { newPath ->
                    AssetBrowserPanel.refreshAssets()
                    onDuplicateSuccess?.invoke(newPath)
                }
            }
        }
    }

    private fun loadPresetTags(file: File): List<String> {
        return try {
            if (!file.exists()) return emptyList()
            val dto = json.decodeFromString<DeckPresetDto>(file.readText())
            dto.tags
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updatePresetFileMeta(file: File, newName: String, newTags: List<String>) {
        try {
            if (!file.exists()) return
            val dto = json.decodeFromString<DeckPresetDto>(file.readText())
            val updated = dto.copy(name = newName, tags = newTags)
            file.writeText(json.encodeToString(DeckPresetDto.serializer(), updated))
        } catch (e: Exception) {
            logger.error(e) { "Failed to update preset file metadata for ${file.name}" }
        }
    }

    fun drawRenameAssetPopup() {
        if (ImGui.beginPopupModal("RenameAssetPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            val target = renameTarget
            if (target == null) {
                ImGui.closeCurrentPopup()
                ImGui.endPopup()
                return
            }
            
            val typeStr = when (target.type) {
                AssetType.PRESET -> "Preset"
                AssetType.PLAYLIST -> "Playlist"
                AssetType.FOLDER -> "Folder"
            }
            
            ImGui.text("Rename $typeStr to:")
            ImGui.inputText("##renameAssetInput", renameBuffer)
            
            if (ImGui.button("Rename", 120f, 0f)) {
                val newName = renameBuffer.get().trim()
                if (newName.isNotBlank()) {
                    FileSystemManager.renameFile(target.path, newName).onSuccess { newPath ->
                        if (target.type == AssetType.PRESET) {
                            PlaylistManager.updatePresetPathInAllPlaylists(target.path, newPath)
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
                AssetType.PRESET -> "Preset"
                AssetType.PLAYLIST -> "Playlist"
                AssetType.FOLDER -> "Folder"
            }
            
            ImGui.text("Delete $typeStr ${target.name}?")
            ImGui.text("This action cannot be undone.")
            ImGui.separator()
            if (ImGui.button("Delete", 120f, 0f)) {
                FileSystemManager.deleteFile(target.path).onSuccess {
                    if (target.type == AssetType.PRESET) {
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
                            PlaylistManager.insertPreset(playlist, queueFile.absolutePath, playlist.presets.size)
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
