# Your Workspace

Liquid LSD has a lot going on, but it's organized around a handful of core ideas. Once those click, everything else makes sense.

---

## The Library & What It Controls

The Library panel spans the left and middle columns of the app. That column has four views:

- **Perform** — The Performance rows sit above the Library, which fills the rest of the window. This is the everyday view for playing a set.
- **Edit** — Click a row's **gear** and the Library goes away completely. That row's pair of rows and its parameters fill the column, so you have room to fine-tune presets and FX. Its header line has **Back** and a **Browse | Params** switch: Browse shows the list for the same deck and half. Press **Esc**, **Back** or the lit gear to get back to Perform.
- **Pair view** — Picking for a deck (or for Transitions / Master FX) whose rows are not on the current page (for example clicking the BG monitor from the A/B page) brings that pair's two rows on screen with the browser below, and the Library goes away. Its header line matches Edit's. Press **Esc** or **Back** to return to Perform. See [Presets & Library](presets_and_library.md).
- **Library** — The Library fills the whole column and the Performance rows are hidden. Use this when you're building or editing playlists and queues.

Clicking a source name, FX chain name, FX slot or the transition name on a Perform row doesn't change the view: the Library below starts applying to that slot (its name shows on the Library's tab line), until you press **Esc**, its **✕**, or click the slot again. See [Presets & Library](presets_and_library.md#picking-for-a-slot-the-library-applies-in-place).

Press **`Space`** (when the cursor isn't in a text field) or the button at the right of the Library's title bar to switch between Perform and Library. From Edit or the pair view, `Space`, `Ctrl+F` or `/` closes them (cancelling any armed Add Target or Learn) and brings the Library back; **Esc** then returns you to where you were.

The Performance rows have a fixed height, so the Library gets whatever window height they leave: a taller window means a taller Library.

The Library contains your **Presets**, **Playlists**, and **A/B Queues** — not shaders (those are managed separately under **Preferences → Shader Locations**).

---

## The Four Decks

Liquid LSD uses four independent rendering decks — think of them like channels on a mixer, each running its own visual independently:

- **Deck A & Deck B** — Your two main stage decks. These go through the crossfader and blend modes, so you can mix between them in real time.

- **Deck BG (Background)** — Renders behind Decks A and B. Good for subtle textures or dark ambient fields that sit underneath your main visuals.

- **Deck PV (Preview)** — A fully independent audition deck that *never* outputs to the master. Use it to quietly load and preview a preset on your monitor before pushing it live. It runs the complete visual and modulation chain, so what you see is exactly what you'll get.

Each deck is completely independent — its own source, its own 3-slot effects chain, and its own modulation.

---

## Signal Flow

Here's how audio becomes visuals:

1. **Audio in** → The DSP engine analyses your track in real time, splitting it into frequency bands and tracking the beat.
2. **CV modulation** → Those audio signals become control voltages that can drive any visual parameter.
3. **Sources** → Each deck runs a source (a generator such as Mandala, an ISF shader, external video, etc.) using those CV signals to shape what renders.
4. **FX chain** → Each deck runs its source through a chain of three effect slots, left to right. Feedback (the trails that layer the image over time) is one of the effects you can put in a slot.
5. **Mixer** → Deck A and B blend together through the crossfader and the current transition. Deck BG fills in behind them, wherever their mix is transparent.
6. **Master FX** → The mixed image runs through a final chain of three effect slots.
7. **Output** → The master output goes to your screen, projector, or other apps.

---

## The Performance Panel (Left & Middle)

The top of the left and middle columns is the **Perform view**: a 4×4 grid of macro knobs,
one row per deck or section, with tabs across the top (A/B, BG/PV and MIXER). Click a deck row's source name to change its source in the Library below.

For full control, open a row's **Edit** (the gear on the row, or **Params** in the pair view's header). Edit has three columns:

- **Side rail** — `[MST] [OUT] [A] [B] [BG] [PV]` to jump between channels. **MST** is the Master MST row and the Clock row (deck levels, queue triggers); **OUT** is the output stage (Transitions and Master FX).
- **Parameter grid** — every row is a visual parameter (like "Lobes", "Zoom", or "Cell scale", shown with the shader's own label) and each
  column is a modulation source (manual value, MIDI, LFO, sequencer, audio). Click a cell to
  configure that connection. See [Modulation](modulation.md) for the full guide.
- **Modulation** — the details of the selected cell: waveform controls for LFOs, band selectors
  for audio, step patterns for the sequencer, etc., with a live oscilloscope of the signal going
  to the parameter. Opening Edit or switching tabs selects the first knob-assigned parameter (or
  the first row), so this column is never empty.

See [Macro Controls & the Perform View](macros_and_rack.md) for the knobs and Edit in detail.

---

## Themes

Liquid LSD has two color themes: **Grey Acid** (dark, the default) and **Orange Sunshine** (light). Switch between them in **Preferences → General → Theme** (`Ctrl+P`); the choice is remembered between sessions.

---

## The Mixer (Right Panel)

The right panel shows:
- Live previews of each deck
- The crossfader between Deck A and Deck B
- Level controls for each deck
- Master output monitor
- Blend mode selector

Clicking a deck monitor points the Library at that deck's source; clicking the master monitor (or its **M** badge) points it at the Master FX chain (or at the transition, if you touched that last). When the current page doesn't show those rows, the pair view brings them on screen instead. Use a row's gear for the full Edit.

Macro knobs are edited in the Performance rows' Edit view, where each knob gets a target strip for choosing what it controls. See [Macro Controls & the Perform View](macros_and_rack.md).

---

## The Library

The Library spans the lower portion of the left and middle columns and holds your Presets, Playlists, and A/B Queues. See [Presets & Library](presets_and_library.md) for the full guide.

---

## Global Shortcuts

A few keyboard shortcuts work anywhere in the app:

| Key | What it does |
|-----|-------------|
| `Space` | Switch between the Perform and Library views; from Edit or the pair view, close it and bring the Library back (when cursor isn't in a text field) |
| `F` | Output View — hides the UI, pure video output |
| `Esc` | Exit Output View; otherwise go back one step (stop browsing a slot, leave the pair view or Edit, or return from the Library to where you opened it). An Esc that closes a menu or popup only closes it |
| `F11` | Toggle fullscreen for the main window (UI stays visible) |
| `B` | Toggle background video rendering behind the UI |
| `Ctrl+Z` | Undo the last parameter/modulator change (30-step history) |
| `Ctrl+S` | Save the preset of the deck open in Edit |
| `Ctrl+R` | Start / stop recording |
| `Ctrl+P` | Open Preferences |
| `Ctrl+F` or `/` | Jump to the search box of the open Library tab |
| `T` | Tap tempo |
| `Ctrl+Shift+O` | Toggle OSC map mode (click any OSC-learnable control to map it) |
| `Ctrl+-` / `Ctrl+=` | Make the Library preset names smaller / larger |
| `Caps Lock` | Toggle the touch console controller |
| `Q` / `Shift+Q` | Add the selected Library item to the A/B queue / BG queue |
| `Up` / `Down` | Move the selection in the Library lists and queues |
| `Enter` | Run the selected Library row's default action (load, apply, or apply to the bound slot) |
| `Delete` / `Backspace` | Delete the selected saved item, or remove the selected queue item (the key is set in Preferences → Shortcuts) |
| `Alt+click` | Preview a Sources row on Deck PV |
| A letter key | With the Library list focused, start a search with that letter (keys bound to a shortcut keep it) |
| `Ctrl+C` / `Ctrl+V` | Copy / paste a modulation cell or row in Edit |

Every shortcut except `Caps Lock` and `Space` can be rebound in **Preferences → Shortcuts**. Shortcuts are ignored while you are typing in a text field.

## Glossary

| Term | Meaning |
|------|---------|
| **Source** | A stock ISF shader or an external video input. External video can't be saved as a preset. |
| **Preset** | A Source you configured, named and saved. |
| **Edit** | The parameter editor, with the open unit's two rows above it (a deck's source and FX, Master's Transitions and FX): parameter grid, modulators and Modulation. |
| **Bank** | A saved set of macro knob targets (Library → Macros → Banks). Perform pages (Library → Macros → Pages) are saved row layouts. |
| **A/B Queue / BG Queue** | The auto-VJ queues feeding Decks A/B and the Background deck. FX and Transitions have their own queues. |
| **Level** | A deck's or the Master's output opacity. |
| **MST** | The Master row's pill: deck levels and the master level. (It was called MIX before.) |
| **OUT** | Edit's output-stage tab: Transitions and Master FX. |
| **Dry/Wet** | How much of an FX chain (or one effect) is mixed in. **Chain Dry/Wet** is on the chain's Edit tab; a chain below 100% shows "Wet NN%" on its Perform row. |
| **Mapping** | A hardware (MIDI/OSC) control tied to a parameter. A macro knob's **targets** are the parameters it drives (no hardware involved). |
| **Bypass / Mute** | Bypass turns off an FX chain or shader; Mute turns off a modulator. |
