package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.liveDeck
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.LibraryNavigation
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.ParametersState

/**
 * The one place that decides what picking a row does in the [BrowserDock]: a bound dock (an [ApplyTarget] from an Edit row) applies the
 * row to its target; an unbound dock (the Library) loads it. The list panels, the context menus and the controller all go through here.
 */
internal object DockActions {
    /** A tap on a row: applies it to the bound target. Unbound taps only select. */
    fun tap(target: ApplyTarget?, asset: AssetItem) {
        target?.apply(asset)
    }

    /** A double-click on a row ([parametersState] is only needed for sources). Bound docks already applied it on the tap, so only the unbound dock acts: it loads to the deck or mixer. */
    fun doubleClick(session: SessionContext, mixer: Mixer, parametersState: ParametersState?, kind: BrowseKind, asset: AssetItem, target: ApplyTarget?) {
        if (target != null) return
        when (kind) {
            BrowseKind.SRC -> parametersState?.let { LibraryNavigation.loadAssetToInactiveDeck(session, mixer, asset, it) }
            BrowseKind.FX -> FXBrowserPanel.applyToDeck(session, asset, mixer.liveDeck)
            BrowseKind.TRANS -> TransitionBrowserPanel.applyToMixer(session, mixer, asset)
        }
    }

    /** The controller's accept on a list row: applies to the bound target, else the Library behaviour (sources load, FX is queued, transitions apply). */
    fun acceptCursorRow(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        if (BrowserPane.hosted() != null) {
            BrowserPane.applyCursorRow()
            return
        }
        when (LibraryPanel.navMode) {
            LibraryViewMode.PRESETS -> PresetListPanel.selectedAsset?.let { LibraryNavigation.loadAssetToInactiveDeck(session, mixer, it, parametersState) }
            LibraryViewMode.FX -> LibraryNavigation.enqueue(session, bg = false)
            LibraryViewMode.TRANS -> TransitionBrowserPanel.selectedAsset?.let { TransitionBrowserPanel.applyToMixer(session, mixer, it) }
            LibraryViewMode.MAPS -> Unit
        }
    }

    /** Library-only list management (reordering a playlist, Delete removing assets) is off while a row's target is bound. */
    fun canManageList(target: ApplyTarget?): Boolean = target == null
}
