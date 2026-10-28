#!/usr/bin/env python3
"""
gen_stock_web_presets.py - writes web/presets/stock-<source>.lsd from the desktop's shipped source defaults
(src/main/resources/stock_defaults/sources/<id>.json), so the web TV plays the same audio responses and
random ranges the desktop does. Run after changing a stock default, then:
    node --test web/tools/content.test.mjs web/tools/reactivity.test.mjs
    node web/checks/render_check.mjs

A stock file is sparse: it names a parameter's randomizeBase / baseMin / baseMax / modulators and leaves the
rest to the shader. The web format wants a complete ParameterDto, so this fills baseValue (the shader's DEFAULT,
or the middle of the range; randomizeBase rolls it at every load anyway), keeps the modulators as they are
(including bypassed alternates, which the evaluator skips) and drops notes. Only source parameters travel:
knobs, macros and FX are desktop concepts, and the web pairs FX chains itself.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "web"
STOCK = ROOT / "src/main/resources/stock_defaults/sources"

# Same lists as gen_web_presets.py: how pairing.js decides which FX chains suit a source.
FULL_FRAME_SOURCES = {"cyclic_ca", "kali_set", "glitch_blocks", "halftone_matrix", "quasicrystal", "thin_film",
                      "voronoi_cells", "wave_ripples", "isometric_voxels", "girih_stars",
                      "apollonian", "escape_time", "nebula_volume", "pseudo_kleinian",
                      # their stock ranges run bright: feedback and bloom washed them out in render_check
                      "domain_warp_fluid", "hyper_slice", "hyperbolic_tiling", "mandelbox", "metaballs",
                      "reaction_diffusion"}
SPARSE_SOURCES = {"dynamic_spiral"}

# Audio signals -> the tag a preset earns by having an active modulator on one (reactivity.test.mjs checks each).
SIGNAL_TAGS = {"audio_bass": "bass", "audio_flux_bass": "kick", "audio_mid": "mid", "audio_amp": "amp",
               "audio_high": "high", "audio_flux_high": "high"}


def header(path):
    m = re.search(r"/\*\s*(\{.*?\})\s*\*/", Path(path).read_text(), re.S)
    return json.loads(m.group(1))


def shader_inputs(source):
    f = WEB / "shaders" / f"{source}.frag"
    if not f.exists() or "/*" not in f.read_text():
        return {}
    return {i["NAME"]: i for i in header(f)["INPUTS"] if i["TYPE"] != "image"}


def number(x):
    return round(float(x), 5)


def build(stock):
    source = stock["sourceId"]
    inputs = shader_inputs(source)
    params, tags = {}, set()
    for name, p in stock["parameters"].items():
        info = inputs.get(name, {})
        lo = p.get("baseMin", info.get("MIN", 0.0))
        hi = p.get("baseMax", info.get("MAX", 1.0))
        base = p.get("baseValue", info.get("DEFAULT", (lo + hi) / 2))
        mods = []
        for m in p.get("modulators", []):
            m = {k: v for k, v in m.items() if k != "name"}
            m.setdefault("dcOffset", 0.0)
            mods.append(m)
            if not m.get("bypassed"):
                if m["sourceId"] in SIGNAL_TAGS:
                    tags.add(SIGNAL_TAGS[m["sourceId"]])
                elif m["sourceId"] == "lfo" and m.get("genUnit") == "BEAT" and m.get("subdivision", 1) >= 4:
                    tags.add("bar")
        params[name] = {"baseValue": number(base), "baseMin": number(lo), "baseMax": number(hi),
                        "randomizeBase": bool(p.get("randomizeBase", False)), "modulators": mods}
    out_tags = ["stock"] + sorted(tags)
    if source in FULL_FRAME_SOURCES:
        out_tags.append("fullframe")
    if source in SPARSE_SOURCES:
        out_tags.append("sparse")
    return {"version": 1, "name": f"stock-{source}", "visualSourceType": source, "parameters": params,
            "tags": out_tags, "presetNotes": f"The desktop's shipped default for {source}: random ranges and audio responses.",
            "globalAlpha": {"baseValue": 1.0, "baseMin": 0.0, "baseMax": 1.0, "randomizeBase": False, "modulators": []}}


def main():
    names = []
    catalog = {e["id"] for e in json.loads((WEB / "catalog.json").read_text())["sources"]} | {"mandala"}
    for f in sorted(STOCK.glob("*.json")):
        dto = build(json.loads(f.read_text()))
        skip = None
        if dto["visualSourceType"] not in catalog:
            skip = "not a web source"
        elif not any(not m.get("bypassed") for p in dto["parameters"].values() for m in p["modulators"]):
            skip = "no active audio response (the web rotation gate wants every look to move with the music)"
        if skip:
            print(f"skipped {f.stem}: {skip}")
            (WEB / "presets" / f"{dto['name']}.lsd").unlink(missing_ok=True)
            continue
        (WEB / "presets" / f"{dto['name']}.lsd").write_text(json.dumps(dto, indent=1) + "\n")
        names.append(dto["name"])
    print(f"wrote {len(names)} stock presets under {WEB / 'presets'}")
    return names


if __name__ == "__main__":
    main()
