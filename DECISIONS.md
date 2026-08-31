# Architectural Decisions - Liquid LSD

This document outlines the key architectural decisions made in the development of Liquid LSD, detailing the context, options considered, and the rationale behind each choice.

---

## Automated Continuous Beta Releases & Release Notes Generation

- **Decision**: Automate the creation and publishing of GitHub beta releases and release notes on every push to `main`:
  - **Push Triggers**: Configure `.github/workflows/release.yml` to trigger on `push: branches: [ main ]`, `push: tags: [ 'v*' ]`, and `workflow_dispatch`.
  - **Sequential Concurrency**: Enforce concurrency grouping (`cancel-in-progress: false`) to ensure sequential, non-colliding releases.
  - **Dynamic Beta Versioning**: On branch push, introspect existing tags, determine the latest `v1.0.0-beta.N` tag, calculate `v1.0.0-beta.(N+1)`, tag the commit via `github-actions[bot]`, and push the tag.
  - **Automated Changelog & Release Notes Extraction**: Automatically extract the rich release notes description from `RELEASE_NOTES.md` (or `docs/release_notes.md`) matching the version, falling back to compiled commit history with links and authors, naming the release directly as `${TAG_NAME}` (e.g., `v1.0.0-beta.32`), and setting `make_latest: true`.
- **Rationale**:
  - Eliminates manual release creation and manual release notes editing after every commit push.
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
  - **Continuous Phase & Cosine Generator**: Outputs continuous normalized phase $\phi(t) \in [0.0, 1.0)$ and locked cosine modulation signal $\cos(2\pi \phi(t))$ via zero-allocation queries (`getPhase`, `getCosine`, `getPhaseAndCosine`, `getPhaseAndCosinePacked`).
- **Rationale**:
  - Eliminates visual phase stutter and snapping during tempo adjustments or syncopated drum breaks.
  - Guarantees strict zero-allocation real-time safety on JACK/PipeWire audio callback threads and 60–144Hz+ rendering loops, preventing audio buffer underruns (XRUNs).

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

## GUI Scale: Percentage Model (75%–200%) instead of Raw Pixel Size

- **Decision**: Express the global UI scale as a percentage (75%–200%, 5% steps) rather than a raw `baseSize` pixel value, with `glfwGetWindowContentScale` queried on first launch to seed a sensible default.
- **Rationale**:
  - **User mental model**: "I want a 150% UI" is immediately meaningful; "I want 22.5 px body text" is not.
  - **Internally transparent**: `baseSize` (px) remains the storage format. The conversion is `baseSize = pct / 100 * 15f`. No existing save files break.
  - **HiDPI hygiene**: Without OS DPI detection, a 4K monitor renders the default 15 px controls at microscopic size (~8 CSS px equivalent). `glfwGetWindowContentScale` returns the OS-reported logical→physical pixel ratio (e.g. 2.0 on macOS Retina, 1.5 on some 4K Linux setups), which is snapped to the nearest 5% step and applied only on first run.
  - **Discrete steps**: 5% steps keep the slider tactile and prevent half-pixel font renders that look blurry in the ImGui atlas.

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

## 5. Lock-Free Audio-to-Render Data Passing
- **Decision**: Avoid mutexes/locks for thread synchronization. Instead, pass data from the audio thread to the rendering thread using `@Volatile` primitive fields (`anchorBeats`, `anchorBpm`, `anchorTimeNs`), and the custom single-writer `CvHistoryBuffer` ring-buffer.
- **Rationale**: 
  - Locking on the audio thread can cause **priority inversion**, where a lower-priority rendering thread holding the lock blocks the real-time audio thread.
  - Lock-free structures keep the threads decoupled; transient data races in visualization buffers (like the oscilloscope) are acceptable, as they cause at most a single-frame visual glitch rather than an application-wide crash or xrun.

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


