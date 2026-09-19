package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import mu.KotlinLogging
import java.io.File

object FXQueueActionsPanel {
    private val logger = KotlinLogging.logger {}
    var selectedIndex: Int = -1

    fun draw(session: SessionContext, mixer: Mixer) {
        val navBtnW = ImGui.calcTextSize(">").x + ImGui.getStyle().getFramePaddingX() * 2f
        val itemSpacingX = ImGui.getStyle().getItemSpacingX()
        val totalRightW = navBtnW * 2f + itemSpacingX

        // Title Bar: "FX Queue (A/B)" on the left, "<", ">" buttons on the right
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("FX Queue (A/B)")
        }
        ImGui.sameLine()
        val rightX = ImGui.getWindowContentRegionMaxX() - totalRightW
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("<##fxQueuePrev", navBtnW, 0f)) {
                FXQueueManager.advancePrevious(session, mixer)
            }
            itemTooltip("Trigger previous item in live FX Queue.")

            ImGui.sameLine()
            if (ImGui.button(">##fxQueueNext", navBtnW, 0f)) {
                FXQueueManager.advanceNext(session, mixer)
            }
            itemTooltip("Trigger next item in live FX Queue.")

            ImGui.separator()
            ImGui.spacing()

            // Controls Toolbar: Repeat, Shuffle, Export, Clear
            val repeatActive = FXQueueManager.isRepeatEnabled
            if (repeatActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
            }
            if (ImGui.button("${Icons.REPEAT}##fxRepeatQueue")) {
                FXQueueManager.isRepeatEnabled = !FXQueueManager.isRepeatEnabled
            }
            if (repeatActive) {
                ImGui.popStyleColor(2)
            }
            itemTooltip("Repeat Queue: cycle back to start when bottom is reached.")

            ImGui.sameLine()
            val shuffleActive = FXQueueManager.isShuffleEnabled
            if (shuffleActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
            }
            if (ImGui.button("${Icons.SHUFFLE}##fxShuffleQueue")) {
                FXQueueManager.isShuffleEnabled = !FXQueueManager.isShuffleEnabled
                if (FXQueueManager.isShuffleEnabled) {
                    FXQueueManager.initializeShuffle()
                }
            }
            if (shuffleActive) {
                ImGui.popStyleColor(2)
            }
            itemTooltip("Shuffle Queue: play FX presets/chains in random order.")

            ImGui.sameLine()
            if (ImGui.button("Export##fxQueue")) {
                BrowserPopupHandler.pendingOpenExportFxQueuePopup = true
            }
            itemTooltip("Export live FX queue as a new FX playlist.")

            ImGui.sameLine()
            val clearBtnW = ImGui.calcTextSize("Clear").x + ImGui.getStyle().getFramePaddingX() * 2f
            if (ImGui.button("Clear##fxQueue", clearBtnW, 0f)) {
                FXQueueManager.clearQueue()
                selectedIndex = -1
            }
            itemTooltip("Empty the live FX queue.")
        }

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##fx_queue_items_scroll", 0f, 0f, false)) {
            var moveFrom = -1
            var moveTo = -1
            var removeFromQueueIndex = -1

            var insertSlot = -1
            var insertLineY = -1f
            val insertLineColor = (255 shl 24) or (204 shl 16) or (255 shl 8) or 102

            FXQueueManager.queue.forEachIndexed { index, file ->
                val isActive = index == FXQueueManager.activeIndex
                val isSelected = index == selectedIndex
                val displayName = if (file.exists()) file.nameWithoutExtension else file.name.replace("_", " ")
                val label = "${index + 1}. $displayName${if (isActive) " ->" else ""}"

                if (isActive) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f)
                }

                val popupId = "fx_queue_item_menu_$index"

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
                    if (ImGui.selectable("$label##fx_q_$index", isSelected, 0, itemW, 0f)) {
                        itemClicked = true
                    }
                }
                if (itemClicked) {
                    selectedIndex = index
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.FX_QUEUE_AB
                }
                val isRowHovered = ImGui.isItemHovered()
                if (ImGui.isItemClicked(1)) {
                    ImGui.openPopup(popupId)
                }

                val io = ImGui.getIO()
                val isWindowFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
                val canAutoSelect = isWindowFocused && LibraryPanel.activeSelectionSource == LibraryPanel.SelectionSource.FX_QUEUE_AB
                if (canAutoSelect && ImGui.isItemFocused() && !isSelected && !io.wantTextInput) {
                    selectedIndex = index
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.FX_QUEUE_AB
                }

                // Double click jumps to this FX item
                if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                    FXQueueManager.jumpToIndex(index, session, mixer)
                }

                // Drag source (reorder within queue)
                if (ImGui.beginDragDropSource()) {
                    ImGui.setDragDropPayload("FX_QUEUE_ITEM", index as Any)
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

                    val queuePayload = ImGui.acceptDragDropPayload<Int>("FX_QUEUE_ITEM")
                    if (queuePayload != null) {
                        moveFrom = queuePayload
                        val rawTo = if (queuePayload < effectiveSlot) effectiveSlot - 1 else effectiveSlot
                        moveTo = rawTo.coerceIn(0, FXQueueManager.queue.size - 1)
                    }

                    val assetPayload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                    if (assetPayload != null) {
                        val droppedFile = File(assetPayload)
                        val insertAt = effectiveSlot.coerceIn(0, FXQueueManager.queue.size)
                        FXQueueManager.insertAt(insertAt, droppedFile)
                    }
                    ImGui.endDragDropTarget()
                }
                ImGui.popStyleColor()

                ImGui.sameLine(0f, 0f)
                BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "fx_q_$index", btnW)

                // Context menu
                if (ImGui.beginPopup(popupId)) {
                    if (ImGui.menuItem("Apply to Active Deck (A/B)")) {
                        FXQueueManager.jumpToIndex(index, session, mixer)
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Remove from queue")) {
                        removeFromQueueIndex = index
                    }
                    ImGui.endPopup()
                }
            }

            // Keyboard shortcuts
            val io = ImGui.getIO()
            if (selectedIndex in FXQueueManager.queue.indices && !io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper) {
                if (ImGui.isKeyPressed(ImGuiKey.Delete, false) ||
                    ImGui.isKeyPressed(ImGuiKey.Backspace, false)) {
                    removeFromQueueIndex = selectedIndex
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

            if (moveFrom != -1 && moveTo != -1) {
                FXQueueManager.moveItem(moveFrom, moveTo)
                if (selectedIndex == moveFrom) selectedIndex = moveTo
            }
            if (removeFromQueueIndex != -1) {
                FXQueueManager.removeFromQueue(removeFromQueueIndex)
                val newSize = FXQueueManager.queue.size
                if (selectedIndex >= newSize) {
                    selectedIndex = newSize - 1
                }
            }

            // Drop target for empty space below queue items
            val remainingH = ImGui.getContentRegionAvailY()
            if (remainingH > 5f) {
                ImGui.dummy(ImGui.getWindowWidth(), remainingH)
                ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
                if (ImGui.beginDragDropTarget()) {
                    val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                    if (payload != null) {
                        val file = File(payload)
                        FXQueueManager.appendToQueue(file)
                    }
                    ImGui.endDragDropTarget()
                }
                ImGui.popStyleColor()
            }
        }
        ImGui.endChild()
    }
}
