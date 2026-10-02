package llm.slop.liquidlsd.control

/**
 * The `knob.<n>` command family and the press gestures built on it:
 *  - `knob.<n>` turns knob n. Turning while its switch is held is a fine adjustment.
 *  - `knob.<n>.press` is the switch: a tap (released without turning) runs the knob's primary action.
 *  - `knob.<n>.press_alt` is the same switch with shift held: a tap runs the secondary action.
 * Hold state lives here, not in the device, because the Twister sends identical turn messages whether
 * or not its switch is down. One instance serves all devices; the switch is expected to be held on
 * one device at a time.
 */
class KnobCommands(private val knobCount: Int = KNOB_COUNT, private val fineFactor: Float = FINE_FACTOR) {
    private val held = BooleanArray(knobCount)
    private val turnedWhileHeld = BooleanArray(knobCount)

    fun register(registry: CommandRegistry) {
        for (n in 1..knobCount) {
            val knob = n - 1
            registry.register(Command("knob.$n", CommandKind.RELATIVE, "knob", "Turn knob $n (fine while its switch is held)") { input, ctx ->
                val delta = (input as CommandInput.Delta).steps
                if (held[knob]) turnedWhileHeld[knob] = true
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

    private fun press(knob: Int, down: Boolean, shifted: Boolean, ctx: CommandContext) {
        if (down) {
            held[knob] = true
            turnedWhileHeld[knob] = false
            return
        }
        val wasHeld = held[knob]
        held[knob] = false
        if (!wasHeld || turnedWhileHeld[knob]) return
        val surface = ctx.knobSurface ?: return
        if (shifted) surface.secondary(knob) else surface.primary(knob)
    }

    companion object {
        const val KNOB_COUNT = 16
        /** How much a held switch scales a turn. */
        const val FINE_FACTOR = 0.1f
    }
}
