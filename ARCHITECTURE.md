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
            [FX Slot 1]        [FX Slot 1]        [FX Slot 1]
                 │                  │                  │
            [FX Slot 2]        [FX Slot 2]        [FX Slot 2]
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

## Zero-Allocation Render Loop Guarantees

To maintain stable 60–120 FPS playback without ZGC pause interruptions or frame drops, the hot render path (executed strictly on OS Thread 0) adheres to strict zero-allocation rules:
- **ISF & Visual Generators (`ISFVisualSource.kt`, `DynamicVisualSource.kt`, `ISFFilter.kt`)**: Uniform names, derived input bindings (`color`, `point2D`, `float`, `bool`), and multipass targets are pre-bound at initialization time. Uniform setters, `DATE` epoch calculations, and parameter updates loop directly over pre-allocated arrays, avoiding `LocalDateTime` instantiation, map lookups, string formatting, and collection iterator allocations.
- **Dynamic Visual Source Thread 0 Discipline (`VisualSourceRegistry.kt`)**: Visual source scanning delegates OpenGL shader compilation tasks to `pendingGlTasks` queue processed exclusively on OS Thread 0 (`processPendingGlTasks()`) at the start of each frame, completely isolating background discovery threads from GPU context operations. Startup initialization (`loadAll(async = false)`) compiles shaders synchronously on Thread 0 before the render loop begins.
- **MIDI CC Evaluation (`MidiMappingManager.kt`, `ParameterResolver.kt`)**: `MidiMappingManager` resolves parameter paths once when mappings change, storing bindings in an unboxed, flat `ResolvedMidiBinding` array. The per-frame `update(mixer)` loop iterates via primitive indexed bounds (`0 until size`) with zero runtime heap allocations, string formatting, or map lookups. `ParameterResolver` caches resolved parameter paths in a `ConcurrentHashMap` to eliminate recursive tree traversals.
- **CV Source Evaluation & MIDI Routing (`CVRegistry.kt`, `Evaluators.kt`)**: Non-audio CV sources are maintained in a pre-allocated `activeNonAudioSources` array traversed via indexed loops during `updateAll()`. MIDI CC lookups bit-pack channel and CC into 64-bit keys (`(channel.toLong() shl 32) or cc.toLong()`) avoiding string splitting, and `AudioFollowerTracker` guards state map retrieval before calling `computeIfAbsent` to eliminate capturing lambda allocations on the audio path.
- **Mixer & Modulators (`Mixer.kt`, `ModulatableParameter.kt`)**: Modulator activity checks on `CopyOnWriteArrayList` use index-based O(1) traversal (`hasActiveModulator()`) instead of `.any { }` to prevent per-frame `COWIterator` allocations.
- **Registries (`ISFFilterRegistry.kt`, `ISFTransitionRegistry.kt`, `ISFLibraryRegistry.kt`)**: `availableFilters`, `availableTransitions`, and `allAssets` are backed by `@Volatile` immutable sorted snapshots rebuilt once per scan or modification, eliminating per-frame list re-allocation and re-sorting during picker rendering. Mutual exclusion prevents transition shaders from polluting the filter registry.

## File Map

```
src/main/kotlin/llm/slop/liquidlsd/
├── Main.kt                     — GLFW window, multi-resolution app icon loading (16x16 to 256x256), render loop
├── SessionContext.kt           — Application state & context
├── audio/
│   ├── AudioEngine.kt          — Audio lifecycle, coordinates JACK & Java Sound, pushes CV values
│   ├── AudioChannelRouting.kt  — Stereo channel routing enum (Mix L+R, Left Only, Right Only)
│   ├── BeatTrackerEngine.kt    — Real-time Beat Tracker (inspired by Adam Stark's beat tracking research) with causal dynamic programming and continuous phase generator
│   ├── ClockSource.kt          — Timing source enum (AUDIO_TRACKER, ABLETON_LINK, MANUAL_TAP)
│   ├── JackClient.kt           — JNAJack callback wrapper
│   ├── JavaSoundClient.kt      — Java Sound TargetDataLine fallback client
│   ├── BiquadFilter.kt         — Zero-alloc biquad IIR filter
│   ├── AmplitudeExtractor.kt   — RMS amplitude per band
│   ├── AudioInputDevice.kt     — Input device selection
│   ├── SystemAudioVolume.kt    — Master volume control
│   ├── MidiJackWatchdog.kt     — MIDI hotplug monitoring
│   └── TapTempoController.kt   — VJ tap tempo cadence tracking, interval averaging, 2.0s timeout reset, and phase alignment
├── link/                       — Ableton Link network interop & clock synchronization
│   ├── LinkSyncManager.kt      — Session status accessor and audio tempo damping orchestrator
│   ├── BeatTrackToLinkDamping.kt — Signal conditioner (Median + EMA, 0.5 BPM / 4-beat hysteresis, >=0.5 beat phase error)
│   ├── AbletonLinkEngine.kt    — Manager for Link network session state & tempo sync
│   ├── LinkBackend.kt          — Driver interface (Native JNI, Carabiner TCP, No-Op)
│   ├── NativeJniLinkBackend.kt — C++ JNI bridge (liblink_jni) embedding ableton::Link
│   ├── CarabinerTcpLinkBackend.kt — TCP socket client for local Carabiner daemon
│   └── NoOpLinkBackend.kt      — Disconnected fallback backend
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
├── input/
│   ├── TouchConsoleController.kt — 4-zone SCS.3m virtual console, LIFO stacks, zone affinity
│   ├── TouchConsoleEvent.kt    — Low-latency native touch event model
│   ├── TouchStripBackend.kt    — Hardware backend interface & states
│   ├── LinuxEvdevTouchBackend.kt — Linux evdev JNA reader, EVIOCGRAB, EVIOCGABS, MT Protocol B cache
│   ├── MacCocoaTouchBackend.kt — macOS Cocoa NSTouch indirect touch events
│   └── NoOpTouchBackend.kt     — Safe fallback
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
│   ├── PresetDependencyAnalyzer.kt — Dependency analysis, disabled/offline feature inspection, zero-alloc memoization
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
│   ├── Mandala.kt              — Mandala4Arm (recipe + field docs), Mandala (VisualSource), analytical arm normalization
│   ├── MandalaLibrary.kt       — ~300 curated MandalaRatio entries
│   ├── VisualEffect.kt         — Interface for post-processing effects
│   ├── isf/                    — ISF specification parser, data models, ISFFilter, ISFVisualSource, ISFTransitionRegistry, ISFDirectoryManager, ISFScanner, ISFLibraryRegistry & ISFFileWatcher
│   ├── Deck.kt                 — VisualSource + rawSource2DFBO + rawSourceFBO + cleanFBO + fxFBO1 + ping-pong FBOs + 2D/3D View params + FB params
│   ├── Mixer.kt                — Blends Deck A+B over BG -> masterFBO with channel level multipliers & ISF transition engine (blendFBO)
│   ├── Renderer.kt             — Per-frame: polymorphic source drawTopology() -> 2D view transform / 3D Tri-Planar & Hex-Planar projection / Tetrahedral Kaleidoscope (2D sources only) -> feedback -> ISF transition pass / non-ISF mix -> composite -> blit
│   ├── VisualSource.kt         — Interface (Mandala, DynamicVisualSource, 2D/3D classification via is3D)
│   ├── VisualSourceRegistry.kt — Pluggable dynamic visual sources with automatic 3D detection
│   ├── DynamicVisualSource.kt  — Wraps loaded GLSL shaders, handles 2D/3D source tagging and uniform binding
│   ├── DynamicSpiral.kt        — Specialized particle/spiral visual source
│   ├── ExternalVideoSource.kt  — Live video ingest visual source driven by Spout/Syphon/PipeWire video streams
│   ├── ExternalVideoDiscovery.kt — Background discovery service polling for active Spout, Syphon, and PipeWire video streams
│   ├── HyperMesh.kt            — Real-time 4D Polychoron (600-cell & 120-cell) visual source with Hopf fibration
│   ├── Icosahedron.kt          — 32-Stellation icosahedral manifold visual source
│   ├── SourceDocRegistry.kt    — Built-in engine & parameter documentation registry
│   ├── Shader.kt               — GLSL shader compilation/management
│   ├── Geometry.kt             — Vertex buffers, basic shapes
│   ├── FBO.kt                  — OpenGL framebuffer wrapper
│   ├── GLDebug.kt              — OpenGL debug context callbacks
│   ├── GLResourceTracker.kt    — OpenGL leak tracking
│   ├── TextureStreamer.kt      — Multi-endpoint live video sharing (Spout2 on Windows, Syphon Obj-C Runtime on macOS, PipeWire 0.3 on Linux)
│   ├── TextureReceiver.kt      — Live video stream ingestion client bindings (Spout2 on Windows, Syphon Client on macOS, PipeWire 0.3 on Linux)
│   ├── VideoOutputSettings.kt  — Video output endpoints, resolution overrides, scaling modes, and stream configurations
│   └── ViewportHelper.kt       — Output scaling modes
├── ui/                         — ImGui panels and UI orchestration; see docs/developer/ui.md
│   ├── UIManager.kt            — Top-level layout orchestrator & GLFW/ImGui render loop
│   ├── MenuBar.kt              — Unified header bar, navigation menus, telemetry HUD & window controls
│   ├── ShaderPickerPopup.kt    — High-performance category-based shader & source selector
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
│   ├── DeckControlPanel.kt     — Individual deck preview monitor, toolbar, inside-clustered badge/die overlays, and vertical channel level fader
│   ├── MixerMonitorPanel.kt    — 2x2 monitor matrix, master output monitor with [M] badge, [🎲 ALL], master level fader, and streamlined crossfader
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
| `audio_amp` | Audio | Overall full-mix RMS amplitude |
| `audio_bass` | Audio | Low-frequency RMS amplitude |
| `audio_mid` | Audio | Mid-frequency RMS amplitude |
| `audio_high` | Audio | High-frequency RMS amplitude |
| `audio_flux_amp` | Audio | Overall full-mix spectral flux (transient onset) |
| `audio_flux_bass` | Audio | Low-frequency spectral flux (bass/kick transient) |
| `audio_flux_mid` | Audio | Mid-frequency spectral flux (snare/vocal attack) |
| `audio_flux_high` | Audio | High-frequency spectral flux (hi-hat/treble strike) |
| `lfo` | Generator | Time-based or beat-based waveform; evaluated inline per `CvModulator` |
| `BeatSine` | Generator | Sine wave locked to beat phase |
| `seq` | Sequencer | Step Sequencer pattern modulation |

## Modulation Math

`ModulatableParameter.evaluate()` per frame:
```
result = baseValue
for each active CvModulator:
    cv = CvModulator.evaluateValue()  (runs beatPhase/lfo/snh calculation locally; audio from CVRegistry.get())
    
    // Depth/Offset math (LFO 1 & Audio use Min/Max in UI, but convert to this internal form):
    // Depth = (Max - Min) / 2
    // Offset = (Max + Min) / 2
    
    amount = cv * depth + dcOffset
    result = result + amount          (ADD)
           | result * (1 + amount)    (MUL)
           | result * (1.0f - depth + amount) (SCALE)
value = result.coerceIn(minClamp, maxClamp)
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
Preset Grid columns: VAL | MIDI | LFO | SEQ | AUD  
*(Note: Engine subsystems `midiEnabled`, `sequencerEnabled`, and `audioEngineEnabled` serve as the single source of truth for Preset Grid and Cell Config column visibility; the header kebab menu `⋮` allows immediate toggling of these engines and columns).*

## Application Icons & Window Branding
The project includes an official application icon featuring an audio-reactive psychedelic eye with chromatic aberration and a falling liquid drop.
- **Desktop (GLFW)**: `setWindowAppIcons(window)` in `Main.kt` loads multi-resolution PNGs (`16x16`, `32x32`, `48x48`, `64x64`, `128x128`, `256x256`) from `src/main/resources/icons/` into LWJGL `GLFWImage.Buffer` using `stbi_load_from_memory`. Applied to both primary desktop and secondary output preview windows.
- **Linux Compositor & Desktop Entry**: Windows configure `GLFW_WAYLAND_APP_ID`, `GLFW_X11_CLASS_NAME`, and `GLFW_X11_INSTANCE_NAME` as `liquid-lsd`. `ensureLinuxDesktopEntry()` registers local FreeDesktop launcher and hicolor icons in user data paths, supported by `scripts/install_desktop.sh` and distribution zip packages.
- **Web Player**: `web/favicon.ico`, `web/favicon.png` (32×32), `web/apple-touch-icon.png` (180×180), `web/icon-192.png`, and `web/icon-512.png` wired into `web/index.html`.
- **Website & Documentation**: Bundled under `website/assets/images/` and generated into `greenjon/assets/images/`.

## Design Principles
- **Zero-allocation audio loops** — pre-allocated buffers, no object creation in JACK callback or Java Sound conversion loop
- **Deck PV preview** — third deck runs the full render pipeline but is excluded from `Mixer` output; used for preset authoring while A/B perform live
- **VisualSource abstraction** — Deck is source-agnostic; `Mandala`, `DynamicVisualSource`, `DynamicSpiral` all satisfy the interface
- **VisualSourceRegistry** — pluggable dynamic visual sources (GLSL shaders loaded from `library/sources/`)
- **Thread safety** — `@Volatile` primitive fields (`anchorBeats`, `anchorBpm`, `anchorTimeNs`) for zero-allocation audio thread beat clock sync, `CopyOnWriteArrayList` for modulators, `ConcurrentLinkedQueue` for MIDI CC events
- **Blank startup state** — Decks default to empty (`isEmpty = true`); on initial application launch without a prior session file, all four decks start with clean blank screens and Launchpad controls rather than pre-populated visual sources
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
  1. `deckA.cleanFBO`: Generates Mandala ribbon source geometry (4096 vertices) with sum-of-lengths analytical size normalization.
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

## Touchpad Performance Console (SCS.3m Virtual Console)

Transforms the laptop trackpad into an absolute 4-zone performance surface when `CapsLock` is engaged:
- **Spatial Zoning**:
  - **Bottom 28%** ($Y \le 0.28$): Horizontal Crossfader (Deck A $\leftrightarrow$ Deck B, mapped to `mixer.crossfade` $[-1.0, 1.0]$).
  - **Deadzone Buffer** ($Y \in [0.28, 0.45]$): 17% buffer height rejecting new touch-downs while preserving active drag continuity under Zone Affinity.
  - **Top 55%** ($Y \ge 0.45$): Three vertical Level/Alpha faders ($0.0 \dots 1.0$, direct jump):
    - Left 33%: Deck A Level (`mixer.levelA`)
    - Center 33%: Deck BG Level (`mixer.levelBG`)
    - Right 33%: Deck B Level (`mixer.levelB`)
- **Multi-Touch Engine**:
  - 4 independent LIFO touch stacks. Touching with a second finger instantly jumps to that position; releasing snaps back to the underlying anchor finger.
  - Bezel clamping ($Y \le 0.48 \to 0.0$, $Y \ge 0.94 \to 1.0$; $X \le 0.05 \to -1.0$, $X \ge 0.95 \to 1.0$, center detent $\pm 0.02 \to 0.0$).
  - Sticky hold level indicators on UI HUD.
- **Native Backends**:
  - **Linux**: Direct evdev reader via JNA `libc`, `EVIOCGRAB` (`0x40044590`) cursor grab, `EVIOCGABS` hardware axis query, and MT Protocol B slot cache.
  - **macOS**: Cocoa `NSTouch` indirect touch events.
  - **Thread-Safety**: Low-latency lock-free event queue drained strictly on Thread 0 once per frame.

## Version & Update Engine (`update`)

- **Authoritative Version Resolution (`AppVersion.kt`)**: Dynamically resolves the runtime version from JAR manifest attributes (`Implementation-Version`), packaged classpath `/version.txt`, or fallback default.
- **Semantic Versioning (`SemVer.kt`)**: Zero-dependency parser and comparator implementing SemVer 2.0.0 precedence rules (supporting numeric major/minor/patch, release vs pre-release precedence, dot-separated pre-release tokens, and snapshot identifiers).
- **Background Release Checker (`UpdateChecker.kt`)**: Asynchronous, daemon-threaded update engine checking GitHub releases with 5-second timeouts. Features dual-mode detection (GitHub REST API with fallback to web redirect inspection) and fail-safe error handling to guarantee audio processing and rendering loops remain unblocked.
- **Interactive Modals (`UpdatePromptModal.kt`, `AboutModal.kt`)**:
  - `UpdatePromptModal`: Prompts when newer releases are discovered, offering immediate download via system browser, session reminder, or permanent per-version skip.
  - `AboutModal`: Accessible from the Help menu; displays current version, manual update checker, and repository links.

## Build & Run
```bash
./gradlew run              # launch (JACK/PipeWire recommended for Linux, Java Sound fallback runs otherwise)
./gradlew compileKotlin    # type-check only, no run
./gradlew test             # run test suite (includes WebSyncTest)
./gradlew checkWebSync     # verify desktop ↔ web asset synchronization
./gradlew syncWeb          # auto-transpile desktop shaders into web/
./gradlew packageThumbDrive  # bundle fat JAR + JREs + library for all 4 platforms
./gradlew packageZips        # assemble platform distribution ZIPs (Windows x64, Linux x64, macOS arm64/x64)
./gradlew run --args="--smoke-test"  # run headless binary self-diagnostic smoke test
./gradlew run --args="--version"     # print version, architecture, and JVM runtime details
```
Custom visual shaders and presets are loaded from `library/sources/` and `library/presets/`. Distribution ZIPs package the complete `library/` folder with executable (`755`) permissions on launcher scripts (`.sh`, `.command`) and bundled JRE binaries. Launcher scripts forward all CLI arguments (`"$@"` / `%*`) directly to the bundled JVM. 

### Multi-Platform Verification Matrix (GitHub Actions)
All four target platform distributions are pre-tested natively on GitHub-hosted runners (`ubuntu-latest`, `macos-latest`, `macos-15-intel`, `windows-latest`) via `.github/workflows/smoke-test.yml` (on PRs) and `.github/workflows/release.yml` (prior to release publishing). The release workflow employs selective gating: only distributions that pass automated smoke testing are published as release assets.

For deeper notes see `docs/developer/`, `DECISIONS.md`, and `.agents/PROJECT.md`.

