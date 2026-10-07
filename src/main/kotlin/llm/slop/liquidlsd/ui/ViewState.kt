package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext

/**
 * Which of the three views is up, derived in one place so the layout, the controller and the macro strip agree.
 *
 * - Perform: rows + a HALF dock (`editing` and `maximized` both false).
 * - Library: the dock FULL, no rows (`maximized`).
 * - Edit: one focused row over the Params editor in the bay (`editing`).
 *
 * Pair view ([pair]): the focused deck/Master/XF pair's two rows over the Browse dock; always [dockActive], never with [editing].
 *
 * [dockActive] is whether a dock is the surface the controller browses: Library FULL, or the pair view.
 */
internal data class ViewState(val editing: Boolean, val maximized: Boolean, val dockActive: Boolean, val pair: Boolean = false) {
    val rowsShown: Boolean get() = !maximized
}

internal fun viewStateOf(session: SessionContext): ViewState {
    val maximized = session.uiTheme.libraryMode == UITheme.LibraryMode.FULL
    val editing = session.parametersState.anyRackModuleExpanded() && !maximized
    val ps = session.parametersState
    val pair = ps.focusedPair != null && !maximized
    return ViewState(editing, maximized, dockActive = maximized || pair, pair = pair)
}
