# D-items Hand-off (v1.0 stability)

You're picking up the v1.0 stability "D-items" in Liquid LSD. The DeckOps/TransitionOps refactor (phases 0-5) is finished and committed. Don't redo or extend it.

## Read first, in this order
1. `.planning/deck-transition-ops-plan.md`: status line, then "Out of scope". That list is the backlog.
2. `docs/developer/ui_interaction_architecture_review.md` §7.2 (defects D1-D17) and §7.4. Defect numbers below refer to it.
3. `DECISIONS.md`, the DeckOps and TransitionOps entries. Rule to keep: deck and transition changes go only through DeckOps/TransitionOps.

## Task 1: D7, stable modulator IDs (saved-format change; do this first, on its own)
Decided 2026-10-04: do it properly before release, not as a stopgap and not in v1.1. Reason: no users yet, so the migration is cheap now; after release every saved preset, session and macro bank would need a permanent migration.

Problem: `MacroBinding.modulatorIndex` (MacroModels.kt) is a position in the parameter's modulator list. Removing or reordering modulators (PropertiesPanel.kt:380-388, "Clear all CVs", MIDI unbind) leaves bindings dropping silently (MacroEngine.kt:340) or driving the wrong modulator.

Proposed design (confirm or improve it):
- Each modulator gets a stable `id` (UUID string, as `MacroControl.id` does). Bindings store `modulatorId`; `ModulatorPropertyAccessor` resolves id to modulator at read time. A binding whose id no longer exists is ignored with a log line (or dropped by the editor).
- Old files: a binding with only `modulatorIndex` is resolved once against the modulator list as saved and given that modulator's id; modulators without an id get one on load. Keep it minimal (no external users; cover your own files and test fixtures).
- Check whether D9 (re-enabling a binding; editors should read `control.bindings`, not the resolved cache) falls out of the same change so it isn't done twice.

Steps:
1. Delegate an inventory to an Explore agent: `modulatorIndex` has about 90 uses in 14 files (MacroEngine, MacroLearnState, MidiMappingManager, OscMappingManager, ModulatorPropertyAccessor, the Lfo/Seq/Audio sections, BeatDivisionSlider, CustomRangeSlider, PropertiesPanel, MacroBindingNav). Classify each as binding identity (must change) or UI/display index (leave).
2. Give the user a short recommendation, then plan.
3. Implement. Round-trip tests with an old-format fixture; tests for removing the first, middle and last modulator and for reordering.
4. Commit-ready on its own before starting task 2.

## Task 2: small v1.0 fixes (each independent)
- D8: MIDI/OSC mapping on a knob-bound parameter does nothing, because `MacroEngine.tick` overwrites `baseValue` every frame. Lock or indicate it, like the existing macro lock.
- D9: only if Task 1 didn't already fix it.
- D10: lock only for base-value bindings.
- D13: Missing Files relink by asset type.
- D14: the toolbar Q uses the visible tab's selection.
- D15: Ctrl+F focuses the active tab's search.
- D16: FX chain `markClean` on every save path, with the DTO captured at confirm time.

Suggested order: D16, then D8/D10 together (related lock logic), D14, D15, D13. Line numbers in the review predate the DeckOps work: grep to confirm each before coding.

## Working rules
- Short recommendation first, then implement. Delegate architecture research to an Explore agent.
- "Done" = code + tests + both release-notes files (`RELEASE_NOTES.md` and `docs/release_notes.md`) + relevant `docs/user_guide` / `docs/developer` pages + tooltips + a `DECISIONS.md` entry when a rule is set. Run `./gradlew test` and `./gradlew generateDocs`; the generated HTML under `src/main/resources/docs` is committed.
- Release-note style: plain user-facing bullets first, then an "Internal:" bullet.
- Never add Co-Authored-By or any Claude attribution to commits or PRs. Commit only when asked.
- Feature freeze: no new features, UI surfaces or settings beyond what a fix needs. Min screen 1280x720; don't change perform-row geometry.
- Update the "Out of scope" list in `.planning/deck-transition-ops-plan.md` as items land (strike them), or start a new plan file if Task 1 outgrows this one.

## Not covered
- Eject and Copy/Move/Swap undo (needs FX chain capture).
- Knob-selection state unification (review §7.3, v1.1).
- MIDI controller phase 5, the profile UI (`.planning/midi-controller-handoff.md`).
