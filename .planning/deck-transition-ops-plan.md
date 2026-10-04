# DeckOps / TransitionOps Plan

**Status**: Phases 0 and 1 DONE 2026-10-03. Phase 2 DONE 2026-10-03 (907 tests pass): `DeckOps`, `manualLoadDirtyBehavior`, injected hooks, `loadDeckPresetAsync` shim, bank remap, `DeckOpsTest`. Deviations: undo is pushed for MANUAL `Source`/`Preset` only (eject/copy/move/swap can reset FX, which isn't captured); the old `applyPendingPresets` and per-deck queues were deleted rather than delegated; `DeckSourcePicker.swapSource`, `guardDeckTransition` and the dead file browsers were already removed in Phase 2; "Don't ask again" was added to the prompt; `DeckUtilityTest` was folded into `DeckOpsTest`. Phase 3 DONE 2026-10-03: all callers on `DeckOps.request`, shim deleted, `handleDirtyDeck` removed from Play/Bg/FX queues. `request` now returns Boolean (false = dropped; queue managers leave position unchanged) and `wouldSkipQueueLoad` lets BG skip before its dip-to-black. FX-queue skip tests deleted (D17). `UIManager`/`DeckPresetController` wrappers kept as thin delegates. Phase 4 (docs) DONE 2026-10-03. Phase 5 (video export waits for its snapshot) DONE 2026-10-04. Decisions confirmed by the user: manual loads PROMPT by default (no existing users, so no compatibility concern); clean-deck source change no longer prompts; presets without a bank get the generator default bank; D7 (stable modulator IDs) moves into v1.0 scope and is tackled after DeckOps.
**Source**: `docs/developer/ui_interaction_architecture_review.md` §7
**Scope**: v1.0 (stability). It removes code paths and adds no features. Defect numbers (D1–D17) refer to review §7.2.

## Goal

Every change to what a deck or the transition holds goes through one entry point, the way FX changes already go through `FxOps`. The undo, dirty-guard, macro-bank, toast and threading rules then live in one place instead of being reimplemented by each of about 40 call sites.

## Design

### DeckSlot (phase 0)

A new `presets/DeckSlot.kt` defines `enum class DeckSlot(val label: String, val bankId: String, val index: Int) { A, B, BG, PV }` with `of(deck, mixer)` and `deck(mixer)`.

`PresetManager` gets per-slot accessors: `activePreset(slot)`, `cachedDto(slot)`, `setActive(slot, name, dto)`, `clearActive(slot)` and `mtime(slot)`. The existing `activePresetA`/`cachedDtoA`/... fields stay as the backing store for now. They have 182 uses in 15 files. Only the files this plan touches are migrated to the accessors, and the rest are left alone, so there is no big-bang rename.

### DeckOps (`presets/DeckOps.kt`)

```kotlin
sealed interface DeckChange {
    data class Source(val source: VisualSource) : DeckChange
    data class Preset(val file: File) : DeckChange
    object Eject : DeckChange
    data class CopyFrom(val from: DeckSlot) : DeckChange
    data class MoveFrom(val from: DeckSlot) : DeckChange
    data class SwapWith(val other: DeckSlot) : DeckChange
}
enum class LoadOrigin { MANUAL, QUEUE }   // QUEUE = A/B, BG and FX queue advances, AutoVJ

object DeckOps {
    fun request(slot: DeckSlot, change: DeckChange, origin: LoadOrigin = LoadOrigin.MANUAL)
    fun drainOnGlThread(mixer: Mixer)   // replaces PresetManager.applyPendingPresets
    fun isDirty(slot: DeckSlot, mixer: Mixer): Boolean
}
```

`request` runs these steps in order:

1. **No-op check.** For a `Source` change where `deck.source.id == source.id` and the source isn't external video, nothing happens. This fixes D5's reset-on-re-pick.
2. **Dirty guard.** One policy function replaces the four existing copies: `DeckPresetController.guardDeckTransition`, `PlayQueueManager.handleDirtyDeck`, `BgQueueManager.handleDirtyDeck` and `FxQueueEngine.handleDirtyDeck`.
   - `MANUAL` uses a **new** preference, `manualLoadDirtyBehavior` { PROMPT, DISCARD, AUTO_SAVE }, defaulting to **PROMPT** (D1).
   - `QUEUE` keeps `autoVjDirtyBehavior` unchanged.
   - Prompting goes through an injected `DeckOps.prompt: (slot, onProceed) -> Unit`, which `UIManager` wires to `PopupManager.requestDeckConfirm`. If nothing is wired (tests), the change proceeds.
   - `requestSourceChangeConfirm` is removed. A clean deck with an active preset no longer prompts, because the change can now be undone (D5).
3. **Preset changes** are read and migrated on `presetIoExecutor` (the code moves out of `PresetRepository.loadDeckPresetAsync`), then posted. All other changes are posted immediately.

`drainOnGlThread` applies one op per deck slot. One loop replaces the four copied blocks in `PresetManager.applyPendingPresets`. For each op it:

1. **MANUAL only:** captures undo. A snapshot of the deck DTO, the macro bank's knobs, and the active preset name and cached DTO goes to an injected `DeckOps.undoSink: ((() -> Unit) -> Unit)?`, which `UIManager` wires to `ParametersUndo.pushUndoState`. Queue loads don't push undo, so AutoVJ doesn't flood the stack. The existing `DeckSourcePicker.captureDeckForUndo` becomes this function, extended to the whole deck DTO.
2. Applies the change:
   - `Source`: clone it, set `isEmpty = false`, `GeneratorDefaults.applyToDeck`.
   - `Preset`: `applyDto`.
   - `Eject`: `deck.reset()`.
   - Copy, move or swap: `applyDto` from a snapshot taken at drain time.
3. **Installs the macro bank (one policy):**
   - `Preset` with a bank: `installPresetBank`.
   - `Preset` with no bank (legacy or hand-made; app saves always attach one): the generator's default bank, not a blank one (D11).
   - Copy, move or swap: the source deck's bank is remapped with `installBankForDeck`, which fixes D6. Swap snapshots both banks before writing.
4. **Bookkeeping:** active name, cached DTO and mtime, `NotesManager.syncFromDto`, and `PlayQueueManager.notifyManualDeckLoaded` for MANUAL loads to A, B or PV. After a `Source` change, the cached DTO is set to a **baseline** of `deck.toDto(source.displayName)` with the active name null, so tweaks to a fresh generator show as dirty (D2).
5. **Toast** when the previous bank had bindings that the new bank replaced. Use the same check `GeneratorDefaults.applyToDeck` returns, generalised. The text reads "…Ctrl+Z to undo" only when undo was pushed.
6. Once per drain, if anything was applied: `MidiMappingManager.invalidateBindings()`, `ParameterResolver.clearCache()` and `BroadcastEngine.notifyStateChanged()`. These run today too.
7. Runs a UI-side post-apply hook for `Source`: `state.clearSelection()` and `setDeckSubTab(label, "SRC")`. This is injected the same way, since `presets` shouldn't reach into `ParametersState`.

**Dirty comparison (D4):** `isDirty` compares `toDto` **plus** a bank snapshot against the cached DTO plus the bank captured at load or save. `PresetManager.isDeckDirty` delegates to it.

### TransitionOps (`presets/TransitionOps.kt`)

```kotlin
object TransitionOps {
    fun setStock(id: String?)          // null → linear_crossfade
    fun loadPreset(file: File)         // read on presetIoExecutor, then post
    fun applyItem(file: File)          // .lsdtrans → loadPreset, else stock id from file name
    fun drainOnGlThread(mixer: Mixer)
}
```

- `drainOnGlThread` is called in `Main.kt` next to `FxOps.drainOnGlThread`.
- If a preset fails to load: log it, show a toast, and fall back to the stock id. This is today's `TransitionQueueManager` behaviour, now shown to the user.
- `TransitionQueueManager.applyTransitionItem` delegates to `applyItem`.

## Phases

### Phase 0: DeckSlot and accessors (DONE)
- Add `DeckSlot` and the `PresetManager` per-slot accessors.
- Migrate `DeckLifecycleManager`, `DeckPresetController` and `PresetManager` itself.
- No change in behaviour. Test: round-trip of the accessors against the backing fields.

### Phase 1: TransitionOps (smallest, fixes D12) (DONE)
- Add `TransitionOps` and call it from `Main.kt`.
- Replace the call sites:
  - `PerformanceMatrixPanel.kt:503-517`
  - `MixerPanel.kt:329-342`
  - `PerformanceTransitionsControls.kt:157-170` and `:426-439`
  - `TransitionBrowserPanel.kt:234,270-279`
  - `PerformanceBrowseBay.kt:166`
  - `ParametersTabs.kt:381`
  - `TransitionQueueManager`
- `SessionSerializer` restore can stay direct: it runs at startup on the main thread.
- Test, `TransitionOpsTest` modelled on `FxOpsTest`: nothing is applied before the drain; a preset is applied after the drain; a bad file falls back to the stock id.

### Phase 2: DeckOps core
- Add `DeckOps`, the `manualLoadDirtyBehavior` preference (`AppPreferences`, `AppPreferencesStore`, the Preferences panel next to the AutoVJ setting, a tooltip), and the injected prompt, undo and post-apply hooks wired in `UIManager`.
- `PresetRepository.loadDeckPresetAsync` becomes a deprecated shim that calls `DeckOps.request(slot, Preset(file), origin)`. Existing callers then get the guard and undo at once, which closes D3 before the call sites are migrated.
- `PresetManager.applyPendingPresets` delegates to `DeckOps.drainOnGlThread`.
- `DeckLifecycleManager` copy/move/swap are called only from the drain, and the bank remap is added (D6).
- Tests:
  - `PresetDirtyLoadingTest` and `DeckSourceSwapUndoTest` updated.
  - New `DeckOpsTest`:
    - a preset load captures undo and the undo restores the deck, bank and name
    - a queue-origin load pushes no undo
    - a source swap sets a dirty baseline (D2)
    - re-picking the same source does nothing
    - copying A→B moves A's bank, remapped to `Deck B/...` (D6)
    - a preset with no bank gets the generator default (D11)
    - a bank edit makes the deck dirty (D4)
    - MANUAL uses the manual preference and QUEUE uses the AutoVJ one

### Phase 3: Migrate call sites and delete duplicates
- Route each of these through `DeckOps.request`:
  - `DeckPresetController`: `loadDeckPresetSafely`, `changeVisualSourceSafely`, `handleUtilityAction`, `handleEjectDeck` and `newPresetSafely` become thin wrappers or are removed. Delete the dead `loadDeckPreset`, `performLoadDeckPreset`, `deckA/BFileBrowser` and `drawFileBrowsers`, and the `UIManager.kt:300` call.
  - `DeckSourcePicker.swapSource` and `changeSource`: the undo capture moves into `DeckOps`.
  - `PerformanceDeckControls` "Apply Default Now" and "Reset to Factory" become `Source` re-applies with a force flag, so they get undo and a toast. Today they have neither.
  - `PerformanceMatrixPanel` and `DeckControlPanel` drops: remove the inline duplicate dirty checks.
  - `PerformanceBrowseBay.kt:113` saved-preset pick.
  - The four "Load to Deck" context menus: `PresetListPanel`, `QueueActionsPanel`, `BgQueueActionsPanel` and `PlaylistEditorPanel`. They all call `BrowserDeckButtons.loadPresetToDeck`, and that one function calls `DeckOps`.
  - The launchpad (`DeckSourcePicker.kt:202`), `LibraryNavigation` accept, `MenuBar` New Preset, and `VideoExportModal` (check what it loads).
  - `PlayQueueManager` and `BgQueueManager` use `origin = QUEUE` and drop their own `handleDirtyDeck`. `FxQueueEngine.handleDirtyDeck` is deleted: FX changes don't touch the deck preset (D17).
- Remove the `loadDeckPresetAsync` shim once there are no callers. `grep loadDeckPresetAsync` is the done-check.

### Phase 4: Docs, release notes, tooltips
Per the repo's definition of done:
- `RELEASE_NOTES.md` and `docs/release_notes.md`:
  - manual loads now prompt by default
  - loading a preset can be undone
  - Copy/Move/Swap carry the knob bank
  - a fresh generator now shows unsaved changes
  - a transition drop no longer risks a GL crash
- User guide: a dirty-state and undo section, and the new preference.
- `DECISIONS.md`: the entry-point rule ("deck/transition changes go through DeckOps/TransitionOps only").
- Regenerate the HTML docs (`./gradlew generateDocs`).

### Phase 5: Video export waits for its preset snapshot (DONE 2026-10-04)
- `DeckOps.request(..., onResult: ((Boolean) -> Unit)?)`: true after the drain applied the change, false if dropped, cancelled, unreadable or failed. `DeckOps.prompt` and `PopupManager.requestDeckConfirm` gained a cancel continuation (a newer prompt cancels an older one).
- `VideoExportModal` starts `OfflineRenderStudio.startExport` from the callback, aborts with a status message on false, and disables Start Export while waiting.
- Tests: three `onResult` cases in `DeckOpsTest` (applied, prompt cancelled, queue skip).

## Out of scope (separate small v1.0 fixes, see review §7.4)
- D8: MIDI/OSC on a knob-bound parameter. Lock or indicate it, like the macro lock.
- D9: re-enabling a binding. Editors should read `control.bindings`, not the resolved cache.
- D10: lock only for base-value bindings.
- D13: Missing Files relink by asset type.
- D14: toolbar Q uses the visible tab's selection.
- D15: Ctrl+F focuses the active tab's search.
- D16: FX chain `markClean` on every save path, with the DTO captured at confirm time.
- D7 stopgap: remap or drop modulator-property bindings when modulators are removed. Stable modulator IDs are v1.1.
- ~~Video export with a preset snapshot~~ moved to Phase 5.

## Risks
- **The prompt now defaults on.** Performers used to silent discard will see dialogs. Mitigation: the preference is in the first-run notes, and the prompt has a "don't ask again" option that sets DISCARD.
- **Async undo ordering.** Undo is captured at drain time, not request time, so an undo between the request and the drain is a no-op for that load. That is acceptable, since the drain happens on the next frame.
- **Whole-deck DTO snapshots are larger than modulator snapshots.** The undo stack depth is unchanged, so memory stays bounded.
