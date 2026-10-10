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

Open: density buffer format. Spike compares RGBA16F vs RGBA32F (memory, bandwidth, saturation). Desktop GL allows float blending; WebGL2 needs `EXT_float_blend` for 32F. Also whether hit weight in alpha is enough or separate buffers are needed, and the fill-rate cost of the kernel footprint.

## Genome as parameters

Fixed capacity so the existing parameter system carries it:

- **Global:** camera (zoom, rotation, center x/y), brightness, gamma, vibrancy, decay, symmetry order, palette id/rotation/hue, background, point count, iterations per frame.
- **Per xform** (up to 6, plus a final xform, so 7 slots; unused slots have weight 0):
  - weight, color index, color speed, opacity
  - **pre-affine** decomposed: rotation, scale x/y, shear, translate x/y
  - **post-affine** decomposed the same way (identity by default)
  - **K = 4 variation slots**: discrete variation id + weight, plus up to 2 shared variation parameters per slot (e.g. blob high/low, julia-n)
- Rough count: ~15 per xform pre-variation + 4x(2..4) -> ~30 per xform, ~210 total. Too many for flat knobs, so Flame needs a **structured editor** (xform tabs) instead of a flat param list, with macros/Super Knob for performance. Params still live as `ModulatableParameter`s.
- Why decomposed affine: interpolating raw matrices collapses the shape. Interpolating rotation/scale/shear gives the swirling morph, and gives audio a clean target (bass -> rotation of xform 2).

### Morphing variation slots

An id swap cannot be interpolated. During a morph the shader evaluates **both A's and B's variation slots** (2K per xform) and cross-weights them by morph t. Decided: `DeckMorphController` owns the states and exposes read-only `stateA`, `stateB`, `t`; Flame reads the discrete ids and slot weights from the raw states, not from the interpolated parameters (which the controller writes each frame). No separate Flame snapshot. Verify against the controller code in phase 2, including what happens when not morphing and when a new B is sampled.

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
