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

**Fullscreen:** Double-click the TV screen, press `F`, or choose MENU › **Fullscreen** to expand to full-screen (the on-screen display comes along). Do any of them again, or press `Esc`, to go back to the TV shell. Choosing it from the MENU closes the menu. In Chrome and Edge, `Esc` closes the menu first if it is open (hold `Esc` to leave full screen right away); other browsers always leave full screen on `Esc`.

**Version:** the MENU title shows the TV's version (v1, v2, …) on the right. If it is older than the one just announced, reload the page to get the latest.

**Microphone:** press `A` or choose MENU › **Audio Input** to make the picture follow your microphone instead of the radio, for a room playing its own music. Your browser asks permission the first time; the sound is only analysed, never played through the speakers. Press `A` again for the radio. Turning the TV off also puts the radio back. It works offline too. Locked during a live broadcast.

**Viewer controls** (Autopilot only; while you broadcast live they are locked and the screen says so):

| Control | What it does | Keys / touch |
|---------|-------------|--------------|
| **VOL** | The volume (drag or scroll); `U` mutes, and again brings it back | `↑` `↓` `U` |
| **CH ▲ / ▼** | The next / previous channel, now (a channel you've seen keeps the FX chain it had; the autopilot itself still picks channels at random) | `→` `N` / `←` `P`; swipe left / right on the picture |
| *(jump)* | A channel picked at random, now | `J` |
| **FX** | Puts the next FX chain on the scene on air | `E` |
| **ROLL** | A new variation of the scene on air: its settings and its effects' roll new values, gliding in to land on the next bar line | `R` |
| *(background)* | The next background, now (MENU › BACKGROUND steps either way) | `B` |
| *(tune by number)* | Type a channel's digits, like a remote: three digits tune at once, fewer after a short pause | `0`-`9` |
| *(channel skip)* | Skips the channel on air and moves on; `X` again within 5 s undoes it; `X` on a skipped channel restores it | `X` |
| **HOLD** | Keeps the current scene until you press it again (the background keeps changing); its light is on while it holds | `H`, or `Space` when no tape is in; long-press the picture |
| **TIME** | How long each scene holds: 8, 16, 32 or 64 bars (drag, scroll or click) | `[` `]` |
| **REACT** | How strongly the picture reacts to the music, 25-200%, 100% to start (double-click for 100%). The TV levels quiet and loud shows itself, so this is about taste, not volume | `-` `=` |

**The controls:** PWR, VOL and CH on the left; the scene panel (FX, ROLL, HOLD, BARS and REACT) in the middle; and on the right the radio face, a display window over a row of piano keys, like a car radio's. The keys choose what shows over the picture: **OFF · INFO · MENU · SCHED · TAPES · ?**. One key is down at a time, and its label lights. Press the key that is down to let it back up. **OFF** clears the picture: it closes whatever page is open and turns INFO off. INFO is the key the others rest on: open MENU, SCHED, TAPES or `?` with INFO on and INFO pops up, then goes back down when you close the page. **CHAT** sits beside the keys.

Press `?` (or the **?** key) for the about and keys page: Liquid LSD at the top, every key below, and in the corner a link to the Liquid LSD website (with a QR code for your phone) and to the Spaz Radio app for Android. MENU › TV › **ABOUT** opens the same page. Any key, or a click anywhere but the links, closes it. With the menu open, `?`, `I`, `S` and `T` still work (they close it first), and `C` opens or closes the chat.

Point at the picture with the mouse, or click or tap it, to show the channel, the scene, its FX chain, the track playing on the radio and the next show on Spaz Radio for a few seconds. **INFO** (or `I`) keeps them on screen, updating as they change, until you turn it off; the TV remembers it. TIME, REACT, INFO, the MENU settings and your skipped channels are remembered in the browser. The display window on the right shows the channel on air (`SPAZ RADIO • CH.042`), or LIVE during a live broadcast. Under it, while the TV is on, is how many people are on the Spaz Radio stream right now, in any player (`12 mutants watching TV.`); the line is blank when the count is zero or can't be read.

**No signal:** if the radio stream drops or goes quiet, the screen shows snow and NO SIGNAL, and the TV reconnects by itself.

**Big GPU scenes:** the most demanding 3D fractals and simulations only play on computers fast enough for them (the TV checks at power-on; MENU › Big GPU scenes overrides it). Add `?heavy=1` to the address to force them on, `?heavy=0` to keep them off.

**Display:** the display window always shows the channel. The small box before it says what the TV is listening to: `LIVE SHOW` (a desktop broadcast, or a show the station says is live, which also gets `[LIVE!]` before its title), `JUKEBOX` (no live show: Spaz Radio is playing shows from the archive at random), `TAPE` (a show you picked from TAPES) or `MIC`. Hover it for the show's name.

**Screen:** while the TV is on, your computer or phone won't dim or lock (in browsers that support it).

**Lock screen and media keys:** your phone's lock screen and your keyboard's media keys show the show and the scene; play/pause is the power switch, next/previous change the scene.

**Install as an app:** use your browser's Install / Add to Home Screen; it opens fullscreen like a TV app.

**Lite picture (older and slower devices):** when your browser can't draw the visuals, the TV shows the **Spaz Radio photo gallery** instead: the listeners' photos fading in over each other while they slowly turn and grow, as on the old radio page. Everything else works as usual (the radio, MENU, the schedule, the chat, tapes); CH ▲▼ brings the next photo, and the scene controls (FX, ROLL, HOLD, TIME, REACT) rest. Where the visuals run too slowly (under 12 frames a second even at the lowest resolution), the TV switches to lite by itself and remembers it on that device. On any device, MENU › TV › **VISUALS** switches between *Full* and *Lite* (at once, with the sound playing on; *Full* tries the visuals again); lite saves battery and data, about 4 MB of photos in all, loaded one at a time while the TV is on. The full picture needs Safari 15 (iOS 15) or a 2021 or newer Chrome, Firefox or Edge with WebGL 2.

**Offline:** after one visit with a network, the TV keeps every scene, effect and transition, and the last 60 gallery photos it showed, so it opens and changes scenes with no connection at all. The radio needs the network, so offline the screen says `OFFLINE · A FOR MICROPHONE` (on a phone, MENU › SOUND): turn the microphone on and the TV runs on the sound in the room. Live broadcasts, the chat, the schedule and tapes come back with the network (the TAPES list still opens offline, from the last copy). To play archive shows offline, download them in the Spaz Radio app for Android.

**Kiosk mode:** `index.html?kiosk` shows only the picture (no TV body), turns itself on and hides the mouse pointer; use the keyboard or swipes. Browsers that refuse to start sound on their own get a "click or press a key for sound" prompt.

**MENU:** the MENU button or `M` opens the service menu, on the page you used last. `←` `→` or `Tab` change page, or click a tab; `↓` goes into the rows, where the arrows or clicks change a setting. The mouse wheel changes a value with the cursor directly on it, and scrolls anywhere else. `Esc`, `M` or the **✕** close it. The line at the bottom explains the selected row, or the one under the cursor.

- **SCENES:** **Time**, **Auto roll**, **Follow music**, **Effects**, **Background**, **BG changes**, **Skip this channel**, **Skipped**.
- **SOUND:** **Audio input** (radio or microphone), **REACT**, **SYNC** (how late the sound reaches you, for Bluetooth and other delayed speakers), **TAP SYNC**.
- **EQ:** **Equalizer** on or off (off by default), **Bass**, **Mid** and **Treble** (±12 dB each), **Flat**. It changes only what you hear from the radio or a tape, never what the picture reacts to. Boosting a band turns the overall volume down by the same amount so loud songs don't distort.
- **TV:** **Fullscreen**, **Theme** (the cabinet and the room around it: light, a 1970s hi-fi receiver in brushed aluminium and walnut, the default; or dark plastic), **Announce scenes** (name each new scene on screen; off by default), **Reduced motion** (Auto follows your system setting), **Big GPU scenes** (Auto lets the GPU check decide), **Reset**, **About**.

Settings are remembered in this browser. REACT, TIME, Auto roll, the background, effects and follow-music rows, Audio input and Big GPU scenes are locked during a live broadcast. RESET puts every setting back, including turning the EQ off and flat, but leaves skipped channels skipped.

**Variations:** no scene looks exactly the same twice. Each one's settings roll within a narrow span around the values it was tuned with whenever it comes on, and again on **ROLL** (`R`). Settings that come in steps (a colour mode, a number of petals, Mandala's shape) change on the bar line; the rest glide over it. MENU › **Auto roll** rolls the scene on air every 4, 8 or 16 bars, which goes well with HOLD.

**Background:** the background plays behind the scene and shows wherever the scene is dark or see-through. It starts as the **Spaz Radio photo gallery**: the photos listeners added to the old radio page (spaz.org/radio), fading in on the beat and slowly turning and growing, as they did there. `B` or MENU › **Background** changes it now (to the other backgrounds, and back). MENU › **BG changes**: *Hold* (stays until you change it, the default), *With scenes* (a new one every two scenes) or *Off* (no background).

**Effects and following the music:** MENU › **Effects** *Off* plays the scenes and backgrounds without FX chains (the info panel says `FX: OFF`). MENU › **Follow music** *Off* stops the calm scene for a breakdown, the hard cut on the drop and the tempo-weighted picks.

**Channel skip:** don't like a channel? `X` skips it (or MENU › **Skip this channel** on a touch screen). It keeps its number but never comes up by itself again: not from the autopilot, `J`, CH up or down. MENU › **Skipped** lists the channels you skipped; pick one to watch it, and `X` there brings it back into the rotation. At least 8 channels always stay.

**SCHEDULE:** the SCHED key or `S` lays Spaz Radio's upcoming shows over the picture, grouped by day (Today, Tomorrow, then the date), in your own time zone (shown at the top). The show playing now is marked ON AIR. Any key or a click closes it. It works during a live broadcast too. When the track name comes up on screen, the next show comes up under it (`NEXT: <show> · <time>`). If the station's schedule can't be reached, the page says so and the TV keeps the last copy it had.

**TAPES:** the TAPES key or `T` opens the Spaz Radio archive over the picture. Type to search by name or date, use the arrows and Enter or click a show to play it, and Esc or the **✕** to close. **RANDOM TAPE** picks any show, **ON THIS DAY** finds today's date in every year, and **RESUME** goes back to the show you last stopped. The TV keeps the show list in the browser, so TAPES opens at once on later visits and checks for new shows at most once an hour.

To download shows and play them offline, use the Spaz Radio app for Android: the link at the bottom of TAPES, or on the `?` page, opens its download page (Google Play on other devices).

The picture follows the tape as it follows the radio, and the display reads `TAPE`. Point at or tap the picture for the transport strip along the bottom (INFO keeps it up): eject, 30 seconds back and forward, pause, and the show's waveform to click or drag. Keys: `K` or `Space` pause, `,` and `.` 30 seconds, `<` and `>` 5 minutes.

**LINK** on the strip copies a link to this moment of the show, such as `https://spaz.org/tv/?tape=2026-06-19-Portland_s_Most_Unwanted&t=1h2m`. Whoever opens it gets the TV with that show cued at that point; turning the TV on plays it. (Where the browser won't copy, the link goes into the address bar to copy from there.) Leave `&t=…` off to start from the top; it also takes `72m`, `90s` or `1:12:03`.

**EJECT** on the strip, or `L`, takes the tape out and goes back to the radio. The small light on the TAPES key is on while a tape is in or its list is open. If a live show starts during a tape, the tape keeps playing, the `TAPE` box pulses red, and `LIVE NOW: <name> · EJECT OR L` comes up once with the strip; a broadcast's picture does not take over until you go back. The chat stays on the live room, with a note on the board that you're on a tape.

Your place in each show is remembered in this browser. Turning the TV off and on keeps the tape in; a reload starts on the radio, with RESUME in the list. At the end of a show, the TV goes back to the radio.

**CHAT:** the Spaz Radio chat hangs beside the TV on a blackboard: the same room as the Spaz Radio Android app and spaz.org/radio. You can read the recent conversation and see who is here straight away (the list at the top; click it to see every name). To say something, write your name in chalk on the bottom line, press Enter, then write and press Enter to send. Your name is remembered in this browser; click it to change it. Each name is always the same colour of chalk, and yours is the only yellow one. Times are in your own time zone. Links open in a new tab.
The board is open on your first visit. The **X** in its top corner closes it, and the TV grows back to full size; the CHAT button or `C` brings it back. The CHAT button's light is on while the board is open; while it is closed, the light blinks when someone says something. Keys typed on the board stay on the board (Esc leaves the line). The chat runs whether the TV is on or off, works during a live broadcast, and stays out of fullscreen. On a phone, or any narrow or upright window, the board hangs under the TV: scroll down to it. If the connection drops, the board says "offline · reconnecting…" and fills in what was said meanwhile when it's back. Kiosk mode and the picture inside the Spaz Radio app (which has its own chat) leave it out.

**SYNC:** if the picture's pulses come before the beat you hear (Bluetooth speakers are often 150-250 ms late), open MENU › **TAP SYNC** and tap Space or the picture on 8 beats; or set **SYNC** by hand. It needs the beat light to be green (or a live broadcast).

**Reduced motion:** follows your system's "reduce motion" setting (or MENU › Reduced motion): gentler reaction, no hard scenes, no flashes or glitch effects, no cut on the drop, slower changes.

**Going live:** when the desktop starts broadcasting, viewers see a LIVE title card with the broadcaster's name (if one is set in the desktop's Web Broadcast preferences).

**Power:** click anywhere on the TV to turn it on (the browser needs that click to start the sound). The piano keys (OFF, INFO, MENU, SCHED, TAPES, ?) and CHAT work without turning it on, and picking a tape turns it on and plays the tape. Once it's on, only the PWR switch turns it off: clicks on the cabinet are ignored, a click that just misses a button presses the nearest one, and clicks on the picture show the info.

**The info panel:** point at or click the picture, or press **I**, to see it. Top left: what Spaz Radio is playing and the next show. Bottom left: **SCENE** (the scene on air), **BACKGROUND** (what plays behind it: the photo gallery, or with BG changes *With scenes*, a scene that changes on its own clock, twice the scene length) and **FX** (the scene's effects). With MENU › **ANNOUNCE SCENES** on, the panel comes up by itself for each new scene, and the BACKGROUND line when the background changes. Control messages (HOLD, BARS, SYNC…) come up in the middle of the picture.

**About:** the `?` page (also MENU › TV › **ABOUT**) links the Liquid LSD website, with a QR code for your phone, and **I** shows a Liquid LSD line with the info.

**Beat light:** the beat light in the display window blinks on every beat: green when the TV has locked onto the music's beat, amber while it keeps time on its own (quiet passages, or before it has found the beat).

**What viewers see.** Each deck's source and its parameters, the deck FX chains and the master FX chain, the transition and crossfader, the channel levels and the beat all go over the wire, so the web TV follows your mix closely. A few things stay desktop-only: external video sources (Spout, Syphon and PipeWire, webcams included) show as nothing, and anything the web player does not have (a custom shader you added to your own library) is skipped. Viewers join in the middle of a broadcast on the right beat.

The viewer's browser also connects to a live audio stream and runs its own audio analysis, keeping the visuals reactive to music even when you're not broadcasting.

---

## Autopilot mode

When no broadcaster is connected, the web player switches to **Autopilot** — running its own curated visual playlist against the live audio stream. It keeps the visualizer interesting and audio-reactive 24/7 without needing a human operator. It finds the beat of the radio stream itself, so beat-synced movement lands on the music, and changes scenes on the bar: every 16 bars by default (the TIME dial), with the background (the photo gallery unless you pick another) held, or changing half as often with BG changes *With scenes*. It also follows the song: when the bass drops out for a breakdown a calm or building scene comes in, and when the drop lands a hard scene cuts in on it (unless HOLD is on).

When a broadcaster connects, Autopilot smoothly hands off control to the live stream. When the broadcast ends, it picks up again automatically.

The web presets and playlists used for Autopilot live in `web/presets/` and `web/playlists/`, separate from your desktop preset library. Web presets are ordinary desktop `.lsd` files, so you can copy one across in either direction (a preset using only sources the web has). A playlist line can add an FX chain after a bar: `my-preset | my-chain`; the desktop's FX chains and transition presets ship with the web TV.
