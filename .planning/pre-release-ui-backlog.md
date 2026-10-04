# Pre-release UI backlog (everything still open from the 2026 UI review)

Written 2026-10-04. Replaces `d-items-handoff.md` and `v1.1-ui-inconsistencies.md`. This is the complete list of scoped work left from `docs/developer/ui_interaction_architecture_review.md` (§7) and the DeckOps/TransitionOps work (`.planning/deck-transition-ops-plan.md`, finished).

**Scope rule (user, 2026-10-04):** everything except wholly new features is pre-release work, and there is no release date pressure, so do it properly now. Format changes must land before release because there are no users yet and every later change would need a permanent migration. Items that look like new features are marked **[decide]**.

## Already done (don't redo)

- DeckOps / TransitionOps refactor, phases 0-5. Rule to keep: deck and transition changes go only through `DeckOps` / `TransitionOps` (see `DECISIONS.md`).
- Review defects D1-D6, D8-D17 (fixed and verified in code on 2026-10-04). D8 is fixed for macro vs MIDI/OSC by locking (`MacroEngine.lockingBindingInfo`). D9 is done (`findBindingInfos(includeDisabled = true)`).
- Follow-ups from the 2026-10-04 review: unique `Untitled_<Deck>` names in the unsaved-changes prompt, session restore taking a dirty baseline through `PresetManager.setActive` (fields are now `private set`).
- Baseline at this point: 926 tests, 0 failures. Nothing in this file has been checked by running the app.

## Working rules

- Short recommendation first, then implement. Delegate architecture research to an Explore agent.
- "Done" = code + tests + both release-notes files (`RELEASE_NOTES.md`, `docs/release_notes.md`) + relevant `docs/user_guide` / `docs/developer` pages + tooltips + a `DECISIONS.md` entry when a rule is set. Run `./gradlew test` and `./gradlew generateDocs`; the generated HTML under `src/main/resources/docs` is committed.
- Release-note style: plain user-facing bullets first, then an "Internal:" bullet.
- Never add Co-Authored-By or any Claude attribution to commits or PRs. Commit only when asked.
- No new UI surfaces or settings beyond what a fix needs. Min screen 1280x720; don't change perform-row geometry.
- Items 1-27 below were written from review text and an agent trace. **Check each against the code before planning it**; line numbers have drifted.
- Strike items here as they land.

## Task 1: D7, stable modulator IDs (saved-format change; do first, on its own)

Problem: `MacroBinding.modulatorIndex` (`MacroModels.kt`) is a position in the parameter's modulator list. Removing or reordering modulators (`PropertiesPanel.kt` ~380-388, "Clear all CVs", MIDI unbind) leaves bindings dropping silently (`MacroEngine.resolveControls`) or driving the wrong modulator.

Proposed design (confirm or improve it):
- Each modulator gets a stable `id` (UUID string, as `MacroControl.id` does). Bindings store `modulatorId`; `ModulatorPropertyAccessor` resolves id to modulator at read time. A binding whose id no longer exists is ignored with a log line (or dropped by the editor).
- Old files: a binding with only `modulatorIndex` is resolved once against the modulator list as saved and given that modulator's id; modulators without an id get one on load. Keep it minimal (no external users; cover your own files and test fixtures).

Steps:
1. Delegate an inventory to an Explore agent: `modulatorIndex` has about 90 uses in 14 files (MacroEngine, MacroLearnState, MidiMappingManager, OscMappingManager, ModulatorPropertyAccessor, the Lfo/Seq/Audio sections, BeatDivisionSlider, CustomRangeSlider, PropertiesPanel, MacroBindingNav). Classify each as binding identity (must change) or UI/display index (leave).
2. Give the user a short recommendation, then plan.
3. Implement. Round-trip tests with an old-format fixture; tests for removing the first, middle and last modulator and for reordering.
4. Commit-ready on its own before starting anything else.

## Open items

### Tier 1: bugs, or likely to surprise mid-show

13. **A MIDI/OSC mapping made before a knob binding is silently overridden.** D8 only disables *new* "Learn MIDI/OSC" on a knob-bound parameter. An existing mapping keeps running, and `MacroEngine.tick` rewrites `baseValue` after it every frame. Likely fix: the mapping editor flags it, or the mapping is suspended while the binding exists.
14. **Request ordering across `DeckOps` changes.** A preset is read on the IO thread before it is queued; a generator pick is queued immediately. A preset click followed within milliseconds by a generator pick on the same deck applies the generator first and the preset last. Likely fix: a per-slot sequence number, dropping the older request if a newer one has already been applied.
8. **FX-bank bindings look editable and get overwritten.** Properties and the slider popups show a full editor (including delete) for FX-bank bindings, but `FxMacroSync` overwrites the edits on the next re-sync, and `MacroUndoTracker` skips FX banks. The Edit strip already treats them as read-only; make the other editors do the same. (Absorbs former item 27.)
1. **A single `.lsdfx` loaded into a full chain has three outcomes.** FXBrowser double-click silently overwrites slot 1; the audition latch asks which slot to overwrite; the FX queue and FX playlist replace the whole chain (`FxOps.applyItem`). Likely fix: one placement rule in `FxOps`.
22. **Macro-bank import validates parameter paths against the file's original deck path before remapping to the target deck** (`MacroBankSerializer.importFromFile` vs `installBankForDeck`). `installBankForDeck` also rewrites any first path segment, including `Mixer/...`.
20. **Lock behaviour differs by widget.** `drawMinMaxRangeSlider` never locks Min/Max even when `lfoMin/lfoMax` is bound, so a drag fights the macro; randomize-mode min/max inputs aren't locked either.
6. **"Learn" has three meanings** (macro target, MIDI, OSC). Up to four modes can be armed at once; Esc cancels only two of them (`BackNavigation`). At minimum make Esc cancel all of them.
17. **Eject and Copy/Move/Swap are not undoable** (they can reset the deck's FX chain, which isn't captured), although the Preferences tooltip groups them with undoable loads. Either capture the FX chain or reword the tooltip.
15. **Closing the Video Export window doesn't cancel a pending export.** `VideoExportModal` starts the export from the `DeckOps.request` callback, so an export waiting on the unsaved-changes prompt still starts after the window is closed.

### Tier 2: inconsistencies

2. **Target deck rules differ by route.** Preset accept and the queues use the inactive A/B deck. FX double-click, the FX queue and the FX playlist use the live deck. BG and PV can't be targeted from the keyboard or MIDI.
3. **Queue double-click and MIDI accept differ.** Double-click moves the queue index, auto-fades and advances the transition queue. MIDI accept just loads.
4. **Labels and drop targets are inconsistent.** "Reset Slot" clears the slot while "Reset Parameters" resets values. In the Library "+" means "empty the deck" in Sources but "save current" in FX and Trans.
26. **Drop targets differ:** the matrix deck badge takes presets, stock sources and `.lsdfxchain` but not `.lsdfx`; the monitor takes no FX; the ParametersTabs slot drop ignores stock-filter drags that `FxSlotCell` accepts.
5. **Seven systems can drive a parameter** (macro bindings, modulator stack, ISF meta-bindings, Super Knob, MIDI, OSC, controller profiles); three write `baseValue` with no priority order. Decide and document a precedence, and surface it where the lock is shown.
7. **Knob selection is stored twice** (`MacroLearnState.selectedControlId`, `ParametersState.selectedRackMacroId`). A parameter click sets only the first, so they can drift. Unify into one.
19. **Some sliders can't be bound or learned:** the double-track Min/Max labels drop `paramKey`/`propertyName` (`CustomRangeSlider` ~281-292), and `MidiModulatorSection` passes no `paramKey`. A bound `BeatDivisionSlider` label only selects the knob, with no popup.
18. **Two learn paths for MIDI on a parameter:** a profile mapping (`CustomRangeSlider`) and a `midi_cc_` modulator (`MidiMappingManager`, `GridCell`). Pick one or label the difference.
21. **A typed knob value ignores curve, invert and link mode** (`MacroKnobWidget` ~438-441 maps it linearly through the first binding).
23. **A MASTER knob can bind a Deck parameter** (`sectionFor` returns null for MASTER). Clicking that parameter selects a knob whose strip isn't shown in Deck Edit. `MacroBindingNav.navTargetFor` returns null for non-FX `Master/...` paths, so those chips select but don't jump.
25. **Silent failures and dropped input:** FX and transition load failures only log (`FxOps`); queue deltas from MIDI/CV collapse to one step, so +3 moves one item (`UIManager` ~216-222); the Library's Transitions view has its own hard-coded Q/Enter keys instead of `ShortcutManager` bindings (`LibraryPanel` ~455-469).
16. **"Don't ask again" in the unsaved-changes prompt** switches manual loads to Discard from one checkbox. Loads are undoable, so it is recoverable, but the label should say so.
9. **Leaving Edit collapses the expanded module**, with no way back to it. Restore the previously expanded module when returning.
24. **Dead code:** GLOBAL has 0 knobs, so its GUEST strip and learn branches (`PerformanceMatrixPanel`, `MacroStripVisibility`) can't be reached. Remove, or keep with a comment tied to the v1.1 free-knob row (`project_global_knobs_row_v11` in memory).

### Tier 3: Library gaps **[decide]** (some may count as new features)

10. **Transitions can only be saved from the Library** (not from the Mixer TRANS tab or the inline picker).
11. **Macro banks and Perform pages never appear in the Library** (banks export through a raw file browser; Perform pages are edited inside MIDI Preferences).
12. **FX favourites exist only in the inline picker**, not in the Library FX browser.

## Suggested order

D7 first, alone. Then, grouped by shared code: 13 + 20 + 5 (lock and precedence logic), 8 + 22 + 23 + 7 (macro editor and bank consistency), 14 + 17 + 15 + 16 + 3 (`DeckOps` follow-ups), 1 + 2 + 26 + 4 (FX and Library load rules), 6 + 19 + 18 + 21 (learn and slider behaviour), 9 + 25 + 24, then the Tier 3 decisions.

## Not covered here

- MIDI controller phase 5, the profile UI: `.planning/midi-controller-handoff.md`.
- FX chain expanded view (shelved): `.planning/fx-chain-expanded-view-notes.md`.
