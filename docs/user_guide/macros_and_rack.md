# Macro Controls & Performance Mode

Macro Controls give you a small number of physical-style knobs that each drive several
parameters at once — the fast, tactile layer you reach for live instead of hunting through the
Parameters panel. Performance Mode builds on the same macro system to give you a full-screen 4×4
knob matrix purpose-built for live performance.

---

## Macro Controls (Column 3 MACROS)

Column 3 (the right-hand panel, where the Mixer normally lives) has a mode toggle at the top:
**`[ MIXER | MACROS ]`**.

- **MIXER** — The classic 4-deck crossfader layout (Deck A, B, BG, Master).
- **MACROS** — 8 Macro Knobs (2×4 grid), a binding inspector, and a single-deck preview. This is
  the **editing** surface for your macro layout.

### Binding a knob (Learn Mode)

1. Click **`LEARN`** on any Macro Knob. It starts pulsing to show it's armed, and a banner
   appears: *"LEARN MODE: Click any parameter slider or modulator to bind."*
2. Click the target:
   - A parameter slider in the **Parameters** panel (Column 1) binds to that parameter's base
     value.
   - A modulator control in the **Properties** panel (Column 2) — e.g. an LFO's Subdivision or
     Morph slider, or an envelope's Attack/Decay — binds to that modulator property directly.
     This lets a macro knob speed up an LFO or shorten an envelope's decay, not just move a value.
3. A toast confirms the binding (e.g. *"Bound Knob 3 → Zoom [LFO 1 Morph]"*) and Learn Mode
   turns itself off.

Each knob can hold up to **4 bindings**, so one knob can drive several parameters (or modulator
properties) simultaneously — with independent settings per binding.

### The Binding Inspector

Selecting a knob shows its bindings in the inspector below the grid. For each binding you can
set:

- **Min / Max** — the travel range the binding maps onto, independent of the target's own range.
- **Curve** — Linear, Exponential, Logarithmic, S-Curve, or Step (quantized into a fixed number
  of positions).
- **Invert** — turning the knob up moves this target down.
- **Enabled** — toggling a binding off immediately hands the target field back to normal
  manual/mouse editing; toggling it back on resumes macro control.

The **binding target name** (e.g. `Deck A/Mandala/L1`) is a clickable link. Clicking it switches
Column 1 to the corresponding deck and sub-tab, so you can immediately reach the parameter being
controlled without hunting for it manually.

### Locked fields & Visual Indicators

Any parameter base value or modulator property that is actively bound to an enabled macro control
receives distinct visual cues across Columns 1 and 2:

- **Parameters List (Column 1)**:
  - The row displays an **Electric Cyan left-accent border** and a subtle cyan background tint.
  - An inline badge such as **`[K1]`** appears beside the parameter name, and the name is
    tinted Electric Cyan.
  - The "VAL" meter cell is outlined with a cyan border.
  - Hovering over the row or value cell shows a contextual tooltip identifying the controlling
    macro (e.g. `Locked: Driven by Knob 1 (WARP) [K1]`).
  - **Clicking the row label, badge, or VAL cell** directly navigates Column 3 to the **MACROS**
    view and selects that specific macro control.

- **Properties & Sliders (Column 2)**:
  - An Electric Cyan **bounding box and background highlight** frames the entire slider row.
  - The variable label displays the **`[K1]`** badge in cyan.
  - The slider track and dynamic indicator dot glow Electric Cyan instead of their default color.
  - Numeric input text boxes are outlined in cyan and set to read-only to prevent fighting the
    engine.
  - Hovering over the slider track, handle, or text box displays a tooltip indicating the bound
    macro control.
  - Clicking the variable label or badge jumps directly to the Column 3 Macro Inspector.

### Saving your knob layout

Macro banks are bundled directly into `.lsd` / `.lsdplay` preset files — they load and save with
the preset automatically, with no extra file to manage. If you want to reuse a favorite knob
layout across unrelated presets, use **Export Macro Bank...** / **Import Macro Bank...** in the
inspector to save/load a standalone `.knobpreset.json`.

### Hardware control

Macro Knobs sit at the top of the MIDI/OSC input hierarchy:

- Right-click any knob in the **Performance Mode 4×4 matrix** (see below) to arm hardware MIDI
  Learn for it — the next CC your controller sends binds to that knob. Hardware MIDI Learn is
  Performance-Mode-only; the Classic MACROS editor's `LEARN` button is for parameter binding, not
  MIDI mapping.
- Turn on the OSC server in **Preferences → OSC Controls**, and `/macro/knob/1`–`/macro/knob/8`
  send and receive live updates.

---

## FX Rack (FX1 / FX2 / MFX tabs)

Selecting **FX1**, **FX2**, or **MFX** in Column 3's tab strip swaps the generic 8-knob grid for a
dedicated **FX Rack** view — a Traktor/Mixxx-style performance strip for that bank's currently
active chain (use the `[ Chain 1 ] [ Chain 2 ] [ Chain 3 ]` subtabs to switch chains).

### Chain Super Knob & effect Metaknobs

- **Super Knob** — one knob that sweeps every **linked** slot's own Metaknob together.
- **Metaknob** (one per slot) — the effect's single macro control. For a hand-curated effect
  (the bundled filters) it's tuned to feel right out of the box. For any other ISF shader —
  including the thousands available from third-party shader packs — it's auto-mapped the first
  time the shader loads, using the shader's own declared `IDENTITY`/parameter metadata, or a
  Dry/Wet fallback if nothing better is found. You never get a dead knob.
- **Link** (per slot) — toggles whether that slot's Metaknob follows the Super Knob. Turning Link
  back on doesn't snap the Metaknob to wherever the Super Knob currently sits — it waits until you
  move the Super Knob far enough for it to reach the Metaknob's current position first (soft
  pickup), the same behavior used for hardware MIDI/OSC takeover elsewhere in the app.
- **Focus** (per slot) — swaps the 3 knobs to that effect's own top parameters instead of the
  group Metaknobs, for fine-grained single-effect tweaking. Click **Group Mode** to return.
- **Right-click a Metaknob** to rebind it to a different parameter, to the Dry/Wet safety net, or
  to reset it back to the auto-bind default.

### Hardware control note

Every knob in the strip (Super Knob, each Metaknob, and each focused-mode parameter row) supports
the same right-click **Learn MIDI** / **Learn OSC** as any other slider in the app — each has its
own fixed control path. **This means a physical knob you've bound to a slot's Metaknob in Group
Mode will *not* automatically control that slot's parameters once you switch to Focus Mode** (and
vice versa) — they're different paths, so you'd bind the physical knob again for the focused view
if you want it there too. A true Traktor/Mixxx hardware unit keeps the same 4 physical knobs
mapped regardless of focus state; matching that exactly would need new plumbing this pass didn't
build. If this turns out to matter in practice for your hardware workflow, it's a known follow-up.

---

## Performance Mode (4×4 Matrix)

Performance Mode replaces Columns 1 & 2 with a full **4×4 Macro Knob Matrix** — 16 knobs
arranged in 4 rows across 4 columns, color-coded by deck — purpose-built for live use. The
Mixer column (Column 3) and Library dock remain fully visible.

### Switching to Performance Mode

- Press **`F4`** to toggle between Classic Deck View and Performance Mode (pressing it again
  switches back).
- Or use **View → Performance Mode** / **View → Classic Deck View** in the menu bar, or click
  the **`[ CLASSIC | PERF ]`** pill in the toolbar.

Your active tab is remembered between sessions.

### The four layout tabs

Each tab shows 16 knobs across 4 rows, mapped to different banks:

| Tab | Row 1 | Row 2 | Row 3 | Row 4 |
|:---|:---|:---|:---|:---|
| **LIVE QUAD** | Deck A (1–4) | Deck B (1–4) | Deck BG (1–4) | Transitions (1–4) |
| **DUAL DECKS** | Deck A (1–4) | Deck A (5–8) | Deck B (1–4) | Deck B (5–8) |
| **PREP & BG** | Deck PV (1–4) | Deck PV (5–8) | Deck BG (1–4) | Deck BG (5–8) |
| **MASTER & FX** | Transitions (1–4) | Transitions (5–8) | Master (1–4) | Master (5–8) |

Each row is color-coded to its deck (blue for Deck A, orange for Deck B, amber for Deck BG,
mint for Deck PV, violet for Transitions, crimson for Master) with a colored bar on the left
edge and a faint row label.

### Interacting with knobs

- **Drag** (up/down) to adjust the knob value — same feel as the classic macro knobs.
- **Mouse wheel** to fine-adjust (Shift for finer, Ctrl+Shift for coarser).
- **Right-click** arms hardware MIDI Learn for that knob — the knob pulses cyan while armed, and
  the next CC message from your controller binds to it. Right-click again to cancel. This is the
  only editing action available in Performance Mode; everything else (parameter-bind Learn,
  binding inspector, Min/Max/Curve) is done in Classic mode's Column 3 MACROS tab.

Knobs in Performance Mode and Classic MACROS read from and write to the **same underlying
`MacroEngine` banks** — changes in one mode are immediately visible in the other.

### Setting up knob labels and bindings

Performance Mode does not have an editor. To set what each knob does:

1. Press **`F4`** to switch to Classic mode.
2. Open **`[ MACROS ]`** in Column 3.
3. Select the knob you want to configure, click **LEARN**, and click a target parameter.
4. Set Min, Max, Curve as desired in the Binding Inspector.
5. Press **`F4`** to return to Performance Mode — all your settings are live.

---

## Macro Banks

There are six always-present macro banks, one per deck/scope:

| Bank | Knobs available | Canonical ID |
|:---|:---|:---|
| Deck A | 8 | `deckA` |
| Deck B | 8 | `deckB` |
| Deck BG | 8 | `deckBG` |
| Deck PV | 8 | `deckPV` |
| Transitions | 8 | `masterTransition` |
| Master | 8 | `master` |

All banks start blank — no default bindings. Label your knobs and bind them to whatever matters
for your performance in the Classic MACROS editor.

Banks are saved in `last_session.json` and bundled into preset files automatically.

---

## Shortcuts

| Key | What it does |
|-----|-------------|
| `F4` | Toggle between Classic Deck View and Performance Mode |
