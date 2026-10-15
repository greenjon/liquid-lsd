# mandelbox (heavy source notes)

Files: `library/sources/mandelbox/mandelbox.fs` (identical copy in `src/main/resources/default_sources/mandelbox/`). Header: `LSD_COST` 3, `is3D` true, CREDIT "Liquid LSD", CATEGORIES Generator / 3D / Fractal. 15 inputs.

## Parameters (stored value, default, working-unit remap)
| Input | Range | Default | Maps to |
|---|---|---|---|
| Scale | -1..1 | 0.25 | s = +(1.5 + 2x) for x >= 0 (default 2.0), s = -(1.5 + 2|x|) for x < 0 (-1.5 .. -3.5). Deliberate jump at 0: |s| < 1.5 collapses the set |
| FoldLimit | 0..1 | 0.5 | box fold limit 0.5 .. 1.5 (default 1.0) |
| MinRadius | 0..1 | 0.5 | 0.1 .. 0.9 (default 0.5) |
| MaxRadius | 0..1 | 0.333 | 0.5 .. 2.0 (default 1.0); clamped to at least MinRadius + 0.05 |
| Quality | 0..1 | 0.5 | march steps 48..128, fold iterations 6..12, AO taps 0..4 (default 88 / 9 / 2) |
| FlightSpeed | -1..1 | 0.25 | camera glides in and out along the view axis, phase = TIME * 2x * 0.2 (period about 63 s at default); negative reverses |
| Glow | 0..1 | 0.4 | halo from the near-miss step count, 0..2 |
| ColorMode | 0..4 STEP 1 | 0 | 0 orbit trap min |z| (origin), 1 orbit trap distance to coordinate planes, 2 sphere-fold fraction, 3 normal spectrum, 4 depth |
| HueOffset, Saturation (0.75), Brightness (0.5 -> x2) | 0..1 | | colour |
| Zoom | 0..1 | 0.5 | standard exponential map, 0.5 = 1x |
| RotateX/Y/Z | +/-PI | 0 | on top of a fixed built-in tilt (rotY 0.55, rotX -0.35) |

Camera distance follows an estimated half-extent `bound = fold * (|s|+1)/max(|s|-1, 0.5)` (x2.3 for s > 0, found by eye), camDist = bound * (2.1 + 0.7 cos(phase)) (never nearer than 1.4 x bound), so framing tracks Scale and FoldLimit. Safeguard: the distance estimate is evaluated at the camera (up to 6 steps) and the camera is pushed outward until it is at least 0.1 x bound from the set; the push is continuous in camDist, so there is no pop. The camera no longer flies inside the set.

## Loop caps and cost
- Hard caps: MAX_MARCH 128, MAX_FOLD 12, MAX_AO 4 (constant bounds, live counts break early). Distance estimator also bails out when |z| > 40.
- Default (Quality 0.5): up to 88 march steps x 9 fold iterations = at most 792 fold iterations per pixel (about 25 flops each, roughly 20 kflop) for rays that use every step; most rays exit far earlier. Hit pixels add 4 (normal) + 2 (AO) + 1 (colour trap) estimator calls = 63 iterations.
- Max (Quality 1): 128 x 12 = 1536 iterations + 9 estimator calls (108 iterations).
- Rough worst case at 1080p default: 2.07 Mpx x 20 kflop x 60 fps = about 2.5 TFLOP/s, under the A2000's ~8 TFLOPS; real cost is lower thanks to early exit. Unmeasured.
- Hit threshold scales with distance and pixel size (`t * fov / height * 0.6`), so far pixels stop early. Step length 0.8 x DE.
- No buffers, no passes, no textures.

## Mathematical derivation (own, from the definition only)
- Mandelbox iteration: z <- s * sphereFold(boxFold(z)) + c, with c = the sample point (Mandelbrot-style).
- Box fold: reflection of each coordinate into [-L, L]: z = clamp(z, -L, L) * 2 - z.
- Sphere fold with radii m < M: r = |z|; r < m: z *= M^2/m^2; m <= r < M: z *= M^2/r^2 (inversion); else unchanged.
- Distance estimate: track dr, the norm of the derivative of z with respect to c. The box fold has unit Jacobian norm, the sphere fold is conformal with scale k (M^2/m^2, M^2/r^2 or 1), so dr <- |s| k dr + 1, and DE = |z| / dr (the usual |G|/|G'| estimator for a linear-growth potential).
- Normal by the tetrahedral 4-tap central difference; AO as distance-shortfall comparisons along the normal (own form); orbit traps are min |z| and min distance to a coordinate plane over the iterations.
- No Shadertoy, Fragmentarium or forum code was read or recalled; git history of the old removed mandelbox was not used.

## Verification done
- ISFParser parses the header (15 inputs, gpuCost 3, is3D true). `BundledIsfRangesTest` and `StockDiscreteInputsAuditTest` pass for mandelbox (the combined run failed only on another agent's `fluid_ink.fs` input `Emitters` 1..4, not on mandelbox). Both trees identical.
- Compiled and rendered under the app's real wrapper (`ISFParser.buildGLSLFragmentShader`) on a hidden GL 3.3 core context at 480x270 with a throwaway test (deleted): no GL errors, no black frame or NaN at defaults, all 5 colour modes, Scale -1/-0.5/-0.25/0.1/0.5/1, Quality 0/1, extreme fold/radius settings, TIME 3/40/60/70, Glow 1 + FlightSpeed 1. Default look checked by eye: a lit red/green cube with fractal chambers on a dark gradient.

## Known issues
- Scale has a discontinuity at 0 (+1.5 <-> -1.5); automating it across 0 jumps.
- The camera path was changed to stay outside (see above). Sweep (TIME 0..600 step 1.7 x 8 Scales x 3 FoldLimit x 3 MinRadius x 3 MaxRadius x 3 rotations x Quality 0/1 at FlightSpeed 1 = 457,488 frames): DE at the camera never fell below the 0.1 x bound clearance. The push fires in about 6% of those frames, only at the extremes (Scale near -1 or +0.05, i.e. |s| = 3.5 or 1.6, where the extent estimate is too small), by up to 0.34 bound. Rendered TIME 0/3/8/40/200/377 and the Scale/Fold corners: no flat or solid frames; the closest pass (cos phase = -1) is a close-up of the surface.
- Low Quality leaves visible stair-stepping and fills grazing rays as hits.
- The look on a real display is unseen; the bound factor 2.3 for s > 0 is an eyeballed fit.

## Draft provenance-ledger row
| Mandelbox source (`mandelbox`) | Defining mathematics: box fold, sphere fold (min/max radius), scale, running-derivative distance estimate |
| Own derivation from the definition; no third-party shader or Fragmentarium code read or recalled; old removed `mandelbox` history not used | `library/sources/mandelbox/mandelbox.fs`, `.planning/heavy-sources-notes/mandelbox.md` | n/a |

(Full table form: `| Mandelbox source (mandelbox) | Box fold, sphere fold and scale iteration with a running-derivative distance estimate, from the definition | Own derivation; no third-party shader code read or copied | library/sources/mandelbox/mandelbox.fs, this notes file | n/a |`)

## Draft SHADERS_LICENSE row (Bundled Visual Sources)
| mandelbox | Original work, Liquid LSD. Raymarched Mandelbox (box fold, sphere fold, scale) with orbit-trap colouring, derived from the defining mathematics; no third-party code | Same licence as the other bundled sources |

Algorithm Credits candidate: the Mandelbox construction is due to Tom Lowe (Tglad), 2010 (fractal definition only, no code used).

## A2000 timings (RTX A2000 Laptop GPU, confirmed by GL_RENDERER, PRIME offload; 1920x1080, hidden GL 3.3 core context, offscreen RGBA8, avg of 20 frames after 3 warm-up)
| Case | ms | fps |
|---|---|---|
| mandelbox default (Quality 0.5) | 7.7 | 130 |
| mandelbox Quality 1 | 8.4 | 120 |
| mandelbox Quality 0 | 5.0 | 199 |
| Quality 1, Zoom 1, closest camera, Scale 1 (s = -3.5) | 13.6 | 74 |
| Quality 1, Zoom 1, closest camera, Scale 0.05 / Fold 0 / MaxRadius 1 | 27.4 | 37 |
| Quality 1, Zoom 1, closest camera, default Scale | 29.0 | 35 |
| worst corner found: Quality 1, Zoom 1, closest, Scale 0.1, Fold 1, MinRadius 1 | 42.0 | 24 |
Defaults are well inside the 60 fps target; the full-quality typical view is about 120 fps. Only a deliberate corner (maximum zoom with a close camera and an extreme parameter set) falls below 30 fps. Single deck only; one measurement session, GPU clocks not locked.
Same method for the others (not edited): kifs default 4.8 ms (210 fps), Quality 1 5.7 ms (177), Quality 0 2.1 ms (474); mandelbulb default 5.2 ms (193), Quality 1 5.7 ms (174), Quality 0 4.5 ms (223). Corners were not searched for those two.
