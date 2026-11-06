// beatclock.js
// Beat flywheel for the live broadcast: follows the desktop's beat anchors (CVRegistry.updateBeatAnchor)
// and coasts between them. Like CVRegistry.getSynchronizedTotalBeats, beats only move forward and a large
// disagreement snaps. A small backward correction is absorbed by coasting, a little slow here so the
// output does get back to the true beat: at full tempo it would stay ahead of it until the next correction.
// On top of that each anchor is blended in rather than adopted, so network jitter in when a
// message arrives does not show up as jitter in the phase.

const SNAP_BEATS = 0.25; // desktop's backward-jitter window; anything farther is a real jump (tempo tap, seek)
const BLEND = 0.25;      // fraction of the phase error taken from each anchor
const CATCH_UP = 0.8;    // read() runs at this share of the tempo while it waits for the true beat to catch up

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

  /** A new tempo from `nowMs` on, with the beat count carrying on from where it is. */
  retune(bpm, nowMs) {
    this.beats = this.#predict(nowMs);
    this.at = nowMs;
    this.bpm = bpm;
  }

  #predict(nowMs) {
    return this.beats + (Math.max(0, nowMs - this.at) / 1000) * (this.bpm / 60);
  }

  /** Beats at `nowMs` from the last anchor, without the forward-only smoothing of `read`. */
  predict(nowMs) { return this.#predict(nowMs); }

  /** Total beats at `nowMs`; call once per frame. Never steps backward by less than the snap window. */
  read(nowMs) {
    const raw = this.#predict(nowMs);
    let out = raw;
    if (raw < this.lastOut && this.lastOut - raw < SNAP_BEATS) {
      out = this.lastOut + (Math.max(0, nowMs - this.lastOutAt) / 1000) * (this.bpm / 60) * CATCH_UP;
    }
    this.lastOut = out;
    this.lastOutAt = nowMs;
    return out;
  }
}
