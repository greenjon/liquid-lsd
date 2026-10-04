# Unified BrowserPane plan (2026-10-04)

Status: PLANNED, not started. Decisions recorded in memory `unified-browser-pane`.

## Goal
Replace the 4-column LibraryPanel and the Edit-view Browse bay (PerformanceBrowseBay + ShaderPickerPopup inline + ChainListBrowse) with one `BrowserPane`:

| Left ~25% | Middle ~50% | Right ~25% |
|---|---|---|
| Folder tree | name \| info list (FX chains list all 3 FX) | Queue(s), stacked |

Tabs SRC | FX | Trans (| Maps, see Open items) stay on top.

## Decisions (user, 2026-10-04)
- Edit view uses the FULL tree, queue ALWAYS visible. Fall back to compact only if it demonstrably doesn't fit.
- Transitions: one queue (taller), no history list.
- In v1.0 scope; no deadline. Keep the old Browse bay until the new pane reaches parity.
- Playlists are folders; selecting one makes the middle list reorderable (removes the three PlaylistEditor panels).
- Favorites is a folder everywhere (replaces "favorites only" checkbox and picker Favorites category).
- Stock folders are read-only (no add to playlist/queue), as today.

## Measured constraints (from code, estimates not runtime)
- Edit bay width == Library width (`libraryW = displayWidth - rightW`, UIManager.kt:479), about 830-900 px at 1280x720, 1400-1500 at 1920x1080. 25/50/25 gives roughly 210 / 420 / 210 at the minimum screen. Tight but workable if: tree and queue have min widths (~180), splitters are draggable, the info column elides.
- Edit bay height about 585 at 1280x720 with one visible row (shrinks with more rows), about 900 at 1080p.
- STEP 0 MEASURED 2026-10-04 (temporary stderr log at UIManager.kt:479, reverted; `--window=WxH`):
  - 1280 wide (display height came out 768): contentH 739, rightW 409 (Mixer could take 601 but is clamped), **libraryW 871** = `performanceMatrixPanel.calculateMinWidth` floor. Pane at 25/50/25: about 218 / 436 / 218.
  - 1920x1080: contentH 1051, rightW 878 (height/aspect-driven), **libraryW only 1042**, not the 1400+ estimated above. Pane: about 260 / 520 / 260.
  - So the left column is 871-1042 px across the supported range: it barely grows with the screen, because the Mixer column eats the extra width. Design for about 870-1050, not for "big screens".
  - The 871 floor is Deep Edit's own minimum (tabs + params + 450 reserved Properties + gaps), so the pane can never be narrower than that in Edit view.
  - Screenshot mode (`--screenshot-ui`) did not produce a PNG within 300 s here, so no visual mock yet; do the mock by hand or in the build.
- Library HALF is about 338 px tall at 720p; the tree needs to scroll independently, and the queue pane must still show several rows.

## Architecture
- `BrowserPane(kind, target: BrowseTarget?)`. `BrowseTarget` is null in Library view; in Edit view it is the slot (SRC / FX1-3 / Chain / TRANS) chosen by the existing bay tab row.
  - target == null: double-click loads to the deck, Enter/Q enqueues (current Library behavior).
  - target != null: click applies immediately via DeckOps / FxOps / TransitionOps, Ctrl+Z undoes (current bay behavior).
- One `BrowseModel` per kind (SRC, FX, TRANS) producing `rows` from stock + saved + chains + favorites + playlists. Replaces PresetListPanel / FXBrowserPanel / TransitionBrowserPanel list building and ShaderPickerPopup's lists. Search stays `SearchMatcher`.
- Folder tree nodes (virtual, synthesized):
  - SRC: All, Favorites, Stock, Saved (+ subfolders from relative path of `library/presets`), Playlists.
  - FX: All, Favorites, Stock filters (ISF `folderPath`), Saved single FX, Saved chains, Playlists.
  - TRANS: All, Favorites?, Stock, Saved, Playlists.
- Playlists: existing flat files (`library/playlists` .lsdplay, `fx_playlists` .lsdfxplay, `transition_playlists` .lsdtransplay); no storage change. Tree lists them as leaf folders. User subfolders for playlists are out of scope.
- Queue pane: SRC/FX show BG queue + A/B queue stacked (reuse existing queue panels); TRANS shows the single queue.
- Single nav model: one cursor system with panes tree -> list -> queue, replacing `LibraryNavigation` panes() and the picker's moveCursor/acceptCursor/stepCategory/ChainListBrowse. NavSurface keeps its API (browseStep/browseAccept/button).

## Steps
0. Measure: log libraryW/rightW/bay size at 1280x720 and 1920x1080; sketch the pane at those sizes (ASCII or screenshot) and confirm 25/50/25 works.
1. `BrowseModel` + row types + tree nodes (pure Kotlin, unit-tested: filtering, favorites, playlist membership, read-only stock rule).
2. `BrowserPane` UI, Library view only (target null): tree, list, queues, drag/drop, context menu, multi-select (reuse MultiSelectionModel, BrowserPopupHandler, BrowserActionToolbar). Swap LibraryPanel's body to it per tab.
3. Single nav model for the Library context; port LibraryNavigation + NavigationSurface; update midi-phase3-navigation-plan.md. Hardware check on the Twister.
4. Edit bay hosting: `target` mode inside PerformanceBrowseBay; apply-immediately and undo; port ChainListBrowse and picker nav; keep old path behind a flag until parity.
5. Delete ShaderPickerPopup inline mode, ChainListBrowse, PresetListPanel / FXBrowserPanel / TransitionBrowserPanel / the three PlaylistEditor panels and obsolete queue-action panels once unused.
6. Dirty-deck modal MIDI answer (separate, can ship earlier): expose `PopupManager.pendingDeckConfirm`; NavSurface routes button/browseAccept to Save/Discard/Cancel while non-null; extract the three button bodies from `drawDeckConfirmPopups` (PopupManager.kt:108-150).
7. Docs: DECISIONS.md entry, RELEASE_NOTES.md + docs/release_notes.md, docs/user_guide/presets_and_library.md, docs/developer/ui.md, tooltips, .planning/pre-release-ui-backlog.md, regenerate src/main/resources/docs/* (find how: script vs. by hand).

## Progress
- Step 0 DONE (measurements above).
- Step 1 DONE 2026-10-04 (uncommitted): `ui/browser/BrowseModel.kt` (`BrowseCatalog`, `BrowseEntry`, `BrowseScope`, `BrowseNode`, `BrowsePlaylist`, `BrowseKind`, `BrowseSection`) + `BrowseCatalogTest` (10 tests). Pure, no ImGui. Tree, scopes, search (via SearchMatcher), playlist resolution, reorderable-only-in-playlists, `folderOf`. Not yet wired to any panel: a later step builds catalogs from `ISFFilterRegistry` / `VisualSourceRegistry` / `ISFTransitionRegistry` / `FileSystemManager.scanAll*` and the playlist files.
- Correction: stock rows are read-only for SRC and FX playlists, but TRANSITION playlists store stock shader ids, so stock transitions CAN be added (`BrowseKind.stockInPlaylists`).
- Favorites: only FX has a store (`FxShortlist`, stock filters only). Catalog takes `favorites = null` for SRC/TRANS and then shows no Favorites folder. Adding SRC/TRANS favorites would be a new feature (decide separately).

- SRC/Trans favorites DECIDED (user, 2026-10-04): `BrowseFavorites` (SRC + TRANS JSON lists in `library/src_favorites.json` / `transition_favorites.json`; FX delegates to `FxShortlist`). Keys: stock id or saved file path. Done + tested (`BrowseFavoritesTest`). Context-menu star toggle wired for SRC only so far.
- `BrowseCatalogs` (done): builds catalogs from registries/scans/playlist files, info column (FX chain = "A > B > C", single FX, saved transition = filter name), cached by upstream values.
- Step 2, slice A DONE 2026-10-04 (uncommitted): `ui/browser/BrowserPane.kt` for the Sources tab only. Beta toggle "Unified" in the Library tab bar (or `-Dlsd.unifiedBrowser=true`), not persisted; classic 4 columns stay the default and were verified unchanged by screenshot. `PresetListPanel.drawRows` extracted so the old column and the pane share one row renderer (drag, context menus, audition, delete, selection) and gained `favoriteKeys` / `infoFor` / `contextExtras`. Verified visually at 1280x768 (tree 218, list 430, queues 218); full test suite green.
- Known gaps in slice A (to reach parity before it becomes the default):
  - No "+" new-preset button, "..." menu, or Stock/Saved filter; the tree replaces the filter, new-preset needs a home (toolbar above the list).
  - Playlists: selecting one shows its rows but cannot reorder/remove (PlaylistEditorPanel did); "new playlist/rename/delete" have no entry point.
  - Queues are two half-height panes in HALF Library (about 7 rows each at 720p); consider a splitter or tabs if cramped.
  - Info column is drawn over the row and can overlap long names (no clipping).
  - Tree has no collapse/expand yet and no remembered scope across restarts.
  - FX and Trans tabs still classic; LibraryNavigation / MIDI nav still follow the old pane order (step 3).
- Dev note: `--screenshot-ui` only fires when `--screenshot-after-frames` is below the frames drawn per second (frameCount resets every second in Main.kt:465); use `--screenshot-after-frames=10`.

- Sources parity DONE 2026-10-04 (4fa09b6): list toolbar (+, ...), playlist reorder/remove/new/rename/clone/delete from the tree, tree fold, info clipping. Not done: scope persistence across restarts (Library tab itself isn't persisted), queue splitter.
- Step 2 FX + Trans DONE 2026-10-04 (uncommitted until this note's commit): `FXBrowserPanel.drawRows` / `TransitionBrowserPanel.drawRows` extracted; `PlaylistEdit.kt` (`PlaylistEdit`, `PresetPlaylistEdit`, `TokenPlaylistEdit`, `PlaylistRows`, `PlaylistItems` + test) shared by all three kinds; pane is kind-aware (queues: FX = BG + A/B stacked, Trans = one full-height queue). "Unified" toggle now covers SRC/FX/Trans. Dev: `-Dlsd.libraryTab=fx|trans` picks the initial Library tab for screenshots. Verified by screenshot at 1280x768.
  - Not yet verified by hand: FX/Trans playlist drag-reorder and insert, FX overwrite popup from the pane, FX/Trans multi-select (FX/Trans keep single selection).
  - FX/Trans have no playlist transport beyond "add all to queue" (same as classic).

## Open items
- MAPS tab (`LibraryViewMode.MAPS`, MapsBrowserPanel; saved macro banks in `library/knobpresets` + Perform pages; no queues, nothing loads to a deck). DECIDED 2026-10-04: keep it as a 4th tab in the same shell, same 25/50/25 geometry, but not forced into the queue model: left = Banks | Pages, middle = list, right = detail/actions (save-from / apply-to bank, show/hide/copy/delete page) instead of queues. Library view only; not a target in Edit view. Its panel ports in step 2 with minimal change; MIDI nav stays `emptyList()` for it.
- Presets have no folder field on `AssetItem`; derive from `walkTopDown` relative path (step 1).
- Saved FX/chains/transitions have no subfolder convention; tree shows them flat unless we add one.
- Hover/loaded badge on the applied item (open item in MIDI phase 3 plan) comes cheap in step 1-2.
- FX queue transport and knobs 2-16 in browse (MIDI phase 3 leftovers) can ride on the unified nav in step 3.
