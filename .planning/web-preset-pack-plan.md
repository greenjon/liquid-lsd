# Web preset pack: batches 2-5 (40 more presets)

Context: batch 1 (commit e8ebe16) made 10 presets + 5 FX chains with `scripts/gen_web_presets.py`
(read its docstring first; it is the authoring guide). Phase 7 of `web-renderer-parity-plan.md`.
Batch 1 left 7 presets from the old pack renamed `web-*`, so the library is 17 presets today.

## Constraints that shape the plan
- Only 8 sources exist (7 ISF + mandala). 40 presets means ~5 more per source, so a new preset
  must be a visibly different *look*, not the same look with new modulators. Differentiate by
  (a) region of parameter space (palette mode, shape/surface type, density, wireframe vs solid),
  (b) which musical signal drives which visual axis, (c) tempo feel (slow/ambient vs hard/strobing).
- Gates only work when base == parameter minimum (reactivity.test enforces).
- No `seq`, no MIDI/macro modulators. Look right with the beat anywhere.
- Per-source gotchas are in the parity plan Phase 7 note (gyroid black by default, hyper_slice
  blows out under feedback, spiral needs DotSize ~.3 / Glow ~.5, icosa small at Zoom .5).

## Per-batch workflow (same loop each time)
1. Append presets (and any chains) to `gen_web_presets.py`; run it.
2. Add each preset to `default.lsdplay` / `default_bg.lsdplay`; add new chains to `settings.json`
   `fxChains` and `catalog.json` (check whether `sync_web.py` regenerates catalog entries).
3. `node --test web/tools/content.test.mjs web/tools/reactivity.test.mjs`
4. `node web/checks/render_check.mjs`, then LOOK at the PNGs in /tmp/lsd-render-check (blank,
   white-out and clipping are the usual failures; the tests do not catch them).
5. Fix, then one commit per batch; update both release-notes files + `web_subsystem.md` count.

## Batches (each: 10 presets, ~2 new chains)
Allocation targets total ~6 per source across the pack; check counts after each batch.

- **Batch 2: second look per source.** One new preset on each of the 7 ISF sources + 2 mandala
  + 1 more spiral. Deliberately the opposite mood of batch 1 (solid surfaces vs wireframe,
  alternate PaletteMode, dense vs sparse). Gives the baseline "two looks per source".
- **Batch 3: tempo/energy tiers.** Same sources, driven for energy: 4 slow/ambient (amp/mid
  followers, 32-64 beat LFOs, no gates; candidates for the bg playlist), 3 mid-groove, 3 hard
  (kick + bass-gated + hat strobing). Tag `ambient`/`groove`/`hard` and consider a test that
  ambient presets have no sub-beat modulation.
- **Batch 4: signal-role variety.** Presets whose identity is one signal: hat-driven (high),
  mid-driven vocals/synth, bar-phrase builds (a 16-64 beat LFO ramps intensity, bass-gate lands
  the drop), kick-only. Add "build/drop" chains (strobe, bloom swell) to pair with them.
- **Batch 5: signature pairs + polish.** Pin 8-10 preset|chain pairs in the playlist (the
  `preset | chain` syntax), 10 presets filling the thinnest sources, then a whole-pack pass:
  duplicate-look check by eye on a contact sheet, normalise overall brightness, trim weakest.

## Chains
~8 new chains across batches (aim for ~15 rotating): kaleido variants, mirror_sphere/
polar_tunnel/vortex_swirl for geometry, halftone/retro_crt/thermal for stylised, a gentle
no-reactivity "ambient" chain, and a heavy "drop" chain. Keep the rotation in `settings.json`.

## Tooling to add before/early (small)
- Contact-sheet script: render all presets to one PNG grid (render_check already renders each;
  tile with ImageMagick `montage` if present) so duplicates and blank frames are seen at once.
- Brightness/blank guard in `render_check.mjs`: fail on mean luma < ~0.03 or > ~0.9.
- Optional: reactivity test for `ambient`/`hard` tags (see batch 3).

## Open question for the user
Real audio listen test is still missing (harness has no audio). Plan assumes by-eye + evaluator
tests are enough per batch, with one real-music pass on the live site after batch 3 and 5.
