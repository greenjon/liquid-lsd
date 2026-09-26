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
import llm.slop.liquidlsd.ui.browser.FXPlaylistEditorPanel
import llm.slop.liquidlsd.ui.browser.FXQueueActionsPanel
import llm.slop.liquidlsd.ui.browser.PlaylistEditorPanel
import llm.slop.liquidlsd.ui.browser.PresetListPanel
import llm.slop.liquidlsd.ui.browser.QueueActionsPanel
import llm.slop.liquidlsd.ui.browser.StockTransitionListPanel
import llm.slop.liquidlsd.ui.browser.TransitionPlaylistEditorPanel
import llm.slop.liquidlsd.ui.browser.TransitionPresetListPanel
import llm.slop.liquidlsd.ui.browser.TransitionQueuePanel
import mu.KotlinLogging
import java.io.File

object LibraryPanel {
    private val logger = KotlinLogging.logger {}

    enum class LibraryViewMode {
        PRESETS,
        FX,
        TRANS
    }

    enum class SelectionSource {
        PRESETS,
        PLAYLIST,
        QUEUE_AB,
        QUEUE_BG,
        STOCK_TRANSITIONS,
        TRANSITION_PRESETS,
        TRANSITION_PLAYLIST,
        TRANSITION_QUEUE,
        FX_PLAYLIST,
        FX_QUEUE_AB,
        FX_QUEUE_BG
    }

    var viewMode: LibraryViewMode = LibraryViewMode.PRESETS
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
            SelectionSource.PRESETS -> {
                if (viewMode == LibraryViewMode.FX) {
                    FXBrowserPanel.selectedAsset?.let { File(it.path) }
                } else {
                    PresetListPanel.selectedAsset?.let { File(it.path) }
                }
            }
            SelectionSource.PLAYLIST -> {
                PlaylistEditorPanel.getSelectedPresetFile()
            }
            SelectionSource.QUEUE_AB -> {
                val idx = QueueActionsPanel.selectedIndex
                if (idx in session.playQueueManager.queue.indices) session.playQueueManager.queue[idx] else null
            }
            SelectionSource.QUEUE_BG -> {
                val idx = llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex
                if (idx in llm.slop.liquidlsd.presets.BgQueueManager.queue.indices) llm.slop.liquidlsd.presets.BgQueueManager.queue[idx] else null
            }
            SelectionSource.STOCK_TRANSITIONS -> {
                StockTransitionListPanel.selectedTransitionId?.let { id ->
                    val trans = llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.availableTransitions.find { it.id == id }
                    trans?.baseDir?.let { File(it, "$id.fs") } ?: File(id)
                }
            }
            SelectionSource.TRANSITION_PRESETS -> {
                TransitionPresetListPanel.selectedAsset?.let { File(it.path) }
            }
            SelectionSource.TRANSITION_PLAYLIST -> {
                TransitionPlaylistEditorPanel.getSelectedPresetFile()
            }
            SelectionSource.TRANSITION_QUEUE -> {
                val idx = TransitionQueuePanel.selectedIndex
                if (idx in TransitionQueueManager.queue.indices) TransitionQueueManager.queue[idx] else null
            }
            SelectionSource.FX_PLAYLIST -> {
                FXPlaylistEditorPanel.getSelectedPresetFile()
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
            PlaylistEditorPanel.selectedPresetIndex = -1
            QueueActionsPanel.selectedIndex = -1
            llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
            auditionIfLocked(File(asset.path), session, mixer)
        }
    }

    fun selectPlaylistPreset(index: Int, session: SessionContext, mixer: Mixer) {
        PlaylistEditorPanel.selectedPresetIndex = index
        if (index >= 0) {
            activeSelectionSource = SelectionSource.PLAYLIST
            PresetListPanel.selectedAsset = null
            QueueActionsPanel.selectedIndex = -1
            llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
            val file = PlaylistEditorPanel.getSelectedPresetFile()
            if (file != null) auditionIfLocked(file, session, mixer)
        }
    }

    fun selectQueueAb(index: Int, session: SessionContext, mixer: Mixer) {
        QueueActionsPanel.selectedIndex = index
        if (index >= 0) {
            activeSelectionSource = SelectionSource.QUEUE_AB
            PresetListPanel.selectedAsset = null
            PlaylistEditorPanel.selectedPresetIndex = -1
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
            PlaylistEditorPanel.selectedPresetIndex = -1
            QueueActionsPanel.selectedIndex = -1
            val file = llm.slop.liquidlsd.presets.BgQueueManager.queue.getOrNull(index)
            if (file != null) auditionIfLocked(file, session, mixer)
        }
    }

    fun clearAllSelection() {
        activeSelectionSource = null
        PresetListPanel.selectedAsset = null
        FXBrowserPanel.selectedAsset = null
        StockTransitionListPanel.selectedTransitionId = null
        TransitionPresetListPanel.selectedAsset = null
        TransitionPlaylistEditorPanel.selectedItemIndex = -1
        TransitionQueuePanel.selectedIndex = -1
        PlaylistEditorPanel.selectedPresetIndex = -1
        QueueActionsPanel.selectedIndex = -1
        llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
        FXPlaylistEditorPanel.selectedItemIndex = -1
        FXQueueActionsPanel.selectedIndex = -1
        FXBgQueueActionsPanel.selectedIndex = -1
    }

    fun auditionIfLocked(file: File, session: SessionContext, mixer: Mixer) {
        if (llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.isAuditionLocked) {
            val target = llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.latchedDeckTarget ?: return
            BrowserDeckButtons.loadPresetToDeck(session, mixer, file, target.deckIndex)
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

            // Left Mode Toggle: [ Presets ] / [ FX ] / [ Trans ]
            ImGui.setCursorPosX(8f)
            ImGui.setCursorPosY(yOffset)
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                val btnWMode = 54f
                val activeCol = ImGui.colorConvertFloat4ToU32(0.25f, 0.45f, 0.75f, 0.8f)
                val inactiveCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 0.8f)

                val isPresets = viewMode == LibraryViewMode.PRESETS
                ImGui.pushStyleColor(ImGuiCol.Button, if (isPresets) activeCol else inactiveCol)
                if (ImGui.button("Presets##mode_presets", btnWMode, btnH)) {
                    viewMode = LibraryViewMode.PRESETS
                }
                ImGui.popStyleColor()

                ImGui.sameLine(0f, 2f)

                val isFx = viewMode == LibraryViewMode.FX
                ImGui.pushStyleColor(ImGuiCol.Button, if (isFx) activeCol else inactiveCol)
                if (ImGui.button("FX##mode_fx", btnWMode, btnH)) {
                    viewMode = LibraryViewMode.FX
                }
                ImGui.popStyleColor()

                ImGui.sameLine(0f, 2f)

                val isTrans = viewMode == LibraryViewMode.TRANS
                ImGui.pushStyleColor(ImGuiCol.Button, if (isTrans) activeCol else inactiveCol)
                if (ImGui.button("Trans##mode_trans", btnWMode, btnH)) {
                    viewMode = LibraryViewMode.TRANS
                }
                ImGui.popStyleColor()
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
        ImGui.pushStyleColor(ImGuiCol.ChildBg, ImGui.colorConvertFloat4ToU32(0.10f, 0.10f, 0.12f, 0.6f))
        ImGui.pushStyleColor(ImGuiCol.Border, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.28f, 0.8f))

        // Group 1 Box: Col 1 & Col 2
        ImGui.beginChild("LibraryGroup1", groupW1, contentH, true, outerFlags)
        val g1AvailW = ImGui.getContentRegionAvailX().coerceAtLeast(20f)
        val g1AvailH = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
        val colGap = 6f
        val c1W = ((g1AvailW - colGap) * 0.5f).coerceAtLeast(10f)
        val c2W = (g1AvailW - c1W - colGap).coerceAtLeast(10f)

        when (viewMode) {
            LibraryViewMode.PRESETS -> {
                // Column 1: Presets Library
                ImGui.beginChild("LibraryPresetsList", c1W, g1AvailH, false, outerFlags)
                ImGui.setScrollX(0f)
                PresetListPanel.draw(session, mixer, parametersState)
                ImGui.endChild()

                ImGui.sameLine(0f, colGap)

                // Column 2: Playlist Editor
                ImGui.beginChild("LibraryPlaylistEditor", c2W, g1AvailH, false, outerFlags)
                ImGui.setScrollX(0f)
                PlaylistEditorPanel.draw(session, mixer)
                ImGui.endChild()
            }
            LibraryViewMode.FX -> {
                // Column 1: unified FX browser (stock filters, saved singles, saved chains)
                ImGui.beginChild("LibraryFXBrowser", c1W, g1AvailH, false, outerFlags)
                ImGui.setScrollX(0f)
                FXBrowserPanel.draw(session, mixer)
                ImGui.endChild()

                ImGui.sameLine(0f, colGap)

                // Column 2: FX Playlists Editor
                ImGui.beginChild("LibraryFXPlaylists", c2W, g1AvailH, false, outerFlags)
                ImGui.setScrollX(0f)
                FXPlaylistEditorPanel.draw(session, mixer)
                ImGui.endChild()
            }
            LibraryViewMode.TRANS -> {
                // Column 1: Stock Transitions List
                ImGui.beginChild("LibraryStockTransitionsList", c1W, g1AvailH, false, outerFlags)
                ImGui.setScrollX(0f)
                StockTransitionListPanel.draw(session, mixer)
                ImGui.endChild()

                ImGui.sameLine(0f, colGap)

                // Column 2: Transition Presets List
                ImGui.beginChild("LibraryTransitionPresetsList", c2W, g1AvailH, false, outerFlags)
                ImGui.setScrollX(0f)
                TransitionPresetListPanel.draw(session, mixer, parametersState)
                ImGui.endChild()
            }
        }
        ImGui.endChild()

        ImGui.sameLine(0f, groupGap)

        // Group 2 Box: Col 3 & Col 4
        ImGui.beginChild("LibraryGroup2", groupW2, contentH, true, outerFlags)
        val g2AvailW = ImGui.getContentRegionAvailX().coerceAtLeast(20f)
        val g2AvailH = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
        val c3W = ((g2AvailW - colGap) * 0.5f).coerceAtLeast(10f)
        val c4W = (g2AvailW - c3W - colGap).coerceAtLeast(10f)

        if (viewMode == LibraryViewMode.TRANS) {
            // Column 3: Transition Playlists Editor
            ImGui.beginChild("LibraryTransitionPlaylists", c3W, g2AvailH, false, outerFlags)
            ImGui.setScrollX(0f)
            TransitionPlaylistEditorPanel.draw(session, mixer)
            ImGui.endChild()

            ImGui.sameLine(0f, colGap)

            // Column 4: Live Transition Queue
            ImGui.beginChild("LibraryTransitionQueue", c4W, g2AvailH, false, outerFlags)
            ImGui.setScrollX(0f)
            TransitionQueuePanel.draw(session, mixer)
            ImGui.endChild()
        } else if (viewMode == LibraryViewMode.FX) {
            // Column 3: Background FX Queue (BG)
            ImGui.beginChild("LibraryFxBgQueue", c3W, g2AvailH, false, outerFlags)
            ImGui.setScrollX(0f)
            FXBgQueueActionsPanel.draw(session, mixer)
            ImGui.endChild()

            ImGui.sameLine(0f, colGap)

            // Column 4: Play FX Queue (A/B)
            ImGui.beginChild("LibraryFxQueue", c4W, g2AvailH, false, outerFlags)
            ImGui.setScrollX(0f)
            FXQueueActionsPanel.draw(session, mixer)
            ImGui.endChild()
        } else {
            // Column 3: Background Queue (BG)
            ImGui.beginChild("LibraryBgQueue", c3W, g2AvailH, false, outerFlags)
            ImGui.setScrollX(0f)
            llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.draw(session, mixer)
            ImGui.endChild()

            ImGui.sameLine(0f, colGap)

            // Column 4: Play Queue (A/B)
            ImGui.beginChild("LibraryQueue", c4W, g2AvailH, false, outerFlags)
            ImGui.setScrollX(0f)
            QueueActionsPanel.draw(session, mixer)
            ImGui.endChild()
        }

        ImGui.endChild()

        ImGui.popStyleColor(2)
        ImGui.popStyleVar(2)

        // Global library keyboard shortcuts dynamically mapped via ShortcutManager
        val activeFile = getActiveSelectedFile(session)
        val io = ImGui.getIO()
        if (!io.wantTextInput) {
            if (viewMode == LibraryViewMode.TRANS) {
                // In TRANS mode: shortcuts Q (enqueue transition), Enter (apply to mixer), Up/Down (nav)
                val isEnter = ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false)
                val isQueueKey = ImGui.isKeyPressed(ImGuiKey.Q, false)
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
                val isLoadA = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.load_deck_a")
                val isLoadB = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.load_deck_b")
                val isLoadBG = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.load_deck_bg")
                val isLoadPV = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.load_deck_pv")
                val isQueueAB = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.queue_ab")
                val isQueueBG = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.queue_bg")
                val isNavUp = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("library.navigate")
                val isNavDown = !io.keyCtrl && !io.keyAlt && !io.keySuper && !io.keyShift && ImGui.isKeyPressed(ImGuiKey.DownArrow, false)

                val isFxMode = viewMode == LibraryViewMode.FX
                if (isLoadA && activeFile != null && activeFile.exists()) {
                    if (isFxMode) llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.handleDeckLoad(session, mixer, 1, mixer.deckA, "Deck A", activeFile)
                    else BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 1)
                    shouldReclaimFocus = true
                } else if (isLoadB && activeFile != null && activeFile.exists()) {
                    if (isFxMode) llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.handleDeckLoad(session, mixer, 2, mixer.deckB, "Deck B", activeFile)
                    else BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 2)
                    shouldReclaimFocus = true
                } else if (isLoadBG && activeFile != null && activeFile.exists()) {
                    if (isFxMode) llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.handleDeckLoad(session, mixer, 3, mixer.deckBG, "Deck BG", activeFile)
                    else BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 3)
                    shouldReclaimFocus = true
                } else if (isLoadPV && activeFile != null && activeFile.exists()) {
                    if (isFxMode) llm.slop.liquidlsd.ui.browser.BrowserActionToolbar.handleDeckLoad(session, mixer, 4, mixer.deckPV, "Deck PV", activeFile)
                    else BrowserDeckButtons.loadPresetToDeck(session, mixer, activeFile, 4)
                    shouldReclaimFocus = true
                } else if (isQueueBG && activeFile != null && activeFile.exists()) {
                    if (isFxMode) llm.slop.liquidlsd.presets.FXBgQueueManager.appendToQueue(activeFile)
                    else llm.slop.liquidlsd.presets.BgQueueManager.appendToQueue(activeFile)
                    shouldReclaimFocus = true
                } else if (isQueueAB && activeFile != null && activeFile.exists()) {
                    if (isFxMode) llm.slop.liquidlsd.presets.FXQueueManager.appendToQueue(activeFile)
                    else session.playQueueManager.appendToQueue(activeFile)
                    shouldReclaimFocus = true
                } else if (isNavUp) {
                    navigateSelection(-1, session, mixer)
                } else if (isNavDown) {
                    navigateSelection(1, session, mixer)
                }
            }
        }

        // Popups
        if (BrowserPopupHandler.pendingOpenRenamePopup) {
            ImGui.openPopup("RenameAssetPopup")
            BrowserPopupHandler.pendingOpenRenamePopup = false
        }
        if (BrowserPopupHandler.pendingOpenDeletePopup) {
            ImGui.openPopup("ConfirmDeleteAssetPopup")
            BrowserPopupHandler.pendingOpenDeletePopup = false
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
        BrowserPopupHandler.drawDeleteAssetConfirmationPopup()
        BrowserPopupHandler.drawNewPlaylistPopup()
        BrowserPopupHandler.drawExportQueuePopup(session)
        BrowserPopupHandler.drawExportBgQueuePopup()
        BrowserPopupHandler.drawExportTransQueuePopup()
        BrowserPopupHandler.drawExportFxQueuePopup()
        BrowserPopupHandler.drawExportFxBgQueuePopup()

        // Reset one-shot focus/scroll flags at end of frame
        shouldReclaimFocus = false
        shouldScrollToSelection = false
    }

    fun navigateSelection(delta: Int, session: SessionContext, mixer: Mixer) {
        when (activeSelectionSource) {
            SelectionSource.PRESETS -> {
                if (viewMode == LibraryViewMode.FX) {
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
            SelectionSource.PLAYLIST -> {
                val playlist = activePlaylistData
                if (playlist != null && playlist.presets.isNotEmpty()) {
                    val currentIdx = PlaylistEditorPanel.selectedPresetIndex
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else playlist.presets.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, playlist.presets.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        selectPlaylistPreset(targetIdx, session, mixer)
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
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
            SelectionSource.STOCK_TRANSITIONS -> {
                val list = StockTransitionListPanel.filteredTransitions
                if (list.isNotEmpty()) {
                    val currentIdx = list.indexOfFirst { it.id == StockTransitionListPanel.selectedTransitionId }
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else list.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, list.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        StockTransitionListPanel.selectedTransitionId = list[targetIdx].id
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            SelectionSource.TRANSITION_PRESETS -> {
                val list = TransitionPresetListPanel.filteredPresets
                if (list.isNotEmpty()) {
                    val currentIdx = list.indexOfFirst { it.path == TransitionPresetListPanel.selectedAsset?.path }
                    val targetIdx = if (currentIdx < 0) {
                        if (delta > 0) 0 else list.lastIndex
                    } else {
                        (currentIdx + delta).coerceIn(0, list.lastIndex)
                    }
                    if (targetIdx != currentIdx) {
                        TransitionPresetListPanel.selectedAsset = list[targetIdx]
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                }
            }
            SelectionSource.TRANSITION_PLAYLIST -> {
                val list = TransitionPlaylistEditorPanel.getSelectedPresetFile()
                // Navigation handled inside panel
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
            SelectionSource.FX_PLAYLIST -> {
                // Navigation handled inside panel
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
                if (viewMode == LibraryViewMode.TRANS) {
                    val list = StockTransitionListPanel.filteredTransitions
                    if (list.isNotEmpty()) {
                        StockTransitionListPanel.selectedTransitionId = list.first().id
                        activeSelectionSource = SelectionSource.STOCK_TRANSITIONS
                        shouldScrollToSelection = true
                        shouldReclaimFocus = true
                    }
                } else if (viewMode == LibraryViewMode.FX) {
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
        return when (viewMode) {
            LibraryViewMode.FX -> FXBrowserPanel.selectedAsset
            LibraryViewMode.TRANS -> TransitionPresetListPanel.selectedAsset
            LibraryViewMode.PRESETS -> PresetListPanel.selectedAsset
        }
    }
}
