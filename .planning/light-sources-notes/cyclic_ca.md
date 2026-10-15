# cyclic_ca (light sources, Wave 2)
Cyclic cellular automaton (Belousov-Zhabotinsky look). No LSD_COST, no is3D. Passes: four chained persistent 256x256 RGBA8 sim passes (simA..simD, sub-steps) then a display pass. Toroidal grid.

## Inputs (11)
States (0..1 -> 3..24 cyclic states, default 0.1 = 5), Threshold (0..1, neighbours needed = round(T*N*0.6) clamped 1..N; default 0.4 = Moore 2 / vN 1 / extended 6), Neighborhood (STEP 0..2: Moore 8, von Neumann 4, extended radius-2 Moore 24), Speed (0..1 -> 2..120 generations/s), Density (seed fraction 0.02..1 of cells given a random state, others 0), Reseed (bool; rising edge reseeds), PaletteMode (STEP 0..3, periodic cosine palettes), HueOffset, Saturation (0.5 = x1), Brightness (0.5 = x1), Quality (<0.34 crisp cells, to 0.67 bilinear in colour space, above smootherstep).

## New selector names
`Neighborhood` (STEP 0..2) is new: add to BundledIsfRangesTest `discrete` and check StockDiscreteInputsAuditTest. `PaletteMode` reused. `Reseed` is a `bool` input.

## Derivation
Cell state s in Z_N advances to (s+1) mod N when at least T neighbours hold (s+1) mod N, else unchanged (synchronous). State in R byte, B = last Reseed level (rising-edge detect), A = 1 once written. A = 0 (never written / cleared by resize) -> hash seed, salted by TIME; never uses FRAMEINDEX. Cells with s >= N after reducing States are folded by mod N.
Frame-rate independence: stateless; generations due this frame = floor(TIME*rate) - floor((TIME-dt)*rate), dt = clamp(TIMEDELTA,0,0.1); pass k steps if k < due, else copies. Cap 4 generations/frame (240/s at 60 fps; slower at low fps).
Display: square grid covers the frame (crops long axis, ~7.5 px cells at 1080p 16:9); state -> periodic cosine palette; colours (not states) interpolated, so no wrap artefacts.

## Caps
Extended hood: 24 fetches/cell/step x 4 passes x 65k cells, negligible. No loops over variable bounds.

## Web
Not run in a browser. Uses texelFetch, int %, bool uniform, persistent passes with fixed numeric 256 sizes: all GLSL ES 3.00; web/isf.js handles PERSISTENT. Verify the chained-persistent semantic (a pass reading another persistent target sees this frame's output) matches web/isf.js, and that the bool uniform is set.

## Measured
Harness on Intel UHD TGL GT1: 960x540 about 1.5 ms/frame; 1080p not separately measured (sim cost is resolution independent, display is 4 cell fetches), expect ~2-3 ms.

## Known / tuning
- Classic spirals need low state count relative to threshold. Random start with many states and high threshold freezes (checked: 14 states, Moore T3 is static from noise), hence default 5 states, T2.
- Verified (480p, frame 300): default-ish 5 states/T2 evolves into nested diagonal-edged spiral domains; 3 states T1 and 14 states T3 stay noisy/static. Not yet checked: extreme-input sweep, vN and extended hoods, Reseed edge, resize, palettes, Quality levels visually, 1080p timing.
