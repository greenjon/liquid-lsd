# Liquid LSD — Architecture

Cross-platform VJ software (Linux x64/ARM64, macOS x64/ARM64, Windows x64). Real-time
audio-reactive parametric mandala visuals, four-deck mixer with background and preview decks, CV modulation
matrix. Built with Kotlin/JVM, OpenGL 3.3, ImGui, and JACK audio (with fallback) / Java Sound (cross-platform).

## Video Pipeline

```
JACK / Java Sound ──► AudioEngine ──► CVRegistry
                                    │  (every frame: updateAll)
                 ┌──────────────────┼──────────────────┐
              Deck BG             Deck A             Deck B
          (background layer)   (live output)      (live output)
                 │                  │                  │
         ModulatableParams  ModulatableParams  ModulatableParams
                 │                  │                  │
              cleanFBO           cleanFBO           cleanFBO
                 │                  │                  │
           feedback.frag      feedback.frag      feedback.frag
                 └──────────────────┼──────────────────┘
                                 Mixer.kt
                                mixer.frag
                          (Composite: (A+B) over BG)
                                    │
                               masterFBO ──► screen

Deck PV  (preview only — same pipeline as A/B/BG, excluded from Mixer output)
   └── used to build/audition presets while A, B, and BG are performing live
```

## File Map

```
src/main/kotlin/llm/slop/liquidlsd/
├── Main.kt                     — GLFW window, render loop
├── SessionContext.kt           — Application state & context
├── audio/
│   ├── AudioEngine.kt          — Audio lifecycle, coordinates JACK & Java Sound, pushes CV values
│   ├── JackClient.kt           — JNAJack callback wrapper
│   ├── JavaSoundClient.kt      — Java Sound TargetDataLine fallback client
│   ├── BiquadFilter.kt         — Zero-alloc biquad IIR filter
│   ├── AmplitudeExtractor.kt   — RMS amplitude per band
│   ├── AudioInputDevice.kt     — Input device selection
│   ├── SystemAudioVolume.kt    — Master volume control
│   └── MidiJackWatchdog.kt     — MIDI hotplug monitoring
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
│   ├── PresetModels.kt         — Data models + DTOs for preset serialization
│   └── ClipboardManager.kt     — Copy/paste for preset elements
├── notes/
│   └── NotesManager.kt         — 3-tier notes persistence manager (global source notes, preset notes, param notes)
├── parameters/
│   ├── ModulatableParameter.kt — Parameter state and evaluation
│   ├── CvModulator.kt          — CV modulation routing
│   ├── Enums.kt                — Enums
│   ├── ParameterOwner.kt       — Parameter ownership interface
│   ├── ParameterResolver.kt    — Parameter lookup
│   └── WaveformMath.kt         — Math utils
├── presets/
│   ├── PresetManager.kt        — Save/load presets, state management
│   ├── PlayQueueManager.kt     — Manages A/B playback queues
│   ├── BgQueueManager.kt       — Manages background deck queue
│   ├── PlaylistParser.kt       — Parses playlist files
│   ├── SessionState.kt         — Session state management
│   └── PresetIOStatus.kt       — IO status for UI feedback
├── export/                     — Video & audio render export
│   ├── OfflineRenderStudio.kt  — Single-threaded video rendering
│   └── PboReadbackPipeline.kt  — Fast DMA pixel readback
├── rendering/
│   ├── Mandala.kt              — Mandala4Arm (recipe + field docs), Mandala (VisualSource)
│   ├── MandalaLibrary.kt       — ~300 curated MandalaRatio entries
│   ├── Deck.kt                 — VisualSource + ping-pong FBOs + FB params (Deck A, B & BG -> live; Deck PV -> preview)
│   ├── Mixer.kt                — Blends Deck A+B over BG -> masterFBO (Deck PV excluded)
│   ├── Renderer.kt             — Per-frame: source -> feedback -> mix -> blit
│   ├── VisualSource.kt         — Interface (Mandala, DynamicVisualSource)
│   ├── VisualSourceRegistry.kt — Pluggable dynamic visual sources
│   ├── DynamicVisualSource.kt  — Wraps loaded GLSL shaders
│   ├── DynamicSpiral.kt        — Specialized particle/spiral visual source
│   ├── HyperMesh.kt            — Real-time 4D Polychoron (600-cell & 120-cell) visual source with Hopf fibration
│   ├── Icosahedron.kt          — 32-Stellation icosahedral manifold visual source
│   ├── SourceDocRegistry.kt    — Built-in engine & parameter documentation registry
│   ├── Shader.kt               — GLSL shader compilation/management
│   ├── Geometry.kt             — Vertex buffers, basic shapes
│   ├── FBO.kt                  — OpenGL framebuffer wrapper
│   ├── GLDebug.kt              — OpenGL debug context callbacks
│   ├── GLResourceTracker.kt    — OpenGL leak tracking
│   ├── TextureStreamer.kt      — Async texture loading
│   └── ViewportHelper.kt       — Output scaling modes
├── ui/                         — ImGui panels and UI orchestration; see docs/developer/ui.md
│   ├── UIManager.kt            — Top-level layout orchestrator & GLFW/ImGui render loop
│   ├── DeckPresetController.kt — Deck preset file lifecycle and dialog controller
│   ├── UIThemeStyler.kt        — ImGui dynamic styling, theme palettes, and font scaling
│   ├── SplitterManager.kt      — Multi-column layout dragging and divider render manager
│   ├── PresetGridPanel.kt      — Modulation matrix: param rows × CV columns
│   ├── CellConfigPanel.kt      — Edits one CvModulator with oscilloscope
│   ├── LibraryPanel.kt         — Library dock panel (presets, playlists, queue)
│   ├── NoteEditorModal.kt      — Zero-allocation modal editor for the 3-tier Note System
│   ├── SettingsPanel.kt        — App configuration
│   ├── AudioEnginePanel.kt     — Audio input and beat detection settings
│   ├── ColorTunerPanel.kt      — Interactive theme editor
│   ├── DeckControlPanel.kt     — Individual deck controls
│   ├── MixerMonitorPanel.kt    — 2x2 monitor matrix and crossfader
│   ├── PlaylistManager.kt      — Manages saved setlists
│   ├── browser/                — Sidebar, Playlist Editor, and Queue Actions sub-panels
│   └── PresetGridState.kt      — Selection state & 30-level Undo Stack
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
    amount = cv * depth
    result = result + amount          (ADD)
           | result * (1 + amount)    (MUL)
value = result.coerceIn(0f, 1f)
```

## UI Layout

```
┌──────────────────┬────────────────┬────────────────┐
│                  │                │                │
│  Preset Grid     │  Cell Config   │ Mixer/Monitor  │
│  (40% width)     │  (30% width)   │  (30% width)   │
│                  │                │                │
├──────────────────┴────────────────┴────────────────┤
│                 Library Panel                      │
│        (Presets | Playlists | Q | BGQ)             │
└────────────────────────────────────────────────────┘
```

Preset Grid rows: Mixer → Deck A [Geometry, Color, Feedback] → Deck B [same] → Deck BG [same] → Deck PV [same]  
Preset Grid columns: LFO | AUDIO | TRIG

## Design Principles
- **Zero-allocation audio loops** — pre-allocated buffers, no object creation in JACK callback or Java Sound conversion loop
- **Deck C preview** — third deck runs the full render pipeline but is excluded from `Mixer` output; used for preset authoring while A/B perform live
- **VisualSource abstraction** — Deck is source-agnostic; `Mandala`, `DynamicVisualSource`, `DynamicSpiral` all satisfy the interface
- **VisualSourceRegistry** — pluggable dynamic visual sources (GLSL shaders loaded from `library/sources/`)
- **Thread safety** — `@Volatile` primitive fields (`anchorBeats`, `anchorBpm`, `anchorTimeNs`) for zero-allocation audio thread beat clock sync, `CopyOnWriteArrayList` for modulators, `ConcurrentLinkedQueue` for MIDI CC events
- **Serializable presets** — `CvModulator` is `@Serializable`; clean, direct serialization without legacy aliases

## Build & Run
```bash
./gradlew run              # launch (JACK/PipeWire recommended for Linux, Java Sound fallback runs otherwise)
./gradlew compileKotlin    # type-check only, no run
./gradlew packageThumbDrive  # bundle fat JAR + JREs for all 5 platforms
```
Custom visual shaders are loaded from `library/sources/`.
For deeper notes see `docs/developer/` and `.agents/PROJECT.md`.
