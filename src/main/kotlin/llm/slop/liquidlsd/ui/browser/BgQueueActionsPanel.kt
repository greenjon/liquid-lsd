package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.Icons
import mu.KotlinLogging
import java.io.File

object BgQueueActionsPanel {
    private val logger = KotlinLogging.logger {}
    var selectedIndex: Int = -1

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        // Header Row
        if (ImGui.checkbox("AUTO-BG", BgQueueManager.isAutoBGEnabled)) {
            BgQueueManager.isAutoBGEnabled = !BgQueueManager.isAutoBGEnabled
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Enable automatic background queue. Will cycle through background presets with smooth dip-to-black transitions.")
        }
        
        ImGui.sameLine()
        val repeatActive = BgQueueManager.isRepeatEnabled
        if (repeatActive) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.35f, 0.65f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.4f, 0.1f, 0.3f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.5f, 0.15f, 0.4f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.3f, 0.05f, 0.2f, 1.0f)
        }
        if (ImGui.button("${Icons.REPEAT}##repeatBgQueue")) {
            BgQueueManager.isRepeatEnabled = !BgQueueManager.isRepeatEnabled
        }
        if (repeatActive) {
            ImGui.popStyleColor(4)
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Repeat BG Queue: cycle back to start when the bottom is reached.")
        }

        ImGui.sameLine()
        val shuffleActive = BgQueueManager.isShuffleEnabled
        if (shuffleActive) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.35f, 0.65f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.4f, 0.1f, 0.3f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.5f, 0.15f, 0.4f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.3f, 0.05f, 0.2f, 1.0f)
        }
        if (ImGui.button("${Icons.SHUFFLE}##shuffleBgQueue")) {
            BgQueueManager.isShuffleEnabled = !BgQueueManager.isShuffleEnabled
            if (BgQueueManager.isShuffleEnabled) {
                BgQueueManager.initializeShuffle()
            }
        }
        if (shuffleActive) {
            ImGui.popStyleColor(4)
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Shuffle BG Queue: play presets in a random order.")
        }

        ImGui.sameLine()
        if (ImGui.button("Clear##bgQueue")) {
            BgQueueManager.clearQueue()
            selectedIndex = -1
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Empty the background queue.")
        }

        ImGui.separator()
        ImGui.spacing()
        
        // Queue list
        var moveFrom = -1
        var moveTo = -1
        var removeFromQueueIndex = -1
        var insertSlot = -1
        var insertLineY = -1f
        val insertLineColor = (255 shl 24) or (166 shl 16) or (90 shl 8) or 230 // Rose/magenta, ABGR

        BgQueueManager.queue.forEachIndexed { index, file ->
            val isActive = index == BgQueueManager.activeIndex
            val isSelected = index == selectedIndex
            val label = "${index + 1}. ${file.nameWithoutExtension}${if (isActive) " ->" else ""}"

            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.35f, 0.65f, 1.0f)
            }

            if (ImGui.selectable("$label##bg_queue_$index", isSelected)) {
                selectedIndex = index
            }

            // Double-click to trigger dip-to-black play
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                BgQueueManager.playIndex(index, mixer, withDipToBlack = true)
            }

            // Drag source (BG_QUEUE_ITEM reorder)
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("BG_QUEUE_ITEM", index as Any)
                ImGui.text("Moving $label")
                ImGui.endDragDropSource()
            }

            if (isActive) {
                ImGui.popStyleColor()
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

                val queuePayload = ImGui.acceptDragDropPayload<Int>("BG_QUEUE_ITEM")
                if (queuePayload != null) {
                    moveFrom = queuePayload
                    val rawTo = if (queuePayload < effectiveSlot) effectiveSlot - 1 else effectiveSlot
                    moveTo = rawTo.coerceIn(0, BgQueueManager.queue.size - 1)
                }

                val assetPayload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (assetPayload != null) {
                    val droppedFile = File(assetPayload)
                    val insertAt = effectiveSlot.coerceIn(0, BgQueueManager.queue.size)
                    if (droppedFile.extension.lowercase() in listOf("patch", "lsd", "json")) {
                        BgQueueManager.insertAt(insertAt, droppedFile)
                        logger.info { "Inserted BG preset from drag-drop at slot $insertAt: ${droppedFile.name}" }
                    } else if (droppedFile.extension.lowercase() in listOf("playlist", "lsdset")) {
                        val files = session.playQueueManager.parsePlaylist(droppedFile)
                        files.forEachIndexed { i, f -> BgQueueManager.insertAt(insertAt + i, f) }
                        logger.info { "Inserted BG playlist from drag-drop at slot $insertAt: ${droppedFile.name} (${files.size} items)" }
                    }
                }
                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()

            // Right-click menu
            if (ImGui.beginPopupContextItem("bg_queue_item_menu_$index")) {
                if (ImGui.menuItem("Play (Dip to Black)")) {
                    BgQueueManager.playIndex(index, mixer, withDipToBlack = true)
                }
                if (ImGui.menuItem("Play (Instant Cut)")) {
                    BgQueueManager.playIndex(index, mixer, withDipToBlack = false)
                }
                ImGui.separator()
                if (ImGui.menuItem("Remove from BG queue")) {
                    removeFromQueueIndex = index
                }
                if (ImGui.menuItem("Delete preset from library...")) {
                    BrowserPopupHandler.deleteTarget = AssetItem(
                        path = file.absolutePath,
                        name = file.nameWithoutExtension,
                        type = AssetType.PRESET
                    )
                    BrowserPopupHandler.pendingOpenDeletePopup = true
                }
                ImGui.endPopup()
            }
        }

        // Keyboard shortcuts (Delete / Backspace removes selected item from queue)
        if (selectedIndex in BgQueueManager.queue.indices && !ImGui.getIO().wantTextInput) {
            if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Delete), false) ||
                ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Backspace), false)) {
                removeFromQueueIndex = selectedIndex
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

        if (moveFrom != -1 && moveTo != -1) {
            BgQueueManager.move(moveFrom, moveTo)
            if (selectedIndex == moveFrom) selectedIndex = moveTo
        }
        if (removeFromQueueIndex != -1) {
            BgQueueManager.removeAt(removeFromQueueIndex)
            val newSize = BgQueueManager.queue.size
            if (selectedIndex >= newSize) {
                selectedIndex = newSize - 1
            }
        }

        // Drop target for the empty space below all queue items (append to end)
        val remainingH = ImGui.getContentRegionAvailY()
        if (remainingH > 5f) {
            ImGui.dummy(ImGui.getWindowWidth(), remainingH)
            ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
            if (ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (payload != null) {
                    val file = File(payload)
                    if (file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                        BgQueueManager.appendToQueue(file)
                    } else if (file.extension.lowercase() in listOf("playlist", "lsdset")) {
                        val files = session.playQueueManager.parsePlaylist(file)
                        BgQueueManager.appendAllToQueue(files)
                    }
                }
                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()
        }
    }
}
