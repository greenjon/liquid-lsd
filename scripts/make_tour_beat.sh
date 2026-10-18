#!/bin/bash
# Generates the placeholder tour soundtrack (120 BPM kick + hats + bass, 6 min). Swap tour/beat.wav for the licensed track later.
set -eu
cd "$(dirname "$0")/.."
mkdir -p tour
ffmpeg -v error -y \
  -f lavfi -i "aevalsrc='0.9*sin(2*PI*(55+90*exp(-mod(t,0.5)*30))*mod(t,0.5))*exp(-mod(t,0.5)*7)':s=44100:d=360" \
  -f lavfi -i "aevalsrc='0.25*(random(0)*2-1)*exp(-mod(t+0.25,0.5)*45)':s=44100:d=360" \
  -f lavfi -i "aevalsrc='0.3*sin(2*PI*55*t)*(0.5+0.5*sin(2*PI*t/4))':s=44100:d=360" \
  -filter_complex "amix=inputs=3:normalize=0,volume=0.8" -ac 2 tour/beat.wav
