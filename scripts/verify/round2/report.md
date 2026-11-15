# Scripted pass, round 2 (2026-11-06)

Three things ran against `main` at e43cd39 in throwaway worktrees with test-only hooks (`apply_probe.py`): a knob sweep, Library and UI cases, and mass die rolls. Results are in `results/`, and the fixes are on `fixes/app-verify-round2`.

## Knob sweep (`sweep.py`, `sweep2.py`, `sweep_report.py`)

717 parameters of 37 sources, 27 effects and 8 transitions, at 1544 values, with music. Fractal Flame's X1-style transform values and `progress` were left out. Each value moves the parameter through a real macro knob binding, on a freshly reloaded unit. Frames are compared with an untouched render of the same timeline, and two untouched renders matched exactly in all 72 units, so any difference comes from the parameter.

- One binding failure, now fixed: Luminous Flash Intensity.
- Luminous Flash's other inputs showed no effect because the test sat at the exact midpoint, where the flash clips to white. The shader does use them, but they were not re-measured away from the midpoint.
- Works only with another setting (confirmed by retrying): 3D Elevation Separation, Wave Displace centre, Chladni Hue Offset, Icosa H3 SpikePhase/SpikeSharpness/BlockerSize, KIFS Vertex, KIFS Storm Repeat3D/NormalFrequency, Scope RingSize, Tunnel Symmetries. Celestial PulseWave had no effect with music at defaults, and changed frames in a silent retry whatever else was set; the cause was not looked into.
- Seed Density on Cyclic CA and Lenia is only read at a reseed (from the shaders, not measured). Fluid Ink Hue Offset affects new ink only (from the shader and an earlier note).
- Not retried: Fractal Flame Mutation and XF V1-V4 values.
- Faint: Fluid Ink Swirl, Icosa H3 Morph.

Limits: "has an effect" means frames differ, not that it looks good. Only the ends of each range were tried, plus every step of stepped parameters and quarter turns of angles.

## Library and UI cases (`cases2.py`, `ops.py`, `dsl.py`, `fixtures.py`)

188 checks pass on the fixed branch (`results/ops.txt`): selection, queues and undo on all five, tags, missing items, drops, prompts, failed loads, FX chain menus and undo, binding, all nine monitor drag actions, saves. Each case uses a fresh copy of the library at 1280x720.

Bugs found and fixed:
- Every FX chain showed unsaved at launch, fresh install included. Chain files leave out Metaknob links and modulator ids and steps, so they never matched the live chain. Older files can also hold values past today's range.
- Dragging a selection of FX or transitions carried one item.
- Luminous Flash Intensity knob dead (from the sweep).

A deck monitor takes presets and sources only, not FX, as the user guide says. Decided 2026-11-06: keep it, may revisit later.

## Die rolls (`rolls.py`, `results/rolls_report.txt`, `sheets/`)

1010 rolls through the real button: 20 per source, 10 per effect, with music. 118 flagged. Flags come from one frame's brightness and contrast, so they are a place to look. Dynamic Spiral's 13 of 20 are false, it is sparse by design. Noise-only and mostly single-colour rolls (Cyclic CA) are not flagged.

Tuned afterwards in `stock_defaults` and re-rolled (flagged black or white; `baseMax` and `baseMin` only, all still selectable by hand):

| Source | Change | Before | After |
|---|---|---|---|
| Quasicrystal | Color Method max 3 to 2 | 6 of 20 | 3 of 40 (white, palette 1) |
| Wave Ripples | Color Method max 3 to 1 | 8 of 40 on Layout max 1 | 1 of 40 |
| KIFS Storm | Scale min 0.13 to 0.25 | 5 of 20 | 3 of 40 |
| Domain Warp | Gloss max 0.65 to 0.5 | 6 of 20 | 6 of 40, 5 on palette 1 |

Wave Ripples: the first guess (Layout) was wrong. All 10 Style 2 rolls had under 7% of the frame lit, because its glow exponent is high. Style 3 was fine, but a range can't skip Style 2, so it left the rolls too. A shader change could bring both back.

Tried and reverted: Anamorphic Streak threshold and knee (still 15 of 20 white over Quasicrystal). Left alone: Icosa H3, whose flags are false, it is a small object by design.

Effect rolls also roll the Quasicrystal underneath, so its dark rolls inflate their black counts.

## Not done

Simulated Twister, music measurements, browser automation, upgrade and soak runs. Section 2 beyond in-place binding, Metaknob editor, macros, sequencer toolbar.
