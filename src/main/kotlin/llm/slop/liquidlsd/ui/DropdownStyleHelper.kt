package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiSelectableFlags
import imgui.flag.ImGuiStyleVar

/**
 * Style helpers for open combo boxes, context menus, and other small floating "dropdown" popups
 * (as opposed to modal dialogs, which keep their own deliberate layout/sizing). Applies the
 * Mixxx-inspired dropdown padding (roomy window/item/frame padding) and reuses [TooltipHelper]'s
 * bigger font so an open dropdown's list reads at the same size as a tooltip.
 *
 * [pushOpenDropdownPadding] must be pushed before the call that may create the popup window
 * (`ImGui.beginCombo` / `ImGui.beginPopup` / `ImGui.beginPopupContextItem`) -- the popup window
 * reads WindowPadding at creation time, which happens inside that call. [pushOpenDropdownFont]
 * must only be pushed after that call returns true, so a combo's closed-box preview text (drawn
 * by beginCombo itself, before the popup exists) keeps its normal size.
 */
private var dropdownFontPushed = false

fun pushOpenDropdownPadding() {
    ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 12f, 8f)
    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8f, 5f)
    ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 10f, 5f)
    // Freely-positioned dropdowns/menus (beginPopup-style) open with their vertical center at the
    // cursor's Y, instead of the default top-edge-at-cursor placement, so a tall list grows both
    // up and down from the click point rather than running off the bottom of the screen. Only
    // takes effect on the frame the popup newly appears (ImGuiCond.Appearing); real ImGui combo
    // boxes recompute their own position every frame once opened at least once, so this is a
    // no-op for them and they keep their normal anchored-under-the-box placement.
    ImGui.setNextWindowPos(ImGui.getMousePosX(), ImGui.getMousePosY(), ImGuiCond.Appearing, 0f, 0.5f)
}

/** Pop the 3 style vars pushed above, after the begin*()-guarded block, regardless of whether it was open. */
fun popOpenDropdownPadding() {
    ImGui.popStyleVar(3)
}

fun pushOpenDropdownFont() {
    val font = UITheme.fontFor(UITheme.FontLevel.TOOLTIP)
    dropdownFontPushed = font != null && font.ptr != 0L
    if (dropdownFontPushed) ImGui.pushFont(font, UITheme.FONT_TOOLTIP)
}

fun popOpenDropdownFont() {
    if (dropdownFontPushed) ImGui.popFont()
}

/** Mixxx-style row height for list/dropdown entries drawn via [selectableRow]. */
const val DROPDOWN_ROW_HEIGHT = 26f

/**
 * Drop-in replacement for [ImGui.selectable] for list/dropdown rows: spans the full row width,
 * uses [DROPDOWN_ROW_HEIGHT], and renders the selected row's label in crisp white instead of the
 * theme's default silver-gray body text (so it pops against unselected rows).
 */
fun selectableRow(label: String, isSelected: Boolean, sizeX: Float = 0f, flags: Int = 0): Boolean {
    if (isSelected) ImGui.pushStyleColor(ImGuiCol.Text, 1f, 1f, 1f, 1f)
    val clicked = ImGui.selectable(label, isSelected, flags or ImGuiSelectableFlags.SpanAllColumns, sizeX, DROPDOWN_ROW_HEIGHT)
    if (isSelected) ImGui.popStyleColor()
    return clicked
}
