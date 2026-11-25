// node --test web/tools/tapes.test.mjs : the archive feed, search, remembered positions and waveforms.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { parseFeed, parseDuration, formatClock, tidyTitle, searchShows, randomShow, onThisDayQuery, createResumeStore, reducePeaks, peaksUrl, fetchPeaks, refreshTapes, restoreTapes, createListStore, packShows, unpackShows, tapesState, setTapes, archiveFileFor, LIST_CACHE, parseLinkTime, formatLinkTime, parseTapeLink, showForLink, tapeLinkUrl } from '../tapes.js';

const item = (title, file, duration, pubDate) => `<item><title>${title}</title><link>https://spaz.org/archives/imports/${file}</link><comments>http://spaz.org/radio</comments><pubDate>${pubDate}</pubDate><duration>${duration}</duration><image><url>https://spaz.org/x.png</url><title>nested image title</title><link>http://spaz.org/radio</link></image></item>`;
const FEED = `<?xml version="1.0"?><rss><channel><title>SPAZ Radio Archives</title><link>http://spaz.org/radio</link>
${item('Live_from_Moss_Oak_Commons_in_Arcata - 2026-11-02', '2026-11-02-Live_from_Moss_Oak.ogg', '02:15:53', 'Mon, 02 Nov 2026 00:00:00 +0000')}
${item('Portland_s_Most_Unwanted - 2025-05-09', '2025-05-09-Portland.ogg', '01:00:00', 'Fri, 09 May 2025 00:00:00 +0000')}
${item('Spukkin - 2014-09-09', '2014-09-09-all.ogg.vctemp', '00:14:19', 'Tue, 09 Sep 2014 00:00:00 +0000')}
${item('Megman - Beat Meet &amp; Greet', '2014-08-25-all.ogg', '04:20:57', '2014')}
${item('old undated', 'nodate.ogg', '10:00', '2014')}
</channel></rss>`;

test('the feed gives clean shows, Ogg files in the archive only', () => {
  const shows = parseFeed(FEED);
  assert.deepEqual(shows.map((s) => s.title), ['Live from Moss Oak Commons in Arcata', "Portland's Most Unwanted", 'Megman - Beat Meet & Greet', 'old undated']);
  assert.equal(shows[0].id, '2026-11-02-Live_from_Moss_Oak');
  assert.equal(shows[0].url, 'https://spaz.org/archives/imports/2026-11-02-Live_from_Moss_Oak.ogg');
  assert.equal(shows[0].seconds, 8153);
  assert.equal(shows[2].date, '2014-08-25', 'the file name has the date a bare-year pubDate lacks');
  assert.equal(shows[3].date, '2014');
  assert.deepEqual(parseFeed(null), []);
  assert.deepEqual(parseFeed('<rss></rss>'), []);
});

test('a link outside the archive is not playable', () => {
  assert.deepEqual(parseFeed('<item><title>x</title><link>https://evil.example/archives/imports/a.ogg</link></item>'), []);
});

test('titles and clocks', () => {
  assert.equal(tidyTitle('STYLUS_FLyte - 2023-09-05'), 'STYLUS FLyte');
  assert.equal(tidyTitle('...Stones&amp;boneS... - 2020-06-13'), '...Stones&boneS...');
  assert.equal(parseDuration('02:15:53'), 8153);
  assert.equal(parseDuration('10:00'), 600);
  assert.equal(parseDuration('soon'), 0);
  assert.equal(parseDuration(undefined), 0);
  assert.equal(formatClock(8153), '2:15:53');
  assert.equal(formatClock(723), '12:03');
  assert.equal(formatClock(-5), '0:00');
});

test('search needs every word, in the title or the date', () => {
  const shows = parseFeed(FEED);
  assert.equal(searchShows(shows, '').length, 4);
  assert.deepEqual(searchShows(shows, 'moss ARCATA').map((s) => s.date), ['2026-11-02']);
  assert.deepEqual(searchShows(shows, '2014 megman').map((s) => s.date), ['2014-08-25']);
  assert.deepEqual(searchShows(shows, 'moss megman'), []);
  assert.deepEqual(searchShows(shows, '-05-09').map((s) => s.title), ["Portland's Most Unwanted"]);
});

test('on this day searches the date in any year, in the viewer\'s zone', () => {
  assert.equal(onThisDayQuery(Date.parse('2026-11-03T12:00:00Z'), 'UTC'), '-11-03');
  assert.equal(onThisDayQuery(Date.parse('2026-11-03T02:00:00Z'), 'America/Los_Angeles'), '-11-02');
});

test('the die skips stray short recordings unless that is all there is', () => {
  const shows = [{ id: 'short', seconds: 60 }, { id: 'long', seconds: 3600 }];
  for (const r of [0, 0.5, 0.99]) assert.equal(randomShow(shows, () => r).id, 'long');
  assert.equal(randomShow([{ id: 'short', seconds: 60 }]).id, 'short');
  assert.equal(randomShow([]), null);
});

const fakeStorage = () => { const m = new Map(); return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) }; };

test('positions are remembered newest first, and forgotten near either end', () => {
  const r = createResumeStore(fakeStorage());
  assert.equal(r.get('a'), 0);
  assert.equal(r.latest(), null);
  r.set('a', 100, 1000);
  r.set('b', 200, 1000);
  assert.equal(r.get('a'), 100);
  assert.equal(r.latest().id, 'b');
  r.set('a', 150, 1000);
  assert.equal(r.latest().id, 'a');
  r.set('a', 5, 1000);
  assert.equal(r.get('a'), 0, 'barely started');
  r.set('b', 990, 1000);
  assert.equal(r.get('b'), 0, 'finished');
  r.set('c', 300, 0);
  assert.equal(r.get('c'), 300, 'a show of unknown length is kept');
  r.clear('c');
  assert.equal(r.get('c'), 0);
});

test('the store keeps its limit, and survives no storage and bad data', () => {
  const s = fakeStorage();
  const r = createResumeStore(s);
  for (let i = 0; i < 60; i++) r.set(`s${i}`, 100, 1000);
  assert.equal(r.latest().id, 's59');
  assert.equal(r.get('s0'), 0);
  assert.equal(r.get('s10'), 100);
  s.setItem('lsd.tapes.resume', '{not json');
  assert.equal(r.latest(), null);
  const none = createResumeStore(null);
  none.set('a', 100, 1000);
  assert.equal(none.get('a'), 0);
  const refusing = createResumeStore({ getItem() { throw new Error('denied'); }, setItem() { throw new Error('denied'); } });
  refusing.set('a', 100, 1000);
  assert.equal(refusing.get('a'), 0);
});

test('peaks reduce to levels 0..1 that keep the show\'s shape', () => {
  const quiet = [-0.1, 0.1], loud = [-0.8, 0.8];
  const data = [...Array(100).fill(quiet).flat(), ...Array(100).fill(loud).flat()];
  const levels = reducePeaks(data, 4);
  assert.equal(levels.length, 4);
  assert.ok(Math.abs(levels[0] - 0.125) < 1e-6, levels[0]);
  assert.equal(levels[3], 1);
  assert.equal(reducePeaks(null), null);
  assert.equal(reducePeaks([]), null);
  assert.equal(reducePeaks([-0.5, 0.5], 600).length, 1, 'never more columns than data');
  assert.equal(peaksUrl({ url: 'https://spaz.org/archives/imports/a b.ogg' }), 'https://spaz.org/archives/peaks/a b.json');
});

test('a missing peaks file just means a plain bar', async () => {
  const show = { url: 'https://spaz.org/archives/imports/a.ogg' };
  assert.equal(await fetchPeaks(show, async () => ({ ok: false })), null);
  assert.equal(await fetchPeaks(show, async () => { throw new Error('offline'); }), null);
  assert.equal(await fetchPeaks(show, async () => ({ ok: true, json: async () => ({ nope: 1 }) })), null);
  assert.equal((await fetchPeaks(show, async () => ({ ok: true, json: async () => ({ data: [-1, 1, -1, 1] }) }))).length, 2);
});

test('a failed feed keeps the last good list, and a good one replaces it', async () => {
  setTapes([{ id: 'kept' }]);
  await refreshTapes(async () => { throw new Error('offline'); });
  assert.equal(tapesState.shows[0].id, 'kept');
  assert.equal(tapesState.failed, false);
  await refreshTapes(async () => ({ ok: true, text: async () => FEED }));
  assert.equal(tapesState.shows.length, 4);
  tapesState.shows = [];
  await refreshTapes(async () => ({ ok: true, text: async () => '<rss></rss>' }));
  assert.equal(tapesState.failed, true, 'an empty feed with nothing to fall back on is a failure');
});

test('the service worker leaves the archive alone', () => {
  const sw = fs.readFileSync(new URL('../sw.js', import.meta.url), 'utf8');
  const skip = new RegExp(sw.match(/if \(\/(.*)\/\.test\(url\.pathname\)\) return;/)[1]);
  assert.ok(skip.test('/archives/imports/2026-11-02-show.ogg'));
  assert.ok(skip.test('/archives/archives.rss'));
  assert.ok(skip.test('/archives/peaks/2026-11-02-show.json'));
  assert.ok(!skip.test('/tv/ui.js'));
});

// A Cache Storage stand-in: one cache, keyed by the request string.
const fakeCaches = () => {
  const caches = new Map();
  return {
    names: () => [...caches.keys()],
    async open(name) {
      if (!caches.has(name)) caches.set(name, new Map());
      const c = caches.get(name);
      return { match: async (k) => c.get(k)?.clone(), put: async (k, res) => { c.set(k, res); } };
    },
  };
};
const resetTapes = () => Object.assign(tapesState, { shows: [], fetchedAt: 0, modified: '', failed: false, loading: null, restoring: null, restored: false });
const feedRes = (modified, body = FEED) => {
  let parsed = 0;
  const res = { ok: true, headers: { get: (h) => (h === 'last-modified' ? modified : null) }, text: async () => { parsed++; return body; } };
  return { res, parsed: () => parsed };
};

test('the kept form round-trips a show', () => {
  const shows = parseFeed(FEED);
  assert.deepEqual(unpackShows(packShows(shows)), shows);
  assert.deepEqual(unpackShows(null), []);
  assert.deepEqual(unpackShows([['', 'x'], 'junk', ['ok']]).map((s) => s.id), ['ok']);
});

test('a fetched list is kept, and the next visit opens with it without fetching', async () => {
  const caches = fakeCaches();
  const store = createListStore(caches);
  resetTapes();
  await restoreTapes(store);
  assert.equal(tapesState.shows.length, 0, 'nothing kept yet');
  await refreshTapes(async () => feedRes('Sat, 14 Nov 2026 17:30:44 GMT').res, store);
  assert.deepEqual(caches.names(), [LIST_CACHE]);
  const fetchedAt = tapesState.fetchedAt;

  resetTapes();   // a new visit
  await restoreTapes(store);
  assert.equal(tapesState.shows.length, 4);
  assert.equal(tapesState.shows[0].url, 'https://spaz.org/archives/imports/2026-11-02-Live_from_Moss_Oak.ogg');
  assert.equal(tapesState.fetchedAt, fetchedAt, 'the kept list keeps its age, so an old one is still refreshed');
  assert.equal(tapesState.modified, 'Sat, 14 Nov 2026 17:30:44 GMT');
});

test('an unchanged feed is not parsed again; a changed one is', async () => {
  const store = createListStore(fakeCaches());
  resetTapes();
  await refreshTapes(async () => feedRes('A').res, store);
  const same = feedRes('A');
  await refreshTapes(async () => same.res, store);
  assert.equal(same.parsed(), 0);
  assert.equal(tapesState.shows.length, 4);
  const changed = feedRes('B', FEED.replace(/<item>[\s\S]*?<\/item>/, ''));
  await refreshTapes(async () => changed.res, store);
  assert.equal(changed.parsed(), 1);
  assert.equal(tapesState.shows.length, 3);
});

test('a kept list does not replace one already in, and no Cache Storage just keeps nothing', async () => {
  const store = createListStore(fakeCaches());
  resetTapes();
  await refreshTapes(async () => feedRes('A').res, store);
  resetTapes();
  setTapes([{ id: 'fixture' }]);
  await restoreTapes(store);
  assert.equal(tapesState.shows[0].id, 'fixture');
  resetTapes();
  const none = createListStore(undefined);
  await refreshTapes(async () => feedRes('A').res, none);
  assert.equal(tapesState.shows.length, 4);
  resetTapes();
  await restoreTapes(none);
  assert.equal(tapesState.shows.length, 0);
});

test('the service worker keeps the show list when it clears old caches', () => {
  const sw = fs.readFileSync(new URL('../sw.js', import.meta.url), 'utf8');
  assert.ok(sw.includes(`'${LIST_CACHE}'`) && /k !== TAPES_LIST/.test(sw));
});

test('the app link: spaz.org/go/ on Android, the Play page elsewhere', async () => {
  const { appUrl } = await import('../tapes.js');
  assert.equal(appUrl('Mozilla/5.0 (Linux; Android 14; Pixel 7)'), 'https://spaz.org/go/');
  assert.equal(appUrl('Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0'), 'https://play.google.com/store/apps/details?id=llm.slop.spazradio');
});

test('share links: times both ways', () => {
  assert.equal(parseLinkTime('1h12m3s'), 4323);
  assert.equal(parseLinkTime('72m'), 4320);
  assert.equal(parseLinkTime('90s'), 90);
  assert.equal(parseLinkTime('1:12:03'), 4323);
  assert.equal(parseLinkTime('4323'), 4323);
  for (const bad of ['', 'x', '1h-2m', null, '12q']) assert.equal(parseLinkTime(bad), 0, String(bad));
  assert.equal(formatLinkTime(4323), '1h12m3s');
  assert.equal(formatLinkTime(90), '1m30s');
  assert.equal(formatLinkTime(3600), '1h');
  assert.equal(formatLinkTime(0), '0s');
  for (const s of [0, 59, 61, 3599, 3601, 8153]) assert.equal(parseLinkTime(formatLinkTime(s)), s);
});

test('share links: only an archive name is a tape id', () => {
  assert.deepEqual(parseTapeLink('?tape=2026-11-02-Live_from_Moss_Oak&t=1h2m'), { id: '2026-11-02-Live_from_Moss_Oak', at: 3720 });
  assert.deepEqual(parseTapeLink('?tape=2025-05-09-Portland'), { id: '2025-05-09-Portland', at: 0 });
  for (const bad of ['', '?t=1h', '?tape=', '?tape=../x', '?tape=a/b', '?tape=a%2Fb', '?tape=a%3Fb', '?tape=a%20b', '?tape=' + 'x'.repeat(201)]) assert.equal(parseTapeLink(bad), null, bad);
});

test('share links: the show from the list, or from its id alone', () => {
  const shows = parseFeed(FEED);
  assert.equal(showForLink('2025-05-09-Portland', shows), shows[1]);
  const made = showForLink('2026-11-02-Live_from_Moss_Oak', []);
  assert.equal(made.url, 'https://spaz.org/archives/imports/2026-11-02-Live_from_Moss_Oak.ogg');
  assert.equal(made.title, 'Live from Moss Oak');
  assert.equal(made.date, '2026-11-02');
  assert.equal(made.seconds, 0);
});

test('share links: the link drops the page options and a time near the start', () => {
  assert.equal(tapeLinkUrl('https://spaz.org/tv/?nolive#x', '2025-05-09-Portland', 4323.6), 'https://spaz.org/tv/?tape=2025-05-09-Portland&t=1h12m3s');
  assert.equal(tapeLinkUrl('https://spaz.org/tv/', '2025-05-09-Portland', 3), 'https://spaz.org/tv/?tape=2025-05-09-Portland');
  const back = parseTapeLink(new URL(tapeLinkUrl('https://spaz.org/tv/', '2025-05-09-Portland', 600)).search);
  assert.deepEqual(back, { id: '2025-05-09-Portland', at: 600 });
});

test('archiveFileFor: the jukebox file found in the archive list by name', () => {
  const shows = parseFeed(FEED);
  const imp = 'https://spaz.org/archives/imports/2025-05-09-Portland.ogg';
  assert.equal(archiveFileFor('https://spaz.org/archives/olde/2025-05-09-Portland.ogg', shows), imp, 'olde/ -> imports/');
  assert.equal(archiveFileFor('https://spaz.org/archives/imports/whatever.ogg', shows), 'https://spaz.org/archives/imports/whatever.ogg', 'imports/ is its own copy');
  assert.equal(archiveFileFor('https://spaz.org/archives/olde/not-in-the-list.ogg', shows), null, 'not in the archive: no SAVE');
  assert.equal(archiveFileFor('https://spaz.org/archives/olde/2025-05-09-Portland.mp3', shows), null, 'only Ogg names');
  assert.equal(archiveFileFor('https://spaz.org/archives/olde/2025-05-09-Portland.ogg', []), null, 'no list yet');
  assert.equal(archiveFileFor('', shows), null);
});
