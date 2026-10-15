# isometric_voxels (light sources, Wave 2)
Q*bert-style isometric cube field with analytically resolved occlusion per height layer. No LSD_COST, no is3D, single pass, no PASSES. Web: transpiled entry in `web/sync_manifest.json`, compiles under `web/checks/shader_check.mjs` (ES 3.00).

## Inputs (12)
Wave (SurfaceType, STEP 0..2: ripple, noise, diagonal sweep), Cell Size, Max Height, Wave Speed (signed), Wave Scale, Edge Width, Face Contrast, Palette (STEP 0..3), Quality, HueOffset, Saturation, Brightness.

## Caps
One loop over `MAX_LAYERS = 8` height layers from top to bottom; the layer count is clamped by Max Height. No raymarching.

## Measured (Intel UHD TGL GT1, Mesa, headless harness, CPU-side glFinish span, 2026-10-14)
1080p defaults: 4.9-5.1 ms/frame (~200 fps). No NaN, err=0.
