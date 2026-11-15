#!/usr/bin/env python3
"""ops3.py <work dir> [case...]: as round2/ops.py, plus the fake Twister's output per state label. Results appended to results/ops.txt."""
import json,os,re,shutil,subprocess,sys
H=os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0,H); H2=os.environ.get('RESULTS_DIR') or H; sys.path.insert(0,os.environ.get('CASE_DIR') or H); sys.dont_write_bytecode=True
A=sys.argv[1]; BIN=os.environ.get('RTBIN') or A+'/../rt/build/install/rt/bin/rt'; CWD=A+'/cwd'; LIB=CWD+'/library'; BASE=A+'/libbase'
ENV=dict(os.environ,__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia',XDG_DATA_HOME=A+'/xdg/data',XDG_CONFIG_HOME=A+'/xdg/config',XDG_CACHE_HOME=A+'/xdg/cache',LSD_TOUR_W='1280',LSD_TOUR_H='720')
import importlib
C=importlib.import_module(os.environ.get('CASES','cases3'))
DUMP={};MODS={}
def parse(n):
    st={};to=[];w=[];out={};cur=[];DUMP.clear();MODS.clear()
    for l in open(f'{A}/out/{n}.log',errors='replace'):
        l=l.rstrip('\n')
        if 'TOUR_STATE ' in l:
            p=l.split('TOUR_STATE ')[1].split(' | '); h=p[0].split(); d={'_raw':l}
            for x in p[1:]:
                if ': ' in x: k,v=x.split(': ',1); d[k]=v
                elif '=' in x: d.setdefault('_kv',x)
            st[h[0]]=d; out[h[0]]=cur; cur=[]
        elif 'TOUR_DUMP ' in l: k,*v=l.split('TOUR_DUMP ')[1].split(' ',1); DUMP[k]=' '.join(v)
        elif 'TOUR_MODS ' in l: k,*v=l.split('TOUR_MODS ')[1].split(' ',1); MODS[k]=' '.join(v)
        elif 'TOUR_KEYS ' in l: print('  KEYS',' | '.join(x for x in l.split('|') if x.startswith(('m:','s:','b:')) and not x[2:].isdigit())[:900],flush=True)
        elif 'TOUR_TOAST ' in l: to.append(l.split('TOUR_TOAST ')[1])
        elif 'TOUR_MIDI_OUT ' in l: cur.append(l.split('TOUR_MIDI_OUT ')[1])
        elif 'TOUR_WARN' in l or 'Exception' in l or 'not registered' in l: w.append(l[:170])
    out['_end']=cur
    return st,to,w,out
class R:
    def __init__(s,n,st,to,lib,out,warn): s.n,s.st,s.to,s.lib,s.out,s.warn=n,st,to,lib,out,warn; s.ok=s.bad=0; s.lines=[]
    def q(s,label,q='AB'):
        m=re.match(r'\[(.*)\] active=(-?\d+) cur=(-?\d+)',s.st.get(label,{}).get('Q'+q,''))
        return ([x.strip() for x in m[1].split(',') if x.strip()],int(m[2]),int(m[3])) if m else (None,None,None)
    def f(s,label,k,field):
        m=re.search(field+r'=(\[[^\]]*\]|.*?)(?= \w+=|$)',s.st.get(label,{}).get(k,'')); return m[1] if m else None
    def page(s,label):
        m=re.search(r'page=(\S+)',s.st.get(label,{}).get('_raw','')); return m[1] if m else None
    def libm(s,label):
        m=re.search(r' lib=(\S+)',s.st.get(label,{}).get('_raw','')); return m[1] if m else None
    def ui(s,label,field): return s.f(label,'ui',field)
    def knobs(s,label):
        o=[]
        for x in s.st.get(label,{}).get('knobs','').split(';'):
            if x=='-' or not x: o.append(None); continue
            p=x.split(','); o.append(dict(v=float(p[0]),rgb=tuple(map(float,p[1:4])),lit=p[4]=='1',meter=p[5],ring=float(p[6]),marker=p[7]=='1'))
        return o
    def cc(s,label): return [tuple(map(int,x.split()[1:])) for x in s.out.get(label,[]) if x.startswith('cc ')]
    def sysex(s,label): return [x[6:] for x in s.out.get(label,[]) if x.startswith('sysex')]
    def check(s,what,cond,detail=''):
        x=f"  {'PASS' if cond else 'FAIL'} {s.n}: {what}"+(f'   [{str(detail)[:300]}]' if detail!='' and not cond else ''); print(x,flush=True); s.lines.append(x)
        s.ok+=bool(cond); s.bad+=not cond
    def exists(s,rel): return os.path.exists(f'{s.lib}/{rel}')
    def js(s,rel): return json.load(open(f'{s.lib}/{rel}'))
def run(n):
    b=C.CASES[n](); steps,shots=b.done()
    shutil.rmtree(LIB,ignore_errors=True); shutil.copytree(BASE,LIB,symlinks=True)
    if n in C.SETUP: C.SETUP[n](LIB)
    json.dump({"seed":7,"bpm":128.0,"durationSec":b.t+1,"tooltips":False,"steps":steps},open(f'{A}/scripts/c_{n}.json','w'))
    env=dict(ENV)
    if n.startswith('tw_') or n.startswith('xt_'): env['LSD_FAKE_TWISTER']='1'
    if n.startswith('xt_'): env['LSD_FAKE_TWISTER_PROFILE']='midi-fighter-twister-xt'
    with open(f'{A}/out/c_{n}.log','w') as lg:
        subprocess.run([BIN,f'--tour={A}/scripts/c_{n}.json',f'--tour-out={A}/out/c_{n}.mp4','--no-audio','--tour-hidden','--tour-fps=30','--tour-scale=1','--tour-encoder=nvenc','--tour-no-hashes'],cwd=CWD,env=env,stdout=lg,stderr=subprocess.STDOUT)
    os.makedirs(f'{A}/out/c_{n}_shots',exist_ok=True)
    for s in shots: subprocess.run(['ffmpeg','-v','error','-y','-ss',str(s['t']),'-i',f'{A}/out/c_{n}.mp4','-frames:v','1',f"{A}/out/c_{n}_shots/{s['label']}.png"])
    st,to,w,out=parse(f'c_{n}')
    r=R(n,st,to,LIB,out,w); r.dump=dict(DUMP); r.mods=dict(MODS)
    for x in w[:3]: print('  !',n,x,flush=True)
    if n in C.CHECKS:
        try: C.CHECKS[n](r)
        except Exception as e: r.check('check crashed',False,type(e).__name__+' '+str(e)[:160])
    return r
tot=[0,0]; names=sys.argv[2:] or list(C.CASES)
for n in names:
    r=run(n); tot[0]+=r.ok; tot[1]+=r.bad
    with open(H2+'/results/ops.txt','a') as f: f.write('\n'.join(r.lines)+'\n')
print('TOTAL pass',tot[0],'fail',tot[1])
