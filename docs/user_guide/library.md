# Library & Playlist Management

Liquid LSD includes a dedicated **Library Management System** for organizing visual presets, building live performance playlists, and managing the play queue.

---

## Toggling Modes

Liquid LSD features flexible workspace dock layouts:

1. **Performance Mode (Default)**: Preset Grid, Cell Config, and Mixer / Monitor panels with the Library docked at the bottom.
2. **Library View Modes**: Switch between **Half Height**, **Full Height**, and **Hide** via the toolbar buttons or layout splitters.

---

## Panel Layout in Library Mode

```
┌───────────────────┬───────────────────┬───────────────────┬───────────────────┐
│  PRESET LIBRARY   │  PLAYLIST EDITOR  │    PLAY QUEUE     │ BACKGROUND QUEUE  │
│(All Presets/Search│[Playlist Selector]│   (A/B Auto-VJ)   │ (Dedicated BG/Dip)│
├───────────────────┴───────────────────┼───────────────────┼───────────────────┤
│ [A] [B] [BG] [PV] [Q] [BGQ] [ + ▾]    │ [AUTO-VJ] [🔁][🔀]│ [AUTO-BG] [🔁][🔀]│
├───────────────────┬───────────────────┤                   │                   │
│ [🔍 Search...]    │ 1. Preset Alpha   │ ▶ 1. Preset 1     │ ▶ 1. Nebula BG    │
│ Preset Alpha      │ 2. Preset Beta    │   2. Preset 2     │   2. Dark Grid BG │
│ Preset Beta       │ 3. Preset Gamma   │   3. Preset 3     │                   │
└───────────────────┴───────────────────┴───────────────────┴───────────────────┘
```

---

## Unified Top Action Toolbar

Directly above the Presets and Playlist Editor columns sits a streamlined 7-button routing toolbar:

- **`[ A ]`**: Loads the currently selected preset into Deck A.
- **`[ B ]`**: Loads the currently selected preset into Deck B.
- **`[ BG ]`**: Loads the currently selected preset into Deck BG (Background).
- **`[ PV ]`**: Previews the currently selected preset in Deck PV (Preview).
- **`[ Q ]`**: Appends the currently selected preset to the A/B Play Queue.
- **`[ BGQ ]`**: Appends the currently selected preset to the Background Queue.
- **`[ + ▾ ]`**: Opens a dropdown to create a new blank preset on any deck (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`).

> [!TIP]
> Selecting any preset in the Presets column automatically deselects the current playlist selection (and vice versa) for unambiguous one-click routing.

---

## 1. Preset Library (Column 1)

The Left column displays the complete pool of all available presets discovered across `library/presets/`.

### Features & Navigation
- **Search & Tag Filter**: Type into the top search bar to filter presets in real-time by preset name or assigned tags.
- **Clean List View**: Preset rows display clean typography without cluttered inline buttons.
- **Double-Click**: Automatically loads the preset into the inactive deck based on crossfader position.
- **Drag-and-Drop**: Drag presets directly into the Playlist Editor, A/B Queue, Background Queue, or preview monitors.
- **Keyboard Shortcuts**: Select a preset and press `Delete` or `Backspace` to delete the preset from your library (with permanent deletion confirmation).
- **Context Menu Actions (Right-Click)**:
  - **Load to Deck A / B / BG / PV**: Instant deck routing.
  - **Add to A/B Queue / Background Queue**: Fast queue assignment.
  - **Add to '{Active Playlist}'**: Appends the preset directly into the currently selected playlist.
  - **Rename / Edit Tags… (`F2`)**: Opens the metadata modal to edit filename and tags.
  - **Duplicate Preset…**: Opens the metadata modal pre-populated with `<name>_copy`.
  - **Delete**: Permanently deletes the preset from disk with a confirmation modal.

---

## 2. Playlist Editor (Column 2)

The Middle column allows inspecting and arranging setlists side-by-side with your preset library.

### Header & Playlist Switcher
- **Playlist Dropdown Combo**: Click to instantly switch the active playlist from all discovered `.lsdset` files.
- **`[ + ]` Create New Playlist**: Prompts for a playlist name and creates a new empty setlist file.
- **`[ ••• ]` Playlist Actions Menu**:
  - **Play now (and replace queue)**: Loads and starts playback of the entire playlist.
  - **Insert into queue after current**: Inserts all playlist presets into the live queue after current track.
  - **Add to bottom of queue**: Appends the playlist to the end of the queue.
  - **Rename...**: Renames the active playlist file on disk.
  - **Clone**: Duplicates the active playlist as `<name>_copy.lsdset`.
  - **Delete**: Permanently removes the playlist file.

### Playlist Preset Rows & Auto-Save
- **Auto-Save on Edit**: Any modification (adding presets, dragging to reorder, or removing items) automatically saves to disk.
- **Keyboard Shortcut (`Delete` / `Backspace`)**: Select an item in the playlist and press `Delete` or `Backspace` to remove it from the playlist.
- **Drag Reordering**: Drag items up and down with mint-green insertion line feedback.
- **Item Context Menu (Right-Click)**:
  - **Load to Deck A / B / BG / PV / Q / BGQ**.
  - **Remove from playlist**: Removes the preset from the playlist list.
  - **Delete preset from library...**: Permanently deletes the preset file from your library.

---

## 3. A/B Play Queue (Column 3)

The 3rd column displays the live sequence of presets for main A/B deck Auto-VJ and playback.

- **`AUTO-VJ`**: Enables automated cycling through queue presets at configured crossfade intervals.
- **Repeat (`🔁`) & Shuffle (`🔀`)**: Controls queue cycle loop and randomization.
- **Keyboard Shortcut (`Delete` / `Backspace`)**: Select an item in the play queue and press `Delete` or `Backspace` to remove it from the queue.

---

## 4. Background Queue (Column 4)

The 4th column manages automated cycling and sequential playback for the dedicated background layer (`Deck BG`).

- **`AUTO-BG`**: Enables automatic cycling through background presets.
- **Dip-to-Black Transitions**: Smoothly fades out the current background, loads the new preset, and fades back in beneath the live foreground.
- **Repeat (`🔁`) & Shuffle (`🔀`)**: Continuous loop and shuffle for background visuals.
- **Double-Click & Right-Click Play**: Trigger instant cuts or dip-to-black transitions on demand.

---

## 3. Drag-and-Drop Matrix

| Dragged Item | Target Destination | Resulting Action |
|---|---|---|
| **Preset from Left Column** | Between presets in Playlist Editor | Inserts preset at hovered slot with mint-green guideline |
| **Preset from Left Column** | Empty playlist area / bottom | Appends preset to the end of the playlist |
| **Preset within playlist** | Reorder within active playlist | Reorders preset sequence |
| **Preset from Left Column** | Queue panel | Adds preset to live queue |

---

## 4. Handling Missing Items

If a playlist references a preset file that was moved or deleted from disk:
- The preset row appears in **red** with `[!] (missing)`.
- Right-click the missing row to remove the reference.
