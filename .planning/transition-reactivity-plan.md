# Transitions: shipped defaults + audio response — plan (2026-10-27)

Status: **BUILT 2026-10-27, uncommitted, unseen in app** (judging entry in `.planning/app-checklist.md` 9b). All six
build steps done. Differences from the plan below: transition files carry no `metaBindings` (the TRANS knobs don't use
the Metaknob; the heuristic one is kept); registries now hand out `ISFFilter.newInstance()` so a default saved or removed
in-session applies to the next copy (it didn't, for effects either); "modified" (`TransitionOps.isModified`) compares
against the resolved default; Inferno Film Burn's Burn Spread went 0.46429 -> 0.45 to meet the at-rest limit; the
blockiness LFO is a 2-beat square (flips on every beat).

## Why it needed an audit
The transition shader used to render every frame, including at rest. **Since 2026-10-27 it doesn't:** at progress
exactly 0 or 1 `Renderer.renderMixer` copies the visible deck through (`Renderer.restingTexture`), so the resting
picture is exact by construction. The limits below still matter, now for a different reason: an input that leaks at
the ends makes the picture *pop* the instant a fade leaves rest (progress 0 → 0.001), instead of flickering at rest.

## Per-transition results (inputs in normalized 0..1 units)

| Transition | At rest (p = 0 and 1) | Safe to modulate | Do not modulate |
|---|---|---|---|
| linear_crossfade | exact | nothing continuous | `curveMode` (discrete) |
| luminous_flash | exact while the flash envelope is ~0 at the ends | `intensity` (at spread ≤ default), `colorTemp` | `spread` above **~0.48**: the Gaussian flash leaks at the ends (at spread 1 and intensity 1 the envelope is still ~0.27 at rest, clearly visible). Keep base + swing ≤ 0.45 or leave it alone |
| film_burn | exact while `burnSpread_` (0.02 + 0.28·s) ≤ 0.15 at p = 0 | `glowIntensity` (only visible on the burn edge) | `burnSpread` above **~0.46** (default 0.357): the ember edge and a little of deck B show through at p = 0. `tintMode` (discrete) |
| cyber_datamosh | exact (after the 2026-10-27 NaN fix below) | `packetLoss`, `rgbSplit`, `blockiness` (all gated by the envelope) | — |
| kinetic_zoom | exact (zoom/blur act on the deck that is invisible, or vel = 0) | `zoomFactor`, `motionBlur`, `chromatic` | — |
| liquid_displacement | exact (displacement × sin(πp) = 0) | `warpStrength`, `chromaticDispersion`, `viscosity` | — |
| noise_dissolve | exact for any softness (fbm stays in 0..0.875, inside the threshold margins) | `softness`, `chromaShift`; `noiseScale` is safe but reshapes the dissolve pattern mid-move (may look like boiling, judge by eye) | — |
| vortex_swirl | exact (swirl angle and chroma are 0 on the visible deck) | `rotations`, `radius`, `chromatic` | — |

Never modulate `progress` (it is the crossfader).

## Bug found: Cyber Datamosh at rest on deck B — FIXED 2026-10-27 (desktop + web, uncommitted at time of writing)
`env = sin(p * PI)` with `#define PI 3.14159265359`. That literal rounds to the float just *above* π, so at p = 1.0
`sin` returns about -8.7e-8, and `pow(env, 1.4)` of a negative number is undefined (NaN on common GPUs). `splitAmt`
becomes NaN, so the red and blue channels sample deck B at a NaN coordinate. Expected symptom: with Cyber Datamosh
selected and the crossfader fully on B, B's red/blue look wrong (smeared or taken from the frame edge). It needs
checking in the app. Fix: `float env = max(sin(p * PI), 0.0);`. The other `sin(p * PI)` uses (liquid_displacement,
vortex_swirl, kinetic_zoom) don't feed `pow`, so the tiny negative is harmless there. The web TV copy needs the same
fix (web shader sync / `WebSyncTest`).

Also: `cyber_datamosh.blockiness` is declared 8..64, not normalized 0..1, against the bundled-input rule in
DECISIONS.md. Normalize it in the same pass (update the shipped `.lsdtrans` presets that set it).

## Response ideas (starting points, to judge by eye)
Transitions last seconds, so use followers and 1–2 beat LFOs, never slow LFOs. One active per input, alternates bypassed.
- luminous_flash: `intensity` ← kick (`audio_flux_bass`), so the midpoint flash punches on the beat; `colorTemp` ← highs (bypassed).
- film_burn: `glowIntensity` ← bass/kick.
- cyber_datamosh: `packetLoss` ← hats (`audio_flux_high`); `rgbSplit` ← kick; `blockiness` ← 1-beat square LFO (bypassed).
- kinetic_zoom: `zoomFactor` ← kick (zoom punch); `motionBlur` ← loudness; `chromatic` ← highs (bypassed).
- liquid_displacement: `warpStrength` ← bass; `chromaticDispersion` ← highs.
- noise_dissolve: `chromaShift` ← kick; `softness` ← loudness; `noiseScale` ← mids (bypassed, check for boiling).
- vortex_swirl: `rotations` ← bass (swirl surges); `chromatic` ← kick; `radius` (bypassed).
- linear_crossfade: none.

## Build steps (after FX lands)
1. `stock_defaults/transitions/<id>.json`. The transition registry creates the filter, so `resolveDefault` needs to
   know the kind and read `transitions/` for transitions (filter ids and transition ids must never cross over).
   **Ids do collide:** `vortex_swirl` is both a filter and a transition. Today the FX agent's `shippedDefault` rejects a
   file naming inputs the shader lacks, which keeps the filter default off the transition by luck. Make the kind
   explicit rather than relying on that.
   Transition knobs: the TRANS bank K2–K4 is bound like the FX banks (`FxMacroSync`); check whether a shipped
   `metaBindings` / `knobLayout` is needed to pick which inputs land on K2–K4.
2. "Save as Default for <transition>" and "Remove Saved Default" in the transition menu (`ParametersTabs` TRANS
   options, plus the Edit row if it has one). The content-hash override already handles transitions.
3. Normalize datamosh `blockiness` (the NaN fix is done).
4. Regenerate the 8 shipped `.lsdtrans` presets and check `festival_elite.lsdtransplay`. They store full parameters,
   so without this AutoVJ and the playlist never show the new responses.
5. Tests: extend `StockDefaultsTest` to `transitions` (inputs exist, no `progress`, one active per input,
   `|depth| <= 1`, LFO swings in range), plus an **at-rest limit table** (`luminous_flash.spread <= 0.45`,
   `film_burn.burnSpread <= 0.45`) checked against base + swing.
6. Docs per the repo's "done" rule; add a judging entry to `.planning/app-checklist.md` (rest on A and on B with music
   playing: nothing may move; then slow manual fades on every transition).
