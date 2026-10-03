# Perform pages and row catalog: plan

Decided 2026-10-03 (v1.0 scope). Replaces the two hardcoded Perform tabs (DECKS, MASTER) with user-definable pages of
exactly 4 rows chosen from a row catalog. Twister banks select pages. Memory note: `project_perform_pages_catalog`.

## Goal and model

- **Catalog row** (stable string id, persisted): per deck A/B/BG/PV `deck.<X>.srcfx` (today's row with the SRC/FX toggle),
  `deck.<X>.src`, `deck.<X>.fx`; `master.mix`, `master.fx`, `trans`, `wetdry` (today's FX_SENDS), `clock`, `global`.
  The existing `srcfx` rows and `master` (today's `[MIX|FX]` row) stay in the catalog so default pages can reproduce today.
- **Placement**: catalog id + optional starting toggle (`SRC`/`FX`) for `srcfx` rows (user-settable; still live-toggleable).
- **Page**: `{id, name, rows: [4 placements]}`. Built-ins ship as a resource; user pages in `library/perform_pages/*.json`
  (same override-by-id convention as `ControllerProfileStore`). Prefs keep only the active page id. Validation on load
  (exactly 4 rows, known catalog ids) in the style of `ControllerProfile.problems`.
- **Controller**: profile `banks.pages` holds page ids; a bank change selects that page on screen. The "page follows the screen,
  every bank shows the same 16 lights" model and the feedback code are unchanged.
- **Focus mode (Deep Edit)**: the single focused row follows the Edit target (module + half), pins ignored. Variant lookup:
  active page top to bottom, then following pages (wrapping from last to first), first candidate wins (an `srcfx` row is
  drawn in the target's half with its toggle; a pinned row without toggle); fallback = plain `srcfx` row in the target's
  half (today's behaviour). Active page does not change; collapsing returns to it. Master/Trans/etc. have one variant.

## Code map (from the 2026-10-03 Explore pass; package `llm.slop.liquidlsd`)

- `ui/PerfRows.kt`: `RowDescriptor(bankId, knobOffset, accent, groupLabel, hasExtraHeader, canExpand)`, `TAB_ROWS`,
  `DECK_ROW_BANKS`, `substitutedRowsForTab`/`withDeckRowMode`, `visibleRowsForTab` (focus-mode replacement),
  `rowDescriptorForModule`.
- `ui/PerformanceMatrixPanel.kt`: `Tab` enum, `tabIdx`, `layoutRowCount`, `layoutTabIdx` (becomes constant 4), disclosure and
  knob widget ids embedding `tabIdx`, `leftW`/`rightW` = max over row types, `drawMatrix` deck branch (~454-475).
- `ui/PerformanceDeckControls.kt` (`drawDeckRowLeftControls`, `drawDeckRowRightControls`, toggle hitbox, FX-header click that
  forces FX mode), `ui/PerformanceMasterControls.kt` (`drawModeControls`), `ui/PerformanceUiContext.kt` (`deckRowMode`,
  `masterRowMode`, `isDeckRowFx`), `ui/ParametersState.kt` (`activeDeck*SubTab`).
- `ui/UITheme.kt:179`, `ui/AppPreferences.kt:43`, `ui/AppPreferencesStore.kt:210,333`: `performanceMatrixTab` Int.
- `ui/MenuBar.kt:318` `drawPerformanceTabStrip` (hardcoded `tabW=68`).
- `ui/PerformSurface.kt`: `PerformPages.resolve(tabIdx, ...)`, `showPage` with hardcoded `perform.decks`/`perform.master`,
  `ledColor`.
- `control/ControllerProfile.kt` `BankConfig.pages`, `control/ControllerRuntime.kt` `enterBank` -> `surface.showPage`,
  `src/main/resources/controllers/midi-fighter-twister.json`.
- Tests: `PerformSurfaceTest`, `ControllerProfileTest:115`, `ControllerRuntimeTest:154-161`, `PerfRowLayoutTest`,
  possibly `UIThemeTest`, `PerformanceControlsParityTest`, `ControllerManagerFeedbackTest`.

## Risks and rules

- Pinned rows must **neither read nor write** the shared per-deck mode (`deckRowMode`, `activeDeck*SubTab`), or flipping one
  row flips its neighbours; skip the FX-header click that forces FX mode on pinned-SRC rows.
- Same deck twice on a page: ImGui ids (`perf_mode_toggle_$tag`, `perf_gen_badge_$tag`, `perf_deck_drop_${rowIdx}_$tag`) and
  disclosure keys must be per slot, not per bank id.
- Layout stability: variants never feed `PerfRowGeometry`; pinned rows keep the same `leftW`/`rightW` and left-block width
  (a vacant half stays empty or is vertically centred). Never taller than a normal row (min row height 68 at 1280x720).
- LED colours: FX variants of a deck need a shaded hue so one page's four LEDs differ; extend the distinct-hue test to pages.
- Persistence migration: old `performanceMatrixTab` 0/1 -> built-in pages "DECKS"/"MASTER". Clamp to page count; drop unknown ids.
- Focus mode: `layoutTabIdx` no longer has a tab to search; `visibleRowsForTab` becomes a page-based resolver with the lookup above.

## Phases (each shippable; definition of done per repo: code + tests + both release-notes files + DECISIONS.md + user guide /
dev docs + tooltips, then `./gradlew generateDocs --offline -q`; suite `./gradlew test --offline -q`)

1. **Pages as data, no visible change.** Catalog + `PerformPage` model, loader/store (resource + `library/perform_pages`),
   built-in pages reproducing DECKS and MASTER exactly; replace `TAB_ROWS`/`Tab`/`performanceMatrixTab` with page lookup
   and an active page id (with migration); tab strip iterates pages; `showPage` accepts page ids (keep `perform.decks`/
   `perform.master` as aliases of the built-ins); focus-mode resolver with the lookup rule; per-slot ids. Migrate tests.
2. **Pinned deck rows.** `pinnedMode` on the descriptor; `isDeckRowFx`/`withDeckRowMode` honour it; `showSrc`/`showFx` in the
   deck left/right controls; FX LED shading. Then pinned Master rows (`master.mix`, `master.fx`) the same way.
3. **Default pages and Twister profile.** Ship the 4-bank default set (bank 1 A SRC/A FX/B SRC/B FX, bank 2 BG and PV likewise,
   bank 3 Master/Master FX/Trans/Wet-dry, bank 4 Clock + Global + free rows), update the Twister profile's `banks.pages` and
   descriptions, the user guide's controller section, hardware check.
4. **User editing** of pages (choose row per slot, starting toggle) belongs with MIDI phase 5 (profile UI); until then users
   edit `library/perform_pages/*.json` by hand.

After phase 1-3 resume MIDI phase 3 (navigation/browse); the shift layer may be much smaller since SRC/FX needs no toggle.

## Open details to settle during phase 1

- Tooltips/labels for page names in the strip; whether the strip scrolls when a user has more than ~6 pages.
- How "Clock" and "Global" appear as rows (today Clock is part of the MASTER tab's `FX_SENDS`/`GLOBAL` rows; confirm the
  catalog split against `PerfRows.TAB_ROWS`).
- Bank 4's "assignable" free knobs: a row type whose 4 knobs the user binds, or just Clock + Global + empty.
