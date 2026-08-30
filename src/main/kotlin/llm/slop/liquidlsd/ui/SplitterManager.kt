package llm.slop.liquidlsd.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseCursor

/**
 * Handles mouse hit-testing, dragging, and visual rendering for horizontal
 * and vertical workspace panel splitters.
 */
class SplitterManager {
    var activeSplitterId: String? = null
        private set

    fun drawVerticalSplitter(
        id: String,
        posX: Float,
        posY: Float,
        width: Float,
        height: Float,
        displayWidth: Float,
        drawList: ImDrawList? = null,
        onDrag: (Float) -> Unit,
        onDoubleClick: () -> Unit
    ) {
        val io = ImGui.getIO()
        val mouseX = io.mousePosX
        val mouseY = io.mousePosY
        val halfW = width / 2f
        val inBounds = mouseX >= (posX - halfW) && mouseX <= (posX + halfW) && mouseY >= posY && mouseY <= (posY + height)

        val isActive = activeSplitterId == id
        val isHovered = isActive || (activeSplitterId == null && inBounds)

        if (isActive) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW)
            val deltaX = io.mouseDeltaX
            if (deltaX != 0f) {
                onDrag(deltaX)
            }
            if (!ImGui.isMouseDown(0)) {
                activeSplitterId = null
            }
        } else if (activeSplitterId == null && inBounds) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW)
            if (ImGui.isMouseDoubleClicked(0)) {
                onDoubleClick()
            } else if (ImGui.isMouseClicked(0)) {
                activeSplitterId = id
            }
        }

        val color = when {
            isActive -> ImGui.getColorU32(ImGuiCol.SeparatorActive)
            isHovered -> ImGui.getColorU32(ImGuiCol.SeparatorHovered)
            else -> ImGui.getColorU32(ImGuiCol.Separator)
        }
        val dl = drawList ?: ImGui.getWindowDrawList()
        dl.addLine(posX, posY, posX, posY + height, color, if (isActive || isHovered) 2.5f else 1.5f)
    }

    fun drawHorizontalSplitter(
        id: String,
        posX: Float,
        posY: Float,
        width: Float,
        height: Float,
        displayHeight: Float,
        drawList: ImDrawList? = null,
        onDrag: (Float) -> Unit,
        onDoubleClick: () -> Unit
    ) {
        val io = ImGui.getIO()
        val mouseX = io.mousePosX
        val mouseY = io.mousePosY
        val inBounds = mouseX >= posX && mouseX <= (posX + width) && mouseY >= (posY - 4f) && mouseY <= (posY + height)

        val isAnyItemInteracted = ImGui.isAnyItemHovered() || ImGui.isAnyItemActive()
        val isActive = activeSplitterId == id
        val isHovered = isActive || (activeSplitterId == null && inBounds && !isAnyItemInteracted)

        if (isActive) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS)
            val deltaY = io.mouseDeltaY
            if (deltaY != 0f) {
                onDrag(deltaY)
            }
            if (!ImGui.isMouseDown(0)) {
                activeSplitterId = null
            }
        } else if (activeSplitterId == null && inBounds && !isAnyItemInteracted) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS)
            if (ImGui.isMouseDoubleClicked(0)) {
                onDoubleClick()
            } else if (ImGui.isMouseClicked(0)) {
                activeSplitterId = id
            }
        }

        val color = when {
            isActive -> ImGui.getColorU32(ImGuiCol.SeparatorActive)
            isHovered -> ImGui.getColorU32(ImGuiCol.SeparatorHovered)
            else -> ImGui.getColorU32(ImGuiCol.Separator)
        }
        val dl = drawList ?: ImGui.getWindowDrawList()
        dl.addLine(posX, posY, posX + width, posY, color, if (isActive || isHovered) 2.5f else 1.5f)
    }
}
