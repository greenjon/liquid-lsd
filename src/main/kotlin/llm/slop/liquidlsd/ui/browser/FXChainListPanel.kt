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
import llm.slop.liquidlsd.ui.SavePresetModal
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import mu.KotlinLogging
import java.io.File

object FXChainListPanel {
    private val logger = KotlinLogging.logger {}
    val searchBuffer = ImString(256)
    var selectedAsset: AssetItem? = null
    var shouldFocusSearch: Boolean = false
    var filteredChains: List<AssetItem> = emptyList()

    private var lastQuery: String = ""
    private var lastAllChains: List<AssetItem>? = null
    private var cachedFiltered: List<AssetItem> = emptyList()

    fun draw(session: SessionContext, mixer: Mixer) {
        val btnSize = ImGui.getFrameHeight()

        // Title Bar: "FX Chains" on the left, [+] button on the right
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("FX Chains")
        }
        ImGui.sameLine()
        val rightX = ImGui.getWindowContentRegionMaxX() - btnSize
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

        // [ + ] Create / Save FX Chain button
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.PLUS}##fx_chain_new", btnSize, btnSize)) {
                ImGui.openPopup("create_new_fx_chain_popup")
            }
        }
        itemTooltip("Capture 4-slot FX chain from a deck...")

        if (ImGui.beginPopup("create_new_fx_chain_popup")) {
            ImGui.textDisabled("Capture FX chain from:")
            ImGui.separator()

            val decks = listOf("Deck A" to mixer.deckA, "Deck B" to mixer.deckB, "Deck BG" to mixer.deckBG, "Deck PV" to mixer.deckPV)
            for ((deckLabel, deck) in decks) {
                if (ImGui.menuItem(deckLabel)) {
                    val chainDto = deck.toFxChainDto("fx_chain")
                    SavePresetModal.request(
                        title = "Save FX Chain As",
                        confirmLabel = "Save",
                        defaultName = "fx_chain",
                        targetDir = FileSystemManager.getFxChainsRoot(),
                        extension = "lsdfxchain"
                    ) { name, tags ->
                        val file = File(FileSystemManager.getFxChainsRoot(), "$name.lsdfxchain")
                        session.presetRepository.saveFxChainAsync(file, name, chainDto, tags)
                    }
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
        ImGui.inputTextWithHint("##fxChainSearch", "Search FX chains & tags...", searchBuffer)
        if (ImGui.isItemActive()) {
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                searchBuffer.set("")
                LibraryPanel.shouldReclaimFocus = true
            }
        }
        itemTooltip("Type to filter FX chains (.lsdfxchain) by name or tags.")

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##fx_chains_scroll", 0f, 0f, false)) {
            val allChains = FileSystemManager.scanAllFxChains()
            val query = searchBuffer.get().trim().lowercase()

            val filtered = if (allChains === lastAllChains && query == lastQuery) {
                cachedFiltered
            } else {
                lastQuery = query
                lastAllChains = allChains
                val res = if (query.isEmpty()) {
                    allChains
                } else {
                    allChains.filter { asset ->
                        asset.name.lowercase().contains(query) || asset.tags.any { it.lowercase().contains(query) }
                    }
                }
                cachedFiltered = res
                res
            }
            filteredChains = filtered

            val btnW = 20f
            val isPanelFocused = ImGui.isWindowFocused()

            filtered.forEachIndexed { index, asset ->
                val isSelected = selectedAsset?.path == asset.path
                val popupId = "fx_chain_context_$index"

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

                ImGui.selectable("${Icons.ACTIVITY} ${asset.displayName}##fx_chain_$index", isSelected, 0, itemW, rowHeight)
                val isRowHovered = ImGui.isItemHovered()

                if (ImGui.isItemClicked(0)) {
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PLAYLIST
                    selectedAsset = asset
                }

                // Double-click: load chain to active deck
                if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                    val targetIsA = mixer.crossfade.value > 0.0f
                    val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB
                    val file = File(asset.path)
                    session.presetRepository.loadFxChainAsync(file).thenAccept { chainDto ->
                        targetDeck.applyFxChain(chainDto)
                    }
                }

                // Drag source for FX chain
                if (ImGui.beginDragDropSource()) {
                    ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
                    ImGui.textUnformatted(asset.name)
                    ImGui.endDragDropSource()
                }

                ImGui.sameLine(0f, 0f)
                BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "fx_chain_$index", btnW)

                // Context menu
                if (ImGui.beginPopup(popupId)) {
                    val file = File(asset.path)
                    val decks = listOf("Deck A" to mixer.deckA, "Deck B" to mixer.deckB, "Deck BG" to mixer.deckBG, "Deck PV" to mixer.deckPV)
                    for ((deckLabel, deck) in decks) {
                        if (ImGui.menuItem("Load to $deckLabel")) {
                            session.presetRepository.loadFxChainAsync(file).thenAccept { chainDto ->
                                deck.applyFxChain(chainDto)
                            }
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
}
