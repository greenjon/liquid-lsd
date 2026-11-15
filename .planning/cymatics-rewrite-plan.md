# Chladni Cymatics rewrite

**Status 2026-11-15: rewrite shipped e9cbbee (TV v40); Pitch Follow implemented, uncommitted. Shader, stock default, 5 web presets, docs, tooltips, checklist done; render-checked on the web; gpu_bench.mjs hangs in this environment (Firefox never reports), so GPU cost is unmeasured.**

Goal: replace the "spongey sameness" with the geometric variety of real plates and water cymatics.
Prototype renders (numpy port, session scratchpad): current vs proto contact sheets, 2026-11-15.

## Why the current shader looks spongey
1. Modes are fractional (randomized 2..9 continuous, ±0.15 constant wobble); real plates ring only at integer modes.
2. Sand density is `exp(-(|w|·k)²)`: wave value, not distance to the nodal line, so low-amplitude areas fill with fuzz and line width varies wildly.
3. One fixed construction (`A − B + 0.5·cos·cos`; circle = `sin(r)·cos(mθ)`) means every roll is the same family.
4. No N-fold symmetry, so none of the water-mandala (Faraday) patterns exist.

## Decisions
- Input NAMEs stay (DECISIONS: names never change); meanings, labels and ranges change. Stock defaults, web presets and TV channels are rerolled (user OK 2026-11-15).
- Mode numbers stay continuous inputs (not STEP) so modulators can push them; **the shader snaps** to the integer mode and does the in-between itself (see Mode change).

## New inputs
| NAME | Label | Range | Meaning |
|---|---|---|---|
| PlateShape | Plate | 0..2 STEP 1 | 0 Square plate, 1 Round plate, 2 Water |
| FrequencyM | Mode M | 1..16 | Square: x/y mode m. Round: number of diameters. Water: second ring count |
| FrequencyN | Mode N | 1..16 | Square: mode n. Round: number of rings (Bessel zero index). Water: main ring count (wave number) |
| FrequencyL | Symmetry | 3..12 STEP 1 | Water only: N-fold symmetry (5/7/8/10/12 give quasi-periodic mandalas) |
| ModeMix (new) | Mode Mix | -1..1 | Mixing angle of the paired mode: square/round (m,n) with (n,m); water ring N with ring M. The main variety knob |
| NodeSharpness | Line Width | 0..1 | Sand line width in pixels-at-1080p, from distance-to-node |
| SandAccumulation | Settled | 0..1 | 0 = sand scattered over the plate, 1 = all on the lines |
| VibrationSpeed | Vibration | 0..1 | Phase speed of grain jitter at antinodes (bouncing sand), not frequency wobble |
| ModeSnap (new) | Mode Snap | 0..1 | 0 = old free sliding, 1 = hard switch; in between = width of the scatter-and-resettle window |
| InvertMode, Glow, PaletteMode, HueOffset, Scale | unchanged | | Invert draws antinodes (water-light look in Water mode) |

## Shader core
- Square: free-edge cosine modes on [-1,1]: `A = cos(nπx/2)cos(mπy/2)`, `B` swapped, `w = cos α·A + sin α·B`.
- Round: `J_m(j_{m,n}·r)·cos(mθ)` mixed with the swapped pair. Bessel via a cheap approximation (small-r power series below ~m+1, asymptotic `sqrt(2/πx)·cos(x − mπ/2 − π/4)` above, blended); zeros from McMahon's formula. No loops over the integral form (web/iGPU cost).
- Water: `Σ_{j<S} cos(k·(x cos(πj/S) + y sin(πj/S)))` for ring count N, mixed with the same at ring count M, plus a little `J_0(kr)` so the centre gets the concentric rings real Faraday dishes show; circular dish mask.
- Sand: `d = |w| / |∇w|` with **analytic gradients** for all three (Bessel derivative `(J_{m-1} − J_{m+1})/2`), line density `exp(-(d/width)²)`; grain hash; unsettled sand = sparse grains weighted by `1 − |w|/max`, jittered by VibrationPhase.
- Mode change: `mode = floor(M)`, frac `f`; within the ModeSnap window around the step, Settled is pulled toward 0 (sand scatters), the image crossfades old → new pattern, then Settled recovers. That is the "expression": a kick or swell bumping M makes the plate visibly throw and resettle.
- Keep palettes, glow, border mask, `LSD_PHASES` (VibrationPhase).

## Not doing
- Real particle simulation (persistent buffer). Revisit only if the faked settle reads poorly.
- Triangular/hexagonal plates (Water with Symmetry 3/6 covers the look).

## Overlap check
`quasicrystal` already does 5..8-fold gratings as soft bands. Water mode must read differently: thin sand lines, round dish, central ring term, Invert water-light. Compare side by side before shipping.

## Steps
1. Shader rewrite in `library/sources/chladni_cymatics/chladni_cymatics.fs` (+ default_sources copy if still mirrored); `./gradlew syncWeb`; `./scripts/shipped_source_hashes.py`.
2. Offline render check (numpy port or Xvfb tour frame) of each Plate × a spread of modes/mix; GPU cost on `web/checks/gpu_bench.html` vs the current shader.
3. Stock defaults `stock_defaults/sources/chladni_cymatics.json`: integer-range rolls, ModeMix roll, Plate roll; audio: kick bumps M by one mode (SNAP), energy settles N, highs thin the lines, drop switches Plate/Symmetry (bypassed alt); macro bank PLATE/MODE/MIX/LINES. Update GeneratorDefaultsTest macro list.
4. Reroll web presets in `scripts/gen_web_presets.py` (web-cymatics-sub, -rings, ambient, hard, mid-lace) + regenerate `stock-chladni_cymatics`; reactivity.test.mjs ambient rules; bump TV_VERSION; render_check.
5. Docs: SourceDocRegistry descriptions + param tooltips, docs/user_guide/visual_sources.md, both release-notes files, DECISIONS unstepped list (discrete-parameters note), app-checklist item replaced.
6. Tests: full gradle test + web tests; publish TV only on user say-so.

## Pitch Follow (added 2026-11-15, after the rewrite shipped as TV v40)
Option 1 of "make the geometry frequency-driven": the loudest held tone picks the mode. Two persistent passes:
specState (80x1, per-bin level smoothed over 0.6 s) and pitchState (1x1: current peak bin, glided log-pitch).
A new peak must beat the current one by 0.08 (6 dB); 187 Hz - 3.5 kHz; +3 dB/oct tilt; modes scale by
sqrt(pitch/440) (Chladni's law). Tuned offline on 5 tracks (numpy sim of SpectrumAnalyzer): 15-30 target
changes/min, holds 2-3 s; a plain argmax gave 500-1000/min. Option 2 (every mode driven by its spectral
energy) rejected: broadband music makes the mush we just removed.
