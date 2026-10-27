# Macro Controls & the Perform View

Macro Controls give you a small number of physical-style knobs that each drive several
parameters at once — the fast, tactile layer you reach for live instead of hunting through
parameter grids. The Perform view builds on the same macro system to give you a 4×4 knob matrix
purpose-built for live performance, with Edit underneath for everything else.

---

## Macro Controls

Macro knobs live in the **Perform view 4×4 matrix**, and they are edited in place. The right-hand column
shows only the Mixer; there is no separate MACROS tab.

In the **Edit** view, select a macro knob in an expanded row. That row's left side switches to the
**target strip**:

- **Line 1** — the knob's name (double-click to rename), its live value, **Add Target** / **Cancel**,
  one chip per target (1–4), a kebab menu (⋮), and a close button.
- **Line 2** — the selected target's settings (name, Min/Max, Curve, Link, Invert, Enabled).

FX rows show a read-only strip instead: FX knobs have a fixed role per mode (Super Knob + 3
Metaknobs, or Metaknob + 3 parameters when a slot is focused), so the strip names the role rather
than offering targets.

### Adding targets to a knob

1. Click **`Add Target`** on a macro knob. It starts pulsing to show it's armed, and a banner
   appears: *"ADD TARGET: Click any parameter slider or modulator property."*
2. Click the target:
   - A parameter slider in a Edit **parameter grid** becomes a target through that
     parameter's base value.
   - A modulator control in Edit's **Modulation** column — e.g. an LFO's Subdivision or
     Morph slider, or an envelope's Attack/Decay — becomes a target directly.
     This lets a macro knob speed up an LFO or shorten an envelope's decay, not just move a value.
3. A toast confirms the target (e.g. *"Added target: Knob 3 → Zoom [LFO 1 Morph]"*) and Add Target
   turns itself off.

A knob can only target parameters in **its own deck and section**:

- Deck A **SRC** knobs target Deck A's SRC parameters only.
- **FX** knobs (each deck's and Master's) follow their FX chain automatically and take no manual targets;
  the editors show them as "Driven by the FX chain".
- **Master** and **Transition** knobs take Mixer parameters only, and **FX Send** knobs take the per-deck FX send only.
- **Global** knobs can target anything.

If you click a parameter outside that section, the banner says *"Cannot add target…"* and Add Target stays
armed, so you can click the right one. If you move to another section while Add Target is armed, it
is cancelled. That includes Edit's SRC/FX tabs and the side rail. Master knobs aren't
limited this way, and Transitions knobs can't be learned (they follow the active transition).

Each knob can hold up to **4 targets**, so one knob can drive several parameters (or modulator
properties) simultaneously — with independent settings per target.

### Editing a target

Selecting a knob shows its targets in the target strip (Edit view) and in the Modulation
editor of the open Edit. For each target you can set:

- **Min / Max** — the travel range the target maps onto, independent of the target's own range.
- **Curve** — Linear, Exponential, Logarithmic, S-Curve, or Step (quantized into a fixed number
  of positions).
- **Link** — which zone of the knob's travel this target responds to (Mixxx-style parameter
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
  A common pattern: give one knob two targets, one on **1st Half** and one on **2nd
  Half**, to crossfade or choreograph between them from a single knob turn.
- **Live Value Meter Knob** — on the second row of each target (to the left of Min/Max, Zone, and Curve), a rotary meter knob (the same crisp knob widget used in the parameter grid) displays the live evaluated output value in relation to the macro knob position. If you use a Triangle ramp (peak) curve or a half-turn zone, moving the macro knob from 0 to 1 lets you see the binding sweep (e.g. 0 → 1 → 0) in real time. Hovering over the knob displays exact numerical readouts.
- **Enabled** — toggling a target off immediately hands the target field back to normal
  manual/mouse editing; toggling it back on resumes macro control.

The **target name** (e.g. `Deck A/Mandala/L1`) is a clickable link. Clicking it opens the
corresponding deck's (or the Mixer's) Edit on the right sub-tab with the parameter selected,
so you can immediately reach the parameter being controlled without hunting for it manually.

### Locked fields & Visual Indicators

Any parameter base value or modulator property that is the target of an enabled macro knob
receives distinct visual cues in Edit:

- **Parameter grid**:
  - The row displays an **Electric Cyan left-accent border** and a subtle cyan background tint.
  - An inline badge such as **`[K1]`** appears beside the parameter name, and the name is
    tinted Electric Cyan.
  - The "VAL" meter cell is outlined with a cyan border.
  - Hovering over the row or value cell shows a contextual tooltip identifying the controlling
    macro (e.g. `Locked: Driven by Knob 1 (WARP) [K1]`).
  - **Clicking the row label, badge, or VAL cell** selects that macro control and shows its
    target strip.

- **Modulation & Sliders**:
  - An Electric Cyan **bounding box and background highlight** frames the entire slider row.
  - The variable label displays the **`[K1]`** badge in cyan.
  - The slider track and dynamic indicator dot glow Electric Cyan instead of their default color.
  - Numeric input text boxes are outlined in cyan and set to read-only to prevent fighting the
    engine.
  - Hovering over the slider track, handle, or text box displays a tooltip indicating the controlling
    macro knob.
  - Clicking the variable label or badge selects the controlling macro and shows its target strip.

### Saving your knob layout

Macro banks are bundled directly into `.lsd` / `.lsdplay` preset files — they load and save with
the preset automatically, with no extra file to manage. If you want to reuse a favorite knob
layout across unrelated presets, use the **kebab menu (⋮)** on the target strip: **Export Macro Bank...** saves the whole bank
(all 4 knobs) as a standalone `.knobpreset.json` (default folder `library/knobpresets`), and **Import
Macro Bank...** loads one into the bank you're editing. Targets that don't exist on the deck it lands on
are skipped and counted in the status message. Importing onto a deck bank points its targets at that deck
to that deck, like a preset load; the Master bank keeps targets as saved. The same menu
has **Rename** (double-clicking the name also works). FX banks have no menu.

### Hardware control

Macro Knobs sit at the top of the MIDI/OSC input hierarchy:

- Right-click any knob in the **Perform view 4×4 matrix** (see below) to arm hardware MIDI
  Learn for it — the next CC your controller sends is mapped to that knob. Hardware MIDI Learn is
  Performance-Mode-only; the target strip's `Add Target` button chooses what a knob controls, not
  which controller moves it.
- Turn on the OSC server in **Preferences → OSC Controls**, and `/macro/knob/1`–`/macro/knob/8`
  send and receive live updates.

---

## FX chains in Edit

Each deck and Master has its own **independent FX chain**. A deck's **FX** tab in Edit (and **OUT > FX** for
Master) starts with the chain's **Dry/Wet** row and its `⋮` chain menu (the same menu as on the FX row), then the
**SUPER KNOB & METAKNOBS** block: the chain's Super Knob, then one row per slot Metaknob with its
Super Knob **Link** checkbox. These are full parameter rows, so they take modulators and right-click MIDI / OSC
Learn like any other. Below it, each slot has its parameters, its **Metaknob targets** and its **Knobs** order.

Edit and the macro strip always point at the same deck and section:

- Opening a deck's Edit anywhere (its row's `[EDIT]`, the side rail, the **SRC / FX** tabs)
  focuses that deck and section.

The same applies to the other decks and Master (the Master MST row opens Edit's **MST** side tab,
the Transitions and Master FX rows its **OUT** side tab).

### Who wins when several things drive the same value

A parameter's base value can be driven from several places. From strongest to weakest:

1. **A macro knob target** locks the value against direct writes, but the Value and the knob are linked both ways: dragging, scrolling or typing in **Initial Range** moves the knob (and so every other target of that knob), and the Value follows. Stepped and S-curve targets land on the nearest position, and a typed value outside the target's range stops at the knob's end. A randomize (the deck die, Rand, or the Initial Range dice) moves the knob the same way, so a rolled value sticks. Any MIDI or OSC mapping on it is **suspended** (its row in Preferences shows "Suspended: driven by ...") and works again when you release the target.
2. **An effect Metaknob target** (including through the Super Knob, which moves linked Metaknobs) owns the effect parameter it is aimed at. A macro knob can't be added to that parameter ("driven by its Metaknob"), and an existing macro target on it is ignored while the Metaknob target is enabled. The parameter's row shows the same cyan highlight as a macro target, with a `[K1]` badge when the Metaknob sits on a macro knob. As with a macro target, editing the Value in Edit moves the Metaknob instead (and the macro knob above it, if it has one). Uncheck the Metaknob target (in the slot's **Metaknob targets** block or on the Edit-row strip) to use a macro knob there instead. A Metaknob aimed at **Dry/Wet** owns the slot's Dry/Wet the same way: the focus header's `Wet` slider then moves the Metaknob. MIDI and OSC mappings on it are suspended as well.
3. **Direct edits, MIDI and OSC mappings** on an unlocked value: the last one to move wins.
4. **Modulators (LFO, sequencer, audio)** never compete. They are always added on top of whatever value results from 1-3.

### Chain Super Knob & effect Metaknobs

- **Super Knob** — one knob that sweeps every **linked** slot's own Metaknob together.
- **Metaknob** (one per slot) — the effect's single macro control. For a hand-curated effect
  (the bundled filters) it's tuned to feel right out of the box. For any other ISF shader —
  including the thousands available from third-party shader packs — it's auto-mapped the first
  time the shader loads, using the shader's own declared `IDENTITY`/parameter metadata, or a
  Dry/Wet fallback if nothing better is found. If you remove every target, the Metaknob drives
  nothing, and stays that way when the slot is saved. A target that names a parameter the effect doesn't
  have (renamed, or copied from another effect) drives nothing and shows `(missing)`. When a slot loads
  or resets, the Metaknob starts where its targets come closest to their current values: exactly for one
  target, and the best compromise when several targets can't all be matched by one position.
- **Slot cells** — on a Performance row in FX mode, each Metaknob has no caption; the slot cell
  right under it names the effect in that slot. Only the Super Knob keeps its `SUPER` caption.
- **Link** (per slot) — toggles whether that slot's Metaknob follows the Super Knob. Linking never
  snaps a Metaknob to wherever the Super Knob sits. A linked slot's own knob still works: turning it
  (or editing the Metaknob any other way) moves just that Metaknob.
- **Super Knob response** (Preferences) — how linked slots follow:
  - **Relative** (default): every linked slot moves as soon as you turn the Super Knob, and keeps its
    own position: a slot at 20% and one at 90% both rise, stay apart, and both reach 100% at the top
    and 0% at the bottom. Bring the Super Knob back and they return to where they were. Moving a
    slot by hand just gives it a new starting point.
  - **Pickup**: a slot waits until the Super Knob reaches its Metaknob (soft pickup, as for MIDI/OSC
    takeover), then follows it exactly, so linked slots end up moving together. Turning a slot's own
    knob lets go of the Super Knob until it reaches the slot again. While a slot waits, its link
    button is amber and the Edit-row strip says "waiting for pickup".
- **Focus Mode** (per slot) — hands the chain's knobs to one effect slot. Focusing from the Perform row, the
  Twister or the Metaknob row's menu in Edit is the same thing: the knobs follow, and Edit's SUPER KNOB & METAKNOBS block
  shows which parameters knobs 2-4 hold, with page arrows and **Group Mode** to leave.
  - **Knob 1**: The focused slot's Metaknob, linked or not. The slot's **Dry/Wet** is the `Wet` slider in the chain header (middle-click resets to 100%).
  - **Knobs 2–4**: Retargeted to the focused effect's top parameters on the active page, with parameter paging (`[◀ P1/N ▶]`) when more than 3 parameters exist. A parameter the Metaknob already drives is left out, since knob 1 moves it, unless you set the slot's own knob order (see *Editing a Metaknob's targets and knob order* below). Time-like parameters (names with a word such as rate, speed, frequency, decay or feedback, e.g. `fbDecay`) respond exponentially, so the slow end gets more of the knob's travel.
  - **Parameter Cells (`FxParamCell`)**: Display parameter values with a reset-to-default button (counter-clockwise arrow).
  - **Hardware MIDI**: Physical controllers mapped to `Macro/<bankId>/knob_1..4` keep their paths; in Focus Mode they control the focused slot's Metaknob and parameters. The layout is fixed per mode (Mixxx-style), so a mapped CC changes meaning when you toggle focus.
  - Click the active slot pill in the header (or **Group Mode** in Edit) to return to standard Group Mode.
- **Right-click a Metaknob row in Edit** to focus that slot, **Save as Default** for the effect, or
  **Remove Saved Default** (see *Explicit ISF FX Defaults* below).

#### Editing a Metaknob's targets and knob order

A Metaknob can drive several parameters at once, each with its own response.

- **In the Edit view**, select a slot's Metaknob. The row strip shows one numbered chip per target, **Add Target**
  (pick from the parameters that aren't targets yet, or Dry/Wet) and the selected target's controls: the enable
  checkbox, knob zone (full range, first half, second half, triangle, bipolar, invert), response curve
  (linear, exponential, logarithmic, S-curve, step with a step count), a range bar with draggable handles, Min and
  Max, and delete. Right-click the zone button for the full list.
- **In the Edit bay**, each slot has a **Metaknob targets** block with the same controls for every target and an
  **Add Target** menu. A parameter's own row menu also has a **Metaknob Target** check to add or remove it quickly.
- **Words used here**: a Metaknob's *targets* are the parameters it drives; *Link* only ever means a slot following
  the Super Knob.
- **Knobs** (also in the Edit bay) lists the parameters on focus-mode knobs 2-4, three per page. Move a parameter
  up or down, take it off the knobs, bring it back from *Hidden*, or **Reset order**. A lock beside a parameter
  means the Metaknob drives it, so its knob does nothing while that target is enabled.
- Targets and knob order are saved with the slot and chain. Changing them in one chain never changes another
  chain or the stock effect; **Save as Default** makes your targets and order the starting point for new copies of
  that effect. Ctrl+Z undoes a change, and a drag on a range handle is a single step.

### Hardware control note

Map hardware to the **Perform knobs** (right-click a knob, or use a controller profile such as the Twister's):
their paths (`Macro/<bankId>/knob_1..4`) stay fixed and their meaning follows the mode, so the same physical
knob is the Super Knob in group mode and the focused Metaknob in Focus Mode, Mixxx-style. Learn on a Super
Knob or Metaknob row in Edit maps that one control instead, and is locked (`locked by K#`) while a Perform
knob already drives it.

---

## The Perform View (4×4 Matrix)

The Perform view is the app's main view: a **4×4 Macro Knob Matrix** — 16 knobs arranged in 4
rows across 4 columns, color-coded by deck — on the left, with the Mixer column and
Library dock alongside. (Earlier versions also had a "Classic" Parameters/Properties view,
toggled with `F4`; everything it did now lives in Edit, below.)

The active page is remembered between sessions in application preferences.

### The Perform pages

Each page shows 4 rows of 4 knobs, mapped to different banks:

| Page | Row 1 | Row 2 | Row 3 | Row 4 |
|:---|:---|:---|:---|:---|
| **A/B** | Deck A source | Deck A FX | Deck B source | Deck B FX |
| **BG/PV** | Deck BG source | Deck BG FX | Deck PV source | Deck PV FX |
| **MIXER** | Transitions | Master FX | Master MST | Clock (knob 13) |

Every deck and Master row shows one half (source or FX), so a knob never changes meaning under you.
Opening a deck in Edit shows the row for the Edit tab you are on (SRC or FX).

- **Per-Deck Insert FX & Flexible Routing**: Every deck (A, B, BG, PV) now owns its own dedicated 3-slot `FxChain` running post-generator and pre-crossfader, completely decoupled from other decks.
- **Deck Rows Are Split into a Source Row and an FX Row**: Each deck has a **source row** (`[ SRC ]` pill, kebab, generator/preset badge, Save, queue navigation, eject; its knobs are the source's macros) and an **FX row** (`[ FX ]` pill, kebab, chain name, Save, queue navigation, slot pills `1 2 3`; its knobs are the chain's Super Knob and three Metaknobs). The right wing of the FX row has the `[ FX ON ]` button. The source row's right wing has the randomize die (Source and FX).
- **Master Rows Are Split Too**: The **Master MST row** (`[ MST ]` pill) has the deck-levels badge, with the four knobs on the deck levels and the master level. The **Master FX row** (`[ FX ]` pill) has the Master FX chain header (the chain name, then `[⋮] [Save] [1] [2] [3]`) and its `[ FX ON ]` button, with the knobs on the Master FX Super Knob + 3 Metaknobs. The crossfader sits on the Transitions row.
- The Transitions row has the transition picker on line 1 (click it to pick from the Library below — see below; the Edit gear sits at its end), and line 2 is laid out like an FX row: `[⋮]` menu, `[Save]` (saves the transition as a preset), queue `[◀] [▶]`, then the crossfader cells below. Its four knobs are fixed: **knob 1 is the crossfader** (it follows auto-fades and the slider, and turning it takes over like the slider does), and **knobs 2-4 are the active transition's first three sliders**, relabelled whenever you change transition. Transitions with fewer than three sliders leave the spare knobs blank. Line 2 ends with `A` / `B` snap buttons, `AUTO` and the fade time; there is no crossfader slider on the row (use knob 1 or the Mixer panel's slider). These knobs cannot be re-learned; Dry/Wet stays in the Edit bay.

Each row is color-coded to its deck or target (orange for Deck A, blue for Deck B, green for Deck BG, plum for Deck PV, cyan for Transitions, light grey for Master) and starts with a title badge spanning both control lines: a large **A**, **B**, **BG** or **PV** on deck rows (its caption strip, **SRC** or **FX**, shows which half the knobs control), a large **M** captioned **TRAN**, **FX** or **MST** on the MIXER page's Transitions and Master rows, and **CLK** on the Clock row. No row has a header bar over its knobs, so MIXER-page knobs are the same size as deck knobs.

### Performance Controls & Side-Wing Layout

To maximize vertical space in the matrix and keep the knobs comfortably clustered together:
- **Row Titles & Side-Wing Controls**: Row group titles (e.g. `DECK A`, `DECK A (FX)`) sit at the top-left of the row, level with the top of the knobs. The left- and right-wing controls sit below the title, with their bottom edge lined up with the bottom of the knobs, so short rows never push the controls up into the title. Every wing control (buttons, badges, generator/preset badge) is the same height.
- **Row Height**: Every row has the same fixed height, whatever the window size, so knobs never resize and nothing scrolls. The Library fills the window height below the rows.
- **Deck Rows (Deck A, B, BG, PV)**:
  - **Left Wing (Info & Deck Controls)**:
    - **Row 1 (SRC)**:
      - **`[ SRC ]` Pill**: Labels the source row, whose 4 on-screen macro knobs drive the deck's source.
      - **Source / Preset Badge**: One control, not two -- a preset is just a source with its parameter values saved under a name. With a preset loaded it shows the preset's name (with `*` when changed) followed by the source it plays, dimmed (`curated-0001 · Fractal flame`); with a bare source, the source's name. Click it and the Library below lists stock sources and saved presets together, applying to this deck (click again to stop); right-click for source defaults. Clicking the stock source of the preset you're playing loads that source's defaults; clicking the source a deck already plays bare does nothing.
      - **Eject Button (`⏏`)**: Empties the deck (with the unsaved-changes guard). The deck's FX chain is left as it is. Ctrl+Z can't undo it.
      - **Queue Navigation**: Deck A and Deck B connect to `PlayQueueManager` (`< N/Total >`), Deck BG connects to `BgQueueManager` (`< N/Total >`), and Deck PV features a quick Preview focus button.
      - **Save Button**: Saves the deck's source and parameters as a preset, back to the file it was loaded from (a new name goes to `presets/`). Greyed out for external video, which can't be saved.
      - **Kebab Menu (`⋮`)**: Source operations (Browse, Save As, defaults).
    - **Row 2 (FX)**:
      - **`[ FX ]` Knob Pill**: Assigns the row's 4 on-screen macro knobs to the deck's insert FX chain (Super Knob + 3 Metaknobs).
      - **Dedicated Chain Controls**: Step through chains (`◀` / `▶`), active chain name with dirty dot (`•`) — click it to pick a whole chain from the Library below — `[Save]` button, and kebab menu (`[⋮]`) for Save As, Revert, Copy/Paste, and Resync.
  - **Knob Columns**: The 4 macro knobs are uniformly spaced across the center area between the control wings, sharing the exact same column pitch and horizontal alignment whether assigned to `SRC` or `FX`. When assigned to `FX`, each of knobs 2–4 gets two stacked buttons on its left: a link button (`Icons.LINK`/`Icons.UNLINK`) that links/unlinks its Metaknob to the Super Knob, and a power button that bypasses just that effect. Interactive FX slot cells showing the effect name appear beneath knobs 2–4; hover a cell to show its `◀` / `▶` shortlist arrows.
  - **Macro Knob Ergonomics & Display Modes**:
    - **Dual-Axis Drag & Shift Fine-Tuning**: Drag vertically or horizontally (up/right increases, down/left decreases). Holding **Shift** engages 6x fine-tuning resolution (1200px full sweep) for high-precision decimal tuning without cursor jumps on Shift toggle. Cursor locking (`lockCursorOnKnobDrag`) prevents the mouse from leaving the window on large sweeps.
    - **Bipolar & Endless Modes**: Knobs adapt their visual geometry to the underlying parameter's `MeterType`:
      - *Monopolar* (standard 7:30 to 4:30 sweep).
      - *Bipolar* (12 o'clock center detent, sweeping left for negative or right for positive; middle-click resets to 0.5 center). Focused FX and Deck parameters with negative ranges (e.g. shifts, pans, zooms) render in bipolar mode.
      - *Endless* (continuous 360° circle where 0 and 1 meet at 6 o'clock straight down, sweeping continuous angles for rotation and hue).
    - **Double-Click Direct Numeric Entry**: Double-click any macro knob face to open an inline text entry box. Type exact numerical values (e.g. `0.25`, `-1.5`, `42`) and press `Enter` to commit, or `Escape` to cancel. The typed value is what the parameter receives, so the knob's curve, invert and zone are taken into account (the readout shows the same value). For stepped or half-range bindings the knob snaps to the nearest position that gets closest.
    - **In-Face Readouts**: Hovering or dragging over a targetless or group-mode knob displays a subtle real-time value readout in the center of the knob face.
  - **Right Wing**:
    - **Randomize Die Button (`🎲`)**: Randomizes the source's modulators and base values, its Gain, Deck Zoom / Deck Rotate and the FX chain's Dry/Wet (parameters with randomization turned on), with undo of both the values and the knob positions (when randomization is enabled). Every stock source ships with random ranges on its look parameters (see Modulation, "Built-in random ranges"), and the source knobs move with the roll. Positioned on Row 1 directly above the FX ON button.
    - **`[ FX ON ]`**: Turns the deck's FX chain on or off; lit while on. Positioned on Row 2 aligned with the FX row.
  - **Drag-and-Drop**: Dropping a deck preset (`.patch`, `.lsd`, `.json`) loads the visual preset; dropping a `.lsdfxchain` loads that FX chain onto the deck.
- **Whole-Rig Randomize (`[ ALL 🎲 ]`)**: Positioned at the top right of the performance matrix tab strip, pushing undo state and invoking `mixer.randomizeAll()` across all decks simultaneously.
- **Master FX row**: Left wing: the `FX`-captioned `M` badge, the chain name box and the chain controls line, as on a deck FX row; right wing: Master FX `[ FX ON ]`. Dropping a `.lsdfxchain` onto the `MASTER` badge loads it into Master FX.
- **Level faders on the deck tiles** (the thin strip beside each preview badge) share their value with the Master MST knobs: move the fader and the knob follows, turn the knob and the fader follows, last mover wins. A fader drag also mutes non-MIDI modulation on it and makes a Soft Takeover MIDI/OSC mapping on it pick up again instead of jumping. The handle line marks the fader's own value; the filled bar is the live level after modulation.
- **Master MST row**: The `M` badge carries an `MST` caption. Line 1 is the `Deck Levels & Master` box (click to edit the levels, right-click for level resets) and a `100%` reset button; its gear opens Edit on the MST side tab's LEVELS section (the master level, then each deck's level), which shows this row together with the Clock row; the knobs are the Deck A / B / BG and Master levels. The crossfader is not here: it lives on the Transitions row (knob 1) and the Mixer panel.
- **Transitions (Row 2 in MASTER)**: Left wing features a Transition Picker button displaying the active transition with modified indicator (`*`) — click it to pick from the transition list in the Library below — and Transition Queue stepping controls (`<`, `N/Total`, `>`); right wing hosts the transition randomize die button (`🎲`) on Row 1 (matching the deck rows). Transition presets (`.lsdtrans`) and shaders (`.fs`/`.isf`) can be dropped directly onto the picker button.
- **Clock & Global (Row 4 in MASTER)**: The two lines beside the `CLOCK` badge have the tempo controls from Preferences > Tempo & Sync, placed here for use mid-set:
  - **`[ MAN ]` / `[ AUDIO ]`**: clock source (manual tempo or the audio beat tracker).
  - **`[ LINK n ]`**: the third button of the top line, with the peer count while Ableton Link is on and dim while it is off. Click it to open Tempo & Sync preferences.
  - **BPM readout**: click it to open Tempo & Sync preferences. The **beat dots** under it (one per beat of the bar, 4 by default) show the bar phase; the downbeat is cyan. Hover for the detector's confidence when Downbeat is on Auto.
  - **`[ TAP ]`**: tap tempo. Right-click for MIDI Learn. In Audio mode it nudges the detected tempo and phase. The first tap of a run also marks beat 1 of the bar.
  - **`[ SNAP ]`**: makes the beat nearest now beat 1 of the bar, snapping the beat phase and the bar line together. Press it on the one when the bar dots' cyan dot is in the wrong place. Preferences > Tempo & Sync > **Bar & Downbeat** sets the beats per bar and can let the app find beat 1 from the music (Downbeat: Auto); SNAP, or the first tap of a run, overrides it for 16 bars.
  - **Twister clock knob**: knob 13 on a Midi Fighter Twister pulses its LED on the beat (orange on the downbeat), shows the tempo on its ring and taps tempo when pushed. See [Rings and LEDs](performance_controls.md#twister-lights).
  - **`[ - ][ + ]`**: nudge the tempo by 0.5 BPM. The pair takes the space of one button, so MAN, AUDIO, LINK, TAP, SNAP and the pair make a tidy 3 x 2 grid.
  - **Queue triggers (`QUEUE`)**: right of the tempo controls, captions `A/B`, `A/B FX`, `BG`, `BG FX` and `TRN` over each queue's buttons: `[◀] [▶]`, with a play / pause button beside the caption on the A/B, BG and TRN queues (it toggles that queue's auto-advance and is lit while it runs; the FX queues have none). They are the same triggers as the queue rows of Edit's MST › CTRL tab: right-click a button for MIDI or OSC Learn, and a mapping made there shows on the CTRL row and the other way round.
  - **Randomize triggers (`RAND`)**: while randomization is enabled (Preferences), at the right edge, `[A] [B] [ALL]` over `[BG] [PV]` randomize that deck (or everything), with the same Learn menu and Ctrl+Z undo as the decks' dice. On a window too narrow for them, they are the first thing to go.

  The BPM is deliberately not a knob, since one bump would shift the whole show. The row has no macro knobs; a configurable row of free "global" knobs that can target any parameter is planned for a later release.

### MIDI Learn in the Performance Matrix

Right-click any knob to arm it for MIDI Learn — a cyan pulsing ring appears around the knob,
identical to the Learn ring on Parameter rows. Send a CC message from any connected controller to map
it immediately. Right-click again while armed to cancel.

Interactive header controls in the Performance Matrix also support right-click MIDI & OSC Learn:
- **Deck A and Deck B Badges**: Right-click to Learn MIDI (`Global/snapDeckA`, `Global/snapDeckB`) or Learn OSC (`Mixer/snapDeckA`, `Mixer/snapDeckB`) to instantly snap the crossfader left or right.
- **Auto-Fade Button `[ AUTO ]` & Fade Speed Badge**: Right-click to Learn MIDI (`Global/autoFade`, `Mixer/xfadeSpeed`) or Learn OSC (`Mixer/xfadeSpeed`).
- **PlayQueue & BG Queue `<` and `>`**: Right-click to Learn MIDI (`Global/queuePrev`, `Global/queueNext`, `Global/bgQueuePrev`, `Global/bgQueueNext`).
- **Transition Queue `<` and `>`**: Right-click to Learn MIDI (`Global/transQueuePrev`, `Global/transQueueNext`) or Learn OSC (`Mixer/transQueuePrev`, `Mixer/transQueueNext`).
Controls actively armed for MIDI learn display a pulsing cyan highlight border. Active MIDI assignments are shown in tooltips.

### Interacting with knobs

- **Drag** (up/down or left/right) to adjust the knob value. Dragging up or right increases the value; dragging down or left decreases it — the standard DAW/synth convention. Horizontal dragging makes it easy to adjust knobs located near the top edge of the screen or display boundary.
- **Mouse wheel** to fine-adjust (Shift for finer, Ctrl+Shift for coarser).
- **Hover/drag highlight**: the knob body tints toward the row's accent color on hover, and more
  strongly while you're dragging it, so the knob under your pointer is easy to spot.
- **Right-click** arms hardware MIDI Learn for that knob — the knob pulses cyan while armed, and
  the next CC message from your controller is mapped to it. Right-click again to cancel.

Knobs in the 4×4 matrix, the target strip and the hardware controller all read from and
write to the **same underlying `MacroEngine` banks** — changes in one are immediately visible in the others.

### The Modular Rack: Edit

Every deep-editable row group in the 4×4 matrix (Deck A/B/BG/PV, the FX row, Master, Transitions)
has a small **`[EDIT]` button** in its top-right corner (the Clock row has no knobs and no button). Clicking it toggles that row
between two disclosure tiers, without leaving the Perform view:

1. **Faceplate** (collapsed, the default) — just the 4 knobs, exactly like the plain 4×4 matrix
   above. The `[EDIT]` button has a dark fill and off-white text. Clicking a knob selects it (electric cyan focus card, glowing rim, cyan label), shows
   its current value (`Val: 0.00`) beneath the label, and reveals a compact `[Add Target]` / `[Cancel]`
   button for adding a target on the spot.
2. **Edit** — click `[EDIT]` to open the comprehensive Edit bay below the top macro row.
   The `[EDIT]` button turns green with black text while active.
   The bay is arranged into a 3-column layout:
   - **Side Rail** on the left: color-coded buttons (`[MST]`, `[OUT]`, `[A]`, `[B]`, `[BG]`, `[PV]`)
     allowing instant 1-click navigation between all major sections of the app without closing Edit.
     Selecting a side tab switches both the parameter editor below and the top macro row above.
   - **Parameter Grid** in the center: displays the full **VAL / MIDI / LFO / SEQ / AUD** parameter grid
     with section subtabs across the top. Parameters show the shader's own label, or a readable form of the name
     (`CellScale` reads "Cell scale"). Above the grid sits the same header line as the pair view: **Back**, a
     **Browse | Params** switch (Browse shows the list for this deck and half in the pair view), the module's name and
     what the queue plays next. Each side tab has its own section tabs:
     - **`MST`**: `[ LEVELS ]  [ CTRL ]` (the master level, then each deck's level; and the queue and randomize triggers, which are also on the Clock row)
     - **`OUT`**: `[ FX ]  [ TRANS ]` (the Master FX chain's 3 ISF slots, and Transitions, led by crossfade and fade speed)
     - **`A`**, **`B`**, **`BG`**, **`PV`**: `[ SRC ]  [ FX ]` (visual generator with Gain/Zoom/Rotate Z, and the insert FX chain)
     Edit shows the open unit's two rows above the editor, as the Pair view does: a deck's source and FX rows, Master's Transitions and Master FX rows (`OUT` side tab), or Master's MST and the Clock row (`MST` side tab): in each case the two rows that share a half of a Twister page. The rows stay put when you switch sections; the **gear** that is lit marks the row the editor is on, and clicking the other row's gear moves the editor there (click the lit one to close Edit). Both rows' macro knobs stay live.
   - **Modulation Editor** on the right: side-by-side per-parameter CV detail editor (LFO period/phase/morph/hold/slew, MIDI, SEQ, AUD, curves, and modulators) of whichever cell is selected. Opening Edit or switching tabs selects the first knob-assigned parameter (or the first row), so it is never empty.

   If the deck is **empty**, Edit shows the empty-deck card instead: **Add Source** (opens
   Browse, see below, including external video), **Load Preset**, and **Open Library Panel**.

### Browse: picking a generator, FX or transition

Clicking a generator/preset badge, an FX chain's name, an FX slot's name, or the active transition's
name (or a preview monitor) points the **Library below** at that slot: nothing moves, the slot is outlined
on its row, and its name shows on the Library's tab line with a **✕**. **Esc**, the ✕, or clicking the slot
again stops. When the current page doesn't show that row (say, the BG monitor from the A/B page), the **pair
view** opens instead: that deck's SRC and FX rows (or Transitions and Master FX) stay on
screen, every other row hides, and the Browse list fills the space below. Nothing covers the mixer or the
deck/master monitors; you keep watching the show while you pick. The pair view's header line has **Back**
(or **Esc**) to return to Perform and a **Browse | Params** switch that opens the full editor for the half you last touched.

- **Instant apply, list stays open**: clicking an item in the list applies it immediately and the
  list doesn't close, so you can try several generators, effects, or chains back-to-back. `Ctrl+Z`
  undoes any one pick. A double-click applies and stops (back to the plain Library, or out of the pair view).
  Changing a deck's source while a named/dirty preset is loaded prompts once; the answer is reused
  for the next picks on that deck.
- **The half you touched picks the list**: click a row's source badge, chain name, an effect cell or
  the transition name, or turn a knob on that row, and Browse applies to that target (a deck's source,
  a chain, one FX slot, the transition). the Master MST row and the Clock row have nothing to browse (they are not a pair); the Master
  pair (Transitions + Master FX) with nothing touched browses the Master FX chain. On Edit's MST side tab the Browse button is disabled.
- **Source Browse lists sources and presets together**: stock sources and saved presets
  always appear in the same list — a preset is just a source with its parameter values saved
  under a name. Saved-preset rows get a "⋮" menu (also on right-click) that starts with **Apply to <deck>**,
  then **Rename...**, **Clone...**, **Delete...** and **Reveal in File Manager**, and a floppy-disk
  **Save / Save As** button sits beside the target's name — the same save flow
  as the Mixer's Save button and `Ctrl+Shift+S` — so managing presets no longer requires the Library view.
- **FX Browse** applies to either a whole chain (the saved `.lsdfxchain` files) or one slot's effect
  (stock filters, ★ favorites, saved single-FX presets), each opening on its usual folder.
- **The same browser as the Library**: it has the Library's own **Sources / FX / Transitions / Queues / Macros** tabs,
  folder tree and list. The target's name, such as "● Deck A · FX 1" (the slot, chain or transition you are
  editing), says what a click applies to: on that tab a click applies, the row that is applied (stock or saved)
  is tinted across the whole row, and the list shows only what the target can take. Pick another tab and the name
  greys out ("paused"): it is the plain Library again (double-click loads to the inactive deck).
  Each target remembers the folder you last used there. Search with `Ctrl+F` or `/`. **Clear Slot** /
  **Clear Chain** empties the target.
  The Sources tree also has an **External video** folder listing the live video streams running right now.

**Keyboard shortcuts in Edit**: `Ctrl+C` / `Ctrl+V` (copy/paste a cell or row), `Delete` /
`Backspace` (clear the cell's modulators, or reset the parameter), and `Ctrl+S` / `Shift+Ctrl+S`
(save / save-as the deck being edited) act on the open Edit. Copy/paste/clear only fire while the
Perform view has focus, so `Delete` in the Library doesn't also reset a parameter. `Ctrl+Z`
(undo) works anywhere in the Perform view, with or without Edit open.

**While in Edit**, the top macro row renders the macro controls corresponding to the active channel and subtab,
reserving the freed vertical space for the side rail and parameter bay. The row keeps the same knob size and control
positions it has in the four-row view; it only grows by one line under each knob for the value readout (and the
**Add Target** button under the selected knob). There's no separate title above the parameters, since the row says which
deck and section you're editing.
Collapsing back to Faceplate brings the rest of the 4×4 grid, and the Library, back. While
Edit is open the Library is hidden completely (the **Edit** view — see
[Your Workspace](your_workspace.md)).

Click **`[EDIT]`** again (or press Esc) to fold back to the Faceplate.

**Opening Edit from Confidence Monitors**:
Clicking any preview monitor in the Mixer column (Deck A, Deck B, Deck BG, Deck PV, or Main Output Master) points the Library at that deck's source (or the Master FX chain), or opens the pair view when the page doesn't show those rows. For the full editor use the row's gear, or **Params** in the pair view's header.

**One Edit at a time**: opening a row's Edit collapses any other open one. To move
between decks, use the Edit side rail (**MST / OUT / A / B / BG / PV**) rather than opening
rows side by side.

**Esc** collapses every expanded row back to the Faceplate — unless a Learn is currently
armed (Add Target, MIDI Learn or OSC Learn), in which case Esc cancels every armed Learn at once instead (a second Esc then collapses the rack). A
row whose own knob has an armed Add Target is also exempt from the auto-collapse, so it can't
be accidentally folded away mid-way; a **"Adding target: ‹name› — Esc to cancel"** indicator stays
visible in the toolbar the whole time Add Target is armed, even if you've expanded a different row.
**View → Close Edit** does the same as the row's `[EDIT]` button.

Expanding or collapsing a row is purely a display change — it never re-syncs the Super Knob/Metaknob mapping.

### Setting up knob labels and targets

Targets are edited in the **Edit** view, in the target strip described above (rename,
target chips, Min/Max/Curve/Invert/Link/Enabled) and in the Modulation editor of the open Edit.
Clicking a target's name opens that deck's (or the Mixer's) Edit on the right sub-tab
with the parameter selected.

- **From the 4×4 matrix**: click a knob to select it, then click **`Add Target`** (on the target strip,
  or the inline button in the Perform view). This arms Add Target and opens that row's
  **Edit** *Params* — click a target parameter and set Min/Max/Curve as desired. The target
  must be in the knob's own deck and section (see *Adding targets to a knob* above).

The change is live immediately everywhere, since all views share the same `MacroEngine` banks.

---

## Macro Banks

There are 12 always-resident canonical macro banks (4 knobs each, conforming to the 4-column performance grid):

| Scope | Bank | Knobs | Canonical ID | Smart Defaults |
|:---|:---|:---|:---|:---|
| **Deck Generators** | Deck A | 4 | `deckA` | Blank |
| | Deck B | 4 | `deckB` | Blank |
| | Deck BG | 4 | `deckBG` | Blank |
| | Deck PV | 4 | `deckPV` | Blank |
| **Per-Deck Insert FX** | Deck A FX | 4 | `deckA_fx` | Super Knob + 3 Metaknobs |
| | Deck B FX | 4 | `deckB_fx` | Super Knob + 3 Metaknobs |
| | Deck BG FX | 4 | `deckBG_fx` | Super Knob + 3 Metaknobs |
| | Deck PV FX | 4 | `deckPV_fx` | Super Knob + 3 Metaknobs |
| **Mixer & Transitions**| Transitions | 4 | `trans` | Crossfade, Type, Speed, Next |
| | Master | 4 | `master` | Level A, Level B, Master Level, Crossfader |
| | Master FX | 4 | `masterFx` | Super Knob + 3 Metaknobs |

Deck generator banks initialize from their generator's default preset. Master, Transitions, and FX banks initialize with pre-mapped smart defaults. The five FX banks target their chain's `Deck A/FX/...` … `Master/FX/...` parameters and re-sync automatically whenever that chain's contents change (loading a chain, swapping a slot, restoring a session) — FX knobs are fixed (Super + 3 Metaknobs, or Metaknob + 3 parameters when focused) and can't be retargeted per knob; change what a Metaknob controls with its **Retarget** menu. Sessions saved with hand-retargeted FX knobs lose those targets on load. All 12 canonical banks are preserved in `last_session.json` and session files.

Banks are saved in `last_session.json` and bundled into preset files automatically.

---

## Generator & FX Defaults ("Save as Default")

Similar to Ableton's default presets, you can configure your favorite parameter starting points and 4-knob macro layouts for any visual generator or ISF FX filter, and save them as that device's permanent default.

### Visual Generator Defaults

When swapping sources on any deck (via the source badge or the empty-deck card), the incoming source's default configuration is resolved across three tiers:
1. **User Default**: Custom parameter baselines, `globalAlpha`, and 4-knob macro layout saved in `library/generator_defaults/<sourceId>.json`.
2. **Stock Default**: the default shipped with each bundled generator: its 4-knob macro targets and its built-in audio and beat responses (`mandala`, `dynamic_spiral`, `icosa_h3`, `domain_warp_fluid`, `gyroid_hyperspace`, `celestial_engine`, `hyper_slice`, `chladni_cymatics`, `mandelbox`, `kifs`, `mandelbulb`, `pseudo_kleinian`, `apollonian`, `escape_time`, `nebula_volume`, `reaction_diffusion`, `fluid_ink`, `lenia`, `quasicrystal`, `thin_film`, `tunnel_scenes`, `video_feedback`, `hyperbolic_tiling`, `cyclic_ca`, `metaballs`, `wave_ripples`, `aurora_ribbons`, `isometric_voxels`, `halftone_matrix`, `kali_set`, `girih_stars`, `glitch_blocks`, `voronoi_cells`).
3. **Automated Heuristic Fallback**: For third-party ISF shaders (nothing is written to disk; it is worked out again on every load until you save a default yourself), continuous floats (e.g. speed, rate, zoom, scale, morph, depth, detail) are prioritized, discrete stepped selectors are filtered out, and speed/frequency parameters are shaped with exponential response curves.

**Bank Replacement Behavior**: Applying a default replaces the resident 4-knob deck bank wholesale, identical to preset loading. Stored `Deck/*` targets are dynamically remapped to the target deck slot (e.g., `Deck A`, `Deck B`). Knobs are positioned via inverse-curve mapping so parameter values do not jump on load. Hardware MIDI mappings are stripped on save to avoid duplicate hardware CC collisions across decks.

**How to Save or Reset Generator Defaults**:
- **Right-click the generator badge** on any deck row in the Perform view to open the context menu:
  - `Save as Default for <Generator>`: Saves current parameters, Gain, and 4-knob macro layout as the generator default.
  - `Apply Default Now`: Reapplies the default layout and parameters to the current deck.
  - `Reset to Factory Default`: Deletes the user default file and restores the stock (or heuristic) default.
- Alternatively, open the **Save menu** in Edit's monitor toolbar and select `Save as Default for <Generator>`.

### Explicit ISF FX Defaults

A slot's Metaknob targets, knob order and parameter values can be saved as the effect's own default:
- Tweaking parameters or targets during performance is purely temporary and does **not** silently overwrite defaults on disk.
- **Right-click a Metaknob row** in Edit's SUPER KNOB & METAKNOBS block:
  - `Save as Default for <Filter>` saves this slot's Metaknob targets, knob order, parameter values and modulators (not MIDI mappings) into `library/isf_overrides/<contentHash>.json`. New copies of the effect start from it; slots already in chains keep their own.
  - When a saved default exists, `Remove Saved Default for <Filter>` deletes it, so new copies go back to the stock (or auto-mapped) default, and resets this slot to it too.
- Targets themselves are edited in the slot's **Metaknob targets** block or on the Edit-row strip.

---

## Shortcuts

| Key | What it does |
|-----|-------------|
| `Esc` | Cancel every armed Learn (Add Target, MIDI, OSC), or (if none is armed) stop browsing a slot, or collapse every expanded Rack row back to Faceplate |
