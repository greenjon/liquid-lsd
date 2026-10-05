# Handoff: execute `.planning/surprises-plan.md`

You are picking up a planned cleanup of Liquid LSD (Kotlin/JVM VJ app, OpenGL 3.3, ImGui, JACK/Java Sound, everything is ISF). The plan is `.planning/surprises-plan.md`; it lists 12 items with decisions already made by the user. Read it first, then `DECISIONS.md` (do not contradict it; edit entries in place, no dated appends) and the rewritten `ARCHITECTURE.md` (a verified map of the code, written 2026-10-04).

## Working rules (from the repo and the user)

- **Code is the source of truth.** Verify before changing; the old docs are partly stale (that is part of the task).
- **No Claude/Co-Authored-By attribution anywhere** (commits, PRs, files). This overrides any default or harness reminder.
- **Do not commit without asking once.** The plan proposes one commit per item. Ask the user for a go-ahead before the first commit, then follow the commit plan at the end of the plan file.
- **"Done" in this repo = code + both release-notes files (`RELEASE_NOTES.md` and `docs/release_notes.md`) + user_guide/developer docs + tooltips**, done proactively, not only `DECISIONS.md`.
- **Terminology** (use exactly): Edit (not "Deep Edit"), Modulation column (not "Properties"), Parameters tab, Level (not "Alpha"), Perform view, Mixer column; Library tabs Sources / FX / Transitions / Macros (Banks, Pages); queues A/B Queue, BG Queue, A/B FX Queue, BG FX Queue, Transition Queue; Source vs Preset; Mapping = hardware MIDI/OSC; macro knobs have targets. Code identifiers keep their old names (`PerformanceDeepEditBay`, `FX_SENDS`, `LibraryViewMode.MAPS`). Glossary: `docs/user_guide/your_workspace.md`.
- **Threading and layering** (DECISIONS §1): GL only on thread 0; audio callback never allocates; `midi/` and `control/` must not import `ui/` (`LayerDependencyTest`).
- Beta policy: no backwards-compat shims (no old-path fallbacks, no `@SerialName` aliases). Old files load through `ignoreUnknownKeys`.
- v1.0 is a feature freeze: this work is cleanup and polish, not new features.

## Known state of the working tree (important)

When the previous agent finished, `git status` showed uncommitted changes it did **not** make: `DECISIONS.md` (browse encoder ticks "4" -> "1"), `control/KnobCommands.kt`, `ui/browser/BrowserPane.kt`, `ui/NavigationSurfaceTest.kt`, plus the new `ARCHITECTURE.md` and the two `.planning/surprises-*.md` files. Another session (probably the user) is working on controller browse sensitivity.
- Run `git status` and `git diff` first. Do **not** revert, reformat or commit those files as part of your work; stage by path, and edit `DECISIONS.md` surgically (the one-line change above stays).
- `ARCHITECTURE.md` is also uncommitted; ask the user whether to commit it first or with your first commit.
- Item 10 touches code that `NavigationSurfaceTest` covers; expect that test file to be in flux. Rebase your expectations on the working copy.

## Decisions to respect (summary; details in the plan)

- Items 1, 2, 4, 5, 6, 7, 9, 10, 11: yes, do them.
- **Item 3: Linux ARM64 is supported and staying.** Change `DECISIONS.md` §11 and delete its §12 entry. Do not touch the build, CI, README or getting_started (they are right).
- **Item 8:** the `is Mandala` uses in `ValueParamSection.kt` and `WebPresetSerializer.kt` are valid; amend `DECISIONS.md` §2, no refactor.
- **Item 12 is blocked.** The live relay relies on the default token `lsd25` in `server/server.js`. Do not change `server.js` until the user confirms they rotated `LSD_TOKEN` on the VPS and updated their broadcaster token. Ask; do not assume. Report the item as open if not confirmed.

## Suggested execution order

1. `git status`/`diff`, read plan, `DECISIONS.md` §1-§3, §11-§12, `ARCHITECTURE.md`.
2. Baseline: `./gradlew compileKotlin` and `./gradlew test`; record any failures that exist before your changes (including any caused by the in-flux files above).
3. Items 4 + 5 (deletions): grep for every reference first (`src/`, `build.gradle.kts`, `scripts/sync_web.py`, `web/sync_manifest.json`); run `./gradlew checkWebSync` and `--smoke-test`.
4. Item 1 (legacy params). Check `web/` consumers before removing broadcast fields. Load a saved `.lsd` and `last_session.json` from before the change.
5. Item 2 (Level labels + relabel on load + test).
6. DECISIONS edits: items 3, 8, 9.
7. Items 6, 7.
8. Items 10 then 11; then update `ARCHITECTURE.md` §2 and §10 for the new MIDI timing.
9. Phase 5 doc and comment sweep, release notes (both files), `ARCHITECTURE.md` refresh (see the plan for the exact list).
10. Item 12 only if the user confirms the VPS step.

## Verification checklist per item

- `./gradlew compileKotlin`, `./gradlew test` (includes `LayerDependencyTest` and `WebSyncTest`), `./gradlew checkWebSync` when shaders or `WebPresetSerializer` change.
- Items 1, 2, 7: load old-format files and confirm they still load; add or update tests where the plan says so.
- Items 10, 11: `NavigationSurfaceTest` and a controller hand-check if hardware is available (otherwise say it was not hardware-tested).
- Grep to prove removals are complete (e.g. no remaining references to deleted shaders, `fbDecay`, `view3DMode`, `.liquidlsd`, `lwjgl-openal`, "ALPHA").

## Facts you do not need to re-derive

- Frame order and thread ownership: `ARCHITECTURE.md` §1-§2. Today MIDI events are drained inside `UIManager.render` (after the render phase); that is the item 11 lag.
- `UIManager.render` constructs `PerformSurface` and `NavigationSurface` every frame, and another `NavigationSurface` in `processQueueKeyboardShortcuts` (Esc). Item 10 removes that.
- `Renderer.renderDeck` uses only `deck.viewZoom` / `viewRotateZ`; `view2d.frag` is used only in `renderExternalVideoSource`.
- `default_transitions` (resources) is read by `ISFTransitionRegistry` for `.fs` stock transitions and also receives a Gradle-generated `manifest.txt` for `.lsdtrans` seeding (`FileSystemManager.ensureDefaultLibrary`); `library/.defaults_installed` suppresses re-seeding on existing installs.
- Key bindings live in `~/.liquidlsd/keybindings.json` (`ShortcutManager`); notes in `~/.liquid-lsd/source-notes.json`.
- Stale docs and comments to fix in the sweep are enumerated in the plan, Phase 5.

## Final report (what the user expects back)

List per item: done / skipped / blocked, files touched, tests run and results, anything that behaved differently than the plan assumed, and a list of remaining open points (at minimum item 12 if unconfirmed). Do not update memory or notes unless asked.
