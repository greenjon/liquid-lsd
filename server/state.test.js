'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { RelayState, applyPatch } = require('./state');

const full = (preset, clock) => ({ type: 'state_full', v: 2, preset, clock });

test('applyPatch merges nested objects and null deletes', () => {
  const t = { deckA: { params: { a: 1, b: 2 }, fx: { 0: { id: 'x' }, 1: null } } };
  applyPatch(t, { deckA: { params: { b: 3 }, fx: { 0: null, 1: { id: 'y' } } } });
  assert.deepEqual(t, { deckA: { params: { a: 1, b: 3 }, fx: { 1: { id: 'y' } } } });
});

test('a late joiner gets the merged state, not the first full snapshot', () => {
  let now = 1000;
  const s = new RelayState(() => now);
  assert.equal(s.snapshotMessage(), null);
  assert.ok(s.ingest(full({ mixer: { balance: 0 }, deckA: { params: { Scale: 0.5 } } }, { beats: 10, bpm: 120 })));
  now = 3000;
  assert.ok(s.ingest({ type: 'state_delta', patch: { mixer: { balance: 0.75 } }, clock: { beats: 14, bpm: 120 } }));
  now = 4000;
  const snap = JSON.parse(s.snapshotMessage());
  assert.equal(snap.preset.mixer.balance, 0.75);
  assert.equal(snap.preset.deckA.params.Scale, 0.5);
  // 14 beats at t=3000, one second later at 120 bpm is two more beats
  assert.equal(snap.clock.beats, 16);
});

test('rejects other protocol versions and deltas before any full state', () => {
  const s = new RelayState();
  assert.equal(s.ingest({ type: 'state_delta', patch: { a: 1 } }), false);
  assert.equal(s.ingest({ type: 'state_full', v: 1, preset: {} }), false);
  assert.equal(s.live, false);
});

test('reset forgets the broadcast', () => {
  const s = new RelayState();
  s.ingest(full({ mixer: {} }, { beats: 0, bpm: 120 }));
  s.reset();
  assert.equal(s.snapshotMessage(), null);
});

test('the snapshot is isolated from later patches', () => {
  const s = new RelayState();
  const preset = { mixer: { balance: 0 } };
  s.ingest(full(preset, { beats: 0, bpm: 120 }));
  s.ingest({ type: 'state_delta', patch: { mixer: { balance: 1 } } });
  assert.equal(preset.mixer.balance, 0);
});
