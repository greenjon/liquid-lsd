#!/usr/bin/env bash
# Copies the lsd-visuals Android module (android/lsd-visuals/) into an app, with the web TV engine (web/, what
# ?embed mode loads) as its assets, so the app builds without a liquid-lsd checkout. The app commits the copy.
# Re-run and commit whenever web/ or android/lsd-visuals/ changes.
#   scripts/sync_lsd_visuals.sh APP_DIR               # e.g. ~/AndroidStudioProjects/spazradio
#   scripts/sync_lsd_visuals.sh APP_DIR --no-gallery  # without the Spaz Radio photo gallery (about 9 MB)
set -euo pipefail
src="$(cd "$(dirname "$0")/.." && pwd)"
app="${1:?usage: sync_lsd_visuals.sh APP_DIR [--no-gallery]}"
gallery=1; [ "${2:-}" = "--no-gallery" ] && gallery=0
[ -f "$app/settings.gradle.kts" ] || { echo "$app is not a Gradle project (no settings.gradle.kts)" >&2; exit 1; }
dest="$app/lsd-visuals"
mkdir -p "$dest"
# The module's own files (not its build output, and not the assets this script writes).
rsync -a --delete --checksum --exclude '/build/' --exclude '/src/main/assets/' --exclude '/VERSION' \
  "$src/android/lsd-visuals/" "$dest/"
# The engine: only what embed mode loads. No checks, tools, service worker, manifest or icons, and no fonts
# (only the web TV's chat blackboard uses them, and embed never shows it). The chat's .js files stay: ui.js imports them.
assets="$dest/src/main/assets/visuals"
mkdir -p "$assets"
rsync -a --delete --checksum \
  --exclude 'checks/' --exclude 'tools/' --exclude 'sw.js' --exclude 'manifest.webmanifest' \
  --exclude '*.png' --exclude '*.jpg' --exclude '*.ico' --exclude 'fonts/' --exclude 'gallery/' --exclude 'gallery.json' \
  "$src/web/" "$assets/"
# The photo gallery background (gallery.js) at phone size (768 px), bundled so it works offline.
if [ "$gallery" = 1 ]; then
  python3 "$src/scripts/gallery_curate.py" export "$assets" 768
else
  rm -rf "$assets/gallery"
  printf '{"base":"gallery/","images":[]}\n' > "$assets/gallery.json"
fi
git -C "$src" rev-parse HEAD > "$dest/VERSION"
dirty="$(git -C "$src" status --porcelain -- web android/lsd-visuals | head -1)"
[ -n "$dirty" ] && echo "warning: liquid-lsd has uncommitted changes in web/ or android/lsd-visuals/; VERSION names the last commit" >&2
echo "synced lsd-visuals: $(find "$dest" -type f -not -path '*/build/*' | wc -l) files, $(du -sh --exclude=build "$dest" | cut -f1), liquid-lsd $(cut -c1-9 "$dest/VERSION")"
