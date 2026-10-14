package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.isf.FxMetaBinding
import llm.slop.liquidlsd.rendering.isf.ISFFilter

/**
 * The one way UI code edits a slot's Metaknob bindings: mutate, push a real undo step that puts the bindings back,
 * then re-sync the FX bank knobs. [ISFFilter.focusParameters] (and with it the focus knobs and the page count)
 * depends on which parameters the enabled bindings own, so skipping the re-sync leaves knobs 2-4 stale.
 */
object FxMetaEdit {
    /** The continuous edit (a drag) already represented by the newest undo step, or null. Ended by [endGestureIfMouseUp]. */
    private var activeGesture: Any? = null

    /** Ends the running gesture once the mouse is released; call once per frame from any editor that passes a gesture. */
    fun endGestureIfMouseUp(mouseDown: Boolean) {
        if (!mouseDown) activeGesture = null
    }

    private fun owned(bindings: List<FxMetaBinding>): Set<String> =
        bindings.filter { it.enabled }.mapNotNullTo(HashSet()) { it.targetParamName }

    /**
     * Applies [change] to [fx]. When it changed [ISFFilter.metaBindings], hands [pushUndo] a restore for the old list.
     * [resync] runs only when the set of parameters the Metaknob owns changed, since only that moves the focus knobs.
     *
     * [gesture] groups the many changes of one drag into a single undo step: while the same non-null key stays active, only the
     * first change pushes an undo (its restore holds the state from before the drag). Returns whether anything changed;
     * a no-op edit leaves the undo stack alone.
     */
    fun apply(
        fx: ISFFilter,
        pushUndo: (restore: () -> Unit) -> Unit,
        resync: () -> Unit,
        gesture: Any? = null,
        change: (ISFFilter) -> Unit
    ): Boolean {
        val before: List<FxMetaBinding> = fx.metaBindings.toList()
        change(fx)
        if (fx.metaBindings == before) return false
        if (gesture == null || gesture != activeGesture) {
            pushUndo {
                fx.metaBindings.clear()
                fx.metaBindings.addAll(before)
                resync()
            }
            activeGesture = gesture
        }
        if (owned(before) != owned(fx.metaBindings)) resync()
        return true
    }

    /** [apply] wired to the Parameters undo stack and the FX bank of [chainPrefix] (e.g. "Deck A/FX"). */
    fun apply(state: ParametersState, chainPrefix: String, chain: FxChain, fx: ISFFilter, gesture: Any? = null, change: (ISFFilter) -> Unit): Boolean =
        apply(
            fx,
            pushUndo = { restore -> state.pushUndoState(ParametersUndoSnapshot(emptyMap(), restore)) },
            resync = { FXChainMacroStrip.resyncMacroKnobs(chainPrefix, chain) },
            gesture = gesture,
            change = change
        )

    /** Replaces the binding for the same target as [new] (one binding per target), keeping its position. */
    fun replace(fx: ISFFilter, new: FxMetaBinding) {
        val i = fx.metaBindings.indexOfFirst { it.targetParamName == new.targetParamName }
        if (i >= 0) fx.metaBindings[i] = new
    }

    /** Removes the binding for [target] (null = the Dry/Wet safety net). */
    fun remove(fx: ISFFilter, target: String?) {
        fx.metaBindings.removeAll { it.targetParamName == target }
    }
}
