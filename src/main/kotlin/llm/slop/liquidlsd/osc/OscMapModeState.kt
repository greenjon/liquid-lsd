package llm.slop.liquidlsd.osc

/**
 * Global "click any control to bind it" mode (Ctrl+Shift+O), modeled on Resolume/MadMapper/VDMX's
 * Hardware Learn toggles. While active, individual controls treat a plain click as "arm OSC Learn
 * for me" instead of their normal action, so a performer can bind many controls in a row without
 * repeatedly opening a right-click menu.
 */
object OscMapModeState {
    var active: Boolean = false
        private set

    fun toggle() {
        if (active) deactivate() else activate()
    }

    fun activate() {
        active = true
    }

    fun deactivate() {
        if (!active) return
        active = false
        OscLearnState.cancelLearn()
    }
}
