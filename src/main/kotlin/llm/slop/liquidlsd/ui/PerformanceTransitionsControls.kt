package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File

/**
 * Transitions row (MASTER tab) line left of the knobs, beside the TRANS title badge drawn by
 * [PerformanceMatrixPanel]: a transition picker button showing the active transition with a
 * modified indicator (`*`), TransitionQueue prev/status/next navigation, and the randomize die.
 */
internal object PerformanceTransitionsControls {

    fun draw(
        session: SessionContext,
        mixer: Mixer,
        startX: Float,
        headerY: Float,
        headerH: Float,
        width: Float
    ) {
        val gap = 4f
        val dl = ImGui.getWindowDrawList()

        ImGui.setCursorScreenPos(startX, headerY)
        ImGui.beginGroup()

        // Transition Queue nav: Prev + Count + Next, plus the die when randomization is on.
        val navBtnW = (headerH * 0.9f).coerceIn(22f, 28f)
        val qTextW = 46f
        val diceW = if (session.uiTheme.randomizationEnabled) gap + navBtnW else 0f

        // Transition Picker button takes the rest of the line.
        val transBtnW = (width - gap - navBtnW - 2f - qTextW - 2f - navBtnW - diceW).coerceAtLeast(60f)

        // 1. Transition Picker Button [ Settings Icon + Name * ]
        val transName = mixer.transitionFilter?.displayName ?: "Default Blend"
        val isTransModified = mixer.transitionFilter?.let { filter ->
            filter.dryWet.baseValue != 1.0f ||
                filter.parameters.any { (name, param) ->
                    val defaultVal = filter.header.INPUTS.find { it.NAME == name }?.DEFAULT?.toString()?.toFloatOrNull() ?: 0.0f
                    kotlin.math.abs(param.baseValue - defaultVal) > 0.001f || param.modulators.any { !it.bypassed }
                }
        } ?: false
        val modBadge = if (isTransModified) " *" else ""

        if (ImGui.button("${Icons.SETTINGS} $transName$modBadge##perf_trans_picker_btn", transBtnW, headerH)) {
            ShaderPickerPopup.show("Select Mixer Transition", ShaderPickerPopup.PickerType.MIXER_TRANSITION) { id ->
                mixer.setTransition(id)
            }
        }
        itemTooltip("Select ISF transition shader or blend mode.\nActive: $transName$modBadge")

        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = File(payload)
                if (file.extension.equals("lsdtrans", ignoreCase = true) && file.exists()) {
                    session.presetRepository.loadTransitionPresetAsync(file).thenAccept { dto ->
                        mixer.applyTransitionPreset(dto)
                    }
                } else {
                    val id = if (file.extension.equals("fs", ignoreCase = true) || file.extension.equals("isf", ignoreCase = true)) {
                        file.nameWithoutExtension
                    } else {
                        file.nameWithoutExtension.ifBlank { file.name }
                    }
                    mixer.setTransition(id)
                }
            }
            ImGui.endDragDropTarget()
        }

        ImGui.sameLine(0f, gap)

        // 2. Transition Queue Navigation [ < ] [ N/Total ] [ > ]
        val transQ = TransitionQueueManager.queue
        val transQIdx = TransitionQueueManager.activeIndex
        val transCountStr = if (transQ.isNotEmpty() && transQIdx in transQ.indices) "${transQIdx + 1}/${transQ.size}" else if (transQ.isNotEmpty()) "-/${transQ.size}" else "--"

        val transQPrevKey = "Global/transQueuePrev"
        val transQPrevOscKey = "Mixer/transQueuePrev"
        val isMidiLearnTransQPrev = session.parametersState.isMidiTargetLearning(transQPrevKey)
        val isOscLearnTransQPrev = OscLearnState.isTargetLearning(transQPrevOscKey)
        val transQPrevMidiMapping = session.midiMappingManager.getMappingForParameter(transQPrevKey)
        val transQPrevMidiText = transQPrevMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val transPrevX = ImGui.getCursorScreenPosX()
        val transPrevY = ImGui.getCursorScreenPosY()
        if (ImGui.button("<##perf_trans_q_prev", navBtnW, headerH)) {
            TransitionQueueManager.advancePrevious(mixer)
        }
        if (isMidiLearnTransQPrev) {
            dl.addRect(transPrevX - 1f, transPrevY - 1f, transPrevX + navBtnW + 1f, transPrevY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_trans_q_prev_ctx")) {
            ImGui.textDisabled("Transition Queue Prev (<)")
            ImGui.separator()
            if (ImGui.menuItem("Trigger Previous")) {
                TransitionQueueManager.advancePrevious(mixer)
            }
            ImGui.separator()
            if (isMidiLearnTransQPrev) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Trans Queue Prev)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(transQPrevKey))
                }
            }
            if (transQPrevMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(transQPrevKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnTransQPrev) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Trans Queue Prev)")) {
                    OscLearnState.startLearn(transQPrevOscKey, 0f, 1f, "Trans Queue Prev")
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Advance to previous transition in Transition Queue.$transQPrevMidiText\nRight-click for MIDI/OSC Learn.")

        ImGui.sameLine(0f, 2f)

        val transCurX = ImGui.getCursorScreenPosX()
        val transCurY = ImGui.getCursorScreenPosY()
        val genBgCol = ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.85f)
        val genTextCol = ImGui.colorConvertFloat4ToU32(0.80f, 0.85f, 0.95f, 1f)
        dl.addRectFilled(transCurX, transCurY, transCurX + qTextW, transCurY + headerH, genBgCol, 3f)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val sz = ImGui.calcTextSize(transCountStr)
            dl.addText(transCurX + (qTextW - sz.x) * 0.5f, transCurY + (headerH - sz.y) * 0.5f, genTextCol, transCountStr)
        }
        ImGui.invisibleButton("##perf_trans_q_idx", qTextW, headerH)
        itemTooltip("Transition Queue status: $transCountStr")

        ImGui.sameLine(0f, 2f)

        val transQNextKey = "Global/transQueueNext"
        val transQNextOscKey = "Mixer/transQueueNext"
        val isMidiLearnTransQNext = session.parametersState.isMidiTargetLearning(transQNextKey)
        val isOscLearnTransQNext = OscLearnState.isTargetLearning(transQNextOscKey)
        val transQNextMidiMapping = session.midiMappingManager.getMappingForParameter(transQNextKey)
        val transQNextMidiText = transQNextMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

        val transNextX = ImGui.getCursorScreenPosX()
        val transNextY = ImGui.getCursorScreenPosY()
        if (ImGui.button(">##perf_trans_q_next", navBtnW, headerH)) {
            TransitionQueueManager.advanceNext(mixer)
        }
        if (isMidiLearnTransQNext) {
            dl.addRect(transNextX - 1f, transNextY - 1f, transNextX + navBtnW + 1f, transNextY + headerH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
        }
        if (ImGui.beginPopupContextItem("perf_trans_q_next_ctx")) {
            ImGui.textDisabled("Transition Queue Next (>)")
            ImGui.separator()
            if (ImGui.menuItem("Trigger Next")) {
                TransitionQueueManager.advanceNext(mixer)
            }
            ImGui.separator()
            if (isMidiLearnTransQNext) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Trans Queue Next)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(transQNextKey))
                }
            }
            if (transQNextMidiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(transQNextKey)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearnTransQNext) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Trans Queue Next)")) {
                    OscLearnState.startLearn(transQNextOscKey, 0f, 1f, "Trans Queue Next")
                }
            }
            ImGui.endPopup()
        }
        itemTooltip("Advance to next transition in Transition Queue.$transQNextMidiText\nRight-click for MIDI/OSC Learn.")

        // 3. Randomize Die Button [ DICES ]
        if (session.uiTheme.randomizationEnabled) {
            ImGui.sameLine(0f, gap)
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.20f, 0.16f, 0.24f, 0.90f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.35f, 0.22f, 0.42f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.DICES}##perf_trans_rand", navBtnW, headerH)) {
                    ParametersUndo.pushUndoState(session.parametersState, mixer)
                    mixer.transitionFilter?.let { filter ->
                        filter.parameters.values.forEach { param ->
                            val min = param.minClamp
                            val max = param.maxClamp
                            param.baseValue = (min + Math.random().toFloat() * (max - min)).coerceIn(min, max)
                        }
                    }
                }
            }
            ImGui.popStyleColor(2)
            itemTooltip("Randomize transition parameters.\nClick to randomize with undo support.")
        }

        ImGui.endGroup()
    }
}
