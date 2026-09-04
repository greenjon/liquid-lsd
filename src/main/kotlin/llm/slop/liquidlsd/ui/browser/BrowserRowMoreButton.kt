package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.ui.Icons

/**
 * Helper to draw a right-aligned vertical kebab ("⋮") button on list/grid rows.
 * Appears when the row is hovered, selected (via mouse or keyboard/arrow keys),
 * or when its context popup menu is currently open.
 *
 * Left-clicking or right-clicking the button opens the popup identified by [popupId].
 */
object BrowserRowMoreButton {

    /**
     * Draws the vertical kebab button on the current row.
     *
     * @param popupId The unique popup ID for this item's context menu.
     * @param isRowHovered Whether the main row item is hovered (from `ImGui.isItemHovered()`).
     * @param isSelected Whether the row is selected.
     * @param idSuffix A unique ID suffix for the button (e.g. index or paramKey).
     * @param btnWidth Width of the button in pixels (defaults to 20f).
     * @param btnHeight Height of the button in pixels (0f to match last item height).
     * @param tooltip Optional tooltip when hovering the button directly.
     * @return true if the button was clicked on this frame.
     */
    fun draw(
        popupId: String,
        isRowHovered: Boolean,
        isSelected: Boolean,
        idSuffix: String,
        btnWidth: Float = 28f,
        btnHeight: Float = 0f,
        tooltip: String = "Row options..."
    ): Boolean {
        val isPopupOpen = ImGui.isPopupOpen(popupId)
        val rowH = if (btnHeight > 0f) btnHeight else ImGui.getItemRectSizeY().coerceAtLeast(ImGui.getTextLineHeight())

        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        val btnMinX = ImGui.getCursorScreenPosX()
        val btnMinY = ImGui.getCursorScreenPosY()
        val isMouseOverBtn = mouseX >= btnMinX && mouseX <= btnMinX + btnWidth && mouseY >= btnMinY && mouseY <= btnMinY + rowH

        val isVisible = isRowHovered || isMouseOverBtn || isSelected || isPopupOpen

        ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1f, 1f, 1f, 0.15f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 0.25f)

        // Only show border outline on hover; completely transparent otherwise
        if (isMouseOverBtn) {
            ImGui.pushStyleColor(ImGuiCol.Border, 1f, 1f, 1f, 0.35f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Border, 0f, 0f, 0f, 0f)
        }

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f)

        // Draw the button (empty text so standard button handles hit-testing, active state, styling, and borders)
        val clicked = ImGui.button("##row_more_$idSuffix", btnWidth, rowH)
        val isBtnHovered = ImGui.isItemHovered()

        if (isVisible) {
            val dl = ImGui.getWindowDrawList()
            val dotCol = if (isBtnHovered) {
                ImGui.getColorU32(ImGuiCol.Text)
            } else {
                ImGui.colorConvertFloat4ToU32(0.85f, 0.85f, 0.85f, 0.75f)
            }
            val cx = btnMinX + btnWidth * 0.5f
            val cy = btnMinY + rowH * 0.5f
            val r = 3.5f
            val spacing = 9.0f

            dl.addCircleFilled(cx, cy - spacing, r, dotCol)
            dl.addCircleFilled(cx, cy, r, dotCol)
            dl.addCircleFilled(cx, cy + spacing, r, dotCol)
        }

        // When clicked, close any currently open popup before opening this popup,
        // and also check isMouseClicked directly so clicking while another popup is active works immediately
        val mouseClickedThisBtn = isMouseOverBtn && (ImGui.isMouseClicked(0) || ImGui.isMouseClicked(1))
        if (clicked || ImGui.isItemClicked(0) || ImGui.isItemClicked(1) || mouseClickedThisBtn) {
            ImGui.closeCurrentPopup()
            ImGui.openPopup(popupId)
        }

        if (isBtnHovered && isVisible && tooltip.isNotEmpty() && !isPopupOpen && !ImGui.isPopupOpen(popupId)) {
            ImGui.setTooltip(tooltip)
        }

        ImGui.popStyleVar()
        ImGui.popStyleColor(4)

        return clicked || mouseClickedThisBtn
    }
}
