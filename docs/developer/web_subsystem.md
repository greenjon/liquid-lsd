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

Every message carries `clock: {beats, bpm}`, the desktop's synchronised beat count and tempo at the moment of sending. The browser stores it with the local `performance.now()` at arrival and extrapolates (`beats + elapsed * bpm / 60`) for every `lfo`/`beatSine` modulator and any web-side evaluation, so in live mode it needs no beat detection of its own. Network latency (tens of milliseconds) is not compensated; the once-a-second heartbeat keeps drift from accumulating. Outside a live broadcast the client free-runs at the tempo estimated by `dsp.js` (phase-locked beat tracking is plan phase 4).

---

## 4. Browser-Side Web Audio DSP Graph (`dsp.js`)

In standalone web mode or Autopilot mode, the client performs real-time audio analysis on the Icecast stream (`https://radio.spaz.org:8060/radio.ogg`) via the Web Audio API:

```
Audio Element (Icecast stream)
     │
     ▼
createMediaElementSource() ──► GainNode (Rotary Volume Dial: V²) ──► AudioDestination
     │
     ├─► BiquadFilter (Lowpass <180Hz)  ─► AnalyserNode ─► RMS Envelope (audio_bass)
     ├─► BiquadFilter (Bandpass ~1kHz)  ─► AnalyserNode ─► RMS Envelope (audio_mid)
     ├─► BiquadFilter (Highpass >5kHz)  ─► AnalyserNode ─► RMS Envelope (audio_high)
     └─► Broadband AnalyserNode ───────► RMS Envelope (audio_amp)
                                              │
                                              ▼
                                   Dual-Envelope Follower
                                   (Fast Attack / Slow Baseline)
                                              │
                                              ▼
                                   IOI Median Filter & PLL
                                   (beatPhase 0..1, beatSine)
```

The computed CV envelopes are bound directly to WebGL shader uniforms (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`, `beatPhase`, `beatSine`, `trigger_onset`) each frame.

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
- **Content formats (`web/preset.js`, `web/mandala.js`)**: the autopilot reads the desktop's own formats. `web/presets/*.lsd` are `DeckPresetDto` files (a preset carries no FX); `parseDeckPreset` keeps each `ParameterDto` for `evaluator.js` and resolves `randomizeBase` once at load. FX come from `.lsdfxchain` (`FXChainDto`) files in `web/content/fxchains/` (copied from `defaults/fx_chains`) and `web/fxchains/` (web-authored, `web-*`); transitions from `.lsdtrans` in `web/content/transitions/`. `web/catalog.json` lists all of them. A playlist line is `preset` or `preset | fxchain`; with no pin, `settings.json` `fxChains` rotates (empty = no FX) and `transitions` picks each crossfade's transition. Mandala is evaluated by `mandala.js`, a port of `Mandala.kt`'s recipe selection and uniforms over `web/mandala_recipes.json` (generated from `MandalaLibrary.kt`); `web/tools/mandala_vectors.json` pins the selection to the desktop. Validate authored content with `node --test web/tools/content.test.mjs` (`validate_content.mjs` checks ids, parameter names against the shader headers, ParameterDto fields and modulators).
- **Render graph (`web/isf.js`, `web/graph.js`)**: `isf.js` wraps ISF source into GLSL ES 3.00 (a port of `ISFParser.buildGLSLFragmentShader`) and runs multi-pass shaders (`PASSES`, `TARGET`, `PERSISTENT` ping-pong, `FLOAT`, `$WIDTH`/`$HEIGHT` expressions). `graph.js` is `Renderer.renderDeck`/`renderMixer`: deck source, 3-slot FX chain, transition, composite over BG, master FX chain. Assets compile lazily on first use (`Library`), and a not-yet-loaded effect is skipped for those frames. Mandala is the only source drawn outside the graph. `IMPORTED` assets and `audio`/`audioFFT` inputs are not supported (nothing bundled uses them).
- **Headless checks (`web/checks/`)**: `node web/checks/shader_check.mjs` compiles every catalog shader in headless Firefox; `node web/checks/render_check.mjs [outDir]` drives the real app through every source, filter, transition and bundled preset, writes PNGs, and fails on blank frames, console errors or GL errors. Both need only `firefox` and `node`; software GL is fine.
- **Beat and audio parity (`web/beatclock.js`, `web/dsp.js`)**: live mode runs `BeatFlywheel` on the wire clock (`getSynchronizedTotalBeats` semantics plus blending of each anchor against network jitter); standalone integrates the browser's tempo estimate into `cvState.totalBeats`. `dsp.js` publishes `audio_*` and `audio_flux_*` with the desktop's scaling (`AudioEngine`: RMS / 0.25, band flux / 0.05, amp flux = (2 bass + 0.8 mid + 0.3 high flux) / 0.1). Beat *detection* in `dsp.js` only runs from the radio stream, so it only matters standalone.
- **Continuous Integration**: Gradle task `./gradlew checkWebSync` and JVM unit test `WebSyncTest.kt` guard against drift on every build.

---

## 6. Relay Server Operations (`server/server.js`)

The relay server is a zero-dependency Node.js service using `ws`.

### Environment Variables
- `LSD_PORT`: Port to listen on (default: `9004`).
- `LSD_HOST`: Host interface to bind to (default: `0.0.0.0`).
- `LSD_TOKEN`: Broadcaster authorization secret (default: `lsd25`).

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
