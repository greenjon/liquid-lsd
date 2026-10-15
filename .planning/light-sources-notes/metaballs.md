# metaballs (light sources, Wave 2)
2D metaballs: 4 to 12 balls on Lissajous orbits summing an inverse-square field. No LSD_COST, no is3D, single pass, no PASSES. Web: transpiled entry in `web/sync_manifest.json`, compiles under `web/checks/shader_check.mjs` (ES 3.00).

## Inputs (13)
Balls (MaxPoints, STEP 4..12), Style (ColorMethod, STEP 0..3: shaded, iso-lines, neon outline, gooey two-tone), Palette (STEP 0..3), Ball Size, Size Variance, Orbit Speed (signed), Orbit Spread, Merge, Softness, Quality, HueOffset, Saturation, Brightness.

## Caps
Two loops over `MAXB = 12` balls (field and gradient), with a 2x2 supersample loop for anti-aliasing; no raymarching. A later pass (31f717f) added gel shading and lowered the default Quality to 0.3.

## Measured (Intel UHD TGL GT1, Mesa, headless harness, CPU-side glFinish span, 2026-10-14)
1080p defaults: 4.8-4.9 ms/frame (~200 fps). No NaN, err=0.
