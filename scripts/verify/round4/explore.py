#!/usr/bin/env python3
"""explore.py <work dir> <json steps>: run steps; print on-screen keys not in the base set; frames at audit_keys times saved to out/e_<label>.png"""
import json,subprocess,os,shutil,sys,re
A=sys.argv[1]; steps=json.loads(sys.argv[2]); BIN=A+'/../rt/build/install/rt/bin/rt'; CWD=A+'/kcwd'
shutil.rmtree(CWD,ignore_errors=True); os.makedirs(CWD); shutil.copytree(A+'/libbase',CWD+'/library')
t=.2; st=[]
for s in steps:
    t+=s.pop('dt',.5); st.append(dict(t=round(t,2),**s))
json.dump(dict(seed=7,bpm=128.0,durationSec=t+1,tooltips=False,steps=st),open(A+'/scripts/e.json','w'))
e=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache',LSD_TOUR_W='1280',LSD_TOUR_H='720',LSD_FAKE_TWISTER='1')
p=subprocess.run([BIN,f'--tour={A}/scripts/e.json',f'--tour-out={A}/out/e.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=1','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=e,capture_output=True,text=True)
prev=set()
for l in p.stdout.splitlines():
    if l.startswith('TOUR_KEYS'):
        _,lab,k=l.split(' ',2); ks=set(x for x in k.split('|') if not re.match(r'(btn_|b:)\d*$|btnid_lobe|btn_\W',x)); new=sorted(ks-prev); prev=ks; print(lab,'NEW:',' | '.join(new)[:1800])
    elif l.startswith(('TOUR_DUMP','TOUR_WARN','TOUR_TOAST')) or 'Exception' in l: print(l[:600])
for s in st:
    if s['action']=='audit_keys': subprocess.run(['ffmpeg','-v','error','-y','-ss',str(s['t']),'-i',A+'/out/e.mp4','-frames:v','1',f"{A}/out/e_{s['target']}.png"])
