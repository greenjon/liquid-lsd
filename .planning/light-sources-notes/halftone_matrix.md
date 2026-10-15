# halftone_matrix (light sources, Wave 2)
Procedural halftone and LED matrix: a moving scalar pattern sampled per cell of a rotated grid. No LSD_COST, no is3D, single pass, no PASSES. Web: transpiled entry in `web/sync_manifest.json`, compiles under `web/checks/shader_check.mjs` (ES 3.00).

## Inputs (13)
Pattern (SurfaceType, STEP 0..3: plasma, radial pulse, diagonal bands, low-octave noise), Dot Style (ColorMethod, STEP 0..3: round halftone, square LED, line screen, CMYK-style three-screen), Palette (STEP 0..3), Grid Density, Dot Sharpness, Pattern Speed (signed), Pattern Scale, Grid Rotation, Contrast, HueShift, Saturation, Brightness, Quality (Detail, STEP 1..3).

## Caps
The CMYK style samples three screens (3 iterations); neighbour-cell lookups are a fixed 3x3. Noise pattern is low octave.

## Measured (Intel UHD TGL GT1, Mesa, headless harness, CPU-side glFinish span, 2026-10-14)
1080p defaults: 5.1-5.4 ms/frame (~190 fps). No NaN, err=0.
