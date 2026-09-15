# Proposal: Macro Controls & Parameter Linking System

**Status**: Draft / RFC  
**Target Area**: `ui/`, `parameters/`, `models/`, `presets/`, `midi/`, `osc/`  
**Authors**: GJ & Antigravity  

---

## 1. Executive Summary & Core Concept

As Liquid LSD grows in synthesis capability, visual presets often expose dozens of parameters spread across generators, color tuners, LFO modulators, and audio envelopes. Performing live requires immediate, tactile access to key expressive dimensions without navigating long lists of sliders.

This document outlines the design for the **Macro Controls & Parameter Linking System**—a foundation for both immediate live performance and the upcoming **Modular Video Rack Architecture**.

### Key Highlights:
* **8 Macro Knobs + 4 Macro Switches**: A dedicated physical and UI control surface for live performance.
* **1-to-Many Binding Topology**: A single Macro Knob or Switch can drive up to 4 target parameters simultaneously with independent min/max travel limits, inversion, and response curves.
* **Modulating the Modulators**: Macro Knobs target not only parameter base values but also **modulator properties** (e.g. LFO speed/subdivision, LFO morph, envelope attack/decay, LFO depth).
* **Interactive "Learn Mode" UX**: Click "Learn" on any knob/switch, then click any parameter slider or modulator control in the UI to establish a binding instantly.
* **Column 3 Dual-Mode UI (`[ MIXER | MACROS ]`)**: Places the macro controls, binding inspector, and focused single-deck preview inside Column 3, leaving **Parameters** (Col 1) and **Properties** (Col 2) fully visible for seamless linking.
* **Hardware Parity**: Full integration with `MidiMappingManager` (MIDI CC) and `OscEngine` (OSC).
* **Preset Serialization**: Standalone `.knobpreset.json` files and bundled `.preset.json` presets.

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
    LINEAR, EXPONENTIAL, LOGARITHMIC, STEP
}

enum class SwitchBehavior {
    TOGGLE,     // Latch: click toggles between Min and Max
    MOMENTARY,  // Hold: Max while held, Min on release
    TRIGGER     // Impulse: Sends 1-frame pulse or one-shot trigger
}

data class MacroBinding(
    val parameterId: String,          // e.g. "deckA_zoom"
    val targetType: MacroTargetType,
    val modulatorIndex: Int = 0,      // Index in parameter's modulator stack
    val propertyName: String = "",    // e.g. "subdivision", "morph", "depth", "attackMs"
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

data class MacroPreset(
    val knobs: List<MacroControl>,     // 8 Knobs
    val switches: List<MacroControl>   // 4 Switches
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

---

## 5. Hardware Integration & Serialization

### 5.1 MIDI CC & OSC Mapping
* The 8 Macro Knobs and 4 Macro Switches sit at the top of the input hierarchy.
* Physical MIDI CC knobs/faders map directly to Macro Knobs ($1 \dots 8$).
* Physical MIDI CC/Note buttons map to Macro Switches ($1 \dots 4$).
* When OSC is enabled, `/macro/knob/1`–`/macro/knob/8` and `/macro/switch/1`–`/macro/switch/4` send and receive bidirectional updates to tablet surfaces (TouchOSC).

### 5.2 Preset Serialization Schema
* **Standalone Knob Presets**: Saved as `.knobpreset.json` files for instant recall across different visual sources.
* **Bundled Visual Presets**: Extended `.preset.json` schema storing both visual generator/FX settings AND the active `MacroPreset` state.

---

## 6. Connection to the Modular Video Rack Architecture

In the upcoming **Modular Video Rack Architecture** (`docs/developer/modular_video_rack_proposal.md`), each rack module faceplate will embed this 8-Knob / 4-Switch Macro System as its front-panel performance surface.

---

## 7. Phased Implementation Roadmap

* **Phase 1: Data Model & MacroEngine**: Core `MacroBinding`, `MacroControl`, and zero-allocation frame evaluation.
* **Phase 2: Column 3 Macro UI Panel**: `[ MIXER | MACROS ]` header toggle, 2x4 Knob grid + 4 Switch buttons, single-deck preview window blit.
* **Phase 3: Interactive Learn Mode & Inspector**: Global UI click interceptor and binding inspector drawer.
* **Phase 4: Serialization & MIDI/OSC Integration**: JSON DTOs, file I/O, and `MidiMappingManager` linkage.
