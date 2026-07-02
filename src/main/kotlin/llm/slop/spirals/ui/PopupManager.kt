package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiCol
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.patches.PatchManager

class PopupManager(
    private val projectManager: ProjectManager,
    private val onTriggerExit: () -> Unit,
    private val onSaveDeck: (String, Deck, Boolean) -> Unit,
    private val onExecuteDeckAction: (Deck, Boolean, PendingDeckAction, String?) -> Unit
) {
    var pendingOpenConfirmPopup = false
    var pendingOpenExitPopup = false
    var pendingOpenMidiWarningPopup = false

    enum class PendingDeckAction {
        NONE, NEW, LOAD_FILE, LOAD_PRESET
    }
    
    var pendingDeckActionA = PendingDeckAction.NONE
    var pendingDeckActionB = PendingDeckAction.NONE
    var pendingDeckTargetPresetA: String? = null
    var pendingDeckTargetPresetB: String? = null

    fun drawConfirmPopup(mixer: Mixer, displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            ImGuiCond.Appearing, 0.5f, 0.5f
        )
        
        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                    ImGuiWindowFlags.NoMove            or
                    ImGuiWindowFlags.NoCollapse

        if (ImGui.beginPopupModal("Save Changes?##confirm", flags)) {
            ImGui.text("You have unsaved changes. Do you want to save them before proceeding?")
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()
            
            if (ImGui.button("Save", 80f, 0f)) {
                val saved = projectManager.saveGlobalPatch(mixer, false)
                if (saved) {
                    when (projectManager.pendingProjectAction) {
                        ProjectManager.PendingProjectAction.NEW -> projectManager.performNewProject(mixer)
                        ProjectManager.PendingProjectAction.LOAD -> projectManager.performLoadProject()
                        ProjectManager.PendingProjectAction.LOAD_SETLIST -> projectManager.pendingSetlistFile?.let { projectManager.performLoadFromSetlist(it) }
                        else -> {}
                    }
                    projectManager.pendingProjectAction = ProjectManager.PendingProjectAction.NONE
                }
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                when (projectManager.pendingProjectAction) {
                    ProjectManager.PendingProjectAction.NEW -> projectManager.performNewProject(mixer)
                    ProjectManager.PendingProjectAction.LOAD -> projectManager.performLoadProject()
                    ProjectManager.PendingProjectAction.LOAD_SETLIST -> projectManager.pendingSetlistFile?.let { projectManager.performLoadFromSetlist(it) }
                    else -> {}
                }
                projectManager.pendingProjectAction = ProjectManager.PendingProjectAction.NONE
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                projectManager.pendingProjectAction = ProjectManager.PendingProjectAction.NONE
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawExitPopup(mixer: Mixer, displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            ImGuiCond.Appearing, 0.5f, 0.5f
        )
        
        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                    ImGuiWindowFlags.NoMove            or
                    ImGuiWindowFlags.NoCollapse

        val isDirty = PatchManager.isGlobalPatchDirty(mixer)

        if (ImGui.beginPopupModal("Exit Spirals?##confirm", flags)) {
            if (isDirty) {
                ImGui.text("You have unsaved changes. Do you want to save them before exiting?")
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
                
                if (ImGui.button("Save & Exit", 120f, 0f)) {
                    projectManager.pendingProjectAction = ProjectManager.PendingProjectAction.EXIT
                    val saved = projectManager.saveGlobalPatch(mixer, false)
                    if (saved) {
                        projectManager.pendingProjectAction = ProjectManager.PendingProjectAction.NONE
                        onTriggerExit()
                    }
                    ImGui.closeCurrentPopup()
                }
                ImGui.sameLine()
                if (ImGui.button("Discard & Exit", 120f, 0f)) {
                    projectManager.pendingProjectAction = ProjectManager.PendingProjectAction.NONE
                    onTriggerExit()
                    ImGui.closeCurrentPopup()
                }
                ImGui.sameLine()
                if (ImGui.button("Cancel", 120f, 0f)) {
                    projectManager.pendingProjectAction = ProjectManager.PendingProjectAction.NONE
                    ImGui.closeCurrentPopup()
                }
            } else {
                ImGui.text("Are you sure you want to exit?")
                ImGui.text("Accidentally exiting during a show would be bad!")
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()

                if (ImGui.button("Exit", 120f, 0f)) {
                    onTriggerExit()
                    ImGui.closeCurrentPopup()
                }
                ImGui.sameLine()
                if (ImGui.button("Cancel", 120f, 0f)) {
                    ImGui.closeCurrentPopup()
                }
            }
            ImGui.endPopup()
        }
    }

    fun drawMidiWarningPopup(displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            ImGuiCond.Appearing, 0.5f, 0.5f
        )
        
        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                    ImGuiWindowFlags.NoMove            or
                    ImGuiWindowFlags.NoCollapse

        if (ImGui.beginPopupModal("No MIDI Devices Connected##midi_warning", flags)) {
            ImGui.textWrapped("There are currently no MIDI input devices detected by the system.")
            ImGui.spacing()
            ImGui.textWrapped("You can still map parameters by clicking them, but you will need")
            ImGui.textWrapped("to plug in a MIDI hardware controller to send actual control values.")
            ImGui.spacing()
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f)
            ImGui.textWrapped("A background watchdog is active. Plugging in a MIDI controller")
            ImGui.textWrapped("will automatically activate it within a few seconds.")
            ImGui.popStyleColor()
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()
            
            if (ImGui.button("OK", ImGui.getContentRegionAvailX(), 0f)) {
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawDeckConfirmPopups(deckA: Deck, deckB: Deck) {
        // Deck A Confirmation
        if (pendingDeckActionA != PendingDeckAction.NONE) {
            ImGui.openPopup("Save Changes Deck A?##confirm")
        }
        if (ImGui.beginPopupModal("Save Changes Deck A?##confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes in Deck A. Save before proceeding?")
            ImGui.spacing()
            if (ImGui.button("Save", 80f, 0f)) {
                val activeName = PatchManager.activePresetA
                if (activeName != null) {
                    onSaveDeck(activeName, deckA, true)
                } else {
                    onSaveDeck("Untitled_A", deckA, true)
                }
                onExecuteDeckAction(deckA, true, pendingDeckActionA, pendingDeckTargetPresetA)
                pendingDeckActionA = PendingDeckAction.NONE
                pendingDeckTargetPresetA = null
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                onExecuteDeckAction(deckA, true, pendingDeckActionA, pendingDeckTargetPresetA)
                pendingDeckActionA = PendingDeckAction.NONE
                pendingDeckTargetPresetA = null
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                pendingDeckActionA = PendingDeckAction.NONE
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }

        // Deck B Confirmation
        if (pendingDeckActionB != PendingDeckAction.NONE) {
            ImGui.openPopup("Save Changes Deck B?##confirm")
        }
        
        if (ImGui.beginPopupModal("Save Changes Deck B?##confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes in Deck B. Save before proceeding?")
            ImGui.spacing()
            if (ImGui.button("Save", 80f, 0f)) {
                val activeName = PatchManager.activePresetB
                if (activeName != null) {
                    onSaveDeck(activeName, deckB, false)
                } else {
                    onSaveDeck("Untitled_B", deckB, false)
                }
                onExecuteDeckAction(deckB, false, pendingDeckActionB, pendingDeckTargetPresetB)
                pendingDeckActionB = PendingDeckAction.NONE
                pendingDeckTargetPresetB = null
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                onExecuteDeckAction(deckB, false, pendingDeckActionB, pendingDeckTargetPresetB)
                pendingDeckActionB = PendingDeckAction.NONE
                pendingDeckTargetPresetB = null
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                pendingDeckActionB = PendingDeckAction.NONE
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }
}
