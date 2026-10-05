# Plan: surprising findings from the ARCHITECTURE.md rewrite

Source: the 2026-10-04 `ARCHITECTURE.md` rewrite, which verified the code against `DECISIONS.md` and the docs and found the items below. Decisions were made by the user on 2026-10-04. Handoff for the executing agent: `.planning/surprises-handoff.md`.

Order is by risk: persisted-format changes first (DECISIONS §1: cheapest before v1.0), then dead code, then doc/decision fixes, then runtime hygiene, then the doc sweep.

## Decisions (final)

| # | Question | Decision |
|---|---|---|
| 1 | Remove legacy `fb*` / `view3D*` params | **Yes**, before v1.0 |
| 2 | "ALPHA A/B/BG" macro labels | **Yes**, rename to Level, relabel on load |
| 3 | Linux ARM64 | **Supported, here to stay.** Fix `DECISIONS.md` §11 and §12, not the build |
| 4 | Delete orphaned shaders | Yes |
| 5 | Drop `lwjgl-openal` | Yes |
| 6 | `default_transitions` name collision | Yes (rename the preset seed folder) |
| 7 | Single dot-directory | Yes (`~/.liquid-lsd/`) |
| 8 | `is Mandala` special cases | **Valid exceptions.** Amend `DECISIONS.md` §2; no refactor |
| 9 | `view2d.frag` wording | Yes, fix `DECISIONS.md` §2 |
| 10 | Per-frame `PerformSurface` / `NavigationSurface` allocation | Yes |
| 11 | MIDI one-frame lag | Yes (depends on 10) |
| 12 | Relay default token `lsd25` | **Live relay relies on it. Do not remove or change the default in code until the user rotates the token on the VPS.** See item 12 |

## Phase 1: persisted formats

### 1. Remove legacy `fb*` and `view3D*` parameters
- Keep `viewZoom` and `viewRotateZ` (the only live view params; `Renderer.renderDeck` reads them).
- Remove from `rendering/Deck.kt` (`view3DMode`, `viewRotateX/Y`, `viewPersp`, `viewDepthDim`, `viewSeparation`, `viewBlendMode`, `viewRoundness`, all `fb*`), `models/PresetModels.kt` (`DeckPresetDto.feedbackParameters`, `viewParameters` entries for the dead keys; the toDto/apply/reset blocks), `models/ClipboardManager.kt` (the "Decay" etc. mapping), `broadcast/WebPresetSerializer.kt` (`decay` and any other `fb*` fields), `Deck.getAllRandomizableParameters`, `getParameterPaths`, and any UI that lists them (`ParametersTabs`, `ParameterResolver` paths, macro/MIDI/OSC mappings that name them).
- Before deleting a field, check `web/` (`renderer.js`, `dsp.js`, `evaluator.js`, `autopilot.js`) for consumers. If the web player reads a removed value from the broadcast, send a constant instead of removing the key, and note it.
- Existing files still load: `ignoreUnknownKeys` drops the removed keys. Decide with the user whether `DeckPresetDto.version` should bump (it has no migrate hook; probably leave).
- Verify: `./gradlew test`, load a pre-change `.lsd` and `library/last_session.json` by hand, broadcast tests, `./gradlew checkWebSync`.

### 2. Rename "ALPHA A/B/BG" to Level (labels persisted in saved banks)
- `macro/MacroEngine.kt` `newBankFor(MASTER)`: labels "ALPHA A/B/BG" -> "LEVEL A/B/BG". (The fourth knob is already "MASTER".)
- `ui/PerformanceMasterControls.kt:121` badge "Deck Alphas & Master" -> "Deck Levels & Master".
- Relabel on load: when a restored MASTER bank knob's label equals an old default ("ALPHA A", "ALPHA B", "ALPHA BG") **and** its first binding targets the matching `Mixer/levelX`, replace the label. Do it where banks are registered/restored (`SessionSerializer.loadSession` or `MacroEngine.registerBank`), not in the UI. Do not touch labels the user edited.
- Grep for other user-visible "Alpha" (tooltips, `docs/user_guide/`, `docs/developer/`, shortcut labels). Terminology: **Level**, not Alpha (`DECISIONS.md` §1 UI terminology; glossary in `docs/user_guide/your_workspace.md`). Code identifiers keep their names.
- Add a test for the relabel rule (old default relabelled, edited label untouched).

## Phase 2: dead code and structure

### 4. Delete orphaned shaders
- Remove from `src/main/resources/shaders/`: `feedback.frag`, `tri_planar.frag/.vert`, `tetra_kaleido.frag/.vert`, `chladni.frag`, `gyroid.frag`, `test_gradient.frag`, `test_simple.frag`, `test_solid.frag`. Keep `blit.*`, `mixer.frag`, `view2d.frag`.
- First grep `src/`, `build.gradle.kts`, `scripts/sync_web.py` and `web/sync_manifest.json` for each name. `web/shaders/` has its own copies; if the manifest maps any of these to the desktop file, update the manifest and run `./gradlew checkWebSync` (and `syncWeb` if needed).

### 5. Remove `lwjgl-openal`
- Delete the `org.lwjgl:lwjgl-openal` dependency and its native classifiers from `build.gradle.kts`. Nothing in `src/` imports it. Build, run `./gradlew test`, `--smoke-test`; confirm the shadow jar still starts.

### 6. `default_transitions` name collision
- The resource folder `default_transitions/` holds stock ISF `.fs` transitions (loaded by `ISFTransitionRegistry`) and, via a Gradle-generated `manifest.txt`, the seeded `.lsdtrans` presets (`FileSystemManager.ensureDefaultLibrary`, build task writing `default_transitions/manifest.txt`).
- Rename the **preset seed** folder to `default_transition_presets` (Gradle `PrepareDefaultAssetsTask`, `FileSystemManager.extractBundledAssets` call). Leave the `.fs` folder alone.
- Do **not** move `library/transitions` (it is both an ISF scan root and the `.lsdtrans` folder). Document the double role in `ARCHITECTURE.md` (already noted in the persistence table) and `docs/user_guide/presets_and_library.md` if absent.
- Check an existing install: `library/.defaults_installed` marker means seeding is skipped, so the rename affects only fresh installs and "restore defaults".

### 7. One dot-directory
- Move key bindings from `~/.liquidlsd/keybindings.json` to `~/.liquid-lsd/keybindings.json` (`ui/shortcuts/ShortcutManager.kt`). Source notes already use `~/.liquid-lsd/`.
- No fallback read of the old path (DECISIONS §1: no shims). Mention in release notes that custom key bindings reset.
- Update any test that points at the old path.

## Phase 3: DECISIONS.md fixes (edit entries in place; no dated appends)

### 3. Linux ARM64 is supported
- `DECISIONS.md` §11: replace "Supported targets are linux-x64, windows-x64, macos-arm64, macos-x64. Linux ARM64 was dropped..." with the five targets incl. linux-arm64, and keep the reason it was once dropped only if still useful (no upstream `imgui-java` natives; confirm how the build gets them now before writing, e.g. the `docs/developer/build_arm64_linux.md` flow).
- `DECISIONS.md` §12: delete the "Linux ARM64 target" entry (a reversed decision is deleted, per the file's own rules); optionally add one line to "Removed" only if the reversal itself is worth remembering.
- Do not change `build.gradle.kts`, CI, README or `getting_started.md`; they are correct.
- Check `ARCHITECTURE.md` "Unverified" list: remove the ARM64 bullet and, if desired, list the five platforms in §14.

### 8. `is Mandala` exceptions
- `DECISIONS.md` §2, "Mandala is an ordinary `DynamicVisualSource`": amend to say two sites are accepted exceptions: `ui/ValueParamSection.kt` (UI: recipe/petal display) and `broadcast/WebPresetSerializer.kt` (web player needs the recipe `a,b,c,d`). Say why (the web port is still Mandala-specific; the UI shows recipe details). No code change.

### 9. `view2d.frag` wording
- `DECISIONS.md` §2 "View pipeline": state that `view2d.frag` is applied to external video only (`Renderer.renderExternalVideoSource`); ISF generators receive `uZoom` / `uRotateZ` as uniforms. Check the edge-wrap sentence (Mirror default) still describes what `view2d.frag` does and say which path it applies to.

## Phase 4: runtime hygiene

### 10. Per-frame allocation in `UIManager.render`
- `ui/UIManager.kt` builds `PerformSurface(...)` and `NavigationSurface(...)` every frame (and a third `NavigationSurface` in the Esc handler, `processQueueKeyboardShortcuts`).
- Make them long-lived fields. They need the current `Mixer`, `PerformanceUiContext`, `ParametersState`, `PopupManager` and `SessionContext`; update the mixer reference per frame rather than constructing. Audit both classes for per-frame state that relies on being fresh (cached rows, browse-session counters like `NavSurface.browseSession`, held state) before sharing instances.
- The Esc handler should reuse the same instance.
- Verify: `NavigationSurfaceTest` (note: it currently has someone else's uncommitted edits, see handoff), `PerformSurface` tests, `./gradlew test`, and a hand check with a controller if available (Twister: bank switch, browse, dirty prompt).

### 11. MIDI one-frame lag (after 10)
- Move the `MidiMappingManager.processGlobalMidiEvents` + controller feedback + queue-advance block so incoming MIDI is applied before the frame's render phase. Simplest shape: keep the code in `UIManager`, expose a `fun processInput(mixer)` that `Main.kt` calls right after `MacroEngine`-adjacent input steps (before `MidiMappingManager.update`), and keep ImGui work in `render`.
- Constraints: event handling that opens ImGui state (learn arming, popups) must still happen in a context where ImGui calls are legal; today it runs before `ImGui.newFrame()`, so moving it earlier in the same frame is equivalent. Dirty-deck prompts only set state.
- Keep `MidiEngine.clearEvents()` behaviour when MIDI is disabled.
- Update `ARCHITECTURE.md` §2 (frame loop) and §10 to match; remove the "one frame later" sentence.
- Verify with `./gradlew test`; no behaviour change expected except latency.

### 12. Relay default token: **blocked on user action**
- `server/server.js` has `BROADCAST_TOKEN = process.env.LSD_TOKEN || 'lsd25'`, in a public repo, so anyone can broadcast to the live relay. The user confirmed the live relay relies on this default.
- **Do not change the default or fail at startup yet**; that would break the live relay.
- Safe sequence (user does the VPS step): (a) user sets `LSD_TOKEN` to a new secret on the VPS and restarts (pm2); (b) user updates their desktop broadcaster token in Preferences; (c) only then change `server.js` to refuse to start without `LSD_TOKEN`, update `server/` docs (`docs/developer/web_subsystem.md`, `docs/user_guide/web_broadcast.md`) and the example in the file header.
- The agent's task here is limited to: confirm with the user that (a) and (b) are done, then do (c). Until then leave it open and say so in the final report.

## Phase 5: doc and comment sweep (last)

- Code comments: `ui/PerformanceMatrixPanel.kt` KDoc ("2 tabs DECKS/MASTER" -> pages via `PerfPageStore`; also "Performance Mode" -> Perform view); `osc/OscMappingManager.kt:65` (`/macro/<bankId>/knob/N`, no `switch`); `macro/MacroOscBridge.kt` header (knobs 1..4, not 1..8) and the `getKnobAddress` comment.
- `docs/developer/`: `architecture.md` (diagram, thread list, `feedback.frag`, `PropertiesPanel`, preset queue claim), `beat_sync.md` (`ClockSource` = `MANUAL | AUDIO_TRACKER`; Link is a separate on/off), `rendering.md` ("up to 4" -> 3 slots; "Performance Mode"), `ui.md` ("Performance Mode", historical `ShaderPickerPopup`/`ChainListBrowse`/`MacroPanel` mentions fine if marked as history), `preset_management.md` (Column 2/3/4, "Play Queue", `PlaylistEditorPanel`, `FXPlaylistEditorPanel`), `unified_control_mapping.md` ("Play Queue"). Move `modular_video_rack_proposal.md` to `docs/archive/` or mark it archived.
- `docs/user_guide/visual_sources.md` ("Effect Slots (FX1-FX4)" -> 3 slots).
- `.planning/codebase/`: STRUCTURE.md (file count 291; remove `Column3HeaderToggle`, `MacroPanel`, `MacroBindingInspector`, `PlaylistEditorPanel` etc.), STACK.md (Kotlin 2.3.0, imgui-java 1.92.7.1, JVM 17 toolchain), INTEGRATIONS.md (MIDI profiles in `library/midi/`, date).
- `ARCHITECTURE.md`: bring it in line with whatever landed (legacy-params sentence in §3, "ALPHA" row of the naming table, MIDI timing in §2/§10, ARM64 bullet in Unverified, dot-dir in §11, `default_transition_presets` in §11, shader list if mentioned).
- Release notes: add entries for user-visible changes (key bindings reset, label rename, removed params, ARM64 note if applicable) in **both** `RELEASE_NOTES.md` and `docs/release_notes.md`.

## Commit plan (one commit per item, no attribution lines)

1. Items 4 + 5 (deletions) 2. Item 1 3. Item 2 (+ 9 if the same files) 4. Items 3, 8, 9 (DECISIONS only) 5. Items 6 + 7 6. Items 10 then 11 7. Phase 5 sweep 8. Item 12(c) only after the user confirms the VPS step.
