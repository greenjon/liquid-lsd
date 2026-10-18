#!/bin/bash
# Renders a tour script and derives the website/video outputs from it.
# Usage: scripts/render_tour.sh [--draft] [--webm] <script.json> <out-dir> [extra app args, e.g. --tour-hidden]
#   --draft  fast preview while writing the script: 30 fps, 960x540, NVENC (NVIDIA GPU), no frame hashes.
#   --webm   also encode hero.webm (VP9 on the CPU: slow, several minutes per minute of video).
# Outputs in <out-dir>: hero.mp4 (chapters embedded), poster.jpg, hero.vtt, captions.json, chapters.json,
# clips/<chapter-id>.mp4 (silent loops, max 20 s; .webm too with --webm), raw.* (direct renderer output).
set -eu
DRAFT=0
WEBM=0
while [ $# -gt 0 ]; do
  case "$1" in
    --draft) DRAFT=1; shift ;;
    --webm) WEBM=1; shift ;;
    *) break ;;
  esac
done
SCRIPT="${1:?usage: $0 [--draft] [--webm] <script.json> <out-dir> [extra args]}"
OUT="${2:?usage: $0 [--draft] [--webm] <script.json> <out-dir> [extra args]}"
shift 2
cd "$(dirname "$0")/.."
SCRIPT="$(realpath "$SCRIPT")"
mkdir -p "$OUT/clips"
OUT="$(realpath "$OUT")"
APP_ARGS="$*"
[ "$DRAFT" = 1 ] && APP_ARGS="--tour-draft $APP_ARGS"
START=$(date +%s)
./gradlew run -q --args="--tour=$SCRIPT --tour-out=$OUT/raw.mp4 --no-audio $APP_ARGS" 2>&1 | grep -E "TOUR_(DONE|WARN|OUT|TIMING)|Exception|ERROR" || true
[ -s "$OUT/raw.mp4" ] || { echo "render failed: no raw.mp4" >&2; exit 1; }
echo "render took $(( $(date +%s) - START )) s"

ffmpeg -v error -y -i "$OUT/raw.mp4" -i "$OUT/raw.ffmeta" -map_metadata 1 -map_chapters 1 -c copy -movflags +faststart "$OUT/hero.mp4"
ffmpeg -v error -y -ss 1 -i "$OUT/raw.mp4" -frames:v 1 -q:v 3 "$OUT/poster.jpg"
cp "$OUT/raw.vtt" "$OUT/hero.vtt"
cp "$OUT/raw.captions.json" "$OUT/captions.json"
cp "$OUT/raw.chapters.json" "$OUT/chapters.json"

# Per-chapter silent loops (re-encoded so cuts are frame accurate). Drafts use NVENC; finals x264 at 1280 wide.
python3 -I - "$OUT" "$DRAFT" "$WEBM" <<'PY'
import json, subprocess, sys
out, draft, webm = sys.argv[1], sys.argv[2] == "1", sys.argv[3] == "1"
mp4 = ["-c:v", "h264_nvenc", "-preset", "p4", "-cq", "26"] if draft else ["-c:v", "libx264", "-crf", "23", "-preset", "fast"]
vf = [] if draft else ["-vf", "scale=1280:-2"]
for c in json.load(open(f"{out}/chapters.json")):
    dur = min(c["clip"], c["end"] - c["start"], 20.0)
    base = f"{out}/clips/{c['id']}"
    cut = ["ffmpeg", "-v", "error", "-y", "-ss", str(c["start"]), "-t", str(dur), "-i", f"{out}/raw.mp4", "-an", *vf]
    subprocess.run([*cut, *mp4, "-pix_fmt", "yuv420p", "-movflags", "+faststart", f"{base}.mp4"], check=True)
    if webm:
        subprocess.run([*cut, "-c:v", "libvpx-vp9", "-crf", "34", "-b:v", "0", "-row-mt", "1", "-deadline", "good", "-cpu-used", "4", f"{base}.webm"], check=True)
PY
if [ "$WEBM" = 1 ]; then
  ffmpeg -v error -y -i "$OUT/raw.mp4" -c:v libvpx-vp9 -crf 32 -b:v 0 -row-mt 1 -deadline good -cpu-used 4 -c:a libopus "$OUT/hero.webm"
fi
echo "Tour outputs in $OUT (total $(( $(date +%s) - START )) s)"
