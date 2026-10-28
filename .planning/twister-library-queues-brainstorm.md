# Twister in the split Library view: brainstorm

Status: BRAINSTORM 2026-10-27, nothing decided. Follows `.planning/library-queue-split-plan.md` (committed d39c5b2).

## What the Twister already does in the Library view (`NavigationSurface`, `LibraryNavigation`)

- Knob 16 is the cursor: turn moves it, tap = accept (the row's default action), shift + tap = add to the A/B queue of the tab's kind.
- Knobs 9-12 send the cursor item to Deck A / B / BG / PV, and knob 13 sends to Master FX (Sources and FX tabs only). **Knob 12 is
  already "preview on PV".** Rings glow when the item can go there.
- Side buttons: left-top = back (shift: add to the BG queue), right-middle = tab (shift: back), right-bottom = pane (shift: back).
- Since d39c5b2 the pane step continues from the browser into the five queues, and accept on a queue row plays it now. So
  "navigate the queues and use this now" works today, slowly: one cursor, stepped pane by pane.
- Knobs 1-8, 14 and 15 do nothing in the Library view.
- Hold the knob switch + turn = fine adjust. That means nothing on a cursor knob, so it is free there.

## Every possible thing (rated)

Ratings: **H** = core to the workflow, **M** = clearly useful, **L** = nice but rare, **–** = not a Twister job (mouse or keyboard).

### Browser
| # | Action | Rating | Notes |
|---|---|---|---|
| B1 | Move the cursor in the list | H | exists (K16) |
| B2 | Pick a folder, playlist or tag in the tree | M | exists via the pane button; a tree knob would remove a button press |
| B3 | Switch tab | H | exists (side button) |
| B4 | Search | – | no keyboard |
| B5 | Preview on PV | H | exists (K12) |
| B6 | **Auto-preview: PV follows the cursor** after a short pause | M-H | scroll and watch; a latch, so it can't fire during a set by surprise |
| B7 | Tweak what is on PV while auditioning | M-H | knobs 1-8 are unused here; the pair view already does this for the browsed deck |
| B8 | Send to a deck (A, B, BG, PV, Master FX) | H | exists (K9-13) |
| B9 | Add to a queue at the end | H | exists (shift + tap K16, shift + left-top for BG) |
| B10 | **Add to a queue at a chosen position** | H | the Twister version of the new drag; see the "last touched queue knob" rule below |
| B11 | Add a whole playlist (tree cursor) to a queue | M | falls out of B10 if the tree knob/pane uses the same send |
| B12 | Star (favorite) the cursor item | L-M | curating while auditioning; no obvious free gesture |
| B13 | Add to the selected playlist | L | |
| B14 | Multi-select | L | |
| B15 | Rename, delete, tag, move to a folder | – | |
| B16 | Preview a transition | L | PV can't show one; the only real audition is "use now" on the mixer |

### Queues
| # | Action | Rating | Notes |
|---|---|---|---|
| Q1 | Move a cursor through a queue | H | |
| Q2 | Choose which queue | H | 5 queues vs 4 knobs per row; see the options |
| Q3 | Use this item now | H | exists via accept |
| Q4 | **Move the item up or down** | H | hold + turn = carry it (the free gesture on a cursor knob) |
| Q5 | Make it next (just after the playing one) | M-H | a special case of Q4; maybe a shortcut gesture |
| Q6 | Remove the item | M | live and destructive: needs undo or a deliberate gesture |
| Q7 | Move or copy the item to the other queue of its kind (A/B ↔ BG, FX A/B ↔ FX BG) | L-M | |
| Q8 | Duplicate the item in place | L | |
| Q9 | Queue transport: next, previous, Auto, repeat, shuffle | M | the Auto toggle matters live; MIDI Learn and OSC already reach most of these |
| Q10 | Clear a queue | L | dangerous; leave to the mouse |
| Q11 | Export a queue as a playlist | – | |

### Feedback
| # | What | Rating |
|---|---|---|
| F1 | A queue knob's ring = its cursor's place in the queue; LED in the queue's colour (A/B mint, BG rose) | H |
| F2 | The queue columns the Twister is driving are outlined on screen, so the screen and the hardware agree | H |
| F3 | A queue knob's LED flashes when items arrive (mirrors the border flash) | M |
| F4 | The playing item shows on the ring (one bright LED), the cursor as the bar | L-M |
| F5 | The auto-preview latch shows as a pulsing K12 LED | M |

### Gaps the brainstorm turned up
- **Queue edits have no undo.** Remove (Q6) and carry (Q4) on a knob need Ctrl+Z, or at least a toast with an undo.
- **Tweaks made while auditioning on PV are lost when you queue the item**: queues hold files, not deck state. Either accept it or
  offer "save then queue". (The same is true with the mouse.)
- Queue knobs when the split is off: drive the queues blind, or go dark? Recommend dark, since the screen must show what the knob
  moves.

## Distilled proposal (a starting point, not a decision)

The model: **every list is a knob**. Turn = move its cursor; tap = its main action; shift + tap = its second action; hold + turn =
carry the item (queues only). Moving something from one list to another goes to **the queue knob you touched last, at its
cursor**, the same "last touched" rule that Browse already uses for slots.

Rows 3-4 keep their current jobs (sends and the cursor), so the pair view and the Library still feel the same.

| Knob | Turn | Tap | Shift + tap | Hold + turn |
|---|---|---|---|---|
| **K1-K8** | the PV deck's two rows (PV SRC + PV FX), to tweak the audition | as on those rows | as on those rows | fine adjust |
| **K9-K12** | – | send to A / B / BG / PV (as now) | quick switch (as now) | – |
| K12 | – | send to PV | **toggle auto-preview** (PV follows the cursor) | – |
| **K13** | – | send to Master FX (FX tab, as now) | – | – |
| **K14** | cursor in the tab's **A-side queue** (Sources: A/B; FX: FX A/B; Transitions: Transitions) | use it now | remove it | carry it up or down |
| **K15** | cursor in the tab's **BG-side queue** (Sources: BG; FX: FX BG; Transitions: none) | use it now | remove it | carry it up or down |
| **K16** | browser cursor (as now) | accept (as now) | **add to the last-touched queue knob's queue, before its cursor** (default: the end of the A-side queue) | – |

Side buttons unchanged. On the Queues tab, K16 keeps stepping through all five queues by pane, as today.

Why tab-relative queue knobs: the tab already says what kind of item you're holding, and each kind has at most two queues. That
fits two knobs and no "which queue" step. The cost: the FX queues aren't reachable from the Sources tab without a tab press. That
seems right, since that tab can't add to them either.

## Alternatives considered

1. **One queue-select knob + one queue cursor** (K14 turns through all 5 columns, K15 moves in the chosen one). Reaches every queue
   from any tab, but every queue action starts with a select turn, and a send needs a kind check.
2. **One knob per queue** (row 2 = A/B, FX A/B, BG, FX BG; Transitions somewhere else). Direct, but 5 doesn't fit a row, and it
   gives up the PV tweak knobs.
3. **Rows mirror the screen** (rows 1-2 = browser, rows 3-4 = queues). Nice in principle, but it moves the sends and the cursor,
   which are hardware-tested and shared with the pair view.

## Open questions for the user

1. Tab-relative queue knobs (K14/K15), or the select + cursor pair (alternative 1)?
2. Remove on a knob: shift + tap with an undo toast, or no remove from the Twister at all?
3. K1-8 in the Library view: the PV pair (tweak the audition), or the last send target's rows (like the pair view)?
4. Auto-preview latch: wanted? Delay before it loads (300-500 ms)?
5. Queue knobs with the split off: dark (recommended) or live?
6. Is "make it next" (Q5) worth its own gesture, or is carry enough?
