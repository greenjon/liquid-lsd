# Web TV Audit

How we audit the web TV at https://spaz.org/tv/. It covers `web/`, `server/`, `scripts/publish_web_tv.sh`,
`scripts/make_content_manifest.py`, `tests/web/` and `playwright.config.ts`. This is lane 6 of the
[Audit Blueprint](audit.md), written out as a page of its own because the TV ships on its own schedule: about twenty
TV versions went out between the 2026-11-15 audit and the next one.

## When to run it

- As lane 6 of every full audit.
- On its own, every ten TV versions or so (compare `TV_VERSION` in `web/ui.js` with the last baseline).
- Before every Spaz Radio app release, since the app embeds the TV and fetches its content (see W7).
- After a change that touches the audio path, the relay or the service worker, whatever the version count.

A standalone run uses the same goal, finding format, severity levels and method steps as the full audit
([audit.md](audit.md)). Only the lanes, the baseline tag and the agent split differ.

## Baseline

The TV baseline is the newest of the full-audit tags (`audit-YYYY-MM-DD`) and the TV-only tags (`tv-audit-YYYY-MM-DD`):

```
git describe --tags --match 'audit-*' --match 'tv-audit-*' --abbrev=0
```

TV-only tags start with `tv-` so that the full audit's own `--match 'audit-*'` lookup does not pick them up.
A full audit covers the TV, so its tag also counts as a TV baseline.

| Audit | Baseline tag | TV version at close | Notes |
|---|---|---|---|
| 2026-11-15 (full, lane 6) | `audit-2026-11-15` | v47 | report at `.planning/audit-2026-11-15.md`, findings L6-1..16 |

Scope a run with `git log --oneline <tag>..HEAD -- web server scripts/publish_web_tv.sh scripts/make_content_manifest.py tests/web playwright.config.ts`
and list the TV versions it spans (`git log --oneline <tag>..HEAD | grep -o 'TV v[0-9]*'`).

## Lanes

Each lane has a fixed checklist. As in the full audit, the reviewer works through every item and records the clean
ones too, so we can tell "checked and clean" apart from "not looked at". Findings are numbered `W<lane>-<n>`
(`L6-<n>` when the run is part of a full audit).

### W1. Correctness and lifecycle

Read every changed file under `web/` and `server/`, in full where the change touches control flow.

- **Audio thread:** `feed-worklet.js` and `onset-worklet.js` allocate nothing in `process()`: no arrays, closures or
  string building. Messages to and from the worklet are not sent every block.
- **Per frame:** no allocation in the render loop (`renderer.js`, `graph.js`, `gallery.js`, `lite.js`, `mandala.js`):
  no `new Float32Array`, array literals, `map`/`filter`, template strings or object spreads per frame.
- **Timers and listeners:** every `setInterval`, `setTimeout` chain, `requestAnimationFrame` loop, event listener,
  `MediaSource`/`<audio>` element and WebSocket is released when the TV powers off, switches VISUALS in place, goes to
  the lite picture, or is paused by the host app. Look for a second copy running after an off/on cycle.
- **Power off:** the loop idles once the screen is black (TV v67), and the wake lock is released (L6-8).
- **WebGL:** context loss and restore are handled (L6-7). A shader that fails to compile or a preset that fails to
  load is skipped, not aired under its own name (L6-9).
- **Promises:** no unhandled rejections. Every `fetch` has a timeout or an abort path and a visible failure state.
- **Stored settings:** values read from `localStorage` survive old, missing or corrupt entries, and a new setting
  keeps a choice the viewer already made (the v66 EFFECTS change is the pattern).
- **Relay (`server/`):** the same checks for the Node side: closed sockets are dropped, a replaced broadcaster's late
  `close` does not drop the new one (L6-1), and memory does not grow with connections.

### W2. Browsers and devices

| Target | How it is covered | Notes |
|---|---|---|
| Chromium desktop | Playwright `chromium` | |
| Firefox desktop | Playwright `firefox` | autoplay prefs are opened up in the config, so autoplay blocking is untested there |
| Phone (Pixel 7 profile) | Playwright `phone` | Chromium with a phone viewport and touch, not a real phone |
| WebKit / Safari | Playwright `webkit`, only with `WEBKIT=1` | does not launch on this Fedora machine (L8-2), so in practice untested |
| Safari 15 (the floor) | none | no syntax or API newer than Safari 15.0 |
| iPhone Safari | owner's device | side feed (v58), autoplay, fullscreen |
| Spaz Radio app (Android WebView) | `embed.spec.ts`, `web/checks/embed_check.mjs`, owner's phone | see W7 |

- List every new language feature and web API since the baseline and check it against Safari 15.0 and the Android
  System WebView versions the app supports. Features that arrived in Safari 15.4 or later (`Array.prototype.at`,
  `structuredClone`, `:has()`, `dialog`) need a fallback or must not be used.
- The lite picture takes over on devices too slow for the visuals (v50), and MENU › TV › VISUALS switches both ways
  without a reload (v51).
- Layout holds at 400px wide, in portrait and landscape phone sizes, at 1280×720 and at 4K, in fullscreen and in the
  embed.
- Touch: every control works without hover, and no hover-only hint hides needed information.

### W3. Audio

- **Autoplay:** MENU › TV › AUTOPLAY *Ask*, *On* and *Off* each behave as documented in Chromium, Firefox (with
  autoplay blocking on) and Safari. A blocked start shows a way to start, never silence with no prompt.
- **Live stream:** every power-on asks for a fresh stream address (v63). A stalled or dropped stream reconnects with
  backoff and shows its state. No tight retry loops.
- **Safari:** the side feed lets the picture hear the live radio on iPhone (v58). The volume dial sets the stream's
  own volume, and MENU › EQ is greyed out on live radio (v64).
- **Tapes:** play, seek, share links (`?tape=<id>&t=`) and ⤓ SAVE (including the `archives/imports/` and `olde/`
  path fallback, v59/v60).
- **Microphone:** `getUserMedia` (`dsp.js`) is only called after an explicit viewer action, never in the embed, and
  the stream is stopped when the viewer leaves mic mode. No other capture device is touched.
- **Signals:** audio gain, hits and tempo still feed the picture after each audio-path change (`beat_check`,
  `tempo_report.mjs`).

### W4. Network and external services

For each service, check what the viewer sees when it is down, slow, returns 404, or returns malformed data. Each
should fail visibly, retry with backoff, and leave the rest of the TV working.

| Service | Used by | Notes |
|---|---|---|
| Icecast streams and status on `radio.spaz.org` | `dsp.js`, `sidefeed.js`, listener count | listener count sums every mount |
| `streams/playing-jsonp` | now-playing, [LIVE!] | JSONP: served as `text/javascript`? (L6-13, spaz.org admin) |
| Schedule | `schedule.js` | |
| Show archive (`olde/`, `archives/imports/`) | `tapes.js`, `tapedeck.js` | `olde/` paths often 404 |
| MQTT chat broker `wss://radio.spaz.org:1885/mqtt` | `chat.js`, `chatboard.js` | |
| Live relay `wss://spaz.org/lsd-relay` | `autopilot.js`, `sync.js` | runs `server/server.js` on the VPS |
| Content manifest `/tv/update/` | Spaz Radio app | see W6 and W7 |

- Nothing relies on CORS headers we don't control. No mixed content.
- No requests to hosts other than the ones in this table (fonts and libraries are local). A new host is a finding.

### W5. Security and privacy

- **Chat:** text from the broker is never put into the page as HTML. Check every `innerHTML` in `web/` and confirm its
  input is our own. Nickname and message length are capped. A flood of messages cannot freeze the page.
- **URL parameters:** every parameter (`?tape`, `?t`, `?embed`, and any added since the baseline) is validated. A bad
  value falls back to the default and cannot reach a URL, a selector or HTML unescaped.
- **Host bridge:** `window.LSD` and `window.LSDHost` (`embed.js`) do only what the app needs. Nothing a page or chat
  message can reach calls into the host.
- **Content:** presets, playlists, FX chains and shaders fetched from the server are treated as data. A broken file
  is skipped. The content manifest is signed, and the app checks the signature.
- **Relay:** who may broadcast, message size limits, and per-connection caps in `server/server.js`. A viewer cannot
  push state to other viewers.
- **Stored data:** `localStorage` and the service worker caches hold settings and public files only.
- **Privacy:** the Spaz Radio app's privacy policy still describes what the TV does (chat, mic, downloads, stored
  settings).

### W6. Publishing and the live copy

- Live matches the repo: `scripts/publish_web_tv.sh --dry-run` lists no changed files. Any difference means someone
  published an uncommitted working tree (the script uploads the working tree as it is) or forgot to publish.
- `TV_VERSION` in `web/ui.js` was bumped once for each publish that changed code. Every `TV vN` in the commit log
  appears once.
- `CACHE` in `web/sw.js` was bumped whenever the set of files the service worker caches changed. An installed copy
  of the TV picks up the new version.
- `scripts/publish_web_tv.sh --dry-run --delete` lists server files the TV no longer uses. Decide whether to remove them.
- The content manifest on the server is valid, signed, and lists only files that are on the server.
- The relay running on the VPS is the `server/server.js` in the repo. The owner checks this, since it is deployed by hand.

### W7. The Spaz Radio app contract

The app (`~/projects/spazradio`) loads the TV in a WebView with `?embed`, drives it through `window.LSD`
(`feed`, `pause`, `resume`, `catalog`, `state`), and fetches new presets and playlists from the content manifest.

- Every `window.LSD` call the app makes still exists with the same arguments and result shape. Grep the app for
  `LSD.` to get the current list.
- Content published since the baseline still loads in the oldest app version people are using: the engine
  vendored in that release, not the current `web/` code. Content that needs a newer engine must not be in the
  manifest. An old app already lost its logos this way after a content update.
- `embed.spec.ts` and `web/checks/embed_check.mjs` pass.
- Changes the app should copy are listed in the app's next-release to-dos (for example the v60 archive-download fallback).

### W8. Parity with the desktop

- The evaluator, DSP and tempo code behave like the desktop's. Golden vectors are current (`UPDATE_WEB_VECTORS=1`),
  and `scripts/sync_web.py` leaves no diff.
- Desktop files listed under `monitored_sources` in `web/sync_manifest.json` (the tempo tracker included) have not
  changed since the web port last caught up, or the web port has a matching change.
- Factory content the desktop ships from the web (`web-*` presets and chains) is the same on both sides.

### W9. Text, docs and release notes

- Every TV version since the baseline has an entry, tagged `(TV vN)`, in both `RELEASE_NOTES.md` and
  `docs/release_notes.md`.
- Every viewer control has a hover hint that matches what it does now. Removed controls and keys are gone from hints,
  menus and the about/keys page (v68 removed channel skip; v69 removed the B key and ANNOUNCE SCENES).
- The about/keys page (`?`) matches the actual key map, and each key matches its button's first letter.
- The dark and light themes both define every colour token in `web/tv.css`.
- The banned terms from the full audit's lane 5 do not appear in web UI text.
- `docs/developer/web_subsystem.md` matches the code, and the user docs about the web TV match the current menu.

### W10. Health and performance

- `node --test web/tools/*.test.mjs` and the Playwright suite (`npx playwright test`; `LONG=1` adds the long and radio
  tests). Record pass/fail counts, the slowest tests and anything flaky.
- Light checks: `web/checks/ui_check.mjs`, `shader_check.mjs`, `lite_check.mjs`, `embed_check.mjs`,
  `sidefeed_check.mjs`, `tape_check.mjs`, `beat_check.mjs`. `render_check.mjs` overheats the laptop: run it only
  with the owner's OK, and narrow it with `RC_ONLY`.
- `web/checks/gpu_bench.mjs` against the last recorded numbers, if a renderer or shader change is in scope.
- Memory over a long run (`long.spec.ts`) does not grow without bound. CPU is near zero with the TV off.
- First load: how much is fetched before the first scene shows (fast start, v37).

### W11. Licensing

- Every font, image, gallery photo, shader and npm package added under `web/` or `server/` since the baseline has a
  license record (`docs/licenses/`, `web/fonts/OFL.txt`) and keeps its attribution.
- Gallery photos and logos: permission from Spaz is on record.
- Shaders under `web/shaders/` have the same provenance status as their desktop copies.

## Checks only the owner can run

These need real devices or server access. List them in `.planning/app-checklist.md` under the web TV, merged with
what is already there:

- iPhone Safari: live radio with picture (side feed), autoplay prompt, fullscreen.
- Mac Safari: volume dial and greyed-out EQ on live radio.
- The Spaz Radio app on a phone, after the content manifest is published.
- The VPS: the deployed `server/server.js` matches the repo, and `playing-jsonp` is served as `text/javascript`.

## Who runs what

| Work | Agent type | Model |
|---|---|---|
| W1 + W3 (code correctness and audio) | `general-purpose` | session model (Opus) |
| W4 + W5 (network and security) | `general-purpose` | session model (Opus) |
| W2, W8, W9, W11 | `audit-docs` | Sonnet |
| W6, W7, W10 (scripts, git and test runs) | run directly | none |
| Fix groups | `audit-fixer` | Sonnet |

When the diff since the baseline is small (a few TV versions), one Opus reviewer can take W1, W3, W4 and W5 together.

## Automatic checks

Checks that would catch the findings above without a reviewer. Build each one when its finding shows up in a
second audit. The rule is the same as in the full audit.

| Check | Catches | Status |
|---|---|---|
| `checks.py tv_version` | `web/` code changed in a commit range with no `TV_VERSION` bump | proposed |
| `checks.py tv_release_notes` | a `TV vN` in the commit log with no release-notes entry | proposed |
| `checks.py tv_theme_tokens` | a colour token defined in one theme and not the other | proposed |
| `checks.py web_ui_terms` | banned terms in web UI strings | proposed |
| Safari 15 syntax check | language features newer than the floor (e.g. a parser set to the Safari 15 target) | proposed |
| Publish refuses a dirty tree | uncommitted changes going live | proposed (in `publish_web_tv.sh`) |
| `checkWebSync` in the build, covering `catalog.json` and `mandala_recipes.json` | desktop↔web drift (L6-15) | proposed, open since 2026-11-15 |
| `node --test web/tools/content.test.mjs` | broken content before publishing | exists (run by `publish_web_tv.sh`) |
