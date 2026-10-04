# Handoff: unified BrowserPane, promotion and cleanup (written 2026-10-04)

You are a fresh agent. Read, in this order: `.planning/unified-browser-pane-plan.md` (decisions, measurements, progress), `.planning/unified-browser-step4-handoff.md` (project rules and the previous step's context), then this file. Project rules: Kotlin + Dear ImGui desktop VJ app; "done" = code + tests + DECISIONS.md + RELEASE_NOTES.md + docs/release_notes.md + docs/user_guide + docs/developer/ui.md + tooltips (the `generateDocs` Gradle task regenerates `src/main/resources/docs/` and runs as part of `./gradlew test`, so those files show as modified after a test run: commit them). Never add Claude co-author lines to commits. Give a short recommendation before planning, ask before design decisions beyond the plan, delegate wide exploration to an Explore agent. The user commits themselves unless they ask you to.

## State (everything committed on main; working tree clean as of writing)
- Plan steps 1-5 (model, pane, Library nav, Edit-bay hosting, dirty-deck modal) are implemented. Naming gotcha: the plan's "step 5" (delete old panels) is still OPEN; the plan's "step 6" (dirty-deck modal) is done.
- Step 4 (Edit-bay hosting) shipped as `ApplyTarget` / `ScopeMemory` (`ui/browser/ApplyTarget.kt`), targets built per tab in `PerformanceBrowseBay`, controller nav through `BrowserPane.hosted()` + `LibraryPanel.navMode`. See DECISIONS.md "The Unified Pane Is Hosted in the Edit Bay".
- FX and transition picks in the bay are undoable (`FxOps.undoSink` / `TransitionOps.undoSink`, opt-in `undoable` parameter; backlog 28 done).
- Polish done: tree counts follow the target's filter (`BrowseCatalog.tree(accepts)`), Shift+tap on a tree playlist enqueues it (`BrowserPane.enqueueCursorPlaylist`).
- The pane is still **beta**: `BrowserPane.enabled` (`-Dlsd.unifiedBrowser=true` or the "Unified" toggle in the Library tab bar). One flag covers both the Library and the Edit bay. Classic four columns and the old inline picker are still the default.
- Dev flags for screenshots: `-Dlsd.libraryTab=fx|trans`, `-Dlsd.editBrowse=gen|chain|fx1|fx2|fx3|trans`. Screenshot command: `JAVA_TOOL_OPTIONS="-Dlsd.unifiedBrowser=true -Dlsd.editBrowse=fx1" ./gradlew run --offline -q --args="--no-audio --window=1280x720 --screenshot-ui=/tmp/x.png --screenshot-after-frames=10"` (frame counts above about 30 never fire; the window comes out about 768 tall).

## Decisions (user, 2026-10-04)
- Queue pane stays a fixed half/half stack (no splitter or tabs) for now.
- Scope selection and the Library tab are NOT persisted across restarts. Fine as is.
- Known and accepted: Shift+tap does nothing in the Edit bay; Maps tab stays separate (no queues, `emptyList()` MIDI nav).

## Not yet verified by hand (the user does this; do not promote before they report)
- Edit bay: click-apply on SRC / Chain / FX1-3 / TRANS, applied `●` marker, Ctrl+Z on each, Clear Slot / Clear Chain, per-tab scope memory, External video menu.
- Twister in the bay: side 2 steps tree > list > queues, tree tap selects scope and jumps to the list, knob 1 + tap applies, Shift+side 3 clears.
- Library pane: FX/Trans playlist drag-reorder and insert, FX overwrite popup from the pane, drag from a playlist row to a queue, Shift+tap playlist enqueue.
If the user reports a problem, fix it before anything below.

## The task (in order; stop and ask at each gate)
1. **Ask the user whether the hand-check passed.** Do not start 2 without a yes.
2. **Promote the pane to the default.** Recommendation: flip the default of `BrowserPane.enabled` to true (keep `-Dlsd.unifiedBrowser=false` and the "Unified" toggle as the escape hatch for one more round of use), update the Library tab-bar tooltip and the docs that call it beta. Check what the Library's classic path does when `enabled` is false so the fallback still works.
3. **Plan step 5, delete the old code once nothing uses it** (after the user has lived with the default for a while; ask when). Candidates, verify each with grep before deleting:
   - `ShaderPickerPopup` inline mode (`ensureInline*`, `drawInline`, `moveCursor`, `acceptCursor`, `stepCategory`, `detach`; only `PerformanceBrowseBay` and `NavigationSurface` use it) and `ChainListBrowse` + `drawChainList` in `PerformanceBrowseBay.kt`; the old tab bodies (`drawFxSlotPicker`, the non-pane branches of `drawGenBrowse` / `drawTransitionBrowse`).
   - The classic nav branches: `LibraryNavigation.panes()` non-unified branch, `SelectionSource.PLAYLIST` / `FX_PLAYLIST` / `TRANSITION_PLAYLIST` handling in `LibraryPanel.navigateSelection` / `getActiveSelectedFile` and `LibraryNavigation`, the old picker branch in `NavigationSurface` (`pickerButton` non-hosted path, `browseStep` / `browseAccept` fallbacks, `inPicker`'s old `isShowing` terms). Keep the `unifiedKind()` / `hosted()` logic, drop the `enabled` conditions.
   - The classic Library column wrappers: `PresetListPanel` / `FXBrowserPanel` / `TransitionBrowserPanel` column code, the three playlist editor panels (`PlaylistEditorPanel`, `FXPlaylistEditorPanel`, `TransitionPlaylistEditorPanel`) and any queue-action panel code only they used. **Keep `drawRows` and the row helpers (`drawRow`, `drawInfoColumn`, `nameForInfo`, context menus, `applyToDeck` / `applyToMixer`, create/overwrite popups)**: the pane shares them. Several selection fields (`selectedAsset`, `filteredRows`, `selection`) are still used by the pane and nav.
   - Tests: `NavigationSurfaceTest` (picker section, `ChainListBrowse`), `PickerCursorTest` in `LibraryNavigationTest.kt` pin the old path; port the relevant assertions to the hosted-pane equivalents instead of just deleting them.
   - Update the picker row of the side-button table in `docs/user_guide/performance_controls.md` (it currently has a separate "Picker open, unified pane on (beta)" row) and the picker/browse text in `docs/user_guide/macros_and_rack.md` (~line 330), `docs/user_guide/presets_and_library.md` (describes the four-column Library) and `docs/developer/ui.md`.
4. **Final docs pass (plan step 7):** DECISIONS.md, both release-notes files, user guide, developer docs, tooltips, `.planning/codebase/ARCHITECTURE.md` is stale (see memory), `.planning/pre-release-ui-backlog.md`; run `./gradlew test --offline` (regenerates `src/main/resources/docs/`).

## Pitfalls (learned this step)
- `LibraryPanel.navMode` (not `viewMode`) must be used by any cursor/selection code that should work while the pane is hosted in the Edit bay; `viewMode` is the Library's own tab.
- `BrowserPane.hosted()` is a 300 ms window driven by `UiClock.nowMs`; tests set `UiClock.nowMs` and call `BrowserPane.noteHosting(target)`; reset it (and `BrowserPane.enabled`, `LibraryPanel.activeSelectionSource`) in `finally`.
- `setDisclosure` (opening Edit) drops Library FULL to HALF; tests pin it (see `NavigationSurfaceTest.setUp`).
- `FxOps` / `TransitionOps` changes are queued and applied in `drainOnGlThread`; tests must drain (see `FxOpsTest`).
- A relaxed MockK `Mixer` returns a non-null `transitionFilter`; stub it with `every { mixer.transitionFilter } returns null` when a test needs "no transition".
- Never write a slash-star inside a KDoc.
- Keep the `FxPick` / `SourcePick` types only if something outside the inline picker still uses them; check before deleting.
