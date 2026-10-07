package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

/**
 * In the pair view, touching one of the live knobs (1-4 the first row, 5-8 the second) points the Browse list at that half:
 * a source row browses sources, an FX row browses the slot under the knob (the chain for the row's name knob), Transitions the
 * transition. A half with nothing to browse (Master MIX, Clock) leaves the binding alone.
 */
internal object PairKnobTouch {
    private var seen: Int? = null

    /** Retargets the Browse to the half of the knob touched since the last call. Cheap when nothing was touched. */
    fun sync(ctx: PerformanceUiContext, parametersState: ParametersState, mixer: Mixer, pageId: String) {
        val knob = PerformSurface.lastTouchedKnob
        if (knob == seen) return
        seen = knob
        val tag = parametersState.focusedPair ?: return
        if (knob == null || knob >= 2 * COLS) return
        val pair = PerfRows.pairFor(tag) ?: return
        val row = PerfRows.CATALOG[pair.rowIds.getOrNull(knob / COLS) ?: return] ?: return
        val target = PerformPages.resolve(pageId, ctx, parametersState, mixer).knobs.getOrNull(knob)
        val browse = when {
            row.bankId in FxMacroSync.FX_BANK_IDS -> {
                val chain = FxMacroSync.chainFor(row.bankId, mixer)
                val slot = (target?.spec?.under as? UnderKnob.SlotCell)?.slotIndex ?: chain?.focusedSlot
                ParametersState.BrowseTarget.FxChain(slot)
            }
            row.bankId == MacroEngine.TRANS -> ParametersState.BrowseTarget.Transition
            PerfRows.DECK_TAGS.any { PerfRows.CATALOG["deck.$it.src"]?.bankId == row.bankId } -> ParametersState.BrowseTarget.Gen
            else -> return
        }
        parametersState.openBrowse(ctx.canonicalModuleId(row.bankId), browse)
    }

    private const val COLS = 4
}
