#!/usr/bin/env python3
"""realmusic.py <work dir>: real tracks from a list through the desktop's offline feed. Only numbers are saved, never names or audio: for each, BPM against Mixxx's reading, drops, time read as breakdown, and how bass behaves in those stretches. results/realmusic.txt."""
import json,os,random,subprocess,sys
H=os.path.dirname(os.path.abspath(__file__)); A=sys.argv[1]; CACHE=os.path.abspath(A+'/../..'); BIN=os.path.abspath(A+'/../soakbin/bin/rt'); CWD=A+'/mcwd'; W=CACHE+'/real/t.wav'
ENV=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache',LSD_TOUR_CV='1')
rows=[l.rstrip('\n').split('\t') for l in open(CACHE+'/real/list.tsv')]; r=random.Random(9)
pick=[]
for g,n in (('125-135',8),('110-125',4),('135-190',3),('60-95',3)):
    x=[q for q in rows if q[0]==g]; r.shuffle(x); pick+=x[:n]
out=open(H+'/results/realmusic.txt','w')
for i,(g,bpm,path) in enumerate(pick):
    subprocess.run(['ffmpeg','-v','error','-y','-i',path,'-t','300','-ac','2','-ar','44100',W])
    if not os.path.exists(W): continue
    dur=float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','csv=p=0',W]).decode().strip())
    json.dump(dict(seed=7,bpm=128.0,audio=W,durationSec=dur,tooltips=False,steps=[dict(t=.1,action='load_source',deck='A',target='metaballs')]),open(A+'/scripts/rm.json','w'))
    p=subprocess.run([BIN,f'--tour={A}/scripts/rm.json',f'--tour-out={A}/out/rm.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=0.25','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=ENV,capture_output=True,text=True)
    c=[[float(x) for x in l.split()[1:]] for l in p.stdout.splitlines() if l.startswith('TOUR_CV')]
    if not c: continue
    T=c[30*30:] if len(c)>30*40 else c
    drops=0; prev=0
    for x in c:
        if x[5]>.5 and prev<=.5: drops+=1
        prev=x[5]
    low=[x for x in T if x[6]<=-.8]; norm=[x for x in T if x[6]>-.3]; med=sorted(x[7] for x in T)[len(T)//2]; ref=float(bpm)
    oct_=min((abs(med/ref-k),k) for k in (.5,1,2,1.5,2/3))
    m=lambda v:round(sum(v)/max(1,len(v)),3)
    line=f"{i} {g} mixxx {ref:.0f} desktop {med:.1f} ratio {oct_[1]:.2f} off {oct_[0]*100:.1f}% | {dur:.0f} s, drops {drops}, breakdown {100*len(low)//max(1,len(T))}% of time, bass in it {m([x[1] for x in low])} vs {m([x[1] for x in norm])}"
    print(line,flush=True); out.write(line+'\n'); out.flush()
