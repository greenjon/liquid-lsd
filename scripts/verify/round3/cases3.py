"""The simulated Twister: its messages go through MidiEngine's real queue and the real profile; what the app sends back is logged. Stock (CC) profile unless the case starts xt_."""
from dsl import B
CASES={};CHECKS={};SETUP={}
def case(f): CASES[f.__name__]=f; return f
def chk(name):
    def d(f): CHECKS[name]=f; return f
    return d
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
    def chainlink(s,**k): return s.btn(9,**k)
    def bankstep(s,follow=True):
        s.btn(11); 
        if follow: s.bank=(s.bank+1)%3
        s.t+=.3; return s
    def wake(s):
        """A first message registers the device; the scan then opens its output."""
        s.t+=1.2; s.turn(0,1); s.turn(0,-1); s.t+=1.2; return s
def Tn(src='kifs',dst='voronoi_cells'):
    b=T(); b.st('load_source',deck='A',target=src); b.st('load_source',deck='B',target=dst); b.t+=.8; b.wake(); return b
@case
def tw_turn():
    b=Tn(); b.state('a'); b.turn(0,12); b.state('b'); b.turn(0,-12); b.state('c'); b.turn(1,8); b.state('d'); b.tap(1); b.state('e'); return b
@chk('tw_turn')
def _(r):
    a,b,c,d,e=[r.knobs(x) for x in 'abcde']
    r.check('a knob turn moves its ring value',b[0]['v']!=a[0]['v'],(a[0],b[0]))
    r.check('turning back comes back',abs(c[0]['v']-a[0]['v'])<.02,(a[0],c[0]))
    r.check('the ring is sent on ch1, CC = knob',any(x[0]==0 and x[1]==0 for x in r.cc('b')),r.cc('b')[:8])
    r.check('tap resets to its default',abs(e[1]['v']-a[1]['v'])<.02 and d[1]['v']!=a[1]['v'],(a[1],d[1],e[1]))
@case
def tw_banks():
    b=Tn(); b.state('a'); b.bankstep(); b.state('b'); b.bankstep(); b.state('c'); b.bankstep(); b.state('d'); b.shift(1); b.btn(11); b.shift(0); b.bank=2; b.t+=.3; b.state('e'); return b
@chk('tw_banks')
def _(r):
    pg=[r.page(x) for x in 'abcde']
    r.check('banks walk ab > bgpv > mixer and wrap, shift goes back',pg==['ab','bgpv','mixer','ab','mixer'],pg)
    for lab,n in (('b',1),('c',2),('d',0)): r.check(f'the device is told to change to bank {n}: B3 0{n} 7F',(3,n,127) in r.cc(lab),r.cc(lab)[:6])
    a,b=r.knobs('a'),r.knobs('b')
    r.check('lights change with the bank',[x and x['rgb'] for x in a]!=[x and x['rgb'] for x in b])
@case
def tw_mixer():
    b=Tn(); b.bankstep(); b.bankstep(); b.st('crossfade',value=-1); b.t+=.3; b.state('a'); b.turn(0,20); b.state('b'); b.turn(8,-10); b.state('c'); return b
@chk('tw_mixer')
def _(r):
    xa,xb=float(r.f('a','M','xf')),float(r.f('b','M','xf'))
    r.check('MIXER bank: knob 1 moves the crossfader',xb>xa,(xa,xb))
    r.check('MIXER bank: knob 9 (Master MST) moves',r.knobs('b')[8]['v']!=r.knobs('c')[8]['v'],(r.knobs('b')[8],r.knobs('c')[8]))
@case
def tw_clock():
    b=Tn(); b.bankstep(); b.bankstep(); b.state('a'); b.t+=4; b.state('b'); return b
@chk('tw_clock')
def _(r):
    led=[x for x in r.cc('b') if x[0]==1 and x[1]==12+32]+[x for x in r.cc('b') if x[0]==1 and x[1]==12]
    r.check('knob 13: the LED pulses over 4 s at 128 BPM',len(set(x[2] for x in led))>=2,r.cc('b')[:20])
    r.check('knob 13: the ring shows the tempo',r.knobs('a')[12] and r.knobs('a')[12]['v']>0,r.knobs('a')[12])
@case
def tw_pair_walk():
    b=Tn(); b.st('focus_pair',target='A'); b.t+=.4; b.state('a')
    for l in 'bcdef': b.bankstep(follow=False); b.state(l)
    return b
@chk('tw_pair_walk')
def _(r):
    p=[r.ui(x,'pair') for x in 'abcdef']
    r.check('bank press in the pair view walks A, B, BG, PV, Master, A',p[:5]==['A','B','BG','PV','MASTER'] or p==['A','B','BG','PV','M','A'],p)
@case
def tw_chain():
    b=Tn(); b.st('fx_filter',deck='A',slot=1,target='kaleidoscope'); b.t+=.3; b.state('a'); b.stap(0); b.t+=.3; b.state('b')
    b.turn(0,3); b.turn(5,3); b.turn(13,3); b.state('c')
    b.rb(); b.state('d'); b.turn(15,3); b.state('e'); b.tap(13); b.t+=.6; b.state('f'); b.turn(5,2); b.state('g')
    b.rb(); b.state('h'); b.bankstep(follow=False); b.state('i'); b.bankstep(follow=False); b.lt(); b.state('j'); return b
@chk('tw_chain')
def _(r):
    r.check('shift + tap a SRC knob opens Chain view',r.ui('b','chain')=='PLAY' and r.ui('b','pair')=='A',r.st.get('b',{}).get('ui'))
    a,b,c=r.knobs('b'),r.knobs('b'),r.knobs('c')
    r.check('all 16 knobs are lit rows',all(x is not None for x in r.knobs('b')),r.knobs('b'))
    r.check('right-bottom turns Load on',r.ui('d','chain')=='BROWSE',r.st.get('d',{}).get('ui'))
    d=r.knobs('e')
    r.check('Load: after turning knob 16, knobs 13-15 glow in the slot colours',d[12]['lit'] and d[13]['lit'] and d[14]['lit'],d[12:])
    f0,f1=r.f('e','A','fx'),r.f('f','A','fx')
    r.check('tap knob 14 loads the highlighted effect into slot 2',f0!=f1 and f0.split(',')[0]==f1.split(',')[0],(f0,f1))
    r.check('knobs 5-8 then play it',r.knobs('f')[5]['v']!=r.knobs('g')[5]['v'],(r.knobs('f')[5],r.knobs('g')[5]))
    r.check('right-bottom: back to the SRC row',r.ui('h','chain')=='PLAY',r.st.get('h',{}).get('ui'))
    r.check('bank button: Deck B Chain view',r.ui('i','pair')=='B' and r.ui('i','chain')=='PLAY',r.st.get('i',{}).get('ui'))
    r.check('left-top leaves',r.ui('j','chain')=='null',r.st.get('j',{}).get('ui'))
@case
def tw_bound():
    b=Tn(); b.click('perf_gen_badge_A'); b.t+=.3; b.state('a'); b.turn(15,5); b.state('b'); b.tap(15); b.t+=.7; b.state('c'); b.search('tt_'); b.turn(15,2); b.stap(15); b.state('d'); b.turn(1,5); b.state('e'); b.stap(9); b.t+=.4; b.state('f'); b.rm(); b.state('g'); return b
@chk('tw_bound')
def _(r):
    r.check('badge click binds in place',(r.ui('a','dock') or 'null')!='null',r.st.get('a',{}).get('ui'))
    r.check('knob 16 tap applies the highlighted source',r.f('a','A','src')!=r.f('c','A','src'),(r.f('a','A','src'),r.f('c','A','src')))
    r.check('knob 16 shift + tap queues',len(r.q('d')[0] or [])==1,r.q('d'))
    r.check('knobs 1-8 stay live',r.knobs('d')[1]['v']!=r.knobs('e')[1]['v'],(r.knobs('d')[1],r.knobs('e')[1]))
    r.check('shift + tap a send knob moves the binding to that deck',(r.ui('f','pair') or '')=='B' or 'deckB' in (r.ui('f','dock') or ''),r.st.get('f',{}).get('ui'))
@case
def tw_library():
    b=Tn(); b.rm(); b.t+=.5; b.state('a'); b.turn(15,6); b.state('b'); b.tap(8); b.t+=.8; b.state('c'); b.turn(0,6); b.state('d'); b.tap(11); b.t+=.8; b.state('e'); b.turn(15,2); b.tap(12); b.t+=.6; b.state('f'); b.rm(); b.state('g'); b.lt(); b.t+=.3; b.state('h'); return b
@chk('tw_library')
def _(r):
    r.check('right-middle opens the Library',r.libm('a')=='FULL',r.libm('a'))
    r.check('send knob 9 loads the cursor item on Deck A',r.f('b','A','src')!=r.f('c','A','src') or r.f('b','A','file')!=r.f('c','A','file'),(r.f('b','A','src'),r.f('c','A','src')))
    r.check('in Library FULL a send only loads: the view stays',r.libm('c')=='FULL',r.libm('c'))
    r.check('after a send, knobs 1-8 play that deck',r.ui('c','live')=='A',r.ui('c','live'))
    r.check('knob 1 moved something',r.knobs('c')[0]['v']!=r.knobs('d')[0]['v'],(r.knobs('c')[0],r.knobs('d')[0]))
    r.check('send knob 12 loads on PV and the live pair follows',r.ui('e','live')=='PV',r.ui('e','live'))
    r.check('left-top leaves the Library',r.libm('h')!='FULL',r.libm('h'))
@case
def tw_autopreview():
    b=Tn(); b.rm(); b.t+=.5; b.state('a'); b.stap(11); b.state('b'); b.turn(15,4); b.t+=1.5; b.state('c'); b.turn(15,1,w=.03); b.state('d'); b.lt(); b.t+=.4; b.state('e'); return b
@chk('tw_autopreview')
def _(r):
    r.check('auto-preview: PV loads after the cursor stops',r.f('a','PV','src')!=r.f('c','PV','src'),(r.f('a','PV','src'),r.f('c','PV','src')))
@case
def tw_queues():
    b=Tn(); b.rm(); b.t+=.5
    b.search('tt_'); b.click('browser_row_src_tt_alpha'); b.key('q'); b.click('browser_row_src_tt_beta'); b.key('q'); b.click('browser_row_src_tt_gamma'); b.key('q'); b.state('a')
    b.turn(13,1); b.state('b'); b.turn(13,1); b.state('c'); b.press(13); b.turn(13,1); b.rel(13); b.t+=.3; b.state('d'); b.stap(13); b.t+=.3; b.state('e'); return b
@chk('tw_queues')
def _(r):
    r.check('three items queued',r.q('a')[0]==['tt_alpha','tt_beta','tt_gamma'],r.q('a'))
    r.check('knob 14 moves the A/B highlight',r.q('b')[2]!=r.q('c')[2],(r.q('b'),r.q('c')))
    r.check('hold + turn carries an item and does not play it',r.q('d')[0]!=r.q('c')[0] and sorted(r.q('d')[0])==sorted(r.q('c')[0]) and r.f('c','A','file')==r.f('d','A','file'),(r.q('c'),r.q('d')))
    r.check('shift + tap removes, with a toast',len(r.q('e')[0])==2 and any('emov' in t for t in r.to),(r.q('e'),r.to))
@case
def tw_linkall():
    b=Tn(); b.st('fx_filter',deck='A',slot=0,target='bloom'); b.st('fx_filter',deck='A',slot=1,target='rgb_split'); b.t+=.4; b.turn(4,1); b.turn(4,-1); b.state('a'); b.chainlink(); b.state('b'); b.chainlink(); b.state('c'); return b
@chk('tw_linkall')
def _(r):
    a,b,c=r.knobs('a')[4],r.knobs('b')[4],r.knobs('c')[4]
    r.check('chain link changes the row, and again restores it',(r.st['a']['knobs']==r.st['c']['knobs']) or a==c,(a,b,c))
@case
def tw_layout():
    b=Tn(); b.state('a'); b.st('set_page',target='mixer'); b.t+=.4; b.bank=2; b.state('b'); b.turn(0,6); b.state('c')
    b.st('set_page',target='ab'); b.t+=.4; b.bank=0; b.state('d'); b.turn(1,4); b.state('e')
    b.st('focus_pair',target='B'); b.t+=.4; b.state('f'); b.turn(1,4); b.state('g'); b.st('close_focus'); b.t+=.4; b.state('h'); b.turn(1,4); b.state('i'); return b
@chk('tw_layout')
def _(r):
    r.check('a page change on screen moves the device to its bank',(3,2,127) in r.cc('b'),r.cc('b')[:6])
    r.check('after a page change, knob 1 drives the MIXER (crossfader)',float(r.f('b','M','xf'))!=float(r.f('c','M','xf')),(r.f('b','M','xf'),r.f('c','M','xf')))
    r.check('back on A/B, knob 2 drives Deck A',r.knobs('d')[1]['v']!=r.knobs('e')[1]['v'],(r.knobs('d')[1],r.knobs('e')[1]))
    r.check('pair view of B, knob 2 drives it',r.knobs('f')[1]['v']!=r.knobs('g')[1]['v'],(r.knobs('f')[1],r.knobs('g')[1]))
    r.check('after leaving the pair, knob 2 drives Deck A again',r.knobs('h')[1]['v']!=r.knobs('i')[1]['v'],(r.knobs('h')[1],r.knobs('i')[1]))
@case
def tw_prompt():
    b=T(); b.st('load_preset',deck='A',target='library/presets/tt_alpha.lsd'); b.t+=.9; b.wake(); b.turn(1,5); b.state('a'); b.rm(); b.t+=.5; b.turn(15,3); b.tap(8); b.t+=.5; b.state('b'); b.lt(); b.t+=.5; b.state('c'); b.tap(8); b.t+=.5; b.rb(); b.t+=.8; b.state('d'); return b
@chk('tw_prompt')
def _(r):
    r.check('a send onto an unsaved deck waits for the prompt',r.f('b','A','file')=='tt_alpha.lsd',r.f('b','A','file'))
    r.check('left-top cancels',r.f('c','A','file')=='tt_alpha.lsd',r.f('c','A','file'))
    r.check('right-bottom discards and loads',r.f('d','A','file')!='tt_alpha.lsd' or r.f('d','A','src')!=r.f('c','A','src'),(r.f('c','A','file'),r.f('d','A','file')))
@case
def xt_native():
    b=Tn(); b.state('a'); b.turn(0,4); b.state('b'); b.bankstep(); b.state('c'); return b
@chk('xt_native')
def _(r):
    r.check('native mode entered with a SysEx',len(r.sysex('a'))>0,r.out.get('a',[])[:5])
    ms=[x['meter'] for x in r.knobs('a') if x]
    r.check('the rings have a style per parameter kind',len(set(ms))>=1,ms)
    r.check('knob turn still reaches the ring',len(r.cc('b'))>0,r.out.get('b',[])[:5])
    r.check('leaves native mode at exit',any(x.startswith('sysex') for x in r.out.get('_end',[])) ,r.out.get('_end',[])[-5:])


@case
def tw_clock_tap():
    b=Tn(); b.bankstep(); b.bankstep(); b.state('a')
    for _ in range(5): b.tap(12,.29)
    b.state('b'); return b
@chk('tw_clock_tap')
def _(r):
    a,b=float(r.st['a']['bpm']),float(r.st['b']['bpm'])
    r.check('knob 13 press taps the tempo',a!=b and 100<b<170,(a,b))
@case
def tw_shift_tempo():
    b=Tn(); b.state('a'); b.shift(1)
    for _ in range(5): b.cc(3,9,127,.05); b.cc(3,9,0,.25)
    b.shift(0); b.state('b'); return b
@chk('tw_shift_tempo')
def _(r): r.check('shift + left-middle taps the tempo',float(r.st['a']['bpm'])!=float(r.st['b']['bpm']),(r.st['a']['bpm'],r.st['b']['bpm']))
@case
def tw_pair_switch():
    b=Tn(); b.st('focus_pair',target='A'); b.t+=.4; b.state('a'); b.stap(9); b.t+=.4; b.state('b'); return b
@chk('tw_pair_switch')
def _(r):
    r.check('in the pair view, shift + tap a send knob switches pair without sending',r.ui('b','pair')=='B' and r.f('a','B','src')==r.f('b','B','src'),(r.ui('b','pair'),r.f('a','B','src'),r.f('b','B','src')))
@case
def tw_fx_tap():
    b=Tn(); b.st('fx_clear',deck='A'); b.st('crossfade',value=-1); b.t+=.4; b.rm(); b.t+=.5; b.rm(); b.t+=.4; b.state('a'); b.turn(15,3); b.tap(15); b.t+=.7; b.state('b'); b.turn(15,1); b.tap(15); b.t+=.7; b.state('c'); b.search('tt_fx'); b.turn(15,1); b.stap(15); b.t+=.4; b.state('d'); return b
@chk('tw_fx_tap')
def _(r):
    fa,fb,fc=r.f('a','A','fx'),r.f('b','A','fx'),r.f('c','A','fx')
    r.check('FX tap with nothing bound fills the first vacant slot of the live deck',fa=='[-, -, -]' and fb.split(', ')[0]!='[-' and fb.endswith('-, -]'),(fa,fb))
    r.check('the next tap fills the second',fc.split(', ')[1]!='-' and fc.endswith('-]'),fc)
    r.check('shift + tap queues it',len(r.q('d','FX_AB')[0] or [])==1,r.q('d','FX_AB'))
@case
def tw_after_knob14():
    b=Tn(); b.rm(); b.t+=.5; b.search('tt_'); b.click('browser_row_src_tt_alpha'); b.key('q'); b.click('browser_row_src_tt_beta'); b.key('q'); b.state('a')
    b.turn(13,-3); b.state('a2'); b.tap(13); b.t+=.8; b.state('a3'); b.turn(13,1); b.state('a4'); b.turn(15,1); b.state('a5'); b.stap(15); b.state('b'); b.turn(15,1); b.stap(15); b.state('c'); return b
@chk('tw_after_knob14')
def _(r):
    qa,qb,qc=r.q('a')[0],r.q('b')[0],r.q('c')[0]
    r.check('after turning knob 14, shift + tap knob 16 inserts after its cursor, not at the end',qb==['tt_alpha','tt_gamma','tt_beta'],(qa,r.q('a2'),r.q('a3'),r.q('a4'),r.q('a5'),qb))
    r.check('a second one goes after it',qc==['tt_alpha','tt_gamma','tt_inside','tt_beta'],(qb,qc))
@case
def tw_pickup():
    b=Tn(); b.st('super_mode',target='PICKUP'); b.st('fx_chain_enable',deck='A',value=1); b.st('fx_filter',deck='A',slot=0,target='bloom'); b.st('fx_link',deck='A',slot=0,value=0); b.t+=.3; b.st('fx_link',deck='A',slot=0,value=1); b.t+=.3; b.state('a'); b.t+=2; b.state('b'); return b
@chk('tw_pickup')
def _(r):
    leds=[x for x in r.cc('b') if x[0]==1 and x[1]%16 in (4,5)]
    r.check('a waiting slot blinks: its LED changes repeatedly',len(set(x[2] for x in leds))>=2 and len(leds)>=4,r.cc('b')[:30])
@case
def tw_super_tap():
    b=Tn(); b.st('fx_filter',deck='A',slot=0,target='bloom'); b.t+=.3; b.turn(4,4); b.state('a'); b.tap(4); b.t+=.3; b.state('b'); b.stap(4); b.t+=.3; b.state('c'); return b
@chk('tw_super_tap')
def _(r):
    r.check('tap on the Super Knob does nothing',r.knobs('a')[4]==r.knobs('b')[4],(r.knobs('a')[4],r.knobs('b')[4]))
@case
def tw_unplug():
    b=Tn(); b.state('a'); b.st('fake_plug',value=0); b.t+=2.5; b.state('b'); b.turn(1,3); b.t+=1.5; b.state('c'); b.st('fake_plug',value=1); b.t+=11; b.turn(1,1); b.t+=1.5; b.state('d'); return b
@chk('tw_unplug')
def _(r):
    r.check('the app keeps running while unplugged',all(x in r.st for x in 'abcd'),list(r.st))
    r.check('nothing is sent to a device that left',len(r.cc('c'))==0,r.cc('c')[:6])
    r.check('plugged back, the lights are rewritten',len(r.cc('d'))>=16,len(r.cc('d')))
@case
def xt_unplug():
    b=Tn(); b.state('a'); b.st('fake_plug',value=0); b.t+=2.5; b.state('b'); b.st('fake_plug',value=1); b.t+=11; b.turn(1,1); b.t+=1.5; b.state('c'); return b
@chk('xt_unplug')
def _(r):
    r.check('a replugged device enters native mode again',len(r.sysex('c'))>0,r.out.get('c',[])[:4])
@case
def tw_edit_layout():
    b=Tn(); b.shift(1); b.lt(); b.shift(0); b.t+=.6; b.state('a'); b.turn(1,4); b.state('b'); b.shift(1); b.lt(); b.shift(0); b.t+=.6; b.state('c'); b.turn(1,4); b.state('d'); return b
@chk('tw_edit_layout')
def _(r):
    r.check('in Edit knob 2 still drives the pair on screen',r.knobs('a')[1]['v']!=r.knobs('b')[1]['v'],(r.knobs('a')[1],r.knobs('b')[1]))
    r.check('after leaving Edit, knob 2 still works',r.knobs('c')[1]['v']!=r.knobs('d')[1]['v'],(r.knobs('c')[1],r.knobs('d')[1]))

@case
def tw_insert_middle():
    b=Tn(); b.rm(); b.t+=.5; b.search('tt_'); b.click('browser_row_src_tt_alpha'); b.key('q'); b.click('browser_row_src_tt_beta'); b.key('q'); b.state('a')
    b.turn(13,-3); b.turn(15,1); b.stap(15); b.state('b'); b.turn(15,1); b.stap(15); b.state('c'); return b
@chk('tw_insert_middle')
def _(r):
    r.check('nothing playing: inserts after knob 14\'s row, even after knob 16 moved',r.q('b')[0]==['tt_alpha','tt_gamma','tt_beta'],r.q('b'))
    r.check('and the next one after that',r.q('c')[0]==['tt_alpha','tt_gamma','tt_inside','tt_beta'],r.q('c'))
