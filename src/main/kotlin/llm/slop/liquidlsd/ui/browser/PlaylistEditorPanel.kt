package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiComboFlags
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
import mu.KotlinLogging
import java.io.File

object PlaylistEditorPanel {
    private val logger = KotlinLogging.logger {}
    var selectedPresetIndex: Int = -1

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

        if (selectedFile == null || currentPlaylist == null) {
            drawEmptyPlaylistsState()
            return
        }

        drawPlaylistContent(session, mixer, currentPlaylist)
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
        if (ImGui.button("${Icons.PLUS}##createNewPlaylistBtn", btnSize, btnSize)) {
            ImGui.openPopup("NewPlaylistPopup")
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Create new playlist.")
        }

        ImGui.sameLine()

        // [ ... ] More Playlist Actions button
        val moreDisabled = selectedFile == null || currentPlaylist == null
        if (moreDisabled) {
            ImGui.beginDisabled()
        }
        if (ImGui.button("${Icons.MORE_HORIZONTAL}##playlistMoreBtn", btnSize, btnSize)) {
            ImGui.openPopup("playlist_header_more_menu")
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Playlist actions.")
        }
        if (moreDisabled) {
            ImGui.endDisabled()
        }

        ImGui.separator()
        ImGui.spacing()

        // Dropdown Combo (Full width)
        val comboWidth = ImGui.getContentRegionAvailX()
        val comboPreview = currentPlaylist?.name ?: if (allPlaylists.isEmpty()) "No playlists" else "Select playlist..."
        ImGui.setNextItemWidth(comboWidth)
        if (ImGui.beginCombo("##playlistSelectCombo", comboPreview, ImGuiComboFlags.None)) {
            allPlaylists.forEach { item ->
                val isSelected = selectedFile?.absolutePath == item.path
                if (ImGui.selectable(item.name, isSelected)) {
                    LibraryPanel.selectedPlaylistFile = File(item.path)
                    LibraryPanel.activePlaylistData = null
                    selectedPresetIndex = -1
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Select active playlist.")
        }

        // Popup menu for playlist actions
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
            ImGui.openPopup("NewPlaylistPopup")
        }
    }

    private fun drawPlaylistContent(session: SessionContext, mixer: Mixer, playlist: PlaylistManager.Playlist) {
        var moveFrom = -1
        var moveTo = -1
        var removePresetIndex = -1

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
            val isSelected = index == selectedPresetIndex

            ImGui.pushID(index)

            if (!exists) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.3f, 0.3f, 1f)
            }

            if (isSelected && LibraryPanel.shouldReclaimFocus) {
                ImGui.setKeyboardFocusHere()
            }
            if (isSelected && LibraryPanel.shouldScrollToSelection) {
                ImGui.setScrollHereY(0.5f)
            }

            if (ImGui.selectable(label, isSelected)) {
                LibraryPanel.selectPlaylistPreset(index, session, mixer)
            }

            val io = ImGui.getIO()
            if (ImGui.isItemFocused() && !isSelected && !io.wantTextInput) {
                LibraryPanel.selectPlaylistPreset(index, session, mixer)
            }

            // Double click loads to standby deck
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0) && exists) {
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

                // Accept preset dropped from presets library
                val assetPayload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (assetPayload != null) {
                    val assetFile = File(assetPayload)
                    if (assetFile.extension == "lsdset") {
                        PlaylistManager.unpackPlaylistInto(playlist, assetPayload, effectiveSlot)
                    } else {
                        PlaylistManager.insertPreset(playlist, assetPayload, effectiveSlot)
                    }
                }

                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()

            // Right-click menu
            if (ImGui.beginPopupContextItem("playlist_item_menu")) {
                if (exists) {
                    if (ImGui.menuItem("Load to Deck A")) {
                        session.presetManager.loadDeckPresetAsync(resolvedFile, isDeckA = true)
                    }
                    if (ImGui.menuItem("Load to Deck B")) {
                        session.presetManager.loadDeckPresetAsync(resolvedFile, isDeckA = false, isDeckBG = false, isDeckPV = false)
                    }
                    if (ImGui.menuItem("Load to Deck BG")) {
                        session.presetManager.loadDeckPresetAsync(resolvedFile, isDeckBG = true)
                    }
                    if (ImGui.menuItem("Preview on Deck PV")) {
                        session.presetManager.loadDeckPresetAsync(resolvedFile, isDeckPV = true)
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Add to A/B Queue")) {
                        session.playQueueManager.appendToQueue(resolvedFile)
                    }
                    if (ImGui.menuItem("Add to Background Queue")) {
                        BgQueueManager.appendToQueue(resolvedFile)
                    }
                    ImGui.separator()
                }
                if (ImGui.menuItem("Remove from playlist")) {
                    removePresetIndex = index
                }
                if (ImGui.menuItem("Delete preset from library...")) {
                    BrowserPopupHandler.deleteTarget = AssetItem(
                        path = resolvedFile.absolutePath,
                        name = displayName,
                        type = AssetType.PRESET
                    )
                    BrowserPopupHandler.pendingOpenDeletePopup = true
                }
                ImGui.endPopup()
            }

            ImGui.popID()
        }

        // Keyboard shortcuts (Delete / Backspace removes selected preset from active playlist)
        val io = ImGui.getIO()
        if (selectedPresetIndex in playlist.presets.indices && !io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper) {
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Delete), false) ||
                ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Backspace), false)) {
                removePresetIndex = selectedPresetIndex
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
                val assetFile = File(payload)
                if (assetFile.extension == "lsdset") {
                    PlaylistManager.unpackPlaylistInto(playlist, payload, playlist.presets.size)
                } else {
                    PlaylistManager.insertPreset(playlist, payload, playlist.presets.size)
                }
            }
            ImGui.endDragDropTarget()
        }
        ImGui.popStyleColor()

        if (moveFrom != -1 && moveTo != -1) {
            PlaylistManager.movePreset(playlist, moveFrom, moveTo)
            if (selectedPresetIndex == moveFrom) selectedPresetIndex = moveTo
        }

        if (removePresetIndex != -1) {
            PlaylistManager.removePreset(playlist, removePresetIndex)
            val newSize = playlist.presets.size
            if (selectedPresetIndex >= newSize) {
                selectedPresetIndex = newSize - 1
            }
        }
    }
}
