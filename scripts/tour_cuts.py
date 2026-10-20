#!/usr/bin/env python3
"""Cuts a rendered tour (raw.mp4 + chapters.json in <out>) into per-chapter silent videos.

clips/<id>.mp4: the whole chapter (the tour page and user-guide pages play these).
loops/<id>.mp4: its first clipSec, at most 20 s (the landing page's muted cards); .webm too with webm=1.
Re-encoded so cuts are frame accurate. Drafts use NVENC at render size; finals x264 at 1080p30 (chapters, CRF 30: UI text
stays sharp, and busy generator video is what costs bytes) and 1280 wide (loops).
Usage: tour_cuts.py <out-dir> <draft 0|1> <webm 0|1>
"""
import json, subprocess, sys

out, draft, webm = sys.argv[1], sys.argv[2] == "1", sys.argv[3] == "1"


def enc(crf):
    if draft:
        return ["-c:v", "h264_nvenc", "-preset", "p4", "-cq", "26"]
    return ["-c:v", "libx264", "-crf", str(crf), "-preset", "slow"]


for c in json.load(open(f"{out}/chapters.json")):
    length = c["end"] - c["start"]
    for kind, dur, vf, crf in (("clips", length, "fps=30", 30), ("loops", min(c["clip"], length, 20.0), "fps=30,scale=1280:-2", 28)):
        base = f"{out}/{kind}/{c['id']}"
        cut = ["ffmpeg", "-v", "error", "-y", "-ss", str(c["start"]), "-t", str(dur), "-i", f"{out}/raw.mp4", "-an",
               *([] if draft else ["-vf", vf])]
        subprocess.run([*cut, *enc(crf), "-pix_fmt", "yuv420p", "-movflags", "+faststart", f"{base}.mp4"], check=True)
        if webm and kind == "loops":
            subprocess.run([*cut, "-c:v", "libvpx-vp9", "-crf", "34", "-b:v", "0", "-row-mt", "1", "-deadline", "good",
                            "-cpu-used", "4", f"{base}.webm"], check=True)
