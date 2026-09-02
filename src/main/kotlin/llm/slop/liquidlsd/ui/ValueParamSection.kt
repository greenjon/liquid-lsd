package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.MandalaLibrary
import kotlin.math.roundToInt

private val MAX_POINTS_PRESETS = listOf(100, 250, 500, 750, 1000, 1500, 2000)
val MIX_MODE_LABELS = arrayOf(
    "0: Add (ADD)",
    "1: Screen (SCREEN)",
    "2: Multiply (MULT)",
    "3: Max (MAX)",
    "4: Crossfade (XFADE)"
)

fun getMixModeLabel(mode: Float): String {
    val idx = mode.roundToInt().coerceIn(0, 4)
    return when (idx) {
        0 -> "Add (ADD)"
        1 -> "Screen (SCREEN)"
        2 -> "Multiply (MULT)"
        3 -> "Max (MAX)"
        4 -> "Crossfade (XFADE)"
        else -> "Crossfade (XFADE)"
    }
}

object ValueParamSection {

    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        state: PresetGridState,
        param: ModulatableParameter,
        paramKey: String,
        themeColor: Int,
        mandala: Mandala?
    ) {
        session.uiTheme.h2Colored(0.4f, 0.9f, 1.0f, 1.0f, paramKey.replace("/", " | "))
        ImGui.separator()
        ImGui.spacing()

        // Live value text readout
        val isMixerMode = paramKey == "Mixer/mode"
        val isBgStyle = paramKey.endsWith("/Background/Style")
        val isHueSweep = paramKey.endsWith("/HueSweep") || paramKey.endsWith("/Color/HueSweep")
        val isLobes = paramKey.endsWith("/Geometry/Lobes")
        val isRecipeSelect = paramKey.endsWith("/Geometry/Recipe")
        val isMaxPoints = paramKey.endsWith("/Max Points")
        val liveVal = param.value
        val liveLabel = when {
            isMixerMode -> getMixModeLabel(liveVal)
            isMaxPoints -> "${liveVal.roundToInt()} points"
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

        // Oscilloscope showing 100% true recorded history
        OscilloscopeDrawer.drawValueOscilloscope(session, param, themeColor)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        if (paramKey == "Mixer/crossfade") {
            // The master crossfader is dynamically driven by manual takeover, Auto-VJ transitions,
            // or unbiased center (0.0) CV modulation. Hide the Initial Range section.
            return
        }

        // --- SCROLLABLE BODY: INITIAL VALUE CONTROLS ---
        val childFlags = if (CustomRangeSlider.isAnySliderHovered) imgui.flag.ImGuiWindowFlags.NoScrollWithMouse else 0
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        if (ImGui.beginChild("##value_param_scroll", 0f, 0f, false, childFlags)) {
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
                defaultValue = param.defaultValue,
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
            if (isMaxPoints) {
                session.uiTheme.caption("Point Count (GPU Performance):")
                val currentPts = param.baseValue.roundToInt()
                val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
                val pillH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() + 6f * fontScale }.coerceAtLeast(24f * fontScale)
                val availW = ImGui.getContentRegionAvailX()
                var startLine = true

                for (preset in MAX_POINTS_PRESETS) {
                    val label = "$preset"
                    val btnW = session.uiTheme.withFont(UITheme.FontLevel.BODY) { (ImGui.calcTextSize(label).x + 16f * fontScale).coerceAtLeast(40f * fontScale) }

                    if (!startLine) {
                        val lastX = ImGui.getCursorPosX()
                        if (lastX + btnW + 4f < availW) {
                            ImGui.sameLine()
                        } else {
                            startLine = true
                        }
                    }

                    val isActive = (currentPts == preset)
                    if (isActive) {
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, themeColor)
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, themeColor)
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, themeColor)
                        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))
                    }

                    if (ImGui.button("$label##maxpts_pill_$preset", btnW, pillH)) {
                        val newVal = preset.toFloat()
                        param.baseValue = newVal
                        param.baseMin = newVal
                        param.baseMax = newVal
                    }

                    if (isActive) {
                        ImGui.popStyleColor(4)
                    }

                    startLine = false
                }
                ImGui.spacing()
                session.uiTheme.caption("${Icons.ALERT} Not CV-modulatable — higher counts may reduce frame rate.")
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            } else if (isLobes) {
                session.uiTheme.caption("Lobe Count Quick Selection:")
                val currentLobe = param.baseValue.roundToInt()
                val availableLobes = MandalaLibrary.uniquePetals
                val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
                val pillH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() + 6f * fontScale }.coerceAtLeast(24f * fontScale)
                val availW = ImGui.getContentRegionAvailX()
                var startLine = true

                for (petal in availableLobes) {
                    val label = "$petal"
                    val btnW = session.uiTheme.withFont(UITheme.FontLevel.BODY) { (ImGui.calcTextSize(label).x + 14f * fontScale).coerceAtLeast(28f * fontScale) }

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

                    if (ImGui.button("$label##lobe_pill_$petal", btnW, pillH)) {
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
                
                val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
                val stepBtnW = 36f * fontScale
                val stepBtnH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() + 6f * fontScale }.coerceAtLeast(24f * fontScale)

                val canPrev = count > 1 && currentIdx > 0
                val canNext = count > 1 && currentIdx < count - 1

                if (!canPrev) ImGui.beginDisabled()
                if (ImGui.button("◀##recipe_prev", stepBtnW, stepBtnH)) {
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
                if (ImGui.button("▶##recipe_next", stepBtnW, stepBtnH)) {
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
            } else if (isMixerMode) {
                session.uiTheme.caption("Deck A/B Mix Mode:")
                val currentIdx = param.baseValue.roundToInt().coerceIn(0, MIX_MODE_LABELS.size - 1)
                val selectedOpt = ImInt(currentIdx)
                ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 10f)
                if (ImGui.combo("##mixer_mode_combo", selectedOpt, MIX_MODE_LABELS)) {
                    val nextIdx = selectedOpt.get().coerceIn(0, MIX_MODE_LABELS.size - 1)
                    val newVal = nextIdx.toFloat()
                    param.baseValue = newVal
                    param.baseMin = newVal
                    param.baseMax = newVal
                }
                if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                    ImGui.setTooltip("Select blending mode between Deck A and Deck B.\n0: ADD — additive blend\n1: SCREEN — screen blend\n2: MULT — multiply blend\n3: MAX — maximum pixel brightness\n4: XFADE — 4th-order polynomial crossfade")
                }
                ImGui.popItemWidth()

                ImGui.spacing()
                session.uiTheme.caption("${Icons.ALERT} Not CV-modulatable — sets static deck compositing formula.")
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
                defaultValue = param.defaultValue * scale,
                isRandomizable = param.randomizeBase,
                showControls = true,
                formatValue = {
                    when {
                        isMixerMode -> getMixModeLabel(it)
                        isMaxPoints -> "${it.roundToInt()} pts"
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
            val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
            val btnH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getTextLineHeight() + 8f * fontScale }.coerceAtLeast(28f * fontScale)
            val randomizeBaseActive = param.randomizeBase
            if (!randomizeBaseActive) {
                ImGui.beginDisabled()
            }
            if (ImGui.button("${Icons.DICES}  Randomize Initial Value", ImGui.getContentRegionAvailX(), btnH)) {
                param.randomizeBaseValue()
            }
            if (!randomizeBaseActive) {
                ImGui.endDisabled()
            }
        }


        ImGui.spacing()
        if (isMixerMode) {
            session.uiTheme.caption("Static Initial Value: ${getMixModeLabel(param.baseValue)}")
        } else if (isMaxPoints) {
            session.uiTheme.caption("Static Initial Value: ${param.baseValue.roundToInt()} pts")
        } else if (isHueSweep && mandala != null) {
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
        val normBase = if (param.maxClamp > param.minClamp) ((param.baseValue - param.minClamp) / (param.maxClamp - param.minClamp)).coerceIn(0f, 1f) else param.baseValue.coerceIn(0f, 1f)
        baseDl.addRectFilled(cx, cy, cx + baseBarW * normBase, cy + 10f, CvTheme.getThemeColor("base"))
        ImGui.dummy(baseBarW, 10f)

            ImGui.endChild()
        }
        ImGui.popStyleVar()
    }
}
