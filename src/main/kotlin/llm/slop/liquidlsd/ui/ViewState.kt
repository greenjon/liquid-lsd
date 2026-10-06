package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.ui.browser.BrowserPane

/**
 * Which of the three views is up, derived in one place so the layout, the controller and the macro strip agree.
 *
 * - Perform: rows + a HALF dock (`editing` and `maximized` both false).
 * - Library: the dock FULL, no rows (`maximized`).
 * - Edit: one focused row over the dock in the bay (`editing`).
 *
 * [dockBound] is true while the dock shows a tab of the selected target's kind (in any view), so a tap applies to the target.
 * [dockActive] is whether a dock is the surface the controller browses: Library FULL, or the Edit bay with its Browse tab up.
 */
internal data class ViewState(val editing: Boolean, val maximized: Boolean, val dockBound: Boolean, val dockActive: Boolean) {
    val rowsShown: Boolean get() = !maximized
}

internal fun viewStateOf(session: SessionContext): ViewState {
    val maximized = session.uiTheme.libraryMode == UITheme.LibraryMode.FULL
    val editing = session.parametersState.anyRackModuleExpanded() && !maximized
    val ps = session.parametersState
    val browsing = editing && ps.rackModuleDisclosure.entries.any { it.value != ParametersState.DisclosureLevel.COLLAPSED && ps.sectionModeFor(it.key) == ParametersState.SectionMode.BROWSE }
    return ViewState(editing, maximized, dockBound = BrowserPane.hosted() != null, dockActive = maximized || browsing)
}
