# Lane 1b: correctness of changes since audit-2026-10-29, `ui/` package

Scope: the 56 files from `git diff --stat audit-2026-10-29 HEAD -- src/main/kotlin/llm/slop/liquidlsd/ui`. Every diff read; full
control flow read for BrowserPopupHandler (New Playlist and export modals), PlaylistMenu/PlaylistTargets/PlaylistEdit, PresetListPanel
(clipper and press/release select), BrowserPane (drop target, search Esc), FileSystemManager (scan cache and background revalidate),
SavePresetModal, ModalKeys, NameCheck, DeckPresetController, PerformanceDeepEditBay / PerformanceMatrixPanel / UIManager (undo key
routing), QueueKnobs, ParametersRenderer (reset undo), AppPreferencesStore (randomization migration). Tooltip wording was skipped (lane 5).

## Checklist coverage

| Checklist item | Result |
|---|---|
| Thread 0: no file I/O, network, sleep, blocking joins, MIDI open/close | findings L1b-1, L1b-2 (new menu/drop/modal paths read and write files on Thread 0; per-frame directory listing and stat while a menu or modal is open). No sleep, join, network or MIDI port work added in `ui/`. |
| Audio callback | n/a (no `ui/` file runs on the audio thread) |
| Per-frame work: no allocation in frame loop / per-knob code | findings L1b-6, L1b-7, L1b-8 |
| Cross-thread fields | clean. `FileSystemManager.scanCache` / `presetMetaCache` are `ConcurrentHashMap`, `revalidating` is a concurrent set, `synchronousScans` is `@Volatile`; `UserNotice` is a `ConcurrentLinkedQueue`; `RealtimeRecorder.isFinishing` / `lastStartError` are `@Volatile`; `AudioEngine.isScanningDevices` reads an atomic; `DeckPresetController` failure path hops back with `runOnGlThread`. Logic race noted separately in L1b-3. |
| Ops paths (DeckOps / FxOps / TransitionOps / QueueOps, undo) | clean. No new direct deck/FX/transition mutation. Queue edits in `QueueKnobs` and `BrowserPane` playlist menu still go through `QueueOps`; menu "Add to ... Queue" items are plain appends, which DECISIONS allows without undo. Parameter reset now has a full undo step (`ParametersRenderer.kt:282-286`). Undo key routing: one Ctrl+Z now runs once per frame (pair bay no longer double-handles; Library FULL gets `handleKeysWhileHidden`). DECISIONS text left stale: L1b-9. |
| Precedence (macro / Metaknob / Super Knob / MIDI) | clean. No re-implementation added; `PerformanceMacroStrip`, `PropertiesPanel`, `MacroBindingEditor` changes are label formatting only. |
| Persistence: atomic writes, unknown fields survive, versions bumped | atomic: clean (every `writeText` touched in `ui/` was replaced by `AtomicFile.write`; the remaining `markerFile.writeText` in `FileSystemManager.kt:1025` and `MidiPreferencesPanel.kt:293` predate the baseline). Unknown fields: L1b-4. Versions: n/a (no versioned format changed here). New playlist token format: L1b-5. |
| Lifecycle (native resources, threads) | clean. No GL/native/MIDI resource added; the new background preset revalidation runs on the existing daemon `FileSystemScanner` executor. `PresetListPanel.rowClipper` is one lazily created `ImGuiListClipper`, reused. |
| Error paths: failures reach the user, no swallowed catch | clean for new code: `PlaylistMenu.add` toasts success and failure, `createPlaylist` logs, `deleteFile`/`deleteFolder` toast a permanent delete (`warnDeletedForGood`), `BrowseFavorites` keeps an unreadable file aside, recording start failure toasts. Minor gap: L1b-10 (New Playlist failure is log-only). The only empty catch in `ui/` (`AppPreferencesStore.kt:123`) predates the baseline. |

## Findings

### L1b-1 [bug] New playlist menu, tree drop and New Playlist modal read and write playlist files on Thread 0
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/browser/PlaylistTargets.kt:41-49 (`appendTokens`: `file.readText()` + `AtomicFile.write`), :69-80 (`append` -> `PlaylistManager.loadPlaylist` / `savePlaylist`, then `LibraryPanel.refreshAssets()`); PlaylistMenu.kt:51-55 (`add`, called from a menu click); BrowserPane.kt:544-561 (`playlistDropTarget`: `getOrLoadPlaylist` / `TokenPlaylistEdit.open` / `insertPaths` on drop); BrowserPopupHandler.kt:404-425 (`createPlaylist`: `AtomicFile.write`), and the three export modals (:507, :543, :579).
- Evidence: all of these run inside ImGui draw code on Thread 0. Each click/drop does a JSON read, an fsync'd temp-file write + move, and then `LibraryPanel.refreshAssets()` (LibraryPanel.kt:196-205), which now calls `FileSystemManager.clearScanCache()` first and so forces every `scanAll*` listing to rebuild synchronously: full recursive walks of all seven roots, a stat per preset, and for playlists `validatePlaylistFile` (reads every playlist and resolves each item). The blueprint rule: "File work goes to `presetIoExecutor` or a named thread." The old FX/transition "Add to" code did the same on Thread 0, so the pattern predates the baseline, but these paths are new or newly reached (submenus at every folder level, tree drops, Add to Playlist on all three tabs), and the `clearScanCache()` in `refreshAssets` is new.
- Impact: a visible output hitch on each add-to-playlist / drop / create during a set, growing with library size (the code's own note puts a preset-folder walk at ~30 ms at 10,000 presets, FileSystemManager.kt:135-137). Worse on slow or network drives.
- Fix: run `PlaylistTargets.append` / `createPlaylist` / `insertPaths` saves on `presetIoExecutor` and post the toast and `refreshAssets` back with `VisualSourceRegistry.runOnGlThread`. At minimum, drop the unconditional `clearScanCache()` from `refreshAssets` (writes already change the signature) or let `scanAllPresets` revalidate in the background as it does on the TTL path.
- Verified:

### L1b-2 [polish] Directory listing and stats every frame while a playlist menu or the New Playlist modal is open
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/browser/PlaylistMenu.kt:32 (`PlaylistTargets.level(dir, ext)` = `dir.listFiles()` + per-entry `isFile`/`isDirectory`, every frame per open submenu level); PlaylistTargets.kt:83 (`selected(kind)` stats the selected playlist every frame the row menu is open); BrowserPopupHandler.kt:363 (`LibraryFolders.isInside` = two `canonicalFile` calls per frame) and :376 (`File(dir, "$name.$ext").exists()` per frame).
- Evidence: the KDoc says "Reads the folder only while it is open", so it is deliberate, but it is still Thread 0 file I/O at frame rate.
- Impact: small with a local disk and a few playlists. On a network or sleeping drive, menu hover can stall frames.
- Fix: cache `Level` per directory for the life of the open menu (clear when the menu closes or on `refreshAssets`); compute `dir` and `taken` only when the name buffer or the request changes.
- Verified:

### L1b-3 [bug] (suspected) Background preset revalidation can overwrite a fresh listing with a stale one
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt:138-163 (`scanAllPresets` returns the cached list and starts `revalidateAllPresets`), :150-161 (background thread: signature, then `buildAllPresets`, which stores into `scanCache[cacheKey]` unconditionally at :188).
- Evidence: suppose the background task computes its signature and starts walking. Meanwhile Thread 0 saves a preset, `clearScanCache()` runs and the next frame rebuilds the listing with the new file. The background task then finishes and writes its older listing (without the new file) with `cachedAtMs = now`. For the next ~2 s (`SCAN_CACHE_TTL_MS`) and one more background pass, the Library shows the stale list. The comment at :135-137 says "Changes the app makes clear the cache and show at once". The same applies to the `current.copy(cachedAtMs=...)` branch at :156, which can refresh the timestamp of an entry Thread 0 already replaced (harmless) but never checks for a newer entry.
- Impact: a just-saved or just-renamed preset can be missing from the Sources list, or appear under its old name, for a few seconds after the action. Rare, with a race window as long as the walk (longer on large libraries).
- Fix: tag entries with a generation counter bumped by `clearScanCache`; the background task captures it at the start and stores only if it is unchanged (`scanCache.compute`).
- Verified:

### L1b-4 [bug] Appending to an FX/transition playlist drops JSON fields the DTO doesn't know
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/browser/PlaylistTargets.kt:45-46 (decode with `ignoreUnknownKeys` into `FXPlaylistDto` / `TransitionPlaylistDto`, re-encode the DTO); the same in the pre-baseline `TokenPlaylistEdit.save` (PlaylistEdit.kt:127-129), now also reached from the new tree drop target (BrowserPane.kt:551).
- Evidence: blueprint rule "unknown JSON fields survive a round-trip". `BrowserPopupHandler.updateFileMetadata` (:176-184) already does this right through a `JsonObject`; the new append path does not.
- Impact: a playlist written by a newer build, or edited by hand with an extra field, loses that field the first time a row is added from the menu or dropped on the tree. Low today (no extra fields exist yet), but this is the kind of loss the rule exists to prevent.
- Fix: parse to `JsonObject`, replace only `items`, and write the object back (the same pattern as `updateFileMetadata`).
- Verified:

### L1b-5 [decision] Playlists now store relative tokens; resolution tries the working directory first
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/browser/PlaylistEdit.kt:51-70 (`tokenForPayload` / `shortToken` write "sub/x.lsdfx" relative to the saved root); PlaylistTargets.kt:33-35; presets/PlaylistParser.kt:30-39 (`resolveItem` tries `File(spelling)`, i.e. relative to the process CWD, before the roots).
- Evidence: new entries are relative to `library/fx`, `library/fx_chains` or `library/transitions`. `FxQueueEngine.playlistRoots` / `TransitionQueueManager.playlistRoots` and `tokenNamesFile` handle them, and `relocateFiles` converts targets to short tokens correctly. But a relative token is first checked against the CWD, so a file at `<cwd>/sub/x.lsdfx` would win over the library copy. Old builds reading a new playlist resolve the same way, so they are compatible.
- Impact: none in a normal install (the CWD holds `library/`, not FX files at top level). Noted because the token format changed without a doc entry. Check DECISIONS / web parity (lane 2/6).
- Fix: owner call. Either record the relative-token format in DECISIONS, or have `resolveItem` try the roots before the bare CWD path for relative spellings.
- Verified:

### L1b-6 [polish] New per-row, per-frame allocations in the Library lists
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/browser/PresetListPanel.kt:119-122 (two `filter` lists per saved-preset row per frame, for every drawn row); FXBrowserPanel.kt:145 and TransitionBrowserPanel.kt:100 (eager `listOfNotNull(...).joinToString("\n")` tooltip string per row per frame, now also with `LoadedMarks.tooltipOrNull(marks)` joining a second list for loaded rows; `itemTooltip(String)` builds it whether or not the row is hovered).
- Evidence: rule "no allocation in the frame loop (lists, lambdas, string building)". The FX and Transitions lists are not clipped (only Sources over 400 rows is), so every row allocates every frame.
- Impact: GC churn proportional to the visible list size while the Library is open. No visible stall under ZGC today.
- Fix: in PresetListPanel compute `hasIssues` with `any {}` and filter `notes` only inside the hover tooltip block; in FX/Trans use the lambda `itemTooltip { }` overload (TooltipHelper.kt:322) so the string is built only on hover.
- Verified:

### L1b-7 [polish] `floatArrayOf` per macro-bound row per frame in the parameter grid
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/ParametersRenderer.kt:169
- Evidence: `val linked = if (TangoPalette.isLightTheme) floatArrayOf(0.00f, 0.38f, 0.52f) else floatArrayOf(0.2f, 0.85f, 1.0f)`, allocated every frame for each macro- or Metaknob-driven row label. The same values exist as `TangoPalette.LINKED_TEXT`, which the lines around it already use.
- Impact: small per-frame garbage in the hot Edit grid path.
- Fix: two `private val` arrays, or pass `TangoPalette.LINKED_TEXT` into a `tooltipColored(u32, ...)` variant.
- Verified:

### L1b-8 [polish] Per-frame string building in macro target labels; per-frame `ImInt` in the Audio modulator section
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/PerformanceMacroStrip.kt:141, :179, :275-277 (`targetLabel` now also runs `formatPropertyLabel(...).removePrefix(...)` per binding per frame; :141 passes it eagerly to `itemTooltip(String)`); PropertiesPanel.kt:392; AudioModulatorSection.kt:92, :112, :227 (`ImInt(...)` per frame; these date from before the baseline, but the file changed and the rule says `ImString`/ImGui buffers are fields).
- Evidence: ARCHITECTURE §1: "ImGui native buffers (`ImString`, `ImInt`, fonts) are fields, not per-frame locals". Hot per-frame paths avoid allocation.
- Impact: minor garbage while the Edit strip or the Audio tab is shown.
- Fix: cache labels per binding (as `PerformanceTransitionsControls.suffixCache` does); make the three `ImInt`s object fields set before each combo.
- Verified:

### L1b-9 [stale] DECISIONS still says the pair bay runs `handleDeepEditKeys`
- Where: DECISIONS.md:319 ("`drawPairBay` now runs it (undo only)"); code: src/main/kotlin/llm/slop/liquidlsd/ui/PerformanceDeepEditBay.kt:161 (removed; `beginFrame` at :49-53 handles undo when no Params editor owns the keys) and PerformanceMatrixPanel.kt:83-84 + UIManager.kt:624-626 (new `handleKeysWhileHidden` for Library FULL).
- Evidence: the code change fixed a double undo per Ctrl+Z. The DECISIONS entry describes the old routing.
- Impact: a future reader may "restore" the second call and bring back the double undo.
- Fix: edit DECISIONS.md:319 in place: undo runs once per frame from `PerformanceDeepEditBay.beginFrame` when no Params editor owns the keyboard, and from `handleKeysWhileHidden` while the Library is maximized.
- Verified:

### L1b-10 [polish] New Playlist failure is log-only; the modal closes as if it worked
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/browser/BrowserPopupHandler.kt:380-387 and :398-425 (`createPlaylist` returns null and logs; the modal closes anyway, with no toast)
- Evidence: rule "failures reach the user (toast or log), not just printStackTrace". Logging meets the letter of it, but the user sees the dialog close and no playlist appear. The same modal's add step (`PlaylistMenu.add`) does toast its failures.
- Impact: on a read-only or full drive, "New Playlist..." silently does nothing.
- Fix: `ToastOverlay.show("Couldn't create '$name' (see log)")` when `createPlaylist` returns null.
- Verified:

### L1b-11 [bug] (suspected) A failed Save As restores the preset marker but not the macro-bank dirty baseline
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/DeckPresetController.kt:36-45; presets/PresetManager.kt:112-121 (`setActive` re-snapshots `bankBaselines[slot]` from the *current* bank)
- Evidence: on write failure the callback calls `setActive(slot, before.first, before.second, before.third)`. `setActive` takes a new bank baseline from the live bank, so a deck whose macro bank had unsaved edits before the failed save now compares its bank against those edits and reads as bank-clean. The parameter-level dirty check against `before.second` is still right.
- Impact: after a failed save (disk full, permissions), the dirty guard may not prompt for macro-bank-only edits, so a later load can drop them without asking. Needs a failed write to happen first, so rare.
- Fix: also capture `bankBaselines[slot.index]` in `before` and restore it (a `PresetManager.restoreActive(slot, snapshot)` that sets all four fields).
- Verified:

### L1b-12 [polish] (suspected) `pendingNarrow` can outlive its press
- Where: src/main/kotlin/llm/slop/liquidlsd/ui/browser/PresetListPanel.kt:71-89 (`pressSelect` / `releaseSelect` / `dragStarted`)
- Evidence: the press on a row inside a multi-selection is parked in `pendingNarrow` until a release on that same row, or until a drag starts. If the mouse is released elsewhere without crossing the drag threshold (press, then Esc or a scroll, then release off the row), the flag stays set. A later release while the row is drawn (any `isMouseReleased(0)` frame, e.g. after an unrelated click elsewhere) narrows the selection to that row unexpectedly. The flag is one static shared by the FX and Transitions panels.
- Impact: occasional surprise loss of a multi-selection. Rare.
- Fix: clear `pendingNarrow` on any frame where `ImGui.isMouseReleased(0)` and it was not consumed (e.g. at the end of the FX/Trans `drawRows`).
- Verified:

## Checked and clean (notable)
- `PresetListPanel` clipper (`includeItemByIndex` before the first `step()`, selected rows and the open-menu row kept drawn, `menuRow` index guarded by `< filtered.size`).
- `QueueKnobs.insertAnchor`: matches `QueueOps.insertAfter` (cursor = at+n-1 = anchor+n); a stale anchor is guarded by `in indices`.
- `BrowseCatalog.folderNodes` one-pass counts equal the old `inFolder` counts; `fullTree` lazy is safe because a catalog is rebuilt when its inputs change.
- `AppPreferencesStore` randomization migration: the one-time flag is written on the next save, and the default and migration agree (true).
- `SavePresetModal` / `NameCheck` / `ModalKeys.refocusField`: Enter is refused for bad names and the field gets the keyboard back; the overwrite two-step still needs a second confirm.
- `VideoExportModal` Esc: `ModalKeys.answer()` is skipped while a field has text input, so Esc in a field doesn't close the dialog, and the global Esc already skips while text input is active.
- `UIManager` Esc-from-empty-search flag is consumed once per frame and still yields to `ModalKeys.ownsEscape()`.
- `OscilloscopeDrawer` style push/pop is balanced (7 pushed, 4 + 3 popped).
- `AudioModulatorSection` arrays (`AudioSignals.LEVELS/TRANSIENTS/SECTIONS`) line up with `BAND_LABELS` (5) and `SECTION_LABELS` (6) in order.
