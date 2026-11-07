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

`rtsoak.py`: the real app in real time, Auto VJ stepping a queue of 60 presets every 32 beats. @SOAK

A first 40-minute run with a queue that never moved (my session file was wrong) is in `results/rtsoak_idle_40min.txt`: resident memory 1016 to 1631 MB, steady. ZGC under `-Xmx2g` grows until it needs to collect, so only a longer run can say.
