#!/usr/bin/env python3
"""Synthesized test music with known beats, breakdowns and drops: tests/web/.cache/<name>.ogg + <name>.json. Nothing here is anyone's music."""
import json,os,subprocess,sys
import numpy as np
SR=44100; rng=np.random.default_rng(7)
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),'.cache'); os.makedirs(OUT,exist_ok=True)
def env(n,tau): return np.exp(-np.arange(n)/(tau*SR))
def kick(): 
    n=int(.28*SR); t=np.arange(n)/SR; f=48+150*np.exp(-t/.03); return np.sin(2*np.pi*np.cumsum(f)/SR)*env(n,.09)
def snare():
    n=int(.2*SR); return (rng.standard_normal(n)*.7+np.sin(2*np.pi*190*np.arange(n)/SR)*.5)*env(n,.05)
def hat():
    n=int(.05*SR); x=rng.standard_normal(n); return np.diff(x,prepend=0)*env(n,.012)
def add(b,s,t,g=1.):
    i=int(t*SR); e=min(len(b),i+len(s))
    if i<len(b): b[i:e]+=s[:e-i]*g
def track(name,bpm,sections,kick_on=None,snare_on=(1,3),hats=2,level=.8):
    """sections: list of (kind,bars) kind in groove/breakdown. Beats are exact."""
    bl=60/bpm; total=sum(b for _,b in sections)*4*bl; x=np.zeros(int(total*SR)+SR); t=0.; truth=dict(bpm=bpm,sections=[],beats_per_bar=4)
    K,S,H=kick(),snare(),hat(); kick_on=kick_on or (0,1,2,3)
    notes=[55,55,65.4,49]
    for kind,bars in sections:
        truth['sections'].append(dict(kind=kind,start=round(t,4),end=round(t+bars*4*bl,4)))
        for bar in range(bars):
            note=notes[bar%4]; n=int(4*bl*SR); tt=np.arange(n)/SR; seg=np.zeros(n)
            if kind=='groove':
                for b in kick_on: add(x,K,t+b*bl,.95)
                for b in snare_on: add(x,S,t+b*bl,.45)
                for h in range(4*hats): add(x,H,t+h*bl/hats,.22)
                # off-beat bass and a mid stab
                for b in range(4):
                    i=int((b+.5)*bl*SR); m=int(.4*bl*SR); seg[i:i+m]+=.42*np.sign(np.sin(2*np.pi*note*tt[:m]))*env(m,.09)*.8
                i=int(1.5*bl*SR); m=int(.3*bl*SR); seg[i:i+m]+=.14*np.sin(2*np.pi*note*8*tt[:m])*env(m,.1)
            else:
                # no low end: a thin pad and a riser of hats
                seg+=.12*(np.sin(2*np.pi*note*6*tt)+np.sin(2*np.pi*note*9*tt))
                for h in range(4): add(x,H,t+h*bl,.12)
            i=int(t*SR); x[i:i+n]+=seg; t+=4*bl
    x=x/np.max(np.abs(x))*level
    pcm=(x*32767).astype(np.int16).tobytes()
    ogg=f'{OUT}/{name}.ogg'
    subprocess.run(['ffmpeg','-v','error','-y','-f','s16le','-ar',str(SR),'-ac','1','-i','-','-c:a','libvorbis','-b:a','96k',ogg],input=pcm,check=True)
    truth['duration']=len(x)/SR
    json.dump(truth,open(f'{OUT}/{name}.json','w'),indent=1)
    print(name,round(truth['duration']),'s',os.path.getsize(ogg)//1024,'KB')
g=lambda b:('groove',b); d=lambda b:('breakdown',b)
if not os.path.exists(f'{OUT}/dance128.ogg'):
    track('dance128',128,[g(40),d(16),g(32),d(12),g(32)])
    track('dnb174',174,[g(200)],kick_on=(0,2.5),snare_on=(1,3),hats=4)
    track('hiphop85',85,[g(90)],kick_on=(0,2.5),snare_on=(1,3),hats=2)
    track('steady128',128,[g(800)])
