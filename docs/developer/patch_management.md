# Patch & Queue Management

This section documents the `patches/` package, which is responsible for preset saving/loading, play queue management (for AutoVJ transitions), and playlist persistence.

---

## Package Overview

The `patches/` package manages file serialization, playlist parsing, and play queue orchestration. All file-based I/O operations are run asynchronously to ensure that reading or writing large JSON patch configurations does not block the main rendering thread (Thread 0) during a live VJ performance.

---

## 1. PatchManager (`PatchManager.kt`)

`PatchManager` is a singleton object that serves as the entry point for saving and loading presets and session configurations.

### Asynchronous I/O Executor
To prevent blocking the main rendering loop, all JSON serialization and file read/write operations are delegated to a dedicated single-threaded daemon background executor:
```kotlin
private val patchIoExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "PatchManager-IO").apply { isDaemon = true }
}
```

### Thread-Safe Deferral (Main-Thread Apply)
Since OpenGL contexts cannot be accessed from background threads, `PatchManager` loads data in the background and pushes the resulting Data Transfer Objects (DTOs) into thread-safe concurrent queues:
- `globalPatchQueue: ConcurrentLinkedQueue<GlobalPatchDto>`
- `deckAPatchQueue: ConcurrentLinkedQueue<DeckPatchDto>`
- `deckBPatchQueue: ConcurrentLinkedQueue<DeckPatchDto>`
- `deckCPatchQueue: ConcurrentLinkedQueue<DeckPatchDto>`

Every frame, the main rendering loop calls `applyPendingPatches(mixer)`, which polls these queues and applies the settings to the active `Mixer` or `Deck` instances safely on Thread 0.

### Session Management
At shutdown or save, `PatchManager.saveSession` saves the entire active VJ configuration (Decks A/B/C, crossfade position, master opacity, active queue index, shuffle/repeat parameters) to `presets/last_session.json`. On startup, `loadSession` reconstructs the previous state.

---

## 2. PlayQueueManager (`PlayQueueManager.kt`)

`PlayQueueManager` manages the volatile play queue in RAM, providing features tailored for live automatic performances (AutoVJ).

### AutoVJ and Decks Crossfading
When `PlayQueueManager.triggerNext()` is called (either manually or by an automated beat counter threshold):
1. It determines which deck is currently inactive (by reading `mixer.crossfade.value`).
2. It polls the next patch file from the queue.
3. It calls `PatchManager.loadDeckPresetAsync` to load the preset into the inactive deck.
4. It sets the mixer's `targetCrossfade` parameter and enables `mixer.isAutoFading = true`, causing the visuals to smoothly transition to the new preset.

### Playback Options
- **Repeat**: If enabled, the queue wraps back to the beginning upon reaching the end.
- **Shuffle**: Uses a history-tracking system to ensure all patches are played exactly once before wrapping. It tracks played indexes in `playedIndices` and keeps a sequential history in `playbackHistory` (allowing the "previous" button to backtrack correctly in shuffle mode).

### Dirty Deck Handling
If a preset in a deck has unsaved changes, the VJ can configure the AutoVJ engine's dirty behavior using `UITheme.autoVjDirtyBehavior`:
- `SKIP`: Skips queue advancement to prevent discarding active work.
- `AUTO_SAVE`: Automatically saves the modified state to `presets/patches/AutoVJ_<Deck>_<Timestamp>.lsd` before loading the next preset.
- `AUTO_DISCARD`: Discards manual changes and forces loading the next preset.

---

## 3. PlaylistManager (`PlaylistManager.kt`)

`PlaylistManager` handles the state of the active playlist currently loaded in the user interface.

- **Persistence Format**: Saves playlists as lightweight text files (`.txt` or `.lsd` extension), containing a comment header and a newline-separated list of patch filenames (e.g. `Mandala_Red.lsd`).
- **Interactive Editing**: Supports CRUD actions: appending, removing, reordering items in the active playlist view, and converting the active queue state into a new playlist.
- **Queue Interop**: Provides `pushToPlayQueue()` to dump the loaded playlist directly into the active `PlayQueueManager` queue.

---

## 4. PlaylistParser (`PlaylistParser.kt`)

`PlaylistParser` is a utility class that handles parsing playlist files and resolving patch names to their absolute disk locations:
- **Formats**: Parses both legacy plaintext file lists (with `#` comments) and new serialized `PlaylistDto` configurations.
- **Path Resolution**: Employs fallback path resolution: if a patch filename is given, it looks in `presets/patches/` and appends known extensions (`.lsd`, `.json`, `.patch`) in sequence until a match is found on disk.
