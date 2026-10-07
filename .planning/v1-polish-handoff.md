# v1.0 polish handoff (2026-10-07)

For a fresh agent. Read this, then `DECISIONS.md` (durable rules), then only the files named below. Working tree is clean at `73017d1`.

## Working rules (owner's)

- Short recommendation first, then implement; delegate broad architecture research to an Explore agent.
- "Done" = code + tests + BOTH release-notes files (`RELEASE_NOTES.md` and `docs/release_notes.md`, same entry, newest under `## [Unreleased]`, plain user bullets then an `Internal:` bullet) + relevant `docs/user_guide` / `docs/developer` pages + tooltips + a `DECISIONS.md` entry only for a durable rule (edit entries in place, no dated appends). `./gradlew test` must pass; the build regenerates tracked files under `src/main/resources/docs`, so run `git checkout -- src/main/resources/docs` after builds.
- Commit only when asked. Never add Co-Authored-By or any Claude attribution to commits or PRs.
- v1.0 is a feature freeze: polish, usability, stability. New features go to v1.1. Min screen 1280x720. Do not change perform-row geometry. No new UI surfaces or settings beyond what a fix needs.
- Deck and transition changes go only through `DeckOps` / `TransitionOps`.
- Pronouns: use they/them for the owner.

## What just landed (all committed)

1. **Pair focus view** (`.planning/pair-focus-view-plan.md`, `DECISIONS.md` section 7). Clicking a deck SRC badge / FX chain or slot / transition name / monitor focuses a pair of rows (deck SRC+FX, Master MIX+FX, Transitions+Clock) with the Browse list below. Key code: `ParametersState.focusedPair`/`focusPair`/`openBrowse`, `PerfRows.PAIRS`, `PairKnobTouch`, `NavSurface.browseLiveKnobs`. The Edit bay is Params-only; the bound Perform dock, Browse tab and `SectionMode` are gone.
2. **Twister**: in the pair view a bank press walks the pairs (A > B > BG > PV > Master > XF), `ControllerFeedback.syncActiveBank` is skipped while a pair is focused, and Shift + tap on a send knob (9-13) switches to that pair without sending.
3. **Controller FX tap** with nothing bound applies to the live deck (first vacant slot, last slot when full; Shift + tap queues). **Browser scope/search/cursor memory is per kind** (`ScopeMemory.contextOf`; a whole-chain target keeps its own bucket).
4. **Tooltips** on the badges/slots/monitors say they open the pair view.
5. **Light theme, first contrast pass**: new `TangoPalette` Roles (`HOVER_OVERLAY`, `PRESS_OVERLAY`, `HEADER_TINT`, `CELL_WASH`, `HOVER_BORDER`, `TEXT_FAINT/DIM`, `TEXT_OK/WARN/ERROR`, `QUEUE_AB/BG_TEXT`), `CvTheme.LIGHT_THEME_SCALE` (0.62). None of it has been seen in the app. Status and remaining literals: `.planning/theme-color-audit.md`.

## Backlog status

`.planning/pre-release-ui-backlog.md`: every item is done or decided. Item 9 (restore expanded module after leaving Edit) is **decided: leave as is** (option A). An agent re-checked the DONE claims against the code on 2026-10-07 and found them intact.

## Open work (nothing here is started)

### A. Small code gaps (safe to just do)
- **FX browser context-menu "Load to <chain> / Slot n"** (`ui/browser/FXBrowserPanel.kt` ~line 307) calls `FxOps.loadSlot` without `undoable = true`. Make it undoable like the other bay/pick paths.
- **Item 5 follow-up**: the backlog says a lock badge is still open for sliders whose uniform is owned by a Metaknob link (precedence is documented; `MacroEngine.isMappingTargetLocked`, `ModulatableParameter.metaDrivenBy`). Check whether the slider shows any lock indication; if not, show the same lock the macro-bound sliders show (no new UI surface).
- **Backlog text nits**: item 6 says "up to four" Learn modes (there are three: macro, OSC, MIDI; Esc cancels all three); "former item 27" (absorbed by item 8) is not recorded anywhere. Fix the wording.

### B. Light theme remaining (the owner will do the visual cleanup after looking; do not guess at looks)
Still literals, listed in `.planning/theme-color-audit.md`: `DropdownStyleHelper` selected text, remaining `ParametersRenderer` fills and the `(0.2)` grey border, the grey button triple `(0.15/0.25/0.35)` in `PropertiesPanel` / `AudioModulatorSection` / `ParametersTabs` / `ModulatorHeaderRow`, `MenuBar`, `MacroBindingEditor` mini-slider, `TempoSyncPanel`, oscilloscope and audio-meter text, `UIThemeStyler` neutrals, the die button/fader duplicated between `MixerPanel` and `DeckControlPanel`. Wait for the owner's notes before changing the CvTheme scale or any "island" verdicts.

### C. Needs the owner (do not do blind)
- **Nothing in the backlog has been click-tested in the running app**: Maps tab, undo of Edit-bay picks, Sources/Transitions tab screenshots, Ctrl+F in all three Library tabs, the two-line deck-row badge (`project_deck_row_two_line_badge`), the light theme. Pair-view step 9 is partly checked by the owner (banks, send, shift-switch); not yet: Learn inside a pair view returns to the 4-row layout (owner is fine with that for now), Master pair, XF+CLK pair, monitor click.
- **Hardware**: Twister checks beyond the above. The owner reported once that Shift + tap on a send knob "seemed strange" and later said it was fine; if it is intermittent it will reveal itself.

### D. Other open items outside the backlog file
- MIDI controller **phase 5, the profile UI**: `.planning/midi-phase5-profile-ui-plan.md`, `.planning/midi-controller-handoff.md`.
- Chain Link **Part B** (free a Twister button): see `.planning/midi-controller-handoff.md`.
- FX queue transport commands (`fx.queue_next/prev`, `fx.bg_queue_next/prev`) have no free button in the built-in profile.
- **Every-ISF binding audit** (v1.0 scope item) and **CvTheme / slider colour literals** from the Tango palette work.
- `.planning/codebase/ARCHITECTURE.md` is stale (predates per-deck FX, pair view, three views).
- Shelved to v1.1 (do not start): FX chain expanded view, free-knob Global row, localization.

## Where things are

- Plans/handoffs: `.planning/` (`STATE.md` is an index). Durable decisions: `DECISIONS.md` (sections 1-11; the pair-view rules are in section 7). Old log: `docs/archive/DECISIONS-history.md` (frozen).
- User docs: `docs/user_guide/*.md`, developer docs: `docs/developer/*.md` (`ui.md` has the Tango palette section and the pair-view description).
- Tests: `src/test/kotlin/llm/slop/liquidlsd/...` (pair view: `ui/PairFocusTest.kt`, `ui/NavigationSurfaceTest.kt`, `ui/PerformSurfaceTest.kt`, `control/KnobCommandsBrowseTest.kt`).

## Suggested order

A (all three are small), then ask the owner what they found clicking through (C) before touching B or D.
