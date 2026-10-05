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
     * True while [browsing] and the row being filled is on screen (the picker): knobs 1-4 stay live on it, so a
     * freshly loaded source or chain can be tweaked at once. Every other knob except the cursor is inert.
     */
    val browseRowLive: Boolean get() = false

    /** Increases every time [browsing] turns on, so consumers can drop per-session state (e.g. partial knob travel). */
    val browseSession: Int

    /** Side button [index] (0-based, in the order of the profile's `side` input), with shift held or not. */
    fun button(index: Int, shifted: Boolean)

    /** Moves the browse cursor by [steps] items (negative = up). */
    fun browseStep(steps: Int)

    /** The cursor knob's tap while [browsing]: apply the cursor item; with [shifted] its alternative (enqueue). */
    fun browseAccept(shifted: Boolean)

    /** The send targets whose knob is live right now: the cursor item can go there (Library only; the picker keeps knobs 1-4 for its row). */
    val sendTargets: Set<SendTarget> get() = emptySet()

    /** A tap on a live send knob: send the cursor item to [target]. */
    fun browseSend(target: SendTarget) {}
}

/**
 * Where a Library knob tap sends the cursor item. Column one is the decks, top to bottom (knobs 1, 5, 9, 13); knob 2,
 * top of column two and next to Deck A, is the master bus (FX only).
 */
enum class SendTarget(val knob: Int) {
    A(0), MASTER(1), B(4), BG(8), PV(12);

    companion object {
        fun forKnob(knob: Int): SendTarget? = entries.firstOrNull { it.knob == knob }
    }
}
