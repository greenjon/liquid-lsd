# harmonograph (light source, Wave 2)

Files: `library/sources/harmonograph/harmonograph.fs` (identical copy in `src/main/resources/default_sources/`). No `LSD_COST`, no `is3D`, single pass, no texture reads.

## Maths
Trail position u in [0,1] (0 oldest, 1 = beam head), time relative to head t = S(u-1), S = 2pi*cycles/max(fx,fy), cycles = 0.75..3.75 (Trail). Per axis two damped sinusoids:
`x = e(u) (0.7 sin(fx t + a1) + 0.3 sin(fx t/2 + a2))`, `y = e(u) (0.7 sin(fy t + b1) + 0.3 sin(fy t/2 + b2))`, `e(u) = exp(-D (1-u))` (older parts decayed more, so the trail spirals in). Frequencies are the preset ratio normalised so the fastest axis is 1 rad/unit, fy scaled by (1 + 0.08*Detune). The head advances at 0.8 rad/s (each term's head phase wrapped with `mod(.., 2pi)`), and signed phase drift (+-1.5 rad/s, wrapped) rotates the y phase and the second-harmonic phases. The curve is sampled as n segments (n = 16..64 from Quality, loop bound 64). Per pixel: point-to-segment distance; core = max over segments of exp(-d^2/w^2) * fade * dwell (fade = 0.06 + 0.94 u^2 age gradient; dwell = 1..2.5 brightening of slow parts, the phosphor dwell effect); glow = sum of g^2/(d^2+g^2) weighted by segment length (so joints are not double counted), cut off smoothly at 6 glow radii (early `continue`, ~30% faster). Colour from the trail age of the nearest segment (core) and the glow-weighted mean age, plus a white-hot core term, hue rotation about the grey axis, saturation, brightness, soft tonemap `1-exp(-1.4 c)`.

## Inputs (13)
RatioPreset = Ratio X:Y (STEP 0..7, default 2: 1:1, 1:2, 2:3, 3:4, 3:5, 4:5, 5:6, 3:7), PaletteMode (STEP 0..3: phosphor green [default], rainbow, ember, ice), Detune (signed, +-8% of Y), DriftSpeed = Phase Drift (signed), Damping (0..4 e-folds over the trail), LineWidth (0.0015..0.0135 of screen height), Glow, Trail = Trail Length, Rotation (+-pi), Quality (default 0.6 = 45 segments), HueOffset, Saturation, Brightness (x2).

## New discrete selector name
`RatioPreset` (not in the `BundledIsfRangesTest` allowlist; integration pass must add it, plus `StockDiscreteInputsAuditTest` if it lists names). `PaletteMode` is reused.

## Caps and cost
Segment loop bound 64 (constant, `break` at n). Per segment: 4 sin + 1 exp (curve point), one sqrt/inversesqrt, one exp. Cost is dominated by the curve point evaluation, repeated per pixel.

## Precision / NaN
Time phases wrapped per term; divisions guarded (`max(l2,1e-10)`, `len+1e-4`, rCut2 from g >= 0.00225 so never infinite). Checked default, all-min, all-max with TIME=100000: no NaN or black frame, glGetError 0.

## Web
Expected to transpile: no `#version`, constant loop bounds, `continue`/`break` in the loop, `inversesqrt`/`cross` valid ES 3.00, no uniform initialisers. Not run (syncWeb belongs to the integration pass).

## Measured (Intel UHD TGL GT1, Mesa, harness glFinish span, other agents loading the GPU)
1080p default (Quality 0.6): ~15-21 ms per frame (about 50-65 fps, single deck); Quality 1.0: ~30-45 ms; Quality 0.5 not separately clean. 540p roughly a quarter of that. The first version without the glow cutoff measured ~40 ms at Quality 1.0. This is the heaviest of the light sources, so it is borderline for "comfortably above 60 fps" at 1080p; lower Quality or output resolution if needed (no LSD_COST by owner decision).

## Known issues
At 1080p and Quality 0.6 the curve shows slight polygon faceting on the fastest axis (about 20 segments per cycle); Quality 1.0 hides most of it. Trail is limited to 3.75 cycles of the fast axis so n <= 64 stays smooth enough.
