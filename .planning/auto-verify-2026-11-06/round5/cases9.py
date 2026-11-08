"""Round 5 overnight: names typed into save and rename dialogs. CASES=cases9 python3 round5/ops5.py <work dir> [case...]"""
import os,glob
from dsl import B
CASES={};CHECKS={};SETUP={}
def case(f): CASES[f.__name__]=f; return f
def chk(name):
    def d(f): CHECKS[name]=f; return f
    return d
def trans_dialog(b):
    b.st('set_page',target='mixer'); b.t+=.4; b.st('transition',target='film_burn'); b.t+=.4; b.click('perf_trans_more'); b.t+=.2; b.click('m:Save As...'); b.t+=.3
    return b
@case
def n_trans_bad_then_good():
    b=trans_dialog(B()); b.typ('AC/DC'); b.t+=1; b.shot('slash'); b.typ('CON'); b.t+=1; b.shot('reserved'); b.typ('Café Ñandú'); b.t+=.8; b.shot('after')
    return b
@chk('n_trans_bad_then_good')
def _(r):
    r.check('nothing written for AC/DC',not r.exists('transitions/AC') and not glob.glob(r.lib+'/**/DC.lsdtrans',recursive=True))
    r.check('nothing written for CON',not glob.glob(r.lib+'/**/CON.lsdtrans',recursive=True))
    r.check('accented name saved as typed',r.exists('transitions/Café Ñandú.lsdtrans'),sorted(os.listdir(r.lib+'/transitions'))[-5:])
def lib(s='tt_'):
    b=B(); b.full(); b.search(s); return b
@case
def n_rename_bad_then_good():
    b=lib(); b.rclick('browser_row_src_tt_alpha'); b.click('m:Rename...'); b.t+=.3; b.typ('a/b'); b.t+=1; b.shot('slash'); b.typ('NUL.x'); b.t+=1; b.shot('reserved'); b.typ('Ünïcødé ålpha'); b.t+=.8; b.shot('after')
    return b
@chk('n_rename_bad_then_good')
def _(r):
    r.check('renamed to the accented name',r.exists('presets/Ünïcødé ålpha.lsd') and not r.exists('presets/tt_alpha.lsd'),sorted(os.listdir(r.lib+'/presets'))[-4:])
    r.check('nothing made for a/b or NUL.x',not r.exists('presets/a') and not glob.glob(r.lib+'/presets/NUL*'))
@case
def n_playlist_bad_then_good():
    b=B(); b.full(); b.click('s:ttpl'); b.click('btnid_browserNewPlaylist'); b.t+=.3; b.typ('set:1'); b.t+=1; b.shot('colon'); b.typ('Liste été'); b.t+=.8; b.shot('after')
    return b
@chk('n_playlist_bad_then_good')
def _(r):
    found=glob.glob(r.lib+'/**/*.lsdplay',recursive=True)
    r.check('playlist made with the accented name',any(f.endswith('/Liste été.lsdplay') for f in found),[os.path.basename(f) for f in found][-5:])
    r.check('nothing made for set:1',not any('set' in os.path.basename(f) for f in found))
# ---- full disk: library/presets on a tiny tmpfs (run ops5 inside `unshare -Urm`)
import subprocess,shutil,hashlib
def tmpfs_presets(lib,fill):
    p=lib+'/presets'; keep=lib+'/../presets_keep'; shutil.rmtree(keep,ignore_errors=True); shutil.move(p,keep); os.makedirs(p)
    subprocess.run(['mount','-t','tmpfs','-o','size=64m','tmpfs',p],check=True)
    for x in [x for x in os.listdir(keep) if not x.endswith('.zip')]: (shutil.copytree if os.path.isdir(keep+'/'+x) else shutil.copy2)(keep+'/'+x,p+'/'+x)
    open(lib+'/../alpha.sha','w').write(hashlib.sha1(open(p+'/tt_alpha.lsd','rb').read()).hexdigest())
    if fill: subprocess.run(['dd','if=/dev/zero',f'of={p}/zz_fill','bs=4k'],stderr=subprocess.DEVNULL)
def save_case(b):
    b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=.9; b.st('set_param',deck='A',param='L1',value=.77); b.t+=.4; b.click('perf_src_save_A'); b.t+=1.2; b.shot('saved'); return b
@case
def n_full_disk_save():
    return save_case(B())
SETUP['n_full_disk_save']=lambda lib: tmpfs_presets(lib,True)
@chk('n_full_disk_save')
def _(r):
    p=r.lib+'/presets'; now=hashlib.sha1(open(p+'/tt_alpha.lsd','rb').read()).hexdigest(); was=open(r.lib+'/../alpha.sha').read()
    r.check('preset untouched on a full disk',now==was)
    r.check('still valid JSON',bool(r.js('presets/tt_alpha.lsd')))
    r.check('no temp files left',not [x for x in os.listdir(p) if x.endswith('.tmp')],os.listdir(p))
    log=open(os.path.normpath(r.lib+'/../../out/c_n_full_disk_save.log'),errors='replace').read()
    r.check('the failure is logged','No space left' in log,[l for l in log.splitlines() if 'tt_alpha' in l and ('ERROR' in l or 'WARN' in l)][:2])
@case
def n_tmpfs_save_ok():
    return save_case(B())
SETUP['n_tmpfs_save_ok']=lambda lib: tmpfs_presets(lib,False)
@chk('n_tmpfs_save_ok')
def _(r):
    r.check('saves fine with room (control)',abs(r.js('presets/tt_alpha.lsd')['parameters']['L1']['baseValue']-.77)<1e-3)
    r.check('no temp files left',not [x for x in os.listdir(r.lib+'/presets') if x.endswith('.tmp')])
# ---- 10,000 presets: the clipped Library list still navigates, scrolls and opens row menus
def big_library(lib):
    src=lib+'/presets/tt_alpha.lsd'
    for i in range(10000):
        d=f'{lib}/presets/big/set{i//500:02d}'; os.makedirs(d,exist_ok=True); shutil.copy(src,f'{d}/flame_{i:05d}.lsd')
@case
def n_big_keys():
    b=B(); b.full(); b.search('flame_'); b.t+=.5; b.click('browser_row_src_flame_00000'); b.t+=.4
    for _ in range(40): b.key('down',.12)
    b.t+=.4; b.shot('nav'); b.st('audit_rects',target='nav'); b.t+=.1; return b
SETUP['n_big_keys']=big_library
@chk('n_big_keys')
def _(r):
    # Rows the clipper drew at the end: the list followed the keyboard down; row 0 stays drawn because it is still selected.
    log=open(os.path.normpath(r.lib+'/../../out/c_n_big_keys.log'),errors='replace').read()
    import re as _re
    m=_re.search(r'TOUR_RECTS \S+ t=\S+ (.*)',log); keys=[k.rpartition('=')[0] for k in (m.group(1).split('\t') if m else [])]
    rows=sorted(k for k in keys if k.startswith('browser_row_src_flame_'))
    r.check('keyboard walks 40 rows down a clipped list: row 40 drawn with the rows in view (plus the selected row 0), not all 10,000','browser_row_src_flame_00040' in rows and 'browser_row_src_flame_00000' in rows and len(rows)<60,(len(rows),rows[:1],rows[-1:]))
@case
def n_big_menu():
    b=B(); b.full(); b.search('flame_'); b.t+=.5; b.go('browser_row_src_flame_00000')
    for _ in range(6): b.st('scroll',value=-40.0,dt=.15)
    b.t+=.6; b.shot('scrolled'); b.rclick(x=400,y=300); b.t+=.4; b.shot('menu'); b.click('m:Load to Deck A'); b.t+=1.2; b.state('a',.4); return b
SETUP['n_big_menu']=big_library
@chk('n_big_menu')
def _(r):
    a=r.st['a'].get('A','')
    import re as _re
    m=_re.search(r'flame_(\d{5})',a)
    r.check('a row far down the clipped list opens its menu and loads',bool(m) and int(m.group(1))>100,a[:90])
@case
def n_small_keys():
    # Control: the same keys on a list short enough not to be clipped (100 rows).
    b=B(); b.full(); b.search('flame_000'); b.t+=.5; b.click('browser_row_src_flame_00000'); b.t+=.4
    for _ in range(40): b.key('down',.12)
    b.t+=.4; b.key('enter',1.2); b.state('a',.4); return b
SETUP['n_small_keys']=big_library
@chk('n_small_keys')
def _(r): r.check('control (unclipped): Enter after 40 downs',True,r.st['a'].get('A','')[:90])
@case
def n_down_enter():
    # Library, short list: click a row, Down once, Enter. enter_src (round 2) shows click + Enter loads.
    b=B(); b.full(); b.search('tt_'); b.t+=.4; b.click('browser_row_src_tt_alpha'); b.t+=.3; b.key('down',.3); b.shot('down'); b.key('enter',1.2); b.state('a',.4); return b
@chk('n_down_enter')
def _(r): r.check('Down then Enter loads the next row',any(n in (r.st['a'].get('A','')+r.st['a'].get('B','')) for n in ('tt_beta','tt_gamma')),(r.st['a'].get('A','')[:60],r.st['a'].get('B','')[:60]))
def _downs(n,gap):
    def f():
        b=B(); b.full(); b.search('flame_000'); b.t+=.5; b.click('browser_row_src_flame_00000'); b.t+=.4
        for _ in range(n): b.key('down',gap)
        b.t+=.4; b.shot('end'); b.key('enter',1.5); b.state('a',.4); return b
    return f
for n,g in ((5,.12),(40,.3)):
    nm=f'n_downs_{n}_{int(g*100)}'; CASES[nm]=_downs(n,g); SETUP[nm]=big_library
    CHECKS[nm]=(lambda n: lambda r: r.check(f'{n} downs then Enter loads flame_{n:05d}',f'flame_{n:05d}' in r.st['a'].get('A','')+r.st['a'].get('B',''),r.st['a'].get('A','')[:70]))(n)
def _big_downs():
    b=B(); b.full(); b.search('flame_'); b.t+=.5; b.click('browser_row_src_flame_00000'); b.t+=.4
    for _ in range(40): b.key('down',.3)
    b.t+=.4; b.key('enter',1.5); b.state('a',.4); return b
CASES['n_big_downs_enter']=_big_downs; SETUP['n_big_downs_enter']=big_library
CHECKS['n_big_downs_enter']=lambda r: r.check('clipped list (10,000 rows): 40 downs then Enter loads flame_00040','flame_00040' in r.st['a'].get('A','')+r.st['a'].get('B',''),r.st['a'].get('A','')[:70])
