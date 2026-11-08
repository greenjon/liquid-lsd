#!/usr/bin/env python3
"""labrun2.py <work dir> <plan.json>: run inside pwlab.sh. The test build in real time with LSD_LIVE_PROBE, fed by the private PipeWire.
plan: list of steps {"play": file, "secs": n} | {"silence": n} | {"restart_pw": n} (PipeWire down for n s). Writes lab2/live.txt (numbers only)."""
import os,shutil,subprocess,sys,time,json,signal
A,plan=sys.argv[1],json.load(open(sys.argv[2])); S=A+'/lab2'; BIN=os.path.abspath(A+'/../rt/build/install/rt/bin/rt'); D=os.environ['PWLAB_DIR']
shutil.rmtree(S,ignore_errors=True); os.makedirs(S+'/shots'); shutil.copytree(A+'/libbase',S+'/cwd/library')
open(S+'/cwd/lsd-preferences.properties','w').write('clockSource='+os.environ.get('LAB_CLOCK','AUDIO_TRACKER')+'\n'+os.environ.get('LAB_PREFS','')+'\n')
dsp=':71'; xv=subprocess.Popen(['Xvfb',dsp,'-screen','0','1280x720x24'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1)
J=f'-Duser.home={S}/home --enable-native-access=ALL-UNNAMED -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g'
E=dict(os.environ,DISPLAY=dsp,XDG_SESSION_TYPE='x11',XDG_DATA_HOME=S+'/xdg/data',XDG_CACHE_HOME=S+'/xdg/cache',__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',__GL_THREADED_OPTIMIZATIONS='0',JAVA_OPTS=J,LSD_LIVE_PROBE='1')
E.pop('WAYLAND_DISPLAY',None)
def sh(c): return subprocess.run(c,shell=True,capture_output=True,text=True,env=E).stdout
def sink(): subprocess.run(['pw-cli','create-node','adapter','{ factory.name=support.null-audio-sink node.name=labsink media.class=Audio/Sink object.linger=true audio.position=[FL FR] }'],env=E,capture_output=True); time.sleep(1)
sink()
log=open(S+'/app.log','w'); app=subprocess.Popen([BIN,'--window','1280x720'],cwd=S+'/cwd',env=E,stdout=log,stderr=subprocess.STDOUT)
mark=open(S+'/marks.txt','w')
def note(x): mark.write(f'{time.monotonic():.1f} {x}\n'); mark.flush()
def link():
    for c in ('FL','FR'):
        sh(f'pw-link labsink:monitor_{c} lsd:input_{1 if c=="FL" else 2}')
time.sleep(20); link(); note('start')
pl=None; n=0
for st in plan:
    if 'play' in st:
        note(f"play {st.get('label','')}"); pl=subprocess.Popen(['pw-play','--target','labsink',st['play']],env=E,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        t0=time.time()
        while time.time()-t0<st['secs']:
            time.sleep(5); n+=1; subprocess.run(['import','-window','root',f'{S}/shots/s{n:03d}.png'],env=E)
            if pl.poll() is not None: pl=subprocess.Popen(['pw-play','--target','labsink',st['play']],env=E,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        pl.terminate(); pl=None
    elif 'sh' in st: time.sleep(st.get('wait',0)); note('sh '+st['sh'][:60]+' => '+sh(st['sh']).replace('\n',' | ')[:1500])
    elif 'silence' in st: note('silence'); time.sleep(st['silence'])
    elif 'restart_pw' in st:
        note('pw down')
        for f in ('wp.pid','pw.pid'):
            try: os.kill(int(open(f'{D}/{f}').read()),signal.SIGTERM)
            except Exception as e: note(f'kill {f} {e}')
        time.sleep(st['restart_pw'])
        pw=subprocess.Popen(['pipewire'],env=E,stdout=open(D+'/pipewire2.log','w'),stderr=subprocess.STDOUT); time.sleep(1)
        wp=subprocess.Popen(['wireplumber'],env=E,stdout=open(D+'/wireplumber2.log','w'),stderr=subprocess.STDOUT); time.sleep(2)
        open(f'{D}/pw.pid','w').write(str(pw.pid)); open(f'{D}/wp.pid','w').write(str(wp.pid))
        sink(); note('pw up'); time.sleep(st.get('wait',15)); note('ports '+sh('pw-link -i').replace('\n',' ')); link(); note('relinked '+sh('pw-link -l').replace('\n',' ')[:300])
note('end'); app.terminate(); time.sleep(4); app.kill(); xv.kill()
