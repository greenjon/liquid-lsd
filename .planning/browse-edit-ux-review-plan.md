# Browse / Library / Edit UX review: implementation plan (2026-10-21)

## Status (2026-10-21)
All waves done on main: 1C merged in `bed8f0c`, 1B in `6f18645`, 1A in `4a823d3`, Wave 2 in `ebb996f` (plus `25f5d08` Esc-closes-popup fix, `805dd15` chip font), Wave 3 docs after it.
Open:
- **Owner decision**: the Master MIX gear lands on MIX › TRANS, so Edit swaps the Master MIX row for the Transitions row (Edit rows follow the side tab), and LEVEL A/B/BG have no Edit rows (DECISIONS §7, Edit bay tabs entry).
- ~~Perform knob captions were truncated raw names~~: fixed, stored labels are the full readable name (`ParamLabels`), ellipsized at draw.
- Tours need updating for in-place binding and the renamed header targets: `tours/library.json` (chapter "pair": caption "Deck B's source and FX rows stay on top"; `btn_Parameters` / `btn_Back` are now `btnid_bay_params` / `btnid_bay_back`; the source-name click near line 30), `tours/fx_chains.json` (chapter "chains"), `tours/overview.json` (~line 127).
- ~~Ctrl+F while bound in place ended the binding~~: fixed, `LibraryPanel.show` now keeps an in-place binding.
- Not verified on Twister hardware, nor at 1280x720.

The owner approved every item of the 2026-10-21 review ("do it all"). Screens were captured with a tour script
(`--tour=... --tour-hidden --tour-fps=10 --tour-no-hashes`, then `ffmpeg -ss T -frames:v 1`), at 1920x1080, plus one
`--screenshot-ui --window=1280x720` shot of Perform.

Waves 1A/1B/1C run in parallel worktrees, then are merged. Wave 2 builds on 1C. Docs/release notes come last, in one pass, so
agents do NOT edit `RELEASE_NOTES.md`, `docs/release_notes.md`, `docs/user_guide/*`, `DECISIONS.md`: they report bullets instead.

## Wave 1A: Library list polish (`ui/browser/*` rows, tree, popups, queues)
1. Stock-item `Icons.SQUARE` reads as a checkbox: drop it (or a small muted "stock" tag), keep the saved-preset marker distinct.
2. Row ⋮ button draws an empty frame on every row: draw it only on hover / selected / menu open (keep its hit area).
3. Loaded-marks `×n` chip is white on light grey and hides decks: legible contrast; prefer coloured per-deck chips, fall back to
   a coloured count chip only when even compact chips cannot fit.
4. Name column (fixed third) overlaps the info column at 1280 wide and for long saved transition names: ellipsize to the column, full name in hover.
   Prettify snake_case saved names for display (`hyperspace_crash_zoom` -> `Hyperspace crash zoom`), file names unchanged.
5. Heavy-GPU ⚠ badge reads as an error: different glyph + tooltip explaining it.
6. The two "+" buttons (tree header, list header): tooltips/labels saying what they make. ⋮ on the list header: tooltip.
7. Tree: real fold arrows instead of "-"/"+" text, Playlists node shows a count (or none when empty, consistently), counts mean items.
8. Queues tab: empty-state hint per column ("Empty. Drag items here, or select one and press Q"), tooltips on transport buttons,
   consistent button sets; controller step order matches screen order (`LibraryNavigation.kt:69-71` vs `QueuesPane.kt:24-30`).
9. FX/Transition rows open their context menu on right-click too. Unify menu wording across kinds ("Rename...", "Delete...", "Add to 'X'").
   Reveal in File Manager for all kinds with files.
10. Bound (pair view) context menu leads with "Apply to <target label>"; the Load-to-deck items stay below a separator.
11. Keyboard: Enter = the row's default action on every tab (bound: apply; unbound: same as double-click). Use the ShortcutManager
    `library.delete_asset` binding instead of hard-coded Delete/Backspace. Type-to-search: printable key while the list is focused
    (and no text input active) jumps into the search box with that character.
12. Alt+click a Sources row previews it on PV (same path as "Preview on Deck PV").
13. "Recent" folder in the tree for Sources/FX/Transitions: last ~20 loaded/applied items, in memory + persisted with prefs if cheap.
14. Modals (Rename, Delete, New Playlist, Export): autofocus the text input, Enter = confirm, Esc = cancel. Dirty-deck modal: Enter = default, button widths match.
15. Deleting an FX or Transition file also removes it from queues and playlists (Sources already do, `BrowserPopupHandler.kt:268-293`).
16. Dragging an external source carries a payload decks accept (`PresetListPanel.kt:188-189`).

## Wave 1B: Edit view (`PerformanceDeepEditBay`, `ParametersTabs`, `ParametersRenderer`, `PropertiesPanel`)
1. Side-rail tabs clipped at the window's left edge ("CTRL" cut off).
2. Properties pane is empty until a click: pre-select a parameter (first knob-bound, else first) when a module opens with no selection.
3. Parameter labels in the grid show raw ISF names (`CellScale`, `ColorMethod`): show humanized labels (the ISF LABEL if present,
   else split camelCase/snake_case), consistent with the list and knob captions. Keys/paths unchanged.
4. Master Edit lands on CTRL (queue prev/next, tap tempo): from the Master MIX row, land on MIX instead.
5. "Next up" queue line: only on SRC modules, so SRC<->FX edit jumps ~35 px. Coordinate with 1C (the header line absorbs it):
   1B leaves `drawQueueRow` alone; 1C moves it.

## Wave 1C: view switching (`BrowserDock` header/chip, `PerformanceBrowseBay`, `ParametersState`, `BackNavigation`, `NavigationSurface`)
1. One header line, same place in pair view and Edit: `[Back] [Browse | Params]  ● <target chip> [row buttons]` then the tabs
   (pair view) or the editor (Edit), with the next-up queue text right-aligned in it. Edit gets a visible Back. Browse|Params toggles
   between the pair view and Edit for the same pair/half. Removes the separate Back/Parameters line and the chip's duplicate ✕
   (or keep ✕ with a correct tooltip). Geometry identical in both, and for SRC/FX.
2. Master pair with nothing touched: bind the Master FX chain instead of "Nothing to browse".
3. `drawForPair` early returns must call `BrowserPane.noteHosting(null)` (stale target bug).
4. `BrowserDock.syncTab` must not yank the tab on every `dockSelectionEpoch` bump from knob touches: only when the target itself changes.
5. Dirty-deck modal: ask once per bound browse session (same deck), then apply later clicks with the chosen answer; undo still works.
6. Double-click while bound applies and returns to Perform: say so in a hint/tooltip (keep behaviour).
7. Backlog item 9, option B: Esc/back from a FULL Library (or Ctrl+F) opened from Edit or the pair view returns there.
8. Controller in pair view: change tab (side 2) and enqueue (shift + knob 16), like the Library.
9. Stale texts: chip ✕ tooltip, Master "M" badge tooltip (`MixerPanel.kt:114-118`; make it enter the Master pair like deck badges),
   `PerformanceDeepEditBay.kt:192` tooltip, KDoc "Multi mode" leftovers, `DeckControlPanel.openDeepEdit` name.
10. Applied-row tint also for a saved preset on the deck, a saved chain/effect on the slot, a saved transition
    (`PerformanceBrowseBay.kt:88-94,158-161,189`).

## Wave 2: target-as-selection (after 1C is merged)
Clicking a row's slot (source name, chain name, FX slot, transition name) in Perform binds the Perform HALF dock in place, with the
chip in the dock header, no layout change; Esc/✕ unbinds. The pair view is only used when the target's rows are not on the current
page. Single-click applies while bound, as in the pair view.

## Wave 3: docs
Release notes (both files), `docs/user_guide/presets_and_library.md` drift (listed in the review), performance docs, DECISIONS.md
sections in place, ARCHITECTURE.md one-liners, `.planning/pair-focus-view-plan.md` status, backlog item 9 closed.
