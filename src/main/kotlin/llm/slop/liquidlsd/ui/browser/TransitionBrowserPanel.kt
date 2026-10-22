package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.SmoothScroll
import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.type.ImBoolean
import imgui.type.ImString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.TransitionPlaylistDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.presets.TransitionOps
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.SavePresetModal
import llm.slop.liquidlsd.ui.TransitionSave
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
 * Transition row helpers for the unified browser: stock ISF transition shaders
 * and saved transition presets (.lsdtrans) in one filterable list, mirroring
 * FXBrowserPanel's stock/saved merge. Unlike FX_STOCK filters, stock transitions
 * support Add to A/B Queue / Add to Playlist same as saved presets — only
 * Rename/Clone/Delete (file lifecycle ops) are preset-only.
 */
object TransitionBrowserPanel {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private const val STOCK_PATH_PREFIX = "stock-trans://"

    var selectedAsset: AssetItem? = null
    var filteredRows: List<AssetItem> = emptyList()

    /** The row loop, used by the unified [BrowserPane]. [infoFor] is drawn as a muted second column; [contextExtras] adds items on top of each row menu. */
    internal fun drawRows(
        session: SessionContext, mixer: Mixer, rows: List<AssetItem>,
        infoFor: ((AssetItem) -> String)? = null, contextExtras: ((AssetItem) -> Unit)? = null, playlistRows: PlaylistRows? = null,
        target: ApplyTarget? = null
    ) {
        val loaded = LoadedMarks.fxSnapshot(mixer)
        rows.forEachIndexed { index, asset ->
            ImGui.pushID(index)
            drawRow(session, mixer, asset, index, 20f, infoFor?.invoke(asset) ?: "", contextExtras, playlistRows, target, loaded.marksFor(asset))
            ImGui.popID()
        }
        playlistRows?.finish()
    }

    private fun drawRow(
        session: SessionContext, mixer: Mixer, asset: AssetItem, index: Int, btnW: Float,
        info: String = "", contextExtras: ((AssetItem) -> Unit)? = null, playlistRows: PlaylistRows? = null,
        target: ApplyTarget? = null, marks: List<LoadedMarks.Mark> = emptyList()
    ) {
        // Stock shaders leave the icon slot blank; saved transitions keep their mark.
        val icon = if (asset.type == AssetType.TRANSITION_STOCK) null else Icons.ACTIVITY
        val isSelected = selectedAsset?.path == asset.path
        val popupId = "trans_browser_context_$index"

        val availW = ImGui.getContentRegionAvailX()
        val itemW = (availW - btnW - 4f - LoadedMarks.GUTTER_W).coerceAtLeast(10f)
        if (target?.isApplied(asset) == true) LoadedMarks.drawBoundFill(availW - btnW - 4f, DROPDOWN_ROW_HEIGHT)
        val gutter = LoadedMarks.beginGutter()

        if (isSelected) SmoothScroll.follow(LibraryPanel.shouldScrollToSelection)

        var nameElided = false
        session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
            PresetListPanel.rowSelectable("trans_browser_$index", icon, PresetListPanel.rowName(asset), info, asset.gpuCost, isSelected, itemW)
            nameElided = PresetListPanel.lastNameElided
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { PresetListPanel.tourRowKey(BrowseKind.TRANS, asset) }
        LoadedMarks.drawGutter(gutter, marks, DROPDOWN_ROW_HEIGHT)
        val isRowHovered = ImGui.isItemHovered()
        val about = if (asset.type == AssetType.TRANSITION_STOCK) {
            "Stock ISF transition shader — apply, queue, or add to a playlist."
        } else {
            "Saved transition preset (.lsdtrans) — apply, queue, add to a playlist, or manage."
        }
        val how = if (target != null) "Click: apply. Right-click: more." else "Double-click or Enter: apply to the mixer. Right-click: more."
        itemTooltip(listOfNotNull(PresetListPanel.fullNameTip(asset, nameElided), about, how).joinToString("\n"))

        if (ImGui.isItemClicked(0)) {
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            selectedAsset = asset
            DockActions.tap(target, asset)
        }
        if (ImGui.isItemClicked(1)) {
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            selectedAsset = asset
            ImGui.openPopup(popupId)
        }

        if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
            DockActions.doubleClick(session, mixer, null, BrowseKind.TRANS, asset, target)
        }

        // Drag source: both stock and saved presets resolve to a real file path under ASSET_ITEM,
        // matching whatever drop targets (crossfader track, transition picker) already accept.
        if (ImGui.beginDragDropSource()) {
            if (playlistRows != null) {
                ImGui.setDragDropPayload(PAYLOAD_PLAYLIST_ITEM, playlistRows.indexOfRow(index) as Any)
                ImGui.textUnformatted(asset.name)
                ImGui.endDragDropSource()
            } else {
                ImGui.setDragDropPayload("ASSET_ITEM", fileFor(asset).path as Any)
                ImGui.textUnformatted(asset.name)
                ImGui.endDragDropSource()
            }
        }

        playlistRows?.dropTarget(index)

        ImGui.sameLine(0f, 0f)
        BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "trans_browser_$index", btnW)

        pushOpenDropdownPadding()
        if (ImGui.beginPopup(popupId)) {
            pushOpenDropdownFont()
            BrowserPane.applyMenuItem(target, asset)
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

    /** Resolves [asset] to its backing file — a bundled `.fs` for stock shaders, or the saved `.lsdtrans` path. */
    fun fileFor(asset: AssetItem): File {
        return if (asset.type == AssetType.TRANSITION_STOCK) {
            val id = asset.path.removePrefix(STOCK_PATH_PREFIX)
            val trans = ISFTransitionRegistry.availableTransitions.find { it.id == id }
            trans?.baseDir?.let { File(it, "$id.fs") } ?: File(id)
        } else {
            File(asset.path)
        }
    }

    internal fun applyToMixer(session: SessionContext, mixer: Mixer, asset: AssetItem) {
        if (asset.type == AssetType.TRANSITION_STOCK) {
            val id = asset.path.removePrefix(STOCK_PATH_PREFIX)
            logger.info { "Applying stock transition $id to mixer" }
            TransitionOps.setStock(id)
        } else {
            TransitionOps.loadPreset(fileFor(asset))
        }
    }

    private fun drawContextMenu(session: SessionContext, mixer: Mixer, asset: AssetItem) {
        if (ImGui.menuItem("Apply to Mixer")) {
            applyToMixer(session, mixer, asset)
            BrowseRecents.note(asset)
        }
        ImGui.separator()
        if (ImGui.menuItem("Add to A/B Queue")) {
            TransitionQueueManager.appendToQueue(fileFor(asset))
        }
        val activePlFile = LibraryPanel.selectedTransitionPlaylistFile
        if (activePlFile != null) {
            val token = if (asset.type == AssetType.TRANSITION_STOCK) asset.path.removePrefix(STOCK_PATH_PREFIX) else asset.path
            if (ImGui.menuItem("Add to '${activePlFile.nameWithoutExtension}'")) {
                appendToActiveTransitionPlaylist(activePlFile, token)
            }
        }

        if (asset.type == AssetType.TRANSITION_PRESET) {
            ImGui.separator()
            if (ImGui.menuItem("Rename...")) BrowserPopupHandler.openRename(asset)
            if (ImGui.menuItem("Clone")) {
                FileSystemManager.cloneFile(asset.path).onSuccess {
                    LibraryPanel.refreshAssets()
                }
            }
            if (ImGui.menuItem("Delete...")) BrowserPopupHandler.openDeleteConfirmation(listOf(asset))
            ImGui.separator()
            if (ImGui.menuItem("Reveal in File Manager")) BrowserPopupHandler.revealInFileManager(File(asset.path))
        }
    }

    private fun appendToActiveTransitionPlaylist(playlistFile: File, token: String) {
        if (!playlistFile.exists()) return
        try {
            val dto = json.decodeFromString<TransitionPlaylistDto>(playlistFile.readText())
            val updated = dto.copy(items = dto.items + token)
            playlistFile.writeText(json.encodeToString(TransitionPlaylistDto.serializer(), updated))
            LibraryPanel.refreshAssets()
        } catch (e: Exception) {
            logger.error(e) { "Failed to append item $token to transition playlist ${playlistFile.name}" }
        }
    }
}
