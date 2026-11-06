#!/usr/bin/env python3
"""Writes tour scripts for checklist verification into the directory given as argv[1].

generators.json   every source: load fresh on A, output view, hold (sections 12, 13, 17)
rolls.json        every source: die pressed several times, output view after each (section 9c)
trans_<id>.json   one per transition at rest on A then B, then a slow fade; compare frame hashes (section 3)
"""
import json
import os
import sys

OUT = sys.argv[1]
AUDIO = sys.argv[2] if len(sys.argv) > 2 else None
DUR_TAIL = 8.5
os.makedirs(OUT, exist_ok=True)

SOURCES = """mandelbox kifs kifs_storm mandelbulb pseudo_kleinian apollonian escape_time nebula_volume
reaction_diffusion fluid_ink lenia quasicrystal thin_film tunnel_scenes video_feedback hyperbolic_tiling
cyclic_ca metaballs wave_ripples aurora_ribbons isometric_voxels halftone_matrix kali_set girih_stars
glitch_blocks voronoi_cells celestial_engine chladni_cymatics domain_warp_fluid dynamic_spiral
gyroid_hyperspace hyper_slice icosa_h3 mandala""".split()

TRANSITIONS = """linear_crossfade luminous_flash film_burn cyber_datamosh kinetic_zoom
liquid_displacement noise_dissolve vortex_swirl""".split()


def base(steps, seed=7):
    d = {"seed": seed, "bpm": 128.0, "tailSec": 0.5, "steps": steps, "durationSec": max(s["t"] for s in steps) + DUR_TAIL}
    if AUDIO:
        d["audio"] = AUDIO
    return d


def write(name, d):
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(d, f, indent=1)


# --- generators: one fresh load per source, 8 s each. index.json gives the time window of each.
steps = [{"t": 0.0, "action": "crossfade", "value": -1.0}, {"t": 0.1, "action": "output_view", "value": 1}]
index = []
t = 0.5
for s in SOURCES:
    steps.append({"t": t, "action": "load_source", "deck": "A", "target": s})
    index.append({"source": s, "start": t, "end": t + 8.0})
    t += 8.0
write("generators", base(steps))
with open(os.path.join(OUT, "generators.index.json"), "w") as f:
    json.dump(index, f, indent=1)

# --- rolls: load, then 6 presses of the deck die through the real button.
ROLLS = 6
steps = [{"t": 0.0, "action": "crossfade", "value": -1.0}, {"t": 0.05, "action": "randomization", "value": 1}]
index = []
t = 0.5
for s in SOURCES:
    steps.append({"t": t, "action": "output_view", "value": 0})
    steps.append({"t": t + 0.05, "action": "load_source", "deck": "A", "target": s})
    t += 0.6
    for r in range(ROLLS):
        steps.append({"t": t, "action": "output_view", "value": 0})
        steps.append({"t": t + 0.25, "action": "move", "target": "perf_rand_A", "durationSec": 0.1})
        steps.append({"t": t + 0.45, "action": "click"})
        steps.append({"t": t + 0.8, "action": "output_view", "value": 1})
        index.append({"source": s, "roll": r, "sample": t + 3.2})
        t += 3.4
write("rolls", base(steps))
with open(os.path.join(OUT, "rolls.index.json"), "w") as f:
    json.dump(index, f, indent=1)


# --- transitions: same timeline per transition, plus a baseline with none selected.
def trans_steps(tid):
    st = [
        {"t": 0.0, "action": "load_source", "deck": "A", "target": "quasicrystal"},
        {"t": 0.0, "action": "load_source", "deck": "B", "target": "voronoi_cells"},
        {"t": 0.05, "action": "crossfade", "value": -1.0},
        {"t": 0.1, "action": "output_view", "value": 1},
    ]
    if tid:
        st.append({"t": 0.2, "action": "transition", "target": tid})
    st += [
        {"t": 6.0, "action": "crossfade", "value": 1.0},  # rest on B from 6 s
        {"t": 12.0, "action": "crossfade", "value": -1.0},  # back to A
        {"t": 14.0, "action": "crossfade", "value": 1.0, "durationSec": 6.0},  # slow fade A to B
    ]
    return st


for tid in TRANSITIONS:
    write("trans_" + tid, base(trans_steps(tid)))
print("sources", len(SOURCES), "transitions", len(TRANSITIONS))
