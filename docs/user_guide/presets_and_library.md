# Presets & Library

Presets are the core unit of work in Liquid LSD. A preset captures the complete state of a deck — every parameter, every modulation connection, every note you've added — into a single `.lsd` file. The library and playlist tools are built around organizing and performing with those files.

---

## Presets

### What a preset contains

- All visual parameter values (geometry, colour, view, feedback)
- Every modulation setup (LFOs, audio connections, sequencer patterns, MIDI assignments)
- Preset notes and per-parameter notes (if you've added any)

### Saving a preset

- **`Ctrl+S`** — Saves the active deck's preset to disk immediately. If the deck doesn't have a saved file yet, it opens a **Save As** dialog instead.
- **`Shift+Ctrl+S`** — Always opens the Save As dialog, even for already-saved presets. The suggested name gets `_copy` appended so you don't accidentally overwrite the original.

When you modify a loaded preset, a `*` appears next to the deck name to show there are unsaved changes.

### The `.lsd` format

Presets are plain JSON files stored in `library/presets/` (and subfolders). You can open them in any text editor, back them up, share them, or paste them as JSON using **Copy Preset JSON** / **Paste Preset JSON** from the right-click context menu.

### Copying settings between decks

Right-clicking in the **VALUE** column (or in Deck Controls) gives you copy and paste options to clone parameters from one deck to another. You can also copy individual modulator cells and paste them onto other parameters — see the [Modulation](modulation.md) page for details. You can also use the keyboard shortcuts CTRL-c and CTRL-v to copy and paste (or Command-c and -v on Macs)

### Unsaved changes & the Auto-VJ queue

If Auto-VJ switches presets while a deck has unsaved changes, you can control what happens in **Settings → General**:

- **Skip** — Don't load the next preset until changes are saved or discarded.
- **Auto-Save** — Automatically save the current state before switching.
- **Auto-Discard** — Discard changes and switch immediately.

---

## The Library Panel

The Library panel spans the left and middle columns and has three height modes. Press **`Space`** (when the cursor isn't in a text field) to cycle between them:

- **Full Height** — The Preset Grid and Cell Config panels are hidden. Use this when you're fully focused on building or editing playlists and queues.
- **Half Height** — The Library sits in the lower half of the workspace, with the Preset Grid and Cell Config still visible above it. Good for tweaking modulation while keeping your setlist in view.
- **Docked** — The Library collapses to a slim toolbar. Use this during a performance when you don't need to manage playlists or queues.

In Half Height mode, drag the Library's title bar to resize it freely — the height is remembered. Double-click the title bar to snap back to a 50/50 split. The window buttons on the right of the title bar also let you jump between heights directly.

### Preset Browser (All Presets)

The left column shows every preset saved in `library/presets/`. 

- **Search** — Type to filter by name or tag. `Ctrl+F` or `/` jumps focus to the search box from anywhere in the app.
- **Double-click** — Loads the preset into whichever deck is currently inactive on the crossfader.
- **Number keys 1–4** — Route the selected preset to Deck A, B, BG, or PV respectively.
- **Right-click or ⋮** — Rename, retag, duplicate, add to a queue, or delete.
- **`[!]` badge** — Appears when a preset uses a subsystem that's currently offline (e.g. MIDI or audio). The preset still loads fine; hover the badge to see what's missing.

### Audition Latch

Click **`[ Lock ]`** in the toolbar to enable audition mode. While latched, clicking any preset (or pressing `↑` / `↓`) immediately loads it into the preview deck (Deck PV) so you can hear and see it without touching the live output. Click `A`, `B`, or `BG` while latched to target a different deck. Click the lock again to return to normal selection.

---

## Playlists (Setlists)

The playlist column is where you build your setlist. Playlists are `.lsdset` files — simple ordered lists of presets.

- **Switch between playlists** — Use the dropdown at the top of the playlist column.
- **Reorder** — Drag and drop presets within the list. Changes save automatically.
- **Playlist actions (⋮ menu)**:
  - **Play Now** — Replaces the live queue with this playlist and starts playback.
  - **Insert After Current** — Slides the playlist into the live queue right after the currently playing preset.
  - **Add to Bottom** — Appends it to the end of the queue.

If a playlist references a preset that's been moved or deleted, the row appears in red with a `[!] (missing)` indicator. Right-click it to remove the dead link.

---

## The Play Queue (Auto-VJ)

The play queue drives the main crossfader automatically, sequencing through presets with transitions between Deck A and Deck B.

- **Transport controls** — `<` (previous), `▶/⏸` (play/pause), `>` (next).
- **Loop / Shuffle** — `🔁` for continuous looping, `🔀` for random order.
- **Add to queue** — Select a preset in the browser and press `Q`, or right-click and choose **Add to Queue**.
- **Export** — Save an improvised live queue as a permanent playlist file.

When the queue is playing, the crossfader moves automatically between decks as presets transition. Grabbing the crossfader manually immediately pauses Auto-VJ so you have direct control.

---

## Background Queue (Deck BG)

The Background Queue works the same way as the main queue but drives Deck BG independently. Add presets with `Shift+Q`. Background transitions use dip-to-black fades — double-click or right-click a queued item to choose between an instant cut or a fade.

---

## Drag & Drop

| From                       | To                             | Result                   |
| -------------------------- | ------------------------------ | ------------------------ |
| Preset Browser             | Playlist (between items)       | Inserts at that position |
| Preset Browser             | Empty space at playlist bottom | Appends to the end       |
| Playlist item              | Up / down in the same playlist | Reorders                 |
| Preset Browser or Playlist | Queue                          | Adds to the live queue   |

---

## MIDI Mapping

> MIDI is disabled by default. Enable it in **Settings → MIDI & Controls**.

Liquid LSD keeps hardware controller maps separate from visual presets, so you can swap physical controllers without touching your preset files.

**There are two levels of MIDI mapping:**

1. **Hardware Profile** (stored in `library/midi/default.json`) — Maps physical knobs and faders to global controls like the master crossfader, deck gain, and level faders. These bindings follow you regardless of which preset is loaded.

2. **Grid Cell Modulators** (stored inside each `.lsd` file) — Binds MIDI CC numbers to specific parameters as dynamic modulation sources in the CV grid. These travel with the preset.

### MIDI Learn

1. Click the **MIDI Learn** button in the Preset Grid header or next to any parameter slider.
2. Move a knob, fader, or button on your controller.
3. Liquid LSD captures the CC and confirms the binding automatically.
4. To unbind, right-click the mapped control and clear the MIDI assignment.
