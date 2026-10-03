# Codebase Concerns

**Analysis Date:** 2026-10-03
**Re-verified:** every item below was checked against the current code on 2026-10-03. Items from the 2026-07-07 analysis that no longer apply are collapsed into "Resolved / Stale" at the end rather than kept as active concerns. Terminology: "Patch" is now "Preset" (`patches/` -> `presets/`, `PatchManager` -> `PresetManager`, `AssetBrowserPanel` -> `ui/browser/*`); the Parameters/Properties "classic" view is gone (three views: Perform / Edit / Library).

## Tech Debt

**Layering inversion: `midi/` (and others) import `ui/`:**
- Issue: `midi/MidiMappingManager.kt` imports `ui.ParametersState` and `ui.MidiLearnTarget`; `midi/MidiEngine.kt` reads `llm.slop.liquidlsd.ui.UITheme.midiEnabled` in several places. `UITheme` is also reached from `presets/`, `audio/`, `cv/`, `parameters/`, `rendering/` and `macro/` files. `control/` is cleaner (it sees the UI only through `KnobSurface`/`NavSurface`), but `CommandContext` (`control/Command.kt`) holds a concrete `rendering.Mixer` instead of a narrow interface.
- Impact: lower layers cannot be tested or reused without the UI singleton; the "UI is a leaf" mental model is false.
- Fix approach: move learn-target and "MIDI enabled" state into `midi/` (or a small `settings` holder), have the UI observe it; give `CommandContext` an interface for what commands need from the mixer.

**Near-duplicate stores: `ControllerProfileStore` and `PerfPageStore`:**
- Issue: both implement built-in + user-dir scan, same-id override, rejection reporting, copy-built-in-to-user and atomic save. Only the low-level scan/atomic write is shared (`control/UserJsonFiles.kt`); the override/validation logic is copied. They live in different packages (`control/` vs `ui/`).
- Fix approach: extract a generic `UserOverridableStore<T>` on top of `UserJsonFiles` if a third JSON-backed store appears; until then keep them in step by hand.

**No schema version in controller profile / Perform page JSON:**
- Issue: `ControllerProfile` (`control/ControllerProfile.kt`) and `PerfPageDef` (`ui/PerfPageStore.kt`) carry no `version` field; both load with `ignoreUnknownKeys`. Contrast `FxDefaultDto.version` and versioned preset DTOs.
- Impact: a future format change cannot be detected or migrated; user files will silently misparse or be rejected by the validator.
- Fix approach: add `version: Int = 1` now (cheap, backward compatible) and reject/migrate newer values explicitly.

**Remaining per-event allocations on the controller / render path:**
- `CommandInput.Delta(steps)` and `CommandInput.Value(value)` (`control/Command.kt`) are allocated per encoder/fader message (button `Press.DOWN/UP` are shared instances; those are fine).
- `isTransModified` in `ui/PerformanceTransitionsControls.kt` (`mixer.transitionFilter?.let { ... }`) evaluates a capturing lambda per frame.
- `ui/FxChainHeader.kt` builds lambdas per frame.
- Impact: small GC pressure during fast encoder turns / every frame; contradicts the zero-allocation rules in `ARCHITECTURE.md`. Not measured.
- Fix approach: reuse a mutable per-runtime input object (handlers run on one thread), hoist lambdas to fields.

**`KnobCommands.browseAccum` carry-over:**
- Issue: `browseAccum` (`control/KnobCommands.kt`) is reset when browse mode ends and in a couple of paths, but a sub-step remainder from a previous browse session can carry into the next one if browse restarts without passing through those resets (e.g. switching list/context while a partial delta is pending).
- Impact: first detent of a new browse can step early/late. Minor; unconfirmed on hardware.
- Fix approach: reset on every browse-context change (publish/reset of `ChainListBrowse`, `LibraryNavigation.setViewMode`), and add a regression test.

**Global mutable singletons:**
- Issue: `PresetManager`, `PlayQueueManager`, `BgQueueManager`, `FXQueueManager`, `FXBgQueueManager`, `TransitionQueueManager`, `UITheme`, `MidiMappingManager`, `OscMappingManager`, `MacroEngine`, `MacroLearnState`, `OscLearnState`, `FxShortlist`, `CVRegistry` are process-wide `object`s. `SessionContext` is a partial facade. Newer code (`ControllerManager`, `ControllerProfileStore`, `PerfPageStore`) takes injectable dependencies, which is the better pattern.
- Impact: tests must reset singleton state manually (`mockkObject` / `clearQueue()`); multiple sessions are hard.
- Fix approach: explicit state holders passed from `SessionContext`; keep singleton facades only at app boundaries.

**Browser tier panels are parallel copies:**
- Issue: `ui/browser/` split the old `AssetBrowserPanel` (resolved 2026-09-18), but the Preset / FX / Transition list panels still share near-identical draw/search/context-menu code. `PlayQueueManager` / `BgQueueManager` keep their own copy of the index bookkeeping and are intentionally not on `QueueEngine` (live-show critical, behaviorally different). Treat as accepted duplication unless a clean abstraction appears.

**Manual parameter path registry:**
- Issue: `ParameterResolver.getAllParameterPaths()` hard-codes every mixer/deck/Mandala path (still true). MIDI/OSC/macro bindings key off these paths, so renaming a parameter silently breaks saved mappings.
- Fix approach: derive paths from parameter owners/descriptors; treat unresolved bindings as validation errors with a test.

## Known Bugs / Fragility

**JACK reconnect vs. in-flight preset work (not reproduced):**
- `MidiJackWatchdog` calls `AudioEngine.tryReconnect()` from a daemon thread (stop/start) while preset I/O runs on `PresetManager.presetIoExecutor`; no lifecycle coordination between them. Workaround: `UITheme.audioEngineEnabled = false` or `MidiJackWatchdog.isJackReconnectActive = false`.
- Files: `audio/MidiJackWatchdog.kt`, `audio/AudioEngine.kt`, `presets/PresetManager.kt`.

## Security Considerations

**Managed-path confinement is a checklist item for every new write site:**
- `FileSystemManager.isManagedAssetPath()` / `requireManagedAssetPath()` confine rename/clone/move/delete and (since 2026-09-19) the new-playlist and export-queue write sites to managed roots. This was missed once for a brand-new asset type; any new file-creating code (imports, new asset types) must call it. The two new user-JSON stores write only inside their fixed `library/controllers` and `library/perform_pages` directories via `UserJsonFiles`, and `PerfPageStore` validates page ids against `[a-z0-9][a-z0-9_-]*` before they become filenames; confirm `ControllerProfileStore` does the same for profile ids (not verified).
- Files: `ui/FileSystemManager.kt`, `ui/PlaylistManager.kt`, `ui/browser/BrowserPopupHandler.kt`, `control/UserJsonFiles.kt`, `ui/PerfPageStore.kt`.

**Distribution JRE checksums are placeholders (still open):**
- `build.gradle.kts` has `jreChecksums` and `verifyChecksum()`, but all five platform entries are still `"EXPECTED_SHA256_HERE"` and verification is skipped with a warning. Fill in real SHA-256 values and make the placeholder fail the build.

## Performance

**Library/playlist scanning is synchronous on the UI thread (partially mitigated):**
- `FileSystemManager` has a 1 s `scanCache`, but a cache-miss scan, playlist validation (`readText()`) and scans triggered from draw calls still run on the render thread. Large libraries can stall frames.
- Fix approach: async validation, debounced refresh, immutable scan results.

## Test Coverage Gaps

**Controller/navigation UI (updated 2026-10-03):** `src/test/.../control/` covers CcQueue, CommandRegistry, ControllerFeedback (+sequence, manager), ControllerProfile, ControllerRuntime, KnobCommands browse and NavCommands; `midi/MidiEngineQueueTest` covers the capped queue; `ui/` has `PerfPageStoreTest`, `PerformSurfaceTest`, `PerfRowLayoutTest`, `LibraryNavigationTest` and `NavigationSurfaceTest` (new, uncommitted). Still without tests when this was written:
- `BackNavigation` (Esc / controller back priority stack),
- `PerformPagesPanel` temporary-tab behavior,
- `PerformanceBrowseBay` / `ChainListBrowse` cursor,
- `PerfKnobSpec` (`KnobSpec` resolution per row mode).
- `ProfileBindingEdit` and `ControllerProfileStore` (user override / rejection) have no dedicated test file; confirm before relying on them.

**Audio DSP and JACK callback:** no tests for callback allocation behavior, `nframes` bounds, reconnect lifecycle (`AudioEngineTest` and `BeatDetectorTest` cover the DSP, not the JACK callback path). Priority: High.

**Filesystem/playlist safety:** `FileSystemManagerTest` covers confinement, scan cache and FX playlist validation. Still untested: the creation/export write sites in `ui/browser/BrowserPopupHandler.kt` and `PlaylistManager.createPlaylist()` calling `isManagedAssetPath()`; session path portability. Priority: Medium.

**Rendering lifecycle / GL resources:** `RenderingLifecycleTest` exists but real GL context creation, secondary window context switching and renderer shutdown are not exercised. Priority: Medium.

**MIDI profile persistence:** `MidiMappingManagerTest` exists; loading `UITheme.activeMidiProfile` at startup (`Main.kt` calls `MidiMappingManager.loadProfile`) is not covered end to end. Priority: Low.

## Scaling Limits

- **Asset library size:** synchronous scans (see Performance); scaling path is a file index with a watch service.
- **Session persistence:** queues store absolute paths in `library/last_session.json`; moved libraries lose items (`MissingItemsPanel` now surfaces unresolved items for repair, but paths are still absolute).
- **MIDI event queue:** `MidiEngine.MAX_QUEUED_EVENTS = 4096`; beyond that new events are dropped with one warning until the queue drains (by design, bounds memory if the render thread stalls).

## Dependencies at Risk

- **JACK:** `org.jaudiolibs:jnajack:1.4.0` is platform-sensitive; audio degrades to Java Sound / silent without it.
- **Version pins** (re-checked in `build.gradle.kts`): Kotlin 2.3.0, Shadow plugin 9.0.0 (do not update automatically), imgui-java 1.92.7.1 (see `docs/developer/imgui_upgrade_guide.md`), logback 1.5.38. Keep periodic security/JDK review and run packaging smoke tests after upgrades.

## Missing Features

- **No integrated render/audio latency budget view.**
- **I/O status:** `PresetIOStatus` exists for UI feedback, but not every async failure path is confirmed surfaced; verify before claiming parity.
- **No profile-binding or Perform-page versioning/migration** (see Tech Debt).

## Resolved / Stale (do not re-investigate)

- RESOLVED: asset browser monolith (split into `ui/browser/`, 2026-09-18).
- RESOLVED: queue/playlist logic duplication for FX and Transition (`QueueEngine`, `FxQueueEngine`; `PlayQueueManager`/`BgQueueManager` deliberately excluded); generic `PlaylistParser`; `validateFxPlaylistFile()`.
- RESOLVED: saved MIDI profile applied at startup (`Main.kt` `loadProfile`); MIDI profile name sanitization (`sanitiseProfileName`); JSON `.lsdplay` validation shares `PlaylistParser`.
- RESOLVED: unbounded async I/O (`PresetManager.presetIoExecutor`, a single-thread executor used by `PresetRepository`).
- RESOLVED: frame limiter busy-yield (`Main.kt` now uses two calibrated sleeps; `Thread.yield()` removed).
- STALE/NOT FOUND: beat-history `System.arraycopy` in the JACK path (no such copy remains in `audio/AudioEngine.kt`); `FBO`/`Shader` `finalize()` warnings (no `finalize()` remains in `rendering/`); explicit disposal is still mandatory (`FBO.dispose()`, `Shader.dispose()`), and `GLResourceTracker` exists for leak tracking.
- STALE: `PatchGrid*`, `MixerMonitorPanel`, `SettingsPanel`, `BeatDetector` as a separate file, classic Parameters/Properties views.

---

*Concerns audit: 2026-10-03*
