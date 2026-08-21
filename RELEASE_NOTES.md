# Liquid LSD — Release Notes

## Version 1.0.0-beta.27

> [!NOTE]
> **Release 1.0.0-beta.27** is a major feature and performance release introducing multi-band cross-spectral autocorrelation beat detection, master crossfader manual takeover with CV auto-centering, "Jump the Line" Auto-VJ standby staging, 3-column library workflow with quick action buttons, sticky oscilloscopes with high-resolution LFO rendering, percentage-based GUI scaling with HiDPI auto-detection, keyboard preset management with permanent deletion warnings, and core visual source streamlining for low-power and integrated GPUs.

---

### Key Highlights

#### 0. Icosa-Dodeca Visual Source — Morph Smoothness & Color Improvements
- **C² Morph Transitions (`smootherstep`)**: Replaced the four piecewise-linear ramps in `getMorphState` with Ken Perlin's quintic `smootherstep` (`6t⁵ - 15t⁴ + 10t³`), providing zero first *and* second derivative at every phase boundary (0, 0.25, 0.5, 0.75). Eliminates the visible velocity snap/pop when driving `uMorph` with an LFO.
- **Cross-Faded Symmetry Sectors**: The "Sectors" color method now smoothly cross-fades between 3-fold (icosahedral) and 5-fold (dodecahedral) sector coloring, driven by the morph parameter `m`. Both sector palette values are computed independently and blended via `mix(col3, col5, mState)`, eliminating the previous hard visual flip at `m = 0.5`.
- **Sharper Spike Normals**: Reduced the central-difference normal epsilon from `0.002` to `0.001`, improving normal accuracy at narrow stellated spike tips.
- **Ray-Marcher Efficiency**: Advanced ray start from `t = 0.5` to `t = 1.8` (shape begins at ~`t = 2.0` from camera) and tightened the post-hit overstep from `+0.04` to `+0.02` to avoid skipping past thin spike features while reclaiming those early wasted steps.

#### 1. Multi-Band Autocorrelation Beat Engine & Benchmarking Suite
- **Multi-Band Cross-Spectral Autocorrelation Engine (`BeatDetectionMode.AUTOCORRELATION`)**: Upgraded beat tracking to maintain zero-allocation primitive FloatArray ring buffers (`bassHistory`, `midHistory`, `highHistory`, 2048 blocks) on the real-time audio callback thread.
- **Harmonic Comb Unwrapping**: Implemented half-lag ($d/2$) evaluation to eliminate half-tempo (e.g. 60 BPM) and double-tempo (e.g. 200 BPM) octave traps by verifying fundamental beat periods, ensuring 100, 120, 128, and 140 BPM tracks lock precisely to their fundamental tempo.
- **Sub-Block Parabolic Lag Interpolation**: Fits a 2nd-order parabola over correlation peaks to extract sub-block fractional lag offsets $\delta$, achieving floating-point tempo tracking within $\pm 0.1$ BPM.
- **Gaussian Musical Tempo Weighting**: Applies a subtle Gaussian curve centered at 120 BPM ($\sigma = 80$ BPM) to bias candidate selection towards natural musical tempos during ambiguous transients.
- **Synthetic Audio Benchmark Suite (`BeatDetectorBenchmarkTest.kt`)**: Built automated synthetic audio tests for 120 BPM House, 128 BPM EDM, 140 BPM Dubstep, 100 BPM Hip-Hop, and silent breakdowns to validate lock speed (< 3.0s), lock accuracy (< 1.5 BPM error), and flywheel momentum.
- **Audio Settings Sliders**: Refactored `AudioEnginePanel` controls for energy threshold, PLL adaptation, and correlation analysis window length ($1.0\text{s} \dots 10.0\text{s}$).

#### 2. Master Crossfader Manual Takeover & CV Auto-Centering
- **Instant Manual Takeover**: Interacting with the master Crossfader via mouse or physical MIDI CC immediately takes priority:
  - **Auto-VJ Disarm**: Auto-VJ is turned off (`isAutoVJEnabled = false`) and any active auto-fade transition halts cleanly at current position.
  - **CV Modulation Muting**: All non-MIDI modulators (LFO, Audio Followers, Triggers) on `Mixer/crossfade` are automatically muted (`bypassed = true`), giving the performer 1:1 manual control without fighting background modulation.
  - **MIDI CC Modulators Preserved**: Modulators mapped to physical MIDI CCs are preserved and remain active.
- **Auto-Centering on CV Unmute**: Unmuting any CV modulator on `Mixer/crossfade` automatically snaps `crossfade.baseValue` to `0.0` (unbiased center), allowing LFOs and audio followers to oscillate symmetrically across both decks without clipping against manual hold positions. Modulator `DC Offset` provides optional deck bias.
- **Playlist "Play Now" Auto-VJ Fix**: Selecting *"Play now (and replace queue)"* on a playlist or preset automatically engages the `AUTO-VJ` toggle and mutes crossfade CVs for smooth automated queue crossfading.
- **Master Mixer Momentary Triggers**: Added a dedicated row of 6 momentary buttons beneath the Crossfader:
  - **Playlist Navigation**: `< Prev` (`Mixer/queuePrev`) and `Next >` (`Mixer/queueNext`) for stepping through the active playlist queue.
  - **Deck & Master Randomization**: `Rand A`, `Rand B`, `Rand C`, and `Rand All` for instant re-rolling of active modulators and randomizable parameters with full Undo history support.
- **Disabled Randomization Cleanups**: When randomization is disabled in Settings, parameter rows for randomization, yellow randomization range arcs in cell dials, and Mixer Monitor randomize buttons are automatically hidden.

#### 3. Auto-VJ & Manual Deck Loading Integration ("Jump the Line")
- **Standby Deck Staging ("Jump the Line")**: When a preset is manually loaded into the standby/inactive deck while Auto-VJ is active, that deck is marked as staged. The next Auto-VJ transition automatically fades into the staged preset without overwriting it from the queue, preserving the next queue track for the subsequent cycle.
- **Active Deck Overrides**: Manually loading into the live deck replaces the output immediately while keeping Auto-VJ armed and the queue untouched.
- **Queue Preservation**: Manual deck loading when Auto-VJ is OFF never modifies the queue contents or active index.
- **Seamless Mid-Set Arming**: Enabling Auto-VJ mid-set does not cause jump cuts; it arms the standby deck and smoothly waits for the next advance trigger (CV pulse, beat trigger, MIDI CC, or manual Next button).
- **Deck C Independence**: Manual loading and interactions on Deck C (master overlay) remain completely independent of the A/B Auto-VJ pipeline.

#### 4. Library & Playlist Management Enhancements
- **3-Column Side-by-Side Workflow**:
  - **Left Column (Presets Library Pool)**: Displays a flat list of all visual presets with real-time text search filtering across preset names and tags, plus `[Create new preset...]` deck eject rows.
  - **Middle Column (Playlist Editor)**: Dedicated setlist editor featuring playlist selector combo, `[ + ]` new playlist button, `[ ••• ]` actions menu (Play now, Append to queue, Rename, Clone, Delete), drag-and-drop preset insertion from the library with visual mint-green insertion line, and auto-save on edit.
  - **Right Column (Play Queue)**: Live volatile playback queue and transport controls.
- **`[ A ] [ B ] [ C ] [ Q ]` Quick Action Buttons**: Standardized across preset items and playlist rows, adding `[ Q ]` (violet accent) to append presets directly to the end of the queue.
- **Play Queue & Playlist `Delete` / `Backspace` Key Support**: Pressing `Delete` or `Backspace` on a selected item in the Play Queue or Playlist Editor removes the preset from that list.
- **Preset Library `Delete` / `Backspace` Key Support & Warnings**: Pressing `Delete` or `Backspace` on a selected preset in the library opens a permanent deletion warning modal ("Warning: This will permanently delete this preset from your library. This action cannot be undone.").
- **Cascading Reference Cleanup**: Permanently deleting a preset file removes all occurrences from all `.lsdset` playlist files on disk and from the live Play Queue.
- **UI Helper Modularization**: Extracted `BrowserDeckButtons` helper for unified deck button rendering.

#### 5. Sticky Oscilloscope & Cell Config UX Overhaul
- **Sticky Oscilloscope & Header in Cell Config**: The top region of the Cell Config panel (CV tab switcher, parameter title, and the full live oscilloscope) is fixed at the top of the panel, allowing modulator controls below to scroll independently in a child view.
- **Live BPM-Aware AUTO Timebase**: Oscilloscope's `AUTO` mode reads actual running BPM from `CVRegistry` when calculating beat-based LFO periods, sizing scope windows accurately at any tempo.
- **High Analytical Step Resolution**: Sampling steps for LFO scopes increased with a 1.5x pixel-width multiplier (`(pixelWidth * 1.5f).toInt().coerceIn(60, 1000)`), eliminating polygonal stepping and pixelation on fast LFO periods.
- **Pixel-Perfect Seam Alignment**: Past segment endpoints connect seamlessly into future segment starting points (`lfoSeamY`), eliminating 1-pixel discontinuities at the `NOW` playhead.
- **Modulator Header Organization**: Modulator titles (`Onset / Transient`, `Accent / Peak`, `Amplitude`, `Low`, `LFO 2`, etc.) and reset buttons render on the first row, with controls (mute/bypass, dice, operator combo) on the second row.
- **Cell Mute / Preview System**: Toggle any CV modulation cell (LFO, Audio, Trigger, MIDI) from sending values to live parameters (`Final`) while keeping the oscilloscope live and animated. Muted cells in the Preset Grid drop knob arc/meter opacity to 35% and display a centered 'M'. Middle-clicking any active or muted cell toggles its mute state.

#### 6. Visual Source Library Streamlining & Icosa-Dodeca Generator
- **Added Icosa-Dodeca Morph & Stellation Source (`icosa_dodeca`)**: Added a closed-form geometric generator providing a unified 4-phase continuous cyclic morph across canonical polyhedra: `0.0–0.25` (Icosahedron → Dodecahedron), `0.25–0.50` (Dodecahedron → Great Stellated Dodecahedron), `0.50–0.75` (Great Stellated Dodecahedron → Great Icosahedron), and `0.75–1.00` (Great Icosahedron → Icosahedron), with mathematical depth/symmetry coloring and semi-transparent crystal reveal face rendering.
- **Removed GPU-Heavy Raymarchers**: Removed heavy distance-field fractal and 4D raymarchers (`clifford_torus`, `kifs`, `mandelbox`, `pseudo_kleinian`, `mandelbulb`) that caused high ALU load and frame drops on integrated GPUs (Intel Iris Xe / AMD APUs).
- **Refocused Core Visual Engine**: Refocused the built-in generator collection on high-performance, lightweight sources:
  - `icosa_dodeca`: Icosahedron-Dodecahedron duality morph and Kepler-Poinsot stellations.
  - `mandala`: Ultra-low overhead 2D/3D hardware ribbon rasterization.
  - `attractor_feedback`: 2D strange attractor density inverse mapping with feedback decay.
  - `dynamic_spiral`: Analytical Newton-Raphson range-culled spiral particle trails.
  - `gyroid`: Closed-form 3D triply periodic minimal surface raymarcher.
  - `chladni`: Closed-form 2D/3D acoustic resonance standing wave raymarcher.

#### 7. GUI Scaling, Hotkeys & System Polish
- **GUI Scale Percentage Model (75%–200%)**: Continuous percentage slider in Settings → Appearance with 5% snapping increments.
- **Updated Hotkeys**: `Ctrl+-` / `Ctrl+=` adjust GUI scale by ±5% per press within 75%–200% bounds.
- **HiDPI / 4K Auto-Detection**: Auto-detects OS content-scale factor via `glfwGetWindowContentScale` on first launch and snaps to the nearest 5% scale step.
- **Background Video Keybinding (`B`)**: Global hotkey `B` instantly toggles master video background rendering behind semi-transparent UI.
- **Production Logging**: Set default log level to `WARN` to eliminate console I/O overhead.

---

### 📜 Commit History (v1.0.0-beta.26 → v1.0.0-beta.27)

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
- **Aspect-Aware UI Previews & Splitter Clamping**: `MixerMonitorLayoutCalculator` and `MixerMonitorPanel` dynamically scale Deck A, Deck B, Deck C, and Master preview heights to match the active render aspect ratio. Splitter positioning clamps Column 3 width to the maximum preview capacity given window height, eliminating letterbox dead space.
- **Opt-In Secondary Video Output & Menu Control**: Secondary window is strictly opt-in on single-monitor setups (no unsolicited popups on startup). Added an **"Output Window"** item in the main menu bar to toggle external/secondary output window, and removed the Spacebar hotkey to prevent accidental triggers.

#### 2. UI Architecture Modularization & Interactive Developer Tools
- **`DeckPresetController` Extraction**: Decoupled deck preset file actions (Save, Save As, Rename, Duplicate, Overwrite, Eject, Reset), file dialog handling, and save status notifications from `UIManager` into a dedicated controller class.
- **`UIThemeStyler` Extraction**: Extracted dynamic ImGui theme application, custom color palette mapping (`BORING`, `DARK_SOLARIZED`, `LIGHT_SOLARIZED`, `DARK_LUNARIZED`, `LIGHT_LUNARIZED`, `NEON`), window background alpha/video blending, neon gradient rendering, and proportional `ImGuiStyle` size scaling.
- **`SplitterManager` Extraction**: Extracted multi-column workspace splitter state, drag interaction tracking, cursor hinting (`ResizeEW`/`ResizeNS`), double-click reset positions, and draw-list divider rendering from `UIManager`.
- **Library Panel Renaming**: Refactored and renamed the 3-column Preset, Playlist, and Play Queue dock from "Asset Browser" to **"Library"**, standardizing terminology with DJ/VJ performance software, and modernizing `LibraryPanel`, `LibraryMode` (`FULL`, `HALF`, `HIDE`), and backward-compatible settings persistence.
- **Live Theme `ColorTunerPanel`**: Added interactive non-modal color tuner accessible via the top menu bar ("Color"), allowing real-time assignment of palette swatches across all 17 themed ImGui elements with live updates and instant Kotlin code generation for clipboard export. Canonical HEX palettes enforced for Solarized and Lunarized themes.
- **Background Video Keybinding (`B`)**: Added a global hotkey `B` to instantly toggle master video background rendering behind the semi-transparent UI with synchronized settings persistence.
- **Linux Window Title & X11 Class Hints**: Replaced multi-byte Unicode em-dash (`—`) in GLFW window title with standard ASCII hyphen (`-`) and explicitly configured `GLFW_X11_CLASS_NAME` ("Liquid LSD") and `GLFW_X11_INSTANCE_NAME` ("liquid-lsd"), preventing mojibake/corrupted garbage characters in Linux alt-tab task switchers.

#### 3. Multi-Scale Calibrated Oscilloscopes & Signal Visualization
- **Dynamic FPS Sync for Frame-Based LFOs**: Fixed frame-synced LFO lookahead projection, auto-timebase calculations, and history sampling across `Evaluators`, `ModulatableParameter`, and `OscilloscopeDrawer` to dynamically bind to the configured target frame rate (`CVRegistry.getTargetFps()`) rather than assuming 60 FPS. At 30 FPS, setting an LFO to 30 frames now correctly oscillates at exactly $1.0\text{ Hz}$ ($1.0\text{s}$ period).
- **Multi-Scale Calibrated Timebases**: Oscilloscopes support selectable physical time windows spanning from fast transients to circadian cycles: `1s` ($250\text{ms/div}$), `10s` ($2.0\text{s/div}$), `100s` ($20\text{s/div}$), `15m` ($3\text{m/div}$), `2.5h` ($30\text{m/div}$), and `24h` ($4\text{h/div}$). Time range dropdown combo widths and spacing dynamically autoscale with font size.
- **Real-Time Lookahead Future Projection**: Real-time forward waveform projection for deterministic LFO modulators rendered in front of the `NOW` playhead.
- **Decoupled Per-Scope Timebases**: Timebase selections across individual CV scopes (LFO, Audio, Trigger, MIDI) and the Final parameter oscilloscope are completely decoupled. Changing the time window on one tab no longer changes the scale of other tabs.
- **Auto Scale Exclusively for LFO**: The `Auto` timebase option (which dynamically fits $1\text{–}2$ periods of the active waveform) is offered exclusively on the **LFO** oscilloscope. **Audio**, **Trigger**, **MIDI**, and **Final** default to **`10s`** (displaying the full recorded history window) and provide fixed physical options (`1s` to `24h`).
#### 4. Cell-Level Mute & Live Oscilloscope Preview
- **Cell Mute / Preview System**: Users can mute any CV modulation cell (LFO, Audio, Trigger, MIDI) from sending values to live parameters (`Final`) while keeping the Oscilloscope 100% live and animated in Cell Config for real-time waveform previewing.
- **Preset Grid Visual Indicators**: Muted cells in the Preset Grid drop knob arc/meter opacity to **35%** and display a centered sans-serif **'M'** inside the knob.
- **Master Scope Mute Toggle**: Cell Config features a master `[ LIVE ]` / `[ MUTED ]` toggle button in the top-right corner of the Oscilloscope header bar, with an amber `[SCOPE LIVE — OUTPUT MUTED FROM FINAL]` watermark when muted.
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
