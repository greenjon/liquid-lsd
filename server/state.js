// state.js — merged broadcast state kept by the relay so a viewer joining mid-session gets the
// current state, not the (stale) last state_full. Pure: no sockets, so it is testable on its own.
//
// Wire protocol v2 (see docs/developer/web_subsystem.md):
//   state_full  { type, v, preset, clock }
//   state_delta { type, v?, patch, clock }   patch is a recursive diff; null removes a key
//   clock       { beats, bpm } at the moment the broadcaster sent the message

'use strict';

const PROTOCOL_VERSION = 2;

const isObject = (v) => typeof v === 'object' && v !== null && !Array.isArray(v);

/** Recursive merge. A null in the patch deletes the key. */
function applyPatch(target, patch) {
  for (const [key, val] of Object.entries(patch)) {
    if (val === null) {
      delete target[key];
    } else if (isObject(val) && isObject(target[key])) {
      applyPatch(target[key], val);
    } else {
      target[key] = val;
    }
  }
  return target;
}

class RelayState {
  constructor(now = () => Date.now()) {
    this.now = now;
    this.reset();
  }

  reset() {
    this.preset = null;
    this.clock = null; // { beats, bpm, at } with `at` in relay milliseconds
  }

  get live() { return this.preset !== null; }

  /** Feeds one parsed broadcaster message. Returns false if it was ignored. */
  ingest(msg) {
    if (!isObject(msg)) return false;
    if (msg.type === 'state_full') {
      if (msg.v !== PROTOCOL_VERSION || !isObject(msg.preset)) return false;
      this.preset = JSON.parse(JSON.stringify(msg.preset));
    } else if (msg.type === 'state_delta') {
      if (this.preset === null || !isObject(msg.patch)) return false;
      applyPatch(this.preset, msg.patch);
    } else {
      return false;
    }
    if (isObject(msg.clock) && Number.isFinite(msg.clock.beats) && Number.isFinite(msg.clock.bpm)) {
      this.clock = { beats: msg.clock.beats, bpm: msg.clock.bpm, at: this.now() };
    }
    return true;
  }

  /** A state_full message for a viewer joining now, or null if no broadcast is live. */
  snapshotMessage() {
    if (this.preset === null) return null;
    const msg = { type: 'state_full', v: PROTOCOL_VERSION, preset: this.preset };
    if (this.clock) {
      const elapsedSec = Math.max(0, (this.now() - this.clock.at) / 1000);
      msg.clock = { beats: this.clock.beats + elapsedSec * this.clock.bpm / 60, bpm: this.clock.bpm };
    }
    return JSON.stringify(msg);
  }
}

module.exports = { RelayState, applyPatch, PROTOCOL_VERSION };
