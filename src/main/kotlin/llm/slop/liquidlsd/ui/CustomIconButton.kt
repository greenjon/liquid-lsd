package llm.slop.liquidlsd.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.ImColor
import kotlin.math.sin

enum class WaveShape {
    SINE, RAMP_UP, RAMP_DOWN, TRIANGLE, SQUARE, RANDOM,
    SQUARE_10, SQUARE_90
}

object CustomIconButton {
    fun drawWaveformButton(id: String, shape: WaveShape, isSelected: Boolean, themeColor: Int, width: Float, height: Float): Boolean {
        val cursorPos = ImGui.getCursorScreenPos()
        val pMinX = cursorPos.x
        val pMinY = cursorPos.y
        val pMaxX = pMinX + width
        val pMaxY = pMinY + height

        // Button background logic
        ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 1f, 1f, 1f, 0.1f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 1f, 1f, 1f, 0.2f)

        val clicked = ImGui.invisibleButton(id, width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        
        ImGui.popStyleColor(3)
        
        val isHovered = ImGui.isItemHovered()
        val isActive = ImGui.isItemActive()

        val drawList = ImGui.getWindowDrawList()
        
        // Draw background hover/active states manually if we used invisible button
        if (isActive) {
            drawList.addRectFilled(pMinX, pMinY, pMaxX, pMaxY, ImColor.rgba(1f, 1f, 1f, 0.2f), ImGui.getStyle().frameRounding)
        } else if (isHovered) {
            drawList.addRectFilled(pMinX, pMinY, pMaxX, pMaxY, ImColor.rgba(1f, 1f, 1f, 0.1f), ImGui.getStyle().frameRounding)
        }

        // Draw border if selected? Or just color the line? 
        // Let's color the line `themeColor` if selected, otherwise a lighter grey/white
        val lineColor = if (isSelected) themeColor else ImColor.rgba(0.8f, 0.8f, 0.8f, 1.0f)
        val thickness = 2.0f

        val paddingX = width * 0.2f
        val paddingY = height * 0.25f
        val startX = pMinX + paddingX
        val endX = pMaxX - paddingX
        val startY = pMinY + paddingY
        val endY = pMaxY - paddingY
        val midY = (startY + endY) * 0.5f
        val w = endX - startX
        val h = endY - startY

        when (shape) {
            WaveShape.SINE -> {
                val segments = 16
                var prevX = startX
                var prevY = midY
                for (i in 1..segments) {
                    val t = i.toFloat() / segments
                    val x = startX + t * w
                    val y = midY - sin(t * Math.PI.toFloat() * 2f) * (h * 0.5f)
                    drawList.addLine(prevX, prevY, x, y, lineColor, thickness)
                    prevX = x
                    prevY = y
                }
            }
            WaveShape.RAMP_UP -> {
                drawList.addLine(startX, endY, endX, startY, lineColor, thickness)
                drawList.addLine(endX, startY, endX, endY, lineColor, thickness)
            }
            WaveShape.RAMP_DOWN -> {
                drawList.addLine(startX, startY, startX, endY, lineColor, thickness)
                drawList.addLine(startX, startY, endX, endY, lineColor, thickness)
            }
            WaveShape.TRIANGLE -> {
                val midX = startX + w * 0.5f
                drawList.addLine(startX, endY, midX, startY, lineColor, thickness)
                drawList.addLine(midX, startY, endX, endY, lineColor, thickness)
            }
            WaveShape.SQUARE -> {
                drawSquareWave(drawList, startX, endX, startY, endY, 0.5f, lineColor, thickness)
            }
            WaveShape.SQUARE_10 -> {
                drawSquareWave(drawList, startX, endX, startY, endY, 0.1f, lineColor, thickness)
            }
            WaveShape.SQUARE_90 -> {
                drawSquareWave(drawList, startX, endX, startY, endY, 0.9f, lineColor, thickness)
            }
            WaveShape.RANDOM -> {
                // S&H style random stairs
                val steps = 5
                val stepW = w / steps
                val yVals = floatArrayOf(endY, startY + h*0.2f, endY - h*0.1f, startY, endY - h*0.3f, startY + h*0.5f)
                var prevX = startX
                var prevY = yVals[0]
                for (i in 0 until steps) {
                    val nx = startX + (i + 1) * stepW
                    val ny = yVals[i + 1]
                    // Horizontal line
                    drawList.addLine(prevX, prevY, nx, prevY, lineColor, thickness)
                    // Vertical line to next
                    if (i < steps - 1) {
                        drawList.addLine(nx, prevY, nx, ny, lineColor, thickness)
                    }
                    prevX = nx
                    prevY = ny
                }
            }
        }
        
        return clicked
    }

    private fun drawSquareWave(
        drawList: ImDrawList,
        startX: Float,
        endX: Float,
        startY: Float,
        endY: Float,
        duty: Float,
        lineColor: Int,
        thickness: Float
    ) {
        val w = endX - startX
        // For 10% and 90% duty, clamp visual pulse and trough widths to maintain visible clarity
        // without vertical strokes merging on small buttons (~20px wide)
        val pulseW = when {
            duty <= 0.2f -> (w * 0.2f).coerceIn(3.5f, w - 3.5f)
            duty >= 0.8f -> (w * 0.8f).coerceIn(3.5f, w - 3.5f)
            else -> w * duty
        }
        val splitX = startX + pulseW

        // Leading edge: vertical rise from baseline to high
        drawList.addLine(startX, endY, startX, startY, lineColor, thickness)
        // High pulse plateau
        drawList.addLine(startX, startY, splitX, startY, lineColor, thickness)
        // Trailing edge: vertical drop from high to baseline
        drawList.addLine(splitX, startY, splitX, endY, lineColor, thickness)
        // Low baseline plateau
        drawList.addLine(splitX, endY, endX, endY, lineColor, thickness)
    }
}
