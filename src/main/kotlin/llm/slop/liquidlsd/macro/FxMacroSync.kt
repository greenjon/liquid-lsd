package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Smart-default bridge from an [FxChain] to its FX row's [MacroBank]: Knob 1 becomes the chain's
 * Super Knob, Knobs 2-4 become each slot's Metaknob. Runs whenever a chain's contents change
 * (load, slot swap, link toggle), so the Performance Console's generic 4-knob FX rows (and
 * Column 3's MACROS view, which reads the same banks) show a sensible default without the user
 * hand-wiring bindings.
 *
 * Every FX chain -- each deck's and the master bus's -- lives under "<label>/FX/..." (e.g.
 * "Deck A/FX/Super", "Master/FX/FX2/Meta"); [labelFor]/[chainFor] map an FX bank id to it.
 *
 * Ownership rule: a knob is only overwritten if it's unbound or its current primary binding
 * already matches this sync's own path pattern for the *same* chain -- a knob the user manually
 * retargeted to something else (e.g. "deckA/Warp") is left alone on every future sync.
 * [forceResync] bypasses that check for the row's explicit "Resync" action.
 *
 * Mutually exclusive with [FxChain]'s Link checkbox: both would otherwise write
 * [llm.slop.liquidlsd.rendering.isf.ISFFilter.metaKnob]'s base value every frame (one via this
 * binding, one via [FxChain.update]'s soft-takeover propagation). A linked slot is skipped here
 * entirely (no binding, label shows the effect name) -- see [llm.slop.liquidlsd.ui.FXChainMacroStrip]
 * for the reverse: enabling Link there clears this sync's binding for that slot.
 */
object FxMacroSync {

    private val OWNED_PATH_PATTERN = Regex("""^([^/]+)/FX/(Super|DryWet|FX\d+(/.*)?)$""")

    /** The FX bank ids, one per FX chain, in display order. */
    val FX_BANK_IDS = listOf(
        MacroEngine.DECK_A_FX, MacroEngine.DECK_B_FX, MacroEngine.DECK_BG_FX, MacroEngine.DECK_PV_FX, MacroEngine.MASTER_FX
    )

    /** Parameter-path label of [bankId]'s chain ("Deck A", ..., "Master"), or null if [bankId] isn't an FX bank. */
    fun labelFor(bankId: String): String? = when (bankId) {
        MacroEngine.DECK_A_FX -> "Deck A"
        MacroEngine.DECK_B_FX -> "Deck B"
        MacroEngine.DECK_BG_FX -> "Deck BG"
        MacroEngine.DECK_PV_FX -> "Deck PV"
        MacroEngine.MASTER_FX -> "Master"
        else -> null
    }

    /** The live [FxChain] behind [bankId], or null if [bankId] isn't an FX bank. */
    fun chainFor(bankId: String, mixer: Mixer): FxChain? = when (bankId) {
        MacroEngine.DECK_A_FX -> mixer.deckA.fxChain
        MacroEngine.DECK_B_FX -> mixer.deckB.fxChain
        MacroEngine.DECK_BG_FX -> mixer.deckBG.fxChain
        MacroEngine.DECK_PV_FX -> mixer.deckPV.fxChain
        MacroEngine.MASTER_FX -> mixer.masterFxChain
        else -> null
    }

    /** The FX bank id owning [chain] (identity match), or null if it isn't one of [mixer]'s chains. */
    fun bankIdFor(chain: FxChain, mixer: Mixer): String? = FX_BANK_IDS.firstOrNull { chainFor(it, mixer) === chain }

    /** Re-syncs [bankId]'s knobs to its chain. No-op for non-FX bank ids. */
    fun syncFor(bankId: String, mixer: Mixer, forceResync: Boolean = false) {
        val chain = chainFor(bankId, mixer) ?: return
        val label = labelFor(bankId) ?: return
        syncChain(bankId, label, chain, forceResync)
    }

    /** Focuses slot [slotIndex] (or null to return to Group mode) for [bankId] and re-syncs. */
    fun focusSlot(bankId: String, mixer: Mixer, slotIndex: Int?) {
        val chain = chainFor(bankId, mixer) ?: return
        chain.focusSlot(slotIndex)
        syncFor(bankId, mixer, forceResync = true)
    }

    /** Steps parameter page by [dir] (-1 or +1) for [bankId] and re-syncs. */
    fun stepParamPage(bankId: String, mixer: Mixer, dir: Int) {
        val chain = chainFor(bankId, mixer) ?: return
        chain.stepParamPage(dir)
        syncFor(bankId, mixer, forceResync = true)
    }

    /** Re-syncs every FX bank. */
    fun syncAll(mixer: Mixer, forceResync: Boolean = false) {
        for (bankId in FX_BANK_IDS) syncFor(bankId, mixer, forceResync)
    }

    /**
     * Re-syncs [bankId]'s [MacroBank] knobs 0-3 to [chain].
     * In Group Mode: Knob 0 becomes Super Knob, Knobs 1-3 each slot's Metaknob.
     * In Focus Mode: Knob 0 becomes focused slot's Dry/Wet, Knobs 1-3 its top parameters (paged).
     */
    fun syncChain(bankId: String, chainLabel: String, chain: FxChain, forceResync: Boolean = false) {
        val macroBank = MacroEngine.getBank(bankId) ?: MacroEngine.newBankFor(bankId).also {
            MacroEngine.registerBank(bankId, it)
        }

        val focused = chain.focusedSlot
        if (focused != null && focused in 0 until FxChain.SLOT_COUNT) {
            syncFocusMode(macroBank, chainLabel, chain, focused, forceResync)
        } else {
            syncGroupMode(macroBank, chainLabel, chain, forceResync)
        }

        MacroEngine.invalidate()
    }

    private fun syncGroupMode(
        macroBank: MacroBank,
        chainLabel: String,
        chain: FxChain,
        forceResync: Boolean
    ) {
        syncKnob(
            macroBank = macroBank,
            knobIndex = 0,
            bankLabel = chainLabel,
            defaultLabel = "SUPER",
            targetPath = "$chainLabel/FX/Super",
            initialValue = chain.superKnob.baseValue,
            forceResync = forceResync
        )

        for (slotIdx in 0 until FxChain.SLOT_COUNT) {
            val knobIndex = slotIdx + 1
            val slot = chain.slots.getOrNull(slotIdx)
            val label = "META"
            if (chain.slotSuperKnobLink.getOrNull(slotIdx) == true) {
                // Linked slots are driven by the Super Knob's soft-takeover propagation
                // (FxChain.propagateSuperKnob) -- a MacroBinding here would race it for the same
                // field. Clear any previously-owned binding and leave the knob unbound while
                // keeping the label.
                clearOwnedBinding(macroBank, knobIndex, chainLabel)
                macroBank.knobs.getOrNull(knobIndex)?.let { if (isOwnedOrEmpty(it, chainLabel)) it.label = label }
                continue
            }
            syncKnob(
                macroBank = macroBank,
                knobIndex = knobIndex,
                bankLabel = chainLabel,
                defaultLabel = label,
                targetPath = "$chainLabel/FX/FX$knobIndex/Meta",
                initialValue = slot?.metaKnob?.baseValue ?: 0f,
                forceResync = forceResync
            )
        }
    }

    private fun syncFocusMode(
        macroBank: MacroBank,
        chainLabel: String,
        chain: FxChain,
        slotIdx: Int,
        forceResync: Boolean
    ) {
        val slotNum = slotIdx + 1
        val slot = chain.slots.getOrNull(slotIdx)

        // Knob 0: Focused slot's individual Dry/Wet
        syncKnob(
            macroBank = macroBank,
            knobIndex = 0,
            bankLabel = chainLabel,
            defaultLabel = "DRY/WET",
            targetPath = "$chainLabel/FX/FX$slotNum/DryWet",
            minVal = 0f,
            maxVal = 1f,
            initialValue = slot?.dryWet?.baseValue ?: 1f,
            forceResync = forceResync
        )

        // Knobs 1..3: Focused slot's parameters for active page
        val paramEntries = slot?.parameters?.entries?.toList() ?: emptyList()
        val page = chain.focusParamPage
        val startIndex = page * 3

        for (k in 0 until 3) {
            val knobIndex = k + 1
            val paramIdx = startIndex + k
            if (paramIdx < paramEntries.size) {
                val entry = paramEntries[paramIdx]
                val paramName = entry.key
                val param = entry.value
                val label = paramName.uppercase().take(10)
                val minVal = param.minClamp
                val maxVal = param.maxClamp
                val range = maxVal - minVal
                val normVal = if (range > 0f) ((param.baseValue - minVal) / range).coerceIn(0f, 1f) else 0f
                syncKnob(
                    macroBank = macroBank,
                    knobIndex = knobIndex,
                    bankLabel = chainLabel,
                    defaultLabel = label,
                    targetPath = "$chainLabel/FX/FX$slotNum/$paramName",
                    minVal = minVal,
                    maxVal = maxVal,
                    initialValue = normVal,
                    forceResync = forceResync
                )
            } else {
                clearKnob(macroBank, knobIndex, chainLabel, forceResync)
            }
        }
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

    private fun clearKnob(macroBank: MacroBank, knobIndex: Int, bankLabel: String, forceResync: Boolean) {
        val control = macroBank.knobs.getOrNull(knobIndex) ?: return
        if (!forceResync && !isOwnedOrEmpty(control, bankLabel)) return
        control.label = "—"
        control.bindings.clear()
        control.value = 0f
    }

    private fun syncKnob(
        macroBank: MacroBank,
        knobIndex: Int,
        bankLabel: String,
        defaultLabel: String,
        targetPath: String,
        minVal: Float = 0f,
        maxVal: Float = 1f,
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
                minVal = minVal,
                maxVal = maxVal,
                curve = MacroCurveType.LINEAR,
                inverted = false
            )
        )
        control.value = initialValue
    }
}
