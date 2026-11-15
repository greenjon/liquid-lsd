#!/usr/bin/env python3
"""music.py <work dir> [name...]: the desktop's audio response on synthesized music with known sections, through the real offline feed. Writes results/music_<name>.txt."""
import json,os,re,subprocess,sys
H=os.path.dirname(os.path.abspath(__file__)); A=sys.argv[1]; BIN=A+'/../rt/build/install/rt/bin/rt'; CACHE=os.path.abspath(A+'/../..'); CWD=A+'/mcwd'
ENV=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache',LSD_TOUR_CV='1')
def ff(*a): subprocess.run(['ffmpeg','-v','error','-y',*a],check=True)
def wav(n):
    p=f'{CACHE}/{n}.wav'
    if os.path.exists(p): return p
    if n=='levels': # loud, 20 dB down, silence, back louder at another level
        ff('-i',f'{CACHE}/steady128.ogg','-t','190','-af',"volume='if(lt(t,50),1,if(lt(t,130),0.1,if(lt(t,145),0,0.5)))':eval=frame",p)
    else: ff('-i',f'{CACHE}/{n}.ogg',p)
    return p
def run(name,track,dur,resp=1.0,steps=()):
    s=dict(seed=7,bpm=128.0,audio=wav(track),durationSec=dur,tooltips=False,steps=[dict(t=.1,action='load_source',deck='A',target='metaballs'),dict(t=.1,action='audio_response',value=resp),*steps])
    f=f'{A}/scripts/m_{name}.json'; json.dump(s,open(f,'w')); log=f'{A}/out/m_{name}.log'
    with open(log,'w') as lg: subprocess.run([BIN,f'--tour={f}',f'--tour-out={A}/out/m_{name}.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=0.25','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=ENV,stdout=lg,stderr=subprocess.STDOUT)
    rows=[[float(x) for x in l.split()[1:]] for l in open(log,errors='replace') if l.startswith('TOUR_CV')]
    return rows
def mean(r,a,b,i): v=[x[i] for x in r if a<=x[0]<b]; return sum(v)/max(1,len(v))
def peak(r,a,b,i): v=[x[i] for x in r if a<=x[0]<b]; return max(v) if v else 0
BASS,AMP,FLUX,EN,DROP,ENB,BPM=1,2,3,4,5,6,7
T=json.load(open(f'{CACHE}/dance128.json'))
out=[]; say=lambda *a:(out.append(' '.join(str(x) for x in a)),print(*a,flush=True))
names=sys.argv[2:] or ['resp100','resp0','resp200','levels']
R={}
for n,resp in (('resp100',1.0),('resp0',0.0),('resp200',2.0)):
    if n not in names: continue
    r=R[n]=run(n,'dance128',245,resp); say(f'== {n}: {len(r)} frames')
    say('groove 40-70 s: bass mean',round(mean(r,40,70,BASS),3),'peak',round(peak(r,40,70,BASS),3),'kick flux mean',round(mean(r,40,70,FLUX),3),'energy',round(mean(r,40,70,EN),2),'bpm',round(mean(r,40,70,BPM),1))
    for a,b in ((75,105),(165,187.5)):
        say(f'breakdown {a}-{b}:',' '.join(f'{t:.0f}s={mean(r,t,t+3,BASS):.3f}' for t in (a,a+3,a+6,a+12,a+18,b-3)),'energy end',round(mean(r,b-4,b,EN),2),'bass energy end',round(mean(r,b-4,b,ENB),2),'bpm',round(mean(r,a+3,b,BPM),1))
        pk=next((x[0] for x in r if x[0]>b-1 and x[DROP]>.5),None)
        say(f'  drop at {b}: audio_drop above half at',pk,'late by',None if pk is None else round(pk-b,2),'s; peak',round(peak(r,b,b+3,DROP),2),'bass peak after',round(peak(r,b,b+4,BASS),3),'before',round(peak(r,a-4,a,BASS),3))
    say('false drops in groove (audio_drop peak 30-70 s):',round(peak(r,30,70,DROP),2))
if 'levels' in names:
    r=run('levels','levels',190); say('== levels: 0-50 full, 50-130 -20 dB, 130-145 silence, 145-190 -6 dB')
    for a,b in ((30,50),(50,55),(55,60),(60,70),(80,90),(100,110),(110,120),(120,130),(130,134),(140,145),(145,148),(148,153),(160,190)):
        say(f'{a}-{b}: bass {mean(r,a,b,BASS):.3f} peak {peak(r,a,b,BASS):.3f} amp {mean(r,a,b,AMP):.3f} energy {mean(r,a,b,EN):+.2f}')
open(f'{H}/results/music.txt','a').write('\n'.join(out)+'\n')
