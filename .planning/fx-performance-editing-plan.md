# FX Performance Editing — Implementation Plan (Steps 1–3)

2026-09-24. Goal: every *major* FX switch (swap an effect, reorder, bypass a slot, load / save / new chain) happens
on the Performance Mode FX rows. Deep Edit is only for fine tuning. Model: Mixxx FX units driven from a Traktor controller.

Out of scope here (later passes): **Step 4** focus mode (knob 1 = slot dry/wet, knobs 2–4 = top params with paging,
retargeting the same `Macro/<bank>/knob_N` paths), **Step 5** hardware button paths, chain snapshots.

All paths below are relative to `src/main/kotlin/llm/slop/liquidlsd/`.

---

## Decisions (answered 2026-09-24)

1. **Deck presets must not carry FX.** `DeckPresetDto.fxChain` (re-added by mistake in `cf73254`) is removed again in Step 1,
   along with its capture/apply in `Deck.toDto`/`applyDto`, and stripped from on-disk `.lsd` files. FX is separate from presets.
2. **No legacy-save shims.** Old sessions' master FX resets to the default chain once.
3. **Loading over a dirty chain just loads.** No prompt.

---

## Step 1 — Master FX becomes a plain `FxChain`; FX operations go through one GL-thread path

### 1a. Model and render

- `rendering/Mixer.kt`: replace `masterFxBank = FxBank("MFX")` (:39) with `val masterFxChain = FxChain("Master FX")`.
  - Delete `masterFxWetDry` (:41), `masterFxSlots` (:44), the `*MasterFx*` delegates (:116–125), and `MASTER_FX_SLOT_COUNT` (:273).
  - Parameter paths become `Master/FX/...` via `masterFxChain.getParameterPaths("Master/FX")` (:347–349). This is the same
    shape as `Deck A/FX/...`, and `"Master/"` isn't used by any other path today.
  - Randomizable params (:301–312): iterate the single chain.
- `rendering/Renderer.kt`: the master pass (:331–346) calls `renderFxChainPass`. Delete `renderFxBankPass` (:459–571), which is
  a near copy of `renderFxChainPass` (:371–451) plus a second bank-level wet/dry blend.
  - All 5 shipped banks have `masterWetDry = 1.0`, so dropping that blend changes nothing visually.
  - Drop the now-unused master bank-out FBO (`Mixer.kt:48–50`, :98–108, :582–584) if `renderFxChainPass` doesn't need a third buffer.
- Delete `rendering/FxBank.kt`. Also delete the unused FxBank param/imports in `Deck.getEffectiveWet` (:66),
  `BrowserActionToolbar.kt:7` and `FXBrowserPanel.kt:13`, and fix the stale comment at `FxChain.kt:16`.

### 1b. Persistence and assets

- `models/FXPresetModels.kt`: delete `FXBankDto` (:33–41).
- `models/PresetModels.kt` `MixerDto` (:277–280): replace `masterFxSlots`, `fxBank1`, `fxBank2` and `masterFxBank` with
  `masterFxChain: FXChainDto?`.
- `presets/SessionSerializer.kt`: write/read `masterFxChain`.
  - Delete the `masterFxSlots` handling (:37, :55, :136–144) and the legacy `fxBank1/2` migration (:157–168).
  - Point the unresolved-filter check (:218–221) at `masterFxChain.slots`.
- Default assets:
  - Add the two bank-only chains to `defaults/fx_chains/`: **Subtle Optical Warmth** (club_master_finishers C1) and
    **Digital Bitcrush Mosaic** (glitch_strobe C3). Every other bank chain already exists there.
  - Delete `defaults/fx_banks/` and `library/fx_banks/`.
  - Replace `Mixer.loadDefaultFxBanks()` (:133, :142–179) with `loadDefaultFxChains()`. It loads named `.lsdfxchain` files:
    Master = Subtle Optical Warmth; A / B / BG / PV = the same chains used today (Warp & Flow C1, Liquid Chrome C1,
    Warp & Flow C2, Liquid Chrome C2), loaded by their chain file names.
  - Called from `Mixer` and `SessionSerializer.kt:373`.
- Remove bank plumbing:
  - `FileSystemManager`: `FX_BANKS_ROOT` (:33), `managedRootPaths` entry (:106), `getFxBankTags` / `scanAllFxBanks` (:273–310),
    `getFxBanksRoot` (:880–888), bundled extraction (:968) and `fxBanksExtracted`.
  - `PresetRepository.save/loadFxBankAsync` (:170–190), `AssetType.FX_BANK`, `ClipboardManager` bank ops (:12, :22–24).
  - `FXBrowserPanel`: bank filter (:47–53, :98–99), scan (:147–150), icon, tooltip and menu (:228, :256, :435–455).
  - `BrowserPopupHandler` (:147, :213).
  - `build.gradle.kts` bank copy in `SyncDefaultsTask` (:206–213) and `PrepareDefaultAssetsTask` (~:250).

### 1c. Macro sync — one code path, correct labels

- `macro/FxMacroSync.kt`:
  - Collapse `sync` / `syncChain` / `syncDeckFx` into one `syncChain(bankId, label, chain, force)` that writes `"$label/FX/..."`.
    Simplify `OWNED_PATH_PATTERN` to `^([^/]+)/FX/(Super|FX\d+/Meta)$`.
  - Add `FxMacroSync.syncFor(bankId, mixer, force = false)`, which resolves the chain and label from the bank id
    (`deckA_fx` → `Deck A`, …, `masterFx` → `Master`).
  - It replaces the 8 copied `when (bankId)` blocks in `PerformanceMatrixPanel` (:685, :860, :1377, :1392, :1490, :1527, :1542, :1587),
    `MacroPanel.kt:205`, `FXChainMacroStrip.resyncMacroKnobs` (:38–43), `Mixer` default load and `SessionSerializer.kt:383`.
  - That fixes the `"Deck A FX"` label bug (:653, :1681, :1695, :2143), the `"Master"`-vs-`"MFX"` bug (:186), and MacroPanel's
    hard-coded chain 0.
  - Delete `resolveFxChainLabel`; `resolveFxChain` becomes a shared helper, `FxTargets.chainFor(bankId, mixer)`.
- `macro/MacroEngine.kt`:
  - Delete the FxBank import (:6), `syncLinkedFxKnobValues(FxBank)` (:356–366) and the stale comment (:39–45).
  - Use the chain variant for master (:342). `canonicalIdForDeckLabel` gets `"Master" -> MASTER_FX` (:220).
- `midi/MidiMappingManager.kt:20–28`: `fxMacroSyncBankIds` currently covers only `MASTER_FX`. Extend it to the 4 deck FX banks
  so all FX knobs get the same learn defaults.
- UI:
  - `ParametersTabs`: delete `drawFxBankGroupContent` (:463–607). Master Deep Edit and `drawMixerFxTab` (:399–411) call
    `drawFxChainContent(mixer.masterFxChain, "Master/FX", …)`.
  - `ParametersState`: delete `get/setActiveChainIndex` (:213–223).
  - Update `MacroPanel` "MST FX" (:44–53 and others) and `MacroBindingInspector.navTargetFor` (:56).
  - Fix the stale `activeTopTab = "MFX"` at `PerformanceMatrixPanel.kt:1024`.

### 1d. `FxOps` — every FX mutation on the GL thread, with sync and undo

**Found while grounding (real bug):** every async FX chain load applies the DTO inside `CompletableFuture.thenAccept`,
which runs on `PresetManager.presetIoExecutor`, not the GL thread.
- Callers: `ParametersTabs.kt:679`; `PerformanceMatrixPanel.kt:645`, :682, :1375, :1525, :1711; `FXItemApplier.kt:21`, :26.
- `applyFxChain` → `clearFxSlot` → `ISFFilter.dispose()` calls `glDelete*` on multipass FBOs from the wrong thread.
- The slot swap also races the renderer's slot loop.
- Deck presets already avoid this with `PresetManager.deck*PresetQueue` (`ConcurrentLinkedQueue`), drained on the GL thread.

New `rendering/FxOps.kt` (the single entry point for FX changes; later steps build on it):
```kotlin
object FxOps {
    fun loadChain(session, file: File, target: FxTarget)         // async read -> GL-thread apply -> sync -> dirty baseline
    fun applyChain(dto: FXChainDto, target: FxTarget, source: File?)
    fun setSlotFilter(target: FxTarget, slot: Int, filterId: String?)   // stock ISF (null = clear)
    fun loadSlot(session, file: File, target: FxTarget, slot: Int)       // saved .lsdfx
    fun swapSlots(a: FxTarget, aSlot: Int, b: FxTarget, bSlot: Int)
    fun setSlotEnabled(target: FxTarget, slot: Int, enabled: Boolean)
    fun drainOnGlThread(mixer)   // called once per frame from the render loop, next to the deck preset queue drain
}
data class FxTarget(val bankId: String)   // deckA_fx … masterFx; resolves the chain via FxTargets.chainFor
```
- Every op runs on the GL thread and then calls `armSlotTakeover`, `FxMacroSync.syncFor` and the undo push.
- Callers to move onto `FxOps`:
  - the three Perf row popups, both Perf drop targets and the Deep Edit slot picker / drops;
  - `FXBrowserPanel` "Load to …" menus, double-click and the `BrowserActionToolbar.handleDeckLoad` FX branch;
  - `FXItemApplier`, which takes an `FxTarget` instead of a `Deck`.
- Fixes "loads from the browser/toolbar/Deep Edit never refresh the knob labels".
- Note: `ParametersUndo` snapshots modulators only (`ParametersUndo.kt`), so slot and chain changes aren't really undoable
  today. That doesn't change in this plan. Revert (Step 3) covers the main "oops" case.

### 1e. Tests and docs for Step 1

- Tests:
  - Delete `FxBankTest.kt` and the bank-generation block in `FXPresetSerializationTest.kt` (:695–790).
  - Rewrite `FxMacroSyncTest.kt` for `Master/FX/...` paths.
  - Update `MacroEngineTest.kt` (:396–409), `MasterFxTest.kt` (`masterFxChain` round-trip), `StarterFxAndLegacyBanksTest.kt`
    (starter chain loads; drop the legacy decode) and `MacroBindingNavTest.kt:28`.
  - New: an `FxOps` test that a chain applied from a background future isn't visible until `drainOnGlThread`.
- Docs:
  - `performance_controls.md:112–138` and `presets_and_library.md:78`, :133–154 (the bank section, which is already wrong about slot orders).
  - `preset_management.md:169`, :200; `macros_and_rack.md:330`; `ARCHITECTURE.md:164–167`, :220; `ROADMAP.md:23`, :74.
  - DECISIONS.md entry superseding :14–22. Matching `[Unreleased]` notes in both release-notes files.

---

## Step 2 — Slot cells on every FX row

### 2a. Layout

- One shared drawer, `ui/FxSlotCell.kt`, is used by every FX row variant (ALL FX, LIVE CONSOLE, deck row in FX mode).
  It's drawn under each of knobs 2–4 (`PerformanceMatrixPanel.kt:823–931`).
- Cell width = the column pitch (`maxColW`), not `knobColW`, so there's room for the name. Height is one `CTRL_H` (24px) line.
  Row height grows by that line. Tune it with `/run` screenshots.
- Contents: `[● on/off] [◀] Effect Name [▶]`. The name truncates, and its tooltip shows the full name and category.
  - The knob's own label (currently the effect name) switches to what the knob actually drives: `META` or the bound param name.
- Keep the existing link/unlink icon (:835–876) where it is.

### 2b. Interactions

- **Click name** → the picker, with **Stock** and **Saved** tabs.
  - `ShaderPickerPopup` (`ui/ShaderPickerPopup.kt:62`) gets
    `showFx(title, slot, callback: (FxPick) -> Unit)`, where `FxPick = Stock(id) | Saved(file) | None`.
  - Saved = `scanAllFxPresets()` (`.lsdfx`).
  - Clean up the unused `PickerType.FX_SLOT_4` (:26).
- **◀ / ▶ or mouse wheel over the name** → next/previous in the **FX shortlist**:
  - Favourites: a ☆ toggle on each picker row, stored in app preferences as an ordered id list.
    Nothing like this exists today, so it's new.
  - If the shortlist is empty or doesn't contain the current effect, step through the current effect's category, alphabetically.
    That way ◀ ▶ is useful with zero setup.
- **● pill** → `FxOps.setSlotEnabled`. The renderer already skips disabled slots (`Renderer.kt:390`).
  Show disabled slots dimmed, with the knob greyed out.
- **Drag cell → cell** (new payload `"FX_SLOT"` = bankId + slot):
  - drop onto the same chain = reorder / swap;
  - drop onto another row = swap, or copy with Ctrl held.
  - The swap moves the filter together with its `slotSuperKnobLink` flag and calls `armSlotTakeover` on both slots.
  - Modulators live on the `ModulatableParameter` objects, so they travel with the effect.
  - User-made MIDI/macro bindings to `.../FX2/<param>` stay with the *slot index*, like Mixxx. Documented, not "fixed".
- **Drop onto a cell** from the Library:
  - `.lsdfx` → load into that slot.
  - Stock ISF filters: add a drag source in `FXBrowserPanel` (stock rows aren't draggable today) with payload `"ISF_FILTER"`.
- **Right-click menu**: Replace…, Save as FX preset…, Copy / Paste slot, Reset params, Clear, Edit in Deep Edit
  (opens the row's Deep Edit with that slot expanded).

### 2c. Fade on swap

- Every slot replacement through `FxOps` (◀▶, picker, drop, swap, clear) does a **dip**:
  1. ramp the slot's output gain 1→0,
  2. swap on the GL thread at 0,
  3. ramp 0→1.
  Default 150 ms total; 0 = hard cut. Stored in preferences, set in the FX section of Preferences.
- Chain loads dip the whole chain the same way, using a chain-level gain.
- Implementation: `FxChain` gets `slotGain: FloatArray(3)` and `chainGain`, plus a small per-slot state machine advanced in
  `FxChain.update(dt)`. `renderFxChainPass` multiplies these into the existing per-slot and chain wet
  (`Renderer.kt:400–413` and the chain blend). No allocation in the render loop, and the user's `dryWet` value and its
  modulation are never touched.
- There's no tween utility in the codebase (only ad-hoc ramps in `Mixer.update` and the MIDI/OSC slew), so it lives in FxChain.
- Feedback/trail effects start from cleared buffers; the fade-in covers the rebuild.
- A true crossfade (old effect keeps rendering while the new one fades in) is deferred. It needs a spare slot's worth of FBOs
  per chain and is only worth it if the dip reads badly live.

### 2d. Swap-timing test (answers "does swapping hitch?")

- Code finding: shaders compile once at startup (`ISFFilterRegistry.loadAll`). `createFilter` = `clone()`, which **shares the
  compiled program and imported textures** (`ownsShader = false`). A swap costs the `ISFFilter` constructor (param map,
  override-file check in `ISFAutoBindEngine.loadOverride`) plus multipass FBO allocation on the first render.
- Measure:
  - Temporary `nanoTime` instrumentation around `FxOps` slot replacement and the next 3 frame times.
  - Use `/run` to cycle ◀▶ through ~30 filters, including multipass/persistent ones, on 2 decks at once.
  - Report worst-case frame spikes.
- If first-use spikes show up (driver-deferred compile, FBO allocation), pre-warm the shortlist in the background:
  render each shortlist filter once into a 16×16 FBO at startup. Remove the instrumentation afterwards.

### 2e. Tests and docs for Step 2

- Tests: slot swap / reorder (link flags and takeover move with the filter), shortlist stepping (shortlist, then category
  fallback, including wraparound), the dip state machine (gain curve, swap happens exactly once, 0 ms = immediate),
  `setSlotEnabled` rendering skip.
- Docs: new "FX slot cells" section in `docs/user_guide/performance_controls.md`, `macros_and_rack.md` FX Rack section,
  tooltips on every cell control, DECISIONS.md, both release-notes files.

---

## Step 3 — Chain header

### 3a. Tracking source and dirty state

- `FxChain` gains `sourceFile: File?` and `baselineDto: FXChainDto?`, set by `FxOps.applyChain` / `loadChain` and after Save.
  `name` stays.
- `isDirty` = `baselineDto != toFxChainDto(baselineDto.name)`, the same data-class comparison `PresetManager.isDeckDirty`
  (:69–80) uses.
  - Checked at ~4 Hz per chain, not every frame, because `toFxChainDto` allocates.
  - No baseline (New / session-restored chain) → dirty once anything is in it.
- Persist `sourceFile` in the session so the name and Save target survive a restart. That's a new optional path field next to
  each deck's and master's chain DTO.

### 3b. One shared header

- New `ui/FxChainHeader.kt`, used by all three FX row variants. It replaces the 3 copies of the Preset ▾ popup
  (`PerformanceMatrixPanel.kt:1365–1402`, :1515–1552, :1701–1724) and the 2 copies of Bypass/Resync (:1557–1599, :2109–2148).
  Each variant keeps only its own leading element: target badge, A/B/BG/PV/MST selector, or SRC|FX toggle.
- Layout: `[◀] Chain Name • [▶]  [Save] [⋮]  …  [BYPASS]`
  - **Name** click → chain browser popup: search box plus a list.
    `scanAllFxChains()` is currently called every frame while a popup is open, so cache the list on popup open instead.
  - **◀ ▶** → previous/next `.lsdfxchain` in the loaded chain's folder, alphabetical order (the root if there's no source file).
    - FX playlists / FXQ stay in the Library. They target decks by crossfader side, not by row, so tying row stepping to them
      would be confusing.
  - **• dirty dot** + **Save**: Save overwrites `sourceFile`. It's disabled when there's no source, and then acts as Save As.
  - **⋮ menu**:
    - Save As…: `SavePresetModal.request`, as in `ParametersTabs.kt:640–653`. Unlike there, capture the DTO when the user
      confirms, not when the menu opens.
    - New (empty chain, name "Untitled", no source), Revert (re-apply `baselineDto`, disabled when clean), Clear,
      Copy / Paste chain, Resync knobs (demoted from a button; once 1c lands it should rarely be needed).
  - **BYPASS** stays a top-level button.
- Dropping a `.lsdfxchain` on the row title band routes through `FxOps.loadChain` (`PerformanceMatrixPanel.kt:640–697`).
- Loading a chain over a dirty one just loads it. No prompt (decided).

### 3c. Tests and docs for Step 3

- Tests: dirty tracking (load → clean; tweak → dirty; save → clean; revert → clean), New/Revert semantics, ◀▶ folder stepping
  and wraparound, Save-with-no-source → Save As.
- Docs: `performance_controls.md` chain-header section, tooltips, DECISIONS.md, both release-notes files.

---

## Order and checkpoints

1. **1d (`FxOps` + GL-thread drain)** first. It's a live bug fix and everything else builds on it.
2. 1a–1c, then 1e. **Checkpoint:** `./gradlew build` green; `/run`: master FX renders, knob labels correct on every FX row,
   Resync on the MST row no longer breaks bindings.
3. Step 2, starting with the timing test (2d) on a bare ◀▶ without the fade, so the numbers are raw. **Checkpoint:** screenshots
   of the row layout for your review before polishing.
4. Step 3. **Checkpoint:** build + `/run` walkthrough (New → pick 3 effects → Save As → tweak → Revert → ◀▶).

One commit per step (1d can go in its own commit).
