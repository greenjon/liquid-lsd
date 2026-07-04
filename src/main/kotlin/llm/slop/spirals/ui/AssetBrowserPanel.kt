package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import llm.slop.spirals.patches.PlayQueueManager
import llm.slop.spirals.patches.PatchManager
import llm.slop.spirals.rendering.Mixer
import mu.KotlinLogging
import java.io.File

sealed class LibraryView {
    object Queue : LibraryView()
    object PlaylistsRoot : LibraryView()
    data class SpecificPlaylist(val playlistFile: File) : LibraryView()
    data class Patches(val currentDir: File) : LibraryView()
}

object AssetBrowserPanel {
    private val logger = KotlinLogging.logger {}
    
    private var currentView: LibraryView = LibraryView.Patches(FileSystemManager.getPatchesRoot())
    
    private var currentDirectory: File
        get() = when (val view = currentView) {
            is LibraryView.Patches -> view.currentDir
            else -> FileSystemManager.getPatchesRoot()
        }
        set(value) {
            currentView = LibraryView.Patches(value)
        }
        
    private var assets: List<AssetItem> = emptyList()
    private var selectedAsset: AssetItem? = null
    private var showSidebar = true
    private var renameTarget: AssetItem? = null
    private val renameBuffer = ImString(256)
    private val folderNameBuffer = ImString(256)
    
    // Context menu state
    private var contextMenuTarget: AssetItem? = null
    
    private val searchBuffer = ImString(256)
    private val newPlaylistNameBuffer = ImString(256)
    private val renamePlaylistBuffer = ImString(256)
    private var activePlaylistData: PlaylistManager.Playlist? = null
    private val exportQueueNameBuffer = ImString(256)
    
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
        val sidebarWidth = if (showSidebar) width * 0.25f else 0f
        val mainWidth = width - sidebarWidth

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
                ImGui.popStyleColor(4)
            }

            ImGui.endMenuBar()
        }
        ImGui.popStyleVar()

        if (UITheme.assetBrowserMode == UITheme.AssetBrowserMode.HIDE) return

        // Two-column layout
        val contentH = ImGui.getContentRegionAvailY() - 5f
        if (showSidebar) {
            ImGui.beginChild("AssetSidebar", sidebarWidth - 5f, contentH, true)
            drawNavigationSidebar()
            ImGui.endChild()
            ImGui.sameLine()
        }

        ImGui.beginChild("AssetMain", mainWidth, contentH, true)
        drawMainContent(mixer)
        ImGui.endChild()
    }
    
    private fun drawNavigationSidebar() {
        // Node 1: Queue
        val queueFlags = ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.SpanAvailWidth or
            (if (currentView is LibraryView.Queue) ImGuiTreeNodeFlags.Selected else 0)
        val queueOpened = ImGui.treeNodeEx("Queue", queueFlags)
        if (ImGui.isItemClicked()) {
            currentView = LibraryView.Queue
        }
        
        // Drag and drop target for Queue
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = File(payload)
                if (file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                    PlayQueueManager.appendToQueue(file)
                } else if (file.extension.lowercase() in listOf("playlist", "lsdset")) {
                    PlayQueueManager.appendPlaylistToQueue(file)
                }
            }
            ImGui.endDragDropTarget()
        }
        
        if (queueOpened) {
            ImGui.treePop()
        }
        
        // Node 2: Playlists
        val isPlaylistsActive = currentView is LibraryView.PlaylistsRoot || currentView is LibraryView.SpecificPlaylist
        val playlistsFlags = ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.OpenOnDoubleClick or ImGuiTreeNodeFlags.SpanAvailWidth or
            (if (isPlaylistsActive) ImGuiTreeNodeFlags.Selected else 0)
        val playlistsOpened = ImGui.treeNodeEx("Playlists", playlistsFlags)
        if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) {
            currentView = LibraryView.PlaylistsRoot
        }
        if (playlistsOpened) {
            drawPlaylistsSidebarTree(FileSystemManager.getPlaylistsRoot())
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
            refreshAssets()
        }
        if (patchesOpened) {
            drawPatchesFolderTree(patchesRoot)
            ImGui.treePop()
        }
    }
    
    private fun drawPlaylistsSidebarTree(root: File) {
        val items = FileSystemManager.scanDirectory(root)
        items.forEach { asset ->
            if (asset.type == AssetType.FOLDER) {
                val flags = ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.OpenOnDoubleClick or ImGuiTreeNodeFlags.SpanAvailWidth
                val opened = ImGui.treeNodeEx("[D] ${asset.name}##sidebar_${asset.path}", flags)
                if (opened) {
                    drawPlaylistsSidebarTree(File(asset.path))
                    ImGui.treePop()
                }
            } else if (asset.type == AssetType.PLAYLIST) {
                val isPlaylistSelected = (currentView as? LibraryView.SpecificPlaylist)?.playlistFile?.absolutePath == asset.path
                val itemFlags = ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.SpanAvailWidth or
                    (if (isPlaylistSelected) ImGuiTreeNodeFlags.Selected else 0)
                
                val itemOpened = ImGui.treeNodeEx("[L] ${asset.displayName}##sidebar_${asset.path}", itemFlags)
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
                    if (ImGui.menuItem("Add to Queue")) {
                        PlayQueueManager.appendPlaylistToQueue(File(asset.path))
                    }
                    ImGui.endPopup()
                }
                
                if (itemOpened) {
                    ImGui.treePop()
                }
            }
        }
    }
    
    private fun handlePatchDropOnPlaylist(patchPath: String, playlistFile: File) {
        val droppedFile = File(patchPath)
        if (droppedFile.extension.lowercase() in listOf("patch", "lsd", "json")) {
            val playlistToModify = if (activePlaylistData?.filePath == playlistFile.absolutePath) {
                activePlaylistData
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
                refreshAssets()
            }
            if (opened) {
                drawPatchesFolderTree(subDir)
                ImGui.treePop()
            }
        }
    }
    

    
    private fun drawMainContent(mixer: Mixer) {
        when (val view = currentView) {
            is LibraryView.Queue -> drawQueueView(mixer)
            is LibraryView.PlaylistsRoot -> drawPlaylistsRootView()
            is LibraryView.SpecificPlaylist -> drawSpecificPlaylistView(view.playlistFile)
            is LibraryView.Patches -> drawPatchesView(view.currentDir, mixer)
        }
    }

    private fun drawQueueView(mixer: Mixer) {
        // Header Row
        if (ImGui.checkbox("AUTO-VJ", PlayQueueManager.isAutoVJEnabled)) {
            PlayQueueManager.isAutoVJEnabled = !PlayQueueManager.isAutoVJEnabled
        }
        ImGui.sameLine()
        if (ImGui.button("Clear Queue")) {
            PlayQueueManager.clearQueue()
        }
        ImGui.sameLine()
        if (ImGui.button("Export Queue")) {
            ImGui.openPopup("ExportQueuePopup")
        }
        drawExportQueuePopup()
        
        ImGui.separator()
        ImGui.spacing()
        
        // Queue list
        var moveFrom = -1
        var moveTo = -1
        var removeFromQueueIndex = -1
        
        PlayQueueManager.queue.forEachIndexed { index, file ->
            val isActive = index == PlayQueueManager.activeIndex
            val label = "${index + 1}. [P] ${file.nameWithoutExtension}${if (isActive) " ->" else ""}"
            
            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active
            }
            
            ImGui.selectable("$label##queue_$index", false)
            
            // Drag source
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("QUEUE_ITEM", index as Any)
                ImGui.text("Moving $label")
                ImGui.endDragDropSource()
            }
            
            if (isActive) {
                ImGui.popStyleColor()
            }
            
            // Drag target
            if (ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<Int>("QUEUE_ITEM")
                if (payload != null) {
                    moveFrom = payload
                    moveTo = index
                }
                ImGui.endDragDropTarget()
            }
            
            // Right-click menu
            if (ImGui.beginPopupContextItem("queue_item_menu_$index")) {
                if (ImGui.menuItem("Remove")) {
                    removeFromQueueIndex = index
                }
                ImGui.endPopup()
            }
        }
        
        if (moveFrom != -1 && moveTo != -1) {
            PlayQueueManager.moveQueueItem(moveFrom, moveTo)
        }
        if (removeFromQueueIndex != -1) {
            PlayQueueManager.removeFromQueue(removeFromQueueIndex)
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
        
        drawNewPlaylistPopup()
    }

    private fun drawSpecificPlaylistView(playlistFile: File) {
        val playlist = getOrLoadPlaylist(playlistFile)
        if (playlist == null) {
            ImGui.textColored(1f, 0.3f, 0.3f, 1f, "Error loading playlist: ${playlistFile.name}")
            return
        }
        
        // Header Row
        ImGui.text("Editing Playlist: ${playlist.name}")
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
        
        // List of patches in playlist
        var moveFrom = -1
        var moveTo = -1
        var removePatchIndex = -1
        
        playlist.patches.forEachIndexed { index, patchPath ->
            val resolvedFile = PlaylistManager.resolvePatch(patchPath)
            val exists = resolvedFile.exists()
            val displayName = resolvedFile.nameWithoutExtension.ifBlank { patchPath }
            val label = "${index + 1}. ${if (exists) "[P]" else "[!]" } $displayName${if (!exists) " (missing)" else ""}"
            
            if (!exists) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.3f, 0.3f, 1f)
            }
            
            ImGui.selectable("$label##playlist_item_$index", false)
            
            // Drag source for reordering within playlist
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("PLAYLIST_PATCH_ITEM", index as Any)
                ImGui.text("Moving $displayName")
                ImGui.endDragDropSource()
            }
            
            if (!exists) {
                ImGui.popStyleColor()
            }
            
            // Drag target for reordering within playlist
            if (ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<Int>("PLAYLIST_PATCH_ITEM")
                if (payload != null) {
                    moveFrom = payload
                    moveTo = index
                }
                ImGui.endDragDropTarget()
            }
            
            // Right-click menu
            if (ImGui.beginPopupContextItem("playlist_item_menu_$index")) {
                if (ImGui.menuItem("Remove")) {
                    removePatchIndex = index
                }
                ImGui.endPopup()
            }
        }
        
        if (moveFrom != -1 && moveTo != -1) {
            PlaylistManager.movePatch(playlist, moveFrom, moveTo).onSuccess {
                // Auto-save playlist when reordering
                PlaylistManager.savePlaylist(playlist)
            }
        }
        
        if (removePatchIndex != -1) {
            PlaylistManager.removePatch(playlist, removePatchIndex).onSuccess {
                // Auto-save playlist when removing
                PlaylistManager.savePlaylist(playlist)
            }
        }
        
        // Rename Playlist Popup
        drawRenamePlaylistPopup(playlist)
        
        // Delete Playlist Confirmation Popup
        drawDeletePlaylistConfirmationPopup(playlistFile)
    }

    private fun drawRenamePlaylistPopup(playlist: PlaylistManager.Playlist) {
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
                        currentView = LibraryView.SpecificPlaylist(File(newPath))
                        activePlaylistData = null // force reload
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

    private fun drawDeletePlaylistConfirmationPopup(playlistFile: File) {
        if (ImGui.beginPopupModal("ConfirmDeletePlaylistPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Delete Playlist ${playlistFile.nameWithoutExtension}?")
            ImGui.text("This action cannot be undone.")
            ImGui.separator()
            if (ImGui.button("Delete", 120f, 0f)) {
                FileSystemManager.deleteFile(playlistFile.absolutePath).onSuccess {
                    currentView = LibraryView.PlaylistsRoot
                    activePlaylistData = null
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

    private fun drawPatchesView(currentDir: File, mixer: Mixer) {
        // Header Row
        if (ImGui.button("Refresh Folder")) {
            refreshAssets()
        }
        ImGui.sameLine()
        ImGui.inputText("Filter", searchBuffer)
        
        ImGui.separator()
        ImGui.spacing()
        
        // List of patches in currentDir
        val filterText = searchBuffer.get().trim().lowercase()
        val filteredAssets = assets.filter { 
            it.type == AssetType.PATCH && (filterText.isEmpty() || it.displayName.lowercase().contains(filterText)) 
        }
        
        filteredAssets.forEachIndexed { index, asset ->
            ImGui.pushID(index)
            
            // Preview button
            if (ImGui.button(">##preview_$index", 24f, 24f)) {
                val isDirty = PatchManager.isDeckDirty(mixer.deckC, mixer)
                if (!isDirty) {
                    logger.info { "Previewing patch ${asset.name} on Deck C" }
                    PatchManager.loadDeckPresetAsync(File(asset.path), isDeckA = false, isDeckC = true)
                } else {
                    UIManager.triggerDeckDragDrop(File(asset.path), mixer.deckC, false, mixer)
                }
            }
            ImGui.sameLine()

            val label = "[P] ${asset.displayName}"
            val isSelected = selectedAsset == asset
            
            if (ImGui.selectable(label, isSelected)) {
                selectedAsset = asset
            }
            
            // Double-click: Load the patch to the inactive deck (>50% crossfader).
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                val targetIsA = mixer.crossfade.value > 0.5f
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
                if (ImGui.menuItem("Add to Queue")) {
                    PlayQueueManager.appendToQueue(File(asset.path))
                }
                ImGui.endPopup()
            }
            
            ImGui.popID()
        }
    }

    private fun drawNewPlaylistPopup() {
        if (ImGui.beginPopupModal("NewPlaylistPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Create New Playlist")
            ImGui.separator()
            ImGui.inputText("Name", newPlaylistNameBuffer)
            if (ImGui.button("Create", 120f, 0f)) {
                val name = newPlaylistNameBuffer.get()
                if (name.isNotBlank()) {
                    PlaylistManager.createPlaylist(name, FileSystemManager.getPlaylistsRoot()).onSuccess { newPlaylist ->
                        currentView = LibraryView.SpecificPlaylist(File(newPlaylist.filePath))
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

    private fun drawExportQueuePopup() {
        if (ImGui.beginPopupModal("ExportQueuePopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Export Queue as Playlist")
            ImGui.separator()
            ImGui.inputText("Playlist Name", exportQueueNameBuffer)
            if (ImGui.button("Export", 120f, 0f)) {
                val name = exportQueueNameBuffer.get().trim()
                if (name.isNotBlank()) {
                    PlaylistManager.createPlaylist(name, FileSystemManager.getPlaylistsRoot()).onSuccess { playlist ->
                        PlayQueueManager.queue.forEach { queueFile ->
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

    private fun refreshAssets() {
        assets = FileSystemManager.scanDirectory(currentDirectory)
        logger.debug { "Refreshed assets: ${assets.size} items in ${currentDirectory.name}" }
    }
    
    fun getSelectedAsset(): AssetItem? = selectedAsset
}
