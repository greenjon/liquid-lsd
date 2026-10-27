# Liquid LSD — Master Project Roadmap

This document serves as the single source of truth for completed, active, and upcoming engineering milestones for **Liquid LSD Desktop**.

---

## High-Level Vision & Path to v1.0

Liquid LSD is a real-time, audio-reactive procedural visual synthesizer and VJ performance workstation. The roadmap to v1.0 centers on **performance, interoperability, tactile control, and stage reliability**:

1. **Foundational Synthesis & Graphics**: Real-time OpenGL 3.3 FBO pipeline, dual decks + background/preview decks, ping-pong feedback, and modular ISF post-processing.
2. **Inter-App Sharing & Ingest**: Seamless zero-copy GPU video streaming (Spout, Syphon, PipeWire) and DAW network clock sync (Ableton Link).
3. **Tactile Hardware Control**: Full control parity across MIDI controllers, keyboard shortcuts, CV modulators, and GUI.
4. **Ergonomic Live Performance**: Low-cognitive-load UI, continuous generative morphing, and rapid preset discovery.

---

## Current Status & Summary Matrix

| Milestone / Workstream | Target Area | Target | Status | Key Deliverables |
| :--- | :--- | :---: | :---: | :--- |
| **Performance Mode & Macros** | `ui/*`, `macro/*`, `presets/*` | **v1.0** | **OPERATIONAL** | Performance matrix (A/B, BG/PV, MIXER pages, `F4`) with Deep Edit, `MacroEngine`/`MacroBank` binding, curves, FX slot cells and focus mode on the deck/Master rows. Remaining macro-learn coverage bumped to v1.1 (Milestone 5). |
| **UI & UX Polish** | `ui/*` | **v1.0** | **ACTIVE** | Perform / Edit / Library views polished and verified at 1280×720. |
| **Stability Testing** | all | **v1.0** | **ACTIVE** | Long sessions, load/swap under load, session restore, device hot-plug, 5 release platforms. |
| **ISF Binding Audit** | `rendering/isf/*`, bundled shaders | **v1.0** | **ACTIVE** | Sensible Metaknob/auto-bind and default bindings verified for every bundled ISF. |
| **Two Polished Themes** | `ui/UITheme.kt` | **v1.0** | **ACTIVE** | Two `UITheme.Theme` palettes dialed in as the supported v1.0 themes (TBD which). |
| **Unified Control & Mapping** | `midi/*`, `shortcuts/*`, `ui/*` | **v1.1** | **PENDING** | Decoupled `CommandRegistry`, hardware controller profiles (`library/mappings/`), universal learn. |
| **Session Scratchpad** | `notes/*`, `ui/*` | **v1.1** | **PENDING** | Standalone floating/docked notes scratchpad window (`~/.liquid-lsd/scratchpad.txt`). |
| **Mandala v2+ Recipe Vault** | `sources/mandala/*`, `ui/*` | **v1.1** | **PENDING** | Visual recipe gallery popover with micro-previews, geometric style tagging, quick-slots. |
| **FX Metaknob Multi-Parameter Linking** | `rendering/isf/*`, `rendering/FxChain.kt`, `ui/FxMetaBindingEditor.kt` | **v1.0** | **DONE** | Several targets per Metaknob with knob zones and all five curves, edited in the Edit-row strip and Edit bay, plus a per-slot knob order for the focus knobs. |
| **Macro-Learn Coverage Completion** | `ui/*`, `macro/*` | **v1.1** | **PENDING** | Click-to-learn for randomization bound rows, per-step grid, and dropdowns (shared combo wrapper). Bumped from v1.0. |
| **Automated Screen Capture & UI Lab** | `export/*`, `ui/*`, `Main.kt` | **v1.0** | **DONE** | GPU PBO readback, headless `--screenshot-ui` runner and `--ui-lab` gallery (`./gradlew captureUiLab`). |
| **Step Sequencer v2** | `ui/SeqSection.kt`, `cv/Evaluators.kt`, `web/evaluator.js` | **v1.1** | **PENDING** | Playback direction (reverse, ping-pong, random), swing, per-step probability/ties/skip, per-step length multipliers, saved named patterns. |
| **Toggle/Select Metaknob Targets** | `rendering/isf/*`, `ui/*` | **v1.1** | **PENDING** | Bool and select inputs as Metaknob targets (Milestone 10). |
| **Localization** | `ui/*` | **v1.1** | **PENDING** | All user-facing text moved to string tables; translation support with locale selection. |
| **Live Shader Reload** | `rendering/isf/*`, `rendering/VisualSourceRegistry.kt`, `rendering/Shader.kt` | **v1.1** | **PENDING** | Recompile a saved shader in place, in every deck, slot and transition that uses it, keeping the last good version on error (Milestone 11). |
| **Reactivity Recipes: FX, Transitions, Tooling** | `resources/stock_defaults/`, `presets/StockDefaults.kt`, `rendering/isf/ISFAutoBindEngine.kt`, `web/dsp.js` | **TBD** | **PENDING** | 33 stock sources ship recipes (2026-10-27). Still to do: FX and transition recipes (needs a place for FX defaults to carry modulators, and a transition defaults store), the auto gain ported to the web TV, and a recipe idea for Fluid Ink. |
| **Harmonograph Rework** | `default_sources/harmonograph/*` (removed from the stock set 2026-10-27), `web/shaders/*` | **v1.1** | **PENDING** | Rebuild the harmonograph as a persistent accumulation (many windings building into a dense rosette, slow decay) instead of one 128-segment polyline, which read as a single thin string. Restore it to the stock set, the web TV (3 presets) and its reactivity recipe from git history (commit before `Remove Harmonograph`); the old files are the starting point. |
| **Downbeat Detection** | `audio/BeatTrackerEngine.kt`, `cv/CVRegistry.kt`, `web/beattrack.js` | **v1.1** | **PENDING** | Bar-start (downbeat) detection on top of the beat tracker, a real `barPhase` source and beats-per-bar setting, tap override and confidence (Milestone 12). |
| **Expanded FX Chain View** | `ui/PerformanceMatrixPanel.kt`, `ui/FxSlotCell.kt`, `macro/FxMacroSync.kt` | **Compost (v1.2)** | **SHELVED** | Mixxx-style 3-sub-row chain editing on the Performance matrix. Decided 2026-10-13: no milestone. Notes: `.planning/fx-chain-expanded-view-notes.md`. |

---

## v1.0 Active Milestones

### v1.0 Release Focus (decided 2026-09-25)

The feature set is frozen for v1.0. What remains is finishing and hardening, not new features — this may be all that happens before the release. Everything else is in the [v1.1 Backlog](#v11-backlog).

- [ ] **UI & UX Polish**: layout, spacing, labels, tooltips and interaction flow across Perform / Edit / Library views, verified at the 1280×720 minimum screen size.
- [ ] **Stability Testing**: long-running sessions, preset/chain/queue load and swap under load, session restore, device hot-plug (audio, MIDI, video ingest), across the 5 release platforms.
- [ ] **ISF Binding Audit**: check the Metaknob/auto-bind result (`ISFAutoBindEngine`, the shipped defaults in `resources/stock_defaults/`) and default parameter bindings for **every** bundled ISF generator, filter and transition, so each one does something sensible out of the box.
- [ ] **Two Polished Themes**: dial in two of the `UITheme.Theme` palettes as the supported v1.0 themes (which two is TBD).
- [ ] **Linux Camera Input**: webcams appear under External video (PipeWire lists them as video nodes) but have never connected in testing. Suspect: `ExternalVideoDiscovery` names streams by `node.description`, and `PipeWireReceiverImpl` passes that as `target.object`, which PipeWire matches against `node.name` or `object.serial`. Until fixed, the user guide and the walkthrough do not mention cameras.

---

## v1.1 Backlog

> These milestones are parked for consideration after v1.0 ships. Scope, priority, and whether each makes the cut will be decided once the v1.0 scope is locked.

### Milestone 1: Unified Control Mapping & Hardware Profiles
> **Reference**: [`docs/developer/unified_control_mapping.md`](docs/developer/unified_control_mapping.md)
> **Status**: Pending / High Priority

While the multi-type MIDI subsystem (Notes, CC, Pitch Bend, Soft Takeover, Relative Rotary Encoders) and centralized `ShortcutManager` are operational, the goal is full control parity across all input modalities (inspired by Mixxx):

- [x] Multi-type MIDI event capture and lock-free atomic buffers (`MidiEngine`).
- [x] Soft takeover (pickup) and relative rotary encoder decoding (Binary Offset, Signed Bit, Two's Complement).
- [x] Centralized `ShortcutManager` with interactive key rebinding and collision detection.
- [x] Dedicated "MIDI Controls" tab in Preferences with real-time packet monitor and profile manager.
- [ ] **Universal Action / Command Registry (`CommandRegistry`)**:
  - Decouple all user actions (menu actions, deck operations, mixer transitions, parameter tweaks) into registered `Command` instances.
  - Expose consistent interfaces for Trigger, Continuous/Scalar, Stepped/Relative, and Latched Toggle inputs.
- [ ] **Hardware Controller Preset Profiles (`library/mappings/`)**:
  - Out-of-the-box controller mappings (e.g. Novation Launchpad, Akai APC40, Pioneer DDJ, Midi Fighter).
  - JSON profile schema for community controller sharing.
- [ ] **Universal Right-Click "Learn" Overlay**:
  - Context menu on any UI slider, button, or toggle to trigger MIDI Learn, key shortcut assignment, or CV binding.

**Open question, revisit later (2026-09-28, from OSC UX audit):** Macro knobs currently reach OSC via a fixed, permanent address (`/macro/<bankId>/knob/<n>`, forwarded straight to `MacroOscBridge` — no Learn step needed, unlike every other OSC-mappable control in the app). MIDI has no such fixed-CC convention for macro knobs — it treats them as genuinely learnable via a dedicated `Macro/<bankId>/knob_N` path (`MidiMappingManager`'s regex-matched dispatch branch). Giving macro knobs the same *learnable, variable-address* OSC binding as MIDI (so a performer could point an arbitrary/unconventional TouchOSC control at a specific knob instead of using its fixed address) would require extending `OscMappingManager`'s dispatch with a matching `Macro/<bankId>/knob_N` case and turning `MacroKnobWidget`'s right-click from a bare MIDI-learn toggle into an actual Learn-MIDI/Learn-OSC context menu. Deferred for now — v1.0 shipped only a tooltip on each macro knob surfacing its fixed OSC address for discoverability. Decide later whether the fixed-address model is sufficient on its own or whether performers actually want the learnable escape hatch too.

---

### Milestone 2: Session Scratchpad & Live Notes
> **Reference**: Notes System & Companion Scratchpad Plan
> **Status**: Pending / Medium Priority

Performers need persistent, glanceable set notes during live shows without relying on physical sticky notes on their monitors:

- [x] Preset Tags metadata in `.lsdpatch` JSON DTOs (`DeckPresetDto.tags`).
- [x] Library search filtering by preset tags (`Search presets & tags...`).
- [x] Preset tag editing via browser context menu (`Rename / Edit Tags...`) and Save Preset modal.
- [x] 3-tier hierarchical notes storage: Source notes, Preset notes, Parameter notes (`NotesManager`).
- [ ] **Dedicated Session Scratchpad Window**:
  - Lightweight, togglable floating or docked ImGui window ("Notes" / "Scratchpad").
  - Unstructured multi-line text buffer automatically persisted to `~/.liquid-lsd/scratchpad.txt`.
  - Accessible via global shortcut (`Ctrl+N` / `Cmd+N`) and MenuBar entry (`View > Scratchpad`).

---

### Milestone 3: Mandala Visual Generator v2+
> **Reference**: [`docs/developer/mandala_future_roadmap.md`](docs/developer/mandala_future_roadmap.md)
> **Status**: Backlog / Future Enhancement

Enhancing the built-in Mandala procedural visual generator for live stage recall:

- [ ] **Visual Recipe Vault / Gallery Popover**:
  - Grid modal showing pre-rendered snapshots or live micro-previews of all ~300 built-in recipes.
  - Category tabs (`All`, `3 Lobes`, `4 Lobes`, `5 Lobes`, `6 Lobes`, `8+ Lobes`).
- [ ] **Geometric Tagging & Humanized Naming**:
  - Descriptive names and style tags (e.g., `#7: Floral Weave`, `Crystalline`, `Starburst`) replacing raw math vectors (`[26, 23, 14, 14]`).
- [ ] **Global Recipe Sweep Index**:
  - Sweep parameter moving continuously through all 300 recipes across all lobe counts via a single LFO or MIDI slider.
- [ ] **Favorites & Performance Quick-Slots**:
  - Quick-recall bookmark buttons (`[ Fav 1 ] [ Fav 2 ] [ Fav 3 ] [ Fav 4 ]`) on the Deck panel for instant switching.

---

### Milestone 4: FX Metaknob Multi-Parameter Linking
> **Reference**: Mixxx-style parameter link modes (Full/1st Half/2nd Half/Triangle/Bipolar/Invert), proposed 2026-09-22 alongside the macro link modes feature that ships this same transfer-function math for `MacroBinding`.
> **Status**: Done (2026-10-14). The open questions below were settled: bindings are per-slot and baked into presets, the auto-bind default stays single-target, and editing uses the same editor as macro targets (`FxMetaBindingEditor`).

Extend each ISF FX slot's single Metaknob (`ISFFilter.metaBinding`, `rendering/isf/FxMetaBinding.kt`) from driving exactly one uniform to driving **multiple** shader parameters simultaneously, each with its own link mode/curve/range — e.g. one Metaknob opening Cutoff on the first half and peaking Resonance in a triangle around center. This is a materially bigger lift than the macro-side version because today's Metaknob binding is auto-resolved by `ISFAutoBindEngine` (163 lines) to exactly one target (with dry/wet as the last-resort safety net), and that single-binding assumption threads through `FxChain` DTO serialization, `FXChainMacroStrip` UI, and `ParametersTabs` context menus.

**Open questions to resolve before scoping this for real:**
- **Auto-bind semantics with N targets**: `ISFAutoBindEngine.resolveBinding()` currently picks the *one* best macro-worthy uniform per filter. With multiple simultaneous bindings, does auto-bind pick a curated set automatically (and by what heuristic), or does it stay single-target-automatic with additional targets always manual/opt-in?
- **Is this redundant with macro link modes?** Once `MacroBinding` supports link modes and multi-target bindings per knob (see Milestone 1's Macro system), a performer can already choreograph multiple FX parameters from one macro knob by binding each parameter separately with its own link mode. Does a *second*, FX-slot-local multi-binding system pull its weight, or should Metaknob multi-binding be dropped in favor of steering users toward macro bindings for this use case?
- **UI surface cost**: per-parameter-row link-mode badges/cycling buttons in `FXChainMacroStrip`'s focused mode, plus a new context submenu in `ParametersTabs` — is the added visual density worth it in the already-dense FX rack UI?
- **Preset/DTO compatibility**: needs a `metaBindings: List<FxMetaBindingDto>?` alongside the existing single `metaBinding: FxMetaBindingDto?` on `FXSlotDto`/`FxChain`, with a defined fallback order for loading old presets — straightforward but adds a second code path to maintain indefinitely.
- **Zero-allocation constraint**: multi-binding evaluation runs in the render loop; needs to stay pure primitive arithmetic like the macro-side version, not surprising but worth stating as a hard constraint before implementation.

---

### Milestone 5: Macro-Learn Coverage Completion
> **Status**: Bumped from v1.0 (2026-09-25)

Click-to-learn already works for number boxes, Seq, LFO and Audio controls. Remaining:

- [ ] Randomization bound rows.
- [ ] Per-step sequencer bars (each step's value learnable, in the bar editor).
- [ ] Dropdown/select controls (`ImGui.combo()`) — needs a shared combo wrapper first (see Phase 7 notes in the archive).

---

### Milestone 6: Automated Screen Capture & UI Lab
> **Reference**: [`docs/developer/screen_capture_and_ui_iteration_proposal.md`](docs/developer/screen_capture_and_ui_iteration_proposal.md)
> **Status**: Bumped from v1.0 (2026-09-25) / Partially complete (Core GPU PBO readback complete; CLI screenshot automation pending)

Automating crisp, deterministic UI screenshot capture for documentation assets and providing an isolated sandbox for UI layout regression testing:

- [x] **Asynchronous GPU Readback**: High-performance PBO framebuffer readback pipeline (`PboReadbackPipeline.kt`) and Video Export dialog.
- [ ] **Headless CLI Screenshot Runner**: Parsing `--screenshot-ui=<file>`, `--window=<1920x1080>`, and `--screenshot-after-frames=<N>` arguments in `Main.kt` for automated CI/documentation image generation.
- [ ] **Isolated UI Lab Gallery Sandbox**: A lightweight `--ui-lab` startup mode providing an isolated sandbox environment to preview themes, custom icons, sliders, meters, and modal popups without requiring active audio hardware or heavyweight GLSL shader compilation.

---

### Milestone 7: Localization & Translatable UI Text
> **Status**: Pending (added 2026-09-25)

- [ ] **Extract user-facing strings**: move every hard-coded UI string (labels, buttons, menus, tooltips, dialogs, error messages) into a string resource table keyed by id.
- [ ] **Translation support**: locale selection in Preferences, loading of translated string tables with fallback to English, and a documented format for contributed translations.
- [ ] **Layout robustness**: check fixed-width widgets (badges, pills, slot cells) against longer translated strings, and make sure the bundled fonts cover the glyphs the target languages need.

---

### Milestone 8: Expanded FX Chain View (Mixxx-style)
> **Reference**: [`.planning/fx-chain-expanded-view-notes.md`](.planning/fx-chain-expanded-view-notes.md)
> **Status**: Compost (v1.2) / Shelved 2026-09-25, decided 2026-10-13

When editing an FX chain on the Performance matrix, show 3 sub-rows (one per slot, each with dry/wet + top 3 params, per-slot paging) instead of the single row + focus mode, so the whole chain is visible and ~12 parameters are reachable by mouse. Hardware keeps today's focus semantics: the focused sub-row is the `Macro/<bank>/knob_N` target, the others bind directly to their params.

- [ ] **Verify first**: whether focusing any FX row shrinks every knob in the grid to ~20px (shared `diameter` in `drawMatrix`) — fix with per-group knob sizing regardless of this feature.
- [ ] **Prototype Option A** (chain takes over the grid; reuses Edit-view row hiding + `groupRows` sub-labels) behind a toggle and evaluate in a real session.
- [ ] **If it earns its keep, Option B**: expand in place with the other rows collapsed to 22px strips (needs FxParamCell dropped to keep ~44px knobs at 1280×720, crossfader relocated when expanding Master FX).

**Deciding question:** big win for mouse-driven play; mostly a larger display of focus mode for 4-knob-controller play.

---

### Milestone 9: Step Sequencer v2
> **Status**: Parked (2026-10-20). The v1.0 sequencer is a bar editor with pattern tools, glide trace, snap and undo (see release notes); these are the features deliberately left out.

- [ ] Playback direction: forward, reverse, ping-pong, random.
- [ ] Swing (delay every other step by a fraction of the step).
- [ ] Per-step probability, ties and skips.
- [ ] Per-step length multipliers (a step that lasts 2x or 0.5x).
- [ ] Saved named patterns: a pattern library next to the in-memory Copy/Paste clipboard, shared across presets.
- [ ] Per-step macro/MIDI/OSC learn (tracked under Milestone 5).
- [ ] Every evaluator change here must be mirrored in `web/evaluator.js` (`scripts/sync_web.py`).

---

### Milestone 10: Toggle and Select Metaknob Targets
> **Status**: Pending (deferred from the 2026-10-21 Super Knob / Metaknob review, item 19b).

Metaknob targets are float inputs only (Add Target lists `TYPE == "float"`). Let toggles (`bool`, e.g. `mirror`, `invert`) and selects (`long` with `VALUES`) be targets too, so "the top of the sweep switches mirror on" works with a Second Half zone and a Step curve. Needs: the Add Target lists opened up, snapping checked under every zone and curve, and a look at whether a select changing mode mid-sweep reads as a glitch (maybe a default Step curve with a dead band).

---

### Milestone 11: Live Shader Reload
> **Status**: Pending (from the 2026-10-22 SRC review).

Saving a shader file while the app runs should recompile it and swap it into every deck, FX slot and transition using it, without a restart. Today a new file is picked up by Rescan Now, but a shader that's already loaded keeps its first version until restart (the file watcher only refreshes `ISFLibraryRegistry`, which nothing reads). In 1.0, a source that fails to compile shows a red checkerboard and an ⚠ with its first compiler error in Browse.

Build it in stages. Stage 1 covers the common case, an edit to the GLSL body only; stage 2 adds input changes; until stage 2 lands, an edit that changes the inputs is deferred (stage 3).

**Stage 1: reload with an unchanged input set**
- [ ] Shared-shader ownership: decks, FX slots and transitions share a master's `Shader`, so a reload has to replace the program in place (a mutable `programId` with `Shader.reload(vert, frag)` that links a new program, swaps it and deletes the old one; clear `uniformLocationCache` on swap), not dispose it under them. Clones then pick the new program up with no change.
- [ ] Wire `ISFFileWatcher` changes to a GL-thread reload of just the changed file (sources, filters, transitions). The watcher callback must report the changed path; queue the request and drain it at the top of the render loop, since GL calls can't run on the watcher thread. Extract the shader-building part of `loadFromISFFile` and its filter and transition counterparts into helpers shared by load and reload.
- [ ] A failed recompile keeps the last good version running (the old docs promised this) and surfaces the error in the UI (toast or the Browse ⚠), not only the log. A later successful save clears `compileErrors`.
- [ ] Tests: successful swap, failed compile keeping the old program.

**Stage 2: input changes**
- [ ] Inputs added, removed or retyped by the edit: keep values and bindings by name, drop the rest, and say so. Each live clone needs a "rebind to new header" step (cloned parameters, input bindings, persistent buffers and pass FBOs; multipass and feedback shaders rebuild their per-pass targets if the pass list changed). Decide how saved presets and macro bindings that reference dropped inputs behave.
- [ ] Tests: a retyped input keeps its value by name; a dropped input is reported.

**Stage 3: fallback until stage 2 lands**
- [ ] An edit that changes the inputs is not applied to live decks; mark the source "needs reload on next load" and say so in the UI.

**Open decision**
- [ ] Removed files: what a deck playing a deleted source does. Leading option: keep the last program running, mark the source as missing in Browse, drop it from the catalog.

**Done means:** release notes in both files, user guide and developer docs, tooltips.


---

### Milestone 12: Downbeat (Bar Start) Detection
> **Reference**: [`.planning/downbeat-detection-plan.md`](.planning/downbeat-detection-plan.md)
> **Status**: Desktop built 2026-10-23 (tests pass, unseen in the app; see `.planning/app-checklist.md`). Web port waits until we see how well it works on real music.

The beat tracker finds tempo and beat phase but not which beat is beat 1. A bar is `beats mod 4` from an arbitrary start or the last tap, so bar-length LFOs, sequencers, the clock knob downbeat flash and the web autopilot's bar-line switching are only as aligned as that accident.

- [x] **Bar plumbing**: `beatsPerBar` in `CVRegistry` (the beat count is renumbered so bars start on multiples of it, instead of carrying a separate offset), SNAP and the first tap of a run set beat 1, Link's quantum follows the setting.
- [x] **Detection**: `DownbeatDetector`: per-beat kick/onset/level features, scores over the last ~5 bars with margin and hysteresis, a confidence output.
- [x] **Control**: Off/Auto setting (default Off until tuned), tap/SNAP override that locks out the detector for 16 bars, hold on low confidence.
- [ ] **Web port** of the above, then phrase-aligned switching in `autopilot.js`.
- [ ] Tune on real music (labelled clips) before the web port. Not done: 3-vs-4 meter suggestion (a setting instead; unreliable without harmonic features), chroma features, phrase detection.

**Done means:** release notes in both files, user guide and developer docs, tooltips.

---

## Completed Milestones Archive

### Phase 1: Core Engine & Dual Deck Architecture
- [x] GLFW windowing and OpenGL 3.3 core context creation on Linux, macOS, and Windows.
- [x] Low-latency FBO architecture with ping-pong feedback loops (`cleanFBO`, `rawSource2DFBO`, `masterFBO`).
- [x] Dual deck rendering pipeline (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`) and mixer compositing (`Mixer.kt`).
- [x] Real-time JACK Audio Connection Kit and PipeWire-JACK client with zero-allocation audio callbacks.
- [x] Audio analysis DSP: Onset detection, spectral flux, frequency bands (bass/mid/high), RMS amplitude, and `CVRegistry`.
- [x] Hierarchical modulation system: `ModulatableParameter`, LFOs, Sample & Hold, and BeatClock.

### Phase 2: Suite C UI & Modern Layout
- [x] Modern 3-column workspace architecture: **Parameters**, **Properties**, **Mixer**, and collapsible **Library** drawer.
- [x] Synchronized 1.5x panel headers and title bars (`PanelTitleBar.kt`).
- [x] Embedded Properties tab row (`Value`, `MIDI`, `LFO 1`, `LFO 2`, `SEQ`, `Audio`) with overlaid live oscilloscope canvas.
- [x] Dynamic auto-scaling timebases for oscilloscope with zero-allocation label caching.
- [x] Full transition from legacy "Settings" to standardized **Preferences** (`AppPreferences`, `lsd-preferences.properties`, `Ctrl+P`).

### Phase 3: Universal Shader Ecosystem & ISF Standard
- [x] Full **Interactive Shader Format (ISF 2.0)** specification parser and GLSL preprocessor (`ISFParser`).
- [x] Multi-format shader compatibility bridge: Automatic ingestion of **ISF**, **Shadertoy** (`mainImage`), and **GLSLSandbox** without manual modification.
- [x] Modular FX slots per deck (originally 2 — Slot 1: Color/Degradation, Slot 2: Spatial/Distortion — since expanded to 4, `Deck.FX_SLOT_COUNT`).
- [x] ISF transition engine in Mixer with bundled transitions (`wipe`, `glitch`, `radial`, `luma_wipe`, `zoom_fade`).
- [x] Multi-pass generator and filter FBO rendering with persistent history buffers and imported texture assets.
- [x] Real-time audio FFT magnitude and waveform texture (`audioFFT` / `iChannel0` 512x2 `GL_R32F`). The spectrum row was silent until the dedicated `SpectrumAnalyzer` (1024-point Hann FFT, dB-mapped) replaced the beat tracker feed.

### Phase 4: Stage Interoperability & Video Sharing
- [x] Zero-copy GPU texture sharing outputs: **Spout2** (Windows), **Syphon** (macOS), and **PipeWire DMA-BUF** (Linux).
- [x] Independent video output streams for Deck A, Deck B, Deck BG, Deck PV, and Master Composite.
- [x] First-class external video stream ingest as native visual sources (cameras, OBS, media servers).
- [x] Direct live video feed selection in the Edit bay's SRC Browse tab (`External video...` menu) (the menu lists server names only; the old activity indicators went with the modal picker).
- [x] Ableton Link integration for network-wide tempo, beat phase, and quantum synchronization across local DAWs.

### Phase 5: Generative Morphing & Performance Controls
- [x] Continuous Constrained Random Morphing ($0.0 \leftrightarrow 1.0$) with ping-pong flip-flop boundary re-rolling.
- [x] Coordinate-space 2D transformations (Zoom and Rotate Z in generator space without edge cropping).
- [x] High-performance asynchronous PBO framebuffer readback pipeline (`PboReadbackPipeline.kt`) and Video Export dialog.
- [x] Integrated telemetry HUD in top bar displaying FPS, frame time, DSP latency, CPU%, and beat phase dots.
- [x] Dear ImGui modernization to 1.92.7.1.

### Phase 6: Advanced Feature Milestones
- [x] **TouchOSC & Open Sound Control**: Native UDP OSC 1.0 engine (`OscCodec`, `OscEngine`), TouchOSC layout mapping, XY pads, OSC Learn, bidirectional feedback. `OscMappingManager` with JSON profile persistence under `library/osc/`. Dedicated OSC Controls tab in Preferences. `/macro/knob/N` and `/macro/switch/N` forwarded to `MacroOscBridge` with outbound feedback.
- [x] **100% ISF Pipeline Migration**: Feedback loop, 2D-to-3D projection methods, and all mixer blend modes fully ported to ISF. Legacy hard-wired shaders (`feedback.frag`, `tri_planar.*`, `tetra_kaleido.*`) retired from the rendering pipeline; `mixer.frag` legacy blend branches bypassed (`uMode = -1` in `Renderer.kt`). Bundled ISF transitions in `src/main/resources/default_transitions/`; `library/transitions/` is the user-scannable drop folder.
- [x] **Preset Tags & Search**: Preset tags in `.lsdpatch` JSON (`DeckPresetDto.tags`), inline tag editor in browser context menu and Save Preset modal, tag search filtering in Library (`PresetListPanel`).
- [x] **Tooltips & LFO Ergonomics**: Multi-tier tooltips (`TooltipHelper`), logarithmic LFO time parser (`TimeUtils.parsePeriod`), and Min/Max modulation ranges on `CustomRangeSlider`.
- [x] **Modern Window Experience (CSD)**: Unified 1.5x top bar, frameless window drag region (`MenuBar`), window controls (minimize / maximize / close), and live telemetry HUD.
- [x] **Macro Controls & Parameter Linking**: 8 Knobs + 4 Switches (`MacroBank` / `MacroControl` / `MacroBinding`), 1-to-many bindings, modulating modulators, Column 3 `[MIXER|MACROS]` mode, Learn mode UX; `MidiMappingManager` Macro CC dispatch and `MacroOscBridge` OSC linkage.
- [x] **Build for ARM64 Linux**: Prebuilt `libimgui-java64.so` ARM64 native sourced from [`imgui-java-natives-linux-arm64`](https://github.com/greenjon/imgui-java-natives-linux-arm64) on GitHub free ARM runners; runtime dynamic loader hook (`NativeLibraryLoader.prepareImGuiNatives()`); JRE 17 `linux-aarch64` integration; `zipLinuxArm` packaging task; 5-platform CI smoke matrix restored and verified passing.

---

### Phase 7: Modular Video Rack & Macro Performance System (rack retired)
> **RETIRED (rack half only)**: Phases 5-9 below (the rack chassis UI itself — bay, faceplates, confidence micro-monitors, `Tab`-flip rear patching) shipped as described, then were **deleted from the codebase** and replaced by **Performance Mode** (`PerformanceMatrixPanel.kt`, 4×4 macro knob matrix, `F4`). The Macro engine (Phases 1-4) is unaffected and still shipping — it's now consumed by Performance Mode instead of the rack. This section is kept as historical implementation record; see the Summary Matrix above for current status.
>
> **Reference & Design Specs**:
> - [`docs/user_guide/macros_and_rack.md`](docs/user_guide/macros_and_rack.md) (Macro Controls & Performance Mode User Guide)
> - [`docs/archive/modular_video_rack_proposal.md`](docs/archive/modular_video_rack_proposal.md) (Modular Video Rack Architecture — retired design doc)
> **Status**: Core Complete (Phases 1-9 shipped) — all 6 rack-doc Open Questions decided and implemented 2026-09-16; rack chassis (Phases 5, 7, 8, 9) later retired and removed
> **Inspiration**: Hardware 19" studio racks, Propellerhead Reason, Eurorack, Ableton Device Racks

Evolving Liquid LSD from a fixed 2-deck mixer into a modular hardware-style video rack designed for tactile live performance:

- **Concept & Architecture**:
  - **Preset-as-Module**: Each deck (generator + all its FX, flattened into one unit — see rack doc §2.7) or transition is housed within an interchangeable rack unit with a standardized 19" bay width and quantized modular height ($1\text{U}, 2\text{U}, 3\text{U}, \dots$).
  - **Curated Performance Faceplates (80/20 Rule)**: Performers curate custom front panels exposing *only* high-impact live controls (knobs, sliders, toggles, and macros). Underlying automation, LFOs, audio-reactive envelopes, and fine math run silently in the background without cluttering the performance surface.
  - **Integrated Confidence Monitoring**: Every rack unit features an embedded real-time preview monitor rendering an offscreen FBO preview of that unit's output before downstream routing.
  - **Macro Controls**: Assignable multi-target macro knobs modulating multiple internal parameters simultaneously with customizable travel limits, inverted directions, and nonlinear response curves (linear, exponential, S-curve).
  - **Dual-Faced Architecture (Reason-Style `Tab` Flip)**:
    - *Front Face*: Clean tactile controls, meters, and micro-monitors.
    - *Rear Chassis*: Pressing `Tab` flips the entire rack around 180° to expose patch jacks (`Video In`, `Video Out`, `Mask / Sidechain In`, `CV Modulation In`).
  - **Normalled Signal Flow with Cable Overrides**:
    - By default, dropping units into a vertical stack normalizes connections top-to-bottom automatically without requiring manual patching.
    - Dragging virtual patch cables overrides the default flow for complex split-routing, parallel processing, external video I/O routing (Spout/Syphon/PipeWire), or intentional optical feedback loops.

- **Implementation Milestones** (single unified roadmap across both reference docs — the Macro system is a shared engine, not a rack-specific one; full detail in each doc's own Phase list):
  - [x] **Phase 1: Data Model & MacroEngine** *(macro doc §7)*: Core `MacroBinding`/`MacroControl`/`MacroBank`, instance-scoped bindings, zero-allocation frame evaluation.
  - [x] **Phase 2: Column 3 Macro UI Panel** *(macro doc §7)*: `[ MIXER | MACROS ]` toggle, 2x4 Knob grid + 4 Switches, single-deck preview.
  - [x] **Phase 3: Interactive Learn Mode & Inspector** *(macro doc §7)*: Global click-to-bind UX, pulsing highlights, field-ownership locking, and binding inspector drawer.
  - [x] **Phase 4: Serialization & MIDI/OSC Integration** *(macro doc §7)*: Bundled `.lsd`/`.lsdplay` DTOs, standalone `.knobpreset.json` export/import, `MidiMappingManager`/`MacroOscBridge` linkage.
  - [x] **Phase 5: Rack Chassis & Slot Layout System** *(rack doc §4)*: Standardized rack bay container, grid-based faceplate layout, unit header rails (power, bypass, solo, drag handle).
  - [x] **Phase 6: Per-Unit Macro Curation** *(rack doc §4)*: Each unit gets its own `MacroBank` (0-8 knobs/0-4 switches) scoped via `unitInstanceId`; curation UI picks which unit parameters occupy which slot. No freeform faceplate designer yet.
  - [x] **Phase 7: Embedded Confidence Micro-Monitors** *(rack doc §4)*: Lightweight texture blits rendering offscreen FBO passes directly onto unit faceplates.
  - [x] **Phase 8: Rear Panel & Virtual Patch Cables (`Tab` Flip)** *(rack doc §4)*: Dual-faced 180° flipped rear chassis view with physics-curved virtual patch cables, 1/4" hex phone jacks, LED status indicators, drag-to-patch interactive routing, and normalled override engine.
  - [x] **Phase 9: Unit Consolidation & Rack Layout Finalization** *(rack doc §4, implements all 6 decided Open Questions in rack doc §3)*: Merged `DeckGeneratorUnit` + up to 4 `ISFProcessorUnit`s into one new `DeckRackUnit` per deck with a flattened generator+FX parameter namespace (rack doc §2.7); deleted `FeedbackProcessorUnit`, `DeckGeneratorUnit`, and `ISFProcessorUnit` entirely (zero remaining construction sites); added a Deck BG column (`mixer.deckBG`, no new Deck plumbing needed); built the 3U `QueueStagingRackUnit` (Play Queue / BG Queue / Transition Staging) as a condensed-transport view onto the existing `PlayQueueManager`/`BgQueueManager`/`TransitionQueueManager` singletons; kept `MixerTransitionUnit` as its own Master unit; implemented confidence-monitor downscaling (240x135 preview `FBO` per unit via the existing `Renderer.rescale()`) and an FBO-count/GPU-memory telemetry readout in the menu bar. Off-screen GL culling was evaluated and deliberately not built (near-zero payoff given today's units are all cheap texture-ID reads, not real draw calls). Implemented 2026-09-16 — see rack doc §4 for full detail, including a double-update bug found and fixed along the way (Rack-mode `update()` was double-ticking `Deck`/`Mixer` state, since `Main.kt`'s main loop already ticks them unconditionally every frame regardless of workspace mode).
  - **Deferred, not scheduled**: true patchable transitions/splitters (rack doc Q2 Models B/C) and a freeform Faceplate Designer both depend on restructuring `Deck`/`Mixer` to accept externally patched textures — the same underlying capability as the patch-cable known issue below. Bundle these together into a future dedicated milestone once that foundational work is deliberately undertaken.
  - **Bumped to v1.1 (2026-09-25, see v1.1 Milestone 5)**: extend the generalized macro-learn "click to bind" affordance (landing on number boxes via `CustomRangeSlider`/`BeatDivisionSlider`) to dropdown/select controls (`ImGui.combo()`). Dropdowns have no shared wrapper component anywhere in the codebase today — every call site (`Lfo1Section`, `Lfo2Section`, `ValueParamSection`, `RackUnitMacroCuration`, etc.) owns its own inline `ImGui.combo` — so this needs a small shared combo wrapper built first. Do this once the number-box version has shipped and proven the interaction pattern.
  - **Known issues** (surfaced by a 2026-09-15 post-implementation correctness review of Phases 5-8; full rationale in `docs/archive/DECISIONS-history.md`):
    - ~~`FeedbackProcessorUnit`'s curated macro knobs (GAIN/DECAY/ZOOM/HUE) are bound to `Deck.fbGain`/`fbDecay`/etc. — legacy fields left over from before the ISF feedback migration that no shader reads anymore~~ — resolved by Phase 9: the unit type was deleted entirely rather than rewired; feedback already appears correctly as a normal FX-slot parameter in the merged `DeckRackUnit`'s flattened namespace.
    - Virtual patch-cable overrides only reroute pixels for genuinely custom/utility rack units. For the built-in Generator/Processor/Transition units that wrap the existing fixed `Deck`/`Mixer` pipeline, cables render and jacks light up but don't change actual signal routing — making that real requires restructuring `Deck`'s fixed FX-slot chain and `Mixer`'s hardcoded Deck A/B inputs to accept externally patched textures, which touches the master output path every workspace mode relies on. Tracked as the same deferred work as rack doc Q2 Models B/C above — not addressed by Phase 9.
    - ~~`MacroOscBridge` (Phase 4) has no OSC transport to connect to yet~~ — resolved: `OscMappingManager` now forwards `/macro/knob/N` and `/macro/switch/N` straight to `MacroOscBridge.handleOscMessage()`, and registers a `MacroFeedbackListener` that broadcasts value changes back out through `OscEngine`.
    - Minor hardening left undone: `MacroBank` shape isn't validated/normalized on deserialization (a hand-edited `.knobpreset.json` with the wrong knob/switch count won't crash today, but isn't guarded either), and `MidiMappingManager`'s `Macro/knob_N`/`Macro/switch_N` CC dispatch still scans the full mapping table per incoming MIDI event instead of using the pre-resolved flat-array pattern the rest of that file uses (bounded by MIDI event rate, not frame rate, so not urgent). Both remain low-priority fix-opportunistically items.
    - The Phase 9 monitor-downscaling change (a GL viewport-changing blit inside the per-unit faceplate draw call) was not visually verified on screen — confirmed via live app launches that the render loop runs cleanly with no new errors, but the actual rack monitors weren't screenshotted (Wayland session, no reachable screenshot tooling in that pass). Worth a manual look next time the app is run interactively.
  - **Obsolete (2026-09-25)** — `FxBank`, FX1/FX2/MFX and the `LIVE CONSOLE` tab were all deleted (deck and Master FX are plain `FxChain`s on their own Performance rows), so this question no longer applies. Original text: **Open design question (2026-09-20, raised during Live Performance Console planning)**: once the FX Bank simplification lands (see the Live Performance Console & FX Macro Integration row above — `FxBank.activeChainIndex` becomes a single shared "which chain is live" fact across Performance Mode and Classic Mode), should the *bank* focus (FX1 vs FX2 vs MFX) that the Performance Console's FX row is currently pointed at also become shared/global state, rather than console-local UI state? There's no equivalent "current bank" concept elsewhere today (Classic Mode shows FX1/FX2/MFX as three simultaneous subtabs, not a single focus), so for now it stays console-local. Revisit once `LIVE CONSOLE` has shipped and real usage patterns (e.g. multiple performance controllers, or a desire for OSC/MIDI-driven bank switching from outside the console) make the tradeoff concrete.

---

## Retired / Resolved Workstream Items

| Item | Original Source | Resolution Details |
| :--- | :--- | :--- |
| **OpenGL Error Profiling** | Historical `TODO.md` (`glGetDebugMessageLog`) | Implemented via `GLDebug.setupDebugCallback()` leveraging native `glDebugMessageCallback` on modern contexts; verified on OpenGL 3.3/4.3+. |
| **Frame Budget & Latency Metrics** | Historical `CONCERNS.md` | Implemented via `PerformanceStats` and rendered continuously in `MenuBar.kt` (DSP latency, FPS, frame time, CPU usage). |
| **Live Performance Console** | ROADMAP v1.1 row (2026-09-20) | Superseded 2026-09-25: `LIVE CONSOLE` tab and `FxBank` (FX1/FX2/MFX) deleted; the Performance matrix is now DECKS + MASTER tabs, with deck and Master FX as plain `FxChain`s edited on their own rows. |
| **Screen Capture Automation RFC** | `screen_capture_and_ui_iteration_proposal.md` | Core GPU readback completed via `PboReadbackPipeline`; headless CLI screenshot automation deferred to build infrastructure as needed. |
