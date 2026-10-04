package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import llm.slop.liquidlsd.macro.*
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Reusable editor for one [MacroBinding]: enabled lock, travel range (Min/Max), link mode / invert,
 * response curve, step count and delete. Hosted by the Properties panel (and, later, the Edit-row strip).
 *
 * Callers own the header (which macro, which target); this draws only the binding's settings.
 * Edits aren't on the undo stack, same as the Binding Inspector it replaces.
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
        if (ImGui.button("${Icons.TRASH}##del", btn, btn)) delete = true
        itemTooltip("Delete this binding.")

        // Line 2: range bar with live position dot.
        val lo = minOf(param?.minClamp ?: 0f, binding.minVal, binding.maxVal)
        val hi = maxOf(param?.maxClamp ?: 1f, binding.minVal, binding.maxVal)
        drawRangeBar(binding, lo, hi, MacroCurve.mapToRange(control.value, binding), width)

        // Line 3: numeric Min/Max.
        val fieldW = (width - 40f) * 0.5f
        ImGui.setNextItemWidth(fieldW)
        drawMinField(binding)
        ImGui.sameLine(0f, 6f)
        ImGui.setNextItemWidth(fieldW)
        drawMaxField(binding)

        ImGui.spacing()
        ImGui.popID()
        return delete
    }

    private fun drawLinkMode(control: MacroControl, binding: MacroBinding) {
        LinkModeButton.drawMacroLink(
            id = "macro_bind_${control.id}_${System.identityHashCode(binding)}",
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

    private fun drawMinField(binding: MacroBinding) {
        minValBuf[0] = binding.minVal
        if (ImGui.dragFloat("Min##min", minValBuf, 0.01f, -10f, 10f, "%.2f")) binding.minVal = minValBuf[0]
        itemTooltip("Output value when the macro is at 0.0.")
    }

    private fun drawMaxField(binding: MacroBinding) {
        maxValBuf[0] = binding.maxVal
        if (ImGui.dragFloat("Max##max", maxValBuf, 0.01f, -10f, 10f, "%.2f")) binding.maxVal = maxValBuf[0]
        itemTooltip("Output value when the macro is at 1.0.")
    }

    /**
     * Track spanning [lo]..[hi] with a filled min→max segment, two draggable handles and a dot at the
     * binding's current mapped output [live].
     */
    private fun drawRangeBar(binding: MacroBinding, lo: Float, hi: Float, live: Float, width: Float) {
        val span = (hi - lo).coerceAtLeast(1e-4f)
        val usable = (width - HANDLE_W).coerceAtLeast(20f)
        val x0 = ImGui.getCursorScreenPosX() + HANDLE_W * 0.5f
        val y0 = ImGui.getCursorScreenPosY()
        val localX = ImGui.getCursorPosX()
        val localY = ImGui.getCursorPosY()
        val cy = y0 + BAR_H * 0.5f
        val dl = ImGui.getWindowDrawList()

        fun xOf(v: Float) = x0 + ((v - lo) / span).coerceIn(0f, 1f) * usable

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
            ImGui.invisibleButton(id, HANDLE_W, BAR_H)
            if (ImGui.isItemActive()) {
                val v = lo + ((ImGui.getMousePosX() - x0) / usable).coerceIn(0f, 1f) * span
                set((Math.round(v * 100f) / 100f))
            }
            if (ImGui.isItemHovered() || ImGui.isItemActive()) {
                ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeEW)
                itemTooltip("%.2f".format(value))
            }
            dl.addRectFilled(x - HANDLE_W * 0.5f, y0 + 2f, x + HANDLE_W * 0.5f, y0 + BAR_H - 2f, handleCol, 2f)
        }
        handle("##min_h", binding.minVal, xa) { binding.minVal = it }
        handle("##max_h", binding.maxVal, xb) { binding.maxVal = it }

        if (active) dl.addCircleFilled(xOf(live), cy, 3.5f, dotCol)

        // Resume layout below the bar.
        ImGui.setCursorPos(localX, localY + BAR_H + 2f)
        ImGui.dummy(width, 0f)
    }
}
