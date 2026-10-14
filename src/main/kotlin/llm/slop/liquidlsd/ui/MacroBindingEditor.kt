package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import llm.slop.liquidlsd.macro.*
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Reusable editor for one [MacroBinding]: enabled lock, travel range (Min/Max), link mode / invert,
 * response curve, step count and delete. Hosted by the Properties panel ([drawFull]) and the Edit-row strip
 * ([drawLine]); both share [drawRangeBar] and the Min/Max fields.
 *
 * Callers own the header (which macro, which target); this draws only the binding's settings.
 * Edits are undone by `MacroUndoTracker`, which watches the banks each frame, not by anything here.
 */
object MacroBindingEditor {
    // Scratch buffers allocated once as fields (imgui_memory_management guidelines).
    private val enabledBuf = ImBoolean()
    private val curves = arrayOf("Linear", "Exponential", "Logarithmic", "S-Curve", "Step")

    private const val BAR_H = BindingEditorParts.BAR_H

    /**
     * Full layout for a wide host (Properties). Returns true if the user asked to delete the binding;
     * the caller removes it from [control] and calls [MacroEngine.invalidate].
     */
    fun drawFull(
        session: llm.slop.liquidlsd.SessionContext,
        control: MacroControl,
        binding: MacroBinding,
        param: ModulatableParameter?,
        width: Float
    ): Boolean {
        // Hosts that don't know the target's real travel (Properties) rely on the range its slider reported.
        val known = ranges[binding]
        return if (known != null) drawFull(session, control, binding, known.lo, known.hi, width, known.log)
        else drawFull(session, control, binding, param?.minClamp ?: 0f, param?.maxClamp ?: 1f, width)
    }

    private class TargetRange(var lo: Float, var hi: Float, var log: Boolean)
    // Identity-keyed: MacroBinding is a data class whose hashCode changes as its range is edited.
    private val ranges = java.util.IdentityHashMap<MacroBinding, TargetRange>()

    /**
     * A bound slider reports its real travel [lo]..[hi] here each frame so every editor for [binding] scales
     * to the target's limits, not to the binding's own (shrinking) min/max -- otherwise the bar rescales
     * under the handle mid-drag.
     */
    fun noteTargetRange(binding: MacroBinding, lo: Float, hi: Float, log: Boolean) {
        if (ranges.size > 512) ranges.clear()
        val r = ranges[binding]
        if (r == null) ranges[binding] = TargetRange(lo, hi, log) else { r.lo = lo; r.hi = hi; r.log = log }
    }

    private const val POPUP_W = 380f
    private val popupIds = HashMap<String, String>()

    /** Stable popup id per bound property ("macro_pop_lfoMin"); cached so the per-frame call allocates nothing. */
    fun popupIdFor(propertyName: String): String = popupIds.getOrPut(propertyName) { "macro_pop_$propertyName" }

    /**
     * The binding popup opened by clicking a bound slider's macro badge. Call every frame after the
     * badge; open it with `ImGui.openPopup(popupId)` on click. [lo]..[hi] is the target's travel range.
     */
    fun drawPopup(
        session: llm.slop.liquidlsd.SessionContext,
        popupId: String,
        info: MacroBindingInfo,
        lo: Float,
        hi: Float,
        logarithmic: Boolean = false
    ) {
        noteTargetRange(info.binding, lo, hi, logarithmic)
        if (!ImGui.beginPopup(popupId)) return
        session.uiTheme.caption("${Icons.LOCK} ${info.controlName} [${info.badgeLabel}] -> ${info.binding.propertyName.ifEmpty { "base value" }}")
        if (llm.slop.liquidlsd.macro.TransitionMacroSync.isSyncOwned(info.bankKey)) {
            // FxMacroSync / TransitionMacroSync rewrite these bindings from the model, so an edit here would be lost on the next sync.
            session.uiTheme.caption(llm.slop.liquidlsd.macro.TransitionMacroSync.ownerNote(info.bankKey))
            ImGui.endPopup()
            return
        }
        if (drawFull(session, info.control, info.binding, lo, hi, POPUP_W, logarithmic)) {
            info.control.bindings.remove(info.binding)
            MacroEngine.invalidate()
            ImGui.closeCurrentPopup()
        }
        ImGui.endPopup()
    }

    /** Same as the [ModulatableParameter] overload, with an explicit target travel range [lo]..[hi]. */
    fun drawFull(
        session: llm.slop.liquidlsd.SessionContext,
        control: MacroControl,
        binding: MacroBinding,
        lo: Float,
        hi: Float,
        width: Float,
        logarithmic: Boolean = false
    ): Boolean {
        var delete = false
        ImGui.pushID(System.identityHashCode(binding))

        // Line 1: lock, link mode, curve, steps, delete.
        enabledBuf.set(binding.enabled)
        if (ImGui.checkbox("##enabled", enabledBuf)) {
            binding.enabled = enabledBuf.get()
            MacroEngine.invalidate()
        }
        itemTooltip(if (binding.enabled) "Active: target locked to this macro. Uncheck to release it for manual control." else "Disabled: target released for manual control. Check to resume macro lock.")

        ImGui.sameLine(0f, 8f)
        drawLinkMode(control, binding)

        ImGui.sameLine(0f, 8f)
        ImGui.setNextItemWidth(100f)
        drawCurveCombo(binding)

        if (binding.curve == MacroCurveType.STEP) {
            ImGui.sameLine(0f, 6f)
            ImGui.setNextItemWidth(50f)
            drawStepCount(binding)
        }

        val btn = 20f
        ImGui.sameLine(ImGui.getCursorPosX() + maxOf(0f, ImGui.getContentRegionAvailX() - btn))
        if (ButtonChrome.button("${Icons.TRASH}##del", btn, btn)) delete = true
        itemTooltip("Delete this target.")

        // Line 2: range bar with live position dot.
        val barLo = minOf(lo, binding.minVal, binding.maxVal)
        val barHi = maxOf(hi, binding.minVal, binding.maxVal)
        // A log track needs a strictly positive range (rate-style params span ms..hours).
        val useLog = logarithmic && barLo > 0f
        drawRangeBar(binding, barLo, barHi, MacroCurve.mapToRange(control.value, binding), width, useLog)

        // Line 3: numeric Min/Max.
        val fieldW = (width - 40f) * 0.5f
        ImGui.setNextItemWidth(fieldW)
        drawMinField(binding, barLo, barHi, useLog)
        ImGui.sameLine(0f, 6f)
        ImGui.setNextItemWidth(fieldW)
        drawMaxField(binding, barLo, barHi, useLog)

        ImGui.spacing()
        ImGui.popID()
        return delete
    }

    /**
     * One-control-line layout for the Edit-row strip, drawn at screen ([x], [y]) in a [w] x [h] slot:
     * lock, target path, link/invert, curve, steps (STEP only), then a range bar (when there's room),
     * Min/Max and delete. Returns true if the user asked to delete the binding (caller removes it and
     * calls [MacroEngine.invalidate]). [targetLabel] is the full path shown ellipsized; [param] is the
     * resolved target, used for its travel range when no slider has reported one.
     */
    fun drawLine(
        control: MacroControl,
        binding: MacroBinding,
        param: ModulatableParameter?,
        targetLabel: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ): Boolean {
        var delete = false
        val known = ranges[binding]
        val lo = known?.lo ?: param?.minClamp ?: 0f
        val hi = known?.hi ?: param?.maxClamp ?: 1f
        val barLo = minOf(lo, binding.minVal, binding.maxVal)
        val barHi = maxOf(hi, binding.minVal, binding.maxVal)
        val useLog = (known?.log ?: false) && barLo > 0f

        ImGui.pushID(System.identityHashCode(binding))
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 4f, ((h - ImGui.getFontSize()) * 0.5f).coerceAtLeast(0f))
        val gap = 4f
        val fieldW = 44f

        // Right-aligned group first so the left group knows how much room it has.
        val rightW = h + gap + fieldW * 2 + gap
        val rx = x + w - rightW
        ImGui.setCursorScreenPos(rx, y)
        ImGui.setNextItemWidth(fieldW)
        drawMinField(binding, barLo, barHi, useLog, compact = true)
        ImGui.sameLine(0f, gap)
        ImGui.setNextItemWidth(fieldW)
        drawMaxField(binding, barLo, barHi, useLog, compact = true)
        ImGui.sameLine(0f, gap)
        if (ButtonChrome.button("${Icons.TRASH}##del", h, h)) delete = true
        itemTooltip("Delete this target.")

        var cx = x
        ImGui.setCursorScreenPos(cx, y)
        enabledBuf.set(binding.enabled)
        if (ImGui.checkbox("##enabled", enabledBuf)) {
            binding.enabled = enabledBuf.get()
            MacroEngine.invalidate()
        }
        itemTooltip(if (binding.enabled) "Active: target locked to this macro. Uncheck to release it for manual control." else "Disabled: target released for manual control. Check to resume macro lock.")
        cx = ImGui.getItemRectMaxX() + gap

        val pathW = (w * 0.2f).coerceAtLeast(40f)
        val shown = TextFit.ellipsize(targetLabel, pathW)
        ImGui.getWindowDrawList().addText(cx, TextFit.centeredY(y, h, ImGui.getTextLineHeight()), ImGui.getColorU32(imgui.flag.ImGuiCol.Text), shown)
        ImGui.setCursorScreenPos(cx, y)
        ImGui.dummy(pathW, h)
        itemTooltip(targetLabel)
        cx += pathW + gap

        ImGui.setCursorScreenPos(cx, y)
        LinkModeButton.drawMacroLink(
            id = "macro_line_${control.id}_${System.identityHashCode(binding)}",
            mode = binding.linkMode,
            inverted = binding.inverted,
            isLinked = binding.enabled,
            width = 28f,
            height = h,
            onCycleMode = { cycleLinkMode(binding) },
            onSelectMode = { binding.linkMode = it },
            onToggleInvert = { binding.inverted = !binding.inverted }
        )
        cx = ImGui.getItemRectMaxX() + gap

        ImGui.setCursorScreenPos(cx, y)
        ImGui.setNextItemWidth(78f)
        drawCurveCombo(binding)
        cx = ImGui.getItemRectMaxX() + gap

        if (binding.curve == MacroCurveType.STEP) {
            ImGui.setCursorScreenPos(cx, y)
            ImGui.setNextItemWidth(38f)
            drawStepCount(binding)
            cx = ImGui.getItemRectMaxX() + gap
        }

        val barW = rx - gap - cx
        if (barW >= 50f) {
            ImGui.setCursorScreenPos(cx, y + (h - BAR_H) * 0.5f)
            drawRangeBar(binding, barLo, barHi, MacroCurve.mapToRange(control.value, binding), barW, useLog)
        }

        ImGui.popStyleVar()
        ImGui.popID()
        return delete
    }

    private fun cycleLinkMode(binding: MacroBinding) {
        binding.linkMode = when (binding.linkMode) {
            MacroLinkMode.FULL -> MacroLinkMode.FIRST_HALF
            MacroLinkMode.FIRST_HALF -> MacroLinkMode.SECOND_HALF
            MacroLinkMode.SECOND_HALF -> MacroLinkMode.TRIANGLE
            MacroLinkMode.TRIANGLE -> MacroLinkMode.BIPOLAR
            MacroLinkMode.BIPOLAR -> MacroLinkMode.FULL
        }
    }

    private fun drawLinkMode(control: MacroControl, binding: MacroBinding) {
        LinkModeButton.drawMacroLink(
            id = "macro_bind_${control.id}_${System.identityHashCode(binding)}",
            mode = binding.linkMode,
            inverted = binding.inverted,
            isLinked = binding.enabled,
            onCycleMode = { cycleLinkMode(binding) },
            onSelectMode = { binding.linkMode = it },
            onToggleInvert = { binding.inverted = !binding.inverted }
        )
    }

    private fun drawCurveCombo(binding: MacroBinding) {
        BindingEditorParts.curveCombo(curves, binding.curve.ordinal)?.let {
            binding.curve = MacroCurveType.entries.getOrElse(it) { MacroCurveType.LINEAR }
        }
    }

    private fun drawStepCount(binding: MacroBinding) {
        BindingEditorParts.stepCount(binding.stepCount)?.let { binding.stepCount = it }
    }

    private fun drawMinField(binding: MacroBinding, lo: Float, hi: Float, log: Boolean, compact: Boolean = false) {
        BindingEditorParts.minField(binding.minVal, lo, hi, log, compact)?.let { binding.minVal = it }
    }

    private fun drawMaxField(binding: MacroBinding, lo: Float, hi: Float, log: Boolean, compact: Boolean = false) {
        BindingEditorParts.maxField(binding.maxVal, lo, hi, log, compact)?.let { binding.maxVal = it }
    }

    /** Track with a filled min->max segment, two draggable handles and a dot at the binding's live mapped output. */
    private fun drawRangeBar(binding: MacroBinding, lo: Float, hi: Float, live: Float, width: Float, log: Boolean, barH: Float = BAR_H) {
        BindingEditorParts.rangeBar(
            binding.minVal, binding.maxVal, lo, hi, live, width, log, binding.enabled,
            setMin = { binding.minVal = it }, setMax = { binding.maxVal = it }, barH = barH
        )
    }
}
