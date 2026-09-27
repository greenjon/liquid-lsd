package llm.slop.liquidlsd.ui

import imgui.ImGui

/** Fitting text into fixed-width cells (knob strips, FX slot/param cells). */
internal object TextFit {
    const val ELLIPSIS = "…"

    /** Shortens [text] with an ellipsis until it fits [maxW] in the *current* font. */
    fun ellipsize(text: String, maxW: Float): String = ellipsize(text, maxW) { ImGui.calcTextSize(it).x }

    /** [ellipsize] with an explicit text measurer -- unit-testable without an ImGui context. */
    fun ellipsize(text: String, maxW: Float, measure: (String) -> Float): String {
        if (measure(text) <= maxW) return text
        var end = text.length
        while (end > 1 && measure(text.substring(0, end) + ELLIPSIS) > maxW) end--
        return text.substring(0, end) + ELLIPSIS
    }

    /** Y at which a line of [lineH] sits vertically centered in a box at [y], [h] tall. */
    fun centeredY(y: Float, h: Float, lineH: Float): Float = y + (h - lineH) * 0.5f
}
