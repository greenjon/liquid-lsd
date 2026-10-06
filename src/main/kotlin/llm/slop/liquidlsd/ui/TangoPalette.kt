package llm.slop.liquidlsd.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiCol

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

    /**
     * Draws the amber "armed" border shared by every OSC-Learn-capable control, so the pulse
     * rate and color can't drift between call sites the way they did when each one inlined its
     * own `sin()` copy.
     */
    fun drawOscLearnPulseBorder(dl: ImDrawList, x1: Float, y1: Float, x2: Float, y2: Float, rounding: Float = 3f, thickness: Float = 1.5f) {
        val pulseAlpha = (kotlin.math.sin(System.currentTimeMillis() * 0.008) * 0.35 + 0.65).toFloat()
        dl.addRect(x1, y1, x2, y2, u32(ALERT.normal, pulseAlpha), rounding, 0, thickness)
    }

    /**
     * Slow (~0.5 Hz) pulsing thick frame marking "this is the thing being edited". Deliberately slower
     * and thicker than [drawOscLearnPulseBorder] so the two can't be confused. [col] is an opaque u32.
     */
    fun drawEditingPulseFrame(dl: ImDrawList, x1: Float, y1: Float, x2: Float, y2: Float, col: Int) {
        val t = (kotlin.math.sin(llm.slop.liquidlsd.utils.TimeSource.getTimeSec() * 3.0) * 0.5 + 0.5).toFloat()
        val a = 0.35f + 0.65f * t
        val rgb = col and 0x00FFFFFF
        val argb = rgb or ((a * 255f).toInt().coerceIn(0, 255) shl 24)
        dl.addRect(x1, y1, x2, y2, argb, 0f, 0, 3f)
    }

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

    // Perform-row half tags (the caption strip under a row's letter badge), drawn with white text: SRC reads
    // neutral slate, FX reads cyan (the hue the FX slot pills and focus mode use, deepened so white is legible on it).
    val TAG_SRC = ALUMINIUM_2.normal
    val TAG_FX  = floatArrayOf(SYNC_CYAN.normal[0] * 0.65f, SYNC_CYAN.normal[1] * 0.65f, SYNC_CYAN.normal[2] * 0.65f)

    // Neutrals
    val NEUTRAL_LIGHT = ALUMINIUM_1  // text, master/neutral accent
    val NEUTRAL_DARK  = ALUMINIUM_2  // panels, disabled text, idle button surfaces

    // -- Semantic UI roles (theme-aware) ---------------------------------------------------------
    /**
     * True while the light theme (ORANGE_SUNSHINE) is active. Set by [UIThemeStyler.setupThemeColors]
     * whenever the theme is applied, so call sites resolve a [Role] without testing the theme
     * themselves.
     */
    @JvmStatic var isLightTheme: Boolean = false

    /**
     * A named surface/ink color with one value per theme. [dark] is RGBA for the dark theme. The
     * light theme uses [light] RGBA when given, else the live ImGui style color in [lightSlot] (so
     * light surfaces follow the styled theme), else falls back to [dark]. Must define exactly one of
     * [light] / [lightSlot] or neither (theme-independent role).
     */
    class Role(val dark: FloatArray, val light: FloatArray? = null, val lightSlot: Int = -1) {
        fun u32(): Int {
            if (isLightTheme) {
                if (light != null) return ImGui.colorConvertFloat4ToU32(light[0], light[1], light[2], light[3])
                if (lightSlot >= 0) return ImGui.getColorU32(lightSlot)
            }
            return ImGui.colorConvertFloat4ToU32(dark[0], dark[1], dark[2], dark[3])
        }
    }

    private fun a(h: FloatArray, alpha: Float = 1f) = floatArrayOf(h[0], h[1], h[2], alpha)
    private fun c(r: Float, g: Float, b: Float, alpha: Float = 1f) = floatArrayOf(r, g, b, alpha)

    // Badges (generator / master readout badges)
    val BADGE_BG           = Role(c(0.14f, 0.16f, 0.20f, 0.85f), lightSlot = ImGuiCol.FrameBg)
    val BADGE_BORDER       = Role(c(0.35f, 0.40f, 0.50f, 0.70f), lightSlot = ImGuiCol.Border)
    val BADGE_TEXT         = Role(c(0.80f, 0.85f, 0.95f, 1f), lightSlot = ImGuiCol.Text)
    val BADGE_HOVER_BORDER = Role(c(0.60f, 0.70f, 0.90f, 1f), light = a(ORANGE.normal))
    /** Flat dark readout cells (queue counters etc.): same look in both themes. */
    val CELL_BG            = Role(c(0.14f, 0.16f, 0.20f, 0.85f))
    val CELL_TEXT          = Role(c(0.80f, 0.85f, 0.95f, 1f))
    /** Near-black pill behind a coloured outline (BPM readout, snapshot badges). */
    val PILL_BG            = Role(c(0.08f, 0.08f, 0.08f, 0.80f))

    // Buttons
    val BUTTON_BG          = Role(c(0.16f, 0.18f, 0.22f, 1f), lightSlot = ImGuiCol.Button)
    val BUTTON_HOVER       = Role(c(0.24f, 0.28f, 0.35f, 1f), lightSlot = ImGuiCol.ButtonHovered)
    val BUTTON_SOFT_BG     = Role(c(0.18f, 0.20f, 0.24f, 0.80f), lightSlot = ImGuiCol.Button)
    val SPEED_BG           = Role(c(0.14f, 0.16f, 0.20f, 0.90f))
    val SPEED_HOVER        = Role(c(0.20f, 0.24f, 0.32f, 1f))
    val CLOCK_IDLE_BG      = Role(c(0.14f, 0.16f, 0.20f, 0.70f))
    val PREVIEW_BG         = Role(c(0.12f, 0.22f, 0.18f, 0.85f), lightSlot = ImGuiCol.Button)
    val PREVIEW_HOVER      = Role(c(0.18f, 0.32f, 0.25f, 1f), light = a(PLUM.light))
    val EJECT_HOVER        = Role(c(0.45f, 0.20f, 0.20f, 1f), light = a(DANGER.light))
    val RANDOM_BG          = Role(c(0.20f, 0.16f, 0.24f, 0.90f), lightSlot = ImGuiCol.Button)
    val RANDOM_HOVER       = Role(c(0.35f, 0.22f, 0.42f, 1f), light = a(PLUM.light))
    /** Selected tab/button in blue (clock source, TAP idle). */
    val ACTIVE_BLUE        = SKY_BLUE.normal
    val TAP_FLASH          = ORANGE.light
    val TAP_COUNTING       = CHOCOLATE.normal
    val AUTOFADE_ACTIVE    = ORANGE.normal
    val AUTOFADE_HOVER     = ORANGE.light
    val LINK_NO_PEERS      = CHOCOLATE.normal

    // Library mode toggle + panel surfaces
    val MODE_ACTIVE        = Role(c(0.25f, 0.45f, 0.75f, 0.80f), light = a(SYNC.normal))
    val MODE_INACTIVE      = Role(c(0.18f, 0.18f, 0.18f, 0.80f), lightSlot = ImGuiCol.Button)
    val MODE_ACTIVE_TEXT   = Role(c(1f, 1f, 1f, 1f), light = c(0.05f, 0.05f, 0.05f, 1f))
    val PANEL_BG           = Role(c(0.10f, 0.10f, 0.12f, 0.60f), lightSlot = ImGuiCol.ChildBg)
    val PANEL_BORDER       = Role(c(0.25f, 0.25f, 0.28f, 0.80f), lightSlot = ImGuiCol.Border)

    // Pill toggle outline
    val PILL_BORDER        = Role(c(0.25f, 0.28f, 0.35f, 0.50f))
    val PILL_BORDER_HOVER  = Role(c(1f, 1f, 1f, 0.40f))

    // Beat dots (4-beat bar phase)
    val BEAT_DOWNBEAT      = Role(c(0.2f, 0.95f, 1f, 1f))
    val BEAT_DOT           = Role(c(0.9f, 0.95f, 0.4f, 1f))
    val BEAT_IDLE_RING     = Role(c(0.35f, 0.35f, 0.40f, 0.6f))

    // Crossfader track / handle (PerformanceTransitionsControls)
    val XF_LINE            = Role(c(0.15f, 0.15f, 0.15f, 1f))
    val XF_MARK_FAINT      = Role(c(0.65f, 0.65f, 0.65f, 0.28f))
    val XF_MARK_CENTER     = Role(c(0.85f, 0.85f, 0.85f, 0.45f))
    val XF_MARK_ENDS       = Role(c(0.70f, 0.70f, 0.70f, 0.35f))
    val XF_HANDLE_IDLE     = Role(c(0.5f, 0.5f, 0.5f, 1f))
    val XF_HANDLE_ACTIVE   = Role(c(0.8f, 0.8f, 0.8f, 1f))
    val XF_HANDLE_BORDER   = Role(c(0.1f, 0.1f, 0.1f, 1f))
    /** Hovered-track border and live (modulated / auto-fading) position dot: ALERT butter. */
    val XF_HOVER_BORDER    = Role(a(ALERT.normal, 0.9f))
    val XF_LIVE_DOT        = Role(a(ALERT.normal))

    // Macro-knob inline Learn / Cancel buttons (PerformanceMatrixPanel)
    val LEARN_BTN_BG       = Role(c(0.18f, 0.38f, 0.24f, 0.85f))
    val CANCEL_BTN_BG      = Role(a(DANGER.light, 0.85f))

    // FX chain header (slot pills, focused-effect name, save button)
    val FX_PILL_ON         = Role(a(SYNC.normal, 0.95f), light = a(ORANGE.normal))
    val FX_PILL_FILLED     = Role(c(0.20f, 0.22f, 0.26f, 0.9f), lightSlot = ImGuiCol.Button)
    val FX_PILL_EMPTY      = Role(c(0.14f, 0.15f, 0.18f, 0.6f), light = c(0.95f, 0.96f, 0.97f, 1f))
    val FX_PILL_TEXT       = Role(c(0.90f, 0.92f, 0.95f, 1f), lightSlot = ImGuiCol.Text)
    val FX_PILL_TEXT_EMPTY = Role(c(0.55f, 0.58f, 0.62f, 1f), lightSlot = ImGuiCol.TextDisabled)
    val FX_PAGE_TEXT       = Role(c(0.9f, 0.9f, 0.95f, 1f))
    val FX_SAVE_BG         = Role(c(0.18f, 0.20f, 0.24f, 0.8f), lightSlot = ImGuiCol.Button)
    val FX_SAVE_INK        = Role(c(0.97f, 0.97f, 0.97f, 1f), light = c(0.06f, 0.07f, 0.08f, 1f))
    /** Unsaved-chain name ink: ALERT butter, darker on the light theme for contrast. */
    val FX_DIRTY_TEXT      = Role(a(ALERT.light), light = a(ALERT.dark))
    val WHITE              = Role(c(1f, 1f, 1f, 1f))

    // FX slot / param cells (flat dark cells, same in both themes)
    /** RGB of the cell backdrop; alpha varies with filled/empty so it is applied by the caller via [u32]. */
    val FX_CELL_BG_RGB     = floatArrayOf(0.08f, 0.09f, 0.11f)
    val FX_PLACEHOLDER     = Role(c(0.40f, 0.42f, 0.48f, 0.6f))
    val FX_PARAM_NAME      = Role(c(0.85f, 0.88f, 0.92f, 0.95f))
    val FX_SLOT_NAME       = Role(c(0.85f, 0.85f, 0.88f, 0.95f))
    val FX_SLOT_NAME_EMPTY = Role(c(0.5f, 0.5f, 0.55f, 0.8f))
    val FX_SLOT_NAME_OFF   = Role(c(0.55f, 0.55f, 0.6f, 0.75f))
    val FX_ARROW_IDLE      = Role(c(0.6f, 0.62f, 0.68f, 0.85f))
    val FX_BTN_IDLE_BG     = Role(c(0.14f, 0.16f, 0.20f, 0.35f))
    val FX_BTN_IDLE_HOVER  = Role(c(0.20f, 0.22f, 0.27f, 0.6f))
    val FX_BTN_IDLE_TEXT   = Role(c(0.5f, 0.52f, 0.58f, 0.7f))
    val FX_BTN_EMPTY_TEXT  = Role(c(0.4f, 0.4f, 0.45f, 0.5f))
    val FX_RESET_BG        = Role(c(0.45f, 0.35f, 0.10f, 0.75f))
    val FX_RESET_HOVER     = Role(c(0.60f, 0.47f, 0.14f, 0.9f))
    val FX_RESET_TEXT      = Role(c(0.95f, 0.85f, 0.55f, 1f))
    val FX_LINK_IDLE_BG    = Role(c(0.14f, 0.16f, 0.20f, 0.50f))
    val FX_LINK_IDLE_HOVER = Role(c(0.22f, 0.25f, 0.32f, 0.80f))
    val FX_LINK_IDLE_TEXT  = Role(c(0.55f, 0.58f, 0.65f, 0.80f))

    /** [rgb] scaled by [k] (dimmed accent) with [alpha], packed to U32. */
    fun u32Scaled(rgb: FloatArray, k: Float, alpha: Float): Int =
        ImGui.colorConvertFloat4ToU32(rgb[0] * k, rgb[1] * k, rgb[2] * k, alpha)

    /** Border drawn around any control armed for MIDI learn (SYNC cyan). */
    fun learnBorder(): Int = u32(SYNC.normal)

    /** Every [Role], for the unit test that checks they resolve for both themes. */
    val ALL_ROLES: List<Role> by lazy {
        listOf(BADGE_BG, BADGE_BORDER, BADGE_TEXT, BADGE_HOVER_BORDER, CELL_BG, CELL_TEXT, PILL_BG,
            BUTTON_BG, BUTTON_HOVER, BUTTON_SOFT_BG, SPEED_BG, SPEED_HOVER, CLOCK_IDLE_BG, PREVIEW_BG,
            PREVIEW_HOVER, EJECT_HOVER, RANDOM_BG, RANDOM_HOVER, MODE_ACTIVE, MODE_INACTIVE,
            MODE_ACTIVE_TEXT, PANEL_BG, PANEL_BORDER, PILL_BORDER, PILL_BORDER_HOVER, BEAT_DOWNBEAT,
            BEAT_DOT, BEAT_IDLE_RING, XF_LINE, XF_MARK_FAINT, XF_MARK_CENTER, XF_MARK_ENDS, XF_HANDLE_IDLE,
            XF_HANDLE_ACTIVE, XF_HANDLE_BORDER, XF_HOVER_BORDER, XF_LIVE_DOT, LEARN_BTN_BG, CANCEL_BTN_BG,
            FX_PILL_ON, FX_PILL_FILLED, FX_PILL_EMPTY, FX_PILL_TEXT, FX_PILL_TEXT_EMPTY, FX_PAGE_TEXT,
            FX_SAVE_BG, FX_SAVE_INK, FX_DIRTY_TEXT, WHITE, FX_PLACEHOLDER, FX_PARAM_NAME, FX_SLOT_NAME, FX_SLOT_NAME_EMPTY,
            FX_SLOT_NAME_OFF, FX_ARROW_IDLE, FX_BTN_IDLE_BG, FX_BTN_IDLE_HOVER, FX_BTN_IDLE_TEXT,
            FX_BTN_EMPTY_TEXT, FX_RESET_BG, FX_RESET_HOVER, FX_RESET_TEXT, FX_LINK_IDLE_BG, FX_LINK_IDLE_HOVER,
            FX_LINK_IDLE_TEXT)
    }

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
