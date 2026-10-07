package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.rendering.ExternalVideoSource
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiFocusedFlags
import imgui.flag.ImGuiKey
import imgui.type.ImBoolean
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.getIssues
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryNavigation
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.PlaylistManager
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.UIManager
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import llm.slop.liquidlsd.ui.showCustomTooltip
import llm.slop.liquidlsd.ui.pushOpenDropdownPadding
import llm.slop.liquidlsd.ui.popOpenDropdownPadding
import llm.slop.liquidlsd.ui.pushOpenDropdownFont
import llm.slop.liquidlsd.ui.popOpenDropdownFont
import llm.slop.liquidlsd.ui.selectableRow
import mu.KotlinLogging
import java.io.File

object PresetListPanel {
    private val logger = KotlinLogging.logger {}

    /** Stock generators carry no persisted parameters, so they only support "Load to
     *  Deck" -- never "Add to Playlist"/"Add to A/B Queue" (those are reserved for
     *  saved presets, which have reproducible state). */
    const val STOCK_PATH_PREFIX = "stock-source://"
    const val PAYLOAD_STOCK_SOURCE = "ASSET_ITEM_STOCK_SOURCE"

    val selection = MultiSelectionModel<AssetItem>()
    private val focusFollow = FocusFollow<String>()
    var selectedAsset: AssetItem?
        get() = selection.leadItem
        set(value) {
            selection.setSingle(value)
        }
    var filteredPresets: List<AssetItem> = emptyList()

    /**
     * The row loop of the list, shared by the unified [BrowserPane]. [favoriteKeys] (stock id or
     * file path) get a star; [infoFor] is drawn as a muted second column; [contextExtras] adds items at the top of each row's context menu.
     */
    internal fun drawRows(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        filtered: List<AssetItem>,
        favoriteKeys: Set<String>? = null,
        infoFor: ((AssetItem) -> String)? = null,
        contextExtras: ((AssetItem) -> Unit)? = null,
        playlistRows: PlaylistRows? = null,
        target: ApplyTarget? = null
    ) {
                filtered.forEachIndexed { index, asset ->
            ImGui.pushID(index)

            val isExternal = asset.type == AssetType.SOURCE_EXTERNAL
            val isStock = asset.type == AssetType.SOURCE_STOCK || isExternal
            val deps = if (isStock) null else (asset.dependencies ?: FileSystemManager.getPresetDependencies(File(asset.path)))
            val issues = deps?.getIssues(session) ?: emptyList()
            val hasIssues = issues.isNotEmpty()

            val icon = if (isStock) Icons.SQUARE else Icons.DISC
            val star = if (favoriteKeys != null && (if (isStock) asset.path.removePrefix(STOCK_PATH_PREFIX) else asset.path) in favoriteKeys) "\u2605 " else ""
            val applied = if (target?.isApplied(asset) == true) "\u25CF " else ""
            val label = if (hasIssues && asset.isValid) "[!] ${asset.name}" else "$applied$star$icon ${asset.displayName}"
            val isSelected = selection.isSelected(asset)

            val popupId = "preset_context_menu_$index"

            if (isSelected && LibraryPanel.shouldReclaimFocus) {
                ImGui.setKeyboardFocusHere()
            }
            if (isSelected && LibraryPanel.shouldScrollToSelection) {
                ImGui.setScrollHereY(0.5f)
            }

            val btnW = 28f
            val availW = ImGui.getContentRegionAvailX()
            val itemW = (availW - btnW).coerceAtLeast(10f)

            if (hasIssues && !isSelected) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.95f, 0.40f, 0.40f, 1f)
            }
            var itemClicked = false
            val infoText = infoFor?.invoke(asset) ?: ""
            session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
                val shown = if (infoText.isNotEmpty()) nameForInfo(label, itemW) else label
                if (selectableRow("$shown##row", isSelected, itemW)) {
                    itemClicked = true
                }
            }
            val io = ImGui.getIO()
            if (itemClicked) {
                val isCtrl = io.keyCtrl || io.keySuper
                val isShift = io.keyShift
                if (target != null) selection.setSingle(asset) else selection.handleClick(asset, filtered, isCtrl, isShift)
                LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
                QueueActionsPanel.clearSelection()
                llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
                DockActions.tap(target, asset)
            }
            if (hasIssues && !isSelected) {
                ImGui.popStyleColor()
            }
            drawInfoColumn(infoText, itemW)
            val isRowHovered = ImGui.isItemHovered()
            if (ImGui.isItemClicked(1)) {
                if (!selection.isSelected(asset)) {
                    selection.setSingle(asset)
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
                    QueueActionsPanel.clearSelection()
                    llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
                }
                ImGui.openPopup(popupId)
            }

            val isWindowFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
            val canAutoSelect = isWindowFocused && (LibraryPanel.activeSelectionSource == null || LibraryPanel.activeSelectionSource == LibraryPanel.SelectionSource.PRESETS)
            if (focusFollow.arrived(asset.path, ImGui.isItemFocused()) && canAutoSelect && !isSelected && !io.wantTextInput && !io.keyCtrl && !io.keyShift && !io.keySuper) {
                LibraryPanel.selectPreset(asset, session, mixer)
            }

            if (isRowHovered) {
                if (hasIssues) {
                    showCustomTooltip(asset.path.hashCode()) {
                        ImGui.textColored(0.95f, 0.40f, 0.40f, 1f, "[!] Preset has inactive or hidden modulators:")
                        ImGui.spacing()
                        for (issue in issues) {
                            ImGui.bullet()
                            ImGui.text("${issue.title}: ${issue.description}")
                        }
                        if (asset.tags.isNotEmpty()) {
                            ImGui.spacing()
                            ImGui.textDisabled("Tags: ${asset.tags.joinToString(", ")}")
                        }
                    }
                } else if (asset.tags.isNotEmpty()) {
                    showCustomTooltip(asset.path.hashCode()) {
                        ImGui.textUnformatted(asset.name)
                        ImGui.separator()
                        ImGui.textDisabled("Tags: ${asset.tags.joinToString(", ")}")
                    }
                }
            }

            // Double-click: Load to the inactive deck (>0% crossfader).
            if (isRowHovered && ImGui.isMouseDoubleClicked(0)) {
                DockActions.doubleClick(session, mixer, parametersState, BrowseKind.SRC, asset, target)
            }

            // Drag source: saved presets carry their file path (ASSET_ITEM) so they can also go
            // into playlists and queues; stock generators have no persisted state, so they use
            // their own payload that only deck drop targets accept.
            if (ImGui.beginDragDropSource()) {
                if (playlistRows != null) {
                    ImGui.setDragDropPayload(PAYLOAD_PLAYLIST_ITEM, playlistRows.indexOfRow(index) as Any)
                    ImGui.textUnformatted(asset.name)
                } else if (isExternal) {
                    ImGui.textUnformatted(asset.name)
                } else if (isStock) {
                    ImGui.setDragDropPayload(PAYLOAD_STOCK_SOURCE, asset.path.removePrefix(STOCK_PATH_PREFIX) as Any)
                    ImGui.textUnformatted(asset.name)
                } else {
                    val inOrder = selection.getSelectedInOrder(filtered).filter { !(it.type == AssetType.SOURCE_STOCK || it.type == AssetType.SOURCE_EXTERNAL) }
                    val targets = if (inOrder.any { it.path == asset.path }) inOrder else listOf(asset)
                    val payload = targets.joinToString("\n") { it.path }
                    ImGui.setDragDropPayload("ASSET_ITEM", payload as Any)
                    if (targets.size > 1) {
                        ImGui.text("Moving ${targets.size} presets")
                    } else {
                        ImGui.textUnformatted(asset.name)
                    }
                }
                ImGui.endDragDropSource()
            }

            if (playlistRows != null) playlistRows.dropTarget(index)

            ImGui.sameLine(0f, 0f)
            BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "preset_$index", btnW)

            // Context menu (triggered by right-click or more button)
            pushOpenDropdownPadding()
            if (ImGui.beginPopup(popupId)) {
                pushOpenDropdownFont()
                if (contextExtras != null) {
                    contextExtras(asset)
                    ImGui.separator()
                }
                if (isStock) {
                    val source = if (isExternal) ExternalVideoSource(serverName = BrowseCatalogs.externalName(asset))
                        else VisualSourceRegistry.availableSources.find { it.id == asset.path.removePrefix(STOCK_PATH_PREFIX) }
                    if (source != null) {
                        if (ImGui.menuItem("Load to Deck A")) {
                            UIManager.changeVisualSourceSafely(mixer, mixer.deckA, "Deck A", source, parametersState)
                        }
                        if (ImGui.menuItem("Load to Deck B")) {
                            UIManager.changeVisualSourceSafely(mixer, mixer.deckB, "Deck B", source, parametersState)
                        }
                        if (ImGui.menuItem("Load to Deck BG")) {
                            UIManager.changeVisualSourceSafely(mixer, mixer.deckBG, "Deck BG", source, parametersState)
                        }
                        if (ImGui.menuItem("Preview on Deck PV")) {
                            UIManager.changeVisualSourceSafely(mixer, mixer.deckPV, "Deck PV", source, parametersState)
                        }
                    }
                } else {
                    val inOrder = selection.getSelectedInOrder(filtered).filter { !(it.type == AssetType.SOURCE_STOCK || it.type == AssetType.SOURCE_EXTERNAL) }
                    val targets = if (inOrder.any { it.path == asset.path }) inOrder else listOf(asset)
                    val count = targets.size

                    if (count > 1) {
                        ImGui.textDisabled("$count Presets Selected")
                        ImGui.separator()
                    }

                    if (count == 1) {
                        if (ImGui.menuItem("Load to Deck A")) {
                            BrowserDeckButtons.loadPresetToDeck(session, mixer, File(asset.path), 1)
                        }
                        if (ImGui.menuItem("Load to Deck B")) {
                            BrowserDeckButtons.loadPresetToDeck(session, mixer, File(asset.path), 2)
                        }
                        if (ImGui.menuItem("Load to Deck BG")) {
                            BrowserDeckButtons.loadPresetToDeck(session, mixer, File(asset.path), 3)
                        }
                        if (ImGui.menuItem("Preview on Deck PV")) {
                            BrowserDeckButtons.loadPresetToDeck(session, mixer, File(asset.path), 4)
                        }
                        ImGui.separator()
                    }
                    val abLabel = if (count > 1) "Add $count Presets to A/B Queue" else "Add to A/B Queue"
                    if (ImGui.menuItem(abLabel)) {
                        targets.forEach { session.playQueueManager.appendToQueue(File(it.path)) }
                    }
                    val bgLabel = if (count > 1) "Add $count Presets to BG Queue" else "Add to BG Queue"
                    if (ImGui.menuItem(bgLabel)) {
                        targets.forEach { llm.slop.liquidlsd.presets.BgQueueManager.appendToQueue(File(it.path)) }
                    }
                    val activePl = LibraryPanel.activePlaylistData
                    if (activePl != null) {
                        val plLabel = if (count > 1) "Add $count Presets to '${activePl.name}'" else "Add to '${activePl.name}'"
                        if (ImGui.menuItem(plLabel)) {
                            targets.forEach { PlaylistManager.insertPreset(activePl, it.path, activePl.presets.size) }
                        }
                    }
                    ImGui.separator()
                    if (count == 1) {
                        if (asset.type == AssetType.PRESET) {
                            if (ImGui.menuItem("Rename / Edit Tags...")) {
                                BrowserPopupHandler.openRenamePresetModal(asset)
                            }
                            if (ImGui.menuItem("Duplicate Preset...")) {
                                BrowserPopupHandler.openDuplicatePresetModal(asset)
                            }
                        } else {
                            if (ImGui.menuItem("Rename")) {
                                BrowserPopupHandler.renameTarget = asset
                                BrowserPopupHandler.renameBuffer.set(asset.name)
                                BrowserPopupHandler.pendingOpenRenamePopup = true
                            }
                            if (ImGui.menuItem("Clone")) {
                                FileSystemManager.cloneFile(asset.path).onSuccess {
                                    LibraryPanel.refreshAssets()
                                }
                            }
                        }
                    }
                    val delLabel = if (count > 1) "Delete $count Presets..." else "Delete"
                    if (ImGui.menuItem(delLabel)) {
                        BrowserPopupHandler.openDeleteConfirmation(targets)
                    }
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()

            ImGui.popID()
                }
        playlistRows?.finish()
    }

    /** Fraction of the row width the name column may use when an info column is drawn: a third, so names get a quarter of the dock and the info (tags, chain contents) half. */
    private const val INFO_COLUMN_START = 1f / 3f

    /** [text] shortened with an ellipsis so it fits in [maxW] pixels in the current font. */
    internal fun elide(text: String, maxW: Float): String {
        if (maxW <= 0f || ImGui.calcTextSize(text).x <= maxW) return text
        var end = text.length
        while (end > 1 && ImGui.calcTextSize(text.substring(0, end) + "...").x > maxW) end--
        return text.substring(0, end).trimEnd() + "..."
    }

    /** Draws the muted info text of a row in the info column, elided to fit. Call right after the row selectable. */
    internal fun drawInfoColumn(info: String, itemW: Float) {
        if (info.isEmpty()) return
        val nameColW = itemW * INFO_COLUMN_START
        val x = ImGui.getItemRectMinX() + nameColW
        val y = ImGui.getItemRectMinY() + (ImGui.getItemRectSizeY() - ImGui.getTextLineHeight()) * 0.5f
        ImGui.getWindowDrawList().addText(x, y, ImGui.getColorU32(ImGuiCol.TextDisabled), elide(info, itemW - nameColW - 6f))
    }

    /** Name text shortened so it stays clear of the info column. */
    internal fun nameForInfo(label: String, itemW: Float): String = elide(label, itemW * INFO_COLUMN_START - 12f)
}
