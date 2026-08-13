# Patch, Queue & Notes Management Architecture

This section documents preset serialization DTOs, async I/O worker pools, `NotesManager` synchronization, AutoVJ play queue orchestration, and playlist parsing in Liquid LSD.

---

## 1. PatchManager (`PatchManager.kt`)

[`PatchManager.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt) manages asynchronous patch saving, loading, and session persistence.

### Asynchronous I/O Executor
To prevent file system I/O from blocking OpenGL rendering on Thread 0, JSON serialization and file operations run on a dedicated single-threaded daemon executor:
```kotlin
private val patchIoExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "PatchManager-IO").apply { isDaemon = true }
}
```

### Thread-Safe Deferred Queue (`applyPendingPatches`)
Data Transfer Objects (DTOs) generated on the background executor are offered to concurrent queues (`deckAPatchQueue`, `deckBPatchQueue`, `deckCPatchQueue`).
- Every frame, Thread 0 invokes `applyPendingPatches(mixer)`.
- `applyPendingPatches` polls the queues, applies parameter values to `Deck` instances safely on Thread 0, and triggers `NotesManager.syncFromDto(deckLabel, dto)` to load patch and parameter notes into memory.

### Active Preset Timestamp Tracking
`PatchManager` tracks file modification timestamps for active deck patches:
```kotlin
var activePresetMtimeA: Long? = null
var activePresetMtimeB: Long? = null
var activePresetMtimeC: Long? = null
```
- Populated from `File.lastModified()` prior to spawning background load tasks.
- Updated upon completing successful `saveDeckPresetAsync` operations.
- Consumed by `DeckControlPanel` to draw hover tooltips (`Last saved: yyyy-MM-dd HH:mm   v<version>`).

---

## 2. Notes System Manager (`NotesManager.kt`)

[`NotesManager.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/notes/NotesManager.kt) manages memory buffers and persistence for the 3-tier Note System:

- **Global Source Notes**: Stored in `~/.liquid-lsd/source-notes.json`. Loaded eagerly at app startup via `NotesManager.loadSourceNotes()`. Saved synchronously whenever edited.
- **Deck DTO Synchronization**:
  - `syncFromDto(deckLabel, dto)`: Extracts `dto.patchNotes` and `dto.paramNotes` into deck memory maps on patch load.
  - `syncToDto(deckLabel, dto)`: Embeds current in-memory patch and parameter notes into `DeckPatchDto` prior to serialization.

### DTO Schema Extensions (`PatchModels.kt`)
[`DeckPatchDto`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt) includes backward-compatible optional fields:
```kotlin
val patchNotes: String = "",
val paramNotes: Map<String, String> = emptyMap()
```

---

## 3. PlayQueueManager (`PlayQueueManager.kt`)

[`PlayQueueManager.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt) controls the volatile RAM play queue and AutoVJ set transitions.

### AutoVJ Crossfading Pipeline
When `triggerNext()` is called:
1. Identifies the inactive deck from `mixer.crossfade.value`.
2. Polls the next patch file path from the queue.
3. Invokes `PatchManager.loadDeckPresetAsync` to load the preset into the inactive deck in the background.
4. Triggers automatic crossfader transition (`mixer.isAutoFading = true`), smoothly blending visuals to the new preset.

### Dirty Deck Handling Behaviors (`UITheme.autoVjDirtyBehavior`)
If the active deck contains unsaved manual changes:
- **`SKIP`**: Aborts queue advancement to protect unsaved work.
- **`AUTO_SAVE`**: Automatically saves modified state to `presets/patches/AutoVJ_<Deck>_<Timestamp>.lsd` before advancing.
- **`AUTO_DISCARD`**: Discards manual changes and forces queue advancement.

---

## 4. PlaylistManager & PlaylistParser

- **`PlaylistManager.kt`**: Handles CRUD operations on setlists (`.playlist` files), supports reordering patches, and provides `pushToPlayQueue()` to dump playlists directly into `PlayQueueManager`.
- **`PlaylistParser.kt`**: Parses text and DTO playlist formats, using fallback resolution in `presets/patches/` with auto-extension matching (`.lsdpatch`, `.lsd`, `.json`, `.patch`).
