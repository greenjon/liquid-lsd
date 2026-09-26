# Macro Controls & Performance Mode

Macro Controls give you a small number of physical-style knobs that each drive several
parameters at once — the fast, tactile layer you reach for live instead of hunting through
parameter grids. Performance Mode builds on the same macro system to give you a 4×4 knob matrix
purpose-built for live performance, with Deep Edit underneath for everything else.

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
   - A parameter slider in a Deep Edit **parameter grid** binds to that parameter's base
     value.
   - A modulator control in Deep Edit's **Properties** column — e.g. an LFO's Subdivision or
     Morph slider, or an envelope's Attack/Decay — binds to that modulator property directly.
     This lets a macro knob speed up an LFO or shorten an envelope's decay, not just move a value.
3. A toast confirms the binding (e.g. *"Bound Knob 3 → Zoom [LFO 1 Morph]"*) and Learn Mode
   turns itself off.

A knob can only bind to parameters in **its own deck and section**:

- Deck A **SRC** knobs bind to Deck A's SRC parameters only.
- Deck A **FX** knobs bind to Deck A's FX chain only.
- **Master FX** knobs bind to the Master FX chain only.

If you click a parameter outside that section, the banner says *"Cannot bind…"* and Learn stays
armed, so you can click the right one. If you move to another section while Learn is armed, Learn
is cancelled. That includes Deep Edit's SRC/FX tabs, the side rail, the MACROS tabs, and a Deck
row's `[SRC]`/`[FX]` pills. Master, Transitions and FX Sends knobs aren't limited this way.

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
- **Live Value Meter Knob** — on the second row of each binding (to the left of Min/Max, Link Mode, and Curve), a rotary meter knob (the same crisp knob widget used in the parameter grid) displays the live evaluated output value in relation to the macro knob position. If you use a Triangle ramp (peak) curve or a half-turn zone, moving the macro knob from 0 to 1 lets you see the binding sweep (e.g. 0 → 1 → 0) in real time. Hovering over the knob displays exact numerical readouts.
- **Enabled** — toggling a binding off immediately hands the target field back to normal
  manual/mouse editing; toggling it back on resumes macro control.

The **binding target name** (e.g. `Deck A/Mandala/L1`) is a clickable link. Clicking it opens the
corresponding deck's (or the Mixer's) Deep Edit on the right sub-tab with the parameter selected,
so you can immediately reach the parameter being controlled without hunting for it manually.

### Locked fields & Visual Indicators

Any parameter base value or modulator property that is actively bound to an enabled macro control
receives distinct visual cues in Deep Edit:

- **Parameter grid**:
  - The row displays an **Electric Cyan left-accent border** and a subtle cyan background tint.
  - An inline badge such as **`[K1]`** appears beside the parameter name, and the name is
    tinted Electric Cyan.
  - The "VAL" meter cell is outlined with a cyan border.
  - Hovering over the row or value cell shows a contextual tooltip identifying the controlling
    macro (e.g. `Locked: Driven by Knob 1 (WARP) [K1]`).
  - **Clicking the row label, badge, or VAL cell** directly navigates Column 3 to the **MACROS**
    view and selects that specific macro control.

- **Properties & Sliders**:
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
  Performance-Mode-only; the Column 3 MACROS editor's `LEARN` button is for parameter binding, not
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

MACROS and Deep Edit always point at the same deck and section:

- Picking a tab in MACROS (e.g. **B FX**) switches an open Deep Edit to that deck and section. If Deep Edit is
  closed, the tab only changes what MACROS shows.
- Picking a deck on Deep Edit's side rail, its **SRC / FX** tabs, or opening a deck's Deep Edit
  anywhere else switches MACROS to match.
- A Performance row's `[SRC]` / `[FX]` pill also switches that deck's section.

The same applies to the other decks and Master (**MST**, **TRANS** and **MST FX** match Deep Edit's
Mixer **CTRL**, **TRANS** and **FX** tabs).

**GLB** shows the 4 **Global** knobs from the Performance MASTER tab's Clock row. Unlike every
other bank, Global knobs aren't tied to one deck or section: one Global knob can drive, say,
Deck A's zoom, Deck B's zoom and the Master FX Super Knob together. Picking **GLB** doesn't move
Deep Edit. Click **Learn**, open any Deep Edit, and click the parameters to bind. Global knobs
are saved with your session, not with deck presets.

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
- **Focus Mode** (per slot) — focuses on an individual effect slot across the Performance Matrix FX rows and Column 3.
  - **Knob 1**: Controls the focused slot's individual Dry/Wet blend.
  - **Knobs 2–4**: Retargeted to the focused effect's top parameters on the active page, with parameter paging (`[◀ P1/N ▶]`) when more than 3 parameters exist.
  - **Parameter Cells (`FxParamCell`)**: Display parameter values with a reset-to-default button (counter-clockwise arrow).
  - **Hardware MIDI**: Physical controllers mapped to `Macro/<bankId>/knob_1..4` retarget dynamically to the focused slot's Dry/Wet and parameters without requiring any MIDI remapping.
  - Click `[◀ CHAIN]` in the header, or the active slot pill, to return to standard Group Mode.
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

Performance Mode is the app's main view: a **4×4 Macro Knob Matrix** — 16 knobs arranged in 4
rows across 4 columns, color-coded by deck — on the left, with the Mixer column (Column 3) and
Library dock alongside. (Earlier versions also had a "Classic" Parameters/Properties view,
toggled with `F4`; everything it did now lives in Deep Edit, below.)

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
- The Transitions row carries the transition picker and queue prev/next controls.

Each row is color-coded to its deck or target (blue for Deck A, orange for Deck B, amber for Deck BG, mint for Deck PV, violet for Transitions, crimson for Master, teal for FX) and starts with a title badge spanning both control lines: a large **A**, **B**, **BG** or **PV** on deck rows (the `[SRC]`/`[FX]` pills beside it show which the knobs control), and **MASTER**, **TRANS**, **WET/DRY** and **CLOCK** on the MASTER tab. No row has a header bar over its knobs, so MASTER-tab knobs are the same size as deck knobs.

### Performance Controls & Side-Wing Layout

To maximize vertical space in the matrix and keep the knobs comfortably clustered together:
- **Row Titles & Side-Wing Controls**: Row group titles (e.g. `DECK A`, `DECK A (FX)`) sit at the top-left of the row, level with the top of the knobs. The left- and right-wing controls sit below the title, with their bottom edge lined up with the bottom of the knobs, so short rows never push the controls up into the title. Every wing control (buttons, badges, preset dropdown) is the same height.
- **Row Height & Scrolling**: Rows share the panel's height, so they shrink as you drag the Library dock taller. Once rows reach a minimum height (about where the Library is at half height), they stop shrinking and the matrix scrolls vertically instead. Two usable rows plus a scrollbar beat four knobs too small to grab. Scroll with the scrollbar, with the mouse wheel over the gaps between knobs (over a knob the wheel still adjusts that knob), or by click-dragging up/down on any empty part of a row, including its title band.
- **Deck Rows (Deck A, B, BG, PV)**:
  - **Left Wing (Info & Deck Controls)**:
    - **Row 1 (SRC)**:
      - **`[ SRC ]` Knob Pill**: Assigns the row's 4 on-screen macro knobs to the deck's Visual Generator.
      - **Generator Badge**: Displays active visual source (`deck.source.displayName`), click to change source.
      - **Preset Dropdown Combo**: Searchable preset selector with auto-focus Quick-Search filter bar (`presetSearch*`) and dirty marker (`*`).
      - **Eject Button (`⏏`)**: Resets the deck to defaults with dirty-state safety guard.
      - **Randomize Die Button (`🎲`)**: Instantly randomizes that deck's modulators & base values with undo support (when randomization is enabled).
      - **Queue Navigation**: Deck A and Deck B connect to `PlayQueueManager` (`< N/Total >`), Deck BG connects to `BgQueueManager` (`< N/Total >`), and Deck PV features a quick Preview focus button.
    - **Row 2 (FX)**:
      - **`[ FX ]` Knob Pill**: Assigns the row's 4 on-screen macro knobs to the deck's insert FX chain (Super Knob + 3 Metaknobs).
      - **Dedicated Chain Controls**: Step through chains (`◀` / `▶`), active chain name with dirty dot (`•`), `[Save]` button, and kebab menu (`[⋮]`) for Save As, Revert, Copy/Paste, and Resync.
  - **Knob Columns**: The 4 macro knobs are uniformly spaced across the center area between the control wings, sharing the exact same column pitch and horizontal alignment whether assigned to `SRC` or `FX`. When assigned to `FX`, each of knobs 2–4 gets two stacked buttons on its left: a link button (`Icons.LINK`/`Icons.UNLINK`) that links/unlinks its Metaknob to the Super Knob, and a power button that bypasses just that effect. Interactive FX slot cells showing the effect name appear beneath knobs 2–4; hover a cell to show its `◀` / `▶` shortlist arrows.
  - **Right Wing**: Displays dedicated insert FX controls for that deck: `[ BYPASS / FX ON ]` kill switch, aligned level with the FX row.
  - **Drag-and-Drop**: Dropping a deck preset (`.patch`, `.lsd`, `.json`) loads the visual preset; dropping a `.lsdfxchain` loads that FX chain onto the deck.
- **Whole-Rig Randomize (`[ ALL 🎲 ]`)**: Positioned at the top right of the performance matrix tab strip, pushing undo state and invoking `mixer.randomizeAll()` across all decks simultaneously.
- **Master (Row 1 in MASTER)**: Left wing: `[ MIX ]` over `[ FX ]` + Master FX chain header, as described above; right wing: Master FX `[ BYPASS / FX ON ]`. Dropping a `.lsdfxchain` onto the `MASTER` badge loads it into Master FX. The `[ MIX ]` line holds the crossfader: Deck A and Deck B quick-snap buttons (`[ A ]` and `[ B ]`), an interactive zero-centered crossfader track with mouse drag, mouse wheel, middle-click center reset, and live amber modulation/auto-fade indicator dot, a smooth `[ AUTO ]` crossfade trigger, and an interactive **Fade Speed Duration Badge** (crossfader time -- scrubbable with mouse drag/wheel and right-click context menu for duration presets `0.5s`–`8.0s` and MIDI/OSC learn). Transition presets (`.lsdtrans`) and shaders (`.fs`/`.isf`) dropped onto the crossfader track apply directly.
- **Transitions (Row 2 in MASTER)**: Features a Transition Picker popup button displaying the active transition with modified indicator (`*`), and Transition Queue stepping controls (`<`, `N/Total`, `>`). Transition presets (`.lsdtrans`) and shaders (`.fs`/`.isf`) can be dropped directly onto the picker button.
- **Clock & Global (Row 4 in MASTER)**: The two lines beside the `CLOCK` badge have the tempo controls from Preferences > Tempo & Sync, placed here for use mid-set:
  - **`[ MAN ]` / `[ AUDIO ]`**: clock source (manual tempo or the audio beat tracker).
  - **`[ LINK n ]`**: shown while Ableton Link is on, with the peer count. Click it to open Tempo & Sync preferences.
  - **BPM readout**: click it to open Tempo & Sync preferences.
  - **4 beat dots**: bar phase; the downbeat is cyan.
  - **`[ TAP ]`**: tap tempo. Right-click for MIDI Learn. In Audio mode it nudges the detected tempo and phase.
  - **`[ RESYNC ]`**: snaps the beat phase to the downbeat.
  - **`[ /2 ]` `[ x2 ]`**: halve or double the tempo.
  - **`[ - ]` `[ + ]`**: nudge the tempo by 0.5 BPM.

  The BPM is deliberately not a knob, since one bump would shift the whole show. The row's 4 knobs are the **Global** macros (see the **GLB** tab above), which can bind to any parameter.

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

Knobs in the 4×4 matrix and in Column 3's MACROS view read from and write to the **same
underlying `MacroEngine` banks** — changes in one are immediately visible in the other.

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
     - **`MIX`**: `[ CTRL ]  [ FX ]  [ TRANS ]` (Master controls, the Master FX chain's 3 ISF slots, and Transitions)
     - **`A`**, **`B`**, **`BG`**, **`PV`**: `[ SRC ]  [ FX ]` (visual generator with Gain/Zoom/Rotate Z, and the insert FX chain)
     Switching between `SRC`/`CTRL` and `FX` in Deep Edit automatically switches the on-screen macro knobs (and corresponding `[SRC]` / `[FX]` pill highlight) between visual source controls and insert/master FX macros (`Super Knob + 3 Metaknobs`). Source and FX chain controls on the deck's performance row remain available simultaneously.
   - **Properties Editor** on the right: side-by-side per-parameter CV detail editor (LFO period/phase/morph/hold/slew, MIDI, SEQ, AUD, curves, and modulators) of whichever cell is selected.

   If the deck is **empty**, Deep Edit shows the empty-deck card instead: **Add Source** (opens the
   source picker, including external video), **Load Preset**, and **Open Library Panel**.

**Changing a deck's visual source**: click the generator badge at the left of any deck row (it shows
the current source, e.g. `Mandala`, or `+ Source` on an empty deck) to open the source picker.

**Keyboard shortcuts in Deep Edit**: `Ctrl+C` / `Ctrl+V` (copy/paste a cell or row), `Delete` /
`Backspace` (clear the cell's modulators, or reset the parameter), and `Ctrl+S` / `Shift+Ctrl+S`
(save / save-as the deck being edited) act on the open Deep Edit. Copy/paste/clear only fire while the
Performance panel has focus, so `Delete` in the Library doesn't also reset a parameter. `Ctrl+Z`
(undo) works anywhere in Performance Mode, with or without Deep Edit open.

**While in Deep Edit**, the top macro row renders the macro controls corresponding to the active channel and subtab,
reserving the freed vertical space for the side rail and parameter bay. The row keeps the same knob size and control
positions it has in the four-row view; it only grows by one line under each knob for the value readout (and the
**Learn** button under the selected knob). There's no separate title above the parameters, since the row says which
deck and section you're editing.
Collapsing back to Faceplate brings the rest of the 4×4 grid, and the Library, back. While
Deep Edit is open the Library is hidden completely (the **Edit** view — see
[Your Workspace](your_workspace.md)).

Click the chevron again (or the row's **Collapse** button, **Close Edit**, or Esc) to fold back to
the Faceplate.

**Opening Deep Edit from Confidence Monitors**:
In addition to the row chevrons, clicking any preview monitor in Column 3 (Deck A, Deck B, Deck BG, Deck PV, Main Output Master, or the MACROS tab preview) will immediately open Deep Edit focused directly on that module. This swaps the Deep Edit bay from your current deck to the clicked deck without needing to scroll or find the row chevron.

**One Deep Edit at a time**: opening a row's Deep Edit collapses any other open one. To move
between decks, use the Deep Edit side rail (**MIX / A / B / BG / PV**) rather than opening
rows side by side. The toolbar above the grid has a **`Close Edit`** button.

**Esc** collapses every expanded row back to the Faceplate — unless a Macro Learn is currently
armed, in which case Esc cancels the Learn instead (a second Esc then collapses the rack). A
row whose own knob has an armed Learn is also exempt from the auto-collapse, so it can't
be accidentally folded away mid-Learn; a **"Learning: ‹name› — Esc to cancel"** indicator stays
visible in the toolbar the whole time a Learn is armed, even if you've expanded a different row.
**View → Close Deep Edit** does the same as `Close Edit`.

Expanding or collapsing a row is purely a display change — it never re-syncs the Super Knob/Metaknob mapping.

### Setting up knob labels and bindings

The full Binding Inspector (rename, target list, Min/Max/Curve/Invert/Link/Enabled) lives only in
the **`[ MACROS ]`** tab of Column 3 — it's not duplicated inside the Performance Mode rack, so
binding a knob doesn't mean scrolling past a Deep Edit panel to reach it. Clicking a bound
target's name in the inspector opens that deck's (or the Mixer's) Deep Edit on the right sub-tab
with the parameter selected.

- **From the 4×4 matrix**: click a knob to select it, then click its inline **`[Learn]`** button.
  This arms parameter-bind Learn, opens that row's **Deep Edit** (if it isn't already open), and
  switches Column 3 to **`[ MACROS ]`** on the matching bank/tab with the Binding Inspector already
  open on that knob — click a target parameter in Deep Edit and set Min/Max/Curve as desired. The
  target must be in the knob's own deck and section (see *Binding a knob* above).
- **From Column 3**: open **`[ MACROS ]`**, select the knob, and click **LEARN**. This arms Learn
  and opens that deck's **Deep Edit** if it isn't already open. Then click a target parameter in
  Deep Edit.

Either way the change is live immediately in both places, since they share the same
`MacroEngine` banks.

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
| | Master | 4 | `master` | Alpha A, Alpha B, Master Level, Crossfader |
| **Master FX** | FX Wet/Dry | 4 | `fxSends` | Deck A, B, BG, PV Insert FX Wet/Dry Levels |
| | Master FX | 4 | `masterFx` | Super Knob + 3 Metaknobs |

Deck generator banks initialize from their generator's default preset. Master, Transitions, and FX banks initialize with pre-mapped smart defaults. The five FX banks bind to their chain's `Deck A/FX/...` … `Master/FX/...` parameters and re-sync automatically whenever that chain's contents change (loading a chain, swapping a slot, restoring a session) — any knob you've retargeted by hand is left alone; **Resync** forces the defaults back. All 12 canonical banks are preserved in `last_session.json` and session files.

Banks are saved in `last_session.json` and bundled into preset files automatically.

---

## Generator & FX Defaults ("Save as Default")

Similar to Ableton's default presets, you can configure your favorite parameter starting points and 4-knob macro layouts for any visual generator or ISF FX filter, and save them as that device's permanent default.

### Visual Generator Defaults

When swapping visual sources on any deck (via the generator badge or launchpad), the incoming generator's default configuration is resolved across three tiers:
1. **User Default**: Custom parameter baselines, `globalAlpha`, and 4-knob macro layout saved in `library/generator_defaults/<sourceId>.json`.
2. **Curated Stock Default**: Hand-curated 4-knob macro bindings for bundled generators (`mandala`, `dynamic_spiral`, `icosa_h3`, `domain_warp_fluid`, `gyroid_hyperspace`, `celestial_engine`, `hyper_slice`, `chladni_cymatics`).
3. **Automated Heuristic Fallback**: For third-party ISF shaders, continuous floats (e.g. speed, rate, zoom, scale, morph, depth, detail) are prioritized, discrete stepped selectors are filtered out, and speed/frequency parameters are shaped with exponential response curves.

**Bank Replacement Behavior**: Applying a default replaces the resident 4-knob deck bank wholesale, identical to preset loading. Stored `Deck/*` bindings are dynamically remapped to the target deck slot (e.g., `Deck A`, `Deck B`). Knobs are positioned via inverse-curve mapping so parameter values do not jump on load. Hardware MIDI mappings are stripped on save to avoid duplicate hardware CC collisions across decks.

**How to Save or Reset Generator Defaults**:
- **Right-click the generator badge** on any deck row in Performance Mode to open the context menu:
  - `Save as Default for <Generator>`: Saves current parameters, alpha, and 4-knob macro layout as the generator default.
  - `Apply Default Now`: Reapplies the default layout and parameters to the current deck.
  - `Reset to Factory Default`: Deletes the user default file and restores curated/heuristic factory defaults.
- Alternatively, open the **Save menu** in Deep Edit's monitor toolbar and select `Save as Default for <Generator>`.

### Explicit ISF FX Defaults

In the FX Chain Macro Strip (and Deep Edit FX views), slot Metaknobs and parameters can be customized and explicitly saved:
- Tweaking parameters or re-targeting Metaknobs during performance is purely temporary and does **not** silently overwrite defaults on disk.
- **Right-click any Metaknob** in the FX strip:
  - Select any parameter from the list (or `Bind to Dry/Wet (safety net)`) to rebind the Metaknob.
  - Choose `Save as Default for <Filter>` to persist the current Metaknob bindings and parameter baseline into `library/isf_overrides/<contentHash>.json`.
  - When a user default is present, choose `Reset to Factory Default` to revert to stock curated/heuristic bindings.

---

## Shortcuts

| Key | What it does |
|-----|-------------|
| `Esc` | Cancel an armed Macro Learn, or (if none is armed) collapse every expanded Rack row back to Faceplate |
