# Pair focus view (plan)

2026-10-06. Replaces most of `dock-binding-single-source-plan.md` (kept for the decisions it records). Not started. v1.0 scope: the owner's bar is
"the interface no longer confuses or annoys me".

## Design

Clicking a badge (deck SRC or FX, Master MIX or FX, XF or CLK) or entering Browse focuses that **pair**; the Browse list sits below it.

- **Pairs**: deck SRC+FX (`deck.<tag>.src` + `deck.<tag>.fx`), Master (`master.mix` + `master.fx`), XF+CLK (`trans` + `global`; rows only, no Params).
- **Other rows hide completely** (the deck monitors stay visible, so nothing is hidden from the performer).
- **Knobs**: 1-8 are the pair's rows (live), 9-12 send to A/B/BG/PV, 13 to Master FX, 16 is the browse cursor, tap applies. Same layout as today's Edit picker, with two live rows instead of one.
- **Browse binds to the half last touched** (row click, badge, or `lastTouchedKnob`), not to a sub-tab. SRC half -> Gen, FX half -> chain or slot, MIX half -> nothing to browse (list stays plain Library), `trans` -> Transition.
- **Exit**: Esc / controller back / a Back button. A send from the picker moves the focus to the destination deck's pair.
- **Params (decision A)**: stay a separate Edit view, reached by the row's EDIT gear (one module, Params editor, no Browse tab). The pair view has a "Parameters" button that hops there.
- The Perform view with all rows visible has no bound dock any more: badge clicks enter the pair view. The mouse-only bound dock, `dropStaleDockSelection`, and the Edit bay's SRC|FX sub-tab go away.

## Decisions carried over (from the binding plan, 2026-10-06)

- Esc/back from the pair view is one press out of Browse (to Perform), not two; Esc in Params edit closes the row. (Revisit once built; the earlier two-step rule was for the Browse/Params toggle inside Edit.)
- FX tap on a controller with nothing bound: applies like the mouse double-click (active deck, first vacant slot, last slot when full); Shift+tap enqueues. Separate small change.
- Scope/search/cursor memory is per kind (Sources, FX, Transitions), not per context. Separate small change (`BrowserPane.noteHosting`, `ScopeMemory.enter`).
- Badge click binds and moves focus; no more "bind without expanding".
- Superseded: pill-flip-drops-binding (Decision 1) and the bound-Perform-dock MIDI work (Decision 4); the sub-tab they were about is removed.

## Survey findings that shape the plan (agent, 2026-10-06; no code changed)

- Perform deck rows are already separate pinned SRC and FX rows (`PerfRows.CATALOG`, `ui/PerfRows.kt:50-59`); the `[SRC|FX]` KDoc in `PerformanceMatrixPanel` is stale.
- 4 rows x 4 knobs per page; pages ab / bgpv / mixer map to Twister banks 1-3. `PerformPages.resolveInto` writes `knobs[i*4+col]` for any list of rows, so a 2-row result maps to knobs 1-8 unchanged. `PerfRowGeometry`, `PerfKnobResolver`, `drawMatrix`, `ControllerFeedback` need no change.
- The pair's rows come from `CATALOG`, independent of the page; the Twister bank stays where it is (do not auto-switch banks while a knob is held, `ControllerFeedback.syncActiveBank`).
- Edit today: `ViewState.editing = anyRackModuleExpanded && !maximized`; `PerfRows.visibleRowsForPage` returns one row per expanded module (half from the Edit bay sub-tab); `UIManager` hides the Library dock while editing.

## Steps

1. **State.** `ParametersState.focusedPair` (tag: A, B, BG, PV, MASTER, XF) plus a pair table (SRC/FX catalog ids, canonical module id). `ViewState`: pair view = `focusedPair != null`; `dockActive` true in it. Tests: pair table, `ViewState`, BackNavigation unwind.
2. **Rows.** `PerfRows.visibleRowsForPage` returns the pair's two rows when set; add the tag to `RowsCache`'s key. Tests on the cache (no stale rows after a pair switch).
3. **Layout.** Pair view reuses the Edit layout slot: two rows (`gridH` already scales with row count), the Browse dock below, Library dock hidden as in Edit. Add the Back and Parameters buttons.
4. **Knobs.** Replace `ROW_KNOBS = 4` / `isLiveRowKnob` / `browseRowLive` with a live-knob count on `NavSurface` (8 in the pair view, 4 in the Params Edit view, 0 elsewhere). Update `dimForBrowse` (knobs 5-8 must stay lit in the pair view). Bump `browseSession` and reset `lastTouchedKnob` on entering or leaving.
5. **Binding.** The Browse bay binds to the half last touched; delete the sub-tab derivation in `targetForRow`; `dockSelection` survives only as the pair's active target. Delete `dropStaleDockSelection`, `selectDock`'s "bind without expanding" branch, and `openFromMonitor`'s Browse carry-over.
6. **Entry points.** Badge/pill/slot/transition-name clicks, the Twister side button 3 picker, the Library send knobs from the picker, empty-deck launchpad ("Open Library..."), the monitor click: all go to `focusPair`. The row's EDIT gear still opens the Params Edit view (one module).
7. **XF + CLK**: rows only; Browse binds to the transition (`trans`), CLK has no target.
8. **Docs and cleanup.** DECISIONS.md (dock target section rewritten, pair view, stale section 7), both release-notes files, user guide (controls + Library/Browse pages), fix the stale KDoc, delete `ViewState.dockBound`, mark `.planning/unified-browser-step7-handoff.md` stale.
9. Run the full suite, then click through every entry and exit in the app, and with the Twister: badge -> pair -> browse -> apply -> send to another deck -> Esc; Master pair; XF+CLK; Learn on a knob in the pair view; monitor click; switch Twister bank while focused.

## Risks

- Solo-module assumptions in `ParametersState.setDisclosure`, `init` (restores one persisted module), `expandedRackModuleId`, `BackNavigation`: the pair view is a second kind of focus next to a Params Edit module. Keep them mutually exclusive (entering one clears the other) and persist neither across restarts unless wanted.
- `lastTouchedKnob` and `browseSession` can leak partial knob travel across a pair switch (reset on change).
- The Twister bank can show a different page than the focused deck; knobs follow the focused pair, rings must be rewritten for all 16 (feedback path already diffs 16 lights).
- Removing the bound Perform dock removes the quick "bind without leaving the rows" workflow; the owner chose that trade (hidden decks are covered by the monitors).
