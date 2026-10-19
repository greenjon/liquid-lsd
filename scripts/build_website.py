#!/usr/bin/env python3
"""Build the Liquid LSD site into ./greenjon (gitignored; published by scripts/publish_site.sh).

Landing page: website/templates/index.html + website/assets.
Docs:         user guide only, built with MkDocs Material from docs/ into greenjon/docs/.
              Developer docs, release notes and the archive are left out of the public site.

Usage: python3 scripts/build_website.py [--out DIR]
"""
import argparse
import html
import json
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
SITE_HOST = "liquid-lsd.greenjon.com"
SITE_URL = f"https://{SITE_HOST}"
PUBLIC_NAV_DROP = ("Developer Reference", "Operations & Tuning", "Release Notes")
STAGE_DROP = ("developer", "archive", "licenses", "release_notes.md")




def clip_block(video: dict, chapters: list, rel: str) -> str:
    """HTML for the chapter clips of one tour video, inserted into the docs page it explains."""
    cards = "\n".join(
        f'<figure><video controls muted loop playsinline preload="none" poster="{rel}tour/media/{video["id"]}/{c["id"]}.jpg" src="{rel}tour/media/{video["id"]}/{c["id"]}.mp4"></video>'
        f'<figcaption><a href="{rel}tour/#{video["id"]}/{c["id"]}">{html.escape(c["title"])}</a></figcaption></figure>'
        for c in chapters
    )
    return (f'<div class="tour-clips">\n<p class="tour-clips-head">Watch: <strong>{html.escape(video["title"])}</strong> '
            f'&mdash; <a href="{rel}tour/">open the guided tour</a></p>\n<div class="tour-clips-grid">\n{cards}\n</div>\n</div>\n')


def inject_clips(stage: Path, tours: Path) -> None:
    """After the intro paragraph of each page, add the clips of the videos that explain it."""
    by_page: dict[str, list[str]] = {}
    for v in json.loads((ROOT / "website/data/tour.json").read_text()):
        chap = tours / v["id"] / "chapters.json"
        if not chap.exists():
            continue
        page = v["docs"].removeprefix("docs/").rstrip("/") + ".md"
        chapters = [c for c in json.loads(chap.read_text()) if (tours / v["id"] / "clips" / f'{c["id"]}.mp4').exists()]
        if chapters and (stage / page).exists():
            rel = "../" * (len(Path(page).parts) + 1)
            by_page.setdefault(page, []).append(clip_block(v, chapters, rel))
    for page, blocks in by_page.items():
        f = stage / page
        text = f.read_text()
        m = re.search(r"^# [^\n]*\n\n.+?\n\n", text, flags=re.S | re.M)
        at = m.end() if m else 0
        f.write_text(text[:at] + "\n".join(blocks) + "\n" + text[at:])


def stage_docs(stage: Path, tours: Path) -> None:
    shutil.copytree(ROOT / "docs", stage, ignore=shutil.ignore_patterns(*STAGE_DROP))
    index = stage / "index.md"
    text = index.read_text()
    # Cut the Developer Reference and Release Notes sections from the home page.
    text = re.sub(r"## Developer Reference.*", "", text, flags=re.S).rstrip()
    text = text.rstrip("-").rstrip() + "\n"
    index.write_text(text)
    inject_clips(stage, tours)
    (stage / "assets").mkdir(exist_ok=True)
    shutil.copy(ROOT / "website/assets/css/docs-clips.css", stage / "assets/docs-clips.css")


def build_docs(out: Path, tours: Path) -> None:
    cfg = yaml.load((ROOT / "mkdocs.yml").read_text(), Loader=yaml.SafeLoader)
    cfg["nav"] = [e for e in cfg["nav"] if not any(k in PUBLIC_NAV_DROP for k in e)]
    cfg.pop("exclude_docs", None)
    cfg["site_url"] = f"{SITE_URL}/docs/"
    cfg["repo_url"] = GITHUB_URL
    cfg["extra_css"] = ["assets/docs-clips.css"]
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        stage = tmp / "docs"
        stage_docs(stage, tours)
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
            (out / name).write_text(src.read_text().replace("{{SITE_URL}}", SITE_URL))
    (out / "CNAME").write_text(SITE_HOST + "\n")  # GitHub Pages custom domain
    (out / ".nojekyll").write_text("")
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


def fmt_time(t: float) -> str:
    return f"{int(t) // 60}:{int(t) % 60:02d}"


def make_poster(clip: Path, dst: Path, dur: float) -> None:
    """Thumbnail for a chapter clip: a frame a few seconds in, when the UI has settled."""
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{min(4.0, dur / 2):.2f}", "-i", str(clip),
                    "-frames:v", "1", "-vf", "scale=800:-2", "-q:v", "4", str(dst)], check=True)


def build_tour(out: Path, tours: Path) -> int:
    """Interactive tour page at /tour/, generated from each video's chapters.json + captions.json.

    Only the silent chapter clips are published (small); hero videos are not copied.
    Returns the number of chapters (0 = no rendered tours found, page skipped).
    """
    meta = json.loads((ROOT / "website/data/tour.json").read_text())
    steps, nav, transcript = [], [], []
    for v in meta:
        src = tours / v["id"]
        if not (src / "chapters.json").exists():
            print(f"tour: skipping {v['id']} (no render in {tours})")
            continue
        chapters = json.loads((src / "chapters.json").read_text())
        captions = json.loads((src / "captions.json").read_text())
        nav.append(f'        <h3>{html.escape(v["title"])}</h3>')
        transcript.append(f'      <h3>{html.escape(v["title"])}</h3>')
        for ch in chapters:
            clip = src / "clips" / f'{ch["id"]}.mp4'
            if not clip.exists():
                continue
            dst = out / "tour/media" / v["id"] / clip.name
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(clip, dst)
            dur = min(ch["clip"], ch["end"] - ch["start"], 20.0)
            make_poster(clip, dst.with_suffix(".jpg"), dur)
            caps = [
                {"start": round(c["start"] - ch["start"], 2), "end": round(c["end"] - ch["start"], 2),
                 "text": c["text"], "inClip": c["start"] - ch["start"] < dur}
                for c in captions if c["chapter"] == ch["id"]
            ]
            i = len(steps)
            slug = f'{v["id"]}/{ch["id"]}'
            steps.append({"slug": slug, "vtitle": v["title"], "title": ch["title"], "docs": v["docs"],
                          "clip": f'media/{v["id"]}/{clip.name}', "poster": f'media/{v["id"]}/{ch["id"]}.jpg', "dur": round(dur, 2), "captions": caps})
            nav.append(f'        <a href="#{slug}" data-step="{i}"><span class="n">{i + 1:02d}</span>{html.escape(ch["title"])}</a>')
            rows = "\n".join(
                f'          <li data-step="{i}" data-line="{k}" class="{"" if c["inClip"] else "late"}">'
                f'<time>{fmt_time(c["start"])}</time><span>{html.escape(c["text"])}'
                f'{"" if c["inClip"] else " <em>full video</em>"}</span></li>'
                for k, c in enumerate(caps)
            )
            transcript.append(f'        <h4 id="t-{slug.replace("/", "-")}">{i + 1:02d} · {html.escape(ch["title"])}</h4>\n        <ol>\n{rows}\n        </ol>')
    if not steps:
        return 0
    page = (ROOT / "website/templates/tour.html").read_text()
    data = json.dumps(steps).replace("</", "<\\/")
    for k, val in {"{{NAV}}": "\n".join(nav), "{{TRANSCRIPT}}": "\n".join(transcript), "{{DATA}}": data,
                   "{{COUNT}}": f"{len(steps):02d}", "{{GITHUB_URL}}": GITHUB_URL, "{{SITE_URL}}": SITE_URL}.items():
        page = page.replace(k, val)
    (out / "tour").mkdir(exist_ok=True)
    (out / "tour/index.html").write_text(page)
    return len(steps)


def default_tours_dir() -> Path:
    final = ROOT / "tours/out"
    return final if (final / "overview/chapters.json").exists() else ROOT / "tours/draft"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / "greenjon"))
    ap.add_argument("--tours", default=None, help="rendered tour root (default: tours/out, else tours/draft)")
    args = ap.parse_args()
    out = Path(args.out).resolve()
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    tours = Path(args.tours).resolve() if args.tours else default_tours_dir()
    build_docs(out, tours)
    build_landing(out)
    n = build_tour(out, tours)
    if n:
        (out / "sitemap.xml").write_text((out / "sitemap.xml").read_text().replace(
            "</urlset>", f"  <url><loc>{SITE_URL}/tour/</loc><lastmod>{date.today()}</lastmod></url>\n</urlset>"))
    print(f"Tour page: {n} chapters.")
    print(f"Site built in {out}. Publish with scripts/publish_site.sh.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
