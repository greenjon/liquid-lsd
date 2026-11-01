# Web TV: Spaz Radio chat on a blackboard

Status: IMPLEMENTED 2026-10-31 (uncommitted, unpublished): unit tests + ui_check green, screenshots at 1280x720 and 412x900 look right. Deviations: no "your name:" label while naming (the placeholder says it; the label truncated), the name label shows once named. Open: the live-room check with the user (app-checklist), Caveat vs Caveat Brush on real screens. Protocol facts are in
`.planning/web-tv-chat-notes.md`. Read that first.

## Goal
An optional chalkboard hangs on the wall beside the TV, holding the Spaz Radio chat. It's the same room as the
Android app and spaz.org/radio, with the same broker, history and online list. It's written in chalk in a
handwriting font. The TV keeps its green phosphor look. Chat stays out of fullscreen entirely.

## Decisions (user, 2026-10-31)
1. **Connect on page load**, whatever the TV power or board state (not under `?embed`/`?kiosk`).
2. **The board starts open**, with a prominent **X** in its upper-right corner so it's obvious it's optional.
   Closing it is remembered per browser (`lsd.chat.open`). The CHAT button, the C key and MENU › CHAT bring it
   back.
3. **Nothing in fullscreen**: no ticker and no options. Fullscreen is the picture only.
4. **Caveat** (OFL) for the board, with name, message and time styled apart (see Look).

## Behaviour
- **Unread**: while the board is closed, the CHAT button LED glows when a new live message arrives. It goes out
  when the board opens.
- **Lurk, then join**: messages and the online list show at once. The bottom line of the board says "write your
  name in chalk". After that the name is saved (`lsd.chat.nick`) and you rejoin automatically next visit, like
  Android. Click your name on the board to change it ("" then the new name on the same clientId, as the legacy
  client does).
- **Presence**: online = connected and named, as on spaz.org/radio, whether the TV is on or off and the board
  open or closed. `pagehide` publishes an empty presence before DISCONNECT. The last will covers crashes.
- **Live broadcast**: chat is allowed. It changes nothing the broadcaster controls.
- **Off entirely** under `?embed` (the Android app already has chat) and `?kiosk`. It doesn't even connect.
- **Reduced motion**: no chalk-writing reveal, no LED pulse.

## Look
### Board
- **Surface**: schoolhouse green `#21352E` (rather than the charcoal option, so it reads as a blackboard at a
  glance and stands apart from the charcoal TV cabinet). It gets texture from an inline SVG `feTurbulence`
  noise data URI at about 4% alpha, plus two or three large, faint eraser smears (low-alpha radial gradients).
  Not the suggested 20 px dot grid: a regular grid reads as graph paper, not chalk.
- **Frame and tray**: a wooden frame with warm brown gradients and rounded corners, and a chalk tray along the
  bottom with one CSS chalk stick and an eraser. It's as tall as the TV cabinet next to it.
- **The X**: a hand-drawn chalk ✕ in the upper-right corner, about 32 px with a 44 px hit area, in chalk white.
  It brightens on hover and focus. `aria-label="Close chat"`, tooltip "Close the chat (C brings it back)".

### Type
One family, three roles. Caveat is a variable font (wght 400–700): vendor its latin + latin-ext woff2 subsets
as `web/fonts/caveat-*.woff2` with `font-display: swap` and the fallbacks `"Comic Neue", "Comic Sans MS",
cursive`. No letter-spacing anywhere, because it breaks Caveat's joins. Caveat Brush is held in reserve if 700
still looks thin on the real board (a judgement made in the app, not up front).

| Role | Font | Size | Colour | Notes |
| --- | --- | --- | --- | --- |
| Message | Caveat 700 | 24 px (`clamp(22px, 1.9vw, 26px)`) | chalk `#EEEBDD` (10.9:1) | line-height 1.4; chalk-dust shadow `0 0 1px` 60% + `0 0 4px` 20% |
| Name | Caveat 700 | 26 px, on its own line above the message | one of 5 pastels hashed from the name: pink `#F2A7B8`, blue `#9FCBEA`, green `#B5E3A1`, orange `#F5B67A`, lilac `#C9B6F0` (6.8–9.0:1) | your own name is always yellow `#F4D06F` (8.7:1), and no one else gets yellow |
| Time | Caveat 500 | 17 px, after the name on the same line | faded chalk `#9EA79F` (5.3:1) | no blur shadow (blur hurts small text); short local time, with a date only on days other than today |
| Header ("Spaz Radio chat") | Caveat 700 | 30 px | yellow `#F4D06F` | with an underline stroke |
| Online list | Caveat 500 | 19 px | chalk at 80% | "4 here: abu, gj, ray dio, …"; click to expand |
| Input and status | Caveat 700 | 24 px | chalk | a chalk underline, no box; status ("offline · reconnecting…", "sending…") in the time style |

All contrast ratios are against `#21352E` and pass WCAG AA; message, name and header pass AAA. The name sits
above the message rather than inline: in a 300–400 px column, an inline name plus a 24 px script leaves too
little room for the message on the first line. Messages are 0.5 em apart.

- **New message**: a 250 ms left-to-right `clip-path` reveal, as if it were being written. Skipped under reduced
  motion and for history.
- **Links**: http(s) and `www.` only, with a chalk underline, `target=_blank rel="noopener noreferrer"`.

## Layout
- `#tv-wrapper` becomes a row: `#tv-body` and then `<aside id="chalkboard">`, with a gap, stretched to the same
  height.
- When the board is open (`body.chat-open`): `--board-w: clamp(280px, 26vw, 400px)` and
  `--tv-screen-w: min(calc(100vw - var(--board-w) - 140px), 128vh)`. At 1280x720 that gives roughly an 800 px
  screen plus a 330 px board. Caveat is narrow, so that's about 35 characters per line at 24 px. Check that it
  fits in 720 px of height with the controls row.
- **Narrow or portrait** (`max-width: 1000px` or `orientation: portrait`): the board drops below the TV at full
  width, `height: min(60vh, 520px)`. The body gets `overflow-y: auto` only while the board is open (it's
  `hidden` now), so the soft keyboard can push the input into view.
- Fullscreen is `#tv-screen-area` alone, so the board is simply not in it.

## Code
1. **`web/mqtt.js` (new)**: a minimal MQTT 3.1.1 client over WebSocket (subprotocol `mqtt`), written from the
   spec. Our own code, no vendored Paho, so no clean-room question.
   - Pure, exported for tests: `encodeConnect({clientId, keepAlive, will})`, `encodeSubscribe`, `encodePublish`,
     `encodePuback`, `encodePing`, `encodeDisconnect`, and `Parser` (handles partial and coalesced frames, plus
     the variable-length remaining-length field).
   - `createClient({url, clientId, keepAlive: 30, will, WebSocketImpl = WebSocket})` returns
     `{connect, close({graceful}), subscribe, publish(topic, payload, {qos: 0|1, retain}) -> Promise, on(event)}`.
     Events: `state` (connecting/online/offline), `message(topic, payload, retained)`.
   - Subscriptions at QoS 1, so there's no QoS 2 handshake to implement. Incoming QoS 1 gets a PUBACK. Chat
     publishes at QoS 1, and the promise resolves on PUBACK (that's the "sending…" state). Presence publishes at
     QoS 1, retained.
   - PINGREQ every keepalive. No PINGRESP within 10 s, or a socket close, means reconnect with backoff
     1→2→4…30 s plus jitter. It reconnects at once on the `online` event.
2. **`web/chat.js` (new, no DOM)**: protocol and model.
   - Constants: `BROKER = 'wss://radio.spaz.org:1885/mqtt'`, `HISTORY = 'https://radio.spaz.org/djdash/chatlog'`,
     `CHAN = 'spazradio'`.
   - `newClientId()`: `mut` + 6 base36 characters from `crypto.getRandomValues`, once per page load, never
     stored.
   - `cleanText(s)`: strips tags and decodes entities (named entities plus numeric `&#..;`/`&#x..;`), matching
     the legacy `.html().text()`. It's pure so the tests can run it in node. The result only ever goes in
     through `textContent`.
   - `linkify(text)` returns `[{text}|{url, text}]` tokens.
   - `mergeHistory(current, history)`: history first, then live messages that aren't in history, deduped by
     user + message within ±10 s. Capped at 300.
   - `roster(map)`: distinct names, sorted case-insensitively.
   - `createChat({mqtt, fetch, now, storage})` (a state machine that can be tested with fakes): on connect it
     subscribes to `spazradio` + `presence/#`, fetches history and merges it (again after every reconnect),
     then publishes presence if named. A 10 s self-heal re-publishes presence if our name is missing from the
     roster. `join(nick)`, `rename(nick)`, `send(text)` (trimmed, max 500 chars, at most 1 per second, rejected
     while offline), `leave()` (empty presence, then a graceful DISCONNECT). Live messages are stamped
     `time_received ?? now()`, and history ms are kept as ms.
3. **`web/chatboard.js` (new, DOM)**: the board, its X, and the CHAT button LED. It renders from
   `chat.js` events. Message list `role="log" aria-live="polite"`. It keeps the view pinned to the bottom unless
   the viewer has scrolled up (as Android and chatster do), with a "↓ new" chalk arrow when they have.
4. **`web/ui.js`**: wires the CHAT button, key `C`, and the MENU row `CHAT` (OPEN/CLOSED; help text: "The Spaz
   Radio chat, on a blackboard beside the TV. Same room as the Spaz Radio app."). It starts the chat connection at page
   load, adds `viewerState.chat` for the headless check, skips it all
   under embed/kiosk, and bumps `TV_VERSION` to v5. The existing keydown guard already ignores keys typed in
   inputs. Esc inside the chat input blurs it (it doesn't leave fullscreen or close anything).
5. **`web/index.html`**: the `#chalkboard` aside (open by default) with its X, and the CHAT control-group after
   SCHEDULE (tooltip "Spaz Radio chat, on a blackboard (C)").
6. **`web/tv.css`**: the board, frame, tray, X, the type table above, and the layout rules. Hidden under `body.kiosk`/`body.embed`. Check that the controls row still fits at 1280 and
   wraps under 700 px with one more button.
7. **`web/sw.js`**: add `mqtt.js`, `chat.js`, `chatboard.js` and the Caveat woff2 files to SHELL. The broker and chatlog are
   other origins, so they're never cached. Offline means the board says "offline".

## Tests / checks
- `web/tools/mqtt.test.mjs`: packet bytes against hand-checked vectors (CONNECT with will, SUBSCRIBE,
  PUBLISH QoS 0/1, PUBACK), remaining-length edges (127/128/16383/16384), the parser with split and
  coalesced frames, and the client with a fake WebSocket (PINGREQ timing, PINGRESP timeout → reconnect, PUBACK
  resolves publish).
- `web/tools/chat.test.mjs`: `cleanText` (entities, tags, `&lt;script&gt;` stays text), `linkify`,
  `mergeHistory` dedupe and order, roster dedupe (one name under several clientIds counts once), and the state
  machine with a fake mqtt + fetch: presence after a named connect, none while lurking, rename = "" then the
  name, leave = empty presence before DISCONNECT, history re-fetched after a reconnect, self-heal, send limits.
- `web/checks/ui_check.mjs`: a fake broker through an injectable socket factory, so the check never touches
  radio.spaz.org. Checks: the board is open on a first visit, the X closes it and the closed state survives a
  reload, C/button/MENU bring it back, layout open/closed at 1280x720 (screen and board fit, no overlap), the
  LED lights on unread, it connects at load with the TV off, and embed/kiosk never create a socket.
- By hand, with the user: post one real message from the web TV, watch it on the phone (and the phone's on the
  board), change the name, close the tab, and confirm the name leaves the online list on the phone. Then
  desktop at 1280x720 and a phone in portrait with the soft keyboard up. Judge Caveat 700 against Caveat Brush on
  the real board.

## Docs (repo "done" rule)
- `docs/user_guide/web_broadcast.md`: CHAT paragraph (the board, the X, joining, the online list), C in the keys, and the MENU row list.
- `docs/developer/web_subsystem.md`: mqtt.js/chat.js/chatboard.js, the topics and payloads, presence rules, the
  history merge, and "never post to the live room from tests".
- Font licence: `web/fonts/OFL.txt` (Caveat), plus a line in the developer doc (and in `clean_room_provenance.md` if it
  lists third-party assets).
- `RELEASE_NOTES.md` + `docs/release_notes.md`; regenerate the `src/main/resources/docs` HTML.
- `.planning/app-checklist.md`: in-app checks (merge, don't append).
- Publish with `scripts/publish_web_tv.sh` only when the user says so.

## Not in scope
- Moderation, emoji pickers, DMs, sound on new messages, anything in fullscreen.
- Spaz Radio Android changes. Two possible Android follow-ups from the exploration, for a separate decision:
  history isn't re-fetched after a reconnect, and live messages show no time (confirmed on the phone 2026-10-31).
