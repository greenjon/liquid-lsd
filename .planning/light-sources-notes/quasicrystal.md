# quasicrystal (light source, Wave 1)

Files: `library/sources/quasicrystal/quasicrystal.fs` (identical copy in `src/main/resources/default_sources/`). No `LSD_COST`, no `is3D`, single pass, no texture reads.

## Maths
Sum of N cosine gratings, directions d_k = (cos(pi k/N + spin), sin(pi k/N + spin)), k = 0..N-1:
`S(p) = sum_k cos(K * dot(p, d_k) + phi_k(t))`, `q = S / sqrt(N/2)` (unit standard deviation, since each cos has variance 1/2 and the terms are roughly independent). N = 5, 7 give 10/14-fold quasi-periodic (Penrose-like) symmetry, N = 8 gives 16-fold; N = 6 is crystalline (hexagonal) by construction. Because cos is even, directions over [0, pi) cover all 2N wave vectors. Phases drift at per-grating rates `w_k = 0.7 + 0.6*fract(0.618034*(k+1))`, so the pattern evolves (phason-like) rather than just translating.

## Inputs (13)
Symmetries (STEP 5..8, default 7), ColorMethod = Style (STEP 0..3: smooth, banded with contour lines [default], two-tone threshold, neon lines), PaletteMode (STEP 0..3 cosine palettes: rainbow, ember, deep sea, neon), Frequency (K = 2pi*(1.5..12) cycles per screen height), Bands (0.5..4 band density), Sharpness (edge softness / line tightness), Rotation (-1..1 = +/-pi), SpinSpeed (signed, +/-0.6 rad/s), DriftSpeed (signed, +/-2 rad/s), Quality, HueOffset, Saturation, Brightness (x2). Selector names reuse the `BundledIsfRangesTest` allowlist (Symmetries, ColorMethod, PaletteMode).

## Cost and caps
Grating loop constant bound 8 (early break at N). Quality < 0.5: 1 sample; >= 0.5 (default 0.5): 2x2 supersample, i.e. up to 4*8 = 32 cos plus one extra centre evaluation for `fwidth` contour widths (~40 cos total, a few hundred FLOPs per pixel).

## Precision
Time-derived phases are wrapped with `mod(..., 2pi)`; spin angle likewise (seamless). Drift phase wrap is not seamless for non-integer w_k but only occurs when TIME*speed passes 2pi multiples in float, which is imperceptible (checked at TIME=100000, no artefacts or NaN).

## Web
Expected to transpile cleanly: no `#version`, no uniform initialisers, constant loop bounds, `fwidth`/`inversesqrt` are valid in ES 3.00, no texture reads, no GLSL 4 functions. `sync_web.py` only prepends `#version 300 es` + `precision highp float;`. Not run (syncWeb is the integration pass). Nonconstant `continue` in nested loops is valid ES 3.00.

## Verification (Intel UHD Graphics TGL GT1, Mesa, default)
- Rendered 960x540 at default, all four styles/palettes, N = 5/6/8, all inputs at max (TIME=100000) and at min: no NaN/black frames (the all-min frame is black only because Brightness = 0), glGetError 0.
- 1080p CPU-side glFinish per frame at defaults: ~3.6-3.7 ms steady (first frames ~5 ms), i.e. far above 60 fps. 540p: 3-7 ms (dominated by harness noise and concurrent agents on the same GPU).
- Known: fwidth contours are evaluated from the centre sample, so thin lines may show slight aliasing at Quality < 0.5.
