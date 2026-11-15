"""Round 8 (2026-11-14 night): CASES=cases12 CASE_DIR=round8 RESULTS_DIR=round8 python3 round8/ops8.py <work dir> [case...]"""
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
def kf(r,l): return open(f'{r.lib}/../../out/keys_c_{r.n}_{l}.txt').read().split('\n')
def rects(r,l):
    for x in open(f'{r.lib}/../../out/c_{r.n}.log',errors='replace'):
        if x.startswith('TOUR_RECTS '+l+' ') or ('TOUR_RECTS '+l+' ') in x:
            o={}
            for p in x.rstrip('\n').split('\t'):
                p=p.split(' ')[-1] if p.startswith('TOUR_RECTS') else p
                if '=' in p:
                    k,v=p.rsplit('=',1)
                    try: o[k]=tuple(map(int,v.split(',')))
                    except: pass
            return o
    return {}
def tips(r,sub=''): return [t for _,t in r.tips if sub.lower() in t.lower()]
def hover(b,k,w=1.4,**kw): b.go(k,**kw); b.t+=w; return b
def srcq(b,names=('tt_alpha','tt_beta','tt_gamma')):
    b=libfull(b); b.search('tt_'); b.t+=.4
    for n in names: b.click('browser_row_src_'+n); b.key('q',.3)
    return b
# ---------- discovery
@case
def d_views():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.st('fx_chain',deck='A',target=CH); b.t+=1
    b=libfull(b); b.t+=.5; keys(b,'libsrc'); b.shot('libsrc')
    b.click('btn_FX'); b.t+=.5; keys(b,'libfx'); b.click('btn_Queues'); b.t+=.5; keys(b,'libq'); b.shot('libq')
    b.st('library_mode',target='HALF'); b.t+=.5
    b.st('open_params',target='deckA_fx'); b.t+=1; keys(b,'editfx'); b.shot('editfx')
    b.st('open_params',target='deckA'); b.t+=1; keys(b,'editsrc'); b.shot('editsrc')
    return b
# ================= §1 Library
def rowS(n): return 'browser_row_src_'+n
def ctrlclick(b,k): b.st('mod',target='ctrl',value=1); b.click(k); b.st('mod',target='ctrl',value=0); return b
def shiftclick(b,k): b.st('mod',target='shift',value=1); b.click(k); b.st('mod',target='shift',value=0); return b
@case
def a_chips_tips():
    b=B(); b.tips=True
    for d in ('A','B','BG','PV'): b.st('load_preset',deck=d,target='library/presets/tt_alpha.lsd')
    for s_ in (0,1,2): b.st('fx_filter',deck='A',slot=s_,target='bloom')
    b.t+=1.5; b=libfull(b); b.search('tt_alpha'); b.t+=.5; hover(b,rowS('tt_alpha'),2.0); b.shot('src'); keys(b,'src')
    b.click('btn_FX'); b.t+=.5; b.search('bloom'); b.t+=.5; keys(b,'fx'); b.shot('fx')
    for k in ('browser_row_fx_bloom','browser_row_fxs_bloom','browser_row_filter_bloom'): pass
    return b
@chk('a_chips_tips')
def _(r):
    t=tips(r,'Loaded on'); print('   tips:',[x[:160] for _,x in r.tips][:8])
    print('   fx keys:',[x for x in kf(r,'fx') if x.startswith('browser_row')])
    r.check('preset row tooltip starts with Loaded on',any(x.startswith('Loaded on') for x in t),[x[:120] for _,x in r.tips][:5])
    r.check('tooltip names all four decks',any(all(d in x for d in ('A','B','BG','PV')) for x in t),t[:2])
@case
def a_ctrlf_esc():
    b=B(); state(b,'p0'); b.go(x=600,y=500); b.key('ctrl+f',.6); state(b,'f1'); b.key('escape',.6); state(b,'e1')
    b.key('ctrl+f',.6); b.typ('zzqq'); b.t+=.3; b.shot('typed'); b.key('escape',.6); state(b,'e2'); b.shot('cleared'); b.key('escape',.6); state(b,'e3'); return b
@chk('a_ctrlf_esc')
def _(r):
    L=lambda l: r.libm(l)
    print('   lib modes:',[L(x) for x in ('p0','f1','e1','e2','e3')])
    r.check('Ctrl+F opens the Library search',L('f1')!=L('p0'),(L('p0'),L('f1')))
    r.check('Esc after Ctrl+F goes back in one press',L('e1')==L('p0'),L('e1'))
    r.check('with text typed, first Esc keeps the Library open',L('e2')==L('f1'),L('e2'))
    r.check('second Esc goes back',L('e3')==L('p0'),L('e3'))
@case
def a_trans_menu_wording():
    b=libfull(tab='Transitions'); b.t+=.4; b.search('film'); b.t+=.4; b.rclick('browser_row_trans_film_burn'); b.t+=.5; keys(b,'m'); b.shot('m'); b.key('escape',.3)
    b.click('s:Saved transitions'); b.t+=.5; b.rclick('browser_row_trans_tt_trans'); b.t+=.5; keys(b,'m2'); b.key('escape',.3); return b
@chk('a_trans_menu_wording')
def _(r):
    for l in ('m','m2'):
        m=[x for x in kf(r,l) if x.startswith('m:')]; print('   ',l,m)
        r.check(f'{l}: menu says Add to Transition Queue',any('Add to Transition Queue' in x for x in m),m)
@case
def a_folder_rename_delete_select():
    b=libfull(); b.click('s:tt_folder'); b.t+=.5; b.shot('in'); b.rclick('s:tt_folder'); b.t+=.4; keys(b,'menu'); b.click('m:Rename Folder...'); b.t+=.5; b.key('ctrl+a',.1); b.typ('tt_folder2'); b.t+=.8
    b.shot('renamed'); keys(b,'renamed'); b.rclick('s:tt_folder2'); b.t+=.4; b.click('m:Delete Folder...'); b.t+=.6; b.shot('deldlg'); keys(b,'deldlg'); b.key('enter',.8); b.shot('deleted'); keys(b,'deleted'); return b
@chk('a_folder_rename_delete_select')
def _(r):
    print('   menu',[x for x in kf(r,'menu') if x.startswith('m:')]); print('   deldlg',[x for x in kf(r,'deldlg') if x.startswith(('b:','m:'))])
    r.check('folder renamed on disk',os.path.isdir(r.lib+'/presets/tt_folder2') or not os.path.isdir(r.lib+'/presets/tt_folder'),os.listdir(r.lib+'/presets')[:5])
    print('   look at shots renamed.png / deleted.png for the selection')
@case
def a_tags_buttons():
    b=libfull(); b.search('tt_'); b.t+=.4; b.click(rowS('tt_alpha')); ctrlclick(b,rowS('tt_gamma')); b.rclick(rowS('tt_gamma')); b.t+=.4; keys(b,'menu'); b.click('m:Add Tag (2)...'); b.t+=.6; keys(b,'dlg'); b.shot('dlg')
    b.click('b:aa_one'); b.t+=.4; b.shot('filled'); b.key('enter',.6); b.click('s:All'); b.t+=.3
    b.click(x=14,y=272); b.t+=.5; keys(b,'tags'); b.shot('tags'); return b
def seedtags(L):
    for n in('tt_alpha','tt_beta'):
        p=f'{L}/presets/{n}.lsd'; d=json.load(open(p)); d['tags']=['aa_one','aa_two']; json.dump(d,open(p,'w'),indent=2)
SETUP['a_tags_buttons']=seedtags
@chk('a_tags_buttons')
def _(r):
    d=[x for x in kf(r,'dlg') if x.startswith(('b:','btn_'))]; print('   dialog buttons:',d[:30])
    r.check('Add Tag dialog offers existing tags as buttons',any('aa_one' in x for x in d),d[:30])
    g=r.js('presets/tt_gamma.lsd').get('tags') or []
    r.check('clicking an existing-tag button fills the field and tags both rows','aa_one' in g and 'aa_one' in (r.js('presets/tt_alpha.lsd').get('tags') or []),g)
    t=[x for x in kf(r,'tags') if x.startswith('s:')]; print('   tree:',t)
    stock={'Generator','Fractal','3D','Organic','Simulation','Filter','Geometric'}
    r.check('stock categories do not show as tags',not any(x[2:] in stock for x in t),t)
@case
def a_slot_stepping():
    b=B(); b.tips=True; b.st('load_source',deck='A',target='mandala'); b.st('fx_filter',deck='A',slot=0,target='bloom'); b.t+=1; state(b,'s0')
    hover(b,'fxcell_deckA_fx_0',1.6); b.shot('hover'); keys(b,'perf')
    for i in range(3): b.click('btnid_slot_next_deckA_fx_0'); b.t+=.6; state(b,f'n{i}')
    return b
SETUP['a_slot_stepping']=lambda L: None
@chk('a_slot_stepping')
def _(r):
    print('   perf keys:',[x for x in kf(r,'perf') if 'deckA_fx_0' in x])
    sl=[re.search(r'fx=(\[[^\]]*\])',r.st[l].get('A',''))[1] for l in ('s0','n0','n1','n2') if l in r.st]; print('   slots:',sl)
    print('   tips:',tips(r)[:4])
# ---------- batch B
KEEP_LIB=set()
@case
def b_ctrlf_from_edit():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=.8; b.st('open_params',target='deckA'); b.t+=1; keys(b,'e0'); b.shot('e0')
    b.go(x=600,y=650); b.key('ctrl+f',.6); keys(b,'f1'); b.shot('f1'); b.key('escape',.6); keys(b,'e1'); b.shot('e1')
    b.key('ctrl+f',.6); b.typ('zzqq'); b.t+=.3; b.key('escape',.6); keys(b,'e2'); b.shot('e2'); b.key('escape',.6); keys(b,'e3'); b.shot('e3'); return b
@chk('b_ctrlf_from_edit')
def _(r):
    ed=lambda l: 'edit_side_A' in kf(r,l)
    print('   edit shown:',[(l,ed(l)) for l in ('e0','f1','e1','e2','e3')])
    r.check('Ctrl+F leaves Edit for the Library',ed('e0') and not ed('f1'))
    r.check('Esc right after goes back in one press',ed('e1'))
    r.check('typed text: first Esc clears and stays',not ed('e2'))
    r.check('second Esc goes back',ed('e3'))
@case
def b_trans_saved_menu():
    b=libfull(tab='Transitions'); b.t+=.4; b.search('tt_'); b.t+=.5; keys(b,'k'); b.rclick('browser_row_trans_tt_trans'); b.t+=.5; keys(b,'m'); b.shot('m'); return b
@chk('b_trans_saved_menu')
def _(r):
    m=[x for x in kf(r,'m') if x.startswith('m:')]; print('   ',m, [x for x in kf(r,'k') if 'row' in x])
    r.check('saved transition menu says Add to Transition Queue',any('Add to Transition Queue' in x for x in m),m)
def favs(L):
    os.makedirs(L+'/..',exist_ok=True)
@case
def b_shortlist_favs():
    b=B(); b.tips=True; b.st('load_source',deck='A',target='mandala'); b.st('fx_filter',deck='A',slot=0,target='pixelate'); b.t+=1
    b=libfull(b,'FX'); b.search('kaleid'); b.t+=.4; b.rclick('browser_row_fx_kaleidoscope'); b.t+=.4; b.click('m:Add to Favorites'); b.t+=.4
    b.search('tt_fx'); b.t+=.4; b.rclick('browser_row_fx_tt_fx'); b.t+=.4; keys(b,'m2'); b.click('m:Add to Favorites'); b.t+=.4
    b.st('library_mode',target='HALF'); b.t+=.6; state(b,'s0'); hover(b,'fxcell_deckA_fx_0',1.5)
    for i in range(14):
        b.st('mod',target='ctrl',value=1); b.st('scroll',value=-1,dt=.05); b.st('mod',target='ctrl',value=0,dt=.05); b.t+=.4; b.state(f'n{i}',.1)
    b.shot('end'); return b
@chk('b_shortlist_favs')
def _(r):
    sl=[re.search(r'fx=\[([^,\]]*)',r.st[l].get('A',''))[1] for l in ['s0']+[f'n{i}' for i in range(14)] if l in r.st]; print('   slot 1 sequence:',sl)
    print('   tips:',[t[:90] for t in tips(r,'Shortlist')][:16])
    r.check('stepping never lands on the saved single FX',not any('tt' in x.lower() for x in sl),sl)
    r.check('stepping reaches the starred Kaleidoscope',any('kaleid' in x.lower() for x in sl),sl)
    r.check('tooltip shows a shortlist position',any(re.search(r'\d+/\d+',t) for t in tips(r,'Shortlist')))
# --- queue split divider
@case
def b_div_drag():
    b=libfull(); b.t+=.4; keys(b,'k0'); b.shot('k0')
    b.go(x=420,y=383); b.t+=.2; b.st('drag',x=420,y=250,durationSec=.5); b.t+=1; keys(b,'k1'); b.shot('k1')
    b.go(x=420,y=None or 250); b.t+=.1
    return b
def qy(r,l): return rects(r,l).get('btnid_queuePrev',(0,0,0,0))[1]
@chk('b_div_drag')
def _(r):
    print('   queuePrev y:',qy(r,'k0'),qy(r,'k1'))
    r.check('dragging the divider up moves the queues up',qy(r,'k1')<qy(r,'k0')-60,(qy(r,'k0'),qy(r,'k1')))
@case
def b_div_clamp_reset():
    b=libfull(); b.t+=.4; keys(b,'k0')
    b.go(x=420,y=383); b.t+=.2; b.st('drag',x=420,y=70,durationSec=.5); b.t+=1; keys(b,'top'); b.shot('top')
    y=None; b.st('audit_rects',target='probe'); b.t+=.1
    return b
@chk('b_div_clamp_reset')
def _(r):
    print('   queuePrev y: start',qy(r,'k0'),'after drag to top',qy(r,'k1') if False else qy(r,'top'))
    r.check('browser keeps a minimum height (queues do not reach the search line)',qy(r,'top')>=200,qy(r,'top'))
@case
def b_div_dbl():
    b=libfull(); b.t+=.4; keys(b,'k0'); b.go(x=420,y=383); b.st('drag',x=420,y=250,durationSec=.5); b.t+=1; keys(b,'k1')
    b.go(x=420,y=None); return b
# persistence pair
@case
def b_div_persist_1():
    b=libfull(); b.t+=.4; b.go(x=420,y=383); b.t+=.2; b.st('drag',x=420,y=260,durationSec=.5); b.t+=1; keys(b,'k1'); b.shot('k1'); return b
ENVS['b_div_persist_1']={'LSD_TOUR_PERSIST':'1'}
@chk('b_div_persist_1')
def _(r):
    p=open(r.lib+'/../lsd-preferences.properties').read() if os.path.exists(r.lib+'/../lsd-preferences.properties') else ''
    print('   saved:',[x for x in p.split('\n') if 'Split' in x], 'queuePrev y',qy(r,'k1'))
@case
def b_div_persist_2():
    b=libfull(); b.t+=.6; keys(b,'k1'); b.shot('k1'); b.click('btnid_lib_queue_split'); b.t+=.6; keys(b,'off'); b.shot('off'); return b
ENVS['b_div_persist_2']={'LSD_TOUR_PERSIST':'1'}
KEEP_LIB.add('b_div_persist_2')
@chk('b_div_persist_2')
def _(r):
    print('   queuePrev y after restart',qy(r,'k1'))
    r.check('divider position survives a restart',190<qy(r,'k1')<330,qy(r,'k1'))
    r.check('rows icon turns the queues off','btnid_queuePrev' not in kf(r,'off'))
@case
def b_div_persist_3():
    b=libfull(); b.t+=.6; keys(b,'k'); b.shot('k'); return b
ENVS['b_div_persist_3']={'LSD_TOUR_PERSIST':'1'}
KEEP_LIB.add('b_div_persist_3')
@chk('b_div_persist_3')
def _(r): r.check('queues stay off after a restart','btnid_queuePrev' not in kf(r,'k'),qy(r,'k'))
# --- queue drops with a playing marker, mixed selection
@case
def b_drop_marker():
    b=srcq(B()); b.dbl(x=60,y=505); b.t+=1.5; state(b,'play')
    b.search('tt_'); b.t+=.3; b.drag(rowS('tt_beta'),x=60,y=489); b.t+=.6; state(b,'drop'); b.shot('drop'); return b
@chk('b_drop_marker')
def _(r):
    p=r.q('play'); d=r.q('drop'); print('   play',p,'drop',d)
    r.check('dropped between rows 1 and 2',d[0] and len(d[0])==4 and d[0][1]=='tt_beta',d)
    r.check('playing marker stays on its item',d[0] and p[0] and d[0][d[1]]==p[0][p[1]],(p,d))
@case
def b_drop_mixed():
    b=libfull(tab='FX'); b.search('tt_'); b.t+=.4; keys(b,'k'); b.click('browser_row_fx_tt_chain'); ctrlclick(b,'browser_row_fx_tt_fx'); b.t+=.2
    b.drag('browser_row_fx_tt_fx',x=60,y=480); b.t+=.6; state(b,'d1'); b.shot('d1'); return b
@chk('b_drop_mixed')
def _(r):
    print('   rows',[x for x in kf(r,'k') if x.startswith('browser_row')]); print('   toasts',r.to); print('   AB',r.q('d1'),'FX',r.q('d1','FX_AB'))

@case
def b_ctrlf_full():
    b=libfull(); state(b,'p0'); b.go(x=600,y=650); b.key('ctrl+f',.6); state(b,'f1'); b.shot('f1'); b.key('escape',.6); state(b,'e1'); b.shot('e1')
    b=libfull(b); b.go(x=600,y=650); b.key('ctrl+f',.6); b.st('chars',target='zzq',dt=.05); b.t+=.8; b.shot('typed'); b.key('escape',.6); state(b,'e2'); b.shot('e2'); b.key('escape',.6); state(b,'e3'); b.shot('e3'); return b
@chk('b_ctrlf_full')
def _(r):
    L=lambda l: r.libm(l); print('   lib:',[(l,L(l)) for l in ('p0','f1','e1','e2','e3')])
    r.check('Ctrl+F then Esc leaves the Library in one press',L('f1')=='FULL' and L('e1')!='FULL')
    r.check('with text, first Esc stays',L('e2')=='FULL')
    r.check('second Esc goes back',L('e3')!='FULL')
@case
def b_drop_mixed2():
    b=libfull(tab='FX'); b.search('tt_'); b.t+=.4; b.click('browser_row_fx_tt_chain'); ctrlclick(b,'browser_row_fx_tt_fx'); b.t+=.2
    b.drag('browser_row_fx_tt_fx',x=250,y=480); b.t+=.6; state(b,'d1'); b.shot('d1')
    b.search('pixel'); b.t+=.4; b.click('browser_row_fx_pixelate'); b.search('tt_chain'); b.t+=.4; ctrlclick(b,'browser_row_fx_tt_chain'); b.t+=.2
    b.drag('browser_row_fx_tt_chain',x=250,y=520); b.t+=.6; state(b,'d2'); b.shot('d2'); return b
@chk('b_drop_mixed2')
def _(r):
    print('   toasts',r.to); print('   FX_AB',r.q('d1','FX_AB'),r.q('d2','FX_AB'))
@case
def b_drop_mixed3():
    b=libfull(); b.search('spiral'); b.t+=.4; keys(b,'k')
    b.click(rowS('dynamic_spiral')); ctrlclick(b,rowS('beat-spiral')); ctrlclick(b,rowS('3d spiral')); b.t+=.2; b.shot('sel')
    b.drag(rowS('beat-spiral'),x=60,y=480); b.t+=.6; state(b,'d1'); b.shot('d1'); return b
@chk('b_drop_mixed3')
def _(r):
    print('   toasts',r.to); print('   AB',r.q('d1'))
    r.check('the saved presets go in',r.q('d1')[0] and set(r.q('d1')[0])=={'beat-spiral','3d spiral'},r.q('d1'))
    r.check('toast counts the skipped stock source',any('1 skipped' in t for t in r.to),r.to)
@case
def b_drop_empty():
    b=libfull(); b.search('spiral'); b.t+=.4
    b.drag(rowS('beat-spiral'),x=80,y=560); b.t+=.8; state(b,'d1'); b.shot('d1')
    b.click(rowS('beat-spiral')); ctrlclick(b,rowS('3d spiral')); b.t+=.3; b.drag(rowS('3d spiral'),x=80,y=600); b.t+=.8; state(b,'d2'); return b
@chk('b_drop_empty')
def _(r): print('   ',r.q('d1'),r.q('d2'),r.to)
# ================= batch C
class T(B):
    bank=0
    def cc(s,ch,n,v,w=.07): s.st('midi',target=f'{ch}:{n}:{v}',dt=.0); s.t+=w; return s
    def turn(s,k,steps=1,w=.05):
        for _ in range(abs(steps)): s.cc(0,k+16*s.bank,65 if steps>0 else 63,w)
        return s
    def press(s,k): return s.cc(1,k+16*s.bank,127)
    def rel(s,k): return s.cc(1,k+16*s.bank,0)
    def tap(s,k,w=.15): s.press(k); s.rel(k); s.t+=w; return s
    def shift(s,on=1): return s.cc(3,10+6*s.bank,127 if on else 0)
    def stap(s,k,w=.15): s.shift(1); s.tap(k,0); s.shift(0); s.t+=w; return s
    def btn(s,base,w=.2): s.cc(3,base+6*s.bank,127); s.cc(3,base+6*s.bank,0); s.t+=w; return s
    def lt(s,**k): return s.btn(8,**k)
    def rm(s,**k): return s.btn(12,**k)
    def rb(s,**k): return s.btn(13,**k)
    def bankstep(s,follow=True):
        s.btn(11)
        if follow: s.bank=(s.bank+1)%3
        s.t+=.3; return s
    def wake(s): s.t+=1.2; s.turn(0,1); s.turn(0,-1); s.t+=1.2; return s
def Tn(src='kifs',dst='voronoi_cells'):
    b=T(); b.st('load_source',deck='A',target=src); b.st('load_source',deck='B',target=dst); b.t+=.8; b.wake(); return b
def prefs(**kv):
    def f(L):
        open(L+'/../lsd-preferences.properties','w').write(''.join(f'{k}={v}\n' for k,v in kv.items()))
    return f
@case
def c_q_cursor():
    b=srcq(B()); state(b,'q0'); b.shot('q0')
    b.click('browser_row_src_tt_alpha'); b.t+=.3
    for i in range(6): b.key('right',.35); b.shot(f'r{i}')
    b.key('down',.3); b.shot('down'); b.key('q',.4); state(b,'q1'); b.shot('q1'); b.key('shift+q',.4); state(b,'q2'); return b
@chk('c_q_cursor')
def _(r):
    q0=r.q('q0'); q1=r.q('q1'); q2=r.q('q2'); bg=(r.q('q0','BG')[0],r.q('q2','BG')[0])
    r.check('Q with the cursor in a queue leaves the A/B queue alone',q1[0]==q0[0],(q0,q1))
    r.check('Shift+Q there leaves both queues alone',q2[0]==q0[0] and bg[0]==bg[1],(q2,bg))
@case
def c_keys_accept():
    b=srcq(B()); b.st('library_mode',target='HALF'); b.t+=.6; b.go(x=1000,y=650); state(b,'a0')
    b.key('right',.3); b.t+=3; state(b,'a1'); b.key('right',.3); b.t+=3; state(b,'a2'); b.key('left',.3); b.t+=3; state(b,'a3'); return b
SETUP['c_keys_accept']=prefs(queueKeyTrigger='ARROWS')
ENVS['c_keys_accept']={'LSD_TOUR_PERSIST':'1'}
@chk('c_keys_accept')
def _(r):
    s=[(r.q(l)[1],r.f(l,'M','xf'),r.f(l,'A','file'),r.f(l,'B','file')) for l in ('a0','a1','a2','a3')]; print('   (active,xf,A,B):',s)
    r.check('Right arrow accepts: next item loads and the crossfader moves',s[1][0]!=s[0][0] and s[1][1]!=s[0][1],s[:2])
    r.check('each press steps once',[x[0] for x in s][1:]==[0,1,0] or [x[0] for x in s][1:]==[1,2,1],s)
@case
def c_midi_plus3():
    b=srcq(B(),('tt_alpha','tt_beta','tt_gamma','tt_alpha','tt_beta')); b.st('library_mode',target='HALF'); b.t+=.5
    b.st('midi_map',param='Global/queueNext',unit='40'); b.t+=.3; state(b,'m0')
    for v in (127,0,127,0,127,0): b.st('midi',target=f'0:40:{v}',dt=0)
    b.t+=3; state(b,'m1'); return b
@chk('c_midi_plus3')
def _(r):
    a=r.q('m0'); c=r.q('m1'); print('   ',a,c)
    r.check('three MIDI presses in one frame move three items',(c[1] or 0)-(a[1] if a[1] is not None and a[1]>=0 else -1)==3,(a,c))
@case
def c_lsdfx_into_full():
    b=B(); b.st('load_source',deck='A',target='mandala')
    for s_,f in ((0,'bloom'),(1,'pixelate'),(2,'kaleidoscope')): b.st('fx_filter',deck='A',slot=s_,target=f)
    b.t+=1; state(b,'c0'); b.click('fxchain_name_deckA_fx'); b.t+=.6; b.search('tt_fx'); b.t+=.4; keys(b,'k'); b.dbl('browser_row_fx_tt_fx'); b.t+=1.2; state(b,'dbl'); b.shot('dbl')
    return b
@chk('c_lsdfx_into_full')
def _(r):
    print('   ',r.f('c0','A','fx'),'->',r.f('dbl','A','fx'),'toasts',r.to,[x for x in kf(r,'k') if 'row' in x][:5])
@case
def c_lsdfx_fxqueue():
    b=B(); b.st('load_source',deck='A',target='mandala')
    for s_,f in ((0,'bloom'),(1,'pixelate'),(2,'kaleidoscope')): b.st('fx_filter',deck='A',slot=s_,target=f)
    b.t+=1; state(b,'c0'); b=libfull(b,'FX'); b.search('tt_fx'); b.t+=.4; b.drag('browser_row_fx_tt_fx',x=250,y=560); b.t+=.6; state(b,'q')
    b.click('btnid_fxQueueNext'); b.t+=1.5; state(b,'n'); return b
@chk('c_lsdfx_fxqueue')
def _(r): print('   FX_AB',r.q('q','FX_AB'),'A fx',r.f('c0','A','fx'),'->',r.f('n','A','fx'),r.to)
@case
def c_macros_save_load():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.st('macro_bind',deck='deckA',unit='0',param='Deck A/Mandala/L1'); b.t+=.5
    b=libfull(b,'Macros'); b.t+=.4; b.click('btnid_maps_save_bank'); b.t+=.4; b.click('m:Deck A'); b.t+=.6; b.shot('dlg'); keys(b,'dlg'); b.typ('tt_bank'); b.t+=.8; keys(b,'after'); b.shot('after')
    b.st('macro_unbind',deck='deckA',unit='0'); b.t+=.3; b.st('audit_param',param='Deck A/Mandala/L1',deck='deckA',target='p0'); b.t+=.1
    return b
@chk('c_macros_save_load')
def _(r):
    print('   dlg',[x for x in kf(r,'dlg') if x.startswith(('b:','m:'))][:20]); print('   after',[x for x in kf(r,'after') if 'tt_bank' in x or x.startswith('maps') or 'bank' in x.lower()][:20])
    print('   files',glob.glob(r.lib+'/**/tt_bank*',recursive=True))
# §2
@case
def c_pairs_header():
    b=B(); b.tips=True; b.st('load_source',deck='BG',target='kifs'); b.t+=.6; b.click(x=955,y=500); b.t+=.8; state(b,'bg'); keys(b,'bg'); b.shot('bg')
    b.st('close_focus'); b.t+=.5; b.st('focus_pair',target='MASTER'); b.t+=.8; state(b,'m'); keys(b,'m'); b.shot('m')
    b.st('close_focus'); b.t+=.5; b.st('open_params',target='master'); b.t+=1; b.click('edit_side_MST'); b.t+=.7; keys(b,'mst'); b.shot('mst'); hover(b,'btnid_bay_browse',1.6)
    return b
@chk('c_pairs_header')
def _(r):
    print('   bg ui',r.st['bg'].get('ui'),' master ui',r.st['m'].get('ui'))
    for l in ('bg','m','mst'): print('  ',l,[x for x in kf(r,l) if x.startswith(('btnid_bay','b:Back','b:Browse','b:Params'))])
    print('   tips',[t[:140] for t in tips(r,'browse')][-3:])
    r.check('BG monitor on the A/B page opens the BG pair',r.ui('bg','pair')=='BG',r.ui('bg','pair'))
@case
def c_badge_tips():
    b=B(); b.tips=True; b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8
    hover(b,'perf_gen_badge_A',1.8); hover(b,'fxchain_name_deckA_fx',1.8); hover(b,'fxcell_deckA_fx_1',1.8); hover(b,'perf_trans_picker',1.8); return b
@chk('c_badge_tips')
def _(r):
    for t in tips(r): print('    TIP',t[:260])
@case
def c_scope_memory():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8
    b.click('fxcell_deckA_fx_0'); b.t+=.6; b.click('s:Saved chains'); b.t+=.3; b.search('blo'); b.t+=.4; b.shot('fx1')
    b.click('perf_gen_badge_A'); b.t+=.6; b.click('s:Stock sources'); b.t+=.3; b.search('ma'); b.t+=.4; b.shot('src1')
    b.click('fxcell_deckA_fx_0'); b.t+=.6; b.shot('fx2'); b.click('perf_gen_badge_A'); b.t+=.6; b.shot('src2'); return b
@case
def c_knob_no_pull():
    b=Tn(); b.st('library_mode',target='FULL'); b.t+=.3; b.click('btn_Queues'); b.t+=.5; state(b,'a'); keys(b,'a')
    b.turn(1,4); b.t+=.4; keys(b,'b'); state(b,'b'); b.shot('b'); b.click('btn_Macros'); b.t+=.4; b.turn(2,4); b.t+=.4; keys(b,'c'); return b
c_knob_no_pull.__name__='tw_knob_no_pull'; CASES['tw_knob_no_pull']=CASES.pop('c_knob_no_pull')
@chk('tw_knob_no_pull')
def _(r):
    tab=lambda l: 'Queues' if 'btnid_queuePrev' in kf(r,l) and 'browser_search' not in kf(r,l) else ('?' )
    print('   ',[ (l,[x for x in kf(r,l) if x.startswith('btnid_maps') or x=='btnid_queuePrev'][:3]) for l in 'abc'])
    r.check('knob turn leaves the Queues tab up','btnid_queuePrev' in kf(r,'b') and 'browser_search' not in kf(r,'b'))
    r.check('knob turn leaves the Macros tab up',any(x.startswith('btnid_maps') for x in kf(r,'c')))
@case
def c_edit_exit_keep():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.st('fx_chain',deck='A',target=CH); b.t+=.8
    b.st('open_params',target='deckA'); b.t+=1; b.click('cv_Deck A/Mandala/L2_lfo'); b.t+=.6; b.shot('e0'); keys(b,'e0')
    b.go(x=600,y=700); b.key('escape',.8); b.shot('out1'); b.click('btnid_perf_src_more_A' if False else 'perf_gen_badge_A'); b.t+=.3; b.key('escape',.3)
    b.st('open_params',target='deckA'); b.t+=1; b.shot('e1'); keys(b,'e1'); return b
@chk('c_edit_exit_keep')
def _(r): print('   compare e0/e1 shots')
@case
def c_lsdfx_dbl_lib():
    b=B(); b.st('load_source',deck='A',target='mandala')
    for s_,f in ((0,'bloom'),(1,'pixelate'),(2,'kaleidoscope')): b.st('fx_filter',deck='A',slot=s_,target=f)
    b.t+=1; state(b,'c0'); b=libfull(b,'FX'); b.search('tt_fx'); b.t+=.4; b.dbl('browser_row_fx_tt_fx'); b.t+=1.2; state(b,'dbl')
    b.st('library_mode',target='HALF'); b.t+=.5; b.click('fxcell_deckA_fx_1'); b.t+=.6; b.search('tt_fx'); b.t+=.4; keys(b,'k'); b.dbl('browser_row_fx_tt_fx'); b.t+=1.2; state(b,'slot'); return b
@chk('c_lsdfx_dbl_lib')
def _(r): print('   ',r.f('c0','A','fx'),'-> lib dbl',r.f('dbl','A','fx'),'-> slot-bound dbl',r.f('slot','A','fx'),'toasts',r.to)
@case
def c_lsdfx_dbl_lib_empty():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.st('fx_filter',deck='A',slot=0,target='bloom')
    b.t+=1; state(b,'c0'); b=libfull(b,'FX'); b.search('tt_fx'); b.t+=.4; b.dbl('browser_row_fx_tt_fx'); b.t+=1.2; state(b,'dbl'); b.search('pixel'); b.t+=.4; b.dbl('browser_row_fx_pixelate'); b.t+=1.2; state(b,'dbl2'); return b
@chk('c_lsdfx_dbl_lib_empty')
def _(r): print('   ',r.f('c0','A','fx'),'-> lib dbl',r.f('dbl','A','fx'),r.f('dbl','M','fx'),' stock dbl',r.f('dbl2','A','fx'),r.f('dbl2','M','fx'),'toasts',r.to, r.ui('dbl','dock'))
@case
def c_q_cursor_down():
    b=srcq(B()); state(b,'q0')
    b.click('browser_row_src_tt_inside'); b.t+=.3
    for i in range(5): b.key('down',.35); b.shot(f'd{i}')
    b.key('q',.4); state(b,'q1'); b.shot('q1'); b.key('shift+q',.4); state(b,'q2'); b.key('enter',.8); state(b,'ent'); return b
@chk('c_q_cursor_down')
def _(r):
    q0=r.q('q0'); q1=r.q('q1'); q2=r.q('q2'); print('   ',q0,q1,q2,'BG',r.q('q2','BG'),'enter A/B',r.q('ent'))
@case
def tw_pane_steps():
    b=Tn(); b=srcq(b); state(b,'q0'); b.click('browser_row_src_tt_alpha'); b.t+=.3; b.shot('p0')
    for i in range(7): b.rb(); b.t+=.3; b.shot(f'p{i+1}'); b.state(f's{i+1}',.05)
    b.go(x=1000,y=650); b.key('q',.4); state(b,'q1'); b.key('shift+q',.4); state(b,'q2'); return b
@chk('tw_pane_steps')
def _(r):
    print('   ',r.q('q0'),r.q('q1'),r.q('q2'),r.q('q2','BG'))
    r.check('Q with the controller cursor in a queue leaves it alone',r.q('q1')[0]==r.q('q0')[0] and r.q('q2')[0]==r.q('q0')[0])
@case
def d_mixer2():
    b=B(); b.st('randomization',value=1); b.st('set_page',target='mixer'); b.t+=.8; keys(b,'mixer'); b.shot('mixer')
    b.st('open_params',target='master'); b.t+=1; b.click('edit_side_MST'); b.t+=.6; keys(b,'mst'); b.shot('mst'); b.click('btn_CTRL'); b.t+=.6; keys(b,'ctrl'); b.shot('ctrl'); return b
@chk('d_mixer2')
def _(r):
    for l in ('mixer','ctrl'):
        print('  ==',l,' '.join(x for x in kf(r,l) if not x.startswith(('cv_','browser_row','b:','btn_'))))
@case
def tw_midi_plus3():
    b=Tn(); b=srcq(b,('tt_alpha','tt_beta','tt_gamma','tt_alpha','tt_beta','tt_gamma','tt_alpha','tt_beta')); b.st('library_mode',target='HALF'); b.t+=.5
    b.st('midi_map',param='mixer.queue_next',unit='100'); b.st('midi_map',param='Global/queueNext',unit='101'); b.t+=.3; state(b,'m0')
    for v in (127,0,127,0,127,0): b.st('midi',target=f'0:100:{v}',dt=0)
    b.t+=3; state(b,'m1')
    for v in (127,0,127,0,127,0): b.st('midi',target=f'0:101:{v}',dt=0)
    b.t+=3; state(b,'m2')
    b.st('midi',target='0:101:127',dt=0); b.t+=.1; b.st('midi',target='0:101:0',dt=0); b.t+=3; state(b,'m3')
    return b
@chk('tw_midi_plus3')
def _(r): print('   cursor/active:',[(r.q(l)[1],r.q(l)[2]) for l in ('m0','m1','m2','m3')])
@case
def tw_fxq_midi():
    b=Tn(); b=libfull(b,'FX'); b.search('tt_chain'); b.t+=.4; b.drag('browser_row_fx_tt_chain',x=250,y=560); b.search('crt'); b.t+=.4; b.drag('browser_row_fx_crt',x=250,y=600); b.t+=.5
    b.st('library_mode',target='HALF'); b.t+=.4; b.st('midi_map',param='Mixer/fxQueueNext',unit='103'); b.t+=.3; state(b,'f0')
    b.st('midi',target='0:103:127',dt=0); b.t+=.1; b.st('midi',target='0:103:0',dt=0); b.t+=2; state(b,'f1')
    b.st('midi',target='0:103:127',dt=0); b.t+=.1; b.st('midi',target='0:103:0',dt=0); b.t+=2; state(b,'f2')
    b.st('set_page',target='mixer'); b.t+=.6; b.rclick('clock_trig_fxQueueNext'); b.t+=.4; keys(b,'rowmenu'); b.key('escape',.3)
    b.st('open_params',target='master'); b.t+=1; b.click('edit_side_MST'); b.t+=.6; b.click('btn_CTRL'); b.t+=.6; b.rclick(x=100,y=514); b.t+=.4; keys(b,'ctrlmenu'); b.shot('ctrlmenu'); return b
@chk('tw_fxq_midi')
def _(r):
    print('   FX_AB',r.q('f0','FX_AB'),r.q('f2','FX_AB'),' chainA',[r.f(l,'A_chain','name') for l in ('f0','f1','f2')])
    for l in ('rowmenu','ctrlmenu'): print('   ',l,[x for x in kf(r,l) if x.startswith('m:') and x not in ('m:File','m:Help','m:Output','m:View')])
    r.check('a MIDI CC mapped to A/B FX queue next steps the FX queue',r.f('f1','A_chain','name')!=r.f('f0','A_chain','name'),[r.f(l,'A_chain','name') for l in ('f0','f1','f2')])
@case
def tw_fxq_midi2():
    b=Tn(); b=libfull(b,'FX'); b.search('tt_chain'); b.t+=.4; b.drag('browser_row_fx_tt_chain',x=250,y=560); b.search('crt'); b.t+=.4; b.drag('browser_row_fx_crt',x=250,y=600); b.t+=.5
    b.st('library_mode',target='HALF'); b.t+=.4; b.st('set_page',target='mixer'); b.t+=.5; state(b,'f0')
    b.click('clock_trig_fxQueueNext'); b.t+=2; state(b,'f1')
    b.st('midi_map',param='Mixer/fxQueueNext',unit='103'); b.st('midi_map',param='Mixer/queueNext',unit='104'); b.t+=.3
    b.st('midi',target='0:103:127',dt=0); b.t+=.3; b.st('midi',target='0:103:0',dt=0); b.t+=2; state(b,'f2')
    b.st('audit_param',param='Mixer/fxQueueNext',target='p'); b.t+=.1; return b
@chk('tw_fxq_midi2')
def _(r):
    print('   chainA',[r.f(l,'A_chain','name') for l in ('f0','f1','f2')],'FXQ',[r.q(l,'FX_AB')[1:] for l in ('f0','f1','f2')],r.param)
    print([l for l in open(f'{r.lib}/../../out/c_{r.n}.log') if 'MIDI' in l or 'apping' in l][:10])
@case
def tw_fxq_learn():
    b=Tn(); b=libfull(b,'FX'); b.search('tt_chain'); b.t+=.4; b.drag('browser_row_fx_tt_chain',x=250,y=560); b.search('crt'); b.t+=.4; b.drag('browser_row_fx_crt',x=250,y=600); b.t+=.5
    b.st('library_mode',target='HALF'); b.t+=.4; b.st('set_page',target='mixer'); b.t+=.5; state(b,'f0')
    b.rclick('clock_trig_fxQueueNext'); b.t+=.4; b.click('m:MIDI Learn (A/B FX Queue Next)'); b.t+=.5
    b.st('midi',target='5:103:127',param='nanoKONTROL2',dt=0); b.t+=.2; b.st('midi',target='5:103:0',param='nanoKONTROL2',dt=0); b.t+=1.5; state(b,'f1')
    b.st('midi',target='5:103:127',param='nanoKONTROL2',dt=0); b.t+=.2; b.st('midi',target='5:103:0',param='nanoKONTROL2',dt=0); b.t+=2; state(b,'f2')
    b.st('midi',target='5:103:127',param='nanoKONTROL2',dt=0); b.t+=.2; b.st('midi',target='5:103:0',param='nanoKONTROL2',dt=0); b.t+=2; state(b,'f3')
    b.st('open_params',target='master'); b.t+=1; b.click('edit_side_MST'); b.t+=.6; b.click('btn_CTRL'); b.t+=.6
    b.rclick(x=100,y=281); b.t+=.4; keys(b,'m1'); b.shot('m1'); b.key('escape',.3); b.rclick(x=250,y=281); b.t+=.4; keys(b,'m2'); b.shot('m2'); return b
@chk('tw_fxq_learn')
def _(r):
    print('   chainA',[r.f(l,'A_chain','name') for l in ('f0','f1','f2','f3')],'FXQ',[r.q(l,'FX_AB')[1:] for l in ('f0','f1','f2','f3')],r.to[-4:])
    for l in ('m1','m2'): print('   ',l,[x for x in kf(r,l) if x.startswith('m:') and x not in ('m:File','m:Help','m:Output','m:View')])
    r.check('MIDI Learn on the Clock FX-queue button, then the CC steps the FX queue',r.f('f2','A_chain','name')!=r.f('f1','A_chain','name') or r.f('f1','A_chain','name')!=r.f('f0','A_chain','name'))
@case
def tw_learn_three():
    b=Tn(); b.st('load_source',deck='A',target='mandala'); b.t+=.6; b.st('open_params',target='deckA'); b.t+=1
    b.rclick('cv_Deck A/Mandala/L2_label'); b.t+=.4; keys(b,'lm'); b.click('m:OSC Learn'); b.t+=.4
    b.rclick('cv_Deck A/Mandala/L3_val'); b.t+=.4; keys(b,'vm'); b.shot('vm'); b.key('escape',.3)
    b.st('audit_dump',target='armed'); b.t+=.1; b.go(x=600,y=650); b.key('escape',.5); b.st('audit_dump',target='after'); b.t+=.1; return b
@chk('tw_learn_three')
def _(r):
    for l in ('lm','vm'): print('   ',l,[x for x in kf(r,l) if x.startswith('m:') and x not in ('m:File','m:Help','m:Output','m:View')])
    print('   ',r.dump.get('armed','')[-40:],r.dump.get('after','')[-40:])
    r.check('Esc cancels OSC Learn','osc=true' in r.dump.get('armed','') and 'osc=false' in r.dump.get('after',''))
@case
def d_geom():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8; keys(b,'g0')
    b.click('btnid_slot_focus_deckA_fx_0'); b.t+=.6; keys(b,'g1'); b.shot('g1')
    b.click('btnid_slot_focus_deckA_fx_0'); b.t+=.6; b.click('fxcell_deckA_fx_1'); b.t+=.6; keys(b,'g2'); b.shot('g2')
    b.key('escape',.4); b.st('fx_clear',deck='A'); b.t+=.6; keys(b,'g3'); b.shot('g3'); return b
@chk('d_geom')
def _(r):
    ks=['perf_gen_badge_A','perf_gen_badge_B','fxchain_name_deckA_fx','fxchain_name_deckB_fx','perf_0_r0_c0','perf_0_r1_c0','perf_0_r2_c0','perf_0_r3_c0']
    R=[rects(r,l) for l in ('g0','g1','g2','g3')]
    for k in ks: print('   ',k,[x.get(k) for x in R])
    ok=all(len({x.get(k) for x in R if x.get(k)})<=1 for k in ks)
    r.check('row geometry is the same in normal, slot focus, bound and empty-chain states',ok)
@case
def d_trans_tips():
    b=B(); b.tips=True; b.st('set_page',target='mixer'); b.t+=.4
    b=libfull(b,'Transitions'); b.search('a'); b.t+=.4
    for n in ('film_burn','luminous_flash','vortex_swirl'): b.search(n[:4]); b.t+=.3; b.click('browser_row_trans_'+n); b.key('q',.3)
    b.st('library_mode',target='HALF'); b.t+=.5; hover(b,'btnid_perf_trans_q_prev',1.8); hover(b,'btnid_perf_trans_q_next',1.8)
    b.click('btnid_perf_trans_q_next'); b.t+=.8; hover(b,'btnid_perf_trans_q_prev',1.8); hover(b,'btnid_perf_trans_q_next',1.8); state(b,'q'); return b
@chk('d_trans_tips')
def _(r):
    print('   TRANS q',r.q('q','TRANS'))
    for t in tips(r,'transition'): print('    TIP',t[:220])
@case
def d_clock_play():
    b=B(); b.tips=True; b=srcq(b); b.st('library_mode',target='HALF'); b.t+=.4; b.st('set_page',target='mixer'); b.t+=.5; state(b,'p0')
    hover(b,'btnid_perf_clock_auto_ab',1.8); b.click('btnid_perf_clock_auto_ab'); b.t+=.4; b.shot('on'); state(b,'p1'); b.t+=20; state(b,'p2'); b.click('btnid_perf_clock_auto_ab'); b.t+=.4; b.shot('off'); state(b,'p3'); return b
@chk('d_clock_play')
def _(r):
    print('   A/B q',[r.q(l) for l in ('p0','p1','p2','p3')],[r.f(l,'M','xf') for l in ('p0','p1','p2','p3')])
    for t in tips(r,'auto'): print('    TIP',t[:220])
@case
def d_chain_master():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8
    b.click('fxchain_more_deckA_fx'); b.t+=.4; b.click('m:Chain View'); b.t+=.8; keys(b,'cv'); b.shot('cv'); state(b,'cv')
    b.go('perf_0_r1_c1'); b.st('drag',x=None or 0,y=0,durationSec=.01) if False else None
    b.drag('perf_0_r1_c1',x=None,y=None) if False else None
    return b
def slotpage(L):
    os.makedirs(L+'/perform_pages',exist_ok=True)
    json.dump({"version":1,"id":"slots","name":"SLOTS","rows":[{"row":"deck.A.fx1"},{"row":"deck.A.fx2"},{"row":"deck.A.fx3"},{"row":"deck.A.src"}]},open(L+'/perform_pages/slots.json','w'),indent=2)
@case
def e_slot_rows():
    b=B(); b.tips=True; b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8; keys(b,'ab')
    b.st('set_page',target='slots'); b.t+=.8; keys(b,'s0'); b.shot('s0'); state(b,'s0')
    return b
SETUP['e_slot_rows']=slotpage
@chk('e_slot_rows')
def _(r):
    print('  ', ' '.join(x for x in kf(r,'s0') if not x.startswith(('cv_','browser_row','s:','m:'))))
    print('   page',r.page('s0'))
@case
def e_chain_view():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8
    b.click('fxchain_more_deckA_fx'); b.t+=.4; b.click('m:Chain View'); b.t+=.8; state(b,'cv0')
    b.go('perf_0_r1_c1'); b.t+=.2; b.st('drag',x=0,y=0,durationSec=.4) if False else None
    rr=None
    b.st('move',target='perf_0_r1_c1',durationSec=.1,dt=.05); b.t+=.2
    b.st('drag',target='perf_0_r1_c2',durationSec=.4); b.t+=.8; state(b,'cv1')
    b.st('move',target='perf_0_r2_c1',durationSec=.1,dt=.05); b.t+=.2; b.st('drag',target='perf_0_r2_c2',durationSec=.4); b.t+=.8; state(b,'cv2'); b.shot('cv2')
    b.key('escape',.6); b.st('set_page',target='mixer'); b.t+=.6; b.click('fxchain_more_masterFx'); b.t+=.4; keys(b,'mm'); b.click('m:Chain View'); b.t+=.8; keys(b,'mcv'); b.shot('mcv'); state(b,'mcv'); return b
@chk('e_chain_view')
def _(r):
    print('   dock',[r.ui(l,'dock') for l in ('cv0','cv1','cv2')],'chain',[r.ui(l,'chain') for l in ('cv0','cv1','cv2','mcv')],'pair',r.ui('mcv','pair'))
    print('   mm',[x for x in kf(r,'mm') if x.startswith('m:')])
@case
def e_slot_rows2():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8
    b.st('set_page',target='slots'); b.t+=.8; b.click('btn_FX'); b.t+=.4; b.search('pixel'); b.t+=.4
    b.drag('browser_row_fx_pixelate',x=30,y=140); b.t+=1; state(b,'d'); b.shot('d')
    b.click('fxpage_next_deckA_fx_s1'); b.t+=.5; b.shot('pg'); return b
SETUP['e_slot_rows2']=slotpage
@chk('e_slot_rows2')
def _(r):
    print('   A fx',r.f('d','A','fx'))
    r.check('effect dropped on the FX2 badge lands in slot 2',r.f('d','A','fx').split(',')[1].strip().lower().startswith('pixel'),r.f('d','A','fx'))
@case
def tw_chain_retarget():
    b=Tn(); b.st('fx_chain',deck='A',target=CH); b.t+=.6
    b.click('fxchain_more_deckA_fx'); b.t+=.4; b.click('m:Chain View'); b.t+=.8; state(b,'c0')
    b.turn(5,3); b.t+=.5; state(b,'c1'); b.turn(9,3); b.t+=.5; state(b,'c2'); b.shot('c2'); return b
@chk('tw_chain_retarget')
def _(r):
    d=[r.ui(l,'dock') for l in ('c0','c1','c2')]; print('   dock',d,[r.ui(l,'chain') for l in ('c0','c1','c2')])
    r.check('touching an FX2 knob in Chain view retargets the list to slot 2','slotIndex=1' in (d[1] or ''),d)
    r.check('touching an FX3 knob retargets to slot 3','slotIndex=2' in (d[2] or ''),d)
@case
def e_osc_esc():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.t+=.6; b.st('open_params',target='deckA'); b.t+=1
    b.rclick('cv_Deck A/Mandala/L2_label'); b.t+=.4; b.click('m:OSC Learn'); b.t+=.5; b.st('audit_dump',target='armed'); b.t+=.1; b.shot('armed')
    b.go(x=600,y=650); b.key('escape',.5); b.st('audit_dump',target='after'); b.t+=.1; return b
@chk('e_osc_esc')
def _(r):
    print('   ',r.dump.get('armed','')[-30:],r.dump.get('after','')[-30:])
    r.check('Esc cancels OSC Learn','osc=true' in r.dump.get('armed','') and 'osc=false' in r.dump.get('after',''))
@case
def e_slot_rows3():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8
    b.st('set_page',target='slots'); b.t+=.8; b.click('btn_FX'); b.t+=.4; b.search('tt_fx'); b.t+=.4
    b.go('browser_row_fx_tt_fx'); b.t+=.15; b.st('drag',x=30,y=128,durationSec=1.0); b.shots.append({"label":"m1","t":round(b.t+.95,3)}); b.t+=1.6; state(b,'d1'); b.shot('d1')
    b.search('pixel'); b.t+=.4; b.go('browser_row_fx_pixelate'); b.t+=.15; b.st('drag',x=30,y=200,durationSec=1.0); b.shots.append({"label":"m2","t":round(b.t+.95,3)}); b.t+=1.6; state(b,'d2'); return b
SETUP['e_slot_rows3']=slotpage
@chk('e_slot_rows3')
def _(r): print('   A fx',r.f('d1','A','fx'),r.f('d2','A','fx'),r.to)
# ================= batch F (§4, §6, §7)
CUR='library/presets/garden/curated/curated-0001.lsd'
@case
def f_flame_clean():
    b=B(); b.tips=True; b.st('load_preset',deck='A',target=CUR); b.t+=2; state(b,'a'); keys(b,'a'); b.shot('a'); hover(b,'perf_gen_badge_A',1.8)
    b=libfull(b); b.search('curated-0001'); b.t+=.5; hover(b,rowS('curated-0001'),1.8); b.shot('row')
    b.search('tt_beta'); b.t+=.4; b.dbl(rowS('tt_beta')); b.t+=1.5; keys(b,'b'); state(b,'b'); b.shot('b'); return b
@chk('f_flame_clean')
def _(r):
    print('   A',r.st['a'].get('A'),'->',r.st['b'].get('A'))
    r.check('curated flame loads clean (not dirty)','dirty=false' in r.st['a'].get('A',''),r.st['a'].get('A'))
    r.check('loading another preset over it: no prompt, it loads','tt_beta' in r.st['b'].get('A','') and 'b:Save' not in kf(r,'b'),r.st['b'].get('A'))
    for t in tips(r): print('    TIP',t[:200])
@case
def f_saveas_presets():
    b=B(); b.st('load_preset',deck='A',target=CUR); b.t+=2; b.click('perf_src_more_A'); b.t+=.4; keys(b,'m'); b.click('m:Save As...'); b.t+=.6; b.shot('dlg'); keys(b,'dlg'); b.typ('tt_flame_copy'); b.t+=1.2; return b
@chk('f_saveas_presets')
def _(r):
    print('   menu',[x for x in kf(r,'m') if x.startswith('m:')])
    f=glob.glob(r.lib+'/presets/**/tt_flame_copy.lsd',recursive=True); print('   ',f)
    r.check('Save As with a new name goes to presets/ (top level)',r.lib+'/presets/tt_flame_copy.lsd' in f,f)
def zoomcase(z):
    def c():
        b=B(); b.st('load_source',deck='A',target='isometric_voxels'); b.st('load_source',deck='B',target='wave_ripples'); b.t+=1
        if z: b.st('macro_bind',deck='deckA',unit='0',param='Deck A/View/Zoom'); b.st('macro_bind',deck='deckA',unit='1',param='Deck B/View/Zoom'); b.st('macro_set',deck='deckA',unit='0',value=.9); b.st('macro_set',deck='deckA',unit='1',value=.9)
        b.t+=1; b.shot('z',.1); return b
    return c
CASES['f_zoom0']=zoomcase(False); CASES['f_zoom1']=zoomcase(True)
@case
def f_zoom_tips():
    b=B(); b.tips=True; b.st('load_source',deck='A',target='reaction_diffusion'); b.t+=.8; b.st('open_params',target='deckA'); b.t+=1; keys(b,'k')
    hover(b,'cv_Deck A/View/Zoom_label',1.8); hover(b,'cv_Deck A/View/RotateZ_label',1.8); b.shot('rd')
    b.st('load_source',deck='A',target='kifs'); b.t+=1; keys(b,'kifs'); return b
@chk('f_zoom_tips')
def _(r):
    for t in tips(r,'zoom')+tips(r,'rotat'): print('    TIP',t[:240])
    print('   rd zoom rows',[x for x in kf(r,'k') if 'Zoom' in x and x.endswith('_label')],' kifs',[x for x in kf(r,'kifs') if ('Zoom' in x or 'Rotate' in x) and x.endswith('_label')])
@case
def tw_gain_midi():
    b=Tn('mandala','kifs'); b.st('midi_map',param='Deck A/Gain',unit='105'); b.t+=.3
    for s_ in ('kifs','voronoi_cells','apollonian'): b.st('load_source',deck='A',target=s_); b.t+=.8
    b.st('midi',target='0:105:32',dt=0); b.t+=.5; b.st('audit_param',param='Deck A/Gain',target='g'); b.t+=.1
    b.st('open_params',target='master'); b.t+=1; b.click('edit_side_MST'); b.t+=.6; b.click('btn_LEVELS'); b.t+=.5; keys(b,'lv'); b.shot('lv'); return b
@chk('tw_gain_midi')
def _(r):
    g=float(r.param['g']['base']); print('   gain',g)
    r.check('MIDI-learned Deck A Gain still drives Gain after three source changes',abs(g-32/127*(float(r.param['g']['max']) if float(r.param['g']['max'])>0 else 1))<.05 or abs(g-0.25)<.08,r.param['g'])
    print('   LEVELS rows',[x for x in kf(r,'lv') if x.startswith('cv_') and x.endswith('_label')])
@case
def f_queue_tips():
    b=B(); b.tips=True; b=srcq(b); b.st('library_mode',target='HALF'); b.t+=.5
    hover(b,'btnid_perf_q_prev_A',1.8); hover(b,'btnid_perf_q_next_A',1.8); hover(b,'btnid_perf_q_next_B',1.8)
    b.st('set_page',target='mixer'); b.t+=.5; hover(b,'btnid_perf_clock_trig_fxQueueNext',1.8); hover(b,'btnid_perf_clock_trig_fxQueuePrev',1.8); return b
@chk('f_queue_tips')
def _(r):
    for t in tips(r,'queue'): print('    TIP',t[:260])
def noTargets(L):
    d=json.load(open(L+'/fx_chains/tt_chain.lsdfxchain')); d['slots'][2]['metaBindings']=[]; d['name']='tt_notarget'; json.dump(d,open(L+'/fx_chains/tt_notarget.lsdfxchain','w'),indent=2)
@case
def f_no_targets():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target='library/fx_chains/tt_notarget.lsdfxchain'); b.t+=1
    b.st('audit_dump',target='d0'); b.t+=.1; b.st('open_params',target='deckA_fx'); b.t+=1; b.click('btnid_rack_chevron_0_1') if False else None; b.shot('edit'); keys(b,'edit')
    b.st('fx_param',deck='A',slot=2,param='threshold',value=.3); b.t+=.3; b.click('fxchain_save_deckA_fx'); b.t+=1
    b.st('fx_clear',deck='A'); b.t+=.4; b.st('fx_chain',deck='A',target='library/fx_chains/tt_notarget.lsdfxchain'); b.t+=1; b.st('audit_dump',target='d1'); b.t+=.1; return b
SETUP['f_no_targets']=noTargets
@chk('f_no_targets')
def _(r):
    g=lambda k: re.search(r'A\.fx2=[^|]*',r.dump.get(k,'')); print('   ',g('d0') and g('d0')[0][:160]); print('   ',g('d1') and g('d1')[0][:160])
    j=r.js('fx_chains/tt_notarget.lsdfxchain'); print('   saved metaBindings',j['slots'][2].get('metaBindings'))
    r.check('a slot with no Metaknob targets loads with none',g('d0') and 'targets=[]' in g('d0')[0])
    r.check('still none after Save and reload',g('d1') and 'targets=[]' in g('d1')[0] and j['slots'][2].get('metaBindings')==[])
# ================= batch G
@case
def g_fewer_params():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_filter',deck='A',slot=0,target='3d_elevation'); b.t+=.8
    b.click('btnid_slot_focus_deckA_fx_0'); b.t+=.6; keys(b,'f0'); b.click('fxpage_next_deckA_fx'); b.t+=.4; b.click('fxpage_next_deckA_fx'); b.t+=.4; b.shot('p3')
    b.st('fx_filter',deck='A',slot=0,target='invert'); b.t+=.8; b.shot('after'); keys(b,'after'); return b
@chk('g_fewer_params')
def _(r): print('   focus keys',[x for x in kf(r,'f0') if 'page' in x or 'focus' in x][:10]); print('   after',[x for x in kf(r,'after') if 'page' in x or 'focus' in x][:10])
def drywet(L):
    d=json.load(open(L+'/fx_chains/tt_chain.lsdfxchain')); d['slots'][0]['metaBindings']=[{"targetParamName":None}]; d['name']='tt_dw'; json.dump(d,open(L+'/fx_chains/tt_dw.lsdfxchain','w'),indent=2); slotpage(L)
@case
def g_drywet_owned():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target='library/fx_chains/tt_dw.lsdfxchain'); b.t+=1; b.st('set_page',target='slots'); b.t+=.8
    b.st('audit_dump',target='d0'); b.t+=.1; b.shot('s0'); r_=None
    b.go('fxwet_deckA_fx_s1'); b.t+=.1; b.st('drag',x=120,y=81,durationSec=.4); b.t+=.8; b.st('audit_dump',target='d1'); b.t+=.1; b.shot('s1'); state(b,'s1'); return b
SETUP['g_drywet_owned']=drywet
@chk('g_drywet_owned')
def _(r):
    g=lambda k: re.search(r'A\.fx0=[^|]*',r.dump.get(k,'')); print('   ',g('d0') and g('d0')[0][:140]); print('   ',g('d1') and g('d1')[0][:140]); print('   wet',r.f('s1','A_chain','wet'))
    m0=float(re.search(r'meta=([\d.]+)',g('d0')[0])[1]); m1=float(re.search(r'meta=([\d.]+)',g('d1')[0])[1])
    r.check('dragging the Wet slider of a Dry/Wet-owned slot moves its Metaknob',abs(m1-m0)>.05,(m0,m1))
def trans_rest(tid):
    def c():
        b=B(); b.st('load_source',deck='A',target='voronoi_cells'); b.st('load_source',deck='B',target='kifs'); b.st('fx_clear',deck='A'); b.st('fx_clear',deck='B'); b.st('fx_clear',deck='MASTER')
        b.st('transition',target=tid); b.st('crossfade',value=-1); b.t+=1.5; b.shot('a',.05); b.st('crossfade',value=1); b.t+=1.5; b.shot('b',.05); return b
    return c
for _t in ('linear_crossfade','film_burn','luminous_flash','cyber_datamosh','kinetic_zoom','liquid_displacement','noise_dissolve','vortex_swirl'): CASES['g_rest_'+_t]=trans_rest(_t)
@case
def g_discrete():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.t+=.8; b.st('set_param',deck='A',param='colorMode',value=0); b.t+=.3
    b.go('perf_0_r0_c0'); b.t+=.1; b.st('drag',x=455,y=10,durationSec=1.5)
    for i in range(6): b.t+=.25; b.st('audit_list',deck='A',target=f'm{i}')
    b.t+=1; b.shot('end'); return b
@chk('g_discrete')
def _(r):
    vals=[]
    for l in open(f'{r.lib}/../../out/c_{r.n}.log',errors='replace'):
        if 'TOUR_AUDIT list' in l and '\tsteps=' in l:
            p=l.rstrip().split('\t')
            if not p[1].split('/')[-1].startswith('m'): continue
            d=dict(x.split('=',1) for x in p[3:] if '=' in x)
            if d.get('steps') not in (None,'0','null') and p[2] in ('fractalType','type','Fractal Type'): vals.append((p[2],d['base']))
    print('   discrete params seen while dragging knob 1:',vals[:12])
    r.check('knob 1 (Fractal Type) lands on whole steps while dragging',vals and all(float(v).is_integer() for _,v in vals),vals[:12])
@case
def g_level_fader():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.t+=.6; b.st('macro_bind',deck='master',unit='0',param='Mixer/levelA'); b.st('macro_set',deck='master',unit='0',value=1); b.t+=.5
    b.st('audit_param',param='Mixer/levelA',deck='master',target='p0'); b.t+=.1
    b.go('deck_fader_A'); b.t+=.1; b.st('drag',x=909,y=350,durationSec=.5); b.t+=1.2; b.st('audit_param',param='Mixer/levelA',deck='master',target='p1'); b.t+=.1; b.shot('f'); return b
@chk('g_level_fader')
def _(r):
    p0,p1=r.param['p0'],r.param['p1']; print('   level',p0['base'],'->',p1['base'],' knob',p0['knobs'],'->',p1['knobs'])
    r.check('fader drag with a bound knob does not snap back',float(p1['base'])<.9,p1['base'])
    r.check('the bound knob follows',p1['knobs'].split(',')[0]!=p0['knobs'].split(',')[0],(p0['knobs'],p1['knobs']))
@case
def g_edit_chain_menu():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target='library/fx_chains/web/'+sorted(os.listdir('/home/gj/projects/liquid-lsd/tests/web/.cache/app/w/libbase/fx_chains/web'))[0]); b.t+=.8
    b.st('open_params',target='deckA_fx'); b.t+=1; b.click('btnid_more_btn_deckA_fx'); b.t+=.4; keys(b,'m'); b.shot('m'); b.click('m:Save As...'); b.t+=.6; b.shot('dlg'); keys(b,'dlg'); return b
@chk('g_edit_chain_menu')
def _(r): print('   Edit chain menu',[x for x in kf(r,'m') if x.startswith('m:')])
@case
def g_gear_fx():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8; keys(b,'p')
    b.click(x=387,y=132); b.t+=1; b.shot('gear'); keys(b,'gear'); b.click('edit_side_B'); b.t+=.6; b.shot('B'); b.click('edit_side_A'); b.t+=.6; b.shot('A'); return b
# ================= batch H (audio)
def BA(): b=B(); b.audio='m60.wav'; return b
@case
def h_audio_disc():
    b=BA(); b.st('load_source',deck='A',target='mandala'); b.t+=.8; b.st('open_params',target='deckA'); b.t+=1; keys(b,'e'); b.shot('e')
    b.click('cv_Deck A/Mandala/L2_audio'); b.t+=.6; b.click('btn_Audio'); b.t+=.6; keys(b,'aud'); b.shot('aud'); return b
@chk('h_audio_disc')
def _(r):
    print('  ',' '.join(x for x in kf(r,'aud') if not x.startswith(('cv_Deck A/Mandala','browser_row','s:','m:','perf_'))))
    print('  ',[x for x in kf(r,'e') if 'L2' in x])
P2='Deck A/Mandala/L2'
def mods(b,l): b.st('audit_mods',param=P2,target=l); b.t+=.05; return b
@case
def h_audio_slots():
    b=BA(); b.st('load_source',deck='A',target='mandala'); b.t+=.8; b.st('open_params',target='deckA'); b.t+=1
    b.click('cv_Deck A/Mandala/L2_audio'); b.t+=.6; b.click('btn_Audio'); b.t+=.6; mods(b,'m0'); keys(b,'k0')
    b.click('mod_power_1'); b.t+=.5; mods(b,'m1'); keys(b,'k1'); b.shot('s1')
    b.click('mod_power_0'); b.t+=.5; mods(b,'m2'); b.click('mod_power_0'); b.t+=.5; mods(b,'m3')
    b.click('btnid_invert_virtual_audio_2') ; b.t+=.4; mods(b,'m4'); b.shot('s4')
    b.click(x=848,y=365); b.t+=.6; mods(b,'m5'); keys(b,'k5'); b.shot('s5'); return b
@chk('h_audio_slots')
def _(r):
    def sm(l):
        o=[]
        for m in r.mods.get(l,'').split('CvModulator(')[1:]:
            g=lambda k: (re.search(k+r'=([^,)]*)',m) or [None,None])[1]
            o.append((g('sourceId'),'byp='+str(g('bypassed')),'inv='+str(g('invert')),'min='+str(g('lfoMin')),'max='+str(g('lfoMax')),'band='+str(g('audioBand'))))
        return o
    for l in ('m0','m1','m2','m3','m4','m5'): print('  ',l,sm(l))
# ================= batch I
@case
def i_seq_disc():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.t+=.8; b.st('open_params',target='deckA'); b.t+=1
    b.click('cv_Deck A/Mandala/L2_seq'); b.t+=.6; b.click('btn_SEQ'); b.t+=.6; keys(b,'seq'); b.shot('seq'); return b
@chk('i_seq_disc')
def _(r): print('  ',' '.join(x for x in kf(r,'seq') if not x.startswith(('cv_','browser_row','s:','perf_','btnid_lobe','btn_','b:1','b:2'))))
@case
def i_copy_move():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8
    b.go(x=955,y=377); b.t+=.15; b.st('drag',x=1170,y=377,durationSec=.6); b.t+=1; keys(b,'pop'); b.shot('pop'); return b
@chk('i_copy_move')
def _(r): print('   popup',[x for x in kf(r,'pop') if x.startswith(('m:','b:')) and x[2:] not in ('File','Help','Output','View','A/B','BG/PV','MIXER')])
@case
def i_apply_bank():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.st('macro_bind',deck='deckA',unit='0',param='Deck A/Mandala/L1'); b.t+=.5
    b=libfull(b,'Macros'); b.t+=.4; b.click('btnid_maps_save_bank'); b.t+=.4; b.click('m:Deck A'); b.t+=.6; b.typ('tt_bank'); b.t+=.8
    b.rclick('s:tt_bank'); b.t+=.4; keys(b,'m'); b.shot('m'); b.click('s:tt_bank'); b.t+=.4; keys(b,'sel'); b.shot('sel'); return b
@chk('i_apply_bank')
def _(r):
    print('   menu',[x for x in kf(r,'m') if x.startswith('m:')]); print('   sel',[x for x in kf(r,'sel') if x.startswith(('b:','btnid_maps'))])
@case
def tw_roll_pickup():
    b=Tn('kifs','voronoi_cells'); b.st('randomization',value=1); b.t+=.5; keys(b,'k'); b.state('r0')
    b.st('audit_list',deck='A',target='before'); b.t+=.1
    b.st('set_page',target='mixer'); b.t+=.5; b.click('clock_trig_randDeckA'); b.t+=.8; b.st('set_page',target='ab'); b.t+=.6; b.state('r1')
    b.st('audit_list',deck='A',target='rolled'); b.t+=.1
    b.turn(1,1); b.t+=.3; b.state('r2'); b.st('audit_list',deck='A',target='turned'); b.t+=.1; return b
@chk('tw_roll_pickup')
def _(r):
    a,b_,c=alist(r,'before'),alist(r,'rolled'),alist(r,'turned')
    ch=[k for k in a if a[k]!=b_.get(k)]; print('   rolled params',ch[:8]); print('   after turn',[(k,b_.get(k),c.get(k)) for k in c if c[k]!=b_.get(k)])
    print('   knobs r0',[x and round(x['v'],3) for x in r.knobs('r0')[:4]],'r1',[x and round(x['v'],3) for x in r.knobs('r1')[:4]],'r2',[x and round(x['v'],3) for x in r.knobs('r2')[:4]])
def alist(r,label):
    o={}
    for l in open(f'{r.lib}/../../out/c_{r.n}.log',errors='replace'):
        if 'TOUR_AUDIT list' in l:
            p=l.rstrip().split('\t')
            if p[1].endswith('/'+label): o[p[2]]=[x for x in p if x.startswith('base=')][0]
    return o
# ================= batch J (simulated Twister)
@case
def tw_quick_master():
    b=Tn(); b.st('focus_pair',target='A'); b.t+=.5; b.state('a'); b.tap(12); b.t+=.6; b.state('b'); b.shot('b'); return b
@chk('tw_quick_master')
def _(r):
    print('   pair',r.ui('a','pair'),'->',r.ui('b','pair'))
    r.check('knob 13 tap in a pair view switches to the Master pair',r.ui('b','pair') in ('MASTER','M'),r.ui('b','pair'))
@case
def tw_bound_panes_clear():
    b=Tn(); b.st('fx_chain',deck='A',target=CH); b.t+=.5; b.click('fxcell_deckA_fx_1'); b.t+=.5; b.state('a'); b.shot('a')
    b.rb(); b.t+=.4; b.shot('rb1'); b.rb(); b.t+=.4; b.shot('rb2'); b.shift(1); b.btn(13); b.shift(0); b.t+=.6; b.state('c'); b.shot('c'); return b
@chk('tw_bound_panes_clear')
def _(r):
    print('   dock',r.ui('a','dock'),' fx',r.f('a','A','fx'),'->',r.f('c','A','fx'))
    r.check('Shift + right-bottom clears the bound slot',r.f('c','A','fx').split(',')[1].strip()=='-',r.f('c','A','fx'))
@case
def tw_lib_knob15():
    b=Tn(); b=srcq(b); b.t+=.3
    b.search('tt_'); b.click('browser_row_src_tt_beta'); b.key('shift+q',.3); b.key('shift+q',.3); b.rm(); b.t+=.5; b.state('s0'); b.shot('s0')
    b.turn(14,1); b.t+=.4; b.state('s1'); b.shot('s1')
    b.click('btn_FX'); b.t+=.5; b.state('fx'); b.shot('fx'); b.click('btn_Transitions'); b.t+=.5; b.state('tr'); b.shot('tr')
    b.click('btn_Sources'); b.t+=.4; b.click('btnid_lib_queue_split'); b.t+=.5; b.state('off'); return b
@chk('tw_lib_knob15')
def _(r):
    L=lambda l: [(i+1,x and x['lit'],x and round(x['v'],2)) for i,x in enumerate(r.knobs(l)) if i in (13,14)]
    for l in ('s0','s1','fx','tr','off'): print('  ',l,'lib',r.libm(l),L(l))
@case
def tw_playlist_whole():
    b=Tn(); b.rm(); b.t+=.5; b.state('a'); b.click('s:ttpl'); b.t+=.3; b.click('s:All'); b.t+=.3
    b.rb(); b.rb(); b.t+=.3; b.shot('pane'); b.turn(15,-30); b.t+=.3; b.turn(15,1); b.shot('c0')
    for i in range(14): b.turn(15,1); b.t+=.12
    b.shot('cur'); b.stap(15); b.t+=.6; b.state('b'); b.shot('b'); return b
@chk('tw_playlist_whole')
def _(r): print('   A/B',r.q('a'),'->',r.q('b'),r.to)
@case
def tw_send_master_rows():
    b=Tn(); b.rm(); b.t+=.5; b.tap(12); b.t+=.6; b.state('m'); keys(b,'m'); b.shot('m'); return b
@chk('tw_send_master_rows')
def _(r): print('   live',r.ui('m','live'),'lib',r.libm('m'))
@case
def tw_fx_focus_knobs():
    b=Tn(); b.st('fx_chain',deck='A',target=CH); b.t+=.6; b.state('a')
    b.click('btnid_slot_focus_deckA_fx_1'); b.t+=.6; b.state('f'); b.turn(5,4); b.t+=.4; b.state('g'); b.st('audit_dump',target='d'); b.t+=.1
    b.click('btnid_slot_focus_deckA_fx_1'); b.t+=.4; b.st('fx_chain',deck='A',target='library/fx_chains/crt.lsdfxchain'); b.t+=.8; b.state('h'); b.turn(6,3); b.t+=.4; b.state('i'); return b
@chk('tw_fx_focus_knobs')
def _(r):
    K=lambda l: [x and (round(x['v'],2),x['lit']) for x in r.knobs(l)[4:8]]
    for l in 'afghi': print('  ',l,K(l))
    print('   chain h',r.f('h','A','fx'))
@case
def tw_quick_master2():
    b=Tn(); b.st('focus_pair',target='A'); b.t+=.5; b.state('a'); b.stap(12); b.t+=.6; b.state('b'); b.stap(9); b.t+=.6; b.state('c'); return b
@chk('tw_quick_master2')
def _(r):
    print('   pair',r.ui('a','pair'),'->',r.ui('b','pair'),'->',r.ui('c','pair'))
    r.check('Shift + tap knob 13 in a pair view switches to the Master pair',r.ui('b','pair') in ('MASTER','M'),r.ui('b','pair'))
@case
def tw_send_master_fx():
    b=Tn(); b.rm(); b.t+=.5; b.click('btn_FX'); b.t+=.4; b.search('crt'); b.t+=.4; b.turn(15,2); b.t+=.3; b.state('a'); b.tap(12); b.t+=.8; b.state('m'); b.shot('m'); return b
@chk('tw_send_master_fx')
def _(r): print('   live',r.ui('a','live'),'->',r.ui('m','live'),' master chain',r.f('a','M_chain','name'),'->',r.f('m','M_chain','name'))
@case
def i_copy_srcfx():
    b=B(); b.tips=True; b.st('load_source',deck='A',target='kifs'); b.st('fx_chain',deck='A',target=CH); b.t+=.8; state(b,'a')
    b.go(x=955,y=377); b.t+=.15; b.st('drag',x=1170,y=377,durationSec=.6); b.t+=1; b.click('m:Copy Source + FX'); b.t+=1.2; state(b,'c'); b.shot('c')
    hover(b,'fxchain_save_deckB_fx',1.8)
    b.click('fxchain_more_deckB_fx'); b.t+=.4; b.click('m:Chain View'); b.t+=.8; keys(b,'cv'); b.shot('cv'); state(b,'cv'); return b
@chk('i_copy_srcfx')
def _(r):
    print('   B',r.st['c'].get('B'),'| chain',r.st['c'].get('B_chain'))
    r.check('Copy Source + FX: B gets the source and the three effects',r.f('c','B','fx')==r.f('a','A','fx') and 'Kifs' in r.st['c'].get('B',''),(r.f('a','A','fx'),r.f('c','B','fx')))
    for t in tips(r,'save'): print('    TIP',t[:200])
    print('   chain view',r.ui('cv','chain'),r.ui('cv','pair'))
# ================= batch K (themes)
@case
def k_theme_roundtrip():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.st('fx_chain',deck='A',target=CH); b.st('randomization',value=1); b.t+=1.2
    b.st('output_view',value=0); b.shot('d0',.3); b.st('theme',target='light'); b.t+=.8; b.shot('l0'); b.st('theme',target='dark'); b.t+=.8; b.shot('d1')
    b.st('set_page',target='mixer'); b.t+=.6; b.shot('md0'); b.st('theme',target='light'); b.t+=.8; b.shot('ml'); b.st('theme',target='dark'); b.t+=.8; b.shot('md1'); return b
@case
def k_light_new_items():
    b=B(); b.tips=True; b.st('theme',target='light'); b.t+=.4
    b.st('load_preset',deck='A',target=CUR); b.st('fx_chain',deck='A',target=CH); b.st('fx_dry_wet',deck='A',value=.4); b.st('randomization',value=1); b.st('super_mode',target='PICKUP') ; b.t+=1.5
    b.st('fx_link',deck='A',slot=0,value=1); b.st('super_knob',deck='A',value=.9); b.t+=.6; b.shot('perform'); hover(b,'btnid_fxlink_deckA_fx_0',1.8); b.shot('pickup_tip')
    b.click('perf_gen_badge_A'); b.t+=1; b.shot('bound_pill'); b.key('escape',.4)
    b.st('set_page',target='mixer'); b.t+=.6; b.shot('clock_tints')
    for d in ('A','B','BG','PV'): b.st('load_preset',deck=d,target='library/presets/tt_alpha.lsd')
    b.t+=1.5; b=libfull(b); b.search('tt_'); b.t+=.5; hover(b,rowS('tt_alpha'),1.8); b.shot('chips')
    b.st('library_mode',target='HALF'); b.st('set_page',target='bgpv'); b.t+=.5; b.st('open_params',target='deckBG'); b.t+=1; b.shot('empty_card'); return b
