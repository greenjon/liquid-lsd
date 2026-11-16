# Lane 6: Web TV (audit 2026-11-15)

Baseline `audit-2026-10-29` (fa4335e). 61 web/server commits, 31 code files changed since. Read in full: dsp.js, onset-worklet.js,
feed-worklet.js, feedring.js, renderer.js, graph.js, isf.js (runtime half), ui.js, menu.js, sync.js, beatclock.js, sw.js,
schedule.js, mqtt.js (lifecycle), chat.js (lifecycle), tapedeck.js (deck + strip), autopilot.js (state machine, controls, relay,
startup), gallery.js (load/render), embed.js (debug, push), preset.js, index.html, tv.css (theme blocks), server/server.js.
Skimmed or covered by vectors: evaluator.js, energy.js, audiotex.js, mandala.js, pairing.js, beattrack.js, tapes.js, chatboard.js.

## Checklist coverage

| Item | Result |
|---|---|
| AudioWorklet / audio-thread allocation | onset-worklet allocates per 10 ms hop (L6-12). feed-worklet + FeedRing clean. |
| Per-frame allocation in render loops | Present in dsp.js, renderer.js, graph.js, gallery.js (L6-12). No unbounded growth found: DelayLine, FrameTransient, fgHistory (50), tracker rings are all capped. |
| Leaked timers / listeners | Mostly clean: OSD timers are cleared, mqtt clears its ping/pong/retry timers, chat leaves on pagehide, the relay reconnect has no duplicate sockets. Found: wake-lock race (L6-8), `loadTape` listener build-up (L6-12). |
| Leaked WebGL objects | Clean: ISFState disposes on program change, FX slot states on id change, the GPU probe disposes its pipeline. Library programs are never freed (bounded by the catalog; not a leak). No context-loss handling (L6-7). |
| fetch / JSONP failures handled | Clean overall: fetchTimed retries, sharedJson/fetchContent drop failed promises, Library retries after 15 s, schedule keeps its last good list, tapes keep a list. Found: a failed preset fetch puts a default Mandala on air (L6-9). Concurrent JSONP calls clobber `update_meta` (L6-13). |
| CORS assumptions about spaz.org | Checked live with curl (Origin: https://spaz.org). ACAO `*` on status-json.xsl, the stream, djdash/droid, djdash/chatlog and archives.rss, as the code assumes. `/playing` has none, as documented. playing-jsonp is served with **no Content-Type** (L6-13). gallery.json is same-origin. |
| Golden vectors current | Yes. Gradle at 18:09 today: WebEvaluatorVectorsTest, WebAudioAgcVectorsTest, WebSpectrumVectorsTest, WebMandalaVectorsTest, TransientDetectorTest and WebSyncTest all pass. No desktop audio/cv change since. JS replay: `node --test` evaluator/transient/beatclock/audiotex/mandala: 104/104 pass. The tempo tracker has no vectors (L6-14). |
| `scripts/sync_web.py` leaves no diff | `--check` (read-only; `--apply`/`--mark-synced` write) gives 88/88 in sync. WebSyncTest/checkWebSync gaps: L6-15. |
| TV_VERSION per publish | v6 to v46 bumped in order; live spaz.org/tv/ui.js = v46 = local. Code commits without a bump (7516ac8, b505076, 324bc59, 55b8099, ...) were all shipped by the next bumped publish. v43 appears in no commit (published from a working tree between cf3162b and 5d628ff). Clean apart from that note. |
| about/keys page vs key handler | Handler keys (ui.js:955-977) all appear on #keys (index.html:136-197) except Escape (leave fullscreen) and `+` (alias of `=`), both fine to leave off. Canvas hover hint is wrong (L6-4). |
| web_subsystem.md vs code | Several stale/wrong lines (L6-5, L6-6, L6-10, L6-11, L6-15, L6-16). |
| Hover hints on viewer controls | Every button, dial and key-bank key in index.html has a `title`. Tape strip buttons (tapedeck.js:202-207) and menu rows (`help`) have them too. One hint is wrong (L6-4). |
| Dark + light theme tokens | Complete. Every token the cabinet uses is defined in `:root` and overridden in `:root[data-theme="light"]`. Dark-only tokens (screen, slate, chalk, LEDs) are shared on purpose. Light-only `--brushed/--spun/--metal-edge` are used only under light selectors. `--led-on` is defined and unused (tv.css:49, polish, not filed). |
| Lane 8 embed.spec failures | Cause found: L6-3. |

Not done: the full `node --test web/tools/*.test.mjs` run was cut off at 5 min (one of the long suites, likely viewer_controls/content). Only the vector suites were run to completion.

## Findings

### L6-1 [bug] Relay: a replaced broadcaster's late `close` drops the new broadcaster
- Where: server/server.js:79-81, 101-106
- Evidence: a new broadcaster calls `broadcaster.close(4000)` on the old socket, then sets `broadcaster = ws`. The old socket's `close` handler runs later and does `broadcaster = null; state.reset(); fanOut(broadcaster_offline)` without checking `broadcaster === ws`. This is exactly what happens when the desktop reconnects after a network blip while its old socket is still open.
- Impact: viewers get `broadcaster_offline` and drop to the autopilot while the desktop is live. They then ignore its deltas (autopilot.js:1088 waits for a `state_full`, which the desktop only sends on a preset or setlist change). Viewers who join later get `broadcaster_offline` (empty state). /health reports no broadcaster. The next reconnect does not close the stray socket.
- Fix: in the broadcaster's close handler, `if (broadcaster !== ws) return;` before the reset and fan-out. Optionally ignore messages from a socket that is no longer `broadcaster`. Add a state.test/integration case.
- Verified:

### L6-2 [bug] After a live broadcast ends, the desktop's mixer and background stay on the autopilot
- Where: web/autopilot.js:1079 (live `state_full` replaces `autopilotState.mixer` and `deckBG`), 1095-1105 (`broadcaster_offline` resets neither), 341-343 (BG `hold` never changes on its own)
- Evidence: `broadcaster_offline` only clears the live flag, the clock and the switch beats. `autopilotState.mixer` keeps the desktop's master `fx`/`fxDryWet`, `levelA/B/BG` and `alpha`. `startForegroundCrossfade` only `Object.assign`s the transition into it. `fgActiveDeck` is stale against the live `balance`, so the first autopilot crossfade can cut into the visible deck. The live `deckBG` stays: with BG CHANGES = HOLD (the default) there is no timed background change, so the desktop's background (or `{empty:true}`) stays until the viewer presses B, while `bgOnAir`/the OSD still name the old line.
- Impact: every viewer who watched a broadcast keeps the broadcaster's master FX and levels, and loses the gallery background, until reload. Docs say viewers "fade back to local autonomous Autopilot".
- Fix: on `broadcaster_offline`, restore a default mixer (`{mode:0,balance:fgActiveDeck==='A'?0:1,alpha:1}` or a snapshot taken at `state_full`), and restore or rebuild `deckBG` from `bgOnAir` (or call `prepareBackground` and fade it in). Add a ui_check/autopilot test that goes live then offline.
- Verified:

### L6-3 [bug] embed.spec.ts:32 and :129 fail: `LSD.catalog()` reports no backgrounds until every preset's tags are in (since the fast start)
- Where: web/autopilot.js:1285-1306 (`startBackground` sets `bgPlaylist` only after `loadPresetTags` over all ~166 presets, three at a time, and gallery.json), 991-1003 (`contentCatalog` maps `bgPlaylist`, `presetSource`, `presetTags` as they are now); tests/web/embed.spec.ts:16-19 (`openEmbed` waits only for `scenes.length > 0` and a state name)
- Evidence: since 8a9a458 (TV v35) `startAutopilot` resolves on the first scene and `startBackground` runs unawaited. `fgPlaylist` is set at once, so `scenes.length > 0` passes, but `bgPlaylist` stays `[]` until all tags have loaded. So `backgrounds.length` is 0 at :32. At :129 there are no gallery entries, and `runnable === false` would not hold either. Before tags are in, scenes report `source: null`, `tags: []` and `runnable: true` even for heavy sources (`runnable` treats an unknown source as light). Nothing tells the host when the catalog is complete: `state()` has no flag and no push fires for it.
- Impact: the test failures are a race the fast start introduced, not a regression in the background logic. But the race is real for any host: the VJ app reading `catalog()` at `LSDHost.ready` gets an empty background list and wrong `runnable` flags. On a phone this window lasts several seconds.
- Fix: expose readiness, e.g. `catalog().complete = tagsReady && bgPlaylist.length > 0` (or a `catalogReady` in `controlState`), and push state once when it flips (embed.js `push` on a new `lsd-catalog-ready` event). In the test, have `openEmbed` (or these two tests) wait for `catalog().backgrounds.length > 0` / `complete`.
- Verified:

### L6-4 [wrong] Picture hover hint says S is fullscreen
- Where: web/ui.js:267 (also the comment at ui.js:1018)
- Evidence: `canvas.title` on: "Double-click for fullscreen (or press S)". S opens the schedule (ui.js:956); fullscreen is F (ui.js:972, #keys index.html:173, MENU help ui.js:603).
- Impact: every desktop viewer who hovers the picture is told the wrong key.
- Fix: "(or press F)", and fix the comment.
- Verified:

### L6-5 [wrong] web_subsystem.md: theme default is light, not dark
- Where: docs/developer/web_subsystem.md:216; code web/ui.js:43, 499; web/index.html inline script
- Evidence: the doc says "`lsd.theme` (JSON, default dark)". Since 8b3c211 (TV v46) the default is light, and the inline script sets light unless the stored value is `"dark"`.
- Impact: developers. User guide was updated in the same commit.
- Fix: "default light (the hi-fi receiver)". Mention that kiosk/embed get the dark base tokens.
- Verified:

### L6-6 [stale] web_subsystem.md: live flywheel says the desktop still coasts at full tempo
- Where: docs/developer/web_subsystem.md:122; code src/main/kotlin/llm/slop/liquidlsd/cv/CVRegistry.kt:24, 187-196
- Evidence: the doc says "at full tempo, as the desktop's `getSynchronizedTotalBeats` still does". Since ef9c2ac the desktop coasts at `COAST_RATE = 0.8`, the same as web `CATCH_UP`.
- Impact: developers. A parity statement is now false.
- Fix: say both coast at 80%.
- Verified:

### L6-7 [bug] No WebGL context-loss handling
- Where: web/renderer.js:58-500 (no `webglcontextlost`/`webglcontextrestored` anywhere in web/)
- Evidence: all programs, VAOs, targets and Library entries are created once. After a context loss (GPU reset, driver update, mobile tab reclaimed, laptop sleep on some drivers) every draw is a no-op and nothing rebuilds them.
- Impact: the TV shows a frozen/black picture with sound until reload. In embed, the host never hears `LSDHost.failed`.
- Fix: at minimum, on `webglcontextlost` call `preventDefault()` and on `restored` reload the page (or call `LSDHost.failed` in embed). A full rebuild is optional.
- Verified:

### L6-8 [bug] Wake lock can stay held after power-off
- Where: web/ui.js:1198-1210
- Evidence: `holdScreenAwake` checks `powerState.on` before `await navigator.wakeLock.request()`, then stores the lock without re-checking. A power-off while the request is pending runs `letScreenSleep()` with `wakeLock` still null, and the lock then lands and is kept.
- Impact: rare. A quick on/off keeps the laptop or phone screen awake with the TV off.
- Fix: after the await, `if (!powerState.on) { lock.release(); return; }`.
- Verified:

### L6-9 [bug] A preset that fails to load goes on air as a default Mandala under its own name
- Where: web/autopilot.js:230-247 (`buildDeck`); web/preset.js:45 (`parseDeckPreset(null)` returns `{source:'mandala', params:{}}`)
- Evidence: `fetchPreset` returns null after 3 timed tries. `buildDeck` does not check, so the scene becomes a default Mandala with no modulation, and the OSD/history name the failed preset.
- Impact: on a flaky connection (before the offline cache has it) a viewer gets an unrelated static mandala labelled as another channel. The same deck is stored in `fgHistory`, so CH back to it repeats it.
- Fix: `buildDeck` throws or returns null on a null preset. `prepareForeground`/`tuneChannel` then skip to the next playable line and `learnPreset` marks it unplayable for a while.
- Verified:

### L6-10 [stale] web_subsystem.md: audio scaling line predates auto gain and hits
- Where: docs/developer/web_subsystem.md:223 (also the diagram at :153-159, which has no EQ between the volume gain and the speakers)
- Evidence: "dsp.js publishes `audio_*` and `audio_flux_*` with the desktop's scaling (RMS / 0.25, band flux / 0.05, amp flux = (2 bass + 0.8 mid + 0.3 high) / 0.1)". The published signals now use AudioAgc divisors plus FrameTransient/HitScale (dsp.js:283-294). The fixed scales only feed `cvState.raw` (line 180 of the same doc says this correctly). The diagram omits the EQ chain (dsp.js:626-635).
- Impact: developers. The page contradicts itself.
- Fix: replace line 223's scaling clause with a pointer to the auto-gain bullet. Add the EQ to the diagram.
- Verified:

### L6-11 [stale] web_subsystem.md: power/near-miss and listener bullets
- Where: docs/developer/web_subsystem.md:214, 217, 287, 316
- Evidence: (a) :214 lists `#about`, which no longer exists (the ? page is `#keys`). Off-power near-misses are said to work for "MENU, SCHEDULE, TAPES, CHAT", but the code matches `#key-bank button, #chatBtn` (ui.js:351), which also includes OFF, INFO and ?. (b) :217 and ui.js:1245-1246 say a browser can't read `/playing`'s listener count. The TV now reads `playing-jsonp`, which carries `listeners` (verified live). (c) :287 "zero-dependency Node.js service using `ws`" (`ws` is its one dependency, server/package.json). (d) :316's "Fullscreen and microphone" bullet sits under section 7 (relay), not section 5.
- Impact: developers.
- Fix: update the wording. For (b), either note that the JSONP has `listeners` or switch `showListeners` to it (owner's choice: the Icecast sum counts every mount, the app's number may differ).
- Verified:

### L6-12 [polish] Allocation on the audio thread and per frame
- Where: web/onset-worklet.js:67-82 (`filters.map` array every 10 ms hop, new `values`/`bands` arrays every 100 ms, `for...of` per sample); web/dsp.js:276-294 (rms object, two `bandSignals` objects + closure, divisors object, `hitSignals` object + closure + `Object.keys`, a `{t, level}` per band per frame in FrameTransient:220); web/renderer.js:187-208 (`Object.fromEntries(AUDIO_KEYS.map(...))`, a spread copy, a closure and a new `evalCtx.cv` each frame), 394 (array literal per Mandala frame); web/graph.js:130, 182, 267 (params/frame/images objects per pass); web/gallery.js:286, 293 (two `filter`s per frame); also web/dsp.js:372 (`loadTape` adds a `loadedmetadata` listener on every 10 s retry while a tape is stuck)
- Evidence: as listed. Each is small, but together they are a few dozen short-lived objects per frame and ~100 arrays/s on the audio render thread. That is GC pressure on phones (embed) and a glitch risk in the worklet.
- Impact: none observed (the 25-minute leak check passes). Worth it for the phone path.
- Fix: preallocate in the worklet (a Float32Array of rises, fixed-size batches). Reuse `evalCtx.cv` and a DelayLine ring of preallocated objects. Make FrameTransient a small ring. Remove the previous `loadedmetadata` listener before adding one.
- Verified:

### L6-13 [polish] playing-jsonp: concurrent calls and a missing Content-Type
- Where: web/schedule.js:96-115; web/ui.js:1218-1237
- Evidence: (a) `loadPlayingJsonp` installs one global `update_meta`. A second call while the first is in flight (power off/on within the 8 s timeout restarts `pollNowPlaying`) replaces it. The first then times out, `fetch` fails on CORS, and `readPlaying` marks the flag unknown for 10 minutes (PLAYING_RETRY_MS). (b) The live file is served with no `Content-Type` header (curl). It runs only because no `nosniff` is set. If the server adds `X-Content-Type-Options: nosniff`, Chromium will refuse it, and LIVE SHOW silently falls back to the schedule.
- Impact: low. LIVE SHOW/JUKEBOX can be wrong for 10 minutes.
- Fix: share one in-flight promise in `loadPlayingJsonp`. Ask the spaz.org admin to serve `streams/playing-jsonp` as `text/javascript`.
- Verified:

### L6-14 [decision] The tempo tracker has no golden vectors; its drift from the desktop is untracked
- Where: web/beattrack.js, web/beatclock.js (`BeatClock`); web/sync_manifest.json `monitored_sources` (only Evaluators.kt, WebPresetSerializer.kt, SpectrumAnalyzer.kt)
- Evidence: the AGC, transients, spectrum, evaluator and mandala have vector tests. BeatTrackerEngine.kt / BeatPhaseLock.kt / the coast in CVRegistry.kt are neither vector-checked nor hash-monitored. The web port deliberately diverges (no inertia, 180 BPM unwrap), so vectors may not fit. But a desktop change (e.g. ef9c2ac, which made L6-6 stale) goes unnoticed.
- Impact: parity of tempo/phase rests on prose.
- Fix (owner): add BeatTrackerEngine.kt, BeatPhaseLock.kt and CVRegistry.kt to `monitored_sources` so a change forces a `--mark-synced` review. Or decide that the web tracker is its own design and say so in DECISIONS.md.
- Verified:

### L6-15 [wrong] "checkWebSync guards drift on every build" is not true, and WebSyncTest misses two files
- Where: docs/developer/web_subsystem.md:224; build.gradle.kts:915-919; src/test/kotlin/.../WebSyncTest.kt (tests at :50, :55, :93, :127)
- Evidence: `checkWebSync` is a standalone Exec task that no `check`/`build`/`test` depends on. WebSyncTest checks shaders, monitored hashes and content copies, but not `web/catalog.json` or `web/mandala_recipes.json`, which `sync_web.py --check` covers. (The mandala table is partly covered by WebMandalaVectorsTest.)
- Impact: a stale catalog (a new desktop shader/filter not listed for the web) passes the Gradle suite.
- Fix: make `check` depend on `checkWebSync`, or add a catalog test to WebSyncTest. Fix the doc line.
- Verified:

### L6-16 [bug] Live mode: SYNC under about a quarter beat is mostly ignored
- Where: web/beatclock.js:55-64 (`read` mutates `lastOut`, documented "call once per frame"); web/sync.js:11 (`heardBeats` calls `clock.read(now - sync)`); callers per frame: web/renderer.js:186 (`heardBeats(now, syncMs)`), :189 (`heardBeats(now, 0)`), web/ui.js:1160 (`heardBeats(now, syncMs)`, the beat LED)
- Evidence: during a broadcast the flywheel is read three times a frame at two different times. Each `read(now - sync)` lands less than SNAP_BEATS (0.25) behind the `lastOut` that `read(now)` just set, so it takes the "small backward correction" path and returns ≈ `lastOut`, i.e. the undelayed beat. Simulation with the real class (126 BPM, scratchpad fly.mjs): the picture's beat runs ahead of the intended delayed beat by 43 ms at SYNC 50 and 89 ms at SYNC 100. From 150 ms (over a quarter beat) it is correct to within about 9 ms. Standalone mode is unaffected (it extrapolates from cvState).
- Impact: viewers who set SYNC (or TAP SYNC) to under ~120 ms see no effect during live broadcasts. Negative SYNC behaves the same way.
- Fix: make `read` pure for callers (`predict` plus a forward-only filter keyed per caller), or read the flywheel once per frame at `now` and derive `now - sync` from it (`beats - sync*bpm/60000`).
- Verified:

## Notes (no action)
- TV_VERSION: v43 is not in git. It was published from a working tree between cf3162b (v42) and 5d628ff (v44).
- Relay reconnect (autopilot.js:1182) retries every 5 s with no backoff. The relay has no ping, so half-open viewer sockets stay in `viewers` until TCP gives up. Both are minor.
- mqtt.js has no CONNACK timeout: a socket that opens but never gets CONNACK stays "connecting" until the browser closes it.
