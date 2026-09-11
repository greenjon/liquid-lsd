# Your Workspace

Liquid LSD has a lot going on, but it's organized around a handful of core ideas. Once those click, everything else makes sense.

---

## The Two Modes

Press **`F3`** at any time to switch between the two main views:

- **Performance Mode** — This is your live show layout. The CV modulation grid is on the left, parameter controls in the middle, and the master output monitor on the right. Everything you need to run a set is here.

- **Asset Management Mode** — Opens the library, preset browser, playlist editor, and shader folder browser. Use this to prepare your setlist, audition looks, and manage your files before or between sets.

---

## The Four Decks

Liquid LSD uses four independent rendering decks — think of them like channels on a mixer, each running its own visual independently:

- **Deck A & Deck B** — Your two main stage decks. These go through the crossfader and blend modes, so you can mix between them in real time.

- **Deck BG (Background)** — Renders behind Decks A and B. Good for subtle textures or dark ambient fields that sit underneath your main visuals.

- **Deck PV (Preview)** — A fully independent audition deck that *never* outputs to the master. Use it to quietly load and preview a preset on your monitor before pushing it live. It runs the complete visual and modulation chain, so what you see is exactly what you'll get.

Each deck is completely independent — its own visual source, its own effects chain, its own feedback loop, and its own modulation.

---

## Signal Flow

Here's how audio becomes visuals:

1. **Audio in** → The DSP engine analyses your track in real time, splitting it into frequency bands and tracking the beat.
2. **CV modulation** → Those audio signals become control voltages that can drive any visual parameter.
3. **Visual generators** → Each deck runs a visual source (Mandala, a shader, external video, etc.) using those CV signals to shape what renders.
4. **FX & Feedback** → Each deck has two post-processing effect slots and an internal feedback loop that layers and trails the image over time.
5. **Mixer** → Deck A and B blend together through a crossfader and blend modes, sitting over the BG layer.
6. **Output** → The master output goes to your screen, projector, or other apps.

---

## The Preset Grid (Left Panel)

This is where you connect things. Every row is a visual parameter (like "Lobes", "Zoom", or "Hue"), and each column is a modulation source (audio, LFO, sequencer, MIDI, or manual value). Click the intersection of a row and a column to configure that connection.

See [Modulation](modulation.md) for the full guide.

---

## The Cell Config Panel (Middle)

When you click a cell in the Preset Grid, the Cell Config panel opens to show you the details — waveform controls for LFOs, band selectors for audio, step patterns for the sequencer, etc. It also shows a live oscilloscope so you can see exactly what signal is going to the parameter.

---

## The Mixer & Output Monitor (Right Panel)

The right panel shows:
- Live previews of each deck
- The crossfader between Deck A and Deck B
- Level controls for each deck
- Master output monitor
- Blend mode selector

Clicking the master monitor preview jumps directly to the **MIX** tab.

---

## The Library (Bottom Panel)

Press **`Space`** (when you're not in a text field) to cycle the library panel between hidden, half-height, and full-screen. The library holds your preset browser, playlist editor, and the Auto-VJ play queue.

See [Presets & Library](presets_and_library.md) for the full guide.

---

## Global Shortcuts

A few keyboard shortcuts work anywhere in the app:

| Key | What it does |
|-----|-------------|
| `F3` | Toggle Performance / Asset Management mode |
| `F` | Fullscreen — hides the UI, pure video output |
| `Esc` | Exit fullscreen |
| `B` | Toggle background video rendering behind the UI |
| `Space` | Cycle library panel size (when not typing) |
| `Ctrl+Z` / `Ctrl+Y` | Undo / Redo (30-step history) |
| `Ctrl+S` | Save the active deck's preset |
| `Ctrl+R` | Start / stop recording |
| `Ctrl+,` | Open Settings |
| `Ctrl+F` or `/` | Jump to preset search |
