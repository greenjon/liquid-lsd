#!/usr/bin/env python3
"""Observes a RANDOM variation: one dot through a final xform, rendered at very low quality, then measured as a cloud.
Hits are counted per pixel (a pixel is lit or not), so the density follows from the lit fraction, lambda = -ln(1 - lit),
whatever the tone mapping does. Writes a radial table around the dot's expected position and the cloud's centroid.

Usage: cloud_stats.py PROGRAM X Y ATTR=VALUE ... [--quality Q] [--out DIR]
  e.g. cloud_stats.py jwildfire 0 0 pre_blur=0.5 linear=1
Needs numpy and Pillow. Reads only rendered images. Prints: centroid, total expected hits, and cumulative share of hits within each radius.
"""
import argparse, math, os, subprocess, sys
import numpy as np
from PIL import Image

here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, here)
import make_probes as mp
import run_probes as rp


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("program"); ap.add_argument("x", type=float); ap.add_argument("y", type=float)
    ap.add_argument("attrs", nargs="+"); ap.add_argument("--quality", type=float, default=0.05)
    ap.add_argument("--out", default="build/blackbox/cloud")
    ap.add_argument("--renders", type=int, default=16); ap.add_argument("--jobs", type=int, default=4)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    attrs = " ".join(f'{k}="{v}"' for k, v in (s.split("=", 1) for s in a.attrs))
    final = f'   <finalxform color="0" color_speed="0" coefs="1 0 0 1 0 0" {attrs}/>\n'
    path = os.path.join(a.out, "cloud.flame")
    open(path, "w").write(mp.flame("cloud", [mp.dot(a.x, a.y)], final, quality=a.quality))
    def render(k):
        png = os.path.join(a.out, f"cloud{k}.png")
        if os.path.exists(png): os.remove(png)
        if a.program == "flam3":
            env = dict(os.environ, **{"in": path, "out": png, "format": "png", "nthreads": "1", "seed": str(1000 + k), "isaac_seed": str(k)})
            subprocess.run(["flam3-render"], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        else:
            import tempfile, shutil
            with tempfile.TemporaryDirectory() as tmp:
                c = os.path.join(tmp, "cloud.flame"); shutil.copy(path, c)
                subprocess.run(["java", "-Djava.awt.headless=true", "-cp", rp.JAR, "org.jwildfire.cli.FlameRenderer", "-f", c,
                                "-w", "1000", "-h", "1000", "-q", str(a.quality)], cwd=tmp, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                shutil.move(os.path.join(tmp, "cloud.png"), png)
        return np.asarray(Image.open(png).convert("L"), dtype=float) / 255 > 0.05
    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(a.jobs) as pool:
        lits = list(pool.map(render, range(a.renders)))
    lit = np.mean(lits, axis=0)    # lit fraction per pixel over the renders
    h, w = lit.shape
    yy, xx = np.mgrid[0:h, 0:w]
    ux = (xx - w / 2) / mp.SCALE; uy = (yy - h / 2) / mp.SCALE     # units; image y runs down, so flip below if needed
    dx = ux - a.x; dy = uy - a.y
    r = np.hypot(dx, dy)
    edges = np.arange(0, 5.01, 0.05)
    cum = []; dens = []
    for lo, hi in zip(edges[:-1], edges[1:]):
        m = (r >= lo) & (r < hi)
        n = m.sum(); f = lit[m].mean() if n else 0
        lam = -math.log(max(1 - f, 1e-9)) if f < 0.999 else float("nan")   # f is the lit fraction over pixels and renders
        dens.append(lam)
    area = np.pi * (edges[1:] ** 2 - edges[:-1] ** 2) * mp.SCALE ** 2   # pixels per annulus
    hits = np.nan_to_num(np.array(dens)) * area
    tot = hits.sum()
    print(f"lit mass {lit.sum():.0f}, expected hits {tot:.0f}, per unit of weight see cdf")
    c = np.cumsum(hits) / tot
    for q in (0.1, 0.25, 0.5, 0.75, 0.9, 0.99):
        print(f"  {int(q*100):3d}% of hits within r = {edges[1:][np.searchsorted(c, q)]:.2f}")
    print("  cdf at r/step 0.05:", " ".join(f"{v:.3f}" for v in c[:30]))
    # centroid of lit pixels (flipping y does not matter for symmetric clouds; report both signs)
    print(f"  centroid offset from the dot: dx {np.average(dx, weights=lit):+.3f}, dy {np.average(dy, weights=lit):+.3f} (image y down)")
    print("  density by radius (hits per unit area, relative):", " ".join(f"{d:.2f}" for d in (np.array(dens)[:40:4])))

if __name__ == "__main__":
    main()
