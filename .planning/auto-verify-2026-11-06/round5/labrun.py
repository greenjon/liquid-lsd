#!/usr/bin/env python3
"""labrun.py <work dir> <track> <seconds>: run inside pwlab.sh. The test build in real time, music played into its JACK inputs through the
private PipeWire. Screenshots every 10 s, port list, logs. Only numbers and pictures are kept."""
import os,shutil,subprocess,sys,time,glob
A,track,secs=sys.argv[1],sys.argv[2],float(sys.argv[3]); S=A+'/lab'; BIN=os.path.abspath(A+'/../rt/build/install/rt/bin/rt')
shutil.rmtree(S+'/cwd',ignore_errors=True); os.makedirs(S+'/shots',exist_ok=True); shutil.copytree(A+'/libbase',S+'/cwd/library')
for f in glob.glob(S+'/shots/*'): os.remove(f)
dsp=':70'; xv=subprocess.Popen(['Xvfb',dsp,'-screen','0','1280x720x24'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1)
J=f'-Duser.home={S}/home --enable-native-access=ALL-UNNAMED -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g'
E=dict(os.environ,DISPLAY=dsp,XDG_SESSION_TYPE='x11',XDG_DATA_HOME=S+'/xdg/data',XDG_CACHE_HOME=S+'/xdg/cache',__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',__GL_THREADED_OPTIMIZATIONS='0',JAVA_OPTS=J)
E.pop('WAYLAND_DISPLAY',None)
subprocess.run(['pw-cli','create-node','adapter','{ factory.name=support.null-audio-sink node.name=labsink media.class=Audio/Sink object.linger=true audio.position=[FL FR] }'],env=E,capture_output=True); time.sleep(1)
log=open(S+'/app.log','w'); app=subprocess.Popen([BIN,'--window','1280x720'],cwd=S+'/cwd',env=E,stdout=log,stderr=subprocess.STDOUT)
def sh(c): return subprocess.run(c,shell=True,capture_output=True,text=True,env=E).stdout
out=open(S+'/lab.txt','w')
time.sleep(20); out.write('ports before play:\n'+sh('pw-link -o; echo --; pw-link -i; echo --; pw-link -l')+'\n'); out.flush()
pl=subprocess.Popen(['pw-play','--target','labsink','--latency=256',track],env=E,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1.5)
outs=[l.strip() for l in sh('pw-link -o').splitlines() if l.startswith('labsink:monitor')]
ins=[l.strip() for l in sh('pw-link -i').splitlines() if 'lsd' in l.lower()]
out.write(f'play ports {outs}\napp ports {ins}\n')
for o,i in zip(sorted(outs),sorted(ins)): out.write(sh(f'pw-link "{o}" "{i}" 2>&1'))
out.write('links:\n'+sh('pw-link -l')+'\n'); out.flush()
t0=time.time(); n=0
while time.time()-t0<secs:
    time.sleep(10); n+=1; subprocess.run(['import','-window','root',f'{S}/shots/s{n:03d}.png'],env=E)
    if pl.poll() is not None: pl=subprocess.Popen(['pw-play','--target','labsink','--latency=256',track],env=E,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1.5); [sh(f'pw-link "{o}" "{i}"') for o,i in zip(sorted(outs),sorted(ins))]
out.write('end links:\n'+sh('pw-link -l')+'\n'+sh('pw-top -b -n 2 2>/dev/null | tail -12')+'\n'); out.close()
pl.terminate(); app.terminate(); time.sleep(4); app.kill(); xv.kill()
