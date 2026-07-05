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
            drawNavigationSidebar(mixer)
            ImGui.endChild()
            ImGui.sameLine()
        }

        ImGui.beginChild("AssetCenter", centerWidth - 6f, contentH, true)
        drawCenterContent(mixer)
        ImGui.endChild()
        ImGui.sameLine()

        ImGui.beginChild("AssetQueue", queueWidth - 8f, contentH, true)
        drawQueueContent(mixer)
        ImGui.endChild()
    }
    
    private fun drawNavigationSidebar(mixer: Mixer) {
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
            refreshAssets()
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
    

    
    private fun drawCenterContent(mixer: Mixer) {
        when (val view = currentView) {
            is LibraryView.PlaylistsRoot -> drawPlaylistsRootView()
            is LibraryView.SpecificPlaylist -> drawSpecificPlaylistView(view.playlistFile, mixer)
            is LibraryView.Patches -> drawPatchesView(view.currentDir, mixer)
        }
    }

    private fun drawQueueContent(mixer: Mixer) {
        // Header Row
        if (ImGui.checkbox("AUTO-VJ", PlayQueueManager.isAutoVJEnabled)) {
            PlayQueueManager.isAutoVJEnabled = !PlayQueueManager.isAutoVJEnabled
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Enable automatic transition queue. Will cycle through queue patches at set intervals.")
        }
        
        ImGui.sameLine()
        val repeatActive = PlayQueueManager.isRepeatEnabled
        if (repeatActive) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active
            ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.15f, 0.5f, 0.4f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.05f, 0.3f, 0.2f, 1.0f)
        }
        if (ImGui.button("${Icons.REPEAT}##repeatQueue")) {
            PlayQueueManager.isRepeatEnabled = !PlayQueueManager.isRepeatEnabled
        }
        if (repeatActive) {
            ImGui.popStyleColor(4)
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Repeat Queue: cycle back to start when the bottom is reached.")
        }

        ImGui.sameLine()
        val shuffleActive = PlayQueueManager.isShuffleEnabled
        if (shuffleActive) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active
            ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.15f, 0.5f, 0.4f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.05f, 0.3f, 0.2f, 1.0f)
        }
        if (ImGui.button("${Icons.SHUFFLE}##shuffleQueue")) {
            PlayQueueManager.isShuffleEnabled = !PlayQueueManager.isShuffleEnabled
            if (PlayQueueManager.isShuffleEnabled) {
                PlayQueueManager.initializeShuffle()
            }
        }
        if (shuffleActive) {
            ImGui.popStyleColor(4)
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Shuffle Queue: play patches in a random order.")
        }

        ImGui.sameLine()
        if (ImGui.button("Clear")) {
            PlayQueueManager.clearQueue()
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Empty the play queue.")
        }
        ImGui.sameLine()
        if (ImGui.button("Export")) {
            ImGui.openPopup("ExportQueuePopup")
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Save current queue sequence as a new playlist.")
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
            val label = "${index + 1}. ${file.nameWithoutExtension}${if (isActive) " ->" else ""}"
            
            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active
            }
            
            ImGui.selectable("$label##queue_$index", false)
            
            // Drag source (for reordering QUEUE_ITEM within the queue)
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
                // 1. Reordering within the queue
                val queuePayload = ImGui.acceptDragDropPayload<Int>("QUEUE_ITEM")
                if (queuePayload != null) {
                    moveFrom = queuePayload
                    moveTo = index
                }
                
                // 2. Inserting a new asset from the center panel
                val assetPayload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (assetPayload != null) {
                    val file = File(assetPayload)
                    if (file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                        PlayQueueManager.queue.add(index, file)
                        logger.info { "Inserted patch from drag-drop at index $index: ${file.name}" }
                    } else if (file.extension.lowercase() in listOf("playlist", "lsdset")) {
                        val files = PlayQueueManager.parsePlaylist(file)
                        PlayQueueManager.queue.addAll(index, files)
                        logger.info { "Inserted playlist from drag-drop at index $index: ${file.name} (${files.size} items)" }
                    }
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

        // Drop target for the empty space (appending to the bottom of the queue)
        val remainingH = ImGui.getContentRegionAvailY()
        if (remainingH > 5f) {
            ImGui.dummy(ImGui.getWindowWidth(), remainingH)
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
                currentView = LibraryView.SpecificPlaylist(File(newPath))
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
        
        // List of patches in playlist
        var moveFrom = -1
        var moveTo = -1
        var removePatchIndex = -1
        
        playlist.patches.forEachIndexed { index, patchPath ->
            val resolvedFile = PlaylistManager.resolvePatch(patchPath)
            val exists = resolvedFile.exists()
            val displayName = resolvedFile.nameWithoutExtension.ifBlank { patchPath }
            val label = "${index + 1}. ${if (exists) "" else "[!] "}$displayName${if (!exists) " (missing)" else ""}"
            
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
                if (ImGui.menuItem("Play now (and replace queue)")) {
                    PlayQueueManager.playNow(resolvedFile, mixer)
                }
                if (ImGui.menuItem("Insert into the queue after current")) {
                    PlayQueueManager.insertAfterCurrent(resolvedFile)
                }
                if (ImGui.menuItem("Add to the bottom of the queue")) {
                    PlayQueueManager.appendToQueue(resolvedFile)
                }
                ImGui.separator()
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
