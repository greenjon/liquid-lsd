## [Unreleased]

### Sync Macro Knob Editor FX Tabs with Performance View (`MacroPanel.kt`, `docs/user_guide/macros_and_rack.md`)
- **Removed stale FX1 / FX2 / MFX tabs from Classic MACROS editor**: The Macro Knob Editor (Column 3 `[ MACROS ]` tab) was still showing three old shared-bank tabs (`FX1`, `FX2`, `MFX`) that no longer match the independent per-deck FX architecture. These have been replaced with the five independent tabs that mirror the Performance Matrix exactly.
- **Added A FX / B FX / BG FX / PV FX / MST FX tabs**: Each maps directly to its `MacroEngine` constant (`DECK_A_FX`, `DECK_B_FX`, `DECK_BG_FX`, `DECK_PV_FX`, `MASTER_FX`) and resolves the correct `FxChain` from the Mixer (`deck.fxChain` for decks, `masterFxBank.activeChain` for Master).
- **Removed multi-chain tab picker from FX Rack view**: Per-deck FX chains are single chains — there is no chain 1/2/3 picker. `drawFxRackView` now accepts `FxChain` directly and routes Super Link sync through `FxMacroSync.syncDeckFx()` (deck tabs) or `syncChain()` (MST FX).
- **Preview monitor follows FX tabs**: Selecting `A FX` shows Deck A's output; `MST FX` shows the master FBO, matching what you'd expect from the corresponding Performance Matrix row.

### Fix Performance Matrix ImGui ID Conflict on Deck FX Mode (`PerformanceMatrixPanel.kt`, `DECISIONS.md`)
- **Resolved Conflicting Widget IDs in Deck FX Mode**: When a deck row was toggled to `FX` mode via `[ SRC | FX ]` (assigning its `bankId` to `DECK_A_FX`), its header drop area collided with the dedicated FX row's drop area on `LIVE CONSOLE` (which was also focused on Deck A FX), triggering Dear ImGui's ID conflict overlay (`2 visible items with conflicting ID: ##perf_fx_drop_deck_a_fx`).
- **Disambiguated Deck Drop vs FX Drop Targets**: `isDeckRow` is now evaluated first and kept strictly distinct from `isFxChainRow`, ensuring deck rows preserve their dual `.lsdfxchain` and deck patch drop handling with deck-specific tags (`##perf_deck_drop_${startRow}_$dropTag`).
- **Unique Row Index Qualification**: Drop target areas and slot link buttons now include the row/startRow index in their ImGui IDs (`##perf_fx_drop_${startRow}_${bankId}`, `##perf_deck_drop_${startRow}_$dropTag`, `##perf_fx_link_${bankId}_${rowIdx}_$slotIdx`), guaranteeing 100% collision-free rendering even when duplicate banks or targets are visible.

### Persist Workspace View Mode & Default to Performance View (`AppPreferences.kt`, `AppPreferencesStore.kt`, `Main.kt`, `PreferencesDefaultsTest.kt`, `DECISIONS.md`)
- **Default to Performance View for New Users**: `AppPreferences.workspaceMode` now defaults to `UITheme.WorkspaceMode.RACK` (Performance View 4×4 macro matrix) for new users, presenting the performance-oriented macro console upon first launch.
- **Persist View Mode Across Shutdown/Startup**: `AppPreferencesStore` now reads and writes `workspaceMode` (and `performanceMatrixTab`) to `lsd-preferences.properties`. Toggling views via `F4`, the titlebar `[ CLASSIC | PERF ]` pill, or the `View` menu is immediately persisted and faithfully restored on next app launch.
- **Save Preferences on Clean Shutdown**: Added an explicit `AppPreferencesStore.savePreferences()` call during application exit in `Main.kt` cleanup to guarantee that whatever view mode or UI preference was active at shutdown is saved to disk.

### Per-Deck Insert FX & Flexible Performance FX Architecture (`Deck.kt`, `Renderer.kt`, `Mixer.kt`, `MacroEngine.kt`, `FxMacroSync.kt`, `FXItemApplier.kt`, `PresetModels.kt`, `SessionSerializer.kt`, `PerformanceMatrixPanel.kt`, `ParametersTabs.kt`, `docs/user_guide/macros_and_rack.md`, `ARCHITECTURE.md`, `DECISIONS.md`)
- **Per-Deck Insert FX Architecture**: Each deck (A, B, BG, PV) now owns its own dedicated 3-slot `FxChain` (`deck.fxChain`), breaking the 2-effect bottleneck across decks. Effects are applied directly to each deck before mixer composite passes with zero-overhead GPU bypass when wet $\le 0$ or slots are inactive.
- **Dedicated Macro Banks for Every Deck FX**: Added canonical macro banks `DECK_A_FX`, `DECK_B_FX`, `DECK_BG_FX`, and `DECK_PV_FX`, maintaining Super Knob (wet/dry master) and 3 Metaknobs (mapped to active effect slots) with automated bidirectional sync and slot link toggles (`[S1]`, `[S2]`, `[S3]`).
- **LIVE CONSOLE Row 4 Target Switcher (Option A)**: Row 4 of LIVE CONSOLE features a dynamic target switcher (`[ A ] [ B ] [ BG ] [ PV ] [ MST ]`) with accent color badges, live preset picker popup (`[ Preset ▾ ]`), and instant bypass/resync, allowing quick single-row control of any deck or master effect chain.
- **In-Row [ SRC | FX ] Mode Toggle (Option B)**: Every deck row header in the Performance Matrix now includes a `[ SRC | FX ]` mode toggle. Performers can flip any deck row between generator macro control and that deck's insert FX macros on the fly, with bypass and resync buttons appearing dynamically in FX mode.
- **Dedicated `ALL FX` Performance Matrix Tab (Option C)**: Added a new `ALL FX` tab to the Performance Matrix displaying Deck A FX, Deck B FX, Deck BG FX, and Master FX simultaneously (16 knobs total) for multi-channel hands-on tactile manipulation.
- **Parameters Panel FX Subtab**: Added an `[FX]` subtab to each deck's parameter group in Classic Mode (`ParametersTabs`), exposing the full 3-slot rack, effect pickers, drag-and-drop `.lsdfx` loading, and parameter sliders directly alongside generator parameters.
- **Drag-and-Drop Preset Loading**: Supports dragging `.lsdfxchain` preset files directly onto Deck rows, FX rows, or Parameters tabs to load full chains with zero configuration.
- **Full Preset & Session Serialization**: Per-deck `FxChain` configurations serialize into `.lsd` session files and `.lsddeck` presets, with backwards-compatible migration for legacy sessions that used shared `fxBank1`/`fxBank2`.

### Live Binding Value Knob in Binding Inspector (`MacroBindingInspector.kt`, `ParametersRenderer.kt`, `docs/user_guide/macros_and_rack.md`, `DECISIONS.md`)
- **Visual Feedback for Target Values**: Added a rotary meter knob to the second row of each target binding in `MacroBindingInspector` (rendered using `ParametersRenderer.drawKnobMeter`, positioned to the left of Min/Max, Link Mode, and Curve).
- **Real-Time Response Curve Feedback**: As the performer adjusts the macro knob from 0 to 1, the binding meter knob immediately visualizes the exact output value produced by that binding's Min, Max, Curve, and Link Mode (such as a Triangle/ramp curve sweeping 0 → 1 → 0).
- **Tooltip Telemetry**: Hovering over the binding meter knob displays a tooltip with the live evaluated value, macro input value, and target range.

### Clean Up Legacy FX Routing & Direct Deck Insert FX Controls (`Deck.kt`, `Mixer.kt`, `PerformanceMatrixPanel.kt`, `ParametersTabs.kt`, `BrowserActionToolbar.kt`, `FXBrowserPanel.kt`, `ARCHITECTURE.md`, `DECISIONS.md`)
- **Direct Deck Row Controls in Performance Matrix**: Replaced the dormant `[FX1]` and `[FX2]` routing buttons on Deck rows in `PerformanceMatrixPanel` with direct `[BYPASS / FX ON]` and `[Resync]` controls that operate on the deck's own dedicated `FxChain`. The controls are always accessible at the right wing of deck rows without requiring mode switching.
- **Removed Dead Context Menus**: Removed `drawFxRoutingContextMenu` and associated dead routing menu items, MIDI learn, and OSC learn targets for `FxRouting`.
- **Cleaned Up `Deck.kt` & `Mixer.kt`**: Removed legacy routing fields (`fxBank1`, `fxBank2`, `fxRouting`, `fxRoutingDefault`, and `assignedFxBank`) from `Deck.kt`, and removed legacy routing assignments from `Mixer.kt`'s initialization.
- **Parameters Tabs Subgroup Cleanup**: Removed the unused "FX Route" parameter row and conditional "FX Send Level" row from `drawDeckViewSubgroup` in `ParametersTabs.kt`.
- **Browser Panel Direct Deck Targeting**: Updated `BrowserActionToolbar.kt` and `FXBrowserPanel.kt` to target each deck's own 3-slot `FxChain` directly (`deck.applyFxSlot`, `deck.applyFxChain`, `deck.fxSlots`, `deck.toFxSlotDto`, `deck.toFxChainDto`) instead of relying on `deck.assignedFxBank`.

### Mixxx-Style Visual Link Buttons & Multi-Parameter FX Metaknob Linking (`LinkModeButton.kt`, `MacroBindingInspector.kt`, `FxMetaBinding.kt`, `ISFFilter.kt`, `FXChainMacroStrip.kt`, `ParametersTabs.kt`, `PresetModels.kt`, `FxChain.kt`, `FxMetaBindingTest.kt`, `docs/user_guide/macros_and_rack.md`)
- **Intuitive Visual Transfer Curve Buttons**: Replaced text dropdowns with compact vector glyph buttons rendered via `ImDrawList`, displaying the exact transfer curve directly on the button face: `[  /  ]` (Full), `[ / ‾ ]` (First Half), `[ _ / ]` (Second Half), `[ /\ ]` (Triangle Peak), `[ \/ ]` (Bipolar Center-0), and `[  —  ]` (Unlinked).
- **Dynamic Inversion (`[±]`)**: Adjacent invert toggle dynamically flips the transfer curve geometry and highlights in Electric Cyan when active.
- **Multi-Parameter FX Metaknob Linking**: An ISF effect slot's Metaknob can now drive multiple parameters of the shader simultaneously, with independent link modes and inversion per parameter (e.g. Cutoff on First Half, Resonance on Triangle Peak, Wet/Delay on Second Half).
- **Focus Mode & Parameter List Controls**: One-click visual link buttons appear directly beside parameter sliders in FX Focus Mode (`FXChainMacroStrip`) and in the expanded slot accordion's parameter menus (`ParametersTabs`).
- **Full Preset Serialization & Backwards Compatibility**: `FXSlotDto` serializes `metaBindings` list while preserving `metaBinding` for 100% backwards compatibility with legacy presets.

### Performance Matrix Row Reshuffle: Master/Transitions Split, Deck BG on Live Console, Deck PV on Master & FX (`PerformanceMatrixPanel.kt`, `DECISIONS.md`, `docs/user_guide/macros_and_rack.md`)
- **`MASTER & FX` reordered to Master, Transitions, FX Sends, Deck PV**: The crossfader and crossfader-time (Fade Speed) controls now live on the **Master** row alongside the composite alpha/master-level knobs; the transition picker and queue prev/next controls now live on the **Transitions** row, which is no longer 4 dead unbound knobs. Both were previously stuck together on `LIVE CONSOLE`'s single combined "Master / Transitions" row.
- **Removed the dedicated `MASTER FX` row**: it drove the exact same `masterFxBank` that `LIVE CONSOLE`'s FX row already reaches via its `[MFX]` bank selector — a straight duplicate, not a distinct signal path. Master FX is now reached from `LIVE CONSOLE` only.
- **`LIVE CONSOLE` reordered to Deck A, Deck B, Deck BG, FX**: gained a full Deck BG row in the slot the old combined Master/Transitions row vacated.
- **`MASTER & FX` gained a full Deck PV row**: same generator badge, preset combo, eject, randomize, preview badge, and FX send toggles `LIVE QUAD` already shows for Deck PV. `LIVE QUAD` itself is unchanged this pass, but is now a clear "fold into the other tabs" candidate since Deck A/B/BG/PV each live on 2-3 tabs.

### Performance Matrix: Binding Inspector Moved to the Macros Tab; Learn Now Auto-Opens It (`PerformanceMatrixPanel.kt`, `ParametersState.kt`, `RackUnit.kt`, `RackDisclosureTest.kt`, `docs/user_guide/macros_and_rack.md`)
- **Removed the Bay disclosure tier**: The Modular Rack's per-row chevron now toggles only **Faceplate ↔ Deep Edit** (`ParametersState.DisclosureLevel` dropped `BAY`) instead of cycling through three tiers. The full Target Bindings Inspector (rename, target list, Min/Max/Curve/Invert/Enabled) that used to live in the standalone Bay tier has been removed from Performance Mode entirely — it duplicated Classic mode's Column 3 `[ MACROS ]` tab and forced scrolling past Parameters/Properties to reach it.
- **Inline Learn now jumps to the Macros tab**: Pressing a selected knob's inline `[Learn]` button in Performance Mode arms parameter-bind Learn *and* automatically switches Column 3 to `[ MACROS ]`, navigating to the matching deck/bank tab (`PerformanceMatrixPanel.navigateMacroPanelTo`) so the Binding Inspector opens already pointed at that knob — no manual mode switch or re-selecting the knob required.

### Performance Matrix Right-Aligned Row Titles & Elevated Knobs (`PerformanceMatrixPanel.kt`, `docs/user_guide/macros_and_rack.md`, `DECISIONS.md`)
- **Right-Aligned Group Titles**: Moved row group titles (e.g. `DECK A`, `FX: FX Bank 1`, `TRANSITIONS`) from being centered across the row box to the right side above the right-wing UI elements (`[FX1][FX2]`, `[BYPASS][Resync]`), right before the `[Collapse]` and chevron buttons.
- **Elevated Knob Cluster**: Removing the row title from the center column allows the 4-knob cluster to start higher up inside the row box (`boxTopY + boxPad`), freeing ~24px of vertical clearance.
- **Generous Vertical Headroom**: With the title shifted to the right wing, the rotary knobs, labels, `Val: 0.00` readouts, and inline `[Learn]` buttons fit comfortably without vertical cramping.

### Performance Matrix Bay View Direct Knob Selection & Inline Learn (`PerformanceMatrixPanel.kt`, `MacroKnobWidget.kt`, `MacroBindingInspector.kt`, `docs/user_guide/macros_and_rack.md`)
- **Direct UI Knob Selection**: In Modular Rack Bay view, clicking any knob directly in the 4-knob UI selects it for binding inspection. The selected knob is framed with an electric cyan focus card (`#1AB0EB` border and subtle fill), glowing rim, and cyan label.
- **Value Readout (`Val: 0.00`)**: Displays the active numerical value directly below the knob label in the 4-knob UI when the module's bay is open.
- **Inline Parameter Learn Button**: When a module is expanded, the selected knob renders a compact `[Learn]` / `[Cancel]` button directly below its value readout. Performers can arm parameter binding right from the knob face without searching through sub-panels.
- **Deduplicated Bay Inspector**: Removed the redundant 4-knob selectable text list, `KNOB` badge, and duplicate value readout from the Bay inspector, focusing the inspector on the target bindings list and renaming.
- **Faceplate Collapse Button**: Added a direct `[Collapse]` button next to the chevron in the row header so performers can fold the bay back up directly from the row.

### Performance Matrix Vertical Space Optimization & Side-Wing Layout (`PerformanceMatrixPanel.kt`, `docs/user_guide/macros_and_rack.md`)
- **Vertical Space Reclaim**: Removed the separate top header bar from Deck rows (Deck A, Deck B, Deck BG, Deck PV) and the FX row, reclaiming ~31px of vertical height per row and allowing larger, comfortably proportioned rotary knobs without screen crowding.
- **Side-Wing Layout for Deck Rows**: Items from each deck row's top bar have been relocated into flanking side wings around the knobs:
  - **Left Wing**: Generator badge, searchable preset dropdown combo, eject button (`Icons.EJECT`), randomize dice button (`Icons.DICES`), and queue navigation (`< N/Total >` for A/B/BG, or `PREVIEW` focus badge for PV).
  - **Right Wing**: Send routing buttons `[FX1]` and `[FX2]` with full MIDI/OSC Learn support and context menus.
- **Side-Wing Layout for FX Row**: Relocated bank switcher (`[FX1][FX2][MFX]`) and chain switcher (`[C1][C2][C3]`) into the left wing, and bypass (`[BYPASS]`/`[FX ON]`) and `[Resync]` buttons into the right wing.
- **Clustered Knob Spacing & Column Alignment**: Rotary macro knobs are now placed closer together in a focused center cluster rather than spanning wide across the full screen width, with uniform column alignment preserved across all 4 rows in the matrix.
- **Compact Accordion Height**: Reduced `compactRowH` to 175f to give expanded modular rack bays significantly more vertical room.

### Unified Modular Rack: 3-Tier Accordion for the Performance Matrix (`PerformanceMatrixPanel.kt`, `rack/RackUnit.kt`, `ParametersState.kt`, `UIManager.kt`, `MenuBar.kt`, `DECISIONS.md`, `docs/user_guide/macros_and_rack.md`)
- **Chevron disclosure on every row**: Each row group in the 4×4 matrix (Deck A/B/BG/PV, the FX row, Transitions, Master, FX Sends, Master FX) now has a chevron button cycling **Faceplate → Bay → Deep Edit → Faceplate**, without leaving Performance Mode. While any row is expanded, every other still-collapsed row is hidden from the grid entirely (not just left the same size), so the expanded row and its Bay/Deep Edit panel get the freed screen space; collapsing back (or expanding a different row in Solo mode) restores the full 4×4 grid.
- **Deep Edit column headers & side-by-side layout**: Deep Edit's parameter grid now shows the same VAL/MIDI/LFO/SEQ/AUD column headers (and column-visibility kebab menu) Classic mode's Column 1 has, via `ParametersPanel.drawColumnHeaders` reused verbatim, and lays the parameter grid and the per-parameter Properties detail editor out side-by-side — like Classic mode's Columns 1 & 2 — instead of stacked vertically.
- **Bay (Tier 2)**: Opens a scrollable panel below the grid with that row's 4 knobs as a list and the same `MacroBindingInspector` Classic mode's Column 3 MACROS tab uses — rename, arm parameter-bind Learn, and edit Min/Max/Curve/Invert/Enabled, all without switching modes.
- **Deep Edit (Tier 3)**: For Deck rows, the FX row / Master FX, and Transitions/Master, adds the full parameter grid (`ParametersTabs.drawSectionTabs`/`drawDeckGroupContent`/`drawFxBankGroupContent`/`drawMixerGroupContent`) plus the per-parameter CV detail editor (`PropertiesPanel.draw`) below the Bay — the same LFO period/phase/morph/hold/slew, MIDI, SEQ, and AUD controls Classic mode's Parameters/Properties columns show, reused verbatim. Binding a macro knob to an LFO's period now works entirely from Performance Mode. (FX Sends doesn't have Deep Edit content of its own — its per-deck send levels are already reachable via each deck's own Deep Edit.)
- **Disclosure state persists across restarts**: which rows are in Bay/Deep Edit is saved (`UITheme.rackExpandedModules`) alongside the Solo/Multi setting, so your rack layout is exactly as you left it next launch.
- **Solo / Multi accordion**: A `[ SOLO | MULTI ]` toolbar toggle (persisted) controls whether expanding one module auto-collapses the others; a `Collapse All` button and matching View-menu items are also available.
- **Learn-mode pinning**: A module with one of its own knobs armed for Macro Learn is exempt from auto-collapse, and a persistent "Learning: ‹name› — Esc to cancel" indicator stays visible in the toolbar regardless of which module's Bay is open.
- **Esc priority stack**: cancels an armed Macro Learn first; otherwise collapses every expanded Rack module back to Faceplate; guarded so it never fires while a text field has focus.
- Expand/collapse never refocuses the FX row's FX1/FX2/MFX selection or re-runs the Super Knob/Metaknob sync — it's strictly a display change.

### Performance Matrix Deck Controls Parity, Randomize Dice (A, B, BG, PV, ALL) & Fade Speed Control (`PerformanceMatrixPanel.kt`, `DECISIONS.md`)
- **Randomization Dice for All Decks**: Added `[ ALL 🎲 ]` button at the right edge of the performance matrix tab strip for instant whole-rig randomization with undo snapshot, plus dedicated dice buttons (`🎲`) in each deck row header (Deck A, Deck B, Deck BG, Deck PV) for individual deck randomization.
- **Header Controls Parity for Decks BG and PV**: Enabled full header controls for all 4 decks in `LIVE QUAD` (and `LIVE CONSOLE`), including generator source badge, searchable preset dropdown combo (`presetSearchBG`, `presetSearchPV`), eject button (`⏏`) routing through `UIManager.triggerDeckEject(deck)`, and FX routing toggles (`[FX1][FX2]`) with MIDI/OSC learn.
- **Queue Navigation Parity**: Deck BG header connects directly to `BgQueueManager` with `< N/Total >` navigation and MIDI/OSC learn, while Deck PV provides a preview deck indicator and focus button.
- **Crossfader Fade Speed Control**: Added an interactive duration badge widget (`${mixer.xfadeSpeed.baseValue}s`) directly between the `[ AUTO ]` button and Transition Picker button on the Master / Transitions row header. Supports drag scrubbing, mouse scroll wheel, and right-click context menu with quick presets (`0.5s`, `1.0s`, `2.0s`, `4.0s`, `8.0s`), MIDI Learn, and OSC Learn.

### Mixer Shader & Engine Consolidation (`Mixer.kt`, `mixer.frag`, `Renderer.kt`)
- **Removed Legacy Mixer Bloom**: Removed the legacy 9-tap blur bloom from `Mixer` and `mixer.frag` (as well as `web/shaders/mixer.frag`). Bloom is now handled exclusively via the modular ISF `"bloom"` filter or transitions.
- **Consolidated Master Opacity into `masterLevel`**: Retired `mixer.masterAlpha` and `uAlpha` uniform, merging master opacity and gain strictly into `mixer.masterLevel` (`uMasterLevel`). Legacy sessions with `masterAlpha` automatically migrate seamlessly to `masterLevel`.
- **Removed Deprecated Mix Mode**: Deleted `Mixer.mode` and the legacy GLSL branching (`uMode`, `uBalance`, `uTex2`) in `mixer.frag`. Transitions are now 100% shader-driven through the ISF transition framework.
- **Parameters UI Cleanup**: Cleaned up the Mixer controls in `ParametersTabs.kt` and `ValueParamSection.kt` to reflect `Master Level` and eliminate the deprecated mix mode and bloom rows.

### Audio Hardware 5×2 Oscilloscope Grid & VU Meter Consolidation (`AudioEnginePanel.kt`, `docs/developer/ui.md`)
- **Deduplicated Stereo VU Meter**: Removed the redundant duplicate input meter from the left controls column, centralizing stereo input peak monitoring at the top of the right monitoring column.
- **Unified 5×2 Oscilloscope Grid**: Standardized all oscilloscopes to uniform 50 px height and organized them into a 5×2 grid with Raw Buffer on row 0 left directly beside Beat Sine (Oscillator) on row 0 right, followed by the four RMS energy bands (Full Mix, Bass Band, Mid Band, High Band) in the left column paired with their corresponding Flux transient bands (Full Mix Transient, Kick Transient, Snare Transient, Hat Transient) in the right column.

### Reorganized Preferences Category Ordering (`PreferencesPanel.kt`, `UIThemeTest.kt`, `docs/developer/ui.md`)
- **Streamlined Preferences Sidebar Flow**: Reordered the left navigation tabs in Preferences (`PreferencesPanel.kt`) to: **General**, **Shader Locations**, **Video & Display**, **Audio Hardware**, **Tempo & Sync**, **MIDI Controls**, **OSC Controls**, **Keyboard Shortcuts**, and **Web Broadcast**. This places critical assets and display/audio configuration directly beneath General settings.

### Live Console Preset Quick-Search & Header Controls MIDI/OSC Learn (`PerformanceMatrixPanel.kt`, `MidiMappingManager.kt`, `MidiPreferencesPanel.kt`, `docs/user_guide/macros_and_rack.md`)
- **Preset Quick-Search in Deck A/B Headers**: Added an integrated quick-search input field at the top of Deck A and Deck B preset dropdown combos in `LIVE CONSOLE`. Automatically focuses on open, filters scanned presets in real-time, and supports pressing `Escape` to instantly clear the search filter.
- **Right-Click MIDI & OSC Learn on Header Controls**: Right-clicking any interactive header control in `LIVE CONSOLE` now opens a context menu with direct **Learn MIDI**, **Learn OSC**, and clear mapping actions:
  - **Crossfader Track**: Learn MIDI / OSC for `Mixer/crossfade`, reset to center (`0.0`), and snap to Deck A or Deck B.
  - **Deck A & B Snap Badges (`[ A ]`, `[ B ]`)**: Learn MIDI for instant hardware snap triggers (`Global/snapDeckA`, `Global/snapDeckB`).
  - **Auto-Fade Button (`[ AUTO ]`)**: Learn MIDI for crossfader auto-fade triggering (`Global/autoFade`).
  - **PlayQueue Prev (`<`) & Next (`>`)**: Learn MIDI (`Global/queuePrev`, `Global/queueNext`) and Learn OSC (`Mixer/queuePrev`, `Mixer/queueNext`).
  - **Transition Queue Prev (`<`) & Next (`>`)**: Learn MIDI (`Global/transQueuePrev`, `Global/transQueueNext`) and Learn OSC (`Mixer/transQueuePrev`, `Mixer/transQueueNext`).
  - **FX Routing Toggles (`[FX1]`, `[FX2]`)**: Learn MIDI and Learn OSC for deck FX send routing (`$deckLabel/View/FxRouting`), with direct route switching menu items.
- **Visual Armed Indicators & Tooltip Mapping Feedback**: Header controls armed for MIDI learn display a cyan highlight border. Active MIDI channel/CC bindings are automatically appended to control tooltips.
- **New Global Actions in MIDI Subsystem**: Added `Global/autoFade`, `Global/snapDeckA`, and `Global/snapDeckB` with high-edge detection in `MidiMappingManager.processGlobalMidiEvents()` and registered them in the `MidiPreferencesPanel` global actions table.

### Live Console Master & Transitions Performance Header Controls (`PerformanceMatrixPanel.kt`, `docs/user_guide/macros_and_rack.md`, `DECISIONS.md`)
- **Integrated Horizontal Crossfader**: Embedded a full-featured zero-centered bipolar crossfader slider (`-1.0` Deck A to `+1.0` Deck B) into Row 4 (`MASTER / TRANSITIONS`) of the `LIVE CONSOLE` performance matrix tab. Supports left-click-drag scrubbing, mouse scroll wheel with fine/coarse modifiers, middle-click center reset to `0.0`, tick marks at endpoints and quarters, and a live amber indicator dot tracking active CV modulation and automated transitions.
- **Deck A & B Instant Snap Badges**: Added color-coded `[ A ]` (cyan/blue) and `[ B ]` (orange/amber) snap buttons at the ends of the crossfader track. Clicking either badge disarms Auto-VJ, halts active transitions, and immediately snaps the crossfader to -1.0 or +1.0.
- **Auto-Fade Trigger**: Added a dedicated `[ AUTO ]` / `[ FADING ]` button that smoothly fades the crossfader to the opposite deck over the configured `mixer.xfadeSpeed` duration, with instant click-to-cancel support.
- **Transition Picker & Modified Indicator**: Added a transition selector button with settings icon displaying the active transition shader or blend mode name, dirty unsaved marker (`*`), and instant access to `ShaderPickerPopup`. Presets (`.lsdtrans`) and shaders (`.fs`/`.isf`) can also be dropped directly onto the header or slider track.
- **Transition Queue Navigation**: Added Prev (`<`) and Next (`>`) navigation buttons with a status counter (`N/Total` or `"--"`) directly stepping through the active `TransitionQueueManager`.

### Performance Matrix Tab Streamlining: 4-Deck LIVE QUAD & Redundant Tab Removal (`PerformanceMatrixPanel.kt`, `docs/user_guide/macros_and_rack.md`)
- **True 4-Deck LIVE QUAD**: Replaced the 4th row (Transitions) in the `LIVE QUAD` performance tab with Deck PV (Preview), so `LIVE QUAD` now presents all four visual generation decks (Deck A, Deck B, Deck BG, Deck PV) with 4 macro knobs each.
- **Pruned Redundant Tabs**: Removed the legacy `DUAL DECKS` and `PREP & BG` tabs from the Performance Matrix. The Performance view is now streamlined to three purpose-built tabs:
  1. **`LIVE QUAD`**: 4 visual decks (A, B, BG, PV) with 16 knobs total.
  2. **`MASTER & FX`**: Dedicated 8-knob Transition and 8-knob Master performance surface.
  3. **`LIVE CONSOLE`**: Curated live show console pairing Deck A, Deck B, active FX Bank (with in-place chain selection & Super link buttons), and Master/Transitions.

### Fix FX Macro Knob Controls & Performance Console Button Responsiveness (`PerformanceMatrixPanel.kt`, `MacroPanel.kt`, `MacroEngine.kt`, `FxMacroSync.kt`, `Icons.kt`, `MacroEngineTest.kt`, `FontInspectorTest.kt`)
- **Performance Console FX Row Click Occlusion (`PerformanceMatrixPanel.kt`)**: Constrained the FX chain drag-and-drop hit target (`##perf_fx_drop_target`) to the title header area (`afterTitleY - boxTopY`) instead of spanning the entire group box. Prevents the invisible button from capturing mouse events and unblocks the `[FX1][FX2][MFX]`, `[C1][C2][C3]`, `[BYPASS]`, and `[Resync]` buttons and rotary knobs from being click-locked.
- **Clickable Chain Link / Unlink Icons (`PerformanceMatrixPanel.kt`, `Icons.kt`)**: Added standard Lucide `LINK` (`\ue102`) and `UNLINK` (`\ue19c`) chain-link icon buttons directly to the left of each FX slot knob (Slots 1–3) in the Performance Console's FX row. Users can now click the icon directly in Performance Mode to toggle a slot's link to the Super Knob without navigating to the Macro panel.
- **Effect Name Retention for Linked Slots (`FxMacroSync.kt`)**: Replaced the previous `(linked)` label override on macro controls with the slot's actual effect name (e.g. `Colorize`, `Luma Key`, or `FX1`). Knobs now consistently display the effect name under the knob face while the link icon indicates and controls link status.
- **Rotary Knobs in Macro Panel FX Tabs (`MacroPanel.kt`)**: Replaced the compact slider strip in `drawFxRackView` with rotary macro knobs via `drawMacroGrid()`, providing consistent rotary knobs across all tabs with mouse drag, scroll wheel fine-adjust, middle-click reset, selection, and MIDI learn.
- **Chain Switcher & Super Link Toggles in Macro Panel (`MacroPanel.kt`)**: Integrated the `[ Chain 1 ] [ Chain 2 ] [ Chain 3 ]` selector buttons and compact `[x] Slot 1 [x] Slot 2 [x] Slot 3` Super Knob Link checkboxes directly above the rotary knobs in Column 3's FX tabs.
- **Linked FX Slot Visual Knob Tracking (`MacroEngine.kt`)**: Added allocation-free synchronization in `MacroEngine.tick()` so linked FX slot knobs track the active chain's Super Knob / slot Metaknob value in memory, ensuring linked knobs visibly rotate in unison when turning the Super Knob.

### 4-Knob Canonical Bank Standardization, Master Alpha Defaults & MST Macro Tab (`MacroEngine.kt`, `MacroPanel.kt`, `PerformanceMatrixPanel.kt`)
- **4-Knob Standardization Across All Canonical Banks (`MacroEngine.kt`)**: Standardized all canonical macro banks (`DECK_A`, `DECK_B`, `DECK_BG`, `DECK_PV`, `TRANS`, `MASTER`, `FX_BANK_1`, `FX_BANK_2`, `FX_SENDS`, `MASTER_FX`) to exactly 4 knobs. Transitions and Master banks no longer allocate redundant 8 knobs, ensuring uniform 4-knob single-row layout across the entire interface. Older sessions with 8-knob Transition/Master banks automatically clamp to the first 4 knobs upon load.
- **Master Composite Alpha Smart Defaults (`MacroEngine.kt`)**: Initialized default bindings for the `MASTER` macro bank targeting the 4 core composite layers: `ALPHA A` (`Mixer/levelA`), `ALPHA B` (`Mixer/levelB`), `ALPHA BG` (`Mixer/levelBG`), and `MASTER` (`Mixer/masterLevel`).
- **FX Sends Smart Defaults (`MacroEngine.kt`)**: Initialized default bindings for `FX_SENDS` targeting `SEND A`, `SEND B`, `SEND BG`, and `SEND PV` (`$deck/FXChain/DryWet`).
- **Dedicated `MST` Tab in Column 3 Macro Panel (`MacroPanel.kt`)**: Added the `MST` tab button to the macro deck selector (`[ A | B | BG | PV | TRANS | MST | FX1 | FX2 | MFX ]`). Selecting `TRANS` switches to `Mixer` parameters with `TRANS` sub-tab; selecting `MST` switches to `Mixer` parameters with `CTRL` sub-tab. The preview monitor displays `PREVIEW: MASTER` for MST and `PREVIEW: TRANSITION` for TRANS.
- **Performance Matrix `MASTER & FX` Tab Redesign (`PerformanceMatrixPanel.kt`)**: Replaced the previous 2-row duplicate groups with 4 focused, single-row (4-knob) group boxes: `TRANSITIONS` (4 knobs), `MASTER` (4 knobs), `FX SENDS` (4 knobs), and `MASTER FX` (4 knobs).

### Macro Bank Knobs 5-8 Retirement & Clamping on Session Restore (`SessionSerializer.kt`, `SessionStateTest.kt`)
- **Deck Macro Bank Sizing Clamping (`SessionSerializer.kt`)**: When restoring session state (`loadSession`), canonical macro banks are now strictly clamped to `MacroEngine.defaultKnobCountFor(canonicalId)` (4 knobs for Decks A/B/BG/PV, FX banks, and FX sends; 8 knobs for Transitions and Master). Prevents legacy sessions with pre-FX-migration 8-knob deck banks from resurrecting orphaned Knobs 5–8 in Classic Mode's Column 3 MACROS panel.
- **Legacy Session Sanitization**: Cleaned up legacy 8-knob records and residual hardcoded bindings on generator banks in `library/last_session.json` to 4 clean, unbound knobs.

### Fix Font Atlas Build Timing, Corrupted Glyph Fallback & Wayland Platform Logging (`UITheme.kt`, `UIManager.kt`, `Main.kt`, `WindowFrameController.kt`)
- **Synchronous Font Atlas Building (`UITheme.kt`)**: Added immediate `atlas.build()` inside `UITheme.loadFonts(io)` right after adding font memory buffers and glyph ranges. Previously, `atlas.build()` was deferred until the first frame render call. Under ZGC (`-XX:+UseZGC`), the temporary JNI critical array pointers (`MAIN_RANGES`, `ICON_RANGE`) released during `addFontFromMemoryTTF` were subject to memory unpinning/relocation during startup asset and shader loading, leaving Dear ImGui with corrupted glyph ranges that failed to bake Latin characters and caused all UI text to fall back to `?` (Inter) or `◆` (JetBrains Mono).
- **Font Unload Lifecycle Cleanup (`UITheme.kt`, `UIManager.kt`)**: Added `UITheme.unloadFonts()` called during `UIManager.dispose()` to reset `isLoaded = false` when destroying the ImGui context, preventing cross-test state leakage and dangling native pointer crashes (`ImFont::GetFontBaked`) when multiple ImGui contexts run within the same JVM.
- **Wayland Platform Guards (`Main.kt`, `WindowFrameController.kt`)**: Guarded `glfwSetWindowIcon` and `glfwGetWindowPos` / `glfwSetWindowPos` calls when running on Wayland (`GLFW_PLATFORM_WAYLAND`), eliminating repeated `GLFW_FEATURE_UNAVAILABLE` errors logged on Linux Wayland environments.

### Fix OSC Engine Lifecycle Thread Safety & Intermittent Test Failures (`OscEngine.kt`, `OscEngineTest.kt`)
- **Lifecycle Synchronization**: Added `@Synchronized` to `OscEngine.start()` and `OscEngine.stop()`, marked channel and receiver thread references as `@Volatile`, and joined the background receiver thread (`receiverThread?.join(1000L)`) on `stop()` to prevent unjoined, interrupted threads from racing and closing channels created in subsequent sessions.
- **Dedicated Channel Parameter**: Passed the newly bound `DatagramChannel` directly into `receiveLoop(channel: DatagramChannel)` instead of resolving through the global mutable singleton field, preventing race conditions during rapid start/stop sequences.
- **Test Resilience & Retries**: Enhanced loopback test cases in `OscEngineTest.kt` to retry sending UDP datagrams within the `waitUntil` poll loop and added descriptive assertions, eliminating intermittent `Condition not met within 2000ms` failures caused by packet drops or receiver startup timing.

### Hard Consolidation of Transitions & "Elite 8" Curated Transition Suite (`ISFTransitionRegistry.kt`, `default_transitions/`, `Mixer.kt`, `FileSystemManager.kt`, `build.gradle.kts`)
- **Complete Legacy Deprecation**: Removed 10 legacy, simplistic mathematical blend modes and untextured geometric wipes (`additive_blend`, `screen_blend`, `multiply_blend`, `max_blend`, `wipe_horizontal`, `wipe_vertical`, `radial_wipe`, `luma_wipe`, `glitch_transition`, `zoom_fade`).
- **Curated "Elite 8" Transition Suite**:
  - **`linear_crossfade.fs`**: Pristine S-curve dissolve with selectable Perceptual Cosine, Linear, and Equal Power curve modes to prevent mid-crossfade perceptual luminance dips.
  - **`luminous_flash.fs`**: Exponential Gaussian exposure overdrive and bloom flare with tunable color temperature (-1.0 icy strobe to +1.0 warm tungsten) and spread, designed for high-energy drop transitions.
  - **`film_burn.fs`**: 35mm celluloid burn featuring multi-octave procedural fractal noise erosion with intense chromatic glowing combustion contours (Fiery Ember, Electric Violet, Acid Green).
  - **`noise_dissolve.fs`**: Multi-octave domain-warped fractal noise erosion with soft feathered contours and subtle chromatic fringing.
  - **`liquid_displacement.fs`**: Interactive cross-deck vector morphing where Deck A and Deck B dynamically displace each other's UV coordinates based on luminance gradient fields.
  - **`kinetic_zoom.fs`**: High-speed camera crash zoom with multi-tap radial velocity streak blur, edge chromatic dispersion, and exponential acceleration curves.
  - **`vortex_swirl.fs`**: Gravitational singularity twisting Deck A into a spiraling vortex at the frame center, peaking at midpoint, and unwinding into Deck B with chromatic flare.
  - **`cyber_datamosh.fs`**: Video compression breakdown emulating digital I-frame / P-frame corruption, macroblock displacement, horizontal sync tear, and chromatic shear.
- **Factory Transition Presets & Playlists**: Shipped 8 curated `.lsdtrans` presets and the default `festival_elite.lsdtransplay` playlist for `TransitionQueueManager` and AutoVJ.
- **Engine Streamlining**: Cleaned up legacy mode-to-transition switching in `Mixer.kt` and automated transition asset packaging in `build.gradle.kts` and `FileSystemManager.kt`.

### Premier 8-Generator Procedural ISF v2.0 Suite (`VisualSourceRegistry.kt`, `SourceDocRegistry.kt`, `default_sources/`, `library/sources/`, `web/sync_manifest.json`, `VisualSourceManifestTest.kt`)
- **Curated 8-Generator Core Lineup**: Established an elite, high-variety catalogue of built-in pure ISF v2.0 generators:
  - **`mandala`** (Harmonic Curves): 4-arm Lissajous harmonic curve engine with 300+ curated recipes.
  - **`dynamic_spiral`** (Particle Dynamics): High-performance particle streak dynamics with wave frequencies and shear.
  - **`icosa_h3`** (Sacred Polyhedra): Native 3D $H_3$ Coxeter raymarcher with duality morph and stellation CSG.
  - **`domain_warp_fluid`** (Organic Fluid): Multi-scale domain-warped fBm fluid simulation with curl noise, dynamic vorticity, surface specular normals, and 5 color palettes.
  - **`gyroid_hyperspace`** (3D Minimal Surfaces): Native 3D raymarcher rendering Triply Periodic Minimal Surfaces (TPMS) with continuous morphing between Gyroid, Schwarz P, and Neovius minimal surfaces, volumetric internal radiance, and camera flight.
  - **`celestial_engine`** (Sacred Geometry & Op-Art): Multi-symmetry sacred geometry and op-art generator combining Flower of Life overlapping circles, concentric harmonic rings, phase twisting, and moiré fringes.
  - **`hyper_slice`** (Higher Dimensions): Raymarched 3D cross-section MRI scan through 4D 120-cell (600 vertices) and 600-cell (120 vertices) polytopes using the $H_4$ Coxeter reflection group with 4D hyper-rotations ($XW, YW, ZW$) and Wythoff facet morphing.
  - **`chladni_cymatics`** (Physics & Cymatics): Physical 2D acoustic plate resonance simulation computing standing wave nodal harmonics across square and circular boundaries with particle accumulation physics and antinode fluid inversion.
- **Canonical Source Packaging Architecture**: Migrated bundled sources to canonical `src/main/resources/default_sources/`, syncing automatically into `library/sources/` on clean setups via `VisualSourceRegistry.ensureDefaultSources()`.
- **Web Subsystem Parity**: Transpiled all 5 new generators to GLSL ES 3.0 WebGL2 shaders (`web/shaders/`) via `sync_web.py --apply` and added to `web/sync_manifest.json`.
- **Documentation & Manifest Verification**: Registered all source descriptions and parameter tooltips in `SourceDocRegistry.kt`, updated `docs/user_guide/visual_sources.md`, and added comprehensive multi-source validation in `VisualSourceManifestTest.kt`.

### Two-Tier FX Macro Knobs (Chain Super Knob + Effect Metaknobs) & ISF Auto-Bind Engine (`FxMetaBinding.kt`, `ISFAutoBindEngine.kt`, `ISFFilter.kt`, `ISFModels.kt`, `FxChain.kt`, `FxBank.kt`, `FXChainMacroStrip.kt`, `MacroPanel.kt`, `ParametersTabs.kt`, `AssetType.kt`, `FileSystemManager.kt`, `FXBrowserPanel.kt`, `ISFAutoBindEngineTest.kt`, `FxChainSuperKnobTest.kt`)
- **ISF Auto-Bind Engine**: Every ISF filter now gets a sensible default Metaknob binding automatically, even shaders never hand-configured — resolved via user override (cached by shader content hash), then a curated table for bundled filters, then a heuristic over the shader's declared `INPUTS` (the new `IDENTITY` property, semantic name matching with automatic exponential curves for time/frequency-like names, single/normalized-float fallback, and a Dry/Wet safety net as a last resort).
- **Chain Super Knob with Soft-Takeover Linking**: Each FX chain now has a Super Knob that drives its 3 slots' Metaknobs when linked. Relinking a slot (or loading a new filter into an already-linked one) arms soft-takeover instead of snapping the Metaknob to the Super Knob's current position — it only starts following once the Super Knob's movement reaches it, mirroring the app's existing MIDI/OSC hardware pickup behavior.
- **FX Rack Performance Strip**: New Traktor/Mixxx-style strip (Super Knob + per-slot Metaknob/Link/Focus controls) in the Parameters panel above each chain's slot accordion, and as a dedicated view replacing the generic knob grid on Column 3's FX1/FX2/MFX tabs. Single FX Focus Mode swaps the 3 knobs to a focused effect's own top parameters. Right-click a Metaknob to rebind it.
- **Library Browser FX Bank Support**: Added `.lsdfxbank` listing/filtering/loading (`Load to FX1/FX2/MFX`) to the FX browser, plus a fix for stock/single/chain FX "Load to Deck" menus that were previously limited to Chain 1 only — they now nest a Chain 1/2/3 selector.
- **Known tradeoff**: hardware MIDI/OSC bindings on FX Rack knobs are per-path (consistent with the rest of the app), so a physical knob bound to a slot's Metaknob in Group Mode does not carry over to that slot's Focus Mode parameter rows — see `docs/user_guide/macros_and_rack.md`.

### 3-Chain FX Banks & Multi-Chain Architecture (`FxBank.kt`, `FxChain.kt`, `Deck.kt`, `Mixer.kt`, `Renderer.kt`, `ParametersTabs.kt`, `ParametersState.kt`, `FXPresetModels.kt`, `PresetRepository.kt`, `FileSystemManager.kt`, `FxBankTest.kt`)
- **Traktor / Mixxx-Style 3-Chain Banks**: Re-architected FX banks (`FX1`, `FX2`, and `MFX`) so each bank hosts 3 serial FX chains (`chain1` → `chain2` → `chain3`), with each chain hosting 3 ISF filter slots. Provides up to 9 effects per bank and up to 27 total effects across the system.
- **Horizontal Subtabs UI**: Added horizontal subtabs `[ Chain 1 ] [ Chain 2 ] [ Chain 3 ]` within the Parameters panel for each FX bank. Each chain provides independent bypass, chain wet/dry (`$bankLabel/C$chainNum/DryWet`), and individual slot accordion sections (`$bankLabel/C$chainNum/FX$slotNum/*`).
- **Master FX (MFX) Parity**: Replaced raw `masterFxSlots` with a full `FxBank("MFX")`, giving the master output full 3-chain capability with identical UI, parameter naming, and preset saving.
- **4-Buffer Ping-Pong GPU Architecture**: Transitioned offscreen rendering to a 4-buffer architecture per deck/mixer (inner scratch pair `fxPingFBO`/`fxPongFBO` for intra-chain slot passes, and outer alternating pair `fxChainOutFBO`/`fxBankOutFBO` for inter-chain progression). Eliminates GPU driver texture feedback loop aliasing.
- **Modulatable Deck Routing**: Added discrete modulatable parameter `View/FxRouting` (0 = None, 1 = FX1, 2 = FX2) to each deck under the View subtab, allowing dynamic CV and MIDI modulation of FX routing.
- **Preset Persistence (`.lsdfxbank`)**: Added `FXBankDto` for bank presets saved in `library/fx_banks/`, added `dryWet` to `FXChainDto`, and extended `PresetRepository` with asynchronous bank save/load methods.

### FX Bank Parameter Path & Dead Code Cleanup (`Mixer.kt`, `MacroEngine.kt`, `Deck.kt`, `FXPresetModels.kt`, `FXBrowserPanel.kt`, `BrowserActionToolbar.kt`, `FXItemApplier.kt`, `FxBankTest.kt`)
- **FX1/FX2 Parameter Path Parity**: Renamed `Mixer`'s internal bank labels from `"Bank 1"`/`"Bank 2"` to `"FX1"`/`"FX2"`, aligning registered parameter paths (`FX1/DryWet`, `FX2/DryWet`) with UI tab labels and `MacroEngine` lookup keys. Updated `MacroEngine.canonicalIdForDeckLabel` to map `"FX1"` and `"FX2"` directly to `FX_BANK_1` and `FX_BANK_2`.
- **Deck FX Dead Code Removal**: Removed legacy slot mutation methods (`toFxSlotDto`, `applyFxSlot`, `clearFxSlot`, `applyFxChain`, `toFxChainDto`) from `Deck.kt` that were allocating throwaway arrays on unassigned decks. Callers in browser toolbars and item appliers now target `deck.assignedFxBank` directly.
- **DTO Documentation Correction**: Corrected stale doc comments in `FXPresetModels.kt` describing `FXChainDto` as 4 slots to reflect the actual 3-slot count.
- **Regression Tests**: Added `testFxBankLabelsAndParameterPaths` and `testMacroEngineCanonicalIdForFxBanks` in `FxBankTest.kt`.

### Parameters Panel FX Tabs Layout & Label Alignment (`ParametersRenderer.kt`, `ParametersTabs.kt`)
- **Indentation for FX Tabs**: Added `PARAM_INDENT` (6f) indentation to `drawFxBankGroupContent` (FX1, FX2 tabs) and `drawMixerFxTab` (MFX tab), ensuring header rows (`FX CHAIN`, `MASTER FX CHAIN`), slot rows, and parameter rows match the standard visual alignment of Deck A, B, BG, PV, and Mixer tabs.
- **Fixed Parameter Label Positioning Over VAL Dial**: In `ParametersRenderer.drawParamRow`, replaced `ImGui.sameLine(cursorStartX)` with `ImGui.setCursorPosX(cursorStartX)`. When rendering unindented or root-aligned rows (`cursorStartX == 0.0f`), ImGui previously fell back to default item spacing after the row's options button, shifting "Wet/Dry" and filter parameter names one column to the right on top of the VAL dial. Parameter names now reliably render in the label column.

### Direct MIDI Control for Modulator Variables & In-Situ MIDI Learn (`ParametersState.kt`, `MidiMappingManager.kt`, `BeatDivisionSlider.kt`, `CustomRangeSlider.kt`, `MidiPreferencesPanel.kt`, `performance_controls.md`)
- **Direct Modulator Variable Addressing**: Extended `MidiMappingManager` to support hierarchical parameter paths (`<parameterPath>:mod/<modulatorIndex>/<propertyName>`, e.g. `Deck A/geometry/zoom:mod/0/subdivision`), enabling direct hardware control of internal LFO speeds/subdivisions, depths, min/max bounds, asymmetry, morphing, and sample & hold without using Macro knobs.
- **Dynamic Modulator Resolution & Allocation-Free Dispatch**: Updated `ResolvedMidiBinding` to delegate reading and writing dynamically to `ModulatorPropertyAccessor` via `getCurrentValue()` and `applyValue()`. Seamlessly handles continuous CCs, relative rotary deltas (Binary Offset, Signed Bit, Two's Complement), discrete buttons/pads (Momentary, Toggle, Step +/-), exponential slew smoothing ($0 \dots 250\,\text{ms}$), and soft takeover (pickup).
- **In-Situ Right-Click "Learn MIDI" Menus**: Added right-click context menus to LFO and modulator sliders (`BeatDivisionSlider`, `CustomRangeSlider`) alongside OSC learn. Armed sliders display a pulsing cyan/blue outline and contextual status tooltip while awaiting controller input.
- **Preferences UI Label Formatting**: The Parameter Mappings table in `MidiPreferencesPanel` now formats modulator paths into clean, readable labels (e.g. `Deck A/geometry/zoom [LFO 1 Speed]`) with full path inspection on hover.
- **Soft Takeover Verification Alignment**: Aligned `isSoftTakeoverActive` in both `MidiMappingManager` and `OscMappingManager` to evaluate pickup state correctly, preventing false "Takeover Synced" displays prior to physical pickup.

### Direct OSC Control for Modulator Variables & In-Situ OSC Learn (`ModulatorPropertyAccessor.kt`, `OscMappingManager.kt`, `OscLearnState.kt`, `BeatDivisionSlider.kt`, `CustomRangeSlider.kt`, `OscPreferencesPanel.kt`, `performance_controls.md`)
- **Direct Modulator Variable Addressing**: Added hierarchical OSC parameter path addressing (`<parameterPath>:mod/<modulatorIndex>/<propertyName>`, e.g. `Deck A/geometry/zoom:mod/0/subdivision`) allowing incoming OSC messages to directly modulate internal LFO speeds/periods, depths, phases, morphing, asymmetry/duty cycle, sample & hold, DC offset, and envelope followers without consuming Macro knobs.
- **Shared Accessor & Mutator (`ModulatorPropertyAccessor`)**: Created an allocation-free utility object to read and write `CvModulator` properties safely and format user-friendly labels. Replaced `MacroEngine`'s private property applicator with this shared implementation.
- **Dynamic Slew & Soft Takeover for Modulators**: Extended `OscMappingManager` target resolution to apply full min/max range scaling, value inversion, exponential slew smoothing ($0 \dots 250\,\text{ms}$), and soft takeover (pickup) to modulator properties dynamically each frame.
- **In-Situ Right-Click "Learn OSC" Menus**: Added right-click context menus on all LFO and modulator sliders (`BeatDivisionSlider`, `CustomRangeSlider`) as well as base value sliders (`ValueParamSection`), allowing immediate click-to-bind OSC learning directly on the control surface. Armed sliders display a pulsing amber outline and contextual status tooltip.
- **Preferences UI Label Formatting**: The Address Mappings table in `OscPreferencesPanel` now formats modulator paths into clean, readable labels (e.g. `Deck A/geometry/zoom [LFO 1 Speed]`) with full path inspection on hover.

### Parameters VAL Cell Modulator Mute Toggle (`ParametersRenderer.kt`, `ParametersPanel.kt`, `ParametersValMuteTest.kt`, `modulation.md`)
- **Toggle Row Modulation via VAL Cell**: Right-clicking (mouse button 1) or middle-clicking (mouse button 2) the VAL cell now toggles mute (bypass) for all modulators on that parameter row instead of destructively clearing them via `param.reset()`.
- **Safe Reset on Empty Modulator Rows**: If no modulators are active on the parameter row, middle-clicking the VAL cell safely resets the base value to factory default.
- **Row Menu Mute/Unmute**: Added "Mute all modulators" / "Unmute all modulators" to the row context menu (`row_menu_$paramKey`).
- **Tooltips & Documentation**: Updated tooltips on the VAL column header and VAL cells, and updated the User Guide shortcuts table to document row-level modulation mute toggling.

### Architectural Compliance & ImGui Widget Hardening (`ValueParamSection.kt`, `Lfo1Section.kt`, `Lfo2Section.kt`, `SeqSection.kt`, `PlaylistManagerTest.kt`, `DECISIONS.md`)
- **Zero-Allocation ImGui Modulator & Parameter Dropdowns**: Pre-allocated reusable `ImInt` singleton fields and static/cached label arrays in `ValueParamSection`, `Lfo1Section`, `Lfo2Section`, and `SeqSection`, eliminating per-frame heap allocations when viewing parameter combos, clock unit selectors, and LFO modulation modes in the Properties panel.
- **Playlist Asset Confinement Test Alignment**: Updated `PlaylistManagerTest` to construct temporary test playlists within `FileSystemManager.getPlaylistsRoot()`, conforming with the path isolation security rules enforced by `isManagedAssetPath`.

### FX Playlists, Live FX Queues & Unified FX Browser (`FXBrowserPanel.kt`, `FXPlaylistEditorPanel.kt`, `FXQueueActionsPanel.kt`, `FXBgQueueActionsPanel.kt`, `FXQueueManager.kt`, `FXBgQueueManager.kt`, `FXItemApplier.kt`, `FXPresetModels.kt`, `LibraryPanel.kt`)
- **Removed Per-Preset FX (Breaking)**: `DeckPresetDto` no longer carries `fxSlot1..4`/`fxChainEnabled`/`fxChainDryWet`. Visual presets (`.lsd`) never touch a deck's FX state on load or save — FX is now exclusively a per-deck concern driven by manual editing, the FX Browser, FX Playlists, and Live FX Queues. Old `.lsd`/session files with embedded FX keys load fine; the keys are silently ignored.
- **Unified FX Browser**: Merged `FXPresetListPanel` and `FXChainListPanel` into a single `FXBrowserPanel` presenting ISF stock filters, saved single presets (.lsdfx), and multi-slot FX chains (.lsdfxchain) with tier badges and filter menu (All / Stock / Singles / Chains). Stock filters only support loading directly to decks, while saved presets and chains can be added to playlists and live queues.
- **FX Playlists (`.lsdfxplay`)**: Introduced curated FX playlist sequences supported by `FXPlaylistDto`, `PresetRepository`, `FileSystemManager`, and `FXPlaylistEditorPanel`. Supports drag-and-drop insertion, reordering, double-click application, and context menus.
- **Deterministic FX Application (`FXItemApplier`)**: Resolves both `.lsdfx` single presets and `.lsdfxchain` multi-slot chains deterministically via `Deck.applyFxChain`, guaranteeing reproducible 4-slot FX state rather than arbitrary vacant-slot allocation.
- **Live FX Queues (A/B and BG)**: Added `FXQueueManager` and `FXBgQueueManager` with volatile RAM queues, repeat, shuffle, history back-stepping, and UI controls in `FXQueueActionsPanel` and `FXBgQueueActionsPanel`. Column 3 (BG) and Column 4 (A/B) in the Library dock swap to FX queues when in FX mode.
- **Export Queue to Playlist**: Enabled instant exporting of live FX queues (A/B and BG) directly to new `.lsdfxplay` playlists.
- **Fixed FX-Mode Keyboard Shortcuts**: `1`–`4` (load to deck) and `Q`/`Shift+Q` (add to queue) previously routed through the visual-preset managers even while browsing `[ FX ]` mode, silently misapplying FX files as `.lsd` presets or queuing them on the wrong manager. `LibraryPanel`'s shortcut handler now dispatches through `BrowserActionToolbar.handleDeckLoad` and `FXQueueManager`/`FXBgQueueManager` when the Library is in FX view mode, matching the toolbar buttons' existing extension-aware behavior.

### Performance Mode 4×4 Macro Matrix — Rack Retired (`PerformanceMatrixPanel.kt`, `MacroEngine.kt`, `MacroKnobWidget.kt`, `UIManager.kt`, `MenuBar.kt`, `AppPreferences.kt`, `UITheme.kt`, `SessionSerializer.kt`)
- **Retired the Modular Video Rack**: The experimental 19" rack chassis UI (patch cables, rear panel, rack unit faceplates) has been removed entirely. All rack module files (`RackPanel`, `RackManager`, `RackPipeline`, `RackPatchBay`, `RackUnit`, `RackUnitType`, `RackChassisRenderer`, etc.) are deleted.
- **Introduced Performance Mode (`PerformanceMatrixPanel`)**: `F4` now switches between Classic Deck View and a new **4×4 Macro Knob Matrix** spanning Columns 1 & 2, with the Mixer and Library dock remaining visible. 16 knobs are arranged in 4 rows × 4 columns, color-coded by deck (blue / orange / amber / mint / violet / crimson). Four tabs — **LIVE QUAD**, **DUAL DECKS**, **PREP & BG**, **MASTER & FX** — offer different row-to-bank mappings for different performance contexts.
- **Performance Panel is read-only**: Dragging a knob adjusts its `MacroControl.value` live; right-click is a no-op. Editing (Learn Mode, binding inspector, Min/Max/Curve) stays in Classic mode's Column 3 `[ MACROS ]` tab. Both surfaces read and write the same underlying `MacroEngine` banks.
- **New `MASTER` canonical bank**: `MacroEngine` now registers six always-resident banks (`DECK_A`, `DECK_B`, `DECK_BG`, `DECK_PV`, `TRANS`, `MASTER`). `canonicalIdForDeckLabel("Master")` routes correctly to `MASTER` instead of falling through to `TRANS`. All six banks start blank — no default bindings.
- **`MacroKnobWidget` accent color**: Added `accentColor: FloatArray?` parameter so the Performance Matrix can tint each row's arc fill, indicator, and hover ring with its deck color. Passing `null` preserves the existing amber style in the Classic `[ MACROS ]` view.
- **Active tab persisted**: `AppPreferences.performanceMatrixTab: Int` stores which of the 4 tabs was last active and restores it on next launch.
- **Knob sizing is height-bounded**: `diameter = min(availW/4 − pad, availH/4 − labelH − pad)` ensures all 16 knobs and their labels remain visible at any window aspect ratio.
- **Toolbar & menu labels updated**: The `[ RACK ]` pill is now `[ PERF ]`; the View menu entry is "Performance Mode"; the F4 tooltip no longer mentions "19\" Modular Video Rack".
- **`SessionSerializer` is now the sole bank registrar**: `RackManager.populateFromSession` was the only other site that called `MacroEngine.registerBank`; it's now deleted. `SessionSerializer.loadSession` and `startEmpty` already handled all six canonical banks independently, so no behavior change occurs.
- **`MacroEngine.unitParameterResolver` removed**: The rack-scoped parameter resolver hook is gone. `resolveControls` always uses `ParameterResolver.findParameterByPath` directly.

### Minimum System Requirements & Startup Diagnostics (`Main.kt`, `README.md`, `docs/getting_started.md`, `ARCHITECTURE.md`)
- **Documented Minimum & Recommended System Requirements**: Formalized comprehensive hardware and software requirements across documentation (`README.md`, `docs/getting_started.md`, `ARCHITECTURE.md`). Explicitly documented that OpenGL 3.3 Core Profile hardware support is mandatory. Added prominent alerts clarifying that legacy architectures like Intel Core 2 Duo / Core 2 Quad and legacy Intel GMA graphics (GMA 3000/X3100/X4500) lack OpenGL 3.3 Core Profile capabilities and cannot run Liquid LSD.
- **Enhanced GLFW Error Diagnostics on Window Creation**: Registered a global `GLFWErrorCallback` before initialization in `Main.kt`. If window creation fails (e.g. on unsupported GPUs or headless environments lacking OpenGL 3.3 Core context creation), the application now queries and displays the exact GLFW error reason along with clear troubleshooting guidance regarding graphics drivers and hardware requirements rather than a generic `Failed to create GLFW window` exception.

### Macro Binding Inspector Navigation & Bound-Parameter Visual Feedback (`MacroBindingInspector.kt`, `ParametersRenderer.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`)
- **Binding target name is now a navigation link**: In the Binding Inspector, clicking the target parameter name (e.g. `Deck A/Mandala/L1`) switches Column 1 directly to that deck and sub-tab. Hover reveals a `→ Go to Deck A → SRC` tooltip.
- **Bound Parameter & Modulator Visual Feedback**: When a parameter or modulator property is bound to a Macro Control, it now displays rich visual affordances across Columns 1 and 2:
  - In the Parameters matrix (Column 1), the row is highlighted with an Electric Cyan left border and tint, with an inline badge (e.g. `[K1]` or `[SW2]`) and cyan text.
  - In the Properties and Slider sections (Column 2), an Electric Cyan bounding box and background highlight frames the entire slider row, the track and dynamic indicator dot glow in cyan, and numeric text boxes are set to read-only with a cyan outline.
  - Hovering over bound rows, labels, sliders, or text inputs displays a contextual tooltip identifying the controlling macro (e.g. `"Locked: Driven by Knob 1 (WARP) [K1]"`).
  - Clicking any bound parameter label, badge, or value cell provides bidirectional jump-to-inspect navigation straight into the Column 3 Macro Inspector.

### Full Screen Video & Monitor Alpha Blend Parity (`Main.kt`, `default_filters/feedback.fs`)
- **Fixed Full Screen vs Monitor Alpha Blend Mismatch**: Changed full screen viewport and secondary window rendering in `Main.kt` from `glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)` to `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` to match ImGui's monitor rendering pipeline. Previously, `GL_ONE` caused non-premultiplied decaying alpha pixels (such as feedback trails) to render at 100% RGB intensity on full screen video without alpha attenuation, appearing drastically heavier and thicker than in confidence monitors.
- **Isotropic Aspect-Ratio Corrected Feedback Zoom & Rotation (`feedback.fs`)**: Added aspect-ratio scaling to `default_filters/feedback.fs` (`float aspect = RENDERSIZE.x / RENDERSIZE.y; uv.x *= aspect; ... uv.x /= aspect;`) so zoom and rotation transformations remain circular and isotropic in non-square viewports (e.g. 16:9).

### Column 3 Header Mode Toggle & Macro Controls Layout (`Column3HeaderToggle.kt`, `UIManager.kt`, `MacroPanel.kt`, `MixerLayout.kt`, `MacroBindingInspector.kt`)
- **6-Column Responsive Macro Panel Grid (`MacroPanel.kt`)**: Re-engineered `MacroPanel` to use a unified 6-column x 2-row layout (`cellW = availW / 6`). The 8 macro knobs occupy columns 1–4 across 2 rows, while the 4 macro switches occupy columns 5–6 as a 2x2 side-by-side block.
- **Macro Binding Inspector Row Layout Optimization (`MacroBindingInspector.kt`)**: Consolidated the response curve dropdown ("Curve") onto the same row as travel range bounds ("Min", "Max") and direction inversion ("Invert"), streamlining the macro binding inspector controls.
- **Column 3 Mode Toggle Always Visible**: Extracted `Column3HeaderToggle` to draw the `[ MIXER | MACROS ]` header toggle directly at the top of the Column 3 window in `UIManager.kt`, ensuring the mode switch pill is always visible in both `MIXER` and `MACROS` modes as documented in `docs/user_guide/macros_and_rack.md`.
- **Layout Height Calculation Update**: Updated `MixerLayoutCalculator`'s vertical chrome estimation to incorporate the header toggle height so Column 3 layout auto-sizing stays exact in both modes.

### Automated Screen Capture, Startup CLI Flags & Isolated UI Lab (`CliArgs.kt`, `ScreenshotCapture.kt`, `UiLabPanel.kt`, `Main.kt`, `UIManager.kt`, `build.gradle.kts`)
- **Startup CLI Arguments Parser (`CliArgs.kt`)**: Added command-line option parser supporting `--screenshot-ui=<file.png>`, `--screenshot-after-frames=<N>` (default: 5), `--window=<W>x<H>|maximized`, `--no-audio`, `--ui-lab`, `--help`, and `--version`.
- **Automated Frame Capture & Graceful Exit (`ScreenshotCapture.kt`)**: Implemented framebuffer PNG export with STB Image write and scanline vertical flipping. The render loop monitors the frame settle count and automatically captures the window framebuffer and exits cleanly when `--screenshot-ui` is provided.
- **Isolated UI Lab Component Sandbox (`UiLabPanel.kt`)**: Introduced an isolated sandbox environment displaying theme color swatches, Lucide icons catalog, wave shape selectors, custom range sliders, beat division selectors, and status meters.
- **Gradle Automation Tasks (`build.gradle.kts`)**: Added `./gradlew captureResponsiveApp` (1080p workspace capture) and `./gradlew captureUiLab` (720p UI Lab sandbox capture) tasks.
- **Documentation Updates**: Updated developer docs (`docs/developer/cli_and_screenshot_automation.md`), user guides (`docs/user_guide/cli_usage.md`), system architecture (`ARCHITECTURE.md`), decisions (`DECISIONS.md`), and proposal status record (`docs/developer/screen_capture_and_ui_iteration_proposal.md`).

### Modular Video Rack Phase 9: Unit Consolidation & Rack Layout Finalization (`RackUnit.kt`, `RackManager.kt`, `RackFaceplateGrid.kt`, `RackMicroMonitor.kt`, `RackPanel.kt`, `FBO.kt`, `PerformanceStats.kt`, `MenuBar.kt`)
- **One Rack Unit Per Deck, Not Per Pipeline Stage**: Replaced `DeckGeneratorUnit` + up to four `ISFProcessorUnit`s per deck with a single merged `DeckRackUnit` exposing a flattened parameter namespace (generator params unprefixed, FX params as `"FX1/…"`..`"FX4/…"`) — a deck with 4 active FX slots now shows as 1 rack unit instead of 5. Deleted `FeedbackProcessorUnit` entirely (its knobs were already bound to legacy fields no shader reads).
- **Deck BG Now Has a Rack Column**: The rack now shows three deck columns — Deck A, Deck B, and Deck BG — plus the Master unit. Deck PV remains excluded (preview/audition deck, not part of the live composite).
- **New Queue & Staging Master Unit**: A always-present 3U rack unit with condensed Play Queue / BG Queue / Transition Staging transport controls (prev / play-pause / next + "now → next"), wired directly to the existing queue engines you already use in Classic mode — no new queue behavior, just a rack-native view onto it.
- **Confidence Monitors Now Render at a Shared Preview Resolution**: Each unit's monitor downscales its output to 240×135 before display instead of sampling the full-resolution source texture directly, reducing per-monitor GPU cost.
- **New GPU Telemetry in the Menu Bar**: Added an `FBO: N (XMB)` readout next to FPS/CPU/BPM showing live framebuffer count and estimated GPU memory in use.
- **Fixed a Double-Update Bug in Rack Mode**: Deck FX filters and the Background Queue's dip-to-black fade timer were being ticked twice per frame whenever Rack mode was the visible workspace (once by the main render loop, once again by the rack unit's own `update()`). Both now tick exactly once, regardless of workspace mode.

### Linux ARM64 (`aarch64`) Distribution Restoration — Now Shipping on 5 Platforms (`build.gradle.kts`, `.github/workflows/*`, `utils/NativeLibraryLoader.kt`, `Main.kt`)
- **Restored Linux ARM64 Build Target**: Re-enabled native Linux ARM64 (`aarch64`) support across the build system, JNI library extraction, and CI distribution matrix. Liquid LSD Desktop now ships on **5 platforms**: Linux x64, Linux ARM64, macOS x64, macOS ARM64, and Windows x64.
- **Embedded JNI Library Loader (`NativeLibraryLoader.prepareImGuiNatives()`)**: Automatically extracts embedded `libimgui-java64.so` for Linux ARM64 to a temporary runtime folder and configures `System.setProperty("imgui.library.path", ...)` before ImGui context initialization.
- **Native Binary Sourced from a Dedicated Repo, Not Compiled In-Repo**: Upstream `imgui-java` doesn't publish Linux ARM64 natives, so `libimgui-java64.so` is built once per `imgui-java` version in [`imgui-java-natives-linux-arm64`](https://github.com/greenjon/imgui-java-natives-linux-arm64) and CI downloads the matching release asset by pinned `imguiVersion` (`docs/developer/build_arm64_linux.md`) — decoupling the native build from Liquid LSD's much more frequent release cadence.
- **Adoptium JRE 17 `linux-aarch64` Packaging**: Restored `zipLinuxArm` Gradle task with `run-linux-arm.sh` launcher script and Adoptium JRE 17 bundling.
- **5-Platform CI Verification Matrix**: Added `linux-arm64` matrix jobs to `.github/workflows/smoke-test.yml` and `.github/workflows/release.yml` — verified passing end-to-end in production release CI.

### TouchOSC & Open Sound Control (OSC) Integration (`OscCodec.kt`, `OscEngine.kt`, `OscMappingManager.kt`, `OscPreferences.kt`, `OscPreferencesPanel.kt`, `MacroOscBridge.kt`)
- **Pure Kotlin Zero-Dependency OSC 1.0 Codec (`OscCodec`)**: High-performance binary encoder and decoder for OSC messages and bundles supporting 32-bit floats (`f`), integers (`i`), strings (`s`), booleans (`T`/`F`), and multi-argument vectors, with 4-byte alignment padding and big-endian network byte order.
- **Low-Latency UDP Engine & Bidirectional Feedback (`OscEngine`)**: Dedicated background UDP receiver socket (default incoming port 8000), thread-safe lock-free queue to the render thread, live packet sniffer circular buffer, and outgoing UDP feedback socket (default outgoing port 9000) transmitting parameter updates and toggle states back to TouchOSC clients with remote client auto-learning.
- **TouchOSC Mapping Manager (`OscMappingManager`)**: Address-string-keyed mapping engine compatible out of the box with default TouchOSC layouts (`/1/fader1`–`5`, `/1/rotary1`–`4`, `/1/toggle1`–`4`, `/1/push1`–`4`, `/2/xy`) and custom semantic paths, XY pad multi-float unpacking to indexed parameter components, numerical min/max range clamping, invert, exponential slew smoothing ($0 \dots 250$ ms), soft takeover (pickup), and JSON profile persistence in `library/osc/`.
- **Macro OSC Bridge Integration**: Forwarded `/macro/knob/1..8` and `/macro/switch/1..4` OSC paths directly to `MacroOscBridge` with reciprocal state feedback sent out via `OscEngine`.
- **OSC Controls Preferences UI (`OscPreferencesPanel`)**: Added dedicated "OSC Controls" tab in Preferences featuring server enable/port settings, learned remote client status, real-time packet sniffer monitor log, mapping editor table, profile management, and interactive target parameter OSC Learn.

### Rack & Macro Correctness Pass: Pipeline Wiring, Instance-Id Collisions & Zero-Allocation Hardening (`RackUnit.kt`, `RackManager.kt`, `RackPipeline.kt`, `RackPatchBay.kt`, `RackPanel.kt`, `UIManager.kt`, `MacroEngine.kt`, `MacroBindingInspector.kt`, `MacroPanel.kt`)
- **Wired the Rack Pipeline Into the Live Render Loop**: `RackManager.process()` was implemented and unit-tested in Phases 5-8 but never called from any production code path — every rack unit's `lastOutputTexture` stayed `0` forever, so confidence micro-monitors always showed "NO SIGNAL" and rear-panel LEDs never lit. Threaded a `Renderer` reference through `UIManager.render()` -> `drawLayout()` -> `drawAssetManagementLayout()` -> `RackPanel.draw()`, which now calls `rackManager.process(renderer)` once per frame.
- **Built-in Rack Units Are Now Read-Only Monitors, Not Re-Renderers**: `DeckGeneratorUnit`, `ISFProcessorUnit`, `FeedbackProcessorUnit`, and `MixerTransitionUnit` previously re-invoked `Renderer.render()` / `Renderer.renderMixer()` / `ISFFilter.render()` inside `process()`, which would have double-rendered every deck/filter/mixer pass each frame once wired up, corrupting persistent-history ISF filters (e.g. feedback) and doubling GPU cost. They now read the textures Main.kt's normal render pass already computed this frame (`deck.cleanFBO.texture`, `deck.fxFBOs[slot].texture`, `mixer.masterFBO.texture`) instead. See `DECISIONS.md` for the full rationale, including why patch-cable overrides stay visual-only for these built-in units.
- **Fixed Micro-Monitor FBO Aliasing**: Because built-in units no longer write into `RackPipeline`'s shared `stageFboA`/`stageFboB` ping-pong buffers, chained ISF FX stages can no longer overwrite each other's texture before the confidence monitor samples it.
- **Fixed `unitInstanceId` Collisions on Cloned Units**: `DeckGeneratorUnit`, `ISFProcessorUnit`, `FeedbackProcessorUnit`, and `MixerTransitionUnit` used fixed, type-derived ids (`"deck_a_gen"`, `"master_transition"`, etc.) instead of per-instance UUIDs, so adding a second copy of a unit via "Add Unit" silently overwrote the original's registered `MacroBank` and could delete the wrong unit on removal. All four now default to `BaseRackUnit`'s random UUID.
- **Added Missing Rear Jacks**: `Mask/Sidechain In` and `CV Modulation In` were part of the Phase 8 spec but never implemented. Added to every unit type's rear panel; `getRearPorts()` is now cached once per unit instead of rebuilt every frame.
- **Zero-Allocation Fixes in `MacroEngine`**: `tick()` allocated a fresh `ArrayList` every frame to consume trigger-switch resets; `findBindingsTargeting()` (called every frame per rendered parameter row from three UI panels) allocated on every call even when nothing was bound. Both now avoid allocating in the common per-frame case, per `ARCHITECTURE.md`'s zero-allocation render-loop guarantee.
- **Wired Hardware MIDI Learn for Macro Knobs/Switches**: `MidiMappingManager` already dispatched `Macro/knob_N`/`Macro/switch_N` CC mappings, but no UI could ever construct a `MidiLearnTarget.MacroTarget` to create one. Added a "MIDI Learn" button to `MacroBindingInspector`.
- **Reduced Allocations in Rack Patch-Cable Hot Paths**: `RackPipeline.process()` and `RackPatchBay.findCableInputFor()` replaced per-frame lambda closures and `"$unitId:$portId"` string concatenation with indexed loops and a cached `PatchPort.fullId`.
- **Discovered & Documented (not fixed this pass)**: `FeedbackProcessorUnit`'s curated macro knobs are bound to legacy `Deck.fbGain`/`fbDecay`/etc. fields that no shader reads anymore (pre-dates the ISF feedback migration) — made it an honest passthrough rather than leaving knobs that move but do nothing. Full detail and remaining follow-ups (real patch-cable rerouting for built-in units, OSC transport, minor hardening items) tracked in `ROADMAP.md` Milestone 6 "Known issues to revisit" and `DECISIONS.md`.

### Rear Panel & Virtual Patch Cables (`RackPatchBay.kt`, `RackCableRenderer.kt`, `RackRearChassisRenderer.kt`, `RackPanel.kt`, `RackUnit.kt`, `RackPipeline.kt`, `RackManager.kt`, `MenuBar.kt`, `ShortcutManager.kt`, `RackPatchBayTest.kt`)
- **Rear Panel & Virtual Patch Cables (Milestone 6, Phase 8)**:
  - Added dual-faced 180° rack flip toggle (`Tab` key, top toolbar `[ FRONT | REAR ]` toggle, and `View > Flip Rack (Rear Panel)` menu) to expose rear chassis panels.
  - Implemented industrial rear panel design with heatsink ventilation slots, stenciled regulatory / warning labels, AC power inlet receptacle box, and 1/4" phone jacks with metallic hexagonal nuts.
  - Built bidirectional port signaling LEDs: dim cyan for available inputs, dim amber for available outputs, and vibrant illuminated matching color when cabled.
  - Designed `RackCableRenderer` simulating cubic Bézier catenary curve sag with distance-dependent physics, multi-pass rubber sheath rendering (drop shadow, thick outer casing, highlight specular core), and nickel-plated metal plugs with strain reliefs.
  - Created interactive drag-to-patch cabling workflow: left-click jack to extend elastic cable, drop onto target jack to patch; right-click any connected jack to unplug.
  - Built `RackPatchBay` routing engine enforcing directional validity (`OUTPUT` to `INPUT`), avoiding same-unit loops, and handling automatic input override replacement.
  - Integrated `RackPipeline` override resolution: evaluates chained stages top-to-bottom normalled by default, seamlessly routing the connected upstream unit's texture output when patched via virtual cable.
  - Added comprehensive test suite `RackPatchBayTest.kt`.

### Embedded Confidence Micro-Monitors (`RackMicroMonitor.kt`, `RackUnit.kt`, `RackPipeline.kt`, `RackFaceplateGrid.kt`, `RackMicroMonitorTest.kt`)
- **Embedded Confidence Micro-Monitors (Milestone 6, Phase 7)**:
  - Added dedicated hardware-styled confidence micro-monitors (`RackMicroMonitor.kt`) embedded directly into unit faceplates.
  - Render clean, unadulterated 1:1 texture output true to the generated video without artificial CRT scanlines, barrel distortion, or color tinting.
  - Implemented recessed industrial metallic bezel framing with rounded corners and subtle bevel highlights.
  - Enhanced `RackUnit` and `RackPipeline` to track and expose `lastOutputTexture` per stage with zero additional GL draw passes or FBO overhead.
  - Built state-reactive status overlays: active video blitting, deep solid black for powered-off units (`STANDBY`), dimming with amber badge for bypassed units (`BYPASS`), and awaiting signal badges.
  - Integrated micro-monitors across `DeckGeneratorUnit` (Columns 1 & 2), `FeedbackProcessorUnit` (Columns 1 & 2 optical loop monitor), `ISFProcessorUnit` (Columns 1 & 2 FX monitor), `MixerTransitionUnit` (Master Out confidence monitor), and `GenericRackUnit`.
  - Added comprehensive test suite `RackMicroMonitorTest.kt`.

### Per-Unit Macro Curation (`RackUnit.kt`, `RackManager.kt`, `MacroEngine.kt`, `MacroLearnState.kt`, `RackUnitMacroCuration.kt`, `RackFaceplateGrid.kt`, `RackUnitHeaderRail.kt`, `RackChassisRenderer.kt`, `RackPanel.kt`)
- **Per-Unit Macro Curation (Milestone 6, Phase 6)**:
  - Extended `RackUnit` to own a dedicated, local `MacroBank` (0-8 knobs, 0-4 switches) scoped by `unitInstanceId`.
  - Upgraded `MacroEngine` evaluation with `unitParameterResolver` to locally resolve rack unit parameters (`unitInstanceId`/`parameterId`) without name collisions across duplicate unit instances in the rack bay.
  - Implemented unit front panel curated Macro controls row in `RackFaceplateGrid.kt`, exposing active curated knobs and switches directly on the 19" faceplate for instant live access.
  - Added dedicated `[ MACRO ]` toggle button to `RackUnitHeaderRail.kt` and created `RackUnitMacroCuration.kt` curation drawer with tabbed views for Knobs (1-8) and Switches (1-4), dynamic target parameter selection combo, Min/Max range bounds, curve selectors, and inversion toggles.
  - Integrated `MacroLearnState` auto-scoping for per-unit controls and expanded unit chassis height dynamically in `RackChassisRenderer.kt` when the curation drawer is open.
  - Added comprehensive test suite `RackUnitMacroTest.kt`.

### Modular Video Rack Chassis & Slot Layout System (`RackUnit.kt`, `RackPipeline.kt`, `RackManager.kt`, `RackChassisRenderer.kt`, `RackUnitHeaderRail.kt`, `RackFaceplateGrid.kt`, `RackPanel.kt`, `UIManager.kt`, `UITheme.kt`, `MenuBar.kt`, `ShortcutManager.kt`)
- **19" Rack Bay Chassis & Slot Layout (Milestone 6, Phase 5)**:
  - Introduced the standardized **19" Modular Video Rack Bay** container with industrial dark brushed metal aesthetics, metallic rack ears, and screw heads aligned at 1U modular height increments.
  - Implemented quantized modular height standards ($1\text{U} = 72\text{px}$, $2\text{U} = 144\text{px}$, $3\text{U} = 216\text{px}$) along with an ultra-compact $0.5\text{U}$ ($32\text{px}$) collapsed spine mode.
  - Added standardized **Unit Header Rails** (`RackUnitHeaderRail.kt`) with illuminated Power switches, latching Bypass (`BYP`) buttons, Solo (`SOLO`) triggers, unit type badges (`GEN`, `FX`, `MIX`, `UTIL`), inline editable labels, drag/reorder controls, and collapse buttons.
  - Implemented **Grid-Based Faceplate Layout** (`RackFaceplateGrid.kt`) snapping controls across responsive 8-column slots for Generator synth units, Processor FX units, Feedback loops, and Transition mixer units.
  - Built **Normalled Top-Down Signal Flow Pipeline** (`RackPipeline.kt` & `RackManager.kt`) chaining stages sequentially with zero-overhead bypass passthrough (bypassing GL draw calls completely when bypassed), solo overrides, and headless test support.
  - Added non-destructive **Workspace View Mode** (`WorkspaceMode.CLASSIC` vs `WorkspaceMode.RACK`) accessible via top-bar pill button `[ CLASSIC | RACK ]`, `View > Modular Video Rack` menu, and global `F4` keyboard shortcut, with automatic session bridging from active decks and mixer stages.
  - Added unit test suites: `RackUnitTest.kt`, `RackPipelineTest.kt`, and `RackManagerTest.kt`.

### Macro Controls Interactive Learn Mode, Inspector, Serialization & Hardware Integration (`MacroLearnState.kt`, `MacroBindingInspector.kt`, `MacroBankSerializer.kt`, `MacroOscBridge.kt`, `MidiMappingManager.kt`, `SessionSerializer.kt`, `PresetManager.kt`, `PresetRepository.kt`, `ParametersRenderer.kt`, `ValueParamSection.kt`, `PropertiesPanel.kt`, `MacroPanel.kt`)
- **Interactive Learn Mode & Inspector (Phase 3)**:
  - Added `MacroLearnState.kt` coordinating click-to-bind linking between armed macro controls and target parameters or modulator properties.
  - Implemented field-ownership locking with visual badges and one-click navigation from bound parameters in Columns 1 & 2 directly to the Column 3 Binding Inspector.
  - Added `MacroBindingInspector.kt` collapsible accordion drawer in Column 3 with control renaming, switch behavior selection (`TOGGLE`, `MOMENTARY`, `TRIGGER`), 1-to-many binding management, Min/Max bounds, response curve selection (`LINEAR`, `EXPONENTIAL`, `LOGARITHMIC`, `S_CURVE`, `STEP`), direction inversion, and field-release toggles.
- **Preset Serialization & Hardware MIDI/OSC Integration (Phase 4)**:
  - Added `MacroBankSerializer.kt` supporting standalone `.knobpreset.json` export/import with missing-parameter skipping, and deck-scoped prefix filtering/restoration.
  - Persisted active global macro bank in `SessionStateDto` for full session recovery and added optional deck-scoped `macroBank` to `DeckPresetDto`.
  - Added hardware MIDI CC and note mapping for `Macro/knob_1`..`Macro/knob_8` and `Macro/switch_1`..`Macro/switch_4` in `MidiMappingManager.kt`.
  - Implemented `MacroOscBridge.kt` supporting bidirectional OSC address routing (`/macro/knob/<1..8>` and `/macro/switch/<1..4>`).
  - Added comprehensive test suites: `MacroLearnStateTest.kt`, `MacroBankSerializationTest.kt`, `MacroMidiIntegrationTest.kt`, and `MacroOscBridgeTest.kt`.

### Macro Controls UI & Dual-Mode Column 3 (`MacroKnobWidget.kt`, `MacroPanel.kt`, `UIManager.kt`, `UITheme.kt`, `AppPreferencesStore.kt`, `MacroKnobWidgetTest.kt`)
- **Macro Controls Panel & Rotary Knobs (Phase 2)**:
  - Added custom ImGui rotary knob widget (`MacroKnobWidget.kt`) with sweep math, DAW-style vertical drag scaling, theme hover/active highlight borders, and unit test coverage.
  - Implemented `MacroPanel.kt` featuring a 2x4 knob bank grid, 4 momentary/toggle switches, and an integrated deck preview monitor.
  - Introduced `Column3Mode` (`MIXER` / `MACROS`) header toggle in Column 3 with preferences persistence in `AppPreferencesStore`.

### AppPreferencesStore Extraction (`AppPreferencesStore.kt`, `UITheme.kt`, Panels)
- **Extracted Preferences Persistence**: Extracted preferences loading and saving logic out of `UITheme` into dedicated `AppPreferencesStore` object, decoupling theme styling from disk property persistence and streamlining preferences testability.
- **Updated Panels & Tests**: Refactored all UI panels and unit tests to invoke `AppPreferencesStore.loadPreferences()` and `AppPreferencesStore.savePreferences()` directly.

### Session Serialization Visibility & Test Delegates (`SessionSerializer.kt`, `PresetManager.kt`, `PresetModels.kt`, `SessionStateTest.kt`, `PresetDirtyLoadingTest.kt`)
- **Restored Queue & Session Path Helper Visibility**: Exposed `startEmpty`, `serializeSessionPath`, `resolveSessionPath`, and `resolveRestoredQueue` with `internal` access on `SessionSerializer` and added delegating methods on `PresetManager`.
- **Deck DTO Apply Safety**: Added null-safe fallback when resolving default visual sources during empty deck resets to prevent `NoSuchElementException` when operating on mock deck instances in unit tests.


### ImGui SetCursorPos Un-submitted Bounds & Preferences Child Window Fix (`CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `OscilloscopeDrawer.kt`, `ParametersPanel.kt`, `PreferencesPanel.kt`)
- **Fix ImGui Assertion Crash on Preferences Modal**: Fixed `Dear ImGui Assertion Failed: (0) && "Code uses SetCursorPos()/SetCursorScreenPos() to extend window/parent boundaries."` caused by `ImGui.setCursorScreenPos(...)` positioning layout cursors without a trailing `ImGui.dummy(0f, 0f)` item submission to reset `DC.IsSetPos` before closing child windows.
- **Adjusted Preset Name Scale Child Height**: Increased `##preset_slider_child` height from `46f` to `52f` with `ImGuiWindowFlags.NoScrollbar` to accommodate theme window padding and custom slider heights cleanly.

### Zero-Allocation MIDI Slew & DSP Refactoring (`MidiMappingManager.kt`, `BeatTrackerEngine.kt`, `CvModulator.kt`, `Lfo1Section.kt`, `Lfo2Section.kt`)
- **Unboxed MIDI Slew State**: Moved target and smoothed parameter values directly onto `ResolvedMidiBinding` unboxed fields, eliminating string-keyed map lookups and GC churn on the hot MIDI update path.
- **Beat Tracker & LFO Constants**: Centralized one-pole EMA smoothing factors (`EMA_RETAIN` / `EMA_UPDATE`) in `BeatTrackerEngine` and frame-subdivision upper bounds (`MAX_FRAME_SUBDIVISION`) in `CvModulator`.

### Zero-Allocation ISF Multipass Optimization (`ISFFilter.kt`, `ISFVisualSource.kt`)
- **Pre-Parsed ISF Pass Dimensions & Array Indexing**: Pre-parse pass dimension expressions (`$WIDTH/2.0`, `$HEIGHT`, bare literals) into compiled `DimExpr` structures at load time. Replaced runtime string substitutions, `split()` calls, map lookups, and `Pair` allocations with fast array indexing and zero-allocation in-place ping-pong slot reference swaps during rendering.

### Transition Library UI & Macro Controls Architecture RFC (`LibraryPanel.kt`, `StockTransitionListPanel.kt`, `TransitionPresetListPanel.kt`, `TransitionPlaylistEditorPanel.kt`, `TransitionQueuePanel.kt`, `MixerPanel.kt`, `macro_controls_and_parameter_linking_proposal.md`)
- **Library Panel `[ Trans ]` Mode**:
  - Added `[ Trans ]` toggle to `LibraryPanel.kt`, enabling VJs to browse Stock ISF Transitions, Transition Presets (`.lsdtrans`), Transition Playlists (`.lsdtransplay`), and the Live Transition Queue.
  - Added drag-and-drop targets on the Mixer Panel transition selector button and crossfader track, allowing direct drag-to-apply of `.lsdtrans` presets and transition shaders.
  - Implemented keyboard navigation, selection management, and export popup handler for Transition Playlists.
- **Macro Controls & Parameter Linking Architecture RFC (`docs/developer/macro_controls_and_parameter_linking_proposal.md`)**:
  - Published comprehensive technical design for 8 Performance Macro Knobs + 4 Macro Switches per session with 1-to-many parameter mapping, curve shapes (Linear, Exp, Log, S-Curve), min/max travel bounds, and modulation matrix integration.

### Phase 2: Transition Presets & Transition Queue Engine (`TransitionPresetDto`, `TransitionPlaylistDto`, `TransitionQueueManager.kt`, `FileSystemManager.kt`, `PresetManager.kt`, `Mixer.kt`, `PlayQueueManager.kt`)
- **Transition Presets (`.lsdtrans`) & Playlists (`.lsdtransplay`)**:
  - Introduced `.lsdtrans` data format for saving and recalling transition parameter states (e.g., customized wipe angle, softness, glitch intensity, and dry/wet blend) stored under `library/transitions/`.
  - Introduced `.lsdtransplay` data format for saving ordered transition setlists stored under `library/transition_playlists/`.
- **High-Performance Transition Queue Engine (`TransitionQueueManager.kt`)**:
  - Volatile queue engine supporting unified stock transition shader IDs and `.lsdtrans` custom preset files.
  - Implemented repeat modes, battle-tested shuffle cycle tracking (`playedIndices`/`playbackHistory`), queue mutation index re-basing (`insertAt`, `removeFromQueue`, `moveItem`), and history back-stepping (`advancePrevious`).
- **Filesystem Scanner Caching & Unified Asset Types (`FileSystemManager.kt`)**:
  - Added scanning support for `.lsdtrans` and `.lsdtransplay` files in `FileSystemManager` with signature-cached recursive directory indexing.
  - Added root directory getters (`getTransitionsRoot()`, `getTransitionPlaylistsRoot()`) and extended `AssetType` with `TRANSITION_PRESET` and `TRANSITION_PLAYLIST`.
- **Auto-Advance Crossfade Integration (`PlayQueueManager.kt`, `Mixer.kt`)**:
  - Added `mixer.applyTransitionPreset(dto)` helper to apply transition presets seamlessly to `mixer.transitionFilter`.
  - Integrated `TransitionQueueManager.advanceOnAutoFade(mixer)` auto-advance hooks into `PlayQueueManager.triggerNext()`, `triggerPrevious()`, and `playIndex()`, staging new transition filters prior to crossfader movement.
- **Session State Persistence (`SessionStateDto` version 6)**:
  - Extended `SessionStateDto` to serialize and restore transition queue items (`transQueue`), active index (`transActiveIndex`), auto-advance (`isTransAutoAdvanceEnabled`), repeat (`isTransRepeatEnabled`), and shuffle (`isTransShuffleEnabled`).

### Phase 1: Master Output FX Pipeline & Mixer Sub-Tabs (`Mixer.kt`, `Renderer.kt`, `PresetModels.kt`, `PresetManager.kt`, `ParametersState.kt`, `ParametersTabs.kt`, `ParametersPanel.kt`)
- **4-Slot Serial Master FX Chain**: Added a 4-slot ISF effect chain to the Master Output stage in `Mixer.kt`, allowing VJs to chain serial post-composite effects (e.g. master bloom, CRT glitch, color adjustment, hue shift, or distortion) across the blended output of Deck A, Deck B, and Deck BG.
- **3-Pass Composite Rendering Pipeline**: Refactored `Renderer.renderMixer()` into a 3-pass GPU architecture: Pass 1 (ISF Transition -> `blendFBO`), Pass 2 (Composite -> `masterCompositeFBO`), and Pass 3 (Serial Master FX chain -> `masterFxFBOs` -> target `masterFBO`). Downstream capture engines (recording, NDI/texture streaming, and display output) continue to consume `masterFBO` seamlessly.
- **Mixer Panel Sub-Tabs (`[ CTRL ]`, `[ TRANS ]`, `[ FX ]`)**: Replaced the monolithic Mixer parameter panel with three structured sub-tabs:
  - **`CTRL` Tab**: Master Alpha, Crossfade, Fade Speed, Bloom, Channel Isolation Levels, Queue & Clock triggers, and Morph triggers.
  - **`TRANS` Tab**: Transition shader selector popup (`MIXER_TRANSITION`), bypass toggle, dry/wet fader, and dynamic transition parameter rows with full CV modulation grid support.
  - **`FX` Tab**: Master FX Chain options (Save/Copy/Paste/Clear), 4 slot expand/collapse chevrons, shader selectors, bypass checkboxes, slot presets (`.lsdfx`), drag-and-drop targets, and dry/wet / parameter rows on the CV grid.
- **Clean `MixerDto` & `SessionStateDto` Version 6**: Refactored session state to cleanly nest all mixer settings, levels, transition filters, and master FX slots into a dedicated `MixerDto`. Bumped `SessionStateDto` version to 6 with automatic graceful fallback on legacy session deserialization.


### 2x2 Grouped Container Layout for Library Panel (`LibraryPanel.kt`, `docs/developer/ui.md`)
- **2x2 Grouped Box Architecture**: The 4 Library columns are now logically organized into two rounded container boxes (`LibraryGroup1` and `LibraryGroup2`) featuring 6 px rounded corners (`ChildRounding`), a subtle dark child background, and border frames (`Border`).
  - **Group 1 (Assets & Setlists)**: Encloses Column 1 (Presets / FX Presets) and Column 2 (Playlist Editor / FX Chains).
  - **Group 2 (Live Play Queues)**: Encloses Column 3 (Background Queue) and Column 4 (Play Queue A/B).
- **Seamless Alignment**: An 8 px horizontal gap (`groupGap`) separates the two rounded group containers, and the top edge of both boxes touches the bottom of the top toolbar menu bar.

### Per-Slot FX Presets & 4-Slot FX Chains (`FXPresetModels.kt`, `ParametersTabs.kt`, `FXPresetListPanel.kt`, `FXChainListPanel.kt`, `LibraryPanel.kt`, `SavePresetModal.kt`, `PresetManager.kt`, `FileSystemManager.kt`)
- **Per-Slot FX Presets (`.lsdfx`)**: Individual slot FX configurations (filter ID, bypass, dry/wet, and parameters) can now be saved into `.lsdfx` files under `library/fx/` and loaded directly onto any deck slot.
- **4-Slot FX Chains (`.lsdfxchain`)**: Full 4-slot FX chains can be captured and saved into `.lsdfxchain` files under `library/fx_chains/` to instantly recall complete effect pipelines.
- **Parameters Panel Kebab Menus & Drag-and-Drop**:
  - **FX Chain Header Bar**: Added a compact header bar above the FX slot list with an options kebab menu (`Save Chain As...`, `Copy Chain`, `Paste Chain`, `Clear All Slots`).
  - **Per-Slot Kebabs**: Each slot features an options kebab menu (`Save Slot Preset As...`, `Copy Slot`, `Paste Slot`, `Reset Slot`).
  - **Drag-and-Drop Targets**: Drag `.lsdfx` or `.lsdfxchain` files directly from the Library onto any slot to update or swap effects with undo support.
- **Library Mode Toggle (`[ Presets ]` / `[ FX ]`)**: Added a segmented mode toggle in the Library menu bar to swap Columns 1 & 2 between Presets/Playlists and FX Presets/FX Chains while anchoring Columns 3 & 4 (BG Queue & Play Queue).
- **Hybrid Toolbar & Smart Overwrite Prompt**: Single FX preset load buttons (`[A]`, `[B]`, `[BG]`, `[PV]`) route into the first vacant slot on a deck, prompting with a slot overwrite selector when all 4 slots are full.

### Fix ImGui 1.92 Icon Font Atlas Glyph Corruption via Inter PUA Cmap Stripping & Dynamic Icon Ranges (`UITheme.kt`, `Icons.kt`, TTF Assets)
- **Inter PUA Cmap Stripping**: Stripped stray Private Use Area (`E000–F8FF`) OpenType stylistic-alternate cmap entries (e.g., `"G.1"`) from bundled Inter TTF font files (`Inter-Regular.ttf`, `Inter-Medium.ttf`, `Inter-Bold.ttf`). Left in place, those entries collided with Lucide merged icon glyphs at the same codepoints, causing icon corruption and glyph aliasing onto unrelated digits in Dear ImGui 1.92.
- **Dynamic Icon Range Generation (`UITheme.kt`)**: Replaced the static full-block `E000–E7FF` (2048 codepoints) range with a dynamic `ICON_RANGE` built via reflection over `Icons.kt` fields, baking only referenced icon codepoints to optimize font atlas memory.
- **Reliable Triangle Chevrons (`Icons.kt`)**: Switched `CHEVRON_UP` and `CHEVRON_DOWN` to standard Unicode triangles (`▲`/`▼`, `U+25B2`/`U+25BC`) baked directly into the base Inter font.

### Removal of Obsolete Legacy Preset Auto-Migration & Preset Loading Test Fix (`PresetModels.kt`, `PresetDirtyLoadingTest.kt`)
- **Removal of Legacy Preset Auto-Migration (`PresetModels.kt`)**: Removed obsolete auto-migration logic in `Deck.applyDto` that conditionally injected legacy feedback and 3D parameters into empty FX slots.
- **Preset Load Test Reliability (`PresetDirtyLoadingTest.kt`)**: Eliminated potential array index out-of-bounds exceptions when loading deck presets on mock decks during automated testing.

### Pure ISF v2.0 Standardization & Removal of Legacy Custom Video Sources (`library/sources/`)
- **ISF v2.0 Shader Migration**: Removed legacy non-ISF custom video source folders (`attractor_feedback`, `chladni`, `colors`, `gyroid`, `hyper_mesh`, `hyper_slice`, and `brick`) relying on proprietary `meta.json` manifests, advancing the system's architecture toward pure ISF v2.0 shader standardization and away from custom video source formats.
- **Native ISF Generator (`dynamic_spiral.fs`)**: Converted Dynamic Spiral from a custom Kotlin class (`DynamicSpiral.kt`) to a standalone ISF v2.0 shader file with an embedded JSON header and standard GLSL 3.30 uniform inputs.
- **GLSL Phase Integration**: Shifted time and shear phase integration to GLSL expressions (`TIME * Speed`), eliminating CPU-side phase accumulators and custom uniform overrides while preserving smooth particle motion and color sweeps.
- **Codebase Clean-up**: Removed `DynamicSpiral.kt`, `meta.json`, and `shader.frag`. Simplified source instantiation logic in `VisualSourceRegistry.kt` and `WebPresetSerializer.kt`.
- **Web Pipeline Parity**: Synchronized WebGL ES 3.0 shader (`web/shaders/dynamic_spiral.frag`) and updated sync manifest hashes (`web/sync_manifest.json`).

### Correct Lucide Icon Font Scoping & Modern ImGui Icon Alignment (`Icons.kt`, `BrowserActionToolbar.kt`, `PresetListPanel.kt`, `PlaylistEditorPanel.kt`, `QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`, `DeckControlPanel.kt`, `MixerPanel.kt`, `ModulatorHeaderRow.kt`, `ParametersTabs.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `Lfo2Section.kt`)
- **Accurate Lucide PUA Codepoint Mappings (`Icons.kt`)**: Audited and corrected codepoint drift against `lucide.ttf`:
  - `Icons.LOCK`: Switched to `\ue10b` (lock padlock, was loader-2).
  - `Icons.CHEVRON_DOWN`: Switched to `\ue06d` (chevron-down, was checkmark).
  - `Icons.CHEVRON_UP`: Switched to `\ue070` (chevron-up, was chevrons-down).
  - `Icons.DOWNLOAD`: Switched to `\ue0b2` (download, was disc).
  - `Icons.NOTE`: Switched to `\ue1f9` (pencil, was percent).
  - `Icons.ALIGN_CENTER_LINE`: Switched to `\ue43b` (fold-horizontal, was proportions).
  - `Icons.DICES`: Switched to `\ue2c5` (dices, was dice-5).
  - Added `POWER_OFF` (`\ue209`), `VOLUME` (`\ue1a9`), `VOLUME_X` (`\ue1ac`), `FILE_PLUS` (`\ue0c9`), and `FOLDER_PLUS` (`\ue0d9`).
- **Deck Control Button Labels Fixed (`DeckControlPanel.kt`)**: Fixed swapped `id` and `icon` arguments in `drawIconButton()` calls, restoring visibility for Save and Eject buttons on all decks.
- **Dynamic On/Mute Modulator Icon (`ModulatorHeaderRow.kt`, `Lfo2Section.kt`)**: Modulator on/mute buttons now dynamically render `Icons.POWER` when enabled and `Icons.POWER_OFF` when bypassed.
- **Playlist Editor Popup Scoping & Creation Modal (`PlaylistEditorPanel.kt`, `BrowserPopupHandler.kt`, `LibraryPanel.kt`)**: Added `pendingOpenNewPlaylistPopup` so clicking "+ New Playlist" from inside child panels properly triggers the root modal popup in Dear ImGui 1.92.
- **Explicit Font Scoping for UI Icon Buttons**: Ensured all icon-bearing button calls have font levels pushed appropriately so the merged Lucide PUA glyph atlas is active when calculating text metrics and drawing buttons.

### Fix Dear ImGui 1.92 Format String Memory Corruption in Performance Readouts (`MenuBar.kt`, `UITheme.kt`, `TooltipHelper.kt`, `DeckControlPanel.kt`, `MissingItemsPanel.kt`, `PresetListPanel.kt`)
- **Unformatted Telemetry Text Rendering**: Replaced `ImGui.text(...)` calls in `MenuBar.kt` performance readouts (`CPU %`, `BPM`, `DSP latency`, `FPS`, `Frame Time`, and live video recording `dropped frames`) with `ImGui.textUnformatted(...)`. Upstream `imgui-java` 1.92 passes strings directly to native `ImGui::Text(fmt, ...)`, causing `vsnprintf` format string specifier parsing and stack memory corruption whenever a string contains `%` characters.
- **Global Typography Helper Safety**: Switched all semantic text helpers (`h1`, `h2`, `h3`, `body`, `caption`, `code` and their colored variants) in `UITheme.kt` to call `ImGui.textUnformatted(...)`, globally eliminating format string vulnerabilities across all theme typography.
- **Tooltip System Safety**: Updated `itemTooltip(text: String)` and `showTooltip(text: String)` in `TooltipHelper.kt` to use `ImGui.textUnformatted(...)`, preventing garbled rendering when displaying tooltips with percentage symbols or formatted string indicators.
- **Dynamic Asset & File Path Protection**: Updated dynamic text rendering for preset names, missing item paths, and asset names across `DeckControlPanel.kt`, `MissingItemsPanel.kt`, and `PresetListPanel.kt` to use `ImGui.textUnformatted(...)`.

### Consolidate 3D Elevation Under FX Tab & Streamline View Tab (`ParametersTabs.kt`, `Renderer.kt`, `DECISIONS.md`)
- **Eliminated Duplicate 3D Elevation Controls**: Removed the redundant rendering of 3D Elevation sliders (`3D Mode`, `Zoom`, `Rotate X/Y/Z`, `Separation`, `Perspective`, `Depth Dim`, `Blend Mode`, `Roundness`) and the "+ Enable 3D Projection" button from the **View** tab. All `3d_elevation` filter parameters are now cleanly and exclusively managed under the **FX** tab in FX Slot 2.
- **Focused View Tab**: The View tab now focuses purely on canvas framing and camera orientation: universal 2D scaling (`Zoom`) and roll (`Rotate Z`) for 2D sources, and native camera rotation (`Rotate X`, `Rotate Y`, `Rotate Z`, `Zoom`) for 3D generators.
- **Renderer Consistency**: Removed the `has3DElevation` transform bypass in `Renderer.renderDeck()`, ensuring deck-level `Zoom` and `Rotate Z` always scale and orient the 2D source cleanly before it enters the FX filter chain.

### Fix Main Video Output for ISF Transitions in Mixer Compositing Pipeline (`mixer.frag`, `Renderer.kt`, `web/shaders/mixer.frag`, `MixerTransitionTest.kt`)
- **Restored Main Video Output for ISF Transitions**: Fixed an issue where selecting ISF transitions resulted in a black main output while `Deck BG` continued rendering. In `mixer.frag`, implemented pure ISF transition composite mode (`uMode < 0`) that samples `uTex1` (the blended transition FBO) scaled by crossfade-interpolated channel level faders (`mix(uLevelA, uLevelB, uProgress)`), while preserving legacy dual-texture blend modes (`uMode >= 0`) for WebGL and fallback compatibility.
- **Defensive Uniform Initialization**: Added explicit GLSL default initializers (`uLevelA = 1.0`, `uLevelB = 1.0`, `uLevelBG = 1.0`, `uMasterLevel = 1.0`, `uProgress = 0.5`, `uMode = -1`) to guard against uninitialized zero-gain states.
- **Zero-Allocation Fallback Transition Caching**: Cached `fallbackTransition` inside `Renderer.kt` so falling back to linear crossfade when no transition is active does not allocate or dispose `ISFFilter` instances on the render thread.
- **WebGL Parity**: Synchronized `web/shaders/mixer.frag` via `syncWeb` with matching dual-mode composite support.

### Fix Dear ImGui MenuBar Boundary Assertion Crash (`PanelTitleBar.kt`, `ParametersPanel.kt`, `WindowLayoutSafetyTest.kt`)
- **Guarded Title Bar `drawExtra` Lambda**: In `ParametersPanel.kt`, only supply the source tab trailing lambda to `PanelTitleBar.draw` when `activeDeck` is non-null and not empty (`activeDeck != null && !activeDeck.isEmpty`). Passing `null` prevents spurious cursor movements via `ImGui.sameLine()` and `ImGui.setCursorPosY()` when viewing the Mixer tab or an empty deck launchpad.
- **Defensive MenuBar Boundary Dummy**: Added `ImGui.dummy(0f, 0f)` inside `PanelTitleBar.draw()` immediately after invoking `drawExtra()`. This resets ImGui's internal `window->DC.IsSetPos` flag and ensures window boundaries are always closed cleanly, preventing Dear ImGui 1.90+ assertions (`Code uses SetCursorPos()/SetCursorScreenPos() to extend window/parent boundaries`) from triggering inside `ImGui.endMenuBar()`.


### Fix Audio Device Disappearance & WirePlumber Link Negotiation Races (`JavaSoundClient.kt`, `JackClient.kt`, `AudioEngine.kt`, `MidiJackWatchdog.kt`, `AudioEnginePanel.kt`)
- **Suppressed Playback Device Probing Storms**: Screened out playback-only soundcards (HDMI, analog speakers, headphones, digital sinks) before querying JavaSound/ALSA lines in `JavaSoundClient.kt`. This eliminates microsecond `snd_pcm_open`/`close` probes on the internal speaker (`hw:0,0`), preventing WirePlumber link negotiation crashes (`proxy destroyed / link failed`) and avoiding GNOME Settings dropping the laptop speaker.
- **Hardware Device Caching**: Cached discovered input devices in memory; re-probing only occurs when explicitly clicking Refresh in preferences.
- **JACK Mode Isolation**: When running under JACK / PipeWire, `getAvailableInputDevices()` returns a virtual `"JACK System Capture"` representation and suppresses all JavaSound ALSA hardware scans.
- **Coordinated Audio Teardown**: In `JavaSoundClient.stop()`, audio capture is paused and flushed, the reader thread is joined with a timeout (`thread.join(1000)`), and the native handle is closed only once confirmed idle. In `JackClient.stop()`, all active links are disconnected via `jack.disconnect()` before client deactivation and closure.
- **Settling Cooldown & No-Op Switching**: Enforced a 150ms settling cooldown between `stop()` and `startClient()` during audio transitions to allow WirePlumber to finish graph cleanup, and short-circuited no-op device selections.
- **Watchdog Reconnect Throttling**: Limited consecutive automatic audio reconnection attempts in `MidiJackWatchdog.kt` to 3 before pausing to prevent rapid cyclic stream recreation.

### 100% ISF Pipeline Migration — Deprecating Hard-Wired FX & Mixer (`Renderer.kt`, `Deck.kt`, `Mixer.kt`, `ISFFilter.kt`, `ISFTransitionRegistry.kt`, `PresetModels.kt`, `default_filters/`, `default_transitions/`)
- **100% Modular Feedback Effect (`default_filters/feedback.fs`)**: Replaced monolithic hardcoded `feedback.frag` with native ISF multi-pass persistent history buffers. Calibrated with 100% mathematical fidelity to the legacy cubic decay curve ($s \to (1 - s)^3$) and all 9 parameters (`fbDecay`, `fbGain`, `fbZoom`, `fbRotate`, `fbHueShift`, `fbBlur`, `fbChroma`, `fbMode`, `fbKaleido`). Added buffer zeroing on reset to prevent ghosting between presets.
- **Modular 3D Elevation (`default_filters/3d_elevation.fs`, `Renderer.kt`, `ParametersTabs.kt`, `PresetModels.kt`)**: Replaced legacy `tri_planar.*` and `tetra_kaleido.*` geometry shaders with a raymarched ISF shader in FX Slot 2. Engineered exact 1:1 scale-normalized analytic inverse projection where vertical quad bounds match 2D flat mode at $z=0$ under `Zoom = 1.0`, and perspective smoothly transitions from orthographic parallel rays to perspective without scaling the focal plane. Added `blendMode` parameter (defaulting to Additive Luminous with `(1.0 + lum * 0.2)` boost) alongside premultiplied Alpha Over. Bypassed 2D zoom/rotation in `Renderer.renderDeck()` to eliminate double-transforms, exposed full 3D controls under the View tab, and ported `viewBlendMode` in preset migration. Supports Tri-Axial, Cube Cage, Hex-Planar, and 24-Chamber Tetrahedral Coxeter space folding with continuous roundness control.
- **Pure ISF Mixer Transitions**: Migrated mixer transitions to 100% ISF shaders taking `startImage`, `endImage`, and `progress`. Bundled high-performance shaders for `additive_blend.fs`, `screen_blend.fs`, `multiply_blend.fs`, and `max_blend.fs` alongside `linear_crossfade.fs`, wipes, and glitch transitions.
- **Preserved Deck BG Compositing**: Master compositing preserves Deck BG behind the active A/B transition output ($\text{Master Output} = \text{Composite}(\text{Deck BG}, \text{ISF\_Transition}(\text{Deck A}, \text{Deck B}, \text{progress}))$).
- **Reduced GPU Memory Footprint**: Removed obsolete `rawSourceFBO`, `rawSource2DFBO`, `fb1`, and `fb2` ping-pong framebuffers from `Deck.kt`, eliminating 16 full-resolution / square FBO allocations across the 4 decks and saving hundreds of megabytes of VRAM.
- **Automatic Preset Migration**: Existing presets containing legacy 3D modes or feedback parameters automatically map to `3d_elevation` in `fxSlot2` and `feedback` in `fxSlot1` upon loading.

- **Dear ImGui 1.92.7.1 Upgrade**: Upgraded `io.github.spair:imgui-java` from `1.86.12` to `1.92.7.1`, bringing the desktop UI to the latest ImGui release.
- **New Key & Navigation Input API**: Replaced obsolete `ImGui.getKeyIndex(ImGuiKey.*)` with direct `ImGuiKey` constants and implemented `KeyCombination.glfwKeyToImGuiKey()` to map GLFW keycodes to ImGuiKey codes in `ShortcutManager.isTriggered`. Fixed remaining raw GLFW keycodes in `UIManager.kt`, `LibraryPanel.kt`, and `PreferencesPanel.kt` to prevent `IsNamedKey` assertion failures.
- **64-bit Texture Handles**: Converted OpenGL texture handles passed to `ImGui.image(...)` to `Long` (`.toLong()`) across deck control, mixer, and video export modals.
- **Backend Lifecycle & Font Rebuilding**: Updated backend methods (`dispose()` to `shutdown()`), called `imguiGl3.newFrame()` in frame initialization, and replaced deprecated font update methods with discrete `destroyFontsTexture()` and `createFontsTexture()` calls, along with dynamic font scaling support (`ImGui.pushFont(font, 0f)`).
- **Layout & Style Safety Guards**: Submitted `ImGui.dummy(0f, 0f)` after cursor repositioning in `ParametersPanel.kt` and `MixerPanel.kt` to satisfy Dear ImGui 1.90+ boundary extension rules, and enforced positive clamps (`coerceAtLeast(1.0f)`) on `separatorSize` and `separatorTextBorderSize` in `UIThemeStyler.kt` and `UIManager.kt`.
- **Mixer Initialization Fix (`Mixer.kt`)**: Reordered `init` block after `mode` property declaration to prevent a startup `NullPointerException` during default transition configuration.

### Fix Visual Source Selection Crash, GLSL Relaxed Typing & Clean Startup Logging (`ParametersPanel.kt`, `VisualSourceRegistry.kt`, `DynamicVisualSource.kt`, `ISFParser.kt`, `ISFFilterRegistry.kt`, `ISFTransitionRegistry.kt`)
- **Resolved ImGui Launchpad Assertion Crash**: Fixed a JVM assertion crash (`id != window->ID`) when opening the "Select Visual Source" or "Load Preset" menus from an empty deck launchpad. Empty or blank ISF `DESCRIPTION` fields now fall back to title-cased filenames in `VisualSourceRegistry.kt` and `DynamicVisualSource.kt`, and all launchpad `ImGui.menuItem` calls now enforce `label.ifBlank { id }` and include unique `##` identifiers to avoid empty IDs and label collisions.
- **Relaxed GLSL Typing via GL_ARB_gpu_shader5 (`ISFParser.kt`)**: Injected `#extension GL_ARB_gpu_shader5 : enable` and `#extension GL_EXT_gpu_shader4 : enable` into preprocessed ISF fragment and vertex shaders. Enables implicit type conversions between `int` and `uint`, bitwise shifts, and mixed equality checks, successfully compiling complex shaders such as `Tiny Date Time Overlay.fs` and `Random Characters.fs`.
- **99.7% GL Driver Fragment Compile Rate**: 326 out of 327 fragment shaders in the user library now compile cleanly with zero errors on the Mesa/OpenGL 3.3 driver.
- **Scoped Directory Scanning & Clean Startup Output**: Explicitly restricted `ISFFilterRegistry`, `ISFTransitionRegistry`, and `VisualSourceRegistry` to skip cross-scanning mismatched directory domains (e.g. `library/sources` is skipped during filter/transition scans). User shader compilation failures are now logged as concise, single-line warnings rather than dumping multi-page stack traces into stdout/stderr on startup.


### Fix Properties Panel Disappearing When Library Is Half-Visible (`ShortcutManager.kt`, `LibraryPanel.kt`, `ParametersKeyboard.kt`, `PresetListPanel.kt`, `PlaylistEditorPanel.kt`, `QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`)
- **Fixed Properties Selection Loss**: Resolved an issue where selecting a parameter cell in `ParametersPanel` briefly showed controls in `PropertiesPanel` before immediately disappearing when the Library panel was in half-visible (`HALF`) mode.
- **Root Cause & `ShortcutManager.isTriggered()`**: In `LibraryPanel.kt`, `ShortcutManager.matchesKey("library.load_deck_a", GLFW_KEY_1)` was being invoked without verifying if the key was actually pressed via ImGui. Because `library.load_deck_a` is bound to key `1` by default, `isLoadA` evaluated to `true` on every frame while the Library was visible, repeatedly loading the selected preset into Deck A at 60 FPS. This recreated Deck A's parameters each frame, causing `PropertiesPanel` to detect stale parameter references and reset the selection. Added `ShortcutManager.isTriggered(actionId)` to properly check active ImGui modifier state and `ImGui.isKeyPressed()`.
- **ParametersKeyboard Shortcut Guards**: Updated `parameters.save_deck` and `parameters.save_deck_as` in `ParametersKeyboard.kt` to use `ShortcutManager.isTriggered()` to prevent spurious triggers when modifier keys are held.
- **Child Window Focus Guard**: Guarded ImGui focus-driven auto-selection checks (`ImGui.isItemFocused()`) in all four Library browser panels (`PresetListPanel`, `PlaylistEditorPanel`, `QueueActionsPanel`, `BgQueueActionsPanel`) to verify that the child window itself has active focus (`ImGui.isWindowFocused(ChildWindows)`) and matches `LibraryPanel.activeSelectionSource`.

### Comprehensive Multi-Type MIDI Subsystem, Soft Takeover, Relative Rotary Decoding & Preferences MIDI Controls Manager (`MidiEngine.kt`, `MidiMappingManager.kt`, `PreferencesPanel.kt`, `UIManager.kt`, `CVRegistry.kt`, `MidiModulatorSection.kt`, `ParametersRenderer.kt`)
- **Multi-Message MIDI Engine**: Expanded `MidiEngine` to capture `NOTE_ON`, `NOTE_OFF`, `CONTROL_CHANGE`, and `PITCH_BEND` events. Implemented lock-free atomic array storage for 16 channels of 128 CCs, 16 channels of 128 Notes, and 16 channels of 14-bit Pitch Bend values.
- **Live MIDI Monitor (Sniffer)**: Built a real-time packet monitor and history sniffer displaying incoming timestamps, channels, types, indices, raw values, and normalized progress bars directly in the Preferences UI.
- **Dedicated "MIDI Controls" Tab in Preferences**: Moved MIDI settings out of the Audio Hardware section into a dedicated `Category.MIDI_CONTROLLER` tab in `PreferencesPanel`, complete with hardware status, device enumeration, hotplug rescan, profile management (new/save/delete), global performance action triggers, and a searchable parameter mappings table.
- **Intelligent Signal Classification & Learn Pipeline**: MIDI Learn inspects incoming streams to automatically infer whether a control is a discrete button/pad (`BUTTON_NOTE`), continuous pot/slider (`CONTINUOUS_CC`), endless rotary encoder (`ROTARY_*`), or pitch bend, while allowing full user overrides.
- **Continuous Control Shaping & Slew Filter**: Added editable Min/Max numeric range clamping, Invert boolean, and an exponential Slew smoothing filter ($0 \dots 250\,\text{ms}$) to eliminate 7-bit zipper noise and discrete stepping on shader uniforms.
- **Soft Takeover (Pickup)**: Introduced Soft Takeover mode to eliminate jarring parameter jumps when switching presets. Parameter values remain untouched until the physical control moves across the stored software value, with live telemetry and visual pickup status cues.
- **Relative Rotary Encoders**: Full decoding support for the three dominant endless encoder standards: Binary Offset (64-centric), Signed Bit (1-centric), and Two's Complement (1-centric), with configurable step size scaling.
- **Discrete Trigger Modes**: Added Toggle (latched), Momentary (active only while depressed), Step Increment, and Step Decrement modes for buttons and pads.
- **Modulation Matrix Note Integration**: Expanded `CVRegistry` and `ParametersRenderer` to support `midi_note_<channel>_<note>` modulators alongside `midi_cc_<channel>_<cc>`.
- **Full Backward Compatibility**: All new fields in `MidiControlMapping` default seamlessly, ensuring existing `library/midi/*.json` profile files load without error or data loss.

### Flexible ISF Shader Management, Role Auto-Detection & Folder Hierarchy (`ISFScanner.kt`, `ISFModels.kt`, `ISFParser.kt`, `ISFTextureLoader.kt`, `ISFVisualSource.kt`, `ISFFilter.kt`, `VisualSourceRegistry.kt`, `ISFFilterRegistry.kt`, `ISFTransitionRegistry.kt`, `ShaderPickerPopup.kt`)
- **JSON Input Role Auto-Detection**: Eliminated rigid directory requirements (such as forcing shaders into specific `Generators/`, `Filters/`, or `Transitions/` folders). The scanner inspects the declared `INPUTS` in the shader's JSON header:
  - **0 image inputs** $\to$ Auto-classified as **Generator** (`ISFAssetType.GENERATOR` / `VisualSourceRegistry`).
  - **1 image input** (`inputImage`) $\to$ Auto-classified as **Filter** (`ISFAssetType.FILTER` / `ISFFilterRegistry`).
  - **2+ image inputs** (`startImage`, `endImage`) or presence of a `progress` float $\to$ Auto-classified as **Transition** (`ISFAssetType.TRANSITION` / `ISFTransitionRegistry`).
- **Preserved Folder Hierarchies as Categories**:
  - Automatically captures user subfolder structures (e.g. `Packs/Retro/Noise`) and attaches them to `folderPath` and shader category tags.
  - Recursively scans directory trees without requiring root-level flattening.
- **Hierarchical UI Browser & View Modes**:
  - `ShaderPickerPopup` now provides a dual view mode toggle: **Folders** (`Icons.FOLDER`) and **Flat** (`Icons.LAYOUT_FULL`).
  - **Folders Mode**: Renders collapsible, organized tree nodes (`ImGui.treeNodeEx`) matching the user's filesystem structure, with auto-expansion when searching.
  - **Flat Mode**: Displays a high-density 3-column table with category badges.
- **Relative Asset Resolution for `IMPORTED` Textures (`ISFTextureLoader.kt`)**:
  - Fully parses ISF `IMPORTED` asset declarations in both JSON dictionary and array schemas.
  - Loads referenced local assets (noise textures, lookup tables, image masks) relative to the shader file's original directory using thread-safe STBImage loading on Thread 0.
  - Injects `uniform sampler2D <name>;` into generated GLSL.
  - Binds imported textures across sequential texture units in `ISFVisualSource` and `ISFFilter` with zero per-frame heap allocations during render loops.

### Transition Settings to Preferences Across UI, Storage & Shortcuts (`PreferencesPanel.kt`, `AppPreferences.kt`, `BroadcastPreferences.kt`, `UITheme.kt`, `MenuBar.kt`, `ShortcutManager.kt`)
- **UI Nomenclature**: Standardized all user-facing dialogs, menus, and HUD tooltips from "Settings" to "Preferences". The top menu bar now provides `File > Preferences...` and telemetry quick-launch actions open Preferences categories directly.
- **Dedicated Global Shortcut**: Added `Ctrl+P` (Windows/Linux) and `Cmd+P` (macOS) shortcut (`global.preferences`) to immediately open the Preferences dialog from anywhere in the application.
- **Model & Class Refactoring**:
  - Renamed `AppSettings` to `AppPreferences`, preserving `typealias AppSettings = AppPreferences` for backward compatibility.
  - Renamed `BroadcastSettings` to `BroadcastPreferences`, with `typealias BroadcastSettings = BroadcastPreferences`.
  - Renamed `SettingsPanel` to `PreferencesPanel`, with `typealias SettingsPanel = PreferencesPanel`.
- **Transparent Configuration Migration**:
  - Application configuration is now persisted to `lsd-preferences.properties` instead of `lsd-settings.properties`.
  - Seamless fallback: `loadPreferences()` automatically inspects `lsd-preferences.properties` first, gracefully falling back to legacy `lsd-settings.properties` if present to guarantee no user settings are lost.
  - Preserved deprecated compatibility wrappers `saveSettings()` and `loadSettings()` delegating to `savePreferences()` and `loadPreferences()`.
- **Test Suite Modernization**: Replaced legacy settings tests with `PreferencesDefaultsTest`, `BroadcastPreferencesTest`, and `AudioEnginePreferencesTest`, adding comprehensive tests for `lsd-preferences.properties` persistence and legacy fallback loading.

### Oscilloscope Controls Overlay & Tab Row Live Button Migration (`OscilloscopeDrawer.kt`, `PropertiesPanel.kt`, `ScopeTimebaseTest.kt`)
- **Overlaid Timebase Dropdown**: Moved the timebase selector directly inside the oscilloscope canvas in the top-right corner with semi-transparent frame styling, eliminating the previous controls bar above the oscilloscope and creating a compact, hardware-like oscilloscope layout.
- **Dynamic Auto Timebase Label**: When set to Auto, the dropdown text dynamically displays the active automatically selected time window (e.g. `Auto (10s)`, `Auto (1s)`).
- **Streamlined Canvas Presentation**: Removed the auxiliary division label (`30m/div`) beside the dropdown for minimal, uncluttered visual presentation.
- **Tab Row `[ LIVE ]` / `[ MUTED ]` Button**: Relocated the Master Cell Mute/Live toggle button to the top tab row (`Value`, `MIDI`, `LFO`, `SEQ`, `Audio`), aligned to the right, matching tab height and maintaining consistent button width.
- **Streamlined Tab-to-Scope Header**: Removed the redundant parameter path header row (`Deck A | ...`), the separator line beneath the tab bar, and the separator line beneath the header row, maximizing vertical screen real estate for the oscilloscope and modulator controls.
- **Zero-Allocation Render Path**: Preallocated immutable label arrays across all physical Auto duration tiers (`1s`, `10s`, `100s`, `15m`, `2.5h`, `24h`) in `OscilloscopeDrawer`, avoiding GC string and array allocations during render loops.

### Universal Shader Ecosystem Support — ISF, Shadertoy & GLSLSandbox (`ISFParser.kt`, `ISFVisualSource.kt`, `Renderer.kt`, `AudioTexture.kt`, `VisualSourceRegistry.kt`, `ISFScanner.kt`)
- **Multi-Format Ingestion & Smart Detection**: Shaders across the live visuals ecosystem (**ISF**, **Shadertoy**, and **The Book of Shaders / GLSLSandbox**) are now automatically recognized, normalized, and cataloged without requiring manual code conversion or JSON headers.
- **Legacy GLSL 1.20 Core 3.30 Compatibility**: Injects automatic polyfills for legacy GLSL calls (`texture2D`, `textureCube`, `texture2DRect`, `texture2DProj`) and aliases `gl_FragColor` to modern core outputs.
- **Complete ISF Macro Suite**: Added built-in macros `IMG_THIS_PIXEL`, `IMG_THIS_NORM_PIXEL`, and `IMG_SIZE` alongside existing `IMG_NORM_PIXEL` and `IMG_PIXEL`.
- **Automatic Shadertoy Entry Point Shim**: Shaders featuring `void mainImage(...)` automatically receive entry-point bridging to `void main()` writing to normalized fragment coordinates.
- **Unified Uniform Bridge**: Pre-injects and binds unified uniform sets on every frame in `Renderer.kt`:
  - Resolution: `RENDERSIZE`, `iResolution` (vec3), `u_resolution`, `resolution`
  - Clocks & Time: `TIME`, `iTime`, `u_time`, `time`, `TIMEDELTA`, `iTimeDelta`, `u_delta`
  - Frame & FrameRate: `FRAMEINDEX`, `iFrame`, `u_frame`, `iFrameRate`
  - Calendar / Clock: `DATE`, `iDate` (year, month, day, seconds since midnight)
  - Interactive Mouse: `iMouse` (vec4 with pixel coordinates and click drag status), `u_mouse` / `mouse` (normalized vec2)
  - Audio Scalars: `audioVolume`, `audioBass`, `audioMid`, `audioTreble` from `CVRegistry`
- **Real-Time Audio FFT & Waveform Texture (`AudioTexture.kt`)**: Implemented a dedicated 512x2 floating-point texture (`GL_R32F`) updating live from `AudioEngine` (Row 0: 512 FFT magnitude bins; Row 1: 512 waveform samples) bound to `audioFFT` and `iChannel0` with zero audio thread allocation.
- **Multi-Pass Visual Generators (`ISFVisualSource.kt`)**: Extended full multi-pass ping-pong FBO rendering to visual generators, supporting offscreen pass targets, dimension expressions (`$WIDTH/2.0`, `$HEIGHT/2.0`), persistent history buffers, and 32-bit floating point passes (`FLOAT: true`).

### Properties Panel Title Bar & Typography Synchronization (`PropertiesPanel.kt`, `UIManager.kt`)
- **Synchronized 1.5x Title Bar Height**: Configured the Properties panel window with `NoTitleBar` and `MenuBar` flags wrapped in `PanelTitleBar.withFramePadding(session)`, eliminating the default un-styled window title bar and rendering the synchronized 1.5x scaled title bar (`PanelTitleBar.calculateHeight(session)`) matching the height of the Parameters panel to the left.
- **Consistent H3 Typography**: Aligned the `"Properties"` title text typography to use `UITheme.FontLevel.H3` and optical vertical text centering via `PanelTitleBar.draw(session, "Properties")`, guaranteeing identical font styling, weight, and visual baseline between Parameters and Properties.

### Keyboard Shortcuts Overhaul, Grid Layout, Rebinding & Collision Detection (`SettingsPanel.kt`, `ShortcutManager.kt`, `KeyCombination.kt`, `ShortcutAction.kt`, `Main.kt`, `LibraryPanel.kt`, `ParametersKeyboard.kt`)
- **Centralized `ShortcutManager` Engine**: Decoupled hardcoded GLFW keyboard shortcuts across `Main.kt`, `LibraryPanel.kt`, `ParametersKeyboard.kt`, and `UIManager.kt` into a centralized `ShortcutManager` service with persistent keybindings storage (`~/.liquidlsd/keybindings.json`).
- **Swapped 2-Column Grid Layout in Settings**: Redesigned **Settings → Keyboard Shortcuts** table to place **Action Names & Detailed Descriptions on the Left** and **Shortcut Key Badges & Rebind Controls on the Right**.
- **Live Search & Filtering Bar**: Added search filter bar (`Icons.SEARCH`) allowing instant filtering of shortcuts by action name, description, category, or current keypress combination.
- **Interactive Key Combination Rebinding**: Clicking any shortcut key badge opens an interactive recording modal allowing users to press any custom key combination (with full modifier support: `Ctrl`, `Shift`, `Alt`, `Super`/`Cmd`), cancel (`Esc`), or unbind (`Backspace`/`Delete`).
- **Scope-Aware Collision Detection**: Automatically detects duplicate keybindings across `GLOBAL` and context-specific scopes. Displays amber warning highlights, conflict badges (`Icons.ALERT`), conflict tooltips, and quick resolution options (`Swap` keybindings, `Reset` to default).

### Contextual In-Properties MIDI Learn (`PropertiesPanel.kt`, `MidiModulatorSection.kt`, `ParametersState.kt`, `ParametersRenderer.kt`, `MenuBar.kt`, `UIManager.kt`, `MixerPanel.kt`)
- **Elimination of Global Modal MIDI Map Mode**: Removed the `MIDI Map` toggle item from the main menu bar (`MenuBar.kt`) and removed the global `isMidiLearnMode` boolean state from `ParametersState.kt`.
- **Inline `[ Learn MIDI ]` Controls**:
  - Unbound MIDI cells in the Properties panel (`PropertiesPanel.kt`) now feature a prominent `[ Learn MIDI ]` button alongside explicit instructions.
  - Clicking `Learn MIDI` updates the button locally to `[ ⏳ Waiting for MIDI CC... (Click to Cancel) ]` with Orchid active styling.
  - Automatically disengages after 15 seconds of inactivity or upon selecting a different cell/tab.
- **In-Context Re-Learn & Unbind**: Bound MIDI parameters in `MidiModulatorSection.kt` render `[ Re-Learn MIDI ]` and `[ Unbind MIDI ]` controls directly above DC Offset and Depth sliders.
- **Matrix Target Highlighting**: The active learning parameter cell in the Parameters matrix displays a glowing cyan border outline while listening for incoming MIDI CC messages.

### First-Class External Video Feeds in Universal Shader Picker (`ShaderPickerPopup.kt`, `ParametersTabs.kt`, `ExternalVideoSource.kt`, `ExternalVideoSourceTest.kt`)
- **Direct Stream Selection in Universal Shader Picker**: Discovered external video streams (PipeWire, Spout2, Syphon) are now listed directly within `ShaderPickerPopup` alongside procedural sources under a dedicated `External Sources` category pill positioned immediately beside `All`.
- **Live Stream Visual Distinction**: External stream rows in the picker table are accented in emerald green and badged with the Lucide live activity icon (`Icons.ACTIVITY`), clearly distinguishing live video feeds from compiled GLSL shaders.
- **Dynamic Header Source Button Display**: Selecting an external feed directly updates the deck header button to reflect the stream's name (e.g. `[OBS-Camera ▾]`), providing instant situational awareness during live performances.
- **Elimination of Conditional UI Layout Shifts**: Removed the conditional "Server" combo dropdown that previously appeared inside the Parameters `SRC` tab only for `ExternalVideoSource`, establishing a permanent, non-shifting layout containing only parameter sliders (`Gain`).
- **CV Parameter Routing Stability**: The canonical parameter modulation path remains stable (`Deck A/External Video/Gain`), ensuring existing presets and CV modulation assignments persist cleanly regardless of stream name or server reconnections.

### Menu Bar, Spacer, and Panel Header Height Expansion (`MenuBar.kt`, `UIManager.kt`, `ParametersPanel.kt`, `PropertiesPanel.kt`, `WindowLayoutSafetyTest.kt`)
- **Main Menu Bar 50% Taller**: Dynamically scaled the main menu bar height by 1.5x via `MenuBar.calculateHeight(session)` and `MenuBar.calculateFramePaddingY(session)`. All child elements, recording badges, ISF status pills, Ableton Link pills, drag regions, and window controls are vertically centered.
- **Top Spacer 100% Taller**: Doubled `UIManager.TITLE_BAR_PANEL_GAP` from `1.0f` to `2.0f`, establishing a clean 2 px visual divider separating the top menu bar from the workspace panels.
- **Left Panel (Parameters) & Middle Panel (Properties) Headers 50% Taller**: Expanded the Parameters window menu bar and column header row by 1.5x, and scaled the Properties CV tab buttons (`drawCvTabRow`) by 1.5x for improved touch/click hit targets and visual harmony across the primary workspace columns.
- **Unified Panel Title Bars (`PanelTitleBar.kt`)**: Added shared `PanelTitleBar` helper unifying title bar rendering for `Parameters` and `Properties`. Both panels now share synchronized 1.5x height scaling, window frame padding, `H3` typography, and optical upward vertical text centering (`TEXT_Y_OPTICAL_OFFSET = 3.0f`), guaranteeing visual consistency and zero style drift when future adjustments are made.

### Suite C UI Panel Migration — Phase 3: Parameters Panel (`ParametersPanel.kt`, `ParametersState.kt`, `ParametersRenderer.kt`, `ParametersTabs.kt`, `ParametersKeyboard.kt`, `ParametersUndo.kt`, `UIManager.kt`, `DeckControlPanel.kt`, `DeckPresetController.kt`, `MixerPanel.kt`, `PropertiesPanel.kt`, `LibraryPanel.kt`, `SettingsPanel.kt`)
- **Panel Renaming (`Preset Grid` → `Parameters`)**: Renamed Column 1 (the left parameter matrix, modulation routing grid, and deck/mixer tabs) to **`Parameters`**, completing Phase 3 and the overall migration to the Suite C panel architecture (`Parameters`, `Properties`, `Mixer`, `Library`).
- **Codebase Cleanliness & Zero-Trace Migration**: Renamed all source files, classes, models, and tests:
  - `PresetGridPanel.kt` → `ParametersPanel.kt` (object `ParametersPanel`)
  - `PresetGridState.kt` → `ParametersState.kt` (class `ParametersState`, `ParameterCellId`, `ParametersUndoSnapshot`)
  - `PresetGridRenderer.kt` → `ParametersRenderer.kt` (object `ParametersRenderer`)
  - `PresetGridTabs.kt` → `ParametersTabs.kt` (object `ParametersTabs`)
  - `PresetGridKeyboard.kt` → `ParametersKeyboard.kt` (object `ParametersKeyboard`)
  - `PresetGridUndo.kt` → `ParametersUndo.kt` (object `ParametersUndo`)
  - `PresetGridKeyboardTest.kt` → `ParametersKeyboardTest.kt`
  - `PresetGridClipboardTest.kt` → `ParametersClipboardTest.kt`
  - Updated all caller references, window names (`ImGui.begin("Parameters")`), internal ImGui IDs, tooltips, comments, and variable names with zero remaining references to `PresetGrid`, `PresetCell`, or `Preset Grid` in `src/`.
- **Documentation & User Guides**: Updated `ARCHITECTURE.md`, `docs/developer/ui.md`, `docs/developer/preset_management.md`, `docs/developer/interop_roadmap.md`, `docs/developer/unified_control_mapping.md`, `docs/developer/screen_capture_and_ui_iteration_proposal.md`, `docs/getting_started.md`, `docs/user_guide/your_workspace.md`, `docs/user_guide/modulation.md`, `docs/user_guide/performance_controls.md`, and `docs/user_guide/presets_and_library.md`.

### Suite C UI Panel Migration — Phase 2: Properties Panel (`PropertiesPanel.kt`, `UIManager.kt`, `SettingsPanel.kt`, `PresetGridRenderer.kt`, `Evaluators.kt`)
- **Panel Renaming (`Cell Config` → `Properties`)**: Renamed Column 2 (the center parameter and modulation editor panel) to **`Properties`**, progressing Phase 2 of the Suite C UI simplification.
- **Codebase Cleanliness & Zero-Trace Migration**: Renamed `CellConfigPanel.kt` to `PropertiesPanel.kt` (object `PropertiesPanel`), updated window titles, and replaced all internal references and empty-state captions across the codebase with zero remaining references in `src/`.
- **Documentation & User Guides**: Updated user guides, getting started diagrams, Settings shortcut references, and developer documentation to reflect `Properties`.

### Suite C UI Panel Migration — Phase 1: Mixer Panel (`MixerPanel.kt`, `MixerLayout.kt`, `UIManager.kt`, `MixerLayoutTest.kt`, `WindowLayoutSafetyTest.kt`)
- **Panel Renaming (`Mixer / Monitor` → `Mixer`)**: Renamed Column 3 (the right-hand panel containing master output preview, crossfader, and 4-deck monitors) to **`Mixer`**, initiating Phase 1 of the Suite C UI simplification.
- **Codebase Cleanliness & Zero-Trace Migration**: Renamed `MixerMonitorPanel.kt` to `MixerPanel.kt` (class `MixerPanel`), `MixerMonitorLayout.kt` to `MixerLayout.kt` (`MixerLayoutCalculator`, `MixerLayout`), and updated all references, drawing calls, and unit tests across the codebase.
- **Documentation & User Guides**: Updated `ARCHITECTURE.md`, `docs/developer/ui.md`, `docs/getting_started.md`, `docs/user_guide/your_workspace.md`, and technical architecture documentation to consistently use `Mixer`.

### Coordinate-Space 2D View Transformations (`blit.vert`, `mandala/shader.vert`, `Renderer.kt`, `Shader.kt`)
- **Direct Coordinate-Space Zoom & Roll (`blit.vert`, `Renderer.kt`)**: Re-architected 2D View transformations (`View > Zoom` and `View > Rotate Z`) so scaling and in-plane roll operate in coordinate space during visual source generation, rather than blitting an intermediate 16:9 texture card onto `cleanFBO`.
- **Full-Screen Continuous Evaluation**:
  - For infinite procedural generators (e.g. "Brick Pattern" ISF, fractal noise, plasma), zooming out evaluates equations across a wider coordinate space, filling the entire screen with smaller pattern elements without rectangular borders.
  - For finite visual objects (e.g. Mandala), scaling down renders the centered object cleanly surrounded by transparent black space, allowing downstream feedback loops and spatial effects to expand and fill the entire screen.
  - When rotating in the Z axis (`Rotate Z`), patterns rotate smoothly without revealing spinning rectangular boundaries or cropped corners.
- **Zero Tiling / Quad Borders**: Eliminated texture card wrapping and tiling heuristics. Dynamic 2D sources render directly to `cleanFBO` with `uZoom`, `uRotateZ`, and `uAspectRatio` injected into `blit.vert` and `mandala/shader.vert`.
- **Feedback Zoom Disambiguation (`feedback.frag`, `Renderer.kt`, `web/renderer.js`)**: Disambiguated the feedback zoom uniform from vertex-stage 2D view zoom by renaming it to `uFbZoom`. Resolves a critical bug where setting `FB Zoom` to small positive values (e.g. `0.001`) inadvertently triggered camera zoom in `blit.vert`, instantly shrinking the quad UVs 1000× to a pinprick. Explicitly initialized fullscreen quad passes (`feedbackShader`, `mixerShader`, `blitShader`) with identity vertex uniforms.

### GitHub Actions Automated Release Notes Scoping (`release.yml`, `docs/release_notes.md`, `RELEASE_NOTES.md`, `DECISIONS.md`)
- **Scoped Release Notes Extraction**: Configured automated release note extraction in `release.yml` to extract only the notes added between `prev_tag` and `HEAD` from candidate release notes files via `git diff`. Eliminates historical accumulation where previous release notes persisted into newly published releases.
- **Fallback to Topmost Unreleased Section**: When `prev_tag` is not available, extraction scopes strictly to the first `### ` section under `## [Unreleased]`, preventing unbounded multiversion capture.
- **Release Documentation Consolidation**: Cleanly filed 0.9.1 feature freeze sections (Phase 1–4 Ableton Link & ISF management, PipeWire 0.3 video ingest) under `## Version 0.9.1`, synchronizing `docs/release_notes.md` and `RELEASE_NOTES.md`.

### Architecture Compliance & Zero-Allocation Render Loop Optimizations (`VisualSourceRegistry.kt`, `Main.kt`, `MidiMappingManager.kt`, `ParameterResolver.kt`, `CVRegistry.kt`, `Evaluators.kt`, `Deck.kt`, `PresetManager.kt`)
- **Thread 0 OpenGL Compilation Discipline (`VisualSourceRegistry.kt`, `Main.kt`)**: Added `pendingGlTasks` concurrent queue to `VisualSourceRegistry` and `processPendingGlTasks()` called exclusively on Thread 0 inside `Main.kt` render loop. Background source scans now delegate `Shader(...)` compilation to Thread 0, completely preventing GLFW/GL driver errors on Linux. Initial startup scans default to synchronous (`async = false`) compilation on Thread 0 before entering the render loop.
- **Pre-Resolved Flat Array MIDI CC Updating (`MidiMappingManager.kt`, `Deck.kt`, `PresetManager.kt`)**: Replaced per-frame string parsing and recursive parameter tree traversals in `MidiMappingManager.update(mixer)` with an indexed flat `ResolvedMidiBinding` array. Resolved bindings are invalidated and rebuilt only when MIDI mappings change or when presets/sources are swapped.
- **Concurrent Parameter Path Memoization (`ParameterResolver.kt`)**: Implemented a thread-safe `ConcurrentHashMap` path resolution cache in `ParameterResolver`, eliminating O(N) recursive tree traversals on modulators and MIDI mappings.
- **Indexed CV Updates & Primitive Bit-Packed MIDI CC Keys (`CVRegistry.kt`)**: Maintained active non-audio CV sources in a contiguous `activeNonAudioSources` array traversed with primitive integer index bounds, avoiding per-frame map iterations. Incoming MIDI CC lookups now bit-pack channel and CC values into 64-bit primitives (`(channel.toLong() shl 32) or cc.toLong()`), removing per-frame string splitting.
- **Zero-Allocation Audio Follower Lookup (`Evaluators.kt`)**: Guarded `AudioFollowerTracker.process` with direct state map lookup before falling back to `computeIfAbsent`, eliminating capturing lambda heap allocations in the audio evaluation pipeline.
- **Broadcast Settings Test Determinism (`BroadcastSettings.kt`, `BroadcastSettingsTest.kt`)**: Added `BroadcastSettings.resetDefaults()` and isolated test cases to guarantee that existing user configuration files do not bleed into test assertions.

### Web Broadcast Configuration & Dynamic Menu Visibility (`BroadcastSettings.kt`, `BroadcastEngine.kt`, `SettingsPanel.kt`, `MenuBar.kt`, `Main.kt`)
- **Removed Hardcoded Credentials**: Removed hardcoded default relay server URL (`http://spaz.org/lsd-relay`) and broadcaster secret token (`lsd25`) from `BroadcastSettings.kt`, initializing both as empty strings (`""`).
- **Dynamic Output Menu Item**: Hid the `Output > Web Broadcast` menu item in `MenuBar.kt` when either the relay server URL or broadcaster token is not populated in Settings (`BroadcastSettings.isConfigured`).
- **Safeguarded Broadcast Connection**: Added checks in `BroadcastEngine.startBroadcast()`, `BroadcastEngine.connectAsync()`, `SettingsPanel.kt` (disabling "Go Live" with helpful tooltip when unconfigured), and `Main.kt` startup logic to prevent connection attempts without valid credentials.

### UI Text Formatting & Font Glyph Safety (`UITheme.kt`)
- **Format String Corruption Fix (`UITheme.kt`)**: Replaced calls to native `ImGui.textColored(...)` in semantic text helpers (`bodyColored`, `h1Colored`, `h2Colored`, `h3Colored`, `captionColored`, `codeColored`) with explicit `PushStyleColor(ImGuiCol.Text, ...)` + unformatted `ImGui.text(text)` + `PopStyleColor()`. `ImGui.textColored` passes text strings as printf format strings (`fmt`), which caused arbitrary memory reads and corrupted text whenever strings contained `%` characters (such as the Beat Tracker confidence percentage readout `"$confPercent%"`).
- **Caption Text Safety (`UITheme.kt`)**: Implemented `UITheme.caption(...)` via `PushStyleColor(ImGuiCol.Text, getColorU32(ImGuiCol.TextDisabled))` + `ImGui.text(text)` + `PopStyleColor()`, removing vulnerable `ImGui.textDisabled(text)` format string handling.
- **Lucide Icon Support in H1 / H2 Headers (`UITheme.kt`)**: Enabled `withIcons = true` when loading `fontH1` and `fontH2` in `UITheme.loadFonts()`, resolving missing glyph `?` placeholders when rendering headers containing `Icons.*` (such as `theme.h2("${Icons.SETTINGS} Beat Clock Mode")`).

### Ableton Link & Master Tempo Deck Architecture (`TempoSyncPanel.kt`, `LinkSyncManager.kt`, `BeatTrackToLinkDamping.kt`, `AudioEngine.kt`, `AudioEnginePanel.kt`, `MenuBar.kt`, `SettingsPanel.kt`, `UITheme.kt`)
- **Resolume-Inspired Master Tempo Deck (`TempoSyncPanel.kt`)**: Extracted timing and tempo controls into a dedicated "Tempo & Sync" settings category with intuitive transport controls: large BPM readout with visual beat-flash indicator, 4-beat bar progress dots, tap tempo button, manual ±0.1 / ±1.0 BPM nudging, downbeat resync, and clock source selection (`AUDIO_TRACKER`, `ABLETON_LINK`, `MANUAL_TAP`).
- **Clean Separation of Concerns**: Streamlined `AudioEnginePanel` to focus purely on hardware devices, backends (JACK / Java Sound), stereo channel routing, gain/volume, and audio signal/CV oscilloscopes, while moving high-level timing and network sync to `TempoSyncPanel`.
- **Enhanced MenuBar Tempo Navigation**: Right-clicking the BPM indicator or clicking the 4-beat phase dots now directly opens the dedicated Tempo & Sync settings deck, with new quick-access menu items for toggling Ableton Link and opening configuration.
- **Eliminated `SyncMode` State Machine**: Removed the redundant `SyncMode` enum (`DISABLED`, `LINK_FOLLOWER`, `AUDIO_BROADCAST`), mode transition lock, background coroutine jobs, and `AudioTempoEventSink` delegation interface.
- **Direct Ableton Link State Integration**: Ableton Link synchronization is now governed directly by `AbletonLinkEngine.isEnabled`. When enabled, audio beat tracker tempo/phase updates pass through `BeatTrackToLinkDamping` signal filtering (lambda callbacks `onTempoCommitted` and `onBeatAligned`) directly to `AbletonLinkEngine`.
- **Streamlined UI & Menus**: Removed `SyncMode` radio button controls and transmission status badges from `AudioEnginePanel`, and simplified the header status pill in `MenuBar` to clean `LINK [peers]` state indicators.

### Settings & UI Controls (`SettingsPanel.kt`, `AudioEnginePanel.kt`, `Main.kt`, `MenuBar.kt`, `UITheme.kt`, `AppSettings.kt`, `BrowserRowMoreButton.kt`)
- **Audio Engine & General Settings Layout Refinement (`AudioEnginePanel.kt`, `SettingsPanel.kt`)**:
  - Moved the MIDI detection hardware status readout from `AudioEnginePanel` to `SettingsPanel > General`, positioning it inline to the right of the "Enable MIDI" checkbox.
  - Placed dropdown combo boxes for Audio Backend, Input Hardware Device, and Channel Routing inline on the same line as their text labels in `AudioEnginePanel`.
  - Moved Input Gain and System Volume sliders from the right column to the left column directly below Channel Routing.
  - Replaced the standalone "Driver: [Backend]" section with colored status badges ("Jack active", "Java Sound Active", or "Audio Inactive") positioned inline to the right of the "Enable Audio Engine" checkbox.
  - Commented out the "Switch to JACK Audio" reconnect button with `// TODO: make this button less annoying`.
- **Settings Modal Closure Fix**: Fixed an issue where toggling "Enable MIDI" in Settings > General would immediately close the Settings modal window. Row context buttons (`BrowserRowMoreButton` and Preset Grid column kebab) now inspect ImGui widget hover states (`ImGui.isItemHovered()`) rather than raw mouse screen coordinates, preventing clicks inside modal windows from erroneously firing background row popups and closing active modals.
- **Settings Layout & Categories Redesign**: Reorganized the Settings modal interface to promote **General** as the primary first tab. Moved parameter randomization, step sequencer, MIDI settings (and CC mappings), frameless window toggle, and SCS.3m trackpad console controls into a unified **Features** section on the General tab.
- **Video & Display Settings Streamlining (`SettingsPanel.kt`)**: Refined the layout under **Settings > Video & Display**:
  - Moved the "Internal render resolution..." caption inline to the right of the "Render Resolution" section header (`h2`).
  - Set the resolution preset dropdown width to 0.4x content width, removed its redundant text label, and configured popup height to display all items without scrollbars.
  - Removed the active size text readout line below the resolution dropdown.
  - Set the Display Scaling dropdown width to 0.4x content width and removed its redundant text label ("Output Scaling").
  - Moved the recording framerate dropdown inline to the right of the "Recording Framerate:" text label with 0.6x width.
- **Minimum Settings Modal Width (1000px)**: Updated `SettingsPanel` window size constraints and default sizing to enforce a minimum modal width of `1000px`.
- **Inline Trackpad Status**: Positioned the SCS.3m touchpad status readout and Polkit permission installer button inline to the right of the Enable Trackpad Console checkbox.
- **Startup & AutoVJ Dropdown Width Expansion**: Expanded the width of the **Startup Behavior** and **AutoVJ Dirty Behavior** dropdown combo boxes under Settings > General back to 1/3rd of panel content width (min 160px) for optimal readability.
- **Preset Size Slider Read-Only & Clean Layout**: Removed label text to the left of the Library Preset Name Size slider and marked its value text box as read-only (`ImGuiInputTextFlags.ReadOnly`) so it acts cleanly as a value display indicator driven by the slider.
- **Hardcoded Tap Tempo Key (`T`)**: Hardcoded global keyboard tap tempo to `T` in `Main.kt`, removing the `tapKeyTrigger` setting, enum, and UI toggle from Settings entirely.
- **Queue Key Trigger Toggle Deprecation**: Removed the `queueKeyTrigger` dropdown combo box from the MIDI & Controls settings panel pending future UI redesign, and updated the Keyboard Shortcuts cheatsheet table accordingly.

### Test Suite Isolation & Real-Time Audio Teardown (`AudioEngineTest.kt`, `CvModulatorTest.kt`)
- **AudioEngine Teardown & Lifecycle Isolation**: Added `@AfterTest` lifecycle teardown and `try-finally` safety in `AudioEngineTest` to immediately invoke `AudioEngine.stop()`, reset audio parameters, and close active JACK / Java Sound client threads when tests complete. Prevents background real-time audio callback threads from leaking into subsequent test suites and asynchronously clobbering CVRegistry audio signals (`audio_amp`, `audio_bass`, etc.).
- **CV Modulator Audio Test Isolation**: Guaranteed `AudioEngine.stop()` and `AudioFollowerTracker.reset()` are called during unipolar and audio follower modulator tests in `CvModulatorTest` for test determinism.

### ISF GLSL Uniform Deduplication & Directory Registry Scoping (`ISFParser.kt`, `ISFFilterRegistry.kt`, `ISFTransitionRegistry.kt`, `VisualSourceRegistry.kt`, `ISFParserTest.kt`)
- **Automatic Standard Uniform Deduplication**: Updated `ISFParser.buildGLSLFragmentShader()` to automatically detect and strip preexisting standard uniform declarations (`TIME`, `uAlpha`, `RENDERSIZE`, `TIMEDELTA`, `FRAMEINDEX`, `DATE`, `PASSINDEX`) from shader source bodies while declaring them safely at global scope at the top. Prevents GLSL 3.30 compilation errors (`redeclared`) when shaders explicitly declare built-in uniforms.
- **Directory & Asset Registry Separation**: Restricted `ISFFilterRegistry`, `ISFTransitionRegistry`, and `VisualSourceRegistry` from cross-scanning mismatched directory types (e.g. generator sources in `library/sources` are no longer parsed as filters or transitions, and filters/transitions in `library/filters` or `library/transitions` are not loaded as visual sources).
- **Filter & Transition Classification Guard**: `ISFFilterRegistry` verifies shaders have image inputs (`inputImage`) and are not generators before compilation; `ISFTransitionRegistry` ensures shaders declare transition categories, `progress` inputs, or transition directory paths before registering them.

### Stereo Audio Pipeline, Channel Selector & Visual VU Input Metering (`AudioEngine.kt`, `JackClient.kt`, `JavaSoundClient.kt`, `AudioEnginePanel.kt`, `AppSettings.kt`, `UITheme.kt`)
- **Dual-Channel Audio Ingestion (Stereo Capture)**: Upgraded both `JackClient` (Linux JACK/PipeWire dual input ports `lsd:input_1` and `lsd:input_2`) and `JavaSoundClient` (cross-platform 16-bit stereo 44.1kHz/48kHz capture with zero-allocation PCM deinterleaving and mono-fallback mirroring) to feed true stereo audio into the DSP engine.
- **Channel Routing Selector (Mix, Left Only, Right Only)**: Added `AudioChannelRouting` with options:
  - `Mix (L + R)` (Default): Standard DJ/broadcast -6dB arithmetic downmix ($s[i] = 0.5 \cdot (L[i] + R[i])$) guaranteeing zero digital clipping on coherent in-phase material while matching unity gain when switching from single-channel mono.
  - `Left Only`: Routes Left channel at full level.
  - `Right Only`: Routes Right channel at full level (or silence if no right channel present).
- **Real-Time 2-Channel Visual VU Peak Meter**: Implemented a responsive stereo peak/RMS input meter in the Audio Engine settings panel. Includes fast-attack / exponential decay ballistics, 1.2s peak-hold tick indicators, multi-segment color thresholds (-12 dB green, -3 dB amber/yellow, 0 dB red), instant `CLIP` LED badge, and visual `[Bypassed]` badge highlighting when a channel is bypassed by routing.
- **Settings Persistence**: Serialized `audioChannelRouting` property in `lsd-settings.properties` through `UITheme` and `AppSettings`.
- **Zero-Allocation Callback Safety**: All stereo metering, channel routing, and pre-allocated buffer transfers adhere strictly to JACK real-time callback safety constraints without heap allocations.

### Code Audit Fixes — Beta 57–62 Surface (post-beta.62)

**Real-time safety:**
- **`BeatTrackToLinkDamping` — removed `ReentrantLock` from JACK audio thread path** (#AUDIT-RT-01): `processRawBpm` and `processBeatOnset` are now lock-free and allocation-free. Internal ring-buffer/EMA state is single-writer (audio thread only) — no synchronization needed. The two `@Volatile` public properties provide visibility to the UI thread without locks. Logger calls are now deferred via `AtomicReference<String?>` and drained by the render thread from inside `AbletonLinkEngine.updateClockAnchor()` (GL thread, once per frame).
- **`BeatTrackToLinkDamping.calculateMedian()` — pre-allocated sort scratch buffer**: `DoubleArray(historyCount)` was re-allocated each call inside `processRawBpm`. Now uses a class-level `sortScratch: DoubleArray(16)` reused across frames — zero allocation on the hot path.
- **`PipeWireReceiverImpl.update()` — removed `pw_thread_loop_lock` from GL render thread** (#AUDIT-RT-02): `pw_stream_dequeue_buffer` / `pw_stream_queue_buffer` are safe to call from the consumer thread without the loop lock; the loop lock is only required when modifying stream topology (which happens in `start()`).
- **`PipeWireBridge.publishFrameBuffer()` — removed `pw_thread_loop_lock` from GL render thread + fixed blank frames** (#AUDIT-RT-02, #AUDIT-FUNC-01): The old code locked the PW loop on every rendered frame AND never copied pixel data into the buffer (immediate enqueue with no data). Now uses a lock-free `AtomicReference<ByteBuffer?>` staging slot (`pendingFrame`). The GL thread deposits the buffer reference atomically; the PW event-loop thread drains it via `drainPendingFrame()`, copies the RGBA pixels into the SPA buffer data pointer, then queues it. This simultaneously fixes the GL-thread blocking AND the blank-output bug.
- **`AbletonLinkEngine.shutdown()` — use-after-free safety** (#AUDIT-LINK-03): `shutdown()` now sets `isEnabled = false` before calling `activeBackend.close()`. Since `updateClockAnchor()` (called from the render thread) guards with `if (!isEnabled) return`, any concurrent call returns immediately without touching the native handle being destroyed. Closes the shutdown race window.

**Rendering performance:**
- **`Renderer.kt` fbDecay cubic curve**: `Math.pow((1.0f - s).toDouble(), 3.0).toFloat()` → `val invS = 1.0f - s; invS * invS * invS`.
- **`ISFVisualSource.setupUniforms()` — zero-allocation DATE uniform**: Replaced `LocalDateTime.now()` / `LocalTime.now()` per-frame with pure primitive epoch arithmetic. Month lookup uses two constant `IntArray` tables (`MONTH_STARTS_NORMAL` / `MONTH_STARTS_LEAP`) in the companion object.
- **`ISFVisualSource.setupUniforms()` — `forEach` → indexed loop**: `header.INPUTS.forEach` allocated an `Iterator` each frame. Replaced with `for (i in 0 until inputs.size)` — allocation-free.
- **`ISFLibraryRegistry.allAssets` — cached sorted snapshot**: The getter previously called `.toList().sortedBy {}` on every access (every render frame while the library panel is visible). Now rebuilt once per scan into `@Volatile cachedAssets`. The render thread reads from the pre-sorted stable list — zero allocation.
- **`SpoutReceiverImpl.update()` — eliminate per-frame `ByteArray`/`IntArray`**: Promoted `ByteArray(256)` + `IntArray(1)` locals to class-level `recvNameBuf`/`recvWBuf`/`recvHBuf` fields reused across frames.
- **`SpoutReceiverImpl.start()` — release receiver & texture handles on sender switch**: Ensured `start()` cleans up previous native receiver handles (`ReleaseReceiver`, `ReleaseSpout`) and GL textures before connecting to a new sender.
- **`PipeWireReceiverImpl.update()` — zero-allocation SPA buffer ingest**: Pre-allocated reusable `SpaData` structure via `bindMemory(datasPtr)` and passed raw native memory address directly to `glTexSubImage2D`/`glTexImage2D`, completely eliminating per-frame `SpaData` and `ByteBuffer` wrapper heap allocations on Thread 0.
- **`PipeWireBridge` — background frame draining worker & SPA buffer mapping**: Wired `drainPendingFrame()` to a dedicated background IO coroutine worker so that frames deposited via `publishFrameBuffer()` are reliably drained and published to PipeWire consumers without blocking Thread 0. Fixed SPA buffer data pointer resolution using `reusableSpaData.bindMemory(datasPtr)` to properly map pixel buffer offsets.
- **`SyphonBridge.textureSizeForImage()` — eliminated per-frame `IntArray` allocation**: Replaced per-call `intArrayOf(0, 0)` with a class-level reusable buffer.
- **`SyphonBridge.publishTexture()` — eliminate per-frame anonymous JNA `Structure` objects**: Replaced with named inner classes `NSSize` / `NSRect` whose instances are created once and mutated in-place. Fields use `Double` (matching `CGFloat` on Apple Silicon LP64 ABI), fixing silent coordinate corruption on ARM64 macOS.
- **`ExternalVideoDiscovery.kt` — cached Spout library & zero-allocation polling**: Cached `SpoutLibrary` JNA bindings across polling cycles and eliminated per-poll string allocations when sender count is 0 or unchanged.
- **`TextureStreamer.kt` / `VideoOutputSettings.kt` — Spout sender name clamping**: Enforced `.take(255)` on Spout sender identifier strings before JNA calls to prevent native C-string buffer overflows.
- **`SettingsPanel` shader location actions — non-blocking async rescan**: All four ISF scan-triggering actions (Add Folder, Rescan Now, Remove, enable/disable toggle) now call `scanLibraryAsync()` instead of `scanLibrary()`. Disk I/O no longer blocks the ImGui render thread.

**Link/audio correctness:**
- **`AbletonLinkEngine.updateClockAnchor()` — capture-and-commit atomicity** (#AUDIT-LINK-01): Now uses a single `timeUs` snapshot for both `getTempo()` and `getBeatAtTime()` queries, ensuring tempo and beat phase are always coherent.

**UI performance:**
- **`ShaderPickerPopup` — `joinToString` per row per frame**: Category string now cached in `ShaderItem.categoriesLabel` at `updateItems()` time — zero allocation during table rendering.
- **`ShaderPickerPopup` — `categories.toList()` defensive copy**: Removed unnecessary copy; the list is only mutated from the same ImGui thread.
- **`PresetListPanel.kt` — zero-allocation search filter cache**: Search query filtering over preset assets previously reallocated `List`, iterators, and lambdas every frame at 60 FPS. Now caches `cachedFiltered` and only re-filters when `query` or the underlying `allPresets` list changes — zero per-frame allocation on the hot path.
- **Startup Asynchrony & ISF Scanning UI Progress Indicator (`Main.kt`, `VisualSourceRegistry.kt`, `ISFLibraryRegistry.kt`, `MenuBar.kt`)**: Decoupled bundled source initialization from directory scanning during startup. Bundled generators and defaults load synchronously on Thread 0 so decks and mandala initialization complete instantly, while recursive directory scanning runs asynchronously in the background. Exposed `isScanning`, `scanProgress`, and `scanCurrentPath` telemetry on `ISFLibraryRegistry`, rendering a non-intrusive status pill in `MenuBar` with live percentage and tooltip diagnostics during background indexing.
- **Comprehensive Session Missing Items Accumulation (`PresetManager.kt`, `MissingItemsPanel.kt`)**: Refactored `resolveRestoredQueue` to return `unresolvedPaths` without prematurely overwriting `sessionState.unresolvedItems`. Unified missing item accumulation across main play queue, background queue, transition filters, deck visual sources, and FX slot filters so earlier missing items are never erased. Enhanced `MissingItemsPanel` to render missing shader/filter diagnostics cleanly while restricting file browser re-linking to missing queue files.

**Serialization & Protocol Safety:**
- **`WebPresetSerializer.kt` — numeric formatting & scientific notation avoidance**: Enhanced `round4` to clamp sub-micro near-zero floats to `0.0f` to ensure standard decimal notation without scientific exponential notation for WebGL2 TV clients.

---

## Version 0.9.1

> [!NOTE]
> **Release 0.9.1** marks the official stable transition from `1.0.0-beta.x` to the `0.9.x` production release line. It bundles all feature-frozen capabilities including Ableton Link beat/phase/tempo synchronization, PipeWire 0.3 Linux live video sharing, ISF v2.0 visual source generator and mixer transition shaders, Spout/Syphon live video ingest, modular post-processing FX slots, multi-touch trackpad performance console, and robust automated 5-platform CI/CD packaging and smoke-testing.

### 🌟 Key Stable Features & Architecture Highlights
- **Authoritative Semantic Versioning & 0.9.x Pipeline**: Transitioned project build, runtime versioning (`AppVersion.kt`), and GitHub Actions release automation (`release.yml`) to authoritative `0.9.x` production releases.
- **Ableton Link & Carabiner Peer Sync**: Multi-backend Ableton Link (`linux-x64`, `windows-x64`, `macos-x64`, `macos-arm64`) and TCP Carabiner synchronization with Phase 1-4 audio-to-link damping and broadcast engines.
- **Interactive Shader Format (ISF v2.0) & Mixxx-Style Management**: Full ISF generator, transition, and effect support with background async directory scanner (`ISFScanner`), file watcher live reload (`ISFFileWatcher`), and path precedence resolution.
- **Cross-Platform Live Video Ingest & Broadcast**: PipeWire 0.3 DMA-BUF / MemFd ingest on Linux, Spout2 on Windows, Syphon on macOS, and live WebSocket broadcasting.
- **Modular Post-Processing & Audio Reactivity**: Dual modular FX slots, ISF multi-pass support, LFO/audio follower min/max bounds conversion, and CapLock multi-touch trackpad performance console.
- **Automated Multi-Platform Verification**: Comprehensive 5-platform CI/CD binary packaging and smoke testing ensuring 100% reliability across Linux, macOS, and Windows.

### Phase 4: Mixxx-Style ISF Library Management, Asynchronous Scanner, File Watcher Live Reload & Preferences Pane (`ISFDirectoryModels.kt`, `ISFDirectoryManager.kt`, `ISFScanner.kt`, `ISFLibraryRegistry.kt`, `ISFFileWatcher.kt`, `SettingsPanel.kt`)
- **Platform-Standard ISF Search Locations**: Pre-populates default search directories for macOS (`/Library/Graphics/ISF/`, `~/Library/Graphics/ISF/`), Windows (`%ProgramData%\ISF\`, `%LOCALAPPDATA%\ISF/`), Linux (`/usr/share/isf/`, `/usr/local/share/isf/`, `$XDG_DATA_HOME/isf/`), and internal application asset bundles.
- **Robust Path Expansion & Variable Resolution**: Automatically expands `~`, Windows `%ENV_VAR%`, and Unix `$ENV_VAR` / `${ENV_VAR}` variables into absolute canonical paths.
- **Directory Lifecycle & Status Tracking (`DirectoryStatus`)**: Real-time evaluation of path accessibility (`Active`, `Missing`, `Unreadable`). Unplugged external SSDs are marked as `Missing` and retained in user configuration without being purged.
- **Persistent JSON Configuration**: Serializes user directory paths and active toggle states into `library/isf_directories.json`.
- **Asynchronous Scanner & Safe Header Parser (`ISFScanner.kt`, `ISFLibraryRegistry.kt`)**: Non-blocking background recursive scanner indexing generators, filters, and transitions with robust exception resilience for malformed or truncated ISF JSON headers.
- **Precedence Collision Resolution**: Deterministic collision handling when unique shader IDs collide, honoring source priority: `Custom` (4) > `UserStandard` (3) > `SystemStandard` (2) > `BuiltIn` (1).
- **Cross-Platform File Watcher & Live Reload (`ISFFileWatcher.kt`)**: Background directory monitoring via Java NIO `WatchService` with a 250ms debounce mechanism preventing compiler errors during external editor saves.
- **Mixxx-Style "Shader Locations" Preferences Pane (`SettingsPanel.kt`)**: Settings UI displaying origin badges, enable/disable checkboxes, drive status indicators, "Add Folder", "Remove Folder" (with built-in path protection), and "Rescan Now" controls.
- **Comprehensive Unit & Integration Test Suite**: Added thorough test coverage in `ISFDirectoryManagerTest.kt`, `ISFLibraryRegistryTest.kt`, and `ISFFileWatcherTest.kt`.

### Phase 3: Carabiner TCP Command Integration & UI State (`CarabinerTcpLinkBackend.kt`, `LinkSyncManager.kt`, `AudioEnginePanel.kt`, `MenuBar.kt`, `CarabinerTcpLinkBackendTest.kt`)
- **Asynchronous Carabiner Outbound Command Queue**: Refactored `CarabinerTcpLinkBackend` to use a lock-free `ConcurrentLinkedQueue<String>`, ensuring network socket writes never block the audio processing or UI rendering threads.
- **Carabiner Protocol Integration & Formatting**: Formatted outbound commands using `Locale.US` for `bpm <val>` tempo updates and timestamped `beat <val> [timeUs] [quantum]` phase alignment.
- **Automatic Socket Reconnection & Recovery**: Isolated all socket I/O to a background daemon thread (`CarabinerTcpClient`) with a 3-second auto-reconnect loop upon socket disconnection or Carabiner daemon restarts.
- **Observable Link UI State**: Exposed `activeBpm` (formatted to 1 decimal place), `confidence` (beat tracking stability metric), and `isTransmitting` (`SyncMode.AUDIO_BROADCAST` active & connected).
- **UI Status Controls & Badges**: Integrated `SyncMode` radio controls, `[TRANSMITTING]` / `[STANDBY]` status indicators, Beat Tracker confidence stability progress bar, and header menu bar status pill in `AudioEnginePanel.kt` and `MenuBar.kt`.
- **Comprehensive Unit Test Suite**: Created `CarabinerTcpLinkBackendTest.kt` with a mock Carabiner server verifying non-blocking command queuing, protocol parsing, auto-reconnection, and transmission state.

### Phase 2: Audio Beat Tracker to Link Signal Conditioning & Damping Bridge (`BeatTrackToLinkDamping.kt`, `LinkSyncManager.kt`, `AppSettings.kt`, `UITheme.kt`, `BeatTrackToLinkDampingTest.kt`)
- **`BeatTrackToLinkDamping` Signal Conditioner**: Created signal conditioner to filter raw audio beat tracking micro-fluctuations before network transmission.
- **Sanity Bounds & Trajectory Smoothing**: Filters raw BPM outside 60–200 BPM, applying a 7-sample rolling median filter to discard onset outliers followed by alpha Exponential Moving Average (EMA) smoothing.
- **Quantization & Hysteresis**: Prevents peer timeline warping by requiring a >= 0.5 BPM divergence sustained for at least 4 consecutive beats before committing outbound tempo changes to Carabiner.
- **Major Phase Error Realignment**: Measures beat onset phase error against Carabiner's timeline clock, only publishing beat realignments when phase error exceeds >= 0.5 beats (half-beat) to eliminate acoustic onset jitter stutter.
- **Settings Persistence & Unit Testing**: Integrated damping parameters into `AppSettings` and `UITheme`, backed by a unit test suite (`BeatTrackToLinkDampingTest.kt`) verifying median/EMA smoothing, hysteresis counters, and phase error thresholding.

### Phase 1: Ableton Link Master & Audio Broadcast Architecture (`SyncMode.kt`, `LinkSyncManager.kt`, `AudioEngine.kt`, `AppSettings.kt`, `UITheme.kt`, `LinkSyncManagerTest.kt`)
- **Tri-State Sync Mode State Machine**: Created `SyncMode` enum (`DISABLED`, `LINK_FOLLOWER`, `AUDIO_BROADCAST`) to cleanly separate follower and broadcast responsibilities.
- **`LinkSyncManager` Orchestration**: Created central thread-safe manager for mode transitions, active coroutine job cancellation to break echo loops, and status telemetry (`isLinked`, `peersCount`, `currentMode`, `activeBpm`).
- **`AudioTempoEventSink` Interface**: Added callback interface with `onTempoCommitted(bpm)` and `onBeatAligned(beatTime, microsecondTimestamp, quantum)` for emitting audio beat tracker tempo and downbeat events in `AUDIO_BROADCAST` mode.
- **Audio Thread Lock-Free Dispatch**: Updated `AudioEngine` to publish audio beat tracker tempo and beat alignment events to `LinkSyncManager` without heap allocations or thread blocking.
- **Performer Gig Guardrail**: Enforced safety rule where app launch always defaults to `SyncMode.DISABLED` (sanitizing any saved `AUDIO_BROADCAST` state) to prevent accidental live network timeline takeovers.
- **Comprehensive Unit Testing**: Added `LinkSyncManagerTest.kt` verifying state transitions, event sink dispatching, thread-safe concurrent toggling, and startup safety sanitization.

### Zero-Copy Linux Video Sharing & Ingest via PipeWire 0.3 (`PipeWireLibrary.kt`, `PipeWireBridge.kt`, `TextureReceiver.kt`, `ExternalVideoDiscovery.kt`, `ExternalVideoSource.kt`, `TextureStreamer.kt`, `SettingsPanel.kt`)
- **Native PipeWire 0.3 JNA Bindings**: Implemented `PipeWireLibrary.kt` to dynamically bind `libpipewire-0.3.so` functions (`pw_init`, `pw_thread_loop_*`, `pw_context_*`, `pw_stream_*`) and SPA video parameters without hard compile-time dependencies.
- **PipeWire Bridge & Stream Lifecycle**: Implemented `PipeWireBridge.kt` managing native thread loop initialization, stream creation, and format negotiation with PipeWire consumers (OBS Studio, Wayland compositors, qpwgraph, WirePlumber).
- **Native Linux Video Ingest (`PipeWireReceiverImpl` & `fetchPipeWireStreams`)**: Created `PipeWireReceiverImpl` in `TextureReceiver.kt` to ingest external live PipeWire video feeds into OpenGL textures, and `fetchPipeWireStreams()` in `ExternalVideoDiscovery.kt` to auto-discover active PipeWire video nodes (`pw-dump` / `pw-cli`).
- **Asynchronous PBO DMA Readback**: Updated `LinuxTextureBridge` in `TextureStreamer.kt` with `PboReadbackPipeline` async GPU-to-CPU buffer transfers, avoiding main-thread OpenGL stalls.
- **GPU DMA-BUF Export & MemFd Fallback**: Automatically exports GPU DMA-BUF buffers when Mesa/EGL drivers are present, with graceful fallback to PipeWire shared memory (`SPA_DATA_MemFd`) and `NullTextureStreamer` when PipeWire is absent.
- **Settings Telemetry & Driver Status**: Added active video driver readout (`PipeWire 0.3 (Linux)`, `Spout2 (Windows)`, `Syphon (macOS)`) and hover tooltips in `SettingsPanel.kt`.

---

## Version 1.0.0-beta.62

> [!NOTE]
> **Release 1.0.0-beta.62** introduces Ableton Link peer-to-peer beat, phase, and tempo synchronization across local networks with a tri-state clock source engine, ISF mixer transition shaders with full fallback and composite preservation, Spout and Syphon live video ingest with dynamic server discovery, and dual modular FX slots with multi-pass ISF and spatial distortion.

### Phase 3: Musical Timing — Ableton Link Integration (`ClockSource.kt`, `AbletonLinkEngine.kt`, `LinkBackend.kt`, `NativeJniLinkBackend.kt`, `CarabinerTcpLinkBackend.kt`, `NativeLibraryLoader.kt`, `MenuBar.kt`, `AudioEnginePanel.kt`, `SettingsPanel.kt`, `AppSettings.kt`, `UITheme.kt`, `AbletonLinkEngineTest.kt`)
- **Tri-State Clock Source Core**: Integrated `ClockSource` enum (`AUDIO_TRACKER` for audio beat tracker FFT onset engine, `ABLETON_LINK` for network peer sync, `MANUAL_TAP` for internal fixed tempo & VJ tap tempo).
- **Multi-Backend Ableton Link Architecture**:
  - Native JNI C++ bridge (`link_jni` embedding `ableton::Link`) for sample-accurate peer-to-peer beat, phase, and tempo sync across `linux-x64`, `windows-x64`, `macos-x64`, and `macos-arm64`.
  - Carabiner TCP socket backend (`CarabinerTcpLinkBackend`) connecting to local Carabiner Link daemon (`127.0.0.1:17000`).
  - Graceful `NoOpLinkBackend` fallback when Link is inactive.
- **Top MenuBar Telemetry & HUD Pill**: Added `LINK [N peers]` status pill, quantum bar phase ring, active driver readout, and clock source dropdown selector (`[AUDIO]`, `[LINK]`, `[MANUAL]`).
- **Audio Engine & Settings Controls**: Added dedicated Ableton Link configuration card in `AudioEnginePanel` with quantum selector (1, 4, 8, 16 beats) and start/stop transport sync support.
- **Settings Persistence & Unit Tests**: Full persistence of clock source and Link configuration in `AppSettings` / `UITheme`, with unit test suite in `AbletonLinkEngineTest.kt`.

### Phase 2.3: ISF Mixer Transitions & Fallback Architecture (`ISFTransitionRegistry.kt`, `ISFFilter.kt`, `Mixer.kt`, `Renderer.kt`, `ShaderPickerPopup.kt`, `MixerMonitorPanel.kt`, `PresetGridPanel.kt`, `PresetModels.kt`, `PresetManager.kt`, `WebPresetSerializer.kt`)
- **Extensible ISF Transition Engine**: Integrated shader-based crossfader transitions accepting `startImage` (Deck A), `endImage` (Deck B), and `progress` ($0.0 \dots 1.0$).
- **Fallback Non-ISF Mixer**: Guaranteed seamless fallback to built-in non-ISF `mixer.frag` blend modes (`ADD`, `SCREEN`, `MULT`, `MAX`, `XFADE`) when `transitionFilter == null`.
- **Composite Chain Preserved**: Transition output is composited in `mixer.frag` with Deck BG (`uTexBG`), channel gain multipliers, master bloom, and master alpha.
- **Shader Picker Integration**: Reused `ShaderPickerPopup` (`PickerType.MIXER_TRANSITION`) for instant fuzzy search, category pill filtering, and transition detaching.
- **Modulatable Transition Parameters**: Transition shader inputs (e.g. wipe direction, softness, glitch intensity) register in the Preset Grid Mix tab for LFO, audio reactivity, and MIDI modulation.
- **Session Serialization & Web Broadcast**: Full persistence of `transitionSlot` in `SessionStateDto` and JSON broadcast serialization for web clients.
- **Bundled Transitions**: Shipped default transition shaders: `linear_crossfade.fs`, `wipe_horizontal.fs`, `wipe_vertical.fs`, `radial_wipe.fs`, `glitch_transition.fs`, `luma_wipe.fs`, and `zoom_fade.fs`.

### Phase 4: Video Processing — Spout, Syphon & PipeWire Input (`TextureReceiver.kt`, `TextureStreamer.kt`, `ExternalVideoDiscovery.kt`, `ExternalVideoSource.kt`, `Renderer.kt`, `VisualSourceRegistry.kt`, `PresetGridTabs.kt`, `PresetModels.kt`, `ExternalVideoSourceTest.kt`, `Main.kt`)
- **Native Live Video Ingest**: Ingest live video feeds from external applications (webcams, OBS, Resolume, TouchDesigner) via Spout2 on Windows (`SpoutReceiverImpl`), Syphon on macOS (`SyphonReceiverImpl`), and PipeWire 0.3 on Linux (`PipeWireReceiverImpl`).
- **Complete Rendering Pipeline Integration**: Integrated `renderExternalVideoSource` in `Renderer.kt`, blitting incoming video textures (`currentTextureId`) directly into deck framebuffers (`rawSource2DFBO` / `rawSourceFBO`) with full downstream 2D/3D transformations, feedback loops, and dual ISF post-processing slots.
- **Dynamic Server Discovery**: Integrated background polling (`ExternalVideoDiscovery`) to automatically detect launched or closed external video servers and PipeWire video streams across the system (`fetchPipeWireStreams`).
- **Native Visual Source Integration & Registry Cleanup**: Added `ExternalVideoSource` to `VisualSourceRegistry` with single-instance guard checks, making external video feeds selectable visual generators within Decks.
- **UI Server Selector**: Implemented a dynamic server combo selector in `PresetGridTabs` for picking live external servers and PipeWire video streams.
- **Preset Serialization & Unit Tests**: Persisted `serverName` selection in `DeckPresetDto` for seamless connection restore on preset load, with full unit test coverage in `ExternalVideoSourceTest.kt` and `PipeWireBridgeTest.kt`.
- **Platform Scope**: Windows (Spout2), macOS (Syphon), and Linux (PipeWire 0.3 via `PipeWireReceiverImpl`) active across all platforms.

### Phase 2.2.2: Multi-Pass ISF Engine, Dual FX Slots & Universal Shader Picker (`ShaderPickerPopup.kt`, `ISFParser.kt`, `ISFFilter.kt`, `Deck.kt`, `Renderer.kt`, `PresetModels.kt`, `PresetGridTabs.kt`, `ISFMultiPassTest.kt`)
- **Dual FX Architecture**: Added a second serialized FX slot (**Slot 2: Spatial / Distortion**) to each Deck pipeline (`Visual Source` $\rightarrow$ `2D/3D Transform` $\rightarrow$ `cleanFBO` $\rightarrow$ `[Slot 1: Color / Degradation]` $\rightarrow$ `[Slot 2: Spatial / Distortion]` $\rightarrow$ `feedback.frag`).
- **Multi-Pass ISF Shader Processing**: Full support for multi-pass ISF shaders (`PASSES` array) with intermediate target FBOs and custom pass dimensions (e.g. `$WIDTH/2.0`, `$HEIGHT/2.0`).
- **Persistent Ping-Pong History Buffers**: Temporal feedback accumulation (`PERSISTENT: true`) using ping-pong FBO pairs without GPU read/write feedback hazards.
- **Universal Searchable Category Shader Picker**: Replaced flat dropdowns with `ShaderPickerPopup`, a zero-allocation modal dialog supporting instant fuzzy search and category filtering for Visual Sources and both FX Slots.
- **Dual FX Preset Serialization**: Full round-trip serialization of both FX slots (`fxSlot1` and `fxSlot2`) in `DeckPresetDto`, maintaining 100% backward compatibility for existing presets.
- **Bundled Multi-Pass & Spatial Filters**: Shipped default filters including `3d_elevation.fs` (unified 3D plane projection across Tri-Axial, Cube Cage, and Hex-Planar modes), `bloom.fs` (multi-pass bloom), `feedback.fs` (full ISF feedback loop with zoom, rotate, kaleidoscope, hue shift, chroma aberration, blur & mode blend), `feedback_trails.fs` (persistent temporal decay), `glitch.fs` (chromatic aberration and scanline dislocation), and `mirror.fs` (coordinate space folding).

---

## Version 1.0.0-beta.60

> [!NOTE]
> **Release 1.0.0-beta.60** introduces Phase 2.2.1 modular post-processing FX Slot 1 with native single-pass ISF filter engine integration across Deck pipelines, hardware dry/wet signal blending via `glBlendColor`, bundled creative filters, FX tab parameter controls, and preset serialization.

### Phase 2.2.1: Single-Pass ISF Filter Engine & Deck Pipeline Integration (`VisualEffect.kt`, `ISFFilter.kt`, `ISFFilterRegistry.kt`, `Deck.kt`, `Renderer.kt`, `PresetModels.kt`, `PresetGridTabs.kt`)
- **Modular FX Slot 1**: Integrated a dedicated post-processing FX slot into each Deck's rendering pipeline.
- **ISF Filter Support**: Native support for single-pass ISF (Interactive Shader Format) image filters with automatic parameter mapping.
- **Hardware Dry/Wet Blending**: Efficient signal blending using `glBlendColor` for minimal GPU overhead.
- **Bundled Filters**: Includes Invert, Hue Shift, Posterize, Luma Key, and Edge Detect filters.
- **Preset Grid Controls**: Added filter selection and parameter modulation controls to the FX tab.
- **Serialization**: Full preset serialization and round-trip support for FX slot configurations.

---

## Version 1.0.0-beta.59

> [!NOTE]
> **Release 1.0.0-beta.59** introduces native, first-class Interactive Shader Format (ISF v2.0) visual source support without requiring companion `meta.json` sidecar files, automatic GLSL 3.30 boilerplate injection, support for standalone `.fs`/`.isf` sources, and dual-format manifest testing.

### First-Class ISF Visual Source Support (`ISFModels.kt`, `ISFParser.kt`, `ISFVisualSource.kt`, `VisualSourceRegistry.kt`, `Renderer.kt`, `VisualSourceManifestTest.kt`, `Source3DModeTest.kt`)
- **First-Class ISF Source Format**: Visual source discovery and manifest verification now support standalone ISF files (`.fs`, `.isf`) and folder-based shaders without requiring an accompanying `meta.json` file.
- **ISF GLSL Preprocessor & Code Synthesis**: Added `ISFParser.buildGLSLFragmentShader()` which automatically injects GLSL 3.30 `#version` directives, fragment output mapping (`gl_FragColor`), normalized coordinates (`isf_FragNormCoord`), standard uniforms (`RENDERSIZE`, `TIME`, `TIMEDELTA`, `FRAMEINDEX`, `DATE`, `PASSINDEX`), and input uniforms into the shader source prior to compilation.
- **Support for Vector & Scalar MIN/MAX Schemas**: Enhanced parameter parsing to accept either scalar or vector array bounds for `point2D` and complex inputs.
- **Dual-Format Test Validation**: Updated `VisualSourceManifestTest` and `Source3DModeTest` to inspect either legacy `meta.json` or embedded ISF headers (`/*{ ... }*/`), allowing new visual sources to be pure ISF shaders.
- **ISF 3D Mode & Feedback Flags**: Added `is3D` and `feedback` metadata support to `ISFHeader` and automatic 3D mode classification based on `Rotate X` and `Rotate Y` parameter detection.
- **Colors Source Parity**: Preserved all 7 parameters (`Style`, `Hue`, `Sat`, `Val`, `Sweep`, `Speed`, `Zoom`) and restored full backwards compatibility.

---

## Version 1.0.0-beta.58

> [!NOTE]
> **Release 1.0.0-beta.58** introduces a multi-endpoint video sharing matrix allowing Decks and Master to be streamed simultaneously over GPU shared memory (Spout2 on Windows, native JNA Objective-C Runtime Syphon on macOS, Linux Texture Bridge) with per-endpoint resolution overrides and scaling modes.

### Multi-Endpoint Video Sharing Matrix (`VideoOutputSettings.kt`, `TextureStreamer.kt`, `SettingsPanel.kt`, `UITheme.kt`, `AppSettings.kt`, `Renderer.kt`, `Main.kt`)
- **Video Output Matrix**: Added configuration UI and pipeline routing for streaming individual Deck outputs (Deck A, Deck B, Deck BG, Deck PV) alongside Master output via Spout (Windows), Syphon (macOS), and Linux texture sharing bridges.
- **Native Syphon Bridge (macOS)**: Replaced legacy JNI-based Syphon wrappers with a modern, high-performance Objective-C Runtime bridge using JNA pointers. Supports Intel and Apple Silicon (ARM64) natively.
- **Native Spout2 Bridge (Windows)**: Integrated zero-copy GPU texture sharing using JNA bindings for `SpoutLibrary.dll`.
- **Independent Resolution & Scaling**: Added per-endpoint resolution overrides (`Sync to Master`, `4K UHD`, `1080p`, `720p`, `540p`) and scaling modes (`Fit`, `Fill`, `Stretch`).
- **GPU Rescaling Pipeline**: Implemented blit shader rescaling pass in `Renderer.kt` and `TextureStreamerManager` to dynamically resize and reformat textures to destination FBOs prior to broadcasting.
- **Settings Persistence**: Serialized multi-stream video output configurations into `lsd-settings.properties` using JSON mapping.

---

## Version 1.0.0-beta.57

> [!NOTE]
> **Release 1.0.0-beta.57** improves Cell Config panel responsiveness with a guaranteed minimum layout width, optimizes numeric input box sizing across modulators and audio engine settings, and introduces 100% default depth ergonomics for Step Sequencer and LFO 2.

### UI Layout & Space Optimization (`UIManager.kt`, `CustomRangeSlider.kt`, `AudioEnginePanel.kt`)
- **Cell Config Minimum Width**: Enforced a minimum width of 450px for the Cell Config panel. The Mixer Monitor now scales down to accommodate this space when necessary, ensuring configuration controls remain accessible even at lower resolutions or with all Preset Grid columns visible.
- **Improved Input Box Efficiency**: Optimized the width of standard numeric input boxes (Depth, Phase, etc.) to 44px (approx. 41.8px at 95% scale).
- **LFO Range Slider Alignment**: Fixed a rendering error where the LFO range slider overlapped the 'Max' numeric input box when randomization was disabled.
- **Proportional Audio Engine Sizing**: Adjusted the Audio Engine settings input boxes to 35.2px to maintain a 20% smaller proportionality relative to the new standard width.

### Sequencer & LFO 2 100% Default Modulation Depth (`CvModulator.kt`, `PresetGridRenderer.kt`, `CellConfigPanel.kt`, `Lfo2Section.kt`)
- **Step Sequencer 100% Depth**: New sequencer modulators now default to 100% (1.0) depth so that step values output 1:1 direct voltage without requiring manual depth adjustments.
- **LFO 2 100% Depth & Auto-Activation**: LFO 2 modulator depth now defaults to 100% (1.0) with middle-click reset to 100%, and automatically ensures full depth upon enabling an AM/PM/ADD modulation mode.

---

## Version 1.0.0-beta.56

> [!NOTE]
> **Release 1.0.0-beta.56** introduces human-readable modulation bounds for LFOs and Audio followers, providing intuitive dual-handled range sliders that replace abstract Depth/Offset math, alongside a streamlined Step Sequencer UI.

### Human-Readable Modulation Bounds (Min/Max) (`CvModulator.kt`, `CustomRangeSlider.kt`, `Lfo1Section.kt`, `AudioModulatorSection.kt`)
- **Min/Max Conversion**: Converted LFO 1 and Audio modulator user controls to explicit Min and Max value bounds.
- **Dual-Handled Sliders**: Replaced separate Depth and DC Offset sliders with a single, intuitive dual-handled range slider.
- **Two-Tier Randomization**: When randomization is enabled, the UI provides two dedicated range sliders to independently define the random drift for the Minimum and Maximum boundaries.
- **Backward Compatibility**: Preserved existing preset math and DTOs by retaining internal Depth/Offset storage with on-the-fly bidirectional conversion.

### UI Cleanup & Ergonomics (`SeqSection.kt`)
- **Sequencer Simplification**: Removed the redundant DC Offset control from the Step Sequencer UI to reduce visual clutter.

---

## Version 1.0.0-beta.55

> [!NOTE]
> **Release 1.0.0-beta.55** focuses on UI stability and visual ergonomics: streamlines tooltip text across the entire interface by removing volatile live numeric values that induced rapid dimension resizing, improves Gradle parallel tooling build performance, and aligns interactive documentation.

### UI Ergonomics & Tooltip Stabilization (`PresetGridRenderer.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `MenuBar.kt`, `MixerMonitorPanel.kt`, `DeckControlPanel.kt`, `SeqSection.kt`)
- **Static Dimensions & Zero-Resize Hover**:
  - Removed fast-fluctuating live numeric readout lines (e.g. `Live: 8.3 (base 4.0 +4.3)`, live modulated boundary speeds/values, audio callback DSP times, crossfader percentages) from tooltips.
  - Eliminates tooltip window resizing and layout jitter on every audio buffer or LFO phase tick while hovering over sliders, cells, and meters.
  - Parameter tooltips now present stable, high-contrast information: Title, valid Range, Factory Default, Engine Description, User Parameter Notes, and interactive shortcut hints (`Click to configure`, `Middle-click to reset`, `Scroll to adjust`).
- **Tooling Optimization (`gradle.properties`)**:
  - Enabled `org.gradle.tooling.parallel=true` for faster project synchronization and Gradle 9.4+ tooling model building.

## Version 1.0.0-beta.54

> [!NOTE]
> **Release 1.0.0-beta.54** introduces the Inter-App Ecosystem & Interoperability Roadmap, live VJ Tap Tempo engine with phase downbeat quantization, ergonomic tooltip quadrant positioning with zero-pivot frame stabilization and opacity enforcement, and `cvModulatorSlider` callback deduplication across all parameter sections.

### Inter-App Ecosystem & Interoperability Roadmap (`docs/developer/interop_roadmap.md`, `docs/index.md`)
- Published the comprehensive multi-phase architectural roadmap for integrating Liquid LSD with third-party VJ software, media servers, and DAWs (Resolume, MadMapper, TouchDesigner, VDMX, OBS Studio, Ableton Live, Bitwig):
  - **Phase 0 (Foundation & Ergonomics)**:
    - **0.1 (Tooltip Formatting Polish)**: Multi-tier hover layout unification, eliminating visual clutter and standardizing type badges, values, descriptions, and shortcut hints.
    - **0.2 (LFO Min/Max Conversion)**: Human-readable modulation bounds replacing abstract Depth and DC offset controls, paired with dual-ended range sliders and backward-compatible preset math.
  - **Phase 1 (Stage Utility)**: Zero-copy GPU video sharing via Spout (Windows) and Syphon (macOS), providing independent output streams for Deck A, Deck B, Deck BG, Deck PV, and Master composite, featuring per-output resolution scaling and automatic naming conventions (`LiquidLSD-DeckA`, etc.).
  - **Phase 2.1 (Content Library)**: Interactive Shader Format (ISF) parser to import thousands of open-source community visual generators and export Liquid LSD sources to third-party apps.
  - **Phase 2.2 (FX System & Dual FX Slots)**: Migration of post-processing to modular ISF effect chains featuring two dedicated, modulatable FX slots per deck — `[ Slot 1: Color / Degradation ]` (Luma Key, Hue Cycle, Posterize, Invert) and `[ Slot 2: Spatial / Distortion ]` (Feedback Trails, Glitch, Mirror, Edge Warp).
  - **Phase 2.3 (Mixer Transitions)**: Extensible ISF 2-image crossfade and transition shaders for custom Deck A/B blending.
  - **Phase 3 (Musical Timing)**: Ableton Link peer-to-peer beat, tempo, and quantum phase synchronization as a network alternative to BTrack audio onset detection.
  - **Phase 4 (Video Ingest & Processing)**: Spout/Syphon live video input as a selectable, automatable visual source with full preset persistence, 2D/3D geometry transforms, and audio-reactive feedback FX.

### `CvModulatorSliderHelpers.kt` — Randomizable Slider Callback Deduplication
- Introduced `cvModulatorSlider(...)` in `ui/CvModulatorSliderHelpers.kt`, a helper that generates the standard `onRandomizableChanged` / `onRandomizeNow` / `onRangeChanged` / `onValueChanged` callback bundle for any `CvModulator` field exposed via `drawCustomRangeSlider`.
- Eliminated ~280 lines of structurally identical boilerplate across `Lfo1Section`, `Lfo2Section`, `AudioModulatorSection`, and `MidiModulatorSection` (from 760+762+403+154 = 2079 total to 578+609+282+94+81 = 1644, a net reduction of ~435 lines including the new helper).
- Beat-subdivision (index-stepping) and Period/Frame (multiplicative halving/doubling) sliders retain inline `onRandomizableChanged` blocks because their expansion logic differs from the standard `±offset` pattern.

### VJ Tap Tempo & Phase Downbeat Synchronization (`TapTempoController.kt`, `AudioEngine.kt`, `BeatTrackerEngine.kt`, `MenuBar.kt`, `Mixer.kt`, `SettingsPanel.kt`)
- **Real-Time Tap Cadence Engine (`TapTempoController.kt`)**:
  - Implements an allocation-free circular buffer averaging the last 8 tap intervals with nanosecond resolution.
  - Automatically resets sequence cadence when tap intervals exceed 2.0 seconds (30 BPM cutoff).
  - Emits a transient visual flash on the BPM readout (300ms decay) and tracks active tap counts (`TAP [N]`).
- **Dual-Mode Audio Engine Integration (`AudioEngine.kt`, `BeatTrackerEngine.kt`)**:
  - **Manual / Locked Mode (`isBpmLocked = true` or Audio Disabled)**: Direct tempo update to `manualBpm` and instant downbeat phase quantization (`round(totalBeats)`), locking visual pulses and the 4-beat bar meter to the musical downbeat on tap.
  - **Live Audio Tracking Mode (`!isBpmLocked`)**: Nudges the beat tracker's candidate tempo, recalculates dynamic programming search center $\tau_0$, and injects a smooth phase nudge into `phaseSlewBuffer`, immediately breaking octave traps (half-time/double-time false locks) without visual jump discontinuities.
- **Mouse & Keyboard Interactions (`MenuBar.kt`, `Main.kt`, `SettingsPanel.kt`, `UITheme.kt`)**:
  - Clicking the `BPM: <val>` readout in the top bar triggers Tap Tempo using an invisible button target, firing immediately on mouse down (`isItemClicked(0)`).
  - While tapping, the BPM readout displays immediate visual confirmation: flashes in bright gold and renders the active tap count (`BPM: [TAP 1]`, `BPM: 128 [2]`).
  - Clicking the `DSP: <val>ms` badge (or 4-beat phase dots) opens the Audio Settings panel. The DSP badge is persistently visible even when audio is disabled (`DSP: OFF`) or inactive (`DSP: --`).
  - Added keyboard trigger support in GLFW key callback (`Main.kt`): pressing `T` (default) or `.` (configurable in Settings under `Tap Tempo Key`) triggers tap tempo when text inputs are not focused.
- **Beat & Flywheel Synchronization (`AudioEngine.kt`, `BeatTrackerEngine.kt`, `CVRegistry.kt`)**:
  - In manual/locked mode, `CVRegistry.alignBeatPhase()` instantly aligns visual pulses and CV oscillators to whole-beat boundaries without monotonic jitter filtering delay.
  - In audio tracking mode, `BeatTrackerEngine.nudgeTempo()` applies a stability lock (`isLocked = true`, `stableAccumulatedSec = stabilityLockDurationSec`) around the tapped BPM to keep the tracker locked to the tap.

### Ergonomic Tooltip Quadrant Positioning, Opacity Enforcement & Zero-Allocation Hover Delay (`TooltipHelper.kt`, UI Panels)
- **Mixxx/Qt-Inspired Quadrant Layout (`TooltipHelper.kt`)**:
  - Replaced Dear ImGui's default bottom-right tooltip placement (`mouse + 16, mouse + 10`) which routinely obscured parameter sliders, values, and adjacent UI controls.
  - Aligns tooltips beneath a fixed $16 \times 22\,\text{px}$ pointer bounding box with a $4\,\text{px}$ gap.
  - **Dynamic Right-Edge Anchoring**: Aligns tooltip left edge with the cursor box left edge by default; automatically flips to anchor its right edge against the cursor box right edge when close to the viewport right border (extending leftward).
  - **Bottom-Edge Overflow Flip**: Flips the tooltip above the cursor box when overflowing the bottom of the viewport/window.
  - **Safety Margins**: Maintains an $8\,\text{px}$ margin from all viewport edges with boundary coordinate clamping.
- **100% Solid Opacity Guarantee (`setNextWindowBgAlpha(1.0f)`)**:
  - Fixed semi-transparent tooltip rendering when hovering over bypassed modulators or inactive buttons.
  - Wrapped tooltip rendering in `ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 1.0f)` and `ImGui.setNextWindowBgAlpha(1.0f)` across all tooltip helpers, insulating tooltips from parent widget alpha inheritance and ensuring crisp, high-contrast legibility.
- **Zero-Allocation Hover Delay Tracker (`TooltipHelper.kt`)**:
  - Implements a non-allocating hover delay tracker ($250\,\text{ms}$ threshold) using spatial item hashing (`(minX shl 16) xor minY xor text.hashCode()`) and frame gap tracking.
  - Eliminates visual flicker and tooltip pop-in during swift cursor sweeps across matrix rows, sliders, and buttons without relying on Dear ImGui 1.88+ flags.
- **Zero Raw `setTooltip` / `beginTooltip` Across Entire Application**:
  - Migrated 100% of tooltips across all UI subsystems to `itemTooltip`, `showTooltip`, and `showCustomTooltip`:
    - `PresetGridRenderer` & `PresetGridPanel`: Value cells, MIDI cells, CV cells, parameter headers, column config kebab menu.
    - `PresetGridTabs`: Left deck tabs, top source selectors, and subtabs.
    - `ModulatorHeaderRow`: Power toggle, dice, operator dropdown, and clear controls.
    - `MixerMonitorPanel` & `OscilloscopeDrawer`: Master monitor, crossfader, fader badges, oscilloscope canvas, timebase selector, and mute controls.
    - `MenuBar`: Menu items, Recording HUD, Web Broadcast HUD, Beat Phase dots, BPM readout, DSP badge, frameless CSD window controls.
    - `SettingsPanel` & `UpdatePromptModal`: All preference sliders, checkboxes, directory pickers, and modals.
    - `LibraryPanel` & `browser/*`: Preset list issue badges, Playlists, Play Queue, Background Queue, and action toolbars.

### Tooltip Polish — Round 2 & 3: Parameter Tooltip Positioning, Zero-Pivot Stabilization, Border & Text Isolation (`TooltipHelper.kt`, `UIThemeStyler.kt`)

**Root cause of tooltip jumping and flickers**:
- In Dear ImGui, passing non-zero pivot offsets (e.g. `pivotX = 1.0f` or `pivotY = 1.0f`) to `SetNextWindowPos` evaluates `pos -= window->SizeFull * pivot`. On frame 1 of a newly appearing tooltip, `window->SizeFull` is uninitialized `(0, 0)`. Dear ImGui therefore applied a 0-pixel offset on frame 1, placing the window at the cursor instead of shifted by its size, and only shifted it on frame 2 when `SizeFull` was computed.
- In addition, an experimental `pushTextWrapPos` call forced text into a collapsing width loop where `recordCustomSize` recorded the collapsed width, shrinking text down to 1–2 characters wide and forming an unconstrained vertical tower.
- **Fix**: Removed `pushTextWrapPos` completely, and updated `prepareTooltipPos` to convert `(targetX, targetY, pivotX, pivotY)` directly into exact top-left window coordinates: `finalX = targetX - contentWidth * pivotX`, `finalY = targetY - contentHeight * pivotY`, passing `(finalX, finalY)` to `setNextWindowPos` with a zero pivot `(0, 0)`. Dear ImGui receives pre-computed top-left coordinates and never applies uninitialized `SizeFull * pivot` offsets on frame 1.
- **Size Cache Guard**: `recordCustomSize` now ignores degenerate dimensions (`width < 100f || height < 30f`), ensuring the cache only stores valid, rendered tooltip sizes.

**Other improvements**:
- **`ImGuiCol.Border` and `ImGuiCol.Text` Isolation**: Pushes `TooltipHelper.baseTextColor` and `TooltipHelper.baseBorderColor` (dynamically captured per theme in `UIThemeStyler.setupThemeColors()`) inside `pushTooltipStyles()`. Prevents button styles (like `BrowserDeckButtons` colored borders/text) from tinting tooltip windows or frames.
- **8-Slot Circular Size Cache**: Keeps a rolling history of the last 8 custom tooltip dimensions (zero heap allocation), ensuring re-hovering across parameter rows or deck headers is an instant cache hit.
- **Compilation Safety**: Annotated `pushTooltipStyles()` and `popTooltipStyles()` with `@PublishedApi internal` so public inline custom tooltip functions compile cleanly.

---

## Version 1.0.0-beta.53

> [!NOTE]
> **Release 1.0.0-beta.53** introduces a streamlined Library panel layout with swapped Background Queue and Play Queue columns, transport controls clustered into the column headers with dynamic Play/Pause icons, inline queue clearing, and build toolchain modernization including Gradle 9 dependency coordinate notation and JDK 25 native access enablement.

### Library Play Queue Rearrangement & Swapped Column Layout (`LibraryPanel.kt`, `QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`, `docs/user_guide/library.md`)
- **Queue Column Order Swap (`LibraryPanel.kt`)**: Swapped Column 3 and Column 4 in the Library panel so the Background Queue (`LibraryBgQueue`) is presented before the main A/B Play Queue (`LibraryQueue`).
- **Transport Controls Cluster in Header (`QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`)**:
  - Relocated automated cycle toggles (Auto-VJ and Auto-BG) from the second row to the top row header, positioned between the Previous (`<`) and Next (`>`) buttons.
  - Converted the button icon to a standard transport toggle: displays Play (`Icons.PLAY`) when paused, and Pause (`Icons.PAUSE`) when actively cycling presets.
  - Retained standard theme button styling for transport controls and preserved existing tooltips.
- **Inline Controls Row & Unified Button Styling (`QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`)**:
  - Moved the `Clear` button to the second row inline directly after `Export`.
  - Unified the active color scheme across both queues: Shuffle and Repeat buttons highlight with mint-green text and background when active, and revert to standard theme button styling when inactive.

### Build System & Toolchain Warning Cleanups (`build.gradle.kts`, `gradlew`, `gradlew.bat`, `gradle.properties`, `SessionStateTest.kt`)
- **JDK 25 Native Access Enablement (`gradlew`, `gradlew.bat`, `gradle.properties`)**: Configured `--enable-native-access=ALL-UNNAMED` in Gradle wrapper default JVM options and daemon JVM args (`org.gradle.jvmargs`), eliminating the Java 25 `System::load` restricted method warning from `native-platform` on modern JDKs.
- **Gradle 9 Dependency Notation Deprecations (`build.gradle.kts`)**: Converted multi-argument `implementation(...)` and `runtimeOnly(...)` dependency calls for LWJGL and ImGui to standard single-string coordinate notation (`"group:name:version"` and `"group:name:version:classifier"`), resolving all Gradle 9 deprecation warnings.
- **Documentation Task Logging (`build.gradle.kts`)**: Switched `mkdocs` missing fallback notice in `generateDocs` from stdout warning formatting to `logger.info`, keeping standard build task output clean when MkDocs is not installed.
- **Kotlin Smart-Cast Nullability Warnings (`SessionStateTest.kt`)**: Removed redundant safe-call operators (`?.`) on values following `assertNotNull` assertions in `SessionStateTest`, resolving Kotlin 2.3+ compiler warnings.

---

## Version 1.0.0-beta.52

> [!NOTE]
> **Release 1.0.0-beta.52** introduces 1:1 scale normalization across 2D and 3D modes (matching vertical frame heights at Zoom 1.0), true 6-panel default cube displacement for Cube Cage mode, visibility and space-folding restoration for the 24-chamber Tetrahedral Kaleidoscope mode, and restriction of secondary 3D projection passes strictly to flat 2D sources while routing native 3D parameters contextually in the View tab.

### 3D Mode Scale Normalization, Cube Cage Base Offset & Tetrahedral Kaleidoscope Fixes (`tri_planar.vert`, `tetra_kaleido.frag`)
- **1:1 Scale Normalization Across 2D and 3D Modes (`tri_planar.vert`, `tetra_kaleido.frag`)**:
  - Aligned hardware perspective division in `tri_planar.vert` (`clipX`, `clipY` scaled by `cameraDistance = 2.5`) so that at `Zoom = 1.0` and `Rotate = (0, 0, 0)`, the 3D planes fill the vertical viewport frame $[-1, 1]$ identically to 2D flat mode, eliminating the previous scale discrepancy where 3D mode required a Zoom of $\approx 1.6$ to match 2D height.
  - Normalized camera ray FOV in `tetra_kaleido.frag` so that at `Zoom = 1.0` and default `Persp = 0.5`, the central kaleidoscopic facet exactly fills the vertical frame.
- **Cube Cage 6-Panel Base Displacement (`tri_planar.vert`)**:
  - For Mode 2 (`Cube Cage`, slider range $1.5 \dots 2.5$), added a base unit displacement (`baseOffset = 1.0`) along the face normals (`localPos += normal * (1.0 + uSeparation)`). This ensures the 6 planes form a true 3D cube box by default even when `Separation = 0.0`, rather than collapsing into the same 3 central planes as Tri-Axial mode.
- **Tetrahedral Kaleidoscope Space-Folding & Visibility Restoration (`tetra_kaleido.frag`)**:
  - Fixed the iterative Coxeter space-folding loop in `tetra_kaleido.frag` to cleanly reflect camera rays across all 6 reflection planes of the $A_3$ tetrahedral group into the fundamental chamber ($p_x \ge p_y \ge |p_z| \ge 0$).
  - Switched from unnormalized directional coordinates to plane-projected coordinates ($u = p_y / p_x, v = p_z / p_x$), mapping the 2D source seamlessly across the central facet without out-of-bounds clipping.
  - Eliminated the black screen bug caused by an inverted/contradictory sorting loop and an aggressive `borderFade <= 0.001` fragment discard, ensuring smooth roundness disc fade and vibrant kaleidoscopic tiling across all 24 tetrahedral chambers.

### 3D Mode Restriction to 2D Sources & Streamlined 3D Transform Controls (`DynamicVisualSource.kt`, `Deck.kt`, `Renderer.kt`, `PresetGridTabs.kt`, `meta.json`)
- **Exclusive 2D->3D Elevation**: Universal 3D modes (Tri-Axial, Cube Cage, Hex-Planar, and Tetrahedral Kaleidoscope) are now strictly restricted to flat 2D visual sources (`mandala`, `colors`, `dynamic_spiral`, `attractor_feedback`). Native 3D visual sources (`icosahedron`, `icosa-v3`, `hyper_mesh`, `icosa_dodeca`, `chladni`, `gyroid`, `hyper_slice`) bypass secondary 3D projection passes, preventing geometric distortion and raymarching artifacts.
- **Contextual View Tab Streamlining**: For native 3D sources, the `3D Mode` parameter is removed from the View tab. This eliminates duplicate rotation controls (`Rotate X` and `Rotate Y`) and prevents conflicts between deck view parameters and native source parameters (`Control X`, `Control Y`).
- **Canonical Parameter Routing**: Native 3D source transform parameters (`Zoom`, `Rotate X`, `Rotate Y`, `Rotate Z`) displayed in the View tab are addressed via their canonical source paths (`$deckLabel/${activeSource.displayName}/$name`), guaranteeing seamless MIDI mapping, CV modulation, and clipboard operations without ID collisions.
- **Native 3D Tagging & Automatic Detection (`is3D`)**: Tagged all 7 native 3D sources with `"is3D": true` in `meta.json` and added automatic fallback detection in `VisualSourceRegistry` for any sources exposing `Rotate X` and `Rotate Y` parameters.
- **Deck Source Assignment Safety**: Assigning a 3D source to a Deck automatically resets `view3DMode` to `0.0`.

---

## Version 1.0.0-beta.51

> [!NOTE]
> **Release 1.0.0-beta.51** introduces bundled factory presets and playlists with safe first-run library seeding, on-demand factory restore, first-run visual autoload on Deck A, a clean-lined modern application icon, static deck preview monitor borders, and zero-centered bipolar crossfader rendering.

### Bundled Factory Presets, Playlists & Safe First-Run Seeding (`build.gradle.kts`, `FileSystemManager.kt`, `PresetManager.kt`, `MenuBar.kt`, `PresetListPanel.kt`)
- **Version-Controlled Defaults**: Added `defaults/presets/` and `defaults/playlists/` directories tracked in Git. Curated presets and setlists can be created inside the app and synced to repository defaults via `./gradlew syncDefaultsFromLibrary`.
- **Automated Resource Packaging (`build.gradle.kts`)**: Gradle build automatically generates `manifest.txt` indices and bundles default presets and playlists into application classpath resources (`default_presets/`, `default_playlists/`), making them immediately available on clean clones and binary releases.
- **Safe First-Run Seeding**: On first launch, `FileSystemManager.ensureDefaultLibrary()` unpacks bundled presets and playlists into `library/presets` and `library/playlists`, recording a persistent `library/.defaults_installed` marker file.
- **Permanent Deletion Safety**: If a user intentionally deletes a factory preset or playlist from their local library, the initialization marker prevents it from resurrecting on future application launches.
- **First-Run Visual Autoload**: When starting without an existing session, Deck A automatically loads a curated starter visual preset (`3d mandala`) instead of starting on a blank screen, while preserving clean empty starts when `--empty` / `-e` is passed.
- **On-Demand Factory Restore**: Added **"Restore Factory Presets..."** to the **File** menu and an in-browser restore button in `PresetListPanel` when no presets are found, allowing users to safely restore missing factory presets/playlists at any time without overwriting their custom work.


### Mixer Monitor & Performance Console Polish (`MixerMonitorPanel.kt`, `LinuxEvdevTouchBackend.kt`, `scripts/install_desktop.sh`)
- **Zero-Centered Bipolar Master Crossfader**: Crossfader bar line now renders outward from the center detent (0.0), matching standard DJ hardware fader conventions: fills left with Deck A amber color when in Deck A territory (-1.0 to 0.0), and fills right with Deck B cyan color when in Deck B territory (0.0 to +1.0).
- **Streamlined Crossfader Chrome**: Removed redundant duplicate alpha stem HUD from `MixerMonitorPanel`, allowing the fader to align cleanly with the channel strips.
- **Static Deck Preview Borders**: Removed dynamic crossfader-linked brightness and thickness modulation from `DeckControlPanel`. Deck preview monitors now consistently render at full theme saturation and constant 2.0px border thickness regardless of crossfader position.
- **udev Rule Priority & Hotplug Reliability**: Renamed touchpad permission rule to `70-liquidlsd-touchpad.rules` with `TAG+="seat"` and `RUN{builtin}+="uaccess"`, ensuring proper evaluation before systemd-logind seat tagging and dynamic ACL application across kernel input device events.

---

## Version 1.0.0-beta.50

> [!NOTE]
> **Release 1.0.0-beta.50** introduces the **CapsLock Multi-Touch Trackpad Performance Console** (transforming laptop trackpads into a tactile 4-zone SCS.3m virtual mixer console with LIFO finger cut stutters, direct-jump alpha faders, sticky hold, and hardware-level Linux evdev and macOS Cocoa backends), alongside the **Startup Version Checker & GitHub Update Prompt** (non-blocking background release checks, SemVer 2.0.0 precedence engine, interactive update modal, and About dialog).

### CapsLock Multi-Touch Trackpad Performance Console (SCS.3m Virtual Console) (`TouchConsoleController.kt`, `LinuxEvdevTouchBackend.kt`, `MacCocoaTouchBackend.kt`, `MixerMonitorPanel.kt`, `SettingsPanel.kt`)
- **4-Zone Performance Surface**:
  - Turns laptop trackpads into an absolute multi-touch performance console when `CapsLock` is engaged.
  - **Bottom 28%**: Horizontal Crossfader (Deck A $\leftrightarrow$ Deck B, direct jump, cut stutters).
  - **Middle 17%**: Safety Deadzone Buffer (retains active drift under Zone Affinity, rejects new taps).
  - **Top 55%**: Three independent vertical Level/Alpha faders for Deck A, Deck BG, and Deck B ($0.0 \dots 1.0$, direct jump).
- **Independent LIFO Multi-Touch Stacks**:
  - Each zone maintains an independent LIFO touch stack. Tapping with a second finger instantly jumps to that position; releasing snaps back to the underlying anchor finger.
  - Enables machine-gun crossfade cut stutters and video flash/strobe blackout gates.
  - Sticky hold retains fader levels when all fingers are lifted.
  - Bezel clamping ($Y \le 0.48 \to 0.0$, $Y \ge 0.94 \to 1.0$; $X \le 0.05 \to -1.0$, $X \ge 0.95 \to 1.0$, center detent $\pm 0.02 \to 0.0$) ensures comfortable control without hitting physical laptop chassis edges.
- **Native Platform Backends**:
  - **Linux**: Direct evdev reader via JNA `libc`, `O_RDWR` with `EVIOCGRAB` (`0x40044590`) cursor grab, `EVIOCGABS` hardware axis query, MT Protocol B slot cache, POSIX ACL validation using `access(2)` (circumventing JVM `File.canWrite()` ACL blind spots), and absolute hardware axis verification with explicit TrackPoint/pointing-stick filtering.
  - **macOS**: Cocoa `NSTouch` indirect touch events.
  - **Zero-Crash Graceful Permissions**: Non-root `uaccess` systemd udev rule (`/etc/udev/rules.d/70-liquidlsd-touchpad.rules`) via `pkexec`, interactive permission status and Polkit elevation button cleanly located in Settings (`Settings > Window Frame & Chrome`) to prevent shifting the Mixer UI layout, and in-app hot-reload.
  - **Thread-Safety**: Low-latency lock-free event queue drained strictly on Thread 0 once per frame.
- **Visual Feedback HUD**:
  - Real-time glowing cyan contact dots for active fingers and amber dots for anchor fingers directly on the Crossfader slider.
  - Active alpha levels are reflected seamlessly on each individual deck's monitor preview fader.
  - **Zero-Centered Bipolar Crossfader Bar**: Crossfader slider track bar now correctly originates from center (`0.0`), extending leftward tinted with Deck A's color when fading towards Deck A, and rightward tinted with Deck B's color when fading towards Deck B (rather than filling unidirectionally from Deck A).
  - Clean Mode remains 100% clean with zero HUD overlays.

### Startup Version Checker & GitHub Update Prompt (`UpdateChecker.kt`, `SemVer.kt`, `AppVersion.kt`, `UpdatePromptModal.kt`, `AboutModal.kt`, `MenuBar.kt`, `SettingsPanel.kt`)
- **Non-Blocking Background Update Engine (`UpdateChecker.kt`)**:
  - Checks for the latest release on GitHub asynchronously via a daemon thread on application startup without blocking audio callbacks or GLFW/OpenGL rendering.
  - Dual-mode network query: Uses the GitHub REST API (`releases/latest`) with an automatic fallback that inspects HTTP redirect headers (`Location`) from `github.com/.../releases/latest`, bypassing unauthenticated API rate limits.
  - Strict 5-second timeouts with fail-safe error handling so offline or network errors never interrupt application startup.
- **SemVer 2.0.0 Parsing & Precedence (`SemVer.kt`)**:
  - Zero-dependency semantic version parser adhering to SemVer 2.0.0 rules (numeric core segments, release vs pre-release precedence, dot-separated tag sequences e.g. `beta.42` > `beta.41`, and snapshot detection).
- **Interactive Update Prompt Modal (`UpdatePromptModal.kt`)**:
  - Automatically alerts the user when a newer release is published on GitHub.
  - Displays the current version, latest version, and release title.
  - Options: **Download Update** (opens browser directly to the GitHub release page), **Remind Later** (dismisses for the current session), or **Skip Version** (persists `ignoredUpdateVersion` in preferences so the user is not prompted again for that specific release).
- **"About Liquid LSD" Dialog & Help Menu Controls (`AboutModal.kt`, `MenuBar.kt`)**:
  - Added **About Liquid LSD** and **Check for Updates...** to the **Help** menu.
  - The About dialog displays the current runtime version, provides a manual **Check for Updates** button with real-time status feedback, and provides direct links to the GitHub repository and documentation.
- **Startup & Update Preferences (`SettingsPanel.kt`, `AppSettings.kt`, `UITheme.kt`)**:
  - Added "Automatically check for updates on launch" toggle and a manual "Check for Updates Now" action in `Settings > General > Startup & Updates`.

---

## Version 1.0.0-beta.41

> [!NOTE]
> **Release 1.0.0-beta.41** brings a fixed 95% global UI typography scale, dedicated Library preset name scaling, inside-clustered monitor overlays with 4-channel physical console level faders and preview dimming, multi-resolution application icons and FreeDesktop launcher integration with Wayland/X11 app IDs, sticky two-row Library column headers, redesigned single-handle Master Crossfader, minimum 1280×720 window bounds, unified column visibility driven directly by engine subsystems, and hex-planar / tetrahedral 24-chamber kaleidoscope 3D modes.

### Fixed 95% UI Scale & Focused Library Preset Sizing (`UITheme.kt`, `AppSettings.kt`, `SettingsPanel.kt`, `GridMetrics.kt`, `UIManager.kt`)
- **Permanently Fixed 95% Global UI Scale**:
  - Pinned all application typography to exact pixel sizes at 95% scale: Caption 12px, Body 14px, Code 14px, H3 15px, H2 18px, H1 22px, baseSize 14.25px.
  - Eliminated dynamic `(baseSize / 15f)` runtime scaling calculations across 14+ UI panels (`PresetGridPanel`, `AudioEnginePanel`, `CellConfigPanel`, `CustomRangeSlider`, `BeatDivisionSlider`, `Lfo1Section`, `Lfo2Section`, `ModulatorHeaderRow`, `OscilloscopeDrawer`, etc.).
- **Deprecation & Removal of Grid Knob Cell Scale**:
  - Completely removed the non-functional `gridCellRatio` setting and UI slider.
  - Precalculated `GridMetrics` into a singleton `INSTANCE` at 95% scale (`cell = 33.25f`), eliminating per-frame heap allocations during Preset Grid rendering.
- **Dedicated Library Preset Name Sizing**:
  - Introduced `presetNameScalePercent` (80%–120%, default 100%, 10% step) to scale preset names in the Library (`PresetListPanel`), Playlist Editor (`PlaylistEditorPanel`), and Play Queues (`QueueActionsPanel`, `BgQueueActionsPanel`) without altering performance controls or deck headers. 10% steps ensure each step produces a distinct, pixel-aligned font size without glyph bounding box collisions.
  - Added an informational typography hierarchy display in `Settings > Appearance` alongside the new "Preset Name Size" slider.
- **Repurposed Zoom Shortcuts**:
  - Shortcuts `Ctrl + -` and `Ctrl + =` (`Cmd + -` and `Cmd + =` on macOS, with keypad +/- support) now adjust Library preset name scale in 10% increments.

### Clustered Monitor Overlays & 4-Channel Level Faders (`DeckControlPanel.kt`, `MixerMonitorPanel.kt`, `Mixer.kt`, `Renderer.kt`, `mixer.frag`)
- **4-Channel Console Level Faders**:
  - Added non-modulatable 0.0–1.0 channel level multipliers (`levelA`, `levelB`, `levelBG`, `levelPV`, `masterLevel`) to `Mixer`.
  - Level faders act as physical mixer console channel strips: they scale output in `mixer.frag` (`uLevelA`, `uLevelB`, `uLevelBG`, `uMasterLevel`) without interfering with or resetting underlying CV modulators.
  - Console isolation: Channel fader settings are preserved across preset swaps, copies, and patch reloads.
- **Inside-Clustered Monitor Overlays**:
  - **Deck A & Deck BG**: Badges `[A]` and `[BG]` relocated to the top-right corner; die buttons `[🎲]` positioned to the left of the badges; vertical level faders hang directly below the badges.
  - **Deck B & Deck PV**: Badges `[B]` and `[PV]` relocated to the top-left corner; die buttons `[🎲]` positioned to the right of the badges; vertical level faders hang directly below the badges.
  - Central Command Spine: All 4 channel faders and dice are clustered in the middle gutter right below the crossfader, within immediate mouse reach.
  - Overlaid directly on the video previews without shrinking preview width or distorting aspect ratios.
- **Master Output Monitor Overlay**:
  - Added bottom-right overlay with `[M]` Master badge, `[🎲 ALL]` button to its left, and vertical Master Level fader directly above `[M]` extending upward. Leaves top-right free for the `[REC]` tally badge.
- **Interactive Badges & Non-Overlapping Monitor Click Bounds**:
  - Clicking `[A]`, `[B]`, `[BG]`, `[PV]`, or `[M]` badges directly focuses that deck/mixer tab in the Preset Grid.
  - Background monitor drag/focus hitboxes exclude the 60px overlay gutter, preventing accidental tab switching or drag-and-drop actions when operating faders or dice.
- **Channel Preview Dimming**:
  - Live monitor previews for Deck A, Deck B, Deck BG, and Deck PV dim dynamically as their corresponding level fader is reduced below 1.0.

### Application Icons & Window Branding (`Main.kt`, `build.gradle.kts`, `scripts/install_desktop.sh`, `liquid-lsd.desktop`, `web/`, `website/`, `src/main/resources/icons/`)
- **Desktop Window Icons & Compositor Integration (GLFW, X11, Wayland)**:
  - Integrated multi-resolution application icon loading (`16x16`, `32x32`, `48x48`, `64x64`, `128x128`, `256x256`) via `stbi_load_from_memory` and `glfwSetWindowIcon` in `Main.kt`.
  - Added `GLFW_WAYLAND_APP_ID`, `GLFW_X11_CLASS_NAME`, and `GLFW_X11_INSTANCE_NAME` hints to ensure proper window grouping and taskbar/dock icon association under modern Linux compositors (GNOME, KDE, Sway/Hyprland).
  - Implemented automatic local FreeDesktop icon and `.desktop` entry registration (`ensureLinuxDesktopEntry()`), alongside distribution packaging scripts (`scripts/install_desktop.sh` and `build.gradle.kts` dist integration).
  - Automatically applied icons and window classes to both primary desktop and secondary / external monitor preview windows.
- **Web & Documentation Branding**:
  - Wired high-resolution favicon and Apple touch icon assets into `web/index.html` and documentation templates (`website/templates/doc_page.html`, `website/templates/index.html`).

### Library Panel UI Refinements (`LibraryPanel.kt`, `BrowserActionToolbar.kt`, `PresetListPanel.kt`, `PlaylistEditorPanel.kt`, `QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`, `Icons.kt`)
- **Sticky Top Two Rows in Library Columns**:
  - The top two header rows of each of the four Library columns (Presets, Playlists, Queue, BG Queue)—including panel titles, action buttons, search filters, playlist selector combo, and playback/auto-vj controls—remain pinned/sticky at the top when scrolling through lists.
  - The item lists now scroll within dedicated inner child windows (`##presets_scroll`, `##playlist_items_scroll`, `##queue_items_scroll`, `##bg_queue_items_scroll`), while the outer column frames lock scrolling via `NoScrollbar` and `NoScrollWithMouse`.
- **Proportional Action Toolbar Buttons**:
  - Resized the deck load/audition buttons in `BrowserActionToolbar` from an oversized fixed width (80 px) to ~1.5x their height (`btnH * 1.5f`, ~36 px at standard scaling), streamlining horizontal footprint and toolbar centering.
- **Balanced Title Bar Button Height & Padding**:
  - Reduced button height in the Library title bar from ~32 px to ~22 px (`(22f * fontScale)`), creating sleeker, more compact controls.
  - Adjusted title bar frame padding to 6.0 px (`libTitleBarH = 32f`), preserving the ~2.5 px bottom margin while adding sufficient top clearance to prevent button borders from clipping against the horizontal splitter line.
- **Fixed Lock/Unlock Icons**:
  - Corrected the Lucide PUA glyph codepoints for `Icons.LOCK` (`\ue10b`, previously misassigned to `map-pin`) and `Icons.UNLOCK` (`\ue10c`, previously misassigned to `user`), restoring standard padlock icons on the quick audition latch button.

### Mixer Monitor UI Refinement (`MixerMonitorPanel.kt`, `MixerMonitorLayout.kt`)
- **Redesigned Master Crossfader**:
  - Replaced the previous progress-bar style flat fader with the standard single-handle slider styling from `CustomRangeSlider` (3 px track line, 6x16 px handle, hover/drag bounding rect highlight, and dynamic modulated amber dot).
  - Added faint vertical position tick marks across the crossfader track indicating the ends (-1.0, +1.0), midway points (-0.5, +0.5), and middle center (0.0).
  - Placed **[ A ]** and **[ B ]** boxed badges to the left and right of the crossfader, styled identically to the Deck A and Deck B monitor overlays.
  - Interactive badges: clicking [A] snaps crossfade to 100% Deck A (-1.0), and clicking [B] snaps crossfade to 100% Deck B (+1.0). Middle-clicking or scrolling adjusts or centers (0.0) the crossfader.
  - Removed the `"Crossfader"` text label for a cleaner, centered look.
- **Streamlined Master Controls**:
  - Removed the `< Prev` and `Next >` queue trigger buttons.
  - Sized the 5 momentary randomization buttons (`Rand A`, `Rand B`, `Rand BG`, `Rand PV`, `Rand All`) to distribute evenly across full panel width when randomization is enabled.
  - Removed the redundant Fade Speed slider from the Mixer Monitor child window.
  - Updated `MixerMonitorLayoutCalculator` vertical chrome calculations to account for the reduced row count, giving more vertical canvas space to preview monitors.

### Preset Grid UI Layout Tightening (`PresetGridTabs.kt`, `PresetGridPanel.kt`, `PresetGridRenderer.kt`, `BrowserRowMoreButton.kt`)
- **Square Side Tab Buttons**: Made the deck selector buttons (`MIX`, `A`, `B`, `BG`, `PV`) square (`width == height`), significantly reducing horizontal footprint while maintaining comfortable click targets.
- **Tightened Parameter Indentation**: Reduced the parameter label indent from Dear ImGui's default ~21–24 px to 6 px (~1/4 width), gaining horizontal space for parameter names and matching section tab insets.
- **Refined Kebab Button Proportions**:
  - Decreased kebab button width by ~25–30% (from 26 px / 28 px to 19 px / 20 px).
  - Scaled down kebab dot radius and spacing for a more refined, minimal appearance.
- **Unified Border Box & Vertical Scrollbar Alignment**:
  - Aligned the right-hand edge of the preset grid container frame box (`boxMaxX`) to share the exact right-hand edge of the column settings kebab.
  - Sized the preset grid scrolling child window (`##preset_grid_scroll`) to position the vertical scrollbar directly in the narrow vertical column beneath the kebab, eliminating visual gaps and overlaps.
- **Symmetrical Right Border to Panel Edge Padding**:
  - Eliminated extra trailing horizontal margin in `PresetGridPanel.calculateRequiredWidth` and `PresetGridPanel.draw`.
  - Balanced the right padding between the preset grid box border and the panel edge/divider line to match the standard `windowPaddingX` (8 px), creating visual symmetry with the padding between the divider line and Cell Config's content.
- **Global Slim 10 px Scrollbar**:
  - Configured global base `scrollbarSize` to 10.0 px (with 5.0 px pill rounding) in `UIManager.kt`.
  - Applies uniformly across all panels (Preset Grid, Cell Config, and Library), providing a cleaner, more compact visual profile while maintaining dynamic scaling with UI font size preferences.

### Minimum Window Dimensions Raised to 1280 x 720 (`Main.kt`, `WindowFrameController.kt`)
- **Updated Minimum Bounds**: Raised the desktop minimum window dimensions from 800 × 600 (SVGA 4:3) to 1280 × 720 (720p HD 16:9).
- **Layout & Multi-Deck Preservation**: Prevents severe horizontal layout compression and UI clipping across the top title bar controls, preset grid, and audio modulation matrix on compact or resized windows, aligning standard desktop bounds with modern HD DJ/VJ workflows.

### Standardized Title Bar to Workspace Panel Gap (`UIManager.kt`, `WindowLayoutSafetyTest.kt`)
- **Resolved Variable Black Gap**: Identified and eliminated an unintended black gap between the top title/menu bar and the primary workspace panels caused by a legacy minimum height clamp (`.coerceAtLeast(32f)`). Following recent Dear ImGui font metric updates, `ImGui.getFrameHeight()` evaluated below 32px at default scaling, displacing panels downward while the menu bar window remained at frame height.
- **Explicit 1 px Spacing Constant**: Replaced the magic number clamp with a named constant `UIManager.TITLE_BAR_PANEL_GAP = 1.0f`. The layout now cleanly calculates panel starting offset as `titleBarH + TITLE_BAR_PANEL_GAP`, maintaining an exact 1 px visual divider across all display sizes and UI scaling presets without disappearing during font zoom.

### Unified Modulator Engine & Preset Grid Column Visibility (`UITheme.kt`, `SettingsPanel.kt`, `PresetGridPanel.kt`, `CellConfigPanel.kt`, `PresetDependencyAnalyzer.kt`)
- **Single Source of Truth**: Unified engine subsystems (`audioEngineEnabled`, `midiEnabled`, `sequencerEnabled`) with Preset Grid column visibility. A column is visible if and only if its underlying subsystem is active, eliminating contradictory states where a disabled engine's column could be shown or an active engine's column hidden.
- **Preset Grid Kebab as Quick Switchboard (`PresetGridPanel.kt`)**: The header kebab menu (`⋮`) now directly toggles the underlying subsystems (starting/stopping `AudioEngine`, scanning/closing `MidiEngine`, running/halting step sequencer clock) alongside their columns in one click, without opening Settings.
- **Streamlined Settings Categories (`SettingsPanel.kt`)**: Removed the redundant `Settings > Preset Grid` category. Relocated the `Grid Knob Cell Scale` (`gridCellRatio`) slider to `Settings > Appearance` under "Fonts & Sizing".
- **Optimized 10-Bit Dependency Issue Cache (`PresetDependencyAnalyzer.kt`)**: Reduced memoization state space from 8192 to 1024 slots with zero runtime GC allocations per frame.

### Manual BPM Configuration & Persistent Title Bar Display when Audio Engine is Disabled
- **Manual BPM Setting When Audio Engine Disabled (`AudioEnginePanel.kt`, `AudioEngine.kt`)**: When the Audio Engine is disabled via Settings, the panel now displays the Beat Sync & Manual Tempo section with a compact manual BPM slider (40.0–200.0 BPM, default 120.0 BPM), a real-time BPM readout with an internal beat phase flashing dot, and a one-click "Reset to 120.0 BPM" button. Users can freely configure tempos offline without needing live audio hardware.
- **Phase-Continuous Offline Clock Re-Anchoring (`AudioEngine.kt`)**: When adjusting manual BPM while the audio engine is disabled or inactive, `setBpmDirectly` captures the running beat count from `CVRegistry.getSynchronizedTotalBeats()` to re-anchor the beat clock, preventing backward beat counter jumps and maintaining seamless phase continuity across BEAT LFOs and step sequencers.
- **Persistent Title Bar BPM & 4-Beat Meter (`MenuBar.kt`)**: The top title bar now persistently displays both the BPM readout and the 4-beat phase meter dots even when the Audio Engine is disabled or inactive. In manual mode, BPM text is rendered in warm amber with contextual hover tooltips explaining that the audio engine is disabled and the tempo is fixed. Clicking either the BPM readout or the 4-beat meter directly opens the Audio Engine settings panel.

### Hex-Planar (60°) & Tetrahedral 24-Chamber Kaleidoscope 3D Modes
- **Hex-Planar Display Mode (6 Planes @ 60°)**: Added Mode 3 to the universal `View` pipeline, replicating any 2D visual source across the 6 symmetry planes of the tetrahedral Coxeter group ($A_3$: $x = \pm y, y = \pm z, z = \pm x$). All 6 planes pass through $(0, 0, 0)$ at $60^\circ$ angles, sharing the exact same origin and expanding along their normals into a 12-faced rhombic dodecahedral cage when `Separation` is increased.
- **Tetrahedral Kaleidoscope Mode (24-Chamber Space Folding)**: Added Mode 4 to the universal `View` pipeline (`tetra_kaleido.vert`, `tetra_kaleido.frag`), implementing iterative Coxeter reflection folding across simple roots. Virtual camera rays fold 24 times into the tetrahedral fundamental domain, producing seamless mirror reflections across all sector boundaries.
- **Circular Disc Boundaries (`Roundness`)**: Added a modulatable `Roundness` parameter (0.0 = square, 1.0 = circle, default 1.0) with anti-aliased edge feathering. In 3D rotation, intersecting circular discs produce continuous, seamless celestial gyroscopes and armillary spheres, completely eliminating boxy corner sweeping.
- **Background Transparency & Discard Precision**: Non-luminous background fragments are cleanly discarded, ensuring both modes float transparently over the background deck with zero gray shadow artifacts.

### Unified Audio & Transient Modulator System
- **Consolidated Audio Matrix Column**: Merged the separate `AUD` and `TRIG` columns in the Preset Grid into a single, unified `AUD` column (`VAL`, `MIDI`, `LFO`, `SEQ`, `AUD`).
- **Dual Modular Audio Slots**: Each modulatable parameter now supports up to 2 independent audio slots (`Audio 1` and `Audio 2`). Slot 2 stays cleanly collapsed behind an `[ + Enable Audio Slot 2 ]` button until activated.
- **Continuous (RMS) vs Transient (Spectral Flux) Modes**: Modulators seamlessly toggle between continuous amplitude tracking (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`) and spectral flux transient detection (`audio_flux_amp`, `audio_flux_bass`, `audio_flux_mid`, `audio_flux_high`).
- **4 Selectable Frequency Bands**: Both modes operate over `Full Mix (AMP)`, `Bass (BASS)`, `Mid (MID)`, and `High (HIGH)`.
- **Response Profiles**: Added curated dynamics presets (`Instant / Raw`, `Snap`, `Punchy`, `Smooth Swell`, `Slow Pulse`, `Ambient Drift`, `Custom`) with full Attack/Decay envelope control.
- **Zero-Allocation DSP**: Computed inside the real-time audio callback loop without memory allocations and displayed via zero-latency oscilloscopes in the UI.

### 4-Platform Targeted Distribution & CI Smoke Testing
- **Targeted Platforms**: Distribution ZIP packaging targets the 4 supported architectures: Windows x64, Linux x64, macOS ARM64 (Apple Silicon), and macOS x64 (Intel). Removed `linux-arm64` from build targets due to lack of upstream Linux ARM64 native JNI binaries in `imgui-java`.
- **Automated Smoke-Test Verification**: All 4 target platform distributions are verified natively on GitHub Actions runners before release, ensuring only verified, functional binaries are published.
- **Fixed Release Notes Accumulation**: Resolved an issue where GitHub Releases accumulated and reprinted historical release notes from all previous versions.

### UI Sizing & HiDPI Double-Scale Fix
- **HiDPI Double-Scaling Resolution (`UITheme.kt`, `SettingsPanel.kt`, `UIManager.kt`, `Main.kt`)**: Removed redundant manual `systemDpiScale` calculation from UI sizing formulas. With `imgui-java` 1.86.12+, the ImGui GLFW/GL3 backends handle OS display content scaling automatically in logical pixels. Base UI font size is now directly calculated as `15.0px * (guiScalePercent / 100)`.
- **Streamlined UI Scale Controls**: Simplified Settings panel sizing controls to a single "UI Scale" slider (75%–200%, 5% steps) with updated tooltip clarifying that OS HiDPI scaling is handled automatically.

### Preset Dependency Inspection & Column Kebab Menu
- **Proactive Dependency Analysis (`PresetDependencyAnalyzer.kt`)**: Automatically inspects visual presets and live decks for reliance on disabled subsystems (`MIDI`, `Step Sequencer`, `Randomization`), offline engines (`Audio Engine`), or hidden columns in the Preset Grid.
- **Non-Destructive Alert Badges**: Displays a red `[!]` indicator alongside affected presets in the Library list and active deck monitor headers with rich, explanatory hover tooltips—never hiding presets or blocking playback.
- **Zero-Allocation Issue Evaluation**: Memoizes issue evaluation through an internal 13-bit state cache, preventing garbage collection pause jitter across 60 FPS list rendering.
- **Preset Grid Header Column Kebab (`⋮`)**: Added an inline column configuration menu to the right of the grid headers (`VAL`, `MIDI`, `LFO`, `SEQ`, `AUD`) with live status indicators, instant column visibility toggles, and one-click `[ Turn On Needed Columns ]` / `[ Enable Audio Engine ]` quick actions.

---

## Version 1.0.0-beta.40

> [!NOTE]
> **Release 1.0.0-beta.40** introduces the standalone "Colors" visual source generator, the zero-allocation Beat Tracker (`BeatTrackerEngine`, inspired by BTrack) with complex spectral difference ODF and causal dynamic programming, an overhaul of the Library browser with a unified menu bar action strip, quick audition padlock, and full keyboard navigation (including <kbd>Ctrl+F</kbd>/<kbd>/</kbd> instant search focus and <kbd>Esc</kbd> clear), dynamic on-air deck illumination proportional to the crossfader, modular Cell Config collapsible accordions with LFO advanced parameter dirty dot indicators (`•`), a 4-beat phase meter in the top menu bar, streamlined 3-column layout sizing with locked aspect ends and flexible center panel, 4-step Spacebar library ping-pong cycling (`HIDE` $\rightarrow$ `HALF` $\rightarrow$ `FULL` $\rightarrow$ `HALF` $\rightarrow$ `HIDE`), title bar click-drag library resizing, symmetrical Background Queue controls with modulation routing and dirty state checking, consolidated 2-column Audio Engine settings with zero-lag CV oscilloscopes, and slider drag-isolation fixes.

---

### Key Highlights

#### 0. Native Apple Silicon (macOS ARM64) Support via ImGui 1.86.12 (`build.gradle.kts`, `docs/developer/imgui_upgrade_guide.md`, `DECISIONS.md`)
- **Native Apple Silicon Binaries**: Upgraded `io.github.spair:imgui-java-*` from `1.86.11` to `1.86.12`, delivering Mach-O universal binaries (`x86_64` + `arm64`) for macOS. This resolves native library linkage failures (`UnsatisfiedLinkError`) on Apple Silicon Macs and unblocks native execution on `macos-arm64`.
- **Zero Breaking Changes**: Version 1.86.12 preserves 100% binary and source compatibility with existing UI code, ensuring zero risk of visual or behavioral regressions.
- **Modernization Roadmap**: Documented the full multi-architecture investigation and Phase 2 migration guide for Dear ImGui 1.92.x in `docs/developer/imgui_upgrade_guide.md`.

#### 0.1. Automated 5-Platform Binary Smoke Testing & Selective Release Gating (`Main.kt`, `build.gradle.kts`, `.github/workflows/smoke-test.yml`, `.github/workflows/release.yml`)
- **Headless Diagnostic Smoke Test (`--smoke-test`)**: Added a fast, 5-stage headless self-diagnostic test to the application entry point. Verifies JVM runtime/architecture, tests LWJGL native library linkage, tests Dear ImGui native bindings and context creation/destruction (`ImGui.createContext()`), verifies classpath resource packaging (core shaders and presets), and verifies AudioEngine fallback initialization without requiring a physical monitor or GPU.
- **CLI Flags & Info Dispatch (`Main.kt`)**: Added `--version` / `-v` (prints version, OS, architecture, and JVM runtime details) and `--help` / `-h`.
- **Launcher CLI Argument Forwarding (`build.gradle.kts`)**: Updated generated desktop launchers (`run-linux.sh`, `run-windows.bat`, `run-mac-arm.command`, `run-mac-intel.command`) to forward all command-line arguments directly to the application JAR (`"$@"` on Unix and `%*` on Windows).
- **Parallel 5-Platform CI Matrix (`.github/workflows/smoke-test.yml`)**: Automated native smoke testing on GitHub-hosted runners (`ubuntu-latest`, `ubuntu-24.04-arm`, `macos-latest`, `macos-13`, and `windows-latest`) on pull requests and workflow dispatch.
- **Selective Release Gating (`.github/workflows/release.yml`)**: Release builds run the 5-platform matrix before publishing. Only platform distribution ZIPs that pass automated smoke testing are published to GitHub Releases, preventing broken builds from reaching users while permitting functioning platforms to release even if a single platform experiences a regression.

#### 0.1. Release Packaging, Launcher Permissions, and Library Distribution (`build.gradle.kts`, `VisualSourceRegistry.kt`, `Main.kt`)
- **Executable Permissions on Release Scripts**: Fixed an issue where `run-linux.sh`, `run-mac-arm.command`, `run-mac-intel.command`, and bundled `bin/java` binaries lost executable permissions in GitHub release ZIPs. Updated `build.gradle.kts` to invoke `permissions { unix("755") }` on `FileCopyDetails` in all distribution zip tasks.
- **Library Sources & Presets Bundled in Releases**: Added `library/**` packaging into `packageThumbDrive` and all platform ZIP distributions (`zipWindows`, `zipLinux`, `zipLinuxArm`, `zipMacArm`, `zipMacIntel`), resolving runtime crashes where `VisualSourceRegistry` failed to find `library/sources/mandala`.
- **Self-Healing Bundled Source Extraction**: Configured `tasks.processResources` to bundle default visual sources into the fat JAR under `default_sources/`. `VisualSourceRegistry.loadAll()` now automatically extracts bundled default sources if `library/sources/` is empty or missing `mandala`, ensuring the application self-heals even when executed from a standalone fat JAR.
- **Accurate Error Messaging**: Corrected the startup failure exception message from `presets/sources/mandala` to `library/sources/mandala`.

#### 0.1. Sequencer, Randomization, and MIDI Settings Toggles & Defaults (`AppSettings.kt`, `SettingsPanel.kt`, `UITheme.kt`, `PresetGridPanel.kt`, `CellConfigPanel.kt`, `MenuBar.kt`, `ModulatableParameter.kt`, `Evaluators.kt`, `CVRegistry.kt`, `MidiEngine.kt`, `MidiJackWatchdog.kt`, `Main.kt`, `UIManager.kt`)
- **Step Sequencer Toggle in Settings**: Added an "Enable Step Sequencer" master toggle under Settings (`General` and `Preset Grid`), allowing users to enable or disable step sequencer execution and grid/cell visibility.
- **Default Disabled Behavior**: The Step Sequencer (`sequencerEnabled`), parameter/modulator randomization (`randomizationEnabled`), and MIDI input/mapping (`midiEnabled`) now default to disabled (`false`) for clean, deterministic, and distraction-free startup.
- **MIDI Master Toggle in Settings**: Added an "Enable MIDI" toggle under Settings (`MIDI & Controls`), safely closing hardware devices when disabled and rescanning devices upon activation. "MIDI Map" in the menu bar and grid columns are gated cleanly.
- **Modulation Bypassing**: When disabled, step sequencer and MIDI modulators are safely bypassed during parameter evaluation, and CVRegistry returns neutral 0.0 values.

#### 0.1. Continuous Constrained Random Morphing (`MorphState.kt`, `Deck.kt`, `Mixer.kt`, `MixerMonitorPanel.kt`)
- **Continuous $0.0 \leftrightarrow 1.0$ Generative Morphing**: Transformed discrete 1-shot randomization triggers (`randDeckA`, `randDeckB`, `randDeckBG`, `randDeckPV`, `randAll`) into continuous morphing controllers. Assigning an LFO or CV source smoothly morphs base values and active modulator properties between two randomized states with zero UI explosion.
- **Selective Randomization Deactivation Gating (`MorphState.kt`)**: Fixed an issue where toggling off randomization on a parameter (`param.randomizeBase = false`) or modulator property while continuous morphing was active (`randDeckA`, `randDeckB`, etc.) failed to stop randomization. `DeckMorphController` now strictly gates base value and modulator field interpolation on their respective `randomize*` flags, immediately freezing non-randomized parameters, syncing state snapshots to active values, and preventing user slider edits from being clobbered during morph cycles.
- **Unidirectional Wrap-Around Morphing (`MorphState.kt`)**: Added seamless support for non-stop, unidirectional forward flow when morph targets are modulated by Sawtooth ramps, `beatPhase`, or phase modulators ($0.0 \to 1.0$). Automatically detects ramp wraps ($1.0 \to 0.0$) and promotes the arrived state ($S_1 \to S_0$) while sampling a fresh destination target into $S_1$, ensuring the value at $v = 0.0$ matches the prior frame with zero jump cut, zero turnaround, and zero pause.
- **Pure Linear Sawtooth & Zero-Hesitation Wrap (`WaveformMath.kt`)**: When LFO asymmetry is set to either extreme (`slope >= 0.999f` for ramp up or `slope <= 0.001f` for ramp down) and `morph = 1.0f`, `calculateAdvancedLFO` now evaluates as an ideal monotonic linear ramp (`phase` or `1.0 - phase`) with instantaneous wrap at the period boundary, completely eliminating reverse fall slopes and peak deceleration. This provides seamless, non-stop continuous rotation when modulating circular and endless parameters (`MeterType.ENDLESS`, angles, hue sweeps) without arc hesitation or backward swing.
- **Flip-Flop Boundary State Machine**: Implemented latching hysteresis (`READY_FOR_ONE` / `READY_FOR_ZERO`) at the $0.01$ and $0.99$ boundaries. Crossing a boundary re-rolls the opposite state, enabling non-repeating generative visual journeys.
- **Zero-Allocation In-Place Lerp & Shortest-Path Angles**: Pre-allocated snapshot buffers and converted runtime modulator fields to mutable vars to eliminate GC allocations during per-frame lerping, with shortest-path modular interpolation for angles and hue rotations.
- **Randomizer Parameter Randomization Lockout (`ModulatableParameter.kt`, `Mixer.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`, `ValueParamSection.kt`, `ModulatorHeaderRow.kt`, `PresetGridRenderer.kt`)**: Explicitly disabled randomizing the base value ranges or modulators of the five Mixer randomizer parameters (`Mixer/randDeckA`, `randDeckB`, `randDeckBG`, `randDeckPV`, and `randAll`). Gated `randomizeBase` and `randomizeBaseValue()` to prevent destabilizing recursive control loops, dimmed dice toggles in Cell Config, disabled row context menu randomization, and added a contextual warning tooltip (*"It is forbidden to randomize the randomizer. Chaos would ensue."*) when hovering over the locked dice.

#### 0.1. Mandala Analytical Size Normalization (`Mandala.kt`, `Renderer.kt`, `web/renderer.js`)
- **Sum-of-Lengths Normalization ($R_{\text{target}} = 2.0$)**: Implemented fast, analytical size normalization for the Mandala visual source. Scales arm lengths $L_1 \dots L_4$ by $\text{scale} = R_{\text{target}} / \sum |L_i|$ (with zero-protection for $\sum |L_i| \le 10^{-5}$), guaranteeing that the theoretical maximum reach of the pen exactly touches $R_{\text{target}}$ and fills the screen height ($2.0 \times 0.5 = 1.0$ in NDC).
- **Morphing vs. Breathing**: Prevents visual clipping and extreme shrinkage when arm lengths vary or are modulated by LFOs, shifting dynamics to harmonic petal balance morphing while anchoring the outer bounding circle.
- **Desktop & WebGL2 Parity**: Uniformly applied in desktop OpenGL (`Renderer.kt`) and browser WebGL2 (`web/renderer.js`) pipelines, ensuring identical visual scaling and radial depth shading across platforms.

#### 0.2. Universal 3D Tri-Axial & 2D View Stage (`Deck.kt`, `Renderer.kt`, `PresetGridTabs.kt`, `PresetModels.kt`, `tri_planar.vert`, `tri_planar.frag`, `view2d.frag`)
- **Universal 3D Orthogonal Transformation**: Elevates any 2D visual source (Chladni, Dynamic Spiral, Attractor, Video, etc.) into a 3D rotating structure across intersecting orthogonal planes ($XY$, $YZ$, $ZX$) at $90^\circ$ angles, forming a holographic gyroscope / celestial sphere, or a 6-sided Cube Cage.
- **Universal 2D View Transformation (`view2d.frag`, `rawSource2DFBO`)**: When in standard 2D mode (`3D Mode < 0.5`), universal View parameters `Zoom` and `Rotate Z` (Roll) actively transform the 2D source before entering the feedback loop. Operates at full native widescreen resolution via `rawSource2DFBO` with isotropic aspect-ratio-corrected rotation and transparent border blanking.
- **Contextual View UI Parameter Visibility (`PresetGridTabs.kt`)**: Reordered the View tab to place universal controls at the top (`Zoom`, `Rotate Z`, `3D Mode`). Automatically hides 3D-only controls (`Rotate X`, `Rotate Y`, `3D Persp`, `Depth Dim`, `Separation`, `Blend Mode`) when in 2D mode (`3D Mode < 0.5`), eliminating confusing non-functional sliders while preserving parameter paths and modulation bindings.
- **Camera Proximity Headlight (`Depth Dim`)**: Applies virtual camera-aligned point lighting with inverse-square falloff in view space. Elements closest to the camera stay crisp and brilliant, while elements sweeping into the distance dim smoothly into atmospheric haze.
- **Perspective-Correct Interpolation & Square Plane Aspect**: Rendered 2D sources into a square $1:1$ `rawSourceFBO` (`FBO(height, height)`) to eliminate non-uniform plane stretching in 3D mode, and emitted homogeneous clip coordinates with $w$-buffering in `tri_planar.vert` for hardware perspective-correct texture mapping without affine distortion.
- **Preset Serialization & Backward Compatibility**: Fully serialized under `viewParameters` in `.lsd` preset files with default fallback to ensure all legacy presets load without interruption.

#### 0.3. Mandala Architecture Unification (`Mandala.kt`, `PresetGridTabs.kt`, `PresetGridPanel.kt`, `PresetModels.kt`, `WebPresetSerializer.kt`, `ValueParamSection.kt`, `CellConfigPanel.kt`)
- **Streamlined Source Parameters & View Tab Cleanup**: Cleaned up Mandala's `meta.json` declaration so generator geometry controls (`Lobes`, `Recipe Select`, `L1`–`L4`, `Thickness`, `Hue Offset`, `Hue Sweep`, `Depth`) populate the `SRC` tab in natural declaration order. Removed legacy hardcoded Mandala transform parameters (`Zoom`, `Rotate Z`, `Rotate Y`, `Rotate X`, `3D Persp`), eliminating duplicate spatial controls in the `View` subtab and delegating all spatial transformation to the universal Deck View pipeline.
- **Streamlined Source Dropdown**: Pruned hardcoded "Mandala" menu entries from `PresetGridTabs.kt` and `PresetGridPanel.kt` in favor of single-loop iteration over `VisualSourceRegistry.availableSources`.
- **Preset Model Simplification & Dynamic Recipe Restoration**: Eliminated `MandalaRecipeDto` and the redundant `recipe` field in `DeckPresetDto`. Presets now serialize and restore `Lobes` and `Recipe Select` as standard modulatable parameters; upon loading, `Mandala.update()` evaluates those parameters and restores the matching Fourier ratio automatically.
- **Hierarchical Parameter Paths**: Removed the legacy custom `Mandala.getParameterPaths()` override; Mandala parameters now follow the universal hierarchical pattern (`Deck A/Mandala/<param>`) alongside all other visual sources.
- **Web TV Broadcast Parity**: Simplified `WebPresetSerializer.serializeDeck` to a single generic loop with a concise extension for Mandala Fourier frequencies (`a`, `b`, `c`, `d`), maintaining 100% backward compatibility with WebGL2 TV clients.
- **Web TV Power Off Switch & Retro CRT Collapse Animation (`web/ui.js`, `web/dsp.js`, `web/renderer.js`, `web/shaders/crt_post.frag`)**: Fixed a bug where the Web TV power switch was a one-way toggle that could not turn the TV off once powered on, and added an authentic 3-phase **CRT Electron Beam Collapse** shutdown animation. Powering down now:
  1. *Vertical Collapse*: Compresses the active visualizer vertically into an intensely bright, overdriven phosphor line across the center.
  2. *Horizontal Shrink*: Pulls the line horizontally inward from both edges, shrinking it into a brilliant pinpoint dot at the center of the tube.
  3. *Phosphor Decay*: Decays the central dot with natural phosphor persistence until it gently fades into total darkness.
  4. Audio & Indicators: Suspends the Web Audio context, cuts the live Icecast stream, flips the physical switch knob up, and dims the station LED.
- **Pristine Web TV Display Presentation (`web/shaders/crt_post.frag`, `web/renderer.js`)**: Streamlined the Web TV post-processing pass to display the live visualizer with maximum fidelity when powered on. Stripped out steady-state distortion artifacts (corner dimming/vignette, interlacing/scanlines, RGB shadow mask grating, chromatic aberration, and barrel warp) while fully preserving the 1.5s CRT warmup expansion and the 3-phase beam collapse shutdown sequence.
- **Independent Web TV Presets & Playlists (`web/presets/`, `web/playlists/`, `web/autopilot.js`)**: Decoupled the Web TV client completely from the desktop application's `library/` folder. Replaced the `web/library` symlink with dedicated, self-contained `web/presets/` and `web/playlists/` directories populated with curated web visualizers (`mandala_flow`, `spiral_drift`, `cosmic_ribbon`, `hyperspace_slice`, `attractor_flow`, `ambient_bg`, `dark_spiral`) and default playlist files (`default.lsdplay`, `default_bg.lsdplay`). Updated `autopilot.js` to resolve relative preset and playlist paths to these web-specific directories by default.

#### 0.4. Renderer Polymorphism & Draw Topology Dispatch (`DynamicVisualSource.kt`, `Mandala.kt`, `HyperMesh.kt`, `Renderer.kt`)
- **Zero Source-Type Knowledge in Renderer**: Completely eliminated `is Mandala` and `is HyperMesh` branching and private helper methods (`renderMandala()`, `renderHyperMesh()`) from `Renderer.kt`. The main rendering pipeline now collapses into a single, unified execution path.
- **Polymorphic `drawTopology()` Dispatch**: Introduced `open fun drawTopology()` on `DynamicVisualSource`, delegating vertex attribute binding and geometry draw calls to each visual source:
  - Default: Renders a fullscreen quad via `Geometry.drawFullscreenQuad()`.
  - `Mandala`: Binds ribbon VAO and draws triangle strips via `glDrawArrays(GL_TRIANGLE_STRIP, 0, (POINTS + 1) * 2)`.
  - `HyperMesh`: Binds 4D polychoron strut and node VAOs and renders indexed triangles via `glDrawElements(GL_TRIANGLES, ...)`.
- **Encapsulated GPU Geometries & Clean Lifecycle**: Moved ribbon VAO/VBO creation and disposal directly into `Mandala.kt` (`initGeometry()`, `dispose()`, and shallow handle sharing in `clone()`), ensuring leak-free and double-free-safe lifecycle tracking.
- **Unified Common Uniforms**: Common frame uniforms (`uAlpha`, `uResolution`, `uTime`, `uAspectRatio`) are now set in a single location for all dynamic sources inside `Renderer.render()`.


#### 0.5. macOS Apple Silicon & Intel Packaging & Launch Reliability (`build.gradle.kts`, `run-mac-arm.command`, `run-mac-intel.command`)
- **Resolved macOS Bundled JRE Resolution**: Fixed an issue where macOS distribution launcher scripts reported *"Bundled JRE not found. Trying system java..."*. Adoptium macOS `.tar.gz` distributions extract using standard Apple bundle hierarchy (`Contents/Home/bin/java`). Launchers (`run-mac-arm.command` and `run-mac-intel.command`) now probe both `jre/macos-<arch>/Contents/Home/bin/java` and flat `jre/macos-<arch>/bin/java` before falling back to system Java.
- **Main OS Thread Execution (`-XstartOnFirstThread`)**: Fixed fatal startup crash (`IllegalStateException: GLFW may only be used on the main thread and that thread must be the first thread in the process`) on macOS Apple Silicon and Intel. Both bundled JRE and system fallback paths in the `.command` scripts, as well as Gradle `JavaExec` tasks when running on macOS, now strictly pass `-XstartOnFirstThread`.
- **JDK 21+ Native Access Warning Suppression**: Added `--enable-native-access=ALL-UNNAMED` to all launchers (Windows, Linux, macOS) and `JavaExec` to eliminate restricted method warnings for LWJGL/Unsafe native callers when running on modern JVMs.
- **macOS Gatekeeper Quarantine Stripping**: Added `xattr -dr com.apple.quarantine jre 2>/dev/null || true` in macOS launcher scripts to ensure bundled JREs downloaded via GitHub zip archives are not blocked by Gatekeeper.
- **Zip Executable Permissions**: Configured Gradle distribution packaging (`zipMacArm`, `zipMacIntel`) to enforce `755` permissions across all binaries in `bin/`, `jspawnhelper`, and `.command` scripts.

#### 1. Performance-Driven UI & Ergonomics Enhancements (`UIManager.kt`, `PresetListPanel.kt`, `DeckControlPanel.kt`, `MenuBar.kt`, `CellConfigPanel.kt`)

- **Horizontal Scroll Wheel Isolation & Layout Stabilization (`UIManager.kt`, `LibraryPanel.kt`, `PresetGridPanel.kt`)**: 
  - Globally disabled `io.mouseWheelH` per frame to prevent unintended horizontal panning across all panels when using two-finger trackpad drag, ThinkPad TrackPoint (nipple) center-button scrolling, or horizontal scroll wheels.
  - Sized Library columns dynamically against `getContentRegionAvailX()` and inner spacing instead of outer window width, eliminating the overflow that caused the 4 columns to shift left and right.
  - Enforced `NoScrollbar` on outer Library and Mixer/Monitor panels and added horizontal scroll resets (`setScrollX(0f)`) across child panels to prevent parameter labels or cards from drifting off-screen.
- **Library & Preset Grid Vertical Kebab More Actions Button (`⋮`) (`Icons.kt`, `BrowserRowMoreButton.kt`, `PresetListPanel.kt`, `PlaylistEditorPanel.kt`, `QueueActionsPanel.kt`, `BgQueueActionsPanel.kt`, `PresetGridRenderer.kt`)**: 
  - Corrected `Icons.MORE_VERTICAL` (`\ue0b7`) and `Icons.MORE_HORIZONTAL` (`\ue0b6`) to match Lucide's TrueType Private Use Area codepoints for true vertical kebab (`⋮`) and horizontal ellipsis, replacing an incorrect mouse pointer arrow glyph.
  - Widened button hit target width to 28px across Library panels and the Preset Grid, enlarged dot size to 3.5px radius with 9.0px vertical spacing, and centered the three vertical dots cleanly with subpixel precision using geometric circle rendering (`ImDrawList.addCircleFilled`), eliminating font advance skew where dots were touching the right border.
  - Implemented seamless popup switching: clicking any kebab button immediately closes any currently open popup and opens the target row's context menu in a single click, suppressing hover tooltips when the menu opens.
  - Sized list row selectables and invisible buttons across the Library and Preset Grid to maintain dedicated hit-testing bounds, ensuring left-clicks and right-clicks on the kebab button reliably trigger context menus on the first click without selectable event stealing or hover flicker.
  - Added the vertical kebab button to parameter rows in the Preset Grid (flush right within the parameter label column), providing left-click discoverability for row actions (Randomize row, Copy/Paste modulations, Reset to default, Clear CVs/MIDI, and Add/Edit Parameter Notes).
- **Instant Search Focus (<kbd>Ctrl+F</kbd> / <kbd>/</kbd>) & Clear (<kbd>Esc</kbd>)**: Pressing <kbd>Ctrl+F</kbd> or <kbd>/</kbd> (when not focused on a text input) automatically opens the Library (if hidden) and focuses the preset search bar with text highlighted. Pressing <kbd>Esc</kbd> while the search box is active clears the filter query and returns focus back to the preset table for seamless keyboard navigation (`1`–`4`, `Q`, `Shift+Q`, `↑`, `↓`).
- **Dynamic HiDPI Scale Detection & Multi-Monitor Adaptation**: Separated UI scaling into dynamic system DPI auto-detection (`systemDpiScale`) and persistent user zoom preference (`guiScalePercent`). Liquid LSD now queries GLFW content scale on every startup and responds dynamically via `glfwSetWindowContentScaleCallback` when dragging windows across displays or changing OS scaling, automatically rebuilding the Dear ImGui font atlas and UI geometry.
- **Dynamic "On-Air" Deck Illumination & Glow**: Decks A and B dynamically scale header, toolbar, and border brightness based on `Mixer.crossfade` position. Active on-air decks render at 100% full saturation with bright glowing borders, while off-air decks smoothly dim to $\sim 35\%$ opacity.
- **Cell Config Modular Accordions & Dirty Indicator (`•`)**: 
  - Converted LFO 2 from an auto-hiding section into an explicit collapsible accordion (`▶ LFO 2 (Modulator)` / `▼ LFO 2`).
  - Wrapped Audio and Trigger multi-band modulators in collapsible headers (`Amplitude`, `Low / Bass`, `Mid`, `High`, `Onset`, `Accent`).
  - Grouped secondary LFO 1 parameters (`Phase Offset`, `Morph`, `Hold`, `Slew`) into an `Advanced Parameters` accordion with an illuminated dirty indicator dot (`•`) whenever any collapsed parameter differs from its default.
  - **Square Wave PWM Duty Cycle & Plateau Hold Ceiling (`Lfo1Section.kt`, `Lfo2Section.kt`, `WaveformMath.kt`, `Evaluators.kt`)**:
    - Fixed Square wave shape preset button setting `Hold` to `0.5` (a 50% trapezoid); selecting Square now sets `Hold` to `0.999` for true sharp vertical transitions.
    - Raised the upper bound for the `Hold` parameter from `0.990` to `0.999` across math evaluators and UI sliders with zero-division protection.
    - Implemented variable Pulse Width Modulation (PWM) for `Waveform.SQUARE` using `slope` (duty cycle from 1% to 99%, default 50%).
    - Dynamically adapts UI labels and quick-presets: when Square wave is selected, "Slew" / "Asymmetry" becomes "Duty Cycle" / "Duty Preset" with 10%, 50%, and 90% pulse buttons.
    - **Expressive Square Wave & Duty Cycle Waveform Icons (`CustomIconButton.kt`, `Lfo1Section.kt`, `Lfo2Section.kt`)**: Added the leading rising edge to the square wave icon so both leading and trailing edges are fully drawn. Added dedicated 10% duty (`WaveShape.SQUARE_10`) and 90% duty (`WaveShape.SQUARE_90`) waveform icons for the duty cycle preset buttons in Cell Config (LFO 1 and LFO 2), visually differentiating narrow 10% pulse, balanced 50% square, and wide 90% pulse states.
  - **Per-Cell Persistent Accordion State & Default Closed Behavior**: Scoped all Cell Config modulator controls to their respective parameter key and CV source ID (`${cell.paramKey}_${cell.cvSourceId}`). Accordion folds in Audio and Trigger multi-band sections as well as LFO 2 now start closed by default (with LFO 1 remaining open and accessible), and opening/closing folds on one grid cell remains isolated and independent from other cells.
  - **Middle-Click Reset, Preset Grid Mute & Hardware Click Latching (`UIManager.kt`, `PresetGridRenderer.kt`, `CustomRangeSlider.kt`, `BeatDivisionSlider.kt`)**: Middle-clicking any modulation cell (MIDI CC, LFO, AUD, TRIG) instantly toggles its bypass/mute state, and middle-clicking parameter rows or value cells resets parameters back to defaults. In Cell Config, middle-clicking on any slider track, text input box, or variable name label instantly resets the parameter to its factory default value with contextual hover tooltips. Added a chained GLFW mouse button callback with a single-frame latching queue in `UIManager` to ensure instantaneous hardware middle-click releases (such as ThinkPad TrackPoint center buttons and Linux/libinput scroll emulation taps) are never dropped during event polling or frame rate dips.
- **Preset Grid Save & Save As Shortcuts (<kbd>Ctrl+S</kbd> / <kbd>Shift+Ctrl+S</kbd>)**: Added global keyboard shortcuts in the Preset Grid. Pressing <kbd>Ctrl+S</kbd> saves the active deck's preset directly (or opens the Save Preset As dialog if the preset is untitled). Pressing <kbd>Shift+Ctrl+S</kbd> opens the Save Preset As modal for the active deck with auto-suggested duplicate naming (`_copy`). Both shortcuts are cleanly ignored when the Preset Grid is focused on the Mixer or an empty deck.
- **Preset Grid Horizontal Column Headers & Dedicated `SRC` Tab Layout**: Converted column titles into horizontal headers (`VAL`, `MIDI`, `LFO`, `AUD`, `TRIG`) sized at `UITheme.FontLevel.BODY`. Separated visual source selection from parameter tab navigation: the visual generator dropdown (`[Source ▾]`) resides in the Preset Grid title bar beside `"Preset Grid"`, while parameter sub-tab navigation is standardized with a dedicated, compact **`SRC`** tab (`[SRC] [FX] [View]`) above the first parameter name with a 24px left indent, preventing long visual source names from crowding the section tabs against the CV columns. Left side tabs (`MIX`, `A`, `B`, `BG`, `PV`) align with the second parameter row.
- **Deck A/B Mix Mode Exposed in PatchGrid (`MIX` Tab)**: Added a dedicated row for `mix mode` in the Preset Grid Mixer tab. It is exposed as a non-modulatable discrete parameter with a dropdown combo selector in the `VAL` configuration panel supporting all 5 blend equations: `0: Add (ADD)`, `1: Screen (SCREEN)`, `2: Multiply (MULT)`, `3: Max (MAX)`, and `4: Crossfade (XFADE)`.
- **Mixer Master Monitor Click Shortcut (`MixerMonitorPanel.kt`)**: Left-clicking the main output monitor in the Mixer / Monitor panel immediately switches the Preset Grid focus to the Mix tab (`activeTopTab = "Mixer"`), complementing the existing deck monitor click shortcuts (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`).
- **Harmonized 5-Column CV Modulation Palette & Theme Unification (`CvTheme.kt`, `PresetGridRenderer.kt`)**: Redesigned the modulation color scheme for the pruned 5-column layout across `PresetGridPanel`, `CellConfigPanel`, and `AudioEnginePanel` into 5 distinct color wheel quadrants: `VAL` (Mint Cyan), `MIDI` (Bright Orchid Violet), `LFO` (Electric Sky Blue), `AUD` (Warm Amber Gold), and `TRIG` (Hot Coral Rose). Replaced all hardcoded/duplicate color tables with centralized `CvTheme` calls. Removed per-column cell knob/arc tinting in favor of inheriting the active theme text color (`ImGuiCol.Text`), guaranteeing universal contrast and clean visual clarity across all light and dark themes.
- **4-Beat Phase Meter in Top Menu Bar**: Added a 4-dot quarter-note beat meter (`[ ● ○ ○ ○ ]`) immediately preceding the `BPM` display in the top Menu Bar, giving instant, zero-clutter confirmation of audio sync and downbeat alignment in sync with the Beat Tracker.
- **Safe Visual Source Switching & Stale Parameter Guard (`DeckPresetController.kt`, `PopupManager.kt`, `CellConfigPanel.kt`)**: Added a user confirmation modal dialog when switching a deck's visual generator if an active preset is loaded or has unsaved edits, preventing accidental preset file overwrites. Automatically clears active preset bindings, resets stale cell selections in `PresetGridState`, updates subtabs, and adds defensive live parameter path resolution in `CellConfigPanel` to eliminate orphaned/detached parameter modulators.

#### 1. Streamlined 3-Column Layout Architecture & Spacebar Library Cycling (`UIManager.kt`, `LibraryPanel.kt`)

- **Locked Aspect-Ratio End Columns**: Sized Column 1 (Preset Grid) dynamically to its active columns and Column 3 (Mixer / Monitor) strictly to its aspect-ratio preview capacity, eliminating wasted horizontal letterboxing and removing manual column splitters.
- **Flexible Middle Column & Library Spanning**: Configured Column 2 (Cell Config) and the docked Library to flexibly absorb all remaining horizontal workspace width across all display resolutions.
- **4-Step Spacebar Library Cycling**: Tapping <kbd>Space</kbd> (when not focused on a text input or search bar) cleanly cycles through `HIDE` $\rightarrow$ `HALF` $\rightarrow$ `FULL` $\rightarrow$ `HALF` $\rightarrow$ `HIDE`.
- **Title Bar Drag-to-Resize & Standardized Window Controls (`LibraryPanel.kt`)**: Replaced the left-hand layout buttons with right-aligned window decorations matching the top title bar: `[-]` (Minimize to bottom dock / Restore) and `[□]` / `[❐]` (Maximize to Full / Restore to Half). Dragging empty space in the Library menu bar vertically resizes the Library height smoothly, with double-click snapping to 50% height.

#### 2. Static Website & Documentation Export Generator (`SiteGenerator.kt`, `build.gradle.kts`, `website/`)

- **Native JVM Static Site & Docs Generator**: Added `./gradlew buildWebsite` (and `./gradlew exportGreenjon`) task to compile the complete Liquid LSD product website, documentation viewer, and offline documentation archive directly into `./greenjon/` for manual FTP deployment to `greenjon.com`.
- **Single Source of Truth**: Sourced documentation dynamically from `docs/` and `RELEASE_NOTES.md`, and version badges/release download links directly from Gradle metadata (`project.version`).
- **Responsive Media & Showcase Layout**: Included responsive video tutorial container slots, interactive screenshot lightbox galleries, and offline ZIP package bundling (`greenjon/docs.zip`).
- **Centralized Media Export & Web Subsystem Documentation**: Added dedicated User Guides and Developer References for disk persistence, real-time MP4 video recording, offline 4K render studio with motion blur, live WebSocket broadcasting, retro CRT TV player controls, and relay server protocols.

#### 2. Standalone "Colors" Visual Source & Mandala Background Extraction (`library/sources/colors`, `Mandala.kt`, `Renderer.kt`)

- **Extracted "Colors" Visual Source**: Packaged solid-color and plasma generation capabilities into a new standalone visual source (`library/sources/colors/`) featuring `Style` (0 = Off, 1 = Solid, 2 = Plasma), `Hue`, `Sat`, `Val`, `Sweep`, `Speed`, and `Zoom` parameters.
- **Mandala Source Simplification**: Removed legacy background parameters (`Bg Style`, `Bg Feedback`, `Bg Hue`, `Bg Sat`, `Bg Val`, `Bg Sweep`, `Bg Speed`, `Bg Zoom`) from Mandala's parameter metadata, shaders, and UI layout.
- **Rendering Pipeline Cleanup (`Renderer.kt`, `Deck.kt`)**: Removed legacy secondary background passes from `renderMandala()` and `renderDeck()`, along with `Deck.getOutputTexture()` branch hacks and the unused `background.frag` resource shader.
- **Uniform Multi-Deck Compositing**: Any deck layer (`Deck A`, `Deck B`, `Deck BG`, or `Deck PV`) can now seamlessly host the `Colors` generator with full modulation and feedback support.

#### 2. Beat Tracker Real-Time Engine & Complex Spectral Difference ODF (`llm.slop.liquidlsd.audio.BeatTrackerEngine`)

- **Beat Tracker & Causal Dynamic Programming Architecture**: Fully integrated a stateful, zero-allocation beat tracking engine (`BeatTrackerEngine`) modeled on BTrack (Adam Stark) and the Dan Ellis causal DP beat tracker.
- **Complex Spectral Difference ODF**: Evaluates a 512-point Radix-2 Cooley-Tukey FFT with pre-computed twiddle factors and 2nd-order phase trajectory prediction to detect pitched notes and percussive transients while suppressing steady tones.
- **Two-State Multi-Band Periodicity Estimation**: State 1 (Acquisition) searches 40–200 BPM across circular history buffers; State 2 (Locked Tracking) constrains search to $\pm 15\%$ around current tempo period with $\pm 2.0$ BPM/beat human tracking inertia and harmonic comb unwrapping.
- **Causal Dynamic Programming Recurrence**: Pre-computed $\log(\tau)$ table (`logTauTable`) evaluates DP recurrence without transcendental `Math.log()` calls on the real-time audio thread.
- **Zero-Allocation Phase & Cosine Queries**: Exposes primitive queries (`getPhase(t)`, `getCosine(t)`, `getPhaseAndCosine(t, out)`, `getPhaseAndCosinePacked(t)`) for continuous visual phase modulation $\cos(2\pi \phi(t))$ with zero object allocations on the render thread.
- **Breakdown Flywheel Tempo Retention ("Dead Reckoning")**: When incoming audio energy drops below analysis thresholds (such as during quiet breakdowns, vocal bridges, or silence), `BeatDetector` and `BeatTrackerEngine` retain the last confirmed tempo without decaying to 120.0 BPM. The phase accumulator continues coasting at the track tempo with zero slew drift and suppressed phase nudges, ensuring beat-synced visuals remain locked across breakdowns and eliminating re-acquisition delay at the drop.
- **Legacy Beat Detection Algorithm & Slider Removal**: Removed obsolete detection modes (`BeatDetectionMode.AUTOCORRELATION`, `ENERGY_DIFFERENCE`, `RESONATOR`) and deprecated sliders (`Analysis Length`, `Energy Threshold`, `PLL Adaptation`, `Resonator Q`) from `AudioEngine` and `AudioEnginePanel`, standardizing the audio subsystem entirely on the Beat Tracker with clean target band selection (`LOW`, `MID`, `HIGH`, `UNFILTERED`) and presets (`High Accuracy`, `Balanced`, `Eco`).
- **Leaky Decay Hysteresis Shock Absorber**: Implemented graceful leaky decay (`dt * 2.0f`) with capped accumulated stability (`1.5 * stabilityLockDurationSec`) in `BeatTrackerEngine`'s tempo lock state machine, preventing periodic 4-second hiccups and frame glitches from dropping lock while allowing quick adaptation on true tempo changes.
- **Decoupled Periodic Autocorrelation**: Decoupled multi-second autocorrelation calculation from the per-block rate to run periodically every 4 blocks (~46 ms, ~21.5 Hz) with physical time-step scaling ($dt_{\text{interval}} = dt \cdot 4$), slashing loop iterations by 75% without compromising tempo lock speed or accuracy, preventing real-time audio buffer underruns (XRUNs).
- **Lock-Free Audio Recording Ring Buffer (`RealtimeRecorder.kt`)**: Replaced `ArrayBlockingQueue` (which used `ReentrantLock` mutexes) with a zero-allocation, wait-free Single-Producer Single-Consumer (`SpscQueue`) bounded ring buffer for live audio block tapping in `pushAudioBlock()`, eliminating lock contention and priority inversion risks on the real-time audio thread.
- **Subnormal / Denormal Float Flushing (`BiquadFilter.kt`)**: Flushes recursive state variables (`z1`, `z2`) to zero when $|\text{state}| < 10^{-15}\text{ f}$, preventing CPU microcode traps and pipeline stalls during trailing audio silence or decaying signal tails.

#### 3. Library Menu Bar Action Strip, Quick Audition Padlock & Keyboard Navigation (`llm.slop.liquidlsd.ui.browser`)

- **Unified Menu Bar Action Strip**: Migrated the preset routing toolbar (`[🔒] [A] [B] [BG] [PV] [Q] [BGQ]`) into the top Library Menu Bar with widened buttons and centered layout.
- **Global 4-Column Preset Selection**: Single-clicking or navigating any item across Preset Library, Playlist Editor, A/B Play Queue, or Background Queue sets a unified global selection and clears other columns.
- **Quick Audition Latch (`[🔒]`) & Smart `PV` Auto-Latch**: Toggling the padlock button arms sticky audition mode and auto-latches to **Deck PV** (Preview) by default. Clicking another deck button (`A`, `B`, `BG`) switches the latch target, while clicking the active deck button unlatches it.
- **Seamless Focus Restoration & Keyboard Hotkeys**: Restores keyboard navigation focus to selected items after clicking routing buttons. Added instant hotkeys `Q` (append to Play Queue) and `Shift+Q` (append to Background Queue) alongside `1`–`4` deck quick-load keys.
- **Column Title Bars & Full-Width Filter Bars**: Added top header title bars across all four columns (`Presets`, `Playlists`, `Queue`, `BG Queue`), allowing search input and playlist dropdowns to span full column width.

#### 4. Symmetrical Background Queue Management & Modulation Parity (`llm.slop.liquidlsd.presets`)

- **Complete Playlist & Queue Parity**: Added BG Queue context menu options ("Play now in BG Queue", "Insert into BG Queue after current", "Add to bottom of BG Queue"), bidirectional routing between queues, and dedicated `.lsdplay` export.
- **Modulation & MIDI Auto-Advance**: Added `Mixer/bgQueuePrev` and `Mixer/bgQueueNext` modulatable parameters with MIDI CC inputs (`Global/bgQueuePrev`, `Global/bgQueueNext`).
- **Robot Icon Queue Toggles**: Replaced text checkboxes with robot toggle buttons (`Icons.BOT` / `Icons.BOT_OFF`) for both A/B Queue (`AUTO-VJ`) and Background Queue (`AUTO-BG`).
- **Deck BG Dirty State Protection**: Symmetrically guards BG Queue auto-advances with `PresetManager.isDeckDirty` respecting `UITheme.autoVjDirtyBehavior`.

#### 5. Consolidated Audio Engine Settings & UI Polish (`llm.slop.liquidlsd.ui`, `llm.slop.liquidlsd.cv`, `llm.slop.liquidlsd.audio`)

- **Decoupled Audio-Rate CV History Pushing**: Directly appends audio RMS and onset signals (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`, `trigger_onset`, `trigger_accent`) to `CvHistoryBuffer` ring buffers inside `AudioEngine.processAudio()` at the audio block rate (~86–344 Hz). Eagerly caches buffer references on `AudioEngine` to maintain zero-allocation, lock-free JACK callback safety, making audio oscilloscopes immune to UI frame drops, GC pauses, or render-thread hitches.
- **Frame-Delta Beat Clock Extrapolation**: Replaced the static flatline clamp (`safeBeats = current`) in `CVRegistry.getSynchronizedTotalBeats()` with elapsed frame-delta forward extrapolation (`current + frameDtSec * (bpm / 60.0)`), completely eliminating periodic oscilloscope freezes, flatlines, and stutter on `beatSine`, `beatPhase`, and beat-synced LFO modulators.
- **Anchor Block Duration Alignment**: Fixed beat anchor timestamps in `AudioEngine.kt` to reflect `currentTime + blockDurationNs`, perfectly aligning `anchorTimeNs` with `totalBeats` calculated at the end of the processed block.
- **Two-Column Audio Engine Settings Tab**: Consolidated driver selection, JACK auto-reconnect, beat detection settings, interactive dual-headed BPM range slider, input gain, and sound-derived CV oscilloscopes into a balanced 2-column layout in Settings.
- **Zero-Lag Oscilloscope Slicing**: Fixed `CvHistoryBuffer.copyTo()` to display the true latest chronological window without latency delay, and expanded the trace buffer to 400 samples.
- **Hardware Device Caching**: Cached ALSA/JavaSound device introspection in `AudioEngine.kt` to prevent per-frame querying handle leaks and out-of-memory crashes.
- **Full Audio Engine Settings Persistence**: All Audio Engine configurations—including audio backend selection (`AUTO`, `JACK_ONLY`, `JAVASOUND_ONLY`), hardware input device selection, manual BPM lock toggle, manual BPM slider, onset target frequency band (`LOW`, `MID`, `HIGH`, `UNFILTERED`), beat tracker presets, and BPM search range floor/ceiling—are now serialized to `lsd-settings.properties` and restored on startup.
- **Theme High-Contrast Palette**: Refined frame borders and contrast across Boring, Solarized, Lunarized, and Neon themes.
- **Deck PV Standardization**: Replaced all remaining legacy "Deck C" references across codebase, UI, and documentation with "Deck PV" (Preview).

---

### 📜 Full Commit History (v1.0.0-beta.30 → v1.0.0-beta.31)

- `ea3bddd` fix(ui): prevent window dragging on slider click-drag and standardize settings sliders
- `b03064b` Fix Audio Engine oscilloscope display lag, zero-center BeatSine, and expand history window
- `ae82e39` fix(audio,ui): eliminate beat clock hesitation, enforce monotonic sync extrapolation, and refine audio settings layout
- `d5ec95a` feat(audio,ui): add low-signal 120 BPM fallback, tempo stability gating, and theme contrast enhancements
- `1d55d71` fix(audio): cache input hardware devices and prevent OOM crash in AudioEnginePanel
- `6457da1` refactor(deck): remove legacy Deck C compatibility layer and enforce Deck PV across codebase
- `65cd5ff` Replace AUTO-VJ and AUTO-BG checkboxes with robot icon toggle buttons
- `6b41f59` fix(ui): fix ColorTuner z-order, close handling, double scrollbar, and column clipping
- `0058614` ui: consolidate Audio Engine into Settings tab with zero-allocation monitor drawer
- `1bc689f` feat(ui,library): add seamless focus restoration, continuous arrow navigation, and Q/Shift+Q queue hotkeys
- `07e37ac` docs(library): update user guide and release notes for column title bars and button layout
- `458e5bd` feat(ui,browser): add panel title headers and full-width search/dropdown bars across library views
- `92c5903` refactor(ui,presets): unify deck transition guards across all load and new preset pathways
- `b670b1d` fix(presets,ui): resolve false dirty state after preset load and clean up browser toolbar layout
- `0a269a5` feat(ui,browser): center action toolbar in menu bar and enable focus-based keyboard navigation
- `c89f920` feat(ui,browser): add unified library menu bar action strip and quick audition lock
- `c818573` feat(ui,grid): add grid shortcuts, middle-click mute, deck quick-load keys, and shortcuts settings page
- `bb2f7a2` feat(audio,sync): normalize audio CV follow scaling and add desktop-to-web sync tooling

---

## Version 1.0.0-beta.30

> [!NOTE]
> **Release 1.0.0-beta.30** introduces live WebSocket broadcasting from Liquid LSD Desktop, the standalone browser-based WebGL2 visualizer with 10+ shader ports, real-time Web Audio DSP streaming, an interactive retro CRT TV shell with phosphor/scanline post-processing, and a stateless Node.js relay server with 24/7 Autopilot fallback.

---

### Key Highlights

#### 1. Live Web Broadcasting & Desktop WebSocket Broadcaster (`llm.slop.liquidlsd.broadcast`)

- **Zero-Impact Asynchronous Architecture (`BroadcastEngine.kt`)**: Decoupled WebSocket broadcaster running on a dedicated daemon background thread (`BroadcastEngine-IO`), ensuring 0 ms impact on JACK audio processing and GLFW/OpenGL frame rates.
- **Non-Blocking WebSocket Dispatch**: Protected asynchronous transmission using `CompletableFuture` to prevent transmission queue buildup, memory leaks, and `IllegalStateException` on high-latency or slow network connections.
- **Throttled Parameter Delta Streaming**: Dispatches full state snapshots (`state_full`) upon initial handshake or preset switching, and lightweight differential patches (`state_delta`) throttled at a configurable rate (default 25 Hz) during live parameter adjustments. Includes JSON `null` deletion semantics when swapping visual source types.
- **Live Modulation Tracking**: Transmits real-time modulated parameter values (`param.value`) instead of static knobs, reproducing dynamic audio reactivity on remote TV clients.
- **Dedicated Web Broadcast Settings & Menu Bar HUD**: Added "Web Broadcast" category in `SettingsPanel.kt` (relay URL, auth token, auto-connect, rate limits) and a live `[LIVE]` status indicator with one-click broadcast toggle in `MenuBar.kt`.

#### 2. Standalone WebGL2 Core Visualizer & Multi-Shader Parity (`web/`)

- **Zero-Dependency Browser Pipeline**: Complete browser-based WebGL2 / GLSL ES 3.0 port of the core multi-pass rendering pipeline (`Mandala`, `DynamicSpiral`, `feedback.frag`, `mixer.frag`, `blit.frag`).
- **Full Visual Source Parity (10+ Shaders)**: High-performance WebGL GLSL ports for `Mandala` (vert/frag), `DynamicSpiral`, `AttractorFeedback`, `Chladni`, `Gyroid`, `HyperSlice`, `Icosahedron`, `IcosaDodeca`, and `IcosaV3`.
- **Ping-Pong Feedback FBOs**: Implemented `RGBA16F` half-float framebuffer textures with `EXT_color_buffer_float` and automatic fallback to `RGBA8`.
- **Mathematical Geometry Engine (`web/icosahedron_math.js`, `web/evaluator.js`)**: Client-side mathematical evaluation for complex plane math, Du Val stellation planes, and $H_3$/$H_4$ symmetry folding.

#### 3. Retro TV Shell & CRT Post-Processing (`web/tv.css`, `web/ui.js`, `web/shaders/crt_post.frag`)

- **Interactive Retro TV Shell**: Wrapped the browser visualizer in a realistic retro CRT TV bezel with an illuminated LED station indicator (`SPAZ RADIO • CH.1` / `SPAZ RADIO • LIVE`), clickable physical power toggle, and mouse/touch draggable rotary volume dial.
- **CRT Warmup & Cold-Boot Sequence**: Animated high-frequency static snow on cold boot; toggling power initiates a 1.5s raster warmup sequence with expanding green-tinted beam line and phosphor decay flash.
- **Comprehensive CRT Shader Pipeline (`crt_post.frag`)**: Single-pass post-processing shader replacing final blit with barrel distortion curvature, scanlines, 3-pixel RGB phosphor shadow mask triad, corner vignette, chromatic aberration channel splitting, and ambient phosphor persistence.
- **Draggable Rotary Volume Dial & Fullscreen Mode**: Drag up/down on the rotary dial controls audio output volume via Web Audio `GainNode` with a perceptually linear squared response curve ($V^2$). Double-clicking the screen expands the visualizer to borderless fullscreen projection mode.

#### 4. Web Audio DSP & Live Stream Integration (`web/dsp.js`)

- **Live Stream DSP**: Connected `https://radio.spaz.org:8060/radio.ogg` to real-time Web Audio graph via lowpass (bass), bandpass (mid), highpass (high), and broadband RMS analysers.
- **Beat Detection & Phase Tracking**: Implemented dual-average onset detection with IOI history for adaptive BPM calculation and beat-synced LFO signals.
- **Audio-Reactive Uniforms**: Wired live CV channels (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`, `beatPhase`, `beatSine`, `trigger_onset`) to dynamically modulate Deck A & Deck B shader parameters in standalone web mode.
- **Click-to-Start Gesture UX**: Autoplay policy compliance with single-click unlock overlay and AudioContext auto-resumption.

#### 5. WebSocket Relay Server & 24/7 Autopilot Scheduler (`server/`, `web/autopilot.js`)

- **Stateless WebSocket Relay (`server/server.js`)**: Lightweight Node.js relay server featuring role-based token authentication (`role=broadcast&key=...`), `state_full` payload caching, and fan-out distribution to all active web viewers with zero transcoding latency. Includes `server/lsd_relay` CLI runner.
- **24/7 Autopilot Scheduler (`web/autopilot.js`)**: Autonomous client-side playlist scheduler executing smooth fade-through-black transitions across curated presets when offline.
- **Seamless Live Broadcast Handshake**: Automatically transitions web viewers from the 24/7 Autopilot to the live broadcast when the VJ connects, updating the station LED badge to `SPAZ RADIO • LIVE`.

#### 6. Dynamic Spiral Continuous Phase Tracking & Stability Hotfixes

- **Continuous Dead-Reckoning Integration**: Synchronizes `integratedTime` and `integratedShear` from desktop to WebGL while maintaining local dead-reckoning between updates, eliminating 60fps stutter.
- **WebSocket URL & Encoding Robustness**: Correctly URL-encodes tokens and handles base URLs with fragment identifiers.
- **Safe Fallback for Unknown Sources**: Safely maps unknown visual sources to `"unknown_source"`.

---

### 📜 Full Commit History (v1.0.0-beta.29 → v1.0.0-beta.30)

- `50b047b` fix(broadcast): resolve WebSocket sync, phase tracking, and state serialization issues
- `5eae5c6` feat(broadcast): add live WebSocket broadcasting, web math/evaluator modules, and shader parity
- `ac95831` feat(web): add standalone WebGL2 core visualizer, Web Audio DSP, retro CRT TV shell, and relay server

---

## Version 1.0.0-beta.29

> [!NOTE]
> **Release 1.0.0-beta.29** delivers high-performance live video recording with real-time audio muxing, deterministic time virtualization for offline rendering, hardware encoder prioritization, and a fully resizable settings panel with automatic persistence.

---

### Key Highlights

#### 1. Live Video Recording with Zero-Allocation Audio Muxing

- **Real-Time Audio Tapping (`AudioEngine.kt` & `RealtimeRecorder.kt`)**: Zero-allocation audio tapping directly inside `AudioEngine.processAudio` using a pre-allocated pool of `AudioBlock` instances on the real-time audio thread (Linux/JACK & cross-platform Java Sound).
- **Asynchronous Audio PCM Writer & Lossless Remuxing**: Background worker streams 16-bit PCM WAV audio during recording and losslessly multiplexes it into the final container using FFmpeg (`-c:v copy -c:a aac -b:a 320k -shortest`) upon stopping.
- **Master Preview Tally Overlay**: Pulsing red `REC MM:SS` badge rendered dynamically on the Master preview monitor.
- **Recording Hotkey (`Ctrl+R`) & OS Standard Directory**: Toggle live recording anytime with `Ctrl+R`. Default recording folder automatically resolves to the system Videos directory (`~/Videos/liquid-lsd` or `~/Movies/liquid-lsd`).

#### 2. Deterministic Time Virtualization (`TimeSource.kt`)

- **Centralized Simulation Time Provider**: Replaced non-deterministic OS/GLFW time queries across all shaders (`uTime`), `CVRegistry` evaluators (`AudioFollowerTracker`), `DynamicSpiral`, and `Mixer` with `TimeSource`.
- **Sample-Accurate Audio/Visual Synchronization**: In `OfflineRenderStudio`, `TimeSource.setSimulatedTime(subFrameTimeSec, subFrameDt)` ensures complete deterministic frame-accurate lockstep between audio DSP analysis and visual motion curves regardless of render speed.

#### 3. Hardware Encoder Prioritization with Dynamic Probing

- **GPU Hardware Acceleration**: Automatically probes and prioritizes hardware encoders (`h264_nvenc`, `h264_qsv`) for high-throughput exporting.
- **Seamless Software Fallback**: Validates encoder functionality with 1-frame probe and automatically falls back to `libx264`, `libx265`, or `prores_ks` if GPU hardware is unavailable.

#### 4. PBO Readback & Zero-Allocation Buffer Pipelines

- **Fast DMA Transfers**: Replaced row-by-row CPU vertical flipping with `MemoryUtil.memCopy` block DMA transfers and delegated vertical flip to FFmpeg filter graph (`-vf vflip`).
- **Zero-Allocation Stream Buffer**: Reused persistent class-level 64 KB stream chunk buffers in `FFmpegProcessPipe`, eliminating ~4 MB/s GC heap churn during recording.

#### 5. Offline Render Studio Enhancements (`VideoExportModal.kt`)

- **Integrated File Browser**: Modal file picker (`ImGuiFileBrowser`) for audio tracks, output paths, and preset/setlist snapshots.
- **Match Project Canvas**: Option to match internal project render resolution or standard presets (1080p, 4K, 720p, 9:16 vertical, 1:1 square).
- **Progress Metrics & Error Diagnostics**: Live speed (FPS), elapsed time, ETA, estimated output file size, and multi-line FFmpeg error reporting.

#### 6. Resizable Settings Panel with Automatic Persistence

- **Interactive Sizing**: Enabled free resizing of the Settings modal with minimum bounds and display-clamping constraints.
- **Dynamic Flexible Layout**: Sidebar navigation and content panes stretch seamlessly to fill window dimensions.
- **Auto-Save Dimensions**: Window width and height are preserved across sessions in `lsd-settings.properties`.

---

### 📜 Full Commit History (v1.0.0-beta.28 → v1.0.0-beta.29)

- `45eb36e` feat(export): upgrade live video recording with audio muxing, deterministic time virtualization, and resizable settings
- `88d0760` docs: align architecture, user guide, and developer docs with deck architecture and preset naming
- `bdd3b80` Fix video recording freeze and FFmpeg broken pipe issues

---

## Version 1.0.0-beta.28

> [!NOTE]
> **Release 1.0.0-beta.28** is a major milestone release that includes all features, architectural additions, visual sources, DSP engines, and workflow enhancements developed since **v1.0.0-beta.26** (incorporating all updates from beta 27 and beta 28).

---

### Key Highlights

#### 1. Dedicated Background (BG) Layer & Deck PV (Preview) Pipeline

- **Dedicated Background Deck (`Deck BG`)**: Added a 4th rendering deck `deckBG` rendered beneath the crossfaded Deck A & Deck B composite in GLSL (`mixer.frag`):
  $$\text{Composite} = \text{Blend}(A, B) + \text{BG} \cdot (1.0 - \text{Blend}_{\alpha})$$
  allowing transparent, generative foregrounds to float naturally over dynamic background visuals.
- **Dedicated Preview Deck (`Deck PV`)**: Dedicated preview deck (`Deck PV`) across the entire UI, parameter tree (`Deck PV/...`), and rendering engine for visual auditioning and staging.
- **Expanded Modulatable Parameter Tree**: Added first-class parameter routing for `Deck BG/...`, `Deck PV/...`, `Mixer/randDeckBG`, and `Mixer/randDeckPV`.

#### 2. Symmetrical 2x2 Preview Monitor Matrix

- **Balanced 2x2 Grid Layout**:
  - **Top Row**: `Deck A` (Electric Blue) & `Deck B` (Warm Orange).
  - **Bottom Row**: `Deck BG` (Amber/Gold) & `Deck PV` (Mint Green).
  - Equal aspect ratios and sizes across all four decks with letter overlay badges (`A`, `B`, `BG`, `PV`).
  - Interactive top preset status bars, `Save`, and `Eject` buttons on each monitor.
  - Full drag-and-drop routing and right-click Move/Copy/Swap menus between all 4 decks.
- **Momentary Mixer Controls Bar**: 7 quick action buttons beneath the master crossfader: `< Prev`, `Next >`, `Rand A`, `Rand B`, `Rand BG`, `Rand PV`, `Rand All`.

#### 3. 4-Column Library Layout & Unified Top Action Toolbar

- **4-Column Side-by-Side Library**:
  - **Column 1 (Presets Pool)**: Real-time search and tag filtering across all `.lsd` presets.
  - **Column 2 (Playlist Editor)**: Setlist inspection, drag reordering with mint-green insertion feedback, and instant auto-save.
  - **Column 3 (A/B Play Queue)**: Live Auto-VJ queue with automated crossfading, repeat (`🔁`), and shuffle (`🔀`).
  - **Column 4 (Background Queue)**: Dedicated playlist queue for `Deck BG` featuring automated cycling (`AUTO-BG`) and smooth single-deck dip-to-black fade transitions.
- **Unified Top Action Toolbar (`[A] [B] [BG] [PV] [Q] [BGQ] [+]`)**:
  - Direct routing buttons located cleanly above the Presets and Playlist Editor columns.
  - Removed cluttered inline buttons from individual preset/playlist rows.
  - Mutual selection: Selecting a preset in the Presets column automatically deselects in Playlists (and vice versa).
  - `[+]` button dropdown to quickly initialize a new blank preset on any deck (`[A]`, `[B]`, `[BG]`, or `[PV]`).

#### 4. 4D Polychoron Visual Sources: Hyper-Mesh & Hyper-Slice

- **4D Hyper-Mesh (`hyper_mesh`)**:
  - Real-time GPU-accelerated 4D Polychoron rendering covering the **600-cell** (120 vertices, 720 edges) and **120-cell** (600 vertices, 1,200 edges, dual polytope).
  - Continuous 4D hyper-rotations across the $XW$, $YW$, and $ZW$ planes for inside-out polytope cell inversions and multi-axis tumbling.
  - 4D perspective and conformal stereographic ($S^3 \to \mathbb{R}^3$) projection modes with modulatable focal distance ($d \in [1.05, 5.0]$).
  - Pre-computed Hopf fibration coordinates ($S^3 \to S^2$) per vertex, enabling dynamic harmonic color waves rippling along Hopf tori.
  - Screen-space extruded anti-aliased tube ribbons and billboard joint nodes with zero heap allocation on the render loop ($< 0.3\,\text{ms}$ GPU frametime on Intel Iris Xe).
- **4D Hyper-Slice (`hyper_slice`)**:
  - Real-time raymarched 3D cross-sections ("MRI scan") through 4D 600-cell and 120-cell polychora using $H_4$ Coxeter reflection group symmetry folding (order 14,400).
  - Modulatable `Slice Offset` along the 4D $W$-axis to sweep 3D cutting hyperplanes through 4D solids to witness continuous polyhedral births, morphs, and subdivisions.
  - Full $XW$, $YW$, and $ZW$ 4D hyper-rotations with continuous 600-cell $\leftrightarrow$ 120-cell Wythoff facet normal slerp morphing.
  - Blinn-Phong specular lighting, Fresnel rim reflections, edge crease detection, and translucent crystal interior reveal.

#### 5. Analytic 32-Stellation Du Val Poset Manifold & CSG Visual Sources

- **Icosahedron 32-Stellation (`icosahedron`)**:
  - GPU $k$-th max deduplicating SDF raymarcher extracting the top 6 distinct plane distances across all 60 $H_3$ planes.
  - Continuous 2D morph pad ($uControlX, uControlY$): $Y$-axis slerps the generator vector between Icosahedron and Dodecahedron; $X$-axis continuously extrudes geometry outward through 1st, 2nd, 3rd, and 4th order Kepler-Poinsot star stellations.
  - In-place zero-allocation normal deduplication on the CPU, eliminating GC pauses on the render loop.
- **Icosa-Dodeca ($H_3$ Coxeter Symmetry Folding IFS SDF)**:
  - Mathematically pure $H_3$ Coxeter symmetry folding shader collapsing all 60 polyhedral faces into single base-plane evaluations in the fundamental chamber.
  - Continuous 4-phase cyclic morph slerping along the spherical fundamental triangle arc ($C_3 \leftrightarrow C_5$) through Icosahedron $\to$ Icosidodecahedron $\to$ Dodecahedron $\to$ Great Stellated Dodecahedron $\to$ Great Icosahedron with $C^2$ `smootherstep` pacing.

#### 6. Per-Band Audio Envelope Followers & Dual-Trace Oscilloscope

- **Independent Dynamics Followers**: Independent envelope followers for all 4 audio bands (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`), allowing punchy transient response on one parameter and long sustained decay swells on another.
- **Musical Dynamics Presets**: `Raw (Instant Jitter)`, `Punchy (Fast)`, `Smooth Swell`, `Slow Pulse`, `Ambient Drift`, and `Custom` (with $0\text{ ms} \dots 500\text{ ms}$ Attack and $10\text{ ms} \dots 3000\text{ ms}$ Decay sliders).
- **Dual-Trace Oscilloscope**: Renders raw incoming audio energy in a ghosted trace ($35\%$ alpha) beneath the solid smoothed follower curve.

#### 7. Multi-Band Autocorrelation Beat Engine & Benchmarking Suite

- **Cross-Spectral Autocorrelation Engine (`BeatDetectionMode.AUTOCORRELATION`)**: Zero-allocation primitive ring buffers on the real-time audio callback thread.
- **Harmonic Comb Unwrapping**: Eliminates half-tempo and double-tempo octave traps by verifying fundamental beat periods.
- **Sub-Block Parabolic Lag Interpolation**: Parabolic curve fitting across correlation peaks achieving floating-point tempo tracking within $\pm 0.1$ BPM.
- **Synthetic Audio Benchmark Suite (`BeatDetectorBenchmarkTest.kt`)**: Automated synthetic audio tests for 120 BPM House, 128 BPM EDM, 140 BPM Dubstep, 100 BPM Hip-Hop, and silent breakdowns.

#### 8. Master Crossfader Manual Takeover & Auto-VJ "Jump the Line" Staging

- **Instant Manual Takeover**: Interacting with the master crossfader via mouse or MIDI CC disarms Auto-VJ and temporarily mutes conflicting crossfade CVs for 1:1 physical control.
- **CV Auto-Centering**: Unmuting any CV modulator on `Mixer/crossfade` automatically centers `crossfade.baseValue` to `0.0`.
- **Standby Deck Staging ("Jump the Line")**: Manually loading a preset into the inactive deck while Auto-VJ is running stages it for the next automated crossfade without overwriting the queue sequence.

#### 9. Sticky Oscilloscope, Cell Muting, GUI Scaling & Engine Polish

- **Sticky Oscilloscope in Cell Config**: Parameter title, CV tab switcher, and live oscilloscope remain pinned at the top while modulator controls scroll independently below.
- **Cell Muting System**: Toggle any CV modulation cell (LFO, Audio, Trigger, MIDI) from sending values to live parameters (`Value`) while keeping the oscilloscope live and animated. Middle-clicking any active or muted cell toggles its mute state.
- **Percentage-Based GUI Scaling (75%–200%)**: Continuous scaling slider in Settings with 5% increments, `Ctrl+-` / `Ctrl+=` hotkeys, and automatic OS content-scale factor detection on launch.
- **Resizable Settings Panel with Automatic Persistence**: Settings modal is now freely resizable with dynamic sidebar and content resizing, bounded by display constraints and automatically saved to `lsd-settings.properties`.
- **Pitch Black Backgrounds**: Enforced solid opaque black OpenGL clear color across GUI mode, clean mode (`f`), and preview monitors.

#### 10. High-Performance Live Video Recording & Deterministic Offline Studio

- **Live Audio Recording & Muxing**: Real-time audio stream capture from `AudioEngine` backed by a zero-allocation pre-allocated block pool on the real-time audio thread. Background worker writes temporary 16-bit PCM WAV audio and losslessly remuxes it with FFmpeg (`-c:v copy -c:a aac -b:a 320k -shortest`) upon stopping. Configurable toggle in Settings.
- **Deterministic Time Virtualization (`TimeSource`)**: Centralized simulation clock eliminating audio/visual desync across all shaders (`uTime`), `CVRegistry` evaluators, `DynamicSpiral`, and `Mixer` during offline rendering.
- **Hardware Encoder Prioritization & Probing**: Probes and prioritizes GPU hardware encoders (`h264_nvenc`, `h264_qsv`) with automatic software fallback (`libx264`, `libx265`, `prores_ks`).
- **Direct Memory Copy & Zero-Allocation Pipelines**: Replaced line-by-line CPU vertical flips with direct `MemoryUtil.memCopy` block transfers and FFmpeg `-vf vflip`. Reused persistent 64KB chunk buffers in `FFmpegProcessPipe`, eliminating 4 MB/s GC heap churn.
- **Live Recording HUD & Settings**: Pulsing red `REC MM:SS` tally badge overlaid on the Master preview monitor, `Ctrl+R` hotkey, and automatic OS standard Videos folder resolution (`~/Videos/liquid-lsd` or `~/Movies/liquid-lsd`).
- **Offline Studio Upgrades**: Integrated `ImGuiFileBrowser` for audio and destination selection, "Match Project Canvas" resolution option, preset/playlist snapshotting, detailed progress & ETA metrics, and multi-line FFmpeg error diagnostics.

---

### 📜 Full Commit History (v1.0.0-beta.26 → v1.0.0-beta.28)

- `ea862c6` Add Background Deck, Deck PV preview, 2x2 monitor matrix, and 4-column library layout
- `159db62` feat(source): add 4D Hyper-Slice raymarched visual source with H4 domain folding
- `b9a19fa` feat(source): add 4D Hyper-Mesh polychoron visual generator (600-cell & 120-cell)
- `4c2e487` fix(icosa-v3): Lock lighting to screen center in camera space
- `d088f80` fix: Prevent icosa-v3 unbounded growth at high Control X values
- `2182751` feat: Upgrade Control X to endless slerp for infinite stellations
- `0f31f62` feat: Add Icosahedron V3 CSG visual source
- `f150640` feat(shaders): implement analytic 32-stellation Du Val poset manifold for icosahedron
- `cea75ef` feat(visuals): add Icosahedron 32-Stellation 2D Du Val poset manifold visual source
- `a6bab1a` feat(modulation): add per-modulator audio envelope followers, UI sections, and dual-trace oscilloscope
- `304ba02` feat(icosa_dodeca): add geometric Wythoff vertex truncation and edge cantellation to Support H
- `06dc5d2` feat(shaders): implement continuous stellation plane tilting for icosa_dodeca
- `f8f6aaf` docs: document H3 Coxeter IFS engine and polyhedral morphs for icosa_dodeca
- `b679aac` fix(ui): enforce solid black background on all 4 preview monitor screens
- `e14ab61` feat(shaders): add smootherstep C2 morph transitions, cross-faded symmetry sectors, and raymarch optimizations for icosa_dodeca
- `1e9565d` feat(shaders): unify icosa_dodeca into continuous 4-stage cyclic morph and add finite cone bounds
- `56e99d7` feat(shaders): improve icosa_dodeca stellation cones, symmetry sectors, and raymarch stepping
- `60ae86c` docs: update release notes and documentation for v1.0.0-beta.27
- `fc1ad3f` Remove GPU-heavy visual sources (clifford_torus, kifs, mandelbox, pseudo_kleinian, mandelbulb)
- `2a53f99` feat(library): add Delete key shortcut, permanent deletion warning, and reference cleanup to playqueue and playlist
- `bc8a5b8` refactor(ui): extract BrowserDeckButtons helper and simplify preset grid UI buttons
- `59bcbb0` feat(library): consolidate library browser and playlist editor with unified subtabs and asset actions
- `193e9b9` feat(presets): integrate manual deck preset loading with Auto-VJ and PlayQueue line-jumping
- `7e819dd` Add Master Mixer momentary controls for playlist navigation and randomization
- `242c426` feat(mixer): improve crossfader manual takeover, CV modulation, and Auto-VJ transition behavior
- `476610d` chore(logging): set default log level to warn
- `3fe8258` feat(ui): make oscilloscope sticky at top of Cell Config panel with scrollable modulators
- `835eb7d` fix(ui): increase analytical step resolution on LFO oscilloscope to eliminate pixelation
- `7c5c7b6` feat(ui): improve LFO oscilloscope rendering, BPM-aware timebase, and seam alignment
- `dcb6243` refactor(ui): place modulator titles on top row above controls in cell config
- `2537cad` refactor(ui): refine LFO section layout and preserve modulator bypass state on depth changes
- `d835368` fix(ui): percentage-based GUI scaling, font atlas GC stability, and window resize bounds
- `9e260d7` feat(ui): add 'b' keybinding to toggle background video output
- `7f0f915` feat(ui): add cell mute toggle with live oscilloscope preview and ASCII window title
- `b40b399` feat(audio): overhaul multi-band autocorrelation beat engine and add benchmark test suite
- `e768ed0` refactor(audio, ui): update beat detection settings and UI panel controls

---

## Version 1.0.0-beta.26

> [!NOTE]
> **Release 1.0.0-beta.26** is a major cumulative milestone rolling up all features, architectural enhancements, performance optimizations, and UI overhauls since `v1.0.0-beta.21`.
> Highlights include user-configurable render resolutions with multi-aspect ratio output scaling (16:9, 4:3, 1:1, custom), live zero-downtime FBO resizing, a multi-scale calibrated oscilloscope engine with real-time future projection, deterministic frame-synced LFOs, a spectral-flux beat detection flywheel overhaul, UI architecture modularization with live theme color tuning, industry-standard "Preset" terminology with unified `library/` storage, dedicated `SavePresetModal` with overwrite safety, unipolar modulation and dial calibrations, resolution-independent UI scaling, and comprehensive zero-allocation render loop hot-path optimizations.

---

### Key Highlights (Rollup since v1.0.0-beta.21)

#### 1. Configurable Render Resolution & Multi-Aspect Output Pipeline

- **Resolution Presets & Custom Dimensions**: Added user-configurable internal rendering resolutions under **Settings -> Video & Display**, featuring standard 16:9 presets (1080p, 720p, 540p, 1440p, 4K UHD), 4:3 presets (UXGA 1600x1200, XGA 1024x768, SVGA 800x600), 1:1 square presets (1080x1080, 800x800, 600x600), and custom dimensions ($128 \times 128$ to $7680 \times 4320$).
- **Live Zero-Downtime Pipeline Resizing**: Decks and Mixer support dynamic reallocation (`Deck.resize` and `Mixer.resize`) on the main OpenGL thread without interrupting playback or losing preset state.
- **GPU Performance Scaling**: Downscaling from 1080p to 720p or 540p reduces raymarching pixel evaluation by 55%–75%, allowing heavy distance-field raymarchers (KIFS, Mandelbulb, Pseudo-Kleinian) to run at solid 60 FPS on laptops and integrated GPUs.
- **Display Output Scaling Modes (`ViewportHelper`)**:
  - **Fit (Letterbox / Pillarbox)**: Preserves exact aspect ratio of the render target with border bars when outputting to mismatched monitor aspect ratios.
  - **Fill (Crop)**: Centers and crops edges to fill the display with no black bars.
  - **Stretch**: Stretches the image to fill the output display.
- **Aspect-Aware UI Previews & Splitter Clamping**: `MixerMonitorLayoutCalculator` and `MixerMonitorPanel` dynamically scale Deck A, Deck B, Deck PV, and Master preview heights to match the active render aspect ratio. Splitter positioning clamps Column 3 width to the maximum preview capacity given window height, eliminating letterbox dead space.
- **Opt-In Secondary Video Output & Menu Control**: Secondary window is strictly opt-in on single-monitor setups (no unsolicited popups on startup). Added an **"Output Window"** item in the main menu bar to toggle external/secondary output window, and removed the Spacebar hotkey to prevent accidental triggers.

#### 2. UI Architecture Modularization & Interactive Developer Tools

- **`DeckPresetController` Extraction**: Decoupled deck preset file actions (Save, Save As, Rename, Duplicate, Overwrite, Eject, Reset), file dialog handling, and save status notifications from `UIManager` into a dedicated controller class.
- **`UIThemeStyler` Extraction**: Extracted dynamic ImGui theme application, custom color palette mapping (`BORING`, `DARK_SOLARIZED`, `LIGHT_SOLARIZED`, `DARK_LUNARIZED`, `LIGHT_LUNARIZED`, `NEON`), window background alpha/video blending, neon gradient rendering, and proportional `ImGuiStyle` size scaling.
- **`SplitterManager` Extraction**: Extracted multi-column workspace splitter state, drag interaction tracking, cursor hinting (`ResizeEW`/`ResizeNS`), double-click reset positions, and window-level draw-list divider rendering from `UIManager`, ensuring splitters remain properly layered below floating windows, dialogs, and tools.
- **Library Panel Renaming**: Refactored and renamed the 3-column Preset, Playlist, and Play Queue dock from "Asset Browser" to **"Library"**, standardizing terminology with DJ/VJ performance software, and modernizing `LibraryPanel`, `LibraryMode` (`FULL`, `HALF`, `HIDE`), and backward-compatible settings persistence.
- **Live Theme `ColorTunerPanel`**: Added interactive non-modal color tuner accessible via the top menu bar ("Color"), allowing real-time assignment of palette swatches across all 17 themed ImGui elements with live updates and instant Kotlin code generation for clipboard export. Canonical HEX palettes enforced for Solarized and Lunarized themes. Fixed close button synchronization, eliminated duplicate outer window scrollbars, widened the Alpha column to prevent text clipping, and ensured proper z-order above workspace divider lines.
- **Background Video Keybinding (`B`)**: Added a global hotkey `B` to instantly toggle master video background rendering behind the semi-transparent UI with synchronized settings persistence.
- **Linux Window Title & X11 Class Hints**: Replaced multi-byte Unicode em-dash (`—`) in GLFW window title with standard ASCII hyphen (`-`) and explicitly configured `GLFW_X11_CLASS_NAME` ("Liquid LSD") and `GLFW_X11_INSTANCE_NAME` ("liquid-lsd"), preventing mojibake/corrupted garbage characters in Linux alt-tab task switchers.

#### 3. Multi-Scale Calibrated Oscilloscopes & Signal Visualization

- **Dynamic FPS Sync for Frame-Based LFOs**: Fixed frame-synced LFO lookahead projection, auto-timebase calculations, and history sampling across `Evaluators`, `ModulatableParameter`, and `OscilloscopeDrawer` to dynamically bind to the configured target frame rate (`CVRegistry.getTargetFps()`) rather than assuming 60 FPS. At 30 FPS, setting an LFO to 30 frames now correctly oscillates at exactly $1.0\text{ Hz}$ ($1.0\text{s}$ period).
- **Multi-Scale Calibrated Timebases**: Oscilloscopes support selectable physical time windows spanning from fast transients to circadian cycles: `1s` ($250\text{ms/div}$), `10s` ($2.0\text{s/div}$), `100s` ($20\text{s/div}$), `15m` ($3\text{m/div}$), `2.5h` ($30\text{m/div}$), and `24h` ($4\text{h/div}$). Time range dropdown combo widths and spacing dynamically autoscale with font size.
- **Real-Time Lookahead Future Projection**: Real-time forward waveform projection for deterministic LFO modulators rendered in front of the `NOW` playhead.
- **Decoupled Per-Scope Timebases**: Timebase selections across individual CV scopes (LFO, Audio, Trigger, MIDI) and the Value parameter oscilloscope are completely decoupled. Changing the time window on one tab no longer changes the scale of other tabs.
- **Auto Scale Exclusively for LFO**: The `Auto` timebase option (which dynamically fits $1\text{–}2$ periods of the active waveform) is offered exclusively on the **LFO** oscilloscope. **Audio**, **Trigger**, **MIDI**, and **Value** default to **`10s`** (displaying the full recorded history window) and provide fixed physical options (`1s` to `24h`).
  
  #### 4. Cell-Level Mute & Live Oscilloscope Preview
- **Cell Mute / Preview System**: Users can mute any CV modulation cell (LFO, Audio, Trigger, MIDI) from sending values to live parameters (`Value`) while keeping the Oscilloscope 100% live and animated in Cell Config for real-time waveform previewing.
- **Preset Grid Visual Indicators**: Muted cells in the Preset Grid drop knob arc/meter opacity to **35%** and display a centered sans-serif **'M'** inside the knob.
- **Master Scope Mute Toggle**: Cell Config features a master `[ LIVE ]` / `[ MUTED ]` toggle button in the top-right corner of the Oscilloscope header bar, with an amber `[SCOPE LIVE — OUTPUT MUTED FROM VALUE]` watermark when muted.
- **Middle-Click Shortcuts**: Middle-clicking any active or muted cell in the Preset Grid immediately toggles its Mute/Unmute state without clearing modulators or losing dial settings.
- **Brightened Grid Ticks & Dynamic Timestamp Badges**: High-contrast, crisp grid division ticks and legible timestamp numbers with dynamic height positioning for clear readability across all themes and zoom levels (`-250ms`, `-2s`, `NOW`, `+2s`, `+15m`, `+4h`, etc.).
- **Unified Oscilloscope Architecture**: Consolidated all oscilloscope rendering into `OscilloscopeDrawer`, eliminating duplicated drawing code across UI panels.

#### 4. Real-Time Multi-Band Beat Detection Engine & Automated Benchmark Testing

- **Multi-Band Cross-Spectral Autocorrelation Engine (`BeatDetectionMode.AUTOCORRELATION`)**: Upgraded beat detection to maintain zero-allocation primitive FloatArray ring buffers (`bassHistory`, `midHistory`, `highHistory`, 2048 blocks). Computes cross-spectral correlation over candidate lags (40–200 BPM) without allocating memory on the JACK/audio callback thread.
- **Harmonic Comb Unwrapping**: Implemented harmonic comb unwrapping to evaluate half-lags ($d/2$). Eliminates half-tempo (60 BPM) and double-tempo (200 BPM) octave traps by verifying fundamental beat periods, ensuring 120, 128, 140, and 100 BPM tracks lock precisely to their true fundamental tempo.
- **Sub-Block Parabolic Lag Interpolation**: Fits a 2nd-order parabola over lag correlation points $(d-1, d, d+1)$ to extract sub-block fractional lag offsets $\delta$, achieving floating-point precision within $\pm 0.1$ BPM.
- **Gaussian Tempo Weighting**: Applies a subtle Gaussian curve centered at 120 BPM ($\sigma = 80$ BPM) to bias candidate selection towards natural musical tempos.
- **Automated Synthetic Audio Benchmark Test Suite (`BeatDetectorBenchmarkTest.kt`)**: Built an automated audio benchmark test suite that generates multi-band synthetic audio for 120 BPM House, 128 BPM EDM, 140 BPM Dubstep, 100 BPM Hip-Hop, and 4-beat silent drum breakdowns. Automatically validates convergence time (< 3.0s), lock accuracy (< 1.5 BPM error), and flywheel momentum retention.
- **UI Analysis Length Slider**: Restored the `Analysis Window Length` slider in `AudioEnginePanel` when `AUTOCORRELATION` mode is active, allowing live tuning of the correlation history window from 1.0 to 10.0 seconds.

#### 5. Deterministic Frame-Synced LFOs (LFO 1 & LFO 2)

- **Frame Frequency Mode**: Added a third frequency clocking mode, `FRAME`, alongside `TIME` and `BEAT` in the unified LFO generator. Frame-synced LFOs oscillate deterministically based on elapsed render frame count (1 to 10,000 integer frames), enabling artifact-free feedback buffer harmonization, per-frame stroboscopic/flicker effects, sample-and-hold per-frame noise, and deterministic video frame captures.
- **Integer-Locked Sliders & Dual Readouts**: Both primary carrier (LFO 1) and modulator (LFO 2) support independent frame sync with integer-locked range sliders and duration readouts (e.g. `120 frames (2.00s)`).

#### 6. Modulation Architecture & Calibration

- **Unipolar CV Modulation & Zero Silence Baseline**: Fixed modulation evaluation formulas for unipolar sources (Audio RMS, Bass/Mid/High frequency bands, Triggers, and MIDI CC). Silence ($cv = 0.0$) remains strictly at $0.0$ without introducing artificial DC offset shifts when increasing Depth, restoring full modulation dynamic range.
- **CV Modulation "Depth" Terminology Standardization**: Standardized the term for the value assigned to a CV modulator from "amplitude" (and legacy JSON "weight") to **"Depth"** across domain models (`CvModulator.depth`, `depthMin`, `depthMax`, `randomizeDepth`), evaluation logic, UI controls (Cell Config Depth range slider, LFO 2 AM Depth mode/tooltips), and documentation.
- **Preset Grid Cell Dial Calibration**: Calibrated knob meters in `PresetGridRenderer` for unipolar audio, trigger, and MIDI cells so dial needles and indicator arcs accurately reflect the true parameter modulation range ($0.0 \dots 1.0$) rather than resting at $0.5$ on silence.
- **Consolidated Modulation Evaluator**: Centralized parameter modulation evaluation into `Evaluators.kt` (`evaluateModulatedValue`), eliminating duplicate evaluation routines across UI panels.

#### 7. Preset & Library Architecture Modernization ("Patch" → "Preset")

- **Industry Standard 'Preset' Terminology Refactor**: Refactored visual parameter snapshots across the codebase from 'Patch' to 'Preset' (`PresetManager`, `DeckPresetDto`, `GlobalPresetDto`, `PresetGridPanel`, `PresetGridState`, `PresetGridRenderer`, `PresetGridTabs`, `PresetGridUndo`).
- **Unified `library/` User Storage Directory**: Standardized user data root to `library/` (`library/presets/*.lsd`, `library/midi/*.json`, `library/playlists/*.lsdplay`, `library/sources/`, `library/last_session.json`).
- **Codebase Streamlining & Legacy Code Removal**: Removed legacy backwards compatibility shims across data models, serialization, session management, and UI browsers. Standardized `ModulatorDto` serialization to directly serialize `depth`, `depthMin`, `depthMax`, and `randomizeDepth` without legacy `@SerialName("weight")` aliases. Removed obsolete `GlobalPresetDto` and legacy conversion methods.

#### 8. UI & UX Refinements, SavePresetModal & Responsive Layouts

- **Dedicated SavePresetModal**: Replaced the floating `DeckPresetBrowser` popup with a clean, dedicated `SavePresetModal` for entering preset names and comma-separated tags directly when selecting "Save As..." (or "Save" on an untitled deck). Dynamic action titles (`Save Preset As`, `Rename / Edit Preset Tags`, `Duplicate Preset`) render cleanly in the ImGui modal title bar without redundant body text.
- **Universal Overwrite Safety**: Added file existence detection and overwrite protection across all preset modal flows (`Save As...`, `Rename`, `Duplicate`). `Save As...` defaults to `${activeName}_copy` to create new files by default, and typing an existing file name prompts with an explicit amber warning badge and `[ Overwrite ]` confirmation button.
- **Library New Preset Creation**: Added a dedicated `[Create new preset...]` row with `[ A ] [ B ] [ BG ] [ PV ]` buttons positioned above the preset list in the Library. Clicking a deck button ejects/resets the deck and switches Preset Grid focus directly to that deck.
- **Mixer Monitor Left-Click Deck Focus**: Left-clicking any deck preview monitor (`Deck A`, `Deck B`, `Deck BG`, or `Deck PV`) in the Mixer Monitor panel directly focuses the Preset Grid to that deck (`activeTopTab`).
- **Redesigned Deck Monitor Toolbar**: Combined the top preset header row and bottom patch label across Deck A, Deck B, Deck BG, and Deck PV preview monitors into a unified interactive toolbar (`[Save] [Eject] [Preset Bar]`) with corner letter badges (`A`, `B`, `BG`, `PV`).
- **Mixer Monitor Vertical Scrollbar Elimination**: Overhauled `MixerMonitorLayoutCalculator` to comprehensively calculate non-aspect vertical chrome, eliminating unwanted vertical scrollbars while preserving aspect preview monitors.
- **Robust Panel Splitters**: Replaced dummy ImGui splitter windows with direct mouse hit-testing and foreground draw list rendering, ensuring resize cursors and drag interactions remain active at all times.
- **Preset Grid Knob Indicators**: Refined circular knob meters across `MONOPOLAR`, `BIPOLAR`, `ENDLESS`, and `DISCRETE` modes in `PresetGridRenderer` by replacing the solid value circle with an elongated inward radial needle pointer (`trackRadius * 0.3f`), boosting background track arc/circle brightness, and adding a vibrant yellow cross-track tick mark.
- **Resolution-Independent Grid Scaling**: Replaced hardcoded cell pixel dimensions with a dynamic `GridMetrics` geometry token system that scales Preset Grid cells and circular readout knobs automatically with global UI font size (`baseSize`). Added a **"Grid Knob Cell Scale"** setting slider (0.70x to 2.00x) in **Settings -> Appearance**.
- **Comprehensive Font Autoscaling**: Dynamic font-scaling across settings modals, empty deck launchpads, cell config tab rows, range sliders, and Lucide icons.
- **Library Live Auto-Refresh**: Real-time filesystem change monitoring across `LibraryPanel` and `ImGuiFileBrowser`, removing redundant manual Refresh buttons and automatically updating file listings when on-disk files change.
- **Playlist Menu Bar Streamlining**: Removed action buttons from the playlist editor menu bar in Library and consolidated them into the right-click context menu.

#### 9. Performance & Zero-Allocation Hot-Path Optimizations

- **Oscilloscope & Modulation GC Optimization**: Replaced per-call `HashSet` instantiation in `isCvSourceBipolar` with a zero-allocation branch, eliminating over $180{,}000$ GC object allocations per second on the 60 FPS render path during anti-aliased waveform rendering.
- **Render-Loop Hot-Path Cleanups**: Preallocated immutable timebase lists/arrays in `OscilloscopeDrawer` and reused persistent `ImInt` wrappers across oscilloscope timebase combos and `ModulatorHeaderRow` operator selectors, ensuring strict ImGui zero-allocation draw rules.
- **Eliminated Dead Multi-Trace History Loops**: Removed unused per-frame modulator history evaluation loops and unreferenced `modulatorHistories` buffers in `ValueParamSection` and `CellConfigPanel`.
- **Preserved Scope Timebases Across Clones**: Fixed `ModulatableParameter.clone()` to preserve custom per-scope timebase zoom settings across preset cloning, undo/redo snapshots, and deck preset duplication.
- **30 FPS User Setting Frame Rate Limiting**: Restored two-stage CPU-efficient sleep frame rate pacing in the main render loop bound to `session.uiTheme.maxFps`, properly enforcing the 30 FPS power-saver limit when enabled.
- **Comprehensive Unit Testing**: Added unit tests for target-FPS frame-synced LFO calculations, scope timebase cloning, source classification helpers, UITheme settings round-trips, ViewportHelper scaling modes, and CV history buffer interpolations.

---

### 📜 Cumulative Commit History (v1.0.0-beta.21 → v1.0.0-beta.26)

- `c23d180` Fix Deck A and Deck B preview monitor sizing to match active render aspect ratio
- `ec1ad47` Limit Mixer/Monitor panel width to maximum preview capacity
- `1037b37` Add configurable render resolution, display scaling modes, and opt-in output window
- `3ec54e0` Refactor ParameterResolver to use ParameterOwner interface and update UIThemeStyler
- `6716812` Restore NoteEditorModal reference in ARCHITECTURE.md ui tree
- `c27f569` Refactor 3-column dock panel and settings from Asset Browser to Library
- `7c491ed` fix(ui): enforce exact canonical HEX codes and all 16 swatches for Lunarized
- `8457753` fix(ui): enforce exact canonical HEX codes and all 16 swatches for Solarized
- `09bdd9d` Enhance ColorTunerPanel swatch dropdowns and layout sizing
- `e431dc7` feat(ui): add live Theme ColorTunerPanel and update release notes for v1.0.0-beta.25
- `c0b2a3e` refactor(presets): remove legacy GlobalPresetDto and unused global preset methods
- `49e8b5c` refactor(ui): extract DeckPresetController from UIManager
- `fcaf0b8` refactor(ui): extract UIThemeStyler and SplitterManager from UIManager
- `8bf8f2a` fix: restore 30 FPS frame rate limiter in render loop
- `a67eb73` docs: add comprehensive beta 24 release notes rolling up changes since beta 21
- `bc02868` Refactor CV evaluators, consolidate oscilloscope rendering, and add tests
- `06acbc7` docs: finalize release notes with decoupled timebase and history details
- `1aa2e2d` feat(ui): default oscilloscope timebase to 10s for all non-LFO tabs
- `11fd129` feat(ui): restrict Auto timebase option exclusively to LFO oscilloscope
- `386c306` feat(ui): decouple oscilloscope timebase selection across individual CV tabs and Final
- `8d14cc8` fix(ui): ensure flat zero baseline outside recorded history on long timebases
- `71b44ad` feat(ui): update Final parameter oscilloscope to always display 100% true recorded history
- `914781f` docs: update RELEASE_NOTES.md with modulation, dial calibration, and oscilloscope envelope highlights
- `f178ab5` fix(ui): implement peak-detect anti-aliasing envelope on long-timebase LFO oscilloscopes
- `2929f94` fix(ui): isolate future lookahead projection strictly to deterministic LFO modulators
- `5114e29` fix(ui): fix preset grid cell knob display value mapping for unipolar audio/midi modulators
- `1ca993a` fix(audio): fix unipolar audio modulation formulas, physical timebase scaling, and zero-baseline bypass
- `c1ace45` fix(ui): eliminate oscilloscope quivering, fix playhead signal binding, and preview live audio CV
- `9ee45f9` feat(ui): implement hybrid oscilloscope with true history replay and context-aware playheads
- `4d6e5c5` fix(ui): autoscale oscilloscope timebase combo and brighten grid ticks and numbers
- `5d226a3` refactor(ui): lock oscilloscope to symmetrical centered playhead and clarify lookback/lookahead tooltips
- `d10602e` fix(ui): calibrate past history rendering to selected timebase
- `33d8ded` feat(ui): add multi-scale calibrated oscilloscopes with future projection and interactive playhead
- `aa8ef1f` Refine preset grid knob meters with needle pointers, yellow base ticks, and brighter tracks
- `cb3ab78` Focus Preset Grid deck tab on monitor left-click
- `aac3f81` fix(ui): dynamic font-scaling for CellConfig sliders, dropdowns, and layouts
- `29ab8b9` fix(ui): vertically center Lucide icons with scaled glyphOffset in UITheme
- `c00e3b2` Fix font scaling for empty deck launchpad and Cell Config tab row buttons
- `a0e1a06` feat(ui): add create new preset row to asset browser
- `e987585` ui: replace DeckPresetBrowser with SavePresetModal and context-aware metadata flows
- `665857b` Fix LFO 1 time slider visibility on Frame sync and compute frame durations using configured maxFps
- `d2ab1f3` feat(audio): overhaul real-time beat detection and phase flywheel tracking
- `dbd7fac` feat(core): remove backwards compatibility code and implement frame-synced LFOs
- `954b32a` fix(ui): improve mixer monitor layout and fix panel splitters
- `b0294c3` refactor(cv): rename modulation amplitude to Depth across domain models, UI, and docs
- `861f323` docs(architecture): update architecture docs to reflect zero-alloc volatile beat clock & library paths
- `a0ef6bc` fix(settings): isolate test settings file cleanup to prevent resetting user column visibility on startup
- `a772b8f` feat(ui): add resolution-independent GridMetrics scaling and adjustable Grid Knob Cell Scale setting
- `47450db` fix(ui): position combined preset bar above Deck A/B/C preview monitors
- `8ebbeb6` refactor: complete patch to preset terminology migration across UI and docs
- `7c71375` refactor: replace internal patch references in audio, notes, and docs
- `b244c90` refactor: rename Patch terminology to Preset and presets/ directory to library/
- `fe9f57b` feat(ui): redesign Deck preview monitor preset bottom bar
- `9c0ef28` refactor(ui): remove playlist menu bar buttons and move actions to right-click menu
- `413b0e6` fix(ui): increase Settings modal content height by 25% to prevent scrolling on Patch Grid settings
- `d481bf8` fix(ui): eliminate modal scrollbars and auto-fit Settings window around content and Close button
- `4e5d97a` fix(ui): reduce Settings modal default height and lock Close button inside visible bounds
- `10b0c90` feat(ui): auto-resize Settings modal window, sidebar, and button heights dynamically with font scale
- `c989432` feat(ui): overlay letter badges on monitor lower-left corners and remove redundant text headers
- `009ab16` feat(ui): implement real-time media browser auto-refresh and remove manual refresh buttons
