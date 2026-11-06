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

test('bands decide the tempo while the phase envelope places the beat', () => {
  // The tempo bands kick at 128; the phase envelope carries the same kicks plus heavy hats between them.
  const { out, kicks } = track({ seconds: 12, bpm: 128, phase: 0.13, hats: 0.5, seed: 31 });
  const kickBins = new Set(kicks.map((k) => Math.round(k / HOP)));
  const r0 = rng(32);
  const t = new BeatTracker();
  let r = null;
  for (const { t: at, v } of out) {
    const i = Math.round(at / HOP);
    const kick = kickBins.has(i) ? 1 : 0;
    t.push(v, at, [kick + 0.02 * r0(), 0.02 * r0(), 0.02 * r0()]);
    if (i % 25 === 0) r = t.analyse();
  }
  assert.ok(Math.abs(r.bpm - 128) < 1.5, `bpm ${r.bpm}`);
  assert.ok(offBy(r.beatTime, kicks) < 0.025, `beat off by ${offBy(r.beatTime, kicks)} s`);
});

test('accents settle a syncopated groove on the beat, not its 3:2 relative', () => {
  // A strong kick every beat, with a weaker hit 1.5 beats after each bar line: the gap correlates but does not repeat.
  const seconds = 16, bpm = 130, beat = 60 / bpm;
  const r0 = rng(41);
  const strong = new Set(), weak = new Set();
  for (let b = 0; b * beat < seconds; b++) {
    strong.add(Math.round((0.1 + b * beat) / HOP));
    if (b % 4 === 0) weak.add(Math.round((0.1 + (b + 1.5) * beat) / HOP));
  }
  const t = new BeatTracker();
  let r = null;
  for (let i = 0; i < seconds / HOP; i++) {
    const v = (strong.has(i) ? 1 : 0) + (weak.has(i) ? 0.5 : 0) + 0.03 * r0();
    t.push(v, i * HOP, [v, 0.03 * r0(), 0.03 * r0()]);
    if (i % 25 === 0) r = t.analyse();
  }
  assert.ok(Math.abs(r.bpm / bpm - 1) < 0.02, `bpm ${r.bpm}`);
});

test('the clock follows a tempo that holds, while the phase is unsure', () => {
  const clock = new BeatClock();
  clock.read(0);
  let now = 0;
  for (let i = 0; i < 19; i++) { now += 250; clock.update({ bpm: 140, beatTime: null, confidence: 0 }, now); }
  assert.equal(clock.bpm, 120, 'followed too soon');
  now += 250;
  clock.update({ bpm: 140, beatTime: null, confidence: 0 }, now);
  assert.equal(clock.locked, false);
  assert.equal(clock.bpm, 140);
  // Runs at the new tempo from there on, without moving the count: 5 s at 120, then 1 s at 140.
  assert.ok(Math.abs(clock.read(6000) - (10 + 140 / 60)) < 1e-6);
});

test('a tempo that keeps moving, as in talk, neither moves the clock nor locks it', () => {
  const clock = new BeatClock();
  clock.read(0);
  let now = 0;
  for (let i = 0; i < 40; i++) {
    now += 250;
    clock.update({ bpm: 90 + (i % 5) * 15, beatTime: now / 1000 - 0.1, confidence: 1 }, now);
    clock.read(now);
  }
  assert.equal(clock.bpm, 120);
  assert.equal(clock.locked, false);
});

test('the clock only jumps to a new phase once it has held for about 2 s', () => {
  const clock = new BeatClock();
  const bpm = 120, beat = 0.5;
  let now = 10000;
  const feed = (offsetBeats) => {
    // A steady reading whose beats sit `offsetBeats` after the clock's starting grid.
    const beatTime = Math.floor((now / 1000 - offsetBeats * beat) / beat) * beat + offsetBeats * beat;
    clock.update({ bpm, beatTime, confidence: 1 }, now);
    now += 250;
  };
  for (let i = 0; i < 24; i++) feed(0);
  const phaseOf = () => { const b = clock.fly.predict(now); return b - Math.floor(b); };
  const settled = phaseOf();
  const shift = (p) => Math.abs(((p - settled + 1.5) % 1) - 0.5);
  for (let i = 0; i < 6; i++) feed(0.4);
  assert.ok(shift(phaseOf()) < 0.02, `moved after 6 readings by ${shift(phaseOf())}`);
  for (let i = 0; i < 4; i++) feed(0.4);
  assert.ok(shift(phaseOf()) > 0.3, `still not moved: ${shift(phaseOf())}`);
});

test('noise without a beat rarely locks the clock', () => {
  let confident = 0, total = 0;
  for (let seed = 100; seed < 120; seed++) {
    const r0 = rng(seed);
    const t = new BeatTracker();
    const clock = new BeatClock();
    for (let i = 0; i < 3000; i++) {
      const v = r0() * (r0() < 0.08 ? 1 : 0.1);
      t.push(v, i * HOP, [v, r0() * (r0() < 0.08 ? 1 : 0.1), r0() * (r0() < 0.08 ? 1 : 0.1)]);
      if (i % 25 === 0) {
        clock.update(t.analyse(), i * 10);
        clock.read(i * 10);
        if (i >= 800) { total++; if (clock.locked) confident++; }
      }
    }
  }
  assert.ok(confident / total < 0.5, `locked ${((confident / total) * 100).toFixed(0)}% of the time`);
});

test('the beat clock coasts at 120 BPM with no audio', () => {
  const clock = new BeatClock();
  clock.read(1000);
  clock.update(null, 1500);
  assert.ok(Math.abs(clock.read(3000) - 4) < 1e-6);
  assert.equal(clock.locked, false);
});
