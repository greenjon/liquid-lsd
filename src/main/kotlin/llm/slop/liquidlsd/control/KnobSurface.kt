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

    /**
     * Called when the knob's switch goes down (outside browsing). Returns true if the knob acts on the press itself --
     * the tempo knob taps here, since a tap on release would lag -- and then the release does nothing.
     */
    fun pressDown(knob: Int): Boolean = false

    /** Shows the named page (e.g. `perform.ab`) so the knobs and the screen agree. Unknown ids are ignored. */
    fun showPage(pageId: String)

    /**
     * Steps the focused pair view [delta] pairs (A > B > BG > PV > Master > XF, wrapping) and returns true; false when no pair
     * is focused. A controller's bank change calls this first, so the banks walk the pairs instead of the Perform pages.
     */
    fun stepPair(delta: Int): Boolean = false

    /** The id of the page the knobs currently control (e.g. `ab`, optionally `perform.`-prefixed), or null if unknown. */
    val currentPageId: String? get() = null

    /** True while the pair view is up: the knobs follow the focused pair on any bank, so the device must not be pulled back to the page's bank. */
    val pairFocused: Boolean get() = false

    /** Toggles Chain Link (all slots to/from the Super Knob) on the FX chain of the row touched last. */
    fun toggleChainLink() {}
}
