package llm.slop.liquidlsd.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer

internal object PerformanceColors {
    val COLOR_DECK_A   = floatArrayOf(0.2f,  0.4f,  0.8f)
    val COLOR_DECK_B   = floatArrayOf(0.8f,  0.4f,  0.2f)
    val COLOR_DECK_BG  = floatArrayOf(0.85f, 0.65f, 0.2f)
    val COLOR_DECK_PV  = floatArrayOf(0.2f,  0.7f,  0.5f)
    val COLOR_TRANS    = floatArrayOf(0.7f,  0.4f,  0.9f)
    val COLOR_MASTER   = floatArrayOf(0.9f,  0.25f, 0.35f)
    val COLOR_FX       = floatArrayOf(0.15f, 0.75f, 0.65f)
    val COLOR_GLOBAL   = floatArrayOf(0.70f, 0.74f, 0.82f)

    /** Uniform height of every row-side control (buttons, badges, preset combo) left/right of the knobs. */
    const val CTRL_H = 24f

    val TOGGLE_ACTIVE_BG = ImGui.colorConvertFloat4ToU32(0.20f, 0.75f, 0.35f, 1f)
    val TOGGLE_ACTIVE_HOVER = ImGui.colorConvertFloat4ToU32(0.26f, 0.82f, 0.42f, 1f)
    val TOGGLE_ACTIVE_PRESSED = ImGui.colorConvertFloat4ToU32(0.16f, 0.68f, 0.30f, 1f)
    val TOGGLE_ACTIVE_TEXT = ImGui.colorConvertFloat4ToU32(0.05f, 0.05f, 0.05f, 1f)

    val TOGGLE_INACTIVE_BG = ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.85f)
    val TOGGLE_INACTIVE_HOVER = ImGui.colorConvertFloat4ToU32(0.20f, 0.23f, 0.28f, 1f)
    val TOGGLE_INACTIVE_PRESSED = ImGui.colorConvertFloat4ToU32(0.12f, 0.14f, 0.17f, 1f)
    val TOGGLE_INACTIVE_TEXT = ImGui.colorConvertFloat4ToU32(0.88f, 0.88f, 0.90f, 1f)

    fun pushActiveToggleStyle(active: Boolean) {
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, TOGGLE_ACTIVE_BG)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, TOGGLE_ACTIVE_HOVER)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, TOGGLE_ACTIVE_PRESSED)
            ImGui.pushStyleColor(ImGuiCol.Text, TOGGLE_ACTIVE_TEXT)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, TOGGLE_INACTIVE_BG)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, TOGGLE_INACTIVE_HOVER)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, TOGGLE_INACTIVE_PRESSED)
            ImGui.pushStyleColor(ImGuiCol.Text, TOGGLE_INACTIVE_TEXT)
        }
    }

    fun popActiveToggleStyle() {
        ImGui.popStyleColor(4)
    }

    fun drawTogglePill(
        dl: ImDrawList,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        text: String,
        isActive: Boolean,
        isHovered: Boolean,
        session: SessionContext
    ) {
        val bgCol = when {
            isActive && isHovered -> TOGGLE_ACTIVE_HOVER
            isActive -> TOGGLE_ACTIVE_BG
            isHovered -> TOGGLE_INACTIVE_HOVER
            else -> TOGGLE_INACTIVE_BG
        }
        val textCol = if (isActive) TOGGLE_ACTIVE_TEXT else TOGGLE_INACTIVE_TEXT
        val rounding = ImGui.getStyle().frameRounding

        dl.addRectFilled(x, y, x + w, y + h, bgCol, rounding)
        val borderCol = if (isHovered) {
            ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.4f)
        } else {
            ImGui.colorConvertFloat4ToU32(0.25f, 0.28f, 0.35f, 0.5f)
        }
        dl.addRect(x, y, x + w, y + h, borderCol, rounding, 0, 1f)

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val sz = ImGui.calcTextSize(text)
            val tx = x + (w - sz.x) * 0.5f
            val ty = y + (h - sz.y) * 0.5f
            dl.addText(tx, ty, textCol, text)
        }
    }
}

internal class PerformanceUiContext {
    /** Per-deck row mode: "SRC" (visual source generator macros) or "FX" (deck FX chain macros). */
    val deckRowMode = mutableMapOf<String, String>()

    /**
     * True when deck [tag]'s knobs drive its FX chain -- via the row's [FX] pill or Deep Edit's FX
     * sub-tab. The single SRC/FX predicate for deck rows; don't re-derive it from the two states.
     */
    fun isDeckRowFx(tag: String, parametersState: ParametersState?): Boolean =
        deckRowMode[tag] == "FX" || parametersState?.getActiveDeckSubTabByTag(tag) == "FX"

    /** Master row mode: "MIX" (composite alphas + master level) or "FX" (Master FX chain macros). */
    var masterRowMode: String = "MIX"

    /** True when the Master row's knobs drive Master FX -- via its [FX] pill or Deep Edit's Mixer FX section, mirroring the deck rows. */
    fun isMasterRowFx(parametersState: ParametersState): Boolean =
        masterRowMode == "FX" || parametersState.activeMixerSubTab == "FX"

    /** Set each frame by [PerformanceMatrixPanel.draw]; null in tests, where source swaps fall back to the unguarded path. */
    var deckPresetController: DeckPresetController? = null

    fun targetBankIdFor(target: String): String = when (target) {
        "A" -> MacroEngine.DECK_A_FX
        "B" -> MacroEngine.DECK_B_FX
        "BG" -> MacroEngine.DECK_BG_FX
        "PV" -> MacroEngine.DECK_PV_FX
        else -> MacroEngine.MASTER_FX
    }

    fun resolveFxChain(mixer: Mixer, bankId: String): FxChain =
        llm.slop.liquidlsd.macro.FxMacroSync.chainFor(bankId, mixer) ?: mixer.deckA.fxChain

    /** Maps sub-modules (DECK_A_FX, MASTER_FX, TRANS, etc.) to their canonical primary module id (DECK_A, MASTER, etc.). */
    fun canonicalModuleId(moduleId: String): String = when (moduleId) {
        MacroEngine.DECK_A, MacroEngine.DECK_A_FX -> MacroEngine.DECK_A
        MacroEngine.DECK_B, MacroEngine.DECK_B_FX -> MacroEngine.DECK_B
        MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX -> MacroEngine.DECK_BG
        MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX -> MacroEngine.DECK_PV
        MacroEngine.MASTER, MacroEngine.TRANS, MacroEngine.MASTER_FX, "Mixer" -> MacroEngine.MASTER
        else -> moduleId
    }

    /**
     * Points [ParametersState.activeTopTab] (and Mixer sub-tab) at whichever tab [MacroPanel]
     * reads to display [bankId], mirroring [MacroPanel.activeBankId]'s reverse mapping -- so
     * arming Learn on a Performance-panel knob and having [UITheme.Column3Mode.MACROS] pop open
     * lands on the *same* bank's knobs rather than whatever tab Deep Edit last had focused.
     * FX_SENDS has no dedicated Macros-tab destination, so it's left as a no-op (Macros still
     * opens, just without a matching tab switch).
     */
    fun navigateMacroPanelTo(parametersState: ParametersState, bankId: String) {
        if (bankId != MacroEngine.GLOBAL) parametersState.hideGlobalMacros()
        when (bankId) {
            MacroEngine.DECK_A -> parametersState.activeTopTab = "Deck A"
            MacroEngine.DECK_B -> parametersState.activeTopTab = "Deck B"
            MacroEngine.DECK_BG -> parametersState.activeTopTab = "Deck BG"
            MacroEngine.DECK_PV -> parametersState.activeTopTab = "Deck PV"
            MacroEngine.DECK_A_FX -> {
                parametersState.activeTopTab = "Deck A"
                parametersState.setDeckSubTab("Deck A", "FX")
            }
            MacroEngine.DECK_B_FX -> {
                parametersState.activeTopTab = "Deck B"
                parametersState.setDeckSubTab("Deck B", "FX")
            }
            MacroEngine.DECK_BG_FX -> {
                parametersState.activeTopTab = "Deck BG"
                parametersState.setDeckSubTab("Deck BG", "FX")
            }
            MacroEngine.DECK_PV_FX -> {
                parametersState.activeTopTab = "Deck PV"
                parametersState.setDeckSubTab("Deck PV", "FX")
            }
            MacroEngine.MASTER_FX -> {
                parametersState.activeTopTab = "Mixer"
                parametersState.activeMixerSubTab = "FX"
            }
            MacroEngine.MASTER -> {
                parametersState.activeTopTab = "Mixer"
                parametersState.activeMixerSubTab = "CTRL"
            }
            MacroEngine.TRANS -> {
                parametersState.activeTopTab = "Mixer"
                parametersState.activeMixerSubTab = "TRANS"
            }
            MacroEngine.GLOBAL -> parametersState.showGlobalMacros()
        }
    }

    fun deckLabelForModuleId(moduleId: String): String? = when (moduleId) {
        MacroEngine.DECK_A, MacroEngine.DECK_A_FX -> "Deck A"
        MacroEngine.DECK_B, MacroEngine.DECK_B_FX -> "Deck B"
        MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX -> "Deck BG"
        MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX -> "Deck PV"
        else -> null
    }

    fun deckForLabel(mixer: Mixer, deckLabel: String): Deck = when (deckLabel) {
        "Deck A" -> mixer.deckA
        "Deck B" -> mixer.deckB
        "Deck BG" -> mixer.deckBG
        else -> mixer.deckPV
    }
}
