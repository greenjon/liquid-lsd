# Scripted pass, round 3 (2026-11-06)

Everything ran from `e59bef8` on branch `fixes/app-verify-round3`. App cases run in a throwaway worktree with test-only hooks (`apply_probe3.py`, `setup.sh`). Browser tests are in `tests/web/`.

## Bugs found and fixed

| Found by | Bug | After |
|---|---|---|
| Browser, GPU tier | Web TV GPU probe timed `gl.finish()`, which doesn't wait in Chromium: 0.3 ms on NVIDIA and Intel, heavy scenes for all | Reads a pixel from the target: NVIDIA 4.4 ms on, Intel 61 ms off |
| Browser, synthesized music | A breakdown starting during a fade lost its calm look, since the fade's end re-planned 16 bars on | Calm at the next bar line; `autopilot.spec.ts` fails on the old code |
| Browser, offline | Six code files missing from the service worker's shell; `?kiosk` and other queries never opened offline | Opens offline after one visit, four address forms |
| Browser, kiosk | Canvas cursor rule beat the idle rule; pointer never hid | Hides |
| Sequencer sheet | Toolbar wrap measured from the line start, so it never wrapped; Clear clipped, Snap off the edge at 1280x720 | Wraps; Clear and undo checked |
| Simulated Twister | Knob 16 cleared knob 14's cursor; inserts ignored it and a second item went before the first | Own anchor; unit test added |
| UI cases | Edit on an empty deck had no side rail | Rail drawn |
| Old presets | A damaged preset failed with no message | Toast |

## What ran

- **Simulated Twister** (`cases3.py`): the real profile and command path, fed through MidiEngine's queue, with the app's output logged. No real port opened. All pass. Not covered: real firmware, feel, LED looks.
- **UI cases** (`cases4.py`, `cases5.py`): binding, pair view, Space, Edit rail, wheel, Revert, Dry/Wet label, swap, Super Knob, sequencer toolbar, old presets, shipped-shader update. All pass, `results/ops.txt`.
- **Old session**: a real run under Xvfb on the user's session copy with removed fields added: restores. A field missing required keys drops the whole session to empty with only a log line (seen by accident, not changed).
- **Music** (`music.py`, `results/music.txt`): synthesized 128 BPM with two breakdowns, through the real offline feed.
- **Browser** (`tests/web`): Chromium on the NVIDIA, Firefox on Intel, WebKit in the Playwright container. WebKit here is not iPhone Safari.
- **Older checks**: `ui_check`, `tape_check`, `shader_check` still pass after the web fixes.
- **Kotlin suite**: 1551, one failure, `AbletonLinkEngineTest`, known.

## Measurements

- **Desktop audio, 128 BPM**: bass 0.47 in the groove, 0.06 within 3 s of each breakdown and steady to its end. Drop called 0.57 s after the bass returns (1.2 beats), both times, no false drops. Audio Response 0/100/200%: bass 0 / 0.47 / 0.69, Energy and Drop unchanged. (The offline feed reports the script's clock, 128, so this says nothing about BPM. Corrected in round 4.) 20 dB down reads quiet about 60 s, then normal; music after silence levels in at once.
- **Off-air frame time**, Nebula and Apollonian, with another run going: fader on A 31-34 ms, centre 35-42 ms, on A with half rate off 39 ms.
- **Desktop long run**: simulated hour, a new preset every 30 s (120 loads): GPU buffers 33-36, 249-273 MB, no trend; JVM heap 360-554 MB, sawtooth, too short to rule out slow growth. Real-time hours not run.
- **Web beat phase**: steady after about 100 s: -70 ms at 128 BPM, +110 ms at 174, measured against the audio element's clock. BPM reads 127.7 at 128 and 174.0 at 174.
- **Web look changes**: fades end on bar lines every 17 bars. Calm look 1 s after a breakdown is detected (detection about 5.4 s in); hard look 0.7 s after the drop.
- **Web channels**: all 132, no console errors, 16.7 ms frames on the NVIDIA. 53 fullframe scenes, none on a trail or bloom chain.
- **Web network loss**: NO SIGNAL in 4.4-5.4 s, back in 0.1-9.7 s.
- **Web 25-minute soak**: 60 fps, nodes 843 and listeners 130 flat, heap 4.3 to 4.9 MB, textures 29-52 by scene, programs level off at 62, no errors.

## Decisions (2026-11-06)

- Audio Response 0% keeps Energy and Drop running: kept, tooltip and guide reworded.
- An 85 BPM synthesized track reads as 170: the user sees no problem so far and will watch real slow shows.
- CH 34 `web-build-drop-spiral` nearly black on one sample: fine.

## Limits

Synthesized music has very clean transients, so real music may do worse. "Passes" on the Twister means messages and logic. Frame times were taken with other runs going, so only their differences mean much. The Playwright workflow (`.github/workflows/playwright.yml`) is not committed: its runner has no GPU and no generated music.
