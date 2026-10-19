# greenjon.com website plan (2026-10-09)

Decisions: SUPERSEDED 2026-10-19: site is self-hosted on GitHub Pages at liquid-lsd.greenjon.com (CNAME from the NearlyFreeSpeech-hosted greenjon.com DNS to greenjon.github.io), built output lives in the separate greenjon/liquid-lsd-site repo (deploy workflow in website/deploy/pages.yml), published with scripts/publish_site.sh (--squash resets that repo's history so re-rendered videos don't bloat it). Originally: site at greenjon.com root, FTP'd by hand. Real screenshots (via --screenshot-ui),
video wanted, dev docs hidden (not on site). Beta line RETIRED: releases are v0.9.N (latest v0.9.113).
Hybrid downloads: 5 latest-build buttons (one per OS) + prominent GitHub link (build from source, earlier versions).

Download model: stable URLs https://github.com/greenjon/liquid-lsd/releases/latest/download/liquid-lsd-{linux-x64,linux-arm64,macos-x64,macos-arm64,windows-x64}.zip
(asset names are version-free). Version label fetched client-side from api.github.com releases/latest, with static fallback text; no rebuilds needed per release.

Steps
1. Pipeline: scripts/build_website.py -> builds website/ templates + mkdocs (user guide only, no developer/release notes) into greenjon/ (gitignored output; deploy = scripts/publish_site.sh). Track script + templates.
2. Landing rewrite: benefit-led copy, drop jargon (zero-alloc, LWJGL, GC, ringbuffers), current feature set, honest "0.9 pre-release", GPL-3.0 named.
3. Visuals: real screenshots (Perform/Edit/Library, FX chains, Web TV), video/GIF of a live set; delete SVG mockups, shrink app_icon.png.
4. Docs: current user_guide only; OS auto-detect highlight on download buttons.
5. Hygiene: robots.txt, sitemap, 404, OG/Twitter tags, unique titles, a11y (aria-expanded, button screenshots, lightbox focus), mobile check; drop/regen docs.zip without dev docs.
