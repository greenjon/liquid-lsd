// node --test web/tools/autopilot.test.mjs : autopilot scheduling.
import test from 'node:test';
import assert from 'node:assert/strict';
import { nextSwitchBeat } from '../autopilot.js';

test('autopilot changes start so they finish on a bar line, after the hold', () => {
  assert.equal(nextSwitchBeat(0, 16, 4), 60);        // fade beats 60-64, new look on the bar at 64
  assert.equal(nextSwitchBeat(65.3, 16, 4), 128);     // held to 129.3: the fade ends on the bar line at 132
  assert.equal(nextSwitchBeat(10, 0, 0), 12);         // resume after a broadcast: next bar line
  assert.equal(nextSwitchBeat(11.5, 0, 4), 11.5);     // never in the past
});
