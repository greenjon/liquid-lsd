package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.*
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.FxMetaBinding
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.MetaLinkMode

/**
 * Edit-row binding strip: replaces the *content* of a row's two left control lines (never its geometry)
 * while a macro knob is selected in the Edit view. See [macroStripModeFor] for when it shows.
 *
 * Line 1: [GLB tag] name (double-click renames), live value, Learn/Cancel, binding chips 1-4, close.
 * Line 2: the selected binding in [MacroBindingEditor.drawLine], or a hint when there are none.
 * FX banks have hard-assigned knobs. A slot's Metaknob (see [metaSlotFor]) gets the same strip over the slot's own
 * [FxMetaBinding] list, edited through [FxMetaBindingEditor]; the other FX knobs (Super Knob, focus-mode parameters)
 * show their role read-only on line 2.
 */
internal object PerformanceMacroStrip {
    private val renameBuf = ImString(64)
    private const val RENAME_POPUP = "macro_strip_rename"
    private const val MENU_POPUP = "macro_strip_menu"
    private val bankBrowser = ImGuiFileBrowser("##macroBankBrowser")
    private val bankDir get() = java.io.File("library/knobpresets")

    /** Bank the open Export/Import file browser acts on (set when the kebab item is picked). */
    private var browserBankId: String? = null
    private var browserDeckLabel: String? = null
    private const val HINT = "Click Add Target, then a parameter or property below"
    private const val ADD_META_POPUP = "fx_meta_add_target"
    private const val MAX_META_CHIPS = 8
    /** Selected binding chip per Metaknob control id. */
    private val metaSel = HashMap<String, Int>()
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
        val isFx = llm.slop.liquidlsd.macro.TransitionMacroSync.isSyncOwned(bankId)
        val metaFx: ISFFilter? = if (isFx && fxChain != null) metaSlotFor(bankId, fxChain, control)?.let { fxChain.slots.getOrNull(it) } else null
        val metaCount = metaFx?.metaBindings?.size?.coerceAtMost(MAX_META_CHIPS) ?: 0
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
        val learnW = 76f
        val chipW = ctrlH
        val chipsW = if (metaFx != null) (chipW + 2f) * metaCount else if (isFx) 0f else (chipW + 2f) * MacroControl.MAX_BINDINGS_PER_CONTROL
        val valueW = 38f
        val rightW = closeW + gap + (if (metaFx != null) learnW + gap + chipsW + gap else if (isFx) 0f else learnW + gap + chipsW + gap + closeW + gap)
        val nameW = (x + w - cx - rightW - valueW - gap * 2).coerceAtLeast(40f)

        val name = control.label.ifEmpty { "Knob" }
        ImGui.setCursorScreenPos(cx, row1Y)
        ImGui.invisibleButton("##name", nameW, ctrlH)
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "macro_name_${bankId}_${control.label}" }
        dl.addText(cx + 2f, TextFit.centeredY(row1Y, ctrlH, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.Text), TextFit.ellipsize(name, nameW - 4f))
        itemTooltip(if (isFx) "$name\nThis knob follows its ${if (bankId == MacroEngine.TRANS) "transition" else "FX chain"}: it can't be renamed or given targets." else "$name\nDouble-click to rename.")
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0) && !isFx) openRename(control)
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

        val valueText = ValueFormat.knob(control.value)
        dl.addText(cx, TextFit.centeredY(row1Y, ctrlH, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled), valueText)
        cx += valueW + gap

        if (metaFx != null && fxChain != null) {
            cx = drawMetaChips(parametersState, mixer, bankId, control, metaFx, cx, row1Y, learnW, chipW, ctrlH, gap)
        }

        if (!isFx) {
            ImGui.setCursorScreenPos(cx, row1Y)
            val canLearn = control.bindings.size < MacroControl.MAX_BINDINGS_PER_CONTROL
            ImGui.beginDisabled(!canLearn && !learning)
            if (ButtonChrome.button(if (learning) "${Icons.X} Cancel##learn" else "Add Target##learn", learnW, ctrlH)) {
                if (learning) MacroLearnState.cancelLearn() else onLearn()
            }
            ImGui.endDisabled()
            itemTooltip(
                if (learning) "Cancel adding a target."
                else if (bankId == MacroEngine.GLOBAL) "Open any Edit row, then click a parameter or modulator property to add it as a target. Global knobs can target anything."
                else "Then click a parameter or modulator property in this row's section to add it as a target."
            )
            cx += learnW + gap

            val selIdx = MacroLearnState.selectedBindingIdx(control)
            control.bindings.forEachIndexed { i, binding ->
                ImGui.setCursorScreenPos(cx, row1Y)
                val selected = i == selIdx
                if (selected) ButtonChrome.pushColor(TangoPalette.u32(TangoPalette.SYNC.normal, 0.55f))
                if (!binding.enabled) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled))
                val clicked = ButtonChrome.button(chipLabels[i] + chipIds[i], chipW, ctrlH)
                if (!binding.enabled) ImGui.popStyleColor()
                if (selected) ImGui.popStyleColor(3)
                itemTooltip(targetLabel(binding) + "\nClick to edit and jump to it.")
                if (clicked) {
                    MacroLearnState.selectBinding(control, i)
                    MacroBindingNav.navTargetFor(binding.parameterId)?.let { MacroBindingNav.navigateTo(binding, it, parametersState, mixer) }
                }
                cx += chipW + 2f
            }
        }

        if (!isFx) {
            ImGui.setCursorScreenPos(cx, row1Y)
            if (ButtonChrome.button("${Icons.MORE_VERTICAL}##menu", closeW, ctrlH)) ImGui.openPopup(MENU_POPUP)
            itemTooltip("Rename this knob, or export / import the whole bank.")
            drawMenu(bankId, control)
        }

        ImGui.setCursorScreenPos(x + w - closeW, row1Y)
        if (ButtonChrome.button("${Icons.X}##close", closeW, ctrlH)) close = true
        itemTooltip("Close the target strip and show the row's controls.")

        // -- Line 2 --
        val bindings = control.bindings
        when {
            metaFx != null -> drawMetaLine(parametersState, mixer, bankId, control, metaFx, x, row2Y, w, ctrlH)
            isFx -> {
                val i = MacroEngine.getBank(bankId)?.knobs?.indexOf(control) ?: -1
                val role = if (bankId == MacroEngine.TRANS) (if (i == 0) "Crossfader" else "Transition parameter") else if (fxChain != null && i >= 0) FxMacroSummary.knobRole(fxChain, i, control) else "FX knob"
                dl.addText(x + 2f, TextFit.centeredY(row2Y, ctrlH, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled),
                    TextFit.ellipsize("$role (fixed assignment)", w - 4f))
            }
            bindings.isEmpty() -> {
                val text = if (learning) "Adding target: click a parameter or property" else HINT
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

    /** Slot index whose Metaknob [control] is in an FX bank, or null (Super Knob, focus-mode parameter knob, not FX). */
    internal fun metaSlotFor(bankId: String, chain: FxChain, control: MacroControl): Int? {
        if (bankId == MacroEngine.TRANS) return null
        val i = MacroEngine.getBank(bankId)?.knobs?.indexOf(control) ?: return null
        val focused = chain.focusedSlot
        val slot = if (focused == null) i - 1 else if (i == 0) focused else return null
        return slot.takeIf { it in 0 until FxChain.SLOT_COUNT && chain.slots.getOrNull(it) != null }
    }

    private fun fxTargetLabel(fx: ISFFilter, binding: FxMetaBinding): String = FxMetaEdit.targetLabel(fx, binding)

    private fun selectedMeta(control: MacroControl, fx: ISFFilter): Int =
        (metaSel[control.id] ?: 0).coerceIn(0, (fx.metaBindings.size - 1).coerceAtLeast(0))

    /** Line 1 right part for a slot Metaknob: Add Target (pick from the parameters that aren't targets yet) and one chip per target. Returns the new x. */
    private fun drawMetaChips(
        state: ParametersState, mixer: Mixer, bankId: String, control: MacroControl, fx: ISFFilter,
        startX: Float, y: Float, addW: Float, chipW: Float, h: Float, gap: Float
    ): Float {
        var cx = startX
        ImGui.setCursorScreenPos(cx, y)
        if (ButtonChrome.button("Add Target##meta_add", addW, h)) ImGui.openPopup(ADD_META_POPUP)
        itemTooltip("Make another parameter of ${fx.displayName} a target of this Metaknob.")
        pushOpenDropdownPadding()
        if (ImGui.beginPopup(ADD_META_POPUP)) {
            pushOpenDropdownFont()
            val linked = fx.metaBindings.mapTo(HashSet()) { it.targetParamName }
            var any = false
            for (input in fx.header.INPUTS) {
                if (!input.TYPE.equals("float", ignoreCase = true) || input.NAME in linked || fx.parameters[input.NAME] == null) continue
                any = true
                if (ImGui.menuItem(ParamLabels.of(fx.header, input.NAME))) {
                    metaSel[control.id] = fx.metaBindings.size
                    metaEdit(state, bankId, mixer, fx).run(null) { it.setParamLink(input.NAME, MetaLinkMode.FULL) }
                }
            }
            if (null !in linked) {
                any = true
                if (ImGui.menuItem("Dry/Wet")) {
                    metaSel[control.id] = fx.metaBindings.size
                    metaEdit(state, bankId, mixer, fx).run(null) { it.metaBindings.add(FxMetaBinding.DRY_WET_SAFETY_NET) }
                }
            }
            if (!any) ImGui.textDisabled("Every parameter is already a target")
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        cx += addW + gap

        val sel = selectedMeta(control, fx)
        for (i in 0 until minOf(fx.metaBindings.size, MAX_META_CHIPS)) {
            val b = fx.metaBindings[i]
            ImGui.setCursorScreenPos(cx, y)
            val selected = i == sel
            if (selected) ButtonChrome.pushColor(TangoPalette.u32(TangoPalette.SYNC.normal, 0.55f))
            if (!b.enabled) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled))
            val clicked = ButtonChrome.button("${i + 1}##meta_chip$i", chipW, h)
            if (!b.enabled) ImGui.popStyleColor()
            if (selected) ImGui.popStyleColor(3)
            itemTooltip(fxTargetLabel(fx, b) + "\nClick to edit.")
            if (clicked) metaSel[control.id] = i
            cx += chipW + 2f
        }
        return cx
    }

    /** Line 2 for a slot Metaknob: the selected target in [FxMetaBindingEditor.drawLine], or a hint when there is none. */
    private fun drawMetaLine(
        state: ParametersState, mixer: Mixer, bankId: String, control: MacroControl, fx: ISFFilter,
        x: Float, y: Float, w: Float, h: Float
    ) {
        if (fx.metaBindings.isEmpty()) {
            ImGui.getWindowDrawList().addText(x + 2f, TextFit.centeredY(y, h, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.TextDisabled),
                TextFit.ellipsize("No targets: click Add Target to choose a parameter", w - 4f))
            return
        }
        val binding = fx.metaBindings[selectedMeta(control, fx)]
        FxMetaBindingEditor.drawLine(fx, binding, fxTargetLabel(fx, binding), x, y, w, h, metaEdit(state, bankId, mixer, fx))
    }

    private fun metaEdit(state: ParametersState, bankId: String, mixer: Mixer, fx: ISFFilter): FxMetaBindingEditor.Edit =
        FxMetaBindingEditor.editFor(state, bankId, mixer, fx)

    private fun targetLabel(binding: MacroBinding): String =
        if (binding.targetType == MacroTargetType.PARAM_BASE_VALUE) binding.parameterId
        else "${binding.parameterId} [${binding.propertyName}]"
    private fun openRename(control: MacroControl) {
        renameBuf.set(control.label)
        ImGui.openPopup(RENAME_POPUP)
    }

    private fun drawMenu(bankId: String, control: MacroControl) {
        pushOpenDropdownPadding()
        if (ImGui.beginPopup(MENU_POPUP)) {
            pushOpenDropdownFont()
            if (ImGui.menuItem("Rename")) renameRequested = true
            ImGui.separator()
            if (ImGui.menuItem("Export Macro Bank...")) {
                browserBankId = bankId
                bankBrowser.open(ImGuiFileBrowser.Mode.SAVE, bankDir.also { it.mkdirs() }, "$bankId.knobpreset.json", listOf(".json"))
            }
            if (ImGui.menuItem("Import Macro Bank...")) {
                browserBankId = bankId
                browserDeckLabel = when (bankId) {
                    MacroEngine.DECK_A -> "Deck A"
                    MacroEngine.DECK_B -> "Deck B"
                    MacroEngine.DECK_BG -> "Deck BG"
                    MacroEngine.DECK_PV -> "Deck PV"
                    else -> null
                }
                bankBrowser.open(ImGuiFileBrowser.Mode.LOAD, bankDir.also { it.mkdirs() }, "", listOf(".json"))
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        if (renameRequested) {
            renameRequested = false
            openRename(control)
        }
    }

    private var renameRequested = false

    /** Draws the Export/Import file browser; call every frame from the root ID scope (see UIManager). */
    fun drawFileBrowser(mixer: Mixer) {
        bankBrowser.draw { file ->
            val bankId = browserBankId ?: return@draw
            val bank = MacroEngine.getBank(bankId) ?: return@draw
            try {
                if (bankBrowser.mode == ImGuiFileBrowser.Mode.SAVE) {
                    MacroBankSerializer.exportToFile(file, bank)
                    MacroLearnState.setStatus("Exported macro bank to ${file.name}")
                } else {
                    // Deck imports re-baseline the tracker (installBankForDeck bumps the epoch), so record the step here.
                    if (browserDeckLabel != null) MacroUndoTracker.recordBeforeBulkEdit()
                    val skipped = MacroBankSerializer.applyFileToBank(file, bankId, bank, browserDeckLabel, mixer)
                    MacroLearnState.setStatus("Imported ${file.name}" + if (skipped > 0) " ($skipped target(s) skipped: parameter not found)" else "")
                }
            } catch (e: Exception) {
                MacroLearnState.setStatus("Macro bank ${if (bankBrowser.mode == ImGuiFileBrowser.Mode.SAVE) "export" else "import"} failed: ${e.message}", 6000L)
            }
        }
    }
}
