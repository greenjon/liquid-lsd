package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiColorEditFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.cv.CvHistoryBuffer
import llm.slop.spirals.cv.evaluateModulator
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.LfoSpeedMode
import llm.slop.spirals.parameters.ModulationOperator
import llm.slop.spirals.parameters.Waveform
import llm.slop.spirals.parameters.GenUnit
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.MandalaLibrary
import llm.slop.spirals.utils.TimeUtils
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
    private val modulatorHistories = mutableMapOf<String, CvHistoryBuffer>()
    private var activeCellId: PatchCellId? = null
    private val virtualModulators = mutableListOf<CvModulator>()
    private var lastActiveIds: Set<String> = emptySet()
    private var draggingMin = false
    private var draggingMax = false
    private var activeSliderLabel: String? = null
    private var clickMouseX = 0f

    private val textBuffers = mutableMapOf<String, imgui.type.ImString>()
    private val textWidgetActive = mutableMapOf<String, Boolean>()

    private fun getThemeColor(cvId: String, alpha: Float = 1f): Int {
        return when (cvId) {
            "final"          -> ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, alpha)
            "base"           -> ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, alpha)
            "midi"           -> ImGui.colorConvertFloat4ToU32(0.3f, 1.0f, 0.4f, alpha)
            "gen1"           -> ImGui.colorConvertFloat4ToU32(0.1f, 0.7f, 0.9f, alpha)
            "gen2"           -> ImGui.colorConvertFloat4ToU32(0.1f, 0.8f, 0.7f, alpha)
            "lfo"            -> ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, alpha)
            "sampleAndHold"  -> ImGui.colorConvertFloat4ToU32(0.7f, 0.4f, 1.0f, alpha)
            "beatPhase"      -> ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 1.0f, alpha)
            "audio"          -> ImGui.colorConvertFloat4ToU32(0.4f, 0.9f, 0.1f, alpha)
            "amp", "audio_amp" -> ImGui.colorConvertFloat4ToU32(0.7f, 0.9f, 0.1f, alpha)
            "bass", "audio_bass" -> ImGui.colorConvertFloat4ToU32(0.9f, 0.2f, 0.2f, alpha)
            "mid", "audio_mid" -> ImGui.colorConvertFloat4ToU32(0.9f, 0.5f, 0.1f, alpha)
            "high", "audio_high" -> ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.2f, alpha)
            "trigger"        -> ImGui.colorConvertFloat4ToU32(0.9f, 0.2f, 0.7f, alpha)
            "onset", "trigger_onset" -> ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.6f, alpha)
            "accent", "trigger_accent" -> ImGui.colorConvertFloat4ToU32(0.9f, 0.1f, 0.9f, alpha)
            else             -> ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, alpha)
        }
    }

    private fun getThemeColorRGB(cvId: String): FloatArray {
        return when (cvId) {
            "final"          -> floatArrayOf(0.4f, 1.0f, 0.8f)
            "base"           -> floatArrayOf(0.8f, 0.6f, 0.2f)
            "midi"           -> floatArrayOf(0.3f, 1.0f, 0.4f)
            "gen1"           -> floatArrayOf(0.1f, 0.7f, 0.9f)
            "gen2"           -> floatArrayOf(0.1f, 0.8f, 0.7f)
            "lfo"            -> floatArrayOf(0.2f, 0.8f, 1.0f)
            "sampleAndHold"  -> floatArrayOf(0.7f, 0.4f, 1.0f)
            "beatPhase"      -> floatArrayOf(0.4f, 0.4f, 1.0f)
            "audio"          -> floatArrayOf(0.4f, 0.9f, 0.1f)
            "amp", "audio_amp" -> floatArrayOf(0.7f, 0.9f, 0.1f)
            "bass", "audio_bass" -> floatArrayOf(0.9f, 0.2f, 0.2f)
            "mid", "audio_mid" -> floatArrayOf(0.9f, 0.5f, 0.1f)
            "high", "audio_high" -> floatArrayOf(0.9f, 0.9f, 0.2f)
            "trigger"        -> floatArrayOf(0.9f, 0.2f, 0.7f)
            "onset", "trigger_onset" -> floatArrayOf(1.0f, 0.3f, 0.6f)
            "accent", "trigger_accent" -> floatArrayOf(0.9f, 0.1f, 0.9f)
            else             -> floatArrayOf(0.5f, 0.5f, 0.5f)
        }
    }

    private fun initializeVirtualModulators(cvId: String, activeMods: List<CvModulator>, hasAdvanced: Boolean) {
        virtualModulators.clear()
        if (cvId == "audio") {
            val bands = listOf("audio_amp", "audio_bass", "audio_mid", "audio_high")
            for (band in bands) {
                val exists = activeMods.any { it.sourceId == band }
                if (!exists) {
                    virtualModulators.add(CvModulator(sourceId = band, bypassed = true))
                }
            }
        } else if (cvId == "trigger") {
            val bands = listOf("trigger_onset", "trigger_accent")
            for (band in bands) {
                val exists = activeMods.any { it.sourceId == band }
                if (!exists) {
                    virtualModulators.add(CvModulator(sourceId = band, bypassed = true))
                }
            }
        } else if (hasAdvanced) {
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

        val themeRGB = getThemeColorRGB(cvId)
        val themeColor = getThemeColor(cvId)

        val deck = when {
            paramKey.startsWith("Deck A/") -> mixer.deckA
            paramKey.startsWith("Deck B/") -> mixer.deckB
            else -> null
        }
        val mandala = deck?.source as? Mandala

        val activeMods = if (cvId == "midi") {
            param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
        } else if (cvId == "audio") {
            param.modulators.filter { it.sourceId in setOf("audio_amp", "audio_bass", "audio_mid", "audio_high") }
        } else if (cvId == "trigger") {
            param.modulators.filter { it.sourceId in setOf("trigger_onset", "trigger_accent") }
        } else {
            param.modulators.filter { it.sourceId == cvId }
        }
        val isMidiMod = cvId == "midi"

        val isBeat = cvId == "beatPhase"
        val isLfo = cvId == "lfo"
        val isSnh = cvId == "sampleAndHold"
        val isGen = cvId == "gen1" || cvId == "gen2"
        val isAudio = cvId == "audio"
        val isTrigger = cvId == "trigger"
        val hasAdvanced = isBeat || isLfo || isSnh || isGen

        if (cvId == "final") {
            UITheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey)
            ImGui.sameLine()
            UITheme.caption("  <--  FINAL & INITIAL VALUE")
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

            // Update active modulators history
            val activeMods = param.modulators.filter { 
                !it.bypassed && (CVRegistry.exists(it.sourceId) || it.sourceId.startsWith("midi_cc_"))
            }
            val activeIds = activeMods.map { it.id }.toSet()
            modulatorHistories.keys.retainAll(activeIds)

            for (mod in activeMods) {
                val hist = modulatorHistories.getOrPut(mod.id) { CvHistoryBuffer(200) }
                val cvVal = evaluateModulator(mod)
                val modAmount = cvVal * mod.amplitude + mod.dcOffset
                val modulatorVal = when (mod.operator) {
                    ModulationOperator.ADD -> param.baseValue + modAmount
                    ModulationOperator.MUL -> param.baseValue * (1.0f + modAmount)
                    ModulationOperator.SCALE -> param.baseValue * (1.0f - mod.amplitude + modAmount)
                }.coerceIn(param.minClamp, param.maxClamp)
                hist.add(modulatorVal)
            }

            // Oscilloscope showing final value history plus modulator histories
            drawFinalOscilloscope(param.history, param.minClamp, param.maxClamp, themeColor, activeMods, modulatorHistories)

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // --- INITIAL VALUE CONTROLS ---
            UITheme.h3("Initial Value Configuration")
            ImGui.spacing()

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
                val isRecipeSelect = paramKey.endsWith("/Geometry/Recipe")

                drawCustomRangeSlider(
                    label = "Initial Range",
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
            if (ImGui.button("🎲 Randomize Initial Value", ImGui.getContentRegionAvailX(), 30f)) {
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
                UITheme.caption("Static Initial Value: ${options[idx]} cycles")
            } else if (isBgStyle) {
                val label = when (param.baseValue.roundToInt()) {
                    0 -> "Off"
                    1 -> "Solid Color"
                    2 -> "Plasma"
                    else -> "Off"
                }
                UITheme.caption("Static Initial Value: $label")
            } else {
                UITheme.caption("Static Initial Value: %.3f".format(param.baseValue))
            }
            val baseBarW = ImGui.getContentRegionAvailX()
            val baseDl = ImGui.getWindowDrawList()
            val cx = ImGui.getCursorScreenPosX()
            val cy = ImGui.getCursorScreenPosY()
            baseDl.addRectFilled(cx, cy, cx + baseBarW, cy + 10f, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
            baseDl.addRectFilled(cx, cy, cx + baseBarW * param.baseValue, cy + 10f, getThemeColor("base"))
            ImGui.dummy(baseBarW, 10f)

            return
        }

        UITheme.h2Colored(themeRGB[0], themeRGB[1], themeRGB[2], 1.0f, paramKey)
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
            UITheme.captionColored(themeRGB[0], themeRGB[1], themeRGB[2], 1.0f, "  <--  $cvId ($label)")
        } else {
            val label = when (cvId) {
                "gen1" -> "Generator 1"
                "gen2" -> "Generator 2"
                "lfo" -> "LFO Modulator"
                "sampleAndHold" -> "Sample & Hold Modulator"
                "beatPhase" -> "Beat Phase Modulator"
                "audio" -> "Audio Envelope / Spectral Bands"
                "amp" -> "Amplitude Envelope"
                "bass" -> "Bass Envelope"
                "mid" -> "Mid Envelope"
                "high" -> "High Envelope"
                "trigger" -> "Trigger Triggers"
                "onset" -> "Transient Onset"
                "accent" -> "Transient Accent"
                else -> cvId
            }
            UITheme.captionColored(themeRGB[0], themeRGB[1], themeRGB[2], 1.0f, "  <--  $cvId ($label)")
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
        drawOscilloscope(param, themeColor)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // ── Modulators ───────────────────────────────────────────
        for ((idx, existing) in modsToDraw.withIndex()) {
            ImGui.pushID(existing.id)
            
            val bypassed = existing.bypassed
            val currentThemeColor = getThemeColor(existing.sourceId)
            val currentThemeRGB = getThemeColorRGB(existing.sourceId)
            
            // Draw background panel for modulator
            val panelStartX = ImGui.getCursorScreenPosX()
            val panelStartY = ImGui.getCursorScreenPosY()
            val dl = ImGui.getWindowDrawList()
            
            if (bypassed) {
                ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)
            }
            
            val bandLabel = when (existing.sourceId) {
                "audio_amp" -> "Envelope / Amp"
                "audio_bass" -> "Low / Bass"
                "audio_mid" -> "Mid"
                "audio_high" -> "High"
                "trigger_onset" -> "Onset / Transient"
                "trigger_accent" -> "Accent / Peak"
                else -> null
            }
            if (bandLabel != null) {
                UITheme.h3(bandLabel)
                ImGui.spacing()
            } else if (modsToDraw.size > 1) {
                val typeLabel = if (hasAdvanced) "Oscillator" else "Modulator"
                UITheme.h3("$typeLabel ${idx + 1}")
                ImGui.spacing()
            }

            ImGui.indent(10f) // Indent controls slightly

            val bypassLabel = if (bypassed) "BYPASSED" else "ACTIVE"
            if (bypassed) ImGui.pushStyleColor(0, 0.5f, 0.5f, 0.5f, 1f)
            else ImGui.pushStyleColor(0, currentThemeRGB[0], currentThemeRGB[1], currentThemeRGB[2], 0.8f) // use theme color for active button
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

        // ── Waveform, Unit and Operator ──────────────────────────
        val showWaveform = hasAdvanced && (!isSnh || isGen)
        if (showWaveform) {
            ImGui.beginGroup()
            UITheme.body("Waveform")
            
            val currentLabels = if (isGen) {
                arrayOf("Sine", "Triangle", "Square", "Random")
            } else {
                waveformLabels
            }
            val wfIdx = ImInt(existing.waveform.ordinal)
            ImGui.pushItemWidth(125f)
            if (ImGui.combo("##waveform", wfIdx, currentLabels)) {
                replaceModulator(state, param, existing.copy(waveform = Waveform.entries[wfIdx.get()]))
            }
            ImGui.popItemWidth()
            ImGui.endGroup()
            
            if (isGen) {
                ImGui.sameLine(0f, 10f)
                ImGui.beginGroup()
                UITheme.body("Unit")
                val unitIdx = ImInt(existing.genUnit.ordinal)
                val unitLabels = arrayOf("Time", "Beat")
                ImGui.pushItemWidth(125f)
                if (ImGui.combo("##unit", unitIdx, unitLabels)) {
                    replaceModulator(state, param, existing.copy(genUnit = GenUnit.entries[unitIdx.get()]))
                }
                ImGui.popItemWidth()
                ImGui.endGroup()
            }
            
            ImGui.sameLine(0f, 10f)
        }

        ImGui.beginGroup()
        UITheme.body("Operator")
        val opIdx = ImInt(when (existing.operator) {
            ModulationOperator.ADD -> 0
            ModulationOperator.MUL -> 1
            ModulationOperator.SCALE -> 2
        })
        ImGui.pushItemWidth(125f)
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
            themeColor = currentThemeColor,
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
            themeColor = currentThemeColor,
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
            ImGui.unindent(10f)
            if (bypassed) {
                ImGui.popStyleVar()
            }
            ImGui.popID()
            if (idx < modsToDraw.size - 1) {
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }
            continue
        }



        // ── Subdivision (Beat / S&H) ─────────────────────────────
        if (isBeat || isSnh || (isGen && existing.genUnit == GenUnit.BEAT)) {
            val currentMinIdx = subdivisionOptions.indexOfFirst { it == existing.subdivisionMin }.coerceAtLeast(0)
            val currentMaxIdx = subdivisionOptions.indexOfFirst { it == existing.subdivisionMax }.coerceAtLeast(0)
            val currentActiveIdx = subdivisionOptions.indexOfFirst { it == existing.subdivision }.coerceAtLeast(0)
            
            drawBeatDivisionSlider(
                idPrefix = existing.id,
                label = "Beat Div",
                themeColor = currentThemeColor,
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
        if (isLfo || (isGen && existing.genUnit == GenUnit.TIME)) {
            val formatFunc: (Float) -> String = { v -> TimeUtils.formatPeriod(v) }
            val parseFunc: (String) -> Float? = { s -> TimeUtils.parsePeriod(s) }

            drawCustomRangeSlider(
                idPrefix = existing.id,
                label = "LFO Period",
                themeColor = currentThemeColor,
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
                    val roundedMin = if (nextMin >= 3600f) nextMin.toInt().toFloat() else nextMin
                    val roundedMax = if (nextMax >= 3600f) nextMax.toInt().toFloat() else nextMax
                    val nextActive = existing.subdivision.coerceIn(roundedMin, roundedMax)
                    val roundedActive = if (nextActive >= 3600f) nextActive.toInt().toFloat() else nextActive
                    replaceModulator(state, param, existing.copy(
                        subdivisionMin = roundedMin,
                        subdivisionMax = roundedMax,
                        subdivision = roundedActive
                    ))
                },
                onValueChanged = { newVal ->
                    val roundedVal = if (newVal >= 3600f) newVal.toInt().toFloat() else newVal
                    replaceModulator(state, param, existing.copy(
                        subdivision = roundedVal,
                        subdivisionMin = roundedVal,
                        subdivisionMax = roundedVal
                    ))
                }
            )
            ImGui.spacing()
        }

        // ── Phase Offset ─────────────────────────────────────────
        drawCustomRangeSlider(
            idPrefix = existing.id,
            label = "Phase Offset",
            themeColor = currentThemeColor,
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

        // ── Slope / Duty (Triangle, Square, S&H / Random) ────────────────
        val needsSlope = isSnh ||
                existing.waveform == Waveform.TRIANGLE ||
                existing.waveform == Waveform.SQUARE ||
                existing.waveform == Waveform.RANDOM
        if (needsSlope) {
            val slopeLabel = when {
                isSnh || existing.waveform == Waveform.RANDOM -> "Glide"
                existing.waveform == Waveform.TRIANGLE -> "Slope"
                else -> "Duty Cycle"
            }
            drawCustomRangeSlider(
            idPrefix = existing.id,
                label = slopeLabel,
                themeColor = currentThemeColor,
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

            ImGui.unindent(10f) // Unindent at the end of block
            
            if (bypassed) {
                ImGui.popStyleVar()
            }
            
            val panelEndY = ImGui.getCursorScreenPosY()
            
            // Draw margin line for active modulators
            if (!bypassed) {
                dl.addLine(panelStartX + 2f, panelStartY, panelStartX + 2f, panelEndY - 10f, currentThemeColor, 4f)
            }

            ImGui.popID()
            if (idx < modsToDraw.size - 1) {
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }
        }

    }

    private fun drawFinalOscilloscope(
        history: CvHistoryBuffer,
        minVal: Float,
        maxVal: Float,
        themeColor: Int,
        modulators: List<CvModulator>,
        modulatorHistories: Map<String, CvHistoryBuffer>
    ) {
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
        
        val range = maxVal - minVal
        val divisor = if (range == 0f) 1f else range
        
        // 1. Draw each active modulator's history line
        for (mod in modulators) {
            val hist = modulatorHistories[mod.id] ?: continue
            val colorId = if (mod.sourceId.startsWith("midi_cc_")) "midi" else mod.sourceId
            val modColor = getThemeColor(colorId, 0.6f) // Thinner and transparent background line
            
            for (i in 0 until historySize - 1) {
                val raw1 = hist.getAt(i)
                val raw2 = hist.getAt(i + 1)
                
                val val1 = if (range == 0f) 0.5f else ((raw1 - minVal) / divisor).coerceIn(0f, 1f)
                val val2 = if (range == 0f) 0.5f else ((raw2 - minVal) / divisor).coerceIn(0f, 1f)
                
                val x1 = startX + i * stepX
                val y1 = (startY + h - 5f) - val1 * usableHeight
                val x2 = startX + (i + 1) * stepX
                val y2 = (startY + h - 5f) - val2 * usableHeight
                
                dl.addLine(x1, y1, x2, y2, modColor, 1.25f)
            }
        }
        
        // 2. Draw final modulated line on top
        val lineCol = themeColor
        for (i in 0 until historySize - 1) {
            val raw1 = history.getAt(i)
            val raw2 = history.getAt(i + 1)
            
            val val1 = if (range == 0f) 0.5f else ((raw1 - minVal) / divisor).coerceIn(0f, 1f)
            val val2 = if (range == 0f) 0.5f else ((raw2 - minVal) / divisor).coerceIn(0f, 1f)
            
            val x1 = startX + i * stepX
            val y1 = (startY + h - 5f) - val1 * usableHeight
            val x2 = startX + (i + 1) * stepX
            val y2 = (startY + h - 5f) - val2 * usableHeight
            
            dl.addLine(x1, y1, x2, y2, lineCol, 2.25f)
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

    private fun drawOscilloscope(param: llm.slop.spirals.parameters.ModulatableParameter, themeColor: Int) {
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
        
        val lineCol = themeColor
        
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

    private fun drawTextInput(
        key: String,
        currentValue: Float,
        minLimit: Float,
        maxLimit: Float,
        posX: Float,
        posY: Float,
        width: Float,
        onChanged: (Float) -> Unit,
        formatValue: (Float) -> String = { "%.3f".format(it) },
        parseValue: (String) -> Float? = { it.toFloatOrNull() }
    ) {
        val buffer = textBuffers.getOrPut(key) { imgui.type.ImString(formatValue(currentValue), 32) }
        val active = textWidgetActive.getOrDefault(key, false)
        if (!active) {
            buffer.set(formatValue(currentValue))
        }
        ImGui.setCursorScreenPos(posX, posY)
        ImGui.pushItemWidth(width)
        if (ImGui.inputText("##input_$key", buffer)) {
            val parsed = parseValue(buffer.get())
            if (parsed != null) {
                onChanged(parsed.coerceIn(minLimit, maxLimit))
            }
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            val fieldType = when {
                key.endsWith("_min") -> "Minimum modulation boundary. Type to set directly."
                key.endsWith("_max") -> "Maximum modulation boundary. Type to set directly."
                key.endsWith("_value") -> "Base value. Type to set directly."
                else -> "Type a precise numeric value."
            }
            ImGui.setTooltip(fieldType)
        }
        textWidgetActive[key] = ImGui.isItemActive()
        ImGui.popItemWidth()
    }

    private fun drawCustomRangeSlider(
        label: String,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        formatValue: (Float) -> String,
        onRangeChanged: (Float, Float) -> Unit,
        idPrefix: String = "",
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f),
        isLogarithmic: Boolean = false,
        parseValue: (String) -> Float? = { it.toFloatOrNull() }
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
            idPrefix = idPrefix,
            themeColor = themeColor,
            isLogarithmic = isLogarithmic,
            parseValue = parseValue
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
        idPrefix: String = "",
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f),
        isLogarithmic: Boolean = false,
        parseValue: (String) -> Float? = { it.toFloatOrNull() }
    ) {
        val rowStartX = ImGui.getCursorScreenPosX()
        val rowStartY = ImGui.getCursorScreenPosY()

        ImGui.pushID(label)

        val w = ImGui.getContentRegionAvailX()
        val h = 44f
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        
        // Reserve space
        ImGui.dummy(w, h)
        
        val dl = ImGui.getWindowDrawList()
        val io = ImGui.getIO()
        val mouseX = io.mousePos.x
        val mouseY = io.mousePos.y
        
        val buttonSize = ImGui.getFrameHeight()
        val spacing = ImGui.getStyle().itemSpacing.x
        val combinedWidth = buttonSize * 2f + spacing
        
        val labelColW = 125f
        val textBoxesStartX = startX + labelColW + 20f
        
        val boxWidth = 115f
        val boxSpacing = 8f
        
        val sliderStartX = textBoxesStartX + (if (isRandomizable) (boxWidth * 2f + boxSpacing) else boxWidth) + 15f
        val lineStartX = sliderStartX
        val lineEndX = startX + w - 10f
        val lineWidth = lineEndX - lineStartX
        val centerY = startY + 28f
        
        val rangeSpan = maxLimit - minLimit

        val toPct: (Float) -> Float = { v ->
            if (isLogarithmic) {
                val logMin = java.lang.Math.log10(minLimit.toDouble())
                val logMax = java.lang.Math.log10(maxLimit.toDouble())
                val logVal = java.lang.Math.log10(v.toDouble().coerceAtLeast(minLimit.toDouble()))
                ((logVal - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)
            } else {
                if (rangeSpan > 0f) (v - minLimit) / rangeSpan else 0f
            }
        }

        val toVal: (Float) -> Float = { p ->
            if (isLogarithmic) {
                val logMin = java.lang.Math.log10(minLimit.toDouble())
                val logMax = java.lang.Math.log10(maxLimit.toDouble())
                val logVal = logMin + p.toDouble() * (logMax - logMin)
                java.lang.Math.pow(10.0, logVal).toFloat().coerceIn(minLimit, maxLimit)
            } else {
                minLimit + p * rangeSpan
            }
        }

        // ─── ROW 1: Labels ───
        ImGui.setCursorScreenPos(startX, startY + 2f)
        UITheme.body(label)
        
        if (isRandomizable) {
            ImGui.setCursorScreenPos(textBoxesStartX, startY + 2f)
            UITheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Min")
            
            ImGui.setCursorScreenPos(textBoxesStartX + boxWidth + boxSpacing, startY + 2f)
            UITheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Max")
            
            // Add "Current" label with [value] centered above the dynamic dot on Row 1
            val curPct = toPct(currentValue)
            val curX = lineStartX + curPct * lineWidth
            val formattedVal = formatValue(currentValue)
            val labelText = "Current: $formattedVal"
            val currentTextWidth = ImGui.calcTextSize(labelText).x
            val minAllowedX = lineStartX
            val maxAllowedX = lineEndX - currentTextWidth
            val textX = (curX - currentTextWidth / 2f).coerceIn(minAllowedX, maxAllowedX)
            
            ImGui.setCursorScreenPos(textX, startY + 2f)
            UITheme.captionColored(0.8f, 0.8f, 0.8f, 0.9f, labelText)
        } else {
            ImGui.setCursorScreenPos(textBoxesStartX, startY + 2f)
            UITheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Current")
        }
        
        // ─── ROW 2: Widgets ───
        val row2Y = startY + 18f
        
        if (showControls) {
            // 1. Checkbox
            ImGui.setCursorScreenPos(startX + labelColW - combinedWidth, row2Y)
            val checked = imgui.type.ImBoolean(isRandomizable)
            if (ImGui.checkbox("##check_$label", checked)) {
                onRandomizableChanged(checked.get())
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Enable parameter modulation limits and randomization")
            }
            
            // 2. Randomize Button
            val randBtnX = startX + labelColW - buttonSize
            ImGui.setCursorScreenPos(randBtnX, row2Y)
            if (!isRandomizable) {
                ImGui.beginDisabled()
            }
            if (ImGui.button("##rand_$label", buttonSize, buttonSize)) {
                onRandomizeNow()
            }
            val hovered = ImGui.isItemHovered()
            if (hovered && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Randomize $label now")
            }
            if (!isRandomizable) {
                ImGui.endDisabled()
            }
            
            // Circle arrow icon for randomize button
            val centerX = randBtnX + buttonSize / 2f
            val centerYBtn = row2Y + buttonSize / 2f
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
            var prevY = centerYBtn + radius * kotlin.math.sin(startAngle)
            for (i in 1..numSegments) {
                val angle = startAngle + (sweepAngle * i / numSegments)
                val nextX = centerX + radius * kotlin.math.cos(angle)
                val nextY = centerYBtn + radius * kotlin.math.sin(angle)
                dl.addLine(prevX, prevY, nextX, nextY, iconColor, thickness)
                prevX = nextX
                prevY = nextY
            }
            val tipX = centerX
            val tipY = centerYBtn - radius
            val arrowSize = buttonSize * 0.12f
            dl.addLine(tipX, tipY, tipX - arrowSize, tipY - arrowSize * 0.7f, iconColor, thickness)
            dl.addLine(tipX, tipY, tipX - arrowSize * 0.7f, tipY + arrowSize, iconColor, thickness)
        }
        
        // 3. Text inputs
        if (isRandomizable) {
            drawTextInput(
                key = "${idPrefix}_${label}_min",
                currentValue = currentMin,
                minLimit = minLimit,
                maxLimit = maxLimit,
                posX = textBoxesStartX,
                posY = row2Y,
                width = boxWidth,
                onChanged = { nextMin ->
                    onRangeChanged(nextMin, currentMax)
                },
                formatValue = formatValue,
                parseValue = parseValue
            )
            drawTextInput(
                key = "${idPrefix}_${label}_max",
                currentValue = currentMax,
                minLimit = minLimit,
                maxLimit = maxLimit,
                posX = textBoxesStartX + boxWidth + boxSpacing,
                posY = row2Y,
                width = boxWidth,
                onChanged = { nextMax ->
                    onRangeChanged(currentMin, nextMax)
                },
                formatValue = formatValue,
                parseValue = parseValue
            )
        } else {
            drawTextInput(
                key = "${idPrefix}_${label}_value",
                currentValue = currentValue,
                minLimit = minLimit,
                maxLimit = maxLimit,
                posX = textBoxesStartX,
                posY = row2Y,
                width = boxWidth,
                onChanged = { newVal ->
                    onValueChanged(newVal)
                },
                formatValue = formatValue,
                parseValue = parseValue
            )
        }
        
        // ─── Dragging & Slider Render ───
        val mousePressed = ImGui.isMouseClicked(0)
        val mouseDown = ImGui.isMouseDown(0)
        
        if (isRandomizable) {
            val minPct = toPct(currentMin)
            val maxPct = toPct(currentMax)
            val minHandleX = lineStartX + minPct * lineWidth
            val maxHandleX = lineStartX + maxPct * lineWidth
            
            if (mousePressed) {
                val inRowY = mouseY >= row2Y && mouseY <= row2Y + buttonSize
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
                val rawVal = toVal(pct)
                if (!draggingMin && !draggingMax) {
                    val dragThreshold = 2f
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
            
            // Draw tracks
            val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f) // Darker inactive track
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(minHandleX, centerY, maxHandleX, centerY, themeColor, 3f) // Active track is theme color
            
            // Draw handles
            val handleW = 6f
            val handleH = 16f
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            
            dl.addRectFilled(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
            dl.addRectFilled(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

            // Draw dynamic current value indicator (Amber Gold dot)
            val curPct = toPct(currentValue)
            val curX = lineStartX + curPct * lineWidth
            val dotY = centerY
            val dotR = 4f
            val curDotCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 1.0f) // Bright Amber Gold
            dl.addCircleFilled(curX, dotY, dotR, curDotCol)
            dl.addCircle(curX, dotY, dotR + 0.5f, ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f), 12, 1.0f) // Dark border
        } else {
            val valPct = toPct(currentValue)
            val valHandleX = lineStartX + valPct * lineWidth
            
            if (mousePressed) {
                val inRowY = mouseY >= row2Y && mouseY <= row2Y + buttonSize
                val inRowX = mouseX >= lineStartX - 10f && mouseX <= lineEndX + 10f
                if (inRowY && inRowX) {
                    activeSliderLabel = idPrefix + label
                    draggingMin = true
                    draggingMax = false
                }
            }
            
            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = toVal(pct)
                onValueChanged(rawVal)
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false
                draggingMax = false
                activeSliderLabel = null
            }
            
            // Draw tracks
            val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f) // Darker inactive track
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(lineStartX, centerY, valHandleX, centerY, themeColor, 3f) // Active track is theme color
            
            // Draw single handle
            val handleW = 6f
            val handleH = 16f
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            
            dl.addRectFilled(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
        }
        
        // Hover-zone tooltips for custom range slider track/handles
        if (UITheme.tooltipsEnabled) {
            val inTrackY = mouseY >= centerY - 8f && mouseY <= centerY + 8f
            val inTrackX = mouseX >= lineStartX - 4f && mouseX <= lineEndX + 4f
            if (inTrackY && inTrackX) {
                if (isRandomizable) {
                    val minPct = toPct(currentMin)
                    val maxPct = toPct(currentMax)
                    val minHandleX = lineStartX + minPct * lineWidth
                    val maxHandleX = lineStartX + maxPct * lineWidth
                    val curPct = toPct(currentValue)
                    val curX = lineStartX + curPct * lineWidth

                    val distToMin = kotlin.math.abs(mouseX - minHandleX)
                    val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                    val distToCur = kotlin.math.abs(mouseX - curX)

                    when {
                        distToMin < 8f -> ImGui.setTooltip("Minimum boundary for $label: ${formatValue(currentMin)}")
                        distToMax < 8f -> ImGui.setTooltip("Maximum boundary for $label: ${formatValue(currentMax)}")
                        distToCur < 6f -> ImGui.setTooltip("Current modulated value for $label: ${formatValue(currentValue)}")
                        else -> ImGui.setTooltip("Drag handles to set modulation bounds for $label")
                    }
                } else {
                    val valPct = toPct(currentValue)
                    val valHandleX = lineStartX + valPct * lineWidth
                    val distToVal = kotlin.math.abs(mouseX - valHandleX)

                    if (distToVal < 8f) {
                        ImGui.setTooltip("Base value for $label: ${formatValue(currentValue)}")
                    } else {
                        ImGui.setTooltip("Drag to adjust base value for $label")
                    }
                }
            }
        }

        ImGui.popID()
        ImGui.setCursorScreenPos(rowStartX, startY + h)
    }

    /**
     * Like [drawCustomRangeSlider] but the Min/Max/Current widgets are combo dropdowns
     * that display [subdivisionLabels] entries instead of numeric text boxes.
     * All values are *indices* into [subdivisionOptions]/[subdivisionLabels].
     */
    private fun drawBeatDivisionSlider(
        label: String,
        currentMin: Float,
        currentMax: Float,
        minLimit: Float,
        maxLimit: Float,
        formatValue: (Float) -> String,
        onRangeChanged: (Float, Float) -> Unit,
        idPrefix: String = "",
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f)
    ) {
        drawBeatDivisionSlider(
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
            idPrefix = idPrefix,
            themeColor = themeColor
        )
    }

    private fun drawBeatDivisionSlider(
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
        idPrefix: String = "",
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.8f, 0.6f)
    ) {
        val rowStartX = ImGui.getCursorScreenPosX()
        val rowStartY = ImGui.getCursorScreenPosY()

        ImGui.pushID(label)

        val w = ImGui.getContentRegionAvailX()
        val h = 44f
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        ImGui.dummy(w, h)

        val dl = ImGui.getWindowDrawList()
        val io = ImGui.getIO()
        val mouseX = io.mousePos.x
        val mouseY = io.mousePos.y

        val buttonSize = ImGui.getFrameHeight()
        val spacing = ImGui.getStyle().itemSpacing.x
        val combinedWidth = buttonSize * 2f + spacing

        val labelColW = 125f
        val textBoxesStartX = startX + labelColW + 20f

        // Combo dropdowns are slightly wider than plain text boxes so they can display labels
        val comboWidth = 125f
        val comboSpacing = 8f

        val sliderStartX = textBoxesStartX + (if (isRandomizable) (comboWidth * 2f + comboSpacing) else comboWidth) + 15f
        val lineStartX = sliderStartX
        val lineEndX = startX + w - 10f
        val lineWidth = lineEndX - lineStartX
        val centerY = startY + 28f

        val rangeSpan = maxLimit - minLimit

        // ─── ROW 1: Labels ───
        ImGui.setCursorScreenPos(startX, startY + 2f)
        UITheme.body(label)

        if (isRandomizable) {
            ImGui.setCursorScreenPos(textBoxesStartX, startY + 2f)
            UITheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Min")

            ImGui.setCursorScreenPos(textBoxesStartX + comboWidth + comboSpacing, startY + 2f)
            UITheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Max")

            val curPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
            val curX = lineStartX + curPct * lineWidth
            val formattedVal = formatValue(currentValue)
            val labelText = "Current: $formattedVal"
            val currentTextWidth = ImGui.calcTextSize(labelText).x
            val minAllowedX = lineStartX
            val maxAllowedX = lineEndX - currentTextWidth
            val textX = (curX - currentTextWidth / 2f).coerceIn(minAllowedX, maxAllowedX)

            ImGui.setCursorScreenPos(textX, startY + 2f)
            UITheme.captionColored(0.8f, 0.8f, 0.8f, 0.9f, labelText)
        } else {
            ImGui.setCursorScreenPos(textBoxesStartX, startY + 2f)
            UITheme.captionColored(0.6f, 0.6f, 0.6f, 0.7f, "Current")
        }

        // ─── ROW 2: Widgets ───
        val row2Y = startY + 18f

        if (showControls) {
            // 1. Checkbox
            ImGui.setCursorScreenPos(startX + labelColW - combinedWidth, row2Y)
            val checked = ImBoolean(isRandomizable)
            if (ImGui.checkbox("##check_$label", checked)) {
                onRandomizableChanged(checked.get())
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Enable parameter modulation limits and randomization")
            }

            // 2. Randomize Button
            val randBtnX = startX + labelColW - buttonSize
            ImGui.setCursorScreenPos(randBtnX, row2Y)
            if (!isRandomizable) ImGui.beginDisabled()
            if (ImGui.button("##rand_$label", buttonSize, buttonSize)) {
                onRandomizeNow()
            }
            val hovered = ImGui.isItemHovered()
            if (hovered && UITheme.tooltipsEnabled) ImGui.setTooltip("Randomize $label now")
            if (!isRandomizable) ImGui.endDisabled()

            // Circle-arrow icon
            val centerX = randBtnX + buttonSize / 2f
            val centerYBtn = row2Y + buttonSize / 2f
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
            var prevY = centerYBtn + radius * kotlin.math.sin(startAngle)
            for (i in 1..numSegments) {
                val angle = startAngle + (sweepAngle * i / numSegments)
                val nextX = centerX + radius * kotlin.math.cos(angle)
                val nextY = centerYBtn + radius * kotlin.math.sin(angle)
                dl.addLine(prevX, prevY, nextX, nextY, iconColor, thickness)
                prevX = nextX
                prevY = nextY
            }
            val tipX = centerX
            val tipY = centerYBtn - radius
            val arrowSize = buttonSize * 0.12f
            dl.addLine(tipX, tipY, tipX - arrowSize, tipY - arrowSize * 0.7f, iconColor, thickness)
            dl.addLine(tipX, tipY, tipX - arrowSize * 0.7f, tipY + arrowSize, iconColor, thickness)
        }

        // ─── Combo dropdowns instead of text inputs ───
        if (isRandomizable) {
            // Min combo
            val minIdx = ImInt(currentMin.toInt().coerceIn(0, subdivisionLabels.size - 1))
            ImGui.setCursorScreenPos(textBoxesStartX, row2Y)
            ImGui.pushItemWidth(comboWidth)
            if (ImGui.combo("##bd_min_$label", minIdx, subdivisionLabels)) {
                onRangeChanged(minIdx.get().toFloat(), currentMax)
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Minimum modulation speed subdivision dropdown")
            }
            ImGui.popItemWidth()

            // Max combo
            val maxIdx = ImInt(currentMax.toInt().coerceIn(0, subdivisionLabels.size - 1))
            ImGui.setCursorScreenPos(textBoxesStartX + comboWidth + comboSpacing, row2Y)
            ImGui.pushItemWidth(comboWidth)
            if (ImGui.combo("##bd_max_$label", maxIdx, subdivisionLabels)) {
                onRangeChanged(currentMin, maxIdx.get().toFloat())
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Maximum modulation speed subdivision dropdown")
            }
            ImGui.popItemWidth()
        } else {
            val valIdx = ImInt(currentValue.toInt().coerceIn(0, subdivisionLabels.size - 1))
            ImGui.setCursorScreenPos(textBoxesStartX, row2Y)
            ImGui.pushItemWidth(comboWidth)
            if (ImGui.combo("##bd_val_$label", valIdx, subdivisionLabels)) {
                onValueChanged(valIdx.get().toFloat())
            }
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Base speed subdivision dropdown")
            }
            ImGui.popItemWidth()
        }

        // ─── Dragging & Slider Render ───
        val mousePressed = ImGui.isMouseClicked(0)
        val mouseDown = ImGui.isMouseDown(0)

        if (isRandomizable) {
            val minPct = if (rangeSpan > 0f) (currentMin - minLimit) / rangeSpan else 0f
            val maxPct = if (rangeSpan > 0f) (currentMax - minLimit) / rangeSpan else 0f
            val minHandleX = lineStartX + minPct * lineWidth
            val maxHandleX = lineStartX + maxPct * lineWidth

            if (mousePressed) {
                val inRowY = mouseY >= row2Y && mouseY <= row2Y + buttonSize
                val inRowX = mouseX >= lineStartX - 10f && mouseX <= lineEndX + 10f
                if (inRowY && inRowX) {
                    activeSliderLabel = idPrefix + label
                    val isOverlapping = kotlin.math.abs(minHandleX - maxHandleX) < 4f
                    if (isOverlapping) {
                        if (mouseX < minHandleX - 5f) {
                            draggingMin = true; draggingMax = false
                        } else if (mouseX > maxHandleX + 5f) {
                            draggingMax = true; draggingMin = false
                        } else {
                            draggingMin = false; draggingMax = false
                            clickMouseX = mouseX
                        }
                    } else {
                        val distToMin = kotlin.math.abs(mouseX - minHandleX)
                        val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                        if (distToMin < distToMax) {
                            draggingMin = true; draggingMax = false
                        } else {
                            draggingMax = true; draggingMin = false
                        }
                    }
                }
            }

            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = (minLimit + pct * rangeSpan).let { v ->
                    // Snap to nearest integer index
                    v.roundToInt().coerceIn(minLimit.toInt(), maxLimit.toInt()).toFloat()
                }
                if (!draggingMin && !draggingMax) {
                    val dragThreshold = 2f
                    if (mouseX > clickMouseX + dragThreshold) {
                        draggingMax = true
                        onRangeChanged(currentMin, rawVal.coerceIn(currentMin, maxLimit))
                    } else if (mouseX < clickMouseX - dragThreshold) {
                        draggingMin = true
                        onRangeChanged(rawVal.coerceIn(minLimit, currentMax), currentMax)
                    }
                } else if (draggingMin) {
                    onRangeChanged(rawVal.coerceIn(minLimit, currentMax), currentMax)
                } else if (draggingMax) {
                    onRangeChanged(currentMin, rawVal.coerceIn(currentMin, maxLimit))
                }
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false; draggingMax = false; activeSliderLabel = null
            }

            // Track
            val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f)
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(minHandleX, centerY, maxHandleX, centerY, themeColor, 3f)

            val handleW = 6f; val handleH = 16f
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            dl.addRectFilled(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(minHandleX - handleW / 2f, centerY - handleH / 2f, minHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
            dl.addRectFilled(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(maxHandleX - handleW / 2f, centerY - handleH / 2f, maxHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)

            // Current value dot
            val curPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
            val curX = lineStartX + curPct * lineWidth
            val curDotCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 1.0f)
            dl.addCircleFilled(curX, centerY, 4f, curDotCol)
            dl.addCircle(curX, centerY, 4.5f, ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f), 12, 1.0f)
        } else {
            val valPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
            val valHandleX = lineStartX + valPct * lineWidth

            if (mousePressed) {
                val inRowY = mouseY >= row2Y && mouseY <= row2Y + buttonSize
                val inRowX = mouseX >= lineStartX - 10f && mouseX <= lineEndX + 10f
                if (inRowY && inRowX) {
                    activeSliderLabel = idPrefix + label
                    draggingMin = true; draggingMax = false
                }
            }

            if (mouseDown && activeSliderLabel == (idPrefix + label)) {
                val pct = ((mouseX - lineStartX) / lineWidth).coerceIn(0f, 1f)
                val rawVal = (minLimit + pct * rangeSpan).roundToInt()
                    .coerceIn(minLimit.toInt(), maxLimit.toInt()).toFloat()
                onValueChanged(rawVal)
            } else if (!mouseDown && activeSliderLabel == (idPrefix + label)) {
                draggingMin = false; draggingMax = false; activeSliderLabel = null
            }

            val lineCol = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f)
            dl.addLine(lineStartX, centerY, lineEndX, centerY, lineCol, 3f)
            dl.addLine(lineStartX, centerY, valHandleX, centerY, themeColor, 3f)

            val handleW = 6f; val handleH = 16f
            val handleBgCol = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1.0f)
            val handleBorderCol = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1.0f)
            dl.addRectFilled(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBgCol, 1f)
            dl.addRect(valHandleX - handleW / 2f, centerY - handleH / 2f, valHandleX + handleW / 2f, centerY + handleH / 2f, handleBorderCol, 1f)
        }

        // Hover-zone tooltips for beat division slider track/handles
        if (UITheme.tooltipsEnabled) {
            val inTrackY = mouseY >= centerY - 8f && mouseY <= centerY + 8f
            val inTrackX = mouseX >= lineStartX - 4f && mouseX <= lineEndX + 4f
            if (inTrackY && inTrackX) {
                if (isRandomizable) {
                    val minPct = if (rangeSpan > 0f) (currentMin - minLimit) / rangeSpan else 0f
                    val maxPct = if (rangeSpan > 0f) (currentMax - minLimit) / rangeSpan else 0f
                    val minHandleX = lineStartX + minPct * lineWidth
                    val maxHandleX = lineStartX + maxPct * lineWidth
                    val curPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
                    val curX = lineStartX + curPct * lineWidth

                    val distToMin = kotlin.math.abs(mouseX - minHandleX)
                    val distToMax = kotlin.math.abs(mouseX - maxHandleX)
                    val distToCur = kotlin.math.abs(mouseX - curX)

                    when {
                        distToMin < 8f -> ImGui.setTooltip("Minimum boundary speed for $label: ${formatValue(currentMin)}")
                        distToMax < 8f -> ImGui.setTooltip("Maximum boundary speed for $label: ${formatValue(currentMax)}")
                        distToCur < 6f -> ImGui.setTooltip("Current modulated speed for $label: ${formatValue(currentValue)}")
                        else -> ImGui.setTooltip("Drag handles to set modulation bounds for $label")
                    }
                } else {
                    val valPct = if (rangeSpan > 0f) (currentValue - minLimit) / rangeSpan else 0f
                    val valHandleX = lineStartX + valPct * lineWidth
                    val distToVal = kotlin.math.abs(mouseX - valHandleX)

                    if (distToVal < 8f) {
                        ImGui.setTooltip("Base speed for $label: ${formatValue(currentValue)}")
                    } else {
                        ImGui.setTooltip("Drag to adjust base speed for $label")
                    }
                }
            }
        }

        ImGui.popID()
        ImGui.setCursorScreenPos(rowStartX, startY + h)
    }
}
