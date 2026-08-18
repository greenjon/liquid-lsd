package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImInt
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.cv.CvHistoryBuffer
import llm.slop.liquidlsd.cv.evaluateModulatorAtOffset
import llm.slop.liquidlsd.cv.getCombinedEffectiveValueAtOffset
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ModulationOperator
import llm.slop.liquidlsd.parameters.ScopeTimebase
import kotlin.math.abs

object OscilloscopeDrawer {

    // Number of steps to evaluate future curve projection
    private const val FUTURE_STEPS = 120

    fun drawFinalOscilloscope(
        session: SessionContext,
        param: ModulatableParameter,
        themeColor: Int,
        activeMods: List<CvModulator>,
        modulatorHistories: Map<String, CvHistoryBuffer>
    ) {
        val history = param.history
        val minVal = param.minClamp
        val maxVal = param.maxClamp
        val isAngle = param.isAngle

        val (totalDuration, divSec) = param.resolveEffectiveTimebase()
        val playheadRatio = param.scopePlayheadRatio.coerceIn(0.0f, 1.0f)

        // 1. Top Controls Bar: Timebase Selector + Playhead Snap Buttons
        drawControlsBar(session, param, totalDuration, divSec)

        val historySize = history.size
        val w = ImGui.getContentRegionAvailX()
        val h = 84f

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        ImGui.dummy(w, h)

        val dl = ImGui.getWindowDrawList()
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + h, bgCol, 4f)

        val centerY = startY + h / 2f
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 0.4f)
        val gridColTick = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 0.6f)

        // Horizontal Grid Lines
        dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        dl.addLine(startX, startY + 6f, startX + w, startY + 6f, gridColFaint, 1f)
        dl.addLine(startX, startY + h - 6f, startX + w, startY + h - 6f, gridColFaint, 1f)

        // Calculate Playhead X position
        val nowX = startX + w * playheadRatio

        // Calibrated Vertical Grid Lines (based on time offsets from NOW)
        if (divSec > 0f && totalDuration > 0f) {
            val pixelsPerSec = w / totalDuration
            val divPixels = divSec * pixelsPerSec

            // Grid lines to the left of NOW (Past)
            var curX = nowX - divPixels
            var curOffset = -divSec
            while (curX >= startX + 1f) {
                dl.addLine(curX, startY, curX, startY + h, gridColTick, 1f)
                val label = ScopeTimebase.formatTimeOffset(curOffset)
                val txtW = ImGui.calcTextSize(label).x
                if (curX - txtW / 2f >= startX + 40f && curX + txtW / 2f <= startX + w - 4f) {
                    ImGui.setCursorScreenPos(curX - txtW / 2f, startY + h - 14f)
                    session.uiTheme.captionColored(0.4f, 0.4f, 0.4f, 0.5f, label)
                }
                curX -= divPixels
                curOffset -= divSec
            }

            // Grid lines to the right of NOW (Future)
            curX = nowX + divPixels
            curOffset = divSec
            while (curX <= startX + w - 1f) {
                dl.addLine(curX, startY, curX, startY + h, gridColTick, 1f)
                val label = ScopeTimebase.formatTimeOffset(curOffset)
                val txtW = ImGui.calcTextSize(label).x
                if (curX - txtW / 2f >= startX + 40f && curX + txtW / 2f <= startX + w - 4f) {
                    ImGui.setCursorScreenPos(curX - txtW / 2f, startY + h - 14f)
                    session.uiTheme.captionColored(0.4f, 0.4f, 0.4f, 0.5f, label)
                }
                curX += divPixels
                curOffset += divSec
            }
        }

        val usableHeight = h - 12f
        val range = maxVal - minVal
        val divisor = if (range == 0f) 1f else range

        // 2. Render Past Modulators (History)
        val pastW = w * playheadRatio
        if (playheadRatio > 0.01f && historySize > 1) {
            val stepPastX = pastW / (historySize - 1)

            // Draw individual modulator past lines
            for (mod in activeMods) {
                val hist = modulatorHistories[mod.id] ?: continue
                val colorId = if (mod.sourceId.startsWith("midi_cc_")) "midi" else mod.sourceId
                val modColor = CvTheme.getThemeColor(colorId, 0.5f)

                for (i in 0 until historySize - 1) {
                    val raw1 = hist.getAt(i)
                    val raw2 = hist.getAt(i + 1)
                    val val1 = if (range == 0f) 0.5f else ((raw1 - minVal) / divisor).coerceIn(0f, 1f)
                    val val2 = if (range == 0f) 0.5f else ((raw2 - minVal) / divisor).coerceIn(0f, 1f)

                    val x1 = startX + i * stepPastX
                    val y1 = (startY + h - 6f) - val1 * usableHeight
                    val x2 = startX + (i + 1) * stepPastX
                    val y2 = (startY + h - 6f) - val2 * usableHeight

                    dl.addLine(x1, y1, x2, y2, modColor, 1.25f)
                }
            }

            // Draw final combined past line
            for (i in 0 until historySize - 1) {
                val raw1 = history.getAt(i)
                val raw2 = history.getAt(i + 1)
                val val1 = if (range == 0f) 0.5f else ((raw1 - minVal) / divisor).coerceIn(0f, 1f)
                val val2 = if (range == 0f) 0.5f else ((raw2 - minVal) / divisor).coerceIn(0f, 1f)

                val x1 = startX + i * stepPastX
                val y1 = (startY + h - 6f) - val1 * usableHeight
                val x2 = startX + (i + 1) * stepPastX
                val y2 = (startY + h - 6f) - val2 * usableHeight

                dl.addLine(x1, y1, x2, y2, themeColor, 2.25f)
            }
        }

        // 3. Render Future Modulators (Projection)
        val futureW = w * (1.0f - playheadRatio)
        val futureSec = totalDuration * (1.0f - playheadRatio)
        if (futureW > 1f && futureSec > 0.001f) {
            val stepFutureX = futureW / FUTURE_STEPS
            val isBipolar = param.minClamp < 0f

            // Draw individual modulator future projections
            for (mod in activeMods) {
                val colorId = if (mod.sourceId.startsWith("midi_cc_")) "midi" else mod.sourceId
                val modProjColor = CvTheme.getThemeColor(colorId, 0.35f)

                var prevX = nowX
                var prevVal: Float = calculateModFutureVal(param, mod, isBipolar, 0.0)
                var prevY = (startY + h - 6f) - (if (range == 0f) 0.5f else ((prevVal - minVal) / divisor).coerceIn(0f, 1f)) * usableHeight

                for (s in 1..FUTURE_STEPS) {
                    val tOffset = (s.toDouble() / FUTURE_STEPS) * futureSec
                    val nextVal = calculateModFutureVal(param, mod, isBipolar, tOffset)
                    val nextX = nowX + s * stepFutureX
                    val nextY = (startY + h - 6f) - (if (range == 0f) 0.5f else ((nextVal - minVal) / divisor).coerceIn(0f, 1f)) * usableHeight

                    dl.addLine(prevX, prevY, nextX, nextY, modProjColor, 1.25f)
                    prevX = nextX
                    prevY = nextY
                }
            }

            // Draw final combined future projection
            val projColor = ImGui.colorConvertFloat4ToU32(
                ((themeColor and 0xFF)) / 255f,
                (((themeColor shr 8) and 0xFF)) / 255f,
                (((themeColor shr 16) and 0xFF)) / 255f,
                0.65f
            )

            var prevX = nowX
            var prevVal = calculateCombinedFutureVal(param, activeMods, isBipolar, 0.0)
            var prevY = (startY + h - 6f) - (if (range == 0f) 0.5f else ((prevVal - minVal) / divisor).coerceIn(0f, 1f)) * usableHeight

            for (s in 1..FUTURE_STEPS) {
                val tOffset = (s.toDouble() / FUTURE_STEPS) * futureSec
                val nextVal = calculateCombinedFutureVal(param, activeMods, isBipolar, tOffset)
                val nextX = nowX + s * stepFutureX
                val nextY = (startY + h - 6f) - (if (range == 0f) 0.5f else ((nextVal - minVal) / divisor).coerceIn(0f, 1f)) * usableHeight

                dl.addLine(prevX, prevY, nextX, nextY, projColor, 2.0f)
                prevX = nextX
                prevY = nextY
            }
        }

        // 4. Draw NOW Playhead & Interactive Handle
        drawPlayhead(session, param, startX, startY, w, h, nowX, usableHeight, minVal, range, divisor)

        // 5. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)

        // 6. Y-Axis Bounds Labels
        val labelScale = if (isAngle) (180f / kotlin.math.PI.toFloat()) else 1f
        val suffix = if (isAngle) "°" else ""

        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.7f, "${"%.1f".format(maxVal * labelScale)}$suffix")
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.7f, "${"%.1f".format((minVal + range * 0.5f) * labelScale)}$suffix")
        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.7f, "${"%.1f".format(minVal * labelScale)}$suffix")

        val title = "Final Parameter Value"
        val textWidth = ImGui.calcTextSize(title).x
        ImGui.setCursorScreenPos(startX + w - textWidth - 8f, startY + 4f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, title)

        // 7. Contextual Tooltips for Past / Playhead / Future
        handleOscilloscopeTooltips(session, startX, startY, w, h, nowX, totalDuration, playheadRatio)

        ImGui.setCursorScreenPos(startX, startY + h)
    }

    fun drawOscilloscope(
        session: SessionContext,
        param: ModulatableParameter,
        themeColor: Int,
        activeHistory: CvHistoryBuffer?,
        activeMods: List<CvModulator> = param.modulators
    ) {
        val history = activeHistory ?: return
        val minVal = param.minClamp
        val maxVal = param.maxClamp
        val isAngle = param.isAngle

        val (totalDuration, divSec) = param.resolveEffectiveTimebase()
        val playheadRatio = param.scopePlayheadRatio.coerceIn(0.0f, 1.0f)

        // Top Controls Bar
        drawControlsBar(session, param, totalDuration, divSec)

        val historySize = history.size
        val w = ImGui.getContentRegionAvailX()
        val h = 84f

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        ImGui.dummy(w, h)

        val dl = ImGui.getWindowDrawList()
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + h, bgCol, 4f)

        val isBipolar = param.minClamp < 0f
        val centerY = startY + h / 2f
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 0.4f)
        val gridColTick = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 0.6f)

        if (isBipolar) {
            dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        } else {
            dl.addLine(startX, startY + h - 6f, startX + w, startY + h - 6f, gridColCenter, 1.5f)
        }
        dl.addLine(startX, startY + 6f, startX + w, startY + 6f, gridColFaint, 1f)
        if (isBipolar) {
            dl.addLine(startX, startY + h - 6f, startX + w, startY + h - 6f, gridColFaint, 1f)
        }

        val nowX = startX + w * playheadRatio

        // Calibrated Vertical Grid Lines
        if (divSec > 0f && totalDuration > 0f) {
            val pixelsPerSec = w / totalDuration
            val divPixels = divSec * pixelsPerSec

            var curX = nowX - divPixels
            var curOffset = -divSec
            while (curX >= startX + 1f) {
                dl.addLine(curX, startY, curX, startY + h, gridColTick, 1f)
                val label = ScopeTimebase.formatTimeOffset(curOffset)
                val txtW = ImGui.calcTextSize(label).x
                if (curX - txtW / 2f >= startX + 40f && curX + txtW / 2f <= startX + w - 4f) {
                    ImGui.setCursorScreenPos(curX - txtW / 2f, startY + h - 14f)
                    session.uiTheme.captionColored(0.4f, 0.4f, 0.4f, 0.5f, label)
                }
                curX -= divPixels
                curOffset -= divSec
            }

            curX = nowX + divPixels
            curOffset = divSec
            while (curX <= startX + w - 1f) {
                dl.addLine(curX, startY, curX, startY + h, gridColTick, 1f)
                val label = ScopeTimebase.formatTimeOffset(curOffset)
                val txtW = ImGui.calcTextSize(label).x
                if (curX - txtW / 2f >= startX + 40f && curX + txtW / 2f <= startX + w - 4f) {
                    ImGui.setCursorScreenPos(curX - txtW / 2f, startY + h - 14f)
                    session.uiTheme.captionColored(0.4f, 0.4f, 0.4f, 0.5f, label)
                }
                curX += divPixels
                curOffset += divSec
            }
        }

        val usableHeight = h - 12f
        val pastW = w * playheadRatio

        // 1. Draw Past History
        if (playheadRatio > 0.01f && historySize > 1) {
            val stepPastX = pastW / (historySize - 1)
            for (i in 0 until historySize - 1) {
                val raw1 = history.getAt(i)
                val raw2 = history.getAt(i + 1)
                val norm1 = if (isBipolar) (raw1 + 1f) / 2f else raw1.coerceIn(0f, 1f)
                val norm2 = if (isBipolar) (raw2 + 1f) / 2f else raw2.coerceIn(0f, 1f)

                val x1 = startX + i * stepPastX
                val y1 = (startY + h - 6f) - norm1 * usableHeight
                val x2 = startX + (i + 1) * stepPastX
                val y2 = (startY + h - 6f) - norm2 * usableHeight

                dl.addLine(x1, y1, x2, y2, themeColor, 2.0f)
            }
        }

        // 2. Draw Future Projection
        val futureW = w * (1.0f - playheadRatio)
        val futureSec = totalDuration * (1.0f - playheadRatio)
        if (futureW > 1f && futureSec > 0.001f) {
            val stepFutureX = futureW / FUTURE_STEPS
            val projColor = ImGui.colorConvertFloat4ToU32(
                ((themeColor and 0xFF)) / 255f,
                (((themeColor shr 8) and 0xFF)) / 255f,
                (((themeColor shr 16) and 0xFF)) / 255f,
                0.65f
            )

            var prevX = nowX
            var prevRaw = getCombinedEffectiveValueAtOffset(activeMods, isBipolar, 0.0)
            var prevNorm = if (isBipolar) (prevRaw + 1f) / 2f else prevRaw.coerceIn(0f, 1f)
            var prevY = (startY + h - 6f) - prevNorm * usableHeight

            for (s in 1..FUTURE_STEPS) {
                val tOffset = (s.toDouble() / FUTURE_STEPS) * futureSec
                val nextRaw = getCombinedEffectiveValueAtOffset(activeMods, isBipolar, tOffset)
                val nextNorm = if (isBipolar) (nextRaw + 1f) / 2f else nextRaw.coerceIn(0f, 1f)
                val nextX = nowX + s * stepFutureX
                val nextY = (startY + h - 6f) - nextNorm * usableHeight

                dl.addLine(prevX, prevY, nextX, nextY, projColor, 1.8f)
                prevX = nextX
                prevY = nextY
            }
        }


        // 3. Draw NOW Playhead
        val range = maxVal - minVal
        val divisor = if (range == 0f) 1f else range
        drawPlayhead(session, param, startX, startY, w, h, nowX, usableHeight, minVal, range, divisor)

        // 4. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)

        // 5. Y-Axis labels
        val labelScale = if (param.isAngle) (180f / kotlin.math.PI.toFloat()) else 1f
        val suffix = if (param.isAngle) "°" else ""
        val maxLabel = "${"%.1f".format(param.maxClamp * labelScale)}$suffix"
        val midLabel = "${"%.1f".format((param.minClamp + (param.maxClamp - param.minClamp) / 2f) * labelScale)}$suffix"
        val minLabel = "${"%.1f".format(param.minClamp * labelScale)}$suffix"

        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.7f, maxLabel)

        if (isBipolar) {
            ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
            session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.7f, midLabel)
        }

        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.7f, minLabel)

        // 6. Tooltips
        handleOscilloscopeTooltips(session, startX, startY, w, h, nowX, totalDuration, playheadRatio)

        ImGui.setCursorScreenPos(startX, startY + h)
    }

    private fun drawControlsBar(
        session: SessionContext,
        param: ModulatableParameter,
        totalDuration: Float,
        divSec: Float
    ) {
        val timebaseLabels = ScopeTimebase.values().map { it.label }.toTypedArray()
        val currentIdx = param.scopeTimebase.ordinal
        val selected = ImInt(currentIdx)

        ImGui.pushItemWidth(76f)
        if (ImGui.combo("##scope_timebase_${param.hashCode()}", selected, timebaseLabels)) {
            param.scopeTimebase = ScopeTimebase.values()[selected.get().coerceIn(0, ScopeTimebase.values().size - 1)]
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Oscilloscope Time Window: Auto scales to LFO period, or choose a fixed window (1s to 24h).")
        }
        ImGui.popItemWidth()

        ImGui.sameLine()
        val infoLabel = if (param.scopeTimebase == ScopeTimebase.AUTO) {
            "Auto (${ScopeTimebase.formatTimeOffset(totalDuration).removePrefix("+")})"
        } else {
            "${ScopeTimebase.formatTimeOffset(totalDuration).removePrefix("+")} (${ScopeTimebase.formatTimeOffset(divSec).removePrefix("+")}/div)"
        }
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.7f, infoLabel)

        // Quick Snap Buttons for Playhead
        val btnW = 34f
        val btnH = 18f
        val rightX = ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - (btnW * 3 + 8f)
        if (rightX > ImGui.getCursorPosX()) {
            ImGui.sameLine(rightX)
            
            val is0 = param.scopePlayheadRatio < 0.05f
            val is50 = param.scopePlayheadRatio in 0.45f..0.55f
            val is100 = param.scopePlayheadRatio > 0.95f

            if (is0) ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.8f, 0.6f))
            if (ImGui.button("0%##snap0_${param.hashCode()}", btnW, btnH)) {
                param.scopePlayheadRatio = 0.0f
            }
            if (is0) ImGui.popStyleColor()
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Lookahead (100% Future Preview)")
            }

            ImGui.sameLine()
            if (is50) ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.8f, 0.6f))
            if (ImGui.button("50%##snap50_${param.hashCode()}", btnW, btnH)) {
                param.scopePlayheadRatio = 0.5f
            }
            if (is50) ImGui.popStyleColor()
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Balanced (50% Past History / 50% Future Projection)")
            }

            ImGui.sameLine()
            if (is100) ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.8f, 0.6f))
            if (ImGui.button("100%##snap100_${param.hashCode()}", btnW, btnH)) {
                param.scopePlayheadRatio = 1.0f
            }
            if (is100) ImGui.popStyleColor()
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("History (100% Recorded Past)")
            }
        }
        ImGui.spacing()
    }

    private fun drawPlayhead(
        session: SessionContext,
        param: ModulatableParameter,
        startX: Float,
        startY: Float,
        w: Float,
        h: Float,
        nowX: Float,
        usableHeight: Float,
        minVal: Float,
        range: Float,
        divisor: Float
    ) {
        val dl = ImGui.getWindowDrawList()
        val playheadLineCol = ImGui.colorConvertFloat4ToU32(0.35f, 0.8f, 1.0f, 0.85f)
        val handleCol = ImGui.colorConvertFloat4ToU32(0.4f, 0.85f, 1.0f, 1.0f)

        // Vertical Playhead Line
        dl.addLine(nowX, startY, nowX, startY + h, playheadLineCol, 1.5f)

        // Top triangle handle [▼]
        val handleSize = 6f
        dl.addTriangleFilled(
            nowX - handleSize, startY,
            nowX + handleSize, startY,
            nowX, startY + handleSize * 1.3f,
            handleCol
        )

        // Playhead current value dot
        val currentNorm = if (range == 0f) 0.5f else ((param.value - minVal) / divisor).coerceIn(0f, 1f)
        val currentY = (startY + h - 6f) - currentNorm * usableHeight
        dl.addCircleFilled(nowX, currentY, 3.5f, handleCol)
        dl.addCircle(nowX, currentY, 5.5f, ImGui.colorConvertFloat4ToU32(0.35f, 0.8f, 1.0f, 0.4f), 12, 1.5f)

        // Interactive dragging of playhead
        val io = ImGui.getIO()
        val mx = io.mousePos.x
        val my = io.mousePos.y
        val isHoveringHandle = mx in (nowX - 10f)..(nowX + 10f) && my in startY..(startY + h)

        if (isHoveringHandle && ImGui.isMouseDown(0)) {
            val newRatio = ((mx - startX) / w).coerceIn(0.0f, 1.0f)
            param.scopePlayheadRatio = newRatio
        }
    }

    private fun handleOscilloscopeTooltips(
        session: SessionContext,
        startX: Float,
        startY: Float,
        w: Float,
        h: Float,
        nowX: Float,
        totalDuration: Float,
        playheadRatio: Float
    ) {
        if (!session.uiTheme.tooltipsEnabled) return
        val io = ImGui.getIO()
        val mx = io.mousePos.x
        val my = io.mousePos.y

        if (mx in startX..(startX + w) && my in startY..(startY + h)) {
            if (abs(mx - nowX) <= 6f) {
                ImGui.setTooltip("Playhead (NOW):\nDrag left/right to adjust past history vs. future lookahead.")
            } else if (mx < nowX) {
                val pastSpan = totalDuration * playheadRatio
                ImGui.setTooltip("Recorded History:\nReal-time past parameter values (-${ScopeTimebase.formatTimeOffset(pastSpan).removePrefix("-").removePrefix("+")} to NOW).")
            } else {
                val futureSpan = totalDuration * (1.0f - playheadRatio)
                ImGui.setTooltip("Projected Future:\nMathematical lookahead of deterministic LFO modulators (NOW to +${ScopeTimebase.formatTimeOffset(futureSpan).removePrefix("+")}).")
            }
        }
    }

    private fun calculateModFutureVal(
        param: ModulatableParameter,
        mod: CvModulator,
        isBipolar: Boolean,
        timeOffsetSec: Double
    ): Float {
        val cvVal = evaluateModulatorAtOffset(mod, timeOffsetSec)
        val rawModAmount = if (isBipolar) {
            cvVal * mod.depth + mod.dcOffset
        } else {
            ((cvVal + 1f) / 2f) * mod.depth + mod.dcOffset
        }
        val scalar = if (mod.operator == ModulationOperator.ADD) {
            if (isBipolar) (param.maxClamp - param.minClamp) / 2.0f else (param.maxClamp - param.minClamp)
        } else 1.0f
        val modAmount = rawModAmount * scalar
        return when (mod.operator) {
            ModulationOperator.ADD -> param.baseValue + modAmount
            ModulationOperator.MUL -> param.baseValue * (1.0f + modAmount)
            ModulationOperator.SCALE -> param.baseValue * (1.0f - mod.depth + modAmount)
        }.coerceIn(param.minClamp, param.maxClamp)
    }

    private fun calculateCombinedFutureVal(
        param: ModulatableParameter,
        mods: List<CvModulator>,
        isBipolar: Boolean,
        timeOffsetSec: Double
    ): Float {
        var result = param.baseValue
        for (mod in mods) {
            if (mod.bypassed) continue
            val cvVal = evaluateModulatorAtOffset(mod, timeOffsetSec)
            val rawModAmount = if (isBipolar) {
                cvVal * mod.depth + mod.dcOffset
            } else {
                ((cvVal + 1f) / 2f) * mod.depth + mod.dcOffset
            }
            val scalar = if (mod.operator == ModulationOperator.ADD) {
                if (isBipolar) (param.maxClamp - param.minClamp) / 2.0f else (param.maxClamp - param.minClamp)
            } else 1.0f
            val modAmount = rawModAmount * scalar
            result = when (mod.operator) {
                ModulationOperator.ADD -> result + modAmount
                ModulationOperator.MUL -> result * (1.0f + modAmount)
                ModulationOperator.SCALE -> result * (1.0f - mod.depth + modAmount)
            }
        }
        return result.coerceIn(param.minClamp, param.maxClamp)
    }

    fun drawBufferOscilloscope(
        session: SessionContext,
        title: String,
        samples: FloatArray,
        minVal: Float,
        maxVal: Float,
        lineColor: Int,
        height: Float
    ) {
        val w = ImGui.getContentRegionAvailX()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        // Reserve display box space
        ImGui.dummy(w, height)

        val dl = ImGui.getWindowDrawList()

        // Background
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + height, bgCol, 4f)

        // Grid lines
        val range = maxVal - minVal
        val zeroY = if (minVal <= 0f && maxVal >= 0f) {
            startY + height * (maxVal / (if (range == 0f) 1f else range))
        } else {
            startY + height / 2f
        }
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.10f, 0.10f, 0.10f, 0.4f)

        // Center / Zero line
        dl.addLine(startX, zeroY, startX + w, zeroY, gridColCenter, 1.5f)
        // Top and bottom boundaries
        dl.addLine(startX, startY + 4f, startX + w, startY + 4f, gridColFaint, 1.0f)
        dl.addLine(startX, startY + height - 4f, startX + w, startY + height - 4f, gridColFaint, 1.0f)

        // Vertical division lines (4 sections)
        val numDivisions = 4
        for (i in 1 until numDivisions) {
            val gridX = startX + (w * i / numDivisions)
            dl.addLine(gridX, startY, gridX, startY + height, gridColFaint, 1.0f)
        }

        // Draw waveform lines
        if (samples.isNotEmpty()) {
            val stepX = w / (samples.size - 1)
            val usableHeight = height - 8f
            val divisor = if (range == 0f) 1f else range

            for (i in 0 until samples.size - 1) {
                val val1 = samples[i].coerceIn(minVal, maxVal)
                val val2 = samples[i + 1].coerceIn(minVal, maxVal)

                val x1 = startX + i * stepX
                val normVal1 = (val1 - minVal) / divisor
                val normVal2 = (val2 - minVal) / divisor

                // Calculate Y coordinates
                val y1 = startY + height - 4f - normVal1 * usableHeight
                val x2 = startX + (i + 1) * stepX
                val y2 = startY + height - 4f - normVal2 * usableHeight

                dl.addLine(x1, y1, x2, y2, lineColor, 2.0f)
            }
        }

        // Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.16f, 0.16f, 0.16f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + height, borderCol, 4f)

        // Axis boundary labels
        ImGui.setCursorScreenPos(startX + 6f, startY + 3f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.1f".format(maxVal))

        ImGui.setCursorScreenPos(startX + 6f, startY + height - 15f)
        session.uiTheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.1f".format(minVal))

        // Left-aligned chart title
        ImGui.setCursorScreenPos(startX + 45f, startY + 3f)
        session.uiTheme.captionColored(0.85f, 0.85f, 0.85f, 0.9f, title)

        // Reset cursor location
        ImGui.setCursorScreenPos(startX, startY + height)
    }
}
