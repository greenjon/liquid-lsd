package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.TangoPalette
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.UIManager
import java.io.File

/**
 * Shared deck-button styling helpers used by [PresetListPanel] and [PlaylistEditorPanel].
 *
 * Each deck has a canonical RGBA accent colour, sourced from [TangoPalette] so no two decks (or
 * deck vs. status role) ever share a hue. Call [push] before the button and [pop] after.
 *
 * Both helpers manage 2 style-vars (FrameBorderSize, FrameRounding) and 5 style-colours
 * (Text, Border, Button, ButtonHovered, ButtonActive).
 */
internal object BrowserDeckButtons {

    // ── Deck accent colours (Tango Desktop Project hues; see TangoPalette) ──
    private val DECK_A   = TangoPalette.DECK_A.normal  // #F57900 orange
    private val DECK_B   = TangoPalette.DECK_B.normal  // #3465A4 sky blue
    private val DECK_BG  = TangoPalette.DECK_BG.normal // #73D216 chameleon green (matches Mixxx's own Deck 3)
    private val DECK_PV  = TangoPalette.DECK_PV.normal // #75507B plum
    // Q / BGQ are toolbar queue actions, not deck identities (Q feeds either A or B; BGQ feeds BG
    // specifically but sits beside Q and must read as the same "queue" family) -- neutral
    // Aluminium shades keep them visually distinct from every deck/status accent above.
    private val DECK_Q   = TangoPalette.NEUTRAL_LIGHT.normal // #D3D7CF
    private val DECK_BGQ = TangoPalette.NEUTRAL_LIGHT.dark   // #BABDB6
    private val DECK_Q_LIGHT   = TangoPalette.NEUTRAL_DARK.dark   // #2E3436
    private val DECK_BGQ_LIGHT = TangoPalette.NEUTRAL_DARK.normal // #555753

    private val LOCK_COLOR = TangoPalette.SYNC.normal // #06AFDF sync/link cyan

    fun colorA() = DECK_A
    fun colorB() = DECK_B
    fun colorBG() = DECK_BG
    fun colorPV() = DECK_PV
    fun colorQ() = if (UITheme.theme == UITheme.Theme.ORANGE_SUNSHINE) DECK_Q_LIGHT else DECK_Q
    fun colorBGQ() = if (UITheme.theme == UITheme.Theme.ORANGE_SUNSHINE) DECK_BGQ_LIGHT else DECK_BGQ
    fun colorLock() = LOCK_COLOR

    /**
     * Push style vars + colours for a deck action button.
     * @param rgb a 3-element float array [r, g, b] for the accent colour.
     * @param alpha alpha applied to Text and Border (use <1.0 for dimmed/missing items).
     * @param isLatched whether this button is currently in a sticky/latched active state.
     */
    fun push(rgb: FloatArray, alpha: Float = 1f, isLatched: Boolean = false) {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 3f)
        if (isLatched) {
            ImGui.pushStyleColor(ImGuiCol.Text,         1f, 1f, 1f, 1f)
            ImGui.pushStyleColor(ImGuiCol.Border,       rgb[0], rgb[1], rgb[2], 1f)
            ImGui.pushStyleColor(ImGuiCol.Button,       rgb[0], rgb[1], rgb[2], 0.35f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered,rgb[0], rgb[1], rgb[2], 0.55f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, rgb[0], rgb[1], rgb[2], 0.70f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text,         rgb[0], rgb[1], rgb[2], alpha)
            ImGui.pushStyleColor(ImGuiCol.Border,       rgb[0], rgb[1], rgb[2], alpha)
            ImGui.pushStyleColor(ImGuiCol.Button,       0f, 0f, 0f, 0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered,rgb[0], rgb[1], rgb[2], 0.15f * alpha)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, rgb[0], rgb[1], rgb[2], 0.3f * alpha)
        }
    }

    /** Pop the 5 colours and 2 style vars pushed by [push]. */
    fun pop() {
        ImGui.popStyleColor(5)
        ImGui.popStyleVar(2)
    }

    /**
     * Load a preset file into Deck A (1), B (2), BG (3), or PV (4) using the unified transition guard.
     */
    fun loadPresetToDeck(session: SessionContext, mixer: Mixer, file: File, deckIndex: Int) {
        val targetDeck = when (deckIndex) {
            1 -> mixer.deckA
            2 -> mixer.deckB
            3 -> mixer.deckBG
            4 -> mixer.deckPV
            else -> return
        }
        UIManager.loadDeckPresetSafely(mixer, targetDeck, file)
    }
}
