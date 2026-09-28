package llm.slop.liquidlsd.ui

import imgui.ImGui

/**
 * The Tango Desktop Project palette (tango.freedesktop.org/Tango_Icon_Theme_Guidelines): 8 hues x
 * (light, normal, dark), the same source Mixxx's Tango skin draws from. This is the sole source for
 * every accent and status color in the app -- see docs/developer/ui.md "Tango Palette" section --
 * so no UI element invents its own RGB literal, and each role below is assigned exactly one hue so
 * deck identity and status meaning never collide (e.g. a deck's color never doubles as "active").
 */
object TangoPalette {

    class Hue(val light: FloatArray, val normal: FloatArray, val dark: FloatArray)

    /** Two-shade accent: a base tone plus a brighter "pressed/active" variant. */
    class TwoTone(val normal: FloatArray, val bright: FloatArray)

    private fun rgb(v: Int): FloatArray = floatArrayOf(
        ((v shr 16) and 0xFF) / 255f,
        ((v shr 8) and 0xFF) / 255f,
        (v and 0xFF) / 255f
    )

    /** Float [r,g,b] plus alpha, packed to ImGui's U32 color format. */
    fun u32(hue: FloatArray, alpha: Float = 1f): Int =
        ImGui.colorConvertFloat4ToU32(hue[0], hue[1], hue[2], alpha)

    // -- The 8 canonical Tango hues -------------------------------------------------------------
    val BUTTER      = Hue(rgb(0xFCE94F), rgb(0xEDD400), rgb(0xC4A000))
    val ORANGE      = Hue(rgb(0xFCAF3E), rgb(0xF57900), rgb(0xCE5C00))
    val CHOCOLATE   = Hue(rgb(0xE9B96E), rgb(0xC17D11), rgb(0x8F5902))
    val CHAMELEON   = Hue(rgb(0x8AE234), rgb(0x73D216), rgb(0x4E9A06))
    val SKY_BLUE    = Hue(rgb(0x729FCF), rgb(0x3465A4), rgb(0x204A87))
    val PLUM        = Hue(rgb(0xAD7FA8), rgb(0x75507B), rgb(0x5C3566))
    val SCARLET_RED = Hue(rgb(0xEF2929), rgb(0xCC0000), rgb(0xA40000))
    val ALUMINIUM_1 = Hue(rgb(0xEEEEEC), rgb(0xD3D7CF), rgb(0xBABDB6))
    val ALUMINIUM_2 = Hue(rgb(0x888A85), rgb(0x555753), rgb(0x2E3436))

    /** Mixxx's own sync/link/learn accent -- not one of the 8 core hues, but its documented Tango-skin extension. */
    val SYNC_CYAN = TwoTone(rgb(0x06AFDF), rgb(0x34E2E2))

    // -- Role assignments ------------------------------------------------------------------------
    // Deck accents (see BrowserDeckButtons, PerformanceColors). DECK_BG intentionally shares its
    // hue with the ACTIVE status role below (both Chameleon green, matching Mixxx's own Deck 3):
    // Chocolate reads as near-identical to Orange (same warm/low-saturation family) so it doesn't
    // actually solve the "decks must look distinct" goal, whereas everyday overlap between "this
    // is Deck BG" and "this control is on" is judged low-risk since they're rarely ambiguous in
    // context.
    val DECK_A  = ORANGE
    val DECK_B  = SKY_BLUE
    val DECK_BG = CHAMELEON
    val DECK_PV = PLUM

    // Status roles, shared by every button/slider/indicator app-wide regardless of which deck it's on.
    val ACTIVE = CHAMELEON    // on / playing / armed / enabled
    val ALERT  = BUTTER       // unsaved / dirty / cue / caution
    val DANGER = SCARLET_RED  // bypassed / error / clipping
    val SYNC   = SYNC_CYAN    // MIDI learn / Ableton Link / focus mode

    // Neutrals
    val NEUTRAL_LIGHT = ALUMINIUM_1  // text, master/neutral accent
    val NEUTRAL_DARK  = ALUMINIUM_2  // panels, disabled text, idle button surfaces

    // -- Ink pairing -----------------------------------------------------------------------------
    // Mixxx's Tango skin never outlines button labels -- it pairs each background shade with dark
    // or light ink chosen for contrast against that specific shade. The app's global default Text
    // color (see UIThemeStyler) is Aluminium-1, an off-white tuned for the default dark Button
    // surface; any call site that pushes a lighter ImGuiCol.Button/Hovered/Active (e.g. ALERT's
    // Butter shades) without also pushing Text inherits that off-white and reads poorly. Use
    // inkFor() whenever a button's background is switched away from the default dark surface.
    val INK_DARK  = floatArrayOf(0.08f, 0.08f, 0.08f)
    val INK_LIGHT = floatArrayOf(0.97f, 0.97f, 0.97f)

    private fun luminance(rgb: FloatArray): Float =
        0.299f * rgb[0] + 0.587f * rgb[1] + 0.114f * rgb[2]

    /**
     * Ink that stays legible against the lightest of the given background shades (pass every
     * Button/ButtonHovered/ButtonActive shade a widget cycles through -- Text can't itself react
     * to hover, so it must be safe for the brightest state the button can show).
     */
    fun inkFor(vararg backgrounds: FloatArray): FloatArray =
        if (backgrounds.maxOf { luminance(it) } > 0.55f) INK_DARK else INK_LIGHT
}
