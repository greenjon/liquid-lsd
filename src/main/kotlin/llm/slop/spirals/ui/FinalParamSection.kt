package llm.slop.spirals.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.cv.CvHistoryBuffer
import llm.slop.spirals.cv.evaluateModulator
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.ModulationOperator
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.MandalaLibrary
import kotlin.math.roundToInt

object FinalParamSection {

    fun draw(
        state: PatchGridState,
        param: ModulatableParameter,
        paramKey: String,
        themeColor: Int,
        mandala: Mandala?,
        modulatorHistories: MutableMap<String, CvHistoryBuffer>
    ) {
        UITheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey.replace("/", " | "))
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
            val isBipolar = param.minClamp < 0f
            val rawModAmount = if (isBipolar) {
                cvVal * mod.amplitude + mod.dcOffset
            } else {
                ((cvVal + 1f) / 2f) * mod.amplitude + mod.dcOffset
            }
            val scalar = if (mod.operator == ModulationOperator.ADD) {
                if (isBipolar) (param.maxClamp - param.minClamp) / 2.0f else (param.maxClamp - param.minClamp)
            } else 1.0f
            val modAmount = rawModAmount * scalar
            val modulatorVal = when (mod.operator) {
                ModulationOperator.ADD -> param.baseValue + modAmount
                ModulationOperator.MUL -> param.baseValue * (1.0f + modAmount)
                ModulationOperator.SCALE -> param.baseValue * (1.0f - mod.amplitude + modAmount)
            }.coerceIn(param.minClamp, param.maxClamp)
            hist.add(modulatorVal)
        }

        // Oscilloscope showing final value history plus modulator histories
        OscilloscopeDrawer.drawFinalOscilloscope(param.history, param.minClamp, param.maxClamp, themeColor, activeMods, modulatorHistories)

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
            if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
                ImGui.setTooltip("Select symmetry-preserving cycle count. Keeps color distributions aligned with geometry lobes.")
            }
            ImGui.popItemWidth()

            ImGui.spacing()
            UITheme.caption("Choose the number of color repetitions along the curve.")
            UITheme.caption("Because it is a factor or multiple of $petals, symmetry is preserved!")

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            CustomRangeSlider.drawCustomRangeSlider(
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
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    param.baseMin = safeMin
                    param.baseMax = safeMax
                    param.baseValue = param.baseValue.coerceIn(safeMin, safeMax)
                },
                onValueChanged = { newVal ->
                    param.baseValue = newVal
                    param.baseMin = newVal
                    param.baseMax = newVal
                }
            )
        } else {
            val isRecipeSelect = paramKey.endsWith("/Geometry/Recipe")

            CustomRangeSlider.drawCustomRangeSlider(
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
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    param.baseMin = safeMin
                    param.baseMax = safeMax
                    param.baseValue = param.baseValue.coerceIn(safeMin, safeMax)
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
        if (ImGui.button("Rand Randomize Initial Value", ImGui.getContentRegionAvailX(), 30f)) {
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
        baseDl.addRectFilled(cx, cy, cx + baseBarW * param.baseValue, cy + 10f, CvTheme.getThemeColor("base"))
        ImGui.dummy(baseBarW, 10f)
    }
}
