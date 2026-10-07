package llm.slop.liquidlsd.control

/**
 * The 16 hardware-addressable knobs of the Perform view, as seen by input devices. Knobs are 0-based
 * and row-major over the visible rows (knob 0-3 = first row). Implemented by the UI layer; handlers
 * run on the render thread.
 */
interface KnobSurface {
    /** Moves knob [knob] by [delta], a fraction of its full range (negative = down). */
    fun turn(knob: Int, delta: Float)

    /** The knob's tap action: bypass an FX slot, or reset a value to its default. */
    fun primary(knob: Int)

    /** The knob's shifted tap action: focus an FX slot / leave focus, or step the parameter page. */
    fun secondary(knob: Int)

    /** Shows the named page (e.g. `perform.ab`) so the knobs and the screen agree. Unknown ids are ignored. */
    fun showPage(pageId: String)

    /**
     * Steps the focused pair view [delta] pairs (A > B > BG > PV > Master > XF, wrapping) and returns true; false when no pair
     * is focused. A controller's bank change calls this first, so the banks walk the pairs instead of the Perform pages.
     */
    fun stepPair(delta: Int): Boolean = false

    /** True while the pair view is up: the knobs follow the focused pair on any bank, so the device must not be pulled back to the page's bank. */
    val pairFocused: Boolean get() = false

    /** Toggles Chain Link (all slots to/from the Super Knob) on the FX chain of the row touched last. */
    fun toggleChainLink() {}
}
