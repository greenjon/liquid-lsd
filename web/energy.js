// energy.js
// Song sections from the band levels, for the autopilot: a *breakdown* is the low end falling well below
// where it has been for the last ~20 s; a *drop* is it coming back after a breakdown of at least 4 s.
// The long-term level freezes during a breakdown (and while one is being confirmed), so the drop is measured against the music before it.
// The same long-term level is what auto gain (AudioAgc in dsp.js) normalises against, so a breakdown also
// reads quiet. A port of the desktop's audio/SectionWatch.kt: keep the two identical.
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
const LOUDER_RATIO = 1.6;  // now above this multiple of the reference...
const LOUDER_HOLD = 4;     // ...for this long (s) is a louder song: adopt it at once

export class EnergyWatch {
  constructor() {
    this.short = 0;
    this.long = 0;
    this.time = 0;
    this.below = 0;        // s the level has been under the breakdown ratio
    this.section = 'normal';
    this.sectionTime = 0;  // s in the current section
    this.drops = 0;        // count of drops so far
    this.louder = 0;       // s the level has been over the louder ratio
    this.rebased = false;  // true on the frame the reference jumped to the current level (a new normal)
  }

  /** `long` is not following the music: a breakdown, or the level has just fallen under the breakdown ratio. */
  get holding() { return this.section === 'breakdown' || this.below > 0; }

  reset() {
    this.short = this.long = this.time = this.below = this.louder = this.sectionTime = 0;
    this.section = 'normal';
    this.rebased = false;
  }

  /** One frame of band levels (0..1) over `dt` seconds. Returns 'breakdown', 'drop' or null. */
  update(bass, amp, dt) {
    const level = 0.7 * bass + 0.3 * amp;
    this.time += dt;
    this.sectionTime += dt;
    this.rebased = false;
    this.short += (level - this.short) * Math.min(1, dt / SHORT_TAU);
    // A running mean until LONG_TAU has passed, so the reference is right from the first seconds. It
    // stops as soon as the level dips under the breakdown ratio, so a breakdown does not drag it down
    // while it is being confirmed.
    if (!this.holding) this.long += (level - this.long) * Math.min(1, dt / Math.min(LONG_TAU, this.time));
    if (this.time < WARMUP || this.long < SILENCE) { this.below = this.louder = 0; return null; }

    if (this.section === 'normal') {
      this.below = this.short < BREAK_RATIO * this.long ? this.below + dt : 0;
      if (this.below >= BREAK_HOLD) return this.#enter('breakdown');
      this.louder = this.short > LOUDER_RATIO * this.long ? this.louder + dt : 0;
      if (this.louder >= LOUDER_HOLD) this.#rebase();
      return null;
    }
    // breakdown
    if (this.short > DROP_RATIO * this.long) {
      if (this.sectionTime >= DROP_MIN) { this.drops++; return this.#enter('normal', 'drop'); }
      this.#enter('normal'); // only a short dip
      return null;
    }
    if (this.sectionTime > BREAK_MAX) { this.#rebase(); this.#enter('normal'); }
    return null;
  }

  #rebase() {
    this.long = this.short;
    this.louder = 0;
    this.rebased = true;
  }

  #enter(section, event = section) {
    this.section = section;
    this.sectionTime = 0;
    this.below = 0;
    return event;
  }
}
