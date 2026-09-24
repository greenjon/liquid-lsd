package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.rendering.FxBank
import llm.slop.liquidlsd.rendering.FxChain

/**
 * Smart-default bridge from an [FxBank]'s active chain to its [MacroBank]: Knob 1 becomes the
 * chain's Super Knob, Knobs 2-4 become each slot's Metaknob. Runs whenever the focused bank or
 * its active chain changes, so the Performance Console's generic 4-knob row (and Column 3's
 * MACROS view, which reads the same bank) show a sensible default without the user hand-wiring
 * bindings.
 *
 * Ownership rule: a knob is only overwritten if it's unbound or its current primary binding
 * already matches this sync's own path pattern for the *same* bank -- a knob the user manually
 * retargeted to something else (e.g. "deckA/Warp") is left alone on every future focus change.
 * [forceResync] bypasses that check for the row header's explicit "Resync to Chain" action.
 *
 * Mutually exclusive with [FxChain]'s Link checkbox: both would otherwise write
 * [llm.slop.liquidlsd.rendering.isf.ISFFilter.metaKnob]'s base value every frame (one via this
 * binding, one via [FxChain.update]'s soft-takeover propagation). A linked slot is skipped here
 * entirely (no binding, label shows "(linked)") -- see [llm.slop.liquidlsd.ui.FXChainMacroStrip]
 * for the reverse: enabling Link there clears this sync's binding for that slot.
 */
object FxMacroSync {

    private val OWNED_PATH_PATTERN = Regex("""^([^/]+)/(?:C\d+|FX)/(Super|FX\d+/Meta)$""")

    /** Re-syncs [bankId]'s [MacroBank] knobs 0-3 to [bank]'s active chain's Super Knob + Metaknobs. */
    fun sync(bankId: String, bank: FxBank, forceResync: Boolean = false) {
        syncChain(bankId, bank.label, bank.activeChain, bank.activeChainIndex, forceResync)
    }

    /**
     * Re-syncs [bankId]'s [MacroBank] knobs 0-3 to a deck's dedicated [FxChain].
     * Knob 0 becomes the chain's Super Knob, Knobs 1-3 become each slot's Metaknob.
     */
    fun syncDeckFx(bankId: String, deckLabel: String, chain: FxChain, forceResync: Boolean = false) {
        val macroBank = MacroEngine.getBank(bankId) ?: MacroEngine.newBankFor(bankId).also {
            MacroEngine.registerBank(bankId, it)
        }

        syncKnob(
            macroBank = macroBank,
            knobIndex = 0,
            bankLabel = deckLabel,
            defaultLabel = "SUPER",
            targetPath = "$deckLabel/FX/Super",
            initialValue = chain.superKnob.baseValue,
            forceResync = forceResync
        )

        for (slotIdx in 0 until FxChain.SLOT_COUNT) {
            val knobIndex = slotIdx + 1
            val slot = chain.slots.getOrNull(slotIdx)
            val label = slot?.displayName?.takeIf { it.isNotBlank() } ?: "FX$knobIndex"
            if (chain.slotSuperKnobLink.getOrNull(slotIdx) == true) {
                clearOwnedBinding(macroBank, knobIndex, deckLabel)
                macroBank.knobs.getOrNull(knobIndex)?.let { if (isOwnedOrEmpty(it, deckLabel)) it.label = label }
                continue
            }
            syncKnob(
                macroBank = macroBank,
                knobIndex = knobIndex,
                bankLabel = deckLabel,
                defaultLabel = label,
                targetPath = "$deckLabel/FX/FX$knobIndex/Meta",
                initialValue = slot?.metaKnob?.baseValue ?: 0f,
                forceResync = forceResync
            )
        }

        MacroEngine.invalidate()
    }

    /**
     * Re-syncs [bankId]'s [MacroBank] knobs 0-3 to [chain] (at [chainIndex] within [bankLabel]'s
     * bank). Split out from [sync] so callers that already have the specific [FxChain] in hand
     * (e.g. [llm.slop.liquidlsd.ui.FXChainMacroStrip] reacting to a Link checkbox toggle) don't
     * need to thread the whole [FxBank] through just to re-derive it.
     */
    fun syncChain(bankId: String, bankLabel: String, chain: FxChain, chainIndex: Int, forceResync: Boolean = false) {
        val macroBank = MacroEngine.getBank(bankId) ?: MacroEngine.newBankFor(bankId).also {
            MacroEngine.registerBank(bankId, it)
        }
        val chainNum = chainIndex + 1

        syncKnob(
            macroBank = macroBank,
            knobIndex = 0,
            bankLabel = bankLabel,
            defaultLabel = "SUPER",
            targetPath = "$bankLabel/C$chainNum/Super",
            initialValue = chain.superKnob.baseValue,
            forceResync = forceResync
        )

        for (slotIdx in 0 until FxChain.SLOT_COUNT) {
            val knobIndex = slotIdx + 1
            val slot = chain.slots.getOrNull(slotIdx)
            val label = slot?.displayName?.takeIf { it.isNotBlank() } ?: "FX$knobIndex"
            if (chain.slotSuperKnobLink.getOrNull(slotIdx) == true) {
                // Linked slots are driven by the Super Knob's soft-takeover propagation
                // (FxChain.propagateSuperKnob) -- a MacroBinding here would race it for the same
                // field. Clear any previously-owned binding and leave the knob unbound while
                // keeping the effect label.
                clearOwnedBinding(macroBank, knobIndex, bankLabel)
                macroBank.knobs.getOrNull(knobIndex)?.let { if (isOwnedOrEmpty(it, bankLabel)) it.label = label }
                continue
            }
            syncKnob(
                macroBank = macroBank,
                knobIndex = knobIndex,
                bankLabel = bankLabel,
                defaultLabel = label,
                targetPath = "$bankLabel/C$chainNum/FX$knobIndex/Meta",
                initialValue = slot?.metaKnob?.baseValue ?: 0f,
                forceResync = forceResync
            )
        }

        MacroEngine.invalidate()
    }

    private fun isOwnedOrEmpty(control: MacroControl, bankLabel: String): Boolean {
        val binding = control.bindings.firstOrNull() ?: return true
        val match = OWNED_PATH_PATTERN.matchEntire(binding.parameterId) ?: return false
        return match.groupValues[1] == bankLabel
    }

    private fun clearOwnedBinding(macroBank: MacroBank, knobIndex: Int, bankLabel: String) {
        val control = macroBank.knobs.getOrNull(knobIndex) ?: return
        if (control.bindings.isNotEmpty() && isOwnedOrEmpty(control, bankLabel)) {
            control.bindings.clear()
        }
    }

    private fun syncKnob(
        macroBank: MacroBank,
        knobIndex: Int,
        bankLabel: String,
        defaultLabel: String,
        targetPath: String,
        initialValue: Float,
        forceResync: Boolean
    ) {
        val control = macroBank.knobs.getOrNull(knobIndex) ?: return
        if (!forceResync && !isOwnedOrEmpty(control, bankLabel)) return

        control.label = defaultLabel
        control.bindings.clear()
        control.bindings.add(
            MacroBinding(
                parameterId = targetPath,
                targetType = MacroTargetType.PARAM_BASE_VALUE,
                minVal = 0f,
                maxVal = 1f,
                curve = MacroCurveType.LINEAR,
                inverted = false
            )
        )
        control.value = initialValue
    }
}
