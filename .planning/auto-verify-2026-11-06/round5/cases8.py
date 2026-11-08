"""Round 5 leftovers: sources (section 4), sequencer (9). CASES=cases8 python3 round5/ops5.py <work dir> [case...]"""
import re
from dsl import B
CASES={};CHECKS={};SETUP={}
def case(f): CASES[f.__name__]=f; return f
def chk(name):
    def d(f): CHECKS[name]=f; return f
    return d
def par(b,l,path,bank='deckA'): b.t+=.2; b.st('audit_param',target=l,param=path,deck=bank); b.t+=.05; return b
def keys(b,l): b.t+=.1; b.st('audit_keys',target=l); b.t+=.05; return b
Z='Deck A/View/Zoom'
@case
def s4_zoom_reset():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.t+=.8; b.st('open_params_pair',target='A'); b.t+=.8; par(b,'a',Z)
    b.click('cv_Deck A/View/Zoom_label'); b.t+=.5; b.go(x=594,y=423); b.st('drag',x=700,y=423,durationSec=.4); b.t+=.8; par(b,'b',Z); b.shot('zoomed')
    b.st('load_source',deck='A',target='voronoi_cells'); b.t+=.8; b.click('b:Discard'); b.t+=1.0; par(b,'c',Z)
    b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=.8; b.click('b:Discard'); b.t+=1.5; par(b,'d',Z); b.state('d'); b.shot('preset')
    return b
@chk('s4_zoom_reset')
def _(r):
    z=[float(r.param[x]['base']) for x in 'abcd']
    r.check('Deck Zoom moves with the knob',abs(z[1]-z[0])>.05,z)
    r.check('picking another source resets it',abs(z[2]-z[0])<1e-3,z)
    r.check('a preset keeps its saved framing (2.036)',abs(z[3]-2.0358)<1e-3,z)
SEQ='cv_Deck A/Mandala/L1_seq'
@case
def s9_seq_disc():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.t+=.8; b.st('open_params_pair',target='A'); b.t+=.8; b.click(SEQ); b.t+=.6; b.shot('seq'); keys(b,'seq')
    return b
P1='Deck A/Mandala/L1'
def mods(b,l,p=P1): b.t+=.2; b.st('audit_mods',target=l,param=p); b.t+=.05; return b
@case
def s9_clock_unit():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.t+=.8; b.st('open_params_pair',target='A'); b.t+=.8; b.click(SEQ); b.t+=.6; mods(b,'a')
    b.click(x=500,y=413); b.t+=.6; b.click(x=465,y=430); b.t+=.6; mods(b,'b'); b.shot('time')
    b.click(x=500,y=413); b.t+=.6; b.click(x=465,y=463); b.t+=.6; mods(b,'c'); b.shot('frame')
    b.dbl(x=516,y=496); b.t+=.3; b.key('ctrl+a',.1); b.typ('7'); b.key('enter',.5); mods(b,'d'); b.shot('len7')
    return b
def seqmod(r,l):
    for blk in r.mods[l].split(';'):
        if 'sourceId=seq' in blk:
            g=lambda k: re.search(rf'\b{k}=([^,)]*)',blk)[1]
            return dict(sub=float(g('subdivision')),unit=g('genUnit'),n=int(g('seqStepCount')))
@chk('s9_clock_unit')
def _(r):
    b,c,d=(seqmod(r,x) for x in 'bcd')
    r.check('BEAT to TIME keeps the step length (1 beat at 128 BPM = 0.469 s)',b['unit']=='TIME' and abs(b['sub']-60/128)<.01,b)
    r.check('TIME to FRAME converts at the frame rate (0.469 s = 14 frames at the tour\'s 30 fps)',c['unit']=='FRAME' and abs(c['sub']-b['sub']*30)<.5,(b,c))
    r.check('Length takes an odd value (7)',d['n']==7,d)
