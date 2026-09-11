# Web Broadcast

> **Heads up:** This is an experimental feature aimed at a pretty specific use case. To use it, you need access to a relay server and a broadcaster token. If you don't know what those are, this page probably isn't for you yet — but read on if you're curious.

The web broadcast system lets you stream your live visual performance to anyone with a web browser, in real time. Instead of sending a video feed (which requires encoding, bandwidth, and introduces latency), Liquid LSD sends the *parameters* of your visuals — the numbers that drive the shaders. The browser re-renders the visuals locally on the viewer's own GPU. The result is zero encoding latency and full 60fps rendering on the viewer's end.

---

## How it works

```
Your Liquid LSD app
      |
      | (lightweight parameter stream over WebSocket)
      v
Relay server
      |
      | (fanned out to all viewers)
      v
Each viewer's browser — renders the visuals locally using WebGL
```

The relay server caches the current visual state so new viewers who join mid-show get the right picture immediately.

---

## Setting it up

Open **Settings** (`Ctrl+,`) and go to the **BROADCAST** tab:

| Setting | What it does |
|---------|-------------|
| **Server URL** | The address of your relay server (e.g. `wss://relay.example.com`) |
| **Broadcaster Token** | The secret key that authenticates you as the broadcaster |
| **Target Rate** | How many parameter updates to send per second (5–60). Higher = smoother, more network traffic |
| **Auto-Connect** | If enabled, broadcasts automatically when the app starts |

These settings are saved between sessions.

Once the URL and token are set, **Output → Web Broadcast** appears in the top menu. Toggle it to start or stop broadcasting.

---

## Status indicators

The title bar shows your broadcast status:

- **[CONNECTING]** (yellow) — Handshaking with the relay.
- **[LIVE]** (red pulsing) — Actively streaming.
- **[LIVE ERR]** (red warning) — Connection dropped or bad token.

If the connection drops, Liquid LSD reconnects automatically in the background without interrupting your audio or visuals.

---

## The web player

The standalone browser client (`web/index.html`) presents the visualizer in a retro CRT television shell.

**Power switch:** Click it to turn the visualizer on. The browser requires this interaction to start Web Audio (standard browser security policy). Turning the TV on triggers a CRT warmup animation; turning it off plays a vintage beam-collapse animation.

**Volume dial:** Drag the rotary dial up or down to adjust volume. The curve is tuned to feel perceptually linear — small moves at the bottom, bigger moves at the top.

**Fullscreen:** Double-click the TV screen to expand to full-screen. Double-click again or press `Esc` to go back to the TV shell.

The viewer's browser also connects to a live audio stream and runs its own audio analysis, keeping the visuals reactive to music even when you're not broadcasting.

---

## Autopilot mode

When no broadcaster is connected, the web player switches to **Autopilot** — running its own curated visual playlist against the live audio stream. It keeps the visualizer interesting and audio-reactive 24/7 without needing a human operator.

When a broadcaster connects, Autopilot smoothly hands off control to the live stream. When the broadcast ends, it picks up again automatically.

The web presets and playlists used for Autopilot live in `web/presets/` and `web/playlists/`, separate from your desktop preset library.
