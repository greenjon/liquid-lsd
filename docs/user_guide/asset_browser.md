# Asset Browser & Playlist Management

Liquid LSD includes a dedicated **Asset Management System** for organizing visual patches, building live performance setlists, and managing play queues.

---

## Toggling Modes

Liquid LSD features two primary interface layouts:

1. **Performance Mode (Default): Preset Grid, Cell Config, and Mixer / Monitor panels.
2. **Asset Management Mode**: Asset Browser, Playlist Editor, and Mixer / Monitor panels.

### How to Toggle
- **Keyboard Shortcut**: Press `F3` at any time to switch modes instantly.
- **Menu Bar**: Select `File → Asset Manager (F3)`.

---

## Panel Layout in Asset Management Mode

```
┌──────────────────┬──────────────────┬──────────────────┐
│  Asset Browser   │  Playlist Editor │  Mixer / Monitor │
│   (35% width)    │   (35% width)    │   (30% width)    │
│                  │                  │                  │
│  Directory tree, │  Setlist editor  │  Master output,  │
│  patch files,    │  & playlist      │  Deck A/B/C,     │
│  context menus   │  browser         │  AutoVJ queue    │
└──────────────────┴──────────────────┴──────────────────┘
```

---

## Asset Browser (Left Panel)

The Asset Browser provides a complete file system view of your presets and playlists (`presets/patches/` and `presets/playlists/`).

### Features & Navigation
- **Collapsible Sidebar Tree**: Click `📁 Show Tree` / `📁 Hide Tree` to toggle the directory sidebar.
- **Search & Filter**: Type into the search bar at the top to quickly locate specific patch files or subfolders.
- **Visual File Type Icons**:
  - 🎨 **Patches** (`.lsd`, `.patch`): Visual presets.
  - 📋 **Playlists** (`.playlist`): Setlist files.
  - 📁 **Folders**: Directories for organizing sets.
  - ⚠ **Invalid / Missing**: Highlighted in red with a warning icon.

### Context Menu Actions (Right-Click)

For Patches & Playlists:
- **Add to Playlist**: Appends the item to the active playlist open in the editor.
- **Add to Play Queue**: Inserts item into the live AutoVJ queue (*Next*, *After Current*, or *At End*).
- **Replace & Play**: Clears the current queue, loads the item, and triggers immediate playback.
- **Clone**: Creates a duplicate file with `_copy` appended.
- **Rename (`F2`)**: Renames the file on disk.
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
Shows the active patch order for a performance setlist:
- **Header Status**: Displays `Playlist: [Setlist Name]` with an unsaved changes indicator (`*`).
- **Context Menu (Right-Click)**: All playlist options (Play now, Insert into queue, Add to queue, Save, Rename, Clone, Delete) are accessed by right-clicking in the Playlist Editor panel or header bar.

---

## Drag-and-Drop Matrix

Liquid LSD supports intuitive drag-and-drop workflows across panels:

| Dragged Item (Asset Browser) | Target Destination (Playlist Editor) | Resulting Action |
|------------------------------|--------------------------------------|------------------|
| **Preset file** | Between presets in playlist | Inserts preset at target index |
| **Preset file** | Empty playlist area | Appends preset to end of playlist |
| **Playlist file** | Inside active playlist editor | **Flat Unpacks** all patches from the source playlist into the active setlist |
| **Patch within playlist** | Reorder within active playlist | Reorders preset sequence |

---

## Handling Missing Assets

If a playlist references a patch file that was moved or deleted from disk:
- The patch row appears in **red** with a ⚠ warning icon.
- Hovering shows the missing path.
- Right-click the missing patch row and select **Relink Asset…** to locate the relocated `.lsd` file and repair the playlist reference.

---

## Keyboard Shortcuts Cheatsheet

| Shortcut | Action |
|----------|--------|
| `F3` | Toggle Asset Management Mode / Performance Mode |
| `F2` | Rename selected file in Asset Browser |
| `Delete` | Delete selected file / remove patch from playlist |
| `Ctrl+F` | Focus search bar in Asset Browser |
| `Ctrl+N` | Create new playlist (when in Browser state) |
| `Ctrl+S` | Save active playlist (when in Editor state) |
| `Ctrl+W` | Close active playlist editor |
| `Ctrl+Up / Down` | Reorder selected patch up/down in setlist |
