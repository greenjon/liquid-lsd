# Codebase Structure

**Analysis Date:** 2026-10-03 (rewritten against the real tree; the 2026-07-07 version predated the Patch->Preset rename, the `control/` package, the ISF pipeline and the Perform view)

## Directory Layout

```text
liquid-lsd/
├── .agents/                 # AGENTS.md, PROJECT.md, skills/ (imgui_memory_management, jack_callback_safety, lwjgl_thread_restriction)
├── .github/workflows/       # release.yml, smoke-test.yml
├── .planning/               # Planning notes; codebase/ holds these maps (STRUCTURE, CONCERNS, STACK, ...)
├── build.gradle.kts         # Gradle build, dependencies, packaging tasks
├── settings.gradle.kt       # Gradle root project name
├── ARCHITECTURE.md          # Root architecture overview (File Map, controller input architecture)
├── DECISIONS.md, ROADMAP.md, README.md, RELEASE_NOTES.md
├── docs/                    # MkDocs source: developer/ (architecture.md, ui.md, rendering.md, ...) and user_guide/
├── library/                 # Runtime user data: presets, playlists, fx, fx_chains, fx_playlists,
│                            #   transitions, transition_playlists, sources, filters, generator_defaults, isf_overrides,
│                            #   midi, osc, controllers (user controller profiles), perform_pages (user Perform pages),
│                            #   fx_shortlist.json, last_session.json
├── defaults/                # Bundled default fx_chains / playlists / presets
├── scripts/                 # install_desktop.sh, sync_web.py
├── web/, website/, greenjon/, server/   # WebGL2 player, site sources and generated site, broadcast relay
├── src/main/kotlin/llm/slop/liquidlsd/  # Application source (271 files, see below)
├── src/main/resources/      # controllers/, perform_pages/, default_filters/, default_sources/, default_transitions/,
│                            #   shaders/, fonts/, icons/, presets/default.json, natives/, docs/, logback.xml, version.txt
└── src/test/kotlin/llm/slop/liquidlsd/  # Tests, package-mirrored
```

## Source Packages

Packages are listed with every file; for one-line descriptions of each file see the File Map in `ARCHITECTURE.md`. Dependency direction is meant to be `ui -> {control, presets, rendering, macro, ...}`; known exceptions (`midi/` importing `ui/`, etc.) are recorded in `CONCERNS.md`.

**`src/main/kotlin/llm/slop/liquidlsd/`**:
- Purpose: App entry point and session facade.
- Files: `Main.kt`, `SessionContext.kt`

**`src/main/kotlin/llm/slop/liquidlsd/audio/`**:
- Purpose: Audio capture (JACK / Java Sound), DSP, beat tracking, tap tempo, system volume, JACK watchdog.
- Files: `AmplitudeExtractor.kt`, `AudioChannelRouting.kt`, `AudioEngine.kt`, `AudioInputDevice.kt`, `BeatTrackerEngine.kt`, `BiquadFilter.kt`, `ClockSource.kt`, `JackClient.kt`, `JavaSoundClient.kt`, `MidiJackWatchdog.kt`, `SystemAudioVolume.kt`, `TapTempoController.kt`

**`src/main/kotlin/llm/slop/liquidlsd/broadcast/`**:
- Purpose: Web broadcast relay client and web-preset serialization.
- Files: `BroadcastEngine.kt`, `BroadcastPreferences.kt`, `WebPresetSerializer.kt`

**`src/main/kotlin/llm/slop/liquidlsd/cli/`**:
- Purpose: Startup CLI argument parsing.
- Files: `CliArgs.kt`

**`src/main/kotlin/llm/slop/liquidlsd/control/`**:
- Purpose: Controller input: profiles, command registry, runtime, knob/nav commands and surfaces, LED feedback, user-JSON helper. See ARCHITECTURE.md "Controller input architecture".
- Files: `CcQueue.kt`, `Command.kt`, `ControllerFeedback.kt`, `ControllerManager.kt`, `ControllerProfile.kt`, `ControllerProfileStore.kt`, `ControllerRuntime.kt`, `GlobalCommands.kt`, `KnobCommands.kt`, `KnobLight.kt`, `KnobSurface.kt`, `MidiSink.kt`, `NavCommands.kt`, `NavSurface.kt`, `ProfileBindingEdit.kt`, `TracingSink.kt`, `UserJsonFiles.kt`

**`src/main/kotlin/llm/slop/liquidlsd/cv/`**:
- Purpose: CV source registry, LFO / sample-and-hold sources, beat clock, evaluators, history buffers.
- Files: `BeatClock.kt`, `CVRegistry.kt`, `CVSource.kt`, `CvHistoryBuffer.kt`, `Evaluators.kt`, `GenCVSource.kt`, `LFO.kt`, `SampleAndHold.kt`

**`src/main/kotlin/llm/slop/liquidlsd/export/`**:
- Purpose: Offline/realtime video + audio export, screenshots, FFmpeg pipe.
- Files: `AccumulationBuffer.kt`, `AudioDecoder.kt`, `FFmpegProcessPipe.kt`, `OfflineRenderStudio.kt`, `PboReadbackPipeline.kt`, `RealtimeRecorder.kt`, `ScreenshotCapture.kt`

**`src/main/kotlin/llm/slop/liquidlsd/input/`**:
- Purpose: Touchpad performance console and native touch backends.
- Files: `LinuxEvdevTouchBackend.kt`, `MacCocoaTouchBackend.kt`, `NoOpTouchBackend.kt`, `TouchConsoleController.kt`, `TouchConsoleEvent.kt`, `TouchStripBackend.kt`

**`src/main/kotlin/llm/slop/liquidlsd/link/`**:
- Purpose: Ableton Link engine and backends.
- Files: `AbletonLinkEngine.kt`, `BeatTrackToLinkDamping.kt`, `CarabinerTcpLinkBackend.kt`, `LinkBackend.kt`, `LinkSyncManager.kt`, `NativeJniLinkBackend.kt`, `NoOpLinkBackend.kt`

**`src/main/kotlin/llm/slop/liquidlsd/macro/`**:
- Purpose: Macro banks, curves, learn mode, FX macro sync, OSC bridge, bank serialization.
- Files: `FxMacroSync.kt`, `MacroBankSerializer.kt`, `MacroCurve.kt`, `MacroEngine.kt`, `MacroLearnState.kt`, `MacroModels.kt`, `MacroOscBridge.kt`

**`src/main/kotlin/llm/slop/liquidlsd/midi/`**:
- Purpose: MIDI input engine (capped queue), legacy mapping manager, MIDI output ports for controller feedback.
- Files: `MidiEngine.kt`, `MidiMappingManager.kt`, `MidiOutputPorts.kt`

**`src/main/kotlin/llm/slop/liquidlsd/models/`**:
- Purpose: Preset / FX preset / generator-default DTOs and clipboard.
- Files: `ClipboardManager.kt`, `FXPresetModels.kt`, `GeneratorDefaultModels.kt`, `PresetModels.kt`

**`src/main/kotlin/llm/slop/liquidlsd/notes/`**:
- Purpose: 3-tier notes persistence.
- Files: `NotesManager.kt`

**`src/main/kotlin/llm/slop/liquidlsd/osc/`**:
- Purpose: OSC codec, engine, learn / map-mode state, mapping manager, preferences.
- Files: `OscCodec.kt`, `OscEngine.kt`, `OscLearnState.kt`, `OscMapModeState.kt`, `OscMappingManager.kt`, `OscPreferences.kt`

**`src/main/kotlin/llm/slop/liquidlsd/parameters/`**:
- Purpose: ModulatableParameter, CvModulator, enums, resolver, waveform math.
- Files: `CvModulator.kt`, `Enums.kt`, `ModulatableParameter.kt`, `ModulatorPropertyAccessor.kt`, `ParameterOwner.kt`, `ParameterResolver.kt`, `WaveformMath.kt`

**`src/main/kotlin/llm/slop/liquidlsd/presets/`**:
- Purpose: Preset/session persistence, queues (play, BG, FX, transition), playlist parsing, FX operations and shortlist, generator defaults.
- Files: `BgQueueManager.kt`, `DeckLifecycleManager.kt`, `FXBgQueueManager.kt`, `FXQueueManager.kt`, `FxOps.kt`, `FxQueueEngine.kt`, `FxShortlist.kt`, `GeneratorDefaults.kt`, `PlayQueueManager.kt`, `PlaylistParser.kt`, `PresetDependencyAnalyzer.kt`, `PresetIOStatus.kt`, `PresetManager.kt`, `PresetMigrator.kt`, `PresetRepository.kt`, `QueueEngine.kt`, `SessionSerializer.kt`, `SessionState.kt`, `TransitionQueueManager.kt`

**`src/main/kotlin/llm/slop/liquidlsd/rendering/`**:
- Purpose: Decks, mixer, FX chain, renderer, FBO/shader/GL helpers, visual sources, external video, mandala.
- Files: `AudioTexture.kt`, `Deck.kt`, `DynamicVisualSource.kt`, `ExternalVideoDiscovery.kt`, `ExternalVideoSource.kt`, `FBO.kt`, `FxChain.kt`, `GLDebug.kt`, `GLResourceTracker.kt`, `Geometry.kt`, `Mandala.kt`, `MandalaLibrary.kt`, `Mixer.kt`, `MorphState.kt`, `Renderer.kt`, `Shader.kt`, `SourceDocRegistry.kt`, `TextureReceiver.kt`, `TextureStreamer.kt`, `VideoOutputSettings.kt`, `ViewportHelper.kt`, `VisualEffect.kt`, `VisualSource.kt`, `VisualSourceRegistry.kt`

**`src/main/kotlin/llm/slop/liquidlsd/rendering/isf/`**:
- Purpose: ISF/Shadertoy/GLSLSandbox parsing, filters, registries, directory management, auto-bind engine.
- Files: `FxMetaBinding.kt`, `ISFAutoBindEngine.kt`, `ISFDirectoryManager.kt`, `ISFDirectoryModels.kt`, `ISFFileWatcher.kt`, `ISFFilter.kt`, `ISFFilterRegistry.kt`, `ISFLibraryRegistry.kt`, `ISFModels.kt`, `ISFParser.kt`, `ISFScanner.kt`, `ISFTextureLoader.kt`, `ISFTransitionRegistry.kt`, `ISFVisualSource.kt`

**`src/main/kotlin/llm/slop/liquidlsd/rendering/pipewire/`**:
- Purpose: PipeWire JNA bridge (Linux video streams).
- Files: `PipeWireBridge.kt`, `PipeWireLibrary.kt`

**`src/main/kotlin/llm/slop/liquidlsd/tools/`**:
- Purpose: Site generator (docs HTML / ZIP).
- Files: `SiteGenerator.kt`

**`src/main/kotlin/llm/slop/liquidlsd/ui/`**:
- Purpose: ImGui panels, widgets, Perform view (matrix, pages, geometry, knob specs), navigation surfaces, themes (UITheme, TangoPalette, CvTheme), preferences.
- Files: `AboutModal.kt`, `AppPreferences.kt`, `AppPreferencesStore.kt`, `AssetType.kt`, `AudioEnginePanel.kt`, `AudioModulatorSection.kt`, `BackNavigation.kt`, `BeatDivisionSlider.kt`, `BroadcastPreferencesPanel.kt`, `Column3HeaderToggle.kt`, `CustomIconButton.kt`, `CustomRangeSlider.kt`, `CvModulatorSliderHelpers.kt`, `CvTheme.kt`, `DeckControlPanel.kt`, `DeckMonitorGrid.kt`, `DeckPresetController.kt`, `DeckSourcePicker.kt`, `DocManager.kt`, `DropdownStyleHelper.kt`, `FXChainMacroStrip.kt`, `FileSystemManager.kt`, `FxChainHeader.kt`, `FxMacroSummary.kt`, `FxParamCell.kt`, `FxSlotCell.kt`, `GridMetrics.kt`, `Icons.kt`, `ImGuiFileBrowser.kt`, `Lfo1Section.kt`, `Lfo2Section.kt`, `LibraryNavigation.kt`, `LibraryPanel.kt`, `LinkModeButton.kt`, `MacroBindingInspector.kt`, `MacroKnobWidget.kt`, `MacroPanel.kt`, `MenuBar.kt`, `MidiModulatorSection.kt`, `MidiPreferencesPanel.kt`, `MissingItemsPanel.kt`, `MixerLayout.kt`, `MixerPanel.kt`, `ModulatorHeaderRow.kt`, `NavigationSurface.kt`, `NoteEditorModal.kt`, `OscLearnStatusOverlay.kt`, `OscPreferencesPanel.kt`, `OscilloscopeDrawer.kt`, `ParameterGridHeaders.kt`, `ParametersKeyboard.kt`, `ParametersRenderer.kt`, `ParametersState.kt`, `ParametersTabs.kt`, `ParametersUndo.kt`, `PerfKnobSpec.kt`, `PerfPageStore.kt`, `PerfRowGeometry.kt`, `PerfRows.kt`, `PerformPagesPanel.kt`, `PerformSurface.kt`, `PerformanceBrowseBay.kt`, `PerformanceClockControls.kt`, `PerformanceDeckControls.kt`, `PerformanceDeepEditBay.kt`, `PerformanceFxSendsControls.kt`, `PerformanceMasterControls.kt`, `PerformanceMatrixPanel.kt`, `PerformanceStats.kt`, `PerformanceTransitionsControls.kt`, `PerformanceUiContext.kt`, `PlaylistManager.kt`, `PopupManager.kt`, `PreferencesPanel.kt`, `PropertiesPanel.kt`, `SavePresetModal.kt`, `SeqSection.kt`, `ShaderLocationsPreferencesPanel.kt`, `ShaderPickerPopup.kt`, `ShortcutsPreferencesPanel.kt`, `SplitterManager.kt`, `TangoPalette.kt`, `TempoSyncPanel.kt`, `TextFit.kt`, `TooltipHelper.kt`, `UIManager.kt`, `UITheme.kt`, `UIThemeStyler.kt`, `UiLabPanel.kt`, `UpdatePromptModal.kt`, `ValueParamSection.kt`, `VideoDisplayPreferencesPanel.kt`, `VideoExportModal.kt`, `WindowFrameController.kt`

**`src/main/kotlin/llm/slop/liquidlsd/ui/browser/`**:
- Purpose: Library sub-panels (preset / FX / transition lists, playlist editors, queue actions, shared popups).
- Files: `BgQueueActionsPanel.kt`, `BrowserActionToolbar.kt`, `BrowserDeckButtons.kt`, `BrowserPopupHandler.kt`, `BrowserRowMoreButton.kt`, `FXBgQueueActionsPanel.kt`, `FXBrowserPanel.kt`, `FXPlaylistEditorPanel.kt`, `FXQueueActionsPanel.kt`, `MultiSelectionModel.kt`, `PlaylistEditorPanel.kt`, `PresetListPanel.kt`, `QueueActionsPanel.kt`, `TransitionBrowserPanel.kt`, `TransitionPlaylistEditorPanel.kt`, `TransitionQueuePanel.kt`

**`src/main/kotlin/llm/slop/liquidlsd/ui/rack/`**:
- Purpose: Modular Rack disclosure helper.
- Files: `RackUnit.kt`

**`src/main/kotlin/llm/slop/liquidlsd/ui/shortcuts/`**:
- Purpose: Rebindable keyboard shortcuts.
- Files: `KeyCombination.kt`, `ShortcutAction.kt`, `ShortcutManager.kt`

**`src/main/kotlin/llm/slop/liquidlsd/update/`**:
- Purpose: Version resolution, SemVer, update checker.
- Files: `AppVersion.kt`, `SemVer.kt`, `UpdateChecker.kt`

**`src/main/kotlin/llm/slop/liquidlsd/utils/`**:
- Purpose: Native library loader, time source and utilities.
- Files: `NativeLibraryLoader.kt`, `TimeSource.kt`, `TimeUtils.kt`

## Resource And Runtime Data

- `src/main/resources/controllers/midi-fighter-twister.json`: built-in controller profile. User profiles go in `library/controllers/` (same id overrides).
- `src/main/resources/perform_pages/`: built-in Perform pages `decks.json`, `deck-ab.json`, `deck-bgpv.json`, `mixer.json`, `master.json`. User pages go in `library/perform_pages/`.
- `src/main/resources/default_filters|default_sources|default_transitions/`: bundled ISF shaders (`.fs`), role by image-input count.
- `src/main/resources/shaders/`: built-in GLSL (`blit`, `mixer.frag`, `view2d.frag`, legacy/test shaders).
- `src/main/resources/fonts/`: Inter, JetBrains Mono, Lucide icon font.
- `src/main/resources/presets/default.json`: bundled default preset.

## Where To Add New Code

- **Render pass / GL primitive**: `rendering/` (keep GL on Thread 0, explicit `dispose()`); built-in shaders in `src/main/resources/shaders/`.
- **Visual source / filter / transition**: drop an ISF shader into a registered ISF directory (role auto-detected); built-in Kotlin sources implement `VisualSource` in `rendering/`.
- **UI panel**: `ui/<Feature>Panel.kt`, wired from `UIManager.kt`/`MenuBar.kt`; app-level settings in `UITheme.kt`/`AppPreferences.kt`; colors from `TangoPalette.kt` only. Render thread only.
- **Perform row / page**: add a row to `PerfRows.CATALOG` (`ui/PerfRows.kt`); geometry in `PerfRowGeometry.kt`, knob content in `PerfKnobSpec.kt`; a page is a JSON file (`PerfPageStore`).
- **Controller command or device**: commands in `control/` (register on `CommandRegistry`), device profile JSON in `resources/controllers/`; UI-facing behaviour goes behind `KnobSurface`/`NavSurface`.
- **Preset field**: DTOs and converters in `models/PresetModels.kt`, migration in `presets/PresetMigrator.kt`, orchestration in `presets/PresetManager.kt`; keep DTOs backward compatible.
- **CV source / modulator behaviour**: `cv/` (register in `CVRegistry.kt`), `parameters/`, `cv/Evaluators.kt`.
- **Audio DSP**: helpers in `audio/`, integrated through `AudioEngine.kt`; pre-allocate, no allocation/logging/IO in the callback.
- **Queue / playlist behaviour**: `presets/QueueEngine.kt` (FX / transition) or `PlayQueueManager.kt` / `BgQueueManager.kt`; UI in `ui/browser/`.
- **Tests**: mirror the production package under `src/test/kotlin/llm/slop/liquidlsd/`, `*Test.kt`.
- **Docs**: Markdown under `docs/`, nav in `mkdocs.yml`.

## Naming Conventions

- PascalCase files matching the main type; `*Panel.kt` for major UI areas, `*Section.kt` for modulator editor sections, `*Test.kt` for tests.
- Presets are `.lsd`, FX slots `.lsdfx`, FX chains `.lsdfxchain`, playlists `.lsdplay` / `.lsdfxplay` / `.lsdtransplay`, transition presets `.lsdtrans`.
- Terminology: "preset" (formerly "patch"); the `patches/` package no longer exists.

## Special Directories

- `.agents/skills/`: project safety/convention instructions for agents (committed).
- `.planning/codebase/`: codebase maps (committed).
- `library/`: user data; runtime-written by managers (`last_session.json`, saved presets).
- `src/main/resources/docs/`: built documentation for in-app help (generated).
- `build/`, `.gradle/`: build output and cache (not committed).

---

*Structure analysis: 2026-10-03*
