#!/usr/bin/env python3
"""analyze.py windows <video> <index.json> <out.json> [sheet.png]
For each {source,start,end} window: luma stats, near-black / clipped share, motion, biggest frame-to-frame jump, flicker.
Also analyze.py sheet <video> <times.json> <out.png>: labelled contact sheet of [{"label":..,"t":..}].
"""
import json
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw

W, H = 160, 90


def frames(video, fps=30):
    p = subprocess.Popen(["ffmpeg", "-v", "error", "-i", video, "-vf", f"scale={W}:{H}", "-r", str(fps), "-f", "rawvideo", "-pix_fmt", "gray", "-"],
                         stdout=subprocess.PIPE)
    i = 0
    while True:
        b = p.stdout.read(W * H)
        if len(b) < W * H:
            break
        yield i / fps, np.frombuffer(b, np.uint8).astype(np.float32)
        i += 1


def grab(video, t):
    p = subprocess.run(["ffmpeg", "-v", "error", "-ss", f"{t:.3f}", "-i", video, "-frames:v", "1", "-vf", "scale=320:180", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
    return Image.frombytes("RGB", (320, 180), p.stdout)


def sheet(video, items, out, cols=6):
    rows = (len(items) + cols - 1) // cols
    im = Image.new("RGB", (cols * 322, rows * 198), (30, 30, 30))
    d = ImageDraw.Draw(im)
    for i, it in enumerate(items):
        x, y = (i % cols) * 322, (i // cols) * 198
        im.paste(grab(video, it["t"]), (x, y + 16))
        d.text((x + 2, y + 2), it["label"], fill=(255, 255, 120))
    im.save(out)


def windows(video, index, out):
    idx = json.load(open(index))
    res = {i: [] for i in range(len(idx))}
    cur = 0
    prev = None
    for t, f in frames(video):
        while cur < len(idx) and t >= idx[cur]["end"]:
            cur += 1
        if cur >= len(idx):
            break
        w = idx[cur]
        if t >= w["start"] + 0.7:  # skip the load itself
            d = float(np.abs(f - prev).mean()) if prev is not None else 0.0
            res[cur].append((f.mean(), (f < 12).mean(), (f > 245).mean(), d))
        prev = f
    rows = []
    for i, w in enumerate(idx):
        a = np.array(res[i]) if res[i] else np.zeros((1, 4))
        rows.append({
            "name": w.get("source", str(i)),
            "luma": round(float(a[:, 0].mean()), 1),
            "luma_min": round(float(a[:, 0].min()), 1),
            "black%": round(float(a[:, 1].mean()) * 100, 1),
            "clip%": round(float(a[:, 2].mean()) * 100, 1),
            "motion": round(float(np.median(a[:, 3])), 2),
            "motion_max": round(float(a[:, 3].max()), 1),
            "frozen_frames%": round(float((a[:, 3] < 0.02).mean()) * 100, 1),
        })
    json.dump(rows, open(out, "w"), indent=1)
    keys = list(rows[0].keys())
    print(" ".join(f"{k:>14}" for k in keys))
    for r in rows:
        print(" ".join(f"{str(r[k])[:18]:>14}" for k in keys))


if __name__ == "__main__":
    if sys.argv[1] == "windows":
        windows(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        sheet(sys.argv[2], json.load(open(sys.argv[3])), sys.argv[4])
