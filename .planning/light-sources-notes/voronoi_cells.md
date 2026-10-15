# voronoi_cells (light sources, Wave 2)
Animated Worley/Voronoi cells: nine hashed feature points per pixel in a 3x3 neighbourhood, each orbiting a small path. No LSD_COST, no is3D, single pass, no PASSES. Web: transpiled entry in `web/sync_manifest.json`, compiles under `web/checks/shader_check.mjs` (ES 3.00).

## Inputs (12)
Style (ColorMethod, STEP 0..3, default 1: flat cells, cracked-glass edge glow, distance rings, faceted gem), Metric (SurfaceType, STEP 0..2: Euclidean, Manhattan, Chebyshev), Palette (STEP 0..3), Cell Scale, Jitter, Speed (signed), Edge Width, Edge Glow, HueOffset, Saturation, Brightness, Quality (Detail, STEP 1..3).

## Caps
Fixed 3x3 neighbourhood (9 feature points); the gem style adds a 4-step loop.

## Measured (Intel UHD TGL GT1, Mesa, headless harness, CPU-side glFinish span, 2026-10-14)
1080p defaults: 4.3-6.4 ms/frame (~170 fps). No NaN, err=0.
