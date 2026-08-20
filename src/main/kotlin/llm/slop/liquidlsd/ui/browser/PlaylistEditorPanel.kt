package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiComboFlags
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.PlaylistManager
import llm.slop.liquidlsd.ui.UIManager
import mu.KotlinLogging
import java.io.File

object PlaylistEditorPanel {
    private val logger = KotlinLogging.logger {}

    fun draw(session: SessionContext, mixer: Mixer) {
        val allPlaylists = FileSystemManager.scanAllPlaylists()

        // Sync selected playlist
        val selectedFile = LibraryPanel.selectedPlaylistFile ?: allPlaylists.firstOrNull()?.let { File(it.path) }
        LibraryPanel.selectedPlaylistFile = selectedFile

        val currentPlaylist = selectedFile?.let { LibraryPanel.getOrLoadPlaylist(it) }

        // Top Header: Dropdown selector + [ + ] New + [ ... ] Actions
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
        val comboWidth = (ImGui.getContentRegionAvailX() - (btnSize * 2f + spacing * 2f)).coerceAtLeast(100f)

        // Dropdown Combo
        val comboPreview = currentPlaylist?.name ?: if (allPlaylists.isEmpty()) "No playlists" else "Select playlist..."
        ImGui.setNextItemWidth(comboWidth)
        if (ImGui.beginCombo("##playlistSelectCombo", comboPreview, ImGuiComboFlags.None)) {
            allPlaylists.forEach { item ->
                val isSelected = selectedFile?.absolutePath == item.path
                if (ImGui.selectable(item.name, isSelected)) {
                    LibraryPanel.selectedPlaylistFile = File(item.path)
                    LibraryPanel.activePlaylistData = null
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

        ImGui.sameLine()

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

        // Popup menu for playlist actions
        if (selectedFile != null && currentPlaylist != null) {
            val playlistAsset = AssetItem(
                path = selectedFile.absolutePath,
                name = selectedFile.nameWithoutExtension,
                type = AssetType.PLAYLIST
            )

            if (ImGui.beginPopup("playlist_header_more_menu")) {
                if (ImGui.menuItem("Play now (and replace queue)")) {
                    session.playQueueManager.playPlaylistNow(selectedFile, mixer)
                }
                if (ImGui.menuItem("Insert into the queue after current")) {
                    session.playQueueManager.insertPlaylistAfterCurrent(selectedFile)
                }
                if (ImGui.menuItem("Add to the bottom of the queue")) {
                    session.playQueueManager.appendPlaylistToQueue(selectedFile)
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

            ImGui.pushID(index)

            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f)
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0f)

            // Button A (Deck A color: Blue)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.4f, 0.8f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.2f, 0.4f, 0.8f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.4f, 0.8f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.4f, 0.8f, 0.3f)
            if (ImGui.button("A##deck_a", btnSize, btnSize) && exists) {
                val targetDeck = mixer.deckA
                val isDirty = session.presetManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    session.presetManager.loadDeckPresetAsync(resolvedFile, isDeckA = true, isDeckC = false)
                } else {
                    UIManager.triggerDeckDragDrop(resolvedFile, targetDeck, true, mixer)
                }
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Load preset to Deck A.")
            ImGui.popStyleColor(5)

            ImGui.sameLine()

            // Button B (Deck B color: Orange)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.4f, 0.2f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.8f, 0.4f, 0.2f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.8f, 0.4f, 0.2f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.4f, 0.2f, 0.3f)
            if (ImGui.button("B##deck_b", btnSize, btnSize) && exists) {
                val targetDeck = mixer.deckB
                val isDirty = session.presetManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    session.presetManager.loadDeckPresetAsync(resolvedFile, isDeckA = false, isDeckC = false)
                } else {
                    UIManager.triggerDeckDragDrop(resolvedFile, targetDeck, false, mixer)
                }
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Load preset to Deck B.")
            ImGui.popStyleColor(5)

            ImGui.sameLine()

            // Button C (Deck C color: Green)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.7f, 0.5f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.2f, 0.7f, 0.5f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.7f, 0.5f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.7f, 0.5f, 0.3f)
            if (ImGui.button("C##deck_c", btnSize, btnSize) && exists) {
                val targetDeck = mixer.deckC
                val isDirty = session.presetManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    session.presetManager.loadDeckPresetAsync(resolvedFile, isDeckA = false, isDeckC = true)
                } else {
                    UIManager.triggerDeckDragDrop(resolvedFile, targetDeck, false, mixer)
                }
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Preview preset on Deck C (Preview/C).")
            ImGui.popStyleColor(5)

            ImGui.sameLine()

            // Button Q (Queue color: Violet / Purple)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.4f, 0.9f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.7f, 0.4f, 0.9f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.4f, 0.9f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.4f, 0.9f, 0.3f)
            if (ImGui.button("Q##deck_q", btnSize, btnSize) && exists) {
                session.playQueueManager.appendToQueue(resolvedFile)
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Add preset to the end of the queue.")
            ImGui.popStyleColor(5)

            ImGui.popStyleVar(2)

            ImGui.sameLine()

            if (!exists) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.3f, 0.3f, 1f)
            }

            ImGui.selectable("$label##item", false)

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
                if (ImGui.menuItem("Play now (and replace queue)")) {
                    session.playQueueManager.playNow(resolvedFile, mixer)
                }
                if (ImGui.menuItem("Insert into the queue after current")) {
                    session.playQueueManager.insertAfterCurrent(resolvedFile)
                }
                if (ImGui.menuItem("Add to the bottom of the queue")) {
                    session.playQueueManager.appendToQueue(resolvedFile)
                }
                ImGui.separator()
                if (ImGui.menuItem("Remove from playlist")) {
                    removePresetIndex = index
                }
                ImGui.endPopup()
            }

            ImGui.popID()
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

        if (moveFrom != -1 && moveTo != -1) {
            PlaylistManager.movePreset(playlist, moveFrom, moveTo)
        }

        if (removePresetIndex != -1) {
            PlaylistManager.removePreset(playlist, removePresetIndex)
        }
    }
}
