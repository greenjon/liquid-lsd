package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.ButtonChrome
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
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

object QueueActionsPanel {
    private val logger = KotlinLogging.logger {}
    val selection = MultiSelectionModel<Int>()
    private val focusFollow = FocusFollow<Int>()
    var selectedIndex: Int
        get() = selection.leadItem ?: -1
        set(value) {
            if (value >= 0) selection.setSingle(value) else selection.clear()
        }

    fun clearSelection() {
        selection.clear()
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        val navBtnW = ImGui.calcTextSize(">").x + ImGui.getStyle().getFramePaddingX() * 2f
        val playPauseBtnW = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            ImGui.calcTextSize(Icons.PLAY).x.coerceAtLeast(ImGui.calcTextSize(Icons.PAUSE).x) + ImGui.getStyle().getFramePaddingX() * 2f
        }
        val itemSpacingX = ImGui.getStyle().getItemSpacingX()
        val totalRightW = navBtnW * 2f + playPauseBtnW + itemSpacingX * 2f

        // Title Bar: "Queue" on the left, "<", "[Play/Pause]", ">" buttons on the right
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("A/B Queue")
        }
        ImGui.sameLine()
        val rightX = ImGui.getWindowContentRegionMaxX() - totalRightW
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ButtonChrome.button("<##queuePrev", navBtnW, 0f)) {
                session.playQueueManager.triggerPrevious(mixer)
            }
            itemTooltip("Trigger previous preset in A/B Queue (Mixer/queuePrev).")

            ImGui.sameLine()
            val autoVjActive = session.playQueueManager.isAutoVJEnabled
            val autoVjIcon = if (autoVjActive) Icons.PAUSE else Icons.PLAY
            if (ButtonChrome.button("$autoVjIcon##autoVj", playPauseBtnW, 0f)) {
                val nextState = !session.playQueueManager.isAutoVJEnabled
                session.playQueueManager.isAutoVJEnabled = nextState
                if (nextState) {
                    mixer.muteCrossfadeNonMidiCv()
                }
            }
            itemTooltip("Auto-VJ: Automatically cycle through queue presets at set intervals.")

            ImGui.sameLine()
            if (ButtonChrome.button(">##queueNext", navBtnW, 0f)) {
                session.playQueueManager.triggerNext(mixer)
            }
            itemTooltip("Trigger next preset in A/B Queue (Mixer/queueNext).")

            ImGui.separator()
            ImGui.spacing()

            // Controls Row: Repeat, Shuffle, Export, Clear
            val repeatActive = session.playQueueManager.isRepeatEnabled
            if (repeatActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active
                ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.15f, 0.5f, 0.4f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.05f, 0.3f, 0.2f, 1.0f)
            }
            if (ButtonChrome.button("${Icons.REPEAT}##repeatQueue")) {
                session.playQueueManager.isRepeatEnabled = !session.playQueueManager.isRepeatEnabled
            }
            if (repeatActive) {
                ImGui.popStyleColor(4)
            }
            itemTooltip("Repeat Queue: cycle back to start when the bottom is reached.")

            ImGui.sameLine()
            val shuffleActive = session.playQueueManager.isShuffleEnabled
            if (shuffleActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active
                ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.15f, 0.5f, 0.4f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.05f, 0.3f, 0.2f, 1.0f)
            }
            if (ButtonChrome.button("${Icons.SHUFFLE}##shuffleQueue")) {
                session.playQueueManager.isShuffleEnabled = !session.playQueueManager.isShuffleEnabled
                if (session.playQueueManager.isShuffleEnabled) {
                    session.playQueueManager.initializeShuffle()
                }
            }
            if (shuffleActive) {
                ImGui.popStyleColor(4)
            }
            itemTooltip("Shuffle Queue: play presets in a random order.")

            ImGui.sameLine()
            if (ButtonChrome.button("Export")) {
                ImGui.openPopup("ExportQueuePopup")
            }
            itemTooltip("Save current queue sequence as a new playlist.")
            BrowserPopupHandler.drawExportQueuePopup(session)

            ImGui.sameLine()
            val clearBtnW = ImGui.calcTextSize("Clear").x + ImGui.getStyle().getFramePaddingX() * 2f
            if (ButtonChrome.button("Clear##queue", clearBtnW, 0f)) {
                session.playQueueManager.clearQueue()
                selectedIndex = -1
            }
            itemTooltip("Empty the play queue.")
        }

        ImGui.separator()
        ImGui.spacing()
        
        if (ImGui.beginChild("##queue_items_scroll", 0f, 0f, false)) {
            // Queue list
            var moveFrom = -1
            var moveTo = -1
            var removeIndices: List<Int> = emptyList()
            // Insertion-line state: slot where the next drop will land, and the Y pixel for the indicator line.
            var insertSlot = -1
            var insertLineY = -1f
            val insertLineColor = (255 shl 24) or (204 shl 16) or (255 shl 8) or 102 // mint-green, ABGR

        session.playQueueManager.queue.forEachIndexed { index, file ->
            val isActive = index == session.playQueueManager.activeIndex
            val isSelected = selection.isSelected(index)
            val label = "${index + 1}. ${file.nameWithoutExtension}${if (isActive) " ->" else ""}"

            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f)
            }

            val popupId = "queue_item_menu_$index"

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
                if (selectableRow("$label##queue_$index", isSelected, itemW)) {
                    itemClicked = true
                }
            }
            val io = ImGui.getIO()
            if (itemClicked) {
                val isCtrl = io.keyCtrl || io.keySuper
                val isShift = io.keyShift
                selection.handleClick(index, session.playQueueManager.queue.indices.toList(), isCtrl, isShift)
                LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.QUEUE_AB
                PresetListPanel.selection.clear()
                llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
            }
            val isRowHovered = ImGui.isItemHovered()
            if (ImGui.isItemClicked(1)) {
                if (!selection.isSelected(index)) {
                    selection.setSingle(index)
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.QUEUE_AB
                    PresetListPanel.selection.clear()
                    llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
                }
                ImGui.openPopup(popupId)
            }

            val isWindowFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
            val canAutoSelect = isWindowFocused && LibraryPanel.activeSelectionSource == LibraryPanel.SelectionSource.QUEUE_AB
            if (focusFollow.arrived(index, ImGui.isItemFocused()) && canAutoSelect && !isSelected && !io.wantTextInput && !io.keyCtrl && !io.keyShift && !io.keySuper) {
                LibraryPanel.selectQueueAb(index, session, mixer)
            }

            // Double-click to load to standby deck and auto-fade
            if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                session.playQueueManager.playIndex(index, mixer)
            }

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
                    val paths = assetPayload.lines().map { it.trim() }.filter { it.isNotBlank() }
                    var insertAt = effectiveSlot.coerceIn(0, session.playQueueManager.queue.size)
                    for (path in paths) {
                        val droppedFile = File(path)
                        if (droppedFile.extension.lowercase() in listOf("patch", "lsd", "json")) {
                            session.playQueueManager.queue.add(insertAt, droppedFile)
                            insertAt++
                        } else if (droppedFile.extension.lowercase() in listOf("playlist", "lsdplay")) {
                            val files = session.playQueueManager.parsePlaylist(droppedFile)
                            session.playQueueManager.queue.addAll(insertAt, files)
                            insertAt += files.size
                        }
                    }
                }
                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()

            ImGui.sameLine(0f, 0f)
            BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "queue_$index", btnW)

            // Context menu (triggered by right-click or more button)
            pushOpenDropdownPadding()
            if (ImGui.beginPopup(popupId)) {
                pushOpenDropdownFont()
                val inOrder = selection.selectedItems.filter { it in session.playQueueManager.queue.indices }.sorted()
                val targetIndices = if (inOrder.contains(index)) inOrder else listOf(index)
                val count = targetIndices.size

                if (count > 1) {
                    ImGui.textDisabled("$count Presets Selected")
                    ImGui.separator()
                }

                if (count == 1) {
                    if (ImGui.menuItem("Load to Deck A")) {
                        BrowserDeckButtons.loadPresetToDeck(session, mixer, file, 1)
                    }
                    if (ImGui.menuItem("Load to Deck B")) {
                        BrowserDeckButtons.loadPresetToDeck(session, mixer, file, 2)
                    }
                    if (ImGui.menuItem("Load to Deck BG")) {
                        BrowserDeckButtons.loadPresetToDeck(session, mixer, file, 3)
                    }
                    if (ImGui.menuItem("Preview on Deck PV")) {
                        BrowserDeckButtons.loadPresetToDeck(session, mixer, file, 4)
                    }
                    ImGui.separator()
                }
                val bgLabel = if (count > 1) "Add $count Presets to BG Queue" else "Add to BG Queue"
                if (ImGui.menuItem(bgLabel)) {
                    targetIndices.forEach { idx ->
                        val f = session.playQueueManager.queue.getOrNull(idx)
                        if (f != null) llm.slop.liquidlsd.presets.BgQueueManager.appendToQueue(f)
                    }
                }
                ImGui.separator()
                val remLabel = if (count > 1) "Remove $count presets from queue" else "Remove from queue"
                if (ImGui.menuItem(remLabel)) {
                    removeIndices = targetIndices
                }
                val delLabel = if (count > 1) "Delete $count presets from library..." else "Delete preset from library..."
                if (ImGui.menuItem(delLabel)) {
                    val targets = targetIndices.mapNotNull { idx ->
                        session.playQueueManager.queue.getOrNull(idx)?.let { f ->
                            AssetItem(path = f.absolutePath, name = f.nameWithoutExtension, type = AssetType.PRESET)
                        }
                    }
                    BrowserPopupHandler.openDeleteConfirmation(targets)
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()
        }

        // Keyboard shortcuts (Delete / Backspace removes selected items from queue)
        val io = ImGui.getIO()
        val inOrder = selection.selectedItems.filter { it in session.playQueueManager.queue.indices }.sorted()
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

        if (moveFrom != -1 && moveTo != -1) {
            session.playQueueManager.moveQueueItem(moveFrom, moveTo)
            if (selectedIndex == moveFrom) selectedIndex = moveTo
        }
        if (removeIndices.isNotEmpty()) {
            removeIndices.sortedDescending().forEach { idx ->
                session.playQueueManager.removeFromQueue(idx)
            }
            selection.clear()
        }

        // Drop target for the empty space below all queue items (append to end)
        val remainingH = ImGui.getContentRegionAvailY()
        if (remainingH > 5f) {
            ImGui.dummy(ImGui.getWindowWidth(), remainingH)
            ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
            if (ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
                if (payload != null) {
                    val paths = payload.lines().map { it.trim() }.filter { it.isNotBlank() }
                    for (path in paths) {
                        val file = File(path)
                        if (file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                            session.playQueueManager.appendToQueue(file)
                        } else if (file.extension.lowercase() in listOf("playlist", "lsdplay")) {
                            session.playQueueManager.appendPlaylistToQueue(file)
                        }
                    }
                }
                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()
        }
        }
        ImGui.endChild()
    }
}
