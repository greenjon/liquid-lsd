package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine

/**
 * Long-lived [FxChainHeader.Actions] for a deck row. The per-frame inputs are written with [set] before
 * each draw, so the header is drawn without creating closures.
 */
internal class DeckFxActions : FxChainHeader.Actions {
    private lateinit var parametersState: ParametersState
    private var deckLabel = ""
    private var canonicalBankId = ""

    fun set(parametersState: ParametersState, deckLabel: String, canonicalBankId: String) {
        this.parametersState = parametersState
        this.deckLabel = deckLabel; this.canonicalBankId = canonicalBankId
    }

    override fun openSlotBrowse(slotIdx: Int) {
        parametersState.selectFxChain(canonicalBankId, deckLabel, slotIndex = slotIdx)
    }

    override fun openChainBrowse() {
        parametersState.selectFxChain(canonicalBankId, deckLabel, slotIndex = null)
    }
}

/** Long-lived [FxChainHeader.Actions] for the Master row; see [DeckFxActions]. */
internal class MasterFxActions : FxChainHeader.Actions {
    private lateinit var parametersState: ParametersState

    fun set(parametersState: ParametersState) {
        this.parametersState = parametersState
    }

    override fun openSlotBrowse(slotIdx: Int) {
        parametersState.selectFxChain(MacroEngine.MASTER, deckLabel = null, slotIndex = slotIdx)
    }

    override fun openChainBrowse() {
        parametersState.selectFxChain(MacroEngine.MASTER, deckLabel = null, slotIndex = null)
    }
}
