package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.isf.FxMetaBinding
import llm.slop.liquidlsd.rendering.isf.ISFFilter

/**
 * The one way UI code edits a slot's Metaknob bindings: mutate, push a real undo step that puts the bindings back,
 * then re-sync the FX bank knobs. [ISFFilter.focusParameters] (and with it the focus knobs and the page count)
 * depends on the enabled bindings, so skipping the re-sync leaves knobs 2-4 stale.
 */
object FxMetaEdit {
    /**
     * Applies [change] to [fx]. When it changed [ISFFilter.metaBindings], hands [pushUndo] a restore for the old list
     * and calls [resync]. Returns whether anything changed; a no-op edit leaves the undo stack alone.
     */
    fun apply(fx: ISFFilter, pushUndo: (restore: () -> Unit) -> Unit, resync: () -> Unit, change: (ISFFilter) -> Unit): Boolean {
        val before: List<FxMetaBinding> = fx.metaBindings.toList()
        change(fx)
        if (fx.metaBindings == before) return false
        pushUndo {
            fx.metaBindings.clear()
            fx.metaBindings.addAll(before)
            resync()
        }
        resync()
        return true
    }

    /** [apply] wired to the Parameters undo stack and the FX bank of [chainPrefix] (e.g. "Deck A/FX"). */
    fun apply(state: ParametersState, chainPrefix: String, chain: FxChain, fx: ISFFilter, change: (ISFFilter) -> Unit): Boolean =
        apply(
            fx,
            pushUndo = { restore -> state.pushUndoState(ParametersUndoSnapshot(emptyMap(), restore)) },
            resync = { FXChainMacroStrip.resyncMacroKnobs(chainPrefix, chain) },
            change = change
        )
}
