# Audio signals: better transients, a hi-mid band, per-band energy (plan)

Started 2026-11-04. Status: phases 0-3 BUILT 2026-11-04 (desktop and web, tests pass, committed, live on the TV (hits since v23, HitScale since v25), in-app check pending). Phase 4 waits until 1-3 are seen in the app. Target: v1.0. Desktop and web TV.

**Results that changed the plan:** see *What the mixes showed* at the end. The calibration target changed, and a third bug was found (a hold was added).

The ideas come from Synesthesia's public descriptions of its audio signals (`syn_*Hits`, `syn_*Presence`, four bands). We have not run Synesthesia and its algorithms are not published, so everything here is our own design. Do not try to match its output.

A second-hand write-up of its signal path (received 2026-11-04, no sources) guesses at the formulas: adaptive threshold for hits, slow level for presence, weighted clocks. Treat those as ideas to test, not facts about Synesthesia.

## Decisions

| Their feature | Decision | Why |
|---|---|---|
| Hits | **Build (phase 1):** rebuild `audio_flux_*` | Transients drive much of the stock set, and the current signal has the problems below |
| Mid-high band | **Build (phase 2):** `audio_himid` | Mid covers about 500-1900 Hz and High starts at 5 kHz, so snare crack and vocal edge have no band |
| Presence | **Build (phase 3):** per-band energy | `AudioAgc` already keeps a short and a long level per band, so this is nearly free |
| `syn_Time`, `syn_BassTime` etc. | Skip | A base Speed plus an audio modulator, integrated by `LSD_PHASES`, is already `dt * (k0 + k1 * level)`. A rough check finds audio on a speed input in 30 of 37 stock sources |
| `syn_CurvedTime` | **Optional (phase 4)** | Modulators have no power curve. A per-modulator Curve would also shape anything else, not only clocks |
| Beat helpers (`syn_RandomOnBeat`, `syn_ToggleOnBeat`, `syn_BPMSin*`, `syn_BPMTwitcher`) | Skip for now | LFO waveform, slope, morph and hold plus sample-and-hold cover them, the twitch only approximately. Revisit if a hard-eased beat shape is missed in use |
| `syn_LevelTrail`, FFT variants | Defer | Only a few shipped shaders read the audio textures at all. Our FFT already has instant attack and a 0.08 s release, and the Spectrogram does its own tilt and log scale. Log resampling plus tilt in a shared texture row is a candidate definition of "juiced" when several shaders want it |
| `syn_Intensity`, `syn_FadeInOut` | Defer | Described too loosely to define well; Energy and Drop already cover song shape |
| Audio-triggered look changes | Defer | Desktop is a performer's tool; section modulators already respond to the song; web autopilot already cuts on drops |
| Audio data over OSC | Defer | Output feature, not analysis |

## Problems with `audio_flux_*` today

From [AudioEngine.kt:652](../src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt) and [dsp.js:144](../web/dsp.js):

1. **It depends on block size (desktop) and frame rate (web).** It is the RMS rise since the previous block or frame, against a fixed scale. A rise spread over several short JACK blocks reads smaller than the same rise in one long block. On the web, 60 Hz and 144 Hz screens get different values.
2. **It probably fires on sustained bass.** An 11.6 ms RMS window is shorter than one cycle of a 50 Hz note, so the level ripples and each ripple counts as a rise. Phase 0 measures this.
3. **It does not compare a rise with recent activity,** so it cannot tell a hit from a busy passage.
4. **The docs call it spectral flux,** but it is a difference in band RMS.

Usage to protect (files in stock defaults, sources and web content, duplicates included): `audio_flux_bass` 183, `audio_flux_high` 66, `audio_flux_mid` 2, `audio_flux_amp` 0.

## Constraints

- **Detectors keep their inputs.** `BeatTrackerEngine`, `DownbeatDetector` and the web's `cvState.raw` keep today's raw `bassFlux` / `onsetStrength`. Changing the tracker's input is a separate job with its own risk.
- **Audio thread rules:** no allocation, no locks.
- **Desktop and web stay identical:** pure classes on both sides, checked by golden vectors (`UPDATE_WEB_VECTORS=1`).
- **Saved presets keep working:** no existing signal ID changes meaning or disappears.
- **Stock content does not use new IDs yet:** web content reaches clients on its own schedule, and the Android app bundles a copy. Adopting the new signals in stock is a separate later step.

## Phase 0: one table, and a baseline

**Signal table.** Signal IDs are hand-listed in `Evaluators.kt`, `CVRegistry.kt`, `CvModulator.kt`, `ClipboardManager.kt`, `AudioModulatorSection.kt`, `AudioEnginePanel.kt` and `CvTheme.kt`, and on the web in `evaluator.js`, `renderer.js`, `pairing.js` and `dsp.js`, plus `scripts/gen_stock_web_presets.py`. Add `cv/AudioSignals.kt` (ID arrays, kind checks, label) and read the Kotlin lists from it, so phases 2 and 3 add table rows instead of editing seven files. No behavior change. The web lists stay by hand, covered by a vector test.

**Baseline harness.** A test-only tool that feeds audio through `processAudio` (`export/AudioDecoder.kt`, `resetForOfflineFeed`) at chosen block sizes and writes each signal to CSV.
- Synthetic: sustained 50 Hz note, kick alone, kick over a sustained bass, 16th-note hats, breakdown then drop.
- Real: the DJ mixes used for the AGC calibration, or replacements.
- Record the old signal's peak percentiles per band. Phase 1 calibrates against these. Keep the music out of git.

## Phase 1: rebuild the transient signals

New pure class `audio/TransientDetector.kt`, ported to `web/dsp.js`. Same IDs, so all presets pick it up.

**Measuring the rise on a fixed time grid.** Inside the block, add up squared samples per band into hops of about 5.8 ms. Each hop gives an 11.6 ms RMS and `rise = max(0, level - level two hops ago)`. Publish the largest value since the last block, so no peak is lost. 11.6 ms is what a 512-frame block uses now, which keeps calibration close.

**Scoring it against recent activity.**

```
localRise = one-pole mean of rise, tau about 1 s
contrast  = rise / (localRise + eps)
gate      = smoothstep(C_LOW, C_HIGH, contrast)
signal    = clamp01(Response * gate * rise / (bandScale * agcDiv / FIXED_SCALE))
```

- The gate should remove ripple on sustained notes, while real attacks stand well above their local mean.
- Keeping the AGC divisor means a breakdown still reads quiet: its reference is frozen, so a soft hat in a breakdown does not come out full size. A purely relative measure would lose this.
- **Try two gates on the harness and keep the better:** the local mean of the rise (above), and the local mean of the band's level (`rise / (localLevel + eps)`). Ripple on a sustained note grows with its level, so the second may reject it better; the first may suit sparse material better.
- No jump to 1.0 and no built-in exponential decay. That would make every hit the same size and lose accents and ghost notes. Each modulator's envelope follower still shapes the signal, and stock responses rely on that.
- `audio_flux_amp` remains the weighted sum of the bands (no stock use).

**Web.** Same class, fed `(levels, dt)`. Analysers give one level per frame rather than sample-accurate hops, so the rise is measured over the time since the last frame and scaled to the 11.6 ms reference. Moving the bands into `onset-worklet.js` for true hops is only worth it if the frame-rate check below fails.

**Calibration.** Set `bandScale` and `C_LOW` / `C_HIGH` so the peak percentiles on real music land near the phase 0 baseline. The aim is that stock responses need no retuning: kicks stay as big, and the ripple goes away.

**Tests** (`TransientDetectorTest`, vectors in `web/tools/`):
- Sustained 50 Hz note reads near 0.
- A kick over a sustained bass stays clearly above that note alone.
- Peak on the same kick stays within 15% across 64, 128, 512 and 1024-frame blocks.
- Web output stays within 20% at 30, 60 and 144 fps (15% was not reachable: 144 fps catches a peak more closely).
- A quiet hit in a breakdown reads below the same hit before it.
- Silence reads 0, and Audio Response still scales the signal.
- Beat tracker and downbeat tests pass unchanged.

**UI text.** `Transient (Flux)` becomes `Transient`; the tooltip says "drum hits and attacks, not the ripple of sustained notes". Fix "spectral flux" in `audio_dsp.md`.

**In-app check** (add to `.planning/app-checklist.md`): stock sources with kick responses, on sustained-bass music and on four-on-the-floor. The new signal should look at least as punchy and less jittery.

## Phase 2: Hi-Mid band

- Band-pass at about 3.2 kHz with Q about 1.05, which puts its edges near 1.9 and 5.2 kHz.
- New signals `audio_himid` and `audio_flux_himid`. Label: "Hi-Mid (Crack)".
- Append `HIMID` after `HIGH` in `AudioAgc`, keeping existing indices. `SCALE[HIMID]` is calibrated with the phase 0 harness. Regenerate `agc_vectors.json`.
- `SectionWatch` inputs do not change. Existing bands do not move.
- Add to the band dropdown, the audio meter, `CvTheme` and the web audio graph (a fifth analyser), including the delay line behind SYNC.
- Add it to the Shadertoy-style uniforms only if a shader needs it.

## Phase 3: per-band energy

- New signals `audio_energy_bass`, `_mid`, `_himid`, `_high`, bipolar -1..1.
- `energy(band) = log2(short[band] / ref[band])`, clamped, using the 1.5 s and 20 s levels `AudioAgc` already has. 0 in warm-up and silence, same as `audio_energy`.
- The floor must be per band, because the high band's RMS is far smaller than bass. Set it from the harness.
- Treated like `audio_energy`: no envelope follower, no Audio Response, same bipolar handling. The hardcoded `audio_energy` checks in `CvModulator.kt`, `Evaluators.kt` and web `BIPOLAR_SOURCES` read the table instead.
- `audio_energy` stays as it is (bass-weighted mix).
- UI: extra entries in the Section dropdown: Energy (vs. the song), Bass Energy, Mid Energy, Hi-Mid Energy, High Energy, Drop.
- Tests: hats enter and only High rises; bass drops out and Bass goes negative while High holds; warm-up and silence read 0; web vectors match.

## Phase 4 (optional): modulator Curve

- A `curve` exponent on audio modulators, default 1 so existing presets do not change: `signal^curve` after the follower, before depth.
- On a Speed input, this gives a slow drift in quiet passages and hard acceleration on loud ones.
- Needs the DTO field, a UI slider, `Evaluators.kt`, `web/evaluator.js` and regenerated evaluator vectors.
- Decide after phases 1-3 are seen in the app. Skip it if Energy and Drop on Speed already give that feel.

## Each phase is done when

Code, tests and web vectors pass; `RELEASE_NOTES.md`, `docs/release_notes.md`, `docs/developer/audio_dsp.md`, `docs/developer/modulation.md`, `docs/user_guide/modulation.md` and tooltips are updated; the in-app checklist has the new items. Publishing the web TV (bump `TV_VERSION`) and an app release stay separate, explicit steps.

## Risks

- **Phase 1 changes the feel of everything using kicks and hats.** Calibration and the in-app check are the protection. The old formula stays in the code for the detectors, so reverting the published signal is one line.
- **The gate could swallow real hits in dense music.** Tune on real mixes, not only synthetic audio. Soften the gate rather than chase every case.
- **A fifth analyser costs CPU on phones.** Measure it in the Android app before publishing.

## Open

- Mixes that would widen the calibration: see below.

## What the mixes showed (2026-11-04)

Three mixes, about 2.7 h: Extra Spectrum "From Saw To Swing" (44.1 kHz), Magical Bass #4 (48 kHz), richimix "Balcik Bulgaria" (44.1 kHz, 128 kbps). Values are what a modulator sees through a 3 ms / 120 ms follower at 60 fps. `AudioMixReportTest` reproduces this.

- **The old signal was pegged**, so "calibrate to the baseline" was the wrong target. Bass: typical 100 ms peak 0.84-0.87, top 10% at 1.0, above 0.5 for 53-54% of the time, about 1 hit/s. New at 0.167: 0.23-0.38, above 0.5 for 19-26%, 1.2-1.7 hits/s. Mean drive falls from about 0.55 to 0.26-0.33 in the bass, so stock responses move less on average.
- **Block size mattered far more than expected.** Held 50 Hz note, old: 1.00 at 64 frames, 0.15 at 512, 0.22 at 1024. New: 0 at all of them.
- **Modulators read once per frame, so short spikes were lost.** Not in the original plan. Without a hold, a 60 fps reader at 64-frame blocks caught about a fifth of the kicks.
- **The rise-based gate beat the level-based gate** on every mix: fewer frames pegged and more separate hits at the same scale, so the level gate was dropped.
- **Web frame rate:** the old formula was already fairly close (0.317 / 0.322 / 0.260 at 30 / 60 / 144 fps) and the new one is 0.317 / 0.322 / 0.374. So the web's gain is the gate, not frame-rate independence.
- **Hi-Mid level** at `SCALE` 2.4: p50 0.35-0.36, p90 0.73-0.80, between mid and high.

**Genre check (2026-11-04, no constants changed):** 30 seeded random tracks each from the user's library, about 8.7 h: modern jazz, older jazz, rock, dub.
- **The gate holds up.** Above the bass it swallows 0-4% of large rises. In the bass it swallows 10-17% at 512 frames (up to 22% at 64), and only 4-14% of those look like real attacks (band up 1.5x, 100 ms against the 100 ms before). So about 1-2% of large bass rises are possibly missed, the rest being ripple. The attack test is a heuristic: a kick over a held note may not raise the 100 ms level that much.
- **Buffer size stays immaterial on real music.** Bass mean at 64 / 512 / 1024 frames: rock 0.29 / 0.29 / 0.31, dub 0.34 / 0.35 / 0.37, older jazz 0.25 / 0.26 / 0.27.
- **Open: acoustic music reads softer above the bass.** Mean mid, hi-mid and high is 0.16-0.24 on rock and jazz against 0.32-0.43 on the electronic mix and 0.34-0.42 on dub. It is mostly the fixed scale (the ungated signal is also low), and no single scale suits both. Decide from the in-app look on rock. If hats and snares feel weak, the fix is normalising hits against the band's recent strong hits, frozen in breakdowns like auto gain.
- Sample lists are not kept. `AudioMixReportTest` takes any file list and `LSD_AUDIO_BLOCKS`.

**Drum and bass check (2026-11-04):** Jezza, "Autumnal Liquid Drum & Bass", 2.4 h. The fixed scale misses in both directions: bass reads softest of any genre (mean 0.18, sustained sub raises its auto-gain reference) and high is still pegged (p50 0.87, 0.49 hits/s).

**Phase 1b, BUILT 2026-11-04 (`HitScale`):** normalise each hit against the band's recent strong hits: `hit / max(ref, agcScale / 3)`, `ref` climbing with a 0.05 s attack and falling with an 8 s decay, frozen while the section watch holds. Attack chosen from 0.0005 / 0.05 / 0.15 / 0.4 s on all genres. First trial with an instant reference: Spread of mean across six sets, current to trial: bass 0.18-0.35 to 0.23-0.31; mid 0.18-0.35 to 0.24-0.32; hi-mid 0.16-0.43 to 0.22-0.32; high 0.19-0.51 to 0.22-0.42. p90 for hi-mid on rock goes 0.58 to 0.87 and drum and bass hits per second in high 0.49 to 0.86. Tests cover a breakdown staying quiet, the drop landing full, and recovery from one huge hit (over 0.4 on the next hit, over 0.7 after 4 s). A later frame-rate check belongs in the browser.

**Separate bug found, not fixed:** the tempo tracker depends on buffer size. Synthetic 128 BPM kick: 200 BPM at 64 frames, 193 at 128, correct at 512 and 1024. The Jezza mix (about 174): at 512 frames 46% of locked time at 86-87, 23% at 115-116 (a 2/3 error); at 1024 frames 174 appears; at 64 frames it wanders. Predates this work (`beatDetector.processBlock` input is unchanged).

**Coverage gap left:** a quiet line-in or room mic on real audio.

**Not fixed here:** the web's mid filter has Q 1.0 and the desktop's 0.707, an older mismatch.
