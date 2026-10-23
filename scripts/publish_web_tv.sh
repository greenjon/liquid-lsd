#!/usr/bin/env bash
# Publish the web TV (web/) to https://spaz.org/tv/ : rsync over SSH (key login as gj) into the folder
# Apache serves at /tv (Alias /tv /www/spaz/htdocs/lsd-relay/public). Only changed files upload (-c);
# the headless checks and test tools stay behind. Files the TV no longer uses are left on the server
# unless you pass --delete.
#   scripts/publish_web_tv.sh            # upload
#   scripts/publish_web_tv.sh --dry-run  # show what would change
#   scripts/publish_web_tv.sh --delete   # also remove server files that are not in web/
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
export RSYNC_RSH="ssh -o ServerAliveInterval=15 -o ServerAliveCountMax=4 -o BatchMode=yes"
rsync -rlc --partial --timeout=120 --chmod=D2755,F644 --exclude=/checks/ --exclude=/tools/ --stats "${EXTRA[@]}" \
  "$ROOT/web/" "$DEST" | grep -E "^\*deleting|^[<>ch.][fdL]|Number of regular files transferred|Total transferred"
echo "Published to https://spaz.org/tv/ (bump CACHE in web/sw.js if installed copies ever look stale)."
