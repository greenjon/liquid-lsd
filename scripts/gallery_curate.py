#!/usr/bin/env python3
"""
gallery_curate.py - curate the photos the web TV's gallery background plays (web/gallery.js).

The photos are the listener uploads from the old radio page (spaz.org/radio, "Add Pic"). Curating happens in
gallery-curate/ at the repo root (not in git): download them all, delete the ones that do not work behind the
visuals (by hand in a file manager, or in the preview), then build the TV's copies.

    python3 scripts/gallery_curate.py download   # every upload into gallery-curate/ (about 775 MB; skips files it has)
    python3 scripts/gallery_curate.py preview    # the TV at http://localhost:8000/?gallery=curate, playing what is
                                                 # left in gallery-curate/ one by one with its name on screen:
                                                 # Delete rejects the photo on top (moved to gallery-curate/_rejected/),
                                                 # U puts the last one back, . brings the next one in now
    python3 scripts/gallery_curate.py build      # what is left -> web/gallery/ (1024 px JPEGs) + web/gallery.json,
                                                 # then `lite`
    python3 scripts/gallery_curate.py lite       # web/gallery/ at 512 px -> web/gallery/lite/ (about 35 KB each),
                                                 # for the TV's lite mode (web/lite.js); adds "lite" to gallery.json
    python3 scripts/gallery_curate.py export DIR [SIDE]
                                                 # web/gallery/ scaled to SIDE px (default 768) -> DIR/gallery/ +
                                                 # DIR/gallery.json: the Spaz Radio app's bundled copy (its
                                                 # scripts/sync_visuals.sh runs this)

The old page also has a logo folder and a sky folder. `--set logo` or `--set sky` before the command curates one of
those instead, in gallery-curate/logo/ or gallery-curate/sky/; their `build` adds them to web/gallery.json under
"logo" and "sky" (the photos' build keeps those):

    python3 scripts/gallery_curate.py --set logo download
    python3 scripts/gallery_curate.py --set logo preview   # a grid at http://localhost:8000/curate/grid, each picture
                                                           # on a checkerboard so transparency shows; click one to
                                                           # reject it, click a rejected one to put it back
    python3 scripts/gallery_curate.py --set logo build     # -> web/gallery/logo/ (512 px PNGs that keep their
                                                           # transparency; a plain black or white backdrop around
                                                           # a logo is cut away); logos still opaque after that are
                                                           # listed under "opaque" (the lite picture only)
    python3 scripts/gallery_curate.py --set sky build      # -> web/gallery/sky/ (1024 px) + sky/lite/ (512 px)

The video background (gallery.js) shows the see-through logos; the lite picture (lite.js) shows all three: sky at
the back, photos, logos in front.

web/gallery/ and web/gallery.json are not in git either; scripts/publish_web_tv.sh uploads them with the rest of
web/ (it copies the working tree), so the TV loads them from spaz.org/tv/gallery/.
"""
import concurrent.futures
import functools
import http.server
import json
import re
import shutil
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / 'web'
CURATE = ROOT / 'gallery-curate'
REJECTED = CURATE / '_rejected'
OUT_DIR = WEB / 'gallery'
OUT_JSON = WEB / 'gallery.json'
RADIO = 'https://www.spaz.org/radio/'
SOURCE = 'https://spaz.org/images/ground/'
SET = 'ground'   # which folder of the old page: ground (the photos), logo or sky; --set changes it
UA = {'User-Agent': 'liquid-lsd-gallery-curate'}
IMAGE_EXT = {'.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp'}
MAX_SIDE = 1024
LITE_DIR = OUT_DIR / 'lite'
LITE_SIDE, LITE_QUALITY = 512, 70


def use_set(name):
    """Curate the old page's `name` folder: ground keeps gallery-curate/ itself, the others get a subfolder."""
    global SET, SOURCE, CURATE, REJECTED
    if name not in ('ground', 'logo', 'sky'):
        sys.exit(f'unknown set {name!r}: ground, logo or sky')
    SET, SOURCE = name, f'https://spaz.org/images/{name}/'
    if name != 'ground':
        CURATE = CURATE / name
        REJECTED = CURATE / '_rejected'


def photos():
    """The photos left in gallery-curate/, sorted by name."""
    if not CURATE.is_dir():
        return []
    return sorted(p.name for p in CURATE.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXT)


# ------------------------------------------------------------------ download
def download():
    page = urllib.request.urlopen(urllib.request.Request(RADIO, headers=UA), timeout=30).read().decode('utf-8', 'replace')
    urls = json.loads(re.search(r'imageURLs=(\{.*?\});?\s*\n', page, re.S).group(1))[SET]['urls']
    names = [u.split(f'/images/{SET}/', 1)[1] for u in urls if f'/images/{SET}/' in u]
    CURATE.mkdir(exist_ok=True)
    rejected = {p.name for d in (REJECTED, CURATE / '_unreadable') if d.is_dir() for p in d.iterdir()}

    def get(name):
        dest = CURATE / name
        if dest.exists() or name in rejected or '/' in name:
            return 'have'
        try:
            r = urllib.request.urlopen(urllib.request.Request(SOURCE + urllib.parse.quote(name), headers=UA), timeout=120)
            if not (r.headers.get('Content-Type') or '').startswith('image/'):
                return 'not an image'
            tmp = dest.with_name(dest.name + '.part')
            with open(tmp, 'wb') as f:
                shutil.copyfileobj(r, f)
            tmp.rename(dest)
            return 'ok'
        except Exception as e:
            return f'failed ({e})'

    done = 0
    with concurrent.futures.ThreadPoolExecutor(4) as ex:
        for name, result in zip(names, ex.map(get, names)):
            done += 1
            if result not in ('ok', 'have'):
                print(f'{name}: {result}')
            if done % 50 == 0:
                print(f'{done}/{len(names)}', flush=True)
    # A few uploads are not pictures at all (a video or a document renamed .jpg): set them aside.
    from PIL import Image
    bad = CURATE / '_unreadable'
    for name in photos():
        try:
            with Image.open(CURATE / name) as im:
                im.verify()
        except Exception:
            bad.mkdir(exist_ok=True)
            (CURATE / name).rename(bad / name)
            print(f'{name}: not a picture, moved to {bad.name}/')
    print(f'{len(photos())} photos in {CURATE}')


# ------------------------------------------------------------------ preview
class PreviewHandler(http.server.SimpleHTTPRequestHandler):
    """Serves web/, plus gallery-curate/ under /curate/ (list.json, the photos, reject and undo)."""

    def log_message(self, *args):
        pass

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def send_json(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def curate_file(self, name):
        p = (CURATE / name).resolve()
        return p if p.parent == CURATE.resolve() and p.is_file() else None

    def do_GET(self):
        path = urllib.parse.urlparse(self.path).path
        if path == '/curate/list.json':
            return self.send_json({'base': 'curate/', 'images': photos()})
        if path == '/curate/grid':
            body = GRID_PAGE.replace('__SET__', SET).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if path == '/curate/grid.json':
            rejected = sorted(q.name for q in REJECTED.iterdir() if q.is_file()) if REJECTED.is_dir() else []
            return self.send_json({'keep': [info(CURATE / n) for n in photos()],
                                   'rejected': [info(REJECTED / n) for n in rejected]})
        if path.startswith('/curate/_rejected/'):
            p = (REJECTED / urllib.parse.unquote(path[len('/curate/_rejected/'):])).resolve()
            if p.parent != REJECTED.resolve() or not p.is_file():
                return self.send_error(404)
            data = p.read_bytes()
            self.send_response(200)
            self.send_header('Content-Type', self.guess_type(str(p)))
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if path.startswith('/curate/'):
            p = self.curate_file(urllib.parse.unquote(path[len('/curate/'):]))
            if not p:
                return self.send_error(404)
            data = p.read_bytes()
            self.send_response(200)
            self.send_header('Content-Type', self.guess_type(str(p)))
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        super().do_GET()

    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length') or 0)) or b'{}')
        if path == '/curate/reject':
            p = self.curate_file(str(body.get('name', '')))
            if not p:
                return self.send_json({'ok': False}, 404)
            REJECTED.mkdir(exist_ok=True)
            p.rename(REJECTED / p.name)
            print(f'rejected {p.name}  ({len(photos())} left)', flush=True)
            return self.send_json({'ok': True, 'left': len(photos())})
        if path == '/curate/restore':
            p = (REJECTED / str(body.get('name', ''))).resolve()
            if p.parent != REJECTED.resolve() or not p.is_file():
                return self.send_json({'ok': False}, 404)
            p.rename(CURATE / p.name)
            print(f'put back {p.name}  ({len(photos())} left)', flush=True)
            return self.send_json({'ok': True})
        if path == '/curate/undo':
            if not REJECTED.is_dir():
                return self.send_json({'ok': False}, 404)
            last = max(REJECTED.iterdir(), key=lambda q: q.stat().st_mtime, default=None)
            if not last:
                return self.send_json({'ok': False}, 404)
            last.rename(CURATE / last.name)
            print(f'put back {last.name}  ({len(photos())} left)', flush=True)
            return self.send_json({'ok': True, 'name': last.name})
        self.send_error(404)


@functools.lru_cache(maxsize=None)
def _info(path, mtime):
    from PIL import Image
    d = {'name': path.name, 'size': path.stat().st_size}
    try:
        with Image.open(path) as im:
            d['w'], d['h'], d['frames'] = im.width, im.height, getattr(im, 'n_frames', 1)
            im.seek(0)
            a = im.convert('RGBA').getchannel('A')
            hist = a.histogram()
            d['clear'] = round(100 * sum(hist[:16]) / (im.width * im.height))   # % (nearly) transparent pixels
    except Exception as e:
        d['error'] = str(e)
    return d


def info(path):
    """Size, frames and how much of a picture is transparent, for the grid."""
    return _info(path, path.stat().st_mtime)


GRID_PAGE = r"""<!doctype html><meta charset=utf-8><title>__SET__ curate</title>
<style>
body{margin:0;padding:16px;background:#222;color:#ddd;font:13px system-ui,sans-serif}
h2{font-size:15px;margin:24px 0 8px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px}
figure{margin:0;cursor:pointer;background:#333;border-radius:6px;overflow:hidden}
.pic{aspect-ratio:1;display:flex;align-items:center;justify-content:center;
 background:repeating-conic-gradient(#888 0 25%,#bbb 0 50%) 0 0/20px 20px}
.pic img{max-width:100%;max-height:100%}figcaption{padding:6px 8px;word-break:break-all}
.tag{display:inline-block;padding:0 5px;margin-right:4px;border-radius:3px;background:#555}
.alpha{background:#2a6}.opaque{background:#a52}#rejected figure{opacity:.5}
body.dark .pic{background:#000}
</style>
<p><b>__SET__</b>: click a picture to reject it; click a rejected one to put it back.
<label><input type=checkbox onchange="document.body.classList.toggle('dark',this.checked)"> black behind</label></p>
<h2 id=kh></h2><div class=grid id=keep></div><h2 id=rh></h2><div class=grid id=rejected></div>
<script>
async function load(){
  const d=await (await fetch('grid.json')).json();
  for(const [id,list,base,act] of [['keep',d.keep,'','reject'],['rejected',d.rejected,'_rejected/','restore']]){
    document.getElementById(id[0]+'h').textContent=(id=='keep'?'Kept':'Rejected')+' ('+list.length+')';
    const g=document.getElementById(id);g.replaceChildren();
    for(const it of list){
      const f=document.createElement('figure'),pic=document.createElement('div'),img=document.createElement('img'),c=document.createElement('figcaption');
      pic.className='pic';img.src=base+encodeURIComponent(it.name);img.loading='lazy';pic.append(img);
      const tags=it.error?['<span class="tag opaque">unreadable</span>']:[
        it.clear>=5?'<span class="tag alpha">'+it.clear+'% clear</span>':'<span class="tag opaque">opaque</span>',
        '<span class=tag>'+it.w+'×'+it.h+'</span>',it.frames>1?'<span class=tag>'+it.frames+' frames</span>':''];
      c.innerHTML=tags.join('')+'<br>';c.append(it.name);f.append(pic,c);
      f.onclick=async()=>{await fetch(act,{method:'POST',body:JSON.stringify({name:it.name})});load();};
      g.append(f);
    }
  }
}
load();
</script>"""


def preview(port=8000):
    if not photos():
        sys.exit(f'no photos in {CURATE}: run "{sys.argv[0]} {"" if SET == "ground" else "--set " + SET + " "}download" first')
    handler = functools.partial(PreviewHandler, directory=str(WEB))
    with http.server.ThreadingHTTPServer(('127.0.0.1', port), handler) as srv:
        where = '/?gallery=curate' if SET == 'ground' else '/curate/grid'
        print(f'{len(photos())} pictures. Open http://localhost:{port}{where}  (Ctrl+C stops)', flush=True)
        try:
            srv.serve_forever()
        except KeyboardInterrupt:
            pass


# ------------------------------------------------------------------ build
def slugger(ext):
    """A web-safe file name for each picture, unique in its folder (x.JPG.jpg -> x.jpg)."""
    used = set()

    def slug(name):
        stem = re.sub(r'(\.(jpe?g|png|gif|webp|bmp|tiff?|heic|mov|mp4))+$', '', name, flags=re.I)
        stem = re.sub(r'[^A-Za-z0-9._-]+', '_', stem).strip('._') or 'photo'
        out, n = f'{stem}{ext}', 2
        while out.lower() in used:
            out, n = f'{stem}_{n}{ext}', n + 1
        used.add(out.lower())
        return out
    return slug


def read_manifest():
    return json.loads(OUT_JSON.read_text()) if OUT_JSON.exists() else {}


def build():
    from PIL import Image, ImageOps
    if SET == 'logo':
        return build_logos()
    if SET == 'sky':
        return build_sky()
    names = photos()
    if not names:
        sys.exit(f'no photos in {CURATE}')
    OUT_DIR.mkdir(exist_ok=True)
    out = []
    slugify = slugger('.jpg')
    for name in names:
        slug = slugify(name)
        try:
            with Image.open(CURATE / name) as im:
                im.seek(0)                                   # a GIF's first frame
                im = ImageOps.exif_transpose(im)
                if im.mode in ('RGBA', 'LA', 'P'):
                    im = im.convert('RGBA')
                    bg = Image.new('RGB', im.size, (0, 0, 0))
                    bg.paste(im, mask=im.getchannel('A'))
                    im = bg
                im = im.convert('RGB')
                im.thumbnail((MAX_SIDE, MAX_SIDE), Image.LANCZOS)
                im.save(OUT_DIR / slug, 'JPEG', quality=85, optimize=True, progressive=True)
            out.append(slug)
        except Exception as e:
            print(f'{name}: skipped ({e})')
    keep = set(out)
    for stale in OUT_DIR.iterdir():
        if stale.is_file() and stale.name not in keep:
            stale.unlink()
    old = read_manifest()
    OUT_JSON.write_text(json.dumps({
        'source': RADIO,
        'base': 'gallery/',
        'note': 'Curated with scripts/gallery_curate.py (not in git; publish_web_tv.sh uploads it).',
        'images': out,
        **{k: old[k] for k in ('logo', 'sky') if k in old},   # built on their own (--set logo / --set sky)
    }, indent=1) + '\n')
    size = sum(p.stat().st_size for p in OUT_DIR.iterdir() if p.is_file())
    print(f'{len(out)} photos -> {OUT_DIR} ({size // 2**20} MB) and {OUT_JSON}')
    lite()


def flat_rgb(im):
    """A picture's first frame, upright, on black where it is see-through."""
    from PIL import Image, ImageOps
    im.seek(0)
    im = ImageOps.exif_transpose(im)
    if im.mode in ('RGBA', 'LA', 'P'):
        im = im.convert('RGBA')
        bg = Image.new('RGB', im.size, (0, 0, 0))
        bg.paste(im, mask=im.getchannel('A'))
        im = bg
    return im.convert('RGB')


def clear_share(im):
    """Share of (nearly) transparent pixels, 0..1."""
    hist = im.getchannel('A').histogram()
    return sum(hist[:16]) / (im.width * im.height)


def cut_backdrop(im):
    """An opaque logo on a plain black or white square: that backdrop, flooded in from the edges, made transparent."""
    from PIL import Image, ImageDraw, ImageFilter
    rgb = im.convert('RGB')
    w, h = rgb.size
    edge = [rgb.getpixel((x, y)) for x in range(0, w, max(1, w // 64)) for y in (0, h - 1)] \
        + [rgb.getpixel((x, y)) for y in range(0, h, max(1, h // 64)) for x in (0, w - 1)]
    for target in ((0, 0, 0), (255, 255, 255)):
        near = [p for p in edge if max(abs(a - b) for a, b in zip(p, target)) < 40]
        if len(near) < 0.9 * len(edge):
            continue
        mark = (1, 254, 3)                                   # a colour no backdrop pixel ends up as
        flood = rgb.copy()
        for x, y in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)] + [(x, y) for x in range(0, w, max(1, w // 16)) for y in (0, h - 1)]:
            if max(abs(a - b) for a, b in zip(flood.getpixel((x, y)), target)) < 40:
                ImageDraw.floodfill(flood, (x, y), mark, thresh=40)
        alpha = Image.new('L', (w, h), 255)
        px, ap = flood.load(), alpha.load()
        for y in range(h):
            for x in range(w):
                if px[x, y] == mark:
                    ap[x, y] = 0
        alpha = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))   # soft, no halo
        out = im.convert('RGBA')
        out.putalpha(alpha)
        return out
    return im


LOGO_SIDE = 512


def build_logos():
    """The curated logos -> web/gallery/logo/ (PNG, transparency kept) and gallery.json "logo"."""
    from PIL import Image, ImageOps
    names = photos()
    if not names:
        sys.exit(f'no logos in {CURATE}')
    out_dir = OUT_DIR / 'logo'
    out_dir.mkdir(parents=True, exist_ok=True)
    out, opaque = [], []
    slugify = slugger('.png')
    for name in names:
        slug = slugify(name)
        try:
            with Image.open(CURATE / name) as im:
                im.seek(0)                                   # a GIF's first frame
                im = ImageOps.exif_transpose(im).convert('RGBA')
                im.thumbnail((LOGO_SIDE, LOGO_SIDE), Image.LANCZOS)
                if clear_share(im) < 0.05:
                    im = cut_backdrop(im)
                if clear_share(im) < 0.05:
                    opaque.append(slug)
                im.save(out_dir / slug, 'PNG', optimize=True)
            out.append(slug)
        except Exception as e:
            print(f'{name}: skipped ({e})')
    keep = set(out)
    for stale in out_dir.iterdir():
        if stale.is_file() and stale.name not in keep:
            stale.unlink()
    m = read_manifest()
    m['logo'] = {'base': 'gallery/logo/', 'images': out, 'opaque': opaque}
    OUT_JSON.write_text(json.dumps(m, indent=1) + '\n')
    size = sum(p.stat().st_size for p in out_dir.iterdir() if p.is_file())
    print(f'{len(out)} logos -> {out_dir} ({size / 2**20:.1f} MB); opaque (lite only): {", ".join(opaque) or "none"}')


def build_sky():
    """The curated skies -> web/gallery/sky/ (1024 px) + sky/lite/ (512 px) and gallery.json "sky"."""
    from PIL import Image
    names = photos()
    if not names:
        sys.exit(f'no skies in {CURATE}')
    out_dir, lite_dir = OUT_DIR / 'sky', OUT_DIR / 'sky' / 'lite'
    lite_dir.mkdir(parents=True, exist_ok=True)
    out = []
    slugify = slugger('.jpg')
    for name in names:
        slug = slugify(name)
        try:
            with Image.open(CURATE / name) as im:
                im = flat_rgb(im)
            for d, side, q in ((out_dir, MAX_SIDE, 85), (lite_dir, LITE_SIDE, LITE_QUALITY)):
                c = im.copy()
                c.thumbnail((side, side), Image.LANCZOS)
                c.save(d / slug, 'JPEG', quality=q, optimize=True, progressive=True)
            out.append(slug)
        except Exception as e:
            print(f'{name}: skipped ({e})')
    keep = set(out)
    for d in (out_dir, lite_dir):
        for stale in d.iterdir():
            if stale.is_file() and stale.name not in keep:
                stale.unlink()
    m = read_manifest()
    m['sky'] = {'base': 'gallery/sky/', 'lite': 'gallery/sky/lite/', 'images': out}
    OUT_JSON.write_text(json.dumps(m, indent=1) + '\n')
    size = sum(p.stat().st_size for p in lite_dir.iterdir() if p.is_file())
    print(f'{len(out)} skies -> {out_dir} (lite copies {size / 2**20:.1f} MB)')


# ------------------------------------------------------------------ lite
def lite():
    """Small copies of the built gallery for the TV's lite mode (old or slow devices: web/lite.js)."""
    from PIL import Image
    if not OUT_JSON.exists():
        sys.exit(f'no {OUT_JSON}: run "{sys.argv[0]} build" first')
    manifest = json.loads(OUT_JSON.read_text())
    LITE_DIR.mkdir(exist_ok=True)
    names = [n for n in manifest['images'] if (OUT_DIR / n).is_file()]
    for name in names:
        with Image.open(OUT_DIR / name) as im:
            im = im.convert('RGB')
            im.thumbnail((LITE_SIDE, LITE_SIDE), Image.LANCZOS)
            im.save(LITE_DIR / name, 'JPEG', quality=LITE_QUALITY, optimize=True, progressive=True)
    keep = set(names)
    for stale in LITE_DIR.iterdir():
        if stale.name not in keep:
            stale.unlink()
    manifest['lite'] = 'gallery/lite/'
    OUT_JSON.write_text(json.dumps(manifest, indent=1) + '\n')
    size = sum(p.stat().st_size for p in LITE_DIR.iterdir())
    print(f'{len(names)} photos at {LITE_SIDE} px -> {LITE_DIR} ({size / 2**20:.1f} MB)')


# ------------------------------------------------------------------ export
def export(dest, side=768):
    """The built gallery (web/gallery.json + web/gallery/) at phone size, for an app that bundles the TV."""
    from PIL import Image
    if not OUT_JSON.exists():
        sys.exit(f'no {OUT_JSON}: run "{sys.argv[0]} build" first')
    manifest = json.loads(OUT_JSON.read_text())
    dest = Path(dest)
    out_dir = dest / 'gallery'
    out_dir.mkdir(parents=True, exist_ok=True)
    names = [n for n in manifest['images'] if (OUT_DIR / n).is_file()]
    for name in names:
        with Image.open(OUT_DIR / name) as im:
            im = im.convert('RGB')
            im.thumbnail((side, side), Image.LANCZOS)
            im.save(out_dir / name, 'JPEG', quality=80, optimize=True, progressive=True)
    keep = set(names)
    for stale in out_dir.iterdir():
        if stale.is_file() and stale.name not in keep:
            stale.unlink()
    app = {k: v for k, v in manifest.items() if k not in ('lite', 'sky')}   # no lite picture in the app
    logo = manifest.get('logo')
    if logo:                                                   # the video background's logos, as built
        logo_dir = out_dir / 'logo'
        logo_dir.mkdir(exist_ok=True)
        video = [n for n in logo['images'] if n not in logo.get('opaque', []) and (OUT_DIR / 'logo' / n).is_file()]
        for n in video:
            shutil.copyfile(OUT_DIR / 'logo' / n, logo_dir / n)
        for stale in logo_dir.iterdir():
            if stale.name not in video:
                stale.unlink()
        app['logo'] = {'base': 'gallery/logo/', 'images': video, 'opaque': []}
    (dest / 'gallery.json').write_text(json.dumps(dict(app, base='gallery/', images=names), indent=1) + '\n')
    size = sum(p.stat().st_size for p in out_dir.iterdir())
    print(f'{len(names)} photos at {side} px -> {out_dir} ({size / 2**20:.1f} MB)')


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == '--set':
        use_set(sys.argv[2])
        del sys.argv[1:3]
    elif any(a.startswith('--set') for a in sys.argv[1:]):
        sys.exit('--set NAME goes before the command')
    cmd = sys.argv[1] if len(sys.argv) > 1 else ''
    if SET != 'ground' and cmd in ('lite', 'export'):
        sys.exit(f'{cmd} only applies to the photos')
    if cmd == 'download':
        download()
    elif cmd == 'preview':
        preview(int(sys.argv[2]) if len(sys.argv) > 2 else 8000)
    elif cmd == 'build':
        build()
    elif cmd == 'lite':
        lite()
    elif cmd == 'export' and len(sys.argv) > 2:
        export(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 768)
    else:
        sys.exit(__doc__)
