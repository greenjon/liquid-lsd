# Flame source: handoff (read this first in a fresh session)

Plan: `.planning/flame-source-plan.md` (decisions, genome layout, phases). This file = current state + how to continue.
Branch: `spike/flame-source` (plan itself is committed on `main`, e9a6c0a). Rules: clean-room (no flam3/Electric Sheep/Apophysis code), no "sheep" wording, no Co-Authored-By lines.

## State (2026-10-09): phase 0 spike DONE and gate PASSED (seen running by the user; results below)

Files:
- (historical, now `FlameSource.kt` + `FlameEngine.kt`) `FlameSpikeSource` (extends `DynamicVisualSource`, overrides `renderTopology`) + private `FlameEngine` (all GL + inline GLSL).
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
- Perf (gpu timestamp log, Kernel 3, fp16): 262k pts x 16 passes = 15.2 ms; 1M pts x 16 = 35-47 ms. ~linear in splats (~2-3.6 ns each). 262k pts: passes 1 = ~2-5 ms, passes 4 = ~7 ms, passes 16 = 13-15 ms (sub-linear, noisy: GPU clocks / slider dragging). Kernel 1 = 6.9 ms, 3 = 6.9 ms, 5 = 8-9 ms at passes 4: sprite fill is NOT the bottleneck (suspect blend contention in dense regions). Corrected budget: ~0.25-0.5M splats/deck (2-3.5 ms). `FLAME_SKIP=splat|iterate` omits a pass to attribute cost (blank screen is expected with either flag).
- **Cost attribution (262k pts, Kernel 3, Intel Iris Xe TGL GT2 = integrated, shared memory, clocks vary with load, so noisy):** iterate-only (skip=splat): ~1.0 ms @4 passes, ~4 ms @16 = ~0.25 ms/pass. Splat with every point culled by the burn-in test (skip=iterate): ~1.4-2.9 ms @4, ~5-8 ms @16 = ~0.35-0.5 ms/pass, so even the vertex stage alone (262k texelFetch vertices) is expensive. Full: ~7 ms @4, 13-15 ms @16. Same-session no-skip baseline, two Flame instances (both decks) active: identical settings (passes 4, Kernel 3) logged 2.3, 3.0, 4.2, 5.9 and 9.3 ms, i.e. a **4x swing with no parameter change** (iGPU clocks + contention), so any fixed budget is unreliable on this hardware and the `Points` auto-governor is effectively required, not optional. Conclusion: no single dominant stage; per-point cost is ~3 ns total (iterate ~1, vertex ~1.5, raster/blend the rest) and is independent of sprite size up to Kernel 3 (point-setup-limited, not fill-limited). **Point count is the cost; sprite size is free to Kernel 3.** This machine is a worst case; a discrete GPU should be several times faster.
- Gotchas learned: `Main` keeps a `GL_TIME_ELAPSED` query open around the whole frame (only one may be active), so per-source timing must use `glQueryCounter(GL_TIMESTAMP)`; `gradlew run` does not forward `-D` flags (use env vars `FLAME_N`, `FLAME_DENSITY`); extracted `library/sources/<id>/` files are never overwritten, so delete the folder after changing bundled spike resources; a param named `Zoom` collides with the renderer's `uZoom`.

## Phase 1 (2026-10-09): genome-as-parameters, implemented, NOT yet seen running
- `FlameGenome.kt`: parameter layout (19 globals, X1..X6 with weight/colour/speed + pre/post affine + 3 variation slots, and a final xform `XF`), built in Kotlin; `pack()` writes the `uXf` uniform array (compacts weight>0 xforms, applies Spin/Breathe/Warp/Drift); `finalIsIdentity()` lets the splat pass skip the final xform.
- `FlameSource.kt` (id `fractal_flame`, label "Fractal Flame") + `FlameEngine.kt` (the old spike engine, shared GLSL `COMMON` for iterate and splat, new `Points` param = fraction of state rows iterated and drawn). `FlameSpikeSource`, `flame_spike` and the `Genome` param are gone; registry branch is by `FlameSource.SOURCE_ID`, still under `EXPERIMENTAL_SOURCE_IDS`.
- `FlameGenomeTest` covers names, discrete ids, packing, compaction, Warp. Full `./gradlew test` passes.
- Deferred from the layout on purpose: variation `A`/`B` params (phase 3, when variations use them; new keys do not break presets), per-xform `Opacity`, UBO (plain uniform array is enough until phase 2 needs A and B slots), 3D keys.
- Quality knobs `Points`/`Passes`/`Kernel` are still ordinary params; they should move to a device setting with the governor.

**To check (needs the app):** `rm -rf library/sources/flame_spike` (stale extracted spike folder, it will load as a broken source), then `./gradlew run`, add "Fractal Flame", look for shader compile errors in the log (the `switch`/`applyXf` GLSL moved into a shared string), confirm the starter looks like the old Genome 0, try X4..X6 weights, XF PostX, Points.

## Phase 2 + Mutate sampler (2026-10-09): implemented, NOT yet seen running
Findings that forced merging phase 2 with the sampler: the morph only lerps `randomizeBase` params, B comes from a uniform per-param draw, and the source could not see A/B/t.
- `MorphState.kt`: new optional `GenomeMorph` interface (`sampleMorphTarget`, `isDiscreteMorph`, `onMorphUpdate`). `DeckMorphController` takes `sourceProvider` (first ctor arg, defaulted so trailing-lambda callers still work); `Deck` passes `{ source }`. `sampleNewState` calls the source's sampler after the generic loop (from = the other snapshot); the lerp uses the nearer end for discrete params of a `GenomeMorph`; `update()` ends with `onMorphUpdate(state0, state1, v)`. Other sources are unaffected.
- `FlameSampler.mutate` (pure, tested): gaussian jitter scaled by `Mutation` x 0.2 x range on xform params (XF and Post* untouched), Pre scales clamped to |0.95|, variation id swap odds 0.5 x Mutation per audible slot (weighted pool), xform birth/death odds 0.3 x Mutation (keeps >= 2 active).
- `FlameGenome`: all xform params and ids are now `randomizeBase` (= morph-enabled; globals/camera/tone/modifiers stay pinned); new `Mutation` global (0.25). Slot vec4 is now `[idA, wA, idB, wB]` (no UBO needed: it still fits the plain uniform array); `pack(params, out, morph)` cross-weights A by (1-t) and B by t, adding weight modulation to both ends; same id at both ends collapses to one entry.
- `FlameSource`: implements `GenomeMorph`; morph view is live only while the controller keeps calling `update` (serial counter checked in `update()`), so manual edits with Rand off are not shadowed by stale snapshots.
- `meta.json` now lists the 6 tone-map inputs (manifest test needs non-empty params); the loader still builds genome params from code. `library/sources/fractal_flame/meta.json` (local extraction) was refreshed to match.
- Tests: `FlameSamplerTest`, morph packing in `FlameGenomeTest`. Full suite passes.

**To check (needs the app):** put Fractal Flame on a deck, raise the deck Rand (morph) so it sweeps A->B; Mutation 0 vs 1; do ids cross-fade (no sweeping through intermediate variations); does a born xform fade in; any blank/exploded genomes (the quality gate is still missing, so expect occasional duds at high Mutation).

**Verified by the user (2026-10-09): morph + Mutate works and looks good.** Gotcha found while verifying: `applyDto` restores the saved `randomizeBase` flag, so a `last_session.json` or preset saved before the morph flags were turned on keeps the genome params pinned and the morph silently does nothing. Re-add the source (or delete the session file). Any future change to a param's default morph flag has the same problem for old saves.

## Phase 4 (2026-10-09): structured editor, implemented, NOT yet seen running, uncommitted
Source params are edited in the Edit row's SRC sub-tab (`ParametersTabs.drawDeckGroupContent`); that flat loop is live, not legacy. New: `rendering/StructuredEditor.kt` (`StructuredEditorSource`: UI-free tab/section layout, `editorTabs(keep)`, add-tab), implemented by `FlameSource` via `FlameGenome.editorTabs`; `ui/StructuredEditorPanel.kt` draws tab buttons + collapsing sections, every field still a `drawParamRow`. Tabs: Look, Mods, X1..Xn (first 2 + any weight>0; "+ Xn" reveals the next), XF. Variation Id rows are normal discrete rows (no name dropdown yet). Morph flags untouched. Tests: `FlameEditorLayoutTest`.
**To check (app):** tab strip look/spacing, zebra rows across sections, selection per deck, "+ Xn", Post-affine collapsed by default, collapsing-header IDs when two decks show flames.

## (done above) phase 4 was: (editor hook; the ~170-row flat Edit list is unusable), then 3 (paper variations + unit checks; adds A/B variation params), then 5 (Random/Breed/Keep/Garden + quality gate)

### Phase 4 starting points (not yet explored beyond the plan)
- Plan section "Structured editor (needs the new hook)": optional `VisualSource.drawEditor(...)`; each field still goes through `ParametersRenderer.drawParamRow` so modulation, learn and the kebab keep working. Layout: Global/Camera/Tone, Modifiers, xform tabs X1..X6 + XF (Weight/Color, Pre, Post, three variation slot rows with a name dropdown), "+ add xform" hiding empty xforms.
- The flat loop is `ui/ParametersTabs.kt` (~line 447, one `drawParamRow` per param). Per the user's memory notes, the old Parameters/Properties classic view is being removed and the Edit row now has a Params-only bay (pair focus view, `PerformanceBrowseBay.kt`), so **first check which UI the user actually edits params in** before choosing where to hook. Use an Explore agent for that, then give a short recommendation before building.
- Variation id params are discrete (`steps`/`labels` set), so the dropdown can use `labels`; ids must not be shown as a lerping slider.
- Morph flags: genome params are `randomizeBase` (see gotcha above); the editor must not change that.
- Also consider a flame-owned morph/"Evolve" control (the plan's macro 8 binds `Mutation`); today morphing is driven by the deck randomize button or a modulator on `rand Deck X`.

## Targets (owner decision 2026-10-09)
Design and tune for **Tier L** (Intel Iris Xe iGPU + the web). **Tier H** (RTX A2000, arriving within days) comes later as quality presets on the same engine: see "Performance targets: two tiers" in the plan. When the A2000 arrives, re-run the spike measurements and record Tier H numbers here before designing Tier H options.
