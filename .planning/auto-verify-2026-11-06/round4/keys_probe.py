import json,subprocess,os,shutil,sys
A=sys.argv[1]; BIN=A+'/../rt/build/install/rt/bin/rt'; CWD=A+'/kcwd'
if not os.path.exists(CWD): os.makedirs(CWD); shutil.copytree(A+'/libbase',CWD+'/library')
st=[dict(t=.2,action='load_source',deck='A',target='mandala'),dict(t=.2,action='fx_filter',deck='A',slot=0,target='bloom'),
 dict(t=1.2,action='open_params',target='deckA_fx'),dict(t=2.2,action='audit_keys',target='fx'),dict(t=2.3,action='audit_dump',target='d'),
 dict(t=2.5,action='open_params',target='deckA'),dict(t=3.4,action='audit_keys',target='src')]
json.dump(dict(seed=7,bpm=128.0,durationSec=4,tooltips=False,steps=st),open(A+'/scripts/k.json','w'))
e=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache',LSD_TOUR_W='1280',LSD_TOUR_H='720')
p=subprocess.run([BIN,f'--tour={A}/scripts/k.json',f'--tour-out={A}/out/k.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=1','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=e,capture_output=True,text=True)
for l in p.stdout.splitlines():
    if l.startswith(('TOUR_KEYS','TOUR_DUMP')): print(l[:3200])
