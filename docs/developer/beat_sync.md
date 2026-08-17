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
              │  (@Volatile primitive fields update: zero heap allocation)
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
An internal oscillator nudges its period $T_{period}$ whenever a transient onset peak occurs:
$$\text{Error} = \frac{\phi_{current}}{T_{period}} - 0.5$$

Period and phase adjust toward transient alignment using dual time constants ($\alpha = \text{pllAdaptationRate}$, $\beta = \alpha \times 0.01$):
$$\phi \leftarrow \phi - \text{Error} \times T \times \alpha$$
$$T \leftarrow T - \text{Error} \times T \times \beta$$

Evaluated strictly on local onset peaks ($\text{flux}_t > \text{flux}_{t-1} \land \text{flux}_t > \text{flux}_{t+1}$) to prevent sample-block over-triggering.

#### B. STFT Comb Filter Bank — `BeatDetectionMode.STFT_COMB`
Runs on a background analysis thread every 16 blocks over onset spectral flux history:
$$\text{Energy}(BPM) = \sum_{k=0}^{3} \text{flux}\left[\text{histIdx} - k \times \text{delay}_{BPM}\right]$$
Includes sub-grid parabolic interpolation around the peak comb energy index for floating-point BPM accuracy, and a phase anchor cross-correlation pass to align beat phase.

#### C. Autocorrelation — `BeatDetectionMode.AUTOCORRELATION`
Runs on a background analysis thread. Evaluates onset flux autocorrelation across candidate lags $\tau$:
$$AC(\tau) = \sum_{i=0}^N \text{flux}[i] \cdot \text{flux}[i - \tau]$$
Applies sub-block parabolic interpolation ($\tau_{\text{sub}} = k_{\text{best}} + \Delta k$) to eliminate integer block discretization steps, paired with phase anchor impulse tracking.

---

## Flywheel Phase Slewing & Thread Safety

Background beat detection results publish a target phase anchor ($\text{pendingPhaseNudge}$). Rather than stepping `totalBeats` instantaneously—which creates visual phase pops or clicks—`AudioEngine` applies second-order phase slewing:
$$\Delta \phi = \text{phaseTarget} - (\text{totalBeats} \bmod 1.0)$$
$$\text{totalBeats} \leftarrow \text{totalBeats} + \text{slewAmount}$$
The discrepancy bleeds off smoothly over subsequent audio blocks, delivering continuous, glitch-free sine wave modulation.

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
