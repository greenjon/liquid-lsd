# aurora_ribbons (light source, no LSD_COST)

Aurora-borealis curtains over a starfield. Single pass, no texture reads, no is3D.

## Inputs (12)
Ribbons (STEP 1, 3..6, def 4), CurtainHeight (0.5), WaveAmp (0.5), WaveSpeed (-1..1, def 0.3, signed), Shimmer (0.5),
SkyDarkness (0.6), PaletteMode (STEP, 0..3: green-violet, ice cyan-blue, green-crimson, gold-magenta), SurfaceType/"Horizon"
(STEP, 0..2: sky only, mountains, water reflection; def 1), Detail/"Quality" (STEP, 1..4 noise octaves, def 2),
HueShift, Saturation (0.65), Brightness (0.5). All remapped via `Name_` defines.

## New discrete selector names
`Ribbons` (3..6, STEP 1) is new and needs adding to the BundledIsfRangesTest discrete allowlist at integration.
`PaletteMode`, `SurfaceType`, `Detail` are already allowlisted.

## Derivation
- Each ribbon i has a base line yb(x,t) = base_i + A * (sin + 0.5 sin + <=4 octaves of periodic value noise); sine phases are `TAU*fract(TIME*rate)`, noise time axis is wrapped at 256 (the hash lattice period), so it is seamless.
- Curtain brightness from d = s - yb (s = height above horizon): exp(-max(d,0)/H) upward glow, exp(32 min(d,0)) sharp fall below, plus a bright lower-edge bump 1.6 exp(-28|d|); steep base-line slope (analytic from the sines) brightens folds (edge-on sheet).
- Rays: vertical stripes from value noise in (x, time), faded in with height; Shimmer blends flat to striped.
- Colour: 3-stop gradient indexed by dd/(3H) with a per-ribbon offset, added across ribbons, tone-mapped `1-exp(-x)`; hue rotate about the grey axis, saturation, brightness.
- Stars: one jittered point per 1/150 cell, hash-selected (3%), twinkle phase wrapped via fract, suppressed under bright aurora.
- Horizon: mountains = sine+noise ridge, near-black land; water = mirror about the horizon with a ripple offset, dimmed and darkened with depth, low treeline ridge at the shore.

## Caps / cost
Loop bound 6 ribbons (break on Ribbons), noise octave loop bound 4. Per ribbon: 1 ray noise + <=4 octave noise (cull with `continue` when the pixel is beyond the curtain's glow reach). One aurora evaluation per pixel (water reflects by remapping coordinates, not a second pass). Stars: 1 hash per pixel + 2 on star cells.

## Measured (Intel UHD TGL GT1 iGPU, CPU-side glFinish span, machine shared with other agents so noisy)
1080p defaults (4 ribbons, Quality 2): ~10 to 15 ms (an un-culled first version was 19 ms). 540p: ~3.7 ms before culling.
Worst case (6 ribbons, Quality 4, water): ~20 to 28 ms. Lower Ribbons/Quality to trade speed.

## Verification
Rendered (NVIDIA, 480x270) at default, all-max (6/Q4/water/crimson/speed -1), all-min (3 ribbons, flat, sky only), water+ice: no NaN, err=0, no black frame except by design. Time phases and noise axis wrapped; GLSL uses only uint/int ops valid in 3.30 and ES 3.00.

## Web
Expected compatible (no textures, no uniform initialisers, uint/ivec2 bitwise ops valid in ES 3.00, `continue` in a constant-bound loop). syncWeb not run; manifest entry is for the integration pass.

## Known
Wave Speed change shifts phase (phases are TIME*speed). At max Brightness the aurora washes toward white. Land is near-black by design.
