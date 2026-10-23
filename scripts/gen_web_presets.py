#!/usr/bin/env python3
"""
gen_web_presets.py - writes the web-* presets and FX chains (desktop .lsd / .lsdfxchain format).

The generated files are the source of truth once committed; this script exists so the modulator
recipes stay consistent and a new preset is a few lines. Run from anywhere:
    python3 scripts/gen_web_presets.py
then check with `node --test web/tools/content.test.mjs web/tools/reactivity.test.mjs`
and `node web/checks/render_check.mjs`.

Modulator semantics (identical on desktop and web, see web/evaluator.js):
  * ADD adds  (cv * depth + dcOffset) * range  to the parameter (range/2 for bipolar parameters).
  * A bipolar source (lfo, beatSine) on a unipolar parameter is first remapped to 0..1, so a
    depth-d lfo sweeps base .. base + d*range. On a bipolar parameter it swings base +/- d*range/2... (cv*depth*range/2).
  * audio_* sources are 0..1. A *gate* is ADD with dcOffset = -threshold*depth: below the
    threshold the amount is negative and the parameter is clamped at its minimum, so a gate only
    works on a parameter whose base IS its minimum (0 = "off": WaveAmp, StellationBoost,
    WireframeMode, PulseWave, a glitch amount...). On any other base the silent value dips below
    base. reactivity.test.mjs enforces this.
  * followerMode CUSTOM + attackMs/decayMs smooths an audio source (fast attack, slow decay = a hit that rings out).
  * lfo with genUnit BEAT: subdivision is in beats (1 = every beat, 4 = bar, 16 = four bars).
    TRIANGLE with slope 0 falls from 1 to 0 over the cycle: an envelope that peaks ON the beat.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "web"

MANDALA_RANGE = {"Lobes": (3, 26)}  # everything else on Mandala is 0..1


def header(path):
    m = re.search(r"/\*\s*(\{.*?\})\s*\*/", Path(path).read_text(), re.S)
    return json.loads(m.group(1))


def ranges(file):
    return {i["NAME"]: (i.get("MIN", 0.0), i.get("MAX", 1.0)) for i in header(file)["INPUTS"] if i["TYPE"] != "image"}


SOURCE_RANGES = {p.stem: ranges(p) for p in (WEB / "shaders").glob("*.frag") if "/*" in p.read_text()}
FILTER_RANGES = {p.stem: ranges(p) for p in (WEB / "shaders" / "fx").glob("*.fs")}


# ---------------------------------------------------------------- modulators
def _mod(source, op, depth, dc=0.0, **extra):
    m = {"sourceId": source, "operator": op, "depth": depth, "dcOffset": dc}
    m.update(extra)
    return m


def lfo(beats, depth, wave="SINE", slope=0.5, phase=0.0, dc=0.0, op="ADD"):
    """Beat-synced LFO with a period of `beats` beats."""
    return _mod("lfo", op, depth, dc, waveform=wave, genUnit="BEAT", subdivision=float(beats), slope=slope, phaseOffset=phase)


def pulse(beats, depth):
    """Envelope that peaks on every `beats`-th beat and decays linearly to the next."""
    return lfo(beats, depth, wave="TRIANGLE", slope=0.0)


def audio(source, depth, attack=None, decay=None, dc=0.0, op="ADD"):
    extra = {}
    if attack is not None or decay is not None:
        extra = {"followerMode": "CUSTOM", "attackMs": float(attack or 0), "decayMs": float(decay or 0)}
    return _mod(source, op, depth, dc, **extra)


def gate(source, threshold, depth, attack=15, decay=250):
    """Silent until `source` passes `threshold` (0..1), then ramps up to depth*(1-threshold)."""
    return audio(source, depth, attack, decay, dc=-threshold * depth)


def ramp(beats, depth):
    """Rises from 0 to depth over `beats` beats, then snaps back: a build-up that restarts every phrase."""
    return lfo(beats, depth, wave="TRIANGLE", slope=1.0)


def bass(depth, attack=20, decay=300): return audio("audio_bass", depth, attack, decay)
def mid(depth, attack=40, decay=300): return audio("audio_mid", depth, attack, decay)
def high(depth, attack=15, decay=140): return audio("audio_high", depth, attack, decay)
def amp(depth, attack=30, decay=400): return audio("audio_amp", depth, attack, decay)
def kick(depth, decay=220): return audio("audio_flux_bass", depth, 4, decay)
def hat(depth, decay=120): return audio("audio_flux_high", depth, 3, decay)


def P(base, lo, hi, *mods):
    return {"baseValue": base, "baseMin": lo, "baseMax": hi, "randomizeBase": False, "modulators": list(mods)}


def isf_params(source, spec):
    rng = SOURCE_RANGES[source]
    out = {}
    for name, v in spec.items():
        mods = []
        if isinstance(v, tuple):
            v, *mods = v
        out[name] = P(v, *rng[name], *mods)
    return out


def mandala_params(spec):
    out = {}
    for name, v in spec.items():
        mods = []
        if isinstance(v, tuple):
            v, *mods = v
        out[name] = P(v, *MANDALA_RANGE.get(name, (0.0, 1.0)), *mods)
    return out


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


# Sources that fill the frame with colour: feedback and bloom chains wash them out to one flat colour,
# so their presets are tagged `fullframe` and the autopilot skips those chains for them (web/pairing.js).
FULL_FRAME_SOURCES = {"cyclic_ca", "kali_set", "glitch_blocks", "halftone_matrix", "quasicrystal", "thin_film",
                      "voronoi_cells", "wave_ripples", "isometric_voxels", "girih_stars",
                      "apollonian", "escape_time", "nebula_volume", "pseudo_kleinian"}


def preset(name, source, spec, tags, notes=""):
    if source in FULL_FRAME_SOURCES:
        tags = tags + ["fullframe"]
    params = mandala_params(spec) if source == "mandala" else isf_params(source, spec)
    write_json(WEB / "presets" / f"{name}.lsd", {
        "version": 1, "name": name, "visualSourceType": source, "parameters": params,
        "feedbackParameters": {}, "tags": ["web"] + tags, "presetNotes": notes,
        "globalAlpha": P(1.0, 0.0, 1.0),
    })


def slot(filter_id, spec, wet=1.0):
    rng = FILTER_RANGES[filter_id]
    params = {}
    for name, v in spec.items():
        mods = []
        if isinstance(v, tuple):
            v, *mods = v
        params[name] = P(v, *rng[name], *mods)
    return {"filterId": filter_id, "enabled": True, "dryWet": P(wet, 0.0, 1.0), "parameters": params}


def chain(name, tags, *slots):
    padded = list(slots) + [None] * (3 - len(slots))
    write_json(WEB / "fxchains" / f"{name}.lsdfxchain", {
        "version": 1, "name": name, "tags": ["web"] + tags, "dryWet": P(1.0, 0.0, 1.0), "slots": padded,
    })


FEEDBACK = lambda decay, gain, zoom=0.0, rotate=0.0, **kw: slot("feedback", {
    "fbDecay": decay, "fbGain": gain, "fbZoom": zoom, "fbRotate": rotate,
    "fbHueShift": kw.get("hue", 0.0), "fbBlur": kw.get("blur", 0.0),
    "fbChroma": kw.get("chroma", 0.0), "fbMode": 0.0, "fbKaleido": 1.0})

# ================================================================ presets
preset("web-beat-spiral", "dynamic_spiral", {
    "MaxPoints": 800, "Scale": (0.42, pulse(1, 0.12)), "Damping": 0.2, "WaveFreq": 0.19,
    "WaveAmp": (0.0, gate("audio_bass", 0.5, 0.9)),
    "Shear": (0.25, lfo(32, 0.25)), "Speed": 0.45,
    "DotSize": (0.3, kick(0.2, 180)), "Glow": (0.5, high(0.25)),
    "HueOffset": (0.0, lfo(64, 1.0)), "HueSweep": 0.09, "TrailDecay": 0.86,
}, ["beat", "kick", "bass-gated", "high"],
    "Spiral thumps on every beat, dots swell on the kick, twists only when the bass is loud, hue turns over 16 bars.")

preset("web-bass-mandala", "mandala", {
    "Lobes": 7, "Recipe Select": (0.3, lfo(16, 0.25, wave="SQUARE")),
    "L1": 0.4, "L2": (0.2, lfo(4, 0.25)), "L3": (0.2, lfo(6, 0.2)), "L4": (0.0, gate("audio_bass", 0.5, 1.0)),
    "Thickness": (0.2, bass(0.35)),
    "Hue Offset": (0.0, lfo(32, 1.0)), "Hue Sweep": 0.5, "Depth": (0.35, mid(0.4)),
}, ["beat", "bar", "bass", "bass-gated", "mid"],
    "A fourth arm appears only on loud bass and the lines thicken with it; the recipe flips every two bars; arms breathe at 4 and 6 beats.")

preset("web-kick-icosa", "icosa_h3", {
    "Morph": (0.1, kick(0.8, 350)), "StellationBoost": (0.0, gate("audio_bass", 0.6, 1.5)),
    "SpikeMode": 0.0, "SpikePhase": (0.0, lfo(16, 1.0)), "SpikeSharpness": 0.6, "BlockerSize": 0.4,
    "HueOffset": 0.0, "HueAnimSpeed": 0.08, "Saturation": 0.85, "Brightness": 0.7, "Opacity": 0.75,
    "EdgeThickness": 0.17, "EdgeBrightness": (0.55, pulse(1, 0.35)), "RimGlow": (0.3, high(0.4)),
    "Zoom": (0.7, kick(0.08, 150)), "RotateX": (0.3, lfo(24, 0.25)), "RotateY": (0.0, lfo(32, 1.0)),
}, ["beat", "bar", "kick", "bass-gated", "high"],
    "Each kick pushes the morph and zoom; edges flash on the beat; stellation spikes erupt on bass drops.")

preset("web-bar-hyperslice", "hyper_slice", {
    "SliceOffset": (0.0, lfo(16, 0.5, wave="TRIANGLE")), "RotateXW": (0.0, lfo(8, 0.6)),
    "RotateYW": (0.3, lfo(12, 0.4)), "RotateZW": 0.0, "Morph": (0.2, mid(0.5)),
    "SupportH": 0.37, "HueOffset": (0.55, lfo(32, 0.4)), "Saturation": 0.85,
    "Brightness": (0.4, pulse(2, 0.12)), "Opacity": 0.6, "EdgeThickness": 0.1,
    "EdgeBrightness": (0.25, pulse(1, 0.15)), "Glow": (0.08, pulse(1, 0.1)), "Zoom": 0.5,
}, ["beat", "bar", "mid"],
    "The slice sweeps through the 4D shape over four bars while the 4D rotations run at 8 and 12 beats.")

preset("web-gyroid-flight", "gyroid_hyperspace", {
    "SurfaceType": 0.0, "WallThickness": (0.3, lfo(8, 0.2)),
    "Frequency": (0.5, lfo(16, 0.15)), "FlightSpeed": 0.25,
    "WireframeMode": 1.0, "CoreGlow": (0.45, kick(0.4)),
    "ColorMode": 0.0, "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 0.9, "Brightness": (0.5, amp(0.25)),
    "Zoom": 0.35, "RotateZ": (0.0, lfo(64, 1.0)),
}, ["beat", "bar", "amp", "kick"],
    "A wireframe lattice you fly through: flight speed follows overall loudness, the core flashes on kicks, the cell size drifts over four bars.")

preset("web-cymatics-sub", "chladni_cymatics", {
    "FrequencyM": (3, bass(0.5, 80, 600)), "FrequencyN": (5, lfo(16, 0.15, wave="SQUARE")),
    "FrequencyL": 2, "PlateShape": (0.0, lfo(32, 0.3)), "NodeSharpness": (0.27, pulse(1, 0.3)),
    "SandAccumulation": 0.7, "VibrationSpeed": 0.15, "InvertMode": 0.0,
    "Glow": (0.35, kick(0.5, 200)), "PaletteMode": 0.0, "HueOffset": (0.0, lfo(24, 1.0)), "Scale": 0.5,
}, ["beat", "bar", "bass", "kick"],
    "More bass pushes the plate into higher modes; the pattern shifts every eight beats; sand glows on kicks.")

preset("web-celestial-pulse", "celestial_engine", {
    "Symmetries": (6, lfo(16, 0.25, wave="SQUARE")), "RingDensity": (0.26, lfo(8, 0.2)),
    "PhaseTwist": (0.25, lfo(16, 0.5)), "MoireStrength": (0.45, high(0.5, 20, 150)),
    "FlowerFold": (0.5, lfo(12, 0.2)), "PulseWave": (0.0, pulse(1, 1.0)),
    "Speed": 0.15, "LineWidth": (0.28, kick(0.35)),
    "Glow": (0.34, bass(0.3)), "ColorMode": 0.0, "HueOffset": (0.0, lfo(40, 1.0)), "HueSweep": 0.25,
    "Scale": (0.5, pulse(2, 0.08)),
}, ["beat", "bar", "bass", "high", "kick"],
    "A pulse wave on every beat, moire shimmer follows the hats, speed follows the bass.")

preset("web-fluid-bassdrop", "domain_warp_fluid", {
    "WarpStrength": (0.0, gate("audio_bass", 0.5, 1.5)), "Swirl": (0.3, lfo(16, 0.5), amp(0.2)),
    "Viscosity": 0.2, "Speed": 0.17, "Detail": 4, "Gloss": (0.45, pulse(1, 0.3), mid(0.2)),
    "PaletteMode": (0.0, lfo(64, 0.5, wave="SQUARE")), "HueOffset": (0.0, lfo(20, 1.0)),
    "HueCycleSpeed": 0.1, "Zoom": (0.5, kick(0.1, 200)),
}, ["beat", "bar", "amp", "kick", "bass-gated", "mid"],
    "Calm marbling until the bass drops, then the warp opens up; gloss glints on the beat; the palette flips every 32 beats.")

preset("web-spiral-storm", "dynamic_spiral", {
    "MaxPoints": 1200, "Scale": (0.3, lfo(8, 0.15)), "Damping": 0.4, "WaveFreq": 0.3,
    "WaveAmp": (0.15, mid(0.3)), "Shear": (0.2, mid(0.4)), "Speed": 0.5,
    "DotSize": (0.2, pulse(0.5, 0.15)), "Glow": 0.5, "HueOffset": (0.3, lfo(16, 1.0)),
    "HueSweep": (0.3, lfo(8, 0.2)), "TrailDecay": 0.88,
}, ["beat", "bar", "mid"],
    "Dense spiral that reverses direction every eight beats; dots tick on every half beat; mids shear it.")

preset("web-mandala-bloom", "mandala", {
    "Lobes": 12, "Recipe Select": (0.6, lfo(8, 0.2, wave="SQUARE")),
    "L1": 0.45, "L2": (0.25, pulse(1, 0.2)), "L3": (0.2, lfo(5, 0.2)), "L4": 0.1,
    "Thickness": (0.35, hat(0.6)), "Hue Offset": (0.15, lfo(16, 1.0)), "Hue Sweep": 0.7,
    "Depth": (0.5, pulse(2, 0.3)),
}, ["beat", "bar", "high"],
    "Twelve-lobed mandala whose arm pulses on the beat and whose line thickens on every hi-hat.")

# ================================================================ FX chains
chain("web-kaleido", ["kaleidoscope", "feedback"],
      slot("kaleidoscope", {"segments": (6, lfo(32, 0.15, wave="SQUARE")), "rotation": (0.0, lfo(16, 1.0)),
                            "zoom": (0.5, pulse(1, 0.1)), "centerX": 0.5, "centerY": 0.5, "originOffset": 0.0}),
      FEEDBACK(0.62, 0.47, 0.004, 0.004))
chain("web-bloom", ["bloom", "streak", "kick"],
      slot("bloom", {"bloomIntensity": (0.2, bass(0.5, 10, 250)), "threshold": 0.45, "blurAmount": 0.35}),
      slot("anamorphic_streak", {"streakIntensity": (0.0, gate("audio_flux_bass", 0.3, 1.0, 4, 250)),
                                 "streakLength": 0.5, "threshold": 0.6}))
chain("web-glitch", ["glitch", "high", "bass-gated"],
      slot("rgb_split", {"amount": (0.0, hat(0.35)), "angle": (0.0, lfo(8, 1.0))}),
      slot("vhs_glitch", {"trackingJitter": (0.0, gate("audio_bass", 0.6, 1.2)), "headSwitching": 0.2,
                          "ycDelay": 0.3, "rfDropouts": (0.1, hat(0.5)), "tapeNoise": 0.2, "mixRatio": 1.0}))
chain("web-punch", ["blur", "pinch", "kick", "beat"],
      slot("radial_blur", {"blurAmount": (0.0, kick(0.7, 220)), "decay": 0.9, "exposure": 0.4}),
      slot("pinch_bulge", {"amount": (0.0, pulse(1, 0.25)), "radius": 0.45}),
      FEEDBACK(0.55, 0.45, 0.003, 0.0))
# Not video_strobe: its rate is a TIME multiplier, so modulating it flickers at random (and a fast strobe on a
# public stream is a photosensitivity risk). The negative flashes once per loud bass hit instead.
chain("web-drop-strobe", ["strobe", "bass-gated", "kick"],
      slot("invert", {"invertIntensity": (0.0, gate("audio_bass", 0.7, 1.0, 5, 120))}),
      slot("color_levels", {"contrast": (0.33, kick(0.2, 150))}))


# ---------------------------------------------------------------- batch 2: second look per source
preset("web-solid-icosa", "icosa_h3", {
    "Morph": (0.35, lfo(32, 0.3)), "StellationBoost": 0.0, "SpikeMode": 1.0,
    "SpikePhase": (0.0, lfo(8, 1.0)), "SpikeSharpness": 0.8, "BlockerSize": 0.5,
    "ColorMode": 2.0, "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 0.9, "Brightness": 0.75, "Opacity": 0.9,
    "EdgeThickness": 0.05, "EdgeBrightness": 0.3, "RimGlow": (0.55, mid(0.4)),
    "Zoom": (0.75, pulse(2, 0.05)), "RotateX": (0.0, lfo(40, 1.0)), "RotateY": (0.0, lfo(56, 1.0)),
}, ["beat", "bar", "mid"],
    "A solid, spiky crystal in banded colour that tumbles slowly; the rim glows with the mids.")

preset("web-hyper-prism", "hyper_slice", {
    "SliceOffset": (0.0, lfo(24, 0.6)), "RotateXW": (0.0, lfo(10, 0.8)), "RotateYW": (0.0, lfo(14, 0.8)),
    "RotateZW": (0.0, lfo(18, 0.5)), "Morph": (0.7, mid(0.3)), "SupportH": 0.5, "ColorMethod": 1.0,
    "HueOffset": (0.0, lfo(32, 1.0)), "Saturation": 0.9, "Brightness": 0.4, "Opacity": 0.4,
    "EdgeThickness": 0.25, "EdgeBrightness": (0.5, hat(0.3)), "Glow": 0.1, "Zoom": (0.55, kick(0.12, 200)),
}, ["beat", "bar", "mid", "kick"],
    "A glassy hypercube-to-sphere with thick edges; hats flicker the edges and kicks punch the zoom.")

preset("web-gyroid-solid", "gyroid_hyperspace", {
    "SurfaceType": 1.0, "WallThickness": (0.3, bass(0.3)), "Frequency": (0.4, lfo(32, 0.1)),
    "FlightSpeed": 0.15, "WireframeMode": 0.0, "CoreGlow": (0.5, kick(0.4)),
    "ColorMode": 1.0, "HueOffset": (0.0, lfo(40, 1.0)), "Saturation": 0.95, "Brightness": (0.7, amp(0.25)),
    "Zoom": 0.65, "RotateX": (0.0, lfo(48, 1.0)), "RotateZ": (0.0, lfo(64, 1.0)),
}, ["beat", "bar", "bass", "amp", "kick"],
    "Thick-walled caves instead of a lattice; the walls swell with the bass and the core glows on kicks.")

preset("web-cymatics-rings", "chladni_cymatics", {
    "FrequencyM": (6, lfo(32, 0.2, wave="SQUARE")), "FrequencyN": (6, mid(0.3, 60, 500)), "FrequencyL": 4,
    "PlateShape": 1.0, "NodeSharpness": (0.5, hat(0.3)), "SandAccumulation": 0.4,
    "VibrationSpeed": 0.1, "InvertMode": 1.0, "Glow": (0.5, kick(0.4, 200)),
    "PaletteMode": 2.0, "HueOffset": (0.0, lfo(30, 1.0)), "Scale": 0.55,
}, ["beat", "bar", "mid", "high", "kick"],
    "A round plate drawn as bright rings on dark; mids change the mode, hats sharpen the nodes.")

preset("web-celestial-bloom", "celestial_engine", {
    "Symmetries": (12, lfo(32, 0.2, wave="SQUARE")), "RingDensity": (0.55, lfo(12, 0.15)),
    "PhaseTwist": (-0.3, lfo(24, 0.5)), "MoireStrength": (0.2, mid(0.5)), "FlowerFold": (0.8, lfo(16, 0.15)),
    "PulseWave": (0.0, gate("audio_bass", 0.5, 1.0)), "Speed": -0.1, "LineWidth": (0.2, amp(0.2)),
    "Glow": (0.5, pulse(1, 0.15)), "ColorMode": 2.0, "HueOffset": (0.0, lfo(36, 1.0)), "HueSweep": 0.6, "Scale": 0.6,
}, ["beat", "bar", "mid", "amp", "bass-gated"],
    "A dense twelve-fold flower turning backwards; pulse rings fire only when the bass is loud.")

preset("web-fluid-lava", "domain_warp_fluid", {
    "WarpStrength": (0.6, bass(0.2)), "Swirl": (-0.4, lfo(24, 0.4)), "Viscosity": 0.7, "Speed": 0.08,
    "Detail": 2, "Gloss": (0.1, pulse(2, 0.1), amp(0.2)), "PaletteMode": 4.0, "HueOffset": (0.0, lfo(48, 1.0)),
    "HueCycleSpeed": 0.05, "Zoom": (0.35, kick(0.06, 250)),
}, ["beat", "bar", "bass", "amp", "kick"],
    "Slow thick marbling in a dark palette; glossy highlights swell every other beat.")

preset("web-spiral-galaxy", "dynamic_spiral", {
    "MaxPoints": 2000, "Scale": (0.2, pulse(1, 0.04)), "Damping": 0.6, "WaveFreq": 0.1,
    "WaveAmp": 0.0, "Shear": (0.5, lfo(48, 0.3)), "Speed": 0.15, "DotSize": (0.32, kick(0.1, 200)),
    "Glow": 0.8, "HueOffset": (0.55, lfo(64, 1.0)), "HueSweep": 0.4, "TrailDecay": 0.92,
}, ["beat", "bar", "kick"],
    "A fine, dense galaxy arm turning slowly; dots swell on each kick.")

preset("web-spiral-sparks", "dynamic_spiral", {
    "MaxPoints": 300, "Scale": (0.55, lfo(8, 0.1)), "Damping": 0.1, "WaveFreq": 0.4,
    "WaveAmp": (0.0, gate("audio_flux_high", 0.4, 1.0, 3, 200)), "Shear": (-0.2, lfo(16, 0.4)),
    "Speed": 0.3, "DotSize": (0.4, hat(0.3)), "Glow": (0.55, amp(0.25)),
    "HueOffset": (0.0, lfo(24, 1.0)), "HueSweep": 0.2, "TrailDecay": 0.6,
}, ["beat", "bar", "amp", "high"],
    "Few big dots with short trails; the wave only ripples through them when the hats are busy.")

preset("web-mandala-lace", "mandala", {
    "Lobes": 18, "Recipe Select": (0.8, lfo(16, 0.2, wave="SQUARE")),
    "L1": 0.5, "L2": (0.3, lfo(6, 0.2)), "L3": (0.25, lfo(10, 0.2)), "L4": (0.15, pulse(1, 0.1)),
    "Thickness": (0.1, mid(0.2)), "Hue Offset": (0.6, lfo(40, 1.0)), "Hue Sweep": 0.3,
    "Depth": (0.7, amp(0.3)),
}, ["beat", "bar", "mid", "amp"],
    "Fine eighteen-lobed lace in a cool palette; depth follows loudness.")

preset("web-mandala-heart", "mandala", {
    "Lobes": 4, "Recipe Select": (0.1, lfo(32, 0.3)),
    "L1": 0.6, "L2": (0.4, kick(0.3, 250)), "L3": 0.3, "L4": (0.0, gate("audio_bass", 0.6, 1.0)),
    "Thickness": (0.45, bass(0.3)), "Hue Offset": (0.0, lfo(24, 1.0)), "Hue Sweep": 0.9,
    "Depth": 0.4,
}, ["beat", "bar", "bass", "kick", "bass-gated"],
    "Bold four-lobed shape with heavy lines; arms thump on kicks and a fourth arm opens on loud bass.")


# ---------------------------------------------------------------- batch 3: energy tiers
# ambient: only slow LFOs (>= 8 beats) and amp/mid followers, no gates or flux; groove: beat + mid + bass;
# hard: kick/hat flux, gates, sub-beat pulses. reactivity.test.mjs enforces the ambient rules.
preset("web-ambient-cymatics", "chladni_cymatics", {
    "FrequencyM": (4, lfo(64, 0.12)), "FrequencyN": (6, lfo(48, 0.12)), "FrequencyL": 3,
    "PlateShape": (0.5, lfo(64, 0.3)), "NodeSharpness": 0.2, "SandAccumulation": 0.6,
    "VibrationSpeed": 0.08, "InvertMode": 0.0, "Glow": (0.35, amp(0.2, 200, 1500)),
    "PaletteMode": 1.0, "HueOffset": (0.0, lfo(96, 1.0)), "Scale": 0.5,
}, ["ambient", "bar", "amp"],
    "A slowly morphing plate in soft colour; modes drift over 8-16 bars and loudness barely moves the glow.")

preset("web-ambient-nebula", "celestial_engine", {
    "Symmetries": 8, "RingDensity": (0.35, lfo(32, 0.15)), "PhaseTwist": (0.15, lfo(48, 0.4)),
    "MoireStrength": (0.3, mid(0.25, 200, 1200)), "FlowerFold": (0.6, lfo(64, 0.2)), "PulseWave": 0.0,
    "Speed": 0.06, "LineWidth": 0.15, "Glow": (0.45, amp(0.15, 200, 1500)),
    "ColorMode": 3.0, "HueOffset": (0.0, lfo(96, 1.0)), "HueSweep": 0.5, "Scale": 0.55,
}, ["ambient", "bar", "mid", "amp"],
    "A slow eight-fold bloom of thin lines; the glow breathes with the overall level, hue turns over 24 bars.")

preset("web-ambient-tides", "domain_warp_fluid", {
    "WarpStrength": (0.45, lfo(32, 0.2)), "Swirl": (0.2, lfo(48, 0.4)), "Viscosity": 0.5, "Speed": 0.05,
    "Detail": 3, "Gloss": (0.2, lfo(24, 0.1), amp(0.15, 200, 1500)), "PaletteMode": 4.0, "HueOffset": (0.0, lfo(96, 1.0)),
    "HueCycleSpeed": 0.03, "Zoom": (0.4, lfo(64, 0.1)),
}, ["ambient", "bar", "amp"],
    "Slow rolling marble; swirl and warp ebb over eight to twelve bars. A good background.")

preset("web-ambient-crystal", "icosa_h3", {
    "Morph": (0.2, lfo(48, 0.3)), "StellationBoost": 0.0, "SpikeMode": 0.0, "SpikePhase": (0.0, lfo(64, 1.0)),
    "SpikeSharpness": 0.5, "BlockerSize": 0.4, "ColorMode": 3.0, "HueOffset": (0.0, lfo(96, 1.0)),
    "Saturation": 0.7, "Brightness": 0.7, "Opacity": 0.6, "EdgeThickness": 0.1, "EdgeBrightness": 0.4,
    "RimGlow": (0.4, amp(0.2, 200, 1500)), "Zoom": 0.7, "RotateX": (0.0, lfo(96, 1.0)), "RotateY": (0.0, lfo(64, 1.0)),
}, ["ambient", "bar", "amp"],
    "A translucent crystal turning over 16 bars in muted colour; the rim glow follows the loudness.")

preset("web-groove-mandala", "mandala", {
    "Lobes": 9, "Recipe Select": (0.45, lfo(16, 0.2, wave="SQUARE")),
    "L1": 0.45, "L2": (0.3, lfo(4, 0.25)), "L3": (0.25, mid(0.3)), "L4": (0.1, pulse(2, 0.15)),
    "Thickness": (0.4, bass(0.3)), "Hue Offset": (0.3, lfo(32, 1.0)), "Hue Sweep": 0.5,
    "Depth": (0.45, pulse(1, 0.2)),
}, ["groove", "beat", "bar", "bass", "mid"],
    "Nine lobes that sway every four beats, thicken with the bass and pulse softly on each beat.")

preset("web-groove-prism", "hyper_slice", {
    "SliceOffset": (0.0, lfo(12, 0.4, wave="TRIANGLE")), "RotateXW": (0.0, lfo(6, 0.6)), "RotateYW": (0.2, lfo(8, 0.4)),
    "RotateZW": 0.0, "Morph": (0.4, bass(0.35)), "SupportH": 0.45, "ColorMethod": 2.0,
    "HueOffset": (0.3, lfo(24, 1.0)), "Saturation": 0.85, "Brightness": (0.4, pulse(1, 0.1)), "Opacity": 0.6,
    "EdgeThickness": 0.1, "EdgeBrightness": (0.25, pulse(1, 0.15)), "Glow": 0.08, "Zoom": (0.5, mid(0.1)),
}, ["groove", "beat", "bar", "bass", "mid"],
    "A 4D shape that sways to the beat; the bass morphs it and the edges tick on every beat.")

preset("web-groove-spiral", "dynamic_spiral", {
    "MaxPoints": 900, "Scale": (0.38, pulse(2, 0.08)), "Damping": 0.3, "WaveFreq": 0.25,
    "WaveAmp": (0.1, mid(0.25)), "Shear": (0.3, lfo(8, 0.4)), "Speed": 0.35,
    "DotSize": (0.3, kick(0.12, 220)), "Glow": 0.5, "HueOffset": (0.1, lfo(24, 1.0)),
    "HueSweep": 0.2, "TrailDecay": 0.84,
}, ["groove", "beat", "bar", "mid", "kick"],
    "A mid-density spiral that shears every eight beats, swells on kicks and ripples with the mids.")

preset("web-hard-cymatics", "chladni_cymatics", {
    "FrequencyM": (5, kick(0.6, 120)), "FrequencyN": (5, lfo(4, 0.25, wave="SQUARE")), "FrequencyL": 2,
    "PlateShape": (0.0, lfo(8, 0.5, wave="SQUARE")), "NodeSharpness": (0.6, hat(0.3)), "SandAccumulation": 0.8,
    "VibrationSpeed": 0.25, "InvertMode": (0.0, gate("audio_bass", 0.6, 1.0, 5, 120)),
    "Glow": (0.4, kick(0.5, 150)), "PaletteMode": 3.0, "HueOffset": (0.0, lfo(8, 1.0)), "Scale": 0.5,
}, ["hard", "beat", "bar", "bass", "kick", "high", "bass-gated"],
    "The plate snaps between shapes every two beats, flashes on kicks, and inverts on loud bass.")

preset("web-hard-celestial", "celestial_engine", {
    "Symmetries": (8, lfo(4, 0.3, wave="SQUARE")), "RingDensity": (0.4, kick(0.3, 150)),
    "PhaseTwist": (0.3, lfo(4, 0.6)), "MoireStrength": (0.4, hat(0.5, 100)),
    "FlowerFold": (0.4, lfo(8, 0.3)), "PulseWave": (0.0, gate("audio_bass", 0.5, 1.2, 5, 120)),
    "Speed": 0.3, "LineWidth": (0.3, kick(0.4, 150)), "Glow": (0.4, pulse(0.5, 0.25)),
    "ColorMode": 1.0, "HueOffset": (0.0, lfo(8, 1.0)), "HueSweep": 0.4, "Scale": (0.5, pulse(1, 0.12)),
}, ["hard", "beat", "bass", "kick", "high", "bass-gated"],
    "Symmetry jumps every two beats, rings and lines kick, shimmer on hats and a pulse wave on bass hits.")

preset("web-hard-mandala", "mandala", {
    "Lobes": (8, lfo(2, 0.25, wave="SQUARE")), "Recipe Select": (0.2, lfo(8, 0.3, wave="SQUARE")),
    "L1": 0.5, "L2": (0.3, kick(0.4, 150)), "L3": (0.3, hat(0.3)), "L4": (0.0, gate("audio_bass", 0.5, 1.0, 5, 120)),
    "Thickness": (0.35, kick(0.4, 150)), "Hue Offset": (0.0, lfo(8, 1.0)), "Hue Sweep": 1.0,
    "Depth": (0.5, pulse(0.5, 0.3)),
}, ["hard", "beat", "bass", "kick", "high", "bass-gated"],
    "Lobe count flips every beat, lines punch on kicks, arms flicker on hats and a fourth arm slams in on bass.")


# ---------------------------------------------------------------- batch 4: one signal is the point
# hat-/mid-/kick-driven presets use mostly that one signal; build presets ramp over a phrase (`ramp`) and
# land a bass-gated drop. reactivity.test.mjs checks `build` (a parameter higher late in the phrase than early).
preset("web-hat-sparkle", "dynamic_spiral", {
    "MaxPoints": 900, "Scale": 0.4, "Damping": 0.25, "WaveFreq": 0.2, "WaveAmp": 0.0, "Shear": 0.2, "Speed": 0.25,
    "DotSize": (0.38, hat(0.25, 140)), "Glow": (0.5, hat(0.4, 160)), "HueOffset": (0.0, hat(0.5, 200)),
    "HueSweep": 0.15, "TrailDecay": 0.8,
}, ["hat", "high"],
    "A calm spiral that does nothing but sparkle: dots grow, glow and the colour jumps with every hi-hat.")

preset("web-hat-icosa", "icosa_h3", {
    "Morph": 0.15, "StellationBoost": 0.0, "SpikeMode": 0.0, "SpikeSharpness": 0.6, "BlockerSize": 0.4,
    "ColorMode": 1.0, "HueOffset": 0.0, "HueAnimSpeed": 0.05, "Saturation": 0.85, "Brightness": 0.6, "Opacity": 0.7,
    "EdgeThickness": (0.1, hat(0.3, 120)), "EdgeBrightness": (0.3, hat(0.6, 120)), "RimGlow": (0.2, hat(0.5, 160)),
    "Zoom": 0.7, "RotateX": (0.0, lfo(96, 1.0)), "RotateY": (0.0, lfo(64, 1.0)),
}, ["hat", "high"],
    "A slowly turning crystal whose edges and rim flare on the hi-hats.")

preset("web-mid-bloom", "celestial_engine", {
    "Symmetries": 8, "RingDensity": (0.3, mid(0.3)), "PhaseTwist": (0.1, mid(0.6, 60, 400)),
    "MoireStrength": (0.2, mid(0.6)), "FlowerFold": (0.35, mid(0.5)), "PulseWave": 0.0, "Speed": 0.12,
    "LineWidth": 0.22, "Glow": (0.35, mid(0.3)), "ColorMode": 2.0, "HueOffset": (0.0, mid(0.4, 80, 600)),
    "HueSweep": 0.4, "Scale": 0.55,
}, ["mid"],
    "A celestial flower that unfolds with the mids (vocals, synths, guitars) and rests when they drop out.")

preset("web-mid-marble", "domain_warp_fluid", {
    "WarpStrength": (0.35, mid(0.4)), "Swirl": (0.0, mid(0.8, 60, 500)), "Viscosity": 0.4, "Speed": 0.1,
    "Detail": 3, "Gloss": (0.3, mid(0.3)), "PaletteMode": 0.0, "HueOffset": 0.0,
    "HueCycleSpeed": 0.0, "Zoom": 0.5,
}, ["mid"],
    "Marbling that swirls and cycles colour only while the mids play.")

preset("web-mid-lace", "chladni_cymatics", {
    "FrequencyM": 4, "FrequencyN": (4, mid(0.6, 80, 500)), "FrequencyL": 2, "PlateShape": (0.2, mid(0.4)),
    "NodeSharpness": 0.35, "SandAccumulation": 0.6, "VibrationSpeed": 0.1, "InvertMode": 0.0,
    "Glow": (0.3, mid(0.5)), "PaletteMode": 2.0, "HueOffset": (0.0, mid(0.3, 80, 800)), "Scale": 0.5,
}, ["mid"],
    "A plate whose pattern gets more complex as the mids get louder.")

preset("web-build-drop-fluid", "domain_warp_fluid", {
    "WarpStrength": (0.0, gate("audio_bass", 0.6, 1.0)), "Swirl": (0.1, ramp(32, 0.7)),
    "Viscosity": 0.3, "Speed": 0.08, "Detail": 3, "Gloss": (0.3, ramp(32, 0.3)),
    "PaletteMode": 4.0, "HueOffset": (0.0, lfo(64, 1.0)), "HueCycleSpeed": 0.05,
    "Zoom": (0.4, kick(0.08, 200)),
}, ["build", "bar", "bass", "kick", "bass-gated"],
    "Calm marble that winds up over eight bars (faster, swirlier, glossier) and gets slammed open by bass hits.")

preset("web-build-drop-mandala", "mandala", {
    "Lobes": 6, "Recipe Select": (0.3, lfo(32, 0.3, wave="SQUARE")),
    "L1": 0.4, "L2": (0.2, ramp(32, 0.3)), "L3": (0.2, ramp(16, 0.3)), "L4": (0.0, gate("audio_bass", 0.6, 1.0)),
    "Thickness": (0.15, ramp(32, 0.4)), "Hue Offset": (0.0, ramp(32, 0.5)), "Hue Sweep": (0.3, ramp(32, 0.6)),
    "Depth": (0.3, ramp(32, 0.4)),
}, ["build", "bar", "bass", "bass-gated"],
    "Thin and quiet at the start of a phrase, thicker and busier every bar; the fourth arm only appears on bass hits.")

preset("web-build-drop-spiral", "dynamic_spiral", {
    "MaxPoints": 700, "Scale": (0.45, ramp(32, -0.15)), "Damping": 0.3, "WaveFreq": 0.2,
    "WaveAmp": (0.0, gate("audio_bass", 0.6, 1.0)), "Shear": (0.1, ramp(32, 0.6)), "Speed": 0.2,
    "DotSize": (0.2, ramp(32, 0.2), kick(0.15, 200)), "Glow": (0.4, ramp(32, 0.3)),
    "HueOffset": (0.0, ramp(32, 0.8)), "HueSweep": 0.15, "TrailDecay": 0.8,
}, ["build", "bar", "bass", "kick", "bass-gated"],
    "A spiral that speeds up, shears and zooms through a phrase; the wave only breaks loose on loud bass.")

preset("web-kick-hyper", "hyper_slice", {
    "SliceOffset": 0.0, "RotateXW": (0.0, lfo(24, 0.5)), "RotateYW": (0.2, lfo(32, 0.4)), "RotateZW": 0.0,
    "Morph": (0.15, kick(0.7, 300)), "SupportH": 0.4, "ColorMethod": 0.0, "HueOffset": (0.5, kick(0.15, 400)),
    "Saturation": 0.85, "Brightness": 0.4, "Opacity": 0.6, "EdgeThickness": 0.1,
    "EdgeBrightness": (0.25, kick(0.5, 200)), "Glow": (0.05, kick(0.15, 200)), "Zoom": (0.45, kick(0.12, 200)),
}, ["kick"],
    "A slowly rotating 4D shape that morphs, flashes and zooms only on kick drums.")

preset("web-kick-rings", "celestial_engine", {
    "Symmetries": 10, "RingDensity": (0.25, kick(0.4, 180)), "PhaseTwist": (0.2, kick(0.4, 250)),
    "MoireStrength": 0.3, "FlowerFold": 0.5, "PulseWave": (0.0, kick(1.0, 250)), "Speed": 0.1,
    "LineWidth": (0.2, kick(0.4, 180)), "Glow": (0.35, kick(0.4, 180)), "ColorMode": 0.0, "HueOffset": 0.0,
    "HueSweep": 0.3, "Scale": (0.5, kick(0.12, 180)),
}, ["kick"],
    "Rings that expand, thicken and glow on every kick and settle back between them.")


# ---------------------------------------------------------------- batch 5: fill the thinnest sources
# (gyroid_hyperspace, icosa_h3, hyper_slice). Gyroid notes: it is a flight, so it is empty ~25% of the time
# whatever the parameters; wireframe at Zoom ~.35 shows structure, Zoom >= .5 is telephoto.
preset("web-gyroid-schwarz", "gyroid_hyperspace", {
    "SurfaceType": 2.0, "WallThickness": (0.35, bass(0.25)), "Frequency": (0.4, lfo(32, 0.15)),
    "FlightSpeed": 0.2, "WireframeMode": 0.0, "CoreGlow": (0.45, kick(0.4)),
    "ColorMode": 2.0, "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 0.95, "Brightness": (0.7, amp(0.25)),
    "Zoom": 0.6, "RotateY": (0.0, lfo(64, 1.0)),
}, ["beat", "bar", "bass", "amp", "kick"],
    "A different cave family (the third surface type) in banded colour; walls swell with bass, the core flashes on kicks.")

preset("web-gyroid-ambient", "gyroid_hyperspace", {
    "SurfaceType": 0.0, "WallThickness": (0.35, lfo(32, 0.15)), "Frequency": (0.45, lfo(64, 0.1)),
    "FlightSpeed": 0.1, "WireframeMode": 1.0, "CoreGlow": (0.4, amp(0.2, 200, 1500)),
    "ColorMode": 1.0, "HueOffset": (0.0, lfo(96, 1.0)), "Saturation": 0.8, "Brightness": 0.45,
    "Zoom": 0.35, "RotateZ": (0.0, lfo(96, 1.0)),
}, ["ambient", "bar", "amp"],
    "A slow drift through a wireframe maze in muted colour; only the overall level nudges speed and glow.")

preset("web-gyroid-kick", "gyroid_hyperspace", {
    "SurfaceType": 1.0, "WallThickness": 0.35, "Frequency": (0.5, lfo(16, 0.1)),
    "FlightSpeed": 0.1, "WireframeMode": 1.0, "CoreGlow": (0.4, kick(0.5, 180)),
    "ColorMode": 0.0, "HueOffset": (0.0, lfo(24, 1.0)), "Saturation": 0.9, "Brightness": 0.5,
    "Zoom": 0.35, "RotateZ": (0.0, lfo(32, 1.0)),
}, ["hard", "beat", "bar", "kick"],
    "The flight lurches forward on every kick and coasts in between; the core glows with each hit.")

preset("web-gyroid-hat", "gyroid_hyperspace", {
    "SurfaceType": 2.0, "WallThickness": 0.3, "Frequency": 0.5, "FlightSpeed": 0.15,
    "WireframeMode": 1.0, "CoreGlow": (0.3, hat(0.5, 140)), "ColorMode": 3.0,
    "HueOffset": (0.0, hat(0.4, 250)), "Saturation": 0.9, "Brightness": (0.4, hat(0.2, 120)),
    "Zoom": 0.4, "RotateX": (0.0, lfo(64, 1.0)),
}, ["hat", "high", "bar"],
    "A steady wireframe flight that glints and shifts colour on the hi-hats.")

preset("web-icosa-spiked", "icosa_h3", {
    "Morph": (0.2, kick(0.6, 250)), "StellationBoost": (0.0, gate("audio_bass", 0.5, 1.5)), "SpikeMode": 1.0,
    "SpikePhase": (0.0, lfo(4, 1.0)), "SpikeSharpness": 0.9, "BlockerSize": 0.45, "ColorMode": 1.0,
    "HueOffset": (0.0, lfo(16, 1.0)), "Saturation": 0.9, "Brightness": 0.65, "Opacity": 0.85,
    "EdgeThickness": 0.08, "EdgeBrightness": (0.3, pulse(0.5, 0.3)), "RimGlow": (0.35, hat(0.3)),
    "Zoom": (0.7, kick(0.08, 150)), "RotateX": (0.0, lfo(16, 1.0)), "RotateY": (0.0, lfo(12, 1.0)),
}, ["hard", "beat", "bar", "kick", "high", "bass-gated"],
    "Needle-sharp spikes that spin on a four-beat cycle; kicks push the morph and bass hits blow up the stellation.")

preset("web-icosa-glass", "icosa_h3", {
    "Morph": (0.3, mid(0.4)), "StellationBoost": 0.0, "SpikeMode": 0.0, "SpikeSharpness": 0.5, "BlockerSize": 0.3,
    "ColorMode": 4.0, "HueOffset": (0.0, lfo(32, 1.0)), "Saturation": 0.8, "Brightness": 0.6, "Opacity": 0.4,
    "EdgeThickness": 0.2, "EdgeBrightness": (0.6, pulse(1, 0.2)), "RimGlow": (0.5, mid(0.3)),
    "Zoom": 0.7, "RotateX": (0.0, lfo(24, 1.0)), "RotateY": (0.0, lfo(32, 1.0)),
}, ["groove", "beat", "bar", "mid"],
    "A see-through crystal with thick bright edges that tick on the beat; the mids morph it and light its rim.")

preset("web-icosa-build", "icosa_h3", {
    "Morph": (0.1, ramp(32, 0.7)), "StellationBoost": (0.0, gate("audio_bass", 0.6, 1.5)), "SpikeMode": 0.0,
    "SpikeSharpness": 0.6, "BlockerSize": 0.4, "ColorMode": 0.0, "HueOffset": (0.0, ramp(32, 0.6)),
    "Saturation": 0.85, "Brightness": 0.65, "Opacity": 0.75, "EdgeThickness": 0.12, "EdgeBrightness": (0.4, ramp(32, 0.4)),
    "RimGlow": (0.2, ramp(32, 0.5)), "Zoom": 0.65, "RotateX": (0.0, lfo(32, 1.0)), "RotateY": (0.0, lfo(24, 1.0)),
}, ["build", "bar", "bass", "bass-gated"],
    "A crystal that morphs, brightens and shifts colour over an eight-bar phrase; bass hits throw spikes out of it.")

preset("web-hyper-ambient", "hyper_slice", {
    "SliceOffset": (0.0, lfo(64, 0.4, wave="TRIANGLE")), "RotateXW": (0.0, lfo(48, 0.5)), "RotateYW": (0.2, lfo(64, 0.4)),
    "RotateZW": 0.0, "Morph": (0.5, amp(0.15, 200, 1500)), "SupportH": 0.5, "ColorMethod": 2.0,
    "HueOffset": (0.4, lfo(96, 1.0)), "Saturation": 0.7, "Brightness": 0.4, "Opacity": 0.55,
    "EdgeThickness": 0.1, "EdgeBrightness": 0.25, "Glow": 0.08, "Zoom": 0.5,
}, ["ambient", "bar", "amp"],
    "A 4D shape sliced slowly over 16 bars in muted colour; only loudness nudges its morph.")

preset("web-hyper-hard", "hyper_slice", {
    "SliceOffset": (0.0, lfo(4, 0.4, wave="SQUARE")), "RotateXW": (0.0, lfo(4, 0.8)), "RotateYW": (0.3, kick(0.6, 150)),
    "RotateZW": 0.0, "Morph": (0.0, gate("audio_bass", 0.5, 1.0, 5, 150)), "SupportH": 0.4, "ColorMethod": 1.0,
    "HueOffset": (0.0, lfo(8, 1.0)), "Saturation": 0.9, "Brightness": 0.4, "Opacity": 0.6,
    "EdgeThickness": 0.12, "EdgeBrightness": (0.25, hat(0.5, 100)), "Glow": (0.05, kick(0.15, 150)), "Zoom": (0.45, kick(0.12, 150)),
}, ["hard", "beat", "bass", "kick", "high", "bass-gated"],
    "The slice flips every four beats, rotation kicks, edges flicker on hats and bass hits morph the whole shape.")

preset("web-hyper-sweep", "hyper_slice", {
    "SliceOffset": (0.0, lfo(8, 0.5, wave="TRIANGLE")), "RotateXW": (0.0, lfo(12, 0.6)), "RotateYW": (0.1, lfo(16, 0.5)),
    "RotateZW": (0.0, lfo(20, 0.4)), "Morph": (0.3, mid(0.4)), "SupportH": 0.45, "ColorMethod": 0.0,
    "HueOffset": (0.2, lfo(24, 1.0)), "Saturation": 0.85, "Brightness": (0.4, pulse(2, 0.1)), "Opacity": 0.6,
    "EdgeThickness": 0.1, "EdgeBrightness": (0.25, pulse(1, 0.15)), "Glow": 0.08, "Zoom": 0.5,
}, ["groove", "beat", "bar", "mid"],
    "A quick two-bar sweep through the 4D shape with all three 4D rotations turning at different speeds.")

# ---------------------------------------------------------------- batch 6: every web source gets presets
# Three roles per source: groove (beat + bass/mid), hard (kick/hat flux, fast square LFOs) and ambient
# (BG playlist: slow LFOs, smoothed amp/mid only). Rate parameters (Speed, FlightSpeed, WaveSpeed, Spin,
# Rate...) are TIME multipliers in the shaders, so they stay static: reactivity.test.mjs enforces that.
SQ = "SQUARE"
AMB = dict(attack=200, decay=1500)

# aurora_ribbons
preset("web-aurora-pulse", "aurora_ribbons", {
    "Ribbons": (4, lfo(32, 0.34, wave=SQ)), "CurtainHeight": (0.5, bass(0.3)), "WaveAmp": (0.4, lfo(8, 0.3)),
    "WaveSpeed": 0.3, "Shimmer": (0.3, hat(0.6)), "SkyDarkness": 0.6, "PaletteMode": 0.0, "SurfaceType": 1.0,
    "HueShift": (0.0, lfo(64, 1.0)), "Saturation": 0.7, "Brightness": (0.5, pulse(1, 0.2)),
}, ["groove", "beat", "bar", "bass", "high"],
    "Curtains that rise with the bass and brighten on every beat; hats make them shimmer; a ribbon joins every eight bars.")
preset("web-aurora-storm", "aurora_ribbons", {
    "Ribbons": 6, "CurtainHeight": (0.4, kick(0.5, 200)), "WaveAmp": (0.5, bass(0.4)), "WaveSpeed": 0.6,
    "Shimmer": (0.5, hat(0.5)), "SkyDarkness": 0.7, "PaletteMode": (2.0, lfo(8, 0.34, wave=SQ)), "SurfaceType": 0.0,
    "HueShift": (0.0, lfo(16, 1.0)), "Saturation": 0.8, "Brightness": (0.45, kick(0.35, 180)),
}, ["hard", "bar", "bass", "kick", "high"],
    "Six curtains that leap on every kick and whip with the bass; the palette flips every two bars.")
preset("web-aurora-ambient", "aurora_ribbons", {
    "Ribbons": 4, "CurtainHeight": (0.55, amp(0.2, **AMB)), "WaveAmp": (0.4, lfo(32, 0.2)), "WaveSpeed": 0.15,
    "Shimmer": (0.3, mid(0.3, 200, 1200)), "SkyDarkness": 0.7, "PaletteMode": 1.0, "SurfaceType": 1.0,
    "HueShift": (0.0, lfo(96, 1.0)), "Saturation": 0.6, "Brightness": 0.5,
}, ["ambient", "bar", "amp", "mid"],
    "Slow northern lights; the curtains lift with the overall level and shimmer with the mids.")

# cyclic_ca: Reseed fires on a rising edge past 0.5, so a deep gate reseeds the grid on big bass hits.
preset("web-ca-reseed", "cyclic_ca", {
    "States": 0.1, "Threshold": 0.4, "Neighborhood": 0.0, "Speed": 0.5,
    "Density": 1.0, "Reseed": (0.0, gate("audio_bass", 0.8, 6.0, 5, 150)), "PaletteMode": (0.0, lfo(8, 0.34, wave=SQ)),
    "HueOffset": (0.0, lfo(32, 1.0)), "Saturation": (0.55, hat(0.3)), "Brightness": (0.5, kick(0.3)),
}, ["hard", "bar", "kick", "high", "bass-gated"],
    "The chemical-wave automaton flashes on kicks and is torn up and reseeded by the biggest bass hits.")
preset("web-ca-groove", "cyclic_ca", {
    "States": 0.1, "Threshold": 0.4, "Neighborhood": 0.0, "Speed": 0.3, "Density": 1.0,
    "Reseed": 0.0, "PaletteMode": (1.0, lfo(32, 0.34, wave=SQ)), "HueOffset": (0.0, lfo(48, 1.0)),
    "Saturation": (0.5, mid(0.4)), "Brightness": (0.45, pulse(1, 0.2)),
}, ["groove", "beat", "bar", "mid"],
    "Spirals of colour that pulse on the beat and saturate with the mids; the palette changes every eight bars.")
preset("web-ca-ambient", "cyclic_ca", {
    "States": 0.1, "Threshold": 0.4, "Neighborhood": 0.0, "Speed": 0.25, "Density": 1.0, "Reseed": 0.0,
    "PaletteMode": 2.0, "HueOffset": (0.0, lfo(96, 1.0)), "Saturation": 0.35, "Brightness": (0.35, amp(0.2, **AMB)),
}, ["ambient", "bar", "amp"],
    "A slow, muted automaton whose brightness follows the overall level.")

# girih_stars
preset("web-girih-groove", "girih_stars", {
    "Symmetries": (6, lfo(32, 0.5, wave=SQ)), "SurfaceType": 0.0, "PaletteMode": 0.0, "TileScale": 0.4,
    "Angle": (0.55, lfo(16, 0.2)), "Sweep": 0.3, "Speed": 0.3, "LineWidth": (0.35, bass(0.3)), "Interlace": 0.8,
    "Glow": (0.35, pulse(1, 0.3)), "HueOffset": (0.0, lfo(64, 1.0)), "Saturation": 0.6, "Brightness": 0.5,
}, ["groove", "beat", "bar", "bass"],
    "Strapwork that glows on every beat and thickens with the bass; the stars switch between six and eight points every eight bars.")
preset("web-girih-hard", "girih_stars", {
    "Symmetries": 8, "SurfaceType": (0.0, lfo(8, 0.5, wave=SQ)), "PaletteMode": 2.0, "TileScale": (0.4, pulse(2, 0.1)),
    "Angle": (0.5, kick(0.3, 200)), "Sweep": 0.3, "Speed": 0.4, "LineWidth": (0.3, hat(0.3)), "Interlace": 0.8,
    "Glow": (0.3, kick(0.5, 150)), "HueOffset": (0.0, lfo(16, 1.0)), "Saturation": 0.7, "Brightness": 0.5,
}, ["hard", "beat", "bar", "kick", "high"],
    "Kicks bend the star angles and flash the glow, hats sharpen the lines, the drawing style flips every four beats.")
preset("web-girih-ambient", "girih_stars", {
    "Symmetries": 8, "SurfaceType": 1.0, "PaletteMode": 1.0, "TileScale": 0.35, "Angle": (0.55, lfo(48, 0.15)),
    "Sweep": (0.3, lfo(64, 0.3)), "Speed": 0.1, "LineWidth": 0.3, "Interlace": 0.8, "Glow": (0.3, mid(0.25, 200, 1200)),
    "HueOffset": (0.6, lfo(96, 1.0)), "Saturation": 0.5, "Brightness": (0.45, amp(0.15, **AMB)),
}, ["ambient", "bar", "mid", "amp"],
    "Slowly re-folding star tiling in soft colour; the glow breathes with the mids.")

# glitch_blocks: busy by nature, so no ambient version.
preset("web-glitch-hard", "glitch_blocks", {
    "SurfaceType": (1.0, lfo(16, 0.34, wave=SQ)), "PaletteMode": 3.0, "Intensity": (0.1, kick(0.7, 200)),
    "BlockSize": (0.4, lfo(4, 0.3, wave=SQ)), "Rate": 0.3, "Tear": (0.0, gate("audio_bass", 0.5, 1.2)),
    "Split": (0.1, hat(0.6)), "Crush": (0.0, gate("audio_flux_bass", 0.4, 1.0, 4, 150)),
    "HueOffset": (0.0, lfo(16, 1.0)), "Saturation": 0.5, "Brightness": 0.42,
}, ["hard", "bar", "kick", "high", "bass-gated"],
    "Test-card bars that shatter on kicks, tear on loud bass and split their channels on hats.")
preset("web-glitch-hats", "glitch_blocks", {
    "SurfaceType": 3.0, "PaletteMode": 3.0, "Intensity": (0.05, hat(0.6, 120)), "BlockSize": 0.35, "Rate": 0.5,
    "Tear": 0.1, "Split": (0.0, hat(0.5, 150)), "Crush": 0.0, "HueOffset": 0.0, "Saturation": (0.5, mid(0.3)),
    "Brightness": 0.45,
}, ["hat", "high", "mid"],
    "A noise field that only glitches on the hi-hats; colour follows the mids.")
preset("web-glitch-testcard", "glitch_blocks", {
    "SurfaceType": 1.0, "PaletteMode": 2.0, "Intensity": (0.15, pulse(1, 0.4)), "BlockSize": 0.45, "Rate": 0.3,
    "Tear": (0.1, pulse(2, 0.3)), "Split": (0.2, bass(0.3)), "Crush": 0.0, "HueOffset": (0.0, lfo(32, 1.0)),
    "Saturation": 0.5, "Brightness": 0.45,
}, ["groove", "beat", "bar", "bass"],
    "Glitches land on the beat and tear every other beat; the bass pulls the colour channels apart.")

# halftone_matrix
preset("web-halftone-groove", "halftone_matrix", {
    "SurfaceType": 1.0, "ColorMethod": 1.0, "PaletteMode": 1.0, "Density": (0.4, lfo(16, 0.2)),
    "Sharpness": (0.6, pulse(1, 0.3)), "PatternSpeed": 0.3, "PatternScale": (0.4, bass(0.3)),
    "GridRotation": (0.1, lfo(32, 0.3)), "Contrast": 0.5, "HueShift": (0.0, lfo(48, 1.0)), "Saturation": 0.7, "Brightness": 0.5,
}, ["groove", "beat", "bar", "bass"],
    "LED-matrix radial pulses that snap sharp on the beat and swell with the bass; the grid turns over eight bars.")
preset("web-halftone-hard", "halftone_matrix", {
    "SurfaceType": 0.0, "ColorMethod": (2.0, lfo(8, 0.34, wave=SQ)), "PaletteMode": 2.0, "Density": (0.5, kick(0.2, 200)),
    "Sharpness": 0.6, "PatternSpeed": 0.4, "PatternScale": (0.4, pulse(0.5, 0.15)), "GridRotation": 0.1,
    "Contrast": (0.45, kick(0.25, 180)), "HueShift": (0.0, lfo(8, 1.0)), "Saturation": 0.75, "Brightness": (0.42, hat(0.15)),
}, ["hard", "beat", "bar", "kick", "high"],
    "Plasma dots that punch on kicks, tick every half beat and switch dot style every four beats.")
preset("web-halftone-ambient", "halftone_matrix", {
    "SurfaceType": 3.0, "ColorMethod": 0.0, "PaletteMode": 1.0, "Density": 0.55, "Sharpness": (0.4, mid(0.2, 200, 1200)),
    "PatternSpeed": 0.1, "PatternScale": 0.4, "GridRotation": (0.1, lfo(64, 0.3)), "Contrast": (0.4, amp(0.2, **AMB)),
    "HueShift": (0.0, lfo(96, 1.0)), "Saturation": 0.6, "Brightness": 0.5,
}, ["ambient", "bar", "mid", "amp"],
    "Soft halftone clouds that gain contrast with the overall level.")

# harmonograph: a thin line by default, so line width, glow and brightness are raised.
preset("web-harmonograph-groove", "harmonograph", {
    "RatioPreset": (2, lfo(32, 0.15, wave=SQ)), "PaletteMode": 0.0, "Detune": (0.25, lfo(16, 0.2)), "DriftSpeed": 0.3,
    "Damping": 0.3, "LineWidth": (0.4, bass(0.3)), "Glow": (0.7, pulse(1, 0.3)), "Trail": 0.7, "Rotation": 0.0,
    "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 1.0, "Brightness": 0.7,
}, ["groove", "beat", "bar", "bass"],
    "A glowing Lissajous rosette that flares on every beat, thickens with the bass and changes ratio every eight bars.")
preset("web-harmonograph-hard", "harmonograph", {
    "RatioPreset": (3, lfo(8, 0.15, wave=SQ)), "PaletteMode": 2.0, "Detune": (0.2, bass(0.3)), "DriftSpeed": 0.4,
    "Damping": (0.3, kick(0.4, 200)), "LineWidth": (0.5, kick(0.3, 150)), "Glow": (0.7, hat(0.3)), "Trail": 0.6,
    "Rotation": (0.0, lfo(16, 0.5)), "HueOffset": (0.0, lfo(16, 1.0)), "Saturation": 1.0, "Brightness": 0.75,
}, ["hard", "bar", "bass", "kick", "high"],
    "Kicks wind the spiral in and thicken the line, the bass detunes it, the ratio jumps every two bars.")
preset("web-harmonograph-ambient", "harmonograph", {
    "RatioPreset": 4, "PaletteMode": 1.0, "Detune": (0.1, lfo(48, 0.2)), "DriftSpeed": 0.15,
    "Damping": (0.4, lfo(64, 0.3)), "LineWidth": 0.35, "Glow": (0.55, amp(0.1, **AMB)), "Trail": 0.9, "Rotation": 0.0,
    "HueOffset": (0.0, lfo(96, 1.0)), "Saturation": 0.8, "Brightness": 0.6,
}, ["ambient", "bar", "amp"],
    "A long-trailed phosphor figure drifting slowly; its glow follows the overall level.")

# hyperbolic_tiling
preset("web-hyperbolic-groove", "hyperbolic_tiling", {
    "P": (5, lfo(32, 0.15, wave=SQ)), "Q": 4, "Speed": 0.3, "Drift": 0.5, "Spin": 0.25,
    "EdgeWidth": (0.35, bass(0.3)), "Glow": (0.5, pulse(1, 0.3)), "Subdivision": (0.25, mid(0.4)), "ColorMode": 0.0,
    "Outside": 0.0, "HueOffset": (0.58, lfo(48, 1.0)), "Saturation": 0.75, "Brightness": 0.5,
}, ["groove", "beat", "bar", "bass", "mid"],
    "An Escher disk that glows on every beat; bass thickens the edges, mids draw the subdivisions.")
preset("web-hyperbolic-hard", "hyperbolic_tiling", {
    "P": 5, "Q": (4, lfo(8, 0.15, wave=SQ)), "Speed": 0.4, "Drift": (0.5, bass(0.3)), "Spin": 0.3,
    "EdgeWidth": (0.3, kick(0.4, 150)), "Glow": (0.5, kick(0.4, 200)), "Subdivision": (0.2, hat(0.5)), "ColorMode": 2.0,
    "Outside": 1.0, "HueOffset": (0.0, lfo(16, 1.0)), "Saturation": 0.8, "Brightness": 0.5,
}, ["hard", "bar", "bass", "kick", "high"],
    "Edges and glow punch on kicks, hats flicker the subdivisions, the tiling changes vertex count every two bars.")
preset("web-hyperbolic-ambient", "hyperbolic_tiling", {
    "P": 7, "Q": 3, "Speed": 0.1, "Drift": (0.4, lfo(64, 0.3)), "Spin": 0.05, "EdgeWidth": 0.3,
    "Glow": (0.45, amp(0.2, **AMB)), "Subdivision": (0.3, lfo(48, 0.3)), "ColorMode": 1.0, "Outside": 0.0,
    "HueOffset": (0.58, lfo(96, 1.0)), "Saturation": 0.6, "Brightness": 0.5,
}, ["ambient", "bar", "amp"],
    "A slow-drifting heptagon tiling whose glow follows the overall level.")

# isometric_voxels
preset("web-voxels-groove", "isometric_voxels", {
    "SurfaceType": (0.0, lfo(32, 0.5, wave=SQ)), "CellSize": 0.45, "MaxHeight": (0.4, bass(0.5)), "Speed": 0.3,
    "Frequency": (0.5, lfo(16, 0.2)), "Gap": 0.25, "Contrast": (0.6, pulse(1, 0.2)), "PaletteMode": 0.0,
    "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 0.85, "Brightness": 0.5,
}, ["groove", "beat", "bar", "bass"],
    "A cube field whose columns rise with the bass and whose faces flash on every beat.")
preset("web-voxels-hard", "isometric_voxels", {
    "SurfaceType": 2.0, "CellSize": (0.45, lfo(8, 0.2, wave=SQ)), "MaxHeight": (0.3, kick(0.45, 250)), "Speed": 0.4,
    "Frequency": 0.5, "Gap": (0.25, hat(0.4)), "Contrast": 0.6, "PaletteMode": 2.0, "HueOffset": (0.0, lfo(8, 1.0)),
    "Saturation": 0.9, "Brightness": (0.5, kick(0.2, 150)),
}, ["hard", "bar", "kick", "high"],
    "Columns slam up on every kick, edges flicker on hats and the cubes change size every four beats.")
preset("web-voxels-ambient", "isometric_voxels", {
    "SurfaceType": 1.0, "CellSize": 0.45, "MaxHeight": (0.5, amp(0.2, **AMB)), "Speed": 0.1,
    "Frequency": (0.4, lfo(64, 0.2)), "Gap": 0.25, "Contrast": 0.6, "PaletteMode": 1.0,
    "HueOffset": (0.0, lfo(96, 1.0)), "Saturation": 0.6, "Brightness": 0.5,
}, ["ambient", "bar", "amp"],
    "Slow rolling cube landscape; the hills grow with the overall level.")

# kali_set
preset("web-kali-groove", "kali_set", {
    "OrbitRadius": (0.7, bass(0.15)), "Speed": 0.3, "Iterations": 0.6, "Twist": (0.0, lfo(16, 0.3)), "SurfaceType": 3.0,
    "TrapScale": (0.5, pulse(1, 0.2)), "Zoom": (0.6, lfo(32, 0.15)), "Drift": 0.3, "ColorMode": 0.0, "PaletteMode": 0.0,
    "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 0.8, "Brightness": 0.5,
}, ["groove", "beat", "bar", "bass"],
    "A fold fractal that reshapes with the bass, its traps pulse on the beat and it twists over four bars.")
preset("web-kali-hard", "kali_set", {
    "OrbitRadius": (0.65, kick(0.2, 200)), "Speed": 0.4, "Iterations": (0.6, kick(0.2, 150)), "Twist": 0.1,
    "SurfaceType": (2.0, lfo(8, 0.34, wave=SQ)), "TrapScale": (0.5, hat(0.3)), "Zoom": (0.4, pulse(1, 0.1)), "Drift": 0.3,
    "ColorMode": 0.0, "PaletteMode": 3.0, "HueOffset": (0.0, lfo(8, 1.0)), "Saturation": 0.85,
    "Brightness": (0.45, kick(0.15)),
}, ["hard", "beat", "bar", "kick", "high"],
    "Every kick throws the fractal into a new shape; trap type flips every four beats; hats scale the traps.")
preset("web-kali-ambient", "kali_set", {
    "OrbitRadius": (0.75, lfo(64, 0.15)), "Speed": 0.1, "Iterations": 0.6, "Twist": (0.0, lfo(96, 0.2)), "SurfaceType": 3.0,
    "TrapScale": 0.5, "Zoom": 0.35, "Drift": 0.3, "ColorMode": 0.0, "PaletteMode": 3.0, "HueOffset": (0.0, lfo(96, 1.0)),
    "Saturation": 0.6, "Brightness": (0.4, amp(0.2, **AMB)),
}, ["ambient", "bar", "amp"],
    "A muted fractal slowly unfolding; brightness follows the overall level.")

# metaballs
preset("web-metaballs-groove", "metaballs", {
    "MaxPoints": (7, lfo(32, 0.25, wave=SQ)), "ColorMethod": 0.0, "PaletteMode": 0.0, "BallSize": (0.55, bass(0.4)),
    "SizeVariance": 0.5, "OrbitSpeed": 0.4, "OrbitSpread": (0.6, lfo(16, 0.2)), "Merge": (0.6, pulse(1, 0.2)),
    "Softness": 0.3, "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 0.85, "Brightness": 0.6,
}, ["groove", "beat", "bar", "bass"],
    "Blobs that swell with the bass and merge on every beat; two more join every eight bars.")
preset("web-metaballs-hard", "metaballs", {
    "MaxPoints": 10, "ColorMethod": (2.0, lfo(8, 0.34, wave=SQ)), "PaletteMode": 2.0, "BallSize": (0.45, kick(0.5, 200)),
    "SizeVariance": (0.5, hat(0.4)), "OrbitSpeed": 0.5, "OrbitSpread": 0.6, "Merge": 0.6, "Softness": (0.3, kick(0.3)),
    "HueOffset": (0.0, lfo(8, 1.0)), "Saturation": 0.9, "Brightness": 0.6,
}, ["hard", "bar", "kick", "high"],
    "Neon blobs that bulge on every kick and jitter in size with the hats; the style flips every four beats.")
preset("web-metaballs-ambient", "metaballs", {
    "MaxPoints": 6, "ColorMethod": 3.0, "PaletteMode": 1.0, "BallSize": (0.6, amp(0.2, **AMB)), "SizeVariance": 0.5,
    "OrbitSpeed": 0.15, "OrbitSpread": 0.6, "Merge": (0.6, lfo(48, 0.2)), "Softness": 0.5,
    "HueOffset": (0.0, lfo(96, 1.0)), "Saturation": 0.75, "Brightness": 0.55,
}, ["ambient", "bar", "amp"],
    "A slow two-tone goo; blobs grow with the overall level.")

# quasicrystal
preset("web-quasi-groove", "quasicrystal", {
    "Symmetries": (7, lfo(32, 0.34, wave=SQ)), "ColorMethod": 1.0, "PaletteMode": 0.0, "Frequency": (0.35, bass(0.2)),
    "Bands": (0.4, pulse(1, 0.2)), "Sharpness": 0.6, "Rotation": (0.0, lfo(32, 0.5)), "SpinSpeed": 0.1,
    "DriftSpeed": 0.25, "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 0.85, "Brightness": 0.5,
}, ["groove", "beat", "bar", "bass"],
    "Penrose-like interference that pulses its bands on the beat and tightens with the bass.")
preset("web-quasi-hard", "quasicrystal", {
    "Symmetries": (5, lfo(4, 0.67, wave=SQ)), "ColorMethod": 2.0, "PaletteMode": 2.0, "Frequency": (0.35, kick(0.15, 200)),
    "Bands": (0.4, hat(0.4)), "Sharpness": (0.5, kick(0.5, 150)), "Rotation": 0.0, "SpinSpeed": 0.15, "DriftSpeed": 0.3,
    "HueOffset": (0.0, lfo(8, 1.0)), "Saturation": 0.9, "Brightness": 0.5,
}, ["hard", "beat", "bar", "kick", "high"],
    "Five-fold and seven-fold symmetry swap every two beats; kicks sharpen the pattern, hats split the bands.")
preset("web-quasi-ambient", "quasicrystal", {
    "Symmetries": 8, "ColorMethod": 1.0, "PaletteMode": 1.0, "Frequency": (0.3, lfo(64, 0.1)),
    "Bands": (0.35, mid(0.2, 200, 1200)), "Sharpness": 0.4, "Rotation": (0.0, lfo(96, 0.5)), "SpinSpeed": 0.03,
    "DriftSpeed": 0.1, "HueOffset": (0.0, lfo(96, 1.0)), "Saturation": 0.7, "Brightness": (0.45, amp(0.15, **AMB)),
}, ["ambient", "bar", "mid", "amp"],
    "Eight-fold interference turning slowly; the bands follow the mids.")

# thin_film
preset("web-film-groove", "thin_film", {
    "FlowSpeed": 0.3, "Scale": 0.3, "FilmThickness": (0.35, bass(0.3)), "ThicknessRange": 0.5, "Contrast": 0.4,
    "Turbulence": (0.4, lfo(16, 0.2)), "Sheen": (0.5, pulse(1, 0.3)), "HueShift": (0.0, lfo(48, 1.0)),
    "Saturation": 0.75, "Brightness": 0.5,
}, ["groove", "beat", "bar", "bass"],
    "An oil slick whose colours shift with the bass and whose sheen flashes on every beat.")
preset("web-film-mid", "thin_film", {
    "FlowSpeed": 0.25, "Scale": 0.35, "FilmThickness": 0.35, "ThicknessRange": (0.4, mid(0.5)), "Contrast": 0.45,
    "Turbulence": (0.3, mid(0.4)), "Sheen": (0.4, mid(0.3)), "HueShift": (0.0, mid(0.3, 80, 800)),
    "Saturation": 0.75, "Brightness": 0.5,
}, ["mid"],
    "Iridescence that churns and changes colour with the mids (vocals, synths) and calms when they stop.")
preset("web-film-ambient", "thin_film", {
    "FlowSpeed": 0.12, "Scale": 0.4, "FilmThickness": (0.35, lfo(64, 0.2)), "ThicknessRange": 0.5, "Contrast": 0.4,
    "Turbulence": 0.35, "Sheen": (0.4, amp(0.2, **AMB)), "HueShift": (0.0, lfo(96, 1.0)), "Saturation": 0.6,
    "Brightness": 0.5,
}, ["ambient", "bar", "amp"],
    "A slow oil-slick drift; the sheen follows the overall level.")

# tunnel_scenes: Speed is a rate, so the music drives glow, line width, twist and sway instead.
preset("web-tunnel-synthwave", "tunnel_scenes", {
    "SurfaceType": 0.0, "Symmetries": 6, "Speed": 0.4, "Twist": 0.0, "Glow": (0.5, pulse(1, 0.35)), "Fog": (0.4, lfo(32, 0.2)),
    "LineWidth": (0.35, bass(0.3)), "Sway": (0.4, lfo(16, 0.3)), "HueOffset": (0.0, lfo(64, 1.0)), "Saturation": 0.85,
    "Brightness": 0.5,
}, ["groove", "beat", "bar", "bass"],
    "A synthwave grid drive: the grid glows on every beat and its lines fatten with the bass.")
preset("web-tunnel-truchet", "tunnel_scenes", {
    "SurfaceType": 1.0, "Symmetries": 6, "Speed": 0.6, "Twist": (0.15, kick(0.3, 300)), "Glow": (0.4, kick(0.5, 180)),
    "Fog": 0.4, "LineWidth": (0.3, hat(0.4)), "Sway": 0.4, "HueOffset": (0.0, lfo(8, 1.0)), "Saturation": 0.85,
    "Brightness": 0.5,
}, ["hard", "bar", "kick", "high"],
    "A Truchet tunnel that twists and flashes on every kick; hats sharpen the lines.")
preset("web-tunnel-wormhole", "tunnel_scenes", {
    "SurfaceType": 2.0, "Symmetries": (6, lfo(16, 0.12, wave=SQ)), "Speed": 0.35, "Twist": (0.2, lfo(32, 0.4)),
    "Glow": (0.45, mid(0.4)), "Fog": (0.4, amp(0.2)), "LineWidth": 0.35, "Sway": 0.4, "HueOffset": (0.0, lfo(48, 1.0)),
    "Saturation": 0.85, "Brightness": 0.5,
}, ["groove", "bar", "mid", "amp"],
    "A polygon wormhole that gains a side every four bars; mids light it up and loudness clears the fog.")

# video_feedback: Zoom/Rotation/HueShift are per-second rates scaled by TIMEDELTA (not TIME), so they are safe to modulate.
preset("web-feedback-groove", "video_feedback", {
    "Zoom": (0.45, kick(0.2, 250)), "Rotation": (0.35, lfo(16, 0.3)), "HueShift": (0.2, mid(0.2)), "Decay": 0.15,
    "SeedShape": (0.0, lfo(32, 0.34, wave=SQ)), "SeedSize": (0.4, bass(0.3)), "Drift": 0.3, "Saturation": 0.5,
    "Brightness": 0.5,
}, ["groove", "bar", "kick", "bass", "mid"],
    "An endless feedback tunnel that lunges on kicks; the seed swells with the bass and the hue turns with the mids.")
preset("web-feedback-hard", "video_feedback", {
    "Zoom": (0.5, kick(0.4, 200)), "Rotation": (0.3, lfo(4, 0.5, wave=SQ)), "HueShift": 0.4, "Decay": (0.15, hat(0.3)),
    "SeedShape": 2.0, "SeedSize": (0.4, kick(0.3)), "Drift": 0.3, "Saturation": 0.6, "Brightness": 0.55,
}, ["hard", "bar", "kick", "high"],
    "The spiral reverses every two beats and dives on every kick; hats cut the trails short.")
preset("web-feedback-ambient", "video_feedback", {
    "Zoom": 0.3, "Rotation": (0.2, lfo(64, 0.3)), "HueShift": (0.1, amp(0.15, **AMB)), "Decay": 0.1, "SeedShape": 0.0,
    "SeedSize": 0.35, "Drift": (0.3, lfo(48, 0.2)), "Saturation": 0.45, "Brightness": 0.5,
}, ["ambient", "bar", "amp"],
    "A slow feedback spiral; the colour drifts faster when the music is louder.")

# voronoi_cells
preset("web-voronoi-groove", "voronoi_cells", {
    "ColorMethod": 1.0, "SurfaceType": 0.0, "PaletteMode": 0.0, "CellScale": (0.35, lfo(16, 0.15)),
    "Jitter": (0.8, mid(-0.3)), "Speed": 0.3, "EdgeWidth": (0.25, bass(0.15)), "EdgeGlow": (0.4, pulse(1, 0.25)),
    "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 0.75, "Brightness": 0.5,
}, ["groove", "beat", "bar", "bass", "mid"],
    "Glowing cells whose walls light up on every beat and thicken with the bass; mids straighten the grid.")
preset("web-voronoi-hard", "voronoi_cells", {
    "ColorMethod": 2.0, "SurfaceType": (0.0, lfo(8, 0.5, wave=SQ)), "PaletteMode": 2.0, "CellScale": (0.4, kick(0.15, 200)),
    "Jitter": 0.8, "Speed": 0.4, "EdgeWidth": (0.25, hat(0.4)), "EdgeGlow": (0.4, kick(0.5, 150)),
    "HueOffset": (0.0, lfo(8, 1.0)), "Saturation": 0.8, "Brightness": 0.5,
}, ["hard", "bar", "kick", "high"],
    "Cells that flash and zoom on kicks; the distance metric flips every four beats.")
preset("web-voronoi-ambient", "voronoi_cells", {
    "ColorMethod": 0.0, "SurfaceType": 0.0, "PaletteMode": 1.0, "CellScale": 0.25, "Jitter": (0.7, lfo(64, 0.2)),
    "Speed": 0.1, "EdgeWidth": 0.3, "EdgeGlow": (0.3, amp(0.2, **AMB)), "HueOffset": (0.0, lfo(96, 1.0)),
    "Saturation": 0.55, "Brightness": 0.5,
}, ["ambient", "bar", "amp"],
    "Large soft cells slowly reshuffling; the walls glow with the overall level.")

# wave_ripples
preset("web-ripples-groove", "wave_ripples", {
    "Symmetries": (4, lfo(32, 0.34, wave=SQ)), "Wavelength": (0.4, bass(0.3)), "WaveSpeed": 0.3, "OrbitSpeed": 0.25,
    "Falloff": (0.35, lfo(16, 0.2)), "SurfaceType": 0.0, "ColorMethod": 0.0, "PaletteMode": 0.0,
    "Contrast": (0.5, pulse(1, 0.3)), "HueShift": (0.0, lfo(48, 1.0)), "Saturation": 0.75, "Brightness": 0.38,
}, ["groove", "beat", "bar", "bass"],
    "Interfering ripples that sharpen on every beat and stretch with the bass; two more sources join every eight bars.")
preset("web-ripples-kick", "wave_ripples", {
    "Symmetries": 6, "Wavelength": (0.35, kick(0.4, 250)), "WaveSpeed": 0.35, "OrbitSpeed": 0.25, "Falloff": 0.3,
    "SurfaceType": 1.0, "ColorMethod": 1.0, "PaletteMode": 2.0, "Contrast": (0.5, kick(0.4, 180)),
    "HueShift": (0.0, kick(0.15, 400)), "Saturation": 0.8, "Brightness": 0.5,
}, ["kick"],
    "Ripples that only react to kick drums: the waves stretch, sharpen and change colour on every hit.")
preset("web-ripples-ambient", "wave_ripples", {
    "Symmetries": 3, "Wavelength": (0.5, lfo(64, 0.15)), "WaveSpeed": 0.12, "OrbitSpeed": 0.05, "Falloff": 0.5,
    "SurfaceType": 0.0, "ColorMethod": 0.0, "PaletteMode": 1.0, "Contrast": (0.4, amp(0.2, **AMB)),
    "HueShift": (0.0, lfo(96, 1.0)), "Saturation": 0.6, "Brightness": 0.5,
}, ["ambient", "bar", "amp"],
    "Three slow ripple sources; contrast follows the overall level.")


# trails: feedback that moves with the music (these were static before the rotation gate)
chain("web-trails", ["feedback", "kick", "bar"],
      FEEDBACK(0.6316, 0.47, (0.002, kick(0.015, 250)), (-0.003, lfo(16, 0.004)), blur=0.2))
chain("web-trails-drift", ["feedback", "bar", "mid"],
      FEEDBACK(0.658, 0.48, 0.005, (0.008, lfo(32, 0.004)), hue=(0.001, mid(0.01))))
chain("web-trails-chroma", ["feedback", "chroma", "high", "beat"],
      FEEDBACK(0.6893, 0.485, (0.006, pulse(1, 0.004)), 0.004, hue=0.002, chroma=(0.3, hat(0.4))))

# batch 2 chains
chain("web-tunnel", ["tunnel", "beat", "bass"],
      slot("polar_tunnel", {"depth": (0.18, bass(0.3)), "twist": (0.0, lfo(32, 0.5)), "centerX": 0.5, "centerY": 0.5,
                            "zoom": (0.5, pulse(1, 0.06)), "symmetry": 2.0, "depthFog": 0.4}))
chain("web-vortex", ["swirl", "kick", "beat"],
      slot("vortex_swirl", {"twist": (0.1, kick(0.5, 300)), "radius": (0.35, pulse(1, 0.15)), "dispersion": (0.2, hat(0.4)),
                            "spiralArms": 0.0, "centerX": 0.5, "centerY": 0.5}),
      FEEDBACK(0.5, 0.4, 0.002, 0.003))

# batch 4 chain
chain("web-build-drop", ["build", "bar", "bass-gated"],
      slot("bloom", {"bloomIntensity": (0.1, ramp(32, 0.5)), "threshold": 0.5, "blurAmount": 0.35}),
      slot("rgb_split", {"amount": (0.0, gate("audio_bass", 0.6, 0.6, 5, 150)), "angle": (0.0, lfo(16, 1.0))}))

# ---------------------------------------------------------------- batch 8: the heavy sources
# Raymarched fractals (LSD_COST 3; adaptive render resolution keeps them fluid) and the three simulations.
# FlightSpeed / MorphSpeed are phase-integrated (LSD_PHASES), so kicks can push the flight forward.
# Lenia and reaction-diffusion only move their look (relief, colour, zoom): growth parameters kill the life.
preset("web-apollonian-groove", "apollonian", {
    "InversionStrength": (0.6, bass(0.2)), "Twist": (0.0, lfo(16, 0.3)), "CoreSize": (0.4, pulse(1, 0.15)),
    "FlightSpeed": (0.25, amp(0.3)), "HueOffset": (0.0, lfo(48, 1.0)), "Brightness": (0.6, kick(0.15)),
}, ["groove", "beat", "bar", "bass", "amp", "kick"],
    "Flying through a sphere gasket: the bass bends the inversion, the core pulses on the beat, louder music flies faster.")
preset("web-apollonian-hard", "apollonian", {
    "InversionRadius": (0.57, kick(0.15, 200)), "Twist": (0.0, lfo(4, 0.5, wave=SQ)), "FlightSpeed": (0.3, kick(0.5, 300)),
    "ColorMode": (0.0, lfo(8, 0.34, wave=SQ)), "Brightness": (0.6, hat(0.15)), "HueOffset": (0.0, lfo(16, 1.0)),
}, ["hard", "bar", "kick", "high"],
    "Every kick lunges the flight forward and warps the gasket; the twist flips every two beats.")
preset("web-apollonian-ambient", "apollonian", {
    "Twist": (0.0, lfo(64, 0.3)), "FlightSpeed": (0.15, amp(0.1, **AMB)), "RotateZ": (0.0, lfo(96, 1.0)),
    "HueOffset": (0.0, lfo(96, 1.0)), "Saturation": 0.55, "Brightness": (0.55, amp(0.1, **AMB)),
}, ["ambient", "bar", "amp"],
    "A slow drift through the gasket, turning over 24 bars.")

preset("web-julia-groove", "escape_time", {
    "JuliaRadius": (0.65, bass(0.08)), "JuliaAngle": (0.1, lfo(32, 0.3)), "ColorCycle": (0.35, lfo(16, 0.3)),
    "TrapGlow": (0.4, pulse(1, 0.3)), "Rotation": (0.0, lfo(64, 0.5)), "HueOffset": (0.0, lfo(48, 1.0)), "Brightness": 0.4,
}, ["groove", "beat", "bar", "bass"],
    "A Julia set whose shape breathes with the bass and glows on every beat.")
preset("web-julia-hard", "escape_time", {
    "FractalType": (0.0, lfo(16, 0.34, wave=SQ)), "JuliaRadius": (0.6, kick(0.1, 200)), "TrapGlow": (0.4, kick(0.4)),
    "Zoom": (0.5, pulse(1, 0.05)), "ColorMode": 2.0, "HueOffset": (0.0, lfo(8, 1.0)), "Brightness": 0.4,
}, ["hard", "beat", "bar", "kick"],
    "Kicks throw the Julia set into new shapes; the fractal family changes every four bars.")
preset("web-julia-ambient", "escape_time", {
    "JuliaAngle": (0.1, lfo(96, 0.4)), "JuliaRadius": (0.65, lfo(64, 0.05)), "ColorCycle": (0.3, amp(0.15, **AMB)),
    "MorphSpeed": 0.05, "Saturation": 0.6, "HueOffset": (0.0, lfo(96, 1.0)), "Brightness": 0.38,
}, ["ambient", "bar", "amp"],
    "A Julia set slowly wandering through its family in soft colour.")

preset("web-ink-groove", "fluid_ink", {
    "Emitters": (0.75, lfo(32, 0.25, wave=SQ)), "Force": (0.5, kick(0.4, 250)), "Swirl": (0.7, lfo(16, 0.2)),
    "Turbulence": (0.3, hat(0.3)), "HueOffset": (0.0, lfo(48, 1.0)), "Brightness": (0.5, bass(0.2)),
}, ["groove", "bar", "kick", "high", "bass"],
    "Ink stirred by the kicks: every hit pushes the emitters, hats roughen the flow.")
preset("web-ink-mid", "fluid_ink", {
    "Force": (0.3, mid(0.5)), "Turbulence": (0.2, mid(0.4)), "Swirl": (0.5, mid(0.4)), "HueOffset": (0.0, mid(0.3, 80, 800)),
}, ["mid"],
    "Ink that swirls with the mids (vocals, synths) and settles when they stop.")
preset("web-ink-ambient", "fluid_ink", {
    "Force": (0.3, amp(0.2, **AMB)), "Swirl": (0.6, lfo(64, 0.2)), "DyeFade": 0.2, "HueOffset": (0.0, lfo(96, 1.0)),
    "Saturation": 0.6,
}, ["ambient", "bar", "amp"],
    "Slow ink clouds; the stirring follows the overall level.")

preset("web-kifs-groove", "kifs", {
    "TwistX": (0.1, lfo(16, 0.2)), "TwistY": (0.15, bass(0.15)), "Glow": (0.4, pulse(1, 0.3)), "Scale": (0.5, lfo(32, 0.1)),
    "RotateY": (0.0, lfo(32, 1.0)), "HueOffset": (0.58, lfo(48, 1.0)), "Brightness": 0.75,
}, ["groove", "beat", "bar", "bass"],
    "A kaleidoscopic fractal that twists with the bass and glows on the beat.")
preset("web-kifs-hard", "kifs", {
    "SurfaceType": (0.0, lfo(8, 0.34, wave=SQ)), "Offset": (0.5, kick(0.15, 200)), "Glow": (0.35, kick(0.25)),
    "TwistZ": (0.0, hat(0.2)), "MorphSpeed": (0.0, kick(0.3)), "RotateY": (0.0, lfo(16, 1.0)), "HueOffset": (0.0, lfo(16, 1.0)), "Brightness": 0.75,
}, ["hard", "bar", "kick", "high"],
    "Kicks push the fold forward and flash it; the surface family flips every two bars.")
preset("web-kifs-ambient", "kifs", {
    "TwistX": (0.1, lfo(64, 0.2)), "MorphSpeed": 0.05, "Glow": (0.35, amp(0.15, **AMB)), "RotateY": (0.0, lfo(96, 1.0)),
    "ColorMode": 1.0, "HueOffset": (0.58, lfo(96, 1.0)), "Saturation": 0.7, "Brightness": 0.75,
}, ["ambient", "bar", "amp"],
    "A kaleidoscopic fractal slowly turning and refolding.")

preset("web-lenia-groove", "lenia", {
    "Relief": (0.5, pulse(1, 0.3)), "Brightness": (0.5, bass(0.25)), "Zoom": (0.5, lfo(32, 0.1)),
    "ColorMode": (0.0, lfo(32, 0.34, wave=SQ)), "HueOffset": (0.0, lfo(48, 1.0)),
}, ["groove", "beat", "bar", "bass"],
    "Artificial life whose relief pops on every beat and brightens with the bass.")
preset("web-lenia-ambient", "lenia", {
    "Relief": (0.4, amp(0.2, **AMB)), "HueOffset": (0.0, lfo(96, 1.0)), "Saturation": 0.6,
}, ["ambient", "bar", "amp"],
    "Lenia creatures drifting in muted colour.")

preset("web-mandelbox-groove", "mandelbox", {
    "Scale": (0.25, lfo(32, 0.1)), "FoldLimit": (0.5, bass(0.1)), "Glow": (0.4, pulse(1, 0.3)), "FlightSpeed": (0.25, amp(0.3)),
    "RotateZ": (0.0, lfo(48, 1.0)), "HueOffset": (0.0, lfo(48, 1.0)), "Brightness": 0.65,
}, ["groove", "beat", "bar", "bass", "amp"],
    "Flying through a Mandelbox that folds with the bass and glows on the beat.")
preset("web-mandelbox-hard", "mandelbox", {
    "MinRadius": (0.5, kick(0.15, 200)), "Glow": (0.4, kick(0.4)), "FlightSpeed": (0.2, kick(0.6, 300)),
    "ColorMode": (0.0, lfo(8, 0.25, wave=SQ)), "RotateY": (0.0, lfo(16, 1.0)), "Brightness": 0.65,
}, ["hard", "bar", "kick"],
    "The flight lurches on every kick while the box swells and flashes.")

preset("web-mandelbulb-groove", "mandelbulb", {
    "Power": (0.43, lfo(32, 0.15)), "JuliaMix": (0.0, bass(0.3)), "Glow": (0.3, pulse(1, 0.3)), "RotateY": (0.0, lfo(32, 1.0)),
    "Brightness": 0.7, "Zoom": 0.6, "HueOffset": (0.0, lfo(48, 1.0)),
}, ["groove", "beat", "bar", "bass"],
    "A Mandelbulb morphing between powers over eight bars, pulled into a Julia shape by the bass.")
preset("web-mandelbulb-hard", "mandelbulb", {
    "Power": (0.43, kick(0.15, 250)), "PowerSwing": (0.0, gate("audio_bass", 0.6, 1.0)), "Glow": (0.3, kick(0.4)),
    "ColorMode": (0.0, lfo(8, 0.25, wave=SQ)), "Brightness": 0.7, "Zoom": 0.6, "RotateY": (0.0, lfo(16, 1.0)),
}, ["hard", "bar", "kick", "bass-gated"],
    "Kicks spike the power; loud bass sets it swinging.")
preset("web-mandelbulb-ambient", "mandelbulb", {
    "Power": (0.4, lfo(96, 0.15)), "JuliaMix": (0.2, lfo(64, 0.2)), "RotateY": (0.0, lfo(96, 1.0)),
    "Glow": (0.3, amp(0.15, **AMB)), "Brightness": 0.65, "Zoom": 0.6,
}, ["ambient", "bar", "amp"],
    "A slowly turning Mandelbulb drifting between shapes.")

preset("web-nebula-groove", "nebula_volume", {
    "Glow": (0.5, pulse(1, 0.25)), "Density": (0.5, bass(0.2)), "Lighting": (0.4, kick(0.3)), "FlightSpeed": (0.25, amp(0.3)),
    "HueOffset": (0.0, lfo(48, 1.0)), "Brightness": 0.45,
}, ["groove", "beat", "bar", "bass", "kick", "amp"],
    "Flying through gas clouds that thicken with the bass and light up on the kicks.")
preset("web-nebula-hard", "nebula_volume", {
    "Lighting": (0.4, kick(0.5, 150)), "Turbulence": (0.5, hat(0.3)), "FlightSpeed": (0.3, kick(0.6, 250)),
    "ColorMode": (0.0, lfo(8, 0.34, wave=SQ)), "HueOffset": (0.0, lfo(16, 1.0)), "Brightness": 0.45,
}, ["hard", "bar", "kick", "high"],
    "Each kick fires the flight forward through lightning-lit gas.")
preset("web-nebula-ambient", "nebula_volume", {
    "Density": (0.5, lfo(64, 0.15)), "Glow": (0.45, amp(0.15, **AMB)), "FlightSpeed": 0.15, "HueOffset": (0.0, lfo(96, 1.0)),
    "Saturation": 0.6, "Brightness": 0.42,
}, ["ambient", "bar", "amp"],
    "A slow glide through a nebula.")

preset("web-kleinian-groove", "pseudo_kleinian", {
    "OffsetX": (0.2, lfo(16, 0.2)), "InversionStrength": (0.6, bass(0.2)), "CoreSize": (0.4, pulse(1, 0.15)),
    "FlightSpeed": (0.25, amp(0.3)), "HueOffset": (0.0, lfo(48, 1.0)),
}, ["groove", "beat", "bar", "bass", "amp"],
    "Flying through Kleinian corridors that bend with the bass.")
preset("web-kleinian-hard", "pseudo_kleinian", {
    "OffsetY": (0.4, kick(0.2, 200)), "FlightSpeed": (0.25, kick(0.5, 300)), "ColorMode": (0.0, lfo(8, 0.34, wave=SQ)),
    "Brightness": (0.6, hat(0.15)), "RotateZ": (0.0, lfo(16, 1.0)), "HueOffset": (0.0, lfo(16, 1.0)),
}, ["hard", "bar", "kick", "high"],
    "Kicks shove the flight and shift the walls; colours flip every two bars.")

preset("web-rd-groove", "reaction_diffusion", {
    "Relief": (0.5, pulse(1, 0.35)), "Brightness": (0.5, bass(0.2)), "Regime": (0.5, lfo(64, 0.2)),
    "ColorMode": (0.0, lfo(32, 0.34, wave=SQ)), "HueOffset": (0.0, lfo(48, 1.0)),
}, ["groove", "beat", "bar", "bass"],
    "Reaction-diffusion patterns whose relief pops on every beat; the pattern regime drifts over 16 bars.")
preset("web-rd-hard", "reaction_diffusion", {
    "Reseed": (0.0, gate("audio_bass", 0.8, 6.0, 5, 150)), "Relief": (0.5, kick(0.4)), "Zoom": (0.5, pulse(1, 0.05)),
    "ColorMode": 2.0, "HueOffset": (0.0, lfo(16, 1.0)),
}, ["hard", "beat", "bar", "kick", "bass-gated"],
    "Patterns that flash on kicks and are reseeded by the biggest bass hits.")
preset("web-rd-ambient", "reaction_diffusion", {
    "Relief": (0.4, amp(0.2, **AMB)), "Feed": (0.0, lfo(96, 0.2)), "HueOffset": (0.0, lfo(96, 1.0)), "Saturation": 0.6,
}, ["ambient", "bar", "amp"],
    "Slowly growing coral patterns.")


# ---------------------------------------------------------------- batch 7: FX chains
# Reactive web versions of the desktop's stock chains (web/content/fxchains/, which have no modulation)
# plus chains for the filters no web chain used yet. No strobes: a fast full-screen flash on a public
# stream is a photosensitivity risk, and video_strobe's rate is a TIME multiplier anyway.
chain("web-cyberpunk", ["pixel", "crt", "kick", "beat", "bass", "high"],
      slot("pixelate", {"pixelSize": (4.0, kick(0.06, 200)), "latticeMode": 0.0, "colorDepth": 16.0}),
      slot("retro_crt", {"scanlineIntensity": (0.45, pulse(1, 0.2)), "scanlineCount": 240.0, "curvature": 0.2,
                         "phosphorMask": 0.3, "vignette": 0.35, "brightnessBoost": (0.375, bass(0.2))}),
      slot("rgb_split", {"amount": (0.1, hat(0.3)), "angle": 0.0, "radialMode": 0.0, "dispersionMode": 1.0}))
chain("web-bitcrush", ["pixel", "glitch", "kick", "bass-gated"],
      slot("pixelate", {"pixelSize": (1.0, gate("audio_bass", 0.5, 0.25, 5, 200)), "latticeMode": 1.0, "colorDepth": 0.0}),
      slot("rgb_split", {"amount": (0.0, kick(0.5, 180)), "angle": (0.0, lfo(16, 1.0)), "radialMode": 0.0, "dispersionMode": 0.0}, wet=0.9))
chain("web-flir", ["thermal", "crt", "bass", "high", "bar"],
      slot("thermal_scanner", {"intensity": 1.0, "mode": (0.0, lfo(32, 0.5, wave=SQ)), "thermalBloom": (0.3, bass(0.4)),
                               "sensorGrain": (0.2, hat(0.4)), "vignette": 0.3}),
      slot("retro_crt", {"scanlineIntensity": 0.25, "scanlineCount": 320.0, "curvature": 0.1, "phosphorMask": 0.0,
                         "vignette": 0.2, "brightnessBoost": 0.25}, wet=0.85))
chain("web-vhs", ["vhs", "crt", "kick", "mid", "high"],
      slot("vhs_glitch", {"trackingJitter": (0.2, kick(0.5, 250)), "headSwitching": 0.5, "ycDelay": (0.3, mid(0.3)),
                          "rfDropouts": (0.1, hat(0.5)), "tapeNoise": 0.3, "mixRatio": 1.0}),
      slot("retro_crt", {"scanlineIntensity": 0.5, "scanlineCount": 300.0, "curvature": 0.3, "phosphorMask": 0.3,
                         "vignette": 0.35, "brightnessBoost": 0.35}, wet=0.85),
      slot("color_levels", {"contrast": 0.42, "saturation": 0.3}))
chain("web-hyperspace", ["kaleidoscope", "feedback", "kick", "bar"],
      slot("kaleidoscope", {"segments": (8, lfo(16, 0.09, wave=SQ)), "rotation": (0.0, lfo(32, 1.0)), "zoom": 0.5,
                            "centerX": 0.5, "centerY": 0.5, "originOffset": 0.0}),
      FEEDBACK(0.72, 0.475, (0.05, kick(0.05, 250)), 0.02, blur=0.2, chroma=0.15),
      slot("gradient_map", {"mixAmount": 0.85, "palette": 0.0, "cycleSpeed": 0.0, "cycleOffset": (0.0, lfo(64, 1.0)),
                            "hueShift": 0.0}, wet=0.85))
chain("web-laser", ["streak", "bloom", "kick", "bass", "bass-gated"],
      slot("anamorphic_streak", {"streakIntensity": (0.2, kick(0.6, 250)), "streakLength": 0.63, "threshold": 0.6,
                                 "knee": 0.39, "colorMode": 0.0, "crossFlare": (0.0, gate("audio_bass", 0.6, 1.0))}),
      slot("bloom", {"bloomIntensity": (0.2, bass(0.3)), "threshold": 0.45, "blurAmount": 0.38}, wet=0.75),
      slot("color_levels", {"contrast": 0.43, "saturation": 0.38}))
chain("web-chrome", ["mirror", "wave", "beat", "bass", "high", "kick"],
      slot("wave_displace", {"amplitude": (0.1, bass(0.3)), "frequency": 0.15, "speed": 0.2, "rippleMode": 1.0}, wet=0.7),
      slot("mirror_sphere", {"sphereRadius": (0.45, pulse(1, 0.08)), "fresnelGlow": (0.5, high(0.4)),
                             "chromaFringe": (0.2, kick(0.4)), "backgroundMix": 0.2}),
      slot("color_levels", {"contrast": 0.43, "saturation": 0.4, "filmicTone": 0.4}))
chain("web-mercury", ["liquid", "bass", "mid", "high"],
      slot("luma_displace", {"refractAmount": (0.2, bass(0.4)), "smoothness": 0.33, "chromaDispersion": (0.2, hat(0.4)),
                             "flowSpeed": 0.17}),
      slot("fluid_smear", {"smearAmount": (0.4, mid(0.4)), "curlScale": 0.15, "flowSpeed": 0.2, "decay": 0.75, "gravity": 0.05}))
chain("web-neon", ["edges", "feedback", "kick", "bass", "high", "bar"],
      slot("neon_edge", {"edgeStrength": (0.25, kick(0.2)), "threshold": 0.12, "glowSpread": (0.18, bass(0.15)),
                         "palette": (0.0, lfo(32, 0.34, wave=SQ)), "backgroundBlend": 0.0}),
      slot("directional_blur", {"blurAmount": (0.0, hat(0.4)), "angle": (0.0, lfo(16, 1.0)), "decay": 0.8,
                                "bidirectional": 1.0, "exposure": 0.56}, wet=0.8),
      FEEDBACK(0.55, 0.425, blur=0.1))
chain("web-popart", ["halftone", "edges", "kick", "bass", "beat", "bar"],
      slot("neon_edge", {"edgeStrength": (0.25, kick(0.3)), "threshold": 0.3, "glowSpread": 0.2, "palette": 2.0,
                         "backgroundBlend": 1.0}, wet=0.9),
      slot("halftone", {"dotScale": (0.3, bass(0.25)), "screenAngle": (0.0, lfo(32, 0.5)), "mode": 0.0, "smoothness": 0.17,
                        "paperTint": 0.15, "mixRatio": 1.0}),
      slot("gradient_map", {"mixAmount": (0.6, pulse(1, 0.3)), "palette": (2.0, lfo(64, 0.17, wave=SQ)), "cycleSpeed": 0.0,
                            "cycleOffset": 0.0, "hueShift": 0.0}, wet=0.7))
chain("web-prism", ["glass", "kaleidoscope", "bass", "high", "kick", "bar"],
      slot("faceted_glass", {"facetScale": (0.23, lfo(16, 0.1)), "refraction": (0.4, bass(0.3)), "dispersion": (0.35, hat(0.4)),
                             "bevelStrength": 0.65, "facetTilt": 0.55}),
      slot("kaleidoscope", {"segments": 6, "rotation": (0.0, lfo(24, 1.0)), "zoom": 0.5, "centerX": 0.5, "centerY": 0.5,
                            "originOffset": 0.0}),
      slot("rgb_split", {"amount": (0.1, kick(0.4)), "angle": 0.0, "radialMode": 1.0, "dispersionMode": 1.0}, wet=0.8))
chain("web-warmth", ["grade", "crt", "bass", "mid", "kick"],
      slot("color_levels", {"warmth": (0.15, bass(0.2)), "contrast": 0.38, "saturation": (0.35, mid(0.2))}),
      slot("retro_crt", {"scanlineIntensity": 0.25, "scanlineCount": 300.0, "curvature": 0.08, "phosphorMask": 0.2,
                         "vignette": 0.3, "brightnessBoost": 0.3}, wet=0.35),
      slot("bloom", {"bloomIntensity": (0.15, kick(0.3)), "threshold": 0.6, "blurAmount": 0.3}, wet=0.5))
chain("web-drop-weapon", ["blur", "glitch", "bass-gated", "kick", "beat"],
      slot("radial_blur", {"blurAmount": (0.0, gate("audio_bass", 0.6, 1.0, 5, 200)), "decay": 0.84, "exposure": 0.46}),
      slot("rgb_split", {"amount": (0.0, kick(0.6, 200)), "angle": 0.0, "radialMode": 1.0, "dispersionMode": 0.0}),
      slot("pinch_bulge", {"amount": (0.0, pulse(1, 0.3)), "radius": 0.4}))
chain("web-wormhole", ["tunnel", "bloom", "bass", "bar", "kick", "beat"],
      slot("polar_tunnel", {"depth": (0.39, bass(0.2)), "twist": (0.4, lfo(32, 0.3)), "zoom": 0.5, "symmetry": 2.0,
                            "depthFog": 0.6, "centerX": 0.5, "centerY": 0.5}),
      slot("pinch_bulge", {"amount": (0.3, kick(0.3)), "radius": 0.38}),
      slot("bloom", {"bloomIntensity": (0.12, pulse(1, 0.12)), "threshold": 0.55, "blurAmount": 0.31}, wet=0.7))
chain("web-blackhole", ["swirl", "tunnel", "bass", "high", "kick", "beat"],
      slot("vortex_swirl", {"twist": (0.3, bass(0.3)), "radius": 0.33, "dispersion": (0.4, hat(0.3)), "spiralArms": 0.0,
                            "centerX": 0.5, "centerY": 0.5}),
      slot("luma_displace", {"refractAmount": (0.3, kick(0.4)), "smoothness": 0.2, "chromaDispersion": 0.5, "flowSpeed": 0.0}, wet=0.85),
      slot("polar_tunnel", {"depth": (0.29, pulse(1, 0.1)), "twist": 0.5, "zoom": 0.5, "symmetry": 1.0, "depthFog": 0.5,
                            "centerX": 0.5, "centerY": 0.5}, wet=0.9))
chain("web-elevation", ["3d", "bar", "bass", "kick", "mid"],
      slot("3d_elevation", {"mode3D": (0.0, lfo(32, 0.34, wave=SQ)), "yaw": (0.0, lfo(32, 1.0)), "pitch": (0.0, lfo(48, 0.3)),
                            "zoom": (0.5, kick(0.1)), "separation": (0.4, bass(0.3)), "perspective": 0.5, "roundness": 1.0,
                            "blendMode": 0.0}),  # alpha over: additive planes burn the middle out to white
      # no feedback: on the 3D planes it piles up into a white blob within a second or two
      slot("color_levels", {"contrast": 0.4, "saturation": (0.35, mid(0.2))}))
chain("web-ink", ["liquid", "palette", "bass", "mid", "bar"],
      slot("fluid_smear", {"smearAmount": (0.2, bass(0.5)), "curlScale": 0.18, "flowSpeed": 0.25, "decay": 0.8,
                           "gravity": (0.0, lfo(16, 0.3))}),
      slot("gradient_map", {"mixAmount": (0.5, mid(0.4)), "palette": 3.0, "cycleSpeed": 0.0, "cycleOffset": (0.0, lfo(32, 1.0)),
                            "hueShift": 0.0}))
chain("web-ripple-drop", ["wave", "bass-gated", "bar", "high"],
      slot("wave_displace", {"amplitude": (0.0, gate("audio_bass", 0.5, 0.6)), "frequency": (0.2, lfo(16, 0.2)), "speed": 0.2,
                             "rippleMode": 1.0}),
      slot("rgb_split", {"amount": (0.0, hat(0.3)), "angle": (0.0, lfo(8, 1.0)), "radialMode": 1.0, "dispersionMode": 1.0}))
chain("web-crt", ["crt", "beat", "kick", "amp"],
      slot("retro_crt", {"scanlineIntensity": (0.3, pulse(1, 0.3)), "scanlineCount": 300.0, "curvature": (0.25, kick(0.2)),
                         "phosphorMask": 0.3, "vignette": (0.4, amp(-0.3)), "brightnessBoost": 0.35}))
chain("web-thermal-drop", ["thermal", "bass-gated"],
      slot("thermal_scanner", {"intensity": (0.0, gate("audio_bass", 0.55, 1.5, 15, 300)), "mode": 1.0, "thermalBloom": 0.3,
                               "sensorGrain": 0.2, "vignette": 0.3}))
chain("web-mirror", ["mirror", "kick", "bass", "beat"],
      slot("mirror_sphere", {"sphereRadius": (0.3, kick(0.25)), "centerX": 0.5, "centerY": 0.5, "fresnelGlow": (0.5, pulse(1, 0.3)),
                             "chromaFringe": 0.25, "backgroundMix": (0.25, bass(0.3))}))
chain("web-halftone-beat", ["halftone", "beat", "bar", "mid"],
      slot("halftone", {"dotScale": (0.25, pulse(1, 0.2)), "screenAngle": 0.0, "mode": (0.0, lfo(16, 0.5, wave=SQ)),
                        "smoothness": 0.2, "paperTint": 0.1, "mixRatio": (0.8, mid(0.2))}))
chain("web-motion", ["blur", "kick", "bar"],
      slot("directional_blur", {"blurAmount": (0.0, kick(0.6)), "angle": (0.0, lfo(8, 1.0)), "decay": 0.9,
                                "bidirectional": 1.0, "exposure": 0.45}))
chain("web-lumakey", ["key", "kick"],
      slot("luma_key", {"threshold": (0.5, kick(-0.4)), "softness": 0.1}),
      slot("color_levels", {"contrast": 0.4}))

print("wrote presets and chains under", WEB)
