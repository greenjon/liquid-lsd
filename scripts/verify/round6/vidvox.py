#!/usr/bin/env python3
"""vidvox.py <work dir>: Vidvox ISF audio visualizers as user sources (and Waveform Displace as an FX), with and without music; a shader that reads the audio differs between the two runs at the same moment."""
import json,os,shutil,subprocess,sys,hashlib
A=sys.argv[1]; BIN=A+'/../rt/build/install/rt/bin/rt'; CWD=A+'/vcwd'; LIB=CWD+'/library'; VV=A+'/../../vidvox'; H=os.path.dirname(os.path.abspath(__file__))
ENV=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache',LSD_TOUR_W='1280',LSD_TOUR_H='720')
shutil.rmtree(CWD,ignore_errors=True); os.makedirs(CWD); shutil.copytree(A+'/libbase',LIB,symlinks=True)
d=json.load(open(LIB+'/isf_directories.json')); d['sources'].append(dict(path=os.path.abspath(VV),type='USER_STANDARD',isEnabled=True)); json.dump(d,open(LIB+'/isf_directories.json','w'),indent=2)
SRC=['audio_waveform_shape','fft_color_lines','fft_filled_waveform','fft_spectrogram','radial_spectrogram']
def run(tag,audio):
    steps=[]; t=.5
    for s in SRC: steps+= [dict(t=t,action='load_source',deck='A',target=s),dict(t=t+2.9,action='audit_state',target=s)]; t+=3
    steps+=[dict(t=t,action='load_source',deck='A',target='metaballs'),dict(t=t,action='fx_filter',deck='A',slot=0,target='waveform_displace'),dict(t=t+2.9,action='audit_state',target='wd')]; t+=3
    sc=dict(seed=7,bpm=128.0,durationSec=t+.5,tooltips=False,steps=steps)
    if audio: sc['audio']=os.path.abspath(A+'/../../dance128.wav')
    p=f'{A}/scripts/vv_{tag}.json'; json.dump(sc,open(p,'w'))
    with open(f'{A}/out/vv_{tag}.log','w') as lg:
        subprocess.run([BIN,f'--tour={p}',f'--tour-out={A}/out/vv_{tag}.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=1','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=ENV,stdout=lg,stderr=subprocess.STDOUT)
    shots={}
    for i,s in enumerate(SRC+['wd']):
        for k in (1,2):
            o=f'{A}/out/vv_{tag}_{s}_{k}.png'
            subprocess.run(['ffmpeg','-v','error','-y','-ss',str(.5+3*i+1.2*k),'-i',f'{A}/out/vv_{tag}.mp4','-frames:v','1','-vf','crop=420:240:850:35',o]); shots[(s,k)]=o
    return shots
def diff(a,b):
    r=subprocess.run(['compare','-metric','RMSE',a,b,'null:'],capture_output=True,text=True); return float(r.stderr.split('(')[1].split(')')[0]) if '(' in r.stderr else -1
m=run('music',True); q=run('quiet',False)
out=[]
for s in SRC+['wd']:
    st=[l for l in open(f'{A}/out/vv_music.log',errors='replace') if f'TOUR_STATE {s} ' in l]
    loaded=st[0].split('| A: ')[1].split(' file=')[0] if st else '?'
    d=[round(diff(m[(s,k)],q[(s,k)]),4) for k in (1,2)]; mv=round(diff(m[(s,1)],m[(s,2)]),4)
    x=f'{s}: loaded as {loaded}; music vs silence at the same moment RMSE {d}; motion with music {mv}'; print(x,flush=True); out.append(x)
warn=[l.strip()[:200] for l in open(f'{A}/out/vv_music.log',errors='replace') if 'WARN' in l or 'rror' in l][:8]
print('\n'.join(warn)); open(H+'/results/vidvox.txt','w').write('\n'.join(out+warn)+'\n')
