// tapes.js
// The Spaz Radio archive as data, for the TV's TAPES (tapedeck.js): the show list, search, the remembered
// positions and each show's waveform. No DOM, so node can test it.
// The feed is the podcast RSS the Android app and the legacy archives page read. It is 6 MB (316 KB gzipped)
// but machine-written and regular, so a regex pass reads it faster than building a DOM of it would.
import { decodeName } from './schedule.js';

export const TAPES_FEED_URL = 'https://spaz.org/archives/archives.rss';
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
// The show list: fetched when TAPES first opens, since most visits never will.
// -------------------------------------------------------
export const tapesState = {
  shows: [],
  fetchedAt: 0,     // Date.now() of the last good fetch (0: never)
  failed: false,    // the last fetch failed and there is no copy
  loading: null,    // the fetch in flight
};

/** Replaces the list (a good fetch, or the headless check's fixture). */
export function setTapes(shows) {
  tapesState.shows = shows;
  tapesState.fetchedAt = Date.now();
  tapesState.failed = false;
}

export const tapesStale = (now = Date.now()) => now - tapesState.fetchedAt > FEED_STALE_MS;

/** Fetches the feed (one retry after 2 s), keeping the last good list if it fails. Returns tapesState. */
export function refreshTapes(fetchImpl = fetch) {
  if (tapesState.loading) return tapesState.loading;
  tapesState.failed = false;
  tapesState.loading = (async () => {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const res = await fetchImpl(TAPES_FEED_URL, { cache: 'no-cache' });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const shows = parseFeed(await res.text());
        if (!shows.length) throw new Error('empty feed');
        setTapes(shows);
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
