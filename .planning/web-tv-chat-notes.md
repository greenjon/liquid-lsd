# Web TV: Spaz Radio chat (exploration notes)

Status: EXPLORED 2026-10-31. No design decision, no code yet.

Sources read: spazradio `data/ChatRepository.kt`, `ChatViewModel.kt`, `ui/components/ChatContent.kt` and their git
history; the legacy web client at spaz.org/radio (`/js/spazchat.js`, `/js/chatster.js`, which is the same file the
DJ dashboard at radio.spaz.org/djdash uses). The live broker was checked with a read-only probe that subscribes
and never publishes. The djdash server source (kenrestivo/djdash) is not public.

## Wire protocol (verified 2026-10-31)
- **Transport**: MQTT 3.1.1 over WebSocket, `wss://radio.spaz.org:1885/mqtt`, subprotocol `mqtt`. No auth.
  The broker has no Origin check (it accepted `https://evil.example`). TLS 1.2 and 1.3 both work. Android forces
  TLS 1.2 because of handshake failures on some devices, which browsers don't have. The port is 1885: an early
  Android try at 443 was reverted. The legacy web client used `ws://…:1884` on http pages.
- **Chat topic `spazradio`**: payload is JSON `{"user": "...", "message": "..."}`, QoS 2, not retained. The
  sender doesn't set a time.
- **Presence topic `presence/<clientId>`**: payload is the nickname, retained. An empty payload means gone (an
  empty retained publish also deletes the retained copy). Android also treats `"offline"` as gone. The roster is
  the distinct, sorted set of the payload values.
- **Last will**: an empty retained message on `presence/<clientId>`. A clean DISCONNECT suppresses the will, so a
  graceful leave has to publish the empty presence first.
- **clientId**: `mut` + 4 base36 characters (web) or `mut` + an int up to 1e6 (Android). It's random for each
  tab or process. A clash makes the broker kick the older client, and with auto-reconnect the two clients would
  keep kicking each other. Use `crypto.getRandomValues` and never persist the id or share it across tabs.
- **History**: `GET https://radio.spaz.org/djdash/chatlog` returns `{status:"OK", history:[{user, message,
  time_received}]}`, the last 100 messages, with `time_received` in **ms** stamped by the server. It's plain JSON
  with `Access-Control-Allow-Origin: *`. The legacy client asks for it as JSONP, but the server returns JSON.

## Subtle behaviour to copy (or to fix)
1. **Live messages carry no time.** Verified 2026-10-31 with one test post (user "gj (web tv test)", 10:10 PM
   PDT): the broker relayed exactly `{"user","message"}`, and the chatlog had stamped `time_received` on its own
   copy. chatster falls back to `new Date()`. Android gets 0 for a live message and hides its time: user CONFIRMED on the phone 2026-10-31 (the test post arrived
   with no time). Times seen on Android came from history loaded at launch. Web: stamp live messages
   with the local receive time, and use `time_received` if it ever shows up.
2. **Text is HTML-ish.** Senders send raw text. The legacy web decodes entities with `$('<p>').html(m).text()`
   and then runs Autolinker into `innerHTML`, which is an XSS hole. Android runs `HtmlCompat.fromHtml`, so tags
   render there. Web: decode entities, insert with `textContent`, and linkify by building `<a rel="noopener
   noreferrer" target="_blank">` nodes ourselves. Treat nicknames the same way.
3. **One name, several clientIds is normal.** It's one person on several devices (the user is often on the app and
   the website at once as "gj"). On 2026-10-31 `spukkin` had 3 clientIds (2 Android, 1 web). Dedupe by name, as
   both clients do. Ghost entries are still possible in theory but haven't been seen. Publish an empty retained presence on `pagehide`, and keep the will as a backup. The legacy
   `beforeunload` sends `send_presence(0)`, which Paho JS rejects because the payload isn't a string, so only the
   will clears it in practice.
4. **Lurking before login.** Both clients connect and show messages and the roster before a nickname is set.
   Presence is published only once there is a name. Android saves the name in prefs and auto-joins next time.
5. **Self-heal presence.** The legacy client re-checks every 10 s: if it's connected and its name isn't in the
   roster, it publishes again. Android publishes presence on every `connectComplete` (reconnects included). Do
   both.
6. **Background = offline but still connected.** Android publishes `""` on `onStop` and the name again on
   `onStart`, and stays subscribed. Web analogue: `visibilitychange`. Open question: a hidden tab that is still
   playing the radio arguably counts as "listening".
7. **History/live ordering.** The legacy client fetches history after connecting and then subscribes (a gap or
   duplicate window). It clears the log on disconnect and fetches again on reconnect. Android fetches once at
   startup and **never again after a reconnect**, so messages sent during a drop are lost (an Android bug). Web:
   subscribe first, buffer, fetch history, merge and dedupe on (user, message, time ±5 s). Fetch again after
   every reconnect.
8. **Nick change**: publish `""` and then the new name on the same clientId (legacy). Android's "reset username"
   disconnects instead.
9. **QoS/keepalive**: chat QoS 2 on both clients. Presence is QoS 2 on web and QoS 1 on Android. Keepalive is
   30 s on web and 60 s on Android. Subscribing at QoS 0/1 is fine and means a minimal client never has to do
   the QoS 2 PUBREC/PUBREL/PUBCOMP handshake.
10. **No moderation**: anyone can post as any name, and there's no rate limit server-side.

## Web TV constraints found
- `?embed` (the Spaz Radio Android app's WebView) and `?kiosk`: chat must be off. Android already has native
  chat, and a second presence entry would show up.
- Fullscreen puts `#tv-screen-area` in fullscreen, so a side panel outside it disappears there. An overlay
  survives fullscreen.
- Single-key shortcuts (N/P/F/G…) and "any key closes" overlays mean a chat input has to stop key propagation.
- In-screen layers already present: OSD, MENU, ABOUT, SCHEDULE, title card, ident, bumper.
- MQTT client: none is bundled. Either vendor Paho JS (`mqttws31.js`, EPL/EDL) or write a small 3.1.1 client
  (CONNECT+will, SUBSCRIBE, PUBLISH QoS 0/1, PINGREQ, reconnect with backoff), about 150 lines. A read-only
  version is in the session probe and worked first try.
