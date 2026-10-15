# fluid_ink (Wave 2, G)
Stable fluids (Stam 1999) on a 384x384 FLOAT torus: pass 0 velocity (RK2 semi-Lagrangian advection of the pressure-projected
velocity, emitter impulses, vorticity confinement, curl-noise turbulence, damping), passes 1-4 Jacobi pressure
(prs1..prs4, warm-started across frames, "Projection" scales how much grad p is removed), pass 5 dye advection +
injection, pass 6 display. 7 passes total.
- Inputs (12): Emitters (0..1 knob -> 1..4; kept normalized so BundledIsfRangesTest needs no allowlist entry), Force, Swirl, Turbulence, Viscosity, DyeFade, Projection (the quality-ish knob), SimSpeed (dt 0..2), ColorMode (STEP), HueOffset, Saturation, Brightness.
- Cost ~0.7 ms at 1080p on the A2000 laptop; LSD_COST 2 (see reaction_diffusion note).
- Square grid stretched to the output (emitters read as slight ellipses on 16:9); the domain wraps. Steps are per frame.
- Derivation: Stam's paper (advect, project, Jacobi pressure) and Fedkiw et al. 2001 for vorticity confinement; emitters, noise and render are our own.
- Verified: dye evolves for 1200 frames without blow-up; no NaN at extremes. Look is soft ink blobs with shear and curls; fine filaments are limited by bilinear advection.
- Open: no TIMEDELTA scaling; Emitters change snaps (emitters move on a fixed Lissajous orbit).
