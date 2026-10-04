# Pre-release UI backlog (everything still open from the 2026 UI review)

Written 2026-10-04. Replaces `d-items-handoff.md` and `v1.1-ui-inconsistencies.md`. This is the complete list of scoped work left from `docs/developer/ui_interaction_architecture_review.md` (§7) and the DeckOps/TransitionOps work (`.planning/deck-transition-ops-plan.md`, finished).

**Scope rule (user, 2026-10-04):** everything except wholly new features is pre-release work, and there is no release date pressure, so do it properly now. Format changes must land before release because there are no users yet and every later change would need a permanent migration. Items that look like new features are marked **[decide]**.

## Already done (don't redo)

- DeckOps / TransitionOps refactor, phases 0-5. Rule to keep: deck and transition changes go only through `DeckOps` / `TransitionOps` (see `DECISIONS.md`).
- D7 (stable modulator IDs), see the struck Task 1 below.
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

## ~~Task 1: D7, stable modulator IDs~~ (DONE: 7532631, close-out 2026-10-04)

`MacroBinding.modulatorId` replaces the positional index (`modulatorIndex` is a legacy read field, migrated lazily in `MacroEngine.migrateLegacyModulatorIndex`). Slider mapping paths now always carry the modulator id. Old numeric `:mod/<n>/` MIDI/OSC mappings stay positional until re-learned (no rewrite).

## Open items

### Tier 1: bugs, or likely to surprise mid-show

~~13. **A MIDI/OSC mapping made before a knob binding is silently overridden.** D8 only disables *new* "Learn MIDI/OSC" on a knob-bound parameter. An existing mapping keeps running, and `MacroEngine.tick` rewrites `baseValue` after it every frame. Likely fix: the mapping editor flags it, or the mapping is suspended while the binding exists.~~ **DONE 2026-10-04**: mappings suspended while a knob drives the target, flagged in both tables.
~~14. **Request ordering across `DeckOps` changes.** A preset is read on the IO thread before it is queued; a generator pick is queued immediately. A preset click followed within milliseconds by a generator pick on the same deck applies the generator first and the preset last. Likely fix: a per-slot sequence number, dropping the older request if a newer one has already been applied.~~ **DONE 2026-10-04**
~~8. **FX-bank bindings look editable and get overwritten.** Properties and the slider popups show a full editor (including delete) for FX-bank bindings, but `FxMacroSync` overwrites the edits on the next re-sync, and `MacroUndoTracker` skips FX banks. The Edit strip already treats them as read-only; make the other editors do the same. (Absorbs former item 27.)~~ **DONE 2026-10-04** ("former item 27" is not recorded anywhere; assumed to be the FX-bank Add Target path, which is now blocked)
~~1. **A single `.lsdfx` loaded into a full chain has three outcomes.** FXBrowser double-click silently overwrites slot 1; the audition latch asks which slot to overwrite; the FX queue and FX playlist replace the whole chain (`FxOps.applyItem`). Likely fix: one placement rule in `FxOps`.~~ **DONE 2026-10-04**
~~22. **Macro-bank import validates parameter paths against the file's original deck path before remapping to the target deck** (`MacroBankSerializer.importFromFile` vs `installBankForDeck`). `installBankForDeck` also rewrites any first path segment, including `Mixer/...`.~~ **DONE 2026-10-04**
~~20. **Lock behaviour differs by widget.** `drawMinMaxRangeSlider` never locks Min/Max even when `lfoMin/lfoMax` is bound, so a drag fights the macro; randomize-mode min/max inputs aren't locked either.~~ **DONE 2026-10-04**.
~~6. **"Learn" has three meanings** (macro target, MIDI, OSC). Up to four modes can be armed at once; Esc cancels only two of them (`BackNavigation`). At minimum make Esc cancel all of them.~~ **DONE 2026-10-04**
~~17. **Eject and Copy/Move/Swap are not undoable** (they can reset the deck's FX chain, which isn't captured), although the Preferences tooltip groups them with undoable loads. Either capture the FX chain or reword the tooltip.~~ **DONE 2026-10-04** (tooltip reworded; FX chain not captured)
~~15. **Closing the Video Export window doesn't cancel a pending export.** `VideoExportModal` starts the export from the `DeckOps.request` callback, so an export waiting on the unsaved-changes prompt still starts after the window is closed.~~ **DONE 2026-10-04**

### Tier 2: inconsistencies

~~2. **Target deck rules differ by route.** Preset accept and the queues use the inactive A/B deck. FX double-click, the FX queue and the FX playlist use the live deck. BG and PV can't be targeted from the keyboard or MIDI.~~ **DONE 2026-10-04**
~~3. **Queue double-click and MIDI accept differ.** Double-click moves the queue index, auto-fades and advances the transition queue. MIDI accept just loads.~~ **DONE 2026-10-04** (QUEUE_AB accept calls playIndex)
~~4. **Labels and drop targets are inconsistent.** "Reset Slot" clears the slot while "Reset Parameters" resets values. In the Library "+" means "empty the deck" in Sources but "save current" in FX and Trans.~~ **DONE 2026-10-04**
~~26. **Drop targets differ:** the matrix deck badge takes presets, stock sources and `.lsdfxchain` but not `.lsdfx`; the monitor takes no FX; the ParametersTabs slot drop ignores stock-filter drags that `FxSlotCell` accepts.~~ **DONE 2026-10-04**
~~5. **Seven systems can drive a parameter** (macro bindings, modulator stack, ISF meta-bindings, Super Knob, MIDI, OSC, controller profiles); three write `baseValue` with no priority order. Decide and document a precedence, and surface it where the lock is shown.~~ **DONE 2026-10-04** (precedence documented, Metaknob link owns its uniform, macro refused). Open: lock badge for Metaknob-owned sliders (lock UI is macro-only).
~~7. **Knob selection is stored twice** (`MacroLearnState.selectedControlId`, `ParametersState.selectedRackMacroId`). A parameter click sets only the first, so they can drift. Unify into one.~~ **DONE 2026-10-04**
~~19. **Some sliders can't be bound or learned:** the double-track Min/Max labels drop `paramKey`/`propertyName` (`CustomRangeSlider` ~281-292), and `MidiModulatorSection` passes no `paramKey`. A bound `BeatDivisionSlider` label only selects the knob, with no popup.~~ **DONE 2026-10-04**: bound labels and Beat Division popup fixed; MidiModulatorSection DC Offset/Depth binding decided against
~~18. **Two learn paths for MIDI on a parameter:** a profile mapping (`CustomRangeSlider`) and a `midi_cc_` modulator (`MidiMappingManager`, `GridCell`). Pick one or label the difference.~~ **DONE 2026-10-04** (labels only)
~~21. **A typed knob value ignores curve, invert and link mode** (`MacroKnobWidget` ~438-441 maps it linearly through the first binding).~~ **DONE 2026-10-04**
~~23. **A MASTER knob can bind a Deck parameter** (`sectionFor` returns null for MASTER). Clicking that parameter selects a knob whose strip isn't shown in Deck Edit. `MacroBindingNav.navTargetFor` returns null for non-FX `Master/...` paths, so those chips select but don't jump.~~ **DONE 2026-10-04** (nav needed no change: no live non-FX `Master/...` paths exist)
~~25. **Silent failures and dropped input:** FX and transition load failures only log (`FxOps`); queue deltas from MIDI/CV collapse to one step, so +3 moves one item (`UIManager` ~216-222); the Library's Transitions view has its own hard-coded Q/Enter keys instead of `ShortcutManager` bindings (`LibraryPanel` ~455-469).~~ **DONE 2026-10-04**: toasts, transition and A/B/BG queue deltas (advanceBy), Q shortcut
~~16. **"Don't ask again" in the unsaved-changes prompt** switches manual loads to Discard from one checkbox. Loads are undoable, so it is recoverable, but the label should say so.~~ **DONE 2026-10-04**
9. **Leaving Edit collapses the expanded module**, with no way back to it. Restore the previously expanded module when returning. **OPEN 2026-10-04**: needs a decision on the restore trigger (no new UI in v1.0)
~~24. **Dead code:** GLOBAL has 0 knobs, so its GUEST strip and learn branches (`PerformanceMatrixPanel`, `MacroStripVisibility`) can't be reached. Remove, or keep with a comment tied to the v1.1 free-knob row (`project_global_knobs_row_v11` in memory).~~ **DONE 2026-10-04** (kept, commented)

### Tier 3: Library gaps **[decide]** (some may count as new features)

10. **Transitions can only be saved from the Library** (not from the Mixer TRANS tab or the inline picker).
11. **Macro banks and Perform pages never appear in the Library** (banks export through a raw file browser; Perform pages are edited inside MIDI Preferences).
~~12. **FX favourites exist only in the inline picker**, not in the Library FX browser.~~ **DONE 2026-10-04**

## Suggested order

D7 first, alone. Then, grouped by shared code: 13 + 20 + 5 (lock and precedence logic), 8 + 22 + 23 + 7 (macro editor and bank consistency), 14 + 17 + 15 + 16 + 3 (`DeckOps` follow-ups), 1 + 2 + 26 + 4 (FX and Library load rules), 6 + 19 + 18 + 21 (learn and slider behaviour), 9 + 25 + 24, then the Tier 3 decisions.

## Not covered here

- MIDI controller phase 5, the profile UI: `.planning/midi-controller-handoff.md`.
- FX chain expanded view (shelved): `.planning/fx-chain-expanded-view-notes.md`.
