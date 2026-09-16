package llm.slop.liquidlsd.rack.ui

import imgui.ImDrawList
import imgui.ImGui
import kotlin.math.hypot

/**
 * High-performance cubic Bézier catenary curve renderer for virtual patch cables (Milestone 6, Phase 8).
 *
 * Implements physics-style gravitational sag and rubber sheath cable rendering:
 * - Distance-dependent sag: longer cables droop further downward.
 * - Multi-layer rendering: soft drop shadow underneath, thick colored rubber sheath,
 *   illuminated highlight along the cable center, and metallic strain relief collars at the jacks.
 */
object RackCableRenderer {

    const val CABLE_THICKNESS = 4.0f
    const val SHADOW_OFFSET_Y = 5.0f

    /**
     * Draws a virtual patch cable between two screen coordinates [p1] and [p2].
     *
     * @param dl ImGui draw list.
     * @param x1 Start X (screen px).
     * @param y1 Start Y (screen px).
     * @param x2 End X (screen px).
     * @param y2 End Y (screen px).
     * @param colorHex 32-bit ARGB/RGBA color of the rubber sheath.
     * @param isInteractiveDragging True if actively dragging one end with the mouse.
     */
    fun drawCable(
        dl: ImDrawList,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        colorHex: Long,
        isInteractiveDragging: Boolean = false
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx, dy)

        // Gravitational sag: base sag plus proportional stretch
        val baseSag = if (isInteractiveDragging) 28.0f else 38.0f
        val sag = baseSag + (dist * 0.22f)

        // Control points for cubic Bézier catenary curve
        val cx1 = x1 + (dx * 0.20f)
        val cy1 = y1 + sag
        val cx2 = x2 - (dx * 0.20f)
        val cy2 = y2 + sag

        // 1. Soft Drop Shadow
        val shadowCol = ImGui.colorConvertFloat4ToU32(0.0f, 0.0f, 0.0f, 0.40f)
        dl.addBezierCubic(
            x1, y1 + SHADOW_OFFSET_Y,
            cx1, cy1 + SHADOW_OFFSET_Y,
            cx2, cy2 + SHADOW_OFFSET_Y,
            x2, y2 + SHADOW_OFFSET_Y,
            shadowCol,
            CABLE_THICKNESS + 2.0f,
            36
        )

        // 2. Thick Outer Rubber Sheath
        val baseR = ((colorHex shr 16) and 0xFF).toFloat() / 255.0f
        val baseG = ((colorHex shr 8) and 0xFF).toFloat() / 255.0f
        val baseB = (colorHex and 0xFF).toFloat() / 255.0f
        val sheathCol = ImGui.colorConvertFloat4ToU32(baseR * 0.85f, baseG * 0.85f, baseB * 0.85f, 1.0f)

        dl.addBezierCubic(x1, y1, cx1, cy1, cx2, cy2, x2, y2, sheathCol, CABLE_THICKNESS, 36)

        // 3. Center Highlight Ridge
        val hiCol = ImGui.colorConvertFloat4ToU32(
            minOf(baseR + 0.35f, 1.0f),
            minOf(baseG + 0.35f, 1.0f),
            minOf(baseB + 0.35f, 1.0f),
            0.85f
        )
        dl.addBezierCubic(x1, y1 - 0.5f, cx1, cy1 - 0.5f, cx2, cy2 - 0.5f, x2, y2 - 0.5f, hiCol, 1.5f, 36)

        // 4. Jack Connectors / Plugs at Endpoints
        drawPlug(dl, x1, y1, sheathCol)
        drawPlug(dl, x2, y2, sheathCol)
    }

    private fun drawPlug(dl: ImDrawList, x: Float, y: Float, plugCol: Int) {
        // Metallic barrel / strain relief sleeve
        val barrelCol = ImGui.colorConvertFloat4ToU32(0.20f, 0.22f, 0.25f, 1.0f)
        val metalHiCol = ImGui.colorConvertFloat4ToU32(0.70f, 0.74f, 0.80f, 1.0f)
        dl.addCircleFilled(x, y, 6.0f, barrelCol, 16)
        dl.addCircle(x, y, 6.0f, metalHiCol, 16, 1.2f)
        dl.addCircleFilled(x, y, 3.5f, plugCol, 12)
    }
}
