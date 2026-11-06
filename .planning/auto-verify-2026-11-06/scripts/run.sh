#!/bin/bash
# usage: run.sh <script name> [extra args]  -> out/<name>/raw.mp4
V=/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify
W=/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/rt
n=$1; shift
mkdir -p $V/out/$n; rm -f $V/out/$n/raw.mp4
cd $W
[ -z "$IGPU" ] && export __NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia
export XDG_DATA_HOME=$V/xdg/data XDG_CONFIG_HOME=$V/xdg/config XDG_CACHE_HOME=$V/xdg/cache
build/install/fx/bin/fx --tour=$V/scripts/$n.json --tour-out=$V/out/$n/raw.mp4 --no-audio --tour-hidden --tour-fps=30 --tour-scale=${SCALE:-0.5} --tour-encoder=nvenc ${HASH:+--tour-hashes} $( [ -z "$HASH" ] && echo --tour-no-hashes) "$@" 2>&1 | awk '{ if (/TOUR_STAT/) print systime(), $0; else print }' > $V/out/$n/log.txt
echo rc=${PIPESTATUS[0]} >> $V/out/$n/log.txt
grep -E "TOUR_(GL|DONE|WARN|TIMING)|Exception|FATAL|rc=" $V/out/$n/log.txt | cut -c1-220 | head -40
