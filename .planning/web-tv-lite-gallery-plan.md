# Web TV lite mode: gallery fallback for weak devices (plan, 2026-11-22)

Status: phase 1 built 2026-11-22 (uncommitted, unpublished, TV v49): lite.js, manual VISUALS row, no-WebGL/error fallback, Safari 15 floor (compat.test.mjs). Next: phase 2 (slow-device auto-switch, remembered per device; embed hands off via failed()). Old-browser bootstrap dropped (user: no 2017-2020 support).

## Problem

Very old phones/tablets/laptops either have no WebGL 2 (renderer.js `alert`s and stops: the screen stays black)
or render at ~1 fps. spaz.org/radio, which the TV replaces, showed listener photos spinning and zooming behind
the player; that worked on anything, but it loads every upload at full size (hundreds of MB) through jQuery
animations of 8000 px images.

## Goal

A "lite" picture for those devices: the curated Spaz gallery (the same 129 photos as web/gallery/), with the
old page's look (photos fading in over each other, slowly swinging, turning and zooming), drawn without WebGL,
cheap on CPU, and a few MB per visit instead of hundreds. Everything else on the TV (radio, OSD, menu, schedule,
chat, tapes, EQ) keeps working.

## Design

### 1. Small images (bandwidth)
- `scripts/gallery_curate.py build` also writes `web/gallery/s/<name>.jpg`: 512 px long side, progressive JPEG,
  q≈70 (expect 25-45 KB each, ~4-5 MB for all 129; the 1024 px originals are 16 MB). JPEG, not WebP: old Safari
  (<14) has no WebP.
- gallery.json gains `"lite": "gallery/s/"`. No new manifest.
- Loading: at most 3 photos on screen and 2 decoded waiting; the next one is fetched only when one is needed
  (about one every 8 s), in shuffled order. Over a 20-minute session that is the whole set once (~4-5 MB), then
  the browser cache. A node test caps each lite file at 60 KB and the folder at 6 MB.

### 2. Drawing (no WebGL)
- New module `web/lite.js`: a `<div id="liteLayer">` over `#glCanvas` inside `#tv-screen-area` (under the OSD
  and the CRT overlay elements). Each photo is an `<img>` moved only with CSS `transform`
  (translate/rotate/scale) and `opacity`, using CSS transitions with long durations, so the browser's compositor
  animates it and JS wakes up only to start the next photo (no per-frame JS, no rAF loop).
- Motion copied from gallery.js's tuned values, not the old code: new photo every 2-8 s (on a beat when the
  beat tracker is running, otherwise wall clock), fade-in 6-16 s, a steady turn of up to ~200° around a point
  off the photo's middle and a slow zoom, newest on top, old one fades out, then its `<img>` is removed
  (frees the decoded bitmap).
- Soft edges with `mask-image` radial/linear gradient where supported; plain edges elsewhere.
- Faint CRT feel without shaders: a static scanline PNG/CSS gradient overlay and the existing bezel. No snow.
- Reduced-motion setting: crossfade only, no spin.

### 3. When lite mode starts
In order of precedence:
1. Manual: MENU › VISUALS: FULL / LITE (and `?lite=1` / `?lite=0`), saved in localStorage.
2. No WebGL 2, or renderer init fails (shader compile/link error): lite at once. The `alert()` goes.
3. Too slow: the adaptive scaler is already at its coarsest step (0.4) and the frame-time average stays above
   ~1/12 s for 8 s (ignoring the first 5 s and hidden-tab time). Then: stop the rAF loop, call
   `WEBGL_lose_context.loseContext()` to free the GPU, start lite, OSD "LITE MODE · MENU › VISUALS for full".
   The device is remembered (`lsd.visuals = 'lite-auto'`), so the next visit starts lite without downloading
   shaders/presets; MENU › VISUALS › FULL clears it and tries again.

Embed (Spaz Radio app) is unchanged: it already calls `LSDHost.failed()` and the app shows its own visuals.
Optionally the slow path also calls `failed()` in embed mode (decision below).

### 4. Not downloading the heavy parts in lite
- renderer.js `init()` checks the lite flag first and returns before fetching catalog/presets/shaders; the
  autopilot never starts. Lite code is imported dynamically.
- index.html's `<link rel=preload>` of shaders/presets and the 25 `modulepreload`s: move the shader/preset/
  catalog preloads into a tiny inline script that adds them only when not lite (the JS modules stay; ~440 KB
  total, cached by sw.js).
- sw.js SHELL adds lite.js; it does not precache gallery/s/ (would defeat the bandwidth goal).
- CH/SCENE keys in lite: CH ± skips to the next/previous photo; SCENE line reads "GALLERY · LITE".

### 5. Very old browsers without ES modules (optional, phase 3)
A `<script nomodule>` fallback that runs the same slideshow (ES5, ~3 KB) plus a plain `<audio controls>` of the
stream. Covers pre-2017 browsers that currently get nothing at all.

## Phases
1. Images + lite.js + manual toggle + no-WebGL2 path. TV vNN.
2. Slow-device auto-switch + remembered choice + preload gating.
3. (optional) nomodule ES5 fallback.

## Verification
- ui_check: `?lite=1` shows the layer, no shader/preset requests in the network log, OSD + audio controls work.
- Forced no-WebGL page (getContext('webgl2') → null) lands in lite with no alert.
- Slow path: Chromium with `--disable-gpu --use-angle=swiftshader` under Xvfb at a large window should drop to
  lite on its own; check it does not trigger on this machine's real GPU.
- Bandwidth: record bytes transferred over 10 min in lite (target < 5 MB first visit, ~0 cached).
- CPU: lite idle CPU in Chromium task manager on a throttled (6x) CPU profile.
- Docs: release notes (both files), user guide TV section, docs/developer/web_subsystem.md, menu tooltips,
  TV_VERSION bump.

## Decisions (user, 2026-11-22)
1. Slow threshold: below ~12 fps for 8 s at the lowest scale. Agreed.
2. Auto-lite is remembered per device.
3. Logos: DROPPED (2026-11-22, user: the logo dir is a leftover of an older radio page; moot). Photos only.
   Old note: Logos on top: yes. The old page's logo cycler (`logoCycler`) faded a random logo in over 0-10 s while
   resizing it to 70-570% width with a CSS spin. NOTE: the live /radio page's logo list is currently EMPTY
   (`imageURLs.logo.urls = []`), and spaz.org/images/logo/ is a 404, so the logo images must come from the user.
4. Embed: the slow path also calls `LSDHost.failed()` so the app shows its own visuals.
5. Old-browser fallback: still being discussed. The real cutoff is ~2020, not 2017: the code uses `?.` (19 files)
   and `??=` (4), which are syntax errors before Safari 14 / Chrome 85 / Firefox 79. Such a browser loads
   nothing at all, lite.js included, if lite.js sits in the same module graph. Needs a classic-script
   feature-test bootstrap (see answer in chat).

## Browser floor findings (2026-11-22)
The full TV's real floor is ~2022, not 2020: besides `?.`/`??=`/`#private` syntax (Safari 14.1, Chrome 85),
it calls `.at()` (schedule.js, autopilot.js, sync.js), `findLast` (autopilot.js) and `AbortSignal.timeout`
(graph.js, autopilot.js), which need Safari 15.4/16 and Chrome 97/103. iOS 15 devices (iPhone 6s/7, iPad Air 2,
iPad mini 4: capped at iOS 15) HAVE WebGL 2 but break on these calls (preset fetches fail).
Proposal (awaiting user):
- A. Replace those three APIs with small helpers + a node test that bans post-floor APIs -> floor ~Safari 14.1 /
  Chrome 85; iOS 15 devices get the full visuals. Cheap.
- B. Optional esbuild legacy bundle (target ~chrome61/safari12, IIFE) chosen by the bootstrap -> reaches
  2017-2020 Chromium/Firefox with WebGL 2 (old Android, smart TVs, frozen desktops). iOS 12-14 gains nothing
  (no WebGL 2 before Safari 15) and goes lite. Decide from spaz.org access-log user agents.
- Hand rewrite to ES5: rejected (440 KB of code, no async/classes/modules; a build tool does the same job).
