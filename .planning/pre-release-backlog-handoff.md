# Handoff: pre-release UI backlog (state at 2026-10-04, end of session)

Read first: `.planning/pre-release-ui-backlog.md` (the list; struck items are done), then this file. Working rules are in the backlog's "Working rules" section and in `CLAUDE.md`/memory; the short version is below.

## Done this session (all committed by the user unless noted)

| Item | What | Notes |
|---|---|---|
| D7 stable modulator IDs | Already in HEAD (`7532631`); close-out committed | Slider mapping paths now only use `modulatorId`. Old numeric `:mod/<n>/` MIDI/OSC mappings stay positional until re-learned (no rewrite). |
| 20 | Min/Max range sliders, randomize-mode boxes and `ValueParamSection` pills/combos lock when a macro drives them | `CustomRangeSlider.minMaxLock`, `renderInternalDualSlider(locked=)` |
| 13 | MIDI/OSC mappings are suspended while a macro knob drives the target; "Suspended: driven by ..." caption in both mapping tables | `MacroEngine.isMappingTargetLocked / mappingSuspendReason / metaOwnerOf` |
| 5 | Precedence documented (macro > Metaknob link > direct edit/MIDI/OSC; modulators always add on top). Metaknob link owns its uniform: macro skipped in `tick`, refused in `bindTarget` | `ModulatableParameter.metaDrivenBy`, set per frame by `ISFFilter.applyMetaKnobBinding`. Open: no lock badge on sliders for Metaknob-owned params (lock UI is macro-only). |
| 7 | One selected knob (`MacroLearnState.selectedControlId`); `ParametersState.selectedRackMacroId` removed | |
| 22 | Bank import validates after remapping to the target deck; `remapDeckPath` leaves `Mixer/Master/Global/Macro` alone | |
| 23 | MASTER/TRANS accept `Mixer/` only, FX_SENDS `/FXChain/` only (`acceptsTarget`); `sectionFor` deliberately unchanged | |
| 8 | FX-bank knobs/bindings read-only everywhere (`FxMacroSync.isFxBank`); `acceptsTarget` rejects FX banks | "Former item 27" was never found in any file; assumed to be the FX-bank Add Target path. Ask the user if it matters. |

Last full run: `./gradlew test` green (about 930 tests, 0 failures). Nothing has been checked by running the app. Docs for all of the above (both release-notes files, user guide, `DECISIONS.md`, generated HTML) are done.

## Next group (from the backlog's "Suggested order")

`DeckOps` follow-ups: **14, 17, 15, 16, 3**. Then: 1 + 2 + 26 + 4 (FX and Library load rules), 6 + 19 + 18 + 21 (learn and slider behaviour), 9 + 25 + 24, then the Tier 3 **[decide]** items (10-12) which need a user decision before any work.

Suggested first step for 14/17/15/16/3: delegate an Explore agent to verify each item against current code (line numbers in the backlog drifted), report, give the user a short recommendation, then implement. Rule to keep: deck and transition changes go only through `DeckOps` / `TransitionOps` (see `DECISIONS.md`).

## Workflow the user expects (from memory/CLAUDE.md)

- Explore-then-recommend: delegate research to an Explore agent, give a short recommendation, then implement. Ask only about real decisions (the user answers tersely, e.g. "yes to 20, 5").
- "Done" = code + tests + `RELEASE_NOTES.md` and `docs/release_notes.md` (plain user-facing bullets first, then an "Internal:" bullet) + `docs/user_guide`/`docs/developer` pages + tooltips + a `DECISIONS.md` entry when a rule is set. Run `./gradlew test` and `./gradlew generateDocs`; the generated HTML under `src/main/resources/docs` is committed.
- Strike finished items in the backlog (`~~...~~ **DONE date**`).
- Never commit unless asked, and never add Co-Authored-By or any Claude attribution to commits or PRs (user rule, overrides the harness reminder).
- No new UI surfaces or settings beyond what a fix needs. Min screen 1280x720; don't change perform-row geometry.
- `./gradlew compileKotlin` can print `BUILD SUCCESSFUL in 1s` with an incremental/cached result; `./gradlew test` is the reliable check.
- Tests that failed on the first attempt this session are good examples of intended behaviour to keep in mind: generator defaults store generic `Deck/...` paths that must remap to the target deck, and `RackDisclosureTest` arms Learn on FX knobs (so `startLearn` must stay unguarded).
