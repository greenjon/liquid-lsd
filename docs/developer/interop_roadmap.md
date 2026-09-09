# Inter-App Ecosystem & Interoperability Roadmap

This document outlines the strategic engineering roadmap for integrating **Liquid LSD** into the broader VJ, live visual, lighting, and music production ecosystem (Resolume, MadMapper, TouchDesigner, VDMX, Ableton Live, Bitwig, Reaper, etc.).

---

## High-Level Vision & Objectives

Liquid LSD excels at procedural, audio-reactive mathematical synthesis, parameter modulation matrices, and real-time live performance. To maximize its utility in professional production rigs and festival stages, Liquid LSD is evolving to seamlessly interface with third-party software across four key domains:

1. **Foundation & Ergonomics (Core Usability)**: Polish UI tooltip readability and modernize LFO parameter controls from abstract Depth/DC offset to intuitive Min/Max bounds.
2. **Stage Utility (Zero-Copy Video Sharing)**: Route live video feeds between Liquid LSD and stage media servers using GPU texture sharing standards (Spout on Windows, Syphon on macOS, with Linux DMA-BUF / PipeWire / SpoutLinux support).
3. **Open Content Library (Interactive Shader Format - ISF)**: Adopt the open ISF specification for generative sources, post-processing FX, and mixer crossfades, enabling cross-compatibility with thousands of existing shaders from the creative coding community.
4. **Musical Timing & Synchronization (Ableton Link)**: Provide sample-accurate peer-to-peer beat, phase, and tempo sync across local networks as an alternative to the built-in BTrack audio analyzer.
5. **External Video Ingest & FX Processing**: Accept external video inputs from cameras, media players, and companion software via Spout/Syphon, treating external video as a native visual source within Liquid LSD's modulation and FX chain.

---

## Phase 0: Foundation & Core Ergonomics

### Objective
Solidify foundational UI/UX readability and parameter ergonomics before layering multi-app sharing and expanded shader engines.

### Phase 0.1: Tooltip Formatting Clean-Up
- **Standardized Multi-Tier Hover Tooltips**:
  - Unify styling, padding, and text wrap across all UI tooltips (`itemTooltip`, `showTooltip`, `showCustomTooltip`).
  - Eliminate visual clutter, inconsistent heading sizes, raw identifier leaks, and orphaned markdown artifacts.
  - Consistent layout hierarchy:
    - **Header**: Parameter / feature title and type badge (`FLOAT`, `INT`, `COLOR`, `TRIGGER`).
    - **Value State**: Current base value, active modulated value, and operator equation.
    - **Description**: Concise human-readable explanation from `meta.json` or `NotesManager`.
    - **Hotkeys / Interactions**: Clear mouse/keyboard shortcut hints (e.g. `Right-Click to Reset`, `Ctrl+Click to Direct Edit`, `Shift+Drag for Fine Tuning`).

### Phase 0.2: LFO Modulation Ergonomics — Depth + DC to Min / Max Conversion
- **Human-Readable Bounds (Min / Max)**:
  - Currently, LFO modulators expose `Depth` (amplitude / span) and `DC Offset` (center point), which can be mathematically abstract and unintuitive during fast-paced live VJ sets.
  - Convert the LFO user-facing controls to explicit **Min Value** and **Max Value** range bounds (e.g. `Min: 0.20`, `Max: 0.85`).
  - Provide a dual-handled or coupled range slider for intuitive visual adjustment of modulation boundaries.
- **Mathematical Mapping & Backward Compatibility**:
  - Under the hood, retain bidirectional conversion to preserve existing preset DTOs and math routines:
    $$\text{Depth} = \frac{\text{Max} - \text{Min}}{2}, \quad \text{DC Offset} = \frac{\text{Max} + \text{Min}}{2}$$
    $$\text{Min} = \text{DC Offset} - \text{Depth}, \quad \text{Max} = \text{DC Offset} + \text{Depth}$$
  - Seamless preset migration: existing presets saved with `depth` and `offset` transparently map to `min` and `max` without breaking live setlists.

---

## Phase 1: Stage Utility — Spout & Syphon Video Outputs

### Objective
Enable Liquid LSD to act as a high-performance visual generator feeding external video mixers, projection mappers (MadMapper, Resolume Arena), or live streaming encoders (OBS Studio) without CPU frame readback bottlenecks.

### Architectural Overview
- **Zero-Copy GPU Texture Sharing**:
  - **macOS**: Syphon (`SyphonServer` via native Objective-C / Metal / OpenGL bridge).
  - **Windows**: Spout2 (`SpoutLibrary` / DirectX 11 ↔ OpenGL interop via JNA / native bindings).
  - **Linux**: Inter-app video sharing via PipeWire DMA-BUF video streams or SpoutLinux / GLX shared contexts.
- **Dedicated Output Endpoints**:
  Liquid LSD provides independent, concurrent virtual video outputs for:
  - `Deck A` (`LiquidLSD-DeckA`)
  - `Deck B` (`LiquidLSD-DeckB`)
  - `Deck BG` (`LiquidLSD-DeckBG`)
  - `Deck PV` (`LiquidLSD-DeckPV` - Preview deck)
  - `Master Composite` (`LiquidLSD-Master`)

### Settings & Configuration UI
- **Per-Output Routing Matrix in Settings**:
  - Individual toggle switches to enable/disable each output stream independently (reducing GPU overhead when only specific decks are needed).
  - Auto-naming convention (`LiquidLSD-DeckA`, `LiquidLSD-DeckB`, `LiquidLSD-DeckBG`, `LiquidLSD-DeckPV`, `LiquidLSD-Master`) with optional user prefix/suffix customization.
  - Per-output resolution & aspect ratio selector:
    - *Native Render Resolution* (matches current internal canvas, e.g. 1080p, 4K).
    - *Fixed Rescaling* (e.g. downscale to 720p for low-latency stage monitors, or fixed 1920x1080 / 1280x720 / 3840x2160 independent of main canvas).
    - Aspect ratio conform modes (Fit/Letterbox, Fill/Crop, Stretch).

### Technical Tasks & Implementation Milestones
### Technical Tasks & Implementation Milestones
- [x] Research and bundle native JVM bindings for Spout2 (Windows) and Syphon (macOS). [DONE]
- [x] Create `VideoSharingEngine` abstraction with platform-specific backends (`SpoutBackend`, `SyphonBackend`, `PipeWireBackend`, `NoOpBackend`). [DONE]
- [x] Hook into Deck and Mixer rendering pipelines: blit/share textures from `rawSource2DFBO` / `cleanFBO` / `masterFBO` immediately following render passes. [DONE]
- [x] Build Settings UI panel for Spout/Syphon output configuration with resolution dropdowns and live connection indicators. [DONE]
- [x] Document Spout/Syphon configuration and multi-app workflows in `docs/user_guide/` and `docs/developer/`. [DONE]

---

## Phase 2: Content Library & Pipeline Standardization — Interactive Shader Format (ISF)

### Objective
Adopt the **Interactive Shader Format (ISF)** standard created by VIDVOX. This replaces proprietary shader wrappers with a standardized JSON-in-GLSL specification, unlocking thousands of existing community shaders (from [editor.isf.video](https://editor.isf.video)) and allowing Liquid LSD's visual sources and FX to be exported and used in other VJ apps.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              ISF Ecosystem                              │
│       Thousands of community shaders & effects (editor.isf.video)       │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              ▼                      ▼                      ▼
        Phase 2.1                Phase 2.2              Phase 2.3
      Visual Sources           Dual FX Slots           Mixer Blends
     (Deck Generator)     [Slot 1: Color/Degrade]    (Crossfader FX)
                          [Slot 2: Spatial/Distort]
```

### Phase 2.1: ISF Parser & Visual Source Migration [COMPLETED]
- **ISF Specification Support**:
  - Parse ISF JSON header comments (`/*{ "DESCRIPTION": "...", "INPUTS": [ ... ] }*/`) embedded directly at the top of GLSL files.
  - Parse input types (`float`, `bool`, `color`, `point2D`, `long`/`event`) and automatically map them to Liquid LSD's `ModulatableParameter System`.
  - Support ISF standard uniforms: `RENDERSIZE` (`vec2`), `TIME` (`float`), `TIMEDELTA` (`float`), `FRAMEINDEX` (`int`), `DATE` (`vec4`).
- **Source Compatibility**:
  - Convert existing procedural sources (`Mandala`, `Gyroid`, `Chladni`, `Dynamic Spiral`, etc.) into ISF-compliant shader sources where feasible.
  - Maintain hot-reloading: drop `.fs` or `.isf` files into `library/sources/` for instant compilation and parameter binding.
  - Fallback and migration path for existing `meta.json` source bundles.

### Phase 2.2: FX System Conversion to ISF & Dual FX Slots [COMPLETED]
- **Modular Post-Processing Chain**:
  - Migrate the hardcoded feedback post-processing stage (`feedback.frag`) into a modular, chainable ISF effect processor.
  - Map ISF `image` inputs (e.g. `inputImage`) to Deck clean/feedback FBO textures.
  - Support multi-pass ISF shaders (`PASSES` array with custom buffer definitions and persistent history buffers).
  - Expose ISF FX parameter inputs directly within the Preset Grid for audio/LFO modulation.
- **Dual FX Architecture (Two Dedicated Slots per Deck)**:
  - Provide two serialized, independently modulatable FX slots in each deck processing chain:
    - **Slot 1: Color / Degradation** (Pixel & Chromatic Processing) [COMPLETED]
      - Focus: Color alteration, tonal remapping, keying, and signal degradation.
      - Examples: *Luma Key*, *Hue Cycle / Shift*, *Posterize*, *Invert*, *Color Grade / LUT*, *Threshold / Dither*.
    - **Slot 2: Spatial / Distortion** (Geometric & Feedback Processing) [COMPLETED]
      - Focus: Coordinate space distortion, temporal feedback, optics, and geometric dislocation.
      - Examples: *3D Elevation / Spatial Projection*, *Feedback Trails*, *Digital Glitch / Artifacting*, *Mirror / Kaleidoscope*, *Edge Warp / Barrel Distortion*, *Displacement Map*.
  - **Signal Chain & Routing**:
    - `Visual Source` $\rightarrow$ `[Slot 1: Color / Degradation]` $\rightarrow$ `[Slot 2: Spatial / Distortion]` $\rightarrow$ `Mixer / Output`.
    - Each slot features independent bypass toggles, wet/dry mix, preset loading, and parameter randomization hooks in the Preset Grid.

### Phase 2.3: Mixer Crossfading & Blending via ISF [COMPLETED]
- **Extensible Transition Engine**:
  - Optional ISF transition shaders taking two texture inputs (`startImage` / `Deck A`, `endImage` / `Deck B`) and a transition progress uniform (`progress` / crossfader position $0.0 \dots 1.0$).
  - Seamless fallback to current non-ISF `mixer.frag` blend modes (`ADD`, `SCREEN`, `MULT`, `MAX`, `XFADE`) when no transition is selected.
  - Reuses `ShaderPickerPopup` (`PickerType.MIXER_TRANSITION`) for search, category filtering, and detaching transitions.
  - Bundled transitions (`linear_crossfade`, `wipe_horizontal`, `wipe_vertical`, `radial_wipe`, `glitch_transition`, `luma_wipe`, `zoom_fade`) and support for user-installed transitions in `library/transitions/`.

### Technical Tasks & Implementation Milestones
- [x] Implement ISF JSON header parser and GLSL preprocessor (`ISFParser`). [DONE]
- [x] Support automatic mapping of ISF inputs to `ModulatableParameter`. [DONE]
- [x] Integrate ISF post-processing stage (Slot 1) into Deck pipeline. [DONE]
- [x] Build UI for ISF filter selection and parameter modulation in Preset Grid. [DONE]
- [x] Implement multi-pass ISF support with ping-pong buffers (Phase 2.2.2). [DONE]
- [x] Add second modular FX slot (Slot 2) for spatial/distortion effects (Phase 2.2.2). [DONE]
- [x] Port feedback loop to modular ISF effect (Phase 2.2.3). [DONE]
- [x] Build `ISFTransitionRegistry` and bundled transition shaders (Phase 2.3). [DONE]
- [x] Integrate ISF transition pass into `Mixer.kt` and `Renderer.kt` with fallback to non-ISF mixer. [DONE]
- [x] Extend `ShaderPickerPopup` and `MixerMonitorPanel` for transition selection. [DONE]
- [x] Support session state serialization (`transitionSlot`) and web client broadcast. [DONE]

---

## Phase 3: Musical Timing — Ableton Link Integration [COMPLETED]

### Objective
Provide synchronization with DAWs (Ableton Live, Bitwig, Traktor, Serato, Reaper) and other performance software across local Wi-Fi or Ethernet networks via **Ableton Link**, supplementing Liquid LSD's real-time audio FFT/beat tracker (`BeatTrackerEngine`).

### Architecture & Synchronization Model
- **Tri-State Timing Core (Hybrid Clock)**:
  - Selectable clock source in MenuBar, Audio Panel, and Settings:
    - `BTrack Audio`: Autonomous FFT onset detection and dynamic programming flywheel from live microphone/line input.
    - `Ableton Link`: Network-synchronized shared beat timeline, tempo, and quantum phase across local peers.
    - `Manual Fixed`: Internal flywheel running at fixed manual BPM with VJ tap tempo.
- **Multi-Backend Link Engine (`AbletonLinkEngine`)**:
  - Native JNI C++ bridge (`link_jni` embedding `ableton::Link`) supporting `linux-x64`, `windows-x64`, `macos-x64`, and `macos-arm64`.
  - Secondary Carabiner TCP socket backend (`CarabinerTcpLinkBackend`) for local daemon connectivity.
  - Automatic `NoOpLinkBackend` fallback when Link is inactive or uninstalled.
  - Bidirectional tempo and phase synchronization mapping into `CVRegistry`.
  - Quantum selection (1, 4, 8, 16 beats) and start/stop transport sync support.
- **UI & HUD Indicators**:
  - Top MenuBar status pill `LINK [N peers]` showing connected peers, quantum phase ring, active driver, and quick clock dropdown menu.
  - Dedicated Ableton Link controls card in `AudioEnginePanel`.
  - Seamless fallback: if Link session disconnects, clock seamlessly reverts to internal flywheel or audio tracking without audio/visual glitches.

---

## Phase 4: Video Processing — Spout & Syphon Input [COMPLETED]

### Objective
Enable Liquid LSD to ingest external live video streams (webcams, Blackmagic capture cards via OBS, Resolume layer outputs, TouchDesigner generative textures) and route them as native visual sources through Liquid LSD's 2D/3D geometry, feedback loops, and modulation FX.

### Visual Source Integration
- **Spout/Syphon as a Visual Source**:
  - `Spout/Syphon Input` appears as a selectable visual source in the Deck source selector dropdown alongside `Mandala`, `Gyroid`, etc.
  - When selected, a source picker dropdown in the Preset Grid allows selecting from currently discovered external servers (e.g., `Resolume Arena - Layer 1`, `OBS-Camera`, `TouchDesigner-Out`).
  - Discovery updates dynamically via `ExternalVideoDiscovery` when third-party servers launch or terminate.
- **Preset Persistence & Serialization**:
  - The external source selection is serialized in preset JSON (`sourceId: "spout_input"`, `serverName: "Resolume Arena - Layer 1"`).
  - Graceful fallback: if the saved server name is not found on preset load, displays empty / disconnected state cleanly without crashing the render pipeline.
- **Video Processing Pipeline**:
  - External video frames are consumed via `TextureReceiver` and bound to Liquid LSD's rendering pipeline (`renderExternalVideoSource` in `Renderer.kt`).
  - Active video textures (`currentTextureId`) blit into deck framebuffers (`rawSource2DFBO` / `rawSourceFBO`) with full compatibility across Liquid LSD's downstream stages:
    - 2D transforms: Zoom, Rotate Z, Pan.
    - 3D projections: Tri-Planar, Cube Cage, Hex-Planar, and Coxeter space folding.
    - Audio-reactive feedback loops (decay, chromatic aberration, blur, hue shifts).
    - ISF post-processing effects.
- **Platform Scope**:
  - Windows (Spout2 via `SpoutReceiverImpl`) and macOS (Syphon via `SyphonReceiverImpl`) are fully active.
  - Linux DMA-BUF / PipeWire ingest is postponed and stubbed to `NullTextureReceiver`.

---

## Summary Matrix

| Phase | Feature Area | Primary Technologies | Primary Benefit |
| :--- | :--- | :--- | :--- |
| **Phase 0.1** | **Tooltip Polish** | `TooltipHelper`, ImGui styling | Consistent hierarchy, visual clarity, and clean shortcut hints. |
| **Phase 0.2** | **LFO Min/Max Conversion** | `CvModulator`, range sliders, DTO mapping | Intuitive modulation bounds replacing abstract Depth/DC offset. |
| **Phase 1** | **Stage Utility (Spout/Syphon Output)** | Spout2 (Win), Syphon (macOS), PipeWire (Linux) | Multi-channel zero-copy video routing to Resolume, OBS, stage media servers. |
| **Phase 2.1** | **Content Library (ISF Sources)** | ISF Specification, JSON GLSL parser | Open ecosystem, access to 1000s of community visual generators. |
| **Phase 2.2** | **Dual FX Slots (ISF Effects)** | Multi-pass FBO pipeline, ISF image inputs | Two dedicated slots per deck: [Slot 1: Color/Degradation] and [Slot 2: Spatial/Distortion]. |
| **Phase 2.3** | **Mixer Transitions (ISF Crossfades)** | ISF 2-image transition shaders | Custom wipe, glitch, and morph crossfader transitions. |
| **Phase 3** | **Musical Timing (Ableton Link)** | Ableton Link C++/JNI bindings | Sample-accurate wireless/wired network beat & tempo sync with DAWs. |
| **Phase 4** | **Video Ingest (Spout/Syphon Input)** | Texture sharing consumer, Dynamic source binding | External camera, media player, and generative stream processing with Liquid LSD FX. |

