package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.control.KnobCommands
import llm.slop.liquidlsd.control.KnobLight
import llm.slop.liquidlsd.control.KnobLightSource
import llm.slop.liquidlsd.control.KnobSurface
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.Mixer

/** One addressable knob of the Perform view: its row's bank plus what the knob currently shows. */
internal data class PageKnob(val bankId: String, val spec: KnobSpec, val accent: FloatArray) {
    val control: MacroControl get() = spec.control
}

/** The 16 knobs (row-major over the visible rows) that a hardware controller addresses; null where a row has no knob. */
internal class PerformPage(val knobs: List<PageKnob?>)

internal object PerformPages {
    private const val COLS = 4

    /** The LED colour for [row]: its accent, except the greyscale Master and Global rows (see [PerformanceColors.LED_MASTER]). */
    fun ledColor(row: RowDescriptor): FloatArray {
        val base = when (row.bankId) {
            MacroEngine.MASTER, MacroEngine.MASTER_FX -> PerformanceColors.LED_MASTER
            MacroEngine.GLOBAL -> PerformanceColors.LED_GLOBAL
            else -> row.accent
        }
        // A pinned FX row sits beside its deck's (or Master's) SRC/MIX row on one page; a hue shift tells them apart on the LEDs.
        if (row.pinnedMode != "FX") return base
        val shift = if (row.bankId == MacroEngine.MASTER_FX) -FX_HUE_SHIFT else FX_HUE_SHIFT
        return rotateHue(base, shift)
    }

    /** How far a pinned FX row's LED hue moves from its SRC row's (degrees); Master goes the other way to stay clear of Wet/Dry. */
    private const val FX_HUE_SHIFT = 30f

    private fun rotateHue(rgb: FloatArray, degrees: Float): FloatArray {
        val hsb = java.awt.Color.RGBtoHSB((rgb[0] * 255f).toInt(), (rgb[1] * 255f).toInt(), (rgb[2] * 255f).toInt(), null)
        val h = ((hsb[0] + degrees / 360f) % 1f + 1f) % 1f
        val c = java.awt.Color(java.awt.Color.HSBtoRGB(h, hsb[1], hsb[2]))
        return floatArrayOf(c.red / 255f, c.green / 255f, c.blue / 255f)
    }

    /**
     * Resolves the page the way [PerformanceMatrixPanel] draws it: the rows [PerfRows.visibleRowsForPage]
     * returns (the page's four rows, or the open module's row in Deep Edit), each through [PerfKnobResolver].
     */
    fun resolve(pageId: String, ctx: PerformanceUiContext, parametersState: ParametersState, mixer: Mixer): PerformPage {
        val pages = PerfPageStore.default.all()
        val page = pages.firstOrNull { it.id == pageId } ?: pages.first()
        val rows = PerfRows.visibleRowsForPage(page, ctx, parametersState, { it }, pages)
        val knobs = arrayOfNulls<PageKnob>(KnobCommands.KNOB_COUNT)
        for ((rowIdx, row) in rows.take(KnobCommands.KNOB_COUNT / COLS).withIndex()) {
            val bank = MacroEngine.getBank(row.bankId) ?: MacroEngine.bankForParamPath(row.bankId)
            val isFxBank = row.bankId in FxMacroSync.FX_BANK_IDS
            val chain = if (isFxBank && row.hasExtraHeader) ctx.resolveFxChain(mixer, row.bankId) else null
            for (spec in PerfKnobResolver.resolve(bank, row.knobOffset, chain?.let { FxRowState.of(it) })) {
                knobs[rowIdx * COLS + spec.col] = PageKnob(row.bankId, spec, ledColor(row))
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
) : KnobSurface, KnobLightSource {

    private fun knob(index: Int): PageKnob? =
        PerformPages.resolve(theme.performancePageId, ctx, parametersState, mixer).knobs.getOrNull(index)

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
        // `perform.<id>` names a page; the legacy `perform.decks` / `perform.master` are the built-ins' ids.
        val id = pageId.removePrefix("perform.")
        if (PerfPageStore.default.get(id) != null) theme.performancePageId = id
    }

    /**
     * Ring = the knob's value; LED = its row's accent colour, dark where nothing is there to
     * control (an empty or bypassed FX slot, a blank parameter page position).
     */
    override fun knobLights(): List<KnobLight?> =
        PerformPages.resolve(theme.performancePageId, ctx, parametersState, mixer).knobs.map { target ->
            target?.let { KnobLight(it.control.value, it.accent[0], it.accent[1], it.accent[2], lit = isLit(it)) }
        }

    private fun isLit(target: PageKnob): Boolean = when (val under = target.spec.under) {
        is UnderKnob.SlotCell -> FxMacroSync.chainFor(target.bankId, mixer)?.slots?.getOrNull(under.slotIndex)?.enabled == true
        is UnderKnob.ParamCell -> under.param != null
        is UnderKnob.Label -> true
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
