"""Round 7 (2026-11-14): more checklist items. CASES=cases11 CASE_DIR=round7 RESULTS_DIR=round7 python3 round6/ops6.py <work dir> [case...]"""
import re,os,json,glob,shutil
from dsl import B
CASES={};CHECKS={};SETUP={};ENVS={}
def case(f): CASES[f.__name__]=f; return f
def chk(name):
    def d(f): CHECKS[name]=f; return f
    return d
def keys(b,l): b.t+=.1; b.st('audit_keys',target=l); b.st('audit_rects',target=l); b.t+=.05; return b
def state(b,l): return b.state(l)
CH='library/fx_chains/tt_chain.lsdfxchain'
def libfull(b=None,tab=None):
    b=b or B(); b.full(); b.t+=.3
    if tab: b.click('btn_'+tab); b.t+=.4
    return b
# ---------- discovery
@case
def d_bound():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.st('fx_chain',deck='A',target=CH); b.t+=1
    keys(b,'perform'); b.shot('perform')
    b.click('perf_gen_badge_A'); b.t+=.6; keys(b,'bound'); b.shot('bound'); state(b,'bound')
    b.click('fxcell_deckA_fx_0'); b.t+=.6; keys(b,'slot'); b.shot('slot'); state(b,'slot')
    b.key('escape',.4)
    b.click('btnid_slot_focus_deckA_fx_0'); b.t+=.6; keys(b,'focus'); b.shot('focus')
    return b
@case
def d_edit():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.st('fx_chain',deck='A',target=CH); b.t+=1
    b.st('open_params',target='deckA_fx'); b.t+=1; keys(b,'editfx'); b.shot('editfx')
    b.st('open_params',target='master'); b.t+=1; keys(b,'editm'); b.shot('editm')
    return b
@case
def d_mixer():
    b=B(); b.st('randomization',value=1); b.st('set_page',target='mixer'); b.t+=.8; keys(b,'mixer'); b.shot('mixer')
    b.click('perf_trans_more'); b.t+=.4; keys(b,'transmenu'); b.shot('transmenu'); b.key('escape',.3)
    return b
@case
def d_lib():
    b=libfull(tab='Macros'); b.t+=.4; keys(b,'macros'); b.shot('macros')
    b.click('btn_FX'); b.t+=.4; b.search('bloom'); b.t+=.3; keys(b,'fx'); b.shot('fx')
    return b

# ---------- real cases
def kf(r,l): return open(f'{r.lib}/../../out/keys_c_{r.n}_{l}.txt').read().split('\n')
def srcq(b,names=('tt_alpha','tt_beta','tt_gamma')):
    b=libfull(b); b.search('tt_'); b.t+=.4
    for n in names: b.click('browser_row_src_'+n); b.key('q',.3)
    return b
@case
def s1_recent_after_rename():
    b=libfull(B()); b.search('tt_'); b.t+=.4; b.dbl('browser_row_src_tt_alpha'); b.t+=1.5
    b.click('s:Recent'); b.t+=.5; keys(b,'rec0'); b.click('s:All'); b.t+=.4; b.search('tt_'); b.t+=.4
    b.rclick('browser_row_src_tt_alpha'); b.t+=.4; b.click('m:Rename...'); b.t+=.5; b.key('ctrl+a',.1); b.typ('tt_renamed'); b.t+=.8
    b.click('s:Recent'); b.t+=.5; keys(b,'rec'); b.shot('rec'); state(b,'a'); return b
@chk('s1_recent_after_rename')
def _(r):
    k=kf(r,'rec'); rows=[x for x in k if x.startswith('browser_row')]
    print('   Recent before rename:',[x for x in kf(r,'rec0') if x.startswith('browser_row')][:6])
    r.check('Recent lists the renamed preset','browser_row_src_tt_renamed' in k,rows[:10])
    r.check('Recent no longer lists the old name','browser_row_src_tt_alpha' not in k,rows[:10])
    r.check('deck A follows the rename','file=tt_renamed.lsd' in r.st['a'].get('A',''),r.st['a'].get('A'))
@case
def s1_q_in_queue_cursor():
    b=srcq(B()); state(b,'q0'); b.shot('q0')
    b.click('browser_row_src_tt_alpha'); b.t+=.3
    for i in range(6): b.key('right',.3); b.shot(f'r{i}')
    b.key('down',.3); b.shot('down'); b.key('q',.4); state(b,'q1'); b.shot('q1'); b.key('shift+q',.4); state(b,'q2'); return b
@chk('s1_q_in_queue_cursor')
def _(r):
    q0=r.q('q0'); q1=r.q('q1'); q2=r.q('q2'); bg=(r.q('q0','BG')[0],r.q('q2','BG')[0])
    r.check('Q with the cursor in a queue leaves the A/B queue alone',q1[0]==q0[0],(q0,q1))
    r.check('Shift+Q there leaves both queues alone',q2[0]==q0[0] and bg[0]==bg[1],(q2,bg))
@case
def s2_x_unbinds():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=1
    b.click('perf_gen_badge_A'); b.t+=.6; state(b,'bound'); b.shot('bound')
    b.click('btnid_dock_unbind'); b.t+=.5; state(b,'x'); b.shot('x')
    b.search('tt_'); b.t+=.4; b.click('browser_row_src_tt_gamma'); b.t+=1; state(b,'after'); return b
@chk('s2_x_unbinds')
def _(r):
    r.check('clicking the badge binds deck A source','dock=null' not in r.st['bound']['_raw'],r.ui('bound','dock'))
    r.check('✕ stops the binding',r.ui('x','dock')=='null',r.ui('x','dock'))
    r.check('after ✕ a list click does not load into deck A','file=tt_alpha.lsd' in r.st['after'].get('A',''),r.st['after'].get('A'))
@case
def s1_bound_single_select():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=1
    b.click('perf_gen_badge_A'); b.t+=.6; b.search('tt_'); b.t+=.4
    b.click('browser_row_src_tt_beta'); b.t+=1; state(b,'one')
    b.st('mod',target='ctrl',value=1); b.click('browser_row_src_tt_gamma'); b.st('mod',target='ctrl',value=0); b.t+=1; state(b,'two'); b.shot('two')
    b.rclick('browser_row_src_tt_gamma'); b.t+=.4; keys(b,'menu'); b.shot('menu'); b.key('escape',.3); return b
@chk('s1_bound_single_select')
def _(r):
    r.check('a click in the bound list applies','file=tt_beta.lsd' in r.st['one'].get('A',''),r.st['one'].get('A'))
    m=[x for x in kf(r,'menu') if x.startswith('m:')]
    r.check('Ctrl+click in a bound list gives no multi-selection (menu has no "Add 2"/"Delete 2")',not any(re.search(r'\b2\b',x) for x in m),m)
    print('   ctrl+click result A:',r.st['two'].get('A'))
@case
def s2_main_monitor_binds():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=1
    b.click(x=1050,y=150); b.t+=.6; state(b,'mon'); b.shot('mon'); keys(b,'mon'); return b
@chk('s2_main_monitor_binds')
def _(r):
    print('   main monitor dock:',r.ui('mon','dock'),'pair:',r.ui('mon','pair'),'lib:',r.libm('mon'))
    r.check('main output monitor binds the Master FX chain','master' in (r.ui('mon','dock') or '').lower(),r.ui('mon','dock'))
def nosrc(lib):
    j=json.load(open(lib+'/presets/tt_alpha.lsd')); j['name']='tt_nosrc'; j['visualSourceType']='tt_gone_shader'
    json.dump(j,open(lib+'/presets/tt_nosrc.lsd','w'),indent=2)
@case
def s4_missing_source():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=1
    b=libfull(b); b.search('tt_nosrc'); b.t+=.6; b.go('browser_row_src_tt_nosrc'); b.t+=1.2; b.shot('row'); keys(b,'row')
    b.dbl('browser_row_src_tt_nosrc'); b.t+=1.5; state(b,'a'); b.shot('after'); return b
SETUP['s4_missing_source']=nosrc
@chk('s4_missing_source')
def _(r):
    r.check('loading a preset whose source is missing keeps the deck as it was','file=tt_alpha.lsd' in r.st['a'].get('A','') and 'src=Mandala' in r.st['a'].get('A',''),r.st['a'].get('A'))
    r.check('a toast says why',any('tt_gone' in t.lower() or 'missing' in t.lower() or 'not found' in t.lower() for t in r.to),r.to)
@case
def s4_eject_add_source():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.st('fx_chain',deck='A',target=CH); b.t+=1
    b.click('perf_eject_A'); b.t+=.8; state(b,'ej')
    b.st('open_params',target='deckA'); b.t+=1; keys(b,'card'); b.shot('card')
    b.click('btnid_launchpad_add_Deck A'); b.t+=.8; state(b,'add'); b.shot('add'); keys(b,'add'); return b
@chk('s4_eject_add_source')
def _(r):
    r.check('Eject empties the deck and keeps the chain','src=EMPTY' in r.st['ej'].get('A','') and 'name=tt_chain' in r.st['ej'].get('A_chain',''),(r.st['ej'].get('A'),r.st['ej'].get('A_chain')))
    print('   after Add Source: dock',r.ui('add','dock'),'lib',r.libm('add'),'pair',r.ui('add','pair'))
    r.check('Add Source opens Browse bound to deck A source',(r.ui('add','dock') or 'null')!='null' or 'browser_search' in kf(r,'add'),r.ui('add','dock'))
@case
def s6_load_to_undo():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.st('fx_chain',deck='A',target=CH); b.t+=1; state(b,'c0')
    b=libfull(b,'FX'); b.search('pixelate'); b.t+=.4; b.rclick('browser_row_fx_pixelate'); b.t+=.5; keys(b,'menu'); b.shot('menu')
    b.click('m:Load to Deck A'); b.t+=.6; keys(b,'sub'); b.shot('sub'); b.click('m:Slot 2'); b.t+=.6; state(b,'loaded')
    b.go(x=600,y=650); b.key('ctrl+z',.6); state(b,'undo'); return b
@chk('s6_load_to_undo')
def _(r):
    f=lambda l: re.search(r'fx=(\[[^\]]*\])',r.st[l].get('A','')); g=lambda l: f(l)[1] if f(l) else None
    print('   slots:',g('c0'),g('loaded'),g('undo'))
    r.check('Load to Deck A > Slot 2 puts Pixelate in slot 2',g('loaded') and g('loaded').split(',')[1].strip().lower().startswith('pixelate'),g('loaded'))
    r.check('Ctrl+Z puts the chain back',g('undo')==g('c0'),(g('c0'),g('undo')))
@case
def s16b_playlist_to_queue_undo():
    b=srcq(B(),('tt_alpha',)); state(b,'q0')
    b.rclick('s:ttpl'); b.t+=.5; keys(b,'menu'); b.shot('menu'); b.click('m:Add to the bottom of A/B Queue'); b.t+=.6; state(b,'add')
    b.go(x=600,y=650); b.key('ctrl+z',.6); state(b,'undo')
    b.rclick('s:ttpl'); b.t+=.5; b.click('m:Add to the bottom of BG Queue'); b.t+=.6; state(b,'addbg'); b.go(x=600,y=650); b.key('ctrl+z',.6); state(b,'undobg'); return b
@chk('s16b_playlist_to_queue_undo')
def _(r):
    q0=r.q('q0')[0]; a=r.q('add')[0]; u=r.q('undo')[0]
    r.check('adding a playlist from its menu grows the A/B queue',a and len(a)>len(q0),(q0,a))
    r.check('Ctrl+Z takes the playlist back out of A/B',u==q0,(q0,u))
    b0=r.q('q0','BG')[0]; ba=r.q('addbg','BG')[0]; bu=r.q('undobg','BG')[0]
    r.check('BG: add then Ctrl+Z',ba and len(ba)>len(b0 or []) and bu==b0,(b0,ba,bu))
@case
def s16b_macros_save_from():
    b=libfull(tab='Macros'); b.t+=.4; b.click('btnid_maps_save_bank'); b.t+=.5; keys(b,'menu'); b.shot('menu'); return b
@chk('s16b_macros_save_from')
def _(r):
    m=[x[2:] for x in kf(r,'menu') if x.startswith('m:') and x[2:] not in ('File','Help','Output','View')]
    print('   Save bank from menu:',m)
    r.check('Save bank from offers Decks A/B/BG/PV and Master only',set(m)<= {'Deck A','Deck B','Deck BG','Deck PV','Master','Deck A FX','Deck B FX'} and len(m)>=5,m)
@case
def s3_clock_rand_1920():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.t+=1; b.st('randomization',value=1); b.st('set_page',target='mixer'); b.t+=.8; keys(b,'mixer'); b.shot('mixer')
    b.st('audit_list',deck='A',target='before'); b.t+=.1
    b.click('clock_trig_randDeckA'); b.t+=.8; b.st('audit_list',deck='A',target='rolled'); b.t+=.1; b.shot('rolled')
    b.go(x=700,y=900); b.key('ctrl+z',.8); b.st('audit_list',deck='A',target='undone'); b.t+=.1
    return b
def alist(r,label):
    o={}
    for l in open(f'{r.lib}/../../out/c_{r.n}.log',errors='replace'):
        if 'TOUR_AUDIT list' in l:
            p=l.rstrip().split('\t')
            if p[1].endswith('/'+label): o[p[2]]=[x for x in p if x.startswith('base=')][0]
    return o
@chk('s3_clock_rand_1920')
def _(r):
    a,b_,c=alist(r,'before'),alist(r,'rolled'),alist(r,'undone')
    r.check('RAND A rolls deck A',a!=b_ and len(a)>3,[k for k in a if a[k]!=b_.get(k)][:6])
    r.check('Ctrl+Z after RAND A puts every value back',a==c,[(k,a[k],c.get(k)) for k in a if a[k]!=c.get(k)][:6])
ENVS['s3_clock_rand_1920']={'LSD_TOUR_W':'1920','LSD_TOUR_H':'1080'}
@case
def s3_clock_rand_1280():
    b=B(); b.st('randomization',value=1); b.st('set_page',target='mixer'); b.t+=.8; keys(b,'mixer'); b.shot('mixer'); return b
@chk('s3_clock_rand_1280')
def _(r):
    k=kf(r,'mixer'); print('   rand keys:',[x for x in k if 'rand' in x.lower()])
@case
def d_trans():
    b=B(); b.st('transition',target='film_burn'); b.t+=.5; b.st('trans_param',param='burnSpread',value=.9); b.t+=.5
    b.st('open_params',target='master'); b.t+=1; b.click('edit_side_OUT'); b.t+=.6; b.click('btn_TRANS'); b.t+=.8; keys(b,'edit'); b.shot('edit')
    b.click('btnid_bay_back'); b.t+=.6; b=libfull(b,'Transitions'); b.t+=.5; keys(b,'lib'); b.shot('lib'); return b
@case
def s1_save_transition_places():
    b=B(); b.st('set_page',target='mixer'); b.t+=.4; b.st('transition',target='film_burn'); b.t+=.5; b.st('trans_param',param='burnSpread',value=.9); b.t+=.6
    b.click('perf_trans_save'); b.t+=.8; b.shot('rowsave'); keys(b,'rowsave'); b.typ('tt_tr_row'); b.t+=.8; state(b,'a')
    b.st('trans_param',param='burnSpread',value=.2); b.t+=.4
    b.st('open_params',target='master'); b.t+=1; b.click('edit_side_OUT'); b.t+=.6; b.click('btn_TRANS'); b.t+=.8
    b.click('btnid_mixer_trans_kebab'); b.t+=.5; keys(b,'kebab'); b.shot('kebab')
    b.click('m:Save Transition As...'); b.t+=.8; b.shot('editsave'); b.typ('tt_tr_edit'); b.t+=.8; state(b,'b'); return b
@chk('s1_save_transition_places')
def _(r):
    print('   edit kebab menu:',[x for x in kf(r,'kebab') if x.startswith('m:')])
    print('   transitions now:',sorted(os.listdir(r.lib+'/transitions')))
    r.check("Transitions row's Save writes a file",bool(glob.glob(r.lib+'/transitions/**/tt_tr_row.lsdtrans',recursive=True)))
    r.check("Edit OUT > TRANS Save As writes a file",bool(glob.glob(r.lib+'/transitions/**/tt_tr_edit.lsdtrans',recursive=True)))
@case
def s1_save_transition_library():
    b=B(); b.st('set_page',target='mixer'); b.t+=.4; b.st('transition',target='film_burn'); b.t+=.5; b.st('trans_param',param='burnSpread',value=.9); b.t+=.6
    b.click('perf_trans_picker'); b.t+=.8; keys(b,'bound'); b.shot('bound'); state(b,'bound')
    b.click(x=821,y=380); b.t+=.8; b.shot('dlg'); b.typ('tt_tr_lib'); b.t+=.8; return b
@chk('s1_save_transition_library')
def _(r):
    r.check("the bound Transitions list's save icon writes a file",bool(glob.glob(r.lib+'/transitions/**/tt_tr_lib.lsdtrans',recursive=True)),sorted(os.listdir(r.lib+'/transitions')))
