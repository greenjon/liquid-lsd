package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.SmoothScroll
import llm.slop.liquidlsd.rendering.liveDeck
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
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.presets.FxShortlist
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import llm.slop.liquidlsd.ui.pushOpenDropdownPadding
import llm.slop.liquidlsd.ui.popOpenDropdownPadding
import llm.slop.liquidlsd.ui.pushOpenDropdownFont
import llm.slop.liquidlsd.ui.popOpenDropdownFont
import llm.slop.liquidlsd.ui.DROPDOWN_ROW_HEIGHT
import llm.slop.liquidlsd.ui.selectableRow
import mu.KotlinLogging
import java.io.File

/**
 * Unified FX browser for the Library: stock ISF filters, saved single FX
 * presets (.lsdfx), and saved FX chains (.lsdfxchain) in one filterable list.
 * Stock filters carry no persisted parameters, so they only support "Load to
 * Deck" — never "Add to Playlist"/"Add to A/B Queue" (those are reserved for
 * saved singles/chains, which have reproducible state).
 */
object FXBrowserPanel {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private const val STOCK_PATH_PREFIX = "stock-fx://"

    val selection = MultiSelectionModel<AssetItem>()
    var selectedAsset: AssetItem?
        get() = selection.leadItem
        set(value) {
            selection.setSingle(value)
        }
    var filteredRows: List<AssetItem> = emptyList()

    internal fun drawCreatePopup(session: SessionContext, mixer: Mixer) {
        pushOpenDropdownPadding()
        if (ImGui.beginPopup("create_new_fx_popup")) {
            pushOpenDropdownFont()
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
                            llm.slop.liquidlsd.ui.FxChainHeader.openSaveSlotAsModal(session, chain, i)
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
                    llm.slop.liquidlsd.ui.FxChainHeader.openSaveAsModal(session, chain)
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }

    /** The row loop, used by the unified [BrowserPane]. [infoFor] is drawn as a muted second column; [contextExtras] adds items on top of each row menu. */
    internal fun drawRows(
        session: SessionContext, mixer: Mixer, rows: List<AssetItem>,
        infoFor: ((AssetItem) -> String)? = null, contextExtras: ((AssetItem) -> Unit)? = null, playlistRows: PlaylistRows? = null,
        target: ApplyTarget? = null
    ) {
        val loaded = LoadedMarks.fxSnapshot(mixer)
        rows.forEachIndexed { index, asset ->
            ImGui.pushID(index)
            drawRow(session, mixer, rows, asset, index, 20f, infoFor?.invoke(asset) ?: "", contextExtras, playlistRows, target, loaded.marksFor(asset))
            ImGui.popID()
        }
        playlistRows?.finish()
    }

    private fun drawRow(
        session: SessionContext, mixer: Mixer, rows: List<AssetItem>, asset: AssetItem, index: Int, btnW: Float,
        info: String = "", contextExtras: ((AssetItem) -> Unit)? = null, playlistRows: PlaylistRows? = null,
        target: ApplyTarget? = null, marks: List<LoadedMarks.Mark> = emptyList()
    ) {
        // Stock filters leave the icon slot blank unless starred; saved singles and chains keep their own marks.
        val icon = when (asset.type) {
            AssetType.FX_STOCK -> if (FxShortlist.isFavorite(asset.path.removePrefix(STOCK_PATH_PREFIX))) "\u2605" else null
            else -> if (BrowseFavorites.isFavorite(BrowseKind.FX, asset.path)) "\u2605" else if (asset.type == AssetType.FX_CHAIN) Icons.ACTIVITY else Icons.ZAP
        }
        val isSelected = selection.isSelected(asset)
        val popupId = "fx_browser_context_$index"

        val availW = ImGui.getContentRegionAvailX()
        val itemW = (availW - btnW - 4f - LoadedMarks.GUTTER_W).coerceAtLeast(10f)
        if (target?.isApplied(asset) == true) LoadedMarks.drawBoundFill(availW - btnW - 4f, DROPDOWN_ROW_HEIGHT)
        val gutter = LoadedMarks.beginGutter()

        if (isSelected) SmoothScroll.follow(LibraryPanel.shouldScrollToSelection)

        var nameElided = false
        session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
            PresetListPanel.rowSelectable("fx_browser_$index", icon, PresetListPanel.rowName(asset), info, asset.gpuCost, isSelected, itemW)
            nameElided = PresetListPanel.lastNameElided
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { PresetListPanel.tourRowKey(BrowseKind.FX, asset) }
        LoadedMarks.drawGutter(gutter, marks, DROPDOWN_ROW_HEIGHT)
        val isRowHovered = ImGui.isItemHovered()
        val about = when (asset.type) {
            AssetType.FX_STOCK -> "Stock ISF filter — load only, not saveable to a playlist or queue." +
                if (asset.gpuCost >= PresetListPanel.GPU_COST_HEAVY) "\n${Icons.GAUGE} ${PresetListPanel.GPU_COST_TIP}" else ""
            AssetType.FX_CHAIN -> "Saved 3-slot FX chain (.lsdfxchain) — replaces all 3 slots of the target chain."
            else -> "Saved single FX preset (.lsdfx) — loads into one FX slot."
        }
        val how = if (target != null) "Click: apply. Right-click: more." else "Double-click or Enter: load to the live deck. Ctrl/Shift+click: select several. Right-click: more."
        itemTooltip(listOfNotNull(PresetListPanel.fullNameTip(asset, nameElided), about, how).joinToString("\n"))

        if (ImGui.isItemClicked(0)) {
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            PresetListPanel.clickSelect(selection, asset, rows, target)
            DockActions.tap(target, asset)
        }
        if (ImGui.isItemClicked(1)) {
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            if (!selection.isSelected(asset)) selection.setSingle(asset)
            ImGui.openPopup(popupId)
        }

        if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
            DockActions.doubleClick(session, mixer, null, BrowseKind.FX, asset, target)
        }

        // Drag source. Saved singles/chains carry their file path (ASSET_ITEM) so they can also go
        // into playlists and queues; stock filters have no persisted state, so they use their own
        // payload that only FX slots accept (see FxSlotCell).
        if (ImGui.beginDragDropSource()) {
            if (playlistRows != null) {
                ImGui.setDragDropPayload(PAYLOAD_PLAYLIST_ITEM, playlistRows.indexOfRow(index) as Any)
                ImGui.textUnformatted(asset.name)
                ImGui.endDragDropSource()
            } else {
                if (asset.type == AssetType.FX_STOCK) {
                    ImGui.setDragDropPayload(llm.slop.liquidlsd.ui.FxSlotCell.PAYLOAD_STOCK_FILTER, asset.path.removePrefix(STOCK_PATH_PREFIX) as Any)
                    ImGui.textUnformatted(asset.name)
                } else {
                    // A drag from inside a multi-selection carries every selected saved FX, one path per line.
                    val targets = savedTargets(rows, asset)
                    ImGui.setDragDropPayload("ASSET_ITEM", targets.joinToString("\n") { it.path } as Any)
                    ImGui.textUnformatted(if (targets.size > 1) "Moving ${targets.size} FX" else asset.name)
                }
                ImGui.endDragDropSource()
            }
        }

        playlistRows?.dropTarget(index)

        ImGui.sameLine(0f, 0f)
        BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "fx_browser_$index", btnW)

        pushOpenDropdownPadding()
        if (ImGui.beginPopup(popupId)) {
            pushOpenDropdownFont()
            BrowserPane.applyMenuItem(target, asset)
            if (contextExtras != null) {
                contextExtras(asset)
                ImGui.separator()
            }
            if (selection.count > 1 && selection.isSelected(asset)) drawMultiMenu(rows) else drawContextMenu(session, mixer, asset)
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }

    /** The saved FX a row action covers: the selected saved rows when [asset] is one of them, else [asset] alone. Stock filters never queue. */
    private fun savedTargets(rows: List<AssetItem>, asset: AssetItem): List<AssetItem> {
        val inOrder = selection.getSelectedInOrder(rows).filter { it.type != AssetType.FX_STOCK }
        return if (inOrder.any { it.path == asset.path }) inOrder else listOf(asset)
    }

    /** The row menu while several rows are selected: queue, playlist and delete for the saved FX among them (stock filters load one at a time). */
    private fun drawMultiMenu(rows: List<AssetItem>) {
        val saved = selection.getSelectedInOrder(rows).filter { it.type != AssetType.FX_STOCK }
        ImGui.textDisabled("${selection.count} FX Selected")
        ImGui.separator()
        if (saved.isEmpty()) {
            ImGui.textDisabled("Stock filters load one at a time")
            return
        }
        val n = if (saved.size > 1) "${saved.size} FX" else "1 FX"
        if (ImGui.menuItem("Add $n to A/B FX Queue")) saved.forEach { FXQueueManager.appendToQueue(File(it.path)) }
        if (ImGui.menuItem("Add $n to BG FX Queue")) saved.forEach { FXBgQueueManager.appendToQueue(File(it.path)) }
        LibraryPanel.selectedFxPlaylistFile?.let { pl ->
            if (ImGui.menuItem("Add $n to '${pl.nameWithoutExtension}'")) appendToActiveFxPlaylist(pl, saved.map { it.path })
        }
        ImGui.separator()
        if (ImGui.menuItem("Delete $n...")) BrowserPopupHandler.openDeleteConfirmation(saved)
    }

    /** A single FX that found no vacant slot, waiting for the user to pick the slot to overwrite. */
    private var pendingOverwrite: Pair<Deck, AssetItem>? = null
    private var openOverwritePopup = false

    internal fun applyToDeck(session: SessionContext, asset: AssetItem, deck: Deck) {
        val file = File(asset.path)
        when (asset.type) {
            AssetType.FX_STOCK, AssetType.FX_PRESET -> {
                val slot = FxOps.firstVacantSlot(deck.fxChain)
                if (slot == null) {
                    pendingOverwrite = deck to asset
                    openOverwritePopup = true
                } else loadSingle(session, asset, deck.fxChain, slot)
            }
            AssetType.FX_CHAIN -> FxOps.loadChain(session, file, deck.fxChain, undoable = true)
            else -> {}
        }
    }

    /**
     * The controller's tap on an FX row with nothing bound: applies like a double-click on the live deck, but a controller has no popup,
     * so a single FX goes into the first vacant slot (the last slot when the chain is full). Undoable; toasts where it landed.
     */
    internal fun applyToDeckFromController(session: SessionContext, asset: AssetItem, deck: Deck, deckLabel: String) {
        when (asset.type) {
            AssetType.FX_STOCK, AssetType.FX_PRESET -> {
                val slot = FxOps.firstVacantSlot(deck.fxChain) ?: (FxChain.SLOT_COUNT - 1)
                loadSingle(session, asset, deck.fxChain, slot)
                ToastOverlay.show("${asset.name} -> $deckLabel FX slot ${slot + 1}")
            }
            AssetType.FX_CHAIN -> {
                FxOps.loadChain(session, File(asset.path), deck.fxChain, undoable = true)
                ToastOverlay.show("${asset.name} -> $deckLabel FX chain")
            }
            else -> {}
        }
    }

    /** Puts a single stock or saved FX into [slot] of [chain] (undoable). */
    internal fun loadSingle(session: SessionContext, asset: AssetItem, chain: FxChain, slot: Int) {
        if (asset.type == AssetType.FX_STOCK) FxOps.setSlotFilter(chain, slot, asset.path.removePrefix(STOCK_PATH_PREFIX), undoable = true)
        else FxOps.loadSlot(session, File(asset.path), chain, slot, undoable = true)
    }

    /** Asks which slot to overwrite when a double-clicked single FX finds the live deck's chain full. */
    internal fun drawOverwritePopup(session: SessionContext, mixer: Mixer) {
        if (openOverwritePopup) { ImGui.openPopup("fx_browser_overwrite_slot"); openOverwritePopup = false }
        pushOpenDropdownPadding()
        if (ImGui.beginPopup("fx_browser_overwrite_slot")) {
            pushOpenDropdownFont()
            val (deck, asset) = pendingOverwrite ?: (null to null)
            val label = if (deck === mixer.deckA) "Deck A" else "Deck B"
            ImGui.textDisabled("$label FX slots are full. Select slot to overwrite:")
            ImGui.separator()
            if (deck != null && asset != null) {
                for (s in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) {
                    val fx = deck.fxSlots[s]
                    if (ImGui.menuItem("Slot ${s + 1}: ${fx?.displayName ?: "Empty"}")) loadSingle(session, asset, deck.fxChain, s)
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
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
                if (ImGui.menuItem(if (FxShortlist.isFavorite(id)) "★ Remove from Favorites" else "☆ Add to Favorites")) {
                    FxShortlist.toggle(id)
                }
                ImGui.separator()
                drawSlotMenus(chains) { chain, s -> FxOps.setSlotFilter(chain, s, id, undoable = true); BrowseRecents.note(asset) }
            }
            AssetType.FX_PRESET -> {
                drawSlotMenus(chains) { chain, s -> FxOps.loadSlot(session, file, chain, s, undoable = true); BrowseRecents.note(asset) }
                ImGui.separator()
                drawSavedItems(asset, file)
            }
            AssetType.FX_CHAIN -> {
                for ((chainLabel, chain) in chains) {
                    if (ImGui.menuItem("Load to $chainLabel")) {
                        FxOps.loadChain(session, file, chain, undoable = true)
                        BrowseRecents.note(asset)
                    }
                }
                ImGui.separator()
                drawSavedItems(asset, file)
            }
            else -> {}
        }
    }

    /** "Load to <chain>" submenus, one item per slot. */
    private fun drawSlotMenus(chains: List<Pair<String, FxChain>>, load: (FxChain, Int) -> Unit) {
        for ((chainLabel, chain) in chains) {
            if (ImGui.beginMenu("Load to $chainLabel")) {
                for (s in 0 until FxChain.SLOT_COUNT) {
                    if (ImGui.menuItem("Slot ${s + 1}")) load(chain, s)
                }
                ImGui.endMenu()
            }
        }
    }

    /** The queue, playlist and file items every saved FX file shares (same words as the Sources and Transitions menus). */
    private fun drawSavedItems(asset: AssetItem, file: File) {
        if (ImGui.menuItem("Add to A/B FX Queue")) {
            FXQueueManager.appendToQueue(file)
        }
        if (ImGui.menuItem("Add to BG FX Queue")) {
            FXBgQueueManager.appendToQueue(file)
        }
        val activePlaylist = LibraryPanel.selectedFxPlaylistFile
        if (activePlaylist != null) {
            if (ImGui.menuItem("Add to '${activePlaylist.nameWithoutExtension}'")) {
                appendToActiveFxPlaylist(activePlaylist, listOf(asset.path))
            }
        }
        ImGui.separator()
        if (ImGui.menuItem("Rename...")) BrowserPopupHandler.openRename(asset)
        if (ImGui.menuItem("Clone")) {
            FileSystemManager.cloneFile(asset.path)
        }
        if (ImGui.menuItem("Delete...")) BrowserPopupHandler.openDeleteConfirmation(listOf(asset))
        ImGui.separator()
        if (ImGui.menuItem("Reveal in File Manager")) BrowserPopupHandler.revealInFileManager(file)
    }

    private fun appendToActiveFxPlaylist(playlistFile: File, fxPaths: List<String>) {
        if (!playlistFile.exists()) return
        try {
            val dto = json.decodeFromString<FXPlaylistDto>(playlistFile.readText())
            val updated = dto.copy(items = dto.items + fxPaths)
            playlistFile.writeText(json.encodeToString(FXPlaylistDto.serializer(), updated))
            LibraryPanel.refreshAssets()
        } catch (e: Exception) {
            logger.error(e) { "Failed to append $fxPaths to FX playlist ${playlistFile.name}" }
        }
    }
}
