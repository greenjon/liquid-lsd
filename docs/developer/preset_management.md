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
- **Deck C (Overlay)**: Manual loading on Deck C is independent and never affects Auto-VJ or deck staging.
- **Auto-VJ Mid-Session Arming**: Turning Auto-VJ ON while presets are playing manually arms the system for the next advance trigger without causing immediate jump cuts.

### Dirty Deck Handling Behaviors (`UITheme.autoVjDirtyBehavior`)
If the target deck contains unsaved manual changes:
- **`SKIP`**: Aborts queue advancement to protect unsaved work.
- **`AUTO_SAVE`**: Automatically saves modified state to `library/presets/AutoVJ_<Deck>_<Timestamp>.lsd` before advancing.
- **`AUTO_DISCARD`**: Discards manual changes and forces queue advancement.

---

## 4. PlaylistManager & PlaylistParser

- **`PlaylistManager.kt`**: Handles CRUD operations on setlists (`.lsdset` files), supports reordering presets, and provides `removePresetFromAllPlaylists(presetAbsPath)` to clean up deleted preset file references across all playlist files on disk.
- **`PlaylistParser.kt`**: Parses text and DTO playlist formats, using primary resolution in `library/presets/` (and fallback to legacy `presets/patches/`) with auto-extension matching (`.lsd`, `.json`, `.patch`).
- **`PlayQueueManager.kt`**: Provides `removeFileFromQueue(file)` to remove all references to a deleted file from the queue and shift active index/shuffle state.
