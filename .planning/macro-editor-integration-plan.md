# Macro Editor Integration — Implementation Plan

2026-10-03. Goal: retire Column 3's **MACROS** tab and edit macro bindings where they belong. Binding settings go in the
Edit row and on the bound targets in the Deep Edit bay. Column 3 goes back to being just the Mixer. In v1.0 scope as
a usability fix.

All paths below are relative to `src/main/kotlin/llm/slop/liquidlsd/`.

---

## Why

Today, MACROS shares Column 3 with the Mixer (`ui/UIManager.kt:606-612`, `ui/Column3HeaderToggle.kt`). Binding a knob
means juggling three places:
- the Performance row (pick the knob, arm Learn)
- the Deep Edit bay (click the target)
- Column 3 (ranges, curve, link mode)

Six call sites force `column3Mode = MACROS` just to make that work.

Most of `ui/MacroPanel.kt` duplicates things that are already on screen:

| MacroPanel part | Replacement |
|---|---|
| Bank tabs (2 rows, 12 buttons) | The Edit row already selects the bank |
| Knob chips | The row's own knobs |
| Learn banner | Line 1 of the row strip (below) |
| `FxMacroSummary` on FX tabs | Line 1 of the row strip on FX banks |
| Binding Inspector | Row strip line 2 + Properties binding editor + target range arcs |
| Deck monitor quad / big preview | Already in the Mixer; dropped |

## Decisions (2026-10-03)

- **The strip appears in the Edit view only.** Perform view keeps today's knob selection card and the Learn button
  hanging below the knob. Pressing Learn in Perform view still opens that row's Deep Edit, which switches to the Edit
  view, and the strip appears there.
- **GLOBAL knobs can bind to any bindable variable.** This is already true in the binding logic:
  - `MacroLearnState.sectionFor(GLOBAL) == null`, so `acceptsTarget` accepts everything.
  - `onNavigateSection` never cancels Learn for GLOBAL.

  The gap is in the UI. The GLOBAL row is `canExpand = false` (`ui/PerfRows.kt:66`), so it has no Edit view of its
  own. Its strip must therefore ride along on whatever Edit row is open (a *guest strip*, see Phase 2).
- **Geometry rule still holds.** Showing the strip swaps the *content* of the row's two left control lines. Row
  height, knob positions and bay height never change (see `.planning/perf-row-layout-stability-plan.md`).

## Measurements (1280×720, Edit view)

- **Edit row:** ~74px tall (`baseRowH`, `MIN_ROW_H = 68`). About 65px of content height.
- **Control lines:** two, each `ctrlH` 21px compact or `PerformanceColors.CTRL_H` otherwise.
- **Left control area:** the 42px badge + 6px, then `max(deckRow1W, transRowW)`, which comes to ~400–480px.
- **Width needed:** a current inspector binding row needs ~420px. It fits on one control line.
- **Bay:** ~600px tall. Param grid label column is 185px, and Properties is 280–450px wide
  (`ui/PerformanceDeepEditBay.kt:28-35`).

---

## Phase 1 — Binding editor in Properties + range arcs on targets

This phase ships on its own. MACROS still exists, but most binding edits no longer need it.

1. **Extract the per-binding editor** from `ui/MacroBindingInspector.kt` (the enabled checkbox, Min/Max, link
   mode/invert, curve, steps, delete) into a reusable `ui/MacroBindingEditor.kt`.
   - It has two layouts: `drawFull(binding, control, width)` for Properties, and `drawLine(…)` for one control line
     (needed in Phase 2).
   - Min/Max becomes a `CustomRangeSlider.drawMinMaxRangeSlider` with a live position dot. This replaces the two 60px
     `dragFloat`s in the full layout. Keep the drag fields in the line layout because they're narrower.
   - Keep the scratch buffers as fields (no per-frame allocation, same as the inspector today).
2. **Properties → Value section** (`ui/ValueParamSection.kt:124-137`): replace the "Base value controlled by… Click
   to inspect in Column 3" button with a header ("◆ <macro> [badge]") and `MacroBindingEditor.drawFull`, once per
   binding that targets this param's base value. More than one macro can bind the same param, so list each one.
3. **Modulator property sliders** (`ui/CustomRangeSlider.kt:727-770, 848-880`):
   - Draw the selected macro's min→max as brackets on the track.
   - Clicking the macro badge opens a popup containing `MacroBindingEditor.drawFull`. Use a cached popup id; there
     are ~134 existing popup call sites to copy the pattern from.
   - Same for the lfoMin/lfoMax/dcOffset/depth bound labels (`:306-405`).
4. **Param grid value cell** (`ui/ParametersRenderer.kt:321-370`, `drawKnobMeter` `:733`):
   - When the selected macro controls this param, draw its range as an arc in the macro color, plus the live
     position.
   - Params bound to *other* knobs in the same bank get a dim arc.
   - Get "selected macro" from `MacroLearnState.selectedControlId`.
   - Add a small lookup in `MacroEngine` (`bindingsFor(paramKey)` or extend `findPrimaryBindingInfo`) so the grid
     doesn't scan every bank per cell per frame. Cache it, and invalidate it with `MacroEngine.invalidate()`.
5. **Clicking a bound param selects its macro.** Do this at `ParametersRenderer.kt:96-101, 360-367`,
   `CustomRangeSlider.kt:764-768`, `BeatDivisionSlider.kt:200` and `PropertiesPanel.kt:419`:
   - Set `MacroLearnState.selectedControlId`.
   - **Stop setting `column3Mode`.**
   - Select the cell so Properties shows the editor.

## Phase 2 — Row binding strip (Edit view)

1. **New `ui/PerformanceMacroStrip.kt`.** It draws into the same two-line left area that
   `PerformanceDeckControls` / `PerformanceMasterControls` / `PerformanceClockControls` use. Its width is
   `DeckRowMetrics.row1Width` / `transRowW`, and its line height is `ctrlH`.
   - **Line 1:**
     - the macro name (double-click → rename popup that reuses `labelBuf`)
     - the live value
     - Learn/Cancel
     - binding chips ①–④, plus "+" while fewer than `MAX_BINDINGS_PER_CONTROL`
     - ✕ to close
   - **Line 2:** the selected binding in `MacroBindingEditor.drawLine`: target path (ellipsized via `TextFit`), range
     bar, min→max, link/invert, curve, and steps when the curve is STEP. With no bindings it shows the hint
     "Click a parameter or property below to bind".
   - **Chips:**
     - Click selects that binding for line 2 and selects its target cell in the bay. This is today's
       `navTargetFor` + `rackSelectedCell` code at `MacroBindingInspector.kt:170-190`; move it into a shared helper.
     - Tooltip shows the full target path.
   - **FX banks:** line 1 shows the `FxMacroSummary` content. Line 2 is read-only, because FX knobs are
     hard-assigned (`.planning/fx-macro-hard-assign-plan.md`).
   - The selected binding index is UI state. Keep it per control in `MacroLearnState` (e.g. `selectedBindingIdx`) and
     clamp it when bindings are removed.
2. **When it shows** (`ui/PerformanceMatrixPanel.kt` row loop): the view is the Edit view
   (`LibraryPanel.isEditView`), *and* `selectedControlId` belongs to this row's active bank or is a GLOBAL control.
   The row's active bank depends on the SRC/FX pill on deck rows, and on MIX/FX on Master.
   - The row's badge stays.
   - Clicking the badge or ✕ clears `selectedControlId` and restores the controls.
   - Switching the row's SRC/FX pill also clears it, if the selected control belongs to the other bank.
3. **Guest strip for GLOBAL.**
   - If `selectedControlId` is a GLOBAL knob and any Edit row is visible, the strip draws on that row with a
     `COLOR_GLOBAL` "GLB" tag at the start of line 1. The row's own badge stays, so it's clear the strip is visiting.
   - Opening another deck's Edit keeps the strip and keeps Learn armed, since `onNavigateSection` already ignores
     GLOBAL.
   - GLOBAL Learn from the Perform-view Clock row: there is no Deep Edit to open. Keep the user in Perform view and
     show the status "Open any Edit and click a parameter". The guest strip appears once they enter an Edit view.
   - Consider resetting the Learn timeout (`TIMEOUT_MS = 20s`) on entering an Edit view while a GLOBAL Learn is
     armed. Otherwise the navigation can eat the timeout.
4. **Overhang cleanup.** In the Edit view, drop the below-knob Learn button (`OVERHANG_LEARN`), because Learn is on
   line 1. Keep the selection card in both views. Perform view's Learn button stays, minus the
   `column3Mode = MACROS` line (`PerformanceMatrixPanel.kt:214`).
5. Move the Learn tooltips' "and the Mixer panel's Macros tab" wording to the strip. Write new strings so they can be
   pulled into string tables later (localization is v1.1).

## Phase 3 — Remove Column 3 MACROS

1. Delete `ui/Column3HeaderToggle.kt`, `ui/MacroPanel.kt` and `ui/MacroBindingInspector.kt`, after moving
   `navTargetFor` to the shared helper from Phase 2. Also delete `ui/FxMacroSummary.kt` if it was fully absorbed;
   otherwise keep it as the strip's line-1 renderer.
   - Check whether `DeckMonitorGrid.kt` has other users before deleting it.
2. Remove `UITheme.Column3Mode` / `column3Mode` (`ui/UITheme.kt:172-176`) and `AppPreferences.column3Mode`. In
   `AppPreferencesStore.kt:204-207, 337`, drop the read and write. Old settings files keep a stale key that is
   ignored, so no migration is needed.
3. `UIManager.kt:606-612`: Column 3 always draws `mixerPanel`. Give the Mixer back the toggle's ≥28px header row.
4. Rename `PerformanceUiContext.navigateMacroPanelTo` (`:152`). Its job shrinks to "focus the bank's Deep Edit tab".
   Callers: `PerformanceMatrixPanel.kt:177,213`, `PerformanceMasterControls.kt:141,155`.
   - Remove `ParametersState.showGlobalMacros` / `hideGlobalMacros` / `isGlobalMacrosShown` if nothing else reads
     them. They existed only for the GLB tab.
5. Clean up the comments that mention Column 3 or the MACROS tab: `macro/MacroLearnState.kt:1-8`, the
   `PerformanceMatrixPanel.kt:38` KDoc, `PerformanceDeepEditBay.kt:123`, and `PerformanceUiContext.kt:147`.

## Tests

- **Delete** `UIThemeTest.testColumn3ModeDefaultAndToggle`.
- **Retarget** `MacroBindingNavTest` to the shared nav helper.
- **New pure tests (no ImGui):**
  - Strip visibility: (view, row bank, selected control's bank) → strip shown, guest strip, or none. Cover deck SRC,
    deck FX, Master MIX/FX, and GLOBAL.
  - Selected-binding index clamps when bindings are removed.
  - Param→bindings lookup cache invalidates when `MacroEngine.invalidate()` runs.
  - Geometry: `PerfRowLayoutTest` still passes. The strip must not touch `PerfRowGeometry` inputs.
- `LayerDependencyTest` still passes. Keep the new helpers in `ui/` and don't make `macro/` depend on them.

## Docs / release notes

Per the docs/release-notes completeness rule, the work isn't done until all of these are updated:
- `RELEASE_NOTES.md` and `docs/release_notes.md`: MACROS tab removed, binding editing moved into Edit.
- `docs/user_guide/macros_and_rack.md`: rewrite §"Macro Controls (Column 3 MACROS)", §"The Binding Inspector", and
  :92, :103, :118, :128, :281, :355, :372-383. This includes the GLOBAL guest-strip workflow.
- `docs/developer/ui.md`: :94 (GLB tab pin), plus a new paragraph on `PerformanceMacroStrip` and its visibility rule.
- `docs/developer/preset_management.md:175`: where binding edits live.
- `docs/user_guide/presets_and_library.md`: the Column 3 mention.
- Tooltips: every tooltip that says "Column 3" or "Macros tab". `grep -rn "Column 3\|Macros tab"` must come back empty
  under `ui/`.
- `DECISIONS.md` entry for the removal.

## Risks / open items

- **Strip width on the MASTER tab.** The Transitions and Clock rows' left areas differ. The strip should use the
  reserved max width (`maxOf(deckLeftW, masterTabLeftW)`), not the row's own width.
- **Multiple macros on one param.** The arc shows only the selected macro. Other knobs in the same bank are dim, and
  other banks (GLOBAL) aren't shown. The Properties editor lists all bindings, so nothing is hidden.
- **Hardware.** The Twister's focus mode changes knob *selection*? Check that hardware selection doesn't set
  `selectedControlId`. Otherwise strips would appear while performing in the Edit view.
- **Undo.** The inspector's edits aren't on the undo stack today. Keep parity; adding undo is v1.1.
- **Rename UX.** It moves from an always-visible text field to a double-click popup. Also add it to the knob's kebab
  or context menu, if there is one.

## Acceptance (manual, 1280×720)

1. Select Deck A row → Edit → pick K2 → strip shows. Learn → click `fbZoom` in the grid → chip ① appears, an arc
   shows on `fbZoom`, and Properties shows the editor. Dragging the range in Properties updates the arc and line 2
   together.
2. Close the strip with ✕ → deck controls return. Knob positions are identical before and after; compare
   screenshots.
3. Pick a GLOBAL knob on the Clock row → Learn → open Deck B Edit → the guest strip shows GLB → bind a Deck B param →
   open Master Edit → bind a Master FX param. Both chips work.
4. Column 3 shows only the Mixer, and no UI path switches it.
