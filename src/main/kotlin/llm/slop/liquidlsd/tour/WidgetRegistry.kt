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

    /** Stores the rectangle of the most recently submitted ImGui item under [key]. */
    fun noteLastItem(key: String) {
        if (!enabled) return
        rects[key] = Rect(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(), ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY())
    }

    /** [noteLastItem] with a lazily built key: nothing is allocated outside tour mode. */
    inline fun note(key: () -> String) {
        if (enabled) noteLastItem(key())
    }

    fun find(key: String): Rect? = rects[key]

    fun keys(): Set<String> = rects.keys
}
