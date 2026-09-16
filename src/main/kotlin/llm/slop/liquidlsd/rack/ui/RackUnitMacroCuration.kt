package llm.slop.liquidlsd.rack.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.*
import llm.slop.liquidlsd.rack.RackUnit
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.MacroKnobWidget
import llm.slop.liquidlsd.ui.UITheme

/**
 * Collapsible curation drawer rendered inside a [RackUnit] allowing the performer
 * to curate which unit parameters occupy which of its 8 knob slots and 4 switch slots.
 */
object RackUnitMacroCuration {

    private val labelBuf = ImString(64)
    private var activeTab: Int = 0 // 0 = Knobs, 1 = Switches

    fun draw(
        session: SessionContext,
        unit: RackUnit,
        width: Float
    ) {
        val dl = ImGui.getWindowDrawList()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        ImGui.pushID("curation_${unit.id}")

        // Background drawer styling (recessed dark metallic panel with cyan accent border)
        val bgCol = ImGui.colorConvertFloat4ToU32(0.12f, 0.13f, 0.16f, 0.98f)
        val borderCol = ImGui.colorConvertFloat4ToU32(0.20f, 0.55f, 0.75f, 0.80f)

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6.0f, 4.0f)

        // Header bar
        ImGui.spacing()
        ImGui.pushStyleColor(ImGuiCol.Text, 0.25f, 0.85f, 0.95f, 1.0f)
        ImGui.textUnformatted("${Icons.SETTINGS} MACRO CURATION — ${unit.label}")
        ImGui.popStyleColor()
        ImGui.sameLine()

        // Tab switcher: [ KNOBS (8) ] [ SWITCHES (4) ]
        if (activeTab == 0) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.50f, 0.70f, 1.0f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.16f, 0.18f, 0.22f, 1.0f)
        }
        if (ImGui.button("Knobs (8)##tab_knobs_${unit.id}")) {
            activeTab = 0
        }
        ImGui.popStyleColor()
        ImGui.sameLine()

        if (activeTab == 1) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.50f, 0.70f, 1.0f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.16f, 0.18f, 0.22f, 1.0f)
        }
        if (ImGui.button("Switches (4)##tab_switches_${unit.id}")) {
            activeTab = 1
        }
        ImGui.popStyleColor()
        ImGui.sameLine()

        // Close drawer button
        val closeX = width - 70f
        if (closeX > ImGui.getCursorPosX()) {
            ImGui.setCursorPosX(closeX)
        }
        if (ImGui.button("Close##close_curation_${unit.id}")) {
            unit.isMacroCurationOpen = false
        }

        ImGui.separator()
        ImGui.spacing()

        val namedParams = unit.getNamedParameters()
        val paramNames = listOf("(None)") + namedParams.keys.toList()

        if (activeTab == 0) {
            drawKnobSlots(session, unit, paramNames)
        } else {
            drawSwitchSlots(session, unit, paramNames)
        }

        ImGui.spacing()
        ImGui.separator()

        ImGui.popStyleVar(2)
        ImGui.popID()
    }

    private fun drawKnobSlots(
        session: SessionContext,
        unit: RackUnit,
        paramNames: List<String>
    ) {
        val bank = unit.macroBank
        val colWidth = 280.0f

        // Draw 8 knobs in 4 columns x 2 rows
        for (row in 0..1) {
            for (col in 0..3) {
                val idx = row * 4 + col
                if (idx >= bank.knobs.size) break
                val knob = bank.knobs[idx]

                if (col > 0) ImGui.sameLine(0f, 12f)
                ImGui.beginGroup()
                ImGui.pushID("knob_slot_${unit.id}_$idx")

                // Top: slot badge & knob widget
                ImGui.textColored(0.2f, 0.85f, 1.0f, 1.0f, "KNOB ${idx + 1}")
                ImGui.sameLine()
                ImGui.setNextItemWidth(120f)
                labelBuf.set(knob.label)
                if (ImGui.inputText("##lbl", labelBuf)) {
                    knob.label = labelBuf.get()
                }

                // Dial preview
                val isSelected = MacroLearnState.selectedControlId == knob.id
                val isLearning = MacroLearnState.isControlLearning(knob.id)
                MacroKnobWidget.draw(
                    session = session,
                    id = "curation_${unit.id}_knob_$idx",
                    label = knob.label.ifEmpty { "K${idx + 1}" },
                    value = knob.value,
                    diameter = 44f,
                    isSelected = isSelected,
                    isLearning = isLearning,
                    onSelect = { MacroLearnState.selectedControlId = knob.id },
                    onToggleLearn = {
                        if (isLearning) MacroLearnState.cancelLearn() else MacroLearnState.startLearn(knob.id)
                    },
                    onChanged = { knob.value = it }
                )
                ImGui.sameLine(0f, 8f)

                // Binding controls
                ImGui.beginGroup()
                val primaryBinding = knob.bindings.firstOrNull()

                // Target parameter combo
                val currentParam = primaryBinding?.parameterId ?: "(None)"
                val currentIdx = paramNames.indexOf(currentParam).coerceAtLeast(0)
                val imIdx = ImInt(currentIdx)
                ImGui.setNextItemWidth(130f)
                if (ImGui.combo("##param_picker", imIdx, paramNames.toTypedArray())) {
                    val chosen = paramNames[imIdx.get()]
                    if (chosen == "(None)") {
                        knob.bindings.clear()
                    } else {
                        if (primaryBinding == null) {
                            knob.bindings.add(
                                MacroBinding(
                                    unitInstanceId = unit.id,
                                    parameterId = chosen,
                                    targetType = MacroTargetType.PARAM_BASE_VALUE
                                )
                            )
                        } else {
                            knob.bindings[0] = primaryBinding.copy(
                                unitInstanceId = unit.id,
                                parameterId = chosen
                            )
                        }
                        if (knob.label.isEmpty()) {
                            knob.label = chosen.take(8).uppercase()
                        }
                    }
                    MacroEngine.invalidate()
                }

                if (primaryBinding != null) {
                    // Min / Max range
                    ImGui.setNextItemWidth(60f)
                    val minArr = floatArrayOf(primaryBinding.minVal)
                    if (ImGui.dragFloat("##min", minArr, 0.01f, -10f, 10f, "Min:%.2f")) {
                        primaryBinding.minVal = minArr[0]
                    }
                    ImGui.sameLine(0f, 4f)
                    ImGui.setNextItemWidth(60f)
                    val maxArr = floatArrayOf(primaryBinding.maxVal)
                    if (ImGui.dragFloat("##max", maxArr, 0.01f, -10f, 10f, "Max:%.2f")) {
                        primaryBinding.maxVal = maxArr[0]
                    }

                    // Invert checkbox
                    val inv = ImBoolean(primaryBinding.inverted)
                    if (ImGui.checkbox("Inv##inv", inv)) {
                        primaryBinding.inverted = inv.get()
                    }
                    ImGui.sameLine(0f, 6f)

                    // Learn button
                    if (ImGui.button(if (isLearning) "Learning..." else "Learn##lrn")) {
                        if (isLearning) MacroLearnState.cancelLearn() else MacroLearnState.startLearn(knob.id)
                    }
                }

                ImGui.endGroup()

                ImGui.popID()
                ImGui.endGroup()
            }
            ImGui.spacing()
        }
    }

    private fun drawSwitchSlots(
        session: SessionContext,
        unit: RackUnit,
        paramNames: List<String>
    ) {
        val bank = unit.macroBank

        // 4 switches
        for (i in bank.switches.indices) {
            val sw = bank.switches[i]
            if (i > 0) ImGui.sameLine(0f, 16f)

            ImGui.beginGroup()
            ImGui.pushID("sw_slot_${unit.id}_$i")

            ImGui.textColored(0.95f, 0.75f, 0.15f, 1.0f, "SWITCH ${i + 1}")
            ImGui.sameLine()
            ImGui.setNextItemWidth(100f)
            labelBuf.set(sw.label)
            if (ImGui.inputText("##sw_lbl", labelBuf)) {
                sw.label = labelBuf.get()
            }

            // Switch button toggle
            val swActive = sw.value >= 0.5f
            if (swActive) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.95f, 0.75f, 0.15f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.Text, 0.1f, 0.1f, 0.1f, 1.0f)
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.22f, 0.25f, 1.0f)
                ImGui.pushStyleColor(ImGuiCol.Text, 0.60f, 0.62f, 0.65f, 1.0f)
            }
            if (ImGui.button(if (swActive) "ON##btn" else "OFF##btn", 60f, 24f)) {
                sw.onPress()
            }
            ImGui.popStyleColor(2)

            // Switch behavior
            val behaviors = arrayOf("Toggle", "Momentary", "Trigger")
            val currentIdx = when (sw.switchBehavior) {
                SwitchBehavior.TOGGLE -> 0
                SwitchBehavior.MOMENTARY -> 1
                SwitchBehavior.TRIGGER -> 2
            }
            val imIdx = ImInt(currentIdx)
            ImGui.setNextItemWidth(90f)
            if (ImGui.combo("##beh", imIdx, behaviors)) {
                sw.switchBehavior = when (imIdx.get()) {
                    0 -> SwitchBehavior.TOGGLE
                    1 -> SwitchBehavior.MOMENTARY
                    else -> SwitchBehavior.TRIGGER
                }
            }

            // Target param
            val primaryBinding = sw.bindings.firstOrNull()
            val currentParam = primaryBinding?.parameterId ?: "(None)"
            val paramIdx = paramNames.indexOf(currentParam).coerceAtLeast(0)
            val imPIdx = ImInt(paramIdx)
            ImGui.setNextItemWidth(120f)
            if (ImGui.combo("##sw_param", imPIdx, paramNames.toTypedArray())) {
                val chosen = paramNames[imPIdx.get()]
                if (chosen == "(None)") {
                    sw.bindings.clear()
                } else {
                    if (primaryBinding == null) {
                        sw.bindings.add(
                            MacroBinding(
                                unitInstanceId = unit.id,
                                parameterId = chosen,
                                targetType = MacroTargetType.PARAM_BASE_VALUE
                            )
                        )
                    } else {
                        sw.bindings[0] = primaryBinding.copy(
                            unitInstanceId = unit.id,
                            parameterId = chosen
                        )
                    }
                }
                MacroEngine.invalidate()
            }

            ImGui.popID()
            ImGui.endGroup()
        }
    }
}
