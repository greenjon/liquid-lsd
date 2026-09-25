package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.type.ImBoolean
import imgui.type.ImString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.FXPlaylistDto
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.SavePresetModal
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import mu.KotlinLogging
import java.io.File

/**
 * Unified FX browser for Library column 1: stock ISF filters, saved single FX
 * presets (.lsdfx), and saved FX chains (.lsdfxchain) in one filterable list.
 * Stock filters carry no persisted parameters, so they only support "Load to
 * Deck" — never "Add to Playlist"/"Add to Live Queue" (those are reserved for
 * saved singles/chains, which have reproducible state).
 */
object FXBrowserPanel {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private const val STOCK_PATH_PREFIX = "stock-fx://"

    val searchBuffer = ImString(256)
    var selectedAsset: AssetItem? = null
    var shouldFocusSearch: Boolean = false
    var filteredRows: List<AssetItem> = emptyList()

    var showStock = true
    var showSingle = true
    var showChain = true

    private val showStockRef = ImBoolean(true)
    private val showSingleRef = ImBoolean(true)
    private val showChainRef = ImBoolean(true)

    private var lastQuery: String = ""
    private var lastFilterState: List<Boolean> = listOf(true, true, true)
    private var cachedRows: List<AssetItem> = emptyList()

    fun draw(session: SessionContext, mixer: Mixer) {
        val btnSize = ImGui.getFrameHeight()

        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("FX")
        }
        ImGui.sameLine()
        val totalButtonsWidth = btnSize * 2f + ImGui.getStyle().getItemSpacingX()
        val rightX = ImGui.getWindowContentRegionMaxX() - totalButtonsWidth
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

        // [ + ] Create / Save FX (single slot or 3-slot chain) button
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.PLUS}##fx_browser_new", btnSize, btnSize)) {
                ImGui.openPopup("create_new_fx_popup")
            }
        }
        itemTooltip("Save FX slot or 3-slot chain from a deck...")
        drawCreatePopup(session, mixer)

        ImGui.sameLine()

        // [...] tier filter kebab
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.MORE_VERTICAL}##fx_browser_filter", btnSize, btnSize)) {
                ImGui.openPopup("fx_browser_tier_filter")
            }
        }
        itemTooltip("Filter FX list by type.")
        if (ImGui.beginPopup("fx_browser_tier_filter")) {
            showStockRef.set(showStock)
            if (ImGui.checkbox("Stock Filters", showStockRef)) showStock = showStockRef.get()
            showSingleRef.set(showSingle)
            if (ImGui.checkbox("Saved Single FX", showSingleRef)) showSingle = showSingleRef.get()
            showChainRef.set(showChain)
            if (ImGui.checkbox("Saved FX Chains", showChainRef)) showChain = showChainRef.get()
            ImGui.endPopup()
        }

        ImGui.separator()
        ImGui.spacing()

        val searchWidth = ImGui.getContentRegionAvailX()
        ImGui.setNextItemWidth(searchWidth)
        if (shouldFocusSearch) {
            ImGui.setKeyboardFocusHere()
            shouldFocusSearch = false
        }
        ImGui.inputTextWithHint("##fxBrowserSearch", "Search FX & tags...", searchBuffer)
        if (ImGui.isItemActive() && ImGui.isKeyPressed(ImGuiKey.Escape)) {
            searchBuffer.set("")
            LibraryPanel.shouldReclaimFocus = true
        }
        itemTooltip("Type to filter by name or tags.")

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##fx_browser_scroll", 0f, 0f, false)) {
            val query = searchBuffer.get().trim().lowercase()
            val filterState = listOf(showStock, showSingle, showChain)
            val rows = if (query == lastQuery && filterState == lastFilterState) {
                cachedRows
            } else {
                lastQuery = query
                lastFilterState = filterState
                val result = mutableListOf<AssetItem>()
                if (showStock) {
                    ISFFilterRegistry.availableFilters
                        .filter { query.isEmpty() || it.displayName.lowercase().contains(query) }
                        .sortedBy { it.displayName.lowercase() }
                        .forEach { result.add(AssetItem(path = STOCK_PATH_PREFIX + it.id, name = it.displayName, type = AssetType.FX_STOCK)) }
                }
                if (showSingle) {
                    FileSystemManager.scanAllFxPresets()
                        .filter { query.isEmpty() || it.name.lowercase().contains(query) || it.tags.any { t -> t.lowercase().contains(query) } }
                        .forEach { result.add(it) }
                }
                if (showChain) {
                    FileSystemManager.scanAllFxChains()
                        .filter { query.isEmpty() || it.name.lowercase().contains(query) || it.tags.any { t -> t.lowercase().contains(query) } }
                        .forEach { result.add(it) }
                }
                cachedRows = result
                result
            }
            filteredRows = rows

            val btnW = 20f
            val isPanelFocused = ImGui.isWindowFocused()

            rows.forEachIndexed { index, asset ->
                drawRow(session, mixer, asset, index, btnW)
            }

            if (LibraryPanel.shouldReclaimFocus && isPanelFocused) {
                ImGui.setKeyboardFocusHere(-1)
            }
        }
        ImGui.endChild()
    }

    private fun drawCreatePopup(session: SessionContext, mixer: Mixer) {
        if (ImGui.beginPopup("create_new_fx_popup")) {
            val chains = listOf(
                "Deck A" to mixer.deckA.fxChain, "Deck B" to mixer.deckB.fxChain, "Deck BG" to mixer.deckBG.fxChain,
                "Deck PV" to mixer.deckPV.fxChain, "Master FX" to mixer.masterFxChain
            )

            ImGui.textDisabled("Save single FX slot from:")
            ImGui.separator()
            for ((chainLabel, chain) in chains) {
                if (ImGui.beginMenu(chainLabel)) {
                    for (i in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) {
                        val slotNum = i + 1
                        val fx = chain.slots[i]
                        val hasFx = fx != null && fx.id.isNotEmpty()
                        val label = if (fx != null && fx.id.isNotEmpty()) "Slot $slotNum: ${fx.displayName}" else "Slot $slotNum: Empty"
                        if (ImGui.menuItem(label, "", false, hasFx) && fx != null) {
                            chain.toFxSlotDto(i)?.let { slotDto ->
                                SavePresetModal.request(
                                    title = "Save FX Slot Preset As",
                                    confirmLabel = "Save",
                                    defaultName = fx.displayName.lowercase().replace(" ", "_").ifBlank { "fx_preset" },
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
            ImGui.separator()
            ImGui.textDisabled("Save 3-slot chain from:")
            ImGui.separator()
            for ((chainLabel, chain) in chains) {
                if (ImGui.menuItem(chainLabel)) {
                    val chainDto = chain.toFxChainDto("fx_chain")
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
    }

    private fun drawRow(session: SessionContext, mixer: Mixer, asset: AssetItem, index: Int, btnW: Float) {
        val icon = when (asset.type) {
            AssetType.FX_STOCK -> Icons.SQUARE
            AssetType.FX_CHAIN -> Icons.ACTIVITY
            else -> Icons.ZAP
        }
        val isSelected = selectedAsset?.path == asset.path
        val popupId = "fx_browser_context_$index"

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

        ImGui.selectable("$icon ${asset.displayName}##fx_browser_$index", isSelected, 0, itemW, rowHeight)
        val isRowHovered = ImGui.isItemHovered()
        itemTooltip(
            when (asset.type) {
                AssetType.FX_STOCK -> "Stock ISF filter — load only, not saveable to a playlist or queue."
                AssetType.FX_CHAIN -> "Saved 3-slot FX chain (.lsdfxchain) — replaces all 3 slots of the target chain."
                else -> "Saved single FX preset (.lsdfx) — loads into one FX slot."
            }
        )

        if (ImGui.isItemClicked(0)) {
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            selectedAsset = asset
        }

        if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
            // crossfade: -1.0 = Deck A, 1.0 = Deck B (see Mixer.crossfade) — target the
            // deck that's actually dominant, matching FXQueueManager/FXPlaylistEditorPanel.
            val targetDeck = if (mixer.crossfade.value <= 0.0f) mixer.deckA else mixer.deckB
            applyToDeck(session, asset, targetDeck)
        }

        // Drag source (only saved singles/chains are draggable — stock filters have no
        // persisted state, so they can't be dropped into a playlist or queue)
        if (asset.type != AssetType.FX_STOCK) {
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
                ImGui.textUnformatted(asset.name)
                ImGui.endDragDropSource()
            }
        }

        ImGui.sameLine(0f, 0f)
        BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "fx_browser_$index", btnW)

        if (ImGui.beginPopup(popupId)) {
            drawContextMenu(session, mixer, asset)
            ImGui.endPopup()
        }
    }

    /** Finds the first empty slotIndex in [deck], or 0 if full. */
    private fun firstVacantSlot(deck: Deck): Int {
        for (s in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) {
            if (deck.fxSlots[s] == null) return s
        }
        return 0
    }

    private fun applyToDeck(session: SessionContext, asset: AssetItem, deck: Deck) {
        val file = File(asset.path)
        when (asset.type) {
            AssetType.FX_STOCK -> {
                val id = asset.path.removePrefix(STOCK_PATH_PREFIX)
                FxOps.setSlotFilter(deck.fxChain, firstVacantSlot(deck), id)
            }
            AssetType.FX_PRESET -> FxOps.loadSlot(session, file, deck.fxChain, firstVacantSlot(deck))
            AssetType.FX_CHAIN -> FxOps.loadChain(session, file, deck.fxChain)
            else -> {}
        }
    }

    private fun drawContextMenu(session: SessionContext, mixer: Mixer, asset: AssetItem) {
        val chains = listOf(
            "Deck A" to mixer.deckA.fxChain, "Deck B" to mixer.deckB.fxChain, "Deck BG" to mixer.deckBG.fxChain,
            "Deck PV" to mixer.deckPV.fxChain, "Master FX" to mixer.masterFxChain
        )
        val file = File(asset.path)

        when (asset.type) {
            AssetType.FX_STOCK -> {
                val id = asset.path.removePrefix(STOCK_PATH_PREFIX)
                for ((chainLabel, chain) in chains) {
                    if (ImGui.beginMenu("Load to $chainLabel")) {
                        for (s in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) {
                            val slotNum = s + 1
                            if (ImGui.menuItem("Slot $slotNum")) {
                                FxOps.setSlotFilter(chain, s, id)
                            }
                        }
                        ImGui.endMenu()
                    }
                }
            }
            AssetType.FX_PRESET -> {
                for ((chainLabel, chain) in chains) {
                    if (ImGui.beginMenu("Load to $chainLabel")) {
                        for (s in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) {
                            val slotNum = s + 1
                            if (ImGui.menuItem("Slot $slotNum")) {
                                FxOps.loadSlot(session, file, chain, s)
                            }
                        }
                        ImGui.endMenu()
                    }
                }
                ImGui.separator()
                if (ImGui.menuItem("Add to Live FX Queue (A/B)")) {
                    FXQueueManager.appendToQueue(file)
                }
                if (ImGui.menuItem("Add to BG FX Queue")) {
                    FXBgQueueManager.appendToQueue(file)
                }
                val activePlFilePreset = LibraryPanel.selectedFxPlaylistFile
                if (activePlFilePreset != null) {
                    if (ImGui.menuItem("Add to '${activePlFilePreset.nameWithoutExtension}' Playlist")) {
                        appendToActiveFxPlaylist(activePlFilePreset, asset.path)
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
                    revealInFileManager(file)
                }
            }
            AssetType.FX_CHAIN -> {
                for ((chainLabel, chain) in chains) {
                    if (ImGui.menuItem("Load to $chainLabel")) {
                        FxOps.loadChain(session, file, chain)
                    }
                }
                ImGui.separator()
                if (ImGui.menuItem("Add to Live FX Queue (A/B)")) {
                    FXQueueManager.appendToQueue(file)
                }
                if (ImGui.menuItem("Add to BG FX Queue")) {
                    FXBgQueueManager.appendToQueue(file)
                }
                val activePlFileChain = LibraryPanel.selectedFxPlaylistFile
                if (activePlFileChain != null) {
                    if (ImGui.menuItem("Add to '${activePlFileChain.nameWithoutExtension}' Playlist")) {
                        appendToActiveFxPlaylist(activePlFileChain, asset.path)
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
                    revealInFileManager(file)
                }
            }
            else -> {}
        }
    }

    private fun appendToActiveFxPlaylist(playlistFile: File, fxPath: String) {
        if (!playlistFile.exists()) return
        try {
            val dto = json.decodeFromString<FXPlaylistDto>(playlistFile.readText())
            val updated = dto.copy(items = dto.items + fxPath)
            playlistFile.writeText(json.encodeToString(FXPlaylistDto.serializer(), updated))
            LibraryPanel.refreshAssets()
        } catch (e: Exception) {
            logger.error(e) { "Failed to append item $fxPath to FX playlist ${playlistFile.name}" }
        }
    }

    private fun revealInFileManager(file: File) {
        val parentDir = file.parentFile
        if (parentDir != null && parentDir.exists()) {
            try {
                java.awt.Desktop.getDesktop().open(parentDir)
            } catch (e: Exception) {
                logger.error(e) { "Failed to open directory" }
            }
        }
    }
}
