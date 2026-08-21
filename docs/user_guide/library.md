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
┌───────────────────────────┬───────────────────────────┬───────────────────────────┐
│     PRESET LIBRARY        │      PLAYLIST EDITOR      │        PLAY QUEUE         │
│  (All Presets + Filters)  │   [ Playlist Dropdown ▾]  │     (Now Playing / Live)  │
├───────────────────────────┼───────────────────────────┼───────────────────────────┤
│ [🔍 Search presets & tags]│ [ + ] [ ••• ]             │ [Play] [Prev] [Next] [🔀] │
│                           │                           │                           │
│ [A][B][C] Create preset...│ 1. [A][B][C][Q] Preset 1  │ ▶ 1. Preset 1 (0:42)      │
│                           │ 2. [A][B][C][Q] Preset 2  │   2. Preset 2             │
│ [A][B][C][Q] Preset Alpha │ 3. [A][B][C][Q] Preset 3  │   3. Preset 3             │
│ [A][B][C][Q] Preset Beta  │   (Drag from left to add) │                           │
└───────────────────────────┴───────────────────────────┴───────────────────────────┘
```

---

## 1. Preset Library (Left Column)

The Left column displays the complete pool of all available presets discovered across `library/presets/`.

### Features & Navigation
- **Search & Tag Filter**: Type into the top search bar to filter presets in real-time by preset name or assigned tags.
- **`[Create new preset...]` Row**: Positioned directly above the preset list with `[ A ]`, `[ B ]`, and `[ C ]` buttons. Clicking a deck button ejects/resets that deck and immediately switches focus to that deck.
- **`[ A ] [ B ] [ C ] [ Q ]` Quick Action Buttons**:
  - **`[ A ]`**: Load preset directly into Deck A.
  - **`[ B ]`**: Load preset directly into Deck B.
  - **`[ C ]`**: Preview preset in Deck C (Preview deck).
  - **`[ Q ]`**: Append preset immediately to the end of the live Play Queue.
- **Double-Click**: Automatically loads the preset into the inactive deck based on crossfader position.
- **Drag-and-Drop**: Drag presets directly into the Playlist Editor (middle column) or Queue (right column).

- **Keyboard Shortcuts**: Select a preset and press `Delete` or `Backspace` to delete the preset from your library (with permanent deletion confirmation).
- **Context Menu Actions (Right-Click)**:
  - **Add to '{Active Playlist}'**: Appends the preset directly into the currently selected playlist.
  - **Play now (and replace queue)**: Clears the current queue, loads the item, and triggers immediate playback.
  - **Insert into the queue after current**: Inserts item into the live Auto-VJ queue after the currently playing preset.
  - **Add to the bottom of the queue**: Appends item to the end of the queue.
  - **Rename / Edit Tags… (`F2`)**: Opens the metadata modal to edit both the preset's filename and comma-separated tags in a single step.
  - **Duplicate Preset…**: Opens the metadata modal pre-populated with `<name>_copy` and existing tags.
  - **Delete**: Permanently deletes the preset from your disk/library with a warning modal, updating all playlists and queues.

---

## 2. Playlist Editor (Middle Column)

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
- **`[ A ] [ B ] [ C ] [ Q ]` Buttons**: Fast deck loading and queue appending per playlist item.
- **Keyboard Shortcut (`Delete` / `Backspace`)**: Select an item in the playlist and press `Delete` or `Backspace` to remove it from the playlist.
- **Drag Reordering**: Drag items up and down with mint-green insertion line feedback.
- **Item Context Menu (Right-Click)**:
  - Play now, Insert after current, Add to bottom of queue.
  - **Remove from playlist**: Removes the preset from the playlist list.
  - **Delete preset from library...**: Permanently deletes the preset file from your library with a warning modal.

---

## 3. Play Queue (Right Column)

The Right column displays the live sequence of presets for Auto-VJ and playback.

- **Keyboard Shortcut (`Delete` / `Backspace`)**: Select an item in the play queue and press `Delete` or `Backspace` to remove it from the queue.
- **Item Context Menu (Right-Click)**:
  - **Remove from queue**: Removes the item from the play queue list.
  - **Delete preset from library...**: Permanently deletes the preset file from your library with a warning modal.

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
