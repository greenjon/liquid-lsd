package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.presets.DeckChange
import llm.slop.liquidlsd.presets.DeckOps
import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSource
import llm.slop.liquidlsd.rendering.VisualSourceRegistry

/**
 * Deck visual-source selection shared by the Performance deck row's generator badge and the
 * empty-deck launchpad in Deep Edit -- one code path so both swap sources identically.
 */
object DeckSourcePicker {

    /**
     * Resolves a picked source id (a stock [VisualSourceRegistry] id, or an `ext_video:<server>`
     * external-video id) from the Browse Gen tab and applies it to [deck], or does nothing for a
     * null/unresolvable id (e.g. the "no streams active" placeholder, or Detach/None).
     */
    fun applyPickedSourceId(
        session: llm.slop.liquidlsd.SessionContext,
        state: ParametersState,
        mixer: Mixer,
        deck: Deck,
        deckLabel: String,
        sourceId: String?,
        deckPresetController: DeckPresetController?
    ) {
        if (sourceId == null) return
        val newSource = if (sourceId.startsWith("ext_video:")) {
            val serverName = sourceId.removePrefix("ext_video:")
            llm.slop.liquidlsd.rendering.ExternalVideoSource(serverName = serverName)
        } else {
            VisualSourceRegistry.availableSources.find { it.id == sourceId }
        }
        if (newSource != null) {
            changeSource(session, state, mixer, deck, deckLabel, newSource, deckPresetController)
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
        DeckSlot.of(deck, mixer)?.let { DeckOps.request(it, DeckChange.Source(newSource)) }
    }

    /** Empty-deck card: one button that opens Browse on this deck's sources and presets. */
    fun drawLaunchpad(
        session: llm.slop.liquidlsd.SessionContext,
        deckLabel: String,
        deck: Deck,
        state: ParametersState,
        mixer: Mixer,
        deckPresetController: DeckPresetController? = null
    ) {
        val deckColorU32 = ParametersTabs.getDeckColor(deckLabel, 1f)

        val availW = ImGui.getContentRegionAvailX()
        val cardW = (availW - 16f).coerceIn(160f, 342f).coerceAtMost(availW)
        val paddingX = ((availW - cardW) * 0.5f).coerceAtLeast(0f)

        ImGui.dummy(0f, 11.4f)
        ImGui.indent(paddingX)

        val cardH = 150f
        if (ImGui.beginChild("##launchpad_$deckLabel", cardW, cardH, true)) {
            ImGui.spacing()
            ImGui.spacing()

            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, deckColorU32)
            session.uiTheme.withFont(UITheme.FontLevel.H2) {
                ImGui.textWrapped("$deckLabel is Empty")
            }
            ImGui.popStyleColor()

            ImGui.spacing()
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled))
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                ImGui.textWrapped("No source is assigned to this deck. Pick a source or a saved preset in the list.")
            }
            ImGui.popStyleColor()

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 5.7f)
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ButtonChrome.button("${Icons.PLUS}  Add Source##launchpad_add_$deckLabel", ImGui.getContentRegionAvailX(), 30.4f)) {
                    val canonicalBankId = llm.slop.liquidlsd.macro.MacroEngine.deckBankIdFor(deck, mixer) ?: llm.slop.liquidlsd.macro.MacroEngine.DECK_A
                    state.openGenBrowse(canonicalBankId, deckLabel)
                }
            }
            itemTooltip("Opens the list below on $deckLabel's sources: stock sources, external video and saved presets.")
            ImGui.popStyleVar()
        }
        ImGui.endChild()
        ImGui.unindent(paddingX)
    }
}
