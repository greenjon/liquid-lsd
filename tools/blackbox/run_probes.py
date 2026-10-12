#!/usr/bin/env python3
"""Observes one variation with a command-line flame renderer and writes table.csv (the observer role, automated).
The program is run as a user would, on the probe flames, and only its rendered images are read.

Programs: flam3 (flam3-render on the PATH), jwildfire (java -cp $JWILDFIRE_JAR org.jwildfire.cli.FlameRenderer;
JWILDFIRE_JAR defaults to ~/Apps/j-wildfire-9.00/lib/j-wildfire.jar).

Usage: run_probes.py PROGRAM VARIATION [--param name=value ...] [--translate tx,ty] [--out DIR] [--jobs N]
"""
import argparse, os, shutil, subprocess, sys, tempfile
from concurrent.futures import ThreadPoolExecutor

here = os.path.dirname(os.path.abspath(__file__))
JAR = os.environ.get("JWILDFIRE_JAR", os.path.expanduser("~/Apps/j-wildfire-9.00/lib/j-wildfire.jar"))


def render_flam3(flame, out_png):
    env = dict(os.environ, **{"in": flame, "out": out_png, "format": "png", "nthreads": "2"})
    return subprocess.run(["flam3-render"], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode


def render_jwildfire(flame, out_png):
    # the program writes the PNG next to the flame it is given, so give it a private copy
    with tempfile.TemporaryDirectory() as tmp:
        copy = os.path.join(tmp, os.path.basename(flame))
        shutil.copy(flame, copy)
        r = subprocess.run(["java", "-Djava.awt.headless=true", "-cp", JAR, "org.jwildfire.cli.FlameRenderer",
                            "-f", copy, "-w", "1000", "-h", "1000", "-q", "40"], cwd=tmp,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        png = copy[:-6] + ".png"
        if os.path.isfile(png):
            shutil.move(png, out_png)
        return r.returncode


RENDERERS = {"flam3": render_flam3, "jwildfire": render_jwildfire}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("program", choices=sorted(RENDERERS)); ap.add_argument("variation")
    ap.add_argument("--param", action="append", default=[])
    ap.add_argument("--translate", default=None, help="tx,ty of the final xform, recorded in probes.json")
    ap.add_argument("--out", default=None); ap.add_argument("--jobs", type=int, default=3)
    a = ap.parse_args()
    out = a.out or f"build/blackbox/{a.program}/{a.variation}"
    cmd = [sys.executable, os.path.join(here, "make_probes.py"), a.variation, "--out", out]
    for p in a.param:
        cmd += ["--param", p]
    if a.translate:
        cmd += ["--translate", a.translate]
    subprocess.check_call(cmd, stdout=subprocess.DEVNULL)
    flames = sorted(f for f in os.listdir(out) if f.startswith("probe_") and f.endswith(".flame") and "fill" not in f)
    images = os.path.join(out, "images")
    os.makedirs(images, exist_ok=True)

    def one(f):
        png = os.path.join(images, f[:-6] + ".png")
        RENDERERS[a.program](os.path.join(out, f), png)
        if not os.path.isfile(png):
            raise SystemExit(f"{a.program} produced no image for {f}")

    with ThreadPoolExecutor(a.jobs) as pool:
        list(pool.map(one, flames))
    subprocess.check_call([sys.executable, os.path.join(here, "extract_points.py"), out, images, "--out", os.path.join(out, "table.csv")])


if __name__ == "__main__":
    main()
