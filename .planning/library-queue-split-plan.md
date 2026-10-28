# Library + Queues split view

Status: IMPLEMENTED 2026-10-27 (all 5 steps, decisions 1-4 as recommended), uncommitted; screenshot-checked at 1280x720, drag/drop unseen in app. v1.0 scope (workflow restructuring of existing features, same footing as the
library/browser unification).

## The problem

The Library has one body slot, and the Queues tab takes it over. To put an item at a particular place in a queue you add it from
the browser (Q, Shift+Q, row menu: always at the bottom), switch to the Queues tab, and drag it up. You can't audition in the
browser and drop into a queue at the position you want in one move.

## The idea

In the Library view (FULL), split the body into two stacked panes, each about half the height:

```
+--------------------------------------------------------------+
| tabs: SRC  FX  TRANS  QUEUES  MAPS            [Q split] [max] |
+--------------------------------------------------------------+
|  browser pane (tree | list), the same width as today          |
|                                                              |
+============================ drag ============================+
| A/B | FX A/B | BG | FX BG | TRANS    (QueuesPane, unchanged) |
|  1. |  1.    | 1. |  1.   |  1.                              |
+--------------------------------------------------------------+
```

Both panes keep their current widths. Dragging from the browser into a queue column already inserts at the drop position, with the
insert line (`QueueActionsPanel.kt` ~L182 and the same block in the other four panels), so the drop behavior exists. This is a
layout change plus a few safeguards.

## What exists today (checked 2026-10-27)

- `BrowserDock.drawBody` picks ONE of: `QueuesPane.draw`, `MapsBrowserPanel.draw`, `BrowserPane.draw`, by `LibraryPanel.viewMode`.
- `QueuesPane` draws 5 equal columns in a row, filling the given space (min column 150px).
- Each queue panel accepts `ASSET_ITEM` (one path per line) at an index and reorders with its own payload
  (`QUEUE_ITEM`, `FX_QUEUE_ITEM`, `FX_BG_QUEUE_ITEM`, `TRANS_QUEUE_ITEM`, plus BG). The insert-line and drop code is copied
  five times.
- **No kind check on drop in the FX, FX BG and Transitions queues**: they insert any path. The A/B and BG queues filter by
  extension (`patch`, `lsd`, `json`, playlists). Nothing triggers this today because the queues are never on screen while you drag
  from another tab. With the split it would be easy to do by accident.
- `LibraryRefs.kindOf(file)` / `playlistKindOf(file)` already map extensions to `BrowseKind` (`.lsd` SRC, `.lsdfx`/`.lsdfxchain`
  FX, `.lsdtrans` TRANS, plus the three playlist extensions). Use them as the single kind check.
- Stock sources and stock FX drag with their own payloads (`PAYLOAD_STOCK_SOURCE`, `PAYLOAD_STOCK_FILTER`). Queues hold files, so
  stock items can't be queued. Leave that as it is.
- The Perform view's Library (HALF) is whatever height the fixed Perform rows (`PERFORM_TOP_H`) leave, which is too short to split.
  So the split is for FULL only.
- Keyboard and controller move the cursor through `LibraryNavigation.panes()`: TREE, PRESETS on browser tabs, the five queues on
  the Queues tab. Clicking a queue row clears the browser selection (one cursor, `activeSelectionSource`).

## Decisions (recommendations; confirm before step 2)

1. **Toggle or always split?** Recommend a toggle button on the Library header line next to Maximize/Restore, remembered in
   AppPreferences, **on by default**. It only shows in FULL, on the SRC/FX/TRANS tabs.
2. **Keep the Queues tab?** Yes, unchanged. It is the only way to see queues from the Perform view (HALF), and in FULL it is the
   split with the browser collapsed. Maps never splits.
3. **Show all five queues, or only the ones that fit the tab?** All five, so the bottom pane never changes when you switch tabs.
   During a drag, columns that won't take the item dim (step 1), so the extra columns don't get in the way.
4. **Pane heights:** a draggable divider, ratio remembered (default 0.5). Clamp so neither pane drops below a minimum (queues: the
   toolbar plus 3 rows; browser: the search line plus 3 rows). At 1280x720 that's about 300px each by default.

## Steps

Each step is one green commit.

### Step 1: one shared drop helper that checks the kind

- New `ui/browser/QueueDrop.kt`, used by all five queue panels in place of their copied `ASSET_ITEM` blocks (both the per-row
  insert target and the empty-list append target):
  - `accepts(queue, path)`: `LibraryRefs.kindOf` / `playlistKindOf` must match the queue's kind (SRC for A/B and BG, FX for both FX
    queues, TRANS for transitions). Keep the legacy `patch`/`json` extensions on the SRC queues if they still load anywhere.
    Check this; if nothing loads them, drop them.
  - `insert(queue, index, payload)`: expands playlists, skips files of the wrong kind, returns how many it added. If some were
    skipped, a toast says so (e.g. "2 FX skipped: the A/B queue takes sources").
- **Dim during a drag:** while `ImGui.getDragDropPayload()` is an `ASSET_ITEM` and none of its paths fit this column, draw the
  column at reduced alpha and skip `beginDragDropTarget`.
- Optional, cheap: accept `BROWSE_PLAYLIST` (a playlist dragged from the tree) at a position too, expanded like a dropped
  playlist file.
- Tests: kind matrix (every queue × every extension, plus mixed multi-line payloads and playlists), the skipped count.

This fixes the latent wrong-kind insert whether or not the split ships.

### Step 2: the split layout

- `BrowserDock.drawBody`: when FULL, the split is on, and the tab is SRC/FX/TRANS, draw `BrowserPane` in a child sized
  `topH`, then a divider (an invisible button with a 6px hit area and a 1px line, resize cursor on hover), then `QueuesPane.draw` in a
  child sized `bottomH`.
- Do the height math in a pure function (`QueueSplit.heights(contentH, ratio, minTop, minBottom)`) so it can be tested.
- Divider drag updates the ratio; save it on release, not every frame. Double-click on the divider resets to 0.5.
- Header toggle: icon button with a tooltip ("Queues below the browser: drag rows straight into a queue at the place you want."),
  lit while on. Hidden in HALF and on Maps/Queues tabs.
- `BrowserPane` and `QueuesPane` must not assume they own the whole window height. Both already size from
  `getContentRegionAvailY()`; check that nothing reads the window size directly.
- Tests: the height function (default, clamps, a window too short for both minimums, which means drop the split for this frame).

### Step 3: scroll the queue while dragging near its edge

- While a drag is in progress and the mouse is within about 24px of the top or bottom of a queue's `##..._items_scroll` child,
  scroll that child, faster nearer the edge. One helper in `QueueDrop`, called by all five lists. Use it for the browser list too,
  since dragging rows onto folders has the same issue.
- Check first whether the mouse wheel already scrolls the hovered child mid-drag in imgui-java. Edge scrolling is still worth
  adding either way.

### Step 4: keyboard and controller

- `LibraryNavigation.panes()` when the split is shown: `TREE, PRESETS, QUEUE_AB, FX_QUEUE_AB, QUEUE_BG, FX_QUEUE_BG,
  TRANSITION_QUEUE` (screen order: top pane, then bottom left to right). `stepPane` and the Twister browse knobs pick this up.
- `enqueueTargets` stays based on the browser selection. On the split, Q / Shift+Q still append, and the receiving column briefly
  flashes its border (step-1 helper) so you see where the item went.
- Moving the cursor into a queue clears the browser selection, as it does today. Accept this; don't add a second cursor.
- Tests: extend `LibraryNavigationTest` (pane order with split on/off, FULL vs HALF).

### Step 5: docs and checks (part of "done", per repo rule)

- `docs/user_guide/presets_and_library.md`: Library view section (the split, the toggle, the divider), and the Queues tab paragraph
  (drag targets dim when they don't fit).
- `RELEASE_NOTES.md` and `docs/release_notes.md`.
- `DECISIONS.md`: edit the three-view / Library entry in place. FULL may split browser over queues; HALF never does.
- Tooltips: toggle, divider, the queue columns' empty hint (`QueueToolbar.drawEmptyHint`: "Drag items here" is now literally
  possible from the same screen).
- `tours/library.json`: the Queues tab caption is still true; consider a step that drags into a queue on the split.
- `.planning/app-checklist.md`: merge the visual checks below into it (don't append a separate list).

## Checks in the app

- 1280x720 (`--window=1280x720`): both panes usable at the default ratio; the divider clamps.
- Drag a source into the middle of the A/B queue: lands at the line. Drag it at the FX queues: they're dimmed and the drop does
  nothing.
- Drag a multi-selection containing a playlist: expands in place, in order.
- Drag to the bottom edge of a long queue: it scrolls.
- Toggle off: Library FULL looks exactly as it does today. HALF is unchanged at all times.
- Light theme: the dim and the divider line are both visible.

## Out of scope

- Splitting the Perform view's Library (HALF): no room.
- A "queue next" insert (after the playing item) for Q. Good idea, separate small item.
- Queuing stock (unsaved) sources/FX.
