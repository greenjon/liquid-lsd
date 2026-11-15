import subprocess,json,numpy as np
def load(v):
    p=subprocess.run(['ffmpeg','-v','error','-i',v,'-vf','scale=160:90,format=gray','-r','30','-f','rawvideo','-'],stdout=subprocess.PIPE)
    return np.frombuffer(p.stdout,np.uint8).reshape(-1,90,160).astype(np.float32)
F=load('out/params/raw.mp4');at=lambda t:F[min(int(round(t*30)),len(F)-1)]
idx=json.load(open('scripts/params.index.json'));dead={};n=0
for w in idx:
    base=np.abs(at(w['a'])-at(w['a']-.3)).mean(); eff=np.abs(at(w['b'])-at(w['a'])).mean()
    n+=1
    if eff<max(base*1.3,.4): dead.setdefault(w['source'],[]).append(w['param'])
print(n,'params checked; no clear effect:',sum(len(v) for v in dead.values()))
for s,v in dead.items(): print(' ',s,v)
F=load('out/light/raw.mp4')
print('light: lit luma across a full turn min/median/max, % frames < half median')
for w in json.load(open('scripts/light.index.json')):
    a=F[int(w['start']*30):int(w['end']*30)]; m=np.array([f[f>12].mean() if (f>12).any() else 0 for f in a])
    print(f" {w['source']:12}{w['axis']:9}{m.min():6.0f}{np.median(m):6.0f}{m.max():6.0f}  {(m<np.median(m)*.5).mean()*100:4.0f}%")
A,B=load('out/flamebg_0/raw.mp4')[120],load('out/flamebg_1/raw.mp4')[120]
d=np.abs(A-B);print('flame over BG: dark change',round(float(d[A<12].mean()),1),'bright change',round(float(d[A>150].mean()),1))
print('flames lowest luma_min',min(x['luma_min'] for x in json.load(open('out/flames/metrics.json'))))
