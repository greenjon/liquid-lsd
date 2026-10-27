#!/usr/bin/env python3
"""Build the signed content-update manifest for the Spaz Radio app (and anything else that follows it).

The manifest lists the *data* files of web/ (presets, playlists, shaders, FX chains, transitions, catalog,
settings), each with sha256 + size, and is signed with an ECDSA P-256 / SHA-256 key so a client can trust
what it downloads even if the server or domain were taken over. JS, HTML and CSS are deliberately NOT
listed: downloaded content is data only; the engine ships with the app.

  scripts/make_content_manifest.py OUTDIR            # writes OUTDIR/manifest.json + OUTDIR/manifest.json.sig
  scripts/make_content_manifest.py OUTDIR --no-sign  # manifest only (dry runs / tests)

The private key lives outside the repo (default ~/.config/liquid-lsd/content-signing.pem, override with
LSD_CONTENT_KEY); the public key is scripts/content_signing_pub.pem and is baked into the app.
The signature is over the exact bytes of manifest.json (DER, `openssl dgst -sha256 -sign`).

CONTENT_ENGINE is the engine level the content needs. Bump it when web/ gains a feature that new content
relies on and old apps lack (new modulator source, new ISF input kind, ...). An app whose own engine level
is lower ignores the update instead of showing broken looks.
"""
import hashlib, json, os, subprocess, sys, time
from pathlib import Path

CONTENT_ENGINE = 1
ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "web"
DIRS = ["presets", "playlists", "shaders", "fxchains", "content"]
DATA_EXT = {".lsd", ".lsdplay", ".lsdfxchain", ".lsdtrans", ".frag", ".vert", ".fs", ".json"}
ROOT_FILES = ["catalog.json", "settings.json", "mandala_recipes.json", "autopilot.json"]


def collect():
    paths = [WEB / f for f in ROOT_FILES]
    for d in DIRS:
        paths += sorted(p for p in (WEB / d).rglob("*") if p.is_file() and p.suffix in DATA_EXT)
    return paths


def build():
    files = {}
    for p in collect():
        data = p.read_bytes()
        files[p.relative_to(WEB).as_posix()] = {"sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}
    return {"format": 1, "version": int(time.time()), "minEngine": CONTENT_ENGINE, "files": files}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 1:
        sys.exit(__doc__)
    out = Path(args[0]); out.mkdir(parents=True, exist_ok=True)
    manifest = out / "manifest.json"
    manifest.write_text(json.dumps(build(), indent=1, sort_keys=True) + "\n")
    if "--no-sign" not in sys.argv:
        key = os.environ.get("LSD_CONTENT_KEY", str(Path.home() / ".config/liquid-lsd/content-signing.pem"))
        if not Path(key).exists():
            sys.exit(f"signing key not found: {key}")
        subprocess.run(["openssl", "dgst", "-sha256", "-sign", key, "-out", str(out / "manifest.json.sig"), str(manifest)], check=True)
    print(f"manifest: {len(json.loads(manifest.read_text())['files'])} files -> {out}")


main()
