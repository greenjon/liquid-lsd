#!/usr/bin/env python3
"""Reads the rendered probe images and writes the observed table: input point -> output point.

Usage: extract_points.py PROBE_DIR IMAGE_DIR [--out table.csv]
IMAGE_DIR holds one PNG per probe flame with the same base name (probe_grid_bit0.png, ...), rendered at the size and
camera written in the flame files. Measures the image axes from probe_calibrate.png instead of assuming them.
Needs numpy and Pillow. Does not read or run any flame program.
"""
import argparse, csv, json, os
import numpy as np
from PIL import Image

THRESH = 0.12   # luma above this counts as a hit
MAX_PIXELS = 30  # a dot wider than this has been stretched: its centroid says little about the point


def load(path):
    return np.asarray(Image.open(path).convert("L"), dtype=np.float64) / 255.0


def blobs(img, merge=1):
    """Connected components of the hit pixels (merged across gaps of up to `merge` pixels, because a sparse dot renders as
    a few separate speckles): list of (cx, cy, mass, npix) in pixel coordinates, centroid weighted by brightness."""
    hit = img > THRESH
    near = hit.copy()
    for dy in range(-merge, merge + 1):
        for dx in range(-merge, merge + 1):
            near |= np.roll(np.roll(hit, dy, axis=0), dx, axis=1)
    seen = np.zeros_like(near)
    h, w = near.shape
    out = []
    for y0, x0 in zip(*np.nonzero(near & hit)):
        if seen[y0, x0]:
            continue
        stack = [(y0, x0)]; seen[y0, x0] = True
        sx = sy = m = 0.0; n = 0
        while stack:
            y, x = stack.pop()
            if hit[y, x]:
                v = img[y, x]
                sx += x * v; sy += y * v; m += v; n += 1
            for dy in range(-merge, merge + 1):
                for dx in range(-merge, merge + 1):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < h and 0 <= xx < w and near[yy, xx] and not seen[yy, xx]:
                        seen[yy, xx] = True; stack.append((yy, xx))
        out.append((sx / m, sy / m, m, n))
    return out


def calibration(meta, img_dir):
    """Least-squares affine map pixel -> coordinate from the calibration dots (measured, not assumed)."""
    bl = blobs(load(os.path.join(img_dir, "probe_calibrate.png")))
    pts = meta["calibration"]
    if len(bl) != len(pts):
        raise SystemExit(f"calibration image shows {len(bl)} dots, expected {len(pts)}: check size, camera and brightness")
    # pair each dot with the nearest prediction under the nominal mapping (x right, y down), then fit
    size, scale = meta["size"], meta["scale"]
    nominal = lambda p: (size / 2 + p[0] * scale, size / 2 + p[1] * scale)
    pairs = []
    for p in pts:
        nx, ny = nominal(p)
        b = min(bl, key=lambda b: min(abs(b[0] - nx) + abs(b[1] - ny), abs(b[0] - nx) + abs(b[1] - (size - ny))))
        pairs.append((b[0], b[1], p[0], p[1]))
    A = np.array([[px, py, 1.0] for px, py, _, _ in pairs])
    cx = np.linalg.lstsq(A, np.array([q[2] for q in pairs]), rcond=None)[0]
    cy = np.linalg.lstsq(A, np.array([q[3] for q in pairs]), rcond=None)[0]
    resid = max(abs(A @ cx - [q[2] for q in pairs]).max(), abs(A @ cy - [q[3] for q in pairs]).max())
    print(f"calibration: x = {cx.round(5).tolist()}, y = {cy.round(5).tolist()}, worst residual {resid:.4f} units")
    return cx, cy


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("probe_dir"); ap.add_argument("image_dir"); ap.add_argument("--out", default="table.csv")
    a = ap.parse_args()
    meta = json.load(open(os.path.join(a.probe_dir, "probes.json")))
    cx, cy = calibration(meta, a.image_dir)
    to_xy = lambda px, py: (cx[0] * px + cx[1] * py + cx[2], cy[0] * px + cy[1] * py + cy[2])
    rows = []   # (set, index+1 in the set or 0 when the code is invalid, mass, pixels, x_out, y_out)
    for g in meta["groups"]:
        allb = blobs(load(os.path.join(a.image_dir, f"probe_{g['tag']}_all.png")))
        bit_imgs = [load(os.path.join(a.image_dir, f"probe_{g['tag']}_bit{j}.png")) for j in range(g["bits"])]
        for (bx, by, mass, npix) in allb:
            code = 0
            for j, im in enumerate(bit_imgs):
                y, x = int(round(by)), int(round(bx))
                if im[max(0, y - 2):y + 3, max(0, x - 2):x + 3].max() > THRESH:
                    code |= 1 << j
            x_out, y_out = to_xy(bx, by)
            rows.append((g["set"], g["start"] + code if 1 <= code <= g["count"] else 0, mass, npix, x_out, y_out))
    # a blob whose code is a single index is clean; anything else (merged dots, bad decode) is flagged
    with open(a.out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["set", "index", "x_in", "y_in", "x_out", "y_out", "mass", "pixels", "flag"])
        used = {}
        per_index = {}
        for set_name, code, *_ in rows:
            per_index[(set_name, code - 1)] = per_index.get((set_name, code - 1), 0) + 1
        for set_name, code, mass, npix, xo, yo in rows:
            pts = meta["sets"][set_name]
            idx = code - 1
            ok = 0 <= idx < len(pts)
            flag = "" if ok else "undecodable"
            if ok and per_index[(set_name, idx)] > 1:
                flag = "several blobs for one point (smeared into pieces, or noise)"
            elif ok and npix > MAX_PIXELS:
                flag = "smeared (dot stretched by the variation)"
            if ok and not flag:
                used[(set_name, idx)] = used.get((set_name, idx), 0) + 1
            xi, yi = pts[idx] if ok else ("", "")
            w.writerow([set_name, idx if ok else "", xi, yi, f"{xo:.5f}", f"{yo:.5f}", f"{mass:.2f}", npix, flag])
        for set_name in ("grid", "ring"):
            for i in range(len(meta["sets"][set_name])):
                if (set_name, i) not in used:
                    xi, yi = meta["sets"][set_name][i]
                    w.writerow([set_name, i, xi, yi, "", "", "", "", "no clean dot (off-screen, merged, smeared or lost)"])
    print(f"wrote {a.out}: {len(rows)} blobs, {len(used)} clean points of {sum(len(meta['sets'][k]) for k in ('grid', 'ring'))}")


if __name__ == "__main__":
    main()
