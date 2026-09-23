package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import llm.slop.liquidlsd.macro.*
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.cv.isAudioSource

/**
 * Binding Inspector drawer for the selected Macro Control in Column 3.
 *
 * Displays control metadata (label) and its active 1-to-many bindings list (0..4).
 * Allows modifying travel bounds (Min/Max), response curves, direction inversion, and enabling/disabling
 * bindings (which acts as the field-ownership lock release mechanism per proposal §3.3).
 *
 * Hardware MIDI Learn for macro knobs lives in the Performance Mode 4×4 matrix
 * ([PerformanceMatrixPanel]), not here -- this drawer only handles parameter-bind Learn.
 */
object MacroBindingInspector {
    private val labelBuf = ImString(64)
    private var lastControlId: String? = null

    // Reusable Im-types and scratch arrays allocated once as fields (imgui_memory_management guidelines)
    private val enabledBuf = ImBoolean()
    private val invertBuf = ImBoolean()
    private val curveBuf = ImInt()
    private val linkBuf = ImInt()
    private val minValBuf = FloatArray(1)
    private val maxValBuf = FloatArray(1)
    private val stepCountBuf = IntArray(1)
    private val curves = arrayOf("Linear", "Exponential", "Logarithmic", "S-Curve", "Step")
    private val linkModes = arrayOf("Full (0-100%)", "1st Half (0-50%)", "2nd Half (50-100%)", "Triangle (Peak)", "Bipolar (Center-0)")

    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        control: MacroControl?,
        parametersState: ParametersState,
        mixer: Mixer
    ) {
        if (control == null) {
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                ImGui.textDisabled("Select a Knob above to inspect bindings.")
            }
            return
        }

        if (lastControlId != control.id) {
            labelBuf.set(control.label)
            lastControlId = control.id
        }

        ImGui.pushID(control.id)

        // Header row: Type badge, name input, value readout, and Learn button
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textColored(0.2f, 0.85f, 1.0f, 1.0f, "KNOB")
        }
        ImGui.sameLine(0f, 6f)

        ImGui.setNextItemWidth(120f)
        if (ImGui.inputText("##rename_${control.id}", labelBuf)) {
            control.label = labelBuf.get()
        }
        itemTooltip("Rename macro control.")

        ImGui.sameLine(0f, 8f)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled("Val: ${"%.2f".format(control.value)}")
        }

        val isLearning = MacroLearnState.isControlLearning(control.id)
        ImGui.sameLine(0f, 8f)
        if (isLearning) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.85f, 0.2f, 0.2f, 0.8f))
            if (ImGui.button("${Icons.X} Cancel Learn##cancel_learn")) {
                MacroLearnState.cancelLearn()
            }
            ImGui.popStyleColor()
            itemTooltip("Cancel Learn Mode.")
        } else {
            val canLearn = control.bindings.size < MacroControl.MAX_BINDINGS_PER_CONTROL
            if (canLearn) {
                if (ImGui.button("${Icons.REFRESH} Learn##start_learn")) {
                    MacroLearnState.startLearn(control.id)
                }
                itemTooltip("Arm Learn Mode. Then click any parameter slider or modulator property in Column 1 or 2.")
            } else {
                ImGui.textDisabled("[Max 4 targets]")
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // Bindings list
        if (control.bindings.isEmpty()) {
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                ImGui.textDisabled("No parameters bound. Click [Learn] then click any parameter in Column 1 or 2.")
            }
        } else {
            val toRemove = mutableListOf<Int>()
            val dl = ImGui.getWindowDrawList()
            for ((idx, binding) in control.bindings.withIndex()) {
                ImGui.pushID("binding_$idx")

                enabledBuf.set(binding.enabled)
                if (ImGui.checkbox("##enabled", enabledBuf)) {
                    binding.enabled = enabledBuf.get()
                    MacroEngine.invalidate()
                }
                itemTooltip(if (binding.enabled) "Active: target parameter locked to this macro. Uncheck to release parameter for manual control." else "Disabled: parameter released for manual control. Check to resume macro lock.")

                ImGui.sameLine(0f, 6f)
                val targetText = if (binding.targetType == MacroTargetType.PARAM_BASE_VALUE) {
                    binding.parameterId
                } else {
                    "${binding.parameterId} [${binding.propertyName}]"
                }

                // Parse the deck/section from the parameterId for navigation.
                // Paths are "Deck A/fbZoom", "Deck A/Mandala/L1", "Mixer/crossfade", etc.
                val slashIdx = binding.parameterId.indexOf('/')
                val navDeck = if (slashIdx > 0) binding.parameterId.substring(0, slashIdx) else null
                val navSubTab = when (navDeck) {
                    "Mixer" -> "CTRL"
                    else    -> "SRC"
                }

                // Render as a tinted text label. textColored + isItemClicked is the standard
                // ImGui clickable-text pattern and reliably receives clicks inside child windows,
                // unlike smallButton which can be swallowed by a scroll child's focus logic.
                val linkR = if (binding.enabled) 0.35f else 0.5f
                val linkG = if (binding.enabled) 0.75f else 0.5f
                val linkB = if (binding.enabled) 1.0f  else 0.5f
                ImGui.textColored(linkR, linkG, linkB, 1f, targetText)

                if (ImGui.isItemHovered()) {
                    ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.Hand)
                    if (navDeck != null) itemTooltip("\u2192 Go to $navDeck \u2192 $navSubTab")
                }
                if (ImGui.isItemClicked(0) && navDeck != null) {
                    parametersState.activeTopTab = navDeck
                    parametersState.setDeckSubTab(navDeck, navSubTab)
                    val targetParam = ParameterResolver.findParameterByPath(mixer, binding.parameterId)
                    if (targetParam != null) {
                        val cvId = if (binding.targetType == MacroTargetType.MODULATOR_PROPERTY) {
                            targetParam.modulators.getOrNull(binding.modulatorIndex)?.let { modulatorCvId(it.sourceId) } ?: "value"
                        } else {
                            "value"
                        }
                        parametersState.select(ParameterCellId(binding.parameterId, cvId), targetParam)
                    }
                }

                val btnSize = 20f
                val availW = ImGui.getContentRegionAvailX()
                ImGui.sameLine(ImGui.getCursorPosX() + availW - btnSize)
                if (ImGui.button("${Icons.TRASH}##del_$idx", btnSize, btnSize)) {
                    toRemove.add(idx)
                }
                itemTooltip("Delete this binding.")

                // Row 2: Live output meter knob + Min/Max, Visual Link Mode & Invert, and Curve controls
                ImGui.indent(4f)

                // Compute the current mapped output value for this binding based on the macro control's value.
                val mappedVal = MacroCurve.mapToRange(control.value, binding)
                val targetParam = ParameterResolver.findParameterByPath(mixer, binding.parameterId)
                val meterMin = minOf(binding.minVal, binding.maxVal)
                val meterMax = maxOf(binding.minVal, binding.maxVal)
                val meterType = targetParam?.meterType ?: if (meterMin < 0f) llm.slop.liquidlsd.parameters.MeterType.BIPOLAR else llm.slop.liquidlsd.parameters.MeterType.MONOPOLAR
                val knobColor = if (binding.enabled) CvTheme.getThemeColor("value") else ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.5f)
                val knobBgCol = ImGui.colorConvertFloat4ToU32(0.10f, 0.12f, 0.15f, 1f)
                val knobBorderCol = if (binding.enabled) ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.7f, 0.8f) else ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 0.6f)

                val knobSize = 22f
                val knobR = knobSize * 0.5f
                val knobScreenX = ImGui.getCursorScreenPosX()
                val knobScreenY = ImGui.getCursorScreenPosY()

                ParametersRenderer.drawKnobMeter(
                    session = session,
                    dl = dl,
                    x = knobScreenX,
                    y = knobScreenY,
                    r = knobR,
                    value = mappedVal,
                    min = meterMin,
                    max = meterMax,
                    meterType = meterType,
                    baseValue = null,
                    baseMin = null,
                    baseMax = null,
                    color = knobColor,
                    bgCol = knobBgCol,
                    borderCol = knobBorderCol,
                    isBypassed = !binding.enabled,
                    isHovered = false
                )

                // Invisible button over the knob for tooltip and hover affordance
                ImGui.invisibleButton("##binding_knob_$idx", knobSize, knobSize)
                val isKnobHovered = ImGui.isItemHovered()
                if (isKnobHovered) {
                    itemTooltip("Live Binding Value: ${"%.3f".format(mappedVal)} (Macro: ${"%.2f".format(control.value)})\nRange: ${"%.2f".format(binding.minVal)} -> ${"%.2f".format(binding.maxVal)}")
                }

                ImGui.sameLine(0f, 6f)
                val subControlsY = knobScreenY + (knobSize - ImGui.getFrameHeight()) * 0.5f
                ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), subControlsY)

                ImGui.setNextItemWidth(60f)
                minValBuf[0] = binding.minVal
                if (ImGui.dragFloat("Min", minValBuf, 0.01f, -10f, 10f, "%.2f")) {
                    binding.minVal = minValBuf[0]
                }
                itemTooltip("Output value when macro is at 0.0.")

                ImGui.sameLine(0f, 6f)
                ImGui.setNextItemWidth(60f)
                maxValBuf[0] = binding.maxVal
                if (ImGui.dragFloat("Max", maxValBuf, 0.01f, -10f, 10f, "%.2f")) {
                    binding.maxVal = maxValBuf[0]
                }
                itemTooltip("Output value when macro is at 1.0.")

                ImGui.sameLine(0f, 8f)
                LinkModeButton.drawMacroLink(
                    id = "macro_bind_${control.id}_$idx",
                    mode = binding.linkMode,
                    inverted = binding.inverted,
                    isLinked = binding.enabled,
                    onCycleMode = {
                        binding.linkMode = when (binding.linkMode) {
                            MacroLinkMode.FULL -> MacroLinkMode.FIRST_HALF
                            MacroLinkMode.FIRST_HALF -> MacroLinkMode.SECOND_HALF
                            MacroLinkMode.SECOND_HALF -> MacroLinkMode.TRIANGLE
                            MacroLinkMode.TRIANGLE -> MacroLinkMode.BIPOLAR
                            MacroLinkMode.BIPOLAR -> MacroLinkMode.FULL
                        }
                    },
                    onSelectMode = { binding.linkMode = it },
                    onToggleInvert = { binding.inverted = !binding.inverted }
                )

                ImGui.sameLine(0f, 8f)
                ImGui.setNextItemWidth(90f)
                val currentCurveIdx = when (binding.curve) {
                    MacroCurveType.LINEAR -> 0
                    MacroCurveType.EXPONENTIAL -> 1
                    MacroCurveType.LOGARITHMIC -> 2
                    MacroCurveType.S_CURVE -> 3
                    MacroCurveType.STEP -> 4
                }
                curveBuf.set(currentCurveIdx)
                if (ImGui.combo("Curve", curveBuf, curves)) {
                    binding.curve = when (curveBuf.get()) {
                        0 -> MacroCurveType.LINEAR
                        1 -> MacroCurveType.EXPONENTIAL
                        2 -> MacroCurveType.LOGARITHMIC
                        3 -> MacroCurveType.S_CURVE
                        else -> MacroCurveType.STEP
                    }
                }
                itemTooltip("Select response curve shaping.")

                if (binding.curve == MacroCurveType.STEP) {
                    ImGui.sameLine(0f, 6f)
                    ImGui.setNextItemWidth(45f)
                    stepCountBuf[0] = binding.stepCount
                    if (ImGui.dragInt("Steps", stepCountBuf, 1f, 2, 64)) {
                        binding.stepCount = stepCountBuf[0].coerceIn(2, 64)
                    }
                    itemTooltip("Number of quantized steps across the travel range.")
                }

                ImGui.unindent(4f)
                ImGui.spacing()
                ImGui.popID()
            }

            if (toRemove.isNotEmpty()) {
                toRemove.sortedDescending().forEach { control.bindings.removeAt(it) }
                MacroEngine.invalidate()
            }
        }

        ImGui.popID()
    }

    /**
     * Maps a [llm.slop.liquidlsd.parameters.CvModulator.sourceId] to the Properties panel's
     * cvSourceId column key (see [ParametersRenderer.drawCvCell]): individual audio-reactive
     * bands share the single "audio" column/tab, MIDI CC modulators share "midi", and every
     * other source (e.g. "lfo", "seq") is used verbatim as its own column.
     */
    private fun modulatorCvId(sourceId: String): String = when {
        isAudioSource(sourceId)        -> "audio"
        sourceId.startsWith("midi_cc_") -> "midi"
        else                             -> sourceId
    }
}
