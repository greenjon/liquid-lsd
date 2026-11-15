# Scripted pass, round 4 (2026-11-07)

Throwaway test build as in round 3 (`round3/setup.sh`, plus `apply_probe4.py`). Branch `fixes/app-verify-round4`.

## Bugs found and fixed

| Found by | Bug | After |
|---|---|---|
| UI cases | One Ctrl+Z undid two steps in the pair view and Chain view: the pair bay handled undo as well as `beginFrame` | One step; `pair_undo_steps`, `chain_undo_steps` |
| UI cases | Reset Parameter to Default couldn't be undone: the undo step only held modulators | Value and range come back |
| UI cases | Right-click menus near the bottom were cut off (explicit position skips ImGui's fit), hiding Metaknob Target on lower Edit rows | Open away from the edge |
| Real zips | Unzipping a release over an older one overwrote an edited shader, because the zip carried `library/sources` | Sources ship only in the jar; a fresh run with the folder deleted rebuilds all 37. Not run on a built zip |

## Crash test (`monkey.py`, `fuzzlib.py`, `results/monkey.txt`)

30 seeds, about 570 random actions each (clicks, drags, keys, wheel, Twister messages), 41 damaged files in the library. No network, invisible X server, outside commands stubbed. No crash, hang or unexpected exception. The 103 logged errors were the damaged files themselves. GPU buffers 29-44 with no trend within a run. A run is 2 simulated minutes from a fresh start, so this does not reach deep state.

## Real music (Mixxx readings as the reference; only numbers saved)

Web tracker, `tempo_report tags`, 156 tracks, one minute each:

| Mixxx BPM | n | exact | with octaves | locked |
|---|---|---|---|---|
| 60-95 | 39 | 18% | 79% | 63% |
| 95-110 | 30 | 73% | 73% | 75% |
| 110-125 | 29 | 100% | 100% | 88% |
| 125-135 | 30 | 97% | 97% | 83% |
| 135-190 | 28 | 86% | 86% | 79% |

Desktop sections, 18 tracks, first 5 minutes (`results/realmusic.txt`): time read as breakdown 0-26%, 0-2 drops, bass in those stretches well under the rest. No truth for where real breakdowns are. Its BPM column is the script's clock, not a reading.

## Real radio (`leftovers.spec.ts`, read only, chat socket blocked)

10 minutes on spaz.org/tv v29: sound in 298 of 300 samples, locked 58%, mostly 140 BPM, no NO SIGNAL, no console errors, looks kept changing, one drop.

## Other

- Web: schedule scrolls under a finger; WITH SCENES backgrounds every 33 bars; Firefox GPU test 5.25 ms, heavy on (its renderer name is masked).
- Light theme: `results/contrast.txt`, an estimate from widget rectangles in 13 views, frames in `results/contrast/`. Many entries are noise, and dark has a similar count; read the frames.
- Corrections: `NO TARGET` no longer exists; DISCRETE ring ticks are by design only on focus-mode knobs, so a source row's stepped knob moves smoothly on the Twister while its value steps.
- Kotlin suite: 1551, one known failure (`AbletonLinkEngineTest.testAbletonLinkEngineInitializationAndState`). A second Link test failed once under load and passed alone.

## Overnight

`rtsoak.py`: the real app in real time, Auto VJ stepping a queue of 60 presets every 32 beats. 8 hours, no crash and no exceptions, decks changing throughout, GPU buffers flat at 24-30. **Resident memory climbed from about 900 MB to 5.8 GB, about 10 MB a minute, and never levelled off.** That run used the Java defaults by mistake (G1, 15 GB heap limit), not the shipped ZGC with `-Xmx2g`; the launcher in the test build has no JVM options.

### The leak (`leak.py`, `mtrace_shim.c`, `results/leak_*.txt`)

Not the Java heap (about 30 MB live after each GC) and not the JVM's own native memory (Native Memory Tracking flat). A malloc tracer put the growth in one allocation site in `libnvidia-glcore`, on a thread the driver owns. It follows frames rendered, not preset loads, flames or shader recompiles.

Growth of anonymous memory, MB a minute, shipped JVM options unless noted:

| Run | Growth |
|---|---|
| Stepping queue, Xvfb, NVIDIA | 11-14 |
| Stepping, Java defaults | 10 |
| Idle queue, Xvfb, NVIDIA | 18 |
| Idle, XWayland, NVIDIA | 19 |
| Idle, debug context off | 19 |
| Idle, shader disk cache off | 20 |
| Stepping, Intel GPU | under 2 (797 to 838 MB over 24 min) |
| Idle, headless Wayland, NVIDIA, vsync off | 4.5 over 12 min, not settled |
| Idle, `__GL_THREADED_OPTIMIZATIONS=0` | flat after 8 min |
| Stepping, 40 min, 80 preset changes, `__GL_THREADED_OPTIMIZATIONS=0` | 418 to 458 MB, 5 MB in the last 28 min |

Headless Wayland with vsync on rendered almost nothing, so those two runs say nothing. The `leak_wl*.txt` flat lines are not evidence.

Fix: the Linux launchers and `gradle run` set `__GL_THREADED_OPTIMIZATIONS=0` unless the user set it. Not tested: an X server running NVIDIA's own driver, Windows (this variable doesn't apply there, and whether the leak does is unknown), and a built zip. Frame rate with it off: same GC rate on the same scene (155 against 153), and one title bar reading each of 25 fps and 30 fps with three runs sharing the GPU, under Xvfb. That doesn't settle the cost on a real screen. An 8-hour rerun with the fix has not been done.

## Slow tempo: tried, no change

Asked to try tuning the 60-95 BPM range. Result: no change to the tracker.

- **The yardstick is doubtful down there.** Readings cluster at 68-76, half of the common 136-152. Genres include Techno at 68 and Electro at 68-71, which are more likely 136-142. File tags agree with Mixxx on all 53 slow tracks, but they may be Mixxx's own, so they aren't a second opinion. Some of the "doubled" reads may be right.
- **Nothing measured separates them.** Bass pattern (median 0.76 for both slow-tagged and 130-150 tracks), onset rate and band balance don't. The comb ratio `score[2L]/score[L]` helps only against 130-140.
- **Tradeoff on 263 tracks (156 plus 107 held out), counting tracks currently read double (30) and fast tracks currently read right (77):**

| Rule on the fast reading | Slow fixed | Fast broken |
|---|---|---|
| from 130, ratio above 0.6 | 22/30 | 31/77 |
| from 140, ratio above 0.8 | 11/30 | 5/77 |
| from 150, ratio above 0.7 | 12/30 | 4/77 |
| from 165, ratio above 0.7 | 8/30 | 0/77 |

The from-165 rule looks free, but this list has one track above 165 and it would read drum and bass at 87, which was deliberately fixed to 174. The Jezza mix used for that isn't on this machine. Real truth needs a person to listen to a few of these tracks.


An earlier 40-minute run with a queue that never moved (my session file was wrong) is in `results/rtsoak_idle_40min.txt`: resident memory 1016 to 1631 MB, steady. ZGC under `-Xmx2g` grows until it needs to collect, so only a longer run can say.
