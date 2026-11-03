# Web TV: TAPES (the Spaz Radio archive as a VCR)

Status: step 1 IMPLEMENTED 2026-11-03, uncommitted and unpublished (TV v19): unit tests and headless checks pass, real feed/show/peaks/seek verified in headless Firefox. Unseen by the user; iPhone Safari untested. Differences from the plan below: the feed is parsed by regex (node-testable), not DOMParser; peaks confirmed on 191 of 191 sampled shows.

## Goal
The 3,813 archive shows play on spaz.org/tv, and the picture reacts to whatever is playing. A tape is a third
audio input next to the radio and the microphone. The viewer can always tell what they are hearing and how to
get back to live.

## Decisions (user, 2026-11-03)
1. **VCR metaphor:** not a separate page or a shelf beside the TV.
2. **A live show starting during a tape:** the tape keeps playing; the LIVE button pulses.
3. **Chat during a tape:** stays on the live room, with a small "you're on a tape" marker.

## Verified facts (2026-11-03)
- Feed: `https://spaz.org/archives/archives.rss`, the one the Android app reads. 6.1 MB, 316 KB with gzip (the
  server gzips). 3,813 items: `title`, `link` (the file), `pubDate`, `duration` (`HH:MM:SS`), `dc:creator`.
  Newest first. 3,812 are `.ogg`, one is `.vctemp` (drop it). Some `pubDate`s are just `2014`.
- Audio: `/archives/imports/<name>.ogg`, Ogg Vorbis, `Accept-Ranges: bytes`, 206 responses, CORS `*`.
- Peaks: `/archives/peaks/<name>.json`, 20 Hz min/max pairs for the whole show, 1.7-3.4 MB. Present for a 2026
  show and a 2014 show. Coverage is not surveyed; a 404 falls back to a plain bar.
- `<itunes:image>` returned 404 on the newest show. Don't use it.
- Show lengths: median 2.9 h, p90 5.1 h, longest 13.5 h. Resume and seeking are core, not extras.
- The relay caches state for late joiners (docs/developer/web_subsystem.md), so reconnecting gets a fresh snapshot.

## Traps found in the code
1. **Service worker (web/sw.js):** archives share the TV's origin, so `/archives/` requests DO pass through the
   fetch handler. It would try to cache a 94 MB show and the 6 MB feed, and `cache.put` rejects 206 responses.
   Add `/archives/` to the exclusion next to `gallery|curate`, and ship it first.
2. **Watchdog (dsp.js `updateSignal`, `reconnect`):** 4 s of quiet means NO SIGNAL snow, then the live stream URL
   is reloaded. That would pull a viewer off a tape during a quiet passage.
3. **Mic off (dsp.js `setMic`):** also calls `reconnect()`, so it must restore the tape rather than the stream.
4. **Live takeover (autopilot.js `handleRelayMessage`):** `isLiveBroadcast` swaps the picture for the desktop's,
   which follows the live audio, and locks the viewer controls. It must not apply during a tape.
5. **Now playing (ui.js `pollNowPlaying`):** would overwrite the OSD track line and the media session title.
6. **Media session (ui.js):** pause is wired to the power switch. On a tape, pause should pause the tape.

## Implementation

### 1. `web/tapes.js` (new; pure helpers + fetch, like schedule.js)
- `parseFeed(xmlText)` -> `[{ id, title, show, date, url, seconds }]`, via DOMParser. `id` is the file name, which
  is stable and good for links and resume. `.ogg` only. Tidy titles: underscores to spaces.
- `searchShows(shows, query)`: every space-separated word must match the title or date.
- `randomShow(shows, rng)`, `onThisDay(shows, now)`, `formatClock(seconds)`.
- Resume store in localStorage, guarded with try/catch: `{ id: seconds }`, newest 50 only, saved every 10 s and on
  pause or eject. Don't resume within 30 s of either end of a show.
- The feed is fetched the first time TAPES opens, then kept in memory.
- Peaks: fetched when a tape loads, reduced straight away to about 600 columns, and the raw JSON discarded.

### 2. `web/dsp.js`: the source
- `setTape({ url, startAt })` / `clearTape()`, plus `tapePause`, `tapeSeek`, `tapePosition`.
- Tape mode sets `audioEl.src` to the file. The analysis graph needs no change.
- `updateSignal`: on a tape, quiet is never NO SIGNAL. Only an `error` event or a long `stalled` counts, and the
  retry reloads the file and seeks back to the saved position.
- After a seek or a tape change: `tracker.reset()`, `agc.reset()`.
- `reconnect()` and `setMic(false)` go through one `restoreInput()`, which picks the tape or the stream.
- `stopAudio`: save the position first.

### 3. `web/autopilot.js`: hold off the live picture
- `setTapeHold(on)`. While held, `handleRelayMessage` only records `stationOnAir` and the broadcaster, and fires
  `lsd-station-live`. It never sets `isLiveBroadcast`. Inserting a tape during a broadcast returns to the autopilot.
- Release: reconnect the socket so the relay's cached snapshot arrives and the broadcast takes over cleanly.
- NOTE: this file has another session's uncommitted edit (random starting channel). Stage by explicit hunks or
  wait for it to land.

### 4. `web/ui.js`, `web/index.html`, `web/tv.css`: the VCR
- **TAPES button + `T` key:** a green phosphor list over the picture, like MENU and SCHEDULE. Top rows: search box,
  RANDOM TAPE, ON THIS DAY, RESUME (if there is one). Then shows, newest first, rendered as a window around the
  selection rather than 3,813 DOM rows. ↑↓ / Enter / Esc, wheel and touch scroll.
- **Inserting:** a brief tracking-noise glitch (reuse `uNoSignal` for about 400 ms), then the OSD shows the title.
  Badge: `SPAZ RADIO • TAPE`.
- **Transport strip:** bottom of the picture, shown with the OSD (picture click, INFO), a click/drag waveform from
  peaks, with ⏸ ⏪ ⏩ ⏏ and `1:12:03 / 2:15:53`, big enough for fingers.
- **Keys on a tape:** `K` pause, `,` `.` ±30 s, `<` `>` ±5 min, `L` back to live. All are free today. Space stays
  HOLD, arrows stay channel and volume.
- **LIVE button:** next to TAPES, inert when not on a tape, lit on a tape. It **pulses** when a live show is on,
  with one `LIVE NOW: <name>` message. Sources: the relay's `stationOnAir`, and the schedule's `onAir` checked each
  minute. It stops pulsing when the show ends. Pressing it saves the position and returns to the stream.
- **OSD:** track line `▶ <title> · 1:12:03 / 2:15:53`. `pollNowPlaying` keeps polling but stores the live title
  separately. Viewer controls stay unlocked on a tape, since `live()` is false.
- **Media session:** tape title, `seekto`/`seekbackward`/`seekforward`, `setPositionState`, pause pauses the tape.
  Next/previous still change the scene.
- **Chat:** a small chalk line in chatboard.js, `YOU'RE ON A TAPE · CHAT IS LIVE`. Nothing else changes.
- **Hidden** under `?embed` (the Android app has its own archive tab) and `?kiosk`.
- A TAPES row in MENU, like SCHEDULE. `?` key page and all tooltips updated.

### 5. Defaults (the user may overrule)
- **End of tape:** `END OF TAPE`, back to the radio.
- **Power off then on, same visit:** the tape stays in and resumes.
- **Reload or new visit:** always starts on the radio; RESUME is offered in the list. Nothing surprising on load.
- **MENU INPUT with a tape in:** the mic pauses the tape; mic off resumes it.

## Tests
- `web/tools/tapes.test.mjs`: feed parsing (including the `2014` date and the `.vctemp` item), search, resume
  store limits and end zones, clock format.
- `web/tools/autopilot.test.mjs`: tape hold ignores `state_full`, records on-air, release reconnects.
- `web/checks/ui_check`: list open/search/insert, badge, strip, LIVE pulse, chat marker, hidden under `?embed`.
  Fixture feed and a short local `.ogg`; no real archive traffic from tests.
- By hand in real browsers: Firefox, Chrome, Android Chrome, **iPhone Safari** (the legacy page special-cases
  iPhone, so Ogg playback and seeking there are unproven), phone layout of the now-longer button row.

## Ship checklist
`TV_VERSION` bump, `tapes.js` in the `sw.js` SHELL with a `CACHE` bump, `RELEASE_NOTES.md` + `docs/release_notes.md`,
`docs/user_guide/web_broadcast.md`, `docs/developer/web_subsystem.md`, `ARCHITECTURE.md` file map,
`.planning/app-checklist.md`. Spaz Radio's `sync_visuals.sh` will copy `tapes.js` into the APK: harmless, because
it is hidden under `?embed`.

## Later, not in this step
2. `/tv/?tape=<id>&t=1h12m` share links; WATCH links on spaz.org/radio/archives/ (that page is outside this repo).
3. Peaks-driven scene timing: change scene just ahead of a known drop; jump to next section.
4. Rerun channel: position computed from the clock, so all viewers hear the same moment with no server.
