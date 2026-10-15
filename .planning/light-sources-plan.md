# Light Sources Batch (plan, 2026-10-14)

Status: Wave 1 (5 sources) written, integrated and tested 2026-10-14 (uncommitted); unseen in the app by the owner. Companion to `heavy-sources-plan.md`; read that file for the shared codebase facts (file locations, ISF dialect, normalised ranges, registration touch-points, provenance rules, doc checklist). This plan only records what differs.

## Goal

A batch of cheap, distinctive generators that hold a usable frame rate on **integrated graphics** (Intel Iris Xe floor, shared DDR bandwidth) with two decks, an FX chain and a crossfade running. Heavy sources are tagged `LSD_COST: 3`; these are the opposite tier.

## Decisions (owner, 2026-10-14)

- Ships in **v1.0** (freeze exception, as for the heavy batch).
- **No badge.** A light source is signalled by the *absence* of a cost badge. Do **not** add `LSD_COST` to these sources and do not add a new tier. (`LSD_COST` 1 or 2 stays unused.)
- **Web where possible.** Every source that fits GLSL ES 3.00 gets a `web/sync_manifest.json` entry and a `syncWeb` run. Desktop-only is acceptable only when a source needs something the web runtime lacks; record the reason in the notes file. `web/isf.js` already handles `PERSISTENT` passes (verify with a real test, see feedback source).
- Placement (stock vs pack) follows whatever is decided for the heavy batch (Open question 1 there); source agents deliver only `.fs` pairs and notes.
- Algorithms are written clean from the mathematics (provenance rules apply, no copying Shadertoy or community code).

## Cost budget (per source, at 1080p default parameters)

- A source should cost on the order of 500 to 1000 FLOPs per pixel at most. Rules of thumb:
  - Analytic or closed-form only. Raymarching only with a hard cap of 24 steps (prefer none).
  - Noise: at most 3 to 4 octaves, hash-based (no noise textures). Prefer value or gradient noise built from `fract`/`sin`-free integer-style hashes that behave the same on all GPUs.
  - Loops: constant bounds, cap at 24 iterations unless the source is a fold loop that is clearly cheaper per iteration (hyperbolic tiling: 12 to 16).
  - No full-resolution multipass. Persistent buffers use a fixed small `WIDTH`/`HEIGHT` (256 to 512 per side), `FLOAT` only if precision demands it, default `GL_RGBA8`.
  - No texture reads in single-pass sources.
- Every source still exposes a **Quality** input that caps iterations, octaves or element count, so the owner can trade fidelity for speed.
- Web constraint: GLSL ES 3.00, `highp float`, no uniform initialisers, no GLSL 4 functions. Keep code valid in both 3.30 and ES 3.00. Loops over constants only; avoid `float` precision traps (large `TIME * freq` arguments to `sin`: wrap time with `mod(TIME, period)` or use `fract` where seamless).

## Wave 1 catalogue

All generators, no `is3D` (the host provides View zoom and rotate), CATEGORIES `["Generator", ...]`, no `LSD_COST`.

1. `quasicrystal`: sum of N (5 to 8, STEP selector) rotated cosine gratings with slow phase drift, giving Penrose-like quasi-periodic interference. Cosine palette, contour/threshold style selector, rotation and frequency controls.
2. `thin_film`: oil-slick iridescence. Low-octave noise height field, finite-difference normal, thin-film interference colour ramp from film thickness and view angle, slow flow. Single pass.
3. `tunnel_scenes`: analytic ray-plane and ray-cylinder intersections, no marching. Selector between a synthwave grid floor with horizon, a Truchet-tiled tunnel, and a polygonal wormhole (N sides). Forward speed signed, twist, glow.
4. `video_feedback`: classic video-feedback loop. Persistent low-res buffer (fixed 512 grid), previous frame sampled with zoom, rotate and offset, hue-shift and decay, seeded by a few moving analytic shapes. Re-seeds itself when the state is empty (no reliance on `FRAMEINDEX`). Use `TIMEDELTA` or document frame-rate dependence.
5. `hyperbolic_tiling`: Poincare-disk {p,q} tiling by reflection folds (12 to 16 iterations), Escher-style fill with cell-id colouring, edge glow, drifting view in the disk (Mobius translation). `P` and `Q` selectors with STEP, restricted to hyperbolic pairs ((p-2)(q-2) > 4).

## Wave 2 (later, owner-triggered)

Cyclic CA / Hodgepodge (256 grid), 2D metaballs, multi-source ripples, harmonograph/Lissajous, aurora ribbons, isometric voxel tiling, halftone/LED matrix, Kali-set orbit trap, girih star patterns, glitch generator, Worley/Voronoi cells.

## Per-source template

Same as `heavy-sources-plan.md` "Per-source template", except: **no `LSD_COST`**, no `Rotate`/`Zoom` inputs unless the effect needs them internally, and 8 to 14 inputs total. Normalised ranges, `STEP` on selectors, `Name_` remap block, NaN guards, neutral attractive default, Quality input.

## Deliverables per source agent

- `library/sources/<id>/<id>.fs` and the identical copy in `src/main/resources/default_sources/<id>/<id>.fs`.
- `.planning/heavy-sources-notes/<id>.md` (params, iteration caps, derivation, web status and any web caveat, known issues).
- Verify with the scratch render harness (`.planning/heavy-sources-notes/README-wave2-harness.md`) at 540p and 1080p; report timings measured on the Intel iGPU (the laptop default), not just the NVIDIA GPU.
- Agents do **not** edit shared registration files, the web manifest, docs, or release notes. The integration pass does that (below).

## Integration pass (after sources land)

Touch-points as listed in `heavy-sources-plan.md` "Making a source stock", minus the `StockGpuCostAuditTest` heavy allowlist (these are not heavy), plus `web/sync_manifest.json` entries and `./gradlew syncWeb` / `checkWebSync`, ledger and licence rows, user guide, `rendering.md`, both release-notes files, tooltips. Also run `BundledIsfRangesTest`, `StockDiscreteInputsAuditTest`, and `diff -rq library/sources src/main/resources/default_sources`.

## Checklist before a source counts as done

- [ ] No `LSD_COST` key; both tree copies identical.
- [ ] Compiles under 3.30 core and passes the web transpile (ES 3.00) with no warnings.
- [ ] Range and discrete audits pass; no NaN or black frame at default, min and max of every input.
- [ ] Measured on the Intel iGPU at 1080p: single deck comfortably above 60 fps at defaults (record the number).
- [ ] Looks right at 540p, 720p, 1080p and after resize (persistent state re-seeds).
- [ ] Provenance and licence rows, docs and release notes (integration pass).
