package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.control.KnobCommands
import llm.slop.liquidlsd.control.KnobSurface
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.Mixer

/** One addressable knob of the Perform view: its row's bank plus what the knob currently shows. */
internal data class PageKnob(val bankId: String, val spec: KnobSpec) {
    val control: MacroControl get() = spec.control
}

/** The 16 knobs (row-major over the visible rows) that a hardware controller addresses; null where a row has no knob. */
internal class PerformPage(val knobs: List<PageKnob?>)

internal object PerformPages {
    private const val COLS = 4

    /**
     * Resolves the page the way [PerformanceMatrixPanel] draws it: the rows [PerfRows.visibleRowsForTab]
     * returns (the tab's four rows, or the open module's row in Deep Edit), each through [PerfKnobResolver].
     */
    fun resolve(tabIdx: Int, ctx: PerformanceUiContext, parametersState: ParametersState, mixer: Mixer): PerformPage {
        val rows = PerfRows.visibleRowsForTab(tabIdx.coerceIn(0, PerfRows.TAB_ROWS.size - 1), ctx, parametersState) { it }
        val knobs = arrayOfNulls<PageKnob>(KnobCommands.KNOB_COUNT)
        for ((rowIdx, row) in rows.take(KnobCommands.KNOB_COUNT / COLS).withIndex()) {
            val bank = MacroEngine.getBank(row.bankId) ?: MacroEngine.bankForParamPath(row.bankId)
            val isFxBank = row.bankId in FxMacroSync.FX_BANK_IDS
            val chain = if (isFxBank && row.hasExtraHeader) ctx.resolveFxChain(mixer, row.bankId) else null
            for (spec in PerfKnobResolver.resolve(bank, row.knobOffset, chain?.let { FxRowState.of(it) })) {
                knobs[rowIdx * COLS + spec.col] = PageKnob(row.bankId, spec)
            }
        }
        return PerformPage(knobs.toList())
    }
}

/**
 * Applies hardware knob gestures to the live Perform view. Each call re-resolves the page, so a row
 * flipping between SRC/FX or into FX focus retargets the same physical knob immediately.
 */
internal class PerformSurface(
    private val theme: UITheme,
    private val ctx: PerformanceUiContext,
    private val parametersState: ParametersState,
    private val mixer: Mixer
) : KnobSurface {

    private fun knob(index: Int): PageKnob? =
        PerformPages.resolve(theme.performanceMatrixTab, ctx, parametersState, mixer).knobs.getOrNull(index)

    override fun turn(knob: Int, delta: Float) {
        val control = knob(knob)?.control ?: return
        control.value = (control.value + delta).coerceIn(0f, 1f)
    }

    override fun primary(knob: Int) {
        val target = knob(knob) ?: return
        when (val under = target.spec.under) {
            is UnderKnob.SlotCell -> toggleBypass(target.bankId, under.slotIndex)
            is UnderKnob.ParamCell -> under.param?.let { resetParameter(target.control, it) }
            is UnderKnob.Label -> target.control.value = LABEL_KNOB_DEFAULT
        }
    }

    override fun secondary(knob: Int) {
        val target = knob(knob) ?: return
        when (val under = target.spec.under) {
            is UnderKnob.SlotCell -> {
                // Group mode: knobs 2-4 focus their slot. Focus mode: knob 1 leaves focus.
                val leaving = target.spec.side is SideButtons.Bypass
                FxMacroSync.focusSlot(target.bankId, mixer, if (leaving) null else under.slotIndex)
            }
            is UnderKnob.ParamCell -> if (under.param != null) FxMacroSync.stepParamPage(target.bankId, mixer, +1)
            is UnderKnob.Label -> Unit
        }
    }

    override fun showPage(pageId: String) {
        when (pageId) {
            PAGE_DECKS -> theme.performanceMatrixTab = PerfRows.TAB_DECKS
            PAGE_MASTER -> theme.performanceMatrixTab = PerfRows.TAB_MASTER
        }
    }

    private fun toggleBypass(bankId: String, slotIndex: Int) {
        val chain = FxMacroSync.chainFor(bankId, mixer) ?: return
        val slot = chain.slots.getOrNull(slotIndex) ?: return
        FxOps.setSlotEnabled(chain, slotIndex, !slot.enabled)
    }

    /** Same as the focus-mode reset button: the parameter returns to its default and the knob follows. */
    private fun resetParameter(control: MacroControl, param: ModulatableParameter) {
        param.baseValue = param.defaultValue
        val range = param.maxClamp - param.minClamp
        control.value = if (range > 0f) ((param.baseValue - param.minClamp) / range).coerceIn(0f, 1f) else 0f
    }

    companion object {
        const val PAGE_DECKS = "perform.decks"
        const val PAGE_MASTER = "perform.master"

        /** What a mouse middle-click does on these knobs (the matrix passes 0.5 as every macro's default). */
        const val LABEL_KNOB_DEFAULT = 0.5f
    }
}
