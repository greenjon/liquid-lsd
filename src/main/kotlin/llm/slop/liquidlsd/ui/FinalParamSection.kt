package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.CvHistoryBuffer
import llm.slop.liquidlsd.cv.evaluateModulator
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ModulationOperator
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.MandalaLibrary
import kotlin.math.roundToInt

object FinalParamSection {

    fun draw(session: llm.slop.liquidlsd.SessionContext, state: PatchGridState,
        param: ModulatableParameter,
        paramKey: String,
        themeColor: Int,
        mandala: Mandala?,
        modulatorHistories: MutableMap<String, CvHistoryBuffer>
    ) {
        session.uiTheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey.replace("/", " | "))
        ImGui.separator()
        ImGui.spacing()

        // Live value text readout
        val isBgStyle = paramKey.endsWith("/Background/Style")
        val isHueSweep = paramKey.endsWith("/HueSweep") || paramKey.endsWith("/Color/HueSweep")
        val isLobes = paramKey.endsWith("/Geometry/Lobes")
        val isRecipeSelect = paramKey.endsWith("/Geometry/Recipe")
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
            isRecipeSelect && mandala != null -> {
                val currentLobe = mandala.parameters["Lobes"]?.value?.roundToInt() ?: mandala.recipe.petals
                val closestLobe = MandalaLibrary.uniquePetals.minByOrNull { kotlin.math.abs(it - currentLobe) } ?: 3
                val filtered = MandalaLibrary.recipesByPetals[closestLobe] ?: emptyList()
                if (filtered.isNotEmpty()) {
                    val idx = (liveVal * (filtered.size - 1)).roundToInt().coerceIn(0, filtered.size - 1)
                    "Recipe ${idx + 1}/${filtered.size} [${filtered[idx].a}, ${filtered[idx].b}, ${filtered[idx].c}, ${filtered[idx].d}]"
                } else "%.3f".format(liveVal)
            }
            param.isAngle -> "${"%.1f".format(liveVal * 180f / kotlin.math.PI.toFloat())}°"
            else -> "%.3f".format(liveVal)
        }
        session.uiTheme.h3("Live Modulated Value: $liveLabel")
        ImGui.spacing()

        // Update active modulators history
        val activeMods = param.modulators.filter { 
            !it.bypassed && (session.cvRegistry.exists(it.sourceId) || it.sourceId.startsWith("midi_cc_"))
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
        OscilloscopeDrawer.drawFinalOscilloscope(session, param.history, param.minClamp, param.maxClamp, themeColor, activeMods, modulatorHistories, isAngle = param.isAngle)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- INITIAL VALUE CONTROLS ---
        session.uiTheme.h3("Initial Value Configuration")
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

            session.uiTheme.caption("Symmetric Cycles (Symmetry-preserving factor/multiple of $petals petals):")

            val labels = options.map { "$it cycles" }.toTypedArray()
            val selectedOpt = ImInt(currentIndex)
            ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 10f)
            if (ImGui.combo("##hue_symmetry_combo", selectedOpt, labels)) {
                val nextIdx = selectedOpt.get()
                val newVal = if (options.size > 1) nextIdx.toFloat() / (options.size - 1).toFloat() else 0.0f
                param.set(newVal)
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Select symmetry-preserving cycle count. Keeps color distributions aligned with geometry lobes.")
            }
            ImGui.popItemWidth()

            ImGui.spacing()
            session.uiTheme.caption("Choose the number of color repetitions along the curve.")
            session.uiTheme.caption("Because it is a factor or multiple of $petals, symmetry is preserved!")

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            CustomRangeSlider.drawCustomRangeSlider(session, idPrefix = "hue_sweep_base",
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
            if (isLobes) {
                session.uiTheme.caption("Lobe Count Quick Selection:")
                val currentLobe = param.baseValue.roundToInt()
                val availableLobes = MandalaLibrary.uniquePetals
                val availW = ImGui.getContentRegionAvailX()
                var startLine = true

                for (petal in availableLobes) {
                    val label = "$petal"
                    val btnW = 30f

                    if (!startLine) {
                        val lastX = ImGui.getCursorPosX()
                        if (lastX + btnW + 4f < availW) {
                            ImGui.sameLine()
                        } else {
                            startLine = true
                        }
                    }

                    val isActive = (currentLobe == petal)
                    if (isActive) {
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, themeColor)
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, themeColor)
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, themeColor)
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))
                    }

                    if (ImGui.button("$label##lobe_pill_$petal", btnW, 24f)) {
                        val newVal = petal.toFloat()
                        param.baseValue = newVal
                        if (!param.randomizeBase) {
                            param.baseMin = newVal
                            param.baseMax = newVal
                        }
                    }

                    if (isActive) {
                        ImGui.popStyleColor(4)
                    }

                    startLine = false
                }
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            } else if (isRecipeSelect && mandala != null) {
                val currentLobe = mandala.parameters["Lobes"]?.value?.roundToInt() ?: mandala.recipe.petals
                val closestLobe = MandalaLibrary.uniquePetals.minByOrNull { kotlin.math.abs(it - currentLobe) } ?: 3
                val filtered = MandalaLibrary.recipesByPetals[closestLobe] ?: emptyList()
                val count = filtered.size
                val currentIdx = if (count > 0) {
                    (param.baseValue * (count - 1)).roundToInt().coerceIn(0, count - 1)
                } else 0

                session.uiTheme.caption("Recipe Selection Stepper ($closestLobe lobes):")
                
                val canPrev = count > 1 && currentIdx > 0
                val canNext = count > 1 && currentIdx < count - 1

                if (!canPrev) ImGui.beginDisabled()
                if (ImGui.button("◀##recipe_prev", 36f, 26f)) {
                    val prevIdx = (currentIdx - 1).coerceAtLeast(0)
                    val newVal = if (count > 1) prevIdx.toFloat() / (count - 1).toFloat() else 0f
                    param.baseValue = newVal
                    if (!param.randomizeBase) {
                        param.baseMin = newVal
                        param.baseMax = newVal
                    }
                }
                if (!canPrev) ImGui.endDisabled()

                ImGui.sameLine()
                ImGui.alignTextToFramePadding()
                val recipeLabel = if (count > 0) "Recipe ${currentIdx + 1} of $count" else "No Recipes"
                session.uiTheme.body(recipeLabel)

                ImGui.sameLine()
                if (!canNext) ImGui.beginDisabled()
                if (ImGui.button("▶##recipe_next", 36f, 26f)) {
                    val nextIdx = (currentIdx + 1).coerceAtMost(count - 1)
                    val newVal = if (count > 1) nextIdx.toFloat() / (count - 1).toFloat() else 0f
                    param.baseValue = newVal
                    if (!param.randomizeBase) {
                        param.baseMin = newVal
                        param.baseMax = newVal
                    }
                }
                if (!canNext) ImGui.endDisabled()

                if (count > 0) {
                    val r = filtered[currentIdx]
                    session.uiTheme.caption("Coefficients: [${r.a}, ${r.b}, ${r.c}, ${r.d}]")
                }
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }

            val scale = if (param.isAngle) (180f / kotlin.math.PI.toFloat()) else 1f
            val invScale = if (param.isAngle) (kotlin.math.PI.toFloat() / 180f) else 1f

            CustomRangeSlider.drawCustomRangeSlider(session, label = "Initial Range",
                currentValue = param.baseValue * scale,
                currentMin = param.baseMin * scale,
                currentMax = param.baseMax * scale,
                minLimit = param.minClamp * scale,
                maxLimit = param.maxClamp * scale,
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
                                    "Recipe ${idx + 1}/${filtered.size} [${filtered[idx].a}, ${filtered[idx].b}, ${filtered[idx].c}, ${filtered[idx].d}]"
                                } else "No recipes"
                            } else "%.3f".format(it)
                        }
                        param.isAngle -> "${"%.1f".format(it)}°"
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
                    val safeMin = minOf(nextMin, nextMax) * invScale
                    val safeMax = maxOf(nextMin, nextMax) * invScale
                    param.baseMin = safeMin
                    param.baseMax = safeMax
                    param.baseValue = param.baseValue.coerceIn(safeMin, safeMax)
                },
                onValueChanged = { newVal ->
                    val radianVal = newVal * invScale
                    param.baseValue = radianVal
                    param.baseMin = radianVal
                    param.baseMax = radianVal
                }
            )
        }

        if (session.uiTheme.randomizationEnabled) {
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
        }


        ImGui.spacing()
        if (isHueSweep && mandala != null) {
            val petals = mandala.recipe.petals
            val options = mandala.getSymmetricHueCycles(petals)
            val idx = if (options.size > 1) (param.baseValue * (options.size - 1)).roundToInt().coerceIn(0, options.size - 1) else 0
            session.uiTheme.caption("Static Initial Value: ${options[idx]} cycles")
        } else if (isBgStyle) {
            val label = when (param.baseValue.roundToInt()) {
                0 -> "Off"
                1 -> "Solid Color"
                2 -> "Plasma"
                else -> "Off"
            }
            session.uiTheme.caption("Static Initial Value: $label")
        } else {
            val displayBase = if (param.isAngle) "${"%.1f".format(param.baseValue * 180f / kotlin.math.PI.toFloat())}°" else "%.3f".format(param.baseValue)
            session.uiTheme.caption("Static Initial Value: $displayBase")
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
