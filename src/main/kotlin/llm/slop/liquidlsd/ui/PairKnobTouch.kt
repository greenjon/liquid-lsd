package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

/**
 * With a bound dock (the pair view, or the Perform dock bound in place), touching one of the live knobs (1-4 the first row, 5-8 the
 * second) points the Browse list at that half: a source row browses sources, an FX row browses the slot under the knob (the chain for
 * the row's name knob), Transitions the transition. A half with nothing to browse (Master MST, Clock) leaves the binding alone.
 * Called every frame outside Edit, so a touch made while nothing was bound never retargets a binding made later.
 */
internal object PairKnobTouch {
    private var seen: Int? = null

    /** Retargets the Browse to the half of the knob touched since the last call. Cheap when nothing was touched. */
    fun sync(ctx: PerformanceUiContext, parametersState: ParametersState, mixer: Mixer, pageId: String) {
        val knob = PerformSurface.lastTouchedKnob
        if (knob == seen) return
        seen = knob
        val tag = parametersState.focusedPair
        if (tag == null && !parametersState.boundInPlace) return
        // Chain view: all four rows are live (its load row has no knobs to touch).
        val chainRows = tag?.let { t -> parametersState.chainView?.let { PerfRows.chainRowsFor(t, it) } }
        if (knob == null || knob >= (chainRows?.size ?: 2) * COLS) return
        val target = PerformPages.resolve(pageId, ctx, parametersState, mixer).knobs.getOrNull(knob)
        // The pair view's rows are the pair's; bound in place they are the page's, which the resolved knob names.
        val bankId = when {
            chainRows != null -> chainRows[knob / COLS].takeIf { !it.loadRow }?.bankId ?: return
            tag != null -> {
                val pair = PerfRows.pairFor(tag) ?: return
                PerfRows.CATALOG[pair.rowIds.getOrNull(knob / COLS) ?: return]?.bankId ?: return
            }
            else -> target?.bankId ?: return
        }
        val browse = when {
            FxMacroSync.isFxBank(bankId) -> {
                val chain = FxMacroSync.chainFor(bankId, mixer)
                val slot = (target?.spec?.under as? UnderKnob.SlotCell)?.slotIndex ?: FxMacroSync.slotBankTarget(bankId)?.second ?: chain?.focusedSlot
                ParametersState.BrowseTarget.FxChain(slot)
            }
            bankId == MacroEngine.TRANS -> ParametersState.BrowseTarget.Transition
            PerfRows.DECK_TAGS.any { PerfRows.CATALOG["deck.$it.src"]?.bankId == bankId } -> ParametersState.BrowseTarget.Gen
            else -> return
        }
        // No retab: a knob touch only moves the tab when the target changes, so it never pulls the user off Queues or Macros.
        parametersState.openBrowse(ctx.canonicalModuleId(bankId), browse, retab = false)
    }

    private const val COLS = 4
}
