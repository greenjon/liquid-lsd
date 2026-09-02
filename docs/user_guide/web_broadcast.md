# Web Broadcasting & Retro TV Player

Liquid LSD features a live WebSocket broadcasting subsystem that streams generative visual performance parameters from your desktop workstation to a standalone browser-based visualizer running in an interactive retro CRT TV shell.

---

## 1. Overview & Architecture

Live web streaming allows remote audiences to experience your visual performance directly in their web browser with zero video stream latency and full 60 FPS client-side rendering.

```
┌─────────────────────────────────┐
│     Liquid LSD Desktop App      │
│  - BroadcastEngine (WebSocket)  │
│  - SettingsPanel (BROADCAST)    │
└────────────────┬────────────────┘
                 │ WebSocket JSON Stream (?role=broadcast&key=...)
                 ▼
┌─────────────────────────────────┐
│     Node.js Relay Server        │
│      (server/server.js)         │
│  - Caches current preset state  │
│  - Multi-client fan-out relay   │
└────────────────┬────────────────┘
                 │ WebSocket JSON Stream (?role=viewer)
                 ▼
┌─────────────────────────────────┐
│       WebGL2 Web Player         │
│  - Retro CRT TV Shell (ui.js)   │
│  - Web Audio DSP (dsp.js)       │
│  - Autopilot Fallback           │
└─────────────────────────────────┘
```

Rather than transmitting a heavy, compressed pixel video feed (which consumes immense server bandwidth and suffers from encoder latency), Liquid LSD broadcasts lightweight mathematical parameter deltas. The viewer's browser executes the WebGL2 shader pipeline and audio reactivity natively on their own GPU.

---

## 2. Broadcasting from the Desktop Workstation

### Configuration Settings

Open **Settings** (`Ctrl+,` or `Cmd+,`) and navigate to the **BROADCAST** tab:

| Setting | Default | Description |
| :--- | :--- | :--- |
| **Server URL** | `http://spaz.org/lsd-relay` | WebSocket or HTTP relay server endpoint (e.g., `ws://relay.example.com:9004` or `http://...`). |
| **Broadcaster Token** | `lsd25` | Shared secret key required to authenticate as the active broadcaster. |
| **Target Rate (FPS)** | `25` | Parameter transmission rate (5–60 Hz). Higher rates provide smoother transitions but increase network packets. |
| **Auto-Connect** | `Off` | If enabled, initiates broadcast connection automatically upon application launch. |

*These settings are persisted across sessions in `lsd-settings.properties`.*

### Starting & Stopping a Live Broadcast

1. **Top Menu**: Select **Output → Web Broadcast** to toggle streaming.
2. **Title Bar HUD Indicator**:
   - `[CONNECTING]` (Yellow): Handshaking with the WebSocket relay server.
   - `[LIVE]` (Red pulsating pill): Actively streaming parameter deltas.
   - `[LIVE ERR]` (Red warning): Connection lost or bad auth token.
3. **Automatic Reconnection**: If the network or relay drops, the desktop engine automatically attempts reconnection in the background on exponential backoff without interrupting your live audio or visual rendering.

---

## 3. Web Player Controls & Retro CRT TV Experience

The standalone web client (`web/index.html`) encapsulates the visualizer in an authentic retro CRT television bezel.

```
┌──────────────────────────────────────────────┐
│  ┌───────────────────────────────┐  ┌─────┐  │
│  │                               │  │ (•) │  │ Power Switch
│  │                               │  └─────┘  │
│  │     WebGL2 CRT Visualizer     │           │
│  │     (Phosphor Glow, Static)   │  ┌─────┐  │
│  │                               │  │ (O) │  │ Rotary Volume Dial
│  │                               │  └─────┘  │
│  └───────────────────────────────┘           │
│  [ SPAZ RADIO • LIVE ]                       │ Station LED Badge
└──────────────────────────────────────────────┘
```

### TV Shell Interactive Controls

- **Power Switch (`ui.js`)**:
  - Clicking the physical power toggle satisfies modern browser autoplay policies for Web Audio.
  - Ignites a realistic **1.5s CRT warmup animation**: a thin horizontal raster line with intense phosphor glow expands vertically until the full scanline image fills the screen.
- **Rotary Volume Dial (`ui.js`, `dsp.js`)**:
  - Dragging the physical dial up or down (or touching and dragging on mobile) rotates the dial between `-150°` and `+150°`.
  - Adjusts Web Audio volume smoothly using a squared attenuation curve ($V^2$) via `GainNode.setTargetAtTime`, ensuring natural, perceptually linear acoustic volume.
- **Station LED Badge**:
  - Displays `SPAZ RADIO • AUTOPILOT` when running client-side scheduled visual loops.
  - Automatically flips to `SPAZ RADIO • LIVE` the moment a live desktop broadcaster connects.
- **Fullscreen Mode**:
  - Double-clicking the TV screen expands the visualizer to borderless, immersive fullscreen projection. Double-click again or press `Esc` to return to the CRT chassis.

---

## 4. Web Audio DSP & 24/7 Autopilot Fallback

### Live Audio Stream Integration (`dsp.js`)
When powered on, the browser connects to the live Icecast audio stream (`https://radio.spaz.org:8060/radio.ogg`). The Web Audio DSP graph splits the incoming stream into:
- **Sub-band Filters**: Lowpass (bass < 180 Hz), Bandpass (mid ~1 kHz), Highpass (high > 5 kHz).
- **RMS Energy Tracking**: Continuously calculates broadband and per-band energy envelopes with peak-hold normalization.
- **Dual-Envelope Beat Follower**: Estimates real-time BPM and beat phase ($0.0 \dots 1.0$), dynamically modulating shader uniforms in the browser.

### 24/7 Autopilot Scheduler (`autopilot.js`)
When no live broadcaster is connected, the web visualizer automatically operates in **Autopilot Mode**:
- Sequentially cycles through curated visual presets defined in `web/autopilot.json`.
- Executes smooth fade-through-black master alpha transitions between presets every 30–60 seconds.
- Modulates all parameters against the live Icecast audio stream so visuals remain dynamically audio-reactive 24/7.
- Smoothly yields control and transitions to live visual parameters the second a broadcaster connects.
