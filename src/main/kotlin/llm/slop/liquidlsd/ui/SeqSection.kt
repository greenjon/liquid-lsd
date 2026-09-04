package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt
import imgui.type.ImString
import imgui.callback.ImGuiInputTextCallback
import imgui.ImGuiInputTextCallbackData
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.GenUnit
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.LfoSpeedMode

/**
 * Dedicated UI section for the Step Sequencer CV modulator ("seq").
 * Features an 8-wide step grid (8, 16, or 32 steps) with real-time active step highlight,
 * precise numeric typing and arrow/wheel dial-in, timing/clock unit selectors,
 * hold/glide dynamics slider with curve toggle, and modulation depth/offset.
 */
object SeqSection {

    private class StepInputCallback : ImGuiInputTextCallback() {
        var currentValue: Float = 0f
        var minLimit: Float = 0f
        var maxLimit: Float = 1f
        var onChanged: (Float) -> Unit = {}

        override fun accept(data: ImGuiInputTextCallbackData) {
            val upPressed = data.eventKey == ImGuiKey.UpArrow
            val downPressed = data.eventKey == ImGuiKey.DownArrow
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
                val nextValue = (currentValue + dir * delta).coerceIn(minLimit, maxLimit)
                val formatted = "%.3f".format(nextValue)
                data.deleteChars(0, data.buf.length)
                data.insertChars(0, formatted)
                data.cursorPos = formatted.length
                data.selectionStart = formatted.length
                data.selectionEnd = formatted.length
                data.setBufDirty(true)
                onChanged(nextValue)
            }
        }
    }

    private val textBuffers = mutableMapOf<String, ImString>()
    private val textCallbacks = mutableMapOf<String, StepInputCallback>()
    private val textWidgetActive = mutableMapOf<String, Boolean>()

    private val BEAT_PRESETS = listOf(
        "1/16" to 0.0625f,
        "1/8" to 0.125f,
        "1/4" to 0.25f,
        "1/2" to 0.5f,
        "1" to 1.0f,
        "2" to 2.0f,
        "4" to 4.0f
    )

    fun draw(
        session: SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
        val isBipolar = param.minClamp < 0f
        val stepMinLimit = if (isBipolar) -1.0f else 0.0f
        val stepMaxLimit = 1.0f

        // 1. Clock Unit & Reset Header
        session.uiTheme.body("Clock Unit:")
        ImGui.sameLine(0f, 8f * fontScale)

        val units = GenUnit.values()
        val unitLabels = units.map { it.name }.toTypedArray()
        val currentUnitIdx = units.indexOf(existing.genUnit).coerceAtLeast(0)
        val unitIdxWrapper = ImInt(currentUnitIdx)

        ImGui.pushItemWidth(110f * fontScale)
        if (ImGui.combo("##seq_unit_${existing.id}", unitIdxWrapper, unitLabels)) {
            val nextUnit = units[unitIdxWrapper.get()]
            val defaultSubdiv = when (nextUnit) {
                GenUnit.BEAT -> 0.25f // 1/16th beat default
                GenUnit.TIME -> 0.125f // 125ms default
                GenUnit.FRAME -> 4.0f  // 4 frames default
            }
            onReplace(existing.copy(genUnit = nextUnit, subdivision = defaultSubdiv))
        }
        ImGui.popItemWidth()

        ImGui.sameLine(0f, 16f * fontScale)
        if (ImGui.button("Reset to Step 0##seq_reset_${existing.id}")) {
            val rawPos = when (existing.genUnit) {
                GenUnit.TIME -> CVRegistry.getElapsedRealtimeSec() / existing.subdivision.toDouble().coerceAtLeast(0.001)
                GenUnit.BEAT -> CVRegistry.getSynchronizedTotalBeats() / existing.subdivision.toDouble().coerceAtLeast(0.001)
                GenUnit.FRAME -> CVRegistry.getRenderFrameCount().toDouble() / existing.subdivision.toDouble().coerceAtLeast(1.0)
            }
            val stepCount = existing.seqStepCount.coerceIn(1, 32)
            val currentPhaseRemainder = rawPos % stepCount.toDouble()
            val newPhase = (-currentPhaseRemainder).toFloat()
            onReplace(existing.copy(phaseOffset = newPhase))
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Instantly realign the sequencer playhead to Step 0 at the current moment.")
        }

        ImGui.spacing()

        // 2. Timing Rate Slider
        when (existing.genUnit) {
            GenUnit.BEAT -> {
                // Quick Beat Presets
                session.uiTheme.body("Beat Presets:")
                ImGui.sameLine(0f, 8f * fontScale)
                BEAT_PRESETS.forEachIndexed { idx, (label, value) ->
                    if (idx > 0) ImGui.sameLine(0f, 4f * fontScale)
                    val isSelected = kotlin.math.abs(existing.subdivision - value) < 0.001f
                    if (isSelected) {
                        ImGui.pushStyleColor(ImGuiCol.Button, themeColor)
                        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))
                    }
                    if (ImGui.button("$label##seq_beat_preset_${existing.id}_$idx", 42f * fontScale, 0f)) {
                        onReplace(existing.copy(subdivision = value))
                    }
                    if (isSelected) {
                        ImGui.popStyleColor(2)
                    }
                }
                ImGui.spacing()

                CustomRangeSlider.drawCustomRangeSlider(
                    session = session,
                    idPrefix = existing.id,
                    label = "Step Interval (Beats)",
                    themeColor = themeColor,
                    currentValue = existing.subdivision,
                    currentMin = existing.subdivisionMin,
                    currentMax = existing.subdivisionMax,
                    minLimit = 0.03125f,
                    maxLimit = 8.0f,
                    defaultValue = 0.25f,
                    isRandomizable = existing.randomizeSubdivision,
                    isRandomizeDisabled = param.isRandomizeDisabled,
                    randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                    formatValue = { "%.3f b".format(it) },
                    onRandomizableChanged = { checked -> onReplace(existing.copy(randomizeSubdivision = checked)) },
                    onRandomizeNow = { onReplace(existing.randomizeSubdivision()) },
                    onRangeChanged = { min, max -> onReplace(existing.copy(subdivisionMin = min, subdivisionMax = max)) },
                    onValueChanged = { v -> onReplace(existing.copy(subdivision = v, subdivisionMin = v, subdivisionMax = v)) }
                )
            }
            GenUnit.TIME -> {
                CustomRangeSlider.drawCustomRangeSlider(
                    session = session,
                    idPrefix = existing.id,
                    label = "Step Duration",
                    themeColor = themeColor,
                    currentValue = existing.subdivision,
                    currentMin = existing.subdivisionMin,
                    currentMax = existing.subdivisionMax,
                    minLimit = 0.01f,
                    maxLimit = 5.0f,
                    defaultValue = 0.25f,
                    isRandomizable = existing.randomizeSubdivision,
                    isRandomizeDisabled = param.isRandomizeDisabled,
                    randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                    formatValue = { "%.3fs".format(it) },
                    onRandomizableChanged = { checked -> onReplace(existing.copy(randomizeSubdivision = checked)) },
                    onRandomizeNow = { onReplace(existing.randomizeSubdivision()) },
                    onRangeChanged = { min, max -> onReplace(existing.copy(subdivisionMin = min, subdivisionMax = max)) },
                    onValueChanged = { v -> onReplace(existing.copy(subdivision = v, subdivisionMin = v, subdivisionMax = v)) }
                )
            }
            GenUnit.FRAME -> {
                CustomRangeSlider.drawCustomRangeSlider(
                    session = session,
                    idPrefix = existing.id,
                    label = "Frames Per Step",
                    themeColor = themeColor,
                    currentValue = existing.subdivision,
                    currentMin = existing.subdivisionMin,
                    currentMax = existing.subdivisionMax,
                    minLimit = 1.0f,
                    maxLimit = 120.0f,
                    defaultValue = 4.0f,
                    isRandomizable = existing.randomizeSubdivision,
                    isRandomizeDisabled = param.isRandomizeDisabled,
                    randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                    formatValue = { "%.0f f".format(it) },
                    onRandomizableChanged = { checked -> onReplace(existing.copy(randomizeSubdivision = checked)) },
                    onRandomizeNow = { onReplace(existing.randomizeSubdivision()) },
                    onRangeChanged = { min, max -> onReplace(existing.copy(subdivisionMin = min, subdivisionMax = max)) },
                    onValueChanged = { v -> onReplace(existing.copy(subdivision = v, subdivisionMin = v, subdivisionMax = v)) }
                )
            }
        }

        ImGui.spacing()

        // 3. Step Count & Clear Row
        session.uiTheme.body("Pattern Length:")
        ImGui.sameLine(0f, 8f * fontScale)

        val stepCounts = listOf(8, 16, 32)
        stepCounts.forEachIndexed { idx, count ->
            if (idx > 0) ImGui.sameLine(0f, 6f * fontScale)
            val isCurrent = existing.seqStepCount == count
            if (isCurrent) {
                ImGui.pushStyleColor(ImGuiCol.Button, themeColor)
                ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))
            }
            if (ImGui.button("$count Steps##seq_len_${existing.id}_$count", 80f * fontScale, 0f)) {
                onReplace(existing.copy(seqStepCount = count))
            }
            if (isCurrent) {
                ImGui.popStyleColor(2)
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                val rows = count / 8
                ImGui.setTooltip("Set sequence length to $count steps ($rows ${if (rows == 1) "row" else "rows"} of 8)")
            }
        }

        ImGui.sameLine(0f, 18f * fontScale)
        if (ImGui.button("Clear (All 0)##seq_clear_${existing.id}")) {
            onReplace(existing.copy(seqSteps = List(32) { 0.0f }))
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Reset all 32 step values to 0.0.")
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 4. Calculate Current Active Step Playhead
        val stepCount = existing.seqStepCount.coerceIn(1, 32)
        val cyclePosition = when (existing.genUnit) {
            GenUnit.TIME -> {
                val seconds = CVRegistry.getElapsedRealtimeSec()
                val period = existing.subdivision.toDouble().coerceAtLeast(0.001)
                (seconds / period) + existing.phaseOffset
            }
            GenUnit.BEAT -> {
                val bpmVal = CVRegistry.get("bpm").toDouble().coerceIn(20.0, 300.0)
                val beats = CVRegistry.getSynchronizedTotalBeats()
                val stepDiv = existing.subdivision.toDouble().coerceAtLeast(0.001)
                (beats / stepDiv) + existing.phaseOffset
            }
            GenUnit.FRAME -> {
                val frameCount = CVRegistry.getRenderFrameCount().toDouble()
                val framePeriod = existing.subdivision.toDouble().coerceAtLeast(1.0)
                (frameCount / framePeriod) + existing.phaseOffset
            }
        }
        val stepIndexRaw = kotlin.math.floor(cyclePosition).toLong()
        val activeStep = Math.floorMod(stepIndexRaw, stepCount.toLong()).toInt()

        // 5. 8-Wide Step Grid
        val totalSteps = existing.seqStepCount.coerceIn(8, 32)
        val numRows = (totalSteps + 7) / 8
        val dl = ImGui.getWindowDrawList()
        val availW = ImGui.getContentRegionAvailX()
        val colGap = 6f * fontScale
        val totalGaps = colGap * 7
        val boxW = ((availW - totalGaps) / 8f).coerceAtLeast(40f * fontScale)
        val frameH = ImGui.getFrameHeight()

        for (row in 0 until numRows) {
            for (col in 0 until 8) {
                val stepIdx = row * 8 + col
                if (stepIdx >= totalSteps) break

                if (col > 0) ImGui.sameLine(0f, colGap)

                val key = "seq_step_${existing.id}_$stepIdx"
                val curVal = if (stepIdx < existing.seqSteps.size) existing.seqSteps[stepIdx] else 0f

                val buffer = textBuffers.getOrPut(key) { ImString("%.3f".format(curVal), 16) }
                val isFocused = textWidgetActive.getOrDefault(key, false)
                if (!isFocused) {
                    buffer.set("%.3f".format(curVal))
                }

                val posX = ImGui.getCursorScreenPosX()
                val posY = ImGui.getCursorScreenPosY()

                val isActive = stepIdx == activeStep
                if (isActive) {
                    // Draw illuminated active step playhead border / glow
                    val glowCol = themeColor
                    dl.addRect(posX - 2.5f, posY - 2.5f, posX + boxW + 2.5f, posY + frameH + 2.5f, glowCol, 4f, 0, 2.5f)
                }

                ImGui.pushItemWidth(boxW)
                val callback = textCallbacks.getOrPut(key) { StepInputCallback() }
                callback.currentValue = curVal
                callback.minLimit = stepMinLimit
                callback.maxLimit = stepMaxLimit
                callback.onChanged = { newVal ->
                    val mutableSteps = existing.seqSteps.toMutableList()
                    while (mutableSteps.size <= stepIdx) mutableSteps.add(0f)
                    mutableSteps[stepIdx] = newVal
                    onReplace(existing.copy(seqSteps = mutableSteps))
                }

                val flags = ImGuiInputTextFlags.CallbackHistory
                val inputChanged = ImGui.inputText("##$key", buffer, flags, callback)
                if (inputChanged) {
                    val parsed = buffer.get().toFloatOrNull()
                    if (parsed != null) {
                        val clamped = parsed.coerceIn(stepMinLimit, stepMaxLimit)
                        val mutableSteps = existing.seqSteps.toMutableList()
                        while (mutableSteps.size <= stepIdx) mutableSteps.add(0f)
                        mutableSteps[stepIdx] = clamped
                        onReplace(existing.copy(seqSteps = mutableSteps))
                    }
                }

                val itemHovered = ImGui.isItemHovered()
                val itemActive = ImGui.isItemActive()
                textWidgetActive[key] = itemActive

                if (itemHovered) {
                    val io = ImGui.getIO()
                    if (io.mouseWheel != 0f) {
                        val shift = io.keyShift
                        val ctrl = io.keyCtrl
                        val delta = if (ctrl && shift) 0.1f else if (shift) 0.01f else 0.001f
                        val nextVal = (curVal + io.mouseWheel * delta).coerceIn(stepMinLimit, stepMaxLimit)
                        val mutableSteps = existing.seqSteps.toMutableList()
                        while (mutableSteps.size <= stepIdx) mutableSteps.add(0f)
                        mutableSteps[stepIdx] = nextVal
                        onReplace(existing.copy(seqSteps = mutableSteps))
                        io.mouseWheel = 0f
                    }
                    if (ImGui.isMouseClicked(2) || ImGui.isItemClicked(2)) { // Middle-click reset
                        val mutableSteps = existing.seqSteps.toMutableList()
                        while (mutableSteps.size <= stepIdx) mutableSteps.add(0f)
                        mutableSteps[stepIdx] = 0.0f
                        onReplace(existing.copy(seqSteps = mutableSteps))
                    }
                    if (session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip("Step ${stepIdx + 1}: ${"%.3f".format(curVal)}\nType number, Up/Down arrow, or Scroll.\nMiddle-click to reset to 0.")
                    }
                }

                ImGui.popItemWidth()
            }
            ImGui.spacing()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 6. Dynamics: Hold & Curve
        session.uiTheme.h3("Dynamics")
        ImGui.spacing()

        // Hold / Slew Slider with editable text box
        CustomRangeSlider.drawCompactSlider(
            session = session,
            label = "Step Hold",
            currentValue = existing.seqHold,
            minLimit = 0.0f,
            maxLimit = 1.0f,
            defaultValue = 1.0f,
            idPrefix = existing.id,
            themeColor = themeColor,
            formatValue = { "${(it * 100f).toInt()}%" },
            onValueChanged = { v ->
                onReplace(existing.copy(seqHold = v, seqHoldMin = v, seqHoldMax = v))
            }
        )
        if (session.uiTheme.tooltipsEnabled && ImGui.isItemHovered()) {
            ImGui.setTooltip("100% = Instant step jumps (no glide).\n0% = Continuous glide over full step duration.\n50% = Hold 50% of step, glide for 50%.")
        }

        ImGui.spacing()

        // Curve Toggle: Linear vs Smooth S-Curve
        session.uiTheme.body("Glide Curve:")
        ImGui.sameLine(0f, 10f * fontScale)

        val isSmooth = existing.seqCurveSmooth
        if (!isSmooth) {
            ImGui.pushStyleColor(ImGuiCol.Button, themeColor)
            ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))
        }
        if (ImGui.button("Linear##seq_curve_lin_${existing.id}", 70f * fontScale, 0f)) {
            onReplace(existing.copy(seqCurveSmooth = false))
        }
        if (!isSmooth) ImGui.popStyleColor(2)
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Linear glide interpolation between steps.")
        }

        ImGui.sameLine(0f, 6f * fontScale)
        if (isSmooth) {
            ImGui.pushStyleColor(ImGuiCol.Button, themeColor)
            ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))
        }
        if (ImGui.button("Smooth##seq_curve_smooth_${existing.id}", 70f * fontScale, 0f)) {
            onReplace(existing.copy(seqCurveSmooth = true))
        }
        if (isSmooth) ImGui.popStyleColor(2)
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Smooth cosine / S-curve glide easing between steps.")
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // 7. Modulation Depth & DC Offset
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = existing.id,
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
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked -> onReplace(existing.copy(randomizeDepth = checked)) },
            onRandomizeNow = { onReplace(existing.randomizeDepth()) },
            onRangeChanged = { min, max -> onReplace(existing.copy(depthMin = min, depthMax = max)) },
            onValueChanged = { v -> onReplace(existing.copy(depth = v, depthMin = v, depthMax = v)) }
        )

        ImGui.spacing()

        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = existing.id,
            label = "DC Offset",
            themeColor = themeColor,
            currentValue = existing.dcOffset,
            currentMin = existing.dcOffsetMin,
            currentMax = existing.dcOffsetMax,
            minLimit = -1f,
            maxLimit = 1f,
            defaultValue = 0f,
            isRandomizable = existing.randomizeDcOffset,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked -> onReplace(existing.copy(randomizeDcOffset = checked)) },
            onRandomizeNow = { onReplace(existing.randomizeDcOffset()) },
            onRangeChanged = { min, max -> onReplace(existing.copy(dcOffsetMin = min, dcOffsetMax = max)) },
            onValueChanged = { v -> onReplace(existing.copy(dcOffset = v, dcOffsetMin = v, dcOffsetMax = v)) }
        )
    }
}
