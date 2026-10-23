// node --test web/tools/beattrack.test.mjs : standalone beat tracking on synthetic onset envelopes.
import test from 'node:test';
import assert from 'node:assert/strict';
import { BeatTracker, BeatClock, HOP, LOCK_CONFIDENCE } from '../beattrack.js';

// Deterministic noise so a failure reproduces.
function rng(seed) { let s = seed >>> 0; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 2 ** 32); }

/**
 * Onset envelope, one value per 10 ms: kicks at `bpm` from `phase` s (tempo may change at `changeAt`),
 * optional off-beat hats, background noise. Returns [{t, v}] and the kick times.
 */
function track({ seconds, bpm, phase = 0, hats = 0, noise = 0.05, bpm2 = bpm, changeAt = Infinity, seed = 1, dropout = 0 }) {
  const r = rng(seed);
  const kicks = [];
  for (let t = phase; t < seconds;) { kicks.push(t); t += 60 / (t < changeAt ? bpm : bpm2); }
  const out = [];
  const n = Math.round(seconds / HOP);
  const kickBins = new Set(kicks.map((k) => Math.round(k / HOP)));
  const hatBins = new Set(kicks.slice(0, -1).map((k, i) => Math.round((k + kicks[i + 1]) / 2 / HOP)));
  for (let i = 0; i < n; i++) {
    let v = noise * r();
    if (kickBins.has(i) && r() >= dropout) v += 1;
    if (hatBins.has(i)) v += hats;
    out.push({ t: i * HOP, v });
  }
  return { out, kicks };
}

/** Feeds the envelope and analyses every 250 ms; returns the last result. */
function run(tracker, out, clock) {
  let last = null;
  for (const { t, v } of out) {
    tracker.push(v, t);
    if (Math.round(t / HOP) % 25 === 0) {
      last = tracker.analyse();
      if (clock) { clock.update(last, t * 1000); }
    }
    if (clock) clock.read(t * 1000);
  }
  return last;
}

/** Distance (s) from `t` to the nearest kick. */
const offBy = (t, kicks) => Math.min(...kicks.map((k) => Math.abs(k - t)));

test('finds tempo and phase of a steady four-on-the-floor', () => {
  const { out, kicks } = track({ seconds: 10, bpm: 128, phase: 0.137 });
  const r = run(new BeatTracker(), out);
  assert.ok(Math.abs(r.bpm - 128) < 1.5, `bpm ${r.bpm}`);
  assert.ok(offBy(r.beatTime, kicks) < 0.025, `beat off by ${offBy(r.beatTime, kicks)} s`);
  assert.ok(r.confidence > 0.6, `confidence ${r.confidence}`);
});

test('off-beat hats do not pull it to double time or onto the off-beat', () => {
  const { out, kicks } = track({ seconds: 10, bpm: 124, phase: 0.31, hats: 0.5, seed: 3 });
  const r = run(new BeatTracker(), out);
  assert.ok(Math.abs(r.bpm - 124) < 1.5, `bpm ${r.bpm}`);
  assert.ok(offBy(r.beatTime, kicks) < 0.025, `beat off by ${offBy(r.beatTime, kicks)} s`);
});

test('slow and fast tempos inside 70-180 BPM', () => {
  for (const bpm of [85, 100, 140, 172]) {
    const { out } = track({ seconds: 10, bpm, phase: 0.05, seed: bpm });
    const r = run(new BeatTracker(), out);
    assert.ok(Math.abs(r.bpm - bpm) / bpm < 0.015, `${bpm}: got ${r.bpm}`);
  }
});

test('missing kicks (breaks, fills) still track', () => {
  const { out, kicks } = track({ seconds: 10, bpm: 120, phase: 0.2, dropout: 0.3, seed: 9 });
  const r = run(new BeatTracker(), out);
  assert.ok(Math.abs(r.bpm - 120) < 2, `bpm ${r.bpm}`);
  assert.ok(offBy(r.beatTime, kicks) < 0.03);
});

test('a tempo change is followed within a few seconds', () => {
  const { out } = track({ seconds: 20, bpm: 120, bpm2: 132, changeAt: 10, phase: 0.1, seed: 5 });
  const r = run(new BeatTracker(), out);
  assert.ok(Math.abs(r.bpm - 132) < 2, `bpm ${r.bpm}`);
});

test('noise without a beat is not confident', () => {
  const r0 = rng(11);
  const t = new BeatTracker();
  let r = null;
  for (let i = 0; i < 1000; i++) { t.push(r0() * (r0() < 0.08 ? 1 : 0.1), i * HOP); if (i % 25 === 0) r = t.analyse(); }
  assert.ok(r.confidence < LOCK_CONFIDENCE, `confidence ${r.confidence}`);
});

test('the beat clock locks onto the kicks and never runs backward', () => {
  const { out, kicks } = track({ seconds: 16, bpm: 126, phase: 0.29, hats: 0.3, seed: 21 });
  const clock = new BeatClock();
  const tracker = new BeatTracker();
  const kickBins = new Set(kicks.filter((k) => k > 12).map((k) => Math.round(k / HOP)));
  let prev = -Infinity, worstMs = 0;
  for (const { t, v } of out) {
    tracker.push(v, t);
    if (Math.round(t / HOP) % 25 === 0) clock.update(tracker.analyse(), t * 1000);
    const b = clock.read(t * 1000);
    assert.ok(b >= prev - 1e-9, `backward at ${t}`);
    prev = b;
    // In the last 4 s every kick lands on a whole beat of the clock.
    if (kickBins.has(Math.round(t / HOP))) worstMs = Math.max(worstMs, Math.abs(b - Math.round(b)) * 60000 / 126);
  }
  assert.ok(clock.locked, 'not locked');
  assert.ok(Math.abs(clock.bpm - 126) < 1.5, `bpm ${clock.bpm}`);
  assert.ok(worstMs < 30, `a kick was ${worstMs.toFixed(0)} ms off the beat`);
});

test('the beat clock coasts at 120 BPM with no audio', () => {
  const clock = new BeatClock();
  clock.read(1000);
  clock.update(null, 1500);
  assert.ok(Math.abs(clock.read(3000) - 4) < 1e-6);
  assert.equal(clock.locked, false);
});
