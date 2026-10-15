# escape_time (wave 3)

Inputs: FractalType (0 Julia, 1 Burning Ship Julia, 2 relaxed Newton z^3-1, 3 Mandelbrot), JuliaRadius, JuliaAngle, Power, MorphSpeed, Detail, Zoom, CenterX/Y, Rotation, ColorMode (0-3), ColorCycle, TrapGlow, HueOffset, Saturation, Brightness.
Caps: MAX_IT 400 (Detail 48..400), bailout 256, Newton tol 1e-5, 2x2 AA above Detail 0.75.
Derivation: standard iterations and smooth escape count from the definitions; no third-party shader code.
Measured (A2000 Laptop, 1080p, default): Julia 0.39 ms, Newton 0.94, Mandelbrot 1.45, Julia Detail 1 1.18.
Gotchas: Newton relaxation factor must be vec2(1,0)+k*c, not vec2(1.0) (that is (1,1)); relaxation >~0.25*c makes basins chaotic and nothing converges. Stock set grows to include this id. Not 3D: uses the deck View transform on top of its own Zoom.
Known: no deep zoom (fp32). Interior-heavy deep views run the full cap per pixel; untested at the extremes.
