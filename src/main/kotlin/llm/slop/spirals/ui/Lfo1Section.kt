package llm.slop.spirals.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.GenUnit
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.Waveform
import llm.slop.spirals.utils.TimeUtils

object Lfo1Section {

    fun draw(
        state: PatchGridState,
        param: ModulatableParameter,
        existing: CvModulator,
        isLfo: Boolean,
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
            val startX = ImGui.getCursorPosX()

            // 1. Shape Preset buttons
            UITheme.body(if (isGen) "LFO 1 Shape:" else "Shape Preset:")
            ImGui.sameLine(0f, 10f)

            val btnW = 35f
            val btnH = ImGui.getFrameHeight()

            // Sine Button
            val isSine = existing.waveform == Waveform.SINE && existing.morph == 0.0f && existing.hold == 0.0f
            if (CustomIconButton.drawWaveformButton("lfo1_sine", WaveShape.SINE, isSine, themeColor, btnW, btnH)) {
                onReplace(existing.copy(
                    waveform = Waveform.SINE,
                    morph = 0.0f,
                    hold = 0.0f
                ))
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Load standard smooth Sine wave LFO.")
            }

            // Triangle Button
            ImGui.sameLine(0f, 4f)
            val isTri = existing.waveform == Waveform.TRIANGLE && existing.morph == 1.0f && existing.hold == 0.0f
            if (CustomIconButton.drawWaveformButton("lfo1_tri", WaveShape.TRIANGLE, isTri, themeColor, btnW, btnH)) {
                onReplace(existing.copy(
                    waveform = Waveform.TRIANGLE,
                    morph = 1.0f,
                    hold = 0.0f
                ))
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Load linear Triangle wave LFO.")
            }

            // Square Button
            ImGui.sameLine(0f, 4f)
            val isSquare = existing.waveform == Waveform.SQUARE && existing.morph == 1.0f && existing.hold == 0.5f
            if (CustomIconButton.drawWaveformButton("lfo1_square", WaveShape.SQUARE, isSquare, themeColor, btnW, btnH)) {
                onReplace(existing.copy(
                    waveform = Waveform.SQUARE,
                    morph = 1.0f,
                    hold = 0.5f
                ))
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Load binary Square wave LFO.")
            }

            // Random Button
            ImGui.sameLine(0f, 4f)
            val isRandom = existing.waveform == Waveform.RANDOM
            if (CustomIconButton.drawWaveformButton("lfo1_random", WaveShape.RANDOM, isRandom, themeColor, btnW, btnH)) {
                onReplace(existing.copy(
                    waveform = Waveform.RANDOM
                ))
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Load step or smooth Random noise LFO.")
            }

            // 2. Slew Preset buttons (only if not Random)
            if (existing.waveform != Waveform.RANDOM) {
                ImGui.sameLine(0f, 20f)
                UITheme.body("Asymmetry:")
                ImGui.sameLine(0f, 10f)

                // Left Button
                val isLeft = existing.slope <= 0.01f
                if (CustomIconButton.drawWaveformButton("lfo1_left", WaveShape.RAMP_DOWN, isLeft, themeColor, 35f, btnH)) {
                    onReplace(existing.copy(slope = 0.001f))
                }
                if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                    ImGui.setTooltip("Set LFO asymmetry fully Left (sawtooth falling / ramp down).")
                }

                // Center Button
                ImGui.sameLine(0f, 4f)
                val isCenter = existing.slope >= 0.49f && existing.slope <= 0.51f
                if (CustomIconButton.drawWaveformButton("lfo1_center", WaveShape.TRIANGLE, isCenter, themeColor, 35f, btnH)) {
                    onReplace(existing.copy(slope = 0.5f))
                }
                if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                    ImGui.setTooltip("Set LFO asymmetry to Center (perfectly symmetrical).")
                }

                // Right Button
                ImGui.sameLine(0f, 4f)
                val isRight = existing.slope >= 0.99f
                if (CustomIconButton.drawWaveformButton("lfo1_right", WaveShape.RAMP_UP, isRight, themeColor, 35f, btnH)) {
                    onReplace(existing.copy(slope = 0.999f))
                }
                if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                    ImGui.setTooltip("Set LFO asymmetry fully Right (sawtooth rising / ramp up).")
                }
            }

            ImGui.spacing()

            // -- Unit Selection Dropdown (Time/Beat) if applicable --
            if (isGen) {
                UITheme.body("LFO 1 Unit:")
                ImGui.sameLine(0f, 10f)
                val unitIdx = ImInt(existing.genUnit.ordinal)
                val unitLabels = arrayOf("Time", "Beat")
                if (bypassed) ImGui.popStyleVar()
                ImGui.pushItemWidth(125f)
                if (ImGui.combo("##unit", unitIdx, unitLabels)) {
                    onReplace(existing.copy(genUnit = GenUnit.entries[unitIdx.get()]))
                }
                if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                    ImGui.setTooltip("Select frequency unit:\nTime: Rate is in seconds.\nBeat: Rate is synchronized to BPM subdivisions.")
                }
                ImGui.popItemWidth()
                if (bypassed) ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)
                ImGui.spacing()
            }
        }

        ImGui.spacing()

        // -- DC Offset ---------------------------------------------
        CustomRangeSlider.drawCustomRangeSlider(
            idPrefix = existing.id,
            label = "DC Offset",
            themeColor = themeColor,
            currentValue = existing.dcOffset,
            currentMin = existing.dcOffsetMin,
            currentMax = existing.dcOffsetMax,
            minLimit = -1f,
            maxLimit = 1f,
            isRandomizable = existing.randomizeDcOffset,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.dcOffsetMin
                    val rMax = existing.dcOffsetMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.dcOffset - 0.1f).coerceAtLeast(-1f), (existing.dcOffset + 0.1f).coerceAtMost(1f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    onReplace(existing.copy(
                        randomizeDcOffset = true,
                        dcOffsetMin = nextMin,
                        dcOffsetMax = nextMax
                    ))
                } else {
                    onReplace(existing.copy(
                        randomizeDcOffset = false,
                        dcOffsetMin = existing.dcOffset,
                        dcOffsetMax = existing.dcOffset
                    ))
                }
            },
            onRandomizeNow = {
                onReplace(existing.randomizeDcOffset())
            },
            onRangeChanged = { nextMin, nextMax ->
                val safeMin = minOf(nextMin, nextMax)
                val safeMax = maxOf(nextMin, nextMax)
                val nextActive = existing.dcOffset.coerceIn(safeMin, safeMax)
                onReplace(existing.copy(
                    dcOffsetMin = safeMin,
                    dcOffsetMax = safeMax,
                    dcOffset = nextActive
                ))
            },
            onValueChanged = { newVal ->
                onReplace(existing.copy(
                    dcOffset = newVal,
                    dcOffsetMin = newVal,
                    dcOffsetMax = newVal
                ))
            }
        )
        ImGui.spacing()

        // -- Amplitude ---------------------------------------------
        CustomRangeSlider.drawCustomRangeSlider(
            idPrefix = existing.id,
            label = "Amplitude",
            themeColor = themeColor,
            currentValue = existing.amplitude,
            currentMin = existing.amplitudeMin,
            currentMax = existing.amplitudeMax,
            minLimit = 0f,
            maxLimit = 1f,
            isRandomizable = existing.randomizeAmplitude,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.amplitudeMin
                    val rMax = existing.amplitudeMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.amplitude - 0.1f).coerceAtLeast(0f), (existing.amplitude + 0.1f).coerceAtMost(1f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    onReplace(existing.copy(
                        randomizeAmplitude = true,
                        amplitudeMin = nextMin,
                        amplitudeMax = nextMax
                    ))
                } else {
                    onReplace(existing.copy(
                        randomizeAmplitude = false,
                        amplitudeMin = existing.amplitude,
                        amplitudeMax = existing.amplitude
                    ))
                }
            },
            onRandomizeNow = {
                onReplace(existing.randomizeAmplitude())
            },
            onRangeChanged = { nextMin, nextMax ->
                val safeMin = minOf(nextMin, nextMax)
                val safeMax = maxOf(nextMin, nextMax)
                val nextActive = existing.amplitude.coerceIn(safeMin, safeMax)
                onReplace(existing.copy(
                    amplitudeMin = safeMin,
                    amplitudeMax = safeMax,
                    amplitude = nextActive
                ))
            },
            onValueChanged = { newVal ->
                onReplace(existing.copy(
                    amplitude = newVal,
                    amplitudeMin = newVal,
                    amplitudeMax = newVal
                ))
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
            
            BeatDivisionSlider.drawBeatDivisionSlider(
                idPrefix = existing.id,
                label = if (isGen) "LFO 1 Beat Div" else "Beat Div",
                themeColor = themeColor,
                currentValue = currentActiveIdx.toFloat(),
                currentMin = currentMinIdx.toFloat(),
                currentMax = currentMaxIdx.toFloat(),
                minLimit = 0f,
                maxLimit = (subdivisionOptions.size - 1).toFloat(),
                isRandomizable = existing.randomizeSubdivision,
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
        if (isLfo || (isGen && existing.genUnit == GenUnit.TIME)) {
            val formatFunc: (Float) -> String = { v -> TimeUtils.formatPeriod(v) }
            val parseFunc: (String) -> Float? = { s -> TimeUtils.parsePeriod(s) }

            CustomRangeSlider.drawCustomRangeSlider(
                idPrefix = existing.id,
                label = if (isGen) "LFO 1 Period" else "LFO Period",
                themeColor = themeColor,
                currentValue = existing.subdivision,
                currentMin = existing.subdivisionMin,
                currentMax = existing.subdivisionMax,
                minLimit = 0.01f,
                maxLimit = 86400f,
                isRandomizable = existing.randomizeSubdivision,
                formatValue = formatFunc,
                isLogarithmic = true,
                parseValue = parseFunc,
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

        // -- Phase Offset -----------------------------------------
        CustomRangeSlider.drawCustomRangeSlider(
            idPrefix = existing.id,
            label = if (isGen) "LFO 1 Phase" else "Phase Offset",
            themeColor = themeColor,
            currentValue = existing.phaseOffset,
            currentMin = existing.phaseOffsetMin,
            currentMax = existing.phaseOffsetMax,
            minLimit = 0f,
            maxLimit = 1f,
            isRandomizable = existing.randomizePhaseOffset,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.phaseOffsetMin
                    val rMax = existing.phaseOffsetMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.phaseOffset - 0.1f).coerceAtLeast(0f), (existing.phaseOffset + 0.1f).coerceAtMost(1f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    onReplace(existing.copy(
                        randomizePhaseOffset = true,
                        phaseOffsetMin = nextMin,
                        phaseOffsetMax = nextMax
                    ))
                } else {
                    onReplace(existing.copy(
                        randomizePhaseOffset = false,
                        phaseOffsetMin = existing.phaseOffset,
                        phaseOffsetMax = existing.phaseOffset
                    ))
                }
            },
            onRandomizeNow = {
                onReplace(existing.randomizePhaseOffset())
            },
            onRangeChanged = { nextMin, nextMax ->
                val safeMin = minOf(nextMin, nextMax)
                val safeMax = maxOf(nextMin, nextMax)
                val nextActive = existing.phaseOffset.coerceIn(safeMin, safeMax)
                onReplace(existing.copy(
                    phaseOffsetMin = safeMin,
                    phaseOffsetMax = safeMax,
                    phaseOffset = nextActive
                ))
            },
            onValueChanged = { newVal ->
                onReplace(existing.copy(
                    phaseOffset = newVal,
                    phaseOffsetMin = newVal,
                    phaseOffsetMax = newVal
                ))
            }
        )
        ImGui.spacing()

        // -- Morph Slider --
        CustomRangeSlider.drawCustomRangeSlider(
            idPrefix = existing.id + "_morph",
            label = if (isGen) "LFO 1 Morph" else "Morph",
            themeColor = themeColor,
            currentValue = existing.morph,
            currentMin = existing.morphMin,
            currentMax = existing.morphMax,
            minLimit = 0f,
            maxLimit = 1f,
            isRandomizable = existing.randomizeMorph,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.morphMin
                    val rMax = existing.morphMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.morph - 0.1f).coerceAtLeast(0f), (existing.morph + 0.1f).coerceAtMost(1f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    onReplace(existing.copy(
                        randomizeMorph = true,
                        morphMin = nextMin,
                        morphMax = nextMax
                    ))
                } else {
                    onReplace(existing.copy(
                        randomizeMorph = false,
                        morphMin = existing.morph,
                        morphMax = existing.morph
                    ))
                }
            },
            onRandomizeNow = {
                onReplace(existing.randomizeMorph())
            },
            onRangeChanged = { nextMin, nextMax ->
                val safeMin = minOf(nextMin, nextMax)
                val safeMax = maxOf(nextMin, nextMax)
                val nextActive = existing.morph.coerceIn(safeMin, safeMax)
                onReplace(existing.copy(
                    morphMin = safeMin,
                    morphMax = safeMax,
                    morph = nextActive
                ))
            },
            onValueChanged = { newVal ->
                onReplace(existing.copy(
                    morph = newVal,
                    morphMin = newVal,
                    morphMax = newVal
                ))
            }
        )
        ImGui.spacing()

        // -- Hold Slider --
        CustomRangeSlider.drawCustomRangeSlider(
            idPrefix = existing.id + "_hold",
            label = if (isGen) "LFO 1 Hold" else "Hold",
            themeColor = themeColor,
            currentValue = existing.hold,
            currentMin = existing.holdMin,
            currentMax = existing.holdMax,
            minLimit = 0f,
            maxLimit = 0.99f,
            isRandomizable = existing.randomizeHold,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.holdMin
                    val rMax = existing.holdMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.hold - 0.1f).coerceAtLeast(0f), (existing.hold + 0.1f).coerceAtMost(0.99f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    onReplace(existing.copy(
                        randomizeHold = true,
                        holdMin = nextMin,
                        holdMax = nextMax
                    ))
                } else {
                    onReplace(existing.copy(
                        randomizeHold = false,
                        holdMin = existing.hold,
                        holdMax = existing.hold
                    ))
                }
            },
            onRandomizeNow = {
                onReplace(existing.randomizeHold())
            },
            onRangeChanged = { nextMin, nextMax ->
                val safeMin = minOf(nextMin, nextMax)
                val safeMax = maxOf(nextMin, nextMax)
                val nextActive = existing.hold.coerceIn(safeMin, safeMax)
                onReplace(existing.copy(
                    holdMin = safeMin,
                    holdMax = safeMax,
                    hold = nextActive
                ))
            },
            onValueChanged = { newVal ->
                onReplace(existing.copy(
                    hold = newVal,
                    holdMin = newVal,
                    holdMax = newVal
                ))
            }
        )
        ImGui.spacing()

        // -- Slew / Slope Slider (only if not Random) --
        if (existing.waveform != Waveform.RANDOM) {
            CustomRangeSlider.drawCustomRangeSlider(
                idPrefix = existing.id,
                label = if (isGen) "LFO 1 Slew" else "Slew",
                themeColor = themeColor,
                currentValue = existing.slope,
                currentMin = existing.slopeMin,
                currentMax = existing.slopeMax,
                minLimit = 0.001f,
                maxLimit = 0.999f,
                isRandomizable = existing.randomizeSlope,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.slopeMin
                        val rMax = existing.slopeMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.slope - 0.1f).coerceAtLeast(0.001f), (existing.slope + 0.1f).coerceAtMost(0.999f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        onReplace(existing.copy(
                            randomizeSlope = true,
                            slopeMin = nextMin,
                            slopeMax = nextMax
                        ))
                    } else {
                        onReplace(existing.copy(
                            randomizeSlope = false,
                            slopeMin = existing.slope,
                            slopeMax = existing.slope
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeSlope())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    val nextActive = existing.slope.coerceIn(safeMin, safeMax)
                    onReplace(existing.copy(
                        slopeMin = safeMin,
                        slopeMax = safeMax,
                        slope = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    onReplace(existing.copy(
                        slope = newVal,
                        slopeMin = newVal,
                        slopeMax = newVal
                    ))
                }
            )
            ImGui.spacing()
        }
    }
}
