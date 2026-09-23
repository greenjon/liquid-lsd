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

    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        control: MacroControl?,
        parametersState: ParametersState,
        mixer: Mixer,
        showKnobHeader: Boolean = true
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

        if (showKnobHeader) {
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
        } else {
            // Compact Bay view: label input and bindings header only (knob/val/learn live directly on the knob UI)
            session.uiTheme.withFont(UITheme.FontLevel.H3) {
                ImGui.textColored(0.2f, 0.85f, 1.0f, 1.0f, "Target Bindings:")
            }
            ImGui.sameLine(0f, 8f)
            ImGui.setNextItemWidth(140f)
            if (ImGui.inputText("##rename_${control.id}", labelBuf)) {
                control.label = labelBuf.get()
            }
            itemTooltip("Rename macro control.")
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
            for ((idx, binding) in control.bindings.withIndex()) {
                ImGui.pushID("binding_$idx")

                val enabledBool = ImBoolean(binding.enabled)
                if (ImGui.checkbox("##enabled", enabledBool)) {
                    binding.enabled = enabledBool.get()
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

                // Min/Max, Invert, and Curve row
                ImGui.indent(18f)

                ImGui.setNextItemWidth(65f)
                val minVal = floatArrayOf(binding.minVal)
                if (ImGui.dragFloat("Min", minVal, 0.01f, -10f, 10f, "%.2f")) {
                    binding.minVal = minVal[0]
                }
                itemTooltip("Output value when macro is at 0.0.")

                ImGui.sameLine(0f, 8f)
                ImGui.setNextItemWidth(65f)
                val maxVal = floatArrayOf(binding.maxVal)
                if (ImGui.dragFloat("Max", maxVal, 0.01f, -10f, 10f, "%.2f")) {
                    binding.maxVal = maxVal[0]
                }
                itemTooltip("Output value when macro is at 1.0.")

                ImGui.sameLine(0f, 8f)
                val invertBool = ImBoolean(binding.inverted)
                if (ImGui.checkbox("Invert", invertBool)) {
                    binding.inverted = invertBool.get()
                }
                itemTooltip("Invert travel direction.")

                ImGui.sameLine(0f, 8f)
                ImGui.setNextItemWidth(100f)
                val curves = arrayOf("Linear", "Exponential", "Logarithmic", "S-Curve", "Step")
                val currentCurveIdx = when (binding.curve) {
                    MacroCurveType.LINEAR -> 0
                    MacroCurveType.EXPONENTIAL -> 1
                    MacroCurveType.LOGARITHMIC -> 2
                    MacroCurveType.S_CURVE -> 3
                    MacroCurveType.STEP -> 4
                }
                val curveImIdx = ImInt(currentCurveIdx)
                if (ImGui.combo("Curve", curveImIdx, curves)) {
                    binding.curve = when (curveImIdx.get()) {
                        0 -> MacroCurveType.LINEAR
                        1 -> MacroCurveType.EXPONENTIAL
                        2 -> MacroCurveType.LOGARITHMIC
                        3 -> MacroCurveType.S_CURVE
                        else -> MacroCurveType.STEP
                    }
                }
                itemTooltip("Select response curve shaping.")

                if (binding.curve == MacroCurveType.STEP) {
                    ImGui.sameLine(0f, 8f)
                    ImGui.setNextItemWidth(50f)
                    val steps = intArrayOf(binding.stepCount)
                    if (ImGui.dragInt("Steps", steps, 1f, 2, 64)) {
                        binding.stepCount = steps[0].coerceIn(2, 64)
                    }
                    itemTooltip("Number of quantized steps across the travel range.")
                }

                ImGui.unindent(18f)
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
