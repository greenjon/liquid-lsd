# Twister in the split Library view (plan)

Status: PLANNED 2026-10-27, not started. Decisions from `.planning/twister-library-queues-brainstorm.md`, all six open questions
answered with the recommendations (user, 2026-10-27). Builds on the queue split (d39c5b2).

## Decisions

1. **Queue knobs follow the tab.** K14 = the tab's A-side queue (Sources: A/B, FX: FX A/B, Transitions: Transitions), K15 = its
   BG-side queue (Sources: BG, FX: FX BG, Transitions: none, dark). The Queues tab keeps today's pane stepping with K16; K14/K15
   are inert there.
2. **Remove = shift + tap on a queue knob**, with undo (Ctrl+Z) and a toast. Moving items is undoable too. Removing the playing
   item leaves it playing on its deck.
3. **K1-K8 in the Library view = the last send target's pair** (its SRC + FX rows; Master: Transitions + Master FX), starting on
   PV. Shown on screen as a chip on the Library tab line, and the knob LEDs take that pair's colours.
4. **Auto-preview latch:** shift + tap K12 turns it on and off; while on, PV loads the cursor item (Sources and FX tabs) once the
   cursor has rested 400 ms. Off on leaving the Library view. K12's LED pulses while on. Check on the iGPU for stutter on heavy
   items; fallback is skipping gauge-marked items.
5. **Queue knobs are dark and inert while the queues aren't shown** (`QueueSplit.shows` false).
6. **No separate "make next" gesture.** Moving an item is 1 item per detent.
7. Additions: the two driven queue columns get an outline on screen in their knob colour; a queue knob's LED flashes when items
   arrive (mirrors `QueueDrop.flashAmount`); move and remove share one undo snapshot.

**One deliberate change from the brainstorm:** shift + tap K16 inserts **after** the queue knob's cursor, not before, and the cursor
moves onto the new item. With the cursor on the playing item, that makes the new item play next, which covers most of "make next"
with no extra gesture, and repeated inserts keep their order.

## The model

Every list is a knob: turn = move its cursor, tap = its main action, shift + tap = its second action, hold + turn = carry the item
(queues only). An item sent from the browser goes to the queue whose knob you touched last, after its cursor.

| Knob | Turn | Tap | Shift + tap | Hold + turn |
|---|---|---|---|---|
| K1-K8 | the live pair (last send target, else PV) | as on those rows | as on those rows | fine adjust |
| K9-K11, K13 | – | send (as now) | – | – |
| K12 | – | send to PV (as now) | auto-preview on/off | – |
| K14 | A-side queue cursor | use it now | remove | carry |
| K15 | BG-side queue cursor | use it now | remove | carry |
| K16 | browser cursor (as now) | accept (as now) | add after the last-touched queue knob's cursor (none touched: end of the A-side queue) | – |

Side buttons unchanged. Shift + left-top (add to BG queue) stays as a shortcut to the end of the BG-side queue.

## What exists (checked 2026-10-27)

- `control/KnobCommands.kt`: in browse, the cursor knob turns via `browseTurn` (one item per message, `BROWSE_STEP` = 1 tick);
  other knobs are inert unless `NavSurface.isBrowseLive`; taps go cursor > live row > `browseKnobTap` > switch > send. Hold state
  (`held`, `turnedWhileHeld`) is tracked here and a tap after a turn-while-held is already swallowed, so carry can't also play.
- `control/NavSurface.kt` has `browseKnobTap` but no per-knob turn hook.
- `ui/NavigationSurface.kt`: `inLibraryView`, `browseLiveKnobs` = 8 only in the picker / pair view (0 in the Library).
- `ui/PerformSurface.kt`: knobs resolve through `PerformPages.resolveInto` > `PerfRows.visibleRowsForPage` (the page, or
  `focusedPair`'s rows); `dimForBrowse` lights the cursor, live knobs, sends and Chain view's load knobs.
- `ui/LibraryNavigation.kt`: `send`, `sendTargets`, `accept` (already plays a queue row by `SelectionSource`), `enqueue`.
- Queue panels keep their own cursors (`selectedIndex` / `selection`), drawn whatever `activeSelectionSource` is, so a queue knob
  can move a queue's highlight without taking K16's browser cursor.
- Undo: `ParametersState.pushUndoState(ParametersUndoSnapshot(emptyMap(), restore))` is the light, restore-only entry
  (`FxMetaEdit` uses it).
- Queue managers: move (`moveQueueItem` / `move` / `moveItem`), remove (`removeFromQueue` / `removeAt`), play
  (`playIndex` / `jumpToIndex` / `applyTransitionItem`), `insertAt` (all five, via `QueueDrop.insertFiles`).
  `PlayQueueManager.activeIndex` has a private setter.

## Steps

Each step is one green commit.

### Step 1: queue operations with undo (no Twister yet)

- New `ui/browser/QueueOps.kt` over `QueueDrop.Queue`: `cursor(q)` / `setCursor(q, i)` (the panel's selection), `play(q, i)` (the
  same paths as `LibraryNavigation.accept`), `move(q, from, to)`, `remove(q, i)`, `insertAfter(q, i, files)`.
- `snapshot(q)` / `restore(q, snap)`: files, active index and shuffle history. Needs a `restoreState(...)` on `PlayQueueManager`,
  `BgQueueManager` and `QueueEngine` (they own `activeIndex`). Move and remove push
  `ParametersUndoSnapshot(emptyMap()) { restore(...) }`. Remove toasts "Removed X from the A/B queue (Ctrl+Z)".
- Use `QueueOps.remove` / `move` from the queue panels' menu and Delete key too, so the mouse gets the undo as well.
- Tests: move/remove/insertAfter keep the playing item on its file; undo puts the list, active index and shuffle history back;
  remove of the playing item.

### Step 2: knob routing for queue knobs

- `NavSurface`: add `browseKnobTurn(knob, steps, held): Boolean` (consumed or not), next to `browseKnobTap`.
- `KnobCommands`: in browse, a non-cursor, non-live knob offers its turn to `browseKnobTurn` with whole steps (the same
  accumulator logic as `browseTurn`, per knob, 1 item per message) and `held[knob]`.
- `NavigationSurface` (Library view, `QueueSplit.shows`): K14/K15 turn = move that queue's cursor (held: carry the item, cursor
  following it); tap = `QueueOps.play`; shift + tap = `QueueOps.remove`. Records `lastQueueKnob` on any touch; cleared on tab
  change and when the Library closes.
- `QueueKnobs.queuesFor(tab)` = (A-side, BG-side?) in one place, used by routing, lights and the screen outline.
- Tests: `KnobCommands` with a fake `NavSurface` (turn, held turn, tap after a held turn swallowed); `NavigationSurfaceTest` for
  each tab's mapping, inert on the Queues tab and with the split off.

### Step 3: positional send from K16

- Shift + tap K16 in the Library view: `QueueOps.insertAfter(lastQueueKnob's queue, its cursor, enqueueTargets)` (a tree playlist
  expands, as in `enqueue`), the cursor moves onto the last inserted item, `QueueDrop.flash`. No queue knob touched (or its queue
  is the wrong kind): append to the A-side queue, as today.
- Tests: insert after cursor, cursor follows, default append, playlist expansion, wrong-kind fallback.

### Step 4: K1-K8 live pair in the Library view

- `ParametersState.libraryLivePair: String` (pair tag, default PV); `LibraryNavigation.send` from the Library view sets it to the
  target's pair (Master: `PAIR_MASTER`). Not persisted.
- `NavigationSurface.browseLiveKnobs` = 8 in the Library view too; `PerformSurface` resolves the Library view's knobs from
  `PerfRows.rowsForPair(pairFor(libraryLivePair))` instead of the page (an override passed into `resolveInto`, so the matrix
  panel's rows are untouched).
- Chip on the Library tab line (Library view only, while a controller is connected): "Twister 1-8: Deck PV", in the pair's
  colour; tooltip explains that sends move it.
- Tests: the pair follows sends; knobs 1-8 resolve to the pair's rows in the Library view and to the page elsewhere.

### Step 5: auto-preview latch

- `LibraryNavigation.autoPreview` (off; toggled by shift + tap K12 in the Library view; cleared when the Library view closes).
- Per frame while on: the Sources/FX cursor item and the time it became the cursor item; after 400 ms unchanged and different from
  the last previewed item, `send(PV)` (which also points K1-K8 at PV). A one-time toast when FX is previewed with PV empty
  ("PV has no source: the FX shows nothing").
- Time comes in as a parameter so the dwell logic is testable. Tests: dwell, no re-send of the same item, off on leaving.
- Check on the laptop iGPU while scrolling past gauge-marked items; if the output stutters, skip heavy items while auto-previewing
  (they still preview on a tap of K12).

### Step 6: lights and on-screen feedback

- `PerformSurface.dimForBrowse`: K14/K15 = `KnobLight(cursor position, queue colour)` (A/B and FX A/B mint
  `QUEUE_AB_TEXT`, BG and FX BG rose `QUEUE_BG_TEXT`, Transitions `COLOR_TRANS`), ring = cursor place (empty queue: dark), brighter
  while `QueueDrop.flashAmount` > 0; dark while inert. K12 pulses while auto-preview is on. K1-K8 lit in the live pair's colours
  (already true for live knobs once step 4 marks them live).
- `QueuesPane`: in the Library view with a controller connected, outline the K14/K15 columns in their knob colours (thin, under the
  arrival flash).
- Tests: the light buffer for each tab (pure, with a fake nav surface), as `PerformSurfaceTest` does.

### Step 7: docs (part of "done")

- `docs/user_guide/performance_controls.md`: the side-button table's Library row, plus bullets for queue knobs, positional send,
  live pair and auto-preview; the knob 16 / send rings bullets.
- `RELEASE_NOTES.md` and `docs/release_notes.md`; `DECISIONS.md` (the controller-navigation entry, edited in place); the
  `NavigationSurface` / `NavSurface` / `KnobCommands` doc comments; tooltips (the live-pair chip).
- `.planning/app-checklist.md`: merge the hardware checks (all of steps 2-6 are unseen on hardware), and the stutter check from
  step 5.
- Regenerate the in-app docs (`src/main/resources/docs/`) with the build, as usual.

## Risks

- Shader compile stalls from auto-preview (step 5).
- K1-K8 drive a live deck you can't see in the Library view: the chip and LEDs are the safeguard. If it still surprises in practice,
  fall back to PV only.
- `activeSelectionSource` is a single cursor; queue knobs must not change it, or K16 and the keyboard lose their place. Keep the
  queue knob cursors in the panels' own selection only.
