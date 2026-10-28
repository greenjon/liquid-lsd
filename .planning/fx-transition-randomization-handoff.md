# Handoff: random-by-default ranges for FX and transitions — 2026-10-27

## Goal
Make the dice and the Rand controls do something good out of the box on every stock effect (27) and stock
transition (8), the way they now do on every stock source: each one ships a curated random range
(`randomizeBase` / `baseMin` / `baseMax`) on the inputs that change its *look*, so a roll gives a new,
watchable variation. Ranges are judged by eye, not derived from theory.

## Read first
- `DECISIONS.md`: "Randomization is a continuous morph", "Stock sources ship random ranges; a randomize moves the
  macro knob", "Shaders Ship Hand-Written Audio Response", "Effects keep the Metaknob meaning 'how much'",
  "Transitions read their own folder".
- `docs/developer/modulation.md`, section "Reactivity Recipes", bullet "Random ranges (sources)".
- The source work: commits `e1cafdb` and `44993b2` (`git show --stat`), and the shipped files
  `src/main/resources/stock_defaults/sources/*.json` as examples of the format.
- `.planning/fx-reactivity-handoff.md` (the FX audio-response work; same files, same taste rules).

## How it works today (verified 2026-10-27)
- **Engine, already generic:** `ModulatableParameter.sampleBase` (a stepped input draws a whole step, all steps
  equally likely), `writeBase` (moves the driving macro knob via `baseDriver`; skipped while a Metaknob owns the
  input, `metaDrivenBy != null`), `DeckMorphController` (a stepped input switches at the morph midpoint),
  `ParametersUndo.pushRandomizeUndo` (die undo restores base values and knob positions).
- **File format:** `stock_defaults/filters/<id>.json` and `stock_defaults/transitions/<id>.json` already take
  `params.<input>` entries, laid over the shader by `StockDefaults.overlayParameters` and put on the slot by
  `FxDefaultDto.applyParametersTo` (`applyDto`, which copies `randomizeBase`/`baseMin`/`baseMax`). So adding
  `"randomizeBase": true, "baseMin": lo, "baseMax": hi` to a `params` entry should just work. Verify it.
- **Who rolls what (the real gaps; decide each with the user before building):**
  1. *Deck die / deck Rand* (`Deck.getAllRandomizableParameters`): the source, Gain, Deck Zoom/Rotate and the
     chain's Dry/Wet. **Deck FX slot inputs are not included**, so ranges on deck effects would do nothing.
     Only the whole-rig die / Rand All (`Mixer.getAllMixerRandomizableParameters`) includes slot inputs, and only
     the **Master** chain's. Adding deck slots means the source die also re-rolls that deck's FX; the user may
     prefer a separate FX die, or FX rolled only by Rand All. Ask.
  2. *Metaknob conflict:* each slot's Metaknob writes its target input's base every frame, so a range on that
     input is overwritten next frame (`writeBase` deliberately skips `metaDrivenBy` inputs). Either leave
     Metaknob targets un-randomized (the Metaknob means "how much", which arguably should stay the performer's),
     or roll the Metaknob itself, as sources roll their macro knobs. Recommendation: leave the amount alone;
     roll the character inputs (angles, sizes, colours, modes).
  3. *FX chains store full slot parameters* (`defaults/fx_chains/*.lsdfxchain`, deck starter chains), so a loaded
     chain replaces shipped ranges, the same as it replaces shipped audio responses. The pending "rebuild every
     stock FX chain from the shipped defaults" (fx-reactivity handoff) should carry the ranges too.
  4. *Transition die* (`PerformanceTransitionsControls`, `perf_trans_rand`): it ignores ranges and sets **every**
     input to a uniform value over its **full** range (`progress` is excluded upstream, `ISFFilter` line ~335).
     That bypasses the Luminous Flash `spread` / Film Burn `burnSpread` 0.45 at-rest cap (`StockDefaultsTest.
     transitionResponsesStayInsideTheAtRestLimits`), so a roll can make a fade pop. Rewrite it to roll only
     `randomizeBase` inputs through `randomizeBaseValue()` (and so `writeBase`, which moves the TRANS bank's
     K2-K4 knobs that drive the first three floats via `TransitionMacroSync`). Transitions are not in any
     `DeckMorphController`; whether Rand All should morph them is another question for the user.
  5. *Stock transition presets* (`defaults/transitions/*.lsdtrans` and `web/content/transitions/`) store full
     parameters; regenerate them with the ranges, as was done for the audio responses
     (`shippedTransitionPresetsCarryTheShippedResponse` guards drift).

## Taste rules (from the source pass and the user's feedback)
- Roll what changes the look (modes, palettes, angles, sizes, segment counts, colour shifts); never what decides
  whether the picture is watchable (overall amount, brightness, feedback gain, anything that can white out,
  black out or freeze the picture).
- Each range must leave room for the input's running audio/LFO response: an `ADD` LFO swings `dcOffset ± depth`,
  a follower adds up to `depth` (times the input's span; half-span for bipolar inputs). A `MUL` response scales
  the base. Keep inputs whose motion looks like a bug out entirely.
- Stateful filters (Feedback, Fluid Smear, Video Strobe, datamosh-like) carry history; a big change mid-run can
  smear or freeze. Be gentle, check rolls over several seconds.
- Transitions: a range on any input that leaks to the ends of the fade must keep the reach (base max + every
  modulator's reach) at or below the at-rest limits. Nothing may move at rest.
- The user's verdicts on the source pass: rolls that "only change the colour" read as broken; nearly identical
  rolls read as broken; solid-colour or black rolls are the worst case.

## Verifying by eye (do this, it found three real bugs last time)
Tour mode renders the real app headless and deterministically (`docs/developer/tour_renderer.md`). Scripts from
the source pass are in the previous session's scratchpad and easy to recreate:
- A tour JSON with `"randomization"` on, `crossfade -1`, `load_source` a test source on A, then for each effect
  `fx_filter` (deck, slot, id) and repeated `move` + `click` on the die you wire up (`perf_rand_A` is the Clock-row
  deck trigger; `perf_trans_rand` the transition die), dumping frames with `--tour-dump=<dir>:<frames>`.
- `./gradlew run -q --args="--tour=<json> --tour-out=<dir>/raw.mp4 --no-audio --tour-hidden --tour-dump=<dir>:f1,f2"`
- Frames are raw 1920x1080 RGBA, bottom-up: `ffmpeg -f rawvideo -pix_fmt rgba -s 1920x1080 -i frame_N.rgba
  -vf vflip,crop=880:495:1030:20` crops the main monitor. `hstack` them into one strip per effect and look.
- For transitions, set `crossfade` to mid-fade values (e.g. -0.3, 0, 0.3) after each roll, and also check -1/1.
- Don't `pgrep`/`pkill -f` a pattern that appears in your own command line (it matches itself).

## Tests
Extend `StockDefaultsTest`: a filter/transition twin of `shippedRandomRangesFitTheirParameters` (range inside the
input, room for running `ADD` responses, non-empty), a check that no range sits on an input a shipped Metaknob
binding targets (if that is the decision), and for transitions that the at-rest limits hold with `baseMax`
instead of `baseValue`. Add a test that the transition die only touches `randomizeBase` inputs.

## Done means (this repo's rule)
Code, tests, both `RELEASE_NOTES.md` and `docs/release_notes.md`, the user guide (`docs/user_guide/modulation.md`
"Built-in random ranges", `macros_and_rack.md` die descriptions), the developer docs (`docs/developer/
modulation.md`), DECISIONS.md (edit entries in place; no dated appends), the ROADMAP.md "Random-by-Default Ranges"
row, tooltips where the dice change, and a merged entry in `.planning/app-checklist.md` section 9c (regenerate the
PDF with pandoc + weasyprint). Shader edits: `default_*` and `library/` copies, `python3 scripts/sync_web.py
--apply`, `scripts/shipped_source_hashes.py` for sources. Commit only when the user asks, staging explicit paths
(other agents share the tree), with no Claude co-author lines.

## Order of work
1. Ask the user the decisions in gaps 1, 2 and 4 (and whether Rand All morphs the transition). Recommend:
   deck FX slots join the deck die and Rand (behind the existing per-parameter flags), Metaknob targets stay
   unrolled, transition die rolls ranges only.
2. Wire the engine for those decisions, with tests.
3. Ranges for the 27 effects in batches of about 10, then the 8 transitions; verify each batch by tour frames.
4. Regenerate the stock FX chains and transition presets (after the user approves the ranges).
