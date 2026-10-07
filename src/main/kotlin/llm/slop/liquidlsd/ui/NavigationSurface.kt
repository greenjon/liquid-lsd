package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.control.NavSurface
import llm.slop.liquidlsd.control.SendTarget
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.browser.BrowserDock
import llm.slop.liquidlsd.ui.browser.BrowserPane

/**
 * Applies controller navigation to the live UI. The three side buttons mean different things per context:
 *  - Library view (Library FULL): back, next tab, next list; the cursor is knob 16. Tapping a send knob (9-12 = A, B, BG, PV;
 *    13 = master FX) sends the cursor item there and opens that row in the picker, so it also works in the picker.
 *    With shift: enqueue to the BG queue, previous tab, previous list.
 *  - Pair view (the unified pane under the focused pair's two rows): left-top = back, right-top = next pane
 *    (tree > list > queues; shift: previous), shift + right-bottom = clear the slot or chain; the cursor is knob 16
 *    (tap = apply, and a tap on a tree row selects the scope and moves the cursor into the list). Shift + tap on a send knob (9-13)
 *    switches to that deck's (or Master's) pair without sending.
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

    /** The pair view is up: its two rows over the Browse dock. */
    private val inPicker: Boolean get() = viewStateOf(session).pair

    private val confirming: Boolean get() = deckConfirm?.deckConfirmPending == true

    /** While the dirty-deck modal is up, the cursor knob and the side buttons answer it (see [DeckConfirmChoice]). */
    override val browsing: Boolean get() = confirming || inLibraryView || inPicker

    /** In the pair view its two rows are the only rows on screen, so Twister rows one and two (knobs 1-8) drive them. */
    override val browseLiveKnobs: Int get() = if (inPicker && !confirming) PAIR_LIVE_KNOBS else 0

    // The surface is rebuilt every frame, so the session counter lives in the companion; sampling on
    // construction and on read catches every inactive -> active edge.
    init { sampleBrowsing() }

    override val browseSession: Int get() { sampleBrowsing(); return session_ }

    private fun sampleBrowsing() {
        val now = browsing
        val pair = parametersState.focusedPair
        if (now && !wasBrowsing) session_++
        // Entering, leaving or switching the pair drops partial cursor travel and the touched knob (it named a row of the old layout).
        if (pair != lastPair) {
            if (now) session_++
            PerformSurface.lastTouchedKnob = null
            lastPair = pair
        }
        wasBrowsing = now
    }

    private companion object {
        /** Two rows of four knobs. */
        const val PAIR_LIVE_KNOBS = 8
        var session_ = 0
        var wasBrowsing = false
        var lastPair: String? = null
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
            inPicker -> pickerButton(index, shifted)
            else -> performButton(index, shifted)
        }
    }

    private fun pickerButton(index: Int, shifted: Boolean) {
        // The unified pane is in the bay: side 2 steps its panes (tree > list > queues), shift + side 3 clears the slot or chain.
        val hosted = BrowserPane.hosted()
        when (index) {
            0 -> if (!shifted) back()
            1 -> LibraryNavigation.stepPane(if (shifted) -1 else 1, session, mixer)
            2 -> if (shifted) hosted?.clear?.invoke()
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
        if (shifted) return
        when (index) {
            0 -> back()
            1 -> openLibrary()
            2 -> openPicker()
        }
    }

    /** Opens the picker for the row of the knob touched last (see [PerformSurface.lastTouchedKnob]). */
    private fun openPicker() {
        val knob = PerformSurface.lastTouchedKnob ?: return
        val target = PerformPages.resolve(theme.performancePageId, ctx, parametersState, mixer).knobs.getOrNull(knob) ?: return
        val bankId = target.bankId
        val deckLabel = ctx.deckLabelForModuleId(bankId)
        when {
            bankId in FxMacroSync.FX_BANK_IDS -> {
                val chain = FxMacroSync.chainFor(bankId, mixer) ?: return
                val slot = (target.spec.under as? UnderKnob.SlotCell)?.slotIndex ?: chain.focusedSlot
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

    override val sendTargets: Set<SendTarget> get() = if ((inLibraryView || inPicker) && !confirming) LibraryNavigation.sendTargets() else emptySet()

    /** In the pair view every send knob (decks, Master) is also a quick switch to that pair. */
    override val switchTargets: Set<SendTarget> get() = if (inPicker && !confirming) SendTarget.entries.toSet() else emptySet()

    override fun browseSwitch(target: SendTarget) {
        if (inPicker && !confirming) parametersState.focusPair(target.name)
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
        if (inPicker) {
            if (!shifted) LibraryNavigation.accept(session, mixer, parametersState)
            return
        }
        if (!inLibraryView) return
        if (shifted) LibraryNavigation.enqueue(session, bg = false) else LibraryNavigation.accept(session, mixer, parametersState)
    }
}

/** Whether the Esc key should run [NavigationSurface.back] this frame: pressed, and no text field has keyboard focus. */
internal fun shouldHandleEscape(wantTextInput: Boolean, escPressed: Boolean): Boolean = escPressed && !wantTextInput
