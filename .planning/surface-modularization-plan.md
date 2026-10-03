# Surface modularization plan (v1.1)

Drafted 2026-10-03. **v1.1 work: do not start before the v1.0 release** (feature freeze, see memory `project_v1_release_scope`).
Goal: keep the rack metaphor (a screen canvas of rack units) but stop hardcoding the Twister's 4x4 shape, so other hardware
(first target: Stanton SCS.3m) can be supported by data (controller profile + perform pages) instead of code changes.

## Decisions so far

- Users define a **surface layout + mapping**, not their own UI. Pluggable UI units (scripting / stable Kotlin API) are out of scope.
- Two things are separated that are currently fused: the **on-screen canvas** (rows of units, unchanged) and the **control surface**
  (device-declared geometry that a page is projected onto).
- Milestone 1 (double-height Twister unit) is the cheap proof of the unit-size model; milestone 2 (SCS.3m) proves the surface model.

## Facts that drive the design (2026-10-03 Explore pass; paths under `src/main/kotlin/llm/slop/liquidlsd/`)

- No rack-unit type with a size exists. `ui/rack/RackUnit.kt` is only chevron + Deep Edit frame. A row is `RowDescriptor`
  (`ui/PerfRows.kt`), implicitly 4 knobs from `MacroBank.knobs[knobOffset..+3]`; `PerfRows.CATALOG` is code, not data.
- The 4x4 shape is hardcoded in: `KnobCommands.KNOB_COUNT = 16`, `PerformPages.COLS = 4` and `resolveInto` (`rowIdx * 4 + col`,
  `min(rows.size, KNOB_COUNT / COLS)`), `PerfPageDef.ROWS = 4` (`ui/PerfPageStore.kt`, also `problems()`), `PerfKnobResolver.resolve`
  (`0 until 4`, `ui/PerfKnobSpec.kt`), `PerfRowGeometry` (`/ 4f`, one uniform `rowH`), widget ids in `PerformanceMatrixPanel`
  (`tabIdx * 16 + rowIdx`, `* 4 + col`), `MIN_ROW_H = 68f`.
- Profile layer is already fairly generic (`control/ControllerProfile.kt`): `InputKind` ENCODER/BUTTON/FADER/MODIFIER/BANK_SWITCH, input
  groups with `count`/`ccs`/`bankStride`, `banks.count` may be 1 with no `switch`, per-bank overrides, wildcards, user files override
  built-ins by id. A FADER executes as SCALAR (`rawValue / 127f`) in `ControllerRuntime`.
- Gaps: no fader-addressing commands (`fader.N`) and no non-knob slot in `KnobSurface`/`KnobLight`/`PerformPages`; `OutputConfig.knobs`
  addresses one ENCODER group only (no button-LED / fader model); navigation hardwires knob 1 as browse cursor and `side.1..3` + `shift`
  (`control/NavCommands.kt`, `NavSurface`, `PerformSurface.lastTouchedKnob`); `banks.pages` ids are not validated against
  `PerfPageStore` at load.
- Layering (`LayerDependencyTest`): `midi/` and `control/` must not reference `ui/`; the UI reaches `control/` only via
  `KnobSurface`/`KnobLightSource`/`NavSurface`. Keep that: surface geometry must live in `control/` (or a neutral package) and be read by `ui/`.

## Milestone 1: unit sizes, starting with a double-height Twister unit

Why it works on the Twister: pages are flattened row-major, so a unit owning two consecutive row slots owns 8 consecutive knobs
(1-8 for the first double unit). No hardware mapping change; this is UI-layer work.

1. `RowDescriptor` gets `span` (1 or 2; default 1). New catalog entry, e.g. `deck.A.src+fx` (and B/BG/PV), `span = 2`, 8 knobs.
2. `PerfPageDef.problems()`: "exactly 4 rows" becomes "sum of catalog spans == 4". `RowPlacement` stays `{row}`; span comes from the catalog.
3. `PerformanceMatrixPanel`: iterate by cumulative span, give a double unit `2 * rowH` (still `>= MIN_ROW_H` per slot).
4. `PerfRowGeometry` takes the unit's column and sub-row counts (4 x 2 for a double unit) instead of the fixed 4 columns, so a double unit
   can share a title/header and use the box freely (this is the point of the feature).
5. `PerfKnobResolver.resolve` returns 8 `KnobSpec`s; `KnobSpec.col` becomes a slot index 0..7 (or row+col).
6. Widget ids keyed by slot index so two units of the same deck on one page stay unique (already a rule in `perform-pages-plan.md`).
7. `PerformPagesPanel`: show a double unit as two slots, never splittable.
8. Keep SRC = top half and FX = bottom half, each reusing its existing resolver; the unit is a layout container, not a new control
   scheme. Focus-mode lookup (`perform-pages-plan.md`) must treat a double unit as a variant for both halves.
9. Tests: `PerfRowLayoutTest`, `PerformSurfaceTest`, page-packing tests, distinct-LED-hue test (8 lights for one unit).

Open: LED colours for the 8 knobs (top half = SRC hue, bottom = FX shade, per the existing rule); whether the double unit has its own
starting-toggle semantics (probably none: both halves are always visible).

## Milestone 2: SCS.3m (and the general surface model)

Source: Mixxx 2.5 manual, https://manual.mixxx.org/2.5/gl/hardware/controllers/stanton_scs3m . It describes functions, **not** the MIDI
map. Before designing the schema, get the real map (the Mixxx mapping script `Stanton-SCS3m-scripts.js` / `.midi.xml`, or capture with
MIDI monitor on the device).

What the Mixxx page says about the device:
- Two identical sides (one per deck), each: a top pitch strip with edge-touch pitch bend, three vertical sliders (EQ or FX depending on
  mode), a volume slider; a central crossfader strip (also needle-drop search).
- Four soft buttons per side (rewind, fast-forward, cue, play), headphone toggles, EQ/FX mode buttons, deck-select buttons (A/C left,
  B/D right) which double as modifiers, a Master button (left = headphone, right = main output).
- Two LED meter strips (pre-fader; stereo main when Master held). The page says "LEDs calibrated to the VU meters on-screen".
- Modes: EQ (low/mid/high + filter), FX (four effect chains; sliders = first three params + dry/wet), Master. Reset is noted as not working in FX and Master modes.
- **No encoders, no banks.** All continuous controls are absolute (sliders/strips). Mode buttons play the role the Twister's banks play.

Design consequences (these are the actual new work):
1. **Surface geometry in the profile** (step A). Add a `surface` block, e.g. `{slots: [...]}` where a slot has a kind (`encoder`,
   `fader`, `button`) and position, not just `cols x rows`. SCS.3m is ~8 faders/strips plus buttons, which is not a grid. Replace
   `KNOB_COUNT`/`COLS`/`ROWS` with this value everywhere listed in "Facts". Keep the Twister profile as a 4x4 encoder surface.
2. **Absolute controls and soft takeover** (step B). A fader has a physical position unrelated to the parameter value after a page /
   mode change; the Twister's rings hide this because encoders are relative-ish with feedback. Needs pickup (catch) or jump policy per
   binding, and visual indication on screen. This is the biggest design question of the milestone.
3. **Fader slots in the page model** (step C): `fader.N` commands, `KnobSurface` generalized to "slot surface" (name TBD), slot kinds in `PerformPages`.
4. **Pages projected onto a smaller surface** (step D): with ~3 sliders per side, a 16-knob page does not fit. Define the projection:
   either the profile maps a subset of page slots (e.g. the first 3 columns of a unit) or paginates by mode button (EQ/FX/Master = bank-like).
   Reuse `banks.pages` semantics: mode buttons act as `BANK_SWITCH` inputs.
5. **Navigation roles** (step E): replace hardwired knob-1-cursor and `side.1..3` with bindable roles (`nav.cursor`, `nav.confirm`,
   `nav.back`...) so the SCS.3m can use a strip/soft buttons. Touch strips can act as scroll for browse.
6. **Feedback model** (step F): `OutputConfig` gains button LEDs and meter strips (value -> LED bar), not just encoder ring/colour.
   Rate-limit like `minIntervalMs`.
7. **Validation at load** (step G): page ids named by `banks.pages` exist; a page fits its surface (warn, don't fail); every bound
   command exists. Surface problems in the profile UI (MIDI phase 5).
8. **Docs** (step H): "Make your own controller profile" guide with the schema, the SCS.3m profile as the worked example, plus the
   usual release-notes/user_guide/dev docs/tooltips per `feedback_docs_release_notes_completeness`.

## Order and risks

- Do milestone 1 first (low risk, proves unit sizes, touches only `ui/`). Then A, C, B, D, E, F, G, H.
- Step A touches the perform hot path (`KnobCommands`, `PerformSurface`, `PerformPages.resolveInto`, `PerfRowGeometry`); land it behind
  the existing Twister tests (`PerformSurfaceTest`, `ControllerProfileTest`, `ControllerRuntimeTest`, `ControllerManagerFeedbackTest`) with
  the Twister profile producing byte-identical behaviour before adding any new device.
- Profile schema versioning: bump `ControllerProfile.version` and add a `migrate` step; old profiles imply a 4x4 encoder surface.
- Do not design the `surface` schema from the Mixxx page alone: it lacks CC/note numbers, touch-strip resolution and LED protocol.
- Hardware needed to validate steps B, D, F: a real SCS.3m. Without it, the schema is speculative.
- Perform-pages invariants still hold: pinned rows do not read/write shared deck mode; ids are per slot; no variant feeds `PerfRowGeometry`.
