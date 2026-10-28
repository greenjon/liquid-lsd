package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.SmoothScroll
import llm.slop.liquidlsd.ui.TangoPalette
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
import llm.slop.liquidlsd.ui.DROPDOWN_ROW_HEIGHT
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
     * A left click on a list row: Ctrl/Cmd and Shift build a multi-selection (see [MultiSelectionModel]); bound to a [target],
     * a click applies the row, so it stays a single selection.
     */
    internal fun <T> clickSelect(selection: MultiSelectionModel<T>, item: T, rows: List<T>, target: ApplyTarget?) {
        val io = ImGui.getIO()
        if (target != null) selection.setSingle(item) else selection.handleClick(item, rows, io.keyCtrl || io.keySuper, io.keyShift)
    }

    /** Tour widget key of a list row: `browser_row_<kind>_<stock id or file name without extension>`. */
    internal fun tourRowKey(kind: BrowseKind, asset: AssetItem): String =
        "browser_row_${kind.name.lowercase()}_" + asset.path.substringAfterLast('/').substringBeforeLast('.')

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
        val loaded = LoadedMarks.snapshot(mixer, session.presetManager)
        filtered.forEachIndexed { index, asset ->
            ImGui.pushID(index)

            val isExternal = asset.type == AssetType.SOURCE_EXTERNAL
            val isStock = asset.type == AssetType.SOURCE_STOCK || isExternal
            val deps = if (isStock) null else (asset.dependencies ?: FileSystemManager.getPresetDependencies(File(asset.path)))
            val issues = deps?.getIssues(session) ?: emptyList()
            val hasIssues = issues.isNotEmpty()

            // Stock rows leave the icon slot blank (a box there read as a checkbox); saved presets keep the disc.
            val icon = if (isStock || (hasIssues && asset.isValid)) null else Icons.DISC
            val star = if (favoriteKeys != null && (if (isStock) asset.path.removePrefix(STOCK_PATH_PREFIX) else asset.path) in favoriteKeys) "\u2605 " else ""
            val label = if (hasIssues && asset.isValid) "[!] ${rowName(asset)}" else "$star${rowName(asset)}"
            val isSelected = selection.isSelected(asset)

            val popupId = "preset_context_menu_$index"

            if (isSelected && LibraryPanel.shouldReclaimFocus) {
                ImGui.setKeyboardFocusHere()
            }
            if (isSelected) SmoothScroll.follow(LibraryPanel.shouldScrollToSelection)

            val btnW = 28f
            val availW = ImGui.getContentRegionAvailX()
            val itemW = (availW - btnW - LoadedMarks.GUTTER_W).coerceAtLeast(10f)

            if (target?.isApplied(asset) == true) LoadedMarks.drawBoundFill(availW - btnW, DROPDOWN_ROW_HEIGHT)
            val gutter = LoadedMarks.beginGutter()
            if (hasIssues && !isSelected) {
                ImGui.pushStyleColor(ImGuiCol.Text, TangoPalette.TEXT_ERROR.u32())
            }
            var itemClicked = false
            var nameElided = false
            val infoText = infoFor?.invoke(asset) ?: ""
            session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
                if (rowSelectable("row", icon, label, infoText, asset.gpuCost, isSelected, itemW)) {
                    itemClicked = true
                }
                nameElided = lastNameElided
                llm.slop.liquidlsd.tour.WidgetRegistry.note { tourRowKey(BrowseKind.SRC, asset) }
            }
            LoadedMarks.drawGutter(gutter, loaded.marksFor(asset), DROPDOWN_ROW_HEIGHT)
            val io = ImGui.getIO()
            // Keyboard nav also "clicks" the focused row on Enter; Enter's row action is BrowserDock's, so that click is dropped here.
            if (itemClicked && (ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false))) itemClicked = false
            if (itemClicked) {
                if (io.keyAlt) selection.setSingle(asset) else clickSelect(selection, asset, filtered, target)
                LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
                QueueActionsPanel.clearSelection()
                llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
                // Alt+click previews on Deck PV (the menu's "Preview on Deck PV"), bound or not; a plain click applies to a bound target.
                if (io.keyAlt) previewOnPv(session, mixer, parametersState, asset) else DockActions.tap(target, asset)
            }
            if (hasIssues && !isSelected) {
                ImGui.popStyleColor()
            }
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
                } else {
                    val thumb = if (isStock) 0 else llm.slop.liquidlsd.presets.GardenThumbnails.textureFor(asset.path)
                    val heavy = asset.gpuCost >= GPU_COST_HEAVY
                    showCustomTooltip(asset.path.hashCode(), estimatedHeight = if (thumb != 0) 240f else if (heavy) 150f else 110f) {
                        ImGui.textUnformatted(fullNameTip(asset, nameElided) ?: rowName(asset))
                        ImGui.separator()
                        if (heavy) ImGui.textUnformatted("${Icons.GAUGE} $GPU_COST_TIP")
                        if (isStock) llm.slop.liquidlsd.rendering.VisualSourceRegistry.compileErrors[asset.path.removePrefix(STOCK_PATH_PREFIX)]?.let { err ->
                            ImGui.pushStyleColor(ImGuiCol.Text, TangoPalette.u32(TangoPalette.ALERT.normal))
                            ImGui.textUnformatted("${Icons.ALERT} This shader failed to compile and shows a red checkerboard. Fix it and restart the app.")
                            ImGui.popStyleColor()
                            ImGui.textDisabled(err)
                        }
                        asset.sourceId?.let { id ->
                            val source = BrowseCatalogs.sourceLabel(id)
                            if (source != null) ImGui.textDisabled("Plays: $source")
                            else {
                                ImGui.pushStyleColor(ImGuiCol.Text, TangoPalette.u32(TangoPalette.ALERT.normal))
                                ImGui.textUnformatted("${Icons.ALERT} Needs source '$id', which isn't installed or failed to load, so it won't load.")
                                ImGui.popStyleColor()
                            }
                        }
                        if (thumb != 0) ImGui.image(thumb.toLong(), llm.slop.liquidlsd.presets.GardenThumbnails.WIDTH.toFloat(), llm.slop.liquidlsd.presets.GardenThumbnails.HEIGHT.toFloat())
                        if (asset.tags.isNotEmpty()) ImGui.textDisabled("Tags: ${asset.tags.joinToString(", ")}")
                        ImGui.textDisabled(if (target != null) "Click: apply. Alt+click: preview on Deck PV." else "Double-click or Enter: load. Alt+click: preview on Deck PV. Ctrl/Shift+click: select several.")
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
                    // Same payload as a stock generator; the deck drop targets resolve the ext_video: id (see [sourceForPayload]).
                    ImGui.setDragDropPayload(PAYLOAD_STOCK_SOURCE, "$EXTERNAL_PAYLOAD_PREFIX${BrowseCatalogs.externalName(asset)}" as Any)
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
                BrowserPane.applyMenuItem(target, asset)
                if (contextExtras != null) {
                    contextExtras(asset)
                    ImGui.separator()
                }
                if (isStock) {
                    val source = stockSourceOf(asset)
                    if (source != null) {
                        val decks = listOf(
                            Triple("Load to Deck A", mixer.deckA, "Deck A"), Triple("Load to Deck B", mixer.deckB, "Deck B"),
                            Triple("Load to Deck BG", mixer.deckBG, "Deck BG"), Triple("Preview on Deck PV", mixer.deckPV, "Deck PV")
                        )
                        for ((label, deck, deckLabel) in decks) {
                            if (ImGui.menuItem(label)) {
                                UIManager.changeVisualSourceSafely(mixer, deck, deckLabel, source, parametersState)
                                BrowseRecents.note(asset)
                            }
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
                        for ((label, deckIndex) in listOf("Load to Deck A" to 1, "Load to Deck B" to 2, "Load to Deck BG" to 3, "Preview on Deck PV" to 4)) {
                            if (ImGui.menuItem(label)) {
                                BrowserDeckButtons.loadPresetToDeck(session, mixer, File(asset.path), deckIndex)
                                BrowseRecents.note(asset)
                            }
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
                            // Same words as the FX and Transition menus; the preset modal also edits tags.
                            if (ImGui.menuItem("Rename...")) {
                                BrowserPopupHandler.openRenamePresetModal(asset)
                            }
                            if (ImGui.menuItem("Clone...")) {
                                BrowserPopupHandler.openDuplicatePresetModal(asset)
                            }
                        } else {
                            if (ImGui.menuItem("Rename...")) BrowserPopupHandler.openRename(asset)
                            if (ImGui.menuItem("Clone")) {
                                FileSystemManager.cloneFile(asset.path).onSuccess {
                                    LibraryPanel.refreshAssets()
                                }
                            }
                        }
                    }
                    val delLabel = if (count > 1) "Delete $count Presets..." else "Delete..."
                    if (ImGui.menuItem(delLabel)) {
                        BrowserPopupHandler.openDeleteConfirmation(targets)
                    }
                    if (count == 1) {
                        ImGui.separator()
                        if (ImGui.menuItem("Reveal in File Manager")) BrowserPopupHandler.revealInFileManager(File(asset.path))
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

    /** Fraction of the row width (after the loaded-marks gutter) the name column takes when an info column is drawn; the info (tags, chain contents) gets the rest. */
    private const val INFO_COLUMN_START = 1f / 3f

    /** [text] shortened with an ellipsis so it fits in [maxW] pixels in the current font. */
    internal fun elide(text: String, maxW: Float): String {
        if (ImGui.calcTextSize(text).x <= maxW) return text
        if (maxW <= 0f) return ""
        var end = text.length
        while (end > 1 && ImGui.calcTextSize(text.substring(0, end) + "...").x > maxW) end--
        return text.substring(0, end).trimEnd() + "..."
    }

    /** Prefix of a [PAYLOAD_STOCK_SOURCE] that names a live external stream rather than a stock generator id. */
    const val EXTERNAL_PAYLOAD_PREFIX = "ext_video:"

    /** The source a [PAYLOAD_STOCK_SOURCE] drop names: a stock generator id, or `ext_video:<stream>` for a live external stream. */
    fun sourceForPayload(id: String): llm.slop.liquidlsd.rendering.VisualSource? =
        if (id.startsWith(EXTERNAL_PAYLOAD_PREFIX)) ExternalVideoSource(serverName = id.removePrefix(EXTERNAL_PAYLOAD_PREFIX))
        else VisualSourceRegistry.availableSources.find { it.id == id }

    /** The source behind a stock or external row. */
    private fun stockSourceOf(asset: AssetItem): llm.slop.liquidlsd.rendering.VisualSource? =
        if (asset.type == AssetType.SOURCE_EXTERNAL) ExternalVideoSource(serverName = BrowseCatalogs.externalName(asset))
        else VisualSourceRegistry.availableSources.find { it.id == asset.path.removePrefix(STOCK_PATH_PREFIX) }

    /** Alt+click: the row on Deck PV, exactly like the menu's "Preview on Deck PV". */
    private fun previewOnPv(session: SessionContext, mixer: Mixer, parametersState: ParametersState, asset: AssetItem) {
        if (asset.type == AssetType.PRESET) {
            BrowserDeckButtons.loadPresetToDeck(session, mixer, File(asset.path), 4)
        } else {
            val source = stockSourceOf(asset) ?: return
            UIManager.changeVisualSourceSafely(mixer, mixer.deckPV, "Deck PV", source, parametersState)
        }
        BrowseRecents.note(asset)
    }

    const val GPU_COST_HEAVY = 3
    const val GPU_COST_TIP = "Heavy shader: GPU-intensive, may run slowly on integrated graphics."

    /** The name a list row shows for [asset]: saved files written in snake_case read as words; stock names are already display names. */
    internal fun rowName(asset: AssetItem): String = when (asset.type) {
        AssetType.PRESET, AssetType.FX_PRESET, AssetType.FX_CHAIN, AssetType.TRANSITION_PRESET -> BrowseCatalog.prettyName(asset.displayName)
        else -> asset.displayName
    }

    /** Hover text naming a row: its full shown name, plus the file name when the shown name was prettified. Null when the row shows it all already. */
    internal fun fullNameTip(asset: AssetItem, nameElided: Boolean): String? {
        val shown = rowName(asset)
        return when {
            shown != asset.displayName -> "$shown\n${File(asset.path).name}"
            nameElided -> shown
            else -> null
        }
    }

    /**
     * One list row: a selectable [itemW] wide with its text drawn on top, so every part stays in its own column whatever the window
     * width. An icon slot (left blank when [icon] is null, so names line up), the [name] shortened to the name column (a third of
     * the row when there is [info], else up to the badge), the muted [info] column, and the heavy-GPU badge for [gpuCost] 3.
     * Draw inside the row font. Returns whether the selectable was clicked; [lastNameElided] tells whether the name was shortened.
     */
    internal fun rowSelectable(id: String, icon: String?, name: String, info: String, gpuCost: Int, isSelected: Boolean, itemW: Float): Boolean {
        val textX = ImGui.getCursorScreenPosX()
        val textCol = if (isSelected) TangoPalette.u32(floatArrayOf(1f, 1f, 1f, 1f)) else ImGui.getColorU32(ImGuiCol.Text)
        val clicked = selectableRow("##$id", isSelected, itemW)
        val rowMaxX = ImGui.getItemRectMaxX()
        val y = ImGui.getItemRectMinY() + (ImGui.getItemRectSizeY() - ImGui.getTextLineHeight()) * 0.5f
        val dl = ImGui.getWindowDrawList()

        val heavy = gpuCost >= GPU_COST_HEAVY
        val badgeW = if (heavy) ImGui.calcTextSize(Icons.GAUGE).x + 6f else 0f
        if (heavy) dl.addText(rowMaxX - badgeW, y, ImGui.getColorU32(ImGuiCol.TextDisabled), Icons.GAUGE)

        val iconSlot = ImGui.calcTextSize(Icons.DISC).x + 6f
        if (icon != null) dl.addText(textX, y, textCol, icon)
        val nameX = textX + iconSlot
        val infoX = textX + itemW * INFO_COLUMN_START
        val nameEnd = if (info.isNotEmpty()) infoX - 10f else rowMaxX - badgeW - 6f
        val shown = elide(name, nameEnd - nameX)
        lastNameElided = shown != name
        dl.addText(nameX, y, textCol, shown)
        if (info.isNotEmpty()) {
            val infoStart = maxOf(infoX, nameX + ImGui.calcTextSize(shown).x + 10f)
            val infoText = elide(info, rowMaxX - badgeW - 6f - infoStart)
            if (infoText.isNotEmpty()) dl.addText(infoStart, y, ImGui.getColorU32(ImGuiCol.TextDisabled), infoText)
        }
        return clicked
    }

    /** Whether the last [rowSelectable] had to shorten its name. */
    internal var lastNameElided = false
        private set
}
