#!/usr/bin/env python3
"""replay_hang.py <work dir> <saved script.json> [display]: replays one crash-test script alone (as monkey.py runs it: no network, empty /dev/snd) and, if it
hasn't finished by 3x its length, writes Java thread dumps (SIGQUIT) into the log before stopping it. Log in mk2/r.log."""
import json,os,shutil,subprocess,sys,time
H=os.path.dirname(os.path.abspath(__file__)); A=os.path.abspath(sys.argv[1]); S=sys.argv[2]; DSP=sys.argv[3] if len(sys.argv)>3 else ':79'
BIN=os.environ.get('RTBIN') or os.path.abspath(A+'/../mkbin/bin/rt'); W=A+'/mk2'
for d in ('stub','run','xdg/home'): os.makedirs(f'{W}/{d}',exist_ok=True)
for n in 'xdg-open gio wpctl pkexec pw-dump pw-cli pw-link zenity kdialog nautilus osascript'.split():
    p=f'{W}/stub/{n}'; open(p,'w').write('#!/bin/sh\nexit 1\n'); os.chmod(p,0o755)
ENV=dict(os.environ,DISPLAY=DSP,XDG_SESSION_TYPE='x11',XDG_RUNTIME_DIR=W+'/run',XDG_DATA_HOME=W+'/xdg/data',XDG_CONFIG_HOME=W+'/xdg/config',XDG_CACHE_HOME=W+'/xdg/cache',PATH=W+'/stub:'+os.environ['PATH'],DBUS_SESSION_BUS_ADDRESS='unix:path=/nonexistent',
 __NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',LSD_TOUR_W='1280',LSD_TOUR_H='720',LSD_FAKE_TWISTER='1',LSD_MONKEY='1',JAVA_OPTS=f'-Duser.home={W}/xdg/home -Djava.awt.headless=true')
ENV.pop('WAYLAND_DISPLAY',None)
L=W+'/cwd/library'; shutil.rmtree(W+'/cwd',ignore_errors=True); os.makedirs(W+'/cwd'); shutil.copytree(A+'/libbase',L,symlinks=True)
if os.path.exists(A+'/fuzzlib'): shutil.copytree(A+'/fuzzlib',L,dirs_exist_ok=True)
dur=json.load(open(S))['durationSec']
xv=subprocess.Popen(['Xvfb',DSP,'-screen','0','1280x720x24'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1)
try:
    with open(W+'/r.log','w') as lg:
        p=subprocess.Popen(['unshare','-Urnm','sh','-c','mount -t tmpfs tmpfs /dev/snd && exec "$0" "$@"',BIN,f'--tour={S}',f'--tour-out={W}/r.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=0.5','--tour-encoder=libx264','--tour-no-hashes'],cwd=W+'/cwd',env=ENV,stdout=lg,stderr=subprocess.STDOUT)
        t0=time.time()
        try: rc=p.wait(timeout=dur*3); print('rc',rc,'in',round(time.time()-t0),'s')
        except subprocess.TimeoutExpired:
            # The java child of the launcher script gets the SIGQUIT.
            kids=subprocess.run(['pgrep','-P',str(p.pid)],capture_output=True,text=True).stdout.split()
            kids+= [g for k in kids for g in subprocess.run(['pgrep','-P',k],capture_output=True,text=True).stdout.split()]
            for k in kids+[str(p.pid)]:
                for _ in range(2): subprocess.run(['kill','-QUIT',k]); time.sleep(3)
            time.sleep(2); p.kill(); print('HUNG; thread dumps in',W+'/r.log')
finally: xv.kill()
