package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer

/**
 * The 2x2 deck monitor grid (A | B over BG | PV), shared by the Mixer view and the Macros tab so their
 * geometry can't drift. [drawTile] draws one tile: (label, deck, width, height, isDeckA).
 */
object DeckMonitorGrid {
    const val PADDING = 8f

    /** Total height the grid occupies for the given tile height, including the row gap. */
    fun totalHeight(tileH: Float): Float = tileH * 2f + ImGui.getStyle().getItemSpacingY() + 6f + 4f

    fun draw(
        mixer: Mixer,
        startX: Float,
        startY: Float,
        availW: Float,
        tileH: Float,
        drawTile: (String, Deck, Float, Float, Boolean) -> Unit
    ) {
        val halfW = ((availW - PADDING) * 0.5f).coerceAtLeast(1f)
        val rightX = startX + halfW + PADDING
        val row2Y = startY + tileH + ImGui.getStyle().getItemSpacingY() + 6f

        ImGui.setCursorScreenPos(startX, startY)
        drawTile("Deck A", mixer.deckA, halfW, tileH, true)
        ImGui.setCursorScreenPos(rightX, startY)
        drawTile("Deck B", mixer.deckB, halfW, tileH, false)
        ImGui.setCursorScreenPos(startX, row2Y)
        drawTile("Deck BG", mixer.deckBG, halfW, tileH, false)
        ImGui.setCursorScreenPos(rightX, row2Y)
        drawTile("Deck PV", mixer.deckPV, halfW, tileH, false)

        ImGui.setCursorScreenPos(startX, row2Y + tileH + 4f)
        ImGui.dummy(0f, 0f)
    }
}
