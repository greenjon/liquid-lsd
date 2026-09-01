package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImInt
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.cv.CVRegistry
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

    // Golden-ratio conjugate for Halton quasi-random jitter in waveform anti-aliasing
    private const val PHI_FRAC = 0.618033988749895

    // Preallocated immutable timebase lists and label arrays to eliminate GC allocations on render path
    private val LFO_TIMEBASES = ScopeTimebase.values().toList()
    private val NON_LFO_TIMEBASES = ScopeTimebase.values().filter { it != ScopeTimebase.AUTO }
    private val LFO_LABELS = LFO_TIMEBASES.map { it.label }.toTypedArray()
    private val NON_LFO_LABELS = NON_LFO_TIMEBASES.map { it.label }.toTypedArray()

    // Reusable ImInt wrapper for timebase dropdown combo to prevent per-frame allocations
    private val timebaseComboIndex = ImInt(0)

    fun drawValueOscilloscope(
        session: SessionContext,
        param: ModulatableParameter,
        themeColor: Int,
        scopeKey: String = "value"
    ) {
        val history = param.history
        val minVal = param.minClamp
        val maxVal = param.maxClamp
        val isAngle = param.isAngle

        val (totalDuration, divSec) = param.resolveEffectiveTimebase(scopeKey = scopeKey, defaultWhenNoLfo = ScopeTimebase.TEN_SEC)

        // 1. Top Controls Bar: Timebase Selector
        drawControlsBar(session, param, scopeKey, totalDuration, divSec)

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

        // Calculate Playhead X position (Always right-aligned for Value history view)
        val nowX = startX + w

        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }

        // Calibrated Vertical Grid Lines (based on time offsets to the left of NOW)
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
        }

        val usableHeight = h - 12f
        val range = maxVal - minVal
        val divisor = if (range == 0f) 1f else range

        // 2. Render Full-Width Recorded History
        if (w > 1f && totalDuration > 0.001f) {
            val fps = CVRegistry.getTargetFps()
            val samplesInSpan = (totalDuration * fps).toInt().coerceAtLeast(2)
            val visibleSamples = minOf(samplesInSpan, history.size)
            val pixelsPerSample = (w / totalDuration) / fps
            val startSampleIdx = history.size - visibleSamples

            var prevRaw = history.getAt(startSampleIdx)
            var prevNorm = if (range == 0f) 0.5f else ((prevRaw - minVal) / divisor).coerceIn(0f, 1f)
            var prevX = nowX - (history.size - 1 - startSampleIdx) * pixelsPerSample
            var prevY = (startY + h - 6f) - prevNorm * usableHeight

            // Baseline if history buffer does not cover full selected duration
            if (prevX > startX + 1f) {
                val baseNorm = if (range == 0f) 0.5f else ((param.defaultValue - minVal) / divisor).coerceIn(0f, 1f)
                val baseY = (startY + h - 6f) - baseNorm * usableHeight
                dl.addLine(startX, baseY, prevX, baseY, themeColor, 2.0f)
                dl.addLine(prevX, baseY, prevX, prevY, themeColor, 2.0f)
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
        }

        // 3. Draw NOW Playhead (Right-aligned)
        drawPlayhead(session, param.value, startX, startY, w, h, nowX, usableHeight, minVal, range, divisor, isNormalized = false)

        // 4. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.26f, 0.28f, 0.32f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)

        // 5. Y-Axis Bounds Labels
        val labelScale = if (isAngle) (180f / kotlin.math.PI.toFloat()) else 1f
        val suffix = if (isAngle) "°" else ""

        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, "${"%.1f".format(maxVal * labelScale)}$suffix")
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, "${"%.1f".format((minVal + range * 0.5f) * labelScale)}$suffix")
        ImGui.setCursorScreenPos(startX + 6f, startY + h - captionH - 2f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, "${"%.1f".format(minVal * labelScale)}$suffix")

        val title = "Parameter Value"
        val titleX = startX + 60f
        ImGui.setCursorScreenPos(titleX, startY + 4f)
        session.uiTheme.captionColored(0.78f, 0.82f, 0.86f, 0.90f, title)

        // 6. Contextual Tooltips
        handleOscilloscopeTooltips(session, startX, startY, w, h, nowX, totalDuration, hasLfo = false)

        ImGui.setCursorScreenPos(startX, startY + h)
    }

    fun drawFinalOscilloscope(
        session: SessionContext,
        param: ModulatableParameter,
        themeColor: Int,
        scopeKey: String = "value"
    ) = drawValueOscilloscope(session, param, themeColor, scopeKey)

    fun drawOscilloscope(
        session: SessionContext,
        param: ModulatableParameter,
        themeColor: Int,
        activeHistory: CvHistoryBuffer?,
        activeMods: List<CvModulator> = param.modulators,
        scopeKey: String = "default",
        ghostHistory: CvHistoryBuffer? = null
    ) {
        val history = activeHistory ?: return
        val minVal = param.minClamp
        val maxVal = param.maxClamp
        val isMuted = activeMods.isNotEmpty() && activeMods.all { it.bypassed }
        val strokeColor = if (isMuted) ImGui.colorConvertFloat4ToU32(1.0f, 0.82f, 0.20f, 0.95f) else themeColor

        val hasLfo = activeMods.any { isCvSourceBipolar(it.sourceId) }
        val lfoMods = if (hasLfo) activeMods.filter { isCvSourceBipolar(it.sourceId) } else emptyList()

        val (totalDuration, divSec) = param.resolveEffectiveTimebase(scopeKey = scopeKey, defaultWhenNoLfo = ScopeTimebase.TEN_SEC)

        // Top Controls Bar
        drawControlsBar(session, param, scopeKey, totalDuration, divSec, activeMods)

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

        // Playhead X: centred at 50% for LFO (history left, lookahead right), right-aligned for non-deterministic sources
        val nowX = if (hasLfo) startX + w * 0.5f else startX + w

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

            if (hasLfo) {
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
        val pastW = if (hasLfo) w * 0.5f else w
        val pastSec = if (hasLfo) totalDuration * 0.5f else totalDuration

        // Pre-compute y at t=0 (NOW) to seed seamless continuity between past and future segments
        val nowRaw0 = if (lfoMods.isNotEmpty()) getCombinedEffectiveValueAtOffset(lfoMods, isBipolar, 0.0, includeBypassed = true) else 0f
        val nowNorm0 = if (isBipolar) (nowRaw0 + 1f) / 2f else nowRaw0.coerceIn(0f, 1f)
        var lfoSeamY = (startY + h - 6f) - nowNorm0 * usableHeight

        // 1. Draw Lookback / Past History
        if (pastW > 1f && pastSec > 0.001f) {
            if (!hasLfo) {
                // True recorded history playback for Audio, Trigger, MIDI (summed value)
                val fps = CVRegistry.getTargetFps()
                val samplesInSpan = (pastSec * fps).toInt().coerceAtLeast(2)
                val visibleSamples = minOf(samplesInSpan, history.size)
                val pixelsPerSample = (pastW / pastSec) / fps
                val startSampleIdx = history.size - visibleSamples

                // Draw Ghost Trace (e.g. Raw Audio Input) if provided
                if (ghostHistory != null && ghostHistory.size > 1) {
                    val r = (strokeColor and 0xFF) / 255f
                    val g = (strokeColor ushr 8 and 0xFF) / 255f
                    val b = (strokeColor ushr 16 and 0xFF) / 255f
                    val ghostColor = ImGui.colorConvertFloat4ToU32(r, g, b, 0.35f)

                    val visibleGhostSamples = minOf(samplesInSpan, ghostHistory.size)
                    val startGhostIdx = ghostHistory.size - visibleGhostSamples
                    var prevGhostRaw = ghostHistory.getAt(startGhostIdx)
                    var prevGhostNorm = if (isBipolar) (prevGhostRaw + 1f) / 2f else prevGhostRaw.coerceIn(0f, 1f)
                    var prevGhostX = nowX - (ghostHistory.size - 1 - startGhostIdx) * pixelsPerSample
                    var prevGhostY = (startY + h - 6f) - prevGhostNorm * usableHeight

                    for (idx in (startGhostIdx + 1) until ghostHistory.size) {
                        val nextGhostRaw = ghostHistory.getAt(idx)
                        val nextGhostNorm = if (isBipolar) (nextGhostRaw + 1f) / 2f else nextGhostRaw.coerceIn(0f, 1f)
                        val nextGhostX = nowX - (ghostHistory.size - 1 - idx) * pixelsPerSample
                        val nextGhostY = (startY + h - 6f) - nextGhostNorm * usableHeight

                        dl.addLine(prevGhostX, prevGhostY, nextGhostX, nextGhostY, ghostColor, 1.5f)
                        prevGhostX = nextGhostX
                        prevGhostY = nextGhostY
                    }
                }

                var prevRaw = history.getAt(startSampleIdx)
                var prevNorm = if (isBipolar) (prevRaw + 1f) / 2f else prevRaw.coerceIn(0f, 1f)
                var prevX = nowX - (history.size - 1 - startSampleIdx) * pixelsPerSample
                var prevY = (startY + h - 6f) - prevNorm * usableHeight

                // Draw flat baseline if history buffer does not cover full selected pastSec
                if (prevX > startX + 1f) {
                    val baseNorm = if (isBipolar) 0.5f else 0.0f
                    val baseY = (startY + h - 6f) - baseNorm * usableHeight
                    dl.addLine(startX, baseY, prevX, baseY, strokeColor, 2.0f)
                    dl.addLine(prevX, baseY, prevX, prevY, strokeColor, 2.0f)
                }

                // Draw solid combined history line
                for (idx in (startSampleIdx + 1) until history.size) {
                    val nextRaw = history.getAt(idx)
                    val nextNorm = if (isBipolar) (nextRaw + 1f) / 2f else nextRaw.coerceIn(0f, 1f)
                    val nextX = nowX - (history.size - 1 - idx) * pixelsPerSample
                    val nextY = (startY + h - 6f) - nextNorm * usableHeight

                    dl.addLine(prevX, prevY, nextX, nextY, strokeColor, 2.25f)
                    prevX = nextX
                    prevY = nextY
                }
            } else {
                // Deterministic LFO lookback: analytically computed from -pastSec to NOW.
                // Steps proportional to pixel width for high-density sample resolution at any scope size.
                val steps = (pastW * 1.5f).toInt().coerceIn(60, 1000)
                val initRaw = getCombinedEffectiveValueAtOffset(lfoMods, isBipolar, -pastSec.toDouble(), includeBypassed = true)
                val initNorm = if (isBipolar) (initRaw + 1f) / 2f else initRaw.coerceIn(0f, 1f)
                val initY = (startY + h - 6f) - initNorm * usableHeight
                val (_, seam) = drawLfoWaveSegments(
                    dl, lfoMods, isBipolar, startY, h, usableHeight,
                    segStartX = startX, stepX = pastW / steps, steps = steps,
                    tOrigin = -pastSec.toDouble(), tFinal = 0.0,
                    color = strokeColor, lineWidth = 2.0f,
                    initPrevX = startX, initPrevY = initY
                )
                lfoSeamY = seam  // carry endpoint y into the future segment for pixel continuity
            }
        }

        // 2. Draw Lookahead (Right Half) — LFO only, always 50% of the window
        val futureW = if (hasLfo) w * 0.5f else 0f
        val futureSec = if (hasLfo) totalDuration * 0.5f else 0f
        if (hasLfo && futureW > 1f && futureSec > 0.001f && lfoMods.isNotEmpty()) {
            // ImGui U32 layout = 0xAABBGGRR — use unsigned shift to extract channels safely
            val r = (strokeColor         and 0xFF) / 255f
            val g = (strokeColor ushr 8  and 0xFF) / 255f
            val b = (strokeColor ushr 16 and 0xFF) / 255f
            val projColor = ImGui.colorConvertFloat4ToU32(r, g, b, 0.65f)

            val steps = (futureW * 1.5f).toInt().coerceIn(60, 1000)
            drawLfoWaveSegments(
                dl, lfoMods, isBipolar, startY, h, usableHeight,
                segStartX = nowX, stepX = futureW / steps, steps = steps,
                tOrigin = 0.0, tFinal = futureSec.toDouble(),
                color = projColor, lineWidth = 1.8f,
                initPrevX = nowX, initPrevY = lfoSeamY  // start exactly where the past segment ended
            )
        }


        // 3. Draw NOW Playhead (Centered or Right-aligned)
        val range = maxVal - minVal
        val divisor = if (range == 0f) 1f else range
        val currentModVal = if (hasLfo) {
            getCombinedEffectiveValueAtOffset(activeMods, isBipolar, 0.0, includeBypassed = true)
        } else {
            if (history.size > 0) history.getAt(history.size - 1) else 0f
        }
        drawPlayhead(session, currentModVal, startX, startY, w, h, nowX, usableHeight, minVal, range, divisor, isNormalized = true, isBipolar = isBipolar)

        // 4. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.26f, 0.28f, 0.32f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)

        // 5. Y-Axis labels (computed early so maxLabelW can anchor the muted watermark)
        val labelScale = if (param.isAngle) (180f / kotlin.math.PI.toFloat()) else 1f
        val suffix = if (param.isAngle) "°" else ""
        val maxLabel = "${"%.1f".format(param.maxClamp * labelScale)}$suffix"
        val midLabel = "${"%.1f".format((param.minClamp + (param.maxClamp - param.minClamp) / 2f) * labelScale)}$suffix"
        val minLabel = "${"%.1f".format(param.minClamp * labelScale)}$suffix"
        val maxLabelW = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.calcTextSize(maxLabel).x }

        // 6. Watermark caption when muted
        if (isMuted) {
            ImGui.setCursorScreenPos(startX + maxLabelW + 10f, startY + 4f)
            session.uiTheme.captionColored(1.0f, 0.82f, 0.20f, 0.90f, "[SCOPE LIVE — OUTPUT MUTED FROM VALUE]")
        }

        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, maxLabel)

        if (isBipolar) {
            ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
            session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, midLabel)
        }

        ImGui.setCursorScreenPos(startX + 6f, startY + h - captionH - 2f)
        session.uiTheme.captionColored(0.80f, 0.83f, 0.88f, 0.92f, minLabel)

        // 7. Tooltips
        handleOscilloscopeTooltips(session, startX, startY, w, h, nowX, totalDuration, hasLfo)

        ImGui.setCursorScreenPos(startX, startY + h)
    }

    private fun drawControlsBar(
        session: SessionContext,
        param: ModulatableParameter,
        scopeKey: String,
        totalDuration: Float,
        divSec: Float,
        activeMods: List<CvModulator> = emptyList()
    ) {
        val isLfoScope = (scopeKey == "lfo" || scopeKey == "default")
        val availableTimebases = if (isLfoScope) LFO_TIMEBASES else NON_LFO_TIMEBASES
        val timebaseLabels = if (isLfoScope) LFO_LABELS else NON_LFO_LABELS
        val currentTimebase = param.getScopeTimebase(scopeKey)
        val currentIdx = availableTimebases.indexOf(currentTimebase).coerceAtLeast(0)
        timebaseComboIndex.set(currentIdx)

        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
        val maxLabelWidth = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            timebaseLabels.maxOfOrNull { ImGui.calcTextSize(it).x } ?: 40f
        }
        val comboWidth = (maxLabelWidth + ImGui.getFrameHeight() + 18f * fontScale).coerceAtLeast(80f * fontScale)

        ImGui.pushItemWidth(comboWidth)
        if (ImGui.combo("##scope_timebase_${param.hashCode()}_$scopeKey", timebaseComboIndex, timebaseLabels)) {
            param.setScopeTimebase(scopeKey, availableTimebases[timebaseComboIndex.get().coerceIn(0, availableTimebases.size - 1)])
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            val tooltip = if (isLfoScope) {
                "Oscilloscope Time Window: Auto scales to LFO period, or choose a fixed window (1s to 24h)."
            } else {
                "Oscilloscope Time Window: Choose a fixed window (1s to 24h)."
            }
            ImGui.setTooltip(tooltip)
        }
        ImGui.popItemWidth()

        ImGui.sameLine(0f, 8f * fontScale)
        val infoLabel = if (currentTimebase == ScopeTimebase.AUTO) {
            "Auto (${ScopeTimebase.formatTimeOffset(totalDuration).removePrefix("+")})"
        } else {
            "${ScopeTimebase.formatTimeOffset(totalDuration).removePrefix("+")} (${ScopeTimebase.formatTimeOffset(divSec).removePrefix("+")}/div)"
        }
        session.uiTheme.captionColored(0.75f, 0.78f, 0.82f, 0.95f, infoLabel)

        // Master Cell Mute / Live Toggle Button at upper-right of control bar
        if (activeMods.isNotEmpty()) {
            val isMuted = activeMods.all { it.bypassed }
            val btnText = if (isMuted) "[ MUTED ]" else "[ LIVE ]"
            val btnW = (ImGui.calcTextSize(btnText).x + 16f * fontScale).coerceAtLeast(60f * fontScale)
            val btnH = ImGui.getFrameHeight()

            ImGui.sameLine(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - btnW)
            if (isMuted) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.1f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.9f, 0.7f, 0.2f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.3f, 1f))
            } else {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.1f, 0.5f, 0.4f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.5f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 0.6f, 1f))
            }

            if (ImGui.button(btnText, btnW, btnH)) {
                val targetBypassed = !isMuted
                val updated = param.modulators.map { mod ->
                    if (activeMods.any { it.id == mod.id }) mod.copy(bypassed = targetBypassed) else mod
                }
                param.modulators.clear()
                param.modulators.addAll(updated)
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip(if (isMuted) "Unmute cell modulation (Route to Value)" else "Mute cell modulation (Preview on O-scope)")
            }
            ImGui.popStyleColor(3)
        }
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
        hasLfo: Boolean
    ) {
        if (!session.uiTheme.tooltipsEnabled) return
        val io = ImGui.getIO()
        val mx = io.mousePos.x
        val my = io.mousePos.y

        if (mx in startX..(startX + w) && my in startY..(startY + h)) {
            if (!hasLfo) {
                if (abs(mx - nowX) <= 10f) {
                    ImGui.setTooltip("Playhead (NOW):\nCurrent live value.")
                } else {
                    ImGui.setTooltip("Waveform History:\nRecorded CV trajectory leading into current value (-${ScopeTimebase.formatTimeOffset(totalDuration).removePrefix("-").removePrefix("+")} to NOW).")
                }
            } else {
                // LFO scope is always 50:50 — past and future spans are equal
                val halfSpan = totalDuration * 0.5f
                if (abs(mx - nowX) <= 6f) {
                    ImGui.setTooltip("Playhead (NOW):\nCurrent parameter value & phase.")
                } else if (mx < nowX) {
                    ImGui.setTooltip("Waveform Lookback:\nModulation trajectory leading into current phase (-${ScopeTimebase.formatTimeOffset(halfSpan).removePrefix("-").removePrefix("+")} to NOW).")
                } else {
                    ImGui.setTooltip("Waveform Lookahead:\nProjected modulation trajectory ahead of current phase (NOW to +${ScopeTimebase.formatTimeOffset(halfSpan).removePrefix("+")}).")
                }
            }
        }
    }





    /**
     * Renders one half (past or future) of the LFO waveform using Halton-jittered
     * anti-aliasing. Samples [tOrigin, tFinal] analytically using [getCombinedEffectiveValueAtOffset],
     * drawing each step either as a thick bar (when min–max spread > 2.5px) or a line segment.
     *
     * Returns the (x, y) pixel coordinates of the last drawn endpoint so the adjacent
     * segment can start exactly where this one ended for seamless continuity at nowX.
     */
    private fun drawLfoWaveSegments(
        dl: imgui.ImDrawList,
        mods: List<CvModulator>,
        isBipolar: Boolean,
        startY: Float,
        h: Float,
        usableHeight: Float,
        segStartX: Float,
        stepX: Float,
        steps: Int,
        tOrigin: Double,
        tFinal: Double,
        color: Int,
        lineWidth: Float,
        initPrevX: Float,
        initPrevY: Float
    ): Pair<Float, Float> {
        var prevX = initPrevX
        var prevY = initPrevY
        val tSpan = tFinal - tOrigin
        for (s in 0 until steps) {
            val tStart = tOrigin + tSpan * s / steps
            val tEnd   = tOrigin + tSpan * (s + 1) / steps
            val x = segStartX + (s + 1) * stepX

            var stepMin = Float.POSITIVE_INFINITY
            var stepMax = Float.NEGATIVE_INFINITY
            for (k in 0..12) {
                val frac = if (k == 0) 0.0 else (k * PHI_FRAC) % 1.0
                val t = tStart + (tEnd - tStart) * frac
                val v = getCombinedEffectiveValueAtOffset(mods, isBipolar, t, includeBypassed = true)
                if (v < stepMin) stepMin = v
                if (v > stepMax) stepMax = v
            }

            val normMin = if (isBipolar) (stepMin + 1f) / 2f else stepMin.coerceIn(0f, 1f)
            val normMax = if (isBipolar) (stepMax + 1f) / 2f else stepMax.coerceIn(0f, 1f)
            val yTop    = (startY + h - 6f) - normMax * usableHeight
            val yBottom = (startY + h - 6f) - normMin * usableHeight

            if (abs(yBottom - yTop) > 2.5f) {
                dl.addLine(x - stepX * 0.5f, yTop, x - stepX * 0.5f, yBottom, color, stepX + 0.5f)
                prevX = x
                prevY = (yTop + yBottom) / 2f
            } else {
                val yMid = (yTop + yBottom) / 2f
                dl.addLine(prevX, prevY, x, yMid, color, lineWidth)
                prevX = x
                prevY = yMid
            }
        }
        return Pair(prevX, prevY)
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
