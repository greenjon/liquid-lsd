package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Fixed (Mixxx-style) mapping from an [FxChain] to its FX row's [MacroBank]:
 *  - Group mode: Knob 1 = the chain's Super Knob, Knobs 2-4 = each slot's Metaknob.
 *  - Focus mode: Knob 1 = the focused slot's Metaknob, Knobs 2-4 = its top parameters (paged).
 *    The focused slot's Dry/Wet lives on the chain header, not on a knob.
 * Runs whenever a chain's contents change (load, slot swap, link toggle, focus), so the
 * Performance Console's 4-knob FX rows and the Edit-row macro strip (which read the same banks)
 * always show the current mapping. FX banks are not user-bindable: every sync rewrites all four
 * knobs, so a physical knob's meaning is fixed per mode.
 *
 * Every FX chain -- each deck's and the master bus's -- lives under "<label>/FX/..." (e.g.
 * "Deck A/FX/Super", "Master/FX/FX2/Meta"); [labelFor]/[chainFor] map an FX bank id to it.
 *
 *
 * Mutually exclusive with [FxChain]'s slot link: both would otherwise write
 * [llm.slop.liquidlsd.rendering.isf.ISFFilter.metaKnob]'s base value every frame (one via this
 * binding, one via [FxChain.update]'s soft-takeover propagation). A linked slot's knob gets no binding;
 * [MacroEngine.tick] mirrors it both ways instead, so turning it still moves the Metaknob (and releases
 * the slot from the Super Knob until the Super Knob picks it up again).
 */
object FxMacroSync {

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
    /** True for banks whose knobs/bindings are owned by [FxMacroSync] (read-only everywhere else). */
    fun isFxBank(bankId: String?): Boolean = bankId in FX_BANK_IDS

    fun bankIdFor(chain: FxChain, mixer: Mixer): String? = FX_BANK_IDS.firstOrNull { chainFor(it, mixer) === chain }

    /** Re-syncs [bankId]'s knobs to its chain. No-op for non-FX bank ids. */
    fun syncFor(bankId: String, mixer: Mixer) {
        val chain = chainFor(bankId, mixer) ?: return
        val label = labelFor(bankId) ?: return
        syncChain(bankId, label, chain)
    }

    /** Focuses slot [slotIndex] (or null to return to Group mode) for [bankId] and re-syncs. */
    fun focusSlot(bankId: String, mixer: Mixer, slotIndex: Int?) {
        val chain = chainFor(bankId, mixer) ?: return
        chain.focusSlot(slotIndex)
        syncFor(bankId, mixer)
    }

    /** Steps parameter page by [dir] (-1 or +1) for [bankId] and re-syncs. */
    fun stepParamPage(bankId: String, mixer: Mixer, dir: Int) {
        val chain = chainFor(bankId, mixer) ?: return
        chain.stepParamPage(dir)
        syncFor(bankId, mixer)
    }

    /** Re-syncs every FX bank. */
    fun syncAll(mixer: Mixer) {
        for (bankId in FX_BANK_IDS) syncFor(bankId, mixer)
    }

    /**
     * Re-syncs [bankId]'s [MacroBank] knobs 0-3 to [chain].
     * In Group Mode: Knob 0 becomes Super Knob, Knobs 1-3 each slot's Metaknob.
     * In Focus Mode: Knob 0 becomes the focused slot's Metaknob, Knobs 1-3 its top parameters (paged).
     */
    fun syncChain(bankId: String, chainLabel: String, chain: FxChain) {
        val macroBank = MacroEngine.getBank(bankId) ?: MacroEngine.newBankFor(bankId).also {
            MacroEngine.registerBank(bankId, it)
        }

        val focused = chain.focusedSlot
        if (focused != null && focused in 0 until FxChain.SLOT_COUNT) {
            syncFocusMode(macroBank, chainLabel, chain, focused)
        } else {
            syncGroupMode(macroBank, chainLabel, chain)
        }

        MacroEngine.forgetLinkedKnobMirror(macroBank)
        MacroEngine.invalidate()
    }

    private fun syncGroupMode(
        macroBank: MacroBank,
        chainLabel: String,
        chain: FxChain
    ) {
        syncKnob(
            macroBank = macroBank,
            knobIndex = 0,
            defaultLabel = "SUPER",
            targetPath = "$chainLabel/FX/Super",
            initialValue = chain.superKnob.baseValue
        )

        for (slotIdx in 0 until FxChain.SLOT_COUNT) {
            val knobIndex = slotIdx + 1
            val slot = chain.slots.getOrNull(slotIdx)
            val label = "META"
            if (chain.slotSuperKnobLink.getOrNull(slotIdx) == true) {
                // Linked slots are driven by the Super Knob's soft-takeover propagation
                // (FxChain.propagateSuperKnob) -- a MacroBinding here would race it for the same
                // field. Leave the knob unbound; MacroEngine.tick mirrors it to the Metaknob both ways.
                macroBank.knobs.getOrNull(knobIndex)?.let { it.bindings.clear(); it.label = label }
                continue
            }
            syncKnob(
                macroBank = macroBank,
                knobIndex = knobIndex,
                defaultLabel = label,
                targetPath = "$chainLabel/FX/FX$knobIndex/Meta",
                initialValue = slot?.metaKnob?.baseValue ?: 0f
            )
        }
    }

    private fun syncFocusMode(
        macroBank: MacroBank,
        chainLabel: String,
        chain: FxChain,
        slotIdx: Int
    ) {
        val slotNum = slotIdx + 1
        val slot = chain.slots.getOrNull(slotIdx)

        // Knob 0: Focused slot's Metaknob (its Dry/Wet is on the chain header). A slot linked to the
        // Super Knob is driven by FxChain's propagation, so its knob is an unbound two-way mirror (see group mode).
        if (chain.slotSuperKnobLink.getOrNull(slotIdx) == true) {
            macroBank.knobs.getOrNull(0)?.let { it.bindings.clear(); it.label = "META" }
        } else {
            syncKnob(
                macroBank = macroBank,
                knobIndex = 0,
                defaultLabel = "META",
                targetPath = "$chainLabel/FX/FX$slotNum/Meta",
                initialValue = slot?.metaKnob?.baseValue ?: 0f
            )
        }

        // Knobs 1..3: Focused slot's parameters for active page
        val paramEntries = slot?.focusParameters ?: emptyList()
        val page = chain.focusParamPage
        val startIndex = page * 3

        for (k in 0 until 3) {
            val knobIndex = k + 1
            val paramIdx = startIndex + k
            if (paramIdx < paramEntries.size) {
                val entry = paramEntries[paramIdx]
                val paramName = entry.key
                val param = entry.value
                val label = llm.slop.liquidlsd.ui.ParamLabels.of(slot?.header, paramName).uppercase()
                // Time/frequency-like parameters get the same exponential response the Metaknob auto-mapping uses, so the
                // useful low end isn't squeezed into the first few degrees. Not for stepped or bipolar (negative) ranges.
                val curve = if (param.steps == null && param.minClamp >= 0f &&
                    llm.slop.liquidlsd.rendering.isf.ISFAutoBindEngine.isExponentialName(paramName)
                ) MacroCurveType.EXPONENTIAL else MacroCurveType.LINEAR
                syncKnob(
                    macroBank = macroBank,
                    knobIndex = knobIndex,
                    defaultLabel = label,
                    targetPath = "$chainLabel/FX/FX$slotNum/$paramName",
                    minVal = param.minClamp,
                    maxVal = param.maxClamp,
                    curve = curve,
                    initialValue = null,
                    currentTarget = param.baseValue
                )
            } else {
                clearKnob(macroBank, knobIndex)
            }
        }
    }

    private fun clearKnob(macroBank: MacroBank, knobIndex: Int) {
        val control = macroBank.knobs.getOrNull(knobIndex) ?: return
        control.label = "—"
        control.bindings.clear()
        control.value = 0f
    }

    private fun syncKnob(
        macroBank: MacroBank,
        knobIndex: Int,
        defaultLabel: String,
        targetPath: String,
        minVal: Float = 0f,
        maxVal: Float = 1f,
        curve: MacroCurveType = MacroCurveType.LINEAR,
        initialValue: Float?,
        /** With a null [initialValue]: the target's current value, which the knob is placed to reproduce (no jump). */
        currentTarget: Float = 0f
    ) {
        val control = macroBank.knobs.getOrNull(knobIndex) ?: return

        control.label = defaultLabel
        control.bindings.clear()
        val binding = MacroBinding(
            parameterId = targetPath,
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = minVal,
            maxVal = maxVal,
            curve = curve,
            inverted = false
        )
        control.bindings.add(binding)
        control.value = initialValue ?: MacroCurve.inverse(currentTarget, binding).coerceIn(0f, 1f)
    }
}
