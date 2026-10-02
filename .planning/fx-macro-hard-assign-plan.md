# FX macro knobs: hard-assigned (Mixxx-style) — plan

Status: implemented 2026-09-30 (Dry/Wet on header, Metaknob on focus knob 1). Follows the Macros-tab monitor/Super Link change (same day).

## Goal
FX banks (`deckA_fx`, `deckB_fx`, `deckBG_fx`, `deckPV_fx`, `masterFx`) stop being freely bindable 4-binding knobs. Each has exactly two fixed modes:

| Mode | Knob 1 | Knobs 2-4 |
|---|---|---|
| Group (no slot focused) | Super | Metaknob of slot 1 / 2 / 3 |
| Focus (one slot focused) | focused slot's Metaknob | focused slot's top 3 ISF params (page*3+k) |

Dry/Wet in focus mode moves to the chain header (already planned: "focus knob1=dry/wet" in the FX performance editing plan — reconcile with that before building; if knob 1 must stay Dry/Wet, Metaknob goes on the header instead).

The per-slot Metaknob (`ISFFilter.metaKnob` + multi-binding `metaBindings`, `ISFAutoBindEngine`) is unchanged and remains the only place FX bindings (min/max/curve/invert) are edited.

## Current state (verified 2026-09-30)
- `FxMacroSync` auto-fills knobs with one binding each; an "ownership rule" (`OWNED_PATH_PATTERN`, `isOwnedOrEmpty`, `forceResync`) lets users rebind and resyncs only untouched knobs.
- Focus mode today: knob 0 = `FX{n}/DryWet`, knobs 1-3 = params. No Metaknob in focus mode.
- Linked slots have bank-knob bindings cleared; `MacroEngine.syncLinkedFxChainKnobValues` mirrors `metaKnob.baseValue` into knob value each tick.
- MIDI/OSC: `Macro/<bankId>/knob_1..4` paths; `MidiMappingManager` (~L415-445, regex `knob_([1-4])`, `isFxMacroSyncOwnedKnobPath` L23).

## Changes
1. **Model**: keep `MacroBank` objects and the 4 knob paths for FX banks (hardware mappings and perf-matrix widgets keep working). Mark FX banks as "fixed" (e.g. `MacroBank.fixed` or a check via `FxMacroSync.FX_BANK_IDS`) so nothing user-edits their bindings.
2. **`FxMacroSync`**: replace ownership logic with an unconditional rewrite on every sync (mode/focus/link/slot-load change). Delete `OWNED_PATH_PATTERN`, `isOwnedOrEmpty`, `forceResync` (or make `forceResync` the only path). Focus mode: knob 1 -> `FX{n}/Meta`.
3. **Migration**: on session load (`SessionSerializer` ~L259-297, which already runs `syncAll`), force-rewrite FX banks so stale user rebindings and extra bindings are discarded. Accept the loss (v1.0 polish; FX-bank rebinds are rare). Note it in release notes.
4. **Macros tab UI** (`MacroPanel.drawFxRackView`): for FX pages replace the chip row + Binding Inspector with a read-only mapping summary (what each of the 4 knobs controls in the current mode) plus an editable view of the selected slot's Metaknob `metaBindings`. Keep Binding Inspector for deck/master/transition/global banks. Remove `MacroBindingInspector.linkedSuperKnobBindingsFor`.
5. **MIDI**: drop `isFxMacroSyncOwnedKnobPath` special case; FX knob dispatch is uniform.
6. **Perf matrix**: `PerfKnobResolver`/`MacroKnobWidget` read labels from the bank, so they follow automatically. Verify labels (SUPER/META/META/META; focus: META + 3 param names) and that Learn on these knobs is disabled or routes to the Metaknob's bindings rather than the bank.
7. **Focus retargeting of physical knobs**: fixed by construction (knob meaning is fixed per mode), but a CC bound to knob N still changes meaning when focus toggles — that is intended Mixxx behaviour; document it. Closes the open tradeoff in `project_fx_isf_automap_macro_hierarchy`.

## Tests
- Rewrite: `FxMacroSyncTest` (drop ownership/forceResync tests L122-173; add group-mode and focus-mode fixed mapping, migration-discards-rebind).
- Update: `FxFocusModeTest` (knob 1 = Metaknob), `MacroMidiIntegrationTest`, `MacroKnobWidgetTest` if they assert FX knob paths/labels.
- Unchanged: `FxChainSuperKnobTest`, `ISFAutoBindEngineTest`, `FxMetaBindingTest`, `FXPresetSerializationTest`, `FxOpsTest`, `PerformanceControlsParityTest` (FX chain presets don't store macro banks).
- Note: full `./gradlew test` exceeded 10 minutes on 2026-09-30; run targeted classes (`--tests`).

## Docs (definition of done)
`RELEASE_NOTES.md` + `docs/release_notes.md` (incl. migration note), `DECISIONS.md`, `docs/user_guide/macros_and_rack.md` (FX Rack section), `docs/user_guide/performance_controls.md`, tooltips on the read-only summary, `.planning/codebase/ARCHITECTURE.md` macro lines.

## Risks / open questions
- Dry/Wet placement in focus mode (see Goal) — decide before step 2.
- Users who rebound FX knobs lose that on load (accepted).
- Standalone `.knobpreset.json` export of FX banks becomes meaningless; hide the export action for FX banks.
- Whether Learn on an FX perf knob should be blocked or forwarded.

## Order
2 -> 1 -> 3 (sync + tests), then 5, 6, then 4 (UI), then docs.
