package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.PresetManager

class PopupManager(
    private val onTriggerExit: () -> Unit,
    private val onSaveDeck: (String, Deck, Boolean) -> Unit
) {
    var pendingOpenExitPopup = false
    var pendingOpenMidiWarningPopup = false

    private var pendingConfirmDeck: Deck? = null
    private var pendingConfirmLabel: String? = null
    private var pendingConfirmCallback: (() -> Unit)? = null

    private var pendingSourceDeck: Deck? = null
    private var pendingSourceLabel: String? = null
    private var pendingSourceOldName: String? = null
    private var pendingSourceNewName: String? = null
    private var pendingSourceCallback: (() -> Unit)? = null

    fun requestDeckConfirm(deck: Deck, label: String, onProceed: () -> Unit) {
        pendingConfirmDeck = deck
        pendingConfirmLabel = label
        pendingConfirmCallback = onProceed
    }

    fun clearDeckConfirm() {
        pendingConfirmDeck = null
        pendingConfirmLabel = null
        pendingConfirmCallback = null
    }

    fun requestSourceChangeConfirm(
        deck: Deck,
        label: String,
        oldSourceName: String,
        newSourceName: String,
        onProceed: () -> Unit
    ) {
        pendingSourceDeck = deck
        pendingSourceLabel = label
        pendingSourceOldName = oldSourceName
        pendingSourceNewName = newSourceName
        pendingSourceCallback = onProceed
    }

    fun clearSourceChangeConfirm() {
        pendingSourceDeck = null
        pendingSourceLabel = null
        pendingSourceOldName = null
        pendingSourceNewName = null
        pendingSourceCallback = null
    }

    fun drawExitPopup(mixer: Mixer, displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            ImGuiCond.Appearing, 0.5f, 0.5f
        )
        
        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                    ImGuiWindowFlags.NoMove            or
                    ImGuiWindowFlags.NoCollapse

        if (ImGui.beginPopupModal("Exit Liquid LSD?##confirm", flags)) {
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

    fun drawDeckConfirmPopups(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        val deck = pendingConfirmDeck ?: return
        val label = pendingConfirmLabel ?: "Deck"
        val onProceed = pendingConfirmCallback ?: return

        val popupId = "Save Changes $label?##confirm"
        ImGui.openPopup(popupId)

        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes in $label. Save before proceeding?")
            ImGui.spacing()

            if (ImGui.button("Save", 80f, 0f)) {
                val activeName = when {
                    deck === mixer.deckA -> session.presetManager.activePresetA
                    deck === mixer.deckB -> session.presetManager.activePresetB
                    deck === mixer.deckBG -> session.presetManager.activePresetBG
                    else -> session.presetManager.activePresetPV
                }
                onSaveDeck(activeName ?: "Untitled_${label.replace(" ", "")}", deck, deck === mixer.deckA)
                onProceed()
                clearDeckConfirm()
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                onProceed()
                clearDeckConfirm()
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                clearDeckConfirm()
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    fun drawSourceChangeConfirmPopup(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer) {
        val deck = pendingSourceDeck ?: return
        val label = pendingSourceLabel ?: "Deck"
        val oldName = pendingSourceOldName ?: "Current Source"
        val newName = pendingSourceNewName ?: "New Source"
        val onProceed = pendingSourceCallback ?: return

        val activeName = when {
            deck === mixer.deckA -> session.presetManager.activePresetA
            deck === mixer.deckB -> session.presetManager.activePresetB
            deck === mixer.deckBG -> session.presetManager.activePresetBG
            else -> session.presetManager.activePresetPV
        }

        val popupId = "Change Visual Source $label?##source_confirm"
        ImGui.openPopup(popupId)

        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            if (!activeName.isNullOrBlank()) {
                ImGui.textWrapped("Changing visual source for $label from '$oldName' to '$newName' will unload preset '$activeName' and discard its source parameters.")
            } else {
                ImGui.textWrapped("Changing visual source for $label from '$oldName' to '$newName' will reset all source parameters.")
            }
            ImGui.spacing()
            ImGui.text("Are you sure you want to proceed?")
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.6f, 0.2f, 0.2f, 1f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.75f, 0.25f, 0.25f, 1f))
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.9f, 0.3f, 0.3f, 1f))
            if (ImGui.button("Change Source", 120f, 0f)) {
                onProceed()
                clearSourceChangeConfirm()
                ImGui.closeCurrentPopup()
            }
            ImGui.popStyleColor(3)
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                clearSourceChangeConfirm()
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }
}
