# kali_set (light sources, Wave 2)
Kali-family inversion-fold fractal in the plane: z -> |z|/(z.z) - c with optional per-step rotation, coloured by orbit traps. No LSD_COST, no is3D, single pass, no PASSES. Web: transpiled entry in `web/sync_manifest.json`, compiles under `web/checks/shader_check.mjs` (ES 3.00).

## Inputs (14)
Orbit Radius, Orbit Speed (signed; c circles on a small animated path), Iterations, Quality, Twist Per Step (signed), Trap Type (SurfaceType, STEP 0..3: point, line, circle, combined; default combined), Trap Scale, Zoom, Centre Drift, Color Mode (STEP 0..3), Palette Mode (STEP 0..3), HueOffset, Saturation, Brightness.

## Caps
One fold loop with a hard cap of 24 iterations; Iterations and Quality lower the effective count. Zoom here is an internal input (default 0.4) because the effect needs it.

## Measured (Intel UHD TGL GT1, Mesa, headless harness, CPU-side glFinish span, 2026-10-14)
1080p defaults (Quality 1.0): 6-13 ms/frame, rising over the run (6.1, 7.7, 12.9 ms at frames 30, 60, 90 as the orbit moves). Roughly 80-160 fps. No NaN, err=0.
