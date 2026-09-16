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
* Each rack unit owns its own local `MacroBank` (0-8 knobs, 0-4 switches) — the same data model and engine as the global Column 3 macro bank, just scoped to that unit's own parameters via a stable `unitInstanceId`. There is no separate, rack-specific macro system; this section only summarizes how that shared system applies to a unit's faceplate.
* For the complete data model (`MacroControl`, `MacroBinding`, curve types, evaluation pipeline) and the instance-scoping mechanism, see [`docs/developer/macro_controls_and_parameter_linking_proposal.md`](macro_controls_and_parameter_linking_proposal.md) §6.
* Each target parameter binding includes:
  * Minimum and Maximum travel bounds.
  * Curve profile (Linear, Exponential, Logarithmic, S-Curve, or Step).
  * Direction inversion (e.g., as Macro turns up, Zoom increases while Feedback Gain decreases).
* **v1 scope**: curating a unit's macro bank means choosing which of its parameters occupy which knob/switch slot and in what order — not designing an arbitrary widget layout. See Open Question 3 below.

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

**Decided for v1**: no freeform widget-placement designer yet. A unit's macro surface is curated, not designed — the performer picks which of the unit's parameters occupy which of its (up to 8) knob slots and (up to 4) switch slots and in what order, reusing the same fixed 2×4 knob grid / 4-switch row as the Column 3 global bank (see §2.3 and the macro proposal §6). This avoids building a full grid-snap widget editor before the underlying macro engine and per-unit scoping exist. Plain (non-macro) widgets — a slider or button bound 1:1 to a single parameter, a mini-monitor — still need *some* placement mechanism; the simplest v1 answer is a fixed template per unit type (e.g. macro grid on top, monitor below) rather than user-arranged slots. The remaining questions below (freeform grid editing, full widget palette) are pushed to the backlog as the **Full Faceplate Designer**.

* **v1 Storage Schema** (fixed macro slot curation + templated plain widgets):
    ```json
    {
      "presetName": "Cyber Gyroid",
      "generator": "gyroid_core",
      "faceplate": {
        "heightU": 2,
        "unitInstanceId": "a1b2c3d4",
        "macroBank": {
          "knobs": [
            { "label": "WARP", "bindings": [
              { "parameterId": "dimensionWarp", "minVal": 0.0, "maxVal": 2.5, "curve": "EXPONENTIAL" },
              { "parameterId": "feedbackZoom", "minVal": 1.0, "maxVal": 0.85, "curve": "LINEAR" }
            ]},
            null, null, null, null, null, null, null
          ],
          "switches": [null, null, null, null]
        },
        "plainControls": [
          { "type": "slider", "label": "COLOR DECAY", "param": "decayRate" }
        ]
      }
    }
    ```
  (`null` slots are simply unused knob/switch positions.)

* **Backlog — Full Faceplate Designer** (deferred, not part of v1):
  * A dedicated "Edit Mode" toggle (unlock/wrench icon on the rack ears): unlocked allows adding widgets and dragging to arbitrary grid slots; locked is clean performance mode.
  * Full widget palette beyond the macro grid: vintage rotary pots (continuous/stepped), vertical/horizontal sliders, XY touch pads, oscilloscope displays, arbitrarily placed via `slot: [col, row]` / `colSpan` / `rowSpan` grid coordinates.
  * Revisit only if the fixed-template v1 model proves too limiting in practice.

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

These phases continue directly from Phases 1-4 in [`docs/developer/macro_controls_and_parameter_linking_proposal.md`](macro_controls_and_parameter_linking_proposal.md) §7 (Data Model & `MacroEngine`, Column 3 UI, Learn Mode, Serialization). This document does not define its own macro engine — see §2.3 and §6 of that proposal for why.

* **[x] Phase 5: Rack Chassis & Slot Layout System**: Standardized rack bay container, grid-based faceplate layout, unit header rails (power, bypass, solo, drag handle), and normalled top-down texture routing. No custom faceplate designer yet (see Question 3). [Implemented]
* **[x] Phase 6: Per-Unit Macro Curation**: Give each rack unit its own `MacroBank` scoped via `unitInstanceId` (macro proposal §6), and build the curation UI for picking which unit parameters occupy which of its knob/switch slots. Reuses the Column 3 engine and Learn Mode UX from Phases 1-3 verbatim — no new binding infrastructure. [Implemented]
* **[x] Phase 7: Embedded Confidence Micro-Monitors**: Lightweight texture blits rendering downscaled offscreen FBO passes directly onto unit faceplates. [Implemented]
* **[x] Phase 8: Rear Panel & Virtual Patch Cables (`Tab` Flip)**: Dual-faced flipped rear chassis view (`Tab` shortcut) with catenary-sagging virtual patch cables, 1/4" hex phone jacks, LED status indicators, drag-to-patch interactive routing, and normalled override engine. [Implemented]

**Not yet scheduled** (open questions above still need a decision before these can be phased): Playlist/Setlist staging strategy (Question 1), transition topology (Question 2), the Full Faceplate Designer backlog item (Question 3), GPU FBO pooling (Question 4), and hardware focus-follow mapping (Question 5).
