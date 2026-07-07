package llm.slop.spirals.ui

import imgui.ImGui
import imgui.type.ImInt
import kotlin.math.roundToInt

object BeatDivisionSlider {
    val subdivisionOptions = floatArrayOf(
        0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f
    )
    val subdivisionLabels = arrayOf(
        "1/8", "1/4", "1/2", "1", "2", "4", "8", "16", "32", "64", "128", "256"
    )

    private var draggingMin = false
    private var draggingMax = false
    private var activeSliderLabel: String? = null
    private var clickMouseX = 0f

    fun drawBeatDivisionSlider(
        label: String,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        formatValue: (Float) -> String,
        onRangeChanged: (Float, Float) -> Unit,
        idPrefix: String = "",
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f)
    ) {
        drawBeatDivisionSlider(
            label = label,
            currentValue = currentMin,
            currentMin = currentMin,
            currentMax = currentMax,
            minLimit = minLimit,
            maxLimit = maxLimit,
            isRandomizable = true,
            showControls = false,
            formatValue = formatValue,
            onRangeChanged = onRangeChanged,
            idPrefix = idPrefix,
            themeColor = themeColor
        )
    }

    fun drawBeatDivisionSlider(
        label: String,
        currentValue: Float,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        isRandomizable: Boolean,
        showControls: Boolean = true,
        formatValue: (Float) -> String,
        onRandomizableChanged: (Boolean) -> Unit = {},
        onRandomizeNow: () -> Unit = {},
        onRangeChanged: (Float, Float) -> Unit = { _, _ -> },
        onValueChanged: (Float) -> Unit = {},
        idPrefix: String = "",
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f)
    ) {
        val rowStartX = ImGui.getCursorScreenPosX()
        val rowStartY = ImGui.getCursorScreenPosY()

        ImGui.pushID(label)

        val w = ImGui.getContentRegionAvailX()
        val h = 44f
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        ImGui.dummy(w, h)

        val dl = ImGui.getWindowDrawList()
        val io = ImGui.getIO()
        val mouseX = io.mousePos.x
        val mouseY = io.mousePos.y

        val buttonSize = ImGui.getFrameHeight()
        val spacing = ImGui.getStyle().itemSpacing.x
        val combinedWidth = buttonSize

        val labelColW = 175f
        val textBoxesStartX = startX + labelColW + 20f

        // Combo dropdowns are slightly wider than plain text boxes so they can display labels
        val comboWidth = 125f
        val comboSpacing = 8f

        val sliderStartX = textBoxesStartX + (if (isRandomizable) (comboWidth * 2f + comboSpacing) else comboWidth) + 15f
        val lineStartX = sliderStartX
        val lineEndX = startX + w - 10f
        val lineWidth = lineEndX - lineStartX
        val centerY = startY + 28f

        val rangeSpan = maxLimit - minLimit

        // --- ROW 1: Labels ---
        if (isRandomizable) {
            ImGui.setCursorScreenPos(textBoxesStartX, startY + 2f)
            UITheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Min")

            ImGui.setCursorScreenPos(textBoxesStartX + comboWidth + comboSpacing, startY + 2f)
            UITheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Max")

            val curPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
            val curX = lineStartX + curPct * lineWidth
            val formattedVal = formatValue(currentValue)
            val labelText = "Current: $formattedVal"
            val currentTextWidth = ImGui.calcTextSize(labelText).x
            val minAllowedX = lineStartX
            val maxAllowedX = (lineEndX - currentTextWidth).coerceAtLeast(minAllowedX)
            val textX = (curX - currentTextWidth / 2f).coerceIn(minAllowedX, maxAllowedX)

            ImGui.setCursorScreenPos(textX, startY + 2f)
            UITheme.captionColored(0.8f, 0.8f, 0.8f, 0.9f, labelText)
        } else {
            ImGui.setCursorScreenPos(textBoxesStartX, startY + 2f)
            UITheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Current")
        }

        // --- ROW 2: Widgets ---
        val row2Y = startY + 18f

        // Render name of variable beside the die, to its left, sharing vertical center
        val textHeight = UITheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() }
        val textY = row2Y + (buttonSize - textHeight) / 2f
        ImGui.setCursorScreenPos(startX, textY)
        UITheme.body(label)

        if (showControls) {
            val randBtnX = startX + labelColW - buttonSize
            ImGui.setCursorScreenPos(randBtnX, row2Y)
            
            if (UITheme.iconButton("##rand_$label", Icons.DICES, "Left-click to toggle random range.\nRight-click to randomize now.", active = isRandomizable, size = buttonSize)) {
                onRandomizableChanged(!isRandomizable)
            }
            
            if (ImGui.isItemClicked(1)) { // Right click
                if (!isRandomizable) {
                    onRandomizableChanged(true)
                }
                onRandomizeNow()
            }
        }

        // --- Combo dropdowns instead of text inputs ---
        if (isRandomizable) {
            // Min combo
            val minIdx = ImInt(currentMin.toInt().coerceIn(0, subdivisionLabels.size - 1))
            ImGui.setCursorScreenPos(textBoxesStartX, row2Y)
            ImGui.pushItemWidth(comboWidth)
            if (ImGui.combo("##bd_min_$label", minIdx, subdivisionLabels)) {
                val nextMin = minIdx.get().toFloat()
                onRangeChanged(nextMin, maxOf(nextMin, currentMax))
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Minimum modulation speed subdivision dropdown")
            }
            ImGui.popItemWidth()

            // Max combo
            val maxIdx = ImInt(currentMax.toInt().coerceIn(0, subdivisionLabels.size - 1))
            ImGui.setCursorScreenPos(textBoxesStartX + comboWidth + comboSpacing, row2Y)
            ImGui.pushItemWidth(comboWidth)
            if (ImGui.combo("##bd_max_$label", maxIdx, subdivisionLabels)) {
                val nextMax = maxIdx.get().toFloat()
                onRangeChanged(minOf(nextMax, currentMin), nextMax)
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Maximum modulation speed subdivision dropdown")
            }
            ImGui.popItemWidth()
        } else {
            val valIdx = ImInt(currentValue.toInt().coerceIn(0, subdivisionLabels.size - 1))
            ImGui.setCursorScreenPos(textBoxesStartX, row2Y)
            ImGui.pushItemWidth(comboWidth)
            if (ImGui.combo("##bd_val_$label", valIdx, subdivisionLabels)) {
                onValueChanged(valIdx.get().toFloat())
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Base speed subdivision dropdown")
            }
            ImGui.popItemWidth()
        }

        // --- Dragging & Slider Render ---
        val mousePressed = ImGui.isMouseClicked(0)
        val mouseDown = ImGui.isMouseDown(0)

        if (isRandomizable) {
            val minPct = if (rangeSpan > 0f) (currentMin - minLimit) / rangeSpan else 0f
            val maxPct = if (rangeSpan > 0f) (currentMax - minLimit) / rangeSpan else 0f
            val minHandleX = lineStartX + minPct * lineWidth
            val maxHandleX = lineStartX + maxPct * lineWidth

            if (mousePressed) {
                val inRowY = mouseY >= row2Y && mouseY <= row2Y + buttonSize
                val inRowX = mouseX >= lineStartX - 10f && mouseX <= lineEndX + 10f
                if (inRowY && inRowX) {
                    activeSliderLabel = idPrefix + label
                    val isOverlapping = kotlin.math.abs(minHandleX - maxHandleX) < 4f
                    if (isOverlapping) {
                        if (mouseX < minHandleX - 5f) {
                            draggingMin = true; draggingMax = false
                        } else if (mouseX > maxHandleX + 5f) {
                            draggingMax = true; draggingMin = false
                        } else {
                            draggingMin = false; draggingMax = false
                            clickMouseX = mouseX
                        }
                    } else {
                        val distToMin = kotlin.math.abs(mouseX - minHandleX)
                        val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                        if (distToMin < distToMax) {
                            draggingMin = true; draggingMax = false
                        } else {
                            draggingMax = true; draggingMin = false
                        }
                    }
                }
            }

            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = (minLimit + pct * rangeSpan).let { v ->
                    // Snap to nearest integer index
                    v.roundToInt().coerceIn(minLimit.toInt(), maxLimit.toInt()).toFloat()
                }
                if (!draggingMin && !draggingMax) {
                    val dragThreshold = 2f
                    if (mouseX > clickMouseX + dragThreshold) {
                        draggingMax = true
                        onRangeChanged(currentMin, rawVal.coerceIn(currentMin, maxLimit))
                    } else if (mouseX < clickMouseX - dragThreshold) {
                        draggingMin = true
                        onRangeChanged(rawVal.coerceIn(minLimit, currentMax), currentMax)
                    }
                } else if (draggingMin) {
                    onRangeChanged(rawVal.coerceIn(minLimit, currentMax), currentMax)
                } else if (draggingMax) {
                    onRangeChanged(currentMin, rawVal.coerceIn(currentMin, maxLimit))
                }
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false; draggingMax = false; activeSliderLabel = null
            }

            // Track
            val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f)
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(minHandleX, centerY, maxHandleX, centerY, themeColor, 3f)

            val handleW = 6f; val handleH = 16f
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            dl.addRectFilled(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
            dl.addRectFilled(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

            // Current value dot
            val curPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
            val curX = lineStartX + curPct * lineWidth
            val curDotCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 1.0f)
            dl.addCircleFilled(curX, centerY, 4f, curDotCol)
            dl.addCircle(curX, centerY, 4.5f, ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f), 12, 1.0f)
        } else {
            val valPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
            val valHandleX = lineStartX + valPct * lineWidth

            if (mousePressed) {
                val inRowY = mouseY >= row2Y && mouseY <= row2Y + buttonSize
                val inRowX = mouseX >= lineStartX - 10f && mouseX <= lineEndX + 10f
                if (inRowY && inRowX) {
                    activeSliderLabel = idPrefix + label
                    draggingMin = true; draggingMax = false
                }
            }

            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = (minLimit + pct * rangeSpan).roundToInt()
                    .coerceIn(minLimit.toInt(), maxLimit.toInt()).toFloat()
                onValueChanged(rawVal)
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false; draggingMax = false; activeSliderLabel = null
            }

            val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f)
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(lineStartX, centerY, valHandleX, centerY, themeColor, 3f)

            val handleW = 6f; val handleH = 16f
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            dl.addRectFilled(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
        }

        // Hover-zone tooltips for beat division slider track/handles
        if (UITheme.tooltipsEnabled) {
            val inTrackY = mouseY >= centerY - 8f && mouseY <= centerY + 8f
            val inTrackX = mouseX >= lineStartX - 4f && mouseX <= lineEndX + 4f
            if (inTrackY && inTrackX) {
                if (isRandomizable) {
                    val minPct = if (rangeSpan > 0f) (currentMin - minLimit) / rangeSpan else 0f
                    val maxPct = if (rangeSpan > 0f) (currentMax - minLimit) / rangeSpan else 0f
                    val minHandleX = lineStartX + minPct * lineWidth
                    val maxHandleX = lineStartX + maxPct * lineWidth
                    val curPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
                    val curX = lineStartX + curPct * lineWidth

                    val distToMin = kotlin.math.abs(mouseX - minHandleX)
                    val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                    val distToCur = kotlin.math.abs(mouseX - curX)

                    when {
                        distToMin < 8f -> ImGui.setTooltip("Minimum boundary speed for $label: ${formatValue(currentMin)}")
                        distToMax < 8f -> ImGui.setTooltip("Maximum boundary speed for $label: ${formatValue(currentMax)}")
                        distToCur < 6f -> ImGui.setTooltip("Current modulated speed for $label: ${formatValue(currentValue)}")
                        else -> ImGui.setTooltip("Drag handles to set modulation bounds for $label")
                    }
                } else {
                    val valPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
                    val valHandleX = lineStartX + valPct * lineWidth
                    val distToVal = kotlin.math.abs(mouseX - valHandleX)

                    if (distToVal < 8f) {
                        ImGui.setTooltip("Base speed for $label: ${formatValue(currentValue)}")
                    } else {
                        ImGui.setTooltip("Drag to adjust base speed for $label")
                    }
                }
            }
        }

        ImGui.popID()
        ImGui.setCursorScreenPos(rowStartX, startY + h)
    }
}
