# Performance Row Layout Stability — Implementation Plan

2026-09-27. Goal: on every Performance row, knobs, captions/cells and side buttons keep **exactly** the same size and
position regardless of what the knobs currently drive (source macros, FX chain group mode, FX focus mode, Master
MIX/FX), which rows on the tab are in which mode, and which deck the row belongs to. Mode may change *content*, never
*geometry*.

All paths below are relative to `src/main/kotlin/llm/slop/liquidlsd/`.

---

## Root causes (from the 2026-09-27 review)

1. **Geometry depends on mode.** `ui/PerformanceMatrixPanel.kt` budgets "text below knob" per mode — SRC
   `captionH + 7` (~19px) vs FX `7 + max(captionH, FxSlotCell.HEIGHT)` (~27px) (:410–416).
   - The diameter is the minimum over `layoutRows` (:417), which already has SRC/FX substituted in (:136). So toggling
     *any* deck to FX shrinks *every* knob on the tab by ~8px when the row height is the limit (the norm at 720p).
   - `knobTopY` centers on the per-mode `totalWidgetH` (:582–585), so the toggled row's knobs also shift ~4px vertically.
2. **Caption font mismatch, no truncation.** The layout budgets CAPTION (12px). `MacroKnobWidget` draws the label in
   BODY (14px) (:287–289), and never truncates it. The FX cells truncate with an ellipsis (`FxSlotCell.truncate`,
   `FxParamCell.truncateName`, two copies of the same function).
3. **Reserved side width ≠ drawn side width.** `deckRow1W` (:384) hard-codes `28+4+70+…+22+…+78`. What's actually
   drawn in `ui/PerformanceDeckControls.kt`:
   - generator badge: 74px (:90)
   - eject / dice buttons: `ctrlH` (21 or 24px)
   - queue nav: ~83px (:277, :339)
   - PV's badge: 60px (:534)

   That's ~13px over budget, which eats the 12px gutter before the knobs.
4. **Mode predicate duplicated three ways.** `deckRowMode[tag] == "FX" || activeDeckXSubTab == "FX"` appears in
   `rowDescriptorForModule` (:169…), `substitutedRowsForTab` (:216…), and a differently-shaped variant at
   `PerformanceDeckControls.kt:68`. FX focus mode is a third state that lives on `FxChain`.
5. **Per-knob content decided inline** in one ~250-line loop (`showKnobLabel`, `captionBlockH`, `sideSlotIdx`,
   `focusedParamEntry`, `cellY`; :650–870). Every mode tweak has to keep all of them consistent by hand.

The label-sync concern raised in the review turned out to be unfounded. Focus, param paging and every `FxOps`
mutation already route through `FxMacroSync` (`focusSlot`, `stepParamPage`, `FxOps.resync`). No work is needed there.

---

## Step 1 — Pure row geometry (`ui/PerfRowGeometry.kt`, new)

A plain Kotlin class with no ImGui calls, so it's unit-testable.

```kotlin
internal data class PerfRowMetrics(         // font/measure inputs, captured once per frame
    val bodyLineH: Float, val captionLineH: Float, val ctrlH: Float, val stackGap: Float
)
internal data class KnobSlot(               // one knob column, row-relative coordinates
    val knobX: Float, val knobY: Float, val diameter: Float,
    val stripX: Float, val stripY: Float, val stripW: Float, val stripH: Float,  // under-knob strip
    val sideBtnX: Float, val sideTopY: Float, val sideBotY: Float, val sideBtnSize: Float,
    val cardX1: Float, val cardY1: Float, val cardX2: Float, val cardY2: Float  // selection card (collapsed)
)
internal class PerfRowGeometry(gridW: Float, rowH: Float, metrics: PerfRowMetrics, leftW: Float, rightW: Float) {
    val diameter: Float
    val knobColW: Float
    val knobClusterStartX: Float
    fun slot(col: Int): KnobSlot            // offsets relative to row top / grid left
}
```

Rules:
- **One under-knob strip for every mode:**
  `STRIP_H = max(bodyLineH, FxSlotCell.HEIGHT, FxParamCell.HEIGHT)`.
  - `textBelowH = 3 + STRIP_H + 4`, used for *all* rows.
  - `layoutRows` no longer affects diameter. Only the row count and `rowH` do.
- **Knob vertical position** comes from the fixed `diameter + textBelowH`, centered in the row once. It's the same for
  every row on both tabs.
- **Side buttons are reserved on both sides of the knob** so it stays centered and never collides with the neighboring
  column: `diamByWidth = knobColW − 2·(sideBtnSize + 4)`. Today's rule is `knobColW − 12`. At 1280 wide the columns
  are ~180px, so this never binds in practice.
- The Deep Edit extras (value line + Learn button, `expandedExtraH`) stay *below* the strip, as today.
- `leftW` / `rightW` come from Step 4's metrics, maxed over both tabs, as today.

`PerformanceMatrixPanel.drawMatrix` constructs one `PerfRowGeometry` per frame and reads every coordinate from it.
Delete from `drawMatrix`:
- `baseTextBelowH`, `fxTextBelowH`, `effectiveTextBelowH`
- the per-group `diamByHeight` loop
- `captionBlockH`, the inline card math, the inline side-button math

`draw()` still passes `layoutRows`, but only for the row count (rename its use accordingly). `substitutedRowsForTab`
stays for the *visible* rows.

## Step 2 — One mode accessor (`ui/PerformanceUiContext.kt`)

```kotlin
internal enum class RowKnobMode { SOURCE, MIX, FX_GROUP, FX_FOCUS }
fun deckKnobMode(tag: String, ps: ParametersState, mixer: Mixer): RowKnobMode
fun masterKnobMode(ps: ParametersState, mixer: Mixer): RowKnobMode
fun isDeckRowFx(tag: String, ps: ParametersState): Boolean   // the single SRC/FX predicate
```

- Replace the four per-deck branches in `rowDescriptorForModule` and `substitutedRowsForTab` with a loop over
  tags that calls `isDeckRowFx`. Replace `PerformanceDeckControls.kt:68` with the same call.
- Keep the stored state as-is (`deckRowMode` + deck sub-tab, OR'd). This step only removes the copies; it doesn't
  change behavior.

## Step 3 — Knob content model (`ui/PerfKnobSpec.kt`, new)

```kotlin
internal sealed interface UnderKnob {
    data class Label(val text: String) : UnderKnob
    data class SlotCell(val slotIndex: Int) : UnderKnob
    data class ParamCell(val name: String?, val param: ModulatableParameter?) : UnderKnob
}
internal sealed interface SideButtons {
    data object None : SideButtons
    data class LinkAndBypass(val slotIndex: Int) : SideButtons  // FX group mode, knobs 2–4
    data class Bypass(val slotIndex: Int) : SideButtons         // FX focus mode, knob 1
    data class Reset(val name: String, val param: ModulatableParameter) : SideButtons // focus mode, knobs 2–4
}
internal data class KnobSpec(val control: MacroControl, val under: UnderKnob, val side: SideButtons, val valueOverlay: String?)

internal object PerfKnobResolver {
    fun resolve(mode: RowKnobMode, bank: MacroBank, knobOffset: Int, chain: FxChain?): List<KnobSpec>  // size ≤ 4
}
```

The resolver takes over the inline logic from `drawMatrix` (:650–870): `focusedParamEntry`, `sideSlotIdx`,
`showKnobLabel`, `fxValueOverlay`, and the cell-kind branches. It's pure, so each mode can be tested directly.

## Step 4 — Rendering against geometry + spec

- **`MacroKnobWidget.draw`**: the matrix always passes `showLabel = false` (and `showValue` only when expanded, as
  today), so the widget draws only the face. `MacroPanel`'s call sites are unchanged.
  - The widget's cursor-advance math (:330–336) must keep working for `MacroPanel`. The matrix ignores it because it
    positions everything absolutely.
- **New `ui/TextFit.kt`**: `ellipsize(text, maxW)`, measured in the current font. Replaces both `FxSlotCell.truncate`
  and `FxParamCell.truncateName`.
- **New `drawStripLabel(session, text, x, y, w, h, color)`**: renders `UnderKnob.Label` inside the strip, in BODY,
  ellipsized and vertically centered at the same baseline as the cells' text. The full label stays in the knob tooltip
  (it's already there).
- **Matrix per-knob loop becomes** geometry → spec → knob face → strip (label | slot cell | param cell) → side buttons
  → selection card / Learn button.
  - Move the link/bypass button body (:672–700) into `FxSlotCell.drawLinkButton(...)`, next to the existing
    `drawBypassButton`.
  - `FxSlotCell.draw` / `FxParamCell.draw` take the strip rectangle. Assert `h == STRIP_H` in debug, or just drop their
    local `HEIGHT`, and have `PerfRowGeometry` own the constant.
- **New `DeckRowMetrics`** object (in `PerformanceDeckControls.kt` or its own file) holds:
  - `MODE_PILL_W`, `GEN_BADGE_W`, `GAP`, `NAV_BTN_W(ctrlH)`, `QUEUE_IDX_W`, `PV_BADGE_W`
  - `fun row1Width(ctrlH, comboW, randomization): Float`, which returns the max over the A/B queue-nav tail and the
    PV badge, so all decks reserve the same width.

  `drawDeckRowLeftControls` reads every width from it. `PerformanceMatrixPanel` computes `deckRow1W` from it (at the
  non-compact `CTRL_H`, the larger case). Do the same for the MASTER-tab left controls if they hard-code a sum.
  `masterRowW` is already derived, so check `PerformanceMasterControls` / `PerformanceClockControls` for constants that
  exceed it.

## Step 5 — Tests (`src/test/kotlin/llm/slop/liquidlsd/ui/PerfRowLayoutTest.kt`, new)

- **Geometry invariance.** For `gridW ∈ {1280, 1600, 1920}` and `rowH ∈ {68, 74, 95, 140}`:
  - `PerfRowGeometry` gives identical `diameter`, `knobY` and strip rectangles whether it's built from DECKS or MASTER
    rows.
  - It never sees the mode at all. This is enforced by the signature; the test documents it.
- **Strip holds every content kind.** `STRIP_H ≥ bodyLineH` and `≥ FxSlotCell/FxParamCell` height.
- **No overlap.**
  - Side buttons of column *n* don't reach column *n−1*.
  - `leftW` ends before `knobClusterStartX`.
  - `DeckRowMetrics.row1Width` equals the sum of the widths the drawing code places, for each deck kind and
    `randomization` on/off.
- **Resolver.** One test per mode:
  - SOURCE → 4× `Label`, no side buttons.
  - FX_GROUP → `Label` + 3× `SlotCell` with `LinkAndBypass`.
  - FX_FOCUS → `SlotCell(focused)` + `Bypass`, then 3× `ParamCell` + `Reset`, with paging offsets.
  - An empty slot / missing param → `ParamCell(null, null)`, which occupies the same strip.
- **Mode accessor.** Table test over `deckRowMode` × sub-tab.

## Step 6 — Verify in the running app

Headless run using the Xvfb recipe (copied dir, `--no-audio`) at 1280×720 and 1920×1080. Screenshot each state:
- DECKS tab: all SRC; A in FX group; A in FX focus; A+B FX
- MASTER tab: MIX; FX
- Deep Edit open on Deck A (SRC and FX)

Diff the knob regions to confirm pixel-identical knob positions across states.

## Step 7 — Docs

- `docs/developer/ui.md`: add a short "Performance row layout contract" section covering fixed geometry, content via
  `KnobSpec`, and `DeckRowMetrics` as the single width source.
- Release notes: `RELEASE_NOTES.md`, `docs/release_notes.md`, `src/main/resources/docs/release_notes/index.html`.
- `docs/user_guide/performance_controls.md`: only if the visible behavior is worth a line (long knob names now
  ellipsize; the full name is in the tooltip).

---

## Work order / commits

1. Steps 2 + 3 (accessor + resolver + their tests). This is a pure refactor with no visual change.
2. Steps 1 + 4 (geometry + rendering + `DeckRowMetrics`) with the Step 5 layout tests. This is the visible fix.
3. Step 6 verification, then Step 7 docs.

## Expected visible changes

- Knobs no longer resize or jump when any row toggles SRC/FX/focus.
- At 720p, knobs settle ~3–4px smaller than today's SRC-only size, because the strip is always FX-height.
- Knob captions move from 12px budgeted / 14px drawn to a consistent 14px strip, ellipsized.
- Side controls stop crowding the first knob column.

## Open questions

- **Under-knob text size:** BODY (14px, matches the FX cells; recommended) or CAPTION (12px, gains ~2px of knob)?
- **Link button placement:** should the Super Knob link button stay stacked left of the knob with bypass, or move into
  the slot cell? The plan keeps it where it is.
