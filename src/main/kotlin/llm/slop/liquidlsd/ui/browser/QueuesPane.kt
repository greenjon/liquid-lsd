package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.PerformanceColors
import llm.slop.liquidlsd.ui.TangoPalette

/**
 * Every play queue on screen at once, one column each: the Queues tab, and the lower pane of the Library view when the queues are
 * under the browser ([QueueSplit]). Items get in from the browser (Q / Shift+Q, the toolbar buttons, a row's menu, or a drag to the
 * place in the queue they should go); here they are watched, reordered and played. While a drag holds nothing a column takes, the
 * column dims ([QueueDrop.blocked]); when items arrive its border flashes.
 */
object QueuesPane {
    private const val GAP = 6f
    private const val MIN_COLUMN_W = 150f
    /** How far every other column's fill moves toward white (black in the light theme). */
    private const val ALT_SHADE = 0.07f

    /** Opacity of a column the current drag can't drop into. */
    private const val BLOCKED_ALPHA = 0.35f

    private class Column(val id: String, val queue: QueueDrop.Queue, val draw: (SessionContext, Mixer) -> Unit)

    // A/B side first (presets, then FX), then BG (presets, then FX), then transitions.
    private val columns = listOf(
        Column("QueuesAb", QueueDrop.Queue.AB, QueueActionsPanel::draw),
        Column("QueuesFxAb", QueueDrop.Queue.FX_AB, FXQueueActionsPanel::draw),
        Column("QueuesBg", QueueDrop.Queue.BG, BgQueueActionsPanel::draw),
        Column("QueuesFxBg", QueueDrop.Queue.FX_BG, FXBgQueueActionsPanel::draw),
        Column("QueuesTrans", QueueDrop.Queue.TRANS, TransitionQueuePanel::draw)
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
        val knobQueues = if (QueueKnobs.live() && llm.slop.liquidlsd.midi.MidiMappingManager.controllers.anyConnected) {
            QueueKnobs.queuesFor(llm.slop.liquidlsd.ui.LibraryPanel.viewMode)
        } else null
        for ((i, column) in columns.withIndex()) {
            if (i > 0) ImGui.sameLine(0f, GAP)
            // Alternate the column fill so five similar button strips read as five separate queues.
            ImGui.pushStyleColor(ImGuiCol.ChildBg, if (i % 2 == 0) bg else bgAlt)
            val blocked = QueueDrop.blocked(column.queue)
            if (blocked) ImGui.pushStyleVar(ImGuiStyleVar.Alpha, BLOCKED_ALPHA)
            ImGui.beginChild(column.id, colW, h, true, flags)
            column.draw(session, mixer)
            ImGui.endChild()
            if (blocked) ImGui.popStyleVar()
            ImGui.popStyleColor()
            // The two columns the controller's queue knobs drive (14, 15), outlined in their LED colours so the screen and the hardware agree.
            val knobDriven = knobQueues?.let { column.queue == it.first || column.queue == it.second } == true
            if (knobDriven) {
                val col = if (column.queue.kind == BrowseKind.TRANS) TangoPalette.u32(PerformanceColors.COLOR_TRANS)
                    else if (column.queue == QueueDrop.Queue.BG || column.queue == QueueDrop.Queue.FX_BG) TangoPalette.QUEUE_BG_TEXT.u32()
                    else TangoPalette.QUEUE_AB_TEXT.u32()
                ImGui.getWindowDrawList().addRect(
                    ImGui.getItemRectMinX() + 1f, ImGui.getItemRectMinY() + 1f, ImGui.getItemRectMaxX() - 1f, ImGui.getItemRectMaxY() - 1f,
                    col, 6f, 0, 1f
                )
            }
            val flash = QueueDrop.flashAmount(column.queue)
            if (flash > 0f) {
                ImGui.getWindowDrawList().addRect(
                    ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY(),
                    TangoPalette.u32(TangoPalette.SYNC.normal, flash), 6f, 0, 2f
                )
            }
        }
        ImGui.popStyleColor()
        ImGui.popStyleVar(2)
    }
}
