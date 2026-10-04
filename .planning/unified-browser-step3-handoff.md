# Handoff: unified BrowserPane step 3, MIDI navigation (written 2026-10-04)

You are a fresh agent. Read, in this order: `.planning/unified-browser-pane-plan.md` (decisions, measurements, progress), `.planning/midi-phase3-navigation-plan.md` (the existing MIDI nav design, contexts and button sets), then this file. Project rules: Kotlin + Dear ImGui desktop VJ app; "done" = code + tests + DECISIONS.md + RELEASE_NOTES.md + docs/release_notes.md + docs/user_guide + docs/developer/ui.md + tooltips (the regenerated `src/main/resources/docs/` is produced by mkdocs via the `generateDocs` Gradle task, see build.gradle.kts ~line 150; step 6 of the plan does it once at the end). Never add Claude co-author lines to commits. Give a short recommendation before planning, and ask before design decisions beyond the plan. Delegate wide exploration to an Explore agent.

## State (all committed on main)
- Step 1 model, favorites, catalogs: `ui/browser/BrowseModel.kt`, `BrowseCatalogs.kt`, `BrowseFavorites.kt`.
- Step 2 done for all three kinds (SRC, FX, TRANS): `ui/browser/BrowserPane.kt` is the pane (tree 25% | list 50% | queues 25%), behind the beta "Unified" toggle in the Library tab bar or `-Dlsd.unifiedBrowser=true`. Classic four columns are still the default and still work.
- Playlist editing in the pane goes through `ui/browser/PlaylistEdit.kt` (`PlaylistEdit`, `PresetPlaylistEdit`, `TokenPlaylistEdit`, `PlaylistRows`, `PlaylistItems`).
- Step 5 (dirty-deck modal answerable from the controller) is DONE: `DeckConfirmPrompt` in `PopupManager.kt`, handled first in `NavigationSurface`.
- Step 3 (this task) and steps 4 and 6 are NOT started. Do not do step 4 here.

## The task
Make controller (Twister) navigation work with the unified pane, with pane order tree -> list -> queue, replacing the old per-column model, without breaking the classic Library or the Edit-row picker (the picker is step 4's problem; keep `ShaderPickerPopup`/`ChainListBrowse` nav untouched).

## How nav works today (read these files)
- `ui/NavigationSurface.kt`: per-frame object implementing `control/NavSurface`. Contexts: dirty-deck modal, Library FULL (`inLibraryView`), picker (`inPicker`), Perform/Edit. Library buttons: side1 = back (shift: enqueue BG), side2 = next tab (shift prev), side3 = next pane (shift prev); knob 1 turn = `browseStep`, tap = `browseAccept` (shift+tap = enqueue A/B). `browsing` is true in Library FULL.
- `ui/LibraryNavigation.kt`: `stepTab`, `stepPane`, `step`, `enqueue`, `accept`, and `panes()` returning `LibraryPanel.SelectionSource` lists per tab (PRESETS, PLAYLIST, QUEUE_BG, QUEUE_AB, FX_*, TRANSITION_*).
- `ui/LibraryPanel.kt`: `navigateSelection(delta, ...)` (~line 590) moves the cursor per `activeSelectionSource`; `getActiveSelectedFile`, `selectPreset`, `selectQueueAb/Bg`, and each list's own selection state (`PresetListPanel.selection`, `FXBrowserPanel.selectedAsset`, `TransitionBrowserPanel.selectedAsset`, `QueueActionsPanel.selectedIndex`, `BgQueueActionsPanel.selectedIndex`, `FXQueueActionsPanel`/`FXBgQueueActionsPanel`/`TransitionQueuePanel.selectedIndex`).
- Tests: `src/test/.../ui/LibraryNavigationTest.kt`, `NavigationSurfaceTest.kt` (shows how to construct a surface; `NavigationSurface(session, state, mixer, ctx, deckConfirm?)`). Intended behavior is recorded in DECISIONS.md "MIDI phase 3" entries.

## What changes in the unified pane (the facts that make this non-trivial)
1. **Playlists are no longer a separate pane.** In the pane a playlist is a tree scope (`BrowserPane.scopes[kind]` is a `BrowseScope.Playlist`); its rows are drawn in the list and use the PRESETS selection source. `SelectionSource.PLAYLIST/FX_PLAYLIST/TRANSITION_PLAYLIST` and `LibraryPanel.activePlaylistData` are only meaningful in classic mode. So in pane mode `panes()` must be tree, list, then the kind's queue(s): SRC = BG queue, A/B queue; FX = FX BG queue, FX A/B queue; TRANS = the single transition queue.
2. **There is no tree cursor yet.** `BrowserPane.drawTree` selects on mouse click only. You need a tree cursor (suggest: a new `SelectionSource.TREE`, stepping over selectable `catalog.tree()` nodes that are visible given `collapsed`, accept = select the scope, which `select()` already does, including setting the Library's selected playlist file). Expose a small public/internal API on `BrowserPane` (e.g. `stepTree(kind, delta)`, `scopeOf`) rather than reaching into private maps. The pure part (next visible selectable node) belongs in `BrowseModel.kt` or a new pure file and should be unit tested.
3. **The list is fed from the pane.** The pane already writes `PresetListPanel.filteredPresets` / `FXBrowserPanel.filteredRows` / `TransitionBrowserPanel.filteredRows` each frame, so `navigateSelection` over PRESETS works unchanged for the list pane. Verify this with a screenshot or a test, including that the list selection resets when the tree scope changes (stale selection from the previous scope should not survive; decide and test).
4. **Accept semantics**: list pane accept loads/applies as today (`LibraryNavigation.accept`); tree accept selects the scope; queue accept as today. Stepping never applies (existing rule).
5. **Enqueue** (shift+tap, shift+side1) uses `enqueueTargets`/`enqueue`: check they still work when the list shows a playlist scope, and in a playlist scope think about whether "enqueue" should add the single item (current behavior); no change needed unless it breaks.
6. Scroll/focus: rows scroll to the selection via `LibraryPanel.shouldScrollToSelection`; the tree needs the same for the cursor row (add an equivalent flag or reuse it).
7. The cursor row needs a visible highlight in the tree (the lists already highlight selection). Keep the classic path working: gate everything on `BrowserPane.enabled && supports(kind)` (a helper like `LibraryNavigation.unifiedActive()` would help) and leave the classic `panes()` branch as is.

## Recommended approach (confirm with the user before large deviations)
- Add `SelectionSource.TREE` plus a pane-mode `panes()` branch; extend `paneSize`, `hasCursor`, `navigateSelection`, `accept` for TREE only; do not touch the classic branches.
- Keep side-button meaning identical (side3 = next/previous pane) so no docs about buttons change, only the pane list.
- Open decisions to ask the user: (a) should entering the tab start the cursor on the list or the tree; (b) should stepping the tree select the scope immediately (browse-as-you-turn) or only on tap. The existing rule says stepping never applies, which argues for tap, but a tree whose list doesn't update while turning feels dead; recommend "cursor moves, list previews only on tap" to stay consistent unless the user prefers otherwise; (c) knobs 2-16 stay inert while browsing (already decided in phase 3).

## Verification
- Unit tests for the pure tree stepping and for `LibraryNavigation.panes()`/`stepPane` in pane mode (see `LibraryNavigationTest`).
- `./gradlew test --offline` must stay green (it is as of f0112c4).
- Screenshots: `JAVA_TOOL_OPTIONS="-Dlsd.unifiedBrowser=true -Dlsd.libraryTab=fx" ./gradlew run --offline -q --args="--no-audio --window=1280x720 --screenshot-ui=/tmp/x.png --screenshot-after-frames=10"` (frame counts above ~30 never fire; window comes out about 768 tall; `-Dlsd.libraryTab=fx|trans|presets` picks the starting tab). Screenshots cannot press MIDI buttons, so cursor behavior must be covered by tests; the hardware check on the Twister is the user's (ask them to do it; say what to try: side2 tabs, side3 panes through tree/list/queues, knob 1 turn/tap, shift+tap enqueue).
- Docs for this step: DECISIONS.md entry, both release-notes files, `docs/user_guide/performance_controls.md` (the side-button table row for Library says "step the list (browser, playlist, BG queue, A/B queue)": update for pane mode once pane mode is the default, or note both), tooltips, and update `.planning/midi-phase3-navigation-plan.md` and `unified-browser-pane-plan.md` progress.

## Other known gaps (not step 3, don't fix unasked)
- Scope selection is not persisted across restarts; queue pane is a fixed half/half stack (no splitter).
- Not hand-tested: FX/Trans playlist drag-reorder/insert, FX overwrite popup from the pane, drag from a playlist row to a queue (playlist rows drag as reorder items only).
- Maps tab is separate and has no queues; MIDI nav returns `emptyList()` for it.
