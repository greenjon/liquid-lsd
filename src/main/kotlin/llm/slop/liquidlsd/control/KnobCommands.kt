package llm.slop.liquidlsd.control

/**
 * The `knob.<n>` command family and the press gestures built on it:
 *  - `knob.<n>` turns knob n. Turning while its switch is held is a fine adjustment.
 *  - `knob.<n>.press` is the switch: a tap (released without turning) runs the knob's primary action.
 *  - `knob.<n>.press_alt` is the same switch with shift held: a tap runs the secondary action.
 * While a browse context is active, knob 16 browses and the others are inert, except knobs 1-4 while the
 * browsed row is on screen ([NavSurface.browseRowLive]) and the tap of a send knob ([SendTarget]) in the Library.
 * Hold state lives here, not in the device, because the Twister sends identical turn messages whether
 * or not its switch is down. One instance serves all devices; the switch is expected to be held on
 * one device at a time.
 */
class KnobCommands(private val knobCount: Int = KNOB_COUNT, private val fineFactor: Float = FINE_FACTOR) {
    private val held = BooleanArray(knobCount)
    private val turnedWhileHeld = BooleanArray(knobCount)
    private var browseAccum = 0f
    private var browseWasActive = false
    private var lastBrowseSession = 0

    /** Drops leftover browse travel so it can't leak into the next browse session. */
    private fun endBrowseSession() {
        if (browseWasActive) { browseWasActive = false; browseAccum = 0f }
    }

    fun register(registry: CommandRegistry) {
        registry.onClearHeldState {
            held.fill(false)
            turnedWhileHeld.fill(false)
            browseAccum = 0f
            browseWasActive = false
        }
        for (n in 1..knobCount) {
            val knob = n - 1
            registry.register(Command("knob.$n", CommandKind.RELATIVE, "knob", "Turn knob $n (fine while its switch is held)") { input, ctx ->
                val delta = (input as CommandInput.Delta).steps
                if (held[knob]) turnedWhileHeld[knob] = true
                val nav = ctx.navSurface
                if (nav != null && nav.browsing) {
                    val session = nav.browseSession
                    if (session != lastBrowseSession) { lastBrowseSession = session; browseAccum = 0f }
                    browseWasActive = true
                    if (knob == BROWSE_KNOB) browseTurn(delta, nav)
                    else if (isLiveRowKnob(knob, nav)) ctx.knobSurface?.turn(knob, if (held[knob]) delta * fineFactor else delta)
                    return@Command // every other knob is inert while browsing
                }
                endBrowseSession()
                ctx.knobSurface?.turn(knob, if (held[knob]) delta * fineFactor else delta)
            })
            registry.register(Command("knob.$n.press", CommandKind.MOMENTARY, "knob", "Knob $n switch: tap for its primary action") { input, ctx ->
                press(knob, (input as CommandInput.Press).down, shifted = false, ctx)
            })
            registry.register(Command("knob.$n.press_alt", CommandKind.MOMENTARY, "knob", "Knob $n switch with shift: tap for its secondary action") { input, ctx ->
                press(knob, (input as CommandInput.Press).down, shifted = true, ctx)
            })
        }
    }

    /**
     * Turns the cursor knob into whole cursor steps: [BROWSE_STEP] of knob travel is one item. At most one item per turn
     * message: the profile's turn acceleration would otherwise skip rows and slam into the ends of long lists.
     */
    private fun browseTurn(delta: Float, nav: NavSurface) {
        browseAccum += delta
        val steps = (browseAccum / BROWSE_STEP).toInt()
        if (steps != 0) {
            browseAccum -= steps * BROWSE_STEP
            nav.browseStep(steps.coerceIn(-1, 1))
        }
    }

    /** Knobs 1-4 (row one) stay on the browsed row while it is on screen. */
    private fun isLiveRowKnob(knob: Int, nav: NavSurface) = nav.browseRowLive && knob < ROW_KNOBS

    private fun press(knob: Int, down: Boolean, shifted: Boolean, ctx: CommandContext) {
        if (down) {
            held[knob] = true
            turnedWhileHeld[knob] = false
            return
        }
        val wasHeld = held[knob]
        held[knob] = false
        if (!wasHeld || turnedWhileHeld[knob]) return
        val nav = ctx.navSurface
        if (nav != null && nav.browsing) {
            if (knob == BROWSE_KNOB) nav.browseAccept(shifted)
            else if (isLiveRowKnob(knob, nav)) {
                val surface = ctx.knobSurface ?: return
                if (shifted) surface.secondary(knob) else surface.primary(knob)
            } else SendTarget.forKnob(knob)?.takeIf { it in nav.sendTargets }?.let(nav::browseSend)
            return
        }
        endBrowseSession()
        val surface = ctx.knobSurface ?: return
        if (shifted) surface.secondary(knob) else surface.primary(knob)
    }

    companion object {
        const val KNOB_COUNT = 16
        /** The browse cursor knob (0-based): 16, bottom right, the easiest to reach. */
        const val BROWSE_KNOB = KNOB_COUNT - 1
        /** Knobs per row; row one is the one that stays live in the picker. */
        const val ROW_KNOBS = 4
        /** How much a held switch scales a turn. */
        const val FINE_FACTOR = 0.1f
        /** Knob travel (fraction of range, before fine scaling) per browse cursor step: 1 encoder tick = 1 item. */
        const val BROWSE_STEP = 1f / 127f
    }
}
