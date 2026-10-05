package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.ui.browser.BrowserDock
import llm.slop.liquidlsd.ui.browser.BrowserPane

/**
 * Which of the three views is up, derived in one place so the layout, the controller and the macro strip agree.
 *
 * - Perform: rows + a HALF dock (`editing` and `maximized` both false).
 * - Library: the dock FULL, no rows (`maximized`).
 * - Edit: one focused row over the dock in the bay (`editing`).
 *
 * [dockBound] is true while the dock in the Edit bay shows a tab of the row's own kind, so a tap applies to the row.
 * [dockActive] is whether a dock is the surface the controller browses: Library FULL, or the Edit bay with its Browse tab up.
 */
internal data class ViewState(val editing: Boolean, val maximized: Boolean, val dockBound: Boolean, val dockActive: Boolean) {
    val rowsShown: Boolean get() = !maximized
}

internal fun viewStateOf(session: SessionContext): ViewState {
    val maximized = session.uiTheme.libraryMode == UITheme.LibraryMode.FULL
    val editing = session.parametersState.anyRackModuleExpanded() && !maximized
    val bound = editing && BrowserPane.hosted() != null
    return ViewState(editing, maximized, bound, dockActive = maximized || (editing && BrowserDock.editHosted()))
}
