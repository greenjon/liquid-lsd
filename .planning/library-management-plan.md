# Library management: folders, tag crates, relocation (v1.0)

Decided 2026-10-27: this is **v1.0 scope**, as a usability item. A growing library needs folders the app can manage and
crate-style groupings. We take a small subset of Mixxx's library management, not all of it.

## Status

- **Phase 1 DONE 2026-10-27** (uncommitted, tests pass, unseen in app). `LibraryRefs` lives in `ui/browser/` (next to the favorites
  and recents stores it updates), not `presets/`. Rename and delete popups use it; `drawRenameAssetPopup`,
  `drawDeleteAssetConfirmationPopup` and `BrowserDock.drawPopups` now take the `Mixer` for its `fxChains`. Also follows
  `FxOps` slot sources (saved singles in slots), which the plan below didn't list. Tests: `LibraryRefsTest`.
- Phases 2-4 not started.

## Decisions (2026-10-27)

1. **Delete folder works on non-empty folders.** The confirmation shows the impact, not just a count: "Delete 'garden': 23
   presets, used in 2 playlists and the A/B queue." Deletion tries the OS trash (`Desktop.moveToTrash` when
   `Desktop.Action.MOVE_TO_TRASH` is supported) and falls back to a permanent delete. Single-file delete moves to the same path.
2. **FX favorites cover saved singles and chains**, in a separate store `library/fx_saved_favorites.json` keyed by path. The
   FX tree's Favorites node shows both. `FxShortlist` stays stock-only, because the slot ◀/▶ stepper walks shader ids and
   can't recognise a saved single in a slot. A stepper that loads saved singles is v1.1.
3. **Save As default folder, in order:** (a) the folder of the file the deck was loaded from (`PresetManager.activeFile`),
   so a variation lands next to the original; (b) otherwise the tree selection when it is a saved folder; (c) otherwise the
   root. The save modal always shows the destination as a folder dropdown. The same rule applies to FX chain saves
   (`FxChainHeader` already defaults to `chain.sourceFile?.parentFile`).

## Where we are

- **Folders are read-only.** `BrowseCatalog.tree()` ([BrowseModel.kt](../src/main/kotlin/llm/slop/liquidlsd/ui/browser/BrowseModel.kt))
  builds subfolder nodes from each entry's `folder` (`BrowseCatalog.folderOf`). Folders exist only if a file is in them, so
  an empty folder can't appear. No UI creates, renames, moves or deletes folders. `FileSystemManager.moveFile` exists, but
  nothing calls it.
- **Saving always writes to the root.** `PresetManager.saveFileFor` writes `library/presets/<name>.lsd` unless the preset is
  being re-saved under its own name. Selecting a folder in the tree has no effect on where a save goes.
- **Playlists hold paths.** `.lsdplay` holds line-based relative paths (`PlaylistManager`). `.lsdfxplay` and
  `.lsdtransplay` hold tokens (`TokenPlaylistEdit`). When a file is missing, its entry is hidden and a
  "playlist item(s) not found" note appears.
- **The tree shows playlists flat.** The `scanAll*Playlists` functions walk subfolders, but the tree lists every playlist at
  depth 1.
- **Tags** are free-text and searchable (`SearchMatcher`), but the tree doesn't show them. Deck presets edit tags through
  the Rename/Edit Tags modal.
- **Favorites**: SRC and TRANS have their own stores (`library/src_favorites.json` and `library/transition_favorites.json`).
  FX favorites come from `FxShortlist`, which holds stock filters only. **Recent** keeps 20 per kind, keyed by path.

### Bugs in today's rename (fixed by phase 1)

`BrowserPopupHandler`'s rename calls `PlaylistManager.updatePresetPathInAllPlaylists` **for deck presets only**. Renaming an
FX single, an FX chain or a transition preset breaks every playlist entry that points at it. No rename of any kind updates:
- live queues (A/B, BG, A/B FX, BG FX, Transition)
- favorites and recents (keys are paths)
- `PresetManager.activeFiles`, which holds the deck's file for save-back
- `FxChain.sourceFile`, which holds the chain's file and its dirty baseline
- the preset's garden thumbnail sidecar (`<name>.png` next to the preset, see `GardenThumbnails.thumbnailFor`)

Moving files between folders would run into all of these at once. That's why the reference rewrite comes first.

## Phase 1: one path-rewrite service, and rename fixed for every kind

Add `LibraryRefs.relocate(moves: Map<File, File>, fxChains)` in `ui/browser/`, a single entry point that rewrites every
reference to a moved or renamed file:
1. Playlists of that kind. For `.lsdplay`, reuse the logic in `updatePresetPathInAllPlaylists`. For `.lsdfxplay` and
   `.lsdtransplay`, add a `TokenPlaylistEdit.replaceFile(old, new)` that mirrors `removeFile`.
2. Live queues: a `replaceFileInQueue(old, new)` on each queue manager, next to `removeFileFromQueue`.
3. Favorites and recents: rewrite the keys and bump the version.
4. `PresetManager.activeFiles` and the `FxChain.sourceFile` of each of the 5 chains. `last_session.json` is written from
   live state, so it follows automatically.
5. Thumbnail sidecars move along with their preset.

One file is a map of size 1, so rename is just a special case. Rename, phase 2's folder ops and phase 4's relocate all go
through this one call. Delete gets a matching `LibraryRefs.forget(kind, files)`, which replaces the per-type `when` blocks
in the delete popup.

**Tests:** for each kind, rename or move a file, then assert that its playlists, queues, favorites and recents all point at
the new path. Moving a folder of N files rewrites all N.

## Phase 2: folder management

Saved sections only (Saved Presets, Singles, Chains, Saved Transitions). Stock sections stay read-only.

- **Empty folders appear.** The catalog also lists the directories under each saved root, not only the folders that hold
  entries. The count shows nothing when the folder is empty, as it does today.
- **New folder**: a `FOLDER_PLUS` icon button above the tree, plus **New folder…** on a section or folder node's context
  menu. It creates the folder inside whichever saved section or folder is selected.
- **Rename folder… / Move folder** on a folder node's context menu. They call `relocate` with every file inside.
- **Delete folder…** opens the same confirmation modal as delete, listing how many items it will remove. Cleanup then
  goes through `LibraryRefs.forget`.
- **Moving rows**:
  - Drag rows, or a multi-selection, onto a folder node or section node.
  - A **Move to ▸** submenu in the row menu and the batch menu, listing the folder tree of that kind's saved root.
  - A move can't cross kinds or sections (a chain can't go into Singles).
  - When the destination already has a file with that name, ask: skip, or keep both with a `_2` suffix.
- **Save goes where you're looking.** If a saved folder is selected in the tree, Save As writes there. The save modal shows
  the destination folder as a dropdown, defaulting to the selected folder.
- Folder names follow the same character rules as preset names, and nothing may resolve outside the managed root:
  `managedPathViolation` already enforces this, so keep it on every new path.

## Phase 3: tags as crates

Mixxx crates are unordered, overlapping groups, and our tags already work that way. A tag is stored in the preset file
itself, so it travels with the preset when shared. No new file format.

- A **Tags** group in the tree, folded by default, with one node per distinct tag and a count. Add a `BrowseScope.Tag(name)`
  that filters on `asset.tags` (`categories` for stock rows).
- Batch actions: **Add tag… / Remove tag ▸** on single and multi-selections. Dropping rows on a tag node adds that tag.
- Every saved kind gets tag editing: check whether FX singles, chains and transitions have the deck preset's
  Rename/Edit Tags modal, and add it where missing.
- **Rename tag… / Delete tag** on a tag node rewrites the tag in every file that carries it.

## Phase 4: missing items, and playlist folders

- **Missing items stay visible.** A playlist entry whose file is gone shows as a dimmed ⚠ row instead of being hidden,
  with **Locate…** (file picker) and **Remove**. Above the list, **Find missing** looks under the kind's root for a file
  with the same name. When there's exactly one match, it relocates the entry through `LibraryRefs.relocate`. When there
  are several, it lists them.
- **Playlist folders**: nest playlist nodes under their subfolders, the way section folders nest, and give them the same
  New folder / Move / Rename actions. This is small once phase 2 exists.

## Not doing (v1.1 or never)

Ratings, play counts, analysis (BPM/key), Auto DJ (the queues cover it), multiple library roots, smart or saved-search
crates, and sortable columns (v1.1).

## Each phase also needs

- Tooltips on every new button and menu item.
- `docs/user_guide/presets_and_library.md`: the folder tree, Move to, Tags, missing items. The page currently says
  "Favorites (FX)", which is stale, because SRC and TRANS have favorites too.
- Both release-notes files, `DECISIONS.md` (library references are rewritten in one place), and the regenerated docs site.
- Entries in `.planning/app-checklist.md`.
- A check that Twister/MIDI browse navigation (MIDI phase 3) still steps through the tree with folder and tag nodes, and
  that the pair-view Browse dock gets the same menus.

