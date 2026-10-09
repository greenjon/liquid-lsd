// beatclock.js
// Beat flywheel for the live broadcast: follows the desktop's beat anchors (CVRegistry.updateBeatAnchor)
// and coasts between them. Mirrors CVRegistry.getSynchronizedTotalBeats: beats only move forward
// (a small backward correction is absorbed by coasting), and a large disagreement snaps.
// On top of that each anchor is blended in rather than adopted, so network jitter in when a
// message arrives does not show up as jitter in the phase.

const SNAP_BEATS = 0.25; // desktop's backward-jitter window; anything farther is a real jump (tempo tap, seek)
const BLEND = 0.25;      // fraction of the phase error taken from each anchor

export class BeatFlywheel {
  constructor() { this.reset(); }

  reset() {
    this.valid = false;
    this.beats = 0;
    this.bpm = 120;
    this.at = 0;        // ms timestamp the (beats, bpm) pair refers to
    this.lastOut = 0;
    this.lastOutAt = 0;
  }

  /** A clock message arrived: `beats` at tempo `bpm`, received at `nowMs`. */
  anchor(beats, bpm, nowMs) {
    if (!this.valid) {
      this.beats = beats; this.bpm = bpm; this.at = nowMs;
      this.lastOut = beats; this.lastOutAt = nowMs;
      this.valid = true;
      return;
    }
    const predicted = this.#predict(nowMs);
    const err = beats - predicted;
    this.beats = Math.abs(err) > SNAP_BEATS ? beats : predicted + err * BLEND;
    this.bpm = bpm;
    this.at = nowMs;
  }

  #predict(nowMs) {
    return this.beats + (Math.max(0, nowMs - this.at) / 1000) * (this.bpm / 60);
  }

  /** Total beats at `nowMs`; call once per frame. Never steps backward by less than the snap window. */
  read(nowMs) {
    const raw = this.#predict(nowMs);
    let out = raw;
    if (raw < this.lastOut && this.lastOut - raw < SNAP_BEATS) {
      out = this.lastOut + (Math.max(0, nowMs - this.lastOutAt) / 1000) * (this.bpm / 60);
    }
    this.lastOut = out;
    this.lastOutAt = nowMs;
    return out;
  }
}
