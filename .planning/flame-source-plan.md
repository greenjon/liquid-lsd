# Flame fractal source + evolving lineage (plan)

Started 2026-10-09. Status: PLAN, nothing built. Target: v1.1 (v1.0 feature freeze).

## Goal and decisions (user)

- A generator with the power of the Electric Sheep flame fractals: every parameter the originals had, plus our own additions, all audio-reactive.
- Way to **keep "children"** for playback at a show. Desktop gets a keep/breed workflow; web gets explicit Yes/No selection.
- **Licensing:** may be sold someday. **No flam3 / Electric Sheep / Apophysis source is read or copied.** Implement from Draves & Reckase, "The Fractal Flame Algorithm" (the published math), the way the beat tracker was built from the BTrack reference paper. Acknowledge the paper in credits/docs.
- **No "sheep" terminology** in code, UI, or docs we ship. Names: user-facing source label **Fractal Flame** (descriptive; avoid bare "Flame", which is Autodesk's video product), internal class `FlameSource`; saved items = **offspring**; the saved collection = **Garden**; breeding = **Breed**. Trademark search before any commercial release (not done).
- **Spike on a branch:** `spike/flame-source`, branched from a clean tree (main currently has uncommitted website work).
- **Morph:** `DeckMorphController` stays the owner. Flame gets read-only `stateA`, `stateB`, `t` from it (to be verified against the controller code in phase 2).
- **Desktop first.** Web port comes after the desktop look and parameter model are settled.
- Variations: the paper's set first, then our own additions.
- Include a **`.flame` XML importer** (user-supplied files only).

## Clean-room rules

- Implementer works from the paper and from our own derivations. Do not open flam3, Apophysis, or Electric Sheep code.
- The `.flame` importer needs the file format's attribute names. Take them from sample files a user supplies plus the paper; do not read the flam3 parser.
- Ideas from third-party/AI descriptions of closed-source apps (e.g. float accumulation, Gaussian splats, post-iteration lens camera, filmic tone map, 3D + DOF) are adopted only as generic graphics techniques; those descriptions are unverified and we never claim to match any product. No code, no product names in shipped text.
- Paper citation: Draves & Reckase, "The Fractal Flame Algorithm" (verify authors, year/revision, and section numbers against the paper itself before citing in docs).
- Do **not bundle** any Electric Sheep genome archive. Check its license before any thought of it. The importer lets users load their own.
- Record the approach in DECISIONS.md like the BTrack precedent.

## Why this fits the existing app

- `DeckMorphController` (`rendering/MorphState.kt`) already does state0 -> state1, promote, resample: the same loop as the original's edge/loop interpolation. Missing: a choice of how the next state is picked.
- Parameters are `ModulatableParameter`s, so modulation, macros, Super Knob, discrete steps, presets (`.lsd`), and the web evaluator all work if the genome is expressed as parameters.
- Hard constraint: **OpenGL 3.3 core** (`Main.kt:187`), no compute, no transform feedback. WebGL2 has the same limits, so the design below carries to the web.

## Rendering design (GL 3.3)

1. **State texture**, ping-pong, RGBA32F, side N (e.g. 512 or 1024 -> 0.25M-1M points). Each texel holds position **xyz** plus colorIndex; rng state in a second attachment (MRT) or derived from a hash of the point id + frame. xyz from the start so 3D is an upgrade, not a rewrite; xforms are effectively 2D (z passes through) until phase 7.
2. **Iterate pass** (fragment shader over the state texture): per point, hash-pick an xform by weight, apply pre-affine, the xform's variation slots, post-affine, then the color blend toward the xform color. Several iterations per frame are cheap to add. Final xform applied only to the drawn position, not stored.
3. **Splat pass**: draw `GL_POINTS` (count = N*N), vertex shader fetches the state texel, applies the **final xform**, then the **camera stage**: affine (zoom, rotation, center) -> optional 3D orbit/perspective -> optional **non-linear lens** (fisheye / spherical / cylindrical), kept strictly after iteration so the attractor math is untouched. Optional stochastic **depth of field**: per-point hashed disc offset scaled by |z - focus|. Points are 2-3 px sprites; the fragment shader evaluates a **Gaussian (or Mitchell) kernel** from `gl_PointCoord`, with the subpixel offset passed as a varying, and additively blends into the **density buffer** (rgb = palette-weighted color, a = hit weight). Footprint size is a quality knob.
4. **Tone-map pass**: log-density scaling with **exposure from a mip-chain / previous-frame average** (no true max reduction), then a filmic/S-curve with toe and shoulder, gamma, vibrancy, brightness, palette lookup, optional density-aware blur (cheap mip-based stand-in for density estimation), background color. Output to the deck FBO as usual.
5. **Decay**: the density buffer persists between frames with a decay multiplier. Decay = 0 gives clean per-frame rendering; higher gives trails and hides point noise. This is also an audio-reactive parameter.
6. Points that diverge (NaN/inf/out of bounds) are reseeded in the iterate pass.
7. Morph gets smoothness for free: points keep iterating and track the changing attractor.

**Spike results (2026-10-09, user's GPU, see flame-source-handoff.md):** the approach works and looks like a real flame. Density format: **fp16 decided** (visually identical to fp32, half the bandwidth, no float-blend extension on web); decay bounds accumulation precision. Cost is roughly linear in splats (points x passes): ~3.6 ns/splat at 262k x 16 (15.2 ms), ~2.1-2.8 ns/splat at 1M x 16 (35-47 ms), Kernel 3. Steady-state: 262k pts x 4 passes (1M splats) = ~7 ms; 1 pass (262k) = ~2 ms; 16 passes = 13-15 ms. Cost is NOT linear in passes (GPU clocks / blend contention), and Kernel 1 vs 3 costs the same while Kernel 5 adds ~30%, so sprite fill is not the bottleneck. Plan for ~0.25-0.5M splats per deck (2-3.5 ms): quality tiers = few points + decay. Attribution (Intel Iris Xe iGPU): iterate ~0.25 ms/pass, vertex stage ~0.35-0.5 ms/pass, remainder raster/blend; cost is per point, not per pixel. **Points as a continuous knob:** make the state texture 512x512 and let `Points` set how many rows are iterated and drawn (viewport height / draw count), so quality scales smoothly; an **auto-governor (needed: identical settings varied 2.3-9.3 ms on the iGPU)** lowers `Points` from the existing GPU timestamp query to hold a frame budget at a show. **Decay is the main quality lever**: 0.75-0.9 fills fine filaments from few points, so quality tiers should prefer fewer points + decay over many points. A `Toe` threshold removes isolated-hit noise; exposure is normalised by (1-decay). Kernel: 1 = crisp hairlines, 3 = full, 5 = soft glow (a look knob, also a cost knob).

## Genome as parameters

### Findings that shape the layout (checked in code 2026-10-09)

- Params are `Map<String, ModulatableParameter>` (`VisualSource.kt`), insertion-ordered in `DynamicVisualSource`; presets key by plain name and **ignore unknown / default missing keys**, so adding 3D or post-affine params later does not break old presets. No count limit. Names must be unique after space-stripping, and must not contain `/` (macro paths).
- `meta.json` cannot declare discrete params and `loadFromFolder` hard-codes `id == "mandala"`. `FlameSource` therefore **builds its parameter map in Kotlin** (constructor `steps`/`labels` for discrete ids) and needs a new registration branch (a stub `meta.json` folder, or a registry entry).
- **`DeckMorphController` lerps discrete params like continuous ones** (no special case), so a variation id would sweep through every id in between. Confirms the plan: Flame must take ids from `stateA`/`stateB`, never from the interpolated value. Angle params are handled (`isAngle`), so mark rotations as angles to get shortest-path morphing.
- **Per-parameter random is uniform in `baseMin..baseMax` with no grouping.** 200 independent uniform draws produce mush and divergent genomes. Flame needs a **genome-aware sampler** supplied by the source (see "Sampler hook" below). Generic randomize is disabled (`isRandomizeDisabled`) on structural params.
- **Edit tab is a hard-wired flat loop** (`ParametersTabs.kt:447`, one `drawParamRow` per param, no paging). Needs a new optional `VisualSource` hook for a custom editor that still calls `drawParamRow` for chosen subsets (keeps modulator UI, learn, kebab).
- **Perform knobs reach a source only through macros** (8 knobs x up to 4 bindings). Super Knob / Metaknob are FX-only. Curated defaults go in `GeneratorDefaults` (hand-curated tier).

### Parameter layout (names are the keys; first draft)

Capacity: 6 xforms (`X1`..`X6`) + final xform (`XF`). Unused xform = weight 0. 2D now; 3D additions are new keys later.

- **Global (~30):**
  - Camera: `Zoom`, `Rotation`(angle), `CenterX`, `CenterY`
  - Tone: `Exposure`, `Brightness`, `Gamma`, `Vibrancy`, `Toe`, `Shoulder`, `Decay`, `Kernel`(splat size), `Background R/G/B`
  - Color: `Palette`(discrete), `PaletteRot`, `PaletteHue`
  - Structure: `Symmetry`(discrete), `Points`(discrete tiers), `Iterations`
  - Lineage: `Mutation` (0..1; ordinary modulatable parameter read by the sampler when it picks the next B. Decided: no special input channel, so LFO/audio/MIDI/macros drive it. Acts on the next B, never the current state.)
  - Lens (later): `Lens`(discrete), `LensAmount`
  - 3D (later): `OrbitYaw`, `OrbitPitch`, `OrbitRoll`, `Perspective`, `FocusZ`, `Aperture`
- **Global modifiers (performance handles, 4):** `Spin` (adds to every xform's rotation), `Breathe` (scales every xform), `Warp` (blends non-linear variation weights up vs linear), `Drift` (color-speed offset). One parameter moves all xforms, so a single macro binding does what would otherwise take 6+. This is how 8 macros x 4 bindings stays enough.
- **Per xform (28):**
  - `Weight`, `Color`, `ColorSpeed`, `Opacity` (4)
  - Pre-affine: `PreRot`(angle), `PreScaleX`, `PreScaleY`, `PreShear`, `PreX`, `PreY` (6; negative scale allowed, so flips need no separate flag)
  - Post-affine: same 6 as `PostRot` ... (identity default)
  - **K = 3 variation slots** `V1`..`V3`: `Id`(discrete), `Weight`, `A`, `B` (shared variation parameters, 12). Typical genomes use 1-3 variations per xform; K=3 beats K=4 on shader cost.
  - Example keys: `X2 PreRot`, `X2 V1 Id`, `XF V2 Weight`.
- **3D later (+8 per xform):** `PreTiltA`, `PreTiltB`, `PreScaleZ`, `PreZ`, same for post. Added as new keys, old presets unaffected.
- Total: ~30 + 4 + 7x28 = ~230 (2D), ~290 with 3D. Fine technically; the UI handles it (below).

### Uploading the genome to the shader

Continuous values are interpolated on the CPU by the morph controller as usual. Only the variation slots need A **and** B in the shader. Pack into a UBO (std140; GL 3.1+): continuous genome (current) + slot `(idA, wA, aA, bA, idB, wB, aB, bB)` per slot + `t`. ~7x(26 floats + 3x8) = about 350 floats, well under the 16 KB UBO minimum. Do not use plain uniforms (1024-component fragment minimum is tight once A/B are doubled).

### Structured editor (needs the new hook)

`VisualSource.drawEditor(...)` optional. Flame draws: **Global/Camera/Tone**, **Modifiers**, **Xform tabs** (X1..X6, XF), each with Weight/Color, Pre, Post and three variation slot rows (variation dropdown by name, weight, A/B shown only when the chosen variation has parameters). Each field is still a `drawParamRow`, so modulation, learn and the kebab work. Hide empty xforms behind "+ add xform".

### Sampler hook (genome-aware randomize / mutate / breed)

`VisualSource.sampleState(strategy, rng, parentA, parentB)` optional; generic sources fall back to today's per-parameter behavior.

- **Random:** pick 2-5 active xforms; random affine with contractivity bias (mean spectral norm < 1, with a few expanding allowed); 1-3 variation slots per xform from a weighted pool (favor linear/sinusoidal/spherical/swirl-class, rare exotic); palette from library; weights normalized; camera framed to the attractor extent (estimated).
- **Mutate:** gaussian jitter on continuous params scaled by range x strength; occasional variation id swap in a slot; occasional xform add (weight 0 -> small) or remove; palette neighbor swap.
- **Breed:** whole-xform crossover (take xform i from A or B, not per parameter), globals from one parent or blended, then Mutate.
- **Quality gate:** candidate B is rejected if a low-res test render (a few frames, hidden FBO, async readback) is blank, saturated, or collapsed to a point/line. Cheap CPU pre-check on affine contractivity first. Open: where the gate runs relative to the morph promote (one-frame delay is acceptable).

### Palette

Palette is not a `ModulatableParameter`. A `library/palettes` set of 256-color palettes; `Palette` param indexes it (discrete). Imported `.flame` palettes are added to the library by content hash; the preset embeds a copy (via a new optional `sourceData` field on `DeckPresetDto`) so presets stay portable. **Decided:** embed (portable presets; a missing palette would otherwise fail silently at a show). On load, a palette whose content hash is already in the library reuses it, otherwise it is added. `sourceData` is optional and old presets ignore it; ~1 KB per preset.

### Default macros (curated in `GeneratorDefaults`)

1 Zoom (+LensAmount) / 2 Spin (modifier) / 3 Warp (modifier) / 4 Breathe (modifier) / 5 Color (PaletteRot, Drift, PaletteHue) / 6 Glow (Exposure, Brightness, Vibrancy) / 7 Trails (Decay) / 8 Evolve (binds the global `Mutation` parameter).

### Why decomposed affine

Interpolating raw matrices collapses the shape. Interpolating rotation/scale/shear gives the swirling morph, and gives audio a clean target (bass -> rotation of xform 2).

### Morphing variation slots

An id swap cannot be interpolated. During a morph the shader evaluates **both A's and B's variation slots** and cross-weights them by morph t: A's slot weight x (1-t), B's slot weight x t. `DeckMorphController` owns the states and exposes read-only `stateA`, `stateB`, `t`. Flame reads ids and slot weights from the raw states. No separate Flame snapshot. Verify in phase 2: behavior when not morphing, when a new B is sampled, and when the user edits a slot mid-morph (edit applies to A? B? decide).

## Variations (the paper's set first)

Implement the variations defined in the paper (linear, sinusoidal, spherical, swirl, horseshoe, polar, handkerchief, heart, disc, spiral, hyperbolic, diamond, ex, julia, bent, waves, fisheye, popcorn, exponential, power, cosine, rings, fan, blob, pdj, fan2, rings2, eyefish, bubble, cylinder, perspective, noise, julian, juliascope, blur, gaussian blur, radial blur, pie, ngon, curl, rectangles, arch, tangent, square, rays, blade, secant, twintrian, cross). Verify the list against the paper itself, not from memory, before step 4.
Our additions (later): audio-spectrum-driven variation, mirrored/kaleidoscope folds, per-xform beat pulse, **3D variations and full 3D affine** (12 values per affine vs 6; adds to the parameter count), lens camera and DOF as audio targets, and others to brainstorm.

Shader structure: one GLSL function per variation, a `switch` on variation id per slot. Branch divergence is the main performance cost; mitigate by sorting is not possible, so keep K small and measure.

## Audio

Everything is a `ModulatableParameter`, so the existing sources apply. Suggested defaults for the shipped starter flames (the `randomizeBase` ranges matter for morph):
- bass -> camera zoom pulse, xform rotation
- mids -> variation weight of a slot
- highs -> color speed, palette rotation
- beat (BeatSine / bpm) -> xform scale impulse, brightness
- amplitude -> decay (trail length)

## Lineage: keep, breed, and the next-state sampler

### Pluggable sampler (works for ALL sources, not only Flame)

`DeckMorphController.sampleNewState` becomes a strategy:
- **Random**: today's behavior.
- **Mutate(A)**: perturb A within a mutation strength (per-parameter, scaled by range). This is the original's drift.
- **Pool**: pick from the Garden (weighted, e.g. by rating or recency).
- **Breed(Pool)**: crossover of two Garden members, then mutate.

### Desktop UX

- **Keep** (star) on the playing or target state: saves it to `library/presets/garden/` as a normal `.lsd` (no new persistence). Optional name/tags auto-filled with a generation counter and parents.
- **Garden** browser pane (a library root in the unified browser) with thumbnails; select offspring as parents, **Breed** produces a child that is loaded into the morph B slot or saved.
- Show use: playlist of Garden offspring, or a deck on Pool/Mutate sampling, or a hand-built morph sequence.
- A "veto" (thumbs-down) skips the current B and resamples.
- Lineage metadata (parents, generation) stored in `presetNotes`/`tags` first; a proper DTO field only if needed.

Crossover for Flame: take whole xforms (not individual parameters) from each parent, to keep xforms coherent; globals (palette, camera) picked from one parent or blended.

### Web UX (later)

Explicit **Yes / No** buttons:
- Yes: adds the current genome to a local pool and raises its breeding weight; the next genome is a mutation or crossover of liked ones.
- No: ends it and moves away (mutate in a different direction or resample).
- Genome encodes to a compact URL string -> shareable.
- **Local-only pool for v1.** A shared pool via `server/` is a later, separate decision (moderation, abuse, storage).

## `.flame` importer

- Parse the XML genome: xforms with coefs/post, variation weights, palette (256 colors), camera, color params.
- Map to our model: affine matrix -> decomposition (rotation/scale/shear), variations -> top-K by weight (document the loss), palette -> nearest of our palettes or a custom palette slot.
- Report what was dropped on import. Unsupported variations are skipped and listed.
- Palette decision: support a custom 256-color palette per preset (needed anyway for fidelity), stored in the preset.

## Phases

0. **Spike (throwaway, branch `spike/flame-source`)**: GL 3.3 state-texture (xyz) + point splat with Gaussian kernel + tone map, 12 variations, a hardcoded genome, viewable as a generator. Measure points/frame, 16F vs 32F density, kernel footprint cost, perf per deck. Gate: decide if the look is acceptable and the budget works.
1. **Source class + genome params**: `FlameSource` (extends `VisualSource`), parameter set above, preset save/load, randomization ranges, a starter flame.
2. **Morph integration**: variation-slot cross-weighting; affine decomposition interpolation; verify A->B looks right.
3. **Variation set from the paper**, with per-variation unit checks (e.g. known input/output pairs derived from the math).
4. **Flame editor UI**: xform tabs, variation slots, affine controls (maybe a small on-canvas affine handle editor later), palette picker.
5. **Sampler strategies + Keep + Garden pane + Breed.**
6. **`.flame` importer.**
7. **Audio defaults + our additions** (incl. 3D camera/DOF/lens, then 3D xforms), tooltips, docs, release notes (user_guide + dev docs + both release-notes files, per the docs-completeness rule).
8. **Web port**: WebGL2 renderer, sync via `scripts/sync_web.py`, Yes/No UI, genome URL encoding.

## Risks / open questions

- Performance budget with several decks each running ~0.25-1M points: needs a point-count/quality setting and maybe auto-scaling.
- Density estimation quality vs cost; the original's adaptive filter is expensive, our blur is an approximation.
- 16F density buffer precision at high hit counts.
- ~210 parameters: macro/Super Knob strategy and how preset randomization avoids ugly/blank states (the original filtered degenerate genomes). Need a **quality check**: reject genomes that are blank, saturated, or collapsed after a short test render, perhaps by sampling the density buffer.
- Whether the starter flames need an in-repo library of ~20-50 hand-built genomes (generated by a script, like `scripts/gen_web_presets.py`).
- Final naming (Flame / Garden / Offspring / Breed) and a credits entry for Draves & Reckase.
- Interaction with the v1.0 freeze: no code before the release; the spike may be done on a branch.
