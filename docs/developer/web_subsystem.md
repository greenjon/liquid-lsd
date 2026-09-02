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
│                               ▼ (25 Hz Throttled)            │
│  BroadcastEngine-IO Thread ──► java.net.http.WebSocket       │
└───────────────────────────────┬──────────────────────────────┘
                                │ WSS: ?role=broadcast&key=<token>
                                ▼
┌──────────────────────────────────────────────────────────────┐
│ NODE.JS RELAY SERVER (server/server.js)                      │
│                                                              │
│  - Authentication & Role Verification                        │
│  - Active Broadcaster Arbitration                            │
│  - Full State Caching (`state_full`)                         │
│  - Low-Latency Fan-Out Distribution                          │
└───────────────────────────────┬──────────────────────────────┘
                                │ WSS: ?role=viewer
                                ▼
┌──────────────────────────────────────────────────────────────┐
│ BROWSER CLIENT (web/)                                        │
│                                                              │
│  - Autopilot Playlist Manager (autopilot.js)                 │
│  - Web Audio DSP Graph & Beat Tracker (dsp.js)               │
│  - Dead-Reckoning Extrapolation (renderer.js)                │
│  - WebGL2 Multi-Pass Pipeline & CRT Post-Processing          │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. WebSocket Wire Protocol

The relay server and browser clients communicate using compact JSON messages.

### Message Types

#### 1. Full State Snapshot (`state_full`)
Dispatched immediately when the broadcaster connects, when a new preset is loaded, or when a new viewer joins mid-session. Contains complete deck parameters, shader source selections, LFO states, and CV routing matrices.

```json
{
  "type": "state_full",
  "timestamp": 1725234500123,
  "deckA": {
    "source": "mandala",
    "params": {
      "petals": 8.0,
      "morph": 1.2,
      "zoom": 1.05,
      "rotSpeed": 0.4
    }
  },
  "deckB": {
    "source": "dynamic_spiral",
    "params": {
      "coils": 12.0,
      "decay": 0.96
    }
  },
  "mixer": {
    "crossfader": 0.5,
    "blendMode": "ADD",
    "masterGain": 1.0
  }
}
```

#### 2. Throttled Delta Update (`state_delta`)
Streamed at a configurable rate (default: 25 Hz) during live performance. Transmits only parameters and continuous signals that have changed beyond an epsilon threshold.

```json
{
  "type": "state_delta",
  "timestamp": 1725234500163,
  "integratedTime": 142.845,
  "integratedShear": 12.302,
  "crossfader": 0.52,
  "deckA": {
    "zoom": 1.08
  }
}
```

#### 3. Broadcaster Handshake (`broadcaster_online` / `broadcaster_offline`)
Dispatched by the relay server to all connected viewers when the desktop broadcaster connects or disconnects:
- `broadcaster_online`: Viewers smoothly transition from Autopilot mode to the live broadcaster state, and the TV station badge flips to `SPAZ RADIO • LIVE`.
- `broadcaster_offline`: Viewers smoothly fade back to local autonomous Autopilot playlist cycles.

---

## 3. Dead-Reckoning & Continuous Phase Tracking

To eliminate visual jitter and 60 FPS stutter caused by network packet quantization:
1. The desktop broadcaster transmits continuous monotonically accumulating time variables (`integratedTime`, `integratedShear`).
2. The client-side WebGL2 renderer (`renderer.js`) maintains local dead-reckoning integration: between delta updates, the renderer advances time variables locally using `performance.now()`.
3. When incoming delta packets arrive, the local timeline gently soft-locks to the broadcaster's anchor time without discontinuous phase jumps.

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
- **Authoritative Source**: Desktop GLSL shaders (`src/main/resources/shaders/`, `library/sources/`) and algorithmic Kotlin math files (`Icosahedron.kt`, `Evaluators.kt`, `WebPresetSerializer.kt`) are the sole sources of truth.
- **Sync Manifest (`web/sync_manifest.json`)**: Authoritative mapping of desktop files to WebGL2 / ES module equivalents with tracked SHA-256 hashes.
- **Sync CLI Tool (`scripts/sync_web.py`)**:
  - `--check`: Compares actual files against manifest hashes and transpiled sources; fails with exit code 1 if drift exists.
  - `--apply`: Automatically transpiles desktop `#version 330 core` GLSL shaders to WebGL2 `#version 300 es` (`precision highp float;`).
  - `--mark-synced <target>`: Updates recorded hashes for verified manual Kotlin-to-JS algorithm ports.
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
