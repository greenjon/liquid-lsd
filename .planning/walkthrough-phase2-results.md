# Tour renderer phase 2 results (determinism hardening)

Branch: worktree-agent-ab9c08321224f071a. Contract: per-frame SHA-256 of the raw FBO-0 readback
(`<out>.hashes`); encoded video frames compared with `ffmpeg -f framemd5` (audio/container excluded).
Check: `scripts/tour_determinism_check.sh <script.json> [--tour-hidden]`. Encoder: libx264 (installed now), software only.

## Hash runs (each = two fresh renders of the same script, 1920x1080 visible window)
| Scene | Content | Frames | Result |
|---|---|---|---|
| t1 | empty decks, tab clicks, tooltip hover | 300 | identical (3 pairs) |
| s1 | kifs (ISF) on A, mandala on B, preset mandala-7 on BG; no audio | 360 | identical (5 pairs after fixes) |
| s2 | mandala with audio_bass/audio_high/LFO TIME/LFO BEAT modulators + kifs with audio_amp + LFO; beat.wav fed by frame clock and muxed (aac) | 360 | identical raw, identical encoded video (1 + 3 sparse pairs) |
| s3 | kifs + preset, audio-reactive, cursor moves + clicks across all three pages | 420 | identical raw, identical encoded video (1 + 3 sparse pairs) |

## Leaks found and fixed this phase
- Background scan timing (FileSystemManager follow-up scan) -> runs inline in tour mode, TTL rescans disabled; ISF file watcher off; `PresetManager.awaitIo()` barrier before every frame and after each scripted deck action (DeckOps/FxOps/TransitionOps drained by the normal loop the same frame). Sleep warm-up replaced by 10 uncaptured frames.
- Tooltip hover delay, pulse alphas (sin(currentTimeMillis)), toasts, learn timeouts, library auto refresh, FxChain dirty check -> TimeSource.
- imgui.ini persisted window layout between runs -> disabled in tour mode.
- Real pointer/keyboard GLFW callbacks (wheel scrolled the browser list, hover rows) -> callbacks removed in tour mode; focus pinned.
- Per-source TIMEDELTA/FRAMEINDEX (Renderer shared them across decks), iFrameRate=60 in tour mode.
- Randomness: DeterministicRandom (seeded from script `seed`) used by CvModulator/ModulatableParameter/MorphState defaults, queue managers, PerformanceTransitionsControls, modulator/macro ids.

## Audio
Sample-cursor feeder (TourAudioFeeder): all full 512-sample blocks with end <= frame*sr/60, block timestamps on the simulated clock,
analyzer reset first (AudioEngine.resetForOfflineFeed, MANUAL clock at script `bpm`). Never AudioEngine.start().
Measured: audio_bass reaches 1.0 with the test tone. FFT texture (iChannel0 row 0): measured max 0.000 during that run;
code shows BeatTrackerEngine.magSpectrum is only written by computeComplexSpectralDifference, which no live path calls -> the FFT texture is always zero (live bug, not fixed).

## Open
- Unit test suite: see final report (WebEvaluatorVectorsTest failed once in a full run and passed regenerated with identical output; re-verify).
- Not exercised: FxOps/TransitionOps scripted actions, shuffle queues, second theme, hidden window with real content, other machines/GPUs.

- Hidden window (`--tour-hidden`) with s3: identical raw + encoded, 22 ms/frame (visible 28 ms). `./gradlew test` green on the final tree (one earlier full run failed WebEvaluatorVectorsTest; regenerated vectors were byte-identical, did not reproduce).
