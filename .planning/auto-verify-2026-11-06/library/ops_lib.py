#!/usr/bin/env python3
"""ops_lib.py <case...>: reset library, run one tour per case at 1280x720, print the file differences on disk. Cases live in cases.py."""
import hashlib,json,os,shutil,subprocess,sys
V=os.path.dirname(os.path.abspath(__file__)); W=V+'/../rt'; L=W+'/library'; B=V+'/libbase'
sys.path.insert(0,V)
import importlib,cases
importlib.reload(cases)
def snap(root):
    r={}
    for d,_,fs in os.walk(root):
        if '/natives' in d or '/sources' in d: continue
        for f in fs:
            if f.endswith(('.json','.ini')) and 'recent' in f: continue
            p=os.path.join(d,f); r[os.path.relpath(p,root)]=hashlib.sha1(open(p,'rb').read()).hexdigest()
        if not fs and not _: r[os.path.relpath(d,root)+'/']='dir'
    return r
def run(name):
    build=cases.CASES[name]
    steps,shots=build()
    shutil.rmtree(L); shutil.copytree(B,L,symlinks=True)
    json.dump({"seed":7,"bpm":128.0,"durationSec":max([s["t"] for s in steps]+[s["t"] for s in shots])+1.5,"tooltips":False,"steps":steps},open(f'{V}/scripts/lib_{name}.json','w'))
    json.dump(shots,open(f'{V}/scripts/lib_{name}.shots.json','w'))
    env=dict(os.environ,SCALE='1',LSD_TOUR_W='1280',LSD_TOUR_H='720')
    o=subprocess.run([f'{V}/run.sh',f'lib_{name}'],env=env,capture_output=True,text=True).stdout
    subprocess.run([f'{V}/shots.sh',f'lib_{name}'],capture_output=True)
    a,b=snap(B),snap(L)
    print(f'=== {name}')
    for l in o.splitlines():
        if 'WARN' in l or 'rc=' in l and 'rc=0' not in l or 'Exception' in l: print('  !',l[:150])
    for k in sorted(set(a)|set(b)):
        if k.endswith('recent.json') or k=='last_session.json': continue
        if k not in a: print('  + ',k)
        elif k not in b: print('  - ',k)
        elif a[k]!=b[k]: print('  ~ ',k)
    if name in cases.CHECKS:
        try: cases.CHECKS[name](L)
        except Exception as e: print('      check:',type(e).__name__,str(e)[-60:])
for n in sys.argv[1:]: run(n)
