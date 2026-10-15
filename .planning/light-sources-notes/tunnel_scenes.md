# tunnel_scenes (light source)

Files: `library/sources/tunnel_scenes/tunnel_scenes.fs` + identical `src/main/resources/default_sources/tunnel_scenes/tunnel_scenes.fs`. No `LSD_COST`, no `is3D`, CATEGORIES Generator / Geometric. Single pass, no texture reads, no raymarching.

## Inputs (12, all normalised)
| Name (label) | Range | Default | Meaning |
|---|---|---|---|
| SurfaceType ("Scene", STEP 1) | 0..2 | 0 | 0 grid floor+ceiling+sun, 1 Truchet cylinder, 2 polygon wormhole. Allowlisted name reused. |
| Symmetries ("Sides", STEP 1) | 3..12 | 6 | Polygon sides, scene 2 only. Allowlisted name reused. |
| Speed | -1..1 | 0.35 | Signed forward speed, x4 units/s. |
| Twist | -1..1 | 0.15 | Spiral twist, x1.2. |
| Glow | 0..1 | 0.5 | Line halo plus horizon/end-of-tunnel glow. |
| Fog | 0..1 | 0.4 | Depth fade density exp(-t*(0.02+0.28*Fog)). |
| LineWidth | 0..1 | 0.35 | Half-width 0.02..0.14 of a cell. |
| Sway | 0..1 | 0.4 | Camera position wander (max 0.12) plus roll. |
| Quality | 0..1 | 0.5 | Layers 1..3 (nested surfaces) and AA filter 0.6..1.4x pixel footprint. |
| HueOffset / Saturation / Brightness | 0..1 | 0 / 0.85 / 0.5 | Brightness x2, then 1-exp tonemap. |

Hash selectors reuse `SurfaceType` and `Symmetries` so `BundledIsfRangesTest` needs no allowlist edit; if the integrator prefers `Scene`/`Sides` names, add them to its `discrete` set.

## Caps
Layers max 3; polygon loop max 12 faces x 3 layers; no other loops. Per pixel: 1-3 analytic intersections, a few `fwidth`, one integer hash.

## Derivation (own mathematics)
- Plane y = +-h: t = (h -/+ o.y)/|rd.y|, with |rd.y| clamped to 1e-3 and t clamped to 300 (horizon guard). Hit point gives grid coordinates (x, z+travel); lines by distance to nearest integer. Layers add planes at h = 1, 1.8, 2.6 with spacings 1, 2, 4. Sun: disc at the vanishing point in q = rd.xy/rd.z, lower half cut by stripes widening downward.
- Cylinder radius r, camera inside: a = |rd.xy|^2, b = o.xy.rd.xy, c = |o.xy|^2 - r^2 < 0, t = (-b + sqrt(b^2 - a c))/a (a clamped >= 1e-5). Texture coords (atan/2pi*N, z/len), N = 12/8/6 tiles around so wrapping is seamless. Truchet: per-cell integer hash flips the tile, line = min distance to two quarter circles of radius 0.5 centred on opposite cell corners (continuous across cell edges).
- Polygon prism: the nearest positive ray-plane hit over N face planes n_i.p = r. Face-local coordinate s = p.tangent gives corner-edge lines; ring lines every 0.5 along z; chevrons from ring phase + |s|. Nested layers rotate by half a face.
- Twist: rotating the image about the vanishing point by an angle proportional to 1/(rho+0.25) is the screen-space image of an azimuth shift proportional to depth, so one cheap warp twists all three scenes (exact for the cylinder).
- AA: coverage = 1 - smoothstep(w-fw, w+fw, d), relaxing to the mean 2w when the pixel footprint fw exceeds ~0.35 cells (kills moire toward the horizon/vanishing point).
- Precision: sway phases use `fract(TIME*f)*2pi`; travel wrapped to whole pattern periods (grid 64 units, wormhole 32 units = 64 rings, Truchet 64 cells via mod(travel/len, 64)); hashed ids are mod-wrapped to the same periods, so wrap is seamless.

## Measured (Mesa Intel UHD TGL GT1, headless harness, glFinish span)
1080p: scene 0 3.1-3.3 ms (Q 0.5), 3.3 ms (Q 1); scene 1 2.9-4.5 ms, 4.4 ms (Q 1); scene 2 5.0-5.2 ms, 7.1 ms (Q 1). 540p worst case (all extremes, Q 1) 1.4 / 4.4 / 6.1 ms (noisy). All comfortably above 60 fps. No NaN or black frames at default and at extreme inputs (Q 0/1, Twist +-1, Fog 0/1, LineWidth 0/1, Sides 3/12, TIME 1e5); Brightness 0.1 is dim by definition. PNGs inspected for all three scenes.

## Web
Uses only constructs valid in ES 3.00 (uint hash, fwidth, constant-bound loops, no uniform initialisers, `const` globals). `scripts/sync_web.py` transpile only swaps the `#version` line, adds `precision highp float;` and drops uniform initialisers, so no rewrite issues are expected; syncWeb not run. Needs a `web/sync_manifest.json` entry (integrator). `fwidth` needs no extension in ES 3.00.

## Known issues
- Changing Speed rescales `TIME*Speed`, so the pattern phase jumps when Speed is modulated (same as other TIME-based sources).
- Twist near the vanishing point spins fast (angle ~ 1/rho); fogged out at default.
- Scene 0 default shows the sun warped by Twist; set Twist 0 for a flat sun.
- Truchet tiles have constant hue per cell, so colour changes abruptly where tiles meet (intended look).
