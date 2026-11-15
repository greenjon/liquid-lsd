#!/usr/bin/env python3
"""monkey.py <work dir> <first seed> <count> [seconds]: random clicks, drags, keys, wheel and Twister messages on a throwaway library copy.
Isolated: no network (unshare -n), an invisible X server, a PATH that stubs everything that reaches outside (browser, file manager, volume, pkexec, dialogs).
Writes results/monkey.txt; keeps the script and log of any seed with a finding in mk/out/."""
import json,os,random,re,shutil,subprocess,sys,time
H=os.path.dirname(os.path.abspath(__file__)); A,first,count=sys.argv[1],int(sys.argv[2]),int(sys.argv[3]); SECS=int(sys.argv[4]) if len(sys.argv)>4 else 90
BIN=os.path.abspath(A+'/../mkbin/bin/rt'); W=A+'/mk'
for d in ('stub','out','run','xdg/home'): os.makedirs(f'{W}/{d}',exist_ok=True)
for n in 'xdg-open gio wpctl pkexec pw-dump pw-cli pw-link zenity kdialog nautilus osascript'.split():
    p=f'{W}/stub/{n}'; open(p,'w').write(f'#!/bin/sh\necho "$0 $*" >> "{W}/stub/calls.log"\nexit 1\n'); os.chmod(p,0o755)
ENV=dict(os.environ,DISPLAY=':78',XDG_SESSION_TYPE='x11',XDG_RUNTIME_DIR=W+'/run',XDG_DATA_HOME=W+'/xdg/data',XDG_CONFIG_HOME=W+'/xdg/config',XDG_CACHE_HOME=W+'/xdg/cache',PATH=W+'/stub:'+os.environ['PATH'],DBUS_SESSION_BUS_ADDRESS='unix:path=/nonexistent',
 __NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',LSD_TOUR_W='1280',LSD_TOUR_H='720',LSD_FAKE_TWISTER='1',LSD_MONKEY='1',JAVA_OPTS=f'-Duser.home={W}/xdg/home -Djava.awt.headless=true')
ENV.pop('WAYLAND_DISPLAY',None)
KEYS='q shift+q enter escape tab space backspace delete up down ctrl+z ctrl+s ctrl+f ctrl+a a b c m r x 1 2 3'.split()
TXT=['tt','kifs','a/b','..','   ','Ω','x'*80]
def script(seed):
    r=random.Random(seed); st=[]; t=1.0
    SRC='kifs metaballs voronoi_cells mandala fractal_flame reaction_diffusion quasicrystal'.split(); FX='bloom rgb_split kaleidoscope feedback'.split()
    def add(a,**k):
        nonlocal t; t+=r.choice((.02,.04,.06,.12)); st.append(dict(t=round(t,3),action=a,**k))
    st.append(dict(t=.3,action='load_source',deck='A',target=r.choice(SRC))); st.append(dict(t=.3,action='load_source',deck='B',target=r.choice(SRC)))
    n=0
    while t<SECS-3:
        n+=1; x=r.random(); px,py=r.randint(0,1279),r.randint(0,719)
        if x<.42: add('move',x=px,y=py,durationSec=.04); add('click')
        elif x<.50: add('move',x=px,y=py,durationSec=.04); add('rclick')
        elif x<.60: add('move',x=px,y=py,durationSec=.04); t+=.1; add('drag',x=r.randint(0,1279),y=r.randint(0,719),durationSec=.3); t+=.4
        elif x<.70: add('key',target=r.choice(KEYS))
        elif x<.74: add('type',target=r.choice(TXT)); t+=1.2
        elif x<.78: add('scroll',value=r.choice((-3,-1,1,3)))
        elif x<.92:
            k=r.randint(0,15)+16*r.randint(0,2); kind=r.random()
            if kind<.6:
                for _ in range(r.randint(1,4)): add('midi',target=f'0:{k}:{r.choice((63,65))}')
            elif kind<.8: add('midi',target=f'1:{k}:127'); add('midi',target=f'1:{k}:0')
            else:
                c=r.choice((8,9,10,11,12,13))+6*r.randint(0,2); add('midi',target=f'3:{c}:127'); add('midi',target=f'3:{c}:0')
        elif x<.95:
            m=r.choice(('ctrl','shift','alt')); add('mod',target=m,value=1); add('click'); add('mod',target=m,value=0)
        elif x<.97: add('set_page',target=r.choice(('ab','bgpv','mixer')))
        elif x<.985: add('library_mode',target=r.choice(('FULL','HALF')))
        else: add('fx_filter',deck=r.choice('AB'),slot=r.randint(0,2),target=r.choice(FX))
        if n%120==0: add('audit_state',target=f'm{n}')
    add('audit_state',target='end')
    return dict(seed=7,bpm=128.0,durationSec=SECS,tooltips=True,steps=st),n
def fresh():
    L=W+'/cwd/library'; shutil.rmtree(W+'/cwd',ignore_errors=True); os.makedirs(W+'/cwd'); shutil.copytree(A+'/libbase',L,symlinks=True)
    if os.path.exists(A+'/fuzzlib'): shutil.copytree(A+'/fuzzlib',L,dirs_exist_ok=True)
xv=subprocess.Popen(['Xvfb',':78','-screen','0','1280x720x24'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1)
res=open(H+'/results/monkey.txt','a')
try:
  for s in range(first,first+count):
    fresh(); sc,n=script(s); f=f'{W}/s.json'; json.dump(sc,open(f,'w')); log=f'{W}/s.log'; t0=time.time()
    with open(log,'w') as lg:
        p=subprocess.Popen(['unshare','-Urn',BIN,f'--tour={f}',f'--tour-out={W}/s.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=0.5','--tour-encoder=libx264','--tour-no-hashes'],cwd=W+'/cwd',env=ENV,stdout=lg,stderr=subprocess.STDOUT)
        try: rc=p.wait(timeout=SECS*6+120)
        except subprocess.TimeoutExpired: p.kill(); rc='HUNG'
    txt=open(log,errors='replace').read(); done='TOUR_DONE' in txt
    ex=sorted(set(re.findall(r'^(?:Exception in thread[^\n]*|[\w.]+(?:Exception|Error)\b[^\n]{0,120}|.*\bERROR\b[^\n]{0,140})',txt,re.M)))
    mem=re.findall(r'mem: fbo=(\d+) mb=(\S+) heap=(\d+)',txt)
    line=f"seed {s}: actions {n}, rc {rc}, done {done}, {round(time.time()-t0)} s, fbo {mem[0][0] if mem else '?'}->{mem[-1][0] if mem else '?'}, exits {txt.count('TOUR_EXIT_REQUEST')}, warns {txt.count('TOUR_WARN')}, findings {len(ex)}"
    print(line,flush=True); res.write(line+'\n')
    for e in ex[:12]: print('    ',e[:200],flush=True); res.write('    '+e[:200]+'\n')
    res.flush()
    if rc!=0 or not done or ex:
        shutil.copy(log,f'{W}/out/s{s}.log'); shutil.copy(f,f'{W}/out/s{s}.json')
        if os.path.exists(W+'/s.mp4'): shutil.copy(W+'/s.mp4',f'{W}/out/s{s}.mp4')
finally: xv.kill()
