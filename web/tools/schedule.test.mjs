// node --test web/tools/schedule.test.mjs : the SCHEDULE page's feed parsing, grouping and NEXT line.
import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeName, parseSchedule, upcoming, nextShow, dayLabel, timeLabel, zoneLabel, groupByDay, nextLine, refreshSchedule, scheduleState } from '../schedule.js';

const LA = { locale: 'en-US', timeZone: 'America/Los_Angeles' };
const at = (iso) => Date.parse(iso);
const H = 3600e3;

test('names lose their HTML escapes and stray spaces', () => {
  assert.equal(decodeName('Spukkin Faceship&#039;s Fukkin Spaceship'), "Spukkin Faceship's Fukkin Spaceship");
  assert.equal(decodeName('Drum &amp; Bass &#x2014; &quot;live&quot;'), 'Drum & Bass — "live"');
  assert.equal(decodeName('  <b>bold</b>  name '), '<b>bold</b> name'); // tags stay text: shown with textContent
  assert.equal(decodeName('&bogus; &#0;'), '&bogus; &#0;');
  assert.equal(decodeName(null), '');
});

test('a bad or odd feed gives a clean, sorted list', () => {
  assert.deepEqual(parseSchedule(null), []);
  assert.deepEqual(parseSchedule({ error: 'x' }), []);
  const shows = parseSchedule([
    { start_timestamp: 3000, end_timestamp: 4000, name: 'B', url: '' },
    { start_timestamp: 1000, end_timestamp: 2000, name: '', url: '' },
    { start_timestamp: 5000, end_timestamp: 5000, name: 'zero length' },
    { start_timestamp: 'soon', end_timestamp: 9000, name: 'bad' },
    null,
  ]);
  assert.deepEqual(shows.map((s) => s.name), ['SPAZ RADIO', 'B']);
});

test('ended shows drop out; the one playing now is ON AIR; next is the first not started', () => {
  const shows = parseSchedule([
    { start_timestamp: 0, end_timestamp: 100, name: 'over' },
    { start_timestamp: 100, end_timestamp: 300, name: 'now' },
    { start_timestamp: 300, end_timestamp: 400, name: 'next' },
  ]);
  const up = upcoming(shows, 200);
  assert.deepEqual(up.map((s) => [s.name, s.onAir]), [['now', true], ['next', false]]);
  assert.equal(nextShow(shows, 200).name, 'next');
  assert.equal(nextShow(shows, 350), null);
  assert.equal(upcoming(shows, 100)[0].onAir, true); // starts exactly now
});

test('days are TODAY, TOMORROW, then the date, in the viewer zone', () => {
  const now = at('2026-11-01T06:00:00Z'); // Oct 31, 11 PM in Los Angeles
  assert.equal(dayLabel(now + 0.5 * H, now, LA), 'TODAY');
  assert.equal(dayLabel(now + 1.5 * H, now, LA), 'TOMORROW'); // after midnight local
  assert.equal(dayLabel(now + 30 * H, now, LA), 'MON, NOV 2');
  assert.equal(timeLabel(at('2026-11-01T03:30:00Z'), LA), '8:30 PM');
  assert.equal(timeLabel(at('2026-11-01T03:30:00Z'), { locale: 'de-DE', timeZone: 'Europe/Berlin' }), '04:30');
});

test('TOMORROW is right across a DST change (25 h and 23 h days)', () => {
  // Nov 1 2026 is 25 h long in Los Angeles (PDT -> PST at 2 AM).
  const justAfterMidnight = at('2026-11-01T07:30:00Z'); // 00:30 PDT, Nov 1
  assert.equal(dayLabel(at('2026-11-02T07:30:00Z'), justAfterMidnight, LA), 'TODAY'); // 23:30 PST, still Nov 1
  assert.equal(dayLabel(at('2026-11-02T08:30:00Z'), justAfterMidnight, LA), 'TOMORROW'); // 00:30 PST Nov 2
  // Mar 8 2026 is 23 h long.
  const lateMar7 = at('2026-03-08T07:30:00Z'); // 23:30 PST, Mar 7
  assert.equal(dayLabel(at('2026-03-09T06:30:00Z'), lateMar7, LA), 'TOMORROW'); // 23:30 PDT, Mar 8
  assert.equal(dayLabel(at('2026-03-09T07:30:00Z'), lateMar7, LA), 'MON, MAR 9');
});

test('the zone label names the zone in effect', () => {
  assert.equal(zoneLabel(at('2026-07-01T00:00:00Z'), LA), 'PDT');
  assert.equal(zoneLabel(at('2026-12-01T00:00:00Z'), LA), 'PST');
});

test('shows group under the day they start, with times and ON AIR', () => {
  const now = at('2026-11-01T04:00:00Z'); // Oct 31, 9 PM PDT (Nov 1 falls back to PST at 2 AM)
  const shows = parseSchedule([
    { start_timestamp: at('2026-11-01T03:00:00Z'), end_timestamp: at('2026-11-01T05:00:00Z'), name: 'Blind Spot Radio' },
    { start_timestamp: at('2026-11-01T18:00:00Z'), end_timestamp: at('2026-11-01T20:00:00Z'), name: 'Telepathic Bubblebath' },
    { start_timestamp: at('2026-11-01T22:00:00Z'), end_timestamp: at('2026-11-01T23:00:00Z'), name: 'Late' },
  ]);
  const days = groupByDay(shows, now, LA);
  assert.deepEqual(days.map((d) => d.day), ['TODAY', 'TOMORROW']);
  assert.equal(days[0].shows[0].onAir, true);
  assert.equal(days[0].shows[0].time, '8:00 PM – 10:00 PM');
  assert.equal(days[1].shows.length, 2);
  assert.equal(nextLine(shows, now, LA), 'NEXT: Telepathic Bubblebath · TOMORROW 10:00 AM');
  assert.equal(nextLine(shows, at('2026-11-01T19:00:00Z'), LA), 'NEXT: Late · 2:00 PM');
  assert.equal(nextLine([], now, LA), '');
});

test('a failed fetch keeps the last good list', async () => {
  const good = async () => ({ ok: true, json: async () => [{ start_timestamp: 1, end_timestamp: 2, name: 'A' }] });
  await refreshSchedule(good);
  assert.equal(scheduleState.shows.length, 1);
  assert.equal(scheduleState.failed, false);
  let calls = 0;
  const down = async () => { calls++; throw new Error('offline'); };
  const warn = console.warn; console.warn = () => {};
  try { await refreshSchedule(down); } finally { console.warn = warn; }
  assert.equal(calls, 2); // one retry
  assert.equal(scheduleState.shows.length, 1);
  assert.equal(scheduleState.failed, false);
});
