# Lane 2: Architecture rules (audit 2026-11-15)

Baseline `audit-2026-10-29` (fa4335e) → HEAD aeb7937 (154 commits). Read-only. `B` = `src/main/kotlin/llm/slop/liquidlsd`.

## Checklist coverage

| Item | Done | Result |
|---|---|---|
| DECISIONS.md, claim by claim | yes, every section (1-12 plus trailing Flame/Dock entries) | 17 false/stale claims below |
| ARCHITECTURE.md, claim by claim | yes, §1-§14 + Unverified | see table |
| File counts | recounted | stale (L2-13) |
| Thread table | grep `Thread(`, `thread(`, `Executors.`, `CoroutineScope`, `pw_thread_loop` | stale (L2-11) |
| Frame-loop steps (Main.kt:529-760) | read | true, steps 1-5 in order; shutdown order true; startup true except the first-run note (L2-1) |
| Persistence layout | every path grepped | true, except seeding source and `.unreadable` copies (L2-13) |
| Subsystem list vs new work since baseline | checked | PipeWire/Syphon/Spout rewrite, BeatPhaseLock, transient hits/Hi-Mid, factory content partly or not described (L2-9..13, L2-21); KIFS Storm, spectrogram/scope in the DECISIONS stock list (true); cymatics Pitch Follow is shader-only, needs no entry; Link follow/lead is on unmerged branch `link-follow-lead`, correctly absent |
| Unverified list | each re-checked | 3 verified (drop), 2 kept narrowed (see end) |
| LayerDependencyTest + new violations | read; grepped every package for `ui.` | test passes its own scope; 2 new ui imports outside it (L2-15) |
| Mechanical checks | proposed | L2-24..L2-30 |

Identifier sweep: every backticked identifier of 4+ characters in both files was grepped against `src/main` (excluding the bundled `resources/docs` copy), `build.gradle.kts`, `.github`, `scripts`. All exist except intentional "deleted" names (verified absent from `src/main/kotlin`: `Column3HeaderToggle`, `MacroPanel`, `MacroBindingInspector`, `Column3Mode`, `ShaderPickerPopup`, `ChainListBrowse`, the three `*PlaylistEditorPanel`s, `FX_SENDS`, `isDeckRowFx`, `isMasterRowFx`, `selectedRackMacroId`, `handleDirtyDeck`, `loadDeckPresetAsync`, `SectionMode`, `lsdfxbank`, `unifiedBrowser`, `SiteGenerator`) and `MAX_TRANSITION_STEPS_PER_FRAME` and `Icosahedron.kt` (L2-18, L2-6).

### Claims checked (true unless an id is given)

| Doc § | Claim | Result |
|---|---|---|
| A intro | stack, ISF-everything, blit/mixer/view2d + mandala/flame exceptions | true |
| A UI-name table | all code names exist | true |
| A §1 | Thread 0 owns GL, ImGui, Ops drains, MacroEngine.tick, CVRegistry.updateAll | true |
| A §1 | MIDI queue `MAX_QUEUED_EVENTS = 4096` (MidiEngine.kt:37) | true |
| A §1 | `osc-receiver` → `OscEngine.inboundQueue` CLQ | true |
| A §1 | `PresetManager-IO` single daemon; Garden rescans, thumbnails, flame import on it | true |
| A §1 | ISF scanner/watcher/FileSystemScanner, BroadcastEngine-IO, midi-feedback-writer, controller-native-leave hook, Evdev reader, UpdateChecker, MidiJackWatchdog, ManualReconnect, TouchpadPermissionElevation, JackClient-ErrorLogger, Carabiner, SystemAudioVolume | true (names match) |
| A §1 | audio callback component list | stale L2-10 |
| A §1 | thread table complete | stale L2-11 |
| A §1 | `ToastOverlay` one `AtomicReference` | true |
| A §1 / D §1 | layering + LayerDependencyTest allow-lists | true for midi/control; L2-15 elsewhere |
| A §2 | frame-loop steps 1-5, offline render replaces step 3, 60 s autosave | true |
| A §2 | shutdown order | true (plus `RealtimeRecorder.stopRecording(waitForFinish = true)` now before streamers, Main.kt:767, not listed: polish, folded into L2-11) |
| A §2 | startup: sync loadAll, Garden.refreshPool, mandala clones, startEmpty, loadSession unless EMPTY | true |
| A §2 / D §1 | "first run is four blank decks" | **false** L2-1 |
| A §3 | pipeline diagram, progress = (crossfade+1)/2 (Renderer.kt:256), at-rest copy | true |
| A §3 | Deck view params, DeckSlot | true |
| A §3 | role by image count incl. `progress`; `ISFLibraryRegistry.allAssets` is what the browser reads | **false** L2-8 |
| A §3 | directory priority, ISFFileWatcher, stock lists | true |
| A §3 | ISFAutoBindEngine tiers, StockDefaults | true |
| A §3 | audio into shaders | stale L2-12 |
| A §3 | TimeSource, PhaseIntegrators, ClockAnchors | true |
| A §3 | LSD_COST badge via `rowSelectable(..., gpuCost)` (TransitionBrowserPanel.kt:88, BrowseCatalogs.kt:80,115), DeckRenderBudget | true |
| A §3 | Fractal Flame / Garden description | true (spot-checked names) |
| A §4 | DeckOps queue, `DeckChange` variants (DeckOps.kt:25-32), injected hooks (DeckOps.kt:59-68) | true |
| A §4 | FxOps / TransitionOps entry points | true (all listed functions exist) |
| A §4 | three undo sinks are one lambda (UIManager.kt:118-120), depth 30 (ParametersState.kt:627) | true |
| A §4 | queue structure, cap 8/frame (UIManager.kt:503) | true |
| A §5 | FxChain fields, FX is session state, FxMacroSync sole writer | true |
| A §6 | CV source list | stale L2-9 |
| A §6 | `updateAll` skips audio sources (CVRegistry.kt:313) | true |
| A §7 | canonical bank ids (MacroEngine.kt:51-55), sync-owned, applyFileToBank refuses (MacroBankSerializer.kt:100), slot banks (FxMacroSync.kt:39) | true |
| A §8 | MIDI/OSC/controller flow, CommandKind, KnobCommands/NavCommands | true |
| A §8 / D §6 | stock profile `wrapPages` | **false** L2-2 |
| A §8 | native mode, special knobs, Chain view, touch backends, keyboard | true |
| A §9 | backend modes, ClockSource + ABLETON_LINK→MANUAL (ClockSource.kt:21), Link backend order (AbletonLinkEngine.kt:33-51) | true |
| A §9 | three `@Volatile` anchors (CVRegistry.kt:14-16) | true |
| A §9 | audio-thread component list | stale L2-10 |
| A §10 | UIManager render order, ViewState, views, pair/bound-in-place, pages/rows, Edit bay, Library, ScopeMemory, Recents limit 20 (BrowseRecents.kt:21), QueueSplit | true |
| A §10 | `CvTheme.LIGHT_THEME_SCALE` 0.62 (CvTheme.kt:91) | true |
| A §10 / D §8 | "light theme not checked visually; remaining literals: AudioEnginePanel" | stale L2-17 |
| A §11 | every path in the table | true (grepped each) |
| A §11 | session `version = 6` (PresetModels.kt:306) | true |
| A §11 | preferences tmp + move (AppPreferencesStore.kt:454) | true |
| A §11 | first-run seeding "generated by Gradle from defaults/" | stale L2-13 |
| A §12 | recorder, PBO, offline render, streamers per endpoint | true |
| A §13 | broadcast, sync, web ports list | stale L2-21 |
| A §14 | Kotlin 2.3.0, JVM 17, LWJGL 3.3.3, imgui 1.92.7.1, JNAJack 1.4.0, JNA 5.19.1; tasks run/test/checkWebSync/syncWeb/packageZips/curateFlames/flameStats/compareVariation; CLI flags (CliArgs.kt:52-102) | true |
| A §14 | file counts, CI workflow list | stale L2-13, L2-22 |
| D §1 | thirdPartyNotices, license-audit path exists | true |
| D §1 | JNAJack, Java Sound fallback, Thread 0 GL, `-XstartOnFirstThread`, ZGC 2 ms (build.gradle.kts:139-146 and launchers) | true |
| D §1 | presetIoExecutor does all library file work a frame could wait on | **decision** L2-14 |
| D §1 | first run four blank decks, `PresetManager.startEmpty` | **false** L2-1 |
| D §1 | Twister native mode / clock knob | true (spot-checked) |
| D §2 | role from header, universal ingestion, registries own role | true (registries); L2-8 for scanner |
| D §2 | 37 generators, names, 8 transitions | true (default_sources has exactly the 37; default_transitions the 8) |
| D §2 | heavy ones LSD_COST 3, simulations LSD_COST 2, light set untagged | true (checked every `.fs`) |
| D §2 | fractals/nebula/simulations "desktop-only (no web sync entry)" | **false** L2-5 |
| D §2 | Mandala exceptions only ValueParamSection + WebPresetSerializer | true (Main.kt bootstrap aside) |
| D §2 | "Colors" generator | **false** L2-19 |
| D §2 | PipeWire DMA-BUF export + shm fallback | **false** L2-4 |
| D §2 | four decks, view pipeline, master level | true |
| D §3 | DeckOps/TransitionOps/setActive bookkeeping, `MacroLearnState.getActiveStatus()` → ToastOverlay (UIManager.kt:388) | true |
| D §3 | "macro/ cannot depend on ui/" | **false** L2-15 |
| D §4 | FxOps single path, Super Knob, Metaknob, StockDefaults tiers | true (names/structure) |
| D §4 | "Audio Response multiplies the eight audio CV signals"; AGC only replaces divisors | stale L2-9 |
| D §5 | modulator ids, lock queries, ownership order, FX/TRANS read-only | true (names exist; behaviour per lane 1) |
| D §6 | command registry, logical input ids, 16 knobs = grid, ring/LED pacing (`minIntervalMs` 2, MidiOutputPorts.kt:29) | true |
| D §6 | "Right-middle = bank, left-middle = Chain Link" | **false** L2-3 (left-middle true) |
| D §6 | built-in pages ab/bgpv/mixer, mixer = trans, master.fx, master.mix, global | true |
| D §6 | "Left alone: CommandContext's concrete Mixer" | stale L2-16 |
| D §6 | UserJsonFiles atomic write, schema version | true |
| D §7 | entries (BrowserPane, LibraryRefs, folders, tags, ApplyTarget, dock selection, QueueOps, prompt, SearchMatcher, NextUp, Maps, advanceBy, Esc) | true except L2-18, L2-20 |
| D §8 | geometry rule, QueueSplit, QueueDrop, knob drag, window frame | true (names) |
| D §8 | TangoPalette sole source, no theme forks | **false** L2-17 |
| D §9 | four bands, flux, `BeatTrackerEngine` details | stale L2-9, L2-10 |
| D §10 | sync translation `330 core`→`300 es` (sync_web.py:51-55); hashed Kotlin sources | partly **false** L2-6 |
| D §10 | touch console evdev, EVIOCGRAB, access(2) (LinuxEvdevTouchBackend.kt:34,54) | true |
| D §11 | targets, packaging, launchers forward args | true |
| D §11 | `v1.0.0-beta.N` tags | **false** L2-7 |
| D §11 | CLI list | stale L2-22 |
| D §12 | removed items absent from code | true |
| D trailing | Dock, Flame variations, provenance ledger, FlameUnits, quality gate | true (names, tests exist) |

## Findings

### L2-1 [wrong] First run no longer starts with four blank decks
- Where: DECISIONS.md:33; ARCHITECTURE.md:67 ("DECISIONS §1: first run is four blank decks"). Code: B/presets/SessionSerializer.kt:110-118 (`loadInitialPreset` when no session file), :400-418 (`FIRST_RUN_PRESETS`: groove-mandala, tunnel-synthwave, ambient-tides, julia-groove via `DeckOps.request(..., LoadOrigin.QUEUE)`).
- Evidence: 3ea7b52 made a fresh install open playing. DECISIONS still says "No hard-coded startup generator" and names `PresetManager.startEmpty`, which is `SessionSerializer.startEmpty` (SessionSerializer.kt:365).
- Impact: anyone relying on the decision would "fix" the factory first run back to blank.
- Fix: rewrite the bullet: first run = `startEmpty` then the four factory presets (light sources, skipped if deleted); a failed load still starts empty. Update ARCHITECTURE §2 startup.
- Verified:

### L2-2 [wrong] The stock Twister profile does not set `wrapPages`
- Where: DECISIONS.md:265 (Perform pages are data: "`banks.wrapPages` in the stock profile"); ARCHITECTURE.md:171. Code: src/main/resources/controllers/midi-fighter-twister.json:11-20 (banks: count 4, pages ×3, no `wrapPages`; same in `-xt.json`); B/control/ControllerRuntime.kt:74.
- Evidence: without `wrapPages`, a hardware bank 4 gives `page = null` and the page is left alone; nothing loops to bank 1. The bank-step button does wrap (`floorMod`, ControllerRuntime.kt:95), which is a different thing.
- Impact: a user who selects bank 4 on the device gets the old page, not bank 1 as documented.
- Fix: either add `"wrapPages": true` to both Twister profiles (behaviour change, user's call) or reword both docs to "the bank-step button wraps over three pages; device bank 4 keeps the current page".
- Verified:

### L2-3 [wrong] The Twister bank button is right-top, not right-middle
- Where: DECISIONS.md:259 ("Right-middle = bank (shift = previous), left-middle = Chain Link"). Code: midi-fighter-twister.json inputs: `bankstep` cc 11, `chainlink` cc 9, `shift` cc 10, `side` ccs [8, 12, 13]; the profile's own description says "right-top steps to the next bank".
- Evidence: CC order per bank is 8+6n left-top … 13+6n right-bottom, so cc 11 = right-top.
- Impact: docs and the user guide (if copied) point at the wrong button.
- Fix: "Right-top = bank".
- Verified:

### L2-4 [wrong] PipeWire has no DMA-BUF export
- Where: DECISIONS.md:86. Code: B/rendering/pipewire/PipeWireBridge.kt:48-49 ("Frames go through shared memory; GPU (DMA-BUF) sharing isn't implemented"). Baseline had `checkDmaBufSupport()`; bf27791 rewrote it with hand-built SPA pods (B/rendering/pipewire/SpaPod.kt, constants from PipeWire 1.6 headers).
- Evidence: shared memory only, PBO readback (TextureStreamer.kt:122-153).
- Impact: misleads anyone tuning PipeWire performance.
- Fix: "PipeWire over JNA (libpipewire-0.3 ABI), shared memory buffers, SPA pods built by hand (`SpaPod`)". Also mention the Spout (`SpoutApi`, vtable calls into SpoutLibrary.dll 2.007.017) and Syphon (`SyphonBridge`, objc_msgSend, autorelease pools) rewrites from fb3b073/ab3e52a in ARCHITECTURE §12.
- Verified:

### L2-5 [wrong] The heavy fractals, nebula and simulations do have web sync entries
- Where: DECISIONS.md:63 ("desktop-only (no web sync entry)"). Code: web/sync_manifest.json:427 (mandelbox), :439 (nebula_volume), :451 (reaction_diffusion); every default source except `fractal_flame` has a manifest entry.
- Evidence: already true at the baseline (`git show audit-2026-10-29:web/sync_manifest.json` has mandelbox).
- Impact: someone might drop the web copies or skip web parity for them.
- Fix: replace with "all stock generators but `fractal_flame` are synced to the web".
- Verified:

### L2-6 [wrong] The hashed Kotlin sources are not `Icosahedron.kt`
- Where: DECISIONS.md:262 (§10). Code: web/sync_manifest.json:457-479 `monitored_sources` = `cv/Evaluators.kt`, `broadcast/WebPresetSerializer.kt`, `audio/SpectrumAnalyzer.kt`; plus `mandala_recipes` from `MandalaLibrary.kt` (:495-498). No `Icosahedron.kt` exists.
- Evidence: also not monitored: the web twins of `AudioAgc`/`SectionWatch` (dsp.js/energy.js), `TransientDetector` (transient vectors), `BeatPhaseLock` (beattrack.js) – these rely on vector tests instead.
- Impact: wrong guidance on what drift the check catches.
- Fix: list the real three + recipes, and say which ports are guarded by vector tests instead.
- Verified:

### L2-7 [wrong] CI tags are `v0.9.N`, not `v1.0.0-beta.N`
- Where: DECISIONS.md:271. Code: .github/workflows/release.yml:52-65 (`git tag -l "v0.9.*"`, next `v0.9.{n+1}`).
- Impact: release docs wrong.
- Fix: "`v0.9.N`" (and note a pushed tag is used as-is).
- Verified:

### L2-8 [wrong] `ISFLibraryRegistry` snapshot is not what the browser reads; `ISFAssetType` ignores `progress`
- Where: ARCHITECTURE.md:89. Code: B/rendering/isf/ISFLibraryRegistry.kt:45-49 (`allAssets`, `getAsset`: no readers anywhere in `src/main`); B/ui/browser/BrowseCatalogs.kt:38,80,115 reads `VisualSourceRegistry.availableSources` and the filter/transition registries. B/rendering/isf/ISFScanner.kt:97-106 classifies by image count only, while ISFFilterRegistry.kt:120 and ISFTransitionRegistry.kt:121 also use a `progress` input.
- Evidence: one image + `progress` is TRANSITION in the registries but FILTER in `ISFAssetType`. Harmless today because nothing reads `ISFAssetType`, but the scan result itself looks dead (the scan runs at startup, VisualSourceRegistry.kt:279-285, for nothing but `isScanning`/progress in MenuBar).
- Impact: doc misleads; a future reader of `allAssets` gets wrong roles.
- Fix: doc: roles are decided by each registry; drop the `allAssets` sentence. Code (owner): either delete the unused snapshot or make `ISFScanner` call the same role function as the registries.
- Verified:

### L2-9 [stale] Audio CV signals: five bands, hits, band energies
- Where: ARCHITECTURE.md:136 (`audio_amp|bass|mid|high`, `audio_flux_*`, section sources); DECISIONS.md:171 ("eight audio CV signals"), :173 (AGC "only replaces the CV normalisation divisors"), :250 ("four bands", "transient (spectral flux)"). Code: B/cv/AudioSignals.kt:14-19 (LEVELS/TRANSIENTS × 5 incl. `himid`, `audio_energy_bass|mid|himid|high`); B/audio/AudioEngine.kt:794-815 (Response `k` scales 10 signals; hits sized by `hitScale*` against the band's recent hits, AGC divisor only caps them; `TransientDetector`).
- Impact: docs describe the pre-4de0ef9/115f847 signal set.
- Fix: rewrite the CV bullet from `AudioSignals` (point at it as the one list) and the Audio Response/AGC bullets (ten signals; hits sized by HitScale).
- Verified:

### L2-10 [stale] Audio-thread components and beat-tracker details
- Where: ARCHITECTURE.md:29, :180-181; DECISIONS.md:254. Code: B/audio/AudioEngine.kt:140-153 (`himidPass`, `TransientDetector`, `BeatHopper`, `BeatPhaseLock`); B/audio/BeatPhaseLock.kt:12-21 (comb on its own thread, `steer` on the audio thread, corrections at AudioEngine.kt:780); B/audio/BeatTrackerEngine.kt:44 (`tempoEstimationIntervalBlocks = 16`, was 4) and :388 (autocorrelation spread over blocks).
- Evidence: no DECISIONS entry for BeatPhaseLock (why a separate thread, why the tempo-hold/contrast gating, jump vs slew `JUMP_BEATS`) or for BeatHopper (fixed fftSize grid so tempo is buffer-size independent). "autocorrelation every 4 blocks" is wrong.
- Impact: the beat clock now has two writers of phase (tracker + lock) that the docs don't mention.
- Fix: add BeatHopper/TransientDetector/BeatPhaseLock to §1 and §9; one DECISIONS §9 entry for phase lock; fix "every 4 blocks".
- Verified:

### L2-11 [stale] Thread table misses five threads and the PipeWire/discovery coroutines
- Where: ARCHITECTURE.md:26-40. Code:
  - `beat-phase-lock` B/audio/BeatPhaseLock.kt:247-262 (daemon, sleeps 250 ms, hands readings over by `@Volatile latest`)
  - `AudioDeviceScan` B/audio/AudioEngine.kt:460-468 (5c4f303; Java Sound list off the render thread)
  - `RealtimeRecorder-Finish` B/export/RealtimeRecorder.kt:468-476 (ff4b121; non-daemon, joined at exit via `stopRecording(waitForFinish = true)`, Main.kt:767)
  - `ffmpeg-encoder-probe` B/export/FFmpegProcessPipe.kt:85
  - named `JavaSoundClient-Capture`, `RealtimeRecorder-VideoWorker/AudioWorker`, `FFmpeg-Stderr-Reader` (table uses generic names; fine)
  - PipeWire: native `pw_thread_loop` per stream + a `Dispatchers.IO` drain coroutine (PipeWireBridge.kt:39, 74, 137); `ExternalVideoDiscovery` is a `Dispatchers.IO` coroutine (ExternalVideoDiscovery.kt:23-28), not a thread; Syphon discovery calls run in autorelease pools off Thread 0 (SyphonBridge.kt:26).
- Impact: lane-1 reviewers and future fixes use this table as the inventory of cross-thread hand-offs.
- Fix: add rows; add `RealtimeRecorder.stopRecording` to the shutdown order.
- Verified:

### L2-12 [stale] Audio into shaders now has three textures
- Where: ARCHITECTURE.md:93. Code: B/rendering/AudioTexture.kt:13-28 (512×2 stacked texture for `audioFFT`/`iChannel0`, plus 512×1 `isfWaveTextureId`/`isfFftTextureId` on fixed top units); B/rendering/isf/ISFAudioInputs.kt (per-shader `audio`/`audioFFT` input names → units); ISFParser.kt:116 treats them as samplers.
- Fix: describe the three textures and `ISFAudioInputs` (bd78bcc).
- Verified:

### L2-13 [stale] File counts, new cross-cutting utilities and seeding source
- Where: ARCHITECTURE.md:3, :277, :256, and the subsystem sections.
- Evidence: `src/main/kotlin/llm/slop/liquidlsd` has 379 `.kt` files (was 367: +13, −1 `tools/SiteGenerator.kt`); tests 229 (was 214). Not described anywhere in either file: `utils/AtomicFile` (the one safe-save path: temp + move, keeps permissions, Windows retry; `keepUnreadable` writes `<file>.unreadable` copies and posts a notice; ~40 call sites), `utils/UserNotice` (queue from non-UI code to `ToastOverlay`, drained at UIManager.kt:387), `ui/NameCheck`, `ui/browser/PlaylistTargets`/`PlaylistMenu`. Seeding: build.gradle.kts:267-271, 409-410 now builds `default_presets`/`default_fx_chains` from `web/presets/web-*.lsd` and `web/fxchains/web-*.lsdfxchain` too, not only `defaults/`.
- Fix: update counts/date; add AtomicFile + UserNotice to §1 rules and §11 (plus `.unreadable` copies row); fix the seeding row. DECISIONS §1 could carry "every user-file write goes through `AtomicFile.write`" as a rule (see L2-27).
- Verified:

### L2-14 [decision] "presetIoExecutor does all library file work a frame could wait on" does not hold
- Where: DECISIONS.md:27. Code: Main.kt:531-535 (60 s autosave: `SessionSerializer.saveSession` serialises and writes `last_session.json` on Thread 0, through `AtomicFile.write`); B/ui/browser/BrowseRecents.kt:118, BrowseFavorites.kt:106, PlaylistEdit.kt:131, BrowserPopupHandler.kt:148/184/421, ShortcutManager.kt:208, MacroBankSerializer.kt:123 write synchronously from UI code; B/utils/AtomicFile.kt `moveIntoPlace` retries with `Thread.sleep(40)` up to 4 times.
- Evidence: worst case a Thread-0 save stalls ~160 ms plus the write on a Windows box with a scanner holding the file; the autosave hitch recurs every minute.
- Impact: frame hitches; the rule as written is not what the code does.
- Fix (owner choice): narrow the rule ("preset/FX/transition/playlist loads and saves") and accept small UI writes, or move the autosave serialise-then-write to `presetIoExecutor` (serialise on Thread 0, write off it). Cross-ref lane 1.
- Verified:

### L2-15 [wrong] Non-midi/control layers import ui/, including two new sites; "macro/ cannot depend on ui/" is false
- Where: DECISIONS.md:120 ("macro/ cannot depend on ui/"), :107 / ARCHITECTURE.md:110 ("`presets/` imports no UI state"). Code: B/macro/FxMacroSync.kt:231 and B/macro/TransitionMacroSync.kt:60 (new since baseline) call `ui.ParamLabels.of`; B/presets/SessionSerializer.kt:414 (new) `ui.FileSystemManager.getPresetsRoot`; presets/ has 12 files on `UITheme`/`ToastOverlay`/`FileSystemManager`/`browser.BrowseFavorites` (Garden.kt:78); rendering/ 8, cv/ 2, parameters/ 1, audio/ 1, osc/ 1, broadcast/ 1.
- Evidence: `ParamLabels` depends only on `rendering.isf.ISFHeader`, so it is in the wrong package. `UITheme` is really the preference store, used everywhere.
- Impact: the DECISIONS sentence is false; the "UI pulls" reasoning it supports is moot. Layering drift is not guarded outside midi/control.
- Fix: move `ParamLabels` to `rendering/isf/` (or `utils/`); reword "presets/ imports no UI state" to "no UI panel state (only `UITheme` preferences, `ToastOverlay`, `FileSystemManager` roots)"; add the ratchet check L2-24.
- Verified:

### L2-16 [stale] `CommandContext` no longer holds a concrete Mixer
- Where: DECISIONS.md:96 ("Left alone: ... `CommandContext`'s concrete `Mixer`"). Code: B/control/Command.kt:32-33 (`crossfade: CrossfadeControl`); LayerDependencyTest `controlDoesNotDependOnRendering` enforces it.
- Fix: delete that clause.
- Verified:

### L2-17 [wrong] Theme forks and literals outside TangoPalette; light theme was checked
- Where: DECISIONS.md:234 ("Call sites never test the theme and never use a colour literal ... Left outside: ... AudioEnginePanel ... (open)"); ARCHITECTURE.md:189 ("The light theme has not been checked visually; remaining literals: `AudioEnginePanel`...").
- Evidence: `if (TangoPalette.isLightTheme)` forks at B/ui/FxSlotCell.kt:90 (Perform UI), B/ui/browser/QueuesPane.kt:40, B/ui/ParametersRenderer.kt:169 (ButtonChrome.kt:113 is the sanctioned `Look` switch). Literal colour counts: ParametersRenderer 25, MixerPanel 20, AudioEnginePanel 15, DeckControlPanel 11, ModulatorHeaderRow 5, TempoSyncPanel 2. `.planning/open-items.md:20-21` already lists most of these. Light-theme legibility pass 9f85a26 and round 5 checked it.
- Impact: rule says these are bugs; docs say the theme is unchecked.
- Fix: update both lists to match open-items.md (or point at it only), drop "not checked visually", and either convert the three forks to Roles or allow-list them; add L2-25.
- Verified:

### L2-18 [stale] Queue-delta "not done" contradicts the next entry; renamed constant
- Where: DECISIONS.md:400 (title cites `UIManager.MAX_TRANSITION_STEPS_PER_FRAME`), :403 ("Not done: A/B and BG queue deltas still collapse to one step ... needs advanceBy(n) (open)"). Code: B/ui/UIManager.kt:293, 297, 302, 323 (`advanceBy`), :503 `MAX_QUEUE_STEPS_PER_FRAME = 8`; DECISIONS.md:409 says advanceBy is done.
- Fix: delete the "Not done" sentence; rename the constant in the title.
- Verified:

### L2-19 [wrong] There is no `Colors` generator
- Where: DECISIONS.md:64 ("extracted into the separate `Colors` generator"). Code: src/main/resources/default_sources/ has no colors source; no `colors` id in `src/main/kotlin`.
- Fix: "backgrounds come from Deck BG" only (or name the generator that replaced it, if any).
- Verified:

### L2-20 [polish] Link to an archived review as a developer page
- Where: DECISIONS.md:391 (`docs/developer/ui_interaction_architecture_review.md`). File is at docs/archive/ui_interaction_architecture_review.md (the other two references already use the archive path).
- Fix: point at docs/archive/.
- Verified:

### L2-21 [stale] Web ports list; direction of truth for factory content
- Where: ARCHITECTURE.md:270; DECISIONS.md:262 ("The desktop is the source of truth for the web client").
- Evidence: web/beattrack.js + beatclock.js are the twins of `BeatPhaseLock` (BeatPhaseLock.kt:17 says so); web/tools/transient_vectors.json + transient.test.mjs guard the hits port; neither listed. Since 3ea7b52 the factory presets and FX chains flow web → desktop (`scripts/gen_web_presets.py` is "the one source", build.gradle.kts:267-271).
- Fix: list the ports and their guards; add a sentence that content (presets/chains) is generated once and shipped to both, an exception to "desktop is the source of truth".
- Verified:

### L2-22 [polish] CLI and CI lists
- Where: DECISIONS.md:271, :273; ARCHITECTURE.md:278.
- Evidence: DECISIONS CLI list omits the `--tour*` flags (ARCHITECTURE has them). `.github/workflows/build-syphon.yml` (manual, builds the Syphon framework release asset that release/smoke download) is not listed. Release publishing is gated on release.yml's own smoke matrix and aborts only if zero platforms pass (release.yml:335-360), not on smoke-test.yml.
- Fix: one-line edits.
- Verified:

### L2-23 [stale] ARCHITECTURE "Unverified" list
- Where: ARCHITECTURE.md:280-288.
- Evidence and verdict:
  1. bundled lists vs files: **verified**, `ISFFilterRegistry.bundledFilters` = the 27 files in default_filters/ exactly; `bundledTransitions` = the 8 in default_transitions/. Drop.
  2. Twister CC layout: **verified** from the profile: encoders ch1 CC knob+16·bank, switches ch2 same CCs, side buttons ch4 CC 8+6n..13+6n, bank switch ch4 CC n. Drop, but fix L2-2/L2-3.
  3. Touch zones: **verified** B/input/TouchConsoleController.kt:12-31, 153-160: crossfader y ≤ 0.28, deadzone 0.28-0.45, then Level A / BG / B thirds (x < 0.33, ≤ 0.67). Drop (or add one line to §8).
  4. Beat tracker: components verified (complex spectral difference ODF, two-state periodicity, causal DP with `logTauTable`, BeatTrackerEngine.kt:10-19, 64); `audio_dsp.md` claims are lane 3. Keep, narrowed to audio_dsp.md.
  5. Modulation formula: not re-read; lane 3 (modulation.md). Keep.
- Verified:

## Proposed checks (polish)

### L2-24 [polish] Check: layering ratchet for every package
- Where: src/test/kotlin/llm/slop/liquidlsd/architecture/LayerDependencyTest.kt
- Evidence: the test only guards midi/ and control/. L2-15's two new ui imports (TransitionMacroSync, SessionSerializer) landed unnoticed.
- Fix: add a per-package frozen allow-list (file → reason) for `macro/`, `presets/`, `rendering/`, `cv/`, `parameters/`, `audio/`, `osc/`, `broadcast/`, `utils/` → `ui.`; new files fail. Also `utils/` → anything but stdlib/`ui.ToastOverlay` KDoc.
- Verified:

### L2-25 [polish] Check: no theme forks or colour literals outside TangoPalette
- Fix: test that greps `isLightTheme` outside `TangoPalette.kt`, `UIThemeStyler.kt`, `CvTheme.kt`, `ButtonChrome.kt` (allow-list the three in L2-17 until fixed), and counts `colorConvertFloat4ToU32(<literal>` / `ImVec4(<literal>` per file in `ui/` against a ratchet that may only go down.
- Verified:

### L2-26 [polish] Check: doc identifiers exist
- Fix: `scripts/audit/doc_identifiers.py`: every backticked identifier/file name in ARCHITECTURE.md and DECISIONS.md must occur in `src/main` (excluding `resources/docs`), `build.gradle.kts`, `.github`, `scripts`, `web`, or in an explicit "removed names" list. Would have caught `Icosahedron.kt`, `MAX_TRANSITION_STEPS_PER_FRAME`, `Colors`. Also assert the "(N files; M test files)" line matches the tree.
- Verified:

### L2-27 [polish] Check: thread inventory and safe saves
- Fix: test that collects every `Thread(..., "<name>")`, `Executors.new*` thread name and `CoroutineScope(` file in `src/main` and asserts each name/file appears in ARCHITECTURE.md §1. Second test: no `writeText(` / `Files.write(` / `outputStream()` on user files outside `AtomicFile.kt` and an allow-list (recorder, PNG, FFmpeg temp files).
- Verified:

### L2-28 [polish] Check: one ISF role function
- Fix: unit test feeding headers (0/1/2 images, 1 image + `progress`, audio inputs) through `ISFScanner`, `ISFFilterRegistry`, `ISFTransitionRegistry` and `VisualSourceRegistry` acceptance; all must agree (fails today for 1 image + progress, L2-8).
- Verified:

### L2-29 [polish] Check: stock-set claims
- Fix: test that the DECISIONS §2 generator list equals `default_sources/` dirs, that LSD_COST 3 / 2 / none match the listed tiers, and that `web/sync_manifest.json` covers every stock source but `fractal_flame` (or whatever DECISIONS says). Same pattern for `AudioSignals` ids vs ARCHITECTURE §6.
- Verified:

### L2-30 [polish] Check: controller profile vs docs
- Fix: test in `control/` asserting the stock Twister profile's button map (`bankstep` = cc 11 right-top, `chainlink` = cc 9, `shift` = cc 10, side = 8/12/13) and `wrapPages` value, with the docs sentence quoted in the test name, so changing either side forces the other.
- Verified:

## Counts

30 findings: wrong 11 (L2-1..8, 15, 17, 19), stale 9 (L2-9..13, 16, 18, 21, 23), decision 1 (L2-14), polish 9 (L2-20, 22, 24..30; seven are `Check:` proposals). No bug-severity findings in this lane.
