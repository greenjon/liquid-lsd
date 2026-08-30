package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.UIManager
import java.io.File

/**
 * Shared deck-button styling helpers used by [PresetListPanel] and [PlaylistEditorPanel].
 *
 * Each deck has a canonical RGBA accent colour.  The Q (queue) button uses violet.
 * Call [push] before the button and [pop] after.
 *
 * Both helpers manage 2 style-vars (FrameBorderSize, FrameRounding) and 5 style-colours
 * (Text, Border, Button, ButtonHovered, ButtonActive).
 */
internal object BrowserDeckButtons {

    // ── Deck accent colours ────────────────────────────────────────────────
    private val DECK_A   = floatArrayOf(0.2f, 0.4f, 0.8f) // blue
    private val DECK_B   = floatArrayOf(0.8f, 0.4f, 0.2f) // orange
    private val DECK_BG  = floatArrayOf(0.85f, 0.65f, 0.2f) // amber / gold
    private val DECK_PV  = floatArrayOf(0.2f, 0.7f, 0.5f) // mint green
    private val DECK_C   = DECK_PV // alias
    private val DECK_Q   = floatArrayOf(0.7f, 0.4f, 0.9f) // violet
    private val DECK_BGQ = floatArrayOf(0.9f, 0.35f, 0.65f) // magenta / rose

    private val LOCK_COLOR = floatArrayOf(0.2f, 0.8f, 1.0f) // cyan / electric blue

    fun colorA() = DECK_A
    fun colorB() = DECK_B
    fun colorBG() = DECK_BG
    fun colorPV() = DECK_PV
    fun colorC() = DECK_C
    fun colorQ() = DECK_Q
    fun colorBGQ() = DECK_BGQ
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
