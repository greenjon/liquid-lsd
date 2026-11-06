#!/usr/bin/env python3
"""Summarises results/*.json: binding failures, parameters with no effect (frames byte-identical to the baseline), and values with a tiny effect."""
import glob,json,os
H=os.path.dirname(os.path.abspath(__file__)); tot=win=0; dead={};faint={};bind={};deadval={};nd=[]
for f in sorted(glob.glob(H+'/results/*_*.json')):
    d=json.load(open(f)); u=d['unit']
    if d['noise_frames']: nd.append((u,d['noise_frames'],d['frames']))
    by={}
    for w in d['win']: by.setdefault(w['param'],[]).append(w); win+=1
    for p,ws in by.items():
        tot+=1
        noisy=any(w['noise'] for w in ws)
        same=[w for w in ws if w['hash_diff']==0]
        if 'want' in ws[0]:
            badb=[w for w in ws if 'base' in w and abs(w['base']-w['want'])>max(1e-3,abs(w['want'])*.02)]
            if badb: bind.setdefault(u,[]).append(f"{p} want {badb[0]['want']:.3g} got {badb[0]['base']:.3g}")
        floor=max(w['noise_mean'] for w in ws)
        if not noisy and len(same)==len(ws): dead.setdefault(u,[]).append(p)
        elif noisy and max(w['mean'] for w in ws)<=floor*1.5+.05: dead.setdefault(u,[]).append(p+'?')
        elif max(w['mean'] for w in ws)<.25 and max(w['big'] for w in ws)<.5: faint.setdefault(u,[]).append(f"{p} {max(w['mean'] for w in ws):.2f}")
        else:
            for w in same: deadval.setdefault(u,[]).append(f"{p}={w['value']:.3g}")
print(tot,'parameters,',win,'value windows,',len(glob.glob(H+'/results/*_*.json')),'units')
print('not deterministic:',nd)
print('\nBINDING FAILED (knob did not set the value):'); [print(' ',u,v) for u,v in bind.items()]
print('\nNO EFFECT at any value, at default settings of everything else:',sum(map(len,dead.values()))); [print(' ',u,v) for u,v in dead.items()]
print('\nFAINT:',sum(map(len,faint.values()))); [print(' ',u,v) for u,v in faint.items()]
print('\nONE VALUE IDENTICAL to default, others work (often a stepped mode):',sum(map(len,deadval.values()))); [print(' ',u,v[:12]) for u,v in deadval.items()]
