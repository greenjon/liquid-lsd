#!/usr/bin/env python3
"""offair.py <work dir>: real frame times, two heavy sources, crossfader centre vs fully on A, half rate on and off."""
import json,os,re,subprocess,sys
A=sys.argv[1]; BIN=A+'/../rt/build/install/rt/bin/rt'; CWD=A+'/mcwd'; H=os.path.dirname(os.path.abspath(__file__))
env=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache')
out=[]
for name,xf,half in (('centre',0.,1),('on_A',-1.,1),('on_A_every_frame',-1.,0),('centre2',0.,1),('on_A2',-1.,1)):
    s=dict(seed=7,bpm=128.0,durationSec=12,tooltips=False,steps=[dict(t=.1,action='load_source',deck='A',target='nebula_volume'),dict(t=.1,action='load_source',deck='B',target='apollonian'),dict(t=.2,action='crossfade',value=xf),dict(t=.2,action='off_air',value=float(half))])
    f=f'{A}/scripts/oa_{name}.json'; json.dump(s,open(f,'w'))
    p=subprocess.run([BIN,f'--tour={f}',f'--tour-out={A}/out/oa_{name}.mp4','--no-audio','--tour-hidden','--tour-fps=60','--tour-scale=0.25','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=env,capture_output=True,text=True)
    m=re.search(r'TOUR_DONE .*avg_ms=(\S+) max_ms=(\S+)',p.stdout); x=f"{name}: avg {m[1] if m else '?'} ms, max {m[2] if m else '?'} ms"; print(x,flush=True); out.append(x)
open(H+'/results/offair.txt','w').write('\n'.join(out)+'\n')
