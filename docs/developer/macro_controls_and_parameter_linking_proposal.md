# Proposal: Macro Controls & Parameter Linking System

**Status**: Draft / RFC  
**Target Area**: `ui/`, `parameters/`, `models/`, `presets/`, `midi/`, `osc/`  
**Authors**: GJ & Antigravity  

---

## 1. Executive Summary & Core Concept

As Liquid LSD grows in synthesis capability, visual presets often expose dozens of parameters spread across generators, color tuners, LFO modulators, and audio envelopes. Performing live requires immediate, tactile access to key expressive dimensions without navigating long lists of sliders.

This document outlines the design for the **Macro Controls & Parameter Linking System**—a foundation for both immediate live performance and the upcoming **Modular Video Rack Architecture**.

### Key Highlights:
* **Up to 8 Macro Knobs + 4 Macro Switches per Bank**: A fixed-shape physical and UI control surface for live performance — chosen to match common 8-knob/4-button hardware controllers, not a hard limit on the system's flexibility (see §6).
* **1-to-Many Binding Topology**: A single Macro Knob or Switch can drive up to 4 target parameters simultaneously with independent min/max travel limits, inversion, and response curves.
* **Modulating the Modulators**: Macro Knobs target not only parameter base values but also **modulator properties** (e.g. LFO speed/subdivision, LFO morph, envelope attack/decay, LFO depth).
* **Interactive "Learn Mode" UX**: Click "Learn" on any knob/switch, then click any parameter slider or modulator control in the UI to establish a binding instantly.
* **Column 3 Dual-Mode UI (`[ MIXER | MACROS ]`)**: Places the macro controls, binding inspector, and focused single-deck preview inside Column 3, leaving **Parameters** (Col 1) and **Properties** (Col 2) fully visible for seamless linking.
* **Hardware Parity**: Full integration with `MidiMappingManager` (MIDI CC) and `OscEngine` (OSC).
* **Instance-Scoped Bindings**: Bindings resolve against either the global session (today's decks) or a specific rack unit instance, so the same binding engine and UI serve both the Column 3 global bank and the per-unit banks introduced by the Modular Video Rack (see §6).
* **Preset Serialization**: Bundled into `.lsd` / `.lsdset` preset files, with an on-demand export/import to standalone `.knobpreset.json` for reuse across presets.

---

## 2. Workspace Layout & Screen Real Estate

To allow easy linking, performers need simultaneous visibility of:
1. **Parameters Panel (Column 1)**: All visual parameters for active decks.
2. **Properties Panel (Column 2)**: Fine-grained modulator controls (LFO 1, LFO 2, SEQ, Audio Envelopes).
3. **Macro Controls Panel (Column 3)**: The live macro knobs, switches, binding inspector, and preview monitor.

```
+------------------------+------------------------+---------------------------------+
|                        |                        |  COL 3: MACRO & PREVIEW         |
|                        |                        |  +---------------------------+  |
|                        |                        |  | 8 MACRO KNOBS (2x4 Grid)  |  |
|  COL 1: PARAMETERS     |  COL 2: PROPERTIES     |  | 4 MACRO SWITCHES          |  |
|  (All Deck Params)     |  (LFO/Env/Mod Settings)|  +---------------------------+  |
|                        |                        |  | BINDING & LEARN INSPECTOR |  |
|                        |                        |  +---------------------------+  |
|                        |                        |  | SINGLE DECK PREVIEW       |  |
+------------------------+------------------------+---------------------------------+
|                        BOTTOM: LIBRARY & PRESET BROWSER                           |
+-----------------------------------------------------------------------------------+
```

Column 3 includes a mode header toggle (`[ MIXER | MACROS ]`):
* **`MIXER` Mode**: Classic 4-deck crossfader layout (Deck A, B, C, D, Master).
* **`MACROS` Mode**: Focuses on single-deck macro performance with 8 Knobs, 4 Switches, binding inspector, and a live preview monitor at the bottom.

---

## 3. Engine Architecture & Data Model

### 3.1 Data Structures

A Macro Control set consists of 8 Knobs and 4 Switches. Each knob/switch holds a list of up to 4 bindings:

```kotlin
enum class MacroTargetType {
    PARAM_BASE_VALUE,      // Target ModulatableParameter.baseValue
    MODULATOR_PROPERTY     // Target CvModulator property (depth, subdivision, morph, slope, hold, etc.)
}

enum class MacroCurveType {
    LINEAR, EXPONENTIAL, LOGARITHMIC, S_CURVE, STEP
}

enum class SwitchBehavior {
    TOGGLE,     // Latch: click toggles between Min and Max
    MOMENTARY,  // Hold: Max while held, Min on release
    TRIGGER     // Impulse: Sends 1-frame pulse or one-shot trigger
}

data class MacroBinding(
    val unitInstanceId: String? = null, // null = global/session scope (today's decks); non-null = a Rack unit's stable id (see §6)
    val parameterId: String,            // e.g. "deckA_zoom" (global scope) or "dimensionWarp" (local to unitInstanceId)
    val targetType: MacroTargetType,
    val modulatorIndex: Int = 0,        // Index in parameter's modulator stack
    val propertyName: String = "",      // e.g. "subdivision", "morph", "depth", "attackMs"
    var minVal: Float = 0.0f,
    var maxVal: Float = 1.0f,
    var curve: MacroCurveType = MacroCurveType.LINEAR,
    var inverted: Boolean = false,
    var enabled: Boolean = true
)

data class MacroControl(
    val id: String,                    // e.g. "knob_1", "switch_1"
    var label: String,                 // Custom display name (e.g. "WARP", "STROBE")
    var value: Float = 0.0f,           // Normalized position [0.0..1.0]
    val isSwitch: Boolean = false,
    var switchBehavior: SwitchBehavior = SwitchBehavior.TOGGLE,
    val bindings: MutableList<MacroBinding> = mutableListOf() // Max 4 bindings
)

// A bank is the fixed-shape container for one macro surface: the global Column 3
// bank, or a single Rack unit's own local bank (see §6). Both use the same shape.
data class MacroBank(
    val knobs: List<MacroControl>,     // 0..8 Knobs
    val switches: List<MacroControl>   // 0..4 Switches
)
```

### 3.2 Evaluation Pipeline ("Modulating the Modulators")

Instead of injecting Macro values as raw sources into `CVRegistry`, the `MacroEngine` runs directly on the frame tick **prior** to CV evaluation:

1. For each active `MacroControl`, calculate mapped outputs for its `bindings`:
   $$\text{mappedVal} = \text{Curve}(\text{macroVal}, \text{curve}) \times (\text{maxVal} - \text{minVal}) + \text{minVal}$$
   (Inverted if `inverted == true`).
2. If `targetType == PARAM_BASE_VALUE`:
   * Set `ModulatableParameter.baseValue = mappedVal`.
3. If `targetType == MODULATOR_PROPERTY`:
   * Locate `CvModulator` at `modulatorIndex` on the target parameter.
   * Mutate the target field (e.g., `depth`, `subdivision`, `morph`, `slope`, `hold`, `dcOffset`, `attackMs`, `decayMs`) with `mappedVal`.

This zero-allocation approach allows Macro Knobs to smoothly modulate LFO speeds, envelope decay times, and morph parameters in real-time.

### 3.3 Field Ownership & Manual Override

Because the pipeline above writes its target field unconditionally every frame the binding is `enabled`, that field cannot remain a normal interactive control elsewhere in the UI — a manual drag would be overwritten on the very next frame. This applies equally to `PARAM_BASE_VALUE` targets (Parameters panel) and `MODULATOR_PROPERTY` targets (Properties panel); both need the same treatment:

* **Locked, labeled rendering**: Any slider whose exact field — a parameter's `baseValue`, or a specific property (`subdivision`, `morph`, `attackMs`, etc.) on one `CvModulator` — is targeted by an *enabled* `MacroBinding` renders read-only with a small "macro-owned" badge and a tooltip identifying the owner, e.g. `"Controlled by Knob 3 (WARP)"`. This reuses the existing pulsing-highlight visual language already used for the active `MidiLearnTarget` in `PropertiesPanel.kt` / `ParametersRenderer.kt`, rather than introducing a second "externally owned" affordance.
* **One click to the source**: Clicking the badge jumps straight to that binding's row in the Column 3 Binding Inspector, so there is always an obvious next step for changing or releasing it.
* **Release via the existing `enabled` flag**: `MacroBinding.enabled` is the release mechanism — switching it off in the Binding Inspector immediately returns the field to normal interactive editing; switching it back on resumes macro control and accepts a value jump on re-take (mirroring MIDI's `TakeoverMode.IMMEDIATE`, since re-enabling a binding is a deliberate, occasional action rather than a live hardware-jitter case that would need soft takeover).
* **Single source of truth for "is this locked?"**: `MacroEngine` exposes `findBindingsTargeting(unitInstanceId: String?, parameterId: String, modulatorIndex: Int? = null, propertyName: String? = null): List<MacroBinding>`. Both the per-frame evaluation loop and the Parameters/Properties panels' "should this slider render locked?" check call the same lookup, so engine behavior and UI lock state can never drift apart.

---

## 4. Interactive "Learn Mode" UX Workflow

1. **Activate Learn**: The user clicks **`[LEARN]`** on any Macro Knob or Switch in Column 3.
   * The targeted knob enters an active state (pulsing neon highlight border).
2. **Select Target**: The user clicks any control in the UI:
   * **Parameters Panel (Col 1)**: Click any parameter slider -> binds to `PARAM_BASE_VALUE`.
   * **Properties Panel (Col 2)**: Click any modulator slider/knob (e.g., LFO 1 Subdivision, LFO 2 Morph) -> binds to `MODULATOR_PROPERTY`.
3. **Binding Established**:
   * A new `MacroBinding` is appended (up to 4 bindings per control).
   * A toast notification confirms: `"Bound Knob 3 -> Zoom [LFO 1 Morph]"`.
   * Learn mode automatically deactivates.
4. **Binding Inspector**:
   * Displays the active binding list for the selected Macro Knob.
   * Allows setting custom Min/Max bounds, response curve, Invert, or deleting bindings.
   * Per-binding **Enabled** toggle: the release mechanism for the field-ownership lock described in §3.3 — disabling a binding here immediately hands its target field back to normal manual editing.

---

## 5. Hardware Integration & Serialization

### 5.1 MIDI CC & OSC Mapping
* The 8 Macro Knobs and 4 Macro Switches sit at the top of the input hierarchy.
* Physical MIDI CC knobs/faders map directly to Macro Knobs ($1 \dots 8$).
* Physical MIDI CC/Note buttons map to Macro Switches ($1 \dots 4$).
* When OSC is enabled, `/macro/knob/1`–`/macro/knob/8` and `/macro/switch/1`–`/macro/switch/4` send and receive bidirectional updates to tablet surfaces (TouchOSC).

### 5.2 Preset Serialization Schema
* **Bundled (primary, authoritative)**: The active `MacroBank` is embedded directly in the `.lsd` (single deck) / `.lsdset` (deck set) preset JSON, alongside visual generator/FX settings. This is what loads and saves automatically with the preset — no separate file to track, and no risk of bindings referencing parameters that no longer exist.
* **Standalone export/import (secondary, on-demand)**: The Binding Inspector offers "Export Macro Bank..." / "Import Macro Bank..." actions that read/write just the `MacroBank` JSON fragment to a standalone `.knobpreset.json` file. This exists purely so a performer can carry a favorite knob layout across otherwise-unrelated presets; it is never a live-loaded, auto-tracked file the way the bundled copy is. Imports that reference parameters absent from the currently loaded preset simply skip that binding rather than failing the whole import.

---

## 6. Connection to the Modular Video Rack Architecture

The Modular Video Rack Architecture (`docs/developer/modular_video_rack_proposal.md`) reuses this exact data model and engine — `MacroControl`, `MacroBinding`, `MacroEngine` — at a second, additional scope, rather than defining its own macro system:

* **Global bank (this document, unchanged)**: Exactly one `MacroBank` lives in Column 3, scoped to the whole session. Its bindings leave `unitInstanceId = null` and resolve against today's deck-scoped parameter ids (e.g. `"deckA_zoom"`), exactly as described above.
* **Per-unit banks (new, added by the Rack)**: Each rack unit instance additionally owns its *own* `MacroBank` of the same fixed shape (0-8 knobs, 0-4 switches). Its bindings set `unitInstanceId` to that unit's stable id (assigned when the unit is dropped into the bay) and resolve `parameterId` against that unit's own parameters, not the global session. This is what makes the same generator module droppable multiple times into a rack without its macro bindings colliding — two instances of the same module have different `unitInstanceId`s even though their `parameterId`s (e.g. `"dimensionWarp"`) are identical local names.
* **Curation, not custom design (v1 scope)**: A rack unit's faceplate does not get a freeform widget-placement designer in v1. Curating a unit's macro surface means picking which of that unit's exposed parameters occupy which of its (up to 8) knob slots and (up to 4) switch slots, and in what order — the same fixed 2×4 knob grid / 4-switch row layout as Column 3, just scoped to one unit. A full drag-and-drop faceplate designer (arbitrary widget types and grid placement) remains an explicit backlog item; see Open Question 3 in the Rack proposal.

This means Phases 1-4 below (the engine, Column 3 UI, Learn Mode, and serialization) are pure prerequisites for the Rack's per-unit macro curation in Phase 6 — nothing there needs to be rebuilt, only re-scoped via `unitInstanceId`.

---

## 7. Phased Implementation Roadmap

This is the unified roadmap for both this proposal and the Modular Video Rack Architecture; Phases 1-4 live here, Phases 5-8 (and the deferred backlog) are detailed in `modular_video_rack_proposal.md` §4. See also `ROADMAP.md` Milestone 6.

* **Phase 1: Data Model & MacroEngine**: Core `MacroBinding` (including `unitInstanceId` scoping), `MacroControl`, `MacroBank`, and zero-allocation frame evaluation.
* **Phase 2: Column 3 Macro UI Panel**: `[ MIXER | MACROS ]` header toggle, 2x4 Knob grid + 4 Switch buttons, single-deck preview window blit.
* **Phase 3: Interactive Learn Mode & Inspector**: Global UI click interceptor and binding inspector drawer.
* **Phase 4: Serialization & MIDI/OSC Integration**: Bundled `.lsd`/`.lsdset` DTOs, standalone `.knobpreset.json` export/import, and `MidiMappingManager`/`OscEngine` linkage.

> Phases 5-8 (Rack Chassis, Per-Unit Macro Curation, Confidence Micro-Monitors, Rear Panel & Patch Cables) continue in `modular_video_rack_proposal.md`.
