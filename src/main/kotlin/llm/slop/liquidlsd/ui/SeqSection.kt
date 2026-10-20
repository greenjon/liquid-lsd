package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
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

    private val BEAT_PRESETS = listOf(
        "1/16" to 0.0625f, "1/8" to 0.125f, "1/4" to 0.25f, "1/2" to 0.5f,
        "1" to 1.0f, "2" to 2.0f, "4" to 4.0f
    )
    private val unitLabels = GenUnit.entries.map { it.name }.toTypedArray()
    private val unitIdxWrapper = ImInt()

    /** In-memory pattern clipboard shared by every sequencer (steps, length). */
    private var clipboard: Pair<List<Float>, Int>? = null

    private val euclidHits = intArrayOf(3)

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
        drawToolbar(existing, stepCount, isBipolar, onReplace)
        ImGui.spacing()

        val activeStep = Math.floorMod(floor(seqCyclePosition(existing)).toLong(), stepCount.toLong()).toInt()
        drawStepBars(existing, stepCount, stepMin, activeStep, themeColor, onReplace)
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
            session.uiTheme.body("Beat Presets:")
            ImGui.sameLine(0f, 8f * FONT_SCALE)
            BEAT_PRESETS.forEachIndexed { idx, (label, value) ->
                if (idx > 0) ImGui.sameLine(0f, 4f * FONT_SCALE)
                val selected = abs(existing.subdivision - value) < 0.001f
                if (selected) {
                    ButtonChrome.pushColor(themeColor)
                    ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))
                }
                if (ButtonChrome.button("$label##seq_beat_preset_${id}_$idx", 42f * FONT_SCALE, 0f)) {
                    onReplace(existing.copy(subdivision = value, subdivisionMin = value, subdivisionMax = value))
                }
                if (selected) ImGui.popStyleColor(4)
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

    private fun drawToolbar(existing: CvModulator, stepCount: Int, bipolar: Boolean, onReplace: (CvModulator) -> Unit) {
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
            ImGui.openPopup("seq_euclid_$id")
        }
        if (ImGui.beginPopup("seq_euclid_$id")) {
            ImGui.pushItemWidth(160f)
            ImGui.sliderInt("Hits##seq_euclid_hits_$id", euclidHits, 0, stepCount)
            ImGui.popItemWidth()
            if (ButtonChrome.button("Apply##seq_euclid_apply_$id")) {
                edit(SeqPatterns.euclid(steps, stepCount, euclidHits[0]))
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }

        if (button("Clear", "Reset all 32 step values to 0.")) edit(List(SeqPatterns.MAX_STEPS) { 0.0f })
    }

    // ── Bar-graph step editor ───────────────────────────────────────────────────────────────

    private fun drawStepBars(
        existing: CvModulator, count: Int, stepMin: Float, activeStep: Int, themeColor: Int,
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
        val perRow = minOf(count, MAX_PER_ROW)
        val rows = (count + perRow - 1) / perRow
        val availW = ImGui.getContentRegionAvailX()
        val cellW = availW / perRow
        val h = BAR_HEIGHT
        val bgCol = ImGui.getColorU32(ImGuiCol.FrameBg)
        val bgAltCol = ImGui.getColorU32(ImGuiCol.FrameBgHovered)
        val textCol = ImGui.getColorU32(ImGuiCol.Text)

        for (row in 0 until rows) {
            val x0 = ImGui.getCursorScreenPosX()
            val y0 = ImGui.getCursorScreenPosY()
            val first = row * perRow
            val last = minOf(count, first + perRow) - 1
            val rowW = cellW * (last - first + 1)

            ImGui.invisibleButton("##seq_bars_${id}_$row", rowW, h)
            llm.slop.liquidlsd.tour.WidgetRegistry.note { "seq_bars_$row" }
            val hovered = ImGui.isItemHovered()
            val dragging = ImGui.isItemActive() && ImGui.isMouseDown(0)
            val mouseIdx = (first + floor((io.mousePosX - x0) / cellW).toInt()).coerceIn(first, last)

            if (dragging) {
                val t = ((io.mousePosY - y0) / h).coerceIn(0f, 1f)
                var v = if (bipolar) 1f - 2f * t else 1f - t
                if (!io.keyShift) v = SeqPatterns.snap(v, stepMin, 1f)
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
            }

            if (hovered && !dragging) {
                if (ImGui.isMouseClicked(1) || ImGui.isMouseClicked(2)) setStep(mouseIdx, 0f)
                if (ImGui.isMouseDoubleClicked(0)) {
                    editStep = mouseIdx
                    editFocusPending = true
                    editBuf.set(ValueFormat.format(working[mouseIdx], scale))
                    ImGui.openPopup("seq_edit_$id")
                }
                if (io.mouseWheel != 0f) {
                    val d = if (io.keyShift) 0.01f else if (io.keyCtrl) 0.2f else 0.05f
                    setStep(mouseIdx, (working[mouseIdx] + io.mouseWheel * d).coerceIn(stepMin, 1f))
                    io.mouseWheel = 0f
                }
                showTooltip(
                    "Step ${mouseIdx + 1}: ${ValueFormat.format(working[mouseIdx], scale)}\n" +
                        "Click/drag to paint (Shift = no snap). Wheel to nudge.\n" +
                        "Double-click to type. Right-click to reset."
                )
            }

            for (idx in first..last) {
                val x = x0 + (idx - first) * cellW
                val isActive = idx == activeStep
                val groupAlt = (idx / 4) % 2 == 1
                dl.addRectFilled(x + 1f, y0, x + cellW - 1f, y0 + h, if (groupAlt) bgAltCol else bgCol, 2f)

                val v = working[idx].coerceIn(stepMin, 1f)
                val base = if (bipolar) y0 + h / 2f else y0 + h
                val top = if (bipolar) y0 + h / 2f - v * (h / 2f) else y0 + h - v * h
                val barCol = withAlpha(themeColor, if (isActive) 1f else 0.62f)
                if (abs(top - base) < 2f) {
                    dl.addRectFilled(x + 2f, base - 1f, x + cellW - 2f, base + 1f, barCol)
                } else {
                    dl.addRectFilled(x + 2f, minOf(top, base), x + cellW - 2f, maxOf(top, base), barCol, 1.5f)
                }
                if (bipolar) dl.addLine(x + 1f, base, x + cellW - 1f, base, withAlpha(textCol, 0.25f))
                if (idx % 4 == 0 && cellW >= 22f) {
                    dl.addText(x + 4f, y0 + 2f, withAlpha(textCol, 0.45f), (idx + 1).toString())
                }
                if (isActive) {
                    dl.addRect(x + 0.5f, y0 - 1f, x + cellW - 0.5f, y0 + h + 1f, themeColor, 2f, 0, 2.0f)
                } else if (hovered && idx == mouseIdx) {
                    dl.addRect(x + 1f, y0, x + cellW - 1f, y0 + h, withAlpha(textCol, 0.35f), 2f, 0, 1.0f)
                }
            }
            ImGui.dummy(0f, 3f)
        }

        if (!ImGui.isMouseDown(0)) lastPaintIdx = -1

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

        if (dirty) onReplace(existing.copy(seqSteps = working))
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
            if (selected) {
                ButtonChrome.pushColor(themeColor)
                ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))
            }
            if (ButtonChrome.button("$label##seq_curve_${id}_$label", 70f * FONT_SCALE, 0f)) {
                onReplace(existing.copy(seqCurveSmooth = smooth))
            }
            if (selected) ImGui.popStyleColor(4)
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
