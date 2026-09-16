# Liquid LSD — Master Project Roadmap

This document serves as the single source of truth for completed, active, and upcoming engineering milestones for **Liquid LSD Desktop**.

---

## High-Level Vision & Path to v1.0

Liquid LSD is a real-time, audio-reactive procedural visual synthesizer and VJ performance workstation. The roadmap to v1.0 centers on **performance, interoperability, tactile control, and stage reliability**:

1. **Foundational Synthesis & Graphics**: Real-time OpenGL 3.3 FBO pipeline, dual decks + background/preview decks, ping-pong feedback, and modular ISF post-processing.
2. **Inter-App Sharing & Ingest**: Seamless zero-copy GPU video streaming (Spout, Syphon, PipeWire) and DAW network clock sync (Ableton Link).
3. **Tactile Hardware Control**: Full control parity across MIDI controllers, keyboard shortcuts, CV modulators, and GUI.
4. **Ergonomic Live Performance**: Low-cognitive-load UI, continuous generative morphing, and rapid preset discovery.

---

## Current Status & Summary Matrix

| Milestone / Workstream | Target Area | Status | Key Deliverables |
| :--- | :--- | :---: | :--- |
| **Dear ImGui 1.92 Upgrade** | `ui/*`, `build.gradle.kts` | **COMPLETE** | Migrated to `imgui-java` 1.92.7.1, new key input API, 64-bit texture handles. |
| **Tooltips & LFO Ergonomics** | `ui/Lfo*`, `TooltipHelper` | **COMPLETE** | Multi-tier tooltips, logarithmic LFO time parser, and Min/Max modulation ranges. |
| **Continuous Random Morphing** | `Mixer.kt`, `Deck.kt`, `ui/*` | **COMPLETE** | Two-state interpolation ($S_0 \leftrightarrow S_1$), ping-pong flip-flop boundary latch, LFO/CV driving. |
| **Modern Window Experience (CSD)** | `MenuBar.kt`, `Main.kt` | **COMPLETE** | Unified 1.5x top bar, frameless window drag, window controls (`_ ◻ ✕`), and live telemetry HUD. |
| **Video Recording & Export** | `export/*`, `VideoExportModal` | **COMPLETE** | High-performance asynchronous GPU-to-CPU PBO readback pipeline (`PboReadbackPipeline`). |
| **Preset Tags & Search** | `browser/*`, `PresetModels` | **COMPLETE** | Preset tags in JSON, inline tag editor in browser context menu, tag search in Library. |
| **TouchOSC & Open Sound Control** | `osc/*`, `ui/*` | **PENDING** | Native UDP OSC 1.0 engine, TouchOSC layout mapping, XY pads, OSC Learn, bidirectional feedback. |
| **100% ISF Pipeline Migration** | `rendering/*`, `shaders/*`, `isf/*` | **COMPLETE** | Migrate feedback, 2D-to-3D, & mixer to ISF; deprecate and remove legacy hard-wired shaders. |
| **Unified Control & Mapping** | `midi/*`, `shortcuts/*`, `ui/*` | **PENDING** | Decoupled `CommandRegistry`, hardware controller profiles (`library/mappings/`), universal learn. |
| **Session Scratchpad** | `notes/*`, `ui/*` | **PENDING** | Standalone floating/docked notes scratchpad window (`~/.liquid-lsd/scratchpad.txt`). |
| **Mandala v2+ Recipe Vault** | `sources/mandala/*`, `ui/*` | **PENDING** | Visual recipe gallery popover with micro-previews, geometric style tagging, quick-slots. |
| **Macro Controls & Parameter Linking** | `ui/*`, `parameters/*`, `models/*` | **PROPOSED** | 8 Knobs + 4 Switches, 1-to-many bindings, modulating modulators, Column 3 `[MIXER\|MACROS]` mode, Learn mode UX. |
| **Modular Video Rack** | `ui/*`, `rendering/*`, `presets/*` | **PENDING** | 19" modular bay, curated faceplates, embedded confidence monitors, macros, Tab-flip rear patching. |
| **Build for ARM64 Linux** | `build.gradle.kts`, `ci` | **PLANNED** | Compile `imgui-java` via GitHub Actions ARM64 runner, integrate natives, restore Linux ARM64 distribution target. |

---

## Active & Upcoming Milestones

### Milestone 1: TouchOSC & Open Sound Control (OSC)
> **Reference**: TouchOSC Modular Control Specification  
> **Status**: Planned / Next Up  
> **Compatibility**: TouchOSC (iOS/Android/Desktop) control surfaces (faders, rotaries, XY pads, toggles, pushes). *Explicit non-goal: Resolume clip-launcher / composition hierarchy.*

Enable wireless and wired control from mobile devices and tablets running TouchOSC without third-party bridges:

- [ ] **Pure Kotlin Zero-Dependency OSC 1.0 Codec (`OscCodec`)**:
  - High-performance binary encoder and decoder for OSC messages and bundles.
  - Full support for OSC types: 32-bit floats (`f`), integers (`i`), strings (`s`), booleans (`T`/`F`), and multi-argument vectors (XY pads).
  - 4-byte boundary padding and big-endian network byte order handling without external jar dependencies.
- [ ] **Low-Latency UDP Engine & Bidirectional Feedback (`OscEngine`)**:
  - Dedicated background UDP receiver socket (default incoming port `8000`).
  - Thread-safe, lock-free queue passing incoming OSC events to the render thread.
  - Real-time packet sniffer circular buffer for live monitoring in Preferences.
  - Outgoing UDP feedback socket (default outgoing port `9000`) transmitting parameter updates, toggle states, and text labels back to TouchOSC clients to keep tablet displays in sync.
  - Auto-learning of remote TouchOSC client IP address from incoming packets.
- [ ] **TouchOSC Mapping Manager (`OscMappingManager`)**:
  - Out-of-the-box support for classic TouchOSC default layouts (`/1/fader1`–`/1/fader5`, `/1/rotary1`–`/1/rotary4`, `/1/toggle1`–`/1/toggle4`, `/1/push1`–`/1/push4`, `/2/xy`).
  - Direct semantic address fallback (`/mixer/crossfade`, `/deck_a/randomize`, `/clock/bpm`, `/param/...`).
  - XY pad 2-float packet unpacking to dual parameters (e.g. Pan X & Pan Y, Zoom & Rotate Z).
  - Control shaping: Min/Max numerical clamping, Invert, exponential Slew smoothing ($0 \dots 250$ ms), and Soft Takeover (pickup).
  - JSON mapping profile persistence in `library/osc/*.json`.
- [ ] **Interactive OSC Learn & Preferences UI**:
  - Dedicated **OSC Controls** tab in Preferences with server IP/port telemetry, client connection controls, live packet sniffer, and editable mappings table.
  - Contextual "Learn OSC" button in Properties and Preferences for instant one-touch control binding.

---

### Milestone 2: 100% ISF Pipeline Migration — Deprecating Hard-Wired FX & Mixer
> **Target Areas**: `Renderer.kt`, `Deck.kt`, `Mixer.kt`, `shaders/`, `library/filters/`, `library/transitions/`  
> **Status**: COMPLETE  
> **Objective**: Make all legacy hard-wired FX, 2D-to-3D projection shaders, and hard-coded mixer blend modes redundant by porting them completely to the open Interactive Shader Format (ISF), then removing the legacy code paths while preserving Deck BG compositing.

- [x] **Port Legacy Feedback Loop (`feedback.frag`) to Native Modular ISF**:
  - Re-architect the monolithic `feedback.frag` pass into clean, modular ISF effect(s) with multi-pass persistent history buffers.
  - Encapsulate feedback decay, gain, zoom (`uFbZoom`), rotation, hue shift, directional/radial blur, chromatic aberration, and kaleidoscopic folding as standard ISF inputs.
  - Run feedback through the modular deck FX chain instead of a rigid hardwired render pass in `Renderer.kt`.
- [x] **Convert 2D-to-3D Projection Methods to Modular ISF Shaders**:
  - Port `tri_planar.vert`/`frag` (Tri-Planar, Cube Cage, Hex-Planar instanced planes) and `tetra_kaleido.vert`/`frag` (Tetrahedral 24-chamber Coxeter space folding) to standard ISF shaders.
  - Route them through **Slot 2: Spatial / Distortion**, eliminating the custom hard-wired 3D branches and intermediate `rawSourceFBO` in `Renderer.kt`.
- [x] **100% ISF Mixer Transitions (Deck A $\leftrightarrow$ Deck B)**:
  - Eliminate hardcoded blend modes (`ADD`, `SCREEN`, `MULT`, `MAX`, `XFADE`) in `mixer.frag`.
  - All transitions between Deck A and Deck B become pure ISF transitions (`ISFAssetType.TRANSITION`) taking `startImage` (Deck A), `endImage` (Deck B), and `progress` ($0.0 \dots 1.0$).
  - Standard crossfade and blend modes ship as bundled, high-performance ISF transition shaders (`linear_crossfade.fs`, `additive_blend.fs`, `screen_blend.fs`, `multiply_blend.fs`, `max_blend.fs`, alongside wipes, glitch, and morphs).
- [x] **Preserve Deck BG Layering**:
  - Maintain the architectural compositing model where **Deck BG is rendered behind Decks A and B**:
    $$\text{Master Output} = \text{Composite}(\text{Deck BG}, \text{ISF\_Transition}(\text{Deck A}, \text{Deck B}, \text{progress}))$$
  - Streamline the final master compositing pass to cleanly blend Deck BG behind the active A/B transition output with bloom, levels, and master alpha.
- [x] **Deprecation & Removal of Legacy Hard-Wired Code**:
  - Remove `feedback.frag`, `feedbackShader`, `tri_planar.*`, `tetra_kaleido.*`, and legacy fallback blend code from `mixer.frag`.
  - Remove obsolete FBO allocations (`rawSourceFBO`, legacy ping-pong buffers in `Deck.kt`).
  - Clean up `Renderer.renderDeck` and `Renderer.renderMixer` into unified, lightweight ISF execution pipelines.

---

### Milestone 3: Unified Control Mapping & Hardware Profiles
> **Reference**: [`docs/developer/unified_control_mapping.md`](docs/developer/unified_control_mapping.md)  
> **Status**: In Progress / High Priority

While the multi-type MIDI subsystem (Notes, CC, Pitch Bend, Soft Takeover, Relative Rotary Encoders) and centralized `ShortcutManager` are operational, the goal is full control parity across all input modalities (inspired by Mixxx):

- [x] Multi-type MIDI event capture and lock-free atomic buffers (`MidiEngine`).
- [x] Soft takeover (pickup) and relative rotary encoder decoding (Binary Offset, Signed Bit, Two's Complement).
- [x] Centralized `ShortcutManager` with interactive key rebinding and collision detection.
- [x] Dedicated "MIDI Controls" tab in Preferences with real-time packet monitor and profile manager.
- [ ] **Universal Action / Command Registry (`CommandRegistry`)**:
  - Decouple all user actions (menu actions, deck operations, mixer transitions, parameter tweaks) into registered `Command` instances.
  - Expose consistent interfaces for Trigger, Continuous/Scalar, Stepped/Relative, and Latched Toggle inputs.
- [ ] **Hardware Controller Preset Profiles (`library/mappings/`)**:
  - Out-of-the-box controller mappings (e.g. Novation Launchpad, Akai APC40, Pioneer DDJ, Midi Fighter).
  - JSON profile schema for community controller sharing.
- [ ] **Universal Right-Click "Learn" Overlay**:
  - Context menu on any UI slider, button, or toggle to trigger MIDI Learn, key shortcut assignment, or CV binding.

---

### Milestone 4: Session Scratchpad & Live Notes
> **Reference**: Notes System & Companion Scratchpad Plan  
> **Status**: Pending / Medium Priority

Performers need persistent, glanceable set notes during live shows without relying on physical sticky notes on their monitors:

- [x] Preset Tags metadata in `.lsdpatch` JSON DTOs (`DeckPresetDto.tags`).
- [x] Library search filtering by preset tags (`Search presets & tags...`).
- [x] Preset tag editing via browser context menu (`Rename / Edit Tags...`) and Save Preset modal.
- [x] 3-tier hierarchical notes storage: Source notes, Preset notes, Parameter notes (`NotesManager`).
- [ ] **Dedicated Session Scratchpad Window**:
  - Lightweight, togglable floating or docked ImGui window ("Notes" / "Scratchpad").
  - Unstructured multi-line text buffer automatically persisted to `~/.liquid-lsd/scratchpad.txt`.
  - Accessible via global shortcut (`Ctrl+N` / `Cmd+N`) and MenuBar entry (`View > Scratchpad`).

---

### Milestone 5: Mandala Visual Generator v2+
> **Reference**: [`docs/developer/mandala_future_roadmap.md`](docs/developer/mandala_future_roadmap.md)  
> **Status**: Backlog / Future Enhancement

Enhancing the built-in Mandala procedural visual generator for live stage recall:

- [ ] **Visual Recipe Vault / Gallery Popover**:
  - Grid modal showing pre-rendered snapshots or live micro-previews of all ~300 built-in recipes.
  - Category tabs (`All`, `3 Lobes`, `4 Lobes`, `5 Lobes`, `6 Lobes`, `8+ Lobes`).
- [ ] **Geometric Tagging & Humanized Naming**:
  - Descriptive names and style tags (e.g., `#7: Floral Weave`, `Crystalline`, `Starburst`) replacing raw math vectors (`[26, 23, 14, 14]`).
- [ ] **Global Recipe Sweep Index**:
  - Sweep parameter moving continuously through all 300 recipes across all lobe counts via a single LFO or MIDI slider.
- [ ] **Favorites & Performance Quick-Slots**:
  - Quick-recall bookmark buttons (`[ Fav 1 ] [ Fav 2 ] [ Fav 3 ] [ Fav 4 ]`) on the Deck panel for instant switching.

---

### Milestone 6: Modular Video Rack & Macro Performance System
> **Reference & Design Specs**:
> - [`docs/developer/macro_controls_and_parameter_linking_proposal.md`](docs/developer/macro_controls_and_parameter_linking_proposal.md) (Macro Controls & Parameter Linking System)
> - [`docs/developer/modular_video_rack_proposal.md`](docs/developer/modular_video_rack_proposal.md) (Modular Video Rack Architecture)
> **Status**: Concept / Long-Term Architecture RFC  
> **Inspiration**: Hardware 19" studio racks, Propellerhead Reason, Eurorack, Ableton Device Racks

Evolving Liquid LSD from a fixed 2-deck mixer into a modular hardware-style video rack designed for tactile live performance:

- **Concept & Architecture**:
  - **Preset-as-Module**: Each visual generator, post-processing FX block, or transition is housed within an interchangeable rack unit with a standardized 19" bay width and quantized modular height ($1\text{U}, 2\text{U}, 3\text{U}, \dots$).
  - **Curated Performance Faceplates (80/20 Rule)**: Performers curate custom front panels exposing *only* high-impact live controls (knobs, sliders, toggles, and macros). Underlying automation, LFOs, audio-reactive envelopes, and fine math run silently in the background without cluttering the performance surface.
  - **Integrated Confidence Monitoring**: Every rack unit features an embedded real-time preview monitor rendering an offscreen FBO preview of that unit's output before downstream routing.
  - **Macro Controls**: Assignable multi-target macro knobs modulating multiple internal parameters simultaneously with customizable travel limits, inverted directions, and nonlinear response curves (linear, exponential, S-curve).
  - **Dual-Faced Architecture (Reason-Style `Tab` Flip)**:
    - *Front Face*: Clean tactile controls, meters, and micro-monitors.
    - *Rear Chassis*: Pressing `Tab` flips the entire rack around 180° to expose patch jacks (`Video In`, `Video Out`, `Mask / Sidechain In`, `CV Modulation In`).
  - **Normalled Signal Flow with Cable Overrides**:
    - By default, dropping units into a vertical stack normalizes connections top-to-bottom automatically without requiring manual patching.
    - Dragging virtual patch cables overrides the default flow for complex split-routing, parallel processing, external video I/O routing (Spout/Syphon/PipeWire), or intentional optical feedback loops.

- **Implementation Milestones** (single unified roadmap across both reference docs — the Macro system is a shared engine, not a rack-specific one; full detail in each doc's own Phase list):
  - [ ] **Phase 1: Data Model & MacroEngine** *(macro doc §7)*: Core `MacroBinding`/`MacroControl`/`MacroBank`, instance-scoped bindings, zero-allocation frame evaluation.
  - [ ] **Phase 2: Column 3 Macro UI Panel** *(macro doc §7)*: `[ MIXER | MACROS ]` toggle, 2x4 Knob grid + 4 Switches, single-deck preview.
  - [ ] **Phase 3: Interactive Learn Mode & Inspector** *(macro doc §7)*: Global click-to-bind UX and binding inspector drawer.
  - [ ] **Phase 4: Serialization & MIDI/OSC Integration** *(macro doc §7)*: Bundled `.lsd`/`.lsdset` DTOs, standalone `.knobpreset.json` export/import, `MidiMappingManager`/`OscEngine` linkage.
  - [ ] **Phase 5: Rack Chassis & Slot Layout System** *(rack doc §4)*: Standardized rack bay container, grid-based faceplate layout, unit header rails (power, bypass, solo, drag handle).
  - [ ] **Phase 6: Per-Unit Macro Curation** *(rack doc §4)*: Each unit gets its own `MacroBank` (0-8 knobs/0-4 switches) scoped via `unitInstanceId`; curation UI picks which unit parameters occupy which slot. No freeform faceplate designer yet.
  - [ ] **Phase 7: Embedded Confidence Micro-Monitors** *(rack doc §4)*: Lightweight texture blits rendering offscreen FBO passes directly onto unit faceplates.
  - [ ] **Phase 8: Rear Panel & Virtual Patch Cables (`Tab` Flip)** *(rack doc §4)*: 3D or 2.5D flipped rear chassis view with physics-curved virtual patch cables and port normaling.
  - **Backlog / not yet scheduled**: Full freeform Faceplate Designer, Playlist/Setlist staging strategy, transition topology, GPU FBO pooling, hardware focus-follow mapping — see rack doc §3 Open Questions.

---

### Milestone 7: Build for ARM64 Linux
> **Reference**: [Build for ARM64 Linux](docs/developer/build_arm64_linux.md)  
> **Status**: Planned / Platform Target Restoration  
> **Objective**: Restore native Linux ARM64 (`aarch64`) desktop support by compiling missing native JNI binaries (`imgui-java`) on GitHub Actions native ARM runners, integrating them via runtime loader hooks, and restoring `linux-arm64` distribution ZIP packaging.

- [ ] **Compile `libimgui-java64.so` for aarch64 on GitHub Actions**:
  - Run build workflow on GitHub's free native `ubuntu-24.04-arm` runners.
  - Compile Dear ImGui C++ sources and package native ELF shared library.
- [ ] **Runtime Dynamic Loader Integration (`NativeLibraryLoader`)**:
  - Place `libimgui-java64.so` in `src/main/resources/natives/linux-arm64/`.
  - Extract and configure `System.setProperty("imgui.library.path", ...)` before ImGui initialization on Linux ARM64.
- [ ] **Optional: Native Ableton Link (`link_jni`) Build**:
  - Compile `liblink_jni.so` for Linux ARM64 (or rely on automatic Carabiner TCP fallback).
- [ ] **Re-enable Packaging & CI**:
  - Restore `zipLinuxArm` task and Adoptium `linux-aarch64` JRE in `build.gradle.kts`.
  - Re-enable `linux-arm64` smoke test in GitHub Actions CI workflow.

---

## Completed Milestones Archive

### Phase 1: Core Engine & Dual Deck Architecture
- [x] GLFW windowing and OpenGL 3.3 core context creation on Linux, macOS, and Windows.
- [x] Low-latency FBO architecture with ping-pong feedback loops (`cleanFBO`, `rawSource2DFBO`, `masterFBO`).
- [x] Dual deck rendering pipeline (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`) and mixer compositing (`Mixer.kt`).
- [x] Real-time JACK Audio Connection Kit and PipeWire-JACK client with zero-allocation audio callbacks.
- [x] Audio analysis DSP: Onset detection, spectral flux, frequency bands (bass/mid/high), RMS amplitude, and `CVRegistry`.
- [x] Hierarchical modulation system: `ModulatableParameter`, LFOs, Sample & Hold, and BeatClock.

### Phase 2: Suite C UI & Modern Layout
- [x] Modern 3-column workspace architecture: **Parameters**, **Properties**, **Mixer**, and collapsible **Library** drawer.
- [x] Synchronized 1.5x panel headers and title bars (`PanelTitleBar.kt`).
- [x] Embedded Properties tab row (`Value`, `MIDI`, `LFO 1`, `LFO 2`, `SEQ`, `Audio`) with overlaid live oscilloscope canvas.
- [x] Dynamic auto-scaling timebases for oscilloscope with zero-allocation label caching.
- [x] Full transition from legacy "Settings" to standardized **Preferences** (`AppPreferences`, `lsd-preferences.properties`, `Ctrl+P`).

### Phase 3: Universal Shader Ecosystem & ISF Standard
- [x] Full **Interactive Shader Format (ISF 2.0)** specification parser and GLSL preprocessor (`ISFParser`).
- [x] Multi-format shader compatibility bridge: Automatic ingestion of **ISF**, **Shadertoy** (`mainImage`), and **GLSLSandbox** without manual modification.
- [x] Dual modular FX slots per deck: **Slot 1: Color / Degradation** and **Slot 2: Spatial / Distortion**.
- [x] ISF transition engine in Mixer with bundled transitions (`wipe`, `glitch`, `radial`, `luma_wipe`, `zoom_fade`).
- [x] Multi-pass generator and filter FBO rendering with persistent history buffers and imported texture assets.
- [x] Real-time audio FFT magnitude and waveform texture (`audioFFT` / `iChannel0` 512x2 `GL_R32F`).

### Phase 4: Stage Interoperability & Video Sharing
- [x] Zero-copy GPU texture sharing outputs: **Spout2** (Windows), **Syphon** (macOS), and **PipeWire DMA-BUF** (Linux).
- [x] Independent video output streams for Deck A, Deck B, Deck BG, Deck PV, and Master Composite.
- [x] First-class external video stream ingest as native visual sources (cameras, OBS, media servers).
- [x] Direct live video feed selection in `ShaderPickerPopup` with live activity indicators (`Icons.ACTIVITY`).
- [x] Ableton Link integration for network-wide tempo, beat phase, and quantum synchronization across local DAWs.

### Phase 5: Generative Morphing & Performance Controls
- [x] Continuous Constrained Random Morphing ($0.0 \leftrightarrow 1.0$) with ping-pong flip-flop boundary re-rolling.
- [x] Coordinate-space 2D transformations (Zoom and Rotate Z in generator space without edge cropping).
- [x] High-performance asynchronous PBO framebuffer readback pipeline (`PboReadbackPipeline.kt`) and Video Export dialog.
- [x] Integrated telemetry HUD in top bar displaying FPS, frame time, DSP latency, CPU%, and beat phase dots.
- [x] Dear ImGui modernization to 1.92.7.1.

---

## Retired / Resolved Workstream Items

| Item | Original Source | Resolution Details |
| :--- | :--- | :--- |
| **OpenGL Error Profiling** | Historical `TODO.md` (`glGetDebugMessageLog`) | Implemented via `GLDebug.setupDebugCallback()` leveraging native `glDebugMessageCallback` on modern contexts; verified on OpenGL 3.3/4.3+. |
| **Frame Budget & Latency Metrics** | Historical `CONCERNS.md` | Implemented via `PerformanceStats` and rendered continuously in `MenuBar.kt` (DSP latency, FPS, frame time, CPU usage). |
| **Screen Capture Automation RFC** | `screen_capture_and_ui_iteration_proposal.md` | Core GPU readback completed via `PboReadbackPipeline`; headless CLI screenshot automation deferred to build infrastructure as needed. |
