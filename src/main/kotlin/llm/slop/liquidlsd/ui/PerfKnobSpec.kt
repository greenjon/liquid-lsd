package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.FxChain
import java.util.Locale

/**
 * What one Performance-row knob *shows*, independent of where it's drawn. [PerformanceMatrixPanel]
 * places every knob, its under-knob strip and its side buttons from [PerfRowGeometry], which never
 * looks at the row's mode -- the mode only changes the [KnobSpec] content resolved here. See
 * docs/developer/ui.md ("Performance row layout contract").
 */
internal sealed interface UnderKnob {
    /** The macro's own caption (source/mix rows, and an FX row's Super Knob in group mode). */
    data class Label(val text: String) : UnderKnob
    /** An FX slot's cell ([FxSlotCell]): group-mode knobs 2-4, or focus mode's knob 1 (the focused slot's name and bypass; its Metaknob value). */
    data class SlotCell(val slotIndex: Int) : UnderKnob
    /** A focused slot's parameter cell ([FxParamCell]); null name/param = blank knob on this page. */
    data class ParamCell(val name: String?, val param: ModulatableParameter?) : UnderKnob
}

/** The small buttons stacked left of a knob. */
internal sealed interface SideButtons {
    data object None : SideButtons
    /** Group mode, knobs 2-4: Super Knob link over the slot's bypass. */
    data class LinkAndBypass(val slotIndex: Int) : SideButtons
    /** Focus mode, knob 1: the focused slot's bypass. */
    data class Bypass(val slotIndex: Int) : SideButtons
    /** Focus mode, knobs 2-4: the parameter's reset-to-default. */
    data class Reset(val name: String, val param: ModulatableParameter) : SideButtons
}

internal data class KnobSpec(
    val col: Int,
    val knobIndex: Int,
    val control: MacroControl,
    val under: UnderKnob,
    val side: SideButtons,
    /** Drawn inside the knob face (focus mode's parameter values), keeping the strip for the name. */
    val valueOverlay: String? = null,
    val meterType: MeterType = MeterType.MONOPOLAR
)

/** The slice of an [FxChain]'s state the resolver needs -- a plain value so tests needn't build GL filters. */
internal data class FxRowState(
    val focusedSlot: Int?,
    val focusParamPage: Int,
    /** The focused slot's parameters in authored order; empty when not focused or the slot is empty. */
    val focusedParams: List<Pair<String, ModulatableParameter>>
) {
    companion object {
        fun of(chain: FxChain): FxRowState {
            val slot = chain.focusedSlot
            val params = slot?.let { chain.slots.getOrNull(it) }?.parameters?.map { it.key to it.value } ?: emptyList()
            return FxRowState(slot, chain.focusParamPage, params)
        }
    }
}

internal object PerfKnobResolver {

    /**
     * Resolves the 4 knobs (cols 0-3, from [knobOffset]) of a row driven by [bank]. [fx] is non-null
     * only for rows whose knobs drive an FX chain *and* carry slot cells (deck/Master rows in FX mode).
     * Columns without a control in [bank] are omitted.
     */
    fun resolve(bank: MacroBank, knobOffset: Int, fx: FxRowState?): List<KnobSpec> =
        (0 until 4).mapNotNull { col ->
            val knobIdx = knobOffset + col
            val control = bank.knobs.getOrNull(knobIdx) ?: return@mapNotNull null
            val label = UnderKnob.Label(control.label.ifEmpty { "K${knobIdx + 1}" })
            when {
                fx == null -> KnobSpec(col, knobIdx, control, label, SideButtons.None)
                fx.focusedSlot != null -> focusSpec(col, knobIdx, control, fx, fx.focusedSlot)
                col == 0 -> KnobSpec(col, knobIdx, control, label, SideButtons.None)
                else -> KnobSpec(col, knobIdx, control, UnderKnob.SlotCell(col - 1), SideButtons.LinkAndBypass(col - 1))
            }
        }

    private fun focusSpec(col: Int, knobIdx: Int, control: MacroControl, fx: FxRowState, focusedSlot: Int): KnobSpec {
        if (col == 0) return KnobSpec(col, knobIdx, control, UnderKnob.SlotCell(focusedSlot), SideButtons.Bypass(focusedSlot), meterType = MeterType.MONOPOLAR)
        val entry = fx.focusedParams.getOrNull(fx.focusParamPage * 3 + (col - 1))
            ?: return KnobSpec(col, knobIdx, control, UnderKnob.ParamCell(null, null), SideButtons.None)
        val (name, param) = entry
        return KnobSpec(
            col = col,
            knobIndex = knobIdx,
            control = control,
            under = UnderKnob.ParamCell(name, param),
            side = SideButtons.Reset(name, param),
            valueOverlay = formatValue(param.baseValue),
            meterType = param.meterType
        )
    }

    /** Integers as-is, everything else to 2 decimals -- short enough for the knob face. */
    fun formatValue(v: Float): String =
        if (v == v.toInt().toFloat() && kotlin.math.abs(v) < 1000f) v.toInt().toString()
        else String.format(Locale.ROOT, "%.2f", v)
}
