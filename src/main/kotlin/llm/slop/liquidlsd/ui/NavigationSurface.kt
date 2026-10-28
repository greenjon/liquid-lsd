package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.control.NavSurface
import llm.slop.liquidlsd.control.SendTarget
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.browser.BrowserDock
import llm.slop.liquidlsd.ui.browser.BrowserPane
import llm.slop.liquidlsd.ui.browser.QueueKnobs

/**
 * Applies controller navigation to the live UI. The three side buttons mean different things per context:
 *  - Library view (Library FULL): back, next tab, next list; the cursor is knob 16. Tapping a send knob (9-12 = A, B, BG, PV;
 *    13 = master FX) sends the cursor item there and opens that row in the picker, so it also works in the picker.
 *    With shift: enqueue to the BG queue, previous tab, previous list. With the queues under the browser, knobs 14-15 are the tab's
 *    two queues ([QueueKnobs]) and knob 16's shift + tap puts the item after the cursor of the queue knob touched last.
 *  - Pair view (the unified pane under the focused pair's two rows), or the Perform dock bound in place (same buttons; knobs 1-8
 *    stay live on the page's first two rows): side 1 = back, side 2 = next tab (shift: previous),
 *    side 3 = next pane (tree > list), shift + side 3 = clear the slot or chain; the cursor is knob 16
 *    (tap = apply, shift + tap = enqueue as in the Library, and a tap on a tree row selects the scope and moves the cursor into the
 *    list). Shift + tap on a send knob (9-13) switches to that deck's (or Master's) pair without sending.
 *  - Chain view (a pair view state, [ParametersState.chainView]): side 1 = back (leave Chain view), side 3 = Load on/off
 *    (row 4 swaps between the SRC row and the load row). Playing, nothing browses: all 16 knobs are the four rows and side 2 opens the
 *    Library as in Perform. Loading, knob 16 is the cursor (tap = apply to the targeted slot), knobs 13-15 load the cursor item into
 *    slots 1-3, knobs 1-12 stay on the slot rows, there are no sends, and side 2 steps the pane (shift: the tab), shift + side 3
 *    clears the targeted slot.
 *  - Dirty-deck modal up: back = Cancel, side 2 / knob tap = Save, side 3 / shift+tap = Discard (overrides every other context).
 *  - Perform / Edit view: back (the Esc stack), open the Library, open the picker of the row whose knob
 *    was touched last (an SRC row: its source; an FX row: the slot under the knob, or the chain list for knob 1;
 *    Transitions: the transition list).
 */
internal class NavigationSurface(
    private val session: SessionContext,
    private val parametersState: ParametersState,
    private val mixer: Mixer,
    private val ctx: PerformanceUiContext,
    private val deckConfirm: DeckConfirmPrompt? = null
) : NavSurface {
    private val theme get() = session.uiTheme

    private val inLibraryView: Boolean get() = viewStateOf(session).maximized

    /** A bound dock is up: the pair view (its two rows over the Browse dock), or the Perform dock bound in place under the page's rows. */
    private val inPicker: Boolean get() = viewStateOf(session).let { it.pair || it.bound }

    /** Chain view is on screen (it is a pair view, so [inPicker] is true too); null otherwise. */
    private val chainState: ParametersState.ChainViewState?
        get() = parametersState.chainView?.takeIf { viewStateOf(session).pair }

    private val chainLoading: Boolean get() = chainState == ParametersState.ChainViewState.BROWSE

    internal val confirming: Boolean get() = deckConfirm?.deckConfirmPending == true

    /** While the dirty-deck modal is up, the cursor knob and the side buttons answer it (see [DeckConfirmChoice]). */
    override val browsing: Boolean get() = confirming || inLibraryView || (inPicker && chainState != ParametersState.ChainViewState.PLAY)

    override val browsePosition: Float get() = if (confirming) 1f else LibraryNavigation.cursorPosition(session)

    /**
     * In the pair view its two rows are the only rows on screen, so Twister rows one and two (knobs 1-8) drive them. Bound in place,
     * the same knobs drive the page's first two rows (the rest of the page is sends and the cursor while the list has the controller).
     * In the Library view they drive the live pair, the deck the last send loaded ([ParametersState.libraryLivePair]).
     */
    override val browseLiveKnobs: Int get() = if ((inPicker || inLibraryView) && !confirming) PAIR_LIVE_KNOBS else 0

    /** Chain view loading: the three slot rows (knobs 1-12) stay live above the load row. The cursor is knob 16, as everywhere. */
    override fun isBrowseLive(knob: Int): Boolean =
        if (chainLoading && !confirming) knob < CHAIN_LOAD_FIRST_KNOB else knob < browseLiveKnobs

    /** Chain view loading: knobs 13-15 load the cursor item into slots 1-3. Library view: the queue knobs ([QueueKnobs], 14-15). */
    override fun browseKnobTap(knob: Int, shifted: Boolean): Boolean {
        if (confirming) return false
        if (inLibraryView && shifted && knob == SendTarget.PV.knob) { LibraryNavigation.toggleAutoPreview(); return true }
        if (inLibraryView) return QueueKnobs.tap(knob, shifted, browseSession, session, mixer)
        val slot = knob - CHAIN_LOAD_FIRST_KNOB
        if (!chainLoading || shifted || slot !in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) return false
        ChainLoad.loadCursorInto(session, mixer, parametersState, slot)
        return true
    }

    /** Library view: knobs 14-15 move the cursor of the tab's two queues; held, they carry the cursor row ([QueueKnobs]). */
    override fun browseKnobTurn(knob: Int, steps: Int, held: Boolean): Boolean =
        inLibraryView && !confirming && QueueKnobs.turn(knob, steps, held, browseSession, parametersState)

    // The session counter lives in the companion so short-lived surfaces (tests) share it; sampling on construction,
    // once per frame (UIManager) and on read catches every inactive -> active edge.
    init { sampleBrowsing() }

    override val browseSession: Int get() { sampleBrowsing(); return session_ }

    fun sampleBrowsing() {
        val now = browsing
        val pair = parametersState.focusedPair
        val chain = parametersState.chainView
        if (now && !wasBrowsing) session_++
        // Entering, leaving or switching the pair (or Chain view's control row) drops partial cursor travel and the touched knob (it named a row of the old layout).
        if (pair != lastPair || chain != lastChain) {
            if (now) session_++
            PerformSurface.lastTouchedKnob = null
            lastPair = pair
            lastChain = chain
        }
        wasBrowsing = now
    }

    private companion object {
        /** Two rows of four knobs. */
        const val PAIR_LIVE_KNOBS = 8
        /** Chain view's load row is the last row: knobs 13-15 load, knob 16 is the cursor. */
        const val CHAIN_LOAD_FIRST_KNOB = PerfRows.CHAIN_CONTROL_ROW * 4
        var session_ = 0
        var wasBrowsing = false
        var lastPair: String? = null
        var lastChain: ParametersState.ChainViewState? = null
    }

    override fun button(index: Int, shifted: Boolean) {
        if (confirming) {
            if (shifted) return
            when (index) {
                0 -> deckConfirm?.answerDeckConfirm(DeckConfirmChoice.CANCEL)
                1 -> deckConfirm?.answerDeckConfirm(DeckConfirmChoice.SAVE)
                2 -> deckConfirm?.answerDeckConfirm(DeckConfirmChoice.DISCARD)
            }
            return
        }
        when {
            inLibraryView -> libraryButton(index, shifted)
            chainState != null -> chainButton(index, shifted)
            inPicker -> pickerButton(index, shifted)
            else -> performButton(index, shifted)
        }
    }

    private fun pickerButton(index: Int, shifted: Boolean) {
        // Like the Library: side 2 steps the tab (shift: back a tab), side 3 the panes (tree > list); shift + side 3 clears the slot or chain instead.
        when (index) {
            0 -> if (!shifted) back()
            1 -> LibraryNavigation.stepTab(if (shifted) -1 else 1)
            2 -> if (shifted) BrowserPane.hosted()?.clear?.invoke() else LibraryNavigation.stepPane(1, session, mixer)
        }
    }

    private fun chainButton(index: Int, shifted: Boolean) {
        val loading = chainLoading
        when (index) {
            0 -> if (!shifted) back()
            1 -> when {
                !loading -> { openLibrary(); if (shifted) LibraryNavigation.setViewMode(LibraryPanel.LibraryViewMode.FX) }
                shifted -> LibraryNavigation.stepTab(1)
                else -> LibraryNavigation.stepPane(1, session, mixer)
            }
            2 -> if (shifted) { if (loading) BrowserPane.hosted()?.clear?.invoke() } else parametersState.toggleChainBrowse()
        }
    }

    private fun libraryButton(index: Int, shifted: Boolean) {
        val dir = if (shifted) -1 else 1
        when (index) {
            0 -> if (shifted) LibraryNavigation.enqueue(session, bg = true) else back()
            1 -> LibraryNavigation.stepTab(dir)
            2 -> LibraryNavigation.stepPane(dir, session, mixer)
        }
    }

    private fun performButton(index: Int, shifted: Boolean) {
        when (index) {
            0 -> if (shifted) toggleEditMode() else back()
            1 -> { openLibrary(); if (shifted) LibraryNavigation.setViewMode(LibraryPanel.LibraryViewMode.FX) }
            2 -> if (shifted) togglePairFocus() else openPicker()
        }
    }

    /** Toggles Edit mode (Parameters Edit Bay) for the row touched last. */
    private fun toggleEditMode() {
        if (parametersState.anyRackModuleExpanded()) {
            parametersState.collapseAllRackModules()
            return
        }
        val knob = PerformSurface.lastTouchedKnob
        val target = knob?.let { PerformPages.resolve(theme.performancePageId, ctx, parametersState, mixer).knobs.getOrNull(it) }
        val bankId = target?.bankId ?: parametersState.focusedPair?.let { PerfRows.pairFor(it)?.moduleId } ?: MacroEngine.DECK_A
        val moduleId = ctx.canonicalModuleId(bankId)
        parametersState.openParams(moduleId)
    }

    /** Toggles Pair view focus for the row touched last. */
    private fun togglePairFocus() {
        val knob = PerformSurface.lastTouchedKnob
        val target = knob?.let { PerformPages.resolve(theme.performancePageId, ctx, parametersState, mixer).knobs.getOrNull(it) }
        val bankId = target?.bankId ?: return
        // Master MST and Clock have no pair to browse (the Master pair is Transitions + Master FX): nothing to focus.
        val pairTag = PerfRows.pairForBank(bankId)?.tag ?: return
        if (parametersState.focusedPair == pairTag && !inPicker) {
            parametersState.leavePair()
        } else {
            parametersState.focusPair(pairTag)
        }
    }

    /** Opens the picker for the row of the knob touched last (see [PerformSurface.lastTouchedKnob]). */
    private fun openPicker() {
        val knob = PerformSurface.lastTouchedKnob ?: return
        val target = PerformPages.resolve(theme.performancePageId, ctx, parametersState, mixer).knobs.getOrNull(knob) ?: return
        val bankId = target.bankId
        val deckLabel = ctx.deckLabelForModuleId(bankId)
        when {
            FxMacroSync.isFxBank(bankId) -> {
                val chain = FxMacroSync.chainFor(bankId, mixer) ?: return
                val slot = (target.spec.under as? UnderKnob.SlotCell)?.slotIndex ?: FxMacroSync.slotBankTarget(bankId)?.second ?: chain.focusedSlot
                parametersState.openFxChainBrowse(ctx.canonicalModuleId(bankId), deckLabel, slot)
            }
            bankId == MacroEngine.TRANS -> parametersState.openTransitionBrowse()
            deckLabel != null -> parametersState.openGenBrowse(bankId, deckLabel)
        }
    }

    /**
     * The single back action for both the controller's back button and the Esc key: undo the innermost thing
     * on the [BackNavigation] stack; in Library FULL with nothing left to undo, drop to Library HALF.
     * Returns true if anything changed.
     */
    fun back(): Boolean {
        if (confirming) {
            deckConfirm?.answerDeckConfirm(DeckConfirmChoice.CANCEL)
            return true
        }
        if (BackNavigation.back(parametersState, mixer)) return true
        if (!inLibraryView) return false
        theme.libraryMode = UITheme.LibraryMode.HALF
        AppPreferencesStore.savePreferences()
        return true
    }

    private fun openLibrary() {
        LibraryPanel.show(session)
        theme.libraryMode = UITheme.LibraryMode.FULL
        AppPreferencesStore.savePreferences()
    }

    // Chain view has no sends: every load goes into one of its own slots, and knobs 9-13 are slot rows there.
    override val sendTargets: Set<SendTarget> get() =
        if ((inLibraryView || inPicker) && chainState == null && !confirming) LibraryNavigation.sendTargets() else emptySet()

    /** With a bound dock every send knob (decks, Master) is also a quick switch to that pair (in place when its row is on the page). */
    override val switchTargets: Set<SendTarget> get() = if (inPicker && chainState == null && !confirming) SendTarget.entries.toSet() else emptySet()

    override fun browseSwitch(target: SendTarget) {
        if (inPicker && !confirming) parametersState.switchBrowseTo(target.name)
    }

    override fun browseSend(target: SendTarget) {
        if ((inLibraryView || inPicker) && !confirming) LibraryNavigation.send(target, session, mixer, parametersState)
    }

    override fun browseStep(steps: Int) {
        if (confirming) return
        if (inLibraryView || inPicker) LibraryNavigation.step(steps, session, mixer)
    }

    override fun browseAccept(shifted: Boolean) {
        if (confirming) {
            deckConfirm?.answerDeckConfirm(if (shifted) DeckConfirmChoice.DISCARD else DeckConfirmChoice.SAVE)
            return
        }
        if (!inLibraryView && !inPicker) return
        if (!shifted) LibraryNavigation.accept(session, mixer, parametersState)
        // Library view: after the cursor of the queue knob touched last; none touched, the end of the A-side queue as before.
        else if (!(inLibraryView && LibraryNavigation.enqueueAtQueueKnob(session, browseSession))) LibraryNavigation.enqueue(session, bg = false)
    }
}

/** Whether the Esc key should run [NavigationSurface.back] this frame: pressed, and no text field has keyboard focus. */
internal fun shouldHandleEscape(wantTextInput: Boolean, escPressed: Boolean): Boolean = escPressed && !wantTextInput
