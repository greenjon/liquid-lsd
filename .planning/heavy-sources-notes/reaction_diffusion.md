# reaction_diffusion (Wave 2, E-F)
Gray-Scott, 512x512 FLOAT torus, 4 chained sim passes (rdA..rdD) = 4 sub-steps/frame, plus a display pass.
- Inputs (12): Regime (F,k path: maze, worms, coral [default 0.5], chaos/holes... see header comment), Feed/Kill trims (-1..1), Diffusion, SimSpeed (dt 0.5..1.0, capped so Du*dt <= 1.2), Reseed (latch: crossing 0.5 re-seeds once; empty state re-seeds itself, so resize is safe), Relief, ColorMode (STEP 0..3), HueOffset, Saturation, Brightness, Zoom. No quality knob: the grid is fixed and the cost is ~0.5 ms at 1080p.
- LSD_COST is 2, not 3: measured 0.5 ms at 1080p on an RTX A2000 Laptop (4 x 512^2 float passes, 9 taps each). A heavy badge would be a false warning. Change to 3 if the owner wants the batch badged uniformly.
- Steps are per frame (not TIMEDELTA-scaled), so speed follows frame rate.
- Derivation: Gray-Scott equations (Pearson 1993) with the standard 9-point Laplacian (0.2/0.05 weights, Du=1, Dv=0.5); (F,k) stops are the commonly published pattern regimes. Seed noise is our own hash.
- Verified: patterns form from empty state (maze/coral/holes/spots seen at frame 900), no NaN at all-0 / all-1 inputs.
- Known: Reseed held at 1 with Zoom/Diffusion at max can leave a dim near-empty field.
