# FX Chain Expanded View (Mixxx-style) — Shelved Notes

**Status:** shelved 2026-09-25, parked on ROADMAP v1.1 backlog. Nothing built.

## Idea

Today an FX row stays one row tall: the slot cells name the 3 effects, and to edit one you
focus it (knob 1 = dry/wet, knobs 2-4 = top 3 params with paging). Mixxx instead expands the
effect unit to show all 3 effects at once. Proposal: when editing a chain, show 3 sub-rows —
one per slot — each with dry/wet + top 3 params, so all 3 effects are visible and reachable.

## Tradeoffs

**Better**
- See the whole chain at once — interplay (feedback → blur → colour) is visible, no
  focus-hopping between slots.
- Mouse users get ~12 direct knobs instead of 4 + paging. This is the main win.
- Series order reads top-to-bottom.
- Each sub-row can reuse the focus-mode layout (dry/wet + 3 params), so it is "focus mode for
  all three at once" — nothing new to learn.

**Worse**
- Hiding the other rows is a *mode* on the live surface: lose Deck B, crossfader,
  Transitions until you exit. Mixxx expands in place and keeps decks visible.
- Hardware still only has 4 knobs per row — focus stays necessary; the focused sub-row must be
  clearly highlighted as the hardware target.
- Must use auto-bind/top-3 ranking + per-row paging, not "first 3 declared" ISF params (order
  is often arbitrary).
- Overlaps Deep Edit (Perform = big switches, Edit = fine tuning); a 12-knob chain view blurs that.

**Deciding question:** mostly mouse → probably great; mostly a 4-knob controller → mostly a
bigger display of today's focus mode. Also: does the user actually hop focus between slots often?

## Layout math at 1280×720 (Perform view, from reading `drawMatrix`, not measured)

Grid ≈ 296px (4 rows × ~74px, `MIN_ROW_H`), ~9px box overhead per row, knob area ≈ 750px wide
(4 cols × ~187px), caption line ≈ 14px.

| Row today | Text below knob | Knob |
|---|---|---|
| Normal deck/master | caption (21px) | ~44px |
| FX group mode | slot cell (27px) | ~38px |
| FX focus mode | caption + FxParamCell (45px) | ~20px |

**Suspected existing issue (unverified):** `diameter` is the min over all groups
(`PerformanceMatrixPanel.kt` ~line 450), so focusing *any* FX row likely shrinks *every* knob
in the grid to ~20px. Worth a screenshot check regardless of this feature.

### Option A — chain takes over the grid (user's original idea)

```
┌────────────┬──────────────────────────────────────────────────────────────┐
│  A  FX     │ ① ● Kaleido ◀▶   p1/2 ◀▶ │ (wet)  (seg)  (rot)  (zoom)       │ 98px
│ ────────── │ ② ○ Feedback ◀▶          │ (wet)  (amt)  (dec)  (hue)        │ 98px
│ MyChain* ◀▶│ ③ ● Bloom ◀▶             │ (wet)  (thr)  (rad)  (int)        │ 98px
│ Save  (SK) │                          │  ▲ highlighted = hardware target  │
└────────────┴──────────────────────────────────────────────────────────────┘
```

- 3 sub-rows × ~98px → ~44px knobs even with the full focus layout. Fits comfortably.
- Chain header (badge, name, ◀▶, Save, Super Knob) in a left column spanning all 3.
- Cost: low–medium. Row-hiding already exists (`visibleRowsForTab`, Edit view) and multi-row
  groups with sub-labels exist (`groupRows`, `subLabel`).
- Risk: other rows/crossfader gone while expanded.

### Option B — expand in place, other rows collapse to strips

```
┌─ A FX ── MyChain* ◀▶ Save (SK) ─┬── ① Kaleido  (wet)(seg)(rot)(zoom) ─┐ ~76px
│                                  ├── ② Feedback (wet)(amt)(dec)(hue)  ─┤ ~76px
│                                  ├── ③ Bloom    (wet)(thr)(rad)(int)  ─┤ ~76px
├─ B  │ SRC │ Preset name ────────── mini send ━━━━━━━━━━━━━━━━━━━━━━━━━┤ 22px
├─ BG │ SRC │ ...                                                       ┤ 22px
└─ PV │ SRC │ ...                                                       ┘ 22px
```

- ~76px sub-rows → ~44px knobs **only if** the 20px FxParamCell under each knob is dropped
  (param name as caption, value on hover/in-knob, paging moved to the sub-row's left side).
  Keeping the cell → ~20px knobs, too small.
- Strips can't hold knobs: collapsed decks lose their macro knobs while expanded.
- Master tab: expanding Master FX eats the row carrying the crossfader → crossfader moves to
  the left column.
- Cost: medium–large (new strip row type, per-group knob sizing, new param-cell layout).

## Changes needed for either option

1. **Direct-param knobs** for non-focused sub-rows — only the focused one drives
   `Macro/<bank>/knob_N`; the other two bind straight to their params (like Deep Edit sliders).
2. **Per-slot page state** — `FxChain.focusParamPage` is a single value today.
3. **Click a sub-row = focus it** — focus becomes "which sub-row hardware drives", not a mode.
4. **Per-group knob diameter** instead of grid-wide min (also fixes the ~20px issue above).

## Suggested path if revived

Prototype Option A behind a toggle (most machinery exists), use it for a session, then either
promote to Option B or drop it. Item 4 is worth keeping either way.
