package llm.slop.liquidlsd.ui

import imgui.ImGui

/**
 * Subtle "lit from above" bevel for real buttons (Mixxx-style): a faint top-to-bottom gradient, a 1px
 * highlight on the top edge and a 1px shade on the bottom edge. Drawn as a translucent overlay on top of
 * whatever fill the button already has, so Tango accent/active colors and both themes keep working.
 * Pressed buttons flip to an inset look. Knobs, sliders and cells stay flat on purpose, so only true
 * buttons read as pressable.
 */
object ButtonChrome {

    /**
     * Every tunable of the bevel, one set per theme. Alphas are 0..1 white/black overlays; sizes are px.
     * Change the look here only -- no call site carries its own numbers.
     */
    class Look(
        /** White overlay alpha at the top of the gradient. */
        val gradTop: Float,
        /** Black overlay alpha at the bottom of the gradient. */
        val gradBottom: Float,
        /** White alpha of the top edge line. */
        val edgeHilite: Float,
        /** Black alpha of the bottom edge line. */
        val edgeShade: Float,
        /** Thickness of both edge lines. */
        val edgeThickness: Float = 1f,
        /** Corner radius used by the bevel's edge lines; match the theme's FrameRounding. */
        val rounding: Float = 3f,
        /** Buttons smaller than this (either side) get no bevel. */
        val minSize: Float = 8f
    )

    /** Dark theme: a faint lift. */
    var dark = Look(gradTop = 0.07f, gradBottom = 0.07f, edgeHilite = 0.20f, edgeShade = 0.28f)

    /** Light theme can't brighten a light surface, so it leans on the shade side more than the highlight. */
    var light = Look(gradTop = 0.40f, gradBottom = 0.10f, edgeHilite = 0.55f, edgeShade = 0.22f)

    /** Hover lightens and held darkens the fill by this fraction (see [pushColor]). */
    var hoverShift = 0.14f

    /**
     * Pushes the Button, ButtonHovered and ButtonActive colors for a button whose resting fill is [base]
     * (packed U32): hover lightens it, held darkens it (the bevel also flips inset). Always set all three
     * together -- overriding only Button lets the theme's grey hover/accent held colors show through,
     * so a lit toggle would go grey under the cursor. Pops with `ImGui.popStyleColor(3)` (plus any
     * extra colors pushed alongside). [hoverShift] 0 keeps hover and held identical to the base.
     */
    fun pushColor(base: Int, hoverShift: Float = this.hoverShift) {
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, base)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, shift(base, hoverShift))
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, shift(base, -hoverShift))
    }

    fun pushColor(r: Float, g: Float, b: Float, a: Float, hoverShift: Float = this.hoverShift) =
        pushColor(ImGui.colorConvertFloat4ToU32(r, g, b, a), hoverShift)

    /** Mixes [col] toward white (positive [amt]) or black (negative), keeping its alpha. */
    private fun shift(col: Int, amt: Float): Int {
        val target = if (amt >= 0f) 255f else 0f
        val t = kotlin.math.abs(amt)
        fun ch(shiftBits: Int): Int {
            val v = (col shr shiftBits) and 0xFF
            return (v + (target - v) * t).toInt().coerceIn(0, 255)
        }
        return (col and (0xFF shl 24)) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Drop-in for `ImGui.button`: the same button plus the bevel. Prefer this for every opaque button. */
    fun button(label: String): Boolean = ImGui.button(label).also { afterItem() }

    fun button(label: String, width: Float, height: Float): Boolean = ImGui.button(label, width, height).also { afterItem() }

    /** Bevel the last submitted item (call right after an `ImGui.button(...)` that isn't [button]). Uses its active state for the inset look. */
    fun afterItem() {
        val min = ImGui.getItemRectMin()
        val max = ImGui.getItemRectMax()
        bevel(min.x, min.y, max.x, max.y, ImGui.isItemActive())
    }

    /** Bevel an explicit rect in the current window's draw list (for custom-drawn buttons). */
    fun bevel(x1: Float, y1: Float, x2: Float, y2: Float, pressed: Boolean = false) {
        val look = if (TangoPalette.isLightTheme) light else dark
        if (x2 - x1 < look.minSize || y2 - y1 < look.minSize) return

        val white = { a: Float -> ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, a) }
        val black = { a: Float -> ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, a) }
        val clear = 0
        val dl = ImGui.getWindowDrawList()
        val r = look.rounding
        val t = look.edgeThickness

        // Gradient overlay, inset so its square corners stay inside the rounded fill.
        val inset = r * 0.4f
        val gx1 = x1 + inset
        val gx2 = x2 - inset
        val gy1 = y1 + t
        val gy2 = y2 - t
        if (pressed) {
            val top = black(look.gradBottom)
            dl.addRectFilledMultiColor(gx1, gy1, gx2, gy2, top, top, clear, clear)
            dl.addLine(x1 + r, y2 - t, x2 - r, y2 - t, white(look.edgeHilite * 0.5f), t)
        } else {
            val top = white(look.gradTop)
            val bot = black(look.gradBottom)
            dl.addRectFilledMultiColor(gx1, gy1, gx2, gy2, top, top, bot, bot)
            dl.addLine(x1 + r, y1 + t, x2 - r, y1 + t, white(look.edgeHilite), t)
            dl.addLine(x1 + r, y2 - t, x2 - r, y2 - t, black(look.edgeShade), t)
        }
    }
}
