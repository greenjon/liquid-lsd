#!/usr/bin/env bash
# Build the site and publish it to the liquid-lsd-site repo (GitHub Pages, liquid-lsd.greenjon.com).
# The media (tour clips) live only in that repo, so its history can be squashed now and then:
#   scripts/publish_site.sh --squash   # replace the site repo's history with one commit
# Usage: scripts/publish_site.sh [--squash] [--tours DIR] [--site-repo DIR]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SITE_REPO="$ROOT/../liquid-lsd-site"
SQUASH=0
TOURS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --squash) SQUASH=1 ;;
    --tours) TOURS=(--tours "$2"); shift ;;
    --site-repo) SITE_REPO="$2"; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done

if [ ! -d "$SITE_REPO/.git" ]; then
  git clone https://github.com/greenjon/liquid-lsd-site.git "$SITE_REPO"
fi
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
python3 "$ROOT/scripts/build_website.py" --out "$BUILD/site" "${TOURS[@]}"

rsync -a --delete --exclude=.git --exclude=.github "$BUILD/site/" "$SITE_REPO/"
mkdir -p "$SITE_REPO/.github/workflows"
cp "$ROOT/website/deploy/pages.yml" "$SITE_REPO/.github/workflows/pages.yml"

cd "$SITE_REPO"
if [ "$SQUASH" = 1 ]; then
  git checkout --orphan squashed
  git add -A
  git commit -q -m "Site $(date +%F)"
  git branch -M main
  git push --force origin main
else
  git add -A
  if git diff --cached --quiet; then echo "Site unchanged."; exit 0; fi
  git commit -q -m "Site $(date +%F)"
  git push origin main
fi
