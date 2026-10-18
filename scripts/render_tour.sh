#!/bin/bash
# Renders a tour script and derives every website/video output from it.
# Usage: scripts/render_tour.sh <script.json> <out-dir> [extra app args, e.g. --tour-hidden]
# Outputs in <out-dir> (name = script basename): hero.mp4 (chapters embedded), hero.webm, poster.jpg, hero.vtt,
# captions.json, chapters.json, clips/<chapter-id>.{mp4,webm} (silent loops, max 20 s), raw.mp4.hashes (determinism hashes).
set -eu
SCRIPT="${1:?usage: $0 <script.json> <out-dir> [extra args]}"
OUT="${2:?usage: $0 <script.json> <out-dir> [extra args]}"
shift 2
cd "$(dirname "$0")/.."
SCRIPT="$(realpath "$SCRIPT")"
mkdir -p "$OUT/clips"
OUT="$(realpath "$OUT")"
./gradlew run -q --args="--tour=$SCRIPT --tour-out=$OUT/raw.mp4 --no-audio $*" 2>&1 | grep -E "TOUR_DONE|TOUR_WARN|Exception|ERROR" || true
[ -s "$OUT/raw.mp4" ] || { echo "render failed: no raw.mp4" >&2; exit 1; }

ffmpeg -v error -y -i "$OUT/raw.mp4" -i "$OUT/raw.ffmeta" -map_metadata 1 -map_chapters 1 -c copy -movflags +faststart "$OUT/hero.mp4"
ffmpeg -v error -y -i "$OUT/raw.mp4" -c:v libvpx-vp9 -crf 32 -b:v 0 -row-mt 1 -c:a libopus "$OUT/hero.webm"
ffmpeg -v error -y -ss 1 -i "$OUT/raw.mp4" -frames:v 1 -q:v 3 "$OUT/poster.jpg"
cp "$OUT/raw.vtt" "$OUT/hero.vtt"
cp "$OUT/raw.captions.json" "$OUT/captions.json"
cp "$OUT/raw.chapters.json" "$OUT/chapters.json"

# Per-chapter silent loops (re-encoded so cuts are frame accurate).
python3 -I - "$OUT" <<'PY'
import json, subprocess, sys
out = sys.argv[1]
for c in json.load(open(f"{out}/chapters.json")):
    dur = min(c["clip"], c["end"] - c["start"], 20.0)
    base = f"{out}/clips/{c['id']}"
    for ext, codec in (("mp4", ["-c:v", "libx264", "-crf", "23", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]),
                       ("webm", ["-c:v", "libvpx-vp9", "-crf", "34", "-b:v", "0", "-row-mt", "1"])):
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(c["start"]), "-t", str(dur), "-i", f"{out}/raw.mp4",
                        "-an", "-vf", "scale=1280:-2", *codec, f"{base}.{ext}"], check=True)
PY
echo "Tour outputs in $OUT"
