# thin_film (light source, no LSD_COST)

Oil-slick iridescence. Single pass, no texture reads, no is3D.

## Inputs (11)
FlowSpeed (-1..1, def 0.3, signed), Scale (0.3), FilmThickness (0.35, centre 150..900 nm), ThicknessRange (0.5, spread 60..900 nm),
Contrast (0.4), Turbulence (0.4, domain-warp strength + fbm gain), Sheen (0.5), Detail/"Quality" (STEP 1, 1..4 octaves, def 2; name is in the BundledIsfRangesTest discrete allowlist),
HueShift, Saturation (0.75), Brightness (0.5). All remapped via `Name_` defines.

## Derivation
- Height h: 2-stage domain-warped value-noise fbm (quintic interpolation), 1..4 octaves, gain 0.5+0.12*turb. Two extra height samples give a finite-difference normal.
- Thickness d = FilmThickness + (h-0.5)*Range nm. Film index n=1.45; cos(theta_t) = sqrt(1 - sin^2(theta)/n^2), theta from normal vs a per-pixel view ray.
- Optical path difference = 2 n d cos(theta_t). Top reflection flips phase, bottom does not, so R(lambda) = 0.5 (1 - cos(2 pi OPD / lambda)) with lambda = 610/540/460 nm for R/G/B. Coherence fade exp(-d/1500) mixes toward 0.5 for thick films (pastel wash).
- Colour = R * (0.55 + 1.2 Schlick fresnel); hue rotation about the grey axis, saturation about luma, brightness scale, plus Blinn-style specular sheen (two lobes).

## Caps / cost
Hash noise: 256x256 periodic uint hash (no textures, no sin). Per pixel: 4 warp noise evals + 3 x (<=4) fbm evals; fixed 4-iteration loop with break. Flow phase wrapped `mod(TIME*speed, 256)`; octave offsets are integer vectors so the wrap is seamless (only a change of speed shifts phase). Checked at TIME=100000: fine.

## Measured (Intel UHD TGL GT1 iGPU, CPU-side glFinish span)
1080p: ~6.1 ms at defaults (Quality 2), ~10 ms at Quality 4 (earlier heavier hash was 26 ms; replaced by a cheaper 2-multiply hash). 540p: ~2.8 ms.

## Verification
Rendered at default, all-max (Quality 4, flow -1), all-min (black by Brightness 0, no NaN), TIME=1e5: no NaN, err=0.

## Web
Expected compatible: uses uint/int bitwise ops and ivec2 (valid ES 3.00), no uniform initialisers, no texture reads, only a local `const float`. Transpile rules in scripts/sync_web.py only rewrite the #version line and strip uniform initialisers; nothing here is affected. syncWeb not run. Manifest entry is for the integration pass.

## Known
Faint lattice character possible at Scale max with Quality 1 (value noise). Default is slightly busy at Scale high; tune Scale/Turbulence.
