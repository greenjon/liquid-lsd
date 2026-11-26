// schedule.js
// Spaz Radio's upcoming shows, for the SCHEDULE page (index.html #schedule) and the OSD's NEXT line.
// The feed is the one the Spaz Radio Android app reads: a JSON array of
// { start_timestamp, end_timestamp, name, url } with UTC milliseconds and HTML-escaped names. It allows any
// origin (CORS *), so the TV fetches it directly; sw.js never caches other origins.
// The pure helpers take an optional `timeZone` so the tests can pin one; the TV uses the viewer's own.

export const SCHEDULE_URL = 'https://radio.spaz.org/djdash/droid';
export const SCHEDULE_REFRESH_MS = 60 * 60 * 1000; // hourly while the TV is on (like the app)
export const SCHEDULE_STALE_MS = 10 * 60 * 1000;   // opening the page refetches a list older than this

// Array.at / findLast need Safari 15.4; the TV supports Safari 15.0 (tools/compat.test.mjs).
const last = (a) => a[a.length - 1];
const NAMED = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' };

/** `Spukkin Faceship&#039;s` -> `Spukkin Faceship's`. The result is text: put it in with textContent. */
export function decodeName(s) {
  return String(s ?? '')
    .replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, e) => {
      if (e[0] === '#') {
        const code = e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
        return Number.isFinite(code) && code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : m;
      }
      return NAMED[e.toLowerCase()] ?? m;
    })
    .replace(/\s+/g, ' ')
    .trim();
}

/** The raw feed -> [{ start, end, name }], well-formed entries only, sorted by start. */
export function parseSchedule(raw) {
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((s) => s && Number.isFinite(s.start_timestamp) && Number.isFinite(s.end_timestamp) && s.end_timestamp > s.start_timestamp)
    .map((s) => ({ start: s.start_timestamp, end: s.end_timestamp, name: decodeName(s.name) || 'SPAZ RADIO' }))
    .sort((a, b) => a.start - b.start);
}

/** Shows that have not ended at `now`, each with `onAir` (it is playing now). */
export function upcoming(shows, now) {
  return shows.filter((s) => s.end > now).map((s) => ({ ...s, onAir: s.start <= now }));
}

/** The first show that has not started yet at `now`, or null. */
export function nextShow(shows, now) {
  return shows.find((s) => s.start > now) ?? null;
}

const dayKey = (t, timeZone) => new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(t);

/** TODAY, TOMORROW, or `SAT, NOV 1` (in the locale's own order), for the day `t` falls on in `timeZone`. */
export function dayLabel(t, now, { locale, timeZone } = {}) {
  const k = dayKey(t, timeZone), today = dayKey(now, timeZone);
  if (k === today) return 'TODAY';
  // Tomorrow: the first day key after today, stepping an hour at a time (days are 23-25 h around DST).
  let tomorrow = today;
  for (let h = 1; h <= 26 && tomorrow === today; h++) tomorrow = dayKey(now + h * 3600e3, timeZone);
  if (k === tomorrow) return 'TOMORROW';
  return new Intl.DateTimeFormat(locale, { timeZone, weekday: 'short', month: 'short', day: 'numeric' }).format(t).toUpperCase();
}

/** `8:00 PM` / `20:00`, in the locale's own style. */
export function timeLabel(t, { locale, timeZone } = {}) {
  return new Intl.DateTimeFormat(locale, { timeZone, timeStyle: 'short' }).format(t).toUpperCase();
}

/** The short name of the zone the times are in at `now`: `PDT`, `CET`, or `GMT+2` where there is none. */
export function zoneLabel(now, { locale, timeZone } = {}) {
  try {
    const part = new Intl.DateTimeFormat(locale, { timeZone, timeZoneName: 'short' }).formatToParts(now).find((p) => p.type === 'timeZoneName');
    return part ? part.value.toUpperCase() : '';
  } catch { return ''; }
}

/** Upcoming shows grouped by the day they start: [{ day, shows: [{ time, name, onAir }] }]. */
export function groupByDay(shows, now, opts = {}) {
  const days = [];
  for (const s of upcoming(shows, now)) {
    const day = dayLabel(s.start, now, opts);
    if (last(days)?.day !== day) days.push({ day, shows: [] });
    last(days).shows.push({ time: `${timeLabel(s.start, opts)} – ${timeLabel(s.end, opts)}`, name: s.name, onAir: s.onAir });
  }
  return days;
}

/** The OSD line for the next show: `NEXT: TELEPATHIC BUBBLEBATH · TOMORROW 8:00 PM`, or '' if none. */
export function nextLine(shows, now, opts = {}) {
  const s = nextShow(shows, now);
  if (!s) return '';
  const day = dayLabel(s.start, now, opts);
  return `NEXT: ${s.name} · ${day === 'TODAY' ? '' : `${day} `}${timeLabel(s.start, opts)}`;
}

/**
 * Reads radio.spaz.org/playing the way spaz.org/radio does: its JSONP copy, a script that calls
 * update_meta({...}). A script needs no CORS header, and /playing sends none, so fetch() can't read it.
 * The file is static and always calls update_meta, whatever ?callback= says. Resolves with the raw object.
 */
// One request at a time: update_meta is a single global, so a second script loaded while the first is out (a quick
// power off/on) would take the first one's answer and leave it to time out. A call while one is out shares it.
let playingInFlight = null;
export function loadPlayingJsonp(url = 'https://radio.spaz.org/streams/playing-jsonp', timeoutMs = 8000) {
  if (!playingInFlight) {
    playingInFlight = requestPlayingJsonp(url, timeoutMs);
    playingInFlight.then(() => { playingInFlight = null; }, () => { playingInFlight = null; });
  }
  return playingInFlight;
}
function requestPlayingJsonp(url, timeoutMs) {
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    let settled = false;
    const done = (fn, v) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      s.remove();
      globalThis.update_meta = () => {}; // a late copy finds a no-op, not a ReferenceError
      fn(v);
    };
    globalThis.update_meta = (m) => done(resolve, m);
    s.onload = () => done(reject, new Error('playing-jsonp did not call update_meta'));
    s.onerror = () => done(reject, new Error('playing-jsonp failed to load'));
    const timer = setTimeout(() => done(reject, new Error('playing-jsonp timed out')), timeoutMs);
    s.src = `${url}${url.includes('?') ? '&' : '?'}callback=update_meta&t=${Date.now()}`;
    document.head.appendChild(s);
  });
}

/** radio.spaz.org/playing -> { title, live, download }. The server decides what is live, and puts `[LIVE!] ` before
 * the title. `download` is the show's file on spaz.org as the server names it: often
 * `archives/olde/<id>.ogg`, the radio's own copy, which spaz.org does not serve; `archiveFileFor` in tapes.js
 * finds the archive's copy), or ''
 * while the show is live: a live show has no file yet. */
export function parsePlaying(raw) {
  const live = raw?.live === true || raw?.live === 'true';
  const title = decodeName(raw?.playing);
  return {
    title: live && title && !title.startsWith('[LIVE!]') ? `[LIVE!] ${title}` : title,
    live,
    download: live || !raw?.download ? '' : archiveFileUrl(raw.download),
  };
}
/** A path or URL from the server as an http(s) URL on spaz.org's terms, or '' for anything else. */
function archiveFileUrl(path) {
  try {
    const url = new URL(String(path), 'https://spaz.org/');
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : '';
  } catch { return ''; }
}

// -------------------------------------------------------
// The live list: fetched at power-on, hourly while on, and when the page opens on a stale list.
// -------------------------------------------------------
export const scheduleState = {
  shows: [],        // parsed, sorted; the last good copy survives a failed fetch
  fetchedAt: 0,     // Date.now() of the last good fetch (0: never)
  failed: false,    // the last fetch failed and there is no copy
  loading: null,    // the fetch in flight
};

/** An abort signal that fires after `ms` (none where the browser lacks AbortSignal.timeout): a hung request must not hold its poller. */
export const timeoutSignal = (ms) => (typeof AbortSignal !== 'undefined' && AbortSignal.timeout ? AbortSignal.timeout(ms) : undefined);

/** Fetches the feed (one retry after 2 s). Keeps the last good list if it fails. Returns scheduleState. */
export function refreshSchedule(fetchImpl = fetch) {
  if (scheduleState.loading) return scheduleState.loading;
  scheduleState.loading = (async () => {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const res = await fetchImpl(SCHEDULE_URL, { cache: 'no-store', signal: timeoutSignal(10000) });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        setShows(parseSchedule(await res.json()));
        return scheduleState;
      } catch (err) {
        if (attempt === 0) await new Promise((r) => setTimeout(r, 2000));
        else { console.warn('[lsd] schedule unavailable:', err.message); scheduleState.failed = scheduleState.shows.length === 0; }
      }
    }
    return scheduleState;
  })().finally(() => { scheduleState.loading = null; });
  return scheduleState.loading;
}

/** Replaces the list (a good fetch, or the headless check's fixture). */
export function setShows(shows) {
  scheduleState.shows = shows;
  scheduleState.fetchedAt = Date.now();
  scheduleState.failed = false;
}
