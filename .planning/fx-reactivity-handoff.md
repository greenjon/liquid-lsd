# Handoff: audio response for FX (filters) — 2026-10-27

> **Status 2026-10-27: done for all 27 filters, awaiting the user's in-app check** (`.planning/app-checklist.md` 9b,
> FX batches 1 and 2). Decisions taken: a response on the Metaknob's input is `MUL` (knob at zero stays off), `ADD`
> only on inputs the main amount gates; Invert, Luma Key and Video Strobe ship bypassed responses only; a shipped
> filter file is ignored when the shader lacks any input it names (keeps it off the `vortex_swirl` transition).
> **Done 2026-10-27:** every stock FX chain (`defaults/fx_chains/*.lsdfxchain`, web copies) carries the shipped
> responses and random ranges (`StockDefaultsTest.shippedFxChainsCarryTheShippedDefaults`). The notes below are the original brief.

## Goal
Every stock FX ships hand-curated audio/beat response out of the box, as the 33 stock sources do now: per parameter
one running modulator plus bypassed alternates, judged by eye. Transitions come after this, as separate work.

## Read first
- `DECISIONS.md`: "Generator and FX defaults" and "Shaders Ship Hand-Written Audio Response".
- `docs/developer/modulation.md`, section "Reactivity Recipes": the file format, the LFO polarity rule, the tests.
- `src/main/kotlin/llm/slop/liquidlsd/presets/StockDefaults.kt`, `rendering/isf/ISFAutoBindEngine.kt`
  (`resolveDefault`, `shippedDefault`, `FxDefaultDto`) and `src/test/.../presets/StockDefaultsTest.kt`.
- Examples to copy: `src/main/resources/stock_defaults/sources/*.json` (look at the `parameters.<name>.modulators` entries).

## Where things stand (all committed)
- **One shipped default file per stock source and per stock effect**: `resources/stock_defaults/sources/<id>.json` and
  `resources/stock_defaults/filters/<id>.json`. Each file has the shape of the default a user saves (`GeneratorDefaultDto` /
  `FxDefaultDto`) but is sparse: it lists only what differs from the shader. There are no separate recipe files and no
  knob tables in code any more.
- The 27 filter files hold only `metaBindings` today. Your job is to add `params`:
  ```json
  "params": {
    "bloomIntensity": {
      "note": "why this response",
      "modulators": [
        {"name": "kick bloom", "sourceId": "audio_flux_bass", "operator": "ADD", "depth": 0.2, "followerMode": "PUNCHY", "attackMs": 5, "decayMs": 150},
        {"name": "slow swell", "sourceId": "lfo", "operator": "ADD", "bypassed": true, "waveform": "SINE", "genUnit": "BEAT", "subdivision": 16, "depth": 0.1, "dcOffset": 0.1}
      ]
    }
  }
  ```
  `ISFAutoBindEngine.shippedDefault` already lays `params` over the filter (`StockDefaults.overlayParameters`), and
  `FxDefaultDto.applyParametersTo` puts them on the slot. Each modulator must give `sourceId`, `operator` and a
  non-zero `depth`; `genUnit` defaults to TIME, so say `"BEAT"` for beat LFOs.
- A user's "Save as Default for <effect>" (content-hash override) stores full parameters with modulators and replaces
  the shipped default wholesale. "Remove Saved Default" goes back to the shipped one.
- **Audio Response** (`AudioEngine.response`) is the only global control. Recipe modulators are ordinary modulators:
  no flag, no per-recipe scaling. The user rejected that idea.

## Things to check
- `ISFFilter` is shared with transitions (`Mixer.transitionFilter`), and init runs the same `resolveDefault`. Shipped
  files are keyed by id under `stock_defaults/filters/`, so a transition only picks one up if the ids collide. Make sure
  they don't.
- FX chains (`FXChainDto` / `FXSlotDto`: the deck starter chains loaded by `Mixer`, the stock `.lsdfxchain` files)
  store full slot parameters. Loading a chain therefore replaces the shipped response for its slots. Decide with the
  user whether the stock chains should be regenerated so they pick the new responses up.
- The Metaknob / Super Knob drive a parameter's *base*. A modulator on the same parameter rides on top of it. Check
  that the two together stay in range and still feel controllable; this may argue for putting the response on a
  different input than the Metaknob's.
- Only modulate a rate input (Speed/Spin/Rate) if the shader integrates it through `LSD_PHASES`.
- Polarity: bipolar sources (LFO, sample-and-hold) swing `dcOffset ± depth`. To sweep a 0..1 input upward from its
  minimum, use `depth = dcOffset = d/2`.

## Taste notes from the source work
- FX sit on top of a source that already moves. Prefer responses that read clearly without piling chaos on chaos:
  kick hits, loudness swells, slow beat LFOs. Keep one main response obvious and the alternates bypassed.
- Anything the user wouldn't notice gets removed. Anything whose motion looks like a bug is left alone.
- Feedback-style filters carry state (feedback, fluid smear, video strobe and similar). Be gentle with them.
- Work in batches of about 10. After each batch, merge a judging entry into `.planning/app-checklist.md` (regenerate
  the PDF with pandoc + weasyprint) so the user can review in the app.

## Tests
Extend `StockDefaultsTest`. `everyShippedFilterDefaultTargetsRealInputs` already checks binding targets and `params`
names. Add for filters what `everyShippedSourceDefaultIsValid` and `lfoSwingsFitTheirParameters` do for sources: every
modulator validates, at most one active per input, `|depth| <= 1`, LFO swings inside the input's range (from the ISF
header in `default_filters/<id>.fs`). Add one test that a stock filter built from its shader gets its shipped
modulators and that a saved override replaces them.

## Done means (this repo's rule)
Code, tests, both `RELEASE_NOTES.md` and `docs/release_notes.md`, the user guide (`docs/user_guide/modulation.md`
"Built-in responses"), the developer docs (`docs/developer/modulation.md`), DECISIONS.md (edit entries in place; no
dated appends), ROADMAP.md (the Reactivity Recipes row), and tooltips where the UI changes. The bundled docs site is
regenerated by the build. Commit only when the user asks, staging explicit paths (other agents share the tree), with
no Claude co-author lines.
