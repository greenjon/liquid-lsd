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
  - **Stale Parameter Protection (`CellConfigPanel`)**: `CellConfigPanel.draw()` defensively validates `state.selectedParam` against `ParameterResolver.findParameterByPath()`. If the parameter was orphaned or detached, selection is immediately cleared.

---

## 4. Background Queue Manager (`BgQueueManager.kt`)

[`BgQueueManager.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/presets/BgQueueManager.kt) controls the background video / generator deck (`Deck BG`) playlist and single-deck dip-to-black transitions.

### Dip-to-Black & Modulation Pipeline
- **Transition States**: `IDLE` -> `FADING_OUT` -> `FADING_IN` -> `IDLE`.
- **Modulation & MIDI Triggers**: Symmetrically modulated by `Mixer/bgQueuePrev` and `Mixer/bgQueueNext`, along with dedicated MIDI CC bindings (`Global/bgQueuePrev`, `Global/bgQueueNext`).
- **Dirty Deck Guard**: Observes `UITheme.autoVjDirtyBehavior` (`SKIP`, `AUTO_SAVE`, `AUTO_DISCARD`) when transitioning or advancing on Deck BG.
- **Double-Click Playback**: Double-clicking any track in BG Queue triggers immediate playback with dip-to-black (`playIndex(index, mixer, withDipToBlack = true)`), while double-clicking in Play Queue triggers standby deck load and auto-fade crossfading.

---

## 5. PlaylistManager & PlaylistParser

- **`PlaylistManager.kt`**: Handles CRUD operations on setlists (`.lsdset` files), supports reordering presets, and provides `removePresetFromAllPlaylists(presetAbsPath)` to clean up deleted preset file references across all playlist files on disk.
- **`PlaylistParser.kt`**: Parses text and DTO playlist formats, using primary resolution in `library/presets/` (and fallback to legacy `presets/patches/`) with auto-extension matching (`.lsd`, `.json`, `.patch`).
- **`PlayQueueManager.kt` / `BgQueueManager.kt`**: Provides `removeFileFromQueue(file)` to remove all references to a deleted file from both queues and shift active index/shuffle state.
