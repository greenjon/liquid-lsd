// node --test web/tools/*.test.mjs : beat flywheel and audio band signals (no browser needed)
import test from 'node:test';
import assert from 'node:assert/strict';
import { BeatFlywheel } from '../beatclock.js';
import { bandSignals, AudioAgc } from '../dsp.js';

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

test('auto gain levels quiet and loud streams to the same peak, like the desktop AudioAgc', () => {
  const feed = (level, seconds) => {
    const agc = new AudioAgc();
    let div;
    for (let i = 0; i < seconds * 100; i++) div = agc.update({ amp: level, bass: level, mid: level, high: 0 }, 0.01);
    return div;
  };
  const quiet = feed(0.05, 1), loud = feed(0.5, 1);
  assert.ok(Math.abs(0.05 / quiet.bass - 0.5 / loud.bass) < 1e-6);
  assert.ok(Math.abs(0.05 / quiet.bass - 1 / AudioAgc.HEADROOM) < 1e-6);
  assert.equal(quiet.high, AudioAgc.FLOOR); // silence is not boosted past the floor
  // Flux is stretched by the band's ratio, so a quiet stream's kicks read as loud as a loud one's.
  const q = bandSignals({ amp: 0.05, bass: 0.05, mid: 0, high: 0 }, { bass: 0.045, mid: 0, high: 0 }, quiet);
  const l = bandSignals({ amp: 0.5, bass: 0.5, mid: 0, high: 0 }, { bass: 0.45, mid: 0, high: 0 }, loud);
  assert.ok(Math.abs(q.audio_flux_bass - l.audio_flux_bass) < 1e-6);
  assert.equal(q.audio_bass, 1); // 1 / 0.85, clamped
});

test('auto gain releases over seconds after a loud passage', () => {
  const agc = new AudioAgc();
  for (let i = 0; i < 100; i++) agc.update({ amp: 0.5, bass: 0.5, mid: 0.5, high: 0.5 }, 0.01);
  const loud = agc.update({ amp: 0.5, bass: 0.5, mid: 0.5, high: 0.5 }, 0.01).bass;
  let div;
  for (let i = 0; i < 4000; i++) div = agc.update({ amp: 0.05, bass: 0.05, mid: 0.05, high: 0.05 }, 0.01);
  assert.ok(div.bass < loud / 3);
});
