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
          [FX Routing: None/FX1/FX2 via 3 Serial Chains x 3 Filter Slots]
                 │                  └────────┬─────────┘
                 │                           │
                 │                ISF Transition Filter
                 │             (e.g. crossfade, additive,
                 │                screen, multiply, max)
                 │                           │
                 └──────────────────┬────────┘
                                    │
                                 Mixer.kt
                                mixer.frag
                   (Composite: Transition Output over BG)
                                    │
                    [MFX: 3 Serial Chains x 3 Filter Slots]
                                    │
                               masterFBO ──► screen

Deck PV  (preview only — same pipeline as A/B/BG, excluded from Mixer output)
   └── used to build/audition presets while A, B, and BG are performing live
```

## Zero-Allocation Render Loop Guarantees

To maintain stable 60–120 FPS playback without ZGC pause interruptions or frame drops, the hot render path (executed strictly on OS Thread 0) adheres to strict zero-allocation rules:
- **ISF & Visual Generators (`ISFVisualSource.kt`, `DynamicVisualSource.kt`, `ISFFilter.kt`)**: Uniform names, derived input bindings (`color`, `point2D`, `float`, `bool`), and multipass targets are pre-bound at initialization time. Uniform setters, `DATE` epoch calculations, and parameter updates loop directly over pre-allocated arrays, avoiding `LocalDateTime` instantiation, map lookups, string formatting, and collection iterator allocations.
- **Dynamic Visual Source Thread 0 Discipline (`VisualSourceRegistry.kt`)**: Visual source scanning delegates OpenGL shader compilation tasks to `pendingGlTasks` queue processed exclusively on OS Thread 0 (`processPendingGlTasks()`) at the start of each frame, completely isolating background discovery threads from GPU context operations. Startup initialization (`loadAll(async = false)`) compiles shaders synchronously on Thread 0 before the render loop begins.
- **MIDI Evaluation & Dispatch (`MidiEngine.kt`, `MidiMappingManager.kt`, `ParameterResolver.kt`, `ModulatorPropertyAccessor.kt`)**: `MidiEngine` captures `ShortMessage.CONTROL_CHANGE`, `NOTE_ON`, `NOTE_OFF`, and `PITCH_BEND` into atomic storage and lock-free queues (`receivedEvents`). `MidiMappingManager` resolves parameter paths once when mappings change, supporting both direct parameter paths and hierarchical modulator variables (`:mod/<index>/<property>`), storing bindings in an unboxed, flat `ResolvedMidiBinding` array. The event dispatch delegates value mutations to `ModulatorPropertyAccessor` or `param.baseValue`, processing incoming relative rotary deltas (Binary Offset, Signed Bit, Two's Complement), discrete button triggers (Momentary, Toggle, Step), and continuous soft takeover (pickup). The per-frame `update(mixer)` loop evaluates exponential slew smoothing without heap allocations, map lookups, or garbage collection churn.
- **CV Source Evaluation & MIDI Routing (`CVRegistry.kt`, `Evaluators.kt`)**: Non-audio CV sources are maintained in a pre-allocated `activeNonAudioSources` array traversed via indexed loops during `updateAll()`. MIDI CC and Note lookups bit-pack channel and index into 64-bit keys (`(channel.toLong() shl 32) or index.toLong()`) avoiding string splitting, and `AudioFollowerTracker` guards state map retrieval before calling `computeIfAbsent` to eliminate capturing lambda allocations on the audio path.
- **Mixer & Modulators (`Mixer.kt`, `ModulatableParameter.kt`)**: Modulator activity checks on `CopyOnWriteArrayList` use index-based O(1) traversal (`hasActiveModulator()`) instead of `.any { }` to prevent per-frame `COWIterator` allocations.
- **Registries (`ISFFilterRegistry.kt`, `ISFTransitionRegistry.kt`, `ISFLibraryRegistry.kt`, `VisualSourceRegistry.kt`)**: `availableFilters`, `availableTransitions`, `availableSources`, and `allAssets` are backed by `@Volatile` immutable sorted snapshots rebuilt once per scan or modification, eliminating per-frame list re-allocation and re-sorting during picker rendering. Directory scoping prevents cross-scanning between generator and filter/transition folders (`library/sources`, `library/filters`, `library/transitions`). All visual source and preset menu items in `ParametersPanel.drawLaunchpad` enforce non-blank labels with unique `##` identifier scoping to prevent ImGui empty-ID root assertion crashes (`id != window->ID`). Compilation errors in user shaders are logged as concise warnings rather than dumping multi-page stack traces during startup.

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
│   ├── BroadcastPreferences.kt — Broadcast configuration and persistence (lsd-preferences.properties with legacy fallback)
│   └── WebPresetSerializer.kt  — Converts desktop Deck/Mixer state to WebGL2 TV JSON schema
├── cv/
│   ├── CVRegistry.kt           — Singleton: all CV sources, beat sync, histories
│   ├── CVSource.kt             — Interface: id, value, update()
│   ├── BeatClock.kt            — Beat phase 0..1, JACK-synced
│   ├── Evaluators.kt           — Evaluators for lfo, beatPhase, sampleAndHold, audio
│   ├── GenCVSource.kt          — Registry placeholder for the lfo generator
│   └── CvHistoryBuffer.kt      — Ring buffer (200 samples)
├── midi/
│   ├── MidiEngine.kt           — Multi-message MIDI receiver, atomic state, event queue, and live sniffer buffer
│   └── MidiMappingManager.kt   — Multi-type parameter mapping, soft takeover, rotary decoding, and slew smoothing
├── osc/
│   ├── OscCodec.kt             — Pure Kotlin zero-dependency binary OSC 1.0 encoder/decoder (messages & bundles)
│   ├── OscEngine.kt            — Low-latency UDP receiver/transmitter, remote client auto-learn & packet sniffer
│   ├── OscLearnState.kt        — Interactive "Learn OSC" target-arming state machine, mirrors MacroLearnState
│   ├── OscMappingManager.kt    — OSC address routing (parameters & modulators), vector unpacking, slew smoothing & profile persistence
│   └── OscPreferences.kt       — OSC network port, enable flag & active profile persistence settings
├── input/
│   ├── TouchConsoleController.kt — 4-zone SCS.3m virtual console, LIFO stacks, zone affinity
│   ├── TouchConsoleEvent.kt    — Low-latency native touch event model
│   ├── TouchStripBackend.kt    — Hardware backend interface & states
│   ├── LinuxEvdevTouchBackend.kt — Linux evdev JNA reader, EVIOCGRAB, EVIOCGABS, MT Protocol B cache
│   ├── MacCocoaTouchBackend.kt — macOS Cocoa NSTouch indirect touch events
│   └── NoOpTouchBackend.kt     — Safe fallback
├── models/
│   ├── PresetModels.kt         — Data models + DTOs for preset serialization
│   ├── FXPresetModels.kt       — Data models for per-slot (.lsdfx) and FX chain (.lsdfxchain) serialization
│   └── ClipboardManager.kt     — Copy/paste for preset, slot, and chain elements
├── notes/
│   └── NotesManager.kt         — 3-tier notes persistence manager (global source notes, preset notes, param notes)
├── parameters/
│   ├── ModulatableParameter.kt — Parameter state and evaluation
│   ├── CvModulator.kt          — CV modulation routing
│   ├── ModulatorPropertyAccessor.kt — Unified accessor/mutator for modulator variables (LFO period, depth, shape, hold)
│   ├── Enums.kt                — Enums
│   ├── ParameterOwner.kt       — Parameter ownership interface
│   ├── ParameterResolver.kt    — Parameter lookup
│   └── WaveformMath.kt         — Math utils
├── macro/                      — Macro Controls & Parameter Linking engine; see docs/user_guide/macros_and_rack.md
│   ├── MacroModels.kt          — Data model: `MacroBinding`, `MacroControl` (knob/switch value + TOGGLE/MOMENTARY/TRIGGER state machine), `MacroBank` (up to 8 knobs / 4 switches)
│   ├── MacroCurve.kt           — Pure curve-shaping math (LINEAR/EXPONENTIAL/LOGARITHMIC/S_CURVE/STEP) and min/max/invert range mapping
│   ├── MacroEngine.kt          — Per-frame binding evaluation singleton; one `MacroBank` per canonical bank id (`DECK_A`/`DECK_B`/`DECK_BG`/`DECK_PV`/`TRANS`/`MASTER`), read/written by both Classic Column 3 and the Performance Mode 4×4 Matrix
│   ├── MacroLearnState.kt      — Interactive click-to-bind Learn Mode session state machine and UI status banner
│   ├── MacroBankSerializer.kt  — Deck-scoped bank filtering/remapping for `.lsd`/`.lsdplay` DTOs, plus standalone `.knobpreset.json` export/import
│   └── MacroOscBridge.kt       — `/macro/knob/N` & `/macro/switch/N` inbound OSC address routing and outbound feedback broadcast
├── presets/
│   ├── PresetManager.kt        — Save/load presets, state management, per-deck dirty-state cache (`isDeckDirty`)
│   ├── PresetRepository.kt     — Async load/save for deck presets, FX presets/chains/playlists, and transition presets/playlists (`CompletableFuture` + bounded executor)
│   ├── PresetDependencyAnalyzer.kt — Dependency analysis, disabled/offline feature inspection, zero-alloc memoization
│   ├── PresetMigrator.kt       — Sanitizes a loaded `DeckPresetDto` against the active visual source/feedback schema, filling defaults & stripping obsolete keys
│   ├── DeckLifecycleManager.kt — Deck clear/copy/move/swap operations and associated active-preset bookkeeping
│   ├── PlayQueueManager.kt     — Manages the A/B playback queue (shuffle/repeat/history, dirty-deck SKIP/AUTO_SAVE/AUTO_DISCARD guard via `UITheme.autoVjDirtyBehavior`); has its own independent copy of the index-bookkeeping helpers, not on `QueueEngine`
│   ├── BgQueueManager.kt       — Manages the background deck queue, incl. dip-to-black transition state machine; also not on `QueueEngine` (see PlayQueueManager.kt note)
│   ├── QueueEngine.kt          — Shared abstract base for FX and Transition queues: shuffle/repeat/history-index bookkeeping, playlist parsing (via `PlaylistParser`), append/insert/remove/move/clear, `computeNextIndex`/`computePrevIndex`
│   ├── FxQueueEngine.kt        — `QueueEngine` + deck-targeting & dirty-deck guard, backing the FX A/B and FX BG queues
│   ├── FXQueueManager.kt       — `FxQueueEngine` for Deck A/B, targets the crossfader-active deck
│   ├── FXBgQueueManager.kt     — `FxQueueEngine` for Deck BG
│   ├── FXItemApplier.kt        — Applies a queued `.lsdfx`/`.lsdfxchain` file to a deck's 4 FX slots deterministically
│   ├── TransitionQueueManager.kt — `QueueEngine` + transition apply/auto-fade-hook/session-restore, for the Transition Queue (`.lsdtrans`/`.lsdtransplay`); keeps unresolved playlist items as literal stock-shader-ID tokens instead of dropping them
│   ├── PlaylistParser.kt       — Parses playlist files
│   ├── SessionState.kt         — Session state management
│   ├── SessionSerializer.kt    — Persists/restores the active session, incl. the five canonical per-deck/mixer macro banks
│   └── PresetIOStatus.kt       — IO status for UI feedback
├── cli/                        — Startup CLI argument parsing & validation
│   └── CliArgs.kt              — Command line options (--screenshot-ui, --window, --no-audio, --ui-lab)
├── export/                     — Video & audio render export
│   ├── AccumulationBuffer.kt   — HDR multi-pass motion blur accumulation
│   ├── AudioDecoder.kt         — Audio file decoding (WAV, MP3, FLAC, OGG, M4A)
│   ├── FFmpegProcessPipe.kt    — Non-blocking FFmpeg subprocess pipe with HW encoder prioritization
│   ├── OfflineRenderStudio.kt  — Deterministic offline video rendering with sample-accurate DSP
│   ├── PboReadbackPipeline.kt  — High-speed DMA GPU-to-CPU framebuffer readback
│   ├── RealtimeRecorder.kt     — Live session video & audio capture and muxing
│   └── ScreenshotCapture.kt    — Synchronous/FBO PNG image export with scanline vertical flip
├── rendering/
│   ├── Mandala.kt              — Mandala4Arm (recipe + field docs), Mandala (VisualSource), analytical arm normalization
│   ├── MandalaLibrary.kt       — ~300 curated MandalaRatio entries
│   ├── VisualEffect.kt         — Interface for post-processing effects
│   ├── isf/                    — Universal shader preprocessor, ISF/Shadertoy/GLSLSandbox format parser, models, ISFFilter (incl. per-effect Metaknob), ISFAutoBindEngine & FxMetaBinding (3-tier Metaknob auto-bind: user override cache/curated/heuristic), multi-pass ISFVisualSource, ISFTransitionRegistry, ISFDirectoryManager, ISFScanner, ISFLibraryRegistry & ISFFileWatcher
│   ├── AudioTexture.kt         — Universal 512x2 floating-point audio FFT spectrum and live waveform OpenGL texture stream
│   ├── FxChain.kt              — Individual FX chain hosting 3 ISF filter slots with chain-level wet/dry and bypass, plus a Super Knob that drives linked slots' Metaknobs via soft-takeover
│   ├── FxBank.kt               — FX Bank (FX1, FX2, MFX) managing 3 serial FxChain instances with master wet/dry and bypass
│   ├── Deck.kt                 — VisualSource + cleanFBO + 4-buffer ping-pong architecture (scratch fxPingFBO/fxPongFBO + alternating fxChainOutFBO/fxBankOutFBO) + View & FxRouting params
│   ├── Mixer.kt                — Blends Deck A+B via 100% ISF transition over BG -> masterFBO with masterFxBank (MFX) & 4-buffer ping-pong architecture
│   ├── Renderer.kt             — Per-frame: universal uniform bridge -> polymorphic source renderTopology() -> 2D view transform -> serial 3-chain FX bank pass -> ISF transition pass (A/B) -> Deck BG composite -> master FX pass -> blit
│   ├── VisualSource.kt         — Interface (Mandala, DynamicVisualSource, 2D/3D classification via is3D)
│   ├── VisualSourceRegistry.kt — Pluggable dynamic visual sources with automatic 3D and foreign shader format detection
│   ├── DynamicVisualSource.kt  — Wraps loaded GLSL shaders, handles 2D/3D source tagging, uniform binding, and multi-pass topology rendering
│   ├── ExternalVideoSource.kt  — Live video ingest visual source driven by Spout/Syphon/PipeWire video streams
│   ├── ExternalVideoDiscovery.kt — Background discovery service polling for active Spout, Syphon, and PipeWire video streams
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
│   ├── AppPreferences.kt       — App preferences data model, persistent layout & feature toggles
│   ├── UIManager.kt            — Top-level layout orchestrator & GLFW/ImGui render loop
│   ├── MenuBar.kt              — Unified header bar, navigation menus, telemetry HUD & window controls
│   ├── ShaderPickerPopup.kt    — High-performance category-based shader & source selector
│   ├── WindowFrameController.kt— Client-Side Decorations (CSD), window dragging & perimeter edge resizing
│   ├── DeckPresetController.kt — Deck preset file lifecycle and dialog controller
│   ├── UIThemeStyler.kt        — ImGui dynamic styling, theme palettes, and font scaling
│   ├── SplitterManager.kt      — Multi-column layout dragging and divider render manager
│   ├── ParametersPanel.kt      — Parameter matrix with tabs, source dropdown, and modulator columns
│   ├── PropertiesPanel.kt      — Edits parameter values and modulators with oscilloscope
│   ├── PanelTitleBar.kt        — Synchronized 1.5x title bar renderer and optical text centering for Parameters & Properties
│   ├── LibraryPanel.kt         — Library dock panel (presets, playlists, queue)
│   ├── NoteEditorModal.kt      — Zero-allocation modal editor for the 3-tier Note System
│   ├── PreferencesPanel.kt     — App configuration & tabbed preferences modal
│   ├── AudioEnginePanel.kt     — Audio input, beat detection, and real-time oscilloscopes (Preferences tab drawer)
│   ├── MidiPreferencesPanel.kt — MIDI port configuration, channel filters & mapping table UI
│   ├── OscPreferencesPanel.kt  — TouchOSC / OSC server config, live packet sniffer & Learn UI
│   ├── ColorTunerPanel.kt      — Interactive theme editor
│   ├── DeckControlPanel.kt     — Individual deck preview monitor, toolbar, inside-clustered badge/die overlays, and vertical channel level fader
│   ├── MixerPanel.kt           — 2x2 monitor matrix, master output monitor with [M] badge, [🎲 ALL], master level fader, and streamlined crossfader
│   ├── PlaylistManager.kt      — Manages saved setlists
│   ├── VideoExportModal.kt     — Modal for offline video render studio & file chooser
│   ├── MacroPanel.kt           — Column 3 MACROS editing surface: 8 knobs + 4 switches, binding inspector, Learn Mode; renders the dedicated FX Rack view (below) instead of the generic grid for the FX1/FX2/MFX tabs
│   ├── MacroKnobWidget.kt      — Rotary macro knob widget: drag/wheel interaction, accent-colored arc fill, optional deck tint
│   ├── FXChainMacroStrip.kt    — Traktor/Mixxx-style FX Rack strip: Chain Super Knob + 3 slot Metaknobs (soft-takeover link toggles), Single FX Focus Mode, right-click Metaknob rebind menu. Drawn in both ParametersTabs.kt (per-chain, Parameters panel) and MacroPanel.kt (FX1/FX2/MFX tabs, Column 3)
│   ├── PerformanceMatrixPanel.kt — Performance Mode 4×4 knob grid: 4 tabs, deck-colored rows, plus the Modular Rack accordion (see `rack/` below) — a chevron on each row group cycles Faceplate → Bay → Deep Edit, with a scrollable Bay/Deep-Edit region drawn below the (always fixed-height) grid
│   ├── rack/
│   │   └── RackUnit.kt          — Shared chevron/disclosure-tier drawing helper for the Modular Rack accordion, and the persistent "Learning: …" indicator; stateless, operates only on `ParametersState` (never `Mixer`/`FxBank`, enforcing that disclosure changes can't trigger FX bank refocus or `FxMacroSync` re-runs)
│   ├── UiLabPanel.kt           — Isolated UI component gallery sandbox (swatches, icons, custom widgets)
│   ├── browser/                — LibraryPanel sub-panels: preset/FX/transition list, playlist editor & queue actions
│   │   ├── PresetListPanel.kt          — Preset list/grid tier of the library browser
│   │   ├── PlaylistEditorPanel.kt      — `.lsdplay` playlist editor tier
│   │   ├── QueueActionsPanel.kt        — Play Queue (A/B) actions: reorder, shuffle/repeat, jump/advance
│   │   ├── BgQueueActionsPanel.kt      — Background Queue actions (mirrors QueueActionsPanel for Deck BG)
│   │   ├── FXBrowserPanel.kt           — Unified FX browser: stock ISF filters, saved `.lsdfx`, saved `.lsdfxchain`, saved `.lsdfxbank` in one list
│   │   ├── FXPlaylistEditorPanel.kt    — `.lsdfxplay` FX playlist editor tier
│   │   ├── FXQueueActionsPanel.kt      — FX Queue (A/B) actions, mirrors QueueActionsPanel for FX items
│   │   ├── FXBgQueueActionsPanel.kt    — FX Queue (BG) actions, mirrors QueueActionsPanel for Deck BG FX items
│   │   ├── StockTransitionListPanel.kt — Built-in ISF transition list tier
│   │   ├── TransitionPresetListPanel.kt — Saved `.lsdtrans` transition preset list tier
│   │   ├── TransitionPlaylistEditorPanel.kt — `.lsdtransplay` transition playlist editor tier
│   │   ├── TransitionQueuePanel.kt     — Live Transition Queue actions
│   │   ├── BrowserPopupHandler.kt      — Rename/delete/new-playlist/export-queue modal popups shared across all list tiers
│   │   ├── BrowserActionToolbar.kt     — Shared top toolbar (view toggles, search, sort) across list tiers
│   │   ├── BrowserDeckButtons.kt       — Shared deck-target button styling for PresetListPanel/PlaylistEditorPanel
│   │   └── BrowserRowMoreButton.kt     — Shared right-aligned kebab (⋮) row context-menu button
│   └── ParametersState.kt      — Selection state & 30-level Undo Stack; also owns Modular Rack accordion state (`rackModuleDisclosure`/`DisclosureLevel`, `rackSoloMode`, `selectedRackMacroId`, `rackSelectedCell` — the last two are per-moduleId so two simultaneously open Deep Edits in Multi mode don't fight over one shared selection)
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
│  Parameters      │  Properties    │  Mixer         │
│  (40% width)     │  (30% width)   │  (30% width)   │
│                  │                │                │
├──────────────────┴────────────────┴────────────────┤
│                 Library Panel                      │
│        (Presets | Playlists | Q | BGQ)             │
└────────────────────────────────────────────────────┘
```

Parameters rows: Mixer → Deck A [Geometry, Color, Feedback] → Deck B [same] → Deck BG [same] → Deck PV [same]  
Parameters columns: VAL | MIDI | LFO | SEQ | AUD  
*(Note: Engine subsystems `midiEnabled`, `sequencerEnabled`, and `audioEngineEnabled` serve as the single source of truth for Parameters and Properties column visibility; the header kebab menu `⋮` allows immediate toggling of these engines and columns).*

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
- **Per-Slot FX Presets & FX Chains** — Modular `.lsdfx` (stored in `library/fx/`) and `.lsdfxchain` (stored in `library/fx_chains/`) serialized DTOs for saving and recalling single slot effects or 4-slot FX chains.
- **FX Queues & Playlists** — `.lsdfxplay` FX playlists (stored in `library/fx_playlists/`) and the live volatile FX Queue (A/B and BG, `FxQueueEngine` + `FXQueueManager`/`FXBgQueueManager`) apply queued `.lsdfx`/`.lsdfxchain` items deterministically to all 4 slots via `FXItemApplier` — never a per-slot merge. Mirrors the `PlayQueueManager`/`BgQueueManager` shuffle/repeat/history/dirty-deck-guard pattern already used for presets.
- **Thread safety & OpenGL Thread 0 Discipline** — `@Volatile` primitive fields (`anchorBeats`, `anchorBpm`, `anchorTimeNs`) for zero-allocation audio thread beat clock sync, `CopyOnWriteArrayList` for modulators, `ConcurrentLinkedQueue` for MIDI CC events, and strict Main OS Thread (Thread 0) execution for all GLFW window polling, OpenGL context operations, and ISFFilter creation/disposal.
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

- **Sync Manifest (`web/sync_manifest.json`)**: Authoritative mapping of desktop assets, GLSL 3.3 Core shaders (`src/main/resources/shaders/`, `library/sources/`), and algorithmic math files (`Evaluators.kt`, `WebPresetSerializer.kt`) to their WebGL2 / ES module equivalents.
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

## Flexible ISF Directory Architecture, Role Auto-Detection & Asset Resolution

- **Role Auto-Detection via JSON `INPUTS`**: Removes rigid folder requirements (`library/sources`, `library/filters`, `library/transitions`). Any directory registered in `ISFDirectoryManager` is scanned recursively, classifying shaders by image input count:
  - 0 image inputs: Generator / Visual Source (`ISFAssetType.GENERATOR` $\to$ `VisualSourceRegistry`)
  - 1 image input: Filter / FX (`ISFAssetType.FILTER` $\to$ `ISFFilterRegistry`)
  - 2+ image inputs (or transition `progress` input): Mixer Transition (`ISFAssetType.TRANSITION` $\to$ `ISFTransitionRegistry`)
- **Preserved Folder Hierarchies & Tags**: Retains relative subfolder paths in `ISFAsset.folderPath` and tags in `categories`. `ShaderPickerPopup` provides both a collapsible folder tree view (`Icons.FOLDER`) and flat table view (`Icons.LAYOUT_FULL`) with zero per-frame render thread allocations.
## 100% ISF Pipeline & Modular Effects Engine

All post-processing effects, 2D-to-3D projection geometry, and mixer transitions run as modular Interactive Shader Format (ISF) effects:
- **Modular Feedback (`default_filters/feedback.fs`)**:
  - Replaces monolithic hardcoded `feedback.frag` with an ISF multi-pass persistent history buffer filter.
  - Exactly preserves the legacy cubic decay curve ($s \to (1 - s)^3$) and 9-parameter feedback optics (`fbDecay`, `fbGain`, `fbZoom`, `fbRotate`, `fbHueShift`, `fbBlur`, `fbChroma`, `fbMode`, `fbKaleido`).
  - History buffers clear to zero on filter reset or preset loading (`ISFFilter.reset()`) to eliminate ghost frames.
- **Modular 3D Elevation (`default_filters/3d_elevation.fs`)**:
  - Replaces legacy hardcoded `tri_planar.*` and `tetra_kaleido.*` shaders with a raymarched ISF filter, conventionally loaded into FX Slot 2 (any of the 4 slots works).
  - Implements Tri-Planar, Cube Cage, Hex-Planar, and 24-Chamber Tetrahedral Coxeter space folding via analytic inverse camera raymarching with exact 1:1 scale normalization matching 2D mode height at $z = 0$.
  - Supports dual blend modes (`blendMode`): luminous additive energy synthesis (`glBlendFunc(GL_ONE, GL_ONE)` equivalent) and premultiplied alpha over.
  - All 10 parameters are directly accessible and modulatable under the Deck "FX" tab, in whichever slot the filter is loaded.
- **Pure ISF Mixer Transitions**:
  - Eliminates legacy math blend modes and unfeathered geometric wipes in favor of a curated suite of 8 club-grade shaders.
  - All Deck A $\leftrightarrow$ Deck B transitions execute via `ISFFilter` taking `startImage`, `endImage`, and `progress` ($0 \dots 1$).
  - Bundled curated transitions:
    1. `linear_crossfade.fs`: Pristine dissolve with perceptual cosine S-curve smoothing and equal power options.
    2. `luminous_flash.fs`: Filmic exposure overdrive and bloom flare peaking at midpoint for musical drops.
    3. `film_burn.fs`: 35mm celluloid burn with organic fractal noise and glowing chromatic ember frontiers.
    4. `noise_dissolve.fs`: Multi-octave domain-warped fractal noise erosion with soft feathered contours and chromatic fringing.
    5. `liquid_displacement.fs`: Cross-deck optical vector morphing where Deck A and Deck B dynamically melt and ripple into each other.
    6. `kinetic_zoom.fs`: High-speed camera crash zoom with multi-tap radial motion blur streak and edge chromatic dispersion.
    7. `vortex_swirl.fs`: Gravitational vortex singularity spiraling Deck A into center and unwinding Deck B.
    8. `cyber_datamosh.fs`: Digital video codec corruption with macroblock tearing, horizontal sync jitter, and chromatic shear.
- **Preserved Deck BG Compositing & Dual-Mode Transition Shading**:
  - Deck BG is composited behind the active A/B transition output in a streamlined `mixer.frag` pass with bloom, levels, and master alpha:
    $$\text{Master Output} = \text{Composite}(\text{Deck BG}, \text{ISF\_Transition}(\text{Deck A}, \text{Deck B}, \text{progress}))$$
  - `mixer.frag` operates in dual mode: pure ISF composite mode (`uMode < 0`) where `uTex1` (`blendFBO`) is modulated by crossfade channel levels (`mix(uLevelA, uLevelB, uProgress)`), and legacy dual-input mode (`uMode >= 0`) for WebGL / non-ISF fallback rendering.
  - Zero-allocation fallback: `Renderer.kt` caches an instance of `linear_crossfade` so unassigned transition states never allocate or destroy filters during frame rendering.
- **Simplified FBO Footprint**:
  - Removed obsolete `rawSourceFBO`, `rawSource2DFBO`, `fb1`, and `fb2` ping-pong buffers from `Deck.kt`, saving 16 full-resolution FBOs across the 4 decks and dramatically reducing GPU memory usage.

## Version & Update Engine (`update`)


- **Authoritative Version Resolution (`AppVersion.kt`)**: Dynamically resolves the runtime version from JAR manifest attributes (`Implementation-Version`), packaged classpath `/version.txt`, or fallback default.
- **Semantic Versioning (`SemVer.kt`)**: Zero-dependency parser and comparator implementing SemVer 2.0.0 precedence rules (supporting numeric major/minor/patch, release vs pre-release precedence, dot-separated pre-release tokens, and snapshot identifiers).
- **Background Release Checker (`UpdateChecker.kt`)**: Asynchronous, daemon-threaded update engine checking GitHub releases with 5-second timeouts. Features dual-mode detection (GitHub REST API with fallback to web redirect inspection) and fail-safe error handling to guarantee audio processing and rendering loops remain unblocked.
- **Interactive Modals (`UpdatePromptModal.kt`, `AboutModal.kt`)**:
  - `UpdatePromptModal`: Prompts when newer releases are discovered, offering immediate download via system browser, session reminder, or permanent per-version skip.
  - `AboutModal`: Accessible from the Help menu; displays current version, manual update checker, and repository links.

## System Requirements & Platform Constraints

Liquid LSD targets low-latency live performance across 5 platforms (Linux x64, Linux ARM64, macOS x64, macOS ARM64, Windows x64). Because it runs multiple concurrent FBO render pipelines and GLSL 330 core shaders, the engine imposes strict architectural constraints:

- **OpenGL 3.3 Core Profile Hardware Acceleration**:
  - Requires hardware-accelerated OpenGL 3.3 Core Profile context (`GLFW_CONTEXT_VERSION_MAJOR 3`, `GLFW_CONTEXT_VERSION_MINOR 3`, `GLFW_OPENGL_PROFILE GLFW_OPENGL_CORE_PROFILE`, `GLFW_OPENGL_FORWARD_COMPAT GLFW_TRUE`).
  - Legacy GPUs such as Intel GMA 3000/X3100/X4500 (standard in Core 2 Duo era machines) and 1st-Gen Intel HD Graphics (Arrandale/Clarkdale) cap out at OpenGL 1.4–2.1 and **cannot** instantiate an OpenGL 3.3 Core Profile GLFW context.
  - Supported GPU families: Intel HD 3000/4000+ (Mesa 20+ on Linux), Iris, UHD, Xe, Arc; AMD Radeon HD 5000+ (TeraScale 2/GCN/RDNA); NVIDIA GeForce 8000/9000/GT 200+ (Tesla 2.0 / Fermi+); Apple Silicon M-Series.
- **64-Bit OS & Architecture**:
  - The JVM, JNI native loaders (`imgui-java`, `lwjgl`, `jna`, `liblink_jni`), and ZGC memory mapping require a 64-bit operating system (`x86_64` or `aarch64`). 32-bit systems are unsupported.
- **CPU & Memory**:
  - Minimum: 64-bit dual-core CPU with SSE4.1/AVX (Intel 2nd-gen Core 2011+, AMD FX/Ryzen, Apple Silicon). Recommended: 4+ physical cores with 8–16 GB RAM for smooth multi-deck video compositing and sub-millisecond ZGC GC pauses.
- **Audio Subsystem**:
  - Linux: Real-time JACK or PipeWire (`pipewire-jack`) recommended for sub-millisecond DSP and zero-allocation audio callbacks. Java Sound provides ALSA/PulseAudio fallback on Linux and primary audio input on macOS/Windows.

## Build & Run
```bash
./gradlew run              # launch (JACK/PipeWire recommended for Linux, Java Sound fallback runs otherwise)
./gradlew compileKotlin    # type-check only, no run
./gradlew test             # run test suite (includes WebSyncTest)
./gradlew checkWebSync     # verify desktop ↔ web asset synchronization
./gradlew syncWeb          # auto-transpile desktop shaders into web/
./gradlew packageThumbDrive  # bundle fat JAR + JREs + library for all 5 platforms
./gradlew packageZips        # assemble platform distribution ZIPs (Windows x64, Linux x64, Linux ARM64, macOS arm64, macOS x64)
./gradlew run --args="--smoke-test"  # run headless binary self-diagnostic smoke test
./gradlew run --args="--version"     # print version, architecture, and JVM runtime details
```
Custom visual shaders and presets are loaded from `library/sources/` and `library/presets/`. Distribution ZIPs package the complete `library/` folder with executable (`755`) permissions on launcher scripts (`.sh`, `.command`) and bundled JRE binaries. Launcher scripts forward all CLI arguments (`"$@"` / `%*`) directly to the bundled JVM. 

### Multi-Platform Verification Matrix (GitHub Actions)
All five target platform distributions are pre-tested natively on GitHub-hosted runners (`ubuntu-latest`, `ubuntu-24.04-arm`, `macos-latest`, `macos-15-intel`, `windows-latest`) via `.github/workflows/smoke-test.yml` (on PRs) and `.github/workflows/release.yml` (prior to release publishing). The release workflow employs selective gating: only distributions that pass automated smoke testing are published as release assets.

For deeper notes see `docs/developer/`, `DECISIONS.md`, and `.agents/PROJECT.md`.

