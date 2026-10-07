package llm.slop.liquidlsd.control

/**
 * Navigation and browsing as seen by input devices: the free side buttons plus the last knob (16, bottom
 * right) acting as a cursor while a browse context (the Library, the picker) is on screen. Implemented by the UI layer; handlers
 * run on the render thread. What a button does depends on the current context, so the surface
 * decides; the commands only say which physical button was pressed.
 */
interface NavSurface {
    /** True while knob 16 ([KnobCommands.BROWSE_KNOB]) should drive a cursor (turn = step, tap = accept) instead of its Perform knob. */
    val browsing: Boolean

    /**
     * How many leading knobs stay live while [browsing] (the pair view shows two rows: 8): the rows being filled are on screen,
     * so a freshly loaded source or chain can be tweaked at once. Every other knob except the cursor and the sends is inert.
     */
    val browseLiveKnobs: Int get() = 0

    /** Increases every time [browsing] turns on, so consumers can drop per-session state (e.g. partial knob travel). */
    val browseSession: Int

    /** Side button [index] (0-based, in the order of the profile's `side` input), with shift held or not. */
    fun button(index: Int, shifted: Boolean)

    /** Moves the browse cursor by [steps] items (negative = up). */
    fun browseStep(steps: Int)

    /** The cursor knob's tap while [browsing]: apply the cursor item; with [shifted] its alternative (enqueue). */
    fun browseAccept(shifted: Boolean)

    /** The send targets whose knob is live right now: the cursor item can go there (Library or picker). */
    val sendTargets: Set<SendTarget> get() = emptySet()

    /** A tap on a live send knob: send the cursor item to [target]. */
    fun browseSend(target: SendTarget) {}

    /** The send knobs that a shifted tap turns into a plain switch to that destination (no item needed). */
    val switchTargets: Set<SendTarget> get() = emptySet()

    /** A shifted tap on a knob in [switchTargets]: go to [target]'s pair without sending anything. */
    fun browseSwitch(target: SendTarget) {}
}

/**
 * Where a Library or picker knob tap sends the cursor item. Row three is the decks (knobs 9-12 = A, B, BG, PV) and knob 13,
 * first of row four, is the master bus (FX only); row one stays free for the row the send opens.
 */
enum class SendTarget(val knob: Int) {
    A(8), B(9), BG(10), PV(11), MASTER(12);

    companion object {
        fun forKnob(knob: Int): SendTarget? = entries.firstOrNull { it.knob == knob }
    }
}
