"""Upgrading over an older install: old bundled shaders replaced, edited ones kept, presets from old versions still load. CASES=cases5 python3 ops3.py <work dir>."""
import os,subprocess,hashlib,glob,json
from dsl import B
CASES={};CHECKS={};SETUP={}
ROOT=subprocess.check_output(['git','rev-parse','--show-toplevel'],text=True).strip()
def git(*a): return subprocess.check_output(['git','-C',ROOT,*a])
def old_blob(rel):
    """An older shipped version of library/sources/<rel>, differing from today's."""
    for path in (f'library/sources/{rel}',f'src/main/resources/default_sources/{rel}'):
        for c in git('log','--all','--format=%h','--',path).decode().split():
            try: b=git('show',f'{c}:{path}')
            except Exception: continue
            yield b
def pick_old(lib):
    ship=set(l.strip() for l in open(ROOT+'/src/main/resources/shipped_source_hashes.txt'))
    for rel in ('kifs/kifs.fs','quasicrystal/quasicrystal.fs','wave_ripples/wave_ripples.fs','mandelbox/mandelbox.fs'):
        p=f'{lib}/sources/{rel}'
        if not os.path.exists(p): continue
        cur=open(p,'rb').read()
        for b in old_blob(rel):
            if b!=cur and f'{rel} {hashlib.sha256(b).hexdigest()}' in ship: return rel,b,cur
    raise Exception('no older shipped version found')
INFO={}
def setup_up(lib):
    rel,b,cur=pick_old(lib); open(f'{lib}/sources/{rel}','wb').write(b); INFO['old']=(rel,cur)
    ed=sorted(glob.glob(f'{lib}/sources/mandelbulb/*.fs'))[0]; t=open(ed).read()+'\n// my own edit\n'; open(ed,'w').write(t); INFO['edit']=(os.path.relpath(ed,f'{lib}/sources'),t)
SETUP['up_shaders']=setup_up
def up_shaders():
    b=B(); b.t+=1; b.st('load_source',deck='A',target='mandelbulb'); b.state('a',1.5); return b
CASES['up_shaders']=up_shaders
def _(r):
    rel,cur=INFO['old']; r.check(f'an unedited older shipped shader is replaced ({rel})',open(f'{r.lib}/sources/{rel}','rb').read()==cur)
    rel,t=INFO['edit']; r.check(f'an edited one is kept ({rel})',open(f'{r.lib}/sources/{rel}').read()==t)
    r.check('and the edited one loads','Mandelbulb' in r.st['a']['A'],r.st['a']['A'][:60])
CHECKS['up_shaders']=_
# Presets from three points in the repo's history, one deck load each.
OLD=[]
def setup_old(lib):
    os.makedirs(f'{lib}/presets/old',exist_ok=True)
    for c in OLDC:
        for i,n in enumerate(git('ls-tree','-r','--name-only',c).decode().splitlines()):
            if ok(n) and n not in OLD_SEEN:
                OLD_SEEN.add(n); out=f"{c}_{os.path.basename(n)}".replace(' ','_')
                try: open(f'{lib}/presets/old/{out}','wb').write(git('show',f'{c}:{n}'))
                except Exception: pass
OLDC=[git('log','-1','--format=%h','--before='+d).decode().strip() for d in ('2026-09-15','2026-10-20')]
def ok(n): return n.endswith('.lsd') and not n.startswith('web/')
OLD_SEEN=set()
NAMES=[]
for c in OLDC:
    for n in git('ls-tree','-r','--name-only',c).decode().splitlines():
        if ok(n) and n not in NAMES: NAMES.append(n)
NAMES=NAMES[:14]+NAMES[14::12][:26]
def old_case():
    b=B()
    for n in NAMES:
        c=OLDC[0] if n in git('ls-tree','-r','--name-only',OLDC[0]).decode() else OLDC[1]
        b.st('load_preset',deck='A',target=f"library/presets/old/{c}_{os.path.basename(n)}".replace(' ','_')); b.state('s%d'%NAMES.index(n),1.2)
    b.key('ctrl+z',.5); b.state('z'); return b
CASES['up_old_presets']=old_case
SETUP['up_old_presets']=setup_old
def _(r):
    want=lambda n:os.path.basename(n).replace(' ','_')
    bad=[(n,r.st.get('s%d'%i,{}).get('A','')[:70]) for i,n in enumerate(NAMES) if not (r.f('s%d'%i,'A','file') or '').endswith(want(n))]
    r.check(f'{len(NAMES)} presets from old versions load: {len(NAMES)-len(bad)}',not bad,bad[:6])
    r.check('no exceptions in the log',not [w for w in r.warn if 'xception' in w],r.warn[:3])
CHECKS['up_old_presets']=_

def setup_bad(lib):
    open(f'{lib}/presets/tt_broken.lsd','w').write('{"name":"x","parameters":{"M":3.0}}')
SETUP['bad_preset_toast']=setup_bad
def bad():
    b=B(); b.st('load_source',deck='A',target='kifs'); b.t+=.8; b.st('load_preset',deck='A',target='library/presets/tt_broken.lsd'); b.state('a',1); return b
CASES['bad_preset_toast']=bad
def _(r):
    r.check('a damaged preset shows a toast',any('damaged' in t for t in r.to),r.to)
    r.check('and the deck keeps what it had','Kifs' in r.st['a']['A'],r.st['a']['A'][:50])
CHECKS['bad_preset_toast']=_
