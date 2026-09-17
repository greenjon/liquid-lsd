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

| Milestone / Workstream | Target Area | Target | Status | Key Deliverables |
| :--- | :--- | :---: | :---: | :--- |
| **Modular Video Rack** | `ui/*`, `rendering/*`, `presets/*` | **v1.0** | **CORE COMPLETE** | 19" modular bay, curated faceplates, embedded confidence monitors, macros, Tab-flip rear patching. Backlog/open questions remain. |
| **Automated Screen Capture & UI Lab** | `export/*`, `ui/*`, `Main.kt` | **v1.0** | **IN PROGRESS** | Headless CLI screenshot automation (`--screenshot-ui`) for CI/docs & isolated UI Lab gallery (`--ui-lab`). |
| **Unified Control & Mapping** | `midi/*`, `shortcuts/*`, `ui/*` | **v1.1** | **PENDING** | Decoupled `CommandRegistry`, hardware controller profiles (`library/mappings/`), universal learn. |
| **Session Scratchpad** | `notes/*`, `ui/*` | **v1.1** | **PENDING** | Standalone floating/docked notes scratchpad window (`~/.liquid-lsd/scratchpad.txt`). |
| **Mandala v2+ Recipe Vault** | `sources/mandala/*`, `ui/*` | **v1.1** | **PENDING** | Visual recipe gallery popover with micro-previews, geometric style tagging, quick-slots. |

---

## v1.0 Active Milestones

### Milestone 1: Modular Video Rack & Macro Performance System
> **Reference & Design Specs**:
> - [`docs/user_guide/macros_and_rack.md`](docs/user_guide/macros_and_rack.md) (Macro Controls & Modular Rack User Guide)
> - [`docs/developer/modular_video_rack_proposal.md`](docs/developer/modular_video_rack_proposal.md) (Modular Video Rack Architecture)
> **Status**: Core Complete (Phases 1-9 shipped) — all 6 rack-doc Open Questions decided and implemented 2026-09-16
> **Inspiration**: Hardware 19" studio racks, Propellerhead Reason, Eurorack, Ableton Device Racks

Evolving Liquid LSD from a fixed 2-deck mixer into a modular hardware-style video rack designed for tactile live performance:

- **Concept & Architecture**:
  - **Preset-as-Module**: Each deck (generator + all its FX, flattened into one unit — see rack doc §2.7) or transition is housed within an interchangeable rack unit with a standardized 19" bay width and quantized modular height ($1\text{U}, 2\text{U}, 3\text{U}, \dots$).
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
  - [x] **Phase 1: Data Model & MacroEngine** *(macro doc §7)*: Core `MacroBinding`/`MacroControl`/`MacroBank`, instance-scoped bindings, zero-allocation frame evaluation.
  - [x] **Phase 2: Column 3 Macro UI Panel** *(macro doc §7)*: `[ MIXER | MACROS ]` toggle, 2x4 Knob grid + 4 Switches, single-deck preview.
  - [x] **Phase 3: Interactive Learn Mode & Inspector** *(macro doc §7)*: Global click-to-bind UX, pulsing highlights, field-ownership locking, and binding inspector drawer.
  - [x] **Phase 4: Serialization & MIDI/OSC Integration** *(macro doc §7)*: Bundled `.lsd`/`.lsdset` DTOs, standalone `.knobpreset.json` export/import, `MidiMappingManager`/`MacroOscBridge` linkage.
  - [x] **Phase 5: Rack Chassis & Slot Layout System** *(rack doc §4)*: Standardized rack bay container, grid-based faceplate layout, unit header rails (power, bypass, solo, drag handle).
  - [x] **Phase 6: Per-Unit Macro Curation** *(rack doc §4)*: Each unit gets its own `MacroBank` (0-8 knobs/0-4 switches) scoped via `unitInstanceId`; curation UI picks which unit parameters occupy which slot. No freeform faceplate designer yet.
  - [x] **Phase 7: Embedded Confidence Micro-Monitors** *(rack doc §4)*: Lightweight texture blits rendering offscreen FBO passes directly onto unit faceplates.
  - [x] **Phase 8: Rear Panel & Virtual Patch Cables (`Tab` Flip)** *(rack doc §4)*: Dual-faced 180° flipped rear chassis view with physics-curved virtual patch cables, 1/4" hex phone jacks, LED status indicators, drag-to-patch interactive routing, and normalled override engine.
  - [x] **Phase 9: Unit Consolidation & Rack Layout Finalization** *(rack doc §4, implements all 6 decided Open Questions in rack doc §3)*: Merged `DeckGeneratorUnit` + up to 4 `ISFProcessorUnit`s into one new `DeckRackUnit` per deck with a flattened generator+FX parameter namespace (rack doc §2.7); deleted `FeedbackProcessorUnit`, `DeckGeneratorUnit`, and `ISFProcessorUnit` entirely (zero remaining construction sites); added a Deck BG column (`mixer.deckBG`, no new Deck plumbing needed); built the 3U `QueueStagingRackUnit` (Play Queue / BG Queue / Transition Staging) as a condensed-transport view onto the existing `PlayQueueManager`/`BgQueueManager`/`TransitionQueueManager` singletons; kept `MixerTransitionUnit` as its own Master unit; implemented confidence-monitor downscaling (240x135 preview `FBO` per unit via the existing `Renderer.rescale()`) and an FBO-count/GPU-memory telemetry readout in the menu bar. Off-screen GL culling was evaluated and deliberately not built (near-zero payoff given today's units are all cheap texture-ID reads, not real draw calls). Implemented 2026-09-16 — see rack doc §4 for full detail, including a double-update bug found and fixed along the way (Rack-mode `update()` was double-ticking `Deck`/`Mixer` state, since `Main.kt`'s main loop already ticks them unconditionally every frame regardless of workspace mode).
  - **Deferred, not scheduled**: true patchable transitions/splitters (rack doc Q2 Models B/C) and a freeform Faceplate Designer both depend on restructuring `Deck`/`Mixer` to accept externally patched textures — the same underlying capability as the patch-cable known issue below. Bundle these together into a future dedicated milestone once that foundational work is deliberately undertaken.
  - **Deferred to before v1.0 ships**: extend the generalized macro-learn "click to bind" affordance (landing on number boxes via `CustomRangeSlider`/`BeatDivisionSlider`) to dropdown/select controls (`ImGui.combo()`). Dropdowns have no shared wrapper component anywhere in the codebase today — every call site (`Lfo1Section`, `Lfo2Section`, `ValueParamSection`, `RackUnitMacroCuration`, etc.) owns its own inline `ImGui.combo` — so this needs a small shared combo wrapper built first. Do this once the number-box version has shipped and proven the interaction pattern.
  - **Known issues** (surfaced by a 2026-09-15 post-implementation correctness review of Phases 5-8; full rationale in `DECISIONS.md`):
    - ~~`FeedbackProcessorUnit`'s curated macro knobs (GAIN/DECAY/ZOOM/HUE) are bound to `Deck.fbGain`/`fbDecay`/etc. — legacy fields left over from before the ISF feedback migration that no shader reads anymore~~ — resolved by Phase 9: the unit type was deleted entirely rather than rewired; feedback already appears correctly as a normal FX-slot parameter in the merged `DeckRackUnit`'s flattened namespace.
    - Virtual patch-cable overrides only reroute pixels for genuinely custom/utility rack units. For the built-in Generator/Processor/Transition units that wrap the existing fixed `Deck`/`Mixer` pipeline, cables render and jacks light up but don't change actual signal routing — making that real requires restructuring `Deck`'s fixed FX-slot chain and `Mixer`'s hardcoded Deck A/B inputs to accept externally patched textures, which touches the master output path every workspace mode relies on. Tracked as the same deferred work as rack doc Q2 Models B/C above — not addressed by Phase 9.
    - ~~`MacroOscBridge` (Phase 4) has no OSC transport to connect to yet~~ — resolved: `OscMappingManager` now forwards `/macro/knob/N` and `/macro/switch/N` straight to `MacroOscBridge.handleOscMessage()`, and registers a `MacroFeedbackListener` that broadcasts value changes back out through `OscEngine`.
    - Minor hardening left undone: `MacroBank` shape isn't validated/normalized on deserialization (a hand-edited `.knobpreset.json` with the wrong knob/switch count won't crash today, but isn't guarded either), and `MidiMappingManager`'s `Macro/knob_N`/`Macro/switch_N` CC dispatch still scans the full mapping table per incoming MIDI event instead of using the pre-resolved flat-array pattern the rest of that file uses (bounded by MIDI event rate, not frame rate, so not urgent). Both remain low-priority fix-opportunistically items.
    - The Phase 9 monitor-downscaling change (a GL viewport-changing blit inside the per-unit faceplate draw call) was not visually verified on screen — confirmed via live app launches that the render loop runs cleanly with no new errors, but the actual rack monitors weren't screenshotted (Wayland session, no reachable screenshot tooling in that pass). Worth a manual look next time the app is run interactively.

---

### Milestone 2: Automated Screen Capture & UI Lab
> **Reference**: [`docs/developer/screen_capture_and_ui_iteration_proposal.md`](docs/developer/screen_capture_and_ui_iteration_proposal.md)
> **Status**: Active / In Progress (Core GPU PBO readback complete; CLI screenshot automation pending)

Automating crisp, deterministic UI screenshot capture for documentation assets and providing an isolated sandbox for UI layout regression testing:

- [x] **Asynchronous GPU Readback**: High-performance PBO framebuffer readback pipeline (`PboReadbackPipeline.kt`) and Video Export dialog.
- [ ] **Headless CLI Screenshot Runner**: Parsing `--screenshot-ui=<file>`, `--window=<1920x1080>`, and `--screenshot-after-frames=<N>` arguments in `Main.kt` for automated CI/documentation image generation.
- [ ] **Isolated UI Lab Gallery Sandbox**: A lightweight `--ui-lab` startup mode providing an isolated sandbox environment to preview themes, custom icons, sliders, meters, and modal popups without requiring active audio hardware or heavyweight GLSL shader compilation.

---

## v1.1 Backlog

> These milestones are parked for consideration after v1.0 ships. Scope, priority, and whether each makes the cut will be decided once the v1.0 scope is locked.

### Milestone 1: Unified Control Mapping & Hardware Profiles
> **Reference**: [`docs/developer/unified_control_mapping.md`](docs/developer/unified_control_mapping.md)
> **Status**: Pending / High Priority

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

### Milestone 2: Session Scratchpad & Live Notes
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

### Milestone 3: Mandala Visual Generator v2+
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
- [x] Modular FX slots per deck (originally 2 — Slot 1: Color/Degradation, Slot 2: Spatial/Distortion — since expanded to 4, `Deck.FX_SLOT_COUNT`).
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

### Phase 6: Advanced Feature Milestones
- [x] **TouchOSC & Open Sound Control**: Native UDP OSC 1.0 engine (`OscCodec`, `OscEngine`), TouchOSC layout mapping, XY pads, OSC Learn, bidirectional feedback. `OscMappingManager` with JSON profile persistence under `library/osc/`. Dedicated OSC Controls tab in Preferences. `/macro/knob/N` and `/macro/switch/N` forwarded to `MacroOscBridge` with outbound feedback.
- [x] **100% ISF Pipeline Migration**: Feedback loop, 2D-to-3D projection methods, and all mixer blend modes fully ported to ISF. Legacy hard-wired shaders (`feedback.frag`, `tri_planar.*`, `tetra_kaleido.*`) retired from the rendering pipeline; `mixer.frag` legacy blend branches bypassed (`uMode = -1` in `Renderer.kt`). Bundled ISF transitions in `src/main/resources/default_transitions/`; `library/transitions/` is the user-scannable drop folder.
- [x] **Preset Tags & Search**: Preset tags in `.lsdpatch` JSON (`DeckPresetDto.tags`), inline tag editor in browser context menu and Save Preset modal, tag search filtering in Library (`PresetListPanel`).
- [x] **Tooltips & LFO Ergonomics**: Multi-tier tooltips (`TooltipHelper`), logarithmic LFO time parser (`TimeUtils.parsePeriod`), and Min/Max modulation ranges on `CustomRangeSlider`.
- [x] **Modern Window Experience (CSD)**: Unified 1.5x top bar, frameless window drag region (`MenuBar`), window controls (minimize / maximize / close), and live telemetry HUD.
- [x] **Macro Controls & Parameter Linking**: 8 Knobs + 4 Switches (`MacroBank` / `MacroControl` / `MacroBinding`), 1-to-many bindings, modulating modulators, Column 3 `[MIXER|MACROS]` mode, Learn mode UX; `MidiMappingManager` Macro CC dispatch and `MacroOscBridge` OSC linkage.
- [x] **Build for ARM64 Linux**: Prebuilt `libimgui-java64.so` ARM64 native sourced from [`imgui-java-natives-linux-arm64`](https://github.com/greenjon/imgui-java-natives-linux-arm64) on GitHub free ARM runners; runtime dynamic loader hook (`NativeLibraryLoader.prepareImGuiNatives()`); JRE 17 `linux-aarch64` integration; `zipLinuxArm` packaging task; 5-platform CI smoke matrix restored and verified passing.

---

## Retired / Resolved Workstream Items

| Item | Original Source | Resolution Details |
| :--- | :--- | :--- |
| **OpenGL Error Profiling** | Historical `TODO.md` (`glGetDebugMessageLog`) | Implemented via `GLDebug.setupDebugCallback()` leveraging native `glDebugMessageCallback` on modern contexts; verified on OpenGL 3.3/4.3+. |
| **Frame Budget & Latency Metrics** | Historical `CONCERNS.md` | Implemented via `PerformanceStats` and rendered continuously in `MenuBar.kt` (DSP latency, FPS, frame time, CPU usage). |
| **Screen Capture Automation RFC** | `screen_capture_and_ui_iteration_proposal.md` | Core GPU readback completed via `PboReadbackPipeline`; headless CLI screenshot automation deferred to build infrastructure as needed. |
