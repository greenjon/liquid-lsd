import json,re,glob,os
W='/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/../wt'
SRC=[s['source'] for s in json.load(open('scripts/generators.index.json'))]
idx=[];st=[{"t":0,"action":"crossfade","value":-1.0},{"t":.1,"action":"output_view","value":1},{"t":.2,"action":"move","x":5,"y":5,"durationSec":.1}];t=.5
for s in SRC:
    fp=f'{W}/library/sources/{s}/{s}.fs'
    if not os.path.exists(fp): continue
    txt=open(fp,errors='ignore').read()
    bounds={}
    for m in re.finditer(r'\{[^{}]*?"NAME"\s*:\s*"(\w+)"[^{}]*?\}',txt):
        b=m.group(0)
        if '"float"' not in b: continue
        a=re.search(r'"MIN"\s*:\s*(-?[\d.]+)',b);c=re.search(r'"MAX"\s*:\s*(-?[\d.]+)',b)
        if a and c: bounds[m.group(1)]=(float(a.group(1)),float(c.group(1)))
    try:
        d=json.load(open(f'{W}/src/main/resources/stock_defaults/sources/{s}.json'))
    except Exception: d={}
    bound={b.get('parameterId') for k in d.get('macroBank',{}).get('knobs',[]) for b in k.get('bindings',[])}
    st.append({"t":t,"action":"load_source","deck":"A","target":s}); t+=1.5
    for p,(lo,hi) in bounds.items():
        if p in bound: continue
        st.append({"t":t,"action":"set_param","deck":"A","param":p,"value":lo})
        st.append({"t":t+.5,"action":"set_param","deck":"A","param":p,"value":hi})
        idx.append({"source":s,"param":p,"a":t+.45,"b":t+.95});t+=1.0
        st.append({"t":t,"action":"set_param","deck":"A","param":p,"value":(lo+hi)/2}); t+=.1
json.dump({"seed":7,"bpm":128.0,"durationSec":t+.5,"steps":st},open('scripts/params.json','w'));json.dump(idx,open('scripts/params.index.json','w'));print('params',len(idx),'sim s',round(t))
st=[{"t":0,"action":"crossfade","value":-1.0},{"t":.1,"action":"output_view","value":1},{"t":.2,"action":"move","x":5,"y":5,"durationSec":.1}];t=.5;li=[]
for s in ("mandelbulb","mandelbox","kifs"):
    st.append({"t":t,"action":"load_source","deck":"A","target":s});t+=1
    for ax in ("RotateX","RotateY","RotateZ"):
        st.append({"t":t,"action":"set_param","deck":"A","param":ax,"value":-3.14}); t+=.2
        st.append({"t":t,"action":"set_param","deck":"A","param":ax,"value":3.14,"durationSec":6.0}); li.append({"source":s,"axis":ax,"start":t,"end":t+6}); t+=6.1
        st.append({"t":t,"action":"set_param","deck":"A","param":ax,"value":0.0}); t+=.1
json.dump({"seed":7,"bpm":128.0,"durationSec":t+.5,"steps":st},open('scripts/light.json','w'));json.dump(li,open('scripts/light.index.json','w'))
g=sorted(glob.glob(W+'/library/presets/garden/**/*.lsd',recursive=True))[20]
for bg in (0,1):
    st=[{"t":0,"action":"crossfade","value":-1.0},{"t":.05,"action":"load_source","deck":"BG","target":"quasicrystal"},{"t":.1,"action":"deck_level","deck":"BG","value":float(bg)},{"t":.15,"action":"output_view","value":1},{"t":.2,"action":"move","x":5,"y":5,"durationSec":.1},{"t":.5,"action":"load_preset","deck":"A","target":g}]
    json.dump({"seed":7,"bpm":128.0,"durationSec":5,"steps":st},open(f'scripts/flamebg_{bg}.json','w'))
