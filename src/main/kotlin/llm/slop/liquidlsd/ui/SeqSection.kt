package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiKey
import imgui.type.ImInt
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.seqCyclePosition
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.GenUnit
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.SeqPatterns
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Dedicated UI section for the Step Sequencer CV modulator ("seq").
 * Layout: clock unit + sync, step interval, length + phase, pattern toolbar (randomize, invert, shift,
 * copy/paste, fills, euclid, clear), a paintable bar-graph step editor with a live playhead, glide
 * (hold) dynamics, and depth/offset.
 */
object SeqSection {
    private const val FONT_SCALE = 0.95f
    private const val BAR_HEIGHT = 64f
    private const val MAX_PER_ROW = 16
    private const val MIN_PER_ROW = 8

    private val unitLabels = GenUnit.entries.map { it.name }.toTypedArray()
    private val unitIdxWrapper = ImInt()

    /** In-memory pattern clipboard shared by every sequencer (steps, length). */
    private var clipboard: Pair<List<Float>, Int>? = null

    private val euclidHits = intArrayOf(3)
    private val euclidRotation = intArrayOf(0)

    private val SNAP_LABELS = listOf("Off" to 0f, "5%" to 0.05f, "10%" to 0.1f, "12.5%" to 0.125f, "25%" to 0.25f)

    /** Paint/nudge grid in step-value units (0 = free). A view preference, not saved with the pattern. */
    private var snapStep = SeqPatterns.SNAP

    // Keyboard selection: which step of which sequencer the arrow keys act on.
    private var selectedStep = -1
    private var selectedFor = ""

    // Painting state: previous painted step/value so fast drags fill the gap between frames.
    private var lastPaintIdx = -1
    private var lastPaintVal = 0f

    // Numeric entry popup (double-click a bar).
    private var editStep = -1
    private var editFocusPending = false
    private val editBuf = ImString(16)

    private fun withAlpha(col: Int, a: Float): Int {
        val alpha = (((col ushr 24) and 0xFF) * a).roundToInt().coerceIn(0, 255)
        return (col and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun unitSeconds(unit: GenUnit, v: Float): Double = when (unit) {
        GenUnit.TIME -> v.toDouble()
        GenUnit.BEAT -> v * 60.0 / CVRegistry.get("bpm").toDouble().coerceIn(20.0, 300.0)
        GenUnit.FRAME -> v / CVRegistry.getTargetFps().toDouble().coerceAtLeast(1.0)
    }

    private fun fromSeconds(unit: GenUnit, sec: Double): Float = when (unit) {
        GenUnit.TIME -> sec.toFloat()
        GenUnit.BEAT -> (sec * CVRegistry.get("bpm").toDouble().coerceIn(20.0, 300.0) / 60.0).toFloat()
        GenUnit.FRAME -> (sec * CVRegistry.getTargetFps().toDouble()).toFloat()
    }

    private fun intervalLimits(unit: GenUnit): Pair<Float, Float> = when (unit) {
        GenUnit.BEAT -> 0.03125f to 8.0f
        GenUnit.TIME -> 0.01f to 5.0f
        GenUnit.FRAME -> 1.0f to 120.0f
    }

    fun draw(
        session: SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        paramKey: String,
        modulatorIndex: Int,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        val isBipolar = param.minClamp < 0f
        val stepMin = if (isBipolar) -1.0f else 0.0f
        val stepCount = existing.seqStepCount.coerceIn(1, SeqPatterns.MAX_STEPS)

        drawTiming(session, param, existing, paramKey, modulatorIndex, themeColor, onReplace)
        drawLengthAndPhase(session, param, existing, paramKey, modulatorIndex, themeColor, stepCount, onReplace)
        ImGui.spacing()
        drawToolbar(session, param, existing, stepCount, isBipolar, themeColor, onReplace)
        ImGui.spacing()

        val cyclePos = seqCyclePosition(existing, anchors = param.clockAnchors)
        val activeStep = Math.floorMod(floor(cyclePos).toLong(), stepCount.toLong()).toInt()
        val stepFrac = (cyclePos - floor(cyclePos)).toFloat()
        drawStepBars(existing, stepCount, stepMin, activeStep, stepFrac, themeColor, onReplace)
        ImGui.spacing()

        drawGlide(session, param, existing, paramKey, modulatorIndex, themeColor, onReplace)
        drawDepthOffset(session, param, existing, paramKey, modulatorIndex, themeColor, onReplace)
    }

    // ── Timing ──────────────────────────────────────────────────────────────────────────────

    private fun drawTiming(
        session: SessionContext, param: ModulatableParameter, existing: CvModulator,
        paramKey: String, modulatorIndex: Int, themeColor: Int, onReplace: (CvModulator) -> Unit
    ) {
        val id = existing.id
        session.uiTheme.body("Clock Unit:")
        ImGui.sameLine(0f, 8f * FONT_SCALE)

        val units = GenUnit.entries
        unitIdxWrapper.set(units.indexOf(existing.genUnit).coerceAtLeast(0))
        ImGui.pushItemWidth(110f * FONT_SCALE)
        val unitChanged = ImGui.combo("##seq_unit_$id", unitIdxWrapper, unitLabels)
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "seq_unit" }
        if (unitChanged) {
            // Carry the step length across units (same duration), clamped to the new unit's range.
            val next = units[unitIdxWrapper.get()]
            val (lo, hi) = intervalLimits(next)
            val converted = fromSeconds(next, unitSeconds(existing.genUnit, existing.subdivision)).coerceIn(lo, hi)
            onReplace(existing.copy(
                genUnit = next, subdivision = converted, subdivisionMin = converted, subdivisionMax = converted,
                randomizeSubdivision = false
            ))
        }
        itemTooltip("Clock for the step interval. Changing it keeps the same step duration where possible.")
        ImGui.popItemWidth()

        ImGui.sameLine(0f, 16f * FONT_SCALE)
        if (ButtonChrome.button("Sync to Step 1##seq_reset_$id")) {
            val unitDiv = when (existing.genUnit) {
                GenUnit.TIME -> CVRegistry.getElapsedRealtimeSec() / existing.subdivision.toDouble().coerceAtLeast(0.001)
                GenUnit.BEAT -> CVRegistry.getSynchronizedTotalBeats() / existing.subdivision.toDouble().coerceAtLeast(0.001)
                GenUnit.FRAME -> CVRegistry.getRenderFrameCount().toDouble() / existing.subdivision.toDouble().coerceAtLeast(1.0)
            }
            val n = existing.seqStepCount.coerceIn(1, SeqPatterns.MAX_STEPS)
            val phase = Math.floorMod(-floor(unitDiv).toLong(), n.toLong()).toFloat() - (unitDiv - floor(unitDiv)).toFloat()
            onReplace(existing.copy(phaseOffset = phase, phaseOffsetMin = phase, phaseOffsetMax = phase, randomizePhaseOffset = false))
        }
        itemTooltip("Restart the pattern now: the playhead jumps to the start of step 1 (sets Phase).")

        ImGui.spacing()

        val (lo, hi) = intervalLimits(existing.genUnit)
        if (existing.genUnit == GenUnit.BEAT) {
            BeatPresetPills.draw(session, id, themeColor, existing.subdivision, BeatPresetPills.SEQ_PRESETS) { value ->
                onReplace(existing.copy(subdivision = value, subdivisionMin = value, subdivisionMax = value))
            }
            ImGui.spacing()
        }

        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = id,
            label = when (existing.genUnit) {
                GenUnit.BEAT -> "Step Interval (Beats)"
                GenUnit.TIME -> "Step Duration"
                GenUnit.FRAME -> "Frames Per Step"
            },
            themeColor = themeColor,
            currentValue = existing.subdivision,
            currentMin = existing.subdivisionMin,
            currentMax = existing.subdivisionMax,
            minLimit = lo,
            maxLimit = hi,
            defaultValue = if (existing.genUnit == GenUnit.FRAME) 4.0f else 0.25f,
            isRandomizable = existing.randomizeSubdivision,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = when (existing.genUnit) {
                GenUnit.BEAT -> { v: Float -> "%.3f b".format(v) }
                GenUnit.TIME -> { v: Float -> "%.3fs".format(v) }
                GenUnit.FRAME -> { v: Float -> "%.0f f".format(v) }
            },
            onRandomizableChanged = { checked -> onReplace(existing.copy(randomizeSubdivision = checked)) },
            onRandomizeNow = { onReplace(existing.randomizeSubdivision()) },
            onRangeChanged = { min, max -> onReplace(existing.copy(subdivisionMin = min, subdivisionMax = max)) },
            onValueChanged = { v -> onReplace(existing.copy(subdivision = v, subdivisionMin = v, subdivisionMax = v)) },
            paramKey = paramKey,
            modulatorIndex = modulatorIndex,
            modulatorId = id,
            propertyName = "subdivision"
        )
    }

    private fun drawLengthAndPhase(
        session: SessionContext, param: ModulatableParameter, existing: CvModulator,
        paramKey: String, modulatorIndex: Int, themeColor: Int, stepCount: Int, onReplace: (CvModulator) -> Unit
    ) {
        val id = existing.id
        ImGui.spacing()
        CustomRangeSlider.drawCompactSlider(
            session = session,
            label = "Length",
            currentValue = stepCount.toFloat(),
            minLimit = 1f,
            maxLimit = SeqPatterns.MAX_STEPS.toFloat(),
            defaultValue = 16f,
            idPrefix = id,
            themeColor = themeColor,
            formatValue = { "${it.roundToInt()} steps" },
            onValueChanged = { v ->
                val n = v.roundToInt().coerceIn(1, SeqPatterns.MAX_STEPS)
                if (n != existing.seqStepCount) {
                    val wrapped = Math.floorMod(existing.phaseOffset.roundToInt(), n).toFloat()
                    onReplace(existing.copy(seqStepCount = n, phaseOffset = wrapped, phaseOffsetMin = wrapped, phaseOffsetMax = wrapped))
                }
            }
        )
        itemTooltip("Pattern length, 1 to 32 steps. Steps past the end are kept, so lengthening again brings them back.")

        val phaseCbs = cvModulatorSlider(
            existing = existing,
            getValue = { phaseOffset }, getMin = { phaseOffsetMin }, getMax = { phaseOffsetMax },
            minLimit = 0f, maxLimit = stepCount.toFloat(), defaultOffset = 1f,
            copyWithRandomize = { enabled, nMin, nMax -> copy(randomizePhaseOffset = enabled, phaseOffsetMin = nMin, phaseOffsetMax = nMax) },
            copyWithRange = { sMin, sMax, v -> copy(phaseOffsetMin = sMin, phaseOffsetMax = sMax, phaseOffset = v) },
            copyWithValue = { v -> copy(phaseOffset = v, phaseOffsetMin = v, phaseOffsetMax = v) },
            randomizeNow = { randomizePhaseOffset() },
            onReplace = onReplace,
        )
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = "${id}_phase",
            label = "Phase",
            themeColor = themeColor,
            paramKey = paramKey, modulatorIndex = modulatorIndex, propertyName = "phaseOffset",
            currentValue = existing.phaseOffset,
            currentMin = existing.phaseOffsetMin,
            currentMax = existing.phaseOffsetMax,
            minLimit = 0f, maxLimit = stepCount.toFloat(), defaultValue = 0f,
            isRandomizable = existing.randomizePhaseOffset,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = { "%.2f steps".format(it) },
            onRandomizableChanged = phaseCbs.onRandomizableChanged,
            onRandomizeNow = phaseCbs.onRandomizeNow,
            onRangeChanged = phaseCbs.onRangeChanged,
            onValueChanged = phaseCbs.onValueChanged,
        )
        itemTooltip("Start the pattern part-way through. Useful to offset several sequencers against each other.")
    }

    // ── Pattern toolbar ─────────────────────────────────────────────────────────────────────

    private fun drawToolbar(
        session: SessionContext, param: ModulatableParameter, existing: CvModulator,
        stepCount: Int, bipolar: Boolean, themeColor: Int, onReplace: (CvModulator) -> Unit
    ) {
        val id = existing.id
        val gap = 6f * FONT_SCALE
        val padX = ImGui.getStyle().framePaddingX
        var first = true

        fun place(w: Float) {
            if (!first && ImGui.getContentRegionAvailX() >= w + gap) ImGui.sameLine(0f, gap)
            first = false
        }
        fun button(label: String, tip: String): Boolean {
            place(ImGui.calcTextSize(label.substringBefore("##")).x + padX * 2)
            val hit = ButtonChrome.button("$label##seq_tb_${id}_$label")
            itemTooltip(tip)
            return hit
        }
        fun edit(newSteps: List<Float>) = onReplace(existing.copy(seqSteps = newSteps))
        val steps = existing.seqSteps

        if (button("Randomize", "Fill the active steps with random values.")) {
            edit(SeqPatterns.randomize(steps, stepCount, bipolar))
        }
        if (session.uiTheme.randomizationEnabled && !param.isRandomizeDisabled) {
            place(ImGui.calcTextSize("Auto").x + padX * 2)
            if (ButtonChrome.toggleButton("Auto##seq_auto_$id", existing.randomizeSeqSteps, themeColor)) {
                onReplace(existing.copy(randomizeSeqSteps = !existing.randomizeSeqSteps))
            }
            itemTooltip("When on, randomizing a preset or deck also re-rolls this pattern (and morphs between patterns).")
        }
        if (button("Invert", "Flip every active step (1 - value, or negate when bipolar).")) {
            edit(SeqPatterns.invert(steps, stepCount, bipolar))
        }
        if (button("Shift <", "Rotate the pattern one step earlier.")) edit(SeqPatterns.rotate(steps, stepCount, -1))
        if (button("Shift >", "Rotate the pattern one step later.")) edit(SeqPatterns.rotate(steps, stepCount, 1))
        if (button("Copy", "Copy this pattern (steps and length) to the sequencer clipboard.")) {
            clipboard = SeqPatterns.padded(steps) to stepCount
        }
        val clip = clipboard
        if (clip == null) ImGui.beginDisabled()
        if (button("Paste", "Paste the copied pattern here, including its length.") && clip != null) {
            onReplace(existing.copy(seqSteps = SeqPatterns.padded(clip.first), seqStepCount = clip.second))
        }
        if (clip == null) ImGui.endDisabled()

        place(80f * FONT_SCALE + padX * 2)
        ImGui.pushItemWidth(80f * FONT_SCALE)
        if (ImGui.beginCombo("##seq_fill_$id", "Fill...")) {
            for (shape in SeqPatterns.Shape.entries) {
                if (ImGui.selectable(shape.label)) edit(SeqPatterns.shape(steps, stepCount, bipolar, shape))
            }
            ImGui.endCombo()
        }
        ImGui.popItemWidth()
        itemTooltip("Replace the active steps with a preset shape.")

        if (button("Euclid...", "Spread N hits evenly across the pattern (classic euclidean rhythm).")) {
            euclidHits[0] = euclidHits[0].coerceIn(0, stepCount)
            euclidRotation[0] = euclidRotation[0].coerceIn(0, stepCount - 1)
            ImGui.openPopup("seq_euclid_$id")
        }
        if (ImGui.beginPopup("seq_euclid_$id")) {
            ImGui.pushItemWidth(160f)
            ImGui.sliderInt("Hits##seq_euclid_hits_$id", euclidHits, 0, stepCount)
            ImGui.sliderInt("Rotate##seq_euclid_rot_$id", euclidRotation, 0, maxOf(0, stepCount - 1))
            ImGui.popItemWidth()
            val pattern = SeqPatterns.euclid(steps, stepCount, euclidHits[0], euclidRotation[0])
            val dl = ImGui.getWindowDrawList()
            val px = ImGui.getCursorScreenPosX()
            val py = ImGui.getCursorScreenPosY()
            val cell = 12f
            for (i in 0 until stepCount) {
                val x = px + i * (cell + 2f)
                val col = if (pattern[i] > 0.5f) themeColor else withAlpha(ImGui.getColorU32(ImGuiCol.Text), 0.2f)
                dl.addRectFilled(x, py, x + cell, py + cell, col, 2f)
            }
            ImGui.dummy(stepCount * (cell + 2f), cell + 2f)
            if (ButtonChrome.button("Apply##seq_euclid_apply_$id")) {
                edit(pattern)
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }

        if (button("Clear", "Reset all 32 step values to 0.")) edit(List(SeqPatterns.MAX_STEPS) { 0.0f })

        place(92f * FONT_SCALE + padX * 2)
        ImGui.pushItemWidth(92f * FONT_SCALE)
        val snapLabel = SNAP_LABELS.firstOrNull { abs(it.second - snapStep) < 1e-4f }?.first ?: "Custom"
        if (ImGui.beginCombo("##seq_snap_$id", "Snap: $snapLabel")) {
            for ((label, v) in SNAP_LABELS) {
                if (ImGui.selectable(label, abs(v - snapStep) < 1e-4f)) snapStep = v
            }
            ImGui.endCombo()
        }
        ImGui.popItemWidth()
        itemTooltip("Grid that painting and nudging snap to. Hold Shift while painting to ignore it.")
    }

    // ── Bar-graph step editor ───────────────────────────────────────────────────────────────

    private fun drawStepBars(
        existing: CvModulator, count: Int, stepMin: Float, activeStep: Int, stepFrac: Float, themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        val id = existing.id
        val bipolar = stepMin < 0f
        val scale = ValueFormat.scaleFor(stepMin, 1f)
        val working = SeqPatterns.padded(existing.seqSteps)
        var dirty = false
        fun setStep(i: Int, v: Float) {
            if (i in 0 until count && working[i] != v) { working[i] = v; dirty = true }
        }

        val dl = ImGui.getWindowDrawList()
        val io = ImGui.getIO()
        val dim = if (existing.bypassed) 0.4f else 1f
        val perRow = minOf(count, MAX_PER_ROW).coerceAtLeast(MIN_PER_ROW)
        // Steps past the length that still hold data are drawn ghosted so shortening is visibly reversible.
        val lastData = (SeqPatterns.MAX_STEPS - 1 downTo 0).firstOrNull { working[it] != 0f } ?: -1
        val slots = maxOf(count, lastData + 1)
        val rows = (slots + perRow - 1) / perRow
        val availW = ImGui.getContentRegionAvailX()
        val cellW = availW / perRow
        val h = BAR_HEIGHT
        val bgCol = ImGui.getColorU32(ImGuiCol.FrameBg)
        val bgAltCol = ImGui.getColorU32(ImGuiCol.FrameBgHovered)
        val textCol = ImGui.getColorU32(ImGuiCol.Text)
        fun yFor(y0: Float, v: Float): Float =
            if (bipolar) y0 + h / 2f - v.coerceIn(-1f, 1f) * (h / 2f) else y0 + h - v.coerceIn(0f, 1f) * h

        val editorX0 = ImGui.getCursorScreenPosX()
        val editorY0 = ImGui.getCursorScreenPosY()

        for (row in 0 until rows) {
            val x0 = ImGui.getCursorScreenPosX()
            val y0 = ImGui.getCursorScreenPosY()
            val first = row * perRow
            val lastSlot = minOf(slots, first + perRow) - 1
            val last = minOf(count, first + perRow) - 1 // last playable step in this row (< first if none)
            var hovered = false
            var mouseIdx = first

            if (last >= first) {
                val rowW = cellW * (last - first + 1)
                ImGui.invisibleButton("##seq_bars_${id}_$row", rowW, h)
                llm.slop.liquidlsd.tour.WidgetRegistry.note { "seq_bars_$row" }
                hovered = ImGui.isItemHovered()
                val dragging = ImGui.isItemActive() && ImGui.isMouseDown(0)
                mouseIdx = (first + floor((io.mousePosX - x0) / cellW).toInt()).coerceIn(first, last)

                if (dragging) {
                    val t = ((io.mousePosY - y0) / h).coerceIn(0f, 1f)
                    var v = if (bipolar) 1f - 2f * t else 1f - t
                    if (!io.keyShift) v = SeqPatterns.snap(v, stepMin, 1f, snapStep)
                    if (lastPaintIdx in 0 until count && abs(mouseIdx - lastPaintIdx) > 1) {
                        val span = abs(mouseIdx - lastPaintIdx)
                        val dir = if (mouseIdx > lastPaintIdx) 1 else -1
                        for (k in 1 until span) {
                            setStep(lastPaintIdx + dir * k, lastPaintVal + (v - lastPaintVal) * k / span)
                        }
                    }
                    setStep(mouseIdx, v)
                    lastPaintIdx = mouseIdx
                    lastPaintVal = v
                    selectedStep = mouseIdx
                    selectedFor = id
                }

                if (hovered && !dragging) {
                    if (ImGui.isMouseClicked(1) || ImGui.isMouseClicked(2)) setStep(mouseIdx, 0f)
                    if (ImGui.isMouseDoubleClicked(0)) openStepEditor(id, mouseIdx, working[mouseIdx], scale)
                    if (io.mouseWheel != 0f) {
                        val d = if (io.keyShift) 0.01f else if (io.keyCtrl) 0.2f else if (snapStep > 0f) snapStep else 0.05f
                        setStep(mouseIdx, (working[mouseIdx] + io.mouseWheel * d).coerceIn(stepMin, 1f))
                        io.mouseWheel = 0f
                    }
                    showTooltip(
                        "Step ${mouseIdx + 1}: ${ValueFormat.format(working[mouseIdx], scale)}\n" +
                            "Click/drag to paint (Shift = no snap). Wheel to nudge.\n" +
                            "Arrow keys edit the selected step. Double-click to type. Right-click to reset."
                    )
                }
            } else {
                ImGui.dummy(availW, h)
            }

            for (idx in first..lastSlot) {
                val x = x0 + (idx - first) * cellW
                val ghost = idx >= count
                val isActive = !ghost && idx == activeStep
                val groupAlt = (idx / 4) % 2 == 1
                dl.addRectFilled(x + 1f, y0, x + cellW - 1f, y0 + h, withAlpha(if (groupAlt) bgAltCol else bgCol, if (ghost) 0.35f else dim), 2f)

                val v = working[idx].coerceIn(stepMin, 1f)
                val base = if (bipolar) y0 + h / 2f else y0 + h
                val top = yFor(y0, v)
                val barCol = if (ghost) withAlpha(textCol, 0.18f) else withAlpha(themeColor, (if (isActive) 1f else 0.62f) * dim)
                if (abs(top - base) < 2f) {
                    dl.addRectFilled(x + 2f, base - 1f, x + cellW - 2f, base + 1f, barCol)
                } else {
                    dl.addRectFilled(x + 2f, minOf(top, base), x + cellW - 2f, maxOf(top, base), barCol, 1.5f)
                }
                if (bipolar) dl.addLine(x + 1f, base, x + cellW - 1f, base, withAlpha(textCol, 0.25f * dim))
                if (idx % 4 == 0 && cellW >= 22f) {
                    dl.addText(x + 4f, y0 + 2f, withAlpha(textCol, (if (ghost) 0.25f else 0.45f) * dim), (idx + 1).toString())
                }
                if (isActive) {
                    dl.addRect(x + 0.5f, y0 - 1f, x + cellW - 0.5f, y0 + h + 1f, withAlpha(themeColor, dim), 2f, 0, 2.0f)
                } else if (!ghost && selectedFor == id && idx == selectedStep) {
                    dl.addRect(x + 1.5f, y0 + 0.5f, x + cellW - 1.5f, y0 + h - 0.5f, withAlpha(textCol, 0.9f * dim), 2f, 0, 1.5f)
                } else if (hovered && idx == mouseIdx) {
                    dl.addRect(x + 1f, y0, x + cellW - 1f, y0 + h, withAlpha(textCol, 0.35f * dim), 2f, 0, 1.0f)
                }
            }

            // Glide trace: the line the output actually follows (flat steps when Hold is 100%).
            if (last >= first) {
                val traceCol = withAlpha(textCol, 0.85f * dim)
                val samples = if (existing.seqHold >= 0.999f) 1 else 12
                var px = Float.NaN
                var py = 0f
                for (idx in first..last) {
                    val cur = working[idx]
                    val next = working[(idx + 1) % count]
                    for (k in 0..samples) {
                        val f = k / samples.toFloat()
                        val x = x0 + (idx - first + f) * cellW
                        val y = yFor(y0, SeqPatterns.glide(cur, next, f, existing.seqHold, existing.seqCurveSmooth))
                        if (!px.isNaN()) dl.addLine(px, py, x, y, traceCol, 1.5f)
                        px = x
                        py = y
                    }
                }
                if (activeStep in first..last) {
                    val cur = working[activeStep]
                    val next = working[(activeStep + 1) % count]
                    val mx = x0 + (activeStep - first + stepFrac) * cellW
                    val my = yFor(y0, SeqPatterns.glide(cur, next, stepFrac, existing.seqHold, existing.seqCurveSmooth))
                    dl.addCircleFilled(mx, my, 4f, withAlpha(themeColor, dim))
                    dl.addCircle(mx, my, 4f, withAlpha(textCol, dim), 12, 1.5f)
                }
            }
            ImGui.dummy(0f, 3f)
        }

        // Keyboard editing of the selected step while the pointer is over the editor.
        val editorY1 = ImGui.getCursorScreenPosY()
        if (selectedFor == id && selectedStep in 0 until count && !io.wantTextInput &&
            ImGui.isMouseHoveringRect(editorX0, editorY0, editorX0 + availW, editorY1)
        ) {
            val nudge = if (io.keyShift) 0.01f else if (snapStep > 0f) snapStep else 0.05f
            if (ImGui.isKeyPressed(ImGuiKey.LeftArrow)) selectedStep = (selectedStep - 1).coerceAtLeast(0)
            if (ImGui.isKeyPressed(ImGuiKey.RightArrow)) selectedStep = (selectedStep + 1).coerceAtMost(count - 1)
            if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) setStep(selectedStep, (working[selectedStep] + nudge).coerceIn(stepMin, 1f))
            if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) setStep(selectedStep, (working[selectedStep] - nudge).coerceIn(stepMin, 1f))
            if (ImGui.isKeyPressed(ImGuiKey.Delete) || ImGui.isKeyPressed(ImGuiKey.Backspace)) setStep(selectedStep, 0f)
            if (ImGui.isKeyPressed(ImGuiKey.Enter)) openStepEditor(id, selectedStep, working[selectedStep], scale)
        }

        if (ImGui.beginPopup("seq_edit_$id")) {
            if (editFocusPending) { ImGui.setKeyboardFocusHere(); editFocusPending = false }
            ImGui.text("Step ${editStep + 1}")
            ImGui.sameLine()
            ImGui.pushItemWidth(80f)
            val flags = ImGuiInputTextFlags.EnterReturnsTrue or ImGuiInputTextFlags.AutoSelectAll
            if (ImGui.inputText("##seq_edit_val_$id", editBuf, flags)) {
                editBuf.get().toFloatOrNull()?.let { setStep(editStep, (it / scale).coerceIn(stepMin, 1f)) }
                ImGui.closeCurrentPopup()
            }
            ImGui.popItemWidth()
            ImGui.endPopup()
        }

        if (!ImGui.isMouseDown(0)) lastPaintIdx = -1
        if (dirty) onReplace(existing.copy(seqSteps = working))
    }

    private fun openStepEditor(id: String, step: Int, value: Float, scale: Float) {
        editStep = step
        editFocusPending = true
        editBuf.set(ValueFormat.format(value, scale))
        ImGui.openPopup("seq_edit_$id")
    }

    // ── Glide + output scaling ──────────────────────────────────────────────────────────────

    private fun drawGlide(
        session: SessionContext, param: ModulatableParameter, existing: CvModulator,
        paramKey: String, modulatorIndex: Int, themeColor: Int, onReplace: (CvModulator) -> Unit
    ) {
        val id = existing.id
        val holdCbs = cvModulatorSlider(
            existing = existing,
            getValue = { seqHold }, getMin = { seqHoldMin }, getMax = { seqHoldMax },
            minLimit = 0f, maxLimit = 1f, defaultOffset = 0.25f,
            copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeSeqHold = enabled, seqHoldMin = nMin, seqHoldMax = nMax) },
            copyWithRange = { sMin, sMax, v -> copy(seqHoldMin = sMin, seqHoldMax = sMax, seqHold = v) },
            copyWithValue = { v -> copy(seqHold = v, seqHoldMin = v, seqHoldMax = v) },
            randomizeNow = { randomizeSeqHold() },
            onReplace = onReplace,
        )
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = "${id}_hold",
            label = "Step Hold",
            themeColor = themeColor,
            paramKey = paramKey, modulatorIndex = modulatorIndex, propertyName = "seqHold",
            currentValue = existing.seqHold,
            currentMin = existing.seqHoldMin,
            currentMax = existing.seqHoldMax,
            minLimit = 0f, maxLimit = 1f, defaultValue = 1f,
            isRandomizable = existing.randomizeSeqHold,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = {
                when {
                    it >= 0.999f -> "100% (stepped)"
                    it <= 0.001f -> "0% (full glide)"
                    else -> "${(it * 100f).roundToInt()}%"
                }
            },
            onRandomizableChanged = holdCbs.onRandomizableChanged,
            onRandomizeNow = holdCbs.onRandomizeNow,
            onRangeChanged = holdCbs.onRangeChanged,
            onValueChanged = holdCbs.onValueChanged,
        )
        itemTooltip("How much of each step is held flat before gliding to the next.\n100% = instant jumps (no glide).\n0% = continuous glide over the whole step.\n50% = hold half the step, glide the rest.")

        val gliding = existing.seqHold < 0.999f
        if (!gliding) ImGui.beginDisabled()
        session.uiTheme.body("Glide Curve:")
        ImGui.sameLine(0f, 10f * FONT_SCALE)
        for ((smooth, label, tip) in listOf(
            Triple(false, "Linear", "Linear glide between steps."),
            Triple(true, "Smooth", "Cosine / S-curve easing between steps.")
        )) {
            if (smooth) ImGui.sameLine(0f, 6f * FONT_SCALE)
            val selected = existing.seqCurveSmooth == smooth
            if (ButtonChrome.toggleButton("$label##seq_curve_${id}_$label", selected, themeColor, 70f * FONT_SCALE, 0f)) {
                onReplace(existing.copy(seqCurveSmooth = smooth))
            }
            itemTooltip(tip)
        }
        if (!gliding) ImGui.endDisabled()
        ImGui.spacing()
    }

    private fun drawDepthOffset(
        session: SessionContext, param: ModulatableParameter, existing: CvModulator,
        paramKey: String, modulatorIndex: Int, themeColor: Int, onReplace: (CvModulator) -> Unit
    ) {
        val id = existing.id
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = id,
            label = "Depth",
            themeColor = themeColor,
            currentValue = existing.depth,
            currentMin = existing.depthMin,
            currentMax = existing.depthMax,
            minLimit = 0f,
            maxLimit = 1f,
            defaultValue = 1f,
            isRandomizable = existing.randomizeDepth,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = ValueFormat.AUTO,
            onRandomizableChanged = { checked -> onReplace(existing.copy(randomizeDepth = checked)) },
            onRandomizeNow = { onReplace(existing.randomizeDepth()) },
            onRangeChanged = { min, max -> onReplace(existing.copy(depthMin = min, depthMax = max)) },
            onValueChanged = { v -> onReplace(existing.copy(depth = v, depthMin = v, depthMax = v)) },
            paramKey = paramKey,
            modulatorIndex = modulatorIndex,
            modulatorId = id,
            propertyName = "depth"
        )

        val offCbs = cvModulatorSlider(
            existing = existing,
            getValue = { dcOffset }, getMin = { dcOffsetMin }, getMax = { dcOffsetMax },
            minLimit = -1f, maxLimit = 1f, defaultOffset = 0.2f,
            copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeDcOffset = enabled, dcOffsetMin = nMin, dcOffsetMax = nMax) },
            copyWithRange = { sMin, sMax, v -> copy(dcOffsetMin = sMin, dcOffsetMax = sMax, dcOffset = v) },
            copyWithValue = { v -> copy(dcOffset = v, dcOffsetMin = v, dcOffsetMax = v) },
            randomizeNow = { randomizeDcOffset() },
            onReplace = onReplace,
        )
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = "${id}_off",
            label = "Offset",
            themeColor = themeColor,
            paramKey = paramKey, modulatorIndex = modulatorIndex, propertyName = "dcOffset",
            currentValue = existing.dcOffset,
            currentMin = existing.dcOffsetMin,
            currentMax = existing.dcOffsetMax,
            minLimit = -1f, maxLimit = 1f, defaultValue = 0f,
            isRandomizable = existing.randomizeDcOffset,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = ValueFormat.AUTO,
            onRandomizableChanged = offCbs.onRandomizableChanged,
            onRandomizeNow = offCbs.onRandomizeNow,
            onRangeChanged = offCbs.onRangeChanged,
            onValueChanged = offCbs.onValueChanged,
        )
        itemTooltip("Added to the pattern after Depth, shifting the whole sequence up or down.")
        ImGui.spacing()
    }
}
