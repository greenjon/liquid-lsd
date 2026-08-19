# Liquid LSD — Release Notes

## Version 1.0.0-beta.25

> [!NOTE]
> **Release 1.0.0-beta.25** focuses on UI architecture modularization, power-efficient render loop frame rate limiting, and legacy data model streamlining.

### Key Highlights

#### 1. UI Architecture Modularization & Interactive Developer Tools
- **`DeckPresetController` Extraction**: Decoupled deck preset file actions (Save, Save As, Rename, Duplicate, Overwrite, Eject, Reset), file dialog handling, and save status notifications from `UIManager` into a dedicated controller class.
- **`UIThemeStyler` Extraction**: Extracted dynamic ImGui theme application, custom color palette mapping (`BORING`, `DARK_SOLARIZED`, `LIGHT_SOLARIZED`, `DARK_LUNARIZED`, `LIGHT_LUNARIZED`, `NEON`), window background alpha/video blending, neon gradient rendering, and proportional `ImGuiStyle` size scaling.
- **`SplitterManager` Extraction**: Extracted multi-column workspace splitter state, drag interaction tracking, cursor hinting (`ResizeEW`/`ResizeNS`), double-click reset positions, and draw-list divider rendering from `UIManager`.
- **Library Panel Renaming**: Refactored and renamed the 3-column Preset, Playlist, and Play Queue dock from "Asset Browser" to **"Library"**, standardizing terminology with DJ/VJ performance software, and modernizing `LibraryPanel`, `LibraryMode` (`FULL`, `HALF`, `HIDE`), and backward-compatible settings persistence.
- **Live Theme `ColorTunerPanel`**: Added interactive non-modal color tuner accessible via the top menu bar ("Color"), allowing real-time assignment of palette swatches across all 17 themed ImGui elements with live updates and instant Kotlin code generation for clipboard export.

#### 2. Frame Rate Limiting & CPU Efficiency
- **Restored 30 FPS Power-Saver Cap**: Re-enabled two-stage CPU-efficient pacing in the primary OpenGL/GLFW render loop honoring `session.uiTheme.maxFps` (30 FPS vs 60 FPS), reducing CPU and GPU utilization during background or power-sensitive operation.

#### 3. Preset & Data Model Streamlining
- **Legacy Global Preset Code Removal**: Completely removed obsolete `GlobalPresetDto` and unused global preset converter/queue methods from `PresetManager` and `PresetModels`. Full session state and multi-deck configurations continue to be cleanly and durably managed via `SessionStateDto` (`library/last_session.json`).

---

## Version 1.0.0-beta.24

> [!NOTE]
> **Release 1.0.0-beta.24** is a major consolidation release rolling up all features, architectural enhancements, performance optimizations, and UI overhauls since `v1.0.0-beta.21`.
> Highlights include a multi-scale calibrated oscilloscope engine with real-time future waveform projection, deterministic frame-synced LFOs, a spectral-flux beat detection flywheel overhaul, industry-standard "Preset" terminology and unified `library/` storage, dedicated `SavePresetModal` with overwrite safety, unipolar modulation and dial calibrations, resolution-independent UI scaling, and comprehensive zero-allocation render loop hot-path optimizations.

---

### Key Highlights (Rollup since v1.0.0-beta.21)

#### 1. Multi-Scale Calibrated Oscilloscopes & Signal Visualization
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

#### 2. Real-Time Beat Detection & Flywheel Engine Overhaul
- **Spectral Flux Onset Beat Analysis**: Replaced raw RMS amplitude beat input with half-wave rectified multi-band spectral flux. Beat detection operates on sharp transient impulses, preventing false triggers on sustained drones or synth bass notes.
- **Sub-Block Parabolic Peak Interpolation**: Added parabolic peak interpolation to STFT Comb and Autocorrelation analysis tasks. Eliminates discrete block-quantization BPM jumps for smooth, floating-point tempo tracking.
- **Background Phase Anchor Alignment**: Background analysis computes cross-correlation beat phase alignment anchors, ensuring beat-synced oscillators (`beatSine`, beat LFOs) lock their peaks directly to audio transient hits.
- **Dual-Time-Constant Peak-Triggered PLL**: Refined Phase-Locked Loop (`BeatDetectionMode.PLL`) to evaluate only on local onset peaks with fast phase correction ($\alpha$) and damped period inertia ($\beta$), eliminating tempo wobble on syncopated hits.
- **Smooth Flywheel Phase Slewing**: Upgraded `AudioEngine` flywheel accumulation to apply second-order phase slewing, smoothly nudging beat phase over audio blocks without instantaneous phase jumps or visual pops.
- **Beat Detection Confidence Metric**: Added a peak-to-average energy confidence metric $C \in [0, 1]$ to stabilize tempo during ambient breakdowns or silent sections.

#### 3. Deterministic Frame-Synced LFOs (LFO 1 & LFO 2)
- **Frame Frequency Mode**: Added a third frequency clocking mode, `FRAME`, alongside `TIME` and `BEAT` in the unified LFO generator. Frame-synced LFOs oscillate deterministically based on elapsed render frame count (1 to 10,000 integer frames), enabling artifact-free feedback buffer harmonization, per-frame stroboscopic/flicker effects, sample-and-hold per-frame noise, and deterministic video frame captures.
- **Integer-Locked Sliders & Dual Readouts**: Both primary carrier (LFO 1) and modulator (LFO 2) support independent frame sync with integer-locked range sliders and duration readouts (e.g. `120 frames (2.00s)`).

#### 4. Modulation Architecture & Calibration
- **Unipolar CV Modulation & Zero Silence Baseline**: Fixed modulation evaluation formulas for unipolar sources (Audio RMS, Bass/Mid/High frequency bands, Triggers, and MIDI CC). Silence ($cv = 0.0$) remains strictly at $0.0$ without introducing artificial DC offset shifts when increasing Depth, restoring full modulation dynamic range.
- **CV Modulation "Depth" Terminology Standardization**: Standardized the term for the value assigned to a CV modulator from "amplitude" (and legacy JSON "weight") to **"Depth"** across domain models (`CvModulator.depth`, `depthMin`, `depthMax`, `randomizeDepth`), evaluation logic, UI controls (Cell Config Depth range slider, LFO 2 AM Depth mode/tooltips), and documentation.
- **Preset Grid Cell Dial Calibration**: Calibrated knob meters in `PresetGridRenderer` for unipolar audio, trigger, and MIDI cells so dial needles and indicator arcs accurately reflect the true parameter modulation range ($0.0 \dots 1.0$) rather than resting at $0.5$ on silence.
- **Consolidated Modulation Evaluator**: Centralized parameter modulation evaluation into `Evaluators.kt` (`evaluateModulatedValue`), eliminating duplicate evaluation routines across UI panels.

#### 5. Preset & Library Architecture Modernization ("Patch" → "Preset")
- **Industry Standard 'Preset' Terminology Refactor**: Refactored visual parameter snapshots across the codebase from 'Patch' to 'Preset' (`PresetManager`, `DeckPresetDto`, `GlobalPresetDto`, `PresetGridPanel`, `PresetGridState`, `PresetGridRenderer`, `PresetGridTabs`, `PresetGridUndo`).
- **Unified `library/` User Storage Directory**: Standardized user data root to `library/` (`library/presets/*.lsd`, `library/midi/*.json`, `library/playlists/*.lsdset`, `library/sources/`, `library/last_session.json`).
- **Codebase Streamlining & Legacy Code Removal**: Removed legacy backwards compatibility shims across data models, serialization, session management, and UI browsers. Standardized `ModulatorDto` serialization to directly serialize `depth`, `depthMin`, `depthMax`, and `randomizeDepth` without legacy `@SerialName("weight")` aliases.

#### 6. UI & UX Refinements, SavePresetModal & Responsive Layouts
- **Dedicated SavePresetModal**: Replaced the floating `DeckPresetBrowser` popup with a clean, dedicated `SavePresetModal` for entering preset names and comma-separated tags directly when selecting "Save As..." (or "Save" on an untitled deck). Dynamic action titles (`Save Preset As`, `Rename / Edit Preset Tags`, `Duplicate Preset`) render cleanly in the ImGui modal title bar without redundant body text.
- **Universal Overwrite Safety**: Added file existence detection and overwrite protection across all preset modal flows (`Save As...`, `Rename`, `Duplicate`). `Save As...` defaults to `${activeName}_copy` to create new files by default, and typing an existing file name prompts with an explicit amber warning badge and `[ Overwrite ]` confirmation button.
- **Asset Browser New Preset Creation**: Added a dedicated `[Create new preset...]` row with `[ A ] [ B ] [ C ]` buttons positioned above the preset list in the Asset Browser. Clicking a deck button ejects/resets the deck and switches Preset Grid focus directly to that deck.
- **Mixer Monitor Left-Click Deck Focus**: Left-clicking any deck preview monitor (`Deck A`, `Deck B`, or `Deck C`) in the Mixer Monitor panel directly focuses the Preset Grid to that deck (`activeTopTab`).
- **Redesigned Deck Monitor Toolbar**: Combined the top preset header row and bottom patch label across Deck A, Deck B, and Deck C preview monitors into a unified interactive bottom bar (`[Save] [Eject] [Preset Bar]`) with corner letter badges (`A`, `B`, `C`).
- **Mixer Monitor Vertical Scrollbar Elimination**: Overhauled `MixerMonitorLayoutCalculator` to comprehensively calculate non-aspect vertical chrome, eliminating unwanted vertical scrollbars while preserving 16:9 aspect preview monitors.
- **Robust Panel Splitters**: Replaced dummy ImGui splitter windows with direct mouse hit-testing and foreground draw list rendering, ensuring resize cursors and drag interactions remain active at all times.
- **Preset Grid Knob Indicators**: Refined circular knob meters across `MONOPOLAR`, `BIPOLAR`, `ENDLESS`, and `DISCRETE` modes in `PresetGridRenderer` by replacing the solid value circle with an elongated inward radial needle pointer (`trackRadius * 0.3f`), boosting background track arc/circle brightness, and adding a vibrant yellow cross-track tick mark.
- **Resolution-Independent Grid Scaling**: Replaced hardcoded cell pixel dimensions with a dynamic `GridMetrics` geometry token system that scales Preset Grid cells and circular readout knobs automatically with global UI font size (`baseSize`). Added a **"Grid Knob Cell Scale"** setting slider (0.70x to 2.00x) in **Settings -> Preset Grid**.
- **Comprehensive Font Autoscaling**: Dynamic font-scaling across settings modals, empty deck launchpads, cell config tab rows, range sliders, and Lucide icons.
- **Media Browser Live Auto-Refresh**: Real-time filesystem change monitoring across `AssetBrowserPanel` and `ImGuiFileBrowser`, removing redundant manual Refresh buttons and automatically updating file listings when on-disk files change.
- **Playlist Menu Bar Streamlining**: Removed action buttons from the playlist editor menu bar in Media Browser and consolidated them into the right-click context menu.

#### 7. Performance & Zero-Allocation Hot-Path Optimizations
- **Oscilloscope & Modulation GC Optimization**: Replaced per-call `HashSet` instantiation in `isCvSourceBipolar` with a zero-allocation branch, eliminating over $180{,}000$ GC object allocations per second on the 60 FPS render path during anti-aliased waveform rendering.
- **Render-Loop Hot-Path Cleanups**: Preallocated immutable timebase lists/arrays in `OscilloscopeDrawer` and reused persistent `ImInt` wrappers across oscilloscope timebase combos and `ModulatorHeaderRow` operator selectors, ensuring strict ImGui zero-allocation draw rules.
- **Eliminated Dead Multi-Trace History Loops**: Removed unused per-frame modulator history evaluation loops and unreferenced `modulatorHistories` buffers in `FinalParamSection` and `CellConfigPanel`.
- **Preserved Scope Timebases Across Clones**: Fixed `ModulatableParameter.clone()` to preserve custom per-scope timebase zoom settings across preset cloning, undo/redo snapshots, and deck preset duplication.
- **30 FPS User Setting Frame Rate Limiting**: Restored two-stage CPU-efficient sleep frame rate pacing in the main render loop bound to `session.uiTheme.maxFps`, properly enforcing the 30 FPS power-saver limit when enabled.
- **Comprehensive Unit Testing**: Added unit tests for target-FPS frame-synced LFO calculations, scope timebase cloning, source classification helpers, UITheme settings round-trips, and CV history buffer interpolations.

---

### 🛠️ Detailed Update Summary (v1.0.0-beta.17 → v1.0.0-beta.20)

#### 1. Save/Load & Session Persistence Overhaul
- **Session State Fix**: Prevented empty decks from restoring stale sources (such as KIFS) on application restart. Empty decks now serialize and restore cleanly with default Mandala baseline.
- **Dirty State Tracking**: Corrected `ParameterDto.equals()` and `ModulatorDto.equals()` so static parameter base value slider edits correctly mark the deck dirty (`isDeckDirty = true`) and display dirty indicators (`*`).
- **Filename vs DTO Alignment**: Aligned loaded DTO names with disk filenames in `loadDeckPresetAsync()`, ensuring **Save** updates the exact file loaded rather than fallback DTO names.
- **Deck C Routing**: Plumbed `isDeckC` routing through preset loading, active preset resolution, and save flows.
- **Startup Queue Trigger Fix**: Prevented false queue advance triggers on startup when `queueNext` base values restore from `last_session.json`. Suppressed automated CV queue advance triggers when AutoVJ is disabled.
- **Filename Sanitization**: Sanitized preset filenames (removing redundant `.lsd`/`.json` suffixes) and automatically cleaned up obsolete legacy `.json` files upon `.lsd` save.

#### 2. Dynamic Spiral Visual Source & Shader Optimizations
- Added `DynamicSpiral` visual source with parameters for points, scale, damping, wave frequency/amplitude, shear, speed, dot size, glow, hue offset/sweep, and trail decay.
- **Integrated Phase Tracking**: Added continuous phase integration across speed and shear changes to prevent visual popping or phase jump glitches during modulation.
- **Shader Culling**: Implemented per-fragment radial range culling in `dynamic_spiral.frag` to bypass out-of-bounds fragment calculations.
- **Tuned Ranges**: Optimized point cloud distribution and default parameter ranges for smooth rendering.

#### 3. PresetGrid Performance Controls
- **Middle-Click Reset**: Middle-clicking any parameter control in the Preset Grid instantly resets its value to default.
- **Modulator Bypass Toggle**: Added a bypass toggle for individual modulators in parameter cards without requiring modulator deletion.
- **Scroll-Wheel Hover Guard**: Prevented parent panel scroll wheel conflicts when hovering over parameter sliders.
- **Mandala Lobe Pills & Stepper**: Added quick lobe selection pills (`2`, `3`, `4`, `6`, `8`, `12`) and a recipe stepper in `FinalParamSection`.
- **CV Parameter Randomization**: Added CV-triggerable parameters `Deck A Param Rand`, `Deck B Param Rand`, `Deck C Param Rand`, and `All Decks Param Rand`.

#### 4. UI Architecture & Dynamic Theming
- **`SessionContext` Refactor**: Replaced singleton access across 28+ UI panels with `SessionContext` dependency injection.
- **UI Theme Engine**: Built `UITheme` theming subsystem for dynamic UI color themes, clean mode (`F`), font scaling, and persistent settings.
- **Auto-Resizing Workspace Layout**: Implemented auto-fitting Left Panel (Preset Grid) width based on active CV columns and font zoom (`CTRL-` / `CTRL=`). Rebalanced vertical splitters so Splitter 1 is a static auto-repositioning divider and Splitter 2 resizes Middle vs Right panels.
- **MIDI Cell Tooltip Fix**: Resolved pre-existing tooltip overlay bug where hiding the MIDI column caused the `FINAL` cell tooltips to be covered by the hidden MIDI cell.
- **Seamless Cards**: Connected active side tabs seamlessly into parameter cards with inline sub-tabs and logical category grouping (Visual Source before FX).
- **Empty Deck Handling**: Added full support for inert empty deck states across `Deck`, `PresetGridPanel`, `PresetGridTabs`, and render loops.

#### 5. Build, Toolchain & Code Cleanup
- Upgraded Gradle wrapper to **Gradle 9.7.0**.
- Added middle-click flags and input guards to `PresetGridRenderer`.
- **Legacy Global Preset Code Removal**: Cleaned up obsolete `GlobalPresetDto`, `isGlobalPresetDirty`, and related legacy global preset I/O queues and converter methods from `PresetManager` and `PresetModels`. Workspace state persistence is handled cleanly by `SessionStateDto`.

---

### 📜 Commit History (v1.0.0-beta.17 → v1.0.0-beta.20)

- `bb03c2d` fix(patches): resolve save/load, preset name alignment, and startup queue advance bugs
- `1b85a39` feat(spiral): implement integrated phase tracking for smooth speed and shear transitions
- `ec093ff` perf(shader): implement per-fragment radial range culling in Dynamic Spiral
- `114abfc` Optimize dynamic spiral point distribution, fix hotkey text input guard, adjust default fbDecay, and add patch tags scratchpad plan
- `7973616` fix: tune Dynamic Spiral parameter ranges
- `5b5b082` build & ui: upgrade Gradle wrapper to 9.7.0 and enable middle-click flags in PresetGridRenderer
- `def9c6b` feat: add Dynamic Spiral visual source
- `c12c23e` ui: refine PresetGrid stroke layout and middle-click/bypass behaviors
- `08c4cb6` Implement middle-click parameter reset, modulator bypass toggle, and quick-creation in Preset Grid; prevent panel scroll-wheel conflict when hovering sliders; update docs and release notes
- `c9c9af6` docs: add beta 19 release notes detailing changes since v1.0.0-beta.17
- `700495c` Refine PresetGridPanel card background and outline border styling
- `43157cc` ui: adjust left tabs vertical alignment relative to column header height
- `0eb72b1` ui: relocate sub-tabs inline and refine patchgrid tab styling
- `0079aff` Fix Rotate X and Rotate Y display in PresetGridTabs for Mandala and cleanup defaultMin/defaultMax preset metadata
- `d7cb193` Add CV-triggered randomization parameters for Decks A/B/C and All
- `69abb47` feat(ui): add quick lobe selection pills and recipe stepper for Mandala visual source
- `aa6af97` feat(ui): add support for empty deck state and refine patchgrid layout
- `ef84af5` Reorder PresetGrid parameters to show Visual Source before FX
- `7726d1f` Refine PresetGrid visual hierarchy and pad performance monitor
- `f37ce6b` feat(ui): connect active side tab to patch grid parameter container with seamless border outline
- `b61c883` docs: update UI developer documentation and PROJECT.md for SessionContext dependency injection
- `d80f487` ui: Adjust settings panel size and center positioning conditions
- `9117aa5` ui: Implement resizable three-column layout and toggleable mixer panel columns
- `fb19654` refactor(ui): introduce SessionContext and refactor UI components to use context instead of global singletons
- `0da9057` feat: implement UI theming support and theme configuration
