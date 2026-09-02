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
│   ├── BeatTrackerEngine.kt    — Real-time Beat Tracker (inspired by BTrack) with causal dynamic programming and continuous phase generator
│   ├── JackClient.kt           — JNAJack callback wrapper
│   ├── JavaSoundClient.kt      — Java Sound TargetDataLine fallback client
│   ├── BiquadFilter.kt         — Zero-alloc biquad IIR filter
│   ├── AmplitudeExtractor.kt   — RMS amplitude per band
│   ├── AudioInputDevice.kt     — Input device selection
│   ├── SystemAudioVolume.kt    — Master volume control
│   └── MidiJackWatchdog.kt     — MIDI hotplug monitoring
├── broadcast/
│   ├── BroadcastEngine.kt      — Live WebSocket relay client, throttled delta streaming, auto-reconnect
│   ├── BroadcastSettings.kt    — Broadcast configuration and persistence (lsd-settings.properties)
│   └── WebPresetSerializer.kt  — Converts desktop Deck/Mixer state to WebGL2 TV JSON schema
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
│   ├── AccumulationBuffer.kt   — HDR multi-pass motion blur accumulation
│   ├── AudioDecoder.kt         — Audio file decoding (WAV, MP3, FLAC, OGG, M4A)
│   ├── FFmpegProcessPipe.kt    — Non-blocking FFmpeg subprocess pipe with HW encoder prioritization
│   ├── OfflineRenderStudio.kt  — Deterministic offline video rendering with sample-accurate DSP
│   ├── PboReadbackPipeline.kt  — High-speed DMA GPU-to-CPU framebuffer readback
│   └── RealtimeRecorder.kt     — Live session video & audio capture and muxing
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
│   ├── MenuBar.kt              — Unified header bar, navigation menus, telemetry HUD & window controls
│   ├── WindowFrameController.kt— Client-Side Decorations (CSD), window dragging & perimeter edge resizing
│   ├── DeckPresetController.kt — Deck preset file lifecycle and dialog controller
│   ├── UIThemeStyler.kt        — ImGui dynamic styling, theme palettes, and font scaling
│   ├── SplitterManager.kt      — Multi-column layout dragging and divider render manager
│   ├── PresetGridPanel.kt      — Modulation matrix: param rows × CV columns
│   ├── CellConfigPanel.kt      — Edits one CvModulator with oscilloscope
│   ├── LibraryPanel.kt         — Library dock panel (presets, playlists, queue)
│   ├── NoteEditorModal.kt      — Zero-allocation modal editor for the 3-tier Note System
│   ├── SettingsPanel.kt        — App configuration & tabbed preferences modal
│   ├── AudioEnginePanel.kt     — Audio input, beat detection, and real-time oscilloscopes (Settings tab drawer)
│   ├── ColorTunerPanel.kt      — Interactive theme editor
│   ├── DeckControlPanel.kt     — Individual deck controls
│   ├── MixerMonitorPanel.kt    — 2x2 monitor matrix and crossfader
│   ├── PlaylistManager.kt      — Manages saved setlists
│   ├── VideoExportModal.kt     — Modal for offline video render studio & file chooser
│   ├── browser/                — Sidebar, Playlist Editor, and Queue Actions sub-panels
│   └── PresetGridState.kt      — Selection state & 30-level Undo Stack
├── tools/
│   └── SiteGenerator.kt        — Static site, documentation HTML, and offline ZIP builder for greenjon.com
└── utils/
    ├── TimeSource.kt           — Time virtualization provider for live and deterministic rendering
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
- **Deck PV preview** — third deck runs the full render pipeline but is excluded from `Mixer` output; used for preset authoring while A/B perform live
- **VisualSource abstraction** — Deck is source-agnostic; `Mandala`, `DynamicVisualSource`, `DynamicSpiral` all satisfy the interface
- **VisualSourceRegistry** — pluggable dynamic visual sources (GLSL shaders loaded from `library/sources/`)
- **Thread safety** — `@Volatile` primitive fields (`anchorBeats`, `anchorBpm`, `anchorTimeNs`) for zero-allocation audio thread beat clock sync, `CopyOnWriteArrayList` for modulators, `ConcurrentLinkedQueue` for MIDI CC events
- **Serializable presets** — `CvModulator` is `@Serializable`; clean, direct serialization without legacy aliases

## WebGL2 Core Renderer (Standalone Web Port)

Directory: `web/`

```
web/
├── index.html              — Entry point: TV bezel DOM shell, audio element & controls
├── tv.css                  — Retro TV bezel styling, power switch, rotary dial, LED badge
├── ui.js                   — UI state machine: power switch, rotary volume dial, fullscreen toggle
├── dsp.js                  — Web Audio DSP: live stream analysis, beat detection, GainNode volume control
├── renderer.js             — Standalone ES module: WebGL2 context, multi-pass pipeline, CRT post-processing
├── preset.json             — Hardcoded test preset schema
└── shaders/
    ├── blit.vert           — Fullscreen quad vertex shader (GLSL ES 3.0)
    ├── blit.frag           — Passthrough blit (GLSL ES 3.0)
    ├── mandala.vert        — Mandala ribbon vertex shader (GLSL ES 3.0)
    ├── mandala.frag        — Mandala ribbon fragment shader (GLSL ES 3.0)
    ├── dynamic_spiral.frag — Dynamic Spiral fullscreen fragment shader (GLSL ES 3.0)
    ├── feedback.frag       — Ping-pong feedback shader (GLSL ES 3.0)
    ├── mixer.frag          — Deck A + Deck B + BG composite (GLSL ES 3.0)
    └── crt_post.frag       — CRT post-processing, static snow, barrel distortion & warmup (GLSL ES 3.0)
```

The WebGL2 standalone player replicates the core desktop multi-pass pipeline and audio reactivity directly in the browser with zero dependencies:
- **Interactive Retro TV Shell (`tv.css`, `ui.js`)**: Encapsulates the visualizer in a retro CRT TV bezel. The physical power switch initiates user-gesture Web Audio initialization and triggers a realistic 1.5s CRT warmup animation (thin expanding raster line with phosphor glow). Rotary volume dial with mouse/touch drag controls audio gain with a squared perceptual curve (`setVolume`). Canvas double-click toggles borderless fullscreen projection mode.
- **Web Audio DSP Pipeline (`dsp.js`)**: Real-time analysis of `https://radio.spaz.org:8060/radio.ogg` Icecast stream via lowpass (bass < 180 Hz), bandpass (mid ~1 kHz), highpass (high > 5 kHz), and broadband analysers with peak-hold normalization and `GainNode` master volume control.
- **Beat & Onset Tracking**: Dual-envelope follower (fast vs baseline energy) with inter-onset interval (IOI) median filtering for real-time BPM estimation, beat phase (0..1), and beat sine oscillation.
- **Audio-Reactive Uniforms**: Per-frame uniform modulation dynamically blending baseline preset parameters with live CV signals (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`, `beatPhase`, `beatSine`, `trigger_onset`).
- **Ping-Pong Feedback FBOs**: Supports `RGBA16F` HDR render targets via `EXT_color_buffer_float` with fallback to `RGBA8`.
- **Render Passes**:
  1. `deckA.cleanFBO`: Generates Mandala ribbon source geometry (4096 vertices).
  2. `deckA.writeFBO`: Applies zoom/rotate/decay feedback blending with `deckA.readTex`.
  3. `deckB.cleanFBO`: Generates Dynamic Spiral with internal trail history (`src` sampler).
  4. `deckB.writeFBO`: Applies outer feedback transformation on Deck B.
  5. `deckBG`: Clears background layer.
  6. `masterFBO`: Blends Deck A + Deck B over BG with selectable blend modes (`mixer.frag`).
  7. `crt_post.frag` -> Screen: Final CRT post-processing with barrel glass distortion, chromatic aberration, scanlines, RGB phosphor shadow mask triad, corner vignette, animated static noise when powered off, and raster warmup sequence. Passes 1–4 are bypassed when powered off to minimize GPU load.

## Desktop-to-Web Sync & Drift Tracking Subsystem

- **Sync Manifest (`web/sync_manifest.json`)**: Authoritative mapping of desktop assets, GLSL 3.3 Core shaders (`src/main/resources/shaders/`, `library/sources/`), and algorithmic math files (`Icosahedron.kt`, `Evaluators.kt`, `WebPresetSerializer.kt`) to their WebGL2 / ES module equivalents.
- **Sync Engine (`scripts/sync_web.py`)**: Zero-dependency Python CLI tool providing:
  - `--check`: Compares actual web files vs transpiled desktop sources and SHA-256 hashes, producing a formatted status report. Returns exit code 1 if drift exists.
  - `--apply`: Automatically transpiles desktop `#version 330 core` shaders into WebGL2 `#version 300 es` (`precision highp float;`) and writes them directly to `web/shaders/`.
  - `--mark-synced <target>`: Updates recorded hashes for verified manual Kotlin-to-JS ports.
- **CI / Build Integration (`WebSyncTest.kt`, Gradle Tasks)**:
  - `./gradlew checkWebSync`: Gradle `Exec` task that validates zero drift across all tracked assets.
  - `./gradlew syncWeb`: Gradle `Exec` task that applies automated shader translation.
  - `WebSyncTest.kt`: JVM unit test executed on every `./gradlew test` run to guard against accidental drift.

## Build & Run
```bash
./gradlew run              # launch (JACK/PipeWire recommended for Linux, Java Sound fallback runs otherwise)
./gradlew compileKotlin    # type-check only, no run
./gradlew test             # run test suite (includes WebSyncTest)
./gradlew checkWebSync     # verify desktop ↔ web asset synchronization
./gradlew syncWeb          # auto-transpile desktop shaders into web/
./gradlew packageThumbDrive  # bundle fat JAR + JREs for all 5 platforms
```
Custom visual shaders are loaded from `library/sources/`.
For deeper notes see `docs/developer/` and `.agents/PROJECT.md`.

