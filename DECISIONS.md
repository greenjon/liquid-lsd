## Macro Link Modes: Mixxx-Style Knob-Travel Windowing (`MacroModels.kt`, `MacroCurve.kt`, `MacroBindingInspector.kt`, `MacroCurveTest.kt`, `DECISIONS.md`, `RELEASE_NOTES.md`, `docs/user_guide/macros_and_rack.md`)

- **Context**: 2026-09-22. A single macro knob can already hold up to 4 bindings (`MacroControl.MAX_BINDINGS_PER_CONTROL`), but every binding always tracked the knob's full 0–100% travel identically — there was no way for two bindings on the same knob to respond to different parts of the turn (e.g. crossfade between two targets, or have a second target only kick in past the halfway point) short of manually authoring a matching Min/Max + curve per binding, which doesn't actually zone the travel, just rescales the output range. Mixxx solves this in its Effect Metaknob system with named "link modes" (Full, First Half, Second Half, Triangle, Superknob/Bipolar) that window the knob's normalized position *before* curve shaping. An implementation plan proposing this (plus a parallel multi-binding extension to the ISF FX Metaknob system) was reviewed; the FX-Metaknob half was scoped out to `ROADMAP.md` (v1.1 Backlog, Milestone 4) pending open questions — notably whether it's redundant with this macro-side feature — while the macro-side half was implemented directly since `MacroBinding` already supports multi-binding-per-knob and needed only an additive field.
- **Decision**:
  - Added `MacroLinkMode` enum (`FULL`, `FIRST_HALF`, `SECOND_HALF`, `TRIANGLE`, `BIPOLAR`) and `var linkMode: MacroLinkMode = MacroLinkMode.FULL` on `MacroBinding` (`MacroModels.kt`). Default `FULL` is bit-identical to pre-existing behavior, so all existing `.lsd`/`.lsdplay`/`.knobpreset.json` presets load unchanged with no DTO/migration work needed — `MacroBinding` is a flat `@Serializable data class` and `linkMode` is just another defaulted field.
  - Added `MacroCurve.window(macroVal, linkMode): Float` — pure `Float -> Float`, zero-allocation, matching the same primitive-arithmetic style as `MacroCurve.shape()`.
  - `MacroCurve.mapToRange()` now applies `window()` **before** `shape()`: the knob-travel zone is carved out first, then curve/invert/range-mapping apply within that zone — same evaluation order as before with one step prepended, not a restructuring.
  - Added a `Link` combo to `MacroBindingInspector.kt`'s per-binding row, following the exact same `ImInt`/`ImGui.combo` pattern already used for `Curve`.
- **Rationale**: This is the natural extension of a system that already supports multiple bindings per knob — the missing piece was giving each binding its own zone of the turn, not adding a new binding mechanism. Keeping `window()` as a separate pure function ahead of `shape()` (rather than folding zone logic into `shape()` itself) keeps each concern testable and swappable independently, and preserves `shape()`'s existing contract (curve types take a `[0,1]` input) unchanged for every other caller.

---

## Performance Matrix: Row Reshuffle — Master/Transitions Split, Deck BG on Live Console, Deck PV on Master & FX, Master FX Row Dropped (`PerformanceMatrixPanel.kt`, `DECISIONS.md`, `RELEASE_NOTES.md`, `docs/user_guide/macros_and_rack.md`)

- **Context**: 2026-09-22. `LIVE CONSOLE`'s 4th row combined Master and Transitions into one "Master / Transitions" box (crossfader, crossfader time, transition picker, and queue nav all together), while `MASTER & FX`'s own `TRANSITIONS` row was just 4 plain unbound knobs with none of that interactivity — the same bank id (`masterTransition`), two very different presentations. Meanwhile `MASTER & FX` carried a dedicated `MASTER FX` row driving `masterFxBank`, even though `LIVE CONSOLE`'s FX row can already focus that identical bank via its `[MFX]` selector — a straight duplicate row, not a different signal path (unlike `FX_BANK_1`/`FX_BANK_2`, which sit pre-crossfade per deck and are genuinely distinct).
- **Decision**:
  - **Split the combined header into two independent header-drawing functions**: `drawMasterTransitionsHeaderControls` became `drawMasterHeaderControls` (Deck A/B snap badges, crossfader, Auto-fade, Fade Speed/crossfader-time) and `drawTransitionsHeaderControls` (transition picker, queue prev/status/next), each recomputing its own layout now that it isn't sharing horizontal space with the other's widgets.
  - **`MASTER & FX` reordered to Master, Transitions, FX Sends, Deck PV**: Master and Transitions are now both `hasExtraHeader = true` rows (`isSpecialHeaderRow` extended to `isTransRow || isMasterRow`), each with its own interactive header instead of Transitions being 4 dead knobs. The `MASTER FX` row was dropped entirely — not replaced, since its content is already reachable via `LIVE CONSOLE`'s FX row `[MFX]` toggle, same live `masterFxBank` object.
  - **`LIVE CONSOLE` reordered to Deck A, Deck B, Deck BG, FX**: gained a `DECK_BG` row (reusing the same deck-row drawing functions `LIVE QUAD` already uses) in the slot the old combined Master/Transitions row vacated.
  - **`MASTER & FX` gained a full `DECK_PV` row**: same full deck-row controls (generator badge, preset combo, eject, randomize, queue/preview badge, FX send toggles) `LIVE QUAD` already shows for Deck PV — this is an intentional step toward making `LIVE QUAD` fully redundant (not removed this pass; still shows all 4 decks unchanged).
  - Confirmed via code read that only the active tab's `TAB_ROWS[tabIdx]` renders per frame (no cross-tab loop), so the same deck's row appearing on two tabs (Deck A/B on both `LIVE QUAD` and `LIVE CONSOLE` already did this; Deck BG and Deck PV now do too) never risks an ImGui widget-ID collision — every ID in the deck/FX row drawing functions keys off deck or bank identity, not tab or row index.
- **Rationale**: Putting the crossfader/crossfader-time next to the other Master-level composite controls (alphas, master level) and the transition picker/queue next to nothing-but-transitions content makes each row's chevron/Deep Edit genuinely about one concern, instead of one combined row doing two jobs while its sibling did none. Dropping `MASTER FX` removes a maintenance-cost duplicate without losing any capability. `LIVE QUAD` is left untouched this pass since decks now live on 2-3 tabs each — flagged as a strong "delete this tab" candidate for the next pass rather than done here.

---

## Performance Matrix: Binding Inspector Moved to the Macros Tab; Learn Now Auto-Opens It (`PerformanceMatrixPanel.kt`, `ParametersState.kt`, `RackUnit.kt`, `RackDisclosureTest.kt`, `DECISIONS.md`, `RELEASE_NOTES.md`, `docs/user_guide/macros_and_rack.md`)

- **Context**: 2026-09-22. The Performance Matrix's Modular Rack had grown to duplicate the Parameters panel, Properties panel, *and* the Macros tab's Target Bindings Inspector, all stacked vertically per row: knobs, then the Bay tier's full binding inspector (rename, target list, Min/Max/Curve/Invert/Enabled), then (one more click) the Deep Edit tier's Parameters/Properties grid. In practice the bindings inspector ended up *below* Deep Edit's parameter editor, so arming a binding meant scrolling past the parameter grid to reach it — one panel of duplication too many.
- **Decision**:
  - **Dropped the Bay disclosure tier**: `ParametersState.DisclosureLevel` is now `{ COLLAPSED, DEEP_EDIT }` instead of `{ COLLAPSED, BAY, DEEP_EDIT }`; the row chevron (`RackUnit.nextLevel`) toggles Faceplate ↔ Deep Edit directly.
  - **Removed the binding inspector from Performance Mode entirely**: `drawRackBayModule` no longer calls `MacroBindingInspector.draw` — that full inspector (rename, target list, curves) now lives only in Classic mode's Column 3 `[ MACROS ]` tab (`MacroPanel.kt`), which the user already reaches for it essentially all the time.
  - **Inline Learn auto-navigates there**: the selected knob's inline `[Learn]` button (still in the Tier-1 grid, unchanged) now also sets `MacroLearnState.selectedControlId`, calls the new `navigateMacroPanelTo` to point `ParametersState.activeTopTab`/`activeMixerSubTab` at the armed knob's own bank, and flips `UITheme.column3Mode` to `MACROS` — so arming Learn from Performance Mode pops the Binding Inspector open already on the right knob, with no extra clicks.
- **Rationale**: The full inspector was used almost exclusively in its Classic-mode location; keeping a second copy in Performance Mode cost a disclosure tier and a scroll-past-Deep-Edit tax for no real benefit. Auto-opening the Macros tab on Learn preserves the "arm and bind without hunting through sub-panels" workflow the Bay tier was trying to provide, without duplicating the editor itself.

---

## Performance Matrix Right-Aligned Row Titles & Elevated Knobs (`PerformanceMatrixPanel.kt`, `DECISIONS.md`, `RELEASE_NOTES.md`, `docs/user_guide/macros_and_rack.md`)

- **Context**: 2026-09-22. In the Performance Matrix, the row group title (e.g. `DECK A`, `FX: FX Bank 1`, `TRANSITIONS`) was rendered centered horizontally across the full row box directly above the central knob cluster. This forced `groupLabelH` (~22px) plus gap spacing to be subtracted from the available vertical space in the center column, leaving knobs, labels, `Val: 0.00` readouts, and inline `[Learn]` buttons cramped vertically.
- **Decision**:
  - **Right-Aligned Row Titles**: Shifted row group titles to the right side of the row box, positioned directly above the right-wing UI controls (`[FX1][FX2]` send toggles on deck rows, `[BYPASS][Resync]` on the FX row) and immediately to the left of the faceplate `[Collapse]` and disclosure chevron buttons.
  - **Elevated Knob Starting Position**: Removed the `groupLabelH` vertical offset from the central knob cluster. The knobs and left-wing controls now start directly at `boxTopY + boxPad`.
  - **Expanded Vertical Headroom**: Knob diameter and available vertical space calculations now utilize the full height of the row box. The knob circle, label, `Val: 0.00` readout, and inline `[Learn]` button sit with ample vertical breathing room without cramped overlaps.
- **Rationale**: The central column is dedicated to the primary tactile controls (the 4 rotary knobs). Moving text titles over to the right above secondary buttons frees vertical space where it matters most, improving readability and touch/mouse target ergonomics.

---

## Performance Matrix Bay View Direct Knob Selection & Inline Learn (`PerformanceMatrixPanel.kt`, `MacroKnobWidget.kt`, `MacroBindingInspector.kt`, `DECISIONS.md`, `RELEASE_NOTES.md`, `docs/user_guide/macros_and_rack.md`)

- **Context**: 2026-09-22. In the Modular Rack Bay view (Tier 2), a vertical list of the 4 knobs sat below the actual 4-knob UI grid, forcing the user to read through text items to select a knob, while the inspector below redundantly printed `KNOB [KNOB 2] Val: 0.00` and `[Learn]`. The UI knobs themselves were not interactive targets for selecting or arming parameter learning, and collapsing the bay required scrolling to the bottom of the inspector or using toolbar buttons.
- **Decision**:
  - **Direct Knob Selection on the 4-Knob Grid**: Clicking any rotary knob when its row is expanded selects it (`parametersState.selectedRackMacroId[moduleId] = control.id`). The selected knob is highlighted with an electric cyan focus card (`#1AB0EB` border and subtle fill), glowing rim, and electric cyan text label.
  - **Numerical Value Readout in 4-Knob Grid**: When a row is expanded, `MacroKnobWidget` renders `Val: 0.00` centered directly beneath the knob label.
  - **Inline Parameter-Bind Learn Button**: The selected knob renders a compact `[Learn]` / `[Cancel]` button directly below its `Val: 0.00` readout in the 4-knob UI. Performers can arm parameter binding right from the knob face without searching through sub-panels.
  - **Deduplicated Bay Inspector**: Removed the redundant 4-knob selectable text list, `KNOB` badge, and duplicate value readout from `MacroBindingInspector` via `showKnobHeader = false`, presenting a clean `Target Bindings: [rename]` header and list of active parameter bindings.
  - **Faceplate Collapse Button**: Added a direct `[Collapse]` button next to the chevron in the row header so performers can fold the bay back up directly from the row.
- **Rationale**: Eliminates redundant widgets, streamlines live performance workflow by allowing direct interaction with the visual rotary controls, and keeps the inspector focused on multi-target parameter bounds and response curves.

---

## Performance Matrix Vertical Space Optimization & Side-Wing Layout (`PerformanceMatrixPanel.kt`, `DECISIONS.md`, `RELEASE_NOTES.md`, `docs/user_guide/macros_and_rack.md`)

- **Context**: 2026-09-22. In Performance Mode (`PerformanceMatrixPanel.kt`), each deck row and the FX row previously rendered a separate top header bar (`EXTRA_HEADER_H = 28f` plus padding) above the knobs, eating ~31px of vertical height per row. Meanwhile, the 4 rotary macro knobs spanned across the entire width of the matrix panel, leaving vast empty horizontal gaps between knobs while cramping vertical headroom.
- **Decision**:
  - **Eliminated Separate Top Bars**: Removed the top header bars from Deck rows (Deck A, Deck B, Deck BG, Deck PV) and the FX row, restoring ~31px of vertical height per row directly to the knob area.
  - **Side-Wing Organization**:
    - **Deck Rows**:
      - **Left Wing**: Generator badge (`deck.source.displayName`), searchable preset dropdown combo with quick-search and dirty marker, eject button (`Icons.EJECT`), randomize die button (`Icons.DICES`), and queue navigation (`< N/Total >` for A/B/BG, or `PREVIEW` focus button for PV).
      - **Right Wing**: Send routing toggles `[FX1]` and `[FX2]` with MIDI/OSC Learn.
    - **FX Row**:
      - **Left Wing**: Bank switcher (`[FX1]`, `[FX2]`, `[MFX]`) and chain switcher (`[C1]`, `[C2]`, `[C3]`).
      - **Right Wing**: Bank bypass button (`[BYPASS]`/`[FX ON]`) and explicit `[Resync]` button.
  - **Clustered Knob Spacing**: The 4 macro knobs are positioned closer together as a tactile center cluster (`targetColW = (diameter + 24f).coerceIn(72f, 96f)`), centered between the left and right control wings. Across all rows on each tab, the knobs share identical column coordinates so they align in 4 uniform vertical columns.
  - **Compact Accordion Height**: Reduced `compactRowH` from 210f to 175f to give expanded modular rack bays significantly more vertical room.
- **Rationale**: Reclaiming vertical space allows larger knob diameters without squishing labels, while grouping related deck and FX controls into flanking wings matches live DJ/VJ hardware ergonomics where knobs are centrally grouped and secondary switches/selectors flank the rotary cluster.

---

## Modular Rack UX Revision: Hide Collapsed Rows, Column Headers, Side-by-Side Deep Edit (`PerformanceMatrixPanel.kt`, `ParametersPanel.kt`, `DECISIONS.md`, `RELEASE_NOTES.md`, `docs/user_guide/macros_and_rack.md`)

- **Context**: 2026-09-22. Live user testing of the Unified Modular Rack (previous entry below) surfaced three usability gaps: (1) the original plan's "fixed-height faceplate band" requirement — every collapsed row stays visible and full-size no matter what's expanded — ate most of the screen once a row expanded, leaving little room for Bay/Deep Edit content; (2) Deep Edit's parameter grid had no VAL/MIDI/LFO/SEQ/AUD column headers, since `drawRackDeepEdit` called `ParametersTabs.drawSectionTabs` + the row-drawing functions directly but never `ParametersPanel`'s (private) column-header drawer; (3) Deep Edit stacked the parameter grid above the Properties detail editor vertically, unlike Classic mode's side-by-side Columns 1 & 2, wasting the width freed up by fix (1).
- **Decision**:
  - **Hide collapsed rows while any module is expanded**: reversed the original plan's fixed-height-band requirement. `PerformanceMatrixPanel.visibleRowsForTab` now filters the current tab's rows down to only those whose module is above Tier 1 whenever any module is expanded (falls back to showing every row if none of the expanded modules have a row on the current tab, so the grid is never left empty). `drawMatrix` and the grid/Bay height split in `draw()` both consume this filtered list, so the grid shrinks to fit just the visible row(s) — via a fixed `compactRowH` per visible row rather than the previous `availH * 0.42f` bay-height ratio — and the freed space goes to the Bay/Deep-Edit region below.
  - **Column headers in Deep Edit**: changed `ParametersPanel.drawColumnHeaders` from `private` to `internal` (no other change) so `drawRackDeepEdit` can call it verbatim instead of duplicating a header row. It also already draws the SRC/View/CTRL/TRANS Section Tabs above the headers, so `drawRackDeepEdit`'s separate `ParametersTabs.drawSectionTabs` call was removed as redundant.
  - **Side-by-side Deep Edit layout**: `drawRackDeepEdit` now opens two child regions (`##rack_deep_params_<id>` ~58% width, `##rack_deep_props_<id>` ~42%) side by side via `ImGui.sameLine`, mirroring Classic mode's Parameters/Properties columns, instead of stacking the parameter grid above `PropertiesPanel.draw`.
- **Rationale**: the original "never shrink a collapsed faceplate" requirement was a reasonable a-priori design goal (protect against losing sight of a knob), but in practice a VJ who has deliberately opened one row's Deep Edit wants that row's editing surface to dominate the screen, not the other 3-15 still-collapsed knobs. Reusing `ParametersPanel.drawColumnHeaders` verbatim (rather than reimplementing column labels) keeps the header row's kebab menu and hover tooltips working identically to Classic mode for free.

---

## Unified Modular Rack: 3-Tier Accordion Disclosure for the Performance Matrix (`PerformanceMatrixPanel.kt`, `rack/RackUnit.kt`, `ParametersState.kt`, `AppPreferences.kt`, `UITheme.kt`, `UIManager.kt`, `MenuBar.kt`, `DECISIONS.md`, `RELEASE_NOTES.md`, `docs/user_guide/macros_and_rack.md`)

- **Context**: 2026-09-22. Performance Mode's 4×4 knob matrix was read-mostly: right-click MIDI Learn on a knob was the only editing action available, so setting up a knob's label, parameter binding, Min/Max/Curve, or a bound parameter's own CV modulators (LFO/MIDI/SEQ/AUD detail) required switching to Classic mode (`F4`) and hunting through Columns 1–3 there. Live performers wanted that editing surface reachable without leaving the 4×4 view.
- **Decision**:
  - Every row group in the matrix (each deck, the FX row, Transitions, Master, FX Sends, Master FX) got a **chevron button** that cycles three disclosure tiers, tracked per moduleId in a new `ParametersState.DisclosureLevel` map (`rackModuleDisclosure`): **Faceplate** (collapsed, today's matrix, unchanged) → **Bay** → **Deep Edit** → back to Faceplate.
  - **Bay** opens a scrollable region below the (always fixed-height) grid showing that row's 4 knobs as a list plus the exact same `MacroBindingInspector` Classic mode's Column 3 MACROS tab uses (rename, Learn, Min/Max/Curve/Invert/Enabled) — called verbatim, not reimplemented.
  - **Deep Edit** additionally reuses `ParametersTabs.drawSectionTabs`/`drawDeckGroupContent` (decks), `drawFxBankGroupContent` (the FX row and Master FX), or `drawMixerGroupContent`'s CTRL/TRANS subtabs (Transitions and Master, which both live under the Parameters panel's "Mixer" top tab) for the row grid, and `PropertiesPanel.draw` verbatim for per-parameter CV detail — identical reachable bindings to Classic mode. `ParametersState.selectedCell`/`selectedParam`/`activeTopTab` are shared globals Classic mode also uses, so Deep Edit saves them, temporarily points them at that module's own `rackSelectedCell` entry, and restores them afterward — this is what lets two Deep Edits stay open independently in Multi mode without fighting over one shared selection, and keeps Classic mode's own selection undisturbed. FX Sends doesn't have a Deep Edit (its 4 per-deck send levels are already reachable via each deck's own Deep Edit instead) — it shows Bay content with a note to use Classic mode.
  - **Disclosure persistence**: `ParametersState.rackModuleDisclosure` seeds itself from `UITheme.rackExpandedModules` on construction (safe because `SessionContext` touches `uiTheme` — triggering `AppPreferencesStore.loadPreferences()` in `UITheme`'s `init` block — before constructing `parametersState`) and writes back (BAY/DEEP_EDIT entries only) on every `setDisclosure`/`collapseAllRackModules` call, so which rows are expanded survives an app restart; `rackSoloMode` was already persisted this way.
  - **Solo vs. Multi**: `rackSoloMode` (persisted, default on) auto-collapses every other module when one expands; a `[ SOLO | MULTI ]` toolbar toggle and `Collapse All` button (also in the View menu) control it.
  - **Learn-mode pinning**: `ParametersState.isLearnPinned(moduleId)` exempts a module from auto-collapse while one of its own knobs has an armed `MacroLearnState` Learn, and a persistent "Learning: ‹name› — Esc to cancel" indicator stays in the toolbar regardless of which module's Bay is open.
  - **Esc priority stack** (global handler in `UIManager.processQueueKeyboardShortcuts`, guarded by `!ImGui.getIO().wantTextInput`): cancel an armed Learn first, else collapse every expanded module, else no-op.
  - **Focus-swap decoupling**: the FX row's moduleId is the stable string `"FX"`, decoupled from `focusedFxBankId` (FX1/FX2/MFX) — expand/collapse never calls the bank-focus-switch path or re-runs `FxMacroSync`; only the row's own `[FX1][FX2][MFX]` buttons do that. `ParametersState.setDisclosure`/`collapseAllRackModules` take no `Mixer`/`FxBank` parameter at all, which is the compile-time enforcement of this rule.
  - Layout: the Tier-1 grid keeps a fixed-height child region regardless of any row's disclosure state (via `ImGui.beginChild` sized before any row is drawn), so collapsed rows are never pushed into a scrollbar by a sibling's expansion — only the Bay/Deep-Edit region below the grid scrolls.
- **Rationale**:
  - Reusing `MacroBindingInspector`/`ParametersTabs`/`PropertiesPanel` verbatim (rather than a parallel Performance-Mode-only editor) means Deep Edit can never drift out of sync with Classic mode's own editing surface, and any future change to those shared drawers benefits both views for free.
  - Solo-mode-by-default plus the fixed-height Tier-1 band keeps the matrix's "glance and see all 16 knobs" property intact even while a row is expanded — the whole point of a live-performance surface.
  - Learn-mode pinning + the persistent indicator prevents a specific failure mode: `MacroLearnState` is a global singleton independent of widget visibility, so a flat "Esc always collapses" would let a user reflexively dismiss the accordion while a Learn arm silently kept running underneath.
- **Deviation from the original plan's file layout**: the plan called for `DeckRackUnit.kt`/`FxRackUnit.kt`/`MasterRackUnit.kt` under a new `ui/rack/` package. The accordion logic instead lives directly in `PerformanceMatrixPanel.kt` (plus the small stateless `rack/RackUnit.kt` chevron/indicator helper), so all of the existing header/knob-drawing/drag-drop code could be reused with zero duplication. Extracting into the planned per-module files remains straightforward later once there's enough module-specific logic to justify the split.

---

## Performance Matrix Deck Controls Parity, Randomize Dice (A, B, BG, PV, ALL), and Fade Speed Control (`PerformanceMatrixPanel.kt`, `DECISIONS.md`, `RELEASE_NOTES.md`)

- **Context**: 2026-09-22. In the Performance Matrix (`PerformanceMatrixPanel.kt`), Decks BG and PV lacked the header control parity found on Decks A and B (preset selector combo, eject button, queue navigation, FX routing). Furthermore, one-click randomize buttons were missing for individual decks and all decks combined in the matrix view, and auto-crossfade duration (`mixer.xfadeSpeed`) lacked direct scrubbing/learning controls on the Transitions row.
- **Decision**:
  - **Randomization Dice for A, B, BG, PV, and ALL**:
    - Added `[ ALL ${Icons.DICES} ]` button at the right edge of the performance matrix tab strip, pushing undo state and invoking `mixer.randomizeAll()`.
    - Added dedicated dice buttons (`${Icons.DICES}`) in each deck row header (A, B, BG, PV) that snapshot undo state and trigger `mixer.randomizeDeckA()`, `mixer.randomizeDeckB()`, `mixer.randomizeDeckBG()`, and `mixer.randomizeDeckPV()`.
  - **Deck BG & PV Controls Parity with A and B**:
    - Enabled `hasExtraHeader = true` for all four decks (`DECK_A`, `DECK_B`, `DECK_BG`, `DECK_PV`) in the `LIVE QUAD` matrix tab.
    - Generalized `drawDeckRowHeaderControls()` to support all four decks with generator badges, searchable preset combo dropdowns (`presetSearchBG`, `presetSearchPV`), eject buttons (`${Icons.EJECT}`) routing through `UIManager.triggerDeckEject(deck)`, and FX routing toggles (`[FX1][FX2]`) with MIDI/OSC learn.
    - Wired `BgQueueManager` navigation (`< N/Total >`) for Deck BG and a preview deck focus button for Deck PV.
  - **Crossfader Fade Speed Control**:
    - Added an interactive duration badge widget (`${mixer.xfadeSpeed.baseValue}s`) directly between `[ AUTO ]` and the Transition Picker button on the Master / Transitions header.
    - Supports drag-scrubbing, mouse wheel adjustment, and right-click context menu offering quick duration presets (`0.5s`, `1.0s`, `2.0s`, `4.0s`, `8.0s`), MIDI Learn, OSC Learn, and mapping removal.
- **Rationale**:
  - Gives live performers complete parity and quick-action control across all visual decks (A, B, BG, and PV) without having to switch tabs or open separate panels.
  - Enables rapid, non-destructive experimentation via one-click dice randomization with full undo support.
  - Allows seamless real-time tweaking of auto-fade duration directly beside the crossfader and auto-fade toggle.

---

## Mixer Shader & Engine Consolidation: Bloom, Master Alpha, and Legacy Mix Mode Removal (`Mixer.kt`, `mixer.frag`, `Renderer.kt`, `DECISIONS.md`)

- **Context**: 2026-09-22. As part of streamlining the Mixer pipeline and macro knob architecture:
  1. The legacy 9-tap blur pass in `mixer.frag` (`uBloom`) was obsolete and redundant with the dedicated ISF filter `"bloom"` and transition shaders like `luminous_flash.fs`.
  2. Master opacity was split between two redundant variables: `mixer.masterAlpha` (`uAlpha`) and `mixer.masterLevel` (`uMasterLevel`), multiplying each other (`finalAlpha = uAlpha * uMasterLevel`).
  3. Legacy hard-coded blend modes (`Mixer.mode` / `uMode`) had already been superseded by pure ISF transition shaders and were hardcoded to `-1` in `Renderer.kt`.
- **Decision**:
  - **Removed Bloom from Mixer**: Stripped `bloom` parameter from `Mixer.kt`, `mixer.frag`, `web/shaders/mixer.frag`, `ClipboardManager.kt`, and `Renderer.kt`. ISF filters in master FX or deck FX now exclusively handle bloom and glow effects.
  - **Consolidated Master Opacity into `masterLevel`**: Removed `masterAlpha` parameter and `uAlpha` uniform from `mixer.frag`. All master level gain and fading is now governed strictly by `mixer.masterLevel` (`uMasterLevel`). Added migration fallback in `SessionSerializer.kt` so legacy sessions load `masterAlpha` into `masterLevel`.
  - **Removed Legacy Mix Mode**: Deleted `Mixer.mode`, the `uMode`, `uBalance`, and `uTex2` uniforms, and all legacy mix mode switch cases in `mixer.frag`. Transition compositing is now 100% driven by the ISF transition pipeline (`uTex0`, `uTex1`, `uTransitionTex`).
  - **UI Cleanups**: Updated `ParametersTabs.kt`, `ParametersRenderer.kt`, and `ValueParamSection.kt` to remove legacy mix mode and bloom rows/captions, cleanly exposing `Master Level` instead.
- **Rationale**:
  - Eliminates redundant shader math, duplicate texture lookups, and unneeded render passes in Pass 2 compositing.
  - Simplifies the Mixer parameter surface, leaving single authoritative controls for crossfading, deck levels, and master level.
  - Preserves backward compatibility when deserializing older presets or session files containing legacy `masterAlpha` or `blendMode` properties.

---

## Audio Hardware Signal & CV Oscilloscope 5×2 Grid and VU Meter Consolidation (`AudioEnginePanel.kt`, `docs/developer/ui.md`)

- **Context**: 2026-09-21. In Audio Hardware preferences (`AudioEnginePanel.kt`), redundant stereo VU meters were drawn in both the left hardware controls column and right signal monitor column. Additionally, the Raw Audio Buffer oscilloscope was positioned separately at height 65 px above the CV oscilloscopes, leaving a visual gap next to Beat Sine Oscillator.
- **Decision**:
  - **Deduplicated Stereo VU Meter**: Removed the duplicate `drawStereoVuMeter()` call from the left column, consolidating all visual input metering cleanly at the top of the right column above the oscilloscopes.
  - **Unified 5×2 Oscilloscope Grid (`##cv_oscilloscopes_grid`)**: Standardized all 10 oscilloscopes to uniform 50 px height and arranged them in a balanced 2-column grid:
    - Row 0: Raw Buffer (bipolar audio waveform) on the left directly beside Beat Sine (Oscillator) on the right.
    - Rows 1–4: Sustained RMS power bands on the left paired with onset Flux transient bands on the right:
      - Row 1: Full Mix (RMS) beside Full Mix Transient (Flux)
      - Row 2: Bass Band (RMS) beside Kick Transient (Flux)
      - Row 3: Mid Band (RMS) beside Snare Transient (Flux)
      - Row 4: High Band (RMS) beside Hat Transient (Flux)
- **Rationale**:
  - Removes redundant metering widgets from the configuration column while preserving real-time physical stereo input levels in the signal monitoring column.
  - Pairs the raw audio input signal alongside the rhythmic beat sine oscillator on row 0, followed by the 4 frequency bands (Full Mix, Bass/Kick, Mid/Snare, High/Hat) in parallel RMS vs. Flux columns.
  - Standardizes oscilloscope dimensions across all 10 graphs to 50 px for visual harmony and zero vertical layout distortion.

---

## Preferences Tab Ordering Reorganization (`PreferencesPanel.kt`, `UIThemeTest.kt`, `docs/developer/ui.md`)

- **Context**: 2026-09-21. To optimize configuration workflow and place the most frequently adjusted setup options near the top, the Preferences categories needed to be reorganized to prioritize shader locations, video & display, and audio hardware setup ahead of auxiliary controllers and network services.
- **Decision**:
  - Reordered `PreferencesPanel.Category` enum and content dispatcher to:
    1. `GENERAL` ("General")
    2. `SHADER_LOCATIONS` ("Shader Locations")
    3. `VIDEO_DISPLAY` ("Video & Display")
    4. `AUDIO_ENGINE` ("Audio Hardware")
    5. `TEMPO_SYNC` ("Tempo & Sync")
    6. `MIDI_CONTROLLER` ("MIDI Controls")
    7. `OSC_CONTROLLER` ("OSC Controls")
    8. `SHORTCUTS` ("Keyboard Shortcuts")
    9. `BROADCAST` ("Web Broadcast")
  - Updated `UIThemeTest.kt` to validate the full category sequence.
  - Updated developer documentation in `docs/developer/ui.md`.
- **Rationale**:
  - Keeps initial setup tasks (shader locations, display configuration, and audio hardware) upfront, followed by tempo sync, hardware controller mapping (MIDI and OSC), keybindings, and finally remote broadcast output.

---

## Live Console Preset Quick-Search & Header Controls MIDI/OSC Learn (`PerformanceMatrixPanel.kt`, `MidiMappingManager.kt`)

- **Context**: 2026-09-21. Performers utilizing the `LIVE CONSOLE` layout needed rapid preset filtering for large preset libraries directly within the Deck A and Deck B dropdown combos, as well as unified MIDI and OSC hardware learnability for interactive header controls (Crossfader, Snap buttons, Auto-Fade, PlayQueue, Transition Queue, and FX routing toggles) without needing to configure them in separate preference tabs.
- **Decision**:
  - **Preset Quick-Search**:
    - Integrated an auto-focused search text input (`ImGui.inputTextWithHint`) at the top of Deck A and Deck B preset combo popups.
    - Implemented real-time case-insensitive string filtering over `FileSystemManager.scanAllPresets()`, with `Escape` shortcut to clear the search buffer and automatic state cleanup on combo close.
  - **Unified MIDI & OSC Context Menus**:
    - Implemented right-click context menus (`beginPopupContextItem`) on all `LIVE CONSOLE` header controls:
      - Crossfader slider track: Learn MIDI (`Mixer/crossfade`), Learn OSC (`Mixer/crossfade`), Reset to Center, Snap A/B, Clear Mappings.
      - Deck A & B snap badges (`[ A ]`, `[ B ]`): Learn MIDI (`Global/snapDeckA`, `Global/snapDeckB`), Clear Mappings.
      - Auto-Fade button (`[ AUTO ]`): Learn MIDI (`Global/autoFade`), Clear Mappings.
      - PlayQueue `<` and `>`: Learn MIDI (`Global/queuePrev`, `Global/queueNext`), Learn OSC (`Mixer/queuePrev`, `Mixer/queueNext`), Clear Mappings.
      - Transition Queue `<` and `>`: Learn MIDI (`Global/transQueuePrev`, `Global/transQueueNext`), Learn OSC (`Mixer/transQueuePrev`, `Mixer/transQueueNext`), Clear Mappings.
      - FX Routing toggles (`[FX1]`, `[FX2]`): Learn MIDI & OSC for `$deckLabel/View/FxRouting`, Clear Mappings.
  - **New Global Actions**:
    - Added high-edge detection for `Global/autoFade`, `Global/snapDeckA`, and `Global/snapDeckB` in `MidiMappingManager.processGlobalMidiEvents()`.
    - Exposed them in `MidiPreferencesPanel` for centralized inspection and manual mapping.
  - **Visual Feedback**:
    - Added a cyan border highlight around header controls when armed for MIDI learn, and added active MIDI channel/CC indicators to control tooltips.
- **Rationale**:
  - Provides instantaneous hardware binding and keyboard-friendly search directly inside the live show workflow, keeping performers focused on the Performance Matrix without context switching.
  - Adheres strictly to Zero-Allocation callback rules and maintains consistency with the app's established MIDI/OSC learn architecture.

---

## Live Console Master & Transitions Performance Header Controls (`PerformanceMatrixPanel.kt`)

- **Context**: 2026-09-21. In the `LIVE CONSOLE` tab of Performance Mode, Decks A, B, and FX rows were equipped with quick-access headers (preset loading, generator badges, play queue, FX routing, bank/chain switching). Row 4 (`MASTER / TRANSITIONS`) contained only 4 macro knobs, leaving performers without a way to crossfade, snap between decks, inspect/pick transition shaders, or step the transition queue without switching to the Classic Mixer.
- **Decision**:
  - Set `hasExtraHeader = true` on Row 4 of `LIVE CONSOLE`.
  - Embedded an interactive zero-centered horizontal crossfader slider (`-1.0` Deck A to `+1.0` Deck B) with mouse drag, mouse wheel fine-adjust, middle-click center reset, and live amber modulation/auto-fade indicator dot.
  - Added Deck A `[ A ]` and Deck B `[ B ]` instant snap badges that immediately disarm Auto-VJ, halt auto-fading, and snap the crossfader to -1.0 or +1.0.
  - Added an `[ AUTO ]` / `[ FADING ]` button that fades to the opposite deck over the configured `mixer.xfadeSpeed` duration.
  - Added a Transition Picker button showing the active transition name and modified indicator (`*`), launching `ShaderPickerPopup`.
  - Added Transition Queue navigation (`<`, `N/Total`, `>`) controlling `TransitionQueueManager`.
  - Added drag-and-drop support on the row title and crossfader track for `.lsdtrans` presets and `.fs`/`.isf` transition shaders.
- **Rationale**:
  - Makes `LIVE CONSOLE` a completely self-contained performance surface, allowing a performer to execute a full DJ/VJ set (deck selection, preset queuing, FX tweaking, crossfading, and transitions) entirely from the 4×4 Performance Matrix.
  - Preserves zero-allocation real-time safety and integrates seamlessly with existing `Mixer`, `TransitionQueueManager`, and `ShaderPickerPopup` models.

---

## Performance Matrix Tab Streamlining: 4-Deck LIVE QUAD & Pruning Redundant Tabs (`PerformanceMatrixPanel.kt`)

- **Context**: 2026-09-21. The Performance Mode 4×4 Matrix previously had five layout tabs: `LIVE QUAD` (A, B, BG, Transitions), `DUAL DECKS` (A 1-4, B 1-4), `PREP & BG` (PV 1-4, BG 1-4), `MASTER & FX` (Transitions 1-8, Master 1-8), and `LIVE CONSOLE` (A, B, FX, Master/Transitions). With the recent retirement of Knobs 5-8 on decks and the addition of `LIVE CONSOLE`, `DUAL DECKS` and `PREP & BG` became redundant sub-slices of the quad view. Meanwhile, `LIVE QUAD` awkwardly contained Transitions on Row 4 instead of the 4th deck (Deck PV).
- **Decision**:
  - **4-Deck `LIVE QUAD`**: Replaced Transitions on Row 4 of `LIVE QUAD` with Deck PV (`MacroEngine.DECK_PV`). `LIVE QUAD` now maps cleanly to all four visual generation decks (Deck A, Deck B, Deck BG, Deck PV) with 4 macro knobs each.
  - **Pruned `DUAL DECKS` and `PREP & BG`**: Removed both 2-deck tabs from `Tab` and `TAB_ROWS`.
  - **Streamlined 3-Tab Structure**: Performance Mode now has three focused tabs:
    1. `LIVE QUAD` (16 generation knobs across Decks A, B, BG, PV).
    2. `MASTER & FX` (16 knobs: 8 for Transitions, 8 for Master).
    3. `LIVE CONSOLE` (16 knobs: Deck A, Deck B, focused FX bank/chain with in-place link buttons, Master/Transitions).
- **Rationale**:
  - Eliminates tab clutter and visual redundancy.
  - Makes `LIVE QUAD` an intuitive, complete representation of all 4 visual decks.
  - Provides three distinct, complementary performance surfaces for live show workflows.

---

## FX Macro Knob Controls Unification & Hit-Target Isolation (`PerformanceMatrixPanel.kt`, `MacroPanel.kt`, `MacroEngine.kt`)

- **Context**: 2026-09-21. With the migration to the Two-Tier FX Macro system (Chain Super Knob + Effect Metaknobs), two interaction issues emerged across views:
  1. In `PerformanceMatrixPanel.kt`, dragging a `.lsdfxchain` file into the FX group box was handled via an `invisibleButton("##perf_fx_drop_target")` covering the entire box height. In Dear ImGui, this consumed mouse-down events across the entire group, preventing clicks from reaching the header buttons (`[FX1][FX2][MFX]`, `[C1][C2][C3]`, `[BYPASS]`, `[Resync]`) and disabling left-click-drag on `MacroKnobWidget` (while scroll wheel still responded due to global hover polling).
  2. In `MacroPanel.kt`, the Column 3 `MACROS` view for FX tabs (`FX1`, `FX2`, `MFX`) rendered `FXChainMacroStrip.draw()`, which displayed compact sliders (`CustomRangeSlider.drawCompactSlider()`). Because parameters were macro-bound, the slider's `!isMacroBound` guard suppressed mouse interaction, locking them from both drag and scroll. Furthermore, users preferred rotary knobs consistent with the rest of the Macro Panel.
- **Decision**:
  - **Hit-Target Isolation (`PerformanceMatrixPanel.kt`)**: Constrain `##perf_fx_drop_target` strictly to the title header area (`afterTitleY - boxTopY`). The title bar remains an active drag-and-drop receiver for `.lsdfxchain` assets, while the header buttons and 4 rotary knobs below remain fully responsive and unoccluded. Also pass `control.bindings` to `MacroKnobWidget.draw()` to properly expose binding metadata and tooltips.
  - **Clickable Chain Link / Unlink Icons (`PerformanceMatrixPanel.kt`, `Icons.kt`)**: Added standard Lucide `LINK` (`\ue102`) and `UNLINK` (`\ue19c`) icon buttons to the left of each FX slot knob (Slots 1–3) in Performance Mode. Clicking the icon immediately toggles the slot's link status (`setSlotLinked`), resyncing the chain without leaving Performance Mode.
  - **Effect Name Retention for Linked Slots (`FxMacroSync.kt`)**: Ceased overriding macro control labels with `(linked)`. Instead, knobs consistently show the active effect's name (or `FX1`–`FX3` fallback), relying on the link icon to display and toggle link status.
  - **Rotary Knobs in Macro Panel FX Tabs (`MacroPanel.kt`)**: Replaced the slider strip in `drawFxRackView` with standard rotary knobs via `drawMacroGrid()`. Display the `[ Chain 1 ] [ Chain 2 ] [ Chain 3 ]` switcher and compact `[x] Slot 1 [x] Slot 2 [x] Slot 3` Super Knob Link checkboxes directly above the knobs. Knob IDs are prefixed with `activeBankId()` to prevent ID collisions.
  - **Zero-Allocation Visual Sync in `MacroEngine.tick()`**: In `MacroEngine.tick()`, synchronize linked FX slot macro knob values (`knob.value`) to mirror `slot.metaKnob.baseValue` / `chain.superKnob.baseValue` in memory without heap allocations, providing smooth visual rotary tracking when turning the Super Knob.
- **Rationale**:
  - Eliminates button and knob click-blocking in the Performance Console.
  - Provides instant, in-place link toggling directly on the performance surface without context switching.
  - Preserves effect identity on knob labels while clearly communicating link state through standard iconography.
  - Provides uniform rotary knob ergonomics, drag physics, scroll wheel fine-adjust, and MIDI learn across all tabs in Column 3 MACROS.
  - Keeps linked FX controls visually coherent across both Classic and Performance views.

---

## Retirement of Deck Macro Knobs 5-8 & Strict Clamping on Session Restore (`SessionSerializer.kt`, `SessionStateTest.kt`, `MacroEngine.kt`)

- **Context**: 2026-09-21. Following the migration to the Two-Tier FX Architecture (`FxBank` + `FxChain` + `FxMacroSync`), effects processing moved from deck-level slots into shared multi-chain insert banks (`FX1`, `FX2`, `MFX`) with their own 4-control performance surface (1 Super Knob + 3 Slot Metaknobs). Previously, decks owned 8 macro knobs (Knobs 1–4 for generation, Knobs 5–8 for deck FX). With FX decoupled from decks, per-deck macro banks were redesigned to hold strictly 4 generation-only knobs (`MacroEngine.defaultKnobCountFor`). However, `SessionSerializer.loadSession()` previously restored `session.deckMacroBanks` directly without clamping, allowing legacy session files saved prior to the migration to resurrect obsolete Knobs 5–8 in Classic Mode's Column 3 MACROS panel.
- **Decision**:
  - **Enforce Clamping on Session Restore**: In `SessionSerializer.loadSession()`, canonical macro banks are strictly clamped to `MacroEngine.defaultKnobCountFor(canonicalId)`. If a saved bank contains fewer than the expected count, it is padded with generic blank controls.
  - **Cleaned Workspace State**: Sanitized `library/last_session.json` to eliminate legacy 8-knob records and residual hardcoded bindings on generator banks.
- **Rationale**:
  - Eliminates dead-weight, confusing orphan controls on decks that no longer map to any deck-level FX pipeline.
  - Guarantees visual consistency between fresh installations, preset loads, and restored previous sessions across both Classic and Performance modes.

---

## 4-Knob Canonical Bank Standardization, Master Composite Alpha Defaults, and MST Macro Tab (`MacroEngine.kt`, `MacroPanel.kt`, `PerformanceMatrixPanel.kt`)

- **Context**: 2026-09-22. While generator and FX banks were previously standardized to 4 knobs, Transitions (`TRANS`) and Master (`MASTER`) retained 8 knobs. In Column 3's `MacroPanel`, Transitions rendered as a tall 4×2 grid that crowded out the Binding Inspector and Preview Monitor, and Master lacked a dedicated macro tab. In `PerformanceMatrixPanel`, the `MASTER & FX` tab rendered duplicate two-row blocks (`1-4` and `5-8`) for Transitions and Master, leaving FX sends and Master FX without dedicated matrix space.
- **Decision**:
  - **Standardized All Canonical Banks to 4 Knobs**: `MacroEngine.defaultKnobCountFor` now returns 4 for all canonical banks (`DECK_A`, `DECK_B`, `DECK_BG`, `DECK_PV`, `TRANS`, `MASTER`, `FX_BANK_1`, `FX_BANK_2`, `FX_SENDS`, `MASTER_FX`). Legacy sessions with 8-knob Transition/Master banks are automatically clamped to 4 upon restore.
  - **Master Composite Alpha Smart Defaults**: `MacroEngine.newBankFor(MASTER)` pre-populates 4 primary composite balance controls:
    1. Knob 1: `ALPHA A` -> `Mixer/levelA` (1.0f)
    2. Knob 2: `ALPHA B` -> `Mixer/levelB` (1.0f)
    3. Knob 3: `ALPHA BG` -> `Mixer/levelBG` (0.0f)
    4. Knob 4: `MASTER` -> `Mixer/masterLevel` (1.0f)
  - **FX Sends Smart Defaults**: `MacroEngine.newBankFor(FX_SENDS)` pre-populates 4 send level controls (`SEND A`, `SEND B`, `SEND BG`, `SEND PV` targeting `$deck/FXChain/DryWet`).
  - **Dedicated `MST` Tab in Column 3 Macro Panel**: Added `MST` to `MacroPanel`'s tab strip (`[ A | B | BG | PV | TRANS | MST | FX1 | FX2 | MFX ]`). Clicking `TRANS` navigates to `Mixer` tab with `TRANS` sub-tab; clicking `MST` navigates to `Mixer` tab with `CTRL` sub-tab. The preview monitor displays `PREVIEW: MASTER` for MST and `PREVIEW: TRANSITION` for TRANS.
  - **Reconfigured Performance Matrix `MASTER & FX` Tab**: Restructured into 4 distinct single-row (4-knob) group boxes:
    1. Row 1: `TRANSITIONS` (4 knobs)
    2. Row 2: `MASTER` (4 knobs)
    3. Row 3: `FX SENDS` (4 knobs)
    4. Row 4: `MASTER FX` (4 knobs)
- **Rationale**:
  - Delivers complete UI and ergonomic symmetry: every single macro bank across both Classic and Performance modes is exactly 4 knobs wide and 1 row high.
  - Fixes vertical space crunch in Column 3, allowing the Binding Inspector and live preview to breathe.
  - Turns `MASTER & FX` into a high-utility routing and composite performance surface with zero wasted knobs.

---

## Hard Consolidation of Transitions & The "Elite 8" Curated Transition Suite (`ISFTransitionRegistry.kt`, `default_transitions/`, `Mixer.kt`, `FileSystemManager.kt`, `build.gradle.kts`)

- **Context**: 2026-09-20. Liquid LSD previously bundled 11 stock transitions (`additive_blend`, `screen_blend`, `multiply_blend`, `max_blend`, `wipe_horizontal`, `wipe_vertical`, `radial_wipe`, `luma_wipe`, `glitch_transition`, `zoom_fade`, `linear_crossfade`). Following the shader curation overhaul, an audit benchmarked against Resolume Arena, VDMX, and Synesthesia revealed that legacy mathematical blend formulas, simplistic PowerPoint-style wipes, and primitive block glitches degraded live VJ quality. As fast-moving beta software, backwards compatibility was explicitly dropped to allow a clean hard cut.
- **Decision**:
  - **Deprecated & Removed 10 Legacy Files**: Deleted `additive_blend.fs`, `screen_blend.fs`, `multiply_blend.fs`, `max_blend.fs`, `wipe_horizontal.fs`, `wipe_vertical.fs`, `radial_wipe.fs`, `luma_wipe.fs`, `glitch_transition.fs`, and `zoom_fade.fs`.
  - **Curated "Elite 8" Transition Suite (`src/main/resources/default_transitions/`)**:
    1. **`linear_crossfade.fs`**: High-precision dissolve with selectable Perceptual Cosine S-curve, Linear, and Equal Power curve modes to eliminate midpoint perceived luminance drop.
    2. **`luminous_flash.fs`**: Midpoint exposure flare and Gaussian bloom overdrive with tunable color temperature (-1.0 icy strobe to +1.0 warm incandescent) and spread, designed for musical drop transitions.
    3. **`film_burn.fs`**: Organic 35mm celluloid burn featuring multi-octave procedural fractal noise erosion with intense chromatic glowing combustion contours (Fiery Ember, Electric Violet, Acid Green).
    4. **`noise_dissolve.fs`**: Multi-octave domain-warped fractal noise erosion with feathered edge contouring and subtle chromatic aberration on dissolving frontiers.
    5. **`liquid_displacement.fs`**: Interactive cross-deck vector morphing where Deck A and Deck B dynamically displace each other's UVs using luminance gradient fields, creating a molten hydrodynamic melt.
    6. **`kinetic_zoom.fs`**: High-speed camera crash zoom with multi-tap radial velocity streak blur, edge chromatic dispersion, and exponential acceleration curves.
    7. **`vortex_swirl.fs`**: Gravitational singularity twisting Deck A into a spiraling vortex at the center of the frame, peaking at midpoint, and unwinding into Deck B with chromatic flare.
    8. **`cyber_datamosh.fs`**: Video compression breakdown emulating digital I-frame / P-frame corruption, macroblock displacement, horizontal sync tear, and chromatic shear.
  - **Cleaned Mixer Engine**: Removed legacy hardcoded mode-to-transitionId mappings in `Mixer.kt` that attempted to synchronize `mode` to non-existent blend shaders.
  - **Factory Transition Presets & Playlists**: Bundled 8 curated `.lsdtrans` presets and the default `festival_elite.lsdtransplay` playlist in `defaults/` and `library/`, and automated classpath asset packaging via `PrepareDefaultAssetsTask` and `FileSystemManager.ensureDefaultLibrary()`.
- **Rationale**:
  - Elevates stage visual production values to club- and festival-ready standards.
  - Eliminates visual stutter and muddy mid-fade artifacts.

---

## Premier 8-Generator Procedural ISF v2.0 Suite & Canonical Source Packaging (`VisualSourceRegistry.kt`, `SourceDocRegistry.kt`, `default_sources/`, `library/sources/`, `web/sync_manifest.json`)

- **Context**: 2026-09-20. Following the pure ISF v2.0 standardization where legacy proprietary sources were pruned down to 3 core favorites (`mandala`, `dynamic_spiral`, `icosa_h3`), users required a rich, curated catalogue of procedural generators designed for high expressive variety, visual "wow", and balanced coverage across geometric, 3D minimal surface, 4D polytope, organic fluid, and acoustic domains.
- **Decision**:
  - **Curated 8-Generator Core Lineup**:
    - **`mandala`** (Existing): 4-arm Lissajous harmonic curves with 300+ curated recipes and size normalization.
    - **`dynamic_spiral`** (Existing): High-performance logarithmic particle streak dynamics with wave frequencies and shear.
    - **`icosa_h3`** (Existing): Native 3D $H_3$ Coxeter raymarcher with continuous duality morph and stellation CSG.
    - **`domain_warp_fluid`** (New): Multi-scale domain-warped fBm fluid simulation with curl noise, dynamic vorticity, surface specular normals, and 5 color palettes (Psychedelic Neon, Liquid Chrome, Oil Slick, Opal Sunset, Deep Ocean).
    - **`gyroid_hyperspace`** (New): Native 3D raymarcher rendering Triply Periodic Minimal Surfaces (TPMS) with continuous morphing between Gyroid, Schwarz P, and Neovius minimal surfaces, volumetric internal radiance, and camera flight.
    - **`celestial_engine`** (New): Multi-symmetry sacred geometry and op-art generator combining Flower of Life overlapping circles, concentric harmonic rings, phase twisting, and moiré fringes.
    - **`hyper_slice`** (Cleanroom Port): Raymarched 3D cross-section MRI scan through 4D 120-cell (600 vertices) and 600-cell (120 vertices) polytopes using the $H_4$ Coxeter reflection group with 4D hyper-rotations ($XW, YW, ZW$) and Wythoff facet morphing.
    - **`chladni_cymatics`** (Cleanroom Redesign): Physical 2D acoustic plate resonance simulation computing standing wave nodal harmonics across square and circular boundaries with particle accumulation physics and antinode fluid inversion.
  - **Canonical Packaging Architecture (`src/main/resources/default_sources/`)**: Established `src/main/resources/default_sources/` as the canonical build source (matching `default_filters/` and `default_transitions/`), extracted automatically into `library/sources/` on fresh installations via `VisualSourceRegistry.ensureDefaultSources()`.
  - **Web Parity & Transpilation**: Updated `web/sync_manifest.json` and generated WebGL2 fragment shaders under `web/shaders/` via `scripts/sync_web.py --apply`.
  - **Full Documentation**: Added detailed parameter docstrings in `SourceDocRegistry.kt` and user guide documentation in `docs/user_guide/visual_sources.md`.
- **Rationale**:
  - Delivers maximum visual variety and parameter-sweeping responsiveness without CPU allocations or runtime overhead.
  - Standardizes generator resource bundling alongside filters and transitions.

---

## Two-Tier FX Macro Knobs (Chain Super Knob + Effect Metaknobs) & ISF Auto-Bind Engine (`FxMetaBinding.kt`, `ISFAutoBindEngine.kt`, `ISFFilter.kt`, `ISFModels.kt`, `FxChain.kt`, `FxBank.kt`, `FXChainMacroStrip.kt`, `MacroPanel.kt`, `ParametersTabs.kt`, `AssetType.kt`, `FileSystemManager.kt`, `FXBrowserPanel.kt`)

- **Context**: 2026-09-20. Building on the 3-chain FX bank architecture below, users needed Mixxx/Traktor S2 MK2-style performance macro knobs: one knob per effect ("Metaknob") plus one knob per chain ("Super Knob") that drives all 3 linked Metaknobs together. Since users can load thousands of arbitrary third-party ISF shaders (not just a hand-curated set), each shader's Metaknob needed a sensible default binding without manual configuration.
- **Decision**:
  - **ISF Auto-Bind Engine (`ISFAutoBindEngine.kt`, `FxMetaBinding.kt`)**: Resolves each `ISFFilter`'s Metaknob binding via 3 tiers, checked most-specific-first: (1) a user override cached by shader content hash (SHA-256 of source, so it survives file moves/pack updates) under `library/isf_overrides/`; (2) a hand-curated table for bundled filters (`invert`, `hue_shift`, `posterize`, etc.); (3) an automated heuristic over the shader's declared `INPUTS` — the new `IDENTITY` property (sweep direction resolved for min-anchored/max-anchored/interior cases), then semantic name matching (`amount`, `intensity`, `mix`, `decay`, etc., with time/frequency-like names defaulting to an exponential curve), then a single/normalized-float fallback, then a Dry/Wet safety net if no float candidate exists at all.
  - **Metaknob starts at the target's authored default**: `ISFFilter.metaKnob` initializes (and re-syncs on `reset()`) to the knob position that reproduces the bound parameter's own default value, so loading a filter never silently overwrites its authored default via an implicit knob=0.
  - **Chain Super Knob with Soft-Takeover Linking (`FxChain.kt`)**: Each `FxChain` gained a `superKnob` and per-slot `slotSuperKnobLink` flags. A linked slot's Metaknob only starts following the Super Knob once the Super Knob's own movement crosses (or comes within ~4% tolerance of) that slot's current Metaknob value — mirroring the existing MIDI/OSC hardware pickup convergence pattern — so relinking or loading a new filter into an already-linked slot never yanks its Metaknob to wherever the Super Knob currently sits.
  - **FX Rack Performance Strip (`FXChainMacroStrip.kt`)**: New Traktor-style strip (Super Knob + per-slot Metaknob/Link/Focus controls) drawn above the per-slot accordion in `ParametersTabs.kt` and as a dedicated FX Rack view replacing the generic knob grid in `MacroPanel.kt` for the FX1/FX2/MFX tabs. "Single FX Focus Mode" swaps the 3 knobs to the focused slot's own top parameters. Right-click a Metaknob to rebind it to a different parameter, the Dry/Wet safety net, or reset to the auto-bind default.
  - **Library Browser FX Bank Support (`AssetType.kt`, `FileSystemManager.kt`, `FXBrowserPanel.kt`)**: Added `AssetType.FX_BANK` and `scanAllFxBanks()` so `.lsdfxbank` presets are listed/filterable/loadable (`Load to FX1/FX2/MFX`) alongside single FX and chain presets. Also fixed a pre-existing multi-chain gap: the browser's "Load to Deck" menus for stock/single/chain FX previously always targeted Chain 1 only (via `FxBank`'s backward-compat delegates); they now nest a Chain 1/2/3 selector.
- **Rationale**:
  - Matches the muscle-memory of dedicated DJ/VJ hardware (Traktor S2 MK2's 4-knob FX unit) without requiring hand-curated bindings for the long tail of ISF shaders.
  - Content-hash-keyed overrides (not path- or sidecar-file-based) survive shader pack reorganization and avoid writing into potentially read-only or shared third-party shader directories.

---

## 3-Chain FX Banks & Multi-Chain Architecture (`FxBank.kt`, `FxChain.kt`, `Deck.kt`, `Mixer.kt`, `Renderer.kt`, `ParametersTabs.kt`, `ParametersState.kt`, `FXPresetModels.kt`, `PresetRepository.kt`, `FileSystemManager.kt`)

- **Context**: 2026-09-20. To level up to DJ/VJ performance standards (such as Mixxx and Traktor Pro), the FX architecture needed to evolve beyond simple banks of 3 individual effect slots. Users needed each FX bank to host multiple FX chains in series, with each chain hosting multiple effect slots, allowing complex multi-stage effect soundscapes (e.g. Distortion chain -> Color/Glitch chain -> Delay/Feedback chain), while keeping the parameter panel manageable via horizontal subtabs and maintaining strict real-time GPU rendering without feedback aliasing.
- **Decision**:
  - **3-Chain Hierarchy (`FxChain.kt`, `FxBank.kt`)**: Re-architected `FxBank` (`FX1`, `FX2`, `MFX`) so each bank owns 3 serial `FxChain` instances (`CHAIN_COUNT = 3`), and each `FxChain` owns 3 ISF filter slots (`SLOT_COUNT = 3`), providing up to 9 effects per bank and up to 27 filters system-wide.
  - **Full Parity for Master FX (`Mixer.kt`)**: Replaced raw `masterFxSlots` with an `FxBank("MFX")` instance, giving master output identical 3-chain capability and enabling unified UI rendering via `drawFxBankGroupContent`.
  - **4-Buffer Ping-Pong GPU Architecture (`Deck.kt`, `Mixer.kt`, `Renderer.kt`)**: Upgraded offscreen FBO architecture to 4 buffers per deck/mixer:
    - Inner scratch pair (`fxPingFBO`, `fxPongFBO`) for slot-to-slot progression *within* a chain.
    - Outer alternating pair (`fxChainOutFBO`, `fxBankOutFBO`) carrying chain-level blended results forward into the next chain.
    - Ensures no shader pass reads and writes to the same texture/FBO, preventing driver texture feedback loop aliasing.
  - **Modulatable Deck Routing (`Deck.kt`, `Mixer.kt`, `ParametersTabs.kt`)**: Added discrete modulatable parameter `View/FxRouting` (`0.0f` = None/Bypass, `1.0f` = FX1, `2.0f` = FX2) to each deck, synced in `Mixer.update()`.
  - **Horizontal Subtabs UI (`ParametersTabs.kt`, `ParametersState.kt`)**:
    - Bank Header: Displays bank label, Bank Enabled bypass toggle, Bank Wet/Dry slider (`$bankLabel/DryWet`), and Bank Kebab menu (Save `.lsdfxbank`, Copy, Paste, Clear All).
    - Horizontal Subtabs `[ Chain 1 ] [ Chain 2 ] [ Chain 3 ]` allow quick navigation without overwhelming vertical scrolling.
    - Active Chain Section: Displays chain name, Chain Enabled bypass, Chain Wet/Dry (`$bankLabel/C$chainNum/DryWet`), Chain Kebab menu (Save `.lsdfxchain`, Copy, Paste, Clear), followed by accordion rows for the 3 slots (`$bankLabel/C$chainNum/FX$slotNum/*`).
    - Drag-and-drop support: `.lsdfxbank` on bank, `.lsdfxchain` on bank/chain/slot, `.lsdfx` on slot.
  - **Preset Persistence (`FXPresetModels.kt`, `PresetRepository.kt`, `FileSystemManager.kt`)**:
    - Added `dryWet: ParameterDto?` to `FXChainDto` for chain-level blend persistence.
    - Added `FXBankDto` for `.lsdfxbank` presets stored in `library/fx_banks/`.
    - Extended `PresetRepository` with `saveFxBankAsync` and `loadFxBankAsync`.
- **Rationale**:
  - Provides professional-grade modular chaining while keeping parameter discovery and modulation clean and accessible.
  - Guarantees 100% architectural and UI parity across FX1, FX2, and MFX.
  - Adheres to Thread 0 OpenGL rendering safety and zero allocations on the audio callback thread.

---

## Direct MIDI Control for Modulator Variables & In-Situ MIDI Learn (`ParametersState.kt`, `MidiMappingManager.kt`, `BeatDivisionSlider.kt`, `CustomRangeSlider.kt`, `MidiPreferencesPanel.kt`, `MidiMappingManagerModulatorTest.kt`)

- **Context**: 2026-09-20. Following the implementation of direct OSC control for modulator variables, full hardware control parity was required for physical MIDI controllers (rotary knobs, sliders, pads, foot switches). Users needed to bind hardware controls directly to internal modulator variables (LFO speed/subdivision, depth, min/max bounds, asymmetry, morph, hold) via right-click in-situ learning without consuming a Macro knob.
- **Decision**:
  - **Shared Modulator Path Schema Parity**: Extended `MidiMappingManager` to natively support the hierarchical `:mod/<modulatorIndex>/<propertyName>` path schema established for OSC (e.g., `Deck A/geometry/zoom:mod/0/subdivision`).
  - **Dynamic Modulator Resolution & Allocation-Free Dispatch**: Updated `ResolvedMidiBinding` to delegate value reading and writing dynamically to `ModulatorPropertyAccessor` via `getCurrentValue()` and `applyValue()`. This prevents stale object references across UI modulator cloning while supporting continuous CCs, relative rotary deltas (Binary Offset, Signed Bit, Two's Complement), discrete buttons/pads (Momentary, Toggle, Step +/-), exponential slew smoothing ($0 \dots 250\,\text{ms}$), and soft takeover (pickup).
  - **In-Situ Right-Click "Learn MIDI" Menus (`BeatDivisionSlider.kt`, `CustomRangeSlider.kt`)**: Added right-click context menu options alongside OSC learn. Sliders armed for MIDI learn pulse in cyan/blue while awaiting hardware input.
  - **Preferences UI Formatting (`MidiPreferencesPanel.kt`)**: Formatted modulator sub-paths in the MIDI mapping table into readable labels (e.g. `Deck A/geometry/zoom [LFO 1 Speed]`) with full path tooltips on hover.
  - **Soft Takeover Verification Alignment**: Updated `isSoftTakeoverActive` in both `MidiMappingManager` and `OscMappingManager` to accurately reflect `hasTakenOver` when `takeoverMode == SOFT_TAKEOVER`, eliminating premature "Takeover Synced" status before physical pickup.
- **Rationale**:
  - Provides complete feature and architectural parity between MIDI and OSC control surfaces.
  - Zero-allocation callback thread discipline and lock-free event processing maintained on Thread 0.

---

## Direct OSC Control for Modulator Variables & In-Situ OSC Learn (`ModulatorPropertyAccessor.kt`, `OscMappingManager.kt`, `OscLearnState.kt`, `BeatDivisionSlider.kt`, `CustomRangeSlider.kt`, `OscPreferencesPanel.kt`)

- **Context**: 2026-09-20. OSC control in Liquid LSD previously mapped exclusively to top-level `ModulatableParameter.baseValue` paths (e.g. `Mixer/crossfade`, `Deck A/geometry/zoom`). While internal modulator properties (such as an LFO's period/speed `subdivision`, depth, morph, or asymmetry) could be manipulated via Macro knobs (`/macro/<bankId>/knob/N`), there was no direct OSC addressing or in-situ Learn flow for modulator variables without consuming a Macro knob.
- **Decision**:
  - **Hierarchical Modulator Path Syntax**: Introduced a colon-delimited sub-path convention `<parameterPath>:mod/<modulatorIndex>/<propertyName>` (e.g. `Deck A/geometry/zoom:mod/0/subdivision`). Backward-compatible with existing JSON mapping profiles under `library/osc/`.
  - **Protocol-Agnostic Accessor (`ModulatorPropertyAccessor.kt`)**: Created an allocation-free utility object to read and mutate `CvModulator` properties (`subdivision`, `depth`, `phaseOffset`, `slope`, `morph`, `hold`, `dcOffset`, `lfoMin`/`max`, `attackMs`, `decayMs`, etc.) and format human-readable labels. Refactored `MacroEngine.applyModulatorProperty` to delegate to `ModulatorPropertyAccessor.set`.
  - **Dynamic Target Resolution in `OscMappingManager.kt`**: Extended `resolveTarget` to resolve both top-level parameters and nested modulator variables dynamically each tick, preventing stale object references when the UI clones modulators, and applying full min/max scaling, invert, slew smoothing ($0 \dots 250\,\text{ms}$), and soft takeover to modulator fields.
  - **In-Situ Right-Click "Learn OSC" Menus (`BeatDivisionSlider.kt`, `CustomRangeSlider.kt`)**: Added right-click context menus on LFO and modulator sliders to arm OSC Learn on the fly with a single click. When armed, sliders render a pulsing amber outline and contextual tooltip feedback.
  - **Preferences UI Formatting (`OscPreferencesPanel.kt`)**: Formatted modulator sub-paths into clean labels (e.g. `Deck A/geometry/zoom [LFO 1 Speed]`) with full path inspection on hover.
- **Rationale**:
  - Allows live performers to directly bind TouchOSC and external OSC controllers to LFO speeds, shapes, and modulation depths with zero boilerplate.
  - Prepares the shared accessor layer so the same right-click learn flow can be extended to hardware MIDI in a subsequent pass without redesigning widget logic.

---

## Parameters VAL Cell Modulator Mute Toggle (`ParametersRenderer.kt`, `ParametersPanel.kt`, `ParametersValMuteTest.kt`)

- **Context**: 2026-09-20. Clicking or right-clicking on the VAL cell in the Parameters panel previously risked clearing all active modulators on that row due to a misplaced middle-click reset handler and `param.reset()` invocation. Users requested that right-clicking the VAL cell toggle mute for all modulators on that row instead of destructively wiping them out.
- **Decision**:
  - Right-clicking (mouse button 1) the VAL cell now directly toggles bypass/mute for all modulators on that parameter row. If all modulators are currently bypassed, it un-bypasses (unmutes) them; otherwise, it bypasses (mutes) them. If unmuting crossfade modulation, `mixer.onCrossfadeCvUnmuted()` is dispatched.
  - Middle-clicking (mouse button 2) the VAL cell now also toggles mute when modulators are present on that row, guarding against accidental destruction of modulation routes. When no modulators exist, middle-clicking safely resets the base value to default (`param.reset()`).
  - Moved row label click handlers directly adjacent to `invisibleButton("row_label_btn_$paramKey")`, fixing an issue where trailing click handlers checked mouse clicks against unrelated or subsequent widgets.
  - Added a "Mute all modulators" / "Unmute all modulators" entry to the row label context menu (`row_menu_$paramKey`).
  - Updated tooltips on the VAL column header and VAL cells to document the toggle mute interaction.
- **Rationale**:
  - Matches the existing master `[ LIVE ]` / `[ MUTED ]` toggle in the Properties panel when inspecting VALUE.
  - Prevents irreversible loss of complex multi-modulator routing configurations while enabling fast, non-destructive A/B auditioning of parameter modulation during live performances.

---

## Removed Per-Preset FX (`PresetModels.kt`, `SessionSerializer.kt`, `ISFMultiPassTest.kt`)

- **Context**: 2026-09-19. Per-deck FX (manual editing, FX Browser, FX Playlists, and Live FX Queues via `FxQueueEngine`) fully superseded the older mechanism of embedding an FX chain inside a visual preset (`DeckPresetDto.fxSlot1..4`/`fxChainEnabled`/`fxChainDryWet`, restored on `Deck.applyDto()` and captured on `Deck.toDto()`). Keeping both meant a preset with configured FX would clobber whatever the deck's live FX queue/playlist had loaded — the exact hybrid "only overwrite if the preset has FX" behavior recorded as **Preserve Deck FX on Clean Preset Load** in the release notes was a patch over this redundancy, not a fix for it.
- **Decision**:
  - Deleted `fxSlot1..4`, `fxChainEnabled`, `fxChainDryWet` from `DeckPresetDto` entirely. `Deck.toDto()`/`Deck.applyDto()` no longer read or write a deck's FX state at all — loading or saving a `.lsd` preset is now a strict no-op for FX in every case, not just the "preset has no FX configured" case.
  - Deleted the corresponding unresolved-filter validation for `dDto.fxSlot1..4` in `SessionSerializer.loadSession()` (master FX and transition-slot validation are untouched — separate systems).
  - Deleted the `ISFMultiPassTest` test asserting on `DeckPresetDto.fxSlot1`/`fxSlot2` round-tripping.
  - No migration/back-compat path: old `.lsd`/session files on disk that still have `fxSlot*` keys load fine and those keys are silently dropped by `ignoreUnknownKeys = true`. This is beta software; breaking changes are acceptable and preferred over carrying dead fields.
  - `FXSlotDto` itself is kept — still the shared shape for `Mixer.masterFxSlots` and the standalone `.lsdfx`/`.lsdfxchain` asset files, which are unrelated to this removal.
- **Rationale**:
  - Eliminates a second, competing source of truth for deck FX state; FX is now unambiguously a per-deck, queue/playlist/manual-driven concern.
  - Removes the load-time coupling where editing a deck's FX chain marked the currently-loaded preset "dirty" (`PresetManager.isDeckDirty` compares `toDto()` snapshots) — preset dirtiness now reflects only visual-source/feedback/view state, which better matches how deck FX and presets are actually used independently in performance.
  - Simplifies `Deck.applyDto()` by removing a branch whose entire purpose was to paper over the two systems overlapping.

---

## Architectural Compliance: ImGui Widget Allocation Caching & Playlist Test Confinement (`ValueParamSection.kt`, `Lfo1Section.kt`, `Lfo2Section.kt`, `SeqSection.kt`, `PlaylistManagerTest.kt`)

- **Context**: 2026-09-18 architectural audit. Audited real-time audio, Thread 0 OpenGL context safety, UI typography, and ImGui native memory management against project standards.
- **Decision**:
  - **ImGui Widget Allocation Hardening**: Eliminated ephemeral per-frame `ImInt(...)` wrapper and label array allocations in secondary parameter and modulator panels (`ValueParamSection.kt`, `Lfo1Section.kt`, `Lfo2Section.kt`, `SeqSection.kt`) by pre-allocating reusable `ImInt` singleton fields and static/cached label arrays. Removed unnecessary non-null assertion on smart-cast `macroInfo`.
  - **Playlist Test Containment Alignment**: Updated `PlaylistManagerTest.testCreateAndAutoSaveOperations` to create its temporary test fixture inside `FileSystemManager.getPlaylistsRoot()`, conforming with the asset sandboxing rules enforced by `FileSystemManager.isManagedAssetPath()`.
- **Rationale**:
  - Enforces the `imgui_memory_management` standard ("Im-types Must Be Fields, Not Locals") across all parameter and modulator sections in `PropertiesPanel`, eliminating GC allocation churn during UI inspection.
  - Ensures the full test suite runs cleanly while maintaining strict path confinement against path traversal outside managed asset roots.

---

## Per-Binding Switch Behavior Override (`MacroModels.kt`, `MacroEngine.kt`, `MacroBindingInspector.kt`, `MacroKnobWidget.kt`)

- **Context**: 2026-09-17. The Binding Inspector previously applied a single `SwitchBehavior` (TOGGLE/MOMENTARY/TRIGGER) to all of a switch control's bindings. Users asked whether a single button press could latch one parameter, pulse a second, and hold a third simultaneously.
- **Decision**:
  - `SwitchBehavior` stays on `MacroControl` as the **default** that all bindings inherit. It is now labelled "Default Behavior" in the Inspector.
  - A new `switchBehaviorOverride: SwitchBehavior?` field on `MacroBinding` carries the per-binding override; `null` = inherit the control default (backward-compatible serialization: old presets deserialize `null` cleanly via `ignoreUnknownKeys`).
  - `MacroControl` gains two `@Transient` fields: `rawPressValue` (1f while held, 0f on release) and `prevRawPressValue` (previous frame, for edge detection). These are set in `onPress`/`onRelease` alongside the existing `value` state machine, which is left unchanged.
  - `MacroEngine.effectiveValue(control, binding)` computes the binding's normalized input: falls back to `control.value` for knobs or when no override is set; routes through per-binding state (`bindingLatchState`, `rawPressValue`, `bindingPendingPulse`) for the three override cases.
  - The per-binding state machines (TOGGLE edge-flip, TRIGGER pulse arm/consume) are driven by a press-edge detection loop in `MacroEngine.tick()`, immediately after the existing TRIGGER reset sweep.
  - The switch widget is **not changed**: `isLit = control.value >= 0.5f` continues to reflect the button's raw press state, which is the correct mental model — the button is the input, not any one binding's output.
- **Rationale**:
  - Keeping `control.value` as the processed output for the widget and keeping `rawPressValue` as the raw signal consumed only by the engine cleanly separates rendering from evaluation without breaking any existing callers.
  - `@Transient` body-property placement (not primary-constructor params) follows the established pattern set by `MacroControl.pendingTriggerReset` — excluded from `equals`/`hashCode`/`copy` and from serialization without a custom serializer.
  - The Inspector shows the per-binding override combo only when the parent is a switch control; the first option dynamically embeds the current default behavior label (e.g. `"— default (Toggle)"`) so the user always knows what "inherit" means without scrolling up.

---

## Full Screen Video & Monitor Alpha Blend Parity (`Main.kt`, `default_filters/feedback.fs`)

- **Context**: On 2026-09-17, a visual rendering mismatch was identified between ImGui confidence monitors and full screen video mode: feedback effects (such as "Full feedback with zoom...") rendered drastically heavier and thicker on full screen video than in the confidence monitors.
- **Decision**:
  - Fixed `Main.kt`'s full screen and secondary window blit calls to use `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` instead of `glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)`.
  - Added display aspect-ratio scaling to `default_filters/feedback.fs` (`float aspect = RENDERSIZE.x / RENDERSIZE.y; uv.x *= aspect; ... uv.x /= aspect;`).
- **Rationale**:
  - ImGui's monitor rendering pipeline uses `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)`, which multiplies the source RGB by alpha ($\alpha \times \text{RGB}$).
  - `Main.kt` was using `GL_ONE`, which assumed premultiplied alpha and drew non-premultiplied decaying feedback pixels at full 100% RGB weight ($1.0 \times \text{RGB}$), ignoring alpha attenuation. This caused decaying feedback trails and low-alpha pixels to linger brightly on full screen video while fading to black in confidence monitors.
  - Applying `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA` to full screen video ensures 100% visual parity across all monitors and full screen outputs.

---

## Modular Video Rack Phase 9: Unit Consolidation & Rack Layout Finalization (`RackUnit.kt`, `RackManager.kt`, `RackFaceplateGrid.kt`, `RackMicroMonitor.kt`, `RackPanel.kt`, `FBO.kt`, `PerformanceStats.kt`, `MenuBar.kt`)

- **Context**: All 6 Open Questions in `docs/developer/modular_video_rack_proposal.md` §3 were decided 2026-09-16. Phase 9 implements those decisions: merge the per-stage rack units into one unit per deck, add a Deck BG column, build the Queue & Staging master unit, and act on the GPU-resource-management decision (Question 4).
- **Decision**: Implemented as scoped in the proposal, with three deviations worth recording:
  - **Merged unit type, not three**: `DeckGeneratorUnit` and `ISFProcessorUnit` were deleted outright rather than kept alongside the new `DeckRackUnit`, once `RackManager.populateFromSession` no longer constructed either (confirmed zero remaining construction sites app-wide, including `RackPanel.kt`'s manual "Add Unit" modal). Their `RackFaceplateGrid.kt` faceplate-drawing branches were deleted in the same pass rather than left as dead code.
  - **The "3 parallel columns vs. 1 serial pipeline" concern in the proposal resolved itself**: `RackPipeline.process()` threads a single `currentTexture` through a flat `units` list, which reads like it would wire Deck B's output into Deck BG's input by default. In practice every built-in unit's `process()` (from the Phase 5-8 correctness pass, see the entry below) already ignores `inputTexture` entirely and reads its own pre-rendered `Deck`/`Mixer` state directly — so the flat list ordering doesn't corrupt anything today, and no column data structure was added to `RackPipeline`. This only becomes a real gap once a genuinely patchable unit (rack doc Q2 Models B/C, still deferred) starts actually consuming upstream pixels.
  - **Off-screen GL culling (part of Question 4's "smart culling") was deliberately not built**, unlike the rest of Question 4. Bypass/power-off skip already existed pre-Phase-9. Off-screen culling would require the pipeline to know per-unit screen visibility before it runs (an architecture change, since `RackPipeline.process()` resolves the whole rack in one pass before the UI loop lays out unit positions), for a payoff that's currently ~zero: every unit's `process()` is a cheap texture-ID read, not a real draw call. Revisit once a real patchable transition/splitter unit does real per-frame GPU work.
  - **Monitor downscaling reused an existing primitive instead of new shader code**: `Renderer.rescale(srcTex, srcW, srcH, destFbo, mode)` already existed (used for output-scaling elsewhere) and does exactly what Question 4 asked for — blit a source texture into a smaller destination FBO. `RackMicroMonitor` now lazily creates one 240×135 preview `FBO` per unit (keyed by `unit.id`) and blits into it with `OutputScaleMode.STRETCH` each frame the unit's faceplate is actually drawn (collapsed units skip this automatically, for free). Preview FBOs are released via `releaseUnit`/`releaseAll`, called from `RackPanel.kt` on unit removal and on rack re-sync (`populateFromSession` discards the old unit list without disposing it, so without this the preview cache would leak one FBO per removed/re-synced unit).
  - **GPU-memory telemetry**: `GLResourceTracker` turned out to track a mix of resource types (FBOs, shaders, audio textures) in one flat id->description map, not usable for an FBO-specific count. Added a small companion-object registry directly to `FBO.kt` instead (`liveCount`/`liveBytes`, format-aware byte estimate), exposed via `PerformanceStats.fboCount`/`fboMemoryMB` and shown in the menu bar. Instrumentation only — no pooled allocator was built, per the Question 4 decision to measure before building it.
  - **Found and fixed while merging**: `DeckGeneratorUnit`/`MixerTransitionUnit`'s `update()` overrides called `deck.source.update()`/`mixer.update()` directly. `Main.kt`'s main render loop already calls these unconditionally every frame regardless of workspace mode (`WorkspaceMode` only ever gates which *panel* gets drawn, never the underlying simulation tick) — so whenever Rack mode was visible, `RackManager.update()` (called from `RackPanel.draw()`) was double-ticking every deck's FX filter history and `BgQueueManager`'s dip-to-black fade timer. `DeckRackUnit` and `MixerTransitionUnit` now have no `update()` override, inheriting `BaseRackUnit`'s no-op default.
- **Rationale**:
  - Each deviation above followed the same rule: verify the actual current behavior before building on top of it, rather than trusting the proposal's problem framing at face value. Two of the three "required" pieces of new infrastructure (column-aware pipeline, off-screen culling) turned out to be either unnecessary or not worth building yet once the real code was read.
  - Reusing `Renderer.rescale()` for monitor downscaling avoided writing new, unverified GPU code for a purely cosmetic feature — same-codebase precedent that had already been tested by existing callers.
  - The `update()` double-tick was a real, previously-undiscovered correctness bug (not introduced by Phase 9 — it dates to the Phase 5/6 wrappers) that only manifests while Rack mode is the visible workspace; worth fixing in the same pass since Phase 9 was already touching every line that called it.

---

## Modular Video Rack Correctness Pass: Read-Only Signal Monitoring, Not an Independently-Repatchable Graph (`RackUnit.kt`, `RackPipeline.kt`, `RackManager.kt`, `RackPatchBay.kt`, `MacroEngine.kt`, `UIManager.kt`, `RackPanel.kt`, `MacroBindingInspector.kt`)

- **Context**: A 2026-09-15 post-implementation review of Milestone 6 Phases 5-8 found that `RackManager.process()` — the routine that resolves each rack unit's `lastOutputTexture` for normalled/patched routing — was implemented and unit-tested but never called from any production code path. Every confidence micro-monitor showed "NO SIGNAL" and every rear-panel LED stayed dark in the running app.
- **Decision**: Wired `RackManager.process(renderer)` into the live per-frame ImGui draw path (`RackPanel.draw()`, via a `Renderer` reference threaded through `UIManager.render()` -> `drawLayout()` -> `drawAssetManagementLayout()`), but rejected the literal original design of having each `RackUnit.process()` re-invoke real rendering (`Renderer.render()`/`Renderer.renderMixer()`/`ISFFilter.render()`) to do it:
  - **Read-only monitoring for built-in wrapper units**: `DeckGeneratorUnit`, `ISFProcessorUnit`, `FeedbackProcessorUnit`, and `MixerTransitionUnit` now read the textures Main.kt's normal render pass already computed this frame (`deck.cleanFBO.texture`, `deck.fxFBOs[slot].texture`, `mixer.masterFBO.texture`) instead of re-rendering. Main.kt's `renderer.renderDeck(deckA)`/`renderMixer(mixer)` calls already run once per frame before the Rack UI draws; calling them a second time from inside `process()` would have doubled GPU cost and, for filters with persistent per-frame history buffers (e.g. the modular feedback ISF filter), corrupted that history by advancing it twice per frame.
  - **Patch-cable overrides stay real only for genuinely custom/utility units** (`GenericRackUnit` and any future truly-modular unit type), which already correctly consume the `inputTexture` argument. For units wrapping `Deck`'s fixed FX-slot chain or `Mixer`'s hardcoded Deck A/B crossfade, a dragged cable renders and its jack LED lights up, but does not reroute pixels — the rear-panel topology for these units accurately reflects Liquid LSD's real (currently fixed) signal path rather than pretending to be independently repatchable.
  - **Fixed the resulting FBO aliasing**: because built-in units no longer write into `RackPipeline`'s shared `stageFboA`/`stageFboB` ping-pong buffers, chained ISF FX stages (the default topology once more than one FX slot is active per deck) can no longer overwrite each other's texture before the confidence monitor samples it.
  - **Fixed `unitInstanceId` collisions**: `DeckGeneratorUnit`, `ISFProcessorUnit`, `FeedbackProcessorUnit`, and `MixerTransitionUnit` previously used fixed, type-derived ids (`"deck_a_gen"`, `"master_transition"`, etc.) instead of per-instance UUIDs. Cloning a unit via "Add Unit" silently overwrote the original's registered `MacroBank` in `MacroEngine` and could delete the wrong unit on removal. All four now default to `BaseRackUnit`'s random UUID, matching the proposal's "assigned when the unit is dropped into the bay" intent.
  - **Discovered while investigating `FeedbackProcessorUnit`**: its curated macro knobs (GAIN/DECAY/ZOOM/HUE) are bound to `Deck.fbGain`/`fbDecay`/`fbZoom`/`fbHueShift` — fields that predate the ISF pipeline migration (see ARCHITECTURE.md's "100% ISF Pipeline & Modular Effects Engine") and are retained on `Deck` only for backward preset/broadcast serialization; no shader reads them anymore (confirmed by grep across `src/main`). Feedback today is just another ISF filter (`default_filters/feedback.fs`) that, when loaded into an FX slot, already appears in the rack as its own `ISFProcessorUnit` with real, live parameters. Left the class in place (still constructible, still tested) but made `process()` an honest passthrough and documented the dead fields, rather than silently leaving knobs that visibly move but do nothing — see `ROADMAP.md` Milestone 6 "Known issues to revisit" for the follow-up.
  - **Also fixed as part of the same pass**: two rear jacks required by the Phase 8 spec but never implemented (`Mask/Sidechain In`, `CV Modulation In`) were added to every unit type; `getRearPorts()` is now cached per-unit instead of rebuilt every frame; `MacroEngine.tick()`/`findBindingsTargeting()` no longer allocate in the common per-frame case (the latter is called every frame per rendered parameter row from three UI panels); `RackPipeline.process()` and `RackPatchBay.findCableInputFor()` replaced per-frame lambda closures and string concatenation with indexed loops and a cached `PatchPort.fullId`; hardware MIDI Learn for macro knobs/switches was wired up in `MacroBindingInspector` (the CC dispatch already existed in `MidiMappingManager`, but no UI could ever arm it).
- **Rationale**:
  - Making the Rack a correct **monitor** of Liquid LSD's real signal state is a contained, low-risk fix. Making its patch cables **independently reroute** the built-in Deck/Mixer/FX pipeline is a materially larger change — it would mean restructuring `Deck`'s fixed FX-slot chain and `Mixer`'s hardcoded Deck A/B inputs to accept externally supplied textures, touching the single master-output path every workspace mode (not just Rack mode) depends on. That tradeoff should be made deliberately, not as a side effect of wiring up an existing-but-dormant pipeline.
  - Zero-allocation and Thread 0 discipline (ARCHITECTURE.md) apply to this pipeline once it's actually running every frame, not just to the primary Deck/Mixer/audio hot paths — the allocation fixes here close that gap before it could compound with future rack unit types.
  - A macro knob that visibly moves but produces no effect is a worse live-performance trap than a cable that's cosmetic-but-honest about it; both `FeedbackProcessorUnit` and the built-in-wrapper cable jacks now document their real capability rather than implying more than they deliver.

---

## Phase 1: Master Output FX Pipeline & Mixer Sub-Tabs Architecture (`Mixer.kt`, `Renderer.kt`, `PresetModels.kt`, `PresetManager.kt`, `ParametersState.kt`, `ParametersTabs.kt`, `ParametersPanel.kt`)

- **Decision**: Added a 4-slot serial ISF FX chain to the Master Output, introduced sub-tabs (`[ CTRL ]`, `[ TRANS ]`, `[ FX ]`) to the Mixer section in `ParametersTabs`, and refactored `SessionStateDto` (version 6) with a dedicated `MixerDto`:
  - **Serial Master FX Pipeline**: Added `masterFxSlots` (4 ISF slots) and intermediate FBOs (`masterCompositeFBO` and `masterFxFBOs`) to `Mixer.kt`. Refactored `Renderer.renderMixer` into a 3-pass pipeline (Pass 1: Transition -> `blendFBO`, Pass 2: Composite -> `masterCompositeFBO`, Pass 3: Serial Master FX -> `masterFxFBOs` -> target `masterFBO`).
  - **Modulation Grid Integration**: Parameter paths for Master FX slots are registered under `Mixer/FX1` .. `Mixer/FX4`, matching Deck FX paths so accordion keys and CV matrix rows align cleanly.
  - **Mixer Sub-Tabs**: Added sub-tab bar `[ CTRL ] [ TRANS ] [ FX ]` under the `MIX` tab in the Parameters panel:
    - `CTRL`: Master Alpha, Crossfade, Fade Speed, Bloom, Channel Levels, Queue & Clock triggers, and Morph triggers.
    - `TRANS`: Transition shader selector button (`MIXER_TRANSITION`), bypass checkbox, dry/wet slider, and dynamic transition parameter rows on the CV modulation grid.
    - `FX`: Master FX chain options (Save/Copy/Paste/Clear), 4 slot expand/collapse chevrons, shader selectors, bypass checkboxes, slot presets (`.lsdfx`), and dry/wet / parameter modulation rows on the grid.
  - **Clean Serialization Schema (`MixerDto` & `SessionStateDto` v6)**: Consolidated all mixer properties, levels, transition slot, and master FX slots into `MixerDto`. Bumped `SessionStateDto` version to 6 with automatic graceful fallback on legacy session deserialization.
- **Rationale**:
  - Provides VJs with full master-bus effect processing (e.g. master bloom, color grade, glitch, or post-composite filters) across the composited output.
  - Replaces monolithic Mixer parameter lists with structured sub-tabs while maintaining 100% modulation grid capability across transition and master FX parameters.

---

## Lucide Icon PUA Codepoints & Dear ImGui 1.92 Font & Popup Scoping (`Icons.kt`, `DeckControlPanel.kt`, `PlaylistEditorPanel.kt`, `ModulatorHeaderRow.kt`, `Lfo2Section.kt`)

- **Decision**: Aligned all `Icons.*` constants in `Icons.kt` with the verified Fontello Private Use Area (PUA) glyph mapping from `src/main/resources/fonts/lucide.ttf`, resolved popup modal scoping across ImGui child windows, and fixed inverted button arguments:
  - **Codepoint Verification**: Replaced drift codepoints with exact glyph matches extracted via `fontTools`:
    - `Icons.LOCK`: Updated from `\ue10a` (loader-2 circle) to `\ue10b` (lock padlock).
    - `Icons.CHEVRON_DOWN`: Updated from `\ue06c` (check mark) to `\ue06d` (chevron-down).
    - `Icons.CHEVRON_UP`: Updated from `\ue071` (chevrons-down) to `\ue070` (chevron-up).
    - `Icons.DOWNLOAD`: Updated from `\ue0af` (disc) to `\ue0b2` (download), preserving `Icons.DISC` as `\ue0af`.
    - `Icons.NOTE`: Updated from `\ue132` (percent) to `\ue1f9` (pencil).
    - `Icons.ALIGN_CENTER_LINE`: Updated from `\ue5cf` (proportions) to `\ue43b` (fold-horizontal).
    - `Icons.DICES`: Updated from `\ue28b` (dice-5) to `\ue2c5` (dices).
    - Added missing semantic constants: `POWER_OFF = "\ue209"`, `VOLUME = "\ue1a9"`, `VOLUME_X = "\ue1ac"`, `FILE_PLUS = "\ue0c9"`, `FOLDER_PLUS = "\ue0d9"`.
  - **Modal Scoping Across ImGui Child Windows**:
    - In Dear ImGui 1.92, child window popups cannot open parent-window modals (`NewPlaylistPopup`) via direct `ImGui.openPopup()` calls inside child scopes without root context mismatch.
    - Added `BrowserPopupHandler.pendingOpenNewPlaylistPopup` flag, consumed in `LibraryPanel.kt` at the root window scope alongside rename and delete confirmation modals.
  - **Dynamic State Icons**:
    - In `ModulatorHeaderRow.kt` and `Lfo2Section.kt`, updated modulator on/bypass toggle to dynamically render `Icons.POWER` when active and `Icons.POWER_OFF` when bypassed/muted.
  - **Button Argument Order**:
    - In `DeckControlPanel.kt`, swapped inverted `id` and `icon` arguments passed to `drawIconButton()`, which previously rendered buttons with label `"##btn_Save_$tag\ue14d"`, making them completely blank.
- **Rationale**:
  - Eliminates visual glitches (wrong glyphs, checkmarks in dropdowns, blank save/eject buttons, unresponsive modal popups).
  - Guarantees 100% fidelity between UI buttons and embedded font glyphs.

---

## Standardization on Pure ISF v2.0 Shaders & Removal of Legacy Custom Sources (`library/sources/`)

- **Decision**: Removed non-ISF legacy source folders (`attractor_feedback`, `chladni`, `colors`, `gyroid`, `hyper_mesh`, `hyper_slice`, and `brick`) relying on proprietary `meta.json` + `shader.frag` manifests in favor of standard Interactive Shader Format (ISF v2.0) files (`.fs`):
  - **Single Source Architecture**: All procedural and particle generators now operate through unified ISF v2.0 `.fs` shader files with embedded JSON headers, standardized inputs, and native GLSL uniform bindings.
  - **Eliminated Proprietary Manifests**: Replaced custom `meta.json` parameter descriptors with standard ISF `INPUTS` definitions.
- **Rationale**:
  - Another major step toward full ISF v2.0 standardization and away from custom, proprietary video source wrappers.
  - Improves ecosystem portability across industry-standard VJ tools (Resolume, VDMX, ISF Editor, MadMapper) and simplifies shader authoring and live-reloading.

---

## Convert Dynamic Spiral to Pure ISF v2.0 Shader (`library/sources/dynamic_spiral/dynamic_spiral.fs`, `VisualSourceRegistry.kt`, `WebPresetSerializer.kt`)

- **Decision**: Refactored `Dynamic Spiral` from a Kotlin-backed custom visual source (`DynamicSpiral.kt` + `meta.json` + `shader.frag`) into a pure, standalone ISF v2.0 visual generator shader (`library/sources/dynamic_spiral/dynamic_spiral.fs`):
  - **ISF v2.0 Shader Standard**: Encapsulated shader metadata and all 12 parameters (`MaxPoints`, `Scale`, `Damping`, `WaveFreq`, `WaveAmp`, `Shear`, `Speed`, `DotSize`, `Glow`, `HueOffset`, `HueSweep`, `TrailDecay`) directly inside the ISF JSON comment header block.
  - **Time & Phase Integration**: Integrated `TIME * Speed` and `TIME * Speed * Shear` directly within GLSL, eliminating CPU-side phase accumulation while maintaining smooth particle motion and color sweep cycles.
  - **Codebase Simplification**: Deleted `DynamicSpiral.kt`, `meta.json`, and `shader.frag`. Replaced hardcoded class branch checks in `VisualSourceRegistry.kt` and `WebPresetSerializer.kt` with standard `ISFVisualSource` / `DynamicVisualSource` polymorphism.
  - **Cross-Platform & Web Parity**: Synchronized transpiled GLSL ES 3.0 WebGL shader (`web/shaders/dynamic_spiral.frag`) and updated sync manifest (`web/sync_manifest.json`).
- **Rationale**:
  - Removes bespoke Java/Kotlin class overhead for standard fragment-shader particle generators.
  - Makes `Dynamic Spiral` 100% portable across standard ISF hosts (Resolume, VDMX, ISF Editor, MadMapper) and hot-reloadable on file save.

---

## Restoring All 4 Distinct 3D Elevation Projection Modes (`3d_elevation.fs`, `ISFFilter.kt`, `PresetModels.kt`, `ValueParamSection.kt`)

- **Decision**: Restructure and fully expose the four distinct geometric elevation projection modes in `3d_elevation.fs` with calibrated math, enum-safe clamp ranges, and dedicated UI selection:
  - **4 Distinct Geometric Projections**:
    - **Mode 0 — Tri-Axial (3 Planes Intersecting)**: Three orthogonal planes ($XY, YZ, ZX$) intersecting through the center coordinate space.
    - **Mode 1 — Hex-Planar (6 Planes Intersecting)**: Six planes intersecting through the origin rotated symmetrically at 60° increments.
    - **Mode 2 — Cube Cage (6 Planes as Faces of a Cube)**: Six planes oriented outward as the six square faces of a cube with inward facing normals and distance offset (`baseOffset = 1.0`).
    - **Mode 3 — Tetrahedral (24-Chamber Kaleidoscope)**: A 24-chamber space-folding tetrahedral kaleidoscopic projection using folded ray reflections.
  - **Enum/Long ISF Clamp Bounds Derivation (`ISFFilter.kt`)**: Added `"MIN": 0, "MAX": 3` explicitly to `mode3D`, and updated `ISFFilter.kt` to inspect `input.VALUES` if `MIN` or `MAX` are omitted. This guarantees integer/enum parameters are not inadvertently clamped to `[0.0, 1.0]`.
  - **Preset Migration Calibration (`PresetModels.kt`)**: Aligned legacy preset migration so legacy modes (1: Tri-Axial, 2: Cube Cage, 3: Hex-Planar, 4: Tetrahedral) map directly to indices 0, 2, 1, 3 respectively.
  - **Dedicated UI Combo (`ValueParamSection.kt`)**: Added a clear dropdown selector with mode descriptions and live text readout when inspecting the `mode3D` parameter in the Properties panel.
  - **Isotropic Texture Coordinate Aspect Correction (`3d_elevation.fs`)**:
    - In the original hard-coded pipeline, 2D visual sources were rendered to a dedicated square FBO (`FBO(height, height)`) with aspect $1.0$, which was then bound to the 3D projection shaders.
    - In the modular ISF pipeline, the 2D visual source renders to `cleanFBO` ($1920 \times 1080$, aspect $16/9$). An isotropic circle in generator space occupies the center square region of the texture ($x \in [0.5 - 0.5/aspect, 0.5 + 0.5/aspect]$, $y \in [0, 1]$).
    - When `3d_elevation.fs` previously mapped square 3D planes ($u, v \in [-1, 1]$) directly to $[0, 1] \times [0, 1]$, the image was squished horizontally by $1 / aspect$ ($9/16$), turning circles into ovals.
    - Updated texture sampling in both planar modes ($0 \dots 2$) and kaleidoscopic mode ($3$) to scale normalized coordinates by $1 / \max(1.0, aspect)$ horizontally and $1 / \max(1.0, 1.0/aspect)$ vertically. This restores 100% isotropic geometry where circular sources remain true circles on the 3D planes and match 2D flat mode pixel-for-pixel at default orientation.
- **Rationale**:
  - The initial port had `mode3D` clamped to `[0.0, 1.0]` by the ISF parameter loader due to missing min/max bounds, hiding modes 2 and 3.
  - Restoring all 4 modes provides the complete visual palette of 3D geometric elevations originally present in the hard-coded pipeline.
  - Aspect-ratio correcting texture lookups eliminates the X-axis squish and oval distortion across non-square aspect ratios (e.g. 16:9).

---

## Consolidate 3D Elevation Under FX Tab & Streamline View Tab (`ParametersTabs.kt`, `Renderer.kt`, `DECISIONS.md`)

- **Decision**: Fully consolidate 3D Elevation filter controls under the FX tab and eliminate duplicate 3D controls and the "+ Enable 3D Projection" shortcut button from the View tab:
  - **Single Canonical Home for `3d_elevation`**: As an ISF filter, `3d_elevation` is loaded, toggled, and modulated in FX Slot 2 (or Slot 1). All 10 projection parameters (`mode3D`, `zoom`, `pitch`, `yaw`, `roll`, `separation`, `perspective`, `depthDim`, `blendMode`, `roundness`) now render exclusively within the FX tab.
  - **Streamlined View Tab**:
    - For 2D sources: Displays universal Deck `Zoom` (`deck.viewZoom`) and `Rotate Z` (`deck.viewRotateZ`), plus any generator transform parameters.
    - For native 3D sources: Displays the source's native transform parameters (`Zoom`, `Rotate X`, `Rotate Y`, `Rotate Z`, etc.), or a clean disabled indicator if the 3D source handles projection internally with no exposed uniforms.
    - Removed the confusing duplicate display of `3d_elevation` sliders and the "+ Enable 3D Projection" button from the View tab.
  - **Renderer Consistency**: Removed `has3DElevation` bypass in `Renderer.renderDeck()`, allowing `deck.viewZoom` and `deck.viewRotateZ` to reliably scale and orient 2D sources before the FX chain, while `3d_elevation`'s camera zoom and roll parameters independently control the 3D projection stage in FX Slot 2.
- **Rationale**:
  - Eliminates confusing dual-tab parameter duplication where `FX2` and `View` showed the same sliders with identical parameter paths simultaneously.
  - Keeps the View tab focused strictly on stage/camera framing and canvas transforms without magical side effects or implicit coupling to FX slots.

---

## WirePlumber Link Negotiation Safety, Playback Probe Filtering & Coordinated Teardown (`JavaSoundClient.kt`, `JackClient.kt`, `AudioEngine.kt`, `MidiJackWatchdog.kt`, `AudioEnginePanel.kt`)

- **Decision**: Harden audio client initialization, device enumeration, and teardown lifecycles across JACK and JavaSound to eliminate PipeWire/WirePlumber link negotiation races:
  - **Playback Sink Probe Suppression (`JavaSoundClient.kt`)**: Filtered out all playback-only soundcards (matching keywords `speaker`, `headphone`, `hdmi`, `output`, `sink`, `spdif`, `iec958`) in `isLikelyPlaybackOnly()` prior to calling `AudioSystem.getMixer(mixerInfo)` or querying lines. This stops ALSA from opening/closing temporary playback handles on the internal laptop speaker (`hw:0,0`), preventing WirePlumber from aborting link hooks with `proxy destroyed` and dropping analog speakers from GNOME Settings.
  - **Device List Caching (`JavaSoundClient.kt`, `AudioEngine.kt`)**: Discovered input devices are cached in memory and re-scanned only on explicit user request (`refreshInputDevices()`).
  - **JACK Mode Isolation (`AudioEngine.kt`, `AudioEnginePanel.kt`)**: When running in `JACK_ONLY` mode or when JACK is connected, device enumeration returns a virtual `"JACK System Capture"` handle and bypasses JavaSound ALSA hardware scans entirely.
  - **Coordinated Audio Teardown**:
    - `JavaSoundClient.stop()`: Stops/flushes the line, interrupts the reader thread, waits for it to cleanly exit via `thread.join(1000)`, and only then destroys the native handle `line.close()`.
    - `JackClient.stop()`: Iterates through `port.connections` and disconnects all active links via `jack.disconnect()` before deactivating and closing the client.
  - **Settling Cooldown & Redundant Restart Suppression (`AudioEngine.kt`)**: `selectDevice()` short-circuits if arguments match current state. When switching devices or backends, a 150ms settling delay is enforced between `stop()` and `startClient()` to allow WirePlumber to finish graph cleanup before the new node appears.
  - **Watchdog Reconnect Throttling (`MidiJackWatchdog.kt`)**: Reconnection attempts are capped to 3 consecutive failures before entering a paused state until manual intervention or settings toggle.
- **Rationale**:
  - Resolves laptop speaker disappearance and route fallback to HDMI in PipeWire/WirePlumber environments.
  - Protects real-time OS audio graph integrity and prevents audio thread xruns or driver faults during device transitions.

---

## 100% ISF Pipeline Migration — Deprecating Hard-Wired FX & Mixer (`Renderer.kt`, `Deck.kt`, `Mixer.kt`, `ISFFilter.kt`, `ISFTransitionRegistry.kt`, `PresetModels.kt`, `default_filters/`, `default_transitions/`)

- **Decision**: Fully eliminate hardcoded post-processing shaders, 2D-to-3D projection geometry passes, and hardwired mixer blend modes, replacing them with a 100% modular Interactive Shader Format (ISF) pipeline:
  - **Modular Feedback with Exact Math Parity (`default_filters/feedback.fs`)**:
    - Replaced hardwired `feedback.frag` with native multi-pass persistent history buffers in ISF.
    - Calibrated with exact parity for all 9 parameters (`fbDecay`, `fbGain`, `fbZoom`, `fbRotate`, `fbHueShift`, `fbBlur`, `fbChroma`, `fbMode`, `fbKaleido`).
    - Matched the exact cubic decay curve: $\text{invS} = 1.0 - \text{fbDecay}$, $\text{decayVal} = \text{invS}^3$, $\text{history.rgb} *= \text{fbGain} \times (1.0 - \text{decayVal})$, $\text{history.a} = \text{clamp}(\text{history.a} - \text{decayVal}, 0.0, 1.0)$.
    - Updated `ISFFilter.reset()` to explicitly clear persistent history FBOs to transparent black `(0, 0, 0, 0)` upon reset or preset load, eliminating ghost frames.
  - **Modular 3D Elevation (`default_filters/3d_elevation.fs`, `Renderer.kt`, `ParametersTabs.kt`, `PresetModels.kt`)**:
    - Replaced monolithic `tri_planar.vert/frag` and `tetra_kaleido.vert/frag` with raymarched ISF shaders assigned to FX Slot 2 with exact visual and mathematical parity to the original hardware pipeline.
    - **Analytic 1:1 Scale Normalized Projection**: Screen NDC coordinates span $[-1, 1]$ directly derived from `(isf_FragNormCoord - 0.5) * 2.0`. Rays are constructed from the exact inverse of the original `tri_planar` projection matrix, guaranteeing that at $z = 0$, quad height matches 2D flat mode 1:1 at `Zoom = 1.0`. Perspective cleanly transitions from orthographic parallel rays (`roView = vec3(pZero.xy, 2.5), rdView = vec3(0, 0, -1)`) to deep perspective without altering the scale of the focal plane.
    - **Dual Blend Modes (`blendMode`)**: Added `blendMode` parameter (default 1 / Additive Luminous). In Additive mode, planes accumulate luminous energy (`composite.rgb += src.rgb`, with `(1.0 + lum * 0.2)` boost) matching original `glBlendFunc(GL_ONE, GL_ONE)` behavior. In Alpha mode, fragments composite cleanly via premultiplied alpha back-to-front without squaring edge fades.
    - **Double-Transform Prevention in Renderer**: In `Renderer.renderDeck()`, when 3D elevation is active in either FX slot, the clean 2D source pass bypasses `viewZoom` and `viewRotateZ` to prevent compounding transforms.
    - **View Tab Parameter Exposing**: In `ParametersTabs.kt`, the View tab displays and controls all 10 parameters of the active 3D elevation filter directly (`3D Mode`, `Zoom`, `Rotate X`, `Rotate Y`, `Rotate Z`, `Separation`, `3D Persp`, `Depth Dim`, `Blend Mode`, `Roundness`) alongside an instant toggle button. Preset migration in `PresetModels.kt` automatically ports `viewBlendMode`.
  - **100% ISF Transitions & Bundled Blend Shaders**:
    - Replaced hardwired blend modes (`ADD`, `SCREEN`, `MULT`, `MAX`, `XFADE`) in `mixer.frag` with pure ISF transition shaders taking `startImage`, `endImage`, and `progress`.
    - Bundled high-performance ISF transitions: `linear_crossfade.fs`, `additive_blend.fs`, `screen_blend.fs`, `multiply_blend.fs`, `max_blend.fs`.
    - Standardized `Mixer.transitionFilter` to default to `"linear_crossfade"`, keeping `mixer.mode` and `mixer.transitionFilter` automatically synchronized.
  - **Preserved Deck BG Compositing & Dual-Mode Transition Shading (`mixer.frag`, `Renderer.kt`)**:
    - Maintained the architectural compositing model: $\text{Master Output} = \text{Composite}(\text{Deck BG}, \text{ISF\_Transition}(\text{Deck A}, \text{Deck B}, \text{progress}))$.
    - Streamlined `mixer.frag` to support dual-mode compositing: pure ISF composite mode (`uMode < 0`) that samples `uTex1` (`blendFBO`) scaled by crossfade-interpolated channel level faders (`mix(uLevelA, uLevelB, uProgress)`), while preserving legacy dual-texture blend modes (`uMode >= 0`) for WebGL and fallback rendering.
    - Added default initial values to all uniforms in `mixer.frag` (`uLevelA = 1.0`, `uLevelB = 1.0`, etc.) to prevent uninitialized OpenGL zero-gain states.
    - Cached `fallbackTransition` in `Renderer.kt` to ensure zero allocations on the render loop when no transition filter is active.
  - **Massive GPU Memory Footprint Reduction**:
    - Removed obsolete `rawSourceFBO`, `rawSource2DFBO`, `fb1`, and `fb2` ping-pong framebuffers from `Deck.kt`.
    - Eliminated 16 full-resolution / square FBO allocations across the 4 decks (Deck A, B, BG, PV), saving hundreds of megabytes of VRAM.
  - **Backward-Compatible Preset Migration (`PresetModels.kt`)**:
    - Presets with legacy `view3DMode` or `fbDecay` automatically instantiate `3d_elevation` in `fxSlot2` and `feedback` in `fxSlot1` during `Deck.applyDto()`.
- **Rationale**:
  - Unifies all visual manipulation under the extensible ISF ecosystem.
  - Greatly simplifies the core OpenGL render loop in `Renderer.kt`, reducing maintenance burden and bug surface.
  - Preserves 100% fidelity and feel of the cherished feedback system while making it modulatable, swappable, and order-flexible.

---

## Master Project Roadmap Consolidation (`ROADMAP.md`, `.planning/STATE.md`)

- **Decision**: Establish a single authoritative master roadmap in `ROADMAP.md` at the repository root, consolidating historical roadmaps, developer proposals (`interop_roadmap.md`, `unified_control_mapping.md`, `mandala_future_roadmap.md`, `continuous_random_morphing_proposal.md`), and TODO items:
  - Cataloged and archived completed milestones: Core Dual-Deck Engine, Suite C UI Modernization, Universal Shader & ISF Ecosystem, Stage Video Interoperability (Spout/Syphon/PipeWire), Ableton Link Synchronization, Advanced MIDI & Controller Engine, Continuous Random Morphing, Unified Window CSD, Preset Tags & Search, and Dear ImGui 1.92.
  - Formally retired legacy TODO items: `glGetDebugMessageLog` profiling (superseded by active `GLDebug.setupDebugCallback()` with `glDebugMessageCallback()`) and frame budget monitoring (active via MenuBar telemetry HUD).
  - Defined active and upcoming milestones for the path to v1.0:
    - **Milestone 1**: TouchOSC & Open Sound Control (`OscCodec`, `OscEngine`, TouchOSC layouts, bidirectional feedback).
    - **Milestone 2**: 100% ISF Pipeline Migration (Feedback FX, 2D-to-3D & Pure ISF Mixer).
    - **Milestone 3**: Unified Control Mapping & Hardware Profiles (`CommandRegistry`, `library/mappings/`).
    - **Milestone 4**: Session Scratchpad & Live Notes (`~/.liquid-lsd/scratchpad.txt`).
    - **Milestone 5**: Mandala Visual Generator v2+ Recipe Vault & Geometric Tagging.
    - **Milestone 6**: Modular Video Rack Architecture (`modular_video_rack_proposal.md`).
  - Linked `.planning/STATE.md` and cleared the long-standing blocker regarding missing roadmap tracking.
- **Rationale**:
  - Eliminates fragmentation between disconnected markdown documents and historical scratchpads.
  - Provides contributors and performers with immediate clarity on completed capabilities, active development tracks, and future architectural direction.

---

## Modernization Upgrade to Dear ImGui 1.92 (`imgui-java` 1.92.7.1) (`build.gradle.kts`, `KeyCombination.kt`, `ShortcutManager.kt`, `UIManager.kt`, `UITheme.kt`, `UIThemeStyler.kt`, `ParametersKeyboardTest.kt`)

- **Decision**: Complete Phase 2 modernization of `io.github.spair:imgui-java` from `1.86.12` to `1.92.7.1` (tracking Dear ImGui 1.92):
  - **New Key & Navigation Input API**:
    - Replaced obsolete `ImGui.getKeyIndex(ImGuiKey.*)` with direct `ImGuiKey` constants across `ParametersKeyboard.kt`, `UIManager.kt`, `PresetListPanel.kt`, `PlaylistEditorPanel.kt`, `QueueActionsPanel.kt`, and `BgQueueActionsPanel.kt`.
    - Added comprehensive `KeyCombination.glfwKeyToImGuiKey(glfwKey: Int): Int` mapping to translate GLFW keycodes to `ImGuiKey` codes in `ShortcutManager.isTriggered`. This ensures shortcuts trigger correctly through the 1.92 input pipeline without test mocking hacks.
  - **64-bit Texture Handles**:
    - Converted OpenGL texture handles passed to `ImGui.image(...)` to `Long` (`.toLong()`) in `DeckControlPanel.kt`, `MixerPanel.kt`, and `VideoExportModal.kt`.
  - **Backend Lifecycle & Font Rebuilding**:
    - Updated `imGuiGlfw.dispose()` and `imguiGl3.dispose()` to standard `shutdown()` in `UIManager.kt`.
    - Replaced removed `imguiGl3.updateFontsTexture()` with discrete `imguiGl3.destroyFontsTexture()` and `imguiGl3.createFontsTexture()` calls.
  - **Font Push & Style Modernization**:
    - Updated `ImGui.pushFont(font, 0f)` in `UITheme.kt` to satisfy the new dynamic font scaling signature.
    - Standardized `addRectFilledMultiColor` in `UIThemeStyler.kt` to 32-bit `Int` colors.
    - Removed deprecated `tabMinWidthForCloseButton` style copying.
  - **Runtime Assertion & Lifecycle Fixes**:
    - **Raw GLFW Keycode Eradication**: Fixed `UIManager.kt` (Ctrl+F / Slash), `LibraryPanel.kt` (Down arrow), and `PreferencesPanel.kt` (Escape, Backspace, and key capture loop) to use `ImGuiKey` constants and `KeyCombination.glfwKeyToImGuiKey()`, preventing `IsNamedKey(key)` assertions.
    - **Renderer Backend Frame Integration**: Added `imguiGl3.newFrame()` in `UIManager.render()` to initialize the font texture atlas and avoid renderer backend assertion crashes.
    - **Window Boundary Extension Compliance**: Added `ImGui.dummy(0f, 0f)` after cursor repositioning in `ParametersPanel.kt` and `MixerPanel.kt` to conform with Dear ImGui 1.90+ boundary extension rules.
    - **Separator Size Underflow Prevention**: Guarded `separatorSize` and `separatorTextBorderSize` with `.coerceAtLeast(1.0f)` in `UIThemeStyler.scaleStyleFromDefault()` to prevent integer truncation to `0.0f` from failing `thickness > 0.0f` inside `ImGui.separator()`.
    - **Mixer Initialization Ordering**: Moved `Mixer.init` block below `mode` property definition to avoid startup `NullPointerException` on `mode.setBaseValue()`.
  - **Unformatted Telemetry & Format String Safety**:
    - Replaced calls to `ImGui.text(...)` with `ImGui.textUnformatted(...)` across performance readouts in `MenuBar.kt` (`CPU %`, `BPM`, `DSP latency`, `FPS`, `Frame Time`, `dropped frames`), global typography helpers in `UITheme.kt` (`h1`..`code` and colored variants), tooltip utilities in `TooltipHelper.kt` (`itemTooltip`, `showTooltip`), and dynamic user string rendering in `DeckControlPanel.kt`, `MissingItemsPanel.kt`, and `PresetListPanel.kt`. Upstream `imgui-java` 1.92 passes text strings as `fmt` strings to C `vsnprintf`, causing stack memory dereferencing and corrupting UI readouts whenever strings contain literal `%` characters.
- **Rationale**:
  - Positions the desktop UI on modern Dear ImGui 1.92, unlocking dynamic font scaling, upgraded table layouts, and native multi-selection primitives.
  - Fixes stale Gradle cache locking issues, ensures keyboard shortcuts work reliably at runtime, and guarantees stability across all UI panels.

---

## ImGui Launchpad Popup Safety, Relaxed GLSL Extensions & Scoped Directory Scanning (`ParametersPanel.kt`, `VisualSourceRegistry.kt`, `DynamicVisualSource.kt`, `ISFParser.kt`, `ISFFilterRegistry.kt`, `ISFTransitionRegistry.kt`)

- **Decision**:
  - **Sanitized Visual Source & Preset Labels**:
    - Guarded `ImGui.menuItem` in `ParametersPanel.drawLaunchpad` by appending disambiguated unique IDs (`"$label##launchpad_src_${source.id}"` and `"$label##launchpad_preset_${asset.path}"`) and falling back to IDs when display names are empty or blank (`label.ifBlank { id }`).
    - Enforced `displayName = displayName.ifBlank { id }` in `DynamicVisualSource` and `VisualSourceRegistry.loadFromISFFile` to prevent empty string labels from propagating into ImGui windows, which triggers the assertion `id != window->ID`.
  - **Relaxed GLSL Type Checking via Pragmas (`ISFParser.kt`)**:
    - Injected `#extension GL_ARB_gpu_shader5 : enable` and `#extension GL_EXT_gpu_shader4 : enable` into preprocessed fragment and vertex shader headers.
    - Resolves GLSL 3.30 Core strict signed/unsigned int equality checks and bitwise operator restrictions in complex legacy ISF shaders (e.g. `Tiny Date Time Overlay.fs` and `Random Characters.fs`), raising offscreen driver compilation pass rate to 99.7% (326/327 shaders).
  - **Scoped Directory Scans & Streamlined Startup Logging**:
    - Restricted `ISFFilterRegistry.scanUserFilters` and `ISFTransitionRegistry.scanUserTransitions` to exclude generator directories (`library/sources`), and restricted `VisualSourceRegistry.scanUserSources` to exclude filter/transition directories (`library/filters`, `library/transitions`).
    - Changed compilation failure logging in registries from dumping entire multi-line Java exception stack traces (`logger.error(e)`) to single-line summaries at `logger.warn`, with full traces deferred to `logger.debug`.
- **Rationale**:
  - Empty string menu items at the root of an ImGui popup hash to `window->ID`, violating ImGui ID stack invariants and immediately crashing the JVM process.
  - Legacy ISF shaders written on macOS or WebGL frequently mix `int` and `uint` without explicit `u` suffixes or casts; enabling `GL_ARB_gpu_shader5` and `GL_EXT_gpu_shader4` allows compliant modern drivers to compile them seamlessly.
  - Eagerly compiling foreign or mismatched shaders at startup flooded the console with unnecessary stack traces, alarming users even when the app was operating normally.

---

## Multi-Type MIDI Subsystem, Soft Takeover, Relative Rotary Decoding, and MIDI Controls Manager (`MidiEngine.kt`, `MidiMappingManager.kt`, `PreferencesPanel.kt`, `UIManager.kt`, `CVRegistry.kt`)

- **Decision**: Overhaul the MIDI subsystem from CC-only polling into a high-performance, multi-type event and state architecture spanning Phases 1A, 1B, 2, and 3:
  - **Multi-Message Capture (`MidiEngine.kt`)**:
    - Expanded Java Sound input receiver to capture `ShortMessage.CONTROL_CHANGE`, `NOTE_ON`, `NOTE_OFF`, and `PITCH_BEND`.
    - Maintained zero-allocation thread-safe state via `AtomicIntegerArray` storage for 16 channels of 128 CCs, 16 channels of 128 Notes, and 16 channels of 14-bit Pitch Bend values.
    - Unified incoming event queue (`receivedEvents: ConcurrentLinkedQueue<MidiEvent>`) and a rolling 32-event thread-safe buffer (`recentEventsList`) for live input sniffing.
  - **Intelligent Signal Classification & Learn Pipeline (`UIManager.kt`, `MidiMappingManager.kt`)**:
    - During MIDI Learn, incoming streams are automatically classified into:
      - `BUTTON_NOTE` for pads/keys (defaulting to Momentary mode).
      - `CONTINUOUS_CC` for pots and faders ($0 \dots 127$).
      - `ROTARY_*` when receiving relative delta packets ($63/65$ or $1/127$).
      - `PITCH_BEND` for 14-bit center-sprung bipolar inputs.
    - Support mapping directly to Base Value sliders, Modulation Matrix cells (`midi_cc_` and `midi_note_`), and Global Performance Actions.
  - **Contextual Fine-Tuning (Continuous & Discrete Modes)**:
    - Continuous: Min/Max numerical travel clamping, Invert boolean, and an exponential Slew smoothing filter ($0 \dots 250\,\text{ms}$) to eliminate 7-bit zipper noise on shader uniforms.
    - Discrete: Trigger modes for Momentary (hold-to-activate), Latched Toggle, Step Increment, and Step Decrement.
  - **Hardware Desync & Relative Rotary Decoding (Edge-Case Handling)**:
    - *Soft Takeover (Pickup)*: Prevents parameter jumps when changing presets by delaying value updates until the physical control crosses or reaches the stored software value. The UI indicates `Awaiting Pickup` alongside live physical position telemetry.
    - *Relative Encoders*: Implemented decoding for the three dominant rotary standards: `ROTARY_BINARY_OFFSET` (64-centric), `ROTARY_SIGNED_BIT` (1-centric), and `ROTARY_TWOS_COMP` (1-centric), with configurable step size scaling.
  - **Dedicated MIDI Controls Manager (`PreferencesPanel.kt`)**:
    - Added a dedicated `Category.MIDI_CONTROLLER("MIDI Controls")` tab in Preferences.
    - Includes controller hardware status, plug-and-play rescan, profile management (create/save/delete/switch), live packet sniffer table, global action learn buttons, and a filterable, inline-editable parameter mappings table.
  - **Backwards Compatibility**:
    - Default arguments on `MidiControlMapping` ensure full backward compatibility with legacy `library/midi/*.json` files.
- **Rationale**:
  - Live visual performance demands instant, jitter-free physical control without jarring parameter jumps when switching presets.
  - Endless rotary encoders and performance pads previously could not be utilized properly due to CC-only absolute assumptions.
  - Relocating MIDI from the cramped Audio Hardware section to a dedicated Preferences tab provides the visual real-estate needed for real-time packet inspection and deep mapping customization.

---

## Transition from Settings to Preferences (`AppPreferences.kt`, `BroadcastPreferences.kt`, `PreferencesPanel.kt`, `UITheme.kt`, `MenuBar.kt`, `lsd-preferences.properties`)

- **Decision**: Standardize all user-facing configuration, persistence layers, modal dialogs, and internal models from "Settings" to "Preferences":
  - **User-Facing UI & Shortcuts**:
    - Renamed top menu bar entry from `Settings...` to `Preferences...` under `File` and header telemetry quick-launchers.
    - Added global shortcut `Ctrl+P` / `Cmd+P` (`global.preferences`) to immediately open the Preferences modal.
    - Updated modal title to `"Preferences"` (`"Preferences##modal"`), internal child window IDs, and tooltips.
    - Added `Icons.PREFERENCES` in `Icons.kt` (aliased to Lucide slider settings icon).
  - **Data Models & State Storage**:
    - Renamed data model `AppSettings` to `AppPreferences`, with `typealias AppSettings = AppPreferences` for backward compatibility.
    - Renamed `BroadcastSettings` to `BroadcastPreferences`, with `typealias BroadcastSettings = BroadcastPreferences`.
    - Renamed `SettingsPanel` to `PreferencesPanel`, with `typealias SettingsPanel = PreferencesPanel`.

---

## Automated Screen Capture, Startup CLI Options, and Isolated UI Lab Sandbox (`CliArgs.kt`, `ScreenshotCapture.kt`, `UiLabPanel.kt`, `Main.kt`, `UIManager.kt`, `build.gradle.kts`)

- **Decision**: Implement startup argument parsing, single-frame automated framebuffer PNG capture, an isolated UI Lab component sandbox, and Gradle verification tasks:
  - **Startup Argument Parsing (`CliArgs.kt`)**:
    - Introduced `CliArgs` data class and parser supporting `--screenshot-ui=<file.png>`, `--screenshot-after-frames=<N>` (default: 5), `--window=<W>x<H>|maximized`, `--no-audio`, `--ui-lab`, `--help`, and `--version`.
  - **Framebuffer Capture & Auto-Exit Pipeline (`ScreenshotCapture.kt`, `Main.kt`)**:
    - Synchronous/FBO PNG export utility using `glReadPixels` and scanline vertical flipping with STB Image (`stbi_write_png`).
    - Render loop monitors frame settle count ($N$ frames); when `--screenshot-ui` is provided and frame count matches, it captures the framebuffer PNG and signals graceful exit (`glfwSetWindowShouldClose(window, true)`).
  - **Isolated UI Lab Component Gallery (`UiLabPanel.kt`, `UIManager.kt`)**:
    - Sandbox environment rendering theme color swatches, Lucide icons catalog, wave shape icon selectors, custom range sliders, beat division selectors, and status meters.
    - Launched via `--ui-lab` flag, bypassing live audio hardware and shader compilation passes for lightweight UI design iteration.
  - **Gradle Verification & Asset Build Automation (`build.gradle.kts`)**:
    - Registered `./gradlew captureResponsiveApp` (1080p workspace capture) and `./gradlew captureUiLab` (720p UI Lab sandbox capture).
- **Rationale**:
  - Provides deterministic, automated screenshot generation for user guides and documentation without manual intervention.
  - Enables headless visual regression testing on Linux CI runners via `xvfb`.
  - Allows rapid theme and UI control development without requiring live audio drivers or heavy GLSL shader pipelines.

    - Renamed modal sizing state variables `settingsWidth`/`settingsHeight` to `preferencesWidth`/`preferencesHeight` in `UITheme`.
  - **Configuration File Migration & Fallback**:
    - Primary configuration file is now `lsd-preferences.properties`.
    - Seamless migration: `UITheme.loadPreferences()` and `BroadcastPreferences.loadPreferences()` automatically inspect `lsd-preferences.properties` first; if absent, they fall back to legacy `lsd-settings.properties`.
    - When preferences are saved via `savePreferences()`, they are written to `lsd-preferences.properties`.
    - `saveSettings()` and `loadSettings()` remain as deprecated aliases to prevent breaking any legacy call sites.
  - **Test Suites**:
    - Replaced `SettingsDefaultsTest`, `BroadcastSettingsTest`, and `AudioEngineSettingsTest` with `PreferencesDefaultsTest`, `BroadcastPreferencesTest`, and `AudioEnginePreferencesTest`. Added test coverage explicitly verifying roundtrip persistence to `lsd-preferences.properties` and transparent fallback from `lsd-settings.properties`.
- **Rationale**:
  - In modern desktop GUI conventions (macOS, GNOME, IntelliJ, VS Code, Ableton Live), application-wide user configuration is consistently termed "Preferences" (with shortcut `Cmd+,` / `Ctrl+,`), whereas "Settings" is typically reserved for system-level controls, project-specific properties, or build tools (e.g. `settings.gradle.kts`).
  - Transitioning cleanly both in UI and under the hood prevents confusing dissonance where UI labels say "Preferences" while code and config files say "Settings", while backward-compatible typealiases and fallback loading guarantee existing user setups are preserved without disruption.

---

## Universal Shader Pipeline & Compatibility Bridge (`ISFParser.kt`, `ISFVisualSource.kt`, `Renderer.kt`, `AudioTexture.kt`)

- **Decision**: Expand shader support from strictly formatted ISF files to a universal ingestion and uniform bridge pipeline supporting **ISF**, **Shadertoy**, and **The Book of Shaders / GLSLSandbox**:
  - **Universal Format Detection & Normalization**:
    - Scans for `/*{ ... }*/` (ISF), `void mainImage(...)` (Shadertoy), or `void main(...)` (GLSLSandbox).
    - For Shadertoy shaders, automatically appends a bridge shim `void main() { mainImage(isf_FragColor, gl_FragCoord.xy); }` and aliases output colors.
    - Synthesizes fallback `ISFHeader` instances with appropriate categories so Shadertoy and Sandbox shaders catalog seamlessly into the Visual Source library.
  - **Legacy GLSL 1.20 Core 3.30 Polyfills**:
    - Injects `#define texture2D texture`, `#define textureCube texture`, `#define texture2DRect(s, c) texture(s, (c) / RENDERSIZE)`, and `#define gl_FragColor isf_FragColor`.
  - **Complete ISF Macro Definitions**:
    - Injects `IMG_THIS_PIXEL`, `IMG_THIS_NORM_PIXEL`, and `IMG_SIZE` alongside `IMG_NORM_PIXEL` and `IMG_PIXEL`.
  - **Unified Uniform Bridge (Host Dispatcher)**:
    - Pre-injects and binds unified uniform sets on every frame in `Renderer.kt`:
      - Resolution: `RENDERSIZE`, `iResolution` (vec3), `u_resolution`, `resolution`
      - Clocks & Time: `TIME`, `iTime`, `u_time`, `time`, `TIMEDELTA`, `iTimeDelta`, `u_delta`
      - Frame & FrameRate: `FRAMEINDEX`, `iFrame`, `u_frame`, `iFrameRate`
      - Date: `DATE`, `iDate` (year, month, day, seconds since midnight)
      - Interaction: `iMouse` (vec4 with click coordinates), `u_mouse` / `mouse` (normalized vec2)
      - Real-time Audio: `audioVolume`, `audioBass`, `audioMid`, `audioTreble` from `CVRegistry`
  - **Live Audio FFT Texture (`AudioTexture.kt`)**:
    - Allocates a dedicated `512 x 2` floating-point OpenGL texture (`GL_R32F`).
    - Row 0 (`y = 0.25`): 512 normalized frequency spectrum bins (FFT) from `BeatTrackerEngine.magSpectrum`.
    - Row 1 (`y = 0.75`): 512 normalized waveform samples from live audio stream (`AudioEngine.mixedBuffer`).
    - Bound to `audioFFT` and `iChannel0` (texture unit 5) on Thread 0 with zero audio callback thread overhead.
  - **Multi-Pass Visual Generators (`ISFVisualSource.kt`)**:
    - Extends multi-pass ping-pong FBO execution to visual generators, matching the capabilities of `ISFFilter.kt`.
    - Supports pass target textures, dimension expressions (`$WIDTH/2.0`, `$HEIGHT/2.0`), persistent ping-pong history FBOs, and 32-bit floating point passes (`FLOAT: true`).
- **Rationale**:
  - Live VJ and visual artists draw heavily from Shadertoy, GLSLSandbox, and Book of Shaders alongside ISF. Requiring manual code conversion was a significant barrier to entry.
  - Providing an automatic injection and normalization pipeline allows foreign shaders to run out of the box with zero manual editing while reacting directly to the audio engine and user interaction.

---

## Contextual In-Properties MIDI Learn (`PropertiesPanel.kt`, `MidiModulatorSection.kt`, `ParametersState.kt`)

- **Decision**: Replace the global modal `MIDI Map` toggle in the main menu bar with targeted, inline MIDI Learn controls located directly inside the **Properties** panel (`PropertiesPanel.kt`, `MidiModulatorSection.kt`):
  - **Removal of Global Learn Mode**: Removed the `MIDI Map` toggle button from `MenuBar.kt` and `isMidiLearnMode` global boolean state from `ParametersState.kt`.
  - **Inline `[ Learn MIDI ]` Controls**:
    - Unbound MIDI cells in the Properties panel feature a prominent `[ Learn MIDI ]` button alongside instructional text.
    - When clicked, the button updates locally to `[ ⏳ Waiting for MIDI CC... (Click to Cancel) ]` with an Orchid active tint.
    - Active learn mode automatically disengages after 15 seconds of inactivity or upon selecting another cell/tab.
  - **In-Context Re-Learn & Unbind**: Mapped MIDI parameters in `MidiModulatorSection.kt` render `[ Re-Learn MIDI ]` and `[ Unbind MIDI ]` controls directly above DC Offset and Depth sliders.
  - **Focused Target Highlighting**: The active learning parameter cell in the Parameters matrix displays a glowing cyan border outline while listening for incoming MIDI CCs.
- **Rationale**:
  - Global modal toggles induce severe "mode errors", where users forget that Learn Mode is enabled and inadvertently rebind controls when tweaking hardware later.
  - Locating Learn Mode directly within the Properties panel streamlines the mapping workflow to 2 local clicks, provides clear state feedback, and keeps the top menu bar clean.

---

## First-Class External Video Feeds in Universal Shader Picker (`ShaderPickerPopup.kt`, `ParametersTabs.kt`, `ExternalVideoSource.kt`)

- **Decision**: Promote external video feeds (PipeWire, Spout2, Syphon) to first-class visual sources within the Universal Shader Picker, eliminating conditional UI dropdowns in parameter tabs:
  - **Direct Stream Selection in Picker**: Dynamic external video streams discovered via `ExternalVideoDiscovery` are presented directly in `ShaderPickerPopup` alongside procedural generators under an `External Sources` category pill positioned immediately beside `All`.
  - **Live Visual Distinction**: External video rows are styled with an emerald green text accent and the Lucide live activity icon (`Icons.ACTIVITY`), clearly distinguishing live hardware/software inputs from compiled GLSL shaders.
  - **Dynamic Deck Header Display**: Selecting a stream dynamically updates the deck header source button to show the feed name (e.g. `[OBS-Camera ▾]`) via dynamic `ExternalVideoSource.displayName`.
  - **Elimination of Conditional Parameter Controls**: Removed the conditional "Server" combo dropdown that previously appeared inside the Parameters `SRC` tab only for `ExternalVideoSource`, establishing a permanent, non-shifting layout where only parameter sliders (e.g. `Gain`) are rendered.
  - **CV Routing Path Stability**: Canonical parameter modulation routing paths remain stable (`Deck A/External Video/Gain`) regardless of stream name or server reconnections.
- **Rationale**:
  - The previous workflow required a disjointed two-step selection process (pick generic "External Video" in modal, then look for a conditional dropdown in parameter tabs) that induced noticeable layout shift.
  - Treating live external streams identically to procedural shaders unifies the mental model and provides immediate situational awareness for live VJ performances.

---

## Suite C UI Panel Architecture Migration (`Parameters`, `Properties`, `Mixer`, `Library`)

- **Decision**: Migrate all four primary workspace UI panels to the intuitive "Suite C" naming conventions across three phased refactors with complete zero-trace codebase migration:
  - **Phase 1: Mixer** (`Mixer / Monitor` → `Mixer`): Renamed `MixerMonitorPanel.kt` to `MixerPanel.kt` and `MixerMonitorLayout.kt` to `MixerLayout.kt`. UI window title updated to `ImGui.begin("Mixer")`.
  - **Phase 2: Properties** (`Cell Config` → `Properties`): Renamed `CellConfigPanel.kt` to `PropertiesPanel.kt`. UI window title updated to `ImGui.begin("Properties")`.
  - **Phase 3: Parameters** (`Preset Grid` → `Parameters`):
    - `PresetGridPanel.kt` → `ParametersPanel.kt` (object `ParametersPanel`, window title `ImGui.begin("Parameters")`)
    - `PresetGridState.kt` → `ParametersState.kt` (`class ParametersState`, `ParameterCellId`, `ParametersUndoSnapshot`)
    - `PresetGridRenderer.kt` → `ParametersRenderer.kt`
    - `PresetGridTabs.kt` → `ParametersTabs.kt`
    - `PresetGridKeyboard.kt` → `ParametersKeyboard.kt`
    - `PresetGridUndo.kt` → `ParametersUndo.kt`
    - `PresetGridKeyboardTest.kt` → `ParametersKeyboardTest.kt`
    - `PresetGridClipboardTest.kt` → `ParametersClipboardTest.kt`
- **Rationale**:
  - The previous nomenclature ("Preset Grid", "Cell Config", "Mixer / Monitor") caused confusion for new users and cognitive overhead for developers. "Preset Grid" misleadingly implied a grid of saved presets rather than a parameter matrix; "Cell Config" sounded like low-level spreadsheet settings; "Mixer / Monitor" was redundant.
  - "Suite C" adopts established conventions from modern modular synthesizers, DAW environments (Ableton Live, Bitwig), and professional VJ software (Resolume Arena, TouchDesigner):
    - **Parameters**: The complete controllable parameter surface and modulation routing matrix.
    - **Properties**: The inspector/editor for the selected parameter, oscillator, or modulation source.
    - **Mixer**: Deck monitors, crossfader, faders, and master preview.
    - **Library**: Presets, playlists, and playback queues.
  - A zero-trace policy was strictly enforced across all three phases so no deprecated aliases, obsolete comments, or legacy naming remained in the codebase.

---

## Coordinate-Space 2D View Transformation Architecture (`blit.vert`, `mandala/shader.vert`, `Renderer.kt`, `Shader.kt`)

- **Decision**: Perform 2D View scaling (`Zoom`) and in-plane roll (`Rotate Z`) directly in coordinate space during visual source generation, rather than blitting an intermediate 16:9 texture card via `view2d.frag`:
  - **Vertex-Space Coordinate Transformation (`blit.vert`)**: Injected `uZoom`, `uRotateZ`, and `uAspectRatio` into `blit.vert` (and `mandala/shader.vert`), centering transformations at `(0.5, 0.5)` with isotropic aspect-ratio compensation.
  - **Full-Screen Continuous Evaluation**: For infinite procedural patterns (such as ISF shaders like "Brick Pattern", fractal noise, plasma), zooming out evaluates mathematical equations over a broader coordinate domain, filling the entire display with more pattern elements without rectangular boundaries or black letterboxing. For finite centered sources (e.g. Mandala, particles), scaling down leaves transparent black space around the object, allowing downstream feedback trails and spatial effects to radiate outward unimpeded across the full screen.
  - **No Tiling or Border Cards**: Replaced post-process texture quad blitting and discarded mirror/repeat tiling heuristics, preventing visible rotation corners or artificial quad edges.
  - **Feedback Shader Uniform Disambiguation (`uFbZoom`)**: Renamed feedback zoom in `feedback.frag` from `uZoom` to `uFbZoom`. Because `feedbackShader` links with `shaders/blit.vert` (which declares `uZoom` for 2D view transformations), passing `deck.fbZoom` into `uZoom` caused `blit.vert` to interpret small positive feedback zoom values (e.g. 0.001) as camera zoom, immediately shrinking the quad coordinates 1000× to a pinprick. Disambiguating the uniform to `uFbZoom` and explicitly initializing `feedbackShader`, `mixerShader`, and `blitShader` with identity vertex uniforms (`uZoom = 1.0f`, `uRotateZ = 0.0f`) guarantees post-processing passes remain completely isolated from source view transformations.
- **Rationale**:
  - Blitting an already-rendered 16:9 frame (`rawSource2DFBO` -> `view2d.frag`) treats every procedural graphic like a flat rectangular photograph, creating an isolated floating box when zoomed out and spinning rectangular corners when rotated.
  - Applying transforms directly in vertex space evaluates shaders natively across the entire screen canvas at native resolution.

---

## ARM64 Linux Native Build Externalized to a Dedicated Repo (`.github/workflows/*`, `docs/developer/build_arm64_linux.md`)

- **Context**: The initial ARM64 Linux restoration (below) compiled `libimgui-java64.so` in-repo on every CI run via a `build-imgui-arm64-native` job. That job never actually succeeded across 6 iterations of debugging (progressively more elaborate `g++`/`gcc` argument-stripping shim scripts), and even once fixed, compiling native C++ on every one of Liquid LSD's several-times-a-day releases is wasted CI time for a dependency (`imgui-java`) that changes far less often.
- **Decision**: Moved the ARM64 native build out of this repo entirely into
  [`greenjon/imgui-java-natives-linux-arm64`](https://github.com/greenjon/imgui-java-natives-linux-arm64), a small public repo that builds `libimgui-java64.so` once per `imgui-java` version and publishes it as a GitHub Release asset. Liquid LSD's CI now just `curl`s the asset matching the pinned `imguiVersion` (`build.gradle.kts`) into `src/main/resources/natives/linux-arm64/` — no compiler toolchain needed in this repo's CI at all.
- **Root cause found while debugging the in-repo attempts**: `imgui-java`'s own `GenerateLibs.groovy` always constructs its Linux `BuildTarget` via `newDefaultTarget(Os.Linux, Bitness._64)`, which defaults to `Architecture.x86` and bakes in x86-only compiler flags (`-mfpmath=sse -msse -m64`) — rejected outright by a native aarch64 `g++`, regardless of the runner itself being ARM64. `gdx-jnigen` 2.5.2 (the library `imgui-java` builds on) already ships a working `Architecture.ARM` Linux target (`aarch64-linux-gnu-` prefix, `-fPIC`, no SSE flags); `imgui-java`'s script just never selects it. The natives repo's workflow patches this in via `sed` against the checked-out `imgui-java` source and pins the resulting file/folder naming back to what the rest of that script hardcodes downstream.
- **Also found and fixed in the same pass**: `.github/workflows/release.yml` had `permissions: workflows: write`, which isn't a valid GitHub Actions permission scope (that's a PAT/OAuth-only concept). This made every run of the release workflow fail schema validation instantly — before any job could start — independent of the ARM64 issue. `contents: write` already covers the tag push this workflow performs.
- **Verified**: a full production `release.yml` run (triggered by these fixes landing on `main`) passed end-to-end across all 5 platforms, including the `linux-arm64` smoke test, and published a real release.

---

## Platform Target Restoration: Linux ARM64 (`aarch64`) (`build.gradle.kts`, `.github/workflows/*`, `NativeLibraryLoader.kt`)

- **Decision**: Restored Linux ARM64 (`aarch64`) as a first-class supported build and release target.
- **Implementation**:
  - **Native GitHub Actions ARM Runner Build Strategy**: Documented and implemented GitHub Actions workflow for building native `libimgui-java64.so` binaries on native `ubuntu-24.04-arm` runners (`docs/developer/build_arm64_linux.md`).
  - **Embedded JNI Resource Loader (`NativeLibraryLoader.prepareImGuiNatives()`)**: Added dynamic loader hook that extracts embedded `libimgui-java64.so` from `/natives/linux-arm64/` resources to a temporary runtime folder and configures `System.setProperty("imgui.library.path", ...)` before ImGui context initialization.
  - **Adoptium JRE 17 `linux-aarch64` Distribution Packaging**: Restored `zipLinuxArm` Gradle task in `build.gradle.kts` with `run-linux-arm.sh` launcher script and Adoptium `linux-aarch64` JRE 17 bundling.
  - **5-Platform CI Smoke Testing Matrix**: Updated `.github/workflows/smoke-test.yml` and `release.yml` to test all 5 target platform distributions (`windows-x64`, `linux-x64`, `linux-arm64`, `macos-x64`, `macos-arm64`).

---

## Platform Target: Linux ARM64 Dropped (Archived / Superseded)

- **Decision**: Linux ARM64 (aarch64) was temporarily dropped on 2026-09-10 due to missing upstream Maven binaries and restored via native GitHub Actions compilation and runtime JNI loader hooks.
- **Reason**: Upstream `io.github.spair:imgui-java` does not publish an ARM64 Linux native binary (`libimgui-java64.so`), and upstream issue [#105](https://github.com/SpaiR/imgui-java/issues/105) remains open. A roadmap milestone and step-by-step restoration guide using GitHub Actions native ARM runners is documented in [`docs/developer/build_arm64_linux.md`](docs/developer/build_arm64_linux.md).
- **Remaining targets**: Linux x64, macOS x64, macOS ARM64 (Apple Silicon), Windows x64.
- **Note on ARM64 macOS**: macOS ARM64 (Apple Silicon) remains fully supported. The `NSRect`/`NSSize` JNA `Structure` field-type fix (`Double` instead of `Float`) introduced in the beta 57–62 audit specifically targets ARM64 macOS correctness and must be preserved.
- **Impact**: Audit finding #16 ("PipeWire struct offsets wrong on ARM64 Linux") is **closed as N/A** — PipeWire is Linux-only and ARM64 Linux is no longer a target. Raw byte-offset struct access in `PipeWireLibrary.kt` only needs to be correct for x86_64 Linux.

## Thread 0 OpenGL Discipline & Zero-Allocation Hot-Path MIDI/CV Routing (`VisualSourceRegistry.kt`, `Main.kt`, `MidiMappingManager.kt`, `ParameterResolver.kt`, `CVRegistry.kt`, `Evaluators.kt`)

- **Decision**: Strictly enforce OS Thread 0 execution for all OpenGL shader compilation, eliminate off-thread GPU calls in `VisualSourceRegistry`, and eliminate per-frame allocations across MIDI CC mapping and CV evaluation:
  - **Thread 0 GL Task Queue in `VisualSourceRegistry`**: Replaced direct off-thread `Shader(...)` calls during asynchronous background directory scanning with `pendingGlTasks: ConcurrentLinkedQueue<() -> Unit>` dispatched via `VisualSourceRegistry.processPendingGlTasks()`, executed exclusively on OS Thread 0 inside `Main.kt` render loop. Added `loadAll(async = false)` synchronous startup mode so shaders compile deterministically on Thread 0 before the render loop begins.
  - **Flat Array MIDI CC Resolution (`MidiMappingManager`)**: Pre-resolves parameter paths (`mixer.deckA.color.hue`, etc.) into a flat, unboxed `ResolvedMidiBinding` array whenever mappings are altered or deck sources/presets change. In `MidiMappingManager.update(mixer)`, the hot loop iterates via indexed bounds (`0 until size`) with zero string allocations, zero map lookups, and zero iterator creation per frame.
  - **Concurrent Path Caching in `ParameterResolver`**: Added thread-safe `ConcurrentHashMap` memoization (`pathCache`) to `ParameterResolver.resolve()`, avoiding recursive tree traversals across decks and effect parameters. Invalidation hooks wired into `Deck.source` and `PresetManager`.
  - **Indexed Active CV Sources & Long-Packed MIDI CC Keys (`CVRegistry.kt`)**: Replaced map value iteration in `CVRegistry.updateAll()` with a flat `activeNonAudioSources: Array<ActiveSourceEntry>` array traversed by index loop. Replaced `substring().split('_')` string parsing on incoming MIDI CC identifiers with a 64-bit primitive bit-packed cache key `(channel.toLong() shl 32) or cc.toLong()`.
  - **Zero-Allocation Audio Follower State Guard (`Evaluators.kt`)**: Added explicit lookup guard `states[id]` before calling `computeIfAbsent` in `AudioFollowerTracker.process`, preventing Kotlin capturing lambda instantiations on the real-time audio evaluation path.
- **Rationale**:
  - Eliminates GLFW/OpenGL driver crashes and debug context faults caused by off-thread shader compilation on Linux X11/Wayland.
  - Guarantees true zero-allocation execution during 60–120 FPS render loops, preventing JVM GC pauses from interrupting real-time VJ performance.

---

## Scoped Automated Release Notes Extraction for Continuous Releases (`.github/workflows/release.yml`, `docs/release_notes.md`, `RELEASE_NOTES.md`)

- **Decision**: Update automated release notes extraction in `.github/workflows/release.yml` from greedy multi-version regex captures to a deterministic 3-tier scoping strategy:
  1. **Milestone Version Match (Tier 1)**: If an explicit milestone header matching the target version exists (e.g. `## Version 0.9.1`), extract that exact block up to the next version boundary.
  2. **Git Diff Extraction between Releases (Tier 2)**: When no milestone version header exists and a previous tag (`prev_tag`) is present, execute `git diff $prev_tag..HEAD -- $fpath` against candidate release notes files. Extract strictly the added lines (`+` lines, omitting diff markers and unreleased headers). If the diff consists of raw bullet items without an enclosing `### ` subsection header, automatically extract and prepend the active `### ` subsection header from the candidate file.
  3. **Topmost Section Fallback (Tier 3)**: If `prev_tag` is unavailable (e.g., initial repository push or shallow clones without tag history), scope extraction strictly to the first/topmost `### ` subsection under `## [Unreleased]` rather than capturing the entire unreleased block.
  - **Release Notes Consolidation**: Cleanly relocate released milestone features (e.g. Phase 1–4 Ableton Link and PipeWire 0.3 video ingest) under explicit version headers (`## Version 0.9.1`) so that `## [Unreleased]` contains only pending changes.
- **Rationale**:
  - In continuous-push continuous-delivery models with auto-incrementing patch versions (`v0.9.1`, `v0.9.2`, ...), CI workflows do not commit version bumps back to `main` to prevent infinite workflow loops.
  - The previous regex greedily captured the entire `## [Unreleased]` section across all historical releases, causing newly published GitHub Releases to monotonically grow longer and accumulate obsolete release notes.
  - Scoping extraction via `git diff $prev_tag..HEAD` guarantees that each published release note contains exclusively the changes introduced since the previous release tag, eliminating manual post-release edits.

---

## Zero-Copy Linux Video Sharing & Live Video Ingest via PipeWire 0.3 (`PipeWireLibrary.kt`, `PipeWireBridge.kt`, `TextureReceiver.kt`, `ExternalVideoDiscovery.kt`, `ExternalVideoSource.kt`, `TextureStreamer.kt`, `SettingsPanel.kt`)

- **Decision**: Implement zero-copy GPU video frame streaming and live video ingest on Linux using **PipeWire 0.3** (`libpipewire-0.3.so`) with DMA-BUF GPU export, shared-memory (`SPA_DATA_MemFd`) fallback, and `PipeWireReceiverImpl` ingestion:
  - **JNA Native PipeWire Bindings**: Implemented `PipeWireLibrary.kt` to bind `libpipewire-0.3.so.0` / `libpipewire-0.3.so` functions (`pw_init`, `pw_thread_loop_*`, `pw_context_*`, `pw_stream_*`) and SPA video parameters (`SPA_VIDEO_FORMAT_RGBA`).
  - **Thread-Loop Lifecycle & Stream Management**: Implemented `PipeWireBridge.kt` to handle native thread loop lifecycle, stream setup, format negotiation, and buffer submission for video outputs.
  - **Native Linux Video Ingest (`PipeWireReceiverImpl` & `fetchPipeWireStreams`)**: Created `PipeWireReceiverImpl` in `TextureReceiver.kt` to ingest external live PipeWire video feeds into OpenGL textures, and `fetchPipeWireStreams()` in `ExternalVideoDiscovery.kt` to auto-discover active PipeWire video nodes (`pw-dump` / `pw-cli`).
  - **Zero-Stall GPU-to-Stream Readback (`LinuxTextureBridge`)**: Replaced the stubbed `LinuxTextureBridge` in `TextureStreamer.kt` with active PipeWire streaming. Uses `PboReadbackPipeline` to perform asynchronous PBO DMA readback from FBO textures without stalling the GL rendering pipeline.
  - **Graceful Fallback & Zero Hard Dependencies**: If `libpipewire-0.3.so` is absent or PipeWire is not running, `LinuxTextureBridge` cleanly degrades to `NullTextureStreamer` and `PipeWireReceiverImpl` degrades to `NullTextureReceiver`. If GPU DRM DMA-BUF export is unsupported by the display driver, it uses `SPA_DATA_MemFd` ring buffer memory.
  - **Settings UI & Telemetry Integration**: Updated `SettingsPanel.kt` to display active driver information (`PipeWire 0.3 (Linux)`, `Spout2 (Windows)`, `Syphon (macOS)`) and hover tooltips detailing live stream status.
- **Rationale**:
  - Achieves feature parity across Windows (Spout2), macOS (Syphon), and Linux (PipeWire 0.3).
  - PipeWire 0.3 is the default multimedia server on modern Linux distributions (Ubuntu 22.04+, Debian 12+, Fedora 34+, Arch).
  - Eliminates main-thread OpenGL pipeline stalls while sharing live video feeds with OBS Studio, Resolume Arena, qpwgraph, and stage projection servers.

## First-Run Clean Startup with Blank Deck Screens (`Deck.kt`, `Main.kt`, `PresetManager.kt`, `SaveLoadFixesTest.kt`)

- **Decision**: Initialize all four decks (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`) as blank/empty screens on initial application startup:
  - **Deck Default `isEmpty = true`**: Configured `Deck(..., var isEmpty: Boolean = true)` so newly instantiated decks default to an empty/blank state.
  - **Removal of Hardcoded Mandala Startup Recipes (`Main.kt`)**: Removed legacy hardcoded Fourier ratio definitions (`recipeA`, `recipeB`, `recipeBG`, `recipePV`) from `Main.kt`. Decks now cleanly initialize without pre-populating animated Mandala sources.
  - **Explicit `startEmpty(mixer)` on Missing Session (`PresetManager.loadSession`)**: When `last_session.json` is missing (first run or deleted state) or fails to load, `loadSession` explicitly triggers `startEmpty(mixer)` to ensure all decks are reset, active preset labels/DTOs are null, and the play queues are cleared.
  - **Clean Launchpad UI Workflow**: On first launch, the 4 deck monitors and master preview display blank black screens, and selecting any deck in the Preset Grid presents the Launchpad with "Add Source" and "Load Preset" buttons, providing an intuitive, distraction-free entry point for artists.
- **Rationale**:
  - Replaces legacy hardcoded initial state with a clean, intentional slate on first launch.
  - Preserves user sessions when `last_session.json` exists, while ensuring first-time users or users with empty startup settings get clean blank monitors.

## Interactive Shader Format (ISF) Implementation (`ISFVisualSource.kt`, `ISFParser.kt`, `ISFModels.kt`, `VisualSourceRegistry.kt`, `Renderer.kt`, `VisualSourceManifestTest.kt`, `Source3DModeTest.kt`)

- **Decision**: Introduce native support for the ISF specification (v2.0) to standardize visual source loading and effect processing, without requiring `meta.json`:
  - **Shader-Embedded Metadata**: Support parsing JSON headers directly from GLSL files using `/*{ ... }*/` comments.
  - **Automatic Parameter Mapping**: ISF `float`, `bool`, `long`, `color`, and `point2D` inputs are automatically converted into Liquid LSD `ModulatableParameter` instances. Complex types like `color` and `point2D` are split into individual modulatable components (e.g., `Color R`, `Color G`, etc.), with support for both scalar and vector `MIN`/`MAX` schemas.
  - **ISF GLSL Preprocessor & Uniform Injection**: Implemented `ISFParser.buildGLSLFragmentShader()` which automatically injects required GLSL 3.30 boilerplate before compilation: standard uniforms (`RENDERSIZE`, `TIME`, `TIMEDELTA`, `FRAMEINDEX`, `DATE`, `PASSINDEX`, `uAlpha`), input uniform declarations (`float`, `bool`, `vec4`, `vec2`), coordinate aliases (`isf_FragNormCoord` -> `vTexCoord`), sampler macros (`IMG_NORM_PIXEL`, `IMG_PIXEL`), and fragment output mapping (`gl_FragColor` -> `isf_FragColor`). Preexisting uniform declarations in shader bodies are automatically deduplicated to prevent GLSL redeclaration errors.
  - **Scoped Directory & Asset Registries**: `ISFFilterRegistry`, `ISFTransitionRegistry`, and `VisualSourceRegistry` restrict scans to their designated directory domains and evaluate shader characteristics (e.g. image inputs, `progress` inputs, categories) to prevent visual generator sources from being erroneously registered as filters or transitions.
  - **Flexible Discovery & Dual-Format Manifest Validation**: `VisualSourceRegistry` scans for standalone `.fs`/`.isf` files and folders containing ISF-compatible shaders, while maintaining full backward compatibility with the legacy `meta.json` format. Visual source tests (`VisualSourceManifestTest`, `Source3DModeTest`) discover and validate sources using either `meta.json` or embedded ISF headers, removing the mandatory `meta.json` restriction across the codebase.
- **Rationale**:
  - Leverages a massive ecosystem of existing high-quality shaders from the VJ community.
  - Simplifies shader development by consolidating metadata and code into a single file.
  - Eliminates the maintenance burden of separate `meta.json` sidecar files for new and imported shaders while preserving legacy source compatibility.
  - Provides a robust foundation for Phase 2.1 migration and future modular effect chains.

## Flexible ISF Directory Role Auto-Detection, Folder Hierarchy Preservation, and Relative Asset Resolution (`ISFScanner.kt`, `ISFParser.kt`, `ISFModels.kt`, `ISFTextureLoader.kt`, `VisualSourceRegistry.kt`, `ISFFilterRegistry.kt`, `ISFTransitionRegistry.kt`, `ShaderPickerPopup.kt`)

- **Decision**: Remove hardcoded folder naming requirements (`/sources`, `/filters`, `/transitions`), auto-detect shader roles purely from JSON `INPUTS` image counts, preserve subfolder structures in metadata and UI, and resolve local relative assets (`IMPORTED`):
  - **Auto-Detect Role via JSON `INPUTS`**: Instead of path-based keyword filtering (`lowerPath.contains("filter")` or directory exclusions), scanned shaders are classified by their declared image inputs count:
    - 0 image inputs: Visual Generator Source (`ISFAssetType.GENERATOR` $\to$ `VisualSourceRegistry`)
    - 1 image input: Deck FX Filter (`ISFAssetType.FILTER` $\to$ `ISFFilterRegistry`)
    - 2+ image inputs (or transition `progress` parameter): Mixer Transition (`ISFAssetType.TRANSITION` $\to$ `ISFTransitionRegistry`)
  - **Preserve Folder Hierarchies as Categories & UI Folders**: Captures the relative directory path from the scanned root in `ISFAsset.folderPath` and injects path hierarchy segments into `categories`. `ShaderPickerPopup` features a dual-mode browser:
    - Collapsible Folder Tree view (`Icons.FOLDER`): Groups shaders in expandable folder nodes matching the pack hierarchy on disk.
    - Flat List view (`Icons.LAYOUT_FULL`): Fast table view displaying folder tags in the Categories column.
    - Zero per-frame allocations during UI rendering via pre-cached grouping in `updateItems()`.
  - **Respect Relative Assets (`IMPORTED`)**: Shaders remain in their original directories during execution. Declared static assets in `IMPORTED` (supporting both JSON Object and JSON Array schemas) have their paths resolved relative to the shader file (`baseDir`), uniforms are injected into GLSL (`uniform sampler2D`), and OpenGL 2D textures are loaded on Thread 0 via `ISFTextureLoader`. Pre-bound texture units ensure zero allocation per frame.
- **Rationale**:
  - Users can point Liquid LSD to any existing shader pack or folder without reorganizing or renaming files into rigid folder silos.
  - Subfolder pack organization remains intact in the UI.
  - ISF shaders referencing external noise textures, lookup tables, and audio maps work out-of-the-box.


## Fixed 95% Global UI Scale, Removal of Grid Cell Ratio, and Dedicated Library Preset Sizing (`UITheme.kt`, `AppSettings.kt`, `SettingsPanel.kt`, `GridMetrics.kt`, `UIManager.kt`, `PresetListPanel.kt`)

- **Decision**: Permanently fix the global UI scale at 95% across all panels and controls, remove arbitrary runtime UI scaling and the non-functional `gridCellRatio`, and introduce a dedicated, bounded user control (80%–120%) exclusively for preset name sizing in the Library:
  - **Fixed 95% Semantic Typography Hierarchy**: Defined exact pixel values for all core font levels at 95% scaling:
    - Caption: 12px
    - Body: 14px
    - Code: 14px
    - H3: 15px
    - H2: 18px
    - H1: 22px
    - Baseline size: 14.25px (95% of 15px baseline)
  - **Zero Dynamic Font Scaling Overhead**: Replaced per-frame `baseSize / 15f` runtime scaling calculations across 14+ UI panels (`PresetGridPanel`, `AudioEnginePanel`, `CellConfigPanel`, `CustomRangeSlider`, `BeatDivisionSlider`, `Lfo1Section`, `Lfo2Section`, `ModulatorHeaderRow`, `OscilloscopeDrawer`, etc.) with fixed constants or precalculated metrics.
  - **Removal of Grid Knob Cell Scale (`gridCellRatio`)**: Deprecated and completely removed `gridCellRatio` from settings, persistence, and UI. Precalculated `GridMetrics` into a singleton `INSTANCE` at 95% scale (`cell = 33.25f`), eliminating per-frame heap allocations during grid rendering.
  - **Focused Library Preset Name Sizing (`presetNameScalePercent`)**: Added `presetNameScalePercent` (range 80% to 120%, default 100%, 10% step) to `AppSettings` and `UITheme`. Added `FontLevel.PRESET_NAME` which renders preset items in `PresetListPanel`, `PlaylistEditorPanel`, `QueueActionsPanel`, and `BgQueueActionsPanel` with proportional font scaling. Deck headers remain fixed at standard size. 10% steps guarantee that each step rasterizes to a distinct integer pixel size without glyph height collisions.
  - **Repurposed Zoom Shortcuts**: `Ctrl + -` and `Ctrl + =` (`Cmd + -` and `Cmd + =` on macOS, with keypad +/- support) now adjust Library preset name scale in 10% increments rather than rebuilding global UI fonts.
- **Rationale**:
  - Eliminates visual glitches, overlapping text boxes, and layout instability caused by arbitrary global scaling in immediate-mode ImGui layouts.
  - Aligns with standard audio/VJ software paradigms (e.g. Mixxx) where typography scaling is focused on high-density library lists rather than the fixed-geometry performance controls.
  - Removes unnecessary garbage collection overhead and continuous font atlas rebuilds.

## Clustered Monitor Overlays, Channel Level Faders, and Single-Row Master Controls (`DeckControlPanel.kt`, `MixerMonitorPanel.kt`, `Mixer.kt`, `Renderer.kt`, `mixer.frag`)

- **Decision**: Redesign the 4 deck monitors and main output monitor with inside-clustered overlays, physical mixer channel level faders, and streamlined single-row crossfader controls:
  - **4-Channel Console Level Faders**: Added non-modulatable 0.0–1.0 channel level multipliers (`levelA`, `levelB`, `levelBG`, `levelPV`, `masterLevel`) to `Mixer`. These scale channel output in `mixer.frag` (`uLevelA`, `uLevelB`, `uLevelBG`, `uMasterLevel`) and act as pre-crossfade channel faders (Deck A/B), background gain (BG), preview dimmer (PV), and master gain (Master).
  - **Multiplier Pattern (Avoiding Takeover Magic)**: Rather than adding complex CV takeover/muting/unmuting logic, channel faders are pure scaling multipliers (`effectiveAlpha = modulatedAlpha * channelFader`). At 1.0 (unity), CV passes through unaltered; at 0.0, the channel is completely cut.
  - **Inside-Clustered Monitor Controls**:
    - Left monitors (`Deck A`, `Deck BG`): Badges `[A]` and `[BG]` moved to top-right corner; die `[🎲]` placed to the left of the badge; vertical level fader hangs directly below the badge.
    - Right monitors (`Deck B`, `Deck PV`): Badges `[B]` and `[PV]` moved to top-left corner; die `[🎲]` placed to the right of the badge; vertical level fader hangs directly below the badge.
    - Central Spine Clustering: All 4 faders and dice are clustered along the center gutter directly beneath the crossfader, within minimal mouse travel radius.
    - Video Preview Preservation: Overlays reside directly within the video preview without shrinking or distorting the aspect ratio.
  - **Master Monitor Overlay**:
    - Bottom-right corner: Badge `[M]`, die `[🎲 ALL]` to its left, and vertical Master Level fader directly above `[M]` extending upward. Leaves top-right free for the `[REC]` tally badge.
  - **Middle-Click Reset**: Middle-clicking any fader track immediately resets its level to 100% (1.0), matching `CellConfig` and crossfader conventions while preventing accidental jumps on double-click.
  - **Single-Row Master Controls Strip**: Removed the 5-button dice row below the crossfader in `MixerMonitorPanel`, reducing `MasterControls` height to a single row (~34px) and reclaiming vertical screen real estate for preview monitors.
- **Rationale**:
  - Unifies the VJ mixing console experience with physical channel strips and crossfader.
  - Preserves deck preset boundaries: channel faders are console properties that do not transfer when copying or swapping patches.
  - Enhances spatial ergonomics during live performance.

## Title Bar to Panel Layout Spacing & Gap Standardization (`UIManager.kt`, `MenuBar.kt`, `ParametersPanel.kt`, `PropertiesPanel.kt`, `WindowLayoutSafetyTest.kt`)

- **Decision**: Standardize menu bar and panel header heights with proportional 50% height expansions and an explicit 2.0 px separator gap:
  - **Explicit 2 px Gap Constant**: Maintained `UIManager.TITLE_BAR_PANEL_GAP = 2.0f` (expanded 100% from 1.0f to 2.0f) to specify the vertical spacer gap between the top title/menu bar and the workspace panels (Parameters, Properties, Mixer). The panel starting Y position is calculated directly as `titleBarH + TITLE_BAR_PANEL_GAP`.
  - **50% Taller Main Menu Bar**: Main menu bar height is dynamically scaled to $1.5 \times$ base frame height via `MenuBar.calculateHeight(session)` and `MenuBar.calculateFramePaddingY(session)`. Menu items, recording pills, drag regions, and window controls vertically center within the taller bar.
  - **50% Taller Workspace Panel Headers & Synchronized Title Bars**: Left panel (`Parameters`) and middle panel (`Properties`) window menu bars are dynamically scaled to $1.5 \times$ base height and vertically centered via the shared `PanelTitleBar` helper (`withFramePadding`, `calculateHeight`, `calculateFramePaddingY`, and `draw`). Ensures both panels share identical optical text centering, typography, and button heights, maintaining synchronized layout styling when future adjustments are made. Column headers in Parameters and CV tab row buttons (`drawCvTabRow`) in Properties are likewise scaled to $1.5 \times$ for improved touch/click hit targets.
- **Rationale**:
  - Unifies the design system across the primary Suite C columns without code duplication or drift.
  - Eliminates hardcoded magic numbers and ensures layout intent is cleanly named and configurable.
  - Guarantees a consistent, intentional 2 px visual separation across all display resolutions and font scales without disappearing or expanding unpredictably during zoom.
  - Gives the top application chrome and workspace inspectors ample breathing room and larger, touch-friendly hitboxes.

## Unification of Modulator Engine States and Preset Grid Column Visibility (`UITheme.kt`, `SettingsPanel.kt`, `PresetGridPanel.kt`, `CellConfigPanel.kt`, `PresetDependencyAnalyzer.kt`)

- **Decision**: Unify modulation subsystem states (`audioEngineEnabled`, `midiEnabled`, `sequencerEnabled`) with their corresponding Preset Grid column visibility, and eliminate the redundant `Settings > Preset Grid` category:
  - **Single Source of Truth**: Removed duplicate, conflicting state flags (`showMidiCol`, `showSeqCol`, `showAudioCol`). A modulator column is visible if and only if its underlying subsystem is enabled (`midiEnabled`, `sequencerEnabled`, `audioEngineEnabled`). For LFO (which has no heavy external I/O or background process), `showLfoCol` remains the sole visibility toggle.
  - **Kebab Menu (`⋮`) as Fast Modulator Switchboard**: In `PresetGridPanel`, toggling the MIDI, SEQ, or AUD checkboxes directly starts or stops the respective subsystem (`session.audioEngine.start()` / `stop()`, `MidiEngine.scanForNewDevices()` / `close()`) and shows/hides the column concurrently.
  - **Streamlined Settings Categories**: Removed the redundant `Category.PRESET_GRID` tab from `SettingsPanel`. The lone grid-specific layout setting, `gridCellRatio` ("Grid Knob Cell Scale"), was relocated to `Settings > Appearance` under "Fonts & Sizing".
  - **Zero-Allocation 10-Bit Issue Cache**: Simplified `PresetDependencyAnalyzer.getIssues()` to check the 4 master subsystems and LFO column, reducing the memoization cache index from 13 bits (8192 entries) to 10 bits (1024 entries) with zero per-frame heap allocations.
- **Rationale**:
  - Eliminates contradictory states where a column could be set to "Show" while its engine was disabled, or where an enabled engine's column was hidden.
  - Simplifies the Settings UI by avoiding duplicate toggles across multiple dialogs.
  - Keeps the live VJ workflow fast and responsive by allowing complete modulator enable/disable control directly from the header kebab in the primary view.

## Non-Destructive Preset Dependency Inspection and Preset Grid Column Kebab Menu (`PresetDependencyAnalyzer.kt`, `FileSystemManager.kt`, `PresetGridPanel.kt`, `PresetListPanel.kt`, `DeckControlPanel.kt`)

- **Decision**: Introduce a proactive, non-destructive dependency inspection system and a Preset Grid header column kebab (`⋮`) to handle patches relying on disabled subsystems, offline engines, or hidden columns:
  - **Zero-Hiding Policy**: Never hide presets from the Library or block them from loading. When a preset uses features currently disabled or offline, display a single red `[!]` alert indicator alongside the preset name in the Library list and Deck monitor headers.
  - **Zero-Allocation Memoized Issue Evaluation**: `PresetDependencies.getIssues(session)` memoizes issues using an internal state cache, guaranteeing zero object allocations per frame across hundreds of presets rendered at 60 FPS.
  - **Rich Context Tooltips**: Hovering the `[!]` badge in either the Library or Deck monitor displays bulleted issue summaries detailing inactive engines (`Audio Engine Disabled`), disabled subsystems (`MIDI Disabled`, `Sequencer Disabled`, `Randomization Disabled`), or hidden columns (`LFO Column Hidden`).
  - **Preset Grid Header Column Kebab (`⋮`)**: Added a column visibility kebab menu directly to the right of the Preset Grid column headers (`VAL`, `MIDI`, `LFO`, `SEQ`, `AUD`):
    - Displays each CV column with real-time status badges: `(! Needed by patch)`.
    - Allows instant toggling of column visibility and subsystems without navigating into Settings.
    - Displays a red `[!]` badge over the kebab when the active deck utilizes columns or engines that are disabled.
    - **One-Click Quick Actions**: Provides a prominent `[ Turn On Needed Columns ]` action button that reveals all hidden columns needed by the active patch and turns on required subsystems (`midiEnabled`, `sequencerEnabled`, `randomizationEnabled`, `audioEngineEnabled`).
- **Rationale**:
  - Eliminates "silent failures" where users loaded audio-reactive or sequenced presets and wondered why they appeared static or unresponsive.
  - Preserves user layout choices by never force-unhiding columns without explicit user action, while providing a frictionless one-click affordance to restore full visual expressiveness directly from the performance view.

## Unification of Audio & Trigger Modulation into 2 Modular Audio Slots (`AudioModulatorSection.kt`, `CVRegistry.kt`, `AudioEngine.kt`, `PresetGridPanel.kt`, `PresetGridRenderer.kt`, `CellConfigPanel.kt`, `Enums.kt`, `Evaluators.kt`)

- **Decision**: Consolidate the separate `AUDIO` and `TRIGGER` modulation domains into a single unified `AUDIO` column (`AUD`) featuring **2 independent modular Audio Slots** per parameter:
  - **Single Grid Column**: Replaced the separate `AUD` and `TRIG` columns in the Preset Grid with a single `AUD` column. The Preset Grid now features 5 streamlined columns: `VAL`, `MIDI`, `LFO`, `SEQ`, `AUD`.
  - **2 Modular Audio Slots per Parameter**: Each modulatable parameter supports up to 2 independent audio modulators (`Audio 1` and `Audio 2`). In `CellConfigPanel`, Slot 1 is always accessible, and Slot 2 can be added via a clean `[ + Enable Audio Slot 2 ]` action button, collapsing automatically when inactive.
  - **Continuous (RMS) vs Transient (Spectral Flux)**: Audio slots allow instant toggling between Continuous energy tracking (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`) and Transient onset detection (`audio_flux_amp`, `audio_flux_bass`, `audio_flux_mid`, `audio_flux_high`).
  - **4-Band Frequency Selection**: Both Continuous and Transient modes operate over 4 selectable bands: `Full Mix (AMP)`, `Bass (BASS)`, `Mid (MID)`, and `High (HIGH)`.
  - **Musical Response Presets & Dynamics**: Upgraded envelope follower presets with `Instant (Raw Jitter)`, `Snap` (0ms/35ms), `Punchy` (5ms/150ms), `Smooth Swell` (40ms/400ms), `Slow Pulse` (100ms/900ms), `Ambient Drift` (250ms/1800ms), and `Custom` (user-controlled Attack $0\dots500\text{ ms}$ / Decay $10\dots3000\text{ ms}$).
  - **Zero-Allocation Real-Time Audio DSP**: Spectral flux calculations and multi-band RMS are computed directly within the real-time processing loop without heap allocations, and cached in ring buffers for instantaneous UI oscilloscope rendering.
- **Rationale**:
  - **Eliminates UI Redundancy & Fragmentation**: Previously, audio envelope and trigger/transient modulations were split across two separate grid columns and config sections with overlapping concepts. Unifying them into one modular section simplifies the matrix interface while expanding creative flexibility.
  - **Dual-Slot Expressiveness**: Musicians and visual artists can now bind both a slow ambient swell (Continuous RMS on Mid) and a snappy kick pulse (Transient Flux on Bass) to the same parameter within a single unified cell.
  - **Clean Code Architecture**: Removed legacy trigger shims and dedicated trigger UI classes, reducing architectural complexity.

## ImGui Upgrade Strategy: 1.86.12 Adoption for Apple Silicon ARM64 and 1.92.x Modernization Roadmap (`build.gradle.kts`, `docs/developer/imgui_upgrade_guide.md`, `DECISIONS.md`)

- **Decision**: Update `io.github.spair:imgui-java-*` dependencies from `1.86.11` to `1.86.12` as Phase 1 of our ImGui modernization strategy, while scheduling Dear ImGui 1.92.x as Phase 2:
  - **Phase 1 (Immediate - 1.86.12)**: Upgrades the pinned ImGui version to `1.86.12` in `build.gradle.kts`. This release is the first upstream version to package macOS native dylibs as Mach-O Universal Binaries (`x86_64` + `arm64`) using `lipo`, providing immediate, native execution on Apple Silicon Macs and unblocking `macos-arm64` binary smoke testing.
  - **Zero Breaking Changes**: Version 1.86.12 preserves 100% binary and source compatibility with 1.86.11, requiring zero code modifications across the UI layer and presenting zero risk of visual or behavioral regressions.
  - **Linux ARM64 Limitation**: Upstream `io.github.spair:imgui-java-natives-linux` does not publish ARM64 ELF binaries in any version up through 1.92.7.1 (issue #105 remains open upstream). Thus, bumping to 1.92.x does not resolve Linux ARM64, making 1.86.12 functionally identical to 1.92.x with respect to platform coverage.
  - **Phase 2 Documented Roadmap**: Documented the full migration requirements for Dear ImGui 1.92.x in `docs/developer/imgui_upgrade_guide.md`, covering 64-bit `ImTextureID` conversions, GLFW/GL3 `shutdown()` lifecycle, dynamic font texture recreation, the new `ImGuiKey` API (removal of `getKeyIndex`), `pushFont(ImFont, Float)` scaling, and style property cleanups.
- **Rationale**:
  - Provides instant, robust support for Apple Silicon users without destabilizing existing UI systems or delaying releases.
  - Keeps the codebase on a stable foundation while equipping developers with an actionable, thoroughly verified roadmap for future major UI modernization.

## Multi-Platform Automated Binary Smoke Testing and Selective Release Gating (`Main.kt`, `build.gradle.kts`, `smoke-test.yml`, `release.yml`)

- **Decision**: Introduce headless multi-component binary smoke testing and multi-platform CI verification across the four supported target architectures (`linux-x64`, `windows-x64`, `macos-arm64`, `macos-x64`), with selective release publishing and clean release notes extraction:
  - **4-Platform Targeted Distributions (`build.gradle.kts`)**: Removed `linux-arm64` (`zipLinuxArm` and `jre/linux-aarch64`) due to the lack of upstream Linux ARM64 native JNI binaries in `imgui-java`. Packaging now focuses exclusively on the 4 viable desktop platforms: Windows x64, Linux x64, macOS ARM64 (Apple Silicon), and macOS x64 (Intel).
  - **CLI Diagnostics & Argument Dispatch (`Main.kt`)**: Added CLI arguments to the entry point: `--version` / `-v` (prints version, OS, architecture, and JVM information), `--help` / `-h`, and `--smoke-test`. The smoke-test mode performs a rapid, headless 5-step self-test without requiring a physical display or GPU: verifies JVM environment, tests LWJGL native library linkage, tests Dear ImGui native bindings and context creation/destruction (`ImGui.createContext()`), verifies classpath resource packaging (core shaders and default presets), and verifies AudioEngine fallback backend initialization, exiting 0 on success.
  - **Launcher Argument Forwarding (`build.gradle.kts`)**: Updated desktop distribution launcher generation in `packageThumbDrive` to forward all command-line arguments to the application JAR (`"$@"` in Unix shell/command scripts and `%*` in Windows batch scripts).
  - **Standalone CI Matrix Workflow (`.github/workflows/smoke-test.yml`)**: A dedicated workflow that builds the platform distributions and runs a parallel smoke-test matrix on GitHub-hosted native runners (`ubuntu-latest`, `macos-latest`, `macos-15-intel`, `windows-latest`) on pull requests and manual workflow dispatches.
  - **Selective Release Gating & Notes Scoping (`.github/workflows/release.yml`)**: Integrated the smoke-test matrix between packaging and publishing. Individual platform matrix jobs execute with `continue-on-error: true`. Jobs that pass upload a verified asset artifact (`verified-<platform>`). The release publishing job gathers only the verified ZIP distributions, ensures at least one platform passed, appends an automated verification badge summary to the release notes, and publishes exclusively verified binaries. Scoped the Python release note extractor to only pull notes from the current version or `## [Unreleased]` (stopping at the section delimiter) and disabled redundant `generate_release_notes: true`, eliminating historical release note accumulation across versions.
- **Rationale**:
  - **Catches Native JNI Incompatibilities Early**: Cross-platform JVM packaging frequently suffers from platform-specific dynamic library linkage errors. Headless smoke testing detects these failures before users download broken releases.
  - **Graceful Partial Releases**: An OS-specific regression or upstream runner glitch on one platform does not block releasing functioning builds on the other platforms, while guaranteeing broken distributions are never published.
  - **Eliminates Release Note Duplication**: Isolating each release's notes to its specific tag/unreleased section prevents hundreds of lines of historical notes and duplicate commit logs from accumulating in GitHub Releases.
  - **Zero Display Server Requirement for CI**: Standardizing on headless diagnostics allows fast verification across disparate runner environments without requiring physical GPU hardware.


## Distribution Packaging, Zip Permissions, and Self-Healing Source Extraction (`build.gradle.kts`, `VisualSourceRegistry.kt`, `Main.kt`)

- **Decision**: Package the complete `library/` folder into all release ZIP archives and thumb drive bundles, enforce POSIX `755` executable permissions across all Unix shell scripts and JRE binaries via Gradle `FileCopyDetails.permissions`, and bundle default visual sources into the fat JAR classpath (`default_sources/`) for automatic runtime self-healing:
  - **Gradle 9 File Permissions Fix (`build.gradle.kts`)**: In Gradle 9, calling `filePermissions { unix("755") }` within an `eachFile { ... }` block resolves to the outer `Zip` task rather than mutating the individual `FileCopyDetails` instance. Replaced with `permissions { unix("755") }` on `FileCopyDetails` for all `.sh`, `.command`, and `bin/` executables (`java`, `jspawnhelper`).
  - **Distribution Packaging (`build.gradle.kts`)**: Updated `packageThumbDrive` and all platform distribution tasks (`zipWindows`, `zipLinux`, `zipLinuxArm`, `zipMacArm`, `zipMacIntel`) to copy and include `library/**` (sources, presets, playlists, and MIDI profiles).
  - **Classpath Bundling & Self-Healing Extraction (`VisualSourceRegistry.kt`, `processResources`)**: Configured `tasks.processResources` to package `library/sources` into the JAR under `default_sources/`. If `library/sources/mandala` or default sources are missing on disk at startup (e.g. running standalone fat JAR or unbundled executions), `VisualSourceRegistry.ensureDefaultSources` extracts the bundled default sources automatically into `library/sources/`.
  - **Clear Error Messaging (`Main.kt`)**: Updated the startup failure exception message from `presets/sources/mandala` to `library/sources/mandala`.
- **Rationale**:
  - Eliminates the need for users to manually run `chmod +x run-linux.sh` or `chmod +x run-mac-*.command` after extracting release zip archives.
  - Fixes startup crashes caused by missing visual source definitions (e.g., `RuntimeException: Mandala source not loaded...`).
  - Provides multi-layered defense-in-depth: the pre-built distribution contains user-editable sources out-of-the-box, and standalone JAR executions self-heal automatically without crashing.

## Default-Disabled Policy for Sequencer, Randomization, and MIDI Subsystems (`AppSettings.kt`, `SettingsPanel.kt`, `UITheme.kt`, `PresetGridPanel.kt`, `CellConfigPanel.kt`, `MidiEngine.kt`)

- **Decision**: Configure the Step Sequencer, Parameter Randomization, and MIDI hardware/mapping subsystems to be disabled by default (`sequencerEnabled = false`, `randomizationEnabled = false`, `midiEnabled = false`), and provide explicit master toggles in Settings:
  - **Sequencer Master Toggle**: Added `sequencerEnabled` in `AppSettings` / `UITheme`, exposed in **Settings** $\to$ **General** and **Settings** $\to$ **Preset Grid**. When disabled, parameter evaluation skips sequencer modulators, `CVRegistry.get("seq")` returns 0.0f, and sequencer columns/tabs are hidden.
  - **Randomization Default**: Changed default `randomizationEnabled` from `true` to `false` in `AppSettings`.
  - **MIDI Master Toggle**: Added `midiEnabled` in `AppSettings` / `UITheme`, exposed in **Settings** $\to$ **MIDI & Controls**. When disabled, device enumeration and watchdog polling are suppressed, open MIDI devices are closed, incoming CC queues are cleared, the "MIDI Map" menu action is disabled, and MIDI modulators evaluate as neutral.
- **Rationale**:
  - **Deterministic Initial State**: Users launching the application for the first time or starting a new session are not confronted with unpredictable randomized parameter drift, unexpected MIDI input intercepting desktop controls, or complex sequencing dynamics running before they have intentionally configured them.
  - **Reduced Resource Consumption**: Suppressing background MIDI device polling, queue draining, and sequencer evaluation until enabled saves unnecessary background thread cycles and USB bus probes on systems without MIDI hardware.
  - **Consistent Subsystem Symmetry**: Brings Step Sequencer and MIDI subsystems into architectural symmetry with `audioEngineEnabled`, where subsystems default to safe states and expose clear master enable toggles with automatic UI column/tab synchronization.

## macOS JRE Bundle Hierarchy & GLFW Main Thread Dispatch (`build.gradle.kts`, `run-mac-arm.command`, `run-mac-intel.command`)

- **Decision**: Accommodate macOS-specific JRE bundle layouts and Cocoa runtime threading constraints in desktop launcher generation and Gradle tasks:
  - **macOS Bundle Directory Structure Resolution**: Adoptium `.tar.gz` distributions for macOS package JRE binaries inside a standard macOS bundle structure (`Contents/Home/bin/java`). The launcher generation scripts (`run-mac-arm.command` and `run-mac-intel.command`) now probe both `jre/macos-<arch>/Contents/Home/bin/java` and flat `jre/macos-<arch>/bin/java` paths before attempting system Java fallback.
  - **Thread 0 JVM Dispatch (`-XstartOnFirstThread`)**: macOS Cocoa requires that GLFW event loop initialization and window message polling execute strictly on the primary OS thread (Thread 0). Both the bundled JRE execution paths and system Java fallback in `run-mac-*.command`, as well as Gradle `JavaExec` tasks when `os.name` contains `mac`, now pass `-XstartOnFirstThread`.
  - **Native Access Warning Suppression (`--enable-native-access=ALL-UNNAMED`)**: Passed to all launcher scripts and `JavaExec` tasks to ensure clean JVM startup without JNI/Unsafe deprecation warnings under modern JDKs (JDK 21+).
  - **Gatekeeper Quarantine Stripping**: Automatically executes `xattr -dr com.apple.quarantine jre 2>/dev/null || true` inside the `.command` launcher scripts to prevent macOS Gatekeeper from blocking execution of bundled JRE binaries extracted from downloaded zip archives.
  - **Zip Permissions Preservation**: Configured `zipMacArm` and `zipMacIntel` Gradle tasks to set `755` permissions across all binaries in `bin/`, `jspawnhelper`, and `.command` launchers.
- **Rationale**:
  - Eliminates the `IllegalStateException: GLFW may only be used on the main thread` crash when launching on macOS Apple Silicon or Intel.
  - Fixes false-positive "Bundled JRE not found" errors that caused the app to fall back to whatever system Java was installed on the user's Mac.
  - Provides a frictionless double-click launch experience on macOS Finder without Gatekeeper quarantine roadblocks.


## Explicit Randomization Disabling for Mixer Randomizer Parameters (`ModulatableParameter.kt`, `Mixer.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `ValueParamSection.kt`, `ModulatorHeaderRow.kt`, `PresetGridRenderer.kt`)

- **Decision**: Explicitly prohibit parameter and modulator randomization on the five Mixer randomization controllers (`Mixer/randDeckA`, `Mixer/randDeckB`, `Mixer/randDeckBG`, `Mixer/randDeckPV`, and `Mixer/randAll`):
  - **Engine Level Gating (`ModulatableParameter.kt`)**: Added `isRandomizeDisabled: Boolean = false` to `ModulatableParameter`. When true, `randomizeBase` is strictly gated to `false`, and `randomizeBaseValue()` is a no-op.
  - **Mixer Parameter Declarations (`Mixer.kt`)**: Declared `randDeckA`, `randDeckB`, `randDeckBG`, `randDeckPV`, and `randAll` with `isRandomizeDisabled = true`.
  - **UI Controls & Tooltip Feedback (`CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `ValueParamSection.kt`, `ModulatorHeaderRow.kt`, `PresetGridRenderer.kt`)**:
    - Dimmed dice toggle buttons (0.25f text alpha) for initial value ranges and modulators driving randomizer parameters.
    - Ignored click and right-click toggle actions on disabled dice.
    - Disabled `"Randomize row"` in the Preset Grid context menu for these parameters.
    - Added contextual tooltip: `"It is forbidden to randomize the randomizer. Chaos would ensue."`
- **Rationale**:
  - **Prevents Destabilizing Control Feedback Loops**: If a modulator (such as an LFO driving continuous morphing on `randDeckA`) could have its own variables randomized during the morph cycle, each boundary crossing would re-seed its own clock/waveform, creating an unrecoverable recursive feedback loop that causes tempo jitter, audio-rate state thrashing, or complete lockups.
  - **Eliminates Dead & Misleading UI**: Previously, these parameters were omitted from `getAllRandomizableParameters()` under the hood, but the UI still permitted toggling random ranges on them, leading to user confusion when the ranges were ignored.
  - **Clear Communicative UX**: The distinct tooltip clearly explains *why* the dice buttons are disabled rather than leaving users wondering if the interface is unresponsive.

## Universal 2D/3D View Pipeline & Contextual Parameter Visibility (`Renderer.kt`, `Deck.kt`, `view2d.frag`, `PresetGridTabs.kt`)

- **Decision**: Establish a clean separation between universal 2D/3D spatial parameters (`Zoom`, `Rotate Z`) and 3D-projection-specific parameters (`Rotate X`, `Rotate Y`, `3D Persp`, `Depth Dim`, `Separation`, `Blend Mode`):
  - **Universal 2D View Pass (`view2d.frag`, `rawSource2DFBO`)**:
    - When `3D Mode < 0.5`, the active visual source renders directly into `rawSource2DFBO` at full native widescreen resolution (`width x height`).
    - A dedicated 2D view transformation shader (`view2d.frag`) executes over a fullscreen quad, mapping coordinates centered at `(0.5, 0.5)`, applying aspect-ratio-corrected isotropic rotation along the Z axis (`uRotateZ` / Roll), dividing by `uZoom` for continuous scaling ($0.1\times$ to $5.0\times$, with $1.0$ being pixel-identical 1:1 scale), and outputting transparent black (`vec4(0.0)`) for coordinates sampled outside the $[0, 1]$ canvas bounds.
    - Blends the transformed source into `cleanFBO` before entering the feedback loop.
  - **3D Mode Preservation (`tri_planar.vert`, `rawSourceFBO`)**:
    - When `3D Mode >= 0.5`, sources render into square 1:1 `rawSourceFBO` (`height x height`) and project onto 3 or 6 orthogonal planes via `tri_planar.vert` & `tri_planar.frag`.
  - **Contextual UI Visibility & Ordering (`PresetGridTabs.kt`)**:
    - `Zoom` and `Rotate Z` are always visible at the top of the `View` subgroup, followed by `3D Mode`.
    - `Rotate X` (Pitch) and `Rotate Y` (Yaw) are hidden when `3D Mode < 0.5`, grouped with the other 3D parameters (`3D Persp`, `Depth Dim`, `Separation`, `Blend Mode`).
    - Full parameter registration in `Deck.getParameterPaths()` is retained so preset loading and modulation routings for `Rotate X` and `Rotate Y` persist across modes.
- **Rationale**:
  - Eliminates misleading "dead" UI controls: `Rotate X` and `Rotate Y` have no geometric meaning in a flat 2D projection.
  - Restores essential 2D rotation and zoom capabilities to flat generators (Mandala, Lissajous, shaders) without reintroducing source-specific duplicate parameters.
  - Aspect-ratio correction prevents non-square circular distortion during 2D rotation.
  - Full native-resolution `rawSource2DFBO` avoids the horizontal downsampling that would occur if using the square 1:1 `rawSourceFBO` in 2D mode.

## 3D Mode Scale Normalization, Cube Cage Base Offset & Tetrahedral Kaleidoscope Space-Folding (`tri_planar.vert`, `tetra_kaleido.frag`)

- **Decision**: Calibrate and normalize scale across 2D flat mode and all four 3D modes, provide base unit displacement for the Cube Cage mode, and overhaul tetrahedral space folding in the kaleidoscope shader:
  - **1:1 Scale Normalization across 2D and 3D View Modes**:
    - In `tri_planar.vert`, clip space coordinates (`clipX`, `clipY`) are scaled by `cameraDistance = 2.5` instead of `1.5`. With hardware perspective division by $w = 2.5$, the NDC vertical bounds span $[-1, 1]$ exactly at `Zoom = 1.0` and `Pitch/Yaw/Roll = 0`, matching 2D flat mode height 1:1 without requiring compensatory zoom ($\approx 1.6$).
    - In `tetra_kaleido.frag`, normalized virtual camera FOV so that at `Zoom = 1.0` and default `Persp = 0.5`, the central medallion facet exactly fills the vertical viewport height.
  - **Cube Cage 6-Panel Outward Displacement (`tri_planar.vert`)**:
    - In Mode 2 (`Cube Cage`, $1.5 \dots 2.5$), added a base unit displacement (`baseOffset = 1.0`) along face normals (`localPos += normal * (1.0 + uSeparation)`). When `Separation = 0.0`, the 6 panels form a true 3D cube box rather than collapsing onto the 3 central planes of Tri-Axial mode. Increasing `Separation` explodes the cube outward into a floating panel array.
  - **Tetrahedral Kaleidoscope Space-Folding & Discard Precision (`tetra_kaleido.frag`)**:
    - Replaced the flawed, contradictory reflection sorting loop with a 4-pass Coxeter $A_3$ tetrahedral space-folding algorithm reflecting across $x \pm y = 0, y \pm z = 0, z \pm x = 0$, guaranteeing convergence to the fundamental chamber ($p_x \ge p_y \ge |p_z| \ge 0$).
    - Switched from unnormalized 3D ray vectors to plane-projected coordinates ($u = p_y / p_x, v = p_z / p_x$), ensuring coordinates remain strictly bounded within $[-1, 1]$ across all 24 chambers.
    - Resolved the complete black screen bug caused by excessive fragment discard (`borderFade <= 0.001`), ensuring smooth disc rounding and vibrant kaleidoscopic tiling.
- **Rationale**:
  - Consistent scale across 2D and 3D modes ensures smooth, predictable transitions when modulating `3D Mode` without sudden jarring changes in visual size.
  - Mode 2 (Cube Cage) now presents an unmistakably distinct 3D geometry from Mode 1 (Tri-Axial).
  - Mode 4 (Tetrahedral Kaleidoscope) functions as a fully operational, mathematically correct 24-chamber 3D optical kaleidoscope.

---

## 3D Mode Restriction to 2D Sources & Native 3D Transform Streamlining (`DynamicVisualSource.kt`, `Deck.kt`, `Renderer.kt`, `PresetGridTabs.kt`, `meta.json`)

- **Decision**: Restrict 3D projection modes (`Tri-Axial`, `Cube Cage`, `Hex-Planar`, and `Tetrahedral Kaleidoscope`) strictly to 2D visual sources, completely excluding the `3D Mode` parameter and its secondary projection pipeline from native 3D visual sources:
  - **Native 3D Source Tagging & Auto-Detection (`is3D`)**:
    - Added `"is3D": true` to the metadata manifests of all 7 native 3D visual generators: `icosahedron` (Icosahedron 32-Stellation), `icosa-v3` (Icosahedron V3 CSG), `hyper_mesh` (4D Hyper-Mesh), `icosa_dodeca` (Icosa-Dodeca), `chladni` (Chladni), `gyroid` (Gyroid), and `hyper_slice` (4D Hyper-Slice).
    - Exposed `val is3D: Boolean` on `VisualSource` and `DynamicVisualSource`.
    - Added automatic fallback detection in `VisualSourceRegistry.kt`: any source declaring `Rotate X` and `Rotate Y` parameters is identified as 3D if not explicitly configured in `meta.json`.
  - **Exclusion of `3D Mode` from Native 3D UI (`PresetGridTabs.kt`)**:
    - In `PresetGridTabs.kt`, the `3D Mode` row is only rendered when `!activeSource.is3D`. For 3D sources, `3D Mode` is completely excluded.
    - Eliminates duplicate sets of rotation controls: previously, enabling 3D mode on 3D sources caused the deck's `Rotate X` and `Rotate Y` to appear alongside the source's native `Rotate X`, `Rotate Y`, `Control X`, and `Control Y`.
    - The `View` tab for 3D sources displays the source's own camera/transform parameters (`Zoom`, `Rotate X`, `Rotate Y`, `Rotate Z`) sorted in a consistent, canonical sequence.
    - Parameter routing uses the canonical source parameter key (`$deckLabel/${activeSource.displayName}/$name`), guaranteeing seamless MIDI mapping, CV modulation, and undo/redo without ID collisions against Deck View parameters.
  - **Pipeline Bypass in Rendering (`Renderer.kt`)**:
    - In `Renderer.kt`, 3D projection is conditioned on `!deck.source.is3D && deck.view3DMode.value >= 0.5f`.
    - When a 3D source is active, the tri-planar/kaleidoscopic projection pipeline is completely bypassed. The native 3D source renders at full native resolution to `rawSource2DFBO` and passes through `view2d.frag` at unscaled 1:1 scale (`uZoom = 1.0f`, `uRotateZ = 0.0f`), preserving the shader's internal perspective and raymarched geometry.
  - **Automatic State Reset on Source Assignment (`Deck.kt`)**:
    - When assigning `deck.source`, if the new source has `is3D == true`, `view3DMode.reset()` is automatically triggered.
- **Rationale**:
  - The 3D projection modes (`tri_planar`, `tetra_kaleido`) were specifically engineered to turn flat 2D sources into 3D objects; re-projecting an already raymarched 3D volume or 4D polychoron mesh through orthogonal planes or kaleidoscope space folding severely mangled and distorted the 3D source's geometry.
  - Eliminates confusing redundant controls in the UI: 3D sources now show one clean, unified set of spatial rotation controls in the View tab.

---

## Mandala Architecture Unification as DynamicVisualSource (`Mandala.kt`, `PresetGridTabs.kt`, `PresetModels.kt`, `WebPresetSerializer.kt`)

- **Decision**: Completely unify `Mandala` into the generic `DynamicVisualSource` framework, removing hardcoded `if (source is Mandala)` / `if (mandala != null)` special cases across the serialization, UI, and preset model layers:
  - **Single Generic Presets Path (`Deck.applyDto`)**: Presets serialize and deserialize `Mandala` strictly using the standard `ModulatableParameter` map (`Lobes`, `Recipe Select`, `L1`–`L4`, etc.). Removed `MandalaRecipeDto` and the redundant `recipe` field on `DeckPresetDto`. When a preset loads, `Mandala.update()` evaluates `Lobes` and `Recipe Select` and restores the matching Fourier ratio automatically.
  - **Dynamic Parameter & Tab Decomposition (`meta.json`, `PresetGridTabs.kt`)**: Reordered and streamlined `library/sources/mandala/meta.json` so generator parameters appear in natural logical order (`Lobes`, `Recipe Select`, `L1`–`L4`, `Thickness`, `Hue Offset`, `Hue Sweep`, `Depth`). Removed legacy hardcoded Mandala transform parameters (`Zoom`, `Rotate Z`, `Rotate Y`, `Rotate X`, `3D Persp`), eliminating duplicate controls in the `View` subtab and delegating all spatial transformation and 3D projection to the universal Deck View pipeline.
  - **Universal MIDI & Parameter Addressing**: Removed the legacy custom `Mandala.getParameterPaths()` override (which produced non-standard group paths like `Deck A/Geometry/L1`); all Mandala controls now follow the canonical format (`Deck A/Mandala/L1`, `Deck A/Mandala/Gain`).
  - **Lightweight Web Broadcast Serialization (`WebPresetSerializer.kt`)**: Replaced the 3-way branching `serializeDeck` with a single generic loop over `src.parameters`, accompanied by a 4-line extension emitting Mandala recipe frequencies (`a`, `b`, `c`, `d`) to preserve full WebGL2 client compatibility.
- **Rationale**:
  - Treats Mandala as a first-class `DynamicVisualSource`, reducing technical debt, code duplication, and UI fragility.
  - Eliminates hardcoded visual source branching in `PresetGridTabs.kt` and `PresetGridPanel.kt`.
  - Preserves 100% backward compatibility for existing `.lsd` files (ignoring legacy `recipe` fields gracefully) and WebGL2 TV clients.

---

## Continuous Constrained Random Morphing & Flip-Flop State Latches (`MorphState.kt`, `Deck.kt`, `Mixer.kt`)

- **Decision**: Transform discrete one-shot randomization triggers (`Mixer/randDeckA`, `randDeckB`, `randDeckBG`, `randDeckPV`, `randAll`) into continuous $0.0 \leftrightarrow 1.0$ morphing controllers:
  - **Two-State Snapshot Model ($S_0 \leftrightarrow S_1$)**: Each deck and mixer maintains two state snapshots (`state0` and `state1`) storing base values and all active modulator parameters (`depth`, `subdivision`, `phaseOffset`, `slope`, `morph`, `hold`, `dcOffset`, etc.).
  - **In-Place Zero-Allocation Lerp**: Converted mutable runtime modulator fields from `val` to `var` in `CvModulator.kt`, allowing per-frame interpolation without allocating objects on the render thread or churning `CopyOnWriteArrayList`.
  - **Flip-Flop Boundary State Machine with Hysteresis**:
    - Ascending towards $1.0$ ($V \ge 0.99$): Latch transitions to `READY_FOR_ZERO` and re-rolls $S_0$ as the new target. Visuals remain static while held at $1.0$.
    - Descending towards $0.0$ ($V \le 0.01$): Latch transitions to `READY_FOR_ONE` and re-rolls $S_1$ as the new target. Visuals remain static while held at $0.0$.
    - Boundary hysteresis guarantees that noisy analog CV or LFO signals wobbling near extremes do not trigger duplicate rolls.
  - **Selective Per-Parameter & Per-Modulator Randomization Gating**: Base values and active modulator properties are interpolated if and only if their respective `randomize*` flags (`param.randomizeBase`, `mod.randomizeDepth`, etc.) are enabled. When disabled, parameters freeze at their current values and state snapshots continuously synchronize to active values, ensuring that manual UI slider adjustments are preserved and never clobbered by morph updates.
  - **Unidirectional Wrap-Around State Promotion**: When driven by monotonic ramps (such as a Sawtooth LFO or `beatPhase` rising $0.0 \to 1.0$), detecting a rapid reset ($V_{\text{prev}} \ge 0.7 \to V \le 0.3$) rotates states in-place: $S_0 \leftarrow S_1$ and $S_1 \leftarrow \text{sampleNewState()}$. Because $\text{lerp}(S_0, S_1, 0.0) = S_0 = S_1$, this completely eliminates turnaround deceleration, peak pauses, and jump cuts, providing seamless, infinite forward visual flow.
  - **Shortest-Path Angle & Hue Interpolation**: Angular parameters (`isAngle`) and circular meters (`MeterType.ENDLESS`) use shortest-path wrapping modular arithmetic to prevent unwinding artifacts across periodic boundaries.
- **Rationale**:
  - Eliminates UI explosion: reuses existing `randDeckX` parameters directly without adding new interval/hold/target sliders.
  - Generative synergy: combining `randDeckA` with LFO waveforms featuring `Hold` (`RANDOM` / `TRIANGLE` with `hold > 0`) automatically delivers customizable morph-then-hold generative evolutions synchronized to beat clock or time.

---

## Unified Title Bar & Custom Window Frame (CSD) (`WindowFrameController.kt`, `MenuBar.kt`)

- **Decision**: Replace the traditional OS title bar with a modern integrated Unified Header & Title Bar (Client-Side Decorations / CSD) combining cross-platform borderless window management with an optional native OS decoration fallback:
  - **Option A (Pure Cross-Platform GLFW / ImGui CSD)**: Implemented `WindowFrameController` to handle window dragging via empty header bar space, double-click maximize/restore toggling, custom window controls (minimize `Icons.MINUS`, maximize/restore `Icons.SQUARE`/`Icons.COPY`, close `Icons.X`), and 4-border/4-corner perimeter hit-testing with dynamic cursor switching (`GLFW_HRESIZE_CURSOR`, `GLFW_VRESIZE_CURSOR`) and minimum bounds clamping (`800x600`).
  - **Option B (Configurable Native Fallback)**: Added `framelessWindow: Boolean = true` to `AppSettings` (persisted in `lsd-settings.properties`) and a toggle in `SettingsPanel` (`Window Frame & Chrome`). When disabled, `Main.kt` passes `GLFW_DECORATED = GLFW_TRUE`, allowing users on tiling window managers (like i3/sway) to use standard OS decorations while hiding the custom window action buttons in `MenuBar`.
- **Rationale**:
  - Reclaims 30–40px of precious vertical screen space on desktop screens, merging menus, telemetry HUD, recording/broadcast controls, and window actions into a single cohesive ~32px top bar.
  - Pure GLFW/ImGui implementation avoids fragile platform-specific native JNI bindings or OS hook complexity while maintaining 100% portability across Linux (X11/Wayland), macOS, and Windows.

---

## Harmonized 5-Column CV Modulation Palette & Central Theme Unification (`CvTheme.kt`)

- **Decision**: Redesign the CV modulation color scheme across `PresetGridPanel`, `CellConfigPanel`, and `AudioEnginePanel` to span 5 distinct quadrants of the color wheel with high-contrast, anti-aliased luminance calibrated for dark backgrounds:
  - **Pruned 5-Column Distribution**:
    - **`VAL` (Value / Base)**: Crisp Mint Cyan (`#00F2B8`, `rgb(0.00, 0.95, 0.72)`) — Ground/anchor reference.
    - **`MIDI` (MIDI CC)**: Bright Orchid Violet (`#B873FF`, `rgb(0.72, 0.45, 1.00)`) — External controller bindings.
    - **`LFO` (Synthetic Oscillators)**: Electric Sky Blue (`#26BFFF`, `rgb(0.15, 0.75, 1.00)`) — Flowing wave generators.
    - **`AUD` (Audio Followers)**: Warm Amber Gold (`#FFAE1F`, `rgb(1.00, 0.68, 0.12)`) — Acoustic energy / VU meter warmth.
    - **`TRIG` (Transient Triggers)**: Hot Coral Rose (`#FF4080`, `rgb(1.00, 0.25, 0.50)`) — Punchy onset/accent transients.
  - **Sub-Band Family Harmonies**: Sub-signals under Audio and Trigger naturally extend their respective color families (Audio RMS Amber, Bass Deep Orange, Mid Golden Amber, High Bright Gold; Trigger Onset Coral Pink, Trigger Accent Crimson Rose).
  - **Single Source of Truth (`CvTheme.kt`)**: Replaced duplicate, hardcoded, and out-of-sync color mappings in `PresetGridPanel.kt`, `CellConfigPanel.kt`, and `AudioEnginePanel.kt` with centralized `CvTheme.getThemeColor()` and `CvTheme.getThemeColorRGB()` calls.
  - **Theme-Adaptive Grid Cells (`PresetGridRenderer.kt`)**: Removed per-column cell knob/needle/arc tinting in favor of inheriting the parameter's native theme text color (`ImGuiCol.Text`). This ensures consistent legibility, clean neutral cell backgrounds, and seamless adaptability across all light and dark theme palettes (Solarized, Lunarized, Neon, Boring) while keeping column headers and `CellConfigPanel` tabs vibrantly color-coded.
- **Rationale**:
  - The previous palette originated from a 12-column matrix where adjacent columns clustered into similar lime greens and deep purples. When pruned to 5 columns, `VAL` (mint) and `AUD` (lime) looked nearly identical, while `MIDI` and `TRIG` were dark/clashing purples.
  - The new 5-column palette evenly distributes hues (~38° Gold, ~165° Mint, ~202° Sky Blue, ~270° Violet, ~340° Coral Rose), ensuring instantaneous visual recognition, excellent text readability, and complete thematic coherence across all modulation panels.
  - Neutralizing per-column cell coloring prevents visual noise and low-contrast clashes against custom theme backgrounds.

---

## Decoupled Audio-Rate CV Oscilloscope History & Frame-Delta Beat Extrapolation

- **Decision**: Decouple sound-derived CV oscilloscope history buffers from the UI render loop and replace hard monotonic clamping in the visual beat clock with frame-delta forward extrapolation:
  - **Audio-Rate Direct History Writes (`AudioEngine.kt`)**: Eagerly cache direct `CvHistoryBuffer` references on `AudioEngine` (`ampHistory`, `bassHistory`, `midHistory`, `highHistory`, `onsetHistory`, `accentHistory`) and append historical samples directly within `processAudio()` at the audio block rate (~86–344 Hz). This bypasses the UI render loop, making audio oscilloscopes immune to UI frame drops, GC hiccups, and render-thread latency spikes.
  - **Block Duration Timestamp Alignment**: Publish beat anchors with `currentTime + blockDurationNs` to synchronize the anchor timestamp with the end-of-block beat position (`totalBeats += deltaTimeSec * (effectiveBpm / 60.0)`).
  - **Single-Source Appending (`CVRegistry.kt`)**: Filter out audio and trigger sources in `CVRegistry.updateAll()` (`isAudioOrTriggerSource(source.id)`) to eliminate double-sampling and redundant map queries on the render thread.
  - **Nominal Frame-Delta Extrapolation (`CVRegistry.getSynchronizedTotalBeats()`)**: Replace the static flatline clamp (`safeBeats = current`) during backwards time jitter with forward progression based on elapsed render frame time (`current + frameDtSec * (bpm / 60.0)`). Reset `lastRenderTimeNs` on `resetBeatAnchor()` to prevent spurious phase jumps on track seeking or tempo resets.
- **Rationale**:
  - Eliminates periodic visual freezing and stuttering across all oscilloscopes, `beatSine`, and beat-synchronized LFO modulators.
  - Preserves strict zero-allocation, lock-free real-time audio thread safety on JACK and Java Sound backends.

---

## Automated Continuous Beta Releases & Release Notes Generation

- **Decision**: Automate the creation and publishing of GitHub beta releases and release notes on every push to `main`:
  - **Push Triggers**: Configure `.github/workflows/release.yml` to trigger on `push: branches: [ main ]`, `push: tags: [ 'v*' ]`, and `workflow_dispatch`.
  - **Sequential Concurrency**: Enforce concurrency grouping (`cancel-in-progress: false`) to ensure sequential, non-colliding releases.
  - **Dynamic Beta Versioning**: On branch push, introspect existing tags, determine the latest `v1.0.0-beta.N` tag, calculate `v1.0.0-beta.(N+1)`, tag the commit via `github-actions[bot]`, and push the tag.
  - **Automated Changelog & Resilient Release Notes Extraction**: Automatically extract rich release notes from `docs/release_notes.md`, `RELEASE_NOTES.md`, or alternative changelog files. The extractor checks for an exact version match, an `[Unreleased]` staging block, or falls back to the topmost version block in the file (normalizing the header), and prepends these human-friendly highlights above the compiled commit history with links and authors. Releases are named directly as `${TAG_NAME}` (e.g., `v1.0.0-beta.38`) with `make_latest: true`.
- **Rationale**:
  - Eliminates manual release creation and keeps human-curated documentation synchronized with rapid push-based releases even when the exact beta tag count outpaces manual version bumps.
  - Guarantees immediate binary distribution builds (`windows-x64`, `linux-x64`, `linux-arm64`, `macos-arm64`, `macos-x64`) for rapid testing across all platforms.

---

## Play Queue and Background Queue Feature & Interaction Parity

- **Decision**: Symmetrize modulation, auto-advance triggers, dirty-state protection, navigation controls, and interaction models between the A/B Play Queue and Background Queue:
  - **Modulation & MIDI CC Parity**: Added `Mixer/bgQueuePrev` and `Mixer/bgQueueNext` modulatable parameters with MIDI CC inputs (`Global/bgQueuePrev`, `Global/bgQueueNext`) and modulation grid rows in the Mixer tab.
  - **Title Bar Navigation Controls**: Added `<` and `>` quick navigation buttons directly to the title bars of both Play Queue and Background Queue.
  - **Deck BG Dirty Checking**: Symmetrically checks `PresetManager.isDeckDirty` on `Deck BG` during all queue advances and transitions, respecting `UITheme.autoVjDirtyBehavior` (`SKIP`, `AUTO_SAVE`, `AUTO_DISCARD`).
  - **Double-Click Playback Parity**: Double-clicking any track in the Play Queue targets the standby deck and smoothly auto-fades to it (`playIndex(index, mixer)`), matching the immediate dip-to-black double-click playback in Background Queue.
  - **Uniform Drop Target Styling**: Pushed transparent drag-and-drop target styling across all 4 library columns to eliminate intrusive yellow highlight bounding boxes.
- **Rationale**:
  - Provides a consistent, predictable mental model for VJs managing dual-deck A/B foreground visuals and background visual sets.
  - Enables hands-free MIDI and generative CV control over background playlist progression.

---

## Mandala Background Parameter Extraction into Standalone "Colors" Visual Source

- **Decision**: Remove legacy background parameters (`Bg Style`, `Bg Feedback`, `Bg Hue`, `Bg Sat`, `Bg Val`, `Bg Sweep`, `Bg Speed`, `Bg Zoom`) and custom background rendering passes from the `Mandala` visual source and `Renderer.kt`, packaging the background solid-color and plasma generation capabilities into a new standalone visual source called **Colors** (`library/sources/colors/`):
  - **Standalone Generator (`Colors`)**: Standardized as a `DynamicVisualSource` with parameters `Style` (0 = Off, 1 = Solid, 2 = Plasma), `Hue`, `Sat`, `Val`, `Sweep`, `Speed`, and `Zoom`.
  - **Pipeline Simplification**: Eliminates custom secondary background rendering passes in `renderMandala()` and `renderDeckFeedback()`, as well as `cleanFBO` texture overrides in `Deck.getOutputTexture()`.
  - **Multi-Deck Compositing**: Leveraging the dedicated `Deck BG` compositing layer, any background styling (solid colors, animated plasma, or other visual sources) is now composed uniformly via the mixer rather than hardcoded inside individual geometric generators.
- **Rationale**:
  - Mandala was built prior to the dedicated 4-deck architecture and had ad-hoc background uniforms embedded directly inside its parameter set and renderer.
  - Decoupling visual generators maintains the single-responsibility principle and lets users route `Colors` (or any other generator) to any deck (`Deck A`, `Deck B`, `Deck BG`, or `Deck PV`).

---

## Beat Tracker (BeatTrackerEngine) Real-Time Engine & Continuous Modulation Generator

- **Decision**: Replace heuristic beat detection with a stateful, zero-allocation beat tracking engine modeled on **BTrack** (Adam Stark) and the Dan Ellis causal dynamic programming model:
  - **Complex Spectral Difference ODF**: 512-point Radix-2 Cooley-Tukey FFT with pre-allocated twiddle factors and Hann windowing. Evaluates 2nd-order phase trajectory prediction to detect pitched attacks and percussive transients while suppressing steady-state tones.
  - **Two-State Multi-Band Periodicity Estimation**: State 1 (Acquisition) searches 40–200 BPM across circular history buffers; State 2 (Locked Tracking) constrains search to $\pm 15\%$ around current tempo period with $\pm 2.0$ BPM/beat human tracking inertia and harmonic comb unwrapping.
  - **Decoupled Periodic Autocorrelation**: Decouples multi-second autocorrelation calculation from the per-block rate to run periodically every 4 blocks (~46 ms, ~21.5 Hz) with physical time-step scaling ($dt_{\text{interval}} = dt \cdot 4$), slashing loop iterations by 75% without compromising tempo lock speed or accuracy.
  - **Causal Dynamic Programming Recurrence**: Circular cumulative score buffer evaluating causal DP recurrence with pre-tabulated $\log(\tau)$ tables (`logTauTable`) to eliminate transcendental functions from the real-time audio thread.
  - **Subnormal / Denormal Float Flushing (`BiquadFilter.kt`)**: Flushes biquad filter recursive state variables (`z1`, `z2`) to zero when $|\text{state}| < 10^{-15}\text{ f}$, eliminating hardware microcode exceptions and CPU pipeline stalls when audio decays toward silence.
  - **Continuous Phase & Cosine Generator**: Outputs continuous normalized phase $\phi(t) \in [0.0, 1.0)$ and locked cosine modulation signal $\cos(2\pi \phi(t))$ via zero-allocation queries (`getPhase`, `getCosine`, `getPhaseAndCosine`, `getPhaseAndCosinePacked`).
- **Rationale**:
  - Eliminates visual phase stutter and snapping during tempo adjustments or syncopated drum breaks.
  - Guarantees strict zero-allocation real-time safety and prevents subnormal floating-point stalls on JACK/PipeWire audio callback threads and 60–144Hz+ rendering loops, preventing audio buffer underruns (XRUNs).

---

## 4-Deck Architecture (Deck BG & Deck PV), 2x2 Monitor Grid, and 4-Column Library

- **Decision**: Evolve the rendering and UI architecture from a 3-deck model (A, B, C) to a dedicated 4-deck pipeline (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`):
  - **Background Compositing Layer (`Deck BG`)**: Rendered in GLSL (`mixer.frag`) directly beneath the crossfaded A/B foreground (`rgb = fg.rgb + bg.rgb * (1.0 - fg.a)`), allowing transparent and additive foreground visuals to float naturally over dynamic background visuals.
  - **Dedicated Preview Deck (`Deck PV`)**: Dedicated preview deck `Deck PV` for visual monitoring and preparation without affecting master output.
  - **2x2 Preview Monitor Matrix**: Arranged deck monitors in a symmetrical 2x2 grid (Top: A & B; Bottom: BG & PV) with interactive toolbars, letter badges, drag-and-drop routing, and individual deck theme coloring.
  - **4-Column Library**: Organized the browser panel into 4 balanced columns: Presets | Playlists | A/B Play Queue | Background Queue (`BgQueueManager`).
  - **Unified Top Action Toolbar**: Eliminated cluttered inline row buttons in favor of a top action bar (`[A] [B] [BG] [PV] [Q] [BGQ] [+]`) with mutual selection between Presets and Playlists.
  - **Background Queue Engine**: Built a single-deck dip-to-black state transition machine for automated or manual background cycling.
- **Rationale**:
  - Solves the visual clutter problem of having separate load buttons on every preset row when multiple deck targets exist.
  - Provides a dedicated background layer essential for multi-layer generative visual performance.
  - Symmetrical 2x2 monitor grid maximizes screen estate and preserves equal aspect ratios for all decks.

---

## Per-Modulator Audio Envelope Followers & Dual-Trace Oscilloscope

- **Decision**: Implement independent per-modulator audio envelope followers with selectable dynamics presets and contextual custom sliders:
  - **Independent per-band / per-modulator**: Each audio band modulator (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`) maintains its own runtime envelope follower state rather than forcing a global smoothing parameter across all visuals.
  - **Preset Dropdown Workflow**: Offer musically tuned presets (`Raw (Instant Jitter)`, `Punchy (Fast)`, `Smooth Swell`, `Slow Pulse`, `Ambient Drift`, and `Custom`). Hardcode Attack and Decay numbers in presets while hiding sliders to keep the interface minimal.
  - **Seamless Custom Transition**: Selecting `Custom` automatically populates the Attack and Decay sliders with the current active preset numbers (e.g. transitioning from `Ambient Drift` sets Attack to $250\text{ ms}$ and Decay to $1500\text{ ms}$).
  - **Dual-Trace Oscilloscope Feedback**: The parameter's audio oscilloscope renders the raw incoming audio band energy in a faint ghosted trace ($35\%$ alpha) beneath the solid smoothed follower curve, fully respecting live vs. muted color styling.
- **Rationale**:
  - Eliminates visual audio jitter on parameters that require smooth organic breathing (like zoom, rotation, or color drift) while preserving instant reactive jitter where desired.
  - Zero heap allocation in audio callbacks and render loops; sample-rate and framerate independent.

---

## Auto-VJ Manual Deck Load & Line-Jumping Architecture

- **Decision**: Integrate manual deck preset loading with Auto-VJ and the Play Queue using a non-destructive, staged line-jumping model:
  - **Queue Preservation**: Manual deck loading when Auto-VJ is OFF never modifies queue contents or the active index.
  - **Standby Staging ("Jump the Line")**: When a preset is manually loaded into the inactive/standby deck while Auto-VJ is active, that deck is marked as staged. The next Auto-VJ trigger initiates an automated crossfade directly to the staged preset without overwriting it from the queue, preserving the next queue track for the subsequent cycle.
  - **Active Deck Overrides**: Manually loading into the live deck replaces the output immediately while keeping Auto-VJ armed and the queue index untouched.
  - **Seamless Mid-Set Arming**: Enabling Auto-VJ mid-set does not trigger immediate jump cuts; it waits for the next advance trigger (CV pulse, MIDI CC, beat trigger, or UI button) to load into the standby deck.
  - **Deck PV Isolation**: Manual interactions with Deck PV (master overlay) remain completely independent of the A/B Auto-VJ pipeline.
- **Rationale**:
  - Matches industry-standard DJ/VJ workflows (Traktor Pro Cruise mode, VirtualDJ Automix).
  - Eliminates accidental queue mutation during manual performance and avoids jarring visual jump cuts.

---

## GUI Scale: Native HiDPI Handling and Single User UI Scale Knob

- **Decision**: UI sizing is governed solely by the baseline font size and persistent user zoom preference (`guiScalePercent`, 75%–200%, 5% steps). Manual `systemDpiScale` factoring was removed:
  $$\text{Effective Base Size (px)} = 15.0\text{px} \times \left(\frac{\text{guiScalePercent}}{100}\right)$$
- **Rationale**:
  - **Elimination of Double-Scaling**: Starting with `imgui-java` 1.86.12, the ImGui GLFW and GL3 backends automatically detect and scale the display for OS window content scale (`glfwGetWindowContentScale`). Retaining manual `systemDpiScale` multiplication in `baseSize` resulted in UI elements scaling twice (e.g. 4× on 2× HiDPI displays).
  - **Clean Single-Knob Model**: Modern desktop applications maintain logical pixels in application code while delegating display scaling to the backend/compositor. The user setting (`guiScalePercent`, default 100%) acts as a clean relative zoom factor.
  - **Discrete Steps**: 5% steps keep the slider tactile and prevent blurry fractional font rasterization in the Dear ImGui atlas.

---

## 1. Tech Stack Selection (Kotlin/JVM + LWJGL + JNAJack)
- **Decision**: Build the VJ system using **Kotlin/JVM** on top of **LWJGL 3 (GLFW + OpenGL 3.3)** for graphics and **JNAJack** for Linux audio.
- **Rationale**: 
  - **Kotlin/JVM** provides excellent development velocity, strong typing, and rich ecosystem support (e.g. serialization, logging).
  - **LWJGL 3** gives us thin, high-performance bindings to native windowing (GLFW) and graphics (OpenGL), bypassing heavy Java 2D or JavaFX graphics layers.
  - **JNAJack** provides direct, low-latency access to JACK/PipeWire sound servers, which is crucial for real-time audio analysis.

---

## 2. Zero-Allocation & Non-Blocking Audio Callback
- **Decision**: Enforce a strict zero-allocation, non-blocking rule within the JACK audio processing thread (e.g. `AudioEngine` callback).
- **Rationale**: 
  - The JACK callback runs inside a real-time system thread managed by the host OS audio server. Any blockage (I/O, locks, logging) or non-deterministic CPU pause (JVM Garbage Collection allocation sweeps) can cause buffer underruns, resulting in audible stutters/dropouts (xruns) or server crashes.
  - All buffers, filter banks, and memory arrays used in audio analysis are pre-allocated during initialization.

---

## 3. Z Garbage Collector (ZGC) for Latency Control
- **Decision**: Mandate launching the JVM with ZGC (`-XX:+UseZGC`) and a low pause-time target (`-XX:MaxGCPauseMillis=2`).
- **Rationale**: 
  - The default G1 garbage collector can introduce pause times of 10-100ms. Since a typical low-latency audio buffer of 128 frames at 48kHz must process in **2.6ms**, a GC pause of even 3ms will guarantee an xrun.
  - ZGC performs concurrent GC phases, keeping JVM pauses under 1ms, safely below the real-time audio thread budget and visual frame budgets (16.6ms for 60Hz).

---

## 4. Single-Threaded Windowing and OpenGL (Thread 0)
- **Decision**: Bind the OpenGL context and all GLFW window operations strictly to the primary OS thread (Thread 0).
- **Rationale**: 
  - Operating system window managers (particularly macOS and Linux X11/Wayland) require window events and event polling to run on the thread that created the window.
  - Calling OpenGL functions from multiple threads or off-thread triggers driver faults and immediate native JVM crashes (segfaults).

---

## 5. Lock-Free Audio-to-Render & Audio-to-Worker Data Passing
- **Decision**: Avoid mutexes/locks (`ReentrantLock`, `synchronized`, blocking queues) on the real-time audio thread. Instead, pass data from the audio thread to the rendering thread using `@Volatile` primitive fields (`anchorBeats`, `anchorBpm`, `anchorTimeNs`), the custom single-writer `CvHistoryBuffer` ring-buffer, and lock-free Single-Producer Single-Consumer (`SpscQueue`) ring buffers for live audio session recording (`RealtimeRecorder`).
- **Rationale**: 
  - Locking on the audio thread can cause **priority inversion**, where a lower-priority rendering or background worker thread holding the lock blocks the real-time audio thread.
  - Lock-free structures keep all threads decoupled and wait-free; transient data races in visualization buffers (like the oscilloscope) cause at most a single-frame visual glitch rather than an application-wide crash or xrun.

---

## 6. Explicit ImGui Native Memory Management
- **Decision**: Explicitly allocate and free native `imgui-java` structures (e.g. calling `.destroy()` on styles and font configs, and caching `ImString` buffers as class fields).
- **Rationale**: 
  - `imgui-java` is a JNI wrapper around a C++ immediate-mode library. Standard JVM GC does not track or clean up C++ heap allocations.
  - Allocating ImGui objects (like `ImString` or `ImInt`) per-frame on the heap results in severe native memory leaks. Failing to keep a JVM reference to font arrays while native ImGui references them triggers JVM SegFaults.

---

## 7. Cross-Platform Audio Capture with JACK and Java Sound (TargetDataLine) Fallback
- **Decision**: Support both JACK/PipeWire and Java Sound (`TargetDataLine`) as audio backends. JACK is the preferred primary audio backend on Linux, while Java Sound serves as a cross-platform fallback on macOS, Windows, and Linux if JACK is absent.
- **Rationale**: 
  - **JACK/PipeWire** provides the native, ultra-low-latency pipeline (<3ms buffer sizes) and visual patchbay routing (e.g. `Helvum`, `qpwgraph`) critical for professional Linux VJs who need to route audio between applications (e.g. from Bitwig/Reaper into Liquid LSD).
  - **Java Sound (`TargetDataLine`)** is part of the standard JDK and runs out-of-the-box on macOS, Windows, and JACK-less Linux configurations without native dependencies, ensuring the visuals remain fully audio-reactive across all OSes.
  - On Linux, supporting both allows pro-audio users to benefit from JACK's superior routing and low-latency, while providing a seamless, config-free setup for casual desktop users.

---

## 8. Decoupled Asynchronous Preset IO
- **Decision**: Execute all preset saving/loading (`.lsd` JSON files) on a dedicated daemon background thread (`PresetManager-IO` executor) and pass the loaded DTOs to the main thread via thread-safe queues.
- **Rationale**: 
  - File I/O is slow and blocking. Saving or loading a preset on the main rendering thread would cause noticeable frame drops during a live VJ performance.
  - Loading on a background thread keeps rendering smooth, and using thread-safe queues ensures that applying loaded presets happens atomically at the start of the next render frame, preventing OpenGL state corruption.

---

## 9. Terminology Standardisation: Preset & Unified Library Folder
- **Decision**: Refactor visual parameter snapshots from 'Patch' to 'Preset' (e.g., `PresetManager`, `DeckPresetDto`, `PresetGridPanel`), and rename the top-level user storage directory from `presets/` to `library/` (containing `library/presets/`, `library/midi/`, `library/playlists/`, `library/sources/`, `library/last_session.json`).
- **Rationale**: 
  - Aligns with VJ / audio industry standards (Resolume, Ableton, TouchDesigner) where 'patch' refers to modular node graphs, whereas saved parameter snapshots are called 'presets'.
  - Eliminates path redundancy (e.g. `presets/patches` or `presets/presets`) by establishing `library/` as the single root folder for user assets.

---

## 10. Streamlined Beta Architecture: Zero Backwards Compatibility Shims
- **Decision**: Remove all legacy migration logic, serialization aliases (`@SerialName`), fallback file path resolvers (`.json`/`.patch`), directory migrations (`presets/` -> `library/`), and parameter name translation dictionaries across models, session state, and UI.
- **Rationale**: 
  - During early beta development, maintaining backwards compatibility shims and dual-path code introduces maintenance overhead, dead code branches, and unnecessary complexity.
  - Streamlining the codebase to use canonical naming (`depth`, `presetNotes`, `library/presets/*.lsd`) ensures high readability, clean domain boundaries, and zero legacy bloat.

---

## 11. Standalone WebGL2 Core Renderer Architecture (Phase 1)
- **Decision**: Port the core desktop multi-pass OpenGL rendering pipeline to WebGL2 and GLSL ES 3.0 as a vanilla, standalone web client in `web/` without bundlers, transpilers, or npm dependencies.
- **Rationale**:
  - Eliminates build system complexity and dependency fragility, enabling instant evaluation via any static web server or browser.
  - Maintains 1:1 parity with desktop GLSL shaders (Mandala ribbon, Dynamic Spiral, Feedback ping-pong, and Mixer compositing) while utilizing standard WebGL2 floating-point framebuffers (`RGBA16F` with `EXT_color_buffer_float`).

---

## 12. Browser-Side Web Audio DSP & Icecast Streaming (Phase 2)
- **Decision**: Implement real-time audio analysis and beat tracking in `web/dsp.js` using the standard Web Audio API connected to the live `radio.spaz.org:8060` Icecast stream via `AudioContext` and `createMediaElementSource`.
- **Rationale**:
  - Direct client-side streaming and analysis removes the need for backend DSP or WebSocket relays.
  - Multi-band biquad filtering (lowpass, bandpass, highpass) + RMS peak followers yield zero-allocation per-frame CV metrics (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`).
  - Dual-average onset detection with IOI history tracking generates synchronized beat phase and sine modulation at 60fps render cadence.

---

## 13. Retro TV Shell & CRT Post-Processing Pipeline (Phase 3)
- **Decision**: Wrap the standalone WebGL2 visualizer in a retro CRT TV bezel (`tv.css`, `ui.js`) with physical power switch, rotary volume dial, and single-pass CRT post-processing fragment shader (`crt_post.frag`).
- **Rationale**:
  - Encapsulating the player in a retro TV housing with an interactive power switch fulfills browser autoplay user-gesture policies organically without generic modal dialogs.
  - Consolidating barrel curvature, RGB phosphor shadow mask triad, scanlines, chromatic aberration, corner vignette, cold-state animated static noise, and expanding-raster warmup sequence into a single fragment shader pass (`crt_post.frag`) minimizes GPU overhead and replaces the generic final blit.
  - Gating multi-pass render passes (Passes 1–4) when powered off eliminates wasteful GPU computation while displaying animated static noise.
  - Rotary dial volume control applies a squared attenuation curve ($V^2$) via Web Audio `GainNode.setTargetAtTime` for natural, perceptually linear volume adjustment.

---

## 14. WebSocket Relay Server & 24/7 Autopilot Fallback (Phase 4)
- **Decision**: Implement a Node.js WebSocket relay server (`server/server.js`) with bearer token authentication for broadcasters (`role=broadcast&key=...`) and fan-out distribution to web viewers, paired with a client-side 24/7 Autopilot playlist scheduler (`web/autopilot.js`).
- **Rationale**:
  - Keeps the backend stateless and lightweight: caches the latest `state_full` JSON payload in memory and fans out updates to thousands of viewers without transcoding or heavy computing.
  - Autopilot engine guarantees 24/7 autonomous visuals on the Web TV client using fade-through-black master alpha transitions when no live broadcaster is connected.
  - Viewers automatically switch to the live broadcast upon receiving `state_full` and revert to autopilot upon `broadcaster_offline`.

---

## 15. Desktop Broadcaster Subsystem & Throttled Delta Streaming (Phase 5)
- **Decision**: Implement `BroadcastEngine` and `WebPresetSerializer` in the desktop app using Java 11+ `java.net.http.WebSocket` running asynchronously on a dedicated daemon executor (`BroadcastEngine-IO`), with rate-limited parameter delta streaming (~25 Hz).
- **Rationale**:
  - Decoupling network I/O from the main GLFW/OpenGL thread and JACK audio thread ensures zero dropped frames and zero audio dropouts (xruns).
  - Emitting full state snapshots (`state_full`) upon connection/preset loads and compact diff patches (`state_delta`) during live knob/fader adjustments minimizes network bandwidth while keeping live interactions immediate and snappy.
  - Dedicated "Web Broadcast" settings tab in `SettingsPanel.kt` and menu bar status HUD give the VJ clear visual confirmation of live status and one-click toggle control.

---

## 16. Desktop-to-Web Asset Synchronization & Manifest Drift Tracking (Phase 6)
- **Decision**: Establish desktop shaders and Kotlin algorithms as the authoritative source of truth, backed by `web/sync_manifest.json`, the `scripts/sync_web.py` CLI engine, and Gradle/CI verification tasks (`checkWebSync`, `syncWeb`, `WebSyncTest.kt`).
- **Rationale**:
  - Mechanical GLSL translation (converting `#version 330 core` to WebGL2 `#version 300 es` + `precision highp float;`) eliminates redundant manual shader copy-pasting and prevents syntax drift between platforms.
  - Tracking SHA-256 hashes of algorithmic Kotlin sources (`Icosahedron.kt`, `Evaluators.kt`, `WebPresetSerializer.kt`) ensures developers are immediately warned with exact file paths and instructions whenever desktop math logic changes without a corresponding update to web JavaScript equivalents.
  - Zero-dependency Python CLI coupled with standard Gradle `Exec` tasks and JUnit tests guarantees drift detection is caught in development, pre-commit, and CI pipelines without adding heavy external dependencies.

---

## 17. Audio Engine Settings & Real-Time Monitor Consolidation
- **Decision**: Consolidate the Audio Engine hardware controls, backend routing, beat detection settings, and real-time oscilloscopes into the dedicated "Audio Engine" category tab inside `SettingsPanel.kt`, while maintaining the modular implementation in `AudioEnginePanel.kt`.
- **Rationale**:
  - Eliminates redundant overlapping popup modals by unifying hardware configuration with real-time waveform and CV monitoring in one consistent location.
  - Top menu bar "Audio Engine" item routes directly into the Settings modal focused on the Audio Engine tab for instant 1-click access.
  - Clean separation of concerns: `AudioEnginePanel.kt` encapsulates all zero-allocation oscilloscope rendering, primitive state arrays, and audio UI logic.

---

## 18. Pure JVM/Gradle Static Site & Documentation Generator (`greenjon.com`)
- **Decision**: Implement a native Kotlin/JVM static site generator (`SiteGenerator.kt`) executed via Gradle (`./gradlew buildWebsite` / `./gradlew exportGreenjon`) to compile `docs/`, `RELEASE_NOTES.md`, and project metadata into a self-contained `./greenjon/` distribution folder ready for FTP upload.
- **Rationale**:
  - **Zero External Tooling**: Eliminates dependencies on external Python (`mkdocs`), Node.js, or Ruby engines for generating the product website and web documentation.
  - **Single Source of Truth**: Project version, release URLs, and documentation are pulled directly from `build.gradle.kts` and `docs/`, ensuring the website and downloadable documentation package (`docs.zip`) remain continuously in sync with codebase releases.
  - **Self-Contained & Relative**: All generated HTML pages, CSS stylesheets, SVGs, and scripts use relative asset paths, allowing immediate drag-and-drop deployment via FTP to `greenjon.com` or any static web host.

---

---

## 20. CapsLock Multi-Touch Trackpad Performance Console (SCS.3m Virtual Console)
- **Decision**: Implement a 4-zone capacitive trackpad performance console inspired by the vintage Stanton SCS.3m DJ mixer, activated via the `CapsLock` latch key.
  - **4-Zone Spatial Layout**:
    - Bottom 28% ($Y \in [0.00, 0.28]$): Horizontal Crossfader (Deck A $\leftrightarrow$ Deck B, mapped to `mixer.crossfade` $[-1.0, 1.0]$).
    - Middle 17% ($Y \in [0.28, 0.45]$): Safety Deadzone Buffer (retains active drift under Zone Affinity, rejects new taps).
    - Top 55% ($Y \in [0.45, 1.00]$): Three independent vertical Level/Alpha faders for Deck A, Deck BG, and Deck B ($0.0 \dots 1.0$, direct jump).
  - **Independent LIFO Multi-Touch Stacks**:
    - Each zone maintains an independent LIFO touch stack. New taps jump directly to the contact point; lifting a tap snaps back to the underlying anchor finger, enabling high-speed cut stutters and video flash/strobe blackout gates.
    - Sticky hold preserves fader level when all fingers are lifted.
    - Bezel clamping ($Y \le 0.48 \to 0.0$, $Y \ge 0.94 \to 1.0$; $X \le 0.05 \to -1.0$, $X \ge 0.95 \to 1.0$, center detent $\pm 0.02 \to 0.0$) ensures comfortable control without hitting the physical plastic chassis.
  - **Platform Architecture**:
    - **Linux**: Direct evdev reader via JNA `libc` (`open`, `close`, `read`, `ioctl`, `access`), `O_RDWR` with `EVIOCGRAB` (`0x40044590`) cursor grab, and `EVIOCGABS` hardware axis limit query with Multi-Touch Protocol B slot state caching synchronized on `SYN_REPORT`.
    - **Device Discovery & TrackPoint Isolation**: Device scanning inspects `/dev/input/by-id` and `/proc/bus/input/devices`, explicitly rejecting non-touchpad hardware (`trackpoint`, `pointingstick`, `pen`, `stylus`, raw mice) and verifying absolute hardware coordinate capability (`B: ABS=`). This avoids selecting ThinkPad TrackPoints or standard mice that share vendor substrings (e.g. `ELAN`).
    - **macOS**: Cocoa `NSTouch` indirect touch events.
    - **Permissions & POSIX ACL Verification**: Systemd `TAG+="uaccess", TAG+="seat", RUN{builtin}+="uaccess"` udev rule (`/etc/udev/rules.d/70-liquidlsd-touchpad.rules`) via `pkexec`. Access is validated via JNA `access(2)` (`R_OK or W_OK`) rather than JVM `File.canWrite()`, because `File.canWrite()` ignores POSIX ACLs assigned to desktop seat users by `systemd-logind`.
    - **Thread Safety**: Events are pushed into a lock-free queue and processed strictly on Thread 0 once per frame.

---

## 22. LFO and Audio Min/Max Conversion & Sequencer UI Simplification (`CvModulator.kt`, `Lfo1Section.kt`, `AudioModulatorSection.kt`, `SeqSection.kt`, `CustomRangeSlider.kt`)

- **Decision**: Convert LFO 1 and Audio modulator user controls from abstract "Depth" (amplitude) and "DC Offset" (center) to intuitive "Min Value" and "Max Value" range bounds:
  - **Human-Readable Bounds**: Users now explicitly define the modulation boundaries (e.g., Min 0.20, Max 0.85) rather than calculating offset and span in their heads.
  - **Dual-Handled Range Sliders**: Introduced `drawMinMaxRangeSlider` in `CustomRangeSlider.kt`. It provides a single track with two independent handles for setting modulation limits in one visual sweep.
  - **Two-Tier Randomization Ranges**: When "randomize" is enabled for Min/Max, the UI expands to two dual-handled sliders:
    - Top Slider: Defines the random range for the **Minimum** boundary.
    - Bottom Slider: Defines the random range for the **Maximum** boundary.
  - **Backward-Compatible Math & DTOs**: Retained internal `depth` and `dcOffset` fields in `CvModulator.kt` for seamless integration with the existing rendering engine and math routines. Bidirectional conversion math ($Depth = (Max - Min) / 2$, $Offset = (Max + Min) / 2$) is performed on the fly during UI interaction and randomization.
  - **Sequencer UI Simplification**: Removed the redundant "DC Offset" control from the Step Sequencer UI (`SeqSection.kt`), keeping "Depth" as the sole scaling factor to streamline pattern modulation.
- **Rationale**:
  - Abstract Depth and Offset are mathematically precise but cognitively heavy during fast-paced live VJ sets. Min/Max bounds map directly to the visual extremes of the parameter being modulated.
  - Symmetrizing the randomization UI for bounds allows for complex, multi-layered generative drift (e.g., a "breathing" LFO where the floor and ceiling themselves drift over time).
  - Cleaning up the Sequencer UI reduces clutter and focuses control on pattern amplitude, as step values are already typically defined relative to the sequencer's base range.

## Phase 2.2.1: Single-Pass ISF Filter Engine & Deck Pipeline Integration (`VisualEffect.kt`, `ISFFilter.kt`, `ISFFilterRegistry.kt`, `Deck.kt`, `Renderer.kt`, `PresetModels.kt`, `PresetGridTabs.kt`)

- **Decision**: Integrate a modular ISF post-processing stage into each Deck's rendering pipeline, supporting single-pass image filters:
  - **Modular FX Slot 1**: Each deck features one dedicated FX slot (Slot 1) positioned between the 2D/3D geometry transform (`cleanFBO`) and the feedback loop.
  - **ISFFilter Engine**: Implemented `ISFFilter` following the ISF v2.0 specification for single-pass fragment shaders. Includes automatic parameter synthesis for modulatable inputs and standard ISF uniform injection.
  - **Hardware Dry/Wet Blending**: When Dry/Wet is between 0.0 and 1.0, the dry signal is blended with the filter output using `glBlendColor` / `GL_CONSTANT_ALPHA` to minimize draw calls.
  - **Preset Grid Integration**: Added filter selection, bypass, and modulatable parameter controls to the "FX" sub-tab in the Preset Grid.
  - **Bundled Filters**: Included standard single-pass ISF filters (Invert, Hue Shift, Posterize, Luma Key, Edge Detect) in application resources.
- **Rationale**:
  - Establishes the foundation for modular effect chains as outlined in the Inter-App Interoperability Roadmap.
  - Leverages the existing ISF ecosystem for extensible image processing.
  - Enhances creative flexibility by allowing color and stylized effects to be applied before the feedback loop.
  - Maintains backward compatibility with existing presets through optional DTO fields.

## Phase 2.2.2: Multi-Pass ISF Engine, Dual FX Slots & Universal Shader Picker (`ShaderPickerPopup.kt`, `ISFParser.kt`, `ISFFilter.kt`, `Deck.kt`, `Renderer.kt`, `PresetModels.kt`, `PresetGridTabs.kt`, `ISFMultiPassTest.kt`)

- **Decision**: Introduce multi-pass ISF shader processing, dual serialized FX slots per Deck, preset serialization, and a high-performance searchable shader picker:
  - **Dual FX Pipeline Architecture (`Deck.kt`, `Renderer.kt`)**: Added a second serialized FX slot (**Slot 2: Spatial / Distortion**) to each Deck pipeline (`Visual Source` $\rightarrow$ `2D/3D Transform` $\rightarrow$ `cleanFBO` $\rightarrow$ `[Slot 1: Color / Degradation]` $\rightarrow$ `[Slot 2: Spatial / Distortion]` $\rightarrow$ `feedback.frag`).
  - **Multi-Pass ISF Shader Engine (`ISFParser.kt`, `ISFFilter.kt`)**: Implemented support for `PASSES` arrays, custom target buffer resolution expressions (`$WIDTH/2.0`, `$HEIGHT/2.0`), automatic sampler uniform injection (`uniform sampler2D <TARGET>;`), and ping-pong accumulation buffers for `PERSISTENT: true` temporal feedback loops without GPU read/write hazards.
  - **Universal Shader Picker Modal (`ShaderPickerPopup.kt`)**: Built a zero-allocation, searchable modal dialog with category pill filtering (`Color Adjustment`, `Distortion`, `Glitch`, `Geometry`, `3D`, `Generator`, `All`) capable of smoothly handling 300+ installed visual sources and ISF effects.
  - **Dual FX Preset Serialization (`PresetModels.kt`, `PresetGridTabs.kt`)**: Full serialization of both `fxSlot1` and `fxSlot2` in `DeckPresetDto`, paired with redesigned FX tab control panels for Slot 1, Slot 2, and Feedback. Optional `fxSlot2 = null` default preserves 100% backward compatibility for existing presets.
  - **Bundled Creative ISF Shaders (`default_filters/`)**: Shipped default filters including `3d_elevation.fs` (unified 3D plane projection across Tri-Axial, Cube Cage, and Hex-Planar modes), `bloom.fs` (multi-pass bloom highlights and Gaussian diffusion), `feedback.fs` (full ISF feedback loop with spatial transformation, hue shift, chroma aberration, blur & mode blend), `feedback_trails.fs` (persistent motion trails), `glitch.fs` (scanline dislocation and chromatic aberration), and `mirror.fs` (coordinate space folding).
- **Rationale**:
  - Unlocks rich creative post-processing combinations (e.g. Invert/Posterize in Slot 1 followed by Bloom/Glitch in Slot 2) prior to entering feedback.
  - Zero draw call / zero memory overhead when slots are bypassed or Dry/Wet is $0.0$.
  - Strictly adheres to VIDVOX ISF 2.0 specifications and Liquid LSD's zero-heap real-time performance guidelines.

## Phase 2.3: ISF Mixer Transitions & Fallback Architecture (`ISFTransitionRegistry.kt`, `ISFFilter.kt`, `Mixer.kt`, `Renderer.kt`, `ShaderPickerPopup.kt`, `MixerMonitorPanel.kt`, `PresetGridPanel.kt`, `PresetModels.kt`, `PresetManager.kt`, `WebPresetSerializer.kt`)

- **Decision**: Implement extensible ISF-based crossfader transitions while maintaining seamless fallback to the built-in non-ISF mixer:
  - **ISF Transition Engine (`ISFTransitionRegistry.kt`, `ISFFilter.kt`)**: Implemented ISF transition shader loading for 2-image transitions accepting `startImage` (Deck A), `endImage` (Deck B), and `progress` ($0.0 \dots 1.0$).
  - **Fallback Non-ISF Mixer (`Mixer.kt`, `Renderer.kt`)**: When no ISF transition is selected (`transitionFilter == null`), rendering falls back to `mixerShader` (`mixer.frag`) with built-in blend modes (`ADD`, `SCREEN`, `MULT`, `MAX`, `XFADE`).
  - **Composite Pipeline Integrity (`Renderer.kt`)**: Active transition shaders render into an intermediate `blendFBO`, which is then composited in `mixer.frag` with Deck BG (`uTexBG`), channel level multipliers (`uLevelA`, `uLevelB`, `uLevelBG`), master bloom, and master alpha.
  - **Shader Picker Integration (`ShaderPickerPopup.kt`)**: Extended `ShaderPickerPopup` with `PickerType.MIXER_TRANSITION` for category filtering, fuzzy search, and detaching transitions back to default blend modes.
  - **Modulatable Transition Parameters (`PresetGridPanel.kt`, `MixerMonitorPanel.kt`)**: Transition parameters (e.g. wipe direction, softness, glitch intensity) automatically register in the Preset Grid Mix tab for LFO, audio reactivity, and MIDI modulation.
  - **Session Serialization (`PresetModels.kt`, `PresetManager.kt`, `WebPresetSerializer.kt`)**: Added `transitionSlot: FXSlotDto?` to `SessionStateDto` for preset persistence and web client state synchronization.
  - **Bundled Transitions (`default_transitions/`)**: Shipped bundled transition shaders: `linear_crossfade.fs`, `wipe_horizontal.fs`, `wipe_vertical.fs`, `radial_wipe.fs`, `glitch_transition.fs`, `luma_wipe.fs`, and `zoom_fade.fs`.
- **Rationale**:
  - Completes Phase 2 of the Interoperability Roadmap by standardizing visual sources, dual FX slots, and mixer transitions on the open ISF specification.
  - Guarantees 100% backward compatibility and zero overhead when custom transition shaders are not in use.

## Phase 2.4: Mixxx-Style ISF Library Management System, Directory Scanner, Live Reload & Preferences Pane (`ISFDirectoryModels.kt`, `ISFDirectoryManager.kt`, `ISFScanner.kt`, `ISFLibraryRegistry.kt`, `ISFFileWatcher.kt`, `SettingsPanel.kt`, Registries)

- **Decision**: Implement a robust, cross-platform library management system for ISF assets (generators, filters, transitions) modeled after Mixxx:
  - **Platform-Standard Default Locations (`ISFDirectoryManager.kt`)**: Pre-populates default search paths for macOS (`/Library/Graphics/ISF/`, `~/Library/Graphics/ISF/`), Windows (`%ProgramData%\ISF\`, `%LOCALAPPDATA%\ISF\`), Linux (`/usr/share/isf/`, `/usr/local/share/isf/`, `$XDG_DATA_HOME/isf/`), and internal application bundle assets.
  - **Path Expansion & Variable Resolution**: Automatically expands `~`, Windows `%ENV_VAR%`, and Unix `$ENV_VAR` / `${ENV_VAR}` variables into absolute canonical paths.
  - **Directory Lifecycle & Status Tracking (`DirectoryStatus`)**: Evaluates directory availability (`Active`, `Missing`, `Unreadable`). Disconnected or missing external drives are marked as `Missing` and retained in configuration without being purged.
  - **Persistent JSON Configuration**: Serializes user directory paths and active toggle states into `library/isf_directories.json`.
  - **Asynchronous Scanner & Safe Header Parser (`ISFScanner.kt`, `ISFLibraryRegistry.kt`)**: Recursively traverses enabled and active directories for `.fs`, `.isf`, and `.frag` shaders with graceful exception handling for malformed or truncated JSON headers.
  - **Precedence Collision Resolution**: Resolves unique shader ID conflicts deterministically based on source origin priority: `Custom` (4) > `UserStandard` (3) > `SystemStandard` (2) > `BuiltIn` (1).
  - **Cross-Platform File Watcher & Live Reload (`ISFFileWatcher.kt`)**: Implements background directory monitoring via Java NIO `WatchService` with a 250ms debounce mechanism to prevent compiler errors during external editor saves.
  - **Mixxx-Style Preferences UI Pane (`SettingsPanel.kt`)**: Built the **Shader Locations** settings pane displaying origin badges, enable/disable checkboxes, drive status badges, "Add Folder", "Remove Folder" (with built-in path protection), and "Rescan Now" controls.
- **Rationale**:
  - Unlocks professional VJ asset organization, allowing artists to manage external shader collections and SSD libraries seamlessly.
  - Guarantees zero-allocation performance on the audio thread and non-blocking background scanning for Thread 0.

## 2D View Canvas Edge Wrap Modes (`view2d.frag`, `Deck.kt`, `Renderer.kt`, `PresetModels.kt`, `PresetGridTabs.kt`)

- **Decision**: Introduce canvas edge wrap modes to the 2D View transformation pipeline, defaulting to seamless Mirror Repeat:
  - **Wrap Modes in `view2d.frag` (`uWrapMode`)**:
    - Mode 0 (**Mirror**, default): Evaluates `1.0 - abs(mod(uv, 2.0) - 1.0)`. Reflects coordinates smoothly across tile boundaries as a continuous triangle wave, eliminating rectangular boundaries and corner cutoffs entirely.
    - Mode 1 (**Repeat**): Standard periodic tiling via `fract(uv)`.
    - Mode 2 (**Clamp**): Stretches edge pixels via `clamp(uv, 0.0, 1.0)`.
    - Mode 3 (**Border**): Legacy behavior outputting transparent black (`vec4(0.0)`) for out-of-bounds coordinates.
  - **Parameter Integration & Serialization (`Deck.kt`, `PresetModels.kt`)**: Added `viewWrapMode` ($0 \dots 3$) to `Deck`, exposed in the `View` subgroup of `PresetGridTabs`, and serialized in `viewParameters` DTOs.
- **Rationale**:
  - Resolves the issue where zooming out (`View > Zoom < 1.0`) shrunk the canvas into an isolated small rectangle floating in black.
  - Resolves the issue where in-plane rotation (`View > Rotate Z`) produced spinning rectangle corners and black edge cutoffs.
  - Allows outward-radiating feedback and downstream FX to expand and fill the entire screen seamlessly, even when the initial source is zoomed out or rotated.


