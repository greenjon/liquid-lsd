#!/bin/bash
# shots.sh <name>: one PNG per shot
V=$(dirname "$0"); n=$1; mkdir -p $V/out/$n/shots
python3 - "$V" "$n" <<'PY'
import json,subprocess,sys
V,n=sys.argv[1:3]
for s in json.load(open(f'{V}/scripts/{n}.shots.json')):
    subprocess.run(['ffmpeg','-v','error','-y','-ss',str(s['t']),'-i',f'{V}/out/{n}/raw.mp4','-frames:v','1',f"{V}/out/{n}/shots/{s['label']}.png"])
PY
