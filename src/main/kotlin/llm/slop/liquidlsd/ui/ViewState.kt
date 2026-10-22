package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext

/**
 * Which of the three views is up, derived in one place so the layout, the controller and the macro strip agree.
 *
 * - Perform: rows + a HALF dock (`editing` and `maximized` both false).
 * - Library: the dock FULL, no rows (`maximized`).
 * - Edit: the open module's pair of rows over the Params editor in the bay (`editing`).
 *
 * Pair view ([pair]): the focused deck/Master pair's two rows over the Browse dock; always [dockActive], never with [editing].
 * Bound in place ([bound]): Perform with its HALF dock bound to a slot of a row on the page ([ParametersState.boundInPlace]); same
 * layout as Perform.
 *
 * [dockActive] is whether a dock is the surface the controller browses: Library FULL, the pair view, or the Perform dock bound in place.
 */
internal data class ViewState(
    val editing: Boolean,
    val maximized: Boolean,
    val dockActive: Boolean,
    val pair: Boolean = false,
    val bound: Boolean = false
) {
    val rowsShown: Boolean get() = !maximized
}

internal fun viewStateOf(session: SessionContext): ViewState {
    val maximized = session.uiTheme.libraryMode == UITheme.LibraryMode.FULL
    val editing = session.parametersState.anyRackModuleExpanded() && !maximized
    val ps = session.parametersState
    val pair = ps.focusedPair != null && !maximized
    val bound = ps.boundInPlace && !maximized
    return ViewState(editing, maximized, dockActive = maximized || pair || bound, pair = pair, bound = bound)
}
