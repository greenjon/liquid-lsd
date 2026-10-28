package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseCursor
import llm.slop.liquidlsd.ui.AppPreferencesStore
import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.UITheme

/**
 * The Library view (FULL) split in two: the browser on top, the five queues ([QueuesPane]) under it, a draggable divider between.
 * A row dragged from the browser lands at the place in the queue it is dropped on, so a source, effect or chain can be auditioned
 * and queued in one move. On while [UITheme.libraryQueueSplit] is set, on the Sources, FX and Transitions tabs only; the Perform
 * view's Library is too short to split, and the Queues tab is the same queues full height.
 */
object QueueSplit {
    /** Height of the divider's grab area. */
    const val DIVIDER_H = 8f
    /** Neither pane gets shorter: the queues' caption and button strip plus about three rows, likewise the browser's search line. */
    const val MIN_PANE_H = 140f
    const val DEFAULT_RATIO = 0.5f

    fun shows(libraryFull: Boolean, splitOn: Boolean, tab: LibraryViewMode): Boolean =
        libraryFull && splitOn && tab in setOf(LibraryViewMode.PRESETS, LibraryViewMode.FX, LibraryViewMode.TRANS)

    /** Whether the Library window draws the queues under the browser right now. */
    fun shows(tab: LibraryViewMode): Boolean =
        shows(UITheme.libraryMode == UITheme.LibraryMode.FULL, UITheme.libraryQueueSplit, tab)

    class Heights(val top: Float, val bottom: Float)

    /**
     * The two panes' heights out of [available] (the space left once the divider and its gaps are taken), the browser getting [ratio]
     * of it, each clamped to [MIN_PANE_H]. Null when there isn't room for both minimums: the browser then takes it all.
     */
    fun heights(available: Float, ratio: Float): Heights? {
        if (available < 2f * MIN_PANE_H) return null
        val top = (available * ratio).coerceIn(MIN_PANE_H, available - MIN_PANE_H)
        return Heights(top, available - top)
    }

    private var dragRatio: Float? = null

    /** The browser's share while the divider is being dragged, else the saved one. */
    fun ratio(): Float = dragRatio ?: UITheme.libraryQueueSplitRatio

    /**
     * The divider between the panes, full [width]. Dragging moves it ([available] is the height the two panes share, [paneTop] the
     * screen Y of the browser's top); the new ratio is saved on release. A double-click puts it back in the middle.
     */
    fun drawDivider(width: Float, available: Float, paneTop: Float) {
        ImGui.invisibleButton("##queue_split_divider", width, DIVIDER_H)
        val hovered = ImGui.isItemHovered()
        val active = ImGui.isItemActive()
        if (hovered || active) ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS)
        if (active && available > 0f) {
            dragRatio = ((ImGui.getMousePosY() - paneTop - DIVIDER_H * 0.5f) / available).coerceIn(0.1f, 0.9f)
        }
        if (ImGui.isItemDeactivated()) {
            dragRatio?.let { UITheme.libraryQueueSplitRatio = it }
            dragRatio = null
            AppPreferencesStore.savePreferences()
        }
        if (hovered && ImGui.isMouseDoubleClicked(0)) {
            dragRatio = null
            UITheme.libraryQueueSplitRatio = DEFAULT_RATIO
            AppPreferencesStore.savePreferences()
        }
        llm.slop.liquidlsd.ui.itemTooltip("Drag to share the height between the browser and the queues. Double-click: half and half.")
        val y = (ImGui.getItemRectMinY() + ImGui.getItemRectMaxY()) * 0.5f
        val x0 = ImGui.getItemRectMinX() + 24f
        val x1 = ImGui.getItemRectMaxX() - 24f
        val col = ImGui.getColorU32(if (active) ImGuiCol.SeparatorActive else if (hovered) ImGuiCol.SeparatorHovered else ImGuiCol.Separator)
        ImGui.getWindowDrawList().addLine(x0, y, x1, y, col, if (hovered || active) 2f else 1f)
    }
}
