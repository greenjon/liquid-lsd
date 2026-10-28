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

Open **Preferences** (`Ctrl+P` / `Cmd+P`). The **Web Broadcast** tab is hidden until you turn on **General → Show Advanced Preferences** (it also appears if a relay is already configured). Then open the tab:

| Setting | What it does |
|---------|-------------|
| **Server URL** | The address of your relay server (e.g. `wss://relay.example.com`) |
| **Broadcaster Token** | The secret key that authenticates you as the broadcaster |
| **Target Rate** | How many parameter updates to send per second (5–60). Higher = smoother, more network traffic |
| **Auto-Connect** | If enabled, broadcasts automatically when the app starts |
| **Broadcaster Name** | Shown to viewers on the web TV's LIVE title card ("LIVE · name"); leave blank for just LIVE. Up to 40 characters |

These preferences are saved between sessions in `lsd-preferences.properties`.

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

**Power switch:** Click it (or anywhere on the TV) to turn the visualizer on. The browser requires this interaction to start Web Audio (standard browser security policy). Turning the TV on triggers a CRT warmup animation; turning it off plays a vintage beam-collapse animation.

**Volume dial:** Drag the rotary dial up or down to adjust volume. The curve is tuned to feel perceptually linear — small moves at the bottom, bigger moves at the top.

**Fullscreen:** Double-click the TV screen, press `S`, or choose MENU › **Fullscreen** to expand to full-screen (the on-screen display comes along). Do any of them again, or press `Esc`, to go back to the TV shell. Choosing it from the MENU closes the menu. In Chrome and Edge, `Esc` closes the menu first if it is open (hold `Esc` to leave full screen right away); other browsers always leave full screen on `Esc`.

**Version:** the MENU title shows the TV's version (v1, v2, …) on the right. If it is older than the one just announced, reload the page to get the latest.

**Microphone:** press `A` or choose MENU › **Input** to make the picture follow your microphone instead of the radio, for a room playing its own music. Your browser asks permission the first time; the sound is only analysed, never played through the speakers. Press `A` again for the radio. Turning the TV off also puts the radio back. Locked during a live broadcast.

**Viewer controls** (Autopilot only; while you broadcast live they are locked and the screen says so):

| Control | What it does | Keys / touch |
|---------|-------------|--------------|
| **CH ▲ / ▼** | The next / previous channel, now (a channel you've seen keeps the FX chain it had; the autopilot itself still picks channels at random) | `→` `↑` `N` / `←` `↓` `P`; swipe left / right on the picture |
| **FX** | Puts the next FX chain on the look on air | `F` |
| **HOLD** | Keeps the current look until you press it again (the background keeps changing) | `H` or `Space`; long-press the picture |
| **TIME** | How long each look holds: 8, 16, 32 or 64 bars (drag, scroll or click) | `[` `]` |
| **REACT** | How strongly the picture reacts to the music, 25-200%, 100% to start (double-click for 100%). The TV levels quiet and loud shows itself, so this is about taste, not volume | `-` `=` |

Click or tap the picture (or press `I`) to show the channel, the look, its FX chain and the track playing on the radio. TIME and REACT are remembered in the browser.

**No signal:** if the radio stream drops or goes quiet, the screen shows snow and NO SIGNAL, and the TV reconnects by itself.

**Big GPU looks:** the most demanding 3D fractals and simulations only play on computers fast enough for them (the TV checks at power-on; MENU › Big GPU looks overrides it). Add `?heavy=1` to the address to force them on, `?heavy=0` to keep them off.

**Screen:** while the TV is on, your computer or phone won't dim or lock (in browsers that support it).

**Lock screen and media keys:** your phone's lock screen and your keyboard's media keys show the show and the look; play/pause is the power switch, next/previous change the look.

**Install as an app:** use your browser's Install / Add to Home Screen; it opens fullscreen like a TV app.

**Kiosk mode:** `index.html?kiosk` shows only the picture (no TV body), turns itself on and hides the mouse pointer; use the keyboard or swipes. Browsers that refuse to start sound on their own get a "click or press a key for sound" prompt.

**MENU:** the MENU button or `M` opens the service menu (arrows or clicks to change, Esc or M to close): **SYNC** (how late the sound reaches you, for Bluetooth and other delayed speakers), **Announce looks** (name each new look on screen; off by default), **REACT**, **TIME**, **Reduced motion** (Auto follows your system setting), **Big GPU looks** (Auto lets the GPU check decide), **Input** (radio or microphone) and **Fullscreen**. The selected row explains itself at the bottom of the menu (and on mouse hover). Settings are remembered in this browser. REACT, TIME and Heavy looks are locked during a live broadcast.

**SYNC:** if the picture's pulses come before the beat you hear (Bluetooth speakers are often 150-250 ms late), open MENU › **TAP SYNC** and tap Space or the picture on 8 beats; or set **SYNC** by hand. It needs the beat light to be green (or a live broadcast).

**Reduced motion:** follows your system's "reduce motion" setting (or MENU › Reduced motion): gentler reaction, no hard looks, no flashes or glitch effects, no cut on the drop, slower changes.

**Going live:** when the desktop starts broadcasting, viewers see a LIVE title card with the broadcaster's name (if one is set in the desktop's Web Broadcast preferences).

**Power:** click anywhere on the TV to turn it on (the browser needs that click to start the sound). Once it's on, a click on the cabinet turns it off; clicks on the picture show the info instead.

**About:** the nameplate under the screen opens the Liquid LSD website. MENU › **ABOUT** tells you about the desktop app (with a QR code for your phone), and **I** shows a Liquid LSD line with the info.

**Beat light:** the second light on the badge blinks on every beat: green when the TV has locked onto the music's beat, amber while it keeps time on its own (quiet passages, or before it has found the beat).

**What viewers see.** Each deck's source and its parameters, the deck FX chains and the master FX chain, the transition and crossfader, the channel levels and the beat all go over the wire, so the web TV follows your mix closely. A few things stay desktop-only: external video sources (Spout, NDI, PipeWire, webcam) show as nothing, and anything the web player does not have (a custom shader you added to your own library) is skipped. Viewers join in the middle of a broadcast on the right beat.

The viewer's browser also connects to a live audio stream and runs its own audio analysis, keeping the visuals reactive to music even when you're not broadcasting.

---

## Autopilot mode

When no broadcaster is connected, the web player switches to **Autopilot** — running its own curated visual playlist against the live audio stream. It keeps the visualizer interesting and audio-reactive 24/7 without needing a human operator. It finds the beat of the radio stream itself, so beat-synced movement lands on the music, and changes looks on the bar: every 16 bars by default (the TIME dial), with the background changing half as often. It also follows the song: when the bass drops out for a breakdown a calm or building look comes in, and when the drop lands a hard look cuts in on it (unless HOLD is on).

When a broadcaster connects, Autopilot smoothly hands off control to the live stream. When the broadcast ends, it picks up again automatically.

The web presets and playlists used for Autopilot live in `web/presets/` and `web/playlists/`, separate from your desktop preset library. Web presets are ordinary desktop `.lsd` files, so you can copy one across in either direction (a preset using only sources the web has). A playlist line can add an FX chain after a bar: `my-preset | my-chain`; the desktop's FX chains and transition presets ship with the web TV.
