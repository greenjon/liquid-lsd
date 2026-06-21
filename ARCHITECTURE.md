# Spirals Desktop — Architecture

Linux desktop VJ software. Real-time audio-reactive parametric mandala visuals, dual-deck mixer,
CV modulation matrix. Built with Kotlin/JVM, OpenGL 3.3, ImGui, and JACK audio.

## Video Pipeline

```
JACK Audio ──► AudioEngine ──► CVRegistry
                                    │  (every frame: updateAll)
                         ┌──────────┴──────────┐
                      Deck A                Deck B
                   (Mandala source)     (Mandala source)
                         │                    │
               ModulatableParams          ModulatableParams
               evaluated via CV          evaluated via CV
                         │                    │
                    cleanFBO             cleanFBO
                         │                    │
                   feedback.frag         feedback.frag
                   (ping-pong FBOs)      (ping-pong FBOs)
                         └──────────┬──────────┘
                                Mixer.kt
                              mixer.frag
                                  │
                             masterFBO ──► screen
```

## File Map

```
src/main/kotlin/llm/slop/spirals/
├── Main.kt                     — GLFW window, render loop
├── audio/
│   ├── AudioEngine.kt          — JACK lifecycle, pushes CV values
│   ├── JackClient.kt           — JNAJack callback wrapper
│   ├── DSP.kt                  — Band-split FFT, RMS, onset detection
│   ├── BiquadFilter.kt         — Zero-alloc biquad IIR filter
│   └── AmplitudeExtractor.kt   — RMS amplitude per band
├── cv/
│   ├── CVRegistry.kt           — Singleton: all CV sources, beat sync, histories
│   ├── CVSource.kt             — Interface: id, value, update()
│   ├── MutableCVSource.kt      — Writable CV (audio signals)
│   ├── BeatClock.kt            — Beat phase 0..1, JACK-synced
│   ├── LFO.kt                  — Time-based oscillator
│   ├── SampleAndHold.kt        — Random step with glide
│   └── CvHistoryBuffer.kt      — Ring buffer (200 samples)
├── parameters/
│   └── Parameter.kt            — ModulatableParameter, CvModulator,
│                                  ModulationOperator, Waveform, LfoSpeedMode
├── rendering/
│   ├── Mandala.kt              — Mandala4Arm (recipe + full field docs),
│   │                             Mandala (VisualSource with all params)
│   ├── MandalaLibrary.kt       — ~300 curated MandalaRatio entries
│   ├── RandomizationModels.kt  — RandomSet, ArmConstraints, etc.
│   ├── RecipeRandomizer.kt     — Generates mandala config from RandomSet
│   ├── Deck.kt                 — VisualSource + ping-pong FBOs + FB params
│   ├── Mixer.kt                — Blends Deck A+B → masterFBO
│   ├── Renderer.kt             — Per-frame: source → feedback → mix → blit
│   ├── VisualSource.kt         — Interface (Mandala, future: video/3D)
│   └── FBO.kt                  — OpenGL framebuffer wrapper
├── ui/
│   ├── UIManager.kt            — Layout: PatchGrid (L 40%) | CellConfig (M 30%) | Mixer (R 30%)
│   ├── PatchGridPanel.kt       — Modulation matrix: param rows × CV columns
│   ├── CellConfigPanel.kt      — Edits one CvModulator with lazy real-time oscilloscope
│   └── PatchGridState.kt       — Selection state (cell, param, modulator)
└── ANDROID-REFERENCE/          — REMOVED (extracted into rendering/)
```

## CV Sources (registered IDs)

| ID | Type | Description |
|----|------|-------------|
| `amp` | Audio | Overall RMS amplitude |
| `bass` | Audio | Low-frequency RMS |
| `mid` | Audio | Mid-frequency RMS |
| `high` | Audio | High-frequency RMS |
| `bassFlux` | Audio | Bass spectral flux |
| `onset` | Audio | Transient/onset pulse |
| `accent` | Audio | Strong beat accent |
| `bpm` | Audio | Detected tempo |
| `beatPhase` | Generator | Beat phase 0→1 per beat |
| `lfo` | Generator | Time-based oscillator |
| `sampleAndHold` | Generator | Random step with glide |

## Modulation Math

`ModulatableParameter.evaluate()` per frame:
```
result = baseValue
for each active CvModulator:
    cv = CvModulator.evaluateValue()  (runs beatPhase/lfo/snh calculation locally; audio from CVRegistry.get())
    amount = cv * weight
    result = result + amount          (ADD)
           | result * (1 + amount)    (MUL)
value = result.coerceIn(0f, 1f)
```

## UI Layout

```
┌──────────────────┬────────────────┬────────────────┐
│                  │                │                │
│  Patch Grid      │  Cell Config   │ Mixer/Monitor  │
│  (40% width)     │  (30% width)   │  (30% width)   │
│                  │                │                │
└──────────────────┴────────────────┴────────────────┘
```

Patch Grid rows: Mixer → Deck A [Geometry, Color, Feedback] → Deck B [same]  
Patch Grid columns: AMP BASS MID HIGH FLUX ONSET ACCENT BEAT LFO RAND

## Design Principles
- **Zero-allocation audio thread** — pre-allocated buffers, no object creation in JACK callback
- **VisualSource abstraction** — Deck is source-agnostic; video/3D sources slot in later
- **Thread safety** — `@Volatile` beat anchor, `CopyOnWriteArrayList` for modulators
- **Serializable patches** — `CvModulator` is `@Serializable`; save/load is Phase 5

## Build & Run
```bash
./gradlew run          # development
./gradlew compileKotlin  # check for errors only
```
**JACK must be running** before launch (visuals work without it; audio CVs stay at 0).
