# Liquid LSD — Architecture

A map of the code as it is. For the *why* behind a rule, follow the `DECISIONS §n` links (`DECISIONS.md`, durable decisions only); for depth on a subsystem, follow the `docs/developer/` link at the end of its section. Verified against `src/main/kotlin/llm/slop/liquidlsd/` (299 files) on 2026-10-07.

Liquid LSD is a Kotlin/JVM VJ application: OpenGL 3.3 core (LWJGL 3 + GLFW), Dear ImGui (`imgui-java`), JACK (JNAJack) with a Java Sound fallback for audio, Java Sound for MIDI. **Everything that draws is an ISF shader**: generators, FX filters and transitions. Only `blit`, `mixer` and `view2d` are hard-wired shaders (`src/main/resources/shaders/`).

**UI name vs code name.** The UI was renamed without renaming the code (DECISIONS §1). Where they differ:

| UI name | Code name |
|---|---|
| Edit (row bay, Params only) | `PerformanceDeepEditBay`, `drawRackDeepEdit`, `ParametersState.rack*` |
| Pair view (a pair of rows over Browse) | `ParametersState.focusedPair` / `focusPair`, `PerfRows.PAIRS` / `visibleRowsForPage`, `PerformanceDeepEditBay.drawPairBay`, `PerformanceBrowseBay.drawForPair`, `PairKnobTouch` |
| Browse dock | `BrowserDock`, `DockActions`, `DockOutline`, `ParametersState.dockSelection` |
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
| Audio callback (JACK process thread, or `JavaSoundClient` capture thread) | `AudioEngine.processAudio`: biquads, RMS/flux, `BeatTrackerEngine`; no allocation, no locks | `@Volatile` primitives, `CvHistoryBuffer`, `CVRegistry.updateBeatAnchor` / `alignBeatPhase` |
| MIDI receiver (Java Sound `Receiver`, `MidiEngine.MidiInputReceiver`) | decodes messages | `MidiEngine` atomic state + capped queue (`MAX_QUEUED_EVENTS = 4096`) drained on Thread 0 |
| `osc-receiver` | UDP read + `OscCodec` decode | `OscEngine.inboundQueue` (`ConcurrentLinkedQueue`) drained on Thread 0 |
| `PresetManager-IO` (`presetIoExecutor`, single daemon) | preset/playlist/FX file read and write (`PresetRepository`) | `CompletableFuture`s; results are applied by the owning `*Ops` drain, never on this thread |
| `ISF-Library-Scanner`, `ISF-FileWatcher-*`, `FileSystemScanner` | directory scans, debounced watch | `VisualSourceRegistry.pendingGlTasks` (drained on Thread 0) and `@Volatile` immutable snapshots |
| `RealtimeRecorder` video/audio workers, FFmpeg stderr reader | frame/PCM write to FFmpeg | `SpscQueue`, `ArrayBlockingQueue` |
| `BroadcastEngine-IO` | WebSocket send/reconnect | `AtomicBoolean needsFullSync`, serialization on Thread 0, send async |
| `midi-feedback-writer` (per `MidiOutputPorts` sink) | paced controller LED/ring writes | `control.CcQueue` (coalescing) |
| `LiquidLSD-EvdevTouchpadReader` | Linux touchpad read | `TouchConsoleController.eventQueue`, drained on Thread 0 |
| `LiquidLSD-UpdateChecker`, `MidiJackWatchdog`, `ExternalVideoDiscovery` poller, `CarabinerTcpClient`, `SystemAudioVolume-Worker`, PipeWire worker | daemons; never touch GL | — |

Rules (DECISIONS §1): GL objects are created/disposed only on Thread 0, so anything off-thread queues a task that Thread 0 drains. The audio callback never allocates or blocks. Hot per-frame paths (MIDI/CV routing, controller input, Perform draw) avoid allocation: paths are resolved into flat arrays when mappings change (`ResolvedMidiBinding`, `ParameterResolver.pathCache`). ImGui native buffers (`ImString`, `ImInt`, fonts) are fields, not per-frame locals. `LayerDependencyTest` forbids `midi/` and `control/` from importing `ui/`, and allow-lists the only `midi/ -> control/` references.

Deeper: `docs/developer/architecture.md` (partly stale, see report), `docs/developer/audio_dsp.md`, `.agents/skills/`.

## 2. Frame loop

`Main.kt` loop, in order, each frame:

1. `glfwPollEvents`, `touchConsoleController.processPendingEvents`.
2. If `OfflineRenderStudio.isRendering`, `step(mixer, renderer)` replaces step 3; otherwise:
3. Render phase
   1. resize the pipeline if `UITheme.renderWidth/Height` changed (`mixer.resize`);
   2. `DeckOps.drainOnGlThread`, `FxOps.drainOnGlThread`, `TransitionOps.drainOnGlThread` (§4);
   3. `VisualSourceRegistry.processPendingGlTasks`;
   4. `CVRegistry.updateAll` (Link anchor, non-audio CV sources);
   5. `MidiMappingManager.update` (slew/convergence of MIDI mappings), then drain `OscEngine.inboundQueue` into `OscMappingManager.onOscMessage` and `OscMappingManager.update`;
   6. `MacroEngine.tick` (before any deck evaluates, so macro-driven values are in place);
   7. for A, B, BG, PV: `deck.update()` then `renderer.renderDeck(deck)`;
   8. `mixer.update()` then `renderer.renderMixer(mixer)`;
   9. `TextureStreamerManager.update` for each `VideoOutputEndpoint` (Spout/Syphon/PipeWire out), `RealtimeRecorder.captureFrame`, `BroadcastEngine.tick`.
4. Blit `mixer.masterFBO` to the window (when background video or Output View is on), then `uiManager.render(...)`. **Incoming MIDI events are drained inside `UIManager.render`** (`MidiMappingManager.processGlobalMidiEvents`, controller feedback, queue advances), i.e. after the frame's render phase, so a knob turn reaches the screen one frame later.
5. `glfwSwapBuffers`; optional secondary output window (second GL context, blits `masterFBO`); frame cap from `UITheme.maxFps` by sleep (no spin).

Shutdown order: save preferences, stop broadcast/recorder/streamers, `SessionSerializer.saveSession`, stop audio/MIDI/OSC, dispose GL, `GLResourceTracker.assertNoLeaks`.

Startup: libraries and ISF registries load on Thread 0 before the loop (`VisualSourceRegistry.loadAll(async = false)`, `ISFFilterRegistry.loadAll`, `ISFTransitionRegistry.loadAll`); four decks are created with a cloned `mandala` source, `SessionSerializer.startEmpty` blanks them, then `loadSession` restores `library/last_session.json` unless startup behaviour is EMPTY (DECISIONS §1: first run is four blank decks).

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

- **Deck** (`Deck.kt`): one `VisualSource`, `cleanFBO`, one `FxChain`, view parameters (`viewZoom`, `viewRotateZ` are applied: as uniforms for generators, via `view2d.frag` for external video; the other `view*`/`fb*` parameters are legacy, see report), a `DeckMorphController` (randomize as a continuous morph, `MorphState`). Decks are `DeckSlot` A/B/BG/PV (`presets/DeckSlot.kt`).
- **VisualSource** hierarchy: `DynamicVisualSource` (shader + uniform binding + multipass topology) ← `ISFVisualSource` (ISF generator) and `Mandala` (a `DynamicVisualSource` with a `recipe`; `MandalaLibrary`, `MorphState`); `ExternalVideoSource` for Spout/Syphon/PipeWire feeds (`TextureReceiver`, `ExternalVideoDiscovery`, `rendering/pipewire/`). A source "master" in `VisualSourceRegistry` owns the shader; decks hold clones.
- **ISF** (`rendering/isf/`), DECISIONS §2:
  - `ISFParser` reads ISF, and wraps Shadertoy (`mainImage`) and GLSLSandbox shaders with a synthesized header; `ISFModels`, `ISFScanner`, `ISFTextureLoader` (imported textures).
  - `ISFFilter` is the runnable multipass filter for FX slots *and* transitions. It carries the per-effect `metaKnob` / `metaBindings` and sets `ModulatableParameter.metaDrivenBy`. `ISFVisualSource` is the generator form.
  - **Role is chosen by image-input count** (0 generator, 1 filter, 2+ or a `progress` input transition; `ISFAssetType`) and routed to `VisualSourceRegistry` / `ISFFilterRegistry` / `ISFTransitionRegistry`. `ISFLibraryRegistry.allAssets` is the combined `@Volatile` snapshot the browser reads.
  - Directories come from `ISFDirectoryManager` (`library/isf_directories.json`; `library/sources|filters|transitions` plus per-OS standard ISF folders; priority Custom > UserStandard > SystemStandard > BuiltIn) and are watched by `ISFFileWatcher`.
  - Stock generators ship in the jar (`default_sources/`, copied into `library/sources` when missing). Stock filters and transitions load from `default_filters/*.fs` and `default_transitions/*.fs` using the lists in `ISFFilterRegistry` / `ISFTransitionRegistry`.
- **Metaknob auto-binding**: `ISFAutoBindEngine` (user override by shader content hash in `library/isf_overrides/`, curated table, generic fallback) produces `FxMetaBinding`s so every filter loads with a live knob. DECISIONS §4.
- **Audio into shaders**: `AudioTexture` (512×2 FFT + waveform float texture) and uniforms set in `Renderer.render` / `ISFVisualSource`.
- **Time**: `utils/TimeSource` virtualizes time so offline renders are deterministic.

Deeper: `docs/developer/rendering.md` (partly stale), `docs/developer/preset_management.md`.

## 4. Deck, FX and transition changes: the Ops queues

Deck loads, FX changes and transition changes each have **one mutation path**, `object`s in `presets/`. Callers (UI, queues, MIDI, session code) `request`/post a change from any thread; Thread 0 applies it once per frame in `drainOnGlThread` (called from `Main.kt`). DECISIONS §3, §4.

| Ops | Queue | Entry points | Notes |
|---|---|---|---|
| `DeckOps` | `ConcurrentLinkedQueue<Op>`, per-slot sequence numbers; stale ops dropped | `request(slot, DeckChange, LoadOrigin, onResult)`; `DeckChange` = `Source`, `Preset(file)`, `Eject`, `CopyFrom`, `MoveFrom`, `SwapWith` | dirty guard (`LoadOrigin.MANUAL` vs `QUEUE` policies), macro-bank install/remap, `PresetManager.setActive`/`clearActive` bookkeeping; preset files read on `presetIoExecutor` |
| `FxOps` | `ConcurrentLinkedQueue<(Mixer) -> Unit>` | `loadChain/applyChain/clearChain/newChain/revertChain`, `setSlotFilter/stepSlot/loadSlot/applySlot/clearSlot/resetSlot/setSlotEnabled/swapSlots`, `dropAsset`, `applyItem` (queue/playlist path) | swap fade-dip (`fadeSec` ← `UITheme.fxSwapFadeMs`); after each op re-syncs that chain's knobs with `FxMacroSync.syncFor`; failures toast (`reportFailure`) |
| `TransitionOps` | same shape | `setStock`, `applyPreset`, `loadPreset`, `applyItem` | session restore at startup still calls `Mixer.setTransition` directly (before the loop) |

**Undo sinks.** `DeckOps.undoSink`, `FxOps.undoSink`, `TransitionOps.undoSink` are the same lambda, wired in `UIManager` init to `ParametersUndo.pushUndoState(parametersState, mixer, restore)`. The stack lives in `ParametersState` (depth 30, `ParametersUndoSnapshot` = modulators + optional `restore` lambda). The snapshot is taken when the queued change is about to apply; only callers passing `undoable = true` (the Edit bay) push. `DeckOps` also has injected `mixerProvider`, `prompt` (the dirty-deck popup in `PopupManager`) and `postApply`, so `presets/` imports no UI state. Macro-bank edits are undone by `ui/MacroUndoTracker` (per-frame change hash), not by hooks.

**Queues** (all `object`s in `presets/`):
- `PlayQueueManager` (A/B Queue) and `BgQueueManager` (BG Queue) are standalone.
- `QueueEngine` is the shared base of `FxQueueEngine` (→ `FXQueueManager` = A/B FX Queue, `FXBgQueueManager` = BG FX Queue) and of `TransitionQueueManager` (Transition Queue).
- Advancement comes from UI buttons, keyboard, MIDI/controller deltas and CV triggers (`Mixer.pollQueueAdvance`, `pollBgQueueAdvance`, `pollTransQueueAdvance`). `UIManager.render` collects them and caps each at 8 steps per frame.
- The queues feed `DeckOps` / `FxOps.applyItem` / `TransitionOps`. `QueueNextUp` is the pure "next item" readout.

Other `presets/` pieces:
- `PresetManager`: active-preset bookkeeping, dirty baselines (incl. macro bank), `presetIoExecutor`.
- `PresetRepository`: async load/save of presets, FX, transitions, playlists.
- `DeckLifecycleManager`, `PresetMigrator`, `PresetDependencyAnalyzer`, `PlaylistParser`, `FxShortlist`.
- `GeneratorDefaults`: 3-tier starting macro bank for a source.
- `SessionSerializer` / `SessionState`: session save and restore.

## 5. FX model

Every deck (A, B, BG, PV) and Master owns one `FxChain` (`rendering/FxChain.kt`): 3 slots of `ISFFilter?`, chain `dryWet` and `enabled`, a `superKnob`, `slotSuperKnobLink[3]` (soft takeover when linking), `focusedSlot` / `focusParamPage` (Focus mode), dirty-by-structure, swap-dip gains. Rendered by the one `Renderer.renderFxChainPass` for decks and Master. **FX is session state, not part of a preset**: `DeckPresetDto` has no FX fields; `MixerDto` carries `masterFxChain`, `deckA/B/BG/PVFxChain` and their source paths. Saved forms: `.lsdfx` (slot), `.lsdfxchain` (chain), `.lsdfxplay` (playlist) via `models/FXPresetModels.kt`. Defaults for new chains: `Mixer.loadDefaultFxChains`, `ui/FxLinkDefaults` (Auto/Linked/Unlinked preference). DECISIONS §2, §4.

`FxMacroSync` (`macro/`) is the only writer of the FX macro banks (`deckA_fx` … `masterFx`): group mode = Super Knob + 3 slot Metaknobs, focus mode = that slot's Metaknob + 3 parameters per page. Per-deck chain dry/wet is edited in Edit only (the `FX_SENDS` bank and its Perform row were removed); "FX1/FX2 sends" and per-row alternative chains no longer exist.

## 6. Parameters, modulation, CV

- `parameters/`: `ModulatableParameter` (base value, clamp, `modulators`, `evaluate()`, `metaDrivenBy`, history), `CvModulator` (`sourceId`, stable `id`, depth/offset, `ModulationOperator` ADD/MUL/SCALE, LFO/seq/audio settings), `ModulatorPropertyAccessor` (read/write a modulator's property by name; `:mod/<id>/<property>` paths), `ParameterResolver` (path → parameter, cached), `ParameterOwner` (`getParameterPaths`, implemented by `Deck`, `Mixer`, sources).
- `cv/`: `CVRegistry` registers `bpm`, `BeatSine`, `lfo`, `seq`, and the audio sources `audio_amp|bass|mid|high` and `audio_flux_amp|bass|mid|high`; `midi_cc_<ch>_<n>` / `midi_note_<ch>_<n>` sources are resolved dynamically (bit-packed keys). `Evaluators.kt` evaluates `beatPhase`, `sampleAndHold`, `lfo`, `seq` inline per modulator; audio values are pushed by `AudioEngine`. `CVRegistry.updateAll` skips audio sources (they are written at audio-block rate) and updates only active non-audio sources.
- Evaluation happens in `deck.update()` / `mixer.update()` each frame after `MacroEngine.tick`. Formula and operator semantics: `docs/developer/modulation.md`.

## 7. Macros

`macro/`: `MacroModels.kt` (`MacroBank` → `MacroControl` (value, label, ≤N targets) → `MacroBinding` (parameter id, `PARAM_BASE_VALUE` or `MODULATOR_PROPERTY`, min/max/invert, `MacroCurveType`, `MacroLinkMode`, `modulatorId`)), `MacroCurve` (curve, inverse, travel window), `MacroEngine` (singleton; resident banks; `tick` evaluates a resolved-binding cache rebuilt when `bindingsDirty`; lock queries; mapping suspension), `FxMacroSync`, `MacroLearnState` (click-to-assign session, selection, status), `MacroBankSerializer` (bank in/out of `DeckPresetDto.macroBank`, deck-path remap, `.knobpreset.json` import/export), `MacroOscBridge` (`/macro/<bankId>/knob/<n>` in and feedback out).

Canonical bank ids (`MacroEngine.CANONICAL_BANK_IDS`): `deckA|deckB|deckBG|deckPV`, `deckA_fx|…|deckPV_fx`, `masterTransition` (`TRANS`), `master`, `masterFx` (`MASTER_FX`), `global` (`GLOBAL`, registered with 0 knobs). All other banks hold 4 knobs. Session persists banks in `SessionStateDto.deckMacroBanks`; a deck preset carries its own bank.

Ownership order and locking (macro target > Metaknob link > direct edit/MIDI/OSC; mappings suspended, not overwritten): DECISIONS §5. `ModulatorPropertyAccessor`/`:mod/<id>/` ids: DECISIONS §5. UI side: `PerformanceMacroStrip` (Edit-row target strip), `MacroBindingEditor`, `MacroKnobWidget`, `MacroUndoTracker`.

## 8. Control surfaces

```
MIDI device ─► MidiEngine (Receiver thread → capped queue)         OSC UDP ─► OscEngine.inboundQueue
     │ drained in UIManager.render                                       │ drained in Main loop
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
  - Profiles: declarative `ControllerProfile` JSON compiled to `CompiledController`. `ControllerProfileStore` holds built-ins (`resources/controllers/midi-fighter-twister.json`, with its Utility settings `midi-fighter-twister.mfs`) and user files (`library/controllers/`, user id overrides). It and `ui/PerfPageStore` both sit on `UserJsonLibrary` / `UserJsonFiles`.
  - Runtime: `ControllerManager` (one `ControllerRuntime` + `ControllerFeedback` per matched device); `ControllerRuntime` (active bank, modifiers, acceleration, routes inputs to commands).
  - Commands: `CommandRegistry` / `Command` (`CommandKind` TRIGGER/TOGGLE/MOMENTARY/SCALAR/RELATIVE); `KnobCommands` (`knob.<n>`, `.press`, `.press_alt`); `NavCommands` (`nav.button.<n>`, `controller.bank_next/prev`, chain link); `GlobalCommands`.
  - UI-side interfaces: `KnobSurface` ← `ui/PerformSurface` (the 16 visible Perform knobs) and `NavSurface` ← `ui/NavigationSurface` (side buttons, browse cursor, dirty-prompt answers).
  - Feedback: `KnobLightSource` → `ControllerFeedback` → `CcQueue` → `MidiSink`.
- **Perform pages and banks**: Twister hardware banks name Perform pages (`perform.ab`, `perform.bgpv`, `perform.mixer`; the profile's `wrapPages` makes the device's spare bank 4 loop back to bank 1); the UI page is the source of truth and `ControllerFeedback.syncActiveBank` sends a bank switch when the UI page changes. **In the pair view** a bank change calls `KnobSurface.stepPair` (implemented by `PerformSurface`; walks A > B > BG > PV > Master > XF) instead of changing page, and `UIManager` passes a null `activePageId` to the controller manager while `performSurface.pairFocused`, so `syncActiveBank` is skipped. `NavSurface.browseLiveKnobs` (8) says how many knobs stay live on the pair's rows while browsing. A controller FX tap with nothing bound applies to the live deck (`FXBrowserPanel.applyToDeckFromController`, via `DockActions`; Shift + tap queues).
- **Touch console** (`input/`): `TouchConsoleController` (CapsLock latch; zones drive crossfade and levels), backends `LinuxEvdevTouchBackend`, `MacCocoaTouchBackend`, `NoOpTouchBackend` (other platforms), events through a lock-free queue drained on Thread 0. DECISIONS §10.
- **Keyboard**: `ui/shortcuts/` (`ShortcutManager`, rebindable; `~/.liquidlsd/keybindings.json`); global hotkeys are handled in the GLFW key callback in `Main.kt`.

Deeper: `docs/developer/unified_control_mapping.md`, `.planning/midi-controller-handoff.md`.

## 9. Audio, beat sync, Ableton Link

- `audio/AudioEngine` (object) chooses the backend (`AudioBackendMode` AUTO / JACK_ONLY / JAVASOUND_ONLY): `JackClient` (JNAJack) or `JavaSoundClient`; runs `BiquadFilter` bands, `AmplitudeExtractor`, spectral flux, and `BeatTrackerEngine`; publishes into `CVRegistry` (sources and history buffers) and to `AudioTexture`. `MidiJackWatchdog` handles MIDI hotplug and JACK reconnect.
- **Clock**: `ClockSource` is `MANUAL` or `AUDIO_TRACKER` (a stored `ABLETON_LINK` value maps to `MANUAL`). Ableton Link is a separate on/off (`AbletonLinkEngine.isEnabled`): when on, `CVRegistry.updateAll` calls `updateClockAnchor`, and `LinkSyncManager.signalDamping` (`BeatTrackToLinkDamping`) conditions the audio tracker before committing tempo/phase to Link. Backends (`LinkBackend`): `NativeJniLinkBackend` → `CarabinerTcpLinkBackend` → `NoOpLinkBackend`, first that initializes. `TapTempoController` for tap tempo.
- Beat clock is three `@Volatile` anchor fields set by the audio thread (or Link/tap); `CVRegistry.getSynchronizedTotalBeats()` extrapolates on Thread 0. DECISIONS §9.

Deeper: `docs/developer/beat_sync.md` (stale on `ClockSource`), `docs/developer/audio_dsp.md`.

## 10. UI

`ui/UIManager` builds and drives everything each frame (`render`): MIDI drain → queue advances → ImGui frame → `MenuBar` → `drawLayout` → modals → `MacroUndoTracker.update` → `ToastOverlay`. Styling: `UITheme` (settings singleton over `AppPreferences`), `UIThemeStyler`, `TangoPalette` (sole colour source for Perform UI; DECISIONS §8). Its `Role`s hold a dark value and a light value or ImGui slot, resolved with `role.u32()` against `TangoPalette.isLightTheme` (set in `UIThemeStyler.setupThemeColors`); the light-theme roles include `HOVER_OVERLAY`, `PRESS_OVERLAY`, `HEADER_TINT`, `CELL_WASH`, `HOVER_BORDER`, `TEXT_FAINT`, `TEXT_DIM`, `TEXT_OK`, `TEXT_WARN`, `TEXT_ERROR`, `QUEUE_AB_TEXT`, `QUEUE_BG_TEXT`. `ButtonChrome` draws the bevel on real buttons (tunables in its per-theme `Look`). `CvTheme` is still separate; it dims its colours by `CvTheme.LIGHT_THEME_SCALE` (0.62) in the light theme. The light theme has not been checked visually; remaining literals: `.planning/theme-color-audit.md`.

**Three views plus the pair view** (no classic view, no Parameters/Properties panels, no modular rack with MULTI mode, no Preset Grid, no Column 3 MACROS tab):

- **Perform** (default): the `PerformanceMatrixPanel` knob rows over a half-height Library, with the Mixer column on the right. `LibraryMode` is `HALF` or `FULL`.
- **Edit**: opening the bay on a row (`LibraryPanel.isEditView` = a module expanded and Library not FULL) hides the Library; the row expands into `PerformanceDeepEditBay` (Params only).
- **Library**: `LibraryMode.FULL`.
- **Pair view** (DECISIONS §7): clicking a deck SRC badge / FX chain or slot, a transition name, a preview monitor, or a controller picker/send focuses a pair of rows (`ParametersState.focusPair(tag)`; `focusedPair` holds the tag): a deck's SRC + FX, Master MIX + FX, or Transitions + Clock (`PerfRows.PAIRS`, `PairDef`). `PerfRows.visibleRowsForPage` swaps the pair's two rows in for the page rows, the Library is hidden, and `PerformanceDeepEditBay.drawPairBay` draws Back / Parameters over the Browse dock (`PerformanceBrowseBay.drawForPair`). It is exclusive with Edit (`focusPair` collapses open modules, `setDisclosure` clears the pair), is not persisted, and entering Library FULL leaves it (`leavePair`). `PairKnobTouch` records which half a live-knob touch bound (`openBrowse`).
- **`ViewState`** (`viewStateOf(session)`, `ui/ViewState.kt`) is the single place the active view is derived (`editing`, `maximized`, `pair`, `dockActive`); layout, controller and macro strip read it rather than re-testing `libraryMode` / expanded modules.

Perform surface:
- **Pages**: `PerfPageStore` (built-ins `resources/perform_pages/{deck-ab,deck-bgpv,mixer}.json`, user pages `library/perform_pages/`) → `PerfPageDef` = exactly 4 `RowPlacement`s whose ids come from `PerfRows.CATALOG` (`deck.<tag>.src|fx`, `master.mix|fx`, `trans`, `global`). The tab strip is drawn in `MenuBar` (`PerfTabStrip`); the active id is `UITheme.performancePageId`. `PerformPagesPanel` edits pages in Preferences.
- **Rows**:
  - `PerformanceMatrixPanel` orchestrates `PerformanceDeckControls`, `PerformanceMasterControls`, `PerformanceTransitionsControls`, `PerformanceClockControls`, with shared state in `PerformanceUiContext`.
  - FX header and cells: `FxChainHeader`, `FxSlotCell`, `FxParamCell`, `FXChainMacroStrip`.
  - Geometry never depends on row mode (`PerfRowGeometry`, `DeckRowMetrics`, `PerfKnobSpec` / `PerfKnobResolver`). Every deck and Master row shows one half (`pinnedMode`), so a row's meaning never depends on a mode; only the Edit bay's SRC/FX and MIX/FX tabs (`ParametersState`, read via `PerformanceUiContext.isDeckBayFx` / `isMasterBayFx`) choose which half an open module shows.
- **Hardware view of the same grid**: `PerformSurface` and `NavigationSurface` (constructed in `UIManager.render`) resolve knobs with the same `PerfRows` + resolver the panel draws with.

Edit bay (`PerformanceDeepEditBay.drawRackDeepEdit`, one per open row; **Params only**: no Browse tab, no `SectionMode`). The tab row carries the SRC/FX or MIX/FX half tabs and the "Next Up" readout (`drawQueueNextUp`, `QueueNextUp`).
- **Parameters**: `ParametersTabs` (side rail, deck/Master parameter rows), `ParametersRenderer` and `ParameterGridHeaders` (VAL / MIDI / LFO / SEQ / AUD columns).
- **Modulation** column: `PropertiesPanel` with the editors `Lfo1Section`, `Lfo2Section`, `SeqSection`, `AudioModulatorSection`, `MidiModulatorSection`, `ValueParamSection`.
- Macro target strip: `PerformanceMacroStrip` (the GLOBAL guest strip is dormant).
- State: `ParametersState` (selection, per-module disclosure, `focusedPair`, `dockSelection`, undo stack, learn targets), `ParametersKeyboard`, `ParametersUndo`. The pair view runs `handleDeepEditKeys` (undo only).

Library (`ui/LibraryPanel`, `LibraryNavigation`, `BackNavigation`), DECISIONS §7:
- Tabs are `LibraryViewMode` PRESETS (Sources), FX, TRANS (Transitions), MAPS (Macros: Banks + Pages, `MapsBrowserPanel`).
- Sources / FX / Transitions use the **unified `ui/browser/BrowserPane`**: `BrowseModel`, `BrowseCatalogs` → `BrowseCatalog` (tree, list, `SearchMatcher`), `BrowseFavorites`, `FxShortlist`.
- **One dock, optional target** (DECISIONS §7): `BrowserDock` (tabs, toolbar, pane, shortcuts, popups) is drawn by `LibraryPanel` (plain) and by `PerformanceBrowseBay` in the pair view, where it is bound to an `ApplyTarget` (`ui/browser/ApplyTarget.kt`) while the selected tab's kind matches `ParametersState.dockSelection` (otherwise the chip greys and it is the plain Library). `DockActions` is the one tap / double-click / controller-accept path; `DockOutline` outlines the row control that is the current selection. `BrowserPane.hosted()` tells `NavigationSurface` whether the dock is bound.
- **`ScopeMemory`** (`ApplyTarget.kt`) keeps scope, search and tree cursor **per kind**, shared by the Library and every target (`ScopeMemory.contextOf`); only a whole-chain FX target has its own bucket (saved chains).
- Beside it: the queue panels (`QueueActionsPanel`, `BgQueueActionsPanel`, `FXQueueActionsPanel`, `FXBgQueueActionsPanel`, `TransitionQueuePanel`) and shared list/popup helpers (`PresetListPanel`, `FXBrowserPanel`, `TransitionBrowserPanel`, `PlaylistEdit`, `BrowserPopupHandler`).
- The classic browser, `ShaderPickerPopup` and the playlist-editor panels are deleted (DECISIONS §12).
- Filesystem access: `FileSystemManager` (managed roots, 1 s scan cache, `ensureDefaultLibrary`).

Mixer column: `MixerPanel`, `DeckControlPanel`, `DeckMonitorGrid` (A | B over BG | PV monitors), `MixerLayout`.

Other UI:
- `PreferencesPanel` and its pages: `AudioEnginePanel`, `MidiPreferencesPanel`, `OscPreferencesPanel`, `BroadcastPreferencesPanel`, `ShaderLocationsPreferencesPanel`, `ShortcutsPreferencesPanel`, `VideoDisplayPreferencesPanel`, `TempoSyncPanel`, `PerformPagesPanel`.
- `PopupManager` (exit, dirty-deck prompts); modals `SavePresetModal`, `VideoExportModal`, `NoteEditorModal`, `UpdatePromptModal`, `AboutModal`, `MissingItemsPanel`.
- `ToastOverlay`, `TooltipHelper`, `UiLabPanel` (`--ui-lab`), `WindowFrameController` (client-side decorations).
- Minimum window 1280×720 (`glfwSetWindowSizeLimits`).

Deeper: `docs/developer/ui.md` (partly stale), `docs/developer/ui_interaction_architecture_review.md`, `docs/user_guide/your_workspace.md` (glossary).

## 11. Persistence layout

Paths are relative to the working directory (`PresetManager.LIBRARY_ROOT = File("library")`).

| What | Where | Written by |
|---|---|---|
| Session (decks, mixer incl. FX chains, queues, macro banks) | `library/last_session.json` (`SessionStateDto`, `version = 6`) | `SessionSerializer` on exit |
| Deck presets / playlists | `library/presets/*.lsd`, `library/playlists/*.lsdplay` | `PresetRepository` |
| FX | `library/fx/*.lsdfx`, `library/fx_chains/*.lsdfxchain`, `library/fx_playlists/*.lsdfxplay`, `library/fx_shortlist.json` | `PresetRepository`, `FxShortlist` |
| Transitions | `library/transitions/*.lsdtrans` (same folder is also an ISF scan root), `library/transition_playlists/*.lsdtransplay`, `library/transition_favorites.json`, `library/src_favorites.json` | `PresetRepository`, `BrowseFavorites` |
| ISF shaders | `library/sources`, `library/filters`, `library/transitions`, user ISF dirs; `library/isf_directories.json` | `ISFDirectoryManager` |
| Generator defaults / Metaknob overrides | `library/generator_defaults/<sourceId>.json`, `library/isf_overrides/` | `GeneratorDefaults`, `ISFAutoBindEngine` |
| Controller profiles, Perform pages | `library/controllers/`, `library/perform_pages/` | `UserJsonLibrary` (atomic write) |
| MIDI / OSC mapping profiles | `library/midi/*.json`, `library/osc/*.json` | `MidiMappingManager`, `OscMappingManager` |
| Macro bank export | `*.knobpreset.json` (user-chosen path) | `MacroBankSerializer` |
| Preferences | `lsd-preferences.properties` in the working directory (legacy `lsd-settings.properties` read as fallback; OSC and broadcast read the same file) | `AppPreferencesStore` (tmp + move) |
| Source notes, key bindings | `~/.liquid-lsd/source-notes.json`, `~/.liquidlsd/keybindings.json` | `NotesManager`, `ShortcutManager` |
| First-run seeding | `library/.defaults_installed` marker; resources `default_presets`, `default_playlists`, `default_fx_chains`, `default_transitions`, `default_transition_playlists` (manifests generated by Gradle from `defaults/`) | `FileSystemManager.ensureDefaultLibrary` |

Policy (no beta compatibility shims, migrate from v1.0, schema `version` fields): DECISIONS §1. Preset notes live in the `.lsd` (`presetNotes`, `paramNotes`).

## 12. Export, recording, outputs

- `export/RealtimeRecorder` (live MP4: PBO ping-pong readback on Thread 0, workers + `SpscQueue` → `FFmpegProcessPipe`, audio via `AudioEngine`), `PboReadbackPipeline`, `OfflineRenderStudio` (deterministic frame-stepped render on Thread 0 with `AccumulationBuffer` motion blur and `AudioDecoder` for the source audio), `ScreenshotCapture`. FFmpeg is an external process found on `PATH`. UI: Output menu, `VideoExportModal`.
- Live video out: `rendering/TextureStreamer` (`TextureStreamerManager`; Spout2 / Syphon / PipeWire) per `VideoOutputEndpoint` (A, B, BG, PV, Master), configured in `VideoOutputSettings`; secondary output window in `Main.kt`.

Deeper: `docs/developer/export_pipeline.md`.

## 13. Web broadcast and web player

- `broadcast/BroadcastEngine` (WebSocket client on `BroadcastEngine-IO`, auto-reconnect, `tick` rate-limited by `BroadcastPreferences.targetFps` 5–60) sends `state_full` (on connect and via `notifyStateChanged`) and `state_delta` patches from `WebPresetSerializer`. `server/server.js` is the Node relay (`ws`), `web/` the standalone WebGL2 + Web Audio player. `scripts/sync_web.py` + `web/sync_manifest.json` (Gradle `checkWebSync`/`syncWeb`, `WebSyncTest`) keep desktop shaders/Kotlin math and the web copies in step. DECISIONS §10.

Deeper: `docs/developer/web_subsystem.md`.

## 14. Build, test, release

- Gradle Kotlin DSL (`build.gradle.kts`): Kotlin 2.3.0, JVM toolchain 17, LWJGL 3.3.3, imgui-java 1.92.7.1, JNAJack 1.4.0, JNA 5.19.1, kotlinx.serialization/coroutines. `./gradlew run | compileKotlin | test | checkWebSync | syncWeb | packageZips | buildWebsite`. CLI (`cli/CliArgs`): `--version`, `--help`, `--smoke-test`, `--screenshot-ui`, `--screenshot-after-frames`, `--window`, `--no-audio`, `--ui-lab`.
- Tests: `src/test/kotlin/llm/slop/liquidlsd/` (144 files) mirror the packages; `architecture/LayerDependencyTest` is the layering guard. See `.planning/codebase/TESTING.md`.
- CI/release/platforms: `.github/workflows/release.yml`, `smoke-test.yml`; DECISIONS §11. Docs site: `tools/SiteGenerator` (`buildWebsite`). Self-update check: `update/` (`UpdateChecker`, `SemVer`, `AppVersion`).

## Unverified

Not confirmed in code while writing; check before relying on them:

- The README / docs and CI still list Linux ARM64 while DECISIONS §11 says it was dropped (see report); this doc deliberately lists no platform set.
- That `ISFFilterRegistry.bundledFilters` and `ISFTransitionRegistry.bundledTransitions` match the files in `default_filters/` and `default_transitions/` exactly (only the loading mechanism was read).
- The exact encoder/LED protocol and per-bank CC layout of the Twister profiles (taken from DECISIONS §6 and the profile description, not re-derived).
- Touch console zone geometry and behaviour (old text dropped; not re-read).
- Exact beat-tracker algorithm details and `docs/developer/audio_dsp.md` claims.
- Modulation formula: `ModulatableParameter.evaluate` was only skimmed; this doc defers to `docs/developer/modulation.md`.
