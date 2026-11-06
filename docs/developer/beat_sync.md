# Audio Beat Synchronization & Stability

This document details tempo detection, beat clock flywheel accumulator logic, and continuous beat phase generation in Liquid LSD.

---

## Architecture Overview

```
Ableton Link Session Orchestration: LinkSyncManager & AbletonLinkEngine
    │
    ├──► AbletonLinkEngine.isEnabled == true:
    │       ├─► Network Link active; updates/synchronizes BPM & beat timeline across Link peers
    │       └─► Filters audio beat tracking jitter via BeatTrackToLinkDamping (Median + EMA, 0.5 BPM / 4-beat hysteresis, >= 0.5 beat phase error)
    │       └─► Commits tempo updates and phase alignment directly to AbletonLinkEngine
    │
    └──► AbletonLinkEngine.isEnabled == false:
            └─► Network Link disabled; local clock sources operate independently

Ableton Link on (a separate on/off, not a clock source): AbletonLinkEngine (Native JNI / Carabiner TCP)
    └─► updateClockAnchor() on Render Thread 0 (CVRegistry.updateAll) → CVRegistry.updateBeatAnchor(linkBeat, linkBpm, nanoTime)

Clock Source Selection: ClockSource (MANUAL | AUDIO_TRACKER)
    │   (saved values "MANUAL_TAP" and "ABLETON_LINK" load as MANUAL)
    │
    ├──► AUDIO_TRACKER: JACK / Java Sound Callback (Audio Engine Thread)
    │       ├─► BeatTrackerEngine.processMultiBand() / processBlock()  (the tempo)
    │       ├─► BeatPhaseLock.process() / steer()  (where the beat is; the comb runs on its own thread)
    │       └─► CVRegistry.updateBeatAnchor(totalBeats, bpm, nanoTime)
    │
    └──► MANUAL: Internal Manual Flywheel & Tap Tempo Controller
            └─► CVRegistry.alignBeatPhase(alignedBeats, manualBpm, nanoTime)
```
              │  (@Volatile primitive fields update: zero heap allocation)
              │
        Render Thread 0 (Every Frame @ 60Hz–144Hz+)
              │
              └─► CVRegistry.getSynchronizedTotalBeats()
                      └─► Sub-millisecond forward clock interpolation; a small backward step coasts at 80% of tempo
                              │
                        Evaluators.kt  ← beatPhase, LFO 1/2, Sample & Hold
```

---

## Beat Tracker Model (`BeatTrackerEngine.kt`)

Liquid LSD features a real-time beat tracker and continuous phase generator modeled on the beat tracking algorithm by Adam Stark and the Dan Ellis causal dynamic programming beat tracker.

### 1. Complex Spectral Difference Onset Detection Function (ODF)
- Evaluates a zero-allocation 512-point Radix-2 Cooley-Tukey FFT with pre-computed twiddle factors and Hann windowing.
- Measures the Euclidean distance between expected complex STFT spectrum bins $(\hat{r}_k, \hat{\theta}_k)$ (predicted from 2nd-order phase trajectory $\hat{\theta}_t = 2\theta_{t-1} - \theta_{t-2}$) and observed spectrum $(r_k, \theta_k)$:
  $$\text{ODF}(t) = \sum_{k} \sqrt{ |r_k \cos \theta_k - \hat{r}_k \cos \hat{\theta}_k|^2 + |r_k \sin \theta_k - \hat{r}_k \sin \hat{\theta}_k|^2 }$$
- **Principal Phase Normalization**: $\hat{\theta}_t$ is wrapped into $[-\pi, \pi]$ before synthesizing expected complex spectral bins $(\hat{r} \cos \hat{\theta}, \hat{r} \sin \hat{\theta})$, eliminating spurious transient spikes on continuous pitched tones.
- Suppresses stationary tones while detecting soft pitched attacks and percussive transients.

### 2. Tempo Estimation

**Fixed input grid.** The tracker counts its input in units of `fftSize` (512) samples to turn lags into tempo, so `BeatHopper.kt` feeds it exactly one call per 512 samples, whatever the audio buffer size, and measures the band levels and rises over the same grid. Before this, one call per buffer read a 128 BPM kick as 200 BPM at 64 frames. The downbeat detector reads the hopper's rises and level too. `AudioEngine.processAudio` is the only caller.

**Onsets per band.** The history holds each band's half-wave rectified rise in level, divided by the band's own mean rise over about 6 s (`BAND_MEAN_RATE`), so a sustained note does not fill every lag and a loud kick does not decide alone.

**Spread over blocks.** All of this runs in the audio callback, whose budget at a 64-frame buffer is 1.45 ms. `stepTempoEstimate` computes a share of the autocorrelation lags per block, over `tempoEstimationIntervalBlocks` (16, about 186 ms; 4 during the first 5 s of history, when each pass is cheap), then `estimateTempo` finishes in one block. Measured on the dev machine: mean 77 µs per block, p99 about 265 µs, worst about 640 µs. The time delta passed to the stability logic scales with the interval. Nothing is estimated until 1.5 s of history (`MIN_HISTORY_SEC`): two onsets are not a tempo, and a wrong first lock jumps the phase.

**Choosing the beat**, in order:
$$\text{AC}(d) = \sum_{i=0}^{W-1} \left(1 - \frac{i}{W}\right) \sum_{b} H_b[t-i] H_b[t-i-d]$$

1. **Autocorrelation** over a 4 s Bartlett window, at every lag out to twice the slowest tempo's period.
2. **Comb and prior.** A lag's score is the mean of $\text{AC}(kd)/k$ for $k = 1..4$, times a log-normal preference one octave wide around a 0.5 s period (120 BPM). A beat repeats at its multiples, while a syncopated gap does not, and the prior stops a whole bar (which always correlates best) from winning.
3. **Where the accents fall (`settleMetre`).** Autocorrelation alone cannot separate the beat from a 3:2 or 4:3 relative. The six strongest score peaks are each folded over the last 8 s (`foldedStrike`, at the fractional period and a little either side, because half a block of error smears the accents over 8 s), and the strength of the best position is compared. On the true beat every cycle lands on an accent; a 1.5-beat grid alternates between an accent and an off-beat. The comb's own pick needs beating by 25%, and a lag at the locked tempo or an octave of it gets a 30% bonus, so a groove that shifts for a bar does not move the tempo.
4. **Unwrapping.** The half period replaces the lag while its fold reaches 55% of the full one and it is 165 BPM or under, which turns kick-and-snare half-time into the quarter note. Hats between the beats fall short.
5. **Sub-block parabolic interpolation** on the autocorrelation peak.

The stability gating and the tracking inertia ($\pm 2.0$ BPM per beat) then apply as before.

**Measured** with `AudioTempoReportTest`, on 313 tracks from a personal library against BPM tags from analysis software (a second opinion, not the truth), and on a 2.4 h liquid drum and bass mix at about 174 BPM with `AudioMixReportTest`:

| | Before | After |
| :--- | :--- | :--- |
| Tags: agrees | 65% | 74% |
| Tags: agrees or an octave off | 72% | 83% |
| Tags: 3:2 family errors | 12% | 8% |
| Tags: time locked | 64% | 91% |
| Mix: on the tempo or an octave off, of all time | 45% | 67% |
| Mix: exactly 174, of locked time | 17% | 66% |

`TempoSuiteTest` holds grooves of known tempo and the buffer-size checks. The web TV's standalone tracker uses the same estimator with a few differences; see [Web Subsystem](web_subsystem.md). Known limit: a groove whose kicks are 1.5 beats apart with equal weight (some jungle) can still read 2:3, and about 15% of the mix's locked time does, at 115-116. The tracker has no sense of the drum kit.

### 3. Causal Dynamic Programming Recurrence
Maintains a circular cumulative score ring buffer evaluating causal DP recurrence:
$$\text{Score}(t) = \text{ODF}(t) + \lambda \cdot \max_{\tau \in [\tau_{\min}, \tau_{\max}]} \left\{ \text{Score}(t - \tau) - \alpha \cdot \left(\log \frac{\tau}{\tau_0}\right)^2 \right\}$$
- **Ultra-Fast Log-Table Optimization**: Pre-calculates $\log(\tau)$ table (`logTauTable`) and evaluates $\log(\tau_0)$ once per block outside the inner candidate loop, eliminating transcendental `Math.log()` calls from the real-time audio callback.

### 4. Continuous Visual Phase & Cosine Modulation Signal
**Nothing in the app reads this phase.** The beat count comes from [`BeatPhaseLock`](#beat-phase-lock-beatphaselockkt), which scored better against known beats. Provides zero-allocation primitive queries for visual rendering at arbitrary frame query timestamps:
- **Strictly $C^0$ Continuous Phase Accumulator**: Operates an internal Numerically Controlled Oscillator (NCO) phase accumulator $\phi \in [0.0, 1.0)$.
- **Smooth 2nd-Order Slew on Beat Anchors**: When beat anchors or DP peak offsets are detected at `bestBlock`, the absolute phase error between elapsed time since the anchor (`idealPhase`) and `accumulatedPhase` is converted into fractional phase error $\epsilon \in [-0.5, 0.5]$ and integrated into a 2nd-order exponential frequency slew ($k_{\text{slew}} = 0.35$). This securely locks phase $\phi = 0.0$ to true beat arrivals and eliminates open-loop drift without discrete phase snapping or visual jitter.
- **Lock-Free Atomic Seqlock Snapshot**: Synchronizes multi-word phase state (`snapTimestampSec`, `snapPhase`, `snapFreqHz`) across threads using a zero-allocation sequence lock counter (`snapSeq`). Eliminates torn reads between the real-time JACK audio thread and high-framerate render threads without locks, blocking, or heap allocations:
  $$\phi(t) = \left( \text{snapPhase} + (t - \text{snapTimestampSec}) \cdot \text{snapFreqHz} \right) \pmod{1.0}$$
- **Locked Cosine Output**: $\cos(2\pi \phi(t)) \in [-1.0, 1.0]$.
- **Zero-Allocation Queries**:
  - `getPhase(t): Double`
  - `getCosine(t): Double`
  - `getPhaseAndCosine(t, outReusedContainer: FloatArray)`
  - `getPhaseAndCosinePacked(t): Long` (packed primitive 64-bit value: high 32 bits = `phase.toRawBits()`, low 32 bits = `cosine.toRawBits()`).

---

## Beat Phase Lock (`BeatPhaseLock.kt`)

`AudioEngine.totalBeats` integrates the tempo, so on its own it lands on the music only by chance, and any tempo error drifts. In tracker mode with Link off, `BeatPhaseLock` steers it onto the beat. It is the web TV's method ([Web Subsystem](web_subsystem.md)).

- **Envelope:** every 10 ms, the rise in log energy of the bass below 150 Hz (weighted double) plus the rise of the full signal.
- **Reading, every 250 ms, on the `beat-phase-lock` thread:** a comb at the tracker's tempo, fine-tuned by up to 2%, over the last 8 s. The offset with the most onset energy is the time since the last beat. Confidence is how far that offset stands above the average one, and is zero if the window's older and newer halves disagree. About 0.4 ms per reading on the dev machine. The audio thread only writes the envelope and applies readings, about 4 µs per 512-frame block, with no allocation or waiting.
- **What may steer the clock:** the tracker is locked, the tempo has held within 3% for 20 readings (about 5 s), confidence is at least 0.35, and two readings in a row agree within 0.1 beat.
- **How it steers:** an error within 0.25 beat is slewed in, a quarter per reading, through `phaseSlewBuffer`. A larger one must hold for 8 readings (about 2 s), then jumps forward, so the beat count never runs back by more than a slew.
- **By hand:** TAP and SNAP call `claimPhase()`. The lock waits 4 s, then keeps the offset between the music's grid and where the person put the beat. It drops that offset when the tempo moves more than 3% or the grid jumps, because that usually means a new track.

**Link:** the Link timeline is the beat, so the lock does not steer while Link is on. A small backward move in it is absorbed by `getSynchronizedTotalBeats`, which coasts at 80% of tempo until the true beat catches up. At full tempo the output stayed ahead for good (+120 ms in a simulated hour after a 0.24 beat move).

**Measured** with `AudioPhaseReportTest`, on 150 tracks with fixed Mixxx beat grids (`scripts/mixxx_beat_grids.py`), an independent tracker's opinion rather than the truth. Share of clock beats within 70 ms of a Mixxx beat, where about 30% is chance:

| | On the beat | Half-way between beats | Tracks 60%+ on the beat |
| :--- | :--- | :--- | :--- |
| Integrated tempo only (before) | 30.0% | 31.4% | 9 of 150 |
| `BeatTrackerEngine.getPhase` | 46.1% | 27.4% | 53 of 150 |
| `BeatPhaseLock` | 53.2% | 28.6% | 65 of 150 |

No-beat audio: on six long spoken-word files the lock is on 1.5 to 3.9% of the time and moves the clock at most 0.05 beat per minute, and on beatless ambient tracks it is mostly off. On 43 DJ mixes (45.5 h) it is on 79% of the time, with 0.43 jumps a minute.

## BPM Sources & Locking

### 1. Manual BPM Lock (`isBpmLocked = true`)
The default and recommended mode for live performances. `AudioEngine.manualBpm` drives the flywheel directly. Automated phase realignment nudges and phase slew buffers are strictly bypassed, guaranteeing zero tempo jitter and perfectly smooth cosine / sine modulation.

### 2. Automatic Detection (`isBpmLocked = false`)
When unlocked, `BeatDetector` (in [`AudioEngine.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt)) delegates to [`BeatTrackerEngine.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/audio/BeatTrackerEngine.kt).
- **120 BPM Startup & Source Switch Lock**: On application launch, audio engine start, or audio device switching, the engine initializes and holds a steady 120.0 BPM lock until `BeatDetector` has continuously observed and confirmed a stable candidate tempo (`isTempoLocked = true`). Only upon confirmed lock does the engine smoothly transition from 120 BPM to the track's tempo.

### 3. Low-Signal Detection & Flywheel Tempo Retention
When incoming audio level drops below the analysis threshold (`localAudioEnergy <= 0.0025f`), such as during quiet ambient breakdowns, vocal bridges, or extended silence:
- **Flywheel Tempo Retention ("Dead Reckoning")**: Autocorrelation on spectral noise is bypassed, and `currentBpm` retains the last confirmed track tempo without decaying or drifting towards 120 BPM. This ensures visualizers, LFOs, and phase modulators maintain musical meter throughout 16–64 bar breakdowns.
- **Phase Nudge Suppression**: Phase realignment nudges are suppressed (`pendingPhaseNudge = -1.0`), preventing background noise from perturbing the phase accumulator.
- **Continuous Flywheel Coasting**: During silent passages or drops, the beat flywheel preserves momentum and continues advancing sample-accurately at the active tempo rather than freezing or changing frequency.
### 4. Audio Engine Disabled / Internal Manual Clock Mode
When the Audio Engine is disabled (`UITheme.audioEngineEnabled = false`):
- **Suspended Audio Capture**: Live audio input capture, backend drivers, and audio-reactive CV signals (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`, `audio_flux_*`) are stopped to conserve CPU cycles.
- **Clock Row & Preferences Telemetry**: Both the BPM readout and the bar phase meter remain visible and interactive on the Performance MIXER page's Clock row and within Preferences > Tempo & Sync, reflecting manual fixed clock mode with monotonic phase advance.

### 5. VJ Tap Tempo & Phase Synchronization
The application features a real-time Tap Tempo system ([`TapTempoController.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/audio/TapTempoController.kt)) enabling VJs to rhythmically tap in tempos via the Clock row's `[TAP]` button, global keyboard shortcut (`T`), or MIDI CC (`Mixer/tapTempo` or `Global/tapTempo`):
- **Interval Averaging & Timeout Reset**: Tracks a rolling buffer of up to 8 taps with microsecond resolution. Taps separated by > 2.0 seconds (30 BPM) automatically reset the sequence so a new cadence begins cleanly.
- **Immediate Visual Feedback**: While tapping, the `[TAP]` button and Clock row BPM readout instantly confirm incoming taps with an active cadence color highlight and counter readout (`TAP [1]`, `TAP [2]`), returning to standard display once the cadence times out.
- **Manual / Locked Mode Behavior**:
  - Immediately updates `manualBpm`, `estimatedBpm`, and `CVRegistry.alignBeatPhase` to the tapped tempo.
  - **Beat Phase Quantization**: Every tap represents an arrival on the beat; the engine quantizes `totalBeats` to the nearest whole integer boundary (`round(totalBeats)`), resetting render monotonic progress so visual pulses and the bar meter immediately lock to the beat.
  - **Downbeat claim**: the first tap of a run (`registerTap(null)`) additionally calls `barLineBeat`, moving the count to the nearest multiple of `beatsPerBar`, and `DownbeatDetector.claimDownbeat()`. See [Bars and Downbeats](#6-bars-and-downbeats).
- **Active Audio Tracking Mode Behavior**:
  - **Dynamic Programming Prior Shift**: Recalculates the causal dynamic programming target interval $\tau_0 = \text{fps} \times (60.0 / \text{BPM})$ and sets `stableCandidateBpm` to the tapped tempo with `isLocked = true` and `stableAccumulatedSec = stabilityLockDurationSec`, immediately locking the autocorrelation search window to the tapped tempo and breaking octave traps (half-time/double-time locks).
  - **Phase Slew Nudge**: Injects a phase nudge (`pendingPhaseNudge = 0.0`) into the audio callback's `phaseSlewBuffer`, smoothly slewing the visual flywheel phase without visual jump discontinuities.
  - **Phase lock**: `BeatPhaseLock.claimPhase()` makes the lock keep this alignment relative to the music's grid. See [Beat Phase Lock](#beat-phase-lock-beatphaselockkt).

### 6. Bars and Downbeats

The tracker locks the beat *phase*; it does not know which beat opens the bar. Bars are therefore a property of the beat **count**: the engine keeps `totalBeats` so that a bar starts on every multiple of `CVRegistry.beatsPerBar` (2..8, default 4). Every consumer (LFO/sequencer `GenUnit.BEAT`, `ClockKnobFeed`, the bar dots, the web serializer) reads that one count, so "where is beat 1" is answered by *renumbering* the count by a whole number of beats; there is no separate offset to forget to apply. `CVRegistry.beatInBar(beats)` and `barPhase(beats)` are the helpers.

Beat 1 can come from four places:

| Source | How |
| :--- | :--- |
| `SNAP` / `resyncDownbeat()` | rounds to a whole beat, then to the nearest multiple of `beatsPerBar` (`barLineBeat`, at most half a bar of movement), zeroes phase slew, calls `claimDownbeat()`. |
| First tap of a run | `registerTap(null)` does the same; later taps keep the bar line (they only round to whole beats). Link mode returns early: the Link quantum owns the bar. |
| `DownbeatDetector` (Preferences > Tempo & Sync > Downbeat: Auto) | below. |
| Ableton Link | `AbletonLinkEngine.quantum` is the bar; the setting `setBeatsPerBarAndLinkQuantum` keeps the two equal. The detector is not run under Link. |

**`DownbeatDetector`** (audio thread, no allocation) runs from `AudioEngine.processAudio` right after the flywheel advances, only when `downbeatMode == AUTO`, the clock is `AUDIO_TRACKER`, the signal is `ACTIVE` and the tempo is locked:

1. Each block is assigned to the beat whose window `[n-0.2, n+0.8)` contains the block's centre. When a beat's window ends it yields three numbers: summed low-band flux, summed overall onset strength and mean low-band level, each divided by its own running average (`EMA_RATE` 0.05), so loudness and genre do not matter.
2. `feature = 0.45 kick + 0.35 onset + 0.2 level`, plus half of any rise over the previous beat (a new bar's first beat is usually a jump). It is added into `score[n mod beatsPerBar]` with a decay of 0.8 per bar (about the last five bars).
3. Every `beatsPerBar` beats after 4 bars of warm-up it compares positions. The first answer needs the best position to lead the runner-up by 15 % (`SWITCH_MARGIN`). Afterwards position 0 is "the downbeat", and another position must lead it by 15 % for 2 evaluations in a row (30 % after a manual claim) before the bar line moves.
4. A move is returned from `process` as a whole-beat shift (`shiftToBarLine`: the shorter way, forward on a tie); the engine adds it to `totalBeats` and the detector rotates its scores to match. The fractional phase is untouched.
5. `confidence` (0..1) is how far position 0 leads the best other position, scaled so a 25 % lead reads 1.0. A jump in the count (tap, resync, lost lock) or a change of `beatsPerBar` throws the gathered scores away.
6. `claimDownbeat()` (volatile request from the UI thread) clears the scores, marks the current answer as decided and ignores the detector for `LOCK_BARS` = 16 bars.

Known limits: it learns from accents, so a groove with an identical kick on every beat and nothing else that changes per bar (a flat four-on-the-floor loop) leaves it undecided, with low confidence, rather than guessing; breakdowns without a kick pause the evidence; it assumes the beat phase is on the beat, not the off-beat. It is not a meter detector (3 vs 4 beats is a setting). The web TV does not use any of this yet.

Tests: `DownbeatDetectorTest` (synthetic accented grooves for 3/4/6/7 beats per bar, flat groove, warm-up, claim lock, count jump, `shiftToBarLine`) and the SNAP/tap cases in `AudioEngineTest`.

---

## Automated Audio Benchmark Test Suite

The engine performance is validated using [`BeatDetectorBenchmarkTest.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/test/kotlin/llm/slop/liquidlsd/audio/BeatDetectorBenchmarkTest.kt).
This automated suite simulates multi-band drum audio across diverse genres and tempos:
1. **120 BPM Four-on-the-Floor**: House/Techno kick-snare patterns (Target: 120.0 BPM, Max Convergence: < 2.5s, Error: < 1.5 BPM).
2. **128 BPM Mainstage EDM**: Fast house kick, snare, and 8th-note hi-hats.
3. **140 BPM Dubstep / Half-Step**: Heavy kick-snare syncopation.
4. **100 BPM Hip-Hop / Slow Groove**: Down-tempo groove stability.
5. **PLL Flywheel Breakdown Stability**: 4-beat silent drum breakdown test to verify flywheel momentum retention.
