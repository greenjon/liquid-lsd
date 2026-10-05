package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.liveDeck
import llm.slop.liquidlsd.rendering.inactiveDeck
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.LibraryPanel.SelectionSource
import llm.slop.liquidlsd.ui.browser.BrowseKind
import llm.slop.liquidlsd.ui.browser.BrowserDeckButtons
import llm.slop.liquidlsd.ui.browser.BrowserPane
import llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel
import llm.slop.liquidlsd.ui.browser.FXBgQueueActionsPanel
import llm.slop.liquidlsd.ui.browser.FXBrowserPanel
import llm.slop.liquidlsd.ui.browser.FXQueueActionsPanel
import llm.slop.liquidlsd.ui.browser.FXPlaylistEditorPanel
import llm.slop.liquidlsd.ui.browser.PlaylistEditorPanel
import llm.slop.liquidlsd.ui.browser.TransitionPlaylistEditorPanel
import llm.slop.liquidlsd.ui.browser.QueueActionsPanel
import llm.slop.liquidlsd.ui.browser.TransitionQueuePanel
import llm.slop.liquidlsd.ui.browser.PresetListPanel
import llm.slop.liquidlsd.ui.browser.TransitionBrowserPanel
import java.io.File
import kotlin.math.abs

/**
 * Library actions that don't depend on drawing, so the keyboard, the mouse and a controller share them:
 * tab and pane stepping, cursor stepping, loading to a deck, enqueueing. The cursor itself lives in
 * [LibraryPanel] (`activeSelectionSource` plus each list's selection).
 */
internal object LibraryNavigation {
    /** Switches tab. The selection source is dropped so the next cursor step starts in the new tab's own list. */
    fun setViewMode(mode: LibraryViewMode) {
        if (mode == LibraryPanel.viewMode) return
        LibraryPanel.viewMode = mode
        LibraryPanel.activeSelectionSource = null
    }

    fun stepTab(delta: Int) {
        val modes = LibraryViewMode.values()
        setViewMode(modes[Math.floorMod(LibraryPanel.viewMode.ordinal + delta, modes.size)])
    }

    /** The browse kind of the current tab (the pane is what is on screen), else null (Maps). */
    internal fun unifiedKind(): BrowseKind? {
        return when (LibraryPanel.navMode) {
            LibraryViewMode.PRESETS -> BrowseKind.SRC
            LibraryViewMode.FX -> BrowseKind.FX
            LibraryViewMode.TRANS -> BrowseKind.TRANS
            LibraryViewMode.MAPS -> null
        }
    }

    /** The lists the cursor can sit in for the current tab, left to right. The unified pane has no playlist column: a playlist is a tree scope shown in the list. */
    internal fun panes(): List<SelectionSource> = if (unifiedKind() != null) when (LibraryPanel.navMode) {
        LibraryViewMode.PRESETS -> listOf(SelectionSource.TREE, SelectionSource.PRESETS, SelectionSource.QUEUE_BG, SelectionSource.QUEUE_AB)
        LibraryViewMode.FX -> listOf(SelectionSource.TREE, SelectionSource.PRESETS, SelectionSource.FX_QUEUE_BG, SelectionSource.FX_QUEUE_AB)
        else -> listOf(SelectionSource.TREE, SelectionSource.PRESETS, SelectionSource.TRANSITION_QUEUE)
    } else when (LibraryPanel.navMode) {
        LibraryViewMode.PRESETS -> listOf(SelectionSource.PRESETS, SelectionSource.PLAYLIST, SelectionSource.QUEUE_BG, SelectionSource.QUEUE_AB)
        LibraryViewMode.FX -> listOf(SelectionSource.PRESETS, SelectionSource.FX_PLAYLIST, SelectionSource.FX_QUEUE_BG, SelectionSource.FX_QUEUE_AB)
        LibraryViewMode.TRANS -> listOf(SelectionSource.PRESETS, SelectionSource.TRANSITION_PLAYLIST, SelectionSource.TRANSITION_QUEUE)
        LibraryViewMode.MAPS -> emptyList()
    }

    private fun paneSize(source: SelectionSource, session: SessionContext): Int = when (source) {
        SelectionSource.TREE -> unifiedKind()?.let { BrowserPane.treeSize(it) } ?: 0
        SelectionSource.PRESETS -> when (LibraryPanel.navMode) {
            LibraryViewMode.PRESETS -> PresetListPanel.filteredPresets.size
            LibraryViewMode.FX -> FXBrowserPanel.filteredRows.size
            LibraryViewMode.TRANS -> TransitionBrowserPanel.filteredRows.size
            LibraryViewMode.MAPS -> 0
        }
        SelectionSource.PLAYLIST -> LibraryPanel.selectedPlaylistFile?.let { LibraryPanel.getOrLoadPlaylist(it) }?.presets?.size ?: 0
        SelectionSource.QUEUE_AB -> session.playQueueManager.queue.size
        SelectionSource.QUEUE_BG -> BgQueueManager.queue.size
        SelectionSource.TRANSITION_QUEUE -> TransitionQueueManager.queue.size
        SelectionSource.FX_QUEUE_AB -> FXQueueManager.queue.size
        SelectionSource.FX_QUEUE_BG -> FXBgQueueManager.queue.size
        SelectionSource.TRANSITION_PLAYLIST -> TransitionPlaylistEditorPanel.itemCount()
        SelectionSource.FX_PLAYLIST -> FXPlaylistEditorPanel.itemCount()
    }

    private fun hasCursor(source: SelectionSource): Boolean = when (source) {
        SelectionSource.TREE -> true // the cursor starts on the selected scope
        SelectionSource.PRESETS -> LibraryPanel.getSelectedAsset() != null
        SelectionSource.PLAYLIST -> PlaylistEditorPanel.selectedPresetIndex >= 0
        SelectionSource.QUEUE_AB -> QueueActionsPanel.selectedIndex >= 0
        SelectionSource.QUEUE_BG -> BgQueueActionsPanel.selectedIndex >= 0
        SelectionSource.TRANSITION_QUEUE -> TransitionQueuePanel.selectedIndex >= 0
        SelectionSource.FX_QUEUE_AB -> FXQueueActionsPanel.selectedIndex >= 0
        SelectionSource.FX_QUEUE_BG -> FXBgQueueActionsPanel.selectedIndex >= 0
        SelectionSource.TRANSITION_PLAYLIST -> TransitionPlaylistEditorPanel.selectedItemIndex >= 0
        SelectionSource.FX_PLAYLIST -> FXPlaylistEditorPanel.selectedItemIndex >= 0
    }

    /** Moves the cursor to the next (or previous) non-empty list of this tab and puts it on an item. */
    fun stepPane(delta: Int, session: SessionContext, mixer: Mixer) {
        val panes = panes()
        // No cursor yet: the unified pane counts as being in its list (where a fresh tab starts), the classic columns start before the first pane.
        val from = panes.indexOf(LibraryPanel.activeSelectionSource).let {
            when {
                it >= 0 -> it
                SelectionSource.TREE in panes -> panes.indexOf(SelectionSource.PRESETS)
                delta > 0 -> -1
                else -> 0
            }
        }
        for (i in 1..panes.size) {
            val candidate = panes[Math.floorMod(from + delta * i, panes.size)]
            if (paneSize(candidate, session) == 0) continue
            LibraryPanel.activeSelectionSource = candidate
            if (!hasCursor(candidate)) LibraryPanel.navigateSelection(1, session, mixer)
            LibraryPanel.shouldScrollToSelection = true
            return
        }
    }

    fun step(steps: Int, session: SessionContext, mixer: Mixer) {
        val direction = if (steps > 0) 1 else -1
        repeat(abs(steps)) { LibraryPanel.navigateSelection(direction, session, mixer) }
    }

    /** The files the enqueue shortcuts act on: the multi-selection in the Sources list, else the cursor item. */
    fun enqueueTargets(session: SessionContext): List<File> {
        if (LibraryPanel.activeSelectionSource == SelectionSource.PRESETS && LibraryPanel.navMode == LibraryViewMode.PRESETS) {
            return PresetListPanel.selection.getSelectedInOrder(PresetListPanel.filteredPresets)
                .filter { it.type != AssetType.SOURCE_STOCK }
                .map { File(it.path) }
        }
        val file = LibraryPanel.getActiveSelectedFile(session)
        return if (file != null && file.exists()) listOf(file) else emptyList()
    }

    /** Appends the cursor item(s) (on the unified tree: the cursor playlist) to the background queue ([bg]) or the A/B queue of the current tab. Returns false if nothing was added. */
    fun enqueue(session: SessionContext, bg: Boolean): Boolean {
        // On the unified pane's tree the cursor row is a folder; a playlist there enqueues as a whole.
        if (LibraryPanel.activeSelectionSource == SelectionSource.TREE) return unifiedKind()?.let { BrowserPane.enqueueCursorPlaylist(session, it, bg) } ?: false
        val files = enqueueTargets(session)
        if (files.isEmpty()) return false
        when (LibraryPanel.navMode) {
            LibraryViewMode.FX -> files.forEach { if (bg) FXBgQueueManager.appendToQueue(it) else FXQueueManager.appendToQueue(it) }
            LibraryViewMode.PRESETS -> files.forEach { if (bg) BgQueueManager.appendToQueue(it) else session.playQueueManager.appendToQueue(it) }
            LibraryViewMode.TRANS -> files.forEach { TransitionQueueManager.appendToQueue(it) }
            LibraryViewMode.MAPS -> Unit
        }
        return true
    }

    /** Loads a Sources-list item (preset file or stock generator) to the deck the crossfader is moving away from. */
    fun loadAssetToInactiveDeck(session: SessionContext, mixer: Mixer, asset: AssetItem, parametersState: ParametersState) {
        val targetDeck = mixer.inactiveDeck
        val targetLabel = if (targetDeck === mixer.deckA) "Deck A" else "Deck B"
        if (asset.type == AssetType.SOURCE_STOCK) {
            val source = VisualSourceRegistry.availableSources.find { it.id == asset.path.removePrefix(PresetListPanel.STOCK_PATH_PREFIX) } ?: return
            UIManager.changeVisualSourceSafely(mixer, targetDeck, targetLabel, source, parametersState)
        } else {
            UIManager.loadDeckPresetSafely(mixer, targetDeck, File(asset.path))
        }
    }

    /**
     * Applies the cursor item: a preset or generator loads to the inactive deck (an A/B queue item plays like a double-click; the BG queue loads to
     * Deck BG), a transition applies to the mixer, an FX browser item goes to the FX A/B queue, an FX queue item is applied to its deck.
     */
    fun accept(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        val file = LibraryPanel.getActiveSelectedFile(session)
        when (LibraryPanel.activeSelectionSource) {
            SelectionSource.TREE -> unifiedKind()?.let {
                BrowserPane.acceptTree(it)
                // In the Edit bay a scope tap also moves the cursor into the list (fewer presses); the Library keeps tap-selects-only.
                if (BrowserPane.hosted() != null) LibraryPanel.activeSelectionSource = SelectionSource.PRESETS
            }
            SelectionSource.PRESETS -> if (BrowserPane.hosted() != null) {
                // Hosted in the Edit bay: the cursor row applies to the bay's target.
                BrowserPane.applyCursorRow()
            } else when (LibraryPanel.navMode) {
                LibraryViewMode.PRESETS -> PresetListPanel.selectedAsset?.let { loadAssetToInactiveDeck(session, mixer, it, parametersState) }
                LibraryViewMode.FX -> enqueue(session, bg = false)
                LibraryViewMode.TRANS -> TransitionBrowserPanel.selectedAsset?.let { TransitionBrowserPanel.applyToMixer(session, mixer, it) }
                LibraryViewMode.MAPS -> Unit
            }
            SelectionSource.PLAYLIST ->
                file?.let { BrowserDeckButtons.loadPresetToDeck(session, mixer, it, if (mixer.crossfade.value > 0.0f) 1 else 2) }
            // Same as a double-click: moves the queue position, fades to the loaded deck and advances the transition queue.
            SelectionSource.QUEUE_AB -> session.playQueueManager.playIndex(QueueActionsPanel.selectedIndex, mixer)
            SelectionSource.QUEUE_BG -> file?.let { BrowserDeckButtons.loadPresetToDeck(session, mixer, it, 3) }
            SelectionSource.TRANSITION_QUEUE, SelectionSource.TRANSITION_PLAYLIST -> file?.let { TransitionQueueManager.applyTransitionItem(it, mixer) }
            SelectionSource.FX_QUEUE_AB -> FXQueueManager.jumpToIndex(FXQueueActionsPanel.selectedIndex, session, mixer)
            SelectionSource.FX_QUEUE_BG -> FXBgQueueManager.jumpToIndex(FXBgQueueActionsPanel.selectedIndex, session, mixer)
            SelectionSource.FX_PLAYLIST -> file?.let {
                FxOps.applyItem(session, it, mixer.liveDeck.fxChain)
            }
            null -> Unit
        }
    }
}
