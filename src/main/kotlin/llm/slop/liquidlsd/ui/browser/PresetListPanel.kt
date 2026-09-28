package llm.slop.liquidlsd.ui.browser

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
     *  Deck" -- never "Add to Playlist"/"Add to Live Queue" (those are reserved for
     *  saved presets, which have reproducible state). */
    const val STOCK_PATH_PREFIX = "stock-source://"
    const val PAYLOAD_STOCK_SOURCE = "ASSET_ITEM_STOCK_SOURCE"

    val searchBuffer = ImString(256)
    val selection = MultiSelectionModel<AssetItem>()
    var selectedAsset: AssetItem?
        get() = selection.leadItem
        set(value) {
            selection.setSingle(value)
        }
    var shouldFocusSearch: Boolean = false
    var filteredPresets: List<AssetItem> = emptyList()

    var showStock = true
    var showSaved = true
    private val showStockRef = ImBoolean(true)
    private val showSavedRef = ImBoolean(true)

    private var lastQuery: String = ""
    private var lastAllPresets: List<AssetItem>? = null
    private var lastStock: List<llm.slop.liquidlsd.rendering.VisualSource>? = null
    private var lastFilterState: List<Boolean> = emptyList()
    private var cachedFiltered: List<AssetItem> = emptyList()

    fun draw(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        val btnSize = ImGui.getFrameHeight()

        // Title Bar: "Sources" on the left, [+] and [...] buttons on the right
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.text("Sources")
        }
        ImGui.sameLine()
        val totalButtonsWidth = btnSize * 2f + ImGui.getStyle().getItemSpacingX()
        val rightX = ImGui.getWindowContentRegionMaxX() - totalButtonsWidth
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(rightX)
        }

        // [ + ] Create New Preset dropdown button
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.PLUS}##preset_new_preset", btnSize, btnSize)) {
                ImGui.openPopup("create_new_preset_popup")
            }
        }
        itemTooltip("Create new preset on a deck...")

        pushOpenDropdownPadding()
        if (ImGui.beginPopup("create_new_preset_popup")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Create new preset on:")
            ImGui.separator()
            if (ImGui.menuItem("Deck A")) {
                UIManager.newPresetSafely(mixer, mixer.deckA)
                parametersState.activeTopTab = "Deck A"
            }
            if (ImGui.menuItem("Deck B")) {
                UIManager.newPresetSafely(mixer, mixer.deckB)
                parametersState.activeTopTab = "Deck B"
            }
            if (ImGui.menuItem("Deck BG")) {
                UIManager.newPresetSafely(mixer, mixer.deckBG)
                parametersState.activeTopTab = "Deck BG"
            }
            if (ImGui.menuItem("Deck PV")) {
                UIManager.newPresetSafely(mixer, mixer.deckPV)
                parametersState.activeTopTab = "Deck PV"
            }
            ImGui.separator()
            if (ImGui.menuItem("Restore Factory Presets")) {
                FileSystemManager.restoreFactoryPresets()
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()

        ImGui.sameLine()

        // [...] tier filter kebab
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.MORE_VERTICAL}##preset_browser_filter", btnSize, btnSize)) {
                ImGui.openPopup("preset_browser_tier_filter")
            }
        }
        itemTooltip("Filter Sources list by type.")
        pushOpenDropdownPadding()
        if (ImGui.beginPopup("preset_browser_tier_filter")) {
            pushOpenDropdownFont()
            showStockRef.set(showStock)
            if (ImGui.checkbox("Stock Sources", showStockRef)) showStock = showStockRef.get()
            showSavedRef.set(showSaved)
            if (ImGui.checkbox("Saved Presets", showSavedRef)) showSaved = showSavedRef.get()
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()

        ImGui.separator()
        ImGui.spacing()

        // Search Filter Bar (Full width)
        val searchWidth = ImGui.getContentRegionAvailX()
        ImGui.setNextItemWidth(searchWidth)
        if (shouldFocusSearch) {
            ImGui.setKeyboardFocusHere()
            shouldFocusSearch = false
        }
        ImGui.inputTextWithHint("##presetSearch", "Search sources, presets & tags... (Ctrl+F)", searchBuffer)
        if (ImGui.isItemActive()) {
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                searchBuffer.set("")
                LibraryPanel.shouldReclaimFocus = true
            }
        }
        itemTooltip("Type to filter presets by name or tags.\nPress Esc while searching to clear.")

        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##presets_scroll", 0f, 0f, false)) {
            // Stock generators (VisualSourceRegistry) and saved presets (FileSystemManager) are
            // merged into one filterable list, mirroring FXBrowserPanel/TransitionBrowserPanel.
            val stock = VisualSourceRegistry.availableSources
            val allPresets = FileSystemManager.scanAllPresets()
            val query = searchBuffer.get().trim().lowercase()
            val filterState = listOf(showStock, showSaved)

            val filtered = if (stock === lastStock && allPresets === lastAllPresets &&
                query == lastQuery && filterState == lastFilterState) {
                cachedFiltered
            } else {
                lastStock = stock
                lastAllPresets = allPresets
                lastQuery = query
                lastFilterState = filterState
                val res = mutableListOf<AssetItem>()
                if (showStock) {
                    stock
                        .filter { query.isEmpty() || it.displayName.lowercase().contains(query) || it.categories.any { c -> c.lowercase().contains(query) } }
                        .sortedBy { it.displayName.lowercase() }
                        .forEach { res.add(AssetItem(path = STOCK_PATH_PREFIX + it.id, name = it.displayName, type = AssetType.SOURCE_STOCK, tags = it.categories)) }
                }
                if (showSaved) {
                    allPresets
                        .filter { query.isEmpty() || it.name.lowercase().contains(query) || it.tags.any { t -> t.lowercase().contains(query) } }
                        .forEach { res.add(it) }
                }
                cachedFiltered = res
                res
            }
            filteredPresets = filtered

            if (filtered.isEmpty()) {
                ImGui.textDisabled(if (query.isEmpty()) "No presets found" else "No matching presets")
                if (allPresets.isEmpty()) {
                    ImGui.spacing()
                    if (ImGui.button("${Icons.REFRESH} Restore Factory Presets")) {
                        FileSystemManager.restoreFactoryPresets()
                    }
                    itemTooltip("Re-extract bundled factory presets into library/presets")
                }
            } else {
                filtered.forEachIndexed { index, asset ->
            ImGui.pushID(index)

            val isStock = asset.type == AssetType.SOURCE_STOCK
            val deps = if (isStock) null else (asset.dependencies ?: FileSystemManager.getPresetDependencies(File(asset.path)))
            val issues = deps?.getIssues(session) ?: emptyList()
            val hasIssues = issues.isNotEmpty()

            val icon = if (isStock) Icons.SQUARE else Icons.DISC
            val label = if (hasIssues && asset.isValid) "[!] ${asset.name}" else "$icon ${asset.displayName}"
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
            session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) {
                if (selectableRow(label, isSelected, itemW)) {
                    itemClicked = true
                }
            }
            val io = ImGui.getIO()
            if (itemClicked) {
                val isCtrl = io.keyCtrl || io.keySuper
                val isShift = io.keyShift
                selection.handleClick(asset, filtered, isCtrl, isShift)
                LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
                PlaylistEditorPanel.clearSelection()
                QueueActionsPanel.clearSelection()
                llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
                if (asset.type != AssetType.SOURCE_STOCK) {
                    LibraryPanel.auditionIfLocked(File(asset.path), session, mixer)
                }
            }
            if (hasIssues && !isSelected) {
                ImGui.popStyleColor()
            }
            val isRowHovered = ImGui.isItemHovered()
            if (ImGui.isItemClicked(1)) {
                if (!selection.isSelected(asset)) {
                    selection.setSingle(asset)
                    LibraryPanel.activeSelectionSource = LibraryPanel.SelectionSource.PRESETS
                    PlaylistEditorPanel.clearSelection()
                    QueueActionsPanel.clearSelection()
                    llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
                }
                ImGui.openPopup(popupId)
            }

            val isWindowFocused = ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows)
            val canAutoSelect = isWindowFocused && (LibraryPanel.activeSelectionSource == null || LibraryPanel.activeSelectionSource == LibraryPanel.SelectionSource.PRESETS)
            if (canAutoSelect && ImGui.isItemFocused() && !isSelected && !io.wantTextInput && !io.keyCtrl && !io.keyShift && !io.keySuper) {
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
                val targetIsA = mixer.crossfade.value > 0.0f
                val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB
                val targetLabel = if (targetIsA) "Deck A" else "Deck B"
                if (isStock) {
                    val source = VisualSourceRegistry.availableSources.find { it.id == asset.path.removePrefix(STOCK_PATH_PREFIX) }
                    if (source != null) {
                        logger.info { "Loading stock generator ${asset.name} to inactive deck $targetLabel" }
                        UIManager.changeVisualSourceSafely(mixer, targetDeck, targetLabel, source, parametersState)
                    }
                } else {
                    logger.info { "Loading preset ${asset.name} to inactive deck $targetLabel" }
                    UIManager.loadDeckPresetSafely(mixer, targetDeck, File(asset.path))
                }
            }

            // Drag source: saved presets carry their file path (ASSET_ITEM) so they can also go
            // into playlists and queues; stock generators have no persisted state, so they use
            // their own payload that only deck drop targets accept.
            if (ImGui.beginDragDropSource()) {
                if (isStock) {
                    ImGui.setDragDropPayload(PAYLOAD_STOCK_SOURCE, asset.path.removePrefix(STOCK_PATH_PREFIX) as Any)
                    ImGui.textUnformatted(asset.name)
                } else {
                    val inOrder = selection.getSelectedInOrder(filtered).filter { it.type != AssetType.SOURCE_STOCK }
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

            ImGui.sameLine(0f, 0f)
            BrowserRowMoreButton.draw(popupId, isRowHovered, isSelected, "preset_$index", btnW)

            // Context menu (triggered by right-click or more button)
            pushOpenDropdownPadding()
            if (ImGui.beginPopup(popupId)) {
                pushOpenDropdownFont()
                if (isStock) {
                    val source = VisualSourceRegistry.availableSources.find { it.id == asset.path.removePrefix(STOCK_PATH_PREFIX) }
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
                    val inOrder = selection.getSelectedInOrder(filtered).filter { it.type != AssetType.SOURCE_STOCK }
                    val targets = if (inOrder.any { it.path == asset.path }) inOrder else listOf(asset)
                    val count = targets.size

                    if (count > 1) {
                        ImGui.textDisabled("$count Presets Selected")
                        ImGui.separator()
                    }

                    if (count == 1) {
                        if (ImGui.menuItem("Load to Deck A")) {
                            session.presetRepository.loadDeckPresetAsync(File(asset.path), isDeckA = true)
                        }
                        if (ImGui.menuItem("Load to Deck B")) {
                            session.presetRepository.loadDeckPresetAsync(File(asset.path), isDeckA = false, isDeckBG = false, isDeckPV = false)
                        }
                        if (ImGui.menuItem("Load to Deck BG")) {
                            session.presetRepository.loadDeckPresetAsync(File(asset.path), isDeckBG = true)
                        }
                        if (ImGui.menuItem("Preview on Deck PV")) {
                            session.presetRepository.loadDeckPresetAsync(File(asset.path), isDeckPV = true)
                        }
                        ImGui.separator()
                    }
                    val abLabel = if (count > 1) "Add $count Presets to A/B Queue" else "Add to A/B Queue"
                    if (ImGui.menuItem(abLabel)) {
                        targets.forEach { session.playQueueManager.appendToQueue(File(it.path)) }
                    }
                    val bgLabel = if (count > 1) "Add $count Presets to Background Queue" else "Add to Background Queue"
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
            }
        }
        ImGui.endChild()

        // Keyboard shortcuts (Delete / Backspace deletes selected asset(s) with confirmation)
        val io = ImGui.getIO()
        val targetsToDelete = selection.getSelectedInOrder(filteredPresets).filter { it.type != AssetType.SOURCE_STOCK }
        if (targetsToDelete.isNotEmpty() && !io.wantTextInput && !io.keyCtrl && !io.keyAlt && !io.keySuper) {
            if (ImGui.isKeyPressed(ImGuiKey.Delete, false) ||
                ImGui.isKeyPressed(ImGuiKey.Backspace, false)) {
                BrowserPopupHandler.openDeleteConfirmation(targetsToDelete)
            }
        }
    }
}
