# Preset, Queue & Notes Management Architecture

This section documents preset serialization DTOs, async I/O worker pools, `NotesManager` synchronization, AutoVJ play queue orchestration, and playlist parsing in Liquid LSD.

---

## 1. PresetManager (`PresetManager.kt`)

[`PresetManager.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/presets/PresetManager.kt) manages asynchronous preset saving, loading, and session persistence.

### Asynchronous I/O Executor
To prevent file system I/O from blocking OpenGL rendering on Thread 0, JSON serialization and file operations run on a dedicated single-threaded daemon executor:
```kotlin
private val presetIoExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "PresetManager-IO").apply { isDaemon = true }
}
```
Besides preset, chain and playlist files, it runs the Garden pool rescans, Garden thumbnail PNG encode/write and flame imports (section 12). Results are applied on Thread 0 (an `Ops` drain or `VisualSourceRegistry.runOnGlThread`); a failure toasts through `ToastOverlay`, whose text and expiry are one `AtomicReference` because this thread writes it.

### Auto-Healing Preset Loader (`sanitizePresetDto`)
To eliminate schema drift and prevent dirty-flag trip bugs when shaders or feedback parameters evolve:
- When `DeckOps.request` reads a preset file on `presetIoExecutor`, `PresetMigrator.sanitizePresetDto(dto)` verifies the incoming parameter map against the target visual source's `meta.json` and canonical feedback parameter specifications.
- **Fills Missing Parameters**: Injects default `ParameterDto` instances for any newly introduced visual source or feedback parameters (`fbKaleido`, `Stellation`, `Support H`, etc.).
- **Prunes Obsolete Keys**: Strips unknown or deprecated legacy fields (e.g. `sourceSelect`, `globalScale`).
- **Background Auto-Save**: If schema changes are detected, `DeckOps` immediately and quietly rewrites the updated `.lsd` file to disk on `presetIoExecutor` without blocking the main rendering thread.

### Thread-Safe Deferred Queue (`DeckOps.drainOnGlThread`) & Canonical Baseline Caching
Data Transfer Objects (DTOs) generated on the background executor are offered to `DeckOps`' single queue.
- Every frame, Thread 0 invokes `DeckOps.drainOnGlThread(mixer)`.
- It polls the queue, resets baseline parameters and applies incoming DTO parameter values to `Deck` instances safely on Thread 0.
- Captures a canonical snapshot of the initialized deck (`deck.toDto(...)`) into `cachedDtoA`/`cachedDtoB`/`cachedDtoBG`/`cachedDtoPV`, ensuring that newly loaded presets start with a clean dirty flag (`isDeckDirty == false`).
- Triggers `NotesManager.syncFromDto(deckLabel, dto)` to load patch and parameter notes into memory.

### Active Preset Timestamp Tracking
`PresetManager` tracks file modification timestamps for active deck presets:
```kotlin
var activePresetMtimeA: Long? = null
var activePresetMtimeB: Long? = null
var activePresetMtimeBG: Long? = null
var activePresetMtimePV: Long? = null
```
- Populated from `File.lastModified()` prior to spawning background load tasks.
- Updated upon completing successful `saveDeckPresetAsync` operations.
- Consumed by `DeckControlPanel` to draw hover tooltips (`Last saved: yyyy-MM-dd HH:mm   v<version>`).

### Clean First-Run & Session Lifecycle (`startEmpty`, `loadSession`)
- **Initial App Launch**: Newly instantiated decks default to `isEmpty = true`. When launching without a pre-existing `last_session.json` (or when `StartupBehavior.EMPTY` is active), `PresetManager.startEmpty(mixer)` is invoked.
- **Empty Deck State**: Resets parameters across all available visual sources and 2D/3D view pipelines, clears FBO framebuffers, and leaves all deck monitors as blank black screens with the Parameters Launchpad activated ("Add Source" / "Load Preset").

### External Video Streams Excluded from Presets (`ExternalVideoSource`)
- **No Preset Persistence**: Decks whose active visual source is an `ExternalVideoSource` (Spout2 on Windows, Syphon on macOS, PipeWire on Linux) cannot be saved as `.lsd` presets.
- **Rationale**: External video streams possess 0 procedural generator shader parameters, are ephemeral runtime inter-process handles, and can become dead "ghost" presets if saved. Direct live selection from the "External video" folder of the SRC browser guarantees stream presence and connects immediately.
- **UI & Controller Guards**: The Save icon buttons in `DeckControlPanel` and `PerformanceBrowseBay` (Gen Browse) are visibly disabled (`ImGui.beginDisabled`) with an explanatory hover tooltip (`External video streams (<source name>) cannot be saved as presets.`, powered by `allowWhenDisabled` in `TooltipHelper.kt`). Keyboard shortcuts (`Ctrl+S`/`Cmd+S`), `DeckPresetController.handleSaveDeck`, `DeckPresetController.saveDeckPreset`, and `PresetRepository.saveDeckPresetAsync` early-return immediately without action.
- **Dirty Checking**: `PresetManager.isDeckDirty` unconditionally returns `false` for `ExternalVideoSource`, avoiding spurious unsaved changes prompts when switching sources or ejecting decks.

---

## 2. Notes System Manager (`NotesManager.kt`)

[`NotesManager.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/notes/NotesManager.kt) manages memory buffers and persistence for the 3-tier Note System:

- **Global Source Notes**: Stored in `~/.liquid-lsd/source-notes.json`. Loaded eagerly at app startup via `NotesManager.loadSourceNotes()`. Saved synchronously whenever edited.
- **Deck DTO Synchronization**:
  - `syncFromDto(deckLabel, dto)`: Extracts `dto.presetNotes` and `dto.paramNotes` into deck memory maps on preset load.
  - `syncToDto(deckLabel, dto)`: Embeds current in-memory preset and parameter notes into `DeckPresetDto` prior to serialization.

### DTO Schema Notes Fields (`PresetModels.kt`)
[`DeckPresetDto`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/models/PresetModels.kt) includes:
```kotlin
val presetNotes: String = "",
val paramNotes: Map<String, String> = emptyMap()
```

---

## 3. PlayQueueManager (`PlayQueueManager.kt`)

[`PlayQueueManager.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/presets/PlayQueueManager.kt) controls the volatile RAM play queue and AutoVJ set transitions.

### AutoVJ Crossfading Pipeline
When `triggerNext()` is called:
1. Identifies the inactive/standby deck from `mixer.crossfade.value`.
2. Checks if the standby deck has a **manually staged override** (`stagedDeckA` or `stagedDeckB`).
   - If staged, Auto-VJ starts the crossfader transition (`mixer.isAutoFading = true`) directly to the staged preset without pulling from the queue or advancing `activeIndex`.
   - If not staged, polls the next preset file path from the queue, loads it in background via `DeckOps.request(slot, DeckChange.Preset(file), LoadOrigin.QUEUE)`, advances `activeIndex`, and starts the crossfader transition.

### Manual Deck Loading & Line-Jumping Behaviors
- **Auto-VJ OFF**: Manually loading presets to any deck keeps the queue contents and `activeIndex` completely untouched.
- **Auto-VJ ON (Active Deck)**: Loading a preset into the live deck plays immediately; Auto-VJ remains ON and transitions to the standby deck on the next trigger.
- **Auto-VJ ON (Standby Deck / "Jump the Line")**: Loading a preset into the inactive deck flags it as staged. The next Auto-VJ trigger crossfades to that staged visual without overwriting it, preserving the next queue track for the subsequent cycle.
- **Deck PV (Preview)**: Manual loading on Deck PV is independent and never affects Auto-VJ or deck staging.
- **Auto-VJ Mid-Session Arming**: Turning Auto-VJ ON while presets are playing manually arms the system for the next advance trigger without causing immediate jump cuts.

### Deck Changes: `DeckOps` (`presets/DeckOps.kt`)
Every change to what a deck holds goes through `DeckOps.request(slot, change, origin)`; see the `DeckOps` entry in `DECISIONS.md` for the rationale. `DeckChange` is `Source`, `Preset`, `Eject`, `CopyFrom`, `MoveFrom` or `SwapWith` (each takes a `DeckScope`: `SOURCE`, `FX` or `BOTH`, default `BOTH`; the deck FX chain is not in `DeckPresetDto`, so it travels as an `FxSnapshot`: the chain's `FXChainDto`, its `sourceFile` and its dirty `baselineDto`); `LoadOrigin` is `MANUAL` or `QUEUE`.
- **Request time (calling thread)**: drop a no-op `Source` change, run the dirty guard, then either queue the change or read the preset on `presetIoExecutor` and queue the result.
- **Dirty guard**: a clean deck proceeds. `MANUAL` uses `UITheme.manualLoadDirtyBehavior` (`PROMPT` calls `DeckOps.prompt`, wired to `PopupManager.requestDeckConfirm`; `DISCARD`; `AUTO_SAVE`). `QUEUE` uses `UITheme.autoVjDirtyBehavior` (`SKIP` drops the load, `AUTO_SAVE`, `AUTO_DISCARD`).
- **Ordering**: each accepted request is stamped with a per-slot sequence number (after the dirty prompt, before the async preset read). The drain skips an op older than the last one applied to that slot and reports it to `onResult(false)`, so a slow preset read never overwrites a newer generator pick.
- **Drain (`DeckOps.drainOnGlThread`, once per frame in `Main`)**: for each queued change, push undo (MANUAL `Source`/`Preset` only, via `DeckOps.undoSink`), apply it, install the macro bank, update `PresetManager` bookkeeping, toast if bound knobs were replaced, then call `DeckOps.postApply` (the UI clears the selection and shows the SRC sub-tab after a source change). Once per drain: `MidiMappingManager.invalidateBindings`, `ParameterResolver.clearCache`, `BroadcastEngine.notifyStateChanged`.
- **With FX** (`DeckScope.FX` / `BOTH`): the target's chain changes through `FxOps.applyChain(chain, dto, file, baseline = ...)`, like any chain change (swap dip, `FxMacroSync` re-sync, link split; a saved chain stays saved, so Save is not Save As). A Move empties the source with `FxOps.clearChain`, which makes it a new unsaved "Untitled" chain so a later Save there can't overwrite the file. Both snapshots of a swap are taken before either chain changes. Copy/Move/Swap push no undo.
- **Dirty check**: `DeckOps.isDirty` (`PresetManager.isDeckDirty` delegates) compares `deck.toDto` with the cached DTO, plus the macro bank's labels and bindings with the signature `PresetManager.setActive` captured.
- All UI and queue call sites go through `DeckOps.request`; the old `PresetRepository.loadDeckPresetAsync` shim and the queue managers' own `handleDirtyDeck` are gone. `request` returns `false` when the change was dropped (no-op, prompt cancelled, or QUEUE `SKIP`); queue managers then leave their position unchanged. `DeckOps.wouldSkipQueueLoad` lets BG skip before its dip-to-black. Callers that must act once the deck holds the change pass `onResult` to `request`: it fires once, true after the drain applied it, false if dropped, prompt-cancelled, unreadable or failed (`VideoExportModal` starts its export from it).

---

## 4. Background Queue Manager (`BgQueueManager.kt`)

[`BgQueueManager.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/presets/BgQueueManager.kt) controls the background video / generator deck (`Deck BG`) playlist and single-deck dip-to-black transitions.

### Dip-to-Black & Modulation Pipeline
- **Transition States**: `IDLE` -> `FADING_OUT` -> `FADING_IN` -> `IDLE`.
- **Modulation & MIDI Triggers**: Symmetrically modulated by `Mixer/bgQueuePrev` and `Mixer/bgQueueNext`, along with dedicated MIDI CC bindings (`Global/bgQueuePrev`, `Global/bgQueueNext`).
- **Dirty Deck Guard**: Observes `UITheme.autoVjDirtyBehavior` (`SKIP`, `AUTO_SAVE`, `AUTO_DISCARD`) when transitioning or advancing on Deck BG; loads go through `DeckOps` with `LoadOrigin.QUEUE`.
- **Double-Click Playback**: Double-clicking any track in BG Queue triggers immediate playback with dip-to-black (`playIndex(index, mixer, withDipToBlack = true)`), while double-clicking in the A/B Queue triggers standby deck load and auto-fade crossfading.

## 5. Transition Queue Manager (`TransitionQueueManager.kt`)

[`TransitionQueueManager.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/presets/TransitionQueueManager.kt) manages the volatile Transition Queue engine (Phase 2).

### Unified Queue Item Resolution
The transition queue supports two types of transition items seamlessly:
1. **Transition Presets (`.lsdtrans`)**: Customized transition presets containing shader ID, customized parameter values (wipe angle, smoothness, glitch rate), and dry/wet settings.
2. **Stock Shaders (`.fs` files or shader IDs)**: Raw transition filters loaded with standard parameter defaults.

### Auto-Advance Crossfade Hook Timing
Auto-advance triggers inside `PlayQueueManager.triggerNext()`, `triggerPrevious()`, and `playIndex()` immediately before crossfading begins (`mixer.targetCrossfade = ...`), ensuring the new transition is loaded into `mixer.transitionFilter` prior to the crossfader moving.

---

## 6. PlaylistManager & PlaylistParser

- **`PlaylistManager.kt`**: Handles CRUD operations on setlists (`.lsdplay` files), supports reordering presets, and provides `removePresetFromAllPlaylists(presetAbsPath)` to clean up deleted preset file references across all playlist files on disk.
- **`PlaylistParser.kt`**: Parses text and DTO playlist formats, using primary resolution in `library/presets/` (and fallback to legacy `presets/patches/`) with auto-extension matching (`.lsd`, `.json`, `.patch`).
- **`PlayQueueManager.kt` / `BgQueueManager.kt`**: Provides `removeFileFromQueue(file)` to remove all references to a deleted file from both queues and shift active index/shuffle state.

---

## 7. Factory Presets & Playlists Bundling (`defaults/`, First-Run Seeding)

To ensure users never start with a blank screen on clean git clones or new releases while protecting user customizations:

### Version-Controlled Defaults (`defaults/`)
- Curated presets and playlists are kept under version control in `defaults/presets/` and `defaults/playlists/`.
- **Syncing from Library**: Developers can run `./gradlew syncDefaultsFromLibrary` to copy curated `.lsd` presets from `library/presets/` and `.lsdplay` setlists from `library/playlists/` into `defaults/`.
- **Automated Packaging (`prepareDefaultAssets`)**: During build, Gradle automatically generates `manifest.txt` indices and packages the contents of `defaults/` into the classpath (`/default_presets/` and `/default_playlists/`).

### Safe First-Run Seeding (`FileSystemManager.ensureDefaultLibrary`)
- On first launch, `FileSystemManager.ensureDefaultLibrary()` checks for the presence of `library/.defaults_installed`.
- If uninitialized, it extracts all bundled presets into `library/presets/` and bundled playlists into `library/playlists/` (skipping any existing files to protect user data), then records `library/.defaults_installed`.
- **Deletion Safety**: If a user intentionally deletes a factory preset or playlist, the existence of `library/.defaults_installed` ensures it will **not** be resurrected on subsequent application launches.
- **First-Run Visual Autoload**: When starting without a pre-existing `last_session.json` (and without `--empty`), `PresetManager.loadSession` automatically loads the first available starter preset (preferring `3d mandala`) onto Deck A so the user is immediately greeted with live graphics.

### Factory Restore Action (`FileSystemManager.restoreFactoryPresets`)
- Users can trigger **File > Restore Factory Presets...** (or via the empty preset browser button) at any time.
- Calls `ensureDefaultLibrary(forceRestore = true)` which re-extracts any missing factory presets and playlists without touching or overwriting custom presets.

---

## 8. Canonical Per-Deck Macro Banks (`MacroEngine`, `MacroBankSerializer`, `SessionSerializer`)

The 19" Modular Video Rack chassis UI (`rack/`, `rack/ui/` — faceplates, rear patch-cable view, per-unit dynamic macro banks) documented in earlier revisions of this file has been **removed entirely** and replaced by **Performance Mode** (`PerformanceMatrixPanel.kt`, a 4×4 knob matrix toggled with `F4`; see `docs/user_guide/macros_and_rack.md`). Historical rack design notes are kept for reference in `docs/archive/modular_video_rack_proposal.md`, marked retired/superseded.

### 12 Always-Resident Canonical Banks
`MacroEngine` registers 12 canonical, always-resident banks by id (`MacroEngine.CANONICAL_BANK_IDS`): `DECK_A`, `DECK_B`, `DECK_BG`, `DECK_PV`, `DECK_A_FX`, `DECK_B_FX`, `DECK_BG_FX`, `DECK_PV_FX`, `TRANS`, `MASTER`, and `MASTER_FX`. `MacroEngine.canonicalIdForDeckLabel(deckLabel)` maps a deck label ("Deck A", "Deck B", "Deck BG", "Deck PV", "Master") to its generator bank id, falling through to `TRANS` for anything else, while `targetBankIdFor()` maps per-deck insert FX banks. Both the Performance Matrix's tabs and the Edit-row macro strip (`PerformanceMacroStrip`) read and write these same underlying banks — the matrix is a live control *view* onto them (dragging a knob live-updates `MacroControl.value`; editing bindings happens in the Edit-row strip and the Properties editor).

### `SessionSerializer` Is the Sole Bank Registrar
[`SessionSerializer.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/presets/SessionSerializer.kt) is the only site that calls `MacroEngine.registerBank()`: both `loadSession()` (restoring `session.deckMacroBanks[canonicalId]` per canonical id) and `startEmpty()` register all 12 canonical banks on startup. There is no per-unit/dynamic bank registration anymore.

### Per-Deck Persistence (`MacroBankSerializer`, `PresetRepository`)
Each deck's own canonical bank snapshot travels with its preset file, not just the session:
- **Save**: `PresetRepository.saveDeckPresetAsync()` resolves the deck's canonical bank via `MacroEngine.canonicalIdForDeckLabel(deckLabel)` + `MacroEngine.getBank(canonicalBankId)`, deep-copies it immutably with `MacroBankSerializer.snapshotForPreset()`, and bundles it into `DeckPresetDto.macroBank`.
- **Load**: `MacroBankSerializer.install(deckBank, targetBank)` performs a full **swap**, not a merge — loading a preset is meant to load exactly the knob layout it was saved with. A null/empty `deckBank` (older presets, or an empty deck slot) clears the target bank to blank rather than leaving stale bindings behind.
- **Session-level**: `SessionSerializer` separately bundles all 12 canonical banks into `SessionStateDto.deckMacroBanks` so the full macro state round-trips even without touching individual preset files.

---

## 9. FX Presets, Playlists & Live Queues

The `[ FX ]` Library view mode (see `docs/developer/ui.md` §8 and `docs/user_guide/presets_and_library.md`) is backed by its own preset/queue manager family, parallel to but independent from the `.lsd` preset managers documented above.

### One Mutation Path: `FxOps.kt`
[`FxOps.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/presets/FxOps.kt) is the single entry point for changing what's loaded in any `FxChain` (the four decks' `fxChain` and `Mixer.masterFxChain`): `loadChain`, `applyChain`, `clearChain`, `setSlotFilter`, `loadSlot`, `applySlot`, `clearSlot`, and `applyItem` (the deterministic playlist/queue path: a `.lsdfxchain` replaces the chain, a `.lsdfx` becomes slot 1 of an otherwise empty chain). It is used by the Perform rows, Edit, the FX browser and toolbar, FX playlists, and both live FX queues.

Every op is queued and applied in `FxOps.drainOnGlThread(mixer)`, called once per frame from the render loop right after `DeckOps.drainOnGlThread`. That matters because async file loads complete on `PresetManager.presetIoExecutor`: replacing a slot disposes the old `ISFFilter` (deleting its GL framebuffers), which must happen on the GL thread, and swapping `FxChain.slots` mid-frame would race the renderer. After each op, `FxMacroSync.syncFor()` refreshes that chain's FX row knobs. Never mutate `FxChain.slots` directly from a `CompletableFuture` callback.

### FX Chains Are Session State, Not Preset State
Deck presets (`.lsd`, `DeckPresetDto`) carry no FX. Every chain is persisted in the session instead: `MixerDto.masterFxChain`, `deckAFxChain`, `deckBFxChain`, `deckBGFxChain`, `deckPVFxChain`. On a fresh session, `Mixer.loadDefaultFxChains()` seeds all five from bundled `.lsdfxchain` files.

### `FXQueueManager.kt` / `FXBgQueueManager.kt`
Mirror `PlayQueueManager.kt` / `BgQueueManager.kt` but operate on FX assets instead of visual presets:
- Volatile RAM `queue: CopyOnWriteArrayList<File>` of `.lsdfx` / `.lsdfxchain` / `.lsdfxplay` entries, with `isRepeatEnabled`, `isShuffleEnabled`, `activeIndex`, and `playbackHistory` for shuffle back-stepping (`initializeShuffle()`, `playedIndices`).
- `FXQueueManager` applies deterministically to the crossfader-active deck (A or B); `FXBgQueueManager` drives Deck BG independently, same as the non-FX background queue.
- UI: `FXQueueActionsPanel.kt` (A/B FX Queue) and `FXBgQueueActionsPanel.kt` (BG FX Queue), two of the five columns of the Library's Queues tab (`ui/browser/QueuesPane.kt`).
- **Export Queue to Playlist**: Both managers support instantly exporting the current live queue to a new `.lsdfxplay` playlist file (`BrowserPopupHandler.kt` "Export Live FX Queue (A/B) as Playlist" / "Export Background FX Queue as Playlist" popups).

### FX Playlists (`.lsdfxplay`)
[`FXPresetModels.kt`](https://github.com/greenjon/liquid-lsd/blob/main/src/main/kotlin/llm/slop/liquidlsd/models/FXPresetModels.kt) defines `FXPlaylistDto`, an ordered list of FX asset paths persisted by `PresetRepository` and `FileSystemManager` under `library/fx_playlists/`. A playlist is an entry in the FX tab's folder tree (`BrowserPane`): selecting it lists its rows, which support drag-and-drop insertion, reordering, double-click application and context-menu actions; the list edits are `ui/browser/PlaylistEdit.kt` (`PlaylistItems`), shared with transition playlists. The old column editors (`PlaylistEditorPanel`, `FXPlaylistEditorPanel`) are gone.

### `FXBrowserPanel.kt`
Unifies the formerly separate `FXPresetListPanel` (single `.lsdfx` presets) and `FXChainListPanel` (`.lsdfxchain` chains) into the Library's FX tab with ISF stock filters, saved singles, and saved chains, tier badges, and an All / Stock / Singles / Chains filter menu. Stock (built-in ISF) filters and presets load into any deck's or Master's 3-slot FX chain via `FxOps`; saved presets and chains can additionally be added to FX playlists and live FX queues. "Load to" submenus for stock/single items let you target any of the 3 slots, while chains load directly. (The `.lsdfxbank` format was removed along with `FxBank`: Master FX is a plain `FxChain` now.)

### FX Metaknob / Super Knob Persistence
`FXSlotDto` (in `PresetModels.kt`) carries `metaKnob: ParameterDto?`, `metaBinding: FxMetaBindingDto?` (legacy singular), `metaBindings: List<FxMetaBindingDto>?` (each with target, range, `curve`, `stepCount`, `linkMode`, `invert`, `enabled`) and `knobLayout: List<String>?` (focus-knob parameter order; null = authored order; names the shader lacks are dropped on load). `FxDefaultDto` (Save as Default, `library/isf_overrides/<hash>.json`) carries the same `metaBindings` and `knobLayout` (see `rendering/isf/FxMetaBinding.kt`); `FXChainDto` carries `superKnob: ParameterDto?` and `slotSuperKnobLink: List<Boolean>?`. Both are **baked at save time** — a saved slot/chain preset always restores the exact Metaknob binding/position it had when saved, even if `ISFAutoBindEngine`'s resolution for that shader (curated table, or a since-changed user override under `library/isf_overrides/`) has since changed. There is no live re-resolution path from a saved preset; re-save (or an explicit future "re-resolve" UI action) is required to pick up a changed binding.

### FX Chain Dirty Tracking Criteria
`FxChain.isDirty()` (cached for 250 ms) / `computeIsDirty()` compares the chain with its `baselineDto` (the file it was loaded from, or the session's copy) in everything a chain file stores: slots and their order, enabled flags, Dry/Wets, parameters with their modulators, Metaknob links and targets, Super Knob links, knob layout. The `•` indicator on the chain name and the alert-coloured Save follow it.
- **Performance positions are ignored**: the Super Knob's value, each Metaknob's value and the values Metaknob targets drive. Their modulators, links and targets still count.
- Floats compare with a 1e-4 relative tolerance; parameters present on only one side (the shader gained or lost an input) are skipped. A chain with no baseline is dirty when any slot holds an effect.

---

## 10. Library Management (`ui/browser/LibraryFolders.kt`, `LibraryTags.kt`, `LibraryRefs.kt`, `MultiSelectionModel.kt`)

- **Folders** exist only inside the Saved sections (Saved presets, Saved single FX, Saved chains, Saved transitions); stock sections have no folder on disk. `LibraryFolders` lists, creates, renames, moves and deletes folders and moves saved files between them; it only touches the disk and returns what moved, and the caller hands that to `LibraryRefs`. Save As starts in the folder the deck's preset was loaded from.
- **References** (`LibraryRefs`): the one place that follows a renamed, moved or deleted file everywhere the app names it: playlists, live queues, favorites, recents, the decks' save-back files, FX chains' and slots' source files, a preset's thumbnail.
- **Trash**: deletes go to the desktop trash (`FileSystemManager.moveToTrash`: `java.awt.Desktop`, else `gio trash`); hidden `.trash` folders are skipped by listings. Tests set `-Dlsd.noTrash=true`.
- **Tags** (`LibraryTags`, `TagPopups`, `BrowseScope.Tag`): tags are crates stored in the file's own `tags` list, so they travel with it. Only that list is rewritten (the JSON object is patched, every other field kept) and written with `UserJsonFiles.writeAtomic`; decks and chains loaded from a retagged file take the new tags so their next save doesn't restore the old ones. Rename / Edit Preset Tags (`BrowserPopupHandler`) patches `name` and `tags` the same way.
- **Multi-select** (`MultiSelectionModel`): click, Ctrl/Cmd+click, Shift+click ranges over the visible order; allocation-free `isSelected`; the browser's multi-row actions act on the selection.

## 11. Queue Edits (`ui/browser/QueueOps.kt`, `QueueDrop.kt`, `QueueSplit.kt`)

- `QueueOps` edits any of the five queues by index (cursor, play now, move, remove, `insert`, `clear`, `edit`) and pushes one restore-only undo step per edit (items, playing position, shuffle history). Callers: each queue panel's Clear, drops from the browser onto a queue (`QueueDrop`, which also refuses items of the wrong kind), the playlist menu's play now / insert / append, the controller's queue knobs (`QueueKnobs`).
- Not through `QueueOps`: plain appends (`LibraryNavigation.enqueue`, the Q key, toolbar enqueue) still call the queue managers and push no undo.
- `QueueSplit` shows the five queues (`QueuesPane`) under the browser in Library FULL; a browser row dragged onto a queue lands at the drop position.

## 12. Fractal Flame Garden and Imports (`presets/Garden.kt`, `GardenThumbnails.kt`, `FlameImportOps.kt`)

- Kept offspring are ordinary `.lsd` presets in `library/presets/garden/` (tags `garden`, `gen:N`, `parent:<name>`), with a `<name>.png` thumbnail. `Garden.pool(sourceId)` returns a `@Volatile` immutable snapshot (never touches the disk on Thread 0) and queues one coalesced rescan per call on `presetIoExecutor` to pick up outside changes; `Garden.refreshPool` rebuilds it at startup and after a Keep.
- `GardenThumbnails.capture` reads the deck's texture back on Thread 0; the PNG encode and write run on `presetIoExecutor`.
- `FlameImportOps.importFile` parses a `.flame` file and writes one preset per flame into `library/presets/imported-flames/` on `presetIoExecutor`, returns a completion future, and shows its result toast on Thread 0 (`runOnGlThread`). Details: `flame_source.md`.

## 13. Shipped Defaults (`presets/StockDefaults.kt`, `GeneratorDefaults.kt`)

- Stock sources, effects and transitions ship sparse default files, `resources/stock_defaults/{sources,filters,transitions}/<id>.json` (shape of `GeneratorDefaultDto` / `FxDefaultDto`, naming only what differs from the shader: base values, `randomizeBase` + `baseMin`/`baseMax` random ranges, modulators incl. audio and section responses, knobs).
- `GeneratorDefaults.resolve`: user file `library/generator_defaults/<sourceId>.json`, else the shipped file, else a heuristic for third-party shaders. Effects and transitions resolve in `ISFAutoBindEngine` (content-hash override in `library/isf_overrides/`, shipped file, heuristic). A user default replaces the shipped one wholesale. Format and tests: `modulation.md`, "Reactivity Recipes".
