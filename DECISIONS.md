# DECISIONS

Decisions that still hold, with the reason, so they are not re-litigated or silently undone. Current as of 2026-10-04.

**What belongs here**: an invariant the code relies on, a trade-off someone accepted, or an alternative that was rejected and why. **What does not**: layout tweaks, tuning, renames, "added X" (those go in `RELEASE_NOTES.md` / `docs/release_notes.md`), and plans (`.planning/`). When a topic changes, rewrite its entry here instead of adding a new one on top. When a decision is reversed, delete the entry and, if the reversal itself is worth remembering, add a line to "Removed, do not reintroduce".

The previous 2,700-line log is frozen in `docs/archive/DECISIONS-history.md`. Most of it describes code that no longer exists.

Contents: 1 Foundations · 2 Rendering and shaders · 3 One path for every change (Ops) · 4 FX model · 5 Macros and parameter ownership · 6 Controllers and Perform pages · 7 Library, browser and Edit bay · 8 UI conventions · 9 Audio, CV and modulation · 10 Web broadcast and touch console · 11 Build, release, platforms · 12 Removed, do not reintroduce

---

# 1. Foundations

## Stack and threading rules
- **Kotlin/JVM, LWJGL 3 (GLFW + OpenGL 3.3), Dear ImGui via `imgui-java`, JNAJack** for JACK/PipeWire audio. Java Sound (`TargetDataLine`) is the fallback backend on every OS (macOS, Windows, JACK-less Linux). JACK is preferred on Linux for latency and patchbay routing.
- **Thread 0 owns GLFW and all OpenGL.** Off-thread GL is a driver fault or a segfault. Anything that creates or disposes GL objects from another thread (shader compile after a background scan, filter dispose, preset apply) queues a task that `Main` drains on thread 0 once per frame (`VisualSourceRegistry.processPendingGlTasks`, `DeckOps` / `FxOps` / `TransitionOps` drains). macOS needs `-XstartOnFirstThread` for the same reason.
- **The audio callback allocates nothing and never blocks**: no locks, no logging, no I/O. Pre-allocate every buffer. Audio-to-render data moves through `@Volatile` primitives, the single-writer `CvHistoryBuffer` and SPSC queues (`SpscQueue`, `RealtimeRecorder`). A race in a visualization buffer costs one glitched frame; a lock costs an xrun by priority inversion.
- **ZGC with a 2 ms pause target** (`-XX:+UseZGC -XX:MaxGCPauseMillis=2`) because a 128-frame buffer at 48 kHz is 2.6 ms.
- **Zero allocation on the per-frame paths too**: MIDI/CV routing, controller input, Perform draw. Resolve parameter paths into flat arrays when mappings change, not per event (`ResolvedMidiBinding`, `ParameterResolver.pathCache`, `ControllerRuntime` primitive arrays); index loops instead of iterators; `ImInt`/`ImString` are fields, never per-frame locals.
- **ImGui native memory is not GC-managed.** Keep `ImString`/`ImInt` buffers as fields; keep a JVM reference to font data ImGui points at; `.destroy()` styles and font configs. Per-frame allocation leaks native memory; a dropped font reference segfaults.
- **Preset I/O runs on a daemon executor (`presetIoExecutor`)** and results are applied on the next frame by the owning `Ops` drain, never on the IO thread.
- **Layering**: `midi/` and `control/` import nothing from `ui/` or `rendering/` (`LayerDependencyTest` enforces it). They talk to the app through small interfaces (`MidiLearnSink`, `MidiEnabledSource`, `CrossfadeControl`, `ProfileLearner`) installed from `Main`.

## Naming and compatibility
- **Preset, not patch.** The user storage root is `library/` (`library/presets`, `playlists`, `sources`, `controllers`, `perform_pages`, ...). A "preset" is a saved parameter snapshot.
- **Beta: no backwards-compatibility shims.** No `@SerialName` aliases, path fallbacks or parameter-name translation tables. Old files load through `ignoreUnknownKeys` and unknown keys are dropped. The one exception: decided 2026-10-04 to change what is persisted (modulator ids, schema `version`) before release, because after release every format needs a permanent migration. From v1.0 on, format changes need a migration.
- **First run starts with four blank decks** (`Deck.isEmpty = true`, `PresetManager.startEmpty` when `last_session.json` is missing or fails to load). No hard-coded startup generator.
- **Never multiply UI sizes by the OS DPI scale.** The ImGui GLFW/GL3 backends already apply the window content scale; doing it again scales twice (4x on a 2x display).

---

# 2. Rendering and shaders

## Everything is ISF; there are no hard-wired effect or mixer shaders
- Generators, filters and transitions are Interactive Shader Format v2.0 `.fs` files with the JSON header embedded in the shader. No proprietary `meta.json` + `shader.frag` sources. Hard-wired feedback, bloom, 3D projection and mixer blend modes were all replaced by ISF equivalents; the mixer shader only composites (transition output + Deck BG + channel levels + master level).
- **Role is detected from the header, not the folder**: 0 image inputs = generator, 1 = FX filter, 2+ or a `progress` input = transition. Subfolders are kept as hierarchy. Registries (`VisualSourceRegistry`, `ISFFilterRegistry`, `ISFTransitionRegistry`) only take their own role.
- **Universal ingestion**: Shadertoy (`mainImage`) and GLSLSandbox shaders are wrapped with a bridge shim and a synthesized header so they catalogue like ISF. Missing `displayName` falls back to the id (an empty label trips an ImGui ID assertion).
- **ISF inputs become `ModulatableParameter`s automatically**; `color` and `point2D` split into components. Multi-pass (`PASSES`, `PERSISTENT` ping-pong targets) is supported.
- **Library locations** (`ISFDirectoryManager`): platform-standard ISF folders plus user folders, expanded env vars, `Missing` folders kept in config, recursive async scan, file watcher with 250 ms debounce. A duplicate shader id resolves by origin priority Custom > UserStandard > SystemStandard > BuiltIn.
- **Curated stock set**: 8 generators (`mandala`, `dynamic_spiral`, `icosa_h3`, `domain_warp_fluid`, `gyroid_hyperspace`, `celestial_engine`, `hyper_slice`, `chladni_cymatics`) and 8 transitions ("Elite 8": `linear_crossfade`, `luminous_flash`, `film_burn`, `noise_dissolve`, `cyber_datamosh`, `kinetic_zoom`, `liquid_displacement`, `vortex_swirl`). The old blend-mode and wipe transitions were deliberately removed; do not re-add them as stock. Stock sources are bundled in the jar (`default_sources/`) and self-heal into `library/sources` if missing.
- **Mandala is an ordinary `DynamicVisualSource`**, with no `if (source is Mandala)` special cases in serialization, UI or MIDI paths. Its old background parameters were extracted into the separate `Colors` generator; backgrounds come from Deck BG.

## Four decks and the compositing order
- Decks are **A, B, BG, PV**. BG is composited under the crossfaded A/B foreground (`rgb = fg.rgb + bg.rgb * (1 - fg.a)`). PV is the preview deck and never affects master output.
- **View pipeline**: 2D sources go through `view2d.frag` (zoom, Z-rotate, edge wrap Mirror/Repeat/Clamp/Border; Mirror is the default so zooming out never leaves a floating rectangle). The 3D projection modes apply only to 2D sources; native 3D generators (`is3D`) bypass them.
- **Master level is one value** (`Mixer.masterLevel`). There is no separate master alpha, mixer bloom or legacy mix mode.

## Source and FX state
- **FX is session state, never part of a preset.** `DeckPresetDto` has no FX fields; loading or saving a `.lsd` does not touch a deck's chain. FX chains persist in the session (`MixerDto`: master chain, per-deck chains, and their source file paths). *Why*: FX queues and playlists assume "FX is independent of what's playing"; embedding it made a preset load clobber live FX and any FX tweak dirty the preset.
- **Randomization is a continuous morph, not a one-shot** (`MorphState`): two snapshots S0/S1, a 0-1 driver, flip-flop latches with hysteresis so noisy CV near the extremes can't re-roll twice, wrap-around promotion for sawtooth drivers, shortest-path interpolation for angles and hues. Only parameters/modulator fields whose `randomize*` flag is on take part. The five `Mixer/rand*` controllers are themselves non-randomizable (`isRandomizeDisabled`).

## External video
- Spout, Syphon and PipeWire feeds are `ExternalVideoSource`s. **They can't be saved as presets** (no procedural parameters, ephemeral handles, "ghost" presets load black): the Save button is disabled with a tooltip, Ctrl+S is ignored, `isDeckDirty` is always false, and the controller/repository save paths reject them.
- Linux uses PipeWire 0.3 over JNA with DMA-BUF export and a shared-memory fallback; readback uses a PBO pipeline so the GL thread never stalls. Audio device enumeration filters playback-only sinks and caches the list, to avoid ALSA/WirePlumber link-negotiation races; JACK mode skips JavaSound scans entirely.

---

# 3. One path for every change

Deck loads, FX changes and transition changes each have exactly one mutation path that queues the change and applies it on the GL thread. UI, queues, MIDI and session code never call the underlying setters directly. The FX path (`FxOps`) is in section 4.

## Deck Preset Bookkeeping Is Written Only Through `PresetManager.setActive` / `clearActive`
- **Decision**: `activePresetA/B/BG/PV` and `cachedDtoA/B/BG/PV` have private setters. `setActive(slot, name, dto)` stores both and snapshots the slot's macro bank as the dirty baseline (`bankBaseline`); `DeckOps.isDirty` compares against it. Session restore calls `setActive` *after* the macro banks are registered, otherwise the baseline would be taken from the previous run's banks.
- **Why**: session restore wrote the cached DTO directly, so no baseline existed and bank edits never made a restored deck dirty (review section 7.2, defect 4). `bankBaseline` is tied to the DTO instance, so a stale baseline is never compared, but a bypass fails silently, which is why the setters are now closed.
- **Unnamed decks**: the unsaved-changes prompt saves a deck with no preset name under the first free `Untitled_<Deck>[_n]` name instead of overwriting `Untitled_<Deck>`.

## All Deck Changes Go Through `DeckOps` (`presets/DeckOps.kt`)
- **Decision**: what a deck holds (bare source, preset file, eject, copy/move/swap) changes only through `DeckOps.request(slot, DeckChange, LoadOrigin)`; UI and queue code never call `Deck.applyDto`, `deck.source =` or `PresetManager.setActive` for a load. Requests are guarded and queued on the calling thread (preset files are read on `presetIoExecutor`); `DeckOps.drainOnGlThread` applies them once per frame, in `Main` next to `FxOps` and `TransitionOps`.
- **Why**: ten-plus call sites each remembered a different subset of dirty guard, undo, macro-bank install, toast and bookkeeping (review §7.2 D1-D6, D11), and four copies of the dirty policy had drifted.
- **Dirty policy**: `MANUAL` follows the new `manualLoadDirtyBehavior` (PROMPT default, DISCARD, AUTO_SAVE); `QUEUE` (queues, AutoVJ, session restore) follows `autoVjDirtyBehavior`, where SKIP drops the load without prompting. A clean deck never prompts. A request for the generator the deck already runs is dropped (unless `force` or the deck is empty or external video).
- **Dirty baseline**: `PresetManager.setActive` snapshots the macro bank's labels and bindings next to the cached DTO (knob values excluded: they move while performing). The snapshot is only compared when it was taken for the current cached DTO (identity check), so code that still assigns `cachedDtoX` directly (`SessionSerializer`) can't cause a false dirty. A source change sets a baseline DTO with a null preset name, so a fresh generator can be dirty (D2).
- **Undo**: pushed at drain time, before the change, for MANUAL `Source` and `Preset` only. A source change restores the old source instance; a preset load rebuilds the deck from its DTO. Eject and copy/move/swap push none because they can reset the deck's FX chain, which isn't captured. Undo between request and drain is a no-op for that load (one frame).
- **Macro bank**: preset with a bank installs it; without one gets `GeneratorDefaults.installDefaultBank`; copy/move/swap install the source deck's bank remapped to the target's path (swap snapshots both first); move clears the source's bank.
- **Injected hooks** (`mixerProvider`, `prompt`, `undoSink`, `postApply`, wired in `UIManager`) keep `presets/` from importing UI state. With nothing wired (tests, headless) the guard is skipped or proceeds.
- **Rejected**: keeping the four per-deck preset queues behind `DeckOps` (they added nothing once the drain is one loop).
- **Return value**: `request` returns `false` when dropped; queue managers leave their position unchanged. The queue managers' `handleDirtyDeck` and the `loadDeckPresetAsync` shim are deleted.
- **Completion callback**: `request(..., onResult)` fires once with true after the drain applied the change, false if dropped, prompt-cancelled, unreadable or failed. Callers that must act on the loaded deck (video export from a snapshot) start from it, never right after `request`. A newer prompt cancels an older pending one.

## All Transition Changes Go Through `TransitionOps` (GL-thread queue), Like `FxOps` (`presets/TransitionOps.kt`)
- **Decision**: `Mixer.setTransition` / `applyTransitionPreset` dispose and create ISF GL filters, so UI and queue code never call them directly. `TransitionOps.setStock / applyPreset / loadPreset / applyItem` queue the change; `Main.kt` drains it once per frame next to `FxOps.drainOnGlThread`. Session restore at startup still calls the mixer directly (main thread, before rendering).
- **Why**: five copies of the `.lsdtrans` drop handler applied the preset in `thenAccept` on `presetIoExecutor` (a GL-thread violation), and the queue manager read the file synchronously on the UI thread. Review: `docs/developer/ui_interaction_architecture_review.md` section 7 (defect 12).
- **Failure**: an unreadable preset toasts and falls back to the stock transition named after the file, as the queue manager already did silently.
- **Next**: `DeckOps` is the same idea for deck loads (`.planning/deck-transition-ops-plan.md`). `DeckSlot` and the `PresetManager` per-slot accessors are its groundwork; the legacy `activePresetA/B/BG/PV` fields remain the backing store and are migrated only where touched.

## Source Change Reports Replaced Macro Bindings via a Toast (`GeneratorDefaults.applyToDeck`, `ToastOverlay`)
- **Decision**: `applyToDeck` returns true when the bank held bindings and the default installed different ones; `DeckSourcePicker.swapSource` shows a `ToastOverlay` message in that case. Bindings are not carried over by parameter name (names such as Scale/Speed/Depth mean different things per generator).
- **Undo (follow-up)**: `ParametersUndo` used to snapshot modulators only, and `swapSource` pushed its snapshot *after* the swap, so Ctrl+Z after a source change did nothing (the docs claiming otherwise were wrong). `ParametersUndoSnapshot` now has an optional `restore` lambda run before modulators are restored; `swapSource` pushes before the swap with a lambda that restores the old source, `isEmpty`, the deck's macro bank and the active-preset marker (`DeckLifecycleManager.captureActivePreset`). The toast therefore says "Ctrl+Z to undo". Not covered: other macro-bank edits (binding edits, renames) are still not undoable.
- **Open**: restoring the replaced bank (stash it in `swapSource`, offer a restore action) is the follow-up if the toast proves insufficient. `MacroLearnState.statusBanner` (used for "Added target...") has no on-screen reader in `ui/`; **Done**: `UIManager` passes `MacroLearnState.getActiveStatus()` to `ToastOverlay.draw` as a fallback (macro/ cannot depend on ui/, so the UI pulls rather than macro pushing).

## DeckOps Requests Are Ordered Per Slot; Closing Video Export Cancels Its Pending Start; Queue Accept Plays (`DeckOps.requestSeq`, `VideoExportModal.exportToken`, `LibraryNavigation.accept`)
- **Decision**: every accepted `DeckOps.request` gets a per-slot sequence number; `drainOnGlThread` drops an op older than the last applied one (`onResult(false)`). The number is taken when the request proceeds (after the dirty prompt), so a preset whose file read finishes late can't land after a newer change.
- **Video Export**: closing the window bumps a token so an export waiting on its preset load or the unsaved-changes prompt doesn't start.
- **Queue accept**: MIDI accept on an A/B queue item calls `PlayQueueManager.playIndex`, same as double-click (index, fade, transition queue). Playlist accept stays a plain load. Decided 2026-10-04.
- **Undo (item 17)**: Eject and Copy/Move/Swap stay non-undoable (FX chain isn't captured); the Preferences tooltip and the "Don't ask again" label now say only preset/generator loads undo.


---

# 4. FX model

## Every FX chain is a plain `FxChain`; every change goes through `FxOps` (`presets/FxOps.kt`)
- **Decision**: each deck (A, B, BG, PV) and Master has one `FxChain` of 3 slots, rendered by the same `renderFxChainPass`, with macro paths `Deck A/FX/...` and `Master/FX/...`. There are no FX banks, no alternative chains per row and no shared send banks. `FxOps` is the single mutation path (load/apply/clear chain, set/load/apply/clear slot, queue and playlist `applyItem`); each op is queued and `FxOps.drainOnGlThread` applies it once per frame, then re-syncs that chain's knobs via `FxMacroSync`.
- **Why**: async loads used to apply inside `thenAccept` on the IO thread, disposing GL framebuffers off the GL thread and swapping `FxChain.slots` under the renderer. Routing through one queue fixes that by construction and gives swap-dip, dirty tracking and undo one place to hook in.
- **Failures** log and toast (`FxOps.reportFailure`). A single FX with no named slot takes the first vacant slot; when full, browser double-click and audition latch ask which slot to overwrite and a badge drop toasts. Queues and playlists keep the deterministic rule (`.lsdfx` = slot 1 of an empty chain), because their target must not depend on live state.
- **Targeting**: FX goes to the live deck (`Mixer.liveDeck`, crossfade <= 0 = A); presets and generators go to the inactive deck (`Mixer.inactiveDeck`). FX drops go to slots, chain headers and deck badges; the monitor takes presets and generators only.
- **Swap dip**: replacing a slot, stepping, or loading a chain fades out, swaps, fades in (`fxSwapFadeMs`, default 150, 0 = hard cut).
- **Dirty means structure only**: `FxChain.computeIsDirty` compares the sequence of filter ids per slot against the baseline. Super Knob sweeps, Metaknob moves and parameter tweaks never mark a chain dirty, because they are live performance gestures and a dirty dot on every sweep is a false alarm. It compares ids directly so the 250 ms poll allocates nothing.
- **Stepping**: the deck FX row's arrows step the live FX queues (A/B queue, BG queue), targeting the clicked deck explicitly, not the crossfader side. PV and Master have no rotating queue and no arrows. Slot stepping uses the user's FX shortlist (stock filters only; favorites are shared between the Library and the picker), falling back to the effect's category.

## Two-tier performance knobs: Super Knob + per-slot Metaknob
- **Decision**: an FX bank has 4 knobs. Group mode: knob 1 = chain Super Knob, knobs 2-4 = the three slot Metaknobs. Focus mode (one slot focused): knob 1 = that slot's Metaknob, knobs 2-4 = the effect's top parameters on the current page (3 per page); the slot's Dry/Wet is a slider on the chain header, not a knob. The mapping is fixed and `FxMacroSync` rewrites all four knobs on every sync; there is no ownership rule, resync menu or hand-retargeting. Sessions with hand-retargeted FX knobs lose those bindings on load (accepted).
- **Metaknob binding is resolved per shader by `ISFAutoBindEngine`**, most specific first: user override keyed by shader content hash (SHA-256, so it survives file moves) in `library/isf_overrides/`; a hand-curated table for bundled filters; then a generic fallback. The Metaknob starts at the position that reproduces the bound parameter's authored default, so loading a filter never changes its look. Overrides are saved only by an explicit "Save as Default for <Filter>" (never implicitly), and "Reset to Factory Default" clears them.
- **FX chains link to the Super Knob per slot, and the starting state is a preference** (Preferences > "Default FX chain linking": Auto / Linked / Unlinked; Auto = unlinked while a knob controller such as the Twister is connected, so each slot Metaknob has its own knob; no controller = linked, so one Super Knob drives everything). It only applies to a chain with no saved link flags (new, reset, or a preset that omits `slotSuperKnobLink`; shipped `defaults/fx_chains` omit it); saved flags always restore as saved. `FxChain.defaultLinked` is the provider (tests fix it to `true`). A chain-wide **Chain Link** toggle sits beside the Super Knob (Perform row side button, Macro Strip header) and on Shift + tap of knob 1 in group mode: all linked -> unlink all, otherwise link all, going through `setSlotLinked` so linking arms soft takeover. Empty slots don't count toward "all/partial".
- **Super Knob links use soft takeover**: a linked slot's Metaknob only starts following once the Super Knob reaches (within ~4%) the Metaknob's current value. In Focus mode the Super Knob does not propagate.
- **Multi-parameter Metaknob links** (`FxMetaBinding`) and per-binding **link modes** (knob-travel windowing, Mixxx-style) live in the macro model; the ownership order they obey is in section 5. A binding's link mode windows the knob travel (`MacroCurve.window`) *before* the curve and range mapping run.
- **FX banks are read-only to every editor but `FxMacroSync`** (see section 5).

## Generator and FX defaults
- A generator's starting bank resolves in three tiers: user file `library/generator_defaults/<sourceId>.json`, hand-curated 4-knob bindings for the stock generators, then a heuristic for third-party ISF shaders (skip discrete selectors, prefer continuous parameters, exponential curves for time/frequency). Swapping the source replaces the deck's bank wholesale, like a preset load, with `Deck/` paths remapped to the resident deck. `MacroCurve.inverse` positions each knob so installing a default causes no visible jump. MIDI mappings are stripped from saved defaults to avoid duplicate CCs across decks. Bindings are not carried over by parameter name (Scale/Speed/Depth mean different things per generator); a toast reports replaced bindings.

---

# 5. Macros and parameter ownership

## Macro-Bank Edits Are Undone via a Per-Frame Change Tracker, Not Per-Widget Hooks (`MacroUndoTracker`)
- **Decision**: `MacroUndoTracker.update` (called from `UIManager` each frame) hashes the label + bindings of every Deck/Master/Global knob; the first change of a gesture pushes a `ParametersUndoSnapshot` whose `restore` lambda puts the previous copy back. A gesture ends when the mouse is up and nothing changed that frame, so a Min/Max drag is one step.
- **Why not instrument the widgets**: the edits are made in ~15 places across `MacroBindingEditor` (strip and Properties), `PerformanceMacroStrip`, `PropertiesPanel`, `MacroLearnState` and `CustomRangeSlider`, each a drag/click/text input; a hook per site would miss new ones and need per-widget drag coalescing.
- **Not user edits**: `MacroEngine.bankReplaceEpoch` (bumped by register/unregister and `installBankForDeck`) makes the tracker re-baseline silently; the source-swap undo step restores via `noteBankReplaced()` too. Knob `value` is ignored; FX banks are skipped (`FxMacroSync` rewrites them).
- **Known edge**: a wholesale load and a hand edit in the same frame count as a load (the edit is not separately undoable). Importing into a deck records its step explicitly (`recordBeforeBulkEdit`) because the install bumps the epoch.

## Learn Means Hardware; Macro Knobs Add Targets (`MacroLearnState`, UI strings)

- **Decision**: "MIDI Learn" / "OSC Learn" keep "Learn" and use "map". Macro knob to parameter is **Add Target** and each assignment is a **target**. "Bind/bound/binding" is removed from user-facing text.
- **Rationale**: "Learn" covered three different jobs (MIDI, OSC, macro knobs), and "Link" was already taken (link mode, Ableton Link).
- **Alternatives rejected**: "Assign" (generic), "Link" (collision), keeping "Bind" for macros (still collides with MIDI/OSC "bind").
- **Consequences**: text and docs only; code identifiers (`MacroBinding`, `MacroLearnState`, `ProfileBindingEdit`) keep their names. `TooltipHelper` recognizes `Target:` / `No target` and `MIDI Learn` / `OSC Learn` right-click prefixes.

## Column 3 MACROS Tab Removed; Macro Binding Is Edited in the Edit Row (`PerformanceMacroStrip`, `MacroBindingEditor`)

- **Decision**: Column 3 always draws the Mixer. Binding editing moved to an Edit-row strip (`PerformanceMacroStrip`) plus the Properties editor; the GLOBAL bank uses a guest strip on the open Edit row. `Column3HeaderToggle`, `MacroPanel`, `MacroBindingInspector`, `UITheme.Column3Mode` and the GLB tab pin in `ParametersState` were deleted.
- **Rationale**: the MACROS tab was a second place to find the same knobs, and arming Learn there meant navigating away from the row being edited. The strip keeps selection, Learn and bindings on the row itself.
- **Follow-up**: the Clock row's 4 Global knobs were removed (unbindable from Perform). `MacroEngine.GLOBAL` stays registered with 0 knobs so the v1.1 "global knobs row" (rows of free knobs, possibly several Global banks) needs no migration; the GUEST strip code is dormant until then.
- **Alternatives rejected**: keeping MACROS as an optional tab (two editing surfaces to keep in sync); a modal inspector (hides the parameter grid Learn needs to click).
- **Consequences**: Export/Import Macro Bank (`.knobpreset.json`) is exposed in the strip's kebab menu via `ImGuiFileBrowser`; the old inspector never had it. The Perform-view Clock row cannot arm GLOBAL Learn (its knobs cannot be selected there). `PerformanceUiContext.focusDeepEditTab` is what remains of the old panel navigation.

## Modulator Targets Are Addressed by Stable ID, Not List Position (`MacroBinding.modulatorId`, `:mod/<id>/` paths)
- **Decision**: Macro bindings and MIDI/OSC `:mod/` paths identify a modulator by `CvModulator.id`, never by its index in `param.modulators`. Decided 2026-10-04, before release, while no saved files exist outside the developer's: after release every preset, session and bank would need a permanent migration.
- **Migration**: `MacroBinding.modulatorIndex` stays as a nullable legacy read field. `MacroEngine.resolveControls` pins it to the modulator's id on first resolve (modulators in old files get an id on load) and clears it, so the next save writes ids. A numeric `:mod/<n>/` path segment is still read as a position (`ModulatorPropertyAccessor.findByPathRef`) but is never written by new code.
- **Missing id**: the binding is skipped and logged, not dropped, so undo that restores the modulator restores the binding.
- **Not decided here**: D9 (editors reading the resolved cache, so disabled bindings vanish) is a separate change.
- **Rationale**: removing, resetting or undoing modulators shifted indices, so bindings silently died or drove the wrong modulator.

## Editors List Disabled Macro Bindings; Lock Queries Stay Enabled-Only (`MacroEngine.findBindingInfos(includeDisabled)`)
- **Decision**: Two kinds of macro-binding query. Lock/indicator/arc queries (`findBindingsTargeting`, `findPrimaryBindingInfo`, `baseBindingInfos`) read the resolved cache: enabled and resolvable only, allocation-free, per-frame. Editor lists use `findBindingInfos(..., includeDisabled = true)`, which scans the controls' own `bindings`, so an unchecked target stays listed and re-enableable.
- **Rationale**: a disabled binding is not locking anything, but it is still the user's data; hiding it from the editor that just disabled it made re-enabling impossible there (D9). Decided 2026-10-04.

## A Macro-Driven Value Is Locked Against Every Other Writer; "Locked" Is Per Target Kind (`MacroEngine.lockingBindingInfo`)
- **Decision**: `MacroEngine.tick` rewrites a bound value every frame, so any other writer (slider, MIDI, OSC) is dead while an enabled binding drives it. The UI therefore treats the value as locked for all of them: sliders are read-only, and Learn MIDI/OSC is disabled with "locked by K#". Existing mappings are kept, inert, and work again when the binding is released. Decided 2026-10-04 (D8, D10).
- **Target kind**: a base-value binding locks only the base value; a modulator-property binding locks only that property (`findBaseBindingInfo`, `lockingBindingInfo`). `findPrimaryBindingInfo(null, paramKey)` with no modulator or property still matches any binding on the parameter and must not be used to answer "is the base value locked?".
- **Alternative rejected**: making the engine yield to MIDI/OSC (last-writer-wins) would need per-binding takeover state and make the knob unreliable on stage.

## Parameter Ownership Order: Macro Target > Metaknob Link > Direct Edit/MIDI/OSC; Modulators Always Add On Top (`MacroEngine.isMappingTargetLocked`, `ModulatableParameter.metaDrivenBy`)
- **Decision**: a macro-driven value suspends MIDI/OSC mappings on it at runtime (they resume when the binding is released), instead of being overwritten after the fact. An enabled ISF meta-binding (what the Super Knob drives through the slot Metaknob) owns its uniform: `ISFFilter` sets `ModulatableParameter.metaDrivenBy` every frame, `MacroEngine.tick` skips such parameters, `MacroLearnState.bindTarget` refuses them, and MIDI/OSC mappings on them are suspended. Decided 2026-10-04 (backlog items 13, 20 and 5).
- **Why**: the frame order was OSC/MIDI, then macro tick, then deck update with the meta-binding last, so the later writer silently won and the lock UI contradicted what happened. Soft takeover on a suspended mapping also chased the macro value and showed "Awaiting Pickup" forever.
- **Lock coverage (item 20)**: every editor of a locked value now locks, including the Min/Max range sliders and boxes (per bound field) and the special pill/combo widgets in `ValueParamSection`.
- **Not done**: sliders on a Metaknob-owned uniform do not yet show a lock badge (the lock UI is macro-only).
- **Alternative rejected**: letting the macro win over the Metaknob (suspending the meta-binding) would need per-binding state in presets and breaks the "you never get a dead Metaknob" guarantee.

## FX-Bank Knobs Are Read-Only Outside FxMacroSync; Master/Transition/FX-Send Knobs Are Path-Scoped (`MacroLearnState.acceptsTarget`, `FxMacroSync.isFxBank`)
- **Decision**: `FxMacroSync` owns every FX-bank binding and rewrites it from the chain, so no other editor may add, edit or delete one: `acceptsTarget` rejects FX banks, and Properties, the base-value panel and the slider popup show a read-only line. MASTER and TRANS knobs accept `Mixer/...` only, FX_SENDS accepts `.../FXChain/...` only, GLOBAL stays unscoped. Bank import remaps only deck-rooted paths (everything except `Mixer`, `Master`, `Global`, `Macro`) and validates after remapping. Decided 2026-10-04 (backlog items 8, 22, 23, 7).
- **Selection**: one `MacroLearnState.selectedControlId` drives the strip, the card highlight and the Learn button; the per-module `selectedRackMacroId` map was dropped. Consequence: one highlighted knob across all expanded modules.
- **Not changed**: `sectionFor` stays null for MASTER/TRANS/FX_SENDS, so Learn is not disarmed by moving between Mixer sub-tabs. `Learn` can still be armed on an FX knob (the UI hides the button); `bindTarget` refuses it.


---

# 6. Controllers and Perform pages

## Command registry + declarative controller profiles (`control/`, `resources/controllers/`)
- **Decision**: controllers are a stable-id `CommandRegistry` plus declarative JSON profiles (built-ins in the jar, user files in `library/controllers/`); the Midi Fighter Twister is the first profile. Bindings are keyed by logical input id (`knob.3`), never raw CC, so one input keeps one id when the device switches hardware banks (a bank adds a CC stride). `MidiEvent` carries its source `deviceId`. Bindings take modifier prefixes (`shift+knob.3.press`), per-bank overrides and `*` / `{n}` wildcards; the most specific wins. Legacy learned mappings on an exact channel/CC beat the profile.
- **Why**: a Twister-only hard-coded mapping would be redone for the next device. Declarative JSON, no scripting, covers most grid/knob controllers and can be copied and edited like a Mixxx mapping.
- **Rejected**: Mixxx-style JS scripting (heavy to sandbox, not needed for v1.0); keying by raw CC (breaks on bank change).

## Control pages: the 16 knobs are the Perform grid
- **Decision**: a controller's 16 knobs are the visible Perform rows flattened row-major (or the open row in Edit), resolved through the same `PerfRows` + `PerfKnobResolver` the panel draws with, so screen and hardware cannot disagree. Hardware bank buttons select a Perform *page* (`banks.pages`); the UI page stays the single source of truth, so clicking a tab on screen retargets the encoders, and `ControllerFeedback.syncActiveBank` sends a hardware bank switch when the UI page changes.
- **Gestures**: tap = FX slot bypass / reset to default (SRC and mix knobs reset to 0.5, like middle-click); shift + tap = focus slot, leave focus, or next parameter page; hold the knob switch and turn = fine (x0.1). The hold is tracked in the app because the Twister sends identical turn messages either way. A press's release goes to the command that received its press, so releasing shift first cannot break a tap.
- **Encoders** are relative binary offset (no end stops, no resync); absolute mode is read as the change since the previous value. Acceleration (up to 4x) and `step` are per input.

## Ring/LED feedback
- **Decision**: the screen is the only truth for relative encoders, so the app mirrors it to the device: ring = the encoder's CC echoed on its channel, LED = the same CC on a second channel with a hue-wheel value (all of it profile data, `output.knobs`). Dark LED = nothing to control (empty/bypassed slot, blank parameter); the ring still shows the value. LED colour comes from the row accent; Master and Global have dedicated hues (`LED_MASTER`, `LED_GLOBAL`) because their on-screen greys have none. `MeterType` (MONOPOLAR / BIPOLAR / ENDLESS) is resolved from the bound parameter so centre-detent values show correctly.
- **The Twister drops messages that arrive in a burst** (a 32-message bank rewrite landed as 1-7 rings). Output is therefore paced and coalesced (`CcQueue` keeps one pending value per (channel, CC); the writer spaces sends by `output.minIntervalMs`, default 2). Lights are written to the *active* bank only, and the whole bank is rewritten when it becomes active. Each bank uses its own CC numbers (knob 1 on bank 2 is CC 16); the stock 4-bank firmware works with that.
- **Rejected**: writing all four banks at once (burst loss), absolute encoders with device-local ring movement (fine mode and acceleration make app value and ring diverge), an echo guard (would swallow real ticks near centre), periodic heartbeat/settle rewrites (tried during firmware experiments, found unnecessary, removed).
- **Known limit**: an RGB LED cannot express brightness, so FX Wet/Dry's colour looks like Deck A's on hardware. A firmware update (XT) made banks 2-4 stop updating live; the app's writes were verified correct, so that is firmware-side.

## Navigation from the controller
- **Decision**: free side buttons are commands `nav.button.1..3` and `.alt` (shift); a `NavSurface` on `CommandContext` decides what they mean from the current UI context (Perform, Library, or a hosted Edit-bay browse), so the profile stays static. While browsing, knob 1 browses (4 encoder ticks per item) and taps accepts; **stepping never applies, accept does**; knobs 2-16 are inert so a brush can't change a live parameter while the screen shows a list. Esc and the controller back button call the same `NavigationSurface.back()`.
- **Optional second Twister profile with all six side buttons as CC** (`midi-fighter-twister-6btn`): with the middle buttons on `CC Hold` the device sends six CCs per bank (`8 + 6n .. 13 + 6n`), which renumbers the corner buttons on banks 2-4, so it can't share the default profile (stride 4). The app, not the firmware, steps banks: `controller.bank_next/prev` show the neighbouring page and `ControllerFeedback.syncActiveBank` (already used when you click a tab) sends the bank change (`ch4 CC n`, value 127, verified on hardware). Right-middle = bank (shift = previous), left-middle = Chain Link. Both built-ins match the same device, so the 6-button one is selected by copying it to `library/controllers/` (a user profile wins); a proper profile picker is MIDI phase 5.
- **FX queue transport commands** (`fx.queue_next/prev`, `fx.bg_queue_next/prev`) ship unbound: every Twister button already has a meaning, so a default would steal one.
- **Rejected**: applying on every cursor step (loads a generator per detent; the dirty-deck prompt is an ImGui modal), context in the profile (it cannot see UI state), a dedicated row-cursor mode.
- The hosted-pane controller behavior is in section 7.

## Perform pages are data (`ui/PerfPageStore.kt`, `PerfRows.kt`, `resources/perform_pages/`)
- **Decision**: a page is `PerfPageDef(id, name, tooltip, 4 x RowPlacement(catalogId))`. `PerfRows.CATALOG` maps stable row ids (`deck.<tag>.srcfx`, `deck.<tag>.src`, `deck.<tag>.fx`, `master`, `master.mix`, `master.fx`, `trans`, `wetdry`, `global`) to row descriptors. Built-in pages: `decks`, `master`, `ab`, `bgpv`, `mixer`; the Twister's four banks are `perform.ab`, `perform.bgpv`, `perform.mixer`, `perform.master`. User pages live in `library/perform_pages/` and override a built-in by id, same convention as controller profiles. A page that isn't exactly 4 rows of known ids is skipped with a log line. Preferences store only the active page id.
- **Pinned rows** (`pinnedMode`: SRC/FX for decks, MIX/FX for Master) show one half permanently, with no toggle gesture, so the hardware gets one fixed meaning per knob. They ignore the toggle state, and the toggle's hit box becomes a same-size dummy so geometry doesn't change. Deep Edit's focus lookup takes the target half, so Deck A's FX half does not land on a pinned A-SRC row.
- **Page ids are stable.** Profiles reference `perform.<id>` and `showPage` silently ignores unknown ids, so ids never follow names; "Copy As New Page" mints a new id from a typed name (refused if it exists). A copied built-in that is only renamed keeps its old controller name.
- **Hidden pages are a preference** (`hiddenPerformPages`), not a field in the page file (built-ins are read-only), and affect only the tab strip: banks and Deep Edit still use every page. The last visible page can't be hidden. A hidden page that is active gets a temporary tab so the strip never shows nothing highlighted. Tabs keep a fixed width up to five, then shrink; the strip sits in the menu bar and can't take more room.
- **Rejected**: a scrolling tab strip (needs a clipped child in the main menu bar), positional ids, ids derived from the name on rename.
- Bank 4 (the legacy MASTER page) has free knobs whose meaning is not designed; revisit with the profile UI.

## Robust User File Loading and Saving for Controller Profiles and Perform Pages (`control/UserJsonFiles.kt`, `ControllerProfileStore.kt`, `PerfPageStore.kt`)

- **Decision**: Extract the shared file-IO of the two user-file stores into one internal helper in `control/` (no `ui/` imports there):
  - **Safe scan**: reading is inside the same guard as parsing, so any IO or parse failure on a user file becomes a `Rejected` entry and never throws out of `all()`/`rejected()`/`snapshot()`.
  - **Duplicate ids**: the first file by name wins; later files with the same id are rejected with a "duplicate id" reason.
  - **Atomic write**: write to a `.tmp` sibling (not matched by the `*.json` scan) then `Files.move` with `ATOMIC_MOVE`, falling back to `REPLACE_EXISTING`; the temp file is deleted on failure.
  - `ControllerProfileStore.saveUser` needs no extra id check: `ControllerProfile.compile()` already enforces `[a-z0-9][a-z0-9_-]*`. Public APIs are unchanged; no generic-store rewrite.
- **Rationale**:
  - `Perform` tab strip reads `PerfPageStore` every frame and `ControllerManager.handle` reads the profile store in the MIDI loop, so a throw there was a crash path for a bad file.
  - `writeText` directly on the target truncates a user's file on a crash or full disk; silent id overrides made results depend on scan order.

## midi/ and control/ No Longer Import ui/ (`MidiLearnTarget.kt`, `MidiEngine.install`, `LayerDependencyTest`)
- **Context**: the 2026-10-03 audit found `MidiMappingManager` importing `ui.ParametersState`/`ui.MidiLearnTarget`, `MidiEngine` reading `ui.UITheme.midiEnabled`, and `MidiEngine` calling `control.ControllerProfileStore`.
- **Decision**: `MidiLearnTarget` and `ParameterCellId` moved to `midi/MidiLearnTarget.kt` (plain move). `midi.MidiLearnSink` (`midiLearnTarget`, `midiLearnStartTimeMs`) is implemented by `ParametersState` and is what `processGlobalMidiEvents` takes. `midi.MidiEnabledSource` plus an optional device-opened listener are passed to `MidiEngine.install(...)` from `Main`, which also opens devices (the old `init` block read `UITheme`); default source is "disabled". Interface calls on the hot path do not allocate.
- **Guard**: `LayerDependencyTest` fails if any `midi/` or `control/` file references `llm.slop.liquidlsd.ui`.
- **Left alone** (profile learn since fixed, see "Profile Learn Interface and UserJsonLibrary"): `MidiMappingManager` -> `ControllerProfileStore.default` and `CommandContext`'s concrete `Mixer` (wide use in `GlobalCommands`).

## Schema Version in Controller Profile and Perform Page JSON (`ControllerProfile.version`, `PerfPageDef.version`, `UserJsonFiles.newerVersionWarning`)
- **Context**: the 2026-10-03 audit found neither format versioned; `ignoreUnknownKeys` hides fields from newer builds and there was no migration hook.
- **Decision**: field is `version: Int = 1`, matching `FxDefaultDto`/preset DTOs (not `schemaVersion`). Missing means 1. Each format has `CURRENT_SCHEMA_VERSION` (1) and `migrate(dto)` (identity), called when `version < current`. Stores always write `version = CURRENT_SCHEMA_VERSION`.
- **Newer file**: loads best-effort and is listed in `store.warnings()` (amber lines in `MidiPreferencesPanel` / `PerformPagesPanel`, plus a log warning). `saveUser` REFUSES to overwrite it (returns a problem) because saving would drop unknown fields; `deleteUser` still works.
- **Rejected alternative**: a hard reject of newer files, which would lose a user's page/profile entirely for a harmless extra key.

## Profile Learn Interface and `UserJsonLibrary` (`midi/ProfileLearner.kt`, `control/UserJsonLibrary.kt`)
- **Context**: the architecture audit listed `MidiMappingManager` reading `ControllerProfileStore.default`, and `ControllerProfileStore` / `PerfPageStore` copying the same override/save/reject plumbing.
- **Decision (A)**: `midi.ProfileLearner.learn(profileId, event, commandId, modifiers)` returns `Outcome` (Missing / Ignored / Saved / NotSaved). `MidiMappingManager.profileLearner` defaults to a no-op `NONE`; `Main` installs `control.ControllerProfileLearner`. Messages shown to the user are unchanged. `MidiMappingManager` is an `object`, so injection is a property, not a constructor argument.
- **Decision (B)**: `control.UserJsonLibrary<S, T>` (configured by a `Spec`) holds snapshot caching, built-in loading, scan, same-id override, rejected/warnings, newer-version save refusal, copy-built-in, save, delete and atomic write. Each store keeps its public API, nested `Source`/`Rejected` types (mapped from the library's), validation, ordering and (PerfPageStore) fallback page. Error and log messages are parameterised by `kind`/`noun` and read as before.
- **Guard**: `LayerDependencyTest` now bans profile-storage names in `midi/` and allow-lists the existing midi -> control files (`MidiMappingManager`, `MidiOutputPorts`) so nothing new can be added.
- **Left alone**: midi -> control for the command registry / `ControllerManager` / `CommandContext` / `MidiSink` (control -> midi also exists, so a full split needs moving `MidiMappingManager`'s host role). Result mapping between library and store `Rejected` types allocates per call; only the preferences panels call it.

---

# 7. Library, browser and Edit bay

## The Classic Browser Code Is Deleted; the Unified Pane Is the Only Browser (`ui/browser/BrowserPane.kt`, `ui/LibraryPanel.kt`, `ui/NavigationSurface.kt`)
- **Decision (2026-10-04)**: after the pane became the default and the hand-check passed, the classic path was deleted in one pass. Gone: `BrowserPane.enabled` / `supports` / `-Dlsd.unifiedBrowser` and the Library "Unified" toggle; the four-column fork in `LibraryPanel.draw`; `ShaderPickerPopup` (modal and inline, with `FxPick` / `SourcePick`); `ChainListBrowse` and the old tab bodies in `PerformanceBrowseBay`; `SelectionSource.PLAYLIST` / `FX_PLAYLIST` / `TRANSITION_PLAYLIST`; `PlaylistEditorPanel`, `FXPlaylistEditorPanel`, `TransitionPlaylistEditorPanel`; the classic `draw()` bodies, search buffers and filter caches of `PresetListPanel` / `FXBrowserPanel` / `TransitionBrowserPanel`.
- **Accepted losses** (user, 2026-10-04): the old picker's category multi-select, its FOLDERS/FLAT toggle and its per-row preset manage buttons (the pane's tree, search and row context menu replace them), and the live-activity icon on external video servers.
- **Kept**: the row, menu and popup helpers in the three browser panels (`drawRows`, `drawRow`, `applyToDeck`, `drawContextMenu`, create/overwrite popups), `PlaylistEdit.kt`, all queue panels, `activePlaylistData` / `getOrLoadPlaylist` / the selected-playlist-file fields, and the non-hosted `PRESETS` fallback in `LibraryNavigation.accept`.
- **Maps** has its own full-width child in `LibraryPanel.draw` (`unifiedKind()` is null for it). **Ctrl+F** reaches the pane through `BrowserPane.focusSearch(kind)` (per-kind flag consumed in `drawList`).
- **Bug found in the check**: Ctrl+Z was dead in every Browse tab because only `drawRackDeepEdit` ran `handleDeepEditKeys`; `drawRackBayModule` now runs it (undo only) in Browse mode.

## The Unified Pane Is Hosted in the Edit Bay Through `ApplyTarget` (`ui/browser/ApplyTarget.kt`, `ui/PerformanceBrowseBay.kt`, `ui/NavigationSurface.kt`)
- **Decision**: `BrowserPane.draw(..., target: ApplyTarget?)`: null in the Library, set when the Edit bay hosts the pane . A target carries `kind`, `accepts`, `isApplied`, `apply`, optional `clear` and a `contextKey`. With a target a single click applies (`DeckOps` / `FxOps` / `TransitionOps`), double-click-to-load, multi-select, audition, playlist reorder and Delete-from-library are off, the applied row shows a `\u25CF`, and rows the target cannot take are hidden (FX slots: stock + single effects; Chain: saved chains; deck: stock + saved presets; transition: stock + saved presets). The bay keeps its own floppy Save, an `External video...` menu (the pane's SRC catalog has no live feeds) and Clear Slot / Clear Chain above the pane.
- **Per-target scope**: `ScopeMemory` stashes the scope per (context, kind) and restores it, else the target default (Chain tab: Saved chains; FX slot: Stock filters). A context change also drops the list selection, search and tree cursor.
- **Controller**: `BrowserPane.hosted()` (the target drawn in the last 300 ms) makes `NavigationSurface.inPicker` true and switches the picker context to the Library's functions: side 2 = `stepPane` (tree > list > queues; replaces category stepping), shift + side 3 = the target's `clear`, knob 1 steps, tap applies the list cursor row (`BrowserPane.applyCursorRow`). A tap on a tree row selects the scope AND moves the cursor to the list (Library keeps tap-selects-only). `LibraryPanel.navMode` (the hosted kind, else `viewMode`) replaces `viewMode` in the cursor functions, and stepping a row while hosted does not audition.
- **Undo**: bay picks and clears are undoable. `FxOps` and `TransitionOps` got an `undoSink` (wired to the same `ParametersUndo` stack as `DeckOps`) and an opt-in `undoable` parameter on `setSlotFilter`, `loadSlot`, `applySlot`, `clearSlot`, `loadChain`, `applyChain`, `clearChain`, `setStock`, `applyPreset`, `loadPreset`. The snapshot is taken when the queued change is about to apply (after any fade dip, so async loads capture the state they replace): a slot's `FXSlotDto` or emptiness, the whole chain's DTO + `sourceFile` + `baselineDto`, or the transition's id/enabled/dry-wet/parameters. The restore re-enters the ops queue without `undoable`. Queues, macros, Super Knob stepping and session restore never push undo. Only the Edit bay passes `undoable = true`; Library double-click loads and MIDI/CV stepping stay un-undoable.

## Controller Navigation of the Unified Browser: Tree Cursor, Tap to Select (`ui/browser/BrowserPane.kt`, `ui/LibraryNavigation.kt`)
- **Decision**: in pane mode `LibraryNavigation.panes()` is TREE, PRESETS (the list), then the kind's queues; playlists are tree scopes, not a pane. The tree has its own cursor (`BrowserPane.treeCursors`, per kind), stepped by `stepTreeCursor` over `visibleSelectableScopes` (clamped, skips the Playlists header and collapsed children). A tab starts in the list (a missing cursor counts as the list for pane stepping); stepping never selects a scope, tap (`acceptTree`) does.
- **List reset**: every scope change (click, tap, a playlist created elsewhere) clears the kind's list selection (`setScope`), so a row from the previous scope can't be accepted.
- **Why**: "stepping never applies" is the existing rule for every pane; a list that doesn't update while turning is the accepted cost. Gated on `BrowserPane.enabled`, so the classic `panes()` branch and the Edit-row picker (`ChainListBrowse`) are untouched. Shift+tap on a tree *playlist* enqueues the whole playlist (`BrowserPane.enqueueCursorPlaylist`, same calls as the playlist's context menu; BG queue via the shift side button); on any other tree row it does nothing. In the Edit bay shift+tap stays unused. While hosted, the tree counts include only rows the target accepts (`BrowseCatalog.tree(accepts)`).

## Dirty-Deck Prompt Is Answerable from the Controller, and Takes Over Navigation While Up (`ui/PopupManager.kt`, `ui/NavigationSurface.kt`)

- **Decision**: `PopupManager` implements `DeckConfirmPrompt` (`deckConfirmPending`, `answerDeckConfirm(choice)`). `NavigationSurface` checks it first in `button`, `browseStep`, `browseAccept`, `back` and `browsing`: back = Cancel, side 2 / knob tap = Save, side 3 / Shift + tap = Discard; steps are ignored. A controller answer is stored and applied inside `drawDeckConfirmPopups`, the same code path as the mouse buttons.
- **Rationale**: the modal blocks the mouse but not MIDI, so a knob turn could step the Library behind it, and a Twister-only user could not answer. Applying the answer inside the modal block keeps `closeCurrentPopup` valid (closing from outside would leave an orphaned modal swallowing input). Save is on the tap and the less destructive side-2 button; Discard needs side 3 or Shift.

## Browser Search Is One Rule: `SearchMatcher` (`ui/browser/SearchMatcher.kt`)
- **Decision**: all asset browsers (Library panels and the unified pane, including the Browse bay) filter with `SearchMatcher.matches(tokens, fields, extra)`: whitespace-split words, each must be a case-insensitive substring of some field (name/id/folder, plus categories or tags). One buffer size (`BUFFER_SIZE` = 256).
- **Why**: the same text used to give different results per browser (FX panel: name only; picker: no tags for stock items; phrase-only multi-word). Review: `docs/developer/ui_interaction_architecture_review.md` section 3.1.
- **Left alone**: the two browsers still keep separate search *buffers* (typing in one does not fill the other), and category-chip filtering in the picker is unchanged. Library panels tokenise only on a cache miss, not per frame.

## Edit View "Next Up" Lives in the Bay Tab Row, Not the Perform Rows (`QueueNextUp`, `PerformanceDeepEditBay.drawQueueNextUp`)
- **Decision**: a one-line, right-aligned readout of the queue feeding the open module. `QueueNextUp.describe` is a pure function mirroring `triggerNext`'s next-index rules (staged standby deck first, then shuffle, repeat wrap, end of queue); it advances nothing.
- **Why there**: Perform rows have fixed geometry (layout-stability work) and deck-row queue *status text* was deliberately removed earlier (`DeckRowMetrics`). The bay tab row has free space on the right and exists only in Edit view, exactly where the Library is hidden.
- **Shuffle**: names no item, because the next item is chosen at trigger time (`unplayed.random()`); showing one would be wrong half the time.
- **Left alone**: a resizable Library dock in Edit view (conflicts with the three-view decision); Auto-VJ countdown/state is not shown.

## Deck Row SRC/FX Mode Is One Stored Value: The Deck Sub-Tab (`PerformanceUiContext.isDeckRowFx`)
- **Decision**: `ParametersState` deck sub-tab (`SRC`/`FX`) is the single source of truth for a deck row's mode. `PerformanceUiContext.deckRowMode` is deleted; `isDeckRowFx` reads the sub-tab only.
- **Why**: the predicate was `deckRowMode == "FX" || subTab == "FX"`. Writers that set the sub-tab to `SRC` (bay SRC tab, source pick, Deep Edit tabs) could not clear the pill's `FX`, so the row and bay drifted apart (found in `docs/developer/ui_interaction_architecture_review.md` section 3.2).
- **Master too**: `masterRowMode` is deleted the same way; `isMasterRowFx` reads `activeMixerSubTab == "FX"` only. Before, `FX` pill then the bay's `TRANS` tab left the Master row on FX.

## The Library Gets a Maps Tab (Banks + Pages); Transition Save Is One Helper (`MapsBrowserPanel`, `TransitionSave`)
- **Decision (items 10, 11)**: one fourth Library mode instead of two, with a Banks/Pages toggle, since banks and pages have no playlists or queues. It reuses `MacroBankSerializer`/`PerfPageStore`; it adds no new file formats. Bank Save/Apply covers deck, Master, Transition and FX Sends banks (FX banks are chain-driven; GLOBAL has no knobs). Page row editing stays in Preferences. Nothing is dragged to decks and Maps rows can't be queued.
- **Open**: keyboard/MIDI list stepping and Q/Enter don't act on Maps rows (they are mouse-driven); the layout wasn't checked at 1280x720 in the running app.
- **Transition save** has one entry point (`TransitionSave.requestSaveCurrent`) called from the Library "+", the Mixer TRANS kebab and the inline picker. Decided 2026-10-04.

## Failures Toast; Transition Queue Deltas Apply in Full; GLOBAL Strip Code Is Kept Dormant (`FxOps.reportFailure`, `UIManager.MAX_TRANSITION_STEPS_PER_FRAME`)
- **Decision (item 25a)**: FX/transition load and apply failures log and show a `ToastOverlay` message; no new UI.
- **Decision (item 25b)**: transition queue deltas apply as many steps as asked (cap 8/frame) because a transition step is just a selection. **Not done**: A/B and BG queue deltas still collapse to one step, since each step loads a preset; applying `+n` there needs an `advanceBy(n)` that loads only the final item (open).
- **Decision (item 25c)**: the Transitions view queue key uses the registered `library.queue_ab` shortcut. Enter stays hard-wired (no registered action).
- **Decision (item 24)**: the dormant GLOBAL guest-strip/learn code stays, with comments tying it to the v1.1 free-knob row (matches the existing GLOBAL-bank decision). Decided 2026-10-04.
- **Open (item 9)**: restoring the previously expanded module after leaving Edit needs a trigger decision (no new button is allowed in v1.0); not implemented.

## Multi-Step Queue Moves Load Only the Final Item; FX Favorites Are Stock-Only and Shared (`PlayQueueManager.advanceBy`, `FxShortlist.version`)
- **Decision (item 25b)**: `advanceBy(n)` moves by n and loads once. Linear: clamp without repeat, wrap with it, and a load rejected by the dirty-deck SKIP rule restores the old position. Shuffle: intermediate picks only update the bookkeeping. A staged deck consumes the whole step as one. All queue deltas are capped at 8 per frame.
- **Decision (item 12)**: the Library FX browser reads and writes the same `FxShortlist` as the picker (stock filters only; saved FX can't be starred). Marked by a ★ in the row, a context-menu toggle and a "favorites only" filter, with no extra row button.
- **Decision (item 19.2)**: MIDI modulators' own DC Offset/Depth sliders stay unbindable (controller driving a controller; keeps the menus simple). Decided 2026-10-04 with the user.

## Esc Cancels All Armed Learns; Two MIDI Learn Paths Stay, Named Apart; Typed Knob Values Use the Curve (`BackNavigation.back`, `MacroKnobWidget.knobValueFromTyped`)
- **Decision (item 6)**: stacked Learn modes are a mode error, so one Esc/back press clears macro, MIDI and OSC Learn together. OSC Map Mode keeps its own toggle.
- **Decision (item 18)**: both MIDI paths stay. The slider's Learn is a controller-profile mapping (sets the value, survives across presets); the Properties button creates a preset-saved `midi_cc_` modulator (adds on top). Only the labels changed (Learn MIDI Mapping / Learn MIDI Modulator).
- **Decision (item 21)**: a typed knob value goes through `MacroCurve.inverse`, and the readout through `mapToRange`, for the first binding. Non-monotonic or stepped bindings land on the nearest position.
- **Not done (item 19 part 2)**: `MidiModulatorSection`'s DC Offset/Depth sliders still have no macro/MIDI/OSC binding. Mapping a MIDI modulator's own range is arguably meta; left for the user to decide. Decided 2026-10-04.


---

# 8. UI conventions

## Perform rows: content changes, geometry doesn't
- Switching a row between SRC and FX, entering Focus, or pinning a row changes what is drawn, never where controls sit (`PerfRowGeometry`, `DeckRowMetrics`, `FxSlotCell.HEIGHT == FxParamCell` height). Widget ids embed the page position and each row's controls sit in a `pushID(rowIdx)` scope so the same deck on two slots cannot collide. This is why queue status text was removed from deck rows and why "Next Up" lives in the Edit bay tab row.
- The SRC and FX rows share one control order so they scan the same, and both end at the same x. Slot pills `1 2 3` focus a slot (and switch an SRC-mode row to FX); clicking the focused pill again leaves Focus.
- The whole-deck randomize die sits in the right wing because it randomizes SRC *and* FX parameters; putting it on the SRC row misreported its scope.
- Minimum supported screen is 1280x720; Perform, Edit and Library are three views of the one window.

## Colour: `TangoPalette` is the only source
- Roles (`Role(dark, light?, lightSlot)`) plus plain swatches; the theme is held in `TangoPalette.isLightTheme`, set by `UIThemeStyler.setupThemeColors`. Call sites never test the theme and never use a colour literal; a literal or an `if (isLight)` fork in Perform UI is a bug. Dark values equal the old literals. `CvTheme` and slider literals are still outside it.

## Text and tooltips
- "Learn" means hardware (MIDI/OSC); a macro knob assigns a **target** (see section 5).
- Control tooltips (`ControlTooltipBuilder`, `controlTooltip { }`) have three blank-line-separated tiers: header and context (label, value, routing), gestures as bullets, then protocol lines (`OSC:`, `MIDI:`). Gestures are always in this order: Drag, Shift-drag, Scroll, Left-click, Double-click, Middle-click, Right-click. Use the builder; don't concatenate tooltip strings.

## Knob drag
- Dragging a knob locks the pointer (`GLFW_CURSOR_DISABLED`) after a 3 px deadzone, so range is unbounded and the cursor doesn't drift or cover the value; Shift (fine) skips the deadzone. The cursor is hidden with a blank GLFW cursor as well, because some Wayland compositors don't clear it. On release, `glfwSetCursorPos` runs *while still disabled* and before restoring the normal cursor, which is what `zwp_pointer_constraints_v1` honors; global pointer warping in normal mode is forbidden on Wayland. Focus loss and window close abort the drag and restore the cursor. Preference `lockCursorOnKnobDrag` (default on); bypassed while the touch console is active.

## Window frame
- Client-side decorations by default (`WindowFrameController`, pure GLFW/ImGui, no native hooks) to reclaim ~30-40 px of height; `framelessWindow = false` restores native decorations for tiling window managers.

---

# 9. Audio, CV and modulation

- **Modulators are addressed by stable id**, not list position (section 5). Audio modulation has two independent slots per parameter, each continuous (RMS band) or transient (spectral flux), over four bands (full mix, bass, mid, high). Each audio modulator owns its envelope follower, with musical presets (Raw, Punchy, Smooth Swell, Slow Pulse, Ambient Drift, Custom); the oscilloscope draws the raw band ghosted beneath the smoothed trace.
- **LFO and Audio modulators are edited as Min/Max** (dual-handle range slider) because depth/offset is cognitively heavy on stage. The stored fields stay `depth` and `dcOffset`; conversion is `depth = (max - min) / 2`, `offset = (max + min) / 2` at the UI edge.
- **Oscilloscope history is written at audio-block rate from `processAudio()`** (direct `CvHistoryBuffer` references on `AudioEngine`), not sampled by the UI loop, so scopes survive UI frame drops. `CVRegistry.updateAll()` skips audio and trigger sources to avoid double sampling.
- **Beat clock**: anchors are published with `currentTime + blockDurationNs`. When render time jitters backwards, `CVRegistry.getSynchronizedTotalBeats()` extrapolates forward by the frame delta instead of clamping flat (clamping froze beat-synced LFOs); the anchor resets on seek or tempo change.
- **`BeatTrackerEngine`** (BTrack-style: complex spectral difference onset function, two-state acquisition/locked periodicity, causal dynamic programming) runs allocation-free on the audio thread, with `logTauTable` instead of transcendental calls, autocorrelation every 4 blocks, and biquad state flushed below 1e-15 to avoid denormal stalls. It outputs a continuous phase and locked cosine.
- **Auto-VJ and queues**: manual loads never mutate a queue. A manual load into the standby deck while Auto-VJ runs *stages* that deck; the next trigger crossfades to it and the queue position is unchanged. Loading into the live deck replaces output immediately. Enabling Auto-VJ mid-set waits for the next trigger. Deck PV is independent of Auto-VJ. The A/B and BG queues have parity: prev/next parameters with MIDI CC, the same dirty-deck policy (`SKIP`, `AUTO_SAVE`, `AUTO_DISCARD`), and double-click = load, fade and play.
- **MIDI**: multi-type capture (CC, note, pitch bend) into lock-free state arrays, soft takeover on every mapping, relative rotary decoding, and mappings suspended (not overwritten) while a macro or Metaknob owns the target (section 5).

---

# 10. Web broadcast and touch console

- **The desktop is the source of truth for the web client.** `web/` is a vanilla WebGL2 + Web Audio client with no bundler or npm dependency, 1:1 with the desktop GLSL. `scripts/sync_web.py`, `web/sync_manifest.json` and the Gradle `checkWebSync` / `syncWeb` tasks translate shaders mechanically (`#version 330 core` to `300 es`) and hash the algorithmic Kotlin sources (`Icosahedron.kt`, `Evaluators.kt`, `WebPresetSerializer.kt`) so drift between Kotlin math and its JavaScript twin fails the check.
- **Broadcast**: `BroadcastEngine` streams over `java.net.http.WebSocket` on its own daemon executor (never the GL or audio thread), `state_full` on connect and preset loads, rate-limited `state_delta` (~25 Hz) afterwards. A stateless Node relay (`server/server.js`) caches the latest `state_full`, authenticates broadcasters by key, and fans out; viewers fall back to the client-side autopilot when no broadcaster is live.
- **Touch console** (CapsLock latch, SCS.3m-style): four spatial zones with independent LIFO touch stacks per zone, bezel clamping, events handed to Thread 0 through a lock-free queue. Linux reads evdev directly through JNA with `EVIOCGRAB`; devices that look like TrackPoints, mice or pens are rejected by name and by lacking absolute axes. Access is checked with `access(2)`, not `File.canWrite()`, because the latter ignores the POSIX ACLs `systemd-logind` assigns; a `uaccess` udev rule grants it. macOS uses `NSTouch`.

---

# 11. Build, release, platforms

- **Supported targets are linux-x64, windows-x64, macos-arm64, macos-x64.** Linux ARM64 was dropped (no upstream `imgui-java` natives, and the native build was moved out of this repo). Do not re-add it without upstream natives.
- **CI**: every push to `main` cuts the next `v1.0.0-beta.N` tag and release (`release.yml`, sequential, no cancel-in-progress), keeping the 3 newest releases and deleting older releases and tags. Release notes come from `docs/release_notes.md` / `RELEASE_NOTES.md` in three tiers: an exact version header; else the added lines since the previous tag; else the first `###` block under `[Unreleased]`. `smoke-test.yml` runs the headless `--smoke-test` on each platform's native runner for PRs; release publishing is gated on it.
- **Packaging**: `library/**` ships in every archive, the bundled stock sources live in the jar and self-extract, Unix scripts and JRE binaries get mode 755 via `FileCopyDetails.permissions` (Gradle 9: `filePermissions` inside `eachFile` binds to the Zip task, not the file). Launchers forward arguments (`"$@"` / `%*`) and pass `--enable-native-access=ALL-UNNAMED`. macOS: probe both JRE layouts (`Contents/Home/bin/java`), `-XstartOnFirstThread`, strip the Gatekeeper quarantine on the bundled JRE.
- **CLI**: `--version`, `--help`, `--smoke-test`, `--screenshot-ui=<png>`, `--screenshot-after-frames`, `--window`, `--no-audio`, `--ui-lab` (isolated component sandbox).
- **ImGui** is `imgui-java` 1.92.x; popups need unique ids and non-blank labels.
- **Docs**: the website (`greenjon.com`) is generated by a pure Kotlin/Gradle generator (`buildWebsite` / `exportGreenjon`) from `docs/` and `RELEASE_NOTES.md`; no Python, Node or Ruby tooling.

---

# 12. Removed, do not reintroduce

Each of these was built, then deliberately deleted. The reason is the point.

- **The classic view (separate Parameters and Properties panels), Preset Grid, Rack accordion / MULTI rack mode, Column 3 MACROS tab.** Two editing surfaces for the same knobs drifted apart. Perform/Edit/Library replaced them; binding editing lives in the Edit-row strip and the Properties editor.
- **Modular rack as a repatchable graph.** Built-in units wrapping the fixed deck chain and the crossfade can only monitor, not reroute; the patch-cable illusion was misleading. (`ui/rack/RackUnit.kt` survives only as drawing helpers.)
- **FX banks, FX1/FX2 shared send banks, per-row alternative chains, `.lsdfxbank` as a live concept.** One `FxChain` per deck and Master (section 4).
- **Per-preset FX** (`DeckPresetDto.fxSlot*`). FX is session state (section 2).
- **Modal pickers (`ShaderPickerPopup`), the classic four-column Library, the three playlist-editor panels, `ChainListBrowse`.** Replaced by the unified `BrowserPane`. Accepted losses (2026-10-04): the picker's category multi-select, FOLDERS/FLAT toggle, per-row preset manage buttons, and the live-activity icon on external video servers.
- **Hard-wired feedback / bloom / mixer blend modes / legacy 3D passes, the 10 legacy blend and wipe transitions.** ISF only.
- **Deck macro knobs 5-8.** Every canonical bank has exactly 4 knobs (restore clamps older banks); FX knobs have a fixed mapping.
- **Clock-row Global knobs.** The `GLOBAL` bank stays registered with 0 knobs so the v1.1 free-knob row needs no migration; its strip code is dormant.
- **Linux ARM64 target** (section 11).
- **Compatibility shims during beta** (section 1).
