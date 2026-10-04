# Handoff: unified BrowserPane step 4, Edit-bay hosting (written 2026-10-04)

You are a fresh agent. Read, in this order: `.planning/unified-browser-pane-plan.md` (decisions, measurements, progress; "Architecture" and step 4), `.planning/midi-phase3-navigation-plan.md` (picker slice), then this file. Project rules: Kotlin + Dear ImGui desktop VJ app; "done" = code + tests + DECISIONS.md + RELEASE_NOTES.md + docs/release_notes.md + docs/user_guide + docs/developer/ui.md + tooltips (the regenerated `src/main/resources/docs/` comes from the `generateDocs` Gradle task; plan step 7 does it once at the end). Never add Claude co-author lines to commits. Give a short recommendation before planning, ask before design decisions beyond the plan, delegate wide exploration to an Explore agent.

## State (all committed on main)
- Steps 1, 2 (SRC/FX/TRANS), 5 (dirty-deck modal from the controller) and 3 (Twister navigation of the pane) are DONE.
- Step 3 specifics: `SelectionSource.TREE`; `LibraryNavigation.unifiedKind()` (non-null when `BrowserPane.enabled && supports(kind)` on a SRC/FX/TRANS tab) and `panes()` = TREE, PRESETS (the list), then the kind's queues; `BrowserPane.stepTree/acceptTree/treeCursorOf/treeSize`; pure `visibleSelectableScopes` / `stepTreeCursor` in `BrowseModel.kt`. User decisions: a fresh tab starts in the list; tree turn only moves the cursor, tap selects the scope; a scope change clears the list selection (`BrowserPane.setScope`). Tree cursor is drawn as a SYNC-cyan outline.
- Not verified by hand: the Twister run of step 3 (user does it) and the cursor outline visually (screenshots can't press buttons; the outline only shows while `activeSelectionSource == TREE`).
- Pane is still beta (`BrowserPane.enabled`, "Unified" toggle in the Library tab bar or `-Dlsd.unifiedBrowser=true`); classic columns remain the default.

## The task (plan step 4)
Host the pane in the Edit view: `BrowserPane(kind, target)` where `target` is null in the Library (today's behavior) and in Edit view is the slot chosen by the bay's tab row (SRC / FX1-3 / Chain / TRANS). With a target, a click applies immediately through `DeckOps` / `FxOps` / `TransitionOps` and Ctrl+Z undoes (current bay behavior). The Edit view uses the FULL tree with the queue always visible (user decision); fall back to compact only if it demonstrably does not fit (Edit bay width is about 871 at 1280, 1042 at 1920, so tree/list/queue is about 218/436/218 at the minimum).
- Read: `ui/PerformanceBrowseBay.kt`, `ShaderPickerPopup` (inline mode), `ChainListBrowse`, `NavigationSurface.kt` (the `inPicker` context and `ChainListBrowse.move/accept/publish/reset`, `stepCategory`), `ui/browser/BrowserPane.kt`, `ui/LibraryNavigation.kt`, `PerformanceBrowseBayTest`/`PickerCursorTest` in `LibraryNavigationTest.kt`.
- Nav: port the picker's cursor/category stepping onto the pane's panes (tree/list/queue) for the Edit context; keep the old path behind a flag until parity (plan step 4 says so). The picker context buttons are documented in `docs/user_guide/performance_controls.md` (picker row of the side-button table): update when the model changes.
- Do not do step 5 (deleting the old panels) here; that waits until nothing uses them.

## Decisions (user, 2026-10-04; step 3 hardware check passed, "works well")
1. Flag: reuse `BrowserPane.enabled` for the Edit bay (one beta switch). This was the agent's recommendation and the user's "yes to all" did not pick between the options, so mention it once when you present the plan; switch to a separate flag if they object.
2. Edit context on the controller: tapping a tree row selects the scope AND moves the cursor to the list (fewer presses). The Library keeps tap-selects-only.
3. Click semantics with a target: single click applies (as the bay does today); the Library pane keeps double-click to load.
The user is open to discussion, so raise any problem you find rather than working around it.

## Verification
- Unit tests for any new pure logic; `./gradlew test --offline` must stay green (it is as of step 3).
- Screenshots: `JAVA_TOOL_OPTIONS="-Dlsd.unifiedBrowser=true" ./gradlew run --offline -q --args="--no-audio --window=1280x720 --screenshot-ui=/tmp/x.png --screenshot-after-frames=10"` (frame counts above about 30 never fire; the window comes out about 768 tall). Check how to open Edit view with a rack module expanded for a screenshot.
- Hardware check on the Twister is the user's; say what to try.

## Known gaps carried over (not step 4)
- Scope selection is not persisted across restarts; queue pane is a fixed half/half stack.
- Not hand-tested: FX/Trans playlist drag-reorder/insert, FX overwrite popup from the pane, drag from a playlist row to a queue.
- Shift+tap enqueue does nothing while the cursor is on the tree (could enqueue the whole playlist later).
- Maps tab is separate, no queues; MIDI nav returns `emptyList()` for it.
