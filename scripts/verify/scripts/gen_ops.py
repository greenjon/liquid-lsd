import json,sys
out=sys.argv[1]
steps,shots=[],[];t=0.0
def st(a,dt=0.0,**k):
    global t
    t+=dt;d={"t":round(t,3),"action":a};d.update(k);steps.append(d)
def shot(l,w=0.8):
    global t
    t+=w;shots.append({"label":l,"t":round(t,3)});t+=.15
def click(k=None,**kw):
    st("move",target=k,durationSec=.15,dt=.05,**kw) if k else None
    st("click",dt=.25)
st("randomization",value=1)
for d,s in (("A","kifs"),("B","voronoi_cells"),("BG","aurora_ribbons"),("PV","metaballs")):
    st("load_source",deck=d,target=s,dt=.05)
st("move",x=1000,y=20,durationSec=.1)
shot("start",1.0)
# die, undo
click("perf_rand_A"); shot("die_rolled",1.0)
st("key",target="ctrl+z",dt=.1); shot("die_undone",1.0)
# edit on 3D source: Deck Zoom hidden; eject + empty card
st("move",x=391,y=58,durationSec=.15); st("click",dt=.25); shot("edit_kifs_rows",.8)
st("move",x=391,y=58,durationSec=.15); st("click",dt=.25); shot("gear_closed",.6)
click("perf_eject_B"); shot("ejected_B",.8)
st("move",x=391,y=206,durationSec=.15,dt=.05); st("click",dt=.25); shot("empty_deck_edit",.8)
st("close_focus",dt=.1)
st("load_source",deck="B",target="voronoi_cells",dt=.1)
# FX undo: bound slot pick then ctrl+z
click("fxcell_deckA_fx_1"); shot("fx_bound",.5)
st("move",target="browser_row_fx_kaleidoscope",durationSec=.15); st("click",dt=.25); shot("fx_picked",.8)
st("key",target="ctrl+z",dt=.1); shot("fx_undone",.8)
st("key",target="escape",dt=.1)
# chain view
st("move",target="fxchain_more_deckA_fx",durationSec=.15,dt=.2); st("click",dt=.25); st("move",x=120,y=118,durationSec=.15,dt=.3); shot("chain_menu",.3); st("click",dt=.05); shot("chain_view",1.0)
st("move",target="btnid_bay_chain",durationSec=.1) ; shot("chain_view_hover",.3)
st("key",target="escape",dt=.1); shot("chain_view_esc",.8)
# monitor drag A -> B : nine choices
st("move",x=985,y=340,durationSec=.15,dt=.1); st("drag",x=1180,y=340,durationSec=.4,dt=.2); shot("monitor_menu",.9)
st("key",target="escape",dt=.1)
# user shaders in Browse
st("move",target="browser_search",durationSec=.15,dt=.3); st("click",dt=.25); st("type",target="zz_",dt=.2); shot("browse_zz",1.2)
st("tooltips",value=1)
for off,l in ((0,"zz_row0"),(32,"zz_row1"),(64,"zz_row2")):
    st("move",x=340,y=453+off,durationSec=.15,dt=.05); shot("tip_"+l,1.3)
st("tooltips",value=0)
st("move",x=340,y=453,durationSec=.1,dt=.05)
st("click",dt=.2); shot("load_broken_click",1.5)
# tooltips
st("key",target="escape",dt=.05);
st("set_page",target="ab",dt=.1); st("tooltips",value=1,dt=.1)
TIP=["perf_rand_A","perf_eject_A","perf_save_A","perf_gen_badge_A","fxcell_deckA_fx_0","fxchain_name_deckA_fx","fxchain_on_A","btnid_perf_q_next_B" ,"btnid_chainlink_deckA_fx","browser_tab_fx"]
for k in TIP:
    st("move",target=k,durationSec=.1,dt=.05); shot("tip_"+k,1.3)
st("move",x=1100,y=150,durationSec=.1,dt=.05); shot("tip_monitor_main",1.3)
st("set_page",target="mixer",dt=.1)
for k in ("perf_trans_picker","perf_autofade","perf_fade_time","btnid_perf_clock_tap","btnid_perf_clock_trig_randAll"):
    st("move",target=k,durationSec=.1,dt=.3); shot("tip_"+k,1.3)
st("tooltips",value=0,dt=.1)
json.dump({"seed":7,"bpm":128.0,"durationSec":t+.5,"steps":steps},open(out+"/ops.json","w"),indent=0)
json.dump(shots,open(out+"/ops.shots.json","w"))
print('ops',len(shots),round(t,1))
