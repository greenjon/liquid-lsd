#!/usr/bin/env python3
"""fixtures.py <main library> <out libbase>: a copy of the library plus tt_* test items. Reads the source library only."""
import glob,json,os,shutil,sys
src,L=sys.argv[1:3]
shutil.rmtree(L,ignore_errors=True); shutil.copytree(src,L,symlinks=True,ignore=shutil.ignore_patterns('last_session.json','*recent.json'))
def jw(p,d): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(d,open(p,'w'),indent=2)
base=json.load(open(sorted(glob.glob(f'{L}/presets/web/*.lsd'))[0]))
mand=next((json.load(open(f)) for f in glob.glob(f'{L}/presets/**/*.lsd',recursive=True) if json.load(open(f)).get('sourceId','')=='mandala' or 'mandala' in os.path.basename(f)),base)
for n,d in(('tt_alpha','presets'),('tt_beta','presets'),('tt_gamma','presets'),('tt_inside','presets/tt_folder')):
    x=dict(mand); x['name']=n; x['tags']=[]; jw(f'{L}/{d}/{n}.lsd',x)
c=json.load(open(sorted(glob.glob(f'{L}/fx_chains/*.lsdfxchain'))[0])); c['name']='tt_chain'; c['tags']=[]; jw(f'{L}/fx_chains/tt_chain.lsdfxchain',c)
s=next(x for x in c['slots'] if x and x.get('filterId')); jw(f'{L}/fx/tt_fx.lsdfx',dict(version=1,name='tt_fx',tags=[],slot=s))
t=json.load(open(sorted(glob.glob(f'{L}/transitions/*.lsdtrans'))[0])); t['name']='tt_trans'; jw(f'{L}/transitions/tt_trans.lsdtrans',t)
open(f'{L}/playlists/ttpl.lsdplay','w').write('tt_alpha.lsd\ntt_beta.lsd\ntt_gamma.lsd\n')
open(f'{L}/playlists/ttmiss.lsdplay','w').write('tt_alpha.lsd\ntt_gone.lsd\n')
