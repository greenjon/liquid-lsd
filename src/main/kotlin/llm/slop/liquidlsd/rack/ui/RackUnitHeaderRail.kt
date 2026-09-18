package llm.slop.liquidlsd.rack.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import llm.slop.liquidlsd.rack.RackUnit
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip

/**
 * Standardized top header rail rendered across every rack unit.
 */
object RackUnitHeaderRail {

    fun draw(
        unit: RackUnit,
        unitIndex: Int,
        totalUnits: Int,
        railWidth: Float
    ) {
        ImGui.pushID(unit.id)

        val dl = ImGui.getWindowDrawList()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val railH = RackChassisRenderer.UNIT_HEADER_HEIGHT

        // Header background strip
        val headerBg = ImGui.colorConvertFloat4ToU32(0.14f, 0.15f, 0.18f, 1.0f)
        val headerDivider = ImGui.colorConvertFloat4ToU32(0.22f, 0.24f, 0.28f, 1.0f)
        dl.addRectFilled(startX, startY, startX + railWidth, startY + railH, headerBg, 2.0f)
        dl.addLine(startX, startY + railH, startX + railWidth, startY + railH, headerDivider, 1.0f)

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 2.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4.0f, 2.0f)

        // 1. Drag / Reorder handle
        ImGui.alignTextToFramePadding()
        ImGui.pushStyleColor(ImGuiCol.Text, 0.50f, 0.55f, 0.60f, 1.0f)
        ImGui.textUnformatted(Icons.MORE_VERTICAL)
        ImGui.popStyleColor()
        itemTooltip("Rack slot #${unitIndex + 1}")
        ImGui.sameLine()

        // 2. Power button
        val pwrCol = if (unit.isPowered) {
            floatArrayOf(0.15f, 0.75f, 0.40f, 1.0f)
        } else {
            floatArrayOf(0.30f, 0.32f, 0.35f, 1.0f)
        }
        ImGui.pushStyleColor(ImGuiCol.Button, pwrCol[0] * 0.3f, pwrCol[1] * 0.3f, pwrCol[2] * 0.3f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.Text, pwrCol[0], pwrCol[1], pwrCol[2], 1.0f)
        if (ImGui.button("${Icons.POWER}##pwr_${unit.id}")) {
            unit.isPowered = !unit.isPowered
        }
        ImGui.popStyleColor(2)
        itemTooltip(if (unit.isPowered) "Power: ON (Click to turn off)" else "Power: OFF (Click to turn on)")
        ImGui.sameLine()

        // 3. Bypass switch (BYP)
        val bypActive = unit.isBypassed
        if (bypActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.85f, 0.45f, 0.15f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 1.0f, 1.0f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.22f, 0.25f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.60f, 0.62f, 0.65f, 1.0f)
        }
        if (ImGui.button("BYP##${unit.id}")) {
            unit.isBypassed = !unit.isBypassed
        }
        ImGui.popStyleColor(2)
        itemTooltip("Bypass processing (passthrough video without shader cost)")
        ImGui.sameLine()

        // 4. Solo switch (SOLO)
        val soloActive = unit.isSoloed
        if (soloActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.95f, 0.75f, 0.15f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.10f, 0.10f, 0.10f, 1.0f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.22f, 0.25f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.60f, 0.62f, 0.65f, 1.0f)
        }
        if (ImGui.button("SOLO##${unit.id}")) {
            unit.isSoloed = !unit.isSoloed
        }
        ImGui.popStyleColor(2)
        itemTooltip("Solo unit output directly to master")
        ImGui.sameLine()

        // 5. Unit Type Badge
        val ut = unit.unitType
        ImGui.pushStyleColor(ImGuiCol.Button, ut.colorR * 0.25f, ut.colorG * 0.25f, ut.colorB * 0.25f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.Text, ut.colorR, ut.colorG, ut.colorB, 1.0f)
        ImGui.button(ut.badgeLabel)
        ImGui.popStyleColor(2)
        itemTooltip("Unit Type: ${ut.displayName}")
        ImGui.sameLine()

        // 6. Unit Title / Editable Label
        ImGui.pushStyleColor(ImGuiCol.Text, 0.90f, 0.92f, 0.95f, 1.0f)
        ImGui.textUnformatted(unit.label)
        ImGui.popStyleColor()
        ImGui.sameLine()

        // Right-aligned controls: Height badge, Collapse
        val rightGroupW = 70.0f
        val currentX = ImGui.getCursorPosX()
        val targetX = railWidth - rightGroupW
        if (targetX > currentX) {
            ImGui.setCursorPosX(targetX)
        }

        // Height badge (e.g. 1U, 2U)
        ImGui.pushStyleColor(ImGuiCol.Text, 0.50f, 0.55f, 0.60f, 1.0f)
        ImGui.textUnformatted("${unit.heightU}U")
        ImGui.popStyleColor()
        ImGui.sameLine()

        // Collapse / Expand toggle
        val foldIcon = if (unit.isCollapsed) Icons.FOLDER else Icons.CHEVRON_DOWN
        if (ImGui.button("${if (unit.isCollapsed) "+" else "-"}##fold_${unit.id}")) {
            unit.isCollapsed = !unit.isCollapsed
        }
        itemTooltip(if (unit.isCollapsed) "Expand to full unit controls" else "Collapse to 0.5U spine")

        ImGui.popStyleVar(2)
        ImGui.popID()
    }
}
