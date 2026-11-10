# VJ app plan (working name: "the VJ app")

A general-purpose, audio-responsive Android VJ app built on the same visuals as the web TV and the Spaz Radio
app. Spaz Radio stays as it is (no feature creep); the VJ app is a separate app.

## Decisions (2026-11-09)

- Separate app, same visuals: web TV, Spaz Radio and the VJ app share one engine and one content set.
- Content arrives at runtime (continuous development), with a bundled snapshot so the app works offline.
- The VJ app follows desktop live broadcasts, as the TV does.
- Audio: microphone, or whatever the phone is playing (system audio capture).
- Video out over USB-C (and anything else Android exposes as a second display).
- Open: the app's name; licensing (see the licensing-intent memory; the VJ app is the first general product on
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

**Distribution of lsd-visuals: a committed file-based Maven repo in each app.** `scripts/release_lsd_visuals.sh
<app-dir>` builds the AAR in liquid-lsd and publishes it (with its POM, so OkHttp/WorkManager/webkit come along)
into `<app-dir>/lsd-repo/`, which is committed. The app adds `maven { url = uri("lsd-repo") }` and
`implementation("org.liquidlsd:lsd-visuals:<version>")`. This keeps Spaz Radio's rule that builds never depend on a
liquid-lsd checkout (CI builds from the repo alone) and replaces `sync_visuals.sh`. Rejected: Gradle composite
build (needs the checkout), JitPack (an outside service in the build path), a bare `.aar` in `libs/` (loses the
dependency metadata).

Versioning: lsd-visuals version = `<engine level>.<liquid-lsd short date>`; the engine level is
`CONTENT_ENGINE` in `make_content_manifest.py`, so an app knows which content it can take.

## Phases

### 1. Extract lsd-visuals (no behaviour change)

1. Create `android/lsd-visuals/` (its own small Gradle build in liquid-lsd; AGP, Kotlin, Compose for the
   `VisualizerView` composable). Package `org.liquidlsd.visuals`.
2. Move in: `VisualizerView` (split into a plain `LsdVisualsView` that owns the WebView, plus a thin Compose
   wrapper, because phase 4 needs a WebView inside a `Presentation`, outside Compose), `AssetClient`,
   `LsdHostBridge`, `ContentStore`, `ContentUpdater`, `ContentUpdates` (base URL and public key become
   parameters), `PcmMonoChunker`, `VisualsSettings`. Their unit tests move with them.
3. The assets: a Gradle task copies `web/` into the library's assets with the same excludes and gallery export
   as `sync_visuals.sh`. Gallery photos become optional (`includeGallery`), since the VJ app may not want
   Spaz Radio's photos.
4. Spaz Radio switches to the library; its sync script is replaced by `release_lsd_visuals.sh`.
5. Verify: Spaz Radio unit tests, lintDebug, a run on the phone (picture, sync, pause, content update, live
   follow). Release only when the user wants one (not required for the VJ app).

### 2. Control API in the engine (`web/embed.js`)

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

- New repo, Kotlin + Compose, minSdk 29 (system audio capture needs Android 10; about 95% of active phones).
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

### 4. External display (USB-C, Cast, Miracast)

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
