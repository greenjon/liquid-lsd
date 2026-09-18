package llm.slop.liquidlsd.rack.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rack.RackManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip

/**
 * Top-level workspace panel rendering the 19" Modular Video Rack.
 */
class RackPanel(
    val rackManager: RackManager = RackManager()
) {
    private var initializedFromSession = false
    var isRearView = false

    fun draw(
        session: SessionContext,
        mixer: Mixer,
        renderer: Renderer,
        panelWidth: Float,
        panelHeight: Float
    ) {
        if (!initializedFromSession) {
            rackManager.populateFromSession(mixer)
            initializedFromSession = true
        }

        rackManager.update()
        // Resolves each unit's lastOutputTexture (normalled/patched routing) from this frame's
        // already-rendered Deck/Mixer/FX state so the confidence monitors and rear-panel LEDs
        // reflect real signal state. Pure texture-ID reads for the built-in unit types (see
        // RackUnit.kt doc comments) -- safe to call here mid-ImGui-draw with no GL side effects.
        rackManager.process(renderer)

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

        // Handle Tab key shortcut to flip between Front and Rear
        if (llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.isTriggered("global.flip_rack")) {
            isRearView = !isRearView
        }

        // 3. Scrollable Rack Bay Region
        ImGui.setCursorPosX(bayStartX - earW)
        ImGui.setCursorPosY(toolbarH)

        RackRearChassisRenderer.clearFrameState()

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
            val totalBayH = maxOf(totalUnitsH + 40f, contentH)

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

                val bodyH = unitH - RackChassisRenderer.UNIT_HEADER_HEIGHT

                // Body: Faceplate (Front) vs Chassis Jacks (Rear)
                if (!unit.isCollapsed) {
                    if (isRearView) {
                        RackRearChassisRenderer.drawUnitRear(session, unit, rackManager.patchBay, bayW, bodyH)
                    } else {
                        RackFaceplateGrid.drawFaceplate(session, unit, bayW, bodyH, renderer)
                    }
                }

                // Advance cursor for next unit
                ImGui.setCursorPosY(ImGui.getCursorPosY() + bodyH + RackChassisRenderer.UNIT_MARGIN_Y)
            }

            // Submits an item at the final cursor position so the child window's content bounds
            // are legitimately extended down to it -- ImGui asserts if SetCursorPos moves past the
            // last submitted item with nothing submitted there (see imgui.cpp's "Please submit an
            // item e.g. Dummy() afterwards" assertion). This used to be the "+ INSERT RACK MODULE"
            // button; removing that in the ad-hoc-units cleanup left the cursor advance with
            // nothing after it.
            ImGui.dummy(0f, 0f)

            // Draw Virtual Patch Cables across the entire rear bay (drawn on top of all units)
            if (isRearView) {
                drawPatchCablesOverlay(dl)
            }

            // Apply deferred reordering or removal
            unitToRemoveId?.let {
                rackManager.removeUnit(it)
                RackMicroMonitor.releaseUnit(it)
            }
            unitToMoveUpIdx?.let { rackManager.moveUp(it) }
            unitToMoveDownIdx?.let { rackManager.moveDown(it) }
        }
        ImGui.endChild()

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

        // Flip Rack View (Tab) button
        if (isRearView) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.25f, 0.65f, 0.90f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 1.0f, 1.0f)
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.22f, 0.25f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.Text, 0.70f, 0.75f, 0.80f, 1.0f)
        }
        val flipLabel = if (isRearView) "${Icons.REFRESH} REAR CHASSIS (Tab)" else "${Icons.REFRESH} FRONT FACEPLATE (Tab)"
        if (ImGui.button(flipLabel)) {
            isRearView = !isRearView
        }
        ImGui.popStyleColor(2)
        itemTooltip("Flip rack 180° between Front Performance Faceplates and Rear Patch Cable Chassis (Shortcut: Tab)")
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
        itemTooltip("Bypass processing on every rack unit at once (passthrough, overrides individual unit bypass).")
        ImGui.sameLine()

        // Fold / Unfold All
        val foldText = if (rackManager.isMasterFolded) "UNFOLD ALL" else "FOLD ALL"
        if (ImGui.button(foldText)) {
            rackManager.toggleFoldAll()
        }
        itemTooltip(if (rackManager.isMasterFolded) "Expand every rack unit back to full controls." else "Collapse every rack unit to its 0.5U spine.")
        ImGui.sameLine()

        // Clear Solo
        if (ImGui.button("CLEAR SOLO")) {
            rackManager.clearAllSolo()
        }
        itemTooltip("Un-solo every rack unit.")
        ImGui.sameLine()

        // Reset to session
        if (ImGui.button("${Icons.REFRESH} RE-SYNC SESSION")) {
            // populateFromSession discards the old unit list without disposing it (units hold no
            // GPU resources of their own -- see RackUnit.kt), but the monitor's preview FBOs are
            // keyed by the old (now-orphaned) unit IDs and must be released here or they'd leak.
            RackMicroMonitor.releaseAll()
            rackManager.populateFromSession(mixer)
        }
        itemTooltip("Rebuild the rack from the current session's Decks/Mixer, discarding any added units and patch cables.")

        ImGui.popStyleVar(2)
    }

    private fun drawPatchCablesOverlay(dl: imgui.ImDrawList) {
        val jackMap = RackRearChassisRenderer.registeredJackPositions

        // 1. Draw established patch cables
        for (cable in rackManager.patchBay.getCables()) {
            val fromCoord = jackMap[cable.fromPort.fullId]
            val toCoord = jackMap[cable.toPort.fullId]
            if (fromCoord != null && toCoord != null) {
                RackCableRenderer.drawCable(
                    dl = dl,
                    x1 = fromCoord.first,
                    y1 = fromCoord.second,
                    x2 = toCoord.first,
                    y2 = toCoord.second,
                    colorHex = cable.colorHex,
                    isInteractiveDragging = false
                )
            }
        }

        // 2. Draw active dragging elastic cable following mouse cursor
        val draggingFrom = RackRearChassisRenderer.draggingFromPort
        if (draggingFrom != null) {
            val startX = RackRearChassisRenderer.dragStartPos[0]
            val startY = RackRearChassisRenderer.dragStartPos[1]
            val mousePos = ImGui.getIO().mousePos

            val previewColor = 0xFF00E5FF // Cyan preview
            RackCableRenderer.drawCable(
                dl = dl,
                x1 = startX,
                y1 = startY,
                x2 = mousePos.x,
                y2 = mousePos.y,
                colorHex = previewColor,
                isInteractiveDragging = true
            )
        }
    }

}
