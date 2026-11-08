#!/usr/bin/env python3
"""upgrade.py <work dir>: the real Linux zips: run an older release in an invisible X server with no network, edit a shader, then unzip a newer release over it and run again. results/upgrade.txt, screenshots beside it."""
import glob,hashlib,json,os,shutil,subprocess,sys,time
H=os.path.dirname(os.path.abspath(__file__)); A=sys.argv[1]; U=A+'/up'; OLD,NEW=sys.argv[2],sys.argv[3]
out=[]; say=lambda *a:(out.append(' '.join(map(str,a))),print(*a,flush=True))
shutil.rmtree(U,ignore_errors=True); os.makedirs(U+'/dl'); os.makedirs(U+'/run'); os.makedirs(U+'/xdg/home'); os.makedirs(U+'/stub')
for n in 'xdg-open gio wpctl pkexec pw-dump pw-cli zenity kdialog'.split():
    p=f'{U}/stub/{n}'; open(p,'w').write('#!/bin/sh\nexit 1\n'); os.chmod(p,0o755)
for tag in (OLD,NEW):
    subprocess.run(['gh','release','download',tag,'-p','liquid-lsd-linux-x64.zip','-O',f'{U}/dl/{tag}.zip','--clobber'],check=True)
    say(tag,os.path.getsize(f'{U}/dl/{tag}.zip')//1000000,'MB')
I=U+'/install'; os.makedirs(I)
subprocess.run(['unzip','-q','-o',f'{U}/dl/{OLD}.zip','-d',I],check=True)
print(sorted(os.listdir(I))[:12])
tops=os.listdir(I); root=I if not (len(tops)==1 and os.path.isdir(f'{I}/{tops[0]}')) else f'{I}/{tops[0]}'
launch=next((p for p in ('run-linux.sh','run.sh','bin/liquid-lsd','liquid-lsd.sh','start.sh') if os.path.exists(f'{root}/{p}')),None)
say('layout',sorted(os.listdir(root))[:14],'launcher',launch)
ENV=dict(os.environ,DISPLAY=':79',XDG_SESSION_TYPE='x11',XDG_RUNTIME_DIR=U+'/run',XDG_DATA_HOME=U+'/xdg/data',XDG_CONFIG_HOME=U+'/xdg/config',XDG_CACHE_HOME=U+'/xdg/cache',HOME=U+'/xdg/home',PATH=U+'/stub:'+os.environ['PATH'],DBUS_SESSION_BUS_ADDRESS='unix:path=/nonexistent',__NV_PRIME_RENDER_OFFLOAD='1',__GLX_VENDOR_LIBRARY_NAME='nvidia')
ENV.pop('WAYLAND_DISPLAY',None)
xv=subprocess.Popen(['Xvfb',':79','-screen','0','1280x720x24'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); time.sleep(1)
def run(tag,secs=40):
    log=f'{U}/{tag}.log'
    with open(log,'w') as lg:
        p=subprocess.Popen(['unshare','-Urn','bash',launch],cwd=root,env=ENV,stdout=lg,stderr=subprocess.STDOUT,start_new_session=True)
        time.sleep(secs)
        subprocess.run(['import','-window','root',f'{H}/results/upgrade_{tag}.png'],env=dict(os.environ,DISPLAY=':79'))
        os.killpg(p.pid,15); time.sleep(4)
        try: os.killpg(p.pid,9)
        except ProcessLookupError: pass
    t=open(log,errors='replace').read(); return t
try:
    t=run(OLD)
    libs=[d for d in (f'{root}/library',f"{U}/xdg/home/library",f"{U}/xdg/data/liquid-lsd/library") if os.path.isdir(d)]
    say('first run: exceptions',t.count('Exception'),'library at',[os.path.relpath(x,U) for x in libs])
    L=libs[0]; fs=sorted(glob.glob(f'{L}/sources/**/*.fs',recursive=True))
    ed=[f for f in fs if 'mandelbulb' in f][0]; t0=open(ed).read()+'\n// my own edit\n'; open(ed,'w').write(t0)
    pr=f'{L}/presets/my_look.lsd'; prefs=glob.glob(f'{L}/presets/**/*.lsd',recursive=True)
    if prefs: shutil.copy(prefs[0],pr)
    before={f:hashlib.sha256(open(f,'rb').read()).hexdigest() for f in fs}; sess=os.path.exists(f'{L}/last_session.json')
    subprocess.run(['unzip','-q','-o',f'{U}/dl/{NEW}.zip','-d',I],check=True)
    t=run(NEW)
    after={f:hashlib.sha256(open(f,'rb').read()).hexdigest() for f in fs if os.path.exists(f)}
    say('second run: exceptions',t.count('Exception'),'errors',t.count(' ERROR '),'fatal' ,'hs_err' in ' '.join(os.listdir(root)))
    say('edited shader kept:',open(ed).read()==t0,'| my preset kept:',os.path.exists(pr),'| session file kept:',os.path.exists(f'{L}/last_session.json'),'was there:',sess)
    say('bundled files changed by the upgrade:',sum(1 for f,h in after.items() if h!=before.get(f)),'of',len(after))
    for l in t.splitlines():
        if any(x in l for x in ('Keeping','keeping','edited','Updated bundled','ensureDefault')): say('  log:',l[:150])
finally:
    xv.kill(); open(H+'/results/upgrade.txt','w').write('\n'.join(out)+'\n')
