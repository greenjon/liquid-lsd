# Lane 3: Developer docs (audit 2026-11-15)

Baseline `audit-2026-10-29` (fa4335e). Every `docs/developer/*.md` page was read end to end (except `audit.md` and
`web_subsystem.md`, which lane 6 owns) against `src/main/kotlin/llm/slop/liquidlsd/`. Backticked identifiers were grepped
in main code (the bundled `src/main/resources/docs/` HTML excluded, so it could not mask a missing name). Paths below
are short: `S/` = `src/main/kotlin/llm/slop/liquidlsd/`.

## Checklist coverage

| Page | Result |
|---|---|
| architecture.md | L3-14, L3-15, L3-16 |
| audio_dsp.md | L3-17 (otherwise clean: filter cutoffs, AGC SCALE, silence gate, 16384 buffers, watchdog, 150 ms cooldown, enums all match) |
| beat_sync.md | L3-16, L3-18 (identifiers, tests and constants checked clean) |
| build_arm64_linux.md | L3-32 |
| clean_room_provenance.md | L3-34 (ledger content is lane 7's; only the internal id contradiction noted) |
| cli_and_screenshot_automation.md | L3-30 |
| export_pipeline.md | L3-28 |
| flame_source.md | L3-33 (all class, test and constant names exist; 158 curated flames confirmed) |
| imgui_upgrade_guide.md | clean (1.92.7.1 matches `build.gradle.kts:29`) |
| modulation.md | L3-14, L3-19, L3-20, L3-21, L3-22 (StockDefaultsTest names other than the chain one exist; operators, ClockAnchors, crossfade takeover, morph controller clean) |
| ops_tuning.md | L3-29 |
| preset_management.md | L3-9, L3-23, L3-24, L3-25, L3-26, L3-27 |
| rendering.md | L3-1, L3-2, L3-3, L3-4, L3-5 (read in full; heavy-source table, LSD_COST, LSD_PHASES, audio units 13-15, ISF role detection, discrete inputs, render budget, resting-texture copy all verified clean) |
| tour_renderer.md | L3-35 (scripts, tour files, website data all exist) |
| ui.md | L3-6 to L3-13 (read in full; ~200 identifiers grepped, all others exist) |
| unified_control_mapping.md | L3-31 (sections 1-5 are labelled design spec/roadmap, not checked as fact) |
| web_subsystem.md | not reviewed (lane 6). Cross-lane hint: line 179 names `web/content/fxchains/` and "copied from `defaults/fx_chains`", both gone since 3ea7b52 (see L3-22) |
| audit.md | not reviewed (blueprint) |
| Links across all of `docs/` | clean: no `file:///` link (only `--media-url file://...` as a CLI value in tour_renderer.md:172), no absolute or root-relative link, no relative link or `github.com/.../blob/main/<path>` link to a missing file |
| Links into `docs/archive/` | clean: rendering.md:74, preset_management.md:162, ui.md:3, ui.md:164, cli_and_screenshot_automation.md:5 all label the target as archived / retired history |
| mkdocs.yml nav | every non-archive page under `docs/` is in nav; `archive/` excluded on purpose (`exclude_docs`). L3-36 |

Severity counts: bug 0, wrong 12, stale 14, polish 10, decision 0.

## Findings

### L3-1 [wrong] SourceDocRegistry section describes a registry that no longer exists
- Where: docs/developer/rendering.md:82-84
- Evidence: doc says the registry covers only `mandala`, `dynamic_spiral`, `icosa_h3` plus dead entries (`colors`, `gyroid`, `chladni`, `attractor_feedback`, `hyper_mesh`, `hyper_slice`), keys are `"feedback/<param>"`, and it is surfaced by `ParametersRenderer` and `DeckControlPanel`. Code: `S/rendering/SourceDocRegistry.kt:15-59` describes every stock source (none of the listed dead ids except `hyper_slice`, which is a live stock source, `S/rendering/VisualSourceRegistry.kt:71`); key prefixes are `"view/"` and `"mixer/"` (`SourceDocRegistry.kt:8,63`); the only consumer is `S/ui/ParametersRenderer.kt:219-221` (DeckControlPanel does not use it).
- Impact: a developer adding a source follows wrong advice ("prune dead entries", `feedback/` keys).
- Fix: rewrite the bullet: one entry per stock source, keys `<id>/<Param>`, `view/<Param>`, `mixer/<Param>`, consumer ParametersRenderer.
- Verified:

### L3-2 [stale] Render diagram and steps omit the Master FX chain and the chain-level wet
- Where: docs/developer/rendering.md:35-49
- Evidence: doc ends the pipeline at `mixer.frag composite -> masterFBO`, says the composite applies "bloom", and that the last enabled slot's texture is the deck output. Code: `S/rendering/Renderer.kt:316-354` composites into `masterCompositeFBO`, runs the Master FX chain (`renderFxChainPass(... mixer.masterFxChain ...)`), then blits to `masterFBO`; `renderFxChainPass` (`Renderer.kt:361-438`) blends per-slot wet, then blends the whole chain against the clean texture by `effectiveChainWet()` into `fxOutFBO`. `src/main/resources/shaders/mixer.frag:5-12` has levels only, no bloom.
- Impact: anyone reasoning about the master output or chain Dry/Wet from this page gets the wrong pass order.
- Fix: add the Master FX stage (masterCompositeFBO -> master chain -> masterFBO), mention chain wet and the ping/pong/out FBOs, drop "bloom".
- Verified:

### L3-3 [wrong] "Trail-based generators like Dynamic Spiral" own fb1/fb2 history buffers
- Where: docs/developer/rendering.md:47, :60
- Evidence: `hasFeedback` is only set from a folder source's `meta.json` `feedback` flag (`S/rendering/VisualSourceRegistry.kt:432,452`); no shipped `meta.json` sets it (`grep '"feedback"' library/sources/*/meta.json` is empty) and `dynamic_spiral` is a single ISF `.fs` with no passes. The fb1/fb2 path in `Renderer.render` (`Renderer.kt:46-62`) is reached only by a user meta.json source.
- Impact: misleads anyone touching Dynamic Spiral or the feedback path (it looks live and load-bearing; it is effectively unused by stock content).
- Fix: say the path serves folder sources whose `meta.json` sets `feedback: true` (none shipped); drop the Dynamic Spiral example.
- Verified:

### L3-4 [stale] VisualSourceRegistry described as a meta.json + shader.frag loader
- Where: docs/developer/rendering.md:90-98, :246
- Evidence: doc: "Scans subfolders on startup, parses `meta.json`, compiles `shader.frag`"; "`library/sources/*/shader.frag` use `#version 330 core`". Code: only `FOLDER_STYLE_IDS = setOf("mandala", FlameSource.SOURCE_ID)` are meta.json folders (`VisualSourceRegistry.kt:107,131-136`); every other stock source is `<id>.fs` loaded as `ISFVisualSource`.
- Impact: new contributors look for meta.json/shader.frag that do not exist.
- Fix: describe the two kinds (ISF `.fs`, the default; folder style for mandala and fractal_flame).
- Verified:

### L3-5 [polish] FBO telemetry label
- Where: docs/developer/rendering.md:72
- Evidence: doc says the menu bar shows `FBO: N (XMB)`; `S/ui/MenuBar.kt:437` draws `"GPU buffers: %d (%.0f MB)"` (tooltip at :561 says "GPU Framebuffers (FBO)").
- Impact: minor; searching for the label fails.
- Fix: quote the current label.
- Verified:

### L3-6 [wrong] Modal pipeline names a popup that does not exist
- Where: docs/developer/ui.md:82
- Evidence: doc lists `drawSourceChangeConfirmPopup()`. `S/ui/PopupManager.kt` has `drawExitPopup` (:68), `drawMidiWarningPopup` (:97), `drawDeckConfirmPopups` (:131), `drawRestoreDefaultsPopup` (:192), and no source-change popup. The root-scope list in `S/ui/UIManager.kt:362-378` also draws `VideoExportModal`, `BrowserPopupHandler.drawDeleteAssetConfirmationPopup`, `UpdatePromptModal`, `AboutModal`, `missingItemsPanel` and `PerformanceMacroStrip.drawFileBrowser`, none listed.
- Impact: developer looks for a function that is gone.
- Fix: replace the list with the current one from UIManager.kt:362-378.
- Verified:

### L3-7 [stale] DeckPresetController section describes logic that moved to DeckOps
- Where: docs/developer/ui.md:104-113
- Evidence: doc: `changeVisualSourceSafely` "prompts user confirmation ..., resets active preset ..., clears stale selections ..., pushes an undo snapshot", and DeckPresetController "manages independent `ImGuiFileBrowser` dialogs for Deck A and Deck B". Code: `S/ui/DeckPresetController.kt:98-107` is a one-line delegate to `DeckOps.request(slot, DeckChange.Source(...))` with the comment "[state] and [deckLabel] are unused now"; DeckPresetController has no file browser (ImGuiFileBrowser is used by PerformanceMacroStrip, VideoExportModal, MidiPreferencesPanel, BrowserPane, MissingItemsPanel).
- Impact: wrong place to change source-switch or prompt behaviour.
- Fix: say it delegates to DeckOps (link preset_management.md §3); drop the file-browser bullet.
- Verified:

### L3-8 [stale] Clock row placed on a "MASTER tab"
- Where: docs/developer/ui.md:211
- Evidence: "consolidated on the Performance MASTER tab's Clock row". Pages are `ab`, `bgpv`, `mixer` (`S/ui/PerfPageStore.kt:135`, `src/main/resources/perform_pages/mixer.json` holds `global`); ui.md:86 itself says "MIXER page". beat_sync.md:148 already says MIXER.
- Impact: inconsistent within the page.
- Fix: "the MIXER page's Clock row".
- Verified:

### L3-9 [stale] Two-mode `[ Presets ] / [ FX ]` Library toggle and its group boxes
- Where: docs/developer/ui.md:221-222; docs/developer/preset_management.md:180, :201
- Evidence: ui.md: "`LibraryViewMode` toggle picks ... In `[ FX ]` mode ... its live queues ... in place of the normal preset panels", "A 8 px horizontal gap (`groupGap`) separates the two rounded group boxes". Code: `LibraryViewMode` is `PRESETS, FX, TRANS, QUEUES, MAPS` tabs (`S/ui/LibraryPanel.kt:29-36`); all five queues sit side by side on the Queues tab (ui.md:214 says so); `groupGap` does not exist. preset_management.md:201 says FXBrowserPanel has "an All / Stock / Singles / Chains filter menu"; `S/ui/browser/FXBrowserPanel.kt` has no such menu (sections are tree scopes in BrowserPane).
- Impact: describes a layout that has been gone for weeks; contradicts ui.md:214.
- Fix: delete ui.md:221-222 (keep the `FxOps.applyItem` sentence under §8 Library layout); reword preset_management.md:180, :201 to "the FX tab".
- Verified:

### L3-10 [polish] Stale child-window id and a "fixed" height that is computed
- Where: docs/developer/ui.md:223, :224
- Evidence: `##presets_scroll` is not in code (queue children `##queue_items_scroll`, `##bg_queue_items_scroll`, `##fx_bg_queue_items_scroll` exist). `libTitleBarH = 32f` is `(textLineHeight + 12 + 2*border).coerceAtLeast(32f)` (`S/ui/UIManager.kt:572-574`).
- Impact: small.
- Fix: drop `##presets_scroll`; say "at least 32 px".
- Verified:

### L3-11 [polish] TooltipHelper section uses old constant and field names; API list incomplete
- Where: docs/developer/ui.md:229-233, :247-248, :250-265
- Evidence: doc names `CURSOR_HEIGHT`, `CURSOR_GAP`, `CURSOR_WIDTH`, `MARGIN`, `lastHoveredFrame`, key `(minX.toInt() shl 16) xor minY.toInt() xor text.hashCode()`. Code: `CURSOR_BOX_WIDTH`, `CURSOR_BOX_HEIGHT`, `GAP_Y`, `PADDING` (`S/ui/TooltipHelper.kt:31-35`), `lastHoverFrame` (:80), key `(minX shl 16) xor (minY and 0xFFFF) xor text.hashCode()` (:296). API list omits `showCustomTooltip` (:389, one of the four helpers the audit blueprint mandates) and `itemTooltip(..., allowWhenDisabled)` (:286).
- Impact: grep for the documented names fails; the mandated helper is undocumented here.
- Fix: rename in the doc; add `showCustomTooltip` and `allowWhenDisabled`.
- Verified:

### L3-12 [polish] Preferences label and menu inventory out of date
- Where: docs/developer/ui.md:204, :206-209
- Evidence: doc: "`File > Preferences... > Audio Engine`"; the category label is "Audio Hardware" (`S/ui/PreferencesPanel.kt:31`). File menu also has "Restore Factory Presets..." (`S/ui/MenuBar.kt:65`); Help has "Check for Updates..." and "About Liquid LSD" (:324, :337); doc lists neither.
- Impact: small.
- Fix: update the label and menu lists.
- Verified:

### L3-13 [polish] Component graph and DI note incomplete
- Where: docs/developer/ui.md:49-52, :58
- Evidence: mermaid graph lists four PropertiesPanel sections; `PropertiesPanel` also hosts `SeqSection` and `ValueParamSection` (`S/ui/PropertiesPanel.kt:60`, files in `S/ui/`). ui.md:58 says panels reach `NotesManager` via `session`; `S/SessionContext.kt:17-31` has no notes manager (NoteEditorModal calls `NotesManager` directly).
- Impact: small.
- Fix: add the two sections; drop NotesManager from the session list.
- Verified:

### L3-14 [wrong] CvHistoryBuffer size is 600, not 200
- Where: docs/developer/architecture.md:81; docs/developer/modulation.md:17
- Evidence: doc: "Pre-allocated ring buffer storing 200 CV samples" / "history (CvHistoryBuffer, 200 samples)". Code: `ModulatableParameter.historySize = 600` (`S/parameters/ModulatableParameter.kt:19,58`), `CVRegistry` histories `CvHistoryBuffer(600)` (`S/cv/CVRegistry.kt:129`), PropertiesPanel `CvHistoryBuffer(600)` (`S/ui/PropertiesPanel.kt:253`).
- Impact: wrong constant for anyone sizing scopes.
- Fix: 600.
- Verified:

### L3-15 [wrong] Notes sync attributed to PresetManager
- Where: docs/developer/architecture.md:109
- Evidence: "`PresetManager` automatically syncs in-memory notes ... (`syncFromDto`) and save (`syncToDto`)". Callers: `S/presets/DeckOps.kt:298` (load) and `S/presets/PresetRepository.kt:47` (save); PresetManager calls neither. (preset_management.md:32 correctly says DeckOps.)
- Impact: wrong file to look in.
- Fix: "DeckOps (load) and PresetRepository (save)".
- Verified:

### L3-16 [stale] `isBpmLocked` documented as the clock-mode flag
- Where: docs/developer/beat_sync.md:131-138; docs/developer/architecture.md:82
- Evidence: beat_sync §"BPM Sources & Locking" is organised around `isBpmLocked = true/false`; architecture lists it among `@Volatile` flags. Code: `isBpmLocked` is `@Deprecated("Use clockSource == ClockSource.MANUAL instead")`, a computed property over `@Volatile var clockSource` (`S/audio/AudioEngine.kt:215,234-239`). The diagram at beat_sync.md:23 already uses `ClockSource`.
- Impact: points developers at a deprecated API.
- Fix: rename the subsections to `ClockSource.MANUAL` / `AUDIO_TRACKER`; list `clockSource` in architecture.md.
- Verified:

### L3-17 [stale] Audio settings file and owner
- Where: docs/developer/audio_dsp.md:192-202
- Evidence: "serialized to and restored from `lsd-settings.properties` via `UITheme.kt`". Code: `S/ui/AppPreferencesStore.kt:31-32` writes `lsd-preferences.properties` and only reads `lsd-settings.properties` as a legacy fallback; the key list omits `audioResponse` (the one reactivity control, documented at audio_dsp.md:96).
- Impact: someone editing settings by hand edits the wrong file.
- Fix: name `lsd-preferences.properties` / `AppPreferencesStore`; add `audioResponse`.
- Verified:

### L3-18 [polish] Unbalanced code fence in the beat-sync diagram
- Where: docs/developer/beat_sync.md:9, :33, :42
- Evidence: three ``` lines; :33 closes the block early, :34-41 render as a second indented block, :42 renders as a literal ``` paragraph (bundled `src/main/resources/docs/developer/beat_sync/index.html` shows `<p>```</p>` right before "Beat Tracker Model"). Only file in docs/ with an odd fence count.
- Impact: visible stray ``` and a split diagram in the docs site and in-app help.
- Fix: delete line 33.
- Verified:

### L3-19 [wrong] Serialization section describes a migration and aliases that do not exist
- Where: docs/developer/modulation.md:306-317
- Evidence: doc: `ModulatorDto.toDomain()` "includes a one-line migration `"gen1" / "gen2"` → `"lfo"`"; "some use `@SerialName` aliases, e.g. `weight` for `depth`". Code: `S/models/PresetModels.kt:400-` maps `sourceId = sourceId` straight through; no `gen1`/`gen2` anywhere in `S/`; no `@SerialName` in `PresetModels.kt` or `S/parameters/CvModulator.kt`.
- Impact: a developer relies on a migration that will not happen.
- Fix: delete both claims (or restore the migration if old patches still matter: owner's call).
- Verified:

### L3-20 [stale] ParameterResolver paths and categories predate the ISF migration
- Where: docs/developer/modulation.md:323-334
- Evidence: doc examples `"Deck A/View/Zoom"`, `"Deck A/FB/Decay"`, Gain at `"Deck <X>/<DisplayName>/Gain"`, categories "Deck View: 3DMode, Zoom, RotateX, RotateY, ... BlendMode" and "Deck Feedback: Decay, Gain, ... Kaleido". Code: `S/rendering/Deck.kt:175` `"$prefix/Gain"`, :179 FX chain under `"$prefix/FX"`, :182-183 only `View/Zoom` and `View/RotateZ`; the old Gain path is only a legacy alias (`S/parameters/ParameterResolver.kt:19-25`). Mixer list misses `transQueuePrev/Next`, `fxQueuePrev/Next`, `fxBgQueuePrev/Next`, `tapTempo` and `Transition/...` (`S/rendering/Mixer.kt:355-380`). rendering.md:9 already says the fb/3D parameters are gone.
- Impact: MIDI/OSC mapping authors use paths that do not resolve.
- Fix: regenerate the list from `Deck.getParameterPaths` / `Mixer.getParameterPaths`.
- Verified:

### L3-21 [stale] CV source and follower lists miss the sequencer, beatSine and SNAP
- Where: docs/developer/modulation.md:84, :136, :140, :269-285
- Evidence: registered ids include `seq` (`S/cv/CVRegistry.kt:107`, evaluated at `S/cv/Evaluators.kt:218`) and `beatSine` (`CVRegistry.kt:101`, `Evaluators.kt:302`), absent from the sourceId list and the dispatch diagram. `AudioFollowerMode` has `SNAP` (`S/parameters/Enums.kt:77`), absent from the table. :136 limits followers to `audio_amp/bass/mid/high`; hi-mid and the hit sources go through it too.
- Impact: the sequencer, a whole modulator type, is undocumented on the modulation page.
- Fix: add `seq` (and a short sequencer subsection or link), `beatSine`, `SNAP`; widen :136.
- Verified:

### L3-22 [stale] Stock FX chain location and test name
- Where: docs/developer/modulation.md:298, :302
- Evidence: "The 16 stock chains in `defaults/fx_chains/` (and their identical `web/content/fxchains/` copies) ... `StockDefaultsTest.shippedFxChainsCarryTheShippedDefaults`". Commit 3ea7b52 deleted `defaults/fx_chains/` (neither directory exists); factory chains are `web/fxchains/web-*.lsdfxchain`, checked by `factoryFxChainLfoSwingsFit` and `factoryPresetsAndChainsUseDesktopParameterNames` (`src/test/kotlin/llm/slop/liquidlsd/presets/StockDefaultsTest.kt:236-246, 382-396`); `shippedFxChainsCarryTheShippedDefaults` no longer exists. Same stale path in web_subsystem.md:179 (lane 6).
- Impact: recipe authors edit and test against files that are gone.
- Fix: point at `web/fxchains/` and the current test names.
- Verified:

### L3-23 [wrong] Canonical bank count and the "sole registrar" claim
- Where: docs/developer/preset_management.md:165, :168
- Evidence: ":165 12 canonical banks" lists 11 (omits `GLOBAL`; `S/macro/MacroEngine.kt:51-55`). ":168 SessionSerializer is the only site that calls `MacroEngine.registerBank()`"; also called by `S/presets/GeneratorDefaults.kt:230`, `S/presets/DeckOps.kt:339`, `S/macro/FxMacroSync.kt:160`, `S/macro/TransitionMacroSync.kt:36`, `S/macro/MacroBankSerializer.kt:89` (all lazily, when the bank is missing).
- Impact: a stated invariant is false; someone may rely on it when changing bank lifecycle.
- Fix: list GLOBAL; restate as "SessionSerializer registers all 12 at startup; other sites register lazily if a bank is missing".
- Verified:

### L3-24 [wrong] Perform view "toggled with F4"
- Where: docs/developer/preset_management.md:162
- Evidence: no F4 shortcut exists (`S/ui/shortcuts/ShortcutManager.kt` registrations; F4 only appears in the key-name table `KeyCombination.kt:247`). The Perform view is the default workspace, not a toggle.
- Impact: wrong instruction.
- Fix: drop "toggled with `F4`".
- Verified:

### L3-25 [wrong] `--empty` flag does not exist
- Where: docs/developer/preset_management.md:152
- Evidence: "When starting without a pre-existing `last_session.json` (and without `--empty`)". `S/cli/CliArgs.kt:52-102` parses `--help`, `--version`, `--smoke-test`, `--tour*`, `--no-audio`, `--ui-lab`, `--screenshot-*`, `--window`; no `--empty`. The empty start is the `StartupBehavior.EMPTY` preference (preset_management.md:47).
- Impact: users and devs try a flag that is silently ignored.
- Fix: replace with "unless Startup is set to Empty".
- Verified:

### L3-26 [wrong] PlaylistParser resolution roots and extensions
- Where: docs/developer/preset_management.md:133
- Evidence: doc: fallback to legacy `presets/patches/`, auto-extension `.lsd`, `.json`, `.patch`. Code: `presetExtensions = listOf(".lsd")`, `defaultRoots = listOf(File("library/presets"))` (`S/presets/PlaylistParser.kt:17-18`).
- Impact: wrong expectation about which playlist lines resolve.
- Fix: library/presets only, `.lsd` only.
- Verified:

### L3-27 [stale] Preset mtime tooltip and feedback-parameter healing
- Where: docs/developer/preset_management.md:23, :35-44
- Evidence: :44 "Consumed by `DeckControlPanel` to draw hover tooltips (`Last saved: ...`)": `activePresetMtime*` is read only in `S/presets/PresetManager.kt`, `PresetRepository.kt`, `SessionSerializer.kt`; no UI shows "Last saved". :23 sanitizer "injects ... feedback parameters (`fbKaleido`, ...)" against "canonical feedback parameter specifications": deck feedback is gone and legacy feedback values are dropped (`S/presets/PresetMigrator.kt:12`; rendering.md:9).
- Impact: describes UI and migration behaviour that does not happen.
- Fix: drop the tooltip consumer (or say the field is bookkeeping only); drop the feedback bullets.
- Verified:

### L3-28 [wrong] Export pipeline specifics
- Where: docs/developer/export_pipeline.md:44-46, :63, :79-81, :89-95
- Evidence: (a) audio is written as 16-bit PCM WAV (`.tmp_aud_*.wav`, `S/export/RealtimeRecorder.kt:188`, samples `* 32767 ... toShort()` :292), not "raw 32-bit float PCM". (b) Sub-frames are 1..8 (`config.superSamplingFactor.coerceIn(1, 8)`, `S/export/OfflineRenderStudio.kt:112`), not {2,4,8,16}. (c) Accumulation is `GL_RGBA16F` only with uniform 1/N weights (`S/export/AccumulationBuffer.kt:21,31-40`); no RGBA32F, Gaussian curve or shutter angle exists. (d) "Execute Ping-Pong Feedback" step: deck feedback is gone. (e) Example command uses `-preset veryfast -crf 17`; code uses `-preset fast` with default crf 18 (`S/export/FFmpegProcessPipe.kt:36,210`); probed hardware encoders are nvenc/qsv/amf/videotoolbox (:76), not `h264_vaapi`. (rendering.md:216-236 repeats only the correct parts.)
- Impact: wrong expectations about export quality and encoder support.
- Fix: correct each point; drop shutter/Gaussian or mark as not built.
- Verified:

### L3-29 [stale] Jar name in the ZGC launch lines
- Where: docs/developer/ops_tuning.md:13, :16
- Evidence: `build/libs/liquid-lsd-desktop-1.0-SNAPSHOT-all.jar`; the build produces `build/libs/liquid-lsd-<version>-all.jar` (currently `liquid-lsd-0.9.1-all.jar`; version from `build.gradle.kts:21`).
- Impact: copy-paste command fails.
- Fix: use `liquid-lsd-<version>-all.jar` (or the shipped run scripts).
- Verified:

### L3-30 [polish] CLI flag table incomplete
- Where: docs/developer/cli_and_screenshot_automation.md:221-229
- Evidence: table omits `--smoke-test` and the `--tour*` family (`S/cli/CliArgs.kt:58-70`).
- Impact: small.
- Fix: add `--smoke-test`; one row pointing to tour_renderer.md for `--tour*`.
- Verified:

### L3-31 [wrong] Built-in Perform pages and Twister side-button CCs
- Where: docs/developer/unified_control_mapping.md:296 (§6.3), :303 (§6.4), :279
- Evidence: §6.3: built-in pages "`decks`, `master`, `ab`, `bgpv`, `mixer`" and "the shipped Twister profile uses `ab`, `bgpv`, `mixer`, `master`". Code: built-ins are `deck-ab`, `deck-bgpv`, `mixer` (ids ab/bgpv/mixer; `S/ui/PerfPageStore.kt:135`); `src/main/resources/controllers/midi-fighter-twister.json` `banks.pages` = `perform.ab`, `perform.bgpv`, `perform.mixer` (§6.4 says this correctly). §6.4: side buttons "CC 8, 11, 13"; the profile has `side` ccs `[8, 12, 13]`, `chainlink` 9, `bankstep` 11, `shift` 10. Heading §6 still dated 2026-10-01.
- Impact: profile authors bind the wrong CC / a page id that does not exist.
- Fix: correct the page list and CCs; drop or refresh the date.
- Verified:

### L3-32 [stale] ARM64 PipeWire struct caveat names removed structs
- Where: docs/developer/build_arm64_linux.md:115-117
- Evidence: "`SpaData` and `PipeWireBufferStruct` in `PipeWireLibrary.kt` are JNA structures ..."; neither exists in `S/` (grep empty). Buffer access is now offset-based `SpaBufferLayout` (`S/rendering/pipewire/PipeWireLibrary.kt:123`), as architecture.md:123 says.
- Impact: the re-verify note points at nothing; the real aarch64 risk (hand-coded offsets) is unstated.
- Fix: say the `SpaBufferLayout` offsets assume LP64 and must be re-checked on aarch64 if the PipeWire headers change.
- Verified:

### L3-33 [stale] Flame gotcha: bundled sources "never overwritten"
- Where: docs/developer/flame_source.md:56
- Evidence: "Files in `library/sources/<id>/` are extracted once and never overwritten: delete the folder after changing a bundled source's resources." `VisualSourceRegistry.ensureDefaultSources` (`S/rendering/VisualSourceRegistry.kt:118-124`) replaces any file that matches a previously shipped hash (`shipped_source_hashes.txt`), so unedited installs get updates; only user-edited files are kept. (The real dev gotcha is `scripts/shipped_source_hashes.py`, per memory/SRC review.)
- Impact: developers delete folders needlessly, or forget to record hashes.
- Fix: replace with the hash rule and the script to run.
- Verified:

### L3-34 [polish] Provenance ledger contradicts itself on reserved flame ids
- Where: docs/developer/clean_room_provenance.md:27 vs :61-62
- Evidence: :27 "Flame variations 49-97 (46 built; 50, 65, 67 and 98 reserved)"; :61-62 and `S/rendering/FlameVariations.kt:57,70,72,93` say reserved are 50 (super_shape), 65 (lazysusan), 98 (mobius); 67 is `pre_blur`, built by black box (flame_source.md:14). The range is 49-98.
- Impact: small, but this is the legal ledger. Lane 7 owns the content.
- Fix: "49-98 (46 built from the spec; 50, 65, 98 reserved; 67 built by black box)".
- Verified:

### L3-35 [polish] `--tour-hashes` listed as an opt-in although hashes are on by default
- Where: docs/developer/tour_renderer.md:109
- Evidence: the option list gives `--tour-hashes (per-frame SHA-256, ...)` as if opt-in; `TourOptions.hashes = true` by default (`S/tour/TourOptions.kt:14`) and `--tour-no-hashes` turns them off (`S/cli/CliArgs.kt:70`). The website line in the same paragraph does use `--tour-no-hashes`.
- Impact: small.
- Fix: document `--tour-no-hashes` as the switch.
- Verified:

### L3-36 [polish] mkdocs nav placement
- Where: mkdocs.yml (nav)
- Evidence: `developer/ops_tuning.md` sits at top level ("Operations & Tuning") instead of under Developer Reference; the `Audit Blueprint` nav entry exists only as an uncommitted working-tree change (`git diff mkdocs.yml`). All other pages are in nav; `archive/` excluded on purpose.
- Impact: small; the audit entry is lost if the working tree is reset.
- Fix: commit the audit entry with the blueprint; move or keep Ops deliberately.
- Verified:

## Top 5

1. L3-20: ParameterResolver paths in modulation.md list removed FB/View parameters and the old Gain path (affects MIDI/OSC mapping authors).
2. L3-2: rendering.md pipeline omits the Master FX chain and chain wet.
3. L3-19: modulation.md documents a gen1/gen2 migration and @SerialName aliases that do not exist.
4. L3-28: export_pipeline.md overstates motion blur (16x, Gaussian, shutter angle) and misstates the audio format and encoders.
5. L3-31: unified_control_mapping.md gives wrong Twister side-button CCs and non-existent built-in pages.
