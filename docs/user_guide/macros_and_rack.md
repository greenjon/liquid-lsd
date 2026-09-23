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
- **Link** — which zone of the knob's travel this binding responds to (Mixxx-style parameter
  linking). Instead of a plain dropdown, this is controlled via an intuitive **vector transfer curve
  button** displaying the exact response curve on the button face, with an adjacent **`[±]`** invert toggle:
  - **Full (0-100%)** `[  /  ]` — tracks the entire knob travel.
  - **1st Half (0-50%)** `[ / ‾ ]` — sweeps 0→1 across the first half of the turn, then holds at 1.0 for
    the rest of the travel.
  - **2nd Half (50-100%)** `[ _ / ]` — holds at 0.0 for the first half, then sweeps 0→1 across the second.
  - **Triangle (Peak)** `[ /\ ]` — sweeps 0→1 up to center, then back down to 0 — useful for a target that
    should peak mid-turn and fall off at either extreme.
  - **Bipolar (Center-0)** `[ \/ ]` — 0.0 at center, rising to 1.0 at either end — useful for a target that
    should stay neutral at rest and react to turning the knob in *either* direction.
  - **Invert Toggle `[±]`** — dynamically flips the direction and mirrors the vector glyph geometry.

  Left-click the link button to cycle through the modes; right-click to open a context menu and select directly.
  A common pattern: bind two targets to the same knob, one on **1st Half** and one on **2nd
  Half**, to crossfade or choreograph between them from a single knob turn.
- **Live Value Meter Knob** — on the second row of each binding (to the left of Min/Max, Link Mode, and Curve), a rotary meter knob (the same crisp knob widget used in the Parameters panel) displays the live evaluated output value in relation to the macro knob position. If you use a Triangle ramp (peak) curve or a half-turn zone, moving the macro knob from 0 to 1 lets you see the binding sweep (e.g. 0 → 1 → 0) in real time. Hovering over the knob displays exact numerical readouts.
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

## FX Rack (per-deck FX tabs)

Each deck and Master now has its own **independent FX chain**. Selecting **A FX**, **B FX**,
**BG FX**, **PV FX**, or **MST FX** in Column 3's tab strip swaps the generic macro-knob grid for a
dedicated **FX Rack** view — a Traktor/Mixxx-style Super Knob + Metaknob strip for that deck's
own FX chain. Unlike the old shared FX1/FX2 banks, each deck's FX chain is always available
regardless of what the other decks are doing.

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

Performance Mode is the default view mode for new users. Your selected workspace view mode (Classic vs Performance) and active tab are remembered between sessions in application preferences.

### The layout tabs

Each tab shows 16 knobs across 4 rows, mapped to different banks:

| Tab | Row 1 | Row 2 | Row 3 | Row 4 |
|:---|:---|:---|:---|:---|
| **LIVE QUAD** | Deck A (1–4) | Deck B (1–4) | Deck BG (1–4) | Deck PV (1–4) |
| **MASTER & FX** | Master (1–4) | Transitions (1–4) | FX Wet/Dry (1–4) | Deck PV (1–4) |
| **LIVE CONSOLE** | Deck A (1–4) | Deck B (1–4) | Deck BG (1–4) | Focused FX Target (Super + 3 Metaknobs) |
| **ALL FX** | Deck A FX (1–4) | Deck B FX (1–4) | Deck BG FX (1–4) | Master FX (1–4) |

- **Per-Deck Insert FX & Flexible Routing**: Every deck (A, B, BG, PV) now owns its own dedicated 3-slot `FxChain` running post-generator and pre-crossfader, completely decoupled from other decks.
- **In-Row `[ SRC | FX ]` Mode Toggle (Deck Rows)**: In the header of each Deck row, click **`[ SRC | FX ]`** to toggle that individual row between visual generator macros (`SRC`) and that deck's insert FX macros (`FX`). In `FX` mode, the knobs immediately become Super Knob + 3 Metaknobs for that deck, and the right-wing controls offer instant `[ BYPASS / FX ON ]` and `[ Resync ]` buttons.
- **Target Switcher `[ A | B | BG | PV | MST ]` (Row 4 in Live Console)**: Row 4 provides a focused 4-knob surface for FX. Quickly switch the row's focus between Deck A, B, BG, PV, or Master FX using the target switcher buttons. The row dynamically adopts the accent color and label of the focused target. A dedicated **`[ Preset ▾ ]`** popup button lets you load saved 3-slot FX chains (`.lsdfxchain`) or clear the active chain.
- **ALL FX Tab**: Provides a comprehensive 16-knob surface controlling Deck A FX, Deck B FX, Deck BG FX, and Master FX simultaneously with dedicated `[ Preset ▾ ]`, `[ BYPASS / FX ON ]`, `[ Resync ]`, and slot link buttons across all 4 rows.
- The Master row carries the crossfader and crossfader-time (Fade Speed) controls; the Transitions row carries the transition picker and queue prev/next controls.

Each row is color-coded to its deck or target (blue for Deck A, orange for Deck B, amber for Deck BG, mint for Deck PV, violet for Transitions, crimson for Master, teal for FX) with a colored bar on the left edge and a clear row label.

### Performance Controls & Side-Wing Layout

To maximize vertical space in the matrix and keep the knobs comfortably clustered together:
- **Row Titles & Side-Wing Controls**: Row group titles (e.g. `DECK A`, `FX: Deck A`) sit at the top-left of the row, level with the top of the knobs. The left- and right-wing controls sit below the title, with their bottom edge lined up with the bottom of the knobs, so short rows never push the controls up into the title. Every wing control (buttons, badges, preset dropdown) is the same height.
- **Row Height & Scrolling**: Rows share the panel's height, so they shrink as you drag the Library dock taller. Once rows reach a minimum height (about where the Library is at half height), they stop shrinking and the matrix scrolls vertically instead. Two usable rows plus a scrollbar beat four knobs too small to grab. Scroll with the scrollbar, with the mouse wheel over the gaps between knobs (over a knob the wheel still adjusts that knob), or by click-dragging up/down on any empty part of a row, including its title band.
- **Deck Rows (Deck A, B, BG, PV)**:
  - **Left Wing (Info & Deck Controls)**:
    - **Generator Badge**: Displays active visual source (`deck.source.displayName`).
    - **`[ SRC | FX ]` Toggle**: Instantly toggles between visual source macros and dedicated deck FX chain macros.
    - **Preset Dropdown Combo**: Searchable preset selector with auto-focus Quick-Search filter bar (`presetSearch*`) and dirty marker (`*`).
    - **Eject Button (`⏏`)**: Resets the deck to defaults with dirty-state safety guard.
    - **Randomize Die Button (`🎲`)**: Instantly randomizes that deck's modulators & base values with undo support (when randomization is enabled).
    - **Queue Navigation**: Deck A and Deck B connect to `PlayQueueManager` (`< N/Total >`), Deck BG connects to `BgQueueManager` (`< N/Total >`), and Deck PV features a quick Preview focus button.
  - **Center Cluster**: The 4 macro knobs are grouped close together, centered neatly between the control wings with uniform column alignment across rows. When in `FX` mode, slot link buttons (`Icons.LINK`/`Icons.UNLINK`) appear on columns 2–4 to link/unlink Metaknobs to the Super Knob.
  - **Right Wing**: Displays dedicated insert FX controls for that deck: `[ BYPASS / FX ON ]` and `[ Resync ]` buttons, directly accessible on the deck row at all times.
  - **Drag-and-Drop**: Dropping a deck preset (`.patch`, `.lsd`, `.json`) loads the visual preset; dropping a `.lsdfxchain` loads that FX chain onto the deck.
- **FX Row (Row 4 in Live Console)**:
  - **Left Wing (Target Switcher & Chain Presets)**: Quick target switcher (`[ A ]`, `[ B ]`, `[ BG ]`, `[ PV ]`, `[ MST ]`) and preset selector popup (`[ Preset ▾ ]`).
  - **Center Cluster**: The 4 macro knobs (Super Knob + 3 slot Metaknobs) with slot link/unlink buttons (`Icons.LINK`/`Icons.UNLINK`) beside each slot knob.
  - **Right Wing (Bypass & Utility)**: Chain bypass toggle (`[ BYPASS / FX ON ]`) and explicit `[ Resync ]` button beneath the title.
  - **Drag-and-Drop**: Chain files (`.lsdfxchain`) can be dragged and dropped onto the title bar.
- **ALL FX Tab Rows**: Each of the 4 rows has its own target badge, `[ Preset ▾ ]` loader popup, `[ BYPASS / FX ON ]` toggle, `[ Resync ]` button, and slot link toggles.
- **Whole-Rig Randomize (`[ ALL 🎲 ]`)**: Positioned at the top right of the performance matrix tab strip, pushing undo state and invoking `mixer.randomizeAll()` across all decks simultaneously.
- **Master (Row 1 in Master & FX)**: Features Deck A and Deck B quick-snap buttons (`[ A ]` and `[ B ]`), an interactive zero-centered crossfader track with mouse drag, mouse wheel, middle-click center reset, and live amber modulation/auto-fade indicator dot, a smooth `[ AUTO ]` crossfade trigger, and an interactive **Fade Speed Duration Badge** (crossfader time -- scrubbable with mouse drag/wheel and right-click context menu for duration presets `0.5s`–`8.0s` and MIDI/OSC learn). Transition presets (`.lsdtrans`) and shaders (`.fs`/`.isf`) dropped onto the crossfader track apply directly.
- **Transitions (Row 2 in Master & FX)**: Features a Transition Picker popup button displaying the active transition with modified indicator (`*`), and Transition Queue stepping controls (`<`, `N/Total`, `>`). Transition presets (`.lsdtrans`) and shaders (`.fs`/`.isf`) can be dropped directly onto the title header.

### MIDI Learn in the Performance Matrix

Right-click any knob to arm it for MIDI Learn — a cyan pulsing ring appears around the knob,
identical to the Learn ring on Parameter rows. Send a CC message from any connected controller to bind
it immediately. Right-click again while armed to cancel.

Interactive header controls in the Performance Matrix also support right-click MIDI & OSC Learn:
- **Deck A and Deck B Badges**: Right-click to Learn MIDI (`Global/snapDeckA`, `Global/snapDeckB`) or Learn OSC (`Mixer/snapDeckA`, `Mixer/snapDeckB`) to instantly snap the crossfader left or right.
- **Auto-Fade Button `[ AUTO ]` & Fade Speed Badge**: Right-click to Learn MIDI (`Global/autoFade`, `Mixer/xfadeSpeed`) or Learn OSC (`Mixer/xfadeSpeed`).
- **PlayQueue & BG Queue `<` and `>`**: Right-click to Learn MIDI (`Global/queuePrev`, `Global/queueNext`, `Global/bgQueuePrev`, `Global/bgQueueNext`).
- **Transition Queue `<` and `>`**: Right-click to Learn MIDI (`Global/transQueuePrev`, `Global/transQueueNext`) or Learn OSC (`Mixer/transQueuePrev`, `Mixer/transQueueNext`).
Controls actively armed for MIDI learn display a pulsing cyan highlight border. Active MIDI assignments are shown in tooltips.

### Interacting with knobs

- **Drag** (up/down) to adjust the knob value — same feel as the classic macro knobs.
- **Mouse wheel** to fine-adjust (Shift for finer, Ctrl+Shift for coarser).
- **Hover/drag highlight**: the knob body tints toward the row's accent color on hover, and more
  strongly while you're dragging it, so the knob under your pointer is easy to spot.
- **Right-click** arms hardware MIDI Learn for that knob — the knob pulses cyan while armed, and
  the next CC message from your controller binds to it. Right-click again to cancel.

Knobs in Performance Mode and Classic MACROS read from and write to the **same underlying
`MacroEngine` banks** — changes in one mode are immediately visible in the other.

### The Modular Rack: Deep Edit

Every deep-editable row group in the 4×4 matrix (Deck A/B/BG/PV, the FX row, Master, Transitions)
has a small **chevron button** in its top-right corner (FX Wet/Dry is a dedicated 4-knob macro row with no chevron). Clicking it toggles that row
between two disclosure tiers, without leaving Performance Mode:

1. **Faceplate** (collapsed, the default) — just the 4 knobs, exactly like the plain 4×4 matrix
   above. Clicking a knob selects it (electric cyan focus card, glowing rim, cyan label), shows
   its current value (`Val: 0.00`) beneath the label, and reveals a compact `[Learn]` / `[Cancel]`
   button for arming parameter-bind Learn on the spot.
2. **Deep Edit** — click the chevron to open the comprehensive Deep Edit bay below the top macro row.
   The bay is arranged into a 3-column layout:
   - **5-Channel Side Rail** on the left: color-coded buttons (`[MIX]`, `[A]`, `[B]`, `[BG]`, `[PV]`)
     allowing instant 1-click navigation between all major sections of the app without closing Deep Edit.
     Selecting a side tab switches both the parameter editor below and the top macro row above.
   - **Parameter Grid** in the center: displays the full **VAL / MIDI / LFO / SEQ / AUD** parameter grid
     with section subtabs across the top. All 5 sections feature a uniform 3-tab layout with **`FX` in the center**:
     - **`MIX`**: `[ CTRL ]  [ FX ]  [ TRANS ]` (Master controls, Master FX 4 ISF slots, and Transitions)
     - **`A`**, **`B`**, **`BG`**, **`PV`**: `[ SRC ]  [ FX ]  [ View ]` (Visual generator, insert FX chain, and 3D View/Transform)
     Switching between `SRC`/`CTRL` and `FX` automatically switches the top macro row between visual source controls and insert/master FX macros (`Super Knob + 3 Metaknobs`).
   - **Properties Editor** on the right: side-by-side per-parameter CV detail editor (LFO period/phase/morph/hold/slew, MIDI, SEQ, AUD, curves, and modulators) of whichever cell is selected.

**While in Deep Edit**, the top macro row renders the macro controls corresponding to the active channel and subtab,
reserving the freed vertical space for the side rail and parameter bay.
Collapsing back to Faceplate (or expanding a different row in `SOLO` mode) brings the rest
of the 4×4 grid back.

Click the chevron again (or the **Collapse** button inside the Deep Edit panel or on the row) to fold back to
the Faceplate.

**Accordion behavior**: by default the rack is in **`SOLO`** mode — opening one row's Deep Edit
automatically collapses any other expanded row, so you're never scrolling past several open
panels at once. The toolbar above the grid has a **`[ SOLO | MULTI ]`** toggle (persisted across
restarts) to switch to `MULTI`, where several rows can stay open side by side, and a
**`Collapse All`** button.

**Esc** collapses every expanded row back to the Faceplate — unless a Macro Learn is currently
armed, in which case Esc cancels the Learn instead (a second Esc then collapses the rack). A
row whose own knob has an armed Learn is also exempt from `SOLO` mode's auto-collapse, so it can't
be accidentally folded away mid-Learn; a **"Learning: ‹name› — Esc to cancel"** indicator stays
visible in the toolbar the whole time a Learn is armed, even if you've expanded a different row.
The same Solo/Collapse-All controls are also in **View** menu.

Expanding or collapsing a row is purely a display change — it never re-syncs the Super Knob/Metaknob mapping.

### Setting up knob labels and bindings

The full Binding Inspector (rename, target list, Min/Max/Curve/Invert/Link/Enabled) lives only in
Classic mode's **`[ MACROS ]`** tab (Column 3) — it's no longer duplicated inside the Performance
Mode rack, so binding a knob doesn't mean scrolling past a Deep Edit panel to reach it.

- **In Performance Mode**: click a knob to select it, then click its inline **`[Learn]`** button.
  This arms parameter-bind Learn *and* automatically switches Column 3 to **`[ MACROS ]`** on the
  matching bank/tab, with the Binding Inspector already open on that knob — click a target
  parameter (open that row's **Deep Edit** first if the parameter you want isn't visible anywhere
  else) and set Min/Max/Curve as desired.
- **In Classic mode**: press **`F4`**, open **`[ MACROS ]`** in Column 3, select the knob, click
  **LEARN**, and click a target parameter.

Either way the change is live immediately in both views — Performance Mode and Classic MACROS
read from and write to the same underlying `MacroEngine` banks.

---

## Macro Banks

There are 14 always-resident canonical macro banks (4 knobs each, conforming to the 4-column performance grid):

| Scope | Bank | Knobs | Canonical ID | Smart Defaults |
|:---|:---|:---|:---|:---|
| **Deck Generators** | Deck A | 4 | `deckA` | Blank |
| | Deck B | 4 | `deckB` | Blank |
| | Deck BG | 4 | `deckBG` | Blank |
| | Deck PV | 4 | `deckPV` | Blank |
| **Per-Deck Insert FX** | Deck A FX | 4 | `deckAFx` | Super Knob + 3 Metaknobs |
| | Deck B FX | 4 | `deckBFx` | Super Knob + 3 Metaknobs |
| | Deck BG FX | 4 | `deckBgFx` | Super Knob + 3 Metaknobs |
| | Deck PV FX | 4 | `deckPvFx` | Super Knob + 3 Metaknobs |
| **Mixer & Transitions**| Transitions | 4 | `trans` | Crossfade, Type, Speed, Next |
| | Master | 4 | `master` | Alpha A, Alpha B, Master Level, Crossfader |
| **Shared & Master FX** | FX Bank 1 | 4 | `fxBank1` | Super Knob + 3 Metaknobs |
| | FX Bank 2 | 4 | `fxBank2` | Super Knob + 3 Metaknobs |
| | FX Wet/Dry | 4 | `fxSends` | Deck A, B, BG, PV Insert FX Wet/Dry Levels |
| | Master FX | 4 | `masterFx` | Super Knob + 3 Metaknobs |

Deck generator banks start blank by default for custom binding in the Classic MACROS editor, while Master, Transitions, and FX banks initialize with pre-mapped smart defaults. All 14 canonical banks are preserved in `last_session.json` and session files.

Banks are saved in `last_session.json` and bundled into preset files automatically.

---

## Shortcuts

| Key | What it does |
|-----|-------------|
| `F4` | Toggle between Classic Deck View and Performance Mode |
| `Esc` | Cancel an armed Macro Learn, or (if none is armed) collapse every expanded Rack row back to Faceplate |
