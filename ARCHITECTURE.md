# Spirals Desktop — Architecture

Cross-platform VJ software (Linux x64/ARM64, macOS x64/ARM64, Windows x64). Real-time
audio-reactive parametric mandala visuals, three-deck mixer with preview deck, CV modulation
matrix. Built with Kotlin/JVM, OpenGL 3.3, ImGui, and JACK audio (Linux only).

## Video Pipeline

```
JACK Audio ──► AudioEngine ──► CVRegistry
                                    │  (every frame: updateAll)
                         ┌──────────┴──────────┐
                      Deck A                Deck B
                   (live output)         (live output)
                         │                    │
               ModulatableParams        ModulatableParams
               evaluated via CV         evaluated via CV
                         │                    │
                    cleanFBO             cleanFBO
                         │                    │
               feedback.frag           feedback.frag
               (ping-pong FBOs)        (ping-pong FBOs)
                         └──────────┬──────────┘
                                Mixer.kt
                              mixer.frag
                                  │
                             masterFBO ──► screen

Deck C  (preview only — same pipeline as A/B, excluded from Mixer output)
   └── used to build/audition patches while A and B are performing live
```

## File Map

```
src/main/kotlin/llm/slop/spirals/
├── Main.kt                     — GLFW window, render loop
├── audio/
│   ├── AudioEngine.kt          — JACK lifecycle, pushes CV values (Linux only)
│   ├── JackClient.kt           — JNAJack callback wrapper
│   ├── DSP.kt                  — Band-split FFT, RMS, onset detection
│   ├── BiquadFilter.kt         — Zero-alloc biquad IIR filter
│   └── AmplitudeExtractor.kt   — RMS amplitude per band
├── cv/
│   ├── CVRegistry.kt           — Singleton: all CV sources, beat sync, histories
│   ├── CVSource.kt             — Interface: id, value, update()
│   ├── BeatClock.kt            — Beat phase 0..1, JACK-synced
│   ├── Evaluators.kt           — Evaluators for lfo, beatPhase, sampleAndHold, audio
│   ├── GenCVSource.kt          — Registry placeholder for the lfo generator
│   └── CvHistoryBuffer.kt      — Ring buffer (200 samples)
├── midi/
│   ├── MidiEngine.kt           — MIDI connection and event polling
│   └── MidiMappingManager.kt   — Maps MIDI CC to UI/parameters
├── models/
│   └── PatchModels.kt          — Data models + DTOs for patch serialization
├── parameters/
│   └── Parameter.kt            — ModulatableParameter, CvModulator,
│                                  ModulationOperator, Waveform, LfoSpeedMode
├── patches/
│   ├── PatchManager.kt         — Save/load patches, state management
│   ├── PlayQueueManager.kt     — Manages deck playback queues
│   ├── PlaylistManager.kt      — Manages saved setlists
│   └── ClipboardManager.kt     — Copy/paste for patch elements
├── rendering/
│   ├── Mandala.kt              — Mandala4Arm (recipe + full field docs),
│   │                             Mandala (VisualSource with all params)
│   ├── MandalaLibrary.kt       — ~300 curated MandalaRatio entries
│   ├── Deck.kt                 — VisualSource + ping-pong FBOs + FB params
│   │                             (Deck A & B → live output; Deck C → preview only)
│   ├── Mixer.kt                — Blends Deck A+B → masterFBO (Deck C excluded)
│   ├── Renderer.kt             — Per-frame: source → feedback → mix → blit
│   ├── VisualSource.kt         — Interface (Mandala, DynamicVisualSource)
│   ├── VisualSourceRegistry.kt — Pluggable dynamic visual sources
│   ├── DynamicVisualSource.kt  — Wraps loaded GLSL shaders
│   ├── Kifs.kt                 — Kaleidoscopic IFS visual source
│   ├── Shader.kt               — GLSL shader compilation/management
│   ├── Geometry.kt             — Vertex buffers, basic shapes
│   └── FBO.kt                  — OpenGL framebuffer wrapper
├── ui/                         — 34 files; see docs/developer/ui.md
│   ├── UIManager.kt            — Top-level layout orchestrator
│   ├── PatchGridPanel.kt       — Modulation matrix: param rows × CV columns
│   ├── CellConfigPanel.kt      — Edits one CvModulator with oscilloscope
│   └── PatchGridState.kt       — Selection state (cell, param, modulator)
└── utils/
    └── TimeUtils.kt            — Timing utilities
```

## CV Sources (registered IDs)

| ID | Type | Description |
|----|------|-------------|
| `bpm` | Audio | Detected tempo |
| `audio_amp` | Audio | Overall RMS amplitude |
| `audio_bass` | Audio | Low-frequency RMS |
| `audio_mid` | Audio | Mid-frequency RMS |
| `audio_high` | Audio | High-frequency RMS |
| `trigger_onset` | Audio | Transient/onset pulse |
| `trigger_accent` | Audio | Strong beat accent |
| `lfo` | Generator | Time-based or beat-based waveform; evaluated inline per `CvModulator` |
| `BeatSine` | Generator | Sine wave locked to beat phase |

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

Patch Grid rows: Mixer → Deck A [Geometry, Color, Feedback] → Deck B [same] → Deck C [same]  
Patch Grid columns: LFO | AUDIO | TRIG

## Design Principles
- **Zero-allocation audio thread** — pre-allocated buffers, no object creation in JACK callback (Linux only)
- **Deck C preview** — third deck runs the full render pipeline but is excluded from `Mixer` output; used for patch authoring while A/B perform live
- **VisualSource abstraction** — Deck is source-agnostic; `Mandala`, `DynamicVisualSource`, `Kifs` all satisfy the interface
- **VisualSourceRegistry** — pluggable dynamic visual sources (GLSL shaders loaded from `presets/sources/`)
- **Thread safety** — `AtomicReference<BeatAnchor>` for beat clock, `CopyOnWriteArrayList` for modulators, `ConcurrentLinkedQueue` for MIDI CC events
- **Serializable patches** — `CvModulator` is `@Serializable`; load-time migration remaps legacy source IDs

## Build & Run
```bash
./gradlew run              # launch (JACK/PipeWire should be running for audio CVs on Linux)
./gradlew compileKotlin    # type-check only, no run
./gradlew packageThumbDrive  # bundle fat JAR + JREs for all 5 platforms
```
Custom visual shaders are loaded from `presets/sources/`.
For deeper notes see `docs/developer/` and `.agents/PROJECT.md`.
