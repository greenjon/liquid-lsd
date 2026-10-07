# Dock binding: one source of truth (plan)

2026-10-06. Follow-up to the Library/Browser/Edit lifecycle review. Already done and committed (f223f96, 7c04b19, 3cad2c6):
every route out of Edit ends the binding, the Edit bay chip has the unbind X + two-step Esc, a re-click re-syncs a paused tab,
Twister sends from Library FULL only load. This plan is the remaining structural fix. Not started.

## The problem

"What is the dock bound to" is stored twice and the two copies can disagree:

| Reader | Reads |
|---|---|
| Edit bay Browse pane (`PerformanceBrowseBay.targetForRow`, `draw`) | the row's **sub-tab pill** (deck SRC\|FX, Master TRANS\|FX) for the kind, and `dockSelection` only for the FX slot index |
| Perform/Library dock (`PerformanceMatrixPanel` line ~282), `DockOutline`, Esc (`BackNavigation`), `openBrowseTab`, `openFromMonitor` | `ParametersState.dockSelection` |

The pill is writable from many places that know nothing about the binding: the Params pills, `PerformanceUiContext.focusDeepEditTab`
(Learn on an FX knob flips the pill to FX), `setDeckSubTab` from `selectGen/selectFxChain`, and every `open*Browse`.

Concrete divergence: `dockSelection = Deck A / FxChain(slot 2)`, then Learn on a Deck A source knob flips the pill to SRC.
The Edit bay now shows the Gen list (pill wins), while the outline and the chip logic still believe FX slot 2 is selected.
Same with `openBrowseTab`: the Browse tab opens on whatever the pill says, which is not visible while in Browse.

A second, smaller inconsistency: `openBrowseTab` and `openFromMonitor` each re-derive a `BrowseTarget` from pill + remembered slot.

## Proposal

Invariant: **while a module's Edit bay is in Browse, `dockSelection` is non-null, is for that module, and is what the bay binds to.**
The pill is a view of the row (which half is on screen), never an input to the bay's binding after the moment of opening.

1. **Derive once, at the explicit action.** Keep `openBrowseTab(moduleId, deckLabel)` as the only place that turns pill + remembered slot into a
   `BrowseTarget` (the user clicked the Browse tab; the pill is visible on the row at that moment). It already ends in `openBrowse`, which sets `dockSelection`.
   `openFromMonitor` in BROWSE mode should call it too instead of its own `when`.
2. **Bay binds from the selection.** `PerformanceBrowseBay.targetForRow` becomes: `dockSelection` if its module matches, else (defensive) `openBrowseTab`-style derivation.
   Delete the pill branch. `draw` keeps the Master-on-MIX fallback to Params.
3. **Keep selection and pill in agreement at the state layer, not the panel.** Move the rule now in `PerformanceMatrixPanel.dropStaleDockSelection`
   ("selection's half is no longer the row's active half -> drop it") into `ParametersState`: `setDeckSubTab` / the `activeMixerSubTab` setter clear a
   selection for that module whose target belongs to the other half. Then Learn-flips-the-pill drops the binding in Edit too, and the bay falls back to
   Params (via the existing null-binding path) instead of silently showing a different list. Wrapper functions `select*`/`open*Browse` set pill first, then
   selection, so they keep working. Needs `activeMixerSubTab` to become a property with a setter (it is a plain var today).
4. **Edit-bay Esc/chip rules need no change** (already via `onEnd`), but the Esc stack's "dockSelection != null && !anyRackModuleExpanded" test can then be
   stated as "dockSelection != null" for the Perform/Library part only.
5. **Remove the pill flip as a *side effect of opening a browse*?** No. The flip is what puts the bound slot on screen in Perform (selecting FX slot 2 must show
   the FX half). Leave it; it is now covered by step 3's rule being consistent with it.

## Decisions made

- **Pill moves to the other half while Edit is on Browse -> clear the binding, bay falls back to Params** (option A, 2026-10-06). Same rule Perform already applies.
- **Badge click** (`selectDock`): keep today's rule -- it binds the dock, and also moves the open Edit bay to that row in Browse when an Edit bay is already open (option B, 2026-10-06). No code change; state the rule in DECISIONS.md when step 5 lands.
- **FX tap on a controller, nothing bound** (`browser/DockActions.kt`): tap applies, like the mouse double-click; Shift+tap enqueues (option B, 2026-10-06). Separate small change, not part of the binding refactor: the active deck, first vacant slot (last slot when full, no popup), and a chain replaces the chain. Update the user guide Picker/Library paragraphs when it lands.
- **Bound Perform dock is controllable from the Twister** (option A, 2026-10-06; chosen over the v1.1 deferral I recommended). `ViewState.dockActive` becomes true for a bound Perform dock, so the browse knobs and side buttons work as in the Edit picker. Open design point before building: Perform's knob 16 is a row knob there, so decide what carries the cursor (a modal takeover while bound, or a different knob) -- ask before implementing. It touches the v1.0 feature freeze; confirm it is in scope.

## Out of scope / separate

- Perform with a bound dock is mouse-only on the Twister (`browsing` false). Needs a product decision: make `ViewState.dockActive` true for a bound Perform
  dock, or accept it. Not a state-model change.
- FX accept divergence: MIDI tap enqueues, mouse double-click applies to the live deck (`DockActions`). Decide the intended meaning first.
- Badge-click meaning (navigate in Perform vs rebind in Edit, `selectDock`): fix after this, it depends on the invariant above.
- Cursor/scope reset on hosting change (`BrowserPane.noteHosting`, `ScopeMemory.enter`) still drops selection and cursor on every context change.
- Docs cleanup: `DECISIONS.md` section 7 still describes a queue column in the pane (queues are the `QUEUES` dock tab) and audition (removed in 4eed340);
  `.planning/unified-browser-step7-handoff.md` is stale (classic path is already deleted); `ViewState.dockBound` has no readers.

## Steps

1. Tests first (`ParametersStateMonitorTest`/`RackDisclosureTest` style, no GL): selection FxChain(2) + pill flipped to SRC -> selection cleared;
   Master FX selection + `activeMixerSubTab = "TRANS"` -> cleared; `openBrowseTab` on pill FX with remembered slot -> FxChain(slot); Browse bay
   target equals `dockSelection.target` whenever the module matches.
2. `activeMixerSubTab` setter + pill-vs-selection rule in `ParametersState`; delete `dropStaleDockSelection` from the panel.
3. `targetForRow` reads the selection; `openFromMonitor` BROWSE branch calls `openBrowseTab`.
4. Run the full suite; run the app and click through: Perform bind -> open Edit -> Params pill flip -> Browse; Learn on an FX knob while Browse is open;
   Master TRANS|FX flips; monitor click in Browse; Twister send from the picker.
5. DECISIONS.md section on the dock selection (state the invariant), both release-notes files (internal-only line plus the user-visible "no more stale list" fix),
   and the stale-docs cleanup above.

## Risks

- `activeMixerSubTab` is persisted/read in many places (`ParametersState.kt:336-340` region, Perform pages); a setter must stay cheap and idempotent.
- Step 3 changes what Learn on an FX knob does while Browse is open (bay drops to Params instead of showing the pill's list). That is arguably the point, but check it feels right.
- `openFromMonitor`'s BROWSE branch deliberately carries the *current* target type across modules; confirm `openBrowseTab` reproduces that for deck-to-deck and deck-to-Master moves before deleting it.
