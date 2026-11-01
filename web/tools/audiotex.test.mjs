// node --test web/tools/audiotex.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { SpectrumAnalyzer, BINS } from '../audiotex.js';

test('spectrum matches the desktop (spectrum_vectors.json from WebSpectrumVectorsTest)', () => {
  const v = JSON.parse(fs.readFileSync(new URL('./spectrum_vectors.json', import.meta.url), 'utf8'));
  const a = new SpectrumAnalyzer();
  const b = new Float32Array(v.block);
  let worst = 0;
  for (let k = 0; k < v.signalBlocks + v.silentBlocks; k++) {
    if (k < v.signalBlocks) b.set(v.input.slice(k * v.block, (k + 1) * v.block)); else b.fill(0);
    a.process(b, v.block, v.sampleRate);
    const snap = v.expect.find((e) => e.afterBlock === k);
    if (!snap) continue;
    for (let i = 0; i < BINS; i++) worst = Math.max(worst, Math.abs(a.spectrum[i] - snap.spectrum[i]));
  }
  // float32 on the desktop, doubles here: well inside one 8-bit display step
  assert.ok(worst < 1e-3, `largest difference ${worst}`);
});

test('a full-scale sine reads ~1 in its bin; silence decays', () => {
  const a = new SpectrumAnalyzer();
  const sr = 48000, bin = 100, f = bin * sr / 1024;
  const b = new Float32Array(512);
  let n = 0;
  for (let k = 0; k < 16; k++) {
    for (let i = 0; i < 512; i++, n++) b[i] = Math.sin(2 * Math.PI * f * n / sr);
    a.process(b, 512, sr);
  }
  let peak = 0;
  for (let i = 1; i < BINS; i++) if (a.spectrum[i] > a.spectrum[peak]) peak = i;
  assert.equal(peak, bin);
  assert.ok(a.spectrum[peak] > 0.95);
  b.fill(0);
  for (let k = 0; k < 100; k++) a.process(b, 512, sr);
  assert.ok(Math.max(...a.spectrum) < 0.01);
});

test('elapsed sets the release, not the window length', () => {
  const sr = 48000;
  const loud = new Float32Array(1024).map((_, i) => Math.sin(2 * Math.PI * 1000 * i / sr));
  const quiet = new Float32Array(1024);
  const fast = new SpectrumAnalyzer(), slow = new SpectrumAnalyzer();
  fast.process(loud, 1024, sr); slow.process(loud, 1024, sr);
  fast.process(quiet, 1024, sr, 4800); // 100 ms passed
  slow.process(quiet, 1024, sr, 480);  // 10 ms passed
  const bin = Math.round(1000 * 1024 / sr);
  assert.ok(fast.spectrum[bin] < slow.spectrum[bin]);
});
