# Modulator range: Min/Max + Base/Amount, with a roll-space choice

Status: IMPLEMENTED 2026-11-18, tests pass, uncommitted; checked in a draft tour render (slide, Ends/Base·Amt rows, pin); not seen by the user. v1.0 polish (no new engine behaviour).
Deviation: MIDI modulators get the range row but stay unbindable (DECISIONS item 19.2), so no Base/Amount captions there.

## Decisions (user, 2026-11-18)

- Storage stays `depth` + `dcOffset` (the evaluator, the web port and the golden vectors do not change).
- No display-mode toggle. The range row keeps Min/Max handles; Base/Amount behaviour comes from a gesture
  and from Learn targets. A binding targets a property, never what the screen shows, so no binding migrates.
- User-facing names: **Base** (= `dcOffset`: centre for bipolar sources, floor for unipolar ones) and
  **Amount** (= `depth`). Internal property names `dcOffset` / `depth` stay, so saved MIDI/OSC/macro bindings keep working.
- **Roll space is per modulator**: `ENDS` (roll Min and Max separately) or `BASE_AMOUNT` (roll Base and Amount,
  each pinnable, e.g. pin Base, roll Amount).
- Edges: **clamp in the UI** (sliding the range stops at ±1 with the span locked), **overshoot from MIDI/OSC/macros**
  (Base/Amount written unclamped; the final parameter clamps as it does today).

## Why the roll-space change is needed

With `lfoMinMaxMode = true`, `dcOffsetMin/Max` hold the Min handle's roll range and `depthMin/Max` the Max handle's
(Lfo1Section, AudioModulatorSection, `CvModulator.randomizeActiveValues`). With it false the same fields mean
Base/Amount ranges. One set of fields with two meanings is the bug; give each meaning its own fields.

## Model (CvModulator + ModulatorDto)

- `enum RollSpace { ENDS, BASE_AMOUNT }`, field `rollSpace`, default `ENDS` (today's behaviour after any UI edit).
- New fields for ENDS: `lfoMinRollMin/Max`, `lfoMaxRollMin/Max`, `randomizeLfoMin`, `randomizeLfoMax`.
- `depthMin/Max` + `randomizeDepth` and `dcOffsetMin/Max` + `randomizeDcOffset` keep only their natural meaning
  (Amount / Base ranges), used when `rollSpace == BASE_AMOUNT`.
- Drop `lfoMinMaxMode` after migration (keep reading it from old JSON).
- Migration on load: `lfoMinMaxMode == true` → `ENDS`, move `dcOffset*` ranges → `lfoMinRoll*`, `depth*` ranges →
  `lfoMaxRoll*`, flags likewise, then collapse `dcOffsetMin/Max` / `depthMin/Max` to the current values.
  `false` → `BASE_AMOUNT`, fields as they are. Cover presets, FX chains, decks, sessions, clipboard, morph snapshots.
- ENDS rolls may produce Min > Max (inverted swing); keep that, it matches today.

## Code touch list

- `CvModulator`: `randomizeActiveValues`, `randomizeDepth`, `randomizeDcOffset`, new `randomizeLfoMin/Max`, `withLfoRange`.
- `PresetModels.kt`: DTO fields, equals/hashCode, `isDcRandom` checks, both converters.
- `MorphState.kt`: lerp `depth`/`dcOffset` when any roll in the active space is on (ENDS rolls change both).
- `PresetDependencyAnalyzer.isModulatorRandomized` (both overloads), `ModulatorHeaderRow` dice, `StockDefaults`
  and the stock-content checks (`scripts/shipped_source_hashes.py` if bundled files change).
- `ModulatorPropertyAccessor`: add new roll fields; labels "DC Offset" → "Base", "Depth" → "Amount" where it is the
  range pair; Base/Amount writes unclamped.
- UI (`CustomRangeSlider.drawMinMaxRangeSlider`, `Lfo1Section`, `AudioModulatorSection`):
  - drag the bar between the handles (or Shift-drag a handle) = slide, span locked, clamped at ±1;
  - right-click Learn on the range row offers Min, Max, Base, Amount;
  - roll-space chooser next to the dice: `[Ends | Base·Amt]`; in Base·Amt the roll ranges edit on a sub-row
    (Base roll lo–hi, Amount roll lo–hi) with a lock per row.
- `SeqSection` / `MidiModulatorSection` already show Depth/Offset sliders: rename to Amount/Base, roll space fixed
  to BASE_AMOUNT there (open question below).
- Hardware feedback: Twister rings / OSC feedback for derived targets (Min/Max/Base/Amount) must refresh when any
  of the four changes from another control. Check the feedback path reads through the accessor.
- Web: `web/evaluator.js` reads only `depth`/`dcOffset`. Check the web TV does no modulator rolls; nothing to port if so.

## Tests

- Migration round-trip for both `lfoMinMaxMode` values; old JSON loads, new JSON omits the flag.
- Roll tests: ENDS rolls each end in its range; BASE_AMOUNT with Base pinned never moves `dcOffset`.
- Accessor: Base/Amount overshoot unclamped; Min/Max still round-trip through `withLfoRange` for both polarities.
- Golden web vectors unchanged (`WebEvaluatorVectorsTest`).

## Docs ("done" = all of these)

- `docs/user_guide/modulation.md` (Depth and Offset section → Base/Amount + roll space),
  `performance_controls.md` (Learn targets list), developer modulation doc, tooltips, both release-notes files,
  `.planning/app-checklist.md` items for the gesture, Learn targets and roll space.

## Seq and MIDI (recommended 2026-11-18, awaiting user go)

- Give both the same Min/Max range row (one range UI on every modulator). MIDI CC most of all: knob 0 → Min,
  127 → Max is how every DAW maps a CC. Seq: steps drawn 0..1 (or -1..1) land between Min and Max.
- Migrated Seq/MIDI modulators start in `BASE_AMOUNT` roll space, since their existing ranges are Base/Amount ranges,
  so they roll exactly as before.
