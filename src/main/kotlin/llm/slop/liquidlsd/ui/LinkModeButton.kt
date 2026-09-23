package llm.slop.liquidlsd.ui

import imgui.ImColor
import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.macro.MacroLinkMode
import llm.slop.liquidlsd.rendering.isf.MetaLinkMode

/**
 * Mixxx-inspired visual transfer curve widget for parameter and macro linking.
 *
 * Renders an intuitive vector glyph directly on the button face using [ImDrawList], showing
 * at a single glance whether a target parameter responds across the full knob turn, the left half,
 * the right half, a center triangle peak, or bipolar center-neutral. An adjacent [±] button toggles
 * direction inversion, which immediately flips the glyph geometry to reflect the inverted curve.
 */
object LinkModeButton {

    /**
     * Draws the visual link button and optional adjacent invert button for a [MacroLinkMode].
     */
    fun drawMacroLink(
        id: String,
        mode: MacroLinkMode,
        inverted: Boolean,
        isLinked: Boolean = true,
        showInvertButton: Boolean = true,
        width: Float = 28f,
        height: Float = 18f,
        onCycleMode: () -> Unit,
        onSelectMode: (MacroLinkMode) -> Unit,
        onToggleInvert: () -> Unit
    ) {
        drawInternal(
            id = id,
            isLinked = isLinked,
            isFirstHalf = mode == MacroLinkMode.FIRST_HALF,
            isSecondHalf = mode == MacroLinkMode.SECOND_HALF,
            isTriangle = mode == MacroLinkMode.TRIANGLE,
            isBipolar = mode == MacroLinkMode.BIPOLAR,
            isFull = mode == MacroLinkMode.FULL,
            inverted = inverted,
            showInvertButton = showInvertButton,
            width = width,
            height = height,
            modeName = when {
                !isLinked -> "Unlinked"
                mode == MacroLinkMode.FULL -> "Full Range (0%–100%)"
                mode == MacroLinkMode.FIRST_HALF -> "First Half (0%–50%)"
                mode == MacroLinkMode.SECOND_HALF -> "Second Half (50%–100%)"
                mode == MacroLinkMode.TRIANGLE -> "Triangle Peak (0%–100%–0%)"
                mode == MacroLinkMode.BIPOLAR -> "Bipolar (Center-0)"
                else -> "Full Range"
            },
            onCycle = onCycleMode,
            onToggleInvert = onToggleInvert,
            drawContextMenu = {
                if (ImGui.menuItem("Full Range (0%–100%)", "", isLinked && mode == MacroLinkMode.FULL)) {
                    onSelectMode(MacroLinkMode.FULL)
                }
                if (ImGui.menuItem("First Half (0%–50%)", "", isLinked && mode == MacroLinkMode.FIRST_HALF)) {
                    onSelectMode(MacroLinkMode.FIRST_HALF)
                }
                if (ImGui.menuItem("Second Half (50%–100%)", "", isLinked && mode == MacroLinkMode.SECOND_HALF)) {
                    onSelectMode(MacroLinkMode.SECOND_HALF)
                }
                if (ImGui.menuItem("Triangle Peak (0%–100%–0%)", "", isLinked && mode == MacroLinkMode.TRIANGLE)) {
                    onSelectMode(MacroLinkMode.TRIANGLE)
                }
                if (ImGui.menuItem("Bipolar (Center-0)", "", isLinked && mode == MacroLinkMode.BIPOLAR)) {
                    onSelectMode(MacroLinkMode.BIPOLAR)
                }
                ImGui.separator()
                if (ImGui.menuItem("Invert Direction", "", inverted)) {
                    onToggleInvert()
                }
            }
        )
    }

    /**
     * Draws the visual link button and optional adjacent invert button for an FX slot's [MetaLinkMode].
     */
    fun drawMetaLink(
        id: String,
        mode: MetaLinkMode?,
        inverted: Boolean,
        showInvertButton: Boolean = true,
        width: Float = 28f,
        height: Float = 18f,
        onCycleMode: () -> Unit,
        onSelectMode: (MetaLinkMode?) -> Unit,
        onToggleInvert: () -> Unit
    ) {
        val isLinked = mode != null
        drawInternal(
            id = id,
            isLinked = isLinked,
            isFirstHalf = mode == MetaLinkMode.FIRST_HALF,
            isSecondHalf = mode == MetaLinkMode.SECOND_HALF,
            isTriangle = mode == MetaLinkMode.TRIANGLE,
            isBipolar = mode == MetaLinkMode.BIPOLAR,
            isFull = mode == MetaLinkMode.FULL,
            inverted = inverted,
            showInvertButton = showInvertButton,
            width = width,
            height = height,
            modeName = when {
                mode == null -> "Unlinked (Manual)"
                mode == MetaLinkMode.FULL -> "Full Range (0%–100%)"
                mode == MetaLinkMode.FIRST_HALF -> "First Half (0%–50%)"
                mode == MetaLinkMode.SECOND_HALF -> "Second Half (50%–100%)"
                mode == MetaLinkMode.TRIANGLE -> "Triangle Peak (0%–100%–0%)"
                mode == MetaLinkMode.BIPOLAR -> "Bipolar (Center-0)"
                else -> "Full Range"
            },
            onCycle = onCycleMode,
            onToggleInvert = onToggleInvert,
            drawContextMenu = {
                if (ImGui.menuItem("Unlinked (Manual)", "", mode == null)) {
                    onSelectMode(null)
                }
                ImGui.separator()
                if (ImGui.menuItem("Full Range (0%–100%)", "", mode == MetaLinkMode.FULL)) {
                    onSelectMode(MetaLinkMode.FULL)
                }
                if (ImGui.menuItem("First Half (0%–50%)", "", mode == MetaLinkMode.FIRST_HALF)) {
                    onSelectMode(MetaLinkMode.FIRST_HALF)
                }
                if (ImGui.menuItem("Second Half (50%–100%)", "", mode == MetaLinkMode.SECOND_HALF)) {
                    onSelectMode(MetaLinkMode.SECOND_HALF)
                }
                if (ImGui.menuItem("Triangle Peak (0%–100%–0%)", "", mode == MetaLinkMode.TRIANGLE)) {
                    onSelectMode(MetaLinkMode.TRIANGLE)
                }
                if (ImGui.menuItem("Bipolar (Center-0)", "", mode == MetaLinkMode.BIPOLAR)) {
                    onSelectMode(MetaLinkMode.BIPOLAR)
                }
                ImGui.separator()
                if (ImGui.menuItem("Invert Direction", "", inverted)) {
                    onToggleInvert()
                }
            }
        )
    }

    private fun drawInternal(
        id: String,
        isLinked: Boolean,
        isFirstHalf: Boolean,
        isSecondHalf: Boolean,
        isTriangle: Boolean,
        isBipolar: Boolean,
        isFull: Boolean,
        inverted: Boolean,
        showInvertButton: Boolean,
        width: Float,
        height: Float,
        modeName: String,
        onCycle: () -> Unit,
        onToggleInvert: () -> Unit,
        drawContextMenu: () -> Unit
    ) {
        val cursorPos = ImGui.getCursorScreenPos()
        val pMinX = cursorPos.x
        val pMinY = cursorPos.y
        val pMaxX = pMinX + width
        val pMaxY = pMinY + height

        ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1f, 1f, 1f, 0.12f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 0.22f)

        val clicked = ImGui.invisibleButton("btn_$id", width.coerceAtLeast(1f), height.coerceAtLeast(1f))

        ImGui.popStyleColor(3)

        val isHovered = ImGui.isItemHovered()
        val isActive = ImGui.isItemActive()
        val drawList = ImGui.getWindowDrawList()

        // Background frame
        val bgCol = if (isActive) ImColor.rgba(1f, 1f, 1f, 0.2f)
        else if (isHovered) ImColor.rgba(1f, 1f, 1f, 0.1f)
        else if (isLinked) ImColor.rgba(0.12f, 0.18f, 0.24f, 0.85f)
        else ImColor.rgba(0.10f, 0.11f, 0.12f, 0.7f)

        val frameCol = if (isHovered) ImColor.rgba(0.2f, 0.85f, 1.0f, 0.8f)
        else if (isLinked) ImColor.rgba(0.2f, 0.75f, 0.95f, 0.45f)
        else ImColor.rgba(0.25f, 0.28f, 0.32f, 0.4f)

        drawList.addRectFilled(pMinX, pMinY, pMaxX, pMaxY, bgCol, 3f)
        drawList.addRect(pMinX, pMinY, pMaxX, pMaxY, frameCol, 3f)

        // Vector geometry bounds
        val padX = 4f
        val padY = 3.5f
        val startX = pMinX + padX
        val endX = pMaxX - padX
        val startY = pMinY + padY
        val endY = pMaxY - padY
        val midX = (startX + endX) * 0.5f
        val midY = (startY + endY) * 0.5f

        // Subtle vertical 50% divider tick mark
        val tickCol = if (isLinked) ImColor.rgba(1f, 1f, 1f, 0.18f) else ImColor.rgba(1f, 1f, 1f, 0.08f)
        drawList.addLine(midX, startY, midX, endY, tickCol, 1.0f)

        // Curve stroke color
        val lineColor = if (!isLinked) {
            ImColor.rgba(0.5f, 0.55f, 0.6f, 0.35f)
        } else if (isHovered) {
            ImColor.rgba(0.4f, 0.95f, 1.0f, 1.0f)
        } else {
            ImColor.rgba(0.2f, 0.85f, 1.0f, 0.95f) // Electric Cyan
        }
        val thickness = if (isLinked) 2.0f else 1.5f

        when {
            !isLinked -> {
                // Dim horizontal dashed center line
                drawList.addLine(startX + 2f, midY, midX - 2f, midY, lineColor, thickness)
                drawList.addLine(midX + 2f, midY, endX - 2f, midY, lineColor, thickness)
            }
            isFirstHalf -> {
                if (!inverted) {
                    drawList.addLine(startX, endY, midX, startY, lineColor, thickness)
                    drawList.addLine(midX, startY, endX, startY, lineColor, thickness)
                } else {
                    drawList.addLine(startX, startY, midX, endY, lineColor, thickness)
                    drawList.addLine(midX, endY, endX, endY, lineColor, thickness)
                }
            }
            isSecondHalf -> {
                if (!inverted) {
                    drawList.addLine(startX, endY, midX, endY, lineColor, thickness)
                    drawList.addLine(midX, endY, endX, startY, lineColor, thickness)
                } else {
                    drawList.addLine(startX, startY, midX, startY, lineColor, thickness)
                    drawList.addLine(midX, startY, endX, endY, lineColor, thickness)
                }
            }
            isTriangle -> {
                if (!inverted) {
                    drawList.addLine(startX, endY, midX, startY, lineColor, thickness)
                    drawList.addLine(midX, startY, endX, endY, lineColor, thickness)
                } else {
                    drawList.addLine(startX, startY, midX, endY, lineColor, thickness)
                    drawList.addLine(midX, endY, endX, startY, lineColor, thickness)
                }
            }
            isBipolar -> {
                if (!inverted) {
                    drawList.addLine(startX, startY, midX, endY, lineColor, thickness)
                    drawList.addLine(midX, endY, endX, startY, lineColor, thickness)
                } else {
                    drawList.addLine(startX, endY, midX, startY, lineColor, thickness)
                    drawList.addLine(midX, startY, endX, endY, lineColor, thickness)
                }
            }
            isFull -> {
                if (!inverted) {
                    drawList.addLine(startX, endY, endX, startY, lineColor, thickness)
                } else {
                    drawList.addLine(startX, startY, endX, endY, lineColor, thickness)
                }
            }
        }

        // Click actions
        if (clicked) {
            onCycle()
        }

        // Right-click context popup
        val popupId = "link_ctx_$id"
        if (ImGui.beginPopupContextItem(popupId)) {
            ImGui.textDisabled("Link Mode")
            ImGui.separator()
            drawContextMenu()
            ImGui.endPopup()
        }

        // Mixxx-style rich tooltip
        val invText = if (inverted && isLinked) " [Inverted]" else ""
        itemTooltip("Link: $modeName$invText\nLeft-click to cycle modes. Right-click to choose.")

        // Adjacent Invert button [±]
        if (showInvertButton) {
            ImGui.sameLine(0f, 4f)
            val invBtnW = 20f
            if (inverted) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImColor.rgba(0.12f, 0.35f, 0.45f, 0.9f))
                ImGui.pushStyleColor(ImGuiCol.Text, ImColor.rgba(0.2f, 0.95f, 1.0f, 1.0f))
            } else {
                ImGui.pushStyleColor(ImGuiCol.Text, ImColor.rgba(0.6f, 0.65f, 0.7f, 0.8f))
            }

            if (ImGui.button("±##inv_$id", invBtnW, height)) {
                onToggleInvert()
            }

            if (inverted) {
                ImGui.popStyleColor(2)
            } else {
                ImGui.popStyleColor(1)
            }

            itemTooltip(if (inverted) "Inversion Active: Direction is inverted.\nClick to restore normal direction." else "Inversion Inactive: Click to invert parameter travel direction.")
        }
    }
}
