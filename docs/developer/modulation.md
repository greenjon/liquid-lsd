# Modulation System

Every visual parameter in Spirals is a `ModulatableParameter`. Each parameter holds a base value
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
| `baseMin / baseMax` | Randomization range for `baseValue` at patch-load time |
| `randomizeBase` | If true, `baseValue` is re-rolled at patch-load from `[baseMin, baseMax]` |
| `minClamp / maxClamp` | Hard output clamp; also determines polarity (see below) |
| `meterType` | `MONOPOLAR` (default), `BIPOLAR` (minClamp < 0), `ENDLESS`, `DISCRETE` |
| `modulators` | `CopyOnWriteArrayList<CvModulator>` — safe for read-heavy concurrent access |
| `modulatorFilter` | Optional `@Volatile` predicate; when set only matching modulators are evaluated (used by deck-preview mode) |
| `value` | Last evaluated output (internal set; updated every `evaluate()` call) |
| `history` | `CvHistoryBuffer` ring buffer — read by the oscilloscope in `CellConfigPanel` |

MIDI mapping fields (`mappedMidiId`, `midiMapMin`, `midiMapMax`) are deprecated. Use
`MidiMappingManager` profiles instead.

### evaluate() — per-frame pipeline

```kotlin
// Simplified from ModulatableParameter.evaluate()

result = baseValue

for (mod in modulators) {
    if (bypassed || not registered || filtered) continue

    rawCV = evaluateModulator(mod)          // Evaluators.kt — see below

    // Polarity normalisation
    rawModAmount = if (isBipolar)
        rawCV * amplitude + dcOffset        // bipolar: symmetric around 0
    else
        ((rawCV + 1) / 2) * amplitude + dcOffset  // monopolar: maps [-1,1] → [0,amp]+dc

    // Scale to parameter range
    scalar = if (ADD) then (range / 2) for bipolar, else range for monopolar; else 1.0
    modAmount = rawModAmount * scalar

    result = when (operator) {
        ADD   -> result + modAmount
        MUL   -> result * (1 + modAmount)
        SCALE -> result * (1 - amplitude + modAmount)
    }
}

value = result.coerceIn(minClamp, maxClamp)
```

**Polarity**: if `minClamp < 0`, the parameter is bipolar. Monopolar parameters map the raw CV
signal from `[-1, 1]` into `[0, 1]` before scaling, so negative CV still produces positive
modulation effect.

---

## CvModulator — Full Field Reference

**File**: `parameters/CvModulator.kt`  
**Serializable** — all fields are stored in `.lsd` patch files.

### Identity

| Field | Type | Purpose |
|-------|------|---------|
| `sourceId` | `String` | Which CV signal drives this modulator. Registered IDs: `lfo`, `beatPhase`, `sampleAndHold`, `audio_amp`, `audio_bass`, `audio_mid`, `audio_high`, `trigger_onset`, `trigger_accent`, `bpm`, `midi_cc_<ch>_<cc>` |
| `operator` | `ModulationOperator` | How this modulator combines with the accumulated result |
| `bypassed` | `Boolean` | If true, skipped entirely in `evaluate()` |
| `id` | `String` (UUID) | Unique identity for undo/copy-paste tracking |

### LFO 1 (Carrier) — applies to all `sourceId`s that use waveform evaluation

| Field | Type | Range | Purpose |
|-------|------|-------|---------|
| `amplitude` | `Float` | `[0, 1]` | Peak modulation depth |
| `dcOffset` | `Float` | `[-1, 1]` | Constant offset added after amplitude scaling |
| `waveform` | `Waveform` | — | Shape: `SINE`, `TRIANGLE`, `SQUARE`, `RANDOM` |
| `subdivision` | `Float` | Seconds (TIME) or beats (BEAT) | One cycle duration |
| `phaseOffset` | `Float` | `[0, 1]` | Phase shift within the cycle |
| `slope` | `Float` | `[0, 1]` | Asymmetry: `0` = sharp fall/slow rise, `0.5` = symmetric, `1` = slow rise/sharp fall |
| `morph` | `Float` | `[0, 1]` | Waveshaping softness (0 = linear triangle, 1 = near-sine via `cosh` approximation) |
| `hold` | `Float` | `[0, 0.99]` | Plateau width at peak (compresses the transition region) |
| `genUnit` | `GenUnit` | `TIME` or `BEAT` | Whether `subdivision` is measured in seconds or musical beats. Only meaningful for `lfo`. |
| `lfoSpeedMode` | `LfoSpeedMode` | `SLOW/MEDIUM/FAST` | UI-only speed range hint for the slider |

### Randomization Ranges

Every continuously-valued field has a `Min`/`Max` pair and a `randomize*` flag:

```
amplitudeMin / amplitudeMax / randomizeAmplitude
subdivisionMin / subdivisionMax / randomizeSubdivision
phaseOffsetMin / phaseOffsetMax / randomizePhaseOffset
slopeMin / slopeMax / randomizeSlope
morphMin / morphMax / randomizeMorph
holdMin / holdMax / randomizeHold
dcOffsetMin / dcOffsetMax / randomizeDcOffset
```

When the patch randomize action fires, `randomizeActiveValues()` samples uniformly within each
`[min, max]` range. For beat-based subdivision the sample is drawn from a discrete set of musical
values `{1/8, 1/4, 1/2, 1, 2, 4, 8 … 256}` instead of a continuous uniform.

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
| `modGenUnit` | `TIME` or `BEAT` for LFO 2's clock |
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
| `SCALE` | `result * (1 - amplitude + modAmount)` — attenuates the base value |

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

When `genUnit == BEAT` the subdivision randomizer draws from the discrete musical grid
rather than a continuous uniform. Beat mode uses `getSynchronizedTotalBeats()` which is an
interpolated estimate updated atomically from the JACK audio thread via `BeatAnchor`.

### GeneratorModMode (LFO 2 interaction)

| Value | Effect on LFO 1 |
|-------|----------------|
| `NONE` | LFO 2 disabled; LFO 1 runs independently |
| `AM` | Amplitude Modulation — `carrierVal * (1 + lfo2Val * depth)` |
| `PM` | Phase Modulation — shifts LFO 1's `phaseOffset` by `lfo2Val * depth` before evaluation |
| `ADD` | Additive — `carrierVal + lfo2Val * depth` |

### LfoSpeedMode

UI hint only (`SLOW`, `MEDIUM`, `FAST`). Controls the slider range shown in `CellConfigPanel`
when the user adjusts `subdivision` in time mode. Does not affect evaluation.

---

## Waveform Math

**File**: `parameters/WaveformMath.kt`

### `calculateAdvancedLFO(phase, morph, hold, slope)` — used by `lfo`, `beatPhase`, `sampleAndHold`

```
1. Compute raw triangle: ramp up to 1 from [0, slope), ramp down to 0 from [slope, 1)
2. Stretch by hold: plateau the peak by compressing the transition into (1 - hold) of the cycle
3. Apply morph via log-cosh waveshaper:
       k = lerp(1.5, 15.0, morph)         (higher k = sharper, more sine-like)
       shaped = log(cosh(k * tri)) / (k * maxVal)
4. Output in [-1, 1]
```

`morph = 0` → sharp triangle. `morph = 1` → smooth near-sine via the log-cosh approximation.

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
  "audio_*"       → CVRegistry.get(id) — pushed from AudioEngine each frame
  "trigger_*"     → CVRegistry.get(id) — pushed from AudioEngine each frame
  "bpm"           → CVRegistry.get("bpm")
  "midi_cc_*"     → MidiEngine.getCcValue(channel, cc)
  else            → CVRegistry.get(id) — any registered custom source
```

---

## Serialization

`CvModulator` is `@Serializable` (kotlinx-serialization). The DTO conversion lives in
`models/PatchModels.kt`:

- `ModulatorDto.toDomain()` — load path; includes a one-line migration:
  `"gen1" / "gen2"` → `"lfo"` to silently upgrade patches saved before the rename.
- `CvModulator.toDto()` — save path; field names are stable (some use `@SerialName` aliases,
  e.g. `weight` for `amplitude`).

Do not rename `CvModulator` fields without adding a corresponding `@SerialName` or migration in
`toDomain()`.

---

## ParameterResolver

**File**: `parameters/ParameterResolver.kt`

`getAllParameterPaths(mixer)` returns every `(path, ModulatableParameter)` pair in the mixer,
using the same `"Deck A/Geometry/Lobes"` path format used by `ClipboardManager` and MIDI
mapping. Used for undo snapshots and MIDI learn target resolution.

The full parameter list covers (per deck A/B/C):
- Geometry: Lobes, Recipe, L1–L4, FreqOffset, HarmonicLock, 3DMode, SphereWrap, Mirror, Permute
- View: Zoom, RotateX/Y/Z, 3DPersp
- Color: Thickness, HueOffset, HueSweep, Depth, Gain
- Background: Style, Feedback, Hue, Sat, Val, Sweep, Speed, Zoom
- Feedback: Decay, Gain, Zoom, Rotate, HueShift, Blur, Chroma, Mode, Source
- Mixer: crossfade, masterAlpha, bloom, xfadeSpeed, queuePrev, queueNext

Dynamic visual sources (`DynamicVisualSource`, `Kifs`) expose their own parameter maps and are
resolved generically via the source's `parameters` map + `globalAlpha`.
