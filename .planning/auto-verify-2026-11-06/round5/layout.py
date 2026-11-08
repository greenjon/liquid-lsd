#!/usr/bin/env python3
"""layout.py <work dir>: the round 4 views at several window sizes. From each view's widget rectangles: pairs that overlap
without one holding the other (two buttons on top of each other), and widgets that run past the window. Frames kept for
anything flagged. results/layout.txt"""
import json,os,re,shutil,subprocess,sys,importlib.util
H=os.path.dirname(os.path.abspath(__file__)); A=os.path.abspath(sys.argv[1]); BIN=A+'/../rt/build/install/rt/bin/rt'; CWD=A+'/ccwd'
if not os.path.exists(CWD): os.makedirs(CWD); shutil.copytree(A+'/libbase',CWD+'/library')
src=open(H+'/../round4/contrast.py').read(); V=eval(src[src.index('V={'):src.index('}\n',src.index('V={'))+1].split('=',1)[1])
os.makedirs(H+'/results/layout',exist_ok=True)
out=open(H+'/results/layout.txt','w')
def area(r): return max(0,r[2]-r[0])*max(0,r[3]-r[1])
def inter(a,b): return area((max(a[0],b[0]),max(a[1],b[1]),min(a[2],b[2]),min(a[3],b[3])))
for W,Hh in ((1280,720),(1920,1080)):
  env=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',__GL_THREADED_OPTIMIZATIONS='0',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache',LSD_TOUR_W=str(W),LSD_TOUR_H=str(Hh))
  for name,steps in V.items():
    t=.3; st=[dict(t=.2,action='load_source',deck='A',target='mandala'),dict(t=.2,action='load_source',deck='B',target='voronoi_cells'),dict(t=.25,action='load_source',deck='BG',target='metaballs')]
    for s in steps:
        s=dict(s); t+=s.pop('dt',.4); st.append(dict(t=round(t,2),**s))
    t+=.8; st.append(dict(t=round(t,2),action='audit_rects',target=name)); snap=t+.05
    json.dump(dict(seed=7,bpm=128.0,durationSec=t+.5,tooltips=False,steps=st),open(f'{A}/scripts/lay.json','w'))
    p=subprocess.run([BIN,f'--tour={A}/scripts/lay.json',f'--tour-out={A}/out/lay.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=1','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=env,capture_output=True,text=True)
    m=re.search(r'TOUR_RECTS \S+ t=\S+ (.*)',p.stdout)
    if not m: out.write(f'{W}x{Hh} {name}: no data\n'); continue
    R=[]
    for it in m[1].split('\t'):
        k,_,r=it.rpartition('=')
        try: R.append((k,tuple(map(int,r.split(',')))))
        except ValueError: pass
    json.dump(R,open(f'{H}/results/layout/{W}x{Hh}_{name}.json','w'))
    # Rows scrolled out of a list are still registered with off-screen rectangles: compare only what is on screen.
    R=[(k,r) for k,r in R if area(r)>=16 and r[0]>=0 and r[1]>=0 and r[2]<=W and r[3]<=Hh]
    flags=[]
    for i in range(len(R)):
        for j in range(i+1,len(R)):
            (k1,a),(k2,b)=R[i],R[j]
            x=inter(a,b)
            if not x: continue
            small=min(area(a),area(b))
            if x>=.9*small: continue          # one holds the other (a cell and its button, a row and its knob)
            if x>=.25*small: flags.append(f'overlap {x*100//small}%: {k1} {a} / {k2} {b}')
    out.write(f'== {W}x{Hh} {name}: {len(R)} widgets, {len(flags)} flagged\n'+''.join(f'   {f}\n' for f in flags[:40])); out.flush()
    if flags: subprocess.run(['ffmpeg','-v','error','-y','-ss',str(snap),'-i',f'{A}/out/lay.mp4','-frames:v','1',f'{H}/results/layout/{W}x{Hh}_{name}.png'])
    print(W,Hh,name,len(R),len(flags),flush=True)
out.close()
