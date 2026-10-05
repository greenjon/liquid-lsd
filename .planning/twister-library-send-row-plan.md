# Library send -> open the target's row (2026-10-05)

Follows `twister-library-send-plan.md` (send knobs, implemented, uncommitted at time of writing).

## Idea
Move the send knobs to **row three** (knobs 9-12 = A, B, BG, PV; knob **13** = Master FX) and, after a send, show the target's
Perform row above the browser so **knobs 1-4 (row one) play the thing just loaded**. Library and picker become one flow:
browse with knob 16, send with row three, tweak with row one.

## Layout facts (code read 2026-10-05)
- **Library FULL draws no Perform rows at all.** `PerformanceMatrix` is skipped (`UIManager.drawAssetManagementLayout`, `libraryMode != FULL` guard);
  the Library gets the whole content height. Mixer column on the right in every view.
- **HALF**: matrix on top (4 rows, each `max(avail/4, 68px)`; about 80 px at 1280x720), Library below, 0.50 default ratio (clamped 0.15-0.85).
- **Edit view** (`LibraryPanel.isEditView` = a rack module expanded and not FULL): Library height 0; one row (`baseRowH`, same size as in Perform)
  on top, and the Edit bay below (`bayH = availH - gridH - spacing`, row grid capped at `max(avail-160,160)`).
  The bay's BROWSE tab hosts the same unified `BrowserPane` (tree, list, queues) -> `NavigationSurface.inPicker`, `browseRowLive`:
  knobs 1-4 live on that row, knob 16 cursor. **This is already "one row above the browser".**
- Which row is edited: `ParametersState.openGenBrowse(bankId, deckLabel)` (deck SRC row), `openFxChainBrowse(bankId, deckLabel?, slot?)` (deck FX / Master FX
  row, optional slot), `openTransitionBrowse()`. `setDisclosure` makes it the only expanded module and drops FULL to HALF.
- No function draws a single row: row drawing is the inline loop in the private `PerformanceMatrixPanel.drawMatrix` (page, scroll, overhang pool,
  ImGui ids from tab/row index).

## Options
**A. Send opens the picker on the target (recommended).** After `LibraryNavigation.send`, call the matching `open*Browse` for the target, which enters
Edit view with the Browse tab hosting the same pane. Knobs 1-4 are live through the existing `browseRowLive`; knob 16 keeps browsing; the send knobs keep
working in the picker (they re-target: send to another deck and the bay moves to that row). No new layout, no row refactor.
Cost: the Library chrome (FULL's full-height tree/queues) is replaced by the bay's pane; Esc/back leaves Edit to Perform HALF, not back to Library FULL.

**B. Pin a row above Library FULL.** New `pinnedRow` state, drawn above the Library, row-one knobs bound to it. Needs a `drawMatrix` refactor
(single-row entry point, id-collision-safe), a reserved ~80 px in FULL, and changes to `isEditView`/`inLibraryView`/`setDisclosure` coupling (a row's gear or
badge click expands a module, which forces FULL to HALF). More work and more risk (back stack, `lastTouchedKnob`, tests in `PerfRowLayoutTest`,
`PerformSurfaceTest`, `NavigationSurfaceTest`, `RackDisclosureTest`, `WindowLayoutSafetyTest`).

Recommendation: do A now. If, after hardware use, leaving Library FULL feels wrong, B is a layout-only follow-up because A already makes the knob behaviour
and send-then-open flow work.

## Plan (A)
1. **Knob move.** `SendTarget` knobs: A=8, B=9, BG=10, PV=11, MASTER=12 (0-based; knobs 9-12 and 13). Update `SendTarget` doc, `KnobCommands` doc comment, tests.
2. **Send in the picker too.** `NavigationSurface.sendTargets/browseSend` currently require `inLibraryView`; allow `inPicker` (cursor in the list pane;
   `LibraryNavigation.sendAsset` already follows `navMode`, which follows the hosted pane). Row-one knobs and send knobs no longer collide.
   `KnobCommands.press` / `dimForBrowse`: send knobs are checked after the live-row check, which is fine now that they are not knobs 1-4.
3. **Open the target row after a send.** In `LibraryNavigation.send` (or the `NavigationSurface.browseSend` wrapper, which owns `ParametersState`):
   - source/preset -> `parametersState.openGenBrowse(bankId, deckLabel)` (SRC row of that deck);
   - FX chain -> `openFxChainBrowse(bankId, deckLabel, null)`; single FX -> same with the slot it landed in;
   - Master FX -> `openFxChainBrowse(MASTER bank, null, slot)`.
   Use `ctx.canonicalModuleId` / `deckLabelForModuleId` as `openPicker` does. This enters Edit view (drops FULL to HALF); the pane keeps its tab and scope.
4. **Lights.** In the picker `dimForBrowse` lights knobs 1-4 (row colour), knob 16 (white) and live send knobs (target colour); check the colours don't confuse
   with the row (the live row's accent equals its deck's colour; consider a dimmer value for the send knob that matches the open row).
5. **Back.** Verify Esc/back from the picker after a send: collapses Edit to Perform HALF (Library visible in the lower half). Decide if a "return to Library FULL"
   is wanted (open question for hardware use).
6. **Tests.** `NavigationSurfaceTest` (sendTargets in picker, `browseSend` opens the right browse target for source / chain / master), `KnobCommandsBrowseTest`
   (new knob indices; row-one taps still route to `KnobSurface` while a send knob routes to send), existing hosted-pane tests unchanged.
7. **Docs.** Edit the earlier entries in place (not new ones): both release-notes files, user guide Twister section (send knobs on row three, row opens after a send),
   `unified_control_mapping.md`, handoff, `DECISIONS.md` (send knobs entry), `twister-library-send-plan.md` table. Regenerate bundled HTML if the repo workflow does (it did on the last build).

## Open for hardware
- Does Library FULL -> Edit/picker after the first send feel right, or should FULL be preserved (option B)?
- A single FX landing in a full chain overwrites the last slot; with the row now opening on that slot this is visible, which may be enough feedback.
