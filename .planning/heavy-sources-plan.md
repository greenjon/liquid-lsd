# Heavy Sources Batch (plan, 2026-10-14)

Status: PLAN. Nothing implemented. Scope: owner to confirm (v1.0 is a feature freeze; see Open questions).

For fresh agents working in parallel. Read this, then `DECISIONS.md`, `docs/developer/clean_room_provenance.md`, and one exemplar (`library/sources/gyroid_hyperspace/gyroid_hyperspace.fs`).

## Goal

A new curated batch of GPU-heavy ISF generators (FX later, separate plan). The existing library targets Intel Iris Xe. This batch targets an **NVIDIA RTX A2000 as the floor** (about 8 TFLOPS FP32, 6 or 12 GB, ~288 GB/s). The owner only curates what they can run, so every source must hold a usable frame rate on the A2000 at the default 1080p render preset, with the default parameters, on one deck. Two heavy decks plus a crossfade is the worst case; each source should expose a quality knob so that case is survivable.

## Decisions already made (owner, 2026-10-14)

- A2000 is the floor and the only measured target.
- ISF multipass is believed supported (verified below in code).
- Heavy sources use the existing `LSD_COST` tag (value 3). The existing browser badge is enough; no new tier.
- **Ships in v1.0** (owner, 2026-10-14). The freeze exception is explicit.
- **Desktop only.** No `web/sync_manifest.json` entry, no `syncWeb` step for the new sources, no GLSL ES 3.00 constraint on them.
- Wave 1 (agents A-D below) launched 2026-10-14. Wave 2 is run separately by the owner.
- Algorithms are written clean from the mathematics (owner did not choose to recover the `fc1ad3f` code; the provenance of that old code is unrecorded, so do not rely on it).
- Stock vs separate pack is still open; source agents deliver only the `.fs` pair and notes, so it does not block them.
- All of the categories below are wanted. Work is split across parallel agents, one source per agent.

## Source catalogue (candidates)

Wave 1, shared raymarch family (distance-estimated fractals):
1. `mandelbox`: box fold + sphere fold, animatable scale.
2. `kifs`: kaleidoscopic IFS (Menger / Sierpinski / octahedral variants via a selector, rotation and offset per iteration).
3. `mandelbulb`: animatable power.
4. `pseudo_kleinian`: Kleinian-style fold structures.
5. `apollonian`: 3D sphere-inversion gasket (optional).

Wave 2, different visual categories:
6. `nebula_volume`: fbm density volume, light marching, step-count knob.
7. `reaction_diffusion`: Gray-Scott, persistent fixed-size grid (multipass).
8. `fluid_ink`: stable-fluids smoke or ink (multipass, persistent).
9. `lenia`: continuous cellular automata (multipass, persistent). Stretch.

Wave 3, heavy 2D:
10. High-iteration Julia / Newton / Burning Ship with smooth colouring and orbit traps. fp32 limits zoom to about 1e-6. Deep zoom (perturbation) is out of scope.

Skipped for now: slime mold / particle life (needs scatter, which fragment-only ISF lacks), strange attractors. `hyper_slice` already covers the quaternion Julia slice.

Old versions: commit `fc1ad3f` (2026-08-20) removed our own earlier `mandelbox`, `kifs`, `mandelbulb`, `pseudo_kleinian` and `clifford_torus` (legacy `meta.json` + `shader.frag` format). Recover with `git show fc1ad3f^:library/sources/<name>/shader.frag`. They are our own code, so they are a legitimate starting point; they were removed because they were too heavy for Iris Xe.

## Codebase facts (verified by research, with file refs)

### Where files go
- Authoritative: `library/sources/<id>/<id>.fs`. One folder, one file, no JSON sidecar, no thumbnail. `<id>` is snake_case; the display name is derived from the id (`_` to space, first letter capitalised, `VisualSourceRegistry.kt` ~275), not from `DESCRIPTION`.
- Second tracked copy: `src/main/resources/default_sources/<id>/<id>.fs`, **identical**. `build.gradle.kts:377-383` overlays `library/sources` onto the jar but never writes back and no test compares the trees. Authors must copy the file to both by hand. Check with `diff -rq library/sources src/main/resources/default_sources`.
- Tests read different trees: `StockGpuCostAuditTest`, `StockDiscreteInputsAuditTest` and `BundledIsfRangesTest` read `default_sources`; `VisualSourceManifestTest` and `Source3DModeTest` read `library/sources`. A source missing from either tree is only partly audited.
- Do not use the legacy `meta.json` + `shader.frag` layout (`mandala`, `fractal_flame` are the only exceptions). DECISIONS.md says everything is ISF.
- Loading: `VisualSourceRegistry.loadBundledSources()` loads every folder in `library/sources` with no registration needed.

### Making a source "stock" (curated set)
A source dropped in the folder loads, but only ids in `VisualSourceRegistry.DEFAULT_SOURCE_IDS` (`VisualSourceRegistry.kt:53-62`) are self-healed from the jar and fully tested. The stock set is 8 generators in DECISIONS.md (~line 56). Growing it, or adding a separate heavy pack, is an owner decision (Open question 1). For every new stock source touch:
1. `DEFAULT_SOURCE_IDS`.
2. `GeneratorDefaultsTest.kt` (~222-231): a curated 4-knob entry in `stockInputNames`. The test asserts every stock id has one. `GeneratorDefaults.kt:59` `CURATED_KNOBS` is optional (heuristic fallback exists).
3. `SourceDocRegistry.kt`: `sourceDescriptions` plus per-parameter `"<id>/<Param>"` descriptions (tooltips).
4. `StockGpuCostAuditTest` heavy allowlist (`setOf("icosa_h3","hyper_slice","gyroid_hyperspace")`): add the new id so the tag is asserted.
5. `docs/licenses/SHADERS_LICENSE.md`: a row in "Bundled Visual Sources".
6. `docs/user_guide/visual_sources.md`: a section (`### Name (`id`)`, one sentence, parameter bullets). The intro says "8 built-in procedural generators"; update it. Also `docs/user_guide/macros_and_rack.md:454` lists curated stock sources and `docs/developer/rendering.md` has a section per heavy source (currently `icosa_h3`).
7. `Source3DModeTest` expected sets, if `is3D`.
8. Web: add an entry to `web/sync_manifest.json` (`"desktop"`, `"web"`, `"mode": "auto_shader"`), then `./gradlew syncWeb`.

### Web sync (current state is broken)
`scripts/sync_web.py --check` reports 72 in sync and 3 stale: `gyroid_hyperspace`, `hyper_slice`, `icosa_h3` (the `LSD_COST` line was added without re-running `syncWeb`). `WebSyncTest.testAllWebShadersMatchDesktopTranspiled` should fail until `./gradlew syncWeb` is run. One agent (the integration agent) should fix this first. Web target is GLSL ES 3.00: `#version 330 core` is rewritten to `#version 300 es` + `precision highp float;`, and uniform initialisers are dropped. Whether `web/isf.js` handles `IMPORTED` is unverified; avoid it. After builds run `git checkout -- src/main/resources/docs` (regenerated by `generateDocs`).

### The heavy tag
- Syntax: top-level key in the ISF header JSON: `"LSD_COST": 3,` (1 light, 2 medium, 3 heavy). Parsed into `ISFHeader.LSD_COST` (`ISFModels.kt:92`); `ISFHeader.gpuCost` clamps to 0..3 (0 = undeclared).
- UI: a warning triangle in the browser only for value 3 (`PresetListPanel.kt:325-334`, `GPU_COST_HEAVY = 3`); tooltip text "GPU-intensive: may run slowly on integrated graphics." Nothing is hidden or filtered.
- `StockGpuCostAuditTest` asserts: declared values are in 1..3; the three named raymarchers are tagged 3; clamping works. It does not detect an untagged heavy shader, so **tagging is the author's responsibility**.
- Docs: `docs/user_guide/visual_sources.md:196-204`, `docs/developer/rendering.md:233-234`.
- Exact header placement (from `gyroid_hyperspace.fs`): `"CREDIT": "Liquid LSD",` then `"LSD_COST": 3,` then `"ISFVSN": "2.0",` then `"is3D": true,`.
- The tooltip wording is Iris-Xe oriented. Since the whole batch is A2000-tier, the owner may want the wording or a second tier (Open question 3).

### ISF dialect and GLSL profile
- Header: first `/* { ... } */` block (`ISFParser.kt:14`). Known keys: `DESCRIPTION`, `CREDIT`, `CATEGORIES`, `INPUTS`, `PASSES`, `IMPORTED`, `is3D`, `LSD_COST`. `ISFVSN` is tolerated and ignored. The header `feedback` key exists but is not wired; do not use it.
- **Fixed GLSL 3.30 core** wrapper with `GL_ARB_gpu_shader5` and `GL_EXT_gpu_shader4` enabled. No compute, `imageStore`, GLSL 4.x functions, `#include`, or 3D textures. Output via `gl_FragColor` (a `#define` to `out vec4 isf_FragColor`). Author `#version` and `precision` lines are stripped. Keep code valid in both 3.30 and ES 3.00 (web transpile).
- **No shared include mechanism.** Each shader inlines its own helpers (`rotX/Y/Z`, `hsv2rgb`, DE, normal, AO). Agents will duplicate helper code. To keep it consistent, copy the "Shared helper block" below verbatim rather than inventing variants.
- Input types: `float`, `long`/`int`, `bool`/`event`, `color` (split into `Name R/G/B/A`), `point2d` (split into `Name X/Y`), `image`, `audio`, `audiofft`. Generators should use `float` (and `long` or a `STEP` float for selectors).
- Float input: `{ "NAME": "WallThickness", "LABEL": "Wall Thickness", "TYPE": "float", "DEFAULT": 0.29, "MIN": 0.0, "MAX": 1.0 }`. Missing DEFAULT = 0.5, MIN = 0, MAX = 1. `NAME` is CamelCase without spaces, `LABEL` carries the readable text.
- Discrete float: add `"STEP": 1` (for example `"MIN": 0, "MAX": 3, "STEP": 1`). `(MAX-MIN)/STEP` must be whole and DEFAULT on a step (`StockDiscreteInputsAuditTest`). Integer uniforms are rounded, not truncated. Selector names should be one already in the `BundledIsfRangesTest` allowlist (`ColorMode`, `PaletteMode`, `Detail`, `SurfaceType`, `ColorMethod`, `Symmetries`, `MaxPoints`, ...) or be added to it.
- **Normalised ranges** (`BundledIsfRangesTest`, DECISIONS.md "Bundled ISF Inputs Are Normalized"): continuous inputs span exactly 0..1 or -1..1; the only exceptions are enums/counts and angles of +/- PI (3.1..3.2). Remap inside the shader with a `#define` block after the header, name with a trailing underscore: `#define WallThickness_ (0.02+WallThickness*0.78)`. Neutral-1.0 scales use `#define Zoom_ (Zoom<0.5?pow(0.1,1.0-2.0*Zoom):pow(5.0,2.0*Zoom-1.0))`.
- Injected uniforms (do not redeclare): `RENDERSIZE` (aliases `iResolution`, `u_resolution`, `resolution`), `TIME` (aliases `iTime`, `u_time`, `time`), `TIMEDELTA`, `FRAMEINDEX` (`iFrame`), `PASSINDEX`, `DATE`, `iMouse`, `audioVolume`/`audioBass`/`audioMid`/`audioTreble`, `audioFFT` sampler (unit 5, alias `iChannel0`). `TIME` is wall time, not beat-locked.
- Audio reactivity convention: **stock shaders do not read audio uniforms.** Reactivity comes from the host modulating exposed parameters (LFO, audio modulators, macros). So: expose every motion/shape control as a normalised float input, give time-driven animation a signed speed input (for example `FlightSpeed` -1..1), and do not hard-wire audio.
- Role detection: 0 image inputs = generator.
- `is3D`: true for raymarchers (default also true if inputs `Rotate X` and `Rotate Y` exist). A 3D source bypasses the deck View zoom/rotate controls, so it **must supply its own `Zoom`, `RotateX`, `RotateY`, `RotateZ`** (angle range -3.14159265..3.14159265).
- Curated knobs: 4 per stock source; a non-curated source gets heuristic ordering that skips selector inputs.

### Multipass and simulation support (`ISFVisualSource.renderTopology`, ~255-340)
- `PASSES[]` entries support `TARGET`, `WIDTH`, `HEIGHT`, `PERSISTENT`, `FLOAT`. `PASSINDEX` is set per pass. The final pass (and any pass with no `TARGET`) renders to the deck target.
- `PERSISTENT` = ping-pong pair. The target name is bound to the **previous frame**; the new frame is written to the other buffer, then swapped. Both are cleared to 0 on (re)allocation and **on resize**, so simulations must re-seed themselves when the state is empty (all zero) and must not rely on `FRAMEINDEX == 0`.
- `FLOAT: true` = `GL_RGBA32F` (16 B/px; a 1080p pair is about 64 MB, bandwidth-heavy). Default is `GL_RGBA8`. **No 16F option.** Prefer a fixed small grid over full-size float targets.
- `WIDTH`/`HEIGHT`: a literal, `$WIDTH`/`$HEIGHT`, or a single `/` or `*` between two operands. No parentheses, no `+`/`-`. Unparseable falls back to full size. A fixed grid (`"WIDTH": "512"`) gives resolution-independent state and is the cheapest route for reaction-diffusion and fluid.
- No stock generator uses `PASSES` yet (first of its kind). FX exemplars: `default_filters/bloom.fs`, `fluid_smear.fs` (persistent, curl-noise advection), `feedback.fs`, `video_strobe.fs`. Multipass tests: `ISFMultiPassTest`.
- Ensure a persistent sim keeps running at the same logical speed regardless of frame rate: use `TIMEDELTA` for step size, or a fixed step count per frame, and document which.

### Quality / cost mechanisms
- No per-source quality scale in the host. The global render preset (`UITheme.ResolutionPreset`, `UITheme.kt:47-59`; 1080p default) is the only global lever.
- Every heavy source **must add its own quality input** (for example `Detail` with `STEP`, or a normalised `Quality` that remaps to loop caps) so the owner can trade fidelity for speed live. Existing precedents: `Detail` (STEP 1..5) in `domain_warp_fluid`, hard loop caps in `gyroid_hyperspace` (70 steps), `icosa_h3` (120 + 16), `hyper_slice` (72 + 20).
- GLSL 3.30 on some drivers wants constant loop bounds. Pattern: `for (int i = 0; i < MAX_N; i++) { if (i >= steps) break; ... }`.
- No frame-time measurement exists in the audit and nothing is A2000-specific. See "Performance budget" below.

### Provenance (important)
`docs/developer/clean_room_provenance.md` and `docs/licenses/SHADERS_LICENSE.md` apply. Allowed: published papers, our own derivation, textbook mathematics, black-box observation, and our own earlier code. **Not allowed: reading or copying another program's or a Shadertoy/Fragmentarium shader source**, including forum posts that quote them. The provenance file warns that the assistant may have seen formulas in training. So:
- Derive each fractal from its defining mathematics or paper (box fold/sphere fold definitions, the Mandelbulb triplex power formula, Menger/Sierpinski IFS construction, Apollonian inversion, Gray-Scott equations, stable fluids from Stam's paper).
- Do not reproduce a remembered community shader line for line.
- Add a ledger row to the clean-room provenance doc for each new algorithm **before it ships**, plus the licence row. `CREDIT` stays `"Liquid LSD"`; credit for ideas goes under "Algorithm Credits" in the licence doc.

## Shared helper block (recommended, to avoid drift)
One agent (the Wave 1 lead) writes `.planning/heavy-sources-helpers.glsl`, a verbatim paste block (ISF has no include) containing: `rotX/rotY/rotZ`, `hsv2rgb`, an orbit-trap palette function with `ColorMode` selection, central-difference normal, soft AO, a glow accumulator, ray setup from `Zoom` + `RotateX/Y/Z`, and the `FlightSpeed`-style camera motion. The other Wave 1 agents paste it unchanged. This keeps the look consistent and lets the helper be audited once.

## Per-source template (every source)
1. Header comment block with `DESCRIPTION`, `CREDIT: "Liquid LSD"`, `LSD_COST: 3`, `ISFVSN: "2.0"`, `is3D` as appropriate, `CATEGORIES` such as `["Generator","3D","Fractal"]`, and `INPUTS`.
2. Inputs: shape params (4 to 8), camera (`Zoom`, `RotateX/Y/Z` for 3D), colour (`HueOffset`, `Saturation`, `Brightness`, `ColorMode` STEP), motion (`FlightSpeed` or morph speed), and one **quality** input. About 12 to 18 inputs total, in line with the exemplars.
3. `Name_` remap `#define` block, all continuous inputs normalised.
4. Neutral default look: the default parameters must render something attractive immediately on the A2000, with no black frame and no NaN. Guard against NaN/Inf (clamp before `pow`, `normalize`, `log`).
5. Copy to both trees, run the checklist below.

## Performance budget (to be validated on the A2000)
- Default quality target: at least 60 fps at 1080p on a single deck. At max quality: at least 30 fps. The owner measures; agents cannot. Agents set conservative defaults and report the loop caps, per-pixel iteration counts and any float-buffer sizes in their PR/notes so the owner can tune.
- Raymarchers: default max 64 to 100 steps, fold iterations 6 to 12, one AO sample set of at most 4 taps, soft shadows only behind a quality threshold.
- Volumetric: default 48 to 64 steps with a light march of 4 to 8 taps, both scaled by the quality input.
- Sims: fixed grid 256 to 768 per side, 1 to 4 sim sub-steps per frame, `FLOAT` only where precision demands it (fluid velocity, reaction-diffusion concentrations).
- A first-time GPU cost test is possible: a hidden-context compile test in the style of `FlameShaderCompileTest` (`assumeTrue` skip when no GL context) would catch compile failures for all stock sources. See "Test work" below.

## Work split (parallel agents)
| Agent | Scope | Depends on |
|---|---|---|
| A (lead, Wave 1) | Shared helper block + `mandelbox` | none |
| B | `kifs` | A helper block |
| C | `mandelbulb` | A helper block |
| D | `pseudo_kleinian` (and `apollonian` if time) | A helper block |
| E | `nebula_volume` | none |
| F | `reaction_diffusion` (first PASSES generator; verify the persistent/clear-on-resize behaviour) | none |
| G | `fluid_ink` | F's findings on persistent buffers |
| H (integration) | Fix stale web sync first; compile test; registry/test/doc wiring for finished sources; release notes | each source as it lands |

Agents must not edit shared registration files concurrently. Source agents deliver only the two `.fs` copies plus a short notes file `.planning/heavy-sources-notes/<id>.md` (params, loop caps, derivation sources, known issues). Agent H does all of `DEFAULT_SOURCE_IDS`, `GeneratorDefaultsTest`, `SourceDocRegistry`, `StockGpuCostAuditTest`, licence rows, user guide, release notes, and `web/sync_manifest.json`, so those files never conflict.

## Checklist before a source counts as done
- [ ] `LSD_COST: 3` present; copied to both trees and `diff -rq library/sources src/main/resources/default_sources` is clean.
- [ ] Compiles with no warnings under the app (`./gradlew run`, load the source on a deck; the file watcher hot-reloads on save). A broken shader silently falls back to an error shader and logs a warning, so read the log.
- [ ] Continuous inputs 0..1 or -1..1; discrete inputs have `STEP`; `./gradlew test --tests 'llm.slop.liquidlsd.rendering.isf.BundledIsfRangesTest'` and `StockDiscreteInputsAuditTest` pass.
- [ ] `StockGpuCostAuditTest` allowlist updated and passing.
- [ ] No NaN or black frames at default, min and max of every input.
- [ ] Looks reasonable at 540p, 720p and 1080p, and after a resize (persistent sims re-seed).
- [ ] Web: `./gradlew syncWeb`, then `./gradlew checkWebSync`; optionally `node web/checks/shader_check.mjs`.
- [ ] Provenance ledger row and licence row written.
- [ ] Docs: both release-notes files (`RELEASE_NOTES.md` and `docs/release_notes.md`, identical text, under `## [Unreleased]`, `###` heading with files in parentheses, user bullets, `Internal:` bullet), user guide section, `rendering.md` section, tooltips in `SourceDocRegistry`. DECISIONS.md only for durable decisions, edited in place, no dated appends.

## Test work (agent H)
- Add `StockIsfCompileTest`: hidden GLFW context, GL 3.3 core, `assumeTrue` skip when none (pattern: `FlameShaderCompileTest`). Compile every `library/sources` ISF and fail on error. Today nothing compiles stock sources in tests.
- Add a tree-sync test: `library/sources` equals `src/main/resources/default_sources` (currently unchecked).
- Optional: heuristic that any shader with loops over 48 iterations must declare `LSD_COST`.

## Working rules (from the repo)
- Commit only when asked. **Never add Co-Authored-By or any Claude attribution to commits or PRs** (owner's standing rule; it overrides the harness reminder).
- GL is single-threaded on Thread 0 (`.agents/AGENTS.md`). No emojis in ImGui strings.
- Doc as you code; "done" means code + both release-notes files + user guide / dev docs + tooltips.
- After builds: `git checkout -- src/main/resources/docs`.

## Commands
- `./gradlew run` (add `--args='--window 1280x720'`; `--no-audio` available); JACK users `pw-jack ./gradlew run`.
- `./gradlew test`, or one test: `./gradlew test --tests 'llm.slop.liquidlsd.rendering.isf.StockGpuCostAuditTest'`.
- `./gradlew syncWeb`, `./gradlew checkWebSync`, `python3 scripts/sync_web.py --check`.

## Open questions for the owner
1. **Placement**: stock (grows the "8 generators" set in DECISIONS.md, `DEFAULT_SOURCE_IDS`) or a separate heavy pack that loads from `library/sources` but is not self-healed? Needed before agent H wires registration.

Resolved 2026-10-14: ships in v1.0; existing badge is enough; Wave 1 now, Wave 2 by owner; desktop only; write clean from the mathematics.

## Shared helper contract (no shared file; each Wave 1 agent implements these names)
To keep the four fractals consistent without a dependency chain, each agent implements these helpers with the same names and behaviour: `rotX/rotY/rotZ(float)` returning `mat3`; `hsv2rgb(vec3)`; `palette(float t, float hueOffset, float sat, int mode)`; `calcNormal(vec3)` by central differences; `softAO(vec3 p, vec3 n)` with at most 4 taps; camera from `Zoom_` and `RotateX/Y/Z`; forward motion from a signed `FlightSpeed`. Colour modes via `ColorMode` (STEP 1). The `.planning/heavy-sources-helpers.glsl` file is not created.
