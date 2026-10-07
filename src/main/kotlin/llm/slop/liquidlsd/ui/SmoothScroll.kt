package llm.slop.liquidlsd.ui

import imgui.ImGui
import kotlin.math.abs
import kotlin.math.exp

/**
 * Eases a list toward its selected row instead of jumping, so stepping through a browser with an encoder
 * glides rather than flickers. Call [follow] right before drawing the selected row, every frame it is drawn:
 * the one-shot [LibraryPanel.shouldScrollToSelection] only starts (or retargets) a glide, later frames continue it.
 */
object SmoothScroll {
    /** Frame each in-flight glide was last stepped on, keyed by the row's ImGui id. */
    private val gliding = HashMap<Int, Int>()

    private const val EASE_RATE = 16f
    private const val ARRIVED_PX = 0.5f
    /** A target farther than this many window heights away is a jump (list just opened), not a step. */
    private const val SNAP_WINDOWS = 1.5f

    fun follow(requested: Boolean) {
        val frame = ImGui.getFrameCount()
        val id = ImGui.getID("##smooth_scroll")
        if (gliding[id].let { it != null && it < frame - 1 }) gliding.remove(id)
        if (!requested && id !in gliding) return

        val windowH = ImGui.getWindowHeight()
        val rowH = ImGui.getTextLineHeightWithSpacing()
        val max = ImGui.getScrollMaxY().coerceAtLeast(0f)
        val target = (ImGui.getCursorPosY() + rowH * 0.5f - windowH * 0.5f).coerceIn(0f, max)
        val current = ImGui.getScrollY()
        val delta = target - current

        if (abs(delta) > windowH * SNAP_WINDOWS) {
            ImGui.setScrollY(target)
            gliding.remove(id)
        } else if (abs(delta) <= ARRIVED_PX) {
            if (delta != 0f) ImGui.setScrollY(target)
            gliding.remove(id)
        } else {
            val k = 1f - exp(-EASE_RATE * ImGui.getIO().deltaTime)
            ImGui.setScrollY(current + delta * k)
            gliding[id] = frame
        }
    }
}
