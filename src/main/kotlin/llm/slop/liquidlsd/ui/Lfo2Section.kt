package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.GenUnit
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.Waveform
import llm.slop.liquidlsd.utils.TimeUtils
import kotlin.math.roundToInt

object Lfo2Section {

    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        idx: Int,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        val bypassed = existing.bypassed
        val btnHeight = ImGui.getFrameHeight()
        val scale = btnHeight / 30f
        val btnWidth = 50f * scale

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        val currentMode = existing.generatorModMode
        val modeLabels = arrayOf("AM (Depth)", "PM (Phase)", "ADD (Additive)")
        val modeIdx = ImInt(if (currentMode == llm.slop.liquidlsd.parameters.GeneratorModMode.NONE) 0 else currentMode.ordinal - 1)

        val fontScale = 0.95f
        val isLfo2Active = (currentMode != llm.slop.liquidlsd.parameters.GeneratorModMode.NONE)
        val dirtyMarker = if (isLfo2Active) " [ON] •" else ""
        val lfo2Title = "${if (existing.sourceId == "lfo") "LFO 2 (Modulator)" else "Oscillator 2 (Modulator)"}$dirtyMarker###lfo2_header"

        if (ImGui.collapsingHeader(lfo2Title, 0)) {
            ImGui.spacing()

            val lfo2Bypassed = (currentMode == llm.slop.liquidlsd.parameters.GeneratorModMode.NONE)
        
        // Push styled button colors: Green for active, Red for bypassed
        val btnColor = if (lfo2Bypassed) ImGui.colorConvertFloat4ToU32(0.7f, 0.2f, 0.2f, 1f) else ImGui.colorConvertFloat4ToU32(0.1f, 0.6f, 0.2f, 1f)
        val btnHoverColor = if (lfo2Bypassed) ImGui.colorConvertFloat4ToU32(0.8f, 0.3f, 0.3f, 1f) else ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.3f, 1f)
        val btnActiveColor = if (lfo2Bypassed) ImGui.colorConvertFloat4ToU32(0.9f, 0.4f, 0.4f, 1f) else ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 0.4f, 1f)
        
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, btnColor)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, btnHoverColor)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, btnActiveColor)
        
        if (ImGui.button("${Icons.POWER}##bypass_lfo2_$idx", btnWidth, btnHeight)) {
            val nextMode = if (lfo2Bypassed) llm.slop.liquidlsd.parameters.GeneratorModMode.AM else llm.slop.liquidlsd.parameters.GeneratorModMode.NONE
            val nextDepth = if (lfo2Bypassed && existing.generatorModDepth == 0.0f) 1.0f else existing.generatorModDepth
            onReplace(existing.copy(
                generatorModMode = nextMode,
                generatorModDepth = nextDepth,
                generatorModDepthMin = if (existing.generatorModDepth == 0.0f) nextDepth else existing.generatorModDepthMin,
                generatorModDepthMax = if (existing.generatorModDepth == 0.0f) nextDepth else existing.generatorModDepthMax
            ))
        }
        itemTooltip(if (lfo2Bypassed) "Enable LFO 2 (Active)" else "Bypass LFO 2")
        ImGui.popStyleColor(3)

        // 2. Dice button for LFO 2
        if (session.uiTheme.randomizationEnabled) {
            ImGui.sameLine(0f, 10f * fontScale)
            if (param.isRandomizeDisabled) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1f, 1f, 1f, 0.25f)
                ImGui.button("${Icons.DICES}##rand_lfo2_$idx", btnWidth, btnHeight)
                ImGui.popStyleColor()
                itemTooltip(llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP)
            } else {
                if (ImGui.button("${Icons.DICES}##rand_lfo2_$idx", btnWidth, btnHeight)) {
                    val randomized = existing
                        .randomizeGeneratorModDepth()
                        .randomizeModSubdivision()
                        .randomizeModPhaseOffset()
                        .randomizeModSlope()
                        .randomizeModMorph()
                        .randomizeModHold()
                    onReplace(randomized)
                }
                itemTooltip("Randomize LFO 2 values")
            }
        }

        ImGui.spacing()

        // 1. Modulation Mode Dropdown
        session.uiTheme.body("Modulation Mode:")
        ImGui.sameLine(0f, 10f * fontScale)

        if (bypassed) ImGui.popStyleVar()
        ImGui.pushItemWidth(160f * fontScale)
        if (ImGui.combo("##gen_mod_mode", modeIdx, modeLabels)) {
            val nextMode = llm.slop.liquidlsd.parameters.GeneratorModMode.entries[modeIdx.get() + 1]
            val nextDepth = if (nextMode != llm.slop.liquidlsd.parameters.GeneratorModMode.NONE && existing.generatorModDepth == 0.0f) 1.0f else existing.generatorModDepth
            onReplace(existing.copy(
                generatorModMode = nextMode,
                generatorModDepth = nextDepth,
                generatorModDepthMin = if (existing.generatorModDepth == 0.0f) nextDepth else existing.generatorModDepthMin,
                generatorModDepthMax = if (existing.generatorModDepth == 0.0f) nextDepth else existing.generatorModDepthMax
            ))
        }
        itemTooltip("Select modulation target/mode for LFO 2:\nAM: Modulates LFO 1's Depth.\nPM: Modulates LFO 1's Phase/Frequency.\nADD: Adds LFO 2 directly to LFO 1's output.")
        ImGui.popItemWidth()
        if (bypassed) ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)

        ImGui.spacing()

        val lfo2Disabled = (currentMode == llm.slop.liquidlsd.parameters.GeneratorModMode.NONE)

        if (lfo2Disabled) {
            ImGui.beginDisabled()
        }

        val btnW = 35f * fontScale
        val btnH = ImGui.getFrameHeight()

        // 2. LFO 2 Shape Preset buttons
        session.uiTheme.body("LFO 2 Shape:")
        ImGui.sameLine(0f, 10f * fontScale)

        // Sine Button
        val isModSine = existing.modWaveform == Waveform.SINE && existing.modMorph == 0.0f && existing.modHold == 0.0f
        if (CustomIconButton.drawWaveformButton("lfo2_sine_$idx", WaveShape.SINE, isModSine, themeColor, btnW, btnH)) {
            onReplace(existing.copy(
                modWaveform = Waveform.SINE,
                modMorph = 0.0f,
                modHold = 0.0f
            ))
        }
            itemTooltip("Load standard smooth Sine wave for LFO 2.")

        // Triangle Button
        ImGui.sameLine(0f, 4f * fontScale)
        val isModTri = existing.modWaveform == Waveform.TRIANGLE && existing.modMorph == 1.0f && existing.modHold == 0.0f
        if (CustomIconButton.drawWaveformButton("lfo2_tri_$idx", WaveShape.TRIANGLE, isModTri, themeColor, btnW, btnH)) {
            onReplace(existing.copy(
                modWaveform = Waveform.TRIANGLE,
                modMorph = 1.0f,
                modHold = 0.0f
            ))
        }
        itemTooltip("Load linear Triangle wave for LFO 2.")

        // Square Button
        ImGui.sameLine(0f, 4f * fontScale)
        val isModSquare = existing.modWaveform == Waveform.SQUARE && existing.modMorph == 1.0f && existing.modHold >= 0.99f
        if (CustomIconButton.drawWaveformButton("lfo2_square_$idx", WaveShape.SQUARE, isModSquare, themeColor, btnW, btnH)) {
            onReplace(existing.copy(
                modWaveform = Waveform.SQUARE,
                modMorph = 1.0f,
                modHold = 0.999f
            ))
        }
        itemTooltip("Load binary Square wave for LFO 2.")

        // Random Button
        ImGui.sameLine(0f, 4f * fontScale)
        val isModRandom = existing.modWaveform == Waveform.RANDOM
        if (CustomIconButton.drawWaveformButton("lfo2_random_$idx", WaveShape.RANDOM, isModRandom, themeColor, btnW, btnH)) {
            onReplace(existing.copy(
                modWaveform = Waveform.RANDOM
            ))
        }
        itemTooltip("Load step or smooth Random noise for LFO 2.")

        // 3. LFO 2 Slew / Duty Preset buttons (only if not Random)
        if (existing.modWaveform != Waveform.RANDOM) {
            val isModSquareWave = existing.modWaveform == Waveform.SQUARE
            session.uiTheme.body(if (isModSquareWave) "Duty Preset:" else "Asymmetry:")
            ImGui.sameLine(0f, 10f * fontScale)

            // Left Button
            val isModLeft = if (isModSquareWave) existing.modSlope <= 0.1f else existing.modSlope <= 0.01f
            if (CustomIconButton.drawWaveformButton("lfo2_left_$idx", if (isModSquareWave) WaveShape.SQUARE_10 else WaveShape.RAMP_DOWN, isModLeft, themeColor, btnW, btnH)) {
                onReplace(existing.copy(modSlope = if (isModSquareWave) 0.1f else 0.001f))
            }
            itemTooltip(if (isModSquareWave) "Set LFO 2 duty cycle to 10% (narrow pulse)." else "Set LFO 2 asymmetry fully Left.")

            // Center Button
            ImGui.sameLine(0f, 4f * fontScale)
            val isModCenter = existing.modSlope >= 0.49f && existing.modSlope <= 0.51f
            if (CustomIconButton.drawWaveformButton("lfo2_center_$idx", if (isModSquareWave) WaveShape.SQUARE else WaveShape.TRIANGLE, isModCenter, themeColor, btnW, btnH)) {
                onReplace(existing.copy(modSlope = 0.5f))
            }
            itemTooltip(if (isModSquareWave) "Set LFO 2 duty cycle to 50% (balanced square wave)." else "Set LFO 2 asymmetry to Center.")

            // Right Button
            ImGui.sameLine(0f, 4f * fontScale)
            val isModRight = if (isModSquareWave) existing.modSlope >= 0.9f else existing.modSlope >= 0.99f
            if (CustomIconButton.drawWaveformButton("lfo2_right_$idx", if (isModSquareWave) WaveShape.SQUARE_90 else WaveShape.RAMP_UP, isModRight, themeColor, btnW, btnH)) {
                onReplace(existing.copy(modSlope = if (isModSquareWave) 0.9f else 0.999f))
            }
            itemTooltip(if (isModSquareWave) "Set LFO 2 duty cycle to 90% (wide pulse)." else "Set LFO 2 asymmetry fully Right.")
        }

        ImGui.spacing()

        // 4. LFO 2 Unit Dropdown
        session.uiTheme.body("LFO 2 Unit:")
        ImGui.sameLine(0f, 10f * fontScale)
        val modUnitIdx = ImInt(existing.modGenUnit.ordinal)
        val modUnitLabels = arrayOf("Time", "Beat", "Frame")
        if (bypassed) ImGui.popStyleVar()
        ImGui.pushItemWidth(110f * fontScale)
        if (ImGui.combo("##mod_unit", modUnitIdx, modUnitLabels)) {
            val selectedUnit = GenUnit.entries[modUnitIdx.get()]
            val adjustedSubdiv = when (selectedUnit) {
                GenUnit.FRAME -> existing.modSubdivision.coerceIn(1f, 10000f).toInt().toFloat()
                GenUnit.TIME -> existing.modSubdivision.coerceIn(0.01f, 86400f)
                GenUnit.BEAT -> {
                    val options = BeatDivisionSlider.subdivisionOptions
                    options.minByOrNull { kotlin.math.abs(it - existing.modSubdivision) } ?: 1.0f
                }
            }
            val adjustedMin = when (selectedUnit) {
                GenUnit.FRAME -> existing.modSubdivisionMin.coerceIn(1f, 10000f).toInt().toFloat()
                GenUnit.TIME -> existing.modSubdivisionMin.coerceIn(0.01f, 86400f)
                GenUnit.BEAT -> {
                    val options = BeatDivisionSlider.subdivisionOptions
                    options.minByOrNull { kotlin.math.abs(it - existing.modSubdivisionMin) } ?: 1.0f
                }
            }
            val adjustedMax = when (selectedUnit) {
                GenUnit.FRAME -> existing.modSubdivisionMax.coerceIn(1f, 10000f).toInt().toFloat()
                GenUnit.TIME -> existing.modSubdivisionMax.coerceIn(0.01f, 86400f)
                GenUnit.BEAT -> {
                    val options = BeatDivisionSlider.subdivisionOptions
                    options.minByOrNull { kotlin.math.abs(it - existing.modSubdivisionMax) } ?: 1.0f
                }
            }
            onReplace(existing.copy(
                modGenUnit = selectedUnit,
                modSubdivision = adjustedSubdiv,
                modSubdivisionMin = adjustedMin,
                modSubdivisionMax = adjustedMax
            ))
        }
        itemTooltip("Select frequency unit for LFO 2:\nTime: Rate is in seconds.\nBeat: Rate is synchronized to BPM subdivisions.\nFrame: Rate is synchronized to render frame count (1-10000 frames).")
        ImGui.popItemWidth()
        if (bypassed) ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)

        if (lfo2Disabled) {
            ImGui.endDisabled()
        }

        ImGui.spacing()

        if (currentMode != llm.slop.liquidlsd.parameters.GeneratorModMode.NONE) {
            ImGui.spacing()

            // Modulation Depth range slider
            val modDepthCbs = cvModulatorSlider(
                existing = existing,
                getValue = { generatorModDepth }, getMin = { generatorModDepthMin }, getMax = { generatorModDepthMax },
                minLimit = 0f, maxLimit = 1f,
                copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeGeneratorModDepth = enabled, generatorModDepthMin = nMin, generatorModDepthMax = nMax) },
                copyWithRange   = { sMin, sMax, v -> copy(generatorModDepthMin = sMin, generatorModDepthMax = sMax, generatorModDepth = v) },
                copyWithValue   = { v -> copy(generatorModDepth = v, generatorModDepthMin = v, generatorModDepthMax = v) },
                randomizeNow    = { randomizeGeneratorModDepth() },
                onReplace = onReplace,
            )
            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id + "_mod_depth",
                label = "Mod Depth",
                themeColor = themeColor,
                currentValue = existing.generatorModDepth,
                currentMin = existing.generatorModDepthMin,
                currentMax = existing.generatorModDepthMax,
                minLimit = 0f, maxLimit = 1f, defaultValue = 1.0f,
                isRandomizable = existing.randomizeGeneratorModDepth,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = modDepthCbs.onRandomizableChanged,
                onRandomizeNow        = modDepthCbs.onRandomizeNow,
                onRangeChanged        = modDepthCbs.onRangeChanged,
                onValueChanged        = modDepthCbs.onValueChanged,
            )

            ImGui.spacing()

            // LFO 2 Speed (Subdivision, Period, or Frames)
            if (existing.modGenUnit == GenUnit.BEAT) {
                val subdivisionOptions = BeatDivisionSlider.subdivisionOptions
                val subdivisionLabels = BeatDivisionSlider.subdivisionLabels
                val currentMinIdx = subdivisionOptions.indexOfFirst { it == existing.modSubdivisionMin }.coerceAtLeast(0)
                val currentMaxIdx = subdivisionOptions.indexOfFirst { it == existing.modSubdivisionMax }.coerceAtLeast(0)
                val currentActiveIdx = subdivisionOptions.indexOfFirst { it == existing.modSubdivision }.coerceAtLeast(0)

                BeatDivisionSlider.drawBeatDivisionSlider(session, idPrefix = existing.id + "_mod",
                    label = "LFO 2 Beat Div",
                    themeColor = themeColor,
                    currentValue = currentActiveIdx.toFloat(),
                    currentMin = currentMinIdx.toFloat(),
                    currentMax = currentMaxIdx.toFloat(),
                    minLimit = 0f,
                    maxLimit = (subdivisionOptions.size - 1).toFloat(),
                    defaultValue = subdivisionOptions.indexOfFirst { it == 1.0f }.coerceAtLeast(0).toFloat(),
                    isRandomizable = existing.randomizeModSubdivision,
                    isRandomizeDisabled = param.isRandomizeDisabled,
                    randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                    formatValue = { idx -> subdivisionLabels[idx.toInt().coerceIn(0, subdivisionOptions.size - 1)] },
                    onRandomizableChanged = { checked ->
                        if (checked) {
                            val rMin = existing.modSubdivisionMin
                            val rMax = existing.modSubdivisionMax
                            val (nextMin, nextMax) = if (rMin == rMax) {
                                val idx = subdivisionOptions.indexOfFirst { it == rMin }.coerceIn(0, subdivisionOptions.size - 1)
                                val minIdx = (idx - 1).coerceAtLeast(0)
                                val maxIdx = (idx + 1).coerceAtMost(subdivisionOptions.size - 1)
                                Pair(subdivisionOptions[minIdx], subdivisionOptions[maxIdx])
                            } else {
                                Pair(rMin, rMax)
                            }
                            onReplace(existing.copy(
                                randomizeModSubdivision = true,
                                modSubdivisionMin = nextMin,
                                modSubdivisionMax = nextMax
                            ))
                        } else {
                            onReplace(existing.copy(
                                randomizeModSubdivision = false,
                                modSubdivisionMin = existing.modSubdivision,
                                modSubdivisionMax = existing.modSubdivision
                            ))
                        }
                    },
                    onRandomizeNow = {
                        onReplace(existing.randomizeModSubdivision())
                    },
                    onRangeChanged = { nextMinIdx, nextMaxIdx ->
                        val rawMinVal = subdivisionOptions[nextMinIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                        val rawMaxVal = subdivisionOptions[nextMaxIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                        val nextMinVal = minOf(rawMinVal, rawMaxVal)
                        val nextMaxVal = maxOf(rawMinVal, rawMaxVal)
                        val nextActive = existing.modSubdivision.coerceIn(nextMinVal, nextMaxVal)
                        onReplace(existing.copy(
                            modSubdivisionMin = nextMinVal,
                            modSubdivisionMax = nextMaxVal,
                            modSubdivision = nextActive
                        ))
                    },
                    onValueChanged = { newValIdx ->
                        val newVal = subdivisionOptions[newValIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                        onReplace(existing.copy(
                            modSubdivision = newVal,
                            modSubdivisionMin = newVal,
                            modSubdivisionMax = newVal
                        ))
                    }
                )
                ImGui.spacing()
            } else if (existing.modGenUnit == GenUnit.FRAME) {
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

                CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id + "_mod",
                    label = "LFO 2 Frames",
                    themeColor = themeColor,
                    currentValue = existing.modSubdivision.toInt().coerceIn(1, 10000).toFloat(),
                    currentMin = existing.modSubdivisionMin.toInt().coerceIn(1, 10000).toFloat(),
                    currentMax = existing.modSubdivisionMax.toInt().coerceIn(1, 10000).toFloat(),
                    minLimit = 1f, maxLimit = 10000f, defaultValue = 1f,
                    isRandomizable = existing.randomizeModSubdivision,
                    isRandomizeDisabled = param.isRandomizeDisabled,
                    randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                    formatValue = formatFunc,
                    formatLabel = formatLabelFunc,
                    isLogarithmic = true,
                    parseValue = parseFunc,
                    // Frames use integer halving/doubling for expansion — kept inline.
                    onRandomizableChanged = { checked ->
                        if (checked) {
                            val rMin = existing.modSubdivisionMin.toInt().coerceIn(1, 10000)
                            val rMax = existing.modSubdivisionMax.toInt().coerceIn(1, 10000)
                            val cur = existing.modSubdivision.toInt().coerceIn(1, 10000)
                            val (nextMin, nextMax) = if (rMin == rMax) {
                                Pair((cur / 2).coerceIn(1, 10000).toFloat(), (cur * 2).coerceIn(1, 10000).toFloat())
                            } else {
                                Pair(rMin.toFloat(), rMax.toFloat())
                            }
                            onReplace(existing.copy(
                                randomizeModSubdivision = true,
                                modSubdivisionMin = nextMin,
                                modSubdivisionMax = nextMax
                            ))
                        } else {
                            val cur = existing.modSubdivision.toInt().coerceIn(1, 10000).toFloat()
                            onReplace(existing.copy(
                                randomizeModSubdivision = false,
                                modSubdivisionMin = cur,
                                modSubdivisionMax = cur
                            ))
                        }
                    },
                    onRandomizeNow = {
                        onReplace(existing.randomizeModSubdivision())
                    },
                    onRangeChanged = { nextMin, nextMax ->
                        val roundedMin = nextMin.toInt().coerceIn(1, 10000).toFloat()
                        val roundedMax = nextMax.toInt().coerceIn(1, 10000).toFloat()
                        val safeMin = minOf(roundedMin, roundedMax)
                        val safeMax = maxOf(roundedMin, roundedMax)
                        val nextActive = existing.modSubdivision.toInt().coerceIn(safeMin.toInt(), safeMax.toInt()).toFloat()
                        onReplace(existing.copy(
                            modSubdivisionMin = safeMin,
                            modSubdivisionMax = safeMax,
                            modSubdivision = nextActive
                        ))
                    },
                    onValueChanged = { newVal ->
                        val roundedVal = newVal.toInt().coerceIn(1, 10000).toFloat()
                        onReplace(existing.copy(
                            modSubdivision = roundedVal,
                            modSubdivisionMin = roundedVal,
                            modSubdivisionMax = roundedVal
                        ))
                    }
                )
                ImGui.spacing()
            } else {
                val formatFunc: (Float) -> String = { v -> TimeUtils.formatPeriod(v) }
                val parseFunc: (String) -> Float? = { s -> TimeUtils.parsePeriod(s) }

                CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id + "_mod",
                    label = "LFO 2 Period",
                    themeColor = themeColor,
                    currentValue = existing.modSubdivision,
                    currentMin = existing.modSubdivisionMin,
                    currentMax = existing.modSubdivisionMax,
                    minLimit = 0.01f, maxLimit = 86400f, defaultValue = 1.0f,
                    isRandomizable = existing.randomizeModSubdivision,
                    isRandomizeDisabled = param.isRandomizeDisabled,
                    randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                    formatValue = formatFunc,
                    isLogarithmic = true,
                    parseValue = parseFunc,
                    // Period uses multiplicative expansion — kept inline.
                    onRandomizableChanged = { checked ->
                        if (checked) {
                            val rMin = existing.modSubdivisionMin
                            val rMax = existing.modSubdivisionMax
                            val (nextMin, nextMax) = if (rMin == rMax) {
                                Pair((existing.modSubdivision * 0.5f).coerceIn(0.01f, 86400f), (existing.modSubdivision * 2f).coerceIn(0.01f, 86400f))
                            } else {
                                Pair(rMin, rMax)
                            }
                            onReplace(existing.copy(
                                randomizeModSubdivision = true,
                                modSubdivisionMin = nextMin,
                                modSubdivisionMax = nextMax
                            ))
                        } else {
                            onReplace(existing.copy(
                                randomizeModSubdivision = false,
                                modSubdivisionMin = existing.modSubdivision,
                                modSubdivisionMax = existing.modSubdivision
                            ))
                        }
                    },
                    onRandomizeNow = {
                        onReplace(existing.randomizeModSubdivision())
                    },
                    onRangeChanged = { nextMin, nextMax ->
                        val safeMin = minOf(nextMin, nextMax)
                        val safeMax = maxOf(nextMin, nextMax)
                        val nextActive = existing.modSubdivision.coerceIn(safeMin, safeMax)
                        onReplace(existing.copy(
                            modSubdivisionMin = safeMin,
                            modSubdivisionMax = safeMax,
                            modSubdivision = nextActive
                        ))
                    },
                    onValueChanged = { newVal ->
                        onReplace(existing.copy(
                            modSubdivision = newVal,
                            modSubdivisionMin = newVal,
                            modSubdivisionMax = newVal
                        ))
                    }
                )
                ImGui.spacing()
            }

            // LFO 2 Phase Offset
            val modPhaseCbs = cvModulatorSlider(
                existing = existing,
                getValue = { modPhaseOffset }, getMin = { modPhaseOffsetMin }, getMax = { modPhaseOffsetMax },
                minLimit = 0f, maxLimit = 1f,
                copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeModPhaseOffset = enabled, modPhaseOffsetMin = nMin, modPhaseOffsetMax = nMax) },
                copyWithRange   = { sMin, sMax, v -> copy(modPhaseOffsetMin = sMin, modPhaseOffsetMax = sMax, modPhaseOffset = v) },
                copyWithValue   = { v -> copy(modPhaseOffset = v, modPhaseOffsetMin = v, modPhaseOffsetMax = v) },
                randomizeNow    = { randomizeModPhaseOffset() },
                onReplace = onReplace,
            )
            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id + "_mod_phase",
                label = "LFO 2 Phase",
                themeColor = themeColor,
                currentValue = existing.modPhaseOffset,
                currentMin = existing.modPhaseOffsetMin,
                currentMax = existing.modPhaseOffsetMax,
                minLimit = 0f, maxLimit = 1f, defaultValue = 0f,
                isRandomizable = existing.randomizeModPhaseOffset,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = modPhaseCbs.onRandomizableChanged,
                onRandomizeNow        = modPhaseCbs.onRandomizeNow,
                onRangeChanged        = modPhaseCbs.onRangeChanged,
                onValueChanged        = modPhaseCbs.onValueChanged,
            )
            ImGui.spacing()

            // LFO 2 Morph
            val modMorphCbs = cvModulatorSlider(
                existing = existing,
                getValue = { modMorph }, getMin = { modMorphMin }, getMax = { modMorphMax },
                minLimit = 0f, maxLimit = 1f,
                copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeModMorph = enabled, modMorphMin = nMin, modMorphMax = nMax) },
                copyWithRange   = { sMin, sMax, v -> copy(modMorphMin = sMin, modMorphMax = sMax, modMorph = v) },
                copyWithValue   = { v -> copy(modMorph = v, modMorphMin = v, modMorphMax = v) },
                randomizeNow    = { randomizeModMorph() },
                onReplace = onReplace,
            )
            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id + "_mod_morph",
                label = "LFO 2 Morph",
                themeColor = themeColor,
                currentValue = existing.modMorph,
                currentMin = existing.modMorphMin,
                currentMax = existing.modMorphMax,
                minLimit = 0f, maxLimit = 1f, defaultValue = 0f,
                isRandomizable = existing.randomizeModMorph,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = modMorphCbs.onRandomizableChanged,
                onRandomizeNow        = modMorphCbs.onRandomizeNow,
                onRangeChanged        = modMorphCbs.onRangeChanged,
                onValueChanged        = modMorphCbs.onValueChanged,
            )
            ImGui.spacing()

            // LFO 2 Hold
            val modHoldCbs = cvModulatorSlider(
                existing = existing,
                getValue = { modHold }, getMin = { modHoldMin }, getMax = { modHoldMax },
                minLimit = 0f, maxLimit = 0.999f,
                copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeModHold = enabled, modHoldMin = nMin, modHoldMax = nMax) },
                copyWithRange   = { sMin, sMax, v -> copy(modHoldMin = sMin, modHoldMax = sMax, modHold = v) },
                copyWithValue   = { v -> copy(modHold = v, modHoldMin = v, modHoldMax = v) },
                randomizeNow    = { randomizeModHold() },
                onReplace = onReplace,
            )
            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id + "_mod_hold",
                label = "LFO 2 Hold",
                themeColor = themeColor,
                currentValue = existing.modHold,
                currentMin = existing.modHoldMin,
                currentMax = existing.modHoldMax,
                minLimit = 0f, maxLimit = 0.999f, defaultValue = 0f,
                isRandomizable = existing.randomizeModHold,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = modHoldCbs.onRandomizableChanged,
                onRandomizeNow        = modHoldCbs.onRandomizeNow,
                onRangeChanged        = modHoldCbs.onRangeChanged,
                onValueChanged        = modHoldCbs.onValueChanged,
            )
            ImGui.spacing()

            // LFO 2 Slew / Duty Cycle (mod slope, if not random)
            if (existing.modWaveform != Waveform.RANDOM) {
                val isModSquareWave = existing.modWaveform == Waveform.SQUARE
                val modSlopeCbs = cvModulatorSlider(
                    existing = existing,
                    getValue = { modSlope }, getMin = { modSlopeMin }, getMax = { modSlopeMax },
                    minLimit = 0.001f, maxLimit = 0.999f,
                    copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeModSlope = enabled, modSlopeMin = nMin, modSlopeMax = nMax) },
                    copyWithRange   = { sMin, sMax, v -> copy(modSlopeMin = sMin, modSlopeMax = sMax, modSlope = v) },
                    copyWithValue   = { v -> copy(modSlope = v, modSlopeMin = v, modSlopeMax = v) },
                    randomizeNow    = { randomizeModSlope() },
                    onReplace = onReplace,
                )
                CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = existing.id + "_mod_slope",
                    label = if (isModSquareWave) "LFO 2 Duty Cycle" else "LFO 2 Slew",
                    themeColor = themeColor,
                    currentValue = existing.modSlope,
                    currentMin = existing.modSlopeMin,
                    currentMax = existing.modSlopeMax,
                    minLimit = 0.001f, maxLimit = 0.999f, defaultValue = 0.5f,
                    isRandomizable = existing.randomizeModSlope,
                    isRandomizeDisabled = param.isRandomizeDisabled,
                    randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                    formatValue = { "%.3f".format(it) },
                    onRandomizableChanged = modSlopeCbs.onRandomizableChanged,
                    onRandomizeNow        = modSlopeCbs.onRandomizeNow,
                    onRangeChanged        = modSlopeCbs.onRangeChanged,
                    onValueChanged        = modSlopeCbs.onValueChanged,
                )
                ImGui.spacing()
            }
            }
        }
    }
}
