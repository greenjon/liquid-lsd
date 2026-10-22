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

    private fun focusNames(fx: ISFFilter): List<String> = fx.focusParameters.map { it.key }

    /**
     * Applies [change] to [fx]. When it changed [ISFFilter.metaBindings] or [ISFFilter.knobLayout], hands [pushUndo] a
     * restore for both. [resync] runs only when [ISFFilter.focusParameters] changed, since only that moves the focus knobs.
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
        val beforeLayout = fx.knobLayout
        val beforeFocus = focusNames(fx)
        change(fx)
        if (fx.metaBindings == before && fx.knobLayout == beforeLayout) return false
        if (gesture == null || gesture != activeGesture) {
            pushUndo {
                fx.metaBindings.clear()
                fx.metaBindings.addAll(before)
                fx.applyKnobLayoutFromPreset(beforeLayout)
                resync()
            }
            activeGesture = gesture
        }
        if (focusNames(fx) != beforeFocus) resync()
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

    /** What a link drives, for editor labels: the parameter's name, "Dry/Wet", or a missing-parameter note. */
    fun targetLabel(fx: ISFFilter, binding: FxMetaBinding): String = when {
        binding.targetParamName == null -> "Dry/Wet"
        fx.isMissingTarget(binding) -> "${binding.targetParamName} (missing)"
        else -> ParamLabels.of(fx.header, binding.targetParamName)
    }

    /** Replaces the binding for the same target as [new] (one binding per target), keeping its position. */
    fun replace(fx: ISFFilter, new: FxMetaBinding) {
        val i = fx.metaBindings.indexOfFirst { it.targetParamName == new.targetParamName }
        if (i >= 0) fx.metaBindings[i] = new
    }

    /** Removes the binding for [target] (null = the Dry/Wet safety net). */
    fun remove(fx: ISFFilter, target: String?) {
        fx.metaBindings.removeAll { it.targetParamName == target }
    }

    // -- Knob layout (the order of parameters on focus knobs 2-4 and later pages) --------------------------------------

    /** The parameter names on the focus knobs right now, in knob order (page = index / 3). */
    fun shownKnobs(fx: ISFFilter): List<String> = fx.focusParameters.map { it.key }

    /** Parameters not on any knob, in authored order. */
    fun hiddenKnobs(fx: ISFFilter): List<String> = (fx.parameters.keys - shownKnobs(fx).toSet()).toList()

    /** Moves the parameter at knob position [from] by [delta] places (clamped). The first edit makes the default order explicit. */
    fun moveKnob(fx: ISFFilter, from: Int, delta: Int) {
        val list = shownKnobs(fx).toMutableList()
        val to = (from + delta).coerceIn(0, list.lastIndex.coerceAtLeast(0))
        if (from !in list.indices || to == from) return
        list.add(to, list.removeAt(from))
        fx.applyKnobLayoutFromPreset(list)
    }

    /** Takes [name] off the knobs. */
    fun hideKnob(fx: ISFFilter, name: String) {
        fx.applyKnobLayoutFromPreset(shownKnobs(fx) - name)
    }

    /** Puts a hidden parameter back at the end of the knob order. */
    fun showKnob(fx: ISFFilter, name: String) {
        if (name in fx.parameters) fx.applyKnobLayoutFromPreset(shownKnobs(fx) + name)
    }

    /** Back to authored order (minus Metaknob-owned parameters). */
    fun resetKnobs(fx: ISFFilter) {
        fx.applyKnobLayoutFromPreset(null)
    }
}
