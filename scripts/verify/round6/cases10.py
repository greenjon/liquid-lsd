"""Round 6 (2026-11-14): remaining checklist items. CASES=cases10 python3 round6/ops6.py <work dir> [case...]"""
import re,os,json,glob,shutil
from dsl import B
CASES={};CHECKS={};SETUP={};ENVS={}
def case(f): CASES[f.__name__]=f; return f
def chk(name):
    def d(f): CHECKS[name]=f; return f
    return d
def keys(b,l): b.t+=.1; b.st('audit_keys',target=l); b.t+=.05; return b
def state(b,l): return b.state(l)
REPO='/home/gj/projects/liquid-lsd'
def sess_setup(lib): shutil.copy(REPO+'/library/last_session.json.unreadable-20261112-141827',lib+'/last_session.json')
@case
def sess_unreadable():
    b=B(); b.t+=2; b.shot('start'); state(b,'a'); return b
SETUP['sess_unreadable']=sess_setup
@chk('sess_unreadable')
def _(r):
    log=open(r.lib+'/../../out/c_sess_unreadable.log',errors='replace').read()
    i=log.find('Failed to load session'); print(log[i:i+3000] if i>=0 else 'no failure line')
    r.check('session loads',len([x for x in os.listdir(r.lib) if 'unreadable' in x])==10)
@case
def first_run():
    b=B(); b.t+=3; b.shot('start'); state(b,'a'); b.t+=2; b.shot('later'); state(b,'b'); return b
def wipe(lib):
    for x in os.listdir(lib):
        if x!='natives': p=lib+'/'+x; shutil.rmtree(p) if os.path.isdir(p) and not os.path.islink(p) else os.remove(p)
@case
def fresh_install():
    b=B()
    for d in ('A','MASTER'): b.st('audit_chain',deck=d,target=d)
    b.t+=4; b.shot('start'); state(b,'a'); b.t+=3; b.shot('later'); state(b,'b'); return b
SETUP['fresh_install']=wipe
@chk('fresh_install')
def _(r):
    raw=r.st['b']['_raw']
    for d,p in (('A','groove-mandala'),('B','tunnel-synthwave'),('BG','ambient-tides'),('PV','julia-groove')):
        r.check(f'fresh install loads {p} on {d}',f'file={p}.lsd' in r.st['b'].get(d,''),r.st['b'].get(d,''))
    r.check('no chain unsaved at launch','dirty=true' not in ' '.join(v for k,v in r.st['b'].items() if k.endswith('_chain')),[ (k,v[-40:]) for k,v in r.st['b'].items() if k.endswith('_chain')])
    r.check('four load toasts or fewer',len(r.to)<=4,r.to)
ENVS['fresh_install']={'LSD_TOUR_STARTUP':'PREVIOUS_SESSION'}
ENVS['sess_unreadable']={'LSD_TOUR_STARTUP':'PREVIOUS_SESSION'}

@case
def sess_real():
    b=B(); b.t+=3; b.shot('start'); state(b,'a'); return b
SETUP['sess_real']=lambda lib: shutil.copy(REPO+'/library/last_session.json',lib+'/last_session.json')
ENVS['sess_real']={'LSD_TOUR_STARTUP':'PREVIOUS_SESSION'}
@chk('sess_real')
def _(r):
    n=len([x for x in os.listdir(r.lib) if 'unreadable' in x])
    r.check('your real last session loads (no new .unreadable copy)',n==10,n)
    r.check('decks restored','src=EMPTY' not in r.st['a'].get('A',''),r.st['a'].get('A'))
# ---------- discovery
@case
def disc_views():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=1
    keys(b,'perform'); b.shot('perform')
    b.full(); b.t+=.5; keys(b,'lib_src'); b.shot('lib_src')
    b.tab('FX'); keys(b,'lib_fx'); b.shot('lib_fx')
    b.tab('Transitions'); keys(b,'lib_tr'); b.shot('lib_tr')
    b.tab('Queues'); keys(b,'lib_q'); b.shot('lib_q')
    b.tab('Macros'); keys(b,'lib_m'); b.shot('lib_m')
    b.st('library_mode',target='HALF'); b.t+=.5
    b.st('open_params',target='A'); b.t+=.8; keys(b,'edit'); b.shot('edit')
    return b
def libfull(b=None,tab=None):
    b=b or B(); b.full(); b.t+=.3
    if tab: b.click('btn_'+tab); b.t+=.4
    return b
def qadd(b,rows,shift=False):
    for r_ in rows:
        b.click(r_); b.t+=.2
        if shift: b.st('mod',target='shift',value=1)
        b.key('q',.3)
        if shift: b.st('mod',target='shift',value=0)
    return b
@case
def disc_queue():
    b=libfull(); b.search('tt_'); b.t+=.5
    qadd(b,['browser_row_src_tt_alpha','browser_row_src_tt_beta','browser_row_src_tt_gamma'])
    qadd(b,['browser_row_src_tt_alpha'],shift=True)
    b.t+=.5; keys(b,'q'); b.shot('q'); state(b,'a')
    b.click('btn_FX'); b.t+=.5; keys(b,'fx'); b.shot('fx')
    return b
# ---------- section 1 / 16b: queues
QX={'AB':154,'FX_AB':321,'BG':488,'FX_BG':655,'TRANS':822}
def fill_all(b):
    b=libfull(b); b.search('tt_'); b.t+=.5
    for r_ in ['browser_row_src_tt_alpha','browser_row_src_tt_beta']: b.click(r_); b.key('q',.3)
    for r_ in ['browser_row_src_tt_gamma','browser_row_src_tt_beta']: b.click(r_); b.key('shift+q',.3)
    b.click('btn_FX'); b.t+=.4; b.click('browser_search'); b.key('ctrl+a',.1); b.key('backspace',.1); b.typ('tt_'); b.t+=.4
    b.click('browser_row_fx_tt_fx'); b.key('q',.3); b.key('shift+q',.3); b.click('browser_row_fx_tt_chain'); b.key('q',.3)
    b.click('btn_Transitions'); b.t+=.4; b.click('browser_search'); b.key('ctrl+a',.1); b.key('backspace',.1); b.typ('film'); b.t+=.4
    b.click(x=330,y=149); b.key('q',.3)
    b.t+=.3; return b
@case
def q_menus_clear_undo():
    b=fill_all(B()); state(b,'full'); b.shot('full')
    for q,x in QX.items():
        b.click(x=x,y=405); b.t+=.4; keys(b,'menu_'+q); b.shot('menu_'+q); b.click('m:Clear queue'); b.t+=.5; state(b,'clr_'+q)
        b.go(x=600,y=600); b.key('ctrl+z',.5); state(b,'undo_'+q)
    return b
@chk('q_menus_clear_undo')
def _(r):
    for q in QX:
        k=open(f'{r.lib}/../../out/keys_c_q_menus_clear_undo_menu_{q}.txt').read().split('\n')
        r.check(f'{q} queue ⋮ has Export and Clear','m:Export as playlist...' in k and 'm:Clear queue' in k,[x for x in k if x.startswith('m:')])
        f=r.q('full',q)[0]; c=r.q('clr_'+q,q)[0]; u=r.q('undo_'+q,q)[0]
        r.check(f'{q} filled',bool(f),f)
        r.check(f'{q} Clear empties it',c==[],c)
        r.check(f'{q} Ctrl+Z brings it back',u==f,(f,u))
    r.check('Clear toasts mention undo',any('undo' in t.lower() or 'ctrl+z' in t.lower() for t in r.to),r.to)
@case
def q_undo_marker():
    b=libfull(); b.search('tt_'); b.t+=.5
    for r_ in ['browser_row_src_tt_alpha','browser_row_src_tt_beta','browser_row_src_tt_gamma']: b.click(r_); b.key('q',.3)
    b.dbl(x=60,y=505); b.t+=1.5; state(b,'play')
    b.rclick(x=60,y=473); b.t+=.4; keys(b,'rowmenu'); b.shot('rowmenu'); b.click('m:Remove from queue'); b.t+=.5; state(b,'rm')
    b.go(x=600,y=600); b.key('ctrl+z',.5); state(b,'undo')
    b.click(x=124,y=440); b.t+=.3; state(b,'shuf'); b.shot('shuf')
    return b
@chk('q_undo_marker')
def _(r):
    p=r.q('play'); m=r.q('rm'); u=r.q('undo')
    r.check('double-click plays the 2nd item',p[1]==1,p)
    r.check('Remove drops the first row and the marker follows its item',m[0]==['tt_beta','tt_gamma'] and m[1]==0,m)
    r.check('Ctrl+Z restores items and the playing marker',u[0]==p[0] and u[1]==p[1],(p,u))
    r.check('Remove toasts what went',any('tt_alpha' in t for t in r.to),r.to)
@case
def s1_folders_fx_chain_trans():
    b=libfull(tab='FX')
    for sec,nm in (('Saved single FX','ttfold_fx'),('Saved chains','ttfold_ch')):
        b.t+=.8; b.click('s:'+sec); b.t+=.6; b.click('btnid_browserNewFolder'); b.t+=.8; b.shot('dlg_'+nm); b.typ(nm); b.t+=1.0; b.shot('typed_'+nm); b.shot('after_'+nm); keys(b,'k_'+nm)
    b.t+=.5; b.click('btn_Transitions'); b.t+=.8; b.click('s:Saved transitions'); b.t+=.6; b.click('btnid_browserNewFolder'); b.t+=.8; b.typ('ttfold_tr'); b.t+=1.0; keys(b,'k_tr'); b.shot('after_tr')
    b.rclick('s:ttfold_tr'); b.t+=.4; keys(b,'trmenu'); b.shot('trmenu'); b.key('escape',.3)
    b.click('s:Stock transitions'); b.t+=.3; b.rclick('s:Stock transitions'); b.t+=.4; keys(b,'stockmenu'); b.key('escape',.3)
    return b
@chk('s1_folders_fx_chain_trans')
def _(r):
    for d,n in (('fx','ttfold_fx'),('fx_chains','ttfold_ch'),('transitions','ttfold_tr')):
        r.check(f'New Folder under saved {d} makes {d}/{n}',os.path.isdir(f'{r.lib}/{d}/{n}'),os.listdir(f'{r.lib}/{d}')[:20])
    k=open(f'{r.lib}/../../out/keys_c_s1_folders_fx_chain_trans_trmenu.txt').read().split('\n')
    r.check('folder menu has Rename/Delete Folder',any('Rename' in x for x in k) and any('Delete' in x for x in k),[x for x in k if x.startswith('m:')])
    k=open(f'{r.lib}/../../out/keys_c_s1_folders_fx_chain_trans_stockmenu.txt').read().split('\n')
    r.check('no folder menu on stock transitions',not any('Folder' in x for x in k if x.startswith('m:')),[x for x in k if x.startswith('m:')])
@case
def s1_star_fx():
    b=libfull(tab='FX'); b.search('tt_'); b.t+=.4
    for row in ('browser_row_fx_tt_fx','browser_row_fx_tt_chain'):
        b.rclick(row); b.t+=.4; keys(b,'menu'); b.shot('menu_'+row[-5:])
        b.click('m:Add to Favorites'); b.t+=.4
    b.shot('starred'); b.click('s:Favorites'); b.t+=.5; keys(b,'fav'); b.shot('fav')
    return b
@chk('s1_star_fx')
def _(r):
    k=open(f'{r.lib}/../../out/keys_c_s1_star_fx_fav.txt').read().split('\n')
    r.check('starred single FX and chain listed under Favorites','browser_row_fx_tt_fx' in k and 'browser_row_fx_tt_chain' in k,[x for x in k if x.startswith('browser_row')][:10])
@case
def s1_multisel_into_playlist():
    b=libfull(); b.search('tt_'); b.t+=.4
    b.click('browser_row_src_tt_alpha'); b.st('mod',target='ctrl',value=1); b.click('browser_row_src_tt_gamma'); b.st('mod',target='ctrl',value=0); b.t+=.3
    b.drag('browser_row_src_tt_gamma','s:test playlist'); b.t+=.6; b.shot('dropped')
    b.click('btn_FX'); b.t+=.4; b.search('tt_')
    b.click('browser_row_fx_tt_fx'); b.st('mod',target='ctrl',value=1); b.click('browser_row_fx_tt_chain'); b.st('mod',target='ctrl',value=0); b.t+=.3
    b.drag('browser_row_fx_tt_chain','s:new'); b.t+=.6; b.shot('fxdropped')
    return b
@chk('s1_multisel_into_playlist')
def _(r):
    pl=[l.strip() for l in open(glob.glob(r.lib+'/playlists/**/test playlist.lsdplay',recursive=True)[0])]
    r.check('a multi-selection dropped on a playlist in the tree lands in it, in order',[x for x in pl if 'tt_' in x]==['tt_alpha.lsd','tt_gamma.lsd'],pl)
    fx=json.load(open(glob.glob(r.lib+'/fx_playlists/**/new.lsdfxplay',recursive=True)[0]))['items']
    r.check('same for saved FX onto an FX playlist',[x for x in fx if 'tt_' in x][-2:]==['tt_fx.lsdfx','tt_chain.lsdfxchain'] or len([x for x in fx if 'tt_' in x])==2,fx)
# ---------- section 1 batch 2
def srcq(b,names=('tt_alpha','tt_beta','tt_gamma')):
    b=libfull(b); b.search('tt_'); b.t+=.4
    for n in names: b.click('browser_row_src_'+n); b.key('q',.3)
    return b
@case
def s1_rename_in_queue_fav():
    b=srcq(B()); b.rclick('browser_row_src_tt_alpha'); b.t+=.4; keys(b,'menu'); b.click('m:Add to Favorites'); b.t+=.3
    b.rclick('browser_row_src_tt_alpha'); b.t+=.4; b.click('m:Rename...'); b.t+=.5; b.key('ctrl+a',.1); b.typ('tt_renamed'); b.t+=.8; state(b,'ren'); b.shot('ren')
    b.dbl(x=60,y=473); b.t+=1.5; state(b,'play'); b.shot('play')
    b.click('s:Favorites'); b.t+=.5; keys(b,'fav'); b.click('s:Recent'); b.t+=.5; keys(b,'rec'); return b
@chk('s1_rename_in_queue_fav')
def _(r):
    q=r.q('ren')[0]; r.check('the renamed preset keeps its queue row, under the new name',q and q[0]=='tt_renamed',q)
    a=r.st['play'].get('A','')+r.st['play'].get('B',''); r.check('the renamed queue row plays the renamed file','file=tt_renamed.lsd' in a,a)
    k=open(f'{r.lib}/../../out/keys_c_s1_rename_in_queue_fav_fav.txt').read().split('\n')
    r.check('Favorites still lists it','browser_row_src_tt_renamed' in k,[x for x in k if x.startswith('browser_row')][:8])
@case
def s1_delete_loaded_recent_master():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_beta.lsd'); b.st('fx_chain',deck='MASTER',target='library/fx_chains/tt_chain.lsdfxchain'); b.t+=1
    b=libfull(b); b.search('tt_'); b.t+=.4; b.rclick('browser_row_src_tt_beta'); b.t+=.4; b.click('m:Add to Favorites'); b.t+=.3
    b.rclick('browser_row_src_tt_beta'); b.t+=.4; b.click('m:Delete...'); b.t+=.5; b.shot('del'); b.key('enter',.8)
    b.click('s:Favorites'); b.t+=.5; keys(b,'fav'); b.click('s:Recent'); b.t+=.5; keys(b,'rec')
    b.click('btn_FX'); b.t+=.4; b.search('tt_'); b.rclick('browser_row_fx_tt_chain'); b.t+=.4; b.click('m:Delete...'); b.t+=.5; b.key('enter',.8); state(b,'a')
    b.st('library_mode',target='HALF'); b.t+=.4; b.st('set_page',target='mixer'); b.t+=.5; keys(b,'mix'); b.shot('mix')
    return b
@chk('s1_delete_loaded_recent_master')
def _(r):
    for l in ('fav','rec'):
        k=open(f'{r.lib}/../../out/keys_c_s1_delete_loaded_recent_master_{l}.txt').read().split('\n')
        r.check(f'deleted preset gone from {l}','browser_row_src_tt_beta' not in k,[x for x in k if x.startswith('browser_row')][:8])
    r.check('tt_beta file gone',not r.exists('presets/tt_beta.lsd'))
    m=r.st['a'].get('M_chain',''); r.check("Master's chain no longer points at the deleted file",'file=null' in m,m)
@case
def s1_multisel_into_folder_saveback():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=1
    b=libfull(b); b.search('tt_'); b.t+=.4
    b.click('browser_row_src_tt_alpha'); b.st('mod',target='ctrl',value=1); b.click('browser_row_src_tt_beta'); b.st('mod',target='ctrl',value=0); b.t+=.3
    b.drag('browser_row_src_tt_beta','s:tt_folder'); b.t+=.8; b.shot('moved')
    b.st('library_mode',target='HALF'); b.t+=.4; b.st('set_param',deck='A',param='L1',value=.61); b.t+=.4; b.click('perf_src_save_A'); b.t+=1; state(b,'a'); return b
@chk('s1_multisel_into_folder_saveback')
def _(r):
    r.check('both selected rows moved into the folder',r.exists('presets/tt_folder/tt_alpha.lsd') and r.exists('presets/tt_folder/tt_beta.lsd'),os.listdir(r.lib+'/presets/tt_folder'))
    r.check('no old-name copy at the top',not r.exists('presets/tt_alpha.lsd') and not r.exists('presets/tt_beta.lsd'))
    a=r.st['a'].get('A',''); r.check('the loaded deck saves back into the folder (clean after Save)','dirty=false' in a and 'file=tt_alpha.lsd' in a,a)
    try: v=r.js('presets/tt_folder/tt_alpha.lsd'); r.check('saved value written to the moved file','0.61' in json.dumps(v),'')
    except Exception as e: r.check('saved value written to the moved file',False,e)
@case
def s1_folder_onto_section_selection():
    b=libfull(); b.click('s:Saved presets'); b.t+=.4; b.click('btnid_browserNewFolder'); b.t+=.8; b.typ('tt_outer'); b.t+=1
    b.drag('s:tt_folder','s:tt_outer'); b.t+=.8; b.shot('nested'); keys(b,'nested')
    b.drag('s:tt_folder','s:Saved presets'); b.t+=.8; b.shot('back'); keys(b,'back')
    b.click('s:tt_outer'); b.t+=.3; b.rclick('s:tt_outer'); b.t+=.4; b.click('m:Rename Folder...'); b.t+=.6; b.key('ctrl+a',.1); b.typ('tt_outer2'); b.t+=.8; b.shot('renamed'); keys(b,'renamed')
    b.rclick('s:tt_outer2'); b.t+=.4; b.click('m:Delete Folder...'); b.t+=.6; b.shot('deldlg'); b.key('enter',.8); b.shot('deleted'); keys(b,'deleted')
    return b
@chk('s1_folder_onto_section_selection')
def _(r):
    r.check('a folder dragged onto its section moves back to the top',r.exists('presets/tt_folder') and not r.exists('presets/tt_outer/tt_folder'),os.listdir(r.lib+'/presets')[:5])
    r.check('renamed folder exists',not r.exists('presets/tt_outer') and not r.exists('presets/tt_outer2'),'deleted afterwards')

@case
def disc_edit():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.st('fx_filter',deck='A',slot=0,target='bloom'); b.t+=1
    b.st('open_params',target='A'); b.t+=1; keys(b,'edit'); b.shot('edit')
    b.st('open_params_pair',target='A'); b.t+=1; keys(b,'pair'); b.shot('pair')
    b.st('close_focus'); b.t+=.6; b.st('set_page',target='mixer'); b.t+=.6; keys(b,'mixer'); b.shot('mixer')
    return b

# ---------- batch 3
CH='library/fx_chains/tt_chain.lsdfxchain'
@case
def s6_dirty_events():
    b=B(); b.st('load_source',deck='A',target='mandala'); b.st('fx_chain',deck='A',target=CH); b.t+=1; state(b,'c0')
    b.st('super_knob',deck='A',value=.8); b.t+=.5; state(b,'super')
    b.st('meta_knob',deck='A',slot=0,value=.9); b.t+=.5; state(b,'meta')
    b.st('fx_link',deck='A',slot=0,value=0); b.t+=.5; state(b,'link')
    b.st('fx_chain',deck='A',target=CH); b.t+=.8; state(b,'rel1')
    b.st('fx_swap',deck='A',slot=0,slot2=1); b.t+=.5; state(b,'swap')
    b.st('fx_chain',deck='A',target=CH); b.t+=.8; b.st('fx_filter',deck='A',slot=2,target='invert'); b.t+=.5; state(b,'slot')
    return b
@chk('s6_dirty_events')
def _(r):
    d=lambda l: 'dirty=true' in r.st[l].get('A_chain','')
    r.check('chain loaded from file is clean',not d('c0'),r.st['c0'].get('A_chain'))
    r.check('turning the Super Knob is not a change',not d('super'))
    r.check('turning a Metaknob is not a change',not d('meta'))
    r.check('unlinking a slot is a change',d('link'))
    r.check('reloading is clean again',not d('rel1'))
    r.check('swapping slots is a change',d('swap'),r.st['swap'].get('A_chain'))
    r.check('a new effect in a slot is a change',d('slot'))
@case
def s4_stock_click_defaults():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=1; state(b,'pre')
    b.click('perf_gen_badge_A'); b.t+=.6; b.search('mandala'); b.t+=.4; keys(b,'bound'); b.shot('bound')
    b.click('browser_row_src_mandala'); b.t+=1; state(b,'once'); b.st('audit_param',target='p1',param='Deck A/Mandala/L1'); b.t+=.1
    b.click('browser_row_src_mandala'); b.t+=1; state(b,'twice'); b.st('audit_param',target='p2',param='Deck A/Mandala/L1'); b.t+=.1; b.shot('twice')
    return b
@chk('s4_stock_click_defaults')
def _(r):
    r.check('preset loaded first','file=tt_alpha.lsd' in r.st['pre'].get('A',''),r.st['pre'].get('A'))
    a=r.st['once'].get('A',''); r.check('clicking its stock source loads the defaults (no preset file)','src=Mandala' in a and 'file=null' in a,a)
    r.check('clicking it again changes nothing',r.st['twice'].get('A')==r.st['once'].get('A') and r.param['p1']['base']==r.param['p2']['base'],(r.st['twice'].get('A'),r.param['p1']['base'],r.param['p2']['base']))
@case
def s16b_copy_move_srcfx():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.st('fx_chain',deck='A',target=CH); b.t+=1; state(b,'a0')
    b.click('perf_src_more_A'); b.t+=.4; keys(b,'more'); b.shot('more')
    return b
@case
def s1_unsaved_prompt():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=1; b.st('set_param',deck='A',param='L1',value=.33); b.t+=.4
    b=libfull(b); b.search('tt_'); b.t+=.4; b.st('crossfade',value=1); b.t+=.3
    b.dbl('browser_row_src_tt_gamma'); b.t+=.8; b.shot('prompt'); keys(b,'prompt'); state(b,'p')
    b.key('escape',.6); state(b,'esc'); b.shot('esc')
    return b
@chk('s1_unsaved_prompt')
def _(r):
    k=open(f'{r.lib}/../../out/keys_c_s1_unsaved_prompt_prompt.txt').read().split('\n')
    print('   prompt buttons:',[x for x in k if x.startswith('b:')][:20])
    r.check('Esc cancels: deck A keeps tt_alpha','file=tt_alpha.lsd' in r.st['esc'].get('A',''),r.st['esc'].get('A'))
@case
def s1_save_transition_row():
    b=B(); b.st('set_page',target='mixer'); b.t+=.4; b.st('transition',target='film_burn'); b.t+=.5; b.st('trans_param',param='burnSpread',value=.9); b.t+=.6; b.shot('changed'); keys(b,'changed')
    b.click('perf_trans_save'); b.t+=.8; b.shot('save'); keys(b,'save')
    return b
@case
def disc_params():
    b=B()
    for s_ in ('kifs','isometric_voxels'):
        b.st('load_source',deck='A',target=s_); b.t+=1; b.st('audit_list',deck='A',target=s_); b.t+=.1
    return b
@case
def tw_s11_discrete_midi():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.t+=1
    for path in ('Deck A/Kifs/ColorMode','Deck A/KIFS/ColorMode'): b.st('midi_map_range',param=path,unit='102')
    b.t+=.3
    for i,v in enumerate((0,20,40,50,70,90,110,127,64,33)):
        b.st('midi',target=f'0:102:{v}'); b.t+=.25
        b.st('audit_list',deck='A',target=f'v{i}'); b.t+=.05
    return b
@chk('tw_s11_discrete_midi')
def _(r):
    L=[l.split('\t') for l in open(f'{r.lib}/../../out/c_tw_s11_discrete_midi.log',errors='replace') if 'TOUR_AUDIT list' in l and '\tColorMode\t' in l]
    d=lambda l,k: float([x for x in l if x.startswith(k+'=')][0].split('=')[1])
    vals=[(d(l,'base'),d(l,'cur')) for l in L]; mn,mx=d(L[0],'min'),d(L[0],'max')
    print('   ColorMode (base, live) from CC sweep:',vals,'range',mn,mx)
    steps=[mn+(mx-mn)*i/4 for i in range(5)]
    r.check('a controller CC steps a discrete parameter: the live value is always on one of its 5 steps',vals and all(min(abs(c-s_) for s_ in steps)<1e-3 for _,c in vals) and len(set(c for _,c in vals))>=4,vals)
