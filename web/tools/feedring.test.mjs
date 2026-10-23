// node --test web/tools/feedring.test.mjs : the embed-mode PCM ring buffer and resampler.
import test from 'node:test';
import assert from 'node:assert/strict';
import { FeedRing } from '../feedring.js';

const sine = (n, rate, hz, start = 0) => Float32Array.from({ length: n }, (_, i) => Math.sin((2 * Math.PI * hz * (start + i)) / rate));

test('silence until primed, then the signal comes out', () => {
  const r = new FeedRing(2, 44100);
  const out = new Float32Array(128);
  r.push(sine(100, 44100, 440));          // under the 40 ms prefill
  r.pull(out, 44100);
  assert.ok(out.every((v) => v === 0));
  r.push(sine(4000, 44100, 440, 100));
  r.pull(out, 44100);
  assert.ok(out.some((v) => Math.abs(v) > 0.1));
});

test('same rate: output equals input', () => {
  const r = new FeedRing(2, 48000);
  const src = sine(8000, 48000, 1000);
  r.push(src);
  const out = new Float32Array(512);
  r.pull(out, 48000);
  for (let i = 0; i < out.length; i++) assert.ok(Math.abs(out[i] - src[i]) < 1e-6, `sample ${i}`);
});

test('resampling 44.1 kHz to 48 kHz keeps the pitch', () => {
  const r = new FeedRing(2, 44100);
  r.push(sine(12000, 44100, 1000));
  const out = new Float32Array(4800);     // 100 ms at 48 kHz
  r.pull(out, 48000);
  let crossings = 0;
  for (let i = 1; i < out.length; i++) if (out[i - 1] < 0 && out[i] >= 0) crossings++;
  assert.ok(Math.abs(crossings - 100) <= 1, `${crossings} cycles in 100 ms`);
});

test('int16 input is scaled to -1..1', () => {
  const r = new FeedRing(2, 48000);
  r.push(new Int16Array(4000).fill(16384));
  const out = new Float32Array(64);
  r.pull(out, 48000);
  assert.ok(out.every((v) => Math.abs(v - 0.5) < 1e-4));
});

test('underrun gives silence and re-primes', () => {
  const r = new FeedRing(2, 48000);
  r.push(sine(2400, 48000, 500));
  const out = new Float32Array(4800);
  r.pull(out, 48000);
  assert.equal(out[4799], 0);
  assert.equal(r.underruns, 1);
  assert.equal(r.primed, false);
});

test('a burst is trimmed so the picture does not lag the sound', () => {
  const r = new FeedRing(2, 48000);
  r.push(sine(48000, 48000, 440));        // a full second at once
  assert.ok(r.queued <= 48000 * 0.3 + 1);
  assert.ok(r.queued >= 48000 * 0.05);
});

test('wraps around the ring without glitches', () => {
  const r = new FeedRing(1, 48000);       // 192000-sample ring, fed 10 s in 20 ms blocks
  let t = 0, worst = 0, prev = null;
  const out = new Float32Array(960);
  for (let b = 0; b < 500; b++) {
    r.push(sine(960, 48000, 100, t)); t += 960;
    r.pull(out, 48000);
    if (b < 3) { prev = out[out.length - 1]; continue; }
    for (const v of out) { if (prev !== null) worst = Math.max(worst, Math.abs(v - prev)); prev = v; }
  }
  assert.ok(worst < 0.2, `largest step ${worst}`);
});
