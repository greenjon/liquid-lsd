package llm.slop.spirals.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.GenUnit
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.Waveform
import llm.slop.spirals.utils.TimeUtils
import kotlin.math.roundToInt

object Lfo2Section {

    fun draw(
        state: PatchGridState,
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
        val modeLabels = arrayOf("AM (Amplitude)", "PM (Phase)", "ADD (Additive)")
        val modeIdx = ImInt(if (currentMode == llm.slop.spirals.parameters.GeneratorModMode.NONE) 0 else currentMode.ordinal - 1)

        val lfo2Bypassed = (currentMode == llm.slop.spirals.parameters.GeneratorModMode.NONE)
        val btnY2 = ImGui.getCursorScreenPosY()
        
        if (UITheme.iconButton("##bypass_lfo2_$idx", Icons.POWER, if (lfo2Bypassed) "Enable LFO 2 (Active)" else "Bypass LFO 2", active = !lfo2Bypassed, width = btnWidth, height = btnHeight)) {
            val nextMode = if (lfo2Bypassed) llm.slop.spirals.parameters.GeneratorModMode.AM else llm.slop.spirals.parameters.GeneratorModMode.NONE
            onReplace(existing.copy(generatorModMode = nextMode))
        }

        // 2. Dice button for LFO 2
        ImGui.sameLine(0f, 10f)
        if (UITheme.iconButton("##rand_lfo2_$idx", Icons.DICES, "Randomize LFO 2 values", width = btnWidth, height = btnHeight)) {
            val randomized = existing
                .randomizeGeneratorModDepth()
                .randomizeModSubdivision()
                .randomizeModPhaseOffset()
                .randomizeModSlope()
                .randomizeModMorph()
                .randomizeModHold()
            onReplace(randomized)
        }

        // Title text for LFO 2
        ImGui.sameLine(0f, 225f)
        val alignY = btnY2 + (btnHeight - ImGui.getTextLineHeightWithSpacing()) / 2f
        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), alignY)
        val lfo2Title = if (existing.sourceId == "lfo") "LFO 2" else "Oscillator 2"
        UITheme.h2(lfo2Title)
        
        ImGui.spacing()

        val startX = ImGui.getCursorPosX()
        val lfo2Disabled = (currentMode == llm.slop.spirals.parameters.GeneratorModMode.NONE)

        if (lfo2Disabled) {
            ImGui.beginDisabled()
        }

        val btnW = 35f
        val btnH = ImGui.getFrameHeight()

        // 1. LFO 2 Shape Preset buttons
        UITheme.body("LFO 2 Shape:")
        ImGui.sameLine(0f, 10f)

        // Sine Button
        val isModSine = existing.modWaveform == Waveform.SINE && existing.modMorph == 0.0f && existing.modHold == 0.0f
        if (CustomIconButton.drawWaveformButton("lfo2_sine_$idx", WaveShape.SINE, isModSine, themeColor, btnW, btnH)) {
            onReplace(existing.copy(
                modWaveform = Waveform.SINE,
                modMorph = 0.0f,
                modHold = 0.0f
            ))
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Load standard smooth Sine wave for LFO 2.")
        }

        // Triangle Button
        ImGui.sameLine(0f, 4f)
        val isModTri = existing.modWaveform == Waveform.TRIANGLE && existing.modMorph == 1.0f && existing.modHold == 0.0f
        if (CustomIconButton.drawWaveformButton("lfo2_tri_$idx", WaveShape.TRIANGLE, isModTri, themeColor, btnW, btnH)) {
            onReplace(existing.copy(
                modWaveform = Waveform.TRIANGLE,
                modMorph = 1.0f,
                modHold = 0.0f
            ))
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Load linear Triangle wave for LFO 2.")
        }

        // Square Button
        ImGui.sameLine(0f, 4f)
        val isModSquare = existing.modWaveform == Waveform.SQUARE && existing.modMorph == 1.0f && existing.modHold == 0.5f
        if (CustomIconButton.drawWaveformButton("lfo2_square_$idx", WaveShape.SQUARE, isModSquare, themeColor, btnW, btnH)) {
            onReplace(existing.copy(
                modWaveform = Waveform.SQUARE,
                modMorph = 1.0f,
                modHold = 0.5f
            ))
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Load binary Square wave for LFO 2.")
        }

        // Random Button
        ImGui.sameLine(0f, 4f)
        val isModRandom = existing.modWaveform == Waveform.RANDOM
        if (CustomIconButton.drawWaveformButton("lfo2_random_$idx", WaveShape.RANDOM, isModRandom, themeColor, btnW, btnH)) {
            onReplace(existing.copy(
                modWaveform = Waveform.RANDOM
            ))
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Load step or smooth Random noise for LFO 2.")
        }

        // 2. LFO 2 Slew Preset buttons (only if not Random)
        if (existing.modWaveform != Waveform.RANDOM) {
            ImGui.sameLine(0f, 20f)
            UITheme.body("Asymmetry:")
            ImGui.sameLine(0f, 10f)

            // Left Button
            val isModLeft = existing.modSlope <= 0.01f
            if (CustomIconButton.drawWaveformButton("lfo2_left_$idx", WaveShape.RAMP_DOWN, isModLeft, themeColor, 35f, btnH)) {
                onReplace(existing.copy(modSlope = 0.001f))
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Set LFO 2 asymmetry fully Left.")
            }

            // Center Button
            ImGui.sameLine(0f, 4f)
            val isModCenter = existing.modSlope >= 0.49f && existing.modSlope <= 0.51f
            if (CustomIconButton.drawWaveformButton("lfo2_center_$idx", WaveShape.TRIANGLE, isModCenter, themeColor, 35f, btnH)) {
                onReplace(existing.copy(modSlope = 0.5f))
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Set LFO 2 asymmetry to Center.")
            }

            // Right Button
            ImGui.sameLine(0f, 4f)
            val isModRight = existing.modSlope >= 0.99f
            if (CustomIconButton.drawWaveformButton("lfo2_right_$idx", WaveShape.RAMP_UP, isModRight, themeColor, 35f, btnH)) {
                onReplace(existing.copy(modSlope = 0.999f))
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Set LFO 2 asymmetry fully Right.")
            }
        }

        ImGui.spacing()

        // 3. Dropdowns Row for Unit and Mode
        UITheme.body("LFO 2 Unit:")
        ImGui.sameLine(0f, 10f)
        val modUnitIdx = ImInt(existing.modGenUnit.ordinal)
        val modUnitLabels = arrayOf("Time", "Beat")
        if (bypassed) ImGui.popStyleVar()
        ImGui.pushItemWidth(125f)
        if (ImGui.combo("##mod_unit", modUnitIdx, modUnitLabels)) {
            onReplace(existing.copy(modGenUnit = GenUnit.entries[modUnitIdx.get()]))
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Select frequency unit for LFO 2:\nTime: Rate is in seconds.\nBeat: Rate is synchronized to BPM subdivisions.")
        }
        ImGui.popItemWidth()
        if (bypassed) ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)

        if (lfo2Disabled) {
            ImGui.endDisabled()
        }

        ImGui.sameLine(0f, 20f)
        UITheme.body("Modulation Mode:")
        ImGui.sameLine(0f, 10f)

        if (bypassed) ImGui.popStyleVar()
        ImGui.pushItemWidth(200f)
        if (ImGui.combo("##gen_mod_mode", modeIdx, modeLabels)) {
            val nextMode = llm.slop.spirals.parameters.GeneratorModMode.entries[modeIdx.get() + 1]
            onReplace(existing.copy(generatorModMode = nextMode))
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Select modulation target/mode for LFO 2:\nAM: Modulates LFO 1's Amplitude.\nPM: Modulates LFO 1's Phase/Frequency.\nADD: Adds LFO 2 directly to LFO 1's output.")
        }
        ImGui.popItemWidth()
        if (bypassed) ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)

        ImGui.spacing()

        if (currentMode != llm.slop.spirals.parameters.GeneratorModMode.NONE) {
            ImGui.spacing()

            // Modulation Depth range slider
            CustomRangeSlider.drawCustomRangeSlider(
                idPrefix = existing.id + "_mod_depth",
                label = "Mod Depth",
                themeColor = themeColor,
                currentValue = existing.generatorModDepth,
                currentMin = existing.generatorModDepthMin,
                currentMax = existing.generatorModDepthMax,
                minLimit = 0f,
                maxLimit = 1f,
                isRandomizable = existing.randomizeGeneratorModDepth,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.generatorModDepthMin
                        val rMax = existing.generatorModDepthMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.generatorModDepth - 0.1f).coerceAtLeast(0f), (existing.generatorModDepth + 0.1f).coerceAtMost(1f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        onReplace(existing.copy(
                            randomizeGeneratorModDepth = true,
                            generatorModDepthMin = nextMin,
                            generatorModDepthMax = nextMax
                        ))
                    } else {
                        onReplace(existing.copy(
                            randomizeGeneratorModDepth = false,
                            generatorModDepthMin = existing.generatorModDepth,
                            generatorModDepthMax = existing.generatorModDepth
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeGeneratorModDepth())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    val nextActive = existing.generatorModDepth.coerceIn(safeMin, safeMax)
                    onReplace(existing.copy(
                        generatorModDepthMin = safeMin,
                        generatorModDepthMax = safeMax,
                        generatorModDepth = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    onReplace(existing.copy(
                        generatorModDepth = newVal,
                        generatorModDepthMin = newVal,
                        generatorModDepthMax = newVal
                    ))
                }
            )

            ImGui.spacing()

            // LFO 2 Speed (Subdivision or Period)
            if (existing.modGenUnit == GenUnit.BEAT) {
                val subdivisionOptions = BeatDivisionSlider.subdivisionOptions
                val subdivisionLabels = BeatDivisionSlider.subdivisionLabels
                val currentMinIdx = subdivisionOptions.indexOfFirst { it == existing.modSubdivisionMin }.coerceAtLeast(0)
                val currentMaxIdx = subdivisionOptions.indexOfFirst { it == existing.modSubdivisionMax }.coerceAtLeast(0)
                val currentActiveIdx = subdivisionOptions.indexOfFirst { it == existing.modSubdivision }.coerceAtLeast(0)

                BeatDivisionSlider.drawBeatDivisionSlider(
                    idPrefix = existing.id + "_mod",
                    label = "LFO 2 Beat Div",
                    themeColor = themeColor,
                    currentValue = currentActiveIdx.toFloat(),
                    currentMin = currentMinIdx.toFloat(),
                    currentMax = currentMaxIdx.toFloat(),
                    minLimit = 0f,
                    maxLimit = (subdivisionOptions.size - 1).toFloat(),
                    isRandomizable = existing.randomizeModSubdivision,
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
            } else {
                val formatFunc: (Float) -> String = { v -> TimeUtils.formatPeriod(v) }
                val parseFunc: (String) -> Float? = { s -> TimeUtils.parsePeriod(s) }

                CustomRangeSlider.drawCustomRangeSlider(
                    idPrefix = existing.id + "_mod",
                    label = "LFO 2 Period",
                    themeColor = themeColor,
                    currentValue = existing.modSubdivision,
                    currentMin = existing.modSubdivisionMin,
                    currentMax = existing.modSubdivisionMax,
                    minLimit = 0.01f,
                    maxLimit = 86400f,
                    isRandomizable = existing.randomizeModSubdivision,
                    formatValue = formatFunc,
                    isLogarithmic = true,
                    parseValue = parseFunc,
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
            CustomRangeSlider.drawCustomRangeSlider(
                idPrefix = existing.id + "_mod_phase",
                label = "LFO 2 Phase",
                themeColor = themeColor,
                currentValue = existing.modPhaseOffset,
                currentMin = existing.modPhaseOffsetMin,
                currentMax = existing.modPhaseOffsetMax,
                minLimit = 0f,
                maxLimit = 1f,
                isRandomizable = existing.randomizeModPhaseOffset,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.modPhaseOffsetMin
                        val rMax = existing.modPhaseOffsetMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.modPhaseOffset - 0.1f).coerceAtLeast(0f), (existing.modPhaseOffset + 0.1f).coerceAtMost(1f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        onReplace(existing.copy(
                            randomizeModPhaseOffset = true,
                            modPhaseOffsetMin = nextMin,
                            modPhaseOffsetMax = nextMax
                        ))
                    } else {
                        onReplace(existing.copy(
                            randomizeModPhaseOffset = false,
                            modPhaseOffsetMin = existing.modPhaseOffset,
                            modPhaseOffsetMax = existing.modPhaseOffset
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeModPhaseOffset())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    val nextActive = existing.modPhaseOffset.coerceIn(safeMin, safeMax)
                    onReplace(existing.copy(
                        modPhaseOffsetMin = safeMin,
                        modPhaseOffsetMax = safeMax,
                        modPhaseOffset = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    onReplace(existing.copy(
                        modPhaseOffset = newVal,
                        modPhaseOffsetMin = newVal,
                        modPhaseOffsetMax = newVal
                    ))
                }
            )
            ImGui.spacing()

            // LFO 2 Morph
            CustomRangeSlider.drawCustomRangeSlider(
                idPrefix = existing.id + "_mod_morph",
                label = "LFO 2 Morph",
                themeColor = themeColor,
                currentValue = existing.modMorph,
                currentMin = existing.modMorphMin,
                currentMax = existing.modMorphMax,
                minLimit = 0f,
                maxLimit = 1f,
                isRandomizable = existing.randomizeModMorph,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.modMorphMin
                        val rMax = existing.modMorphMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.modMorph - 0.1f).coerceAtLeast(0f), (existing.modMorph + 0.1f).coerceAtMost(1f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        onReplace(existing.copy(
                            randomizeModMorph = true,
                            modMorphMin = nextMin,
                            modMorphMax = nextMax
                        ))
                    } else {
                        onReplace(existing.copy(
                            randomizeModMorph = false,
                            modMorphMin = existing.modMorph,
                            modMorphMax = existing.modMorph
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeModMorph())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    val nextActive = existing.modMorph.coerceIn(safeMin, safeMax)
                    onReplace(existing.copy(
                        modMorphMin = safeMin,
                        modMorphMax = safeMax,
                        modMorph = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    onReplace(existing.copy(
                        modMorph = newVal,
                        modMorphMin = newVal,
                        modMorphMax = newVal
                    ))
                }
            )
            ImGui.spacing()

            // LFO 2 Hold
            CustomRangeSlider.drawCustomRangeSlider(
                idPrefix = existing.id + "_mod_hold",
                label = "LFO 2 Hold",
                themeColor = themeColor,
                currentValue = existing.modHold,
                currentMin = existing.modHoldMin,
                currentMax = existing.modHoldMax,
                minLimit = 0f,
                maxLimit = 0.99f,
                isRandomizable = existing.randomizeModHold,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.modHoldMin
                        val rMax = existing.modHoldMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.modHold - 0.1f).coerceAtLeast(0f), (existing.modHold + 0.1f).coerceAtMost(0.99f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        onReplace(existing.copy(
                            randomizeModHold = true,
                            modHoldMin = nextMin,
                            modHoldMax = nextMax
                        ))
                    } else {
                        onReplace(existing.copy(
                            randomizeModHold = false,
                            modHoldMin = existing.modHold,
                            modHoldMax = existing.modHold
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeModHold())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    val nextActive = existing.modHold.coerceIn(safeMin, safeMax)
                    onReplace(existing.copy(
                        modHoldMin = safeMin,
                        modHoldMax = safeMax,
                        modHold = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    onReplace(existing.copy(
                        modHold = newVal,
                        modHoldMin = newVal,
                        modHoldMax = newVal
                    ))
                }
            )
            ImGui.spacing()

            // LFO 2 Slew (mod slope, if not random)
            if (existing.modWaveform != Waveform.RANDOM) {
                CustomRangeSlider.drawCustomRangeSlider(
                    idPrefix = existing.id + "_mod_slope",
                    label = "LFO 2 Slew",
                    themeColor = themeColor,
                    currentValue = existing.modSlope,
                    currentMin = existing.modSlopeMin,
                    currentMax = existing.modSlopeMax,
                    minLimit = 0.001f,
                    maxLimit = 0.999f,
                    isRandomizable = existing.randomizeModSlope,
                    formatValue = { "%.3f".format(it) },
                    onRandomizableChanged = { checked ->
                        if (checked) {
                            val rMin = existing.modSlopeMin
                            val rMax = existing.modSlopeMax
                            val (nextMin, nextMax) = if (rMin == rMax) {
                                Pair((existing.modSlope - 0.1f).coerceAtLeast(0.001f), (existing.modSlope + 0.1f).coerceAtMost(0.999f))
                            } else {
                                Pair(rMin, rMax)
                            }
                            onReplace(existing.copy(
                                randomizeModSlope = true,
                                modSlopeMin = nextMin,
                                modSlopeMax = nextMax
                            ))
                        } else {
                            onReplace(existing.copy(
                                randomizeModSlope = false,
                                modSlopeMin = existing.modSlope,
                                modSlopeMax = existing.modSlope
                            ))
                        }
                    },
                    onRandomizeNow = {
                        onReplace(existing.randomizeModSlope())
                    },
                    onRangeChanged = { nextMin, nextMax ->
                        val safeMin = minOf(nextMin, nextMax)
                        val safeMax = maxOf(nextMin, nextMax)
                        val nextActive = existing.modSlope.coerceIn(safeMin, safeMax)
                        onReplace(existing.copy(
                            modSlopeMin = safeMin,
                            modSlopeMax = safeMax,
                            modSlope = nextActive
                        ))
                    },
                    onValueChanged = { newVal ->
                        onReplace(existing.copy(
                            modSlope = newVal,
                            modSlopeMin = newVal,
                            modSlopeMax = newVal
                        ))
                    }
                )
                ImGui.spacing()
            }
        }
    }
}
