# Proposal: Modular Video Rack Architecture

**Status**: Draft / RFC  
**Target Area**: `ui/`, `rendering/`, `parameters/`, `presets/`, `io/`  
**Authors**: GJ & Antigravity  

---

## 1. Executive Summary & Vision

Liquid LSD currently organizes visual generation into fixed decks (Deck A, Deck B, Deck PV) and a master mixer. While powerful, this structure enforces a fixed mental model and exposes long lists of uniform sliders during live performance.

This proposal outlines a **Modular Video Rack** paradigm inspired by hardware racks (19-inch audio racks, Reason, Eurorack, and Ableton Device Racks), reimagined specifically for **live visual synthesis and VJing**:

* **Preset-as-Module**: Each preset or generator/effect is housed inside a customizable rack unit with a standardized width and variable modular height (1U, 2U, 3U, etc.).
* **Curated Performance Faceplates**: The performer designs a custom front panel for each preset, exposing *only* high-impact controls (knobs, sliders, toggles, and multi-parameter macro knobs) while leaving underlying automation and complex modulation running silently in the background.
* **Integrated Confidence Monitoring**: Every unit features an embedded real-time preview monitor.
* **Dual-Faced Architecture (Reason-Style)**: The front panel provides a clean, tactile live performance surface. Pressing `Tab` flips the rack around to expose the rear panel with patch jacks (Video In, Video Out, Mask In, CV In) and virtual patch cables.
* **Normalled Signal Flow**: By default, signals cascade top-to-bottom without requiring manual cabling, preserving zero-friction simplicity for standard chains.

---

## 2. Completed Decisions & Architectural Principles

### 2.1 The Modular Faceplate Form Factor
* **Standardized Width, Modular Height**: Units conform to a standard width (representing a 19" rack bay) with height quantized to standard modular units ($1\text{U}, 2\text{U}, 3\text{U}, \dots$) or continuous heights.
* **Grid-Based Slot Layout**: Rather than error-prone freeform pixel coordinates, faceplates use a column/row slot grid (e.g., 6 or 8 columns wide). Controls (knobs, faders, meters, monitors) snap into place, ensuring a cohesive, professional aesthetic.
* **Unit Classification**:
  1. **Generator Units (Synths)**: No video input. Generate visuals from shader logic, math, and audio CV $\to$ Video Out.
  2. **Processor Units (FX)**: Accept Video In $\to$ apply feedback, distortion, color grading, displacement $\to$ Video Out.
  3. **Transition / Mixer Units**: Accept multiple Video Inputs $\to$ crossfade / blend $\to$ Master Out.
  4. **Utility Units**: Splitters, clock conductors, and external I/O bridges.

### 2.2 Curated Controls vs. Background Automation (The 80/20 Rule)
* **Performance Surface Curation**: Only performance-critical parameters are given physical knobs or buttons on the faceplate.
* **Hardcoded / Autonomous Background Modulation**: Parameters can have active LFOs, audio-reactive envelopes, or fixed offsets that run continuously without cluttering the UI. These are configured in an "under-the-hood" inspector or edit mode, leaving the live faceplate clean and focused.

### 2.3 Macro Knobs as Expressive Instruments
* A macro knob allows a single control on the faceplate to modulate multiple internal parameters simultaneously.
* For the complete standalone macro specification, see [`docs/developer/macro_controls_and_parameter_linking_proposal.md`](macro_controls_and_parameter_linking_proposal.md).
* Each target parameter binding includes:
  * Minimum and Maximum travel bounds.
  * Curve profile (Linear, Exponential, Logarithmic, S-Curve).
  * Direction inversion (e.g., as Macro turns up, Zoom increases while Feedback Gain decreases).

### 2.4 Confidence Monitoring on Every Unit
* Each rack module includes an integrated mini-monitor rendering an offscreen FBO preview of that unit's immediate state.
* Gives the VJ immediate visual feedback before mixing or routing the signal downstream.

### 2.5 I/O & External Video Streaming
* Each unit can expose assignable input and output ports:
  * Internal GPU texture passes.
  * External low-latency zero-copy bridges: Spout 2 (Windows), Syphon (macOS), and PipeWire (Linux).
* Allows any rack unit to ingest an external camera or feed from Resolume/TouchDesigner, process it through Liquid LSD shaders, and transmit it back out.

### 2.6 Normalled Top-Down Signal Flow with Rear Patching (`Tab` Flip)
* **Default State (Normalled Top-to-Bottom)**: Dropping units into a vertical stack automatically wires Unit $N$ Output into Unit $N+1$ Input.
* **Rear Panel (`Tab` Flip)**:
  * Pressing `Tab` spins the rack 180° to display the rear chassis.
  * Rear jacks include: `Video In`, `Video Out`, `Mask / Sidechain In`, and `CV Modulation In`.
  * Dragging a patch cable overrides the normalled connection, enabling split routing, external I/O routing, and intentional video feedback loops.

---

## 3. Open Questions & Decisions Requiring Deep Thought

```
┌────────────────────────────────────────────────────────────────────────────┐
│                             MODULAR VIDEO RACK                             │
├─────────────────────────────────────┬──────────────────────────────────────┤
│ COMPLETED DECISIONS                 │ OPEN ARCHITECTURAL QUESTIONS         │
│ • Standard 19" width (1U/2U/3U)     │ 1. Playlist & Setlist Management     │
│ • Curated UI vs background mod      │ 2. Transition & Multi-Deck Topologies│
│ • Multi-destination Macro knobs     │ 3. Faceplate Designer & Schema       │
│ • Unit mini-monitors (FBO preview)  │ 4. GPU FBO Pooling & Smart Culling   │
│ • Normalled top-down signal flow    │ 5. Hardware MIDI/OSC Controller Map  │
│ • Tab flip for rear patch cables    │ 6. Migration & Backward Compatibility│
└─────────────────────────────────────┴──────────────────────────────────────┘
```

### Question 1: How should Playlists and Cueing work without excessive vertical clutter?

If a show involves 30 presets, stacking 30 units vertically—with 29 bypassed—creates excessive vertical scrolling and high cognitive load.

#### Evaluated Approaches:
* **Option 1A: Accordion Spines (Collapsible 0.5U Blanking Panels)**  
  * The active preset expands to full $1\text{U}$–$3\text{U}$ controls.
  * The "On-Deck" (Next) preset sits below in a compact $1\text{U}$ state with a preview pip and load button.
  * All remaining queued presets collapse into ultra-thin $\approx 20\text{px}$ rack spines (showing only index, title, and BPM).
  * *Advantage*: Preserves the physical rack metaphor while keeping vertical space minimal.
* **Option 1B: Fixed Chassis with Cartridge / Hot-Swap Bays (500-Series Style)**  
  * The rack has fixed functional slots (e.g. Generator 1, FX 1, FX 2, Master).
  * The Generator unit contains a "Cartridge Loader" header that cycles presets from a playlist.
  * Advancing the playlist hot-swaps or morphs the faceplate and internal shader state in-place.
  * *Advantage*: Zero vertical expansion; constant screen footprint.
* **Option 1C: Two-Deck Staging Caddy (DJ Cue Model)**  
  * Dedicated "Live" rack slot and "Staged / Next" rack slot alongside a collapsible Setlist drawer.
  * Crossfading automatically promotes the staged unit to live and pulls the next item from the queue into the staged slot.
  * *Advantage*: Foolproof live operation; matches existing DJ/VJ instincts.
* **Option 1D: Master "Conductor" Sequencer Unit**  
  * A 1U rack unit containing a horizontal filmstrip timeline of upcoming presets.
  * Supports automatic beat-synced triggers (e.g. advance every 32 bars) or manual advance with customizable morph/dissolve durations.

---

### Question 2: Transition Topologies (Serial vs. Dual-Bay vs. Patchable)

How should crossfading and transitions between two different visual streams be structured?

* **Model A: Dual-Bay Racks (Deck A Rack + Deck B Rack + Master Mixer Strip)**  
  * Matches Liquid LSD's existing underlying dual-deck rendering engine.
  * Column A houses Generator A + FX; Column B houses Generator B + FX.
  * A center or bottom mixer module handles crossfading, wipe patterns, and master post-processing.
* **Model B: In-Rack Transition Units (2-Input Module)**  
  * A 2U transition module placed anywhere in a single stack.
  * `Input A` is normalled from above; `Input B` is patched from another unit or external Spout feed.
  * Contains a large crossfader, blend modes (Additive, Screen, Luma Key, Glitch Wipe), and curve shaping.
* **Model C: Splitter / Branch Units (Parallel Chains)**  
  * A unit splits one video stream into two parallel sub-chains (e.g., clean vs. heavy feedback) and recombines them with a wet/dry crossfader before sending downstream.

---

### Question 3: Faceplate Designer UX & File Format Schema

How does the user construct their custom faceplate, and how is it stored?

* **Editing Experience**:
  * Should there be a dedicated "Edit Mode" toggle (like an unlock/wrench icon on the rack ears)?
  * When unlocked: click to add widgets, drag to rearrange grid slots, right-click to bind parameters/macros.
  * When locked: clean performance mode with no edit handles or accidental drags.
* **Widget Palette**:
  * Vintage rotary pot / knob (continuous and stepped).
  * Vertical and horizontal sliders.
  * Momentary strobe buttons and latched toggle switches.
  * XY touch pads.
  * Mini-monitor / oscilloscope display.
* **Storage Schema**:
  * How should the faceplate be persisted inside the `.lsd` JSON preset file?
  * Example proposed schema:
    ```json
    {
      "presetName": "Cyber Gyroid",
      "generator": "gyroid_core",
      "faceplate": {
        "heightU": 2,
        "gridColumns": 6,
        "widgets": [
          { "type": "macro_knob", "id": "warp", "label": "WARP", "slot": [0, 0], "targets": [
            { "param": "dimensionWarp", "min": 0.0, "max": 2.5, "curve": "exponential" },
            { "param": "feedbackZoom", "min": 1.0, "max": 0.85, "curve": "linear" }
          ]},
          { "type": "slider", "label": "COLOR DECAY", "param": "decayRate", "slot": [1, 0] },
          { "type": "monitor", "slot": [4, 0], "colSpan": 2, "rowSpan": 2 }
        ]
      }
    }
    ```

---

### Question 4: GPU Resource Management & FBO Pipeline

Running $N$ simultaneous rack modules introduces GPU and framebuffer overhead.

* **FBO Pool Architecture**:
  * Avoid allocating dedicated $4\text{K}$ ping-pong FBOs for every inactive or bypassed unit.
  * Implement a pooled intermediate FBO allocator that recycles buffers as frames flow down the chain.
* **Smart Culling / Bypass**:
  * If a unit's output is not routed to a visible monitor or active output, pause its GL draw calls entirely.
  * If a unit is bypassed, zero shader passes should be executed (passthrough texture pointer handoff).
* **Resolution Scaling**:
  * Mini-monitors on the faceplate should render downscaled mipmaps or a shared preview resolution ($240 \times 135$) to eliminate full-res overhead during editing.

---

### Question 5: Hardware Control Mapping (MIDI & OSC)

How do physical hardware controllers (e.g., an 8-knob controller like a MIDI Fighter Twister or Akai Midimix) interact with a dynamic rack?

* **Follow-Focus / Active Unit Mapping**:
  * Knobs $1\dots8$ automatically map to whichever rack unit currently has focus.
  * Clicking a unit (or using arrow keys) shifts the hardware control focus instantly.
* **Fixed Global Mapping**:
  * Specific knobs/faders can be locked to global elements (e.g. Master Crossfader, Master Strobe) regardless of which unit is selected.
* **Soft Takeover Integration**:
  * Leverage Liquid LSD's existing `MidiEngine` soft-takeover and rotary encoder relative modes to prevent value jumps when switching focus between units.

---

### Question 6: Migration & Coexistence with Existing Architecture

* How does the Modular Video Rack relate to Liquid LSD's current standard interface (Deck A, Deck B, Mixer, Parameters Panel)?
  * **Option A: Dedicated "Rack Mode" View**: A toggleable view workspace (e.g., `F4` or tab bar) alongside Classic Deck View.
  * **Option B: Next-Generation Core Paradigm**: The Modular Rack replaces the fixed Decks A/B over time as the primary way users interact with Liquid LSD.
* **Auto-Generating Faceplates for Legacy Presets**:
  * When loading an existing `.lsd` preset that lacks a `faceplate` block, the system automatically synthesizes a clean default 1U or 2U faceplate populated with the preset's primary modulators and a mini-monitor.

---

## 4. Next Steps & Recommended Milestones

1. **Phase 1: Macro Parameter Engine**:
   * Implement `MacroParameter` in `src/.../parameters/` capable of driving multi-target modulation with min/max bounds and non-linear response curves.
2. **Phase 2: Faceplate Widget Layout Prototype**:
   * Build the slot-grid layout container and basic skeuomorphic widgets (knob, slider, button, monitor) in Dear ImGui.
3. **Phase 3: Rack Unit Container & Normalled Routing**:
   * Implement the vertical rack container with top-down texture passing and bypass toggles.
4. **Phase 4: Playlist / Setlist Staging Strategy Selection**:
   * Prototype Accordion Spines (1A) vs. Two-Deck Caddy (1C) to evaluate live ergonomics under performance conditions.
5. **Phase 5: The Flip-to-Back Patching System (`Tab`)**:
   * Implement the rear chassis rendering and bezier patch cord interaction for advanced routing and feedback loops.
