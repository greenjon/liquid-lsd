package llm.slop.liquidlsd.rack.ui

import imgui.ImGui
import imgui.ImDrawList
import llm.slop.liquidlsd.ui.UITheme

/**
 * Renders the 19-inch equipment rack chassis styling:
 * - Left and right metallic rack ears with vertical mounting holes and screws
 * - Recessed bay container with dark brushed industrial aesthetic
 * - Quantized modular unit metrics (1U, 2U, 3U, and 0.5U collapsed spine)
 */
object RackChassisRenderer {

    const val U_HEIGHT_PX = 72.0f
    const val SPINE_HEIGHT_PX = 32.0f
    const val RACK_EAR_WIDTH = 34.0f
    const val UNIT_HEADER_HEIGHT = 28.0f
    const val UNIT_MARGIN_Y = 4.0f

    fun calculateUnitHeight(heightU: Int, isCollapsed: Boolean): Float {
        return when {
            isCollapsed -> SPINE_HEIGHT_PX
            else -> (heightU * U_HEIGHT_PX).coerceAtLeast(U_HEIGHT_PX)
        }
    }

    /**
     * Draws the left and right rack mounting rails and metallic screw heads.
     */
    fun drawRackEars(
        dl: ImDrawList,
        bayStartX: Float,
        bayStartY: Float,
        bayWidth: Float,
        bayHeight: Float
    ) {
        val earW = RACK_EAR_WIDTH
        val leftEarX = bayStartX - earW
        val rightEarX = bayStartX + bayWidth

        // Ear background (brushed dark steel)
        val earBgCol = ImGui.colorConvertFloat4ToU32(0.12f, 0.13f, 0.15f, 1.0f)
        val earBorderCol = ImGui.colorConvertFloat4ToU32(0.22f, 0.24f, 0.28f, 1.0f)
        val screwFillCol = ImGui.colorConvertFloat4ToU32(0.35f, 0.38f, 0.42f, 1.0f)
        val screwSlotCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.16f, 0.18f, 1.0f)
        val screwHighlightCol = ImGui.colorConvertFloat4ToU32(0.60f, 0.65f, 0.70f, 1.0f)

        // Left ear
        dl.addRectFilled(leftEarX, bayStartY, bayStartX, bayStartY + bayHeight, earBgCol)
        dl.addRect(leftEarX, bayStartY, bayStartX, bayStartY + bayHeight, earBorderCol)

        // Right ear
        dl.addRectFilled(rightEarX, bayStartY, rightEarX + earW, bayStartY + bayHeight, earBgCol)
        dl.addRect(rightEarX, bayStartY, rightEarX + earW, bayStartY + bayHeight, earBorderCol)

        // Draw screw holes every 1U interval
        val screwR = 4.5f
        val leftScrewCenterX = leftEarX + (earW * 0.5f)
        val rightScrewCenterX = rightEarX + (earW * 0.5f)

        var curY = bayStartY + (U_HEIGHT_PX * 0.5f)
        while (curY < bayStartY + bayHeight) {
            // Left screw
            drawScrewHead(dl, leftScrewCenterX, curY, screwR, screwFillCol, screwHighlightCol, screwSlotCol)
            // Right screw
            drawScrewHead(dl, rightScrewCenterX, curY, screwR, screwFillCol, screwHighlightCol, screwSlotCol)
            curY += U_HEIGHT_PX
        }
    }

    private fun drawScrewHead(
        dl: ImDrawList,
        cx: Float,
        cy: Float,
        r: Float,
        fillCol: Int,
        hiCol: Int,
        slotCol: Int
    ) {
        // Outer rim
        dl.addCircleFilled(cx, cy, r, fillCol)
        dl.addCircle(cx, cy, r, hiCol, 12, 1.0f)
        // Phillips / slot cross
        val slotLen = r * 0.65f
        dl.addLine(cx - slotLen, cy, cx + slotLen, cy, slotCol, 1.5f)
        dl.addLine(cx, cy - slotLen, cx, cy + slotLen, slotCol, 1.5f)
    }

    /**
     * Draws the recessed background chassis for an individual rack unit.
     */
    fun drawUnitChassis(
        dl: ImDrawList,
        posX: Float,
        posY: Float,
        width: Float,
        height: Float,
        isPowered: Boolean,
        isBypassed: Boolean,
        isSoloed: Boolean
    ) {
        // Main unit background
        val bgCol = when {
            !isPowered -> ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.09f, 0.95f)
            isBypassed -> ImGui.colorConvertFloat4ToU32(0.12f, 0.11f, 0.10f, 0.95f)
            isSoloed -> ImGui.colorConvertFloat4ToU32(0.15f, 0.14f, 0.10f, 0.95f)
            else -> ImGui.colorConvertFloat4ToU32(0.10f, 0.11f, 0.13f, 0.95f)
        }

        // Border styling
        val borderCol = when {
            isSoloed -> ImGui.colorConvertFloat4ToU32(0.95f, 0.75f, 0.15f, 0.85f)
            isBypassed -> ImGui.colorConvertFloat4ToU32(0.85f, 0.45f, 0.15f, 0.75f)
            isPowered -> ImGui.colorConvertFloat4ToU32(0.20f, 0.23f, 0.28f, 1.0f)
            else -> ImGui.colorConvertFloat4ToU32(0.15f, 0.16f, 0.18f, 0.60f)
        }

        dl.addRectFilled(posX, posY, posX + width, posY + height, bgCol, 2.0f)
        dl.addRect(posX, posY, posX + width, posY + height, borderCol, 2.0f, 0, if (isSoloed) 2.0f else 1.0f)

        // Subtle top bevel highlight
        val bevelCol = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.06f)
        dl.addLine(posX + 1f, posY + 1f, posX + width - 1f, posY + 1f, bevelCol, 1.0f)
    }
}
