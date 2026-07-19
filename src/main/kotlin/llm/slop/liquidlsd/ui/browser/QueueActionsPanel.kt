package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.patches.PlayQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging
import java.io.File

object QueueActionsPanel {
    private val logger = KotlinLogging.logger {}

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        // Header Row
        if (ImGui.checkbox("AUTO-VJ", session.playQueueManager.isAutoVJEnabled)) {
            session.playQueueManager.isAutoVJEnabled = !session.playQueueManager.isAutoVJEnabled
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Enable automatic transition queue. Will cycle through queue patches at set intervals.")
        }
        
        ImGui.sameLine()
        val repeatActive = session.playQueueManager.isRepeatEnabled
        if (repeatActive) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active
            ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.15f, 0.5f, 0.4f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.05f, 0.3f, 0.2f, 1.0f)
        }
        if (ImGui.button("${Icons.REPEAT}##repeatQueue")) {
            session.playQueueManager.isRepeatEnabled = !session.playQueueManager.isRepeatEnabled
        }
        if (repeatActive) {
            ImGui.popStyleColor(4)
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Repeat Queue: cycle back to start when the bottom is reached.")
        }

        ImGui.sameLine()
        val shuffleActive = session.playQueueManager.isShuffleEnabled
        if (shuffleActive) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active
            ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.15f, 0.5f, 0.4f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.05f, 0.3f, 0.2f, 1.0f)
        }
        if (ImGui.button("${Icons.SHUFFLE}##shuffleQueue")) {
            session.playQueueManager.isShuffleEnabled = !session.playQueueManager.isShuffleEnabled
            if (session.playQueueManager.isShuffleEnabled) {
                session.playQueueManager.initializeShuffle()
            }
        }
        if (shuffleActive) {
            ImGui.popStyleColor(4)
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Shuffle Queue: play patches in a random order.")
        }

        ImGui.sameLine()
        if (ImGui.button("Clear")) {
            session.playQueueManager.clearQueue()
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Empty the play queue.")
        }
        ImGui.sameLine()
        if (ImGui.button("Export")) {
            ImGui.openPopup("ExportQueuePopup")
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Save current queue sequence as a new playlist.")
        }
        BrowserPopupHandler.drawExportQueuePopup(session)
        
        ImGui.separator()
        ImGui.spacing()
        
        // Queue list
        var moveFrom = -1
        var moveTo = -1
        var removeFromQueueIndex = -1
        // Insertion-line state: slot where the next drop will land, and the Y pixel for the indicator line.
        var insertSlot = -1
        var insertLineY = -1f
        val insertLineColor = (255 shl 24) or (204 shl 16) or (255 shl 8) or 102 // mint-green, ABGR

        session.playQueueManager.queue.forEachIndexed { index, file ->
            val isActive = index == session.playQueueManager.activeIndex
            val label = "${index + 1}. ${file.nameWithoutExtension}${if (isActive) " ->" else ""}"

            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f)
            }

            ImGui.selectable("$label##queue_$index", false)

            // Drag source (QUEUE_ITEM reorder)
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("QUEUE_ITEM", index as Any)
                ImGui.text("Moving $label")
                ImGui.endDragDropSource()
            }

            if (isActive) {
                ImGui.popStyleColor()
            }

            // Store item rect for insertion-line computation inside the target block
            val itemMinY = ImGui.getItemRectMinY()
            val itemMaxY = ImGui.getItemRectMaxY()

            ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
            if (ImGui.beginDragDropTarget()) {
                // Compute insertion slot from mouse Y relative to item midpoint.
                // This must happen inside beginDragDropTarget, which guarantees the mouse
                // is actually over this item's rect — no dependency on isItemHovered() or isMouseDragging().
                val mouseY = ImGui.getMousePosY()
                val insertBefore = mouseY < (itemMinY + itemMaxY) * 0.5f
                val effectiveSlot = if (insertBefore) index else index + 1
                insertSlot = effectiveSlot
                insertLineY = if (insertBefore) itemMinY else itemMaxY

                // 1. Reorder within queue
                val queuePayload = ImGui.acceptDragDropPayload<Int>("QUEUE_ITEM")
                if (queuePayload != null) {
                    moveFrom = queuePayload
                    // moveQueueItem does removeAt(from) then add(to) on the shortened list
                    val rawTo = if (queuePayload < effectiveSlot) effectiveSlot - 1 else effectiveSlot
                    moveTo = rawTo.coerceIn(0, session.playQueueManager.queue.size - 1)
                }

                // 2. Insert asset from center panel
                val assetPayload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (assetPayload != null) {
                    val droppedFile = File(assetPayload)
                    val insertAt = effectiveSlot.coerceIn(0, session.playQueueManager.queue.size)
                    if (droppedFile.extension.lowercase() in listOf("patch", "lsd", "json")) {
                        session.playQueueManager.queue.add(insertAt, droppedFile)
                        logger.info { "Inserted patch from drag-drop at slot $insertAt: ${droppedFile.name}" }
                    } else if (droppedFile.extension.lowercase() in listOf("playlist", "lsdset")) {
                        val files = session.playQueueManager.parsePlaylist(droppedFile)
                        session.playQueueManager.queue.addAll(insertAt, files)
                        logger.info { "Inserted playlist from drag-drop at slot $insertAt: ${droppedFile.name} (${files.size} items)" }
                    }
                }
                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()

            // Right-click menu
            if (ImGui.beginPopupContextItem("queue_item_menu_$index")) {
                if (ImGui.menuItem("Remove")) {
                    removeFromQueueIndex = index
                }
                ImGui.endPopup()
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
            session.playQueueManager.moveQueueItem(moveFrom, moveTo)
        }
        if (removeFromQueueIndex != -1) {
            session.playQueueManager.removeFromQueue(removeFromQueueIndex)
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
                        session.playQueueManager.appendToQueue(file)
                    } else if (file.extension.lowercase() in listOf("playlist", "lsdset")) {
                        session.playQueueManager.appendPlaylistToQueue(file)
                    }
                }
                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()
        }
    }
}
