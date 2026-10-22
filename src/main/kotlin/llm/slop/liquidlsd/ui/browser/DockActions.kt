package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.ui.shortcuts.ShortcutManager
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.liveDeck
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.LibraryNavigation
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.ParametersState

/**
 * The one place that decides what picking a row does in the [BrowserDock]: a bound dock (an [ApplyTarget] from the pair view) applies the
 * row to its target; an unbound dock (the Library) loads it. The list panels, the context menus and the controller all go through here.
 */
internal object DockActions {
    /** A tap on a row: applies it to the bound target. Unbound taps only select. */
    fun tap(target: ApplyTarget?, asset: AssetItem) {
        if (target == null) return
        target.apply(asset)
        BrowseRecents.note(asset)
    }

    /** A double-click on a row ([parametersState] is only needed for sources). Bound docks already applied it on the tap, so a double-click there only commits (drops the selection, see [ApplyTarget.onCommit]); the unbound dock loads to the deck or mixer. */
    fun doubleClick(session: SessionContext, mixer: Mixer, parametersState: ParametersState?, kind: BrowseKind, asset: AssetItem, target: ApplyTarget?) {
        if (target != null) { target.onCommit?.invoke(); return }
        BrowseRecents.note(asset)
        when (kind) {
            BrowseKind.SRC -> parametersState?.let { LibraryNavigation.loadAssetToInactiveDeck(session, mixer, asset, it) }
            BrowseKind.FX -> FXBrowserPanel.applyToDeck(session, asset, mixer.liveDeck)
            BrowseKind.TRANS -> TransitionBrowserPanel.applyToMixer(session, mixer, asset)
        }
    }

    /** The controller's accept on a list row: applies to the bound target, else the Library behaviour (sources load to the inactive deck, FX applies to the live deck, transitions apply). */
    fun acceptCursorRow(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        if (BrowserPane.hosted() != null) {
            BrowserPane.applyCursorRow()
            return
        }
        LibraryPanel.getSelectedAsset()?.let { BrowseRecents.note(it) }
        when (LibraryPanel.navMode) {
            LibraryViewMode.PRESETS -> PresetListPanel.selectedAsset?.let { LibraryNavigation.loadAssetToInactiveDeck(session, mixer, it, parametersState) }
            LibraryViewMode.FX -> FXBrowserPanel.selectedAsset?.let {
                FXBrowserPanel.applyToDeckFromController(session, it, mixer.liveDeck, if (mixer.liveDeck === mixer.deckA) "Deck A" else "Deck B")
            }
            LibraryViewMode.TRANS -> TransitionBrowserPanel.selectedAsset?.let { TransitionBrowserPanel.applyToMixer(session, mixer, it) }
            LibraryViewMode.MAPS, LibraryViewMode.QUEUES -> Unit
        }
    }

    /** Library-only list management (reordering a playlist, Delete removing assets) is off while a row's target is bound. */
    fun canManageList(target: ApplyTarget?): Boolean = target == null

    /**
     * The Library's delete key this frame: the `library.delete_asset` shortcut (Delete by default, rebindable), plus plain Backspace
     * so keyboards without a Delete key still work. Callers check that no text field has the keyboard.
     */
    fun deleteKeyPressed(): Boolean {
        if (ShortcutManager.isTriggered("library.delete_asset")) return true
        val io = ImGui.getIO()
        return !io.keyCtrl && !io.keyAlt && !io.keySuper && !io.keyShift && ImGui.isKeyPressed(ImGuiKey.Backspace, false)
    }

    /**
     * Enter on the list: the row's default action, the same as a double-click in the Library (load, apply to the live deck or
     * the mixer) and a click while bound (apply to the target). False when there is no row under the cursor.
     */
    fun enterOnRow(session: SessionContext, mixer: Mixer, parametersState: ParametersState): Boolean {
        val asset = LibraryPanel.getSelectedAsset() ?: return false
        val target = BrowserPane.hosted()
        if (target != null) {
            if (!target.accepts(asset)) return false
            tap(target, asset)
            return true
        }
        val kind = BrowseRecents.kindOf(asset) ?: return false
        doubleClick(session, mixer, parametersState, kind, asset, null)
        return true
    }
}
