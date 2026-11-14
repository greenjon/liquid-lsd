# Add to Playlist submenu (2026-11-14)

**Status: built 2026-11-14, uncommitted.** Unit tests pass. A scripted real run (tour build with probes, 1280x720) passed 4 cases. Pair and Edit views do draw the New Playlist modal (`PerformanceBrowseBay` calls `BrowserDock.drawPopups`), so New Playlist stays in those menus too.

From the ticked in-app checklist (section 1, menus): "we should make 'add to playlist' open a side menu with the playlists and the
subfolders of playlists".

## Today

- Each row menu has one item, `Add to '<name>'`, and only when a playlist of that kind is selected in the tree:
  - Sources: `PresetListPanel.kt` ~323 (`LibraryPanel.activePlaylistData`, single and multi in one block)
  - FX: `FXBrowserPanel.kt` `drawSavedItems` ~355 and `drawMultiMenu` ~227 (`selectedFxPlaylistFile`)
  - Transitions: `TransitionBrowserPanel.kt` `drawContextMenu` ~216 and `drawMultiMenu` ~174 (`selectedTransitionPlaylistFile`)
- Three separate append paths:
  - Sources go through `PlaylistManager.insertPreset`.
  - FX and Transitions each have a private `appendToActive*Playlist` that rewrites the JSON.
- **Bug found:** the FX menu path stores the absolute path, while dragging stores the short token (`PlaylistItems.tokenForPayload`).
  Playlists filled from the menu break when the library moves.
- New Playlist (`BrowserPopupHandler.drawNewPlaylistPopup`):
  - always creates at the playlists root;
  - decides the kind from `LibraryPanel.viewMode`;
  - has no way to add items once the playlist is created.
- Building blocks already there:
  - `LibraryFolders.rootOf(kind, PLAYLISTS)`, `extensionOf`, `dirsUnder` (cached, hidden folders skipped)
  - `TokenPlaylistEdit.open(kind, file).insertPaths(paths, at)` (writes short tokens)
  - `PlaylistManager.loadPlaylist` / `insertPreset`
  - `ToastOverlay.show`

## Design

The row menu reads like this:

```
Add to A/B Queue
Add to BG Queue
Add to 'warmup'          <- kept when a playlist is selected in the tree (one-click fast path)
Add to Playlist        ▸ ├ closing set
                         ├ warmup              ✓ (the selected one)
                         ├ Gigs              ▸ ├ 2026-11 club
                         │                     ├ ...
                         │                     ├ ──────────
                         │                     └ New Playlist Here...
                         ├ ──────────
                         └ New Playlist...
```

- With several rows selected, the labels become `Add 3 to Playlist` and `Add 3 to 'warmup'`.
- **Order inside each level:** playlists first, then subfolders, each sorted case-insensitively. This matches the tree.
- **An empty folder** shows a disabled `(no playlists)` line plus `New Playlist Here...`.
- **Which rows get the submenu** stays as today:
  - Sources: saved presets (stock generators and live streams can't go in playlists);
  - FX: saved singles and chains (stock filters can't);
  - Transitions: stock and saved.
- **Adding** appends to the end of the playlist. Duplicates are allowed, as when dragging.
- **After adding**, a toast says `Added 3 to 'closing set'`. The tree selection doesn't change, so the list you're browsing stays put.
- **New Playlist... / New Playlist Here...** opens the existing modal, which then:
  - creates the playlist in that folder;
  - adds the rows;
  - selects the new playlist (as New Playlist does today).

## Steps

1. **`ui/browser/PlaylistTargets.kt` (new, pure, unit tested).**
   - `tree(kind): Node` turns the root, `dirsUnder` and the matching files per folder into nested `Node(folder, playlists, children)`.
   - `append(kind, file, paths): Result<Int>`, one path for all three kinds:
     - **SRC:** use `LibraryPanel.activePlaylistData` when it is that file (so the open list updates), else `PlaylistManager.loadPlaylist`.
       Then `insertPreset` at the end for each path.
     - **FX / TRANS:** `TokenPlaylistEdit.open(kind, file)?.insertPaths(paths, size)`. This writes short tokens and fixes the FX
       absolute-path bug. A stock transition's `.fs` path becomes its id (`tokenForPayload`).
     - Then `LibraryPanel.refreshAssets()`.
2. **`ui/browser/PlaylistMenu.kt` (new).** `draw(kind, paths, countLabel)` draws:
   - the fast-path item;
   - the `Add to Playlist` submenu, recursing with `beginMenu` per folder;
   - the `New Playlist...` items.

   It calls `append` and shows the toast.
3. **New Playlist modal.** Give it an explicit request instead of reading `viewMode`:
   - `BrowserPopupHandler.openNewPlaylist(kind, dir = root, addPaths = emptyList())`.
   - The `+` list button in `BrowserPane.kt` ~272 calls it with no paths, so it behaves as before.
   - The modal creates the playlist in `dir`, then calls `PlaylistTargets.append`.

   This also stops a wrong-kind playlist from being created if the modal is ever opened from the Edit or pair-view Browse bay while
   `viewMode` is another tab. Check that `BrowserDock.drawPopups` runs there too. If it doesn't, the menu must hide `New Playlist...`
   in those views.
4. **Wire the three panels**, in both the single-row and the multi-row menus. Delete `appendToActiveFxPlaylist` and
   `appendToActiveTransitionPlaylist`.
5. **Tests.**
   - `PlaylistTargetsTest` on a temp dir:
     - nested folders, `.trash` skipped, wrong extensions skipped, sorting;
     - append for all three formats: Sources relative, FX and Transitions short tokens, stock transition id;
     - append to the active source playlist updates the open object.
   - Keep `PlaylistItemsTest` passing.
6. **Real run** (Xvfb, as in the auto-verify rounds):
   - right-click on each tab, a nested folder two levels deep, New Playlist Here, multi-select;
   - a screenshot at 1280x720 to check the submenu opens on screen near the right edge.
7. **Docs and the rest:**
   - `docs/user_guide/presets_and_library.md` (menu lines ~241 and ~280, and the FX menu);
   - both release-notes files;
   - `docs/developer/ui.md` (Library menus);
   - row tooltips if they mention playlists;
   - a new item in `.planning/app-checklist.md`.

## Risks

- **A very long playlist list** (dozens at one level) makes a menu taller than the screen. ImGui clamps menus to the display without
  scrolling. That's unlikely for now; if it happens, cap a level at about 30 entries and put the rest under `More...`.
- **Removing the FX absolute-path write** changes what new FX menu adds look like. Existing playlists with absolute paths still
  resolve, because `tokenNamesFile` matches both forms.

## Size

Two new files of about 150 lines, about 60 lines changed across the three panels and the modal, and one test file. Half a day
including the real run.
