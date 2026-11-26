// tapes.js
// The Spaz Radio archive as data, for the TV's TAPES (tapedeck.js): the show list, search, the remembered
// positions and each show's waveform. No DOM, so node can test it.
// The feed is the podcast RSS the Android app and the legacy archives page read. It is 6 MB (316 KB gzipped)
// but machine-written and regular, so a regex pass reads it faster than building a DOM of it would.
import { decodeName } from './schedule.js';

export const TAPES_FEED_URL = 'https://spaz.org/archives/archives.rss';
// The Spaz Radio Android app downloads shows to play offline; the TV only offers SAVE on the track line (ui.js). On Android, spaz.org/go/
// offers Google Play or the APK; anywhere else it would send the viewer back to the TV, so they get the Play page.
const APP_GO_URL = 'https://spaz.org/go/';
const APP_PLAY_URL = 'https://play.google.com/store/apps/details?id=llm.slop.spazradio';
export const appUrl = (userAgent = globalThis.navigator?.userAgent ?? '') => (/android/i.test(userAgent) ? APP_GO_URL : APP_PLAY_URL);
const FILE_PREFIX = 'https://spaz.org/archives/imports/';
const FEED_STALE_MS = 60 * 60 * 1000;   // opening TAPES refetches a list older than this (the feed is daily)
const RANDOM_MIN_S = 20 * 60;           // the die does not land on a stray few minutes of a show
const RESUME_KEY = 'lsd.tapes.resume';
const RESUME_KEEP = 50;
const RESUME_END_ZONE_S = 30;           // this close to either end of a show counts as not started, or finished

const ITEM = /<item>([\s\S]*?)<\/item>/g;
const TITLE = /<title>([^<]*)<\/title>/;
const LINK = /<link>([^<]*)<\/link>/;
const DURATION = /<duration>([\d:]+)<\/duration>/;
const PUBDATE = /<pubDate>([^<]*)<\/pubDate>/;

/** `02:15:53` -> 8153. Anything else -> 0. */
export function parseDuration(s) {
  if (!/^\d+(:\d+)*$/.test(String(s ?? ''))) return 0;
  return String(s).split(':').reduce((sum, v) => sum * 60 + Number(v), 0);
}

/** 8153 -> `2:15:53`, 723 -> `12:03`. */
export function formatClock(seconds) {
  const s = Math.max(0, Math.floor(Number(seconds) || 0));
  const h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60;
  const ss = String(s % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}

/** `Portland_s_Most_Unwanted - 2025-05-09` -> `Portland's Most Unwanted`. The result is text: use textContent. */
export function tidyTitle(raw) {
  return decodeName(String(raw ?? '').replace(/\s+-\s+\d{4}-\d{2}-\d{2}\s*$/, '').replace(/(\w)_s_/g, "$1's ").replace(/_/g, ' '));
}

/** The RSS text -> [{ id, url, title, date, seconds, search }], newest first as the feed has them. Plays only Ogg files in the archive. */
export function parseFeed(xml) {
  const shows = [];
  for (const [, item] of String(xml ?? '').matchAll(ITEM)) {
    const url = item.match(LINK)?.[1]?.trim() ?? '';
    if (!url.startsWith(FILE_PREFIX) || !url.endsWith('.ogg')) continue;
    const id = url.slice(FILE_PREFIX.length, -'.ogg'.length);
    // The file name carries the full date; some old items' pubDate is only a year.
    const date = id.match(/^\d{4}-\d{2}-\d{2}/)?.[0] ?? item.match(PUBDATE)?.[1]?.match(/\b(19|20)\d\d\b/)?.[0] ?? '';
    const title = tidyTitle(item.match(TITLE)?.[1]) || id;
    shows.push({ id, url, title, date, seconds: parseDuration(item.match(DURATION)?.[1]), search: `${title} ${date}`.toLowerCase() });
  }
  return shows;
}

// -------------------------------------------------------
// Share links: /tv/?tape=<id>&t=1h12m3s opens the TV with that show cued at that moment. The id is the archive
// file's name without .ogg, as in the feed, so a link outlives the list it was made from.
// -------------------------------------------------------
const LINK_MIN_S = 5;   // a link to the first few seconds is a link to the show

/** `1h12m3s`, `72m`, `90s`, `4323`, `1:12:03` -> seconds. Anything else -> 0. */
export function parseLinkTime(s) {
  const v = String(s ?? '').trim().toLowerCase();
  if (/^\d+(:\d+)*$/.test(v)) return parseDuration(v);
  const m = v.match(/^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$/);
  if (!v || !m) return 0;
  return Number(m[1] ?? 0) * 3600 + Number(m[2] ?? 0) * 60 + Number(m[3] ?? 0);
}

/** 4323 -> `1h12m3s`, 90 -> `1m30s`, 0 -> `0s`. */
export function formatLinkTime(seconds) {
  const s = Math.max(0, Math.floor(Number(seconds) || 0));
  const h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, ss = s % 60;
  return `${h ? `${h}h` : ''}${m ? `${m}m` : ''}${ss || (!h && !m) ? `${ss}s` : ''}`;
}

/** `?tape=<id>&t=…` -> { id, at } (at in seconds), or null when there is no usable tape id. */
export function parseTapeLink(search) {
  const params = new URLSearchParams(search ?? '');
  const id = params.get('tape')?.trim();
  // The id becomes part of a URL under the archive. Every archive name is letters, digits, _ and -, so nothing else
  // gets in: nothing that could leave that folder or end the path.
  if (!id || id.length > 200 || !/^[\w.-]+$/.test(id) || id.includes('..')) return null;
  return { id, at: parseLinkTime(params.get('t')) };
}

/** The show a link names: from the list when it is there, else made from the id alone (no length, a rough title). */
export function showForLink(id, shows = []) {
  const known = shows.find((s) => s.id === id);
  if (known) return known;
  const title = tidyTitle(id.replace(/^\d{4}-\d{2}-\d{2}[-_ ]*/, '')) || id;
  const date = id.match(/^\d{4}-\d{2}-\d{2}/)?.[0] ?? '';
  return { id, url: `${FILE_PREFIX}${id}.ogg`, title, date, seconds: 0, search: `${title} ${date}`.toLowerCase() };
}

/** The share link for show `id` at `seconds`, on the page at `href` (its other options dropped). */
export function tapeLinkUrl(href, id, seconds = 0) {
  const url = new URL(href);
  url.search = '';
  url.hash = '';
  url.searchParams.set('tape', id);
  if (seconds >= LINK_MIN_S) url.searchParams.set('t', formatLinkTime(seconds));
  return url.href;
}

/** Shows matching every word of `query` in the title or date. An empty query keeps them all. */
export function searchShows(shows, query) {
  const words = String(query ?? '').toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return shows;
  return shows.filter((s) => words.every((w) => s.search.includes(w)));
}

/** A random show long enough to settle into (any show, if none is). */
export function randomShow(shows, rand = Math.random) {
  const long = shows.filter((s) => s.seconds >= RANDOM_MIN_S);
  const list = long.length ? long : shows;
  return list.length ? list[Math.floor(rand() * list.length)] : null;
}

/** The search for shows from today's date in any year, like `-11-03`. */
export function onThisDayQuery(now, timeZone) {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone, month: '2-digit', day: '2-digit' }).formatToParts(now);
  const get = (type) => parts.find((p) => p.type === type).value;
  return `-${get('month')}-${get('day')}`;
}

/** Where the viewer stopped in each show: newest first, in localStorage, and a private window just forgets. */
export function createResumeStore(storage) {
  const read = () => {
    try {
      const list = JSON.parse(storage?.getItem(RESUME_KEY) ?? '[]');
      return Array.isArray(list) ? list.filter((e) => typeof e?.id === 'string' && Number.isFinite(e.t)) : [];
    } catch { return []; }
  };
  const write = (list) => { try { storage?.setItem(RESUME_KEY, JSON.stringify(list.slice(0, RESUME_KEEP))); } catch { /* not remembered */ } };
  return {
    get: (id) => read().find((e) => e.id === id)?.t ?? 0,
    latest: () => read()[0] ?? null,
    clear: (id) => write(read().filter((e) => e.id !== id)),
    /** Remembers `t` seconds into a show `seconds` long, or forgets it near either end. */
    set(id, t, seconds) {
      const rest = read().filter((e) => e.id !== id);
      const started = t >= RESUME_END_ZONE_S && (!(seconds > 0) || t <= seconds - RESUME_END_ZONE_S);
      write(started ? [{ id, t: Math.round(t) }, ...rest] : rest);
    },
  };
}

/** The show's peaks file: the audio URL's twin, as the legacy page finds it. */
export const peaksUrl = (show) => show.url.replace('/imports/', '/peaks/').replace(/\.ogg$/, '.json');

/** Flat [min, max, min, max...] -> `columns` levels 0..1, averaged rather than peak so that loud shows keep their shape. */
export function reducePeaks(data, columns = 600) {
  if (!Array.isArray(data) || data.length < 2) return null;
  const pairs = Math.floor(data.length / 2), n = Math.min(columns, pairs);
  const out = new Float32Array(n);
  let top = 0;
  for (let c = 0; c < n; c++) {
    const from = Math.floor(c * pairs / n), to = Math.max(from + 1, Math.floor((c + 1) * pairs / n));
    let sum = 0;
    for (let i = from; i < to; i++) sum += (data[i * 2 + 1] - data[i * 2]) / 2;
    out[c] = sum / (to - from);
    if (out[c] > top) top = out[c];
  }
  if (top > 0) for (let c = 0; c < n; c++) out[c] /= top;
  return out;
}

/** The show's waveform, or null if it can't be had (the strip then draws a plain bar). */
export async function fetchPeaks(show, fetchImpl = fetch) {
  try {
    const res = await fetchImpl(peaksUrl(show));
    if (!res.ok) return null;
    return reducePeaks((await res.json())?.data);
  } catch { return null; }
}

// -------------------------------------------------------
// The show list: fetched when TAPES first opens, since most visits never will. The last good list is kept on
// the device (Cache Storage, LIST_CACHE), so TAPES opens at once on a later visit, offline too, and the feed is
// fetched again only once the kept list is older than FEED_STALE_MS. When it is, the browser asks the server
// (a 304 when the feed has not changed) and an unchanged Last-Modified skips parsing the 6 MB again.
// -------------------------------------------------------
export const LIST_CACHE = 'lsd-tv-tapes';   // sw.js keeps this cache when it clears old ones
const LIST_KEY = 'tapes-list.json';
const LIST_VERSION = 1;

export const tapesState = {
  shows: [],
  fetchedAt: 0,     // Date.now() of the last good fetch (0: never)
  modified: '',     // the feed's Last-Modified at that fetch
  failed: false,    // the last fetch failed and there is no copy
  loading: null,    // the fetch in flight
  restoring: null,  // reading the kept list, the first time TAPES opens
  restored: false,  // the kept list has been read (or there was none)
};

/** Replaces the list (a good fetch, a kept list, or the headless check's fixture). */
export function setTapes(shows, fetchedAt = Date.now(), modified = '') {
  tapesState.shows = shows;
  tapesState.fetchedAt = fetchedAt;
  tapesState.modified = modified;
  tapesState.failed = false;
}

/**
 * The archive's copy of the jukebox show's file (radio.spaz.org/playing's `download`), or null when the archive
 * list has no show by that file name. The radio plays from a batch copied into `archives/olde/`, which spaz.org
 * does not serve; most of those files are in `archives/imports/` under the same name, and the list knows which.
 * (Files the station took from `archives/imported/<uploader>/<folders>/` are not in the list, and their path is
 * lost in the copy, so they have none.) A file already in `imports/` is its own copy.
 */
export function archiveFileFor(url, shows = tapesState.shows) {
  if (!url) return null;
  if (url.startsWith(FILE_PREFIX)) return url;
  let name;
  try { name = decodeURIComponent(new URL(url).pathname.split('/').pop()); } catch { return null; }
  const id = name.replace(/\.ogg$/i, '');
  if (!id || id === name) return null;
  return showIndex(shows).get(id)?.url ?? null;
}
let indexOf = null, index = null;
/** id -> show, rebuilt when the list is replaced. */
function showIndex(shows) {
  if (indexOf !== shows) { index = new Map(shows.map((s) => [s.id, s])); indexOf = shows; }
  return index;
}

export const tapesStale = (now = Date.now()) => now - tapesState.fetchedAt > FEED_STALE_MS;

/** Shows -> the kept form: [id, title, date, seconds] each (the URL and search text follow from them). */
export const packShows = (shows) => shows.map((s) => [s.id, s.title, s.date, s.seconds]);
/** The kept form -> shows. Anything malformed is dropped. */
export function unpackShows(rows) {
  if (!Array.isArray(rows)) return [];
  return rows.filter((r) => Array.isArray(r) && typeof r[0] === 'string' && r[0]).map(([id, title, date, seconds]) => {
    const t = String(title ?? id), d = String(date ?? '');
    return { id, url: `${FILE_PREFIX}${id}.ogg`, title: t, date: d, seconds: Number(seconds) || 0, search: `${t} ${d}`.toLowerCase() };
  });
}

/** Where the list is kept: Cache Storage when the browser has it (not in a private window, not in node), else nowhere. */
export function createListStore(cachesImpl = globalThis.caches) {
  const open = () => cachesImpl.open(LIST_CACHE);
  return {
    async load() {
      try {
        const res = await (await open()).match(LIST_KEY);
        const rec = res ? await res.json() : null;
        return rec?.v === LIST_VERSION ? rec : null;
      } catch { return null; }
    },
    async save(state) {
      try {
        const body = JSON.stringify({ v: LIST_VERSION, fetchedAt: state.fetchedAt, modified: state.modified, shows: packShows(state.shows) });
        await (await open()).put(LIST_KEY, new Response(body, { headers: { 'Content-Type': 'application/json' } }));
      } catch { /* not kept: the next visit fetches the feed */ }
    },
  };
}
let defaultStore = null;
const listStore = () => (defaultStore ??= createListStore());

/** Reads the kept list once, unless a list is already in (a fetch, or the headless fixture). Returns tapesState. */
export function restoreTapes(store = listStore()) {
  if (tapesState.restored) return Promise.resolve(tapesState);
  tapesState.restoring ??= (async () => {
    const rec = await store.load();
    const shows = unpackShows(rec?.shows);
    if (shows.length && !tapesState.fetchedAt && Number.isFinite(rec.fetchedAt)) setTapes(shows, rec.fetchedAt, String(rec.modified ?? ''));
    return tapesState;
  })().finally(() => { tapesState.restoring = null; tapesState.restored = true; });
  return tapesState.restoring;
}

/** Fetches the feed (one retry after 2 s), keeping the last good list if it fails. Returns tapesState. */
const feedSignal = (ms) => (typeof AbortSignal !== 'undefined' && AbortSignal.timeout ? AbortSignal.timeout(ms) : undefined);
export function refreshTapes(fetchImpl = fetch, store = listStore()) {
  if (tapesState.loading) return tapesState.loading;
  tapesState.failed = false;
  tapesState.loading = (async () => {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const res = await fetchImpl(TAPES_FEED_URL, { cache: 'no-cache', signal: feedSignal(20000) });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const modified = res.headers?.get?.('last-modified') ?? '';
        if (modified && modified === tapesState.modified && tapesState.shows.length) {
          setTapes(tapesState.shows, Date.now(), modified);   // unchanged: no need to parse it again
        } else {
          const shows = parseFeed(await res.text());
          if (!shows.length) throw new Error('empty feed');
          setTapes(shows, Date.now(), modified);
        }
        await store.save(tapesState);
        return tapesState;
      } catch (err) {
        if (attempt === 0) await new Promise((r) => setTimeout(r, 2000));
        else { console.warn('[lsd] archive list unavailable:', err.message); tapesState.failed = tapesState.shows.length === 0; }
      }
    }
    return tapesState;
  })().finally(() => { tapesState.loading = null; });
  return tapesState.loading;
}
