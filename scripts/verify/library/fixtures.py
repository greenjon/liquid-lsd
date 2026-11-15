import json,glob,os,shutil,sys
L='/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/wt2/library'
def jw(p,d):
    os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(d,open(p,'w'),indent=2)
for n in ('tt_alpha','tt_beta','tt_gamma'):
    d=json.load(open(f'{L}/presets/mandala-7.lsd')); d['name']=n; d['keepme']=None; d.pop('keepme'); jw(f'{L}/presets/{n}.lsd',d)
c=json.load(open(sorted(glob.glob(f'{L}/fx_chains/*.lsdfxchain'))[0])); c['name']='tt_chain'; jw(f'{L}/fx_chains/tt_chain.lsdfxchain',c)
slot=next(s for s in c['slots'] if s); slot=dict(slot); slot['name']='tt_fx'; jw(f'{L}/fx/tt_fx.lsdfx',slot)
t=json.load(open(sorted(glob.glob(f'{L}/transitions/*.lsdtrans'))[0])); t['name']='tt_trans'; jw(f'{L}/transitions/tt_trans.lsdtrans',t)
os.makedirs(f'{L}/presets/tt_folder',exist_ok=True)
