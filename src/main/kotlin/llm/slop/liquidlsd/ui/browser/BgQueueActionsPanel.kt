package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.ButtonChrome
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.presets.BgQueueManager
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

object BgQueueActionsPanel {
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

        // Title Bar: "BG Queue" on the left, "<", "[Play/Pause]", ">" buttons on the right
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("BG Queue")
        }
        ImGui.sameLine()
        val rightX = ImGui.getWindowContentRegionMaxX() - totalRightW
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ButtonChrome.button("<##bgQueuePrev", navBtnW, 0f)) {
                BgQueueManager.triggerPrevious(mixer)
            }
            itemTooltip("Trigger previous preset in BG Queue (Mixer/bgQueuePrev).")

            ImGui.sameLine()
            val autoBgActive = BgQueueManager.isAutoBGEnabled
            val autoBgIcon = if (autoBgActive) Icons.PAUSE else Icons.PLAY
            if (ButtonChrome.button("$autoBgIcon##autoBg", playPauseBtnW, 0f)) {
                BgQueueManager.isAutoBGEnabled = !BgQueueManager.isAutoBGEnabled
            }
            itemTooltip("Auto-BG: Automatically cycle through background presets with smooth dip-to-black transitions.")

            ImGui.sameLine()
            if (ButtonChrome.button(">##bgQueueNext", navBtnW, 0f)) {
                BgQueueManager.triggerNext(mixer)
            }
            itemTooltip("Trigger next preset in BG Queue (Mixer/bgQueueNext).")

            ImGui.separator()
            ImGui.spacing()

            // Controls Row: Repeat, Shuffle, Export, Clear
            val repeatActive = BgQueueManager.isRepeatEnabled
            if (repeatActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active (matching A/B Queue)
                ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.15f, 0.5f, 0.4f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.05f, 0.3f, 0.2f, 1.0f)
            }
            if (ButtonChrome.button("${Icons.REPEAT}##repeatBgQueue")) {
                BgQueueManager.isRepeatEnabled = !BgQueueManager.isRepeatEnabled
            }
            if (repeatActive) {
                ImGui.popStyleColor(4)
            }
            itemTooltip("Repeat BG Queue: cycle back to start when the bottom is reached.")

            ImGui.sameLine()
            val shuffleActive = BgQueueManager.isShuffleEnabled
            if (shuffleActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 1.0f, 0.8f, 1.0f) // Mint green for active (matching A/B Queue)
                ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.15f, 0.5f, 0.4f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.05f, 0.3f, 0.2f, 1.0f)
            }
            if (ButtonChrome.button("${Icons.SHUFFLE}##shuffleBgQueue")) {
                BgQueueManager.isShuffleEnabled = !BgQueueManager.isShuffleEnabled
                if (BgQueueManager.isShuffleEnabled) {
                    BgQueueManager.initializeShuffle()
                }
            }
            if (shuffleActive) {
                ImGui.popStyleColor(4)
            }
            itemTooltip("Shuffle BG Queue: play presets in a random order.")

            ImGui.sameLine()
            if (ButtonChrome.button("Export##bgQueueExport")) {
                ImGui.openPopup("ExportBgQueuePopup")
            }
            itemTooltip("Save current background queue sequence as a new playlist.")
            BrowserPopupHandler.drawExportBgQueuePopup()

            ImGui.sameLine()
            val clearBtnW = ImGui.calcTextSize("Clear").x + ImGui.getStyle().getFramePaddingX() * 2f
            if (ButtonChrome.button("Clear##bgQueue", clearBtnW, 0f)) {
                BgQueueManager.clearQueue()
                selectedIndex = -1
            }
            itemTooltip("Empty the background queue.")
        }

        ImGui.separator()
        ImGui.spacing()
        
        if (ImGui.beginChild("##bg_queue_items_scroll", 0f, 0f, false)) {
            // Queue list
            var moveFrom = -1
            var moveTo = -1
            var removeIndices: List<Int> = emptyList()
            var insertSlot = -1
            var insertLineY = -1f
            val insertLineColor = (255 shl 24) or (166 shl 16) or (90 shl 8) or 230 // Rose/magenta, ABGR

        BgQueueManager.queue.forEachIndexed { index, file ->
            val isActive = index == BgQueueManager.activeIndex
            val isSelected = selection.isSelected(index)
            val label = "${index + 1}. ${file.nameWithoutExtension}${if (isActive) " ->" else ""}"

            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.35f, 0.65f, 1.0f)
            }

            val popupId = "bg_queue_item_menu_$index"

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
                if (selectableRow("$label##bg_queue_$index", isSelected, itemW)) {
                    itemClicked = true
                }
            }
            val io = ImGui.getIO()
            if (itemClicked) {
                val isCtrl = io.keyCtrl || io.keySuper
                val isShift = io.keyShift
                selection.handleClick(index, BgQueueManager.queue.indices.toList(), isCtrl, isShift)
                LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.QUEUE_BG
                PresetListPanel.selection.clear()
                QueueActionsPanel.clearSelection()
            }
            val isRowHovered = ImGui.isItemHovered()
            if (ImGui.isItemClicked(1)) {
                if (!selection.isSelected(index)) {
                    selection.setSingle(index)
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.QUEUE_BG
                    PresetListPanel.selection.clear()
                    QueueActionsPanel.clearSelection()
                }
                ImGui.openPopup(popupId)
            }

            val isWindowFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
            val canAutoSelect = isWindowFocused && LibraryPanel.activeSelectionSource == LibraryPanel.SelectionSource.QUEUE_BG
            if (focusFollow.arrived(index, ImGui.isItemFocused()) && canAutoSelect && !isSelected && !io.wantTextInput && !io.keyCtrl && !io.keyShift && !io.keySuper) {
                LibraryPanel.selectQueueBg(index, session, mixer)
            }

            // Double-click to trigger dip-to-black play
            if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
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
                    val paths = assetPayload.lines().map { it.trim() }.filter { it.isNotBlank() }
                    var insertAt = effectiveSlot.coerceIn(0, BgQueueManager.queue.size)
                    for (path in paths) {
                        val droppedFile = File(path)
                        if (droppedFile.extension.lowercase() in listOf("patch", "lsd", "json")) {
                            BgQueueManager.insertAt(insertAt, droppedFile)
                            insertAt++
                        } else if (droppedFile.extension.lowercase() in listOf("playlist", "lsdplay")) {
                            val files = session.playQueueManager.parsePlaylist(droppedFile)
                            files.forEach { f ->
                                BgQueueManager.insertAt(insertAt, f)
                                insertAt++
                            }
                        }
                    }
                }
                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()

            ImGui.sameLine(0f, 0f)
            BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "bg_queue_$index", btnW)

            // Context menu (triggered by right-click or more button)
            pushOpenDropdownPadding()
            if (ImGui.beginPopup(popupId)) {
                pushOpenDropdownFont()
                val inOrder = selection.selectedItems.filter { it in BgQueueManager.queue.indices }.sorted()
                val targetIndices = if (inOrder.contains(index)) inOrder else listOf(index)
                val count = targetIndices.size

                if (count > 1) {
                    ImGui.textDisabled("$count Presets Selected")
                    ImGui.separator()
                }

                if (count == 1) {
                    if (ImGui.menuItem("Play (Dip to Black)")) {
                        BgQueueManager.playIndex(index, mixer, withDipToBlack = true)
                    }
                    if (ImGui.menuItem("Play (Instant Cut)")) {
                        BgQueueManager.playIndex(index, mixer, withDipToBlack = false)
                    }
                    if (ImGui.menuItem("Load to Deck A")) {
                        BrowserDeckButtons.loadPresetToDeck(session, mixer, file, 1)
                    }
                    if (ImGui.menuItem("Load to Deck B")) {
                        BrowserDeckButtons.loadPresetToDeck(session, mixer, file, 2)
                    }
                    if (ImGui.menuItem("Preview on Deck PV")) {
                        BrowserDeckButtons.loadPresetToDeck(session, mixer, file, 4)
                    }
                    ImGui.separator()
                }
                val abLabel = if (count > 1) "Add $count Presets to A/B Queue" else "Add to A/B Queue"
                if (ImGui.menuItem(abLabel)) {
                    targetIndices.forEach { idx ->
                        val f = BgQueueManager.queue.getOrNull(idx)
                        if (f != null) session.playQueueManager.appendToQueue(f)
                    }
                }
                ImGui.separator()
                val remLabel = if (count > 1) "Remove $count presets from BG queue" else "Remove from BG queue"
                if (ImGui.menuItem(remLabel)) {
                    removeIndices = targetIndices
                }
                val delLabel = if (count > 1) "Delete $count presets from library..." else "Delete preset from library..."
                if (ImGui.menuItem(delLabel)) {
                    val targets = targetIndices.mapNotNull { idx ->
                        BgQueueManager.queue.getOrNull(idx)?.let { f ->
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
        val inOrder = selection.selectedItems.filter { it in BgQueueManager.queue.indices }.sorted()
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
            BgQueueManager.move(moveFrom, moveTo)
            if (selectedIndex == moveFrom) selectedIndex = moveTo
        }
        if (removeIndices.isNotEmpty()) {
            removeIndices.sortedDescending().forEach { idx ->
                BgQueueManager.removeAt(idx)
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
                            BgQueueManager.appendToQueue(file)
                        } else if (file.extension.lowercase() in listOf("playlist", "lsdplay")) {
                            val files = session.playQueueManager.parsePlaylist(file)
                            BgQueueManager.appendAllToQueue(files)
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
