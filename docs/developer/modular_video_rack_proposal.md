# Proposal: Modular Video Rack Architecture

**Status**: Core Complete (Phases 5-9 implemented — see §4). All 6 Open Questions in §3 decided (2026-09-16); **Phase 9** (implementing those decisions) shipped the same day (2026-09-16) — see §4 for what landed and what was deliberately deferred.  
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
* For the complete data model (`MacroControl`, `MacroBinding`, curve types, evaluation pipeline) and the instance-scoping mechanism, see [`docs/developer/preset_management.md`](preset_management.md) §8 and [`docs/user_guide/macros_and_rack.md`](../user_guide/macros_and_rack.md).
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

### 2.7 Unit Granularity: One Rack Unit Per Deck, Not Per Pipeline Stage

**Decision (2026-09-16)**: A preset's full chain — generator + all 4 FX slots (`Deck.FX_SLOT_COUNT = 4`; the "dual FX slots" phrasing in earlier roadmap text is stale, the engine has supported 4 since before this proposal) — is represented as **one rack unit per deck**, not one unit per pipeline stage.

* **Why not one unit per stage**: the initial Phase 5/6 implementation (`RackManager.populateFromSession`) creates one `DeckGeneratorUnit` plus one `ISFProcessorUnit` *per occupied FX slot* — up to 5 units for a single deck using all 4 FX slots. That's real clutter, and it doesn't buy real modularity today: patch cables between these units are cosmetic only (see the "Known issues" entry in `ROADMAP.md` Milestone 6 / `DECISIONS.md`), since the underlying render path is still one fixed `Deck.cleanFBO -> fxFBOs[0..3] -> output` chain, not an independently-repatchable graph. Splitting into per-stage units pays a UI-complexity cost without a matching capability gain.
* **Why merging doesn't cost macro-knob headroom**: `MacroBank` is a fixed-shape container (8 knobs / 4 switches) *per unit instance*, regardless of `heightU`. Splitting a deck into 5 units yields up to 40 knobs of raw capacity across 5 separate banks; merging into 1 unit caps it at 8. That tradeoff was considered and accepted deliberately — 8 knobs freely assignable across generator + all 4 FX is judged sufficient for v1 (see Question 2 below), and the option to grow `MacroBank` beyond 8 later remains open if that judgment turns out wrong in practice.
* **The merged unit's parameter namespace**: `getNamedParameters()` flattens the generator's own parameters together with each occupied FX slot's parameters, disambiguated with a slot prefix (e.g. `"FX1/dryWet"`, `"FX2/dryWet"`, `"dimensionWarp"` for the generator's own, unprefixed). Any of the unit's 8 knobs can be curated against *any* entry in that combined map — including one knob driving both a generator parameter and an FX parameter simultaneously, since `MacroControl.bindings` already supports up to `MAX_BINDINGS_PER_CONTROL = 4` independent target bindings per knob. **No `MacroEngine`/`MacroBinding` changes are required for this** — `RackUnitMacroCuration.kt`'s existing curation drawer already works unit-locally off `getNamedParameters()`, so it needs no changes either; only the flattened parameter map on the merged unit type is new.
* **Fallout — `FeedbackProcessorUnit` is removed, not fixed**: per the existing known issue, this unit's knobs are bound to legacy `Deck.fbGain`/`fbDecay`/etc. fields no shader reads anymore. Feedback already appears correctly as a normal FX-slot filter (`default_filters/feedback.fs`) once merged into the deck's flattened parameter namespace, so the standalone unit is now fully redundant rather than fixable-in-place. It is deleted, not rewired.
* **Rear-panel implication**: the merged unit exposes one `Video In`/`Video Out` pair (like today's `PROCESSOR` ports) rather than separate jacks per internal stage. A pre-FX "send" tap (output before the internal FX chain) is a reasonable future rear-panel jack on this unit, but depends on the same real-signal-routing work as Question 2's Models B/C — not scoped for the initial merge.

---

## 3. Open Questions & Decisions Requiring Deep Thought

> All 6 questions below were **decided on 2026-09-16** (see the "Decision" block under each). The section is kept in its original open-question form — evaluated options included — as a record of the reasoning; nothing here is still open. Implementation is tracked as **Phase 9** in §4.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                             MODULAR VIDEO RACK                             │
├─────────────────────────────────────┬──────────────────────────────────────┤
│ COMPLETED DECISIONS (§2)            │ DECIDED QUESTIONS (§3, 2026-09-16)   │
│ • Standard 19" width (1U/2U/3U)     │ 1. Playlist & Setlist -> 3U Queue    │
│ • Curated UI vs background mod      │    Master unit                       │
│ • Multi-destination Macro knobs     │ 2. Transition -> 3-column A/B/BG +   │
│ • Unit mini-monitors (FBO preview)  │    dedicated Master unit             │
│ • Normalled top-down signal flow    │ 3. Faceplate Designer -> deferred,   │
│ • Tab flip for rear patch cables    │    fixed template stays              │
│ • One unit per deck, not per stage  │ 4. GPU/FBO -> cull+downscale now,    │
│   (§2.7)                            │    pooling only if measured need     │
│                                      │ 5. Hardware mapping -> Fixed Global  │
│                                      │    only, no Follow-Focus             │
│                                      │ 6. Migration -> coexist permanently, │
│                                      │    no auto-generated faceplates      │
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

**Decision (2026-09-16)**: None of 1A-1D as originally scoped is sufficient on its own — the actual requirement is that Rack mode give the performer the *same* queue control they already have in Classic mode (`PlayQueueManager`, the background queue, and preset/transition staging), not a rack-native reinvention of it. A single-preset "cartridge loader" (1B) or a lone conductor timeline (1D) only covers one of those three existing queues.

* **Chosen shape**: a dedicated **3U "Queue & Staging" master unit**, always present in the rack (not per-deck), with three stacked 1U sections:
  * **1U — Play Queue**: mirrors the Classic-mode Deck A/B play queue (current, on-deck, upcoming), with advance/promote controls.
  * **1U — Background Queue**: mirrors the Classic-mode BG queue, independent advance/promote.
  * **1U — Transition Staging**: exposes whatever the chosen Question 2 topology needs staged before it goes live (e.g. next preset armed for the Master unit's crossfader).
* This reuses the existing `PlayQueueManager`/BG queue engines directly (no new queue data model) — it's a rack-native *view* onto state that already exists and is already fully controllable from Classic mode, per the coexistence decision in Question 6.
* Options 1A/1C's "collapse the rest of a 30-preset show into thin spines" idea is worth revisiting as a *display density* improvement inside this unit's Play Queue section later, but isn't required for v1 and shouldn't block it.

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

**Decision (2026-09-16)**: **Model A**, extended to three columns and paired with the §2.7 unit-merge decision.

* **Three columns, one merged unit each**: **Deck A**, **Deck B**, and **Deck BG** each get exactly one rack unit (generator + its up to 4 FX slots, flattened per §2.7) — not a column of stacked generator/FX units. This is the direct reason the §2.7 merge decision exists: without it, "3 columns" would mean up to 15 stacked units (5 per deck × 3 decks) instead of 3.
* **One dedicated Master unit**, separate from the three deck columns, owning the crossfader, blend mode, BG compositing, and master post-mix FX (bloom, master alpha) — i.e. `MixerTransitionUnit`'s current scope, kept as its own unit rather than folded into a deck column, since it isn't "owned" by any single deck.
* **Deck PV is intentionally excluded** from this v1 layout — it's an audition/preview deck in Classic mode, not part of the live composite, and doesn't obviously map onto a rack column. Left as a follow-up question if a concrete use case shows up (e.g. a "preview" micro-monitor on the Master unit instead of its own column).
* **Models B and C (true patchable transitions/splitters) are deferred**, not rejected — they require the same real-signal-routing capability as the pre-FX send tap from §2.7 and the "patch cables actually reroute pixels" known issue. Once that foundational work happens, Model B becomes a natural *additional* transition unit type alongside the fixed Master unit, not a replacement for it.

---

### Question 3: Faceplate Designer UX & File Format Schema

How does the user construct their custom faceplate, and how is it stored?

**Decision (2026-09-16, reaffirmed)**: no freeform widget-placement designer for now — confirmed after weighing it against the §2.7 unit-merge decision, since a merged deck unit's flattened generator+FX parameter namespace makes the existing fixed 8-knob curation drawer (unmodified) sufficient rather than a reason to need more UI. A unit's macro surface is curated, not designed — the performer picks which of the unit's parameters occupy which of its (up to 8) knob slots and (up to 4) switch slots and in what order, reusing the same fixed 2×4 knob grid / 4-switch row as the Column 3 global bank (see §2.3 and the macro proposal §6). Plain (non-macro) widgets — a slider or button bound 1:1 to a single parameter, a mini-monitor — still need *some* placement mechanism; the simplest v1 answer is a fixed template per unit type (e.g. macro grid on top, monitor below) rather than user-arranged slots. The remaining ideas below (freeform grid editing, full widget palette) stay in the backlog as the **Full Faceplate Designer**, gated behind actual demand once people are hitting the fixed-template's limits in practice — not built speculatively.

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

**Decision (2026-09-16)**: don't front-load full FBO pooling. With the §2.7 merge, the base rack is now only ~4-5 units (3 deck columns + Master + Queue unit) instead of up to 16, which substantially reduces how urgent this is. Do now, cheaply, with no design risk:
* **Smart Culling**: skip GL draw calls entirely for bypassed or off-screen units — a correctness/perf win regardless of scale.
* **Confidence-monitor downscaling**: render mini-monitors at the shared $240 \times 135$ preview resolution rather than full-res.
* **Instrument first**: add an FBO-count / GPU-memory readout to the existing telemetry HUD (`PerformanceStats`, see `ARCHITECTURE.md`). Only build the pooled allocator if real sessions with many units actually show it's a bottleneck — not speculatively.

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

**Decision (2026-09-16)**: **Fixed Global Mapping only** for v1 — no Follow-Focus/Active Unit Mapping. Each rack unit already has its own independently learnable `MacroBank` (per §2.7, one per deck column plus Master and the Queue unit), so per-unit MIDI CC and OSC address mapping already works today via the existing Learn flows (`MidiMappingManager`, and now `OscMappingManager`/`OscLearnState` per the TouchOSC & OSC milestone) — no new plumbing required. Follow-Focus is rejected as a v1 default: a hardware knob silently remapping to a different parameter when UI focus changes is a real live-performance risk (a knob jump mid-set is worse than a knob that's simply not mapped), and it would require new cross-rack "focus" state that doesn't exist. Revisit only if fixed per-unit mapping proves limiting after real use.

---

### Question 6: Migration & Coexistence with Existing Architecture

* How does the Modular Video Rack relate to Liquid LSD's current standard interface (Deck A, Deck B, Mixer, Parameters Panel)?
  * **Option A: Dedicated "Rack Mode" View**: A toggleable view workspace (e.g., `F4` or tab bar) alongside Classic Deck View.
  * **Option B: Next-Generation Core Paradigm**: The Modular Rack replaces the fixed Decks A/B over time as the primary way users interact with Liquid LSD.
* **Auto-Generating Faceplates for Legacy Presets**:
  * When loading an existing `.lsd` preset that lacks a `faceplate` block, the system automatically synthesizes a clean default 1U or 2U faceplate populated with the preset's primary modulators and a mini-monitor.

**Decision (2026-09-16)**: **Option A, permanently** — not a migration path, a permanent coexistence. Classic Deck View already shipped as the default alongside Rack mode via the `F4` `WorkspaceMode` toggle (`UITheme.WorkspaceMode.CLASSIC`/`RACK`); this decision formally rejects Option B. A single screen with access to every variable *and* full VJ ability is a capability Liquid LSD keeps, not a stepping stone to be replaced.

**Auto-generating faceplates is explicitly rejected, not just deferred.** Curating a macro bank is a creative act — deciding which handful of parameters out of dozens deserve one of a unit's 8 precious knob slots, in what order, with what range and curve, is exactly the judgment a script can't make. Auto-generating a plausible-looking faceplate would fill racks with unconsidered, low-value units — worse than no rack support at all for that preset. Until a performer has consciously curated a faceplate for a preset, that preset simply isn't usable in Rack mode; Classic mode remains fully available for it in the meantime. This keeps every unit in a rack a deliberate creative choice.

---

## 4. Next Steps & Recommended Milestones

These phases continue directly from Phases 1-4 of the Macro System (Data Model & `MacroEngine`, Column 3 UI, Learn Mode, Serialization). This document does not define its own macro engine — see §2.3 and [`docs/user_guide/macros_and_rack.md`](../user_guide/macros_and_rack.md).

* **[x] Phase 5: Rack Chassis & Slot Layout System**: Standardized rack bay container, grid-based faceplate layout, unit header rails (power, bypass, solo, drag handle), and normalled top-down texture routing. No custom faceplate designer yet (see Question 3). [Implemented]
* **[x] Phase 6: Per-Unit Macro Curation**: Give each rack unit its own `MacroBank` scoped via `unitInstanceId` (macro proposal §6), and build the curation UI for picking which unit parameters occupy which of its knob/switch slots. Reuses the Column 3 engine and Learn Mode UX from Phases 1-3 verbatim — no new binding infrastructure. [Implemented]
* **[x] Phase 7: Embedded Confidence Micro-Monitors**: Lightweight texture blits rendering downscaled offscreen FBO passes directly onto unit faceplates. [Implemented]
* **[x] Phase 8: Rear Panel & Virtual Patch Cables (`Tab` Flip)**: Dual-faced flipped rear chassis view (`Tab` shortcut) with catenary-sagging virtual patch cables, 1/4" hex phone jacks, LED status indicators, drag-to-patch interactive routing, and normalled override engine. [Implemented]
* **[x] Phase 9: Unit Consolidation & Rack Layout Finalization** *(implements the §3 decisions above)* [Implemented 2026-09-16]:
  * Replaced `DeckGeneratorUnit` + up to four `ISFProcessorUnit`s per deck with **one new merged unit type per deck, `DeckRackUnit`** (§2.7), exposing a flattened `getNamedParameters()` map (generator params unprefixed, FX params prefixed `"FX1/…"`..`"FX4/…"`). `RackUnitMacroCuration.kt` needed no changes, confirming the §2.7 prediction. Gave it `GENERATOR`-type rear ports (`video_out`/`cv_in`, no `video_in`) since a deck column has no upstream unit in the 3-column layout.
  * Deleted `FeedbackProcessorUnit` entirely, and `DeckGeneratorUnit`/`ISFProcessorUnit` once `RackManager.populateFromSession` no longer constructed them (all three had zero remaining construction sites) — including their now-dead `RackFaceplateGrid.kt` faceplate-drawing branches.
  * Extended `RackManager.populateFromSession` to a third column for **Deck BG** (`mixer.deckBG`, already a first-class `Deck` — no new Deck plumbing needed), using `DeckRackUnit`. Deck PV excluded (§ Question 2). The "3 parallel columns vs. 1 serial pipeline" concern turned out to be a non-issue: every built-in unit's `process()` already ignores its `inputTexture` and reads its own pre-rendered Deck/Mixer state directly (per the Phase 5-8 correctness-pass doc comments), so the flat `units` list ordering doesn't corrupt anything — no column data structure was needed in `RackPipeline`.
  * Built the **3U Queue & Staging master unit** (`QueueStagingRackUnit`, § Question 1): 1U Play Queue / 1U BG Queue / 1U Transition Staging, each a condensed transport view (prev/play-pause/next + "now → next" text) wired directly to the existing `PlayQueueManager`/`BgQueueManager`/`TransitionQueueManager` singletons — no new queue data model, and deliberately *not* a re-implementation of the full drag-and-drop queue list UI (`QueueActionsPanel.kt` etc.), which doesn't fit a 1U row.
  * Kept `MixerTransitionUnit` as its own **Master unit**, separate from the three deck columns (§ Question 2) — unchanged.
  * **Smart culling**: bypass/power-off skip was already fully implemented pre-Phase-9 (`RackPipeline.process()`). Off-screen culling was evaluated and **deliberately not built** — every current unit's `process()` is a cheap texture-ID read, not a real GPU draw call, so it has near-zero payoff today and would need real architectural risk (the pipeline resolves all units in one pass before the UI loop knows scroll position) for no measurable gain. Revisit once a real patchable transition/splitter unit (§ Question 2 Models B/C, deferred) does real per-unit GPU work.
  * **Monitor downscaling**: implemented using the existing `Renderer.rescale()` utility (already used elsewhere for output scaling, not new shader code) rather than the render-pipeline work originally scoped. Each unit lazily gets a 240×135 preview `FBO`, cached in `RackMicroMonitor` and released via `releaseUnit`/`releaseAll` hooks in `RackPanel.kt` (on unit removal and rack re-sync) to avoid leaking one FBO per unit lifetime.
  * **Telemetry**: `FBO.kt` now tracks live instance count and estimated byte size (format-aware: RGBA8/16F/32F), exposed via `PerformanceStats.fboCount`/`fboMemoryMB` and shown in the menu bar as `FBO: N (XMB)` — instrumentation in place before any pooled allocator is considered, per the Question 4 decision.
  * No new hardware-mapping work needed (§ Question 5) — confirmed: MIDI/OSC Learn binds by opaque `MacroControl.id`, fully decoupled from parameter-name format, so it already works transparently with the new `"FX2/dryWet"`-style flattened keys.
  * No auto-faceplate-generation work (§ Question 6 — explicitly rejected, nothing built).
  * **Found and fixed during implementation**: `DeckGeneratorUnit`/`MixerTransitionUnit`'s (now `DeckRackUnit`/`MixerTransitionUnit`'s) `update()` overrides were double-ticking `Deck`/`Mixer` state (FX filter history, `BgQueueManager`'s dip-to-black timer) whenever Rack mode was visible, since `Main.kt`'s main loop already ticks them unconditionally every frame regardless of workspace mode. Both now inherit `BaseRackUnit`'s no-op `update()`.
  * **Not independently visually verified**: the monitor-downscaling change does a real GL viewport-changing blit inside the per-unit faceplate draw call. Confirmed via `jstack` across several live app launches that the render loop runs cleanly with no new errors, and confirmed by reading `Main.kt` that this codebase already relies on ImGui's own backend resetting the GL viewport before drawing (an existing code path already leaves a partial viewport active before the UI render phase) — but the actual on-screen rack monitors were not screenshotted (the test session turned out to be native Wayland, and available tooling couldn't reach it). Worth a manual look next time the app is run interactively.
