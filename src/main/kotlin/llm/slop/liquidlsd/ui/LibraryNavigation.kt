package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.ExternalVideoSource
import llm.slop.liquidlsd.ui.browser.BrowseCatalogs
import llm.slop.liquidlsd.rendering.liveDeck
import llm.slop.liquidlsd.rendering.inactiveDeck
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.control.SendTarget
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.presets.DeckChange
import llm.slop.liquidlsd.presets.DeckOps
import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.LibraryPanel.SelectionSource
import llm.slop.liquidlsd.ui.browser.BrowseKind
import llm.slop.liquidlsd.ui.browser.BrowserPane
import llm.slop.liquidlsd.ui.browser.BgQueueActionsPanel
import llm.slop.liquidlsd.ui.browser.FXBgQueueActionsPanel
import llm.slop.liquidlsd.ui.browser.FXBrowserPanel
import llm.slop.liquidlsd.ui.browser.FXQueueActionsPanel
import llm.slop.liquidlsd.ui.browser.QueueActionsPanel
import llm.slop.liquidlsd.ui.browser.QueueDrop
import llm.slop.liquidlsd.ui.browser.QueueKnobs
import llm.slop.liquidlsd.ui.browser.QueueOps
import llm.slop.liquidlsd.ui.browser.QueueSplit
import llm.slop.liquidlsd.ui.browser.TransitionQueuePanel
import llm.slop.liquidlsd.ui.browser.DockActions
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
            LibraryViewMode.MAPS, LibraryViewMode.QUEUES -> null
        }
    }

    /** The lists the cursor can sit in for the current tab, left to right. A playlist is a tree scope shown in the list, so it has no pane of its own. */
    internal fun panes(): List<SelectionSource> = when (LibraryPanel.navMode) {
        // With the queues under the browser ([QueueSplit]), on after the browser's own two lists, in screen order.
        LibraryViewMode.PRESETS, LibraryViewMode.FX, LibraryViewMode.TRANS ->
            listOf(SelectionSource.TREE, SelectionSource.PRESETS) + if (QueueSplit.shows(LibraryPanel.navMode)) QUEUE_PANES else emptyList()
        LibraryViewMode.QUEUES -> QUEUE_PANES
        LibraryViewMode.MAPS -> emptyList()
    }

    /** Screen order of [llm.slop.liquidlsd.ui.browser.QueuesPane]: A/B, FX A/B, BG, FX BG, transitions. */
    private val QUEUE_PANES = listOf(
        SelectionSource.QUEUE_AB, SelectionSource.FX_QUEUE_AB, SelectionSource.QUEUE_BG, SelectionSource.FX_QUEUE_BG, SelectionSource.TRANSITION_QUEUE
    )

    private fun paneSize(source: SelectionSource, session: SessionContext): Int = when (source) {
        SelectionSource.TREE -> unifiedKind()?.let { BrowserPane.treeSize(it) } ?: 0
        SelectionSource.PRESETS -> when (LibraryPanel.navMode) {
            LibraryViewMode.PRESETS -> PresetListPanel.filteredPresets.size
            LibraryViewMode.FX -> FXBrowserPanel.filteredRows.size
            LibraryViewMode.TRANS -> TransitionBrowserPanel.filteredRows.size
            LibraryViewMode.MAPS, LibraryViewMode.QUEUES -> 0
        }
        SelectionSource.QUEUE_AB -> session.playQueueManager.queue.size
        SelectionSource.QUEUE_BG -> BgQueueManager.queue.size
        SelectionSource.TRANSITION_QUEUE -> TransitionQueueManager.queue.size
        SelectionSource.FX_QUEUE_AB -> FXQueueManager.queue.size
        SelectionSource.FX_QUEUE_BG -> FXBgQueueManager.queue.size
    }

    private fun hasCursor(source: SelectionSource): Boolean = when (source) {
        SelectionSource.TREE -> true // the cursor starts on the selected scope
        SelectionSource.PRESETS -> LibraryPanel.getSelectedAsset() != null
        SelectionSource.QUEUE_AB -> QueueActionsPanel.selectedIndex >= 0
        SelectionSource.QUEUE_BG -> BgQueueActionsPanel.selectedIndex >= 0
        SelectionSource.TRANSITION_QUEUE -> TransitionQueuePanel.selectedIndex >= 0
        SelectionSource.FX_QUEUE_AB -> FXQueueActionsPanel.selectedIndex >= 0
        SelectionSource.FX_QUEUE_BG -> FXBgQueueActionsPanel.selectedIndex >= 0
    }

    /** Moves the cursor to the next (or previous) non-empty list of this tab and puts it on an item. */
    fun stepPane(delta: Int, session: SessionContext, mixer: Mixer) {
        val panes = panes()
        // No cursor yet: count as being in the list, where a fresh tab starts.
        val from = panes.indexOf(LibraryPanel.activeSelectionSource).takeIf { it >= 0 } ?: panes.indexOf(SelectionSource.PRESETS)
        for (i in 1..panes.size) {
            val candidate = panes[Math.floorMod(from + delta * i, panes.size)]
            if (paneSize(candidate, session) == 0) continue
            LibraryPanel.activeSelectionSource = candidate
            if (!hasCursor(candidate)) LibraryPanel.navigateSelection(1, session, mixer)
            LibraryPanel.shouldScrollToSelection = true
            return
        }
    }

    /**
     * Where the cursor sits in its list as 0..1 (first row to last), for the cursor knob's ring. Full (1) when there is
     * no cursor yet or the list has at most one row.
     */
    fun cursorPosition(session: SessionContext): Float {
        val source = LibraryPanel.activeSelectionSource ?: SelectionSource.PRESETS
        val size = paneSize(source, session)
        if (size <= 1) return 1f
        val index = when (source) {
            SelectionSource.TREE -> unifiedKind()?.let { BrowserPane.treeIndex(it) } ?: -1
            SelectionSource.PRESETS -> when (LibraryPanel.navMode) {
                LibraryViewMode.PRESETS -> PresetListPanel.filteredPresets.indexOfFirst { it.path == PresetListPanel.selectedAsset?.path }
                LibraryViewMode.FX -> FXBrowserPanel.filteredRows.indexOfFirst { it.path == FXBrowserPanel.selectedAsset?.path }
                LibraryViewMode.TRANS -> TransitionBrowserPanel.filteredRows.indexOfFirst { it.path == TransitionBrowserPanel.selectedAsset?.path }
                LibraryViewMode.MAPS, LibraryViewMode.QUEUES -> -1
            }
            SelectionSource.QUEUE_AB -> QueueActionsPanel.selectedIndex
            SelectionSource.QUEUE_BG -> BgQueueActionsPanel.selectedIndex
            SelectionSource.TRANSITION_QUEUE -> TransitionQueuePanel.selectedIndex
            SelectionSource.FX_QUEUE_AB -> FXQueueActionsPanel.selectedIndex
            SelectionSource.FX_QUEUE_BG -> FXBgQueueActionsPanel.selectedIndex
        }
        return if (index < 0) 1f else (index.toFloat() / (size - 1)).coerceIn(0f, 1f)
    }

    fun step(steps: Int, session: SessionContext, mixer: Mixer) {
        val direction = if (steps > 0) 1 else -1
        repeat(abs(steps)) { LibraryPanel.navigateSelection(direction, session, mixer) }
    }

    /** The files the enqueue shortcuts act on: the list's multi-selection, else the cursor item. */
    fun enqueueTargets(session: SessionContext): List<File> {
        // The queues are the destination, not a source (also with the cursor in one under the browser).
        if (LibraryPanel.navMode == LibraryViewMode.QUEUES || LibraryPanel.activeSelectionSource in QUEUE_PANES) return emptyList()
        if (LibraryPanel.activeSelectionSource == SelectionSource.PRESETS) {
            when (LibraryPanel.navMode) {
                LibraryViewMode.PRESETS -> return PresetListPanel.selection.getSelectedInOrder(PresetListPanel.filteredPresets)
                    .filter { it.type != AssetType.SOURCE_STOCK && it.type != AssetType.SOURCE_EXTERNAL }
                    .map { File(it.path) }
                LibraryViewMode.FX -> if (FXBrowserPanel.selection.count > 1) return FXBrowserPanel.selection.getSelectedInOrder(FXBrowserPanel.filteredRows)
                    .filter { it.type != AssetType.FX_STOCK }
                    .map { File(it.path) }
                LibraryViewMode.TRANS -> if (TransitionBrowserPanel.selection.count > 1) return TransitionBrowserPanel.selection
                    .getSelectedInOrder(TransitionBrowserPanel.filteredRows).map { TransitionBrowserPanel.fileFor(it) }
                else -> Unit
            }
        }
        val file = LibraryPanel.getActiveSelectedFile(session)
        return if (file != null && file.exists()) listOf(file) else emptyList()
    }

    /**
     * The cursor knob's shift + tap in the Library view: the cursor item(s), or the tree cursor's playlist, go after the cursor of the
     * queue knob touched last in browse [browseSession] ([QueueKnobs.insertFromBrowser]). False when no queue knob was touched.
     */
    fun enqueueAtQueueKnob(session: SessionContext, browseSession: Int): Boolean {
        if (QueueKnobs.lastTouched(browseSession) == null) return false
        val files = if (LibraryPanel.activeSelectionSource == SelectionSource.TREE) {
            unifiedKind()?.let { BrowserPane.cursorPlaylistFile(it) }?.let(::listOf) ?: emptyList()
        } else enqueueTargets(session)
        return QueueKnobs.insertFromBrowser(files, browseSession)
    }

    /** Appends the cursor item(s) (on the unified tree: the cursor playlist) to the background queue ([bg]) or the A/B queue of the current tab. Returns false if nothing was added. */
    fun enqueue(session: SessionContext, bg: Boolean): Boolean {
        val queue = when (unifiedKind()) {
            BrowseKind.SRC -> if (bg) QueueDrop.Queue.BG else QueueDrop.Queue.AB
            BrowseKind.FX -> if (bg) QueueDrop.Queue.FX_BG else QueueDrop.Queue.FX_AB
            BrowseKind.TRANS -> QueueDrop.Queue.TRANS
            null -> return false
        }
        // On the unified pane's tree the cursor row is a folder; a playlist there enqueues as a whole.
        if (LibraryPanel.activeSelectionSource == SelectionSource.TREE) {
            return unifiedKind()?.let { BrowserPane.enqueueCursorPlaylist(session, it, bg) }?.also { if (it) QueueDrop.flash(queue) } ?: false
        }
        val files = enqueueTargets(session)
        if (files.isEmpty()) return false
        when (queue) {
            QueueDrop.Queue.FX_AB -> files.forEach { FXQueueManager.appendToQueue(it) }
            QueueDrop.Queue.FX_BG -> files.forEach { FXBgQueueManager.appendToQueue(it) }
            QueueDrop.Queue.AB -> files.forEach { session.playQueueManager.appendToQueue(it) }
            QueueDrop.Queue.BG -> files.forEach { BgQueueManager.appendToQueue(it) }
            QueueDrop.Queue.TRANS -> files.forEach { TransitionQueueManager.appendToQueue(it) }
        }
        // Where they went, for when the queues are on screen (the Queues tab, or under the browser).
        QueueDrop.flash(queue)
        return true
    }

    /** Loads a Sources-list item (preset file or stock generator) to the deck the crossfader is moving away from. */
    fun loadAssetToInactiveDeck(session: SessionContext, mixer: Mixer, asset: AssetItem, parametersState: ParametersState) {
        val targetDeck = mixer.inactiveDeck
        val targetLabel = if (targetDeck === mixer.deckA) "Deck A" else "Deck B"
        if (asset.type == AssetType.SOURCE_STOCK) {
            val source = VisualSourceRegistry.availableSources.find { it.id == asset.path.removePrefix(PresetListPanel.STOCK_PATH_PREFIX) } ?: return
            UIManager.changeVisualSourceSafely(mixer, targetDeck, targetLabel, source, parametersState)
        } else if (asset.type == AssetType.SOURCE_EXTERNAL) {
            UIManager.changeVisualSourceSafely(mixer, targetDeck, targetLabel, ExternalVideoSource(serverName = BrowseCatalogs.externalName(asset)), parametersState)
        } else {
            UIManager.loadDeckPresetSafely(mixer, targetDeck, File(asset.path))
        }
    }

    /**
     * Applies the cursor item: a preset or generator loads to the inactive deck (an A/B queue item plays like a double-click; the BG queue loads to
     * Deck BG), a transition applies to the mixer, an FX browser item goes to the FX A/B queue, an FX queue item is applied to its deck.
     */
    fun accept(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        when (LibraryPanel.activeSelectionSource) {
            SelectionSource.TREE -> unifiedKind()?.let {
                BrowserPane.acceptTree(it)
                // In the pair view a scope tap also moves the cursor into the list (fewer presses); the Library keeps tap-selects-only.
                if (BrowserPane.hosted() != null) LibraryPanel.activeSelectionSource = SelectionSource.PRESETS
            }
            SelectionSource.PRESETS -> DockActions.acceptCursorRow(session, mixer, parametersState)
            // Same as a double-click on the row.
            SelectionSource.QUEUE_AB -> QueueOps.play(QueueDrop.Queue.AB, QueueActionsPanel.selectedIndex, session, mixer)
            SelectionSource.QUEUE_BG -> QueueOps.play(QueueDrop.Queue.BG, BgQueueActionsPanel.selectedIndex, session, mixer)
            SelectionSource.TRANSITION_QUEUE -> QueueOps.play(QueueDrop.Queue.TRANS, TransitionQueuePanel.selectedIndex, session, mixer)
            SelectionSource.FX_QUEUE_AB -> QueueOps.play(QueueDrop.Queue.FX_AB, FXQueueActionsPanel.selectedIndex, session, mixer)
            SelectionSource.FX_QUEUE_BG -> QueueOps.play(QueueDrop.Queue.FX_BG, FXBgQueueActionsPanel.selectedIndex, session, mixer)
            null -> Unit
        }
    }

    /** The cursor item a send knob would act on: the Sources or FX list row, when the cursor is in that list. */
    private fun sendAsset(): AssetItem? {
        if (LibraryPanel.activeSelectionSource != SelectionSource.PRESETS) return null
        val asset = when (LibraryPanel.navMode) {
            LibraryViewMode.PRESETS -> PresetListPanel.selectedAsset?.takeIf { it.type == AssetType.PRESET || it.type == AssetType.SOURCE_STOCK || it.type == AssetType.SOURCE_EXTERNAL }
            LibraryViewMode.FX -> FXBrowserPanel.selectedAsset?.takeIf { it.type in FX_TYPES }
            else -> null
        }
        return asset?.takeIf { it.isValid }
    }

    private val FX_TYPES = setOf(AssetType.FX_STOCK, AssetType.FX_PRESET, AssetType.FX_CHAIN)

    /** The knobs that are live for the cursor item: every deck for a source or preset, the decks and the master bus for an FX. */
    fun sendTargets(): Set<SendTarget> = when (sendAsset()?.type) {
        null -> emptySet()
        AssetType.PRESET, AssetType.SOURCE_STOCK, AssetType.SOURCE_EXTERNAL -> DECK_TARGETS
        else -> SendTarget.entries.toSet()
    }

    private val DECK_TARGETS = setOf(SendTarget.A, SendTarget.B, SendTarget.BG, SendTarget.PV)

    /**
     * Sends the cursor item to [target] through the same paths as the context menu: a source or preset via [DeckOps] (dirty guard and undo),
     * a chain replacing all 3 slots, a single FX into the first vacant slot (the last slot when the chain is full: a controller has no popup).
     * Chain and slot loads are undoable like the deck loads. From Library FULL the send only loads, so the view, scope and cursor stay put and
     * the next item can be sent straight away (FX sends toast where they landed). From the pair view it also moves the focus to the
     * target's pair, bound to the loaded half, so the controller's rows play what was just loaded: the deck's SRC row for a source, its FX chain (the
     * landed slot for a single FX) for an effect, the Master FX chain for the master bus.
     */
    fun send(target: SendTarget, session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        val asset = sendAsset() ?: return
        if (target !in sendTargets()) return
        val deckSlot = DeckSlot.entries.firstOrNull { it.name == target.name }
        val deckLabel = deckSlot?.label
        val bankId = deckSlot?.bankId ?: MacroEngine.MASTER
        // From a bound dock (pair view or in place) the binding follows the send; from Library FULL a send only loads, and the
        // controller's knobs 1-8 move to the target's rows.
        val retarget = parametersState.focusedPair != null || parametersState.boundInPlace
        if (!retarget) parametersState.libraryLivePair = if (target == SendTarget.MASTER) PerfRows.PAIR_MASTER else target.name
        when (asset.type) {
            AssetType.SOURCE_STOCK -> {
                val source = VisualSourceRegistry.availableSources.find { it.id == asset.path.removePrefix(PresetListPanel.STOCK_PATH_PREFIX) } ?: return
                DeckOps.request(deckSlot ?: return, DeckChange.Source(source))
                if (retarget) parametersState.openGenBrowse(bankId, deckLabel ?: return)
            }
            AssetType.SOURCE_EXTERNAL -> {
                DeckOps.request(deckSlot ?: return, DeckChange.Source(ExternalVideoSource(serverName = BrowseCatalogs.externalName(asset))))
                if (retarget) parametersState.openGenBrowse(bankId, deckLabel ?: return)
            }
            AssetType.PRESET -> {
                DeckOps.request(deckSlot ?: return, DeckChange.Preset(File(asset.path)))
                if (retarget) parametersState.openGenBrowse(bankId, deckLabel ?: return)
            }
            AssetType.FX_CHAIN -> {
                val chain = deckSlot?.deck(mixer)?.fxChain ?: mixer.masterFxChain
                FxOps.loadChain(session, File(asset.path), chain, undoable = true)
                ToastOverlay.show("${asset.name} -> ${deckLabel ?: "Master"} FX chain")
                if (retarget) parametersState.openFxChainBrowse(bankId, deckLabel, null)
            }
            else -> {
                val chain = deckSlot?.deck(mixer)?.fxChain ?: mixer.masterFxChain
                val slot = FxOps.firstVacantSlot(chain) ?: (FxChain.SLOT_COUNT - 1)
                FXBrowserPanel.loadSingle(session, asset, chain, slot)
                ToastOverlay.show("${asset.name} -> ${deckLabel ?: "Master"} FX slot ${slot + 1}")
                if (retarget) parametersState.openFxChainBrowse(bankId, deckLabel, slot)
            }
        }
    }
}
