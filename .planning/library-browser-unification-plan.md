# Unify the Library and the Edit-bay browser (v1) — plan, 2026-10-05

Decision (user, 2026-10-05): do this **for v1**, not v1.1. The first public release should feel like one app.
Builds on `unified-browser-pane-plan.md` (one `BrowserPane`), `twister-library-send-plan.md` and `twister-library-send-row-plan.md` (send opens the row).

## Goal
The Library and the Edit bay's Browse tab are the same thing: **one browser dock whose only difference is an optional bound apply-target.**
- Unbound (Library): tap/double-click loads to the inactive deck, context menus and drag work, send knobs, enqueue.
- Bound (opened from a row, or after a send): the same dock, with a chip "Applies to: Deck A source", tap applies to that row.
- Same tabs, toolbar, queues, popups and keyboard shortcuts in both. No feature exists in only one of them.

Views stay three (Perform, Library, Edit); what changes is that Library and Edit stop being two implementations.

| View | Rows on top | Bottom | Bound |
|---|---|---|---|
| Perform (HALF) | 4 page rows | Library dock at `libraryRatio` (unbound) | no |
| Library (FULL) | none | Library dock, full height | no |
| Edit | 1 focused row (+ Learn pins) | Bay: **Params** tab / **Browse** tab = the same dock | derived from the focused row |

## Facts that shape the plan (read 2026-10-05)
- `BrowserPane` is already ONE shared object: scope, search, tree cursor, selections and `activeSelectionSource` are shared by both hosts. Only `ScopeMemory`
  (keyed by `contextKey`, "library" vs `gen/<deck>`, `chain/...`, `fxslot/...`, `transition`) separates them. The bound/unbound split is the `target: ApplyTarget?` parameter.
- What differs is the chrome in `LibraryPanel.draw` and `PerformanceBrowseBay`:
  - Library-only: tabs Sources/FX/Transitions/**Macros** (Macros is `MapsBrowserPanel`, not part of `BrowserPane`), `BrowserActionToolbar` (audition lock, Q, BGQ, deck load buttons),
    keyboard handlers (Q, Shift+Q, Enter in Transitions, Up/Down; dead in Edit view), the Rename / NewPlaylist / export-queue popups.
  - **Gap today:** in the bay the tree's "+" (new playlist) and playlist Rename set pending flags that nothing draws while the Library is hidden.
  - Bay-only: Save + "External video..." (SRC), Clear Slot / Clear Chain (FX), the `Pick: SRC | Chain | FX1-3` tab strip, the next-up queue line.
- Per-panel `target == null` checks (row click, double-click, filter, applied marker, playlist reorder, delete) are scattered in `PresetListPanel`, `FXBrowserPanel`, `TransitionBrowserPanel`, `BrowserPane`.
- Mode state is derived, in three places: `LibraryPanel.isEditView` (`anyRackModuleExpanded && libraryMode != FULL`), `NavigationSurface.inLibraryView` / `inPicker`, and the layout in
  `UIManager.drawAssetManagementLayout`. Edit and Library are made exclusive in two places: `ParametersState.setDisclosure` (drops FULL to HALF) and `LibraryPanel.show` (collapses Edit).
- Which row Edit shows: `rackModuleDisclosure` -> `PerfRows.visibleRowsForPage` (uses the shared per-deck and mixer sub-tabs, so opening an FX browse flips that deck's Perform SRC/FX pill; pinned catalog rows are exempt).
- Row drawing: `drawMatrix(rows, rowH)` already takes any number of rows. Only the macro strip, the selected-knob card and `[EDIT]` affordances depend on `isEditView` / `isModuleExpanded`.

## Design
1. **`BrowserDock`** (new, `ui/browser/BrowserDock.kt`): the body of `LibraryPanel.draw` as a reusable composable: tab strip, toolbar, pane, popups, shortcuts.
   Signature: `draw(session, mixer, parametersState, binding: DockBinding?)`. `DockBinding` = the row's `ApplyTarget` plus its label and clear action.
   `LibraryPanel` draws it with `binding = null` (Library window); the bay draws it with the row's binding (Browse tab).
2. **Bound is derived, not a mode.** In Edit the dock opens on the kind of the focused row (SRC row -> Sources, FX row -> FX, Master/Trans -> Transitions or FX).
   While the selected tab's kind matches the row's editable kind it is bound; picking another kind tab (e.g. Sources while editing an FX row) shows that tab unbound with Library behaviour.
   The chip says which. The bay's `Pick: SRC | Chain | FX1-3` strip becomes the sub-selector shown while the FX tab is bound (chain / slot 1-3).
3. **One apply path.** A `DockActions` object owns: load to inactive deck, load to deck X, send, enqueue, apply-to-target. `LibraryNavigation.accept/send`, the panels' double-click and the context menus call it;
   the `target == null` checks in the panels collapse into `DockActions.activate(asset, binding)`.
4. **Controller:** `NavigationSurface` stops branching on `inLibraryView` / `inPicker`: `browsing` = the dock is the active surface (Library FULL, or Edit with the Browse tab up); `bound` = binding != null;
   `browseRowLive` = Edit view. Side buttons keep their current maps keyed by `bound` (Library: back / tab / pane; bound: back / pane / shift+clear) until hardware use suggests one merged map (see open questions).
5. **Back / Esc order** (unchanged, listed so it's tested): Learn, Preferences, FX focus, collapse Edit (-> Perform), then Library FULL -> HALF. Space: Edit -> Perform, else FULL <-> HALF (unchanged).

## Phases (each ends green: `./gradlew test`, one commit)
**1. Extract `BrowserDock` (no visible change).** Move the `LibraryPanel.draw` body (tabs, toolbar, pane, popups, shortcuts, Maps tab) into `BrowserDock` with `binding = null`.
   `LibraryPanel.draw` becomes window chrome + `BrowserDock.draw(null)`. Popups and the shortcut block become dock-level so they run wherever the dock is drawn.
   Tests: existing `LibraryNavigationTest`, `WindowLayoutSafetyTest` stay as they are.
**2. Dock inside the bay.** `PerformanceBrowseBay` calls `BrowserDock.draw(binding)` instead of building a bare pane. Add the bound chip, keep Save / External video / Clear in the dock header when bound.
   Fixes the new-playlist / rename gap and brings the toolbar, Q shortcuts and queue columns into Edit (the "queues are hidden in Edit" tooltip and next-up line go away).
   Resolve `viewMode` vs hosted kind: the dock owns one selected tab; `LibraryPanel.navMode` reads it (the hosted-kind shadowing disappears).
**3. One apply path.** Introduce `DockActions`; replace the scattered `target == null` branches in the three list panels and `BrowserPane`; `ScopeMemory` contexts keep working (bound contexts remember their scope, unbound = "library").
   Tests: `ApplyTargetTest` (+ activate with / without binding), the double-click / context menu behaviours listed in the facts above.
**4. Controller merge.** Replace `inLibraryView` / `inPicker` / `isEditView` call sites with `ViewState { rowsShown, dockBound, maximized }` computed in one place; update `NavigationSurface`, `LibraryNavigation`, `PerformSurface.dimForBrowse`, `MacroStripVisibility`.
   Keep `LibraryPanel.isEditView` as a thin alias until the last call site moves. Tests: `NavigationSurfaceTest` (rename cases, same assertions), `NavCommandsTest`, `KnobCommandsBrowseTest`.
**5. Layout rule.** Decide FULL + focused row (legal now? see open question 2). If yes: remove the `setDisclosure` FULL->HALF drop and the `show()` collapse, and let `UIManager` give a focused row above a maximized dock; otherwise leave the exclusivity and delete only the comments. Update `RackDisclosureTest.openingDeepEditDropsAFullLibraryToHalf...`.
**6. Polish + docs.** Tooltips/menu text ("Library queue columns are hidden in Edit view" etc.), user guide (`your_workspace.md`, `presets_and_library.md`, `macros_and_rack.md`, `performance_controls.md`), `docs/developer/ui.md`, `unified_control_mapping.md`,
   `DECISIONS.md` (section 7: edit in place; the "dock in Edit view: left alone" decision at ~:282 is reversed by this), both release-notes files, regenerate bundled HTML, update `ui-naming`/glossary if the Browse tab is renamed.

## Risks
- **Keyboard ownership.** The bay's Params keys (`keyboardOwnerModuleId`: Ctrl+S/C/V/Z, Delete) and the dock's Library keys (Q, Up/Down, Delete asset) must not fire together; Delete already has a global confirm. Decide per key which wins when the dock has focus.
- **ImGui ids / state.** The dock drawn in two windows needs id scopes keyed by host so tree/list/queue widgets don't collide when both are alive for a frame (Edit is exclusive with the Library window today, so this only matters if open question 2 is "yes").
- **Min size.** The dock now adds the tab strip + toolbar into the bay; check the 1280x720 floor (`bayH = availH - gridH`, grid capped at `availH - 160`) and `deepEditParamsWidth` / `maxAllowedRightW` (`UIManager.kt:497`).
- **Shared sub-tab coupling** (opening an FX browse flips the deck's Perform SRC/FX pill) stays; it is existing behaviour and not part of this work. Candidate cleanup: pass an explicit half instead of writing the shared sub-tab.
- **Tests that encode the old modes** (listed in the exploration): `NavigationSurfaceTest` (hostPane/unhostPane helpers and `UiClock` timing of `hosted()`), `RackDisclosureTest`, `WindowLayoutSafetyTest`, `PerfRowLayoutTest`, `PerformSurfaceTest`, `MacroStripVisibilityTest`.
- **Hosted timing.** `hosted()` is "drawn in the last 300 ms"; if the dock replaces it, prefer an explicit `DockState.visible/binding` set during draw so controller code doesn't depend on a clock.

## Open questions (my defaults in brackets; say "go" to accept)
1. Does choosing a non-matching tab in Edit unbind that tab (Library behaviour), or stay locked to the row's kind? [unbind; the chip shows which]
2. Should Library FULL be allowed with a focused row above it (the old "pinned row" idea)? [no for v1: Edit already is that layout; revisit after hardware use]
3. Macros tab in the bay: show it (unbound, not controller-navigable, as today in the Library) or hide it while bound? [show]
4. Merge the side-button maps (Library vs bound) into one now, or keep both until hardware feedback? [keep both]

## Not in scope
Per-row focus without touching the shared sub-tabs; controller navigation of the Macros tab; a single window for Library + Edit.
