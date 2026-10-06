package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Makes hand edits to the Deck / Master / Global macro banks (add or remove a target, range, curve, invert,
 * link, enable, rename) undoable with Ctrl+Z, without instrumenting every widget that can change one.
 *
 * Once a frame [update] compares a cheap hash of every tracked knob's label and bindings with the last frame's.
 * The first change of a gesture pushes an undo step that restores the previous copy; a drag is one gesture (it
 * ends once the mouse is up and nothing changed). Knob *values* are deliberately ignored (they move constantly
 * in performance), and FX banks are skipped because [FxMacroSync] rewrites them on its own. Wholesale loads
 * (preset, default, session -- see [MacroEngine.bankReplaceEpoch]) re-baseline instead of recording, since the
 * deck/source undo step already covers them.
 */
object MacroUndoTracker {
    private class KnobCopy(val label: String, val bindings: List<MacroBinding>)

    private var committed: Map<String, List<KnobCopy>>? = null
    private var committedHash = 0
    private var lastEpoch = -1L
    private var inGesture = false
    private var lastState: ParametersState? = null
    private var lastMixer: Mixer? = null

    private val trackedIds get() = MacroEngine.CANONICAL_BANK_IDS.filter { !llm.slop.liquidlsd.macro.TransitionMacroSync.isSyncOwned(it) }

    private fun currentHash(): Int {
        var h = 1
        for (id in trackedIds) {
            val knobs = MacroEngine.getBank(id)?.knobs ?: continue
            for (k in knobs) h = 31 * (31 * h + k.label.hashCode()) + k.bindings.hashCode()
        }
        return h
    }

    private fun copyCurrent(): Map<String, List<KnobCopy>> = trackedIds.mapNotNull { id ->
        MacroEngine.getBank(id)?.let { bank -> id to bank.knobs.map { KnobCopy(it.label, it.bindings.map { b -> b.copy() }) } }
    }.toMap()

    private fun baseline(hash: Int) {
        committed = copyCurrent()
        committedHash = hash
    }

    /** Call once per frame (after the UI has drawn). [mouseDown] is whether the left button is held. */
    fun update(state: ParametersState, mixer: Mixer, mouseDown: Boolean) {
        lastState = state
        lastMixer = mixer
        val epoch = MacroEngine.bankReplaceEpoch
        val hash = currentHash()
        if (committed == null || epoch != lastEpoch) {
            lastEpoch = epoch
            baseline(hash)
            inGesture = false
            return
        }
        if (hash != committedHash) {
            if (!inGesture) push(state, mixer)
            inGesture = true
            baseline(hash)
        } else if (!mouseDown) {
            inGesture = false
        }
    }

    /** Pushes an undo step for the state as of the last frame; call just before a bulk edit that bumps the epoch (e.g. Import). */
    fun recordBeforeBulkEdit() {
        val state = lastState ?: return
        val mixer = lastMixer ?: return
        push(state, mixer)
    }

    private fun push(state: ParametersState, mixer: Mixer) {
        val before = committed ?: return
        ParametersUndo.pushUndoState(state, mixer) { restore(before) }
    }

    private fun restore(before: Map<String, List<KnobCopy>>) {
        for ((id, knobs) in before) {
            val bank = MacroEngine.getBank(id) ?: continue
            knobs.forEachIndexed { i, saved ->
                val knob = bank.knobs.getOrNull(i) ?: return@forEachIndexed
                knob.label = saved.label
                knob.bindings.clear()
                knob.bindings.addAll(saved.bindings.map { it.copy() })
            }
        }
        MacroEngine.invalidate()
        baseline(currentHash())
        inGesture = false
    }

    /** Test hook: forget everything so the next [update] re-baselines. */
    internal fun reset() {
        committed = null
        inGesture = false
        lastEpoch = -1L
    }
}
