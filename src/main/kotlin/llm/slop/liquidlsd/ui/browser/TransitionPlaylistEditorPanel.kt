package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiComboFlags
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiKey
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.TransitionPlaylistDto
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import mu.KotlinLogging
import java.io.File

object TransitionPlaylistEditorPanel {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    var selectedItemIndex: Int = -1

    fun getSelectedPresetFile(): File? {
        val selectedFile = LibraryPanel.selectedTransitionPlaylistFile ?: return null
        val items = loadPlaylistItems(selectedFile)
        if (selectedItemIndex in items.indices) {
            val itemStr = items[selectedItemIndex]
            val candidate = File(itemStr)
            if (candidate.exists()) return candidate
            val candidateInTransitions = File(FileSystemManager.getTransitionsRoot(), itemStr)
            if (candidateInTransitions.exists()) return candidateInTransitions
            return File(itemStr)
        }
        return null
    }

    private fun loadPlaylistDto(file: File): TransitionPlaylistDto? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString<TransitionPlaylistDto>(file.readText())
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse transition playlist DTO: ${file.name}" }
            null
        }
    }

    private fun loadPlaylistItems(file: File): List<String> {
        return loadPlaylistDto(file)?.items ?: emptyList()
    }

    private fun savePlaylistDto(file: File, dto: TransitionPlaylistDto) {
        try {
            file.writeText(json.encodeToString(TransitionPlaylistDto.serializer(), dto))
            LibraryPanel.refreshAssets()
        } catch (e: Exception) {
            logger.error(e) { "Failed to save transition playlist DTO: ${file.name}" }
        }
    }

    fun draw(session: SessionContext, mixer: Mixer) {
        val allPlaylists = FileSystemManager.scanAllTransitionPlaylists()

        val selectedFile = LibraryPanel.selectedTransitionPlaylistFile ?: allPlaylists.firstOrNull()?.let { File(it.path) }
        LibraryPanel.selectedTransitionPlaylistFile = selectedFile

        val currentPlaylistDto = selectedFile?.let { loadPlaylistDto(it) }

        drawHeader(session, mixer, allPlaylists, currentPlaylistDto, selectedFile)

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##trans_playlist_items_scroll", 0f, 0f, false)) {
            if (selectedFile == null || currentPlaylistDto == null) {
                drawEmptyPlaylistsState()
            } else {
                drawPlaylistContent(session, mixer, selectedFile, currentPlaylistDto)
            }
        }
        ImGui.endChild()
    }

    private fun drawHeader(
        session: SessionContext,
        mixer: Mixer,
        allPlaylists: List<AssetItem>,
        currentPlaylist: TransitionPlaylistDto?,
        selectedFile: File?
    ) {
        val btnSize = ImGui.getFrameHeight()
        val spacing = ImGui.getStyle().getItemSpacingX()

        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Playlists")
        }
        ImGui.sameLine()
        val totalButtonsWidth = btnSize * 2f + spacing
        val rightX = ImGui.getWindowContentRegionMaxX() - totalButtonsWidth
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

        // [ + ] Create New Transition Playlist button
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.PLUS}##createNewTransPlaylistBtn", btnSize, btnSize)) {
                BrowserPopupHandler.pendingOpenNewPlaylistPopup = true
            }
        }
        itemTooltip("Create new transition playlist...")

        ImGui.sameLine()

        // [ ... ] More Actions button
        val moreDisabled = selectedFile == null || currentPlaylist == null
        if (moreDisabled) {
            ImGui.beginDisabled()
        }
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.MORE_VERTICAL}##transPlaylistMoreBtn", btnSize, btnSize)) {
                ImGui.openPopup("trans_playlist_header_more_menu")
            }
        }
        itemTooltip("Transition playlist actions.")
        if (moreDisabled) {
            ImGui.endDisabled()
        }

        if (selectedFile != null && currentPlaylist != null) {
            val playlistAsset = AssetItem(
                path = selectedFile.absolutePath,
                name = selectedFile.nameWithoutExtension,
                type = AssetType.TRANSITION_PLAYLIST
            )

            if (ImGui.beginPopup("trans_playlist_header_more_menu")) {
                if (ImGui.menuItem("Load Playlist to Live Queue")) {
                    TransitionQueueManager.appendToQueue(selectedFile)
                }
                ImGui.separator()
                if (ImGui.menuItem("Rename...")) {
                    BrowserPopupHandler.renameTarget = playlistAsset
                    BrowserPopupHandler.renameBuffer.set(playlistAsset.name)
                    BrowserPopupHandler.pendingOpenRenamePopup = true
                }
                if (ImGui.menuItem("Clone")) {
                    FileSystemManager.cloneFile(selectedFile.absolutePath).onSuccess { newPath ->
                        LibraryPanel.selectedTransitionPlaylistFile = File(newPath)
                        selectedItemIndex = -1
                    }
                }
                if (ImGui.menuItem("Delete")) {
                    BrowserPopupHandler.deleteTarget = playlistAsset
                    BrowserPopupHandler.pendingOpenDeletePopup = true
                }
                if (ImGui.menuItem("Clear All Items")) {
                    savePlaylistDto(selectedFile, currentPlaylist.copy(items = emptyList()))
                    selectedItemIndex = -1
                }
                ImGui.endPopup()
            }
        }

        ImGui.separator()
        ImGui.spacing()

        // Dropdown Combo
        val comboWidth = ImGui.getContentRegionAvailX()
        val comboPreview = currentPlaylist?.name ?: if (allPlaylists.isEmpty()) "No transition playlists" else "Select playlist..."
        ImGui.setNextItemWidth(comboWidth)
        if (ImGui.beginCombo("##transPlaylistSelectCombo", comboPreview, ImGuiComboFlags.None)) {
            allPlaylists.forEach { item ->
                val isSelected = selectedFile?.absolutePath == item.path
                if (ImGui.selectable(item.name, isSelected)) {
                    LibraryPanel.selectedTransitionPlaylistFile = File(item.path)
                    selectedItemIndex = -1
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        itemTooltip("Select active transition playlist.")
    }

    private fun drawEmptyPlaylistsState() {
        ImGui.spacing()
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 40f)
        val text = "No transition playlists found"
        val textWidth = ImGui.calcTextSize(text).x
        ImGui.setCursorPosX((ImGui.getWindowWidth() - textWidth) * 0.5f)
        ImGui.textDisabled(text)

        ImGui.spacing()
        val btnText = "Create New Playlist"
        val btnWidth = ImGui.calcTextSize(btnText).x + 30f
        ImGui.setCursorPosX((ImGui.getWindowWidth() - btnWidth) * 0.5f)
        if (ImGui.button(btnText, btnWidth, 0f)) {
            BrowserPopupHandler.pendingOpenNewPlaylistPopup = true
        }
    }

    private fun drawPlaylistContent(
        session: SessionContext,
        mixer: Mixer,
        file: File,
        playlist: TransitionPlaylistDto
    ) {
        var moveFrom = -1
        var moveTo = -1
        var removeItemIndex = -1

        var insertSlot = -1
        var insertLineY = -1f
        val insertLineColor = (255 shl 24) or (204 shl 16) or (255 shl 8) or 102

        val items = playlist.items

        if (items.isEmpty()) {
            ImGui.spacing()
            ImGui.textDisabled("Playlist is empty.")
            ImGui.textDisabled("Drag stock transitions or presets here.")
        }

        items.forEachIndexed { index, itemStr ->
            val resolvedFile = File(itemStr)
            val exists = resolvedFile.exists() || !itemStr.endsWith(".lsdtrans", ignoreCase = true)
            val displayName = if (resolvedFile.exists()) resolvedFile.nameWithoutExtension else itemStr.replace("_", " ")
            val label = "${index + 1}. $displayName"
            val isSelected = index == selectedItemIndex

            ImGui.pushID(index)

            val popupId = "trans_playlist_item_menu_$index"

            if (isSelected && LibraryPanel.shouldReclaimFocus) {
                ImGui.setKeyboardFocusHere()
            }
            if (isSelected && LibraryPanel.shouldScrollToSelection) {
                ImGui.setScrollHereY(0.5f)
            }

            val btnW = 28f
            val availW = ImGui.getContentRegionAvailX()
            val itemW = (availW - btnW).coerceAtLeast(10f)

            var itemClicked = false
            session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
                if (ImGui.selectable(label, isSelected, 0, itemW, 0f)) {
                    itemClicked = true
                }
            }
            if (itemClicked) {
                selectedItemIndex = index
                LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.TRANSITION_PLAYLIST
            }
            val isRowHovered = ImGui.isItemHovered()
            if (ImGui.isItemClicked(1)) {
                ImGui.openPopup(popupId)
            }

            val io = ImGui.getIO()
            val isWindowFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
            val canAutoSelect = isWindowFocused && LibraryPanel.activeSelectionSource == LibraryPanel.SelectionSource.TRANSITION_PLAYLIST
            if (canAutoSelect && ImGui.isItemFocused() && !isSelected && !io.wantTextInput) {
                selectedItemIndex = index
                LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.TRANSITION_PLAYLIST
            }

            // Double click: apply transition to mixer
            if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                TransitionQueueManager.applyTransitionItem(resolvedFile, mixer)
            }

            // Drag source for reordering
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("TRANS_PLAYLIST_ITEM", index as Any)
                ImGui.text("Moving $displayName")
                ImGui.endDragDropSource()
            }

            val itemMinY = ImGui.getItemRectMinY()
            val itemMaxY = ImGui.getItemRectMaxY()

            ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
            if (ImGui.beginDragDropTarget()) {
                val mouseY = ImGui.getMousePosY()
                val insertBefore = mouseY < (itemMinY + itemMaxY) * 0.5f
                val effectiveSlot = if (insertBefore) index else index + 1
                insertSlot = effectiveSlot
                insertLineY = if (insertBefore) itemMinY else itemMaxY

                val reorderPayload = ImGui.acceptDragDropPayload<Int>("TRANS_PLAYLIST_ITEM")
                if (reorderPayload != null) {
                    moveFrom = reorderPayload
                    val rawTo = if (reorderPayload < effectiveSlot) effectiveSlot - 1 else effectiveSlot
                    moveTo = rawTo.coerceIn(0, items.size - 1)
                }

                val assetPayload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (assetPayload != null) {
                    val newItems = items.toMutableList()
                    val insertAt = effectiveSlot.coerceIn(0, newItems.size)
                    newItems.add(insertAt, assetPayload)
                    savePlaylistDto(file, playlist.copy(items = newItems))
                }

                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()

            ImGui.sameLine(0f, 0f)
            BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "trans_pl_item_$index", btnW)

            // Context menu
            if (ImGui.beginPopup(popupId)) {
                if (ImGui.menuItem("Apply to Mixer")) {
                    TransitionQueueManager.applyTransitionItem(resolvedFile, mixer)
                }
                if (ImGui.menuItem("Add to Live Queue")) {
                    TransitionQueueManager.appendToQueue(resolvedFile)
                }
                ImGui.separator()
                if (ImGui.menuItem("Remove from playlist")) {
                    removeItemIndex = index
                }
                ImGui.endPopup()
            }

            ImGui.popID()
        }

        // Keyboard shortcuts
        val io = ImGui.getIO()
        if (selectedItemIndex in items.indices && !io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper) {
            if (ImGui.isKeyPressed(ImGuiKey.Delete, false) ||
                ImGui.isKeyPressed(ImGuiKey.Backspace, false)) {
                removeItemIndex = selectedItemIndex
            }
        }

        // Draw insertion line
        if (insertLineY > 0f) {
            val dl = ImGui.getWindowDrawList()
            val x0 = ImGui.getWindowPosX() + 4f
            val x1 = ImGui.getWindowPosX() + ImGui.getWindowWidth() - 4f
            dl.addCircleFilled(x0 + 2f, insertLineY, 3f, insertLineColor)
            dl.addLine(x0 + 5f, insertLineY, x1, insertLineY, insertLineColor, 2f)
        }

        // Bottom drop target area
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(30f)
        ImGui.dummy(ImGui.getContentRegionAvailX(), availH)
        ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val newItems = items.toMutableList()
                newItems.add(payload)
                savePlaylistDto(file, playlist.copy(items = newItems))
            }
            ImGui.endDragDropTarget()
        }
        ImGui.popStyleColor()

        if (moveFrom != -1 && moveTo != -1) {
            val list = items.toMutableList()
            val moved = list.removeAt(moveFrom)
            list.add(moveTo, moved)
            savePlaylistDto(file, playlist.copy(items = list))
            if (selectedItemIndex == moveFrom) selectedItemIndex = moveTo
        }

        if (removeItemIndex != -1) {
            val list = items.toMutableList()
            if (removeItemIndex in list.indices) {
                list.removeAt(removeItemIndex)
                savePlaylistDto(file, playlist.copy(items = list))
                if (selectedItemIndex >= list.size) {
                    selectedItemIndex = list.size - 1
                }
            }
        }
    }
}
