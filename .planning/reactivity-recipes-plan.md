# Reactivity recipes + global reactivity amount (2026-10-25)

**Status 2026-10-27:** AGC, loader and 33 source recipes are DONE and committed. Open: FX and transition recipes (step 6), web AGC port, Fluid Ink idea. See ROADMAP.md. The format below is as built, with `dcOffset` allowed.

**SUPERSEDED 2026-10-27 (user):** the `recipe` flag and the per-modulator Reactivity amount were REMOVED (two invisible classes of modulator was not worth the complexity). Replaced by **Audio Response** (`AudioEngine.response`, 0..2): one multiplier on the eight audio CV signals after AGC, applying to every audio modulator. LFOs are not scaled. The "Reactivity amount semantics" section and the web `ctx.reactivity` notes below are history.

Goal: every shipped source, FX and transition ships hand-curated audio/beat response. Per parameter, an ordered
stack of modulators: ONE active, alternates present but `bypassed`. A global "reactivity amount" scales the
active recipe modulators.

## Decisions (user)
- Recipes are hand-curated per shader; maximize expressiveness.
- Alternates default to bypassed.
- Global reactivity amount persists as an app setting, NOT in presets. Range 0..2, default 1.

## Findings from code read
- `ModulatableParameter.evaluate()` (parameters/ModulatableParameter.kt ~190): per modulator
  `rawModAmount = cv*depth + dcOffset`, then ADD (scaled by range) / MUL / SCALE (`1 - depth + amount`).
  Bypassed modulators are skipped. Web mirror: web/evaluator.js ~310-345.
- Audio CV is normalized by fixed divisors (`/0.25` amp/bass/mid/high, flux `/0.05`-`0.1`) in AudioEngine.kt ~724,
  after a user `inputGain`. NO AGC. Recipes are therefore tuned against a nominal level; a quiet/loud input
  shifts every recipe. Separate concern from reactivity amount; consider an AGC/auto-trim as its own task.
- `CvModulator` has no way to mark "this came from a recipe" -> need `recipe: Boolean = false` (serialized,
  defaulted, so old files load).
- `FxDefaultDto.parameters` is `Map<String, Float>` (cannot hold modulators); transitions have no defaults store.

## Reactivity amount semantics
- Scale both the `cv*depth` term and `dcOffset` of modulators with `recipe == true`, so r=0 gives the base value
  exactly. (2026-10-27: after the polarity change every bipolar source swings dcOffset +- depth on any parameter, so
  an LFO on a 0..1 parameter at its minimum uses depth = dcOffset = d/2 to swing upward from the set value;
  ReactivityRecipesTest.lfoSwingsFitTheirParameters keeps every swing inside the parameter's range.) For SCALE use `depth' = depth*r` in both places (`1 - depth' + cv*depth'`).
- User-authored and MIDI/macro modulators unaffected.
- Global scalar lives next to other settings (UITheme.settings); evaluator receives it via ctx. Web: add
  `reactivity` to evaluator ctx, update sync_manifest, golden vectors at r = 0, 1, 2.
- Surface: GLOBAL macro bank / Clock row (TBD with user). Per-deck / per-slot trims later.

## Recipe format (proposed)
Sidecar JSON, one per shader, keyed by shader id (sources/fx/transitions share the format):
`library/reactivity/<kind>/<id>.json` (shipped copies under resources, same self-heal + hash list as
default_sources).

```json
{
  "version": 1,
  "params": {
    "<PARAM_NAME>": {
      "note": "why: e.g. kick punches the scale",
      "stack": [
        { "id": "pulse",  "active": true,  "source": "audio_bass", "op": "ADD", "depth": 0.35,
          "follower": "PUNCHY", "attackMs": 5, "decayMs": 180 },
        { "id": "drift",  "active": false, "source": "lfo", "op": "ADD", "depth": 0.2,
          "waveform": "SINE", "subdivision": 0.25 }
      ]
    }
  }
}
```
Stack entries map 1:1 onto `CvModulator` fields (any field may appear); `active:false` -> `bypassed=true`;
all get `recipe=true`. Only waveforms/sources already in the web evaluator (no new ones without a two-sided change).

## Recipe vocabulary (palette to draw on, not a limit)
Pulse (bass follower, punchy -> scale/size), Drift (slow beat LFO -> rotation/phase), Shimmer (high follower
-> brightness/detail), Sweep (beatPhase/saw -> position), Swell (amp smooth -> intensity), Flux hit
(flux_* -> flashes/jumps), Stutter (square LFO, fast division), Random walk (S&H on beat).

## Steps
1. `recipe` flag + reactivity scalar in Kotlin evaluate(); settings persistence; tests.
2. Web evaluator mirror + sync manifest + golden vectors.
3. Recipe loader/applier (sources first), self-heal copy, hash list.
4. UI: reactivity control; recipe modulators visibly tagged; "Reset to recipe".
5. Batch 1 of ~10-15 sources hand-authored; user reviews by eye.
6. FX store extension (modulators in FxDefaultDto or recipe overlay), transitions store.
7. Docs: both release-notes files, user guide, dev docs, tooltips, DECISIONS.md.
