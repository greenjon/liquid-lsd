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
is cancelled. That includes Edit's SRC/FX tabs, the side rail, and a Deck
row's `[SRC]`/`[FX]` pills. Master, Transitions and FX Sends knobs aren't limited this way.

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
- **Live Value Meter Knob** — on the second row of each target (to the left of Min/Max, Link Mode, and Curve), a rotary meter knob (the same crisp knob widget used in the parameter grid) displays the live evaluated output value in relation to the macro knob position. If you use a Triangle ramp (peak) curve or a half-turn zone, moving the macro knob from 0 to 1 lets you see the binding sweep (e.g. 0 → 1 → 0) in real time. Hovering over the knob displays exact numerical readouts.
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

## FX Rack (per-deck FX tabs)

Each deck and Master now has its own **independent FX chain**. Selecting **A FX**, **B FX**,
**BG FX**, **PV FX**, or **MST FX** (the FX mode of a Performance row) shows a
dedicated **FX Rack** view — a Traktor/Mixxx-style Super Knob + Metaknob strip for that deck's
own FX chain. Unlike the old shared FX1/FX2 banks, each deck's FX chain is always available
regardless of what the other decks are doing.

Edit and the macro strip always point at the same deck and section:

- Opening a deck's Edit anywhere (its row's `[EDIT]`, the side rail, the **SRC / FX** tabs)
  focuses that deck and section.
- A Performance row's `[SRC]` / `[FX]` pill also switches that deck's section.

The same applies to the other decks and Master (**MST**, **TRANS** and **MST FX** match Edit's
Mixer **CTRL**, **TRANS** and **FX** tabs).

### Who wins when several things drive the same value

A parameter's base value can be driven from several places. From strongest to weakest:

1. **A macro knob target** locks the value. Sliders are read-only, and any MIDI or OSC mapping on it is **suspended** (its row in Preferences shows "Suspended: driven by ...") and works again when you release the target.
2. **An effect Metaknob link** (including the Super Knob, which moves linked Metaknobs) owns the effect parameter it is aimed at. A macro knob can't be added to that parameter ("driven by its Metaknob"), and an existing macro target on it is ignored while the link is enabled. Disable the Metaknob link (right-click the Metaknob) to use a macro knob there instead. MIDI and OSC mappings on it are suspended as well.
3. **Direct edits, MIDI and OSC mappings** on an unlocked value: the last one to move wins.
4. **Modulators (LFO, sequencer, audio)** never compete. They are always added on top of whatever value results from 1-3.

### Chain Super Knob & effect Metaknobs

- **Super Knob** — one knob that sweeps every **linked** slot's own Metaknob together.
- **Metaknob** (one per slot) — the effect's single macro control. For a hand-curated effect
  (the bundled filters) it's tuned to feel right out of the box. For any other ISF shader —
  including the thousands available from third-party shader packs — it's auto-mapped the first
  time the shader loads, using the shader's own declared `IDENTITY`/parameter metadata, or a
  Dry/Wet fallback if nothing better is found. You never get a dead knob.
- **Slot cells** — on a Performance row in FX mode, each Metaknob has no caption; the slot cell
  right under it names the effect in that slot. Only the Super Knob keeps its `SUPER` caption.
- **Link** (per slot) — toggles whether that slot's Metaknob follows the Super Knob. Turning Link
  back on doesn't snap the Metaknob to wherever the Super Knob currently sits — it waits until you
  move the Super Knob far enough for it to reach the Metaknob's current position first (soft
  pickup), the same behavior used for hardware MIDI/OSC takeover elsewhere in the app.
- **Focus Mode** (per slot) — focuses on an individual effect slot across the Performance Matrix FX rows.
  - **Knob 1**: The focused slot's Metaknob. The slot's **Dry/Wet** is the `Wet` slider in the chain header (middle-click resets to 100%).
  - **Knobs 2–4**: Retargeted to the focused effect's top parameters on the active page, with parameter paging (`[◀ P1/N ▶]`) when more than 3 parameters exist.
  - **Parameter Cells (`FxParamCell`)**: Display parameter values with a reset-to-default button (counter-clockwise arrow).
  - **Hardware MIDI**: Physical controllers mapped to `Macro/<bankId>/knob_1..4` keep their paths; in Focus Mode they control the focused slot's Metaknob and parameters. The layout is fixed per mode (Mixxx-style), so a mapped CC changes meaning when you toggle focus.
  - Click `[◀ CHAIN]` in the header, or the active slot pill, to return to standard Group Mode.
- **Right-click a Metaknob** to retarget it to a different parameter, to the Dry/Wet safety net, or
  to reset it back to the auto-mapped default.

### Hardware control note

Every knob in the strip (Super Knob, each Metaknob, and each focused-mode parameter row) supports
the same right-click **Learn MIDI** / **Learn OSC** as any other slider in the app — each has its
own fixed control path. **This means a physical knob you've mapped to a slot's Metaknob in Group
Mode will *not* automatically control that slot's parameters once you switch to Focus Mode** (and
vice versa) — they're different paths, so you'd map the physical knob again for the focused view
if you want it there too. A true Traktor/Mixxx hardware unit keeps the same 4 physical knobs
mapped regardless of focus state; matching that exactly would need new plumbing this pass didn't
build. If this turns out to matter in practice for your hardware workflow, it's a known follow-up.

---

## The Perform View (4×4 Matrix)

The Perform view is the app's main view: a **4×4 Macro Knob Matrix** — 16 knobs arranged in 4
rows across 4 columns, color-coded by deck — on the left, with the Mixer column and
Library dock alongside. (Earlier versions also had a "Classic" Parameters/Properties view,
toggled with `F4`; everything it did now lives in Edit, below.)

The active tab is remembered between sessions in application preferences.

### The layout tabs

Each tab shows up to 4 rows of 4 knobs, mapped to different banks:

| Tab | Row 1 | Row 2 | Row 3 | Row 4 |
|:---|:---|:---|:---|:---|
| **DECKS** | Deck A `[SRC\|FX]` | Deck B `[SRC\|FX]` | Deck BG `[SRC\|FX]` | Deck PV `[SRC\|FX]` |
| **MASTER** | Master `[MIX\|FX]` | Transitions | FX Wet/Dry | Clock & Global |

Every FX chain is reached from its own row: a deck's FX from that deck's row, Master FX from the
Master row. There are no separate FX-only rows.

- **Per-Deck Insert FX & Flexible Routing**: Every deck (A, B, BG, PV) now owns its own dedicated 3-slot `FxChain` running post-generator and pre-crossfader, completely decoupled from other decks.
- **In-Row `[ SRC | FX ]` Knob Assignment & Stacked Controls (Deck Rows)**: In each Deck row, both visual generator and insert FX chain controls coexist in two stacked rows on the left wing. Click the **`[ SRC ]`** or **`[ FX ]`** knob-assign pill to choose which bank the row's 4 on-screen knobs control (`SRC` for visual generator macros, `FX` for that deck's insert FX Super Knob + 3 Metaknobs). Switching between `SRC` and `FX` retargets on-screen knobs without hiding or toggling away either row's controls. The right wing provides an instant `[ BYPASS / FX ON ]` button for that deck's FX chain.
- **In-Row `[ MIX | FX ]` Knob Assignment (Master Row)**: The Master row works like a deck row. Its left wing stacks a **`[ MIX ]`** pill over an **`[ FX ]`** pill and the Master FX chain header (`[◀] Name • [▶] [Save] [⋮]`). `MIX` puts the knobs on the composite alphas and master level; `FX` puts them on the Master FX Super Knob + 3 Metaknobs. The right wing has the Master FX `[ BYPASS / FX ON ]` button. The crossfader sits on the `[ MIX ]` line in both modes.
- The Transitions row carries the transition picker (opens inline Browse — see below) and queue prev/next controls.

Each row is color-coded to its deck or target (blue for Deck A, orange for Deck B, amber for Deck BG, mint for Deck PV, violet for Transitions, crimson for Master, teal for FX) and starts with a title badge spanning both control lines: a large **A**, **B**, **BG** or **PV** on deck rows (the `[SRC]`/`[FX]` pills beside it show which the knobs control), and **MASTER**, **TRANS**, **WET/DRY** and **CLOCK** on the MASTER tab. No row has a header bar over its knobs, so MASTER-tab knobs are the same size as deck knobs.

### Performance Controls & Side-Wing Layout

To maximize vertical space in the matrix and keep the knobs comfortably clustered together:
- **Row Titles & Side-Wing Controls**: Row group titles (e.g. `DECK A`, `DECK A (FX)`) sit at the top-left of the row, level with the top of the knobs. The left- and right-wing controls sit below the title, with their bottom edge lined up with the bottom of the knobs, so short rows never push the controls up into the title. Every wing control (buttons, badges, generator/preset badge) is the same height.
- **Row Height & Scrolling**: Rows share the panel's height, so they shrink as you drag the Library dock taller. Once rows reach a minimum height (about where the Library is at half height), they stop shrinking and the matrix scrolls vertically instead. Two usable rows plus a scrollbar beat four knobs too small to grab. Scroll with the scrollbar, with the mouse wheel over the gaps between knobs (over a knob the wheel still adjusts that knob), or by click-dragging up/down on any empty part of a row, including its title band.
- **Deck Rows (Deck A, B, BG, PV)**:
  - **Left Wing (Info & Deck Controls)**:
    - **Row 1 (SRC)**:
      - **`[ SRC ]` Knob Pill**: Assigns the row's 4 on-screen macro knobs to the deck's Visual Generator.
      - **Generator / Preset Badge**: One control, not two -- a preset is just a generator with its parameter values saved under a name. Shows the active preset's name (with a dirty marker `*`) or, if nothing's been saved, the generator's own name. Click to open inline Browse listing stock generator types and saved presets together; right-click for source defaults.
      - **Eject Button (`⏏`)**: Resets the deck to defaults with dirty-state safety guard.
      - **Queue Navigation**: Deck A and Deck B connect to `PlayQueueManager` (`< N/Total >`), Deck BG connects to `BgQueueManager` (`< N/Total >`), and Deck PV features a quick Preview focus button.
      - **Save Button**: Saves current visual source and parameters as a preset.
      - **Kebab Menu (`⋮`)**: Source operations (Browse, Save As, defaults).
    - **Row 2 (FX)**:
      - **`[ FX ]` Knob Pill**: Assigns the row's 4 on-screen macro knobs to the deck's insert FX chain (Super Knob + 3 Metaknobs).
      - **Dedicated Chain Controls**: Step through chains (`◀` / `▶`), active chain name with dirty dot (`•`) — click it to open inline Browse on the whole-chain list — `[Save]` button, and kebab menu (`[⋮]`) for Save As, Revert, Copy/Paste, and Resync.
  - **Knob Columns**: The 4 macro knobs are uniformly spaced across the center area between the control wings, sharing the exact same column pitch and horizontal alignment whether assigned to `SRC` or `FX`. When assigned to `FX`, each of knobs 2–4 gets two stacked buttons on its left: a link button (`Icons.LINK`/`Icons.UNLINK`) that links/unlinks its Metaknob to the Super Knob, and a power button that bypasses just that effect. Interactive FX slot cells showing the effect name appear beneath knobs 2–4; hover a cell to show its `◀` / `▶` shortlist arrows.
  - **Macro Knob Ergonomics & Display Modes**:
    - **Dual-Axis Drag & Shift Fine-Tuning**: Drag vertically or horizontally (up/right increases, down/left decreases). Holding **Shift** engages 6x fine-tuning resolution (1200px full sweep) for high-precision decimal tuning without cursor jumps on Shift toggle. Cursor locking (`lockCursorOnKnobDrag`) prevents the mouse from leaving the window on large sweeps.
    - **Bipolar & Endless Modes**: Knobs adapt their visual geometry to the underlying parameter's `MeterType`:
      - *Monopolar* (standard 7:30 to 4:30 sweep).
      - *Bipolar* (12 o'clock center detent, sweeping left for negative or right for positive; middle-click resets to 0.5 center). Focused FX and Deck parameters with negative ranges (e.g. shifts, pans, zooms) render in bipolar mode.
      - *Endless* (continuous 360° circle where 0 and 1 meet at 6 o'clock straight down, sweeping continuous angles for rotation and hue).
    - **Double-Click Direct Numeric Entry**: Double-click any macro knob face to open an inline text entry box. Type exact numerical values (e.g. `0.25`, `-1.5`, `42`) and press `Enter` to commit, or `Escape` to cancel. The typed value is what the parameter receives, so the knob's curve, invert and link mode are taken into account (the readout shows the same value). For stepped or half-range bindings the knob snaps to the nearest position that gets closest.
    - **In-Face Readouts**: Hovering or dragging over a targetless or group-mode knob displays a subtle real-time value readout in the center of the knob face.
  - **Right Wing**:
    - **Randomize Die Button (`🎲`)**: Instantly randomizes that deck's modulators & base values across both Visual Source and Insert FX with undo support (when randomization is enabled). Positioned on Row 1 directly above the BYPASS button.
    - **`[ BYPASS / FX ON ]`**: Dedicated insert FX kill switch, positioned on Row 2 aligned with the FX row.
  - **Drag-and-Drop**: Dropping a deck preset (`.patch`, `.lsd`, `.json`) loads the visual preset; dropping a `.lsdfxchain` loads that FX chain onto the deck.
- **Whole-Rig Randomize (`[ ALL 🎲 ]`)**: Positioned at the top right of the performance matrix tab strip, pushing undo state and invoking `mixer.randomizeAll()` across all decks simultaneously.
- **Master (Row 1 in MASTER)**: Left wing: `[ MIX ]` over `[ FX ]` + Master FX chain header, as described above; right wing: Master FX `[ BYPASS / FX ON ]`. Dropping a `.lsdfxchain` onto the `MASTER` badge loads it into Master FX. The `[ MIX ]` line holds the crossfader: Deck A and Deck B quick-snap buttons (`[ A ]` and `[ B ]`), an interactive zero-centered crossfader track with mouse drag, mouse wheel, middle-click center reset, and live amber modulation/auto-fade indicator dot, a smooth `[ AUTO ]` crossfade trigger, and an interactive **Fade Speed Duration Badge** (crossfader time -- scrubbable with mouse drag/wheel and right-click context menu for duration presets `0.5s`–`8.0s` and MIDI/OSC learn). Transition presets (`.lsdtrans`) and shaders (`.fs`/`.isf`) dropped onto the crossfader track apply directly.
- **Transitions (Row 2 in MASTER)**: Left wing features a Transition Picker button displaying the active transition with modified indicator (`*`) — click it to open inline Browse on the transition list — and Transition Queue stepping controls (`<`, `N/Total`, `>`); right wing hosts the transition randomize die button (`🎲`) on Row 1 (matching the deck rows). Transition presets (`.lsdtrans`) and shaders (`.fs`/`.isf`) can be dropped directly onto the picker button.
- **Clock & Global (Row 4 in MASTER)**: The two lines beside the `CLOCK` badge have the tempo controls from Preferences > Tempo & Sync, placed here for use mid-set:
  - **`[ MAN ]` / `[ AUDIO ]`**: clock source (manual tempo or the audio beat tracker).
  - **`[ LINK n ]`**: shown while Ableton Link is on, with the peer count. Click it to open Tempo & Sync preferences.
  - **BPM readout**: click it to open Tempo & Sync preferences.
  - **4 beat dots**: bar phase; the downbeat is cyan.
  - **`[ TAP ]`**: tap tempo. Right-click for MIDI Learn. In Audio mode it nudges the detected tempo and phase.
  - **`[ RESYNC ]`**: snaps the beat phase to the downbeat.
  - **`[ /2 ]` `[ x2 ]`**: halve or double the tempo.
  - **`[ - ]` `[ + ]`**: nudge the tempo by 0.5 BPM.

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
has a small **`[EDIT]` button** in its top-right corner (FX Wet/Dry is a dedicated 4-knob macro row with no button). Clicking it toggles that row
between two disclosure tiers, without leaving the Perform view:

1. **Faceplate** (collapsed, the default) — just the 4 knobs, exactly like the plain 4×4 matrix
   above. The `[EDIT]` button has a dark fill and off-white text. Clicking a knob selects it (electric cyan focus card, glowing rim, cyan label), shows
   its current value (`Val: 0.00`) beneath the label, and reveals a compact `[Add Target]` / `[Cancel]`
   button for adding a target on the spot.
2. **Edit** — click `[EDIT]` to open the comprehensive Edit bay below the top macro row.
   The `[EDIT]` button turns green with black text while active.
   The bay is arranged into a 3-column layout:
   - **5-Channel Side Rail** on the left: color-coded buttons (`[MIX]`, `[A]`, `[B]`, `[BG]`, `[PV]`)
     allowing instant 1-click navigation between all major sections of the app without closing Edit.
     Selecting a side tab switches both the parameter editor below and the top macro row above.
   - **Parameter Grid** in the center: displays the full **VAL / MIDI / LFO / SEQ / AUD** parameter grid
     with section subtabs across the top. All 5 sections feature a uniform 3-tab layout with **`FX` in the center**:
     - **`MIX`**: `[ CTRL ]  [ FX ]  [ TRANS ]` (Master controls, the Master FX chain's 3 ISF slots, and Transitions)
     - **`A`**, **`B`**, **`BG`**, **`PV`**: `[ SRC ]  [ FX ]` (visual generator with Gain/Zoom/Rotate Z, and the insert FX chain)
     Switching between `SRC`/`CTRL` and `FX` in Edit automatically switches the on-screen macro knobs (and corresponding `[SRC]` / `[FX]` pill highlight) between visual source controls and insert/master FX macros (`Super Knob + 3 Metaknobs`). Source and FX chain controls on the deck's performance row remain available simultaneously.
   - **Modulation Editor** on the right: side-by-side per-parameter CV detail editor (LFO period/phase/morph/hold/slew, MIDI, SEQ, AUD, curves, and modulators) of whichever cell is selected.

   If the deck is **empty**, Edit shows the empty-deck card instead: **Add Source** (opens
   Browse, see below, including external video), **Load Preset**, and **Open Library Panel**.

### Browse: picking a generator, FX or transition without leaving the row

Clicking a generator/preset badge, an FX chain's name, an FX slot's name, or the active transition's
name opens **Browse** in that row's bay — the same place Edit's parameter grid shows, with the
other rows collapsed the same way. Nothing covers the mixer or the deck/master monitors; you keep
watching the show while you pick.

- **Instant apply, list stays open**: clicking an item in the list applies it immediately and the
  list doesn't close, so you can try several generators, effects, or chains back-to-back. `Ctrl+Z`
  undoes any one pick. Changing a deck's source while a named/dirty preset is loaded still prompts
  the usual confirmation before discarding it.
- **Tab row**: the top of the bay has one row of tabs — `Edit | SRC | Chain | FX1 | FX2 | FX3` on a
  deck, and `Edit | TRANS | Chain | FX1 | FX2 | FX3` on Master. `Edit` is the parameter grid; the
  others browse that target. Pick a generator or effect, click `Edit` to tweak it, click the tab
  again to pick the next one.
- **Gen Browse lists generators and presets together**: stock generator types and saved presets
  always appear in the same list — a preset is just a generator with its parameter values saved
  under a name. Saved-preset rows get a "⋮" menu for **Rename / Edit Tags**, **Duplicate**, and
  **Delete**, and a floppy-disk **Save / Save As** button sits above the list — the same save flow
  as the Mixer's Save button and `Ctrl+Shift+S` — so managing presets no longer requires the Library.
- **FX Chain Browse** has **Chain / FX1 / FX2 / FX3** sub-tabs: **Chain** lists the saved
  `.lsdfxchain` files; **FX1**–**FX3** are that chain's per-slot effect pickers (stock filters, ★
  favorites, saved single-FX presets), each opening on its slot's usual folder.
- **The same browser as the Library**: every Browse tab shows the Library's folder tree, list and
  queues. Pick a folder or playlist in the tree, search in the list (`Ctrl+F` or `/`), and click a row
  to apply it; the row that is applied shows a ●. Each tab remembers the folder you last used there
  and lists only what it can take. **Clear Slot** / **Clear Chain** above the browser empties the
  target, and **External video...** (SRC tab) picks a live video stream.

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
In addition to the row's `[EDIT]` button, clicking any preview monitor in the Mixer column (Deck A, Deck B, Deck BG, Deck PV, or Main Output Master) will immediately open Edit focused directly on that module. This swaps the Edit bay from your current deck to the clicked deck without needing to scroll or find the row's button.

**One Edit at a time**: opening a row's Edit collapses any other open one. To move
between decks, use the Edit side rail (**MIX / A / B / BG / PV**) rather than opening
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
| **Master FX** | FX Wet/Dry | 4 | `fxSends` | Deck A, B, BG, PV Insert FX Wet/Dry Levels |
| | Master FX | 4 | `masterFx` | Super Knob + 3 Metaknobs |

Deck generator banks initialize from their generator's default preset. Master, Transitions, and FX banks initialize with pre-mapped smart defaults. The five FX banks target their chain's `Deck A/FX/...` … `Master/FX/...` parameters and re-sync automatically whenever that chain's contents change (loading a chain, swapping a slot, restoring a session) — FX knobs are fixed (Super + 3 Metaknobs, or Metaknob + 3 parameters when focused) and can't be retargeted per knob; change what a Metaknob controls with its **Retarget** menu. Sessions saved with hand-retargeted FX knobs lose those targets on load. All 12 canonical banks are preserved in `last_session.json` and session files.

Banks are saved in `last_session.json` and bundled into preset files automatically.

---

## Generator & FX Defaults ("Save as Default")

Similar to Ableton's default presets, you can configure your favorite parameter starting points and 4-knob macro layouts for any visual generator or ISF FX filter, and save them as that device's permanent default.

### Visual Generator Defaults

When swapping visual sources on any deck (via the generator badge or launchpad), the incoming generator's default configuration is resolved across three tiers:
1. **User Default**: Custom parameter baselines, `globalAlpha`, and 4-knob macro layout saved in `library/generator_defaults/<sourceId>.json`.
2. **Curated Stock Default**: Hand-curated 4-knob macro targets for bundled generators (`mandala`, `dynamic_spiral`, `icosa_h3`, `domain_warp_fluid`, `gyroid_hyperspace`, `celestial_engine`, `hyper_slice`, `chladni_cymatics`).
3. **Automated Heuristic Fallback**: For third-party ISF shaders, continuous floats (e.g. speed, rate, zoom, scale, morph, depth, detail) are prioritized, discrete stepped selectors are filtered out, and speed/frequency parameters are shaped with exponential response curves.

**Bank Replacement Behavior**: Applying a default replaces the resident 4-knob deck bank wholesale, identical to preset loading. Stored `Deck/*` targets are dynamically remapped to the target deck slot (e.g., `Deck A`, `Deck B`). Knobs are positioned via inverse-curve mapping so parameter values do not jump on load. Hardware MIDI mappings are stripped on save to avoid duplicate hardware CC collisions across decks.

**How to Save or Reset Generator Defaults**:
- **Right-click the generator badge** on any deck row in the Perform view to open the context menu:
  - `Save as Default for <Generator>`: Saves current parameters, alpha, and 4-knob macro layout as the generator default.
  - `Apply Default Now`: Reapplies the default layout and parameters to the current deck.
  - `Reset to Factory Default`: Deletes the user default file and restores curated/heuristic factory defaults.
- Alternatively, open the **Save menu** in Edit's monitor toolbar and select `Save as Default for <Generator>`.

### Explicit ISF FX Defaults

In the FX Chain Macro Strip (and Edit FX views), slot Metaknobs and parameters can be customized and explicitly saved:
- Tweaking parameters or re-targeting Metaknobs during performance is purely temporary and does **not** silently overwrite defaults on disk.
- **Right-click any Metaknob** in the FX strip:
  - Select any parameter from the list (or `Target Dry/Wet (safety net)`) to retarget the Metaknob.
  - Choose `Save as Default for <Filter>` to persist the current Metaknob targets and parameter baseline into `library/isf_overrides/<contentHash>.json`.
  - When a user default is present, choose `Reset to Factory Default` to revert to stock curated/heuristic targets.

---

## Shortcuts

| Key | What it does |
|-----|-------------|
| `Esc` | Cancel every armed Learn (Add Target, MIDI, OSC), or (if none is armed) collapse every expanded Rack row back to Faceplate |
