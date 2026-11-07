#!/usr/bin/env python3
"""fuzzlib.py <libbase> <out>: damaged copies of real library files, added to the monkey's library so scans, lists and loads meet them."""
import glob,json,os,random,sys
base,out=sys.argv[1:3]; r=random.Random(11)
def mut(d):
    """A deep random change: wrong types, nulls, missing keys, extreme numbers."""
    if isinstance(d,dict):
        d=dict(d)
        for k in list(d):
            x=r.random()
            if x<.08: del d[k]
            elif x<.14: d[k]=r.choice((None,"x",-1,1e308,[],{},True,"../../etc/passwd"))
            else: d[k]=mut(d[k])
        return d
    if isinstance(d,list): return [mut(v) for v in d][:r.randint(0,40)]
    if isinstance(d,float) and r.random()<.1: return r.choice((1e30,-1e30,0.0))
    return d
kinds={'presets/fuzz':('presets/**/*.lsd','.lsd'),'fx_chains/fuzz':('fx_chains/*.lsdfxchain','.lsdfxchain'),'fx/fuzz':('fx/*.lsdfx','.lsdfx'),'transitions/fuzz':('transitions/*.lsdtrans','.lsdtrans'),'perform_pages/fuzz':('perform_pages/*.json','.json')}
n=0
for dst,(pat,ext) in kinds.items():
    fs=sorted(glob.glob(f'{base}/{pat}',recursive=True)); os.makedirs(f'{out}/{dst}',exist_ok=True)
    if not fs: continue
    for i in range(6):
        raw=open(r.choice(fs),'rb').read(); p=f'{out}/{dst}/zz_mut{i}{ext}'
        try: open(p,'w').write(json.dumps(mut(json.loads(raw))))
        except Exception: open(p,'wb').write(raw)
        n+=1
    v=r.choice(fs); raw=open(v,'rb').read()
    open(f'{out}/{dst}/zz_trunc{ext}','wb').write(raw[:len(raw)//2]); open(f'{out}/{dst}/zz_empty{ext}','wb').write(b'')
    open(f'{out}/{dst}/zz_bin{ext}','wb').write(bytes(r.randrange(256) for _ in range(4096))); open(f'{out}/{dst}/zz_array{ext}','w').write('[1,2,3]'); n+=4
os.makedirs(f'{out}/playlists',exist_ok=True)
open(f'{out}/playlists/zz_odd.lsdplay','w').write('\n'.join(['../../../etc/passwd','/nonexistent/a.lsd','   ','tt_alpha.lsd | | |','\x00bad','Ω.lsd','x'*5000])); n+=1
print(n,'damaged files')
