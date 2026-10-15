# video_feedback (light sources, Wave 1)
Classic video feedback generator. No LSD_COST, no is3D. Pass 0: persistent `vfbState`, fixed 512x512 GL_RGBA8 (no FLOAT). Pass 1: display (bilinear upsample, square buffer "covers" the frame, crops the long axis).

## Inputs (10)
Zoom (-1..1, def 0.45; log-zoom rate 1.5/s), Rotation (-1..1, 0.35; 1.5 rad/s), HueShift (-1..1, 0.2; 3 rad/s about the grey axis), Decay (0..1, 0.15; fade rate 0.4..4.4 /s), SeedShape (0..3 STEP 1: ring, polygon 3/4/5 sides, spinning line, soft blob), SeedSize (0.4), Drift (0.3; up to 0.2 uv/s, direction wanders), Quality (0..1, 0.0; 1 to 4 blur taps), Saturation (0.5 = x1, display), Brightness (0.5 = x1, display).
Integration: `SeedShape` is a 0..3 enum and is NOT in the BundledIsfRangesTest `discrete` allowlist; add it (and check StockDiscreteInputsAuditTest).

## Derivation
Per pixel: inverse-warp sample p' = R(rot*dt) * p * exp(-zoom*dt) + drift*dt of the previous frame (black outside the buffer), average 1..4 taps on a 0.8 px ring, rotate colour about (1,1,1) (Rodrigues) for hue shift, multiply exp(-decay*dt), then screen-blend three Lissajous-orbiting seed shapes (hue cycling). Screen blend and clamps keep values in 0..1; NaN guard on output. Output gets +-0.45 LSB hash dither (stochastic rounding) so slow decay/hue drift does not stall on 8-bit quantisation.
- Frame-rate independent: all rates per second times clamp(TIMEDELTA, 0, 0.1). Seed injection itself is per frame (shape brightness slightly depends on fps, trails look the same).
- Empty detection: alpha of the pixel's own texel is 0 only when never written (cleared); then it starts black and the shapes repaint. No FRAMEINDEX dependence (FRAMEINDEX only seeds dither). Survives resize clearing.
- Time wrap: mod(TIME, 628.3185) with all frequencies multiples of 0.01 rad/s, so seamless.

## Web
web/isf.js: handles PERSISTENT (front/back swap, binds `back` for reads, final pass reads latest after swap), fixed numeric WIDTH/HEIGHT via parseDim ("512" ok), non-FLOAT -> RGBA8, TIMEDELTA uniform set, targets cleared on creation/resize. FRAMEINDEX: only used mod 997 for dither; verify it is provided (`index` in applyUniforms). By reading, this shader should run on the web; I did not run it in a browser. Caveats: web persistent textures are LINEAR + CLAMP_TO_EDGE (matches the desktop harness; shader does its own out-of-range masking); uses texture()/gl_FragColor/isf_FragNormCoord like other web ISFs. Only GLSL ES 3.00 constructs (int loop with constant bound, FRAMEINDEX % int on int).

## Measured (Intel UHD TGL GT1, Mesa, harness CPU-side glFinish span, all passes)
1080p: ~2.0-4.0 ms/frame at defaults (first frame 7.6 ms incl. warm-up) i.e. well above 60 fps; 960x540: ~1.1-2.5 ms (noisy, shared machine). The 512^2 state pass is resolution independent; display is one bilinear tap.
Verified: frames 1/30/120/300 at 540p and 1080p, shapes 0,1 (polygon),2, min and max of all inputs: no NaN, no stall; min (Brightness 0) is black by design; all-max extremes wash toward white (screen blend with Decay 0, huge blobs), expected.

## Known
- Default look is soft/glowy; Quality 0 is sharpest. Trails desaturate toward white where seed shapes overlap (screen blend).
- Buffer is square: on 16:9 about 56% of its height is visible; seed orbits are within |x|,|y| < 0.9.
