package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiColorEditFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.cv.CvHistoryBuffer
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.LfoSpeedMode
import llm.slop.spirals.parameters.ModulationOperator
import llm.slop.spirals.parameters.Waveform
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.MandalaLibrary
import kotlin.math.roundToInt

/**
 * Draws the Cell Config panel contents.
 * Call this inside an ImGui.begin("Cell Config") / ImGui.end() block.
 */
object CellConfigPanel {

    private val subdivisionOptions = floatArrayOf(
        0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f
    )
    private val subdivisionLabels = arrayOf(
        "1/8", "1/4", "1/2", "1", "2", "4", "8", "16", "32", "64", "128", "256"
    )
    private val waveformLabels = arrayOf("Sine", "Triangle", "Square")
    private val speedLabels = arrayOf("Slow", "Medium", "Fast")
    private val operatorLabels = arrayOf("ADD", "MUL", "SCALE")

    private var activeHistory: CvHistoryBuffer? = null
    private var activeCellId: PatchCellId? = null
    private var draggingMin = false
    private var draggingMax = false
    private var activeSliderLabel: String? = null
    private var clickMouseX = 0f

    fun draw(state: PatchGridState, mixer: Mixer) {
        val cell = state.selectedCell
        val param = state.selectedParam

        if (cell == null || param == null) {
            activeHistory = null
            activeCellId = null
            UITheme.caption("Click a cell in the Patch Grid to configure it.")
            return
        }

        val cvId = cell.cvSourceId
        val paramKey = cell.paramKey

        val deck = when {
            paramKey.startsWith("Deck A/") -> mixer.deckA
            paramKey.startsWith("Deck B/") -> mixer.deckB
            else -> null
        }
        val mandala = deck?.source as? Mandala

        val activeMods = param.modulators.filter {
            it.sourceId == cvId || (it.sourceId.startsWith("midi_cc_") && it.sourceId.endsWith("_$cvId"))
        }
        val isMidiMod = activeMods.any { it.sourceId.startsWith("midi_cc_") }

        val isBeat = cvId == "beatPhase" && !isMidiMod
        val isLfo = cvId == "lfo" && !isMidiMod
        val isSnh = cvId == "sampleAndHold" && !isMidiMod
        val hasAdvanced = isBeat || isLfo || isSnh

        if (cvId == "base") {
            UITheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey)
            ImGui.sameLine()
            UITheme.caption("  <--  BASE RANGE")
            ImGui.separator()
            ImGui.spacing()

            val isHueSweep = paramKey.endsWith("/HueSweep") || paramKey.endsWith("/Color/HueSweep")

            if (isHueSweep && mandala != null) {
                val petals = mandala.recipe.petals
                val options = mandala.getSymmetricHueCycles(petals)
                val currentVal = param.baseValue
                val currentIndex = if (options.size > 1) {
                    (currentVal * (options.size - 1)).roundToInt().coerceIn(0, options.size - 1)
                } else {
                    0
                }

                UITheme.caption("Symmetric Cycles (Symmetry-preserving factor/multiple of $petals petals):")

                val labels = options.map { "$it cycles" }.toTypedArray()
                val selectedOpt = ImInt(currentIndex)
                ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 10f)
                if (ImGui.combo("##hue_symmetry_combo", selectedOpt, labels)) {
                    val nextIdx = selectedOpt.get()
                    val newVal = if (options.size > 1) nextIdx.toFloat() / (options.size - 1).toFloat() else 0.0f
                    param.set(newVal)
                }
                ImGui.popItemWidth()

                ImGui.spacing()
                UITheme.caption("Choose the number of color repetitions along the curve.")
                UITheme.caption("Because it is a factor or multiple of $petals, symmetry is preserved!")

                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()

                drawCustomRangeSlider(
                    idPrefix = "hue_sweep_base",
                    label = "Symmetry Random Range",
                    currentValue = param.baseValue,
                    currentMin = param.baseMin,
                    currentMax = param.baseMax,
                    minLimit = 0f,
                    maxLimit = 1f,
                    isRandomizable = param.randomizeBase,
                    showControls = false,
                    formatValue = {
                        val idx = if (options.size > 1) (it * (options.size - 1)).roundToInt().coerceIn(0, options.size - 1) else 0
                        "${options[idx]} cycles"
                    },
                    onRandomizableChanged = { checked ->
                        if (checked) {
                            val rMin = param.baseMin
                            val rMax = param.baseMax
                            val (nextMin, nextMax) = if (rMin == rMax) {
                                Pair((param.baseValue - 0.1f).coerceAtLeast(0f), (param.baseValue + 0.1f).coerceAtMost(1f))
                            } else {
                                Pair(rMin, rMax)
                            }
                            param.randomizeBase = true
                            param.baseMin = nextMin
                            param.baseMax = nextMax
                        } else {
                            param.randomizeBase = false
                            param.baseMin = param.baseValue
                            param.baseMax = param.baseValue
                        }
                    },
                    onRandomizeNow = {
                        param.randomizeBaseValue()
                    },
                    onRangeChanged = { nextMin, nextMax ->
                        param.baseMin = nextMin
                        param.baseMax = nextMax
                        param.baseValue = param.baseValue.coerceIn(nextMin, nextMax)
                    },
                    onValueChanged = { newVal ->
                        param.baseValue = newVal
                        param.baseMin = newVal
                        param.baseMax = newVal
                    }
                )
            } else {
                val isLobes = paramKey.endsWith("/Geometry/Lobes")
                val isRecipeSelect = paramKey.endsWith("/Geometry/Recipe")

                drawCustomRangeSlider(
                    label = "Base Range",
                    currentValue = param.baseValue,
                    currentMin = param.baseMin,
                    currentMax = param.baseMax,
                    minLimit = param.minClamp,
                    maxLimit = param.maxClamp,
                    isRandomizable = param.randomizeBase,
                    showControls = true,
                    formatValue = {
                        when {
                            isLobes -> "${it.roundToInt()} lobes"
                            isRecipeSelect -> {
                                if (mandala != null) {
                                    val currentLobe = mandala.parameters["Lobes"]?.value?.roundToInt() ?: mandala.recipe.petals
                                    val closestLobe = MandalaLibrary.uniquePetals.minByOrNull { kotlin.math.abs(it - currentLobe) } ?: 3
                                    val filtered = MandalaLibrary.recipesByPetals[closestLobe] ?: emptyList()
                                    if (filtered.isNotEmpty()) {
                                        val idx = (it * (filtered.size - 1)).roundToInt().coerceIn(0, filtered.size - 1)
                                        "[${filtered[idx].a}, ${filtered[idx].b}, ${filtered[idx].c}, ${filtered[idx].d}]"
                                    } else "No recipes"
                                } else "%.3f".format(it)
                            }
                            else -> "%.3f".format(it)
                        }
                    },
                    onRandomizableChanged = { checked ->
                        if (checked) {
                            val rMin = param.baseMin
                            val rMax = param.baseMax
                            val rangeSpan = param.maxClamp - param.minClamp
                            val offset = rangeSpan * 0.1f
                            val (nextMin, nextMax) = if (rMin == rMax) {
                                Pair((param.baseValue - offset).coerceAtLeast(param.minClamp), (param.baseValue + offset).coerceAtMost(param.maxClamp))
                            } else {
                                Pair(rMin, rMax)
                            }
                            param.randomizeBase = true
                            param.baseMin = nextMin
                            param.baseMax = nextMax
                        } else {
                            param.randomizeBase = false
                            param.baseMin = param.baseValue
                            param.baseMax = param.baseValue
                        }
                    },
                    onRandomizeNow = {
                        param.randomizeBaseValue()
                    },
                    onRangeChanged = { nextMin, nextMax ->
                        param.baseMin = nextMin
                        param.baseMax = nextMax
                        param.baseValue = param.baseValue.coerceIn(nextMin, nextMax)
                    },
                    onValueChanged = { newVal ->
                        param.baseValue = newVal
                        param.baseMin = newVal
                        param.baseMax = newVal
                    }
                )
            }

            ImGui.spacing()
            val randomizeBaseActive = param.randomizeBase
            if (!randomizeBaseActive) {
                ImGui.beginDisabled()
            }
            if (ImGui.button("🎲 Randomize Base Value", ImGui.getContentRegionAvailX(), 30f)) {
                param.randomizeBaseValue()
            }
            if (!randomizeBaseActive) {
                ImGui.endDisabled()
            }

            ImGui.spacing()
            if (isHueSweep && mandala != null) {
                val petals = mandala.recipe.petals
                val options = mandala.getSymmetricHueCycles(petals)
                val idx = if (options.size > 1) (param.baseValue * (options.size - 1)).roundToInt().coerceIn(0, options.size - 1) else 0
                UITheme.caption("Static Base Value: ${options[idx]} cycles")
            } else {
                UITheme.caption("Static Base Value: %.3f".format(param.baseValue))
            }
            val barW = ImGui.getContentRegionAvailX()
            val dl = ImGui.getWindowDrawList()
            val cx = ImGui.getCursorScreenPosX()
            val cy = ImGui.getCursorScreenPosY()
            dl.addRectFilled(cx, cy, cx + barW, cy + 10f, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
            dl.addRectFilled(cx, cy, cx + barW * param.baseValue, cy + 10f,
                ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f))
            ImGui.dummy(barW, 10f)
            return
        }

        if (cvId == "final") {
            UITheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey)
            ImGui.sameLine()
            UITheme.caption("  <--  FINAL VALUE")
            ImGui.separator()
            ImGui.spacing()

            // Oscilloscope showing final value history
            drawFinalOscilloscope(param.history)

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            val liveVal = param.value
            UITheme.caption("Live Value: %.3f".format(liveVal))
            val barW = ImGui.getContentRegionAvailX()
            val dl = ImGui.getWindowDrawList()
            val cx = ImGui.getCursorScreenPosX()
            val cy = ImGui.getCursorScreenPosY()
            dl.addRectFilled(cx, cy, cx + barW, cy + 10f, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
            dl.addRectFilled(cx, cy, cx + barW * liveVal.coerceIn(0f, 1f), cy + 10f,
                ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 1f))
            ImGui.dummy(barW, 10f)
            return
        }

        UITheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey)
        ImGui.sameLine()
        if (isMidiMod) {
            val firstMidiMod = activeMods.firstOrNull { it.sourceId.startsWith("midi_cc_") }
            val midiId = firstMidiMod?.sourceId ?: ""
            val parts = midiId.substring("midi_cc_".length).split('_')
            val label = if (parts.size >= 2) {
                val ch = parts[0].toIntOrNull() ?: 0
                val cc = parts[1].toIntOrNull() ?: 0
                if (ch == 0) "MIDI CC $cc" else "MIDI Ch ${ch + 1} CC $cc"
            } else "MIDI"
            UITheme.caption("  <--  $cvId ($label)")
        } else {
            UITheme.caption("  <--  $cvId")
        }
        ImGui.separator()
        ImGui.spacing()

        if (activeMods.isEmpty()) {
            activeHistory = null
            activeCellId = null
            // Empty cell: offer to create
            UITheme.caption("No patch at this intersection.")
            ImGui.spacing()
            if (ImGui.button("+ Add Patch", ImGui.getContentRegionAvailX(), 35f)) {
                if (cvId in listOf("beatPhase", "lfo", "sampleAndHold")) {
                    param.modulators.add(CvModulator(sourceId = cvId))
                    param.modulators.add(CvModulator(sourceId = cvId, bypassed = true))
                } else {
                    param.modulators.add(CvModulator(sourceId = cvId))
                }
            }
            return
        }

        // Initialize or update oscilloscope history
        if (activeCellId != cell || activeHistory == null) {
            activeHistory = CvHistoryBuffer(200)
            activeCellId = cell
        }
        val combinedVal = llm.slop.spirals.cv.getCombinedModulatorValue(activeMods)
        activeHistory?.add(combinedVal)

        // ── Delete ALL ───────────────────────────────────────────
        ImGui.pushStyleColor(0, 0.8f, 0.2f, 0.2f, 1f)
        if (ImGui.button("Delete Patch", ImGui.getContentRegionAvailX(), 30f)) {
            val toRemove = activeMods.toList()
            for (mod in toRemove) {
                param.modulators.remove(mod)
            }
            return
        }
        ImGui.popStyleColor()

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ── Unified Oscilloscope ─────────────────────────────────
        drawOscilloscope()

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ── Modulators ───────────────────────────────────────────
        for ((idx, existing) in activeMods.withIndex()) {
            ImGui.pushID(existing.id)
            if (activeMods.size > 1) {
                UITheme.h3("Modulator ${idx + 1}")
                ImGui.spacing()
            }

            val bypassed = existing.bypassed
            val bypassLabel = if (bypassed) "BYPASSED" else "ACTIVE"
            if (bypassed) ImGui.pushStyleColor(0, 0.5f, 0.5f, 0.5f, 1f)
            else ImGui.pushStyleColor(0, 0.2f, 0.8f, 0.4f, 1f)
            if (ImGui.button(bypassLabel, 125f, 30f)) {
                replaceModulator(state, param, existing.copy(bypassed = !bypassed))
            }
            ImGui.popStyleColor()

            ImGui.spacing()

            // ── Operator (ADD / MUL / SCALE) ──────────────────────────
        UITheme.body("Operator")
        val opIdx = ImInt(when (existing.operator) {
            ModulationOperator.ADD -> 0
            ModulationOperator.MUL -> 1
            ModulationOperator.SCALE -> 2
        })
        ImGui.pushItemWidth(150f)
        if (ImGui.combo("##op", opIdx, operatorLabels)) {
            val newOp = when (opIdx.get()) {
                0 -> ModulationOperator.ADD
                1 -> ModulationOperator.MUL
                else -> ModulationOperator.SCALE
            }
            replaceModulator(state, param, existing.copy(operator = newOp))
        }
        ImGui.popItemWidth()
        ImGui.spacing()

        // ── Weight ───────────────────────────────────────────────
        drawCustomRangeSlider(
            idPrefix = existing.id,
            label = "Weight",
            currentValue = existing.weight,
            currentMin = existing.weightMin,
            currentMax = existing.weightMax,
            minLimit = -1f,
            maxLimit = 1f,
            isRandomizable = existing.randomizeWeight,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.weightMin
                    val rMax = existing.weightMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.weight - 0.1f).coerceAtLeast(-1f), (existing.weight + 0.1f).coerceAtMost(1f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    replaceModulator(state, param, existing.copy(
                        randomizeWeight = true,
                        weightMin = nextMin,
                        weightMax = nextMax
                    ))
                } else {
                    replaceModulator(state, param, existing.copy(
                        randomizeWeight = false,
                        weightMin = existing.weight,
                        weightMax = existing.weight
                    ))
                }
            },
            onRandomizeNow = {
                replaceModulator(state, param, existing.randomizeWeight())
            },
            onRangeChanged = { nextMin, nextMax ->
                val nextActive = existing.weight.coerceIn(nextMin, nextMax)
                replaceModulator(state, param, existing.copy(
                    weightMin = nextMin,
                    weightMax = nextMax,
                    weight = nextActive
                ))
            },
            onValueChanged = { newVal ->
                replaceModulator(state, param, existing.copy(
                    weight = newVal,
                    weightMin = newVal,
                    weightMax = newVal
                ))
            }
        )
        ImGui.spacing()

        if (!hasAdvanced) {
            // ── Test Randomize Button ────────────────────────────────
            ImGui.spacing()
            if (ImGui.button("🎲 Test Randomize", ImGui.getContentRegionAvailX(), 30f)) {
                val randomized = existing.randomizeActiveValues()
                replaceModulator(state, param, randomized)
            }
            ImGui.popID()
            if (idx < activeMods.size - 1) {
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }
            continue
        }

        // ── Waveform (Beat / LFO only) ───────────────────────────
        if (!isSnh) {
            UITheme.body("Waveform")
            val wfIdx = ImInt(existing.waveform.ordinal)
            ImGui.pushItemWidth(150f)
            if (ImGui.combo("##waveform", wfIdx, waveformLabels)) {
                replaceModulator(state, param, existing.copy(waveform = Waveform.entries[wfIdx.get()]))
            }
            ImGui.popItemWidth()
            ImGui.spacing()
        }

        // ── Subdivision (Beat / S&H) ─────────────────────────────
        if (isBeat || isSnh) {
            val currentMinIdx = subdivisionOptions.indexOfFirst { it == existing.subdivisionMin }.coerceAtLeast(0)
            val currentMaxIdx = subdivisionOptions.indexOfFirst { it == existing.subdivisionMax }.coerceAtLeast(0)
            val currentActiveIdx = subdivisionOptions.indexOfFirst { it == existing.subdivision }.coerceAtLeast(0)
            
            drawCustomRangeSlider(
            idPrefix = existing.id,
                label = "Beat Division",
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
                        replaceModulator(state, param, existing.copy(
                            randomizeSubdivision = true,
                            subdivisionMin = nextMin,
                            subdivisionMax = nextMax
                        ))
                    } else {
                        replaceModulator(state, param, existing.copy(
                            randomizeSubdivision = false,
                            subdivisionMin = existing.subdivision,
                            subdivisionMax = existing.subdivision
                        ))
                    }
                },
                onRandomizeNow = {
                    replaceModulator(state, param, existing.randomizeSubdivision())
                },
                onRangeChanged = { nextMinIdx, nextMaxIdx ->
                    val nextMinVal = subdivisionOptions[nextMinIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                    val nextMaxVal = subdivisionOptions[nextMaxIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                    val nextActive = existing.subdivision.coerceIn(nextMinVal, nextMaxVal)
                    replaceModulator(state, param, existing.copy(
                        subdivisionMin = nextMinVal,
                        subdivisionMax = nextMaxVal,
                        subdivision = nextActive
                    ))
                },
                onValueChanged = { newValIdx ->
                    val newVal = subdivisionOptions[newValIdx.toInt().coerceIn(0, subdivisionOptions.size - 1)]
                    replaceModulator(state, param, existing.copy(
                        subdivision = newVal,
                        subdivisionMin = newVal,
                        subdivisionMax = newVal
                    ))
                }
            )
            ImGui.spacing()
        }

        // ── LFO Period / Speed ───────────────────────────────────
        if (isLfo) {
            UITheme.body("Speed Range")
            val speedIdx = ImInt(existing.lfoSpeedMode.ordinal)
            ImGui.pushItemWidth(125f)
            if (ImGui.combo("##speed", speedIdx, speedLabels)) {
                replaceModulator(state, param, existing.copy(lfoSpeedMode = LfoSpeedMode.entries[speedIdx.get()]))
            }
            ImGui.popItemWidth()
            ImGui.spacing()

            // Format labels based on speed mode
            val formatFunc: (Float) -> String = { v ->
                when (existing.lfoSpeedMode) {
                    LfoSpeedMode.FAST -> "%.2fs".format(v * 10.0)
                    LfoSpeedMode.MEDIUM -> {
                        val s = (v * 900).toInt()
                        "%02dm:%02ds".format(s / 60, s % 60)
                    }
                    LfoSpeedMode.SLOW -> {
                        val m = (v * 1440).toInt()
                        "%02dh:%02dm".format(m / 60, m % 60)
                    }
                }
            }

            drawCustomRangeSlider(
            idPrefix = existing.id,
                label = "LFO Period",
                currentValue = existing.subdivision,
                currentMin = existing.subdivisionMin,
                currentMax = existing.subdivisionMax,
                minLimit = 0.001f,
                maxLimit = 1f,
                isRandomizable = existing.randomizeSubdivision,
                formatValue = formatFunc,
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.subdivisionMin
                        val rMax = existing.subdivisionMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.subdivision - 0.05f).coerceAtLeast(0.001f), (existing.subdivision + 0.05f).coerceAtMost(1f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        replaceModulator(state, param, existing.copy(
                            randomizeSubdivision = true,
                            subdivisionMin = nextMin,
                            subdivisionMax = nextMax
                        ))
                    } else {
                        replaceModulator(state, param, existing.copy(
                            randomizeSubdivision = false,
                            subdivisionMin = existing.subdivision,
                            subdivisionMax = existing.subdivision
                        ))
                    }
                },
                onRandomizeNow = {
                    replaceModulator(state, param, existing.randomizeSubdivision())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val nextActive = existing.subdivision.coerceIn(nextMin, nextMax)
                    replaceModulator(state, param, existing.copy(
                        subdivisionMin = nextMin,
                        subdivisionMax = nextMax,
                        subdivision = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    replaceModulator(state, param, existing.copy(
                        subdivision = newVal,
                        subdivisionMin = newVal,
                        subdivisionMax = newVal
                    ))
                }
            )
            ImGui.spacing()
        }

        // ── Phase Offset ─────────────────────────────────────────
        drawCustomRangeSlider(
            idPrefix = existing.id,
            label = "Phase Offset",
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
                    replaceModulator(state, param, existing.copy(
                        randomizePhaseOffset = true,
                        phaseOffsetMin = nextMin,
                        phaseOffsetMax = nextMax
                    ))
                } else {
                    replaceModulator(state, param, existing.copy(
                        randomizePhaseOffset = false,
                        phaseOffsetMin = existing.phaseOffset,
                        phaseOffsetMax = existing.phaseOffset
                    ))
                }
            },
            onRandomizeNow = {
                replaceModulator(state, param, existing.randomizePhaseOffset())
            },
            onRangeChanged = { nextMin, nextMax ->
                val nextActive = existing.phaseOffset.coerceIn(nextMin, nextMax)
                replaceModulator(state, param, existing.copy(
                    phaseOffsetMin = nextMin,
                    phaseOffsetMax = nextMax,
                    phaseOffset = nextActive
                ))
            },
            onValueChanged = { newVal ->
                replaceModulator(state, param, existing.copy(
                    phaseOffset = newVal,
                    phaseOffsetMin = newVal,
                    phaseOffsetMax = newVal
                ))
            }
        )
        ImGui.spacing()

        // ── Slope / Duty (Triangle, Square, S&H) ────────────────
        val needsSlope = isSnh ||
                existing.waveform == Waveform.TRIANGLE ||
                existing.waveform == Waveform.SQUARE
        if (needsSlope) {
            val slopeLabel = when {
                isSnh -> "Glide"
                existing.waveform == Waveform.TRIANGLE -> "Slope"
                else -> "Duty Cycle"
            }
            drawCustomRangeSlider(
            idPrefix = existing.id,
                label = slopeLabel,
                currentValue = existing.slope,
                currentMin = existing.slopeMin,
                currentMax = existing.slopeMax,
                minLimit = 0f,
                maxLimit = 1f,
                isRandomizable = existing.randomizeSlope,
                formatValue = { "%.3f".format(it) },
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.slopeMin
                        val rMax = existing.slopeMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.slope - 0.1f).coerceAtLeast(0f), (existing.slope + 0.1f).coerceAtMost(1f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        replaceModulator(state, param, existing.copy(
                            randomizeSlope = true,
                            slopeMin = nextMin,
                            slopeMax = nextMax
                        ))
                    } else {
                        replaceModulator(state, param, existing.copy(
                            randomizeSlope = false,
                            slopeMin = existing.slope,
                            slopeMax = existing.slope
                        ))
                    }
                },
                onRandomizeNow = {
                    replaceModulator(state, param, existing.randomizeSlope())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val nextActive = existing.slope.coerceIn(nextMin, nextMax)
                    replaceModulator(state, param, existing.copy(
                        slopeMin = nextMin,
                        slopeMax = nextMax,
                        slope = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    replaceModulator(state, param, existing.copy(
                        slope = newVal,
                        slopeMin = newVal,
                        slopeMax = newVal
                    ))
                }
            )
            ImGui.spacing()
        }

        // ── Test Randomize Button ────────────────────────────────
        ImGui.spacing()
        if (ImGui.button("🎲 Test Randomize", ImGui.getContentRegionAvailX(), 30f)) {
            val randomized = existing.randomizeActiveValues()
            replaceModulator(state, param, randomized)
        }

            ImGui.popID()
            if (idx < activeMods.size - 1) {
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }
        }

        // ── Live value bar ───────────────────────────────────────
        ImGui.spacing()
        ImGui.separator()
        val liveVal = param.value
        UITheme.caption("Live Value: %.3f".format(liveVal))
        val barW = ImGui.getContentRegionAvailX()
        val dl = ImGui.getWindowDrawList()
        val cx = ImGui.getCursorScreenPosX()
        val cy = ImGui.getCursorScreenPosY()
        dl.addRectFilled(cx, cy, cx + barW, cy + 10f, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
        dl.addRectFilled(cx, cy, cx + barW * liveVal.coerceIn(0f, 1f), cy + 10f,
            ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 1f))
        ImGui.dummy(barW, 10f)
    }

    private fun drawFinalOscilloscope(history: CvHistoryBuffer) {
        val historySize = history.size
        val w = ImGui.getContentRegionAvailX()
        val h = 80f
        
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + h, bgCol, 4f)
        
        val centerY = startY + h / 2f
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 0.5f)
        
        dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        dl.addLine(startX, startY + 5f, startX + w, startY + 5f, gridColFaint, 1f)
        dl.addLine(startX, startY + h - 5f, startX + w, startY + h - 5f, gridColFaint, 1f)
        
        val numDivisions = 4
        for (i in 1 until numDivisions) {
            val gridX = startX + (w * i / numDivisions)
            dl.addLine(gridX, startY, gridX, startY + h, gridColFaint, 1f)
        }
        
        val stepX = w / (historySize - 1)
        val usableHeight = h - 10f
        val lineCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.9f, 1.0f)
        
        for (i in 0 until historySize - 1) {
            val val1 = history.getAt(i)
            val val2 = history.getAt(i + 1)
            
            val x1 = startX + i * stepX
            val y1 = (startY + h - 5f) - val1 * usableHeight
            val x2 = startX + (i + 1) * stepX
            val y2 = (startY + h - 5f) - val2 * usableHeight
            
            dl.addLine(x1, y1, x2, y2, lineCol, 2.0f)
        }
        
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)
        
        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "1.0")
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "0.5")
        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "0.0")
        
        val textWidth = ImGui.calcTextSize("Final Parameter Value").x
        ImGui.setCursorScreenPos(startX + w - textWidth - 8f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "Final Parameter Value")
        
        ImGui.setCursorScreenPos(startX, startY + h)
    }

    private fun replaceModulator(state: PatchGridState, param: llm.slop.spirals.parameters.ModulatableParameter, newMod: CvModulator) {
        val idx = param.modulators.indexOfFirst { it.id == newMod.id }
        if (idx >= 0) param.modulators[idx] = newMod
    }

    private fun drawOscilloscope() {
        val history = activeHistory ?: return
        val historySize = history.size
        val w = ImGui.getContentRegionAvailX()
        val h = 80f // Height of the oscilloscope box
        
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        // Reserve space
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        
        // 1. Background
        val bgCol = ImGui.colorConvertFloat4ToU32(0.04f, 0.04f, 0.04f, 1.0f)
        dl.addRectFilled(startX, startY, startX + w, startY + h, bgCol, 4f)
        
        // 2. Grid lines
        val centerY = startY + h / 2f
        val gridColCenter = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 0.8f)
        val gridColFaint = ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 0.5f)
        
        // Horizontal lines (Center, +1.0, -1.0)
        dl.addLine(startX, centerY, startX + w, centerY, gridColCenter, 1.5f)
        dl.addLine(startX, startY + 5f, startX + w, startY + 5f, gridColFaint, 1f)
        dl.addLine(startX, startY + h - 5f, startX + w, startY + h - 5f, gridColFaint, 1f)
        
        // Vertical lines
        val numDivisions = 4
        for (i in 1 until numDivisions) {
            val gridX = startX + (w * i / numDivisions)
            dl.addLine(gridX, startY, gridX, startY + h, gridColFaint, 1f)
        }
        
        // 3. Draw lines of history
        val stepX = w / (historySize - 1)
        val usableHeight = h - 10f
        
        val lineCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.9f, 1.0f) // neon cyan
        
        for (i in 0 until historySize - 1) {
            val val1 = history.getAt(i).coerceIn(-1f, 1f)
            val val2 = history.getAt(i + 1).coerceIn(-1f, 1f)
            
            val x1 = startX + i * stepX
            val y1 = centerY - val1 * (usableHeight / 2f)
            val x2 = startX + (i + 1) * stepX
            val y2 = centerY - val2 * (usableHeight / 2f)
            
            dl.addLine(x1, y1, x2, y2, lineCol, 2.0f)
        }
        
        // 4. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)
        
        // 5. Y-Axis label markings
        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "+1.0")
        
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "0.0")
        
        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "-1.0")
        
        // Right-aligned helper label
        val textWidth = ImGui.calcTextSize("Modulation Output").x
        ImGui.setCursorScreenPos(startX + w - textWidth - 8f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "Modulation Output")
        
        // Restore cursor position for downstream ImGui rendering
        ImGui.setCursorScreenPos(startX, startY + h)
    }

    private fun drawCustomRangeSlider(
        label: String,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        formatValue: (Float) -> String,
        onRangeChanged: (Float, Float) -> Unit,
        idPrefix: String = ""
    ) {
        drawCustomRangeSlider(
            label = label,
            currentValue = currentMin,
            currentMin = currentMin,
            currentMax = currentMax,
            minLimit = minLimit,
            maxLimit = maxLimit,
            isRandomizable = true,
            showControls = false,
            formatValue = formatValue,
            onRangeChanged = onRangeChanged,
            idPrefix = idPrefix
        )
    }

    private fun drawCustomRangeSlider(
        label: String,
        currentValue: Float,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        isRandomizable: Boolean,
        showControls: Boolean = true,
        formatValue: (Float) -> String,
        onRandomizableChanged: (Boolean) -> Unit = {},
        onRandomizeNow: () -> Unit = {},
        onRangeChanged: (Float, Float) -> Unit = { _, _ -> },
        onValueChanged: (Float) -> Unit = {},
        idPrefix: String = ""
    ) {
        val rowStartX = ImGui.getCursorScreenPosX()
        val rowStartY = ImGui.getCursorScreenPosY()

        ImGui.pushID(label)

        if (showControls) {
            val checked = ImBoolean(isRandomizable)
            if (ImGui.checkbox("##check", checked)) {
                onRandomizableChanged(checked.get())
            }
            ImGui.sameLine()

            if (!isRandomizable) {
                ImGui.beginDisabled()
            }
            if (ImGui.button("↻", 25f, 25f)) {
                onRandomizeNow()
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Randomize $label now")
            }
            if (!isRandomizable) {
                ImGui.endDisabled()
            }
            ImGui.sameLine()
        }

        val w = ImGui.getContentRegionAvailX()
        val h = 36f // height of the widget row
        
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        // Reserve space
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        val io = ImGui.getIO()
        val mouseX = io.mousePos.x
        val mouseY = io.mousePos.y
        
        val leftW = 120f
        val lineStartX = startX + leftW
        val lineEndX = startX + w - 10f
        val lineWidth = lineEndX - lineStartX
        val centerY = startY + h / 2f
        
        val rangeSpan = maxLimit - minLimit

        // Handle dragging logic and rendering based on mode
        val mousePressed = ImGui.isMouseClicked(0)
        val mouseDown = ImGui.isMouseDown(0)

        if (isRandomizable) {
            val minPct = if (rangeSpan > 0f) (currentMin - minLimit) / rangeSpan else 0f
            val maxPct = if (rangeSpan > 0f) (currentMax - minLimit) / rangeSpan else 0f
            
            val minHandleX = lineStartX + minPct * lineWidth
            val maxHandleX = lineStartX + maxPct * lineWidth

            if (mousePressed) {
                val inRowY = mouseY >= startY && mouseY <= startY + h
                val inRowX = mouseX >= lineStartX - 10f && mouseX <= lineEndX + 10f
                if (inRowY && inRowX) {
                    activeSliderLabel = idPrefix + label
                    val isOverlapping = kotlin.math.abs(minHandleX - maxHandleX) < 4f
                    if (isOverlapping) {
                        if (mouseX < minHandleX - 5f) {
                            draggingMin = true
                            draggingMax = false
                        } else if (mouseX > maxHandleX + 5f) {
                            draggingMax = true
                            draggingMin = false
                        } else {
                            draggingMin = false
                            draggingMax = false
                            clickMouseX = mouseX
                        }
                    } else {
                        val distToMin = kotlin.math.abs(mouseX - minHandleX)
                        val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                        if (distToMin < distToMax) {
                            draggingMin = true
                            draggingMax = false
                        } else {
                            draggingMax = true
                            draggingMin = false
                        }
                    }
                }
            }
            
            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = minLimit + pct * rangeSpan
                if (!draggingMin && !draggingMax) {
                    val dragThreshold = 2f // pixels
                    if (mouseX > clickMouseX + dragThreshold) {
                        draggingMax = true
                        val nextMax = rawVal.coerceIn(currentMin, maxLimit)
                        onRangeChanged(currentMin, nextMax)
                    } else if (mouseX < clickMouseX - dragThreshold) {
                        draggingMin = true
                        val nextMin = rawVal.coerceIn(minLimit, currentMax)
                        onRangeChanged(nextMin, currentMax)
                    }
                } else if (draggingMin) {
                    val nextMin = rawVal.coerceIn(minLimit, currentMax)
                    onRangeChanged(nextMin, currentMax)
                } else if (draggingMax) {
                    val nextMax = rawVal.coerceIn(currentMin, maxLimit)
                    onRangeChanged(currentMin, nextMax)
                }
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false
                draggingMax = false
                activeSliderLabel = null
            }

            // ── Render (Range mode) ──
            
            // 1. Text Labels (Left side)
            ImGui.setCursorScreenPos(startX, startY + 2f)
            UITheme.body(label)
            
            ImGui.setCursorScreenPos(startX + 8f, startY + 18f)
            val valueStr = "${formatValue(currentMin)} - ${formatValue(currentMax)}"
            UITheme.caption(valueStr)
            
            // 2. Thin horizontal line
            val lineCol = ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1.0f)
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 2f)
            
            // 3. Highlighted range line between handles
            val activeRangeCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f)
            dl.addLine(minHandleX, centerY, maxHandleX, centerY, activeRangeCol, 3f)
            
            // 4. Draw Handles (taller than wide rectangles)
            val handleW = 6f
            val handleH = 16f
            
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            
            // Min Handle
            dl.addRectFilled(
                minHandleX - handleW / 2f, centerY - handleH / 2f,
                minHandleX + handleW / 2f, centerY + handleH / 2f,
                handleBgCol, 1f
            )
            dl.addRect(
                minHandleX - handleW / 2f, centerY - handleH / 2f,
                minHandleX + handleW / 2f, centerY + handleH / 2f,
                handleBorderCol, 1f
            )
            
            // Max Handle
            dl.addRectFilled(
                maxHandleX - handleW / 2f, centerY - handleH / 2f,
                maxHandleX + handleW / 2f, centerY + handleH / 2f,
                handleBgCol, 1f
            )
            dl.addRect(
                maxHandleX - handleW / 2f, centerY - handleH / 2f,
                maxHandleX + handleW / 2f, centerY + handleH / 2f,
                handleBorderCol, 1f
            )
        } else {
            val valPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
            val valHandleX = lineStartX + valPct * lineWidth

            if (mousePressed) {
                val inRowY = mouseY >= startY && mouseY <= startY + h
                val inRowX = mouseX >= lineStartX - 10f && mouseX <= lineEndX + 10f
                if (inRowY && inRowX) {
                    activeSliderLabel = idPrefix + label
                    draggingMin = true
                    draggingMax = false
                }
            }
            
            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = minLimit + pct * rangeSpan
                onValueChanged(rawVal)
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false
                draggingMax = false
                activeSliderLabel = null
            }

            // ── Render (Static mode) ──
            
            // 1. Text Labels (Left side)
            ImGui.setCursorScreenPos(startX, startY + 2f)
            UITheme.body(label)
            
            ImGui.setCursorScreenPos(startX + 8f, startY + 18f)
            val valueStr = "${formatValue(currentValue)} (Static)"
            UITheme.caption(valueStr)
            
            // 2. Thin horizontal line (muted)
            val lineCol = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1.0f)
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 2f)
            
            // 3. Highlighted track up to handle (muted)
            val activeRangeCol = ImGui.colorConvertFloat4ToU32(0.4f, 0.45f, 0.5f, 0.5f)
            dl.addLine(lineStartX, centerY, valHandleX, centerY, activeRangeCol, 3f)
            
            // 4. Draw Single Handle (muted)
            val handleW = 6f
            val handleH = 16f
            
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            
            dl.addRectFilled(
                valHandleX - handleW / 2f, centerY - handleH / 2f,
                valHandleX + handleW / 2f, centerY + handleH / 2f,
                handleBgCol, 1f
            )
            dl.addRect(
                valHandleX - handleW / 2f, centerY - handleH / 2f,
                valHandleX + handleW / 2f, centerY + handleH / 2f,
                handleBorderCol, 1f
            )
        }

        ImGui.popID()

        // Reset Cursor Pos to prevent horizontal drift for downstream UI
        ImGui.setCursorScreenPos(rowStartX, rowStartY + h)
    }
}
