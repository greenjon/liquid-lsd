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
    private val virtualModulators = mutableListOf<CvModulator>()
    private var lastActiveIds: Set<String> = emptySet()
    private var draggingMin = false
    private var draggingMax = false
    private var activeSliderLabel: String? = null
    private var clickMouseX = 0f

    private fun initializeVirtualModulators(cvId: String, activeMods: List<CvModulator>, hasAdvanced: Boolean) {
        virtualModulators.clear()
        if (hasAdvanced) {
            val activeCount = activeMods.size
            if (activeCount == 0) {
                virtualModulators.add(CvModulator(sourceId = cvId, bypassed = true))
                virtualModulators.add(CvModulator(sourceId = cvId, bypassed = true))
            } else if (activeCount == 1) {
                virtualModulators.add(CvModulator(sourceId = cvId, bypassed = true))
            }
        } else {
            if (activeMods.isEmpty()) {
                virtualModulators.add(CvModulator(sourceId = cvId, bypassed = true))
            }
        }
    }

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

        val activeMods = if (cvId == "midi") {
            param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
        } else {
            param.modulators.filter { it.sourceId == cvId }
        }
        val isMidiMod = cvId == "midi"

        val isBeat = cvId == "beatPhase"
        val isLfo = cvId == "lfo"
        val isSnh = cvId == "sampleAndHold"
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
                val isBgStyle = paramKey.endsWith("/Background/Style")

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
                            isBgStyle -> {
                                when (it.roundToInt()) {
                                    0 -> "Off"
                                    1 -> "Solid Color"
                                    2 -> "Plasma"
                                    else -> "Off"
                                }
                            }
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
            val isBgStyle = paramKey.endsWith("/Background/Style")
            if (isHueSweep && mandala != null) {
                val petals = mandala.recipe.petals
                val options = mandala.getSymmetricHueCycles(petals)
                val idx = if (options.size > 1) (param.baseValue * (options.size - 1)).roundToInt().coerceIn(0, options.size - 1) else 0
                UITheme.caption("Static Base Value: ${options[idx]} cycles")
            } else if (isBgStyle) {
                val label = when (param.baseValue.roundToInt()) {
                    0 -> "Off"
                    1 -> "Solid Color"
                    2 -> "Plasma"
                    else -> "Off"
                }
                UITheme.caption("Static Base Value: $label")
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

            // Live value text readout
            val isBgStyle = paramKey.endsWith("/Background/Style")
            val isHueSweep = paramKey.endsWith("/HueSweep") || paramKey.endsWith("/Color/HueSweep")
            val isLobes = paramKey.endsWith("/Geometry/Lobes")
            val liveVal = param.value
            val liveLabel = when {
                isHueSweep && mandala != null -> {
                    val petals = mandala.recipe.petals
                    val options = mandala.getSymmetricHueCycles(petals)
                    val idx = if (options.size > 1) (liveVal * (options.size - 1)).roundToInt().coerceIn(0, options.size - 1) else 0
                    "${options[idx]} cycles"
                }
                isBgStyle -> {
                    when (liveVal.roundToInt()) {
                        0 -> "Off"
                        1 -> "Solid Color"
                        2 -> "Plasma"
                        else -> "Off"
                    }
                }
                isLobes -> "${liveVal.roundToInt()} lobes"
                else -> "%.3f".format(liveVal)
            }
            UITheme.h3("Live Modulated Value: $liveLabel")
            ImGui.spacing()

            // Oscilloscope showing final value history
            drawFinalOscilloscope(param.history, param.minClamp, param.maxClamp)

            // Cyan progress bar under the oscilloscope showing the value in the clamp range
            ImGui.spacing()
            val range = param.maxClamp - param.minClamp
            val pct = if (range == 0f) 0.5f else ((param.value - param.minClamp) / range).coerceIn(0f, 1f)
            val barW = ImGui.getContentRegionAvailX()
            val dl = ImGui.getWindowDrawList()
            val cx = ImGui.getCursorScreenPosX()
            val cy = ImGui.getCursorScreenPosY()
            dl.addRectFilled(cx, cy, cx + barW, cy + 10f, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
            dl.addRectFilled(cx, cy, cx + barW * pct, cy + 10f,
                ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 1f))
            ImGui.dummy(barW, 10f)

            return
        }

        UITheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey)
        ImGui.sameLine()
        if (isMidiMod) {
            val firstMidiMod = activeMods.firstOrNull { it.sourceId.startsWith("midi_cc_") }
            val label = if (firstMidiMod != null) {
                val midiId = firstMidiMod.sourceId
                val parts = midiId.substring("midi_cc_".length).split('_')
                if (parts.size >= 2) {
                    val ch = parts[0].toIntOrNull() ?: 0
                    val cc = parts[1].toIntOrNull() ?: 0
                    if (ch == 0) "MIDI CC $cc" else "MIDI Ch ${ch + 1} CC $cc"
                } else "MIDI"
            } else {
                "Unmapped MIDI (Click MIDI Map to bind)"
            }
            UITheme.caption("  <--  $cvId ($label)")
        } else {
            UITheme.caption("  <--  $cvId")
        }
        ImGui.separator()
        ImGui.spacing()

        val isVirtual = activeMods.isEmpty()
        if (isVirtual && cvId == "midi") {
            activeHistory = null
            activeCellId = null
            UITheme.caption("No MIDI mapping on this parameter.")
            ImGui.spacing()
            UITheme.caption("To map a controller:")
            UITheme.caption("1. Enable [MIDI Map] in the main menu bar.")
            UITheme.caption("2. Click this cell (which will highlight in cyan).")
            UITheme.caption("3. Turn a knob or move a fader on your MIDI controller.")
            return
        }

        // Initialize or update oscilloscope history and virtual modulators
        val currentActiveIds = activeMods.map { it.id }.toSet()
        if (activeCellId != cell || activeHistory == null || currentActiveIds != lastActiveIds) {
            activeHistory = CvHistoryBuffer(200)
            activeCellId = cell
            lastActiveIds = currentActiveIds
            initializeVirtualModulators(cvId, activeMods, hasAdvanced)
        }

        val modsToDraw = activeMods + virtualModulators.filter { vm -> activeMods.none { am -> am.id == vm.id } }
        val combinedVal = llm.slop.spirals.cv.getCombinedModulatorValue(activeMods)
        activeHistory?.add(combinedVal)

        // ── Delete ALL ───────────────────────────────────────────
        if (isVirtual) {
            ImGui.beginDisabled()
        }
        ImGui.pushStyleColor(0, 0.8f, 0.2f, 0.2f, 1f)
        if (ImGui.button("Delete Patch", ImGui.getContentRegionAvailX(), 30f)) {
            val toRemove = activeMods.toList()
            for (mod in toRemove) {
                param.modulators.remove(mod)
            }
            ImGui.popStyleColor()
            return
        }
        ImGui.popStyleColor()
        if (isVirtual) {
            ImGui.endDisabled()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ── Unified Oscilloscope ─────────────────────────────────
        drawOscilloscope(param)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ── Modulators ───────────────────────────────────────────
        for ((idx, existing) in modsToDraw.withIndex()) {
            ImGui.pushID(existing.id)
            if (modsToDraw.size > 1) {
                val typeLabel = if (hasAdvanced) "Oscillator" else "Modulator"
                UITheme.h3("$typeLabel ${idx + 1}")
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

            ImGui.sameLine(0f, 10f)
            if (ImGui.button("Randomize", 125f, 30f)) {
                val randomized = existing.randomizeActiveValues()
                replaceModulator(state, param, randomized)
            }

            ImGui.spacing()

        // ── Waveform and Operator ──────────────────────────
        val showWaveform = hasAdvanced && !isSnh
        if (showWaveform) {
            ImGui.beginGroup()
            UITheme.body("Waveform")
            val wfIdx = ImInt(existing.waveform.ordinal)
            ImGui.pushItemWidth(120f)
            if (ImGui.combo("##waveform", wfIdx, waveformLabels)) {
                replaceModulator(state, param, existing.copy(waveform = Waveform.entries[wfIdx.get()]))
            }
            ImGui.popItemWidth()
            ImGui.endGroup()
            
            ImGui.sameLine(0f, 20f)
        }

        ImGui.beginGroup()
        UITheme.body("Operator")
        val opIdx = ImInt(when (existing.operator) {
            ModulationOperator.ADD -> 0
            ModulationOperator.MUL -> 1
            ModulationOperator.SCALE -> 2
        })
        ImGui.pushItemWidth(120f)
        if (ImGui.combo("##op", opIdx, operatorLabels)) {
            val newOp = when (opIdx.get()) {
                0 -> ModulationOperator.ADD
                1 -> ModulationOperator.MUL
                else -> ModulationOperator.SCALE
            }
            replaceModulator(state, param, existing.copy(operator = newOp))
        }
        ImGui.popItemWidth()
        ImGui.endGroup()
        ImGui.spacing()

        // ── Amplitude ─────────────────────────────────────────────
        drawCustomRangeSlider(
            idPrefix = existing.id,
            label = "Amplitude",
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
                    replaceModulator(state, param, existing.copy(
                        randomizeAmplitude = true,
                        amplitudeMin = nextMin,
                        amplitudeMax = nextMax
                    ))
                } else {
                    replaceModulator(state, param, existing.copy(
                        randomizeAmplitude = false,
                        amplitudeMin = existing.amplitude,
                        amplitudeMax = existing.amplitude
                    ))
                }
            },
            onRandomizeNow = {
                replaceModulator(state, param, existing.randomizeAmplitude())
            },
            onRangeChanged = { nextMin, nextMax ->
                val nextActive = existing.amplitude.coerceIn(nextMin, nextMax)
                replaceModulator(state, param, existing.copy(
                    amplitudeMin = nextMin,
                    amplitudeMax = nextMax,
                    amplitude = nextActive
                ))
            },
            onValueChanged = { newVal ->
                replaceModulator(state, param, existing.copy(
                    amplitude = newVal,
                    amplitudeMin = newVal,
                    amplitudeMax = newVal
                ))
            }
        )
        ImGui.spacing()

        // ── DC Offset ─────────────────────────────────────────────
        drawCustomRangeSlider(
            idPrefix = existing.id,
            label = "DC Offset",
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
                    replaceModulator(state, param, existing.copy(
                        randomizeDcOffset = true,
                        dcOffsetMin = nextMin,
                        dcOffsetMax = nextMax
                    ))
                } else {
                    replaceModulator(state, param, existing.copy(
                        randomizeDcOffset = false,
                        dcOffsetMin = existing.dcOffset,
                        dcOffsetMax = existing.dcOffset
                    ))
                }
            },
            onRandomizeNow = {
                replaceModulator(state, param, existing.randomizeDcOffset())
            },
            onRangeChanged = { nextMin, nextMax ->
                val nextActive = existing.dcOffset.coerceIn(nextMin, nextMax)
                replaceModulator(state, param, existing.copy(
                    dcOffsetMin = nextMin,
                    dcOffsetMax = nextMax,
                    dcOffset = nextActive
                ))
            },
            onValueChanged = { newVal ->
                replaceModulator(state, param, existing.copy(
                    dcOffset = newVal,
                    dcOffsetMin = newVal,
                    dcOffsetMax = newVal
                ))
            }
        )
        ImGui.spacing()

        if (!hasAdvanced) {
            ImGui.popID()
            if (idx < modsToDraw.size - 1) {
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }
            continue
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

            ImGui.popID()
            if (idx < modsToDraw.size - 1) {
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }
        }

    }

    private fun drawFinalOscilloscope(history: CvHistoryBuffer, minVal: Float, maxVal: Float) {
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
        
        val range = maxVal - minVal
        val divisor = if (range == 0f) 1f else range
        
        for (i in 0 until historySize - 1) {
            val raw1 = history.getAt(i)
            val raw2 = history.getAt(i + 1)
            
            val val1 = if (range == 0f) 0.5f else ((raw1 - minVal) / divisor).coerceIn(0f, 1f)
            val val2 = if (range == 0f) 0.5f else ((raw2 - minVal) / divisor).coerceIn(0f, 1f)
            
            val x1 = startX + i * stepX
            val y1 = (startY + h - 5f) - val1 * usableHeight
            val x2 = startX + (i + 1) * stepX
            val y2 = (startY + h - 5f) - val2 * usableHeight
            
            dl.addLine(x1, y1, x2, y2, lineCol, 2.0f)
        }
        
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)
        
        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.2f".format(maxVal))
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.2f".format(minVal + range * 0.5f))
        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "%.2f".format(minVal))
        
        val textWidth = ImGui.calcTextSize("Final Parameter Value").x
        ImGui.setCursorScreenPos(startX + w - textWidth - 8f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "Final Parameter Value")
        
        ImGui.setCursorScreenPos(startX, startY + h)
    }

    private fun replaceModulator(state: PatchGridState, param: llm.slop.spirals.parameters.ModulatableParameter, newMod: CvModulator) {
        val idx = param.modulators.indexOfFirst { it.id == newMod.id }
        val existing = if (idx >= 0) param.modulators[idx] else virtualModulators.firstOrNull { it.id == newMod.id }
        val wasBypassed = existing?.bypassed ?: false

        // Auto-activate: if amplitude is adjusted to a non-zero value, activate/unbypass the modulator
        val finalizedMod = if (wasBypassed && newMod.bypassed && newMod.amplitude != 0.0f) {
            newMod.copy(bypassed = false)
        } else {
            newMod
        }

        if (idx >= 0) {
            param.modulators[idx] = finalizedMod
        } else {
            param.modulators.add(finalizedMod)
        }
    }

    private fun drawOscilloscope(param: llm.slop.spirals.parameters.ModulatableParameter) {
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
        
        // Horizontal lines (Center, Max, Min)
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
            val raw1 = history.getAt(i).coerceIn(-1f, 1f)
            val raw2 = history.getAt(i + 1).coerceIn(-1f, 1f)
            
            // Map the native -1..1 CV to the parameter's visual range based on MeterType
            val norm1 = if (param.meterType == llm.slop.spirals.parameters.MeterType.BIPOLAR) {
                (raw1 + 1f) / 2f // Map -1..1 to 0..1 visually
            } else {
                raw1.coerceAtLeast(0f) // Map 0..1 to 0..1 visually (clipping negatives)
            }
            
            val norm2 = if (param.meterType == llm.slop.spirals.parameters.MeterType.BIPOLAR) {
                (raw2 + 1f) / 2f
            } else {
                raw2.coerceAtLeast(0f)
            }
            
            val x1 = startX + i * stepX
            val y1 = (startY + h - 5f) - norm1 * usableHeight
            val x2 = startX + (i + 1) * stepX
            val y2 = (startY + h - 5f) - norm2 * usableHeight
            
            dl.addLine(x1, y1, x2, y2, lineCol, 2.0f)
        }
        
        // 4. Border
        val borderCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.18f, 0.18f, 1.0f)
        dl.addRect(startX, startY, startX + w, startY + h, borderCol, 4f)
        
        // 5. Y-Axis label markings (showing scaled bounds instead of hardcoded -1..1)
        val maxLabel = "%.2f".format(param.maxClamp)
        val midLabel = "%.2f".format(param.minClamp + (param.maxClamp - param.minClamp) / 2f)
        val minLabel = "%.2f".format(param.minClamp)
        
        ImGui.setCursorScreenPos(startX + 6f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, maxLabel)
        
        ImGui.setCursorScreenPos(startX + 6f, centerY - 6f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, midLabel)
        
        ImGui.setCursorScreenPos(startX + 6f, startY + h - 16f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, minLabel)
        
        val textWidth = ImGui.calcTextSize("Raw Modulator CV").x
        ImGui.setCursorScreenPos(startX + w - textWidth - 8f, startY + 4f)
        UITheme.captionColored(0.5f, 0.5f, 0.5f, 0.6f, "Raw Modulator CV")
        
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
            val buttonSize = ImGui.getFrameHeight()
            val screenPosX = ImGui.getCursorScreenPosX()
            val screenPosY = ImGui.getCursorScreenPosY()
            if (ImGui.button("##rand", buttonSize, buttonSize)) {
                onRandomizeNow()
            }
            val hovered = ImGui.isItemHovered()
            if (hovered) {
                ImGui.setTooltip("Randomize $label now")
            }
            if (!isRandomizable) {
                ImGui.endDisabled()
            }

            // Draw circle arrow icon on top of the button
            val dl = ImGui.getWindowDrawList()
            val centerX = screenPosX + buttonSize / 2f
            val centerY = screenPosY + buttonSize / 2f
            val radius = buttonSize * 0.22f
            val thickness = 1.8f

            val iconColor = if (!isRandomizable) {
                ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.5f)
            } else if (ImGui.isItemActive()) {
                ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f)
            } else if (hovered) {
                ImGui.colorConvertFloat4ToU32(0.95f, 0.95f, 0.95f, 1.0f)
            } else {
                ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f)
            }

            val numSegments = 16
            val startAngle = -kotlin.math.PI.toFloat() * 0.5f
            val sweepAngle = kotlin.math.PI.toFloat() * 1.55f

            var prevX = centerX + radius * kotlin.math.cos(startAngle)
            var prevY = centerY + radius * kotlin.math.sin(startAngle)
            for (i in 1..numSegments) {
                val angle = startAngle + (sweepAngle * i / numSegments)
                val nextX = centerX + radius * kotlin.math.cos(angle)
                val nextY = centerY + radius * kotlin.math.sin(angle)
                dl.addLine(prevX, prevY, nextX, nextY, iconColor, thickness)
                prevX = nextX
                prevY = nextY
            }

            val tipX = centerX
            val tipY = centerY - radius
            val arrowSize = buttonSize * 0.12f
            dl.addLine(tipX, tipY, tipX - arrowSize, tipY - arrowSize * 0.7f, iconColor, thickness)
            dl.addLine(tipX, tipY, tipX - arrowSize * 0.7f, tipY + arrowSize, iconColor, thickness)
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
