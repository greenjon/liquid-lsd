# pseudo_kleinian (heavy source notes)

Files: `library/sources/pseudo_kleinian/pseudo_kleinian.fs` (identical copy in `src/main/resources/default_sources/pseudo_kleinian/`). Desktop only, GLSL 3.30 core, `LSD_COST` 3, `is3D`.

## What it is
A fly-through of a Kleinian-style limit structure. Each iteration (1) folds space into a slab with a periodic mirror fold, (2) inverts the position in a sphere when it lies inside it, (3) adds a constant offset. After N iterations the surface is where the folded point meets a core sphere. Camera flies down an open channel of the periodic structure; Rotate X/Y/Z turn the view about the camera, not the world, so the camera stays in the channel.

## Parameters (all normalised; shader units after `#define` remap)
| Input | Range | Default | Working value |
|---|---|---|---|
| SlabSize | 0..1 | 0.5 | slab half-width b = 0.6 + 0.8 v (1.0 at default) |
| InversionRadius | 0..1 | 0.64 | sphere radius R = 0.6 + 0.9 v (R^2 = 1.4 at default) |
| InversionStrength | 0..1 | 0.6 | cap on the inversion scale k: 2 + 10 v (8 at default) |
| OffsetX/Y/Z | -1..1 | 0.2 / 0.4 / 0 | added after each inversion, x0.5 (0.1, 0.2, 0 at default) |
| CoreSize | 0..1 | 0.4 | core sphere radius 0.3 + 1.7 v (about 1.0) |
| FlightSpeed | -1..1 | 0.25 | units per second x1.5 along z |
| ColorMode | 0..3 STEP 1 | 0 | 0 distance orbit trap, 1 inversion depth, 2 normal spectrum, 3 wall trap on a gold ramp |
| HueOffset / Saturation / Brightness | 0..1 | 0 / 0.7 / 0.6 | Brightness x2 |
| Zoom | 0..1 | 0.5 | exponential, 0.5 = 1x |
| RotateX/Y/Z | +-PI | 0 | view rotation about the camera |
| Quality | 0..1 | 0.33 | iterations 6..12, march steps 48..120, AO taps 1..4 |

## Loop caps and cost
- Constant loop caps: `MAX_ITER` 12, `MAX_STEPS` 120, `MAX_AO` 4; each loop breaks at the quality-driven count.
- Default (Quality 0.33): 8 iterations, 72 steps, 2 AO taps. Max: 12, 120, 4. Min: 6, 48, 1.
- Per pixel worst case: steps x iterations map() evaluations: 72 x 8 = 576 (default), 1440 (max). Plus 6 map() for the normal and up to 4 for AO at the hit only. Each iteration is about 25 flops. Most rays leave the loop early on a hit.
- Measured (headless GL, this machine, NVIDIA RTX A2000 Laptop GPU via PRIME offload, 1920x1080, 3 s TIME, glFinish-timed, one frame, 5-draw average): Quality 0.33 11.2 ms, Quality 1 14.8 ms, Quality 0 8.1 ms. Extreme everything-max (Zoom 1, SlabSize 1, InversionRadius 1, Quality 1, TIME 30): 27.7 ms. Not a desktop A2000 and not a sustained benchmark (no thermal state, one scene, no compositing), so treat as indicative.
- No float buffers, no textures.

## Mathematical derivation
Only from the mathematics, no existing shader code consulted or reproduced:
- Reflection in a plane and inversion in a sphere are the generators of the discrete groups studied by Poincare and Klein (Kleinian groups; Apollonian packing as the standard example). The slab fold is the reflection group of the box planes. A reflection at x = b maps p to 2b - p; repeating it between x = -b and x = b gives the triangle wave `abs(mod(p - b, 4b) - 2b) - b`, which equals the identity inside the slab and is periodic (period 4b) outside, so the structure is infinite and the camera can fly through it indefinitely.
- Inversion in a sphere of radius R maps p to R^2 p / |p|^2, which is p * k with k = R^2 / |p|^2; its local length scale is that same k. The code applies p *= k with k = clamp(R^2 / |p|^2, 1, kmax): the exact inversion inside the sphere, the identity outside, and a cap kmax so the product of scales stays finite near the centre.
- Distance estimate: a map f that scales lengths locally by s sends a distance d in the image to roughly d/s in the source space (the Lipschitz argument used for all escape-time distance estimators). The product of the k factors, dr, is that scale; the offset is a translation and does not change it. The surface is the preimage of a core sphere of radius c, so `d = (|p_N| - c) / dr`. The folds are isometries, so dr tracks only the inversions. The estimate is approximate where the offset or the kmax cap breaks conformality; the march step is therefore 0.8 d.
- Orbit traps (Hart's idea of recording the closest approach of an orbit to a chosen set): minimum |p|^2 after each iteration, minimum distance to the slab walls, and a count of iterations in which the inversion fired.
- Ray hit threshold is half a pixel footprint (0.5 * t * fov / height), which also sets the normal epsilon (twice that).

## Known issues
- Visual check done only by headless renders at 640x360 and 1920x1080 on the dev machine (viewed as PNG); not seen in the running app, with real deck compositing, or under audio modulation.
- The camera channel (x = y = 0.9 b) is open for the default offset and nearby values. Large offsets, a small slab with a big core (CoreSize near 1) or InversionRadius near 0 can fill the channel or put the camera inside solid, giving a flat or dark frame (never NaN or black from Inf; the NaN guard falls back to the background colour).
- The DE is an estimate; at high InversionStrength with large offsets thin surfaces can show faint step banding or noisy shading at distance. Raising Quality helps.
- Rotation pivots at the camera, so Rotate Y 90 deg looks along the periodic structure sideways and may be walled in at some TIME values.
- InversionStrength is a cap on the scale of a single inversion, not a free strength: it limits how far the orbit is magnified near an inversion centre.

## Draft provenance-ledger row (docs/developer/clean_room_provenance.md)
| `pseudo_kleinian` source | Textbook mathematics: reflection groups and sphere inversion (Kleinian groups, Apollonian packings), the Lipschitz distance estimate, orbit traps (Hart) | Derived by the assistant from the mathematics and tuned by rendering (headless GL, 2026-10-14); no Shadertoy, Fragmentarium or other shader source was read, and the removed earlier `pseudo_kleinian` (`fc1ad3f`) was not used | `library/sources/pseudo_kleinian/pseudo_kleinian.fs`, `.planning/heavy-sources-notes/pseudo_kleinian.md` | The assistant may have seen similar shaders in training; the formulas above were derived independently and the form (periodic triangle fold, scale-capped inversion, core-sphere DE) was chosen by experiment |

## Draft SHADERS_LICENSE row (Bundled Visual Sources)
| `pseudo_kleinian` | Liquid LSD | Original work: periodic mirror fold + capped sphere inversion distance estimator derived from the mathematics of Kleinian groups; no third-party shader code | (same licence as the other bundled sources) |
Algorithm credits: reflection and inversion groups (Poincare, Klein), orbit traps (J. C. Hart).
