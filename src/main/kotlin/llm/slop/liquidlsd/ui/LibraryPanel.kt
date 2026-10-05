package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons
import llm.slop.liquidlsd.ui.browser.BrowserPopupHandler
import llm.slop.liquidlsd.ui.browser.FXBgQueueActionsPanel
import llm.slop.liquidlsd.ui.browser.FXBrowserPanel
import llm.slop.liquidlsd.ui.browser.MapsBrowserPanel
import llm.slop.liquidlsd.ui.browser.FXQueueActionsPanel
import llm.slop.liquidlsd.ui.browser.PresetListPanel
import llm.slop.liquidlsd.ui.browser.QueueActionsPanel
import llm.slop.liquidlsd.ui.browser.TransitionBrowserPanel
import llm.slop.liquidlsd.ui.browser.TransitionQueuePanel
import mu.KotlinLogging
import java.io.File

object LibraryPanel {
    private val logger = KotlinLogging.logger {}

    enum class LibraryViewMode {
        PRESETS,
        FX,
        TRANS,
        /** Saved macro banks and Perform pages (see [MapsBrowserPanel]). Has no playlists or queues. */
        MAPS
    }

    enum class SelectionSource {
        /** Unified pane only: the folder/playlist tree (see [llm.slop.liquidlsd.ui.browser.BrowserPane.stepTree]). */
        TREE,
        PRESETS,
        QUEUE_AB,
        QUEUE_BG,
        TRANSITION_QUEUE,
        FX_QUEUE_AB,
        FX_QUEUE_BG
    }

    var viewMode: LibraryViewMode = System.getProperty("lsd.libraryTab")?.let { n -> LibraryViewMode.entries.firstOrNull { it.name.equals(n, true) } } ?: LibraryViewMode.PRESETS

    /** The tab the controller and keyboard act on: the one the Edit bay's hosted pane is showing, else the Library's own [viewMode]. */
    val navMode: LibraryViewMode
        get() = when (llm.slop.liquidlsd.ui.browser.BrowserPane.hosted()?.kind) {
            llm.slop.liquidlsd.ui.browser.BrowseKind.SRC -> LibraryViewMode.PRESETS
            llm.slop.liquidlsd.ui.browser.BrowseKind.FX -> LibraryViewMode.FX
            llm.slop.liquidlsd.ui.browser.BrowseKind.TRANS -> LibraryViewMode.TRANS
            null -> viewMode
        }
    var activeSelectionSource: SelectionSource? = null
    var selectedPlaylistFile: File? = null
    var selectedTransitionPlaylistFile: File? = null
    var selectedFxPlaylistFile: File? = null
    internal var activePlaylistData: PlaylistManager.Playlist? = null

    var shouldReclaimFocus: Boolean = false
    var shouldScrollToSelection: Boolean = false
    /**
     * Edit view: a Rack module is in Deep Edit and the Library isn't FULL, so the Deep Edit bay
     * takes the whole left column and the Library isn't drawn at all. The other two views are
     * Perform (rows + HALF Library) and Library (FULL).
     */
    fun isEditView(session: SessionContext): Boolean =
        session.parametersState.anyRackModuleExpanded() && session.uiTheme.libraryMode != UITheme.LibraryMode.FULL

    /** Leaves Edit view so the Library is on screen: cancels any armed Learn (it pins its module open) and collapses Deep Edit. */
    fun show(session: SessionContext) {
        if (!isEditView(session)) return
        if (llm.slop.liquidlsd.macro.MacroLearnState.isLearning()) llm.slop.liquidlsd.macro.MacroLearnState.cancelLearn()
        session.parametersState.collapseAllRackModules()
    }

    /** Ctrl+F / "/": focuses the search box of the tab currently shown (Sources, FX or Trans). */
    fun focusActiveSearch() {
        LibraryNavigation.unifiedKind()?.let { llm.slop.liquidlsd.ui.browser.BrowserPane.focusSearch(it) }
    }

    /** Library shortcut: from Edit view, brings the Library back (Perform view); otherwise toggles HALF <-> FULL. */
    fun cycleMode(session: SessionContext) {
        if (isEditView(session)) {
            show(session)
            return
        }
        session.uiTheme.libraryMode = if (session.uiTheme.libraryMode == UITheme.LibraryMode.FULL) UITheme.LibraryMode.HALF else UITheme.LibraryMode.FULL
        AppPreferencesStore.savePreferences()
    }

    private var lastKnownSignature: String = ""
    private var lastAutoRefreshTimeMs: Long = 0L

    fun getActiveSelectedFile(session: SessionContext): File? {
        return when (activeSelectionSource) {
            SelectionSource.TREE -> null
            SelectionSource.PRESETS -> {
                when (navMode) {
                    LibraryViewMode.FX -> FXBrowserPanel.selectedAsset?.let { File(it.path) }
                    LibraryViewMode.TRANS -> TransitionBrowserPanel.selectedAsset?.let { TransitionBrowserPanel.fileFor(it) }
                    LibraryViewMode.PRESETS -> PresetListPanel.selectedAsset
                        ?.takeIf { it.type != AssetType.SOURCE_STOCK }
                        ?.let { File(it.path) }
                    LibraryViewMode.MAPS -> null
                }
            }
            SelectionSource.QUEUE_AB -> {
                val idx = QueueActionsPanel.selectedIndex
                if (idx in session.playQueueManager.queue.indices) session.playQueueManager.queue[idx] else null
            }
            SelectionSource.QUEUE_BG -> {
                val idx = llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex
                if (idx in llm.slop.liquidlsd.presets.BgQueueManager.queue.indices) llm.slop.liquidlsd.presets.BgQueueManager.queue[idx] else null
            }
            SelectionSource.TRANSITION_QUEUE -> {
                val idx = TransitionQueuePanel.selectedIndex
                if (idx in TransitionQueueManager.queue.indices) TransitionQueueManager.queue[idx] else null
            }
            SelectionSource.FX_QUEUE_AB -> {
                val idx = FXQueueActionsPanel.selectedIndex
                if (idx in FXQueueManager.queue.indices) FXQueueManager.queue[idx] else null
            }
            SelectionSource.FX_QUEUE_BG -> {
                val idx = FXBgQueueActionsPanel.selectedIndex
                if (idx in FXBgQueueManager.queue.indices) FXBgQueueManager.queue[idx] else null
            }
            null -> null
        }
    }

    fun selectPreset(asset: AssetItem?, session: SessionContext, mixer: Mixer) {
        PresetListPanel.selectedAsset = asset
        if (asset != null) {
            activeSelectionSource = SelectionSource.PRESETS
            QueueActionsPanel.selectedIndex = -1
            llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
            if (asset.type != AssetType.SOURCE_STOCK && llm.slop.liquidlsd.ui.browser.BrowserPane.hosted() == null) {
                auditionIfLocked(File(asset.path), session, mixer)
            }
        }
    }

    fun selectQueueAb(index: Int, session: SessionContext, mixer: Mixer) {
        QueueActionsPanel.selectedIndex = index
        if (index >= 0) {
            activeSelectionSource = SelectionSource.QUEUE_AB
            PresetListPanel.selectedAsset = null
            llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
            val file = session.playQueueManager.queue.getOrNull(index)
            if (file != null) auditionIfLocked(file, session, mixer)
        }
    }

    fun selectQueueBg(index: Int, session: SessionContext, mixer: Mixer) {
        llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = index
        if (index >= 0) {
            activeSelectionSource = SelectionSource.QUEUE_BG
            PresetListPanel.selectedAsset = null
            QueueActionsPanel.selectedIndex = -1
            val file = llm.slop.liquidlsd.presets.BgQueueManager.queue.getOrNull(index)
            if (file != null) auditionIfLocked(file, session, mixer)
        }
    }

    fun clearAllSelection() {
        activeSelectionSource = null
        PresetListPanel.selection.clear()
        FXBrowserPanel.selectedAsset = null
        TransitionBrowserPanel.selectedAsset = null
        TransitionQueuePanel.selectedIndex = -1
        QueueActionsPanel.clearSelection()
        llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.clearSelection()
        FXQueueActionsPanel.selectedIndex = -1
        FXBgQueueActionsPanel.selectedIndex = -1
    }

    fun auditionIfLocked(file: File, session: SessionContext, mixer: Mixer) {
        if (llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.isAuditionLocked) {
            BrowserDeckButtons.loadPresetToDeck(session, mixer, file, 4)
        }
    }

    fun getOrLoadPlaylist(file: File): PlaylistManager.Playlist? {
        val current = activePlaylistData
        if (current != null && current.filePath == file.absolutePath) {
            return current
        }
        PlaylistManager.loadPlaylist(file).onSuccess { playlist ->
            activePlaylistData = playlist
            return playlist
        }
        return null
    }

    private fun checkAutoRefresh() {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshTimeMs > 500L) {
            val root = FileSystemManager.getPresetsRoot()
            val sig = FileSystemManager.getRecursiveDirectorySignature(root)
            if (sig != lastKnownSignature) {
                refreshAssets()
            }
        }
    }

    fun refreshAssets() {
        val root = FileSystemManager.getPresetsRoot()
        lastKnownSignature = FileSystemManager.getRecursiveDirectorySignature(root)
        lastAutoRefreshTimeMs = System.currentTimeMillis()
        FileSystemManager.scanAllPresets()
        FileSystemManager.scanAllPlaylists()
        FileSystemManager.scanAllFxPresets()
        FileSystemManager.scanAllFxChains()
        FileSystemManager.scanAllFxPlaylists()
        FileSystemManager.scanAllTransitionPresets()
        FileSystemManager.scanAllTransitionPlaylists()
    }

    fun draw(session: SessionContext, width: Float, height: Float, mixer: Mixer, parametersState: ParametersState) {
        checkAutoRefresh()
        val safeW = width.coerceAtLeast(80f)

        if (ImGui.beginMenuBar()) {
            val menuBarH = ImGui.getFrameHeight()
            val btnH = 21f
            val bottomSpacing = 2.5f
            val yOffset = (menuBarH - btnH - bottomSpacing).coerceAtLeast(0f)

            // Left Mode Toggle: [ Sources ] / [ FX ] / [ Trans ]
            ImGui.setCursorPosX(8f)
            ImGui.setCursorPosY(yOffset)
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                val btnWMode = 54f
                val btnWModeWide = 64f
                val activeCol = TangoPalette.MODE_ACTIVE.u32()
                val inactiveCol = TangoPalette.MODE_INACTIVE.u32()
                val activeTextCol = TangoPalette.MODE_ACTIVE_TEXT.u32()
                val inactiveTextCol = ImGui.getColorU32(ImGuiCol.Text)

                val isPresets = viewMode == LibraryViewMode.PRESETS
                ImGui.pushStyleColor(ImGuiCol.Button, if (isPresets) activeCol else inactiveCol)
                ImGui.pushStyleColor(ImGuiCol.Text, if (isPresets) activeTextCol else inactiveTextCol)
                if (ImGui.button("Sources##mode_presets", btnWModeWide, btnH)) {
                    LibraryNavigation.setViewMode(LibraryViewMode.PRESETS)
                }
                ImGui.popStyleColor(2)

                ImGui.sameLine(0f, 2f)

                val isFx = viewMode == LibraryViewMode.FX
                ImGui.pushStyleColor(ImGuiCol.Button, if (isFx) activeCol else inactiveCol)
                ImGui.pushStyleColor(ImGuiCol.Text, if (isFx) activeTextCol else inactiveTextCol)
                if (ImGui.button("FX##mode_fx", btnWMode, btnH)) {
                    LibraryNavigation.setViewMode(LibraryViewMode.FX)
                }
                ImGui.popStyleColor(2)

                ImGui.sameLine(0f, 2f)

                val isTrans = viewMode == LibraryViewMode.TRANS
                ImGui.pushStyleColor(ImGuiCol.Button, if (isTrans) activeCol else inactiveCol)
                ImGui.pushStyleColor(ImGuiCol.Text, if (isTrans) activeTextCol else inactiveTextCol)
                if (ImGui.button("Transitions##mode_trans", 82f, btnH)) {
                    LibraryNavigation.setViewMode(LibraryViewMode.TRANS)
                }
                ImGui.popStyleColor(2)

                ImGui.sameLine(0f, 2f)

                val isMaps = viewMode == LibraryViewMode.MAPS
                ImGui.pushStyleColor(ImGuiCol.Button, if (isMaps) activeCol else inactiveCol)
                ImGui.pushStyleColor(ImGuiCol.Text, if (isMaps) activeTextCol else inactiveTextCol)
                if (ImGui.button("Macros##mode_maps", btnWMode, btnH)) {
                    LibraryNavigation.setViewMode(LibraryViewMode.MAPS)
                }
                ImGui.popStyleColor(2)
            }

            // Centered Action Toolbar
            val totalToolbarW = llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.calculateToolbarWidth(btnH)
            val windowBtnW = (btnH * 1.15f).coerceIn(20f, 32f)
            val windowBtnsW = windowBtnW
            val targetCenterX = ((safeW - totalToolbarW) * 0.5f).coerceIn(120f, (safeW - totalToolbarW - windowBtnsW - 8f).coerceAtLeast(120f))

            ImGui.setCursorPosX(targetCenterX)
            ImGui.setCursorPosY(yOffset)

            val selectedFile = getActiveSelectedFile(session)
            llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.draw(
                session = session,
                mixer = mixer,
                parametersState = parametersState,
                selectedFile = selectedFile,
                source = activeSelectionSource,
                btnHeight = btnH
            )

            // Right-aligned Maximize / Restore button
            val rightX = (safeW - windowBtnsW - 8f).coerceAtLeast(ImGui.getCursorPosX() + 8f)
            ImGui.sameLine(0f, 0f)
            ImGui.setCursorPosX(rightX)
            ImGui.setCursorPosY(yOffset)

            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                // Maximize / Restore [□] / [❐]
                val isFull = session.uiTheme.libraryMode == UITheme.LibraryMode.FULL
                val maxIcon = if (isFull) Icons.COPY else Icons.SQUARE
                if (ImGui.button("$maxIcon##lib_max", windowBtnW, btnH)) {
                    cycleMode(session)
                }
                itemTooltip(if (isFull) "Restore Library (Half size)" else "Maximize Library (Full size)")
            }

            ImGui.endMenuBar()
        }

        val contentH = (ImGui.getContentRegionAvailY() - 4f).coerceAtLeast(1f)
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(80f)
        val groupGap = 8f
        val groupW1 = ((availW - groupGap) * 0.5f).coerceAtLeast(40f)
        val groupW2 = (availW - groupW1 - groupGap).coerceAtLeast(40f)

        val outerFlags = imgui.flag.ImGuiWindowFlags.NoScrollbar or imgui.flag.ImGuiWindowFlags.NoScrollWithMouse

        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 6f, 6f)
        val childBg = TangoPalette.PANEL_BG.u32()
        val childBorder = TangoPalette.PANEL_BORDER.u32()
        ImGui.pushStyleColor(ImGuiCol.ChildBg, childBg)
        ImGui.pushStyleColor(ImGuiCol.Border, childBorder)

        val unifiedKind = when (viewMode) {
            LibraryViewMode.PRESETS -> llm.slop.liquidlsd.ui.browser.BrowseKind.SRC
            LibraryViewMode.FX -> llm.slop.liquidlsd.ui.browser.BrowseKind.FX
            LibraryViewMode.TRANS -> llm.slop.liquidlsd.ui.browser.BrowseKind.TRANS
            LibraryViewMode.MAPS -> null
        }
        if (viewMode == LibraryViewMode.MAPS) {
            // Banks and Pages take the whole panel: no playlists or queues.
            ImGui.beginChild("LibraryMaps", availW, contentH, true, outerFlags)
            ImGui.setScrollX(0f)
            MapsBrowserPanel.draw(session, mixer)
            ImGui.endChild()
        } else if (unifiedKind != null) {
            ImGui.beginChild("LibraryUnified", availW, contentH, false, outerFlags)
            llm.slop.liquidlsd.ui.browser.BrowserPane.draw(session, mixer, parametersState, unifiedKind)
            ImGui.endChild()
        }

        ImGui.popStyleColor(2)
        ImGui.popStyleVar(2)

        // Global library keyboard shortcuts dynamically mapped via ShortcutManager
        val activeFile = getActiveSelectedFile(session)
        val io = ImGui.getIO()
        if (!io.wantTextInput) {
            if (viewMode == LibraryViewMode.TRANS) {
                // In TRANS mode: shortcuts Q (enqueue transition), Enter (apply to mixer), Up/Down (nav)
                val isEnter = ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false)
                val isQueueKey = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.queue_ab")
                val isNavUp = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.navigate")
                val isNavDown = !io.keyCtrl && !io.keyAlt && !io.keySuper && !io.keyShift && ImGui.isKeyPressed(ImGuiKey.DownArrow, false)

                if (isEnter && activeFile != null) {
                    TransitionQueueManager.applyTransitionItem(activeFile, mixer)
                    shouldReclaimFocus = true
                } else if (isQueueKey && activeFile != null) {
                    TransitionQueueManager.appendToQueue(activeFile)
                    shouldReclaimFocus = true
                } else if (isNavUp) {
                    navigateSelection(-1, session, mixer)
                } else if (isNavDown) {
                    navigateSelection(1, session, mixer)
                }
            } else {
                val isQueueAB = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.queue_ab")
                val isQueueBG = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.queue_bg")
                val isNavUp = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.navigate")
                val isNavDown = !io.keyCtrl && !io.keyAlt && !io.keySuper && !io.keyShift && ImGui.isKeyPressed(ImGuiKey.DownArrow, false)

                if (isQueueBG && LibraryNavigation.enqueue(session, bg = true)) {
                    shouldReclaimFocus = true
                } else if (isQueueAB && LibraryNavigation.enqueue(session, bg = false)) {
                    shouldReclaimFocus = true
                } else if (isNavUp) {
                    navigateSelection(-1, session, mixer)
                } else if (isNavDown) {
                    navigateSelection(1, session, mixer)
                }
            }
        }

        // Popups. Delete's trigger+draw is handled globally (see BrowserPopupHandler.drawDeleteAssetConfirmationPopup)
        // since it can now also be triggered from the Performance row's Browse content, which stays
        // on screen while this whole Library panel is skipped (Edit view).
        if (BrowserPopupHandler.pendingOpenRenamePopup) {
            ImGui.openPopup("RenameAssetPopup")
            BrowserPopupHandler.pendingOpenRenamePopup = false
        }
        if (BrowserPopupHandler.pendingOpenNewPlaylistPopup) {
            ImGui.openPopup("NewPlaylistPopup")
            BrowserPopupHandler.pendingOpenNewPlaylistPopup = false
        }
        if (BrowserPopupHandler.pendingOpenExportQueuePopup) {
            if (viewMode == LibraryViewMode.TRANS) {
                ImGui.openPopup("ExportTransQueuePopup")
            } else {
                ImGui.openPopup("ExportQueuePopup")
            }
            BrowserPopupHandler.pendingOpenExportQueuePopup = false
        }
        if (BrowserPopupHandler.pendingOpenExportFxQueuePopup) {
            ImGui.openPopup("ExportFxQueuePopup")
            BrowserPopupHandler.pendingOpenExportFxQueuePopup = false
        }
        if (BrowserPopupHandler.pendingOpenExportFxBgQueuePopup) {
            ImGui.openPopup("ExportFxBgQueuePopup")
            BrowserPopupHandler.pendingOpenExportFxBgQueuePopup = false
        }

        BrowserPopupHandler.drawRenameAssetPopup()
        BrowserPopupHandler.drawNewPlaylistPopup()
        BrowserPopupHandler.drawExportQueuePopup(session)
        BrowserPopupHandler.drawExportBgQueuePopup()
        BrowserPopupHandler.drawExportTransQueuePopup()
        BrowserPopupHandler.drawExportFxQueuePopup()
        BrowserPopupHandler.drawExportFxBgQueuePopup()
    }

    /**
     * Clears the one-shot focus/scroll flags. Called by [UIManager] once the whole frame is drawn, not from [draw]: the Edit
     * bay's picker lists are drawn while this panel is skipped, and the Library's own key shortcuts run after its lists.
     */
    fun endFrame() {
        shouldReclaimFocus = false
        shouldScrollToSelection = false
    }

    fun navigateSelection(delta: Int, session: SessionContext, mixer: Mixer) {
        if (navMode == LibraryViewMode.MAPS) return // Maps rows are clicked, not stepped
        when (activeSelectionSource) {
            SelectionSource.TREE -> {
                LibraryNavigation.unifiedKind()?.let { llm.slop.liquidlsd.ui.browser.BrowserPane.stepTree(it, delta) }
                shouldScrollToSelection = true
            }
            SelectionSource.PRESETS -> {
                if (navMode == LibraryViewMode.FX) {
                    val list = FXBrowserPanel.filteredRows
                    if (list.isNotEmpty()) {
                        val currentIdx = list.indexOfFirst { it.path == FXBrowserPanel.selectedAsset?.path }
                        val targetIdx = if (currentIdx < 0) {
                            if (delta > 0) 0 else list.lastIndex
                        } else {
                            (currentIdx + delta).coerceIn(0, list.lastIndex)
                        }
                        if (targetIdx != currentIdx) {
                            FXBrowserPanel.selectedAsset = list[targetIdx]
                            shouldScrollToSelection = true
                            shouldReclaimFocus = true
                        }
                    }
                } else if (navMode == LibraryViewMode.TRANS) {
                    val list = TransitionBrowserPanel.filteredRows
                    if (list.isNotEmpty()) {
                        val currentIdx = list.indexOfFirst { it.path == TransitionBrowserPanel.selectedAsset?.path }
                        val targetIdx = if (currentIdx < 0) {
                            if (delta > 0) 0 else list.lastIndex
                        } else {
                            (currentIdx + delta).coerceIn(0, list.lastIndex)
                        }
                        if (targetIdx != currentIdx) {
                            TransitionBrowserPanel.selectedAsset = list[targetIdx]
                            shouldScrollToSelection = true
                            shouldReclaimFocus = true
                        }
                    }
                } else {
                    val list = PresetListPanel.filteredPresets
                    if (list.isNotEmpty()) {
                        val currentIdx = list.indexOfFirst { it.path == PresetListPanel.selectedAsset?.path }
                        val targetIdx = if (currentIdx < 0) {
                            if (delta > 0) 0 else list.lastIndex
                        } else {
                            (currentIdx + delta).coerceIn(0, list.lastIndex)
                        }
                        if (targetIdx != currentIdx) {
                            selectPreset(list[targetIdx], session, mixer)
                            shouldScrollToSelection = true
                            shouldReclaimFocus = true
                        }
                    }
                }
            }
            SelectionSource.QUEUE_AB -> {
                val queue = session.playQueueManager.queue
                if (queue.isNotEmpty()) {
                    val currentIdx = QueueActionsPanel.selectedIndex
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else queue.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, queue.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        selectQueueAb(targetIdx, session, mixer)
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            SelectionSource.QUEUE_BG -> {
                val queue = llm.slop.liquidlsd.presets.BgQueueManager.queue
                if (queue.isNotEmpty()) {
                    val currentIdx = llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else queue.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, queue.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        selectQueueBg(targetIdx, session, mixer)
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            SelectionSource.TRANSITION_QUEUE -> {
                val queue = TransitionQueueManager.queue
                if (queue.isNotEmpty()) {
                    val currentIdx = TransitionQueuePanel.selectedIndex
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else queue.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, queue.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        TransitionQueuePanel.selectedIndex = targetIdx
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            SelectionSource.FX_QUEUE_AB -> {
                val queue = FXQueueManager.queue
                if (queue.isNotEmpty()) {
                    val currentIdx = FXQueueActionsPanel.selectedIndex
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else queue.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, queue.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        FXQueueActionsPanel.selectedIndex = targetIdx
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            SelectionSource.FX_QUEUE_BG -> {
                val queue = FXBgQueueManager.queue
                if (queue.isNotEmpty()) {
                    val currentIdx = FXBgQueueActionsPanel.selectedIndex
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else queue.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, queue.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        FXBgQueueActionsPanel.selectedIndex = targetIdx
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            null -> {
                if (navMode == LibraryViewMode.TRANS) {
                    val list = TransitionBrowserPanel.filteredRows
                    if (list.isNotEmpty()) {
                        TransitionBrowserPanel.selectedAsset = list.first()
                        activeSelectionSource = SelectionSource.PRESETS
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                } else if (navMode == LibraryViewMode.FX) {
                    val list = FXBrowserPanel.filteredRows
                    if (list.isNotEmpty()) {
                        FXBrowserPanel.selectedAsset = list.first()
                        activeSelectionSource = SelectionSource.PRESETS
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                } else {
                    val list = PresetListPanel.filteredPresets
                    if (list.isNotEmpty()) {
                        selectPreset(list.first(), session, mixer)
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
        }
    }

    fun getSelectedAsset(): AssetItem? {
        return when (navMode) {
            LibraryViewMode.FX -> FXBrowserPanel.selectedAsset
            LibraryViewMode.TRANS -> TransitionBrowserPanel.selectedAsset
            LibraryViewMode.MAPS -> null
            LibraryViewMode.PRESETS -> PresetListPanel.selectedAsset
        }
    }
}
