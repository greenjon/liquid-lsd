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

### 4.2 Two range-editing widgets

The same `MacroBinding` min/max is edited with numeric scrubbers in the strip and with draggable brackets in Properties. They differ in feel and precision, and the strip has no visual of where the range sits within the parameter's span.

Recommendation (§6.2): keep editing in both places, but use one shared range widget, sized differently, so there is one mental model.

### 4.3 Generator change replaces the macro bank

Choosing a new source in `DeckSourcePicker` (`:59`) calls `GeneratorDefaults.applyToDeck`, which resets the source's parameters and installs the default macro bank over the deck's bank.

What the code does:

- Stock generators carry no bank of their own. Their bank comes from `GeneratorDefaults.resolve(source)`, either a user-saved default or the factory table.
- `saveDefault` stores bindings with deck-agnostic parameter IDs, so a bank saved for one source is not meaningful for a different source.
- `ParametersUndo` snapshots modulators only, so Ctrl+Z does **not** restore the previous macro bank. (An earlier draft of this review said it did; that was wrong.)

So the previous bindings are lost, and nothing told the user. **Update (Oct 2026):** a toast now reports the replacement (`ToastOverlay`, `GeneratorDefaults.applyToDeck` return value). Restoring the old bank is still open; see §6.2.

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

- **Shared range widget (§4.2).** One compact widget used by both the strip and Properties.
- **Clearer bay tabs (§3.3).** Visually separate `Edit` from the picker tabs (for example a divider or a "Pick:" label).
- **Queue visibility in Edit view (§5.1).** At minimum a compact "next up" readout. A resizable Library dock in Edit view is a larger layout change and conflicts with the three-view decision, so treat it as a separate proposal.
- **Scene bundles (§5.2).** A schema referencing deck presets, master FX chain, active transition and controller profile.
- **Restore the replaced bank** (stash it on swap, offer a restore action), or make `ParametersUndo` snapshot macro banks.
- **Optional "lock macro bank" per deck** so a custom bank survives source changes.

### 6.3 Not recommended

- **Carrying bindings across generators by parameter name** (see §4.3).
- **Removing numeric/range editing from the Edit-row strip.** It would force a trip into Deep Edit for every binding tweak, which undoes the Phase 2 work that put binding editing on the row.
