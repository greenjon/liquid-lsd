package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.patches.PatchManager
import llm.slop.liquidlsd.patches.PlayQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.PlaylistManager
import llm.slop.liquidlsd.ui.UIManager
import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging

object PlaylistEditorPanel {
    private val logger = KotlinLogging.logger {}

    fun draw(playlist: PlaylistManager.Playlist, mixer: Mixer) {
        // List of patches in playlist
        var moveFrom = -1
        var moveTo = -1
        var removePatchIndex = -1
        // Insertion-line state
        var insertSlot = -1
        var insertLineY = -1f
        val insertLineColor = (255 shl 24) or (204 shl 16) or (255 shl 8) or 102 // mint-green, ABGR

        playlist.patches.forEachIndexed { index, patchPath ->
            val resolvedFile = PlaylistManager.resolvePatch(patchPath)
            val exists = resolvedFile.exists()
            val displayName = resolvedFile.nameWithoutExtension.ifBlank { patchPath }
            val label = "${index + 1}. ${if (exists) "" else "[!] "}$displayName${if (!exists) " (missing)" else ""}"

            ImGui.pushID(index)

            // A / B / C deck buttons
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f)
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0f)

            // Button A (Deck A color: Blue)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.4f, 0.8f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.2f, 0.4f, 0.8f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.4f, 0.8f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.4f, 0.8f, 0.3f)
            if (ImGui.button("A##deck_a", 24f, 24f) && exists) {
                val targetDeck = mixer.deckA
                val isDirty = PatchManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    PatchManager.loadDeckPresetAsync(resolvedFile, isDeckA = true, isDeckC = false)
                } else {
                    UIManager.triggerDeckDragDrop(resolvedFile, targetDeck, true, mixer)
                }
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) ImGui.setTooltip("Load patch to Deck A.")
            ImGui.popStyleColor(5)

            ImGui.sameLine()

            // Button B (Deck B color: Orange)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.4f, 0.2f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.8f, 0.4f, 0.2f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.8f, 0.4f, 0.2f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.4f, 0.2f, 0.3f)
            if (ImGui.button("B##deck_b", 24f, 24f) && exists) {
                val targetDeck = mixer.deckB
                val isDirty = PatchManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    PatchManager.loadDeckPresetAsync(resolvedFile, isDeckA = false, isDeckC = false)
                } else {
                    UIManager.triggerDeckDragDrop(resolvedFile, targetDeck, false, mixer)
                }
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) ImGui.setTooltip("Load patch to Deck B.")
            ImGui.popStyleColor(5)

            ImGui.sameLine()

            // Button C (Deck C color: Green)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.7f, 0.5f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Border, 0.2f, 0.7f, 0.5f, if (exists) 1.0f else 0.3f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.7f, 0.5f, 0.15f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.7f, 0.5f, 0.3f)
            if (ImGui.button("C##deck_c", 24f, 24f) && exists) {
                val targetDeck = mixer.deckC
                val isDirty = PatchManager.isDeckDirty(targetDeck, mixer)
                if (!isDirty) {
                    PatchManager.loadDeckPresetAsync(resolvedFile, isDeckA = false, isDeckC = true)
                } else {
                    UIManager.triggerDeckDragDrop(resolvedFile, targetDeck, false, mixer)
                }
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) ImGui.setTooltip("Preview patch on Deck C (Preview/C).")
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
                // Compute insertion slot inside the target block — beginDragDropTarget() succeeds
                // on rect overlap alone, without needing isItemHovered() or isMouseDragging().
                val mouseY = ImGui.getMousePosY()
                val insertBefore = mouseY < (itemMinY + itemMaxY) * 0.5f
                val effectiveSlot = if (insertBefore) index else index + 1
                insertSlot = effectiveSlot
                insertLineY = if (insertBefore) itemMinY else itemMaxY

                val payload = ImGui.acceptDragDropPayload<Int>("PLAYLIST_PATCH_ITEM")
                if (payload != null) {
                    moveFrom = payload
                    val rawTo = if (payload < effectiveSlot) effectiveSlot - 1 else effectiveSlot
                    moveTo = rawTo.coerceIn(0, playlist.patches.size - 1)
                }
                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()

            // Right-click menu
            if (ImGui.beginPopupContextItem("playlist_item_menu")) {
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
    }
}
