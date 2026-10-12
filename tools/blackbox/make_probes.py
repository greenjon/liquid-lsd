#!/usr/bin/env python3
"""Writes the probe flames for observing one variation of another flame program as a black box.

Method: tiny dot xforms (each one a contraction onto a chosen input point) draw a cloud of dots at known input points.
A FINAL xform holding only the variation under study moves each dot to where the variation sends it, and a final xform
acts on the plotted point only, never on the chain. Rendering one image therefore shows where the variation sends
every probe point, with no formula, code or parameter dump of the other program involved.

Which dot is which is decoded from a binary code: bit image j holds only the dots whose (index + 1) has bit j set.

Usage: make_probes.py VARIATION [--param name=value ...] [--out DIR]
Needs only the standard library. Does not read or run any flame program.
"""
import argparse, json, math, os

SIZE = 1000          # image is SIZE x SIZE pixels
SCALE = 100          # pixels per unit; with SIZE this shows +-5 units
GROUP = 63          # most dots in one flame
DOT = 0.0003         # dot contraction ratio: a dot cloud is at most ~0.0015 units wide, well under a pixel
PALETTE = "ff" * 3 * 256   # every colour white, so any hit shows as white


def grid_points():
    """11 x 11 Cartesian grid over [-2, 2] (121 points, includes the origin)."""
    return [(round(-2 + 0.4 * i, 6), round(-2 + 0.4 * j, 6)) for j in range(11) for i in range(11)]


def ring_points():
    """12 directions x 6 radii (72 points)."""
    return [(round(r * math.cos(math.radians(a)), 6), round(r * math.sin(math.radians(a)), 6))
            for a in range(0, 360, 30) for r in (0.1, 0.25, 0.5, 1.0, 2.0, 4.0)]


def flame(name, xforms, final, quality=40):
    head = (f'<flame name="{name}" size="{SIZE} {SIZE}" center="0 0" scale="{SCALE}" rotate="0" supersample="1" '
            f'filter="0.2" quality="{quality}" passes="1" temporal_samples="1" background="0 0 0" brightness="5" '
            f'gamma="1" vibrancy="1" highlight_power="-1" gamma_threshold="0" estimator_radius="0" '
            f'estimator_minimum="0" estimator_curve="0.4" palette_mode="linear">\n')
    body = "".join(xforms) + final
    return head + body + f'   <palette count="256" format="RGB">{PALETTE}</palette>\n</flame>\n'


def dot(px, py):
    # x' = s*x + (1-s)*p contracts onto p, so the plotted dot sits on the input point
    e = (1 - DOT) * px; f = (1 - DOT) * py
    return f'   <xform weight="1" color="0" color_speed="1" coefs="{DOT} 0 0 {DOT} {e:.9f} {f:.9f}" linear="1"/>\n'


def final_xform(var, params):
    attrs = f'{var}="1" ' + " ".join(f'{k}="{v}"' for k, v in params.items())
    return f'   <finalxform color="0" color_speed="0" coefs="1 0 0 1 0 0" {attrs.strip()}/>\n'


def identity_final():
    return '   <finalxform color="0" color_speed="0" coefs="1 0 0 1 0 0" linear="1"/>\n'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("variation")
    ap.add_argument("--param", action="append", default=[], help="name=value, the full attribute name, e.g. julian_power=3")
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    params = dict(p.split("=", 1) for p in a.param)
    out = a.out or f"probes_{a.variation}"
    os.makedirs(out, exist_ok=True)
    final = final_xform(a.variation, params)
    meta = {"variation": a.variation, "params": params, "size": SIZE, "scale": SCALE, "sets": {}}

    def write(fname, text):
        with open(os.path.join(out, fname + ".flame"), "w") as f:
            f.write(text)

    # points are split into groups of at most GROUP so no flame has more than 63 dot xforms (one program stops at 100)
    meta["groups"] = []
    for set_name, pts in (("grid", grid_points()), ("ring", ring_points())):
        meta["sets"][set_name] = pts
        for g, start in enumerate(range(0, len(pts), GROUP)):
            chunk = pts[start:start + GROUP]
            tag = f"{set_name}{g}"
            bits = len(chunk).bit_length()          # codes 1..len(chunk)
            meta["groups"].append({"tag": tag, "set": set_name, "start": start, "count": len(chunk), "bits": bits})
            write(f"probe_{tag}_all", flame(f"{a.variation}_{tag}_all", [dot(*p) for p in chunk], final))
            for j in range(bits):
                sel = [p for i, p in enumerate(chunk) if ((i + 1) >> j) & 1]
                write(f"probe_{tag}_bit{j}", flame(f"{a.variation}_{tag}_bit{j}", [dot(*p) for p in sel], final))

    # calibration: three known dots through a plain linear final xform, so the image's axes and scale are measured
    cal = [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (-2.0, 3.0)]
    meta["calibration"] = cal
    write("probe_calibrate", flame("calibrate", [dot(*p) for p in cal], identity_final()))

    # overview: the square [-1, 1]^2 filled by four half-size maps, then the variation
    quarter = [(0.5, 0.5), (-0.5, 0.5), (0.5, -0.5), (-0.5, -0.5)]
    fill = [f'   <xform weight="1" color="0" color_speed="1" coefs="0.5 0 0 0.5 {x} {y}" linear="1"/>\n' for x, y in quarter]
    write("probe_fill", flame(f"{a.variation}_fill", fill, final, quality=300))
    write("probe_fill_plain", flame("fill_plain", fill, identity_final(), quality=300))

    with open(os.path.join(out, "probes.json"), "w") as f:
        json.dump(meta, f)
    n = len([x for x in os.listdir(out) if x.endswith(".flame")])
    print(f"Wrote {n} flames and probes.json to {out}")


if __name__ == "__main__":
    main()
