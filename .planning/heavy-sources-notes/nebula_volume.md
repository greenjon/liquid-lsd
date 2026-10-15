# nebula_volume (Wave 2, E)
Ray-marched emission/absorption through domain-warped ridged value-noise fbm, with a drifting inner light, 1-tap..4-tap
light march, interleaved-gradient-noise jitter, leap over empty space, starfield. is3D, own Zoom/RotateX/Y/Z, FlightSpeed, Drift.
- Inputs (18): Density, Coverage, Scale, Turbulence, Detail (STEP 1..5 octaves, default 2), Quality (20..72 view steps, default 0.15 = ~28), Glow, Lighting (0..4 light taps, default 0.4 = 2), FlightSpeed, Drift, ColorMode (STEP), HueOffset, Saturation, Brightness, Zoom, RotateX/Y/Z.
- Loop caps: MAX_STEPS 72, MAX_LIGHT 4, fbm 5 octaves max; one warp noise per sample.
- Measured on the RTX A2000 Laptop, 1080p, one deck: default 17 ms (~59 fps); Quality 1 / Detail 4 / Lighting 1: 68 ms (~15 fps). The brief's max-quality target (30 fps) is NOT met at those settings; Detail 5 + Quality 1 is slower still. Recommend leaving as is (it is the user's quality knob) or lowering MAX_STEPS. The desktop A2000 is faster than the laptop part.
- Derivation: standard emission-absorption volume rendering; value noise with a hash from our own code; ridge = 1-|2n-1|; no external shader consulted.
- Known: the jitter leaves fine dither grain on still frames; Detail 1 looks flat; gas is dense around the path (envelope exp(-r^2/50)).
