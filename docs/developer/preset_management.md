# Preset, Queue & Notes Management Architecture

This section documents preset serialization DTOs, async I/O worker pools, `NotesManager` synchronization, AutoVJ play queue orchestration, and playlist parsing in Liquid LSD.

---

## 1. PresetManager (`PresetManager.kt`)

[`PresetManager.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/presets/PresetManager.kt) manages asynchronous preset saving, loading, and session persistence.

### Asynchronous I/O Executor
To prevent file system I/O from blocking OpenGL rendering on Thread 0, JSON serialization and file operations run on a dedicated single-threaded daemon executor:
```kotlin
private val presetIoExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "PresetManager-IO").apply { isDaemon = true }
}
```

### Auto-Healing Preset Loader (`sanitizePresetDto`)
To eliminate schema drift and prevent dirty-flag trip bugs when shaders or feedback parameters evolve:
- When a preset is loaded asynchronously in `loadDeckPresetAsync`, `PresetManager.sanitizePresetDto(dto)` verifies the incoming parameter map against the target visual source's `meta.json` and canonical feedback parameter specifications.
- **Fills Missing Parameters**: Injects default `ParameterDto` instances for any newly introduced visual source or feedback parameters (`fbKaleido`, `Stellation`, `Support H`, etc.).
- **Prunes Obsolete Keys**: Strips unknown or deprecated legacy fields (e.g. `sourceSelect`, `globalScale`).
- **Background Auto-Save**: If schema changes are detected, `loadDeckPresetAsync` immediately and quietly rewrites the updated `.lsd` file to disk on `presetIoExecutor` without blocking the main rendering thread.

### Thread-Safe Deferred Queue (`applyPendingPresets`) & Canonical Baseline Caching
Data Transfer Objects (DTOs) generated on the background executor are offered to concurrent queues (`deckAPresetQueue`, `deckBPresetQueue`, `deckBGPresetQueue`, `deckPVPresetQueue`).
- Every frame, Thread 0 invokes `applyPendingPresets(mixer)`.
- `applyPendingPresets` polls the queues, resets baseline parameters and applies incoming DTO parameter values to `Deck` instances safely on Thread 0.
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

---

## 2. Notes System Manager (`NotesManager.kt`)

[`NotesManager.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/notes/NotesManager.kt) manages memory buffers and persistence for the 3-tier Note System:

- **Global Source Notes**: Stored in `~/.liquid-lsd/source-notes.json`. Loaded eagerly at app startup via `NotesManager.loadSourceNotes()`. Saved synchronously whenever edited.
- **Deck DTO Synchronization**:
  - `syncFromDto(deckLabel, dto)`: Extracts `dto.presetNotes` and `dto.paramNotes` into deck memory maps on preset load.
  - `syncToDto(deckLabel, dto)`: Embeds current in-memory preset and parameter notes into `DeckPresetDto` prior to serialization.

### DTO Schema Notes Fields (`PresetModels.kt`)
[`DeckPresetDto`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/models/PresetModels.kt) includes:
```kotlin
val presetNotes: String = "",
val paramNotes: Map<String, String> = emptyMap()
```

---

## 3. PlayQueueManager (`PlayQueueManager.kt`)

[`PlayQueueManager.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/presets/PlayQueueManager.kt) controls the volatile RAM play queue and AutoVJ set transitions.

### AutoVJ Crossfading Pipeline
When `triggerNext()` is called:
1. Identifies the inactive/standby deck from `mixer.crossfade.value`.
2. Checks if the standby deck has a **manually staged override** (`stagedDeckA` or `stagedDeckB`).
   - If staged, Auto-VJ starts the crossfader transition (`mixer.isAutoFading = true`) directly to the staged preset without pulling from the queue or advancing `activeIndex`.
   - If not staged, polls the next preset file path from the queue, loads it in background via `PresetManager.loadDeckPresetAsync`, advances `activeIndex`, and starts the crossfader transition.

### Manual Deck Loading & Line-Jumping Behaviors
- **Auto-VJ OFF**: Manually loading presets to any deck keeps the queue contents and `activeIndex` completely untouched.
- **Auto-VJ ON (Active Deck)**: Loading a preset into the live deck plays immediately; Auto-VJ remains ON and transitions to the standby deck on the next trigger.
- **Auto-VJ ON (Standby Deck / "Jump the Line")**: Loading a preset into the inactive deck flags it as staged. The next Auto-VJ trigger crossfades to that staged visual without overwriting it, preserving the next queue track for the subsequent cycle.
- **Deck PV (Preview)**: Manual loading on Deck PV is independent and never affects Auto-VJ or deck staging.
- **Auto-VJ Mid-Session Arming**: Turning Auto-VJ ON while presets are playing manually arms the system for the next advance trigger without causing immediate jump cuts.

### Unified Dirty Deck Transition Guard (`DeckPresetController.guardDeckTransition`)
Whenever a deck preset is replaced, ejected, overwritten, or reset through any UI pathway:
- **Pathways Guarded**:
  - Eject button on deck monitor toolbars
  - Deck click-and-drag utility actions (Move, Copy, Swap)
  - Library 4-column loader buttons (`[A] [B] [BG] [PV]`, numeric keys `1`–`4`, Quick Audition Padlock)
  - Double-clicking presets in Preset Library or Playlist Editor
  - "New Preset" popups in Preset Library
  - "File -> New Preset" and "File -> Reset" in the main menu bar
  - Dragging and dropping `.lsd` files directly onto decks
- **Configured Behaviors (`UITheme.autoVjDirtyBehavior`)**:
  - **`AUTO_SAVE`**: Silently saves the modified preset to disk immediately (preserving active name or creating timestamped backup) and proceeds with the transition without prompt.
  - **`AUTO_DISCARD`**: Discards modifications immediately and executes the transition without prompt.
  - **`SKIP` (Prompt)**: Dispatches a confirmation request to `PopupManager.requestDeckConfirm` to prompt the user (Save, Discard, or Cancel).

### Visual Source Change Guard (`DeckPresetController.changeVisualSourceSafely`)
- Whenever changing the visual source on a deck (`PresetGridTabs` dropdown or Launchpad):
  - **Confirmation Dialog (`PopupManager.drawSourceChangeConfirmPopup`)**: If the deck has an active named preset or unsaved parameter edits, prompts the user before replacing the source.
  - **Preset Unbinding**: Clears `activePreset` and `cachedDto` in `PresetManager` so subsequent saves require naming or cannot overwrite the previous preset file.
  - **Selection Invalidation & Subtab Synchronization**: Clears `PresetGridState.selectedCell` / `selectedParam` and switches the deck subtab to the new source.
  - **Stale Parameter Protection (`PropertiesPanel`)**: `PropertiesPanel.draw()` defensively validates `state.selectedParam` against `ParameterResolver.findParameterByPath()`. If the parameter was orphaned or detached, selection is immediately cleared.

---

## 4. Background Queue Manager (`BgQueueManager.kt`)

[`BgQueueManager.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/presets/BgQueueManager.kt) controls the background video / generator deck (`Deck BG`) playlist and single-deck dip-to-black transitions.

### Dip-to-Black & Modulation Pipeline
- **Transition States**: `IDLE` -> `FADING_OUT` -> `FADING_IN` -> `IDLE`.
- **Modulation & MIDI Triggers**: Symmetrically modulated by `Mixer/bgQueuePrev` and `Mixer/bgQueueNext`, along with dedicated MIDI CC bindings (`Global/bgQueuePrev`, `Global/bgQueueNext`).
- **Dirty Deck Guard**: Observes `UITheme.autoVjDirtyBehavior` (`SKIP`, `AUTO_SAVE`, `AUTO_DISCARD`) when transitioning or advancing on Deck BG.
- **Double-Click Playback**: Double-clicking any track in BG Queue triggers immediate playback with dip-to-black (`playIndex(index, mixer, withDipToBlack = true)`), while double-clicking in Play Queue triggers standby deck load and auto-fade crossfading.

## 5. Transition Queue Manager (`TransitionQueueManager.kt`)

[`TransitionQueueManager.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/presets/TransitionQueueManager.kt) manages the volatile Transition Queue engine (Phase 2).

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

The 19" Modular Video Rack chassis UI (`rack/`, `rack/ui/` — faceplates, rear patch-cable view, per-unit dynamic macro banks) documented in earlier revisions of this file has been **removed entirely** and replaced by **Performance Mode** (`PerformanceMatrixPanel.kt`, a 4×4 knob matrix toggled with `F4`; see `docs/user_guide/macros_and_rack.md`). Historical rack design notes are kept for reference in `docs/developer/modular_video_rack_proposal.md`, marked retired/superseded.

### 12 Always-Resident Canonical Banks
`MacroEngine` registers 12 canonical, always-resident banks by id (`MacroEngine.CANONICAL_BANK_IDS`): `DECK_A`, `DECK_B`, `DECK_BG`, `DECK_PV`, `DECK_A_FX`, `DECK_B_FX`, `DECK_BG_FX`, `DECK_PV_FX`, `TRANS`, `MASTER`, `FX_SENDS`, and `MASTER_FX`. `MacroEngine.canonicalIdForDeckLabel(deckLabel)` maps a deck label ("Deck A", "Deck B", "Deck BG", "Deck PV", "Master") to its generator bank id, falling through to `TRANS` for anything else, while `targetBankIdFor()` maps per-deck insert FX banks. Both the Column 3 `[ MACROS ]` view and the Performance Matrix's 4 tabs read and write these same underlying banks — the matrix is a live control *view* onto them (dragging a knob live-updates `MacroControl.value`; editing bindings happens in the MACROS view's Binding Inspector).

### `SessionSerializer` Is the Sole Bank Registrar
[`SessionSerializer.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/presets/SessionSerializer.kt) is the only site that calls `MacroEngine.registerBank()`: both `loadSession()` (restoring `session.deckMacroBanks[canonicalId]` per canonical id) and `startEmpty()` register all 12 canonical banks on startup. There is no per-unit/dynamic bank registration anymore.

### Per-Deck Persistence (`MacroBankSerializer`, `PresetRepository`)
Each deck's own canonical bank snapshot travels with its preset file, not just the session:
- **Save**: `PresetRepository.saveDeckPresetAsync()` resolves the deck's canonical bank via `MacroEngine.canonicalIdForDeckLabel(deckLabel)` + `MacroEngine.getBank(canonicalBankId)`, deep-copies it immutably with `MacroBankSerializer.snapshotForPreset()`, and bundles it into `DeckPresetDto.macroBank`.
- **Load**: `MacroBankSerializer.install(deckBank, targetBank)` performs a full **swap**, not a merge — loading a preset is meant to load exactly the knob layout it was saved with. A null/empty `deckBank` (older presets, or an empty deck slot) clears the target bank to blank rather than leaving stale bindings behind.
- **Session-level**: `SessionSerializer` separately bundles all 12 canonical banks into `SessionStateDto.deckMacroBanks` so the full macro state round-trips even without touching individual preset files.

---

## 9. FX Presets, Playlists & Live Queues

The `[ FX ]` Library view mode (see `docs/developer/ui.md` §8 and `docs/user_guide/presets_and_library.md`) is backed by its own preset/queue manager family, parallel to but independent from the `.lsd` preset managers documented above.

### One Mutation Path: `FxOps.kt`
[`FxOps.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/presets/FxOps.kt) is the single entry point for changing what's loaded in any `FxChain` (the four decks' `fxChain` and `Mixer.masterFxChain`): `loadChain`, `applyChain`, `clearChain`, `setSlotFilter`, `loadSlot`, `applySlot`, `clearSlot`, and `applyItem` (the deterministic playlist/queue path: a `.lsdfxchain` replaces the chain, a `.lsdfx` becomes slot 1 of an otherwise empty chain). It is used by the Performance rows, Deep Edit, the FX browser and toolbar, FX playlists, and both live FX queues.

Every op is queued and applied in `FxOps.drainOnGlThread(mixer)`, called once per frame from the render loop right after `PresetManager.applyPendingPresets`. That matters because async file loads complete on `PresetManager.presetIoExecutor`: replacing a slot disposes the old `ISFFilter` (deleting its GL framebuffers), which must happen on the GL thread, and swapping `FxChain.slots` mid-frame would race the renderer. After each op, `FxMacroSync.syncFor()` refreshes that chain's FX row knobs. Never mutate `FxChain.slots` directly from a `CompletableFuture` callback.

### FX Chains Are Session State, Not Preset State
Deck presets (`.lsd`, `DeckPresetDto`) carry no FX. Every chain is persisted in the session instead: `MixerDto.masterFxChain`, `deckAFxChain`, `deckBFxChain`, `deckBGFxChain`, `deckPVFxChain`. On a fresh session, `Mixer.loadDefaultFxChains()` seeds all five from bundled `.lsdfxchain` files.

### `FXQueueManager.kt` / `FXBgQueueManager.kt`
Mirror `PlayQueueManager.kt` / `BgQueueManager.kt` but operate on FX assets instead of visual presets:
- Volatile RAM `queue: CopyOnWriteArrayList<File>` of `.lsdfx` / `.lsdfxchain` / `.lsdfxplay` entries, with `isRepeatEnabled`, `isShuffleEnabled`, `activeIndex`, and `playbackHistory` for shuffle back-stepping (`initializeShuffle()`, `playedIndices`).
- `FXQueueManager` applies deterministically to the crossfader-active deck (A or B); `FXBgQueueManager` drives Deck BG independently, same as the non-FX background queue.
- UI: `FXQueueActionsPanel.kt` (Column 4 in FX mode) and `FXBgQueueActionsPanel.kt` (Column 3 in FX mode).
- **Export Queue to Playlist**: Both managers support instantly exporting the current live queue to a new `.lsdfxplay` playlist file (`BrowserPopupHandler.kt` "Export Live FX Queue (A/B) as Playlist" / "Export Background FX Queue as Playlist" popups).

### FX Playlists (`.lsdfxplay`) & `FXPlaylistEditorPanel.kt`
[`FXPresetModels.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/models/FXPresetModels.kt) defines `FXPlaylistDto`, an ordered list of FX asset paths persisted by `PresetRepository` and `FileSystemManager` under `library/fx_playlists/`. `FXPlaylistEditorPanel.kt` (Column 2 in FX mode) supports drag-and-drop insertion, reordering, double-click application, and context-menu actions — the FX-mode counterpart to `PlaylistEditorPanel.kt`.

### `FXBrowserPanel.kt`
Unifies the formerly separate `FXPresetListPanel` (single `.lsdfx` presets) and `FXChainListPanel` (`.lsdfxchain` chains) into one Column 1 browser with ISF stock filters, saved singles, and saved chains, tier badges, and an All / Stock / Singles / Chains filter menu. Stock (built-in ISF) filters and presets load into any deck's or Master's 3-slot FX chain via `FxOps`; saved presets and chains can additionally be added to FX playlists and live FX queues. "Load to" submenus for stock/single items let you target any of the 3 slots, while chains load directly. (The `.lsdfxbank` format was removed along with `FxBank`: Master FX is a plain `FxChain` now.)

### FX Metaknob / Super Knob Persistence
`FXSlotDto` (in `PresetModels.kt`) carries `metaKnob: ParameterDto?` and `metaBinding: FxMetaBindingDto?` (see `rendering/isf/FxMetaBinding.kt`); `FXChainDto` carries `superKnob: ParameterDto?` and `slotSuperKnobLink: List<Boolean>?`. Both are **baked at save time** — a saved slot/chain preset always restores the exact Metaknob binding/position it had when saved, even if `ISFAutoBindEngine`'s resolution for that shader (curated table, or a since-changed user override under `library/isf_overrides/`) has since changed. There is no live re-resolution path from a saved preset; re-save (or an explicit future "re-resolve" UI action) is required to pick up a changed binding.

