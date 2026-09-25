package llm.slop.liquidlsd.ui

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

    /** Uniform height of every row-side control (buttons, badges, preset combo) left/right of the knobs. */
    const val CTRL_H = 24f
}

internal class PerformanceUiContext {
    /** LIVE_CONSOLE: which target ("A", "B", "BG", "PV", "MST") Row 3 is currently focused on. */
    var focusedFxTarget: String = "A"

    /** Per-deck row mode: "SRC" (visual source generator macros) or "FX" (deck FX chain macros). */
    val deckRowMode = mutableMapOf<String, String>()

    /** Set each frame by [PerformanceMatrixPanel.draw]; null in tests, where source swaps fall back to the unguarded path. */
    var deckPresetController: DeckPresetController? = null

    fun targetBankIdFor(target: String): String = when (target) {
        "A" -> MacroEngine.DECK_A_FX
        "B" -> MacroEngine.DECK_B_FX
        "BG" -> MacroEngine.DECK_BG_FX
        "PV" -> MacroEngine.DECK_PV_FX
        else -> MacroEngine.MASTER_FX
    }

    fun targetAccentFor(target: String): FloatArray = when (target) {
        "A" -> PerformanceColors.COLOR_DECK_A
        "B" -> PerformanceColors.COLOR_DECK_B
        "BG" -> PerformanceColors.COLOR_DECK_BG
        "PV" -> PerformanceColors.COLOR_DECK_PV
        else -> PerformanceColors.COLOR_MASTER
    }

    fun targetDisplayName(target: String): String = when (target) {
        "A" -> "Deck A"
        "B" -> "Deck B"
        "BG" -> "Deck BG"
        "PV" -> "Deck PV"
        else -> "Master"
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
        }
    }

    fun deckLabelForModuleId(moduleId: String): String? = when (moduleId) {
        MacroEngine.DECK_A, MacroEngine.DECK_A_FX -> "Deck A"
        MacroEngine.DECK_B, MacroEngine.DECK_B_FX -> "Deck B"
        MacroEngine.DECK_BG, MacroEngine.DECK_BG_FX -> "Deck BG"
        MacroEngine.DECK_PV, MacroEngine.DECK_PV_FX -> "Deck PV"
        "FX" -> when (focusedFxTarget) {
            "A" -> "Deck A"
            "B" -> "Deck B"
            "BG" -> "Deck BG"
            "PV" -> "Deck PV"
            else -> null
        }
        else -> null
    }

    fun deckForLabel(mixer: Mixer, deckLabel: String): Deck = when (deckLabel) {
        "Deck A" -> mixer.deckA
        "Deck B" -> mixer.deckB
        "Deck BG" -> mixer.deckBG
        else -> mixer.deckPV
    }
}
