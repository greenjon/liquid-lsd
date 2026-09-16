package llm.slop.liquidlsd.rack.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rack.GenericRackUnit
import llm.slop.liquidlsd.rack.RackManager
import llm.slop.liquidlsd.rack.RackUnitType
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.UITheme

/**
 * Top-level workspace panel rendering the 19" Modular Video Rack.
 */
class RackPanel(
    val rackManager: RackManager = RackManager()
) {
    private var initializedFromSession = false
    private var isAddUnitPopupOpen = false

    fun draw(
        session: SessionContext,
        mixer: Mixer,
        panelWidth: Float,
        panelHeight: Float
    ) {
        if (!initializedFromSession) {
            rackManager.populateFromSession(mixer)
            initializedFromSession = true
        }

        rackManager.update()

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0f, 0f)

        // 1. Top Rack Master Toolbar
        drawTopToolbar(session, mixer, panelWidth)

        val toolbarH = 34.0f
        val contentH = (panelHeight - toolbarH).coerceAtLeast(100f)

        // 2. 19" Centered Bay Dimensions
        val earW = RackChassisRenderer.RACK_EAR_WIDTH
        val maxBayW = (panelWidth - (earW * 2f) - 32f).coerceAtLeast(400f)
        val bayW = minOf(maxBayW, 1400f)
        val bayStartX = ((panelWidth - bayW) * 0.5f).coerceAtLeast(earW + 8f)

        // 3. Scrollable Rack Bay Region
        ImGui.setCursorPosX(bayStartX - earW)
        ImGui.setCursorPosY(toolbarH)

        val childFlags = ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysVerticalScrollbar
        if (ImGui.beginChild("##rack_bay_chassis", bayW + (earW * 2f), contentH, false, childFlags)) {
            val dl = ImGui.getWindowDrawList()
            val startX = ImGui.getCursorScreenPosX() + earW
            val startY = ImGui.getCursorScreenPosY()

            // Calculate total height of all units
            var totalUnitsH = 0f
            for (unit in rackManager.units) {
                totalUnitsH += RackChassisRenderer.calculateUnitHeight(unit.heightU, unit.isCollapsed) + RackChassisRenderer.UNIT_MARGIN_Y
            }
            val insertionSlotH = 44.0f
            val totalBayH = maxOf(totalUnitsH + insertionSlotH + 40f, contentH)

            // Draw left & right metallic rack ears
            RackChassisRenderer.drawRackEars(dl, startX, startY, bayW, totalBayH)

            // Render Units
            var unitToRemoveId: String? = null
            var unitToMoveUpIdx: Int? = null
            var unitToMoveDownIdx: Int? = null

            ImGui.setCursorPosX(earW)
            ImGui.setCursorPosY(0f)

            for (i in rackManager.units.indices) {
                val unit = rackManager.units[i]
                val unitH = RackChassisRenderer.calculateUnitHeight(unit.heightU, unit.isCollapsed)
                val unitScreenX = ImGui.getCursorScreenPosX()
                val unitScreenY = ImGui.getCursorScreenPosY()

                // Draw recessed chassis
                RackChassisRenderer.drawUnitChassis(
                    dl, unitScreenX, unitScreenY, bayW, unitH,
                    unit.isPowered, unit.isBypassed, unit.isSoloed
                )

                // Header rail
                RackUnitHeaderRail.draw(
                    unit = unit,
                    unitIndex = i,
                    totalUnits = rackManager.units.size,
                    railWidth = bayW,
                    onMoveUp = { unitToMoveUpIdx = i },
                    onMoveDown = { unitToMoveDownIdx = i },
                    onRemove = { unitToRemoveId = unit.id }
                )

                // Faceplate parameters (if not collapsed)
                if (!unit.isCollapsed) {
                    RackFaceplateGrid.drawFaceplate(unit, bayW, unitH - RackChassisRenderer.UNIT_HEADER_HEIGHT)
                }

                // Advance cursor for next unit
                ImGui.setCursorPosY(ImGui.getCursorPosY() + (unitH - RackChassisRenderer.UNIT_HEADER_HEIGHT) + RackChassisRenderer.UNIT_MARGIN_Y)
            }

            // Apply deferred reordering or removal
            unitToRemoveId?.let { rackManager.removeUnit(it) }
            unitToMoveUpIdx?.let { rackManager.moveUp(it) }
            unitToMoveDownIdx?.let { rackManager.moveDown(it) }

            // Empty insertion slot at bottom
            drawInsertionSlot(startX, bayW, insertionSlotH)
        }
        ImGui.endChild()

        // Popup: Add Unit
        drawAddUnitModal(mixer)

        ImGui.popStyleVar()
    }

    private fun drawTopToolbar(session: SessionContext, mixer: Mixer, panelWidth: Float) {
        val toolbarH = 34.0f
        val dl = ImGui.getWindowDrawList()
        val screenX = ImGui.getCursorScreenPosX()
        val screenY = ImGui.getCursorScreenPosY()

        val bgCol = ImGui.colorConvertFloat4ToU32(0.11f, 0.12f, 0.14f, 1.0f)
        val borderCol = ImGui.colorConvertFloat4ToU32(0.20f, 0.22f, 0.25f, 1.0f)
        dl.addRectFilled(screenX, screenY, screenX + panelWidth, screenY + toolbarH, bgCol)
        dl.addLine(screenX, screenY + toolbarH, screenX + panelWidth, screenY + toolbarH, borderCol, 1.0f)

        ImGui.setCursorPosX(16.0f)
        ImGui.setCursorPosY(6.0f)

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8.0f, 0f)

        // Branding
        ImGui.alignTextToFramePadding()
        ImGui.pushStyleColor(ImGuiCol.Text, 0.25f, 0.85f, 0.95f, 1.0f)
        ImGui.textUnformatted("${Icons.SETTINGS} 19\" MODULAR VIDEO RACK")
        ImGui.popStyleColor()
        ImGui.sameLine()

        // Unit count
        ImGui.pushStyleColor(ImGuiCol.Text, 0.50f, 0.55f, 0.60f, 1.0f)
        ImGui.textUnformatted("• ${rackManager.units.size} Units")
        ImGui.popStyleColor()
        ImGui.sameLine()

        // Add Unit button
        ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.55f, 0.85f, 0.8f)
        if (ImGui.button("${Icons.FILE} + ADD UNIT")) {
            isAddUnitPopupOpen = true
        }
        ImGui.popStyleColor()
        ImGui.sameLine()

        // Master Bypass button
        val bypActive = rackManager.isMasterBypassed
        if (bypActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.85f, 0.45f, 0.15f, 1.0f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.22f, 0.25f, 1.0f)
        }
        if (ImGui.button("MASTER BYPASS")) {
            rackManager.toggleMasterBypass()
        }
        ImGui.popStyleColor()
        ImGui.sameLine()

        // Fold / Unfold All
        val foldText = if (rackManager.isMasterFolded) "UNFOLD ALL" else "FOLD ALL"
        if (ImGui.button(foldText)) {
            rackManager.toggleFoldAll()
        }
        ImGui.sameLine()

        // Clear Solo
        if (ImGui.button("CLEAR SOLO")) {
            rackManager.clearAllSolo()
        }
        ImGui.sameLine()

        // Reset to session
        if (ImGui.button("${Icons.REFRESH} RE-SYNC SESSION")) {
            rackManager.populateFromSession(mixer)
        }

        ImGui.popStyleVar(2)
    }

    private fun drawInsertionSlot(startX: Float, bayWidth: Float, slotHeight: Float) {
        val dl = ImGui.getWindowDrawList()
        val curScreenX = startX
        val curScreenY = ImGui.getCursorScreenPosY()

        // Dashed / outline slot
        val borderCol = ImGui.colorConvertFloat4ToU32(0.30f, 0.35f, 0.40f, 0.6f)
        val bgCol = ImGui.colorConvertFloat4ToU32(0.12f, 0.13f, 0.15f, 0.5f)
        dl.addRectFilled(curScreenX, curScreenY, curScreenX + bayWidth, curScreenY + slotHeight, bgCol, 2.0f)
        dl.addRect(curScreenX, curScreenY, curScreenX + bayWidth, curScreenY + slotHeight, borderCol, 2.0f)

        ImGui.setCursorPosX(RackChassisRenderer.RACK_EAR_WIDTH + (bayWidth * 0.5f) - 80f)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 8f)

        if (ImGui.button("+ INSERT RACK MODULE", 160f, 26f)) {
            isAddUnitPopupOpen = true
        }
    }

    private fun drawAddUnitModal(mixer: Mixer) {
        if (isAddUnitPopupOpen) {
            ImGui.openPopup("Add Rack Unit##popup")
        }

        if (ImGui.beginPopupModal("Add Rack Unit##popup", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Select module type to insert into rack:")
            ImGui.separator()

            if (ImGui.button("Generator: Deck A Clone", 260f, 32f)) {
                rackManager.addUnit(llm.slop.liquidlsd.rack.DeckGeneratorUnit(mixer.deckA, isDeckA = true, label = "Deck A Synth Extra"))
                isAddUnitPopupOpen = false
                ImGui.closeCurrentPopup()
            }

            if (ImGui.button("Generator: Deck B Clone", 260f, 32f)) {
                rackManager.addUnit(llm.slop.liquidlsd.rack.DeckGeneratorUnit(mixer.deckB, isDeckA = false, label = "Deck B Synth Extra"))
                isAddUnitPopupOpen = false
                ImGui.closeCurrentPopup()
            }

            if (ImGui.button("Processor: Feedback Loop", 260f, 32f)) {
                rackManager.addUnit(llm.slop.liquidlsd.rack.FeedbackProcessorUnit(mixer.deckA, isDeckA = true, label = "Feedback FX Unit"))
                isAddUnitPopupOpen = false
                ImGui.closeCurrentPopup()
            }

            if (ImGui.button("Utility: Generic Blank Unit", 260f, 32f)) {
                rackManager.addUnit(GenericRackUnit(label = "Custom Utility Unit", unitType = RackUnitType.UTILITY, heightU = 1))
                isAddUnitPopupOpen = false
                ImGui.closeCurrentPopup()
            }

            ImGui.separator()
            if (ImGui.button("Cancel", 120f, 26f)) {
                isAddUnitPopupOpen = false
                ImGui.closeCurrentPopup()
            }

            ImGui.endPopup()
        }
    }
}
