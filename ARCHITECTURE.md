# Liquid LSD — Architecture

A map of the code as it is. For the *why* behind a rule, follow the `DECISIONS §n` links (`DECISIONS.md`, durable decisions only); for depth on a subsystem, follow the `docs/developer/` link at the end of its section. Verified against `src/main/kotlin/llm/slop/liquidlsd/` (380 files; 236 test files) on 2026-11-15.

Liquid LSD is a Kotlin/JVM VJ application: OpenGL 3.3 core (LWJGL 3 + GLFW), Dear ImGui (`imgui-java`), JACK (JNAJack) with a Java Sound fallback for audio, Java Sound for MIDI. **Everything that draws is an ISF shader**: generators, FX filters and transitions. The exceptions: `blit`, `mixer` and `view2d` are hard-wired shaders (`src/main/resources/shaders/`), `mandala` and `fractal_flame` are `meta.json` + `shader.frag` sources built in Kotlin, and `FlameEngine` compiles its iterate/splat GLSL inline (DECISIONS §2).

**UI name vs code name.** The UI was renamed without renaming the code (DECISIONS §1). Where they differ:

| UI name | Code name |
|---|---|
| Edit (row bay, Params only) | `PerformanceDeepEditBay`, `drawRackDeepEdit`, `ParametersState.rack*` |
| Pair view (a pair of rows over Browse) | `ParametersState.focusedPair` / `focusPair`, `PerfRows.PAIRS` / `visibleRowsForPage`, `PerformanceDeepEditBay.drawPairBay`, `PerformanceBrowseBay.drawForPair`, `PairKnobTouch` |
| Browsing a slot in place (the Perform Library bound to a row's slot) | `ParametersState.boundInPlace`, `ViewState.bound`, `LibraryPanel.drawBoundChip`, `PerformanceMatrixPanel.performDockBinding` |
| Browse dock | `BrowserDock`, `DockActions`, `DockOutline`, `ParametersState.dockSelection` |
| Header line (Back, Browse \| Params, target chip, next up) | `BayHeader` |
| Modulation column | `PropertiesPanel` |
| Library tabs Sources / FX / Transitions / Macros | `LibraryViewMode.PRESETS / FX / TRANS / MAPS` |
| Level | `levelA/B/BG/PV`, `masterLevel` |
| macro knob target | `MacroBinding`, `MacroLearnState`, `ProfileBindingEdit` |
| Mixer column | `MixerPanel`, `MixerLayout` |

## 1. Process, threads, ownership

One JVM process; `main()` is in `Main.kt`. Flags (ZGC, `-XstartOnFirstThread` on macOS, native access) come from `build.gradle.kts` and the launcher scripts (DECISIONS §1, §11).

| Thread | Owns | Talks to the rest via |
|---|---|---|
| **Thread 0** (main / render) | GLFW, every GL call, ImGui, the whole frame loop (§2), all `*Ops` drains, MIDI/OSC/controller dispatch, `MacroEngine.tick`, `CVRegistry.updateAll`, offline render (`OfflineRenderStudio.step`), `BroadcastEngine.tick`, `ParametersUndo` | — |
| Audio callback (JACK process thread, or `JavaSoundClient` capture thread) | `AudioEngine.processAudio`: biquads, RMS, hits (`TransientDetector`, `HitScale`), `BeatHopper` → `BeatTrackerEngine`, `BeatPhaseLock.process` / `steer`; no allocation, no locks | `@Volatile` primitives, `CvHistoryBuffer`, `CVRegistry.updateBeatAnchor` / `alignBeatPhase` |
| MIDI receiver (Java Sound `Receiver`, `MidiEngine.MidiInputReceiver`) | decodes messages | `MidiEngine` atomic state + capped queue (`MAX_QUEUED_EVENTS = 4096`) drained on Thread 0 |
| `osc-receiver` | UDP read + `OscCodec` decode | `OscEngine.inboundQueue` (`ConcurrentLinkedQueue`) drained on Thread 0 |
| `PresetManager-IO` (`presetIoExecutor`, single daemon) | preset/playlist/FX file read and write (`PresetRepository`); the 60 s session autosave write (serialised on Thread 0); recents, favorites, playlist and shortcut saves; Garden pool rescans (`Garden.refreshPool`), Garden thumbnail PNG encode/write (`GardenThumbnails`), flame import (`FlameImportOps.importFile`) | `CompletableFuture`s; results are applied by the owning `*Ops` drain or posted back with `VisualSourceRegistry.runOnGlThread`, never on this thread; the Garden pool is a `@Volatile` snapshot |
| `ISF-Library-Scanner`, `ISF-FileWatcher-*`, `FileSystemScanner` | directory scans, debounced watch | `VisualSourceRegistry.pendingGlTasks` (drained on Thread 0) and `@Volatile` immutable snapshots |
| `RealtimeRecorder-VideoWorker` / `-AudioWorker`, `FFmpeg-Stderr-Reader` | frame/PCM write to FFmpeg | `SpscQueue`, `ArrayBlockingQueue` |
| `RealtimeRecorder-Finish` (non-daemon, one per stop) | joins the workers, muxes video + audio, moves the file into place | `isFinishing` flag; exit waits for it (`stopRecording(waitForFinish = true)`) |
| `beat-phase-lock` (daemon, `BeatPhaseLock.startWorker`) | every 250 ms, the comb over the last 8 s of onset envelope | reads the audio thread's envelope ring by `@Volatile` write count; hands a reading back through one `@Volatile latest` that `steer` picks up on the audio thread |
| `AudioDeviceScan` (daemon, one at a time) | Java Sound input-device list | `@Volatile` cached device arrays; an `AtomicReference` keeps it to one scan |
| `BroadcastEngine-IO` | WebSocket send/reconnect | `AtomicBoolean needsFullSync`, serialization on Thread 0, send async |
| `midi-feedback-writer` (per `MidiOutputPorts` sink) | paced controller LED/ring writes; on `close()` it flushes pending SysEx (e.g. leaving native mode) and releases its own port, so `close()` returns at once (`MidiSink.awaitClosed` waits, exit only) | `control.CcQueue` (coalescing) |
| `controller-native-leave` (JVM shutdown hook, installed once a native-mode device connects) | fallback `ControllerManager.shutdown()` for Ctrl-C/SIGTERM; once-only (`AtomicBoolean`), so after Main's explicit call it does nothing | `feedbackLock` around the feedbacks map |
| `LiquidLSD-EvdevTouchpadReader` | Linux touchpad read | `TouchConsoleController.eventQueue`, drained on Thread 0 |
| PipeWire (`rendering/pipewire/PipeWireBridge`) | a native `pw_thread_loop` per stream, plus a `Dispatchers.IO` coroutine that copies the pending frame into the stream's buffer | `AtomicReference` pending frame (filled from the PBO readback on Thread 0), `@Volatile negotiated` |
| `LiquidLSD-UpdateChecker`, `MidiJackWatchdog-Daemon`, `AudioEngine-ManualReconnect` (Preferences Reconnect button), `LiquidLSD-TouchpadPermissionElevation` (pkexec udev rule), `JackClient-ErrorLogger`, `CarabinerTcpClient`, `SystemAudioVolume-Worker`, `ffmpeg-encoder-probe`; `ExternalVideoDiscovery` (a `Dispatchers.IO` coroutine poller; Syphon discovery calls run in their own autorelease pools) | daemons; never touch GL | — |
| Tour renderer (`--tour`) | no thread of its own: `TourRunner` steps Thread 0 on a fixed 60 fps clock | — |

Rules (DECISIONS §1): GL objects are created/disposed only on Thread 0, so anything off-thread queues a task that Thread 0 drains. The audio callback never allocates or blocks. Hot per-frame paths (MIDI/CV routing, controller input, Perform draw) avoid allocation: paths are resolved into flat arrays when mappings change (`ResolvedMidiBinding`, `ParameterResolver.pathCache`). ImGui native buffers (`ImString`, `ImInt`, fonts) are fields, not per-frame locals. `LayerDependencyTest` forbids `midi/` and `control/` from importing `ui/`, allow-lists the only `midi/ -> control/` references, and forbids `midi/ -> rendering/` except `MidiMappingManager` (takes `rendering.Mixer` to resolve learned parameter paths). `macro/` imports nothing from `ui/`; `presets/` takes only `UITheme` preferences, `ToastOverlay`, `FileSystemManager` roots and favorites keys, never panel state. `ToastOverlay` keeps text + expiry in one `AtomicReference` (shown from `PresetManager-IO` too); code that can't reach the UI posts to `utils/UserNotice`, which `UIManager` drains into it. User files are written through `utils/AtomicFile` (temp file + move; unreadable files kept as `<name>.unreadable`).

Deeper: `docs/developer/architecture.md`, `docs/developer/audio_dsp.md`, `.agents/skills/`.

## 2. Frame loop

`Main.kt` loop, in order, each frame:

1. Every 60 s (not in a tour): `AppPreferencesStore.savePreferences` + `SessionSerializer.saveSession` (autosave; the session is serialised here and written on `presetIoExecutor`). `glfwPollEvents`; in a tour, `tour.beginFrame()`; `touchConsoleController.processPendingEvents`.
2. Input phase: `uiManager.processInput(mixer)` drains incoming MIDI (`MidiMappingManager.processGlobalMidiEvents`), updates controller feedback and applies MIDI/CV queue advances, so a knob turn reaches this frame's render. It runs even when the UI phase is skipped (minimised window) and during an offline render. Keyboard shortcuts stay in `render`, where ImGui key state is fresh.
3. Render phase: if `OfflineRenderStudio.isRendering`, `step(mixer, renderer)` replaces it; otherwise
   1. resize the pipeline if `UITheme.renderWidth/Height` changed (`mixer.resize`);
   2. `DeckOps.drainOnGlThread`, `FxOps.drainOnGlThread`, `TransitionOps.drainOnGlThread` (§4);
   3. `VisualSourceRegistry.processPendingGlTasks`;
   4. `CVRegistry.updateAll` (Link anchor, non-audio CV sources);
   5. `MidiMappingManager.update` (slew/convergence of MIDI mappings), then drain `OscEngine.inboundQueue` into `OscMappingManager.onOscMessage` and `OscMappingManager.update`;
   6. `MacroEngine.tick` (before any deck evaluates, so macro-driven values are in place);
   7. `DeckRenderBudget.nextFrame`, PV resized to its budget size (half output unless streamed); for A, B, BG, PV: `deck.update()` always, `renderer.renderDeck(deck)` only if `budget.shouldRender` (off-air decks every other frame with `UITheme.offAirHalfRate`; streamed decks always);
   8. `mixer.update()` then `renderer.renderMixer(mixer)`;
   9. `TextureStreamerManager.update` for each `VideoOutputEndpoint` (Spout/Syphon/PipeWire out), `RealtimeRecorder.captureFrame`, `BroadcastEngine.tick`.
4. Blit `mixer.masterFBO` to the window (when background video or Output View is on), then `uiManager.render(...)` (keyboard queue shortcuts, then the ImGui frame).
5. `glfwSwapBuffers`; optional secondary output window (second GL context, blits `masterFBO`); frame cap from `UITheme.maxFps` by sleep (no spin).

Shutdown order: `tour.finish`, save preferences, stop broadcast, `RealtimeRecorder.stopRecording(waitForFinish = true)` (the file must be complete), stop streamers, `SessionSerializer.saveSession` (not in a tour), `MidiMappingManager.controllers.shutdown()` (controllers leave native mode while the MIDI ports are still up; waits up to 1 s for the SysEx), `MidiJackWatchdog.stop`, `AudioEngine.stop`, `MidiEngine.close`, `OscEngine.stop`, dispose GL, `GLResourceTracker.assertNoLeaks`.

Startup: libraries and ISF registries load on Thread 0 before the loop (`VisualSourceRegistry.loadAll(async = false)`, `ISFFilterRegistry.loadAll`, `ISFTransitionRegistry.loadAll`), and `Garden.refreshPool` queues the first Garden scan; four decks are created with a cloned `mandala` source, `SessionSerializer.startEmpty` blanks them, then `loadSession` restores `library/last_session.json` unless startup behaviour is EMPTY. With no session file it queues the four factory presets (`SessionSerializer.FIRST_RUN_PRESETS`) through `DeckOps`, so a first run opens playing; an unreadable session file is kept aside and the decks stay blank (DECISIONS §1).

## 3. Rendering

Key files: `rendering/Renderer.kt`, `Mixer.kt`, `MixerDecks.kt` (`liveDeck` / `inactiveDeck` extensions), `Deck.kt`, `FxChain.kt`, `FBO.kt`, `Shader.kt`, `VisualSource*.kt`, `isf/`.

```
source (ISF generator | ExternalVideoSource) ──► Deck.cleanFBO
        ──► Deck.fxChain (3 serial ISF filter slots, ping/pong FBOs, chain wet/dry) ──► Deck output texture
Deck A out ┐
           ├─► ISF transition (Mixer.transitionFilter; progress = (crossfade+1)/2) ─► blendFBO
Deck B out ┘
blendFBO + Deck BG ─► mixer.frag (levels A/B/BG, master level) ─► masterCompositeFBO
       ─► Mixer.masterFxChain (same FxChain + renderFxChainPass) ─► blit ─► masterFBO ─► screen / recorder / streamers / broadcast
Deck PV renders like A/B/BG but is never composited (preview/audition).
```

- **Deck** (`Deck.kt`): one `VisualSource`, `cleanFBO`, one `FxChain`, view parameters (`viewZoom`, `viewRotateZ` are applied: as uniforms for generators, via `view2d.frag` for external video; no other view or feedback parameters exist), a `DeckMorphController` (randomize as a continuous morph, `MorphState`). Decks are `DeckSlot` A/B/BG/PV (`presets/DeckSlot.kt`).
- **VisualSource** hierarchy: `DynamicVisualSource` (shader + uniform binding + multipass topology) ← `ISFVisualSource` (ISF generator) and `Mandala` (a `DynamicVisualSource` with a `recipe`; `MandalaLibrary`, `MorphState`); `ExternalVideoSource` for Spout/Syphon/PipeWire feeds (`TextureReceiver`, `ExternalVideoDiscovery`, `rendering/pipewire/`). A source "master" in `VisualSourceRegistry` owns the shader; decks hold clones.
- **ISF** (`rendering/isf/`), DECISIONS §2:
  - `ISFParser` reads ISF, and wraps Shadertoy (`mainImage`) and GLSLSandbox shaders with a synthesized header; `ISFModels`, `ISFScanner`, `ISFTextureLoader` (imported textures).
  - `ISFFilter` is the runnable multipass filter for FX slots *and* transitions. It carries the per-effect `metaKnob` / `metaBindings` and sets `ModulatableParameter.metaDrivenBy`. `ISFVisualSource` is the generator form.
  - **Role is chosen by image-input count** (0 generator, 1 filter, 2+ or a `progress` input transition), and each registry (`VisualSourceRegistry` / `ISFFilterRegistry` / `ISFTransitionRegistry`) decides it for itself and takes only its own role; the browser reads those registries. `ISFScanner`'s `ISFAssetType` (image count only, so it ignores `progress`) and the `ISFLibraryRegistry` snapshot are filled at startup, but only the scan progress is read (`MenuBar`); nothing reads the roles.
  - Directories come from `ISFDirectoryManager` (`library/isf_directories.json`; `library/sources|filters|transitions` plus per-OS standard ISF folders; priority Custom > UserStandard > SystemStandard > BuiltIn) and are watched by `ISFFileWatcher`.
  - Stock generators ship in the jar (`default_sources/`, copied into `library/sources` when missing). Stock filters and transitions load from `default_filters/*.fs` and `default_transitions/*.fs` using the lists in `ISFFilterRegistry` / `ISFTransitionRegistry`.
- **Metaknob auto-binding**: `ISFAutoBindEngine` (user override by shader content hash in `library/isf_overrides/`, then the shipped default from `StockDefaults` (`resources/stock_defaults/filters|transitions/<id>.json`), then a heuristic over the INPUTS) produces `FxMetaBinding`s so every filter loads with a live knob. DECISIONS §4.
- **Audio into shaders**: `AudioTexture` keeps three float textures on fixed units at the top of the 16 GL 3.3 guarantees: a 512×2 FFT-over-waveform texture (`iChannel0`-style shaders) and two 512×1 rows, `isfWaveTextureId` / `isfFftTextureId`, for ISF `audio` / `audioFFT` inputs. `isf/ISFAudioInputs` maps each shader's audio input names to those units (`ISFParser` treats them as samplers); audio uniforms are set in `Renderer.render` / `ISFVisualSource`.
- **Time**: `utils/TimeSource` virtualizes time so offline renders are deterministic. `isf/PhaseIntegrators` integrates `LSD_PHASES` uniforms (rate x dt) and `cv/ClockAnchors` re-anchors seconds/frame-clocked LFOs on a period change, so a speed change never jumps the phase.
- **Cost**: an ISF header's optional `LSD_COST` (0-3, `ISFHeader.gpuCost`) is shown as a badge in browser rows (`PresetListPanel.rowSelectable(..., gpuCost)`). `DeckRenderBudget` (frame loop step 7) is the runtime saving.
- **Fractal Flame** (`fractal_flame`, `FlameSource`, experimental): a Kotlin-built genome (`FlameGenome`, `FlameUnits` normalised units, `FlameVariations`), a GPU point renderer with inline GLSL (`FlameEngine`, `FlameGovernor` GPU budget), a morph sampler (`FlameSampler`, `FlameProbe`/`FlameQuality` gate, `GenomePool`) and an importer (`FlameImporter`, `presets/FlameImportOps`). `FlameGenome.pack` is allocation-free (unit maps cached per name and in `XfKeys`; `FlamePackGoldenTest`). The **Garden** (`presets/Garden`, `GardenThumbnails`) keeps offspring as tagged `.lsd` presets in `library/presets/garden/`; `Garden.pool` reads a `@Volatile` snapshot that `presetIoExecutor` rebuilds (startup, after Keep, a coalesced rescan per draw). Its parameters are grouped into tabs by `StructuredEditorSource` (`rendering/StructuredEditor.kt`), drawn by `ui/StructuredEditorPanel` (choice combos disabled under a macro or Metaknob). Offline tools: `tools/FlameCurator`, `FlameStats`, `FlameVariationCompare`.

Deeper: `docs/developer/rendering.md`, `docs/developer/flame_source.md`, `docs/developer/preset_management.md`.

## 4. Deck, FX and transition changes: the Ops queues

Deck loads, FX changes and transition changes each have **one mutation path**, `object`s in `presets/`. Callers (UI, queues, MIDI, session code) `request`/post a change from any thread; Thread 0 applies it once per frame in `drainOnGlThread` (called from `Main.kt`). DECISIONS §3, §4.

| Ops | Queue | Entry points | Notes |
|---|---|---|---|
| `DeckOps` | `ConcurrentLinkedQueue<Op>`, per-slot sequence numbers; stale ops dropped | `request(slot, DeckChange, LoadOrigin, onResult)`; `DeckChange` = `Source`, `Preset(file)`, `Eject`, `CopyFrom`, `MoveFrom`, `SwapWith` | dirty guard (`LoadOrigin.MANUAL` vs `QUEUE` policies), macro-bank install/remap, `PresetManager.setActive`/`clearActive` bookkeeping; preset files read on `presetIoExecutor`. Copy/Move/Swap with FX carry an `FxSnapshot` (chain DTO, file, baseline) and post `FxOps.applyChain(..., baseline)`; a Move empties the source with `FxOps.clearChain` ("Untitled") |
| `FxOps` | `ConcurrentLinkedQueue<(Mixer) -> Unit>` | `loadChain/applyChain` (optional `baseline`)`/clearChain/revertChain/saveChain`, `setSlotFilter/stepSlot/loadSlot/applySlot/clearSlot/resetSlot/setSlotEnabled/swapSlots`, `dropAsset`, `applyItem` (queue/playlist path) | swap fade-dip (`fadeSec` ← `UITheme.fxSwapFadeMs`); after each op re-syncs that chain's knobs with `FxMacroSync.syncFor`; failures toast (`reportFailure`) |
| `TransitionOps` | same shape | `setStock`, `applyPreset`, `loadPreset`, `applyItem` | session restore at startup still calls `Mixer.setTransition` directly (before the loop) |

**Undo sinks.** `DeckOps.undoSink`, `FxOps.undoSink`, `TransitionOps.undoSink` are the same lambda, wired in `UIManager` init to `ParametersUndo.pushUndoState(parametersState, mixer, restore)`. The stack lives in `ParametersState` (depth 30, `ParametersUndoSnapshot` = modulators + optional `restore` lambda). The snapshot is taken when the queued change is about to apply; only callers passing `undoable = true` (the Edit bay) push. `DeckOps` also has injected `mixerProvider`, `prompt` (the dirty-deck popup in `PopupManager`) and `postApply`, so `presets/` imports no UI panel state (§1). Macro-bank edits are undone by `ui/MacroUndoTracker` (per-frame change hash), not by hooks.

**Queues** (all `object`s in `presets/`):
- `PlayQueueManager` (A/B Queue) and `BgQueueManager` (BG Queue) are standalone.
- `QueueEngine` is the shared base of `FxQueueEngine` (→ `FXQueueManager` = A/B FX Queue, `FXBgQueueManager` = BG FX Queue) and of `TransitionQueueManager` (Transition Queue).
- Advancement comes from UI buttons, keyboard, MIDI/controller deltas and CV triggers (`Mixer.pollQueueAdvance`, `pollBgQueueAdvance`, `pollTransQueueAdvance`). `UIManager.processInput` (MIDI, CV) and `UIManager.render` (keyboard) collect them and cap each at 8 steps per frame.
- The queues feed `DeckOps` / `FxOps.applyItem` / `TransitionOps`. `QueueNextUp` is the pure "next item" readout.
- Edits by index go through `ui/browser/QueueOps` (cursor, play now, move, remove, `insert`, `clear`, `edit`), which push one undo step each; every queue panel's Clear, browser/queue drops (`QueueDrop`), the playlist menu's play now/insert/append and the controller's queue knobs (`QueueKnobs`) use it. Plain appends (`LibraryNavigation.enqueue`, Q) call the managers directly.

Other `presets/` pieces:
- `PresetManager`: active-preset bookkeeping, dirty baselines (incl. macro bank), `presetIoExecutor`.
- `PresetRepository`: async load/save of presets, FX, transitions, playlists.
- `DeckLifecycleManager`, `PresetMigrator`, `PresetDependencyAnalyzer`, `PlaylistParser`, `FxShortlist`.
- `GeneratorDefaults`: a source's starting parameters and macro bank: user default (`library/generator_defaults/`), else `StockDefaults` (sparse shipped files `resources/stock_defaults/{sources,filters,transitions}/<id>.json`: base values, `randomizeBase` + `baseMin/baseMax` random ranges, modulators incl. audio responses), else a heuristic.
- `Garden`, `GardenThumbnails`, `FlameImportOps`: Fractal Flame offspring and imports (§3).
- `SessionSerializer` / `SessionState`: session save and restore.

## 5. FX model

Every deck (A, B, BG, PV) and Master owns one `FxChain` (`rendering/FxChain.kt`): 3 slots of `ISFFilter?`, chain `dryWet` and `enabled`, a `superKnob`, `slotSuperKnobLink[3]` (soft takeover when linking), `focusedSlot` / `focusParamPage` (Focus mode), `isDirty` (a full compare against `baselineDto`, ignoring performance positions: Super Knob, Metaknobs and what they drive; cached 250 ms), `sourceFile`, swap-dip gains. Rendered by the one `Renderer.renderFxChainPass` for decks and Master. **FX is session state, not part of a preset**: `DeckPresetDto` has no FX fields; `MixerDto` carries `masterFxChain`, `deckA/B/BG/PVFxChain` and their source paths. Saved forms: `.lsdfx` (slot), `.lsdfxchain` (chain), `.lsdfxplay` (playlist) via `models/FXPresetModels.kt`. Defaults for new chains: `Mixer.loadDefaultFxChains`, `ui/FxLinkDefaults` (Linked/Unlinked preference). DECISIONS §2, §4.

`FxMacroSync` (`macro/`) is the only writer of the FX macro banks (`deckA_fx` … `masterFx`): group mode = Super Knob + 3 slot Metaknobs, focus mode = that slot's Metaknob + 3 parameters per page, taken from `ISFFilter.focusParameters` (the slot's `knobLayout`, else authored order minus Metaknob-owned parameters). Links and knob order are edited only through `ui/FxMetaEdit`. Per-deck chain dry/wet is edited in Edit only (the `FX_SENDS` bank and its Perform row were removed); "FX1/FX2 sends" and per-row alternative chains no longer exist.

## 6. Parameters, modulation, CV

- `parameters/`: `ModulatableParameter` (base value, clamp, `modulators`, `evaluate()`, `metaDrivenBy`, history), `CvModulator` (`sourceId`, stable `id`, depth/offset, `ModulationOperator` ADD/MUL/SCALE, LFO/seq/audio settings), `ModulatorPropertyAccessor` (read/write a modulator's property by name; `:mod/<id>/<property>` paths), `ParameterResolver` (path → parameter, cached), `ParameterOwner` (`getParameterPaths`, implemented by `Deck`, `Mixer`, sources).
- `cv/`: `CVRegistry` registers `bpm`, `beatSine`, `lfo`, `seq` and the audio sources listed in `cv/AudioSignals` (the one list): levels `audio_amp|bass|mid|himid|high`, hits `audio_flux_amp|bass|mid|himid|high`, and the section sources `audio_energy`, `audio_energy_bass|mid|himid|high` and `audio_drop` (`SectionDrop`, a beat-snapped decay envelope run on Thread 0); `midi_cc_<ch>_<n>` / `midi_note_<ch>_<n>` sources are resolved dynamically (bit-packed keys). `Evaluators.kt` evaluates `beatPhase`, `sampleAndHold`, `lfo`, `seq` inline per modulator; audio values are pushed by `AudioEngine`. Audio modulators have an `invert` flag (ducking). `CVRegistry.updateAll` skips audio sources (they are written at audio-block rate) and updates only active non-audio sources.
- Evaluation happens in `deck.update()` / `mixer.update()` each frame after `MacroEngine.tick`. Formula and operator semantics: `docs/developer/modulation.md`.

## 7. Macros

`macro/`: `MacroModels.kt` (`MacroBank` → `MacroControl` (value, label, ≤N targets) → `MacroBinding` (parameter id, `PARAM_BASE_VALUE` or `MODULATOR_PROPERTY`, min/max/invert, `MacroCurveType`, `MacroLinkMode`, `modulatorId`)), `MacroCurve` (curve, inverse, travel window), `MacroEngine` (singleton; resident banks; `tick` evaluates a resolved-binding cache rebuilt when `bindingsDirty`; lock queries; mapping suspension), `FxMacroSync`, `MacroLearnState` (click-to-assign session, selection, status), `MacroBankSerializer` (bank in/out of `DeckPresetDto.macroBank`, deck-path remap, `.knobpreset.json` import/export), `MacroOscBridge` (`/macro/<bankId>/knob/<n>` in and feedback out).

Canonical bank ids (`MacroEngine.CANONICAL_BANK_IDS`): `deckA|deckB|deckBG|deckPV`, `deckA_fx|…|deckPV_fx`, `masterTransition` (`TRANS`), `master`, `masterFx` (`MASTER_FX`), `global` (`GLOBAL`, registered with 0 knobs). All other banks hold 4 knobs. FX banks and TRANS are sync-owned (`TransitionMacroSync.isSyncOwned`): `FxMacroSync` / `TransitionMacroSync` rewrite them, `MacroBankSerializer.applyFileToBank` refuses them, and the Macros tab's Apply to / Save from targets are decks A/B/BG/PV and Master only. Session persists banks in `SessionStateDto.deckMacroBanks`; a deck preset carries its own bank.

Ownership order and locking (Metaknob link > macro target > direct edit/MIDI/OSC; mappings suspended, not overwritten; `MacroEngine.tick` and `KnobDrive` (Value edits run back onto the driving knob) agree): DECISIONS §5. `ModulatorPropertyAccessor`/`:mod/<id>/` ids: DECISIONS §5. UI side: `PerformanceMacroStrip` (Edit-row target strip), `MacroBindingEditor`, `MacroKnobWidget`, `MacroUndoTracker`.

## 8. Control surfaces

```
MIDI device ─► MidiEngine (Receiver thread → capped queue)         OSC UDP ─► OscEngine.inboundQueue
     │ drained in UIManager.processInput                                 │ drained in Main loop
     ▼                                                                    ▼
MidiMappingManager.processGlobalMidiEvents                         OscMappingManager.onOscMessage
  learn target? → add mapping / profile command                      (macro addresses → MacroOscBridge)
  else: no learned mapping on this ch/CC → ControllerManager → ControllerRuntime → CommandRegistry
        (Global/... mappings → CommandRegistry) ; then parameter mappings (onMidiEvent → ModulatableParameter.baseValue / modulator property)
```

- **MIDI mappings** (`midi/`):
  - `MidiMappingManager` (object; profiles in `library/midi/*.json`): parameter, modulator and `Global/...` mappings with soft takeover, relative decoding and slew. Mappings on a macro-owned target are suspended.
  - `MidiEngine`, `MidiOutputPorts`, `MidiLearnTarget` / `MidiLearnSink` (implemented by `ParametersState`), `ProfileLearner` (implemented in `control/`).
- **OSC** (`osc/`): `OscCodec` (pure Kotlin OSC 1.0), `OscEngine` (UDP, auto-learn of the remote, sniffer), `OscMappingManager` (profiles in `library/osc/*.json`, same takeover/slew model), `OscLearnState`, `OscMapModeState` (click-any-control map mode), `OscPreferences`.
- **Controller layer** (`control/`), DECISIONS §6:
  - Profiles: declarative `ControllerProfile` JSON compiled to `CompiledController`. `ControllerProfileStore` holds built-ins (`resources/controllers/midi-fighter-twister.json`, with its Utility settings `midi-fighter-twister.mfs`, and the opt-in `midi-fighter-twister-xt.json` for the user-flashed XT firmware's native mode) and user files (`library/controllers/`, user id overrides). It and `ui/PerfPageStore` both sit on `UserJsonLibrary` / `UserJsonFiles`.
  - Runtime: `ControllerManager` (one `ControllerRuntime` + `ControllerFeedback` per matched device); `ControllerRuntime` (active bank, modifiers, acceleration, routes inputs to commands).
  - Commands: `CommandRegistry` / `Command` (`CommandKind` TRIGGER/TOGGLE/MOMENTARY/SCALAR/RELATIVE); `KnobCommands` (`knob.<n>`, `.press`, `.press_alt`); `NavCommands` (`nav.button.<n>`, `controller.bank_next/prev`, chain link); `GlobalCommands`.
  - UI-side interfaces: `KnobSurface` ← `ui/PerformSurface` (the 16 visible Perform knobs) and `NavSurface` ← `ui/NavigationSurface` (side buttons, browse cursor, dirty-prompt answers).
  - Feedback: `KnobLightSource` → `ControllerFeedback` → `CcQueue` → `MidiSink`.
  - **Native mode** (`profile.output.native`, `control/NativeMode.kt`): `ControllerFeedback.enterNativeMode` / `leaveNativeMode` by SysEx (one per config); ring style from the parameter's meter type, RGB LEDs; send knobs show ring off. `ControllerManager.shutdown()` leaves it at exit (Main, then the hook as fallback); `releaseAll` on reset/unplug returns immediately (the writer thread closes the port).
  - Special knobs: `ui/ClockKnob` (Clock row: tempo ring, beat-pulse LED, push = tap), `ui/browser/QueueKnobs` (knobs 14/15 drive the tab's queues in the split Library), `ui/ChainLoad` (Chain view load row, knobs 13-15).
- **Perform pages and banks**: Twister hardware banks name Perform pages (`perform.ab`, `perform.bgpv`, `perform.mixer`; the bank-step button wraps over them, and since the stock profiles leave `wrapPages` off, the device's spare bank 4 keeps the current page); the UI page is the source of truth and `ControllerFeedback.syncActiveBank` sends a bank switch when the UI page changes. **In the pair view** a bank change calls `KnobSurface.stepPair` (implemented by `PerformSurface`; walks `PerfRows.PAIRS`: A > B > BG > PV > Master) instead of changing page, and `UIManager` passes a null `activePageId` to the controller manager while `performSurface.pairFocused`, so `syncActiveBank` is skipped. `NavSurface.browseLiveKnobs` (8) says how many knobs stay live on the pair's rows while browsing. A controller FX tap with nothing bound applies to the live deck (`FXBrowserPanel.applyToDeckFromController`, via `DockActions`; Shift + tap queues).
- **Chain view**: the focused pair shown as SRC + FX1/FX2/FX3 (`ParametersState.chainView`, `ChainViewState` PLAY/BROWSE, `PerfRows.chainRowsFor` (cached per tag and state) with rows `deck.<tag>.fx1-3` / `master.fx1-3` plus the SRC/Transitions row or the LOAD row).
- **Touch console** (`input/`): `TouchConsoleController` (CapsLock latch; zones by normalised position: crossfader at y ≤ 0.28, a dead band to 0.45, then Level A / BG / B in thirds of the width), backends `LinuxEvdevTouchBackend`, `MacCocoaTouchBackend`, `NoOpTouchBackend` (other platforms), events through a lock-free queue drained on Thread 0. DECISIONS §10.
- **Keyboard**: `ui/shortcuts/` (`ShortcutManager`, rebindable; `~/.liquid-lsd/keybindings.json`); global hotkeys are handled in the GLFW key callback in `Main.kt`.

Deeper: `docs/developer/unified_control_mapping.md`.

## 9. Audio, beat sync, Ableton Link

- `audio/AudioEngine` (object) chooses the backend (`AudioBackendMode` AUTO / JACK_ONLY / JAVASOUND_ONLY): `JackClient` (JNAJack) or `JavaSoundClient`, with `AudioChannelRouting` for the input channels; runs `BiquadFilter` bands (bass, mid, hi-mid, high), `AmplitudeExtractor`, the hit detector (`TransientDetector` + per-band `HitScale`), and the beat chain (`BeatHopper` feeds `BeatTrackerEngine` on a fixed `fftSize` grid, so tempo doesn't depend on the buffer size; `BeatPhaseLock` places the beat, see below); publishes into `CVRegistry` (sources and history buffers) and to `AudioTexture`. `MidiJackWatchdog` handles MIDI hotplug and JACK reconnect.
- On the audio thread, allocation-free: `AudioAgc` (always-on per-band auto gain against a long-term level; Audio Response is the only user control), `SectionWatch` (breakdown/drop from the low end; freezes the AGC's long-term level in breakdowns; feeds `audio_energy` / `audio_drop`), `DownbeatDetector` (where the bar starts), `SpectrumAnalyzer` (display FFT for the shader audio texture, independent of beat tracking). `SectionWatch` and `AudioAgc` mirror `web/energy.js` / `dsp.js`, and the hit detector `web/dsp.js` (vector tests).
- **Beat phase**: `BeatTrackerEngine` says how fast, `BeatPhaseLock` where. Its comb runs on the `beat-phase-lock` thread every 250 ms; `steer`, on the audio thread, nudges the beat clock (slew for a small error, a forward jump past `JUMP_BEATS`) only when the tempo has held and two readings agree. So the beat clock has two writers on the audio thread: the tracker's anchors and the lock's corrections. Same method as `web/beattrack.js`. DECISIONS §9.
- **Clock**: `ClockSource` is `MANUAL` or `AUDIO_TRACKER` (a stored `ABLETON_LINK` value maps to `MANUAL`). Ableton Link is a separate on/off (`AbletonLinkEngine.isEnabled`): when on, `CVRegistry.updateAll` calls `updateClockAnchor`, and `LinkSyncManager.signalDamping` (`BeatTrackToLinkDamping`) conditions the audio tracker before committing tempo/phase to Link. Backends (`LinkBackend`): `NativeJniLinkBackend` → `CarabinerTcpLinkBackend` → `NoOpLinkBackend`, first that initializes. `TapTempoController` for tap tempo.
- Beat clock is three `@Volatile` anchor fields set by the audio thread (or Link/tap); `CVRegistry.getSynchronizedTotalBeats()` extrapolates on Thread 0. DECISIONS §9.

Deeper: `docs/developer/beat_sync.md`, `docs/developer/audio_dsp.md`.

## 10. UI

`ui/UIManager` builds and drives everything each frame (`render`): keyboard queue shortcuts → ImGui frame → `MenuBar` → `drawLayout` → modals → `MacroUndoTracker.update` → `ToastOverlay`. Styling: `UITheme` (settings singleton over `AppPreferences`), `UIThemeStyler`, `TangoPalette` (sole colour source for Perform UI; DECISIONS §8). Its `Role`s hold a dark value and a light value or ImGui slot, resolved with `role.u32()` against `TangoPalette.isLightTheme` (set in `UIThemeStyler.setupThemeColors`); the light-theme roles include `HOVER_OVERLAY`, `PRESS_OVERLAY`, `HEADER_TINT`, `CELL_WASH`, `HOVER_BORDER`, `TEXT_FAINT`, `TEXT_DIM`, `TEXT_OK`, `TEXT_WARN`, `TEXT_ERROR`, `QUEUE_AB_TEXT`, `QUEUE_BG_TEXT`. `ButtonChrome` draws the bevel on real buttons (tunables in its per-theme `Look`). `CvTheme` is still separate; it dims its colours by `CvTheme.LIGHT_THEME_SCALE` (0.62) in the light theme. Call sites never test the theme (`ButtonChrome`'s `Look` is the one switch). The colour literals still left in panels (`AudioEnginePanel`, `ParametersRenderer`, `MixerPanel` / `DeckControlPanel`, ...) are listed in `.planning/open-items.md`; the `TourRunner` overlays stay literal by design. Control tooltips are built lazily with `controlTooltip { }` (`ControlTooltipBuilder` in `TooltipHelper.kt`: three tiers, `midiMappingText()`); plain ones via `itemTooltip` / `showTooltip`.

**Three views plus the pair view, and a bound Perform dock** (no classic view, no Parameters/Properties panels, no modular rack with MULTI mode, no Preset Grid, no Column 3 MACROS tab):

- **Perform** (default): the `PerformanceMatrixPanel` knob rows over a half-height Library, with the Mixer column on the right. `LibraryMode` is `HALF` or `FULL`.
- **Edit**: opening the bay on a row (`LibraryPanel.isEditView` = a module expanded and Library not FULL) hides the Library; the row expands into `PerformanceDeepEditBay` (Params only).
- **Library**: `LibraryMode.FULL`.
- **Bound in place** (DECISIONS §7): clicking a deck SRC badge / FX chain or slot, a transition name, a preview monitor or the Master badge, or a controller picker/send, sets `ParametersState.dockSelection` through `openBrowse`. When the slot's row is on the current page (`pageHasRow`) and no pair or Edit module is up, the Perform view's HALF dock is bound in place (`boundInPlace`): nothing moves, the chip and ✕ sit on the Library's tab line (`LibraryPanel.drawBoundChip`, `BayHeader.drawChip`), and the binding comes from `PerformanceMatrixPanel.performDockBinding`. Esc, ✕, re-clicking the slot, a page change or Library FULL ends it (FULL remembers it, `libraryWentFull` / `restoreBoundReturn`).
- **Pair view** (DECISIONS §7): otherwise (the row is not on the page, or the request comes from Edit) `openBrowse` focuses a pair of rows (`ParametersState.focusPair(tag)`; `focusedPair` holds the tag): a deck's SRC + FX, or the Master pair's Transitions + Master FX (`PerfRows.PAIRS`, `PairDef`: a pair is the two rows that share a half of a Twister page; Master MST + Clock is Edit's pair on the MST side tab only). `PerfRows.visibleRowsForPage` swaps the pair's two rows in for the page rows, the Library is hidden, and `PerformanceDeepEditBay.drawPairBay` draws the `BayHeader` line over the Browse dock (`PerformanceBrowseBay.drawForPair`; `pairBrowseTarget` defaults the Master pair to the half Edit's OUT tab last showed, the Master FX chain first). It is exclusive with Edit (`focusPair` collapses open modules, `setDisclosure` clears the pair), is not persisted, and entering Library FULL leaves it (`leavePair`; `LibraryPanel.show` remembers it, and an open Edit module, in `ParametersState.libraryReturn` so Esc from the Library returns there). `PairKnobTouch` retargets a bound dock (pair view or in place) to the half a live-knob touch names (`openBrowse(..., retab = false)`).
- **`ViewState`** (`viewStateOf(session)`, `ui/ViewState.kt`) is the single place the active view is derived (`editing`, `maximized`, `pair`, `bound`, `dockActive`); layout, controller and macro strip read it rather than re-testing `libraryMode` / expanded modules.

Perform surface:
- **Pages**: `PerfPageStore` (built-ins `resources/perform_pages/{deck-ab,deck-bgpv,mixer}.json`, user pages `library/perform_pages/`) → `PerfPageDef` = exactly 4 `RowPlacement`s whose ids come from `PerfRows.CATALOG` (`deck.<tag>.src|fx`, `master.mix|fx`, `trans`, `global`; the Chain-view-only `deck.<tag>.fx1-3`, `master.fx1-3`, and the LOAD rows built in `PerfRows.loadRows`). The tab strip is drawn in `MenuBar` (`PerfTabStrip`); the active id is `UITheme.performancePageId`. `PerformPagesPanel` edits pages in Preferences.
- **Rows**:
  - `PerformanceMatrixPanel` orchestrates `PerformanceDeckControls`, `PerformanceMasterControls`, `PerformanceTransitionsControls`, `PerformanceClockControls` (+ `PerformanceClockTriggers`, the queue and randomize buttons), with shared state in `PerformanceUiContext`.
  - FX header and cells: `FxChainHeader`, `FxSlotCell`, `FxParamCell`, `FXChainMacroStrip`.
  - Geometry never depends on row mode (`PerfRowGeometry`, `DeckRowMetrics`, `PerfKnobSpec` / `PerfKnobResolver`). Every deck and Master row shows one half (`pinnedMode`), so a row's meaning never depends on a mode; only the Edit bay's SRC/FX and MIX/FX tabs (`ParametersState`, read via `PerformanceUiContext.isDeckBayFx` / `isMasterBayFx`) choose which half an open module shows.
- **Hardware view of the same grid**: `PerformSurface` and `NavigationSurface` (built once per `Mixer` in `UIManager`, shared by MIDI dispatch, controller feedback and the Esc key) resolve knobs with the same `PerfRows` + resolver the panel draws with.

Edit bay (`PerformanceDeepEditBay.drawRackDeepEdit`, one per open row; **Params only**: no Browse tab, no `SectionMode`). It starts with the same `BayHeader` line as the pair view (Back, Browse | Params via `ParametersState.openBrowseFromEdit`, the module chip, the "Next Up" readout from `QueueNextUp`); `preselectParam` selects a parameter when nothing is selected.
- **Parameters**: `ParametersTabs` (side rail `MST OUT A B BG PV`: MST = `LEVELS | CTRL` sections beside the Clock row, OUT = `FX | TRANS`; deck/Master parameter rows), `ParametersRenderer` (with `RowScan`, the rows drawn this frame) and `ParameterGridHeaders` (VAL / MIDI / LFO / SEQ / AUD columns); `ParamLabels` turns ISF names into display labels (LABEL, else split words).
- **Modulation** column: `PropertiesPanel` with the editors `Lfo1Section`, `Lfo2Section`, `SeqSection`, `AudioModulatorSection`, `MidiModulatorSection`, `ValueParamSection`.
- Macro target strip: `PerformanceMacroStrip` (the GLOBAL guest strip is dormant).
- State: `ParametersState` (selection, per-module disclosure, `focusedPair`, `dockSelection` / `boundInPlace`, `libraryReturn`, the once-per-bind dirty-prompt answer, undo stack, learn targets), `ParametersKeyboard`, `ParametersUndo`. Undo keys run once per frame: `PerformanceDeepEditBay.beginFrame` runs `handleDeepEditKeys` (undo only) when no open Params editor owns the keyboard, and `PerformanceMatrixPanel.handleKeysWhileHidden` while the Library is maximized.

Library (`ui/LibraryPanel`, `LibraryNavigation`, `BackNavigation`), DECISIONS §7:
- Tabs are `LibraryViewMode` PRESETS (Sources), FX, TRANS (Transitions), QUEUES (all five queues, `QueuesPane`), MAPS (Macros: Banks + Pages, `MapsBrowserPanel`; banks in `library/knobpresets/`).
- Sources / FX / Transitions use the **unified `ui/browser/BrowserPane`**: `BrowseModel`, `BrowseCatalogs` → `BrowseCatalog` (tree, list, `SearchMatcher`), `BrowseFavorites`, `FxShortlist`.
- **One dock, optional target** (DECISIONS §7): `BrowserDock` (tabs, toolbar, pane, shortcuts, popups) is drawn by `LibraryPanel` (plain, or bound in place) and by `PerformanceBrowseBay` in the pair view, where it is bound to an `ApplyTarget` (`ui/browser/ApplyTarget.kt`) while the selected tab's kind matches `ParametersState.dockSelection` (otherwise the chip greys and it is the plain Library). `DockActions` is the one tap / double-click / controller-accept path, plus `enterOnRow` (Enter on every tab) and `deleteKeyPressed` (the `library.delete_asset` shortcut); `BrowserPane.typeToSearch` skips keys `ShortcutManager.isBound`; `DockOutline` outlines the row control that is the current selection. `BrowserPane.hosted()` tells `NavigationSurface` whether the dock is bound.
- **`ScopeMemory`** (`ApplyTarget.kt`) keeps scope, search and tree cursor **per kind**, shared by the Library and every target (`ScopeMemory.contextOf`); only a whole-chain FX target has its own bucket (saved chains).
- Queues: in Library FULL, `QueueSplit` puts `QueuesPane` (the five queue panels `QueueActionsPanel`, `BgQueueActionsPanel`, `FXQueueActionsPanel`, `FXBgQueueActionsPanel`, `TransitionQueuePanel`, sharing `QueueToolbar`'s seven-slot strip and empty hint) under the browser with a divider (`UITheme.libraryQueueSplit`; Sources/FX/Transitions only; drops land at the drop position, `QueueDrop` kind guard); edits go through `QueueOps` (§4). Shared list/popup helpers: (`PresetListPanel`, whose `rowSelectable` draws every kind's row; `FXBrowserPanel`, `TransitionBrowserPanel`, `PlaylistEdit`, `BrowserPopupHandler`). `LoadedMarks` draws the per-deck chips (`gutterStyle`).
- **Library management**: `LibraryFolders` (folders inside the Saved sections, move files), `LibraryTags` + `TagPopups` (tags in the file's own `tags` list; patches the JSON object, `UserJsonFiles.writeAtomic`), `MultiSelectionModel` (Ctrl/Shift selection), `LibraryRefs` (the one place that follows a renamed/moved/deleted file into playlists, queues, favorites, recents, decks, chains, thumbnails), `FolderPopups`; delete goes to the desktop trash (`FileSystemManager.moveToTrash`; `-Dlsd.noTrash=true` in tests). Rename / Edit Preset Tags (`BrowserPopupHandler`) also patches the JSON and writes atomically.
- `ui/browser/BrowseRecents.kt`: the Recent folder (`BrowseScope.Recent`), last 20 loaded/applied items per kind in `library/{src,fx,transition}_recent.json`.
- `ui/ModalKeys.kt`: keyboard for the Library modals (autofocus, Enter confirms, Esc cancels; `ownsEscape` keeps Esc from also going back).
- The classic browser, `ShaderPickerPopup` and the playlist-editor panels are deleted (DECISIONS §12).
- Filesystem access: `FileSystemManager` (managed roots, 1 s scan cache, `ensureDefaultLibrary`).

Mixer column: `MixerPanel`, `DeckControlPanel`, `DeckMonitorGrid` (A | B over BG | PV monitors), `MixerLayout`.

Other UI:
- `PreferencesPanel` and its pages: `AudioEnginePanel`, `MidiPreferencesPanel`, `OscPreferencesPanel`, `BroadcastPreferencesPanel`, `ShaderLocationsPreferencesPanel`, `ShortcutsPreferencesPanel`, `VideoDisplayPreferencesPanel`, `TempoSyncPanel`, `PerformPagesPanel`.
- `PopupManager` (exit, dirty-deck prompts); modals `SavePresetModal`, `VideoExportModal`, `NoteEditorModal`, `UpdatePromptModal` (opened from the menu-bar update icon), `AboutModal`, `MissingItemsPanel`.
- `ToastOverlay`, `TooltipHelper`, `UiLabPanel` (`--ui-lab`), `WindowFrameController` (client-side decorations).
- Minimum window 1280×720 (`glfwSetWindowSizeLimits`).

Deeper: `docs/developer/ui.md`, `docs/archive/ui_interaction_architecture_review.md` (Oct 2026 review, archived), `docs/user_guide/your_workspace.md` (glossary).

## 11. Persistence layout

Paths are relative to the working directory (`PresetManager.LIBRARY_ROOT = File("library")`).

| What | Where | Written by |
|---|---|---|
| Session (decks, mixer incl. FX chains, queues, macro banks) | `library/last_session.json` (`SessionStateDto`, `version = 6`) | `SessionSerializer` on exit and every 60 s (written on `presetIoExecutor`) |
| Deck presets / playlists | `library/presets/**.lsd` (user folders allowed), `library/playlists/*.lsdplay` (tokens relative to `library/presets`; format in `docs/developer/preset_management.md`) | `PresetRepository`, `LibraryFolders`, `ui/browser/PlaylistTargets` / `PlaylistMenu` (Add to Playlist) |
| Garden / imported flames | `library/presets/garden/` (`<name>.lsd` + `<name>.png`; shipped `garden/curated/`), `library/presets/imported-flames/` | `Garden`, `GardenThumbnails`, `FlameImportOps` |
| FX | `library/fx/*.lsdfx`, `library/fx_chains/*.lsdfxchain`, `library/fx_playlists/*.lsdfxplay`, `library/fx_shortlist.json` | `PresetRepository`, `FxShortlist` |
| Transitions | `library/transitions/*.lsdtrans` (same folder is also an ISF scan root), `library/transition_playlists/*.lsdtransplay`, `library/transition_favorites.json`, `library/src_favorites.json`, `library/fx_saved_favorites.json` | `PresetRepository`, `BrowseFavorites` |
| Browser Recent folder | `library/src_recent.json`, `library/fx_recent.json`, `library/transition_recent.json` | `BrowseRecents` |
| ISF shaders | `library/sources`, `library/filters`, `library/transitions`, user ISF dirs; `library/isf_directories.json` | `ISFDirectoryManager` |
| Generator defaults / Metaknob overrides | `library/generator_defaults/<sourceId>.json`, `library/isf_overrides/`; shipped: `resources/stock_defaults/` (read-only) | `GeneratorDefaults`, `ISFAutoBindEngine`, `StockDefaults` |
| Controller profiles, Perform pages | `library/controllers/`, `library/perform_pages/` | `UserJsonLibrary` (atomic write) |
| MIDI / OSC mapping profiles | `library/midi/*.json`, `library/osc/*.json` | `MidiMappingManager`, `OscMappingManager` |
| Macro banks | `library/knobpresets/*.knobpreset.json` (Macros tab), or a user-chosen path | `MacroBankSerializer` |
| Deleted library files | the desktop trash (fallback: deleted) | `FileSystemManager.moveToTrash` |
| Unreadable user files | `<name>.unreadable` beside the original (`.unreadable-<stamp>` if one exists) | `AtomicFile.keepUnreadable`, which also posts a `UserNotice` |
| Preferences | `lsd-preferences.properties` in the working directory (legacy `lsd-settings.properties` read as fallback; OSC and broadcast read the same file) | `AppPreferencesStore` (tmp + move) |
| Source notes, key bindings | `~/.liquid-lsd/source-notes.json`, `~/.liquid-lsd/keybindings.json` | `NotesManager`, `ShortcutManager` |
| First-run seeding | `library/.defaults_installed` marker; resources `default_presets`, `default_playlists`, `default_fx_chains`, `default_transition_presets`, `default_transition_playlists` (manifests generated by Gradle's `prepareDefaultAssets` from `defaults/`, plus the factory presets `web/presets/web-*.lsd` and FX chains `web/fxchains/web-*.lsdfxchain`, shipped without the `web-` prefix) | `FileSystemManager.ensureDefaultLibrary` |

Every user-file write goes through `utils/AtomicFile.write` (temp file + move). Policy (no beta compatibility shims, migrate from v1.0, schema `version` fields): DECISIONS §1. Preset notes live in the `.lsd` (`presetNotes`, `paramNotes`).

## 12. Export, recording, outputs

- `export/RealtimeRecorder` (live MP4: PBO ping-pong readback on Thread 0, workers + `SpscQueue` → `FFmpegProcessPipe`, audio via `AudioEngine`), `PboReadbackPipeline`, `OfflineRenderStudio` (deterministic frame-stepped render on Thread 0 with `AccumulationBuffer` motion blur and `AudioDecoder` for the source audio), `ScreenshotCapture`. FFmpeg is an external process found on `PATH`. UI: Output menu, `VideoExportModal`.
- Live video out: `rendering/TextureStreamer` (`TextureStreamerManager`; Spout2 / Syphon / PipeWire) per `VideoOutputEndpoint` (A, B, BG, PV, Master), configured in `VideoOutputSettings`; secondary output window in `Main.kt`. All three are called through JNA with no wrapper library of ours: `SpoutApi` makes vtable calls into `SpoutLibrary.dll`, `SyphonBridge` calls `objc_msgSend` with fixed signatures inside autorelease pools, and `rendering/pipewire/` speaks the `libpipewire-0.3` ABI with SPA pods built by hand (`SpaPod`) and offset-based buffer access (`SpaBufferLayout`), shared memory only (no DMA-BUF). DECISIONS §2.
- Stopping a recording hands the mux to a `RealtimeRecorder-Finish` thread so Thread 0 doesn't wait; quitting waits for it.

Deeper: `docs/developer/export_pipeline.md`.

## 13. Web broadcast and web player

- `broadcast/BroadcastEngine` (WebSocket client on `BroadcastEngine-IO`, auto-reconnect, `tick` rate-limited by `BroadcastPreferences.targetFps` 5–60) sends `state_full` (on connect and via `notifyStateChanged`) and `state_delta` patches from `WebPresetSerializer`. `server/server.js` is the Node relay (`ws`), `web/` the standalone WebGL2 + Web Audio player. `scripts/sync_web.py` + `web/sync_manifest.json` (Gradle `checkWebSync`/`syncWeb`, `WebSyncTest`) keep desktop shaders/Kotlin math and the web copies in step. DECISIONS §10.
- The web player is also the **web TV** (https://spaz.org/tv/, `scripts/publish_web_tv.sh`; `TV_VERSION` in `web/ui.js`): an autopilot (`autopilot.js`, `pairing.js`, `settings.json`, `catalog.json`) plays the desktop's own preset/chain/transition formats from `web/presets|content|fxchains`; `evaluator.js` (modulation), `dsp.js` (AGC), `energy.js` (section watch) and `audiotex.js` (shader spectrum and audio textures) are value-checked ports (`WebEvaluatorVectorsTest`, `WebAudioAgcVectorsTest`, `WebSpectrumVectorsTest`, golden vectors in `web/tools/`), as are the hits in `dsp.js` (`TransientDetectorTest` writes `web/tools/transient_vectors.json`, `transient.test.mjs` reads it); `beattrack.js` + `beatclock.js` are the twins of `BeatPhaseLock` (same method, own tests, no shared vectors). Factory content runs the other way: `scripts/gen_web_presets.py` writes the presets and FX chains into `web/presets` and `web/fxchains`, and the desktop build ships those as its factory content (§11). Where WebGL 2 is missing or the visuals fail (or MENU › TV › VISUALS asks), `lite.js` plays the photo gallery as an `<img>` slideshow instead. `index.html?embed` (`embedmode.js`, `embed.js`) is the component the Spaz Radio Android app bundles; content updates for it ship as a signed manifest (`web_subsystem.md` §6c).

Deeper: `docs/developer/web_subsystem.md`.

## 14. Build, test, release

- Gradle Kotlin DSL (`build.gradle.kts`): Kotlin 2.3.0, JVM toolchain 17, LWJGL 3.3.3, imgui-java 1.92.7.1, JNAJack 1.4.0, JNA 5.19.1, kotlinx.serialization/coroutines. `./gradlew run | compileKotlin | test | checkWebSync | syncWeb | packageZips`. CLI (`cli/CliArgs`): `--version`, `--help`, `--smoke-test`, `--screenshot-ui`, `--screenshot-after-frames`, `--window`, `--no-audio`, `--ui-lab`, and the tour renderer's `--tour=<script>`, `--tour-out=`, `--tour-hidden`, `--tour-dump=`, `--tour-draft`, `--tour-fps=`, `--tour-scale=`, `--tour-encoder=`, `--tour-hashes`, `--tour-no-hashes` (`tour/`: `TourRunner`, `TourScript`, `PipelineDiagram`, `WidgetRegistry`; `docs/developer/tour_renderer.md`). Flame tools: `./gradlew curateFlames | flameStats | compareVariation`.
- Tests: `src/test/kotlin/llm/slop/liquidlsd/` (236 files) mirror the packages; `architecture/LayerDependencyTest` is the layering guard.
- CI/release/platforms: `.github/workflows/release.yml` (tags `v0.9.N`, own smoke matrix), `smoke-test.yml` (PRs), `build-syphon.yml` (manual, the Syphon framework asset); DECISIONS §11. Docs site: `scripts/build_website.py`, `scripts/publish_site.sh`. Self-update check: `update/` (`UpdateChecker`, `SemVer`, `AppVersion`).

## Unverified

Not confirmed in code while writing; check before relying on them:

- Beat-tracker internals beyond the component list (complex spectral difference onset function, two-state periodicity, causal DP with `logTauTable`, checked 2026-11-15): the remaining claims in `docs/developer/audio_dsp.md`.
- Modulation formula: `ModulatableParameter.evaluate` was only skimmed; this doc defers to `docs/developer/modulation.md`.
