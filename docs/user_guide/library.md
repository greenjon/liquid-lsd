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
┌──────────────────┬──────────────────┬──────────────────┐
│  Presets / Tree  │  Playlist Editor │  Queue & Auto-VJ │
│   (Sidebar)      │   (Center)       │   (Right)        │
│                  │                  │                  │
│  Directory tree, │  Setlist editor  │  Play queue,     │
│  preset files,   │  & playlist      │  Auto-VJ engine, │
│  context menus   │  browser         │  repeat/shuffle  │
└──────────────────┴──────────────────┴──────────────────┘
```

---

## Preset Library (Left / Sidebar & Center)

The Library provides a complete file system view of your presets and playlists (`presets/` and `playlists/`).

### Features & Navigation
- **Collapsible Sidebar Tree**: Click the sidebar toggle icon to show or hide the directory sidebar.
- **Search & Filter**: Type into the search bar at the top to quickly locate specific preset files or subfolders.
- **[Create new preset...] Row**: Positioned directly above the preset list with `[ A ]`, `[ B ]`, and `[ C ]` buttons. Clicking a deck button ejects/resets that deck (prompting to save if dirty according to user preferences) and immediately switches Preset Grid focus to that deck.
- **Visual File Type Icons**:
  - 🎨 **Presets** (`.lsd`, `.patch`): Visual presets.
  - 📋 **Playlists** (`.playlist`, `.lsdset`): Setlist files.
  - 📁 **Folders**: Directories for organizing sets.
  - ⚠ **Invalid / Missing**: Highlighted in red with a warning icon.

### Context Menu Actions (Right-Click)

For Presets & Playlists:
- **Play now (and replace queue)**: Clears the current queue, loads the item, and triggers immediate playback.
- **Insert into the queue after current**: Inserts item into the live Auto-VJ queue after the currently playing preset.
- **Add to the bottom of the queue**: Appends item to the end of the queue.
- **Rename / Edit Tags… (`F2`)**: Opens the metadata modal to edit both the preset's filename and comma-separated tags in a single step.
- **Duplicate Preset…**: Opens the metadata modal pre-populated with `<name>_copy` and existing tags, allowing instant duplication or immediate customization of the copied preset.
- **Delete (`Delete`)**: Removes the file from disk (with confirmation modal).

For Folders:
- **New Folder…**: Creates a subfolder in the current directory.
- **Auto-Refresh**: Automatically monitors and updates directory contents in real time when files are added, modified, or removed on disk.

---

## Playlist Editor (Center Panel)

The Playlist Editor operates in two states:

### 1. Browser State (Default)
Displays all saved `.playlist` files in the current folder.
- **`+ New Playlist`**: Prompts for a setlist name and creates a new blank playlist.
- **Double-Click**: Opens a playlist in Editor State.

### 2. Editor State
Shows the active preset order for a performance setlist:
- **Header Status**: Displays `Playlist: [Setlist Name]` with an unsaved changes indicator (`*`).
- **Context Menu (Right-Click)**: All playlist options (Play now, Insert into queue, Add to queue, Save, Rename, Clone, Delete) are accessed by right-clicking in the Playlist Editor panel or header bar.

---

## Drag-and-Drop Matrix

Liquid LSD supports intuitive drag-and-drop workflows across panels:

| Dragged Item (Library) | Target Destination (Playlist Editor) | Resulting Action |
|------------------------|--------------------------------------|------------------|
| **Preset file** | Between presets in playlist | Inserts preset at target index |
| **Preset file** | Empty playlist area | Appends preset to end of playlist |
| **Playlist file** | Inside active playlist editor | **Flat Unpacks** all presets from the source playlist into the active setlist |
| **Preset within playlist** | Reorder within active playlist | Reorders preset sequence |

---

## Handling Missing Items

If a playlist references a preset file that was moved or deleted from disk:
- The preset row appears in **red** with a ⚠ warning icon.
- Hovering shows the missing path.
- Right-click the missing row to relink or remove the reference.

---

## Keyboard Shortcuts Cheatsheet

| Shortcut | Action |
|----------|--------|
| `F2` | Rename selected file in Library |
| `Delete` | Delete selected file / remove preset from playlist |
| `Ctrl+F` | Focus search bar in Library |
| `Ctrl+N` | Create new playlist (when in Browser state) |
| `Ctrl+S` | Save active playlist (when in Editor state) |
| `Ctrl+W` | Close active playlist editor |
| `Ctrl+Up / Down` | Reorder selected preset up/down in setlist |
