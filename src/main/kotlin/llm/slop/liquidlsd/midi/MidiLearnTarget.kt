package llm.slop.liquidlsd.midi

import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Identifies a single cell in the Deep Edit parameter grid.
 * @param paramKey   Fully-qualified parameter key, e.g. "Mixer/crossfade" or "Deck A/Geometry/L1"
 * @param cvSourceId The CV source column, e.g. "beatPhase", "amp", "lfo"
 */
data class ParameterCellId(val paramKey: String, val cvSourceId: String)

sealed class MidiLearnTarget {
    data class GridCell(val cellId: ParameterCellId, val param: ModulatableParameter) : MidiLearnTarget()
    data class BaseValueSlider(val paramKey: String, val label: String, val param: ModulatableParameter? = null, val min: Float, val max: Float) : MidiLearnTarget()
    data class ModulatorProperty(val fullPath: String, val label: String, val min: Float, val max: Float) : MidiLearnTarget()
    data class GlobalAction(val actionKey: String) : MidiLearnTarget()
    data class MacroTarget(val macroPath: String, val label: String) : MidiLearnTarget()
    /** Bind [commandId] in controller profile [profileId] to the next control moved, held with [modifiers]. */
    data class ProfileCommand(val profileId: String, val commandId: String, val modifiers: List<String>) : MidiLearnTarget()
}

/** What the MIDI mapping manager needs from the UI's learn state; implemented by the UI, never imported from it. */
interface MidiLearnSink {
    var midiLearnTarget: MidiLearnTarget?
    val midiLearnStartTimeMs: Long
}

/** Source of the global "MIDI enabled" switch (backed by UI preferences, wired in Main). */
fun interface MidiEnabledSource {
    fun isMidiEnabled(): Boolean
}
