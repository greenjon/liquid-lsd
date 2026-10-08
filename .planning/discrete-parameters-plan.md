# Discrete parameters: snap, label, tick (plan, 2026-10-07)

Status: PLAN, DECIDED 2026-10-07, not started. For a fresh agent to execute. Read `.planning/v1-polish-handoff.md` (working rules: both release-notes
files, docs, tooltips, `./gradlew test`, then `git checkout -- src/main/resources/docs`; no Claude attribution; commit only when asked).

## Decisions (owner, 2026-10-07)

- **Scope for v1.0: Tier 1 and Tier 2.** Tier 3 is v1.1.
- **Modulation stays allowed on stepped parameters; the value just snaps.** Do not exclude them from LFO/audio/CV sources. (The Max Points "non-modulatable"
  tooltip is wrong either way; fix the tooltip text, do not add enforcement.)
- **Add a `STEP` key to the ISF header for float inputs** that are really discrete. Do not convert them to `long`.
- **`isBgStyle` in `ValueParamSection` is dead code** (left from a custom background generator that predates ISF). Delete the branch as part of this work.

## Problem

Every parameter is a float `ModulatableParameter`. Mode, count and boolean inputs are quantized only at the consumer
(`value.toInt()` in `ISFFilter.kt:373/517`, `roundToInt` in `Mandala.kt` and a few UI sites). So:

- A knob, LFO, audio source or macro sweeps the range continuously and the shader sees 0,0,1,1,... (truncation, so a 0.99 reads as 0).
- The knob ring gives no hint that only N values exist, and shows no label (`LABELS` in the ISF is never read in Kotlin).
- `MeterType.DISCRETE` exists but is never constructed; it is only an alias of `ENDLESS` for ring drawing
  (`ParametersRenderer.kt` ~842, `MacroKnobWidget.kt` 87/367). No snapping, ticks or step count.
- `3d_elevation` `mode3D` works only because `ValueParamSection` hard-codes a combo (`MODE_3D_LABELS`); Max Points, Lobes, Recipe Select and
  Hue Sweep are each another one-off branch keyed on a `paramKey` suffix.

Owner's decision (2026-10-07): a long parameter on a continuous knob is acceptable for now, but stepping is wanted if it is cheap enough for v1.

## Inventory (from an Explore scan; the float-discrete list is keyword-grepped and may be incomplete)

Real discrete types (ISF `TYPE: long`, labels present but unused):
`3d_elevation.mode3D` (0..3, 4 labels), `3d_elevation.blendMode` (0..1), transitions `linear_crossfade.curveMode` (0..2), `film_burn.tintMode` (0..2).
No `int`/`bool`/`event` inputs exist in any stock filter, transition or source.

Declared `float` but discrete (no labels):
- Filters: `feedback.fbMode` (0..1), `video_strobe.strobeMode` (0..3), `polar_tunnel.symmetry` (1..8), `kaleidoscope.segments` (2..24),
  `halftone.mode` (0..2), `pixelate.latticeMode` (0..2), `wave_displace.rippleMode` (0..1), `thermal_scanner.mode` (0..2),
  `anamorphic_streak.colorMode` (0..2), `rgb_split.radialMode` / `dispersionMode` (0..1), `retro_crt.scanlineCount` (50..800).
- Sources: `dynamic_spiral` MaxPoints (100..2000) and ColorMethod (0..2); `domain_warp_fluid` Detail (1..5), PaletteMode (0..4);
  `gyroid_hyperspace` SurfaceType, WireframeMode, ColorMode; `chladni_cymatics` FrequencyM/N (1..16), FrequencyL (0..12), PlateShape, InvertMode, PaletteMode;
  `celestial_engine` Symmetries (3..24), ColorMode; `icosa_h3` SpikeMode, ColorMode (0..4).
- Mandala: `Lobes` (3..26, effective values depend on available petals), `Recipe Select` and `Hue Sweep` (normalized 0..1 whose step count depends on Lobes).
- App: `Deck.view3DMode` (0..4), `viewBlendMode`, `fbMode`, `fbKaleido` (1..12); Mixer trigger params (`queueNext`, `tapTempo`, `randDeckA`... 0..1 buttons).

Things the scan flagged that need checking before relying on them:
- `ISFVisualSource.createParameters` (~455) treats `long` like `float` (default 0.5, max 1.0, ignores `VALUES`); `ISFFilter` handles `VALUES`. Probably a real bug for any source with a `long` input (none shipped today).
- `Deck.view3DMode` comment lists modes in a different order than `MODE_3D_LABELS`; `Deck.fbMode` comment (Max/Difference) disagrees with `SourceDocRegistry:193` (additive/screen/multiply). Doc/code mismatch, not a snapping issue.
- `ParametersRenderer` tooltip calls Max Points "non-modulatable" but nothing enforces it; modulation stays allowed, so correct the text.

## Design

One concept: a parameter may declare a **step set**; everything downstream reads it.

1. `ModulatableParameter` gets an optional `steps: Int?` (number of values, spaced evenly across `minClamp..maxClamp`) and optional `labels: List<String>?`.
   Helper `snap(v)` rounds to the nearest step. `clone()` copies both. A parameter with `steps == null` behaves exactly as today.
2. **Snap where the value is consumed, not where it is written.** `evaluate()` snaps its final result (the one place all sources of change converge:
   knob, LFO, audio, CV, macro `tick`, MIDI, OSC, Metaknob, Morph). `baseValue` stays continuous so drags, soft takeover and
   morph interpolation stay smooth and need no changes at the many direct `baseValue =` writers
   (`MacroEngine.kt:384`, `MidiMappingManager.kt:355`, `OscMappingManager.kt:271`, `MorphState.kt:295`, `ValueParamSection` widgets, `randomizeBaseValue`).
   Rejected alternative: snapping in `set()`. It misses the direct writers and fights soft takeover.
3. Consumers: replace `toInt()` with `roundToInt()` in `ISFFilter` (373/517) and `ISFVisualSource` (198-210) so they stay correct if a step set is absent.
4. Populate from data:
   - ISF `long`/`int`: `steps = VALUES.size` (or `MAX-MIN+1`), `labels = LABELS`. `bool`: `steps = 2`. Build in `ISFFilter.kt` ~270-290 and fix `ISFVisualSource` ~455.
   - Float-declared discrete inputs cannot be auto-detected. Add an optional ISF header key (proposal: `"STEP": 1` on a float input, with `MIN`/`MAX`; ISF
     parsers ignore unknown keys, check `ISFInput` serialization config) and add it to the stock shaders in the list above. We do NOT change their TYPE to `long`
     (that changes the GLSL uniform type and risks breaking shader math).
   - `meta.json` source parameters (`VisualSourceRegistry.kt:332`): accept an optional `steps`. Mandala and `dynamic_spiral` are the candidates.
5. UI (reuse existing surfaces; no new panels):
   - Knob value readout (`MacroKnobWidget` ~127) and the Perform row show the label (or the integer) for the snapped value.
   - `MeterType.DISCRETE` gets a real ring: N tick marks, and draws the snapped position. Set it automatically when `steps != null`; it should NOT use the wrap-around
     (6 o'clock) layout, which is meant for angles. Current ENDLESS/DISCRETE aliasing in `drawKnobMeter` has to be split.
   - Keep the existing hand-built widgets (Max Points grid, Lobes stepper, Recipe prev/next) untouched for v1.
6. Controllers: no change in v1. The Twister ring (`ControllerFeedback.kt:136`) shows the snapped `value` automatically because it reads the evaluated value.

## Scope tiers

**Tier 1, candidate for v1.0 (small, central, low risk):** items 1, 2, 3 and the ISF `long`/`bool` part of 4, plus the readout label and DISCRETE ring ticks from 5.
Fixes `mode3D`, `blendMode`, `curveMode`, `tintMode`. Roughly: one parameter class change, two ISF constructors, one ring-drawing split, tests.

**Tier 2, v1.0 if Tier 1 goes smoothly (data-only):** the `STEP` header key and annotating the ~25 float-discrete stock inputs listed above. Needs a visual check of each shader's knob.
Risk is a wrong `MIN`/`MAX` in a shader making a value unreachable, so a test should assert each annotated input's `steps` matches `MAX-MIN+1`.

**Tier 3, v1.1:** generic combo/stepper in the VAL panel replacing the one-off branches (`is3DMode`, `isMaxPoints`, `isLobes`, `isRecipeSelect`, `isHueSweep`, dead `isBgStyle`);
Mandala dynamic step counts (Recipe/Hue depend on Lobes); `meta.json` `steps`; trigger params as momentary buttons; controller ring detents / knob push-click stepping;
the doc mismatches above.

## Tests

- `ModulatableParameter.snap`: boundaries, `steps = 2`, negative ranges, `steps == null` unchanged; `evaluate()` with an LFO crossing steps never returns a non-step value.
- ISF: `long` with `VALUES`/`LABELS` produces `steps`/`labels`; `bool` gives 2 steps; `ISFVisualSource` `long` default and range (the suspected bug).
- Stock audit (extend `StockFilterBindingAuditTest` or a sibling): every input with `TYPE long` or `STEP` has `steps == MAX-MIN+1`; the snapped default equals the authored default.
- Macro/MIDI/OSC write into a stepped parameter and the evaluated value is a step (one test through `MacroEngine.tick`).
- UI ring/readout: pure helper tests only (tick angles, label lookup); no GL.

## Docs (definition of done)

Both release-notes files, `docs/user_guide` (parameter editing / modulation pages: stepped parameters, labels), `docs/developer/ui.md` (DISCRETE meter), the ISF authoring doc
(the `STEP` key, if added), tooltips on stepped knobs ("N choices"), and a `DECISIONS.md` entry only if "snap in `evaluate()`, keep `baseValue` continuous" is adopted as a rule.

## Execution order (suggested)

1. `ModulatableParameter`: `steps`, `labels`, `snap()`, snap at the end of `evaluate()` (both the no-modulator early return and the modulated path), `clone()`; unit tests.
2. ISF: `long`/`int`/`bool` -> steps/labels in `ISFFilter` (~270-290); fix `ISFVisualSource.createParameters` (~455) `long` handling; `toInt()` -> `roundToInt()`; tests.
3. `STEP` key: add an optional field to `ISFInput` (`ISFModels.kt`), check the JSON config tolerates it and that other ISF tools ignoring it is fine, honor it in both
   constructors (`steps = (MAX-MIN)/STEP + 1`, must be a whole number, else ignore with a log warning). Document it in the ISF authoring doc.
4. Meter/readout: set `MeterType.DISCRETE` automatically when `steps != null`; split DISCRETE from ENDLESS in `drawKnobMeter` (tick marks, no 6 o'clock wrap) and
   `MacroKnobWidget` 87/367; label or integer readout; check `PerfKnobSpec` meter resolution and the `drawMacroRangeArcs` early return (it skips DISCRETE; stepped params
   with macro targets should still show their arc, so narrow that skip to ENDLESS only).
5. Annotate the stock inputs (Tier 2), one shader at a time, with the stock-audit test asserting `steps == MAX-MIN+1` and snapped default == authored default.
   Run the app and look at each knob if possible; otherwise flag the unseen ones in the handoff.
6. Delete the dead `isBgStyle` branch; fix the Max Points tooltip wording.
7. Docs, tooltips ("N choices"), both release-notes files, `./gradlew test`.

## Edge cases to handle

- `retro_crt.scanlineCount` (50..800) and `dynamic_spiral` MaxPoints (100..2000) are counts with hundreds of values: use a `STEP` (for example 10 and 50) rather than every
  integer, or leave them unstepped. Ticks must be suppressed above roughly 16 steps (draw none; the snap still applies).
- Existing `ValueParamSection` widgets for Max Points, Lobes, Recipe Select, Hue Sweep and `mode3D` keep working unchanged in v1; make sure `steps` snapping does not fight them
  (Lobes/Recipe/Hue are normalized and not annotated in v1).
- Preset/session load: a saved `baseValue` that is not on a step is fine (it snaps when evaluated); do not rewrite saved files.
- Morph between presets interpolates `baseValue` continuously; the evaluated value snaps midway. That is acceptable and needs no code.
- `randomizeBaseValue` picks a continuous value that then snaps; acceptable.

## Files expected to change (Tier 1 + 2)

`parameters/ModulatableParameter.kt`, `rendering/isf/ISFFilter.kt`, `rendering/isf/ISFVisualSource.kt`, `rendering/isf/ISFModels.kt` (STEP),
`ui/ParametersRenderer.kt` (`drawKnobMeter`), `ui/MacroKnobWidget.kt`, `ui/PerfKnobSpec.kt` (meter type resolution),
the annotated `src/main/resources/default_*/*.fs`, tests as above.
