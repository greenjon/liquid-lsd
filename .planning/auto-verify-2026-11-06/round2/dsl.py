class B:
    def __init__(s): s.t=0.0; s.steps=[]; s.shots=[]; s.st("move",x=1000,y=20,durationSec=.1); s.t+=.8
    def st(s,a,dt=0.0,**k):
        s.t+=dt; d={"t":round(s.t,3),"action":a}; d.update(k); s.steps.append(d); return s
    def shot(s,l,w=.5):
        s.t+=w; s.shots.append({"label":l,"t":round(s.t,3)}); s.t+=.1; return s
    def state(s,l,w=.3):
        s.t+=w; s.st("audit_state",target=l); s.t+=.05; return s
    def go(s,k=None,x=None,y=None,**kw):
        if k: s.st("move",target=k,durationSec=.1,dt=.05,**kw)
        else: s.st("move",x=x,y=y,durationSec=.1,dt=.05)
        s.t+=.13; return s
    def click(s,*a,**k): s.go(*a,**k); s.st("click",dt=.03); s.t+=.2; return s
    def rclick(s,*a,**k): s.go(*a,**k); s.st("rclick",dt=.03); s.t+=.2; return s
    def dbl(s,*a,**k):
        s.go(*a,**k); s.st("click",dt=.03,durationSec=.03); s.st("click",dt=.1,durationSec=.03); s.t+=.2; return s
    def key(s,k,w=.25): s.st("key",target=k,dt=.04); s.t+=w; return s
    def typ(s,txt): s.st("type",target=txt,dt=.08); s.t+=len(txt)*.14+.5; return s
    def search(s,txt): s.click("browser_search"); return s.typ(txt)
    def tab(s,t): s.click("browser_tab_"+t); s.t+=.25; return s
    def drag(s,src=None,dst=None,x=None,y=None,sx=None,sy=None):
        s.go(src,x=sx,y=sy); s.t+=.15; d=dict(t=round(s.t+.05,3),action="drag",durationSec=.5)
        if dst: d['target']=dst
        else: d['x']=x; d['y']=y
        s.steps.append(d); s.shots.append({"label":"mid","t":round(s.t+.5,3)}); s.t+=1.05; return s
    def full(s): s.st("library_mode",target="FULL",dt=.1); s.t+=.6; return s
    def done(s): return s.steps,s.shots
