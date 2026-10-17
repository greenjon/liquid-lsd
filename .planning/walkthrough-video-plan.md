# Walkthrough video plan (2026-10-17)

## What R3WRK does (the bar to beat)
hteah.github.io/R3WRK/tour/ is NOT a video. It is a scripted HTML replay: one app window, an animated
cursor visiting one control at a time, a caption per step, "01 / 73" counter, chapter jump, and a full
caption transcript underneath (so nothing exists only in the animation). A second page is a separate video
for audio setup. Strengths: precise, skimmable, accessible, per-control. Weakness for us: it's a mock-up of a
static-ish UI. Ours is a live visual instrument, so real moving pixels + audio reactivity is the advantage.

## Deliverables
1. **Hero video** (~4-5 min, 1080p60, captions, chapters) on greenjon.com + YouTube.
2. **Chapter clips** (10-20 s loops, silent, mp4/webm) for the user-guide pages and landing page.
3. **Interactive tour page** (R3WRK-style): same script drives caption list + chapter jump + transcript,
   with the real clips/stills as the visual. (Phase 2; reuses the script and captions.)
4. Optional voice-over track (v1.1 decision; captions-first matches R3WRK and needs no VO).

## Core design decision: deterministic render, not screen recording
We already have headless GPU readback (`--screenshot-ui`, `--screenshot-after-frames`, PBO readback).
Extend it into a **scripted tour driver** inside the app:
- `--tour=<script.json> --tour-out=<dir>`: runs the app windowed/offscreen at fixed 1920x1080, fixed
  timestep (frame N = N/60 s), writes PNG/raw frames piped to ffmpeg.
- Script steps: `{t, action, target, value, caption, chapter}`; actions = move synthetic cursor to a widget
  (by ImGui id), click, drag knob, set value, switch Perform/Edit/Library, load preset, hold N s.
- Synthetic cursor + click ripple + target highlight ring drawn in the overlay (not the OS cursor).
- Audio: fixed demo track (CC0/own music) fed to the analyzer by frame clock, so reactivity is identical on
  every render and A/V sync is exact; the same file is muxed as the soundtrack.
- Captions: emitted as .vtt/.srt from the same script AND optionally burned in by ffmpeg.
Why: reproducible after every UI change (release cadence is v0.9.N), no Wayland capture/input hacks
(xdotool is X11-only), no dropped frames. Fallback if the driver proves too costly: ffmpeg `pipewire`
capture + manual take, with the same script as a shot list.

## Storyboard (chapters, ~60 steps, mirrors Perform / Edit / Library views)
1. Cold open (10 s): full-screen visuals reacting to music, no UI. Title card.
2. The window: Perform / Edit / Library views, deck rows, Master row, clock row, pages (ab / bgpv / mixer).
3. Decks and sources: load a source (ISF, flame, light generators), preview, Browse<->Params toggle.
4. Perform controls: knobs, discrete params, crossfader, transitions.
5. FX chains: deck FX, 3x3 chain, Super Knob/Metaknob, ISF auto-binding.
6. Modulation: LFO / Seq / Audio click-to-learn, macro strip, target arcs.
7. Library and presets: browser pane, search, send to deck, pair focus view.
8. Hardware: MIDI learn, Twister native mode (ring/LEDs), OSC.
9. Output and recording: second-display output, recording, Web TV broadcast.
10. Close: themes (dark/light), download, GPL-3.0, link.
Narrative: one live "mini-set" threaded through chapters so it plays as a performance, not a feature list.

## Work breakdown and subagents
Phase 0 (me, now): confirm decisions below.
Phase 1, parallel read-only Explore agents:
- A: UI inventory: for each chapter, the ImGui ids/widgets, their labels, tooltips, view/page to select.
  Output: `.planning/walkthrough-widget-map.md`.
- B: capture infra: how `--screenshot-ui`, PBO readback, frame clock, and audio analyzer feed can support a
  fixed-timestep, file-fed run. Output: feasibility + file list.
- C: copy source: pull accurate wording from user_guide/*.md and tooltips; draft one caption per step.
Phase 2, implementation (one agent, worktree, I review):
- `TourDriver` (script parser, synthetic cursor, actions) + CLI flags + fixed-clock audio feed. Tests.
Phase 3: author `docs/tour/tour.json` (fresh agent from A+C), render, iterate on pacing.
Phase 4: ffmpeg encode (H.264 mp4 + VP9/AV1 webm, poster, .vtt, chapters metadata), `scripts/render_tour.sh`.
Phase 5: website: hero embed + chapter clips in `scripts/build_website.py`; interactive tour page; user-guide
clips; release notes (both files) + docs per repo convention.
Phase 6: verification: watch frames at chapter boundaries, caption/transcript parity, light theme check.

## Open decisions for you
1. Deterministic in-app driver (recommended) vs. simple screen recording.
2. Music: license/own track for the demo audio? Source assets must be redistributable.
3. Voice-over: captions only for v1.0 (recommended) or VO?
4. Interactive R3WRK-style page in v1.0 or after (feature freeze: it is website, not app, but the driver is new app code)?
5. Hosting: self-host on greenjon.com (FTP, size budget) vs YouTube embed.
6. Hardware segment: show the Twister (needs filming) or UI-only with the on-screen ring graphics.

Environment notes: ffmpeg (x264, libvpx-vp9, svt-av1, aac, opus), pw-record, espeak-ng, gst present; Wayland
session; no OBS/wf-recorder.

---
## Decisions (2026-10-17)
Deterministic in-app driver; captions only; interactive tour page IN v1.0; demo track: user to source (CC0/own, redistributable).

## Exploration results (3 Explore agents, code read only, nothing run)

### Feasibility: yes, most pieces exist
`TimeSource` (simulated clock), `OfflineRenderStudio` (deterministic stepper), `AudioDecoder`,
`PboReadbackPipeline.readFrameSync`, `FFmpegProcessPipe` (rawvideo -> libx264, backpressure, no drops).

**Key difference from OfflineRenderStudio:** it renders the master FBO WITHOUT the UI. The tour needs the UI
(cursor, knobs, panels) in frame. So tour mode = fixed-step loop that runs the full UI render, then reads
FBO 0 synchronously (ScreenshotCapture path, not the master FBO) and pipes to ffmpeg. Pin `io.deltaTime = 1/60`.
Window stays real (hidden-window GL context may not render; use a visible window or offscreen FBO, decide in spike).

### Required code changes (ranked by risk)
1. `CVRegistry.getElapsedRealtimeSec` mixes `System.nanoTime` start with `TimeSource` -> all TIME LFOs/SEQs wrong.
   Also `anchorTimeNs`/`lastRenderTimeNs` init (CVRegistry.kt:10,16,18,167). Reseed from TimeSource; force targetFps 60.
2. Audio feed in OfflineRenderStudio.kt:162-172 is wrong for lockstep (one 512 window/frame = ~30% of audio dropped,
   beat clock ~64% speed, non-causal, double-fed under supersampling). Replace with sample-cursor feeder: feed all full
   512-blocks with end <= N*sr/60. Reset beat detector/biquads/follower/beat anchor first. Never `AudioEngine.start()`.
3. Randomness: modulator ids are UUID (seeds RANDOM LFOs), `Random.Default` in MorphState/CvModulator/ModulatableParameter/
   queues, `Math.random()` in PerformanceTransitionsControls:90. Add seeded `DeterministicRandom`; deterministic ids in tour mode.
4. Async loads: DeckOps/FxOps/TransitionOps load on IO thread, applied at next GL drain. Tour: join + drain at the scripted frame.
   `step()` currently skips those drains, MacroEngine.tick, queue advancing (pollQueueAdvance), PV deck; the tour loop must do all.
5. Smaller leaks: FxChain.kt:273 nanoTime; DATE/iDate wall clock; iFrameRate from ImGui; Renderer shared lastRenderTime/frameIndex
   across decks (TIMEDELTA 0 for decks 2-4; also a live bug); FlameGovernor (set flameBudgetMs=0); force libx264.
6. Isolation: --tour implies no MIDI/OSC/Link/broadcast/file watchers/discovery/live audio, startupBehavior=EMPTY, no pref/session
   save, no autoSave into library/presets, lockCursorOnKnobDrag=false, fixed iMouse.
7. Possible existing defect: FFT texture (iChannel0 row 0) may be all zeros (copyMagSpectrum never fed). Verify; matters for audio-reactive shots.
8. Existing live bug: `--screenshot-after-frames` compares a counter reset every second (only works for N < fps); use monotonic frameIndex.

### Driver design details (from UI inventory)
- Input injection point: after `imguiGlfw.newFrame()`, before `ImGui.newFrame()` (UIManager.kt:287-296): set io mousePos/mouseDown/keys.
- Widget targeting: add `WidgetRegistry.note(key)` (stores getItemRectMin/Max) after target widgets. Knobs already stable:
  `perf_{page}_r{row}_c{col}`. Use `##` suffixes (perf_tab_ab, perf_gen_badge_A, rack_chevron_*, mode_presets, ##browserPaneSearch,
  ##crossfader_slider_track, ##ui_theme...). Do NOT key on `##title_badge_*_x_y` (coords in id).
  Custom-drawn with no ImGui item need explicit rects: deck title badges, under-knob strip labels, overhangButton.
- Scene setup actions are single calls: performancePageId (ab/bgpv/mixer), libraryMode, openParams(moduleId), focusPair(tag),
  PreferencesPanel.open(Category), selectGen, LibraryNavigation.setViewMode.
- Output script: tour.json -> frames + .vtt/.srt + chapters + caption list for the web page + per-chapter clip ranges.

### Caption/doc findings (fix docs or avoid in captions)
- Pages: three (A/B, BG/PV, MIXER). WS says four incl. MASTER; README says two. FX chains: 3 slots (VS and README say 4; WS "two slots").
- Edit view vs Library: docs conflict on whether Edit hides Library; "Pair view" missing from WS "three views". Caption "Perform/Edit/Library".
- Avoid a generator count (34 vs 8 vs ~36). FBK tab / Cell Config / MACROS tab / Faceplate / Classic F4 references are stale.
- Prefs shortcut Ctrl+P vs Ctrl+,; Twister bank-button description conflicts. No user-guide page documents the themes.
- Sequencer and MIDI columns are off by default: tour must enable. Draft caption facts per chapter exist (agent report; regenerate from docs when authoring tour.json).
- Not showable UI-only: Twister rings/LEDs/knob-13 pulse, real MIDI learn input, TouchOSC, 2nd display, Spout/Syphon/PipeWire,
  web relay (needs token), Link peers. Show Preferences/monitor panels + on-screen knob rings; Twister segment = on-screen graphics or filmed insert.

## Revised phases
0. Doc fixes for the contradictions above (small, can run in parallel).
1. Spike (1 agent, worktree): `--tour` skeleton: fixed clock, full UI render, FBO0 sync readback -> ffmpeg, trivial script
   (move cursor, click page tab). Prove determinism: render twice, compare frame hashes. GO/NO-GO gate.
2. Determinism hardening (clock leaks, audio feeder, seeded random, drains/ticks, isolation). Tests.
3. Driver features: WidgetRegistry, actions (move/click/drag/type/set-view/load/hold), cursor+ripple+highlight overlay, caption overlay.
4. Author tour.json (storyboard x widget map x captions), pacing iteration, light-theme pass.
5. Encode + outputs (mp4/webm, poster, vtt, chapter clips); scripts/render_tour.sh.
6. Interactive tour page (website/templates + build_website.py): clips + captions list + chapter jump + counter + transcript.
7. Docs: both release-notes files, developer docs (tour script format), user-facing mention, per repo "done" definition.
