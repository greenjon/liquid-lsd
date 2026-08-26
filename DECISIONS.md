# Architectural Decisions - Liquid LSD

This document outlines the key architectural decisions made in the development of Liquid LSD, detailing the context, options considered, and the rationale behind each choice.

---

## 4-Deck Architecture (Deck BG & Deck PV), 2x2 Monitor Grid, and 4-Column Library

- **Decision**: Evolve the rendering and UI architecture from a 3-deck model (A, B, C) to a dedicated 4-deck pipeline (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`):
  - **Background Compositing Layer (`Deck BG`)**: Rendered in GLSL (`mixer.frag`) directly beneath the crossfaded A/B foreground (`rgb = fg.rgb + bg.rgb * (1.0 - fg.a)`), allowing transparent and additive foreground visuals to float naturally over dynamic background visuals.
  - **Dedicated Preview Deck (`Deck PV`)**: Renamed `Deck C` to `Deck PV` for visual monitoring and preparation without affecting master output.
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
  - **Deck C Isolation**: Manual interactions with Deck C (master overlay) remain completely independent of the A/B Auto-VJ pipeline.
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
