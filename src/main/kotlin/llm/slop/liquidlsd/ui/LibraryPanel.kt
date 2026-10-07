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
import llm.slop.liquidlsd.ui.browser.BrowserDock
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
        /** All five play queues side by side (A/B presets, BG presets, A/B FX, BG FX, transitions). The other tabs have no queue column. */
        QUEUES,
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

    /** The tab the controller and keyboard act on. The dock owns one selected tab wherever it is drawn (Library or Edit bay). */
    val navMode: LibraryViewMode get() = viewMode
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
    fun isEditView(session: SessionContext): Boolean = viewStateOf(session).editing

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
                        ?.takeIf { it.type != AssetType.SOURCE_STOCK && it.type != AssetType.SOURCE_EXTERNAL }
                        ?.let { File(it.path) }
                    LibraryViewMode.MAPS, LibraryViewMode.QUEUES -> null
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
        }
    }

    fun selectQueueAb(index: Int, session: SessionContext, mixer: Mixer) {
        QueueActionsPanel.selectedIndex = index
        if (index >= 0) {
            activeSelectionSource = SelectionSource.QUEUE_AB
            PresetListPanel.selectedAsset = null
            llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = -1
        }
    }

    fun selectQueueBg(index: Int, session: SessionContext, mixer: Mixer) {
        llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel.selectedIndex = index
        if (index >= 0) {
            activeSelectionSource = SelectionSource.QUEUE_BG
            PresetListPanel.selectedAsset = null
            QueueActionsPanel.selectedIndex = -1
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

    fun draw(session: SessionContext, width: Float, height: Float, mixer: Mixer, parametersState: ParametersState, binding: BrowserDock.DockBinding? = null) {
        checkAutoRefresh()
        val safeW = width.coerceAtLeast(80f)

        if (ImGui.beginMenuBar()) {
            val menuBarH = ImGui.getFrameHeight()
            val btnH = 21f
            val bottomSpacing = 2.5f
            val yOffset = (menuBarH - btnH - bottomSpacing).coerceAtLeast(0f)

            BrowserDock.drawHeader(session, mixer, parametersState, safeW, btnH, yOffset, binding = binding)

            // Right-aligned Maximize / Restore button
            val windowBtnW = (btnH * 1.15f).coerceIn(20f, 32f)
            val rightX = (safeW - windowBtnW - 8f).coerceAtLeast(ImGui.getCursorPosX() + 8f)
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

        if (binding == null) BrowserDock.libraryShown()
        BrowserDock.drawBody(session, mixer, parametersState, binding)
        BrowserDock.drawShortcuts(session, mixer)
        BrowserDock.drawPopups(session)
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
                if (navMode == LibraryViewMode.QUEUES) {
                    LibraryNavigation.stepPane(1, session, mixer)
                } else if (navMode == LibraryViewMode.TRANS) {
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
            LibraryViewMode.MAPS, LibraryViewMode.QUEUES -> null
            LibraryViewMode.PRESETS -> PresetListPanel.selectedAsset
        }
    }
}
