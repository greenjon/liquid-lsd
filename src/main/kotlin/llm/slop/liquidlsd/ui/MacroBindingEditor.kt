package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
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
    private val curveBuf = ImInt()
    private val minValBuf = FloatArray(1)
    private val maxValBuf = FloatArray(1)
    private val stepCountBuf = IntArray(1)
    private val curves = arrayOf("Linear", "Exponential", "Logarithmic", "S-Curve", "Step")

    private const val BAR_H = 18f
    private const val HANDLE_W = 8f

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
        curveBuf.set(binding.curve.ordinal)
        if (ImGui.combo("##curve", curveBuf, curves)) {
            binding.curve = MacroCurveType.entries.getOrElse(curveBuf.get()) { MacroCurveType.LINEAR }
        }
        itemTooltip("Response curve shaping.")
    }

    private fun drawStepCount(binding: MacroBinding) {
        stepCountBuf[0] = binding.stepCount
        if (ImGui.dragInt("##steps", stepCountBuf, 1f, 2, 64)) {
            binding.stepCount = stepCountBuf[0].coerceIn(2, 64)
        }
        itemTooltip("Number of quantized steps across the travel range.")
    }

    private fun drawMinField(binding: MacroBinding, lo: Float, hi: Float, log: Boolean, compact: Boolean = false) {
        minValBuf[0] = binding.minVal
        if (ImGui.dragFloat(if (compact) "##min" else "Min##min", minValBuf, dragSpeed(lo, hi, binding.minVal, log), lo, hi, "%.3f")) binding.minVal = minValBuf[0]
        itemTooltip("Output value when the macro is at 0.0. Ctrl+click to type a value.")
    }

    private fun drawMaxField(binding: MacroBinding, lo: Float, hi: Float, log: Boolean, compact: Boolean = false) {
        maxValBuf[0] = binding.maxVal
        if (ImGui.dragFloat(if (compact) "##max" else "Max##max", maxValBuf, dragSpeed(lo, hi, binding.maxVal, log), lo, hi, "%.3f")) binding.maxVal = maxValBuf[0]
        itemTooltip("Output value when the macro is at 1.0. Ctrl+click to type a value.")
    }

    /** Drag speed scaled to the target's real range (proportional to the value on a log range). */
    private fun dragSpeed(lo: Float, hi: Float, value: Float, log: Boolean): Float =
        if (log) (kotlin.math.abs(value) * 0.01f).coerceAtLeast(lo * 0.01f) else ((hi - lo) * 0.002f).coerceAtLeast(1e-5f)

    /** Rounds to ~3 significant digits so dragged handles land on tidy values at any magnitude. */
    private fun roundSig(v: Float): Float {
        if (v == 0f || v.isNaN() || v.isInfinite()) return v
        val mag = Math.floor(Math.log10(kotlin.math.abs(v).toDouble())).toInt()
        val step = Math.pow(10.0, (mag - 2).toDouble())
        return (Math.round(v / step) * step).toFloat()
    }

    /**
     * Track spanning [lo]..[hi] with a filled min→max segment, two draggable handles and a dot at the
     * binding's current mapped output [live].
     */
    private fun drawRangeBar(binding: MacroBinding, lo: Float, hi: Float, live: Float, width: Float, log: Boolean, barH: Float = BAR_H) {
        val span = (hi - lo).coerceAtLeast(1e-6f)
        val logLo = if (log) Math.log10(lo.toDouble()) else 0.0
        val logSpan = if (log) (Math.log10(hi.toDouble()) - logLo).coerceAtLeast(1e-6) else 1.0
        fun pctOf(v: Float): Float = if (log) ((Math.log10(v.toDouble().coerceAtLeast(lo.toDouble())) - logLo) / logSpan).toFloat() else (v - lo) / span
        fun valueAt(p: Float): Float = if (log) Math.pow(10.0, logLo + p * logSpan).toFloat() else lo + p * span
        val usable = (width - HANDLE_W).coerceAtLeast(20f)
        val x0 = ImGui.getCursorScreenPosX() + HANDLE_W * 0.5f
        val y0 = ImGui.getCursorScreenPosY()
        val localX = ImGui.getCursorPosX()
        val localY = ImGui.getCursorPosY()
        val cy = y0 + barH * 0.5f
        val dl = ImGui.getWindowDrawList()

        fun xOf(v: Float) = x0 + pctOf(v).coerceIn(0f, 1f) * usable

        val active = binding.enabled
        val trackCol = ImGui.colorConvertFloat4ToU32(0.12f, 0.14f, 0.18f, 1f)
        val segCol = if (active) ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.85f, 0.8f)
                     else ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.6f)
        val handleCol = ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.95f, 1f)
        val dotCol = ImGui.colorConvertFloat4ToU32(1f, 0.85f, 0.2f, 1f)

        dl.addRectFilled(x0, cy - 3f, x0 + usable, cy + 3f, trackCol, 3f)
        val xa = xOf(binding.minVal)
        val xb = xOf(binding.maxVal)
        dl.addRectFilled(minOf(xa, xb), cy - 3f, maxOf(xa, xb), cy + 3f, segCol, 3f)

        fun handle(id: String, value: Float, x: Float, set: (Float) -> Unit) {
            ImGui.setCursorScreenPos(x - HANDLE_W * 0.5f, y0)
            ImGui.invisibleButton(id, HANDLE_W, barH)
            if (ImGui.isItemActive()) {
                set(roundSig(valueAt(((ImGui.getMousePosX() - x0) / usable).coerceIn(0f, 1f))).coerceIn(lo, hi))
            }
            if (ImGui.isItemHovered() || ImGui.isItemActive()) {
                ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeEW)
                itemTooltip("%.3f".format(value))
            }
            dl.addRectFilled(x - HANDLE_W * 0.5f, y0 + 2f, x + HANDLE_W * 0.5f, y0 + barH - 2f, handleCol, 2f)
        }
        handle("##min_h", binding.minVal, xa) { binding.minVal = it }
        handle("##max_h", binding.maxVal, xb) { binding.maxVal = it }

        if (active) dl.addCircleFilled(xOf(live), cy, 3.5f, dotCol)

        // Resume layout below the bar.
        ImGui.setCursorPos(localX, localY + barH + 2f)
        ImGui.dummy(width, 0f)
    }
}
