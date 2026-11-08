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
