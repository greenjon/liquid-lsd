# Audio Beat Synchronization & Stability

This document details tempo detection, beat clock flywheel accumulator logic, and continuous beat phase generation in Liquid LSD.

---

## Architecture Overview

```
JACK / Java Sound Callback (Audio Engine Thread)
    │
    ├─► BeatTrackerEngine.processMultiBand() / processBlock()  ← runs every audio block (~50–200 Hz)
    │       ├─► 512-Point Zero-Allocation FFT (Complex Spectral Difference ODF)
    │       ├─► Multi-Band Cross-Spectral Autocorrelation & Harmonic Unwrapping
    │       ├─► Causal Dynamic Programming Recurrence with Pre-Computed Log Penalties
    │       └─► Beat Anchor Projections & Flywheel Tracking
    │
    ├─► Flywheel Accumulator: totalBeats += (bufferFrames / sampleRate) * (BPM / 60.0)
    │       └─► Second-order phase slew tracks pendingPhaseNudge
    │
    └─► CVRegistry.updateBeatAnchor(totalBeats, bpm, nanoTime)
              │  (@Volatile primitive fields update: zero heap allocation)
              │
        Render Thread 0 (Every Frame @ 60Hz–144Hz+)
              │
              ├─► BeatTrackerEngine.getPhase(t) / getCosine(t) / getPhaseAndCosine(t, out)
              │       └─► Continuous phase-locked cosine modulation signal: cos(2 * PI * phi)
              │
              └─► CVRegistry.getSynchronizedTotalBeats()
                      └─► Sub-millisecond forward clock interpolation
                              │
                        Evaluators.kt  ← beatPhase, LFO 1/2, Sample & Hold
```

---

## Beat Tracker Model (`BeatTrackerEngine.kt`)

Liquid LSD features a real-time beat tracker and continuous phase generator modeled on BTrack (Adam Stark) and the Dan Ellis causal dynamic programming beat tracker.

### 1. Complex Spectral Difference Onset Detection Function (ODF)
- Evaluates a zero-allocation 512-point Radix-2 Cooley-Tukey FFT with pre-computed twiddle factors and Hann windowing.
- Measures the Euclidean distance between expected complex STFT spectrum bins $(\hat{r}_k, \hat{\theta}_k)$ (predicted from 2nd-order phase trajectory $\hat{\theta}_t = 2\theta_{t-1} - \theta_{t-2}$) and observed spectrum $(r_k, \theta_k)$:
  $$\text{ODF}(t) = \sum_{k} \sqrt{ |r_k \cos \theta_k - \hat{r}_k \cos \hat{\theta}_k|^2 + |r_k \sin \theta_k - \hat{r}_k \sin \hat{\theta}_k|^2 }$$
- **Principal Phase Normalization**: $\hat{\theta}_t$ is wrapped into $[-\pi, \pi]$ before synthesizing expected complex spectral bins $(\hat{r} \cos \hat{\theta}, \hat{r} \sin \hat{\theta})$, eliminating spurious transient spikes on continuous pitched tones.
- Suppresses stationary tones while detecting soft pitched attacks and percussive transients.

### 2. Two-State Periodicity & Tempo Estimation
The engine operates in two distinct states to prevent octave jumps and frequency-doubling artifacts:
- **State 1: Acquisition (Unlocked)**: Unconstrained candidate lag search across 40–200 BPM ($d \in [d_{\min}, d_{\max}]$).
- **State 2: Locked Tracking**: Constrains candidate search to a narrow observation window ($\pm 15\%$) around current tempo period $\tau_0$. Clamps maximum tempo adjustment to $\pm 2.0$ BPM/beat human tracking inertia.

#### Multi-Band Cross-Spectral Autocorrelation
Maintains zero-allocation circular ring buffers (`bassHistory`, `midHistory`, `highHistory`, `odfHistory`) with dynamic linear (Bartlett) windowing $w(i) = 1 - \frac{i}{W}$ to eliminate abrupt boundary dropouts at history window edges:
$$\text{AC}(d) = \sum_{i=0}^{W-1} \left(1 - \frac{i}{W}\right) \left[ H_{\text{bass}}[t-i] H_{\text{bass}}[t-i-d] + H_{\text{mid}}[t-i] H_{\text{mid}}[t-i-d] + H_{\text{high}}[t-i] H_{\text{high}}[t-i-d] \right]$$
- **Dynamic Linear (Bartlett) Windowing**: Applies linearly decaying weights from 1.0 down to 0.0 at the history buffer edge, eliminating periodic 4-second edge dropouts when onsets exit the history window.
- **Gaussian Human Prior**: Multiplies correlation by Gaussian tempo prior centered at 120 BPM ($\sigma = 80$ BPM).
- **Harmonic Comb Unwrapping**: Eliminates half-tempo/double-tempo octave traps by evaluating half-lags ($d / 2$). If $\text{AC}(d/2) \ge 0.45 \times \text{AC}(d)$ and $\text{BPM}(d/2) \le 165.0$, the fundamental beat period unwraps to the quarter-note tempo octave.
- **Sub-Block Parabolic Lag Interpolation**: Fits a 2nd-order parabola across $(d-1, d, d+1)$ to extract sub-block fractional lag offsets $\delta \in [-0.5, 0.5]$, achieving precision within $\pm 0.1$ BPM.

### 3. Causal Dynamic Programming Recurrence
Maintains a circular cumulative score ring buffer evaluating causal DP recurrence:
$$\text{Score}(t) = \text{ODF}(t) + \lambda \cdot \max_{\tau \in [\tau_{\min}, \tau_{\max}]} \left\{ \text{Score}(t - \tau) - \alpha \cdot \left(\log \frac{\tau}{\tau_0}\right)^2 \right\}$$
- **Ultra-Fast Log-Table Optimization**: Pre-calculates $\log(\tau)$ table (`logTauTable`) and evaluates $\log(\tau_0)$ once per block outside the inner candidate loop, eliminating transcendental `Math.log()` calls from the real-time audio callback.

### 4. Continuous Visual Phase & Cosine Modulation Signal
Provides zero-allocation primitive queries for visual rendering at arbitrary frame query timestamps:
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

## BPM Sources & Locking

### 1. Manual BPM Lock (`isBpmLocked = true`)
The default and recommended mode for live performances. `AudioEngine.manualBpm` drives the flywheel directly. Automated phase realignment nudges and phase slew buffers are strictly bypassed, guaranteeing zero tempo jitter and perfectly smooth cosine / sine modulation.

### 2. Automatic Detection (`isBpmLocked = false`)
When unlocked, [`BeatDetector.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt) delegates to [`BeatTrackerEngine.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/BeatTrackerEngine.kt).
- **120 BPM Startup & Source Switch Lock**: On application launch, audio engine start, or audio device switching, the engine initializes and holds a steady 120.0 BPM lock until `BeatDetector` has continuously observed and confirmed a stable candidate tempo (`isTempoLocked = true`). Only upon confirmed lock does the engine smoothly transition from 120 BPM to the track's tempo.

### 3. Low-Signal Detection & Graceful 120 BPM Fallback Lock
When incoming audio level drops below the analysis threshold (`localAudioEnergy <= 0.0025f`), or during extended pauses/silence:
- **Zero Jitter Fallback**: Autocorrelation on spectral noise is bypassed, and `currentBpm` smoothly transitions/locks to **120.0 BPM** using an exponential slew rate (`slewRate = 0.05f`), preventing wild counter swings or erroneous tempo jumps.
- **Phase Nudge Suppression**: Phase realignment signals are suppressed (`pendingPhaseNudge = -1.0`), keeping the beat flywheel accumulator steady.
- **Continuous Flywheel Coasting**: During silent passages or drops, the beat flywheel preserves momentum and continues advancing sample-accurately at the active tempo rather than freezing.
- **Tempo Stability Gating with Leaky Decay Hysteresis**: When a valid audio signal returns (`localAudioEnergy > 0.0025f`), the engine holds candidate tempo analysis in an accumulator. Consistent tempo estimates ($\Delta \text{BPM} \le 4.0$ BPM, with harmonic octave awareness) accumulate stability time up to a capped maximum (`min(stabilityLockDurationSec * 1.5f, stableAccumulatedSec + dt)`) with smooth exponential weighting ($0.95 / 0.05$). Outlier frames decay accumulated stability gracefully via a leaky decay rate (`dt * 2.0f`) instead of a hard reset to zero, acting as a shock absorber against transient noise/hiccups while dropping lock swiftly within $\sim 300\text{ ms}$ on genuine tempo shifts.

---

## Automated Audio Benchmark Test Suite

The engine performance is validated using [`BeatDetectorBenchmarkTest.kt`](file:///home/gj/projects/liquid-lsd/src/test/kotlin/llm/slop/liquidlsd/audio/BeatDetectorBenchmarkTest.kt).
This automated suite simulates multi-band drum audio across diverse genres and tempos:
1. **120 BPM Four-on-the-Floor**: House/Techno kick-snare patterns (Target: 120.0 BPM, Max Convergence: < 2.5s, Error: < 1.5 BPM).
2. **128 BPM Mainstage EDM**: Fast house kick, snare, and 8th-note hi-hats.
3. **140 BPM Dubstep / Half-Step**: Heavy kick-snare syncopation.
4. **100 BPM Hip-Hop / Slow Groove**: Down-tempo groove stability.
5. **PLL Flywheel Breakdown Stability**: 4-beat silent drum breakdown test to verify flywheel momentum retention.
