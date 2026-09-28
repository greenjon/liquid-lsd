package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiComboFlags
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.PlaylistManager
import llm.slop.liquidlsd.ui.UIManager
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import llm.slop.liquidlsd.ui.pushOpenDropdownFont
import llm.slop.liquidlsd.ui.popOpenDropdownFont
import llm.slop.liquidlsd.ui.pushOpenDropdownPadding
import llm.slop.liquidlsd.ui.popOpenDropdownPadding
import llm.slop.liquidlsd.ui.selectableRow
import mu.KotlinLogging
import java.io.File

object PlaylistEditorPanel {
    private val logger = KotlinLogging.logger {}
    val selection = MultiSelectionModel<Int>()
    var selectedPresetIndex: Int
        get() = selection.leadItem ?: -1
        set(value) {
            if (value >= 0) selection.setSingle(value) else selection.clear()
        }

    fun clearSelection() {
        selection.clear()
    }

    fun getSelectedPresetFile(): File? {
        val playlist = LibraryPanel.activePlaylistData ?: return null
        if (selectedPresetIndex in playlist.presets.indices) {
            return PlaylistManager.resolvePreset(playlist.presets[selectedPresetIndex])
        }
        return null
    }

    fun draw(session: SessionContext, mixer: Mixer) {
        val allPlaylists = FileSystemManager.scanAllPlaylists()

        // Sync selected playlist
        val selectedFile = LibraryPanel.selectedPlaylistFile ?: allPlaylists.firstOrNull()?.let { File(it.path) }
        LibraryPanel.selectedPlaylistFile = selectedFile

        val currentPlaylist = selectedFile?.let { LibraryPanel.getOrLoadPlaylist(it) }

        // Top Header: Title bar + Dropdown selector + Action buttons
        drawHeader(session, mixer, allPlaylists, currentPlaylist, selectedFile)

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##playlist_items_scroll", 0f, 0f, false)) {
            if (selectedFile == null || currentPlaylist == null) {
                drawEmptyPlaylistsState()
            } else {
                drawPlaylistContent(session, mixer, currentPlaylist)
            }
        }
        ImGui.endChild()
    }

    private fun drawHeader(
        session: SessionContext,
        mixer: Mixer,
        allPlaylists: List<AssetItem>,
        currentPlaylist: PlaylistManager.Playlist?,
        selectedFile: File?
    ) {
        val btnSize = ImGui.getFrameHeight()
        val spacing = ImGui.getStyle().getItemSpacingX()

        // Title Bar: "Playlists" on the left, [+] and [...] on the right
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

        // [ + ] Create New Playlist button
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.PLUS}##createNewPlaylistBtn", btnSize, btnSize)) {
                BrowserPopupHandler.pendingOpenNewPlaylistPopup = true
            }
        }
        itemTooltip("Create new playlist.")

        ImGui.sameLine()

        // [ ... ] More Playlist Actions button (vertical kebab)
        val moreDisabled = selectedFile == null || currentPlaylist == null
        if (moreDisabled) {
            ImGui.beginDisabled()
        }
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.MORE_VERTICAL}##playlistMoreBtn", btnSize, btnSize)) {
                ImGui.openPopup("playlist_header_more_menu")
            }
        }
        itemTooltip("Playlist actions.")
        if (moreDisabled) {
            ImGui.endDisabled()
        }

        // Popup menu for playlist actions (placed immediately after openPopup trigger)
        if (selectedFile != null && currentPlaylist != null) {
            val playlistAsset = AssetItem(
                path = selectedFile.absolutePath,
                name = selectedFile.nameWithoutExtension,
                type = AssetType.PLAYLIST
            )

            if (ImGui.beginPopup("playlist_header_more_menu")) {
                if (ImGui.menuItem("Play now in A/B Queue (and replace queue)")) {
                    session.playQueueManager.playPlaylistNow(selectedFile, mixer)
                }
                if (ImGui.menuItem("Insert into A/B Queue after current")) {
                    session.playQueueManager.insertPlaylistAfterCurrent(selectedFile)
                }
                if (ImGui.menuItem("Add to the bottom of A/B Queue")) {
                    session.playQueueManager.appendPlaylistToQueue(selectedFile)
                }
                ImGui.separator()
                if (ImGui.menuItem("Play now in BG Queue (and replace queue)")) {
                    BgQueueManager.playPlaylistNow(selectedFile, mixer)
                }
                if (ImGui.menuItem("Insert into BG Queue after current")) {
                    BgQueueManager.insertPlaylistAfterCurrent(selectedFile)
                }
                if (ImGui.menuItem("Add to the bottom of BG Queue")) {
                    BgQueueManager.appendPlaylistToQueue(selectedFile)
                }
                ImGui.separator()
                if (ImGui.menuItem("Rename...")) {
                    BrowserPopupHandler.renameTarget = playlistAsset
                    BrowserPopupHandler.renameBuffer.set(playlistAsset.name)
                    BrowserPopupHandler.pendingOpenRenamePopup = true
                }
                if (ImGui.menuItem("Clone")) {
                    FileSystemManager.cloneFile(selectedFile.absolutePath).onSuccess { newPath ->
                        LibraryPanel.selectedPlaylistFile = File(newPath)
                        LibraryPanel.activePlaylistData = null
                        selectedPresetIndex = -1
                    }
                }
                if (ImGui.menuItem("Delete")) {
                    BrowserPopupHandler.deleteTarget = playlistAsset
                    BrowserPopupHandler.pendingOpenDeletePopup = true
                }
                ImGui.endPopup()
            }
        }

        ImGui.separator()
        ImGui.spacing()

        // Dropdown Combo (Full width)
        val comboWidth = ImGui.getContentRegionAvailX()
        val comboPreview = currentPlaylist?.name ?: if (allPlaylists.isEmpty()) "No playlists" else "Select playlist..."
        ImGui.setNextItemWidth(comboWidth)
        pushOpenDropdownPadding()
        if (ImGui.beginCombo("##playlistSelectCombo", comboPreview, ImGuiComboFlags.HeightLargest)) {
            pushOpenDropdownFont()
            allPlaylists.forEach { item ->
                val isSelected = selectedFile?.absolutePath == item.path
                if (selectableRow(item.name, isSelected)) {
                    LibraryPanel.selectedPlaylistFile = File(item.path)
                    LibraryPanel.activePlaylistData = null
                    selectedPresetIndex = -1
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            popOpenDropdownFont()
            ImGui.endCombo()
        }
        popOpenDropdownPadding()
        itemTooltip("Select active playlist.")
    }

    private fun drawEmptyPlaylistsState() {
        ImGui.spacing()
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 40f)
        val text = "No playlists found"
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

    private fun drawPlaylistContent(session: SessionContext, mixer: Mixer, playlist: PlaylistManager.Playlist) {
        var moveFrom = -1
        var moveTo = -1
        var removeIndices: List<Int> = emptyList()

        var insertSlot = -1
        var insertLineY = -1f
        val insertLineColor = (255 shl 24) or (204 shl 16) or (255 shl 8) or 102 // mint-green, ABGR

        if (playlist.presets.isEmpty()) {
            ImGui.spacing()
            ImGui.textDisabled("Playlist is empty.")
            ImGui.textDisabled("Drag presets from the left panel to add them.")
        }

        val btnSize = ImGui.getFrameHeight()

        playlist.presets.forEachIndexed { index, presetPath ->
            val resolvedFile = PlaylistManager.resolvePreset(presetPath)
            val exists = resolvedFile.exists()
            val displayName = resolvedFile.nameWithoutExtension.ifBlank { presetPath }
            val label = "${index + 1}. ${if (exists) "" else "[!] "}$displayName${if (!exists) " (missing)" else ""}"
            val isSelected = selection.isSelected(index)

            ImGui.pushID(index)

            if (!exists) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.3f, 0.3f, 1f)
            }

            val popupId = "playlist_item_menu_$index"

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
                if (selectableRow(label, isSelected, itemW)) {
                    itemClicked = true
                }
            }
            val io = ImGui.getIO()
            if (itemClicked) {
                val isCtrl = io.keyCtrl || io.keySuper
                val isShift = io.keyShift
                selection.handleClick(index, (0 until playlist.presets.size).toList(), isCtrl, isShift)
                LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PLAYLIST
                PresetListPanel.selection.clear()
                QueueActionsPanel.clearSelection()
                llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
                if (exists) {
                    LibraryPanel.auditionIfLocked(resolvedFile, session, mixer)
                }
            }
            val isRowHovered = ImGui.isItemHovered()
            if (ImGui.isItemClicked(1)) {
                if (!selection.isSelected(index)) {
                    selection.setSingle(index)
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PLAYLIST
                    PresetListPanel.selection.clear()
                    QueueActionsPanel.clearSelection()
                    llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
                }
                ImGui.openPopup(popupId)
            }

            val isWindowFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
            val canAutoSelect = isWindowFocused && LibraryPanel.activeSelectionSource == LibraryPanel.SelectionSource.PLAYLIST
            if (canAutoSelect && ImGui.isItemFocused() && !isSelected && !io.wantTextInput && !io.keyCtrl && !io.keyShift && !io.keySuper) {
                LibraryPanel.selectPlaylistPreset(index, session, mixer)
            }

            // Double click loads to standby deck
            if (isRowHovered && ImGui.isMouseDoubleClicked(0) && exists) {
                val targetIsA = mixer.crossfade.value > 0.0f
                val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB
                UIManager.loadDeckPresetSafely(mixer, targetDeck, resolvedFile)
            }

            // Drag source for reordering within playlist
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("PLAYLIST_PATCH_ITEM", index as Any)
                ImGui.text("Moving $displayName")
                ImGui.endDragDropSource()
            }

            if (!exists) {
                ImGui.popStyleColor()
            }

            // Track insertion slot from mouse position
            val itemMinY = ImGui.getItemRectMinY()
            val itemMaxY = ImGui.getItemRectMaxY()

            ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
            if (ImGui.beginDragDropTarget()) {
                val mouseY = ImGui.getMousePosY()
                val insertBefore = mouseY < (itemMinY + itemMaxY) * 0.5f
                val effectiveSlot = if (insertBefore) index else index + 1
                insertSlot = effectiveSlot
                insertLineY = if (insertBefore) itemMinY else itemMaxY

                // Accept reorder from within playlist
                val reorderPayload = ImGui.acceptDragDropPayload<Int>("PLAYLIST_PATCH_ITEM")
                if (reorderPayload != null) {
                    moveFrom = reorderPayload
                    val rawTo = if (reorderPayload < effectiveSlot) effectiveSlot - 1 else effectiveSlot
                    moveTo = rawTo.coerceIn(0, playlist.presets.size - 1)
                }

                // Accept preset(s) dropped from presets library
                val assetPayload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (assetPayload != null) {
                    val paths = assetPayload.lines().map { it.trim() }.filter { it.isNotBlank() }
                    var currentSlot = effectiveSlot
                    for (path in paths) {
                        val assetFile = File(path)
                        if (assetFile.extension == "lsdplay") {
                            PlaylistManager.unpackPlaylistInto(playlist, path, currentSlot)
                        } else {
                            PlaylistManager.insertPreset(playlist, path, currentSlot)
                            currentSlot++
                        }
                    }
                }

                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()

            ImGui.sameLine(0f, 0f)
            BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "pl_item_$index", btnW)

            // Context menu (triggered by right-click or more button)
            pushOpenDropdownPadding()
            if (ImGui.beginPopup(popupId)) {
                pushOpenDropdownFont()
                val inOrder = selection.selectedItems.filter { it in playlist.presets.indices }.sorted()
                val targetIndices = if (inOrder.contains(index)) inOrder else listOf(index)
                val count = targetIndices.size

                if (count > 1) {
                    ImGui.textDisabled("$count Presets Selected")
                    ImGui.separator()
                }

                if (count == 1 && exists) {
                    if (ImGui.menuItem("Load to Deck A")) {
                        session.presetRepository.loadDeckPresetAsync(resolvedFile, isDeckA = true)
                    }
                    if (ImGui.menuItem("Load to Deck B")) {
                        session.presetRepository.loadDeckPresetAsync(resolvedFile, isDeckA = false, isDeckBG = false, isDeckPV = false)
                    }
                    if (ImGui.menuItem("Load to Deck BG")) {
                        session.presetRepository.loadDeckPresetAsync(resolvedFile, isDeckBG = true)
                    }
                    if (ImGui.menuItem("Preview on Deck PV")) {
                        session.presetRepository.loadDeckPresetAsync(resolvedFile, isDeckPV = true)
                    }
                    ImGui.separator()
                }
                val qLabel = if (count > 1) "Add $count Presets to A/B Queue" else "Add to A/B Queue"
                if (ImGui.menuItem(qLabel)) {
                    targetIndices.forEach { idx ->
                        val f = PlaylistManager.resolvePreset(playlist.presets[idx])
                        if (f.exists()) session.playQueueManager.appendToQueue(f)
                    }
                }
                val bgqLabel = if (count > 1) "Add $count Presets to Background Queue" else "Add to Background Queue"
                if (ImGui.menuItem(bgqLabel)) {
                    targetIndices.forEach { idx ->
                        val f = PlaylistManager.resolvePreset(playlist.presets[idx])
                        if (f.exists()) BgQueueManager.appendToQueue(f)
                    }
                }
                ImGui.separator()
                val remLabel = if (count > 1) "Remove $count presets from playlist" else "Remove from playlist"
                if (ImGui.menuItem(remLabel)) {
                    removeIndices = targetIndices
                }
                val delLabel = if (count > 1) "Delete $count presets from library..." else "Delete preset from library..."
                if (ImGui.menuItem(delLabel)) {
                    val targets = targetIndices.mapNotNull { idx ->
                        val f = PlaylistManager.resolvePreset(playlist.presets[idx])
                        if (f.exists()) AssetItem(path = f.absolutePath, name = f.nameWithoutExtension.ifBlank { playlist.presets[idx] }, type = AssetType.PRESET) else null
                    }
                    BrowserPopupHandler.openDeleteConfirmation(targets)
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()

            ImGui.popID()
        }

        // Keyboard shortcuts (Delete / Backspace removes selected presets from active playlist)
        val io = ImGui.getIO()
        val inOrder = selection.selectedItems.filter { it in playlist.presets.indices }.sorted()
        if (inOrder.isNotEmpty() && !io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper) {
            if (ImGui.isKeyPressed(ImGuiKey.Delete, false) ||
                ImGui.isKeyPressed(ImGuiKey.Backspace, false)) {
                removeIndices = inOrder
            }
        }

        // Draw insertion-line indicator
        if (insertLineY > 0f) {
            val dl = ImGui.getWindowDrawList()
            val x0 = ImGui.getWindowPosX() + 4f
            val x1 = ImGui.getWindowPosX() + ImGui.getWindowWidth() - 4f
            dl.addCircleFilled(x0 + 2f, insertLineY, 3f, insertLineColor)
            dl.addLine(x0 + 5f, insertLineY, x1, insertLineY, insertLineColor, 2f)
        }

        // Bottom drop target area to append to the end
        val availH = ImGui.getContentRegionAvailY().coerceAtLeast(30f)
        ImGui.dummy(ImGui.getContentRegionAvailX(), availH)
        ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val paths = payload.lines().map { it.trim() }.filter { it.isNotBlank() }
                for (path in paths) {
                    val assetFile = File(path)
                    if (assetFile.extension == "lsdplay") {
                        PlaylistManager.unpackPlaylistInto(playlist, path, playlist.presets.size)
                    } else {
                        PlaylistManager.insertPreset(playlist, path, playlist.presets.size)
                    }
                }
            }
            ImGui.endDragDropTarget()
        }
        ImGui.popStyleColor()

        if (moveFrom != -1 && moveTo != -1) {
            PlaylistManager.movePreset(playlist, moveFrom, moveTo)
            if (selectedPresetIndex == moveFrom) selectedPresetIndex = moveTo
        }

        if (removeIndices.isNotEmpty()) {
            removeIndices.sortedDescending().forEach { idx ->
                PlaylistManager.removePreset(playlist, idx)
            }
            selection.clear()
        }
    }
}
