# lenia (Wave 2, stretch)
Single-channel Lenia (Chan 2019) on a 256x256 FLOAT torus, kernel K(r)=exp(4-1/(r(1-r))), Gaussian growth G(U)=2exp(-(U-mu)^2/2sigma^2)-1,
4 chained sim passes (lnA..lnD) = 4 steps/frame. 27x27 tap window max (R<=13), ~2 ms at any resolution (grid is fixed). LSD_COST 2.
- Inputs (12): GrowthCenter (default 0.3 -> mu 0.122), GrowthWidth, Radius (R 5..13), SimSpeed (dt 0.03..0.10), SeedDensity, Reseed (latch), Relief, ColorMode (STEP), HueOffset, Saturation, Brightness, Zoom.
- HONEST STATUS: the default settings grow bubbles/ring cells and, at higher centre/width, stripes and worm textures. I did NOT find the published gliding "Orbium" regime: a 12-point sweep showed the field either dies (mu >= 0.45 with narrow sigma) or settles into a static Turing-like pattern. Treat as a working simulation with a pretty texture, not as gliding creatures. Finding gliders likely needs a seeded Orbium pattern rather than random patches.
- Derivation: the formulas above are from the Lenia paper; seeding and rendering are our own.
