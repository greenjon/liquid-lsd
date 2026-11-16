# Lane 4: User docs and release notes (audit 2026-11-15)

Baseline `audit-2026-10-29` (fa4335e). HEAD aeb7937. Reviewer was read-only; the only file written is this one.

## Checklist coverage

| Item | Result |
|---|---|
| `docs/getting_started.md` vs UI | Read in full. 1 finding (L4-14). Launchers, Space/Ctrl+P, Audio Hardware tab, Sync State ACTIVE, factory decks on first run all match. |
| `docs/index.md` | Read in full. Clean apart from the stale "Web Broadcast requires a relay server" summary (fine: still true for the desktop feature). Index lists 11 of the 18 developer pages (nav has them all): not a finding. |
| `README.md` | Feature and layout sections spot-checked. 2 findings (L4-15, L4-16). imgui version (1.92.7.1) matches build. |
| `user_guide/your_workspace.md` | Read in full; shortcuts checked against `ShortcutManager.kt` and `Main.kt`. Findings L4-1, L4-2, L4-9. Glossary, side rail, page names (A/B, BG/PV, MIXER), themes (Grey Acid / Orange Sunshine), Caps Lock toggle, 30-step undo all OK. |
| `user_guide/visual_sources.md` | Headings vs `library/sources/` (37 sources, all documented); KIFS, KIFS Storm, Chladni, Spectrogram, Scope inputs vs ISF headers; Feedback params vs `feedback.fs`. Findings L4-3, L4-17. |
| `user_guide/modulation.md` | Audio section read in full; band list (Hi-Mid present), Audio Hardware panel labels, Turn On Needed Columns, Retry Connection checked. Finding L4-8. LFO/SEQ sections not line-checked. |
| `user_guide/presets_and_library.md` | Read in full. FX chain table = the 35 shipped `web/fxchains/web-*` chains exactly; first-run chains/presets match `Mixer.loadDefaultFxChains` and `SessionSerializer`; `festival_elite.lsdtransplay` present; `library/midi/` profile path correct. Finding L4-6. |
| `user_guide/performance_controls.md` | Trackpad, notes, FX chain sections read; MIDI section spot-checked (labels exist: Live MIDI Monitor, Tap Tempo Trigger, Learn MIDI Modulator, Awaiting Pickup, FX Swap Fade default 150 ms). Findings L4-4, L4-5, L4-7. OSC section and Twister sections not line-checked (lane 3/5 overlap). |
| `user_guide/macros_and_rack.md` | Perform-view section (lines 228-330) read; rest skimmed. Finding L4-10. |
| `user_guide/hardware.md` | Read in full. Finding L4-7 (shared with performance_controls). |
| `user_guide/output_and_recording.md` | Read in full; Offline render vs `VideoExportModal.kt`. Finding L4-11. |
| `user_guide/web_broadcast.md` | Desktop part checked vs `MenuBar.kt` / `PreferencesPanel.kt` (Output › Web Broadcast, LIVE ERR, Show Advanced Preferences: OK). Web TV part spot-checked vs `web/index.html`, `web/ui.js`. Finding L4-12. |
| `user_guide/cli_usage.md` | Options vs `cli/CliArgs.kt` (all listed options exist, incl. `--window=maximized`, `--smoke-test`, `-h`, `-v`). Finding L4-13. |
| Release notes parity | `diff RELEASE_NOTES.md docs/release_notes.md`: **identical** (4062 lines each). Clean. |
| Release-notes coverage | 61 commits since baseline touch `src/` outside `src/main/resources/docs` (81 including bundle-only commits), plus 18 that touch `library/`, `defaults/`, `build.gradle.kts`. Notes are one `[Unreleased]` list mixing desktop and web-TV entries (`### Web TV: ...` headings), so web-only commits are covered by that convention. Every user-visible desktop commit has an entry except the four in L4-18 (all minor). |
| mkdocs nav | All 34 pages under `docs/` (excl. `archive/`, which is `exclude_docs`) are in nav, counting the **uncommitted** `developer/audit.md` nav line (L4-19). |
| Unused images | `docs/user_guide/assets/ui-desktop-1080p.png`, `ui-lab-1080p.png` are referenced by no page (L4-20). |
| Bundled HTML | Built by Gradle `generateDocs` (`mkdocs build -d src/main/resources/docs`, a dependency of `processResources`), tracked in git. Committed copy is stale by one commit (L4-21). `scripts/build_website.py` is the public site only (to `./greenjon`), not the bundle. |
| Tours | `scripts/tour_staleness.py` read (read-only: git log + file reads) and run. All 7 STALE (L4-22); script over-attributes (L4-23). |

---

## Findings

### L4-1 [wrong] Workspace page describes a Mixer "blend mode selector" and decks going "through ... blend modes"
- Where: docs/user_guide/your_workspace.md:30 ("These go through the crossfader and blend modes"), :87 ("- Blend mode selector")
- Evidence: `ui/MixerPanel.kt` has no blend-mode control (only "blend between Deck A and Deck B" crossfader hint text, :391). Transition blend modes were removed; the blueprint lists "blend mode (transitions)" as a banned term ("removed; do not mention").
- Impact: new users look for a control that doesn't exist.
- Fix: line 30 "go through the crossfader and the current transition"; drop the :87 bullet (replace with "Transitions row / crossfader" if wanted).
- Verified:

### L4-2 [stale] Workspace shortcut table misses Ctrl+Shift+S and Delete-in-Edit, and says `Q` only feeds the A/B queue
- Where: docs/user_guide/your_workspace.md:115-136
- Evidence: `ui/shortcuts/ShortcutManager.kt` registers `parameters.save_deck_as` (Ctrl+Shift+S) and `parameters.clear_reset` (Delete: clear cell / reset parameter) and `library.queue_ab` "Appends the selection to the current tab's queue: the A/B Queue (Sources), the A/B FX Queue (FX) or the Transition Queue (Transitions)". The table row says "Add the selected Library item to the A/B queue / BG queue".
- Impact: users don't find Save As or the FX/transition queue behaviour from the table.
- Fix: add the two rows; reword Q to "the current tab's queue (A/B, A/B FX or Transition) / its BG queue".
- Verified:

### L4-3 [wrong] "36 built-in procedural generators"; there are 37
- Where: docs/user_guide/visual_sources.md:31
- Evidence: `library/sources/` and `src/main/resources/default_sources/` each hold 37 sources, and the page documents 37 (Spectrogram, Scope, KIFS Storm added since the baseline, when the count read 34).
- Impact: minor; count mismatch with the list below it.
- Fix: "37" (or drop the number).
- Verified:

### L4-4 [wrong] Trackpad console: "Hold CapsLock ... Release CapsLock" but Caps Lock toggles
- Where: docs/user_guide/performance_controls.md:9-15
- Evidence: `Main.kt:447-448` calls `touchConsoleController.toggleActive()` on press only; there is no release handler. The Preferences tooltip and status line say "when CapsLock is engaged" / "Press CapsLock to engage". your_workspace.md:131 correctly says "Toggle".
- Impact: a performer releases Caps Lock and expects the mouse back; it stays grabbed until the next press.
- Fix: "Press Caps Lock to turn it on, press again to turn it off."
- Verified:

### L4-5 [wrong] "On macOS, native trackpad support works without any extra setup": the macOS backend is a stub
- Where: docs/user_guide/performance_controls.md:64; tooltip in `ui/PreferencesPanel.kt:311`
- Evidence: `input/MacCocoaTouchBackend.kt` `start()` only logs and sets `isStarted = true` ("Cocoa touch hook setup" is a comment); `onNativeCocoaTouch` is never called anywhere. State is READY on macOS, so the Preferences panel shows "Ready (Press CapsLock to engage)" but no touch events arrive.
- Impact: Mac users see "Ready" and get a dead surface (possibly with the cursor still free, so it looks like nothing happens). Also a lane-1 style bug: the feature appears enabled on a platform where it does nothing.
- Fix: docs: "Linux only for now". Code (owner decision): report `NO_DEVICE`/"Disabled on this platform" on macOS until a real Cocoa hook exists.
- Verified:

### L4-6 [stale] MIDI Learn steps mention a "MIDI Learn button in the Parameters header"
- Where: docs/user_guide/presets_and_library.md:335
- Evidence: no such button exists; MIDI Learn is the right-click menu item on sliders/knobs (`ui/CustomRangeSlider.kt` right-click "MIDI/OSC Learn", "Cancel MIDI Learn", "Clear MIDI Mapping"). "Parameters" is the retired classic-view name. hardware.md:24 has the correct right-click steps.
- Impact: users look for a header button.
- Fix: replace the four steps with a link to hardware.md "Other MIDI controllers", or "Right-click any knob or slider and choose MIDI Learn".
- Verified:

### L4-7 [wrong] "Preferences (`Ctrl+,`)"; the shortcut is Ctrl+P
- Where: docs/user_guide/hardware.md:11, docs/user_guide/performance_controls.md:202, :434
- Evidence: `ShortcutManager.kt` `global.preferences` = Ctrl+P (`MenuBar.kt:70` shows "Ctrl+P"); no binding uses `GLFW_KEY_COMMA`. Every other page says Ctrl+P.
- Impact: the documented key does nothing.
- Fix: `Ctrl+,` -> `Ctrl+P` in the three places.
- Verified:

### L4-8 [wrong] Audio response profile names don't match the UI
- Where: docs/user_guide/modulation.md:73-81
- Evidence: `parameters/Enums.kt:76-82` labels are "Instant (Raw / 1-Frame)", "Strobe / Snap", "Punchy (Accent)", "Smooth Swell", "Slow Bloom", "Ambient Drift", "Custom…". The doc says "Instant (Raw Jitter)", "Snap", "Punchy", "Slow Pulse" (no such profile).
- Impact: users can't match the table to the dropdown, especially "Slow Pulse".
- Fix: use the UI labels; optionally add the attack/decay ms from the enum.
- Verified:

### L4-9 [wrong] Preset notes and source notes are documented but the UI to add them was removed
- Where: docs/user_guide/performance_controls.md:80-110 (three-tier notes table, "Right-click the preset name label below the deck monitor preview → Add/Edit Preset Note...", "Open the Deck Menu popup → Add/Edit Source Note...", "The preset name label" section with `Untitled`)
- Evidence: only `NoteContext.Param` is ever requested (`ui/ParametersRenderer.kt:316-318`, labels "Add Parameter Note..." / "Edit Parameter Note..."). Nothing constructs `NoteContext.Source` or `NoteContext.Preset`; there is no preset name label under the deck monitors (`DeckMonitorGrid.kt`/`MixerPanel.kt`), and no "Deck Menu popup". The removal predates the baseline (git `-S"Preset Note"` -> 90a9d07). Menu label is "Add Parameter Note...", not "Add/Edit Parameter Note...".
- Impact: users can't follow two of the three how-tos; existing preset/source notes are still read (tooltips) but can't be edited.
- Fix: owner decision (see E): either restore entry points (e.g. source badge right-click, preset row menu) or cut the doc to parameter notes and say existing preset/source notes still show in tooltips.
- Verified:

### L4-10 [stale] Perform-view reference in macros_and_rack.md has old row positions, class names and labels
- Where: docs/user_guide/macros_and_rack.md:257-305
- Evidence (examples):
  - "Transitions (Row 2 in MASTER)", "Clock & Global (Row 4 in MASTER)": the page is **MIXER** (`perform_pages/mixer.json`), Transitions is row 1, Clock row 4; the table at :241 has it right.
  - "beside the `CLOCK` badge": the badge reads `CLK` (`PerformanceMatrixPanel.kt:602`; the doc's own :252 says CLK).
  - "Deck A and Deck B connect to `PlayQueueManager` (`< N/Total >`), Deck BG connects to `BgQueueManager` ... Deck PV features a quick Preview focus button": internal class names in user docs (and "PlayQueue" is a banned UI term); the row line-2 layout is described correctly in performance_controls.md:118.
  - "Row group titles (e.g. `DECK A`, `DECK A (FX)`) sit at the top-left": rows have title badges (A/B/BG/PV + SRC/FX caption), as :254 says.
  - "Dropping a deck preset (`.patch`, `.lsd`, `.json`)": no `.patch` handling exists in the code.
- Impact: contradicts the page's own table and performance_controls.md; confusing, not harmful.
- Fix: rewrite :257-305 against the table at :241 and the deck-row description in performance_controls.md; drop class names and `.patch`.
- Verified:

### L4-11 [wrong] Offline render options don't match the dialog
- Where: docs/user_guide/output_and_recording.md:62-83
- Evidence: `ui/VideoExportModal.kt`: title "Offline Video Export Studio"; audio accepts `.wav, .mp3, .flac, .ogg, .m4a, .aac`; resolutions are "Match Project Canvas" + 720p/1080p/1440p/4K/Vertical 9:16/Square/Ultrawide 21:9 (no "custom"); frame rate 24/30/60/120; a **codec** choice (H.264, H.265, ProRes 422 HQ) the doc omits; the preset field also takes a `.lsdplay` playlist. The doc's "live thumbnail so you can confirm it looks right" does not exist (progress view shows speed/elapsed/ETA/size text only).
- Impact: users look for a custom resolution and a thumbnail.
- Fix: list the real options; drop the thumbnail sentence; mention codec and playlist.
- Verified:

### L4-12 [stale] Web TV page: beat light and info-panel text predate TV v42/v45
- Where: docs/user_guide/web_broadcast.md:149 ("click the picture or press I"), :151 ("MENU › ABOUT"), :153 ("the second light on the badge"), :155 ("Spout, NDI, PipeWire, webcam")
- Evidence: the beat light is `#beatLed` in the display window (`web/index.html:280`; release notes TV v45 "the beat light in the display window"); the badge/nameplate is gone (TV v38). ABOUT is under MENU › TV (`web/ui.js:633`; :89 of the same page says "MENU › TV › ABOUT"). Info now shows on hover (:91 says so; :149 still says click). External video is Spout/Syphon/PipeWire; there is no NDI.
- Impact: web viewers/readers look for a light that moved.
- Fix: "the beat light in the display window"; "MENU › TV › ABOUT"; "point at or click the picture"; "Spout, Syphon, PipeWire".
- Verified:

### L4-13 [wrong] CLI page: packaged launcher is not `./liquid-lsd`
- Where: docs/user_guide/cli_usage.md:11-14
- Evidence: release zips ship `run-linux.sh` / `run-mac-*.command` / `run-windows.bat`, which pass arguments through (`"$@"` / `%*`, build.gradle.kts ~657-682). There is no `liquid-lsd` executable.
- Impact: copy-paste fails.
- Fix: `./run-linux.sh [OPTIONS]` (and the Windows/mac launchers).
- Verified:

### L4-14 [stale] Getting Started lists four audio scopes; there are five bands
- Where: docs/getting_started.md:75 ("the Full Mix, Bass, Mid and High scopes")
- Evidence: `ui/AudioEnginePanel.kt:71-75` draws Full Mix, Bass, Mid, **Hi-Mid**, High (Hi-Mid added in 4de0ef9).
- Impact: trivial.
- Fix: add Hi-Mid.
- Verified:

### L4-15 [stale] README band list and filter slot count
- Where: README.md:14 ("audio amplitude bands (Bass/Mid/High)"), README.md:204 ("filters/  ISF effect filters (Slots 1-4)")
- Evidence: five bands incl. Hi-Mid plus Section Energy/Drop; FX chains have 3 slots.
- Impact: first-impression inaccuracies on GitHub.
- Fix: "Bass/Mid/Hi-Mid/High, section energy and drops"; "(Slots 1-3)".
- Verified:

### L4-16 [polish] README "What remains before v1.0" lists "Two polished themes" as open
- Where: README.md:45-52
- Evidence: two themes ship (Grey Acid, Orange Sunshine), light-theme legibility fixed in 9f85a26; memory/notes record v1.0 scope. Whether it is "polished" is the owner's call.
- Impact: low.
- Fix: owner decides whether to strike or keep.
- Verified:

### L4-17 [polish] Chladni Cymatics doc misses Glow and Hue Offset, and says "Palette Modes"
- Where: docs/user_guide/visual_sources.md:114-129
- Evidence: ISF inputs (`library/sources/chladni_cymatics/chladni_cymatics.fs`) include `Glow`, `Hue Offset`, label `Palette Mode`, and `Invert (Antinodes)`.
- Impact: low.
- Fix: add the two knobs; "Palette Mode".
- Verified:

### L4-18 [polish] Four user-visible desktop commits since baseline have no release-notes line
- Where: RELEASE_NOTES.md / docs/release_notes.md `[Unreleased]`
- Evidence:
  - 8720017 "safe saves keep the file's permissions": fixes a regression from 6bac01c (same unreleased cycle) where every save made presets rw-------. Within one unreleased cycle, so arguably no line needed, but users of a build between the two (v0.9.15x tags) saw it.
  - 49a3b81 retry of the safe-save swap on Windows (scanners holding new files).
  - 1fd8f58 a second unreadable copy gets a date stamp (`<name>.unreadable-<stamp>`); the notes at `[Unreleased]` "kept beside the original as `<name>.unreadable`" are now incomplete.
  - de4a0c6 reversed `smoothstep` edges fixed in Pinch/Bulge, Wave Displace, Chladni, Nebula Volume, Aurora Ribbons (identical on NVIDIA; may change rendering on other drivers).
  - Everything else since baseline is covered (checked by commit: every one of the other src/library/defaults commits either edits both notes files or is covered by an existing heading).
- Impact: low.
- Fix: one line each under the Round 3 safe-save entry (permissions, Windows retry, dated copy) and one under a shader-fix line.
- Verified:

### L4-19 [polish] `developer/audit.md` nav entry is uncommitted (and the page is untracked)
- Where: mkdocs.yml (working tree adds "Audit Blueprint: developer/audit.md"); docs/developer/audit.md untracked
- Evidence: `git status` shows ` M mkdocs.yml` and `?? docs/developer/audit.md`. The working-tree bundle has been regenerated with it (`?? src/main/resources/docs/developer/audit/`, 33 modified HTML files differing only by the new nav item).
- Impact: none until commit; committing the blueprint without the regenerated bundle leaves the bundle one page behind.
- Fix: commit mkdocs.yml, the page and the regenerated `src/main/resources/docs/` together.
- Verified:

### L4-20 [decision] Two images under docs/ are referenced by no page but ship in the app
- Where: docs/user_guide/assets/ui-desktop-1080p.png, ui-lab-1080p.png (last changed 3eaf80c, 2026-09-27)
- Evidence: only `build.gradle.kts:927,965` (capture task outputs) and developer/cli_and_screenshot_automation.md name them; no `![]()` anywhere. mkdocs copies them into `src/main/resources/docs/user_guide/assets/` (416 KB in the jar). The desktop screenshot is from September, before the three-view layout.
- Impact: dead weight; and if ever linked, an outdated screenshot.
- Fix: owner: either use a fresh capture in your_workspace.md/getting_started.md, or move the capture targets out of `docs/` (e.g. `build/`).
- Verified:

### L4-21 [stale] Committed bundled HTML lags the Markdown by commit 8b3c211
- Where: src/main/resources/docs/ (last committed in d8d94f1)
- Evidence: 8b3c211 edited `RELEASE_NOTES.md`, `docs/release_notes.md` and `docs/user_guide/web_broadcast.md` without the bundle (`git show HEAD:src/main/resources/docs/release_notes/index.html` has 13 "receiver" hits; a fresh `mkdocs build` to the scratchpad has 17). The bundle is regenerated on every Gradle build (`processResources` depends on `generateDocs`), so the working tree is already current (identical to a scratch `mkdocs build` of the current tree: `diff -r` clean); only the committed copy is behind. Recurring pattern: the bundle is committed by whichever commit next runs a build.
- Impact: a release built from a clean checkout is fine (it regenerates when mkdocs is present); a build host without mkdocs ships the stale committed copy (generateDocs silently skips).
- Fix: commit the regenerated bundle. Automatic check (F): CI step `mkdocs build -d /tmp/x && diff -r /tmp/x src/main/resources/docs` (ignoring sitemap.xml.gz), or make generateDocs fail on CI when mkdocs is missing.
- Verified:

### L4-22 [stale] All seven tour videos are stale
- Where: tours/*.json; tours/out/*/rendered_at.json (all rendered from fa0db14, 2026-11-05)
- Evidence: `python3 scripts/tour_staleness.py`:
  - fx_chains STALE 22 commits (14 maybe)
  - hardware STALE 21 (16 maybe)
  - library STALE 21 (15 maybe)
  - mixing STALE 14 (19 maybe)
  - modulation STALE 20 (16 maybe)
  - output STALE 15 (21 maybe)
  - overview STALE 26 (10 maybe)
  Changes that really show on screen include 3ea7b52 (factory decks playing on first run), 59a03ba/eac05b7 (library chips, Esc, Add to Playlist submenu), 9f85a26 (light theme), 4fa7646/4303bb8 (monitor click, FX queue tooltips), 55f05a3 (randomization on by default).
- Impact: published videos show an older UI and an empty first run.
- Fix: re-render is a separate owner decision (memory: "tour re-render on hold").
- Verified:

### L4-23 [polish] tour_staleness.py counts source-shader commits against every tour
- Where: scripts/tour_staleness.py `map_key`
- Evidence: tour keys that are source ids or generic words (`mandala`, `icosa_h3`, `metaballs`, `mixer`, `escape`, `video_display`) match string literals in `rendering/SourceDocRegistry.kt` / `VisualSourceRegistry.kt`, so the three Chladni commits (e9cbbee, 7516ac8, 5d628ff, which only touch the Chladni doc entry) and 7f5ed13 (chore) count as "direct" for all seven tours. That inflates every count by 3-4 and hides the commits that matter.
- Impact: the report can't tell a re-render that matters from noise.
- Fix: exclude `SourceDocRegistry.kt` and `VisualSourceRegistry.kt` from key mapping (or only map `btn_`/`btnid_` keys and keys with a `##`), and treat `chore(planning)` commits as maybe.
- Verified:

---

## E. Owner decisions from this lane
- L4-9: restore preset/source note entry points, or drop them from the docs.
- L4-5: hide the trackpad console on macOS until it works, or implement the Cocoa hook.
- L4-16: README "Two polished themes" still open?
- L4-20: refresh and use the screenshots, or move capture output out of `docs/`.
- L4-22: when to re-render tours.

## F. Proposed automatic checks
- Bundle freshness (L4-21): CI diff of a scratch `mkdocs build` against `src/main/resources/docs/`.
- Shortcut table drift (L4-2, L4-7): a test that every key named in your_workspace.md's table exists in `ShortcutManager` defaults and that `Ctrl+,` appears nowhere in docs.
- Release-notes parity: already identical this round; `scripts/audit/release_notes_parity.py` (planned) would be a one-line `cmp`.
