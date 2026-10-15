# glitch_blocks (light source, wave 2)

Files: `library/sources/glitch_blocks/glitch_blocks.fs` + identical copy in `src/main/resources/default_sources/glitch_blocks/`. No `LSD_COST`, no `is3D`, CATEGORIES Generator / Glitch. Single pass, no texture reads, no loops beyond constant bounds (3 block layers, 3 noise octaves).

## Inputs (12, all normalised)
| Name (label) | Range | Default | Meaning |
|---|---|---|---|
| SurfaceType ("Base Pattern", STEP 1) | 0..3 | 1 | 0 gradient bars, 1 test-card colour bars, 2 diagonal stripes, 3 quantised noise field. Allowlisted name reused. |
| PaletteMode ("Palette", STEP 1) | 0..3 | 3 | 0 HSV rainbow, 1 cosine neon, 2 mono tint ramp, 3 test-card primaries (8 colours; HueOffset rotates the index). Allowlisted name reused. |
| Intensity | 0..1 | 0.4 | Overall corruption; block/dropout probability scales with I^2. |
| BlockSize | 0..1 | 0.4 | Block row height 0.02..0.32 of frame height (blocks 2.6x wider). |
| Rate | 0..1 | 0.3 | Glitch steps per second, 0.5..24 Hz. |
| Tear | 0..1 | 0.4 | Tearing band probability and max shift (up to 0.45 of width). |
| Split | 0..1 | 0.35 | RGB channel offset up to 0.05 of width, boosted inside displaced blocks. |
| Crush | 0..1 | 0 | Posterise 32 levels down to 2; 0 = off. |
| Quality | 0..1 | 0.7 | Block layers 1..3 and noise octaves 1..3. |
| HueOffset / Saturation / Brightness | 0..1 | 0 / 0.5 / 0.5 | Saturation x2 around luma, Brightness x2, then clamp. |

No new selector names.

## Caps
3 block layers, 3 noise octaves; 3 pattern evaluations per pixel (channel split) each mapped through the palette. About 30 integer hashes per pixel at worst.

## Derivation
- Time: `step = floor(mod(TIME,3600) * rate)`. Every glitch decision is a pure integer hash of (cell ids, step, salt); `mix32` is a two-round multiply/xorshift avalanche on `uint`, bit-identical on all GPUs. Cell ids are folded with `mod(.,4096)` so negative or displaced coordinates stay safe.
- Block layers: row = floor(y/bh), each row has a hashed x offset so blocks are ragged; cell = (row, col). If h < I^2*p the block jumps sideways (+-(0.12+0.35 I)) and sometimes vertically. Layer k uses `step/(k+1)` so finer layers hold longer. Dropouts (solid black / white / saturated hue) come from layer 1 (or 0).
- Tear: 6..15 bands per frame (hashed per step), each active with a hashed probability, sideways shift plus a small skew across the band. Scanline offsets: 270 virtual lines, individual lines nudged. Per-step "calm" frames (prob 0.18*(1-I)) and burst gain 0.5..1.5 give occasional quiet frames.
- Base pattern scroll is `fract(TIME/16)` of one bar period (seamless wrap); noise scroll wraps by 8 lattice units per period (small jump every 16 s, acceptable).
- ES 3.00: only `uint` ops, `const float`, constant-bound loops, no uniform initialisers.

## Measured (Mesa Intel UHD TGL GT1, headless harness, glFinish span)
1080p default: 4.4-4.6 ms (one outlier 10 ms on first frames of a run). Worst case (noise, Quality 1, Intensity 1, Split 1, TIME 1e5): 15-20 ms. 540p default 1.7-4.5 ms. All frames err=0, max 255, no NaN or black frames at defaults and extremes (all four patterns and palettes, Crush 0/1, Quality 0/1, Intensity 0/1, TIME 1e5). PNGs inspected.

## Web
Only ES 3.00-valid constructs; syncWeb not run, needs a manifest entry (integrator). No known web caveats.

## Known issues
- Noise pattern at Quality 1 is the most expensive path (3 octaves x 3 channel samples).
- Changing Rate rescales `TIME*Rate`, so the glitch sequence reshuffles when Rate is modulated.
- Intensity above ~0.7 hides the base pattern almost entirely (intended).
