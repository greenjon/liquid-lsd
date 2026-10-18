#!/bin/bash
# Renders the same tour script twice and compares the per-frame SHA-256 of the raw FBO-0 readback.
# Usage: scripts/tour_determinism_check.sh <script.json> [extra app args, e.g. --tour-hidden]
# Needs a GL-capable session (a visible or hidden GLFW window). Exit 0 = bit-identical, 1 = differs.
set -u
SCRIPT="${1:?usage: $0 <script.json> [extra args]}"
shift
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
for run in a b; do
  ./gradlew run -q --args="--tour=$SCRIPT --tour-out=$OUT/$run.mp4 --no-audio $*" 2>&1 | grep -E "TOUR_DONE|Exception|ERROR" || true
done
if cmp -s "$OUT/a.mp4.hashes" "$OUT/b.mp4.hashes"; then
  echo "DETERMINISTIC: $(wc -l < "$OUT/a.mp4.hashes") frames identical (outputs in $OUT)"
  exit 0
fi
echo "DIFFERS. First differing frame:"
diff <(cat "$OUT/a.mp4.hashes") <(cat "$OUT/b.mp4.hashes") | head -4
echo "(outputs in $OUT)"
exit 1
