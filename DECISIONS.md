# Architectural Decisions - Liquid LSD

This document outlines the key architectural decisions made in the development of Liquid LSD, detailing the context, options considered, and the rationale behind each choice.

## Distribution Packaging, Zip Permissions, and Self-Healing Source Extraction (`build.gradle.kts`, `VisualSourceRegistry.kt`, `Main.kt`)

- **Decision**: Package the complete `library/` folder into all release ZIP archives and thumb drive bundles, enforce POSIX `755` executable permissions across all Unix shell scripts and JRE binaries via Gradle `FileCopyDetails.permissions`, and bundle default visual sources into the fat JAR classpath (`default_sources/`) for automatic runtime self-healing:
  - **Gradle 9 File Permissions Fix (`build.gradle.kts`)**: In Gradle 9, calling `filePermissions { unix("755") }` within an `eachFile { ... }` block resolves to the outer `Zip` task rather than mutating the individual `FileCopyDetails` instance. Replaced with `permissions { unix("755") }` on `FileCopyDetails` for all `.sh`, `.command`, and `bin/` executables (`java`, `jspawnhelper`).
  - **Distribution Packaging (`build.gradle.kts`)**: Updated `packageThumbDrive` and all platform distribution tasks (`zipWindows`, `zipLinux`, `zipLinuxArm`, `zipMacArm`, `zipMacIntel`) to copy and include `library/**` (sources, presets, playlists, and MIDI profiles).
  - **Classpath Bundling & Self-Healing Extraction (`VisualSourceRegistry.kt`, `processResources`)**: Configured `tasks.processResources` to package `library/sources` into the JAR under `default_sources/`. If `library/sources/mandala` or default sources are missing on disk at startup (e.g. running standalone fat JAR or unbundled executions), `VisualSourceRegistry.ensureDefaultSources` extracts the bundled default sources automatically into `library/sources/`.
  - **Clear Error Messaging (`Main.kt`)**: Updated the startup failure exception message from `presets/sources/mandala` to `library/sources/mandala`.
- **Rationale**:
  - Eliminates the need for users to manually run `chmod +x run-linux.sh` or `chmod +x run-mac-*.command` after extracting release zip archives.
  - Fixes startup crashes caused by missing visual source definitions (e.g., `RuntimeException: Mandala source not loaded...`).
  - Provides multi-layered defense-in-depth: the pre-built distribution contains user-editable sources out-of-the-box, and standalone JAR executions self-heal automatically without crashing.

## Default-Disabled Policy for Sequencer, Randomization, and MIDI Subsystems (`AppSettings.kt`, `SettingsPanel.kt`, `UITheme.kt`, `PresetGridPanel.kt`, `CellConfigPanel.kt`, `MidiEngine.kt`)

- **Decision**: Configure the Step Sequencer, Parameter Randomization, and MIDI hardware/mapping subsystems to be disabled by default (`sequencerEnabled = false`, `randomizationEnabled = false`, `midiEnabled = false`), and provide explicit master toggles in Settings:
  - **Sequencer Master Toggle**: Added `sequencerEnabled` in `AppSettings` / `UITheme`, exposed in **Settings** $\to$ **General** and **Settings** $\to$ **Preset Grid**. When disabled, parameter evaluation skips sequencer modulators, `CVRegistry.get("seq")` returns 0.0f, and sequencer columns/tabs are hidden.
  - **Randomization Default**: Changed default `randomizationEnabled` from `true` to `false` in `AppSettings`.
  - **MIDI Master Toggle**: Added `midiEnabled` in `AppSettings` / `UITheme`, exposed in **Settings** $\to$ **MIDI & Controls**. When disabled, device enumeration and watchdog polling are suppressed, open MIDI devices are closed, incoming CC queues are cleared, the "MIDI Map" menu action is disabled, and MIDI modulators evaluate as neutral.
- **Rationale**:
  - **Deterministic Initial State**: Users launching the application for the first time or starting a new session are not confronted with unpredictable randomized parameter drift, unexpected MIDI input intercepting desktop controls, or complex sequencing dynamics running before they have intentionally configured them.
  - **Reduced Resource Consumption**: Suppressing background MIDI device polling, queue draining, and sequencer evaluation until enabled saves unnecessary background thread cycles and USB bus probes on systems without MIDI hardware.
  - **Consistent Subsystem Symmetry**: Brings Step Sequencer and MIDI subsystems into architectural symmetry with `audioEngineEnabled`, where subsystems default to safe states and expose clear master enable toggles with automatic UI column/tab synchronization.

## macOS JRE Bundle Hierarchy & GLFW Main Thread Dispatch (`build.gradle.kts`, `run-mac-arm.command`, `run-mac-intel.command`)

- **Decision**: Accommodate macOS-specific JRE bundle layouts and Cocoa runtime threading constraints in desktop launcher generation and Gradle tasks:
  - **macOS Bundle Directory Structure Resolution**: Adoptium `.tar.gz` distributions for macOS package JRE binaries inside a standard macOS bundle structure (`Contents/Home/bin/java`). The launcher generation scripts (`run-mac-arm.command` and `run-mac-intel.command`) now probe both `jre/macos-<arch>/Contents/Home/bin/java` and flat `jre/macos-<arch>/bin/java` paths before attempting system Java fallback.
  - **Thread 0 JVM Dispatch (`-XstartOnFirstThread`)**: macOS Cocoa requires that GLFW event loop initialization and window message polling execute strictly on the primary OS thread (Thread 0). Both the bundled JRE execution paths and system Java fallback in `run-mac-*.command`, as well as Gradle `JavaExec` tasks when `os.name` contains `mac`, now pass `-XstartOnFirstThread`.
  - **Native Access Warning Suppression (`--enable-native-access=ALL-UNNAMED`)**: Passed to all launcher scripts and `JavaExec` tasks to ensure clean JVM startup without JNI/Unsafe deprecation warnings under modern JDKs (JDK 21+).
  - **Gatekeeper Quarantine Stripping**: Automatically executes `xattr -dr com.apple.quarantine jre 2>/dev/null || true` inside the `.command` launcher scripts to prevent macOS Gatekeeper from blocking execution of bundled JRE binaries extracted from downloaded zip archives.
  - **Zip Permissions Preservation**: Configured `zipMacArm` and `zipMacIntel` Gradle tasks to set `755` permissions across all binaries in `bin/`, `jspawnhelper`, and `.command` launchers.
- **Rationale**:
  - Eliminates the `IllegalStateException: GLFW may only be used on the main thread` crash when launching on macOS Apple Silicon or Intel.
  - Fixes false-positive "Bundled JRE not found" errors that caused the app to fall back to whatever system Java was installed on the user's Mac.
  - Provides a frictionless double-click launch experience on macOS Finder without Gatekeeper quarantine roadblocks.


## Explicit Randomization Disabling for Mixer Randomizer Parameters (`ModulatableParameter.kt`, `Mixer.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `ValueParamSection.kt`, `ModulatorHeaderRow.kt`, `PresetGridRenderer.kt`)

- **Decision**: Explicitly prohibit parameter and modulator randomization on the five Mixer randomization controllers (`Mixer/randDeckA`, `Mixer/randDeckB`, `Mixer/randDeckBG`, `Mixer/randDeckPV`, and `Mixer/randAll`):
  - **Engine Level Gating (`ModulatableParameter.kt`)**: Added `isRandomizeDisabled: Boolean = false` to `ModulatableParameter`. When true, `randomizeBase` is strictly gated to `false`, and `randomizeBaseValue()` is a no-op.
  - **Mixer Parameter Declarations (`Mixer.kt`)**: Declared `randDeckA`, `randDeckB`, `randDeckBG`, `randDeckPV`, and `randAll` with `isRandomizeDisabled = true`.
  - **UI Controls & Tooltip Feedback (`CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `ValueParamSection.kt`, `ModulatorHeaderRow.kt`, `PresetGridRenderer.kt`)**:
    - Dimmed dice toggle buttons (0.25f text alpha) for initial value ranges and modulators driving randomizer parameters.
    - Ignored click and right-click toggle actions on disabled dice.
    - Disabled `"Randomize row"` in the Preset Grid context menu for these parameters.
    - Added contextual tooltip: `"It is forbidden to randomize the randomizer. Chaos would ensue."`
- **Rationale**:
  - **Prevents Destabilizing Control Feedback Loops**: If a modulator (such as an LFO driving continuous morphing on `randDeckA`) could have its own variables randomized during the morph cycle, each boundary crossing would re-seed its own clock/waveform, creating an unrecoverable recursive feedback loop that causes tempo jitter, audio-rate state thrashing, or complete lockups.
  - **Eliminates Dead & Misleading UI**: Previously, these parameters were omitted from `getAllRandomizableParameters()` under the hood, but the UI still permitted toggling random ranges on them, leading to user confusion when the ranges were ignored.
  - **Clear Communicative UX**: The distinct tooltip clearly explains *why* the dice buttons are disabled rather than leaving users wondering if the interface is unresponsive.

## Universal 2D/3D View Pipeline & Contextual Parameter Visibility (`Renderer.kt`, `Deck.kt`, `view2d.frag`, `PresetGridTabs.kt`)

- **Decision**: Establish a clean separation between universal 2D/3D spatial parameters (`Zoom`, `Rotate Z`) and 3D-projection-specific parameters (`Rotate X`, `Rotate Y`, `3D Persp`, `Depth Dim`, `Separation`, `Blend Mode`):
  - **Universal 2D View Pass (`view2d.frag`, `rawSource2DFBO`)**:
    - When `3D Mode < 0.5`, the active visual source renders directly into `rawSource2DFBO` at full native widescreen resolution (`width x height`).
    - A dedicated 2D view transformation shader (`view2d.frag`) executes over a fullscreen quad, mapping coordinates centered at `(0.5, 0.5)`, applying aspect-ratio-corrected isotropic rotation along the Z axis (`uRotateZ` / Roll), dividing by `uZoom` for continuous scaling ($0.1\times$ to $5.0\times$, with $1.0$ being pixel-identical 1:1 scale), and outputting transparent black (`vec4(0.0)`) for coordinates sampled outside the $[0, 1]$ canvas bounds.
    - Blends the transformed source into `cleanFBO` before entering the feedback loop.
  - **3D Mode Preservation (`tri_planar.vert`, `rawSourceFBO`)**:
    - When `3D Mode >= 0.5`, sources render into square 1:1 `rawSourceFBO` (`height x height`) and project onto 3 or 6 orthogonal planes via `tri_planar.vert` & `tri_planar.frag`.
  - **Contextual UI Visibility & Ordering (`PresetGridTabs.kt`)**:
    - `Zoom` and `Rotate Z` are always visible at the top of the `View` subgroup, followed by `3D Mode`.
    - `Rotate X` (Pitch) and `Rotate Y` (Yaw) are hidden when `3D Mode < 0.5`, grouped with the other 3D parameters (`3D Persp`, `Depth Dim`, `Separation`, `Blend Mode`).
    - Full parameter registration in `Deck.getParameterPaths()` is retained so preset loading and modulation routings for `Rotate X` and `Rotate Y` persist across modes.
- **Rationale**:
  - Eliminates misleading "dead" UI controls: `Rotate X` and `Rotate Y` have no geometric meaning in a flat 2D projection.
  - Restores essential 2D rotation and zoom capabilities to flat generators (Mandala, Lissajous, shaders) without reintroducing source-specific duplicate parameters.
  - Aspect-ratio correction prevents non-square circular distortion during 2D rotation.
  - Full native-resolution `rawSource2DFBO` avoids the horizontal downsampling that would occur if using the square 1:1 `rawSourceFBO` in 2D mode.

---

## Mandala Architecture Unification as DynamicVisualSource (`Mandala.kt`, `PresetGridTabs.kt`, `PresetModels.kt`, `WebPresetSerializer.kt`)

- **Decision**: Completely unify `Mandala` into the generic `DynamicVisualSource` framework, removing hardcoded `if (source is Mandala)` / `if (mandala != null)` special cases across the serialization, UI, and preset model layers:
  - **Single Generic Presets Path (`Deck.applyDto`)**: Presets serialize and deserialize `Mandala` strictly using the standard `ModulatableParameter` map (`Lobes`, `Recipe Select`, `L1`–`L4`, etc.). Removed `MandalaRecipeDto` and the redundant `recipe` field on `DeckPresetDto`. When a preset loads, `Mandala.update()` evaluates `Lobes` and `Recipe Select` and restores the matching Fourier ratio automatically.
  - **Dynamic Parameter & Tab Decomposition (`meta.json`, `PresetGridTabs.kt`)**: Reordered and streamlined `library/sources/mandala/meta.json` so generator parameters appear in natural logical order (`Lobes`, `Recipe Select`, `L1`–`L4`, `Thickness`, `Hue Offset`, `Hue Sweep`, `Depth`). Removed legacy hardcoded Mandala transform parameters (`Zoom`, `Rotate Z`, `Rotate Y`, `Rotate X`, `3D Persp`), eliminating duplicate controls in the `View` subtab and delegating all spatial transformation and 3D projection to the universal Deck View pipeline.
  - **Universal MIDI & Parameter Addressing**: Removed the legacy custom `Mandala.getParameterPaths()` override (which produced non-standard group paths like `Deck A/Geometry/L1`); all Mandala controls now follow the canonical format (`Deck A/Mandala/L1`, `Deck A/Mandala/Gain`).
  - **Lightweight Web Broadcast Serialization (`WebPresetSerializer.kt`)**: Replaced the 3-way branching `serializeDeck` with a single generic loop over `src.parameters`, accompanied by a 4-line extension emitting Mandala recipe frequencies (`a`, `b`, `c`, `d`) to preserve full WebGL2 client compatibility.
- **Rationale**:
  - Treats Mandala as a first-class `DynamicVisualSource`, reducing technical debt, code duplication, and UI fragility.
  - Eliminates hardcoded visual source branching in `PresetGridTabs.kt` and `PresetGridPanel.kt`.
  - Preserves 100% backward compatibility for existing `.lsd` files (ignoring legacy `recipe` fields gracefully) and WebGL2 TV clients.

---

## Continuous Constrained Random Morphing & Flip-Flop State Latches (`MorphState.kt`, `Deck.kt`, `Mixer.kt`)

- **Decision**: Transform discrete one-shot randomization triggers (`Mixer/randDeckA`, `randDeckB`, `randDeckBG`, `randDeckPV`, `randAll`) into continuous $0.0 \leftrightarrow 1.0$ morphing controllers:
  - **Two-State Snapshot Model ($S_0 \leftrightarrow S_1$)**: Each deck and mixer maintains two state snapshots (`state0` and `state1`) storing base values and all active modulator parameters (`depth`, `subdivision`, `phaseOffset`, `slope`, `morph`, `hold`, `dcOffset`, etc.).
  - **In-Place Zero-Allocation Lerp**: Converted mutable runtime modulator fields from `val` to `var` in `CvModulator.kt`, allowing per-frame interpolation without allocating objects on the render thread or churning `CopyOnWriteArrayList`.
  - **Flip-Flop Boundary State Machine with Hysteresis**:
    - Ascending towards $1.0$ ($V \ge 0.99$): Latch transitions to `READY_FOR_ZERO` and re-rolls $S_0$ as the new target. Visuals remain static while held at $1.0$.
    - Descending towards $0.0$ ($V \le 0.01$): Latch transitions to `READY_FOR_ONE` and re-rolls $S_1$ as the new target. Visuals remain static while held at $0.0$.
    - Boundary hysteresis guarantees that noisy analog CV or LFO signals wobbling near extremes do not trigger duplicate rolls.
  - **Selective Per-Parameter & Per-Modulator Randomization Gating**: Base values and active modulator properties are interpolated if and only if their respective `randomize*` flags (`param.randomizeBase`, `mod.randomizeDepth`, etc.) are enabled. When disabled, parameters freeze at their current values and state snapshots continuously synchronize to active values, ensuring that manual UI slider adjustments are preserved and never clobbered by morph updates.
  - **Unidirectional Wrap-Around State Promotion**: When driven by monotonic ramps (such as a Sawtooth LFO or `beatPhase` rising $0.0 \to 1.0$), detecting a rapid reset ($V_{\text{prev}} \ge 0.7 \to V \le 0.3$) rotates states in-place: $S_0 \leftarrow S_1$ and $S_1 \leftarrow \text{sampleNewState()}$. Because $\text{lerp}(S_0, S_1, 0.0) = S_0 = S_1$, this completely eliminates turnaround deceleration, peak pauses, and jump cuts, providing seamless, infinite forward visual flow.
  - **Shortest-Path Angle & Hue Interpolation**: Angular parameters (`isAngle`) and circular meters (`MeterType.ENDLESS`) use shortest-path wrapping modular arithmetic to prevent unwinding artifacts across periodic boundaries.
- **Rationale**:
  - Eliminates UI explosion: reuses existing `randDeckX` parameters directly without adding new interval/hold/target sliders.
  - Generative synergy: combining `randDeckA` with LFO waveforms featuring `Hold` (`RANDOM` / `TRIANGLE` with `hold > 0`) automatically delivers customizable morph-then-hold generative evolutions synchronized to beat clock or time.

---

## Unified Title Bar & Custom Window Frame (CSD) (`WindowFrameController.kt`, `MenuBar.kt`)

- **Decision**: Replace the traditional OS title bar with a modern integrated Unified Header & Title Bar (Client-Side Decorations / CSD) combining cross-platform borderless window management with an optional native OS decoration fallback:
  - **Option A (Pure Cross-Platform GLFW / ImGui CSD)**: Implemented `WindowFrameController` to handle window dragging via empty header bar space, double-click maximize/restore toggling, custom window controls (minimize `Icons.MINUS`, maximize/restore `Icons.SQUARE`/`Icons.COPY`, close `Icons.X`), and 4-border/4-corner perimeter hit-testing with dynamic cursor switching (`GLFW_HRESIZE_CURSOR`, `GLFW_VRESIZE_CURSOR`) and minimum bounds clamping (`800x600`).
  - **Option B (Configurable Native Fallback)**: Added `framelessWindow: Boolean = true` to `AppSettings` (persisted in `lsd-settings.properties`) and a toggle in `SettingsPanel` (`Window Frame & Chrome`). When disabled, `Main.kt` passes `GLFW_DECORATED = GLFW_TRUE`, allowing users on tiling window managers (like i3/sway) to use standard OS decorations while hiding the custom window action buttons in `MenuBar`.
- **Rationale**:
  - Reclaims 30–40px of precious vertical screen space on desktop screens, merging menus, telemetry HUD, recording/broadcast controls, and window actions into a single cohesive ~32px top bar.
  - Pure GLFW/ImGui implementation avoids fragile platform-specific native JNI bindings or OS hook complexity while maintaining 100% portability across Linux (X11/Wayland), macOS, and Windows.

---

## Harmonized 5-Column CV Modulation Palette & Central Theme Unification (`CvTheme.kt`)

- **Decision**: Redesign the CV modulation color scheme across `PresetGridPanel`, `CellConfigPanel`, and `AudioEnginePanel` to span 5 distinct quadrants of the color wheel with high-contrast, anti-aliased luminance calibrated for dark backgrounds:
  - **Pruned 5-Column Distribution**:
    - **`VAL` (Value / Base)**: Crisp Mint Cyan (`#00F2B8`, `rgb(0.00, 0.95, 0.72)`) — Ground/anchor reference.
    - **`MIDI` (MIDI CC)**: Bright Orchid Violet (`#B873FF`, `rgb(0.72, 0.45, 1.00)`) — External controller bindings.
    - **`LFO` (Synthetic Oscillators)**: Electric Sky Blue (`#26BFFF`, `rgb(0.15, 0.75, 1.00)`) — Flowing wave generators.
    - **`AUD` (Audio Followers)**: Warm Amber Gold (`#FFAE1F`, `rgb(1.00, 0.68, 0.12)`) — Acoustic energy / VU meter warmth.
    - **`TRIG` (Transient Triggers)**: Hot Coral Rose (`#FF4080`, `rgb(1.00, 0.25, 0.50)`) — Punchy onset/accent transients.
  - **Sub-Band Family Harmonies**: Sub-signals under Audio and Trigger naturally extend their respective color families (Audio RMS Amber, Bass Deep Orange, Mid Golden Amber, High Bright Gold; Trigger Onset Coral Pink, Trigger Accent Crimson Rose).
  - **Single Source of Truth (`CvTheme.kt`)**: Replaced duplicate, hardcoded, and out-of-sync color mappings in `PresetGridPanel.kt`, `CellConfigPanel.kt`, and `AudioEnginePanel.kt` with centralized `CvTheme.getThemeColor()` and `CvTheme.getThemeColorRGB()` calls.
  - **Theme-Adaptive Grid Cells (`PresetGridRenderer.kt`)**: Removed per-column cell knob/needle/arc tinting in favor of inheriting the parameter's native theme text color (`ImGuiCol.Text`). This ensures consistent legibility, clean neutral cell backgrounds, and seamless adaptability across all light and dark theme palettes (Solarized, Lunarized, Neon, Boring) while keeping column headers and `CellConfigPanel` tabs vibrantly color-coded.
- **Rationale**:
  - The previous palette originated from a 12-column matrix where adjacent columns clustered into similar lime greens and deep purples. When pruned to 5 columns, `VAL` (mint) and `AUD` (lime) looked nearly identical, while `MIDI` and `TRIG` were dark/clashing purples.
  - The new 5-column palette evenly distributes hues (~38° Gold, ~165° Mint, ~202° Sky Blue, ~270° Violet, ~340° Coral Rose), ensuring instantaneous visual recognition, excellent text readability, and complete thematic coherence across all modulation panels.
  - Neutralizing per-column cell coloring prevents visual noise and low-contrast clashes against custom theme backgrounds.

---

## Decoupled Audio-Rate CV Oscilloscope History & Frame-Delta Beat Extrapolation

- **Decision**: Decouple sound-derived CV oscilloscope history buffers from the UI render loop and replace hard monotonic clamping in the visual beat clock with frame-delta forward extrapolation:
  - **Audio-Rate Direct History Writes (`AudioEngine.kt`)**: Eagerly cache direct `CvHistoryBuffer` references on `AudioEngine` (`ampHistory`, `bassHistory`, `midHistory`, `highHistory`, `onsetHistory`, `accentHistory`) and append historical samples directly within `processAudio()` at the audio block rate (~86–344 Hz). This bypasses the UI render loop, making audio oscilloscopes immune to UI frame drops, GC hiccups, and render-thread latency spikes.
  - **Block Duration Timestamp Alignment**: Publish beat anchors with `currentTime + blockDurationNs` to synchronize the anchor timestamp with the end-of-block beat position (`totalBeats += deltaTimeSec * (effectiveBpm / 60.0)`).
  - **Single-Source Appending (`CVRegistry.kt`)**: Filter out audio and trigger sources in `CVRegistry.updateAll()` (`isAudioOrTriggerSource(source.id)`) to eliminate double-sampling and redundant map queries on the render thread.
  - **Nominal Frame-Delta Extrapolation (`CVRegistry.getSynchronizedTotalBeats()`)**: Replace the static flatline clamp (`safeBeats = current`) during backwards time jitter with forward progression based on elapsed render frame time (`current + frameDtSec * (bpm / 60.0)`). Reset `lastRenderTimeNs` on `resetBeatAnchor()` to prevent spurious phase jumps on track seeking or tempo resets.
- **Rationale**:
  - Eliminates periodic visual freezing and stuttering across all oscilloscopes, `beatSine`, and beat-synchronized LFO modulators.
  - Preserves strict zero-allocation, lock-free real-time audio thread safety on JACK and Java Sound backends.

---

## Automated Continuous Beta Releases & Release Notes Generation

- **Decision**: Automate the creation and publishing of GitHub beta releases and release notes on every push to `main`:
  - **Push Triggers**: Configure `.github/workflows/release.yml` to trigger on `push: branches: [ main ]`, `push: tags: [ 'v*' ]`, and `workflow_dispatch`.
  - **Sequential Concurrency**: Enforce concurrency grouping (`cancel-in-progress: false`) to ensure sequential, non-colliding releases.
  - **Dynamic Beta Versioning**: On branch push, introspect existing tags, determine the latest `v1.0.0-beta.N` tag, calculate `v1.0.0-beta.(N+1)`, tag the commit via `github-actions[bot]`, and push the tag.
  - **Automated Changelog & Resilient Release Notes Extraction**: Automatically extract rich release notes from `docs/release_notes.md`, `RELEASE_NOTES.md`, or alternative changelog files. The extractor checks for an exact version match, an `[Unreleased]` staging block, or falls back to the topmost version block in the file (normalizing the header), and prepends these human-friendly highlights above the compiled commit history with links and authors. Releases are named directly as `${TAG_NAME}` (e.g., `v1.0.0-beta.38`) with `make_latest: true`.
- **Rationale**:
  - Eliminates manual release creation and keeps human-curated documentation synchronized with rapid push-based releases even when the exact beta tag count outpaces manual version bumps.
  - Guarantees immediate binary distribution builds (`windows-x64`, `linux-x64`, `linux-arm64`, `macos-arm64`, `macos-x64`) for rapid testing across all platforms.

---

## Play Queue and Background Queue Feature & Interaction Parity

- **Decision**: Symmetrize modulation, auto-advance triggers, dirty-state protection, navigation controls, and interaction models between the A/B Play Queue and Background Queue:
  - **Modulation & MIDI CC Parity**: Added `Mixer/bgQueuePrev` and `Mixer/bgQueueNext` modulatable parameters with MIDI CC inputs (`Global/bgQueuePrev`, `Global/bgQueueNext`) and modulation grid rows in the Mixer tab.
  - **Title Bar Navigation Controls**: Added `<` and `>` quick navigation buttons directly to the title bars of both Play Queue and Background Queue.
  - **Deck BG Dirty Checking**: Symmetrically checks `PresetManager.isDeckDirty` on `Deck BG` during all queue advances and transitions, respecting `UITheme.autoVjDirtyBehavior` (`SKIP`, `AUTO_SAVE`, `AUTO_DISCARD`).
  - **Double-Click Playback Parity**: Double-clicking any track in the Play Queue targets the standby deck and smoothly auto-fades to it (`playIndex(index, mixer)`), matching the immediate dip-to-black double-click playback in Background Queue.
  - **Uniform Drop Target Styling**: Pushed transparent drag-and-drop target styling across all 4 library columns to eliminate intrusive yellow highlight bounding boxes.
- **Rationale**:
  - Provides a consistent, predictable mental model for VJs managing dual-deck A/B foreground visuals and background visual sets.
  - Enables hands-free MIDI and generative CV control over background playlist progression.

---

## Mandala Background Parameter Extraction into Standalone "Colors" Visual Source

- **Decision**: Remove legacy background parameters (`Bg Style`, `Bg Feedback`, `Bg Hue`, `Bg Sat`, `Bg Val`, `Bg Sweep`, `Bg Speed`, `Bg Zoom`) and custom background rendering passes from the `Mandala` visual source and `Renderer.kt`, packaging the background solid-color and plasma generation capabilities into a new standalone visual source called **Colors** (`library/sources/colors/`):
  - **Standalone Generator (`Colors`)**: Standardized as a `DynamicVisualSource` with parameters `Style` (0 = Off, 1 = Solid, 2 = Plasma), `Hue`, `Sat`, `Val`, `Sweep`, `Speed`, and `Zoom`.
  - **Pipeline Simplification**: Eliminates custom secondary background rendering passes in `renderMandala()` and `renderDeckFeedback()`, as well as `cleanFBO` texture overrides in `Deck.getOutputTexture()`.
  - **Multi-Deck Compositing**: Leveraging the dedicated `Deck BG` compositing layer, any background styling (solid colors, animated plasma, or other visual sources) is now composed uniformly via the mixer rather than hardcoded inside individual geometric generators.
- **Rationale**:
  - Mandala was built prior to the dedicated 4-deck architecture and had ad-hoc background uniforms embedded directly inside its parameter set and renderer.
  - Decoupling visual generators maintains the single-responsibility principle and lets users route `Colors` (or any other generator) to any deck (`Deck A`, `Deck B`, `Deck BG`, or `Deck PV`).

---

## Beat Tracker (BeatTrackerEngine) Real-Time Engine & Continuous Modulation Generator

- **Decision**: Replace heuristic beat detection with a stateful, zero-allocation beat tracking engine modeled on **BTrack** (Adam Stark) and the Dan Ellis causal dynamic programming model:
  - **Complex Spectral Difference ODF**: 512-point Radix-2 Cooley-Tukey FFT with pre-allocated twiddle factors and Hann windowing. Evaluates 2nd-order phase trajectory prediction to detect pitched attacks and percussive transients while suppressing steady-state tones.
  - **Two-State Multi-Band Periodicity Estimation**: State 1 (Acquisition) searches 40–200 BPM across circular history buffers; State 2 (Locked Tracking) constrains search to $\pm 15\%$ around current tempo period with $\pm 2.0$ BPM/beat human tracking inertia and harmonic comb unwrapping.
  - **Decoupled Periodic Autocorrelation**: Decouples multi-second autocorrelation calculation from the per-block rate to run periodically every 4 blocks (~46 ms, ~21.5 Hz) with physical time-step scaling ($dt_{\text{interval}} = dt \cdot 4$), slashing loop iterations by 75% without compromising tempo lock speed or accuracy.
  - **Causal Dynamic Programming Recurrence**: Circular cumulative score buffer evaluating causal DP recurrence with pre-tabulated $\log(\tau)$ tables (`logTauTable`) to eliminate transcendental functions from the real-time audio thread.
  - **Subnormal / Denormal Float Flushing (`BiquadFilter.kt`)**: Flushes biquad filter recursive state variables (`z1`, `z2`) to zero when $|\text{state}| < 10^{-15}\text{ f}$, eliminating hardware microcode exceptions and CPU pipeline stalls when audio decays toward silence.
  - **Continuous Phase & Cosine Generator**: Outputs continuous normalized phase $\phi(t) \in [0.0, 1.0)$ and locked cosine modulation signal $\cos(2\pi \phi(t))$ via zero-allocation queries (`getPhase`, `getCosine`, `getPhaseAndCosine`, `getPhaseAndCosinePacked`).
- **Rationale**:
  - Eliminates visual phase stutter and snapping during tempo adjustments or syncopated drum breaks.
  - Guarantees strict zero-allocation real-time safety and prevents subnormal floating-point stalls on JACK/PipeWire audio callback threads and 60–144Hz+ rendering loops, preventing audio buffer underruns (XRUNs).

---

## 4-Deck Architecture (Deck BG & Deck PV), 2x2 Monitor Grid, and 4-Column Library

- **Decision**: Evolve the rendering and UI architecture from a 3-deck model (A, B, C) to a dedicated 4-deck pipeline (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`):
  - **Background Compositing Layer (`Deck BG`)**: Rendered in GLSL (`mixer.frag`) directly beneath the crossfaded A/B foreground (`rgb = fg.rgb + bg.rgb * (1.0 - fg.a)`), allowing transparent and additive foreground visuals to float naturally over dynamic background visuals.
  - **Dedicated Preview Deck (`Deck PV`)**: Dedicated preview deck `Deck PV` for visual monitoring and preparation without affecting master output.
  - **2x2 Preview Monitor Matrix**: Arranged deck monitors in a symmetrical 2x2 grid (Top: A & B; Bottom: BG & PV) with interactive toolbars, letter badges, drag-and-drop routing, and individual deck theme coloring.
  - **4-Column Library**: Organized the browser panel into 4 balanced columns: Presets | Playlists | A/B Play Queue | Background Queue (`BgQueueManager`).
  - **Unified Top Action Toolbar**: Eliminated cluttered inline row buttons in favor of a top action bar (`[A] [B] [BG] [PV] [Q] [BGQ] [+]`) with mutual selection between Presets and Playlists.
  - **Background Queue Engine**: Built a single-deck dip-to-black state transition machine for automated or manual background cycling.
- **Rationale**:
  - Solves the visual clutter problem of having separate load buttons on every preset row when multiple deck targets exist.
  - Provides a dedicated background layer essential for multi-layer generative visual performance.
  - Symmetrical 2x2 monitor grid maximizes screen estate and preserves equal aspect ratios for all decks.

---

## Per-Modulator Audio Envelope Followers & Dual-Trace Oscilloscope

- **Decision**: Implement independent per-modulator audio envelope followers with selectable dynamics presets and contextual custom sliders:
  - **Independent per-band / per-modulator**: Each audio band modulator (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`) maintains its own runtime envelope follower state rather than forcing a global smoothing parameter across all visuals.
  - **Preset Dropdown Workflow**: Offer musically tuned presets (`Raw (Instant Jitter)`, `Punchy (Fast)`, `Smooth Swell`, `Slow Pulse`, `Ambient Drift`, and `Custom`). Hardcode Attack and Decay numbers in presets while hiding sliders to keep the interface minimal.
  - **Seamless Custom Transition**: Selecting `Custom` automatically populates the Attack and Decay sliders with the current active preset numbers (e.g. transitioning from `Ambient Drift` sets Attack to $250\text{ ms}$ and Decay to $1500\text{ ms}$).
  - **Dual-Trace Oscilloscope Feedback**: The parameter's audio oscilloscope renders the raw incoming audio band energy in a faint ghosted trace ($35\%$ alpha) beneath the solid smoothed follower curve, fully respecting live vs. muted color styling.
- **Rationale**:
  - Eliminates visual audio jitter on parameters that require smooth organic breathing (like zoom, rotation, or color drift) while preserving instant reactive jitter where desired.
  - Zero heap allocation in audio callbacks and render loops; sample-rate and framerate independent.

---

## Auto-VJ Manual Deck Load & Line-Jumping Architecture

- **Decision**: Integrate manual deck preset loading with Auto-VJ and the Play Queue using a non-destructive, staged line-jumping model:
  - **Queue Preservation**: Manual deck loading when Auto-VJ is OFF never modifies queue contents or the active index.
  - **Standby Staging ("Jump the Line")**: When a preset is manually loaded into the inactive/standby deck while Auto-VJ is active, that deck is marked as staged. The next Auto-VJ trigger initiates an automated crossfade directly to the staged preset without overwriting it from the queue, preserving the next queue track for the subsequent cycle.
  - **Active Deck Overrides**: Manually loading into the live deck replaces the output immediately while keeping Auto-VJ armed and the queue index untouched.
  - **Seamless Mid-Set Arming**: Enabling Auto-VJ mid-set does not trigger immediate jump cuts; it waits for the next advance trigger (CV pulse, MIDI CC, beat trigger, or UI button) to load into the standby deck.
  - **Deck PV Isolation**: Manual interactions with Deck PV (master overlay) remain completely independent of the A/B Auto-VJ pipeline.
- **Rationale**:
  - Matches industry-standard DJ/VJ workflows (Traktor Pro Cruise mode, VirtualDJ Automix).
  - Eliminates accidental queue mutation during manual performance and avoids jarring visual jump cuts.

---

## GUI Scale: Dynamic System DPI Detection & User Zoom Factor Separation

- **Decision**: Separate GUI scaling into dynamic runtime OS DPI detection (`systemDpiScale`, via `glfwGetWindowContentScale` and `glfwSetWindowContentScaleCallback`) and persistent user zoom preference (`guiScalePercent`, 75%–200%, 5% steps). The effective base font size is dynamically computed as:
  $$\text{Effective Base Size (px)} = 15.0\text{px} \times \left(\frac{\text{guiScalePercent}}{100}\right) \times \text{systemDpiScale}$$
- **Rationale**:
  - **Dynamic Multi-Monitor & DPI Adaptation**: Users frequently connect laptops to external 4K / 150% / 200% displays or switch display resolutions. Detecting `systemDpiScale` on every launch and tracking window movement across monitors via `glfwSetWindowContentScaleCallback` prevents UI elements from shrinking or blowing up.
  - **Separation of Preferences**: The persistent settings file stores the user's relative scaling preference (`guiScalePercent`, default 100%) rather than an absolute pixel size baked on one specific monitor.
  - **Automatic Font Atlas Rebuild**: When DPI changes, the ImGui font atlas is dynamically rebuilt and uploaded to the GPU, keeping typography sharp and correctly proportioned across varying pixel densities.
  - **Discrete Steps**: 5% steps keep the slider tactile and prevent blurry fractional font rasterization in the Dear ImGui atlas.

---

## 1. Tech Stack Selection (Kotlin/JVM + LWJGL + JNAJack)
- **Decision**: Build the VJ system using **Kotlin/JVM** on top of **LWJGL 3 (GLFW + OpenGL 3.3)** for graphics and **JNAJack** for Linux audio.
- **Rationale**: 
  - **Kotlin/JVM** provides excellent development velocity, strong typing, and rich ecosystem support (e.g. serialization, logging).
  - **LWJGL 3** gives us thin, high-performance bindings to native windowing (GLFW) and graphics (OpenGL), bypassing heavy Java 2D or JavaFX graphics layers.
  - **JNAJack** provides direct, low-latency access to JACK/PipeWire sound servers, which is crucial for real-time audio analysis.

---

## 2. Zero-Allocation & Non-Blocking Audio Callback
- **Decision**: Enforce a strict zero-allocation, non-blocking rule within the JACK audio processing thread (e.g. `AudioEngine` callback).
- **Rationale**: 
  - The JACK callback runs inside a real-time system thread managed by the host OS audio server. Any blockage (I/O, locks, logging) or non-deterministic CPU pause (JVM Garbage Collection allocation sweeps) can cause buffer underruns, resulting in audible stutters/dropouts (xruns) or server crashes.
  - All buffers, filter banks, and memory arrays used in audio analysis are pre-allocated during initialization.

---

## 3. Z Garbage Collector (ZGC) for Latency Control
- **Decision**: Mandate launching the JVM with ZGC (`-XX:+UseZGC`) and a low pause-time target (`-XX:MaxGCPauseMillis=2`).
- **Rationale**: 
  - The default G1 garbage collector can introduce pause times of 10-100ms. Since a typical low-latency audio buffer of 128 frames at 48kHz must process in **2.6ms**, a GC pause of even 3ms will guarantee an xrun.
  - ZGC performs concurrent GC phases, keeping JVM pauses under 1ms, safely below the real-time audio thread budget and visual frame budgets (16.6ms for 60Hz).

---

## 4. Single-Threaded Windowing and OpenGL (Thread 0)
- **Decision**: Bind the OpenGL context and all GLFW window operations strictly to the primary OS thread (Thread 0).
- **Rationale**: 
  - Operating system window managers (particularly macOS and Linux X11/Wayland) require window events and event polling to run on the thread that created the window.
  - Calling OpenGL functions from multiple threads or off-thread triggers driver faults and immediate native JVM crashes (segfaults).

---

## 5. Lock-Free Audio-to-Render & Audio-to-Worker Data Passing
- **Decision**: Avoid mutexes/locks (`ReentrantLock`, `synchronized`, blocking queues) on the real-time audio thread. Instead, pass data from the audio thread to the rendering thread using `@Volatile` primitive fields (`anchorBeats`, `anchorBpm`, `anchorTimeNs`), the custom single-writer `CvHistoryBuffer` ring-buffer, and lock-free Single-Producer Single-Consumer (`SpscQueue`) ring buffers for live audio session recording (`RealtimeRecorder`).
- **Rationale**: 
  - Locking on the audio thread can cause **priority inversion**, where a lower-priority rendering or background worker thread holding the lock blocks the real-time audio thread.
  - Lock-free structures keep all threads decoupled and wait-free; transient data races in visualization buffers (like the oscilloscope) cause at most a single-frame visual glitch rather than an application-wide crash or xrun.

---

## 6. Explicit ImGui Native Memory Management
- **Decision**: Explicitly allocate and free native `imgui-java` structures (e.g. calling `.destroy()` on styles and font configs, and caching `ImString` buffers as class fields).
- **Rationale**: 
  - `imgui-java` is a JNI wrapper around a C++ immediate-mode library. Standard JVM GC does not track or clean up C++ heap allocations.
  - Allocating ImGui objects (like `ImString` or `ImInt`) per-frame on the heap results in severe native memory leaks. Failing to keep a JVM reference to font arrays while native ImGui references them triggers JVM SegFaults.

---

## 7. Cross-Platform Audio Capture with JACK and Java Sound (TargetDataLine) Fallback
- **Decision**: Support both JACK/PipeWire and Java Sound (`TargetDataLine`) as audio backends. JACK is the preferred primary audio backend on Linux, while Java Sound serves as a cross-platform fallback on macOS, Windows, and Linux if JACK is absent.
- **Rationale**: 
  - **JACK/PipeWire** provides the native, ultra-low-latency pipeline (<3ms buffer sizes) and visual patchbay routing (e.g. `Helvum`, `qpwgraph`) critical for professional Linux VJs who need to route audio between applications (e.g. from Bitwig/Reaper into Liquid LSD).
  - **Java Sound (`TargetDataLine`)** is part of the standard JDK and runs out-of-the-box on macOS, Windows, and JACK-less Linux configurations without native dependencies, ensuring the visuals remain fully audio-reactive across all OSes.
  - On Linux, supporting both allows pro-audio users to benefit from JACK's superior routing and low-latency, while providing a seamless, config-free setup for casual desktop users.

---

## 8. Decoupled Asynchronous Preset IO
- **Decision**: Execute all preset saving/loading (`.lsd` JSON files) on a dedicated daemon background thread (`PresetManager-IO` executor) and pass the loaded DTOs to the main thread via thread-safe queues.
- **Rationale**: 
  - File I/O is slow and blocking. Saving or loading a preset on the main rendering thread would cause noticeable frame drops during a live VJ performance.
  - Loading on a background thread keeps rendering smooth, and using thread-safe queues ensures that applying loaded presets happens atomically at the start of the next render frame, preventing OpenGL state corruption.

---

## 9. Terminology Standardisation: Preset & Unified Library Folder
- **Decision**: Refactor visual parameter snapshots from 'Patch' to 'Preset' (e.g., `PresetManager`, `DeckPresetDto`, `PresetGridPanel`), and rename the top-level user storage directory from `presets/` to `library/` (containing `library/presets/`, `library/midi/`, `library/playlists/`, `library/sources/`, `library/last_session.json`).
- **Rationale**: 
  - Aligns with VJ / audio industry standards (Resolume, Ableton, TouchDesigner) where 'patch' refers to modular node graphs, whereas saved parameter snapshots are called 'presets'.
  - Eliminates path redundancy (e.g. `presets/patches` or `presets/presets`) by establishing `library/` as the single root folder for user assets.

---

## 10. Streamlined Beta Architecture: Zero Backwards Compatibility Shims
- **Decision**: Remove all legacy migration logic, serialization aliases (`@SerialName`), fallback file path resolvers (`.json`/`.patch`), directory migrations (`presets/` -> `library/`), and parameter name translation dictionaries across models, session state, and UI.
- **Rationale**: 
  - During early beta development, maintaining backwards compatibility shims and dual-path code introduces maintenance overhead, dead code branches, and unnecessary complexity.
  - Streamlining the codebase to use canonical naming (`depth`, `presetNotes`, `library/presets/*.lsd`) ensures high readability, clean domain boundaries, and zero legacy bloat.

---

## 11. Standalone WebGL2 Core Renderer Architecture (Phase 1)
- **Decision**: Port the core desktop multi-pass OpenGL rendering pipeline to WebGL2 and GLSL ES 3.0 as a vanilla, standalone web client in `web/` without bundlers, transpilers, or npm dependencies.
- **Rationale**:
  - Eliminates build system complexity and dependency fragility, enabling instant evaluation via any static web server or browser.
  - Maintains 1:1 parity with desktop GLSL shaders (Mandala ribbon, Dynamic Spiral, Feedback ping-pong, and Mixer compositing) while utilizing standard WebGL2 floating-point framebuffers (`RGBA16F` with `EXT_color_buffer_float`).

---

## 12. Browser-Side Web Audio DSP & Icecast Streaming (Phase 2)
- **Decision**: Implement real-time audio analysis and beat tracking in `web/dsp.js` using the standard Web Audio API connected to the live `radio.spaz.org:8060` Icecast stream via `AudioContext` and `createMediaElementSource`.
- **Rationale**:
  - Direct client-side streaming and analysis removes the need for backend DSP or WebSocket relays.
  - Multi-band biquad filtering (lowpass, bandpass, highpass) + RMS peak followers yield zero-allocation per-frame CV metrics (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`).
  - Dual-average onset detection with IOI history tracking generates synchronized beat phase and sine modulation at 60fps render cadence.

---

## 13. Retro TV Shell & CRT Post-Processing Pipeline (Phase 3)
- **Decision**: Wrap the standalone WebGL2 visualizer in a retro CRT TV bezel (`tv.css`, `ui.js`) with physical power switch, rotary volume dial, and single-pass CRT post-processing fragment shader (`crt_post.frag`).
- **Rationale**:
  - Encapsulating the player in a retro TV housing with an interactive power switch fulfills browser autoplay user-gesture policies organically without generic modal dialogs.
  - Consolidating barrel curvature, RGB phosphor shadow mask triad, scanlines, chromatic aberration, corner vignette, cold-state animated static noise, and expanding-raster warmup sequence into a single fragment shader pass (`crt_post.frag`) minimizes GPU overhead and replaces the generic final blit.
  - Gating multi-pass render passes (Passes 1–4) when powered off eliminates wasteful GPU computation while displaying animated static noise.
  - Rotary dial volume control applies a squared attenuation curve ($V^2$) via Web Audio `GainNode.setTargetAtTime` for natural, perceptually linear volume adjustment.

---

## 14. WebSocket Relay Server & 24/7 Autopilot Fallback (Phase 4)
- **Decision**: Implement a Node.js WebSocket relay server (`server/server.js`) with bearer token authentication for broadcasters (`role=broadcast&key=...`) and fan-out distribution to web viewers, paired with a client-side 24/7 Autopilot playlist scheduler (`web/autopilot.js`).
- **Rationale**:
  - Keeps the backend stateless and lightweight: caches the latest `state_full` JSON payload in memory and fans out updates to thousands of viewers without transcoding or heavy computing.
  - Autopilot engine guarantees 24/7 autonomous visuals on the Web TV client using fade-through-black master alpha transitions when no live broadcaster is connected.
  - Viewers automatically switch to the live broadcast upon receiving `state_full` and revert to autopilot upon `broadcaster_offline`.

---

## 15. Desktop Broadcaster Subsystem & Throttled Delta Streaming (Phase 5)
- **Decision**: Implement `BroadcastEngine` and `WebPresetSerializer` in the desktop app using Java 11+ `java.net.http.WebSocket` running asynchronously on a dedicated daemon executor (`BroadcastEngine-IO`), with rate-limited parameter delta streaming (~25 Hz).
- **Rationale**:
  - Decoupling network I/O from the main GLFW/OpenGL thread and JACK audio thread ensures zero dropped frames and zero audio dropouts (xruns).
  - Emitting full state snapshots (`state_full`) upon connection/preset loads and compact diff patches (`state_delta`) during live knob/fader adjustments minimizes network bandwidth while keeping live interactions immediate and snappy.
  - Dedicated "Web Broadcast" settings tab in `SettingsPanel.kt` and menu bar status HUD give the VJ clear visual confirmation of live status and one-click toggle control.

---

## 16. Desktop-to-Web Asset Synchronization & Manifest Drift Tracking (Phase 6)
- **Decision**: Establish desktop shaders and Kotlin algorithms as the authoritative source of truth, backed by `web/sync_manifest.json`, the `scripts/sync_web.py` CLI engine, and Gradle/CI verification tasks (`checkWebSync`, `syncWeb`, `WebSyncTest.kt`).
- **Rationale**:
  - Mechanical GLSL translation (converting `#version 330 core` to WebGL2 `#version 300 es` + `precision highp float;`) eliminates redundant manual shader copy-pasting and prevents syntax drift between platforms.
  - Tracking SHA-256 hashes of algorithmic Kotlin sources (`Icosahedron.kt`, `Evaluators.kt`, `WebPresetSerializer.kt`) ensures developers are immediately warned with exact file paths and instructions whenever desktop math logic changes without a corresponding update to web JavaScript equivalents.
  - Zero-dependency Python CLI coupled with standard Gradle `Exec` tasks and JUnit tests guarantees drift detection is caught in development, pre-commit, and CI pipelines without adding heavy external dependencies.

---

## 17. Audio Engine Settings & Real-Time Monitor Consolidation
- **Decision**: Consolidate the Audio Engine hardware controls, backend routing, beat detection settings, and real-time oscilloscopes into the dedicated "Audio Engine" category tab inside `SettingsPanel.kt`, while maintaining the modular implementation in `AudioEnginePanel.kt`.
- **Rationale**:
  - Eliminates redundant overlapping popup modals by unifying hardware configuration with real-time waveform and CV monitoring in one consistent location.
  - Top menu bar "Audio Engine" item routes directly into the Settings modal focused on the Audio Engine tab for instant 1-click access.
  - Clean separation of concerns: `AudioEnginePanel.kt` encapsulates all zero-allocation oscilloscope rendering, primitive state arrays, and audio UI logic.

---

## 18. Pure JVM/Gradle Static Site & Documentation Generator (`greenjon.com`)
- **Decision**: Implement a native Kotlin/JVM static site generator (`SiteGenerator.kt`) executed via Gradle (`./gradlew buildWebsite` / `./gradlew exportGreenjon`) to compile `docs/`, `RELEASE_NOTES.md`, and project metadata into a self-contained `./greenjon/` distribution folder ready for FTP upload.
- **Rationale**:
  - **Zero External Tooling**: Eliminates dependencies on external Python (`mkdocs`), Node.js, or Ruby engines for generating the product website and web documentation.
  - **Single Source of Truth**: Project version, release URLs, and documentation are pulled directly from `build.gradle.kts` and `docs/`, ensuring the website and downloadable documentation package (`docs.zip`) remain continuously in sync with codebase releases.
  - **Self-Contained & Relative**: All generated HTML pages, CSS stylesheets, SVGs, and scripts use relative asset paths, allowing immediate drag-and-drop deployment via FTP to `greenjon.com` or any static web host.

---

## 19. Renderer Polymorphic Draw Topology (`drawTopology()`)
- **Decision**: Eliminate source-type branching (`is Mandala`, `is HyperMesh`) from `Renderer.kt` by introducing an open `drawTopology()` method on `DynamicVisualSource`, delegating vertex attribute binding and primitive drawing to each individual visual generator.
- **Rationale**:
  - **Zero Source Knowledge in Renderer**: `Renderer.render()` is collapsed into a single, unified render path that only binds framebuffers, evaluates common uniforms (`uAlpha`, `uResolution`, `uTime`, `uAspectRatio`), calls `source.drawTopology()`, and performs feedback ping-pong / blitting.
  - **Encapsulated GPU Geometries**: `Mandala` encapsulates its own triangle-strip ribbon VAO/VBO lifecycle (`initGeometry()`, `dispose()`, and handle sharing on `clone()`), and `HyperMesh` encapsulates its dual 600-cell and 120-cell strut/node draw calls.
  - **Zero Regression**: Preserves 100% visual and mathematical equivalence across all generators while making future custom mesh or geometry-based visual sources cleanly pluggable without modifying `Renderer.kt`.



