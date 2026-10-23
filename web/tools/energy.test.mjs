// node --test web/tools/energy.test.mjs : breakdown / drop detection on synthetic level curves.
import test from 'node:test';
import assert from 'node:assert/strict';
import { EnergyWatch } from '../energy.js';

/** Runs `segments` ([seconds, bass, amp]) at 60 fps with a little wobble; returns [{t, event}]. */
function run(segments) {
  const w = new EnergyWatch();
  const events = [];
  let t = 0;
  for (const [secs, bass, amp] of segments) {
    for (let i = 0; i < secs * 60; i++, t += 1 / 60) {
      const wobble = 1 + 0.15 * Math.sin(t * 2 * Math.PI * 2.1); // beats in the level
      const e = w.update(bass * wobble, amp * wobble, 1 / 60);
      if (e) events.push({ t: +t.toFixed(1), event: e });
    }
  }
  return { w, events };
}

test('a breakdown then a drop', () => {
  const { events, w } = run([[40, 0.6, 0.5], [16, 0.1, 0.25], [10, 0.65, 0.55]]);
  assert.deepEqual(events.map((e) => e.event), ['breakdown', 'drop']);
  assert.ok(events[0].t > 40 && events[0].t < 48, `breakdown at ${events[0].t}`); // level falls ~1.5 s, then 3 s held
  assert.ok(events[1].t > 56 && events[1].t < 59, `drop at ${events[1].t}`);
  assert.equal(w.drops, 1);
});

test('a short dip is neither', () => {
  const { events } = run([[40, 0.6, 0.5], [2, 0.1, 0.2], [20, 0.6, 0.5]]);
  assert.deepEqual(events, []);
});

test('a dip that recovers too soon ends the breakdown without a drop', () => {
  const { events } = run([[40, 0.6, 0.5], [6, 0.1, 0.2], [20, 0.6, 0.5]]);
  assert.deepEqual(events.map((e) => e.event), ['breakdown']);
});

test('nothing during the warm-up or in silence', () => {
  assert.deepEqual(run([[8, 0.6, 0.5], [8, 0.05, 0.05], [3, 0.6, 0.5]]).events, []);
  assert.deepEqual(run([[60, 0.01, 0.01], [20, 0.0, 0.0]]).events, []);
});

test('a long quiet stretch becomes the new normal', () => {
  const { w, events } = run([[40, 0.6, 0.5], [70, 0.15, 0.2], [5, 0.2, 0.25]]);
  assert.deepEqual(events.map((e) => e.event), ['breakdown']);
  assert.equal(w.section, 'normal');
});
