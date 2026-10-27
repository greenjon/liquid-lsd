# Web Subsystem & Broadcast Protocol

This document provides a technical specification for Liquid LSD's browser-based WebGL2 visualizer, live WebSocket broadcast relay, and desktop-to-web synchronization architecture.

---

## 1. System Topology & Architecture

Liquid LSD's web subsystem enables zero-latency live visual streaming without video transcoding by transmitting lightweight mathematical state representations over WebSockets to client-side WebGL2 renderers.

```
┌──────────────────────────────────────────────────────────────┐
│ DESKTOP APPLICATION                                          │
│                                                              │
│  Mixer / Deck Engine ──► WebPresetSerializer (JSON)          │
│                               │                              │
│                               ▼ (throttled, 5-60 Hz)         │
│  BroadcastEngine-IO Thread ──► java.net.http.WebSocket       │
└───────────────────────────────┬──────────────────────────────┘
                                │ WSS: ?role=broadcast&key=<token>
                                ▼
┌──────────────────────────────────────────────────────────────┐
│ NODE.JS RELAY SERVER (server/server.js)                      │
│                                                              │
│  - Authentication & Role Verification                        │
│  - Active Broadcaster Arbitration                            │
│  - Merged State Cache (late joiners get current state)       │
│  - Low-Latency Fan-Out Distribution                          │
└───────────────────────────────┬──────────────────────────────┘
                                │ WSS: ?role=viewer
                                ▼
┌──────────────────────────────────────────────────────────────┐
│ BROWSER CLIENT (web/)                                        │
│                                                              │
│  - Autopilot Playlist Manager (autopilot.js)                 │
│  - Web Audio DSP Graph & Beat Tracker (dsp.js)               │
│  - Beat-Clock Extrapolation (renderer.js)                    │
│  - WebGL2 Multi-Pass Pipeline & CRT Post-Processing          │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. WebSocket Wire Protocol (v2)

The relay server and browser clients exchange compact JSON messages. Every `state_full` carries `"v": 2`; a client that speaks another version ignores it (and the deltas that follow, until a full snapshot of its version arrives). Values on the wire are the desktop's **evaluated** parameter values, rounded to 4 decimals, so the browser runs no modulators in live mode and matches the desktop exactly.

### Message Types

#### 1. Full State Snapshot (`state_full`)
Sent when the broadcaster connects, when a preset or setlist changes, and by the relay to a viewer joining mid-session (built from the relay's merged state, not the broadcaster's last snapshot).

```json
{
  "type": "state_full",
  "v": 2,
  "clock": { "beats": 812.4, "bpm": 126.0 },
  "preset": {
    "deckA": {
      "source": "dynamic_spiral",
      "params": { "MaxPoints": 500.0, "Scale": 0.5, "HueOffset": 0.2 },
      "viewZoom": 1.0, "viewRotateZ": 0.0, "globalAlpha": 1.0,
      "fx": {
        "0": { "id": "feedback", "dryWet": 1.0, "params": { "fbDecay": 0.65, "fbGain": 0.48 } },
        "1": null,
        "2": null
      },
      "fxDryWet": 1.0
    },
    "deckB": { "empty": true },
    "deckBG": { "empty": true },
    "mixer": {
      "balance": 0.0, "alpha": 1.0, "levelA": 1.0, "levelB": 1.0, "levelBG": 1.0,
      "transition": "linear_crossfade", "transitionParams": {},
      "fx": { "0": null, "1": null, "2": null }, "fxDryWet": 1.0
    }
  }
}
```

- **`params`** is keyed by the exact parameter name: the ISF input `NAME` the shader uniform uses (Mandala uses its own names, e.g. `Hue Offset`). The browser looks the name up in the source's ISF header.
- **`fx`** is an object keyed `"0"`..`"2"`, not an array, so a delta can address one slot parameter. An empty slot is `null`. `dryWet` is the *effective* wet amount (zero for a disabled slot, scaled by the desktop's FX dip). `fxDryWet` is the effective chain wet. The master chain is `mixer.fx` / `mixer.fxDryWet`.
- **`balance`** is the crossfader mapped to 0..1 (the transition's `progress`). `transition` is a transition id from `web/catalog.json`; unknown ids fall back to `linear_crossfade`.
- **`mandala`** (Mandala decks only) holds the shader-ready uniforms (`uL1`..`uL4` already normalised, `uA`..`uD` from the selected recipe, `uThickness`, `uHueOffset`, `uHueSweep`, `uDepth`, `uMaxR`), because the browser has no recipe table.
- **`broadcaster`** (optional string, at most 40 characters) is the broadcaster's name from `BroadcastPreferences.broadcasterName`, for the web TV's LIVE title card. It rides inside `preset` so the relay's merged state and the deltas carry it with no relay change (a delta's `null` clears it); clients that do not know it ignore it, so the protocol stays v2.
- **`empty: true`** marks an empty deck; a source the web cannot draw (external video) is sent with an id the catalog does not know and renders as nothing.

#### 2. Throttled Delta Update (`state_delta`)
Streamed at `BroadcastPreferences.targetFps` (5-60 Hz). `patch` is a recursive diff against the last state sent: only changed keys appear, and `null` means the key was removed or the slot emptied. An empty `patch` is a once-a-second heartbeat that only carries the clock.

```json
{
  "type": "state_delta",
  "clock": { "beats": 812.9, "bpm": 126.0 },
  "patch": {
    "mixer": { "balance": 0.52 },
    "deckA": { "fx": { "0": { "params": { "fbDecay": 0.7 } } } }
  }
}
```

#### 3. Broadcaster Handshake (`broadcaster_online` / `broadcaster_offline`)
Dispatched by the relay server to all connected viewers when the desktop broadcaster connects or disconnects:
- `broadcaster_online`: Viewers smoothly transition from Autopilot mode to the live broadcaster state, and the TV station badge flips to `SPAZ RADIO • LIVE`.
- `broadcaster_offline`: Viewers smoothly fade back to local autonomous Autopilot playlist cycles.

### Relay state (`server/state.js`)
The relay keeps the broadcast state merged: each `state_full` replaces it, each `state_delta` is applied to it (a `null` deletes the key). A viewer that joins mid-session is sent a `state_full` built from it, with `clock.beats` advanced by the time since it was recorded, so it starts on the right beat. Messages of another protocol version are relayed but never cached. Tests: `cd server && npm test` (no `ws` needed).

---

## 3. Beat Clock

Every message carries `clock: {beats, bpm}`, the desktop's synchronised beat count and tempo at the moment of sending. The browser stores it with the local `performance.now()` at arrival and extrapolates (`beats + elapsed * bpm / 60`) for every `lfo`/`beatSine` modulator and any web-side evaluation, so in live mode it needs no beat detection of its own. Network latency (tens of milliseconds) is not compensated; the once-a-second heartbeat keeps drift from accumulating. Outside a live broadcast the client tracks the radio stream's own beat (below), so beat-synced modulators land on the music's beats there too.

**Standalone beat tracking (`web/onset-worklet.js`, `web/beattrack.js`).** An AudioWorklet computes an onset envelope at audio rate, one value per 10 ms (rise in log energy of the bass below 150 Hz, weighted double, plus the full signal), so its timing does not depend on the frame rate. Each value is stamped with the `performance.now()` time it is *heard* (`AudioContext.getOutputTimestamp`), so output latency is compensated. `BeatTracker` keeps 8 s of envelope and, every 250 ms: estimates the tempo by autocorrelation (70-180 BPM, plus the second harmonic, under a log-Gaussian prior centred on 120 BPM so it does not lock to double or half time; a fast track such as 174 BPM drum and bass is followed at half time, every kick still on a whole or half beat), fine-tunes the period by +-2% to the one whose comb peaks highest, and finds the phase with a comb filter. Confidence is how far the best phase stands above the average, and is zero unless the older and newer halves of the window agree on the grid (random onsets line up across the whole window, a real beat lines up in both halves). Tempo changes need three agreeing estimates. `BeatClock` turns confident estimates into anchors for a `BeatFlywheel` (the live-mode flywheel): two readings in a row must agree on the phase, a small backward error is absorbed by coasting and a large one jumps *forward* to the next beat, so the beat count never runs backward. With no audio or no clear beat it coasts at the last tempo (120 BPM to start). `cvState.beatLocked` is true while a confident estimate arrived in the last 4 s. Without AudioWorklet support the per-frame onset strength feeds the same tracker. Checks: `node --test web/tools/beattrack.test.mjs` (synthetic envelopes: tempos, off-beat hats, missing kicks, tempo changes, noise, clock lock) and `node web/checks/beat_check.mjs [bpm ...]` (a synthetic track through the real worklet in headless Firefox; fails unless every kick lands within 30 ms of a beat).

**The autopilot runs on this clock.** `settings.json` holds are in bars (`fgHoldBars` 16, `bgHoldBars` 32) and fades in beats (`fgFadeBeats` 4, `bgFadeBeats` 8). A change starts `FadeBeats` before the first bar line after the hold and finishes on it, so a new look arrives on the bar. The next preset (with its chain and transition) is built during the hold, so the change is not delayed by fetching. Bar lines are every 4 beats of the clock's own count; the clock does not know where the music's bar starts (downbeat detection is not done).

**Song sections (`web/energy.js`).** `EnergyWatch` follows the low end (0.7 bass + 0.3 amp): a 1.5 s level against a 20 s reference. A *breakdown* is the level under 45% of the reference for 3 s (not during the first 20 s, and not in silence); the reference freezes during it, so a *drop* is the level back above 85% of the pre-breakdown reference after at least 4 s (a shorter dip just ends the breakdown, and a calm look it brought in leaves again at the next bar line). A breakdown longer than 60 s becomes the new normal. `dsp.js` publishes `cvState.section` and `cvState.drops` (and `cvState.energy`, the watcher itself, for tuning); on a breakdown the autopilot brings in a look tagged `ambient` or `build` (from either playlist) at the next bar line and builds a `hard` / `bass-gated` look, which cuts in on the drop with a half-beat crossfade through `settings.json` `dropTransition` (`laser_drop_flash`, kept out of the regular `transitions` rotation). HOLD overrides both. `node --test web/tools/energy.test.mjs`; `ui_check.mjs` drives a breakdown and a drop through the real page.

---

## 4. Browser-Side Web Audio DSP Graph (`dsp.js`)

In standalone web mode or Autopilot mode, the client performs real-time audio analysis on the Icecast stream (`https://radio.spaz.org:8060/radio.ogg`) via the Web Audio API:

```
Audio Element (Icecast stream)
     │
     ▼
createMediaElementSource() ──► GainNode (Rotary Volume Dial: V²) ──► AudioDestination
     │
     ├─► BiquadFilter (Lowpass 150Hz)  ─► AnalyserNode ─► RMS (audio_bass, audio_flux_bass)
     ├─► BiquadFilter (Bandpass ~1kHz) ─► AnalyserNode ─► RMS (audio_mid, audio_flux_mid)
     ├─► BiquadFilter (Highpass 5kHz)  ─► AnalyserNode ─► RMS (audio_high, audio_flux_high)
     ├─► Broadband AnalyserNode ──────────────────────► RMS (audio_amp, audio_flux_amp)
     └─► AudioWorklet lsd-onset (10 ms onset envelope) ─► BeatTracker ─► BeatClock
                                                          (tempo, phase)  (totalBeats, bpm, beatLocked)
```

The `audio_*` values feed the modulators (`evaluator.js`) each frame; `totalBeats` and `bpm` drive every `lfo`/`seq`/`beatSine` modulator and the autopilot.

---

## 5. Desktop-to-Web Synchronization & Drift Tracking

To maintain 1:1 visual parity without manual dual-maintenance:
- **Authoritative Source**: Desktop GLSL shaders (`src/main/resources/shaders/`, `library/sources/`) and algorithmic Kotlin math files (`Evaluators.kt`, `WebPresetSerializer.kt`) are the sole sources of truth. (`icosa_h3` and `dynamic_spiral` are now pure ISF v2.0 shaders rather than Kotlin-backed generators — see `DECISIONS.md`'s "Standardization on Pure ISF v2.0 Shaders" — so their web ports are tracked as shader files, not algorithm files.)
- **Sync Manifest (`web/sync_manifest.json`)**: Authoritative mapping of desktop files to WebGL2 / ES module equivalents with tracked SHA-256 hashes.
- **Sync CLI Tool (`scripts/sync_web.py`)**:
  - `--check`: Compares actual files against manifest hashes and transpiled sources; fails with exit code 1 if drift exists.
  - `--apply`: Automatically transpiles desktop `#version 330 core` GLSL shaders to WebGL2 `#version 300 es` (`precision highp float;`).
  - `--mark-synced <target>`: Updates recorded hashes for verified manual Kotlin-to-JS algorithm ports.
- **Evaluator parity (`web/evaluator.js`)**: a port of `ModulatableParameter.evaluate()`, `Evaluators.kt` and `WaveformMath.kt`. It is checked by value, not by hash: `WebEvaluatorVectorsTest` runs the real desktop evaluator under simulated time and writes `web/tools/evaluator_vectors.json` (and fails when that file is stale, so a desktop change cannot go unnoticed); `node --test web/tools/evaluator.test.mjs` replays the vectors through the JS port. After changing desktop modulation, regenerate with `UPDATE_WEB_VECTORS=1 ./gradlew test --tests '*WebEvaluatorVectorsTest*'`, then fix the port until the node test passes. `beatPhase` and `sampleAndHold` are no longer registered in `CVRegistry`, so the desktop skips them and the web does not implement them.
- **ISF assets and the catalog**: `sync_web.py --apply` also copies `default_filters` and `default_transitions` into `web/shaders/fx/` and `web/shaders/transitions/` (same transpile: ES 3.00 header, uniform initialisers dropped) and regenerates `web/catalog.json`, the list of sources, filters and transitions the client can load. Ids are file stems, the same ids the desktop registries use. `--check` fails if the catalog is stale.
- **Content formats (`web/preset.js`, `web/mandala.js`)**: the autopilot reads the desktop's own formats. `web/presets/*.lsd` are `DeckPresetDto` files (a preset carries no FX); `parseDeckPreset` keeps each `ParameterDto` for `evaluator.js` and resolves `randomizeBase` once at load. FX come from `.lsdfxchain` (`FXChainDto`) files in `web/content/fxchains/` (copied from `defaults/fx_chains`) and `web/fxchains/` (web-authored, `web-*`; the desktop's stock chains have no modulation, so the rotation uses reactive `web-*` versions of them, generated in batch 7 of `gen_web_presets.py`; no web chain strobes, for photosensitive viewers); transitions from `.lsdtrans` in `web/content/transitions/`. `web/catalog.json` lists all of them. A playlist line is `preset` or `preset | fxchain`; with no pin, `settings.json` `fxChains` rotates (empty = no FX) and `transitions` picks each crossfade's transition. Mandala is evaluated by `mandala.js`, a port of `Mandala.kt`'s recipe selection and uniforms over `web/mandala_recipes.json` (generated from `MandalaLibrary.kt`); `web/tools/mandala_vectors.json` pins the selection to the desktop. Validate authored content with `node --test web/tools/content.test.mjs` (`validate_content.mjs` checks ids, parameter names against the shader headers, ParameterDto fields and modulators; this includes the desktop's stock chains and transitions copied into `web/content/`, so a stock chain naming a parameter its effect no longer has fails the test, where the desktop would silently ignore it). `node web/checks/gpu_bench.mjs [WxH ...]` times every source on this machine's GPU (`gl.finish()` per frame, so the throttled headless frame timer does not matter).
- **REACT and the desktop Audio Response**: both scale the audio CV signals before any modulator reads them (web: `renderer.js` multiplies `evalCtx.cv` by `viewerState.react`; desktop: `AudioEngine.response` after auto gain), so `web/evaluator.js` has no reactivity input of its own. The desktop auto gain (`AudioAgc`) is not ported: `web/dsp.js` still normalises bands by the fixed 0.25 / 0.05 / 0.1 scales.
- **Authoring web presets (`scripts/gen_web_presets.py`)**: the `web-*` presets and chains are generated from short definitions (the generated files are the source of truth once committed; hand edits are fine). Recipes: `pulse(beats, depth)` is an envelope that peaks on the beat; `lfo(beats, depth, wave)` is a beat-synced LFO (period in beats; 16 = four bars); `kick`/`hat` follow `audio_flux_bass`/`audio_flux_high`; `bass`/`mid`/`high`/`amp` follow the band levels with attack/decay smoothing; `gate(source, threshold, depth)` stays silent until the signal passes the threshold. **A gate only works on a parameter whose base is its minimum** (0 = "off"): below the threshold the amount goes negative and clamps at the minimum, so on any other base the silent value dips below base. Parameters in the shaders are normalised 0..1 or -1..1, so ADD depths are fractions of the range (half the range on bipolar parameters); `lfo` on a unipolar parameter sweeps base..base+depth. Tag a preset with the behaviours it promises (`beat`, `bar`, `bass`, `kick`, `mid`, `high`, `amp`, `bass-gated`); `node --test web/tools/reactivity.test.mjs` verifies each tag is backed by a parameter that moves. The same test is the **rotation gate**: every preset in a playlist and every chain in `settings.json` `fxChains` or a playlist pin must move with some audio signal or with the beat (a beat-synced LFO counts), so nothing deaf reaches the web TV. It also fails on any modulator on a **rate parameter** (a name ending in `Speed`, `Spin` or `Rate`) that the shader does not integrate: `TIME * Speed` moves the phase by `TIME` times the change, so after a few minutes on air even a small wobble jumps the picture every frame. Every stock source and filter now integrates its rates (`LSD_PHASES`, see [rendering.md](rendering.md#6-integrated-phases-lsd_phases)), so the check only bites on a new shader that multiplies by `TIME`. Each source has a groove, a hard and an ambient preset (ambient ones go in the BG playlist). Presets of sources that fill the frame with colour are tagged `fullframe` (`FULL_FRAME_SOURCES` in the generator); `web/pairing.js` makes the autopilot skip chains with a feedback or bloom slot for them, since those wash a full frame out to one flat colour, and presets of sources drawn as fine dots or thin lines (`SPARSE_SOURCES`: dynamic_spiral, harmonograph) are tagged `sparse` and skip chains with pixelate, halftone, polar_tunnel, luma_displace or directional_blur, which average or smear them away to black; and `render_check` picks chains the same way; `content.test.mjs` validates names and ranges, and `node web/checks/render_check.mjs` renders every preset (look at the PNGs: sources differ in how much they show at default settings; `hyper_slice` blows out under strong feedback, `gyroid_hyperspace` is black unless Brightness/Zoom are raised, a solid gyroid surface is mostly black). `RC_EXTRA=specs.json RC_ONLY=name` renders ad-hoc looks, and `RC_LOUD=1` adds a `<name>_loud` frame per preset with the audio signals held high, to compare against the silent frame.
- **Render graph (`web/isf.js`, `web/graph.js`)**: `isf.js` wraps ISF source into GLSL ES 3.00 (a port of `ISFParser.buildGLSLFragmentShader`) and runs multi-pass shaders (`PASSES`, `TARGET`, `PERSISTENT` ping-pong, `FLOAT`, `$WIDTH`/`$HEIGHT` expressions). `graph.js` is `Renderer.renderDeck`/`renderMixer`: deck source, 3-slot FX chain, transition, composite over BG, master FX chain. Assets compile lazily on first use (`Library`), and a not-yet-loaded effect is skipped for those frames. Mandala is the only source drawn outside the graph. `IMPORTED` assets and `audio`/`audioFFT` inputs are not supported (nothing bundled uses them).
- **No signal (`web/dsp.js`, `shaders/crt_post.frag`)**: with the TV on, a stream that failed, stalled or stayed under 0.004 amp for 4 s sets `cvState.noSignal`; the CRT pass fades to snow (`uNoSignal`), the OSD says NO SIGNAL, and the stream is reloaded with a fresh URL every 10 s until it plays. Not during a live broadcast. `?nostatic` turns it off for the headless checks, which have no audio.
- **GPU tier (`web/renderer.js`, `settings.json` `heavySources` / `gpuProbe`)**: at the first power-on the renderer draws `gpuProbe.source` (`pseudo_kleinian`, the heaviest) at 960x540 with `gl.finish()` after each frame: two untimed warm-up frames, then up to four timed ones, stopping after one if it is over twice `maxMs`. Only if the average is at most `maxMs` (12 ms) does `autopilot.setHeavyAllowed(true)` let presets of the `heavySources` into the rotation (playlist picks, CH up, breakdown and drop picks); otherwise they are skipped. Until the probe has run, heavy sources are off. `?heavy=1` / `?heavy=0` override it; `window.LSD.gpu` shows `{state, ms, heavy}`. The list comes from `node web/checks/gpu_bench.mjs` on an Intel UHD Tiger Lake GT1 (sources over ~15 ms at 768x432, plus Lenia and Reaction-Diffusion, whose fixed-size grids cost ~17-19 ms at any resolution); the probe measured 52 ms there.
- **Render resolution and prewarm (`web/renderer.js`)**: decks and the mixer render at a fraction of the canvas, capped at 1920x1080 worth of pixels, and the CRT pass scales up. The fraction steps through 1.0 / 0.85 / 0.7 / 0.55 / 0.4: down after 2 s of frames averaging over ~22 ms, up after 15 s under ~17.5 ms, at most every 3 s; a step up that has to be undone becomes the ceiling, so it never oscillates (each step reallocates the targets, which resets feedback). `window.LSD.renderSize()` reports `[w, h, scale]`. `autopilot.setPrewarm` gets a function that starts loading and compiling a queued look's source, FX and transition (`Library.get`), called when the next look is built during the hold.
- **Wake lock (`web/ui.js`)**: a `screen` wake lock is held while powered on, taken again on `visibilitychange` (browsers drop it when the page is hidden) and released at power off; `viewerState.awake` reports it.
- **Media Session (`web/ui.js`)**: metadata = Icecast title, artist Spaz Radio, album `Liquid LSD · <look>` (or LIVE), icons as artwork; updated on power, look and title changes. play/pause/stop press the power switch, nexttrack/previoustrack are CH up/down (locked while live like the buttons).
- **App and kiosk (`web/manifest.webmanifest`, `web/sw.js`, `web/ui.js`)**: the manifest makes the TV installable (fullscreen, the 192/512 icons). `sw.js` is network first for every same-origin GET (updates always land when online) with the cached copy as the offline fallback; the app shell is cached at install one file at a time, and other origins (stream, Icecast status, relay) are never intercepted. Bump `CACHE` to drop old copies. It is not registered under `?nolive` (the headless checks). `?kiosk` adds `body.kiosk` (no TV body, picture fills the window, cursor hidden after 3 s idle) and presses power after load; when the browser blocks audio, the OSD asks for a click or key and the first one starts the sound.
- **Service menu (`web/menu.js`, `web/ui.js`)**: `createMenu(screen, rows)` draws rows `{label, value(), step?(dir), run?(), locked?()}` over the picture; while open it owns the keyboard (the CH arrows do nothing) and the OSD is hidden. ui.js rows: SYNC (`viewerState.syncMs`, -100..+400 ms, 10 ms steps), TAP SYNC, Announce looks (`viewerState.announce`, off by default: a new look is named only when the viewer pressed CH/FX in the last 4 s, or on I / a picture click), REACT (25-400%, default 200%) and TIME (drive the dials), Reduced motion and Big GPU looks (`viewerState.reducedMode` / `heavyMode`: auto / on / off; heavy on/off overrides the GPU probe, which only applies itself in auto), Reset, Exit. Each row's `help` is its hover tooltip and the menu's bottom line while selected. Stored in localStorage (`lsd.sync`, `lsd.announce`, `lsd.reduced`, `lsd.heavy`).
- **SYNC (`web/sync.js`)**: `heardBeats(now, syncMs)` is the beat the viewer hears (live: `clock.read(now - sync)`; standalone: `totalBeats` extrapolated from `cvState.beatsAtMs`, minus sync); the renderer evaluates modulators on it and on the audio signals read `syncMs` late from a `DelayLine` (a negative SYNC advances the beat only). The beat LED uses it too. TAP SYNC: 8 taps, errors folded into [-100 ms, beat - 100 ms) so a long delay is not read as early, median = SYNC (rounded to 10 ms), refused if the middle taps spread over 150 ms or the beat is not locked. `node --test web/tools/sync.test.mjs`.
- **Reduced motion (`viewerState.reducedNow`, `autopilot.setReducedMotion`)**: auto follows `prefers-reduced-motion` (live, via its change event). When on: the renderer caps REACT at 1.2 (the default is 2.0); the autopilot skips looks tagged `hard`, chains tagged `strobe` / `glitch` and the `luminous_flash` / `cyber_datamosh` / `film_burn` transitions, makes no drop cut, and doubles crossfade length; turning it on rebuilds the queued look.
- **Tempo-aware picks (`autopilot.tempoWeight`, `weightedPick`)**: with a locked beat and random order, the next foreground look is a weighted random pick among playable lines: < 95 BPM ambient/groove/mid x3, hard x0.3; 95-125 groove x2; > 125 hard/kick/hat x2.5, ambient x0.5; everything else x1. Unlocked or sequential order: unchanged. Tested in `tools/autopilot.test.mjs`.
- **Answering chains (`pairing.js` `dominantBand`, `bands`, `chainAnswers`)**: audio modulators are grouped into bands (low = bass + kick, mid, high). A chain whose modulation follows only the look's dominant band is skipped for it (the autopilot's `pickChain` falls back to any fitting chain if none answers). With today's content 18 of 35 chains answer a low-end look. `reactivity.test.mjs` checks every rotation look has an answering chain and at least a third of the chains answer low-end looks; `render_check` picks the same way.
- **LIVE title card (`web/ui.js`, `index.html` `#title-card`)**: on `lsd-broadcast-status` live, a 4.5 s CSS ident (scanline roll-in) shows LIVE and `autopilotState.broadcaster`; the OSD keeps the name while live. The name comes from `preset.broadcaster` (see the wire protocol).
- **Branding (`index.html`, `tv.css`, `ui.js`)**: `#nameplate` (link, excluded from the click-to-power area, hidden in kiosk); `#ident` at the first power-on of a page load (logo `icon-512.png`, 3.6 s); `#about` (MENU › ABOUT; the QR code for the site is inline SVG, made with segno); `#osd-brand` on I / picture clicks only; `#bumper` every 30 min while on and not live (`BUMPER_EVERY_MS`, timer restarts at power-on); the LIVE card's "from Liquid LSD". While a card is up the OSD steps aside (`.card-up`). Open Graph / Twitter tags point at `og-tv.jpg` (1200x630 screenshot of the TV).
- **Viewer controls (`web/ui.js`)**: CH up/down, FX, HOLD, TIME (bars per look) and REACT (a factor on every `audio_*` signal in `renderer.js`, so gates and depths respond as if the music were louder or quieter) call `advanceForeground` / `previousForeground` / `nextFxChain` / `setHold` / `setHoldBars` in `autopilot.js`. Both are `changeChannel(±1)`: the next playable playlist line up or down from the one on air (or the one a quick earlier press is still building), whatever the autopilot's own order; a line in the on-air history comes back with its deck, chain and transition. The OSD listens for `lsd-now-showing` (fired when a crossfade starts) and `lsd-broadcast-status` (fired when the relay goes live or offline; the controls lock while live). Now playing comes from the Icecast `status-json.xsl` every 30 s. `node web/checks/ui_check.mjs` drives every control in the real page in headless Firefox, including the live lock.
- **Headless checks (`web/checks/`)**: `node web/checks/shader_check.mjs` compiles every catalog shader in headless Firefox; `node web/checks/render_check.mjs [outDir]` drives the real app through every source, filter, transition and bundled preset, writes PNGs, and fails on blank frames, console errors or GL errors. Both need only `firefox` and `node`; software GL is fine.
- **Beat and audio parity (`web/beatclock.js`, `web/dsp.js`)**: live mode runs `BeatFlywheel` on the wire clock (`getSynchronizedTotalBeats` semantics plus blending of each anchor against network jitter); standalone runs `BeatClock` on the stream's tracked beat (section 3). `dsp.js` publishes `audio_*` and `audio_flux_*` with the desktop's scaling (`AudioEngine`: RMS / 0.25, band flux / 0.05, amp flux = (2 bass + 0.8 mid + 0.3 high flux) / 0.1). Beat tracking only runs from the radio stream, so it only matters standalone.
- **Continuous Integration**: Gradle task `./gradlew checkWebSync` and JVM unit test `WebSyncTest.kt` guard against drift on every build.

---

## 6. Publishing the Web TV

The web TV is served at **https://spaz.org/tv/**: Apache on spaz.org has `Alias /tv /www/spaz/htdocs/lsd-relay/public`, a folder `gj` can write over SSH (key login). `scripts/publish_web_tv.sh` rsyncs `web/` there (checksums, so only changed files go; `web/checks/` and `web/tools/` stay behind). `--dry-run` shows the changes, `--delete` also removes server files that are no longer in `web/` (older deployments left prototype shaders and desktop presets there). The relay's code lives next to it in `/www/spaz/htdocs/lsd-relay/` and is deployed separately (section 7).

## 6b. Embed Mode (`?embed`)

`index.html?embed` is the web TV as a component, built for Spaz Radio's Android WebView (plan: `.planning/spazradio-embed-plan.md`). `embedmode.js` exports the flag; it implies the kiosk layout and adds `body.embed` (`tv.css`: chrome hidden, `pointer-events: none` everywhere, so the host's panels own all touches).

- **Audio in:** `dsp.js` skips the `<audio>` element and builds the same analysis graph from a `feed-worklet.js` node (`lsd-feed`, no inputs) whose output goes to `analysisIn`; the analysers also hang off a muted gain to the destination so the graph keeps being pulled. `feedring.js` is the worklet's mono ring buffer with a linear resampler (stream rate to context rate), a 40 ms prefill, and a skip-ahead to 80 ms when more than 300 ms is queued, so the picture stays close to the sound. `feedPCM(data, rate)` accepts Int16Array, Float32Array, ArrayBuffer (little-endian int16) or base64 and posts to the worklet (transferring buffers it decoded itself). It returns false until the graph exists (`LSD.feedReady()`; `lsd-embed-ready` event).
- **Silence is not "no signal":** `updateSignal` pins `cvState.noSignal` false in embed mode; there is no reconnect logic.
- **What is off:** microphone (`micAvailable()` false), service worker, `status-json` polling, media session, wake lock, bumper, ident, and the relay (`autopilot.setRelayEnabled`, off until the `live` option).
- **Host API (`embed.js`, installed on `window.LSD` by `renderer.js`):** `feed`, `feedReady`, `setOption(name, value)` (`quality`, `reactivity`, `reducedMotion`, `live`, `sync`), `pause`, `resume`, `next`, `prev`, `hold`. `quality: 'low'` turns heavy looks off and floors the render scale at the third step (`adapt.ceiling`); `pause` stops the render loop and suspends the AudioContext, `resume` restarts both and resets the frame clock.
- **Frame rate over sharpness:** in embed mode `renderer.js` uses a 1280x720-pixel budget, seven scale steps down to 25%, steps down after 1 s above 19 ms (not 2 s above 22 ms) with 1.5 s between steps, and forces `gpu.heavy` false (the probe's `gl.finish()` returned in 0.4 ms on a Pixel 7a, so it can't tell). `?embed&debug` shows fps, render size, scale and heavy state in a corner and logs a status line every 2 s.
- **Sync:** the host's sound reaches the speaker after the audio sink's buffer, so the feed leads what is heard; set the `sync` option to delay the picture to match.
- **Checks:** `node --test web/tools/feedring.test.mjs`; `node web/checks/embed_check.mjs` (headless Firefox: loads the page with `?embed`, pushes a synthetic 124 BPM kick track in real time, expects the signals, the beat lock, no snow in silence, options, pause/resume and a non-black picture).

## 6c. Content Updates for the Spaz Radio App

`scripts/publish_web_tv.sh` also publishes a signed manifest to `https://spaz.org/tv/update/` so the app can
fetch new presets and playlists without an app release. `scripts/make_content_manifest.py` lists the data
files of `web/` (presets, playlists, shaders, FX chains, transitions, `catalog.json`, `settings.json`,
`mandala_recipes.json`, `autopilot.json`) with sha256 and size; JS/HTML/CSS are never listed (the engine ships
with the app). `manifest.json.sig` is an ECDSA P-256/SHA-256 signature over the exact manifest bytes. The
private key is `~/.config/liquid-lsd/content-signing.pem` (not in the repo; back it up, losing it means an app
release to change the key); the public key is `scripts/content_signing_pub.pem`.

Publishing validates content first (`web/tools/content.test.mjs`) and uploads the manifest last. `CONTENT_ENGINE`
in the script is the engine level the content needs: bump it when new content depends on a web feature old apps
lack, so those apps skip the update. `rsync --delete` leaves `update/` alone.

## 7. Relay Server Operations (`server/server.js`)

The relay server is a zero-dependency Node.js service using `ws`.

### Environment Variables
- `LSD_PORT`: Port to listen on (default: `9004`).
- `LSD_HOST`: Host interface to bind to (default: `0.0.0.0`).
- `LSD_TOKEN`: Broadcaster authorization secret. **Required**: the relay exits at startup without it, so no shared default can let a stranger broadcast.

### CLI Management
```bash
# Run standalone
./server/lsd_relay start

# Or deploy via PM2
cd server
npm install
pm2 start server.js --name lsd-relay
pm2 save && pm2 startup
```

### Health & Monitoring Endpoint
`GET /health` returns live telemetry JSON:
```json
{
  "status": "ok",
  "viewers": 42,
  "broadcasterConnected": true,
  "uptime": 128450
}
```
- **Fullscreen and microphone (`web/ui.js`, `web/dsp.js`)**: `S` / MENU › Fullscreen / double-click all run `toggleFullscreen` on `#tv-screen-area`. `A` / MENU › Input run `toggleMic` → `dsp.setMic`: every analyser and the onset worklet hang off one `analysisIn` gain that the radio's element source and the `getUserMedia` stream (echo cancellation, noise suppression and AGC off) both feed; only the radio also goes to the volume gain, so the mic never plays back. The radio element is paused while the mic is in, `reconnect()` is skipped, silence still shows NO SIGNAL, `stopAudio` releases the mic, and the `lsd-mic` event refreshes the menu (also when the browser revokes the mic). The headless check only verifies the menu rows; the mic itself needs a real browser.
