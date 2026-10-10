# Fractal flame variations still to implement (indices 49-98, plus a check on 46)

Scope: the importer and shader already cover indices 0-48. This file specifies the 50 variations after that, so
they can be implemented from the mathematics alone. Every test value below was computed by an independent
evaluation of the formulas, not copied from another document. Tolerance for the pairs: 1e-4 absolute.

**Skipped for v1 (do not implement; the importer reports them as unsupported):** 50 super_shape, 65 lazysusan,
98 mobius (each needs more parameters than a variation slot holds) and 67 pre_blur (it alters the point rather
than adding to the output). Their entries stay below for a later version.

## Conventions (apply to every entry)

- **Input point**: (x, y) is the point after the transform's pre-affine map (and after pre_blur, if that transform has one).
- **Polar quantities**:
  - r = sqrt(x² + y²).
  - θ = atan2(x, y), the angle measured from the **y axis**, so sin θ = x/r and cos θ = y/r.
  - φ = atan2(y, x), the ordinary polar angle, so cos φ = x/r and sin φ = y/r.
  - Both are written out explicitly in each entry.
- **Weight v**: each variation produces an output (x′, y′) already multiplied by its weight v. The outputs of all
  variations in a transform are summed and then the post-affine is applied. A variation with weight 0 is skipped.
  Negative weights are allowed and simply scale. Equations below include v wherever the weight is used. Entries
  marked "non-standard weight" use v in some other way as well; follow the equations exactly.
- **ε = 1e-10**. It appears only where an entry says so.
- **Origin**: where an entry uses x/r or y/r, the value at exactly (0,0) is 0/0 = not-a-number. This is the true
  behaviour and flows into the bad-value rule below. An implementation may instead return (0,0) at the exact origin,
  but record that choice; it is a deliberate deviation.
- **Bad-value rule** (global, in the iteration loop, not per variation): if a transform's final (x′, y′) is
  not-a-number or has any component with magnitude above 1e10, the point is thrown away and replaced by a point
  with both coordinates uniform in [−1, 1]. Division results of ±infinity therefore also end up replaced.
- **Random draws**: U denotes an independent uniform number in the **closed** interval [0, 1]. "G" (pseudo-Gaussian)
  means the sum of four independent U minus 2; its range is [−2, 2], mean 0, variance 1/3. Draws in one entry are independent.
- **Remainder**: rem(a, b) = a − b·T(a/b) where T drops the fractional part toward zero. Its sign follows a. If b = 0 the result is not-a-number.
- **Floor** rounds toward −infinity. **Round-half-even**: halves go to the nearest even integer (0.5 → 0, 1.5 → 2, 2.5 → 2, −0.5 → 0).
- **Parameter names** below are the attribute names in the `.flame` file. The variation's own weight is the attribute with the variation's name (e.g. `disc2="0.5"`).
  A parameter missing from the file takes the stated default.
- **Ranges**: the original code states no ranges. Where a "generator range" is given it is what the original
  random-flame generator draws from. It is a sensible working range, not a limit; imported files may be outside it.

---

## 49. disc2
- **Parameters**: `disc2_rot` default 0; `disc2_twist` default 0. Generator range: both 0 to 0.5.
- **Equations**: let τ = twist, s = sin τ, c = cos τ − 1. If τ > 2π, multiply both s and c by (1 + τ − 2π).
  If τ < −2π, multiply both by (1 + τ + 2π). Then with t = π·rot·(x + y) and R = v·θ/π:
  x′ = R (sin t + c), y′ = R (cos t + s).
- **Random**: none. **Inputs**: x, y, θ.
- **Edge cases**: at the origin θ = 0 so the result is (0,0) (no division). The scaling by (1 + τ ∓ 2π) makes the output discontinuous nowhere but non-periodic in τ beyond ±2π.
- **Tests** (v = 1): defaults (1,0) → (0, 0.5); defaults (0,1) → (0,0); rot=1, twist=0, (0.5,0.5) → (0, −0.25); rot=0, twist=π, (1,0) → (−1, 0.5); rot=0, twist=3π, (1,0) → (−4.14159, 0.5).

## 50. super_shape
- **Parameters**: `super_shape_rnd` 0; `super_shape_m` 0; `super_shape_n1` 1; `super_shape_n2` 1; `super_shape_n3` 1; `super_shape_holes` 0. Generator range: rnd 0..1, n1 0..40, n2 0..20, n3 = n2, holes 0.
- **Equations**: ψ = (m/4)·φ + π/4; t₁ = |cos ψ|^n2; t₂ = |sin ψ|^n3; ρ = rnd·U + (1 − rnd)·r − holes;
  k = v · ρ · (t₁ + t₂)^(−1/n1) / r; (x′, y′) = k·(x, y).
- **Random**: one U, only used if rnd ≠ 0 (uniform). **Inputs**: x, y, r, φ.
- **Edge cases**: r = 0 divides by zero. n1 = 0 divides by zero in the exponent. t₁ + t₂ = 0 with a negative exponent gives infinity. Negative ρ flips the point through the origin.
- **Tests** (rnd=0, v=1): defaults (1,0) → (0.70711, 0); defaults (0,1) → (0, 0.70711); m=4, n1=n2=n3=2, (1,0) → (1,0); holes=0.25, (1,0) → (0.53033, 0).

## 51. flower
- **Parameters**: `flower_petals` 0; `flower_holes` 0. Generator range: petals 0..4, holes 0..1.
- **Equations**: k = v · (U − holes) · cos(petals·φ) / r; (x′, y′) = k·(x, y).
- **Random**: one U, uniform. **Inputs**: x, y, r, φ.
- **Edge cases**: r = 0 divides by zero. Output radius is |U − holes|·|cos(petals·φ)|, independent of r.
- **Tests**: random; check statistically that |(x′,y′)| ≤ max(|1 − holes|, |holes|) and that with petals = 0, holes = 0 the output direction equals the input direction.

## 52. conic
- **Parameters**: `conic_eccentricity` 1; `conic_holes` 0. Generator range: both 0..1.
- **Equations**: k = v · (U − holes) · e / (1 + e·x/r) / r with e = eccentricity; (x′, y′) = k·(x, y).
- **Random**: one U, uniform. **Inputs**: x, y, r (x/r is cos φ).
- **Edge cases**: r = 0 divides by zero. 1 + e·x/r = 0 (only possible when |e| ≥ 1, on the ray where x/r = −1/e) divides by zero.
- **Tests**: random; with holes = 0, the output radius is U·e/(1 + e cos φ), a conic in polar form, so U = 1 and e = 1 at φ = 0 gives radius 0.5.

## 53. parabola
- **Parameters**: `parabola_height` 0; `parabola_width` 0. Generator range: both 0.5..1.5.
- **Equations**: x′ = v · height · sin²(r) · U₁; y′ = v · width · cos(r) · U₂.
- **Random**: two independent U, uniform. **Inputs**: r.
- **Edge cases**: with both parameters at the default 0 the output is (0,0).
- **Tests**: random; with height=1, width=1, v=1 and r=π/2, x′ ∈ [0,1] and y′ = 0 exactly (cos(π/2) is ≈ 6e-17).

## 54. bent2
- **Parameters**: `bent2_x` 1; `bent2_y` 1. Generator range: −1.5..1.5.
- **Equations**: x′ = v·(bent2_x·x if x < 0, else x); y′ = v·(bent2_y·y if y < 0, else y).
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: only negative coordinates are scaled; zero counts as non-negative. A negative parameter flips the sign of negative coordinates.
- **Tests** (bent2_x=2, bent2_y=0.5): (1,2) → (1,2); (−1,−2) → (−2,−1); (−3,4) → (−6,4).

## 55. bipolar
- **Parameters**: `bipolar_shift` 0. Generator range: −1..1.
- **Equations**: T = x² + y² + 1. ψ = ½·atan2(2y, x² + y² − 1) − (π/2)·shift. If ψ > π/2: ψ ← −π/2 + rem(ψ + π/2, π). If ψ < −π/2: ψ ← π/2 − rem(π/2 − ψ, π).
  x′ = v · (1/(2π)) · ln((T + 2x)/(T − 2x)); y′ = v · (2/π) · ψ.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: T − 2x = (x−1)² + y², zero only at (1,0), where the logarithm argument is +infinity (bad value). T + 2x is zero only at (−1,0), where the argument is 0 and the logarithm −infinity. On the x axis with |x| < 1 the angle is atan2(±0, negative): +π for y = +0, −π for y = −0, so ψ = ±π/2 and y′ = ±1 depending on the **sign of zero** (an implementation using a different zero convention differs there; use +0).
- **Tests** (v=1): defaults (0,0) → (0, 1); defaults (0,1) → (0, 0.5); defaults (0.5,0) → (0.34970, 1); defaults (1,1) → (0.25615, 0.35242); shift=0.5, (0,1) → (0,0).

## 56. boarders
- **Parameters**: none.
- **Equations**: x_r = round-half-even(x), y_r = round-half-even(y); Δx = x − x_r, Δy = y − y_r. Draw U.
  - If U ≥ 0.75: (x′, y′) = v·(Δx/2 + x_r, Δy/2 + y_r).
  - Otherwise, if |Δx| ≥ |Δy|, let s = +1 if Δx ≥ 0 else −1: x′ = v·(Δx/2 + x_r + s/4), y′ = v·(Δy/2 + y_r + (s/4)·Δy/Δx).
  - Otherwise let s = +1 if Δy ≥ 0 else −1: y′ = v·(Δy/2 + y_r + s/4), x′ = v·(Δx/2 + x_r + (s/4)·Δx/Δy).
- **Random**: one U, uniform: 25% of draws take the interior branch, 75% the border branch. **Inputs**: x, y.
- **Edge cases**: at Δx = Δy = 0 (any integer point) the border branch computes 0/0 = not-a-number (bad value, point replaced). Ties in rounding go to even.
- **Tests** (v=1): interior branch (U ≥ 0.75), (0.3, 0.2) → (0.15, 0.1); border branch with |Δx| ≥ |Δy|, (0.3, 0.1) → (0.4, 0.13333); border branch with |Δy| > |Δx|, (0.1, 0.3) → (0.13333, 0.4); (0.5, 0.5) rounds to (0,0) so x_r = y_r = 0 and the interior branch gives (0.25, 0.25).

## 57. butterfly
- **Parameters**: none.
- **Equations**: C = 4/√(3π) = 1.3029400317. R = v · C · sqrt(|x·y| / (x² + 4y² + ε)). x′ = R·x; y′ = 2R·y.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: on either axis R = 0. At the origin the ε keeps the quotient 0/ε = 0, so the result is (0,0).
- **Tests** (v=1): (0,0) → (0,0); (1,0) → (0,0); (2,1) → (1.30294, 1.30294); (−1,−1) → (−0.58269, −1.16538).

## 58. cell
- **Parameters**: `cell_size` 1 (s). Generator range: 0.5..2.5.
- **Equations**: i = floor(x/s), j = floor(y/s); Δx = x − i·s, Δy = y − j·s.
  Interleave the cell indices: i′ = 2i if i ≥ 0 else −(2i + 1); j′ likewise.
  x′ = v·(Δx + i′·s); y′ = −v·(Δy + j′·s).
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: s = 0 divides by zero (infinite index; undefined). The indices are held as 32-bit integers, so |x/s| beyond about 2·10⁹ overflows. Negative s is processed by the same formulas. Note the y output is negated.
- **Tests** (v=1): s=1: (0.5,0.5) → (0.5, −0.5); (1.5,0.5) → (2.5, −0.5); (−0.5,0.5) → (1.5, −0.5). s=2: (3,−1) → (5, −3). s=0.5: (−0.2, 0.7) → (0.8, −1.2).

## 59. cpow
- **Parameters**: `cpow_r` 1; `cpow_i` 0; `cpow_power` 1. Generator range: r 0..3, i −0.5..0.5, power integer 0..4.
- **Equations**: p = power. Choose k = floor(p·U). L = ln r = ½·ln(x² + y²).
  μ = cpow_r/p, ν = cpow_i/p. α = μ·φ + ν·L + 2πk/p. M = v · exp(μ·L − ν·φ).
  x′ = M cos α; y′ = M sin α.
- **Random**: one U, uniform, choosing the branch k. For p > 0 the choices are k = 0 … ⌈p⌉−1; for non-integer p the last branch is chosen less often. For p < 0, k is zero or negative.
- **Inputs**: x, y, φ, L.
- **Edge cases**: p = 0 divides by zero (a real possibility: the generator can draw 0). r = 0 gives L = −infinity. Large exponents overflow.
- **Tests**: random for p ≥ 2. For p = 1, cpow_r=1, cpow_i=0 the single branch is deterministic (k = 0 unless U = 1 exactly) and the map is the identity: (1,2) → (1,2), (−3,4) → (−3,4).

## 60. curve
- **Parameters**: `curve_xamp` 0; `curve_yamp` 0; `curve_xlength` 1; `curve_ylength` 1. Generator range: xamp −2.5..2.5, yamp −2..2, lengths 1..3.
- **Equations**: Lx = max(xlength², 1e-20), Ly = max(ylength², 1e-20).
  x′ = v·(x + xamp·exp(−y²/Lx)); y′ = v·(y + yamp·exp(−x²/Ly)).
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: zero lengths are clamped to 1e-20, so the exponent is huge and the term is 0 except at y = 0 exactly (exp(0) = 1).
- **Tests** (v=1): defaults (1,2) → (1,2); xamp=1, yamp=2, lengths 1: (0,0) → (1,2); (1,0) → (2, 0.73576); xamp=2, yamp=−1, xlength=2, ylength=0.5: (0.5, 1) → (2.05760, 0.63212).

## 61. edisc
- **Parameters**: none.
- **Equations**: T = x² + y² + 1. a = √(T + 2x), b = √(T − 2x). X = (a + b)/2. α₁ = ln(X + √(X − 1)). α₂ = −arccos(x/X). w = v/11.57034632.
  σ = −sin α₁ if y > 0, else sin α₁. x′ = w·cosh(α₂)·cos(α₁); y′ = w·sinh(α₂)·σ.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: mathematically X ≥ 1 and |x/X| ≤ 1, but rounding can push X just below 1 or |x/X| just above 1, giving not-a-number (bad value). y = 0 uses the "else" sign.
- **Tests** (v=1): (0,0) → (0.21686, 0); (1,1) → (0.07944, 0.06866); (1,−1) → (0.07944, −0.06866).

## 62. elliptic
- **Parameters**: none.
- **Equations**: T = x² + y² + 1. X = ½(√(T + 2x) + √(T − 2x)). a = x/X. B = √(max(0, 1 − a²)). S = √(max(0, X − 1)). w = 2v/π.
  x′ = w·atan2(a, B). y′ = w·ln(X + S) if y > 0, else −w·ln(X + S).
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: the two clamps prevent not-a-number from rounding. **y = 0 takes the negative branch**, so (2,0) maps to a negative y′.
- **Tests** (v=1): (0,0) → (0,0); (0,1) → (0, 0.45941); (2,1) → (0.67701, 0.78342); (2,−1) → (0.67701, −0.78342); (2,0) → (1, −0.69940).

## 63. escher
- **Parameters**: `escher_beta` 0 (β). Generator range: −π..π.
- **Equations**: L = ½·ln(x² + y²). μ = (1 + cos β)/2; ν = (sin β)/2. M = v·exp(μL − νφ); α = μφ + νL.
  x′ = M cos α; y′ = M sin α.
- **Random**: none. **Inputs**: x, y, φ.
- **Edge cases**: origin gives L = −infinity (M = 0 if μ > 0, but α is not-a-number: bad value). β = 0 is the identity (μ=1, ν=0).
- **Tests** (v=1): β=0: (1,0) → (1,0); (0,2) → (0,2); (−3,4) → (−3,4). β=π/2: (1,0) → (1,0); (0,1) → (0.32240, 0.32240); (e,0) → (1.44689, 0.79044). β=π, (0,1) → (1,0).

## 64. foci
- **Parameters**: none.
- **Equations**: D = v/(cosh x − cos y). x′ = D·sinh x; y′ = D·sin y.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: cosh x = cos y only at x = 0, y = 2kπ, where D is infinite (bad value). For |x| beyond about 709 the exponentials overflow or underflow, giving bad values.
- **Tests** (v=1): (0, π/2) → (0, 1); (1,0) → (2.16395, 0); (−1,1) → (−1.17195, 0.83914).

## 65. lazysusan
- **Parameters**: `lazysusan_x` 0; `lazysusan_y` 0; `lazysusan_spin` 0; `lazysusan_twist` 0; `lazysusan_space` 0. Generator range: x,y ±2; spin ±π; twist ±2; space ±2.
- **Equations** (non-standard weight: v is also the disc radius): X = x − lx, Y = y + ly, d = √(X² + Y²).
  - If d < v: α = atan2(Y, X) + spin + twist·(v − d); x′ = v·d·cos α + lx; y′ = v·d·sin α − ly.
  - Otherwise: R = v·(1 + space/d); x′ = R·X + lx; y′ = R·Y − ly.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: the offset is added to y on input but subtracted on output (sign asymmetry is intentional). d = 0 only reaches the second branch if v ≤ 0, where space/d is a division by zero. The map can be discontinuous across d = v.
- **Tests**: v=1, defaults: (2,0) → (2,0); (0.5,0) → (0.5,0). v=2, lx=0.5, ly=0.25, spin=1, twist=0.5, space=0.3: (1, 0.2) → (−0.48885, 0.66223). v=1 with the same parameters: (3, 1) → (3.26833, 1.13416).

## 66. loonie
- **Parameters**: none.
- **Equations** (non-standard weight: v also sets a radius): if x² + y² < v²: R = v·√(v²/(x² + y²) − 1); otherwise R = v. (x′, y′) = R·(x, y).
- **Random**: none. **Inputs**: x, y, r².
- **Edge cases**: at the origin the first branch computes infinity × 0 = not-a-number (bad value). r² exactly equal to v² takes the second branch. Negative v: v² still compares, and the first branch yields a negated result.
- **Tests**: v=1: (2,0) → (2,0); (0.6,0) → (0.8,0). v=2: (1,0) → (3.46410, 0); (3,0) → (6,0).

## 67. pre_blur
- **Parameters**: none.
- **Equations** (non-standard weight, and not an output term): G = v·(sum of four U − 2); Ψ = 2π·U. Before any other variation of the same transform is evaluated, replace x ← x + G cos Ψ, y ← y + G sin Ψ. These replaced coordinates are also what r, θ, φ are computed from. pre_blur contributes **nothing** to the summed output on its own.
- **Random**: one U for Ψ, plus four U for G (uniform angle, pseudo-Gaussian radius). **Inputs**: x, y.
- **Edge cases**: in a transform whose only variation is pre_blur the output is (0,0). Weight 0 means no blur.
- **Tests**: random; the replaced point's offset from the original has mean (0,0) and per-axis variance v²/6.

## 68. modulus
- **Parameters**: `modulus_x` 0; `modulus_y` 0 (written mx, my). Generator range: ±1.
- **Equations**: for the x coordinate with W = 2·mx:
  - if x > mx: x′ = v·(−mx + rem(x + mx, W));
  - if x < −mx: x′ = v·(mx − rem(mx − x, W));
  - otherwise x′ = v·x.
  The y coordinate is the same with my.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: mx = 0 makes W = 0, so any x ≠ 0 gives not-a-number (bad value); with the default of 0 the variation destroys almost every point. A negative mx makes both outer tests true for most x; the first tested branch wins.
- **Tests** (v=1): mx=my=1: (0.5,0.5) → (0.5,0.5); (1.5,0) → (−0.5,0); (−1.5,0) → (0.5,0). mx=0.5, my=2: (1.2, −5) → (0.2, −1).

## 69. oscilloscope
- **Parameters**: `oscope_separation` 1 (also read as `oscilloscope_separation`); `oscope_frequency` π (`oscilloscope_frequency`); `oscope_amplitude` 1; `oscope_damping` 0. Generator range: separation 0..2, frequency ±π, amplitude 1..3, damping 0..1.
- **Equations**: t = amplitude·cos(2π·frequency·x) + separation if damping is exactly 0; otherwise t = amplitude·exp(−|x|·damping)·cos(2π·frequency·x) + separation.
  x′ = v·x; y′ = −v·y if |y| ≤ t, else v·y.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: t may be negative, in which case no y reflects. |y| = t reflects.
- **Tests** (v=1): defaults (0, 0.5) → (0, −0.5); defaults (0, 2.5) → (0, 2.5). amplitude=2, frequency=0.5, damping=1, separation=0: (0.3, 0.1) → (0.3, −0.1) (here t = 0.87088).

## 70. polar2
- **Parameters**: none.
- **Equations**: x′ = (v/π)·θ; y′ = (v/π)·ln r = (v/(2π))·ln(x² + y²).
- **Random**: none. **Inputs**: θ, x² + y².
- **Edge cases**: origin gives ln 0 = −infinity (bad value).
- **Tests** (v=1): (0,1) → (0,0); (1,0) → (0.5,0); (0, e^π) → (0,1); (−1,−1) → (−0.75, 0.11032).

## 71. popcorn2
- **Parameters**: `popcorn2_x` 0; `popcorn2_y` 0; `popcorn2_c` 0. Generator range: x,y 0..0.2; c 0..5.
- **Equations**: x′ = v·(x + popcorn2_x·sin(tan(c·y))); y′ = v·(y + popcorn2_y·sin(tan(c·x))).
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: tan is infinite at odd multiples of π/2 in c·y or c·x, so sin of it is undefined (bad value).
- **Tests** (v=1): defaults any point → itself; c=1, x=y params 0.5: (0,0) → (0,0); (π/4, 0) → (0.78540, 0.42074). c=2, popcorn2_x=0.5, popcorn2_y=0.25: (0.2, 0.3) → (0.51600, 0.40258).

## 72. scry
- **Parameters**: none.
- **Equations** (non-standard weight: v is not a plain output factor): D = 1/(r·(r² + 1/(v + ε))). x′ = D·x; y′ = D·y.
- **Random**: none. **Inputs**: x, y, r.
- **Edge cases**: r = 0 divides by zero. v = 0: the 1/(v+ε) term is 1e10, so D ≈ 0. v = −ε divides by zero.
- **Tests**: v=1: (1,0) → (0.5,0); (0,2) → (0,0.2). v=2: (1,0) → (0.66667, 0).

## 73. separation
- **Parameters**: `separation_x` 0; `separation_xinside` 0; `separation_y` 0; `separation_yinside` 0. Generator range: x,y 0..2; xinside,yinside ±1.
- **Equations** (sx, sy, ix, iy denote the four parameters): x′ = v·(√(x² + sx²) − ix·x) if x > 0, else x′ = −v·(√(x² + sx²) + ix·x). y′ likewise with sy, iy and y.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: x = 0 takes the second branch, giving −v·|sx|. Output at x = 0 therefore jumps from +|sx| (as x → 0⁺) to −|sx|.
- **Tests** (v=1): defaults (1,1) → (1,1); defaults (−1,−2) → (−1,−2); sx=sy=1: (0,0) → (−1,−1); sx=sy=1: (1,1) → (1.41421, 1.41421); sx=1, ix=0.5, sy=0, iy=0.5: (2,−2) → (1.23607, −1).

## 74. split
- **Parameters**: `split_xsize` 0; `split_ysize` 0.
- **Equations**: x′ = v·x if cos(π·y·ysize) ≥ 0, else −v·x. y′ = v·y if cos(π·x·xsize) ≥ 0, else −v·y.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: the cross-dependence is intentional: the **y** coordinate decides the sign of the **x** output and vice versa. When the cosine is within rounding of 0 the branch is unstable.
- **Tests** (v=1): defaults any point → itself; sizes 1 and 1: (1,2) → (1,−2).

## 75. splits
- **Parameters**: `splits_x` 0; `splits_y` 0. Generator range: ±1.
- **Equations**: x′ = v·(x + splits_x) if x ≥ 0, else v·(x − splits_x); y′ likewise with splits_y.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: step discontinuity of 2·splits across each axis; x = 0 takes the positive branch.
- **Tests** (v=1): splits_x=1, splits_y=2: (0,0) → (1,2); (3,4) → (4,6); (−3,−4) → (−4,−6).

## 76. stripes
- **Parameters**: `stripes_space` 0; `stripes_warp` 0. Generator range: space 0..1, warp 0..5.
- **Equations**: x_r = floor(x + ½); Δ = x − x_r. x′ = v·(Δ·(1 − space) + x_r); y′ = v·(y + Δ²·warp).
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: space = 1 collapses x′ to the integers; space > 1 mirrors the stripe.
- **Tests** (v=1): defaults any point → itself; space=0.2, warp=1: (0.2, 1) → (0.16, 1.04); space=1, warp=0: (0.3, 2) → (0, 2).

## 77. wedge
- **Parameters**: `wedge_angle` 0; `wedge_hole` 0; `wedge_count` 1; `wedge_swirl` 0. Generator range: angle 0..π, hole ±0.25, count integer 1..5, swirl 0..1.
- **Equations**: α = φ + swirl·r. k = floor((count·α + π)/(2π)). F = 1 − angle·count/(2π). α′ = α·F + k·angle. R = v·(r + hole).
  x′ = R cos α′; y′ = R sin α′.
- **Random**: none. **Inputs**: x, y, r, φ.
- **Edge cases**: at the origin r = 0, φ = 0 (no division): the result is (v·hole, 0). count = 0 makes k = floor(½) = 0.
- **Tests** (v=1): defaults (1,0) → (1,0); defaults (0,2) → (0,2); hole=0.5, (2,0) → (2.5,0); swirl=1, (1,0) → (0.54030, 0.84147); angle=π/2, count=2, (0,2) → (−1.41421, 1.41421).

## 78. wedge_julia
- **Parameters**: `wedge_julia_power` 1; `wedge_julia_dist` 0; `wedge_julia_count` 1; `wedge_julia_angle` 0. Generator range: power integer 2..6, dist 1, count integer 1..3, angle 0..π.
- **Equations**: p = power. Choose j = T(|p|·U) where T drops the fractional part. α = (φ + 2πj)/p. k = floor((count·α + π)/(2π)). F = 1 − angle·count/(2π). α′ = α·F + k·angle.
  R = v·(x² + y²)^(dist/(2p)). x′ = R cos α′; y′ = R sin α′.
- **Random**: one U choosing the branch j ∈ {0, …, ⌈|p|⌉−1} (uniform for integer p, the last branch under-weighted for fractional p).
- **Inputs**: x, y, φ, x² + y².
- **Edge cases**: p = 0 divides by zero. The origin with a negative exponent is infinite; with a positive exponent R = 0. With the default dist = 0, R = v for every point (the point's radius is discarded).
- **Tests**: random in general. With p=1 there is a single branch (j = 0 unless U = 1 exactly), and dist=1, count=2, angle=π/2, (0,2) → (−1.41421, 1.41421), the same as wedge.

## 79. wedge_sph
- **Parameters**: `wedge_sph_angle` 0; `wedge_sph_hole` 0; `wedge_sph_count` 1; `wedge_sph_swirl` 0. Generator range: as for wedge.
- **Equations**: Q = 1/(r + ε). α = φ + swirl·Q. k = floor((count·α + π)/(2π)). F = 1 − angle·count/(2π). α′ = α·F + k·angle. R = v·(Q + hole).
  x′ = R cos α′; y′ = R sin α′.
- **Random**: none. **Inputs**: x, y, r, φ.
- **Edge cases**: at the origin Q = 1e10 (the ε guard), so the result is a huge but finite point (v·1e10 in direction φ = 0); the bad-value rule replaces it if the magnitude exceeds 1e10 (for example v = 1 with a positive hole).
- **Tests** (v=1): defaults (2,0) → (0.5,0); defaults (0,1) → (0,1); hole=0.5, (2,0) → (1,0).

## 80. whorl
- **Parameters**: `whorl_inside` 0; `whorl_outside` 0. Generator range: both 0..1.
- **Equations** (non-standard weight: v is also the switching radius): α = φ + inside/(v − r) if r < v, else φ + outside/(v − r).
  x′ = v·r·cos α; y′ = v·r·sin α.
- **Random**: none. **Inputs**: x, y, r, φ.
- **Edge cases**: at r = v the outside branch divides by zero (infinity, or not-a-number if outside = 0 too). The origin has α = 0 + inside/v and a zero radius, giving (0,0).
- **Tests**: v=1, defaults: (1,0) → (1,0); (0,2) → (0,2). v=1, inside=1, outside=2: (0.5, 0) → (−0.20807, 0.45465); (2, 0) → (−0.83229, −1.81859).

## 81. waves2
- **Parameters**: `waves2_scalex` 0; `waves2_scaley` 0; `waves2_freqx` 0; `waves2_freqy` 0. Generator range: scales 0.5..1.5, frequencies 0..4.
- **Equations**: x′ = v·(x + scalex·sin(y·freqx)); y′ = v·(y + scaley·sin(x·freqy)).
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: none.
- **Tests** (v=1): defaults any point → itself; scalex=1, scaley=2, freqx=π, freqy=π/2: (0, 0.5) → (1, 0.5).

## 82. exp
- **Equations**: x′ = v·eˣ·cos y; y′ = v·eˣ·sin y. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: x above about 709 overflows to infinity (bad value).
- **Tests** (v=1): (0,0) → (1,0); (0, π/2) → (0,1); (0,π) → (−1,0); (1, π/3) → (1.35914, 2.35410).

## 83. log
- **Equations**: x′ = v·ln r = (v/2)·ln(x² + y²); y′ = v·φ. **Parameters**: none. **Random**: none. **Inputs**: x² + y², φ.
- **Edge cases**: the origin gives −infinity. φ is in (−π, π]; the branch cut lies on the negative x axis, with y = +0 giving +π and y = −0 giving −π.
- **Tests** (v=1): (1,0) → (0,0); (e,0) → (1,0); (0,1) → (0, 1.57080); (−1,0) → (0, 3.14159).

## 84. sin
- **Equations**: x′ = v·sin x·cosh y; y′ = v·cos x·sinh y. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: |y| beyond about 710 overflows.
- **Tests** (v=1): (0,0) → (0,0); (π/2,0) → (1,0); (0,1) → (0, 1.17520); (1, 0.5) → (0.94886, 0.28155).

## 85. cos
- **Equations**: x′ = v·cos x·cosh y; y′ = −v·sin x·sinh y. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: as sin.
- **Tests** (v=1): (0,0) → (1,0); (π,0) → (−1,0); (0,1) → (1.54308, 0); (1, 0.5) → (0.60926, −0.43849).

## 86. tan
- **Equations**: D = cos 2x + cosh 2y; x′ = v·sin 2x/D; y′ = v·sinh 2y/D. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: D = 0 at x = π/2 + kπ, y = 0 (division by zero).
- **Tests** (v=1): (0,0) → (0,0); (π/4,0) → (1,0); (0, 0.5) → (0, 0.46212); (1, 0.5) → (0.80688, 1.04283).

## 87. sec
- **Equations**: D = cos 2x + cosh 2y; x′ = 2v·cos x·cosh y/D; y′ = 2v·sin x·sinh y/D. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: D = 0 at x = π/2 + kπ, y = 0.
- **Tests** (v=1): (0,0) → (1,0); (π,0) → (−1,0); (0,1) → (0.64805, 0); (1, 0.5) → (1.08127, 0.77819).

## 88. csc
- **Equations**: D = cosh 2y − cos 2x; x′ = 2v·sin x·cosh y/D; y′ = −2v·cos x·sinh y/D. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: D = 0 at x = kπ, y = 0, including the origin.
- **Tests** (v=1): (π/2,0) → (1,0); (−π/2,0) → (−1,0); (1, 0.5) → (0.96861, −0.28741).

## 89. cot
- **Equations**: D = cosh 2y − cos 2x; x′ = v·sin 2x/D; y′ = −v·sinh 2y/D. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: D = 0 at x = kπ, y = 0.
- **Tests** (v=1): (π/4,0) → (1,0); (π/2,0) → (0,0); (1, 0.5) → (0.46411, −0.59983).

## 90. sinh
- **Equations**: x′ = v·sinh x·cos y; y′ = v·cosh x·sin y. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: |x| beyond about 710 overflows.
- **Tests** (v=1): (0,0) → (0,0); (0, π/2) → (0,1); (1,0) → (1.17520, 0); (1, 0.5) → (1.03134, 0.73979).

## 91. cosh
- **Equations**: x′ = v·cosh x·cos y; y′ = v·sinh x·sin y. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: as sinh.
- **Tests** (v=1): (0,0) → (1,0); (0,π) → (−1,0); (1,0) → (1.54308, 0); (1, 0.5) → (1.35418, 0.56342).

## 92. tanh
- **Equations**: D = cos 2y + cosh 2x; x′ = v·sinh 2x/D; y′ = v·sin 2y/D. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: D = 0 at x = 0, y = π/2 + kπ.
- **Tests** (v=1): (0,0) → (0,0); (0, π/4) → (0,1); (0.5, 0.5) → (0.56408, 0.40390).

## 93. sech
- **Equations**: D = cos 2y + cosh 2x; x′ = 2v·cosh x·cos y/D; y′ = −2v·sinh x·sin y/D. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: D = 0 at x = 0, y = π/2 + kπ.
- **Tests** (v=1): (0,0) → (1,0); (0,π) → (−1,0); (0.5, 0.5) → (0.94998, −0.23983).

## 94. csch
- **Equations**: D = cosh 2x − cos 2y; x′ = 2v·sinh x·cos y/D; y′ = −2v·cosh x·sin y/D. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: D = 0 at x = 0, y = kπ, including the origin.
- **Tests** (v=1): (0, π/2) → (0,−1); (0, −π/2) → (0,1); (0.5, 0.5) → (0.91207, −1.07823).

## 95. coth
- **Equations**: D = cosh 2x − cos 2y; x′ = v·sinh 2x/D; **y′ = +v·sin 2y/D**. **Parameters**: none. **Random**: none. **Inputs**: x, y.
- **Edge cases**: D = 0 at x = 0, y = kπ. **The sign of y′ is positive in the original program, which differs from the true complex hyperbolic cotangent (that has −sin 2y).** Match the original so imported flames look the same; note the deviation from textbook coth in the shader comment.
- **Tests** (v=1): (0, π/4) → (0, **+1**); (0, π/2) → (0,0); (0.5, 0.5) → (1.17195, **+0.83914**).

## 96. auger
- **Parameters**: `auger_freq` 1; `auger_weight` 0.5 (w); `auger_sym` 0; `auger_scale` 1 (c). Generator range: freq integer 1..5, weight 0.5..1, sym 0, scale 0..1.
- **Equations**: s = sin(freq·x); u = sin(freq·y). Δy = y + w·(c·s/2 + |y|·s); Δx = x + w·(c·u/2 + |x|·u).
  x′ = v·(x + sym·(Δx − x)); y′ = v·Δy.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: sym = 0 means x is unchanged and only y is displaced.
- **Tests** (v=1): defaults (0,0) → (0,0); defaults (π/2, 0) → (1.57080, 0.25); freq=2, (0.3, 0.4) → (0.3, 0.65409); sym=1, (1,2) → (1.68197, 3.05184).

## 97. flux
- **Parameters**: `flux_spread` 0. Generator range: 0.5..1.
- **Equations** (non-standard weight: v also sets the two focus points at x = ±v): a₊ = x + v, a₋ = x − v.
  R = v·(2 + spread)·((y² + a₊²)/(y² + a₋²))^(1/4). α = ½·(atan2(y, a₋) − atan2(y, a₊)).
  x′ = R cos α; y′ = R sin α.
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: at (v, 0) the denominator is zero (infinite R, bad value). At (−v, 0) R = 0.
- **Tests**: v=1, spread=0: (0,1) → (1.41421, 1.41421); (0,0) → (0, 2). v=2, spread=0: (1,1) → (3.14461, 5.08808).

## 98. mobius
- **Parameters** (eight reals; the file also accepts the aliases in brackets): `mobius_re_a` [`Re_A`] 0; `mobius_im_a` [`Im_A`] 0; `mobius_re_b` [`Re_B`] 0; `mobius_im_b` [`Im_B`] 0; `mobius_re_c` [`Re_C`] 0; `mobius_im_c` [`Im_C`] 0; `mobius_re_d` [`Re_D`] 0; `mobius_im_d` [`Im_D`] 0. Generator range: each ±1.
- **Equations**: treat the point as the complex number z = x + iy and the parameters as complex a, b, c, d. The result is v·(a z + b)/(c z + d), written out: u = (a z + b), w = (c z + d), with
  u = (Re a·x − Im a·y + Re b) + i(Re a·y + Im a·x + Im b), and w likewise from c and d.
  x′ = v·(Re u·Re w + Im u·Im w)/|w|²; y′ = v·(Im u·Re w − Re u·Im w)/|w|².
- **Random**: none. **Inputs**: x, y.
- **Edge cases**: |w|² = 0 divides by zero. With all parameters left at 0 this holds at every point, so a mobius with no parameters destroys every point (bad value). The pole is at z = −d/c.
- **Tests** (v=1): identity (Re a = 1, Re d = 1, rest 0): (1,2) → (1,2); (−3,4) → (−3,4). Inversion (Re b = 1, Re c = 1, rest 0): (2,0) → (0.5,0); (0,1) → (0,−1). a = 1+i, d = 1: (1,1) → (0,2). b = 1, c = i, d = 0: (1,0) → (0,−1).

---

## 46. secant2 (done)

Implemented in the shader as `secant2` (replacing the paper's original secant, which left a gap for |y| < 1).
A flame file's `secant2` attribute maps to it by name. Equations, for reference and for tests:

- **Equations** (non-standard weight): R = v·r; C = cos R. x′ = v·x. y′ = v·(1/C + 1) if C < 0, else v·(1/C − 1).
- **Edge cases**: C = 0 divides by zero; the bad-value rule removes the resulting point.
- **Tests** (v=1): (0,0) → (0,0); (1,0) → (1, 0.85082); (π,0) → (π, 0).

## Corrections made to 47 and 48 (same change)

- **twintrian**: the logarithm applies to sin² R alone, then cos R is added; a result that is not-a-number, infinite or beyond 1e10 in magnitude becomes −30.
- **cross**: the divisor is √((x² − y²)² + 1e-10).
