#!/usr/bin/env python3
"""respond.py <work dir> <kind> [ids...]: does the music move the picture? Each item renders the same 20 s twice, Audio
Response 100% and 0%, with the synth 128 BPM track. The difference between the two renders is what the band responses add
(section signals keep running at 0% by design, so they cancel out). kind: src | fx | trans. results/respond_<kind>.txt."""
import json,os,subprocess,sys,shutil
import numpy as np
H=os.path.dirname(os.path.abspath(__file__)); A=os.path.abspath(sys.argv[1]); kind=sys.argv[2]; ids=sys.argv[3:]
BIN=A+'/../rt/build/install/rt/bin/rt'; CWD=A+'/rcwd'; MUS=os.path.abspath(A+'/../../dance128.ogg')
if not os.path.exists(CWD+'/library'): shutil.copytree(A+'/libbase',CWD+'/library')
os.makedirs(A+'/resp',exist_ok=True)
ENV=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',__GL_THREADED_OPTIMIZATIONS='0',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache',LSD_TOUR_CV='1',LSD_TOUR_W='1280',LSD_TOUR_H='720')
W,Hh=96,54
def steps(item,resp):
    s=[dict(t=0.05,action='audio_response',value=resp)]
    if kind=='src': s.append(dict(t=.1,action='load_source',deck='A',target=item))
    elif kind=='fx':
        s.append(dict(t=.1,action='load_source',deck='A',target='voronoi_cells'))
        s.append(dict(t=.15,action='fx_chain_enable',deck='A',value=1.0))
        for k in range(3): s.append(dict(t=.2+k*.01,action='fx_filter',deck='A',slot=k,target=(item if k==0 else '')))
    else:
        s+= [dict(t=.1,action='load_source',deck='A',target='voronoi_cells'),dict(t=.12,action='load_source',deck='B',target='aurora_ribbons'),
             dict(t=.15,action='transition',target=item),dict(t=.2,action='crossfade',value=.5)]
    return s
def render(item,resp):
    tag=f'{kind}_{item}_{int(resp*100)}'; js=f'{A}/resp/{tag}.json'; mp4=f'{A}/resp/{tag}.mp4'
    json.dump(dict(seed=7,bpm=128.0,audio=MUS,durationSec=20,tooltips=False,steps=steps(item,resp)),open(js,'w'))
    p=subprocess.run([BIN,f'--tour={js}',f'--tour-out={mp4}','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=0.25','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=ENV,capture_output=True,text=True)
    cv=[[float(x) for x in l.split()[1:]] for l in p.stdout.splitlines() if l.startswith('TOUR_CV')]
    warn=[l for l in p.stdout.splitlines() if 'TOUR_WARN' in l][:2]
    raw=subprocess.run(['ffmpeg','-v','error','-i',mp4,'-vf',f'scale={W}:{Hh}','-pix_fmt','gray','-f','rawvideo','-'],capture_output=True).stdout
    fr=np.frombuffer(raw,np.uint8).reshape(-1,Hh,W).astype(np.float32); os.remove(mp4)
    return fr,np.array(cv),warn
out=open(f'{H}/results/respond_{kind}.txt','a')
for item in ids:
    a,cv,w1=render(item,1.0); b,_,w2=render(item,0.0)
    n=min(len(a),len(b),len(cv)); a,b=a[60:n],b[60:n]   # skip the first 2 s (load, fade-in)
    diff=np.abs(a-b).mean(axis=(1,2)); motion_a=np.abs(np.diff(a,axis=0)).mean(); motion_b=np.abs(np.diff(b,axis=0)).mean()
    bass=cv[60:n,1] if len(cv) else np.zeros(len(diff))
    # does the picture change when the bass changes: frame-to-frame change of the difference against frame-to-frame bass change
    dd=np.abs(np.diff(a-b,axis=0)).mean(axis=(1,2)); db=np.abs(np.diff(bass))
    corr=float(np.corrcoef(dd,db)[0,1]) if dd.std()>1e-6 and db.std()>1e-6 else 0.0
    line=f"{item:26} diff {diff.mean():6.2f} (max {diff.max():6.2f})  motion {motion_a:5.2f} vs {motion_b:5.2f} at 0%  corr {corr:+.2f}  brightness {a.mean():5.1f}" + (f"  WARN {w1+w2}" if w1 or w2 else '')
    print(line,flush=True); out.write(line+'\n'); out.flush()
