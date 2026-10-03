package llm.slop.liquidlsd.control

/**
 * Navigation and browsing as seen by input devices: the free side buttons plus knob 1 acting as a
 * cursor while a browse context (the Library) is on screen. Implemented by the UI layer; handlers
 * run on the render thread. What a button does depends on the current context, so the surface
 * decides; the commands only say which physical button was pressed.
 */
interface NavSurface {
    /** True while knob 1 should drive a cursor (turn = step, tap = accept) instead of its Perform knob. */
    val browsing: Boolean

    /** Side button [index] (0-based, in the order of the profile's `side` input), with shift held or not. */
    fun button(index: Int, shifted: Boolean)

    /** Moves the browse cursor by [steps] items (negative = up). */
    fun browseStep(steps: Int)

    /** Knob 1's tap while [browsing]: apply the cursor item; with [shifted] its alternative (enqueue). */
    fun browseAccept(shifted: Boolean)
}
