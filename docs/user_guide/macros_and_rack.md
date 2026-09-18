# Macro Controls & the Modular Video Rack

Macro Controls give you a small number of physical-style knobs and switches that each drive several parameters at once — the fast, tactile layer you reach for live instead of hunting through the Parameters panel. The Modular Video Rack builds on the same macro system to let you assemble your visuals as a stack of 19"-rack-style units, each with its own curated faceplate.

---

## Macro Controls (Column 3)

Column 3 (the right-hand panel, where the Mixer normally lives) has a mode toggle at the top: **`[ MIXER | MACROS ]`**.

- **MIXER** — The classic 4-deck crossfader layout (Deck A, B, BG, Master).
- **MACROS** — 8 Macro Knobs (2×4 grid) + 4 Macro Switches, a binding inspector, and a single-deck preview.

### Binding a knob or switch (Learn Mode)

1. Click **`LEARN`** on any Macro Knob or Switch. It starts pulsing to show it's armed, and a banner appears: *"LEARN MODE: Click any parameter slider or modulator to bind."*
2. Click the target:
   - A parameter slider in the **Parameters** panel (Column 1) binds to that parameter's base value.
   - A modulator control in the **Properties** panel (Column 2) — e.g. an LFO's Subdivision or Morph slider, or an envelope's Attack/Decay — binds to that modulator property directly. This lets a macro knob speed up an LFO or shorten an envelope's decay, not just move a value.
3. A toast confirms the binding (e.g. *"Bound Knob 3 → Zoom [LFO 1 Morph]"*) and Learn Mode turns itself off.

Each knob or switch can hold up to **4 bindings**, so one knob can drive several parameters (or modulator properties) simultaneously — with independent settings per binding.

### The Binding Inspector

Selecting a knob/switch shows its bindings in the inspector below the grid. For each binding you can set:

- **Min / Max** — the travel range the binding maps onto, independent of the target's own range.
- **Curve** — Linear, Exponential, Logarithmic, S-Curve, or Step (quantized into a fixed number of positions).
- **Invert** — turning the knob up moves this target down.
- **Enabled** — toggling a binding off immediately hands the target field back to normal manual/mouse editing; toggling it back on resumes macro control.
- **Behavior** *(switch controls only)* — per-binding override of the switch's press/release semantics (see below). Defaults to `— default`, which inherits the control-level **Default Behavior** setting.

The **binding target name** (e.g. `Deck A/Mandala/L1`) is a clickable link. Clicking it switches Column 1 to the corresponding deck and sub-tab, so you can immediately reach the parameter being controlled without hunting for it manually.

### Locked fields & Visual Indicators

Any parameter base value or modulator property that is actively bound to an enabled macro control receives distinct visual cues across Columns 1 and 2:

- **Parameters List (Column 1)**:
  - The row displays an **Electric Cyan left-accent border** and a subtle cyan background tint.
  - An inline badge such as **`[K1]`** or **`[SW2]`** appears beside the parameter name, and the name is tinted Electric Cyan.
  - The "VAL" meter cell is outlined with a cyan border.
  - Hovering over the row or value cell shows a contextual tooltip identifying the controlling macro (e.g. `Locked: Driven by Knob 1 (WARP) [K1]`).
  - **Clicking the row label, badge, or VAL cell** directly navigates Column 3 to the **MACROS** view and selects that specific macro control.

- **Properties & Sliders (Column 2)**:
  - An Electric Cyan **bounding box and background highlight** frames the entire slider row.
  - The variable label displays the **`[K1]`** / **`[SW1]`** badge in cyan.
  - The slider track and dynamic indicator dot glow Electric Cyan instead of their default color.
  - Numeric input text boxes are outlined in cyan and set to read-only to prevent fighting the engine.
  - Hovering over the slider track, handle, or text box displays a tooltip indicating the bound macro control.
  - Clicking the variable label or badge jumps directly to the Column 3 Macro Inspector.

### Switch behaviors

Each Macro Switch has a **Default Behavior** that applies to all of its bindings unless overridden:

- **Toggle** — click flips it between min and max, and it stays there.
- **Momentary** — max while held down, snaps back to min on release. Good for strobes and glitch triggers.
- **Trigger** — fires a single one-frame pulse per click, for one-shot impulses.

Each binding can also carry its own **Behavior** override, set independently in the Binding Inspector. This means a single button press can:

- **Latch** one parameter (Toggle override),
- **Hold** another only while the button is depressed (Momentary override), and
- **Pulse** a third with a one-frame impulse (Trigger override) —

all at the same time, from one button. The switch's lit/unlit state in the UI reflects the button's raw press state as usual; the different downstream behaviors are invisible in the widget itself but described in the tooltip when overrides are mixed.

When any binding has an override, the switch tooltip shows the effective behavior per binding, e.g. *"Mixed behaviors: Toggle / Momentary / Trigger."*

### Saving your knob layout

Macro banks are bundled directly into `.lsd` / `.lsdset` preset files — they load and save with the preset automatically, with no extra file to manage. If you want to reuse a favorite knob layout across unrelated presets, use **Export Macro Bank...** / **Import Macro Bank...** in the inspector to save/load a standalone `.knobpreset.json`. Imports that reference a parameter the currently loaded preset doesn't have simply skip that one binding rather than failing the whole import.

### Hardware control

Macro Knobs and Switches sit at the top of the MIDI/OSC input hierarchy:

- Map a physical MIDI CC knob or fader to any of the 8 Macro Knobs, and a MIDI CC/Note button to any of the 4 Macro Switches, the same way you'd MIDI-learn any other control (see [Performance Controls](performance_controls.md)).
- Turn on the OSC server in **Preferences → OSC Controls**, and `/macro/knob/1`–`/macro/knob/8` and `/macro/switch/1`–`/macro/switch/4` send and receive live updates — handy for a TouchOSC layout on a tablet.

---

## The Modular Video Rack

The Rack is an alternate workspace: instead of the fixed Deck A / B / BG layout, you build a vertical stack of 19"-style rack units, each housing a generator, effect, transition, or utility, with its own curated performance faceplate.

### Switching to Rack view

- Press **`F4`** to toggle between Classic Deck View and the Rack (pressing it again switches back).
- Or use **View → Modular Video Rack** / **View → Classic Deck View** in the menu bar, or click the **`[ CLASSIC | RACK ]`** pill.

### The default rack layout

Opening Rack view populates the bay from your current session:

- **Deck A**, **Deck B**, and **Deck BG** each get one rack unit — the deck's generator *and* all of its active FX slots live together on that single unit's faceplate (FX parameters show up alongside the generator's own, so a deck using 3 FX filters is still just one unit, not four). Deck PV isn't shown in the rack — it's Classic mode's audition/preview deck and doesn't feed the live composite.
- A **Queue & Staging** unit gives you condensed Play Queue / Background Queue / Transition Staging transport controls (prev / play-pause / next, plus what's playing now and what's up next) — the same queues you already use in Classic mode, just visible without leaving the rack.
- A **Master** unit holds the crossfader, blend mode, and master post-FX.

### Adding and arranging units

- **`+ ADD UNIT`** in the rack toolbar lets you drop in an extra clone of Deck A, Deck B, or Deck BG's generator, or a blank Utility unit to build your own — e.g. if you want a second confidence monitor on a deck elsewhere in the stack.
- By default, units are **normalled**: dropping units into the stack automatically wires unit *N*'s output into unit *N+1*'s input, top to bottom — no manual patching needed for a straightforward chain.
- Each unit's header rail has: power, bypass, solo (send straight to master), a macro curation toggle, move up/down, collapse to a thin spine, and remove.

### Per-unit macro curation

Click **`MACRO`** on a unit's header to open its curation drawer. Each rack unit has its own macro bank (up to 8 knobs, 4 switches) — completely independent of the global Column 3 bank and of every other unit's bank, even if two units are the same generator type. Switch between the **Knobs (8)** and **Switches (4)** tabs to pick which of that unit's parameters occupies which slot, and in what order. Binding, curves, min/max, and Learn Mode all work exactly as they do for the global Column 3 macros.

### Confidence micro-monitors

Each unit's faceplate includes a small live preview of that unit's own output, so you can see what it's doing before it feeds downstream, without leaving the rack view.

### Rear panel & patch cables

Press **`Tab`** (or click **FRONT FACEPLATE / REAR CHASSIS** in the toolbar) to flip the whole rack 180° and see the rear chassis: 1/4" phone jacks for **Video In**, **Video Out**, **Mask/Sidechain In**, and **CV Modulation In**, with LEDs showing live signal state.

- **Click a jack and drag** to another jack to patch a cable between them — this overrides the normalled connection for that unit.
- **Right-click** a cable to unplug it and fall back to the normalled routing.

This is where you'd break the default top-to-bottom chain — routing one unit's output to two different downstream units, patching in an external Spout/Syphon/PipeWire feed, or building an intentional feedback loop.

> **Known limitation:** Cables only actually reroute pixels for custom **Utility** units. For the built-in **Generator** (Deck) and **Transition** (Master) units, cables render and jacks light up, but the signal underneath still runs through the fixed Deck/Mixer pipeline — dragging a cable between two built-in units won't change what they actually output. Full patchable routing for built-in units is planned but not yet implemented.

### Master Bypass

The **MASTER BYPASS** button in the rack toolbar passes the input straight through to the output, skipping every unit in the stack — useful for an instant A/B against the unprocessed signal.

---

## Shortcuts

| Key | What it does |
|-----|-------------|
| `F4` | Toggle between Classic Deck View and the Modular Video Rack |
| `Tab` | Flip the rack between Front Faceplate and Rear Chassis (Rack view only) |
