package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImInt
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.cv.CvHistoryBuffer
import llm.slop.liquidlsd.cv.evaluateModulatorAtOffset
import llm.slop.liquidlsd.cv.getCombinedEffectiveValueAtOffset
import llm.slop.liquidlsd.cv.isCvSourceBipolar
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

        val hasLfo = activeMods.any { it.sourceId in setOf("lfo", "beatPhase", "sampleAndHold") }
        val hasNonDet = activeMods.any { it.sourceId in setOf("audio_amp", "audio_bass", "audio_mid", "audio_high", "trigger_onset", "trigger_accent") || it.sourceId.startsWith("midi_cc_") }
        val playheadRatio = if (hasLfo) 0.5f else 1.0f

        val (totalDuration, divSec) = param.resolveEffectiveTimebase(defaultWhenNoLfo = ScopeTimebase.ONE_SEC)

        // 1. Top Controls Bar: Timebase Selector
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
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.32f, 0.35f, 0.38f, 0.9f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.20f, 0.22f, 0.25f, 0.6f)
        val gridColTick = ImGui.colorConvertFloat4ToU32(0.36f, 0.40f, 0.44f, 0.85f)

        // Horizontal Grid Lines
        dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        dl.addLine(startX, startY + 6f, startX + w, startY + 6f, gridColFaint, 1f)
        dl.addLine(startX, startY + h - 6f, startX + w, startY + h - 6f, gridColFaint, 1f)

        // Calculate Playhead X position (Centered for LFO, Right-aligned for Audio/MIDI/Trigger)
        val nowX = startX + w * playheadRatio

        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }

        // Calibrated Vertical Grid Lines (based on time offsets from NOW)
        if (divSec > 0f && totalDuration > 0f) {
            val pixelsPerSec = w / totalDuration
            val divPixels = divSec * pixelsPerSec

            // Grid lines to the left of NOW (Past Lookback / History)
            var curX = nowX - divPixels
            var curOffset = -divSec
            while (curX >= startX + 1f) {
                dl.addLine(curX, startY, curX, startY + h, gridColTick, 1f)
                val label = ScopeTimebase.formatTimeOffset(curOffset)
                val txtW = ImGui.calcTextSize(label).x
                if (curX - txtW / 2f >= startX + 40f && curX + txtW / 2f <= startX + w - 4f) {
                    ImGui.setCursorScreenPos(curX - txtW / 2f, startY + h - captionH - 2f)
                    session.uiTheme.captionColored(0.85f, 0.88f, 0.92f, 0.95f, label)
                }
                curX -= divPixels
                curOffset -= divSec
            }

            // Grid lines to the right of NOW (Future Lookahead) - only if playhead is centered
            if (playheadRatio < 0.999f) {
                curX = nowX + divPixels
                curOffset = divSec
                while (curX <= startX + w - 1f) {
                    dl.addLine(curX, startY, curX, startY + h, gridColTick, 1f)
                    val label = ScopeTimebase.formatTimeOffset(curOffset)
                    val txtW = ImGui.calcTextSize(label).x
                    if (curX - txtW / 2f >= startX + 40f && curX + txtW / 2f <= startX + w - 4f) {
                        ImGui.setCursorScreenPos(curX - txtW / 2f, startY + h - captionH - 2f)
                        session.uiTheme.captionColored(0.85f, 0.88f, 0.92f, 0.95f, label)
                    }
                    curX += divPixels
                    curOffset += divSec
                }
            }
        }

        val usableHeight = h - 12f
        val range = maxVal - minVal
        val divisor = if (range == 0f) 1f else range
        val isBipolar = param.minClamp < 0f

        // 2. Render Lookback / Past History
        val pastW = w * playheadRatio
        val pastSec = totalDuration * playheadRatio
        if (pastW > 1f && pastSec > 0.001f) {
            val stepPastX = pastW / FUTURE_STEPS

            if (hasNonDet || !hasLfo) {
                // True recorded history for audio/midi/trigger
                val fps = 60f
                val samplesInSpan = (pastSec * fps).toInt().coerceAtLeast(2)
                val visibleSamples = minOf(samplesInSpan, history.size)
                val pixelsPerSample = (pastW / pastSec) / fps
                val startSampleIdx = history.size - visibleSamples

                // Final combined past line from recorded param.history
                var prevRaw = history.getAt(startSampleIdx)
                var prevNorm = if (range == 0f) 0.5f else ((prevRaw - minVal) / divisor).coerceIn(0f, 1f)
                var prevX = nowX - (history.size - 1 - startSampleIdx) * pixelsPerSample
                var prevY = (startY + h - 6f) - prevNorm * usableHeight

                // Draw baseline if history buffer does not cover full selected pastSec
                if (prevX > startX + 1f) {
                    val baseNorm = if (range == 0f) 0.5f else ((param.defaultValue - minVal) / divisor).coerceIn(0f, 1f)
                    val baseY = (startY + h - 6f) - baseNorm * usableHeight
                    dl.addLine(startX, baseY, prevX, prevY, themeColor, 2.0f)
                }

                for (idx in (startSampleIdx + 1) until history.size) {
                    val nextRaw = history.getAt(idx)
                    val nextNorm = if (range == 0f) 0.5f else ((nextRaw - minVal) / divisor).coerceIn(0f, 1f)
                    val nextX = nowX - (history.size - 1 - idx) * pixelsPerSample
                    val nextY = (startY + h - 6f) - nextNorm * usableHeight

                    dl.addLine(prevX, prevY, nextX, nextY, themeColor, 2.25f)
                    prevX = nextX
                    prevY = nextY
                }
            } else {
                // Pure deterministic LFO past calculation
                for (mod in activeMods) {
                    val colorId = if (mod.sourceId.startsWith("midi_cc_")) "midi" else mod.sourceId
                    val modColor = CvTheme.getThemeColor(colorId, 0.5f)

                    var prevX = startX
                    var prevVal = calculateModFutureVal(param, mod, isBipolar, -pastSec.toDouble())
                    var prevY = (startY + h - 6f) - (if (range == 0f) 0.5f else ((prevVal - minVal) / divisor).coerceIn(0f, 1f)) * usableHeight

                    for (s in 1..FUTURE_STEPS) {
                        val tOffset = -pastSec.toDouble() * (1.0 - s.toDouble() / FUTURE_STEPS)
                        val nextVal = calculateModFutureVal(param, mod, isBipolar, tOffset)
                        val nextX = startX + s * stepPastX
                        val nextY = (startY + h - 6f) - (if (range == 0f) 0.5f else ((nextVal - minVal) / divisor).coerceIn(0f, 1f)) * usableHeight

                        dl.addLine(prevX, prevY, nextX, nextY, modColor, 1.25f)
                        prevX = nextX
                        prevY = nextY
                    }
                }

                var prevX = startX
                var prevVal = calculateCombinedFutureVal(param, activeMods, isBipolar, -pastSec.toDouble())
                var prevY = (startY + h - 6f) - (if (range == 0f) 0.5f else ((prevVal - minVal) / divisor).coerceIn(0f, 1f)) * usableHeight

                for (s in 1..FUTURE_STEPS) {
                    val tOffset = -pastSec.toDouble() * (1.0 - s.toDouble() / FUTURE_STEPS)
                    val nextVal = calculateCombinedFutureVal(param, activeMods, isBipolar, tOffset)
                    val nextX = startX + s * stepPastX
                    val nextY = (startY + h - 6f) - (if (range == 0f) 0.5f else ((nextVal - minVal) / divisor).coerceIn(0f, 1f)) * usableHeight

                    dl.addLine(prevX, prevY, nextX, nextY, themeColor, 2.25f)
                    prevX = nextX
                    prevY = nextY
                }
            }
        }

        // 3. Render Lookahead (Right Half) - only if playhead is centered
        val futureW = w * (1.0f - playheadRatio)
        val futureSec = totalDuration * (1.0f - playheadRatio)
        if (playheadRatio < 0.999f && futureW > 1f && futureSec > 0.001f) {
            val stepFutureX = futureW / FUTURE_STEPS

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

        // 4. Draw NOW Playhead (Centered or Right-aligned)
        drawPlayhead(session, param.value, startX, startY, w, h, nowX, usableHeight, minVal, range, divisor, isNormalized = false)

        // 5. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.26f, 0.28f, 0.32f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)

        // 6. Y-Axis Bounds Labels
        val labelScale = if (isAngle) (180f / kotlin.math.PI.toFloat()) else 1f
        val suffix = if (isAngle) "°" else ""

        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, "${"%.1f".format(maxVal * labelScale)}$suffix")
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, "${"%.1f".format((minVal + range * 0.5f) * labelScale)}$suffix")
        ImGui.setCursorScreenPos(startX + 6f, startY + h - captionH - 2f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, "${"%.1f".format(minVal * labelScale)}$suffix")

        val title = "Final Parameter Value"
        val textWidth = ImGui.calcTextSize(title).x
        val titleX = if (playheadRatio >= 0.999f) (startX + 60f) else (startX + w - textWidth - 8f)
        ImGui.setCursorScreenPos(titleX, startY + 4f)
        session.uiTheme.captionColored(0.78f, 0.82f, 0.86f, 0.90f, title)

        // 7. Contextual Tooltips
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

        val hasLfo = activeMods.any { it.sourceId in setOf("lfo", "beatPhase", "sampleAndHold") }
        val playheadRatio = if (hasLfo) 0.5f else 1.0f

        val (totalDuration, divSec) = param.resolveEffectiveTimebase(defaultWhenNoLfo = ScopeTimebase.ONE_SEC)

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
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.32f, 0.35f, 0.38f, 0.9f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.20f, 0.22f, 0.25f, 0.6f)
        val gridColTick = ImGui.colorConvertFloat4ToU32(0.36f, 0.40f, 0.44f, 0.85f)

        if (isBipolar) {
            dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        } else {
            dl.addLine(startX, startY + h - 6f, startX + w, startY + h - 6f, gridColCenter, 1.5f)
        }
        dl.addLine(startX, startY + 6f, startX + w, startY + 6f, gridColFaint, 1f)
        if (isBipolar) {
            dl.addLine(startX, startY + h - 6f, startX + w, startY + h - 6f, gridColFaint, 1f)
        }

        // Calculate Playhead X position (Centered for LFO, Right-aligned for Audio/MIDI/Trigger)
        val nowX = startX + w * playheadRatio

        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }

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
                    ImGui.setCursorScreenPos(curX - txtW / 2f, startY + h - captionH - 2f)
                    session.uiTheme.captionColored(0.85f, 0.88f, 0.92f, 0.95f, label)
                }
                curX -= divPixels
                curOffset -= divSec
            }

            if (playheadRatio < 0.999f) {
                curX = nowX + divPixels
                curOffset = divSec
                while (curX <= startX + w - 1f) {
                    dl.addLine(curX, startY, curX, startY + h, gridColTick, 1f)
                    val label = ScopeTimebase.formatTimeOffset(curOffset)
                    val txtW = ImGui.calcTextSize(label).x
                    if (curX - txtW / 2f >= startX + 40f && curX + txtW / 2f <= startX + w - 4f) {
                        ImGui.setCursorScreenPos(curX - txtW / 2f, startY + h - captionH - 2f)
                        session.uiTheme.captionColored(0.85f, 0.88f, 0.92f, 0.95f, label)
                    }
                    curX += divPixels
                    curOffset += divSec
                }
            }
        }

        val usableHeight = h - 12f
        val pastW = w * playheadRatio
        val pastSec = totalDuration * playheadRatio

        // 1. Draw Lookback / Past History
        if (pastW > 1f && pastSec > 0.001f) {
            if (!hasLfo) {
                // True recorded history playback for Audio, Trigger, MIDI (summed value)
                val fps = 60f
                val samplesInSpan = (pastSec * fps).toInt().coerceAtLeast(2)
                val visibleSamples = minOf(samplesInSpan, history.size)
                val pixelsPerSample = (pastW / pastSec) / fps
                val startSampleIdx = history.size - visibleSamples

                var prevRaw = history.getAt(startSampleIdx)
                var prevNorm = if (isBipolar) (prevRaw + 1f) / 2f else prevRaw.coerceIn(0f, 1f)
                var prevX = nowX - (history.size - 1 - startSampleIdx) * pixelsPerSample
                var prevY = (startY + h - 6f) - prevNorm * usableHeight

                // Draw baseline if history buffer does not cover full selected pastSec
                if (prevX > startX + 1f) {
                    val baseNorm = if (isBipolar) 0.5f else 0.0f
                    val baseY = (startY + h - 6f) - baseNorm * usableHeight
                    dl.addLine(startX, baseY, prevX, prevY, themeColor, 2.0f)
                }

                // Draw solid combined history line
                for (idx in (startSampleIdx + 1) until history.size) {
                    val nextRaw = history.getAt(idx)
                    val nextNorm = if (isBipolar) (nextRaw + 1f) / 2f else nextRaw.coerceIn(0f, 1f)
                    val nextX = nowX - (history.size - 1 - idx) * pixelsPerSample
                    val nextY = (startY + h - 6f) - nextNorm * usableHeight

                    dl.addLine(prevX, prevY, nextX, nextY, themeColor, 2.0f)
                    prevX = nextX
                    prevY = nextY
                }
            } else {
                // Deterministic LFO calculated lookback
                val stepPastX = pastW / FUTURE_STEPS
                var prevX = startX
                var prevRaw = getCombinedEffectiveValueAtOffset(activeMods, isBipolar, -pastSec.toDouble())
                var prevNorm = if (isBipolar) (prevRaw + 1f) / 2f else prevRaw.coerceIn(0f, 1f)
                var prevY = (startY + h - 6f) - prevNorm * usableHeight

                for (s in 1..FUTURE_STEPS) {
                    val tOffset = -pastSec.toDouble() * (1.0 - s.toDouble() / FUTURE_STEPS)
                    val nextRaw = getCombinedEffectiveValueAtOffset(activeMods, isBipolar, tOffset)
                    val nextNorm = if (isBipolar) (nextRaw + 1f) / 2f else nextRaw.coerceIn(0f, 1f)
                    val nextX = startX + s * stepPastX
                    val nextY = (startY + h - 6f) - nextNorm * usableHeight

                    dl.addLine(prevX, prevY, nextX, nextY, themeColor, 2.0f)
                    prevX = nextX
                    prevY = nextY
                }
            }
        }

        // 2. Draw Lookahead (Right Half) - only if playhead is centered
        val futureW = w * (1.0f - playheadRatio)
        val futureSec = totalDuration * (1.0f - playheadRatio)
        if (playheadRatio < 0.999f && futureW > 1f && futureSec > 0.001f) {
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


        // 3. Draw NOW Playhead (Centered or Right-aligned)
        val range = maxVal - minVal
        val divisor = if (range == 0f) 1f else range
        val currentModVal = if (hasLfo) {
            getCombinedEffectiveValueAtOffset(activeMods, isBipolar, 0.0)
        } else {
            if (history.size > 0) history.getAt(history.size - 1) else 0f
        }
        drawPlayhead(session, currentModVal, startX, startY, w, h, nowX, usableHeight, minVal, range, divisor, isNormalized = true, isBipolar = isBipolar)

        // 4. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.26f, 0.28f, 0.32f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)

        // 5. Y-Axis labels
        val labelScale = if (param.isAngle) (180f / kotlin.math.PI.toFloat()) else 1f
        val suffix = if (param.isAngle) "°" else ""
        val maxLabel = "${"%.1f".format(param.maxClamp * labelScale)}$suffix"
        val midLabel = "${"%.1f".format((param.minClamp + (param.maxClamp - param.minClamp) / 2f) * labelScale)}$suffix"
        val minLabel = "${"%.1f".format(param.minClamp * labelScale)}$suffix"

        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, maxLabel)

        if (isBipolar) {
            ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
            session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, midLabel)
        }

        ImGui.setCursorScreenPos(startX + 6f, startY + h - captionH - 2f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, minLabel)

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

        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
        val maxLabelWidth = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            timebaseLabels.maxOfOrNull { ImGui.calcTextSize(it).x } ?: 40f
        }
        val comboWidth = (maxLabelWidth + ImGui.getFrameHeight() + 18f * fontScale).coerceAtLeast(80f * fontScale)

        ImGui.pushItemWidth(comboWidth)
        if (ImGui.combo("##scope_timebase_${param.hashCode()}", selected, timebaseLabels)) {
            param.scopeTimebase = ScopeTimebase.values()[selected.get().coerceIn(0, ScopeTimebase.values().size - 1)]
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Oscilloscope Time Window: Auto scales to LFO period, or choose a fixed window (1s to 24h).")
        }
        ImGui.popItemWidth()

        ImGui.sameLine(0f, 8f * fontScale)
        val infoLabel = if (param.scopeTimebase == ScopeTimebase.AUTO) {
            "Auto (${ScopeTimebase.formatTimeOffset(totalDuration).removePrefix("+")})"
        } else {
            "${ScopeTimebase.formatTimeOffset(totalDuration).removePrefix("+")} (${ScopeTimebase.formatTimeOffset(divSec).removePrefix("+")}/div)"
        }
        session.uiTheme.captionColored(0.75f, 0.78f, 0.82f, 0.95f, infoLabel)
        ImGui.spacing()
    }

    private fun drawPlayhead(
        session: SessionContext,
        currentValue: Float,
        startX: Float,
        startY: Float,
        w: Float,
        h: Float,
        nowX: Float,
        usableHeight: Float,
        minVal: Float,
        range: Float,
        divisor: Float,
        isNormalized: Boolean = false,
        isBipolar: Boolean = false
    ) {
        val dl = ImGui.getWindowDrawList()
        val playheadLineCol = ImGui.colorConvertFloat4ToU32(0.35f, 0.8f, 1.0f, 0.85f)
        val handleCol = ImGui.colorConvertFloat4ToU32(0.4f, 0.85f, 1.0f, 1.0f)
        val effectiveX = nowX.coerceIn(startX + 1f, startX + w - 1f)

        // Vertical Playhead Line
        dl.addLine(effectiveX, startY, effectiveX, startY + h, playheadLineCol, 1.5f)

        // Top subtle indicator pip [▼]
        val handleSize = 5f
        dl.addTriangleFilled(
            effectiveX - handleSize, startY,
            effectiveX + handleSize, startY,
            effectiveX, startY + handleSize * 1.2f,
            handleCol
        )

        // Playhead current value dot
        val currentNorm = if (isNormalized) {
            if (isBipolar) (currentValue + 1f) / 2f else currentValue.coerceIn(0f, 1f)
        } else {
            if (range == 0f) 0.5f else ((currentValue - minVal) / divisor).coerceIn(0f, 1f)
        }
        val currentY = (startY + h - 6f) - currentNorm * usableHeight
        dl.addCircleFilled(effectiveX, currentY, 3.5f, handleCol)
        dl.addCircle(effectiveX, currentY, 5.5f, ImGui.colorConvertFloat4ToU32(0.35f, 0.8f, 1.0f, 0.4f), 12, 1.5f)
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
            if (playheadRatio >= 0.999f) {
                if (abs(mx - nowX) <= 10f) {
                    ImGui.setTooltip("Playhead (NOW):\nCurrent live value.")
                } else {
                    ImGui.setTooltip("Waveform History:\nRecorded CV trajectory leading into current value (-${ScopeTimebase.formatTimeOffset(totalDuration).removePrefix("-").removePrefix("+")} to NOW).")
                }
            } else {
                if (abs(mx - nowX) <= 6f) {
                    ImGui.setTooltip("Playhead (NOW):\nCurrent parameter value & phase.")
                } else if (mx < nowX) {
                    val pastSpan = totalDuration * playheadRatio
                    ImGui.setTooltip("Waveform Lookback:\nModulation trajectory leading into current phase (-${ScopeTimebase.formatTimeOffset(pastSpan).removePrefix("-").removePrefix("+")} to NOW).")
                } else {
                    val futureSpan = totalDuration * (1.0f - playheadRatio)
                    ImGui.setTooltip("Waveform Lookahead:\nProjected modulation trajectory ahead of current phase (NOW to +${ScopeTimebase.formatTimeOffset(futureSpan).removePrefix("+")}).")
                }
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
        val isSourceBipolar = isCvSourceBipolar(mod.sourceId)
        val rawModAmount = if (isSourceBipolar) {
            if (isBipolar) cvVal * mod.depth + mod.dcOffset else ((cvVal + 1f) / 2f) * mod.depth + mod.dcOffset
        } else {
            cvVal * mod.depth + mod.dcOffset
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
            val isSourceBipolar = isCvSourceBipolar(mod.sourceId)
            val rawModAmount = if (isSourceBipolar) {
                if (isBipolar) cvVal * mod.depth + mod.dcOffset else ((cvVal + 1f) / 2f) * mod.depth + mod.dcOffset
            } else {
                cvVal * mod.depth + mod.dcOffset
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
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.32f, 0.35f, 0.38f, 0.9f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.20f, 0.22f, 0.25f, 0.6f)

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
        val borderCol = ImGui.colorConvertFloat4ToU32(0.26f, 0.28f, 0.32f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + height, borderCol, 4f)

        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }

        // Axis boundary labels
        ImGui.setCursorScreenPos(startX + 6f, startY + 3f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, "%.1f".format(maxVal))

        ImGui.setCursorScreenPos(startX + 6f, startY + height - captionH - 2f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, "%.1f".format(minVal))

        // Left-aligned chart title
        ImGui.setCursorScreenPos(startX + 45f, startY + 3f)
        session.uiTheme.captionColored(0.88f, 0.90f, 0.94f, 0.95f, title)

        // Reset cursor location
        ImGui.setCursorScreenPos(startX, startY + height)
    }
}
