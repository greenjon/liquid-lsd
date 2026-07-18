package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import llm.slop.liquidlsd.patches.PlayQueueManager
import llm.slop.liquidlsd.patches.PatchManager
import llm.slop.liquidlsd.rendering.Mixer
import mu.KotlinLogging
import java.io.File
import llm.slop.liquidlsd.ui.browser.BrowserPopupHandler
import llm.slop.liquidlsd.ui.browser.SidebarPanel
import llm.slop.liquidlsd.ui.browser.PlaylistEditorPanel
import llm.slop.liquidlsd.ui.browser.QueueActionsPanel

sealed class LibraryView {
    object PlaylistsRoot : LibraryView()
    data class SpecificPlaylist(val playlistFile: File) : LibraryView()
    data class Patches(val currentDir: File) : LibraryView()
}

object AssetBrowserPanel {
    private val logger = KotlinLogging.logger {}
    
    
    internal var currentDirectory: File
        get() = when (val view = SidebarPanel.currentView) {
            is LibraryView.Patches -> view.currentDir
            else -> FileSystemManager.getPatchesRoot()
        }
        set(value) {
            SidebarPanel.currentView = LibraryView.Patches(value)
        }
        
    private var assets: List<AssetItem> = emptyList()
    private var selectedAsset: AssetItem? = null
    private var showSidebar = true
    
    private val searchBuffer = ImString(256)
    internal var activePlaylistData: PlaylistManager.Playlist? = null
    
    private fun getOrLoadPlaylist(file: File): PlaylistManager.Playlist? {
        val current = activePlaylistData
        if (current != null && current.filePath == file.absolutePath) {
            return current
        }
        PlaylistManager.loadPlaylist(file).onSuccess { playlist ->
            activePlaylistData = playlist
            return playlist
        }
        return null
    }
    
    init {
        refreshAssets()
    }
    
    fun draw(width: Float, height: Float, mixer: Mixer) {
        val sidebarWidth = if (showSidebar) width * 0.33f else 0f
        val centerWidth = if (showSidebar) width * 0.33f else width * 0.5f
        val queueWidth = width - sidebarWidth - centerWidth

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), 6f)
        if (ImGui.beginMenuBar()) {
            val toggleIcon = if (showSidebar) Icons.MINUS else Icons.PANEL_LEFT_OPEN

            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1f, 1f, 1f, 0.1f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 0.2f)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f)

            if (ImGui.button("$toggleIcon##toggle_sidebar")) {
                showSidebar = !showSidebar
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Show/hide the library folders and playlists sidebar.")
            }
            ImGui.popStyleColor(4)

            UITheme.AssetBrowserMode.entries.forEach { mode ->
                val active = UITheme.assetBrowserMode == mode
                val icon = when (mode) {
                    UITheme.AssetBrowserMode.FULL -> Icons.LAYOUT_FULL
                    UITheme.AssetBrowserMode.HALF -> Icons.LAYOUT_HALF
                    UITheme.AssetBrowserMode.HIDE -> Icons.LAYOUT_HIDE
                }

                ImGui.sameLine(0f, 6f)

                // Transparent button background style
                ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1f, 1f, 1f, 0.1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 0.2f)

                // Text color: bright white for active, dimmed for inactive
                if (active) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 1f, 1f, 1f, 1.0f)
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f)
                }

                if (ImGui.button("$icon##mode_${mode.name}")) {
                    UITheme.assetBrowserMode = mode
                    UITheme.saveSettings()
                }
                if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                    val modeDesc = when (mode) {
                        UITheme.AssetBrowserMode.FULL -> "Switch asset browser height to Full size."
                        UITheme.AssetBrowserMode.HALF -> "Switch asset browser height to Half size."
                        UITheme.AssetBrowserMode.HIDE -> "Hide the asset browser."
                    }
                    ImGui.setTooltip(modeDesc)
                }
                ImGui.popStyleColor(4)
            }

            ImGui.endMenuBar()
        }
        ImGui.popStyleVar()

        if (UITheme.assetBrowserMode == UITheme.AssetBrowserMode.HIDE) return

        val contentH = ImGui.getContentRegionAvailY() - 5f
        if (showSidebar) {
            ImGui.beginChild("AssetSidebar", sidebarWidth - 6f, contentH, true)
            SidebarPanel.draw(mixer)
            ImGui.endChild()
            ImGui.sameLine()
        }

        ImGui.beginChild("AssetCenter", centerWidth - 6f, contentH, true)
        drawCenterContent(mixer)
        ImGui.endChild()
        ImGui.sameLine()

        ImGui.beginChild("AssetQueue", queueWidth - 8f, contentH, true)
        QueueActionsPanel.draw(mixer)
        ImGui.endChild()

        // Deferred popup opens: ImGui does not allow openPopup() from inside a context menu popup.
        // Flags are set inside the context menu block, and the actual open happens here, outside all popups.
        if (BrowserPopupHandler.pendingOpenRenamePopup) {
            ImGui.openPopup("RenameAssetPopup")
            BrowserPopupHandler.pendingOpenRenamePopup = false
        }
        if (BrowserPopupHandler.pendingOpenDeletePopup) {
            ImGui.openPopup("ConfirmDeleteAssetPopup")
            BrowserPopupHandler.pendingOpenDeletePopup = false
        }
        BrowserPopupHandler.drawRenameAssetPopup()
        BrowserPopupHandler.drawDeleteAssetConfirmationPopup()
    }
    

    private fun drawCenterContent(mixer: Mixer) {
        when (val view = SidebarPanel.currentView) {
            is LibraryView.PlaylistsRoot -> drawPlaylistsRootView()
            is LibraryView.SpecificPlaylist -> drawSpecificPlaylistView(view.playlistFile, mixer)
            is LibraryView.Patches -> drawPatchesView(view.currentDir, mixer)
        }
    }
    private fun drawPlaylistsRootView() {
        ImGui.textDisabled("Playlists Root Settings")
        ImGui.separator()
        ImGui.spacing()
        
        // Centered-ish clickable text
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 100f)
        val windowWidth = ImGui.getWindowWidth()
        val text = "Create new playlist"
        val textWidth = ImGui.calcTextSize(text).x
        ImGui.setCursorPosX((windowWidth - textWidth) * 0.5f)
        
        ImGui.textColored(0.4f, 0.8f, 1.0f, 1.0f, text)
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
        }
        if (ImGui.isItemClicked()) {
            ImGui.openPopup("NewPlaylistPopup")
        }
        
        BrowserPopupHandler.drawNewPlaylistPopup()
    }

    private fun drawSpecificPlaylistView(playlistFile: File, mixer: Mixer) {
        val playlist = getOrLoadPlaylist(playlistFile)
        if (playlist == null) {
            ImGui.textColored(1f, 0.3f, 0.3f, 1f, "Error loading playlist: ${playlistFile.name}")
            return
        }
        
        // Header Row
        ImGui.text("Playlist: ${playlist.name}")
        if (playlist.isDirty) {
            ImGui.sameLine()
            ImGui.textColored(1f, 0.7f, 0.3f, 1f, "*")
        }
        
        ImGui.sameLine()
        if (ImGui.button("Rename Playlist")) {
            ImGui.openPopup("RenamePlaylistPopup")
        }
        ImGui.sameLine()
        if (ImGui.button("Delete Playlist")) {
            ImGui.openPopup("ConfirmDeletePlaylistPopup")
        }
        ImGui.sameLine()
        if (ImGui.button("Clone Playlist")) {
            if (playlist.isDirty) {
                PlaylistManager.savePlaylist(playlist)
            }
            FileSystemManager.cloneFile(playlistFile.absolutePath).onSuccess { newPath ->
                SidebarPanel.currentView = LibraryView.SpecificPlaylist(File(newPath))
                activePlaylistData = null // force reload
            }
        }
        
        ImGui.sameLine()
        if (ImGui.button("Add to queue")) {
            PlayQueueManager.appendPlaylistToQueue(playlistFile)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Add to the bottom of the queue. Right click for more options.")
        }
        if (ImGui.beginPopupContextItem("playlist_header_add_to_queue_menu")) {
            if (ImGui.menuItem("Play now (and replace queue)")) {
                PlayQueueManager.playPlaylistNow(playlistFile, mixer)
            }
            if (ImGui.menuItem("Insert into the queue after current")) {
                PlayQueueManager.insertPlaylistAfterCurrent(playlistFile)
            }
            if (ImGui.menuItem("Add to the bottom of the queue")) {
                PlayQueueManager.appendPlaylistToQueue(playlistFile)
            }
            ImGui.endPopup()
        }
        
        if (playlist.isDirty) {
            ImGui.sameLine()
            if (ImGui.button("Save")) {
                PlaylistManager.savePlaylist(playlist).onSuccess {
                    logger.info { "Saved playlist: ${playlist.name}" }
                }
            }
        }
        
        ImGui.separator()
        ImGui.spacing()
        
        
        PlaylistEditorPanel.draw(playlist, mixer)
        
        // Rename Playlist Popup
        BrowserPopupHandler.drawRenamePlaylistPopup(playlist)
        
        // Delete Playlist Confirmation Popup
        BrowserPopupHandler.drawDeletePlaylistConfirmationPopup(playlistFile)
    }



    private fun drawPatchesView(currentDir: File, mixer: Mixer) {
        // Header Row
        if (ImGui.button("Refresh Folder")) {
            refreshAssets()
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Re-scan active directory for newly added patch or playlist files.")
        }
        ImGui.sameLine()
        ImGui.inputText("Filter", searchBuffer)
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Type to filter patches by filename.")
        }
        
        ImGui.separator()
        ImGui.spacing()
        
        // List of patches in currentDir
        val filterText = searchBuffer.get().trim().lowercase()
        val filteredAssets = assets.filter { 
            it.type == AssetType.PATCH && (filterText.isEmpty() || it.displayName.lowercase().contains(filterText)) 
        }
        
        filteredAssets.forEachIndexed { index, asset ->
            ImGui.pushID(index)
            
            // Preview buttons: A, B, C
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f)
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0f)

            // Button A (Deck A color: Blue)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.4f, 0.8f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.2f, 0.4f, 0.8f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.4f, 0.8f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.4f, 0.8f, 0.3f)
            if (ImGui.button("A##preview_a_$index", 24f, 24f)) {
                val targetDeck = mixer.deckA
                val isDirty = PatchManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    logger.info { "Loading patch ${asset.name} to Deck A" }
                    PatchManager.loadDeckPresetAsync(File(asset.path), isDeckA = true, isDeckC = false)
                } else {
                    UIManager.triggerDeckDragDrop(File(asset.path), targetDeck, true, mixer)
                }
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Load patch to Deck A.")
            }
            ImGui.popStyleColor(5)

            ImGui.sameLine()

            // Button B (Deck B color: Orange)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.4f, 0.2f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.8f, 0.4f, 0.2f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.8f, 0.4f, 0.2f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.4f, 0.2f, 0.3f)
            if (ImGui.button("B##preview_b_$index", 24f, 24f)) {
                val targetDeck = mixer.deckB
                val isDirty = PatchManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    logger.info { "Loading patch ${asset.name} to Deck B" }
                    PatchManager.loadDeckPresetAsync(File(asset.path), isDeckA = false, isDeckC = false)
                } else {
                    UIManager.triggerDeckDragDrop(File(asset.path), targetDeck, false, mixer)
                }
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Load patch to Deck B.")
            }
            ImGui.popStyleColor(5)

            ImGui.sameLine()

            // Button C (Deck C color: Green)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.7f, 0.5f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.2f, 0.7f, 0.5f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.7f, 0.5f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.7f, 0.5f, 0.3f)
            if (ImGui.button("C##preview_c_$index", 24f, 24f)) {
                val targetDeck = mixer.deckC
                val isDirty = PatchManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    logger.info { "Previewing patch ${asset.name} on Deck C" }
                    PatchManager.loadDeckPresetAsync(File(asset.path), isDeckA = false, isDeckC = true)
                } else {
                    UIManager.triggerDeckDragDrop(File(asset.path), targetDeck, false, mixer)
                }
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Preview patch on Deck C (Preview/C).")
            }
            ImGui.popStyleColor(5)

            ImGui.popStyleVar(2)

            ImGui.sameLine()

            val label = asset.displayName
            val isSelected = selectedAsset == asset
            
            if (ImGui.selectable(label, isSelected)) {
                selectedAsset = asset
            }
            
            // Double-click: Load the patch to the inactive deck (>0% crossfader).
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                val targetIsA = mixer.crossfade.value > 0.0f
                val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB
                val isDirty = PatchManager.isDeckDirty(targetDeck, mixer)
                
                if (!isDirty) {
                    logger.info { "Loading patch ${asset.name} to inactive deck ${if (targetIsA) "A" else "B"}" }
                    PatchManager.loadDeckPresetAsync(File(asset.path), targetIsA)
                } else {
                    UIManager.triggerDeckDragDrop(File(asset.path), targetDeck, targetIsA, mixer)
                }
            }
            
            // Drag source: drag a patch
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
                ImGui.text(asset.name)
                ImGui.endDragDropSource()
            }
            
            // Right-click context menu
            if (ImGui.beginPopupContextItem("patch_context_menu_$index")) {
                if (ImGui.menuItem("Play now (and replace queue)")) {
                    PlayQueueManager.playNow(File(asset.path), mixer)
                }
                if (ImGui.menuItem("Insert into the queue after current")) {
                    PlayQueueManager.insertAfterCurrent(File(asset.path))
                }
                if (ImGui.menuItem("Add to the bottom of the queue")) {
                    PlayQueueManager.appendToQueue(File(asset.path))
                }
                ImGui.separator()
                if (ImGui.menuItem("Rename")) {
                    BrowserPopupHandler.renameTarget = asset
                    BrowserPopupHandler.renameBuffer.set(asset.name)
                    BrowserPopupHandler.pendingOpenRenamePopup = true
                }
                if (ImGui.menuItem("Clone")) {
                    FileSystemManager.cloneFile(asset.path).onSuccess {
                        refreshAssets()
                    }
                }
                if (ImGui.menuItem("Delete")) {
                    BrowserPopupHandler.deleteTarget = asset
                    BrowserPopupHandler.pendingOpenDeletePopup = true
                }
                ImGui.endPopup()
            }
            
            ImGui.popID()
        }
    }



    internal fun refreshAssets() {
        assets = FileSystemManager.scanDirectory(currentDirectory)
        logger.debug { "Refreshed assets: ${assets.size} items in ${currentDirectory.name}" }
    }
    
    fun getSelectedAsset(): AssetItem? = selectedAsset
}
