#!/bin/bash
V=/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify; W=/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/../rt
export DISPLAY=:77
[ "$(printenv DISPLAY)" = ":77" ] || exit 9
grab() { ffmpeg -v error -y -f x11grab -video_size 1280x720 -i :77 -frames:v 1 $V/out/real_$1.png; }
ready() { for i in $(seq 1 60); do grab tmp; [ $(stat -c %s $V/out/real_tmp.png) -gt 60000 ] && return 0; sleep 5; done; return 1; }
clk() { xdotool mousemove $1 $2; sleep 1.5; xdotool click 1; sleep 3; }
ready && echo ready1 $(date +%T) || echo notready1
sleep 10; grab 1_start
xdotool search --name Liquid windowfocus 2>/dev/null
clk 838 155; grab 2_fxoff
clk 216 58; grab 3_bound
clk 358 485; sleep 3; grab 4_loaded
xdotool key Escape; sleep 3; grab 5_esc
stat -c '%y %n' $W/lsd-preferences.properties $W/library/last_session.json
sleep 70
stat -c 'after70 %y %n' $W/lsd-preferences.properties $W/library/last_session.json
date +%T; grep -n -iE "startup|bypass|fxon" $W/lsd-preferences.properties | head
python3 -c "
import json;d=json.load(open('$W/library/last_session.json'));s=json.dumps(d);print(list(d.keys())[:12]);print('aurora in session','aurora' in s)"
pkill -9 -f 'install/fx/lib' ; sleep 2; echo killed
sed -i 's/^startupBehavior=.*/startupBehavior=PREVIOUS_SESSION/' $W/lsd-preferences.properties; grep -c '^startupBehavior=PREVIOUS' $W/lsd-preferences.properties
nohup $V/realrun.sh 2 >/dev/null 2>&1 &
sleep 15; ready && echo ready2 || echo notready2
sleep 15; grab 6_restart
pkill -TERM -f 'install/fx/lib'; sleep 5; pkill -9 -f 'install/fx/lib'; kill $(cat $V/xvfb.pid)
echo done
