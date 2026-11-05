// transient.test.mjs: the web TV's hit signals. Run with `node --test web/tools/transient.test.mjs`.
// transient_vectors.json comes from the desktop's TransientDetectorTest (UPDATE_WEB_VECTORS=1).

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { TransientGate, FrameTransient, hitSignals, AudioAgc } from '../dsp.js';

test('the gate matches the desktop (transient_vectors.json)', () => {
  const v = JSON.parse(fs.readFileSync(new URL('./transient_vectors.json', import.meta.url), 'utf8'));
  const gate = new TransientGate();
  v.steps.forEach(([rise, want], i) => {
    const got = gate.step(rise, v.dt);
    assert.ok(Math.abs(got - want) <= 1e-5 * Math.max(1, Math.abs(want)), `step ${i}: ${got} vs desktop ${want}`);
  });
});

const SR = 44100;
const BEAT = 60 / 128;
const WINDOW = 512; // the analysers' block

function kick(t) {
  const at = t % BEAT;
  return 0.6 * Math.sin(2 * Math.PI * (50 * at + 40 * (1 - Math.exp(-at * 30)) / 30)) * Math.exp(-at * 12);
}

/** The newest analyser window's RMS at time `t`, the only thing a frame can read. */
function level(signal, t) {
  const end = Math.floor(t * SR);
  let sum = 0;
  for (let i = end - WINDOW + 1; i <= end; i++) {
    const x = signal(i / SR);
    sum += x * x;
  }
  return Math.sqrt(sum / WINDOW);
}

/** The mean of each beat's peak, after the gate has settled, reading once per frame at `fps`. */
function meanKickPeak(fps, signal) {
  const tr = new FrameTransient();
  const peaks = new Map();
  const dt = 1 / fps;
  for (let t = dt; t < 20; t += dt) {
    const v = tr.update(level(signal, t), dt);
    if (t > 5) {
      const beat = Math.floor(t / BEAT);
      peaks.set(beat, Math.max(peaks.get(beat) ?? 0, v));
    }
  }
  const all = [...peaks.values()];
  return all.reduce((a, b) => a + b, 0) / all.length;
}

test('a kick reads about the same at any frame rate', () => {
  const peaks = [30, 60, 144].map((fps) => meanKickPeak(fps, kick));
  const low = Math.min(...peaks);
  const high = Math.max(...peaks);
  // Faster screens catch a peak a little more closely, which no lag can undo.
  assert.ok(low > 0 && high / low < 1.2, `peaks at 30/60/144 fps: ${peaks}`);
});

test('a sustained note is not a hit', () => {
  const note = (t) => 0.5 * Math.sin(2 * Math.PI * 50 * t);
  for (const fps of [30, 60, 144]) {
    const tr = new FrameTransient();
    let most = 0;
    const dt = 1 / fps;
    for (let t = dt; t < 10; t += dt) {
      const v = tr.update(level(note, t), dt);
      if (t > 3) most = Math.max(most, v);
    }
    assert.ok(most < meanKickPeak(fps, kick) * 0.05, `${fps} fps: sustained ${most}`);
  }
});

test('hits scale like the desktop and are stretched like their band', () => {
  const hits = { bass: 0.0835, mid: 0, himid: 0.167, high: 0 };
  const fixed = { amp: 0.25, bass: 0.25, mid: 0.25, himid: 0.25, high: 0.25 };
  const s = hitSignals(hits, fixed);
  assert.ok(Math.abs(s.audio_flux_bass - 0.5) < 1e-9);
  assert.equal(s.audio_flux_himid, 1);
  assert.equal(s.audio_flux_mid, 0);
  assert.ok(Math.abs(s.audio_flux_amp - (0.0835 * 2 + 0.167 * 0.5) / 0.333) < 1e-9);
  const quiet = hitSignals({ bass: 0.00835, mid: 0, himid: 0, high: 0 }, { ...fixed, bass: 0.025 });
  assert.ok(Math.abs(quiet.audio_flux_bass - s.audio_flux_bass) < 1e-9);
});

test('band energy follows one band and stays 0 in warm-up and when the band is empty', () => {
  const agc = new AudioAgc();
  const feed = (seconds, rms) => {
    for (let t = 0; t < seconds; t += 0.05) agc.update(rms, 0.05);
  };
  const base = { amp: 0.2, bass: 0.2, mid: 0.1, high: 0.05, himid: 0 };
  feed(10, base);
  assert.equal(agc.energy('high'), 0, 'warm-up');
  feed(20, base);
  assert.ok(Math.abs(agc.energy('high')) < 0.05);
  assert.equal(agc.energy('himid'), 0, 'nothing in the band');
  feed(3, { ...base, high: 0.2 });
  assert.ok(agc.energy('high') > 0.5, `hats came in: ${agc.energy('high')}`);
  assert.ok(Math.abs(agc.energy('bass')) < 0.05, 'the bass has not moved');
});
