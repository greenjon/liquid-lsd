package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.SavePresetModal
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import mu.KotlinLogging
import java.io.File

object FXPresetListPanel {
    private val logger = KotlinLogging.logger {}
    val searchBuffer = ImString(256)
    var selectedAsset: AssetItem? = null
    var shouldFocusSearch: Boolean = false
    var filteredPresets: List<AssetItem> = emptyList()

    private var lastQuery: String = ""
    private var lastAllPresets: List<AssetItem>? = null
    private var cachedFiltered: List<AssetItem> = emptyList()

    fun draw(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        val btnSize = ImGui.getFrameHeight()

        // Title Bar: "FX Presets" on the left, [+] button on the right
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("FX Presets")
        }
        ImGui.sameLine()
        val rightX = ImGui.getWindowContentRegionMaxX() - btnSize
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

        // [ + ] Create / Save FX Slot Preset button
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.PLUS}##fx_preset_new", btnSize, btnSize)) {
                ImGui.openPopup("create_new_fx_preset_popup")
            }
        }
        itemTooltip("Save an FX slot preset from a deck...")

        if (ImGui.beginPopup("create_new_fx_preset_popup")) {
            ImGui.textDisabled("Save FX slot from:")
            ImGui.separator()

            val decks = listOf("Deck A" to mixer.deckA, "Deck B" to mixer.deckB, "Deck BG" to mixer.deckBG, "Deck PV" to mixer.deckPV)
            for ((deckLabel, deck) in decks) {
                if (ImGui.beginMenu(deckLabel)) {
                    for (i in 0 until Deck.FX_SLOT_COUNT) {
                        val slotNum = i + 1
                        val fx = deck.fxSlots[i]
                        val hasFx = fx != null && fx.id.isNotEmpty()
                        val label = if (fx != null && fx.id.isNotEmpty()) "Slot $slotNum: ${fx.displayName}" else "Slot $slotNum: Empty"
                        if (ImGui.menuItem(label, "", false, hasFx)) {
                            deck.toFxSlotDto(i)?.let { slotDto ->
                                SavePresetModal.request(
                                    title = "Save FX Slot Preset As",
                                    confirmLabel = "Save",
                                    defaultName = fx?.displayName?.lowercase()?.replace(" ", "_") ?: "fx_preset",
                                    targetDir = FileSystemManager.getFxPresetsRoot(),
                                    extension = "lsdfx"
                                ) { name, tags ->
                                    val file = File(FileSystemManager.getFxPresetsRoot(), "$name.lsdfx")
                                    session.presetRepository.saveFxPresetAsync(file, name, slotDto, tags)
                                }
                            }
                        }
                    }
                    ImGui.endMenu()
                }
            }
            ImGui.endPopup()
        }

        ImGui.separator()
        ImGui.spacing()

        // Search Filter Bar
        val searchWidth = ImGui.getContentRegionAvailX()
        ImGui.setNextItemWidth(searchWidth)
        if (shouldFocusSearch) {
            ImGui.setKeyboardFocusHere()
            shouldFocusSearch = false
        }
        ImGui.inputTextWithHint("##fxPresetSearch", "Search FX presets & tags...", searchBuffer)
        if (ImGui.isItemActive()) {
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                searchBuffer.set("")
                LibraryPanel.shouldReclaimFocus = true
            }
        }
        itemTooltip("Type to filter FX presets (.lsdfx) by name or tags.")

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##fx_presets_scroll", 0f, 0f, false)) {
            val allPresets = FileSystemManager.scanAllFxPresets()
            val query = searchBuffer.get().trim().lowercase()

            val filtered = if (allPresets === lastAllPresets && query == lastQuery) {
                cachedFiltered
            } else {
                lastQuery = query
                lastAllPresets = allPresets
                val res = if (query.isEmpty()) {
                    allPresets
                } else {
                    allPresets.filter { asset ->
                        asset.name.lowercase().contains(query) || asset.tags.any { it.lowercase().contains(query) }
                    }
                }
                cachedFiltered = res
                res
            }
            filteredPresets = filtered

            val btnW = 20f
            val isPanelFocused = ImGui.isWindowFocused()

            filtered.forEachIndexed { index, asset ->
                val isSelected = selectedAsset?.path == asset.path
                val popupId = "fx_preset_context_$index"

                val rowHeight = (session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() } + 6f).coerceAtLeast(20f)
                val itemW = (ImGui.getContentRegionAvailX() - btnW - 4f).coerceAtLeast(10f)

                if (isSelected && LibraryPanel.shouldScrollToSelection) {
                    ImGui.setScrollHereY(0.5f)
                }

                if (isSelected) {
                    val highlightCol = ImGui.colorConvertFloat4ToU32(0.25f, 0.45f, 0.75f, 0.4f)
                    val pMinX = ImGui.getCursorScreenPosX()
                    val pMinY = ImGui.getCursorScreenPosY()
                    val pMaxX = pMinX + ImGui.getContentRegionAvailX()
                    val pMaxY = pMinY + rowHeight
                    ImGui.getWindowDrawList().addRectFilled(pMinX, pMinY, pMaxX, pMaxY, highlightCol)
                }

                ImGui.selectable("${Icons.ZAP} ${asset.displayName}##fx_preset_$index", isSelected, 0, itemW, rowHeight)
                val isRowHovered = ImGui.isItemHovered()

                if (ImGui.isItemClicked(0)) {
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
                    selectedAsset = asset
                }

                // Double-click: load to active deck's first vacant or focused slot
                if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                    val targetIsA = mixer.crossfade.value > 0.0f
                    val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB
                    val file = File(asset.path)
                    loadFxPresetToFirstVacantSlot(session, targetDeck, file)
                }

                // Drag source for FX preset
                if (ImGui.beginDragDropSource()) {
                    ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
                    ImGui.textUnformatted(asset.name)
                    ImGui.endDragDropSource()
                }

                ImGui.sameLine(0f, 0f)
                BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "fx_preset_$index", btnW)

                // Context menu
                if (ImGui.beginPopup(popupId)) {
                    val file = File(asset.path)
                    val decks = listOf("Deck A" to mixer.deckA, "Deck B" to mixer.deckB, "Deck BG" to mixer.deckBG, "Deck PV" to mixer.deckPV)
                    for ((deckLabel, deck) in decks) {
                        if (ImGui.beginMenu("Load to $deckLabel")) {
                            for (s in 0 until Deck.FX_SLOT_COUNT) {
                                val slotNum = s + 1
                                if (ImGui.menuItem("Slot $slotNum")) {
                                    session.presetRepository.loadFxPresetAsync(file).thenAccept { presetDto ->
                                        deck.applyFxSlot(s, presetDto.slot)
                                    }
                                }
                            }
                            ImGui.endMenu()
                        }
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Rename...")) {
                        BrowserPopupHandler.renameTarget = asset
                        BrowserPopupHandler.renameBuffer.set(asset.name)
                        BrowserPopupHandler.pendingOpenRenamePopup = true
                    }
                    if (ImGui.menuItem("Clone")) {
                        FileSystemManager.cloneFile(asset.path)
                    }
                    if (ImGui.menuItem("Delete...")) {
                        BrowserPopupHandler.deleteTarget = asset
                        BrowserPopupHandler.pendingOpenDeletePopup = true
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Reveal in File Manager")) {
                        val parentDir = File(asset.path).parentFile
                        if (parentDir != null && parentDir.exists()) {
                            try {
                                java.awt.Desktop.getDesktop().open(parentDir)
                            } catch (e: Exception) {
                                logger.error(e) { "Failed to open directory" }
                            }
                        }
                    }
                    ImGui.endPopup()
                }
            }

            if (LibraryPanel.shouldReclaimFocus && isPanelFocused) {
                ImGui.setKeyboardFocusHere(-1)
            }
        }
        ImGui.endChild()
    }

    fun loadFxPresetToFirstVacantSlot(session: SessionContext, deck: Deck, file: File, fallbackSlot: Int = 0) {
        val vacantIndex = (0 until Deck.FX_SLOT_COUNT).firstOrNull { deck.fxSlots[it] == null } ?: fallbackSlot
        session.presetRepository.loadFxPresetAsync(file).thenAccept { presetDto ->
            deck.applyFxSlot(vacantIndex, presetDto.slot)
        }
    }
}
