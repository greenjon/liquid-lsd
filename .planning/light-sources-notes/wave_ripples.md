# wave_ripples (light sources, Wave 2)
Interference of 2 to 8 travelling circular waves, each cos(k r - w t) with distance falloff. No LSD_COST, no is3D, single pass, no PASSES. Web: transpiled entry in `web/sync_manifest.json`, compiles under `web/checks/shader_check.mjs` (ES 3.00).

## Inputs (13)
Sources (Symmetries, STEP 2..8), Wavelength, Wave Speed (signed), Orbit Speed (signed), Falloff, Layout (SurfaceType, STEP 0..2: Lissajous, ring, ...), Style (ColorMethod, STEP 0..3: shaded height, contours, neon crests, thin-film ramp), Palette Mode, Contrast, Quality (Detail, STEP 1..4, sets the anti-alias grid up to 4x4), HueShift, Saturation, Brightness.

## Caps
Loops over `MAXSRC = 8` sources (positions precomputed once per pixel, then summed); Quality adds up to 4x4 supersampling.

## Measured (Intel UHD TGL GT1, Mesa, headless harness, CPU-side glFinish span, 2026-10-14)
1080p defaults (4 sources, Quality 2): 6 ms and 12 ms alternating (about 80-160 fps, bimodal, likely iGPU clocking). Worst case (Quality 4, 8 sources): 30-75 ms, so lower Quality first on integrated graphics. No NaN, err=0.
