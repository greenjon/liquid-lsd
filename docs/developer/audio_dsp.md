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
- **Capture Port Auto-Connecting**: `autoConnectInput()` specifically filters physical ports targeting `"capture"` endpoints, preventing accidental connections to output sink monitor ports in PipeWire environments.
- **Clean Port Disconnection on Teardown**: In `stop()`, input ports disconnect all active links via `jack.disconnect()` before invoking `client.deactivate()` and `client.close()`, ensuring WirePlumber cleanly removes link graph policies without throwing `proxy destroyed` errors.
- **Watchdog & Failure Handling**: If JACK startup fails on launch (native library missing or server connection unavailable), automatic background reconnect attempts are throttled and suppressed to prevent watchdog log spam on systems where JACK is not running. Manual reconnection remains available on demand from the Audio Engine panel (`tryReconnect(force = true)`).

### 2. `JavaSoundClient.kt` (macOS, Windows, Standalone Linux)
- Uses Java Sound `TargetDataLine` to capture system input audio (PCM 16-bit signed, mono/stereo 44.1kHz/48kHz).
- Runs inside a dedicated daemon thread loop.
- **Zero-Allocation Conversion**: Conversion from raw PCM byte arrays to normalized `FloatBuffer` arrays uses pre-allocated byte buffers (`byteBuffer`, `floatBuffer`), preventing JVM Garbage Collection (GC) pauses from causing visual micro-stuttering.
- **Playback-Only Mixer Filtering & Device Caching**: Before querying lines via ALSA, `isLikelyPlaybackOnly()` screens out mixers matching playback keywords (`speaker`, `headphone`, `hdmi`, `output`, `sink`, `spdif`). This prevents ALSA from opening/closing temporary PCM playback handles on the internal laptop speaker, which previously interrupted WirePlumber link negotiation and dropped internal speakers from GNOME Settings. Discovered devices are cached in `cachedInputDevices`.
- **Coordinated Teardown**: In `stop()`, capture is paused and flushed (`line.stop()`, `line.flush()`), the reader thread is joined with a timeout (`thread.join(1000)`), and the native `TargetDataLine` is closed only once the thread is confirmed idle. This eliminates native handle destruction mid-read.
- **Safe Format Probing**: Candidate lines that fail to open during format negotiation are closed immediately to prevent ALSA resource leakage.

### 3. Audio Lifecycle & WirePlumber Cooldown (`AudioEngine.kt`)
- **Settling Cooldown**: When switching devices or backends, a 150ms settling cooldown is introduced between `stop()` and `startClient()` to allow WirePlumber and ALSA kernel drivers to complete asynchronous link graph cleanup.
- **No-Op Redundant Switching**: `selectDevice()` immediately short-circuits if the requested device, backend, and active state are already matching.
- **JACK Mode Isolation**: When running under JACK / PipeWire, `getAvailableInputDevices()` returns a virtual `"JACK System Capture"` representation and bypasses JavaSound ALSA hardware scans entirely.

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

- **Subnormal / Denormal Protection**: To avoid CPU microcode stalls during trailing audio silence or decaying states, recursive state variables (`z1`, `z2`) are flushed to zero when $|\text{state}| < 10^{-15}\text{ f}$.

### Amplitude Extractor (`AmplitudeExtractor.kt`)
Calculates Root Mean Square (RMS) energy over each block:

$$\text{RMS} = \sqrt{\frac{1}{N} \sum_{i=1}^N x_i^2}$$

RMS values are normalized and published to `CVRegistry`:
- `audio_amp`: Full-band input RMS.
- `audio_bass`, `audio_mid`, `audio_high`: Per-band filter RMS values.

The divisor is per band and comes from `AudioAgc`, which is always on: `max(longTermLevel * SCALE[band], 0.02)`. The long-term level is a 20 s mean of the band's raw RMS (a running mean while the first 20 s fill, so a new feed levels in at once), the way MilkDrop compares a band to its average rather than its recent peak. `SCALE` (1.5 / 2.0 / 2.0 / 2.7 for amp / bass / mid / high) was calibrated on four 10-minute DJ mixes so steady music lands where the earlier peak follower (8 s release, 0.85 headroom) put it, which the built-in responses are tuned for. The long-term levels stop following while `SectionWatch` holds: during a breakdown, and from the moment the level dips under the breakdown ratio while one is being confirmed. So a breakdown reads quiet for as long as it lasts (on a real mix: bass 0.04 instead of the peak follower's 0.25), a bass dropout is not boosted into kicks, and the drop lands where the music before it did. When the watch adopts a new normal (a "breakdown" over 60 s, or the level over 1.6x the reference for 4 s: a quieter or a louder song) every band's reference jumps to its 1.5 s level. After 2 s under 0.003 RMS (about -50 dBFS) everything waits and starts afresh. `resetForOfflineFeed` resets it.

**Section watch (`SectionWatch.kt`)** is a port of the web TV's `web/energy.js` (`EnergyWatch`): a 1.5 s level of `0.7 bass + 0.3 amp` (fixed scale) against a 20 s reference; a *breakdown* is the level under 45% of the reference for 3 s (not in the first 20 s or in silence), a *drop* is it back above 85% after at least 4 s. Besides auto gain it feeds two modulation sources (see *Section signals* below). `WebAudioAgcVectorsTest` writes `web/tools/agc_vectors.json` and fails when it is stale; `node --test web/tools/beatclock.test.mjs` replays it through the web port, so keep the two identical (regenerate with `UPDATE_WEB_VECTORS=1`).

The rest of the stage: Flux uses its fixed scale (0.05 / 0.1) times the same per-band ratio. Every normalized signal (levels and flux) is then multiplied by `AudioEngine.response` (Audio Response, 0..2, default 1, preference `audioResponse`) and clamped to 0..1, so one setting scales every audio modulator. That is the only reactivity control: the AGC toggle and AGC Headroom slider were removed (a headroom only rescaled every signal, exactly as Response does; `AppPreferencesStore.LEGACY_DEFAULT_HEADROOM` is the 0.85 the scale was calibrated against); `AppPreferencesStore.responseForLegacyHeadroom` folds a saved `audioAgcHeadroom` into `audioResponse` once and drops both legacy keys. `inputGain` (**Input Trim** in the panel) is applied to the raw samples instead, so it is a setup control: auto gain undoes it for the CV signals, and it only changes what the detectors hear. The panel warns when the silence gate says SILENT while the post-trim input peak is over 0.01 (about -40 dBFS). The beat detector, downbeat detector and silence gate take the raw RMS, before this stage; the audio texture (FFT / waveform) is not scaled either.

---

## Transient & Spectral Flux Detection

Musical transients drive per-band Spectral Flux CV signals (`audio_flux_amp`, `audio_flux_bass`, `audio_flux_mid`, `audio_flux_high`).

### Spectral Flux Calculation
Spectral flux measures positive frame-to-frame energy growth across frequency bands (half-wave rectified):

$$\text{Flux}_{band} = \max(0,\ \text{RMS}_{band}(t) - \text{RMS}_{band}(t-1))$$

- `audio_flux_amp`: Instantaneous energy growth on the full-mix signal.
- `audio_flux_bass`: Low-frequency energy growth (kick transients and bass hits).
- `audio_flux_mid`: Mid-frequency energy growth (vocal and snare attacks).
- `audio_flux_high`: High-frequency energy growth (hi-hat and cymbal strikes).

Both RMS and Flux signals are tracked in pre-cached ring buffer histories and modulated with customizable envelope follower dynamics (Attack and Decay) per audio slot.

## Section Signals

Two CV sources come from the section watch rather than from one block's spectrum. Both sit in the Audio tab (`isAudioSource`), but `isSectionSource` keeps them out of the envelope follower, and Audio Response does not scale them: they already have their shape, and they say where the song is, not how hard it hits.

- `audio_energy` (bipolar, -1..1): `log2(short / long)` from `SectionWatch.energy`, clamped. 0 is the usual level (also during the 20 s warm-up and in silence), -1 half of it or less, +1 twice it. While a breakdown holds, `long` is frozen, so energy stays low for the whole breakdown. The audio thread pushes it with its history. `CvModulator` treats it as bipolar (range dc ± depth); `isCvSourceBipolar` stays false because the scope reads that as "deterministic, can draw ahead".
- `audio_drop` (0..1): `cv/SectionDrop.kt`, a `CVSource` the render loop updates from the beat clock. When the watch's drop count goes up, the hit is dated back by `SectionWatch.dropLag` and snapped to the nearest beat (never later than now). Then it falls as (1 - t)² over `SectionDrop.BARS` (4) bars. A 0.1 s `fast` level records when the low end actually came back (`backAt`). It counts as back until it has been under the drop ratio for more than 0.7 s (`FAST_GAP`), so the gaps between kicks don't reset it. The drop is called when the fast level has been back for 0.5 s (`DROP_CONFIRM`) and is above the ratio at that moment, which takes two loud hits in a row, not one hit and its tail. It is also called, as before, when `short` (1.5 s) gets back over the ratio, which on its own takes 1-2.5 s. `dropLag` is the time since `backAt`, capped at 4 s. On synthetic kick tracks at 90-174 bpm the drop is called 0.5-0.7 s after the bass returns and dated to within 0.07 s of it. A kick less than 0.7 s before the drop can date it one beat early. The earlier call also ends auto gain's freeze sooner, and the web TV's autopilot cuts sooner on a drop.

The web port is `EnergyWatch.energy` / `dropLag` and `DropEnvelope` in `web/energy.js`. `web/renderer.js` reports drops at the analysis-time beat and reads the envelope at the heard beat, so SYNC delays the hit along with everything else. `audio_energy` goes through the same delay line as the other audio signals. REACT doesn't scale them; reduced motion caps the drop at 60%.

### Silence Gate
When full-band RMS drops below `silenceThresholdDb` (-40 dBFS) for more than 500 ms, `SignalState` switches to `SILENT`, suppressing accidental transient firing during quiet sections.

---

## Beat Tracker Engine (`BeatTrackerEngine.kt`)

Beat detection and continuous modulation signal generation are handled by [`BeatTrackerEngine.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/BeatTrackerEngine.kt) (inspired by BTrack and the Dan Ellis causal dynamic programming model):
- **Complex Spectral Difference ODF**: 512-point zero-allocation FFT detecting percussive and tonal onsets.
- **Two-State Multi-Band Autocorrelation**: Unconstrained acquisition (40–200 BPM) vs. locked tracking with harmonic comb unwrapping and $\pm 2.0$ BPM/beat human tracking inertia.
- **Decoupled Periodic Tempo Estimation**: Autocorrelation search across multi-second history is decoupled from the per-block rate via `tempoEstimationIntervalBlocks` (default 4 blocks / ~46 ms, ~21.5 Hz), reducing CPU iterations by 75% to prevent real-time XRUNs while preserving instantaneous onset reactivity.
- **Causal Dynamic Programming Recurrence**: Evaluates causal DP beat score recurrence on every audio frame using pre-tabulated logarithm tables (`logTauTable`).
- **Continuous Phase & Cosine Generator**: Outputs continuous normalized phase $\phi(t) \in [0.0, 1.0)$ and locked cosine modulation signal $\cos(2\pi \phi(t))$ via zero-allocation queries (`getPhase`, `getCosine`, `getPhaseAndCosine`, `getPhaseAndCosinePacked`).

---

## Shader Audio Texture Spectrum (`SpectrumAnalyzer.kt`)

Row 0 of the 512x2 `audioFFT` / `iChannel0` texture (`AudioTexture.kt`) comes from [`SpectrumAnalyzer.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/SpectrumAnalyzer.kt), not from the beat tracker (which keeps no display spectrum). `AudioEngine.processAudio` pushes each block's mixed mono samples into it after the waveform tap.
- **Transform:** newest 1024 samples, Hann window, radix-2 FFT, 512 linear bins (0 Hz to Nyquist; bin spacing sampleRate/1024).
- **Scaling:** magnitude normalised so a full-scale sine is 1.0, then dB mapped -80..0 dB onto 0..1.
- **Smoothing:** instant attack, one-pole release with a 0.08 s time constant computed from the block length, so it does not depend on the buffer size.
- **Threading:** zero allocation; written on the audio thread into a double buffer and published by a volatile index; `copyAudioTextureData` copies on the render thread. No clock or random input, so a fixed feed gives identical output (tour renders rely on this); `resetForOfflineFeed` resets it.
- **Cost:** about 19 microseconds per 512-frame block (measured on the dev machine).

---

## Audio Hardware Discovery & UI Caching

Querying audio devices via `AudioSystem.getMixerInfo()` and `AudioSystem.getMixer()` in Java Sound invokes native ALSA/OS audio layer introspection. Probing mixers inside the real-time ImGui render loop creates native memory allocations and file descriptor pressure that can lead to memory exhaustion (OOM).

To prevent this:
- [`AudioEngine.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt) maintains a cached list of input devices (`cachedInputDevices`) and pre-allocated name strings (`cachedDeviceNames`).
- The UI layer ([`AudioEnginePanel.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/ui/AudioEnginePanel.kt)) reads from this cache with 0 allocations on the render loop.
- Dynamic rescan is triggered explicitly via `AudioEngine.refreshInputDevices()` or the UI refresh button (`Icons.REFRESH`).

---

## Stereo Pipeline, Channel Routing & Input Metering

Liquid LSD ingests dual-channel (stereo) audio from JACK/PipeWire and Java Sound, and provides user-selectable routing into the downstream DSP analysis filter bank:

### 1. Channel Routing Strategy (`AudioChannelRouting.kt`)
The operational signal $s[i]$ fed into the biquad filter bank and beat detector is determined by the selected routing mode:
- **`MIX` ("Mix (L + R)") [Default]**:
  Uses a standard arithmetic -6 dB downmix:
  $$s[i] = 0.5 \cdot (L[i] + R[i])$$
  - **Headroom Guarantee**: Perfectly coherent in-phase material ($L=1.0, R=1.0$) sums to exactly $1.0$ (0 dBFS). Clipping is mathematically prevented without requiring artificial compressors or non-linear saturation that could smear transient detection.
  - **Unity Level Consistency**: When switching between `Mix`, `Left Only`, and `Right Only` on standard mono or center-panned sources, the signal level remains identical (1.0x).
  - **Mono Capture Fallback**: When only a single physical capture port is available, `MIX` mode passes $L[i]$ directly at unity gain without the -6 dB attenuation penalty.
- **`LEFT_ONLY` ("Left Only")**: Routes Left channel directly ($s[i] = L[i]$).
- **`RIGHT_ONLY` ("Right Only")**: Routes Right channel directly ($s[i] = R[i]$, or silence if no right channel is connected).

### 2. Real-Time 2-Channel Input Peak Metering
[`AudioEnginePanel.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/ui/AudioEnginePanel.kt) renders a 2-channel stereo peak/RMS meter with:
- **Independent Channel Visualization**: Left and Right levels are measured on raw incoming audio *before* routing. If a user feeds a dead Left channel and an active Right channel, the meter immediately reveals the hardware state.
- **Visual Routing Feedback**: Inactive channels are drawn with dimmed opacity and a `[Bypassed]` badge when `Left Only` or `Right Only` routing is selected.
- **Ballistics**: Fast attack with smooth exponential release (~20 dB/s) and a 1.2-second peak-hold indicator tick.
- **Clipping Warning**: Instant red `CLIP` indicator when peak level $\ge 1.0$.

---

## Audio Engine Settings Persistence

All Audio Engine configuration parameters are serialized to and restored from `lsd-settings.properties` via [`UITheme.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt):
- `audioEngineEnabled`: Master toggle for audio processing.
- `audioBackend`: Selected audio backend mode (`AUTO`, `JACK_ONLY`, `JAVASOUND_ONLY`).
- `audioChannelRouting`: Stereo routing selector (`MIX`, `LEFT_ONLY`, `RIGHT_ONLY`).
- `audioDeviceName`: Explicitly selected audio hardware input device name (or default).
- `audioInputGain`: Input pre-amplification multiplier (0.0x–10.0x).
- `audioBpmLocked`: Manual BPM lock state.
- `audioManualBpm`: Manual tempo setting (40–200 BPM).
- `audioBeatTarget`: Frequency band target for onset detection (`LOW`, `MID`, `HIGH`, `UNFILTERED`).
- `audioBpmFloor` & `audioBpmCeiling`: BPM search range bounds for the Beat Tracker.
- `audioTransitionAlpha` & `audioTrackingInertia`: Beat Tracker tuning constants.



