import test from 'node:test';
import assert from 'node:assert/strict';
import { createFrameLoop } from '../frameloop.js';

// A fake display: frames queued by raf run together at the next vsync().
function fakeDisplay() {
  let next = 1; const q = new Map();
  return {
    raf: (f) => { const id = next++; q.set(id, f); return id; },
    caf: (id) => { q.delete(id); },
    vsync() { const fs = [...q.values()]; q.clear(); fs.forEach((f) => f(0)); },
    queued: () => q.size,
  };
}

test('repeated start leaves one loop', () => {
  const d = fakeDisplay(); let calls = 0; let loop;
  loop = createFrameLoop(() => { calls++; loop.start(); }, d.raf, d.caf);
  loop.start(); loop.start();
  assert.equal(d.queued(), 1);
  d.vsync(); d.vsync();
  assert.equal(calls, 2);
  assert.equal(d.queued(), 1);
});

test('resume while a pre-pause frame is pending does not double the loop', () => {
  const d = fakeDisplay(); let calls = 0; let paused = false; let loop;
  loop = createFrameLoop(() => { if (paused) return; calls++; loop.start(); }, d.raf, d.caf);
  loop.start(); d.vsync();
  paused = true;            // frame already queued, no vsync before resume
  paused = false; loop.start();
  for (let i = 0; i < 5; i++) d.vsync();
  assert.equal(calls, 6);   // 1 + 5, not 1 + 10
});

test('stop cancels', () => {
  const d = fakeDisplay(); const loop = createFrameLoop(() => {}, d.raf, d.caf);
  loop.start(); loop.stop();
  assert.equal(d.queued(), 0);
});
