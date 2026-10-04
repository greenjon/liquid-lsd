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
    // Canonical deck colors matching BrowserDeckButtons, sourced from TangoPalette so no two
    // decks (or a deck vs. a status role) ever share a hue.
    val COLOR_DECK_A   = TangoPalette.DECK_A.normal  // #F57900 orange
    val COLOR_DECK_B   = TangoPalette.DECK_B.normal  // #3465A4 sky blue
    val COLOR_DECK_BG  = TangoPalette.DECK_BG.normal // #73D216 chameleon green (shared with ACTIVE -- see TangoPalette)
    val COLOR_DECK_PV  = TangoPalette.DECK_PV.normal // #75507B plum
    // These 4 badge the MASTER tab's rows, shown stacked together, so they need to read apart from
    // each other as much as from the DECKS tab's deck hues -- neither Master nor Global is deck-
    // specific, so neutral Aluminium tones; Transitions keeps the Sync cyan (crossfading decks is
    // a "sync" concept); FX Wet/Dry uses Chocolate (freed up now that Deck BG is Chameleon) so it
    // doesn't read as just a lighter/darker copy of the Transitions row.
    val COLOR_TRANS    = TangoPalette.SYNC.normal          // #06AFDF
    val COLOR_MASTER   = TangoPalette.NEUTRAL_LIGHT.normal // #D3D7CF
    val COLOR_FX       = TangoPalette.CHOCOLATE.normal     // #C17D11
    val COLOR_GLOBAL   = TangoPalette.NEUTRAL_DARK.normal  // #555753

    // Controller LED colours. An RGB LED can't show the Aluminium greys that badge Master and Global
    // on screen, so those two rows get a hue of their own on the hardware (the screen keeps its greys).
    val LED_MASTER     = TangoPalette.SCARLET_RED.normal
    val LED_GLOBAL     = TangoPalette.PLUM.normal

    /** Uniform height of every row-side control (buttons, badges, preset combo) left/right of the knobs. */
    const val CTRL_H = 24f

    val TOGGLE_ACTIVE_BG = TangoPalette.u32(TangoPalette.ACTIVE.dark)
    val TOGGLE_ACTIVE_HOVER = TangoPalette.u32(TangoPalette.ACTIVE.normal)
    val TOGGLE_ACTIVE_PRESSED = TangoPalette.u32(TangoPalette.ACTIVE.dark, 0.85f)
    val TOGGLE_ACTIVE_TEXT = TangoPalette.u32(TangoPalette.INK_DARK)

    val TOGGLE_INACTIVE_BG = TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark, 0.85f)
    val TOGGLE_INACTIVE_HOVER = TangoPalette.u32(TangoPalette.NEUTRAL_DARK.normal)
    val TOGGLE_INACTIVE_PRESSED = TangoPalette.u32(TangoPalette.NEUTRAL_DARK.dark)
    val TOGGLE_INACTIVE_TEXT = TangoPalette.u32(TangoPalette.NEUTRAL_LIGHT.light)

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
            TangoPalette.PILL_BORDER_HOVER.u32()
        } else {
            TangoPalette.PILL_BORDER.u32()
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
    /**
     * True when deck [tag]'s knobs drive its FX chain. The deck's Deep Edit sub-tab ([ParametersState.setDeckSubTab])
     * is the one stored SRC/FX state: the row's [SRC|FX] pill, the bay's SRC/FX tabs and Browse all write it,
     * so the row and the bay can't disagree.
     */
    fun isDeckRowFx(tag: String, parametersState: ParametersState?): Boolean =
        parametersState?.getActiveDeckSubTabByTag(tag) == "FX"

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
     * Focuses the Deep Edit tab (and Mixer/deck sub-tab) that belongs to [bankId]. GLOBAL and FX_SENDS
     * have no tab, so they are left as a no-op.
     */
    fun focusDeepEditTab(parametersState: ParametersState, bankId: String) {
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
            // GLOBAL has no Deep Edit tab; its knobs are edited in the guest strip.
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
