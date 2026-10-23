// energy.js
// Song sections from the band levels, for the autopilot: a *breakdown* is the low end falling well below
// where it has been for the last ~20 s; a *drop* is it coming back after a breakdown of at least 4 s.
// The long-term level freezes during a breakdown, so the drop is measured against the music before it.
// Pure, so it is unit-tested in node (web/tools/energy.test.mjs).

const SHORT_TAU = 1.5;     // s, the "now" level
const LONG_TAU = 20;       // s, the reference level
const WARMUP = 20;         // s of audio before any section is reported
const SILENCE = 0.03;      // a reference level below this is silence, not a breakdown
const BREAK_RATIO = 0.45;  // now below this fraction of the reference...
const BREAK_HOLD = 3;      // ...for this long (s) is a breakdown
const DROP_RATIO = 0.85;   // back above this fraction of the pre-breakdown level is a drop...
const DROP_MIN = 4;        // ...if the breakdown lasted at least this long (s; short breakdowns are common on the radio)
const BREAK_MAX = 60;      // a "breakdown" this long is just a quieter song: adopt it as the new normal

export class EnergyWatch {
  constructor() {
    this.short = 0;
    this.long = 0;
    this.time = 0;
    this.below = 0;        // s the level has been under the breakdown ratio
    this.section = 'normal';
    this.sectionTime = 0;  // s in the current section
    this.drops = 0;        // count of drops so far
  }

  /** One frame of band levels (0..1) over `dt` seconds. Returns 'breakdown', 'drop' or null. */
  update(bass, amp, dt) {
    const level = 0.7 * bass + 0.3 * amp;
    this.time += dt;
    this.sectionTime += dt;
    this.short += (level - this.short) * Math.min(1, dt / SHORT_TAU);
    if (this.section === 'normal') this.long += (level - this.long) * Math.min(1, dt / LONG_TAU);
    if (this.time < WARMUP || this.long < SILENCE) { this.below = 0; return null; }

    if (this.section === 'normal') {
      this.below = this.short < BREAK_RATIO * this.long ? this.below + dt : 0;
      if (this.below >= BREAK_HOLD) return this.#enter('breakdown');
      return null;
    }
    // breakdown
    if (this.short > DROP_RATIO * this.long) {
      if (this.sectionTime >= DROP_MIN) { this.drops++; return this.#enter('normal', 'drop'); }
      this.#enter('normal'); // only a short dip
      return null;
    }
    if (this.sectionTime > BREAK_MAX) { this.long = this.short; this.#enter('normal'); }
    return null;
  }

  #enter(section, event = section) {
    this.section = section;
    this.sectionTime = 0;
    this.below = 0;
    return event;
  }
}
