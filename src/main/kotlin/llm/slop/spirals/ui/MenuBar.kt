package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.patches.PatchManager
import mu.KotlinLogging
import llm.slop.spirals.midi.MidiEngine

class MenuBar(
    private val popupManager: PopupManager,
    private val patchState: PatchGridState,
    private val onTriggerExitFlow: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onOpenAudioEngineMonitor: () -> Unit
) {
    private val logger = KotlinLogging.logger {}

    fun draw(mixer: Mixer) {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.beginMenu("New Patch")) {
                    if (ImGui.menuItem("To Deck A")) {
                        mixer.deckA.reset()
                        PatchManager.activePresetA = null
                        PatchManager.cachedDtoA = null
                    }
                    if (ImGui.menuItem("To Deck B")) {
                        mixer.deckB.reset()
                        PatchManager.activePresetB = null
                        PatchManager.cachedDtoB = null
                    }
                    if (ImGui.menuItem("To Deck C")) {
                        mixer.deckC.reset()
                        PatchManager.activePresetC = null
                        PatchManager.cachedDtoC = null
                    }
                    ImGui.endMenu()
                }
                ImGui.separator()
                if (ImGui.menuItem("Exit")) {
                    logger.info { "Exit clicked" }
                    onTriggerExitFlow()
                }
                ImGui.endMenu()
            }

            if (ImGui.beginMenu("Randomize")) {
                if (ImGui.selectable("All", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                    PatchGridUndo.pushUndoState(patchState, mixer)
                    mixer.deckA.randomizeModulators()
                    mixer.deckB.randomizeModulators()
                    mixer.deckC.randomizeModulators()
                    listOf(mixer.crossfade, mixer.masterAlpha).forEach { param ->
                        val randomized = param.modulators.map { it.randomizeActiveValues() }
                        param.modulators.clear()
                        param.modulators.addAll(randomized)
                        param.randomizeBaseValue()
                    }
                }
                if (ImGui.selectable("Deck A", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                    PatchGridUndo.pushUndoState(patchState, mixer)
                    mixer.deckA.randomizeModulators()
                }
                if (ImGui.selectable("Deck B", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                    PatchGridUndo.pushUndoState(patchState, mixer)
                    mixer.deckB.randomizeModulators()
                }
                if (ImGui.selectable("Deck C", false, imgui.flag.ImGuiSelectableFlags.DontClosePopups)) {
                    PatchGridUndo.pushUndoState(patchState, mixer)
                    mixer.deckC.randomizeModulators()
                }
                ImGui.endMenu()
            }

            // MIDI Map toggle button
            val isMidiLearn = patchState.isMidiLearnMode
            if (isMidiLearn) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f) // orange
            }
            if (ImGui.menuItem("MIDI Map", "", isMidiLearn)) {
                patchState.isMidiLearnMode = !isMidiLearn
                if (!patchState.isMidiLearnMode) {
                    patchState.midiLearnTarget = null
                } else {
                    if (MidiEngine.getActiveDeviceCount() == 0) {
                        popupManager.pendingOpenMidiWarningPopup = true
                    }
                }
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Toggle MIDI Learn mode. Click a control, then move a knob/fader on your controller to bind it.")
            }
            if (isMidiLearn) {
                ImGui.popStyleColor()
            }

            if (ImGui.menuItem("Settings")) {
                onOpenSettings()
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Configure interface scaling, JACK settings, startup behavior, and MIDI profiles.")
            }

            val isAudioActive = llm.slop.spirals.audio.AudioEngine.isActive()
            if (!isAudioActive && UITheme.audioEngineEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f) // orange warning
            }
            val audioEngineLabel = if (!isAudioActive && UITheme.audioEngineEnabled) "Audio Engine [!]" else "Audio Engine"
            if (ImGui.menuItem(audioEngineLabel)) {
                onOpenAudioEngineMonitor()
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("View real-time input waveforms, estimated BPM, and sound-derived modulation signals.")
            }
            if (!isAudioActive && UITheme.audioEngineEnabled) {
                ImGui.popStyleColor()
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("Documentation")) {
                    DocManager.openDocumentation()
                }
                ImGui.endMenu()
            }

            val tooltipsEnabled = UITheme.tooltipsEnabled
            if (tooltipsEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 0.2f, 1.0f) // green
            } else {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.2f, 0.2f, 1.0f) // red
            }
            if (ImGui.menuItem("Tooltips", "", tooltipsEnabled)) {
                UITheme.tooltipsEnabled = !tooltipsEnabled
                UITheme.saveSettings()
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Toggle visibility of helpful on-hover tooltips across the application.")
            }
            ImGui.popStyleColor()

            ImGui.endMainMenuBar()
        }
    }
}
