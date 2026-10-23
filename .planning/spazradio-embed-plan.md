# Spaz Radio: embed the web TV visuals

2026-10-23. PLAN only, nothing built. Replaces Spaz Radio's Compose oscilloscope with the web app's
video (no TV frame), bundled in the APK so it works offline.

Spaz Radio: `/home/gj/AndroidStudioProjects/spazradio` (Kotlin, Compose, Media3, minSdk 24, APK ~6.2 MB,
no WebView yet, no RECORD_AUDIO). Distributed via GitHub releases and Google Play (no longer F-Droid).

## Decisions (user, 2026-10-23)

- Bundle the assets; offline use is a hard requirement.
- Video only, no TV frame, no OSD/MENU.
- Hard-coded defaults + a few Settings-panel options. No gestures in v1 (panels sit over the video and own
  the touches): no channel change, no Hold, no look-name toast. The WebView is non-interactive.
- v1.1 option: gestures / a small prev-next-hold overlay only while the panels are hidden.

## Key design: native audio feeds the existing analysis graph

`dsp.js` builds one graph: `<audio>` -> MediaElementSource -> `analysisIn` gain -> 4 analysers (broadband,
bass, mid, high) + the `lsd-onset` worklet -> `cvState` / `BeatTracker`. Do NOT rewrite the analysis.
Replace only the source: in embed mode `startAudio()` skips the `<audio>` element and connects a
**feed worklet** (a PCM ring buffer, `feed-worklet.js`, output muted) to `analysisIn`. Every existing
signal (bands, flux, onset, beat clock, energy/section, drops) then works unchanged, and the desktop-parity
scaling is kept.

Native side: `RadioService.audioBufferSink.handleBuffer` already sees the decoded 16-bit PCM
(`flush()` gives sampleRate / channelCount, currently ignored). Add a second consumer next to the 8-bit
`waveformFlow` (leave that alone until the oscilloscope is removed): downmix to mono float, resample not
needed (the worklet is told the rate), post chunks to the page.

Transport (decide in step 3):
- A. `WebMessagePort` with ArrayBuffer messages (androidx.webkit >= 1.12, `WebMessageCompat.TYPE_ARRAY_BUFFER`):
  best, no encoding cost. Check device support with `WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER`.
- B. fallback: base64 `evaluateJavascript("LSD.feed('...')")`, ~33% overhead, fine at ~44 kHz mono int16
  (~90 KB/s) if batched every 30-50 ms.
Latency target: the picture should lag the audio by no more than ~100 ms. Note ExoPlayer's audio sink
buffers ahead of what is audible; the feed will lead the speaker slightly. If visible, delay the feed by a
fixed offset in the worklet (one tunable).

## Status (2026-10-23)

Web side (steps 1-5 below) BUILT and passing, uncommitted: `embed.js`, `embedmode.js`, `feedring.js`,
`feed-worklet.js`, edits in dsp/ui/autopilot/renderer/tv.css, `checks/embed_check.mjs`,
`tools/feedring.test.mjs`, release notes + `docs/developer/web_subsystem.md` §6b. Differences from the plan:
no `setPlaying` (silence = quiet picture, noSignal pinned off); key handlers kept (harmless, handy for testing;
the WebView just must not take focus); `quality: low` = heavy off + render-scale floor at step 2.
Unseen on a device. Web `LSDHost.ready()/failed()` bridge added (renderer.js).

Android side BUILT 2026-10-23 on spazradio branch `visuals-web-embed` (commit c1579d7, not merged/pushed); debug +
release APKs build, unit tests pass; NOT run on a device or emulator (none available here). Differences:
- No androidx.webkit (dependency locking is on): `AssetClient` in VisualizerView.kt serves assets/visuals/ itself;
  feed is base64 via evaluateJavascript (PCM tee -> `PcmMonoChunker` 40 ms mono chunks -> `LSD.feed`).
- Sync is NATIVE: the feed is delayed by the `syncMs` setting (default 250 ms, 0-800) so amplitude and beats
  stay coherent; the web `sync` option is unused by the app.
- No separate low-battery setting: Auto quality drops to Low under the system battery saver.
- Settings > Visuals section (quality, reaction, reduced motion, picture delay, follow live) in 4 languages.
- Fallback to the oscilloscope on: no WebView, LSDHost.failed (no WebGL2), 20 s without LSDHost.ready while visible,
  renderer crashes beyond 2 restarts. Release APK 6.66 MB (was 6.2).
- Picture runs only while visible AND playing (frozen frame when paused).
Open: on-device check (startup, first-frame time, beat sync/delay default, heat, WebView renderer crash, worklet
import inside Android WebView, `LSD.feed` call rate), merge + release notes in spazradio, F-Droid files still in repo.

## Steps

### Web side (liquid-lsd/web)
1. **Embed mode** `?embed` (extends the `?kiosk` path in ui.js:165/487): no TV body/frame/controls, canvas
   fills the view, no OSD/MENU, no cursor logic, no key/touch handlers, no power-on gesture (turns itself on
   when the feed starts), no mic, no `<audio>`, no service worker registration, no relay WebSocket and no
   status-json fetch (offline-first; a setting can enable the relay later).
2. **`feed-worklet.js` + `dsp.js` entry points**: `startAudio()` in embed mode builds the graph from the
   feed node; export `LSD.feed(samples, sampleRate)` (Float32 / Int16) and the port message handler.
   Underrun = silence; the existing `noSignal` logic (4 s quiet -> snow) must not show snow in embed mode
   when the radio is merely paused: add `LSD.setPlaying(bool)` and make paused -> calm idle look, not snow.
3. **Embed API on `window.LSD`**: `setOption(name, value)` for quality (auto/low/high -> `setHeavyAllowed`,
   adaptive resolution tier), reactivity (REACT), reducedMotion (`setReducedMotion`), live (relay on/off);
   `pause()` / `resume()` (stop rAF + audio feed); and, unused in v1, `next()` `prev()` `hold(bool)`
   (thin wrappers over `advanceForeground`, `previousForeground`, `setHold`) so v1.1 gestures are cheap.
4. **Tests**: node unit test for the feed worklet's ring buffer; headless render check
   (`tools/render_check.mjs`) in embed mode with a synthetic feed.
5. **Packaging check**: confirm nothing needs a network in embed mode. Make a build list of what ships:
   everything in `web/` except `checks/`, `tools/`, `sw.js`, icons/og image not used by embed. Target < 2 MB
   compressed; measure.

### Android side (spazradio)
6. **Asset bundling**: `scripts/sync_visuals.sh` in the spazradio repo copies `web/` (minus `checks/`, `tools/`,
   `sw.js`, unused icons) into `app/src/main/assets/visuals/` and writes a version stamp (liquid-lsd git hash).
   The snapshot is committed, so GitHub/Play builds (and any CI) never depend on the sibling checkout.
   Re-run the script when the web app changes. Play delivers an AAB, so the asset size is not a concern.
7. **`VisualizerView`** (Compose `AndroidView` around a `WebView`) in the slot where `Oscilloscope` is
   (MainActivity.kt ~324, behind `MainLayout`). Settings: JS on, DOM storage on, `mediaPlaybackRequiresUserGesture
   = false`, no file access, `WebViewAssetLoader` serving `https://appassets.androidplatform.net/assets/visuals/`
   (needed for ES modules, `fetch` of shaders/presets, AudioWorklet `addModule`). `isClickable=false`,
   `setBackgroundColor(Color.BLACK)`, hardware layer. Block all navigation/external requests
   (`shouldInterceptRequest` returns an empty response for anything not on the loader origin).
8. **Audio bridge** in `RadioService`: second `AudioBufferSink` consumer producing mono float/int16 chunks
   (reuse one buffer; the existing code allocates per call, avoid that here) -> a `SharedFlow`/callback ->
   `VisualizerView` -> port. Send `flush()` sampleRate/channels at start and on change. Send play/pause
   state from the MediaController (`isPlaying`).
9. **Lifecycle & power**: pause the WebView (`onPause()` + `LSD.pause()`) when the app is backgrounded, the
   screen is off, `visuals_enabled` is false, or playback is paused/stopped; resume on return. Background
   audio keeps playing via the service; the visual never runs in the background. Optional: pause below a
   battery threshold. Audio-focus/notification behavior untouched.
10. **Settings panel**: add Quality (Auto/Low/High), Reactivity, Reduced motion, Live takeover (default off),
    Pause on low battery, persisted in the existing prefs, applied through `LSD.setOption`. Existing
    `visuals_enabled` toggle keeps working (now unloads/pauses the WebView; destroy it when off to free GPU
    memory).
11. **Remove** the oscilloscope and the 8-bit `waveformFlow` once the new path is verified (keep until then).

### Verify
- Real device, mid-range phone, 20+ min: frame rate (adaptive tier settles), temperature/battery, no
  memory growth, rotation (activity is portrait-locked), process death/restore, WebView crash
  (`onRenderProcessGone` -> recreate), airplane mode (stream is gone but visuals still load).
- Audio sync by eye on a hard-kick track; beat lock works from the feed (`cvState.beatLocked`).
- Old WebView (minSdk 24 devices with ancient WebView): detect missing WebGL2 -> fall back to the existing
  oscilloscope rather than a black screen. **Keep the oscilloscope as the fallback (step 11 amended):
  delete only `waveformFlow` consumers that the fallback doesn't need.** Decide at step 11.
- Release: record the APK/AAB size delta; check R8 does not strip WebView bridge classes; Play data-safety
  form unchanged (no new permissions, no network use by the visuals).

## Risks / open questions

1. Beat tracking quality from a pushed feed vs the stream element (timing jitter from chunk arrival):
   stamp chunks with a monotonic native time or let the worklet timestamp by its own clock; the tracker
   already tolerates frame-rate envelope (`onsetFromWorklet=false` path) if the worklet path is rough.
2. WebGL2 shader compile stalls on first run of heavy looks (mid-range GPUs): prewarm exists
   (`setPrewarm`); default Quality=Auto should start in the light tier.
3. Vendored copy drifts from `web/`: sync script + a note in both repos' docs; the version stamp is shown
   in the app's About.
4. Does anything in the panels' touch handling need the WebView at all? Planned: no.
5. Spaz Radio `.idea/` has an uncommitted modification; leave it alone.

## Docs to update when built (per repo convention)
Release notes (both files), `docs/user_guide`/dev docs for embed mode and the `LSD` embed API,
DECISIONS.md entry (edit in place), `.planning/app-checklist.md` (unseen-in-app items).
