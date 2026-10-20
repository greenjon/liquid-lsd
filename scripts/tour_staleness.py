#!/usr/bin/env python3
"""Reports which walkthrough videos are out of date, derived from git (no hand-kept list).

Usage: scripts/tour_staleness.py [-v] [--names] [--out tours/out]

Each render stamps <out>/<tour>/rendered_at.json with the commit it was built from
(scripts/render_tour.sh). For every tour this script
  1. reads the widget keys the script aims at (`target` fields of tours/<tour>.json),
  2. maps each key to the Kotlin files that register it (string literal search in src/main/kotlin),
  3. lists commits since the render that touched those files, the tour script, or the tour renderer.
Commits that only touched other UI/rendering files are counted as "maybe" (colors, layout, shaders can
change a video without touching a widget key).

  -v       list the commits and the key->file mapping
  --names  print only the names of stale tours, one per line (for a render loop)
Videos rendered before stamping existed fall back to the video file's modification time.
"""
import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src" / "main" / "kotlin"
RENDERER = "src/main/kotlin/llm/slop/liquidlsd/tour"
MAYBE_DIRS = ["src/main/kotlin/llm/slop/liquidlsd/ui", "src/main/kotlin/llm/slop/liquidlsd/rendering",
              "src/main/resources/shaders", "defaults"]
MAX_FILES_PER_KEY = 15 # a needle matching more files than this is too generic to attribute


def git(*args):
    r = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def load_sources():
    out = {}
    for p in SRC.rglob("*.kt"):
        rel = p.relative_to(ROOT).as_posix()
        if rel.startswith(RENDERER):
            continue
        out[rel] = p.read_text(errors="ignore")
    return out


def tour_keys(script):
    keys = set()
    for step in json.loads(script.read_text()).get("steps", []):
        t = step.get("target")
        if isinstance(t, str) and step.get("action") not in ("type", "chapter", "hold") and re.match(r"^[\w][\w /:.\-]*$", t):
            keys.add(t)
    return keys


def needles(key):
    """Source-literal needles to try for a widget key, most specific first."""
    if key.startswith("btnid_"):
        return ["##" + key[6:]]
    if key.startswith("btn_"):
        return ['"' + key[4:]]
    out, cand = [], key
    while len(cand) >= 5:
        out.append('"' + cand)
        if "_" not in cand:
            break
        cand = cand.rsplit("_", 1)[0]
    head = key.split("_", 1)[0] + "_"
    if len(head) >= 3 and "_" in key:
        out.append('"' + head)
    return out


def map_key(key, sources):
    if key.startswith("pipe:"):
        return [RENDERER + "/PipelineDiagram.kt"]
    for n in needles(key):
        hits = [f for f, text in sources.items() if n in text]
        if 0 < len(hits) <= MAX_FILES_PER_KEY:
            return hits
    return []


def rendered_commit(tour_dir):
    stamp = tour_dir / "rendered_at.json"
    if stamp.exists():
        info = json.loads(stamp.read_text())
        if git("cat-file", "-t", info["commit"]):
            return info["commit"], info, "stamp"
    video = tour_dir / "hero.mp4"
    if video.exists():
        c = git("rev-list", "-1", f"--before=@{int(video.stat().st_mtime)}", "HEAD")
        if c and c.strip():
            return c.strip(), {}, "file date"
    return None, {}, "none"


def log(commit, paths):
    out = git("log", "--format=%h %ad %s", "--date=short", f"{commit}..HEAD", "--", *paths)
    return [l for l in (out or "").splitlines() if l]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-v", action="store_true")
    ap.add_argument("--names", action="store_true")
    ap.add_argument("--out", default="tours/out")
    args = ap.parse_args()
    out_root = ROOT / args.out
    sources = load_sources()
    stale, rows = [], []

    for script in sorted((ROOT / "tours").glob("*.json")):
        name = script.stem
        commit, info, how = rendered_commit(out_root / name)
        if commit is None:
            rows.append((name, "NO RENDER", "no video in " + args.out, [], {}))
            stale.append(name)
            continue
        mapping = {k: map_key(k, sources) for k in sorted(tour_keys(script))}
        files = sorted({f for fs in mapping.values() for f in fs})
        direct = log(commit, files + [f"tours/{name}.json", RENDERER])
        seen = {l.split()[0] for l in direct}
        maybe = [l for l in log(commit, MAYBE_DIRS) if l.split()[0] not in seen]
        note = f"rendered from {commit[:7]} ({how})"
        if info.get("dirty"):
            note += ", dirty tree"
        if info.get("draft"):
            note += ", DRAFT render"
        if direct:
            rows.append((name, "STALE", f"{len(direct)} commit(s); {len(maybe)} maybe; {note}", direct, mapping))
            stale.append(name)
        else:
            extra = f"; {len(maybe)} other UI commit(s) may matter" if maybe else ""
            rows.append((name, "ok", note + extra, maybe if args.v else [], mapping))

    if args.names:
        print("\n".join(stale))
        return
    for name, status, note, commits, mapping in rows:
        print(f"{name:12} {status:9} {note}")
        if args.v:
            for l in commits:
                print(f"    {l}")
            unmapped = [k for k, fs in mapping.items() if not fs]
            if unmapped:
                print(f"    unmapped keys (not tracked): {', '.join(unmapped[:12])}{' ...' if len(unmapped) > 12 else ''}")
    if not stale:
        print("\nAll videos are current.")
    else:
        print(f"\nRe-render: {' '.join(stale)}")


if __name__ == "__main__":
    main()
