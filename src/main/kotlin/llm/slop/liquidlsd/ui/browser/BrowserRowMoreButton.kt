package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.ui.Icons

/**
 * Helper to draw a right-aligned vertical kebab ("⋮") button on list rows.
 * Appears when the row is hovered, selected (via mouse or keyboard/arrow keys),
 * or when its context popup menu is currently open.
 *
 * Clicking the button opens the popup identified by [popupId].
 */
object BrowserRowMoreButton {

    /**
     * Draws the vertical kebab button on the current row.
     *
     * @param popupId The unique popup ID for this item's context menu.
     * @param isRowHovered Whether the row was hovered on this frame (from `ImGui.isItemHovered()`).
     * @param isSelected Whether the row is selected.
     * @param idSuffix A unique ID suffix for the button (e.g. index).
     * @param btnWidth Width of the button in pixels (defaults to 20f).
     * @return true if the button was left-clicked on this frame.
     */
    fun draw(
        popupId: String,
        isRowHovered: Boolean,
        isSelected: Boolean,
        idSuffix: String,
        btnWidth: Float = 20f
    ): Boolean {
        val isPopupOpen = ImGui.isPopupOpen(popupId)
        val isVisible = isRowHovered || isSelected || isPopupOpen

        if (!isVisible) {
            return false
        }

        val rowH = ImGui.getItemRectSizeY().coerceAtLeast(ImGui.getTextLineHeight())
        val rightX = (ImGui.getWindowContentRegionMaxX() - btnWidth).coerceAtLeast(ImGui.getCursorPosX())

        ImGui.sameLine()
        ImGui.setCursorPosX(rightX)

        ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1f, 1f, 1f, 0.15f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 0.25f)
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f)

        val clicked = ImGui.button("${Icons.MORE_VERTICAL}##row_more_$idSuffix", btnWidth, rowH)

        if (ImGui.isItemClicked(1)) {
            ImGui.openPopup(popupId)
        }

        ImGui.popStyleVar()
        ImGui.popStyleColor(3)

        if (clicked) {
            ImGui.openPopup(popupId)
        }

        return clicked
    }
}
