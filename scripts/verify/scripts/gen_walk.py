#!/usr/bin/env python3
"""Screen walk: every view, in a given theme. gen_walk.py <scripts dir> <dark|light> <name>"""
import json
import sys

out, theme, name = sys.argv[1:4]
AUDIO = out.rsplit("/", 1)[0] + "/music.wav"
steps, shots = [], []
t = 0.0


def st(action, dt=0.0, **k):
    global t
    t += dt
    d = {"t": round(t, 3), "action": action}
    d.update(k)
    steps.append(d)


def shot(label, wait=1.0):
    """Let the view settle, then record a sample time."""
    global t
    t += wait
    shots.append({"label": label, "t": round(t, 3)})
    t += 0.15


st("theme", target=theme)
st("randomization", value=1)
st("load_source", deck="A", target="quasicrystal", dt=0.05)
st("load_source", deck="B", target="voronoi_cells", dt=0.05)
st("load_source", deck="BG", target="aurora_ribbons", dt=0.05)
st("load_source", deck="PV", target="metaballs", dt=0.05)
st("fx_filter", deck="A", slot=0, target="bloom", dt=0.05)
st("fx_filter", deck="A", slot=1, target="kaleidoscope", dt=0.05)
st("fx_filter", deck="MASTER", slot=0, target="color_levels", dt=0.05)
st("transition", target="film_burn", dt=0.05)
st("move", x=1000, y=20, durationSec=0.1)

for page in ("ab", "bgpv", "mixer"):
    st("set_page", target=page)
    shot("perform_" + page, 1.2)

st("set_page", target="ab")
shot("back_ab", 0.5)

# Slot bound in place
st("move", target="perf_gen_badge_A", durationSec=0.2)
st("click", dt=0.3)
shot("bound_source_A", 0.8)
st("key", target="escape", dt=0.1)
shot("bound_esc", 0.5)
st("move", target="fxcell_deckA_fx_1", durationSec=0.2, dt=0.1)
st("click", dt=0.3)
shot("bound_fx_slot", 0.8)
st("key", target="escape", dt=0.1)
st("move", target="fxchain_name_deckA_fx", durationSec=0.2, dt=0.3)
st("click", dt=0.3)
shot("bound_chain", 0.8)
st("key", target="escape", dt=0.1)
st("move", x=1000, y=20, durationSec=0.1, dt=0.2)

# Menus on the FX row
st("move", target="fxchain_more_deckA_fx", durationSec=0.2, dt=0.3)
st("click", dt=0.3)
shot("fx_chain_menu", 0.6)
st("key", target="escape", dt=0.1)
st("move", target="perf_src_more_A", durationSec=0.2, dt=0.3)
st("click", dt=0.3)
shot("src_menu", 0.6)
st("key", target="escape", dt=0.1)

# Library maximized
st("library_mode", target="FULL", dt=0.2)
shot("library_full_sources", 1.0)
for tab in ("fx", "trans", "queues", "maps"):
    st("move", target="browser_tab_" + tab, durationSec=0.2)
    st("click", dt=0.25)
    shot("library_full_" + tab, 0.8)
st("move", target="browser_tab_presets", durationSec=0.2)
st("click", dt=0.25)
st("move", target="browser_search", durationSec=0.2, dt=0.4)
st("click", dt=0.25)
st("type", target="kifs", dt=0.2)
shot("library_search", 1.2)
st("key", target="escape", dt=0.1)
st("library_mode", target="HALF", dt=0.3)
shot("library_half", 0.8)

# Pair view, Edit
st("focus_pair", target="A", dt=0.1)
shot("pair_A", 1.0)
st("close_focus")
st("focus_pair", target="BG", dt=0.2)
shot("pair_BG", 1.0)
st("close_focus")
for mod in ("deckA", "deckA_fx", "deckB", "deckBG", "deckPV", "master"):
    st("open_params", target=mod, dt=0.2)
    shot("edit_" + mod, 1.0)
st("close_focus", dt=0.1)
# Real gear clicks (1280x720 coordinates of the A/B page)
if name.endswith("720"):
    st("set_page", target="ab", dt=0.2)
    st("move", x=391, y=132, durationSec=0.2, dt=0.3)
    st("click", dt=0.3)
    shot("gear_click_fx_A", 1.0)
    st("move", x=391, y=58, durationSec=0.2)
    st("click", dt=0.3)
    shot("gear_click_src_A", 1.0)
    st("move", x=391, y=58, durationSec=0.2)
    st("click", dt=0.3)
    shot("gear_lit_closes", 1.0)
    st("set_page", target="mixer", dt=0.2)
    st("move", x=391, y=58, durationSec=0.2, dt=0.3)
    st("click", dt=0.3)
    shot("gear_click_trans", 1.0)
    st("close_focus", dt=0.1)

# Preferences, every category
for cat in ("general", "shader_locations", "video_display", "audio_engine", "tempo_sync", "midi_controller", "osc_controller", "shortcuts", "broadcast"):
    st("preferences", target=cat, dt=0.1)
    shot("prefs_" + cat, 0.8)
st("preferences_close", dt=0.1)
shot("end", 0.5)

json.dump({"seed": 7, "bpm": 128.0, "audio": AUDIO, "durationSec": t + 0.5, "steps": steps}, open(f"{out}/{name}.json", "w"), indent=0)
json.dump(shots, open(f"{out}/{name}.shots.json", "w"))
print(name, "shots", len(shots), "dur", round(t, 1))
