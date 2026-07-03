# Asset Management System

## Overview

The Asset Management System provides a unified interface for managing patches and playlists in Spirals Desktop. It features a three-panel layout optimized for VJ workflows.

## Accessing Asset Management Mode

- **Keyboard Shortcut**: Press `F3` to toggle between normal mode and asset management mode
- **Menu**: Select `Asset Manager (F3)` from the menu bar

## Panel Layout

### Left Panel: Asset Browser (35% width)

The Asset Browser is your file system navigator for both patches and playlists.

#### Features

**Collapsible Folder Tree**
- Click the `📁 Hide Tree` / `📁 Show Tree` button to toggle the folder sidebar
- Navigate through your preset folders
- Right-click folders to create new subfolders or refresh

**File List**
- Visual indicators distinguish file types:
  - 🎨 Patches (.lsd, .patch files)
  - 📋 Playlists (.playlist files)
  - 📁 Folders
- Invalid files show with ⚠ warning icon
- Search/filter bar to quickly find assets

**Context Menu (Right-Click)**

For Patches and Playlists:
- **Add to Playlist** - Adds the item to the currently open playlist
- **Add to Play Queue** → Next / After Current / At End
- **Replace & Play** - Clears queue, adds this item, and plays it
- **Clone** - Creates a copy with `_copy` suffix
- **Rename** - Rename the file (F2 shortcut)
- **Delete** - Remove the file (with confirmation)

For Folders:
- **New Folder...** - Create a subfolder
- **Refresh** - Reload the directory contents

### Middle Panel: Playlist Editor (35% width)

The Playlist Editor has two states:

#### State 1: Browser Mode (Default)

- Browse all available playlists
- Click `➕ New Playlist` to create a new playlist
- Double-click a playlist to open it in editor mode
- Right-click for context menu (Open, Delete)

#### State 2: Editor Mode

When a playlist is open:
- Header shows: `Editing: [Playlist Name]`
- Dirty indicator (*) appears if unsaved changes exist
- `💾 Save` button to save changes
- `✖ Close` button to return to browser mode

**Patch List**
- Numbered list showing all patches in order
- Drag handle (≡) for reordering
- ✖ button to remove patches
- Missing patches show with ⚠ warning and red text

**Operations**
- **Drag & Drop**: Drag patches from Asset Browser to insert them
- **Reorder**: Drag patches within the list to reorder
- **Context Menu** (Right-click on patch):
  - Remove
  - Move Up / Move Down
  - Play Now

### Right Panel: Mixer / Monitor (30% width)

Remains consistent across both modes, showing:
- Video output monitor
- Deck controls
- Mixer controls
- Play queue status

## Drag & Drop Matrix

| Source (Left Panel) | Destination (Middle Panel) | Action |
|---------------------|----------------------------|--------|
| Patch | Between patches in editor | Inserts patch at that index |
| Patch | Empty playlist area | Adds patch to end |
| Playlist | Between patches in editor | **Unpacks** playlist - inserts all its patches at that index |
| Playlist | Empty playlist area | Unpacks playlist - adds all patches to end |

## File Operations

### Non-Destructive Operations

These only modify playlist files, not the underlying patches:
- Reordering patches in a playlist
- Removing patches from a playlist
- Adding patches to a playlist

### Destructive Operations

These modify files on disk:
- **Rename**: Changes the actual filename
- **Delete**: Removes the file from disk
- **Move**: Relocates the file to a different folder
- **Clone**: Creates a physical copy of the file

⚠️ **Warning**: Renaming or moving a patch will break playlists that reference it!

## Handling Missing Assets

When a playlist references a patch that no longer exists:
- The patch row appears in red with ⚠ warning
- Hover over it to see the missing path
- Future enhancement: Right-click → "Relink Asset" to fix the reference

## Keyboard Shortcuts

### Global
- `F3` - Toggle Asset Management Mode
- `Ctrl+N` - New Playlist (when in browser mode)
- `Ctrl+S` - Save Playlist (when in editor mode)
- `Ctrl+W` - Close Playlist (when in editor mode)
- `F2` - Rename selected file
- `Delete` - Delete selected file

### Navigation
- `Ctrl+F` - Focus search bar
- Arrow keys - Navigate file list
- Enter - Open selected playlist

### Playlist Editing
- `Ctrl+↑` / `Ctrl+↓` - Move selected patch up/down
- `Delete` - Remove selected patch from playlist

## File Structure

### Patches
Located in: `presets/patches/`
- Supports `.lsd` and `.patch` extensions
- Can be organized in subfolders

### Playlists
Located in: `presets/playlists/`
- Extension: `.playlist`
- Plain text format, one patch path per line
- Comments start with `#`

Example playlist file:
```
# Spirals Playlist: Summer Vibes
# Generated: 2024-01-15T20:30:00

presets/patches/sunset_mandala.lsd
presets/patches/ocean_waves.lsd
presets/patches/golden_hour.lsd
```

## Best Practices

1. **Organize with Folders**: Create subfolders for different shows, moods, or styles
2. **Use Descriptive Names**: Name patches and playlists clearly
3. **Test Playlists**: Open and verify playlists before a performance
4. **Backup**: Keep copies of important playlists
5. **Avoid Moving Patches**: If you must move/rename patches, update playlists afterward

## Tips & Tricks

- **Quick Add**: Drag multiple patches at once by selecting them (future enhancement)
- **Playlist Templates**: Clone existing playlists as starting points
- **Search**: Use the search bar to quickly find patches by name
- **Preview**: Right-click → "Play Now" to preview a patch before adding it

## Troubleshooting

**Q: My playlist shows warning icons**
A: Some patches are missing. Check if they were moved or deleted.

**Q: Drag and drop isn't working**
A: Make sure you're dragging from the file list, not the folder tree.

**Q: Changes aren't saving**
A: Click the 💾 Save button in the playlist editor header.

**Q: Can't find my patches**
A: Check the current directory in the breadcrumb at the top of the Asset Browser.

## Future Enhancements

- Multi-select support
- Undo/redo for playlist editing
- Playlist metadata (description, tags, BPM)
- Thumbnail previews
- Favorites/starred system
- Export playlists to M3U format
- Automatic playlist validation and repair
- Drag to move files between folders
