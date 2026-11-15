"""Scripted Library, queue, FX chain and binding checks. Each case runs against a fresh copy of the library."""
import json,os,glob,shutil
from dsl import B
CASES={};CHECKS={};SETUP={}
def case(f): CASES[f.__name__]=f; return f
def chk(name):
    def d(f): CHECKS[name]=f; return f
    return d
def rowS(n): return 'browser_row_src_'+n
def lib(tab=None,s='tt_'):
    b=B()
    if tab: b.tab(tab)
    b.full(); b.search(s); return b
def ctrlclick(b,*a,**k): b.st('mod',target='ctrl',value=1); b.t+=.08; b.click(*a,**k); b.st('mod',target='ctrl',value=0); b.t+=.08; return b
def shiftclick(b,*a,**k): b.st('mod',target='shift',value=1); b.t+=.08; b.click(*a,**k); b.st('mod',target='shift',value=0); b.t+=.08; return b
def altclick(b,*a,**k): b.st('mod',target='alt',value=1); b.t+=.08; b.click(*a,**k); b.st('mod',target='alt',value=0); b.t+=.08; return b
X={'AB':60,'FX_AB':225,'BG':395,'FX_BG':560,'TRANS':730}
KEB={'AB':154,'FX_AB':321,'BG':488,'FX_BG':655,'TRANS':822}
Y=[472,504,536]
FXI=['tt_fx','tt_chain','2d_to_3d_elevation_with_feedback']
ITEMS={'AB':('src',['tt_alpha','tt_beta','tt_gamma'],'q'),'BG':('src',['tt_alpha','tt_beta','tt_gamma'],'shift+q'),'FX_AB':('fx',FXI,'q'),'FX_BG':('fx',FXI,'shift+q'),'TRANS':('trans',['film_burn','kinetic_zoom','vortex_swirl'],'q')}
def fillq(q):
    k,names,key=ITEMS[q]; b=B()
    if k!='src': b.tab(k)
    b.full()
    for n in names: b.search(n[:12]); b.click(f'browser_row_{k}_{n}'); b.key(key)
    return b
# ---------- selection and queues
@case
def multi_ctrl():
    b=lib(); b.click(rowS('tt_alpha')); ctrlclick(b,rowS('tt_gamma')); b.key('q'); b.state('a'); return b
@chk('multi_ctrl')
def _(r): r.check('Ctrl+click then Q queues the two rows',r.q('a')[0]==['tt_alpha','tt_gamma'],r.q('a'))
@case
def multi_shift():
    b=lib(); b.click(rowS('tt_alpha')); shiftclick(b,rowS('tt_gamma')); b.key('q'); b.state('a'); return b
@chk('multi_shift')
def _(r): r.check('Shift+click ranges',r.q('a')[0]==['tt_alpha','tt_beta','tt_gamma'],r.q('a'))
@case
def multi_shiftq():
    b=lib(); b.click(rowS('tt_alpha')); ctrlclick(b,rowS('tt_gamma')); b.key('shift+q'); b.state('a'); return b
@chk('multi_shiftq')
def _(r): r.check('Shift+Q queues both in BG',r.q('a','BG')[0]==['tt_alpha','tt_gamma'],r.q('a','BG'))
@case
def multi_drag():
    b=lib(); b.click(rowS('tt_alpha')); ctrlclick(b,rowS('tt_gamma')); b.drag(rowS('tt_gamma'),x=X['AB'],y=600); b.state('a'); return b
@chk('multi_drag')
def _(r): r.check('dragging a preset selection queues all, in order',r.q('a')[0]==['tt_alpha','tt_gamma'],r.q('a'))
@case
def multi_fx():
    b=B(); b.tab('fx'); b.full(); b.search('tt_'); b.click('browser_row_fx_tt_chain'); ctrlclick(b,'browser_row_fx_tt_fx'); b.drag('browser_row_fx_tt_fx',x=X['FX_AB'],y=600); b.state('a'); return b
@chk('multi_fx')
def _(r): r.check('dragging an FX selection queues all',len(r.q('a','FX_AB')[0] or [])==2,r.q('a','FX_AB'))
@case
def multi_trans():
    b=B(); b.tab('trans'); b.full(); b.search('film'); b.click('browser_row_trans_film_burn'); shiftclick(b,'browser_row_trans_inferno_film_burn'); b.drag('browser_row_trans_inferno_film_burn',x=X['TRANS'],y=600); b.state('a'); return b
@chk('multi_trans')
def _(r): r.check('dragging a transition selection queues all',len(r.q('a','TRANS')[0] or [])>=2,r.q('a','TRANS'))
@case
def drag_line():
    b=fillq('AB'); b.search('tt_inside'); b.drag(rowS('tt_inside'),x=60,y=488); b.state('b'); return b
@chk('drag_line')
def _(r): r.check('dropped at the line between rows 1 and 2',r.q('b')[0]==['tt_alpha','tt_inside','tt_beta','tt_gamma'],r.q('b'))
@case
def fxq_refuse():
    b=lib(); b.drag(rowS('tt_alpha'),x=X['FX_AB'],y=600); b.state('a'); return b
@chk('fxq_refuse')
def _(r): r.check('source refused by FX queue',r.q('a','FX_AB')[0]==[] and r.q('a')[0]==[],r.q('a','FX_AB'))
@case
def playlist_drop():
    b=B(); b.full(); b.drag('s:ttpl',x=X['AB'],y=600); b.state('a'); return b
@chk('playlist_drop')
def _(r): r.check('playlist dropped puts its items in',r.q('a')[0]==['tt_alpha','tt_beta','tt_gamma'],r.q('a'))
def undo_case(q,how):
    def f():
        b=fillq(q); b.state('before'); x=X[q]
        if how=='reorder': b.drag(sx=x,sy=Y[2],x=x,y=Y[0]-7)
        elif how=='remove': b.rclick(x=x,y=Y[1]); b.shot('menu',.2); b.click('m:Remove from BG queue' if q=='BG' else 'm:Remove from queue')
        elif how=='delete': b.click(x=x,y=Y[1]); b.key('delete')
        elif how=='clear': b.click(x=KEB[q],y=405); b.click('m:Clear queue')
        b.state('edited'); b.shot('edited',.2); b.key('ctrl+z'); b.state('undone'); return b
    return f
def undo_chk(q,how):
    def f(r):
        a=r.q('before',q)[0]; e=r.q('edited',q)[0]; u=r.q('undone',q)[0]
        r.check(f'{q} filled',a is not None and len(a)==3,a)
        r.check(f'{q} {how} changed it',e!=a,e)
        if how=='reorder': r.check(f'{q} reorder puts row 3 first',e and e[0]==a[2],e)
        r.check(f'{q} {how} undone',u==a,(a,e,u))
    return f
for q in X:
    for how in('reorder','remove','delete','clear'):
        n=f'undo_{q}_{how}'; CASES[n]=undo_case(q,how); CHECKS[n]=undo_chk(q,how)
@case
def bg_item_down():
    b=fillq('BG'); b.state('a'); b.drag(sx=X['BG'],sy=Y[0],x=X['BG'],y=Y[1]+7); b.state('b'); return b
@chk('bg_item_down')
def _(r): r.check('BG item dragged down lands at the line',r.q('b','BG')[0]==['tt_beta','tt_alpha','tt_gamma'],r.q('b','BG'))
@case
def dbl_queue():
    b=fillq('AB'); b.state('a'); b.dbl(x=60,y=Y[1]); b.t+=7; b.state('b'); return b
@chk('dbl_queue')
def _(r):
    r.check('double-click moved the queue position',r.q('b')[1]==1,r.q('b'))
    r.check('item loaded on a deck','tt_beta' in r.st['b'].get('A','')+r.st['b'].get('B',''))
    r.check('crossfader moved',r.f('b','M','xf')!=r.f('a','M','xf'),(r.f('a','M','xf'),r.f('b','M','xf')))
@case
def trans_q():
    b=B(); b.tab('trans'); b.full(); b.search('film'); b.click('browser_row_trans_film_burn'); b.key('q'); b.state('a'); return b
@chk('trans_q')
def _(r): r.check('Q queues in Transitions',len(r.q('a','TRANS')[0] or [])==1,r.q('a','TRANS'))
# ---------- keys
@case
def enter_src():
    b=lib(); b.click(rowS('tt_alpha')); b.key('enter'); b.state('a',.6); return b
@chk('enter_src')
def _(r): r.check('Enter loads on a deck','file=tt_alpha' in r.st['a']['_raw'])
@case
def enter_fx():
    b=B(); b.tab('fx'); b.full(); b.search('kaleid'); b.click('browser_row_fx_kaleidoscope'); b.key('enter'); b.state('a',.6); return b
@chk('enter_fx')
def _(r): r.check('Enter applies an effect','Kaleidoscope' in r.st['a']['_raw'])
@case
def enter_trans():
    b=B(); b.tab('trans'); b.full(); b.search('film'); b.click('browser_row_trans_film_burn'); b.key('enter'); b.state('a',.6); return b
@chk('enter_trans')
def _(r): r.check('Enter applies a transition','film' in (r.f('a','M','trans') or '').lower(),r.f('a','M','trans'))
@case
def alt_click():
    b=lib(); altclick(b,rowS('tt_alpha')); b.state('a',.6); return b
@chk('alt_click')
def _(r): r.check('Alt+click previews on PV','file=tt_alpha' in r.st['a'].get('PV',''),r.st['a'].get('PV'))
@case
def delete_key():
    b=lib(); b.click(rowS('tt_alpha')); b.key('delete'); b.key('escape'); b.state('a'); b.click(rowS('tt_alpha')); b.key('backspace'); b.key('enter'); b.state('b',.6); return b
@chk('delete_key')
def _(r): r.check('Delete + Esc keeps, Backspace + Enter deletes',not r.exists('presets/tt_alpha.lsd'))
@case
def esc_newplaylist():
    b=B(); b.full(); b.click('s:ttpl'); b.click('btnid_browserNewPlaylist'); b.key('escape'); b.state('a'); return b
@chk('esc_newplaylist')
def _(r): r.check('Esc on New Playlist stays in the Library','lib=FULL' in r.st['a']['_raw'])
@case
def esc_export():
    b=fillq('AB'); b.click(x=KEB['AB'],y=405); b.click('m:Export as playlist...'); b.key('escape'); b.state('a'); return b
@chk('esc_export')
def _(r): r.check('Esc on Export stays in the Library','lib=FULL' in r.st['a']['_raw'])
@case
def esc_menu():
    b=lib(); b.rclick(rowS('tt_alpha')); b.key('escape'); b.state('a'); return b
@chk('esc_menu')
def _(r): r.check('Esc closes a menu without going back','lib=FULL' in r.st['a']['_raw'])
# ---------- tags
def fmt(r,f): return r.js(f).get('tags') or []
def seedtags(L):
    for n in('tt_alpha','tt_beta','tt_gamma'):
        p=f'{L}/presets/{n}.lsd'; d=json.load(open(p)); d['tags']=['aa_one','aa_two']; json.dump(d,open(p,'w'),indent=2)
@case
def tag_multi():
    b=lib(); b.click(rowS('tt_alpha')); ctrlclick(b,rowS('tt_gamma')); b.rclick(rowS('tt_gamma')); b.click('m:Add Tag (2)...'); b.typ('zz_multi'); return b
@chk('tag_multi')
def _(r): r.check('tag on both, not on the third','zz_multi' in fmt(r,'presets/tt_alpha.lsd') and 'zz_multi' in fmt(r,'presets/tt_gamma.lsd') and 'zz_multi' not in fmt(r,'presets/tt_beta.lsd'))
@case
def tag_remove():
    b=lib(); b.rclick(rowS('tt_alpha')); b.go('m:Remove Tag'); b.t+=.4; b.click('m:aa_one'); b.state('a'); return b
SETUP['tag_remove']=seedtags
@chk('tag_remove')
def _(r): r.check('Remove Tag submenu removes it',fmt(r,'presets/tt_alpha.lsd')==['aa_two'],fmt(r,'presets/tt_alpha.lsd'))
def opentags(b): b.click(x=14,y=272); b.t+=.3; return b
@case
def tag_rename():
    b=lib(); opentags(b); b.rclick('s:aa_one'); b.click('m:Rename Tag...'); b.typ('aa_new'); return b
SETUP['tag_rename']=seedtags
@chk('tag_rename')
def _(r): r.check('Rename Tag on every item',all(fmt(r,f'presets/{n}.lsd')==['aa_new','aa_two'] for n in('tt_alpha','tt_beta','tt_gamma')),fmt(r,'presets/tt_alpha.lsd'))
@case
def tag_delete():
    b=lib(); opentags(b); b.rclick('s:aa_one'); b.click('m:Delete Tag...'); b.key('enter',.5); return b
SETUP['tag_delete']=seedtags
@chk('tag_delete')
def _(r): r.check('Delete Tag on every item',all(fmt(r,f'presets/{n}.lsd')==['aa_two'] for n in('tt_alpha','tt_beta','tt_gamma')),fmt(r,'presets/tt_alpha.lsd'))
@case
def tag_drag():
    b=lib(); opentags(b); b.drag(rowS('tt_inside'),'s:aa_one'); return b
SETUP['tag_drag']=seedtags
@chk('tag_drag')
def _(r): r.check('row dragged onto a tag gets it','aa_one' in fmt(r,'presets/tt_folder/tt_inside.lsd'),fmt(r,'presets/tt_folder/tt_inside.lsd'))
@case
def tag_loaded_save():
    b=lib(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd',dt=.1); b.t+=.8; b.rclick(rowS('tt_alpha')); b.click('m:Add Tag...'); b.typ('zz_keep'); b.key('ctrl+s',.8); return b
@chk('tag_loaded_save')
def _(r): r.check('tag survives Ctrl+S of the loaded preset','zz_keep' in fmt(r,'presets/tt_alpha.lsd'),fmt(r,'presets/tt_alpha.lsd'))
# ---------- missing items
def _miss(L):
    os.makedirs(f'{L}/presets/tt_moved',exist_ok=True); shutil.move(f'{L}/presets/tt_gamma.lsd',f'{L}/presets/tt_moved/tt_gamma.lsd')
    open(f'{L}/playlists/ttmiss.lsdplay','w').write('presets/tt_alpha.lsd\npresets/tt_gamma.lsd\n')
@case
def missing_findall():
    b=B(); b.full(); b.click('s:ttmiss'); b.t+=.3; b.click('b:Find all'); b.t+=.4; b.state('a'); return b
SETUP['missing_findall']=_miss
@chk('missing_findall')
def _(r):
    t=open(r.lib+'/playlists/ttmiss.lsdplay').read(); r.check('Find all repoints the moved item','tt_moved' in t,t)
# ---------- drops
@case
def drop_preset():
    b=B(); b.search('tt_alpha'); b.drag(rowS('tt_alpha'),x=29,y=68); b.state('a',.8); return b
@chk('drop_preset')
def _(r): r.check('deck badge takes a preset','file=tt_alpha' in r.st['a']['A'],r.st['a']['A'][:90])
@case
def drop_stock():
    b=B(); b.search('metaballs'); b.drag(rowS('metaballs'),x=29,y=216); b.state('a',.8); return b
@chk('drop_stock')
def _(r): r.check('deck badge takes a stock source','Metaballs' in r.st['a']['B'],r.st['a']['B'][:90])
@case
def drop_chain():
    b=B(); b.tab('fx'); b.search('tt_chain'); b.drag('browser_row_fx_tt_chain',x=29,y=142); b.state('a',.8); return b
@chk('drop_chain')
def _(r): r.check('deck FX badge takes a chain','tt_chain' in r.st['a']['A_chain'],r.st['a']['A_chain'][:80])
@case
def drop_single_full():
    b=B(); b.tab('fx'); b.search('tt_fx'); b.drag('browser_row_fx_tt_fx',x=29,y=142); b.state('a',.8); return b
@chk('drop_single_full')
def _(r): r.check('full chain: saved FX dropped on the badge tells you',any('full' in t.lower() for t in r.to),r.to)
@case
def drop_single_vacant():
    b=B(); b.st('fx_filter',deck='A',slot=1,target=''); b.t+=.5; b.tab('fx'); b.search('tt_fx'); b.drag('browser_row_fx_tt_fx',x=29,y=142); b.state('a',.8); return b
@chk('drop_single_vacant')
def _(r): r.check('saved FX takes the first vacant slot','levation' in fxlist(r,'a')[1] or '3d' in fxlist(r,'a')[1].lower(),fxlist(r,'a'))
@case
def drop_slot():
    b=B(); b.tab('fx'); b.search('kaleid'); b.drag('browser_row_fx_kaleidoscope','fxcell_deckA_fx_1'); b.state('a',.8); return b
@chk('drop_slot')
def _(r): r.check('slot takes a stock effect, in slot 2','Kaleidoscope' in (r.f('a','A','fx') or '').split(',')[1],r.f('a','A','fx'))
@case
def drop_monitor_chain():
    b=B(); b.tab('fx'); b.search('tt_chain'); b.drag('browser_row_fx_tt_chain',x=955,y=378); b.state('a',.8); return b
@chk('drop_monitor_chain')
def _(r): r.check('deck monitor refuses FX (presets and sources only, as the guide says)','tt_chain' not in r.st['a']['A_chain'],r.st['a']['A_chain'][:70])
# ---------- load races, failures, prompts
@case
def last_click():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.st('load_source',deck='A',target='metaballs',dt=.034); b.state('a',1.2); return b
@chk('last_click')
def _(r): r.check('preset then source in a blink: last wins','Metaballs' in r.st['a']['A'] and 'file=null' in r.st['a']['A'],r.st['a']['A'][:90])
def _bad(L): open(f'{L}/fx/tt_broken.lsdfx','w').write('{ not json')
@case
def fx_failed_load():
    b=B(); b.tab('fx'); b.search('tt_broken'); b.shot('list',.1); b.dbl('browser_row_fx_tt_broken'); b.t+=.3; b.click('m:Slot 1: Luma displace'); b.state('a',.6); b.shot('a',.1); return b
SETUP['fx_failed_load']=_bad
@chk('fx_failed_load')
def _(r): r.check('failed FX load shows a toast',any('tt_broken' in t or 'load' in t.lower() for t in r.to),r.to)
def _badt(L): open(f'{L}/transitions/tt_broken.lsdtrans','w').write('{ not json')
@case
def trans_failed_load():
    b=B(); b.tab('trans'); b.search('tt_broken'); b.dbl('browser_row_trans_tt_broken'); b.state('a',.6); return b
SETUP['trans_failed_load']=_badt
@chk('trans_failed_load')
def _(r): r.check('failed transition load shows a toast',any('tt_broken' in t for t in r.to),r.to)
def dirty_then_load(b):
    b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=.8; b.st('set_param',deck='A',param='L1',value=.77); b.t+=.3; b.search('tt_beta'); b.state('a'); b.rclick(rowS('tt_beta')); b.click('m:Load to Deck A'); b.shot('prompt',.3); return b
@case
def prompt_esc():
    b=dirty_then_load(B()); b.key('escape',.5); b.state('b'); return b
@chk('prompt_esc')
def _(r):
    r.check('deck is dirty first','dirty=true' in r.st['a']['A'],r.st['a']['A'][:90])
    r.check('Esc cancels: same preset','file=tt_alpha' in r.st['b']['A'],r.st['b']['A'][:90])
@case
def prompt_enter():
    b=dirty_then_load(B()); b.key('enter',1.0); b.state('b'); return b
@chk('prompt_enter')
def _(r):
    v=r.js('presets/tt_alpha.lsd')['parameters'].get('L1',{}).get('baseValue')
    r.check('Enter saved the old preset',v is not None and abs(v-.77)<.01,v)
    r.check('and loaded the new one','file=tt_beta' in r.st['b']['A'],r.st['b']['A'][:90])
# ---------- FX chains
CH='deckA_fx'
def chainmenu(b,item): b.click('fxchain_more_'+CH); b.t+=.15; b.click('m:'+item); b.t+=.3; return b
@case
def chain_clean_launch():
    b=B(); b.state('a',1.5); return b
@chk('chain_clean_launch')
def _(r):
    for k in('A','B','BG','PV','M'): r.check(f'{k} chain clean at launch','dirty=false' in r.st['a'][k+'_chain'],r.st['a'][k+'_chain'][:70])
@case
def chain_clean_launch_fresh():
    b=B(); b.state('a',1.5); return b
def _fresh(L): shutil.rmtree(L); os.makedirs(L)
SETUP['chain_clean_launch_fresh']=_fresh
@chk('chain_clean_launch_fresh')
def _(r):
    for k in('A','B','BG','PV','M'): r.check(f'{k} chain clean at launch, fresh install','dirty=false' in r.st['a'][k+'_chain'],r.st['a'][k+'_chain'][:70])
@case
def chain_param_dirty_revert():
    b=B(); b.state('a',1.5); b.st('fx_param',deck='A',slot=2,param='threshold',value=.91); b.state('b',.6); chainmenu(b,'Revert to Saved'); b.state('c',.6); return b
@chk('chain_param_dirty_revert')
def _(r):
    r.check('param tweak marks dirty','dirty=true' in r.st['b']['A_chain'])
    r.check('Revert to Saved cleans','dirty=false' in r.st['c']['A_chain'],r.st['c']['A_chain'][:70])
@case
def chain_super_not_dirty():
    b=B(); b.state('a',1.5); b.st('super_knob',deck='A',value=.9); b.state('b',.6); return b
@chk('chain_super_not_dirty')
def _(r): r.check('turning the Super Knob is not a change',r.f('a','A_chain','dirty')==r.f('b','A_chain','dirty')=='false',(r.f('a','A_chain','dirty'),r.f('b','A_chain','dirty')))
def fxlist(r,l,k='A'): return [x.strip() for x in (r.f(l,k,'fx') or '[]').strip('[]').split(',')]
@case
def chain_clear_undo():
    b=B(); b.state('a',1); chainmenu(b,'Clear All Slots'); b.state('b',.5); b.key('ctrl+z',.5); b.state('c',.5); return b
@chk('chain_clear_undo')
def _(r):
    r.check('Clear All empties slots',fxlist(r,'b')==['-','-','-'],fxlist(r,'b'))
    r.check('Ctrl+Z brings them back',fxlist(r,'c')==fxlist(r,'a'),fxlist(r,'c'))
@case
def chain_copy_paste_undo():
    b=B(); b.state('a',1); chainmenu(b,'Copy Chain'); b.click('fxchain_more_deckB_fx'); b.t+=.15; b.click('m:Paste Chain'); b.t+=.3; b.state('b',.5); b.key('ctrl+z',.5); b.state('c',.5); return b
@chk('chain_copy_paste_undo')
def _(r):
    r.check('Paste copies onto B',fxlist(r,'b','B')==fxlist(r,'a','A'),fxlist(r,'b','B'))
    r.check('Ctrl+Z undoes Paste',fxlist(r,'c','B')==fxlist(r,'a','B'),fxlist(r,'c','B'))
@case
def slot_swap_undo():
    b=B(); b.state('a',1); b.drag('fxcell_deckA_fx_0','fxcell_deckA_fx_1'); b.state('b',.5); b.key('ctrl+z',.5); b.state('c',.5); return b
@chk('slot_swap_undo')
def _(r):
    a=fxlist(r,'a'); r.check('slot drag swaps',fxlist(r,'b')==[a[1],a[0],a[2]],fxlist(r,'b'))
    r.check('Ctrl+Z undoes the swap',fxlist(r,'c')==a,fxlist(r,'c'))
@case
def slot_ctrl_copy_undo():
    b=B(); b.state('a',1); b.st('mod',target='ctrl',value=1); b.t+=.08; b.drag('fxcell_deckA_fx_0','fxcell_deckA_fx_1'); b.st('mod',target='ctrl',value=0); b.state('b',.5); b.key('ctrl+z',.5); b.state('c',.5); return b
@chk('slot_ctrl_copy_undo')
def _(r):
    a=fxlist(r,'a'); r.check('Ctrl+drag copies',fxlist(r,'b')==[a[0],a[0],a[2]],fxlist(r,'b'))
    r.check('Ctrl+Z undoes the copy',fxlist(r,'c')==a,fxlist(r,'c'))
@case
def chain_saveas():
    b=B(); chainmenu(b,'Save As...'); b.shot('dlg',.3); b.typ('tt_saved_chain'); b.state('a',.6); return b
@chk('chain_saveas')
def _(r):
    r.check('Save As writes the file',r.exists('fx_chains/tt_saved_chain.lsdfxchain'))
    r.check('chain takes the name','tt_saved_chain' in r.st['a']['A_chain'],r.st['a']['A_chain'][:70])
def _tagchain(L):
    for f in glob.glob(f'{L}/fx_chains/liquid_mercury.lsdfxchain'):
        d=json.load(open(f)); d['tags']=['aa_keep']; json.dump(d,open(f,'w'),indent=2)
@case
def chain_save_keeps_tags():
    b=B(); b.st('fx_param',deck='A',slot=2,param='threshold',value=.91,dt=1); b.t+=.6; b.click('fxchain_save_deckA_fx'); b.t+=.3; b.key('enter',.5); b.state('a',.6); return b
SETUP['chain_save_keeps_tags']=_tagchain
@chk('chain_save_keeps_tags')
def _(r):
    r.check('saving over a chain keeps tags',r.js('fx_chains/liquid_mercury.lsdfxchain').get('tags')==['aa_keep'],r.js('fx_chains/liquid_mercury.lsdfxchain').get('tags'))
    r.check('clean afterwards','dirty=false' in r.st['a']['A_chain'],r.st['a']['A_chain'][:70])
def _rochain(L): os.chmod(f'{L}/fx_chains/liquid_mercury.lsdfxchain',0o444)
@case
def chain_save_readonly():
    b=B(); b.st('fx_param',deck='A',slot=2,param='threshold',value=.91,dt=1); b.t+=.6; b.click('fxchain_save_deckA_fx'); b.t+=.3; b.key('enter',.5); b.state('a',.6); b.shot('a',.1); return b
SETUP['chain_save_readonly']=_rochain
@chk('chain_save_readonly')
def _(r):
    r.check('failed save shows a message',any('save' in t.lower() or 'could not' in t.lower() for t in r.to),r.to)
    r.check('and stays unsaved','dirty=true' in r.st['a']['A_chain'],r.st['a']['A_chain'][:70])
    os.chmod(r.lib+'/fx_chains/liquid_mercury.lsdfxchain',0o644)
@case
def eject_keeps_chain():
    b=B(); b.st('load_source',deck='A',target='metaballs'); b.state('a',1); b.click('perf_eject_A'); b.state('b',.5); return b
@chk('eject_keeps_chain')
def _(r): r.check('Eject empties the deck and keeps its chain','src=EMPTY' in r.st['b']['A'] and fxlist(r,'b')==fxlist(r,'a'),r.st['b']['A'][:80])
# ---------- binding in place
@case
def bind_src_apply_undo():
    b=B(); b.click('perf_gen_badge_A'); b.t+=.3; b.search('metaballs'); b.click(rowS('metaballs')); b.state('a',.8); b.key('ctrl+z',.6); b.state('b',.6); return b
@chk('bind_src_apply_undo')
def _(r):
    r.check('bound list: a click applies','Metaballs' in r.st['a']['A'],r.st['a']['A'][:80])
    r.check('Ctrl+Z undoes','Metaballs' not in r.st['b']['A'],r.st['b']['A'][:80])
@case
def bind_fx_apply_undo():
    b=B(); b.click('fxcell_deckA_fx_1'); b.t+=.3; b.search('kaleid'); b.click('browser_row_fx_kaleidoscope'); b.state('a',.8); b.key('ctrl+z',.6); b.state('b',.6); return b
@chk('bind_fx_apply_undo')
def _(r):
    r.check('bound slot: a click applies to it','Kaleidoscope' in fxlist(r,'a')[1],fxlist(r,'a'))
    r.check('Ctrl+Z undoes','Kaleidoscope' not in fxlist(r,'b')[1],fxlist(r,'b'))
@case
def bind_esc_no_apply():
    b=B(); b.st('load_source',deck='A',target='metaballs'); b.t+=.8; b.click('perf_gen_badge_A'); b.t+=.3; b.go(rowS('aurora_ribbons')); b.key('escape',.4); b.state('a',.5); return b
@chk('bind_esc_no_apply')
def _(r): r.check('Esc stops without applying','Metaballs' in r.st['a']['A'],r.st['a']['A'][:80])
# ---------- monitor drag: nine actions
MA=(955,378); MB=(1173,378)
def mon(verb,scope):
    def f():
        b=B(); b.st('load_source',deck='A',target='metaballs'); b.st('load_source',deck='B',target='voronoi_cells'); b.st('fx_filter',deck='B',slot=0,target='kaleidoscope'); b.state('a',1.2)
        b.drag(sx=MA[0],sy=MA[1],x=MB[0],y=MB[1]); b.t+=.3; b.click('m:'+verb+' '+scope); b.state('b',1.2); b.key('ctrl+z',.6); b.state('c',.8); return b
    return f
def srcname(r,l,k): return (r.f(l,k,'src') or '')
def monchk(verb,scope):
    def f(r):
        As,Bs=srcname(r,'a','A'),srcname(r,'a','B'); Af,Bf=fxlist(r,'a','A'),fxlist(r,'a','B')
        a2,b2=srcname(r,'b','A'),srcname(r,'b','B'); fa2,fb2=fxlist(r,'b','A'),fxlist(r,'b','B')
        s=scope!='FX'; x=scope!='Source'; 
        exp_b=As if s else Bs
        exp_a=As if not s or verb=='Copy' else ('EMPTY' if verb=='Move' else Bs)
        r.check(f'{verb} {scope}: target source',b2==exp_b,(b2,exp_b))
        r.check(f'{verb} {scope}: origin source',a2==exp_a,(a2,exp_a))
        exp_fb=Af if x else Bf
        exp_fa=Af if not x or verb=='Copy' else (['-','-','-'] if verb=='Move' else Bf)
        r.check(f'{verb} {scope}: target FX',fb2==exp_fb,(fb2,exp_fb))
        r.check(f'{verb} {scope}: origin FX',fa2==exp_fa,(fa2,exp_fa))
        if verb=='Move' and x: r.check('after a Move the origin chain is Untitled','Untitled' in r.st['b']['A_chain'],r.st['b']['A_chain'][:60])
    return f
for v in('Move','Copy','Swap'):
    for sc in('Source','FX','Source + FX'):
        n='mon_'+v+'_'+sc.replace(' + ','_'); CASES[n]=mon(v,sc); CHECKS[n]=monchk(v,sc)
# ---------- saves
@case
def trans_save_as():
    b=B(); b.st('set_page',target='mixer'); b.t+=.4; b.st('transition',target='film_burn'); b.t+=.4; b.click('perf_trans_more'); b.t+=.2; b.click('m:Save As...'); b.shot('dlg',.2); b.typ('tt_saved_trans'); b.t+=.5; return b
@chk('trans_save_as')
def _(r): r.check('transition Save As writes the file',r.exists('transitions/tt_saved_trans.lsdtrans'))
@case
def deck_save_folder():
    b=B(); b.st('load_preset',deck='A',target='library/presets/tt_folder/tt_inside.lsd'); b.t+=.9; b.st('set_param',deck='A',param='L1',value=.77); b.t+=.4; b.click('perf_src_save_A'); b.t+=.8; b.state('a'); return b
@chk('deck_save_folder')
def _(r):
    v=r.js('presets/tt_folder/tt_inside.lsd')['parameters']['L1']['baseValue']
    r.check('Save writes back into the subfolder',abs(v-.77)<.01,v)
    r.check('no new copy at the top',not r.exists('presets/tt_inside.lsd'))
    r.check('clean afterwards','dirty=false' in r.st['a']['A'],r.st['a']['A'][:90])
@case
def eject_undo_source():
    b=B(); b.st('load_source',deck='A',target='metaballs'); b.t+=.8; b.st('load_source',deck='A',target='voronoi_cells'); b.state('a',.8); b.click('perf_gen_badge_A'); b.t+=.2; b.search('kali'); b.click(rowS('kali_set')); b.state('b',.6); b.key('ctrl+z',.6); b.state('c',.6); return b
@chk('eject_undo_source')
def _(r): r.check('Ctrl+Z after a source change brings the old one back','Voronoi' in r.st['c']['A'],r.st['c']['A'][:70])
@case
def undo_chain_load():
    b=B(); b.state('a',1); b.tab('fx'); b.search('tt_chain'); b.go('browser_row_fx_tt_chain'); b.st('rclick',dt=.03); b.t+=.25; b.shot('menu',.2); b.click('m:Load to Deck A'); b.state('b',.8); b.key('ctrl+z',.6); b.state('c',.6); return b
@chk('undo_chain_load')
def _(r):
    r.check('chain loaded','tt_chain' in r.st['b']['A_chain'],r.st['b']['A_chain'][:60])
    r.check('Ctrl+Z undoes a Library chain load','tt_chain' not in r.st['c']['A_chain'],r.st['c']['A_chain'][:60])
# ---------- transitions: inputs move with their knob, picture changes
@case
def trans_flash_knob():
    b=B(); b.st('load_source',deck='A',target='quasicrystal'); b.st('load_source',deck='B',target='voronoi_cells'); b.st('crossfade',value=-.3); b.st('transition',target='luminous_flash',dt=.3); b.shot('low',.6); b.st('audit_set',deck='TRANS',target='luminous_flash',param='intensity',value=1.0); b.st('audit_read',dt=.3); b.shot('high',.3); return b
@chk('trans_flash_knob')
def _(r):
    import numpy as np
    from PIL import Image
    ln=[l for l in open(r.lib+'/../../out/c_trans_flash_knob.log',errors='replace') if 'TOUR_AUDIT read' in l]
    r.check('knob sets Luminous Flash Intensity',ln and 'base=1.0' in ln[0],ln[:1])
@case
def trans_linear_still_fades():
    b=B(); b.st('load_source',deck='A',target='quasicrystal'); b.st('load_source',deck='B',target='voronoi_cells'); b.st('crossfade',value=-1); b.st('transition',target='linear_crossfade',dt=.3); b.state('a',.6); b.st('crossfade',value=1); b.state('b',.6); return b
@chk('trans_linear_still_fades')
def _(r): r.check('crossfader still moves',r.f('a','M','xf')!=r.f('b','M','xf'))
