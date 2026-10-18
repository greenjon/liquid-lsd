# Walkthrough video: handoff for phase 3 (2026-10-17)

READ FIRST: `.planning/walkthrough-video-plan.md` (plan, storyboard, findings), `.planning/walkthrough-phase2-results.md` (what was measured).
Branch state: phases 1-2 merged to main as b64180c. No Claude/Co-Authored-By attribution in commits (repo rule).

## Decisions (user)
Deterministic in-app driver; captions only (no voice-over); interactive R3WRK-style tour page ships in v1.0;
demo track NOT yet cleared (user is waiting on licensing): author with the generated test beat (`/tmp/tour/beat.wav` may be gone: regenerate with
ffmpeg lavfi) and keep the audio path swappable. Full ffmpeg with libx264 is now installed.
Another agent is separately fixing the FFT texture (`.planning/fft-texture-agent-prompt.md`); do not depend on audioFFT being live.
Docs agent already reconciled pages (3), FX slots (3), views, added a themes section: re-read docs before writing captions.

## What exists (phases 1-2, verified on this machine/GPU only)
- `--tour=<script.json> --tour-out=<file.mp4>` (+ `--tour-hidden`, `--tour-dump=<dir>:<frames>`): fixed 60 fps, 1920x1080, full UI rendered, FBO0 readback -> ffmpeg; writes `<out>.hashes` (per-frame SHA-256) and `.vtt`.
- Code: `src/main/kotlin/llm/slop/liquidlsd/tour/` (`TourRunner`, `TourScript`, `WidgetRegistry`, `TourAudioFeeder`, `DeterministicRandom`), `scripts/tour_determinism_check.sh <script> [args]`, sample script `scripts/tour_spike_script.json`.
- Script: top-level `seed`, `audio`, `bpm`; steps `{t, action, target|x,y, durationSec, caption}`; actions `move, click, set_page, hold, load_source, load_preset, set_param, bind_modulator`.
- Determinism: 4 real scenes (ISF + preset, audio bindings, LFO TIME/BEAT, hidden window) bit-identical raw and encoded across repeats. Tour mode isolates prefs, MIDI/OSC/Link/watchers, imgui.ini, real GLFW input.
- Timing ~22-28 ms/frame (encode-bound), first frame ~1.2 s.

## Known gaps to close in phase 3 (in order)
1. Script actions for FxOps and TransitionOps (chains, Super Knob/Metaknob, transitions), shuffle queues; verify determinism for each.
2. `WidgetRegistry.note(key)` coverage beyond page tabs + Perform knobs. Key list and file:line map are in the plan ("Driver design details" + UI inventory): browser tabs/search, `perf_gen_badge_*`, `rack_chevron_*`, crossfader track, FX slot cells/chain header, Edit side tabs, modulator tabs, macro strip, Preferences categories, `##ui_theme`. Do NOT key on `##title_badge_*_x_y`. Custom-drawn items with no ImGui item (deck title badges, under-knob strip labels, overhangButton) need explicit rects from PerfRowGeometry.
3. View/page setup actions as single calls: `performancePageId`, `libraryMode`, `openParams`, `focusPair`, `PreferencesPanel.open`, theme switch (light = ORANGE_SUNSHINE).
4. Overlay polish: caption bar burned in (optional, .vtt always), target highlight ring, smoother cursor easing, click ripple, drag action for knobs/crossfader (set lockCursorOnKnobDrag=false already in tour mode).
5. Chapter + clip ranges in the script, outputs: hero mp4/webm, poster, chapters metadata, per-chapter silent loops, caption JSON for the web page; `scripts/render_tour.sh`.
6. Remaining nondeterminism: `System.currentTimeMillis` in FileSystemManager, DeckOps autosave names, PlaylistManager, PerformanceStats, TouchConsoleController (did not affect pixels so far); no auto-save into library during tours; library contents are part of the input, so pin a tour library (small curated set) and document it.
7. Not yet verified: other GPUs/compositors, light theme, scripted clicks that trigger loads (only lightly tested; prefer load_* actions).

## Then
Phase 4 author `tour.json` (storyboard x widget map x captions; enable SEQ/MIDI columns first, they are off by default; avoid generator counts; say "three pages A/B, BG/PV, MIXER"), pacing iteration, light-theme pass.
Phase 5 encode outputs. Phase 6 interactive tour page via `scripts/build_website.py` + `website/templates` (clip + caption list + chapter jump + "01 / N" counter + full transcript). Phase 7 docs: both release-notes files, developer docs for the script format, user-facing mention.
Things not showable UI-only: Twister LEDs/rings, real MIDI/OSC input, 2nd display, Spout/Syphon/PipeWire, web relay, Link peers: show Preferences panels + on-screen knob rings; Twister segment needs filmed insert or on-screen graphic.

## Gotchas
- Always run `scripts/tour_determinism_check.sh` on each authored tour before trusting it.
- Hidden window works on this Wayland session; encoder: libx264 now available (software fallback `getBestSoftwareEncoder`).
- `WebEvaluatorVectorsTest` failed once as stale then passed; regenerate with UPDATE_WEB_VECTORS=1 if it recurs.
- Worktrees live under `.claude/worktrees/` (untracked); remove the finished one when done.
