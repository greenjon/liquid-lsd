package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt

/**
 * The pieces of a binding editor that do not depend on what kind of binding it edits: the travel range bar with its two
 * draggable handles and live dot, the Min/Max fields, the curve combo and the step-count field. [MacroBindingEditor]
 * (source rows) and [FxMetaBindingEditor] (FX slots) both draw through here so the two look and behave identically.
 *
 * Everything takes plain values plus setters, so it works for the mutable `MacroBinding` and the immutable `FxMetaBinding`.
 */
object BindingEditorParts {
    // Scratch buffers allocated once as fields (imgui_memory_management guidelines).
    private val curveBuf = ImInt()
    private val minValBuf = FloatArray(1)
    private val maxValBuf = FloatArray(1)
    private val stepCountBuf = IntArray(1)

    const val BAR_H = 18f
    private const val HANDLE_W = 8f

    /** Combo of response curves. [names] are the display names in enum order; [selected] is the current ordinal. Returns the new ordinal, or null when unchanged. */
    fun curveCombo(names: Array<String>, selected: Int): Int? {
        curveBuf.set(selected)
        if (ImGui.combo("##curve", curveBuf, names)) {
            itemTooltip("Response curve shaping.")
            return curveBuf.get()
        }
        itemTooltip("Response curve shaping.")
        return null
    }

    /** Step-count drag field for the STEP curve. Returns the new count, or null when unchanged. */
    fun stepCount(count: Int): Int? {
        stepCountBuf[0] = count
        val changed = ImGui.dragInt("##steps", stepCountBuf, 1f, 2, 64)
        itemTooltip("Number of quantized steps across the travel range.")
        return if (changed) stepCountBuf[0].coerceIn(2, 64) else null
    }

    /** Width of each of a labelled Min and Max field side by side in [width], with [gap] between them, so the "Max" label isn't clipped. */
    fun minMaxFieldWidth(width: Float, gap: Float): Float {
        val labels = ImGui.calcTextSize("Min").x + ImGui.calcTextSize("Max").x + 2f * ImGui.getStyle().itemInnerSpacingX
        return ((width - gap - labels) * 0.5f).coerceAtLeast(30f)
    }

    /** Min field; returns the new value or null when unchanged. */
    fun minField(value: Float, lo: Float, hi: Float, log: Boolean, compact: Boolean, subject: String = "macro"): Float? {
        val s = if (log) 1f else ValueFormat.scaleFor(lo, hi)
        minValBuf[0] = value * s
        val changed = ImGui.dragFloat(if (compact) "##min" else "Min##min", minValBuf, dragSpeed(lo, hi, value, log) * s, lo * s, hi * s, if (s == 1f) "%.3f" else "%.1f")
        itemTooltip("Output value when the $subject is at 0.0. Ctrl+click to type a value.")
        return if (changed) minValBuf[0] / s else null
    }

    /** Max field; returns the new value or null when unchanged. */
    fun maxField(value: Float, lo: Float, hi: Float, log: Boolean, compact: Boolean, subject: String = "macro"): Float? {
        val s = if (log) 1f else ValueFormat.scaleFor(lo, hi)
        maxValBuf[0] = value * s
        val changed = ImGui.dragFloat(if (compact) "##max" else "Max##max", maxValBuf, dragSpeed(lo, hi, value, log) * s, lo * s, hi * s, if (s == 1f) "%.3f" else "%.1f")
        itemTooltip("Output value when the $subject is at 1.0. Ctrl+click to type a value.")
        return if (changed) maxValBuf[0] / s else null
    }

    /** Drag speed scaled to the target's real range (proportional to the value on a log range). */
    private fun dragSpeed(lo: Float, hi: Float, value: Float, log: Boolean): Float =
        if (log) (kotlin.math.abs(value) * 0.01f).coerceAtLeast(lo * 0.01f) else ((hi - lo) * 0.002f).coerceAtLeast(1e-5f)

    /** Rounds to ~3 significant digits so dragged handles land on tidy values at any magnitude. */
    fun roundSig(v: Float): Float {
        if (v == 0f || v.isNaN() || v.isInfinite()) return v
        val mag = Math.floor(Math.log10(kotlin.math.abs(v).toDouble())).toInt()
        val step = Math.pow(10.0, (mag - 2).toDouble())
        return (Math.round(v / step) * step).toFloat()
    }

    /**
     * Track spanning [lo]..[hi] with a filled [minVal]..[maxVal] segment, two draggable handles and a dot at the
     * binding's current mapped output [live]. [setMin] / [setMax] receive dragged handle values.
     */
    fun rangeBar(
        minVal: Float, maxVal: Float, lo: Float, hi: Float, live: Float, width: Float, log: Boolean, active: Boolean,
        setMin: (Float) -> Unit, setMax: (Float) -> Unit, barH: Float = BAR_H
    ) {
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

        val trackCol = ImGui.colorConvertFloat4ToU32(0.12f, 0.14f, 0.18f, 1f)
        val segCol = if (active) ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 0.85f, 0.8f)
                     else ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 0.6f)
        val handleCol = ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.95f, 1f)
        val dotCol = ImGui.colorConvertFloat4ToU32(1f, 0.85f, 0.2f, 1f)

        dl.addRectFilled(x0, cy - 3f, x0 + usable, cy + 3f, trackCol, 3f)
        val xa = xOf(minVal)
        val xb = xOf(maxVal)
        dl.addRectFilled(minOf(xa, xb), cy - 3f, maxOf(xa, xb), cy + 3f, segCol, 3f)

        fun handle(id: String, value: Float, x: Float, set: (Float) -> Unit) {
            ImGui.setCursorScreenPos(x - HANDLE_W * 0.5f, y0)
            ImGui.invisibleButton(id, HANDLE_W, barH)
            if (ImGui.isItemActive()) {
                set(roundSig(valueAt(((ImGui.getMousePosX() - x0) / usable).coerceIn(0f, 1f))).coerceIn(lo, hi))
            }
            if (ImGui.isItemHovered() || ImGui.isItemActive()) {
                ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeEW)
                itemTooltip(ValueFormat.format(value, if (log) 1f else ValueFormat.scaleFor(lo, hi)))
            }
            dl.addRectFilled(x - HANDLE_W * 0.5f, y0 + 2f, x + HANDLE_W * 0.5f, y0 + barH - 2f, handleCol, 2f)
        }
        handle("##min_h", minVal, xa, setMin)
        handle("##max_h", maxVal, xb, setMax)

        if (active) dl.addCircleFilled(xOf(live), cy, 3.5f, dotCol)

        // Resume layout below the bar.
        ImGui.setCursorPos(localX, localY + barH + 2f)
        ImGui.dummy(width, 0f)
    }
}
