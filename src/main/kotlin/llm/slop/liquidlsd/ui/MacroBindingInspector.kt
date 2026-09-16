package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import llm.slop.liquidlsd.macro.*

/**
 * Binding Inspector drawer for the selected Macro Control in Column 3.
 *
 * Displays control metadata (label, switch behavior) and its active 1-to-many bindings list (0..4).
 * Allows modifying travel bounds (Min/Max), response curves, direction inversion, and enabling/disabling
 * bindings (which acts as the field-ownership lock release mechanism per proposal §3.3).
 */
object MacroBindingInspector {
    private val labelBuf = ImString(64)
    private var lastControlId: String? = null

    fun draw(session: llm.slop.liquidlsd.SessionContext, bank: MacroBank, control: MacroControl?, parametersState: ParametersState) {
        if (control == null) {
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                ImGui.textDisabled("Select a Knob or Switch above to inspect bindings.")
            }
            return
        }

        if (lastControlId != control.id) {
            labelBuf.set(control.label)
            lastControlId = control.id
        }

        ImGui.pushID(control.id)

        // Header row: Type badge, name input, value readout, and Learn button
        val typeBadge = if (control.isSwitch) "SWITCH" else "KNOB"
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textColored(0.2f, 0.85f, 1.0f, 1.0f, typeBadge)
        }
        ImGui.sameLine(0f, 6f)

        ImGui.setNextItemWidth(120f)
        if (ImGui.inputText("##rename_${control.id}", labelBuf)) {
            control.label = labelBuf.get()
        }
        itemTooltip("Rename macro control.")

        ImGui.sameLine(0f, 8f)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled("Val: ${"%.2f".format(control.value)}")
        }

        val isLearning = MacroLearnState.isControlLearning(control.id)
        ImGui.sameLine(0f, 8f)
        if (isLearning) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.85f, 0.2f, 0.2f, 0.8f))
            if (ImGui.button("${Icons.X} Cancel Learn##cancel_learn")) {
                MacroLearnState.cancelLearn()
            }
            ImGui.popStyleColor()
            itemTooltip("Cancel Learn Mode.")
        } else {
            val canLearn = control.bindings.size < MacroControl.MAX_BINDINGS_PER_CONTROL
            if (canLearn) {
                if (ImGui.button("${Icons.REFRESH} Learn##start_learn")) {
                    MacroLearnState.startLearn(control.id)
                }
                itemTooltip("Arm Learn Mode. Then click any parameter slider or modulator property in Column 1 or 2.")
            } else {
                ImGui.textDisabled("[Max 4 targets]")
            }
        }

        // Hardware MIDI Learn (proposal §5.1: physical CC/note -> this Macro Knob/Switch).
        // Only the global Column 3 bank is addressable via "Macro/knob_N"/"Macro/switch_N" paths
        // (MidiMappingManager.onMidiEvent dispatches against MacroEngine.globalBank() only) --
        // per-unit Rack banks don't have hardware mapping yet (proposal's Rack doc, Open Question 5).
        val midiPath = macroMidiPath(bank, control)
        if (midiPath != null) {
            ImGui.sameLine(0f, 8f)
            val isMidiLearning = parametersState.midiLearnTarget.let { it is MidiLearnTarget.MacroTarget && it.macroPath == midiPath }
            if (isMidiLearning) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.72f, 0.45f, 1.00f, 0.7f))
                if (ImGui.button("${Icons.REFRESH} Waiting for MIDI... (Cancel)##midi_learn_cancel")) {
                    parametersState.midiLearnTarget = null
                }
                ImGui.popStyleColor()
            } else {
                if (ImGui.button("${Icons.PLUS} MIDI Learn##midi_learn_start")) {
                    parametersState.midiLearnTarget = MidiLearnTarget.MacroTarget(midiPath, control.label.ifEmpty { control.id })
                    parametersState.midiLearnStartTimeMs = System.currentTimeMillis()
                    if (llm.slop.liquidlsd.midi.MidiEngine.getActiveDeviceCount() == 0) {
                        PopupManager.globalPendingMidiWarning = true
                    }
                }
                itemTooltip("Arm hardware MIDI Learn: next CC/Note received binds a physical controller to this Macro $typeBadge.")
            }
        }

        // Switch behavior selector
        if (control.isSwitch) {
            ImGui.spacing()
            ImGui.textDisabled("Behavior:")
            ImGui.sameLine(0f, 6f)
            val behaviors = arrayOf("Toggle (Latch)", "Momentary (Hold)", "Trigger (Pulse)")
            val currentIdx = when (control.switchBehavior) {
                SwitchBehavior.TOGGLE -> 0
                SwitchBehavior.MOMENTARY -> 1
                SwitchBehavior.TRIGGER -> 2
            }
            val imIdx = ImInt(currentIdx)
            ImGui.setNextItemWidth(150f)
            if (ImGui.combo("##switch_behavior", imIdx, behaviors)) {
                control.switchBehavior = when (imIdx.get()) {
                    0 -> SwitchBehavior.TOGGLE
                    1 -> SwitchBehavior.MOMENTARY
                    else -> SwitchBehavior.TRIGGER
                }
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // Bindings list
        if (control.bindings.isEmpty()) {
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                ImGui.textDisabled("No parameters bound. Click [Learn] then click any parameter in Column 1 or 2.")
            }
        } else {
            val toRemove = mutableListOf<Int>()
            for ((idx, binding) in control.bindings.withIndex()) {
                ImGui.pushID("binding_$idx")

                val enabledBool = ImBoolean(binding.enabled)
                if (ImGui.checkbox("##enabled", enabledBool)) {
                    binding.enabled = enabledBool.get()
                    MacroEngine.invalidate()
                }
                itemTooltip(if (binding.enabled) "Active: target parameter locked to this macro. Uncheck to release parameter for manual control." else "Disabled: parameter released for manual control. Check to resume macro lock.")

                ImGui.sameLine(0f, 6f)
                val targetText = if (binding.targetType == MacroTargetType.PARAM_BASE_VALUE) {
                    binding.parameterId
                } else {
                    "${binding.parameterId} [${binding.propertyName}]"
                }
                val textCol = if (binding.enabled) 0.95f else 0.5f
                ImGui.textColored(textCol, textCol, textCol, 1f, targetText)

                val btnSize = 20f
                val availW = ImGui.getContentRegionAvailX()
                ImGui.sameLine(ImGui.getCursorPosX() + availW - btnSize)
                if (ImGui.button("${Icons.TRASH}##del_$idx", btnSize, btnSize)) {
                    toRemove.add(idx)
                }
                itemTooltip("Delete this binding.")

                // Min/Max and Invert row
                ImGui.indent(18f)

                ImGui.setNextItemWidth(65f)
                val minVal = floatArrayOf(binding.minVal)
                if (ImGui.dragFloat("Min", minVal, 0.01f, -10f, 10f, "%.2f")) {
                    binding.minVal = minVal[0]
                }
                itemTooltip("Output value when macro is at 0.0.")

                ImGui.sameLine(0f, 8f)
                ImGui.setNextItemWidth(65f)
                val maxVal = floatArrayOf(binding.maxVal)
                if (ImGui.dragFloat("Max", maxVal, 0.01f, -10f, 10f, "%.2f")) {
                    binding.maxVal = maxVal[0]
                }
                itemTooltip("Output value when macro is at 1.0.")

                ImGui.sameLine(0f, 8f)
                val invertBool = ImBoolean(binding.inverted)
                if (ImGui.checkbox("Invert", invertBool)) {
                    binding.inverted = invertBool.get()
                }
                itemTooltip("Invert travel direction.")

                // Response curve row
                ImGui.setNextItemWidth(100f)
                val curves = arrayOf("Linear", "Exponential", "Logarithmic", "S-Curve", "Step")
                val currentCurveIdx = when (binding.curve) {
                    MacroCurveType.LINEAR -> 0
                    MacroCurveType.EXPONENTIAL -> 1
                    MacroCurveType.LOGARITHMIC -> 2
                    MacroCurveType.S_CURVE -> 3
                    MacroCurveType.STEP -> 4
                }
                val curveImIdx = ImInt(currentCurveIdx)
                if (ImGui.combo("Curve", curveImIdx, curves)) {
                    binding.curve = when (curveImIdx.get()) {
                        0 -> MacroCurveType.LINEAR
                        1 -> MacroCurveType.EXPONENTIAL
                        2 -> MacroCurveType.LOGARITHMIC
                        3 -> MacroCurveType.S_CURVE
                        else -> MacroCurveType.STEP
                    }
                }

                if (binding.curve == MacroCurveType.STEP) {
                    ImGui.sameLine(0f, 8f)
                    ImGui.setNextItemWidth(50f)
                    val steps = intArrayOf(binding.stepCount)
                    if (ImGui.dragInt("Steps", steps, 1f, 2, 64)) {
                        binding.stepCount = steps[0].coerceIn(2, 64)
                    }
                    itemTooltip("Number of quantized steps across the travel range.")
                }

                ImGui.unindent(18f)
                ImGui.spacing()
                ImGui.popID()
            }

            if (toRemove.isNotEmpty()) {
                toRemove.sortedDescending().forEach { control.bindings.removeAt(it) }
                MacroEngine.invalidate()
            }
        }

        ImGui.popID()
    }

    /**
     * Resolves the "Macro/knob_N" / "Macro/switch_N" MIDI mapping path for [control] within
     * [bank], matching the format [llm.slop.liquidlsd.midi.MidiMappingManager.onMidiEvent]
     * dispatches against. Returns null for anything other than the global bank (per-unit Rack
     * banks aren't hardware-mappable yet) or if [control] isn't found in it.
     */
    private fun macroMidiPath(bank: MacroBank, control: MacroControl): String? {
        if (bank !== MacroEngine.globalBank()) return null
        val knobIdx = bank.knobs.indexOf(control)
        if (knobIdx >= 0) return "Macro/knob_${knobIdx + 1}"
        val switchIdx = bank.switches.indexOf(control)
        if (switchIdx >= 0) return "Macro/switch_${switchIdx + 1}"
        return null
    }
}
