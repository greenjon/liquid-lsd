# Audio Beat Synchronization & Stability

This document describes the beat tracking and CV synchronization system in Liquid LSD —
how BPM is detected or set manually, how the beat clock is ticked, and how visual generators
stay phase-locked to the music across frames.

---

## Architecture Overview

Beat synchronization bridges three subsystems:

```
JACK Callback (AudioEngine)
    │
    ├─► BeatDetector.processBlock()   ← runs every audio callback (~50-200 Hz)
    │       └─► Returns estimated BPM
    │
    ├─► Flywheel tick: totalBeats += delta * bpm / 60
    │
    └─► CVRegistry.updateBeatAnchor(totalBeats, bpm, nanoTime)
              │  (AtomicReference<BeatAnchor> swap — lock-free)
              │
        Render Thread (every frame)
              │
              └─► CVRegistry.getSynchronizedTotalBeats()
                      └─► Interpolates beat clock to sub-millisecond precision
                              │
                        Evaluators.kt  ← beatPhase, gen1/gen2, sampleAndHold, lfo
```

---

## BPM Sources

### Manual BPM Lock (`isBpmLocked = true`)

The simplest and most stable mode. `AudioEngine.manualBpm` is used directly as the flywheel
speed. No beat detection runs. This is the default mode and is recommended for most live
performance use cases.

Use the BPM tap button in the UI, or assign a MIDI CC to nudge `manualBpm` in real time.

### Automatic Detection (`isBpmLocked = false`)

When unlocked, `BeatDetector.processBlock()` analyses the audio envelope and returns an estimated
BPM each callback. The engine selects which detection algorithm to use via `BeatDetectionSettings.mode`:

---

## BeatDetector Modes

### 1. PLL (Phase-Locked Loop) — `BeatDetectionMode.PLL`

A phase-locked loop that tracks the beat by nudging an internal oscillator period whenever a
transient is detected. Good for stable, rhythmic material.

On each audio callback block:
1. The internal oscillator `pllPhase` increments by 1 block.
2. When a transient is detected (current amplitude > previous and > threshold), the phase error
   is computed:
   $$\text{error} = \frac{\phi_{current}}{T_{period}} - 0.5\quad\text{(centered)}$$
3. Both phase and period are nudged toward alignment:
   $$\phi \leftarrow \phi - \text{error} \times T \times \alpha$$
   $$T \leftarrow T - \text{error} \times T \times (\alpha \times 0.1)$$
   where $\alpha$ is `pllAdaptationRate` from `BeatDetectionSettings`.
4. BPM is derived from the current period:
   $$BPM = \frac{60 \times fps}{T_{period}}$$
5. Period is clamped to the `[bpmSearchFloor, bpmSearchCeiling]` range.

### 2. STFT Comb Filter — `BeatDetectionMode.STFT_COMB`

Runs periodically (every 16 blocks) on a background analysis thread to avoid blocking the
real-time callback. Uses a comb filter bank to find the periodicity with the highest energy in
the envelope history.

For each candidate BPM in the search range (stepped by `bpmGridResolution`):
$$\text{energy}(BPM) = \sum_{k=0}^{3} \text{envelope}\!\left[\text{histIdx} - k \times \text{delay}_{BPM}\right]$$
where $\text{delay}_{BPM} = \lfloor fps \times 60 / BPM \rfloor$ blocks.

The BPM with the highest comb energy is accepted, then blended smoothly:
$$BPM_{current} \leftarrow BPM_{current} \times 0.9 + BPM_{best} \times 0.1$$

### 3. Autocorrelation — `BeatDetectionMode.AUTOCORRELATION`

Also runs on the background analysis thread. Computes the autocorrelation of the envelope history
at each candidate lag (delay) to find the most periodic tempo:

$$AC(\tau) = \sum_{i=0}^{N} \text{envelope}[i] \cdot \text{envelope}[i - \tau]$$

The lag $\tau^*$ with the highest autocorrelation is converted to BPM:
$$BPM = \frac{60 \times fps}{\tau^*}$$

Then blended into the running estimate:
$$BPM_{current} \leftarrow BPM_{current} \times 0.95 + BPM_{calc} \times 0.05$$

---

## Background Analysis Thread Safety

For both STFT Comb and Autocorrelation modes, the analysis runs on a dedicated
`BeatDetector-Analysis` daemon thread (`analysisExecutor`). To avoid a data race on the
input snapshot, the `BeatDetector` uses a **double-buffered snapshot** pattern:

- Two pre-allocated `AnalysisSnapshot` objects (`snapshot1`, `snapshot2`) hold `fps`,
  `bgHistoryIndex`, and `bgHistoryCount`.
- The audio callback writes the next snapshot and publishes it via a single `@Volatile`
  `pendingSnapshot` reference swap before submitting the task.
- The analysis task reads `pendingSnapshot` once into a local `val` at the start of `run()`,
  making the entire read consistent without a lock.

---

## High-Precision Beat Clock Interpolation

The render thread runs at a different rate than the JACK audio callback. To keep visual
generators phase-accurate between audio callbacks, `CVRegistry.getSynchronizedTotalBeats()`
interpolates the beat clock forward in time:

$$\text{synchronizedBeats} = \text{anchorBeats} + \frac{(t_{now} - t_{anchor}) \times BPM_{anchor}}{60.0 \times 10^9}$$

where $t$ is nanoseconds from `System.nanoTime()`. This gives sub-millisecond phase precision
regardless of the audio callback interval.

---

## Tuning & Stability

| Setting | Effect |
|---------|--------|
| `BeatDetectionSettings.mode` | Choose PLL, Comb, or Autocorrelation |
| `bpmSearchFloor` / `bpmSearchCeiling` | Constrain the detection range (e.g. 80–160 for house) |
| `bpmGridResolution` | Smaller = finer comb search, slower |
| `pllAdaptationRate` | Higher = faster PLL lock, more jitter |
| `analysisWindowLength` | Seconds of history used by autocorrelation |
| Manual BPM lock | Most reliable for live use — set it and forget it |
