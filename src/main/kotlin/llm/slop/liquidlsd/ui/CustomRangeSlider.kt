package llm.slop.liquidlsd.ui

import imgui.ImGui
import kotlin.math.roundToInt

object CustomRangeSlider {
    private var draggingMin = false
    private var draggingMax = false
    private var activeSliderLabel: String? = null
    private var clickMouseX = 0f

    var isAnySliderHovered = false

    private val textBuffers = mutableMapOf<String, imgui.type.ImString>()
    private val textWidgetActive = mutableMapOf<String, Boolean>()
    private val textCallbacks = mutableMapOf<String, ReusableInputCallback>()

    private class ReusableInputCallback : imgui.callback.ImGuiInputTextCallback() {
        var currentValue: Float = 0f
        var minLimit: Float = 0f
        var maxLimit: Float = 0f
        var formatValue: (Float) -> String = { "%.3f".format(it) }
        var onChanged: (Float) -> Unit = {}

        override fun accept(data: imgui.ImGuiInputTextCallbackData) {
            val upPressed = data.eventKey == imgui.flag.ImGuiKey.UpArrow
            val downPressed = data.eventKey == imgui.flag.ImGuiKey.DownArrow
            if (upPressed || downPressed) {
                val io = ImGui.getIO()
                val shift = io.keyShift
                val ctrl = io.keyCtrl
                val delta = if (ctrl && shift) {
                    0.1f
                } else if (shift) {
                    0.01f
                } else {
                    0.001f
                }
                val dir = if (upPressed) 1f else -1f
                val nextValue = currentValue + (dir * delta)
                val clampedValue = nextValue.coerceIn(minLimit, maxLimit)
                
                val formatted = formatValue(clampedValue)
                data.deleteChars(0, data.buf.length)
                data.insertChars(0, formatted)
                data.cursorPos = formatted.length
                data.selectionStart = formatted.length
                data.selectionEnd = formatted.length
                data.setBufDirty(true)

                onChanged(clampedValue)
            }
        }
    }

    private fun drawTextInput(
        session: llm.slop.liquidlsd.SessionContext,
        key: String,
        currentValue: Float,
        minLimit: Float,
        maxLimit: Float,
        posX: Float,
        posY: Float,
        width: Float,
        defaultValue: Float? = null,
        onChanged: (Float) -> Unit,
        formatValue: (Float) -> String = { "%.3f".format(it) },
        parseValue: (String) -> Float? = { it.toFloatOrNull() }
    ) {
        val buffer = textBuffers.getOrPut(key) { imgui.type.ImString(formatValue(currentValue), 32) }
        val active = textWidgetActive.getOrDefault(key, false)
        if (!active) {
            buffer.set(formatValue(currentValue))
        }
        ImGui.setCursorScreenPos(posX, posY)
        ImGui.pushItemWidth(width)

        // Native callback to handle arrow keys
        val flags = imgui.flag.ImGuiInputTextFlags.CallbackHistory
        val callback = textCallbacks.getOrPut(key) { ReusableInputCallback() }
        callback.currentValue = currentValue
        callback.minLimit = minLimit
        callback.maxLimit = maxLimit
        callback.formatValue = formatValue
        callback.onChanged = onChanged

        val inputChanged = ImGui.inputText("##input_$key", buffer, flags, callback)
        if (inputChanged) {
            val parsed = parseValue(buffer.get())
            if (parsed != null) {
                val clamped = parsed.coerceIn(minLimit, maxLimit)
                onChanged(clamped)
            }
        }
        val isItemFocused = ImGui.isItemActive()
        val isHovered = ImGui.isItemHovered()
        if (isItemFocused || isHovered) {
            isAnySliderHovered = true
            val dl = ImGui.getWindowDrawList()
            val frameH = ImGui.getFrameHeight()
            val borderCol = if (isItemFocused) {
                ImGui.colorConvertFloat4ToU32(0.0f, 0.85f, 1.0f, 1.0f) // Electric Cyan for active focus/typing/arrows
            } else {
                ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f) // Bright Amber Gold for mouse hover target
            }
            dl.addRect(posX - 1.5f, posY - 1.5f, posX + width + 1.5f, posY + frameH + 1.5f, borderCol, 3f, 0, 2.0f)

            val io = ImGui.getIO()
            if (io.mouseWheel != 0f) {
                val shift = io.keyShift
                val ctrl = io.keyCtrl
                val deltaStep = if (ctrl && shift) 0.1f else if (shift) 0.01f else 0.001f
                val nextVal = (currentValue + io.mouseWheel * deltaStep).coerceIn(minLimit, maxLimit)
                onChanged(nextVal)
                io.mouseWheel = 0f
            }
            if (ImGui.isMouseClicked(2)) { // Middle click reset
                val resetTarget = defaultValue ?: 0.0f.coerceIn(minLimit, maxLimit)
                onChanged(resetTarget)
            }
            if (session.uiTheme.tooltipsEnabled) {
                val fieldType = when {
                    key.endsWith("_min") -> "Minimum modulation boundary. Type, Up/Down, or Scroll to adjust. Middle-click to reset."
                    key.endsWith("_max") -> "Maximum modulation boundary. Type, Up/Down, or Scroll to adjust. Middle-click to reset."
                    key.endsWith("_value") -> "Base value. Type, Up/Down, or Scroll to adjust. Middle-click to reset."
                    else -> "Type a precise numeric value. Up/Down or Scroll to adjust. Middle-click to reset."
                }
                ImGui.setTooltip(fieldType)
            }
        }
        textWidgetActive[key] = isItemFocused
        ImGui.popItemWidth()
    }

    fun drawCompactSlider(
        session: llm.slop.liquidlsd.SessionContext,
        label: String,
        currentValue: Float,
        minLimit: Float,
        maxLimit: Float,
        defaultValue: Float? = null,
        formatValue: (Float) -> String = { "%.2f".format(it) },
        idPrefix: String = "",
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.9f, 0.9f),
        isLogarithmic: Boolean = false,
        parseValue: (String) -> Float? = { it.toFloatOrNull() },
        showCurrentLabel: Boolean = true,
        customBoxWidth: Float? = null,
        onValueChanged: (Float) -> Unit
    ) {
        drawCustomRangeSlider(
            session = session,
            label = label,
            currentValue = currentValue,
            currentMin = currentValue,
            currentMax = currentValue,
            minLimit = minLimit,
            maxLimit = maxLimit,
            isRandomizable = false,
            showControls = false,
            defaultValue = defaultValue,
            formatValue = formatValue,
            onValueChanged = onValueChanged,
            idPrefix = idPrefix,
            themeColor = themeColor,
            isLogarithmic = isLogarithmic,
            parseValue = parseValue,
            showCurrentLabel = showCurrentLabel,
            customBoxWidth = customBoxWidth
        )
    }

    fun drawCustomRangeSlider(
        session: llm.slop.liquidlsd.SessionContext,
        label: String,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        formatValue: (Float) -> String,
        formatLabel: ((Float) -> String)? = null,
        onRangeChanged: (Float, Float) -> Unit,
        idPrefix: String = "",
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f),
        isLogarithmic: Boolean = false,
        parseValue: (String) -> Float? = { it.toFloatOrNull() },
        showCurrentLabel: Boolean = true,
        customBoxWidth: Float? = null
    ) {
        drawCustomRangeSlider(
            session = session,
            label = label,
            currentValue = currentMin,
            currentMin = currentMin,
            currentMax = currentMax,
            minLimit = minLimit,
            maxLimit = maxLimit,
            isRandomizable = true,
            showControls = false,
            formatValue = formatValue,
            formatLabel = formatLabel,
            onRangeChanged = onRangeChanged,
            idPrefix = idPrefix,
            themeColor = themeColor,
            isLogarithmic = isLogarithmic,
            parseValue = parseValue,
            showCurrentLabel = showCurrentLabel,
            customBoxWidth = customBoxWidth
        )
    }

    fun drawCustomRangeSlider(
        session: llm.slop.liquidlsd.SessionContext,
        label: String,
        currentValue: Float,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        isRandomizable: Boolean,
        showControls: Boolean = true,
        defaultValue: Float? = null,
        formatValue: (Float) -> String,
        formatLabel: ((Float) -> String)? = null,
        onRandomizableChanged: (Boolean) -> Unit = {},
        onRandomizeNow: () -> Unit = {},
        onRangeChanged: (Float, Float) -> Unit = { _, _ -> },
        onValueChanged: (Float) -> Unit = {},
        idPrefix: String = "",
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f),
        isLogarithmic: Boolean = false,
        parseValue: (String) -> Float? = { it.toFloatOrNull() },
        showCurrentLabel: Boolean = true,
        customBoxWidth: Float? = null
    ) {
        val effectiveIsRandomizable = (isRandomizable && session.uiTheme.randomizationEnabled) || (!showControls && isRandomizable)
        val effectiveShowControls = showControls && session.uiTheme.randomizationEnabled

        val rowStartX = ImGui.getCursorScreenPosX()
        val rowStartY = ImGui.getCursorScreenPosY()

        ImGui.pushID(label)

        val w = ImGui.getContentRegionAvailX()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        val buttonSize = ImGui.getFrameHeight()
        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
        val captionHeight = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        
        val labelY = startY + 2f * fontScale
        val row2Y = labelY + captionHeight + 2.5f * fontScale
        val centerY = row2Y + buttonSize * 0.5f
        val h = (row2Y - startY) + buttonSize + 3f * fontScale
        
        // Reserve space
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        val io = ImGui.getIO()
        val mouseX = io.mousePos.x
        val mouseY = io.mousePos.y
        
        val spacing = ImGui.getStyle().itemSpacing.x
        val combinedWidth = buttonSize
        
        val labelColW = 110f * fontScale
        val textBoxesStartX = startX + labelColW + 10f * fontScale
        
        val boxWidth = customBoxWidth ?: (65f * fontScale)
        val boxSpacing = 8f
        
        val sliderStartX = textBoxesStartX + (if (effectiveIsRandomizable) (boxWidth * 2f + boxSpacing) else boxWidth) + 15f
        val lineStartX = sliderStartX
        val lineEndX = maxOf(lineStartX + 1f, startX + w - 10f)
        val lineWidth = (lineEndX - lineStartX).coerceAtLeast(1f)
        
        val rangeSpan = maxLimit - minLimit
        val labelFormatFunc = formatLabel ?: formatValue

        val toPct: (Float) -> Float = { v ->
            if (isLogarithmic) {
                val logMin = java.lang.Math.log10(minLimit.toDouble())
                val logMax = java.lang.Math.log10(maxLimit.toDouble())
                val logVal = java.lang.Math.log10(v.toDouble().coerceAtLeast(minLimit.toDouble()))
                ((logVal - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)
            } else {
                if (rangeSpan > 0f) (v - minLimit) / rangeSpan else 0f
            }
        }

        val toVal: (Float) -> Float = { p ->
            if (isLogarithmic) {
                val logMin = java.lang.Math.log10(minLimit.toDouble())
                val logMax = java.lang.Math.log10(maxLimit.toDouble())
                val logVal = logMin + p.toDouble() * (logMax - logMin)
                java.lang.Math.pow(10.0, logVal).toFloat().coerceIn(minLimit, maxLimit)
            } else {
                minLimit + p * rangeSpan
            }
        }

        // --- ROW 1: Labels ---
        if (effectiveIsRandomizable) {
            ImGui.setCursorScreenPos(textBoxesStartX, labelY)
            session.uiTheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Min")
            
            ImGui.setCursorScreenPos(textBoxesStartX + boxWidth + boxSpacing, labelY)
            session.uiTheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Max")
            
            if (showCurrentLabel) {
                // Add "Current" label with [value] centered above the dynamic dot on Row 1
                val curPct = toPct(currentValue)
                val curX = lineStartX + curPct * lineWidth
                val formattedVal = labelFormatFunc(currentValue)
                val labelText = "Current: $formattedVal"
                val currentTextWidth = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.calcTextSize(labelText).x }
                val minAllowedX = lineStartX
                val maxAllowedX = (lineEndX - currentTextWidth).coerceAtLeast(minAllowedX)
                val textX = (curX - currentTextWidth / 2f).coerceIn(minAllowedX, maxAllowedX)
                
                ImGui.setCursorScreenPos(textX, labelY)
                session.uiTheme.captionColored(0.8f, 0.8f, 0.8f, 0.9f, labelText)
            }
        } else {
            if (showCurrentLabel) {
                ImGui.setCursorScreenPos(textBoxesStartX, labelY)
                val formattedVal = labelFormatFunc(currentValue)
                session.uiTheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Current: $formattedVal")
            }
        }
        
        // --- ROW 2: Widgets ---
        
        // Render name of variable beside the die, to its left, sharing vertical center
        val textHeight = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() }
        val textY = row2Y + (buttonSize - textHeight) / 2f
        ImGui.setCursorScreenPos(startX, textY)
        session.uiTheme.body(label)
        
        if (effectiveShowControls) {
            val randBtnX = startX + labelColW - buttonSize
            ImGui.setCursorScreenPos(randBtnX, row2Y)
            
            if (!effectiveIsRandomizable) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1f, 1f, 1f, 0.4f)
            }
            if (ImGui.button("${Icons.DICES}##rand_$label", buttonSize, buttonSize)) {
                onRandomizableChanged(!effectiveIsRandomizable)
            }
            if (!effectiveIsRandomizable) {
                ImGui.popStyleColor()
            }
            
            if (ImGui.isItemClicked(1)) { // Right click
                if (!effectiveIsRandomizable) {
                    onRandomizableChanged(true)
                }
                onRandomizeNow()
            }
            val hovered = ImGui.isItemHovered()
            if (hovered && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Left-click to toggle random range.\nRight-click to randomize now.")
            }
        }
        
        // 3. Text inputs
        if (effectiveIsRandomizable) {
            drawTextInput(
                session = session,
                key = "${idPrefix}_${label}_min",
                currentValue = currentMin,
                minLimit = minLimit,
                maxLimit = maxLimit,
                posX = textBoxesStartX,
                posY = row2Y,
                width = boxWidth,
                defaultValue = defaultValue,
                onChanged = { nextMin ->
                    onRangeChanged(nextMin, maxOf(nextMin, currentMax))
                },
                formatValue = formatValue,
                parseValue = parseValue
            )
            drawTextInput(
                session = session,
                key = "${idPrefix}_${label}_max",
                currentValue = currentMax,
                minLimit = minLimit,
                maxLimit = maxLimit,
                posX = textBoxesStartX + boxWidth + boxSpacing,
                posY = row2Y,
                width = boxWidth,
                defaultValue = defaultValue,
                onChanged = { nextMax ->
                    onRangeChanged(minOf(nextMax, currentMin), nextMax)
                },
                formatValue = formatValue,
                parseValue = parseValue
            )
        } else {
            drawTextInput(
                session = session,
                key = "${idPrefix}_${label}_value",
                currentValue = currentValue,
                minLimit = minLimit,
                maxLimit = maxLimit,
                posX = textBoxesStartX,
                posY = row2Y,
                width = boxWidth,
                defaultValue = defaultValue,
                onChanged = { newVal ->
                    onValueChanged(newVal)
                },
                formatValue = formatValue,
                parseValue = parseValue
            )
        }
        
        // --- Dragging & Slider Render ---
        val mousePressed = ImGui.isMouseClicked(0)
        val mouseDown = ImGui.isMouseDown(0)
        
        if (effectiveIsRandomizable) {
            val minPct = toPct(currentMin)
            val maxPct = toPct(currentMax)
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
                            draggingMin = true
                            draggingMax = false
                        } else if (mouseX > maxHandleX + 5f) {
                            draggingMax = true
                            draggingMin = false
                        } else {
                            draggingMin = false
                            draggingMax = false
                            clickMouseX = mouseX
                        }
                    } else {
                        val distToMin = kotlin.math.abs(mouseX - minHandleX)
                        val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                        if (distToMin < distToMax) {
                            draggingMin = true
                            draggingMax = false
                        } else {
                            draggingMax = true
                            draggingMin = false
                        }
                    }
                }
            }
            
            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = toVal(pct)
                if (!draggingMin && !draggingMax) {
                    val dragThreshold = 2f
                    if (mouseX > clickMouseX + dragThreshold) {
                        draggingMax = true
                        val nextMax = rawVal.coerceIn(currentMin, maxLimit)
                        onRangeChanged(currentMin, nextMax)
                    } else if (mouseX < clickMouseX - dragThreshold) {
                        draggingMin = true
                        val nextMin = rawVal.coerceIn(minLimit, currentMax)
                        onRangeChanged(nextMin, currentMax)
                    }
                } else if (draggingMin) {
                    val nextMin = rawVal.coerceIn(minLimit, currentMax)
                    onRangeChanged(nextMin, currentMax)
                } else if (draggingMax) {
                    val nextMax = rawVal.coerceIn(currentMin, maxLimit)
                    onRangeChanged(currentMin, nextMax)
                }
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false
                draggingMax = false
                activeSliderLabel = null
            }
            
            // Draw tracks
            val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f) // Darker inactive track
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(minHandleX, centerY, maxHandleX, centerY, themeColor, 3f) // Active track is theme color
            
            // Draw handles
            val handleW = 6f
            val handleH = 16f
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            
            dl.addRectFilled(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
            dl.addRectFilled(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

            // Draw dynamic current value indicator (Amber Gold dot)
            val curPct = toPct(currentValue)
            val curX = lineStartX + curPct * lineWidth
            val dotY = centerY
            val dotR = 4f
            val curDotCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 1.0f) // Bright Amber Gold
            dl.addCircleFilled(curX, dotY, dotR, curDotCol)
            dl.addCircle(curX, dotY, dotR + 0.5f, ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f), 12, 1.0f) // Dark border
        } else {
            val valPct = toPct(currentValue)
            val valHandleX = lineStartX + valPct * lineWidth
            
            if (mousePressed) {
                val inRowY = mouseY >= row2Y && mouseY <= row2Y + buttonSize
                val inRowX = mouseX >= lineStartX - 10f && mouseX <= lineEndX + 10f
                if (inRowY && inRowX) {
                    activeSliderLabel = idPrefix + label
                    draggingMin = true
                    draggingMax = false
                }
            }
            
            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = toVal(pct)
                onValueChanged(rawVal)
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false
                draggingMax = false
                activeSliderLabel = null
            }
            
            // Draw tracks
            val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f) // Darker inactive track
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(lineStartX, centerY, valHandleX, centerY, themeColor, 3f) // Active track is theme color
            
            // Draw single handle
            val handleW = 6f
            val handleH = 16f
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            
            dl.addRectFilled(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
        }
        
        // Hover-zone handling & tooltips for custom range slider track/handles
        val inTrackY = mouseY >= centerY - 8f && mouseY <= centerY + 8f
        val inTrackX = mouseX >= lineStartX - 4f && mouseX <= lineEndX + 4f
        val isTrackActive = activeSliderLabel == (idPrefix + label)
        if ((inTrackY && inTrackX) || isTrackActive) {
            isAnySliderHovered = true
            val borderCol = if (isTrackActive) {
                ImGui.colorConvertFloat4ToU32(0.0f, 0.85f, 1.0f, 1.0f) // Electric Cyan while active dragging
            } else {
                ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f) // Amber Gold on hover target
            }
            dl.addRect(lineStartX - 3f, centerY - 9f, lineEndX + 3f, centerY + 9f, borderCol, 4f, 0, 1.5f)
            
            val io = ImGui.getIO()
            if (io.mouseWheel != 0f) {
                val shift = io.keyShift
                val ctrl = io.keyCtrl
                val deltaStep = if (ctrl && shift) 0.1f else if (shift) 0.01f else 0.001f
                val delta = io.mouseWheel * deltaStep
                if (effectiveIsRandomizable) {
                    val minPct = toPct(currentMin)
                    val maxPct = toPct(currentMax)
                    val minHandleX = lineStartX + minPct * lineWidth
                    val maxHandleX = lineStartX + maxPct * lineWidth
                    val distToMin = kotlin.math.abs(mouseX - minHandleX)
                    val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                    if (distToMin < distToMax) {
                        val nextMin = (currentMin + delta).coerceIn(minLimit, currentMax)
                        onRangeChanged(nextMin, currentMax)
                    } else {
                        val nextMax = (currentMax + delta).coerceIn(currentMin, maxLimit)
                        onRangeChanged(currentMin, nextMax)
                    }
                } else {
                    val nextVal = (currentValue + delta).coerceIn(minLimit, maxLimit)
                    onValueChanged(nextVal)
                }
                io.mouseWheel = 0f // Consume mouse wheel event so parent panel does not scroll
            }
            if (ImGui.isMouseClicked(2)) { // Middle click reset
                val resetTarget = defaultValue ?: 0.0f.coerceIn(minLimit, maxLimit)
                if (effectiveIsRandomizable) {
                    onRangeChanged(resetTarget, resetTarget)
                } else {
                    onValueChanged(resetTarget)
                }
            }
            if (session.uiTheme.tooltipsEnabled) {
                if (effectiveIsRandomizable) {
                    val minPct = toPct(currentMin)
                    val maxPct = toPct(currentMax)
                    val minHandleX = lineStartX + minPct * lineWidth
                    val maxHandleX = lineStartX + maxPct * lineWidth
                    val curPct = toPct(currentValue)
                    val curX = lineStartX + curPct * lineWidth

                    val distToMin = kotlin.math.abs(mouseX - minHandleX)
                    val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                    val distToCur = kotlin.math.abs(mouseX - curX)

                    when {
                        distToMin < 8f -> ImGui.setTooltip("Minimum boundary for $label: ${labelFormatFunc(currentMin)}\nScroll to adjust. Middle-click track to reset.")
                        distToMax < 8f -> ImGui.setTooltip("Maximum boundary for $label: ${labelFormatFunc(currentMax)}\nScroll to adjust. Middle-click track to reset.")
                        distToCur < 6f -> ImGui.setTooltip("Current modulated value for $label: ${labelFormatFunc(currentValue)}")
                        else -> ImGui.setTooltip("Drag handles or Scroll to set bounds for $label. Middle-click to reset.")
                    }
                } else {
                    val valPct = toPct(currentValue)
                    val valHandleX = lineStartX + valPct * lineWidth
                    val distToVal = kotlin.math.abs(mouseX - valHandleX)

                    if (distToVal < 8f) {
                        ImGui.setTooltip("Base value for $label: ${labelFormatFunc(currentValue)}\nScroll to adjust. Middle-click to reset.")
                    } else {
                        ImGui.setTooltip("Drag or Scroll to adjust base value for $label. Middle-click to reset.")
                    }
                }
            }
        }

        ImGui.popID()
        ImGui.setCursorScreenPos(rowStartX, startY + h)
    }
}
