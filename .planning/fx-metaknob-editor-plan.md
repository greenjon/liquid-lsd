# FX slot Metaknob editor + knob layout (plan, 2026-10-13)

Status: IMPLEMENTED 2026-10-14 (steps 1-9, all committed; full suite green; strip, Edit bay and Knobs list not yet exercised on hardware). Scope: **v1.0** (owner confirmed 2026-10-13).
Deviations: Add Target in the strip is a menu of unlinked parameters (not click-to-link); no drag-reorder in the Knobs list (up/down buttons); `drawFocusedMode` was kept and now follows the knob order; focus-knob re-sync happens only when `focusParameters` changed.

## Problem

Source rows have a real binding editor (`MacroBindingEditor`: enable, link mode, curve, range bar, min/max, delete, Add Target). FX slots have the same concepts
(`FxMetaBinding`: target, min/max, curve, invert, link mode, enabled) but the only UI is a right-click submenu in the Edit bay's Parameters tab
(`ParametersTabs.kt:673-706`) that sets link mode + invert. Min, max, curve and enabled cannot be edited anywhere.
In Perform focus mode, knobs 2-4 are just the shader's float inputs in declaration order (`ISFFilter.focusParameters`), paged by 3, minus parameters the Metaknob owns.
There is no way to add, remove, reorder or hide what sits on those knobs, and the Perform/Edit rows show only "Slot n parameter (fixed assignment)".

Decisions already made (owner, 2026-10-13):
- Copy semantics stay: a saved slot/chain bakes its bindings; editing one chain never changes another or the stock effect. No live references.
- Same editing surface as macros: link, response curve, range, shown as a list, not a menu.
- Linked parameters may stay available on the knobs. Resolved (owner delegated, 2026-10-13): the default layout still leaves Metaknob-owned parameters off (they are inert, since `MacroEngine.tick` skips them); the Knobs editor can add them back and they then show the lock state.
- Curves: full parity with source rows (LINEAR, EXPONENTIAL, LOGARITHMIC, S_CURVE, STEP + step count).
- The editor appears in both the Edit-row strip and the Edit bay.

## Design

### A. Metaknob binding editor (the main piece)
- New `ui/FxMetaBindingEditor.kt`, modelled on `MacroBindingEditor.drawLine` / `drawFull`. Do not convert meta bindings to `MacroBinding` (it collides with the sync-owned FX banks).
- Extract the generic drawing helpers (`drawRangeBar`, min/max fields, drag speed) from `MacroBindingEditor` into a small shared object taking `(min, max, lo, hi, live, enabled)` plus setters, so both editors look identical.
- `FxMetaBinding` is immutable: edits replace the element in `ISFFilter.metaBindings[idx]` via `copy()`.
- Line content: enabled checkbox, target label, `LinkModeButton.drawMetaLink`, curve combo (all five, plus a step-count field for STEP), range bar with live dot (`binding.mapKnobToTarget(slot.metaKnob.value)`), min/max, delete.
- Where it lives: `PerformanceMacroStrip` line 2, the `isFx ->` branch (`:148`). For a Slot Metaknob knob, show the slot's binding list as chips (same pattern as `:116-131`) with the selected binding's editor line; add "Add Target" (reuses the learn flow: click a parameter in the Edit bay). The Super Knob stays read-only.
- Mirror the list in the Edit bay under the slot's Metaknob row (`ParametersTabs.drawFxChainContent`) so mouse users find it in either place. Keep the right-click menu as a shortcut.
- Replace `FXChainMacroStrip.drawRebindMenuItems` "Retarget" semantics: it currently wipes the whole list to one target. Make it "Add target / Replace all" with the wipe explicit, or remove it once the list editor exists.
- All mutations go through one helper `FxMetaEdit.apply(chain, slot, edit)` that: pushes a real undo, mutates, then calls `FxMacroSync.syncFor` (see risks A and B).

### B. Knob layout
- New optional per-slot field: `knobLayout: List<String>?` (ordered parameter names shown on focus knobs 2-4 and later pages; null = authored order = today's behaviour). Hidden parameters are simply absent from the list.
- `ISFFilter.focusParameters` becomes: layout order (dropping names no longer in the shader) else declared order; cache the result (it allocates per call today, risk E).
- Editor: a "Knobs" section in the slot's Edit area: ordered list with reorder (drag or up/down), hide/show, and a page divider every 3. Perform focus mode keeps its current look; only content changes (row-geometry stability rules in `perf-row-layout-stability-plan.md` still apply).
- Metaknob ownership vs the layout: see open question 1.

### C. Persistence
- `FXSlotDto`: add `knobLayout: List<String>? = null` (optional, nullable, no migration needed: `ignoreUnknownKeys`, DECISIONS convention). `FXSlotDto` is also used by transitions, `.lsdfx` single-slot presets and the slot clipboard; the field must stay optional/null there. `ISFFilter.copy()` (`:613`) must copy it.
- `FxDefaultDto` (Save as Default): add the same optional field; "Reset to Factory Default" clears it. Stale names dropped on load.
- Dirty tracking stays "structure only" (`computeIsDirty` compares filter ids), so layout/binding edits do not mark a chain dirty; same as bindings today.

### D. Cleanup that falls out
- `FXChainMacroStrip.drawFocusedMode` shows `take(3)` of `header.INPUTS`, independent of `focusParameters` and paging (risk D). Make it follow `focusParameters` or delete it if unreachable.
- Stale docs: `DECISIONS.md` section 4 ("no hand-retargeting") and `:188` ("lock UI not done"); `preset_management.md:200-201` mentions only singular `metaBinding`; ROADMAP Milestone 4 row.

### A2. Curve parity
- Extend `MetaCurve` with LOGARITHMIC, S_CURVE, STEP (same names as `MacroCurveType`, so the DTO string already round-trips) and add `stepCount: Int` (default 8, matching `MacroBinding`) to `FxMetaBinding` + `FxMetaBindingDto` (optional, default).
- Verified: `FxMetaBinding` swaps endpoints when inverted, `MacroCurve.mapToRange` flips `t` after shaping; both equal `min + (1-s)(max-min)` for any curve, so they are numerically identical. Link-mode windowing is also identical.
- So `mapKnobToTarget` should call `MacroCurve.window` and `MacroCurve.shape` (allocation-free, takes enums; map `MetaLinkMode`/`MetaCurve` to the macro enums with a `when`, no lookups per frame). This removes the duplicated math and makes DECISIONS' claim true.
- `knobForTarget` (called only at load/reset, not per frame) needs the inverse for the new curves; reuse `MacroCurve.inverse` via a temporary `MacroBinding` rather than re-deriving S_CURVE/STEP. STEP and piecewise link modes are best-effort, as for macros.
- Tests: golden values per curve/link-mode/invert comparing `FxMetaBinding.mapKnobToTarget` with `MacroCurve.mapToRange`; DTO round trip of the new fields; old DTOs without `stepCount` load.

## Risks (from the code survey)
- **A. Stale knobs.** The right-click Metaknob Link menu and the Retarget/Reset menu change `metaBindings` without calling `FxMacroSync`, so focus knobs and page counts can go stale. The new helper centralizes mutate-then-resync, and the old call sites should use it.
- **B. Undo.** `onPushUndo()` after meta edits pushes only a modulator snapshot and does not restore bindings. Use `ParametersUndo.pushUndoState(state, mixer) { restore }` with a captured `toFxSlotDto`. Avoid `FxOps.applySlot` for undo (async swap dip flashes the slot); restore in place then resync. Coalesce drags into one step.
- **C. Shared DTO.** See Persistence.
- **E.** `FxMetaBinding` is immutable and `focusParameters` allocates per call; cache and invalidate on edit.
- **F.** `setParamLink` only seeds min/max for new bindings; link-mode changes keep them (fine, but the editor must show the real values).
- **First binding is special.** `ISFFilter.metaBinding` (singular) returns `metaBindings.first()` and its setter replaces the list; `knobForTarget`/`reset()` use the first binding. Reordering bindings changes the Metaknob's reset position. Do not offer reordering in v1, or document it.
- New curves need the inverse too; handled in A2.

## Open questions
None blocking. (Resolved 2026-10-13: owned parameters stay off the default layout; full curve parity; strip and Edit bay both.)

## Steps
1. `FxMetaEdit` helper (mutate + resync + real undo) and route the existing menu call sites through it. Tests: resync after link change, undo restores bindings.
2. Curve parity (A2): extend `MetaCurve`, add `stepCount`, delegate to `MacroCurve`, tests.
3. Extract shared range-bar/min-max drawing; write `FxMetaBindingEditor`; unit test the pure parts if any.
4. Strip integration in `PerformanceMacroStrip` (chips, editor line, Add Target); extend `MacroStripVisibilityTest` if the mode set changes.
5. Edit-bay mirror in `ParametersTabs`; retire/rewrite Retarget.
6. `knobLayout`: field, `focusParameters`, `FxChain.totalParamPages`, `PerfKnobResolver`, DTO + `ISFFilter.copy()` + `FxDefaultDto`. Tests: round trip in `FxMetaBindingTest`, `FxFocusModeTest`, `PerformSurfaceTest`, stale-name drop, default save/reset.
7. Knobs editor UI.
8. Remove/repair `drawFocusedMode`.
9. Docs ("done" = code + both release-notes files + user guide `macros_and_rack.md` + `performance_controls.md` + `docs/developer/ui.md` + `preset_management.md` + DECISIONS + ARCHITECTURE + tooltips), then `./gradlew generateDocs --offline -q` and the full test suite.

Order: 1 first (it fixes the stale-knob bug on its own), then 2 (curves), 3-5 (editor), then 6-8 (layout). Steps 1-5 are shippable without the layout.
