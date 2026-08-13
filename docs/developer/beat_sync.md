# Audio Beat Synchronization & Stability

This document details tempo detection, beat clock flywheel accumulator logic, background analysis threading safety, and sub-millisecond visual clock interpolation in Liquid LSD.

---

## Architecture Overview

```
JACK / Java Sound Callback (Audio Engine Thread)
    │
    ├─► BeatDetector.processBlock()   ← runs every audio block (~50–200 Hz)
    │       └─► Calculates estimated BPM
    │
    ├─► Flywheel Accumulator: totalBeats += (bufferFrames / sampleRate) * (BPM / 60.0)
    │
    └─► CVRegistry.updateBeatAnchor(totalBeats, bpm, nanoTime)
              │  (AtomicReference<BeatAnchor> lock-free swap)
              │
        Render Thread 0 (Every Frame @ 60Hz–144Hz+)
              │
              └─► CVRegistry.getSynchronizedTotalBeats()
                      └─► Sub-millisecond forward clock interpolation
                              │
                        Evaluators.kt  ← beatPhase, LFO 1/2, Sample & Hold
```

---

## BPM Sources & Locking

### 1. Manual BPM Lock (`isBpmLocked = true`)
The default and recommended mode for live performances. `AudioEngine.manualBpm` drives the flywheel directly. No background beat detection algorithms execute, guaranteeing zero tempo jitter.

### 2. Automatic Detection (`isBpmLocked = false`)
When unlocked, [`BeatDetector.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/cv/BeatClock.kt) evaluates audio envelopes using one of three selectable algorithms:

#### A. Phase-Locked Loop (PLL) — `BeatDetectionMode.PLL`
An internal oscillator nudges its period $T_{period}$ whenever a transient impulse occurs:
$$\text{Error} = \frac{\phi_{current}}{T_{period}} - 0.5$$

Period and phase adjust toward transient alignment:
$$\phi \leftarrow \phi - \text{Error} \times T \times \alpha$$
$$T \leftarrow T - \text{Error} \times T \times (\alpha \times 0.1)$$

#### B. STFT Comb Filter Bank — `BeatDetectionMode.STFT_COMB`
Runs on a background analysis thread every 16 blocks. Tests candidate BPM delays against past envelope history:
$$\text{Energy}(BPM) = \sum_{k=0}^{3} \text{envelope}\left[\text{histIdx} - k \times \text{delay}_{BPM}\right]$$

#### C. Autocorrelation — `BeatDetectionMode.AUTOCORRELATION`
Runs on a background analysis thread. Evaluates envelope autocorrelation across candidate lags $\tau$:
$$AC(\tau) = \sum_{i=0}^N \text{envelope}[i] \cdot \text{envelope}[i - \tau]$$

---

## Double-Buffered Background Analysis Thread Safety

Heavy comb filter and autocorrelation analysis tasks execute on a background daemon thread (`BeatDetector-Analysis`) to protect the real-time audio callback.

To prevent data races without mutex locks:
1. Two pre-allocated `AnalysisSnapshot` instances (`snapshot1`, `snapshot2`) store envelope history and frame metadata.
2. The audio thread populates the inactive snapshot and publishes it via a `@Volatile pendingSnapshot` reference swap.
3. The background thread reads `pendingSnapshot` once at the start of execution, maintaining thread safety without mutex contention.

---

## High-Precision Visual Beat Interpolation

Because screen rendering runs independently of audio callbacks, `CVRegistry.getSynchronizedTotalBeats()` interpolates the beat phase forward in time:

$$\text{SynchronizedBeats} = \text{anchorBeats} + \frac{(t_{now} - t_{anchor}) \times \text{BPM}_{anchor}}{60.0 \times 10^9}$$

where $t$ represents nanoseconds from `System.nanoTime()`. This provides sub-millisecond visual phase smoothness across all frame rates.
