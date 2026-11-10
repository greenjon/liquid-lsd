# VJ app plan: Liquid LSD for Android

A general-purpose, audio-responsive Android VJ app built on the same visuals as the web TV and the Spaz Radio
app. Spaz Radio stays as it is (no feature creep); the VJ app is a separate app.

## Decisions (2026-11-09)

- Separate app, same visuals: web TV, Spaz Radio and the VJ app share one engine and one content set.
- Content arrives at runtime (continuous development), with a bundled snapshot so the app works offline.
- The VJ app follows desktop live broadcasts, as the TV does.
- Audio: microphone, or whatever the phone is playing (system audio capture).
- Video out over USB-C (and anything else Android exposes as a second display).
- Name **Liquid LSD** (the desktop app's player: it shows, it doesn't edit), app ID **`lsd.liquid`**, own repo
  `~/AndroidStudioProjects/liquid-lsd-android` (2026-11-10). GPL-3.0 for now (user leaning GPL).
- Open: licensing (see the licensing-intent memory; the VJ app is the first general product on
  the engine, so this has to be settled before its release); which phone has DisplayPort over USB-C.

## What already exists (no need to build)

| Piece | Where | Notes |
|---|---|---|
| Engine | `web/` (JS + WebGL2) | One implementation. `?embed` = picture only, driven through `window.LSD` (`web/embed.js`). |
| Signed runtime content updates | liquid-lsd `scripts/make_content_manifest.py` (published by `publish_web_tv.sh` to `/tv/update/`), spazradio `visuals/ContentUpdater.kt`, `ContentStore.kt`, `ContentUpdates.kt` | ECDSA-signed manifest of data files (no code), sha256 per file, `minEngine` gate, atomic swap, overlay over bundled assets, daily WorkManager job. **Runtime fetch is already done**; it only has to move into the shared module. Photos are not part of it. |
| WebView host | spazradio `ui/components/VisualizerView.kt` (299 lines) | Made-up https origin served from assets + ContentStore overlay, `LSDHost` bridge (`ready`, `failed`), startup timeout, render-process-gone restart, battery-saver quality, pause when hidden. |
| Audio feed | spazradio `visuals/PcmTap.kt` | Mono 16-bit chunks of 40 ms to `LSD.feed()`, base64 over `evaluateJavascript`. |
| Settings | spazradio `visuals/VisualsSettings.kt`, `ui/components/VisualsSettingsSection.kt` | quality, reactivity, reduced motion, live, sync, content updates. |
| Offline web TV | `web/sw.js` (TV v31, 2d5e876) | Every scene cached after one visit, kept photos, microphone offline. |

So the shared library is mostly an extraction of about 800 lines of spazradio Kotlin plus the bundled assets.

## Shape

```
liquid-lsd/
  web/                      engine + content (unchanged role)
  android/lsd-visuals/      NEW Android library module: WebView host, LSD bridge, content updater,
                            PCM chunker, settings model, bundled web/ snapshot as assets
spazradio/                  depends on lsd-visuals; keeps its own settings panel and radio
vj-app/ (new repo)          depends on lsd-visuals; native Compose controls, audio inputs, external display
```

**Distribution of lsd-visuals: a committed copy of the module's source in each app** (changed from the
first draft's committed Maven repo, 2026-11-09). `scripts/sync_lsd_visuals.sh <app-dir>` copies
`android/lsd-visuals/` into `<app-dir>/lsd-visuals/` with the web engine as its assets and the liquid-lsd commit
in `VERSION`; the app has `include(":lsd-visuals")` and `implementation(project(":lsd-visuals"))`. Same model as
the old `sync_visuals.sh` (builds never need a liquid-lsd checkout), but with no publishing step, no binary in
the repo, and the app's dependency locking sees ordinary dependencies. The copy must not be edited in the app.

Versioning: `VERSION` (the liquid-lsd commit) identifies a copy; `ContentUpdater.ENGINE` must match
`CONTENT_ENGINE` in `make_content_manifest.py`, so an app knows which content it can take.

## Status

- 2026-11-09 phase 1 built: `android/lsd-visuals/` + `scripts/sync_lsd_visuals.sh`; spazradio branch
  `lsd-visuals-module` uses it (old engine 1543ea9 kept, so the branch changes structure only). Unit tests, lint,
  debug and release builds pass; the release dex keeps the bridge. Run on an emulator (AVD `lsd_test`, Pixel 7,
  Android 15, `-gpu host`; swiftshader crashed): picture at 60 fps, radio feed, pause/resume, relay, content
  update; upgrade from the 2.0.0 code runs the new worker with no errors. Not yet on a real phone.
- Emulator: `~/Android/Sdk/emulator/emulator -avd lsd_test -no-window -no-audio -gpu host`; launch the app with
  `adb shell monkey -p <package> -c android.intent.category.LAUNCHER 1` (`am start` misreports here).

- 2026-11-10 phase 2 built: the control API and state push in `web/embed.js` (see web_subsystem.md 6b),
  `tests/web/embed.spec.ts`; not published to spaz.org (nothing on the TV uses it). The lsd-visuals module
  doesn't expose it yet: `LSDHost.state` and a command helper come with phase 3.

- 2026-11-10 phase 3 first version: repo liquid-lsd-android 230cede (local, no remote yet). lsd-visuals gained
  `LsdControl`, `LsdState`, `LsdCatalog` and the `LSDHost.state` bridge (liquid-lsd e0befbc); package renamed
  `lsd.liquid.visuals` (Spaz Radio branch resynced, b699b73, now also carrying the current web engine). Emulator:
  picture, every tab, scene/hold/time controls, mic and phone-audio services start (no real sound in the
  emulator), control settings and the channel survive a restart. Not on a phone.
- 2026-11-10 phase 4 built: `LsdPlayer` + `LsdPresentation` in lsd-visuals; the app's `ExternalOutput` moves the
  picture to the first presentation display (one renderer, one feed), shows a 2 fps preview and the display's name
  and mode on the phone, and brings the picture back on unplug; Audio tab switch "Picture on the external display".
  Emulator with overlay displays (720p, 1080p): plug, unplug, replug, output keeps running with the app in the
  background. Found and fixed on the way: `LsdVisuals` didn't pause when the activity stopped (no recomposition
  while stopped). Not on real USB-C or Miracast hardware. Embed's 1280x720 render budget caps the external picture;
  a 1080p/4K setting is open. Repo greenjon/liquid-lsd-android created PRIVATE (a public create was blocked by
  the permission rules; the user can flip it).

## Phases

### 1. Extract lsd-visuals (no behaviour change) — built 2026-11-09

1. `android/lsd-visuals/` in liquid-lsd (no Gradle build of its own; it builds and tests inside an app).
   Package `lsd.liquid.visuals`.
2. Moved in from Spaz Radio: `VisualizerView`, split into `LsdPage` (the plain WebView, for a `Presentation`
   later) and `LsdVisuals` (Compose); `AssetClient`, the bridge (`HostBridge`), `ContentStore`, `ContentUpdater`,
   `ContentUpdates` (server and key unchanged: both apps use spaz.org/tv), `PcmTap`/`PcmMonoChunker`,
   `VisualsSettings`, and their unit tests. The background job got a new name (`lsd-visuals-content-update`;
   the old one is cancelled, because its worker class no longer exists).
3. Assets: `scripts/sync_lsd_visuals.sh APP_DIR [--no-gallery]` writes them into the app's copy.
4. Spaz Radio uses the module; its `scripts/sync_visuals.sh` now calls liquid-lsd's script.
5. Verify: unit tests, lint, debug + release builds (done); a run on the phone (picture, sync, pause, content
   update, live follow) done on the emulator, not on a phone. Release only when the user wants one.

### 2. Control API in the engine (`web/embed.js`) — built 2026-11-10

`window.LSD` today: `feed`, `setOption`, `pause`, `resume`, `next`, `prev`, `hold`. The VJ app needs a control
surface comparable to the web TV's viewer controls. Most of it exists inside `ui.js`/`autopilot.js` and only
needs exposing:

- `catalog()`: scenes (playlist lines with names, tags, channel numbers), FX chains, transitions, backgrounds.
- Scene: `next`, `prev`, `channel(n)`, `scene(name)`; transition pick; ROLL (re-roll the current scene).
- Automatic or manual per layer: foreground (auto-advance on/off, TIME in bars), background (`hold` /
  with scenes / off, step, pick), effects (on/off, next chain, pick chain, auto rotation on/off).
- Follow music (tempo picks, calm/drop looks) on/off; skipped channels.
- `LSDHost.state(json)` pushed on every change (current scene, chain, background, BPM, beat lock, live,
  layer modes), so native controls show the truth without polling.
- Locked while following a live broadcast, as on the TV; state says so.

Tests: extend `web/checks/embed_check.mjs` and add `tests/web/embed.spec.ts` (drive every command, read state).
Spaz Radio ignores the new calls, so it doesn't need an update for this.

### 3. VJ app, first usable version

- Repo `liquid-lsd-android`, app ID `lsd.liquid`, Kotlin + Compose, minSdk 29 (system audio capture needs Android 10; about 95% of active phones).
- **Screen**: the picture full screen with controls over it (auto-hide like Spaz Radio's), or a split layout:
  picture on top, a control deck below. Controls: scene browser (catalog with tags), next/prev/roll,
  auto/manual switches per layer, TIME, background picker, effects picker, REACT, SYNC, live follow, tap tempo.
- **Audio input** (foreground service so it keeps running with the screen off or another app in front):
  - Microphone: `AudioRecord`, raw (`UNPROCESSED` or `VOICE_RECOGNITION` with effects off), service type
    `microphone`.
  - System audio: `MediaProjection` consent + `AudioPlaybackCaptureConfiguration` (USAGE_MEDIA, GAME,
    UNKNOWN), service type `mediaProjection`. Limits shown in the UI: consent every session, and apps that
    opt out (many streaming apps) give silence; the input meter makes that obvious, with a hint to use the
    microphone.
  - Both go through `PcmMonoChunker` to `LSD.feed()`; a level meter in the UI.
- **Content**: the library's updater (daily, plus on app start when on Wi-Fi), the bundled snapshot offline.
- **Live**: `setOption('live', true)` by default.
- Privacy: audio is analysed on the phone only, never recorded or sent; say so in the permission prompts and
  the store listing.

### 4. External display (USB-C, Cast, Miracast) — built 2026-11-10

- `DisplayManager.DisplayListener` + `MediaRouter`'s presentation display; when a second display appears,
  offer "Output to <display>" (or do it automatically, a setting).
- The WebView moves to a `Presentation` on that display at its native size (cap at 1080p by default, 4K as a
  setting, since the GPU drives both); the phone shows the controls and a small, low-rate preview (a canvas
  snapshot of the picture a few times a second, not a second renderer).
- Keep the external display awake, handle unplugging (picture returns to the phone), overscan/fit options,
  black background for projectors.
- Testing without hardware: Developer options › Simulate secondary displays (or
  `adb shell settings put global overlay_display_devices 1920x1080/320`) gives a real `Display` for the
  Presentation code. Real DP alt mode: Samsung (DeX phones), Pixel 8 and later on recent Android; check the
  user's phone.

### 5. Release

Store listing, screenshots, privacy text; name and licence decided; signing the same way as Spaz Radio (master
key local, never in CI secrets). Docs: a user guide page for the app, `docs/developer/` page for lsd-visuals.

## Later (not v1)

- MIDI controllers through `android.media.midi` (the Twister mappings from the desktop are an obvious start).
- The user's own photos as the gallery background.
- User playlists and favourites in the app, synced with nothing.
- Recording the output.

## Risks

- **WebView GPU limits**: two displays and 4K on a phone GPU; the adaptive resolution and the GPU probe already
  exist, measure on real hardware early in phase 4.
- **System audio capture coverage**: many popular apps block it; the microphone is the dependable path.
- **Engine/content skew**: content needs a newer engine than an installed app has. Already handled by
  `minEngine`; keep bumping `CONTENT_ENGINE` when content starts relying on something new.
- **A bad content publish reaches every app**: the signature and hashes guard integrity, not quality. The web
  TV is the canary (it gets the same files first); keep `node --test web/tools/content.test.mjs` in the publish
  path (it is).
