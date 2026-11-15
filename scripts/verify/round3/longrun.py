#!/usr/bin/env python3
"""longrun.py <work dir> [minutes]: a simulated hour of A/B changes on the real render path: a new preset on the off-air deck every 30 s, a fade, music. Prints GPU buffers and JVM heap."""
import glob,json,os,random,re,subprocess,sys
A=sys.argv[1]; MIN=int(sys.argv[2]) if len(sys.argv)>2 else 60; CACHE=os.path.abspath(A+'/../..'); CWD=A+'/lcwd'; BIN=A+'/../rt/build/install/rt/bin/rt'; H=os.path.dirname(os.path.abspath(__file__))
if not os.path.exists(CWD): os.makedirs(CWD); subprocess.run(['cp','-r',A+'/libbase',CWD+'/library'],check=True)
ps=sorted(os.path.relpath(p,CWD) for p in glob.glob(CWD+'/library/presets/**/*.lsd',recursive=True) if 'tt_' not in p); random.Random(3).shuffle(ps)
st=[]; t=1.0; deck='B'; v=-1; i=0
while t<MIN*60:
    st.append(dict(t=t,action='load_preset',deck=deck,target=ps[i%len(ps)])); i+=1
    v=-v; st.append(dict(t=t+5,action='crossfade',value=float(v),durationSec=4.0)); deck='A' if deck=='B' else 'B'
    if i%10==0: st.append(dict(t=t+10,action='audit_state',target=f's{i}'))
    t+=30
json.dump(dict(seed=7,bpm=128.0,audio=CACHE+'/steady128.wav' if os.path.exists(CACHE+'/steady128.wav') else None,durationSec=MIN*60,tooltips=False,steps=st),open(A+'/scripts/long.json','w'))
env=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache')
log=A+'/out/long.log'
with open(log,'w') as lg: subprocess.run([BIN,f'--tour={A}/scripts/long.json',f'--tour-out={A}/out/long.mp4','--no-audio','--tour-hidden','--tour-fps=15','--tour-scale=0.25','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=env,stdout=lg,stderr=subprocess.STDOUT)
out=[]
for l in open(log,errors='replace'):
    m=re.search(r'TOUR_STATE (\S+) t=(\S+).*mem: fbo=(\d+) mb=(\S+) heap=(\d+)',l)
    if m: out.append(m.groups())
with open(H+'/results/longrun.txt','w') as f:
    for x in out: f.write(' '.join(x)+'\n')
print(len(out),out[:2],out[-2:])
