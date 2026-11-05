// transient.test.mjs: the web TV's hit signals. Run with `node --test web/tools/transient.test.mjs`.
// transient_vectors.json comes from the desktop's TransientDetectorTest (UPDATE_WEB_VECTORS=1).

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { TransientGate, FrameTransient, HitScale, hitSignals, AudioAgc } from '../dsp.js';

test('the gate matches the desktop (transient_vectors.json)', () => {
  const v = JSON.parse(fs.readFileSync(new URL('./transient_vectors.json', import.meta.url), 'utf8'));
  const gate = new TransientGate();
  v.steps.forEach(([rise, want], i) => {
    const got = gate.step(rise, v.dt);
    assert.ok(Math.abs(got - want) <= 1e-5 * Math.max(1, Math.abs(want)), `step ${i}: ${got} vs desktop ${want}`);
  });
});

test('hit sizing matches the desktop (transient_vectors.json)', () => {
  const v = JSON.parse(fs.readFileSync(new URL('./transient_vectors.json', import.meta.url), 'utf8'));
  const scale = new HitScale();
  v.scale.forEach(([hit, agcScale, frozen, want], i) => {
    const got = scale.signal(hit, agcScale, v.dt, frozen === 1);
    assert.ok(Math.abs(got - want) <= 1e-4 * Math.max(1, Math.abs(want)), `step ${i}: ${got} vs desktop ${want}`);
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

const scales = () => ({ amp: new HitScale(), bass: new HitScale(), mid: new HitScale(), himid: new HitScale(), high: new HitScale() });
const FIXED_DIV = { amp: 0.25, bass: 0.25, mid: 0.25, himid: 0.25, high: 0.25 };

test('hits use the range whatever their size, up to the cap above auto gain', () => {
  const run = (bass) => {
    const s = scales();
    let last = 0;
    for (let i = 0; i < 2000; i++) {
      const hit = i % 40 < 3 ? bass : 0;
      const out = hitSignals({ bass: hit, mid: 0, himid: 0, high: 0 }, FIXED_DIV, s, 1 / 60, false);
      if (hit > 0) last = out.audio_flux_bass;
    }
    return last;
  };
  assert.ok(run(0.2) > 0.9, 'a strong band');
  assert.ok(run(0.06) > 0.9, 'a soft band reads the same');
  const capped = run(0.005);
  assert.ok(Math.abs(capped - 0.005 / (0.167 / 3)) < 1e-6, `a very soft band stops at the cap: ${capped}`);
});

test('the reference waits while frozen, so a quieter stretch stays quieter', () => {
  const s = new HitScale();
  for (let i = 0; i < 1000; i++) s.signal(i % 40 < 3 ? 0.2 : 0, 0.1, 1 / 60, false);
  let frozen = 0;
  for (let i = 0; i < 1500; i++) frozen = Math.max(frozen, s.signal(i % 40 < 3 ? 0.04 : 0, 0.1, 1 / 60, true));
  assert.ok(frozen < 0.3, `frozen ${frozen}`);
  let free = 0;
  for (let i = 0; i < 1500; i++) free = Math.max(free, s.signal(i % 40 < 3 ? 0.04 : 0, 0.1, 1 / 60, false));
  assert.ok(free > 0.9, `free ${free}`);
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
