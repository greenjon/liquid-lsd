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
    python3 scripts/gallery_curate.py build      # what is left -> web/gallery/ (1024 px JPEGs) + web/gallery.json
    python3 scripts/gallery_curate.py export DIR [SIDE]
                                                 # web/gallery/ scaled to SIDE px (default 768) -> DIR/gallery/ +
                                                 # DIR/gallery.json: the Spaz Radio app's bundled copy (its
                                                 # scripts/sync_visuals.sh runs this)

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
UA = {'User-Agent': 'liquid-lsd-gallery-curate'}
IMAGE_EXT = {'.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp'}
MAX_SIDE = 1024


def photos():
    """The photos left in gallery-curate/, sorted by name."""
    if not CURATE.is_dir():
        return []
    return sorted(p.name for p in CURATE.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXT)


# ------------------------------------------------------------------ download
def download():
    page = urllib.request.urlopen(urllib.request.Request(RADIO, headers=UA), timeout=30).read().decode('utf-8', 'replace')
    urls = json.loads(re.search(r'imageURLs=(\{.*?\});?\s*\n', page, re.S).group(1))['ground']['urls']
    names = [u.split('/images/ground/', 1)[1] for u in urls if '/images/ground/' in u]
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


def preview(port=8000):
    if not photos():
        sys.exit(f'no photos in {CURATE}: run "{sys.argv[0]} download" first')
    handler = functools.partial(PreviewHandler, directory=str(WEB))
    with http.server.ThreadingHTTPServer(('127.0.0.1', port), handler) as srv:
        print(f'{len(photos())} photos. Open http://localhost:{port}/?gallery=curate  (Ctrl+C stops)', flush=True)
        try:
            srv.serve_forever()
        except KeyboardInterrupt:
            pass


# ------------------------------------------------------------------ build
def build():
    from PIL import Image, ImageOps
    names = photos()
    if not names:
        sys.exit(f'no photos in {CURATE}')
    OUT_DIR.mkdir(exist_ok=True)
    out, used = [], set()
    for name in names:
        stem = re.sub(r'(\.(jpe?g|png|gif|webp|bmp|tiff?|heic|mov|mp4))+$', '', name, flags=re.I)  # x.JPG.jpg -> x
        stem = re.sub(r'[^A-Za-z0-9._-]+', '_', stem).strip('._') or 'photo'
        slug, n = f'{stem}.jpg', 2
        while slug.lower() in used:
            slug, n = f'{stem}_{n}.jpg', n + 1
        used.add(slug.lower())
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
        if stale.name not in keep:
            stale.unlink()
    OUT_JSON.write_text(json.dumps({
        'source': RADIO,
        'base': 'gallery/',
        'note': 'Curated with scripts/gallery_curate.py (not in git; publish_web_tv.sh uploads it).',
        'images': out,
    }, indent=1) + '\n')
    size = sum(p.stat().st_size for p in OUT_DIR.iterdir())
    print(f'{len(out)} photos -> {OUT_DIR} ({size // 2**20} MB) and {OUT_JSON}')


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
        if stale.name not in keep:
            stale.unlink()
    (dest / 'gallery.json').write_text(json.dumps(dict(manifest, base='gallery/', images=names), indent=1) + '\n')
    size = sum(p.stat().st_size for p in out_dir.iterdir())
    print(f'{len(names)} photos at {side} px -> {out_dir} ({size / 2**20:.1f} MB)')


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else ''
    if cmd == 'download':
        download()
    elif cmd == 'preview':
        preview(int(sys.argv[2]) if len(sys.argv) > 2 else 8000)
    elif cmd == 'build':
        build()
    elif cmd == 'export' and len(sys.argv) > 2:
        export(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 768)
    else:
        sys.exit(__doc__)
