// node --test web/tools/autopilot.test.mjs : autopilot scheduling.
import test from 'node:test';
import assert from 'node:assert/strict';
import { nextSwitchBeat, tempoWeight, weightedPick } from '../autopilot.js';

test('autopilot changes start so they finish on a bar line, after the hold', () => {
  assert.equal(nextSwitchBeat(0, 16, 4), 60);        // fade beats 60-64, new look on the bar at 64
  assert.equal(nextSwitchBeat(65.3, 16, 4), 128);     // held to 129.3: the fade ends on the bar line at 132
  assert.equal(nextSwitchBeat(10, 0, 0), 12);         // resume after a broadcast: next bar line
  assert.equal(nextSwitchBeat(11.5, 0, 4), 11.5);     // never in the past
});

test('tempo leans the pick: calm looks when slow, hard ones when fast, never a filter', () => {
  assert.ok(tempoWeight(['ambient'], 85) > tempoWeight(['hard'], 85));
  assert.ok(tempoWeight(['hard'], 140) > tempoWeight(['ambient'], 140));
  assert.equal(tempoWeight(['groove'], 110), 2);
  for (const bpm of [80, 110, 150]) for (const tags of [['hard'], ['ambient'], ['mid'], []]) assert.ok(tempoWeight(tags, bpm) > 0);
});

test('weighted pick follows the weights and never repeats the current look', () => {
  const list = ['a', 'b', 'c'];
  const counts = [0, 0, 0];
  let seed = 1;
  const rand = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 2 ** 32);
  for (let i = 0; i < 3000; i++) counts[weightedPick(list, [1, 3, 1], 0, rand)]++;
  assert.equal(counts[0], 0);
  assert.ok(counts[1] > counts[2] * 2.2 && counts[1] < counts[2] * 3.8, counts.join(','));
  assert.equal(weightedPick(list, [0, 0, 0], 1), -1);
});
