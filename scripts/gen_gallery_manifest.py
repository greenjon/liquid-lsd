#!/usr/bin/env python3
"""
gen_gallery_manifest.py - writes web/gallery.json, the image list the web TV's Spaz gallery background
(web/gallery.js) plays from.

The images are the listener uploads on the legacy radio page (spaz.org/radio, "Add Pic"). The image folder
cannot be listed, so this reads the list the radio page carries (its `imageURLs.ground`), asks the server when
each one was uploaded (Last-Modified), and keeps the oldest COUNT: the early uploads were the more curated ones.
Files over MAX_BYTES are left out (a few uploads are 2-5 MB originals; the TV scales every photo to 1024 px anyway).

    python3 scripts/gen_gallery_manifest.py [COUNT]     (default 200)
"""
import concurrent.futures
import email.utils
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

RADIO = 'https://www.spaz.org/radio/'
BASE = 'https://spaz.org/images/ground/'   # sends Access-Control-Allow-Origin: *, so WebGL may use the pixels
OUT = Path(__file__).resolve().parent.parent / 'web' / 'gallery.json'
MAX_BYTES = 1_500_000
UA = {'User-Agent': 'liquid-lsd-gallery-manifest'}


def fetch(url, method='GET'):
    return urllib.request.urlopen(urllib.request.Request(url, method=method, headers=UA), timeout=30)


def uploaded(name):
    """(name, upload time) for a gallery image, or None when the server will not serve it as an image."""
    try:
        r = fetch(BASE + urllib.parse.quote(name), 'HEAD')
        lm = r.headers.get('Last-Modified')
        if not lm or not (r.headers.get('Content-Type') or '').startswith('image/'):
            return None
        if int(r.headers.get('Content-Length') or 0) > MAX_BYTES:
            return None
        return name, email.utils.parsedate_to_datetime(lm)
    except Exception:
        return None


def main():
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 200
    page = fetch(RADIO).read().decode('utf-8', 'replace')
    urls = json.loads(re.search(r'imageURLs=(\{.*?\});?\s*\n', page, re.S).group(1))['ground']['urls']
    names = [u.split('/images/ground/', 1)[1] for u in urls if '/images/ground/' in u]
    with concurrent.futures.ThreadPoolExecutor(8) as ex:
        dated = [d for d in ex.map(uploaded, names) if d]
    dated.sort(key=lambda d: d[1])
    keep = dated[:count]
    OUT.write_text(json.dumps({
        'source': RADIO,
        'base': BASE,
        'note': f'The {len(keep)} oldest of {len(dated)} listener uploads (of {len(names)}; none over {MAX_BYTES // 1000} kB), by upload date '
                f'({keep[0][1].date()} to {keep[-1][1].date()}). Regenerate: scripts/gen_gallery_manifest.py',
        'images': [n for n, _ in keep],
    }, indent=1, ensure_ascii=False) + '\n')
    print(f'{OUT}: {len(keep)} of {len(dated)} images, {keep[0][1].date()} .. {keep[-1][1].date()}')


if __name__ == '__main__':
    main()
