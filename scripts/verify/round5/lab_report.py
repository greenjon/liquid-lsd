#!/usr/bin/env python3
"""lab_report.py <lab2 dir>: per part of a labrun2.py run, the live tempo, beat-phase steadiness and signal levels. Numbers only."""
import sys,math,statistics as st
S=sys.argv[1]; marks=[l.rstrip('\n').split(' ',1) for l in open(S+'/marks.txt')]
rows=[]
for l in open(S+'/app.log',errors='replace'):
    if l.startswith('LIVE_CV'):
        p=l.split(); rows.append((float(p[1]),{k:float(v) for k,v in (x.split('=') for x in p[2:])}))
for i,(t,label) in enumerate(marks):
    if not label.startswith('play'): continue
    a=float(t); b=float(marks[i+1][0]) if i+1<len(marks) else 1e12
    r=[(tt,d) for tt,d in rows if a+20<=tt<b]
    if not r: continue
    bpm=[d['bpm'] for _,d in r]; m=st.median(bpm)
    # steadiness: the clock's phase against an ideal grid at the median tempo; 1 = locked to a steady grid, 0 = drifting
    z=sum(complex(math.cos(2*math.pi*(d['phase']-tt*m/60)),math.sin(2*math.pi*(d['phase']-tt*m/60))) for tt,d in r)/len(r)
    moves=sum(1 for j in range(1,len(bpm)) if abs(bpm[j]/bpm[j-1]-1)>.03)
    print(f"{label[5:]:28} bpm {m:6.1f} [{min(bpm):.0f}-{max(bpm):.0f}] tempo moves {moves:2}  phase steadiness {abs(z):.2f}  bass {st.mean(d['bass'] for _,d in r):.2f}  energy {st.mean(d['energy'] for _,d in r):+.2f}  drop {sum(d['drop']>.5 for _,d in r)}")
