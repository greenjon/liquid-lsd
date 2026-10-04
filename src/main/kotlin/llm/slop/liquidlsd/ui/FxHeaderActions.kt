package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Long-lived [FxChainHeader.Actions] for a deck row. The per-frame inputs are written with [set] before
 * each draw, so the header is drawn without creating closures.
 */
internal class DeckFxActions : FxChainHeader.Actions {
    private lateinit var parametersState: ParametersState
    private lateinit var ctx: PerformanceUiContext
    private lateinit var mixer: Mixer
    private var deckLabel = ""
    private var tag = ""
    private var canonicalBankId = ""
    private var targetBank = ""
    private var isFx = false
    private var pinned = false

    fun set(
        parametersState: ParametersState, ctx: PerformanceUiContext, mixer: Mixer,
        deckLabel: String, tag: String, canonicalBankId: String, targetBank: String, isFx: Boolean, pinned: Boolean
    ) {
        this.parametersState = parametersState; this.ctx = ctx; this.mixer = mixer
        this.deckLabel = deckLabel; this.tag = tag; this.canonicalBankId = canonicalBankId
        this.targetBank = targetBank; this.isFx = isFx; this.pinned = pinned
    }

    override fun openSlotBrowse(slotIdx: Int) {
        parametersState.openFxChainBrowse(canonicalBankId, deckLabel, slotIndex = slotIdx)
    }

    override fun focusSlot(slotIdx: Int?) {
        if (slotIdx != null && !isFx && !pinned) {
            MacroLearnState.onNavigateSection(deckLabel, "FX")
            parametersState.setDeckSubTab(deckLabel, "FX")
            FxMacroSync.syncFor(targetBank, mixer)
        }
    }

    override fun openChainBrowse() {
        parametersState.openFxChainBrowse(canonicalBankId, deckLabel, slotIndex = null)
    }
}

/** Long-lived [FxChainHeader.Actions] for the Master row; see [DeckFxActions]. */
internal class MasterFxActions : FxChainHeader.Actions {
    private lateinit var parametersState: ParametersState
    private lateinit var ctx: PerformanceUiContext
    private lateinit var mixer: Mixer
    private var isFx = false
    private var pinned = false

    fun set(parametersState: ParametersState, ctx: PerformanceUiContext, mixer: Mixer, isFx: Boolean, pinned: Boolean) {
        this.parametersState = parametersState; this.ctx = ctx; this.mixer = mixer
        this.isFx = isFx; this.pinned = pinned
    }

    override fun openSlotBrowse(slotIdx: Int) {
        parametersState.openFxChainBrowse(MacroEngine.MASTER, deckLabel = null, slotIndex = slotIdx)
    }

    override fun focusSlot(slotIdx: Int?) {
        if (slotIdx != null && !isFx && !pinned) {
            MacroLearnState.onNavigateSection("Mixer", "FX")
            parametersState.activeMixerSubTab = "FX"
            FxMacroSync.syncFor(MacroEngine.MASTER_FX, mixer)
        }
    }

    override fun openChainBrowse() {
        parametersState.openFxChainBrowse(MacroEngine.MASTER, deckLabel = null, slotIndex = null)
    }
}
