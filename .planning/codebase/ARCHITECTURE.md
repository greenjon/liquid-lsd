<!-- refreshed: 2026-07-07 -->
# Architecture

**Analysis Date:** 2026-07-07

## System Overview

```text
┌─────────────────────────────────────────────────────────────┐
│                 Desktop App Main Loop                       │
│                 `src/main/kotlin/llm/slop/liquidlsd/Main.kt`  │
├──────────────────┬──────────────────┬───────────────────────┤
│   ImGui Control  │   OpenGL Output  │   Input/Automation    │
│   `src/main/.../ui` │ `src/main/.../rendering` │ `src/main/.../midi` │
└────────┬─────────┴────────┬─────────┴──────────┬────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│          Domain State, Modulation, and Persistence           │
│ `src/main/.../parameters`, `src/main/.../cv`,                │
│ `src/main/.../presets`, `src/main/.../models`                │
└─────────────────────────────────────────────────────────────┘
         │                  ▲
         ▼                  │
┌─────────────────────────────────────────────────────────────┐
│  Presets, Shaders, Audio/MIDI Devices, Generated Docs        │
│  `library/`, `src/main/resources/`, JACK, Java MIDI          │
└─────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| Application entry point | Creates preset directories, initializes GLFW/OpenGL/ImGui, loads visual sources, constructs decks and mixer, runs the frame loop, and performs orderly shutdown. | `src/main/kotlin/llm/slop/liquidlsd/Main.kt` |
| Main frame loop | Polls GLFW events, applies queued presets, updates CV and MIDI mappings, renders decks, composites the mixer, draws UI, swaps buffers, and services the secondary output window. | `src/main/kotlin/llm/slop/liquidlsd/Main.kt:198` |
| UI manager | Owns ImGui context/backends, menu/popups, asset browser layout, preset grid, deck preset browser coordination, MIDI learn event draining, and font/style updates. | `src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt` |
| Renderer | Owns core shader programs, Mandala VAO/VBO, deck rendering, feedback passes, dynamic visual source rendering, and mixer compositing. | `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt` |
| Deck | Represents one visual chain with source selection, source FBO, ping-pong feedback FBOs, and per-deck feedback parameters. | `src/main/kotlin/llm/slop/liquidlsd/rendering/Deck.kt` |
| Mixer | Holds Deck A, Deck B, Deck PV, master FBO, crossfade/blend/master controls, queue trigger parameters, and auto-fade state. | `src/main/kotlin/llm/slop/liquidlsd/rendering/Mixer.kt` |
| Visual source registry | Loads dynamic shader sources from `library/sources/*/meta.json` and `library/sources/*/shader.frag`, compiles shaders, and creates master source instances. | `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt` |
| Preset manager | Serializes/deserializes deck/global/session DTOs, queues background loads for main-thread apply, saves presets/session files, and tracks dirty state caches. | `src/main/kotlin/llm/slop/liquidlsd/presets/PresetManager.kt` |
| Preset DTO converters | Defines persisted preset/session/playlist models and extension converters between DTOs and domain objects. | `src/main/kotlin/llm/slop/liquidlsd/models/PresetModels.kt` |
| Modulation parameter | Evaluates base values plus CV/MIDI modulators and records parameter history for meters/oscilloscopes. | `src/main/kotlin/llm/slop/liquidlsd/parameters/ModulatableParameter.kt` |
| CV registry | Stores CV sources and histories, publishes audio-derived values, synchronizes beat state between audio and render threads, and updates sources once per frame. | `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt` |
| Audio engine | Runs JACK-backed audio capture, DSP filters, beat detection, and audio-to-CV publishing. | `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt` |
| JACK client | Opens JACK, registers the input port, installs the real-time process callback, and auto-connects physical input ports. | `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt` |
| MIDI engine | Opens Java MIDI input devices, stores CC values in atomic arrays, and queues MIDI CC events for render-thread processing. | `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt` |
| MIDI mapping manager | Loads/saves mapping profiles and applies CC values to mixer/deck parameters each frame. | `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt` |
| Queue managers | Manage playlist files and the active play queue used by Auto VJ and queue-next/previous triggers. | `src/main/kotlin/llm/slop/liquidlsd/ui/PlaylistManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/PlayQueueManager.kt` |
| Project-local architecture skills | Define native ImGui allocation, JACK callback, and LWJGL/OpenGL thread constraints that must be followed for new work. | `.agents/skills/imgui_memory_management/SKILL.md`, `.agents/skills/jack_callback_safety/SKILL.md`, `.agents/skills/lwjgl_thread_restriction/SKILL.md` |

## Pattern Overview

**Overall:** Single-process immediate-mode desktop app with a main render-thread orchestrator, domain state objects, background IO queues, and native device callbacks.

**Key Characteristics:**
- Keep GLFW polling, OpenGL context work, rendering, ImGui frame creation, and ImGui drawing on the main thread in `src/main/kotlin/llm/slop/liquidlsd/Main.kt`.
- Treat `Mixer`, `Deck`, `VisualSource`, `ModulatableParameter`, and DTO converters as the core domain model. UI panels mutate these domain objects directly on the render thread.
- Use background threads only for IO/device monitoring/analysis, then communicate with render-thread state through atomic values or thread-safe queues.
- Use `presets/sources/*` as data-driven visual source plugins: `meta.json` defines parameters and `shader.frag` supplies fragment shader code.
- Persist application state as JSON DTOs under `presets/`, not through a database or service layer.

## Layers

**Application Orchestration:**
- Purpose: Create native resources, wire subsystems, own frame cadence, and clean up resources in reverse order.
- Location: `src/main/kotlin/llm/slop/liquidlsd/Main.kt`
- Contains: `main()`, monitor/window helpers, GLFW callbacks, main loop, startup/shutdown sequencing.
- Depends on: `src/main/kotlin/llm/slop/liquidlsd/rendering`, `src/main/kotlin/llm/slop/liquidlsd/ui`, `src/main/kotlin/llm/slop/liquidlsd/audio`, `src/main/kotlin/llm/slop/liquidlsd/cv`, `src/main/kotlin/llm/slop/liquidlsd/patches`.
- Used by: Gradle `application.mainClass` in `build.gradle.kts`.

**Rendering:**
- Purpose: Own OpenGL resources and convert deck/mixer domain state into framebuffer output.
- Location: `src/main/kotlin/llm/slop/liquidlsd/rendering`
- Contains: `Renderer`, `Deck`, `Mixer`, `FBO`, `Shader`, `Geometry`, `VisualSource`, `DynamicVisualSource`, `VisualSourceRegistry`, `Mandala`.
- Depends on: LWJGL OpenGL, shaders in `src/main/resources/shaders`, dynamic source presets in `presets/sources`, and modulation classes in `src/main/kotlin/llm/slop/liquidlsd/parameters`.
- Used by: `Main.kt`, `UIManager.kt`, patch DTO converters, deck/mixer tests.

**Immediate-Mode UI:**
- Purpose: Draw all user controls, asset management, patch grid editing, popups, settings, and diagnostics with ImGui.
- Location: `src/main/kotlin/llm/slop/liquidlsd/ui`
- Contains: `UIManager`, panel classes/objects, ImGui widgets, file/asset helpers, theme/font management, patch-grid state.
- Depends on: ImGui Java bindings, rendering domain objects, `PatchManager`, `PlayQueueManager`, MIDI/audio state.
- Used by: `Main.kt` and user interaction callbacks.

**Persistence and Models:**
- Purpose: Convert mutable runtime state into stable serialized DTOs and back.
- Location: `src/main/kotlin/llm/slop/liquidlsd/models`, `src/main/kotlin/llm/slop/liquidlsd/patches`
- Contains: `DeckPatchDto`, `GlobalPatchDto`, `SessionStateDto`, `PlaylistDto`, `PatchManager`, playlist/queue managers.
- Depends on: kotlinx.serialization JSON, `Deck`, `Mixer`, `ModulatableParameter`, local filesystem under `presets/`.
- Used by: UI panels, startup/session restore, Auto VJ queue flow.

**Modulation and CV:**
- Purpose: Evaluate parameter modulation from time, beat, audio, generated CV, and MIDI sources.
- Location: `src/main/kotlin/llm/slop/liquidlsd/parameters`, `src/main/kotlin/llm/slop/liquidlsd/cv`
- Contains: `ModulatableParameter`, `CvModulator`, enums, waveform math, `CVRegistry`, CV source implementations, evaluator functions.
- Depends on: `MidiEngine` for `midi_cc_*` source reads and `CVRegistry` histories.
- Used by: rendering domain objects, patch grid UI, mixer/deck controls, audio publishing.

**Audio and MIDI Input:**
- Purpose: Feed external signal values into CV/modulation and patch queue controls.
- Location: `src/main/kotlin/llm/slop/liquidlsd/audio`, `src/main/kotlin/llm/slop/liquidlsd/midi`
- Contains: JACK client, audio DSP, watchdog, system volume helper, Java MIDI input handling, MIDI mapping profiles.
- Depends on: `org.jaudiolibs.jnajack`, Java MIDI, atomic/concurrent collections, `CVRegistry`, `UITheme`.
- Used by: `Main.kt`, `UIManager.kt`, `CVRegistry`, `MidiMappingManager`.

**Resources and Presets:**
- Purpose: Supply static shaders, fonts, default patches, docs, and data-driven visual sources.
- Location: `src/main/resources`, `presets`, `docs`
- Contains: `src/main/resources/shaders`, `src/main/resources/fonts`, `src/main/resources/patches/default.json`, `presets/sources/*`, docs source files.
- Depends on: Gradle resource processing and `generateDocs` task in `build.gradle.kts`.
- Used by: `Shader.fromResources`, `UITheme.loadFonts`, `VisualSourceRegistry.loadAll`, `DocManager`.

## Data Flow

### Primary Frame Path

1. Start `main()` and initialize preset directories, MIDI profile, GLFW, OpenGL, dynamic visual sources, UI, renderer, decks, mixer, session, audio, and watchdogs (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:24`).
2. Poll native events once per frame (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:200`).
3. Drain patch load queues and apply DTOs to `Mixer`/`Deck` objects on the main thread (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:220`, `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt:239`).
4. Update registered CV sources and histories (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:223`, `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt:147`).
5. Apply MIDI mappings to parameter base values (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:229`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt:101`).
6. Update and render Deck A, Deck B, and Deck PV through `Renderer.renderDeck()` (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:233`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt:305`).
7. Update mixer parameters and composite Deck A/B into `mixer.masterFBO` (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:245`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt:396`).
8. Blit the master output into the main framebuffer when background/clean mode requires it (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:252`).
9. Render ImGui controls and popups (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:271`, `src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt:260`).
10. Swap the main and optional secondary output buffers (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:273`, `src/main/kotlin/llm/slop/liquidlsd/Main.kt:295`).

### Dynamic Visual Source Loading

1. `VisualSourceRegistry.loadAll()` clears previous sources and scans `presets/sources` (`src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt:51`).
2. Each source folder must provide `meta.json` and `shader.frag`; optional `shader.vert` overrides the default vertex shader (`src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt:62`).
3. `SourceMeta`/`ParamMeta` are decoded into `DynamicVisualSource` parameter maps (`src/main/kotlin/llm/slop/liquidlsd/rendering/DynamicVisualSource.kt:8`).
4. The master source owns its shader; `Deck` clones source instances and shares shader objects without owning them (`src/main/kotlin/llm/slop/liquidlsd/rendering/DynamicVisualSource.kt:41`, `src/main/kotlin/llm/slop/liquidlsd/rendering/DynamicVisualSource.kt:76`).

### Patch Load/Save Path

1. UI or queue logic calls async load/save functions (`src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt:507`, `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt:173`).
2. Background tasks read or write JSON files and enqueue decoded DTOs; they must not mutate active GL/UI/domain state directly (`src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt:159`, `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt:206`).
3. `Main.kt` calls `PatchManager.applyPendingPatches(mixer)` each frame to apply queued DTOs on the main thread (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:220`).
4. DTO conversion functions in `PatchModels.kt` map `Deck`, `Mixer`, `ModulatableParameter`, and `CvModulator` to serialized models and back (`src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt:330`, `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt:390`, `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt:479`).
5. Shutdown saves session state to `presets/last_session.json` (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:322`, `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt:296`).

### Audio/CV Path

1. `AudioEngine.start()` creates `JackClient` and passes `processAudio` as the JACK process callback (`src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt:318`, `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt:82`).
2. JACK invokes `AudioEngine.processAudio()` on the real-time audio thread (`src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt:354`).
3. DSP extracts amplitudes, beat estimate, onset, accent, and pushes values to `CVRegistry.updateBeatAnchor()` and `CVRegistry.updatePushedValue()` (`src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt:354`, `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt:76`, `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt:103`).
4. The render thread calls `CVRegistry.updateAll()` once per frame to update generated CV sources and histories (`src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt:147`).
5. `ModulatableParameter.evaluate()` reads CV/MIDI values and stores evaluated values in history (`src/main/kotlin/llm/slop/liquidlsd/parameters/ModulatableParameter.kt:61`).

### MIDI Path

1. `MidiEngine` opens input devices at object initialization and installs `MidiInputReceiver` receivers (`src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt:8`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt:33`).
2. Receiver callbacks write CC values into `AtomicIntegerArray` and enqueue `(channel, cc)` into `receivedCcEvents` (`src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt:14`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt:148`).
3. `UIManager.render()` drains `receivedCcEvents` each frame for MIDI learn and queue triggers (`src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt:273`).
4. `MidiMappingManager.update(mixer)` applies mapping profile values during the frame loop (`src/main/kotlin/llm/slop/liquidlsd/Main.kt:229`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt:101`).

**State Management:**
- Runtime state is mutable in memory and centered on `Mixer`, three `Deck` instances, source parameter maps, `CVRegistry`, singleton managers, and `UITheme`.
- Persistent state is JSON DTOs in `presets/patches`, `presets/playlists`, `presets/midi`, and `presets/last_session.json`.
- Thread handoff state uses `ConcurrentLinkedQueue`, `AtomicReference`, `AtomicIntegerArray`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, and selected `@Volatile` fields.

## Key Abstractions

**VisualSource:**
- Purpose: Common contract for anything renderable as a visual source.
- Examples: `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSource.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/DynamicVisualSource.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Mandala.kt`
- Pattern: Interface plus cloneable mutable implementations with parameter maps and resource disposal.

**DynamicVisualSource:**
- Purpose: Data-driven shader visual source created from preset folders.
- Examples: `src/main/kotlin/llm/slop/liquidlsd/rendering/DynamicVisualSource.kt`, `presets/sources/mandelbulb/meta.json`, `presets/sources/mandelbulb/shader.frag`
- Pattern: Master instance owns `Shader`; cloned deck instances share shader and own only per-instance state like feedback FBOs.

**Deck:**
- Purpose: One visual chain including source, clean FBO, feedback FBOs, source selection, and feedback parameters.
- Examples: `src/main/kotlin/llm/slop/liquidlsd/rendering/Deck.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt:305`
- Pattern: Mutable domain object updated once per frame, then rendered by `Renderer`.

**Mixer:**
- Purpose: Composite Deck A and Deck B, hold preview Deck PV, and expose global modulation controls.
- Examples: `src/main/kotlin/llm/slop/liquidlsd/rendering/Mixer.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt:396`
- Pattern: Aggregate root for global patch DTOs and UI control surfaces.

**ModulatableParameter:**
- Purpose: Base value plus CV/MIDI modulators, randomization bounds, clamp range, meter type, and history.
- Examples: `src/main/kotlin/llm/slop/liquidlsd/parameters/ModulatableParameter.kt`, `src/main/kotlin/llm/slop/liquidlsd/parameters/CvModulator.kt`
- Pattern: Mutable per-frame evaluation object; do not replace it when UI can mutate it in place.

**Patch DTOs:**
- Purpose: Stable serialized models for deck/global/session/playlist persistence.
- Examples: `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt`
- Pattern: Kotlin serialization `@Serializable` data classes plus extension converter functions.

**Managers and Registries:**
- Purpose: Singleton coordination for global services and shared state.
- Examples: `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`
- Pattern: Kotlin `object` singletons; restrict writes to their documented owning thread or use their provided concurrent handoff API.

## Entry Points

**Desktop Application:**
- Location: `src/main/kotlin/llm/slop/liquidlsd/Main.kt`
- Triggers: Gradle application plugin with `mainClass.set("llm.slop.liquidlsd.MainKt")` in `build.gradle.kts`.
- Responsibilities: Native startup, frame loop, shutdown.

**Main Render Loop:**
- Location: `src/main/kotlin/llm/slop/liquidlsd/Main.kt:198`
- Triggers: Runs until `glfwWindowShouldClose(window)` is true.
- Responsibilities: Event polling, queued state application, CV/MIDI updates, rendering passes, ImGui render, buffer swaps, frame cap.

**JACK Process Callback:**
- Location: `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt:82`
- Triggers: JACK audio server invokes process callback after client activation.
- Responsibilities: Fetch input buffer and call `AudioEngine.processAudio()`.

**MIDI Input Receiver:**
- Location: `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt:133`
- Triggers: Java MIDI device emits a `ShortMessage`.
- Responsibilities: Store CC value atomically and queue the event for render-thread processing.

**ImGui UI Frame:**
- Location: `src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt:260`
- Triggers: `Main.kt` calls `uiManager.render()` every frame.
- Responsibilities: Drain MIDI learn events, process queue triggers, rebuild fonts if requested, draw menu/layout/popups/browsers, submit ImGui draw data.

**Dynamic Visual Source Loader:**
- Location: `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt:51`
- Triggers: `Main.kt` startup.
- Responsibilities: Scan `presets/sources`, compile shaders, create available visual source instances.

**Docs Generation:**
- Location: `build.gradle.kts`
- Triggers: Gradle `processResources` depends on `generateDocs`.
- Responsibilities: Build MkDocs output into `src/main/resources/docs` when `mkdocs` is installed.

## Architectural Constraints

- **Threading:** GLFW polling, OpenGL context manipulation, GL resource creation/destruction, ImGui frame work, and domain state mutation must stay on the main thread in `src/main/kotlin/llm/slop/liquidlsd/Main.kt`. This follows `.agents/skills/lwjgl_thread_restriction/SKILL.md`.
- **Audio callback:** Code on the JACK process path in `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt:82` and `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt:354` must avoid blocking calls, logging, object allocation, file IO, network IO, and locks. Pre-allocate buffers and pass values through `CVRegistry` atomic/concurrent APIs. This follows `.agents/skills/jack_callback_safety/SKILL.md`.
- **ImGui native memory:** ImGui changes in `src/main/kotlin/llm/slop/liquidlsd/ui` must use scoped/native allocation patterns and avoid leaking native pointers across frames. Prefer existing panel/widget patterns and `MemoryStack.stackPush().use { ... }` when native structures are needed. This follows `.agents/skills/imgui_memory_management/SKILL.md`.
- **Global state:** Singletons are intentional and central: `PatchManager`, `CVRegistry`, `VisualSourceRegistry`, `AudioEngine`, `MidiEngine`, `MidiMappingManager`, `PlayQueueManager`, `PlaylistManager`, `UITheme`, and `DocManager`.
- **Resource ownership:** `VisualSourceRegistry` master instances own shared shaders; deck clones must not dispose shared shaders. `Deck.dispose()` disposes FBOs and cloned source resources but avoids double-freeing the active source (`src/main/kotlin/llm/slop/liquidlsd/rendering/Deck.kt:153`).
- **Persistence compatibility:** DTOs contain version fields and legacy mapping paths. Preserve conversion compatibility in `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt` when changing patch schemas.
- **Circular imports:** No explicit circular dependency tool output was detected. Architectural coupling is bidirectional by package intent in several places: `ui` imports rendering/patches/audio/midi and managers call back into rendering DTOs. Avoid adding new cross-package callbacks from background threads into `ui` or `rendering`.
- **Filesystem paths:** Runtime preset paths are relative (`presets/...`), so code should resolve consistently from the process working directory unless introducing an application data directory pattern across all callers.

## Anti-Patterns

### Mutating GL/UI State Off The Main Thread

**What happens:** Background work touches `Deck`, `Mixer`, `VisualSource`, ImGui state, GLFW, or OpenGL objects directly.
**Why it's wrong:** OpenGL/GLFW context and ImGui frame state are main-thread native resources; cross-thread mutation can crash drivers or corrupt UI/rendering state.
**Do this instead:** Decode/load in background and enqueue DTOs through `PatchManager` queues, then apply in `PatchManager.applyPendingPatches(mixer)` from `src/main/kotlin/llm/slop/liquidlsd/Main.kt:220`.

### Allocating Or Logging Inside JACK Callback

**What happens:** Code in `AudioEngine.processAudio()` or callbacks reachable from `JackClient.setProcessCallback` performs allocations, logging, locks, or IO.
**Why it's wrong:** The JACK callback is real-time; nondeterministic work causes xruns and can destabilize the audio server.
**Do this instead:** Pre-allocate buffers in `AudioEngine`, write scalar CV values through `CVRegistry.updatePushedValue()`, and use background analysis tasks only after copying into pre-allocated buffers (`src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt:354`).

### Disposing Shared Dynamic Source Shaders From Clones

**What happens:** A cloned `DynamicVisualSource` calls `shader.dispose()` or is marked `ownsShader = true`.
**Why it's wrong:** Deck clones share shader programs owned by registry master instances, so disposing from a clone can invalidate other decks or the registry.
**Do this instead:** Keep `ownsShader = false` in clone implementations and dispose shared shaders through `VisualSourceRegistry.disposeAll()` (`src/main/kotlin/llm/slop/liquidlsd/rendering/DynamicVisualSource.kt:76`, `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt:40`).

### Bypassing DTO Converters For Patch Persistence

**What happens:** Patch/session code manually writes partial maps or directly serializes runtime objects.
**Why it's wrong:** Runtime objects contain native resources, live parameter histories, legacy compatibility mapping, and cloned source ownership details that should not be serialized.
**Do this instead:** Add fields to DTOs in `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt` and update `toDto()`/`applyDto()` extension functions.

### Adding New Visuals Only As Class Code

**What happens:** A new shader visual is hardcoded into `Renderer` or `Deck` without `presets/sources` metadata.
**Why it's wrong:** The application already supports data-driven shader source loading and source selection through `VisualSourceRegistry`.
**Do this instead:** Add a folder under `presets/sources/<source_id>/` with `meta.json` and `shader.frag`; only create Kotlin rendering code when the visual requires a new runtime abstraction.

## Error Handling

**Strategy:** Catch native/device/filesystem errors at subsystem boundaries, log with Kotlin Logging, and keep the app running in fallback mode where possible.

**Patterns:**
- Startup failures for required GLFW/window/OpenGL pieces throw `RuntimeException` from `src/main/kotlin/llm/slop/liquidlsd/Main.kt`.
- JACK connection failures are caught and degrade to silent/fallback mode in `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt:59`.
- JACK callback errors are counted atomically and logged later by a scheduled background logger, not directly from the callback (`src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt:31`).
- Patch load/save/apply errors are caught and logged around each async or queued operation (`src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`).
- Dynamic shader compile failures install an error fallback shader rather than skipping the entire source (`src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt:82`).

## Cross-Cutting Concerns

**Logging:** Kotlin Logging with Logback. Use `private val logger = KotlinLogging.logger {}` or class-local loggers as in `src/main/kotlin/llm/slop/liquidlsd/Main.kt` and `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`.

**Validation:** Mostly boundary-level validation: source folders require `meta.json` and `shader.frag`; file managers validate patch/playlist files; DTO application tolerates missing legacy fields with defaults. Relevant files include `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, and `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt`.

**Authentication:** Not applicable. This is a local desktop application without network authentication.

**Native Resource Cleanup:** Use explicit `dispose()` methods on `Renderer`, `Deck`, `Mixer`, `VisualSourceRegistry`, `Geometry`, and `UIManager`. Keep cleanup paths in `src/main/kotlin/llm/slop/liquidlsd/Main.kt` current when adding new native resources.

**Concurrency:** Use queue/atomic handoffs already present in `PatchManager`, `MidiEngine`, `CVRegistry`, and `JackClient`. Avoid introducing locks on render-frame hot paths or audio callback paths.

---

*Architecture analysis: 2026-07-07*
