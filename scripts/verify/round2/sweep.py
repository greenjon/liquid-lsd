#!/usr/bin/env python3
"""Knob sweep: every parameter of every source, effect and transition, moved through a real macro knob binding.
Each window reloads the unit, so a frame differs from the same frame of an untouched baseline render only because of that parameter.
sweep.py <workdir>: discovers, then runs units not yet in results/. Survives a restart of the machine: results live beside this file."""
import json,os,re,subprocess,sys
import numpy as np
HERE=os.path.dirname(os.path.abspath(__file__)); RES=HERE+'/results'; A=sys.argv[1]; BIN=A+'/../rt/build/install/rt/bin/rt'
CWD=A+'/cwd'; os.makedirs(CWD+'/library',exist_ok=True); os.makedirs(A+'/scripts',exist_ok=True); os.makedirs(A+'/out',exist_ok=True)
ENV=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache')
RS='/home/gj/projects/liquid-lsd/src/main/resources'
FPS=30;WIN=.8;SET=.1;READ=.7
def run(n,d,extra=()):
    json.dump(d,open(f'{A}/scripts/{n}.json','w'))
    v=f'{A}/out/{n}.mp4'
    with open(f'{A}/out/{n}.log','w') as lg:
        rc=subprocess.run([BIN,f'--tour={A}/scripts/{n}.json',f'--tour-out={v}','--no-audio','--tour-hidden',f'--tour-fps={FPS}','--tour-scale=0.25','--tour-encoder=nvenc','--tour-hashes',*extra],cwd=CWD,env=ENV,stdout=lg,stderr=subprocess.STDOUT).returncode
    return rc
def hashes(n): return [l.split()[1] for l in open(f'{A}/out/{n}.mp4.hashes')]
def frames(n):
    p=subprocess.run(['ffmpeg','-v','error','-i',f'{A}/out/{n}.mp4','-vf','scale=160:90','-f','rawvideo','-pix_fmt','rgb24','-'],stdout=subprocess.PIPE)
    return np.frombuffer(p.stdout,np.uint8).reshape(-1,90,160,3).astype(np.int16)
def discover():
    srcs=sorted(d for d in os.listdir(RS+'/default_sources') if os.path.isdir(RS+'/default_sources/'+d))
    fx=sorted(f[:-3] for f in os.listdir(RS+'/default_filters') if f.endswith('.fs'))
    tr=sorted(f[:-3] for f in os.listdir(RS+'/default_transitions') if f.endswith('.fs'))
    st=[dict(t=0.0,action='output_view',value=1)];t=.2
    for s in srcs: st+=[dict(t=t,action='load_source',deck='A',target=s),dict(t=t+.3,action='audit_list',deck='A',target=s)];t+=.4
    st.append(dict(t=t,action='load_source',deck='A',target='quasicrystal'));t+=.2
    for s in fx: st+=[dict(t=t,action='fx_filter',deck='A',slot=0,target=s),dict(t=t+.3,action='audit_list',deck='A',slot=0,target=s)];t+=.4
    for s in tr: st+=[dict(t=t,action='transition',target=s),dict(t=t+.3,action='audit_list',deck='TRANS',target=s)];t+=.4
    run('discover',dict(seed=7,bpm=128.0,durationSec=t+.5,steps=st))
    u={}
    for l in open(f'{A}/out/discover.log',errors='replace'):
        if 'TOUR_AUDIT list' not in l: continue
        p=l.rstrip('\n').split('\t'); who=p[1].split('/'); kv=dict(x.split('=',1) for x in p[3:])
        kind='trans' if who[0]=='TRANS' else ('src' if who[1]=='src' else 'fx')
        u.setdefault(f'{kind}/{who[2]}',[]).append(dict(name=p[2],min=float(kv['min']),max=float(kv['max']),d=float(kv['def']),steps=None if kv['steps']=='null' else int(kv['steps']),angle=kv['angle']=='true',driver=kv['driver']=='true',rand=kv['rand']=='true'))
    json.dump(u,open(RES+'/units.json','w'),indent=0)
    return u
def values(p):
    r=p['max']-p['min']
    if r<=0: return []
    dn=(p['d']-p['min'])/r
    if p['steps'] and 2<=p['steps']<=12: vs=[i/(p['steps']-1) for i in range(p['steps'])]
    elif p['angle']: vs=[0,.25,.75,1]
    else: vs=[0,1]
    return [v for v in vs if abs(v-dn)>.02]
def reload(kind,uid,t):
    if kind=='src': return [dict(t=t,action='load_source',deck='A',target='mandala' if uid=='scope' else 'scope'),dict(t=t+.034,action='load_source',deck='A',target=uid)]
    if kind=='fx':
        return [dict(t=t,action='load_source',deck='A',target='scope'),dict(t=t+.034,action='load_source',deck='A',target='quasicrystal'),dict(t=t,action='fx_filter',deck='A',slot=0,target=''),dict(t=t+.034,action='fx_filter',deck='A',slot=0,target=uid),
                dict(t=t+.067,action='fx_chain_enable',deck='A',value=1),dict(t=t+.067,action='fx_enable',deck='A',slot=0,value=1),dict(t=t+.067,action='super_knob',deck='A',value=.7),dict(t=t+.067,action='meta_knob',deck='A',slot=0,value=.7)]
    return [dict(t=t,action='transition',target='noise_dissolve' if uid=='linear_crossfade' else 'linear_crossfade'),dict(t=t+.034,action='transition',target=uid)]
def unit(key,ps):
    kind,uid=key.split('/'); u=f'{kind}_{uid}'; out=f'{RES}/{u}.json'
    if os.path.exists(out): return
    ps=[p for p in ps if p['name']!='progress']
    if uid=='fractal_flame': ps=[p for p in ps if not re.match(r'^X\d',p['name'])]
    base=[dict(t=0,action='output_view',value=1),dict(t=0,action='crossfade',value=-1 if kind!='trans' else 0)]
    if kind=='trans': base+=[dict(t=0,action='load_source',deck='A',target='quasicrystal'),dict(t=0,action='load_source',deck='B',target='voronoi_cells')]
    sw=list(base); idx=[]; t=.5
    for p in ps:
        for v in values(p):
            r=reload(kind,uid,t); base=base+r; sw=sw+r
            q=dict(deck='TRANS' if kind=='trans' else 'A',param=p['name'],target=uid)
            if kind=='fx': q['slot']=0
            sw=sw+[dict(t=t+SET,action='audit_set',value=round(v,5),**q),dict(t=t+READ,action='audit_read')]
            idx.append(dict(param=p['name'],knob=round(v,5),value=p['min']+(p['max']-p['min'])*v,default=p['d'],steps=p['steps'],start=t,driver=p['driver'])); t+=WIN
    d=lambda st:dict(seed=7,bpm=128.0,audio=A+'/music.wav',durationSec=t+.3,steps=sorted(st,key=lambda s:s['t']))
    for n,st in((u+'_a',base),(u+'_b',base),(u+'_s',sw)):
        for attempt in range(3):
            run(n,d(st))
            if os.path.exists(f'{A}/out/{n}.mp4.hashes'): break
    Ha,Hb,Hs=hashes(u+'_a'),hashes(u+'_b'),hashes(u+'_s'); Fa,Fb,Fs=frames(u+'_a'),frames(u+'_b'),frames(u+'_s')
    reads=[l.rstrip('\n').split('\t')[2:] for l in open(f'{A}/out/{u}_s.log',errors='replace') if 'TOUR_AUDIT read' in l]
    miss=[l.strip()[-60:] for l in open(f'{A}/out/{u}_s.log',errors='replace') if 'TOUR_AUDIT missing' in l or 'TOUR_AUDIT nopath' in l or 'TOUR_WARN' in l]
    res=dict(unit=key,noise_frames=sum(x!=y for x,y in zip(Ha,Hb)),frames=len(Ha),problems=miss[:10],win=[])
    for i,w in enumerate(idx):
        a=int((w['start']+SET)*FPS)+2; b=int((w['start']+WIN)*FPS)
        hd=sum(Ha[j]!=Hs[j] for j in range(a,min(b,len(Ha),len(Hs))))
        noise=sum(Ha[j]!=Hb[j] for j in range(a,min(b,len(Ha),len(Hb))))
        m=b-(b-a)//2
        fa,fs,fb=Fa[m:b],Fs[m:b],Fb[m:b]
        dd=np.abs(fa-fs); nn=np.abs(fa-fb)
        w2=dict(w,hash_diff=hd,noise=noise,n=b-a,mean=round(float(dd.mean()),3),noise_mean=round(float(nn.mean()),3),big=round(float((dd.max(axis=-1)>16).mean())*100,2))
        rd=reads[i] if i<len(reads) else None
        if rd:
            kv=dict(x.split('=',1) for x in rd[1:] if '=' in x)
            w2['base']=float(kv['base']);w2['want']=float(kv['want'])
        res['win'].append(w2)
    json.dump(res,open(out,'w'))
    for n in(u+'_a',u+'_b',u+'_s'):
        for e in('.mp4','.mp4.hashes'):
            try: os.remove(f'{A}/out/{n}{e}')
            except OSError: pass
    print(key,len(idx),'windows',flush=True)
U=json.load(open(RES+'/units.json')) if os.path.exists(RES+'/units.json') else discover()
for k in sorted(U,key=lambda k:len(U[k])): unit(k,U[k])
print('ALLDONE',flush=True)
