// node --test web/tools/tapes.test.mjs : the archive feed, search, remembered positions and waveforms.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { parseFeed, parseDuration, formatClock, tidyTitle, searchShows, randomShow, onThisDayQuery, createResumeStore, reducePeaks, peaksUrl, fetchPeaks, refreshTapes, tapesState, setTapes } from '../tapes.js';

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
