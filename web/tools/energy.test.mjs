// node --test web/tools/energy.test.mjs : breakdown / drop detection on synthetic level curves.
import test from 'node:test';
import assert from 'node:assert/strict';
import { EnergyWatch, DropEnvelope, dropBeat, dropEnvelope } from '../energy.js';

/** Runs `segments` ([seconds, bass, amp]) at 60 fps with a little wobble; returns [{t, event}]. */
function run(segments) {
  const w = new EnergyWatch();
  const events = [];
  let t = 0;
  for (const [secs, bass, amp] of segments) {
    for (let i = 0; i < secs * 60; i++, t += 1 / 60) {
      const wobble = 1 + 0.15 * Math.sin(t * 2 * Math.PI * 2.1); // beats in the level
      const e = w.update(bass * wobble, amp * wobble, 1 / 60);
      if (e) events.push({ t: +t.toFixed(1), event: e, lag: w.dropLag, at: t });
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

test('a drop is dated back to when the low end came back', () => {
  const { events } = run([[40, 0.6, 0.5], [16, 0.1, 0.25], [10, 0.65, 0.55]]);
  const drop = events.find((e) => e.event === 'drop');
  assert.ok(drop.lag > 0.5, `lag ${drop.lag}`);
  assert.ok(Math.abs(drop.at - drop.lag - 56) < 0.3, `dated to ${drop.at - drop.lag}`);
});

test('energy rests at 0 and falls in a breakdown', () => {
  assert.equal(run([[10, 0.6, 0.5]]).w.energy, 0); // warm-up
  assert.ok(Math.abs(run([[40, 0.6, 0.5]]).w.energy) < 0.15);
  assert.ok(run([[40, 0.6, 0.5], [10, 0.1, 0.25]]).w.energy < -0.95);
  assert.ok(run([[40, 0.4, 0.4], [3, 0.7, 0.6]]).w.energy > 0.4);
});

test('the drop envelope: dated to the nearest beat, never the future, 1 down to 0', () => {
  assert.equal(dropBeat(33.4, 1.5, 120), 30);
  assert.equal(dropBeat(33.6, 0, 120), 33.6);
  assert.equal(dropEnvelope(0, 16), 1);
  assert.equal(dropEnvelope(8, 16), 0.25);
  assert.equal(dropEnvelope(16, 16), 0);
  const d = new DropEnvelope();
  d.report(0, 0, 10, 120);
  assert.equal(d.value(10), 0);
  d.report(1, 0, 12, 120);
  assert.equal(d.value(12), 1);
  assert.equal(d.value(20), 0.25);
  d.report(0, 0, 100, 120); // a new watch
  d.report(1, 0, 101, 120);
  assert.equal(d.value(101), 1);
});

test('a drop is called within a second of the low end coming back', () => {
  const drop = run([[40, 0.6, 0.5], [16, 0.1, 0.25], [10, 0.65, 0.55]]).events.find((e) => e.event === 'drop');
  assert.ok(drop.at > 56 && drop.at < 57, `drop at ${drop.at}`);
});

test('one loud hit in a breakdown does not end it', () => {
  const w = new EnergyWatch();
  const step = (n, bass, amp) => { for (let i = 0; i < n; i++) w.update(bass, amp, 1 / 60); };
  step(40 * 60, 0.6, 0.5);
  step(10 * 60, 0.1, 0.25);
  assert.equal(w.section, 'breakdown');
  step(9, 0.9, 0.6); // a 0.15 s hit
  step(3 * 60, 0.1, 0.25);
  assert.equal(w.section, 'breakdown');
  assert.equal(w.drops, 0);
});
