package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import llm.slop.liquidlsd.rendering.isf.FxMetaBinding
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.MetaCurve
import llm.slop.liquidlsd.rendering.isf.MetaLinkMode

/**
 * Editor for one [FxMetaBinding] of an FX slot's Metaknob: enabled, link mode / invert, response curve, step count,
 * travel range (Min/Max) and delete. The counterpart of [MacroBindingEditor] for source rows; both draw through
 * [BindingEditorParts] so they look and behave the same.
 *
 * [FxMetaBinding] is immutable, so every change is a [FxMetaBinding.copy] handed to [edit] -- the host's
 * [FxMetaEdit.apply] wrapper, which supplies undo and the knob re-sync. [edit] receives a gesture key so a drag is one undo step.
 */
object FxMetaBindingEditor {
    private val enabledBuf = ImBoolean()
    private val curves = arrayOf("Linear", "Exponential", "Logarithmic", "S-Curve", "Step")
    private const val BAR_H = BindingEditorParts.BAR_H

    /** Host callback: runs a change on the slot with a gesture key (see [FxMetaEdit.apply]). */
    fun interface Edit {
        fun run(gesture: Any?, change: (ISFFilter) -> Unit)
    }

    private fun travel(fx: ISFFilter, binding: FxMetaBinding): Pair<Float, Float> {
        val p = binding.targetParamName?.let { fx.parameters[it] }
        val lo = minOf(p?.minClamp ?: 0f, binding.minVal, binding.maxVal)
        val hi = maxOf(p?.maxClamp ?: 1f, binding.minVal, binding.maxVal)
        return lo to hi
    }

    private fun gestureKey(fx: ISFFilter, binding: FxMetaBinding): Any = "${System.identityHashCode(fx)}/${binding.targetParamName}"

    private fun update(edit: Edit, binding: FxMetaBinding, gesture: Any?, change: (FxMetaBinding) -> FxMetaBinding) {
        edit.run(gesture) { FxMetaEdit.replace(it, change(binding)) }
    }

    private fun remove(edit: Edit, binding: FxMetaBinding) {
        edit.run(null) { FxMetaEdit.remove(it, binding.targetParamName) }
    }

    private fun cycleLinkMode(m: MetaLinkMode): MetaLinkMode = when (m) {
        MetaLinkMode.FULL -> MetaLinkMode.FIRST_HALF
        MetaLinkMode.FIRST_HALF -> MetaLinkMode.SECOND_HALF
        MetaLinkMode.SECOND_HALF -> MetaLinkMode.TRIANGLE
        MetaLinkMode.TRIANGLE -> MetaLinkMode.BIPOLAR
        MetaLinkMode.BIPOLAR -> MetaLinkMode.FULL
    }

    private fun drawLinkMode(fx: ISFFilter, binding: FxMetaBinding, edit: Edit, width: Float = 28f, height: Float = 18f) {
        LinkModeButton.drawMetaLink(
            id = "fxmeta_link_${System.identityHashCode(fx)}_${binding.targetParamName}",
            mode = binding.linkMode,
            inverted = binding.invert,
            width = width,
            height = height,
            onCycleMode = { update(edit, binding, null) { it.copy(linkMode = cycleLinkMode(it.linkMode)) } },
            // "Unlinked" in the shared menu means: drop this target.
            onSelectMode = { m -> if (m == null) remove(edit, binding) else update(edit, binding, null) { it.copy(linkMode = m) } },
            onToggleInvert = { update(edit, binding, null) { it.copy(invert = !it.invert) } }
        )
    }

    private fun drawEnabled(fx: ISFFilter, binding: FxMetaBinding, edit: Edit) {
        enabledBuf.set(binding.enabled)
        if (ImGui.checkbox("##enabled", enabledBuf)) update(edit, binding, null) { it.copy(enabled = enabledBuf.get()) }
        itemTooltip(if (binding.enabled) "Active: the Metaknob drives this parameter. Uncheck to release it for manual control." else "Disabled: parameter released for manual control. Check to resume the Metaknob link.")
    }

    private fun drawCurve(binding: FxMetaBinding, edit: Edit) {
        BindingEditorParts.curveCombo(curves, binding.curve.ordinal)?.let { i ->
            update(edit, binding, null) { it.copy(curve = MetaCurve.entries.getOrElse(i) { MetaCurve.LINEAR }) }
        }
    }

    private fun drawStepCount(binding: FxMetaBinding, edit: Edit, gesture: Any?) {
        BindingEditorParts.stepCount(binding.stepCount)?.let { n -> update(edit, binding, gesture) { it.copy(stepCount = n) } }
    }

    private fun drawMin(binding: FxMetaBinding, lo: Float, hi: Float, edit: Edit, gesture: Any?, compact: Boolean) {
        BindingEditorParts.minField(binding.minVal, lo, hi, false, compact, subject = "Metaknob")?.let { v -> update(edit, binding, gesture) { it.copy(minVal = v) } }
    }

    private fun drawMax(binding: FxMetaBinding, lo: Float, hi: Float, edit: Edit, gesture: Any?, compact: Boolean) {
        BindingEditorParts.maxField(binding.maxVal, lo, hi, false, compact, subject = "Metaknob")?.let { v -> update(edit, binding, gesture) { it.copy(maxVal = v) } }
    }

    private fun drawBar(fx: ISFFilter, binding: FxMetaBinding, lo: Float, hi: Float, width: Float, edit: Edit, gesture: Any?) {
        BindingEditorParts.rangeBar(
            binding.minVal, binding.maxVal, lo, hi, binding.mapKnobToTarget(fx.metaKnob.baseValue), width, false, binding.enabled,
            setMin = { v -> update(edit, binding, gesture) { it.copy(minVal = v) } },
            setMax = { v -> update(edit, binding, gesture) { it.copy(maxVal = v) } }
        )
    }

    /** Full three-line layout for a wide host (the Edit bay). Cursor-relative; [width] is the usable width. */
    fun drawFull(fx: ISFFilter, binding: FxMetaBinding, width: Float, edit: Edit) {
        FxMetaEdit.endGestureIfMouseUp(ImGui.isMouseDown(0))
        val gesture = gestureKey(fx, binding)
        val (lo, hi) = travel(fx, binding)
        ImGui.pushID("${System.identityHashCode(fx)}_${binding.targetParamName}")

        drawEnabled(fx, binding, edit)
        ImGui.sameLine(0f, 8f)
        drawLinkMode(fx, binding, edit)
        ImGui.sameLine(0f, 8f)
        ImGui.setNextItemWidth(100f)
        drawCurve(binding, edit)
        if (binding.curve == MetaCurve.STEP) {
            ImGui.sameLine(0f, 6f)
            ImGui.setNextItemWidth(50f)
            drawStepCount(binding, edit, gesture)
        }
        val btn = 20f
        ImGui.sameLine(ImGui.getCursorPosX() + maxOf(0f, ImGui.getContentRegionAvailX() - btn))
        if (ButtonChrome.button("${Icons.TRASH}##del", btn, btn)) remove(edit, binding)
        itemTooltip("Remove this link.")

        drawBar(fx, binding, lo, hi, width, edit, gesture)

        val fieldW = BindingEditorParts.minMaxFieldWidth(width, 6f)
        ImGui.setNextItemWidth(fieldW)
        drawMin(binding, lo, hi, edit, gesture, compact = false)
        ImGui.sameLine(0f, 6f)
        ImGui.setNextItemWidth(fieldW)
        drawMax(binding, lo, hi, edit, gesture, compact = false)

        ImGui.spacing()
        ImGui.popID()
    }

    /**
     * One-control-line layout for the Edit-row strip, drawn at screen ([x], [y]) in a [w] x [h] slot: enabled, target,
     * link/invert, curve, steps (STEP only), a range bar when there is room, Min/Max and delete.
     */
    fun drawLine(fx: ISFFilter, binding: FxMetaBinding, targetLabel: String, x: Float, y: Float, w: Float, h: Float, edit: Edit) {
        FxMetaEdit.endGestureIfMouseUp(ImGui.isMouseDown(0))
        val gesture = gestureKey(fx, binding)
        val (lo, hi) = travel(fx, binding)

        ImGui.pushID("${System.identityHashCode(fx)}_${binding.targetParamName}")
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 4f, ((h - ImGui.getFontSize()) * 0.5f).coerceAtLeast(0f))
        val gap = 4f
        val fieldW = 44f

        // Right-aligned group first so the left group knows how much room it has.
        val rightW = h + gap + fieldW * 2 + gap
        val rx = x + w - rightW
        ImGui.setCursorScreenPos(rx, y)
        ImGui.setNextItemWidth(fieldW)
        drawMin(binding, lo, hi, edit, gesture, compact = true)
        ImGui.sameLine(0f, gap)
        ImGui.setNextItemWidth(fieldW)
        drawMax(binding, lo, hi, edit, gesture, compact = true)
        ImGui.sameLine(0f, gap)
        if (ButtonChrome.button("${Icons.TRASH}##del", h, h)) remove(edit, binding)
        itemTooltip("Remove this link.")

        var cx = x
        ImGui.setCursorScreenPos(cx, y)
        drawEnabled(fx, binding, edit)
        cx = ImGui.getItemRectMaxX() + gap

        val pathW = (w * 0.2f).coerceAtLeast(40f)
        val shown = TextFit.ellipsize(targetLabel, pathW)
        ImGui.getWindowDrawList().addText(cx, TextFit.centeredY(y, h, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.Text), shown)
        ImGui.setCursorScreenPos(cx, y)
        ImGui.dummy(pathW, h)
        itemTooltip(targetLabel)
        cx += pathW + gap

        ImGui.setCursorScreenPos(cx, y)
        drawLinkMode(fx, binding, edit, width = 28f, height = h)
        cx = ImGui.getItemRectMaxX() + gap

        ImGui.setCursorScreenPos(cx, y)
        ImGui.setNextItemWidth(78f)
        drawCurve(binding, edit)
        cx = ImGui.getItemRectMaxX() + gap

        if (binding.curve == MetaCurve.STEP) {
            ImGui.setCursorScreenPos(cx, y)
            ImGui.setNextItemWidth(38f)
            drawStepCount(binding, edit, gesture)
            cx = ImGui.getItemRectMaxX() + gap
        }

        val barW = rx - gap - cx
        if (barW >= 50f) {
            ImGui.setCursorScreenPos(cx, y + (h - BAR_H) * 0.5f)
            drawBar(fx, binding, lo, hi, barW, edit, gesture)
        }

        ImGui.popStyleVar()
        ImGui.popID()
    }

    /** [Edit] for hosts that know the FX bank id and mixer (the Edit-row strip) instead of a chain prefix. */
    fun editFor(state: ParametersState, bankId: String, mixer: llm.slop.liquidlsd.rendering.Mixer, fx: ISFFilter): Edit =
        Edit { gesture, change ->
            FxMetaEdit.apply(
                fx,
                pushUndo = { restore -> state.pushUndoState(ParametersUndoSnapshot(emptyMap(), restore)) },
                resync = { llm.slop.liquidlsd.macro.FxMacroSync.syncFor(bankId, mixer) },
                gesture = gesture,
                change = change
            )
        }

    /** Builds the [Edit] callback for the Parameters-undo / FX-bank wiring of [FxMetaEdit.apply]. */
    fun editFor(state: ParametersState, chainPrefix: String, chain: llm.slop.liquidlsd.rendering.FxChain, fx: ISFFilter): Edit =
        Edit { gesture, change -> FxMetaEdit.apply(state, chainPrefix, chain, fx, gesture, change) }
}
