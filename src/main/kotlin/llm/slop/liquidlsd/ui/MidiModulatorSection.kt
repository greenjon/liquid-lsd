package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.midi.ParameterCellId
import llm.slop.liquidlsd.midi.MidiLearnTarget
import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Dedicated UI section for MIDI CC modulators (midi_cc_<channel>_<cc>).
 * Renders the mapped channel/CC readout and the Min/Max range (knob at 0 = Min, at 127 = Max).
 */
object MidiModulatorSection {

    fun draw(
        session: SessionContext,
        state: ParametersState,
        cell: ParameterCellId,
        param: ModulatableParameter,
        existing: CvModulator,
        modulatorIndex: Int? = null,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit,
        onUnbind: () -> Unit
    ) {
        // Channel & CC/Note info readout
        val isNote = existing.sourceId.startsWith("midi_note_")
        val prefix = if (isNote) "midi_note_" else "midi_cc_"
        val parts = existing.sourceId.removePrefix(prefix).split('_')
        if (parts.size >= 2) {
            val ch = parts[0].toIntOrNull() ?: 0
            val idx = parts[1].toIntOrNull() ?: 0
            val liveVal = if (isNote) {
                llm.slop.liquidlsd.midi.MidiEngine.getNoteValue(ch, idx)
            } else {
                llm.slop.liquidlsd.midi.MidiEngine.getCcValue(ch, idx)
            }
            val typeStr = if (isNote) "Note" else "CC"

            session.uiTheme.caption("Assigned MIDI Target: Channel ${ch + 1}, $typeStr $idx (Live: ${"%.2f".format(liveVal)})")
            ImGui.spacing()

            // Re-Learn & Unbind action controls
            val currentTarget = state.midiLearnTarget
            val isThisCellLearning = currentTarget is MidiLearnTarget.GridCell && currentTarget.cellId == cell

            if (isThisCellLearning) {
                val ink = TangoPalette.inkFor(TangoPalette.LEARN_ARMED_BG.dark, TangoPalette.LEARN_ARMED_HOVER.dark)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, TangoPalette.LEARN_ARMED_BG.u32())
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, TangoPalette.LEARN_ARMED_HOVER.u32())
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
                if (ButtonChrome.button("${Icons.REFRESH} Waiting for MIDI Note/CC... (Click to Cancel)##midi_relearn")) {
                    state.midiLearnTarget = null
                }
                ImGui.popStyleColor(3)
            } else {
                if (ButtonChrome.button("Re-Learn MIDI Modulator##midi_relearn")) {
                    state.midiLearnTarget = MidiLearnTarget.GridCell(cell, param)
                    state.midiLearnStartTimeMs = llm.slop.liquidlsd.utils.TimeSource.getEpochMillis()
                    if (llm.slop.liquidlsd.midi.MidiEngine.getActiveDeviceCount() == 0) {
                        PopupManager.globalPendingMidiWarning = true
                    }
                }
                itemTooltip("Adds a MIDI modulator to this parameter, saved in the preset; it adds on top of the value.\nFor a controller mapping that sets the value itself (saved in your controller profile), right-click the slider and choose MIDI Learn.")
                ImGui.sameLine()
                if (ButtonChrome.button("Clear MIDI Modulator##midi_unbind")) {
                    onUnbind()
                }
                itemTooltip("Remove this parameter's MIDI modulator.")
            }
            ImGui.spacing()
        }

        CustomRangeSlider.drawModulatorRange(
            session = session,
            label = "Modulation Range",
            mod = existing,
            unipolar = existing.unipolarOn(param),
            isRandomizeDisabled = param.isRandomizeDisabled,
            themeColor = themeColor,
            onReplace = onReplace,
            modulatorIndex = modulatorIndex,
            // No paramKey: a MIDI modulator's own range is not bindable (a controller driving a controller; DECISIONS.md).
            paramKey = null,
            tooltip = "Where the controller takes this parameter, measured from its set value: knob at 0 gives Min, at 127 gives Max.\n" +
                "Drag between the handles (or Shift-drag) to slide the whole range."
        )
    }
}
