"""Scripted UI cases for sections 2, 6 and 7 of the checklist: binding, pair view, Edit, FX chain gestures, Super Knob. CASES=cases4 python3 ops3.py <work dir>."""
import re
from dsl import B
CASES={};CHECKS={};SETUP={}
def case(f): CASES[f.__name__]=f; return f
def chk(name):
    def d(f): CHECKS[name]=f; return f
    return d
def rowS(n): return 'browser_row_src_'+n
def base():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('load_source',deck='B',target='voronoi_cells'); b.t+=.8; return b
def lst(r,lab,deck,field): 
    m=re.search(field+r'=\[([^\]]*)\]',r.st.get(lab,{}).get(deck+'_fx','')); return [x.strip() for x in m[1].split(',')] if m else None
def sup(r,lab,deck='A'):
    m=re.search(r'super=([\d.]+)',r.st.get(lab,{}).get(deck+'_fx','')); return float(m[1]) if m else None
# ---- binding
@case
def bind_dbl():
    b=base(); b.click('perf_gen_badge_A'); b.t+=.3; b.state('a'); b.search('metaballs'); b.dbl(rowS('metaballs')); b.state('b',.8); return b
@chk('bind_dbl')
def _(r):
    r.check('a double-click applies',"Metaballs" in r.st['b']['A'],r.st['b']['A'][:60])
    r.check('and returns to the plain Library',r.ui('b','dock') in ('null',None),r.st['b'].get('ui'))
@case
def bind_again():
    b=base(); b.click('perf_gen_badge_A'); b.t+=.3; b.state('a'); b.click('perf_gen_badge_A'); b.state('b',.4); return b
@chk('bind_again')
def _(r):
    r.check('first click binds',r.ui('a','dock') not in ('null',None),r.st['a'].get('ui'))
    r.check('a second click stops, nothing applied',r.ui('b','dock') in ('null',None) and 'Kifs' in r.st['b']['A'],r.st['b'].get('ui'))
@case
def bind_page():
    b=base(); b.click('perf_gen_badge_A'); b.t+=.3; b.state('a'); b.click('perf_tab_mixer'); b.state('b',.4); return b
@chk('bind_page')
def _(r):
    r.check('changing page stops the binding',r.page('b')=='mixer' and r.ui('b','dock') in ('null',None),(r.page('b'),r.st['b'].get('ui')))
@case
def bind_nomove():
    b=base(); b.state('a'); b.click('perf_gen_badge_A'); b.t+=.3; b.state('b'); b.click('fxchain_name_deckA_fx'); b.t+=.3; b.state('c'); b.click('fxcell_deckA_fx_1'); b.t+=.3; b.state('d'); b.st('set_page',target='mixer'); b.t+=.5; b.click('perf_trans_picker'); b.t+=.3; b.state('e'); return b
@chk('bind_nomove')
def _(r):
    r.check('source badge binds in place, no pair view',r.ui('b','pair')=='null' and 'deckA' in r.ui('b','dock'),r.st['b'].get('ui'))
    r.check('chain name binds in place',r.ui('c','pair')=='null' and 'FxChain' in r.ui('c','dock'),r.st['c'].get('ui'))
    r.check('FX slot binds in place',r.ui('d','pair')=='null' and 'FxChain' in r.ui('d','dock'),r.st['d'].get('ui'))
@case
def bind_mon():
    b=base(); b.click(x=955,y=378); b.t+=.4; b.state('a'); b.key('escape'); b.t+=.2; b.click('mixer_master_badge'); b.t+=.4; b.state('b'); return b
@chk('bind_mon')
def _(r):
    r.check('deck monitor binds the deck source',r.ui('a','pair')=='null' and 'deckA' in (r.ui('a','dock') or ''),r.st['a'].get('ui'))
    r.check('Master badge binds or pairs the Master FX chain','aster' in (r.st['b'].get('ui') or '') or 'MASTER' in (r.st['b'].get('ui') or ''),r.st['b'].get('ui'))
@case
def pair_off_page():
    b=base(); b.st('load_source',deck='BG',target='metaballs'); b.t+=.6; b.click(x=955,y=515); b.t+=.4; b.state('a'); b.shot('pair'); b.click('perf_tab_mixer'); b.state('b',.4); b.st('set_page',target='ab'); b.t+=.3; b.click(x=955,y=515); b.t+=.4; b.key('escape'); b.state('c',.4); return b
@chk('pair_off_page')
def _(r):
    r.check('the BG monitor from the A/B page opens the pair view',r.ui('a','pair')=='BG',r.st['a'].get('ui'))
    r.check('a page tab closes the pair view',r.ui('b','pair')=='null',r.st['b'].get('ui'))
    r.check('Esc leaves it and drops the dock selection',r.ui('c','pair')=='null' and r.ui('c','dock') in ('null',None),r.st['c'].get('ui'))
@case
def dirty_asks_once():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=.9; b.st('set_param',deck='A',param='L1',value=.77); b.t+=.4; b.click('perf_gen_badge_A'); b.t+=.3; b.search('metaballs'); b.click(rowS('metaballs')); b.state('a',.6); b.key('enter',.8); b.state('b',.5); b.search('voronoi'); b.click(rowS('voronoi_cells')); b.state('c',.8); b.key('ctrl+z',.7); b.state('d'); return b
@chk('dirty_asks_once')
def _(r):
    r.check('trying a source on an unsaved deck waits for the prompt',r.f('a','A','file')=='tt_alpha.lsd',r.st['a']['A'][:70])
    r.check('Enter answers it and the source loads',r.f('b','A','src') and 'Metaballs' in r.f('b','A','src'),r.st['b']['A'][:70])
    r.check('the next source does not ask again',r.f('c','A','src') and 'Voronoi' in r.f('c','A','src'),r.st['c']['A'][:70])
    r.check('Ctrl+Z still undoes each load',r.f('d','A','src') and 'Metaballs' in r.f('d','A','src'),r.st['d']['A'][:70])
# ---- Edit
@case
def edit_rail():
    b=base(); b.st('open_params',target='deckA'); b.t+=.6; b.shot('a'); b.click('edit_side_B'); b.shot('b'); b.click('edit_side_BG'); b.shot('c'); b.click('edit_side_PV'); b.shot('d'); b.click('edit_side_OUT'); b.shot('e'); b.click('edit_side_MST'); b.shot('f'); b.state('z'); return b
@chk('edit_rail')
def _(r): r.check('all six rail buttons are on screen and click',not r.warn,r.warn[:3])
# ---- FX chain gestures
@case
def fx_wheel():
    b=base(); b.st('fx_filter',deck='A',slot=1,target='bloom'); b.t+=.5; b.state('a'); b.go('fxcell_deckA_fx_1'); b.st('scroll',value=-1,dt=.05); b.t+=.5; b.state('b'); b.st('mod',target='ctrl',value=1); b.t+=.08; b.st('scroll',value=-1,dt=.05); b.t+=.3; b.st('mod',target='ctrl',value=0); b.t+=.5; b.state('c'); b.key('ctrl+z',.7); b.state('d'); return b
@chk('fx_wheel')
def _(r):
    r.check('a plain wheel over a slot leaves the effect',r.f('a','A','fx')==r.f('b','A','fx'),(r.f('a','A','fx'),r.f('b','A','fx')))
    r.check('Ctrl + wheel steps the effect',r.f('b','A','fx')!=r.f('c','A','fx'),(r.f('b','A','fx'),r.f('c','A','fx')))
@case
def fx_reset_undo():
    b=base(); b.st('fx_filter',deck='A',slot=1,target='bloom'); b.t+=.5; b.st('fx_param',deck='A',slot=1,param='bloomIntensity',value=.9); b.t+=.3; b.st('audit_list',deck='A',slot=1,target='before'); b.t+=.1; b.rclick('fxcell_deckA_fx_1'); b.t+=.2; b.click('m:Reset Parameters'); b.t+=.4; b.st('audit_list',deck='A',slot=1,target='reset'); b.t+=.1; b.key('ctrl+z',.6); b.st('audit_list',deck='A',slot=1,target='undone'); b.t+=.1; b.state('z'); return b
@chk('fx_reset_undo')
def _(r):
    r.check('menu item reached',not any('Reset' in w for w in r.warn),r.warn[:2])
@case
def fx_revert_undo():
    b=base(); b.st('fx_chain',deck='A',target='library/fx_chains/tt_chain.lsdfxchain'); b.t+=.6; b.st('fx_dry_wet',deck='A',value=.4); b.t+=.3; b.state('a'); b.click('fxchain_more_deckA_fx'); b.t+=.2; b.click('m:Revert to Saved'); b.state('b',.5); b.key('ctrl+z',.6); b.state('c'); return b
@chk('fx_revert_undo')
def _(r):
    wa,wb,wc=r.f('a','A_chain','wet'),r.f('b','A_chain','wet'),r.f('c','A_chain','wet')
    r.check('Revert to Saved brings the saved Dry/Wet back',wb!=wa,(wa,wb)); r.check('Ctrl+Z undoes the revert',wc==wa,(wa,wb,wc))
@case
def fx_wet_label():
    b=base(); b.st('fx_dry_wet',deck='A',value=.4); b.shot('w40',.6); b.st('fx_dry_wet',deck='A',value=0); b.shot('w0',.6); b.state('z'); return b
@case
def fx_swap_links():
    b=base(); b.st('fx_filter',deck='A',slot=0,target='bloom'); b.st('fx_filter',deck='A',slot=1,target='rgb_split'); b.st('fx_link',deck='A',slot=0,value=1); b.st('fx_link',deck='A',slot=1,value=0); b.t+=.4; b.state('a'); b.st('fx_swap',deck='A',slot=0,slot2=1); b.state('b',.5); return b
@chk('fx_swap_links')
def _(r):
    a,c=lst(r,'a','A','links'),lst(r,'b','A','links')
    r.check('swapping effects: each slot keeps its own link setting',a[:2]==c[:2] and a[:2]==['true','false'],(a,c))
    r.check('and the effects did swap',r.f('a','A','fx')!=r.f('b','A','fx'),(r.f('a','A','fx'),r.f('b','A','fx')))
# ---- Super Knob
def sk(mode):
    b=base(); b.st('super_mode',target=mode); b.st('fx_filter',deck='A',slot=0,target='bloom'); b.st('fx_filter',deck='A',slot=1,target='rgb_split'); b.t+=.4
    b.st('super_knob',deck='A',value=0); b.t+=.2; b.st('fx_link',deck='A',slot=0,value=0); b.st('fx_link',deck='A',slot=1,value=0); b.t+=.2
    b.st('fx_link',deck='A',slot=0,value=1); b.st('fx_link',deck='A',slot=1,value=1); b.t+=.2; b.st('meta_knob',deck='A',slot=0,value=.2); b.st('meta_knob',deck='A',slot=1,value=.9); b.t+=.3; return b
@case
def super_relative():
    b=sk('RELATIVE'); b.st('super_knob',deck='A',value=.5); b.state('a',.4); b.st('super_knob',deck='A',value=.6); b.state('b',.4); b.st('super_knob',deck='A',value=1); b.state('c',.4); b.st('super_knob',deck='A',value=0); b.state('d',.4); b.st('super_knob',deck='A',value=.5); b.state('e',.4); return b
@chk('super_relative')
def _(r):
    m=lambda l:[float(x) for x in lst(r,l,'A','metas')[:2]]
    a,b,c,d=m('a'),m('b'),m('c'),m('d')
    r.check('Relative: both rise, staying apart',b[0]>a[0] and b[1]>=a[1] and b[0]<b[1],(a,b))
    r.check('Relative: reach 100% together',c[0]>.99 and c[1]>.99,c)
    r.check('Relative: reach 0% together',d[0]<.01 and d[1]<.01,d)
@case
def super_pickup():
    b=sk('PICKUP'); b.state('a',.4); b.st('super_knob',deck='A',value=.1); b.state('b',.4); b.st('super_knob',deck='A',value=.25); b.state('c',.4); b.st('super_knob',deck='A',value=.95); b.state('d',.4); return b
@chk('super_pickup')
def _(r):
    w=lambda l:lst(r,l,'A','wait')[:2]; m=lambda l:[float(x) for x in lst(r,l,'A','metas')[:2]]
    r.check('Pickup: both wait at first',w('a')==['true','true'],w('a'))
    r.check('Pickup: slot at 20% is not moved until the knob reaches it',abs(m('b')[0]-.2)<.01,m('b'))
    r.check('Pickup: past 20% it follows, the 90% slot still waits',w('c')==['false','true'] and abs(m('c')[1]-.9)<.01,(w('c'),m('c')))
    r.check('Pickup: past 90% both follow',w('d')==['false','false'],(w('d'),m('d')))
@case
def super_hand():
    b=sk('RELATIVE'); b.st('super_knob',deck='A',value=.5); b.t+=.4; b.st('meta_knob',deck='A',slot=0,value=.7); b.state('a',.6); b.t+=1; b.state('b',.4); return b
@chk('super_hand')
def _(r):
    a,b=float(lst(r,'a','A','metas')[0]),float(lst(r,'b','A','metas')[0])
    r.check('a Metaknob moved by hand is not yanked back',abs(a-.7)<.01 and abs(b-.7)<.01,(a,b))

@case
def space_pause():
    b=base(); b.click('perf_gen_badge_A'); b.t+=.3; b.state('a'); b.key('space',.3); b.state('b'); b.key('space',.3); b.state('c'); b.shot('c'); b.st('library_mode',target='FULL'); b.t+=.5; b.state('d'); b.key('escape',.4); b.state('e'); return b
@chk('space_pause')
def _(r):
    r.check('Space pauses (Library maximized), Space again resumes the binding',r.libm('b')=='FULL' and r.ui('c','dock') not in ('null',None),(r.libm('b'),r.st['c'].get('ui')))
    r.check('Esc from maximized comes back still bound',r.ui('e','dock') not in ('null',None) and r.libm('e')!='FULL',(r.libm('e'),r.st['e'].get('ui')))
# ---- sequencer toolbar: real clicks, then look at the sheet
@case
def seq_toolbar():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.t+=.8; b.st('open_params',target='deckA'); b.t+=.6; b.click('cv_Deck A/Mandala/Lobes_seq'); b.t+=.5; b.shot('0_open')
    for i,l in enumerate(['Randomize','Invert','Shift <','Shift >','Copy','Paste','Clear']):
        b.click('btn_'+l); b.shot(f'{i+1}_{l[:5].strip()}',.3)
    b.key('ctrl+z',.5); b.shot('8_undo'); b.state('z'); return b
@chk('seq_toolbar')
def _(r): r.check('every toolbar button is on screen and clicks',not r.warn,r.warn[:3])

