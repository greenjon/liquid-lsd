# Modulation System

Every visual parameter in Liquid LSD is a `ModulatableParameter`. Each parameter holds a base value
and a list of `CvModulator` instances. Once per frame `evaluate()` computes the final driven value
by combining those modulators. This document explains the full pipeline.

---

## ModulatableParameter

**File**: `parameters/ModulatableParameter.kt`

```
baseValue ──┐
            ├──► evaluate() ──► value  (clamped to [minClamp, maxClamp])
modulators ─┘                     │
                                  └──► history (CvHistoryBuffer, 200 samples)
```

Key properties:

| Property | Purpose |
|----------|---------|
| `baseValue` | Starting value before any modulation |
| `baseMin / baseMax` | Randomization range for `baseValue` at preset-load time |
| `randomizeBase` | If true, `baseValue` is re-rolled at preset-load from `[baseMin, baseMax]` |
| `minClamp / maxClamp` | Hard output clamp; also determines polarity (see below) |
| `meterType` | `MONOPOLAR` (default), `BIPOLAR` (minClamp < 0), `ENDLESS`, `DISCRETE` |
| `modulators` | `CopyOnWriteArrayList<CvModulator>` — safe for read-heavy concurrent access |
| `modulatorFilter` | Optional `@Volatile` predicate; when set only matching modulators are evaluated (used by deck-preview mode) |
| `value` | Last evaluated output (internal set; updated every `evaluate()` call) |
| `history` | `CvHistoryBuffer` ring buffer — read by the oscilloscope in `PropertiesPanel` |
| `scopeTimebase` | `ScopeTimebase` (`AUTO`, `1s`, `10s`, `100s`, `15m`, `2.5h`, `24h`) — transient per-parameter oscilloscope zoom |

MIDI mapping fields (`mappedMidiId`, `midiMapMin`, `midiMapMax`) are deprecated. Use
`MidiMappingManager` profiles instead.

### evaluate() — per-frame pipeline

```kotlin
// Simplified from ModulatableParameter.evaluate()

result = baseValue

for (mod in modulators) {
    if (bypassed || not registered || filtered) continue

    rawCV = evaluateModulator(mod)          // Evaluators.kt — see below

    // Same shape for every source and target.
    rawModAmount = rawCV * depth + dcOffset   // CvModulator.lfoShape

    // Scale to parameter range
    scalar = if (ADD) then (range / 2) for bipolar, else range for monopolar; else 1.0
    modAmount = rawModAmount * scalar

    result = when (operator) {
        ADD   -> result + modAmount
        MUL   -> result * (1 + modAmount)
        SCALE -> result * (1 - depth + modAmount)
    }
}

value = result.coerceIn(minClamp, maxClamp)
```

**Polarity**: if `minClamp < 0`, the parameter is bipolar; only the ADD scalar differs (`range / 2` for
bipolar, `range` otherwise). A bipolar source (LFO, sample-and-hold) swings `dcOffset ± depth` on every
parameter, so on a 0..1 parameter it goes below the base value unless `dcOffset` lifts it; unipolar sources
(audio, trigger, MIDI CC) span `dcOffset .. dcOffset + depth`. (Before 2026-10-27 a bipolar source on a
monopolar parameter was remapped to `[0, 1]` first; stock presets and recipes were rewritten to keep their sweep.)

---

## CvModulator — Full Field Reference

**File**: `parameters/CvModulator.kt`  
**Serializable** — all fields are stored in `.lsd` preset files.

### Identity

| Field | Type | Purpose |
|-------|------|---------|
| `sourceId` | `String` | Which CV signal drives this modulator. Registered IDs: `lfo`, `beatPhase`, `sampleAndHold`, `audio_amp`, `audio_bass`, `audio_mid`, `audio_high`, `audio_flux_amp`, `audio_flux_bass`, `audio_flux_mid`, `audio_flux_high`, `bpm`, `midi_cc_<ch>_<cc>` (`trigger_onset`/`trigger_accent` exist only in the WebGL2 web client's `dsp.js`/`evaluator.js` — see `docs/developer/web_subsystem.md` — not as a desktop `CvModulator` source) |
| `operator` | `ModulationOperator` | How this modulator combines with the accumulated result |
| `bypassed` | `Boolean` | If true, skipped entirely in `evaluate()` |
| `id` | `String` (UUID) | Unique identity for undo/copy-paste tracking |

### LFO 1 (Carrier) — applies to all `sourceId`s that use waveform evaluation

| Field | Type | Range | Purpose |
|-------|------|-------|---------|
| `depth` | `Float` | `[0, 1]` | Peak modulation depth |
| `dcOffset` | `Float` | `[-1, 1]` | Constant offset added after depth scaling |
| `waveform` | `Waveform` | — | Shape: `SINE`, `TRIANGLE`, `SQUARE`, `RANDOM` |
| `subdivision` | `Float` | Seconds (`TIME`), beats (`BEAT`), or frames (`FRAME`) | One cycle duration |
| `phaseOffset` | `Float` | `[0, 1]` | Phase shift within the cycle |
| `slope` | `Float` | `[0, 1]` | Asymmetry: `0` = sharp fall/slow rise, `0.5` = symmetric, `1` = slow rise/sharp fall |
| `morph` | `Float` | `[0, 1]` | Waveshaping softness (0 = linear triangle, 1 = near-sine via `cosh` approximation) |
| `hold` | `Float` | `[0, 0.99]` | Plateau width at peak (compresses the transition region) |
| `genUnit` | `GenUnit` | `TIME`, `BEAT`, or `FRAME` | Whether `subdivision` is measured in seconds, musical beats, or render frames (1-10000). Only meaningful for `lfo`. |
| `lfoSpeedMode` | `LfoSpeedMode` | `SLOW/MEDIUM/FAST` | UI-only speed range hint for the slider |

### Randomization Ranges

Every continuously-valued field has a `Min`/`Max` pair and a `randomize*` flag:

```
depthMin / depthMax / randomizeDepth
subdivisionMin / subdivisionMax / randomizeSubdivision
phaseOffsetMin / phaseOffsetMax / randomizePhaseOffset
slopeMin / slopeMax / randomizeSlope
morphMin / morphMax / randomizeMorph
holdMin / holdMax / randomizeHold
dcOffsetMin / dcOffsetMax / randomizeDcOffset
```

When the preset randomize action fires, `randomizeActiveValues()` samples uniformly within each
`[min, max]` range. For beat-based subdivision the sample is drawn from a discrete set of musical
values `{1/8, 1/4, 1/2, 1, 2, 4, 8 … 256}`. For frame-based subdivision, it samples discrete integers in `[min, max]`.

### Modulation Range Bounds (Min/Max Mode)

For LFO 1 and Audio followers, the UI presents human-readable **Min Value** and **Max Value** modulation bounds rather than abstract `depth` (amplitude) and `dcOffset` (center).

- **Mapping**:
  - $\text{Min} = \text{dcOffset} - \text{depth}$
  - $\text{Max} = \text{dcOffset} + \text{depth}$
  - $\text{depth} = (\text{Max} - \text{Min}) / 2$
  - $\text{dcOffset} = (\text{Max} + \text{Min}) / 2$
- **Methods**: `CvModulator.getLfoMin()`, `CvModulator.getLfoMax()`, `CvModulator.withLfoRange(min, max)`.
- **Randomization Range Storage**: When `lfoMinMaxMode` is true, the two-tier randomization sliders store the bounds for the Minimum limit in `dcOffsetMin` / `dcOffsetMax` (with flag `randomizeDcOffset`) and the bounds for the Maximum limit in `depthMin` / `depthMax` (with flag `randomizeDepth`). This ensures complete preset serialization compatibility without altering the underlying DTO schema.

### Audio Envelope Followers — applies when `isAudioSource(sourceId)`

When a modulator uses an audio band source (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`), it can apply an independent asymmetric one-pole IIR envelope follower:

| Field | Type | Range | Purpose |
|-------|------|-------|---------|
| `followerMode` | `AudioFollowerMode` | `RAW`, `PUNCHY`, `SMOOTH`, `SLOW`, `AMBIENT`, `CUSTOM` | Envelope dynamics preset mode |
| `attackMs` | `Float` | `[0, 500]` ms | Rise time constant ($\tau_{\text{att}}$) |
| `decayMs` | `Float` | `[10, 3000]` ms | Fall time constant ($\tau_{\text{dec}}$) |
| `attackMsMin / attackMsMax` | `Float` | `[0, 500]` ms | Randomization bounds for attack time |
| `decayMsMin / decayMsMax` | `Float` | `[10, 3000]` ms | Randomization bounds for decay time |
| `randomizeAttackMs / randomizeDecayMs` | `Boolean` | `true/false` | Enables randomization for attack/decay |

- **Evaluation Pipeline**: Implemented in `AudioFollowerTracker` inside `Evaluators.kt`. Uses framerate-independent exponential one-pole smoothing:
  $$\alpha = 1 - e^{-\Delta t / \tau}$$
  $$\text{If } x > y_{\text{prev}}: \quad y = y_{\text{prev}} + \alpha_{\text{att}} \cdot (x - y_{\text{prev}})$$
  $$\text{If } x \le y_{\text{prev}}: \quad y = y_{\text{prev}} + \alpha_{\text{dec}} \cdot (x - y_{\text{prev}})$$
- **Dual-Trace Scope**: In `OscilloscopeDrawer.drawOscilloscope`, `ghostHistory` receives the un-followed raw band energy ($35\%$ alpha) while the main line plots the filtered follower output.

### LFO 2 (Modulator) — only evaluated when `sourceId == "lfo"`

LFO 2 is a second oscillator that modulates properties of LFO 1. It has its own waveform,
speed, phase, slope, morph, and hold:

| Field | Purpose |
|-------|---------|
| `modWaveform` | LFO 2 shape |
| `modSubdivision` | LFO 2 cycle duration |
| `modPhaseOffset` | LFO 2 phase shift |
| `modSlope` | LFO 2 asymmetry |
| `modMorph` | LFO 2 waveshaping |
| `modHold` | LFO 2 plateau |
| `modGenUnit` | `TIME`, `BEAT`, or `FRAME` for LFO 2's clock |
| `generatorModMode` | How LFO 2 acts on LFO 1: `NONE`, `AM`, `PM`, or `ADD` |
| `generatorModDepth` | How much LFO 2 influences LFO 1 |

Randomization ranges also exist for all mod-fields (`modSubdivisionMin/Max`, etc.).

---

## Enums

**File**: `parameters/Enums.kt`

### ModulationOperator

| Value | Formula |
|-------|---------|
| `ADD` | `result + modAmount` — adds CV signal scaled to parameter range |
| `MUL` | `result * (1 + modAmount)` — multiplicative ring-mod style |
| `SCALE` | `result * (1 - depth + modAmount)` — attenuates the base value |

### Waveform

| Value | Shape |
|-------|-------|
| `SINE` | Standard sinusoid |
| `TRIANGLE` | Linear ramp with `slope`-controlled asymmetry |
| `SQUARE` | Duty-cycle set by `slope` |
| `RANDOM` | Per-cycle sample-and-glide (deterministic from subdivision + phaseOffset hash) |

### GenUnit

| Value | Meaning |
|-------|---------|
| `TIME` | `subdivision` is a period in **seconds** |
| `BEAT` | `subdivision` is a number of **beats** (synced to `CVRegistry.getSynchronizedTotalBeats()`) |
| `FRAME` | `subdivision` is a number of **render frames** (1 to 10,000 frames, synced to `CVRegistry.getRenderFrameCount()`) |

When `genUnit == BEAT` the subdivision randomizer draws from the discrete musical grid
rather than a continuous uniform. When `genUnit == FRAME`, the randomizer and UI lock strictly to integer frame counts. Beat mode uses `getSynchronizedTotalBeats()` which is an
interpolated estimate updated from the JACK audio thread via `@Volatile` fields in `CVRegistry`. Frame mode uses monotonic render frame ticks updated on each frame.

**Period changes stay continuous (`ClockAnchors`).** A `TIME` or `FRAME` generator's position is `clock / period`, and the clock is the seconds (or frames) since start, so changing the period moved the position by `clock` times the change in `1 / period`: an hour in, turning an LFO's rate, a macro or OSC on its subdivision, or a preset morph lerping it jumped the LFO, every frame while it moved. `ClockAnchors` (one per `ModulatableParameter`, keyed by modulator id, separate tables for LFO 1 / sequencer and LFO 2) keeps a shift that absorbs `clock / old - clock / new` when the period changes, so only the rate changes. It is per parameter because the same preset on two decks shares modulator ids. `evaluateModulator`, `evaluateModulatorAtOffset` and `seqCyclePosition` take it as an optional argument (`ModulatableParameter.evaluate` and the sequencer playhead pass `clockAnchors`); look-ahead offsets never move it, and a clock-unit change resets it. `BEAT` generators are not anchored: they stay locked to the bar. RANDOM waveforms still reseed when the period changes (the seed hashes it). The web evaluator mirrors it in `anchoredCycles`, keyed by modulator id in `ctx.anchors`, since broadcast messages replace the modulator objects.

### GeneratorModMode (LFO 2 interaction)

| Value | Effect on LFO 1 |
|-------|----------------|
| `NONE` | LFO 2 disabled; LFO 1 runs independently |
| `AM` | Amplitude Modulation — `carrierVal * (1 + lfo2Val * depth)` |
| `PM` | Phase Modulation — shifts LFO 1's `phaseOffset` by `lfo2Val * depth` before evaluation |
| `ADD` | Additive — `carrierVal + lfo2Val * depth` |

### LfoSpeedMode

UI hint only (`SLOW`, `MEDIUM`, `FAST`). Controls the slider range shown in `PropertiesPanel`
when the user adjusts `subdivision` in time mode. Does not affect evaluation.

---

## Waveform Math

**File**: `parameters/WaveformMath.kt`

### `calculateAdvancedLFO(phase, morph, hold, slope, waveform)` — used by `lfo`, `beatPhase`, `sampleAndHold`

```
1. If waveform == SQUARE:
   - slope functions as Duty Cycle D in [0.001, 0.999] (PWM)
   - Threshold voltage: V_th = 1.0 - 2.0 * D
   - Slices symmetric triangle through threshold V_th with rising edge aligned to phase 0.0
   - Scales by 1.0 / (1.0 - safeHold) and clamps to [-1, 1]
   - Applies morph log-cosh waveshaping
2. If non-square (SINE, TRIANGLE):
   - Extreme slope boundary:
     - `slope >= 0.999f`: evaluated as a pure unidirectional rising sawtooth ramp (`phase.toFloat()`, 0 to 1) with instantaneous wrap to 0 at the period boundary (no reverse fall slope).
     - `slope <= 0.001f`: evaluated as a pure unidirectional falling sawtooth ramp (`(1.0 - phase).toFloat()`, 1 to 0) with instantaneous wrap to 1 at the period boundary.
     - `0.001f < slope < 0.999f`: computes raw asymmetric triangle (ramp up to 1 from [0, slope), ramp down to 0 from [slope, 1)).
   - Stretch by hold: plateau the peak by compressing transition into (1 - safeHold) of the cycle
   - safeHold is clamped to [0.0, 0.999] to prevent division by zero while enabling steep vertical edges
   - If `morph >= 0.999f`: bypasses waveshaper entirely and returns `shaped` directly (zero rounding, zero peak deceleration, exact linear segments).
   - Otherwise, apply morph via log-cosh waveshaper:
       k = lerp(1.5, 15.0, morph)         (higher k = sharper, more triangle/square-like; lower k = rounded sine)
       shaped = log(cosh(k * tri)) / (k * maxVal)
3. Output in [-1, 1]
```

`morph = 0` → rounded sine-like corners. `morph = 1` → pure linear sharp transitions with zero apex rounding. Hold defaults to `0.999` for square waves.

### `RANDOM` waveform

Uses a deterministic per-cycle hash seeded from `(subdivision, phaseOffset, modulator.id)`.
Each cycle independently samples `previousValue` and `currentValue` from the hash, then
interpolates between them using the `hold`/`morph` shaping above (`calculateRandomWaveform`
in `Evaluators.kt`).

---

## Evaluators — `evaluateModulator()` Dispatch

**File**: `cv/Evaluators.kt`

```
evaluateModulator(mod) → Float   (range [-1, 1])

  "beatPhase"     → phase-locked to beat; RANDOM uses per-cycle hash
  "sampleAndHold" → always beat-clocked; RANDOM waveform only
  "lfo"           → full generator:
                    1. evaluate LFO 2 (mod* fields) if generatorModMode != NONE
                    2. apply PM shift to LFO 1 phase if mode == PM
                    3. evaluate LFO 1 (carrier) in TIME or BEAT clock
                    4. combine: AM → carrier*(1+lfo2*depth)
                                ADD → carrier + lfo2*depth
                                PM/NONE → carrier
  "audio_*"       → CVRegistry.get(id) — pushed from AudioEngine each frame (includes audio_flux_* onset/transient signals)
  "bpm"           → CVRegistry.get("bpm")
  "midi_cc_*"     → MidiEngine.getCcValue(channel, cc)
  else            → CVRegistry.get(id) — any registered custom source
```

---

## Reactivity Recipes

Shaders ship hand-written audio and beat responses ("recipes"). Once applied they are ordinary modulators; how strongly audio modulators respond is set by Audio Response, on the signals (see [audio_dsp.md](audio_dsp.md)), not per modulator.

- **Files:** `src/main/resources/reactivity/sources/<id>.json` (`ReactivityRecipes.forId(kind, id)`; only `sources` exist so far). Per parameter an ordered `stack` of modulators; entries with `"active": true` run, the others are wired but bypassed. Entry keys are `ModulatorDto` fields plus `active`, `name`, `note`; defaults are operator `ADD`, LFO unit `BEAT`, follower `PUNCHY` when an attack/decay is given. A parameter may carry `base` to move its set value. The `RANDOM` waveform is the sample-and-hold.
- **Applying:** `GeneratorDefaults.resolve` lays the recipe over the stock (curated or heuristic) default through `ReactivityRecipes.overlay`; a saved user default wins and carries whatever modulators the user left. Overlay sets each named parameter's modulators to fresh copies of its stack (new ids, so envelope followers are never shared between decks; laying it over twice does not stack it) and leaves other parameters alone. It runs on a freshly cloned source, so nothing hand-built is lost. Loading a preset that stores its own parameters replaces the recipe, like any default.
- **Authoring rule:** an LFO on a 0..1 parameter that sits at (or near) its minimum swings both ways around the set value. To sweep upward use `depth = dcOffset = d/2` (range `base .. base + d`). `ReactivityRecipesTest.lfoSwingsFitTheirParameters` fails when any LFO swing leaves its parameter's range (the clamp would flatten part of every cycle); `everyShippedSourceRecipeIsValid` checks parameter names against the shader, one active modulator per parameter, and `|depth| <= 1`.
- **Audio levels:** recipes are tuned against the AGC (`audio/AudioAgc.kt`), which normalises each band against its own recent peak. The web TV still uses the fixed 0.25 scale, so recipes will read quieter there until the AGC is ported.
- **Not covered yet:** FX (`FxDefaultDto.parameters` cannot hold modulators), transitions (no defaults store), a UI marker for recipe modulators and a "reset to recipe" action. Fractal Flame's recipe only touches Breathe, Warp, Spin, Drift, Magnify, Exposure, Brightness, Decay, Vibrancy and Mutation, never the morph-driven xform parameters. Fluid Ink has none, and Harmonograph left the stock set.

---

## Serialization

`CvModulator` is `@Serializable` (kotlinx-serialization). The DTO conversion lives in
`models/PresetModels.kt`:

- `ModulatorDto.toDomain()` — load path; includes a one-line migration:
  `"gen1" / "gen2"` → `"lfo"` to silently upgrade patches saved before the rename.
- `CvModulator.toDto()` — save path; field names are stable (some use `@SerialName` aliases,
  e.g. `weight` for `depth`).

Do not rename `CvModulator` fields without adding a corresponding `@SerialName` or migration in
`toDomain()`.

---

## ParameterResolver

**File**: `parameters/ParameterResolver.kt`

`getAllParameterPaths(mixer)` returns every `(path, ModulatableParameter)` pair in the mixer,
using the standard hierarchical path format (e.g. `"Deck A/Mandala/Lobes"`, `"Deck A/View/Zoom"`, `"Deck A/FB/Decay"`). Used for undo snapshots and MIDI learn target resolution.

All dynamic visual sources (`Mandala`, plus pure-ISF sources like `dynamic_spiral` and `icosa_h3` loaded via `ISFVisualSource`) inherit from `DynamicVisualSource`, exposing their parameter map generically as `"Deck <X>/<DisplayName>/<Param>"` plus `"Deck <X>/<DisplayName>/Gain"`. (The standalone `DynamicSpiral.kt` and `Icosahedron.kt` Kotlin classes referenced by older docs were retired when those generators were migrated to pure ISF v2.0 shader files — see `DECISIONS.md`.)

The overall parameter categories cover:
- Deck Visual Source: mapped dynamically from `source.parameters` (e.g. Lobes, Recipe Select, L1–L4, Thickness, Hue Offset, Hue Sweep, Depth, etc.) + `Gain`
- Deck View: 3DMode, Zoom, RotateX, RotateY, RotateZ, Persp, DepthDim, Separation, BlendMode
- Deck Feedback: Decay, Gain, Zoom, Rotate, HueShift, Blur, Chroma, Mode, Kaleido
- Mixer: crossfade, xfadeSpeed, levelA, levelB, levelBG, levelPV, masterLevel, queuePrev, queueNext, bgQueuePrev, bgQueueNext, randDeckA, randDeckB, randDeckBG, randDeckPV, randAll

---

## Crossfade Manual Takeover & Auto-Centering

**Files**: `rendering/Mixer.kt`, `ui/MixerPanel.kt`, `ui/ParametersRenderer.kt`, `ui/PropertiesPanel.kt`, `midi/MidiMappingManager.kt`

The master crossfader (`Mixer/crossfade`) implements a performance takeover workflow:
1. **Manual Takeover (`Mixer.onCrossfadeManualTakeover()`)**: Triggered when the user interacts with the crossfader slider via mouse or incoming hardware MIDI CC.
   - Disarms Auto-VJ (`PlayQueueManager.isAutoVJEnabled = false`).
   - Halts any active automated fade (`Mixer.isAutoFading = false`).
   - Mutes all non-MIDI modulators on the crossfader (`mod.bypassed = true`), ensuring immediate 1:1 manual authority.
2. **Auto-Centering on Unmute (`Mixer.onCrossfadeCvUnmuted()`)**: Triggered when any CV modulator on `Mixer/crossfade` is unmuted or assigned. Snaps `baseValue` to `0.0f` to ensure modulation oscillates symmetrically between decks.
   - Resets `crossfade.baseValue = 0.0f` (and baseMin/baseMax if non-randomized).
   - Guarantees immediate full-range, unbiased $[-1.0, +1.0]$ oscillation without waveform clipping against previous manual hold positions. Modulator `dcOffset` provides optional deck bias.

---

## Continuous Constrained Random Morphing

**Files**: `rendering/MorphState.kt`, `rendering/Deck.kt`, `rendering/Mixer.kt`

The randomization parameters (`Mixer/randDeckA`, `randDeckB`, `randDeckBG`, `randDeckPV`, `randAll`) operate as continuous morphing controllers:
* **Two-State Snapshot Model**: Each `DeckMorphController` maintains two state snapshots (`state0` and `state1`) storing base values and all active modulator parameters.
* **In-Place Zero-Allocation Lerping**: Per-frame interpolation mutates runtime fields directly on `CvModulator` and `ModulatableParameter` without creating garbage collection pressure on the render thread.
* **Boundary Hysteresis**: Re-rolls opposite state targets at the $0.01$ and $0.99$ normalized thresholds via a flip-flop state machine (`READY_FOR_ONE` / `READY_FOR_ZERO`).
* **Shortest-Path Angle/Hue Wrapping**: Uses modular circular arithmetic on angle and hue parameters to prevent unwanted unwinding spins.

