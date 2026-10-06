# Dock target as a selection (not a place)

Direction agreed 2026-10-06. Follows `library-browser-unification-plan.md` (phases 1-6 done).

## Where the code is today (verified by exploration)

- The apply-target is derived, not stored: `PerformanceBrowseBay` builds a `DockBinding` from the focused rack module's
  `ParametersState.browseTargetFor(moduleId)` (per-module map `rackBrowseTarget`, plus a global `rackSectionMode`).
- Row clicks (SRC badge `PerformanceDeckControls:361`, chain name `FxChainHeader:402`, FX slot `FxSlotCell:104-127`,
  transition `PerformanceTransitionsControls:150`) call `open*Browse`, which also calls `setDisclosure(DEEP_EDIT)` =
  expands the row into the Edit bay and collapses the others. That is why Perform cannot bind.
- "Hosted" = two 300 ms "drawn recently" clocks: `BrowserPane.hosted()` and `BrowserDock.editHosted()`, consumed only by
  `ViewState` (`dockBound`, `dockActive`), `NavigationSurface`, `DockActions.acceptCursorRow`, `LibraryPanel.selectPreset`.
- Library dock (Perform/Library views) is drawn by `UIManager.drawLibraryDock` with no binding; Edit view draws it only
  inside the bay.
- `ViewState.dockBound` has no direct readers.

## Design

One object, `DockTarget` (new, `ui/browser/`), owned by session UI state:

    data class Selection(moduleId, kind: SRC|CHAIN|FX_SLOT|TRANSITION, deckLabel?, slotIndex?)
    DockTarget.selection: Selection?     // null = plain Library
    DockTarget.set(sel) / clear() / paused (derived: selected tab's BrowseKind != selection.kind)

`BrowserDock` binding is built from `DockTarget` (builders moved out of `PerformanceBrowseBay` into one
`DockBindings.of(selection, ...)`), not from the focused row. `hosted()` becomes `DockTarget.active()`
(selection non-null AND dock tab matches); both 300 ms checks are deleted.

## Phases (one green commit each)

1. **DockTarget state, no visible change.** Add `DockTarget`; make `openGenBrowse/openFxChainBrowse/openTransitionBrowse`
   write it (still expanding the row); `browseTargetFor` reads from it. Delete `rackBrowseTarget`. Tests: set/clear/pause.
2. **Explicit hosted.** Replace `hosted()`/`editHosted()` clocks with `DockTarget` state; `ViewState.dockBound/dockActive`
   derive from it; `NavigationSurface`/`DockActions`/`LibraryPanel` updated. Remove `UiClock` hosting helpers from tests,
   replace with `DockTarget.set`. Risk: anything relying on "bound only while drawn" (e.g. Esc/back, Library FULL).
3. **Row clicks select, don't expand.** SRC badge / chain name / slot cell / transition name call `DockTarget.set` and
   switch the dock tab; no `setDisclosure`. Perform's Library dock draws bound (`UIManager.drawLibraryDock` passes the
   binding). Selected slot gets a deck-coloured outline on its row (SRC badge, chain header, slot cell, transition name).
   `FxSlotCell` single click = select (keep the pending-double-click focus toggle).
4. **Header line.** Chip merges into `BrowserDock.drawHeader`: `[tabs] ● Deck A · FX1 [Clear Slot] ✕ … [toolbar][⤢]`;
   short form at narrow widths (budget against `TABS_W=260` + toolbar + `windowBtnW`); dock border takes deck colour while
   bound; ✕ and Esc clear the target; paused state greys the chip. Optional: double-click while bound = apply + clear.
5. **Edit bay.** Delete the `Pick:` strip; tabs become `Parameters | Browse`; Browse = dock bound to `DockTarget` (default
   the focused row's SRC, or last clicked slot on that row). `drawQueueNextUp` stays on Parameters.
6. **Twister sends and picker.** `LibraryNavigation.send` and `NavigationSurface.openPicker` set the target and, if the
   target row is not on the active perform page, switch to the page that shows it (via `PerfRows`/`PerformPages`).
   Check every send target (A/B/BG/PV, master FX) and that knobs 1-4 still play the bound row; add test per target.
7. **Docs.** DECISIONS.md section 7 (reverse "dock in Edit view" wording, record target-as-selection), `docs/developer/ui.md`
   (:187-219), `unified_control_mapping.md:319-321`, user guide (`macros_and_rack.md`, `performance_controls.md` incl.
   removing the stale "Unified" toggle text, `presets_and_library.md`), both release-notes files (they differ — diff first),
   tooltips.

## Decisions (user answered yes to all, 2026-10-06)

1. FULL keeps the selection (chip shown, no row visible); Esc clears.
2. Target row off screen (page change, SRC|FX toggle) clears the target.
3. Esc clears the target first, then the existing back/Library behaviour.
4. Double-click while bound = apply + clear, included in phase 4.
5. A Twister send may switch the visible perform page.
