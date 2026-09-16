package llm.slop.liquidlsd.rack.ui

import imgui.ImDrawList
import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rack.PatchPort
import llm.slop.liquidlsd.rack.PortDirection
import llm.slop.liquidlsd.rack.RackPatchBay
import llm.slop.liquidlsd.rack.RackUnit
import llm.slop.liquidlsd.ui.UITheme

/**
 * Renders the industrial rear chassis panel for a rack unit (Milestone 6, Phase 8).
 *
 * Visual elements:
 * - Industrial matte dark steel chassis with heat dissipation louvers / vents.
 * - Hardware warning / rating labels and IEC AC power inlet module.
 * - 1/4" phone jack sockets with metallic hex nuts, inner contact barrels, and glowing signal LEDs.
 * - Normalled signal pathway indicators (subtle dotted lines between normalled IN and OUT).
 * - Interactive click-and-drag patching: start drag from jack, snap to valid target, right-click to unplug.
 */
object RackRearChassisRenderer {

    const val JACK_OUTER_RADIUS = 10.0f
    const val JACK_INNER_RADIUS = 5.0f

    // Interactive dragging state
    var draggingFromPort: PatchPort? = null
    var dragStartPos = floatArrayOf(0f, 0f)

    // Screen positions of all active jacks rendered in the current frame (for cable endpoint lookup)
    val registeredJackPositions = mutableMapOf<String, Pair<Float, Float>>()

    fun clearFrameState() {
        registeredJackPositions.clear()
    }

    /**
     * Draws the rear panel of one rack unit.
     */
    fun drawUnitRear(
        session: SessionContext,
        unit: RackUnit,
        patchBay: RackPatchBay,
        width: Float,
        height: Float
    ) {
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()

        // 1. Rear Casing Background (dark textured industrial metal)
        val rearBgCol = ImGui.colorConvertFloat4ToU32(0.09f, 0.10f, 0.11f, 1.0f)
        val rearBorderCol = ImGui.colorConvertFloat4ToU32(0.22f, 0.24f, 0.28f, 1.0f)
        dl.addRectFilled(startX, startY, startX + width, startY + height, rearBgCol, 2.0f)
        dl.addRect(startX, startY, startX + width, startY + height, rearBorderCol, 2.0f, 0, 1.0f)

        // 2. Ventilation Louvers / Heat Dissipation Slots
        drawVentilationSlots(dl, startX + 16f, startY + 10f, 110f, height - 20f)

        // 3. Power Inlet Module & Stencils (Left side)
        drawPowerModule(session, dl, startX + 140f, startY + 12f, height - 24f)

        // 4. Center Warning Stencil & Unit Model Badge
        drawStencils(session, dl, startX + 270f, startY + 12f, unit)

        // 5. Patch Bay Jack Sockets (Right side)
        val ports = unit.getRearPorts()
        val jacksStartX = startX + width - 360f
        drawPatchJacks(session, dl, jacksStartX, startY, height, unit, ports, patchBay)

        // Advance cursor
        ImGui.setCursorScreenPos(startX, startY + height)
        ImGui.dummy(0f, 0f)
    }

    private fun drawVentilationSlots(dl: ImDrawList, x: Float, y: Float, w: Float, h: Float) {
        val slotCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.05f, 1.0f)
        val slotHiCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.20f, 0.22f, 0.6f)
        val slotCount = 6
        val slotSpacing = w / slotCount
        for (i in 0 until slotCount) {
            val sx = x + (i * slotSpacing)
            dl.addRectFilled(sx, y + 4f, sx + 5f, y + h - 4f, slotCol, 2.0f)
            dl.addLine(sx, y + h - 4f, sx + 5f, y + h - 4f, slotHiCol, 1.0f)
        }
    }

    private fun drawPowerModule(session: SessionContext, dl: ImDrawList, x: Float, y: Float, h: Float) {
        val inletW = 54f
        val inletH = minOf(h, 34f)
        val inletBg = ImGui.colorConvertFloat4ToU32(0.05f, 0.05f, 0.06f, 1.0f)
        val inletBorder = ImGui.colorConvertFloat4ToU32(0.30f, 0.32f, 0.36f, 1.0f)

        // IEC 3-pin AC inlet box
        dl.addRectFilled(x, y, x + inletW, y + inletH, inletBg, 3.0f)
        dl.addRect(x, y, x + inletW, y + inletH, inletBorder, 3.0f, 0, 1.0f)

        // 3 brass/copper prongs
        val pinCol = ImGui.colorConvertFloat4ToU32(0.85f, 0.65f, 0.25f, 1.0f)
        val midY = y + (inletH * 0.5f)
        dl.addRectFilled(x + 12f, midY - 6f, x + 16f, midY - 2f, pinCol)
        dl.addRectFilled(x + 38f, midY - 6f, x + 42f, midY - 2f, pinCol)
        dl.addRectFilled(x + 25f, midY + 3f, x + 29f, midY + 7f, pinCol)

        // Voltage rating text
        val textCol = ImGui.colorConvertFloat4ToU32(0.45f, 0.48f, 0.52f, 0.8f)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            dl.addText(x, y + inletH + 2f, textCol, "~100-240V 50/60Hz")
        }
    }

    private fun drawStencils(session: SessionContext, dl: ImDrawList, x: Float, y: Float, unit: RackUnit) {
        val headerCol = ImGui.colorConvertFloat4ToU32(0.70f, 0.75f, 0.80f, 1.0f)
        val subCol = ImGui.colorConvertFloat4ToU32(0.45f, 0.48f, 0.52f, 1.0f)
        val badgeCol = ImGui.colorConvertFloat4ToU32(0.85f, 0.55f, 0.15f, 0.85f)

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            dl.addText(x, y, headerCol, "MODEL: ${unit.label.uppercase()}")
        }
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            dl.addText(x, y + 18f, subCol, "SERIAL: LSD-${unit.id.uppercase()} • CLASS 1 LASER PRODUCT")
            dl.addText(x, y + 32f, badgeCol, "CAUTION: ZERO ALLOCATION CALLBACK BUS")
        }
    }

    private fun drawPatchJacks(
        session: SessionContext,
        dl: ImDrawList,
        startX: Float,
        startY: Float,
        height: Float,
        unit: RackUnit,
        ports: List<PatchPort>,
        patchBay: RackPatchBay
    ) {
        val jackSpacing = 90.0f
        val centerY = startY + (height * 0.5f)

        for (i in ports.indices) {
            val port = ports[i]
            val jackX = startX + (i * jackSpacing)
            val jackY = centerY

            // Register screen coordinate for cable rendering
            registeredJackPositions[port.fullId] = Pair(jackX, jackY)

            // Check if connected
            val connectedCable = patchBay.findCableForPort(port.fullId)
            val isConnected = connectedCable != null
            val isInput = port.direction == PortDirection.INPUT

            // Jack Socket Hex Nut & Barrel
            val nutCol = ImGui.colorConvertFloat4ToU32(0.35f, 0.38f, 0.42f, 1.0f)
            val nutBorderCol = ImGui.colorConvertFloat4ToU32(0.60f, 0.65f, 0.70f, 1.0f)
            val holeCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.05f, 1.0f)

            // Hexagon-style round nut
            dl.addCircleFilled(jackX, jackY, JACK_OUTER_RADIUS, nutCol, 6)
            dl.addCircle(jackX, jackY, JACK_OUTER_RADIUS, nutBorderCol, 6, 1.5f)

            // Inner hole
            dl.addCircleFilled(jackX, jackY, JACK_INNER_RADIUS, holeCol, 16)

            // Signal LED
            val ledCol = when {
                isConnected && connectedCable != null -> connectedCable.colorHex.toInt()
                isInput -> ImGui.colorConvertFloat4ToU32(0.20f, 0.60f, 0.80f, 0.40f) // Dim cyan for inputs
                else -> ImGui.colorConvertFloat4ToU32(0.85f, 0.65f, 0.15f, 0.60f) // Dim amber for outputs
            }
            dl.addCircleFilled(jackX, jackY - JACK_OUTER_RADIUS - 5f, 2.5f, ledCol, 8)

            // Jack Label
            var tw = 0f
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { tw = ImGui.calcTextSize(port.label).x }
            val textCol = if (isConnected) ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 1f) else ImGui.colorConvertFloat4ToU32(0.55f, 0.58f, 0.62f, 1f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                dl.addText(jackX - (tw * 0.5f), jackY + JACK_OUTER_RADIUS + 3f, textCol, port.label)
            }

            // Interactive Hit Box
            val hitRadius = JACK_OUTER_RADIUS + 6.0f
            val mousePos = ImGui.getIO().mousePos
            val isHovered = (mousePos.x - jackX) * (mousePos.x - jackX) + (mousePos.y - jackY) * (mousePos.y - jackY) <= hitRadius * hitRadius

            if (isHovered) {
                // Highlight ring
                val hiRingCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.85f, 0.20f, 0.90f)
                dl.addCircle(jackX, jackY, JACK_OUTER_RADIUS + 3.0f, hiRingCol, 16, 2.0f)

                val dirDesc = if (isInput) "INPUT (Normalled from above or override via cable)" else "OUTPUT (Send to any input)"
                val cableDesc = if (isConnected) "\nConnected (Right-click to unplug)" else "\nDrag cable to connect"
                ImGui.setTooltip("Port: ${port.label} [${port.direction.name}]\n$dirDesc$cableDesc")

                // Right click: Unplug cable
                if (ImGui.isMouseClicked(1) && isConnected && connectedCable != null) {
                    patchBay.disconnect(connectedCable.id)
                }

                // Left click start drag: Plug new cable
                if (ImGui.isMouseClicked(0) && draggingFromPort == null) {
                    draggingFromPort = port
                    dragStartPos[0] = jackX
                    dragStartPos[1] = jackY
                }
            }

            // Drop release: Complete connection
            if (ImGui.isMouseReleased(0) && draggingFromPort != null && isHovered) {
                val sourcePort = draggingFromPort
                if (sourcePort != null && sourcePort.fullId != port.fullId) {
                    if (sourcePort.direction == PortDirection.OUTPUT && port.direction == PortDirection.INPUT) {
                        patchBay.connect(sourcePort, port)
                    } else if (sourcePort.direction == PortDirection.INPUT && port.direction == PortDirection.OUTPUT) {
                        patchBay.connect(port, sourcePort)
                    }
                }
                draggingFromPort = null
            }
        }

        // Cancel dragging if mouse released anywhere else
        if (ImGui.isMouseReleased(0) && draggingFromPort != null) {
            draggingFromPort = null
        }
    }
}
