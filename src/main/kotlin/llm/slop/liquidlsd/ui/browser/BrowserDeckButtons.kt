package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar

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
    private val DECK_A = floatArrayOf(0.2f, 0.4f, 0.8f) // blue
    private val DECK_B = floatArrayOf(0.8f, 0.4f, 0.2f) // orange
    private val DECK_C = floatArrayOf(0.2f, 0.7f, 0.5f) // green
    private val DECK_Q = floatArrayOf(0.7f, 0.4f, 0.9f) // violet

    fun colorA() = DECK_A
    fun colorB() = DECK_B
    fun colorC() = DECK_C
    fun colorQ() = DECK_Q

    /**
     * Push style vars + colours for a deck action button.
     * @param rgb a 3-element float array [r, g, b] for the accent colour.
     * @param alpha alpha applied to Text and Border (use <1.0 for dimmed/missing items).
     */
    fun push(rgb: FloatArray, alpha: Float = 1f) {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0f)
        ImGui.pushStyleColor(ImGuiCol.Text,         rgb[0], rgb[1], rgb[2], alpha)
        ImGui.pushStyleColor(ImGuiCol.Border,        rgb[0], rgb[1], rgb[2], alpha)
        ImGui.pushStyleColor(ImGuiCol.Button,        0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, rgb[0], rgb[1], rgb[2], 0.15f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,  rgb[0], rgb[1], rgb[2], 0.3f)
    }

    /** Pop the 5 colours and 2 style vars pushed by [push]. */
    fun pop() {
        ImGui.popStyleColor(5)
        ImGui.popStyleVar(2)
    }
}
