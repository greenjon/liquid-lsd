# Web TV: SCHEDULE button

Status: IMPLEMENTED 2026-10-31 (uncommitted at writing): tests + headless UI check pass; unseen on spaz.org (not published).

## Goal
A SCHEDULE push button on the TV cabinet, next to MENU. Pressing it lays Spaz Radio's upcoming shows over the
picture as a green phosphor "program guide" page, the same way MENU › ABOUT does.

## Data source (verified 2026-10-31)
- `GET https://radio.spaz.org/djdash/droid`, the endpoint the Spaz Radio Android app uses
  (`spazradio/app/src/main/java/llm/slop/spazradio/ScheduleViewModel.kt`, polled hourly, cached on disk).
- Response: JSON array of `{ start_timestamp, end_timestamp, name, url }`, with timestamps in UTC milliseconds.
  `name` is HTML-escaped (`Spukkin Faceship&#039;s ...`), so it has to be decoded. `url` is empty right now.
- CORS: `Access-Control-Allow-Origin: *`, so spaz.org/tv can fetch it directly. About 1.2 KB, around 10 shows.
- `sw.js` ignores other origins, so the response is never cached there. Offline means the last copy in memory or
  "SCHEDULE UNAVAILABLE".

## Decisions (user, 2026-10-31)
1. ABOUT-style read-only page, not a MENU-style list. It scrolls if it's long. SCHEDULE again, `G`, Esc, any
   key or a click closes it.
2. Times are in the viewer's local time (like the Android app), with the zone abbreviation in the header
   (`TIMES IN PDT`), formatted with `Intl.DateTimeFormat` in the browser's locale.
3. It shows every show that hasn't ended, grouped by day. The show on air now is marked **ON AIR**.
4. Extras: keyboard shortcut **G**; a **SCHEDULE** row in MENU (next to ABOUT, closes the menu and opens the
   page); and **Next show in the OSD**: when the track line flashes, also flash `NEXT: <show> · <time>`.

## Implementation
1. **`web/schedule.js` (new, pure + fetch)**
   - `fetchSchedule()`: no-store fetch, one retry after 2 s, keeps the last good list in memory. It refreshes
     at power-on, then hourly while on, and again when the page opens if the list is older than 10 min.
   - `upcoming(list, now)`: drops shows that have ended, sorts by start, and flags `onAir`
     (start <= now < end).
   - `groupByDay(list, locale, tz)`: TODAY / TOMORROW / `SAT NOV 1` headers, with `startTime–endTime` and the
     name on each row.
   - `decodeName(s)`: decodes HTML entities through a detached `<textarea>` (or DOMParser), and the result
     always goes in through `textContent`, never `innerHTML`.
   - `nextShow(list, now)`: the first show that hasn't started yet, for the OSD line.
2. **`web/index.html`**: `#schedule` overlay next to `#about` (head "SPAZ RADIO · SCHEDULE", a `TIMES IN …`
   line, a list body, and the hint "ANY KEY OR CLICK TO CLOSE"); a `#scheduleBtn` push button + `SCHEDULE`
   label after MENU, with the tooltip "Spaz Radio's upcoming shows (G)".
3. **`web/tv.css`**: `#schedule` uses the same shell as `#about` (z-index 5, dark scrim, VT323 green, scroll).
   Day headers are lavender like `.ab-name`. The ON AIR row is inverted green like `.menu-row.sel`. The OSD
   hides while it's open (`:has(#schedule:not([hidden])) #osd`). It is hidden under `body.embed` and kiosk.
   Check that the controls row still fits at 1280 wide and wraps cleanly under 700 px with one more button.
4. **`web/ui.js`**
   - Button click / `G` toggle it, only while powered on; MENU closes if it's open.
   - The keydown handler closes the schedule on any key first (same pattern as `about`), and a click on the
     page closes it.
   - Power-off hides it. The cabinet click handler skips `#schedule` (the existing `closest(...)` list).
   - The bumper doesn't fire while the schedule is open.
   - MENU row `SCHEDULE` (help text: "Spaz Radio's upcoming shows, in your local time.") goes before ABOUT.
   - OSD: in `pollNowPlaying`, when a new track flashes, also flash `NEXT: <name> · <start>` on a second line
     (or right after it). If no next show is known, nothing is added. Live broadcasts don't change this.
   - `viewerState.schedule` hook for the headless check. Bump `TV_VERSION` to v4.
5. **Allowed while live?** Yes. It's read-only and changes nothing the broadcaster controls.

## Tests / checks
- `web/tools/schedule.test.mjs`: upcoming/ended filtering, the ON AIR flag, day grouping across midnight and a
  DST change (fixed `timeZone`), entity decoding, nextShow, and an empty or bad payload.
- `web/checks/ui_check.mjs`: button opens and closes it, G toggles it, MENU › SCHEDULE opens it, any key
  closes it, power-off hides it. The fetch is stubbed with a fixture.
- By hand: real endpoint at spaz.org/tv on desktop + a phone, at 1280x720 and in portrait.

## Docs (repo "done" rule)
- `docs/user_guide/web_broadcast.md`: SCHEDULE paragraph, G in the keys, the MENU row list, and the OSD NEXT line.
- `docs/developer/web_subsystem.md`: schedule.js, endpoint, CORS, refresh cadence.
- `RELEASE_NOTES.md` + `docs/release_notes.md`; regenerate `src/main/resources/docs` HTML.
- `.planning/app-checklist.md`: in-app checks (merge, don't append).
- Publish with `scripts/publish_web_tv.sh` only when the user says so.

## Not in scope
- Spaz Radio Android embed: `?embed` hides every overlay. The app already has its own Schedule panel.
- Reminders/notifications, links to show pages (`url` is empty today).
