package llm.slop.liquidlsd.ui.browser

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
        val icon = if (asset.type == AssetType.TRANSITION_STOCK) Icons.SQUARE else Icons.ACTIVITY
        val isSelected = selectedAsset?.path == asset.path
        val popupId = "trans_browser_context_$index"

        val itemW = (ImGui.getContentRegionAvailX() - btnW - 4f).coerceAtLeast(10f)

        if (isSelected && LibraryPanel.shouldScrollToSelection) {
            ImGui.setScrollHereY(0.5f)
        }

        session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
            val text = "${if (target?.isApplied(asset) == true) "\u25CF " else ""}$icon ${asset.displayName}"
            selectableRow("${if (info.isNotEmpty()) PresetListPanel.nameForInfo(text, itemW) else text}##trans_browser_$index", isSelected, itemW)
        }
        PresetListPanel.drawInfoColumn(info, itemW)
        val isRowHovered = ImGui.isItemHovered()
        itemTooltip(
            if (asset.type == AssetType.TRANSITION_STOCK) {
                "Stock ISF transition shader — apply, queue, or add to a playlist."
            } else {
                "Saved transition preset (.lsdtrans) — apply, queue, add to a playlist, or manage."
            }
        )

        if (ImGui.isItemClicked(0)) {
            LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
            selectedAsset = asset
            target?.apply(asset)
        }

        if (target == null && isRowHovered && ImGui.isMouseDoubleClicked(0)) {
            applyToMixer(session, mixer, asset)
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
        }
        if (ImGui.menuItem("Add to A/B Queue")) {
            TransitionQueueManager.appendToQueue(fileFor(asset))
        }
        val activePlFile = LibraryPanel.selectedTransitionPlaylistFile
        if (activePlFile != null) {
            val token = if (asset.type == AssetType.TRANSITION_STOCK) asset.path.removePrefix(STOCK_PATH_PREFIX) else asset.path
            if (ImGui.menuItem("Add to '${activePlFile.nameWithoutExtension}' Playlist")) {
                appendToActiveTransitionPlaylist(activePlFile, token)
            }
        }

        if (asset.type == AssetType.TRANSITION_PRESET) {
            ImGui.separator()
            if (ImGui.menuItem("Rename...")) {
                BrowserPopupHandler.renameTarget = asset
                BrowserPopupHandler.renameBuffer.set(asset.name)
                BrowserPopupHandler.pendingOpenRenamePopup = true
            }
            if (ImGui.menuItem("Clone")) {
                FileSystemManager.cloneFile(asset.path).onSuccess {
                    LibraryPanel.refreshAssets()
                }
            }
            if (ImGui.menuItem("Delete...")) {
                BrowserPopupHandler.deleteTarget = asset
                BrowserPopupHandler.pendingOpenDeletePopup = true
            }
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
