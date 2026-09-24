# Your Workspace

Liquid LSD has a lot going on, but it's organized around a handful of core ideas. Once those click, everything else makes sense.

---

## The Library & What It Controls

The Library panel spans the left and middle columns of the app and has three height modes. Press **`Space`** (when the cursor isn't in a text field) to cycle between them:

- **Full Height** — The Performance panel is hidden. The Library takes up all the space. Use this when you're focused on building or editing playlists and play queues.
- **Half Height** — The Library sits in the lower half, with the Performance panel still visible above it. Good for tweaking knobs and modulation while keeping an eye on your setlist.
- **Docked** — The Library collapses to a slim toolbar at the bottom of the screen. Use this when you don't need to manage playlists or queues and want the full workspace visible.

You can drag the Library's title bar up or down to resize it freely in Half Height mode — the height you set is remembered. Double-click the title bar to snap back to exactly 50/50. The standard window buttons on the right of the title bar also let you jump between heights.

The Library contains your **Presets**, **Playlists**, and **Play Queues** — not shaders (those are managed separately under **Preferences → Shader Locations**).

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

## The Performance Panel (Left & Middle)

The top of the left and middle columns is the **Performance panel**: a 4×4 grid of macro knobs,
one row per deck or section, with tabs across the top (LIVE QUAD, MASTER & FX, LIVE CONSOLE,
ALL FX). Click a deck row's generator badge to change its visual source.

For full control, open a row's **Deep Edit** (the chevron on the row, or click a deck monitor in
the Mixer). Deep Edit has three columns:

- **Side rail** — `[MIX] [A] [B] [BG] [PV]` to jump between channels.
- **Parameter grid** — every row is a visual parameter (like "Lobes", "Zoom", or "Hue") and each
  column is a modulation source (manual value, MIDI, LFO, sequencer, audio). Click a cell to
  configure that connection. See [Modulation](modulation.md) for the full guide.
- **Properties** — the details of the selected cell: waveform controls for LFOs, band selectors
  for audio, step patterns for the sequencer, etc., with a live oscilloscope of the signal going
  to the parameter.

See [Macro Controls & Performance Mode](macros_and_rack.md) for the knobs and Deep Edit in detail.

---

## The Mixer (Right Panel)

The right panel shows:
- Live previews of each deck
- The crossfader between Deck A and Deck B
- Level controls for each deck
- Master output monitor
- Blend mode selector

Clicking a deck monitor opens that deck in Deep Edit; clicking the master monitor opens the Master (**MIX**) Deep Edit.

A **`[ MIXER | MACROS ]`** toggle at the top of this panel switches it to the Macro Controls view, where you bind the Performance panel's knobs to any parameter or modulator. See [Macro Controls & Performance Mode](macros_and_rack.md).

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
| `Ctrl+Z` | Undo the last parameter/modulator change (30-step history) |
| `Ctrl+S` | Save the preset of the deck open in Deep Edit |
| `Ctrl+R` | Start / stop recording |
| `Ctrl+P` | Open Preferences |
| `Ctrl+F` or `/` | Jump to preset search |
