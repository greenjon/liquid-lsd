# Asset Management Quick Start

## Accessing Asset Management Mode

Press **F3** or click **File → Asset Manager** to toggle between:
- **Performance Mode**: Patch Grid, Cell Config, Mixer/Monitor
- **Asset Management Mode**: Asset Browser, Playlist Editor, Mixer/Monitor

## Asset Browser (Left Panel)

### Navigation
- Click folders to expand/collapse
- Click assets to select them
- Breadcrumb trail shows current path

### File Operations
- **Right-click** for context menu:
  - Rename
  - Clone (creates copy)
  - Delete (with confirmation)
  - New Folder
- **Drag files** to folders to move them
- **Delete key** to delete selected asset

### Visual Indicators
- 📁 **Folders** - Blue text
- 🎨 **Patches** - Green text
- 📋 **Playlists** - Yellow text
- ⚠ **Invalid/Missing** - Red text

## Playlist Editor (Center Panel)

### Browser Mode (Default)
- Lists all playlists in current directory
- **Double-click** playlist to edit
- **"+ New Playlist"** button to create
- Navigate folders with breadcrumbs

### Editor Mode
- View and edit patch list
- **Drag patches** from Asset Browser to add
- **Drag playlists** from Asset Browser to unpack (all patches inserted)
- **Drag within list** to reorder
- **Right-click patch** for options:
  - Remove
  - Relink (if missing)
- **Save** button to save changes
- **Back** button to return to browser

### Missing Patches
- Shown in **red** with ⚠ icon
- Right-click → "Relink..." to fix
- Browse to new location

## Drag-and-Drop Workflows

### Add Patch to Playlist
1. Open playlist in editor
2. Drag patch from Asset Browser
3. Drop at desired position
4. Save playlist

### Unpack Playlist into Another
1. Open target playlist in editor
2. Drag source playlist from Asset Browser
3. Drop at desired position
4. All patches from source are inserted
5. Save playlist

### Reorder Patches
1. Open playlist in editor
2. Drag patch within list
3. Drop at new position
4. Save playlist

### Move Files
1. Drag file from asset list
2. Drop on folder in tree
3. File is moved

## Keyboard Shortcuts

- **F3** - Toggle asset management mode
- **Delete** - Delete selected asset
- **Escape** - Cancel rename operation

## Tips

- **Unsaved changes** are indicated with asterisk (*)
- **Refresh** button updates file list
- **Playlists** use absolute paths for portability
- **Clone** creates copy with "_copy" suffix
- **Delete** requires confirmation

## Directory Structure

```
spirals-desktop/
├── patches/
│   ├── user/          # Your patches
│   └── factory/       # Built-in patches
└── playlists/
    ├── live_shows/    # Performance playlists
    ├── studio/        # Studio sessions
    └── experiments/   # Experimental sets
```

## Common Tasks

### Create a New Playlist
1. Press F3 to enter asset mode
2. Click "+ New Playlist" in Playlist Editor
3. Enter name
4. Drag patches from Asset Browser
5. Click Save

### Organize Patches
1. Press F3 to enter asset mode
2. Right-click in Asset Browser → New Folder
3. Drag patches to folder
4. Rename/clone as needed

### Fix Missing Patches
1. Open playlist with missing patches
2. Missing patches shown in red
3. Right-click missing patch → Relink
4. Browse to new location
5. Save playlist

### Combine Playlists
1. Open target playlist
2. Drag source playlist from Asset Browser
3. All patches are inserted
4. Save combined playlist

## Troubleshooting

**Assets not showing?**
- Click Refresh button
- Check file extensions (.patch, .playlist)

**Can't drag files?**
- Ensure source and target are compatible
- Check console for errors

**Missing patches?**
- Use Relink to fix paths
- Check if files were moved/deleted

**Changes not saved?**
- Click Save button in editor
- Look for asterisk (*) indicator
