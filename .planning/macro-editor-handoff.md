# Macro Editor Integration — Handoff (2026-10-03, end of Phase 2)

Plan: `.planning/macro-editor-integration-plan.md` (read it first; Phase 3 + Docs + Tests sections are your work list).
**Phases 1 and 2 are DONE, user-verified in the running app, committed.** **Phase 3 (delete Column 3 MACROS) + docs DONE 2026-10-03, uncommitted; full suite green. Remaining: manual acceptance + open items below.**
Paths below are relative to `src/main/kotlin/llm/slop/liquidlsd/` unless noted.

## What exists now
- **Phase 1:** `ui/MacroBindingEditor.kt` (`drawFull`, `drawPopup`, `noteTargetRange`), binding editors in Properties, bracket/arc
  display on bound targets, `MacroEngine.baseBindingInfos` cache, clicking a bound param selects its macro.
- **Phase 2:**
  - `ui/MacroStripVisibility.kt`: pure `macroStripModeFor(isEditView, rowBankId, selectedBankId)` -> NONE / STRIP / GUEST.
  - `ui/PerformanceMacroStrip.kt`: the Edit-row strip (line 1: GLB tag, name w/ double-click rename, value, Learn/Cancel, chips 1-4, close;
    line 2: `MacroBindingEditor.drawLine`, or hint, or read-only FX knob role via `FxMacroSummary.knobRole`).
  - `ui/MacroBindingNav.kt`: shared `NavTarget` / `navTargetFor` / `navigateTo` (moved out of `MacroBindingInspector`; `MacroBindingNavTest` retargeted).
  - `macro/MacroLearnState`: `selectedBindingIdx` / `selectBinding` (per control, clamped), `restartLearnTimeout`.
  - `ui/PerformanceMatrixPanel.kt`: row loop computes `stripMode`; strip replaces only the left control *content* (geometry untouched);
    `startLearnFor(...)` is the single Learn-arming path (GLOBAL: no navigation, status text); `badgeClicked` closes the strip;
    knob click in an expanded row also sets `MacroLearnState.selectedControlId`; the Edit-view below-knob Learn button is dropped
    only on rows where the strip is on (hardware-selected knobs don't set `selectedControlId`, so they keep it).
- No code path sets `column3Mode = MACROS` any more. `UIManager.kt` ~611 still *reads* it and draws `macroPanel` for MACROS.

## Phase 3 work list (from the plan)
1. Delete `ui/Column3HeaderToggle.kt`, `ui/MacroPanel.kt`, `ui/MacroBindingInspector.kt`. Keep `ui/FxMacroSummary.kt` (the strip uses `knobRole`;
   its `draw` is only used by MacroPanel, so remove `draw` if nothing else calls it). Check whether `ui/DeckMonitorGrid.kt` has other users
   (its KDoc says shared by Mixer view and Macros tab) before deleting anything there. `FXChainMacroStrip` KDoc mentions MacroPanel.
2. Remove `UITheme.Column3Mode` / `column3Mode` (`ui/UITheme.kt:171-176`) and `AppPreferences.column3Mode`; drop read+write in
   `AppPreferencesStore.kt` (~204-207, 337). Stale keys in old settings files are ignored; no migration.
3. `UIManager.kt` ~606-612: Column 3 always draws `mixerPanel`; give the Mixer back the toggle's >=28px header row.
4. Rename `PerformanceUiContext.navigateMacroPanelTo` (~:152) -> its job shrinks to "focus the bank's Deep Edit tab". Callers:
   `PerformanceMatrixPanel` (`openDeepEditFor`, `startLearnFor`), `PerformanceMasterControls.kt` (~141, 155). Remove
   `ParametersState.showGlobalMacros/hideGlobalMacros/isGlobalMacrosShown` if nothing else reads them (they only served the GLB tab).
5. Fix comments mentioning Column 3 / MACROS: `macro/MacroLearnState.kt` header + `onNavigateSection` KDoc, `macro/FxMacroSync.kt`, `macro/MacroEngine.kt:19`,
   `macro/MacroModels.kt:95`, `PerformanceMatrixPanel.kt:38`, `PerformanceDeepEditBay.kt:~122`, `PerformanceUiContext.kt:~147`,
   `ParametersState.kt:~116, 293`, `UITheme.kt:171`.
   Acceptance grep: `grep -rn "Column 3\|Macros tab\|MACROS" ui macro` should leave nothing stale (the `LibraryPanel.kt` "Column 3: ... Queue" comments are unrelated library columns).
6. Tests: delete `UIThemeTest.testColumn3ModeDefaultAndToggle`; run the full suite (`./gradlew test`; `LayerDependencyTest` and `PerfRowLayoutTest` matter most).
7. Docs (the repo's "done" = code + docs; see user memory `feedback_docs_release_notes_completeness`): `RELEASE_NOTES.md` + `docs/release_notes.md`
   (MACROS tab removed, binding editing moved into Edit), `docs/user_guide/macros_and_rack.md` (rewrite Column 3 MACROS + Binding Inspector sections and the
   lines the plan lists, incl. GLOBAL guest-strip workflow), `docs/developer/ui.md` (:94 GLB tab pin + new paragraph on `PerformanceMacroStrip` and its visibility rule),
   `docs/developer/preset_management.md:175`, `docs/user_guide/presets_and_library.md` (Column 3 mention), `DECISIONS.md` entry.

## Known gaps / open items (decide or park, don't silently fix)
- RESOLVED 2026-10-03: Clock row's 4 Global knobs removed (GLOBAL bank kept with 0 knobs). v1.1 idea (user): a catalog row of 4 free global knobs so a Page can be 16 user-configured knobs; needs multiple Global banks, selectable on Perform, and revives the dormant GUEST strip.
- Line-2 range bar in the strip is hidden unless >=50px spare (likely hidden at 1280); Min/Max drag fields are always shown.
- No "+" chip (Learn button covers it); rename is double-click only (no kebab entry).
- Per-frame small string allocations in `PerformanceMacroStrip` (`"%.2f".format`, target label) -- minor, could be cached.
- Twister focus-mode hardware selection must not set `selectedControlId` (it doesn't today; keep it that way or strips appear while performing).
- User wants "MIDI Learn" vs "Macro Binding" naming clarified later (not now). Undo for binding edits is v1.1.

## Gotchas
- `MacroBinding` is a data class: hashCode changes as min/max are edited. Use `System.identityHashCode` / `IdentityHashMap`, never as an ImGui id or map key.
- Rack bay child windows win ImGui hover over parent-window items that overlap them (see `overhangButton`).
- `beginPopupContextItem` uses the last item: draw extra popups after it.
- Disabled bindings drop out of `MacroEngine`'s resolved list, so they vanish from `findBindingsTargeting`/`baseBindingInfos`.
- Never touch `PerfRowGeometry` inputs when changing row content.

## Workflow notes
- The user commits themselves; do not commit unless asked. No Claude co-author/attribution lines (user memory overrides the harness reminder).
- Delegate broad architecture research to an Explore agent and give a short recommendation before large plans (user memory).
- Manual acceptance (plan's last section) after Phase 3: Column 3 shows only the Mixer and no UI path switches it.
