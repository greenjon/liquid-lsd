class B:
    def __init__(s): s.t=0.0; s.steps=[]; s.shots=[]
    def st(s,a,dt=0.0,**k):
        s.t+=dt; d={"t":round(s.t,3),"action":a}; d.update(k); s.steps.append(d); return s
    def shot(s,l,w=.7):
        s.t+=w; s.shots.append({"label":l,"t":round(s.t,3)}); s.t+=.15; return s
    def go(s,k=None,x=None,y=None,**kw):
        if k: s.st("move",target=k,durationSec=.12,dt=.08,**kw)
        else: s.st("move",x=x,y=y,durationSec=.12,dt=.08)
        s.t+=.15; return s
    def click(s,*a,**k): s.go(*a,**k); s.st("click",dt=.05); s.t+=.25; return s
    def rclick(s,*a,**k): s.go(*a,**k); s.st("rclick",dt=.05); s.t+=.25; return s
    def search(s,txt):
        s.click("browser_search"); s.st("type",target=txt,dt=.1); s.t+=len(txt)*.14+.4; return s
    def tab(s,t): s.click("browser_tab_"+t); s.t+=.3; return s
    def done(s): return s.steps,s.shots

import json,os
SX=400
def esc(b): b.st("key",target="escape",dt=.05); b.t+=.25; return b
def start(tab=None):
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8
    if tab: b.tab(tab)
    b.search("tt_"); return b
def rename_preset():
    b=start(); b.rclick(x=340,y=453); b.click(x=SX,y=528); b.shot("dialog",.5)
    b.st("type",target="tt_renamed",dt=.1); b.t+=2; b.shot("after"); return b.done()
def rename_esc():
    b=start(); b.rclick(x=340,y=453); b.click(x=SX,y=528); b.shot("dialog",.5); esc(b); b.shot("after"); return b.done()
def clone_preset():
    b=start(); b.rclick(x=340,y=453); b.click(x=SX,y=552); b.shot("dialog",.5); b.st("type",target="tt_clone",dt=.1); b.t+=2; b.shot("after"); return b.done()
def delete_preset():
    b=start(); b.rclick(x=340,y=453); b.click(x=SX,y=574); b.shot("dialog",.5); b.st("key",target="enter",dt=.05); b.t+=.6; b.shot("after"); return b.done()
def delete_esc():
    b=start(); b.rclick(x=340,y=453); b.click(x=SX,y=574); b.shot("dialog",.5); esc(b); b.shot("after"); return b.done()
def add_tag():
    b=start(); b.rclick(x=340,y=453); b.click(x=SX,y=326); b.shot("dialog",.5); b.st("type",target="ttag",dt=.1); b.t+=1.2; b.shot("after"); return b.done()
def move_menu():
    b=start(); b.rclick(x=340,y=453); b.go(x=SX,y=350); b.t+=.4; b.shot("submenu",.5); return b.done()
def drag_move():
    b=start(); b.go(x=340,y=453); b.t+=.3; b.st("drag",x=60,y=542,durationSec=.6,dt=.05); b.t+=1; b.shot("after",.8); return b.done()
def new_folder():
    b=start(); b.click(x=60,y=492); b.t+=.2; b.click("btnid_browserNewFolder"); b.shot("dialog",.5); b.st("type",target="tt_new",dt=.1); b.t+=1.2; b.shot("after"); return b.done()
def folder_menu():
    b=start(); b.rclick(x=60,y=542); b.shot("menu",.5); return b.done()
def fx_rename_chain():
    b=start("fx"); b.rclick(x=340,y=485); b.click(x=SX,y=584); b.shot("dialog",.5); b.st("type",target="tt_chain2",dt=.1); b.t+=1.5; b.shot("after"); return b.done()
def fx_rename_single():
    b=start("fx"); b.rclick(x=340,y=453); b.click(x=SX,y=540); b.shot("dialog",.5); b.st("type",target="tt_fx2",dt=.1); b.t+=1.5; b.shot("after"); return b.done()
def fx_delete_single():
    b=start("fx"); b.rclick(x=340,y=453); b.click(x=SX,y=586); b.shot("dialog",.5); b.st("key",target="enter",dt=.05); b.t+=.6; b.shot("after"); return b.done()
def trans_rename():
    b=start("trans"); b.rclick(x=340,y=453); b.click(x=SX,y=494); b.shot("dialog",.5); b.st("type",target="tt_trans2",dt=.1); b.t+=1.5; b.shot("after"); return b.done()
def trans_delete():
    b=start("trans"); b.rclick(x=340,y=453); b.click(x=SX,y=540); b.shot("dialog",.5); b.st("key",target="enter",dt=.05); b.t+=.6; b.shot("after"); return b.done()
def multi():
    b=start(); b.click(x=340,y=453); b.t+=.2; b.st("key",target="ctrl+a",dt=.05); b.t+=.3; b.shot("all"); b.rclick(x=340,y=485); b.shot("menu",.5); return b.done()
def multi_delete():
    b=start(); b.click(x=340,y=453); b.t+=.2; b.st("key",target="ctrl+a",dt=.05); b.t+=.3; b.st("key",target="backspace",dt=.05); b.t+=.3; b.shot("dialog",.5); b.st("key",target="enter",dt=.05); b.t+=.6; b.shot("after"); return b.done()
def missing():
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8; b.click(x=60,y=644); b.shot("list",.8); b.go(x=340,y=453); b.st("tooltips",value=1,dt=.05); b.t+=1.2; b.shot("tip"); return b.done()
def queue_q():
    b=start(); b.click(x=340,y=453); b.st("key",target="q",dt=.05); b.t+=.3; b.shot("flash",.1); b.click(x=340,y=485); b.st("key",target="shift+q",dt=.05); b.t+=.4; b.tab("queues"); b.shot("queues",.5); return b.done()
def queue_clear_undo():
    b=start(); b.click(x=340,y=453); b.st("key",target="q",dt=.05); b.t+=.3; b.tab("queues"); b.shot("filled",.5); b.click(x=162,y=76); b.shot("menu",.5); return b.done()
def loaded_rename_save():
    b=start(); b.st("load_preset",deck="A",target="library/presets/tt_alpha.lsd",dt=.1); b.t+=.8; b.rclick(x=340,y=453); b.click(x=SX,y=528); b.st("type",target="tt_loaded",dt=.1); b.t+=1.5; b.shot("renamed"); b.st("key",target="ctrl+s",dt=.05); b.t+=1; b.shot("saved"); return b.done()

FX=120
def move_via_menu():
    b=start(); b.rclick(x=340,y=453); b.go(x=SX,y=350); b.t+=.4; b.go(x=590,y=350); b.t+=.2; b.click(x=590,y=418); b.t+=1; b.shot("after"); return b.done()
def conflict():
    b=start(); b.go(x=340,y=485); b.t+=.3; b.st("drag",x=60,y=542,durationSec=.6,dt=.05); b.t+=1; b.shot("dialog",.8); return b.done()
def folder_rename():
    b=start(); b.rclick(x=60,y=542); b.click(x=FX,y=578); b.shot("dialog",.5); b.st("type",target="tt_folder_r",dt=.1); b.t+=2; b.shot("after"); return b.done()
def folder_delete():
    b=start(); b.rclick(x=60,y=542); b.click(x=FX,y=598); b.shot("dialog",.5); b.st("key",target="enter",dt=.05); b.t+=.8; b.shot("after"); return b.done()
def folder_drag():
    b=start(); b.go(x=60,y=542); b.t+=.3; b.st("drag",x=60,y=509,durationSec=.6,dt=.05); b.t+=1; b.shot("after",.8); return b.done()
def favorite():
    b=start(); b.rclick(x=340,y=453); b.click(x=SX,y=304); b.t+=.5; b.click(x=60,y=424); b.shot("favs",.8); return b.done()
def tags_tree():
    b=start(); b.click(x=14,y=594); b.t+=.3; b.shot("open",.6); return b.done()
def new_playlist():
    b=start(); b.click(x=60,y=610); b.t+=.2; b.click("btnid_browserNewPlaylist"); b.shot("dialog",.5); b.st("type",target="ttnew",dt=.1); b.t+=1.2; b.shot("after"); return b.done()
def playlist_folder():
    b=start(); b.click(x=60,y=610); b.t+=.2; b.click("btnid_browserNewFolder"); b.shot("dialog",.5); b.st("type",target="ttplf",dt=.1); b.t+=1.2; b.shot("after"); return b.done()
def type_search():
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8; b.click(x=340,y=453); b.st("key",target="b",dt=.1); b.t+=.5; b.shot("after"); return b.done()
def fx_folder_btn():
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8; b.tab("fx"); b.st("tooltips",value=1); b.go(x=175,y=380); b.t+=1.2; b.shot("tip"); return b.done()
def queue_full():
    b=start(); b.click(x=340,y=453); b.st("key",target="q",dt=.05); b.t+=.3; b.click(x=340,y=485); b.st("key",target="q",dt=.05); b.t+=.3
    b.st("library_mode",target="FULL",dt=.1); b.t+=.6; b.shot("full",.5); b.click(x=160,y=406); b.shot("kebab",.5); b.click(x=205,y=446); b.t+=.3; b.shot("clicked",.6); return b.done()
def queue_clear():
    b=start(); b.click(x=340,y=453); b.st("key",target="q",dt=.05); b.t+=.3; b.st("library_mode",target="FULL",dt=.1); b.t+=.6; b.click(x=160,y=406); b.click(x=205,y=470); b.t+=.3; b.shot("cleared",.6); b.st("key",target="ctrl+z",dt=.1); b.t+=.4; b.shot("undone",.6); return b.done()
def queue_drag():
    b=start(); b.st("library_mode",target="FULL",dt=.1); b.t+=.6; b.shot("start",.4); b.go(x=340,y=149); b.t+=.3; b.st("drag",x=90,y=500,durationSec=.6,dt=.05); b.t+=.8; b.shot("on_ab",.6); b.go(x=340,y=181); b.t+=.3; b.st("drag",x=264,y=500,durationSec=.5,dt=.05); b.t+=.2; b.shot("after_fx",.8); return b.done()
def save_as_folder():
    b=start(); b.st("load_preset",deck="A",target="library/presets/tt_folder/tt_inside.lsd",dt=.1); b.t+=.8; b.click("perf_src_more_A"); b.shot("menu",.4); b.click(x=60,y=94); b.shot("dialog",.6); b.st("type",target="tt_saved",dt=.1); b.t+=1.5; b.shot("after"); return b.done()
def delete_loaded():
    b=start(); b.st("load_preset",deck="A",target="library/presets/tt_alpha.lsd",dt=.1); b.t+=.8; b.rclick(x=340,y=453); b.click(x=SX,y=574); b.st("key",target="enter",dt=.1); b.t+=.8; b.shot("after"); b.st("key",target="ctrl+s",dt=.05); b.t+=.6; b.shot("save_asks",.6); return b.done()

def missing_scroll():
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8; b.click(x=60,y=644); b.go(x=500,y=560); b.st("scroll",value=-10,dt=.1); b.t+=.5; b.shot("scrolled",.6); return b.done()

def missing_fix():
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8; b.click(x=60,y=644); b.shot("list",.6); return b.done()
def missing_remove():
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8; b.click(x=60,y=644); b.t+=.4; b.click(x=407,y=468); b.t+=.4; b.shot("after",.6); return b.done()
def queue_clear2():
    b=start(); b.click(x=340,y=453); b.st("key",target="q",dt=.05); b.t+=.3; b.st("library_mode",target="FULL",dt=.1); b.t+=.6; b.click(x=160,y=406); b.click(x=205,y=418); b.t+=.3; b.shot("cleared",.6); b.st("key",target="ctrl+z",dt=.1); b.t+=.4; b.shot("undone",.6); return b.done()
def queue_export():
    b=start(); b.click(x=340,y=453); b.st("key",target="q",dt=.05); b.t+=.3; b.st("library_mode",target="FULL",dt=.1); b.t+=.6; b.click(x=160,y=406); b.click(x=205,y=394); b.shot("dialog",.5); b.st("type",target="ttexport",dt=.1); b.t+=1.2; b.shot("after"); return b.done()
def save_as2():
    b=start(); b.st("load_preset",deck="A",target="library/presets/tt_folder/tt_inside.lsd",dt=.1); b.t+=.8; b.click("perf_src_more_A"); b.click(x=110,y=66); b.shot("dialog",.6); b.st("type",target="tt_saved",dt=.1); b.t+=1.5; b.shot("after"); return b.done()
def loaded_save_click():
    b=start(); b.st("load_preset",deck="A",target="library/presets/tt_alpha.lsd",dt=.1); b.t+=.8; b.rclick(x=340,y=453); b.click(x=SX,y=528); b.st("type",target="tt_loaded",dt=.1); b.t+=1.5; b.st("set_param",deck="A",param="Hue",value=.33,dt=.1); b.t+=.3; b.click("perf_src_save_A"); b.t+=.8; b.shot("saved"); return b.done()
def delete_loaded_save():
    b=start(); b.st("load_preset",deck="A",target="library/presets/tt_alpha.lsd",dt=.1); b.t+=.8; b.rclick(x=340,y=453); b.click(x=SX,y=574); b.st("key",target="enter",dt=.1); b.t+=.8; b.shot("after"); b.click("perf_src_save_A"); b.t+=.4; b.shot("asks",.6); return b.done()
def playlist_rclick():
    b=start(); b.rclick(x=60,y=610); b.shot("menu",.5); return b.done()
def row_kebab_tip():
    b=start("trans"); b.st("tooltips",value=1); b.go(x=340,y=453); b.t+=1.2; b.shot("tip"); return b.done()
def fav_fx():
    b=start("fx"); b.rclick(x=340,y=453); b.click(x=SX,y=292); b.t+=.6; b.shot("after"); return b.done()
def undo_slot_pick():
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8; b.click("fxcell_deckA_fx_1"); b.t+=.5; b.search("kaleido"); b.click(x=340,y=453); b.t+=.8; b.shot("picked",.5); b.st("key",target="ctrl+z",dt=.1); b.t+=.8; b.shot("undone",.5); return b.done()
def undo_trans_pick():
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8; b.st("set_page",target="mixer"); b.t+=.4; b.click("perf_trans_picker"); b.t+=.5; b.search("film"); b.click(x=340,y=453); b.t+=.8; b.shot("picked",.5); b.st("key",target="ctrl+z",dt=.1); b.t+=.8; b.shot("undone",.5); return b.done()

def save_as_abs():
    b=start(); b.st("load_preset",deck="A",target=LIB+"/presets/tt_folder/tt_inside.lsd",dt=.1); b.t+=.8; b.click("perf_src_more_A"); b.click(x=110,y=66); b.shot("dialog",.6); b.st("type",target="tt_saved_abs",dt=.1); b.t+=1.5; b.shot("after"); return b.done()

def qclear_half():
    b=start(); b.click(x=340,y=453); b.st("key",target="q",dt=.05); b.t+=.3; b.tab("queues"); b.shot("filled",.4); b.click(x=154,y=378); b.shot("menu",.4); b.click(x=205,y=390); b.t+=.3; b.shot("cleared",.5); b.st("key",target="ctrl+z",dt=.1); b.t+=.5; b.shot("undone",.5); return b.done()
def qclear_full_wait():
    b=start(); b.click(x=340,y=453); b.st("key",target="q",dt=.05); b.t+=.3; b.st("library_mode",target="FULL",dt=.1); b.t+=.6; b.click(x=160,y=406); b.click(x=205,y=418); b.t+=.3; b.shot("cleared",.6); b.go(x=1000,y=20); b.t+=.5; b.st("key",target="ctrl+z",dt=.1); b.t+=1; b.shot("undone",.8); b.st("key",target="ctrl+z",dt=.1); b.t+=1; b.shot("undone2",.8); return b.done()

def folder_with_playlist():
    b=B(); b.st("move",x=1000,y=20,durationSec=.1); b.t+=.8; b.click(x=60,y=660); b.t+=.3; b.st("tooltips",value=1); b.go("btnid_browserNewFolder"); b.t+=1.2; b.shot("tip"); b.st("tooltips",value=0); b.click("btnid_browserNewFolder"); b.t+=.3; b.st("type",target="ttplf",dt=.1); b.t+=1.2; b.shot("after"); return b.done()
def info_note():
    b=start(); b.st("tooltips",value=1); b.go(x=340,y=453); b.t+=1.2; b.shot("tip"); return b.done()
def delete_toast():
    b=start(); b.rclick(x=340,y=453); b.click(x=SX,y=574); b.shot("dialog",.4); b.st("key",target="enter",dt=.05); b.t+=.6; b.shot("after",.3); return b.done()
LIB=os.path.abspath(os.path.dirname(__file__)+"/../rt/library")
CASES={k:v for k,v in list(globals().items()) if callable(v) and k not in("B","esc","start") and not k.startswith("_")}
def pl(L):
    for f in ("playlists/ttpl.lsdplay","fx_playlists/ttfx.lsdfxplay","transition_playlists/tttr.lsdtransplay"):
        s=open(f"{L}/{f}").read(); print("     ",f.split("/")[1],[l for l in s.splitlines() if l and not l.startswith("#")] if "lsdplay" in f else json.loads(s).get("items"))
def tags(L,f): d=json.load(open(f"{L}/{f}")); print("     ",f,"name=",d.get("name"),"tags=",d.get("tags"),"keys=",len(d))
CHECKS={k:pl for k in("move_via_menu","folder_rename","folder_delete","folder_drag","rename_preset","clone_preset","delete_preset","drag_move","fx_rename_chain","fx_rename_single","fx_delete_single","trans_rename","trans_delete","multi_delete")}
CHECKS["missing_remove"]=lambda L: print("     ",open(L+"/playlists/ttmiss.lsdplay").read().split())
CHECKS["add_tag"]=lambda L:tags(L,"presets/tt_alpha.lsd")
CHECKS["rename_preset"]=lambda L:(pl(L),tags(L,"presets/tt_renamed.lsd"))
CHECKS["loaded_save_click"]=lambda L:(tags(L,"presets/tt_loaded.lsd"),print("      Hue",json.load(open(L+"/presets/tt_loaded.lsd"))["parameters"].get("Hue",{}).get("baseValue")))
CHECKS["loaded_rename_save"]=lambda L:(pl(L),tags(L,"presets/tt_loaded.lsd"))
