#!/usr/bin/env python3
"""Mass die rolls through the real button. rolls.py <workdir> [srcRolls fxRolls]: flags black, white, flat, speck and frozen results, keeps the rolled values, writes a contact sheet of the worst and a sample of the rest."""
import json,os,subprocess,sys
import numpy as np
from PIL import Image,ImageDraw
HERE=os.path.dirname(os.path.abspath(__file__)); RES=HERE+'/results/rolls'; os.makedirs(RES,exist_ok=True); SH=HERE+'/sheets'; os.makedirs(SH,exist_ok=True)
A=sys.argv[1]; NS=int(sys.argv[2]) if len(sys.argv)>2 else 20; NF=int(sys.argv[3]) if len(sys.argv)>3 else 10
BIN=A+'/../rt/build/install/rt/bin/rt'; CWD=A+'/cwd'; os.makedirs(A+'/rolls',exist_ok=True)
ENV=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache')
RS='/home/gj/projects/liquid-lsd/src/main/resources'; FPS=20; STEP=3.0; W,H=160,90
srcs=sorted(d for d in os.listdir(RS+'/default_sources') if os.path.isdir(RS+'/default_sources/'+d))
fx=sorted(f[:-3] for f in os.listdir(RS+'/default_filters') if f.endswith('.fs'))
def script(kind,uid,n):
    st=[dict(t=0,action='crossfade',value=-1),dict(t=.05,action='randomization',value=1)]
    if kind=='src': st.append(dict(t=.1,action='load_source',deck='A',target=uid))
    else: st+=[dict(t=.1,action='load_source',deck='A',target='quasicrystal'),dict(t=.15,action='fx_filter',deck='A',slot=0,target=uid),dict(t=.3,action='fx_chain_enable',deck='A',value=1),dict(t=.3,action='fx_enable',deck='A',slot=0,value=1),dict(t=.3,action='super_knob',deck='A',value=.7),dict(t=.3,action='meta_knob',deck='A',slot=0,value=.7)]
    idx=[];t=1.0
    q=dict(deck='A',target=uid)
    if kind=='fx': q['slot']=0
    for r in range(n):
        st+=[dict(t=t,action='output_view',value=0),dict(t=t+.1,action='move',target='perf_rand_A',durationSec=.1),dict(t=t+.3,action='click'),dict(t=t+.5,action='output_view',value=1),dict(t=t+2.3,action='audit_list',**q)]
        idx.append(dict(roll=r,a=t+2.3,b=t+2.95)); t+=STEP
    return dict(seed=7,bpm=128.0,audio=A+'/music.wav',durationSec=t+.3,steps=sorted(st,key=lambda s:s['t'])),idx
def score(f,g):
    y=f[...,0]*.299+f[...,1]*.587+f[...,2]*.114; lit=(y>=12).mean()*100
    gx=np.abs(np.diff(y,axis=1)).mean()+np.abs(np.diff(y,axis=0)).mean()
    sat=(f.max(-1)-f.min(-1)).mean(); mot=float(np.abs(f.astype(np.float32)-g.astype(np.float32)).mean())
    d=dict(luma=round(float(y.mean()),1),lit=round(float(lit),1),clip=round(float((y>245).mean()*100),1),std=round(float(y.std()),1),edge=round(float(gx),2),sat=round(float(sat),1),motion=round(mot,2))
    flags=[]
    if d['lit']<5: flags.append('black')
    elif d['lit']<15 and d['std']<14: flags.append('speck')
    if d['clip']>60 or d['luma']>225: flags.append('white')
    if d['std']<5 and 'black' not in flags and 'white' not in flags: flags.append('flat')
    if mot<.03 and 'black' not in flags: flags.append('frozen')
    return d,flags
def unit(kind,uid,n):
    name=f'{kind}_{uid}'; out=f'{RES}/{name}.json'
    if os.path.exists(out): return
    d,idx=script(kind,uid,n); sp=f'{A}/rolls/{name}.json'; json.dump(d,open(sp,'w')); v=f'{A}/rolls/{name}.mp4'
    for attempt in range(3):
        with open(f'{A}/rolls/{name}.log','w') as lg:
            subprocess.run([BIN,f'--tour={sp}',f'--tour-out={v}','--no-audio','--tour-hidden',f'--tour-fps={FPS}','--tour-scale=0.25','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=ENV,stdout=lg,stderr=subprocess.STDOUT)
        if os.path.exists(v): break
    p=subprocess.run(['ffmpeg','-v','error','-i',v,'-vf',f'scale={W}:{H}','-f','rawvideo','-pix_fmt','rgb24','-'],stdout=subprocess.PIPE)
    F=np.frombuffer(p.stdout,np.uint8).reshape(-1,H,W,3)
    groups=[]; cur=None
    for l in open(f'{A}/rolls/{name}.log',errors='replace'):
        if 'TOUR_AUDIT list' not in l: continue
        q=l.rstrip('\n').split('\t'); kv=dict(x.split('=',1) for x in q[3:])
        groups.append((q[2],float(kv['base'])))
    np_=len(groups)//max(1,n); rows=[]; thumbs=[]
    for i,w in enumerate(idx):
        a=min(int(w['a']*FPS),len(F)-1); b=min(int(w['b']*FPS),len(F)-1)
        m,fl=score(F[b],F[a]); vals={k:round(x,3) for k,x in groups[i*np_:(i+1)*np_]} if np_ else {}
        rows.append(dict(roll=i,flags=fl,**m,values=vals)); thumbs.append(F[b].copy())
    json.dump(dict(unit=f'{kind}/{uid}',rolls=rows),open(out,'w'))
    order=sorted(range(n),key=lambda i:(not rows[i]['flags'],i))
    cols=10; im=Image.new('RGB',(cols*162,((n+cols-1)//cols)*104),(25,25,25)); D=ImageDraw.Draw(im)
    for j,i in enumerate(order):
        x,y=(j%cols)*162,(j//cols)*104; im.paste(Image.fromarray(thumbs[i]),(x+1,y+13)); D.text((x+2,y+1),f"{i} {','.join(rows[i]['flags'])}",fill=(255,120,120) if rows[i]['flags'] else (200,200,200))
    im.save(f'{SH}/{name}.jpg',quality=80)
    for e in('.mp4',):
        try: os.remove(v)
        except OSError: pass
    print(name,sum(bool(r['flags']) for r in rows),'of',n,flush=True)
for s in srcs: unit('src',s,NS)
for s in fx: unit('fx',s,NF)
print('ROLLSDONE',flush=True)
