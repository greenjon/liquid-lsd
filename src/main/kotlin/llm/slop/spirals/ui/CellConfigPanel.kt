package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiColorEditFlags
import imgui.type.ImInt
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.cv.CvHistoryBuffer
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.LfoSpeedMode
import llm.slop.spirals.parameters.ModulationOperator
import llm.slop.spirals.parameters.Waveform

/**
 * Draws the Cell Config panel contents.
 * Call this inside an ImGui.begin("Cell Config") / ImGui.end() block.
 */
object CellConfigPanel {

    private val subdivisionOptions = floatArrayOf(
        0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f
    )
    private val subdivisionLabels = arrayOf(
        "1/8", "1/4", "1/2", "1", "2", "4", "8", "16", "32", "64", "128", "256"
    )
    private val waveformLabels = arrayOf("Sine", "Triangle", "Square")
    private val speedLabels = arrayOf("Slow", "Medium", "Fast")
    private val operatorLabels = arrayOf("ADD", "MUL")

    private var activeHistory: CvHistoryBuffer? = null
    private var activeCellId: PatchCellId? = null
    private var draggingMin = false
    private var draggingMax = false
    private var activeSliderLabel: String? = null

    fun draw(state: PatchGridState) {
        val cell = state.selectedCell
        val param = state.selectedParam

        if (cell == null || param == null) {
            activeHistory = null
            activeCellId = null
            UITheme.caption("Click a cell in the Patch Grid to configure it.")
            return
        }

        val cvId = cell.cvSourceId
        val paramKey = cell.paramKey
        val isBeat = cvId == "beatPhase"
        val isLfo = cvId == "lfo"
        val isSnh = cvId == "sampleAndHold"
        val hasAdvanced = isBeat || isLfo || isSnh

        if (cvId == "base") {
            UITheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey)
            ImGui.sameLine()
            UITheme.caption("  <--  BASE RANGE")
            ImGui.separator()
            ImGui.spacing()

            drawCustomRangeSlider(
                label = "Base Range",
                currentMin = param.baseMin,
                currentMax = param.baseMax,
                minLimit = 0f,
                maxLimit = 1f,
                formatValue = { "%.3f".format(it) },
                onRangeChanged = { nextMin, nextMax ->
                    param.baseMin = nextMin
                    param.baseMax = nextMax
                    if (nextMin == nextMax) {
                        param.baseValue = nextMin
                    } else {
                        param.baseValue = param.baseValue.coerceIn(nextMin, nextMax)
                    }
                }
            )

            ImGui.spacing()
            if (ImGui.button("🎲 Randomize Base Value", ImGui.getContentRegionAvailX(), 30f)) {
                param.randomizeBaseValue()
            }

            ImGui.spacing()
            UITheme.caption("Static Base Value: %.3f".format(param.baseValue))
            val barW = ImGui.getContentRegionAvailX()
            val dl = ImGui.getWindowDrawList()
            val cx = ImGui.getCursorScreenPosX()
            val cy = ImGui.getCursorScreenPosY()
            dl.addRectFilled(cx, cy, cx + barW, cy + 10f, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
            dl.addRectFilled(cx, cy, cx + barW * param.baseValue, cy + 10f,
                ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f))
            ImGui.dummy(barW, 10f)
            return
        }

        if (cvId == "final") {
            UITheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey)
            ImGui.sameLine()
            UITheme.caption("  <--  FINAL VALUE")
            ImGui.separator()
            ImGui.spacing()

            // Oscilloscope showing final value history
            drawFinalOscilloscope(param.history)

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            val liveVal = param.value
            UITheme.caption("Live Value: %.3f".format(liveVal))
            val barW = ImGui.getContentRegionAvailX()
            val dl = ImGui.getWindowDrawList()
            val cx = ImGui.getCursorScreenPosX()
            val cy = ImGui.getCursorScreenPosY()
            dl.addRectFilled(cx, cy, cx + barW, cy + 10f, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
            dl.addRectFilled(cx, cy, cx + barW * liveVal.coerceIn(0f, 1f), cy + 10f,
                ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 1f))
            ImGui.dummy(barW, 10f)
            return
        }

        UITheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey)
        ImGui.sameLine()
        UITheme.caption("  <--  $cvId")
        ImGui.separator()
        ImGui.spacing()

        val existing = state.editingModulator

        if (existing == null) {
            activeHistory = null
            activeCellId = null
            // Empty cell: offer to create
            UITheme.caption("No patch at this intersection.")
            ImGui.spacing()
            if (ImGui.button("+ Add Patch", ImGui.getContentRegionAvailX(), 35f)) {
                val newMod = CvModulator(sourceId = cvId)
                param.modulators.add(newMod)
                state.editingModulator = newMod
            }
            return
        }

        // Initialize or update oscilloscope history
        if (activeCellId != cell || activeHistory == null) {
            activeHistory = CvHistoryBuffer(200)
            activeCellId = cell
        }
        activeHistory?.add(existing.evaluateValue())

        // ── Bypass / Delete buttons ──────────────────────────────
        val bypassed = existing.bypassed
        val bypassLabel = if (bypassed) "BYPASSED" else "ACTIVE"
        if (bypassed) ImGui.pushStyleColor(0, 0.5f, 0.5f, 0.5f, 1f)
        else ImGui.pushStyleColor(0, 0.2f, 0.8f, 0.4f, 1f)
        if (ImGui.button(bypassLabel, 125f, 30f)) {
            replaceModulator(state, param, existing.copy(bypassed = !bypassed))
        }
        ImGui.popStyleColor()

        ImGui.sameLine()
        ImGui.pushStyleColor(0, 0.8f, 0.2f, 0.2f, 1f)
        if (ImGui.button("Delete Patch", 150f, 30f)) {
            param.modulators.remove(existing)
            state.editingModulator = null
        }
        ImGui.popStyleColor()

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ── Oscilloscope ─────────────────────────────────────────
        drawOscilloscope(existing)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ── Operator (ADD / MUL) ─────────────────────────────────
        UITheme.body("Operator")
        val opIdx = ImInt(if (existing.operator == ModulationOperator.ADD) 0 else 1)
        ImGui.pushItemWidth(150f)
        if (ImGui.combo("##op", opIdx, operatorLabels)) {
            val newOp = if (opIdx.get() == 0) ModulationOperator.ADD else ModulationOperator.MUL
            replaceModulator(state, param, existing.copy(operator = newOp))
        }
        ImGui.popItemWidth()
        ImGui.spacing()

        // ── Weight ───────────────────────────────────────────────
        drawCustomRangeSlider(
            label = "Weight",
            currentMin = existing.weightMin,
            currentMax = existing.weightMax,
            minLimit = -1f,
            maxLimit = 1f,
            formatValue = { "%.3f".format(it) },
            onRangeChanged = { nextMin, nextMax ->
                val nextActive = existing.weight.coerceIn(nextMin, nextMax)
                replaceModulator(state, param, existing.copy(
                    weightMin = nextMin,
                    weightMax = nextMax,
                    weight = nextActive
                ))
            }
        )
        ImGui.spacing()

        if (!hasAdvanced) {
            // ── Test Randomize Button ────────────────────────────────
            ImGui.spacing()
            if (ImGui.button("🎲 Test Randomize", ImGui.getContentRegionAvailX(), 30f)) {
                val randomized = existing.randomizeActiveValues()
                replaceModulator(state, param, randomized)
            }
            return
        }

        // ── Waveform (Beat / LFO only) ───────────────────────────
        if (!isSnh) {
            UITheme.body("Waveform")
            val wfIdx = ImInt(existing.waveform.ordinal)
            ImGui.pushItemWidth(150f)
            if (ImGui.combo("##waveform", wfIdx, waveformLabels)) {
                replaceModulator(state, param, existing.copy(waveform = Waveform.entries[wfIdx.get()]))
            }
            ImGui.popItemWidth()
            ImGui.spacing()
        }

        // ── Subdivision (Beat / S&H) ─────────────────────────────
        if (isBeat || isSnh) {
            val currentMinIdx = subdivisionOptions.indexOfFirst { it == existing.subdivisionMin }.coerceAtLeast(0)
            val currentMaxIdx = subdivisionOptions.indexOfFirst { it == existing.subdivisionMax }.coerceAtLeast(0)
            
            drawCustomRangeSlider(
                label = "Beat Division",
                currentMin = currentMinIdx.toFloat(),
                currentMax = currentMaxIdx.toFloat(),
                minLimit = 0f,
                maxLimit = (subdivisionOptions.size - 1).toFloat(),
                formatValue = { idx -> subdivisionLabels[idx.toInt().coerceIn(0, subdivisionOptions.size - 1)] },
                onRangeChanged = { nextMinIdx, nextMaxIdx ->
                    val nextMinVal = subdivisionOptions[nextMinIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                    val nextMaxVal = subdivisionOptions[nextMaxIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                    val nextActive = existing.subdivision.coerceIn(nextMinVal, nextMaxVal)
                    replaceModulator(state, param, existing.copy(
                        subdivisionMin = nextMinVal,
                        subdivisionMax = nextMaxVal,
                        subdivision = nextActive
                    ))
                }
            )
            ImGui.spacing()
        }

        // ── LFO Period / Speed ───────────────────────────────────
        if (isLfo) {
            UITheme.body("Speed Range")
            val speedIdx = ImInt(existing.lfoSpeedMode.ordinal)
            ImGui.pushItemWidth(125f)
            if (ImGui.combo("##speed", speedIdx, speedLabels)) {
                replaceModulator(state, param, existing.copy(lfoSpeedMode = LfoSpeedMode.entries[speedIdx.get()]))
            }
            ImGui.popItemWidth()
            ImGui.spacing()

            // Format labels based on speed mode
            val formatFunc: (Float) -> String = { v ->
                when (existing.lfoSpeedMode) {
                    LfoSpeedMode.FAST -> "%.2fs".format(v * 10.0)
                    LfoSpeedMode.MEDIUM -> {
                        val s = (v * 900).toInt()
                        "%02dm:%02ds".format(s / 60, s % 60)
                    }
                    LfoSpeedMode.SLOW -> {
                        val m = (v * 1440).toInt()
                        "%02dh:%02dm".format(m / 60, m % 60)
                    }
                }
            }

            drawCustomRangeSlider(
                label = "LFO Period",
                currentMin = existing.subdivisionMin,
                currentMax = existing.subdivisionMax,
                minLimit = 0.001f,
                maxLimit = 1f,
                formatValue = formatFunc,
                onRangeChanged = { nextMin, nextMax ->
                    val nextActive = existing.subdivision.coerceIn(nextMin, nextMax)
                    replaceModulator(state, param, existing.copy(
                        subdivisionMin = nextMin,
                        subdivisionMax = nextMax,
                        subdivision = nextActive
                    ))
                }
            )
            ImGui.spacing()
        }

        // ── Phase Offset ─────────────────────────────────────────
        drawCustomRangeSlider(
            label = "Phase Offset",
            currentMin = existing.phaseOffsetMin,
            currentMax = existing.phaseOffsetMax,
            minLimit = 0f,
            maxLimit = 1f,
            formatValue = { "%.3f".format(it) },
            onRangeChanged = { nextMin, nextMax ->
                val nextActive = existing.phaseOffset.coerceIn(nextMin, nextMax)
                replaceModulator(state, param, existing.copy(
                    phaseOffsetMin = nextMin,
                    phaseOffsetMax = nextMax,
                    phaseOffset = nextActive
                ))
            }
        )
        ImGui.spacing()

        // ── Slope / Duty (Triangle, Square, S&H) ────────────────
        val needsSlope = isSnh ||
                existing.waveform == Waveform.TRIANGLE ||
                existing.waveform == Waveform.SQUARE
        if (needsSlope) {
            val slopeLabel = when {
                isSnh -> "Glide"
                existing.waveform == Waveform.TRIANGLE -> "Slope"
                else -> "Duty Cycle"
            }
            drawCustomRangeSlider(
                label = slopeLabel,
                currentMin = existing.slopeMin,
                currentMax = existing.slopeMax,
                minLimit = 0f,
                maxLimit = 1f,
                formatValue = { "%.3f".format(it) },
                onRangeChanged = { nextMin, nextMax ->
                    val nextActive = existing.slope.coerceIn(nextMin, nextMax)
                    replaceModulator(state, param, existing.copy(
                        slopeMin = nextMin,
                        slopeMax = nextMax,
                        slope = nextActive
                    ))
                }
            )
            ImGui.spacing()
        }

        // ── Test Randomize Button ────────────────────────────────
        ImGui.spacing()
        if (ImGui.button("🎲 Test Randomize", ImGui.getContentRegionAvailX(), 30f)) {
            val randomized = existing.randomizeActiveValues()
            replaceModulator(state, param, randomized)
        }

        // ── Live value bar ───────────────────────────────────────
        ImGui.spacing()
        ImGui.separator()
        val liveVal = param.value
        UITheme.caption("Live Value: %.3f".format(liveVal))
        val barW = ImGui.getContentRegionAvailX()
        val dl = ImGui.getWindowDrawList()
        val cx = ImGui.getCursorScreenPosX()
        val cy = ImGui.getCursorScreenPosY()
        dl.addRectFilled(cx, cy, cx + barW, cy + 10f, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
        dl.addRectFilled(cx, cy, cx + barW * liveVal.coerceIn(0f, 1f), cy + 10f,
            ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 1f))
        ImGui.dummy(barW, 10f)
    }

    private fun drawFinalOscilloscope(history: CvHistoryBuffer) {
        val historySize = history.size
        val w = ImGui.getContentRegionAvailX()
        val h = 80f
        
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + h, bgCol, 4f)
        
        val centerY = startY + h / 2f
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 0.5f)
        
        dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        dl.addLine(startX, startY + 5f, startX + w, startY + 5f, gridColFaint, 1f)
        dl.addLine(startX, startY + h - 5f, startX + w, startY + h - 5f, gridColFaint, 1f)
        
        val numDivisions = 4
        for (i in 1 until numDivisions) {
            val gridX = startX + (w * i / numDivisions)
            dl.addLine(gridX, startY, gridX, startY + h, gridColFaint, 1f)
        }
        
        val stepX = w / (historySize - 1)
        val usableHeight = h - 10f
        val lineCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.9f, 1.0f)
        
        for (i in 0 until historySize - 1) {
            val val1 = history.getAt(i)
            val val2 = history.getAt(i + 1)
            
            val x1 = startX + i * stepX
            val y1 = (startY + h - 5f) - val1 * usableHeight
            val x2 = startX + (i + 1) * stepX
            val y2 = (startY + h - 5f) - val2 * usableHeight
            
            dl.addLine(x1, y1, x2, y2, lineCol, 2.0f)
        }
        
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)
        
        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "1.0")
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "0.5")
        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "0.0")
        
        val textWidth = ImGui.calcTextSize("Final Parameter Value").x
        ImGui.setCursorScreenPos(startX + w - textWidth - 8f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "Final Parameter Value")
        
        ImGui.setCursorScreenPos(startX, startY + h)
    }

    private fun replaceModulator(state: PatchGridState, param: llm.slop.spirals.parameters.ModulatableParameter, newMod: CvModulator) {
        val idx = param.modulators.indexOfFirst { it.sourceId == newMod.sourceId }
        if (idx >= 0) param.modulators[idx] = newMod
        state.editingModulator = newMod
    }

    private fun drawOscilloscope(existing: CvModulator) {
        val history = activeHistory ?: return
        val historySize = history.size
        val w = ImGui.getContentRegionAvailX()
        val h = 80f // Height of the oscilloscope box
        
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        // Reserve space
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        
        // 1. Background
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + h, bgCol, 4f)
        
        // 2. Grid lines
        val centerY = startY + h / 2f
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 0.5f)
        
        // Horizontal lines (Center, +1.0, -1.0)
        dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        dl.addLine(startX, startY + 5f, startX + w, startY + 5f, gridColFaint, 1f)
        dl.addLine(startX, startY + h - 5f, startX + w, startY + h - 5f, gridColFaint, 1f)
        
        // Vertical lines
        val numDivisions = 4
        for (i in 1 until numDivisions) {
            val gridX = startX + (w * i / numDivisions)
            dl.addLine(gridX, startY, gridX, startY + h, gridColFaint, 1f)
        }
        
        // 3. Draw lines of history
        val stepX = w / (historySize - 1)
        val usableHeight = h - 10f
        val weight = existing.weight
        val isBypassed = existing.bypassed
        
        // Raw CV line (faint/dashed gray/blue)
        val rawLineCol = if (isBypassed) 
            ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 0.15f)
        else 
            ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.5f, 0.3f)
            
        for (i in 0 until historySize - 1) {
            val raw1 = history.getAt(i)
            val raw2 = history.getAt(i + 1)
            
            val x1 = startX + i * stepX
            val y1 = centerY - raw1 * (usableHeight / 2f)
            val x2 = startX + (i + 1) * stepX
            val y2 = centerY - raw2 * (usableHeight / 2f)
            
            dl.addLine(x1, y1, x2, y2, rawLineCol, 1.0f)
        }
        
        // Weighted CV line (bright cyan)
        val weightedLineCol = if (isBypassed)
            ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.4f)
        else
            ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.9f, 1.0f) // neon cyan
            
        for (i in 0 until historySize - 1) {
            val raw1 = history.getAt(i)
            val raw2 = history.getAt(i + 1)
            
            val w1 = raw1 * weight
            val w2 = raw2 * weight
            
            val x1 = startX + i * stepX
            val y1 = centerY - w1 * (usableHeight / 2f)
            val x2 = startX + (i + 1) * stepX
            val y2 = centerY - w2 * (usableHeight / 2f)
            
            dl.addLine(x1, y1, x2, y2, weightedLineCol, 2.0f)
        }
        
        // 4. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)
        
        // 5. Y-Axis label markings
        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "+1.0")
        
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "0.0")
        
        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "-1.0")
        
        // Right-aligned helper label
        val textWidth = ImGui.calcTextSize("Modulation Output").x
        ImGui.setCursorScreenPos(startX + w - textWidth - 8f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "Modulation Output")
        
        // Restore cursor position for downstream ImGui rendering
        ImGui.setCursorScreenPos(startX, startY + h)
    }

    private fun drawCustomRangeSlider(
        label: String,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        formatValue: (Float) -> String,
        onRangeChanged: (Float, Float) -> Unit
    ) {
        val w = ImGui.getContentRegionAvailX()
        val h = 36f // height of the widget row
        
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        // Reserve space
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        val io = ImGui.getIO()
        val mouseX = io.mousePos.x
        val mouseY = io.mousePos.y
        
        val leftW = 120f
        val lineStartX = startX + leftW
        val lineEndX = startX + w - 10f
        val lineWidth = lineEndX - lineStartX
        val centerY = startY + h / 2f
        
        // Calculate percentages
        val rangeSpan = maxLimit - minLimit
        val minPct = if (rangeSpan > 0f) (currentMin - minLimit) / rangeSpan else 0f
        val maxPct = if (rangeSpan > 0f) (currentMax - minLimit) / rangeSpan else 0f
        
        val minHandleX = lineStartX + minPct * lineWidth
        val maxHandleX = lineStartX + maxPct * lineWidth
        
        // Handle dragging logic
        val mousePressed = ImGui.isMouseClicked(0)
        val mouseDown = ImGui.isMouseDown(0)
        
        if (mousePressed) {
            val inRowY = mouseY >= startY && mouseY <= startY + h
            val inRowX = mouseX >= lineStartX - 10f && mouseX <= lineEndX + 10f
            if (inRowY && inRowX) {
                activeSliderLabel = label
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
        
        if (mouseDown && activeSliderLabel == label) {
            val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
            val rawVal = minLimit + pct * rangeSpan
            if (draggingMin) {
                val nextMin = rawVal.coerceIn(minLimit, currentMax)
                onRangeChanged(nextMin, currentMax)
            } else if (draggingMax) {
                val nextMax = rawVal.coerceIn(currentMin, maxLimit)
                onRangeChanged(currentMin, nextMax)
            }
        } else if (!mouseDown && activeSliderLabel == label) {
            draggingMin = false
            draggingMax = false
            activeSliderLabel = null
        }
        
        // ── Render ───────────────────────────────────────────────────────────
        
        // 1. Text Labels (Left side)
        // Line 1: Variable Name
        ImGui.setCursorScreenPos(startX, startY + 2f)
        UITheme.body(label)
        
        // Line 2: Current Min/Max Value String
        ImGui.setCursorScreenPos(startX + 8f, startY + 18f)
        val valueStr = "${formatValue(currentMin)} - ${formatValue(currentMax)}"
        UITheme.caption(valueStr)
        
        // 2. Thin horizontal line
        val lineCol = ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1.0f)
        dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 2f)
        
        // 3. Highlighted range line between handles
        val activeRangeCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f)
        dl.addLine(minHandleX, centerY, maxHandleX, centerY, activeRangeCol, 3f)
        
        // 4. Draw Handles (taller than wide rectangles)
        val handleW = 6f
        val handleH = 16f
        
        val handleBgCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f)
        val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
        
        // Min Handle
        dl.addRectFilled(
            minHandleX - handleW / 2f, centerY - handleH / 2f,
            minHandleX + handleW / 2f, centerY + handleH / 2f,
            handleBgCol, 1f
        )
        dl.addRect(
            minHandleX - handleW / 2f, centerY - handleH / 2f,
            minHandleX + handleW / 2f, centerY + handleH / 2f,
            handleBorderCol, 1f
        )
        
        // Max Handle
        dl.addRectFilled(
            maxHandleX - handleW / 2f, centerY - handleH / 2f,
            maxHandleX + handleW / 2f, centerY + handleH / 2f,
            handleBgCol, 1f
        )
        dl.addRect(
            maxHandleX - handleW / 2f, centerY - handleH / 2f,
            maxHandleX + handleW / 2f, centerY + handleH / 2f,
            handleBorderCol, 1f
        )
        
        // Reset Cursor Pos for subsequent UI
        ImGui.setCursorScreenPos(startX, startY + h)
    }
}
