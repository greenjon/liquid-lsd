#!/usr/bin/env python3
"""leak.py (round 5: LEAK_ZIP=<release zip> runs that release through its own launcher) <work dir> <name> <minutes> <display> <java opts...>: same setup as rtsoak.py, with chosen JVM options and a split of RSS by mapping. results/leak_<name>.txt, GC log beside the run."""
import glob,json,os,random,shutil,signal,subprocess,sys,time
H=os.path.dirname(os.path.abspath(__file__)); A,name,mins,dsp=sys.argv[1],sys.argv[2],float(sys.argv[3]),sys.argv[4]; opts=sys.argv[5:]; S=f'{A}/leak_{name}'; BIN=os.path.abspath(A+'/../'+os.environ.get('LEAK_BIN','soakbin')+'/bin/rt')
shutil.rmtree(S,ignore_errors=True)
for d in ('cwd','run','stub','xdg/home'): os.makedirs(f'{S}/{d}')
ZIP=os.environ.get('LEAK_ZIP'); CWD=S+'/cwd'
if ZIP:
    subprocess.run(['unzip','-q',ZIP,'-d',S+'/app'],check=True); CWD=glob.glob(S+'/app/*/run-linux.sh')[0].rsplit('/',1)[0]; BIN=CWD+'/run-linux.sh'
    shutil.rmtree(CWD+'/library',ignore_errors=True); os.rmdir(S+'/cwd')
shutil.copytree(A+'/libbase',CWD+'/library')
for n in 'xdg-open gio wpctl pkexec pw-dump pw-cli zenity kdialog'.split():
    p=f'{S}/stub/{n}'; open(p,'w').write('#!/bin/sh\nexit 1\n'); os.chmod(p,0o755)
L=CWD+'/library'; ps=sorted(glob.glob(L+'/presets/**/*.lsd',recursive=True)); random.Random(4).shuffle(ps)
Q=os.environ.get('LEAK_QUEUE','')
if Q: ps=[x for x in ps if (('flame' in open(x,errors='replace').read().lower())==(Q=='flame'))]; print(Q,len(ps))
d=json.load(open(L+'/last_session.json') if os.path.exists(L+'/last_session.json') else open('library/last_session.json'))
d['queue']=['${LIBRARY}/'+os.path.relpath(p,L) for p in ps[:60]]; d['activeIndex']=0; d['isAutoVJEnabled']=True; d['isRepeatEnabled']=True
q=d['mixer']['queueNext']; m=dict(q['modulators'][0]); m.update(bypassed=False,waveform='SINE',subdivision=32.0,genUnit='BEAT',depth=1.0,dcOffset=0.0,hold=0.0)
for k in ('depthMin','depthMax','dcOffsetMin','dcOffsetMax'): m.pop(k,None)
q['modulators']=[m]; q['baseValue']=0.0
if os.environ.get('LEAK_IDLE'): d['isAutoVJEnabled']=False; m['bypassed']=True
json.dump(d,open(L+'/last_session.json','w'))
J=' '.join([f'-Duser.home={S}/xdg/home',f'-Xlog:gc:file={S}/gc.log']+opts)
ENV=dict(os.environ,DISPLAY=dsp,XDG_SESSION_TYPE='x11',XDG_RUNTIME_DIR=S+'/run',XDG_DATA_HOME=S+'/xdg/data',XDG_CONFIG_HOME=S+'/xdg/config',XDG_CACHE_HOME=S+'/xdg/cache',PATH=S+'/stub:'+os.environ['PATH'],DBUS_SESSION_BUS_ADDRESS='unix:path=/nonexistent',__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',JAVA_OPTS=J)
if ZIP: ENV['JAVA_TOOL_OPTIONS']=J
ENV.pop('WAYLAND_DISPLAY',None)
APP=dict(ENV)
if os.environ.get('LEAK_SHIM'): APP.update(LD_PRELOAD=os.environ['LEAK_SHIM'],LEAK_OUT=S+'/sites.txt')
if ZIP:
    for k in ('__NV_PRIME_RENDER_OFFLOAD','__GLX_VENDOR_LIBRARY_NAME'): APP[k]=ENV[k]
if os.environ.get('LEAK_INTEL'):
    for k in ('__NV_PRIME_RENDER_OFFLOAD','__GLX_VENDOR_LIBRARY_NAME'): APP.pop(k,None)
WL=os.environ.get('LEAK_WAYLAND')
if WL:
    # A real compositor with no output on the user's screen: GLFW then takes the normal Wayland path.
    M=dict(os.environ,XDG_RUNTIME_DIR=S+'/run'); M.pop('WAYLAND_DISPLAY',None); M.pop('DISPLAY',None)
    os.chmod(S+'/run',0o700)
    xv=subprocess.Popen(['dbus-run-session','--','mutter','--headless','--wayland','--no-x11','--virtual-monitor','1280x720','--wayland-display',WL],env=M,stdout=open(S+'/mutter.txt','w'),stderr=subprocess.STDOUT,start_new_session=True); time.sleep(5)
    APP.update(WAYLAND_DISPLAY=WL,XDG_SESSION_TYPE='wayland'); APP.pop('DISPLAY',None)
    if os.environ.get('LEAK_XWL'):
        # The same compositor, the app through XWayland: GLX with NVIDIA on a real path.
        M2=dict(M,WAYLAND_DISPLAY=WL); xw=subprocess.Popen(['Xwayland',dsp,'-noreset','-fullscreen','-geometry','1280x720'],env=M2,stdout=open(S+'/xwl.txt','w'),stderr=subprocess.STDOUT,start_new_session=True); time.sleep(3)
        APP.update(DISPLAY=dsp,XDG_SESSION_TYPE='x11'); APP.pop('WAYLAND_DISPLAY',None)
else:
    xv=subprocess.Popen(['Xvfb',dsp,'-screen','0','1280x720x24'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1)
lg=open(S+'/log.txt','w'); p=subprocess.Popen(['unshare','-Urn',BIN,'--no-audio','--window','1280x720'],cwd=CWD,env=APP,stdout=lg,stderr=subprocess.STDOUT,start_new_session=True)
res=open(f'{H}/results/leak_{name}.txt','w'); t0=time.time(); why='time'
res.write(f'opts {" ".join(opts)}\n')
def java():
    for q in os.listdir('/proc'):
        if q.isdigit():
            try:
                if os.readlink(f'/proc/{q}/cwd')==CWD and open(f'/proc/{q}/comm').read().strip()=='java': return q
            except OSError: pass
def split(q):
    big=[];cur=None;out={}
    for l in open(f'/proc/{q}/smaps'):
        f=l.split()
        if '-' in f[0] and len(f)>=5 and not f[0].endswith(':'): cur=f[5] if len(f)>5 else 'anon'
        elif f[0]=='Rss:':
            k=cur if cur=='anon' or cur.startswith('[') else ('nvidia' if 'nvidia' in cur or 'NVIDIA' in cur else ('dev' if cur.startswith('/dev') or 'memfd' in cur else 'file'))
            out[k]=out.get(k,0)+int(f[1])//1024
    return ' '.join(f'{k}={v}' for k,v in sorted(out.items(),key=lambda x:-x[1])[:6])
def gc():
    try: ls=open(S+'/gc.log',errors='replace').read().splitlines()[-400:]
    except OSError: return ''
    ls=[l.split(']')[-1].strip() for l in ls if '->' in l]
    return ls[-1][:90] if ls else ''
try:
  while time.time()-t0<mins*60:
    time.sleep(120)
    if p.poll() is not None: why=f'app exited {p.returncode}'; break
    if any(open(b+'/status').read().strip()=='Discharging' for b in glob.glob('/sys/class/power_supply/BAT*')): why='on battery'; break
    q=java()
    if not q: continue
    if not os.environ.get('LEAK_WAYLAND'): subprocess.run(['import','-window','root',S+'/last.png'],env=dict(os.environ,DISPLAY=dsp))
    rss=[int(l.split()[1])//1024 for l in open(f'/proc/{q}/status') if l.startswith('VmRSS')][0]
    res.write(f"{round((time.time()-t0)/60)} min rss {rss} | {split(q)} | {gc()}\n"); res.flush()
finally:
    res.write(f'stopped: {why}\n'); res.close()
    try: os.killpg(p.pid,signal.SIGTERM)
    except ProcessLookupError: pass
    time.sleep(8)
    try: os.killpg(xv.pid,signal.SIGTERM)
    except (ProcessLookupError,PermissionError): xv.kill()
