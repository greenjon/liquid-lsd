package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.TangoPalette

/**
 * The Queues tab: every play queue on screen at once, one column each. Items get in from the other tabs (Q / Shift+Q, the toolbar buttons, a row's
 * menu, or a drag onto the Perform rows); this tab is where they are watched, reordered and played.
 */
object QueuesPane {
    private const val GAP = 6f
    private const val MIN_COLUMN_W = 150f
    /** How far every other column's fill moves toward white (black in the light theme). */
    private const val ALT_SHADE = 0.07f

    private class Column(val id: String, val draw: (SessionContext, Mixer) -> Unit)

    // A/B side first (presets, then FX), then BG (presets, then FX), then transitions.
    private val columns = listOf(
        Column("QueuesAb", QueueActionsPanel::draw),
        Column("QueuesFxAb", FXQueueActionsPanel::draw),
        Column("QueuesBg", BgQueueActionsPanel::draw),
        Column("QueuesFxBg", FXBgQueueActionsPanel::draw),
        Column("QueuesTrans", TransitionQueuePanel::draw)
    )

    /** [col] (packed U32) mixed [amt] of the way toward white (dark theme) or black (light theme), alpha kept. */
    private fun contrasted(col: Int, amt: Float): Int {
        val target = if (TangoPalette.isLightTheme) 0f else 255f
        fun ch(shift: Int): Int {
            val v = (col shr shift) and 0xFF
            return (v + (target - v) * amt).toInt().coerceIn(0, 255)
        }
        return (col and (0xFF shl 24)) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun draw(session: SessionContext, mixer: Mixer) {
        val h = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
        val totalW = ImGui.getContentRegionAvailX()
        val colW = ((totalW - GAP * (columns.size - 1)) / columns.size).coerceAtLeast(MIN_COLUMN_W)
        val flags = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse

        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 6f, 6f)
        val bg = TangoPalette.PANEL_BG.u32()
        val bgAlt = contrasted(bg, ALT_SHADE)
        ImGui.pushStyleColor(ImGuiCol.Border, TangoPalette.PANEL_BORDER.u32())
        for ((i, column) in columns.withIndex()) {
            if (i > 0) ImGui.sameLine(0f, GAP)
            // Alternate the column fill so five similar button strips read as five separate queues.
            ImGui.pushStyleColor(ImGuiCol.ChildBg, if (i % 2 == 0) bg else bgAlt)
            ImGui.beginChild(column.id, colW, h, true, flags)
            column.draw(session, mixer)
            ImGui.endChild()
            ImGui.popStyleColor()
        }
        ImGui.popStyleColor()
        ImGui.popStyleVar(2)
    }
}
