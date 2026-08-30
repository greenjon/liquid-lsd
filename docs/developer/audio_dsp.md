# Real-Time Audio & DSP Engine

This document details the real-time audio capture backends, zero-allocation constraints, DSP band-splitting filters, amplitude extraction, and onset detection engine in Liquid LSD.

---

## Dual Audio Client Architecture

Liquid LSD supports two complementary audio client implementations managed by [`AudioEngine.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt):

```
                       ┌───────────────────────────────┐
                       │        AudioEngine.kt         │
                       └───────────────┬───────────────┘
                                       │
                ┌──────────────────────┴──────────────────────┐
                ▼                                             ▼
     Linux JACK / PipeWire                        Cross-Platform Fallback
       (JackClient.kt)                             (JavaSoundClient.kt)
  - JNAJack native bindings                  - TargetDataLine input capture
  - Sub-millisecond callback                 - Pre-allocated byte->float buffer
  - Inter-app audio port graph               - macOS, Windows, JACK-less Linux
```

### 1. `JackClient.kt` (Linux JACK / PipeWire-JACK)
- Uses JNAJack native C bindings to interface directly with the JACK or PipeWire-JACK server.
- Registers client input ports (`lsd:input_1`, `lsd:input_2`).
- Operates inside an OS real-time thread callback (`process(client, nframes)`). Strict OS priority rules apply: any heap allocation or blocking call triggers immediate xruns (audio dropouts) or server disconnection.

### 2. `JavaSoundClient.kt` (macOS, Windows, Standalone Linux)
- Uses Java Sound `TargetDataLine` to capture system input audio (PCM 16-bit signed, mono/stereo 44.1kHz/48kHz).
- Runs inside a dedicated daemon thread loop.
- **Zero-Allocation Conversion**: Conversion from raw PCM byte arrays to normalized `FloatBuffer` arrays uses pre-allocated byte buffers (`byteBuffer`, `floatBuffer`), preventing JVM Garbage Collection (GC) pauses from causing visual micro-stuttering.

---

## Zero-Allocation Rules Enforced in Processing Loops

Across both `JackClient` and `JavaSoundClient`, the inner DSP processing loop strictly enforces zero heap allocations:

1. **No Instantiations**: No `new`, no Kotlin lambdas that capture outer scope, no collection instantiations inside the audio callback path.
2. **No Blocking System Calls**: File I/O, database queries, string formatting, `println`, and mutex locking are forbidden.
3. **Pre-Allocated Buffer Pools**: Filter output arrays (`lowBuffer`, `midBuffer`, `highBuffer`) are pre-allocated as `FloatArray(16384)` during initialization to handle maximum buffer sizes without resizing.

---

## DSP Pipeline & Frequency Band Splitting

Every audio block processes the incoming mono signal sequentially:

```
Input Audio ──► Biquad Filter Bank ──► Amplitude Extractor ──► CVRegistry
                 ├── Low-Pass  (<= 150 Hz)    -> bass
                 ├── Band-Pass (~ 1000 Hz)   -> mid
                 └── High-Pass (>= 5000 Hz)   -> high
```

### Biquad IIR Filter Bank (`BiquadFilter.kt`)
Three parallel second-order IIR (Infinite Impulse Response) biquad filters process the audio stream:
- **Low-pass**: Cutoff $\le 150\text{ Hz}$ (bass/kick band).
- **Band-pass**: Center frequency $\approx 1000\text{ Hz}$ (vocal/snare band).
- **High-pass**: Cutoff $\ge 5000\text{ Hz}$ (hi-hat/cymbal band).

The filter difference equation evaluates without allocations:
$$y[n] = b_0 x[n] + b_1 x[n-1] + b_2 x[n-2] - a_1 y[n-1] - a_2 y[n-2]$$

### Amplitude Extractor (`AmplitudeExtractor.kt`)
Calculates Root Mean Square (RMS) energy over each block:

$$\text{RMS} = \sqrt{\frac{1}{N} \sum_{i=1}^N x_i^2}$$

RMS values are normalized and published to `CVRegistry`:
- `amp`: Full-band input RMS.
- `bass`, `mid`, `high`: Per-band filter RMS values.

---

## Transient & Onset Detection

Musical transients drive `trigger_onset` and `trigger_accent` CV signals.

### Spectral Flux Calculation
Spectral flux measures positive frame-to-frame energy growth across frequency bands (half-wave rectified):

$$\text{Flux}_{band} = \max(0,\ \text{RMS}_{band}(t) - \text{RMS}_{band}(t-1))$$

Weighted band sum favors low-frequency kick transients:
$$\text{OnsetStrength} = \text{Flux}_{bass} \times 2.0 + \text{Flux}_{mid} \times 0.8 + \text{Flux}_{high} \times 0.3$$

### Silence Gate
When full-band RMS drops below `silenceThresholdDb` (-40 dBFS) for more than 500 ms, `SignalState` switches to `SILENT`, suppressing accidental trigger firing during quiet sections.

---

## BTrack Real-Time Beat Tracking Engine (`BeatTrackerEngine.kt`)

Beat detection and continuous modulation signal generation are handled by [`BeatTrackerEngine.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/BeatTrackerEngine.kt):
- **Complex Spectral Difference ODF**: 512-point zero-allocation FFT detecting percussive and tonal onsets.
- **Two-State Multi-Band Autocorrelation**: Unconstrained acquisition (40–200 BPM) vs. locked tracking with harmonic comb unwrapping and $\pm 2.0$ BPM/beat human tracking inertia.
- **Causal Dynamic Programming Recurrence**: Evaluates causal DP beat score recurrence using pre-tabulated logarithm tables (`logTauTable`).
- **Continuous Phase & Cosine Generator**: Outputs continuous normalized phase $\phi(t) \in [0.0, 1.0)$ and locked cosine modulation signal $\cos(2\pi \phi(t))$ via zero-allocation queries (`getPhase`, `getCosine`, `getPhaseAndCosine`, `getPhaseAndCosinePacked`).

---

## Audio Hardware Discovery & UI Caching

Querying audio devices via `AudioSystem.getMixerInfo()` and `AudioSystem.getMixer()` in Java Sound invokes native ALSA/OS audio layer introspection. Probing mixers inside the real-time ImGui render loop creates native memory allocations and file descriptor pressure that can lead to memory exhaustion (OOM).

To prevent this:
- [`AudioEngine.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt) maintains a cached list of input devices (`cachedInputDevices`) and pre-allocated name strings (`cachedDeviceNames`).
- The UI layer ([`AudioEnginePanel.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/ui/AudioEnginePanel.kt)) reads from this cache with 0 allocations on the render loop.
- Dynamic rescan is triggered explicitly via `AudioEngine.refreshInputDevices()` or the UI refresh button (`Icons.REFRESH`).

