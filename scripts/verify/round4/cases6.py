"""Round 4 UI cases: Metaknob editor, learn, sequencer, Chain view Load, undo. CASES=cases6 python3 ../round3/ops3.py <work dir> (PYTHONPATH=round4)."""
import re,os
from dsl import B
CASES={};CHECKS={};SETUP={}
def case(f): CASES[f.__name__]=f; return f
def chk(name):
    def d(f): CHECKS[name]=f; return f
    return d
def base(src='mandala',bloom=True):
    b=B(); b.st('load_source',deck='A',target=src)
    if bloom: b.st('fx_filter',deck='A',slot=0,target='bloom')
    b.t+=.8; return b
def fxtab(b):
    b.st('open_params_pair',target='A'); b.t+=.8; b.click('btn_FX'); b.t+=.4; return b
def dump(b,l): b.t+=.2; b.st('audit_dump',target=l); b.t+=.05; return b
def mods(b,l,p): b.t+=.2; b.st('audit_mods',target=l,param=p); b.t+=.05; return b
TG=lambda r,l:re.search(r'A\.fx0=[^|]*targets=\[([^\]]*)\]',r.dump[l])[1]
# ---- section 7
@case
def meta_add_remove():
    b=fxtab(base()); b.click(x=77,y=525); b.t+=.3; dump(b,'a'); b.click('b:Add Target'); b.t+=.2; b.click('m:Threshold'); b.t+=.3; dump(b,'b'); b.shot('b')
    return b
@chk('meta_add_remove')
def _(r):
    r.check('Add Target adds the parameter','threshold' in TG(r,'b') and 'threshold' not in TG(r,'a'),(TG(r,'a'),TG(r,'b')))
@case
def meta_remove_all():
    b=fxtab(base()); b.click(x=77,y=525); b.t+=.3; b.shot('open'); b.click(x=345,y=562); b.t+=.4; dump(b,'a'); b.shot('a',.2)
    b.click('fxchain_save_deckA_fx'); b.t+=.6; b.state('s'); b.shot('saved',.2)
    return b
@chk('meta_remove_all')
def _(r):
    r.check('the trash removes the last target','bloomIntensity' not in TG(r,'a'),TG(r,'a'))
@case
def meta_row_menu():
    b=fxtab(base()); b.go('cv_Deck A/FX/FX1/threshold_label'); b.st('rclick',dt=.03); b.t+=.4; b.shot('menu'); b.click('m:Metaknob Target'); b.t+=.4; dump(b,'a'); return b
@chk('meta_row_menu')
def _(r): r.check('one Metaknob Target check in the row menu toggles the target','threshold' in TG(r,'a'),TG(r,'a'))
@case
def meta_shape_menu():
    b=fxtab(base()); b.click(x=77,y=525); b.t+=.3; b.click(x=126,y=561); b.t+=.4; b.shot('menu'); b.st('audit_keys',target='menu'); b.t+=.1; return b
@case
def meta_invert():
    b=fxtab(base()); b.click(x=77,y=525); b.t+=.3; dump(b,'a'); b.click(x=154,y=561); b.t+=.4; dump(b,'b'); return b
@chk('meta_invert')
def _(r): r.check('± toggles Invert Direction',':true:true' in TG(r,'b') or 'true' in TG(r,'b').split(':')[3],(TG(r,'a'),TG(r,'b')))
# ---- section 8
@case
def learn_esc():
    b=base(); b.st('open_params_pair',target='A'); b.t+=.8; b.go('cv_Deck A/Mandala/L1_label'); b.st('rclick',dt=.03); b.t+=.3; b.click('m:OSC Learn'); b.t+=.3; dump(b,'a'); b.key('escape',.4); dump(b,'b'); return b
@chk('learn_esc')
def _(r):
    r.check('OSC Learn arms','osc=true' in r.dump['a'],r.dump['a'][-90:]); r.check('Esc cancels it','osc=false' in r.dump['b'],r.dump['b'][-90:])
@case
def reset_param_undo():
    b=base(bloom=False); b.st('set_param',deck='A',param='L1',value=.77); b.t+=.3; mods(b,'a','Deck A/Mandala/L1'); b.st('open_params_pair',target='A'); b.t+=.8; b.go('cv_Deck A/Mandala/L1_label'); b.st('rclick',dt=.03); b.t+=.3; b.click('m:Reset Parameter to Default'); b.t+=.4; mods(b,'b','Deck A/Mandala/L1'); b.key('ctrl+z',.5); mods(b,'c','Deck A/Mandala/L1'); return b
@chk('reset_param_undo')
def _(r):
    v=[float(re.search(r'base=(\S+)',r.mods[x])[1]) for x in 'abc']
    r.check('Reset Parameter to Default resets',v[1]!=v[0],v); r.check('Ctrl+Z brings it back',abs(v[2]-v[0])<1e-3,v)
# ---- section 9
SEQ='cv_Deck A/Mandala/L1_seq'; P='Deck A/Mandala/L1'
def seqopen():
    b=base(bloom=False); b.st('open_params_pair',target='A'); b.t+=.8; b.click(SEQ); b.t+=.5; return b
@case
def seq_undo_steps():
    b=seqopen(); mods(b,'a',P); b.click('btn_Randomize'); b.t+=.3; mods(b,'b',P); b.key('ctrl+z',.5); mods(b,'c',P); b.click('btn_Invert'); b.t+=.3; mods(b,'d',P); b.key('ctrl+z',.5); mods(b,'e',P); return b
@chk('seq_undo_steps')
def _(r):
    m=lambda l:re.sub(r'\bid=[^,]*,','',r.mods[l])
    r.check('Randomize changes the steps',m('a')!=m('b'),'')
    r.check('Ctrl+Z undoes it',m('a')==m('c'),(m('a')[:200],m('c')[:200]))
    r.check('Invert changes, Ctrl+Z undoes',m('d')!=m('c') and m('e')==m('c'),'')
@case
def seq_euclid():
    b=seqopen(); b.click('btn_Euclid...'); b.t+=.5; b.shot('euclid'); b.st('audit_keys',target='euclid'); b.t+=.1; return b
@case
def lfo_undo():
    b=base(bloom=False); b.st('open_params_pair',target='A'); b.t+=.8; b.click('cv_Deck A/Mandala/L1_lfo'); b.t+=.5; mods(b,'a',P); b.shot('open'); b.click('mod_power_0'); b.t+=.3; mods(b,'b',P); b.key('ctrl+z',.5); mods(b,'c',P); return b
@chk('lfo_undo')
def _(r):
    m=lambda l:r.mods[l]
    r.check('an LFO edit takes effect',m('a')!=m('b'),(m('a')[:120],m('b')[:120])); r.check('Ctrl+Z undoes it',m('a')==m('c'),'')
# ---- Chain view with the mouse
@case
def chain_load_mouse():
    b=base(); b.click('fxchain_more_deckA_fx'); b.t+=.3; b.click('m:Chain View'); b.t+=.7; b.state('a'); b.click('btn_Load'); b.t+=.5; b.state('b'); b.search('kaleid'); b.click('browser_row_fx_kaleidoscope'); b.t+=.5; b.shot('load'); b.state('b2'); b.click('b:FX3'); b.t+=.8; b.state('c'); b.key('ctrl+z',.7); b.state('d'); b.key('escape',.4); b.state('e'); return b
@chk('chain_load_mouse')
def _(r):
    r.check('Chain View opens from the menu',r.ui('a','chain')=='PLAY',r.st['a'].get('ui')); r.check('Load turns the load row on',r.ui('b','chain')=='BROWSE',r.st['b'].get('ui'))
    r.check('the row\'s FX3 button loads the highlighted effect into slot 3','Kaleidoscope' in r.f('c','A','fx').split(',')[2],r.f('c','A','fx'))
    r.check('Ctrl+Z undoes it',r.f('d','A','fx')==r.f('b2','A','fx'),(r.f('b2','A','fx'),r.f('d','A','fx')))
    r.check('Esc leaves Chain view',r.ui('e','chain')=='null',r.st['e'].get('ui'))
# ---- FX chain
@case
def fx_reset_slot_undo():
    b=base(); b.st('meta_knob',deck='A',slot=0,value=.9); b.t+=.3; dump(b,'a'); b.rclick('fxcell_deckA_fx_0'); b.t+=.2; b.click('m:Reset Parameters'); b.t+=.4; dump(b,'b'); b.key('ctrl+z',.6); dump(b,'c'); return b
@chk('fx_reset_slot_undo')
def _(r):
    M=lambda l:float(re.search(r'A\.fx0=[^|]*?meta=([\d.]+)',r.dump[l])[1]); v=[M(x) for x in 'abc']
    r.check('Reset Parameters resets the Metaknob',abs(v[1]-v[0])>.01,v); r.check('Ctrl+Z undoes it',abs(v[2]-v[0])<.01,v)
@case
def fx_browser_load_to():
    b=base(); b.tab('fx'); b.t+=.3; b.search('kaleid'); b.rclick('browser_row_fx_kaleidoscope'); b.t+=.3; b.shot('menu'); b.st('audit_keys',target='loadto'); b.t+=.1; return b
@case
def chain_undo_steps():
    b=base(); b.click('fxchain_more_deckA_fx'); b.t+=.3; b.click('m:Chain View'); b.t+=.7; b.click('btn_Load'); b.t+=.5; b.search('kaleid'); b.state('a'); b.click('browser_row_fx_kaleidoscope'); b.t+=2; b.state('b'); b.click('b:FX3'); b.t+=2; b.state('c'); b.key('ctrl+z',1.5); b.state('d'); b.key('ctrl+z',1.5); b.state('e'); return b
@chk('chain_undo_steps')
def _(r):
    f=[r.f(x,'A','fx') for x in 'abcde']; r.check('one Ctrl+Z per load',f[3]==f[1] and f[4]==f[0],f)
@case
def pair_undo_steps():
    b=base(); b.st('load_source',deck='BG',target='metaballs'); b.t+=.6; b.click(x=955,y=515); b.t+=.5; b.state('p'); b.search('voronoi'); b.click('browser_row_src_voronoi_cells'); b.t+=1.5; b.state('a'); b.search('aurora'); b.click('browser_row_src_aurora_ribbons'); b.t+=1.5; b.state('b'); b.key('ctrl+z',1); b.state('c'); b.key('ctrl+z',1); b.state('d'); return b
@chk('pair_undo_steps')
def _(r):
    s=[r.f(x,'BG','src') for x in 'pabcd']; r.check('pair view: one Ctrl+Z per load',s[3]==s[1] and s[4]==s[0] and r.ui('p','pair')=='BG',s)
# ---- more of 7, 9, 11
@case
def meta_no_target_reload():
    b=fxtab(base()); b.click(x=77,y=525); b.t+=.3; b.click(x=345,y=562); b.t+=.5; dump(b,'a'); b.shot('a',.1); b.st('close_focus'); b.t+=.4; b.shot('row',.2); b.st('fx_chain',deck='A',target='library/fx_chains/tt_chain.lsdfxchain'); b.t+=.7; dump(b,'other'); return b
@chk('meta_no_target_reload')
def _(r): r.check('no targets stays no targets on screen','bloomIntensity' not in TG(r,'a'),TG(r,'a'))
def setup_saved(lib):
    import json
    c=json.load(open(f'{lib}/fx_chains/tt_chain.lsdfxchain')); s=c['slots'][0]; s['metaBindings']=[]; s['linkMetaTargets']=[]
    json.dump(c,open(f'{lib}/fx_chains/zz_empty.lsdfxchain','w'))
@case
def seq_euclid_apply():
    b=seqopen(); mods(b,'a',P); b.click('btn_Euclid...'); b.t+=.5; b.click('b:Apply'); b.t+=.5; mods(b,'b',P); b.shot('applied'); b.key('ctrl+z',.5); mods(b,'c',P); return b
@chk('seq_euclid_apply')
def _(r): r.check('Euclid Apply writes a pattern and Ctrl+Z undoes it',r.mods['a']!=r.mods['b'] and r.mods['a']==r.mods['c'],(r.mods['a'][:100],r.mods['b'][:100]))
@case
def seq_paint():
    b=seqopen(); mods(b,'a',P); b.go('seq_bars_0',ox=-150); b.t+=.1; b.st('drag',target='seq_bars_0',ox=150,durationSec=.4); b.t+=.7; mods(b,'b',P); b.shot('painted'); b.key('ctrl+z',.5); mods(b,'c',P); return b
@chk('seq_paint')
def _(r): r.check('dragging paints the bars, and one Ctrl+Z undoes the whole drag',r.mods['a']!=r.mods['b'] and r.mods['a']==r.mods['c'],(r.mods['a'][:80],r.mods['c'][:80]))
