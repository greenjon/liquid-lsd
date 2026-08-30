# Liquid LSD — Release Notes

## Version 1.0.0-beta.30

> [!NOTE]
> **Release 1.0.0-beta.30** introduces live WebSocket broadcasting from Liquid LSD Desktop, the standalone browser-based WebGL2 visualizer with 10+ shader ports, real-time Web Audio DSP streaming, an interactive retro CRT TV shell with phosphor/scanline post-processing, and a stateless Node.js relay server with 24/7 Autopilot fallback.

---

### Key Highlights

#### 1. Library Menu Bar Action Toolbar & Quick Audition Padlock (`llm.slop.liquidlsd.ui.browser`)
- **Unified Menu Bar Action Strip**: Migrated the preset routing toolbar (`[🔒] [A] [B] [BG] [PV] [Q] [BGQ] [+]`) into the top Library Menu Bar alongside the layout mode buttons, utilizing previously empty horizontal space and expanding list visibility in Column 1.
- **Global 4-Column Preset Selection**: Single-clicking or navigating any item across **Preset Library**, **Playlist Editor**, **A/B Play Queue**, or **Background Queue** sets a unified global selection and clears other columns for unambiguous routing.
- **Context-Aware Button Dimming**: Dynamically dims destination buttons when redundant (e.g. `[Q]` dims when selecting an item already in the A/B Queue, `[BGQ]` dims for items in the Background Queue).
- **Quick Audition Latch (`[🔒]`) & Smart `PV` Auto-Latch**: Toggling the padlock button arms sticky audition mode and auto-latches to **Deck PV** (Preview) by default. Clicking another deck button (`A`, `B`, `BG`) switches the latch target, while clicking the active deck button unlatches it.
- **Single-Click & Arrow Key Auditioning**: With the padlock armed, single-clicking any preset or navigating with `↑`/`↓` arrow keys in any column immediately triggers non-blocking asynchronous patch loading (`loadDeckPresetAsync`) to the latched deck.
- **Robot Icon Queue Toggles (`BOT` / `BOT_OFF`)**: Replaced raw text checkboxes in A/B Queue and Background Queue (`AUTO-VJ`, `AUTO-BG`) with robot icon toggle buttons (`Icons.BOT` active / `Icons.BOT_OFF` inactive), matching DJ software conventions like Mixxx.

#### 2. Audio Engine Settings & Real-Time Monitor Consolidation (`llm.slop.liquidlsd.ui`)
- **Low-Signal 120 BPM Fallback Lock**: When incoming audio energy drops below analysis thresholds (silence, quiet passages, or background noise), `BeatDetector` smoothly transitions to and locks at **120.0 BPM** (`slewRate = 0.05f`), suppressing erratic phase nudges and eliminating wild counter swings.
- **Tempo Stability Gating**: When a valid signal is detected, the engine holds the stable 120 BPM lock while analyzing candidate tempos. Once a steady tempo is locked with harmonic octave matching for the required duration, the flywheel PLL seamlessly transitions to the live music BPM.
- **Integrated Audio Engine Tab**: Consolidated driver selection, JACK auto-reconnect, beat detection configuration, input gain, and real-time oscilloscopes into the dedicated "Audio Engine" tab within `SettingsPanel.kt`.
- **Modular Zero-Allocation Architecture (`AudioEnginePanel.kt`)**: Retained modular file separation in `AudioEnginePanel.kt` with class-level pre-allocated buffers and zero runtime memory allocations.
- **Direct Menu Bar Routing**: Clicking "Audio Engine" in `MenuBar.kt` now opens the Settings dialog directly on the Audio Engine tab for quick 1-click access.

#### 3. Live Web Broadcasting & Desktop WebSocket Broadcaster (`llm.slop.liquidlsd.broadcast`)
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

#### 7. Audio CV Normalization & Envelope Follower Isolation Fixes
- **Unipolar Unit Scaling (`[0.0, 1.0]`)**: Normalized audio band RMS energy (`amp`, `bass`, `mid`, `high`) and onset/accent triggers in `AudioEngine.kt` to the standard $[0.0, 1.0]$ range, preventing parameter clipping at moderate depth values ($0.5$).
- **Oscilloscope Active Modulator Filtering**: Fixed `CellConfigPanel.kt` oscilloscope calculation to only evaluate active, unbypassed modulators when active bands exist, preventing muted raw bands from dominating and flattening follower smoothing curves.
- **Virtual Modulator Auto-Activation**: Automatically activates (`bypassed = false`) virtual audio bands when users adjust their presets or attack/decay/depth sliders.

#### 8. Preset Grid Keyboard Shortcuts & Cell Middle-Click Mute Fixes
- **Robust Modulator Extraction for Copy/Paste**: Fixed `PresetGridKeyboard.kt` copying logic to correctly resolve composite modulator source IDs for `AUDIO` (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`), `TRIGGER` (`trigger_onset`, `trigger_accent`), `MIDI` (`midi_cc_*`), and `FINAL` / row selections instead of failing with empty lists.
- **Cross-Column Routing on Paste**: Enhanced `ClipboardManager.kt` to safely remap pasted modulators across differing destination column types (`MIDI`, `AUDIO`, `TRIGGER`, `LFO`) while properly removing existing modulators of the target source family.
- **Row-Level Copy/Paste with Clamp Scaling**: Selecting `FINAL` or clicking the parameter row label copies full parameter definitions with automated range scaling (`srcRange` $\to$ `destRange`) applied on paste.
- **Reliable Middle-Click Cell Mute/Unmute**: Fixed CV cell middle-click detection to use `isCellHovered && ImGui.isMouseReleased(2)` consistent with MIDI, Final, and Row label cells, eliminating missed clicks caused by raw coordinate box checking. Middle-clicking an unmapped CV cell now immediately instantiates a default active modulator.
- **Text Input Shielding**: Keyboard shortcuts in `PresetGridKeyboard.kt` safely yield when text input fields (`io.wantTextInput`) are active to prevent accidental edits while naming presets or typing parameter notes.

#### 8. Desktop-to-Web File Synchronization & Drift Tracking Subsystem
- **Sync Manifest (`web/sync_manifest.json`)**: Configured authoritative mapping of all desktop shaders (`src/main/resources/shaders/`, `library/sources/`) and algorithmic Kotlin math modules (`Icosahedron.kt`, `Evaluators.kt`, `WebPresetSerializer.kt`).
- **Zero-Dependency CLI Tool (`scripts/sync_web.py`)**: Standalone tool providing `--check` (detailed color-coded drift report), `--apply` (automatic WebGL2 shader transpilation), and `--mark-synced` (manifest hash synchronization for manual review files).
- **Gradle & CI Drift Verification (`checkWebSync`, `syncWeb`, `WebSyncTest.kt`)**: Added Gradle tasks and a fast JVM unit test to guarantee zero asset/algorithmic drift between Desktop and Web.

#### 10. Auto-Healing Preset Loader & Unified Dirty Transition Guard
- **Unified Dirty Transition Guard (`DeckPresetController.guardDeckTransition`)**: Centralized all dirty preset transition checks across the entire UI into a single robust guard pipeline. Whether ejecting a deck, performing utility Move/Copy/Swap drag actions, loading via Library buttons (`[A] [B] [BG] [PV]`, numeric keys `1`–`4`, Quick Audition Padlock), double-clicking in Library/Playlist Editor, creating new presets, or dragging `.lsd` files onto decks, `AutoVjDirtyBehavior.AUTO_SAVE` and `AUTO_DISCARD` now immediately execute without unwanted prompts.
- **Universal 4-Deck Confirmation Modal (`PopupManager.kt`)**: Refactored `PopupManager` to use a callback-driven confirmation dialog supporting all 4 decks (Deck A, Deck B, Deck BG, Deck PV) whenever the behavior is set to `SKIP` (Prompt).
- **Auto-Healing Schema Sanitization & Migration (`PresetManager.kt`)**: Implemented `sanitizePresetDto` in `PresetManager.loadDeckPresetAsync`. When loading presets with legacy or missing visual source/feedback parameters, the loader automatically injects engine defaults, purges obsolete keys, silently rewrites the updated `.lsd` file to disk on a background I/O thread, and ensures zero schema drift.
- **Canonical Baseline Snapshotting (`PresetManager.kt`)**: Captured canonical `DeckPresetDto` snapshots immediately following asynchronous preset and session loading (`mixer.deckX.toDto(...)`). Newly loaded presets consistently start in a clean state (`isDeckDirty == false`), while correctly marking decks dirty upon subsequent parameter modifications.
- **Complete Visual Source & Feedback Parameter Restoration (`PresetModels.kt`)**: Fixed `Deck.applyDto` to reset baseline parameter defaults before applying incoming preset maps, restored `source.globalAlpha` application from `dto.globalAlpha`, and added full `fbKaleido` serialization support across `Deck.toDto` and `Deck.applyDto`.

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
- **Cell Muting System**: Toggle any CV modulation cell (LFO, Audio, Trigger, MIDI) from sending values to live parameters (`Final`) while keeping the oscilloscope live and animated. Middle-clicking any active or muted cell toggles its mute state.
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

