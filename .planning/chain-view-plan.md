# Chain view: SRC + FX1/FX2/FX3 rows for one deck (v1.0)

Status: step 1 DONE (2026-10-27, uncommitted; suite green apart from the existing `DeckOpsTest.moveAndSwapCarryTheBankToo` failure,
which also fails on clean main). Step 2 DONE the same day (uncommitted): slot rows in the catalog, usable on custom pages; decision 4 changed, see below. Step 3 DONE the same day (uncommitted): mode state, rows, load row, mouse entry/exit; row order changed to the user's option 2, see "What it is". Step 4 folded into 3. Step 5 (Twister) DONE the same day (uncommitted, unseen on hardware): shift + tap SRC toggles, right-bottom toggles Load, side-2 = Library (playing) / pane (loading), shift + right-bottom clears the targeted slot, `NavSurface.browseKnob` / `isBrowseLive` / `browseKnobTap`, load-knob LEDs. Step 6 docs were done alongside each step; what's left is a look in the app and on hardware. Open questions answered: the user took every default. Scope: v1.0. The point is that 1.1 needs no new UI shift for FX play.
This supersedes the shelved Option A in `fx-chain-expanded-view-notes.md`.

## What it is

Chain view is a deeper version of the pair focus view for one deck. It has two states, **play** and **browse**. Rows 2-4 are the
same in both states, on screen and on the Twister. Only row 1 swaps. (Decided 2026-10-27, the user's option 2: row 1 is the control
row, so SRC stays on top like every other page and signal flow reads top to bottom. The first draft had SRC under the FX rows.)

| Row | Content | K1 | K2-K4 |
|-----|---------|----|-------|
| 1, play | `deck.X.src` (unchanged) | source macros | source macros |
| 1, browse | load row (`RowDescriptor.loadRow`) | K1-3 push = load highlighted effect into slot 1/2/3 | K4 = browse cursor |
| 2 | `deck.X.fx1` | slot 1 Metaknob (push = bypass) | slot 1 `focusParameters`, page p1 |
| 3 | `deck.X.fx2` | slot 2 Metaknob (push = bypass) | slot 2 params, page p2 |
| 4 | `deck.X.fx3` | slot 3 Metaknob (push = bypass) | slot 3 params, page p3 |

- **On screen:** always 4 rows above the Browse dock, so no geometry changes. In browse state row 1 is drawn as the load row: badge
  LOAD, the list's cursor item, three `→ FX1/2/3` targets (a click loads the cursor item there) and a LIST column for knob 4.
- **Twister:** 16 knobs, row-major. In browse state, K1-3 turn = nothing (they're load buttons), K4 = cursor.
- **Superseded 2026-10-28: the control row moved to the bottom** (rows 1-3 FX1-3, row 4 SRC / load), so the cursor is knob 16 here
  too and knobs 13-15 load. Reasons and rejected alternatives in DECISIONS.md, Chain view entry. (Was: cursor knob 4, accepted as an
  inconsistency.)
- **Master:** gets the same view. Row 1 (play) is `trans`, as in the Master pair; rows 2-4 are the Master FX slots.

**Why a mode and not pages:** this is a closer look at one deck, so it uses one gesture and follows the deck. It doesn't use up
Twister banks (the stock profile has only 4). The slot rows still go into `PerfRows.CATALOG` (step 2), so a user page can also
use them through the page editor at no extra cost.

## Design decisions

1. **Knob source: one synced macro bank per slot.** Every `KnobSpec` carries a `MacroControl`, and `PerformSurface.turn` writes
   `control.value`. So each slot gets its own read-only bank, synced by `FxMacroSync` (bank ids `deckX_fx_s1..s3`, `masterFx_s1..s3`).
   `syncFocusMode` already does this mapping for one slot; it is generalized to (chain, slot, bank). The other option was a `KnobSpec`
   variant that drives a `ModulatableParameter` directly, but it would need its own paths for pickup, LEDs, MIDI/OSC and value overlays.
   Bonus: `Macro/deckA_fx_s2/knob_3` always means the same parameter. That's a stable target for user MIDI/OSC maps, unlike the
   focus-mode chain bank, which remaps.
2. **Per-slot param page.** `FxChain.focusParamPage` (single) becomes per slot (`slotParamPage[SLOT_COUNT]`). The existing focus
   mode reads the focused slot's entry, so the two views agree.
3. **Slot row layout reuses `PerfKnobResolver.focusSpec`.** K1 = `SlotCell` + Bypass; K2-4 = `ParamCell` with Reset, value overlay,
   meterType and steps. No new cell types. Geometry is unchanged (`PerfRowGeometry`, ~41px knobs at rowH 74).
4. **Left block per slot row:** a slot badge (`FX1/2/3` in deck accent), the loaded effect's name, and an empty-state label.
   It is a drop target (drag an effect = load into that slot) with a load/clear kebab. Width stays at `DeckRowMetrics` so the
   layout-stability tests hold. The chain-level controls (Super Knob, chain dry/wet, chain bypass) go in a compact chain header in
   the left block of row 1. They are on screen only; there are no spare Twister knobs for them.
   **As built (step 2):** no separate chain header. Every slot row's left block is the focus-mode header for its slot
   (`FxChainHeader.drawSlotHeader`): effect name (click = browse that slot) over `[⋮ chain menu] [slot Wet] [◀ Px/y ▶]`.
   The deck's die + FX ON (Master: FX ON) are drawn on the FX1 row only. The Super Knob and chain dry/wet stay in Edit and on
   the chain's own FX row; chain Save As / Revert / Clear / Copy / Paste are in the ⋮ menu.
   The badge drop target loads a `.lsdfx` into the row's own slot. Shift + tap on a slot row's K1 is a no-op until step 5.
5. **LEDs:** the FX hue rotation stays at +30°, stepped per slot (+20° / +30° / +40°) so the three rows read apart. Empty or
   bypassed slots go dark through the existing `isLit`.
6. **State:** `ParametersState.chainView` (null / PLAY / BROWSE) sits on top of `focusedPair`, kept across `focusPair` (stepping
   decks) and cleared with the pair (leave, Edit, page tab, Library FULL). Not persisted, like the pair view.
   `PerfRows.chainRowsFor(tag, state)` gives the 4 rows; `visibleRowsForPage` and `RowsCache` (key: tag + state) use it.
7. **Layout at 1280x720:** 4 x 74 = 296px of rows, which is under the `availH-160` cap and leaves ~380px for the Browse bay.
   The grid is already `rows x 74`, so no new layout code.

## Entering, leaving, following

**Mouse** (as built in step 3)
- **Chain View** item in every FX chain's ⋮ menu (FX rows, slot rows, the chain's Edit tab) enters it for that chain's pair;
  in Chain view the same item reads **Leave Chain View**.
- The pair-view `BayHeader` has a **Chain** toggle (plain pair view ↔ Chain view) and, in Chain view, a **Load** toggle (play ↔ browse).
- Entering, and switching to browse, put the dock on an FX slot (slot 1 unless one is already targeted).
- Back / Esc leaves Chain view in one press from either state (before the FX-focus step of the back stack), back to the Perform page.

**Twister** (works on stock firmware)
- **Shift+tap on any SRC knob** (on a normal page) enters chain view for that deck, in play state. This gesture is currently a
  no-op (`PerformSurface.secondary`, Label cells). In play state, shift+tap on the row-1 SRC knobs exits too.
- **Back** (left-top) leaves chain view from either state, like the pair view.
- **Picker button** (right-bottom) toggles play ↔ browse. Only row 1 changes.
- **Bank next/prev** walks A → B → BG → PV → Master, staying in chain view and in the current state (reuse `stepPair`).
- **Tap a slot K1** = bypass (unchanged). **Shift+tap a slot K1** targets the Browse dock at that slot (mouse: click the row).
  "Focus slot" has no meaning here, since every slot is already shown.
- **Shift+tap a param knob** = next page for that slot (unchanged meaning, now per slot).

**Browse / auditioning**
- **Browse state, Twister:** K4 scrolls the cursor (the same handler as browse mode). K1/2/3 push = load the highlighted
  effect into slot 1/2/3 (`ChainLoad.loadCursorInto`, already used by the on-screen targets). The slot rows below stay live, so you
  can load, tweak and compare against the other two slots without leaving the view.
  - Live knobs = 5-16 (today `browseLiveKnobs` counts leading knobs; it needs to become a set or a range).
  - The browse knob moves from the fixed `KnobCommands.BROWSE_KNOB` to the surface's choice.
  - The send knobs (9-13) are off in chain view: the destination is always a slot.
  - Nothing hands off to the pair view.
  - **Until step 5, Chain view on the Twister behaves like the pair view:** knobs 1-8 live (rows 1-2), 9-13 sends, 16 cursor.
- **Mouse, either state:** the Browse dock targets the last-touched or clicked slot row (`PairKnobTouch` covers all 4 rows in Chain
  view, done in step 3). Apply/double-click loads into that slot, and dropping an effect on a slot row's badge loads into that slot.
- **LEDs in browse state:** K1-3 lit in their slot's hue (dim if empty); K4 as the browse cursor.

## Steps

1. **Model.** Add per-slot param page in `FxChain` and per-slot banks in `MacroEngine`. Generalize
   `FxMacroSync.syncFocusMode` to sync all slot banks every frame they're visible, or always if cheap. Re-key existing focus mode
   to the per-slot page. Tests: slot banks follow `knobLayout`, the time-like exponential curve, empty slot, and slot swap moving
   pages with the slot.
2. **Catalog rows.** Add `RowDescriptor.slotIndex`, catalog ids `deck.X.fx1..3` / `master.fx1..3`, a `PerfKnobResolver` slot path,
   the left block in `drawMatrix` (badge, name, drop target, chain header on fx1), and slot LED hues. The rows show up in the page
   editor. Tests: `resolveInto` with 4 rows, layout stability, LED hue.
3. **Mode state + screen.** Add `chainView` (off/play/browse) and enter/leave/step/toggle, `visibleRowsForPage` (4 or 3 rows),
   the `RowsCache` key, the bay layout, the expand/collapse buttons, a play/browse toggle in the header, and Esc. Tests: exclusive
   with Edit, leave restores the page, stepping keeps mode and state, rows 1-3 identical across the toggle.
4. **Browse targeting (mouse).** Extend `PairKnobTouch` to the slot rows, add click-to-target and drop-to-slot, and wire the
   `fxBinding` slot index. Tests: target follows the touch; apply loads the right slot.
5. **Twister.**
   - Shift+tap SRC enter/exit, back exits, picker toggles play/browse, bank stepping.
   - Load row: K4 cursor via a surface-chosen browse knob, K1-3 push = load into slot 1/2/3, live knobs 5-16, sends off.
   - Browse-row LEDs, a full 16-ring rewrite on every enter/leave/toggle, and the `lastTouchedKnob` reset.
   - Update both profile descriptions.
   - Tests: `ControllerRuntimeTest` and `PerformSurfaceTest` cases (K2 push loads slot 2; K4 moves the cursor; rows 2-4 keep
     their bindings across the toggle).
6. **Docs + polish.**
   - `performance_controls.md`: new Chain view section, Twister layout, gesture and side-button tables.
   - `hardware.md`, `your_workspace.md`, `macros_and_rack.md` (slot banks as MIDI/OSC targets).
   - Both release-notes files.
   - DECISIONS.md: edit §6/§7/§8 in place.
   - Tooltips on expand/collapse, slot badge and chain header.
   - Merge an entry into `.planning/app-checklist.md` and regenerate the PDF.

Each step is committable on its own. Steps 1-2 are useful without the mode (slot rows on custom pages).

## Open questions (all answered 2026-10-27: defaults taken)

1. ~~Chain Super Knob / chain bypass on the Twister?~~ Settled: screen only. K1-3 are the load buttons in browse state, and
   the SRC knobs in play state.
2. **K1 = slot Metaknob or slot dry/wet?** [Metaknob, which matches focus mode. Slot dry/wet stays in Edit.]
3. **Master row 1 = Transitions or MST levels?** [Transitions, which matches the Master pair.]
4. **Sync cost:** settled: always. `syncChain` runs on chain changes (load, swap, link, focus, page), not per frame, so the
   3 extra banks per sync cost nothing measurable.
5. **PV deck:** does it need chain view? [Yes, same code, no special case.]
