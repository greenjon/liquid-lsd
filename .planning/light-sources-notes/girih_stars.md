# girih_stars (light sources, Wave 2)
Islamic girih strapwork on a periodic lattice (square, hexagonal or 4.8.8), Hankin-style lines from edge midpoints at an adjustable contact angle. No LSD_COST, no is3D, single pass, no PASSES. Web: transpiled entry in `web/sync_manifest.json`, compiles under `web/checks/shader_check.mjs` (ES 3.00).

## Inputs (14)
Star Points (Symmetries, STEP 2 over 4..8), Style (SurfaceType, STEP 0..2: lattice), Palette (STEP 0..3), Tile Scale, Contact Angle, Angle Sweep, Morph Speed (signed), Line Width, Interlace (over-under), Glow, Quality, HueOffset, Saturation, Brightness.

## Caps
The plane is folded into the fundamental triangle of the wallpaper group, then a loop of 4 nearest-strap candidates is evaluated; no variable-bound loops.

## Measured (Intel UHD TGL GT1, Mesa, headless harness, CPU-side glFinish span, 2026-10-14)
1080p defaults: 7.5-8.5 ms/frame (~120 fps). No NaN, err=0.
