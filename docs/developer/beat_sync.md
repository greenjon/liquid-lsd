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
When unlocked, [`BeatDetector.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt) evaluates audio envelopes inline with zero allocations using one of three modes:

#### A. Multi-Band Autocorrelation + Harmonic Comb Unwrapping — `BeatDetectionMode.AUTOCORRELATION` (Default / Recommended)
Maintains zero-allocation primitive FloatArray ring buffers (`bassHistory`, `midHistory`, `highHistory`, 2048 blocks).
Across every block, candidate lag delays (25 to 129 blocks, corresponding to 40–200 BPM) compute cross-spectral correlation over an adaptive analysis window:
$$\text{AC}(d) = \sum_{i=0}^{W-1} \left[ H_{\text{bass}}[t-i] H_{\text{bass}}[t-i-d] + H_{\text{mid}}[t-i] H_{\text{mid}}[t-i-d] + H_{\text{high}}[t-i] H_{\text{high}}[t-i-d] \right]$$
- **Gaussian Tempo Weighting**: Multiplies raw correlation by a subtle Gaussian curve centered at 120 BPM ($\sigma = 80$ BPM) to bias candidate selection towards musical tempos.
- **Harmonic Comb Unwrapping**: Eliminates half-tempo/double-tempo octave traps by evaluating half-lags ($d / 2$). If $AC(d/2) \ge 0.45 \times AC(d)$ and $BPM(d/2) \le 165.0$, the fundamental beat period unwraps to the higher tempo octave.
- **Sub-Block Parabolic Lag Interpolation**: Fits a 2nd-order parabola over lag points $(d-1, d, d+1)$ to extract sub-block fractional lag offsets $\delta$, achieving precision within $\pm 0.1$ BPM.
- **Adaptive 2nd-Order PLL Flywheel**: Smoothly adapts current BPM towards calculated tempo while phase-slewing the global beat counter without visual jitter.

#### B. Energy-Difference + PLL Soft-Sync — `BeatDetectionMode.ENERGY_DIFFERENCE`
Maintains a short-time energy variance (`localEnergyAverage`). If the target band energy exceeds `localEnergyAverage * energyThreshold` and sufficient time has passed, an onset peak is registered.
The time between peaks is used to estimate BPM, which is smoothed via a Phase-Locked Loop (PLL) adaptation rate.
When an onset occurs, `pendingPhaseNudge` is set to `0.0`, signaling the flywheel to smoothly align its cosine output to the transient.

#### C. Complex Domain Onset + Biquad Resonator — `BeatDetectionMode.RESONATOR`
Passes the DC-blocked target band envelope into a highly resonant bandpass filter (Biquad) tuned to the current BPM.
The resonator acts as a "ringing" flywheel that naturally outputs a sine wave. Zero crossings of this sine wave trigger phase nudges and update the underlying tempo estimation.

#### D. Low-Signal Detection & Graceful 120 BPM Fallback Lock
When incoming audio level drops below the analysis threshold (`localEnergyAverage <= 0.003f`), or during extended pauses/silence:
- **Zero Jitter Fallback**: Autocorrelation on spectral noise is bypassed, and `currentBpm` smoothly transitions/locks to **120.0 BPM** using an exponential slew rate (`slewRate = 0.05f`), preventing wild counter swings or erroneous tempo jumps.
- **Phase Nudge Suppression**: Phase realignment signals are suppressed (`pendingPhaseNudge = -1.0`), keeping the beat flywheel accumulator steady.
- **Tempo Stability Gating**: When a valid audio signal returns (`localEnergyAverage > 0.003f`), the engine holds the 120.0 BPM lock while analyzing candidate tempos. Only after a consistent tempo estimate ($\Delta \text{BPM} \le 4.0$ BPM, with harmonic octave awareness) is continuously observed for the stability threshold (`requiredLockDurationSec = 0.4f`), the lock is established (`isTempoLocked = true`), and the flywheel PLL seamlessly adapts to the new track tempo.

---

## Automated Audio Benchmark Test Suite

The engine performance is validated using [`BeatDetectorBenchmarkTest.kt`](file:///home/gj/projects/liquid-lsd/src/test/kotlin/llm/slop/liquidlsd/audio/BeatDetectorBenchmarkTest.kt).
This automated suite simulates multi-band drum audio across diverse genres and tempos:
1. **120 BPM Four-on-the-Floor**: House/Techno kick-snare patterns (Target: 120.0 BPM, Max Convergence: < 3.0s, Error: < 1.5 BPM).
2. **128 BPM Mainstage EDM**: Fast house kick, snare, and 8th-note hi-hats.
3. **140 BPM Dubstep / Half-Step**: Heavy kick-snare syncopation.
4. **100 BPM Hip-Hop / Slow Groove**: Down-tempo groove stability.
5. **PLL Flywheel Breakdown Stability**: 4-beat silent drum breakdown test to verify flywheel momentum retention.

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
