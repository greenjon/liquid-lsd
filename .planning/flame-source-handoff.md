# Flame source: handoff (read this first in a fresh session)

Plan: `.planning/flame-source-plan.md` (decisions, genome layout, phases). This file = current state + how to continue.
Branch: `spike/flame-source` (plan itself is committed on `main`, e9a6c0a). Rules: clean-room (no flam3/Electric Sheep/Apophysis code), no "sheep" wording, no Co-Authored-By lines.

## State (2026-10-09): phase 0 spike DONE and gate PASSED (seen running by the user; results below)

Files:
- `src/main/kotlin/llm/slop/liquidlsd/rendering/FlameSpikeSource.kt`: `FlameSpikeSource` (extends `DynamicVisualSource`, overrides `renderTopology`) + private `FlameEngine` (all GL + inline GLSL).
- `src/main/resources/default_sources/flame_spike/{meta.json,shader.frag}`: parameters + the tone-map pass (this shader is the one `Renderer` binds).
- `VisualSourceRegistry.kt`: `flame_spike` added to `EXPERIMENTAL_SOURCE_IDS` (NOT `DEFAULT_SOURCE_IDS`: stock-source tests assert every default is ISF or mandala, has curated macros, and exists in library/sources), `FOLDER_STYLE_IDS`, and a `loadFromFolder` branch.
- `Shader.kt`: `setUniform4fv`.

How the render works (all in `FlameEngine.render`): fade/clear 32F density FBO -> per pass: iterate fragment pass (state ping-pong, 512x512 RGBA32F = 262k points, xyz-less: x, y, colour, age) -> `GL_POINTS` splat (Gaussian from `gl_PointCoord`, additive, one-to-one blend) -> finally the deck's tone-map shader draws the density to the target. 12 variations hardcoded in GLSL `vari()`, 3 hardcoded genomes (param `Genome` 0..2), modifiers `Spin`/`Breathe`/`Warp`. GPU time is logged every 240 frames ("Flame spike: gpu X ms/frame").

Deviations from the plan, on purpose, for the spike: state is xy (no z yet); 2 variation slots, no A/B params; no final xform/post-affine; genome hardcoded; `Genome` is a continuous param rounded, not discrete.

## To check next (needs a human eye / the running app)

1. `./gradlew run`, put "Flame Spike" on a deck (it is extracted to `library/sources/flame_spike/` on first start). Look for shader compile errors in the log (engine shaders compile on first render).
2. Does it look like a flame? Tune the three genomes if they collapse/blank. Try Genome 0..2, Spin, Breathe, Warp, Kernel, Sharpness, Exposure, Decay, Passes.
3. Perf gate: read the gpu ms/frame log at Passes 1/4/16, with 1-2 decks. fp16 is now the default (user saw no visual difference at spike densities); `FLAME_DENSITY=fp32` (env var; `-D` is NOT forwarded by `gradlew run`) to compare; `FLAME_N=1024 ./gradlew run` for 1M points density formats (JVM arg; check how the run task passes jvmArgs).
4. Variation formulas are written from memory of the paper's definitions: verify against the paper before phase 3 (unit-check list in plan).

## Spike gate questions (answer in the plan's "Risks" section when known)
- Points/frame budget per deck; fp16 vs fp32 density; cost of kernel footprint (Kernel 1 vs 3 vs 5).
- Does decay>0 hide point noise enough to allow fewer points?
- Is the log tone-map acceptable, or do we need the auto-exposure/mip approach now?

## After the gate (plan phases 1+)
Genome-as-parameters in Kotlin (no meta.json for params, discrete ids via `steps`), UBO upload, `drawEditor` hook, `sampleState` hook on `DeckMorphController`, then variations, sampler/Keep/Garden, importer, web.
Gotchas already found: `DeckMorphController` lerps discrete params (read ids from stateA/stateB); per-param random is uniform (needs genome-aware sampler); Edit tab is a flat loop (`ParametersTabs.kt:447`); a param named `Zoom` collides with `uZoom` set by `Renderer` on the same shader (harmless: the engine reads the param directly).


## Spike results (user's machine, 2026-10-09)
- Look: convincing flame structure, feathered filaments, good colour. Genome 0 is the good one; genome 1 collapses to thin curves (needs tuning, irrelevant once genomes are generated).
- fp16 == fp32 visually -> fp16 default. Isolated single hits were ugly -> `Toe` added. Decay 0.75-0.9 = best quality per point; exposure now normalised by (1-decay).
- Perf (gpu timestamp log, Kernel 3, fp16): 262k pts x 16 passes = 15.2 ms; 1M pts x 16 = 35-47 ms. ~linear in splats (~2-3.6 ns each). Budget: ~0.5-1M splats/deck. Passes 1/4 and Kernel 1/3/5 images were reviewed but their timing lines were not pasted.
- Gotchas learned: `Main` keeps a `GL_TIME_ELAPSED` query open around the whole frame (only one may be active), so per-source timing must use `glQueryCounter(GL_TIMESTAMP)`; `gradlew run` does not forward `-D` flags (use env vars `FLAME_N`, `FLAME_DENSITY`); extracted `library/sources/<id>/` files are never overwritten, so delete the folder after changing bundled spike resources; a param named `Zoom` collides with the renderer's `uZoom`.

## Recommended next step: phase 1 on this branch
Keep `FlameEngine` (it is the reusable part) and replace the hardcoded genome: genome-as-parameters built in Kotlin, UBO upload, quality tiers (points x passes presets + decay), then variations from the paper with unit checks. Remove `flame_spike`/`EXPERIMENTAL_SOURCE_IDS` when the real `FlameSource` registers properly.
