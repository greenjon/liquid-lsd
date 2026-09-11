# Your Workspace

Liquid LSD has a lot going on, but it's organized around a handful of core ideas. Once those click, everything else makes sense.

---

## The Library & What It Controls

The Library panel spans the left and middle columns of the app and has three height modes. Press **`Space`** (when the cursor isn't in a text field) to cycle between them:

- **Full Height** — The Parameters and Properties panels are hidden. The Library takes up all the space. Use this when you're focused on building or editing playlists and play queues.
- **Half Height** — The Library sits in the lower half, with the Parameters and Properties panels still visible above it. Good for tweaking modulation while keeping an eye on your setlist.
- **Docked** — The Library collapses to a slim toolbar at the bottom of the screen. Use this when you don't need to manage playlists or queues and want the full workspace visible.

You can drag the Library's title bar up or down to resize it freely in Half Height mode — the height you set is remembered. Double-click the title bar to snap back to exactly 50/50. The standard window buttons on the right of the title bar also let you jump between heights.

The Library contains your **Presets**, **Playlists**, and **Play Queues** — not shaders (those are managed separately under **Settings → Shader Locations**).

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

## The Parameters Panel (Left Panel)

This is where you connect things. Every row is a visual parameter (like "Lobes", "Zoom", or "Hue"), and each column is a modulation source (audio, LFO, sequencer, MIDI, or manual value). Click the intersection of a row and a column to configure that connection.

See [Modulation](modulation.md) for the full guide.

---

## The Properties Panel (Middle)

When you click a cell in the Parameters panel, the Properties panel opens to show you the details — waveform controls for LFOs, band selectors for audio, step patterns for the sequencer, etc. It also shows a live oscilloscope so you can see exactly what signal is going to the parameter.

---

## The Mixer (Right Panel)

The right panel shows:
- Live previews of each deck
- The crossfader between Deck A and Deck B
- Level controls for each deck
- Master output monitor
- Blend mode selector

Clicking the master monitor preview jumps directly to the **MIX** tab.

---

## The Library

The Library spans the lower portion of the left and middle columns and holds your Presets, Playlists, and Play Queues. See [Presets & Library](presets_and_library.md) for the full guide.

---

## Global Shortcuts

A few keyboard shortcuts work anywhere in the app:

| Key | What it does |
|-----|-------------|
| `Space` | Cycle Library height: Full → Half → Docked (when cursor isn't in a text field) |
| `F` | Fullscreen — hides the UI, pure video output |
| `Esc` | Exit fullscreen |
| `B` | Toggle background video rendering behind the UI |
| `Ctrl+Z` / `Ctrl+Y` | Undo / Redo (30-step history) |
| `Ctrl+S` | Save the active deck's preset |
| `Ctrl+R` | Start / stop recording |
| `Ctrl+,` | Open Settings |
| `Ctrl+F` or `/` | Jump to preset search |
