#!/usr/bin/env python3
"""Build the greenjon.com site into ./greenjon (gitignored; deploy by FTP).

Landing page: website/templates/index.html + website/assets.
Docs:         user guide only, built with MkDocs Material from docs/ into greenjon/docs/.
              Developer docs, release notes and the archive are left out of the public site.

Usage: python3 scripts/build_website.py [--out DIR]
"""
import argparse
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import date
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
GITHUB_URL = "https://github.com/greenjon/liquid-lsd"
SITE_URL = "https://greenjon.com"
PUBLIC_NAV_DROP = ("Developer Reference", "Operations & Tuning", "Release Notes")
STAGE_DROP = ("developer", "archive", "licenses", "release_notes.md")




def stage_docs(stage: Path) -> None:
    shutil.copytree(ROOT / "docs", stage, ignore=shutil.ignore_patterns(*STAGE_DROP))
    index = stage / "index.md"
    text = index.read_text()
    # Cut the Developer Reference and Release Notes sections from the home page.
    text = re.sub(r"## Developer Reference.*", "", text, flags=re.S).rstrip()
    text = text.rstrip("-").rstrip() + "\n"
    index.write_text(text)


def build_docs(out: Path) -> None:
    cfg = yaml.load((ROOT / "mkdocs.yml").read_text(), Loader=yaml.SafeLoader)
    cfg["nav"] = [e for e in cfg["nav"] if not any(k in PUBLIC_NAV_DROP for k in e)]
    cfg.pop("exclude_docs", None)
    cfg["site_url"] = f"{SITE_URL}/docs/"
    cfg["repo_url"] = GITHUB_URL
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        stage = tmp / "docs"
        stage_docs(stage)
        cfg["docs_dir"] = str(stage)
        cfg["site_dir"] = str(out / "docs")
        conf = tmp / "mkdocs.yml"
        conf.write_text(yaml.safe_dump(cfg, sort_keys=False))
        subprocess.run(["mkdocs", "build", "-q", "-f", str(conf)], check=True)


def build_landing(out: Path) -> None:
    html = (ROOT / "website/templates/index.html").read_text()
    html = html.replace("{{GITHUB_URL}}", GITHUB_URL).replace("{{SITE_URL}}", SITE_URL)
    (out / "index.html").write_text(html)
    shutil.copytree(ROOT / "website/assets", out / "assets")
    for name in ("404.html", "robots.txt"):
        src = ROOT / "website/static" / name
        if src.exists():
            shutil.copy(src, out / name)
    pages = [f"{SITE_URL}/"] + [
        f"{SITE_URL}/docs/{p.parent.relative_to(out / 'docs')}/".replace("/./", "/")
        for p in sorted((out / "docs").rglob("index.html"))
        if p.parent != out / "docs" and p.parent.name != "search"
    ]
    pages.insert(1, f"{SITE_URL}/docs/")
    urls = "\n".join(f"  <url><loc>{u}</loc><lastmod>{date.today()}</lastmod></url>" for u in pages)
    (out / "sitemap.xml").write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n{urls}\n</urlset>\n'
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / "greenjon"))
    out = Path(ap.parse_args().out).resolve()
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    build_docs(out)
    build_landing(out)
    print(f"Site built in {out} - upload its contents to the greenjon.com web root.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
