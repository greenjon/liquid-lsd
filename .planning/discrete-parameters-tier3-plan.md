# Discrete parameters, Tier 3 (v1.1, 2026-10-07)

Status: PLAN, not started. Follows `.planning/discrete-parameters-plan.md` (Tier 1 + 2 shipped, commit c769b22).

## Decision (owner, 2026-10-07)

Drop the Mandala / `meta.json` parts. No more non-ISF sources are planned, so Mandala dynamic step counts (Lobes, Recipe, Hue Sweep) and a `meta.json` `steps` key
buy almost nothing; their hand-built widgets stay as they are. Do **only the generic chooser** below. Items 4 (trigger params as momentary buttons) and 5
(controller detents / push-click stepping) stay unscheduled until someone asks for them.

## Why the generic chooser is still worth it

It is not for Mandala. About 28 stock ISF inputs now carry `steps` (and four carry `labels`), but the VAL panel still gives them a plain range slider; only `mode3D`
has a real chooser, via a hard-coded branch. A generic branch gives every stepped parameter a combo for free and lets `mode3D` use the ISF `LABELS` instead of a duplicate list.

## Design

In `ui/ValueParamSection.kt` (the branch chain around the `is3DMode` block, ~line 385):

1. Add a generic branch `param.steps in 2..DiscreteTicks.MAX_TICKS` that draws `ImGui.combo` with entries from `param.labels`, else the step values as integers
   (`param.minClamp + i * stepSize`). Write back exactly as the `is3DMode` branch does: `baseValue = stepValue`, and `baseMin = baseMax = stepValue` when not randomizable.
   Respect the existing `isLocked` disabling.
2. Delete the `is3DMode` branch, `MODE_3D_LABELS`, `get3DModeLabel` and the `is3DMode` entries in `liveLabel` / `isSpecialValue`; `3d_elevation.mode3D` has `LABELS` in its ISF header and takes the generic path.
   Keep the tooltip text for the 3D modes by moving it into the shader's `DESCRIPTION`/`LABELS` or dropping it (labels already name the modes).
3. Steps above 16 (`retro_crt.scanlineCount`, `dynamic_spiral.MaxPoints`) get no combo; keep the range slider. `MaxPoints` keeps its preset grid (`isMaxPoints`).
4. `liveLabel` and the "Static Initial Value" caption use `DiscreteTicks.readout(param, v)` for any stepped parameter, so the VAL header shows "Hex-Planar" instead of 1.
5. Randomization: with `randomizeBase` on, the Initial Range min/max sliders stay (snapping happens at evaluation); do not hide them.

## Tests

- Pure helper for the combo entries (labels, else integers, from `steps`/`minClamp`/`maxClamp`), including a negative-range parameter and a missing `labels`.
- `DiscreteTicks.readout` already covered; add the step-value-from-index inverse next to it.
- No GL.

## Docs (definition of done)

Both release-notes files (one user bullet: stepped parameters get a chooser in the VAL panel), `docs/user_guide/modulation.md` "Stepped parameters" paragraph, `docs/developer/ui.md` DISCRETE bullet, `./gradlew test`.

## Risks

- A preset saved with `mode3D` between steps loads unchanged and snaps when evaluated; the combo shows the nearest entry.
- Removing the `is3DMode` tooltip loses the plane-count detail; keep it in the filter's description if it matters.
