package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSource
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import java.io.File

/**
 * Deck visual-source selection shared by the Parameters title bar ([ParametersTabs.drawSourceTab]),
 * the Performance deck row's generator badge, and the empty-deck launchpad (Parameters panel and
 * Deep Edit) -- one code path so every entry point swaps sources identically.
 */
object DeckSourcePicker {

    /** Opens the shared shader picker (including external video servers) for [deck]. */
    fun open(
        session: llm.slop.liquidlsd.SessionContext,
        state: ParametersState,
        mixer: Mixer,
        deck: Deck,
        deckLabel: String,
        deckPresetController: DeckPresetController?
    ) {
        ShaderPickerPopup.show("Select Source for $deckLabel", ShaderPickerPopup.PickerType.SOURCE) { newSourceId ->
            if (newSourceId == null) return@show
            val newSource = if (newSourceId.startsWith("ext_video:")) {
                val serverName = newSourceId.removePrefix("ext_video:")
                llm.slop.liquidlsd.rendering.ExternalVideoSource(serverName = serverName)
            } else {
                VisualSourceRegistry.availableSources.find { it.id == newSourceId }
            }
            if (newSource != null) {
                changeSource(session, state, mixer, deck, deckLabel, newSource, deckPresetController)
            }
        }
    }

    fun changeSource(
        session: llm.slop.liquidlsd.SessionContext,
        state: ParametersState,
        mixer: Mixer,
        deck: Deck,
        deckLabel: String,
        newSource: VisualSource,
        deckPresetController: DeckPresetController?
    ) {
        if (deckPresetController != null) {
            deckPresetController.changeVisualSourceSafely(mixer, deck, deckLabel, newSource, state)
        } else {
            deck.source = newSource.clone()
            deck.isEmpty = false
            session.deckLifecycleManager.clearDeckActivePreset(deck, mixer)
            state.clearSelection()
            state.setDeckSubTab(deckLabel, "SRC")
            ParametersUndo.pushUndoState(state, mixer)
        }
    }

    /** Empty-deck card: Add Source / Load Preset / Open Library. */
    fun drawLaunchpad(
        session: llm.slop.liquidlsd.SessionContext,
        deckLabel: String,
        deck: Deck,
        state: ParametersState,
        mixer: Mixer,
        deckPresetController: DeckPresetController? = null
    ) {
        val isDeckA = deckLabel == "Deck A"
        val isDeckBG = deckLabel == "Deck BG"
        val isDeckPV = deckLabel == "Deck PV"
        val deckColorU32 = ParametersTabs.getDeckColor(deckLabel, 1f)

        val availW = ImGui.getContentRegionAvailX()
        val cardW = (availW - 16f).coerceIn(160f, 342f).coerceAtMost(availW)
        val paddingX = ((availW - cardW) * 0.5f).coerceAtLeast(0f)

        ImGui.dummy(0f, 11.4f)
        ImGui.indent(paddingX)

        val cardH = 209f
        if (ImGui.beginChild("##launchpad_$deckLabel", cardW, cardH, true)) {
            ImGui.spacing()
            ImGui.spacing()

            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, deckColorU32)
            session.uiTheme.withFont(UITheme.FontLevel.H2) {
                ImGui.textWrapped("$deckLabel is Empty")
            }
            ImGui.popStyleColor()

            ImGui.spacing()
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0.65f, 0.65f, 0.70f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                ImGui.textWrapped("No visual generator is currently assigned to this deck. Choose an action below to activate:")
            }
            ImGui.popStyleColor()

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            val buttonWidth = ImGui.getContentRegionAvailX()
            val buttonHeight = 30.4f

            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 5.7f)

            // --- Button 1: Add Visual Source ---
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.18f, 0.22f, 0.30f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.28f, 0.34f, 0.46f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.38f, 0.44f, 0.58f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.PLUS}  Add Source##launchpad_add_$deckLabel", buttonWidth, buttonHeight)) {
                    open(session, state, mixer, deck, deckLabel, deckPresetController)
                }
            }
            itemTooltip("Select a visual generator source (Mandala, Gyroid, Dynamic Spiral, external video, etc.)")
            ImGui.popStyleColor(3)

            ImGui.spacing()

            // --- Button 2: Load Preset ---
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.18f, 0.26f, 0.24f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.28f, 0.38f, 0.34f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.38f, 0.48f, 0.44f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.FOLDER}  Load Preset##launchpad_load_$deckLabel", buttonWidth, buttonHeight)) {
                    ImGui.openPopup("##launchpad_preset_popup_$deckLabel")
                }
            }
            itemTooltip("Choose a saved preset for $deckLabel")
            ImGui.popStyleColor(3)

            if (ImGui.beginPopup("##launchpad_preset_popup_$deckLabel")) {
                ImGui.textDisabled("Quick Select Preset:")
                ImGui.separator()

                val presetFiles = FileSystemManager.scanAllPresets()

                if (presetFiles.isEmpty()) {
                    ImGui.textDisabled("No presets found.")
                } else {
                    for (asset in presetFiles.sortedBy { it.name }) {
                        val label = asset.displayName.ifBlank { asset.name }
                        if (ImGui.menuItem("$label##launchpad_preset_${asset.path}")) {
                            session.presetRepository.loadDeckPresetAsync(File(asset.path), isDeckA = isDeckA, isDeckBG = isDeckBG, isDeckPV = isDeckPV)
                        }
                    }
                }
                ImGui.separator()
                if (ImGui.menuItem("Open Library Panel...##launchpad_open_lib")) {
                    session.uiTheme.libraryMode = UITheme.LibraryMode.HALF
                }
                ImGui.endPopup()
            }

            ImGui.popStyleVar()
        }
        ImGui.endChild()
        ImGui.unindent(paddingX)
    }
}
