package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import mu.KotlinLogging
import java.io.File

/**
 * Left Panel: Unified Asset Browser
 * Displays both .patch and .playlist files with folder navigation.
 */
object AssetBrowserPanel {
    private val logger = KotlinLogging.logger {}
    
    private var currentDirectory: File = FileSystemManager.getPatchesRoot()
    private var assets: List<AssetItem> = emptyList()
    private var selectedAsset: AssetItem? = null
    private var showSidebar = true
    private var renameTarget: AssetItem? = null
    private val renameBuffer = ImString(256)
    private val folderNameBuffer = ImString(256)
    
    // Context menu state
    private var contextMenuTarget: AssetItem? = null
    
    init {
        refreshAssets()
    }
    
    fun draw(width: Float, height: Float) {
        val sidebarWidth = if (showSidebar) width * 0.3f else 0f
        val mainWidth = width - sidebarWidth
        
        // Header with toggle button
        ImGui.text("Asset Browser")
        ImGui.sameLine()
        if (ImGui.smallButton(if (showSidebar) "◀" else "▶")) {
            showSidebar = !showSidebar
        }
        ImGui.sameLine()
        ImGui.textDisabled("(${currentDirectory.name})")
        
        ImGui.separator()
        
        // Two-column layout
        if (showSidebar) {
            ImGui.beginChild("AssetSidebar", sidebarWidth - 5f, height - 60f, true)
            drawFolderTree(FileSystemManager.getPatchesRoot())
            ImGui.endChild()
            ImGui.sameLine()
        }
        
        ImGui.beginChild("AssetMain", mainWidth, height - 60f, true)
        drawAssetList()
        ImGui.endChild()
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
            refreshAssets()
        }
        
        // Drag target for moving files
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                FileSystemManager.moveFile(payload, root.absolutePath).onSuccess {
                    refreshAssets()
                    logger.info { "Moved asset to ${root.name}" }
                }.onFailure {
                    logger.error(it) { "Failed to move asset" }
                }
            }
            ImGui.endDragDropTarget()
        }
        
        if (opened) {
            root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { subDir ->
                drawFolderTree(subDir)
            }
            ImGui.treePop()
        }
    }
    
    private fun drawAssetList() {
        // Toolbar
        if (ImGui.button("↑ Parent")) {
            currentDirectory.parentFile?.let {
                currentDirectory = it
                refreshAssets()
            }
        }
        ImGui.sameLine()
        if (ImGui.button("🔄 Refresh")) {
            refreshAssets()
        }
        ImGui.sameLine()
        if (ImGui.button("📁 New Folder")) {
            ImGui.openPopup("NewFolderPopup")
        }
        
        ImGui.separator()
        
        // Asset list
        assets.forEachIndexed { index, asset ->
            drawAssetItem(asset, index)
        }
        
        // New folder popup
        drawNewFolderPopup()
        
        // Context menu
        drawContextMenu()
    }
    
    private fun drawAssetItem(asset: AssetItem, index: Int) {
        val isSelected = selectedAsset == asset
        val isRenaming = renameTarget == asset
        
        ImGui.pushID(index)
        
        // Icon based on type
        val icon = when (asset.type) {
            AssetType.FOLDER -> "📁"
            AssetType.PATCH -> "🎨"
            AssetType.PLAYLIST -> "📋"
        }
        
        // Color coding for invalid assets
        if (!asset.isValid) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.3f, 0.3f, 1f)
        }
        
        if (isRenaming) {
            // Inline rename mode
            ImGui.text(icon)
            ImGui.sameLine()
            ImGui.setKeyboardFocusHere()
            if (ImGui.inputText("##rename", renameBuffer, imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue)) {
                val newName = renameBuffer.get()
                if (newName.isNotBlank()) {
                    FileSystemManager.renameFile(asset.path, newName).onSuccess {
                        refreshAssets()
                    }.onFailure {
                        logger.error(it) { "Failed to rename asset" }
                    }
                }
                renameTarget = null
            }
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.Escape))) {
                renameTarget = null
            }
        } else {
            // Normal display mode
            val label = "$icon ${asset.displayName}"
            if (ImGui.selectable(label, isSelected, 0, 0f, 0f)) {
                selectedAsset = asset
                if (asset.type == AssetType.FOLDER) {
                    currentDirectory = File(asset.path)
                    refreshAssets()
                }
            }
            
            // Double-click to open
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                when (asset.type) {
                    AssetType.FOLDER -> {
                        currentDirectory = File(asset.path)
                        refreshAssets()
                    }
                    AssetType.PATCH -> {
                        // TODO: Load patch into active deck
                        logger.info { "Double-clicked patch: ${asset.name}" }
                    }
                    AssetType.PLAYLIST -> {
                        // Signal to open in playlist editor
                        PlaylistEditorPanel.openPlaylist(asset.path)
                    }
                }
            }

            // Drop target for adding patches to playlists or moving assets to folders
            if (asset.type == AssetType.PLAYLIST && ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (payload != null) {
                    val droppedPath = payload
                    val droppedFile = File(droppedPath)
                    when (droppedFile.extension.lowercase()) {
                        "patch", "lsd", "json" -> {
                            PlaylistManager.loadPlaylist(File(asset.path)).onSuccess { playlist ->
                                PlaylistManager.insertPatch(playlist, droppedPath, playlist.patches.size).onSuccess {
                                    PlaylistManager.savePlaylist(playlist).onSuccess {
                                        logger.info { "Added patch ${droppedFile.name} to playlist ${asset.name} via drag-drop" }
                                        refreshAssets()
                                    }
                                }
                            }
                        }
                        "playlist", "lsdset" -> {
                            PlaylistManager.loadPlaylist(File(asset.path)).onSuccess { targetPl ->
                                PlaylistManager.unpackPlaylistInto(targetPl, droppedPath, targetPl.patches.size).onSuccess {
                                    PlaylistManager.savePlaylist(targetPl).onSuccess {
                                        logger.info { "Unpacked playlist ${droppedFile.name} into ${asset.name} via drag-drop" }
                                        refreshAssets()
                                    }
                                }
                            }
                        }
                    }
                }
                ImGui.endDragDropTarget()
            } else if (asset.type == AssetType.FOLDER && ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (payload != null) {
                    FileSystemManager.moveFile(payload, asset.path).onSuccess {
                        refreshAssets()
                        logger.info { "Moved asset to ${asset.name} via list drag-drop" }
                    }.onFailure {
                        logger.error(it) { "Failed to move asset" }
                    }
                }
                ImGui.endDragDropTarget()
            }
            
            // Drag source
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
                ImGui.text("${asset.name} (${asset.type})")
                ImGui.endDragDropSource()
            }
            
            // Context menu
            if (ImGui.isItemClicked(1)) {
                contextMenuTarget = asset
                ImGui.openPopup("AssetContextMenu")
            }
            
            // F2 to rename selected item (check if not capturing keyboard)
            if (isSelected && !ImGui.getIO().wantCaptureKeyboard) {
                // Note: ImGui doesn't have F2 key enum, so we skip this for now
                // Can be added via raw key codes if needed
            }
            
            // Delete key to delete selected item
            if (isSelected && !ImGui.getIO().wantCaptureKeyboard && ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.Delete))) {
                contextMenuTarget = asset
                ImGui.openPopup("ConfirmDelete")
            }
        }
        
        if (!asset.isValid) {
            ImGui.popStyleColor()
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(asset.errorMessage ?: "Invalid asset")
            }
        }
        
        ImGui.popID()
    }
    
    private fun drawContextMenu() {
        if (ImGui.beginPopup("AssetContextMenu")) {
            val target = contextMenuTarget
            if (target != null) {
                ImGui.text("${target.name}")
                ImGui.separator()
                
                if (ImGui.menuItem("Rename", "F2")) {
                    renameTarget = target
                    renameBuffer.set(target.name)
                }
                
                if (ImGui.menuItem("Clone")) {
                    FileSystemManager.cloneFile(target.path).onSuccess {
                        refreshAssets()
                        logger.info { "Cloned ${target.name}" }
                    }.onFailure {
                        logger.error(it) { "Failed to clone asset" }
                    }
                }
                
                if (ImGui.menuItem("Delete", "Del")) {
                    ImGui.openPopup("ConfirmDelete")
                }
                
                ImGui.separator()
                
                if (target.type != AssetType.FOLDER) {
                    if (ImGui.menuItem("Append to Playqueue")) {
                        // TODO: Implement playqueue operations
                        logger.info { "Append to playqueue: ${target.name}" }
                    }
                    
                    if (ImGui.menuItem("Insert After Current")) {
                        logger.info { "Insert after current: ${target.name}" }
                    }
                    
                    if (ImGui.menuItem("Replace and Play")) {
                        logger.info { "Replace and play: ${target.name}" }
                    }
                }
            }
            
            ImGui.endPopup()
        }
        
        // Delete confirmation popup
        if (ImGui.beginPopupModal("ConfirmDelete", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            val target = contextMenuTarget
            if (target != null) {
                ImGui.text("Delete ${target.name}?")
                ImGui.text("This action cannot be undone.")
                ImGui.separator()
                
                if (ImGui.button("Delete", 120f, 0f)) {
                    FileSystemManager.deleteFile(target.path).onSuccess {
                        refreshAssets()
                        if (selectedAsset == target) {
                            selectedAsset = null
                        }
                        logger.info { "Deleted ${target.name}" }
                    }.onFailure {
                        logger.error(it) { "Failed to delete asset" }
                    }
                    ImGui.closeCurrentPopup()
                }
                ImGui.sameLine()
                if (ImGui.button("Cancel", 120f, 0f)) {
                    ImGui.closeCurrentPopup()
                }
            }
            ImGui.endPopup()
        }
    }
    
    private fun drawNewFolderPopup() {
        if (ImGui.beginPopupModal("NewFolderPopup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Create New Folder")
            ImGui.separator()
            
            ImGui.inputText("Name", folderNameBuffer)
            
            if (ImGui.button("Create", 120f, 0f)) {
                val name = folderNameBuffer.get()
                if (name.isNotBlank()) {
                    FileSystemManager.createDirectory(currentDirectory.absolutePath, name).onSuccess {
                        refreshAssets()
                        logger.info { "Created folder: $name" }
                        folderNameBuffer.set("")
                    }.onFailure {
                        logger.error(it) { "Failed to create folder" }
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
    
    private fun refreshAssets() {
        assets = FileSystemManager.scanDirectory(currentDirectory)
        logger.debug { "Refreshed assets: ${assets.size} items in ${currentDirectory.name}" }
    }
    
    fun getSelectedAsset(): AssetItem? = selectedAsset
}
