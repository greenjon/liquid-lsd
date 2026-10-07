package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.SmoothScroll
import llm.slop.liquidlsd.ui.TangoPalette
import llm.slop.liquidlsd.ui.ButtonChrome
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import llm.slop.liquidlsd.ui.pushOpenDropdownPadding
import llm.slop.liquidlsd.ui.popOpenDropdownPadding
import llm.slop.liquidlsd.ui.pushOpenDropdownFont
import llm.slop.liquidlsd.ui.popOpenDropdownFont
import llm.slop.liquidlsd.ui.selectableRow
import mu.KotlinLogging
import java.io.File

object TransitionQueuePanel {
    private val logger = KotlinLogging.logger {}
    var selectedIndex: Int = -1
    private val focusFollow = FocusFollow<Int>()

    fun draw(session: SessionContext, mixer: Mixer) {
        QueueToolbar.draw(session, QueueToolbar.Spec(
            caption = "Transition Queue", id = "transQueue",
            prevTooltip = "Trigger previous transition in Transition Queue.",
            onPrev = { TransitionQueueManager.advancePrevious(mixer) },
            nextTooltip = "Trigger next transition in Transition Queue.",
            onNext = { TransitionQueueManager.advanceNext(mixer) },
            auto = QueueToolbar.Auto(
                active = TransitionQueueManager.isAutoAdvanceEnabled,
                tooltip = "Auto-Advance: Automatically advance to the next transition preset when a crossfade triggers.",
                lit = TransitionQueueManager.isAutoAdvanceEnabled,
            ) { TransitionQueueManager.isAutoAdvanceEnabled = !TransitionQueueManager.isAutoAdvanceEnabled },
            repeat = QueueToolbar.Toggle(TransitionQueueManager.isRepeatEnabled,
                "Repeat Queue: cycle back to start when bottom is reached.") {
                TransitionQueueManager.isRepeatEnabled = !TransitionQueueManager.isRepeatEnabled
            },
            shuffle = QueueToolbar.Toggle(TransitionQueueManager.isShuffleEnabled,
                "Shuffle Queue: play transitions in random order.") {
                TransitionQueueManager.isShuffleEnabled = !TransitionQueueManager.isShuffleEnabled
                if (TransitionQueueManager.isShuffleEnabled) {
                    TransitionQueueManager.initializeShuffle()
                }
            },
            exportTooltip = "Export: save live transition queue as a new playlist.",
            onExport = { BrowserPopupHandler.pendingOpenExportQueuePopup = true },
            clearTooltip = "Clear: empty the live transition queue.",
            onClear = {
                TransitionQueueManager.clearQueue()
                selectedIndex = -1
            }
        ))

        if (ImGui.beginChild("##trans_queue_items_scroll", 0f, 0f, false)) {
            var moveFrom = -1
            var moveTo = -1
            var removeFromQueueIndex = -1

            var insertSlot = -1
            var insertLineY = -1f
            val insertLineColor = (255 shl 24) or (204 shl 16) or (255 shl 8) or 102

            TransitionQueueManager.queue.forEachIndexed { index, file ->
                val isActive = index == TransitionQueueManager.activeIndex
                val isSelected = index == selectedIndex
                val displayName = if (file.exists()) file.nameWithoutExtension else file.name.replace("_", " ")
                val label = "${index + 1}. $displayName${if (isActive) " ->" else ""}"

                if (isActive) {
                    ImGui.pushStyleColor(ImGuiCol.Text, TangoPalette.QUEUE_AB_TEXT.u32())
                }

                val popupId = "trans_queue_item_menu_$index"

                if (isSelected && LibraryPanel.shouldReclaimFocus) {
                    ImGui.setKeyboardFocusHere()
                }
                if (isSelected) SmoothScroll.follow(LibraryPanel.shouldScrollToSelection)

                val btnW = 28f
                val availW = ImGui.getContentRegionAvailX()
                val itemW = (availW - btnW).coerceAtLeast(10f)

                var itemClicked = false
                session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
                    if (selectableRow("$label##trans_q_$index", isSelected, itemW)) {
                        itemClicked = true
                    }
                }
                if (itemClicked) {
                    selectedIndex = index
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.TRANSITION_QUEUE
                }
                val isRowHovered = ImGui.isItemHovered()
                if (ImGui.isItemClicked(1)) {
                    ImGui.openPopup(popupId)
                }

                val io = ImGui.getIO()
                val isWindowFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
                val canAutoSelect = isWindowFocused && LibraryPanel.activeSelectionSource == LibraryPanel.SelectionSource.TRANSITION_QUEUE
                if (focusFollow.arrived(index, ImGui.isItemFocused()) && canAutoSelect && !isSelected && !io.wantTextInput) {
                    selectedIndex = index
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.TRANSITION_QUEUE
                }

                // Double click jumps to this transition item
                if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                    TransitionQueueManager.jumpToIndex(index, mixer)
                }

                // Drag source (reorder within queue)
                if (ImGui.beginDragDropSource()) {
                    ImGui.setDragDropPayload("TRANS_QUEUE_ITEM", index as Any)
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

                    val queuePayload = ImGui.acceptDragDropPayload<Int>("TRANS_QUEUE_ITEM")
                    if (queuePayload != null) {
                        moveFrom = queuePayload
                        val rawTo = if (queuePayload < effectiveSlot) effectiveSlot - 1 else effectiveSlot
                        moveTo = rawTo.coerceIn(0, TransitionQueueManager.queue.size - 1)
                    }

                    val assetPayload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                    if (assetPayload != null) {
                        val droppedFile = File(assetPayload)
                        val insertAt = effectiveSlot.coerceIn(0, TransitionQueueManager.queue.size)
                        TransitionQueueManager.insertAt(insertAt, droppedFile)
                    }
                    ImGui.endDragDropTarget()
                }
                ImGui.popStyleColor()

                ImGui.sameLine(0f, 0f)
                BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "trans_q_$index", btnW)

                // Context menu
                pushOpenDropdownPadding()
                if (ImGui.beginPopup(popupId)) {
                    pushOpenDropdownFont()
                    if (ImGui.menuItem("Apply to Mixer")) {
                        TransitionQueueManager.jumpToIndex(index, mixer)
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Remove from queue")) {
                        removeFromQueueIndex = index
                    }
                    popOpenDropdownFont()
                    ImGui.endPopup()
                }
                popOpenDropdownPadding()
            }

            // Keyboard shortcuts
            val io = ImGui.getIO()
            if (selectedIndex in TransitionQueueManager.queue.indices && !io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper) {
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
                TransitionQueueManager.moveItem(moveFrom, moveTo)
                if (selectedIndex == moveFrom) selectedIndex = moveTo
            }
            if (removeFromQueueIndex != -1) {
                TransitionQueueManager.removeFromQueue(removeFromQueueIndex)
                val newSize = TransitionQueueManager.queue.size
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
                        TransitionQueueManager.appendToQueue(file)
                    }
                    ImGui.endDragDropTarget()
                }
                ImGui.popStyleColor()
            }
        }
        ImGui.endChild()
    }
}
