# Codebase Concerns

**Analysis Date:** 2026-07-07
**Partial refresh:** 2026-09-19 — file paths updated after the `patches/`→`presets/` and `AssetBrowserPanel.kt`→`ui/browser/` reorganization; items verified against current code are marked RESOLVED/PARTIALLY RESOLVED inline. Sections not touched by this refresh (Fragile Areas, Scaling Limits, Dependencies at Risk, Missing Critical Features, Test Coverage Gaps) were not re-verified and may also contain stale paths or resolved items.

## Tech Debt

**RESOLVED (2026-09-18) — Asset browser module size and mixed responsibilities:**
- Issue was: `ui/AssetBrowserPanel.kt` (now deleted) combined navigation tree rendering, playlist editing, patch drag/drop, queue commands, popup state, file operations, and deck-load behavior in one 1041-line singleton.
- Fix landed: split into `ui/browser/` — `PresetListPanel.kt`, `PlaylistEditorPanel.kt`, `QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`, `FXBrowserPanel.kt`, `FXPlaylistEditorPanel.kt`, `FXQueueActionsPanel.kt`, `FXBgQueueActionsPanel.kt`, `StockTransitionListPanel.kt`, `TransitionPresetListPanel.kt`, `TransitionPlaylistEditorPanel.kt`, `TransitionQueuePanel.kt`, plus shared `BrowserPopupHandler.kt`/`BrowserActionToolbar.kt`/`BrowserDeckButtons.kt`/`BrowserRowMoreButton.kt`. Disk mutations stayed in `ui/FileSystemManager.kt`; queue mutations stayed in `presets/PlayQueueManager.kt` and siblings. See `ARCHITECTURE.md`'s File Map for the full `ui/browser/` listing.
- Watch-out: the split fixed the single-file "mixed responsibilities" problem, but the browser tier list itself is now duplicated four ways (Preset/FX/Transition-preset/Transition-playlist panels share near-identical draw logic) — see the queue-manager duplication entry below, which is the same underlying pattern applied to the panel layer instead of the manager layer.

**Patch and session persistence uses global mutable singletons:**
- Issue: `PresetManager`, `PlayQueueManager`, `BgQueueManager`, `FXQueueManager`, `FXBgQueueManager`, `TransitionQueueManager`, `UITheme`, `MidiMappingManager`, and `CVRegistry` store app state in process-wide `object` singletons.
- Files: `src/main/kotlin/llm/slop/liquidlsd/presets/PresetManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/PlayQueueManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/BgQueueManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/FXQueueManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/FXBgQueueManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/TransitionQueueManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt`
- Impact: Tests must reset singleton state manually (`mockkObject`/`clearQueue()` in `@BeforeTest`), concurrent flows share mutable state implicitly, and features such as multiple sessions/profiles are hard to reason about. The FX and Transition queue managers added since the original analysis perpetuate the same pattern rather than moving away from it — they're not even routed through a `SessionContext` facade the way `PlayQueueManager`/`BgQueueManager` nominally are.
- Fix approach: Introduce explicit state holders for session, queue, settings, MIDI profiles, and CV registry state. Pass those dependencies to UI/rendering code while preserving singleton facades only at app boundaries.

**RESOLVED (2026-09-19) — Queue/playlist manager logic duplicated three times (Preset → Transition → FX):**
- Issue was: `PlayQueueManager.kt`/`BgQueueManager.kt` (presets), `TransitionQueueManager.kt` (transitions), and `FXQueueManager.kt`/`FXBgQueueManager.kt` (FX) each reimplemented the same shuffle/repeat/history-index bookkeeping, playlist parsing, and advance/jump logic. A first pass (same day) extracted `FxQueueEngine` to unify only FX-A/B vs. FX-BG; a follow-up pass extended the extraction one layer further.
- Fix landed: new `presets/QueueEngine.kt` is now the shared base for *all* queue types — it owns shuffle/repeat state, played/history index tracking (`shiftIndicesAfter`/`removeIndexAndShift`/`moveIndex`), playlist parsing (via `PlaylistParser`, with an overridable `resolveUnmatchedPlaylistItem` hook — see below), and queue mutation (`appendToQueue`/`insertAt`/`removeFromQueue`/`moveItem`/`clearQueue`), plus `computeNextIndex()`/`computePrevIndex()` for the shuffle/repeat advance-selection logic. `FxQueueEngine` (99 lines, was 300+) now only adds the deck-targeting + dirty-deck guard on top; `TransitionQueueManager` (107 lines, was 387) now only adds `applyTransitionItem`/`advanceOnAutoFade`/`restoreSessionQueue` on top. **`PlayQueueManager`/`BgQueueManager` were deliberately left unconverted** — they have real behavioral differences (staged-deck "jump the line" manual-load interaction, dip-to-black fade state machine) and are the most performance-critical, live-show code in the app; folding them into `QueueEngine` was judged higher-risk than the payoff for this pass. Revisit if a good abstraction presents itself, but don't force it.
- Files: `src/main/kotlin/llm/slop/liquidlsd/presets/QueueEngine.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/FxQueueEngine.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/TransitionQueueManager.kt`. `PlayQueueManager.kt`/`BgQueueManager.kt` still have their own independent (duplicated) copies of the same index-bookkeeping helpers.
- Verification: full `./gradlew test` suite green, plus a new regression test (`testParsePlaylistKeepsStockTransitionIdsThatHaveNoBackingFile`) protecting the one subtle behavior difference the merge had to preserve — see next entry.

**RESOLVED (2026-09-19) — Duplicate playlist parsing paths:**
- Issue was: `FxQueueEngine.parsePlaylist()` decoded `.lsdfxplay` JSON directly rather than going through `PlaylistParser`, so it didn't support the line/`#`-comment text format `PlaylistParser` supports for `.lsdplay`, and `FileSystemManager.scanAllFxPlaylists()` validated `.lsdfxplay` with a generic exists/readable/non-empty check instead of resolving referenced items.
- Fix landed: `PlaylistParser.parseItems()` (`presets/PlaylistParser.kt`) is now generic — it extracts an `items` JSON array from any playlist DTO shape (rather than decoding into the preset-specific `PlaylistDto`), so `.lsdplay`/`.lsdfxplay`/`.lsdtransplay` all share one parser. `QueueEngine.parsePlaylist()` (shared base, see above) uses it uniformly. `FileSystemManager` gained `validateFxPlaylistFile()` mirroring `validatePlaylistFile()`, now wired into `scanAllFxPlaylists()`, so a `.lsdfxplay` pointing at deleted `.lsdfx`/`.lsdfxchain` files is correctly flagged invalid instead of showing as valid.
- Subtlety preserved: Transition playlist items can be stock-shader IDs with no backing file (e.g. `"linear_crossfade"`) — unlike FX/preset items, an unresolved Transition item must be *kept* as a literal token, not dropped. `QueueEngine` exposes this as `protected open fun resolveUnmatchedPlaylistItem(item: String): File? = null` (FX/base default: drop + warn); `TransitionQueueManager` overrides it to return `File(item)`. Covered by `TransitionQueueManagerTest.testParsePlaylistKeepsStockTransitionIdsThatHaveNoBackingFile`.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/PlaylistParser.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/QueueEngine.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/TransitionQueueManager.kt`
- Remaining gap: `PlayQueueManager`/`BgQueueManager` (preset side) already used `PlaylistParser` before this pass, so they're unaffected and already correct.

**Manual parameter path registry:**
- Issue: `ParameterResolver.getAllParameterPaths()` hard-codes every mixer/deck/Mandala path and uses `!!` for many parameter lookups.
- Files: `src/main/kotlin/llm/slop/liquidlsd/parameters/ParameterResolver.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/Mandala.kt`, `src/main/kotlin/llm/slop/liquidlsd/rendering/DynamicVisualSource.kt`
- Impact: Renaming a parameter or adding a visual source can silently break MIDI mappings or crash path enumeration.
- Fix approach: Move path metadata closer to parameter owners. Build paths from registered parameter descriptors and treat missing parameters as validation errors with tests.

## Known Bugs

**RESOLVED — Settings active MIDI profile is saved but not applied to MIDI mapping state:**
- Symptom was: `UITheme.loadSettings()` reads `activeMidiProfile`, but `MidiMappingManager` initialized its own `activeProfileName` as `"default"` on object init, ignoring the saved value.
- Fix confirmed: `Main.kt:155` now explicitly calls `MidiMappingManager.loadProfile(UITheme.activeMidiProfile)` at startup — exactly the workaround this entry used to recommend.
- Files: `src/main/kotlin/llm/slop/liquidlsd/Main.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`, `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`

**JACK reconnect can repeatedly restart the audio engine while save/load work is in flight:**
- Symptoms: `MidiJackWatchdog` calls `AudioEngine.tryReconnect()` from a background daemon, which calls `stop()` and `start()` when inactive. Preset/session work uses `CompletableFuture.runAsync()` and shared singletons without lifecycle coordination. (Not re-verified this refresh beyond confirming the files/structure still exist as described.)
- Files: `src/main/kotlin/llm/slop/liquidlsd/audio/MidiJackWatchdog.kt`, `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/PresetManager.kt`
- Trigger: Start with JACK unavailable or unstable while interacting with session/preset saves and UI settings.
- Workaround: Disable JACK reconnect with `UITheme.audioEngineEnabled = false` or `MidiJackWatchdog.isJackReconnectActive = false` for non-audio sessions.

**RESOLVED (preset side) — Asset browser playlist validation rejects JSON playlists:**
- Symptom was: `FileSystemManager.validatePlaylistFile()` treated JSON playlist text as path lines while `PlayQueueManager.parsePlaylist()` decoded JSON — divergent behavior for `.lsdplay`.
- Fix confirmed: `validatePlaylistFile()` (`FileSystemManager.kt:584`) now calls `PlaylistParser.parseFile()`/`resolveItem()`, the same shared parser `PlayQueueManager.parsePlaylist()` uses.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/PlayQueueManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/PlaylistParser.kt`
- **New, weaker instance on the FX side**: see "Duplicate playlist parsing paths" under Tech Debt — `.lsdfxplay` validation doesn't resolve item references at all, so it doesn't have this bug's symptom, but only because it does no validation.

## Security Considerations

**RESOLVED for rename/move/delete/clone; gap remained (now fixed 2026-09-19) for creation/export — Preset/FX/playlist file operations not confined to managed roots:**
- Risk was: Rename, clone, move, and delete accepted arbitrary string paths and operated directly on `File(path)`.
- Fix confirmed for mutation: `FileSystemManager.renameFile()`/`cloneFile()`/`moveFile()`/`deleteFile()` now call `requireManagedAssetPath()` (`FileSystemManager.kt:~112`), which checks the canonicalized target against `managedRootPaths()` (presets, playlists, FX presets/chains/playlists, transitions/transition playlists).
- Gap found and fixed 2026-09-19: *creation/export* flows (new-playlist and export-queue-to-playlist popups in `ui/browser/BrowserPopupHandler.kt`, plus `PlaylistManager.createPlaylist()`) built `File(root, "$name.ext")` straight from a raw trimmed textbox and wrote it without ever calling the confinement check — a `../../` name could escape the library root. All six creation/export write sites now call `FileSystemManager.isManagedAssetPath(file)` before writing. `managedRootPaths()` was also missing `getFxPlaylistsRoot()` (added 2026-09-19); before that fix, rename/delete/move/clone on any `.lsdfxplay` file failed closed.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/PlaylistManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/browser/BrowserPopupHandler.kt`
- Recommendation going forward: any *new* file-creation code path (new asset types, import flows) must call `isManagedAssetPath()`/`requireManagedAssetPath()` before writing — this has now been missed once already for a brand-new asset type (FX playlists) and should be treated as a checklist item, not assumed automatic.

**RESOLVED — MIDI profile names can construct arbitrary relative filenames:**
- Risk was: `MidiMappingManager.loadProfile(profileName)`/`saveActiveProfile()` built `File(midiDir, "$profileName.json")` without sanitizing path separators.
- Fix confirmed: `loadProfile()` now calls `sanitiseProfileName(profileName)` (`MidiMappingManager.kt:132`) before building the file path, falling back to `"default"` on failure.
- Files: `src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt`

**PARTIALLY RESOLVED — Distribution task downloads executable runtimes without checksum pinning:**
- Risk was: `packageThumbDrive` downloaded JRE archives from Adoptium URLs and packaged launchers with the downloaded runtime without checksum verification.
- Fix landed but incomplete: `build.gradle.kts` now has a `jreChecksums` map and a `verifyChecksum()` function that hashes downloaded archives with SHA-256 and `require()`s a match — but every platform entry is still the placeholder `"EXPECTED_SHA256_HERE"`, and `verifyChecksum()` logs a warning and *skips* verification when it sees that placeholder rather than failing. So the mechanism exists but isn't actually pinning anything yet.
- Files: `build.gradle.kts`
- Recommendation: Fill in the real SHA-256 values from the Adoptium API (the code already has a comment pointing at `https://api.adoptium.net/v3/assets/...`) and change the placeholder-skip behavior to fail the build instead of warning.

## Performance Bottlenecks

**Audio callback copies the full beat-history buffer every analysis interval:**
- Problem: `BeatDetector.processBlock()` performs `System.arraycopy(historyBuffer, 0, bgHistoryBuffer, 0, maxEnvelopeBlocks)` inside the JACK process path every 16 blocks.
- Files: `src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt`
- Cause: The real-time callback snapshots all 8192 envelope blocks before scheduling background analysis.
- Improvement path: Copy only the active analysis window or use a lock-free double-buffer handoff where the callback publishes an index and the background thread owns copying outside the callback.

**PARTIALLY MITIGATED — Preset and playlist scanning performs blocking filesystem work on the UI thread:**
- Problem: Directory scans call `listFiles()`, playlist validation reads file contents with `readText()`, and browser panel rendering (now `ui/browser/PresetListPanel.kt` and siblings, not `AssetBrowserPanel.kt` which was deleted 2026-09-18) triggers scans from ImGui draw calls.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/browser/PresetListPanel.kt`
- Cause: Asset browsing and validation are still synchronous and coupled to draw/navigation calls.
- Mitigation added since original analysis: `FileSystemManager` now has a `scanCache` (`ConcurrentHashMap<String, ScanCacheEntry>`, 1s TTL, keyed by recursive directory signature) so repeated draws within a second reuse the last scan instead of re-walking the tree — reduces frequency but a cache-miss scan is still synchronous on the calling (UI) thread.
- Improvement path (still open): validate playlists asynchronously, debounce refreshes, and update the UI from immutable scan results instead of scanning inline during draw.

**RESOLVED — Unbounded async file I/O uses the common ForkJoin pool:**
- Problem was: preset load/save calls used `CompletableFuture.runAsync()` without a bounded executor, so tasks piled onto the shared ForkJoin pool during rapid queue changes or preset saves.
- Fix confirmed: `PresetManager.presetIoExecutor` (`PresetManager.kt:20`) is now a dedicated `Executors.newSingleThreadExecutor`, and `PresetRepository.kt`'s `loadDeckPresetAsync()`/`saveDeckPresetAsync()`/`saveFxPresetAsync()`/etc. all run on it via `CompletableFuture.runAsync(..., PresetManager.presetIoExecutor)` instead of the default common pool.
- Files: `src/main/kotlin/llm/slop/liquidlsd/presets/PresetManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/PresetRepository.kt`
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

**PARTIALLY COVERED — Filesystem and playlist safety:**
- What's now tested (`FileSystemManagerTest.kt`, added since original analysis): `isManagedAssetPath()` root confinement, `renameFile()`/`deleteFile()` rejecting out-of-root targets, scan caching/signature invalidation, and (as of 2026-09-19) `scanAllFxPlaylists()` flagging a `.lsdfxplay` with a missing referenced item as invalid (`testScanAllFxPlaylistsFlagsMissingItemsInvalid`). `FXQueueManagerTest`/`TransitionQueueManagerTest` also now cover the shared `QueueEngine.parsePlaylist()` path, including the stock-transition-ID-must-not-be-dropped case.
- What's still not tested: the FX-playlist creation/export write sites fixed 2026-09-19 (`ui/browser/BrowserPopupHandler.kt`, `ui/PlaylistManager.kt::createPlaylist`) have no regression test proving they now call `isManagedAssetPath()` before writing; session path portability.
- Files: `src/main/kotlin/llm/slop/liquidlsd/ui/FileSystemManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/PlaylistManager.kt`, `src/main/kotlin/llm/slop/liquidlsd/ui/browser/BrowserPopupHandler.kt`, `src/main/kotlin/llm/slop/liquidlsd/presets/PlayQueueManager.kt`
- Risk: Data loss or confusing playlist behavior can ship unnoticed for the still-untested creation/export write sites.
- Priority: Medium

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
