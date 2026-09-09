# Liquid LSD — Release Notes

## [Unreleased]

### Phase 2.2.1: Single-Pass ISF Filter Engine & Deck Pipeline Integration (`VisualEffect.kt`, `ISFFilter.kt`, `ISFFilterRegistry.kt`, `Deck.kt`, `Renderer.kt`, `PresetModels.kt`, `PresetGridTabs.kt`)
- **Modular FX Slot 1**: Integrated a dedicated post-processing FX slot into each Deck's rendering pipeline.
- **ISF Filter Support**: Native support for single-pass ISF (Interactive Shader Format) image filters with automatic parameter mapping.
- **Hardware Dry/Wet Blending**: Efficient signal blending using `glBlendColor` for minimal GPU overhead.
- **Bundled Filters**: Includes Invert, Hue Shift, Posterize, Luma Key, and Edge Detect filters.
- **Preset Grid Controls**: Added filter selection and parameter modulation controls to the FX tab.
- **Serialization**: Full preset serialization and round-trip support for FX slot configurations.

---

## Version 1.0.0-beta.59

> [!NOTE]
> **Release 1.0.0-beta.59** introduces native, first-class Interactive Shader Format (ISF v2.0) visual source support without requiring companion `meta.json` sidecar files, automatic GLSL 3.30 boilerplate injection, support for standalone `.fs`/`.isf` sources, and dual-format manifest testing.

### First-Class ISF Visual Source Support (`ISFModels.kt`, `ISFParser.kt`, `ISFVisualSource.kt`, `VisualSourceRegistry.kt`, `Renderer.kt`, `VisualSourceManifestTest.kt`, `Source3DModeTest.kt`)
- **First-Class ISF Source Format**: Visual source discovery and manifest verification now support standalone ISF files (`.fs`, `.isf`) and folder-based shaders without requiring an accompanying `meta.json` file.
- **ISF GLSL Preprocessor & Code Synthesis**: Added `ISFParser.buildGLSLFragmentShader()` which automatically injects GLSL 3.30 `#version` directives, fragment output mapping (`gl_FragColor`), normalized coordinates (`isf_FragNormCoord`), standard uniforms (`RENDERSIZE`, `TIME`, `TIMEDELTA`, `FRAMEINDEX`, `DATE`, `PASSINDEX`), and input uniforms into the shader source prior to compilation.
- **Support for Vector & Scalar MIN/MAX Schemas**: Enhanced parameter parsing to accept either scalar or vector array bounds for `point2D` and complex inputs.
- **Dual-Format Test Validation**: Updated `VisualSourceManifestTest` and `Source3DModeTest` to inspect either legacy `meta.json` or embedded ISF headers (`/*{ ... }*/`), allowing new visual sources to be pure ISF shaders.
- **ISF 3D Mode & Feedback Flags**: Added `is3D` and `feedback` metadata support to `ISFHeader` and automatic 3D mode classification based on `Rotate X` and `Rotate Y` parameter detection.
- **Colors Source Parity**: Preserved all 7 parameters (`Style`, `Hue`, `Sat`, `Val`, `Sweep`, `Speed`, `Zoom`) and restored full backwards compatibility.

---

## Version 1.0.0-beta.58

> [!NOTE]
> **Release 1.0.0-beta.58** introduces a multi-endpoint video sharing matrix allowing Decks and Master to be streamed simultaneously over GPU shared memory (Spout2 on Windows, native JNA Objective-C Runtime Syphon on macOS, Linux Texture Bridge) with per-endpoint resolution overrides and scaling modes.

### Multi-Endpoint Video Sharing Matrix (`VideoOutputSettings.kt`, `TextureStreamer.kt`, `SettingsPanel.kt`, `UITheme.kt`, `AppSettings.kt`, `Renderer.kt`, `Main.kt`)
- **Video Output Matrix**: Added configuration UI and pipeline routing for streaming individual Deck outputs (Deck A, Deck B, Deck BG, Deck PV) alongside Master output via Spout (Windows), Syphon (macOS), and Linux texture sharing bridges.
- **Native Syphon Bridge (macOS)**: Replaced legacy JNI-based Syphon wrappers with a modern, high-performance Objective-C Runtime bridge using JNA pointers. Supports Intel and Apple Silicon (ARM64) natively.
- **Native Spout2 Bridge (Windows)**: Integrated zero-copy GPU texture sharing using JNA bindings for `SpoutLibrary.dll`.
- **Independent Resolution & Scaling**: Added per-endpoint resolution overrides (`Sync to Master`, `4K UHD`, `1080p`, `720p`, `540p`) and scaling modes (`Fit`, `Fill`, `Stretch`).
- **GPU Rescaling Pipeline**: Implemented blit shader rescaling pass in `Renderer.kt` and `TextureStreamerManager` to dynamically resize and reformat textures to destination FBOs prior to broadcasting.
- **Settings Persistence**: Serialized multi-stream video output configurations into `lsd-settings.properties` using JSON mapping.

---

## Version 1.0.0-beta.57

> [!NOTE]
> **Release 1.0.0-beta.57** improves Cell Config panel responsiveness with a guaranteed minimum layout width, optimizes numeric input box sizing across modulators and audio engine settings, and introduces 100% default depth ergonomics for Step Sequencer and LFO 2.

### UI Layout & Space Optimization (`UIManager.kt`, `CustomRangeSlider.kt`, `AudioEnginePanel.kt`)
- **Cell Config Minimum Width**: Enforced a minimum width of 450px for the Cell Config panel. The Mixer Monitor now scales down to accommodate this space when necessary, ensuring configuration controls remain accessible even at lower resolutions or with all Preset Grid columns visible.
- **Improved Input Box Efficiency**: Optimized the width of standard numeric input boxes (Depth, Phase, etc.) to 44px (approx. 41.8px at 95% scale).
- **LFO Range Slider Alignment**: Fixed a rendering error where the LFO range slider overlapped the 'Max' numeric input box when randomization was disabled.
- **Proportional Audio Engine Sizing**: Adjusted the Audio Engine settings input boxes to 35.2px to maintain a 20% smaller proportionality relative to the new standard width.

### Sequencer & LFO 2 100% Default Modulation Depth (`CvModulator.kt`, `PresetGridRenderer.kt`, `CellConfigPanel.kt`, `Lfo2Section.kt`)
- **Step Sequencer 100% Depth**: New sequencer modulators now default to 100% (1.0) depth so that step values output 1:1 direct voltage without requiring manual depth adjustments.
- **LFO 2 100% Depth & Auto-Activation**: LFO 2 modulator depth now defaults to 100% (1.0) with middle-click reset to 100%, and automatically ensures full depth upon enabling an AM/PM/ADD modulation mode.

---

## Version 1.0.0-beta.56

> [!NOTE]
> **Release 1.0.0-beta.56** introduces human-readable modulation bounds for LFOs and Audio followers, providing intuitive dual-handled range sliders that replace abstract Depth/Offset math, alongside a streamlined Step Sequencer UI.

### Human-Readable Modulation Bounds (Min/Max) (`CvModulator.kt`, `CustomRangeSlider.kt`, `Lfo1Section.kt`, `AudioModulatorSection.kt`)
- **Min/Max Conversion**: Converted LFO 1 and Audio modulator user controls to explicit Min and Max value bounds.
- **Dual-Handled Sliders**: Replaced separate Depth and DC Offset sliders with a single, intuitive dual-handled range slider.
- **Two-Tier Randomization**: When randomization is enabled, the UI provides two dedicated range sliders to independently define the random drift for the Minimum and Maximum boundaries.
- **Backward Compatibility**: Preserved existing preset math and DTOs by retaining internal Depth/Offset storage with on-the-fly bidirectional conversion.

### UI Cleanup & Ergonomics (`SeqSection.kt`)
- **Sequencer Simplification**: Removed the redundant DC Offset control from the Step Sequencer UI to reduce visual clutter.

---

## Version 1.0.0-beta.55

> [!NOTE]
> **Release 1.0.0-beta.55** focuses on UI stability and visual ergonomics: streamlines tooltip text across the entire interface by removing volatile live numeric values that induced rapid dimension resizing, improves Gradle parallel tooling build performance, and aligns interactive documentation.

### UI Ergonomics & Tooltip Stabilization (`PresetGridRenderer.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `MenuBar.kt`, `MixerMonitorPanel.kt`, `DeckControlPanel.kt`, `SeqSection.kt`)
- **Static Dimensions & Zero-Resize Hover**:
  - Removed fast-fluctuating live numeric readout lines (e.g. `Live: 8.3 (base 4.0 +4.3)`, live modulated boundary speeds/values, audio callback DSP times, crossfader percentages) from tooltips.
  - Eliminates tooltip window resizing and layout jitter on every audio buffer or LFO phase tick while hovering over sliders, cells, and meters.
  - Parameter tooltips now present stable, high-contrast information: Title, valid Range, Factory Default, Engine Description, User Parameter Notes, and interactive shortcut hints (`Click to configure`, `Middle-click to reset`, `Scroll to adjust`).
- **Tooling Optimization (`gradle.properties`)**:
  - Enabled `org.gradle.tooling.parallel=true` for faster project synchronization and Gradle 9.4+ tooling model building.

## Version 1.0.0-beta.54

> [!NOTE]
> **Release 1.0.0-beta.54** introduces the Inter-App Ecosystem & Interoperability Roadmap, live VJ Tap Tempo engine with phase downbeat quantization, ergonomic tooltip quadrant positioning with zero-pivot frame stabilization and opacity enforcement, and `cvModulatorSlider` callback deduplication across all parameter sections.

### Inter-App Ecosystem & Interoperability Roadmap (`docs/developer/interop_roadmap.md`, `docs/index.md`)
- Published the comprehensive multi-phase architectural roadmap for integrating Liquid LSD with third-party VJ software, media servers, and DAWs (Resolume, MadMapper, TouchDesigner, VDMX, OBS Studio, Ableton Live, Bitwig):
  - **Phase 0 (Foundation & Ergonomics)**:
    - **0.1 (Tooltip Formatting Polish)**: Multi-tier hover layout unification, eliminating visual clutter and standardizing type badges, values, descriptions, and shortcut hints.
    - **0.2 (LFO Min/Max Conversion)**: Human-readable modulation bounds replacing abstract Depth and DC offset controls, paired with dual-ended range sliders and backward-compatible preset math.
  - **Phase 1 (Stage Utility)**: Zero-copy GPU video sharing via Spout (Windows) and Syphon (macOS), providing independent output streams for Deck A, Deck B, Deck BG, Deck PV, and Master composite, featuring per-output resolution scaling and automatic naming conventions (`LiquidLSD-DeckA`, etc.).
  - **Phase 2.1 (Content Library)**: Interactive Shader Format (ISF) parser to import thousands of open-source community visual generators and export Liquid LSD sources to third-party apps.
  - **Phase 2.2 (FX System & Dual FX Slots)**: Migration of post-processing to modular ISF effect chains featuring two dedicated, modulatable FX slots per deck — `[ Slot 1: Color / Degradation ]` (Luma Key, Hue Cycle, Posterize, Invert) and `[ Slot 2: Spatial / Distortion ]` (Feedback Trails, Glitch, Mirror, Edge Warp).
  - **Phase 2.3 (Mixer Transitions)**: Extensible ISF 2-image crossfade and transition shaders for custom Deck A/B blending.
  - **Phase 3 (Musical Timing)**: Ableton Link peer-to-peer beat, tempo, and quantum phase synchronization as a network alternative to BTrack audio onset detection.
  - **Phase 4 (Video Ingest & Processing)**: Spout/Syphon live video input as a selectable, automatable visual source with full preset persistence, 2D/3D geometry transforms, and audio-reactive feedback FX.

### `CvModulatorSliderHelpers.kt` — Randomizable Slider Callback Deduplication
- Introduced `cvModulatorSlider(...)` in `ui/CvModulatorSliderHelpers.kt`, a helper that generates the standard `onRandomizableChanged` / `onRandomizeNow` / `onRangeChanged` / `onValueChanged` callback bundle for any `CvModulator` field exposed via `drawCustomRangeSlider`.
- Eliminated ~280 lines of structurally identical boilerplate across `Lfo1Section`, `Lfo2Section`, `AudioModulatorSection`, and `MidiModulatorSection` (from 760+762+403+154 = 2079 total to 578+609+282+94+81 = 1644, a net reduction of ~435 lines including the new helper).
- Beat-subdivision (index-stepping) and Period/Frame (multiplicative halving/doubling) sliders retain inline `onRandomizableChanged` blocks because their expansion logic differs from the standard `±offset` pattern.

### VJ Tap Tempo & Phase Downbeat Synchronization (`TapTempoController.kt`, `AudioEngine.kt`, `BeatTrackerEngine.kt`, `MenuBar.kt`, `Mixer.kt`, `SettingsPanel.kt`)
- **Real-Time Tap Cadence Engine (`TapTempoController.kt`)**:
  - Implements an allocation-free circular buffer averaging the last 8 tap intervals with nanosecond resolution.
  - Automatically resets sequence cadence when tap intervals exceed 2.0 seconds (30 BPM cutoff).
  - Emits a transient visual flash on the BPM readout (300ms decay) and tracks active tap counts (`TAP [N]`).
- **Dual-Mode Audio Engine Integration (`AudioEngine.kt`, `BeatTrackerEngine.kt`)**:
  - **Manual / Locked Mode (`isBpmLocked = true` or Audio Disabled)**: Direct tempo update to `manualBpm` and instant downbeat phase quantization (`round(totalBeats)`), locking visual pulses and the 4-beat bar meter to the musical downbeat on tap.
  - **Live Audio Tracking Mode (`!isBpmLocked`)**: Nudges the beat tracker's candidate tempo, recalculates dynamic programming search center $\tau_0$, and injects a smooth phase nudge into `phaseSlewBuffer`, immediately breaking octave traps (half-time/double-time false locks) without visual jump discontinuities.
- **Mouse & Keyboard Interactions (`MenuBar.kt`, `Main.kt`, `SettingsPanel.kt`, `UITheme.kt`)**:
  - Clicking the `BPM: <val>` readout in the top bar triggers Tap Tempo using an invisible button target, firing immediately on mouse down (`isItemClicked(0)`).
  - While tapping, the BPM readout displays immediate visual confirmation: flashes in bright gold and renders the active tap count (`BPM: [TAP 1]`, `BPM: 128 [2]`).
  - Clicking the `DSP: <val>ms` badge (or 4-beat phase dots) opens the Audio Settings panel. The DSP badge is persistently visible even when audio is disabled (`DSP: OFF`) or inactive (`DSP: --`).
  - Added keyboard trigger support in GLFW key callback (`Main.kt`): pressing `T` (default) or `.` (configurable in Settings under `Tap Tempo Key`) triggers tap tempo when text inputs are not focused.
- **Beat & Flywheel Synchronization (`AudioEngine.kt`, `BeatTrackerEngine.kt`, `CVRegistry.kt`)**:
  - In manual/locked mode, `CVRegistry.alignBeatPhase()` instantly aligns visual pulses and CV oscillators to whole-beat boundaries without monotonic jitter filtering delay.
  - In audio tracking mode, `BeatTrackerEngine.nudgeTempo()` applies a stability lock (`isLocked = true`, `stableAccumulatedSec = stabilityLockDurationSec`) around the tapped BPM to keep the tracker locked to the tap.

### Ergonomic Tooltip Quadrant Positioning, Opacity Enforcement & Zero-Allocation Hover Delay (`TooltipHelper.kt`, UI Panels)
- **Mixxx/Qt-Inspired Quadrant Layout (`TooltipHelper.kt`)**:
  - Replaced Dear ImGui's default bottom-right tooltip placement (`mouse + 16, mouse + 10`) which routinely obscured parameter sliders, values, and adjacent UI controls.
  - Aligns tooltips beneath a fixed $16 \times 22\,\text{px}$ pointer bounding box with a $4\,\text{px}$ gap.
  - **Dynamic Right-Edge Anchoring**: Aligns tooltip left edge with the cursor box left edge by default; automatically flips to anchor its right edge against the cursor box right edge when close to the viewport right border (extending leftward).
  - **Bottom-Edge Overflow Flip**: Flips the tooltip above the cursor box when overflowing the bottom of the viewport/window.
  - **Safety Margins**: Maintains an $8\,\text{px}$ margin from all viewport edges with boundary coordinate clamping.
- **100% Solid Opacity Guarantee (`setNextWindowBgAlpha(1.0f)`)**:
  - Fixed semi-transparent tooltip rendering when hovering over bypassed modulators or inactive buttons.
  - Wrapped tooltip rendering in `ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 1.0f)` and `ImGui.setNextWindowBgAlpha(1.0f)` across all tooltip helpers, insulating tooltips from parent widget alpha inheritance and ensuring crisp, high-contrast legibility.
- **Zero-Allocation Hover Delay Tracker (`TooltipHelper.kt`)**:
  - Implements a non-allocating hover delay tracker ($250\,\text{ms}$ threshold) using spatial item hashing (`(minX shl 16) xor minY xor text.hashCode()`) and frame gap tracking.
  - Eliminates visual flicker and tooltip pop-in during swift cursor sweeps across matrix rows, sliders, and buttons without relying on Dear ImGui 1.88+ flags.
- **Zero Raw `setTooltip` / `beginTooltip` Across Entire Application**:
  - Migrated 100% of tooltips across all UI subsystems to `itemTooltip`, `showTooltip`, and `showCustomTooltip`:
    - `PresetGridRenderer` & `PresetGridPanel`: Value cells, MIDI cells, CV cells, parameter headers, column config kebab menu.
    - `PresetGridTabs`: Left deck tabs, top source selectors, and subtabs.
    - `ModulatorHeaderRow`: Power toggle, dice, operator dropdown, and clear controls.
    - `MixerMonitorPanel` & `OscilloscopeDrawer`: Master monitor, crossfader, fader badges, oscilloscope canvas, timebase selector, and mute controls.
    - `MenuBar`: Menu items, Recording HUD, Web Broadcast HUD, Beat Phase dots, BPM readout, DSP badge, frameless CSD window controls.
    - `SettingsPanel` & `UpdatePromptModal`: All preference sliders, checkboxes, directory pickers, and modals.
    - `LibraryPanel` & `browser/*`: Preset list issue badges, Playlists, Play Queue, Background Queue, and action toolbars.

### Tooltip Polish — Round 2 & 3: Parameter Tooltip Positioning, Zero-Pivot Stabilization, Border & Text Isolation (`TooltipHelper.kt`, `UIThemeStyler.kt`)

**Root cause of tooltip jumping and flickers**:
- In Dear ImGui, passing non-zero pivot offsets (e.g. `pivotX = 1.0f` or `pivotY = 1.0f`) to `SetNextWindowPos` evaluates `pos -= window->SizeFull * pivot`. On frame 1 of a newly appearing tooltip, `window->SizeFull` is uninitialized `(0, 0)`. Dear ImGui therefore applied a 0-pixel offset on frame 1, placing the window at the cursor instead of shifted by its size, and only shifted it on frame 2 when `SizeFull` was computed.
- In addition, an experimental `pushTextWrapPos` call forced text into a collapsing width loop where `recordCustomSize` recorded the collapsed width, shrinking text down to 1–2 characters wide and forming an unconstrained vertical tower.
- **Fix**: Removed `pushTextWrapPos` completely, and updated `prepareTooltipPos` to convert `(targetX, targetY, pivotX, pivotY)` directly into exact top-left window coordinates: `finalX = targetX - contentWidth * pivotX`, `finalY = targetY - contentHeight * pivotY`, passing `(finalX, finalY)` to `setNextWindowPos` with a zero pivot `(0, 0)`. Dear ImGui receives pre-computed top-left coordinates and never applies uninitialized `SizeFull * pivot` offsets on frame 1.
- **Size Cache Guard**: `recordCustomSize` now ignores degenerate dimensions (`width < 100f || height < 30f`), ensuring the cache only stores valid, rendered tooltip sizes.

**Other improvements**:
- **`ImGuiCol.Border` and `ImGuiCol.Text` Isolation**: Pushes `TooltipHelper.baseTextColor` and `TooltipHelper.baseBorderColor` (dynamically captured per theme in `UIThemeStyler.setupThemeColors()`) inside `pushTooltipStyles()`. Prevents button styles (like `BrowserDeckButtons` colored borders/text) from tinting tooltip windows or frames.
- **8-Slot Circular Size Cache**: Keeps a rolling history of the last 8 custom tooltip dimensions (zero heap allocation), ensuring re-hovering across parameter rows or deck headers is an instant cache hit.
- **Compilation Safety**: Annotated `pushTooltipStyles()` and `popTooltipStyles()` with `@PublishedApi internal` so public inline custom tooltip functions compile cleanly.

---

## Version 1.0.0-beta.53

> [!NOTE]
> **Release 1.0.0-beta.53** introduces a streamlined Library panel layout with swapped Background Queue and Play Queue columns, transport controls clustered into the column headers with dynamic Play/Pause icons, inline queue clearing, and build toolchain modernization including Gradle 9 dependency coordinate notation and JDK 25 native access enablement.

### Library Play Queue Rearrangement & Swapped Column Layout (`LibraryPanel.kt`, `QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`, `docs/user_guide/library.md`)
- **Queue Column Order Swap (`LibraryPanel.kt`)**: Swapped Column 3 and Column 4 in the Library panel so the Background Queue (`LibraryBgQueue`) is presented before the main A/B Play Queue (`LibraryQueue`).
- **Transport Controls Cluster in Header (`QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`)**:
  - Relocated automated cycle toggles (Auto-VJ and Auto-BG) from the second row to the top row header, positioned between the Previous (`<`) and Next (`>`) buttons.
  - Converted the button icon to a standard transport toggle: displays Play (`Icons.PLAY`) when paused, and Pause (`Icons.PAUSE`) when actively cycling presets.
  - Retained standard theme button styling for transport controls and preserved existing tooltips.
- **Inline Controls Row & Unified Button Styling (`QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`)**:
  - Moved the `Clear` button to the second row inline directly after `Export`.
  - Unified the active color scheme across both queues: Shuffle and Repeat buttons highlight with mint-green text and background when active, and revert to standard theme button styling when inactive.

### Build System & Toolchain Warning Cleanups (`build.gradle.kts`, `gradlew`, `gradlew.bat`, `gradle.properties`, `SessionStateTest.kt`)
- **JDK 25 Native Access Enablement (`gradlew`, `gradlew.bat`, `gradle.properties`)**: Configured `--enable-native-access=ALL-UNNAMED` in Gradle wrapper default JVM options and daemon JVM args (`org.gradle.jvmargs`), eliminating the Java 25 `System::load` restricted method warning from `native-platform` on modern JDKs.
- **Gradle 9 Dependency Notation Deprecations (`build.gradle.kts`)**: Converted multi-argument `implementation(...)` and `runtimeOnly(...)` dependency calls for LWJGL and ImGui to standard single-string coordinate notation (`"group:name:version"` and `"group:name:version:classifier"`), resolving all Gradle 9 deprecation warnings.
- **Documentation Task Logging (`build.gradle.kts`)**: Switched `mkdocs` missing fallback notice in `generateDocs` from stdout warning formatting to `logger.info`, keeping standard build task output clean when MkDocs is not installed.
- **Kotlin Smart-Cast Nullability Warnings (`SessionStateTest.kt`)**: Removed redundant safe-call operators (`?.`) on values following `assertNotNull` assertions in `SessionStateTest`, resolving Kotlin 2.3+ compiler warnings.

---

## Version 1.0.0-beta.52

> [!NOTE]
> **Release 1.0.0-beta.52** introduces 1:1 scale normalization across 2D and 3D modes (matching vertical frame heights at Zoom 1.0), true 6-panel default cube displacement for Cube Cage mode, visibility and space-folding restoration for the 24-chamber Tetrahedral Kaleidoscope mode, and restriction of secondary 3D projection passes strictly to flat 2D sources while routing native 3D parameters contextually in the View tab.

### 3D Mode Scale Normalization, Cube Cage Base Offset & Tetrahedral Kaleidoscope Fixes (`tri_planar.vert`, `tetra_kaleido.frag`)
- **1:1 Scale Normalization Across 2D and 3D Modes (`tri_planar.vert`, `tetra_kaleido.frag`)**:
  - Aligned hardware perspective division in `tri_planar.vert` (`clipX`, `clipY` scaled by `cameraDistance = 2.5`) so that at `Zoom = 1.0` and `Rotate = (0, 0, 0)`, the 3D planes fill the vertical viewport frame $[-1, 1]$ identically to 2D flat mode, eliminating the previous scale discrepancy where 3D mode required a Zoom of $\approx 1.6$ to match 2D height.
  - Normalized camera ray FOV in `tetra_kaleido.frag` so that at `Zoom = 1.0` and default `Persp = 0.5`, the central kaleidoscopic facet exactly fills the vertical frame.
- **Cube Cage 6-Panel Base Displacement (`tri_planar.vert`)**:
  - For Mode 2 (`Cube Cage`, slider range $1.5 \dots 2.5$), added a base unit displacement (`baseOffset = 1.0`) along the face normals (`localPos += normal * (1.0 + uSeparation)`). This ensures the 6 planes form a true 3D cube box by default even when `Separation = 0.0`, rather than collapsing into the same 3 central planes as Tri-Axial mode.
- **Tetrahedral Kaleidoscope Space-Folding & Visibility Restoration (`tetra_kaleido.frag`)**:
  - Fixed the iterative Coxeter space-folding loop in `tetra_kaleido.frag` to cleanly reflect camera rays across all 6 reflection planes of the $A_3$ tetrahedral group into the fundamental chamber ($p_x \ge p_y \ge |p_z| \ge 0$).
  - Switched from unnormalized directional coordinates to plane-projected coordinates ($u = p_y / p_x, v = p_z / p_x$), mapping the 2D source seamlessly across the central facet without out-of-bounds clipping.
  - Eliminated the black screen bug caused by an inverted/contradictory sorting loop and an aggressive `borderFade <= 0.001` fragment discard, ensuring smooth roundness disc fade and vibrant kaleidoscopic tiling across all 24 tetrahedral chambers.

### 3D Mode Restriction to 2D Sources & Streamlined 3D Transform Controls (`DynamicVisualSource.kt`, `Deck.kt`, `Renderer.kt`, `PresetGridTabs.kt`, `meta.json`)
- **Exclusive 2D->3D Elevation**: Universal 3D modes (Tri-Axial, Cube Cage, Hex-Planar, and Tetrahedral Kaleidoscope) are now strictly restricted to flat 2D visual sources (`mandala`, `colors`, `dynamic_spiral`, `attractor_feedback`). Native 3D visual sources (`icosahedron`, `icosa-v3`, `hyper_mesh`, `icosa_dodeca`, `chladni`, `gyroid`, `hyper_slice`) bypass secondary 3D projection passes, preventing geometric distortion and raymarching artifacts.
- **Contextual View Tab Streamlining**: For native 3D sources, the `3D Mode` parameter is removed from the View tab. This eliminates duplicate rotation controls (`Rotate X` and `Rotate Y`) and prevents conflicts between deck view parameters and native source parameters (`Control X`, `Control Y`).
- **Canonical Parameter Routing**: Native 3D source transform parameters (`Zoom`, `Rotate X`, `Rotate Y`, `Rotate Z`) displayed in the View tab are addressed via their canonical source paths (`$deckLabel/${activeSource.displayName}/$name`), guaranteeing seamless MIDI mapping, CV modulation, and clipboard operations without ID collisions.
- **Native 3D Tagging & Automatic Detection (`is3D`)**: Tagged all 7 native 3D sources with `"is3D": true` in `meta.json` and added automatic fallback detection in `VisualSourceRegistry` for any sources exposing `Rotate X` and `Rotate Y` parameters.
- **Deck Source Assignment Safety**: Assigning a 3D source to a Deck automatically resets `view3DMode` to `0.0`.

---

## Version 1.0.0-beta.51

> [!NOTE]
> **Release 1.0.0-beta.51** introduces bundled factory presets and playlists with safe first-run library seeding, on-demand factory restore, first-run visual autoload on Deck A, a clean-lined modern application icon, static deck preview monitor borders, and zero-centered bipolar crossfader rendering.

### Bundled Factory Presets, Playlists & Safe First-Run Seeding (`build.gradle.kts`, `FileSystemManager.kt`, `PresetManager.kt`, `MenuBar.kt`, `PresetListPanel.kt`)
- **Version-Controlled Defaults**: Added `defaults/presets/` and `defaults/playlists/` directories tracked in Git. Curated presets and setlists can be created inside the app and synced to repository defaults via `./gradlew syncDefaultsFromLibrary`.
- **Automated Resource Packaging (`build.gradle.kts`)**: Gradle build automatically generates `manifest.txt` indices and bundles default presets and playlists into application classpath resources (`default_presets/`, `default_playlists/`), making them immediately available on clean clones and binary releases.
- **Safe First-Run Seeding**: On first launch, `FileSystemManager.ensureDefaultLibrary()` unpacks bundled presets and playlists into `library/presets` and `library/playlists`, recording a persistent `library/.defaults_installed` marker file.
- **Permanent Deletion Safety**: If a user intentionally deletes a factory preset or playlist from their local library, the initialization marker prevents it from resurrecting on future application launches.
- **First-Run Visual Autoload**: When starting without an existing session, Deck A automatically loads a curated starter visual preset (`3d mandala`) instead of starting on a blank screen, while preserving clean empty starts when `--empty` / `-e` is passed.
- **On-Demand Factory Restore**: Added **"Restore Factory Presets..."** to the **File** menu and an in-browser restore button in `PresetListPanel` when no presets are found, allowing users to safely restore missing factory presets/playlists at any time without overwriting their custom work.


### Mixer Monitor & Performance Console Polish (`MixerMonitorPanel.kt`, `LinuxEvdevTouchBackend.kt`, `scripts/install_desktop.sh`)
- **Zero-Centered Bipolar Master Crossfader**: Crossfader bar line now renders outward from the center detent (0.0), matching standard DJ hardware fader conventions: fills left with Deck A amber color when in Deck A territory (-1.0 to 0.0), and fills right with Deck B cyan color when in Deck B territory (0.0 to +1.0).
- **Streamlined Crossfader Chrome**: Removed redundant duplicate alpha stem HUD from `MixerMonitorPanel`, allowing the fader to align cleanly with the channel strips.
- **Static Deck Preview Borders**: Removed dynamic crossfader-linked brightness and thickness modulation from `DeckControlPanel`. Deck preview monitors now consistently render at full theme saturation and constant 2.0px border thickness regardless of crossfader position.
- **udev Rule Priority & Hotplug Reliability**: Renamed touchpad permission rule to `70-liquidlsd-touchpad.rules` with `TAG+="seat"` and `RUN{builtin}+="uaccess"`, ensuring proper evaluation before systemd-logind seat tagging and dynamic ACL application across kernel input device events.

---

## Version 1.0.0-beta.50

> [!NOTE]
> **Release 1.0.0-beta.50** introduces the **CapsLock Multi-Touch Trackpad Performance Console** (transforming laptop trackpads into a tactile 4-zone SCS.3m virtual mixer console with LIFO finger cut stutters, direct-jump alpha faders, sticky hold, and hardware-level Linux evdev and macOS Cocoa backends), alongside the **Startup Version Checker & GitHub Update Prompt** (non-blocking background release checks, SemVer 2.0.0 precedence engine, interactive update modal, and About dialog).

### CapsLock Multi-Touch Trackpad Performance Console (SCS.3m Virtual Console) (`TouchConsoleController.kt`, `LinuxEvdevTouchBackend.kt`, `MacCocoaTouchBackend.kt`, `MixerMonitorPanel.kt`, `SettingsPanel.kt`)
- **4-Zone Performance Surface**:
  - Turns laptop trackpads into an absolute multi-touch performance console when `CapsLock` is engaged.
  - **Bottom 28%**: Horizontal Crossfader (Deck A $\leftrightarrow$ Deck B, direct jump, cut stutters).
  - **Middle 17%**: Safety Deadzone Buffer (retains active drift under Zone Affinity, rejects new taps).
  - **Top 55%**: Three independent vertical Level/Alpha faders for Deck A, Deck BG, and Deck B ($0.0 \dots 1.0$, direct jump).
- **Independent LIFO Multi-Touch Stacks**:
  - Each zone maintains an independent LIFO touch stack. Tapping with a second finger instantly jumps to that position; releasing snaps back to the underlying anchor finger.
  - Enables machine-gun crossfade cut stutters and video flash/strobe blackout gates.
  - Sticky hold retains fader levels when all fingers are lifted.
  - Bezel clamping ($Y \le 0.48 \to 0.0$, $Y \ge 0.94 \to 1.0$; $X \le 0.05 \to -1.0$, $X \ge 0.95 \to 1.0$, center detent $\pm 0.02 \to 0.0$) ensures comfortable control without hitting physical laptop chassis edges.
- **Native Platform Backends**:
  - **Linux**: Direct evdev reader via JNA `libc`, `O_RDWR` with `EVIOCGRAB` (`0x40044590`) cursor grab, `EVIOCGABS` hardware axis query, MT Protocol B slot cache, POSIX ACL validation using `access(2)` (circumventing JVM `File.canWrite()` ACL blind spots), and absolute hardware axis verification with explicit TrackPoint/pointing-stick filtering.
  - **macOS**: Cocoa `NSTouch` indirect touch events.
  - **Zero-Crash Graceful Permissions**: Non-root `uaccess` systemd udev rule (`/etc/udev/rules.d/70-liquidlsd-touchpad.rules`) via `pkexec`, interactive permission badge in UI, and in-app hot-reload.
  - **Thread-Safety**: Low-latency lock-free event queue drained strictly on Thread 0 once per frame.
- **Visual Feedback HUD**:
  - Real-time glowing cyan contact dots for active fingers and amber dots for anchor fingers directly on the Crossfader slider.
  - Active alpha levels are reflected seamlessly on each individual deck's monitor preview fader.
  - **Zero-Centered Bipolar Crossfader Bar**: Crossfader slider track bar now correctly originates from center (`0.0`), extending leftward tinted with Deck A's color when fading towards Deck A, and rightward tinted with Deck B's color when fading towards Deck B (rather than filling unidirectionally from Deck A).
  - Clean Mode remains 100% clean with zero HUD overlays.

### Startup Version Checker & GitHub Update Prompt (`UpdateChecker.kt`, `SemVer.kt`, `AppVersion.kt`, `UpdatePromptModal.kt`, `AboutModal.kt`, `MenuBar.kt`, `SettingsPanel.kt`)
- **Non-Blocking Background Update Engine (`UpdateChecker.kt`)**:
  - Checks for the latest release on GitHub asynchronously via a daemon thread on application startup without blocking audio callbacks or GLFW/OpenGL rendering.
  - Dual-mode network query: Uses the GitHub REST API (`releases/latest`) with an automatic fallback that inspects HTTP redirect headers (`Location`) from `github.com/.../releases/latest`, bypassing unauthenticated API rate limits.
  - Strict 5-second timeouts with fail-safe error handling so offline or network errors never interrupt application startup.
- **SemVer 2.0.0 Parsing & Precedence (`SemVer.kt`)**:
  - Zero-dependency semantic version parser adhering to SemVer 2.0.0 rules (numeric core segments, release vs pre-release precedence, dot-separated tag sequences e.g. `beta.42` > `beta.41`, and snapshot detection).
- **Interactive Update Prompt Modal (`UpdatePromptModal.kt`)**:
  - Automatically alerts the user when a newer release is published on GitHub.
  - Displays the current version, latest version, and release title.
  - Options: **Download Update** (opens browser directly to the GitHub release page), **Remind Later** (dismisses for the current session), or **Skip Version** (persists `ignoredUpdateVersion` in preferences so the user is not prompted again for that specific release).
- **"About Liquid LSD" Dialog & Help Menu Controls (`AboutModal.kt`, `MenuBar.kt`)**:
  - Added **About Liquid LSD** and **Check for Updates...** to the **Help** menu.
  - The About dialog displays the current runtime version, provides a manual **Check for Updates** button with real-time status feedback, and provides direct links to the GitHub repository and documentation.
- **Startup & Update Preferences (`SettingsPanel.kt`, `AppSettings.kt`, `UITheme.kt`)**:
  - Added "Automatically check for updates on launch" toggle and a manual "Check for Updates Now" action in `Settings > General > Startup & Updates`.

---

## Version 1.0.0-beta.41

> [!NOTE]
> **Release 1.0.0-beta.41** brings a fixed 95% global UI typography scale, dedicated Library preset name scaling, inside-clustered monitor overlays with 4-channel physical console level faders and preview dimming, multi-resolution application icons and FreeDesktop launcher integration with Wayland/X11 app IDs, sticky two-row Library column headers, redesigned single-handle Master Crossfader, minimum 1280×720 window bounds, unified column visibility driven directly by engine subsystems, and hex-planar / tetrahedral 24-chamber kaleidoscope 3D modes.

### Fixed 95% UI Scale & Focused Library Preset Sizing (`UITheme.kt`, `AppSettings.kt`, `SettingsPanel.kt`, `GridMetrics.kt`, `UIManager.kt`)
- **Permanently Fixed 95% Global UI Scale**:
  - Pinned all application typography to exact pixel sizes at 95% scale: Caption 12px, Body 14px, Code 14px, H3 15px, H2 18px, H1 22px, baseSize 14.25px.
  - Eliminated dynamic `(baseSize / 15f)` runtime scaling calculations across 14+ UI panels (`PresetGridPanel`, `AudioEnginePanel`, `CellConfigPanel`, `CustomRangeSlider`, `BeatDivisionSlider`, `Lfo1Section`, `Lfo2Section`, `ModulatorHeaderRow`, `OscilloscopeDrawer`, etc.).
- **Deprecation & Removal of Grid Knob Cell Scale**:
  - Completely removed the non-functional `gridCellRatio` setting and UI slider.
  - Precalculated `GridMetrics` into a singleton `INSTANCE` at 95% scale (`cell = 33.25f`), eliminating per-frame heap allocations during Preset Grid rendering.
- **Dedicated Library Preset Name Sizing**:
  - Introduced `presetNameScalePercent` (80%–120%, default 100%, 10% step) to scale preset names in the Library (`PresetListPanel`), Playlist Editor (`PlaylistEditorPanel`), and Play Queues (`QueueActionsPanel`, `BgQueueActionsPanel`) without altering performance controls or deck headers. 10% steps ensure each step produces a distinct, pixel-aligned font size without glyph bounding box collisions.
  - Added an informational typography hierarchy display in `Settings > Appearance` alongside the new "Preset Name Size" slider.
- **Repurposed Zoom Shortcuts**:
  - Shortcuts `Ctrl + -` and `Ctrl + =` (`Cmd + -` and `Cmd + =` on macOS, with keypad +/- support) now adjust Library preset name scale in 10% increments.

### Clustered Monitor Overlays & 4-Channel Level Faders (`DeckControlPanel.kt`, `MixerMonitorPanel.kt`, `Mixer.kt`, `Renderer.kt`, `mixer.frag`)
- **4-Channel Console Level Faders**:
  - Added non-modulatable 0.0–1.0 channel level multipliers (`levelA`, `levelB`, `levelBG`, `levelPV`, `masterLevel`) to `Mixer`.
  - Level faders act as physical mixer console channel strips: they scale output in `mixer.frag` (`uLevelA`, `uLevelB`, `uLevelBG`, `uMasterLevel`) without interfering with or resetting underlying CV modulators.
  - Console isolation: Channel fader settings are preserved across preset swaps, copies, and patch reloads.
- **Inside-Clustered Monitor Overlays**:
  - **Deck A & Deck BG**: Badges `[A]` and `[BG]` relocated to the top-right corner; die buttons `[🎲]` positioned to the left of the badges; vertical level faders hang directly below the badges.
  - **Deck B & Deck PV**: Badges `[B]` and `[PV]` relocated to the top-left corner; die buttons `[🎲]` positioned to the right of the badges; vertical level faders hang directly below the badges.
  - Central Command Spine: All 4 channel faders and dice are clustered in the middle gutter right below the crossfader, within immediate mouse reach.
  - Overlaid directly on the video previews without shrinking preview width or distorting aspect ratios.
- **Master Output Monitor Overlay**:
  - Added bottom-right overlay with `[M]` Master badge, `[🎲 ALL]` button to its left, and vertical Master Level fader directly above `[M]` extending upward. Leaves top-right free for the `[REC]` tally badge.
- **Interactive Badges & Non-Overlapping Monitor Click Bounds**:
  - Clicking `[A]`, `[B]`, `[BG]`, `[PV]`, or `[M]` badges directly focuses that deck/mixer tab in the Preset Grid.
  - Background monitor drag/focus hitboxes exclude the 60px overlay gutter, preventing accidental tab switching or drag-and-drop actions when operating faders or dice.
- **Channel Preview Dimming**:
  - Live monitor previews for Deck A, Deck B, Deck BG, and Deck PV dim dynamically as their corresponding level fader is reduced below 1.0.

### Application Icons & Window Branding (`Main.kt`, `build.gradle.kts`, `scripts/install_desktop.sh`, `liquid-lsd.desktop`, `web/`, `website/`, `src/main/resources/icons/`)
- **Desktop Window Icons & Compositor Integration (GLFW, X11, Wayland)**:
  - Integrated multi-resolution application icon loading (`16x16`, `32x32`, `48x48`, `64x64`, `128x128`, `256x256`) via `stbi_load_from_memory` and `glfwSetWindowIcon` in `Main.kt`.
  - Added `GLFW_WAYLAND_APP_ID`, `GLFW_X11_CLASS_NAME`, and `GLFW_X11_INSTANCE_NAME` hints to ensure proper window grouping and taskbar/dock icon association under modern Linux compositors (GNOME, KDE, Sway/Hyprland).
  - Implemented automatic local FreeDesktop icon and `.desktop` entry registration (`ensureLinuxDesktopEntry()`), alongside distribution packaging scripts (`scripts/install_desktop.sh` and `build.gradle.kts` dist integration).
  - Automatically applied icons and window classes to both primary desktop and secondary / external monitor preview windows.
- **Web & Documentation Branding**:
  - Wired high-resolution favicon and Apple touch icon assets into `web/index.html` and documentation templates (`website/templates/doc_page.html`, `website/templates/index.html`).

### Library Panel UI Refinements (`LibraryPanel.kt`, `BrowserActionToolbar.kt`, `PresetListPanel.kt`, `PlaylistEditorPanel.kt`, `QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`, `Icons.kt`)
- **Sticky Top Two Rows in Library Columns**:
  - The top two header rows of each of the four Library columns (Presets, Playlists, Queue, BG Queue)—including panel titles, action buttons, search filters, playlist selector combo, and playback/auto-vj controls—remain pinned/sticky at the top when scrolling through lists.
  - The item lists now scroll within dedicated inner child windows (`##presets_scroll`, `##playlist_items_scroll`, `##queue_items_scroll`, `##bg_queue_items_scroll`), while the outer column frames lock scrolling via `NoScrollbar` and `NoScrollWithMouse`.
- **Proportional Action Toolbar Buttons**:
  - Resized the deck load/audition buttons in `BrowserActionToolbar` from an oversized fixed width (80 px) to ~1.5x their height (`btnH * 1.5f`, ~36 px at standard scaling), streamlining horizontal footprint and toolbar centering.
- **Balanced Title Bar Button Height & Padding**:
  - Reduced button height in the Library title bar from ~32 px to ~22 px (`(22f * fontScale)`), creating sleeker, more compact controls.
  - Adjusted title bar frame padding to 6.0 px (`libTitleBarH = 32f`), preserving the ~2.5 px bottom margin while adding sufficient top clearance to prevent button borders from clipping against the horizontal splitter line.
- **Fixed Lock/Unlock Icons**:
  - Corrected the Lucide PUA glyph codepoints for `Icons.LOCK` (`\ue10b`, previously misassigned to `map-pin`) and `Icons.UNLOCK` (`\ue10c`, previously misassigned to `user`), restoring standard padlock icons on the quick audition latch button.

### Mixer Monitor UI Refinement (`MixerMonitorPanel.kt`, `MixerMonitorLayout.kt`)
- **Redesigned Master Crossfader**:
  - Replaced the previous progress-bar style flat fader with the standard single-handle slider styling from `CustomRangeSlider` (3 px track line, 6x16 px handle, hover/drag bounding rect highlight, and dynamic modulated amber dot).
  - Added faint vertical position tick marks across the crossfader track indicating the ends (-1.0, +1.0), midway points (-0.5, +0.5), and middle center (0.0).
  - Placed **[ A ]** and **[ B ]** boxed badges to the left and right of the crossfader, styled identically to the Deck A and Deck B monitor overlays.
  - Interactive badges: clicking [A] snaps crossfade to 100% Deck A (-1.0), and clicking [B] snaps crossfade to 100% Deck B (+1.0). Middle-clicking or scrolling adjusts or centers (0.0) the crossfader.
  - Removed the `"Crossfader"` text label for a cleaner, centered look.
- **Streamlined Master Controls**:
  - Removed the `< Prev` and `Next >` queue trigger buttons.
  - Sized the 5 momentary randomization buttons (`Rand A`, `Rand B`, `Rand BG`, `Rand PV`, `Rand All`) to distribute evenly across full panel width when randomization is enabled.
  - Removed the redundant Fade Speed slider from the Mixer Monitor child window.
  - Updated `MixerMonitorLayoutCalculator` vertical chrome calculations to account for the reduced row count, giving more vertical canvas space to preview monitors.

### Preset Grid UI Layout Tightening (`PresetGridTabs.kt`, `PresetGridPanel.kt`, `PresetGridRenderer.kt`, `BrowserRowMoreButton.kt`)
- **Square Side Tab Buttons**: Made the deck selector buttons (`MIX`, `A`, `B`, `BG`, `PV`) square (`width == height`), significantly reducing horizontal footprint while maintaining comfortable click targets.
- **Tightened Parameter Indentation**: Reduced the parameter label indent from Dear ImGui's default ~21–24 px to 6 px (~1/4 width), gaining horizontal space for parameter names and matching section tab insets.
- **Refined Kebab Button Proportions**:
  - Decreased kebab button width by ~25–30% (from 26 px / 28 px to 19 px / 20 px).
  - Scaled down kebab dot radius and spacing for a more refined, minimal appearance.
- **Unified Border Box & Vertical Scrollbar Alignment**:
  - Aligned the right-hand edge of the preset grid container frame box (`boxMaxX`) to share the exact right-hand edge of the column settings kebab.
  - Sized the preset grid scrolling child window (`##preset_grid_scroll`) to position the vertical scrollbar directly in the narrow vertical column beneath the kebab, eliminating visual gaps and overlaps.
- **Symmetrical Right Border to Panel Edge Padding**:
  - Eliminated extra trailing horizontal margin in `PresetGridPanel.calculateRequiredWidth` and `PresetGridPanel.draw`.
  - Balanced the right padding between the preset grid box border and the panel edge/divider line to match the standard `windowPaddingX` (8 px), creating visual symmetry with the padding between the divider line and Cell Config's content.
- **Global Slim 10 px Scrollbar**:
  - Configured global base `scrollbarSize` to 10.0 px (with 5.0 px pill rounding) in `UIManager.kt`.
  - Applies uniformly across all panels (Preset Grid, Cell Config, and Library), providing a cleaner, more compact visual profile while maintaining dynamic scaling with UI font size preferences.

### Minimum Window Dimensions Raised to 1280 x 720 (`Main.kt`, `WindowFrameController.kt`)
- **Updated Minimum Bounds**: Raised the desktop minimum window dimensions from 800 × 600 (SVGA 4:3) to 1280 × 720 (720p HD 16:9).
- **Layout & Multi-Deck Preservation**: Prevents severe horizontal layout compression and UI clipping across the top title bar controls, preset grid, and audio modulation matrix on compact or resized windows, aligning standard desktop bounds with modern HD DJ/VJ workflows.

### Standardized Title Bar to Workspace Panel Gap (`UIManager.kt`, `WindowLayoutSafetyTest.kt`)
- **Resolved Variable Black Gap**: Identified and eliminated an unintended black gap between the top title/menu bar and the primary workspace panels caused by a legacy minimum height clamp (`.coerceAtLeast(32f)`). Following recent Dear ImGui font metric updates, `ImGui.getFrameHeight()` evaluated below 32px at default scaling, displacing panels downward while the menu bar window remained at frame height.
- **Explicit 1 px Spacing Constant**: Replaced the magic number clamp with a named constant `UIManager.TITLE_BAR_PANEL_GAP = 1.0f`. The layout now cleanly calculates panel starting offset as `titleBarH + TITLE_BAR_PANEL_GAP`, maintaining an exact 1 px visual divider across all display sizes and UI scaling presets without disappearing during font zoom.

### Unified Modulator Engine & Preset Grid Column Visibility (`UITheme.kt`, `SettingsPanel.kt`, `PresetGridPanel.kt`, `CellConfigPanel.kt`, `PresetDependencyAnalyzer.kt`)
- **Single Source of Truth**: Unified engine subsystems (`audioEngineEnabled`, `midiEnabled`, `sequencerEnabled`) with Preset Grid column visibility. A column is visible if and only if its underlying subsystem is active, eliminating contradictory states where a disabled engine's column could be shown or an active engine's column hidden.
- **Preset Grid Kebab as Quick Switchboard (`PresetGridPanel.kt`)**: The header kebab menu (`⋮`) now directly toggles the underlying subsystems (starting/stopping `AudioEngine`, scanning/closing `MidiEngine`, running/halting step sequencer clock) alongside their columns in one click, without opening Settings.
- **Streamlined Settings Categories (`SettingsPanel.kt`)**: Removed the redundant `Settings > Preset Grid` category. Relocated the `Grid Knob Cell Scale` (`gridCellRatio`) slider to `Settings > Appearance` under "Fonts & Sizing".
- **Optimized 10-Bit Dependency Issue Cache (`PresetDependencyAnalyzer.kt`)**: Reduced memoization state space from 8192 to 1024 slots with zero runtime GC allocations per frame.

### Manual BPM Configuration & Persistent Title Bar Display when Audio Engine is Disabled
- **Manual BPM Setting When Audio Engine Disabled (`AudioEnginePanel.kt`, `AudioEngine.kt`)**: When the Audio Engine is disabled via Settings, the panel now displays the Beat Sync & Manual Tempo section with a compact manual BPM slider (40.0–200.0 BPM, default 120.0 BPM), a real-time BPM readout with an internal beat phase flashing dot, and a one-click "Reset to 120.0 BPM" button. Users can freely configure tempos offline without needing live audio hardware.
- **Phase-Continuous Offline Clock Re-Anchoring (`AudioEngine.kt`)**: When adjusting manual BPM while the audio engine is disabled or inactive, `setBpmDirectly` captures the running beat count from `CVRegistry.getSynchronizedTotalBeats()` to re-anchor the beat clock, preventing backward beat counter jumps and maintaining seamless phase continuity across BEAT LFOs and step sequencers.
- **Persistent Title Bar BPM & 4-Beat Meter (`MenuBar.kt`)**: The top title bar now persistently displays both the BPM readout and the 4-beat phase meter dots even when the Audio Engine is disabled or inactive. In manual mode, BPM text is rendered in warm amber with contextual hover tooltips explaining that the audio engine is disabled and the tempo is fixed. Clicking either the BPM readout or the 4-beat meter directly opens the Audio Engine settings panel.

### Hex-Planar (60°) & Tetrahedral 24-Chamber Kaleidoscope 3D Modes
- **Hex-Planar Display Mode (6 Planes @ 60°)**: Added Mode 3 to the universal `View` pipeline, replicating any 2D visual source across the 6 symmetry planes of the tetrahedral Coxeter group ($A_3$: $x = \pm y, y = \pm z, z = \pm x$). All 6 planes pass through $(0, 0, 0)$ at $60^\circ$ angles, sharing the exact same origin and expanding along their normals into a 12-faced rhombic dodecahedral cage when `Separation` is increased.
- **Tetrahedral Kaleidoscope Mode (24-Chamber Space Folding)**: Added Mode 4 to the universal `View` pipeline (`tetra_kaleido.vert`, `tetra_kaleido.frag`), implementing iterative Coxeter reflection folding across simple roots. Virtual camera rays fold 24 times into the tetrahedral fundamental domain, producing seamless mirror reflections across all sector boundaries.
- **Circular Disc Boundaries (`Roundness`)**: Added a modulatable `Roundness` parameter (0.0 = square, 1.0 = circle, default 1.0) with anti-aliased edge feathering. In 3D rotation, intersecting circular discs produce continuous, seamless celestial gyroscopes and armillary spheres, completely eliminating boxy corner sweeping.
- **Background Transparency & Discard Precision**: Non-luminous background fragments are cleanly discarded, ensuring both modes float transparently over the background deck with zero gray shadow artifacts.

### Unified Audio & Transient Modulator System
- **Consolidated Audio Matrix Column**: Merged the separate `AUD` and `TRIG` columns in the Preset Grid into a single, unified `AUD` column (`VAL`, `MIDI`, `LFO`, `SEQ`, `AUD`).
- **Dual Modular Audio Slots**: Each modulatable parameter now supports up to 2 independent audio slots (`Audio 1` and `Audio 2`). Slot 2 stays cleanly collapsed behind an `[ + Enable Audio Slot 2 ]` button until activated.
- **Continuous (RMS) vs Transient (Spectral Flux) Modes**: Modulators seamlessly toggle between continuous amplitude tracking (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`) and spectral flux transient detection (`audio_flux_amp`, `audio_flux_bass`, `audio_flux_mid`, `audio_flux_high`).
- **4 Selectable Frequency Bands**: Both modes operate over `Full Mix (AMP)`, `Bass (BASS)`, `Mid (MID)`, and `High (HIGH)`.
- **Response Profiles**: Added curated dynamics presets (`Instant / Raw`, `Snap`, `Punchy`, `Smooth Swell`, `Slow Pulse`, `Ambient Drift`, `Custom`) with full Attack/Decay envelope control.
- **Zero-Allocation DSP**: Computed inside the real-time audio callback loop without memory allocations and displayed via zero-latency oscilloscopes in the UI.

### 4-Platform Targeted Distribution & CI Smoke Testing
- **Targeted Platforms**: Distribution ZIP packaging targets the 4 supported architectures: Windows x64, Linux x64, macOS ARM64 (Apple Silicon), and macOS x64 (Intel). Removed `linux-arm64` from build targets due to lack of upstream Linux ARM64 native JNI binaries in `imgui-java`.
- **Automated Smoke-Test Verification**: All 4 target platform distributions are verified natively on GitHub Actions runners before release, ensuring only verified, functional binaries are published.
- **Fixed Release Notes Accumulation**: Resolved an issue where GitHub Releases accumulated and reprinted historical release notes from all previous versions.

### UI Sizing & HiDPI Double-Scale Fix
- **HiDPI Double-Scaling Resolution (`UITheme.kt`, `SettingsPanel.kt`, `UIManager.kt`, `Main.kt`)**: Removed redundant manual `systemDpiScale` calculation from UI sizing formulas. With `imgui-java` 1.86.12+, the ImGui GLFW/GL3 backends handle OS display content scaling automatically in logical pixels. Base UI font size is now directly calculated as `15.0px * (guiScalePercent / 100)`.
- **Streamlined UI Scale Controls**: Simplified Settings panel sizing controls to a single "UI Scale" slider (75%–200%, 5% steps) with updated tooltip clarifying that OS HiDPI scaling is handled automatically.

### Preset Dependency Inspection & Column Kebab Menu
- **Proactive Dependency Analysis (`PresetDependencyAnalyzer.kt`)**: Automatically inspects visual presets and live decks for reliance on disabled subsystems (`MIDI`, `Step Sequencer`, `Randomization`), offline engines (`Audio Engine`), or hidden columns in the Preset Grid.
- **Non-Destructive Alert Badges**: Displays a red `[!]` indicator alongside affected presets in the Library list and active deck monitor headers with rich, explanatory hover tooltips—never hiding presets or blocking playback.
- **Zero-Allocation Issue Evaluation**: Memoizes issue evaluation through an internal 13-bit state cache, preventing garbage collection pause jitter across 60 FPS list rendering.
- **Preset Grid Header Column Kebab (`⋮`)**: Added an inline column configuration menu to the right of the grid headers (`VAL`, `MIDI`, `LFO`, `SEQ`, `AUD`) with live status indicators, instant column visibility toggles, and one-click `[ Turn On Needed Columns ]` / `[ Enable Audio Engine ]` quick actions.

---

## Version 1.0.0-beta.40

> [!NOTE]
> **Release 1.0.0-beta.40** introduces the standalone "Colors" visual source generator, the zero-allocation Beat Tracker (`BeatTrackerEngine`, inspired by BTrack) with complex spectral difference ODF and causal dynamic programming, an overhaul of the Library browser with a unified menu bar action strip, quick audition padlock, and full keyboard navigation (including <kbd>Ctrl+F</kbd>/<kbd>/</kbd> instant search focus and <kbd>Esc</kbd> clear), dynamic on-air deck illumination proportional to the crossfader, modular Cell Config collapsible accordions with LFO advanced parameter dirty dot indicators (`•`), a 4-beat phase meter in the top menu bar, streamlined 3-column layout sizing with locked aspect ends and flexible center panel, 4-step Spacebar library ping-pong cycling (`HIDE` $\rightarrow$ `HALF` $\rightarrow$ `FULL` $\rightarrow$ `HALF` $\rightarrow$ `HIDE`), title bar click-drag library resizing, symmetrical Background Queue controls with modulation routing and dirty state checking, consolidated 2-column Audio Engine settings with zero-lag CV oscilloscopes, and slider drag-isolation fixes.

---

### Key Highlights

#### 0. Native Apple Silicon (macOS ARM64) Support via ImGui 1.86.12 (`build.gradle.kts`, `docs/developer/imgui_upgrade_guide.md`, `DECISIONS.md`)
- **Native Apple Silicon Binaries**: Upgraded `io.github.spair:imgui-java-*` from `1.86.11` to `1.86.12`, delivering Mach-O universal binaries (`x86_64` + `arm64`) for macOS. This resolves native library linkage failures (`UnsatisfiedLinkError`) on Apple Silicon Macs and unblocks native execution on `macos-arm64`.
- **Zero Breaking Changes**: Version 1.86.12 preserves 100% binary and source compatibility with existing UI code, ensuring zero risk of visual or behavioral regressions.
- **Modernization Roadmap**: Documented the full multi-architecture investigation and Phase 2 migration guide for Dear ImGui 1.92.x in `docs/developer/imgui_upgrade_guide.md`.

#### 0.1. Automated 5-Platform Binary Smoke Testing & Selective Release Gating (`Main.kt`, `build.gradle.kts`, `.github/workflows/smoke-test.yml`, `.github/workflows/release.yml`)
- **Headless Diagnostic Smoke Test (`--smoke-test`)**: Added a fast, 5-stage headless self-diagnostic test to the application entry point. Verifies JVM runtime/architecture, tests LWJGL native library linkage, tests Dear ImGui native bindings and context creation/destruction (`ImGui.createContext()`), verifies classpath resource packaging (core shaders and presets), and verifies AudioEngine fallback initialization without requiring a physical monitor or GPU.
- **CLI Flags & Info Dispatch (`Main.kt`)**: Added `--version` / `-v` (prints version, OS, architecture, and JVM runtime details) and `--help` / `-h`.
- **Launcher CLI Argument Forwarding (`build.gradle.kts`)**: Updated generated desktop launchers (`run-linux.sh`, `run-windows.bat`, `run-mac-arm.command`, `run-mac-intel.command`) to forward all command-line arguments directly to the application JAR (`"$@"` on Unix and `%*` on Windows).
- **Parallel 5-Platform CI Matrix (`.github/workflows/smoke-test.yml`)**: Automated native smoke testing on GitHub-hosted runners (`ubuntu-latest`, `ubuntu-24.04-arm`, `macos-latest`, `macos-13`, and `windows-latest`) on pull requests and workflow dispatch.
- **Selective Release Gating (`.github/workflows/release.yml`)**: Release builds run the 5-platform matrix before publishing. Only platform distribution ZIPs that pass automated smoke testing are published to GitHub Releases, preventing broken builds from reaching users while permitting functioning platforms to release even if a single platform experiences a regression.

#### 0.1. Release Packaging, Launcher Permissions, and Library Distribution (`build.gradle.kts`, `VisualSourceRegistry.kt`, `Main.kt`)
- **Executable Permissions on Release Scripts**: Fixed an issue where `run-linux.sh`, `run-mac-arm.command`, `run-mac-intel.command`, and bundled `bin/java` binaries lost executable permissions in GitHub release ZIPs. Updated `build.gradle.kts` to invoke `permissions { unix("755") }` on `FileCopyDetails` in all distribution zip tasks.
- **Library Sources & Presets Bundled in Releases**: Added `library/**` packaging into `packageThumbDrive` and all platform ZIP distributions (`zipWindows`, `zipLinux`, `zipLinuxArm`, `zipMacArm`, `zipMacIntel`), resolving runtime crashes where `VisualSourceRegistry` failed to find `library/sources/mandala`.
- **Self-Healing Bundled Source Extraction**: Configured `tasks.processResources` to bundle default visual sources into the fat JAR under `default_sources/`. `VisualSourceRegistry.loadAll()` now automatically extracts bundled default sources if `library/sources/` is empty or missing `mandala`, ensuring the application self-heals even when executed from a standalone fat JAR.
- **Accurate Error Messaging**: Corrected the startup failure exception message from `presets/sources/mandala` to `library/sources/mandala`.

#### 0.1. Sequencer, Randomization, and MIDI Settings Toggles & Defaults (`AppSettings.kt`, `SettingsPanel.kt`, `UITheme.kt`, `PresetGridPanel.kt`, `CellConfigPanel.kt`, `MenuBar.kt`, `ModulatableParameter.kt`, `Evaluators.kt`, `CVRegistry.kt`, `MidiEngine.kt`, `MidiJackWatchdog.kt`, `Main.kt`, `UIManager.kt`)
- **Step Sequencer Toggle in Settings**: Added an "Enable Step Sequencer" master toggle under Settings (`General` and `Preset Grid`), allowing users to enable or disable step sequencer execution and grid/cell visibility.
- **Default Disabled Behavior**: The Step Sequencer (`sequencerEnabled`), parameter/modulator randomization (`randomizationEnabled`), and MIDI input/mapping (`midiEnabled`) now default to disabled (`false`) for clean, deterministic, and distraction-free startup.
- **MIDI Master Toggle in Settings**: Added an "Enable MIDI" toggle under Settings (`MIDI & Controls`), safely closing hardware devices when disabled and rescanning devices upon activation. "MIDI Map" in the menu bar and grid columns are gated cleanly.
- **Modulation Bypassing**: When disabled, step sequencer and MIDI modulators are safely bypassed during parameter evaluation, and CVRegistry returns neutral 0.0 values.

#### 0.1. Continuous Constrained Random Morphing (`MorphState.kt`, `Deck.kt`, `Mixer.kt`, `MixerMonitorPanel.kt`)
- **Continuous $0.0 \leftrightarrow 1.0$ Generative Morphing**: Transformed discrete 1-shot randomization triggers (`randDeckA`, `randDeckB`, `randDeckBG`, `randDeckPV`, `randAll`) into continuous morphing controllers. Assigning an LFO or CV source smoothly morphs base values and active modulator properties between two randomized states with zero UI explosion.
- **Selective Randomization Deactivation Gating (`MorphState.kt`)**: Fixed an issue where toggling off randomization on a parameter (`param.randomizeBase = false`) or modulator property while continuous morphing was active (`randDeckA`, `randDeckB`, etc.) failed to stop randomization. `DeckMorphController` now strictly gates base value and modulator field interpolation on their respective `randomize*` flags, immediately freezing non-randomized parameters, syncing state snapshots to active values, and preventing user slider edits from being clobbered during morph cycles.
- **Unidirectional Wrap-Around Morphing (`MorphState.kt`)**: Added seamless support for non-stop, unidirectional forward flow when morph targets are modulated by Sawtooth ramps, `beatPhase`, or phase modulators ($0.0 \to 1.0$). Automatically detects ramp wraps ($1.0 \to 0.0$) and promotes the arrived state ($S_1 \to S_0$) while sampling a fresh destination target into $S_1$, ensuring the value at $v = 0.0$ matches the prior frame with zero jump cut, zero turnaround, and zero pause.
- **Pure Linear Sawtooth & Zero-Hesitation Wrap (`WaveformMath.kt`)**: When LFO asymmetry is set to either extreme (`slope >= 0.999f` for ramp up or `slope <= 0.001f` for ramp down) and `morph = 1.0f`, `calculateAdvancedLFO` now evaluates as an ideal monotonic linear ramp (`phase` or `1.0 - phase`) with instantaneous wrap at the period boundary, completely eliminating reverse fall slopes and peak deceleration. This provides seamless, non-stop continuous rotation when modulating circular and endless parameters (`MeterType.ENDLESS`, angles, hue sweeps) without arc hesitation or backward swing.
- **Flip-Flop Boundary State Machine**: Implemented latching hysteresis (`READY_FOR_ONE` / `READY_FOR_ZERO`) at the $0.01$ and $0.99$ boundaries. Crossing a boundary re-rolls the opposite state, enabling non-repeating generative visual journeys.
- **Zero-Allocation In-Place Lerp & Shortest-Path Angles**: Pre-allocated snapshot buffers and converted runtime modulator fields to mutable vars to eliminate GC allocations during per-frame lerping, with shortest-path modular interpolation for angles and hue rotations.
- **Randomizer Parameter Randomization Lockout (`ModulatableParameter.kt`, `Mixer.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `ValueParamSection.kt`, `ModulatorHeaderRow.kt`, `PresetGridRenderer.kt`)**: Explicitly disabled randomizing the base value ranges or modulators of the five Mixer randomizer parameters (`Mixer/randDeckA`, `randDeckB`, `randDeckBG`, `randDeckPV`, and `randAll`). Gated `randomizeBase` and `randomizeBaseValue()` to prevent destabilizing recursive control loops, dimmed dice toggles in Cell Config, disabled row context menu randomization, and added a contextual warning tooltip (*"It is forbidden to randomize the randomizer. Chaos would ensue."*) when hovering over the locked dice.

#### 0.1. Mandala Analytical Size Normalization (`Mandala.kt`, `Renderer.kt`, `web/renderer.js`)
- **Sum-of-Lengths Normalization ($R_{\text{target}} = 2.0$)**: Implemented fast, analytical size normalization for the Mandala visual source. Scales arm lengths $L_1 \dots L_4$ by $\text{scale} = R_{\text{target}} / \sum |L_i|$ (with zero-protection for $\sum |L_i| \le 10^{-5}$), guaranteeing that the theoretical maximum reach of the pen exactly touches $R_{\text{target}}$ and fills the screen height ($2.0 \times 0.5 = 1.0$ in NDC).
- **Morphing vs. Breathing**: Prevents visual clipping and extreme shrinkage when arm lengths vary or are modulated by LFOs, shifting dynamics to harmonic petal balance morphing while anchoring the outer bounding circle.
- **Desktop & WebGL2 Parity**: Uniformly applied in desktop OpenGL (`Renderer.kt`) and browser WebGL2 (`web/renderer.js`) pipelines, ensuring identical visual scaling and radial depth shading across platforms.

#### 0.2. Universal 3D Tri-Axial & 2D View Stage (`Deck.kt`, `Renderer.kt`, `PresetGridTabs.kt`, `PresetModels.kt`, `tri_planar.vert`, `tri_planar.frag`, `view2d.frag`)
- **Universal 3D Orthogonal Transformation**: Elevates any 2D visual source (Chladni, Dynamic Spiral, Attractor, Video, etc.) into a 3D rotating structure across intersecting orthogonal planes ($XY$, $YZ$, $ZX$) at $90^\circ$ angles, forming a holographic gyroscope / celestial sphere, or a 6-sided Cube Cage.
- **Universal 2D View Transformation (`view2d.frag`, `rawSource2DFBO`)**: When in standard 2D mode (`3D Mode < 0.5`), universal View parameters `Zoom` and `Rotate Z` (Roll) actively transform the 2D source before entering the feedback loop. Operates at full native widescreen resolution via `rawSource2DFBO` with isotropic aspect-ratio-corrected rotation and transparent border blanking.
- **Contextual View UI Parameter Visibility (`PresetGridTabs.kt`)**: Reordered the View tab to place universal controls at the top (`Zoom`, `Rotate Z`, `3D Mode`). Automatically hides 3D-only controls (`Rotate X`, `Rotate Y`, `3D Persp`, `Depth Dim`, `Separation`, `Blend Mode`) when in 2D mode (`3D Mode < 0.5`), eliminating confusing non-functional sliders while preserving parameter paths and modulation bindings.
- **Camera Proximity Headlight (`Depth Dim`)**: Applies virtual camera-aligned point lighting with inverse-square falloff in view space. Elements closest to the camera stay crisp and brilliant, while elements sweeping into the distance dim smoothly into atmospheric haze.
- **Perspective-Correct Interpolation & Square Plane Aspect**: Rendered 2D sources into a square $1:1$ `rawSourceFBO` (`FBO(height, height)`) to eliminate non-uniform plane stretching in 3D mode, and emitted homogeneous clip coordinates with $w$-buffering in `tri_planar.vert` for hardware perspective-correct texture mapping without affine distortion.
- **Preset Serialization & Backward Compatibility**: Fully serialized under `viewParameters` in `.lsd` preset files with default fallback to ensure all legacy presets load without interruption.

#### 0.3. Mandala Architecture Unification (`Mandala.kt`, `PresetGridTabs.kt`, `PresetGridPanel.kt`, `PresetModels.kt`, `WebPresetSerializer.kt`, `ValueParamSection.kt`, `CellConfigPanel.kt`)
- **Streamlined Source Parameters & View Tab Cleanup**: Cleaned up Mandala's `meta.json` declaration so generator geometry controls (`Lobes`, `Recipe Select`, `L1`–`L4`, `Thickness`, `Hue Offset`, `Hue Sweep`, `Depth`) populate the `SRC` tab in natural declaration order. Removed legacy hardcoded Mandala transform parameters (`Zoom`, `Rotate Z`, `Rotate Y`, `Rotate X`, `3D Persp`), eliminating duplicate spatial controls in the `View` subtab and delegating all spatial transformation to the universal Deck View pipeline.
- **Streamlined Source Dropdown**: Pruned hardcoded "Mandala" menu entries from `PresetGridTabs.kt` and `PresetGridPanel.kt` in favor of single-loop iteration over `VisualSourceRegistry.availableSources`.
- **Preset Model Simplification & Dynamic Recipe Restoration**: Eliminated `MandalaRecipeDto` and the redundant `recipe` field in `DeckPresetDto`. Presets now serialize and restore `Lobes` and `Recipe Select` as standard modulatable parameters; upon loading, `Mandala.update()` evaluates those parameters and restores the matching Fourier ratio automatically.
- **Hierarchical Parameter Paths**: Removed the legacy custom `Mandala.getParameterPaths()` override; Mandala parameters now follow the universal hierarchical pattern (`Deck A/Mandala/<param>`) alongside all other visual sources.
- **Web TV Broadcast Parity**: Simplified `WebPresetSerializer.serializeDeck` to a single generic loop with a concise extension for Mandala Fourier frequencies (`a`, `b`, `c`, `d`), maintaining 100% backward compatibility with WebGL2 TV clients.
- **Web TV Power Off Switch & Retro CRT Collapse Animation (`web/ui.js`, `web/dsp.js`, `web/renderer.js`, `web/shaders/crt_post.frag`)**: Fixed a bug where the Web TV power switch was a one-way toggle that could not turn the TV off once powered on, and added an authentic 3-phase **CRT Electron Beam Collapse** shutdown animation. Powering down now:
  1. *Vertical Collapse*: Compresses the active visualizer vertically into an intensely bright, overdriven phosphor line across the center.
  2. *Horizontal Shrink*: Pulls the line horizontally inward from both edges, shrinking it into a brilliant pinpoint dot at the center of the tube.
  3. *Phosphor Decay*: Decays the central dot with natural phosphor persistence until it gently fades into total darkness.
  4. Audio & Indicators: Suspends the Web Audio context, cuts the live Icecast stream, flips the physical switch knob up, and dims the station LED.
- **Pristine Web TV Display Presentation (`web/shaders/crt_post.frag`, `web/renderer.js`)**: Streamlined the Web TV post-processing pass to display the live visualizer with maximum fidelity when powered on. Stripped out steady-state distortion artifacts (corner dimming/vignette, interlacing/scanlines, RGB shadow mask grating, chromatic aberration, and barrel warp) while fully preserving the 1.5s CRT warmup expansion and the 3-phase beam collapse shutdown sequence.
- **Independent Web TV Presets & Playlists (`web/presets/`, `web/playlists/`, `web/autopilot.js`)**: Decoupled the Web TV client completely from the desktop application's `library/` folder. Replaced the `web/library` symlink with dedicated, self-contained `web/presets/` and `web/playlists/` directories populated with curated web visualizers (`mandala_flow`, `spiral_drift`, `cosmic_ribbon`, `hyperspace_slice`, `attractor_flow`, `ambient_bg`, `dark_spiral`) and default playlist files (`default.lsdset`, `default_bg.lsdset`). Updated `autopilot.js` to resolve relative preset and playlist paths to these web-specific directories by default.

#### 0.4. Renderer Polymorphism & Draw Topology Dispatch (`DynamicVisualSource.kt`, `Mandala.kt`, `HyperMesh.kt`, `Renderer.kt`)
- **Zero Source-Type Knowledge in Renderer**: Completely eliminated `is Mandala` and `is HyperMesh` branching and private helper methods (`renderMandala()`, `renderHyperMesh()`) from `Renderer.kt`. The main rendering pipeline now collapses into a single, unified execution path.
- **Polymorphic `drawTopology()` Dispatch**: Introduced `open fun drawTopology()` on `DynamicVisualSource`, delegating vertex attribute binding and geometry draw calls to each visual source:
  - Default: Renders a fullscreen quad via `Geometry.drawFullscreenQuad()`.
  - `Mandala`: Binds ribbon VAO and draws triangle strips via `glDrawArrays(GL_TRIANGLE_STRIP, 0, (POINTS + 1) * 2)`.
  - `HyperMesh`: Binds 4D polychoron strut and node VAOs and renders indexed triangles via `glDrawElements(GL_TRIANGLES, ...)`.
- **Encapsulated GPU Geometries & Clean Lifecycle**: Moved ribbon VAO/VBO creation and disposal directly into `Mandala.kt` (`initGeometry()`, `dispose()`, and shallow handle sharing in `clone()`), ensuring leak-free and double-free-safe lifecycle tracking.
- **Unified Common Uniforms**: Common frame uniforms (`uAlpha`, `uResolution`, `uTime`, `uAspectRatio`) are now set in a single location for all dynamic sources inside `Renderer.render()`.


#### 0.5. macOS Apple Silicon & Intel Packaging & Launch Reliability (`build.gradle.kts`, `run-mac-arm.command`, `run-mac-intel.command`)
- **Resolved macOS Bundled JRE Resolution**: Fixed an issue where macOS distribution launcher scripts reported *"Bundled JRE not found. Trying system java..."*. Adoptium macOS `.tar.gz` distributions extract using standard Apple bundle hierarchy (`Contents/Home/bin/java`). Launchers (`run-mac-arm.command` and `run-mac-intel.command`) now probe both `jre/macos-<arch>/Contents/Home/bin/java` and flat `jre/macos-<arch>/bin/java` before falling back to system Java.
- **Main OS Thread Execution (`-XstartOnFirstThread`)**: Fixed fatal startup crash (`IllegalStateException: GLFW may only be used on the main thread and that thread must be the first thread in the process`) on macOS Apple Silicon and Intel. Both bundled JRE and system fallback paths in the `.command` scripts, as well as Gradle `JavaExec` tasks when running on macOS, now strictly pass `-XstartOnFirstThread`.
- **JDK 21+ Native Access Warning Suppression**: Added `--enable-native-access=ALL-UNNAMED` to all launchers (Windows, Linux, macOS) and `JavaExec` to eliminate restricted method warnings for LWJGL/Unsafe native callers when running on modern JVMs.
- **macOS Gatekeeper Quarantine Stripping**: Added `xattr -dr com.apple.quarantine jre 2>/dev/null || true` in macOS launcher scripts to ensure bundled JREs downloaded via GitHub zip archives are not blocked by Gatekeeper.
- **Zip Executable Permissions**: Configured Gradle distribution packaging (`zipMacArm`, `zipMacIntel`) to enforce `755` permissions across all binaries in `bin/`, `jspawnhelper`, and `.command` scripts.

#### 1. Performance-Driven UI & Ergonomics Enhancements (`UIManager.kt`, `PresetListPanel.kt`, `DeckControlPanel.kt`, `MenuBar.kt`, `CellConfigPanel.kt`)

- **Horizontal Scroll Wheel Isolation & Layout Stabilization (`UIManager.kt`, `LibraryPanel.kt`, `PresetGridPanel.kt`)**: 
  - Globally disabled `io.mouseWheelH` per frame to prevent unintended horizontal panning across all panels when using two-finger trackpad drag, ThinkPad TrackPoint (nipple) center-button scrolling, or horizontal scroll wheels.
  - Sized Library columns dynamically against `getContentRegionAvailX()` and inner spacing instead of outer window width, eliminating the overflow that caused the 4 columns to shift left and right.
  - Enforced `NoScrollbar` on outer Library and Mixer/Monitor panels and added horizontal scroll resets (`setScrollX(0f)`) across child panels to prevent parameter labels or cards from drifting off-screen.
- **Library & Preset Grid Vertical Kebab More Actions Button (`⋮`) (`Icons.kt`, `BrowserRowMoreButton.kt`, `PresetListPanel.kt`, `PlaylistEditorPanel.kt`, `QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`, `PresetGridRenderer.kt`)**: 
  - Corrected `Icons.MORE_VERTICAL` (`\ue0b7`) and `Icons.MORE_HORIZONTAL` (`\ue0b6`) to match Lucide's TrueType Private Use Area codepoints for true vertical kebab (`⋮`) and horizontal ellipsis, replacing an incorrect mouse pointer arrow glyph.
  - Widened button hit target width to 28px across Library panels and the Preset Grid, enlarged dot size to 3.5px radius with 9.0px vertical spacing, and centered the three vertical dots cleanly with subpixel precision using geometric circle rendering (`ImDrawList.addCircleFilled`), eliminating font advance skew where dots were touching the right border.
  - Implemented seamless popup switching: clicking any kebab button immediately closes any currently open popup and opens the target row's context menu in a single click, suppressing hover tooltips when the menu opens.
  - Sized list row selectables and invisible buttons across the Library and Preset Grid to maintain dedicated hit-testing bounds, ensuring left-clicks and right-clicks on the kebab button reliably trigger context menus on the first click without selectable event stealing or hover flicker.
  - Added the vertical kebab button to parameter rows in the Preset Grid (flush right within the parameter label column), providing left-click discoverability for row actions (Randomize row, Copy/Paste modulations, Reset to default, Clear CVs/MIDI, and Add/Edit Parameter Notes).
- **Instant Search Focus (<kbd>Ctrl+F</kbd> / <kbd>/</kbd>) & Clear (<kbd>Esc</kbd>)**: Pressing <kbd>Ctrl+F</kbd> or <kbd>/</kbd> (when not focused on a text input) automatically opens the Library (if hidden) and focuses the preset search bar with text highlighted. Pressing <kbd>Esc</kbd> while the search box is active clears the filter query and returns focus back to the preset table for seamless keyboard navigation (`1`–`4`, `Q`, `Shift+Q`, `↑`, `↓`).
- **Dynamic HiDPI Scale Detection & Multi-Monitor Adaptation**: Separated UI scaling into dynamic system DPI auto-detection (`systemDpiScale`) and persistent user zoom preference (`guiScalePercent`). Liquid LSD now queries GLFW content scale on every startup and responds dynamically via `glfwSetWindowContentScaleCallback` when dragging windows across displays or changing OS scaling, automatically rebuilding the Dear ImGui font atlas and UI geometry.
- **Dynamic "On-Air" Deck Illumination & Glow**: Decks A and B dynamically scale header, toolbar, and border brightness based on `Mixer.crossfade` position. Active on-air decks render at 100% full saturation with bright glowing borders, while off-air decks smoothly dim to $\sim 35\%$ opacity.
- **Cell Config Modular Accordions & Dirty Indicator (`•`)**: 
  - Converted LFO 2 from an auto-hiding section into an explicit collapsible accordion (`▶ LFO 2 (Modulator)` / `▼ LFO 2`).
  - Wrapped Audio and Trigger multi-band modulators in collapsible headers (`Amplitude`, `Low / Bass`, `Mid`, `High`, `Onset`, `Accent`).
  - Grouped secondary LFO 1 parameters (`Phase Offset`, `Morph`, `Hold`, `Slew`) into an `Advanced Parameters` accordion with an illuminated dirty indicator dot (`•`) whenever any collapsed parameter differs from its default.
  - **Square Wave PWM Duty Cycle & Plateau Hold Ceiling (`Lfo1Section.kt`, `Lfo2Section.kt`, `WaveformMath.kt`, `Evaluators.kt`)**:
    - Fixed Square wave shape preset button setting `Hold` to `0.5` (a 50% trapezoid); selecting Square now sets `Hold` to `0.999` for true sharp vertical transitions.
    - Raised the upper bound for the `Hold` parameter from `0.990` to `0.999` across math evaluators and UI sliders with zero-division protection.
    - Implemented variable Pulse Width Modulation (PWM) for `Waveform.SQUARE` using `slope` (duty cycle from 1% to 99%, default 50%).
    - Dynamically adapts UI labels and quick-presets: when Square wave is selected, "Slew" / "Asymmetry" becomes "Duty Cycle" / "Duty Preset" with 10%, 50%, and 90% pulse buttons.
    - **Expressive Square Wave & Duty Cycle Waveform Icons (`CustomIconButton.kt`, `Lfo1Section.kt`, `Lfo2Section.kt`)**: Added the leading rising edge to the square wave icon so both leading and trailing edges are fully drawn. Added dedicated 10% duty (`WaveShape.SQUARE_10`) and 90% duty (`WaveShape.SQUARE_90`) waveform icons for the duty cycle preset buttons in Cell Config (LFO 1 and LFO 2), visually differentiating narrow 10% pulse, balanced 50% square, and wide 90% pulse states.
  - **Per-Cell Persistent Accordion State & Default Closed Behavior**: Scoped all Cell Config modulator controls to their respective parameter key and CV source ID (`${cell.paramKey}_${cell.cvSourceId}`). Accordion folds in Audio and Trigger multi-band sections as well as LFO 2 now start closed by default (with LFO 1 remaining open and accessible), and opening/closing folds on one grid cell remains isolated and independent from other cells.
  - **Middle-Click Reset, Preset Grid Mute & Hardware Click Latching (`UIManager.kt`, `PresetGridRenderer.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`)**: Middle-clicking any modulation cell (MIDI CC, LFO, AUD, TRIG) instantly toggles its bypass/mute state, and middle-clicking parameter rows or value cells resets parameters back to defaults. In Cell Config, middle-clicking on any slider track, text input box, or variable name label instantly resets the parameter to its factory default value with contextual hover tooltips. Added a chained GLFW mouse button callback with a single-frame latching queue in `UIManager` to ensure instantaneous hardware middle-click releases (such as ThinkPad TrackPoint center buttons and Linux/libinput scroll emulation taps) are never dropped during event polling or frame rate dips.
- **Preset Grid Save & Save As Shortcuts (<kbd>Ctrl+S</kbd> / <kbd>Shift+Ctrl+S</kbd>)**: Added global keyboard shortcuts in the Preset Grid. Pressing <kbd>Ctrl+S</kbd> saves the active deck's preset directly (or opens the Save Preset As dialog if the preset is untitled). Pressing <kbd>Shift+Ctrl+S</kbd> opens the Save Preset As modal for the active deck with auto-suggested duplicate naming (`_copy`). Both shortcuts are cleanly ignored when the Preset Grid is focused on the Mixer or an empty deck.
- **Preset Grid Horizontal Column Headers & Dedicated `SRC` Tab Layout**: Converted column titles into horizontal headers (`VAL`, `MIDI`, `LFO`, `AUD`, `TRIG`) sized at `UITheme.FontLevel.BODY`. Separated visual source selection from parameter tab navigation: the visual generator dropdown (`[Source ▾]`) resides in the Preset Grid title bar beside `"Preset Grid"`, while parameter sub-tab navigation is standardized with a dedicated, compact **`SRC`** tab (`[SRC] [FX] [View]`) above the first parameter name with a 24px left indent, preventing long visual source names from crowding the section tabs against the CV columns. Left side tabs (`MIX`, `A`, `B`, `BG`, `PV`) align with the second parameter row.
- **Deck A/B Mix Mode Exposed in PatchGrid (`MIX` Tab)**: Added a dedicated row for `mix mode` in the Preset Grid Mixer tab. It is exposed as a non-modulatable discrete parameter with a dropdown combo selector in the `VAL` configuration panel supporting all 5 blend equations: `0: Add (ADD)`, `1: Screen (SCREEN)`, `2: Multiply (MULT)`, `3: Max (MAX)`, and `4: Crossfade (XFADE)`.
- **Mixer Master Monitor Click Shortcut (`MixerMonitorPanel.kt`)**: Left-clicking the main output monitor in the Mixer / Monitor panel immediately switches the Preset Grid focus to the Mix tab (`activeTopTab = "Mixer"`), complementing the existing deck monitor click shortcuts (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`).
- **Harmonized 5-Column CV Modulation Palette & Theme Unification (`CvTheme.kt`, `PresetGridRenderer.kt`)**: Redesigned the modulation color scheme for the pruned 5-column layout across `PresetGridPanel`, `CellConfigPanel`, and `AudioEnginePanel` into 5 distinct color wheel quadrants: `VAL` (Mint Cyan), `MIDI` (Bright Orchid Violet), `LFO` (Electric Sky Blue), `AUD` (Warm Amber Gold), and `TRIG` (Hot Coral Rose). Replaced all hardcoded/duplicate color tables with centralized `CvTheme` calls. Removed per-column cell knob/arc tinting in favor of inheriting the active theme text color (`ImGuiCol.Text`), guaranteeing universal contrast and clean visual clarity across all light and dark themes.
- **4-Beat Phase Meter in Top Menu Bar**: Added a 4-dot quarter-note beat meter (`[ ● ○ ○ ○ ]`) immediately preceding the `BPM` display in the top Menu Bar, giving instant, zero-clutter confirmation of audio sync and downbeat alignment in sync with the Beat Tracker.
- **Safe Visual Source Switching & Stale Parameter Guard (`DeckPresetController.kt`, `PopupManager.kt`, `CellConfigPanel.kt`)**: Added a user confirmation modal dialog when switching a deck's visual generator if an active preset is loaded or has unsaved edits, preventing accidental preset file overwrites. Automatically clears active preset bindings, resets stale cell selections in `PresetGridState`, updates subtabs, and adds defensive live parameter path resolution in `CellConfigPanel` to eliminate orphaned/detached parameter modulators.

#### 1. Streamlined 3-Column Layout Architecture & Spacebar Library Cycling (`UIManager.kt`, `LibraryPanel.kt`)

- **Locked Aspect-Ratio End Columns**: Sized Column 1 (Preset Grid) dynamically to its active columns and Column 3 (Mixer / Monitor) strictly to its aspect-ratio preview capacity, eliminating wasted horizontal letterboxing and removing manual column splitters.
- **Flexible Middle Column & Library Spanning**: Configured Column 2 (Cell Config) and the docked Library to flexibly absorb all remaining horizontal workspace width across all display resolutions.
- **4-Step Spacebar Library Cycling**: Tapping <kbd>Space</kbd> (when not focused on a text input or search bar) cleanly cycles through `HIDE` $\rightarrow$ `HALF` $\rightarrow$ `FULL` $\rightarrow$ `HALF` $\rightarrow$ `HIDE`.
- **Title Bar Drag-to-Resize & Standardized Window Controls (`LibraryPanel.kt`)**: Replaced the left-hand layout buttons with right-aligned window decorations matching the top title bar: `[-]` (Minimize to bottom dock / Restore) and `[□]` / `[❐]` (Maximize to Full / Restore to Half). Dragging empty space in the Library menu bar vertically resizes the Library height smoothly, with double-click snapping to 50% height.

#### 2. Static Website & Documentation Export Generator (`SiteGenerator.kt`, `build.gradle.kts`, `website/`)

- **Native JVM Static Site & Docs Generator**: Added `./gradlew buildWebsite` (and `./gradlew exportGreenjon`) task to compile the complete Liquid LSD product website, documentation viewer, and offline documentation archive directly into `./greenjon/` for manual FTP deployment to `greenjon.com`.
- **Single Source of Truth**: Sourced documentation dynamically from `docs/` and `RELEASE_NOTES.md`, and version badges/release download links directly from Gradle metadata (`project.version`).
- **Responsive Media & Showcase Layout**: Included responsive video tutorial container slots, interactive screenshot lightbox galleries, and offline ZIP package bundling (`greenjon/docs.zip`).
- **Centralized Media Export & Web Subsystem Documentation**: Added dedicated User Guides and Developer References for disk persistence, real-time MP4 video recording, offline 4K render studio with motion blur, live WebSocket broadcasting, retro CRT TV player controls, and relay server protocols.

#### 2. Standalone "Colors" Visual Source & Mandala Background Extraction (`library/sources/colors`, `Mandala.kt`, `Renderer.kt`)

- **Extracted "Colors" Visual Source**: Packaged solid-color and plasma generation capabilities into a new standalone visual source (`library/sources/colors/`) featuring `Style` (0 = Off, 1 = Solid, 2 = Plasma), `Hue`, `Sat`, `Val`, `Sweep`, `Speed`, and `Zoom` parameters.
- **Mandala Source Simplification**: Removed legacy background parameters (`Bg Style`, `Bg Feedback`, `Bg Hue`, `Bg Sat`, `Bg Val`, `Bg Sweep`, `Bg Speed`, `Bg Zoom`) from Mandala's parameter metadata, shaders, and UI layout.
- **Rendering Pipeline Cleanup (`Renderer.kt`, `Deck.kt`)**: Removed legacy secondary background passes from `renderMandala()` and `renderDeck()`, along with `Deck.getOutputTexture()` branch hacks and the unused `background.frag` resource shader.
- **Uniform Multi-Deck Compositing**: Any deck layer (`Deck A`, `Deck B`, `Deck BG`, or `Deck PV`) can now seamlessly host the `Colors` generator with full modulation and feedback support.

#### 2. Beat Tracker Real-Time Engine & Complex Spectral Difference ODF (`llm.slop.liquidlsd.audio.BeatTrackerEngine`)

- **Beat Tracker & Causal Dynamic Programming Architecture**: Fully integrated a stateful, zero-allocation beat tracking engine (`BeatTrackerEngine`) modeled on BTrack (Adam Stark) and the Dan Ellis causal DP beat tracker.
- **Complex Spectral Difference ODF**: Evaluates a 512-point Radix-2 Cooley-Tukey FFT with pre-computed twiddle factors and 2nd-order phase trajectory prediction to detect pitched notes and percussive transients while suppressing steady tones.
- **Two-State Multi-Band Periodicity Estimation**: State 1 (Acquisition) searches 40–200 BPM across circular history buffers; State 2 (Locked Tracking) constrains search to $\pm 15\%$ around current tempo period with $\pm 2.0$ BPM/beat human tracking inertia and harmonic comb unwrapping.
- **Causal Dynamic Programming Recurrence**: Pre-computed $\log(\tau)$ table (`logTauTable`) evaluates DP recurrence without transcendental `Math.log()` calls on the real-time audio thread.
- **Zero-Allocation Phase & Cosine Queries**: Exposes primitive queries (`getPhase(t)`, `getCosine(t)`, `getPhaseAndCosine(t, out)`, `getPhaseAndCosinePacked(t)`) for continuous visual phase modulation $\cos(2\pi \phi(t))$ with zero object allocations on the render thread.
- **Breakdown Flywheel Tempo Retention ("Dead Reckoning")**: When incoming audio energy drops below analysis thresholds (such as during quiet breakdowns, vocal bridges, or silence), `BeatDetector` and `BeatTrackerEngine` retain the last confirmed tempo without decaying to 120.0 BPM. The phase accumulator continues coasting at the track tempo with zero slew drift and suppressed phase nudges, ensuring beat-synced visuals remain locked across breakdowns and eliminating re-acquisition delay at the drop.
- **Legacy Beat Detection Algorithm & Slider Removal**: Removed obsolete detection modes (`BeatDetectionMode.AUTOCORRELATION`, `ENERGY_DIFFERENCE`, `RESONATOR`) and deprecated sliders (`Analysis Length`, `Energy Threshold`, `PLL Adaptation`, `Resonator Q`) from `AudioEngine` and `AudioEnginePanel`, standardizing the audio subsystem entirely on the Beat Tracker with clean target band selection (`LOW`, `MID`, `HIGH`, `UNFILTERED`) and presets (`High Accuracy`, `Balanced`, `Eco`).
- **Leaky Decay Hysteresis Shock Absorber**: Implemented graceful leaky decay (`dt * 2.0f`) with capped accumulated stability (`1.5 * stabilityLockDurationSec`) in `BeatTrackerEngine`'s tempo lock state machine, preventing periodic 4-second hiccups and frame glitches from dropping lock while allowing quick adaptation on true tempo changes.
- **Decoupled Periodic Autocorrelation**: Decoupled multi-second autocorrelation calculation from the per-block rate to run periodically every 4 blocks (~46 ms, ~21.5 Hz) with physical time-step scaling ($dt_{\text{interval}} = dt \cdot 4$), slashing loop iterations by 75% without compromising tempo lock speed or accuracy, preventing real-time audio buffer underruns (XRUNs).
- **Lock-Free Audio Recording Ring Buffer (`RealtimeRecorder.kt`)**: Replaced `ArrayBlockingQueue` (which used `ReentrantLock` mutexes) with a zero-allocation, wait-free Single-Producer Single-Consumer (`SpscQueue`) bounded ring buffer for live audio block tapping in `pushAudioBlock()`, eliminating lock contention and priority inversion risks on the real-time audio thread.
- **Subnormal / Denormal Float Flushing (`BiquadFilter.kt`)**: Flushes recursive state variables (`z1`, `z2`) to zero when $|\text{state}| < 10^{-15}\text{ f}$, preventing CPU microcode traps and pipeline stalls during trailing audio silence or decaying signal tails.

#### 3. Library Menu Bar Action Strip, Quick Audition Padlock & Keyboard Navigation (`llm.slop.liquidlsd.ui.browser`)

- **Unified Menu Bar Action Strip**: Migrated the preset routing toolbar (`[🔒] [A] [B] [BG] [PV] [Q] [BGQ]`) into the top Library Menu Bar with widened buttons and centered layout.
- **Global 4-Column Preset Selection**: Single-clicking or navigating any item across Preset Library, Playlist Editor, A/B Play Queue, or Background Queue sets a unified global selection and clears other columns.
- **Quick Audition Latch (`[🔒]`) & Smart `PV` Auto-Latch**: Toggling the padlock button arms sticky audition mode and auto-latches to **Deck PV** (Preview) by default. Clicking another deck button (`A`, `B`, `BG`) switches the latch target, while clicking the active deck button unlatches it.
- **Seamless Focus Restoration & Keyboard Hotkeys**: Restores keyboard navigation focus to selected items after clicking routing buttons. Added instant hotkeys `Q` (append to Play Queue) and `Shift+Q` (append to Background Queue) alongside `1`–`4` deck quick-load keys.
- **Column Title Bars & Full-Width Filter Bars**: Added top header title bars across all four columns (`Presets`, `Playlists`, `Queue`, `BG Queue`), allowing search input and playlist dropdowns to span full column width.

#### 4. Symmetrical Background Queue Management & Modulation Parity (`llm.slop.liquidlsd.presets`)

- **Complete Playlist & Queue Parity**: Added BG Queue context menu options ("Play now in BG Queue", "Insert into BG Queue after current", "Add to bottom of BG Queue"), bidirectional routing between queues, and dedicated `.lsdset` export.
- **Modulation & MIDI Auto-Advance**: Added `Mixer/bgQueuePrev` and `Mixer/bgQueueNext` modulatable parameters with MIDI CC inputs (`Global/bgQueuePrev`, `Global/bgQueueNext`).
- **Robot Icon Queue Toggles**: Replaced text checkboxes with robot toggle buttons (`Icons.BOT` / `Icons.BOT_OFF`) for both A/B Queue (`AUTO-VJ`) and Background Queue (`AUTO-BG`).
- **Deck BG Dirty State Protection**: Symmetrically guards BG Queue auto-advances with `PresetManager.isDeckDirty` respecting `UITheme.autoVjDirtyBehavior`.

#### 5. Consolidated Audio Engine Settings & UI Polish (`llm.slop.liquidlsd.ui`, `llm.slop.liquidlsd.cv`, `llm.slop.liquidlsd.audio`)

- **Decoupled Audio-Rate CV History Pushing**: Directly appends audio RMS and onset signals (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`, `trigger_onset`, `trigger_accent`) to `CvHistoryBuffer` ring buffers inside `AudioEngine.processAudio()` at the audio block rate (~86–344 Hz). Eagerly caches buffer references on `AudioEngine` to maintain zero-allocation, lock-free JACK callback safety, making audio oscilloscopes immune to UI frame drops, GC pauses, or render-thread hitches.
- **Frame-Delta Beat Clock Extrapolation**: Replaced the static flatline clamp (`safeBeats = current`) in `CVRegistry.getSynchronizedTotalBeats()` with elapsed frame-delta forward extrapolation (`current + frameDtSec * (bpm / 60.0)`), completely eliminating periodic oscilloscope freezes, flatlines, and stutter on `beatSine`, `beatPhase`, and beat-synced LFO modulators.
- **Anchor Block Duration Alignment**: Fixed beat anchor timestamps in `AudioEngine.kt` to reflect `currentTime + blockDurationNs`, perfectly aligning `anchorTimeNs` with `totalBeats` calculated at the end of the processed block.
- **Two-Column Audio Engine Settings Tab**: Consolidated driver selection, JACK auto-reconnect, beat detection settings, interactive dual-headed BPM range slider, input gain, and sound-derived CV oscilloscopes into a balanced 2-column layout in Settings.
- **Zero-Lag Oscilloscope Slicing**: Fixed `CvHistoryBuffer.copyTo()` to display the true latest chronological window without latency delay, and expanded the trace buffer to 400 samples.
- **Hardware Device Caching**: Cached ALSA/JavaSound device introspection in `AudioEngine.kt` to prevent per-frame querying handle leaks and out-of-memory crashes.
- **Full Audio Engine Settings Persistence**: All Audio Engine configurations—including audio backend selection (`AUTO`, `JACK_ONLY`, `JAVASOUND_ONLY`), hardware input device selection, manual BPM lock toggle, manual BPM slider, onset target frequency band (`LOW`, `MID`, `HIGH`, `UNFILTERED`), beat tracker presets, and BPM search range floor/ceiling—are now serialized to `lsd-settings.properties` and restored on startup.
- **Theme High-Contrast Palette**: Refined frame borders and contrast across Boring, Solarized, Lunarized, and Neon themes.
- **Deck PV Standardization**: Replaced all remaining legacy "Deck C" references across codebase, UI, and documentation with "Deck PV" (Preview).

---

### 📜 Full Commit History (v1.0.0-beta.30 → v1.0.0-beta.31)

- `ea3bddd` fix(ui): prevent window dragging on slider click-drag and standardize settings sliders
- `b03064b` Fix Audio Engine oscilloscope display lag, zero-center BeatSine, and expand history window
- `ae82e39` fix(audio,ui): eliminate beat clock hesitation, enforce monotonic sync extrapolation, and refine audio settings layout
- `d5ec95a` feat(audio,ui): add low-signal 120 BPM fallback, tempo stability gating, and theme contrast enhancements
- `1d55d71` fix(audio): cache input hardware devices and prevent OOM crash in AudioEnginePanel
- `6457da1` refactor(deck): remove legacy Deck C compatibility layer and enforce Deck PV across codebase
- `65cd5ff` Replace AUTO-VJ and AUTO-BG checkboxes with robot icon toggle buttons
- `6b41f59` fix(ui): fix ColorTuner z-order, close handling, double scrollbar, and column clipping
- `0058614` ui: consolidate Audio Engine into Settings tab with zero-allocation monitor drawer
- `1bc689f` feat(ui,library): add seamless focus restoration, continuous arrow navigation, and Q/Shift+Q queue hotkeys
- `07e37ac` docs(library): update user guide and release notes for column title bars and button layout
- `458e5bd` feat(ui,browser): add panel title headers and full-width search/dropdown bars across library views
- `92c5903` refactor(ui,presets): unify deck transition guards across all load and new preset pathways
- `b670b1d` fix(presets,ui): resolve false dirty state after preset load and clean up browser toolbar layout
- `0a269a5` feat(ui,browser): center action toolbar in menu bar and enable focus-based keyboard navigation
- `c89f920` feat(ui,browser): add unified library menu bar action strip and quick audition lock
- `c818573` feat(ui,grid): add grid shortcuts, middle-click mute, deck quick-load keys, and shortcuts settings page
- `bb2f7a2` feat(audio,sync): normalize audio CV follow scaling and add desktop-to-web sync tooling

---

## Version 1.0.0-beta.30

> [!NOTE]
> **Release 1.0.0-beta.30** introduces live WebSocket broadcasting from Liquid LSD Desktop, the standalone browser-based WebGL2 visualizer with 10+ shader ports, real-time Web Audio DSP streaming, an interactive retro CRT TV shell with phosphor/scanline post-processing, and a stateless Node.js relay server with 24/7 Autopilot fallback.

---

### Key Highlights

#### 1. Live Web Broadcasting & Desktop WebSocket Broadcaster (`llm.slop.liquidlsd.broadcast`)

- **Zero-Impact Asynchronous Architecture (`BroadcastEngine.kt`)**: Decoupled WebSocket broadcaster running on a dedicated daemon background thread (`BroadcastEngine-IO`), ensuring 0 ms impact on JACK audio processing and GLFW/OpenGL frame rates.
- **Non-Blocking WebSocket Dispatch**: Protected asynchronous transmission using `CompletableFuture` to prevent transmission queue buildup, memory leaks, and `IllegalStateException` on high-latency or slow network connections.
- **Throttled Parameter Delta Streaming**: Dispatches full state snapshots (`state_full`) upon initial handshake or preset switching, and lightweight differential patches (`state_delta`) throttled at a configurable rate (default 25 Hz) during live parameter adjustments. Includes JSON `null` deletion semantics when swapping visual source types.
- **Live Modulation Tracking**: Transmits real-time modulated parameter values (`param.value`) instead of static knobs, reproducing dynamic audio reactivity on remote TV clients.
- **Dedicated Web Broadcast Settings & Menu Bar HUD**: Added "Web Broadcast" category in `SettingsPanel.kt` (relay URL, auth token, auto-connect, rate limits) and a live `[LIVE]` status indicator with one-click broadcast toggle in `MenuBar.kt`.

#### 2. Standalone WebGL2 Core Visualizer & Multi-Shader Parity (`web/`)

- **Zero-Dependency Browser Pipeline**: Complete browser-based WebGL2 / GLSL ES 3.0 port of the core multi-pass rendering pipeline (`Mandala`, `DynamicSpiral`, `feedback.frag`, `mixer.frag`, `blit.frag`).
- **Full Visual Source Parity (10+ Shaders)**: High-performance WebGL GLSL ports for `Mandala` (vert/frag), `DynamicSpiral`, `AttractorFeedback`, `Chladni`, `Gyroid`, `HyperSlice`, `Icosahedron`, `IcosaDodeca`, and `IcosaV3`.
- **Ping-Pong Feedback FBOs**: Implemented `RGBA16F` half-float framebuffer textures with `EXT_color_buffer_float` and automatic fallback to `RGBA8`.
- **Mathematical Geometry Engine (`web/icosahedron_math.js`, `web/evaluator.js`)**: Client-side mathematical evaluation for complex plane math, Du Val stellation planes, and $H_3$/$H_4$ symmetry folding.

#### 3. Retro TV Shell & CRT Post-Processing (`web/tv.css`, `web/ui.js`, `web/shaders/crt_post.frag`)

- **Interactive Retro TV Shell**: Wrapped the browser visualizer in a realistic retro CRT TV bezel with an illuminated LED station indicator (`SPAZ RADIO • CH.1` / `SPAZ RADIO • LIVE`), clickable physical power toggle, and mouse/touch draggable rotary volume dial.
- **CRT Warmup & Cold-Boot Sequence**: Animated high-frequency static snow on cold boot; toggling power initiates a 1.5s raster warmup sequence with expanding green-tinted beam line and phosphor decay flash.
- **Comprehensive CRT Shader Pipeline (`crt_post.frag`)**: Single-pass post-processing shader replacing final blit with barrel distortion curvature, scanlines, 3-pixel RGB phosphor shadow mask triad, corner vignette, chromatic aberration channel splitting, and ambient phosphor persistence.
- **Draggable Rotary Volume Dial & Fullscreen Mode**: Drag up/down on the rotary dial controls audio output volume via Web Audio `GainNode` with a perceptually linear squared response curve ($V^2$). Double-clicking the screen expands the visualizer to borderless fullscreen projection mode.

#### 4. Web Audio DSP & Live Stream Integration (`web/dsp.js`)

- **Live Stream DSP**: Connected `https://radio.spaz.org:8060/radio.ogg` to real-time Web Audio graph via lowpass (bass), bandpass (mid), highpass (high), and broadband RMS analysers.
- **Beat Detection & Phase Tracking**: Implemented dual-average onset detection with IOI history for adaptive BPM calculation and beat-synced LFO signals.
- **Audio-Reactive Uniforms**: Wired live CV channels (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`, `beatPhase`, `beatSine`, `trigger_onset`) to dynamically modulate Deck A & Deck B shader parameters in standalone web mode.
- **Click-to-Start Gesture UX**: Autoplay policy compliance with single-click unlock overlay and AudioContext auto-resumption.

#### 5. WebSocket Relay Server & 24/7 Autopilot Scheduler (`server/`, `web/autopilot.js`)

- **Stateless WebSocket Relay (`server/server.js`)**: Lightweight Node.js relay server featuring role-based token authentication (`role=broadcast&key=...`), `state_full` payload caching, and fan-out distribution to all active web viewers with zero transcoding latency. Includes `server/lsd_relay` CLI runner.
- **24/7 Autopilot Scheduler (`web/autopilot.js`)**: Autonomous client-side playlist scheduler executing smooth fade-through-black transitions across curated presets when offline.
- **Seamless Live Broadcast Handshake**: Automatically transitions web viewers from the 24/7 Autopilot to the live broadcast when the VJ connects, updating the station LED badge to `SPAZ RADIO • LIVE`.

#### 6. Dynamic Spiral Continuous Phase Tracking & Stability Hotfixes

- **Continuous Dead-Reckoning Integration**: Synchronizes `integratedTime` and `integratedShear` from desktop to WebGL while maintaining local dead-reckoning between updates, eliminating 60fps stutter.
- **WebSocket URL & Encoding Robustness**: Correctly URL-encodes tokens and handles base URLs with fragment identifiers.
- **Safe Fallback for Unknown Sources**: Safely maps unknown visual sources to `"unknown_source"`.

---

### 📜 Full Commit History (v1.0.0-beta.29 → v1.0.0-beta.30)

- `50b047b` fix(broadcast): resolve WebSocket sync, phase tracking, and state serialization issues
- `5eae5c6` feat(broadcast): add live WebSocket broadcasting, web math/evaluator modules, and shader parity
- `ac95831` feat(web): add standalone WebGL2 core visualizer, Web Audio DSP, retro CRT TV shell, and relay server

---

## Version 1.0.0-beta.29

> [!NOTE]
> **Release 1.0.0-beta.29** delivers high-performance live video recording with real-time audio muxing, deterministic time virtualization for offline rendering, hardware encoder prioritization, and a fully resizable settings panel with automatic persistence.

---

### Key Highlights

#### 1. Live Video Recording with Zero-Allocation Audio Muxing

- **Real-Time Audio Tapping (`AudioEngine.kt` & `RealtimeRecorder.kt`)**: Zero-allocation audio tapping directly inside `AudioEngine.processAudio` using a pre-allocated pool of `AudioBlock` instances on the real-time audio thread (Linux/JACK & cross-platform Java Sound).
- **Asynchronous Audio PCM Writer & Lossless Remuxing**: Background worker streams 16-bit PCM WAV audio during recording and losslessly multiplexes it into the final container using FFmpeg (`-c:v copy -c:a aac -b:a 320k -shortest`) upon stopping.
- **Master Preview Tally Overlay**: Pulsing red `REC MM:SS` badge rendered dynamically on the Master preview monitor.
- **Recording Hotkey (`Ctrl+R`) & OS Standard Directory**: Toggle live recording anytime with `Ctrl+R`. Default recording folder automatically resolves to the system Videos directory (`~/Videos/liquid-lsd` or `~/Movies/liquid-lsd`).

#### 2. Deterministic Time Virtualization (`TimeSource.kt`)

- **Centralized Simulation Time Provider**: Replaced non-deterministic OS/GLFW time queries across all shaders (`uTime`), `CVRegistry` evaluators (`AudioFollowerTracker`), `DynamicSpiral`, and `Mixer` with `TimeSource`.
- **Sample-Accurate Audio/Visual Synchronization**: In `OfflineRenderStudio`, `TimeSource.setSimulatedTime(subFrameTimeSec, subFrameDt)` ensures complete deterministic frame-accurate lockstep between audio DSP analysis and visual motion curves regardless of render speed.

#### 3. Hardware Encoder Prioritization with Dynamic Probing

- **GPU Hardware Acceleration**: Automatically probes and prioritizes hardware encoders (`h264_nvenc`, `h264_qsv`) for high-throughput exporting.
- **Seamless Software Fallback**: Validates encoder functionality with 1-frame probe and automatically falls back to `libx264`, `libx265`, or `prores_ks` if GPU hardware is unavailable.

#### 4. PBO Readback & Zero-Allocation Buffer Pipelines

- **Fast DMA Transfers**: Replaced row-by-row CPU vertical flipping with `MemoryUtil.memCopy` block DMA transfers and delegated vertical flip to FFmpeg filter graph (`-vf vflip`).
- **Zero-Allocation Stream Buffer**: Reused persistent class-level 64 KB stream chunk buffers in `FFmpegProcessPipe`, eliminating ~4 MB/s GC heap churn during recording.

#### 5. Offline Render Studio Enhancements (`VideoExportModal.kt`)

- **Integrated File Browser**: Modal file picker (`ImGuiFileBrowser`) for audio tracks, output paths, and preset/setlist snapshots.
- **Match Project Canvas**: Option to match internal project render resolution or standard presets (1080p, 4K, 720p, 9:16 vertical, 1:1 square).
- **Progress Metrics & Error Diagnostics**: Live speed (FPS), elapsed time, ETA, estimated output file size, and multi-line FFmpeg error reporting.

#### 6. Resizable Settings Panel with Automatic Persistence

- **Interactive Sizing**: Enabled free resizing of the Settings modal with minimum bounds and display-clamping constraints.
- **Dynamic Flexible Layout**: Sidebar navigation and content panes stretch seamlessly to fill window dimensions.
- **Auto-Save Dimensions**: Window width and height are preserved across sessions in `lsd-settings.properties`.

---

### 📜 Full Commit History (v1.0.0-beta.28 → v1.0.0-beta.29)

- `45eb36e` feat(export): upgrade live video recording with audio muxing, deterministic time virtualization, and resizable settings
- `88d0760` docs: align architecture, user guide, and developer docs with deck architecture and preset naming
- `bdd3b80` Fix video recording freeze and FFmpeg broken pipe issues

---

## Version 1.0.0-beta.28

> [!NOTE]
> **Release 1.0.0-beta.28** is a major milestone release that includes all features, architectural additions, visual sources, DSP engines, and workflow enhancements developed since **v1.0.0-beta.26** (incorporating all updates from beta 27 and beta 28).

---

### Key Highlights

#### 1. Dedicated Background (BG) Layer & Deck PV (Preview) Pipeline

- **Dedicated Background Deck (`Deck BG`)**: Added a 4th rendering deck `deckBG` rendered beneath the crossfaded Deck A & Deck B composite in GLSL (`mixer.frag`):
  $$\text{Composite} = \text{Blend}(A, B) + \text{BG} \cdot (1.0 - \text{Blend}_{\alpha})$$
  allowing transparent, generative foregrounds to float naturally over dynamic background visuals.
- **Dedicated Preview Deck (`Deck PV`)**: Dedicated preview deck (`Deck PV`) across the entire UI, parameter tree (`Deck PV/...`), and rendering engine for visual auditioning and staging.
- **Expanded Modulatable Parameter Tree**: Added first-class parameter routing for `Deck BG/...`, `Deck PV/...`, `Mixer/randDeckBG`, and `Mixer/randDeckPV`.

#### 2. Symmetrical 2x2 Preview Monitor Matrix

- **Balanced 2x2 Grid Layout**:
  - **Top Row**: `Deck A` (Electric Blue) & `Deck B` (Warm Orange).
  - **Bottom Row**: `Deck BG` (Amber/Gold) & `Deck PV` (Mint Green).
  - Equal aspect ratios and sizes across all four decks with letter overlay badges (`A`, `B`, `BG`, `PV`).
  - Interactive top preset status bars, `Save`, and `Eject` buttons on each monitor.
  - Full drag-and-drop routing and right-click Move/Copy/Swap menus between all 4 decks.
- **Momentary Mixer Controls Bar**: 7 quick action buttons beneath the master crossfader: `< Prev`, `Next >`, `Rand A`, `Rand B`, `Rand BG`, `Rand PV`, `Rand All`.

#### 3. 4-Column Library Layout & Unified Top Action Toolbar

- **4-Column Side-by-Side Library**:
  - **Column 1 (Presets Pool)**: Real-time search and tag filtering across all `.lsd` presets.
  - **Column 2 (Playlist Editor)**: Setlist inspection, drag reordering with mint-green insertion feedback, and instant auto-save.
  - **Column 3 (A/B Play Queue)**: Live Auto-VJ queue with automated crossfading, repeat (`🔁`), and shuffle (`🔀`).
  - **Column 4 (Background Queue)**: Dedicated playlist queue for `Deck BG` featuring automated cycling (`AUTO-BG`) and smooth single-deck dip-to-black fade transitions.
- **Unified Top Action Toolbar (`[A] [B] [BG] [PV] [Q] [BGQ] [+]`)**:
  - Direct routing buttons located cleanly above the Presets and Playlist Editor columns.
  - Removed cluttered inline buttons from individual preset/playlist rows.
  - Mutual selection: Selecting a preset in the Presets column automatically deselects in Playlists (and vice versa).
  - `[+]` button dropdown to quickly initialize a new blank preset on any deck (`[A]`, `[B]`, `[BG]`, or `[PV]`).

#### 4. 4D Polychoron Visual Sources: Hyper-Mesh & Hyper-Slice

- **4D Hyper-Mesh (`hyper_mesh`)**:
  - Real-time GPU-accelerated 4D Polychoron rendering covering the **600-cell** (120 vertices, 720 edges) and **120-cell** (600 vertices, 1,200 edges, dual polytope).
  - Continuous 4D hyper-rotations across the $XW$, $YW$, and $ZW$ planes for inside-out polytope cell inversions and multi-axis tumbling.
  - 4D perspective and conformal stereographic ($S^3 \to \mathbb{R}^3$) projection modes with modulatable focal distance ($d \in [1.05, 5.0]$).
  - Pre-computed Hopf fibration coordinates ($S^3 \to S^2$) per vertex, enabling dynamic harmonic color waves rippling along Hopf tori.
  - Screen-space extruded anti-aliased tube ribbons and billboard joint nodes with zero heap allocation on the render loop ($< 0.3\,\text{ms}$ GPU frametime on Intel Iris Xe).
- **4D Hyper-Slice (`hyper_slice`)**:
  - Real-time raymarched 3D cross-sections ("MRI scan") through 4D 600-cell and 120-cell polychora using $H_4$ Coxeter reflection group symmetry folding (order 14,400).
  - Modulatable `Slice Offset` along the 4D $W$-axis to sweep 3D cutting hyperplanes through 4D solids to witness continuous polyhedral births, morphs, and subdivisions.
  - Full $XW$, $YW$, and $ZW$ 4D hyper-rotations with continuous 600-cell $\leftrightarrow$ 120-cell Wythoff facet normal slerp morphing.
  - Blinn-Phong specular lighting, Fresnel rim reflections, edge crease detection, and translucent crystal interior reveal.

#### 5. Analytic 32-Stellation Du Val Poset Manifold & CSG Visual Sources

- **Icosahedron 32-Stellation (`icosahedron`)**:
  - GPU $k$-th max deduplicating SDF raymarcher extracting the top 6 distinct plane distances across all 60 $H_3$ planes.
  - Continuous 2D morph pad ($uControlX, uControlY$): $Y$-axis slerps the generator vector between Icosahedron and Dodecahedron; $X$-axis continuously extrudes geometry outward through 1st, 2nd, 3rd, and 4th order Kepler-Poinsot star stellations.
  - In-place zero-allocation normal deduplication on the CPU, eliminating GC pauses on the render loop.
- **Icosa-Dodeca ($H_3$ Coxeter Symmetry Folding IFS SDF)**:
  - Mathematically pure $H_3$ Coxeter symmetry folding shader collapsing all 60 polyhedral faces into single base-plane evaluations in the fundamental chamber.
  - Continuous 4-phase cyclic morph slerping along the spherical fundamental triangle arc ($C_3 \leftrightarrow C_5$) through Icosahedron $\to$ Icosidodecahedron $\to$ Dodecahedron $\to$ Great Stellated Dodecahedron $\to$ Great Icosahedron with $C^2$ `smootherstep` pacing.

#### 6. Per-Band Audio Envelope Followers & Dual-Trace Oscilloscope

- **Independent Dynamics Followers**: Independent envelope followers for all 4 audio bands (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`), allowing punchy transient response on one parameter and long sustained decay swells on another.
- **Musical Dynamics Presets**: `Raw (Instant Jitter)`, `Punchy (Fast)`, `Smooth Swell`, `Slow Pulse`, `Ambient Drift`, and `Custom` (with $0\text{ ms} \dots 500\text{ ms}$ Attack and $10\text{ ms} \dots 3000\text{ ms}$ Decay sliders).
- **Dual-Trace Oscilloscope**: Renders raw incoming audio energy in a ghosted trace ($35\%$ alpha) beneath the solid smoothed follower curve.

#### 7. Multi-Band Autocorrelation Beat Engine & Benchmarking Suite

- **Cross-Spectral Autocorrelation Engine (`BeatDetectionMode.AUTOCORRELATION`)**: Zero-allocation primitive ring buffers on the real-time audio callback thread.
- **Harmonic Comb Unwrapping**: Eliminates half-tempo and double-tempo octave traps by verifying fundamental beat periods.
- **Sub-Block Parabolic Lag Interpolation**: Parabolic curve fitting across correlation peaks achieving floating-point tempo tracking within $\pm 0.1$ BPM.
- **Synthetic Audio Benchmark Suite (`BeatDetectorBenchmarkTest.kt`)**: Automated synthetic audio tests for 120 BPM House, 128 BPM EDM, 140 BPM Dubstep, 100 BPM Hip-Hop, and silent breakdowns.

#### 8. Master Crossfader Manual Takeover & Auto-VJ "Jump the Line" Staging

- **Instant Manual Takeover**: Interacting with the master crossfader via mouse or MIDI CC disarms Auto-VJ and temporarily mutes conflicting crossfade CVs for 1:1 physical control.
- **CV Auto-Centering**: Unmuting any CV modulator on `Mixer/crossfade` automatically centers `crossfade.baseValue` to `0.0`.
- **Standby Deck Staging ("Jump the Line")**: Manually loading a preset into the inactive deck while Auto-VJ is running stages it for the next automated crossfade without overwriting the queue sequence.

#### 9. Sticky Oscilloscope, Cell Muting, GUI Scaling & Engine Polish

- **Sticky Oscilloscope in Cell Config**: Parameter title, CV tab switcher, and live oscilloscope remain pinned at the top while modulator controls scroll independently below.
- **Cell Muting System**: Toggle any CV modulation cell (LFO, Audio, Trigger, MIDI) from sending values to live parameters (`Value`) while keeping the oscilloscope live and animated. Middle-clicking any active or muted cell toggles its mute state.
- **Percentage-Based GUI Scaling (75%–200%)**: Continuous scaling slider in Settings with 5% increments, `Ctrl+-` / `Ctrl+=` hotkeys, and automatic OS content-scale factor detection on launch.
- **Resizable Settings Panel with Automatic Persistence**: Settings modal is now freely resizable with dynamic sidebar and content resizing, bounded by display constraints and automatically saved to `lsd-settings.properties`.
- **Pitch Black Backgrounds**: Enforced solid opaque black OpenGL clear color across GUI mode, clean mode (`f`), and preview monitors.

#### 10. High-Performance Live Video Recording & Deterministic Offline Studio

- **Live Audio Recording & Muxing**: Real-time audio stream capture from `AudioEngine` backed by a zero-allocation pre-allocated block pool on the real-time audio thread. Background worker writes temporary 16-bit PCM WAV audio and losslessly remuxes it with FFmpeg (`-c:v copy -c:a aac -b:a 320k -shortest`) upon stopping. Configurable toggle in Settings.
- **Deterministic Time Virtualization (`TimeSource`)**: Centralized simulation clock eliminating audio/visual desync across all shaders (`uTime`), `CVRegistry` evaluators, `DynamicSpiral`, and `Mixer` during offline rendering.
- **Hardware Encoder Prioritization & Probing**: Probes and prioritizes GPU hardware encoders (`h264_nvenc`, `h264_qsv`) with automatic software fallback (`libx264`, `libx265`, `prores_ks`).
- **Direct Memory Copy & Zero-Allocation Pipelines**: Replaced line-by-line CPU vertical flips with direct `MemoryUtil.memCopy` block transfers and FFmpeg `-vf vflip`. Reused persistent 64KB chunk buffers in `FFmpegProcessPipe`, eliminating 4 MB/s GC heap churn.
- **Live Recording HUD & Settings**: Pulsing red `REC MM:SS` tally badge overlaid on the Master preview monitor, `Ctrl+R` hotkey, and automatic OS standard Videos folder resolution (`~/Videos/liquid-lsd` or `~/Movies/liquid-lsd`).
- **Offline Studio Upgrades**: Integrated `ImGuiFileBrowser` for audio and destination selection, "Match Project Canvas" resolution option, preset/playlist snapshotting, detailed progress & ETA metrics, and multi-line FFmpeg error diagnostics.

---

### 📜 Full Commit History (v1.0.0-beta.26 → v1.0.0-beta.28)

- `ea862c6` Add Background Deck, Deck PV preview, 2x2 monitor matrix, and 4-column library layout
- `159db62` feat(source): add 4D Hyper-Slice raymarched visual source with H4 domain folding
- `b9a19fa` feat(source): add 4D Hyper-Mesh polychoron visual generator (600-cell & 120-cell)
- `4c2e487` fix(icosa-v3): Lock lighting to screen center in camera space
- `d088f80` fix: Prevent icosa-v3 unbounded growth at high Control X values
- `2182751` feat: Upgrade Control X to endless slerp for infinite stellations
- `0f31f62` feat: Add Icosahedron V3 CSG visual source
- `f150640` feat(shaders): implement analytic 32-stellation Du Val poset manifold for icosahedron
- `cea75ef` feat(visuals): add Icosahedron 32-Stellation 2D Du Val poset manifold visual source
- `a6bab1a` feat(modulation): add per-modulator audio envelope followers, UI sections, and dual-trace oscilloscope
- `304ba02` feat(icosa_dodeca): add geometric Wythoff vertex truncation and edge cantellation to Support H
- `06dc5d2` feat(shaders): implement continuous stellation plane tilting for icosa_dodeca
- `f8f6aaf` docs: document H3 Coxeter IFS engine and polyhedral morphs for icosa_dodeca
- `b679aac` fix(ui): enforce solid black background on all 4 preview monitor screens
- `e14ab61` feat(shaders): add smootherstep C2 morph transitions, cross-faded symmetry sectors, and raymarch optimizations for icosa_dodeca
- `1e9565d` feat(shaders): unify icosa_dodeca into continuous 4-stage cyclic morph and add finite cone bounds
- `56e99d7` feat(shaders): improve icosa_dodeca stellation cones, symmetry sectors, and raymarch stepping
- `60ae86c` docs: update release notes and documentation for v1.0.0-beta.27
- `fc1ad3f` Remove GPU-heavy visual sources (clifford_torus, kifs, mandelbox, pseudo_kleinian, mandelbulb)
- `2a53f99` feat(library): add Delete key shortcut, permanent deletion warning, and reference cleanup to playqueue and playlist
- `bc8a5b8` refactor(ui): extract BrowserDeckButtons helper and simplify preset grid UI buttons
- `59bcbb0` feat(library): consolidate library browser and playlist editor with unified subtabs and asset actions
- `193e9b9` feat(presets): integrate manual deck preset loading with Auto-VJ and PlayQueue line-jumping
- `7e819dd` Add Master Mixer momentary controls for playlist navigation and randomization
- `242c426` feat(mixer): improve crossfader manual takeover, CV modulation, and Auto-VJ transition behavior
- `476610d` chore(logging): set default log level to warn
- `3fe8258` feat(ui): make oscilloscope sticky at top of Cell Config panel with scrollable modulators
- `835eb7d` fix(ui): increase analytical step resolution on LFO oscilloscope to eliminate pixelation
- `7c5c7b6` feat(ui): improve LFO oscilloscope rendering, BPM-aware timebase, and seam alignment
- `dcb6243` refactor(ui): place modulator titles on top row above controls in cell config
- `2537cad` refactor(ui): refine LFO section layout and preserve modulator bypass state on depth changes
- `d835368` fix(ui): percentage-based GUI scaling, font atlas GC stability, and window resize bounds
- `9e260d7` feat(ui): add 'b' keybinding to toggle background video output
- `7f0f915` feat(ui): add cell mute toggle with live oscilloscope preview and ASCII window title
- `b40b399` feat(audio): overhaul multi-band autocorrelation beat engine and add benchmark test suite
- `e768ed0` refactor(audio, ui): update beat detection settings and UI panel controls

---

## Version 1.0.0-beta.26

> [!NOTE]
> **Release 1.0.0-beta.26** is a major cumulative milestone rolling up all features, architectural enhancements, performance optimizations, and UI overhauls since `v1.0.0-beta.21`.
> Highlights include user-configurable render resolutions with multi-aspect ratio output scaling (16:9, 4:3, 1:1, custom), live zero-downtime FBO resizing, a multi-scale calibrated oscilloscope engine with real-time future projection, deterministic frame-synced LFOs, a spectral-flux beat detection flywheel overhaul, UI architecture modularization with live theme color tuning, industry-standard "Preset" terminology with unified `library/` storage, dedicated `SavePresetModal` with overwrite safety, unipolar modulation and dial calibrations, resolution-independent UI scaling, and comprehensive zero-allocation render loop hot-path optimizations.

---

### Key Highlights (Rollup since v1.0.0-beta.21)

#### 1. Configurable Render Resolution & Multi-Aspect Output Pipeline

- **Resolution Presets & Custom Dimensions**: Added user-configurable internal rendering resolutions under **Settings -> Video & Display**, featuring standard 16:9 presets (1080p, 720p, 540p, 1440p, 4K UHD), 4:3 presets (UXGA 1600x1200, XGA 1024x768, SVGA 800x600), 1:1 square presets (1080x1080, 800x800, 600x600), and custom dimensions ($128 \times 128$ to $7680 \times 4320$).
- **Live Zero-Downtime Pipeline Resizing**: Decks and Mixer support dynamic reallocation (`Deck.resize` and `Mixer.resize`) on the main OpenGL thread without interrupting playback or losing preset state.
- **GPU Performance Scaling**: Downscaling from 1080p to 720p or 540p reduces raymarching pixel evaluation by 55%–75%, allowing heavy distance-field raymarchers (KIFS, Mandelbulb, Pseudo-Kleinian) to run at solid 60 FPS on laptops and integrated GPUs.
- **Display Output Scaling Modes (`ViewportHelper`)**:
  - **Fit (Letterbox / Pillarbox)**: Preserves exact aspect ratio of the render target with border bars when outputting to mismatched monitor aspect ratios.
  - **Fill (Crop)**: Centers and crops edges to fill the display with no black bars.
  - **Stretch**: Stretches the image to fill the output display.
- **Aspect-Aware UI Previews & Splitter Clamping**: `MixerMonitorLayoutCalculator` and `MixerMonitorPanel` dynamically scale Deck A, Deck B, Deck PV, and Master preview heights to match the active render aspect ratio. Splitter positioning clamps Column 3 width to the maximum preview capacity given window height, eliminating letterbox dead space.
- **Opt-In Secondary Video Output & Menu Control**: Secondary window is strictly opt-in on single-monitor setups (no unsolicited popups on startup). Added an **"Output Window"** item in the main menu bar to toggle external/secondary output window, and removed the Spacebar hotkey to prevent accidental triggers.

#### 2. UI Architecture Modularization & Interactive Developer Tools

- **`DeckPresetController` Extraction**: Decoupled deck preset file actions (Save, Save As, Rename, Duplicate, Overwrite, Eject, Reset), file dialog handling, and save status notifications from `UIManager` into a dedicated controller class.
- **`UIThemeStyler` Extraction**: Extracted dynamic ImGui theme application, custom color palette mapping (`BORING`, `DARK_SOLARIZED`, `LIGHT_SOLARIZED`, `DARK_LUNARIZED`, `LIGHT_LUNARIZED`, `NEON`), window background alpha/video blending, neon gradient rendering, and proportional `ImGuiStyle` size scaling.
- **`SplitterManager` Extraction**: Extracted multi-column workspace splitter state, drag interaction tracking, cursor hinting (`ResizeEW`/`ResizeNS`), double-click reset positions, and window-level draw-list divider rendering from `UIManager`, ensuring splitters remain properly layered below floating windows, dialogs, and tools.
- **Library Panel Renaming**: Refactored and renamed the 3-column Preset, Playlist, and Play Queue dock from "Asset Browser" to **"Library"**, standardizing terminology with DJ/VJ performance software, and modernizing `LibraryPanel`, `LibraryMode` (`FULL`, `HALF`, `HIDE`), and backward-compatible settings persistence.
- **Live Theme `ColorTunerPanel`**: Added interactive non-modal color tuner accessible via the top menu bar ("Color"), allowing real-time assignment of palette swatches across all 17 themed ImGui elements with live updates and instant Kotlin code generation for clipboard export. Canonical HEX palettes enforced for Solarized and Lunarized themes. Fixed close button synchronization, eliminated duplicate outer window scrollbars, widened the Alpha column to prevent text clipping, and ensured proper z-order above workspace divider lines.
- **Background Video Keybinding (`B`)**: Added a global hotkey `B` to instantly toggle master video background rendering behind the semi-transparent UI with synchronized settings persistence.
- **Linux Window Title & X11 Class Hints**: Replaced multi-byte Unicode em-dash (`—`) in GLFW window title with standard ASCII hyphen (`-`) and explicitly configured `GLFW_X11_CLASS_NAME` ("Liquid LSD") and `GLFW_X11_INSTANCE_NAME` ("liquid-lsd"), preventing mojibake/corrupted garbage characters in Linux alt-tab task switchers.

#### 3. Multi-Scale Calibrated Oscilloscopes & Signal Visualization

- **Dynamic FPS Sync for Frame-Based LFOs**: Fixed frame-synced LFO lookahead projection, auto-timebase calculations, and history sampling across `Evaluators`, `ModulatableParameter`, and `OscilloscopeDrawer` to dynamically bind to the configured target frame rate (`CVRegistry.getTargetFps()`) rather than assuming 60 FPS. At 30 FPS, setting an LFO to 30 frames now correctly oscillates at exactly $1.0\text{ Hz}$ ($1.0\text{s}$ period).
- **Multi-Scale Calibrated Timebases**: Oscilloscopes support selectable physical time windows spanning from fast transients to circadian cycles: `1s` ($250\text{ms/div}$), `10s` ($2.0\text{s/div}$), `100s` ($20\text{s/div}$), `15m` ($3\text{m/div}$), `2.5h` ($30\text{m/div}$), and `24h` ($4\text{h/div}$). Time range dropdown combo widths and spacing dynamically autoscale with font size.
- **Real-Time Lookahead Future Projection**: Real-time forward waveform projection for deterministic LFO modulators rendered in front of the `NOW` playhead.
- **Decoupled Per-Scope Timebases**: Timebase selections across individual CV scopes (LFO, Audio, Trigger, MIDI) and the Value parameter oscilloscope are completely decoupled. Changing the time window on one tab no longer changes the scale of other tabs.
- **Auto Scale Exclusively for LFO**: The `Auto` timebase option (which dynamically fits $1\text{–}2$ periods of the active waveform) is offered exclusively on the **LFO** oscilloscope. **Audio**, **Trigger**, **MIDI**, and **Value** default to **`10s`** (displaying the full recorded history window) and provide fixed physical options (`1s` to `24h`).
  
  #### 4. Cell-Level Mute & Live Oscilloscope Preview
- **Cell Mute / Preview System**: Users can mute any CV modulation cell (LFO, Audio, Trigger, MIDI) from sending values to live parameters (`Value`) while keeping the Oscilloscope 100% live and animated in Cell Config for real-time waveform previewing.
- **Preset Grid Visual Indicators**: Muted cells in the Preset Grid drop knob arc/meter opacity to **35%** and display a centered sans-serif **'M'** inside the knob.
- **Master Scope Mute Toggle**: Cell Config features a master `[ LIVE ]` / `[ MUTED ]` toggle button in the top-right corner of the Oscilloscope header bar, with an amber `[SCOPE LIVE — OUTPUT MUTED FROM VALUE]` watermark when muted.
- **Middle-Click Shortcuts**: Middle-clicking any active or muted cell in the Preset Grid immediately toggles its Mute/Unmute state without clearing modulators or losing dial settings.
- **Brightened Grid Ticks & Dynamic Timestamp Badges**: High-contrast, crisp grid division ticks and legible timestamp numbers with dynamic height positioning for clear readability across all themes and zoom levels (`-250ms`, `-2s`, `NOW`, `+2s`, `+15m`, `+4h`, etc.).
- **Unified Oscilloscope Architecture**: Consolidated all oscilloscope rendering into `OscilloscopeDrawer`, eliminating duplicated drawing code across UI panels.

#### 4. Real-Time Multi-Band Beat Detection Engine & Automated Benchmark Testing

- **Multi-Band Cross-Spectral Autocorrelation Engine (`BeatDetectionMode.AUTOCORRELATION`)**: Upgraded beat detection to maintain zero-allocation primitive FloatArray ring buffers (`bassHistory`, `midHistory`, `highHistory`, 2048 blocks). Computes cross-spectral correlation over candidate lags (40–200 BPM) without allocating memory on the JACK/audio callback thread.
- **Harmonic Comb Unwrapping**: Implemented harmonic comb unwrapping to evaluate half-lags ($d/2$). Eliminates half-tempo (60 BPM) and double-tempo (200 BPM) octave traps by verifying fundamental beat periods, ensuring 120, 128, 140, and 100 BPM tracks lock precisely to their true fundamental tempo.
- **Sub-Block Parabolic Lag Interpolation**: Fits a 2nd-order parabola over lag correlation points $(d-1, d, d+1)$ to extract sub-block fractional lag offsets $\delta$, achieving floating-point precision within $\pm 0.1$ BPM.
- **Gaussian Tempo Weighting**: Applies a subtle Gaussian curve centered at 120 BPM ($\sigma = 80$ BPM) to bias candidate selection towards natural musical tempos.
- **Automated Synthetic Audio Benchmark Test Suite (`BeatDetectorBenchmarkTest.kt`)**: Built an automated audio benchmark test suite that generates multi-band synthetic audio for 120 BPM House, 128 BPM EDM, 140 BPM Dubstep, 100 BPM Hip-Hop, and 4-beat silent drum breakdowns. Automatically validates convergence time (< 3.0s), lock accuracy (< 1.5 BPM error), and flywheel momentum retention.
- **UI Analysis Length Slider**: Restored the `Analysis Window Length` slider in `AudioEnginePanel` when `AUTOCORRELATION` mode is active, allowing live tuning of the correlation history window from 1.0 to 10.0 seconds.

#### 5. Deterministic Frame-Synced LFOs (LFO 1 & LFO 2)

- **Frame Frequency Mode**: Added a third frequency clocking mode, `FRAME`, alongside `TIME` and `BEAT` in the unified LFO generator. Frame-synced LFOs oscillate deterministically based on elapsed render frame count (1 to 10,000 integer frames), enabling artifact-free feedback buffer harmonization, per-frame stroboscopic/flicker effects, sample-and-hold per-frame noise, and deterministic video frame captures.
- **Integer-Locked Sliders & Dual Readouts**: Both primary carrier (LFO 1) and modulator (LFO 2) support independent frame sync with integer-locked range sliders and duration readouts (e.g. `120 frames (2.00s)`).

#### 6. Modulation Architecture & Calibration

- **Unipolar CV Modulation & Zero Silence Baseline**: Fixed modulation evaluation formulas for unipolar sources (Audio RMS, Bass/Mid/High frequency bands, Triggers, and MIDI CC). Silence ($cv = 0.0$) remains strictly at $0.0$ without introducing artificial DC offset shifts when increasing Depth, restoring full modulation dynamic range.
- **CV Modulation "Depth" Terminology Standardization**: Standardized the term for the value assigned to a CV modulator from "amplitude" (and legacy JSON "weight") to **"Depth"** across domain models (`CvModulator.depth`, `depthMin`, `depthMax`, `randomizeDepth`), evaluation logic, UI controls (Cell Config Depth range slider, LFO 2 AM Depth mode/tooltips), and documentation.
- **Preset Grid Cell Dial Calibration**: Calibrated knob meters in `PresetGridRenderer` for unipolar audio, trigger, and MIDI cells so dial needles and indicator arcs accurately reflect the true parameter modulation range ($0.0 \dots 1.0$) rather than resting at $0.5$ on silence.
- **Consolidated Modulation Evaluator**: Centralized parameter modulation evaluation into `Evaluators.kt` (`evaluateModulatedValue`), eliminating duplicate evaluation routines across UI panels.

#### 7. Preset & Library Architecture Modernization ("Patch" → "Preset")

- **Industry Standard 'Preset' Terminology Refactor**: Refactored visual parameter snapshots across the codebase from 'Patch' to 'Preset' (`PresetManager`, `DeckPresetDto`, `GlobalPresetDto`, `PresetGridPanel`, `PresetGridState`, `PresetGridRenderer`, `PresetGridTabs`, `PresetGridUndo`).
- **Unified `library/` User Storage Directory**: Standardized user data root to `library/` (`library/presets/*.lsd`, `library/midi/*.json`, `library/playlists/*.lsdset`, `library/sources/`, `library/last_session.json`).
- **Codebase Streamlining & Legacy Code Removal**: Removed legacy backwards compatibility shims across data models, serialization, session management, and UI browsers. Standardized `ModulatorDto` serialization to directly serialize `depth`, `depthMin`, `depthMax`, and `randomizeDepth` without legacy `@SerialName("weight")` aliases. Removed obsolete `GlobalPresetDto` and legacy conversion methods.

#### 8. UI & UX Refinements, SavePresetModal & Responsive Layouts

- **Dedicated SavePresetModal**: Replaced the floating `DeckPresetBrowser` popup with a clean, dedicated `SavePresetModal` for entering preset names and comma-separated tags directly when selecting "Save As..." (or "Save" on an untitled deck). Dynamic action titles (`Save Preset As`, `Rename / Edit Preset Tags`, `Duplicate Preset`) render cleanly in the ImGui modal title bar without redundant body text.
- **Universal Overwrite Safety**: Added file existence detection and overwrite protection across all preset modal flows (`Save As...`, `Rename`, `Duplicate`). `Save As...` defaults to `${activeName}_copy` to create new files by default, and typing an existing file name prompts with an explicit amber warning badge and `[ Overwrite ]` confirmation button.
- **Library New Preset Creation**: Added a dedicated `[Create new preset...]` row with `[ A ] [ B ] [ BG ] [ PV ]` buttons positioned above the preset list in the Library. Clicking a deck button ejects/resets the deck and switches Preset Grid focus directly to that deck.
- **Mixer Monitor Left-Click Deck Focus**: Left-clicking any deck preview monitor (`Deck A`, `Deck B`, `Deck BG`, or `Deck PV`) in the Mixer Monitor panel directly focuses the Preset Grid to that deck (`activeTopTab`).
- **Redesigned Deck Monitor Toolbar**: Combined the top preset header row and bottom patch label across Deck A, Deck B, Deck BG, and Deck PV preview monitors into a unified interactive toolbar (`[Save] [Eject] [Preset Bar]`) with corner letter badges (`A`, `B`, `BG`, `PV`).
- **Mixer Monitor Vertical Scrollbar Elimination**: Overhauled `MixerMonitorLayoutCalculator` to comprehensively calculate non-aspect vertical chrome, eliminating unwanted vertical scrollbars while preserving aspect preview monitors.
- **Robust Panel Splitters**: Replaced dummy ImGui splitter windows with direct mouse hit-testing and foreground draw list rendering, ensuring resize cursors and drag interactions remain active at all times.
- **Preset Grid Knob Indicators**: Refined circular knob meters across `MONOPOLAR`, `BIPOLAR`, `ENDLESS`, and `DISCRETE` modes in `PresetGridRenderer` by replacing the solid value circle with an elongated inward radial needle pointer (`trackRadius * 0.3f`), boosting background track arc/circle brightness, and adding a vibrant yellow cross-track tick mark.
- **Resolution-Independent Grid Scaling**: Replaced hardcoded cell pixel dimensions with a dynamic `GridMetrics` geometry token system that scales Preset Grid cells and circular readout knobs automatically with global UI font size (`baseSize`). Added a **"Grid Knob Cell Scale"** setting slider (0.70x to 2.00x) in **Settings -> Appearance**.
- **Comprehensive Font Autoscaling**: Dynamic font-scaling across settings modals, empty deck launchpads, cell config tab rows, range sliders, and Lucide icons.
- **Library Live Auto-Refresh**: Real-time filesystem change monitoring across `LibraryPanel` and `ImGuiFileBrowser`, removing redundant manual Refresh buttons and automatically updating file listings when on-disk files change.
- **Playlist Menu Bar Streamlining**: Removed action buttons from the playlist editor menu bar in Library and consolidated them into the right-click context menu.

#### 9. Performance & Zero-Allocation Hot-Path Optimizations

- **Oscilloscope & Modulation GC Optimization**: Replaced per-call `HashSet` instantiation in `isCvSourceBipolar` with a zero-allocation branch, eliminating over $180{,}000$ GC object allocations per second on the 60 FPS render path during anti-aliased waveform rendering.
- **Render-Loop Hot-Path Cleanups**: Preallocated immutable timebase lists/arrays in `OscilloscopeDrawer` and reused persistent `ImInt` wrappers across oscilloscope timebase combos and `ModulatorHeaderRow` operator selectors, ensuring strict ImGui zero-allocation draw rules.
- **Eliminated Dead Multi-Trace History Loops**: Removed unused per-frame modulator history evaluation loops and unreferenced `modulatorHistories` buffers in `ValueParamSection` and `CellConfigPanel`.
- **Preserved Scope Timebases Across Clones**: Fixed `ModulatableParameter.clone()` to preserve custom per-scope timebase zoom settings across preset cloning, undo/redo snapshots, and deck preset duplication.
- **30 FPS User Setting Frame Rate Limiting**: Restored two-stage CPU-efficient sleep frame rate pacing in the main render loop bound to `session.uiTheme.maxFps`, properly enforcing the 30 FPS power-saver limit when enabled.
- **Comprehensive Unit Testing**: Added unit tests for target-FPS frame-synced LFO calculations, scope timebase cloning, source classification helpers, UITheme settings round-trips, ViewportHelper scaling modes, and CV history buffer interpolations.

---

### 📜 Cumulative Commit History (v1.0.0-beta.21 → v1.0.0-beta.26)

- `c23d180` Fix Deck A and Deck B preview monitor sizing to match active render aspect ratio
- `ec1ad47` Limit Mixer/Monitor panel width to maximum preview capacity
- `1037b37` Add configurable render resolution, display scaling modes, and opt-in output window
- `3ec54e0` Refactor ParameterResolver to use ParameterOwner interface and update UIThemeStyler
- `6716812` Restore NoteEditorModal reference in ARCHITECTURE.md ui tree
- `c27f569` Refactor 3-column dock panel and settings from Asset Browser to Library
- `7c491ed` fix(ui): enforce exact canonical HEX codes and all 16 swatches for Lunarized
- `8457753` fix(ui): enforce exact canonical HEX codes and all 16 swatches for Solarized
- `09bdd9d` Enhance ColorTunerPanel swatch dropdowns and layout sizing
- `e431dc7` feat(ui): add live Theme ColorTunerPanel and update release notes for v1.0.0-beta.25
- `c0b2a3e` refactor(presets): remove legacy GlobalPresetDto and unused global preset methods
- `49e8b5c` refactor(ui): extract DeckPresetController from UIManager
- `fcaf0b8` refactor(ui): extract UIThemeStyler and SplitterManager from UIManager
- `8bf8f2a` fix: restore 30 FPS frame rate limiter in render loop
- `a67eb73` docs: add comprehensive beta 24 release notes rolling up changes since beta 21
- `bc02868` Refactor CV evaluators, consolidate oscilloscope rendering, and add tests
- `06acbc7` docs: finalize release notes with decoupled timebase and history details
- `1aa2e2d` feat(ui): default oscilloscope timebase to 10s for all non-LFO tabs
- `11fd129` feat(ui): restrict Auto timebase option exclusively to LFO oscilloscope
- `386c306` feat(ui): decouple oscilloscope timebase selection across individual CV tabs and Final
- `8d14cc8` fix(ui): ensure flat zero baseline outside recorded history on long timebases
- `71b44ad` feat(ui): update Final parameter oscilloscope to always display 100% true recorded history
- `914781f` docs: update RELEASE_NOTES.md with modulation, dial calibration, and oscilloscope envelope highlights
- `f178ab5` fix(ui): implement peak-detect anti-aliasing envelope on long-timebase LFO oscilloscopes
- `2929f94` fix(ui): isolate future lookahead projection strictly to deterministic LFO modulators
- `5114e29` fix(ui): fix preset grid cell knob display value mapping for unipolar audio/midi modulators
- `1ca993a` fix(audio): fix unipolar audio modulation formulas, physical timebase scaling, and zero-baseline bypass
- `c1ace45` fix(ui): eliminate oscilloscope quivering, fix playhead signal binding, and preview live audio CV
- `9ee45f9` feat(ui): implement hybrid oscilloscope with true history replay and context-aware playheads
- `4d6e5c5` fix(ui): autoscale oscilloscope timebase combo and brighten grid ticks and numbers
- `5d226a3` refactor(ui): lock oscilloscope to symmetrical centered playhead and clarify lookback/lookahead tooltips
- `d10602e` fix(ui): calibrate past history rendering to selected timebase
- `33d8ded` feat(ui): add multi-scale calibrated oscilloscopes with future projection and interactive playhead
- `aa8ef1f` Refine preset grid knob meters with needle pointers, yellow base ticks, and brighter tracks
- `cb3ab78` Focus Preset Grid deck tab on monitor left-click
- `aac3f81` fix(ui): dynamic font-scaling for CellConfig sliders, dropdowns, and layouts
- `29ab8b9` fix(ui): vertically center Lucide icons with scaled glyphOffset in UITheme
- `c00e3b2` Fix font scaling for empty deck launchpad and Cell Config tab row buttons
- `a0e1a06` feat(ui): add create new preset row to asset browser
- `e987585` ui: replace DeckPresetBrowser with SavePresetModal and context-aware metadata flows
- `665857b` Fix LFO 1 time slider visibility on Frame sync and compute frame durations using configured maxFps
- `d2ab1f3` feat(audio): overhaul real-time beat detection and phase flywheel tracking
- `dbd7fac` feat(core): remove backwards compatibility code and implement frame-synced LFOs
- `954b32a` fix(ui): improve mixer monitor layout and fix panel splitters
- `b0294c3` refactor(cv): rename modulation amplitude to Depth across domain models, UI, and docs
- `861f323` docs(architecture): update architecture docs to reflect zero-alloc volatile beat clock & library paths
- `a0ef6bc` fix(settings): isolate test settings file cleanup to prevent resetting user column visibility on startup
- `a772b8f` feat(ui): add resolution-independent GridMetrics scaling and adjustable Grid Knob Cell Scale setting
- `47450db` fix(ui): position combined preset bar above Deck A/B/C preview monitors
- `8ebbeb6` refactor: complete patch to preset terminology migration across UI and docs
- `7c71375` refactor: replace internal patch references in audio, notes, and docs
- `b244c90` refactor: rename Patch terminology to Preset and presets/ directory to library/
- `fe9f57b` feat(ui): redesign Deck preview monitor preset bottom bar
- `9c0ef28` refactor(ui): remove playlist menu bar buttons and move actions to right-click menu
- `413b0e6` fix(ui): increase Settings modal content height by 25% to prevent scrolling on Patch Grid settings
- `d481bf8` fix(ui): eliminate modal scrollbars and auto-fit Settings window around content and Close button
- `4e5d97a` fix(ui): reduce Settings modal default height and lock Close button inside visible bounds
- `10b0c90` feat(ui): auto-resize Settings modal window, sidebar, and button heights dynamically with font scale
- `c989432` feat(ui): overlay letter badges on monitor lower-left corners and remove redundant text headers
- `009ab16` feat(ui): implement real-time media browser auto-refresh and remove manual refresh buttons
