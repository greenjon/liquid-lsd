# hyperbolic_tiling (light source, no LSD_COST)

Files: `library/sources/hyperbolic_tiling/hyperbolic_tiling.fs` (identical copy in `src/main/resources/default_sources/`).
Single pass, no texture reads, no `is3D`, no Zoom/Rotate inputs. 14 inputs.

## Inputs
| Input | Range | Default | Remap / meaning |
|---|---|---|---|
| P | 3..10 STEP 1 | 5 | tile sides |
| Q | 3..10 STEP 1 | 4 | tiles per vertex; raised to smallest valid q if (p-2)(q-2) <= 4 (`qmin = 4/(p-2)+3`, int division) |
| Quality | 0..1 | 0.8 | fold-iteration cap 6..16 |
| Speed | -1..1 | 0.3 | Mobius drift rate (x0.5 rad/s) |
| Drift | 0..1 | 0.5 | drift radius 0..0.75 of the disk |
| Spin | -1..1 | 0.25 | view rotation rate (x0.5 rad/s) |
| EdgeWidth | 0..1 | 0.35 | 0.004..0.094 hyperbolic units (sinh d) |
| Glow | 0..1 | 0.5 | edge glow and rim glow (x2) |
| Subdivision | 0..1 | 0.25 | opacity of the fundamental-triangle mirror lines |
| ColorMode | 0..3 STEP 1 | 0 | 0 Escher (depth hue, triangle parity, dome shading), 1 depth bands, 2 two-tone, 3 mono ink |
| Outside | 0..2 STEP 1 | 0 | 0 fade to dark, 1 mirror-continue (z -> z/|z|^2), 2 flat fill |
| HueOffset / Saturation / Brightness | 0..1 | 0.58 / 0.75 / 0.5 | Brightness x2, soft shoulder above 0.8 |

New selector names needing allowlisting in `BundledIsfRangesTest` (integration pass): `P`, `Q`, `Outside` (ColorMode already listed).

## Caps
Fold loop: constant bound 16, `if (i >= iters) break`, early exit when a full pass moves nothing. Points unsettled at the cap (near the rim) fade to a mean colour. Per fold iteration about 40 FLOPs, so at most ~700 FLOPs/pixel plus colouring.

## Derivation
Coxeter triangle (2,p,q), angles pi/2, pi/p (at origin A), pi/q (B on x axis). Mirrors: m1 = x axis, m2 = diameter at angle a = pi/p (normal (sin a, -cos a)), m3 = circle orthogonal to the unit circle with centre c = d(cos a, sin a) (centre lies on m2 because m3 is perpendicular to m2). Angle pi/q against m1: cos b = c_y / r with r^2 = d^2 - 1, giving d^2 = cos^2 b / (cos^2 b - sin^2 a); positive iff cos b > sin a iff 1/p + 1/q < 1/2 iff (p-2)(q-2) > 4. Fold: y = |y|; reflect across m2 if dot(z,n2) < 0; invert in m3 if inside it (z = c + (z-c) r^2/|z-c|^2); repeat until nothing moves. Tile edge = m3; tile centre = A; the fold parity gives the triangle checkerboard, the inversion count is the tile depth used for hue.
Distances in the folded frame: sinh d to a diameter = 2|y|/(1-|z|^2); to an orthogonal circle = | |z-c|^2 - r^2 | / (r(1-|z|^2)). Pixel size in hyperbolic units = pixel * 2/(1-|zs|^2) (isometry invariant), used as the anti-alias width; tiles smaller than a pixel melt into a mean colour. View: rotation, then Mobius z = (w+t)/(1+conj(t) w) along a Lissajous path. Outside the disk mode 1 first inverts z in the unit circle (also an isometry-compatible mirror).

## Web
Compatible with `scripts/sync_web.py` transpile: no `#version`, no uniform initialisers, no GLSL 4 functions, constant loop bound, int division/`clamp(int)` only. `const float` globals are valid ES 3.00. Time wrapped with `mod(TIME, 628.3185)` (100 x 2pi); drift/spin rates are multiples of 0.01 rad/s at default Speed/Spin so the wrap is seamless there, at other values there is a small phase jump every 10.5 min. Needs a manifest entry (integration pass); not run through syncWeb or a browser.

## Verified (Intel UHD TGL GT1 iGPU, Mesa, glFinish span)
1080p default: ~3.2 to 3.4 ms/frame steady (first frame 17 ms incl. warmup), i.e. ~300 fps. 540p: 8 to 14 ms on frame 1 only (warmup). Rendered (p,q) = (5,4), (4,5), (3,7), (7,3), (10,10), (3,3 -> clamped to 3,7), min Quality with Outside=1, max edge/glow/subdivision with ColorMode 2 + Outside 2, ColorMode 3 and 1 at extremes. No NaN or black frames; glGetError 0.

## Known issues
- Brightness at 1.0 (x2) saturates toward white despite the shoulder (same convention as gyroid_hyperspace).
- Quality 0 (6 folds) leaves most of the disk "unsettled" and flat-coloured; intended as a floor.
- Cell colouring uses inversion depth, not a unique tile id (neighbouring tiles at equal depth share hue; parity and dome shading differ).
