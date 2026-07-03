package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiTreeNodeFlags
import mu.KotlinLogging
import java.io.File

/**
 * Center Panel: Playlist Designer
 * Two states: Browser Mode (shows playlists) and Editor Mode (edits a specific playlist).
 */
object PlaylistEditorPanel {
    private val logger = KotlinLogging.logger {}
    
    private enum class State {
        BROWSER,
        EDITOR
    }
    
    private var currentState = State.BROWSER
    private var currentDirectory: File = FileSystemManager.getPlaylistsRoot()
    private var playlists: List<AssetItem> = emptyList()
    private var selectedPlaylist: AssetItem? = null
    private var showSidebar = true
    
    // Editor state
    private var editingPlaylist: PlaylistManager.Playlist? = null
    private var draggedPatchIndex: Int? = null
    private var dropTargetIndex: Int? = null
    private val nameBuffer = imgui.type.ImString(256)
    
    private val relinkBrowser = ImGuiFileBrowser("relinkPatch")
    private var relinkTargetIndex: Int? = null
    private var pendingPlaylistAction: (() -> Unit)? = null
    
    init {
        refreshPlaylists()
    }
    
    fun draw(width: Float, height: Float) {
        relinkBrowser.draw { file ->
            val playlist = editingPlaylist
            val index = relinkTargetIndex
            if (playlist != null && index != null) {
                PlaylistManager.relinkPatch(playlist, index, file.absolutePath).onSuccess {
                    logger.info { "Relinked patch at index $index to ${file.name}" }
                }
            }
            relinkTargetIndex = null
        }

        when (currentState) {
            State.BROWSER -> drawBrowserMode(width, height)
            State.EDITOR -> drawEditorMode(width, height)
        }

        // Execute pending modifications outside the loop
        pendingPlaylistAction?.let {
            it.invoke()
            pendingPlaylistAction = null
        }
    }
    
    private fun drawBrowserMode(width: Float, height: Float) {
        val sidebarWidth = if (showSidebar) width * 0.3f else 0f
        val mainWidth = width - sidebarWidth
        
        // Header
        ImGui.text("Playlists")
        ImGui.sameLine()
        if (ImGui.smallButton(if (showSidebar) "◀" else "▶")) {
            showSidebar = !showSidebar
        }
        ImGui.sameLine()
        if (ImGui.button("➕ New Playlist")) {
            ImGui.openPopup("NewPlaylistPopup")
        }
        
        ImGui.separator()
        
        // Two-column layout
        if (showSidebar) {
            ImGui.beginChild("PlaylistSidebar", sidebarWidth - 5f, height - 60f, true)
            drawFolderTree(FileSystemManager.getPlaylistsRoot())
            ImGui.endChild()
            ImGui.sameLine()
        }
        
        ImGui.beginChild("PlaylistMain", mainWidth, height - 60f, true)
        drawPlaylistList()
        ImGui.endChild()
        
        drawNewPlaylistPopup()
    }
    
    private fun drawEditorMode(width: Float, height: Float) {
        val playlist = editingPlaylist ?: return
        
        // Header with back button
        if (ImGui.button("← Back")) {
            closeEditor()
        }
        ImGui.sameLine()
        ImGui.text("Editing: ${playlist.name}")
        ImGui.sameLine()
        if (playlist.isDirty) {
            ImGui.textColored(1f, 0.7f, 0.3f, 1f, "*")
        }
        ImGui.sameLine()
        if (ImGui.button("💾 Save")) {
            PlaylistManager.savePlaylist(playlist).onSuccess {
                logger.info { "Saved playlist: ${playlist.name}" }
            }.onFailure {
                logger.error(it) { "Failed to save playlist" }
            }
        }
        
        ImGui.separator()
        
        // Validation warnings
        val missingPatches = playlist.validatePatches()
        if (missingPatches.isNotEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.3f, 0.3f, 1f)
            ImGui.text("⚠ ${missingPatches.size} missing patch(es)")
            ImGui.popStyleColor()
            ImGui.separator()
        }
        
        // Patch list
        ImGui.beginChild("PatchList", width, height - 120f, true)
        drawPatchList(playlist)
        ImGui.endChild()

        // Handle dropping onto the background of the patch list child window
        if (ImGui.beginDragDropTarget()) {
            handleDragDrop(playlist, playlist.patches.size)
            ImGui.endDragDropTarget()
        }
        
        // Large drop zone at the bottom for appending (visual indicator)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ChildBg, 0.15f, 0.15f, 0.15f, 1f)
        ImGui.beginChild("AppendZone", 0f, 60f, true)
        ImGui.textDisabled("Drop patches here to append to end")
        ImGui.endChild()
        
        if (ImGui.beginDragDropTarget()) {
            handleDragDrop(playlist, playlist.patches.size)
            ImGui.endDragDropTarget()
        }
        ImGui.popStyleColor()
    }
    
    private fun drawFolderTree(root: File) {
        val flags = ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.OpenOnDoubleClick
        val isSelected = currentDirectory.absolutePath == root.absolutePath
        val nodeFlags = if (isSelected) flags or ImGuiTreeNodeFlags.Selected else flags
        
        val hasChildren = root.listFiles()?.any { it.isDirectory } == true
        val finalFlags = if (hasChildren) nodeFlags else nodeFlags or ImGuiTreeNodeFlags.Leaf
        
        val opened = ImGui.treeNodeEx(root.name, finalFlags)
        
        if (ImGui.isItemClicked()) {
            currentDirectory = root
            refreshPlaylists()
        }
        
        if (opened) {
            root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { subDir ->
                drawFolderTree(subDir)
            }
            ImGui.treePop()
        }
    }
    
    private fun drawPlaylistList() {
        if (ImGui.button("↑ Parent")) {
            currentDirectory.parentFile?.let {
                currentDirectory = it
                refreshPlaylists()
            }
        }
        ImGui.sameLine()
        if (ImGui.button("🔄 Refresh")) {
            refreshPlaylists()
        }
        
        ImGui.separator()
        
        playlists.forEachIndexed { index, playlist ->
            ImGui.pushID(index)
            
            val icon = if (playlist.type == AssetType.FOLDER) "📁" else "📋"
            val isSelected = selectedPlaylist == playlist
            
            if (!playlist.isValid) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.3f, 0.3f, 1f)
            }
            
            if (ImGui.selectable("$icon ${playlist.displayName}", isSelected)) {
                selectedPlaylist = playlist
                if (playlist.type == AssetType.FOLDER) {
                    currentDirectory = File(playlist.path)
                    refreshPlaylists()
                }
            }

            // Drop target for adding patches/playlists to other playlists in browser mode
            if (playlist.type == AssetType.PLAYLIST && ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (payload != null) {
                    val droppedPath = payload
                    val droppedFile = File(droppedPath)
                    when (droppedFile.extension.lowercase()) {
                        "patch", "lsd", "json" -> {
                            PlaylistManager.loadPlaylist(File(playlist.path)).onSuccess { pl ->
                                PlaylistManager.insertPatch(pl, droppedPath, pl.patches.size).onSuccess {
                                    PlaylistManager.savePlaylist(pl).onSuccess {
                                        logger.info { "Added patch ${droppedFile.name} to playlist ${playlist.name} via browser drag-drop" }
                                        refreshPlaylists()
                                    }
                                }
                            }
                        }
                        "playlist", "lsdset" -> {
                            PlaylistManager.loadPlaylist(File(playlist.path)).onSuccess { targetPl ->
                                PlaylistManager.unpackPlaylistInto(targetPl, droppedPath, targetPl.patches.size).onSuccess {
                                    PlaylistManager.savePlaylist(targetPl).onSuccess {
                                        logger.info { "Unpacked playlist ${droppedFile.name} into ${playlist.name} via browser drag-drop" }
                                        refreshPlaylists()
                                    }
                                }
                            }
                        }
                    }
                }
                ImGui.endDragDropTarget()
            }
            
            // Double-click to open in editor
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0) && playlist.type == AssetType.PLAYLIST) {
                openPlaylist(playlist.path)
            }
            
            if (!playlist.isValid) {
                ImGui.popStyleColor()
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(playlist.errorMessage ?: "Invalid playlist")
                }
            }
            
            ImGui.popID()
        }
    }
    
    private fun drawPatchList(playlist: PlaylistManager.Playlist) {
        playlist.patches.forEachIndexed { index, patchPath ->
            ImGui.pushID(index)
            
            val patchFile = PlaylistManager.resolvePatch(patchPath)
            val exists = patchFile.exists()
            val displayName = patchFile.nameWithoutExtension.ifBlank { patchPath }
            
            // Visual indicator for missing patches
            if (!exists) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.3f, 0.3f, 1f)
            }
            
            val label = if (exists) {
                "$index. 🎨 $displayName"
            } else {
                "$index. ⚠ $displayName (missing)"
            }
            
            val isSelected = false // TODO: Track selection
            if (ImGui.selectable(label, isSelected)) {
                // Select patch
            }
            
            if (ImGui.isItemHovered() && !exists) {
                ImGui.setTooltip("Path: $patchPath\nNot found in absolute path or presets/patches/")
            }
            
            // Drag source for reordering
            if (ImGui.beginDragDropSource()) {
                draggedPatchIndex = index
                ImGui.setDragDropPayload("PLAYLIST_PATCH", index as Any)
                ImGui.text(displayName)
                ImGui.endDragDropSource()
            }
            
            // Drop target for insertion
            if (ImGui.beginDragDropTarget()) {
                handleDragDrop(playlist, index)
                ImGui.endDragDropTarget()
            }
            
            // Context menu
            if (ImGui.isItemClicked(1)) {
                ImGui.openPopup("PatchContextMenu##$index")
            }
            
            if (ImGui.beginPopup("PatchContextMenu##$index")) {
                ImGui.text(displayName)
                ImGui.separator()
                
                if (ImGui.menuItem("Remove")) {
                    pendingPlaylistAction = {
                        PlaylistManager.removePatch(playlist, index).onFailure {
                            logger.error(it) { "Failed to remove patch" }
                        }
                    }
                }
                
                if (ImGui.menuItem("Relink...")) {
                    relinkTargetIndex = index
                    relinkBrowser.open(
                        ImGuiFileBrowser.Mode.LOAD,
                        FileSystemManager.getPatchesRoot(),
                        extensions = listOf(".lsd", ".patch", ".json")
                    )
                }
                
                ImGui.endPopup()
            }
            
            if (!exists) {
                ImGui.popStyleColor()
            }
            
            ImGui.popID()
        }
    }
    
    private fun handleDragDrop(playlist: PlaylistManager.Playlist, insertIndex: Int) {
        // Handle patch reordering within playlist
        val patchPayload = ImGui.acceptDragDropPayload<Int>("PLAYLIST_PATCH")
        if (patchPayload != null) {
            val fromIndex = patchPayload
            logger.info { "Moving patch from $fromIndex to $insertIndex" }
            if (fromIndex != insertIndex) {
                pendingPlaylistAction = {
                    PlaylistManager.movePatch(playlist, fromIndex, insertIndex).onFailure {
                        logger.error(it) { "Failed to move patch" }
                    }
                }
            }
            return
        }
        
        // Handle drag from asset browser
        val assetPayload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
        if (assetPayload != null) {
            val assetPath = assetPayload
            val file = File(assetPath)
            logger.info { "Dropped asset: $assetPath (ext: ${file.extension}) at index $insertIndex" }
            
            when (file.extension.lowercase()) {
                "patch", "lsd", "json" -> {
                    // Insert single patch
                    pendingPlaylistAction = {
                        PlaylistManager.insertPatch(playlist, assetPath, insertIndex).onSuccess {
                            logger.info { "Successfully inserted patch: ${file.nameWithoutExtension}" }
                        }.onFailure {
                            logger.error(it) { "Failed to insert patch" }
                        }
                    }
                }
                "playlist", "lsdset" -> {
                    // Flat unpack: extract all patches from source playlist
                    pendingPlaylistAction = {
                        PlaylistManager.unpackPlaylistInto(playlist, assetPath, insertIndex).onSuccess {
                            logger.info { "Successfully unpacked playlist: ${file.nameWithoutExtension}" }
                        }.onFailure {
                            logger.error(it) { "Failed to unpack playlist" }
                        }
                    }
                }
                else -> {
                    logger.warn { "Unsupported file extension for playlist: ${file.extension}" }
                }
            }
        }
    }
    
    private fun drawNewPlaylistPopup() {
        if (ImGui.beginPopupModal("NewPlaylistPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Create New Playlist")
            ImGui.separator()
            
            ImGui.inputText("Name", nameBuffer)
            
            if (ImGui.button("Create", 120f, 0f)) {
                val name = nameBuffer.get()
                if (name.isNotBlank()) {
                    PlaylistManager.createPlaylist(name, currentDirectory).onSuccess { newPlaylist ->
                        refreshPlaylists()
                        openPlaylist(newPlaylist.filePath)
                        logger.info { "Created playlist: $name" }
                        nameBuffer.set("")
                    }.onFailure {
                        logger.error(it) { "Failed to create playlist" }
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
    
    fun openPlaylist(path: String) {
        val file = File(path)
        PlaylistManager.loadPlaylist(file).onSuccess { playlist ->
            editingPlaylist = playlist
            currentState = State.EDITOR
            logger.info { "Opened playlist: ${playlist.name}" }
        }.onFailure {
            logger.error(it) { "Failed to open playlist: ${file.name}" }
        }
    }
    
    private fun closeEditor() {
        val playlist = editingPlaylist
        if (playlist != null && playlist.isDirty) {
            // TODO: Show save confirmation dialog
            logger.warn { "Closing dirty playlist: ${playlist.name}" }
        }
        editingPlaylist = null
        currentState = State.BROWSER
        refreshPlaylists()
    }
    
    private fun refreshPlaylists() {
        val allAssets = FileSystemManager.scanDirectory(currentDirectory)
        playlists = allAssets.filter { it.type == AssetType.PLAYLIST || it.type == AssetType.FOLDER }
        logger.debug { "Refreshed playlists: ${playlists.size} items" }
    }
}
