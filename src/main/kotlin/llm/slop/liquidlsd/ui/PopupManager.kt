package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.presets.PresetManager

class PopupManager(
    private val onTriggerExit: () -> Unit,
    private val onSaveDeck: (String, Deck, Boolean) -> Unit
) {
    companion object {
        var globalPendingMidiWarning = false
    }

    var pendingOpenExitPopup = false
    var pendingOpenMidiWarningPopup = false

    private var pendingConfirmDeck: Deck? = null
    private var pendingConfirmLabel: String? = null
    private var pendingConfirmCallback: (() -> Unit)? = null
    private var pendingConfirmCancel: (() -> Unit)? = null


    private val dontAskAgain = imgui.type.ImBoolean(false)

    fun requestDeckConfirm(deck: Deck, label: String, onProceed: () -> Unit, onCancel: () -> Unit = {}) {
        // A second request replaces the first, which is therefore cancelled.
        pendingConfirmCancel?.invoke()
        dontAskAgain.set(false)
        pendingConfirmCancel = onCancel
        pendingConfirmDeck = deck
        pendingConfirmLabel = label
        pendingConfirmCallback = onProceed
    }

    fun clearDeckConfirm() {
        pendingConfirmDeck = null
        pendingConfirmLabel = null
        pendingConfirmCallback = null
        pendingConfirmCancel = null
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
                val activeName = DeckSlot.of(deck, mixer)?.let { session.presetManager.activePreset(it) }
                // A deck with no preset name (e.g. a fresh generator) gets a name no existing preset has, never overwriting an older one.
                val saveName = activeName ?: DeckPresetController.freePresetName("Untitled_${label.replace(" ", "")}", FileSystemManager.getPresetsRoot())
                onSaveDeck(saveName, deck, deck === mixer.deckA)
                onProceed()
                clearDeckConfirm()
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                if (dontAskAgain.get()) {
                    session.uiTheme.manualLoadDirtyBehavior = UITheme.ManualLoadDirtyBehavior.DISCARD
                    AppPreferencesStore.savePreferences()
                }
                onProceed()
                clearDeckConfirm()
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                val onCancel = pendingConfirmCancel
                clearDeckConfirm()
                onCancel?.invoke()
                ImGui.closeCurrentPopup()
            }
            ImGui.spacing()
            ImGui.checkbox("Don't ask again (always discard; Ctrl+Z undoes preset/generator loads; change in Preferences)", dontAskAgain)
            ImGui.endPopup()
        }
    }

    var pendingOpenRestoreDefaultsPopup = false
    var lastRestoreMessage: String? = null

    fun drawRestoreDefaultsPopup() {
        if (pendingOpenRestoreDefaultsPopup) {
            ImGui.openPopup("Restore Factory Presets?##restore_defaults")
            pendingOpenRestoreDefaultsPopup = false
        }

        if (ImGui.beginPopupModal("Restore Factory Presets?##restore_defaults", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textWrapped("This will restore any missing factory presets and playlists from the application bundle into your library.")
            ImGui.spacing()
            ImGui.textDisabled("Note: Your custom presets and modifications will not be overwritten.")
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            if (ImGui.button("Restore Defaults", 130f, 0f)) {
                val res = FileSystemManager.restoreFactoryPresets()
                lastRestoreMessage = "Restored ${res.presetsExtracted} preset(s) and ${res.playlistsExtracted} playlist(s)."
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }
}
