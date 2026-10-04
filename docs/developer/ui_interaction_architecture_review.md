# UI Interaction Architecture Review: Performance, Parameters, Browse, and Library

**Status**: Architecture review & UX analysis (revised after verification against the code)
**Target Area**: `ui/`, `macro/`, `presets/`, `parameters/`
**Date**: October 2026

---

## 1. Summary

Liquid LSD's interface grew feature by feature. The individual pieces work well, but four areas have friction where the pieces meet:

1. Loading visual generators and FX chains
2. Editing and modulating parameters (Deep Edit)
3. Binding and managing macro knobs
4. The Library and play queues

This document records the friction points that were checked against the code, separates real defects from design observations, and sorts the recommendations by release. **v1.0 is feature-frozen** (polish, usability, stability), so only the items in §6.1 are candidates for v1.0. Everything else is v1.1.

A second pass (§7, October 2026) traced every load path, binding path and Library flow end to end. It found that most of the remaining friction comes from one cause: FX loads go through a single entry point (`FxOps`), but generator loads, transition loads and macro binding do not. Each UI element reimplements the rules for undo, the dirty guard, the macro bank and threading.

Decisions this review takes as given: the three-view layout (Perform / Edit / Library), where Edit view replaces the Library rather than sharing the screen with it; inline Browse inside the Edit row (`PerformanceBrowseBay.kt`); and macro editing in the Edit-row strip plus Properties.

---

## 2. Workspace Topology

```
┌────────────────────────────────────────────────────────────────────────┐
│ MENU BAR                                                               │
├────────────────────────────────────────────────────────────────────────┤
│ 1. PERFORMANCE MATRIX (rows)                                           │
│    • 4 macro knobs per row; tabs DECKS and MASTER                      │
│    • Left: [SRC|FX] pill, badges, queue nav, FX chain controls         │
├────────────────────────────────────────────────────────────────────────┤
│ 2. DEEP EDIT BAY (only when a module is expanded)                      │
│    • Params: parameter grid + side rail + Properties                   │
│    • Browse: inline source / FX / transition picker                    │
├────────────────────────────────────────────────────────────────────────┤
│ 3. LIBRARY DOCK                                                        │
│    • Presets/FX/Trans | Playlists | Live Queue | BG Queue              │
└────────────────────────────────────────────────────────────────────────┘
```

Which views are drawn (`UIManager.kt:483-490`, `LibraryPanel.isEditView`):

| View | Condition | Library height |
|---|---|---|
| Perform | `libraryMode == HALF`, no module expanded | `libraryRatio` of content |
| Edit | `anyRackModuleExpanded() && libraryMode != FULL` | `0` (not drawn) |
| Library | `libraryMode == FULL` | whole content area |

Edit and Library views are mutually exclusive by design.

---

## 3. Loading Generators and FX

### 3.1 Two browsers, no shared model

The Library dock and the inline Browse bay both browse presets and sources, but share no state.

| | Docked Library (`LibraryPanel`, `PresetListPanel`, `FXBrowserPanel`, `TransitionBrowserPanel`) | Inline bay (`PerformanceBrowseBay`, `ShaderPickerPopup`) |
|---|---|---|
| Purpose | Curating queues and playlists | Swapping the source or FX of one slot |
| Search buffer | Three separate buffers, one per panel (256 chars) | One in `ShaderPickerPopup` (256 chars since Oct 2026; was 64), plus one for the chain list |
| Action | Drag, `[Q]`/`[BGQ]`, context menu | Click replaces the slot, with undo |
| Playlists / queues | Yes | No |

Having two surfaces is intended. The problem was that search matching was implemented separately in each, so the same text gave different results. **Fixed Oct 2026:** all of them now use `SearchMatcher` (same rule, same buffer size). Still separate: the buffers themselves and the picker's category chips.

### 3.2 Pill / bay state can drift (confirmed bug, fixed Oct 2026)

> **Fixed**: `deckRowMode` was removed and the deck sub-tab is now the only stored SRC/FX state. The text below describes the bug as found. `masterRowMode` was removed the same way (the Mixer sub-tab is the only stored state).

A deck row's `[SRC|FX]` pill and the Deep Edit bay both feed one predicate, `PerformanceUiContext.isDeckRowFx` (`PerformanceUiContext.kt:111`):

```kotlin
deckRowMode[tag] == "FX" || parametersState?.getActiveDeckSubTabByTag(tag) == "FX"
```

Two pieces of state are OR-ed together, and only one of them is reset by the bay:

- Pill click to FX sets both `deckRowMode[tag] = "FX"` and the deck sub-tab to `"FX"` (`PerformanceDeckControls.kt:299-301`).
- Clicking the bay's `SRC` tab calls `openGenBrowse`, which sets only the sub-tab to `"SRC"` (`ParametersState.kt:226-228`). `deckRowMode[tag]` stays `"FX"`.
- Result: the bay shows source browsing while the row's knobs and pill still show FX. The OR keeps the row in FX mode.

The opposite direction is milder. Clicking the pill while the bay is in a Browse tab changes the sub-tab, but the bay's `browseTargetFor` is unchanged, so the bay keeps showing the old picker. Also, `FxHeaderActions.kt:39` and `PerformanceUiContext.kt:154-170` write the two states independently, so every writer has to remember both.

The code comment says "don't re-derive it from the two states", but two sources of truth still exist underneath. The fix is to store one state. The row mode should be derived from, or be the same field as, the deck sub-tab, so the bay's `openGenBrowse` and `openFxChainBrowse` set the row mode as a consequence.

### 3.3 Navigation layers

Getting from "I want to change this effect" to the right control involves up to five independent selections:

1. Row pill: `[SRC]` vs `[FX]`
2. Bay tab: `Edit | SRC | Chain | FX1 | FX2 | FX3` (derived from `sectionModeFor` / `browseTargetFor`)
3. Side rail: `MIX | A | B | BG | PV`
4. Parameter grid sub-tab: `SRC` vs `FX` (Mixer: `CTRL | FX | TRANS`)
5. Library menu: `Sources | FX | Trans`

Layer 5 is a separate view (Library) and not part of the Edit hierarchy, so the real nesting is four. Layers 1 and 4 are the two halves of the §3.2 bug. The bay tab row mixes two meanings: `Edit` opens the parameter grid, and the other tabs open pickers. This is acceptable, because the tab row says which, but is worth labelling more clearly (see §6.2).

A clearer model is *Target (Deck A/B/BG/PV/Master) × View (Perform / Params / Browse)*. This is close to what the code does already. Making it exact is mostly the §3.2 consolidation.

---

## 4. Macro Knobs and Binding

### 4.1 Workflow (as built)

Selecting a knob in Edit view sets `MacroLearnState.selectedControlId`. `PerformanceMacroStrip` then replaces the row's left controls:

- Line 1: name, live value, `Add Target`, target chips ①–④, kebab menu, close.
- Line 2: `MacroBindingEditor.drawLine` (path, min, max, invert, curve, steps).

Clicking a bound parameter cell opens `MacroBindingEditor.drawFull` in `PropertiesPanel`, which uses `CustomRangeSlider` (range brackets plus a live position dot). The grid cells also show a small arc for the macro range.

This is a deliberate design: a performer can edit a binding straight from the row without opening anything else. That should be kept.

### 4.2 Range editing (already one widget)

An earlier draft of this review said the strip only had numeric scrubbers and Properties had the bracket slider. That was wrong (checked Oct 2026): `MacroBindingEditor.drawLine` (strip) and `drawFull` (Properties/popup) both call the same `drawRangeBar` (track, filled segment, two draggable handles, live dot) and the same `drawMinField` / `drawMaxField`. The only difference is that the strip hides the bar when fewer than 50px remain after the lock, path, link, curve and Min/Max controls. No unification work is needed.

### 4.3 Generator change replaces the macro bank

Choosing a new source in `DeckSourcePicker` (`:59`) calls `GeneratorDefaults.applyToDeck`, which resets the source's parameters and installs the default macro bank over the deck's bank.

What the code does:

- Stock generators carry no bank of their own. Their bank comes from `GeneratorDefaults.resolve(source)`, either a user-saved default or the factory table.
- `saveDefault` stores bindings with deck-agnostic parameter IDs, so a bank saved for one source is not meaningful for a different source.
- `ParametersUndo` snapshotted modulators only, and the swap pushed its snapshot after the change, so Ctrl+Z did **not** undo a source change at all. **Fixed Oct 2026**: the swap now pushes a snapshot first with a restore lambda (old source, macro bank, active preset), so Ctrl+Z undoes it.

So the previous bindings were lost, and nothing told the user. **Update (Oct 2026):** a toast now reports the replacement (`ToastOverlay`, `GeneratorDefaults.applyToDeck` return value) and Ctrl+Z restores it. Other macro-bank edits (binding changes, renames) are still not undoable; see §6.2.

**Why not carry bindings over by parameter name:** an earlier draft of this review suggested keeping bindings whose parameter names match in the new generator. That is not safe. Parameter names are reused for unrelated things across generators (for example "Scale", "Speed", "Depth"). Matching by name would quietly aim a performer's knobs at the wrong controls, which is worse than resetting them.

### 4.4 FX knobs are not user-bindable

Deck and Master knobs are freely bindable. FX bank knobs (`DECK_*_FX`, `MASTER_FX`) are assigned by `FxMacroSync` to slot wet/dry and ISF auto-map, and the strip shows a read-only role line for them. This is intentional (FX knobs follow the chain), and the read-only line is the correct response. It only needs to look read-only, not like an empty editor.

---

## 5. The Library

### 5.1 Two jobs in one panel

`LibraryPanel` is both an asset browser (find, filter, dependency check) and a live queue console (A/B queue, BG queue, advance, auto-VJ). While in Edit view both are hidden. Edit view's own inline browser covers "swap this slot", but nothing covers "what's next in the queue". At present the only way to see the queue from Edit view is to leave it.

### 5.2 Asset formats

| Asset | Extension | Root |
|---|---|---|
| Deck preset | `.patch` / `.lsd` / `.json` | `library/presets/` |
| FX slot | `.lsdfx` | `library/fxpresets/` |
| FX chain | `.lsdfxchain` | `library/fxchains/` |
| Transition | `.lsdtrans` | `library/transitions/` |
| Macro bank | `.knobpreset.json` | `library/knobpresets/` |
| Playlist | `.json` | `library/playlists/` |
| Controller profile | `.json` | `library/controllers/` |

Separate formats per asset type are reasonable and each has a clear owner. The one real gap is that there is no way to save or recall a whole *scene* (both decks, master FX, transition, controller profile) as one unit.

---

## 6. Recommendations

### 6.1 v1.0 candidates (polish, stability, no new features)

1. **~~Fix the pill/bay drift (§3.2).~~ Done.** Make the deck row's SRC/FX mode a single stored value (or derive it fully from the sub-tab), and have `openGenBrowse` / `openFxChainBrowse` / the pill all go through one setter. Add a test next to `NavigationSurfaceTest` covering pill→FX→bay SRC tab→row shows SRC.
2. **~~Tell the user when a generator change replaces their macro bank (§4.3).~~ Done.** A toast reports it; there is no undo hint because undo does not cover macro banks.
3. **~~Share one search implementation (§3.1).~~ Done.** `ui/browser/SearchMatcher` is the one matching rule and buffer size; the browsers still keep separate buffers and filter chips.
4. **~~Make FX knobs look read-only in the macro strip (§4.4).~~ Done.** Checked: the strip already hid Add Target, chips and the kebab and showed a disabled role line for FX knobs; only the name tooltip still offered rename, now fixed.

### 6.2 v1.1

- **~~Shared range widget (§4.2).~~ Not needed**: already shared (see §4.2). If the strip's bar vanishes too often at narrow widths, shorten the path label (currently 20% of the strip) rather than adding a widget.
- **~~Clearer bay tabs (§3.3).~~ Done.** A "Pick:" label separates `Edit` from the picker tabs.
- **~~Queue visibility in Edit view (§5.1).~~ Done (readout).** The bay tab row shows what the queue plays next (`QueueNextUp`). A resizable Library dock in Edit view is a larger layout change and conflicts with the three-view decision, so treat it as a separate proposal.
- **Scene bundles (§5.2).** A schema referencing deck presets, master FX chain, active transition and controller profile.
- **~~Undo for macro-bank edits~~ Done.** `MacroUndoTracker` (per-frame change detection) covers add/remove/range/curve/rename/import; source swaps were done earlier.
- **Optional "lock macro bank" per deck** so a custom bank survives source changes.

### 6.3 Not recommended

- **Carrying bindings across generators by parameter name** (see §4.3).
- **Removing numeric/range editing from the Edit-row strip.** It would force a trip into Deep Edit for every binding tweak, which undoes the Phase 2 work that put binding editing on the row.

---

## 7. Second Pass: Entry Points (October 2026)

The second pass traced about 30 load paths, 20+ binding paths and every Library flow, and checked each one against the code. Items marked ✔ were re-read in the code after the trace. The rest are cited by `file:line` from the trace.

### 7.1 Root cause

| Change | Entry point | Undo | Dirty guard | Macro bank | Thread |
|---|---|---|---|---|---|
| FX slot / chain | `FxOps` (one family) | no | n/a | `FxMacroSync` always | queued, GL thread |
| Bare generator | `DeckSourcePicker.swapSource` | yes | separate source-change confirm | generator default bank, toast | UI thread |
| Deck preset | `PresetRepository.loadDeckPresetAsync` (since removed; all callers now use `DeckOps.request`), called from 10+ places | no | only if the caller remembered `loadDeckPresetSafely` | preset bank, no toast | IO, then queued |
| Copy / Move / Swap deck | `DeckLifecycleManager` | no | yes | **not installed** | UI thread |
| Transition | `Mixer.setTransition` / `applyTransitionPreset`, called from 9 places | no | n/a | n/a | **sometimes the IO thread** |

FX loads behave the same whichever button starts them, because there is one entry point. Generator and transition loads do not, because there isn't.

### 7.2 Defects found

**Losing work**

1. ✔ **Manual loads discard edits by default.** `guardDeckTransition` uses the AutoVJ preference `autoVjDirtyBehavior`, which defaults to `AUTO_DISCARD` (`AppPreferences.kt:35`, `DeckPresetController.kt:100-126`). To get a prompt, the user has to pick "SKIP", which also makes queue advances skip.
2. ✔ **A freshly picked generator is never dirty.** `isDeckDirty` returns false when there is no cached DTO (`PresetManager.kt:77`), and `swapSource` clears it. Tweaks to a new generator are never guarded and never marked `*`.
3. ✔ **Some load paths skip the dirty guard.** These called `loadDeckPresetAsync` directly (fixed: all use `DeckOps.request`):
   - the "Load to Deck A/B/BG/PV" context menus in `PresetListPanel`, `QueueActionsPanel`, `BgQueueActionsPanel` and `PlaylistEditorPanel`
   - the empty-deck launchpad (`DeckSourcePicker.kt:202`)
   - the Browse bay's saved-preset pick (`PerformanceBrowseBay.kt:113`). This one also has no undo, although its doc comment says Ctrl+Z works.
4. **Macro-bank edits don't make a deck dirty.** `Deck.toDto` doesn't include the bank; it is added only at save time (`PresetRepository.kt:91`).
5. **The guard depends on the path for other cases too.** The source-change confirm ignores the dirty preference and fires whenever a preset name is active, even on a clean deck (`DeckPresetController.kt:231`). Re-picking the current generator resets the deck: the `==` check at `:217` compares a clone, and `VisualSource` has no `equals`.

**Knobs and bindings**

6. ✔ **Copy / Move / Swap deck leave the knob bank behind.** `DeckLifecycleManager` calls `Deck.applyDto`, which never installs a macro bank. After copying A→B, Deck B's knobs still target B's old generator.
7. ✔ **Modulator-property bindings break when modulators are removed.** These bindings store a position in the modulator list (`MacroModels.kt:65`). Removing modulators doesn't update them: `PropertiesPanel.kt:380-388`, "Clear all CVs", and MIDI unbind. The binding then drops silently (`MacroEngine.kt:340`) or starts driving the wrong modulator.
8. ✔ **MIDI and OSC mappings on a knob-bound parameter do nothing.** `MacroEngine.tick` writes `baseValue` every frame, after the mappings have.
9. **A disabled binding can't be re-enabled in the editor that disabled it.** The slider popup and Properties editor read the resolved cache, which skips disabled bindings. Only the strip can re-enable it.
10. **"Bound" doesn't distinguish base-value bindings from modulator-property bindings.** `findPrimaryBindingInfo(null, paramKey)` matches any binding on the parameter. The VAL cell and base slider lock when only, for example, the LFO depth is bound, while the arcs show the parameter as unbound.
11. **Preset loads install a blank bank when the preset has none** (`MacroBankSerializer.kt:72-81`). Bare-source loads install the generator default instead. Preset loads never toast.

**Threading and Library**

12. ✔ **`.lsdtrans` drops apply the transition on the IO thread.** Four drop sites, plus the TransitionBrowser menu, call `applyTransitionPreset` inside `thenAccept`. `setTransition` disposes and creates GL filters there. `TransitionQueueManager.applyTransitionItem` instead reads the file synchronously on the UI thread.
13. ✔ **Missing Files "Locate…" always appends to the A/B play queue,** whatever kind of item was missing (`MissingItemsPanel.kt:53`).
14. **Library toolbar Q can queue a hidden selection.** After clicking in the FX or Trans tab, Q still queues the Sources tab's multi-selection (`BrowserActionToolbar.kt:262-265`).
15. **Ctrl+F focuses only the Sources search** and collapses the Edit view.
16. **The FX chain dirty dot is only cleared by the chain header's save.** Saving from the Deep Edit kebab or Library "+" leaves it on, and both capture the DTO when the menu opens, not when the save is confirmed.
17. **The FX queue checks the deck *preset's* dirty flag** (`FxQueueEngine.kt:40`), even though it only changes FX.

### 7.3 Inconsistencies (design)

**Loading**

- **Loading a single `.lsdfx` into a full chain has three different outcomes:**
  - FXBrowser double-click overwrites slot 1 silently.
  - The audition latch asks which slot to overwrite.
  - The FX queue and FX playlist replace the whole chain.
- **Target deck rules differ.** Preset accept and the queues use the *inactive* A/B deck. FX double-click, the FX queue and the FX playlist use the *live* deck. BG and PV can't be targeted from the keyboard or MIDI.
- **Queue double-click and MIDI accept do different things.** Double-click moves the queue index, auto-fades and advances the transition queue. MIDI accept just loads.
- **Labels and drop targets are inconsistent.**
  - "Reset Slot" clears the slot, while elsewhere "Reset Parameters" resets values.
  - In the Library, "+" means "empty the deck" in Sources but "save current" in FX and Trans.
  - Drop targets accept different file types.

**Binding**

- **Seven systems can drive a parameter:** macro bindings, the modulator stack, ISF meta-bindings, Super Knob, MIDI, OSC and controller profiles. Three of them write `baseValue`.
- **"Learn" has three meanings:** macro target, MIDI and OSC. Up to four modes can be armed at once, and Esc cancels two of them.
- **Knob selection is stored twice** (`MacroLearnState.selectedControlId`, `ParametersState.selectedRackMacroId`). A parameter click sets only the first.
- **FX-bank bindings look editable** in Properties and the slider popups, but `FxMacroSync` overwrites the edits on the next re-sync.

**Library**

- **Leaving Edit collapses the expanded module**, and there is no way back to it.
- **Transitions can only be saved from the Library.**
- **Macro banks and Perform pages never appear in the Library.**
- **FX favourites exist only in the inline picker.**

### 7.4 Recommendations

Use three entry points, modelled on `FxOps`, rather than patching each button. The implementation plan is in `.planning/deck-transition-ops-plan.md`.

1. **`DeckOps`** is the only way to change what a deck holds: bare source, preset file, eject, copy/move/swap. It owns:
   - the dirty guard, with its own manual-load preference that defaults to Prompt
   - undo
   - the macro-bank policy and toast
   - per-deck bookkeeping

   It fixes defects 1–6 and 11 and removes four copies of the dirty policy.
2. **`TransitionOps`** queues every transition change on the GL thread. It fixes defect 12 and replaces five copied drop blocks.
3. **Bindings get stable modulator IDs and one knob-selection state.** This fixes defects 7 and 9 and the selection drift. It changes the saved-file format, so it is v1.1. The v1.0 stopgap is to remap or drop bindings when a modulator is removed.

Defects 8, 10 and 13–17 are small local fixes and fit v1.0. The design items in §7.3 are v1.1.
