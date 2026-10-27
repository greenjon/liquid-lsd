#!/usr/bin/env bash
# Publish the web TV (web/) to https://spaz.org/tv/ : rsync over SSH (key login as gj) into the folder
# Apache serves at /tv (Alias /tv /www/spaz/htdocs/lsd-relay/public). Only changed files upload (-c);
# the headless checks and test tools stay behind. Files the TV no longer uses are left on the server
# unless you pass --delete.
#   scripts/publish_web_tv.sh            # upload
#   scripts/publish_web_tv.sh --dry-run  # show what would change
#   scripts/publish_web_tv.sh --delete   # also remove server files that are not in web/
# After the files, it publishes the signed content manifest to /tv/update/ (what the Spaz Radio app polls to
# fetch new presets/playlists; see scripts/make_content_manifest.py). Content is validated first, and the
# manifest goes up LAST so a client never sees hashes for files that are not there yet.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="gj@spaz.org:/www/spaz/htdocs/lsd-relay/public/"
EXTRA=()
for arg in "$@"; do
  case "$arg" in
    --dry-run) EXTRA+=(--dry-run --itemize-changes) ;;
    --delete) EXTRA+=(--delete) ;;
    *) echo "unknown option $arg" >&2; exit 2 ;;
  esac
done
DRY=0; for a in "${EXTRA[@]}"; do [ "$a" = --dry-run ] && DRY=1; done
node --test "$ROOT/web/tools/content.test.mjs" >/dev/null || { echo "content validation failed (node --test web/tools/content.test.mjs); not publishing" >&2; exit 1; }
export RSYNC_RSH="ssh -o ServerAliveInterval=15 -o ServerAliveCountMax=4 -o BatchMode=yes"
rsync -rlc --partial --timeout=120 --chmod=D2755,F644 --exclude=/checks/ --exclude=/tools/ --exclude=/update/ --stats "${EXTRA[@]}" \
  "$ROOT/web/" "$DEST" | grep -E "^\*deleting|^[<>ch.][fdL]|Number of regular files transferred|Total transferred"
if [ "$DRY" = 0 ]; then
  UPD="$(mktemp -d)"; trap 'rm -rf "$UPD"' EXIT
  "$ROOT/scripts/make_content_manifest.py" "$UPD"
  rsync -rlc --timeout=120 --chmod=D2755,F644 "$UPD/" "${DEST}update/"
  echo "Content manifest published to https://spaz.org/tv/update/manifest.json"
fi
echo "Published to https://spaz.org/tv/ (bump CACHE in web/sw.js if installed copies ever look stale)."
