# mandelbulb (heavy source notes)

Files: `library/sources/mandelbulb/mandelbulb.fs` and an identical copy in `src/main/resources/default_sources/mandelbulb/`. Header: `LSD_COST` 3, `is3D` true, CREDIT "Liquid LSD", CATEGORIES Generator / 3D / Fractal. Desktop only (GLSL 3.30 core; not for web sync).

## Parameters (all continuous inputs 0..1 or -1..1; angles +-PI)
| Input | Default | Remap / meaning |
|---|---|---|
| Power | 0.42857 | `Power_ = 2 + 14*Power` (8.0 at default; 2..16) |
| PowerSwing | 0 | adds `4*Swing*sin(0.35*TIME)` to the power, clamped 2..16 (0 = static) |
| JuliaMix | 0 | `c = mix(p, juliaC, JuliaMix)`; juliaC is a fixed constant with a small time wobble |
| Bailout | 0.33333 | `Bailout_ = 2 + 6*Bailout` (4.0 default) escape radius |
| Quality | 0.5 | steps 48..80, iterations 6..10, AO taps 2..4 (see caps) |
| FlightSpeed | 0.25 | signed; turntable orbit about the bulb's pole plus a slow dolly in/out; `FlightSpeed_ = 2*FlightSpeed` |
| Glow | 0.3 | `Glow_ = 2*Glow` halo from the march's closest approach |
| ColorMode | 0 (STEP 1, 0..4) | 0 origin trap, 1 plane traps, 2 smooth escape count, 3 normal spectrum, 4 gold ramp on origin trap |
| HueOffset / Saturation / Brightness | 0 / 0.8 / 0.5 | `Brightness_ = 2*Brightness` |
| Zoom | 0.5 | exponential, 0.5 = 1x (`Zoom_` define) |
| RotateX/Y/Z | 0 | applied after the built-in tilt (-1.15 rad about X, so the default views the bulb from the side, not down the pole) |

## Loop caps and cost
- Constant caps: `MAX_STEPS` 80, `MAX_ITERS` 10, `MAX_AO` 4. Live counts from Quality: steps `48+32q` (default 64), iterations `6+4q` (default 8), AO taps `2+2q` (default 3).
- Early-outs: analytic bounding sphere (radius `1.25 + 1.5/n`) so missed pixels run nothing; the march starts at the sphere entry and stops at its exit; per-iteration bailout break; hit threshold scales with distance (`0.00035*t`).
- Per DE evaluation: up to `iters` loop passes, each with sqrt, log, exp, acos, atan, sin x2, cos x2 (one pow removed by `r^n = exp(n log r)` shared with `r^(n-1)`).
- Per pixel worst case (miss-free, no early hit): 80 x 10 = 800 passes, plus 6 (normal) + 1 (trap) + 4 (AO) DE evaluations at the hit = 11 x 10 = 110. Typical hits take 20-40 steps; background pixels cost almost nothing. Rough default estimate: 30 steps x 8 passes + 10 DE x 8 = about 320 passes per covered pixel; the bulb covers roughly a third of the frame.
- No float buffers, no multipass.

## Derivation sources
Written only from the published mathematics: the triplex / spherical-coordinate power map z -> z^n + c (White and Nylander's construction as described in the literature: r = |z|, theta = acos(z_z / r), phi = atan2(z_y, z_x), z^n = r^n (sin(n theta) cos(n phi), sin(n theta) sin(n phi), cos(n theta))); the running derivative dr_{k+1} = n r_k^(n-1) dr_k + 1 and distance estimate 0.5 log(r) r / dr (the Hubbard-Douady potential estimate generalised to the power-n map); orbit-trap colouring (min |z|, min |z_y|, min |z_x|) and the smooth escape count `i + 1 - log(log r / log bailout) / log n`. Ray / sphere intersection, central-difference normals and the distance-based AO (occluded amount = tap height minus DE at the tap, geometric weights) are textbook. No Shadertoy or Fragmentarium code was consulted or recalled, and the old removed `mandelbulb` (commit `fc1ad3f`) was not used. Camera, palette and lighting are our own, in the shared helper style of `gyroid_hyperspace`.

## Verification done
- Header parses (`ISFParser.parseHeader`): 15 inputs, `gpuCost` 3, `is3D` true.
- Compiled and rendered through the app's own wrapper (`ISFParser.buildGLSLFragmentShader` + `Shader`) on a hidden GL 3.3 core context (Mesa Intel UHD TGL GT1, so not the A2000) at 960x540: no GL errors; default, Quality 0 and 1, ColorMode 1-4, Power 0 and 1, JuliaMix 1, Zoom 0 and 1, and a max-stress combination all produce finite images; the default looks like a coloured Mandelbulb (viewed by eye from PNG dumps).
- Timings on that integrated GPU at 960x540 (about a quarter of 1080p pixels): default ~10 ms, Quality 1 ~11 ms, Power 2 ~20 ms, deep zoom plus Bailout max plus swing ~22 ms. These are not A2000 numbers.
- `BundledIsfRangesTest` has no entry for mandelbulb (passes for it; the only failure in the run was `fluid_ink.fs` Emitters, another agent's file). `StockDiscreteInputsAuditTest` and `StockGpuCostAuditTest` pass.

## Known issues
- Fractional Power: the spherical-angle multiplication has a seam at phi = +-PI, so non-integer powers show a cut plane/sector (inherent to the triplex map). Integer powers (default 8) are clean; animating the power sweeps a seam through the shape. Power 2 gives a lopsided, non-bulb shape (inherent).
- The distance estimate is an underestimate, not exact, near thin features and in Julia mode; the march step is scaled by 0.8 and tiny holes can show at low Quality.
- FlightSpeed uses `TIME * speed`, so changing the speed live jumps the camera angle (same behaviour as `gyroid_hyperspace`).
- Julia constant is fixed (with a 0.12 wobble); JuliaMix 1 gives a smoother bulb-like blob rather than a dramatic shape. Only inspected at one view.
- Not verified: A2000 frame rate (60 fps at 1080p default, ~30 at max), 1080p visuals, light theme irrelevant. Mixed hue: HueOffset 0 gives purple/teal.

## Draft provenance-ledger row
| Mandelbulb generator (`mandelbulb.fs`) | Triplex power map z^n + c in spherical coordinates (White and Nylander's construction) and the running-derivative distance estimate 0.5 log(r) r / dr, from the published mathematics; orbit-trap and smooth-escape colouring are textbook | Our own derivation and implementation; no third-party shader source read or recalled-and-copied; the earlier removed `mandelbulb` (`fc1ad3f`) not used | `library/sources/mandelbulb/mandelbulb.fs`, `.planning/heavy-sources-notes/mandelbulb.md` | The assistant may have seen community Mandelbulb shaders in training; the owner to accept the clean-room statement |

## Draft SHADERS_LICENSE row
| mandelbulb | Liquid LSD | Original (MIT, project licence) | Raymarched Mandelbulb; formulas from the published triplex power map and distance estimate. Algorithm credit: Daniel White and Paul Nylander (Mandelbulb, 2009) |
