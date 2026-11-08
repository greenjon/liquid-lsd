#!/usr/bin/env python3
"""corrupt.py <work dir>: starts the real app with one of its own state files damaged (cut short as by a power cut, random
bytes, or empty) and checks it comes up: alive after 20 s, drawing, errors listed. No network, empty /dev/snd.
results/corrupt.txt; screenshots of anything that failed in results/corrupt/."""
import os,random,re,shutil,subprocess,sys,time
H=os.path.dirname(os.path.abspath(__file__)); A=os.path.abspath(sys.argv[1]); BIN=os.path.abspath(A+'/../rt/build/install/rt/bin/rt')
S=A+'/corrupt'; CWD=S+'/cwd'; HOME=S+'/home'; PRIS=S+'/pristine'; DSP=':74'
if not os.path.exists(CWD):
    shutil.copytree(A+'/bcast/cwd',CWD,symlinks=True)
    p=CWD+'/lsd-preferences.properties'; open(p,'w').write(''.join(l for l in open(p) if not l.startswith('broadcast.')))
os.makedirs(HOME+'/.liquid-lsd',exist_ok=True)
for d in ('run','cfg','data','cache'): os.makedirs(f'{S}/{d}',exist_ok=True)
# Real-looking contents for the files a fresh copy doesn't have.
seed={'library/src_favorites.json':'["mandala","voronoi_cells"]','library/fx_shortlist.json':'["bloom","retro_crt"]',
      HOME+'/.liquid-lsd/keybindings.json':'{"version":1,"bindings":{"parameters.save_deck":"Ctrl+S"}}',
      HOME+'/.liquid-lsd/source-notes.json':'{"mandala":"lovely at 128"}'}
for k,v in seed.items():
    p=k if k.startswith('/') else CWD+'/'+k
    if not os.path.exists(p): open(p,'w').write(v)
T=['lsd-preferences.properties','imgui.ini','library/last_session.json','library/preset.json','library/src_recent.json','library/isf_directories.json',
   'library/midi/default.json','library/src_favorites.json','library/fx_shortlist.json',HOME+'/.liquid-lsd/keybindings.json',HOME+'/.liquid-lsd/source-notes.json']
def path(t): return t if t.startswith('/') else CWD+'/'+t
os.makedirs(PRIS,exist_ok=True)
for t in T:
    b=PRIS+'/'+t.replace('/','_')
    if not os.path.exists(b): shutil.copy(path(t),b)
os.makedirs(H+'/results/corrupt',exist_ok=True)
out=open(H+'/results/corrupt.txt','w')
ENV=dict(os.environ,DISPLAY=DSP,XDG_SESSION_TYPE='x11',XDG_RUNTIME_DIR=S+'/run',XDG_CONFIG_HOME=S+'/cfg',XDG_DATA_HOME=S+'/data',XDG_CACHE_HOME=S+'/cache',
 __NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',__GL_THREADED_OPTIMIZATIONS='0',JAVA_OPTS=f'-Duser.home={HOME} -Xmx2g -XX:+UseZGC')
ENV.pop('WAYLAND_DISPLAY',None)
xv=subprocess.Popen(['Xvfb',DSP,'-screen','0','1280x720x24'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1)
random.seed(3)
try:
  only=sys.argv[2:]
  for t in T:
    if only and not any(o in t for o in only): continue
    good=open(PRIS+'/'+t.replace('/','_'),'rb').read()
    for mode in ('cut','junk','empty'):
        for x in T: shutil.copy(PRIS+'/'+x.replace('/','_'),path(x))
        bad={'cut':good[:max(1,len(good)*2//5)],'junk':bytes(random.randrange(256) for _ in range(300)),'empty':b''}[mode]
        open(path(t),'wb').write(bad)
        log=S+'/app.log'
        with open(log,'w') as lg:
            p=subprocess.Popen(['unshare','-Urnm','sh','-c','mount -t tmpfs tmpfs /dev/snd && exec "$0" "$@"',BIN,'--no-audio','--window','1280x720'],cwd=CWD,env=ENV,stdout=lg,stderr=subprocess.STDOUT)
            time.sleep(20); alive=p.poll() is None
            shot=S+'/shot.png'; subprocess.run(['ffmpeg','-v','error','-y','-f','x11grab','-video_size','1280x720','-i',DSP,'-frames:v','1',shot])
            mean=subprocess.run(['ffprobe','-v','error','-f','lavfi','-i',f'movie={shot},signalstats','-show_entries','frame_tags=lavfi.signalstats.YAVG','-of','csv=p=0'],capture_output=True,text=True).stdout.strip()
            if alive:
                p.terminate()
                try: p.wait(15)
                except subprocess.TimeoutExpired: p.kill(); p.wait()
        txt=open(log,errors='replace').read()
        name=os.path.basename(t)
        errs=sorted(set(re.sub(r'^\S+ ','',l)[:170] for l in txt.splitlines() if ('Exception in thread' in l or ' ERROR ' in l) and 'GLDebug' not in l))
        warns=[re.sub(r'^\S+ ','',l)[:170] for l in txt.splitlines() if ' WARN ' in l and name.split('.')[0] in l][:2]
        try: ymean=float(mean or 0)
        except ValueError: ymean=0
        ok=alive and ymean>8
        line=f"{'OK ' if ok else 'BAD'} {t.replace(HOME,'~')} [{mode}]: alive={alive} picture={ymean:.0f} errors={len(errs)}"
        print(line,flush=True); out.write(line+'\n')
        for e in errs[:4]+warns: out.write('     '+e+'\n')
        out.flush()
        if not ok or errs: shutil.copy(shot,f"{H}/results/corrupt/{name}_{mode}.png")
finally:
    xv.kill()
    for x in T: shutil.copy(PRIS+'/'+x.replace('/','_'),path(x))
