package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiCol
import llm.slop.spirals.config.ProjectConfig
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.patches.PatchManager

class PopupManager(
    private val onTriggerExit: () -> Unit,
    private val onSaveDeck: (String, Deck, Boolean) -> Unit,
    private val onExecuteDeckAction: (Deck, Boolean, PendingDeckAction, String?) -> Unit
) {
    var pendingOpenExitPopup = false
    var pendingOpenMidiWarningPopup = false

    enum class PendingDeckAction {
        NONE, NEW, LOAD_FILE, LOAD_PRESET, DRAG_DROP, MOVE, COPY, SWAP
    }
    
    var pendingDeckActionA = PendingDeckAction.NONE
    var pendingDeckActionB = PendingDeckAction.NONE
    var pendingDeckActionC = PendingDeckAction.NONE

    var pendingDeckTargetPresetA: String? = null
    var pendingDeckTargetPresetB: String? = null
    var pendingDeckTargetPresetC: String? = null

    var pendingDeckSourceFileA: java.io.File? = null
    var pendingDeckSourceFileB: java.io.File? = null
    var pendingDeckSourceFileC: java.io.File? = null

    var pendingDeckUtilitySourceA: Deck? = null
    var pendingDeckUtilitySourceB: Deck? = null
    var pendingDeckUtilitySourceC: Deck? = null

    fun drawExitPopup(mixer: Mixer, displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            ImGuiCond.Appearing, 0.5f, 0.5f
        )
        
        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                    ImGuiWindowFlags.NoMove            or
                    ImGuiWindowFlags.NoCollapse

        if (ImGui.beginPopupModal("${ProjectConfig.App.EXIT_CONFIRM_TITLE}##confirm", flags)) {
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

    fun drawDeckConfirmPopups(mixer: Mixer) {
        drawDeckPopup(mixer, mixer.deckA, true)
        drawDeckPopup(mixer, mixer.deckB, false)
        drawDeckPopup(mixer, mixer.deckC, false, isDeckC = true)
    }

    private fun drawDeckPopup(mixer: Mixer, deck: Deck, isDeckA: Boolean, isDeckC: Boolean = false) {
        val action = when {
            isDeckC -> pendingDeckActionC
            isDeckA -> pendingDeckActionA
            else -> pendingDeckActionB
        }
        if (action == PendingDeckAction.NONE) return

        val label = when {
            isDeckC -> "Deck C"
            isDeckA -> "Deck A"
            else -> "Deck B"
        }
        val popupId = "Save Changes $label?##confirm"
        ImGui.openPopup(popupId)

        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes in $label. Save before proceeding?")
            ImGui.spacing()
            
            if (ImGui.button("Save", 80f, 0f)) {
                val activeName = when {
                    isDeckC -> PatchManager.activePresetC
                    isDeckA -> PatchManager.activePresetA
                    else -> PatchManager.activePresetB
                }
                onSaveDeck(activeName ?: "Untitled_${label.last()}", deck, isDeckA || isDeckC) // Note: UIManager handles Deck C mapping
                executeAction(mixer, deck, isDeckA, isDeckC, action)
                clearAction(isDeckA, isDeckC)
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                executeAction(mixer, deck, isDeckA, isDeckC, action)
                clearAction(isDeckA, isDeckC)
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                clearAction(isDeckA, isDeckC)
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    private fun executeAction(mixer: Mixer, deck: Deck, isDeckA: Boolean, isDeckC: Boolean, action: PendingDeckAction) {
        val targetPreset = when {
            isDeckC -> pendingDeckTargetPresetC
            isDeckA -> pendingDeckTargetPresetA
            else -> pendingDeckTargetPresetB
        }
        val sourceFile = when {
            isDeckC -> pendingDeckSourceFileC
            isDeckA -> pendingDeckSourceFileA
            else -> pendingDeckSourceFileB
        }
        val utilitySource = when {
            isDeckC -> pendingDeckUtilitySourceC
            isDeckA -> pendingDeckUtilitySourceA
            else -> pendingDeckUtilitySourceB
        }

        when (action) {
            PendingDeckAction.DRAG_DROP -> {
                sourceFile?.let { PatchManager.loadDeckPresetAsync(it, isDeckA, isDeckC) }
            }
            PendingDeckAction.MOVE -> {
                utilitySource?.let { PatchManager.moveDeck(mixer, it, deck) }
            }
            PendingDeckAction.COPY -> {
                utilitySource?.let { PatchManager.copyDeck(mixer, it, deck) }
            }
            PendingDeckAction.SWAP -> {
                utilitySource?.let { PatchManager.swapDecks(mixer, it, deck) }
            }
            else -> onExecuteDeckAction(deck, isDeckA || isDeckC, action, targetPreset)
        }
    }

    private fun clearAction(isDeckA: Boolean, isDeckC: Boolean) {
        if (isDeckC) {
            pendingDeckActionC = PendingDeckAction.NONE
            pendingDeckTargetPresetC = null
            pendingDeckSourceFileC = null
            pendingDeckUtilitySourceC = null
        } else if (isDeckA) {
            pendingDeckActionA = PendingDeckAction.NONE
            pendingDeckTargetPresetA = null
            pendingDeckSourceFileA = null
            pendingDeckUtilitySourceA = null
        } else {
            pendingDeckActionB = PendingDeckAction.NONE
            pendingDeckTargetPresetB = null
            pendingDeckSourceFileB = null
            pendingDeckUtilitySourceB = null
        }
    }
}
