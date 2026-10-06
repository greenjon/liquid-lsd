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

    private class Column(val id: String, val draw: (SessionContext, Mixer) -> Unit)

    // Presets first, then FX, then transitions; A/B before BG within a kind.
    private val columns = listOf(
        Column("QueuesAb", QueueActionsPanel::draw),
        Column("QueuesBg", BgQueueActionsPanel::draw),
        Column("QueuesFxAb", FXQueueActionsPanel::draw),
        Column("QueuesFxBg", FXBgQueueActionsPanel::draw),
        Column("QueuesTrans", TransitionQueuePanel::draw)
    )

    fun draw(session: SessionContext, mixer: Mixer) {
        val h = ImGui.getContentRegionAvailY().coerceAtLeast(1f)
        val totalW = ImGui.getContentRegionAvailX()
        val colW = ((totalW - GAP * (columns.size - 1)) / columns.size).coerceAtLeast(MIN_COLUMN_W)
        val flags = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse

        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 6f, 6f)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, TangoPalette.PANEL_BG.u32())
        ImGui.pushStyleColor(ImGuiCol.Border, TangoPalette.PANEL_BORDER.u32())
        for ((i, column) in columns.withIndex()) {
            if (i > 0) ImGui.sameLine(0f, GAP)
            ImGui.beginChild(column.id, colW, h, true, flags)
            column.draw(session, mixer)
            ImGui.endChild()
        }
        ImGui.popStyleColor(2)
        ImGui.popStyleVar(2)
    }
}
