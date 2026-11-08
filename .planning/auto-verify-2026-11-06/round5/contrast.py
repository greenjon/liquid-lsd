#!/usr/bin/env python3
"""contrast.py <work dir>: every view at 1280x720 in both themes. For each labelled widget rectangle: background = median brightness, foreground = the pixels furthest from it (the text or icon). Lists WCAG-style ratios under 3:1. An estimate: a bevel or a neighbour in the rectangle can raise a ratio, and it can't see text that has no widget key. results/contrast.txt, frames in results/contrast/."""
import json,os,re,shutil,subprocess,sys
import numpy as np
from PIL import Image
H=os.path.dirname(os.path.abspath(__file__)); A=sys.argv[1]; BIN=A+'/../rt/build/install/rt/bin/rt'; CWD=A+'/ccwd'
if not os.path.exists(CWD): os.makedirs(CWD); shutil.copytree(A+'/libbase',CWD+'/library')
V={'perform':[],
 'mixer':[dict(action='set_page',target='mixer')],
 'library':[dict(action='library_mode',target='FULL')],
 'library_fx':[dict(action='library_mode',target='FULL'),dict(action='move',target='browser_tab_fx',durationSec=.1),dict(action='click',dt=.2)],
 'queues':[dict(action='move',target='browser_tab_queues',durationSec=.1),dict(action='click',dt=.2)],
 'pair_bg':[dict(action='move',x=955,y=515,durationSec=.1),dict(action='click',dt=.2)],
 'bound':[dict(action='move',target='perf_gen_badge_A',durationSec=.1),dict(action='click',dt=.2)],
 'edit_src':[dict(action='open_params_pair',target='A')],
 'edit_fx':[dict(action='open_params_pair',target='A'),dict(action='move',target='btn_FX',dt=.8,durationSec=.1),dict(action='click',dt=.2)],
 'edit_lfo':[dict(action='open_params_pair',target='A'),dict(action='move',target='cv_Deck A/Mandala/L1_lfo',dt=.8,durationSec=.1),dict(action='click',dt=.2)],
 'edit_seq':[dict(action='open_params_pair',target='A'),dict(action='move',target='cv_Deck A/Mandala/L1_seq',dt=.8,durationSec=.1),dict(action='click',dt=.2)],
 'edit_mst':[dict(action='open_params_pair',target='A'),dict(action='move',target='edit_side_MST',dt=.8,durationSec=.1),dict(action='click',dt=.2)],
 'edit_trans':[dict(action='open_params_pair',target='A'),dict(action='move',target='edit_side_OUT',dt=.8,durationSec=.1),dict(action='click',dt=.2)],
}
def lum(a):
    c=a/255.; l=np.where(c<=.03928,c/12.92,((c+.055)/1.055)**2.4); return .2126*l[...,0]+.7152*l[...,1]+.0722*l[...,2]
os.makedirs(H+'/results/contrast',exist_ok=True)
out=open(H+'/results/contrast.txt','w'); env=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache',LSD_TOUR_W='1280',LSD_TOUR_H='720')
for theme in ('light','dark'):
  bad={}
  for name,steps in V.items():
    st=[dict(t=.1,action='theme',target=theme),dict(t=.2,action='load_source',deck='A',target='mandala'),dict(t=.2,action='load_source',deck='B',target='voronoi_cells'),dict(t=.2,action='load_source',deck='BG',target='metaballs')]; t=1.4
    for s in steps:
        s=dict(s); t+=s.pop('dt',.4); st.append(dict(t=round(t,2),**s))
    t+=.8; st.append(dict(t=round(t,2),action='audit_rects',target=name)); snap=t+.05
    json.dump(dict(seed=7,bpm=128.0,durationSec=t+.5,tooltips=False,steps=st),open(f'{A}/scripts/ct.json','w'))
    p=subprocess.run([BIN,f'--tour={A}/scripts/ct.json',f'--tour-out={A}/out/ct.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=1','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=env,capture_output=True,text=True)
    png=f'{H}/results/contrast/{theme}_{name}.png'; subprocess.run(['ffmpeg','-v','error','-y','-ss',str(snap),'-i',f'{A}/out/ct.mp4','-frames:v','1',png])
    m=re.search(r'TOUR_RECTS \S+ t=\S+ (.*)',p.stdout)
    if not m or not os.path.exists(png): print('no data',theme,name); continue
    im=np.asarray(Image.open(png).convert('RGB'),dtype=float); L=lum(im)
    for it in m[1].split('\t'):
        k,_,r=it.rpartition('=')
        if re.match(r'(b:\d*$|btn_\W*\d*$|deck_fader|crossfader|seq_bars|slider_|wave_|fxpill|btnid_lobe)',k) or k.startswith('b:'): continue
        x0,y0,x1,y1=map(int,r.split(','))
        if x1-x0<6 or y1-y0<6 or x0<0 or y0<0 or x1>1280 or y1>720 or x0>845 and y0>30: continue
        reg=L[y0+1:y1-1,x0+1:x1-1].ravel()
        bg=np.median(reg); d=np.abs(reg-bg); fg=reg[np.argsort(d)[-max(3,reg.size//100):]].mean()
        ratio=(max(fg,bg)+.05)/(min(fg,bg)+.05)
        if ratio<3 and d.max()>.002:
            kk=re.sub(r'(Deck A/|Deck B/|Mandala/|deckA_fx_|deckB_fx_)','',k)
            if kk not in bad or ratio<bad[kk][0]: bad[kk]=(round(float(ratio),2),name)
    print(theme,name,'done',len(bad),flush=True)
  out.write(f'== {theme}: {len(bad)} widgets under 3:1\n')
  for k,(r,v) in sorted(bad.items(),key=lambda x:x[1][0]): out.write(f'{r:5} {v:10} {k}\n')
out.close()
