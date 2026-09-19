# Codebase Concerns

**Analysis Date:** 2026-07-07

## Tech Debt

**Asset browser module size and mixed responsibilities:**
- Issue: `src/main/kotlin/llm/slop/liquidlsd/ui/AssetBrowserPanel.kt` combines navigation tree rendering, playlist editing, patch drag/drop, queue commands, popup state, file operations, and deck-load behavior in one 1041-line singleton.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/AssetBrowserPanel.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt`
- Impact: UI changes can regress file operations or queue behavior because state such as `currentView`, `activePlaylistData`, popup targets, and drag/drop targets share one object. Tests cannot isolate most of the behavior without ImGui.
- Fix approach: Split sidebar, center browser, queue panel, playlist editor, and popup handlers into focused render/controller units. Keep disk mutations in `FileSystemManager` and queue mutations in `PlayQueueManager`; make UI functions delegate instead of directly orchestrating.

**Patch and session persistence uses global mutable singletons:**
- Issue: `PatchManager`, `PlayQueueManager`, `UITheme`, `MidiMappingManager`, and `CVRegistry` store app state in process-wide objects.
- Files: `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt`
- Impact: Tests must reset singleton state manually, concurrent flows share mutable state implicitly, and features such as multiple sessions/profiles are hard to reason about.
- Fix approach: Introduce explicit state holders for session, queue, settings, MIDI profiles, and CV registry state. Pass those dependencies to UI/rendering code while preserving singleton facades only at app boundaries.

**Duplicate playlist parsing paths:**
- Issue: Playlist parsing exists in `FileSystemManager.validatePlaylistFile()` and `PlayQueueManager.parsePlaylist()`, with different format support. `FileSystemManager.parseLsdplayPlaylist()` exists but is unused, while validation uses line parsing only.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/models/PatchModels.kt`
- Impact: A JSON `.lsdplay` playlist can be accepted by the play queue but marked invalid in the asset browser, or vice versa.
- Fix approach: Create one playlist parser/resolver service used by validation, UI display, queue insertion, and session restore.

**Manual parameter path registry:**
- Issue: `ParameterResolver.getAllParameterPaths()` hard-codes every mixer/deck/Mandala path and uses `!!` for many parameter lookups.
- Files: `src/main/kotlin/llm/slop/liquidlsd/parameters/ParameterResolver.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Mandala.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/DynamicVisualSource.kt`
- Impact: Renaming a parameter or adding a visual source can silently break MIDI mappings or crash path enumeration.
- Fix approach: Move path metadata closer to parameter owners. Build paths from registered parameter descriptors and treat missing parameters as validation errors with tests.

## Known Bugs

**Settings active MIDI profile is saved but not applied to MIDI mapping state:**
- Symptoms: `UITheme.loadSettings()` reads `activeMidiProfile`, but `MidiMappingManager` initializes its own `activeProfileName` as `"default"` and loads that profile during object initialization.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`
- Trigger: Save a non-default active MIDI profile in `lsd-settings.properties`, restart, then inspect mappings used by `MidiMappingManager.update()`.
- Workaround: Explicitly call `MidiMappingManager.loadProfile(UITheme.activeMidiProfile)` after settings load and before mappings are used.

**JACK reconnect can repeatedly restart the audio engine while save/load work is in flight:**
- Symptoms: `MidiJackWatchdog` calls `AudioEngine.tryReconnect()` from a background daemon, which calls `stop()` and `start()` when inactive. Patch/session work uses `CompletableFuture.runAsync()` and shared singletons without lifecycle coordination.
- Files: `src/main/kotlin/llm/slop/liquidlsd/audio/MidiJackWatchdog.kt`, `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`
- Trigger: Start with JACK unavailable or unstable while interacting with session/preset saves and UI settings.
- Workaround: Disable JACK reconnect with `UITheme.audioEngineEnabled = false` or `MidiJackWatchdog.isJackReconnectActive = false` for non-audio sessions.

**Asset browser playlist validation rejects JSON playlists:**
- Symptoms: `FileSystemManager.validatePlaylistFile()` calls `parsePlaylistContent()` and treats JSON playlist text as path lines; `PlayQueueManager.parsePlaylist()` decodes JSON when content starts with `{`.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt`
- Trigger: Add a `.lsdplay` JSON playlist with `PlaylistDto.items` under `presets/playlists`.
- Workaround: Use line-based playlist files, or route validation through `PlayQueueManager.parsePlaylist()`.

## Security Considerations

**Preset file operations are not confined to preset roots:**
- Risk: Rename, clone, move, and delete accept arbitrary string paths and operate directly on `File(path)`. Drag/drop payloads and playlist references can pass absolute paths.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/AssetBrowserPanel.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt`
- Current mitigation: UI navigation starts from `presets/patches` and `presets/playlists`, and operations are local desktop actions.
- Recommendations: Canonicalize paths and enforce roots for destructive operations. Require `deleteFile()`, `renameFile()`, `moveFile()`, and playlist mutation paths to stay under allowed directories unless a trusted import flow explicitly opts out.

**MIDI profile names can construct arbitrary relative filenames:**
- Risk: `MidiMappingManager.loadProfile(profileName)` and `saveActiveProfile()` create `File(midiDir, "$profileName.json")` without sanitizing path separators.
- Files: `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`
- Current mitigation: Profiles are app-local data and the default profile name is static.
- Recommendations: Restrict profile IDs to a safe filename pattern, store display names inside JSON, and canonicalize against `presets/midi`.

**Distribution task downloads executable runtimes without checksum pinning:**
- Risk: `packageThumbDrive` downloads JRE archives from Adoptium URLs and packages launchers with the downloaded runtime without checksum verification.
- Files: `build.gradle.kts`
- Current mitigation: Downloads use HTTPS and cache under `build/jre-cache`.
- Recommendations: Pin expected checksums per platform, verify downloaded archive digests before extraction, and fail the package task on mismatch.

## Performance Bottlenecks

**Audio callback copies the full beat-history buffer every analysis interval:**
- Problem: `BeatDetector.processBlock()` performs `System.arraycopy(historyBuffer, 0, bgHistoryBuffer, 0, maxEnvelopeBlocks)` inside the JACK process path every 16 blocks.
- Files: `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt`
- Cause: The real-time callback snapshots all 8192 envelope blocks before scheduling background analysis.
- Improvement path: Copy only the active analysis window or use a lock-free double-buffer handoff where the callback publishes an index and the background thread owns copying outside the callback.

**Preset and playlist scanning performs blocking filesystem work on the UI thread:**
- Problem: Directory scans call `listFiles()`, playlist validation reads file contents with `readText()`, and sidebar tree rendering recursively calls `scanDirectory()` from ImGui rendering.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/AssetBrowserPanel.kt`
- Cause: Asset browsing and validation are synchronous and coupled to draw/navigation calls.
- Improvement path: Cache directory snapshots, validate playlists asynchronously, debounce refreshes, and update the UI from immutable scan results.

**Unbounded async file I/O uses the common ForkJoin pool:**
- Problem: `PatchManager.loadGlobalPatchAsync()`, `loadDeckPresetAsync()`, `saveGlobalPatchAsync()`, and `saveDeckPresetAsync()` call `CompletableFuture.runAsync()` without a bounded executor.
- Files: `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`
- Cause: Every load/save task goes to the shared executor and can pile up during rapid queue changes or preset saves.
- Improvement path: Use a dedicated bounded single-thread or small fixed executor for patch I/O, coalesce repeated saves, and expose completion/error state to the UI.

**Render frame limiter busy-yields after sleeping:**
- Problem: Main loop sleeps for most of the frame gap, then spins with `Thread.yield()` until target time.
- Files: `src/main/kotlin/llm/slop/liquidlsd/Main.kt`
- Cause: Frame pacing combines sleep and busy wait to hit 30/60 FPS.
- Improvement path: Measure CPU cost on target platforms and prefer GLFW swap interval/frame pacing or a calibrated sleep strategy unless the busy wait is required for visual timing.

## Fragile Areas

**Real-time JACK callback safety boundary:**
- Files: `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt`, `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt`, `.agents/skills/jack_callback_safety/SKILL.md`
- Why fragile: The callback must avoid blocking, allocation, and logging. `JackClient` catches callback errors without logging on the callback thread, but `AudioEngine.processAudio()` still calls shared registries and schedules analysis work.
- Safe modification: Keep allocations, locks, file I/O, and logs out of `processAudio()` and `BeatDetector.processBlock()`. Pre-allocate buffers at object init/start and move heavy analysis to background-owned buffers.
- Test coverage: No tests exercise callback allocation behavior, `nframes` bounds, reconnect lifecycle, or analysis error handling.

**LWJGL/OpenGL thread affinity:**
- Files: `src/main/kotlin/llm/slop/liquidlsd/Main.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/FBO.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Shader.kt`, `.agents/skills/lwjgl_thread_restriction/SKILL.md`
- Why fragile: GLFW polling, context switching, OpenGL object creation, and disposal must stay on Thread 0. Secondary window rendering changes the current context twice per frame.
- Safe modification: Keep all GL calls in the main loop or objects created/disposed from it. Pass data from background threads with queues/atomics, never GL handles or callbacks that invoke GL.
- Test coverage: `src/test/kotlin/llm/slop/liquidlsd/rendering/DeckUtilityTest.kt` mocks deck utilities only; there are no render-loop, context, or resource lifecycle tests.

**ImGui native memory and font atlas lifetime:**
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/*.kt`, `.agents/skills/imgui_memory_management/SKILL.md`
- Why fragile: ImGui uses native pointers. `UITheme` correctly keeps font byte arrays and icon ranges alive, but new UI widgets can introduce native allocations or `ImFontConfig` objects that need scoped destruction.
- Safe modification: Use `MemoryStack.stackPush().use { ... }` or explicit `destroy()` for native ImGui/LWJGL objects. Keep native pointers scoped to the render frame unless stored with clear ownership.
- Test coverage: `src/test/kotlin/llm/slop/liquidlsd/ui/FontInspectorTest.kt` inspects font glyphs but does not verify atlas rebuild/resource lifetime.

**OpenGL resource ownership depends on manual disposal:**
- Files: `src/main/kotlin/llm/slop/liquidlsd/rendering/FBO.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Shader.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Deck.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt`, `src/main/kotlin/llm/slop/liquidlsd/Main.kt`
- Why fragile: `FBO` and `Shader` warn in `finalize()` if not disposed, but finalizers do not safely release GL resources and can run off the GL context thread.
- Safe modification: Treat `dispose()` as mandatory and call it from main-thread lifecycle code. Avoid relying on `finalize()` for cleanup; use explicit ownership and idempotent disposal.
- Test coverage: No tests assert all visual sources, FBOs, and shaders are disposed when decks or renderer shut down.

## Scaling Limits

**Asset library size:**
- Current capacity: Directory and playlist scans are synchronous, with validation reading playlist contents and recursively scanning folders from UI code.
- Limit: Large `presets/patches` or `presets/playlists` trees can stall the render loop and make drag/drop interactions janky.
- Scaling path: Add a file index/cache, background refresh, invalidation by directory timestamp or watch service, and paged UI rendering.

**Audio analysis history and analysis rate:**
- Current capacity: Beat history uses 8192 blocks and analyzes every 16 callbacks.
- Limit: Smaller JACK buffers increase callback frequency and make snapshot copying plus analysis scheduling more expensive.
- Scaling path: Make history/window sizes configurable, measure callback time, and keep all O(N) work off the real-time callback path.

**Patch queue and session persistence:**
- Current capacity: Queue state stores absolute file paths in memory and serializes them into `presets/last_session.json`.
- Limit: Moved preset roots or shared portable installs can lose queued items because session restore filters missing files.
- Scaling path: Store paths relative to known roots when possible, keep absolute paths only for imported external assets, and report missing items to the UI.

## Dependencies at Risk

**JACK dependency is platform-sensitive:**
- Risk: `org.jaudiolibs:jnajack:1.4.0` requires JACK/PipeWire-JACK runtime availability and behaves differently across Linux, macOS, and Windows audio setups.
- Impact: Audio-reactive CV signals and beat sync degrade to fallback/silent mode when JACK is unavailable.
- Migration plan: Keep JACK as one backend behind an `AudioInputBackend` interface and add platform-specific backends or explicit no-audio mode.

**ImGui Java binding version is old relative to LWJGL stack:**
- Risk: `io.github.spair:imgui-java-*` is pinned at `1.86.11` while UI code relies on native font and drag/drop behavior.
- Impact: Native crashes or API differences can appear when updating LWJGL/JDK/platform runtimes.
- Migration plan: Upgrade in a dedicated phase with smoke tests for font loading, docking/window layout, drag/drop, and atlas rebuild.

**Logback 1.4.14 and build plugins require periodic security review:**
- Risk: `ch.qos.logback:logback-classic:1.4.14`, Shadow plugin `8.1.1`, Kotlin `2.0.21`, and serialization dependencies are pinned in `build.gradle.kts`.
- Impact: Dependency CVEs or JDK compatibility problems can ship into desktop distributions.
- Migration plan: Add dependency update checks, lock dependency versions, and run packaging smoke tests after upgrades.

## Missing Critical Features

**No user-visible async I/O status:**
- Problem: Patch save/load failures are logged but not surfaced through the UI.
- Blocks: Users cannot tell when a preset failed to save/load unless they inspect logs.

**No recovery UI for missing session or playlist files:**
- Problem: Session restore filters missing queue files and playlist parsing logs unresolved items.
- Blocks: Users cannot repair moved presets from within the app.

**No built-in profiling for render/audio budgets:**
- Problem: TODO lists OpenGL profiling and shader optimization, and docs mention diagnostics, but the app has no integrated timing budget view for callback/render latency.
- Blocks: Performance regressions are hard to attribute during visual or DSP changes.

## Test Coverage Gaps

**Audio DSP and JACK callback path:**
- What's not tested: `AudioEngine.processAudio()`, `BeatDetector.processBlock()`, callback exception accounting, reconnect behavior, and zero-allocation constraints.
- Files: `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt`, `src/main/kotlin/llm/slop/liquidlsd/audio/JackClient.kt`, `src/main/kotlin/llm/slop/liquidlsd/audio/MidiJackWatchdog.kt`
- Risk: Audio dropouts, broken beat detection, or callback crashes can ship unnoticed.
- Priority: High

**Filesystem and playlist safety:**
- What's not tested: Path confinement, rename/delete/move failures, JSON playlist validation, missing playlist item reporting, and session path portability.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PlayQueueManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/patches/PatchManager.kt`
- Risk: Data loss or confusing playlist behavior can ship unnoticed.
- Priority: High

**Rendering lifecycle and GL resources:**
- What's not tested: `FBO.dispose()`, `Shader.dispose()`, dynamic visual feedback FBO recreation, secondary window context switching, and renderer shutdown.
- Files: `src/main/kotlin/llm/slop/liquidlsd/rendering/FBO.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Shader.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Renderer.kt`, `src/main/kotlin/llm/slop/liquidlsd/Main.kt`
- Risk: GL leaks, context-thread crashes, or blank output can ship unnoticed.
- Priority: Medium

**Settings and MIDI profile integration:**
- What's not tested: Loading `UITheme.activeMidiProfile`, applying it to `MidiMappingManager`, profile filename safety, and mapping persistence.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiEngine.kt`
- Risk: User MIDI mappings may not restore as expected after restart.
- Priority: Medium

---

*Concerns audit: 2026-07-07*
