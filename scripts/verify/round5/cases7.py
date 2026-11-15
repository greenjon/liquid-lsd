"""Round 5: macros, Metaknobs, learn and locks (checklist sections 7 and 8). CASES=cases7 python3 round5/ops5.py <work dir> [case...]"""
import re,os
from dsl import B
CASES={};CHECKS={};SETUP={}
def case(f): CASES[f.__name__]=f; return f
def chk(name):
    def d(f): CHECKS[name]=f; return f
    return d
P='Deck A/Mandala/L1'
def base(src='mandala',bloom=False):
    b=B(); b.st('load_source',deck='A',target=src)
    if bloom: b.st('fx_filter',deck='A',slot=0,target='bloom')
    b.t+=.8; return b
def par(b,l,path=P,bank='deckA'): b.t+=.2; b.st('audit_param',target=l,param=path,deck=bank); b.t+=.05; return b
def keys(b,l): b.t+=.1; b.st('audit_keys',target=l); b.t+=.05; return b
@case
def disc_value():
    b=base(); b.st('macro_bind',deck='deckA',unit='0',param=P); b.st('macro_set',deck='deckA',unit='0',value=.3); b.t+=.3
    b.st('open_params_pair',target='A'); b.t+=.8; par(b,'a'); b.shot('pair'); keys(b,'pair')
    b.click('cv_Deck A/Mandala/L1_lfo'); b.t+=.5; b.shot('lfo'); keys(b,'lfo')
    return b
# ---------------- section 8
@case
def tw_s8_midi_suspended():
    b=base(); b.st('midi_map',param=P,unit='101'); b.st('macro_bind',deck='deckA',unit='0',param=P); b.st('macro_set',deck='deckA',unit='0',value=.3); b.t+=.4; par(b,'a')
    for v in (127,0,127): b.st('midi',target=f'0:101:{v}'); b.t+=.15
    b.t+=.3; par(b,'b'); b.st('preferences',target='MIDI_CONTROLLER'); b.t+=.8; b.shot('prefs'); keys(b,'prefs'); b.st('preferences_close'); b.t+=.4
    b.st('macro_unbind',deck='deckA',unit='0'); b.t+=.3
    for v in (127,0,127): b.st('midi',target=f'0:101:{v}'); b.t+=.15
    b.t+=.3; par(b,'c'); return b
@chk('tw_s8_midi_suspended')
def _(r):
    a,b_,c=(float(r.param[x]['base']) for x in 'abc')
    r.check('a MIDI mapping on a macro-driven parameter is suspended',abs(b_-a)<1e-3,(a,b_))
    r.check('its reason names the knob','driven' in r.param['b'].get('suspend',''),r.param['b'].get('suspend'))
    r.check('it resumes when the binding goes',abs(c-1)<1e-3 and r.param['c'].get('suspend')=='null',(c,r.param['c'].get('suspend')))
LFO='cv_Deck A/Mandala/L1_lfo'
@case
def s8_range_lock():
    b=base(); b.st('macro_bind',deck='deckA',unit='1',param=P,target='mod,1,lfoMin'); b.st('macro_set',deck='deckA',unit='1',value=.25); b.t+=.4
    b.st('open_params_pair',target='A'); b.t+=.8; b.click(LFO); b.t+=.5; par(b,'a'); b.shot('lfo')
    b.go(x=713,y=485); b.st('drag',x=640,y=485,durationSec=.4); b.t+=.8; par(b,'b'); b.shot('dragged')
    b.click(x=502,y=468); b.t+=.4; b.shot('label'); keys(b,'label'); b.key('escape',.3)
    b.click(x=516,y=485); b.t+=.3; b.typ('7'); b.key('enter',.3); par(b,'c'); b.shot('typed')
    return b
MIN=lambda r,l: r.param[l]['_raw'].split(' mods=')[1].split(';')[1].split(':min=')[1].split(':')[0]
@chk('s8_range_lock')
def _(r):
    r.check('dragging a bound LFO Min handle leaves it to the knob',MIN(r,'a')==MIN(r,'b'),(MIN(r,'a'),MIN(r,'b')))
    r.check('typing into a bound Min box leaves it to the knob',MIN(r,'a')==MIN(r,'c'),(MIN(r,'a'),MIN(r,'c')))
@case
def s8_initial_range_moves_knob():
    b=base(); b.st('macro_bind',deck='deckA',unit='0',param=P); b.st('macro_set',deck='deckA',unit='0',value=.3); b.t+=.4
    b.st('open_params_pair',target='A'); b.t+=.8; b.click('cv_Deck A/Mandala/L1_label'); b.t+=.4; par(b,'a')
    b.go(x=627,y=526); b.st('drag',x=760,y=526,durationSec=.4); b.t+=.8; par(b,'b'); b.shot('dragged'); return b
K0=lambda r,l: float(r.param[l]['knobs'].split(',')[0])
@chk('s8_initial_range_moves_knob')
def _(r):
    r.check('dragging Initial Range on a knob-driven value moves the knob',K0(r,'b')>K0(r,'a')+.05,(K0(r,'a'),K0(r,'b')))
    r.check('and the value follows the knob',abs(float(r.param['b']['base'])-K0(r,'b'))<.02,(r.param['b']['base'],K0(r,'b')))
P2='Deck A/Mandala/L2'
@case
def s8_typed_knob_curve():
    b=base(); b.st('macro_unbind',deck='deckA',unit='0'); b.st('macro_bind',deck='deckA',unit='0',param=P2,target='EXPONENTIAL,1,FIRST_HALF'); b.t+=.3
    b.st('macro_set',deck='deckA',unit='0',value=.2); b.t+=.4; par(b,'ref',P2)
    b.st('macro_set',deck='deckA',unit='0',value=.8); b.t+=.4; par(b,'other',P2)
    b.go(x=453,y=57); b.st('click',dt=.03,durationSec=.03); b.st('click',dt=.1,durationSec=.03); b.t+=.4; b.shot('edit'); keys(b,'edit')
    b.key('ctrl+a',.1); b.typ('20'); b.key('enter',.4); par(b,'typed',P2); b.shot('typed'); b.t+=.5; par(b,'typed2',P2); return b
@case
def s8_typed_knob_curve_b():
    b=base(); b.st('macro_unbind',deck='deckA',unit='0'); b.st('macro_bind',deck='deckA',unit='0',param=P2,target='EXPONENTIAL,1,FIRST_HALF'); b.t+=.3
    for v in (.447,.3,.1,.45,.6): b.st('macro_set',deck='deckA',unit='0',value=v); b.t+=.4; par(b,f'k{v}',P2)
    return b
@chk('s8_typed_knob_curve')
def _(r):
    t=r.param['typed']; t2=r.param['typed2']
    r.check('typing 20 into a knob with curve, invert and zone sets the value to 20%',abs(float(t2['base'])-.2)<.01,(t['base'],t2['base'],t2['knobs']))
@case
def tw_s8_midi_control():
    b=base(); b.st('midi_map',param='Deck A/Mandala/L3',unit='100'); b.t+=.3; par(b,'a','Deck A/Mandala/L3')
    for v in (127,0,127): b.st('midi',target=f'0:100:{v}'); b.t+=.2
    b.t+=.3; par(b,'b','Deck A/Mandala/L3'); b.st('preferences',target='MIDI_CONTROLLER'); b.t+=.8; b.shot('prefs'); return b
@case
def s8_delete_modulator():
    b=base(); b.st('macro_bind',deck='deckA',unit='1',param=P,target='mod,0,depth'); b.st('macro_set',deck='deckA',unit='1',value=.6); b.t+=.4; par(b,'a')
    b.st('open_params_pair',target='A'); b.t+=.8; b.click(LFO); b.t+=.5; b.click(x=811,y=365); b.t+=.6; b.shot('deleted'); par(b,'b')
    b.st('macro_set',deck='deckA',unit='1',value=.1); b.t+=.4; par(b,'c'); return b
@chk('s8_delete_modulator')
def _(r):
    m=lambda l: r.param[l]['_raw'].split(' mods=')[1].split(';')
    r.check('deleting another modulator removes it',len(m('b'))==len(m('a'))-1,(len(m('a')),len(m('b'))))
    d=lambda l: m(l)[0].split('depth=')[1]
    r.check('the binding still drives the first modulator',m('c')[0].split(':')[0]==m('a')[0].split(':')[0] and d('c')!=d('b'),(m('b')[0],m('c')[0]))
# ---------------- section 7
def fxtab(b):
    b.st('open_params_pair',target='A'); b.t+=.8; b.click('btn_FX'); b.t+=.4; return b
def dump(b,l): b.t+=.2; b.st('audit_dump',target=l); b.t+=.05; return b
TG=lambda r,l:re.search(r'A\.fx0=[^|]*targets=\[([^\]]*)\]',r.dump[l])[1]
META=lambda r,l,i=0: float(re.search(rf'A\.fx{i}=[^|]*?meta=([\d.]+)',r.dump[l])[1])
@case
def s7_metarows():
    b=fxtab(base(bloom=True)); b.click(x=77,y=525); b.t+=.4; b.shot('fx'); keys(b,'fx'); return b
@case
def s7_row_knob_moves_meta():
    b=base(bloom=True); dump(b,'a'); b.go(x=717,y=130); b.st('drag',x=717,y=60,durationSec=.4); b.t+=.8; dump(b,'b'); b.shot('row'); return b
@chk('s7_row_knob_moves_meta')
def _(r): r.check('a linked slot\'s row knob moves its Metaknob',abs(META(r,'b',2)-META(r,'a',2))>.05,(r.dump['a'][:200],r.dump['b'][:200]))
def setup_missing(lib):
    import json
    c=json.load(open(f'{lib}/fx_chains/tt_chain.lsdfxchain')); s=c['slots'][0]; s['metaBindings']=[{'targetParamName':'no_such_param','minVal':0,'maxVal':1}]
    json.dump(c,open(f'{lib}/fx_chains/zz_missing.lsdfxchain','w'))
@case
def s7_missing_target():
    b=base(); b.st('fx_chain',deck='A',target='library/fx_chains/zz_missing.lsdfxchain'); b.t+=.8; dump(b,'a'); b.st('meta_knob',deck='A',slot=0,value=.9); b.t+=.4; dump(b,'b')
    b=fxtab(b); b.click(x=77,y=525); b.t+=.4; b.shot('editor'); return b
SETUP['s7_missing_target']=setup_missing
@chk('s7_missing_target')
def _(r): r.check('a chain with a missing target loads and keeps it',r.dump.get('a') is not None and 'no_such_param' in TG(r,'a'),r.dump.get('a','')[:200])
@case
def s7_save_reload():
    b=base(); b.st('fx_chain',deck='A',target='library/fx_chains/tt_chain.lsdfxchain'); b.t+=.8
    b=fxtab(b); b.click(x=77,y=525); b.t+=.3; b.click('b:Add Target'); b.t+=.3; b.shot('addmenu'); keys(b,'addmenu'); b.key('escape',.3)
    b.click(x=154,y=561); b.t+=.3; b.click(x=126,y=561); b.t+=.4; b.shot('shape'); keys(b,'shape'); b.key('escape',.3); dump(b,'a')
    b.st('close_focus'); b.t+=.3; b.click('fxchain_save_deckA_fx'); b.t+=.8; b.shot('saved'); keys(b,'saved')
    b.st('fx_chain',deck='A',target='library/fx_chains/fx_chain.lsdfxchain'); b.t+=.8; dump(b,'other')
    b.st('fx_chain',deck='A',target='library/fx_chains/tt_chain.lsdfxchain'); b.t+=.8; dump(b,'b')
    return b
@chk('s7_save_reload')
def _(r):
    r.check('a saved chain reloads with the same Metaknob targets, zones and invert',TG(r,'a')==TG(r,'b') and TG(r,'other')!=TG(r,'a'),(TG(r,'a'),TG(r,'b')))
@case
def s7_autobind():
    b=base()
    for i,f in enumerate(['bloom','kaleidoscope','pixelate','rgb_split','wave_displace','halftone','neon_edge','film_grain','mirror_sphere','directional_blur']):
        b.st('fx_filter',deck='A',slot=0,target=f); b.t+=.6; dump(b,f'f{i}')
    return b
@chk('s7_autobind')
def _(r):
    empty=[r.dump[k].split('A.fx0=')[1][:30] for k in sorted(r.dump) if 'A.fx0=' in r.dump[k] and TG(r,k).strip()=='']
    r.check('every loaded filter gets at least one Metaknob target',not empty and len(r.dump)==10,(len(r.dump),empty))
# ---------------- batch 2
@case
def s8_bound_labels():
    b=base(); b.st('macro_bind',deck='deckA',unit='1',param=P,target='mod,1,lfoMin'); b.st('macro_bind',deck='deckA',unit='2',param=P,target='mod,1,subdivision'); b.t+=.4
    b.st('open_params_pair',target='A'); b.t+=.8; b.click(LFO); b.t+=.6; b.shot('lfo'); keys(b,'k0')
    b.click(x=505,y=642); b.t+=.5; b.shot('min'); keys(b,'kmin'); b.key('escape',.4)
    b.click(x=519,y=707); b.t+=.5; b.shot('div'); keys(b,'kdiv'); b.key('escape',.4)
    return b
FXP='Deck A/FX/FX1/threshold'
@case
def s8_fxbank_readonly():
    b=base(bloom=True); b.st('macro_bind',deck='deckA_fx',unit='1',param=FXP); b.st('macro_set',deck='deckA_fx',unit='1',value=.7); b.t+=.4; par(b,'a',FXP,'deckA_fx')
    b=fxtab(b); b.click('cv_Deck A/FX/FX1/threshold_label'); b.t+=.5; b.shot('props'); keys(b,'props'); return b
@case
def s8_macros_tab():
    b=base(); b.click('btn_Macros'); b.t+=.6; b.shot('tab'); keys(b,'tab'); return b
@case
def s8_bank_import():
    b=base(); b.st('load_source',deck='B',target='mandala'); b.t+=.6
    b.st('macro_bind',deck='deckA',unit='3',param='Mixer/crossfade'); b.t+=.2
    b.click('btn_Macros'); b.t+=.5; b.click('b:Save bank from...'); b.t+=.4; b.click('m:Deck A'); b.t+=.5; b.shot('name'); keys(b,'name')
    b.typ('ttbank'); b.key('enter',.6); b.key('escape',.3); b.rclick(x=30,y=443); b.t+=.4; b.shot('rmenu'); keys(b,'rmenu'); b.click('m:Deck B'); b.t+=.6; b.shot('applied'); par(b,'b','Deck B/Mandala/Lobes','deckB')
    return b
@chk('s8_bank_import')
def _(r):
    bd=r.param['b']['_raw'].split(' binds=')[1].split(' mods=')[0]
    r.check('Apply to Deck B remaps deck paths','Deck B/Mandala/Lobes' in bd and 'Deck A/' not in bd,bd[:300])
    r.check('Mixer paths stay','Mixer/crossfade' in bd,bd[:300])
