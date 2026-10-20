#!/bin/bash
# Renders a tour script and derives the website/video outputs from it.
# Usage: scripts/render_tour.sh [--draft] [--webm] <script.json> <out-dir> [extra app args, e.g. --tour-hidden]
#        scripts/render_tour.sh [--draft] [--webm] --all <out-root> [extra app args]   # every tours/*.json into <out-root>/<name>/
#   --draft  fast preview while writing the script: 30 fps, 960x540, NVENC (NVIDIA GPU), no frame hashes.
#   --webm   also encode hero.webm (VP9 on the CPU: slow, several minutes per minute of video).
# Outputs in <out-dir>: hero.mp4 (chapters embedded), poster.jpg, hero.vtt, captions.json, chapters.json,
# clips/<chapter-id>.mp4 (each whole chapter, silent: the tour page and user-guide pages play these),
# loops/<chapter-id>.mp4 (the first clipSec, max 20 s, of each chapter: the landing page's muted cards; .webm too with --webm),
# raw.* (direct renderer output).
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
if [ "${1:-}" = "--all" ]; then
  ROOT="${2:?usage: $0 [--draft] [--webm] --all <out-root> [extra args]}"
  shift 2
  FLAGS=()
  [ "$DRAFT" = 1 ] && FLAGS+=(--draft)
  [ "$WEBM" = 1 ] && FLAGS+=(--webm)
  for s in "$(dirname "$0")"/../tours/*.json; do
    name="$(basename "$s" .json)"
    echo "== $name"
    "$0" "${FLAGS[@]}" "$s" "$ROOT/$name" "$@"
  done
  exit 0
fi
SCRIPT="${1:?usage: $0 [--draft] [--webm] <script.json> <out-dir> [extra args]}"
OUT="${2:?usage: $0 [--draft] [--webm] <script.json> <out-dir> [extra args]}"
shift 2
cd "$(dirname "$0")/.."
SCRIPT="$(realpath "$SCRIPT")"
mkdir -p "$OUT/clips" "$OUT/loops"
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

# Per-chapter silent cuts: whole chapters (clips/) and short loops for the landing cards (loops/). See scripts/tour_cuts.py.
python3 -I "$(dirname "$0")/tour_cuts.py" "$OUT" "$DRAFT" "$WEBM"
if [ "$WEBM" = 1 ]; then
  ffmpeg -v error -y -i "$OUT/raw.mp4" -c:v libvpx-vp9 -crf 32 -b:v 0 -row-mt 1 -deadline good -cpu-used 4 -c:a libopus "$OUT/hero.webm"
fi
echo "Tour outputs in $OUT (total $(( $(date +%s) - START )) s)"
