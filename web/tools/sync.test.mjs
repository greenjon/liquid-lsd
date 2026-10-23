// node --test web/tools/sync.test.mjs : SYNC delay line and tap calibration.
import test from 'node:test';
import assert from 'node:assert/strict';
import { DelayLine, tapOffset } from '../sync.js';

test('the delay line returns the values from delayMs ago', () => {
  const d = new DelayLine();
  for (let t = 0; t <= 500; t += 10) d.push(t, { v: t });
  assert.equal(d.read(500, 0).v, 500);
  assert.equal(d.read(500, 120).v, 380);
  assert.equal(d.read(500, 2000).v, d.items[0].values.v); // older than kept: the oldest
});

test('taps 120 ms late at 120 BPM give +120 ms, with a small spread', () => {
  const beatsAt = (t) => t / 500; // 120 BPM, beat 0 at t = 0
  const taps = [0, 1, 2, 3, 4, 5, 6, 7].map((i) => i * 500 + 120 + (i % 3) * 8 - 8);
  const { ms, spread } = tapOffset(taps, beatsAt, 120);
  assert.ok(Math.abs(ms - 120) <= 8, `ms ${ms}`);
  assert.ok(spread <= 16, `spread ${spread}`);
});

test('early taps give a negative offset; scattered taps a large spread', () => {
  const beatsAt = (t) => t / 500;
  assert.ok(tapOffset([0, 1, 2, 3].map((i) => i * 500 - 40), beatsAt, 120).ms < -30);
  const wild = [0, 1, 2, 3, 4, 5, 6, 7].map((i) => i * 500 + [0, 200, -150, 90, -60, 180, -200, 30][i]);
  assert.ok(tapOffset(wild, beatsAt, 120).spread > 150);
});

test('a long Bluetooth delay is read as late, not as early by a beat', () => {
  const beatsAt = (t) => t / 500; // 120 BPM
  const taps = [0, 1, 2, 3, 4, 5, 6, 7].map((i) => i * 500 + 280);
  assert.ok(Math.abs(tapOffset(taps, beatsAt, 120).ms - 280) < 1);
});
