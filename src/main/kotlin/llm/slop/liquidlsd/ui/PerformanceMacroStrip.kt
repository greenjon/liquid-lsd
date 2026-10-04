package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.*
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Edit-row binding strip: replaces the *content* of a row's two left control lines (never its geometry)
 * while a macro knob is selected in the Edit view. See [macroStripModeFor] for when it shows.
 *
 * Line 1: [GLB tag] name (double-click renames), live value, Learn/Cancel, binding chips 1-4, close.
 * Line 2: the selected binding in [MacroBindingEditor.drawLine], or a hint when there are none.
 * FX banks have hard-assigned knobs: line 2 describes the knob's role instead and nothing is editable.
 */
internal object PerformanceMacroStrip {
    private val renameBuf = ImString(64)
    private const val RENAME_POPUP = "macro_strip_rename"
    private const val HINT = "Click a parameter or property below to bind"
    private val chipLabels = Array(MacroControl.MAX_BINDINGS_PER_CONTROL) { "${it + 1}" }
    private val chipIds = Array(MacroControl.MAX_BINDINGS_PER_CONTROL) { "##chip$it" }

    /**
     * Draws the strip at screen ([x], [row1Y]) / ([x], [row2Y]), [w] wide, [ctrlH] per line. [onLearn] arms
     * Learn for the control (the host also opens that row's Deep Edit params). Returns true when the user
     * closed the strip.
     */
    fun draw(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        mode: MacroStripMode,
        bankId: String,
        control: MacroControl,
        fxChain: FxChain?,
        x: Float,
        row1Y: Float,
        row2Y: Float,
        w: Float,
        ctrlH: Float,
        onLearn: () -> Unit
    ): Boolean {
        var close = false
        val gap = 4f
        val dl = ImGui.getWindowDrawList()
        val isFx = bankId in FxMacroSync.FX_BANK_IDS
        val learning = MacroLearnState.isControlLearning(control.id)
        ImGui.pushID(control.id)
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 4f, ((ctrlH - ImGui.getFontSize()) * 0.5f).coerceAtLeast(0f))
        var cx = x

        // -- Line 1 --
        if (mode == MacroStripMode.GUEST) {
            val tag = "GLB"
            val tw = ImGui.calcTextSize(tag).x + 8f
            dl.addRectFilled(cx, row1Y, cx + tw, row1Y + ctrlH, TangoPalette.u32(PerformanceColors.COLOR_GLOBAL, 0.25f), 4f)
            dl.addRect(cx, row1Y, cx + tw, row1Y + ctrlH, TangoPalette.u32(PerformanceColors.COLOR_GLOBAL, 0.9f), 4f, 0, 1f)
            dl.addText(cx + 4f, TextFit.centeredY(row1Y, ctrlH, ImGui.getTextLineHeight()), TangoPalette.u32(PerformanceColors.COLOR_GLOBAL), tag)
            cx += tw + gap
        }

        val closeW = ctrlH
        val learnW = 58f
        val chipW = ctrlH
        val chipsW = if (isFx) 0f else (chipW + 2f) * MacroControl.MAX_BINDINGS_PER_CONTROL
        val valueW = 38f
        val rightW = closeW + gap + (if (isFx) 0f else learnW + gap + chipsW + gap)
        val nameW = (x + w - cx - rightW - valueW - gap * 2).coerceAtLeast(40f)

        val name = control.label.ifEmpty { "Knob" }
        ImGui.setCursorScreenPos(cx, row1Y)
        ImGui.invisibleButton("##name", nameW, ctrlH)
        dl.addText(cx + 2f, TextFit.centeredY(row1Y, ctrlH, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.Text), TextFit.ellipsize(name, nameW - 4f))
        itemTooltip("$name\nDouble-click to rename.")
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0) && !isFx) {
            renameBuf.set(control.label)
            ImGui.openPopup(RENAME_POPUP)
        }
        if (ImGui.beginPopup(RENAME_POPUP)) {
            ImGui.setNextItemWidth(160f)
            if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere()
            if (ImGui.inputText("##rename", renameBuf, imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue)) {
                control.label = renameBuf.get()
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
        cx += nameW + gap

        val valueText = "%.2f".format(control.value)
        dl.addText(cx, TextFit.centeredY(row1Y, ctrlH, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled), valueText)
        cx += valueW + gap

        if (!isFx) {
            ImGui.setCursorScreenPos(cx, row1Y)
            val canLearn = control.bindings.size < MacroControl.MAX_BINDINGS_PER_CONTROL
            ImGui.beginDisabled(!canLearn && !learning)
            if (ImGui.button(if (learning) "${Icons.X} Cancel##learn" else "${Icons.REFRESH} Learn##learn", learnW, ctrlH)) {
                if (learning) MacroLearnState.cancelLearn() else onLearn()
            }
            ImGui.endDisabled()
            itemTooltip(
                if (learning) "Cancel Learn."
                else if (bankId == MacroEngine.GLOBAL) "Arm Learn, then open any Edit row and click a parameter or modulator property. Global knobs can bind anywhere."
                else "Arm Learn, then click a parameter or modulator property in this row's section."
            )
            cx += learnW + gap

            val selIdx = MacroLearnState.selectedBindingIdx(control)
            control.bindings.forEachIndexed { i, binding ->
                ImGui.setCursorScreenPos(cx, row1Y)
                val selected = i == selIdx
                if (selected) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, TangoPalette.u32(TangoPalette.SYNC.normal, 0.55f))
                if (!binding.enabled) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled))
                val clicked = ImGui.button(chipLabels[i] + chipIds[i], chipW, ctrlH)
                if (!binding.enabled) ImGui.popStyleColor()
                if (selected) ImGui.popStyleColor()
                itemTooltip(targetLabel(binding) + "\nClick to edit and jump to it.")
                if (clicked) {
                    MacroLearnState.selectBinding(control, i)
                    MacroBindingNav.navTargetFor(binding.parameterId)?.let { MacroBindingNav.navigateTo(binding, it, parametersState, mixer) }
                }
                cx += chipW + 2f
            }
        }

        ImGui.setCursorScreenPos(x + w - closeW, row1Y)
        if (ImGui.button("${Icons.X}##close", closeW, ctrlH)) close = true
        itemTooltip("Close the binding strip and show the row's controls.")

        // -- Line 2 --
        val bindings = control.bindings
        when {
            isFx -> {
                val i = MacroEngine.getBank(bankId)?.knobs?.indexOf(control) ?: -1
                val role = if (fxChain != null && i >= 0) FxMacroSummary.knobRole(fxChain, i, control) else "FX knob"
                dl.addText(x + 2f, TextFit.centeredY(row2Y, ctrlH, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled),
                    TextFit.ellipsize("$role (fixed assignment)", w - 4f))
            }
            bindings.isEmpty() -> {
                val text = if (learning) "Learning: click a parameter or property to bind" else HINT
                dl.addText(x + 2f, TextFit.centeredY(row2Y, ctrlH, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled),
                    TextFit.ellipsize(text, w - 4f))
            }
            else -> {
                val binding = bindings[MacroLearnState.selectedBindingIdx(control)]
                val param = ParameterResolver.findParameterByPath(mixer, binding.parameterId)
                if (MacroBindingEditor.drawLine(control, binding, param, targetLabel(binding), x, row2Y, w, ctrlH)) {
                    bindings.remove(binding)
                    MacroEngine.invalidate()
                }
            }
        }

        ImGui.popStyleVar()
        ImGui.popID()
        return close
    }

    private fun targetLabel(binding: MacroBinding): String =
        if (binding.targetType == MacroTargetType.PARAM_BASE_VALUE) binding.parameterId
        else "${binding.parameterId} [${binding.propertyName}]"
}
