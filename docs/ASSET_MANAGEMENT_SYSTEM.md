# Asset Management System

## Overview

The Asset Management System provides a comprehensive interface for organizing, browsing, and managing patches and playlists in Spirals Desktop. It introduces a dual-mode UI that can be toggled between the standard performance view and an asset management view.

## Architecture

### Core Components

1. **FileSystemManager** (`FileSystemManager.kt`)
   - Handles all file system operations (rename, clone, move, delete)
   - Scans directories and categorizes assets (patches, playlists, folders)
   - Validates file existence and provides error reporting
   - Thread-safe operations with Result-based error handling

2. **PlaylistManager** (`PlaylistManager.kt`)
   - Manages playlist file I/O (load, save, create)
   - Supports playlist editing operations (insert, remove, move, relink)
   - Validates patch references and tracks missing files
   - Implements dirty state tracking for unsaved changes
   - Supports "flat unpacking" of playlists into other playlists

3. **AssetBrowserPanel** (`AssetBrowserPanel.kt`)
   - Left panel in asset management mode
   - Dual-pane layout: folder tree + asset list
   - Supports drag-and-drop for file organization
   - Context menu operations (rename, clone, delete)
   - Visual indicators for invalid/missing assets

4. **PlaylistEditorPanel** (`PlaylistEditorPanel.kt`)
   - Center panel in asset management mode
   - Two states: Browser (list playlists) and Editor (edit specific playlist)
   - Drag-and-drop support for:
     - Reordering patches within playlist
     - Adding patches from asset browser
     - Unpacking playlists into other playlists
   - Missing patch detection and relinking

### Data Models

```kotlin
// Asset representation
data class AssetItem(
    val name: String,
    val path: String,
    val type: AssetType,
    val isValid: Boolean,
    val errorMessage: String? = null
)

enum class AssetType {
    FOLDER,
    PATCH,
    PLAYLIST
}

// Playlist representation
data class Playlist(
    val name: String,
    val filePath: String,
    val patches: MutableList<String>
) {
    val isDirty: Boolean
    fun validatePatches(): List<String>
    fun markClean()
}
```

## UI Integration

### Mode Toggle

The asset management mode can be toggled via:
- **Menu**: File → Asset Manager
- **Keyboard**: F3 key
- **Variable**: `UIManager.showAssetManagementMode`

### Layout Modes

#### Default Mode (Performance)
```
┌─────────────┬──────────────────┬─────────────┐
│ Patch Grid  │  Cell Config     │ Mixer/      │
│   (30%)     │     (40%)        │ Monitor     │
│             │                  │  (30%)      │
└─────────────┴──────────────────┴─────────────┘
```

#### Asset Management Mode
```
┌──────────────┬──────────────────┬─────────────┐
│ Asset        │  Playlist        │ Mixer/      │
│ Browser      │  Editor          │ Monitor     │
│  (35%)       │    (35%)         │  (30%)      │
└──────────────┴──────────────────┴─────────────┘
```

## Features

### Asset Browser

**Folder Navigation**
- Collapsible tree view of directory structure
- Click to navigate into folders
- Drag assets to folders to move them

**Asset Operations**
- **Rename**: F2 key or context menu
- **Clone**: Creates copy with "_copy" suffix
- **Delete**: Delete key or context menu (with confirmation)
- **Move**: Drag-and-drop to folders

**Visual Indicators**
- 📁 Folders
- 🎨 Patches
- 📋 Playlists
- ⚠ Invalid/missing files (shown in red)

### Playlist Editor

**Browser Mode**
- Lists all playlists in current directory
- Double-click to open in editor
- Create new playlists
- Navigate folder hierarchy

**Editor Mode**
- View and edit patch list
- Reorder patches via drag-and-drop
- Add patches from asset browser
- Remove patches
- Relink missing patches
- Save changes
- Visual warnings for missing patches

**Drag-and-Drop Behavior**
- **Patch → Playlist**: Inserts patch at drop location
- **Playlist → Playlist**: Unpacks all patches from source playlist (flat unpack)
- **Within Playlist**: Reorders patches

## File Formats

### Playlist Format (.playlist)

```
# Spirals Playlist: My Setlist
# Generated: 2024-01-15T10:30:00

/absolute/path/to/patch1.patch
/absolute/path/to/patch2.patch
/absolute/path/to/patch3.patch
```

- Simple text format, one patch path per line
- Lines starting with `#` are comments
- Empty lines are ignored
- Absolute paths for portability

## Directory Structure

```
spirals-desktop/
├── patches/
│   ├── user/
│   │   ├── subfolder1/
│   │   └── subfolder2/
│   └── factory/
└── playlists/
    ├── live_shows/
    ├── studio/
    └── experiments/
```

## API Reference

### FileSystemManager

```kotlin
// Scan directory for assets
fun scanDirectory(directory: File): List<AssetItem>

// File operations
fun renameFile(path: String, newName: String): Result<String>
fun cloneFile(path: String): Result<String>
fun moveFile(sourcePath: String, targetDir: String): Result<Unit>
fun deleteFile(path: String): Result<Unit>
fun createDirectory(parentPath: String, name: String): Result<File>

// Root directories
fun getPatchesRoot(): File
fun getPlaylistsRoot(): File
```

### PlaylistManager

```kotlin
// Playlist I/O
fun loadPlaylist(file: File): Result<Playlist>
fun savePlaylist(playlist: Playlist): Result<Unit>
fun createPlaylist(name: String, directory: File): Result<Playlist>

// Playlist editing
fun insertPatch(playlist: Playlist, patchPath: String, index: Int): Result<Unit>
fun removePatch(playlist: Playlist, index: Int): Result<Unit>
fun movePatch(playlist: Playlist, fromIndex: Int, toIndex: Int): Result<Unit>
fun unpackPlaylistInto(targetPlaylist: Playlist, sourcePlaylistPath: String, insertIndex: Int): Result<Unit>
fun relinkPatch(playlist: Playlist, index: Int, newPath: String): Result<Unit>
```

## Error Handling

All file operations return `Result<T>` for safe error handling:

```kotlin
FileSystemManager.renameFile(path, newName).onSuccess { newPath ->
    logger.info { "Renamed to $newPath" }
}.onFailure { error ->
    logger.error(error) { "Rename failed" }
}
```

## Future Enhancements

### Planned Features
- [ ] Search/filter assets by name or tags
- [ ] Bulk operations (multi-select)
- [ ] Asset preview/thumbnail
- [ ] Import/export playlists
- [ ] Playlist templates
- [ ] Recent files list
- [ ] Favorites/bookmarks
- [ ] Undo/redo for playlist edits

### Integration Points
- [ ] Load patches directly from asset browser to decks
- [ ] Append to play queue from asset browser
- [ ] Insert after current in play queue
- [ ] Replace and play from asset browser

## Performance Considerations

- Directory scanning is performed on-demand (not continuous)
- File operations are synchronous but fast (local filesystem)
- Large playlists (>1000 patches) may impact UI responsiveness
- Consider pagination for very large directories

## Thread Safety

- All file operations are performed on the UI thread
- No background threads or async operations currently
- File watchers not implemented (manual refresh required)

## Testing

Unit tests are provided for:
- `FileSystemManagerTest`: File operation validation
- `PlaylistManagerTest`: Playlist I/O and editing

Run tests with:
```bash
./gradlew test --tests "llm.slop.spirals.ui.*Test"
```

## Troubleshooting

### Assets not appearing
- Click "🔄 Refresh" button
- Check file permissions
- Verify file extensions (.patch, .playlist)

### Missing patches in playlist
- Red ⚠ indicator shows missing files
- Use "Relink..." context menu to fix
- Check if files were moved or deleted

### Drag-and-drop not working
- Ensure source and target are compatible
- Check console for error messages
- Verify file permissions

## Implementation Notes

### ImGui Drag-Drop API

The implementation uses ImGui's drag-drop system:

```kotlin
// Drag source
if (ImGui.beginDragDropSource()) {
    ImGui.setDragDropPayload("ASSET_ITEM", asset.path as Any)
    ImGui.text("${asset.name}")
    ImGui.endDragDropSource()
}

// Drop target
if (ImGui.beginDragDropTarget()) {
    val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
    if (payload != null) {
        // Handle drop
    }
    ImGui.endDragDropTarget()
}
```

### Keyboard Shortcuts

- **F3**: Toggle asset management mode
- **F2**: Rename selected asset (planned)
- **Delete**: Delete selected asset
- **Escape**: Cancel rename operation

## Credits

Implemented as part of the Spirals Desktop asset management overhaul.
