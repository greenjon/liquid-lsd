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


def preset(name, source, spec, tags, notes=""):
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
    "Shear": (0.25, lfo(32, 0.25)), "Speed": (0.45, amp(0.35)),
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
    "Frequency": (0.5, lfo(16, 0.15)), "FlightSpeed": (0.25, amp(0.5)),
    "WireframeMode": 1.0, "CoreGlow": (0.45, kick(0.4)),
    "ColorMode": 0.0, "HueOffset": (0.0, lfo(48, 1.0)), "Saturation": 0.9, "Brightness": 0.5,
    "Zoom": 0.35, "RotateZ": (0.0, lfo(64, 1.0)),
}, ["beat", "bar", "amp", "kick"],
    "A wireframe lattice you fly through: flight speed follows overall loudness, the core flashes on kicks, the cell size drifts over four bars.")

preset("web-cymatics-sub", "chladni_cymatics", {
    "FrequencyM": (3, bass(0.5, 80, 600)), "FrequencyN": (5, lfo(16, 0.15, wave="SQUARE")),
    "FrequencyL": 2, "PlateShape": (0.0, lfo(32, 0.3)), "NodeSharpness": (0.27, pulse(1, 0.3)),
    "SandAccumulation": 0.7, "VibrationSpeed": (0.15, amp(0.3)), "InvertMode": 0.0,
    "Glow": (0.35, kick(0.5, 200)), "PaletteMode": 0.0, "HueOffset": (0.0, lfo(24, 1.0)), "Scale": 0.5,
}, ["beat", "bar", "bass", "kick"],
    "More bass pushes the plate into higher modes; the pattern shifts every eight beats; sand glows on kicks.")

preset("web-celestial-pulse", "celestial_engine", {
    "Symmetries": (6, lfo(16, 0.25, wave="SQUARE")), "RingDensity": (0.26, lfo(8, 0.2)),
    "PhaseTwist": (0.25, lfo(16, 0.5)), "MoireStrength": (0.45, high(0.5, 20, 150)),
    "FlowerFold": (0.5, lfo(12, 0.2)), "PulseWave": (0.0, pulse(1, 1.0)),
    "Speed": (0.15, bass(0.3)), "LineWidth": (0.28, kick(0.35)),
    "Glow": 0.34, "ColorMode": 0.0, "HueOffset": (0.0, lfo(40, 1.0)), "HueSweep": 0.25,
    "Scale": (0.5, pulse(2, 0.08)),
}, ["beat", "bar", "bass", "high", "kick"],
    "A pulse wave on every beat, moire shimmer follows the hats, speed follows the bass.")

preset("web-fluid-bassdrop", "domain_warp_fluid", {
    "WarpStrength": (0.0, amp(0.2), gate("audio_bass", 0.5, 1.5)), "Swirl": (0.3, lfo(16, 0.5)),
    "Viscosity": 0.2, "Speed": (0.17, amp(0.4)), "Detail": 4, "Gloss": (0.45, pulse(1, 0.3)),
    "PaletteMode": (0.0, lfo(64, 0.5, wave="SQUARE")), "HueOffset": (0.0, lfo(20, 1.0)),
    "HueCycleSpeed": (0.1, mid(0.5)), "Zoom": (0.5, kick(0.1, 200)),
}, ["beat", "bar", "amp", "kick", "bass-gated", "mid"],
    "Calm marbling until the bass drops, then the warp opens up; gloss glints on the beat; the palette flips every 32 beats.")

preset("web-spiral-storm", "dynamic_spiral", {
    "MaxPoints": 1200, "Scale": (0.3, lfo(8, 0.15)), "Damping": 0.4, "WaveFreq": 0.3,
    "WaveAmp": (0.15, mid(0.3)), "Shear": (0.2, mid(0.4)), "Speed": (0.5, lfo(16, 0.9, wave="SQUARE")),
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
chain("web-drop-strobe", ["strobe", "bass-gated", "kick"],
      slot("video_strobe", {"rate": (0.0, gate("audio_bass", 0.7, 1.0, 5, 120)), "strobeMode": 0.0,
                            "dutyCycle": 0.5, "freezeHold": 0.0}),
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
    "HueOffset": (0.0, lfo(32, 1.0)), "Saturation": 0.9, "Brightness": 0.5, "Opacity": 0.55,
    "EdgeThickness": 0.3, "EdgeBrightness": (0.7, hat(0.3)), "Glow": 0.18, "Zoom": (0.55, kick(0.12, 200)),
}, ["beat", "bar", "mid", "kick"],
    "A glassy hypercube-to-sphere with thick edges; hats flicker the edges and kicks punch the zoom.")

preset("web-gyroid-solid", "gyroid_hyperspace", {
    "SurfaceType": 1.0, "WallThickness": (0.3, bass(0.3)), "Frequency": (0.4, lfo(32, 0.1)),
    "FlightSpeed": (0.15, amp(0.4)), "WireframeMode": 0.0, "CoreGlow": (0.5, kick(0.4)),
    "ColorMode": 1.0, "HueOffset": (0.0, lfo(40, 1.0)), "Saturation": 0.95, "Brightness": 0.7,
    "Zoom": 0.65, "RotateX": (0.0, lfo(48, 1.0)), "RotateZ": (0.0, lfo(64, 1.0)),
}, ["beat", "bar", "bass", "amp", "kick"],
    "Thick-walled caves instead of a lattice; the walls swell with the bass and the core glows on kicks.")

preset("web-cymatics-rings", "chladni_cymatics", {
    "FrequencyM": (6, lfo(32, 0.2, wave="SQUARE")), "FrequencyN": (6, mid(0.3, 60, 500)), "FrequencyL": 4,
    "PlateShape": 1.0, "NodeSharpness": (0.5, hat(0.3)), "SandAccumulation": 0.4,
    "VibrationSpeed": (0.1, amp(0.3)), "InvertMode": 1.0, "Glow": (0.5, kick(0.4, 200)),
    "PaletteMode": 2.0, "HueOffset": (0.0, lfo(30, 1.0)), "Scale": 0.55,
}, ["beat", "bar", "mid", "high", "kick"],
    "A round plate drawn as bright rings on dark; mids change the mode, hats sharpen the nodes.")

preset("web-celestial-bloom", "celestial_engine", {
    "Symmetries": (12, lfo(32, 0.2, wave="SQUARE")), "RingDensity": (0.55, lfo(12, 0.15)),
    "PhaseTwist": (-0.3, lfo(24, 0.5)), "MoireStrength": (0.2, mid(0.5)), "FlowerFold": (0.8, lfo(16, 0.15)),
    "PulseWave": (0.0, gate("audio_bass", 0.5, 1.0)), "Speed": (-0.1, amp(-0.3)), "LineWidth": 0.2,
    "Glow": (0.5, pulse(1, 0.15)), "ColorMode": 2.0, "HueOffset": (0.0, lfo(36, 1.0)), "HueSweep": 0.6, "Scale": 0.6,
}, ["beat", "bar", "mid", "amp", "bass-gated"],
    "A dense twelve-fold flower turning backwards; pulse rings fire only when the bass is loud.")

preset("web-fluid-lava", "domain_warp_fluid", {
    "WarpStrength": (0.6, bass(0.2)), "Swirl": (-0.4, lfo(24, 0.4)), "Viscosity": 0.7, "Speed": (0.08, amp(0.3)),
    "Detail": 2, "Gloss": (0.3, pulse(2, 0.15)), "PaletteMode": 4.0, "HueOffset": (0.0, lfo(48, 1.0)),
    "HueCycleSpeed": 0.05, "Zoom": (0.35, kick(0.06, 250)),
}, ["beat", "bar", "bass", "amp", "kick"],
    "Slow thick marbling in a dark palette; glossy highlights swell every other beat.")

preset("web-spiral-galaxy", "dynamic_spiral", {
    "MaxPoints": 2000, "Scale": (0.2, pulse(1, 0.04)), "Damping": 0.6, "WaveFreq": 0.1,
    "WaveAmp": 0.0, "Shear": (0.5, lfo(48, 0.3)), "Speed": 0.15, "DotSize": (0.1, kick(0.1, 200)),
    "Glow": 0.6, "HueOffset": (0.55, lfo(64, 1.0)), "HueSweep": 0.4, "TrailDecay": 0.92,
}, ["beat", "bar", "kick"],
    "A fine, dense galaxy arm turning slowly; dots swell on each kick.")

preset("web-spiral-sparks", "dynamic_spiral", {
    "MaxPoints": 300, "Scale": (0.55, lfo(8, 0.1)), "Damping": 0.1, "WaveFreq": 0.4,
    "WaveAmp": (0.0, gate("audio_flux_high", 0.4, 1.0, 3, 200)), "Shear": (-0.2, lfo(16, 0.4)),
    "Speed": (0.3, amp(0.3)), "DotSize": (0.4, hat(0.3)), "Glow": 0.55,
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
    "VibrationSpeed": (0.08, amp(0.2, 200, 1500)), "InvertMode": 0.0, "Glow": (0.35, amp(0.2, 200, 1500)),
    "PaletteMode": 1.0, "HueOffset": (0.0, lfo(96, 1.0)), "Scale": 0.5,
}, ["ambient", "bar", "amp"],
    "A slowly morphing plate in soft colour; modes drift over 8-16 bars and loudness barely moves the glow.")

preset("web-ambient-nebula", "celestial_engine", {
    "Symmetries": 8, "RingDensity": (0.35, lfo(32, 0.15)), "PhaseTwist": (0.15, lfo(48, 0.4)),
    "MoireStrength": (0.3, mid(0.25, 200, 1200)), "FlowerFold": (0.6, lfo(64, 0.2)), "PulseWave": 0.0,
    "Speed": (0.06, amp(0.1, 200, 1500)), "LineWidth": 0.15, "Glow": (0.45, amp(0.15, 200, 1500)),
    "ColorMode": 3.0, "HueOffset": (0.0, lfo(96, 1.0)), "HueSweep": 0.5, "Scale": 0.55,
}, ["ambient", "bar", "mid", "amp"],
    "A slow eight-fold bloom of thin lines; the glow breathes with the overall level, hue turns over 24 bars.")

preset("web-ambient-tides", "domain_warp_fluid", {
    "WarpStrength": (0.45, lfo(32, 0.2)), "Swirl": (0.2, lfo(48, 0.4)), "Viscosity": 0.5, "Speed": (0.05, amp(0.1, 200, 1500)),
    "Detail": 3, "Gloss": (0.2, lfo(24, 0.1)), "PaletteMode": 4.0, "HueOffset": (0.0, lfo(96, 1.0)),
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
    "WaveAmp": (0.1, mid(0.25)), "Shear": (0.3, lfo(8, 0.4)), "Speed": (0.35, lfo(32, 0.4)),
    "DotSize": (0.3, kick(0.12, 220)), "Glow": 0.5, "HueOffset": (0.1, lfo(24, 1.0)),
    "HueSweep": 0.2, "TrailDecay": 0.84,
}, ["groove", "beat", "bar", "mid", "kick"],
    "A mid-density spiral that shears every eight beats, swells on kicks and ripples with the mids.")

preset("web-hard-cymatics", "chladni_cymatics", {
    "FrequencyM": (5, kick(0.6, 120)), "FrequencyN": (5, lfo(4, 0.25, wave="SQUARE")), "FrequencyL": 2,
    "PlateShape": (0.0, lfo(8, 0.5, wave="SQUARE")), "NodeSharpness": (0.6, hat(0.3)), "SandAccumulation": 0.8,
    "VibrationSpeed": (0.25, bass(0.5)), "InvertMode": (0.0, gate("audio_bass", 0.6, 1.0, 5, 120)),
    "Glow": (0.4, kick(0.5, 150)), "PaletteMode": 3.0, "HueOffset": (0.0, lfo(8, 1.0)), "Scale": 0.5,
}, ["hard", "beat", "bar", "bass", "kick", "high", "bass-gated"],
    "The plate snaps between shapes every two beats, flashes on kicks, and inverts on loud bass.")

preset("web-hard-celestial", "celestial_engine", {
    "Symmetries": (8, lfo(4, 0.3, wave="SQUARE")), "RingDensity": (0.4, kick(0.3, 150)),
    "PhaseTwist": (0.3, lfo(4, 0.6)), "MoireStrength": (0.4, hat(0.5, 100)),
    "FlowerFold": (0.4, lfo(8, 0.3)), "PulseWave": (0.0, gate("audio_bass", 0.5, 1.2, 5, 120)),
    "Speed": (0.3, bass(0.5)), "LineWidth": (0.3, kick(0.4, 150)), "Glow": (0.4, pulse(0.5, 0.25)),
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

# batch 2 chains
chain("web-tunnel", ["tunnel", "beat", "bass"],
      slot("polar_tunnel", {"depth": (0.18, bass(0.3)), "twist": (0.0, lfo(32, 0.5)), "centerX": 0.5, "centerY": 0.5,
                            "zoom": (0.5, pulse(1, 0.06)), "symmetry": 2.0, "depthFog": 0.4}))
chain("web-vortex", ["swirl", "kick", "beat"],
      slot("vortex_swirl", {"twist": (0.1, kick(0.5, 300)), "radius": (0.35, pulse(1, 0.15)), "dispersion": (0.2, hat(0.4)),
                            "spiralArms": 0.0, "centerX": 0.5, "centerY": 0.5}),
      FEEDBACK(0.5, 0.4, 0.002, 0.003))

print("wrote presets and chains under", WEB)
