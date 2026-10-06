package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.rendering.Mixer

/**
 * Fixed mapping from the active transition to the Transitions row's [MacroBank]:
 *  - Knob 1 = the crossfader. A two-way mirror, not a [MacroBinding]: a binding would lock the
 *    crossfader (the slider, auto-fade, snaps and MIDI/OSC mappings would all be overwritten every tick).
 *  - Knobs 2-4 = the transition's first three float parameters, in authored order. Selects, toggles and
 *    images are skipped; knobs with nothing to show are blank.
 * Re-run whenever the transition changes ([Mixer.setTransition], [Mixer.applyTransitionPreset]) and after
 * a session restores. Like the FX banks, the TRANS bank is read-only everywhere else: every sync rewrites
 * all four knobs, so a physical knob's meaning follows the transition.
 */
object TransitionMacroSync {

    const val CROSSFADE_LABEL = "XFADE"
    private const val PARAM_KNOBS = 3

    /** True for the bank whose knobs/bindings are owned by [TransitionMacroSync] (read-only everywhere else). */
    fun isTransBank(bankId: String?): Boolean = bankId == MacroEngine.TRANS

    /** True for any bank whose knobs follow the model (FX chain or transition) and can't be edited by hand. */
    fun isSyncOwned(bankId: String?): Boolean = FxMacroSync.isFxBank(bankId) || isTransBank(bankId)

    /** The "why can't I edit this" caption for a binding in a sync-owned bank. */
    fun ownerNote(bankId: String?): String =
        if (isTransBank(bankId)) "Driven by the active transition; change the transition instead."
        else "Driven by the FX chain; edit the chain's Metaknob link instead."

    // The crossfader knob's value as of the last mirror, to tell "the user moved the knob" from "the crossfader moved".
    private var lastKnobValue = Float.NaN

    fun sync(mixer: Mixer) {
        val bank = MacroEngine.getBank(MacroEngine.TRANS) ?: MacroEngine.newBankFor(MacroEngine.TRANS).also {
            MacroEngine.registerBank(MacroEngine.TRANS, it)
        }
        val knobs = bank.knobs
        knobs.getOrNull(0)?.let {
            it.label = CROSSFADE_LABEL
            it.bindings.clear()
            it.value = crossfadeToKnob(mixer)
            lastKnobValue = it.value
        }

        val trans = mixer.transitionFilter
        val names = trans?.floatParamNames ?: emptyList()
        for (k in 0 until PARAM_KNOBS) {
            val control = knobs.getOrNull(k + 1) ?: continue
            control.bindings.clear()
            val name = names.getOrNull(k)
            val param = name?.let { trans?.parameters?.get(it) }
            if (name == null || param == null) {
                control.label = "—"
                control.value = 0f
                continue
            }
            val range = param.maxClamp - param.minClamp
            control.label = name.uppercase().take(10)
            control.bindings.add(
                MacroBinding(
                    parameterId = "Mixer/Transition/$name",
                    targetType = MacroTargetType.PARAM_BASE_VALUE,
                    minVal = param.minClamp,
                    maxVal = param.maxClamp,
                    curve = MacroCurveType.LINEAR,
                    inverted = false
                )
            )
            control.value = if (range > 0f) ((param.baseValue - param.minClamp) / range).coerceIn(0f, 1f) else 0f
        }
        MacroEngine.invalidate()
    }

    /**
     * Keeps knob 1 and the crossfader in step; called every tick before the bindings are applied.
     * A knob that moved since the last call drives the crossfader (a manual takeover, like the slider);
     * otherwise the knob follows the crossfader, so auto-fade, the slider and MIDI show on the knob.
     */
    fun mirrorCrossfade(mixer: Mixer) {
        val knob = MacroEngine.getBank(MacroEngine.TRANS)?.knobs?.getOrNull(0) ?: return
        if (knob.label != CROSSFADE_LABEL) return
        if (!lastKnobValue.isNaN() && knob.value != lastKnobValue) {
            mixer.onCrossfadeManualTakeover()
            mixer.crossfade.baseValue = (knob.value * 2f - 1f).coerceIn(-1f, 1f)
        } else {
            knob.value = crossfadeToKnob(mixer)
        }
        lastKnobValue = knob.value
    }

    private fun crossfadeToKnob(mixer: Mixer): Float = ((mixer.crossfade.baseValue + 1f) * 0.5f).coerceIn(0f, 1f)
}
