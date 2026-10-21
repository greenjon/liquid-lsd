# kifs (heavy source, Wave 1 agent B)

Files: `library/sources/kifs/kifs.fs` and identical `src/main/resources/default_sources/kifs/kifs.fs`.
Header: `LSD_COST` 3, `is3D` true, CREDIT "Liquid LSD", CATEGORIES Generator / 3D / Fractal. Desktop only (no web entry).

## Parameters (18 inputs, all normalised; names in code)
| Input | Range | Default | Meaning |
|---|---|---|---|
| SurfaceType ("Fractal Type", STEP 1) | 0..3 | 0 | 0 Menger-like (box + sorted fold, scale 3), 1 Sierpinski tetrahedron (plane folds, scale 2), 2 octahedral (sorted fold toward vertex (k,0,0), scale 2), 3 mixed (Menger step on even iterations, Sierpinski step on odd, box base, scale 2). Reuses the allowlisted name `SurfaceType`. |
| Scale | 0..1 | 0.5 | Multiplier on the nominal scale, exponential, 0.5 = 1x, range 0.67x..1.5x. Clamped to >= 1.2. |
| Offset | 0..1 | 0.5 | Multiplier on the fold offset k (the IFS fixed vertex distance), 0.5 = 1x, 0.67x..1.5x. |
| TwistX / TwistY / TwistZ | -1..1 | 0.1 / 0.15 / 0 | Rotation applied before every fold iteration except the first (object orientation fixed), +-0.6 rad per axis. |
| MorphSpeed | -1..1 | 0.2 | Adds a time-driven rotation (up to 0.3 rad/s, axis weights 1, 0.7, 0.43) to the per-iteration rotation. |
| FlightSpeed | -1..1 | 0.15 | Dolly toward the fractal's fixed vertex, `0.5 * FlightSpeed` scale-periods per second (one period = distance divided by the scale). Signed. |
| Glow | 0..1 | 0.4 | Step-count halo around grazing rays. |
| ColorMode (STEP 1) | 0..4 | 0 | 0 point orbit trap (min \|p\|^2), 1 axis-plane trap, 2 iteration-of-min trap, 3 normal spectrum, 4 gold ramp (trap 0). |
| HueOffset, Saturation, Brightness | 0..1 | 0.58, 0.75, 0.5 | Brightness remapped x2. |
| Quality | 0..1 | 0.5 | Fold iterations 5..12, march steps 48..112, AO taps 0 (<0.2), 2 (<0.6), 4. Default: 9 folds, 80 steps, 2 taps. |
| Zoom | 0..1 | 0.5 | Exponential, 0.5 = 1x (fov = 1.1 / Zoom_, Zoom_ clamped >= 0.05). |
| RotateX/Y/Z | +-PI | 0 | Camera rig rotation about the origin. |

## Loop caps and cost
- Hard caps: `MAX_STEPS = 112`, `MAX_FOLDS = 12`, AO at most 4 taps, normal 4 taps. No shadows.
- Per hit-pixel worst case at Quality 1: 112 march steps x 12 folds = 1344 fold iterations, plus (4 normal + 4 AO + 1 trap) = 9 more map() calls (108 folds). Default: 80 x 9 = 720, plus 7 calls (63 folds). Quality 0: 48 x 5 = 240.
- Rays missing the bounding sphere (radius 1.75*max(k,1)+0.1) skip the march; most background pixels cost almost nothing.
- Each fold iteration: one 3x3 matrix multiply, a few min/max/compares, one scale/offset. Roughly 30 flops, so default is about 22k flops per object pixel.
- Measured here (not the A2000): headless GL 3.3 context on this machine reported renderer "Mesa Intel UHD Graphics (TGL GT1)" (the laptop's integrated GPU). At 1920x1080, 5 back-to-back draws averaged: default 19.6 ms (Menger, object fills about 40% of the frame), Quality 1 25.5 ms, Quality 0 11.1 ms, other types 10.7 to 16.7 ms. The A2000 should be several times faster, so 60 fps default and 30 fps at max look comfortable. Worst case is Zoom=1 with Quality=1 (every pixel hits): 35 ms at 1280x720 on the Intel GPU. Unverified on the A2000.

## Mathematics (derived, no external shader code)
- Iterated function system: a union of similarity maps, each x -> (x + c)/s composed with a symmetry. The inverse view used for distance estimation: fold the point into a fundamental domain of the symmetry group, then apply p -> s p - k (s - 1) e, so the attractor is its own image. The distance bound is d(base shape at final p) / s^n (similarity scaling), stepped with factor 0.8.
- Menger sponge: cube [-k,k]^3, scale 3, retained cells are those with at most one coordinate in the middle slab, i.e. cells (1,1,1) and permutations of (1,1,0). Folding |p| and sorting to x >= y >= z reduces all permutations to (1,1,*); then x,y -> 3x - 2k, and for z the two cases (corner 1, edge 0) are one translate: if z' < -k then z' += 2k. General form in the code: zlim = -c.z/2, add c.z.
- Sierpinski tetrahedron: vertices (k,k,k), (k,-k,-k), (-k,k,-k), (-k,-k,k). Its symmetry planes x+y=0, x+z=0, y+z=0 are reflected (p.xy -> -p.yx etc.), then scale 2 toward vertex (k,k,k). Base shape: max of the four face-plane distances (normals (-1,-1,-1), (-1,1,1), (1,-1,1), (1,1,-1); each face at distance k/sqrt(3)).
- Octahedral construction: octahedron |x|+|y|+|z| <= k, fold to x >= y >= z >= 0 (the order-48 octahedral group's fundamental domain), scale 2 toward vertex (k,0,0); sorted abs-fold covers all six vertices. Base distance is (|x|+|y|+|z| - k)/sqrt(3).
- Per-iteration rotation: composing a fixed rotation R with the fold keeps the map a similarity, so the s^n bound still holds; the attractor stays inside radius sqrt(3)k.
- Orbit traps: min |p|^2, min axis-plane distance, and index of the min over the fold iterations (standard orbit-trap colouring idea).
- Normals: four-sample tetrahedral gradient. AO: occlusion proxy sum of (h - d(p + n h))/h along the normal.
- Rotations, hsv2rgb: standard textbook matrices and the usual HSV hexagon formula (same as gyroid_hyperspace).
- Self-similar flight: scaling camera position and scene about the fixed vertex by s gives an identical image, so the camera distance 2.8 * s^(-fract(u)) loops. The view direction is fixed so this holds exactly for rotation 0.

## Known issues
- With non-zero Twist or Morph the loop is not exactly self-similar (the rotated fixed point differs), so the flight wrap shows a small pop. Defaults use a small twist so the pop is mild; at zero twist it is seamless apart from the finite fold-count truncation.
- Deep flight: detail beyond `Quality` fold iterations is cut, so approaching the vertex shows a soft smooth end of detail rather than new structure; at the wrap the visible detail depth steps by one level.
- Scale/Offset away from 1x break the exact attractor for the tetra/octa shapes (base shape no longer tiles perfectly); it is meant as a look control. The DE becomes less conservative; very low Scale can leave faint holes or small step artefacts.
- Distance bound with sort folds is the usual approximate DE, not exact; the 0.8 step factor and a grazing-ray hit rule (d < 4 pixels at step end) trade precision for speed. Rare dark speckle on silhouettes is possible.
- Mixed type uses a box base shape for all iterations; it is not an exact union IFS.
- Fixed light directions; no shadows.
- Unverified: real A2000 frame rate; behaviour on non-NVIDIA drivers other than the Mesa Intel path used here. Visual check was done by headless render to PPM (throwaway test, deleted) at 480x270..1920x1080 for all 4 types, all 5 colour modes, extreme Scale/Offset/Twist/Zoom/Quality, no NaN or black frames (Brightness 0 is black by definition).

## Draft provenance ledger row (docs/developer/clean_room_provenance.md)
| kifs generator (`library/sources/kifs/kifs.fs`) | Defining mathematics of iterated function systems: Menger sponge, Sierpinski tetrahedron and octahedron constructions, reflection-group fundamental-domain folding, similarity-scaled distance estimation, orbit traps | Written from the mathematics (folds, scale and offset about the IFS fixed vertex, base-shape SDF divided by s^n); no Shadertoy/Fragmentarium/Knighty shader or earlier removed `kifs` code read or recalled | `library/sources/kifs/kifs.fs`, `.planning/heavy-sources-notes/kifs.md` | done <date>; the author assistant may have seen similar shaders in training, so the derivation is recorded in the notes |

## Draft SHADERS_LICENSE row (Bundled Visual Sources)
| **KIFS** | `default_sources/kifs/kifs.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Raymarched kaleidoscopic IFS fractal (Menger, Sierpinski, octahedral, mixed) with per-iteration rotation, orbit-trap colouring and self-similar flight. Algorithm credit: textbook IFS mathematics, no third-party shader code. |
(Match the column layout of the existing Bundled Visual Sources table when pasting.)
