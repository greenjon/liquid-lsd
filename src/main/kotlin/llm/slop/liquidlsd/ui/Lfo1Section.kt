package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.GenUnit
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.Waveform
import llm.slop.liquidlsd.utils.TimeUtils

object Lfo1Section {

    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        isBeat: Boolean,
        isSnh: Boolean,
        isGen: Boolean,
        hasAdvanced: Boolean,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        val bypassed = existing.bypassed
        val showWaveform = hasAdvanced && (!isSnh || isGen)

        if (showWaveform) {
            val fontScale = 0.95f
            val btnW = 35f * fontScale
            val btnH = ImGui.getFrameHeight()

            // 1. Shape Preset buttons
            session.uiTheme.body(if (isGen) "LFO 1 Shape:" else "Shape Preset:")
            ImGui.sameLine(0f, 10f * fontScale)

            // Sine Button
            val isSine = existing.waveform == Waveform.SINE && existing.morph == 0.0f && existing.hold == 0.0f
            if (CustomIconButton.drawWaveformButton("lfo1_sine", WaveShape.SINE, isSine, themeColor, btnW, btnH)) {
                onReplace(existing.copy(
                    waveform = Waveform.SINE,
                    morph = 0.0f,
                    hold = 0.0f
                ))
            }
            itemTooltip("Load standard smooth Sine wave LFO.")

            // Triangle Button
            ImGui.sameLine(0f, 4f * fontScale)
            val isTri = existing.waveform == Waveform.TRIANGLE && existing.morph == 1.0f && existing.hold == 0.0f
            if (CustomIconButton.drawWaveformButton("lfo1_tri", WaveShape.TRIANGLE, isTri, themeColor, btnW, btnH)) {
                onReplace(existing.copy(
                    waveform = Waveform.TRIANGLE,
                    morph = 1.0f,
                    hold = 0.0f
                ))
            }
            itemTooltip("Load linear Triangle wave LFO.")

            // Square Button
            ImGui.sameLine(0f, 4f * fontScale)
            val isSquare = existing.waveform == Waveform.SQUARE && existing.morph == 1.0f && existing.hold >= 0.99f
            if (CustomIconButton.drawWaveformButton("lfo1_square", WaveShape.SQUARE, isSquare, themeColor, btnW, btnH)) {
                onReplace(existing.copy(
                    waveform = Waveform.SQUARE,
                    morph = 1.0f,
                    hold = 0.999f
                ))
            }
            itemTooltip("Load binary Square wave LFO.")

            // Random Button
            ImGui.sameLine(0f, 4f * fontScale)
            val isRandom = existing.waveform == Waveform.RANDOM
            if (CustomIconButton.drawWaveformButton("lfo1_random", WaveShape.RANDOM, isRandom, themeColor, btnW, btnH)) {
                onReplace(existing.copy(
                    waveform = Waveform.RANDOM
                ))
            }
            itemTooltip("Load step or smooth Random noise LFO.")

            // 2. Slew / Duty Preset buttons (only if not Random)
            if (existing.waveform != Waveform.RANDOM) {
                val isSquareWave = existing.waveform == Waveform.SQUARE
                session.uiTheme.body(if (isSquareWave) "Duty Preset:" else "Asymmetry:")
                ImGui.sameLine(0f, 10f * fontScale)

                // Left Button
                val isLeft = if (isSquareWave) existing.slope <= 0.1f else existing.slope <= 0.01f
                if (CustomIconButton.drawWaveformButton("lfo1_left", if (isSquareWave) WaveShape.SQUARE_10 else WaveShape.RAMP_DOWN, isLeft, themeColor, btnW, btnH)) {
                    onReplace(existing.copy(slope = if (isSquareWave) 0.1f else 0.001f))
                }
                itemTooltip(if (isSquareWave) "Set Square duty cycle to 10% (narrow pulse)." else "Set LFO asymmetry fully Left (sawtooth falling / ramp down).")

                // Center Button
                ImGui.sameLine(0f, 4f * fontScale)
                val isCenter = existing.slope >= 0.49f && existing.slope <= 0.51f
                if (CustomIconButton.drawWaveformButton("lfo1_center", if (isSquareWave) WaveShape.SQUARE else WaveShape.TRIANGLE, isCenter, themeColor, btnW, btnH)) {
                    onReplace(existing.copy(slope = 0.5f))
                }
                itemTooltip(if (isSquareWave) "Set Square duty cycle to 50% (balanced square wave)." else "Set LFO asymmetry to Center (perfectly symmetrical).")

                // Right Button
                ImGui.sameLine(0f, 4f * fontScale)
                val isRight = if (isSquareWave) existing.slope >= 0.9f else existing.slope >= 0.99f
                if (CustomIconButton.drawWaveformButton("lfo1_right", if (isSquareWave) WaveShape.SQUARE_90 else WaveShape.RAMP_UP, isRight, themeColor, btnW, btnH)) {
                    onReplace(existing.copy(slope = if (isSquareWave) 0.9f else 0.999f))
                }
                itemTooltip(if (isSquareWave) "Set Square duty cycle to 90% (wide pulse)." else "Set LFO asymmetry fully Right (sawtooth rising / ramp up).")
            }

            ImGui.spacing()

            // -- Unit Selection Dropdown (Time/Beat/Frame) if applicable --
            if (isGen) {
                session.uiTheme.body("LFO 1 Unit:")
                ImGui.sameLine(0f, 10f * fontScale)
                val unitIdx = ImInt(existing.genUnit.ordinal)
                val unitLabels = arrayOf("Time", "Beat", "Frame")
                if (bypassed) ImGui.popStyleVar()
                ImGui.pushItemWidth(110f * fontScale)
                if (ImGui.combo("##unit", unitIdx, unitLabels)) {
                    val selectedUnit = GenUnit.entries[unitIdx.get()]
                    val adjustedSubdiv = when (selectedUnit) {
                        GenUnit.FRAME -> existing.subdivision.coerceIn(1f, 10000f).toInt().toFloat()
                        GenUnit.TIME -> existing.subdivision.coerceIn(0.01f, 86400f)
                        GenUnit.BEAT -> {
                            val options = BeatDivisionSlider.subdivisionOptions
                            options.minByOrNull { kotlin.math.abs(it - existing.subdivision) } ?: 1.0f
                        }
                    }
                    val adjustedMin = when (selectedUnit) {
                        GenUnit.FRAME -> existing.subdivisionMin.coerceIn(1f, 10000f).toInt().toFloat()
                        GenUnit.TIME -> existing.subdivisionMin.coerceIn(0.01f, 86400f)
                        GenUnit.BEAT -> {
                            val options = BeatDivisionSlider.subdivisionOptions
                            options.minByOrNull { kotlin.math.abs(it - existing.subdivisionMin) } ?: 1.0f
                        }
                    }
                    val adjustedMax = when (selectedUnit) {
                        GenUnit.FRAME -> existing.subdivisionMax.coerceIn(1f, 10000f).toInt().toFloat()
                        GenUnit.TIME -> existing.subdivisionMax.coerceIn(0.01f, 86400f)
                        GenUnit.BEAT -> {
                            val options = BeatDivisionSlider.subdivisionOptions
                            options.minByOrNull { kotlin.math.abs(it - existing.subdivisionMax) } ?: 1.0f
                        }
                    }
                    onReplace(existing.copy(
                        genUnit = selectedUnit,
                        subdivision = adjustedSubdiv,
                        subdivisionMin = adjustedMin,
                        subdivisionMax = adjustedMax
                    ))
                }
                itemTooltip("Select frequency unit:\nTime: Rate is in seconds.\nBeat: Rate is synchronized to BPM subdivisions.\nFrame: Rate is synchronized to render frame count (1-10000 frames).")
                ImGui.popItemWidth()
                if (bypassed) ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)
                ImGui.spacing()
            }
        }

        ImGui.spacing()

        // -- Modulation Range (Min/Max) ---------------------------
        CustomRangeSlider.drawMinMaxRangeSlider(
            session = session,
            label = if (isGen) "LFO 1 Range" else "Modulation Range",
            currentMin = existing.getLfoMin(),
            currentMax = existing.getLfoMax(),
            minRangeMin = existing.dcOffsetMin,
            minRangeMax = existing.dcOffsetMax,
            maxRangeMin = existing.depthMin,
            maxRangeMax = existing.depthMax,
            minLimit = -1f,
            maxLimit = 1f,
            isRandomizable = existing.randomizeDcOffset || existing.randomizeDepth,
            isRandomizeDisabled = param.isRandomizeDisabled,
            themeColor = themeColor,
            idPrefix = existing.id,
            onRandomizableChanged = { enabled ->
                val rMin = existing.dcOffsetMin
                val rMax = existing.dcOffsetMax
                val dMin = existing.depthMin
                val dMax = existing.depthMax
                val curMin = existing.getLfoMin()
                val curMax = existing.getLfoMax()
                
                onReplace(existing.copy(
                    lfoMinMaxMode = true,
                    randomizeDcOffset = enabled,
                    randomizeDepth = enabled,
                    dcOffsetMin = if (enabled && rMin == rMax) (curMin - 0.1f).coerceAtLeast(-1f) else if (!enabled) curMin else rMin,
                    dcOffsetMax = if (enabled && rMin == rMax) (curMin + 0.1f).coerceAtMost(1f) else if (!enabled) curMin else rMax,
                    depthMin = if (enabled && dMin == dMax) (curMax - 0.1f).coerceAtLeast(-1f) else if (!enabled) curMax else dMin,
                    depthMax = if (enabled && dMin == dMax) (curMax + 0.1f).coerceAtMost(1f) else if (!enabled) curMax else dMax
                ))
            },
            onRandomizeNow = {
                onReplace(existing.randomizeDcOffset().randomizeDepth())
            },
            onMinMaxChanged = { min, max ->
                onReplace(existing.withLfoRange(min, max).copy(lfoMinMaxMode = true))
            },
            onMinRangeChanged = { rMin, rMax ->
                onReplace(existing.copy(lfoMinMaxMode = true, dcOffsetMin = rMin, dcOffsetMax = rMax))
            },
            onMaxRangeChanged = { rMin, rMax ->
                onReplace(existing.copy(lfoMinMaxMode = true, depthMin = rMin, depthMax = rMax))
            }
        )
        ImGui.spacing()

        if (!hasAdvanced) {
            return
        }

        // -- Subdivision (Beat / S&H) -----------------------------
        if (isBeat || isSnh || (isGen && existing.genUnit == GenUnit.BEAT)) {
            val subdivisionOptions = BeatDivisionSlider.subdivisionOptions
            val subdivisionLabels = BeatDivisionSlider.subdivisionLabels
            val currentMinIdx = subdivisionOptions.indexOfFirst { it == existing.subdivisionMin }.coerceAtLeast(0)
            val currentMaxIdx = subdivisionOptions.indexOfFirst { it == existing.subdivisionMax }.coerceAtLeast(0)
            val currentActiveIdx = subdivisionOptions.indexOfFirst { it == existing.subdivision }.coerceAtLeast(0)
            
            BeatDivisionSlider.drawBeatDivisionSlider(session, idPrefix = existing.id,
                label = if (isGen) "LFO 1 Beat Div" else "Beat Div",
                themeColor = themeColor,
                currentValue = currentActiveIdx.toFloat(),
                currentMin = currentMinIdx.toFloat(),
                currentMax = currentMaxIdx.toFloat(),
                minLimit = 0f,
                maxLimit = (subdivisionOptions.size - 1).toFloat(),
                defaultValue = subdivisionOptions.indexOfFirst { it == 1.0f }.coerceAtLeast(0).toFloat(),
                isRandomizable = existing.randomizeSubdivision,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { idx -> subdivisionLabels[idx.toInt().coerceIn(0, subdivisionOptions.size - 1)] },
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.subdivisionMin
                        val rMax = existing.subdivisionMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            val idx = subdivisionOptions.indexOfFirst { it == rMin }.coerceIn(0, subdivisionOptions.size - 1)
                            val minIdx = (idx - 1).coerceAtLeast(0)
                            val maxIdx = (idx + 1).coerceAtMost(subdivisionOptions.size - 1)
                            Pair(subdivisionOptions[minIdx], subdivisionOptions[maxIdx])
                        } else {
                            Pair(rMin, rMax)
                        }
                        onReplace(existing.copy(
                            randomizeSubdivision = true,
                            subdivisionMin = nextMin,
                            subdivisionMax = nextMax
                        ))
                    } else {
                        onReplace(existing.copy(
                            randomizeSubdivision = false,
                            subdivisionMin = existing.subdivision,
                            subdivisionMax = existing.subdivision
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeSubdivision())
                },
                onRangeChanged = { nextMinIdx, nextMaxIdx ->
                    val rawMinVal = subdivisionOptions[nextMinIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                    val rawMaxVal = subdivisionOptions[nextMaxIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                    val nextMinVal = minOf(rawMinVal, rawMaxVal)
                    val nextMaxVal = maxOf(rawMinVal, rawMaxVal)
                    val nextActive = existing.subdivision.coerceIn(nextMinVal, nextMaxVal)
                    onReplace(existing.copy(
                        subdivisionMin = nextMinVal,
                        subdivisionMax = nextMaxVal,
                        subdivision = nextActive
                    ))
                },
                onValueChanged = { newValIdx ->
                    val newVal = subdivisionOptions[newValIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                    onReplace(existing.copy(
                        subdivision = newVal,
                        subdivisionMin = newVal,
                        subdivisionMax = newVal
                    ))
                }
            )
            ImGui.spacing()
        }

        // -- LFO Period / Speed -----------------------------------
        if (isGen && existing.genUnit == GenUnit.TIME) {
            val formatFunc: (Float) -> String = { v -> TimeUtils.formatPeriod(v) }
            val parseFunc: (String) -> Float? = { s -> TimeUtils.parsePeriod(s) }

            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id,
                label = if (isGen) "LFO 1 Period" else "LFO Period",
                themeColor = themeColor,
                currentValue = existing.subdivision,
                currentMin = existing.subdivisionMin,
                currentMax = existing.subdivisionMax,
                minLimit = 0.01f, maxLimit = 86400f, defaultValue = 1.0f,
                isRandomizable = existing.randomizeSubdivision,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = formatFunc,
                isLogarithmic = true,
                parseValue = parseFunc,
                // Period uses multiplicative expansion (x0.5 / x2) instead of ±0.1 offset,
                // and rounds values >= 1 hour to whole seconds — so kept inline.
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.subdivisionMin
                        val rMax = existing.subdivisionMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.subdivision * 0.5f).coerceIn(0.01f, 86400f), (existing.subdivision * 2f).coerceIn(0.01f, 86400f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        onReplace(existing.copy(
                            randomizeSubdivision = true,
                            subdivisionMin = nextMin,
                            subdivisionMax = nextMax
                        ))
                    } else {
                        onReplace(existing.copy(
                            randomizeSubdivision = false,
                            subdivisionMin = existing.subdivision,
                            subdivisionMax = existing.subdivision
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeSubdivision())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val roundedMin = if (nextMin >= 3600f) nextMin.toInt().toFloat() else nextMin
                    val roundedMax = if (nextMax >= 3600f) nextMax.toInt().toFloat() else nextMax
                    val safeMin = minOf(roundedMin, roundedMax)
                    val safeMax = maxOf(roundedMin, roundedMax)
                    val nextActive = existing.subdivision.coerceIn(safeMin, safeMax)
                    val roundedActive = if (nextActive >= 3600f) nextActive.toInt().toFloat() else nextActive
                    onReplace(existing.copy(
                        subdivisionMin = safeMin,
                        subdivisionMax = safeMax,
                        subdivision = roundedActive
                    ))
                },
                onValueChanged = { newVal ->
                    val roundedVal = if (newVal >= 3600f) newVal.toInt().toFloat() else newVal
                    onReplace(existing.copy(
                        subdivision = roundedVal,
                        subdivisionMin = roundedVal,
                        subdivisionMax = roundedVal
                    ))
                }
            )
            ImGui.spacing()
        }

        // -- Frame Period / Speed ---------------------------------
        if (isGen && existing.genUnit == GenUnit.FRAME) {
            val formatFunc: (Float) -> String = { v -> "${v.toInt().coerceIn(1, 10000)}" }
            val formatLabelFunc: (Float) -> String = { v ->
                val frames = v.toInt().coerceIn(1, 10000)
                val fps = session.uiTheme.maxFps.coerceAtLeast(1).toFloat()
                val sec = frames / fps
                val secFormatted = TimeUtils.formatPeriod(sec)
                if (frames == 1) "1 frame ($secFormatted)"
                else "$frames frames ($secFormatted)"
            }
            val parseFunc: (String) -> Float? = { s ->
                s.replace(Regex("[^0-9.]"), "").toFloatOrNull()?.toInt()?.coerceIn(1, 10000)?.toFloat()
            }

            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id,
                label = "LFO 1 Frames",
                themeColor = themeColor,
                currentValue = existing.subdivision.toInt().coerceIn(1, 10000).toFloat(),
                currentMin = existing.subdivisionMin.toInt().coerceIn(1, 10000).toFloat(),
                currentMax = existing.subdivisionMax.toInt().coerceIn(1, 10000).toFloat(),
                minLimit = 1f, maxLimit = 10000f, defaultValue = 1f,
                isRandomizable = existing.randomizeSubdivision,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = formatFunc,
                formatLabel = formatLabelFunc,
                isLogarithmic = true,
                parseValue = parseFunc,
                // Frames use integer halving/doubling for expansion — kept inline.
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.subdivisionMin.toInt().coerceIn(1, 10000)
                        val rMax = existing.subdivisionMax.toInt().coerceIn(1, 10000)
                        val cur = existing.subdivision.toInt().coerceIn(1, 10000)
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((cur / 2).coerceIn(1, 10000).toFloat(), (cur * 2).coerceIn(1, 10000).toFloat())
                        } else {
                            Pair(rMin.toFloat(), rMax.toFloat())
                        }
                        onReplace(existing.copy(
                            randomizeSubdivision = true,
                            subdivisionMin = nextMin,
                            subdivisionMax = nextMax
                        ))
                    } else {
                        val cur = existing.subdivision.toInt().coerceIn(1, 10000).toFloat()
                        onReplace(existing.copy(
                            randomizeSubdivision = false,
                            subdivisionMin = cur,
                            subdivisionMax = cur
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeSubdivision())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val roundedMin = nextMin.toInt().coerceIn(1, 10000).toFloat()
                    val roundedMax = nextMax.toInt().coerceIn(1, 10000).toFloat()
                    val safeMin = minOf(roundedMin, roundedMax)
                    val safeMax = maxOf(roundedMin, roundedMax)
                    val nextActive = existing.subdivision.toInt().coerceIn(safeMin.toInt(), safeMax.toInt()).toFloat()
                    onReplace(existing.copy(
                        subdivisionMin = safeMin,
                        subdivisionMax = safeMax,
                        subdivision = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    val roundedVal = newVal.toInt().coerceIn(1, 10000).toFloat()
                    onReplace(existing.copy(
                        subdivision = roundedVal,
                        subdivisionMin = roundedVal,
                        subdivisionMax = roundedVal
                    ))
                }
            )
            ImGui.spacing()
        }

        // -- Advanced Parameters Accordion --
        val isAdvancedDirty = existing.phaseOffset != 0f || existing.morph != 0f || existing.hold != 0f || (existing.slope != 0.5f && existing.waveform != Waveform.RANDOM)
        val dirtyMarker = if (isAdvancedDirty) " •" else ""
        val advHeader = "Advanced Parameters$dirtyMarker###adv_params_header"
        if (ImGui.collapsingHeader(advHeader, 0)) {
            // -- Phase Offset -----------------------------------------
            val phaseOffsetCbs = cvModulatorSlider(
                existing = existing,
                getValue = { phaseOffset }, getMin = { phaseOffsetMin }, getMax = { phaseOffsetMax },
                minLimit = 0f, maxLimit = 1f,
                copyWithRandomize = { enabled, nMin, nMax -> copy(randomizePhaseOffset = enabled, phaseOffsetMin = nMin, phaseOffsetMax = nMax) },
                copyWithRange   = { sMin, sMax, v -> copy(phaseOffsetMin = sMin, phaseOffsetMax = sMax, phaseOffset = v) },
                copyWithValue   = { v -> copy(phaseOffset = v, phaseOffsetMin = v, phaseOffsetMax = v) },
                randomizeNow    = { randomizePhaseOffset() },
                onReplace = onReplace,
            )
            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id,
                label = if (isGen) "LFO 1 Phase" else "Phase Offset",
                themeColor = themeColor,
                currentValue = existing.phaseOffset,
                currentMin = existing.phaseOffsetMin,
                currentMax = existing.phaseOffsetMax,
                minLimit = 0f, maxLimit = 1f, defaultValue = 0f,
                isRandomizable = existing.randomizePhaseOffset,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = phaseOffsetCbs.onRandomizableChanged,
                onRandomizeNow        = phaseOffsetCbs.onRandomizeNow,
                onRangeChanged        = phaseOffsetCbs.onRangeChanged,
                onValueChanged        = phaseOffsetCbs.onValueChanged,
            )
            ImGui.spacing()

            // -- Morph Slider --
            val morphCbs = cvModulatorSlider(
                existing = existing,
                getValue = { morph }, getMin = { morphMin }, getMax = { morphMax },
                minLimit = 0f, maxLimit = 1f,
                copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeMorph = enabled, morphMin = nMin, morphMax = nMax) },
                copyWithRange   = { sMin, sMax, v -> copy(morphMin = sMin, morphMax = sMax, morph = v) },
                copyWithValue   = { v -> copy(morph = v, morphMin = v, morphMax = v) },
                randomizeNow    = { randomizeMorph() },
                onReplace = onReplace,
            )
            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id + "_morph",
                label = if (isGen) "LFO 1 Morph" else "Morph",
                themeColor = themeColor,
                currentValue = existing.morph,
                currentMin = existing.morphMin,
                currentMax = existing.morphMax,
                minLimit = 0f, maxLimit = 1f, defaultValue = 0f,
                isRandomizable = existing.randomizeMorph,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = morphCbs.onRandomizableChanged,
                onRandomizeNow        = morphCbs.onRandomizeNow,
                onRangeChanged        = morphCbs.onRangeChanged,
                onValueChanged        = morphCbs.onValueChanged,
            )
            ImGui.spacing()

            // -- Hold Slider --
            val holdCbs = cvModulatorSlider(
                existing = existing,
                getValue = { hold }, getMin = { holdMin }, getMax = { holdMax },
                minLimit = 0f, maxLimit = 0.999f,
                copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeHold = enabled, holdMin = nMin, holdMax = nMax) },
                copyWithRange   = { sMin, sMax, v -> copy(holdMin = sMin, holdMax = sMax, hold = v) },
                copyWithValue   = { v -> copy(hold = v, holdMin = v, holdMax = v) },
                randomizeNow    = { randomizeHold() },
                onReplace = onReplace,
            )
            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id + "_hold",
                label = if (isGen) "LFO 1 Hold" else "Hold",
                themeColor = themeColor,
                currentValue = existing.hold,
                currentMin = existing.holdMin,
                currentMax = existing.holdMax,
                minLimit = 0f, maxLimit = 0.999f, defaultValue = 0f,
                isRandomizable = existing.randomizeHold,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = holdCbs.onRandomizableChanged,
                onRandomizeNow        = holdCbs.onRandomizeNow,
                onRangeChanged        = holdCbs.onRangeChanged,
                onValueChanged        = holdCbs.onValueChanged,
            )
            ImGui.spacing()

            // -- Slew / Duty Cycle Slider (only if not Random) --
            if (existing.waveform != Waveform.RANDOM) {
                val isSquare = existing.waveform == Waveform.SQUARE
                val label = if (isSquare) {
                    if (isGen) "LFO 1 Duty Cycle" else "Duty Cycle"
                } else {
                    if (isGen) "LFO 1 Slew" else "Slew"
                }
                val slopeCbs = cvModulatorSlider(
                    existing = existing,
                    getValue = { slope }, getMin = { slopeMin }, getMax = { slopeMax },
                    minLimit = 0.001f, maxLimit = 0.999f,
                    copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeSlope = enabled, slopeMin = nMin, slopeMax = nMax) },
                    copyWithRange   = { sMin, sMax, v -> copy(slopeMin = sMin, slopeMax = sMax, slope = v) },
                    copyWithValue   = { v -> copy(slope = v, slopeMin = v, slopeMax = v) },
                    randomizeNow    = { randomizeSlope() },
                    onReplace = onReplace,
                )
                CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id,
                    label = label,
                    themeColor = themeColor,
                    currentValue = existing.slope,
                    currentMin = existing.slopeMin,
                    currentMax = existing.slopeMax,
                    minLimit = 0.001f, maxLimit = 0.999f, defaultValue = 0.5f,
                    isRandomizable = existing.randomizeSlope,
                    isRandomizeDisabled = param.isRandomizeDisabled,
                    randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                    formatValue = { "%.3f".format(it) },
                    onRandomizableChanged = slopeCbs.onRandomizableChanged,
                    onRandomizeNow        = slopeCbs.onRandomizeNow,
                    onRangeChanged        = slopeCbs.onRangeChanged,
                    onValueChanged        = slopeCbs.onValueChanged,
                )
            }
        }
    }
}
