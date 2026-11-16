#!/usr/bin/env python3
"""Automatic audit checks (docs/developer/audit.md, "Automatic checks").

Each check is a finding type that came back in more than one audit. Run from the repo root:

    python3 scripts/audit/checks.py            # all checks
    python3 scripts/audit/checks.py ui_terms   # one check

Exit status is 1 if any check reports a problem. A source line can opt out of a check with a trailing
`// audit-allow: <check>` comment saying why it is fine.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "src/main/kotlin"
DOCS = ROOT / "docs"

# Banned terms in user-visible strings: (pattern, replacement). Keep in step with the table in audit.md.
BANNED_TERMS = [
    (re.compile(r"\bplay ?queue\b", re.I), "A/B Queue"),
    (re.compile(r"\bProperties\b"), "Modulation (if it means the modulation panel)"),
    (re.compile(r"\bAlpha fader", re.I), "Level"),
    (re.compile(r"\bblend mode\b", re.I), "nothing: transition blend modes were removed"),
    (re.compile(r"\bLibrary of Presets\b", re.I), "Library"),
    (re.compile(r"\bFlux\b"), "Hits"),
    (re.compile(r"\bbackground (FX )?queue\b", re.I), "BG Queue"),
    (re.compile(r"\bTrans queue\b", re.I), "Transition Queue"),
    (re.compile(r"\bDeep Edit\b", re.I), "Edit"),
]

STRING_LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
MD_LINK = re.compile(r"\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")


def allowed(line: str, check: str) -> bool:
    return f"audit-allow: {check}" in line


def kotlin_files():
    return sorted(SRC.rglob("*.kt"))


def rel(p: pathlib.Path) -> str:
    return str(p.relative_to(ROOT))


def check_release_notes_parity():
    """RELEASE_NOTES.md and docs/release_notes.md must say the same thing."""
    a = [l.rstrip() for l in (ROOT / "RELEASE_NOTES.md").read_text().splitlines()]
    b = [l.rstrip() for l in (DOCS / "release_notes.md").read_text().splitlines()]
    if a == b:
        return []
    import difflib
    diff = list(difflib.unified_diff(a, b, "RELEASE_NOTES.md", "docs/release_notes.md", n=0, lineterm=""))
    return ["release notes differ:"] + diff[:60] + (["..."] if len(diff) > 60 else [])


def check_doc_links():
    """file:/// and absolute links, relative links to missing files (archive excluded), and docs pages missing from mkdocs nav."""
    out = []
    md_files = [m for m in sorted(DOCS.rglob("*.md")) if "archive" not in m.parts] + sorted(ROOT.glob("*.md"))
    for md in md_files:
        for n, line in enumerate(md.read_text().splitlines(), 1):
            for target in MD_LINK.findall(line):
                if target.startswith(("file:", "/")):
                    out.append(f"{rel(md)}:{n}: absolute or file:/// link {target}")
                    continue
                if re.match(r"^[a-z]+:", target) or target.startswith("#"):
                    continue
                path = target.split("#")[0]
                if path and not (md.parent / path).exists():
                    out.append(f"{rel(md)}:{n}: link to missing file {target}")
    nav = (ROOT / "mkdocs.yml").read_text()
    for md in sorted(DOCS.rglob("*.md")):
        r = str(md.relative_to(DOCS))
        if r.startswith("archive/"):
            continue
        if r not in nav:
            out.append(f"docs/{r}: not in mkdocs.yml nav (add it or move it to docs/archive/)")
    return out


def check_ui_terms():
    """Banned terms inside Kotlin string literals."""
    out = []
    for f in kotlin_files():
        for n, line in enumerate(f.read_text().splitlines(), 1):
            if allowed(line, "ui_terms") or line.lstrip().startswith(("//", "*", "import ", "@")):
                continue
            for lit in STRING_LITERAL.findall(line):
                for pat, use in BANNED_TERMS:
                    m = pat.search(lit)
                    if m:
                        out.append(f"{rel(f)}:{n}: \"{m.group(0)}\" -> {use}")
    return out


def check_raw_tooltips():
    """Tooltips must go through ui/TooltipHelper.kt."""
    out = []
    for f in kotlin_files():
        if f.name == "TooltipHelper.kt":
            continue
        for n, line in enumerate(f.read_text().splitlines(), 1):
            if allowed(line, "raw_tooltips"):
                continue
            if re.search(r"ImGui\.(setTooltip|beginTooltip|setItemTooltip)\b", line):
                out.append(f"{rel(f)}:{n}: raw ImGui tooltip; use itemTooltip/showTooltip/controlTooltip")
    return out


CHECKS = {
    "release_notes_parity": check_release_notes_parity,
    "doc_links": check_doc_links,
    "ui_terms": check_ui_terms,
    "raw_tooltips": check_raw_tooltips,
}


def main(argv):
    names = argv or list(CHECKS)
    failed = False
    for name in names:
        problems = CHECKS[name]()
        status = "ok" if not problems else f"{len(problems)} problem(s)"
        print(f"== {name}: {status}")
        for p in problems:
            print(f"   {p}")
        failed |= bool(problems)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
