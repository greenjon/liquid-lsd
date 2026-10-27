# Handoff: reactivity recipes for FX (filters) — 2026-10-27

## Goal
Every stock FX ships hand-curated audio/beat response out of the box, the way the 33 stock sources do now: per
parameter one running modulator plus bypassed alternates, judged by eye. Transitions come after this, as separate work.

## Read first
- `.planning/reactivity-recipes-plan.md`: the recipe format and how the source work went. The top note says what
  was superseded.
- `DECISIONS.md`, the section "Shaders Ship Hand-Written Audio Response ("Recipes")".
- `docs/developer/modulation.md`, the section "Reactivity Recipes" (format, the authoring rule for LFO polarity, the tests).
- `src/main/kotlin/llm/slop/liquidlsd/presets/ReactivityRecipes.kt` and `src/test/.../presets/ReactivityRecipesTest.kt`.
- Examples to copy: `src/main/resources/reactivity/sources/*.json`.

## Settled decisions (do not reopen)
- **No special class of modulator.** Recipe modulators are ordinary modulators: no flag, no per-recipe scaling. The
  user rejected a `recipe` flag plus a global Reactivity amount as not worth the complexity (commit 40536f2). The only
  global control is **Audio Response** (`AudioEngine.response`), which scales the audio signals themselves.
- Recipes are data (`reactivity/<kind>/<id>.json`) and are validated by tests. `ReactivityRecipes.forId(kind, id)`
  already takes a kind, so use `filters`.
- A default the user saved wins over the shipped recipe. Loading a saved preset or chain that stores its own
  parameters replaces the recipe, as with sources.
- Polarity: bipolar sources (LFO, sample-and-hold) swing `dcOffset ± depth`. To sweep a 0..1 parameter upward from its
  minimum, use `depth = dcOffset = d/2`.
- Only modulate a rate input (Speed/Spin/Rate) if the shader integrates it through `LSD_PHASES`.

## How FX defaults work today (verify against the code; these are pointers, not gospel)
- `ISFFilter` init (`rendering/isf/ISFFilter.kt` ~line 340) calls `ISFAutoBindEngine.resolveDefault(this)`. The
  order is: user override (keyed by the shader's content hash) → curated Metaknob binding (`CURATED`, 27 bundled
  filters) → heuristic. It then applies `parameters` base values and the Metaknob bindings, and places the knob.
- **Saved FX defaults hold modulators now (2026-10-27, done before this handoff).** `FxDefaultDto.params` is
  `Map<String, ParameterDto>` (version 3; MIDI stripped, modulator ids dropped). `FxDefaultDto.applyParametersTo(filter)`
  applies it, falling back to the version-2 `parameters` base values. So the recipe overlay can work like the sources:
  when there's a user override, it wins and carries whatever modulators the user saved. When there isn't one, lay the
  recipe over the curated/heuristic default, inside `resolveDefault` or right after it in `ISFFilter` init. Note that
  a user default saved *before* recipes existed has no modulators and will hide the recipe; that is the same rule as
  for sources.
- `ISFFilter` is shared with transitions (`Mixer.transitionFilter` is an `ISFFilter`, and init runs the same
  `resolveDefault`). Make sure only filters get filter recipes. Transitions have no "Save as Default" at all (only
  "Save Transition As..." and "Reset Transition").
- FX chains (`FXChainDto` / `FXSlotDto`, the deck starter chains loaded by `Mixer`, the stock `.lsdfxchain` files)
  store slot parameters. Find out whether loading a chain keeps or replaces the recipe, and whether the stock starter
  chains need regenerating so they pick recipes up.
- The Metaknob / Super Knob drive a parameter's *base*. A recipe modulator on the same parameter rides on top of it.
  Check that the two together stay inside the range and still feel controllable. This may argue for putting the
  response on a different parameter than the knob's.

## Taste notes from the source work
- FX sit on top of a source that already moves. Prefer responses that read clearly but don't pile chaos onto chaos:
  kick hits, loudness swells, slow beat LFOs. Keep the main response obvious and the alternates bypassed.
- Anything the user wouldn't notice gets removed (Fluid Ink). Anything whose motion looks like a bug is left alone
  (for example fractional powers and Core Size on sources).
- Work in batches of about 10. After each batch, add a judging entry to `.planning/app-checklist.md` (merge it in,
  don't just append; regenerate the PDF with pandoc + weasyprint), so the user can review in the app.
- Feedback-style filters carry state (feedback, fluid smear, video strobe and similar). Be gentle with them, as with
  the simulations.

## Tests
Extend `ReactivityRecipesTest` to cover `reactivity/filters`: names checked against the ISF INPUTS, at most one active
modulator per parameter, `|depth| <= 1`, and the LFO-swing-in-range check (ranges come from the ISF header). Add a
test that a filter built from the stock source gets its recipe, and that a saved user default wins.

## Done means (this repo's rule)
Code, tests, both `RELEASE_NOTES.md` and `docs/release_notes.md`, the user guide (`docs/user_guide/modulation.md`
"Built-in responses"), the developer docs (`docs/developer/modulation.md`), DECISIONS.md (edit the recipes entry in
place; no dated appends), ROADMAP.md (the Reactivity Recipes row), and tooltips where the UI changes. The bundled docs
site is regenerated by the build. Commit only when the user asks, staging explicit paths (other agents share the
tree), with no Claude co-author lines.
