#!/usr/bin/env python3
"""For parameters the sweep found dead: does it work once another setting allows it? Each no-effect parameter is retried with every other stepped parameter (or switch) at each of its values, and continuous ones at their ends."""
import json,os,subprocess,sys
H=os.path.dirname(os.path.abspath(__file__)); A=sys.argv[1]; BIN=A+'/../rt3/build/install/rt3/bin/rt3'; CWD=A+'/cwd'; OUT=H+'/results/conditional.txt'
ENV=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg2',XDG_CONFIG_HOME=A+'/xdg2',XDG_CACHE_HOME=A+'/xdg2')
U=json.load(open(H+'/results/units.json'))
T={'fx/3d_elevation':['separation'],'fx/wave_displace':['centerX','centerY'],'src/celestial_engine':['PulseWave'],'src/chladni_cymatics':['HueOffset'],'src/icosa_h3':['SpikePhase','SpikeSharpness','BlockerSize'],'src/kifs':['Vertex'],'src/kifs_storm':['Repeat3D','NormalFrequency'],'src/scope':['RingSize'],'src/tunnel_scenes':['Symmetries']}
FPS=30;WIN=.7
def hashes(n,d):
    json.dump(d,open(f'{A}/c2_{n}.json','w'))
    for _ in range(3):
        subprocess.run([BIN,f'--tour={A}/c2_{n}.json',f'--tour-out={A}/c2_{n}.mp4','--no-audio','--tour-hidden',f'--tour-fps={FPS}','--tour-scale=0.25','--tour-encoder=nvenc','--tour-hashes'],cwd=CWD,env=ENV,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        if os.path.exists(f'{A}/c2_{n}.mp4.hashes'): break
    h=[l.split()[1] for l in open(f'{A}/c2_{n}.mp4.hashes')]; os.remove(f'{A}/c2_{n}.mp4'); os.remove(f'{A}/c2_{n}.mp4.hashes'); return h
def vals(p):
    r=p['max']-p['min']
    if r<=0: return []
    if p['steps'] and p['steps']<=8: return [i/(p['steps']-1) for i in range(p['steps'])]
    return [0,1]
open(OUT,'w').close()
for key,dead in T.items():
    kind,uid=key.split('/'); ps=U[key]; base=[dict(t=0,action='output_view',value=1),dict(t=0,action='crossfade',value=-1)]; sw=list(base); idx=[]; t=.5
    for P in dead:
        pp=next(p for p in ps if p['name']==P)
        for M in ps:
            if M['name']==P or M['name'] in('Gain',): continue
            for mv in vals(M):
                r=[dict(t=t,action='load_source',deck='A',target='scope' if uid!='scope' else 'mandala'),dict(t=t+.034,action='load_source',deck='A',target=uid)] if kind=='src' else [dict(t=t,action='load_source',deck='A',target='scope'),dict(t=t+.034,action='load_source',deck='A',target='quasicrystal'),dict(t=t,action='fx_filter',deck='A',slot=0,target=''),dict(t=t+.034,action='fx_filter',deck='A',slot=0,target=uid),dict(t=t+.067,action='fx_chain_enable',deck='A',value=1),dict(t=t+.067,action='fx_enable',deck='A',slot=0,value=1),dict(t=t+.067,action='super_knob',deck='A',value=.7),dict(t=t+.067,action='meta_knob',deck='A',slot=0,value=.7)]
                q=dict(deck='A',target=uid)
                if kind=='fx': q['slot']=0
                mset=dict(t=t+.1,action='audit_set',param=M['name'],value=round(mv,5),unit='2',**q)
                base=base+r+[mset]; sw=sw+r+[mset]
                # P at its far end: the end further from the default
                dn=(pp['d']-pp['min'])/max(1e-6,pp['max']-pp['min']); pv=0.0 if dn>.5 else 1.0
                if pp['angle']: pv=.25
                sw=sw+[dict(t=t+.15,action='audit_set',param=P,value=pv,**q)]
                idx.append((P,M['name'],mv,t)); t+=WIN
    d=lambda st:dict(seed=7,bpm=128.0,durationSec=t+.3,steps=sorted(st,key=lambda s:s['t']))
    Ha=hashes(key.replace('/','_')+'_a',d(base)); Hs=hashes(key.replace('/','_')+'_s',d(sw))
    res={}
    for P,M,mv,t0 in idx:
        a=int((t0+.2)*FPS); b=int((t0+WIN)*FPS); n=sum(Ha[i]!=Hs[i] for i in range(a,min(b,len(Ha),len(Hs))))
        if n: res.setdefault(P,[]).append(f'{M}@{mv:.2f}')
    with open(OUT,'a') as f:
        for P in dead: f.write(f"{key} {P}: {'works when '+', '.join(res[P][:8]) if P in res else 'NO EFFECT in any tried setting'}\n")
print('DONE')
