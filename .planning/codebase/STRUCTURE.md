# Codebase Structure

**Analysis Date:** 2026-07-07

## Directory Layout

```text
liquid-lsd-desktop/
├── .agents/                 # Project-local agent skills and repository agent guidance
│   └── skills/              # Native ImGui, JACK, and LWJGL constraint docs
├── .github/                 # GitHub repository automation/configuration
├── .planning/               # GSD planning and generated codebase maps
│   └── codebase/            # STRUCTURE.md and other generated codebase maps live here
├── docs/                    # MkDocs source documentation
│   ├── developer/           # Developer architecture/audio/rendering docs
│   └── user_guide/          # User-facing concepts and workflow docs
├── gradle/wrapper/          # Gradle wrapper JAR/properties
├── presets/                 # Runtime preset data and shader visual source folders
│   └── sources/             # Dynamic visual source metadata and shaders
├── src/
│   ├── main/
│   │   ├── kotlin/llm/slop/liquidlsd/  # Kotlin application source
│   │   └── resources/                # Fonts, shaders, default patch, logback config
│   └── test/kotlin/llm/slop/liquidlsd/ # Kotlin tests
├── build.gradle.kts         # Gradle build, dependencies, app entry point, packaging tasks
├── settings.gradle.kt       # Gradle root project name
├── mkdocs.yml               # Documentation site configuration
├── README.md                # Project overview
├── ARCHITECTURE.md          # Root architecture overview
├── TODO.md                  # Project TODO notes
└── local.properties         # Local machine Gradle properties
```

## Directory Purposes

**`.agents/`:**
- Purpose: Store project-specific agent instructions and skills.
- Contains: `.agents/AGENTS.md`, `.agents/skills/imgui_memory_management/SKILL.md`, `.agents/skills/jack_callback_safety/SKILL.md`, `.agents/skills/lwjgl_thread_restriction/SKILL.md`.
- Key files: `.agents/skills/imgui_memory_management/SKILL.md`, `.agents/skills/jack_callback_safety/SKILL.md`, `.agents/skills/lwjgl_thread_restriction/SKILL.md`.

**`.planning/`:**
- Purpose: Store GSD planning artifacts and generated codebase maps.
- Contains: `.planning/codebase/`.
- Key files: `.planning/codebase/STRUCTURE.md`, `.planning/codebase/CONCERNS.md`.

**`docs/`:**
- Purpose: Source content for MkDocs documentation and packaged help docs.
- Contains: High-level docs in `docs/*.md`, developer docs in `docs/developer`, user docs in `docs/user_guide`.
- Key files: `docs/developer/architecture.md`, `docs/developer/audio_dsp.md`, `docs/developer/rendering.md`, `docs/user_guide/custom_visuals.md`.

**`gradle/`:**
- Purpose: Gradle wrapper support files.
- Contains: `gradle/wrapper/`.
- Key files: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`.

**`presets/`:**
- Purpose: Runtime user/application preset storage and dynamic source definitions.
- Contains: `presets/sources/*/meta.json`, `presets/sources/*/shader.frag`, optional `presets/sources/*/shader.vert`; runtime-created directories include `presets/patches`, `presets/playlists`, and `presets/midi`.
- Key files: `presets/sources/mandala/meta.json`, `presets/sources/mandala/shader.frag`, `presets/sources/mandala/shader.vert`.

**`src/main/kotlin/llm/slop/liquidlsd/`:**
- Purpose: Main Kotlin/JVM application package.
- Contains: `Main.kt` and domain subpackages for audio, CV, MIDI, models, parameters, patches, rendering, UI, and utilities.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/Main.kt`.

**`src/main/kotlin/llm/slop/liquidlsd/audio/`:**
- Purpose: Audio capture, JACK integration, DSP, beat detection, system volume, and watchdog coordination.
- Contains: `AudioEngine.kt`, `JackClient.kt`, `BiquadFilter.kt`, `AmplitudeExtractor.kt`, `MidiJackWatchdog.kt`, `SystemAudioVolume.kt`.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt`, `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt`.

**`src/main/kotlin/llm/slop/liquidlsd/cv/`:**
- Purpose: Control voltage source registry, generated CV sources, histories, beat clocks, and modulator evaluation functions.
- Contains: `CVRegistry.kt`, `CVSource.kt`, `CvHistoryBuffer.kt`, `Evaluators.kt`, `BeatClock.kt`, `GenCVSource.kt`, `LFO.kt`, `SampleAndHold.kt`.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt`, `src/main/kotlin/llm/slop/liquidlsd/cv/Evaluators.kt`.

**`src/main/kotlin/llm/slop/liquidlsd/midi/`:**
- Purpose: Java MIDI input handling and MIDI mapping profiles.
- Contains: `MidiEngine.kt`, `MidiMappingManager.kt`.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`.

**`src/main/kotlin/llm/slop/liquidlsd/models/`:**
- Purpose: Serializable DTO models, runtime-to-DTO converters, clipboard data.
- Contains: `PatchModels.kt`, `ClipboardManager.kt`.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt`.

**`src/main/kotlin/llm/slop/liquidlsd/parameters/`:**
- Purpose: Modulation parameter types, CV modulator model, enum definitions, resolver helpers, waveform math.
- Contains: `ModulatableParameter.kt`, `CvModulator.kt`, `Enums.kt`, `ParameterResolver.kt`, `WaveformMath.kt`.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/parameters/ModulatableParameter.kt`, `src/main/kotlin/llm/slop/liquidlsd/parameters/CvModulator.kt`.

**`src/main/kotlin/llm/slop/liquidlsd/patches/`:**
- Purpose: Patch/session persistence, playlist persistence, active play queue behavior.
- Contains: `PatchManager.kt`, `PlaylistManager.kt`, `PlayQueueManager.kt`.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt`.

**`src/main/kotlin/llm/slop/liquidlsd/rendering/`:**
- Purpose: OpenGL rendering, framebuffers, shaders, visual source registry, decks, mixer, Mandala visual.
- Contains: `Renderer.kt`, `Deck.kt`, `Mixer.kt`, `FBO.kt`, `Shader.kt`, `Geometry.kt`, `VisualSource.kt`, `DynamicVisualSource.kt`, `VisualSourceRegistry.kt`, `Mandala.kt`, `MandalaLibrary.kt`, `GLDebug.kt`.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Deck.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Mixer.kt`.

**`src/main/kotlin/llm/slop/liquidlsd/ui/`:**
- Purpose: Immediate-mode UI panels, widgets, asset management, patch grid, theme/fonts, settings, popups, and documentation launcher.
- Contains: 33 Kotlin files including `UIManager.kt`, `UITheme.kt`, `AssetBrowserPanel.kt`, `PatchGridPanel.kt`, `PatchGridRenderer.kt`, `MixerMonitorPanel.kt`, `DeckControlPanel.kt`, `SettingsPanel.kt`.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/AssetBrowserPanel.kt`.

**`src/main/kotlin/llm/slop/liquidlsd/utils/`:**
- Purpose: Small shared utilities.
- Contains: `TimeUtils.kt`.
- Key files: `src/main/kotlin/llm/slop/liquidlsd/utils/TimeUtils.kt`.

**`src/main/resources/`:**
- Purpose: Runtime classpath resources.
- Contains: `logback.xml`, `fonts/`, `patches/default.json`, `shaders/`.
- Key files: `src/main/resources/shaders/blit.vert`, `src/main/resources/shaders/feedback.frag`, `src/main/resources/shaders/mixer.frag`, `src/main/resources/fonts/Inter-Regular.ttf`, `src/main/resources/logback.xml`.

**`src/test/kotlin/llm/slop/liquidlsd/`:**
- Purpose: Unit and regression tests.
- Contains: Package-mirrored tests for patches, rendering, and UI utilities.
- Key files: `src/test/kotlin/llm/slop/liquidlsd/patches/DirtyStateTest.kt`, `src/test/kotlin/llm/slop/liquidlsd/patches/PlayQueueManagerTest.kt`, `src/test/kotlin/llm/slop/liquidlsd/patches/SessionStateTest.kt`, `src/test/kotlin/llm/slop/liquidlsd/rendering/DeckUtilityTest.kt`, `src/test/kotlin/llm/slop/liquidlsd/ui/FontInspectorTest.kt`.

## Key File Locations

**Entry Points:**
- `src/main/kotlin/llm/slop/liquidlsd/Main.kt`: Desktop app entry point, native startup, main loop, secondary window management, and shutdown.
- `build.gradle.kts`: Gradle `application` configuration sets `llm.slop.liquidlsd.MainKt` as the runnable main class.

**Configuration:**
- `build.gradle.kts`: Kotlin/JVM plugins, dependencies, JVM toolchain, app runtime args, docs task, packaging tasks.
- `settings.gradle.kt`: Gradle root project name.
- `mkdocs.yml`: MkDocs documentation configuration.
- `src/main/resources/logback.xml`: Logback logging configuration.
- `local.properties`: Local build properties; keep machine-specific changes out of architectural assumptions.

**Core Logic:**
- `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt`: Render passes and compositing.
- `src/main/kotlin/llm/slop/liquidlsd/rendering/Deck.kt`: Deck visual chain state.
- `src/main/kotlin/llm/slop/liquidlsd/rendering/Mixer.kt`: Global mix state.
- `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt`: Dynamic visual source loading.
- `src/main/kotlin/llm/slop/liquidlsd/parameters/ModulatableParameter.kt`: Runtime parameter evaluation.
- `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt`: CV source registry and histories.
- `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt`: Persistence DTOs and conversion functions.
- `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`: Patch/session save-load orchestration.
- `src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt`: UI frame orchestration.
- `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt`: Audio processing and CV publishing.
- `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt`: MIDI device input and event handoff.

**Resources:**
- `src/main/resources/shaders/`: Built-in shader files loaded from the classpath.
- `presets/sources/`: Data-driven dynamic visual sources loaded from the filesystem.
- `src/main/resources/fonts/`: Fonts used by `UITheme`.
- `src/main/resources/patches/default.json`: Bundled default patch resource.
- `docs/`: MkDocs source, optionally generated into `src/main/resources/docs` by Gradle.

**Testing:**
- `src/test/kotlin/llm/slop/liquidlsd/patches/`: Patch/session/queue regression tests.
- `src/test/kotlin/llm/slop/liquidlsd/rendering/`: Rendering-domain utility tests.
- `src/test/kotlin/llm/slop/liquidlsd/ui/`: UI utility tests.

## Naming Conventions

**Files:**
- Kotlin classes/objects use PascalCase file names matching the main type: `Renderer.kt`, `PatchManager.kt`, `ModulatableParameter.kt`, `UIManager.kt`.
- UI panels end with `Panel` when they draw a major UI area: `AssetBrowserPanel.kt`, `AudioEnginePanel.kt`, `CellConfigPanel.kt`, `MixerMonitorPanel.kt`, `PatchGridPanel.kt`, `SettingsPanel.kt`.
- UI reusable controls/widgets use descriptive component names: `CustomIconButton.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `ImGuiFileBrowser.kt`.
- Persistence DTOs are grouped in `PatchModels.kt`; add closely related patch DTOs/converters there unless the file is intentionally split.
- Tests use `*Test.kt` names and mirror the production package: `DirtyStateTest.kt`, `DeckUtilityTest.kt`.
- Shader resources use lowercase descriptive names with `.vert` and `.frag`: `blit.vert`, `feedback.frag`, `mixer.frag`.
- Dynamic visual source folders use lowercase snake_case identifiers: `presets/sources/pseudo_kleinian`, `presets/sources/clifford_torus`, `presets/sources/attractor_feedback`.

**Directories:**
- Kotlin package directories mirror `llm.slop.liquidlsd` and domain package names: `audio`, `cv`, `midi`, `models`, `parameters`, `patches`, `rendering`, `ui`, `utils`.
- Runtime preset directories use plural noun groups: `presets/sources`, `presets/patches`, `presets/playlists`, `presets/midi`.
- Documentation directories separate developer and user audiences: `docs/developer`, `docs/user_guide`.

## Where to Add New Code

**New OpenGL Render Pass Or Rendering Primitive:**
- Primary code: `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Geometry.kt`, or a new focused file under `src/main/kotlin/llm/slop/liquidlsd/rendering/`.
- Resources: Add shaders under `src/main/resources/shaders/` when they are built-in render infrastructure.
- Tests: Add domain tests under `src/test/kotlin/llm/slop/liquidlsd/rendering/`.
- Constraint: Keep GL calls on the main thread and add explicit `dispose()` cleanup for new GL resources.

**New Data-Driven Visual Source:**
- Primary code: Usually no Kotlin code. Add `presets/sources/<source_id>/meta.json` and `presets/sources/<source_id>/shader.frag`; add `shader.vert` only when the source needs custom vertex behavior.
- Registry: Use existing loading in `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt`.
- Tests: Add validation or source selection tests under `src/test/kotlin/llm/slop/liquidlsd/rendering/` if Kotlin logic changes.

**New Built-In Visual Source Type:**
- Primary code: Add a `VisualSource` implementation in `src/main/kotlin/llm/slop/liquidlsd/rendering/`.
- Registry: Extend `src/main/kotlin/llm/slop/liquidlsd/rendering/VisualSourceRegistry.kt` only if data-driven `DynamicVisualSource` is insufficient.
- Persistence: Update `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt` if serialization needs new fields.

**New UI Panel:**
- Primary code: Add `src/main/kotlin/llm/slop/liquidlsd/ui/<Feature>Panel.kt`.
- Integration: Wire it from `src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt`, `MenuBar.kt`, or the relevant existing panel.
- State: Keep panel-specific transient UI state in the panel or `PatchGridState.kt`; keep app-level settings in `UITheme.kt`.
- Constraint: Draw from the render thread only and follow ImGui native memory guidance from `.agents/skills/imgui_memory_management/SKILL.md`.

**New UI Widget:**
- Primary code: Add `src/main/kotlin/llm/slop/liquidlsd/ui/<WidgetName>.kt`.
- Reuse: Follow patterns in `CustomIconButton.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, and `ModulatorHeaderRow.kt`.

**New Patch/Session Field:**
- DTOs: Add fields and defaults in `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt`.
- Conversion: Update `toDto()` and `applyDto()` extension functions in `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt`.
- Save/load orchestration: Update `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`.
- Tests: Add/update tests in `src/test/kotlin/llm/slop/liquidlsd/patches/`.
- Constraint: Preserve backward compatibility with existing versioned DTOs.

**New Modulation Source Or CV Generator:**
- Primary code: Add a `CVSource` implementation under `src/main/kotlin/llm/slop/liquidlsd/cv/`.
- Registration: Register it in `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt`.
- UI: Expose it in patch grid labels/colors in `src/main/kotlin/llm/slop/liquidlsd/ui/PatchGridPanel.kt` or related patch-grid files.
- Tests: Add evaluation tests under `src/test/kotlin/llm/slop/liquidlsd/cv/` or parameter tests if the directory is introduced.

**New Parameter Or Modulator Behavior:**
- Primary code: `src/main/kotlin/llm/slop/liquidlsd/parameters/ModulatableParameter.kt`, `src/main/kotlin/llm/slop/liquidlsd/parameters/CvModulator.kt`, `src/main/kotlin/llm/slop/liquidlsd/cv/Evaluators.kt`, or `src/main/kotlin/llm/slop/liquidlsd/parameters/WaveformMath.kt`.
- Persistence: Update `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt`.
- UI: Update patch-grid controls under `src/main/kotlin/llm/slop/liquidlsd/ui/`.

**New Audio DSP Feature:**
- Primary code: Add focused DSP helpers under `src/main/kotlin/llm/slop/liquidlsd/audio/` and integrate through `AudioEngine.kt`.
- Constraint: Pre-allocate buffers before the JACK callback path. Do not allocate, log, block, or perform IO inside `AudioEngine.processAudio()`.
- Tests: Add unit tests under `src/test/kotlin/llm/slop/liquidlsd/audio/` if introduced.

**New MIDI Control Feature:**
- Input handling: Use `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt` for raw MIDI device input and event queues.
- Mapping: Use `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt` for persistent control mappings.
- UI learn behavior: Drain queued MIDI events from `src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt` or a delegated render-thread UI component.

**New Playlist Or Queue Behavior:**
- Primary code: `src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt` and `src/main/kotlin/llm/slop/liquidlsd/patches/PlaylistManager.kt`.
- UI integration: `src/main/kotlin/llm/slop/liquidlsd/ui/AssetBrowserPanel.kt` and `src/main/kotlin/llm/slop/liquidlsd/ui/PlaylistManager.kt`.
- Tests: `src/test/kotlin/llm/slop/liquidlsd/patches/PlayQueueManagerTest.kt`.

**New Documentation Page:**
- Source: Add Markdown under `docs/`, `docs/developer/`, or `docs/user_guide/`.
- Navigation: Update `mkdocs.yml`.
- Runtime packaging: Gradle `generateDocs` writes built docs to `src/main/resources/docs` when MkDocs is available.

**Utilities:**
- Shared non-domain helpers: `src/main/kotlin/llm/slop/liquidlsd/utils/`.
- Domain-specific helpers: Keep them in the owning package, for example rendering helpers in `src/main/kotlin/llm/slop/liquidlsd/rendering/` and file asset helpers in `src/main/kotlin/llm/slop/liquidlsd/ui/`.

## Special Directories

**`.agents/skills/`:**
- Purpose: Project-specific safety/convention instructions for agents.
- Generated: No.
- Committed: Yes.

**`.planning/codebase/`:**
- Purpose: Generated codebase maps consumed by GSD planning/execution commands.
- Generated: Yes.
- Committed: Depends on workflow, but documents are intended to be written to the repo.

**`presets/sources/`:**
- Purpose: Dynamic visual source definitions loaded at runtime by `VisualSourceRegistry`.
- Generated: No.
- Committed: Yes for bundled sources.

**`presets/patches/`, `presets/playlists/`, `presets/midi/`:**
- Purpose: Runtime-created user preset, playlist, and MIDI profile storage.
- Generated: Yes at runtime by `Main.kt` and manager classes.
- Committed: Usually no for user-generated data unless intentionally adding bundled presets.

**`src/main/resources/shaders/`:**
- Purpose: Built-in shader resources loaded from the classpath by rendering code.
- Generated: No.
- Committed: Yes.

**`src/main/resources/fonts/`:**
- Purpose: Bundled font resources loaded by `UITheme`.
- Generated: No.
- Committed: Yes.

**`src/main/resources/docs/`:**
- Purpose: Built documentation output for runtime documentation access.
- Generated: Yes by `generateDocs` when MkDocs is available.
- Committed: Only if the project chooses to ship prebuilt docs in resources.

**`build/`:**
- Purpose: Gradle build outputs, distribution packages, cached JRE downloads.
- Generated: Yes.
- Committed: No.

**`.gradle/`:**
- Purpose: Gradle local cache/state.
- Generated: Yes.
- Committed: No.

---

*Structure analysis: 2026-07-07*
