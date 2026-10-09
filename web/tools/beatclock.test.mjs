// node --test web/tools/*.test.mjs : beat flywheel and audio band signals (no browser needed)
import test from 'node:test';
import assert from 'node:assert/strict';
import { BeatFlywheel } from '../beatclock.js';
import { bandSignals } from '../dsp.js';

test('flywheel coasts at the anchored tempo', () => {
  const f = new BeatFlywheel();
  f.anchor(10, 120, 1000);
  assert.equal(f.read(1000), 10);
  assert.ok(Math.abs(f.read(2000) - 12) < 1e-9); // 1 s at 120 bpm = 2 beats
});

test('jittery anchors do not make the phase jump or run backward', () => {
  const f = new BeatFlywheel();
  f.anchor(0, 120, 0);
  let prev = 0, worst = 0;
  for (let t = 40; t <= 4000; t += 40) {
    const jitter = ((t / 40) % 5) * 12 - 24; // anchors arrive up to +-24 ms off
    f.anchor((t / 1000) * 2, 120, t + jitter);
    const out = f.read(t);
    assert.ok(out >= prev, `went backward at ${t}: ${out} < ${prev}`);
    worst = Math.max(worst, Math.abs(out - (t / 1000) * 2));
    prev = out;
  }
  assert.ok(worst < 0.1, `phase strayed ${worst} beats`);
});

test('a large disagreement snaps, a tempo change takes effect', () => {
  const f = new BeatFlywheel();
  f.anchor(0, 120, 0);
  f.read(500);
  f.anchor(40, 90, 500); // tap/seek: far from the prediction
  assert.ok(Math.abs(f.read(500) - 40) < 0.5);
  assert.ok(Math.abs(f.read(1500) - (f.read(500) + 1.5)) < 0.6);
});

test('reset makes the clock invalid until the next anchor', () => {
  const f = new BeatFlywheel();
  f.anchor(1, 120, 0);
  f.reset();
  assert.equal(f.valid, false);
});

test('bandSignals uses the desktop scales and rectified flux', () => {
  const s = bandSignals({ amp: 0.125, bass: 0.5, mid: 0.05, high: 0 }, { bass: 0.49, mid: 0.2, high: 0 });
  assert.equal(s.audio_amp, 0.5);
  assert.equal(s.audio_bass, 1); // clamped
  assert.ok(Math.abs(s.audio_flux_bass - 0.2) < 1e-9); // 0.01 / 0.05
  assert.equal(s.audio_flux_mid, 0); // falling energy is not flux
  assert.ok(Math.abs(s.audio_flux_amp - 0.2) < 1e-9); // 0.01 * 2 / 0.1
});
