package llm.slop.liquidlsd.ui.browser

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
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.presets.FxShortlist
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.SavePresetModal
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import llm.slop.liquidlsd.ui.pushOpenDropdownPadding
import llm.slop.liquidlsd.ui.popOpenDropdownPadding
import llm.slop.liquidlsd.ui.pushOpenDropdownFont
import llm.slop.liquidlsd.ui.popOpenDropdownFont
import llm.slop.liquidlsd.ui.selectableRow
import mu.KotlinLogging
import java.io.File

/**
 * Unified FX browser for the Library: stock ISF filters, saved single FX
 * presets (.lsdfx), and saved FX chains (.lsdfxchain) in one filterable list.
 * Stock filters carry no persisted parameters, so they only support "Load to
 * Deck" — never "Add to Playlist"/"Add to Live Queue" (those are reserved for
 * saved singles/chains, which have reproducible state).
 */
object FXBrowserPanel {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private const val STOCK_PATH_PREFIX = "stock-fx://"

    var selectedAsset: AssetItem? = null
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
                    SavePresetModal.request(
                        title = "Save FX Chain As",
                        confirmLabel = "Save",
                        defaultName = "fx_chain",
                        targetDir = FileSystemManager.getFxChainsRoot(),
                        extension = "lsdfxchain"
                    ) { name, tags ->
                        val file = File(FileSystemManager.getFxChainsRoot(), "$name.lsdfxchain")
                        // Captured at confirm time, not when the menu opened; clears the chain's dirty dot.
                        session.presetRepository.saveFxChainAsync(file, name, chain.toFxChainDto(name, tags), tags)
                        chain.markClean(file)
                    }
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
        rows.forEachIndexed { index, asset ->
            ImGui.pushID(index)
            drawRow(session, mixer, asset, index, 20f, infoFor?.invoke(asset) ?: "", contextExtras, playlistRows, target)
            ImGui.popID()
        }
        playlistRows?.finish()
    }

    private fun drawRow(
        session: SessionContext, mixer: Mixer, asset: AssetItem, index: Int, btnW: Float,
        info: String = "", contextExtras: ((AssetItem) -> Unit)? = null, playlistRows: PlaylistRows? = null,
        target: ApplyTarget? = null
    ) {
        val icon = when (asset.type) {
            AssetType.FX_STOCK -> if (FxShortlist.isFavorite(asset.path.removePrefix(STOCK_PATH_PREFIX))) "\u2605" else Icons.SQUARE
            AssetType.FX_CHAIN -> Icons.ACTIVITY
            else -> Icons.ZAP
        }
        val isSelected = selectedAsset?.path == asset.path
        val popupId = "fx_browser_context_$index"

        val itemW = (ImGui.getContentRegionAvailX() - btnW - 4f).coerceAtLeast(10f)

        if (isSelected && LibraryPanel.shouldScrollToSelection) {
            ImGui.setScrollHereY(0.5f)
        }

        session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
            val text = "${if (target?.isApplied(asset) == true) "\u25CF " else ""}$icon ${asset.displayName}"
            selectableRow("${if (info.isNotEmpty()) PresetListPanel.nameForInfo(text, itemW) else text}##fx_browser_$index", isSelected, itemW)
        }
        PresetListPanel.drawInfoColumn(info, itemW)
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
            target?.apply(asset)
        }

        if (target == null && isRowHovered && ImGui.isMouseDoubleClicked(0)) {
            applyToDeck(session, asset, mixer.liveDeck)
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
                } else {
                    ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
            }
                ImGui.textUnformatted(asset.name)
                ImGui.endDragDropSource()
            }
        }

        playlistRows?.dropTarget(index)

        ImGui.sameLine(0f, 0f)
        BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "fx_browser_$index", btnW)

        pushOpenDropdownPadding()
        if (ImGui.beginPopup(popupId)) {
            pushOpenDropdownFont()
            if (contextExtras != null) {
                contextExtras(asset)
                ImGui.separator()
            }
            drawContextMenu(session, mixer, asset)
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }

    /** A single FX that found no vacant slot, waiting for the user to pick the slot to overwrite. */
    private var pendingOverwrite: Pair<Deck, AssetItem>? = null
    private var openOverwritePopup = false

    private fun applyToDeck(session: SessionContext, asset: AssetItem, deck: Deck) {
        val file = File(asset.path)
        when (asset.type) {
            AssetType.FX_STOCK, AssetType.FX_PRESET -> {
                val slot = FxOps.firstVacantSlot(deck.fxChain)
                if (slot == null) {
                    pendingOverwrite = deck to asset
                    openOverwritePopup = true
                } else loadSingle(session, asset, deck, slot)
            }
            AssetType.FX_CHAIN -> FxOps.loadChain(session, file, deck.fxChain)
            else -> {}
        }
    }

    private fun loadSingle(session: SessionContext, asset: AssetItem, deck: Deck, slot: Int) {
        if (asset.type == AssetType.FX_STOCK) FxOps.setSlotFilter(deck.fxChain, slot, asset.path.removePrefix(STOCK_PATH_PREFIX))
        else FxOps.loadSlot(session, File(asset.path), deck.fxChain, slot)
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
                    if (ImGui.menuItem("Slot ${s + 1}: ${fx?.displayName ?: "Empty"}")) loadSingle(session, asset, deck, s)
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
                if (ImGui.menuItem(if (FxShortlist.isFavorite(id)) "\u2605 Remove from Favorites" else "\u2606 Add to Favorites")) {
                    FxShortlist.toggle(id)
                }
                ImGui.separator()
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
