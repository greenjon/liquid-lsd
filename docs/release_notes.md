# Liquid LSD — Release Notes

## Version 1.0.0-beta.26

> [!NOTE]
> **Release 1.0.0-beta.26** is a major cumulative milestone rolling up all features, architectural enhancements, performance optimizations, and UI overhauls since `v1.0.0-beta.21`.
> Highlights include user-configurable render resolutions with multi-aspect ratio output scaling (16:9, 4:3, 1:1, custom), live zero-downtime FBO resizing, a multi-scale calibrated oscilloscope engine with real-time future projection, deterministic frame-synced LFOs, a spectral-flux beat detection flywheel overhaul, UI architecture modularization with live theme color tuning, industry-standard "Preset" terminology with unified `library/` storage, dedicated `SavePresetModal` with overwrite safety, unipolar modulation and dial calibrations, resolution-independent UI scaling, and comprehensive zero-allocation render loop hot-path optimizations.

---

### Key Highlights (Rollup since v1.0.0-beta.21)

#### 1. Configurable Render Resolution & Multi-Aspect Output Pipeline
- **Resolution Presets & Custom Dimensions**: Added user-configurable internal rendering resolutions under **Settings -> Video & Display**, featuring standard 16:9 presets (1080p, 720p, 540p, 1440p, 4K UHD), 4:3 presets (UXGA 1600x1200, XGA 1024x768, SVGA 800x600), 1:1 square presets (1080x1080, 800x800, 600x600), and custom dimensions ($128 \times 128$ to $7680 \times 4320$).
- **Window Resize Safety & Minimum Dimension Constraints**:
  - Enforced native GLFW window size limits (`glfwSetWindowSizeLimits` with minimum dimensions of $800 \times 600$), preventing operating systems from crushing the desktop UI.
  - Patched layout clamping math in `UIManager` to eliminate `IllegalArgumentException: Cannot coerce value to an empty range` when resizing below $358\text{px}$.
  - Fixed modal height clamping in `SettingsPanel` to prevent fatal empty range exceptions on short window heights.
  - Hardened child window, texture preview, and slider dimension calculations across `LibraryPanel`, `MixerMonitorPanel`, `DeckControlPanel`, `CustomIconButton`, `PresetGridRenderer`, and `CustomRangeSlider` to guarantee strictly positive dimensions (`coerceAtLeast(1f)`), preventing Dear ImGui `size_arg.x != 0.0f && size_arg.y != 0.0f` assertion crashes when labels wrap on large font sizes or narrow widths.
  - Guarded the main OpenGL render loop against 0-sized or minimized framebuffers.
- **Live Zero-Downtime Pipeline Resizing**: Decks and Mixer support dynamic reallocation (`Deck.resize` and `Mixer.resize`) on the main OpenGL thread without interrupting playback or losing preset state.
- **GPU Performance Scaling**: Downscaling from 1080p to 720p or 540p reduces raymarching pixel evaluation by 55%–75%, allowing raymarchers and visual shaders to run at solid 60 FPS on laptops and integrated GPUs.
- **Display Output Scaling Modes (`ViewportHelper`)**:
  - **Fit (Letterbox / Pillarbox)**: Preserves exact aspect ratio of the render target with border bars when outputting to mismatched monitor aspect ratios.
  - **Fill (Crop)**: Centers and crops edges to fill the display with no black bars.
  - **Stretch**: Stretches the image to fill the output display.
- **Aspect-Aware UI Previews & Splitter Clamping**: `MixerMonitorLayoutCalculator` and `MixerMonitorPanel` dynamically scale Deck A, Deck B, Deck C, and Master preview heights to match the active render aspect ratio. Splitter positioning clamps Column 3 width to the maximum preview capacity given window height, eliminating letterbox dead space.
- **Opt-In Secondary Video Output & Menu Control**: Secondary window is strictly opt-in on single-monitor setups (no unsolicited popups on startup). Added an **"Output Window"** item in the main menu bar to toggle external/secondary output window, and removed the Spacebar hotkey to prevent accidental triggers.

#### 2. UI Architecture Modularization & Interactive Developer Tools
- **`DeckPresetController` Extraction**: Decoupled deck preset file actions (Save, Save As, Rename, Duplicate, Overwrite, Eject, Reset), file dialog handling, and save status notifications from `UIManager` into a dedicated controller class.
- **`UIThemeStyler` Extraction**: Extracted dynamic ImGui theme application, custom color palette mapping (`BORING`, `DARK_SOLARIZED`, `LIGHT_SOLARIZED`, `DARK_LUNARIZED`, `LIGHT_LUNARIZED`, `NEON`), window background alpha/video blending, neon gradient rendering, and proportional `ImGuiStyle` size scaling.
- **`SplitterManager` Extraction**: Extracted multi-column workspace splitter state, drag interaction tracking, cursor hinting (`ResizeEW`/`ResizeNS`), double-click reset positions, and draw-list divider rendering from `UIManager`.
- **Library Panel Renaming & 3-Column Redesign**: Refactored the Library into an intuitive 3-column layout:
  - **Column 1 (Left - Presets Pool)**: Flat list of all presets with real-time text search filtering by name and preset tags, plus `[Create new preset...]` deck eject rows.
  - **Column 2 (Middle - Playlist Editor)**: Dedicated setlist editor with top playlist selector dropdown combo, `[ + ]` new playlist button, `[ ••• ]` actions menu, drag-and-drop preset insertion, and automatic persistence (auto-save on edit).
  - **Column 3 (Right - Play Queue)**: Live volatile playback queue and transport controls.
  - **`[ A ] [ B ] [ C ] [ Q ]` Quick Action Buttons**: Standardized across preset items and playlist rows, adding `[ Q ]` to append presets directly to the end of the queue.
- **Live Theme `ColorTunerPanel`**: Added interactive non-modal color tuner accessible via the top menu bar ("Color"), allowing real-time assignment of palette swatches across all 17 themed ImGui elements with live updates and instant Kotlin code generation for clipboard export. Canonical HEX palettes enforced for Solarized and Lunarized themes.
- **Background Video Keybinding (`B`)**: Added a global hotkey `B` to instantly toggle master video background rendering behind the semi-transparent UI with synchronized settings persistence.
- **Font Atlas GC Dangling Pointer & Resize Crash Fix**: Fixed a critical JVM SegFault caused by allocating the font glyph ranges (`MAIN_RANGES`) as a local stack array inside `loadFonts`. Dear ImGui retains native C pointers to glyph range arrays; when ZGC collected the array during runtime font resizing (`ctrl-` / `ctrl=`) or startup, native font rasterization accessed freed memory. Moved glyph ranges and TTF byte arrays to permanent static fields and enforced font size floor clamping ($9\text{px} \dots 96\text{px}$).
- **Linux Window Title & X11 Class Hints**: Replaced multi-byte Unicode em-dash (`—`) in GLFW window title with standard ASCII hyphen (`-`) and explicitly configured `GLFW_X11_CLASS_NAME` ("Liquid LSD") and `GLFW_X11_INSTANCE_NAME` ("liquid-lsd"), preventing mojibake/corrupted garbage characters in Linux alt-tab task switchers.

#### 3. Multi-Scale Calibrated Oscilloscopes & Signal Visualization
- **Dynamic FPS Sync for Frame-Based LFOs**: Fixed frame-synced LFO lookahead projection, auto-timebase calculations, and history sampling across `Evaluators`, `ModulatableParameter`, and `OscilloscopeDrawer` to dynamically bind to the configured target frame rate (`CVRegistry.getTargetFps()`) rather than assuming 60 FPS. At 30 FPS, setting an LFO to 30 frames now correctly oscillates at exactly $1.0\text{ Hz}$ ($1.0\text{s}$ period).
- **Multi-Scale Calibrated Timebases**: Oscilloscopes support selectable physical time windows spanning from fast transients to circadian cycles: `1s` ($250\text{ms/div}$), `10s` ($2.0\text{s/div}$), `100s` ($20\text{s/div}$), `15m` ($3\text{m/div}$), `2.5h` ($30\text{m/div}$), and `24h` ($4\text{h/div}$). Time range dropdown combo widths and spacing dynamically autoscale with font size.
- **Real-Time Lookahead Future Projection**: Real-time forward waveform projection for deterministic LFO modulators rendered in front of the `NOW` playhead.
- **Decoupled Per-Scope Timebases**: Timebase selections across individual CV scopes (LFO, Audio, Trigger, MIDI) and the Final parameter oscilloscope are completely decoupled. Changing the time window on one tab no longer changes the scale of other tabs.
- **Auto Scale Exclusively for LFO**: The `Auto` timebase option (which dynamically fits $1\text{–}2$ periods of the active waveform) is offered exclusively on the **LFO** oscilloscope. **Audio**, **Trigger**, **MIDI**, and **Final** default to **`10s`** (displaying the full recorded history window) and provide fixed physical options (`1s` to `24h`).
- **100% True Recorded History on Final**: The Final parameter oscilloscope always renders $100\%$ recorded parameter history (`param.history`) across the entire screen width with a right-aligned `NOW` playhead and backward-calibrated timestamp divisions, functioning as a real-time historical seismograph combining all base value, LFO, Audio, Trigger, and MIDI modulations.
- **Flat Zero Baseline Outside Recorded History**: When viewing extended timebases ($100\text{s}$, $15\text{m}$, $2.5\text{h}$, $24\text{h}$) where the physical window exceeds the buffer size, the pre-history span is rendered as a flat horizontal line resting strictly at zero ($0.0$), eliminating baseline see-saw tilt while live audio plays.
- **Peak-Detect Anti-Aliased Waveform Envelopes**: Implemented golden-ratio peak-detect envelope rendering for long-duration timebases ($100\text{s}$, $15\text{m}$, $2.5\text{h}$, $24\text{h}$). Fast-moving LFOs rendered on slow time scales display their full illuminated dynamic envelope/ribbon without stroboscopic Nyquist aliasing or flatline artifacts.
- **Brightened Grid Ticks & Dynamic Timestamp Badges**: High-contrast, crisp grid division ticks and legible timestamp numbers with dynamic height positioning for clear readability across all themes and zoom levels (`-250ms`, `-2s`, `NOW`, `+2s`, `+15m`, `+4h`, etc.).
- **Unified Oscilloscope Architecture**: Consolidated all oscilloscope rendering into `OscilloscopeDrawer`, eliminating duplicated drawing code across UI panels.

#### 4. Real-Time Beat Detection & Flywheel Engine Overhaul
- **Spectral Flux Onset Beat Analysis**: Replaced raw RMS amplitude beat input with half-wave rectified multi-band spectral flux. Beat detection operates on sharp transient impulses, preventing false triggers on sustained drones or synth bass notes.
- **Sub-Block Parabolic Peak Interpolation**: Added parabolic peak interpolation to STFT Comb and Autocorrelation analysis tasks. Eliminates discrete block-quantization BPM jumps for smooth, floating-point tempo tracking.
- **Background Phase Anchor Alignment**: Background analysis computes cross-correlation beat phase alignment anchors, ensuring beat-synced oscillators (`beatSine`, beat LFOs) lock their peaks directly to audio transient hits.
- **Dual-Time-Constant Peak-Triggered PLL**: Refined Phase-Locked Loop (`BeatDetectionMode.PLL`) to evaluate only on local onset peaks with fast phase correction ($\alpha$) and damped period inertia ($\beta$), eliminating tempo wobble on syncopated hits.
- **Smooth Flywheel Phase Slewing**: Upgraded `AudioEngine` flywheel accumulation to apply second-order phase slewing, smoothly nudging beat phase over audio blocks without instantaneous phase jumps or visual pops.
- **Beat Detection Confidence Metric**: Added a peak-to-average energy confidence metric $C \in [0, 1]$ to stabilize tempo during ambient breakdowns or silent sections.

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
- **Library New Preset Creation**: Added a dedicated `[Create new preset...]` row with `[ A ] [ B ] [ C ]` buttons positioned above the preset list in the Library. Clicking a deck button ejects/resets the deck and switches Preset Grid focus directly to that deck.
- **Mixer Monitor Left-Click Deck Focus**: Left-clicking any deck preview monitor (`Deck A`, `Deck B`, or `Deck C`) in the Mixer Monitor panel directly focuses the Preset Grid to that deck (`activeTopTab`).
- **Redesigned Deck Monitor Toolbar**: Combined the top preset header row and bottom patch label across Deck A, Deck B, and Deck C preview monitors into a unified interactive toolbar (`[Save] [Eject] [Preset Bar]`) with corner letter badges (`A`, `B`, `C`).
- **Mixer Monitor Vertical Scrollbar Elimination**: Overhauled `MixerMonitorLayoutCalculator` to comprehensively calculate non-aspect vertical chrome, eliminating unwanted vertical scrollbars while preserving aspect preview monitors.
- **Robust Panel Splitters**: Replaced dummy ImGui splitter windows with direct mouse hit-testing and foreground draw list rendering, ensuring resize cursors and drag interactions remain active at all times.
- **Preset Grid Knob Indicators**: Refined circular knob meters across `MONOPOLAR`, `BIPOLAR`, `ENDLESS`, and `DISCRETE` modes in `PresetGridRenderer` by replacing the solid value circle with an elongated inward radial needle pointer (`trackRadius * 0.3f`), boosting background track arc/circle brightness, and adding a vibrant yellow cross-track tick mark.
- **Resolution-Independent Grid Scaling**: Replaced hardcoded cell pixel dimensions with a dynamic `GridMetrics` geometry token system that scales Preset Grid cells and circular readout knobs automatically with global UI font size (`baseSize`). Added a **"Grid Knob Cell Scale"** setting slider (0.70x to 2.00x) in **Settings -> Appearance**.
- **Comprehensive Font Autoscaling**: Dynamic font-scaling across settings modals, empty deck launchpads, cell config tab rows, range sliders, and Lucide icons.
- **Library Live Auto-Refresh**: Real-time filesystem change monitoring across `LibraryPanel` and `ImGuiFileBrowser`, removing redundant manual Refresh buttons and automatically updating file listings when on-disk files change.
- **Playlist Menu Bar Streamlining**: Removed action buttons from the playlist editor menu bar in Library and consolidated them into the right-click context menu.
- **Cell Config Modulator Layout & Responsive UI**: Restructured modulator headers (`ModulatorHeaderRow` and `Lfo2Section`) so the modulator title (`Onset / Transient`, `Accent / Peak`, `Amplitude`, `Low`, `LFO 2`, etc.) renders on the first row with the clear/reset button, and controls (mute/bypass, dice, operator combo) render cleanly on the second row. Moved Asymmetry preset buttons below Shape options across LFO 1 and LFO 2 sections to improve usability on compact/small screens, and relocated Modulation Mode above LFO 2 Shape options.
- **Preserved Modulator Bypass on Depth Edits**: Removed auto-unbypassing when adjusting modulator Depth sliders in `CellConfigPanel`, ensuring bypassed modulators remain muted until explicitly enabled.

#### 9. Performance & Zero-Allocation Hot-Path Optimizations
- **Oscilloscope & Modulation GC Optimization**: Replaced per-call `HashSet` instantiation in `isCvSourceBipolar` with a zero-allocation branch, eliminating over $180{,}000$ GC object allocations per second on the 60 FPS render path during anti-aliased waveform rendering.
- **Render-Loop Hot-Path Cleanups**: Preallocated immutable timebase lists/arrays in `OscilloscopeDrawer` and reused persistent `ImInt` wrappers across oscilloscope timebase combos and `ModulatorHeaderRow` operator selectors, ensuring strict ImGui zero-allocation draw rules.
- **Eliminated Dead Multi-Trace History Loops**: Removed unused per-frame modulator history evaluation loops and unreferenced `modulatorHistories` buffers in `FinalParamSection` and `CellConfigPanel`.
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
