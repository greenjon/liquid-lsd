# Macro Editor Integration — Handoff (2026-10-03)

Plan: `.planning/macro-editor-integration-plan.md`. **Phase 1 is DONE, user-verified in the app, committed.**
**Next: Phase 2** (row binding strip, Edit view), then Phase 3 (delete Column 3 MACROS), then tests/docs.

## Done in Phase 1 (all under `src/main/kotlin/llm/slop/liquidlsd/`)
- `ui/MacroBindingEditor.kt` (new): `drawFull(session, control, binding, param|lo,hi, width[, logarithmic])` returns true on delete
  (caller removes binding + `MacroEngine.invalidate()`); `drawPopup`/`popupIdFor` (badge popup); `noteTargetRange(binding, lo, hi, log)`.
  Range bar = two draggable handles + live dot; log scale when target is logarithmic; 3-significant-digit rounding.
  `drawLine(...)` for Phase 2's one-line layout is NOT written yet.
- `ui/ValueParamSection.kt`, `ui/PropertiesPanel.kt`: "Click to inspect in Column 3" buttons replaced by header + editor per binding.
- `macro/MacroEngine.kt`: `findBindingInfos`, private `infoFor`, `bankKeyOfControl`, cached `baseBindingInfos(paramKey)`
  (cache replaced wholesale on `invalidate()`/rebuild). `MacroBindingInfo` gained `bankKey`.
- `ui/ParametersRenderer.kt`: macro range arcs on param grid value cells (selected knob bright + live dot, same-bank others dim).
- `ui/CustomRangeSlider.kt`: cyan `[ ]` brackets on bound property tracks; bound label click opens editor popup
  (also lfoMin/lfoMax/dcOffset/depth bound labels via `macroKey/macroModIdx/macroProp` params). `BeatDivisionSlider` reports its range only.
- `column3Mode = MACROS` removed from 5 sites; the **only remaining forcer is `PerformanceMatrixPanel.kt` Learn handler** (Phase 2 item 4).
- `ui/PerformanceMatrixPanel.kt`: Learn handler now calls `parametersState.openParams(moduleId)` (bay may be in Browse);
  Learn/Cancel use new `overhangButton` (rect hit-test) because the overhang sits under grid/bay child windows, which stole hover.
- Test added: `MacroEngineTest.testBaseBindingInfosCacheInvalidatesWithInvalidate`. Full suite passes.

## Gotchas learned
- `MacroBinding` is a **data class**: `hashCode()` changes as min/max are edited. Never use it as an ImGui ID or map key
  (use `System.identityHashCode` / `IdentityHashMap`).
- Editors must scale to the target's *real* limits, not the binding's min/max, or the bar rescales under the handle.
- `beginPopupContextItem` uses the last item: draw any extra popup *after* it.
- Right-click on a Perform knob = **MIDI learn** (not macro Learn). Macro Learn = the Learn button under the selected knob
  (left-click the knob first in Edit view). User wants clearer naming ("MIDI Learn" vs "Macro Binding") later — not now.
- Disabled bindings drop out of `MacroEngine`'s resolved list, so they vanish from `findBindingsTargeting`/`baseBindingInfos`.
- Rack bay child windows win ImGui hover over parent-window items that overlap them.

## Phase 2 suggested order
1. Pure pieces first: strip visibility rule `(view, row bank, selected control's bank) -> STRIP | GUEST | NONE` + tests
   (deck SRC, deck FX, Master MIX/FX, GLOBAL); `selectedBindingIdx` per control in `MacroLearnState` with clamp + test.
2. Move `navTargetFor`/`NavTarget` (still in `ui/MacroBindingInspector.kt`) to a shared helper; retarget `MacroBindingNavTest`.
3. `ui/PerformanceMacroStrip.kt` + `MacroBindingEditor.drawLine`; wire into the row loop in `PerformanceMatrixPanel`
   (swap content of the left control lines only — **never touch `PerfRowGeometry` inputs**; `PerfRowLayoutTest` must pass).
4. GLOBAL guest strip, overhang cleanup (drop Edit-view Learn overhang button), rewrite Learn tooltips (still mention "Macros tab").
Then Phase 3 + docs per the plan's checklist (both release-notes files, user guide, `docs/developer/ui.md`, DECISIONS.md;
`grep -rn "Column 3\|Macros tab"` under `ui/` must be empty).

## Repo workflow notes
- User commits themselves; do not add Claude co-author/attribution lines (user memory overrides the harness reminder).
- "Done" = code + both release-notes files + user_guide/dev docs + tooltips (see memory `feedback_docs_release_notes_completeness`).

## Phase 2 progress (2026-10-03)
- Steps 1-3 coded, full suite passes, **not yet checked in the running app**: `ui/MacroStripVisibility.kt`, `ui/MacroBindingNav.kt`
  (shared nav), `ui/PerformanceMacroStrip.kt`, `MacroBindingEditor.drawLine`, `FxMacroSummary.knobRole`,
  `MacroLearnState.selectedBindingIdx/selectBinding`; wired in `PerformanceMatrixPanel` row loop (`stripMode`, `badgeClicked`, `startLearnFor`).
- Perform-view Learn no longer forces `column3Mode = MACROS` (all forcers gone). Knob click in an expanded row now also sets `selectedControlId`.
- Known gaps: "+" chip omitted (Learn button covers it); line-2 range bar only shows if >=50px spare (likely hidden at 1280); rename popup
  not on kebab; no GLOBAL Learn path from Perform-view Clock row yet (step 4). Step 4 (overhang cleanup, tooltips) still open.
