#!/usr/bin/env python3
"""rtsoak.py <work dir> <hours>: the real app in real time, in an invisible X server with no network, Auto VJ running a queue of presets. Samples memory every 5 min. Stops if the laptop leaves mains power. results/rtsoak.txt."""
import glob,json,os,random,shutil,signal,subprocess,sys,time
H=os.path.dirname(os.path.abspath(__file__)); A,hours=sys.argv[1],float(sys.argv[2]); S=A+'/rts'; BIN=os.path.abspath(A+'/../soakbin/bin/rt')
shutil.rmtree(S,ignore_errors=True)
for d in ('cwd','run','stub','xdg/home'): os.makedirs(f'{S}/{d}')
shutil.copytree(A+'/libbase',S+'/cwd/library')
for n in 'xdg-open gio wpctl pkexec pw-dump pw-cli zenity kdialog'.split():
    p=f'{S}/stub/{n}'; open(p,'w').write('#!/bin/sh\nexit 1\n'); os.chmod(p,0o755)
L=S+'/cwd/library'; ps=sorted(glob.glob(L+'/presets/**/*.lsd',recursive=True)); random.Random(4).shuffle(ps)
d=json.load(open('library/last_session.json'))
d['queue']=['${LIBRARY}/'+os.path.relpath(p,L) for p in ps[:60]]; d['activeIndex']=0; d['isAutoVJEnabled']=True; d['isRepeatEnabled']=True
# Queue Next on a slow beat LFO, as a user sets it up: one step every 32 beats.
q=d['mixer']['queueNext']; m=dict(q['modulators'][0]); m.update(bypassed=False,waveform='SINE',subdivision=32.0,genUnit='BEAT',depth=1.0,dcOffset=0.0,hold=0.0); m.pop('depthMin',None); m.pop('depthMax',None); m.pop('dcOffsetMin',None); m.pop('dcOffsetMax',None); q['modulators']=[m]; q['baseValue']=0.0
json.dump(d,open(L+'/last_session.json','w'))
ENV=dict(os.environ,DISPLAY=':80',XDG_SESSION_TYPE='x11',XDG_RUNTIME_DIR=S+'/run',XDG_DATA_HOME=S+'/xdg/data',XDG_CONFIG_HOME=S+'/xdg/config',XDG_CACHE_HOME=S+'/xdg/cache',PATH=S+'/stub:'+os.environ['PATH'],DBUS_SESSION_BUS_ADDRESS='unix:path=/nonexistent',__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',JAVA_OPTS=f'-Duser.home={S}/xdg/home')
ENV.pop('WAYLAND_DISPLAY',None)
xv=subprocess.Popen(['Xvfb',':80','-screen','0','1280x720x24'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1)
lg=open(S+'/log.txt','w'); p=subprocess.Popen(['unshare','-Urn',BIN,'--no-audio','--window','1280x720'],cwd=S+'/cwd',env=ENV,stdout=lg,stderr=subprocess.STDOUT,start_new_session=True)
res=open(H+'/results/rtsoak.txt','a'); t0=time.time(); why='time'
def java():
    for q in os.listdir('/proc'):
        if q.isdigit():
            try:
                if os.readlink(f'/proc/{q}/cwd')==S+'/cwd' and open(f'/proc/{q}/comm').read().strip()=='java': return q
            except OSError: pass
try:
  while time.time()-t0<hours*3600:
    time.sleep(300)
    if p.poll() is not None: why=f'app exited {p.returncode}'; break
    if any(open(b+'/status').read().strip()=='Discharging' for b in glob.glob('/sys/class/power_supply/BAT*')): why='on battery'; break
    q=java(); rss=0
    if q: rss=[int(l.split()[1])//1024 for l in open(f'/proc/{q}/status') if l.startswith('VmRSS')][0]
    n=round((time.time()-t0)/60); shot=f'{S}/s{n}.png'; subprocess.run(['import','-window','root',shot],env=dict(os.environ,DISPLAY=':80'))
    if n%30<5: shutil.copy(shot,f'{H}/results/rtsoak_last.png')
    log=open(S+'/log.txt',errors='replace').read()
    res.write(f"{n} min rss {rss} MB exceptions {log.count('Exception')} toasts-or-loads {log.count('Loaded preset')}\n"); res.flush()
finally:
    res.write(f'stopped: {why}\n'); res.close()
    try: os.killpg(p.pid,signal.SIGTERM)
    except ProcessLookupError: pass
    time.sleep(3); xv.kill()
