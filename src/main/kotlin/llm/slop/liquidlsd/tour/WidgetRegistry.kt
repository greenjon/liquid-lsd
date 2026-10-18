package llm.slop.liquidlsd.tour

import imgui.ImGui

/**
 * Records on-screen rectangles of named widgets so a tour script can aim the synthetic cursor at them.
 * Disabled (and free) outside tour mode: call sites must guard with [enabled] so no key strings are built live.
 */
object WidgetRegistry {
    @Volatile var enabled = false

    class Rect(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        val centerX get() = (minX + maxX) * 0.5f
        val centerY get() = (minY + maxY) * 0.5f
    }

    private val rects = HashMap<String, Rect>()
    private val seenFrame = HashMap<String, Int>()

    /** Advanced by the tour once per frame; a rect not re-noted in the previous frame is stale (its widget is no longer drawn). */
    var frame = 0

    /** Stores the rectangle of the most recently submitted ImGui item under [key]. */
    fun noteLastItem(key: String) {
        if (!enabled) return
        rects[key] = Rect(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY())
        seenFrame[key] = frame
    }

    /** [noteLastItem] with a lazily built key: nothing is allocated outside tour mode. */
    inline fun note(key: () -> String) {
        if (enabled) noteLastItem(key())
    }

    fun find(key: String): Rect? = rects[key]

    /** True when [key] was drawn in the frame before [frame] (steps start before this frame's UI is drawn). */
    fun isCurrent(key: String): Boolean = (seenFrame[key] ?: Int.MIN_VALUE) >= frame - 1

    fun keys(): Set<String> = rects.keys
}
