# Round 8 (2026-11-14 night to 11-15): the automatable leftovers

Test build: `tests/web/.cache/app/rt` at 8a9a458 + probes 2-6 + `apply_probe8.py` (Left/Right/Home/End keys, a `chars` action that types without Enter, `TOUR_TIP` lines under `LSD_TOUR_TIPS`, `LSD_TOUR_PERSIST` to keep preferences and the session across runs, a device name on tour MIDI messages) plus this round's fixes. Runner `ops8.py` (soundtrack via `b.audio`, `KEEP_LIB` for restart pairs, preferences wiped per case). Cases in `cases12.py`; working notes `notes.md`; results under `results/`.

Run: `CASES=cases12 CASE_DIR=$PWD/round8 RESULTS_DIR=$PWD/round8 python3 round8/ops8.py <w> [case...]`

## Fixed (main tree, compiled, uncommitted)
- Ctrl+F then Esc took two presses (the 2026-11-14 fix never fired: ImGui deactivates the search field on that same Esc). `ui/browser/BrowserPane.kt`: `isItemDeactivated()`.
- First Record press froze the output ~2.2 s (FFmpeg encoder probe on the render thread). `export/FFmpegProcessPipe.warmUp()` from `Main.kt`. Lab re-run: no gap.
- Chain menu title "Deck A FX FX Chain". `ui/FxChainHeader.kt`.
- `web/ui.js`: SCHEDULE page skips the real fetch under `?nolive` (a real show on air broke ui_check's Offline badge check). Not published.
Release notes in both files.

## For the user
- Arrow keys never reach the queues; the user guide says they do (item 44). Doc or feature.
- Three placement rules for a single .lsdfx into a full chain (item 52).
- Item 114's "Save As goes to presets/" predates item 26's folder rule.
- Monitor tooltip lacks "click again to stop" (76); FX queue tooltips don't say A and B share one position (158).
- Stock source dropped from a mixed drag silently (41).
- MIDI to the FX queue (101): tour mapping didn't step it; button does. Unsettled.
- Light-theme screenshots of the new items in `results/light/`.

## Web
ui_check 196/197 (left: time-of-day schedule fixture just after midnight; once a flaky [LIVE!] stub race), tape_check 78/78, shader_check 70/70, embed_check and beat_check all ok. render_check wrote no frame in 25 min (again).

## Not done
Tours (all 7 stale, ~3 h, render after a commit), the 2-hour offline render (decode was the bug, checked in round 5), Playwright suite.
