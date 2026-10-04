package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.control.NavSurface
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Applies controller navigation to the live UI. The three side buttons mean different things per context:
 *  - Library view (Library FULL): back, next tab, next list; knob 1 is the cursor.
 *    With shift: enqueue to the BG queue, previous tab, previous list.
 *  - Picker (an SRC / FX / transition list is showing in the Edit row): left-top = back, right-top = next
 *    category (shift: previous), shift + right-bottom = clear the slot or chain; knob 1 is the cursor (tap = apply).
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

    private val inLibraryView: Boolean
        get() = theme.libraryMode == UITheme.LibraryMode.FULL && !LibraryPanel.isEditView(session)

    private val inPicker: Boolean
        get() = LibraryPanel.isEditView(session) && (ChainListBrowse.isShowing || ShaderPickerPopup.isShowing)

    private val confirming: Boolean get() = deckConfirm?.deckConfirmPending == true

    /** While the dirty-deck modal is up, knob 1 and the side buttons answer it (see [DeckConfirmChoice]). */
    override val browsing: Boolean get() = confirming || inLibraryView || inPicker

    // The surface is rebuilt every frame, so the session counter lives in the companion; sampling on
    // construction and on read catches every inactive -> active edge.
    init { sampleBrowsing() }

    override val browseSession: Int get() { sampleBrowsing(); return session_ }

    private fun sampleBrowsing() {
        val now = browsing
        if (now && !wasBrowsing) session_++
        wasBrowsing = now
    }

    private companion object {
        var session_ = 0
        var wasBrowsing = false
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
        when (index) {
            0 -> if (!shifted) back()
            1 -> if (!ChainListBrowse.isShowing) ShaderPickerPopup.stepCategory(if (shifted) -1 else 1)
            2 -> if (shifted) {
                if (ChainListBrowse.isShowing) ChainListBrowse.clear() else if (ShaderPickerPopup.canDetach) ShaderPickerPopup.detach()
            }
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

    override fun browseStep(steps: Int) {
        if (confirming) return
        when {
            inLibraryView -> LibraryNavigation.step(steps, session, mixer)
            ChainListBrowse.isShowing -> ChainListBrowse.move(steps)
            else -> ShaderPickerPopup.moveCursor(steps)
        }
    }

    override fun browseAccept(shifted: Boolean) {
        if (confirming) {
            deckConfirm?.answerDeckConfirm(if (shifted) DeckConfirmChoice.DISCARD else DeckConfirmChoice.SAVE)
            return
        }
        if (!inLibraryView) {
            if (!shifted) {
                if (ChainListBrowse.isShowing) ChainListBrowse.accept() else ShaderPickerPopup.acceptCursor()
            }
            return
        }
        if (shifted) LibraryNavigation.enqueue(session, bg = false) else LibraryNavigation.accept(session, mixer, parametersState)
    }
}

/** Whether the Esc key should run [NavigationSurface.back] this frame: pressed, and no text field has keyboard focus. */
internal fun shouldHandleEscape(wantTextInput: Boolean, escPressed: Boolean): Boolean = escPressed && !wantTextInput

/** Wall clock for the "is a picker on screen" windows; tests replace it to advance time without sleeping. */
internal object UiClock {
    @Volatile var nowMs: () -> Long = { System.currentTimeMillis() }
}
