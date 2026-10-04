package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.rendering.Mixer

/**
 * The "back" priority stack shared by the Esc key and a controller's back button:
 *   1. Any armed Learn (macro, MIDI or OSC) is cancelled, all at once. An arm survives independently of any widget's visibility, so it takes priority;
 *      a flat "back always collapses" would let a reflexive press dismiss the accordion while a Learn arm
 *      silently keeps running underneath.
 *   2. The Preferences window.
 *   3. FX focus mode (back to the chain's group mode).
 *   4. Any Rack module above Tier 1 collapses back to Tier 1.
 */
internal object BackNavigation {
    /** Undoes the innermost open thing. Returns true if something was undone. */
    fun back(parametersState: ParametersState, mixer: Mixer?): Boolean {
        var cancelledLearn = false
        if (MacroLearnState.isLearning()) { MacroLearnState.cancelLearn(); cancelledLearn = true }
        if (OscLearnState.isLearning()) { OscLearnState.cancelLearn(); cancelledLearn = true }
        if (parametersState.midiLearnTarget != null) { parametersState.midiLearnTarget = null; cancelledLearn = true }
        if (cancelledLearn) return true
        if (PreferencesPanel.isOpen) {
            PreferencesPanel.close()
            return true
        }
        if (mixer != null) {
            val focusedBankId = FxMacroSync.FX_BANK_IDS.firstOrNull { FxMacroSync.chainFor(it, mixer)?.isFocused() == true }
            if (focusedBankId != null) {
                FxMacroSync.focusSlot(focusedBankId, mixer, null)
                return true
            }
        }
        if (parametersState.anyRackModuleExpanded()) {
            parametersState.collapseAllRackModules()
            return true
        }
        return false
    }
}
