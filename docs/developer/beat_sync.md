# Audio Beat Synchronization & Stability

This document details tempo detection, beat clock flywheel accumulator logic, and sub-millisecond visual clock interpolation in Liquid LSD.

---

## Architecture Overview

```
JACK / Java Sound Callback (Audio Engine Thread)
    │
    ├─► BeatDetector.processBlock()   ← runs every audio block (~50–200 Hz)
    │       └─► Calculates estimated BPM and onset phase target (0.0)
    │
    ├─► Flywheel Accumulator: totalBeats += (bufferFrames / sampleRate) * (BPM / 60.0)
    │       └─► Second-order phase slew tracks pendingPhaseNudge
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
When unlocked, [`BeatDetector.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt) evaluates audio envelopes inline with zero allocations using one of two modes:

#### A. Energy-Difference + PLL Soft-Sync — `BeatDetectionMode.ENERGY_DIFFERENCE`
Maintains a short-time energy variance (`localEnergyAverage`). If the target band energy exceeds `localEnergyAverage * energyThreshold` and sufficient time has passed, an onset peak is registered.
The time between peaks is used to estimate BPM, which is smoothed via a Phase-Locked Loop (PLL) adaptation rate.
When an onset occurs, `pendingPhaseNudge` is set to `0.0`, signaling the flywheel to smoothly align its cosine output to the transient.

#### B. Complex Domain Onset + Biquad Resonator — `BeatDetectionMode.RESONATOR`
Passes the DC-blocked target band envelope into a highly resonant bandpass filter (Biquad) tuned to the current BPM.
The resonator acts as a "ringing" flywheel that naturally outputs a sine wave. Zero crossings of this sine wave trigger phase nudges and update the underlying tempo estimation.

---

## Flywheel Phase Slewing & Thread Safety

Beat detection algorithms publish a target phase anchor (`pendingPhaseNudge = 0.0`). Rather than stepping `totalBeats` instantaneously—which creates visual phase pops or clicks—`AudioEngine` applies second-order phase slewing:

$$\Delta \phi = \text{phaseTarget} - (\text{totalBeats} \bmod 1.0)$$
$$\text{totalBeats} \leftarrow \text{totalBeats} + \text{slewAmount}$$

The discrepancy bleeds off smoothly over subsequent audio blocks, delivering continuous, glitch-free sine wave modulation.

---

## High-Precision Visual Beat Interpolation

Because screen rendering runs independently of audio callbacks, `CVRegistry.getSynchronizedTotalBeats()` interpolates the beat phase forward in time:

$$\text{SynchronizedBeats} = \text{anchorBeats} + \frac{(t_{now} - t_{anchor}) \times \text{BPM}_{anchor}}{60.0 \times 10^9}$$

where $t$ represents nanoseconds from `System.nanoTime()`. This provides sub-millisecond visual phase smoothness across all frame rates.
