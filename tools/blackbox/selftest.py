#!/usr/bin/env python3
"""Checks the probe tooling end to end with a made-up map (no flame program involved): draws the images an ideal
renderer would produce for a toy variation, extracts the table, and compares it with the map."""
import json, math, os, subprocess, sys, tempfile
import numpy as np
from PIL import Image

here = os.path.dirname(os.path.abspath(__file__))
toy = lambda x, y: (x * math.cos(0.3 * math.hypot(x, y)) - y * math.sin(0.3 * math.hypot(x, y)) + 0.1,
                    x * math.sin(0.3 * math.hypot(x, y)) + y * math.cos(0.3 * math.hypot(x, y)))  # a swirl plus a shift

with tempfile.TemporaryDirectory() as tmp:
    probes, imgs = os.path.join(tmp, "p"), os.path.join(tmp, "i")
    os.makedirs(imgs)
    subprocess.check_call([sys.executable, os.path.join(here, "make_probes.py"), "toy", "--out", probes], stdout=subprocess.DEVNULL)
    meta = json.load(open(os.path.join(probes, "probes.json")))
    size, scale = meta["size"], meta["scale"]

    def draw(name, pts, f):
        im = np.zeros((size, size))
        for (x, y) in pts:
            ox, oy = f(x, y)
            px, py = size / 2 + ox * scale, size / 2 + oy * scale     # x right, y down: the common convention
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    xx, yy = int(round(px)) + dx, int(round(py)) + dy
                    if 0 <= xx < size and 0 <= yy < size: im[yy, xx] = 1.0
        Image.fromarray((im * 255).astype(np.uint8)).save(os.path.join(imgs, name + ".png"))

    draw("probe_calibrate", meta["calibration"], lambda x, y: (x, y))
    for s in ("grid", "ring"):
        pts = meta["sets"][s]
        draw(f"probe_{s}_all", pts, toy)
        for j in range(meta["sets"][s + "_bits"]):
            draw(f"probe_{s}_bit{j}", [p for i, p in enumerate(pts) if ((i + 1) >> j) & 1], toy)
    out = os.path.join(tmp, "t.csv")
    subprocess.check_call([sys.executable, os.path.join(here, "extract_points.py"), probes, imgs, "--out", out])
    worst = 0.0; seen = 0; unseen = 0
    for line in open(out).read().splitlines()[1:]:
        s, idx, xi, yi, xo, yo, *_ = line.split(",")
        if not xo: unseen += 1; continue
        ex, ey = toy(float(xi), float(yi))
        worst = max(worst, abs(ex - float(xo)), abs(ey - float(yo))); seen += 1
    print(f"decoded {seen} points, {unseen} not seen, worst error {worst:.4f} units")
    assert seen >= 180 and worst < 0.012, "tooling self-test failed"
    print("ok")
