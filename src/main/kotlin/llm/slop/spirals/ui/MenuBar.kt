package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.patches.PatchManager
import mu.KotlinLogging
import llm.slop.spirals.midi.MidiEngine

class MenuBar(
    private val projectManager: ProjectManager,
    private val popupManager: PopupManager,
    private val patchState: PatchGridState,
    private val onTriggerExitFlow: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onOpenAudioEngineMonitor: () -> Unit,
    private val onOpenPlaylistEditor: () -> Unit,
    private val onToggleAssetManagement: () -> Unit
) {
    private val logger = KotlinLogging.logger {}

    fun draw(mixer: Mixer) {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New Project")) {
                    if (PatchManager.isGlobalPatchDirty(mixer)) {
                        projectManager.pendingProjectAction = ProjectManager.PendingProjectAction.NEW
                        popupManager.pendingOpenConfirmPopup = true
                    } else {
                        projectManager.performNewProject(mixer)
                    }
                }
                if (ImGui.menuItem("Load Project...")) {
                    if (PatchManager.isGlobalPatchDirty(mixer)) {
                        projectManager.pendingProjectAction = ProjectManager.PendingProjectAction.LOAD
                        popupManager.pendingOpenConfirmPopup = true
                    } else {
                        projectManager.performLoadProject()
                    }
                }
                if (ImGui.menuItem("Save Project")) {
                    projectManager.saveGlobalPatch(mixer, false)
                }
                if (ImGui.menuItem("Save Project As...")) {
                    projectManager.saveGlobalPatch(mixer, true)
                }
                ImGui.separator()
                if (ImGui.menuItem("Exit")) {
                    logger.info { "Exit clicked" }
                    onTriggerExitFlow()
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
            if (isMidiLearn) {
                ImGui.popStyleColor()
            }

            if (ImGui.menuItem("Settings")) {
                onOpenSettings()
            }

            val isAudioActive = llm.slop.spirals.audio.AudioEngine.isActive()
            if (!isAudioActive && UITheme.audioEngineEnabled) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f) // orange warning
            }
            val audioEngineLabel = if (!isAudioActive && UITheme.audioEngineEnabled) "Audio Engine ⚠" else "Audio Engine"
            if (ImGui.menuItem(audioEngineLabel)) {
                onOpenAudioEngineMonitor()
            }
            if (!isAudioActive && UITheme.audioEngineEnabled) {
                ImGui.popStyleColor()
            }

            if (ImGui.menuItem("Playlist Editor")) {
                onOpenPlaylistEditor()
            }

            if (ImGui.menuItem("Asset Manager (F3)")) {
                onToggleAssetManagement()
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
            ImGui.popStyleColor()

            ImGui.endMainMenuBar()
        }
    }
}
